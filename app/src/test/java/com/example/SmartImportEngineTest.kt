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
import com.example.utils.MergeAudioPolicy
import com.example.utils.PairingMatchKind
import com.example.utils.SmartImportEngine
import com.example.utils.SmartImportPlanConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartImportEngineTest {

    private fun writeStereoFloatWav(target: File, sampleRate: Int = 48000, durationMs: Int = 180) {
        target.parentFile?.mkdirs()
        val frames = (sampleRate * durationMs) / 1000
        val channels = 2
        val bytesPerSample = 4 // 32-bit IEEE float
        val blockAlign = channels * bytesPerSample
        val dataSize = frames * blockAlign
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(3) // WAVE_FORMAT_IEEE_FLOAT
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * blockAlign)
        buf.putShort(blockAlign.toShort())
        buf.putShort(32)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (i in 0 until frames) {
            val s = (0.45 * sin(2.0 * PI * 220.0 * i / sampleRate)).toFloat()
            buf.putFloat(s)
            buf.putFloat(s * 0.8f)
        }
        target.writeBytes(buf.array())
    }

    private fun writeCanonicalMono16Wav(target: File, sampleRate: Int = 44100, durationMs: Int = 160) {
        target.parentFile?.mkdirs()
        val frames = (sampleRate * durationMs) / 1000
        val dataSize = frames * 2
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(1) // Mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (i in 0 until frames) {
            val s = (12000.0 * sin(2.0 * PI * 240.0 * i / sampleRate)).toInt().toShort()
            buf.putShort(s)
        }
        target.writeBytes(buf.array())
    }

    @Test
    fun `audioWavNormalizer converts stereo 32bit float wav into canonical 44byte mono 16bit pcm`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tmpDir = File(app.cacheDir, "wav_norm_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val floatWav = File(tmpDir, "stereo_float.wav")
            val outWav = File(tmpDir, "canonical_out.wav")
            writeStereoFloatWav(floatWav, sampleRate = 48000, durationMs = 200)

            val rawInfo = AudioWavNormalizer.inspectWavHeader(floatWav)
            assertNotNull(rawInfo)
            assertEquals(false, rawInfo!!.isCanonicalMono16Pcm)
            assertEquals(2, rawInfo.channels)
            assertEquals(32, rawInfo.bitsPerSample)

            val ok = AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(floatWav, outWav)
            assertTrue(ok)
            val normInfo = AudioWavNormalizer.inspectWavHeader(outWav)
            assertNotNull(normInfo)
            assertTrue(normInfo!!.isCanonicalMono16Pcm)
            assertEquals(1, normInfo.channels)
            assertEquals(16, normInfo.bitsPerSample)
            assertEquals(48000, normInfo.sampleRate)
            assertTrue(normInfo.durationMs in 190L..210L)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `smartImportEngine pairs simultaneous wordlist and multi take audio and executes create and merge`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sessionDir = SmartImportEngine.createSessionStagingDir(app)
        val sourceDir = File(app.cacheDir, "smart_src_${System.currentTimeMillis()}").apply { mkdirs() }
        val createdProjectIds = mutableListOf<Long>()
        try {
            val repo = ProjectRepository(db)
            val spkZhangId = repo.createSpeaker("张三", "男", 54, "苏州", "", "")
            val spkLiId = repo.createSpeaker("李阿姨", "女", 61, "苏州", "", "")

            // Prepare mixed CSV wordlist + multi-format/multi-take WAV files in sourceDir
            val csvFile = File(sourceDir, "吴语声母字表.csv")
            csvFile.writeText("序号,分组,词条,提示\n1,唇音,坡,pʰu\n2,唇音,拍,pʰaʔ\n3,舌尖音,搭,taʔ\n", Charsets.UTF_8)

            val wavExact = File(sourceDir, "001_坡.wav").also { writeCanonicalMono16Wav(it) }
            val wavWord = File(sourceDir, "拍.wav").also { writeStereoFloatWav(it) }
            val wavIndex = File(sourceDir, "03.wav").also { writeCanonicalMono16Wav(it) }
            val wavAuto1 = File(sourceDir, "街_1.wav").also { writeCanonicalMono16Wav(it) }
            val wavAuto2 = File(sourceDir, "街_2.wav").also { writeCanonicalMono16Wav(it) }
            val spkSubDir = File(sourceDir, "李阿姨").apply { mkdirs() }
            val wavSubSpk = File(spkSubDir, "001_坡.wav").also { writeCanonicalMono16Wav(it) }

            // Stage via plain zip bundle (contains folder hierarchy + csv + audio) AND standalone files
            val plainBundleZip = File(sourceDir, "采集素材包.zip")
            ByteArrayOutputStream().also { bos ->
                ZipOutputStream(bos).use { zos ->
                    fun addEntry(entryPath: String, file: File) {
                        zos.putNextEntry(ZipEntry(entryPath))
                        zos.write(file.readBytes())
                        zos.closeEntry()
                    }
                    addEntry("吴语声母字表.csv", csvFile)
                    addEntry("001_坡.wav", wavExact)
                    addEntry("拍.wav", wavWord)
                    addEntry("03.wav", wavIndex)
                    addEntry("街_1.wav", wavAuto1)
                    addEntry("街_2.wav", wavAuto2)
                    addEntry("李阿姨/001_坡.wav", wavSubSpk)
                }
                plainBundleZip.writeBytes(bos.toByteArray())
            }

            val staged = SmartImportEngine.stageUrisIntoPool(
                context = app,
                sessionDir = sessionDir,
                existingStaged = emptyList(),
                uris = listOf(Uri.fromFile(plainBundleZip))
            )

            // Verify plain ZIP bundle unpacked into 1 WORDLIST_DOC + 6 AUDIO_CLIP items (not treated as opaque archive)
            assertEquals(1, staged.count { it.kind == ImportItemKind.WORDLIST_DOC })
            assertEquals(6, staged.count { it.kind == ImportItemKind.AUDIO_CLIP })

            val allSpeakers = db.speakerDao().getAllSpeakers().first()
            val config = SmartImportPlanConfig(
                targetMode = ImportTargetMode.CREATE_NEW_PROJECT,
                defaultSpeakerName = "张三",
                autoDetectSubfolderSpeakers = true,
                autoCreateWordsForUnmatchedAudio = true
            )
            val plan = SmartImportEngine.buildImportPlan(
                stagedItems = staged,
                existingWordsInMergeTarget = emptyList(),
                knownSpeakers = allSpeakers,
                config = config
            )

            assertEquals(4, plan.candidateWords.size) // 坡, 拍, 搭 + auto-created 街
            assertEquals(1, plan.autoCreatedWordCount)
            assertEquals(6, plan.readyAudioCount)

            val byDisplay = plan.audioPairings.associateBy { "${it.audioItem.relativeFolder}/${it.audioItem.displayName}".trimStart('/') }
            assertEquals(PairingMatchKind.EXACT_INDEX_AND_WORD, byDisplay.getValue("001_坡.wav").matchKind)
            assertEquals(PairingMatchKind.WORD_TEXT, byDisplay.getValue("拍.wav").matchKind)
            assertEquals(PairingMatchKind.INDEX_ONLY, byDisplay.getValue("03.wav").matchKind)
            assertEquals(PairingMatchKind.AUTO_NEW_WORD, byDisplay.getValue("街_1.wav").matchKind)
            assertEquals(2, byDisplay.getValue("街_2.wav").totalTakesForTarget)
            assertEquals("李阿姨", byDisplay.getValue("李阿姨/001_坡.wav").resolvedSpeakerName)

            // Execute Create New Project
            val result = SmartImportEngine.executeImportPlan(
                context = app,
                repository = repo,
                plan = plan,
                config = config,
                existingProjects = emptyList(),
                existingSpeakers = allSpeakers,
                defaultSpeakerId = spkZhangId
            )
            assertTrue(result.summaryMessage, result.success)
            assertNotNull(result.primaryProjectId)
            val createdProjId = result.primaryProjectId!!
            createdProjectIds.add(createdProjId)
            assertEquals(4, result.createdOrAddedWordCount)
            assertEquals(6, result.importedAudioCount)

            // Verify Zhang San has 4 recorded words (with 2 takes on 街), and Li Ayi has 1 recorded word (坡)
            val zhangWords = repo.getWordsForProjectWithSpeakerSync(createdProjId, spkZhangId)
            assertEquals(4, zhangWords.count { it.isRecorded })
            val jieWord = zhangWords.first { it.text == "街" }
            val projAudioDir = File(app.getExternalFilesDir(null), "project_$createdProjId")
            val jieTakeFiles = projAudioDir.listFiles()?.filter {
                it.name.startsWith("word_${jieWord.id}_speaker_${spkZhangId}_") && it.name.endsWith(".wav")
            }.orEmpty()
            assertEquals(2, jieTakeFiles.size)

            val liWords = repo.getWordsForProjectWithSpeakerSync(createdProjId, spkLiId)
            assertEquals(1, liWords.count { it.isRecorded })
            assertEquals("坡", liWords.first { it.isRecorded }.text)

            // Test Merge into Existing Project with ONLY_FILL_UNRECORDED + 1 new word
            val mergeSession = SmartImportEngine.createSessionStagingDir(app)
            try {
                val mergeAudioExisting = File(sourceDir, "001_坡_补.wav").also { writeCanonicalMono16Wav(it) }
                val mergeAudioNew = File(sourceDir, "飞.wav").also { writeCanonicalMono16Wav(it) }
                val mergeStaged = SmartImportEngine.stageUrisIntoPool(
                    context = app,
                    sessionDir = mergeSession,
                    existingStaged = emptyList(),
                    uris = listOf(Uri.fromFile(mergeAudioExisting), Uri.fromFile(mergeAudioNew))
                )
                val existingWords = repo.getWordsForProjectSync(createdProjId)
                val mergeConfig = SmartImportPlanConfig(
                    targetMode = ImportTargetMode.MERGE_EXISTING_PROJECT,
                    mergeTargetProjectId = createdProjId,
                    defaultSpeakerName = "张三",
                    mergeAudioPolicy = MergeAudioPolicy.ONLY_FILL_UNRECORDED,
                    autoCreateWordsForUnmatchedAudio = true
                )
                val mergePlan = SmartImportEngine.buildImportPlan(
                    stagedItems = mergeStaged,
                    existingWordsInMergeTarget = existingWords,
                    knownSpeakers = db.speakerDao().getAllSpeakers().first(),
                    config = mergeConfig
                )
                val mergeRes = SmartImportEngine.executeImportPlan(
                    context = app,
                    repository = repo,
                    plan = mergePlan,
                    config = mergeConfig,
                    existingProjects = db.projectDao().getAllProjects().first(),
                    existingSpeakers = db.speakerDao().getAllSpeakers().first(),
                    defaultSpeakerId = spkZhangId
                )
                assertTrue(mergeRes.summaryMessage, mergeRes.success)
                assertEquals(1, mergeRes.createdOrAddedWordCount) // "飞" added
                assertEquals(1, mergeRes.importedAudioCount) // "001_坡" skipped due to ONLY_FILL_UNRECORDED, "飞" imported!
            } finally {
                SmartImportEngine.cleanupSessionStagingDir(mergeSession)
            }
        } finally {
            createdProjectIds.forEach { pid ->
                File(app.getExternalFilesDir(null), "project_$pid").deleteRecursively()
            }
            SmartImportEngine.cleanupSessionStagingDir(sessionDir)
            sourceDir.deleteRecursively()
            db.close()
        }
    }
}
