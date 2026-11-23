package com.example

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.utils.AudioWavNormalizer
import com.example.utils.ImportItemKind
import com.example.utils.ImportTargetMode
import com.example.utils.SmartImportEngine
import com.example.utils.SmartImportPlanConfig
import com.example.utils.SpreadsheetWordImporter
import com.example.utils.XlsxSpreadsheetWriter
import com.example.utils.ZipUtility
import com.example.viewmodel.parseImportTextToParsedWordsImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeprojAndImportFixesTest {

    private fun buildMono16WavBytes(sampleRate: Int = 16000, durationMs: Int = 250, freqHz: Double = 220.0): ByteArray {
        val frames = (sampleRate * durationMs) / 1000
        val dataSize = frames * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(1)
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (i in 0 until frames) {
            val s = (14000.0 * sin(2.0 * PI * freqHz * i / sampleRate)).toInt().toShort()
            buf.putShort(s)
        }
        return buf.array()
    }

    @Test
    fun `imports real PhonTracer v2 multi-speaker long-audio teproj with full wordlist and sliced wavs`() = runBlocking {
        val realFile = File("""C:\Users\Sager\Desktop\Program\PhonTracer\Example\wordlist_tone16\普通话双字组16种声调实验.teproj""")
        assumeTrue("Real PhonTracer v2 sample missing: ${realFile.absolutePath}", realFile.isFile)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val sessionDir = SmartImportEngine.createSessionStagingDir(app)
        var createdProjId: Long? = null
        try {
            val repo = ProjectRepository(db)

            // 1. SmartImport quick inspection MUST read real title (no stg_1_ prefix), 32 words, 3 speakers, 96 slices
            val staged = SmartImportEngine.stageLocalFilesIntoPool(
                context = app,
                sessionDir = sessionDir,
                existingStaged = emptyList(),
                files = listOf(realFile)
            )
            assertEquals(1, staged.size)
            val arch = staged.first()
            assertEquals(ImportItemKind.PROJECT_ARCHIVE, arch.kind)
            val inspect = arch.archiveInspect
            assertNotNull(inspect)
            assertFalse("Project name must not contain stg_ prefix: ${inspect!!.archiveProjectName}", inspect.archiveProjectName.startsWith("stg_"))
            assertEquals("普通话双字组16种声调实验字表", inspect.archiveProjectName)
            assertEquals(32, inspect.estimatedWordCount)
            assertEquals(3, inspect.estimatedSpeakerCount)
            assertEquals(96, inspect.estimatedAudioCount)
            assertEquals(32, inspect.previewWords.size)

            // 2. Full SmartImport execution
            val plan = SmartImportEngine.buildImportPlan(staged, emptyList(), emptyList(), SmartImportPlanConfig())
            val result = SmartImportEngine.executeImportPlan(
                context = app,
                repository = repo,
                plan = plan,
                config = SmartImportPlanConfig(),
                existingProjects = emptyList(),
                existingSpeakers = emptyList(),
                defaultSpeakerId = null
            )
            assertTrue(result.summaryMessage, result.success)
            assertEquals(1, result.importedArchiveCount)
            assertNotNull(result.primaryProjectId)
            createdProjId = result.primaryProjectId!!

            val project = repo.getProjectById(createdProjId)!!
            assertEquals("普通话双字组16种声调实验字表", project.name)

            val words = repo.getWordsForProjectSync(createdProjId).sortedBy { it.originalIndex }
            assertEquals(32, words.size)
            assertEquals("播音", words[0].text)
            assertEquals("11_阴阴", words[0].groupName)
            assertEquals(16, words.map { it.groupName }.distinct().size)

            val speakers = db.speakerDao().getAllSpeakers().first()
            assertEquals(3, speakers.size)
            val activeSpeaker = speakers.first { it.name == "M01_男1" }
            assertEquals("Primary speaker must match active_speaker_id (M01_男1)", activeSpeaker.id, result.primarySpeakerId)

            // All 3 speakers MUST have all 32 sliced canonical mono-16 WAV recordings!
            val projDir = File(app.getExternalFilesDir(null), "project_$createdProjId")
            for (spk in speakers) {
                val spkWords = repo.getWordsForProjectWithSpeakerSync(createdProjId, spk.id)
                assertEquals("Speaker ${spk.name} should have 32 recorded sliced words", 32, spkWords.count { it.isRecorded })
                val firstWav = File(projDir, spkWords[0].recordingFile!!)
                assertTrue(firstWav.exists() && firstWav.length() > 44L)
                val hdr = AudioWavNormalizer.inspectWavHeader(firstWav)
                assertNotNull(hdr)
                assertTrue("Sliced wav must be canonical 16-bit mono PCM", hdr!!.isCanonicalMono16Pcm)
                assertTrue("Real RMS should be evaluated, not hardcoded 1200f", spkWords[0].avgRms != 1200f || spkWords[0].peakAmplitude != 12000)
            }

            // 3. Wordlist Editor importer (`SpreadsheetWordImporter`) also extracts all 32 words directly
            val directWordsRes = SpreadsheetWordImporter.importFromBytes(
                bytes = realFile.readBytes(),
                fileName = realFile.name,
                fallbackTextParser = ::parseImportTextToParsedWordsImpl
            )
            assertEquals(32, directWordsRes.words.size)
            assertEquals("播音", directWordsRes.words[0].text)
        } finally {
            createdProjId?.let { File(app.getExternalFilesDir(null), "project_$it").deleteRecursively() }
            SmartImportEngine.cleanupSessionStagingDir(sessionDir)
            db.close()
        }
    }

    @Test
    fun `imports legacy PhonTracer multi-speaker independent-audio teproj without top-level groups`() = runBlocking {
        val realFile = File("""C:\Users\Sager\Desktop\Program\PhonTracer\_archive\examples\Example\07_普通话二字组连读变调\连读变调.teproj""")
        assumeTrue("Real PhonTracer legacy independent sample missing: ${realFile.absolutePath}", realFile.isFile)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val sessionDir = SmartImportEngine.createSessionStagingDir(app)
        var createdProjId: Long? = null
        try {
            val repo = ProjectRepository(db)

            val staged = SmartImportEngine.stageLocalFilesIntoPool(app, sessionDir, emptyList(), listOf(realFile))
            val inspect = staged.first().archiveInspect!!
            assertEquals(32, inspect.estimatedWordCount)
            assertEquals(3, inspect.estimatedSpeakerCount)
            assertEquals(96, inspect.estimatedAudioCount)
            assertEquals(32, inspect.previewWords.size)

            val summary = FileInputStream(realFile).use { fis ->
                ZipUtility.importProjectArchiveZip(app, repo, fis, fallbackProjectName = realFile.nameWithoutExtension)
            }
            assertTrue(summary.message, summary.success)
            createdProjId = summary.projectId
            assertEquals(32, summary.wordCount)
            assertEquals(96, summary.wavCount)
            assertEquals(3, summary.speakerCount)

            val words = repo.getWordsForProjectSync(createdProjId).sortedBy { it.originalIndex }
            assertEquals(32, words.size)
            assertTrue("Should preserve group name from speaker items", words.any { it.groupName == "双上声变调组" })
            assertTrue(
                "Should preserve item_note / item_meta / item_tags in remark",
                words.any { it.remark.contains("双上声变调词") && it.remark.contains("3-3") }
            )

            // Verify every speaker has 32 recordings (P0 multi-speaker loop fix)
            val speakers = db.speakerDao().getAllSpeakers().first()
            assertEquals(3, speakers.size)
            for (spk in speakers) {
                val spkWords = repo.getWordsForProjectWithSpeakerSync(createdProjId, spk.id)
                assertEquals("Speaker ${spk.name} must have all 32 recordings", 32, spkWords.count { it.isRecorded })
            }
        } finally {
            createdProjId?.let { File(app.getExternalFilesDir(null), "project_$it").deleteRecursively() }
            SmartImportEngine.cleanupSessionStagingDir(sessionDir)
            db.close()
        }
    }

    @Test
    fun `imports legacy PhonTracer long-audio teproj without top-level groups and preserves metadata`() = runBlocking {
        val vowelFile = File("""C:\Users\Sager\Desktop\Program\PhonTracer\_archive\examples\Example\01_单元音与双元音\单元音与双元音.teproj""")
        val toneFile = File("""C:\Users\Sager\Desktop\Program\PhonTracer\_archive\examples\Example\wordlist_tone16\普通话双字组声调基频格局与连读变调实验.teproj""")
        assumeTrue("Legacy long-audio samples missing", vowelFile.isFile && toneFile.isFile)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val createdIds = mutableListOf<Long>()
        try {
            val repo = ProjectRepository(db)

            val sum1 = FileInputStream(vowelFile).use { fis ->
                ZipUtility.importProjectArchiveZip(app, repo, fis, fallbackProjectName = vowelFile.nameWithoutExtension)
            }
            assertTrue(sum1.message, sum1.success)
            createdIds.add(sum1.projectId)
            assertEquals(30, sum1.wordCount)
            assertEquals(30, sum1.wavCount)
            assertEquals("单元音与双元音", sum1.projectName)
            val vowelWords = repo.getWordsForProjectSync(sum1.projectId)
            assertTrue("Must not turn long wav filename into fake word", vowelWords.none { it.text.contains("_long_") })

            val sum2 = FileInputStream(toneFile).use { fis ->
                ZipUtility.importProjectArchiveZip(app, repo, fis, fallbackProjectName = toneFile.nameWithoutExtension)
            }
            assertTrue(sum2.message, sum2.success)
            createdIds.add(sum2.projectId)
            assertEquals(32, sum2.wordCount)
            assertTrue("Should slice recordings from long audio", sum2.wavCount >= 32)
            assertEquals("普通话双字组声调基频格局与连读变调实验字表", sum2.projectName)
            val toneWords = repo.getWordsForProjectSync(sum2.projectId).sortedBy { it.originalIndex }
            assertEquals("播音", toneWords[0].text)
            assertEquals("11_阴阴", toneWords[0].groupName)
            assertTrue("Should preserve pinyin/note metadata", toneWords[0].remark.contains("bo1 yin1"))
        } finally {
            createdIds.forEach { File(app.getExternalFilesDir(null), "project_$it").deleteRecursively() }
            db.close()
        }
    }

    @Test
    fun `handles GBK zip names, UTF-8 BOM json, backslash paths, unrecorded items, same-name subfolder wavs and dialect speaker reuse`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val tmpDir = File(app.cacheDir, "gbk_bom_test_${System.currentTimeMillis()}").apply { mkdirs() }
        var createdProjId: Long? = null
        try {
            val repo = ProjectRepository(db)
            // Pre-create local dialect speaker "张三" with Suzhou dialect; teproj import without dialect MUST match it!
            val existingZhangId = repo.createSpeaker("张三", "男", 55, "苏州话", "", "")

            val wavSpk1 = buildMono16WavBytes(durationMs = 200, freqHz = 200.0)
            val wavSpk2 = buildMono16WavBytes(durationMs = 450, freqHz = 320.0)

            val projectJsonWithBom = "\uFEFF" + """
                {
                  "version": "1.0",
                  "active_speaker_id": "SPK02",
                  "wordlist_title": "边界兼容测试表",
                  "speakers": {
                    "SPK01": {
                      "id": "SPK01",
                      "name": "张三",
                      "project_groups": ["阴平", "阳平"],
                      "items": {
                        "I01": { "label": "巴", "group": "阴平", "path": "audio\\张三\\001_巴.wav", "note": "pa55" },
                        "I02": { "label": "爬", "group": "阳平", "note": "未录音词条" }
                      }
                    },
                    "SPK02": {
                      "id": "SPK02",
                      "name": "李四",
                      "project_groups": ["阴平", "阳平"],
                      "items": {
                        "I01": { "label": "巴", "group": "阴平", "path": "audio\\李四\\001_巴.wav", "speaker_note": "李四记音" },
                        "I02": { "label": "爬", "group": "阳平" }
                      }
                    }
                  }
                }
            """.trimIndent()

            val gbkZipFile = File(tmpDir, "gbk_compat.teproj")
            val gbkCharset = Charset.forName("GBK")
            ZipOutputStream(FileOutputStream(gbkZipFile), gbkCharset).use { zos ->
                zos.putNextEntry(ZipEntry("project.json"))
                zos.write(projectJsonWithBom.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("audio/张三/001_巴.wav"))
                zos.write(wavSpk1)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("audio/李四/001_巴.wav"))
                zos.write(wavSpk2)
                zos.closeEntry()
            }

            // 1. Pre-inspect handles GBK entry names + BOM JSON without failing
            val staged = SmartImportEngine.stageLocalFilesIntoPool(app, tmpDir, emptyList(), listOf(gbkZipFile))
            val inspect = staged.first().archiveInspect!!
            assertEquals("边界兼容测试表", inspect.archiveProjectName)
            assertEquals(2, inspect.estimatedWordCount) // includes unrecorded "爬"!
            assertEquals(2, inspect.estimatedSpeakerCount)
            assertEquals(2, inspect.estimatedAudioCount)

            // 2. Full import
            val summary = FileInputStream(gbkZipFile).use { fis ->
                ZipUtility.importProjectArchiveZip(app, repo, fis)
            }
            assertTrue(summary.message, summary.success)
            createdProjId = summary.projectId
            assertEquals(2, summary.wordCount)
            assertEquals(2, summary.wavCount)

            // Verify existing "张三" (苏州话) was reused without duplicating!
            val speakers = db.speakerDao().getAllSpeakers().first()
            assertEquals(2, speakers.size)
            val zhang = speakers.first { it.name == "张三" }
            val li = speakers.first { it.name == "李四" }
            assertEquals(existingZhangId, zhang.id)
            assertEquals("苏州话", zhang.dialect)
            assertEquals("Active speaker SPK02 (李四) should be primary", li.id, summary.primarySpeakerId)

            // Verify subfolder same-basename WAVs were NOT mixed up between speakers!
            val zhangWords = repo.getWordsForProjectWithSpeakerSync(createdProjId, zhang.id).sortedBy { it.originalIndex }
            val liWords = repo.getWordsForProjectWithSpeakerSync(createdProjId, li.id).sortedBy { it.originalIndex }
            assertEquals("巴", zhangWords[0].text)
            assertEquals("爬", zhangWords[1].text) // unrecorded word preserved!
            assertTrue(zhangWords[0].isRecorded)
            assertFalse(zhangWords[1].isRecorded)
            assertTrue(liWords[0].isRecorded)
            assertEquals("李四记音", liWords[0].speakerNote)
            // zhang's WAV is 200ms, li's WAV is 450ms -> distinct durations prove distinct files!
            assertTrue("Zhang wav ~200ms, got ${zhangWords[0].durationMs}", zhangWords[0].durationMs in 150L..260L)
            assertTrue("Li wav ~450ms, got ${liWords[0].durationMs}", liWords[0].durationMs in 400L..520L)
        } finally {
            createdProjId?.let { File(app.getExternalFilesDir(null), "project_$it").deleteRecursively() }
            tmpDir.deleteRecursively()
            db.close()
        }
    }

    @Test
    fun `prevents duplicate staged IDs on delete-then-add, blocks missing merge target, rolls back failed archive, and guards json and multi-sheet xlsx`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val tmpDir = File(app.cacheDir, "edge_guards_${System.currentTimeMillis()}").apply { mkdirs() }
        val sessionDir = SmartImportEngine.createSessionStagingDir(app)
        try {
            val repo = ProjectRepository(db)

            // 1. Delete-then-add monotonic IDs (LazyColumn duplicate key crash fix)
            val f1 = File(tmpDir, "a.txt").apply { writeText("东\n西\n") }
            val f2 = File(tmpDir, "b.txt").apply { writeText("南\n北\n") }
            val f3 = File(tmpDir, "c.txt").apply { writeText("中\n发\n") }
            val batch1 = SmartImportEngine.stageLocalFilesIntoPool(app, sessionDir, emptyList(), listOf(f1, f2))
            assertEquals(2, batch1.size)
            val afterDeleteFirst = batch1.drop(1) // remove item_1, keep item_2
            val batch2 = SmartImportEngine.stageLocalFilesIntoPool(app, sessionDir, afterDeleteFirst, listOf(f3))
            assertEquals(2, batch2.size)
            assertNotEquals("IDs must remain unique after delete+add", batch2[0].id, batch2[1].id)
            assertEquals(2, batch2.map { it.id }.distinct().size)

            // 2. Invalid merge target must fail cleanly and NOT pollute unrelated first project
            val spkId = repo.createSpeaker("测试人", "男", 30, "普通话", "", "")
            val unrelatedProjId = repo.createProject("无关项目", "", listOf("天"), spkId)
            val existingProjects = db.projectDao().getAllProjects().first()
            val badMergeConfig = SmartImportPlanConfig(
                targetMode = ImportTargetMode.MERGE_EXISTING_PROJECT,
                mergeTargetProjectId = 999999L
            )
            val badPlan = SmartImportEngine.buildImportPlan(batch2, emptyList(), db.speakerDao().getAllSpeakers().first(), badMergeConfig)
            val badMergeRes = SmartImportEngine.executeImportPlan(
                context = app,
                repository = repo,
                plan = badPlan,
                config = badMergeConfig,
                existingProjects = existingProjects,
                existingSpeakers = db.speakerDao().getAllSpeakers().first(),
                defaultSpeakerId = spkId
            )
            assertFalse("Missing merge target must fail", badMergeRes.success)
            assertTrue(badMergeRes.summaryMessage.contains("目标项目已不存在"))
            assertEquals("Unrelated project must NOT be modified", 1, repo.getWordsForProjectSync(unrelatedProjId).size)

            // 3. Failed archive with speaker definition but 0 words must NOT leave dirty speaker in DB and must surface real error
            val speakersBefore = db.speakerDao().getAllSpeakers().first().size
            val emptyWordsTeproj = File(tmpDir, "empty_words.teproj")
            ZipOutputStream(FileOutputStream(emptyWordsTeproj)).use { zos ->
                zos.putNextEntry(ZipEntry("project.json"))
                zos.write("""{"version":"1.0","wordlist_title":"空词表","speakers":{"S1":{"id":"S1","name":"幽灵发音人","items":{}}}}""".toByteArray())
                zos.closeEntry()
            }
            val emptyStaged = SmartImportEngine.stageLocalFilesIntoPool(app, sessionDir, emptyList(), listOf(emptyWordsTeproj))
            val emptyPlan = SmartImportEngine.buildImportPlan(emptyStaged, emptyList(), db.speakerDao().getAllSpeakers().first(), SmartImportPlanConfig())
            val emptyExecRes = SmartImportEngine.executeImportPlan(
                context = app,
                repository = repo,
                plan = emptyPlan,
                config = SmartImportPlanConfig(),
                existingProjects = existingProjects,
                existingSpeakers = db.speakerDao().getAllSpeakers().first(),
                defaultSpeakerId = spkId
            )
            assertFalse(emptyExecRes.success)
            assertTrue("Must propagate real failure reason, got: ${emptyExecRes.summaryMessage}", emptyExecRes.summaryMessage.contains("无法从压缩包中解析出有效词条"))
            assertEquals("Must NOT persist orphan speaker on failed import", speakersBefore, db.speakerDao().getAllSpeakers().first().size)

            // 4. Arbitrary non-wordlist JSON must NOT be split into garbage words
            val garbageJson = """{"version": "1.0", "sample_rate": 44100, "encoding": "PCM_16BIT_MONO"}"""
            assertTrue(parseImportTextToParsedWordsImpl(garbageJson).isEmpty())

            // Valid .ptwl v2 wordlist_doc JSON MUST parse cleanly
            val ptwlJson = """{"schema":"ptwl.v2","title":"测试","groups":[{"name":"声母","items":[{"label":"坡","note":"pʰ"}]}]}"""
            val ptwlWords = parseImportTextToParsedWordsImpl(ptwlJson)
            assertEquals(1, ptwlWords.size)
            assertEquals("坡", ptwlWords[0].text)
            assertEquals("声母", ptwlWords[0].groupName)

            // 5. Multi-sheet .xlsx with summary sheet + wordlist sheet must only parse wordlist sheet (no duplicates)
            val xlsxBytes = XlsxSpreadsheetWriter.toByteArray(
                listOf(
                    XlsxSpreadsheetWriter.SheetSpec(
                        name = "录音汇总表",
                        headers = listOf("序号", "分组", "词条", "备注"),
                        rows = listOf(listOf(1, "阴平", "衣", "i55"))
                    ),
                    XlsxSpreadsheetWriter.SheetSpec(
                        name = "完整调查字表",
                        headers = listOf("序号", "分组", "词条", "备注"),
                        rows = listOf(
                            listOf(1, "阴平", "衣", "i55"),
                            listOf(2, "阳平", "移", "i35")
                        )
                    )
                )
            )
            val xlsxRes = SpreadsheetWordImporter.importFromBytes(xlsxBytes, "录音汇总表.xlsx", ::parseImportTextToParsedWordsImpl)
            assertEquals("Should only read dedicated wordlist sheet without duplicating summary row", 2, xlsxRes.words.size)
            assertEquals(listOf("衣", "移"), xlsxRes.words.map { it.text })
        } finally {
            SmartImportEngine.cleanupSessionStagingDir(sessionDir)
            tmpDir.deleteRecursively()
            db.close()
        }
    }
}
