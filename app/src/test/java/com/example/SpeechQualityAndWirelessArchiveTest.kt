package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.NaturalAudioCoach
import com.example.audio.SpeechQualityEvaluator
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.link.RemoteWordItem
import com.example.link.WirelessSessionArchiveManager
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeechQualityAndWirelessArchiveTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        tempDir = File(ctx.cacheDir, "speech_eval_tests_${System.nanoTime()}").apply { mkdirs() }
        WirelessSessionArchiveManager.archivesRootDir(ctx).deleteRecursively()
    }

    @After
    fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        tempDir.deleteRecursively()
        WirelessSessionArchiveManager.archivesRootDir(ctx).deleteRecursively()
    }

    private fun writePcmWav(file: File, samples: ShortArray, sampleRate: Int = 44100) {
        file.parentFile?.mkdirs()
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // Mono
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        val pcmBytes = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (s in samples) putShort(s)
        }.array()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            fos.write(pcmBytes)
        }
    }

    private fun generateRoomTone(ms: Int, amp: Int = 35, sampleRate: Int = 44100, seed: Int = 42): ShortArray {
        val rng = Random(seed)
        val n = (sampleRate * ms) / 1000
        return ShortArray(n) { rng.nextInt(-amp, amp + 1).toShort() }
    }

    private fun generatePeriodicVowel(
        ms: Int,
        peakAmp: Int = 8500,
        f0Hz: Double = 165.0,
        sampleRate: Int = 44100
    ): ShortArray {
        val n = (sampleRate * ms) / 1000
        val fadeSamples = ((sampleRate * 15) / 1000).coerceAtMost(n / 4)
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val wave = 0.65 * sin(2.0 * PI * f0Hz * t) +
                0.25 * sin(2.0 * PI * (2.0 * f0Hz) * t) +
                0.10 * sin(2.0 * PI * (3.0 * f0Hz) * t)
            val env = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > n - fadeSamples -> (n - i).toDouble() / fadeSamples
                else -> 1.0
            }
            (wave * peakAmp * env).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    @Test
    fun `evaluate detects clean speech with healthy leading and trailing silence`() {
        val samples = generateRoomTone(ms = 140, amp = 35, seed = 1) +
            generatePeriodicVowel(ms = 360, peakAmp = 8800, f0Hz = 170.0) +
            generateRoomTone(ms = 150, amp = 35, seed = 2)

        val wav = File(tempDir, "clean_speech.wav")
        writePcmWav(wav, samples)

        val report = SpeechQualityEvaluator.evaluate(wav)
        assertEquals("正常", report.primaryStatus)
        assertFalse(SpeechQualityEvaluator.isBlockingForAutoAdvance(report.primaryStatus))
        assertTrue("Expected onsetMarginMs >= 80, got ${report.onsetMarginMs}", report.onsetMarginMs >= 80)
        assertTrue("Expected offsetMarginMs >= 80, got ${report.offsetMarginMs}", report.offsetMarginMs >= 80)
    }

    @Test
    fun `evaluate detects onset vowel truncation when speech starts immediately at frame zero`() {
        val sampleRate = 44100
        val nVowel = (sampleRate * 340) / 1000
        // Starts directly at full amplitude on sample 0 (speaker spoke before pressing record)
        val abruptOnsetVowel = ShortArray(nVowel) { i ->
            val t = i.toDouble() / sampleRate
            (8500.0 * sin(2.0 * PI * 180.0 * t)).toInt().toShort()
        }
        val samples = abruptOnsetVowel + generateRoomTone(ms = 160, amp = 30, seed = 3)

        val wav = File(tempDir, "onset_truncated_vowel.wav")
        writePcmWav(wav, samples)

        val report = SpeechQualityEvaluator.evaluate(wav)
        assertEquals("开头截断", report.primaryStatus)
        assertTrue(SpeechQualityEvaluator.isBlockingForAutoAdvance(report.primaryStatus))
        assertTrue("Expected near-zero onset margin, got ${report.onsetMarginMs}", report.onsetMarginMs < 20)
    }

    @Test
    fun `evaluate detects onset voiceless sibilant truncation via high-frequency energy`() {
        val sampleRate = 44100
        val rng = Random(99)
        val fricativeSamples = ShortArray((sampleRate * 75) / 1000) { i ->
            // High-frequency voiceless sibilant [s] (~5.5 kHz band) starting right at sample 0
            val carrier = sin(2.0 * PI * 5800.0 * (i.toDouble() / sampleRate))
            val hiss = (rng.nextDouble() * 2.0 - 1.0) * 0.55 + carrier * 0.45
            (hiss * 2200.0).toInt().toShort()
        }
        val vowelSamples = generatePeriodicVowel(ms = 280, peakAmp = 9200, f0Hz = 175.0)
        val trailingRoom = generateRoomTone(ms = 160, amp = 30, seed = 4)

        val wav = File(tempDir, "onset_sibilant_truncated.wav")
        writePcmWav(wav, fricativeSamples + vowelSamples + trailingRoom)

        val report = SpeechQualityEvaluator.evaluate(wav)
        assertEquals("开头截断", report.primaryStatus)
    }

    @Test
    fun `evaluate detects offset nasal coda truncation when weak rhyme runs into EOF`() {
        val sampleRate = 44100
        val leadingRoom = generateRoomTone(ms = 150, amp = 30, seed = 5)
        // Main vowel at ~9500 peak (-10.7 dBFS)
        val mainVowel = generatePeriodicVowel(ms = 240, peakAmp = 9500, f0Hz = 190.0)
        // Nasal coda / falling tone tail (-n / 51 tone) at ~1800 peak (~14.5 dB weaker than vowel, still way above room noise),
        // cut abruptly right at the end of the file when button was released early!
        val nCoda = (sampleRate * 110) / 1000
        val abruptCoda = ShortArray(nCoda) { i ->
            val t = i.toDouble() / sampleRate
            (1850.0 * sin(2.0 * PI * 125.0 * t)).toInt().toShort()
        }

        val wav = File(tempDir, "offset_coda_truncated.wav")
        writePcmWav(wav, leadingRoom + mainVowel + abruptCoda)

        val report = SpeechQualityEvaluator.evaluate(wav)
        assertEquals("收尾截断", report.primaryStatus)
        assertTrue(SpeechQualityEvaluator.isBlockingForAutoAdvance(report.primaryStatus))
        assertTrue("Expected tiny offset margin, got ${report.offsetMarginMs}", report.offsetMarginMs < 25)
    }

    @Test
    fun `evaluate detects clipping overload low volume and empty tap`() {
        // 1. Clipped / flat-topped audio
        val clippedVowel = ShortArray((44100 * 300) / 1000) { i ->
            val raw = (42000.0 * sin(2.0 * PI * 180.0 * (i.toDouble() / 44100.0))).toInt()
            raw.coerceIn(-32760, 32760).toShort()
        }
        val clippedWav = File(tempDir, "clipped.wav")
        writePcmWav(clippedWav, generateRoomTone(120) + clippedVowel + generateRoomTone(120))
        assertEquals("削波/过载", SpeechQualityEvaluator.evaluate(clippedWav).primaryStatus)

        // 2. Very quiet speech
        val quietWav = File(tempDir, "quiet.wav")
        writePcmWav(
            quietWav,
            generateRoomTone(120, amp = 8) +
                generatePeriodicVowel(ms = 320, peakAmp = 230, f0Hz = 165.0) +
                generateRoomTone(120, amp = 8)
        )
        assertEquals("音量过低", SpeechQualityEvaluator.evaluate(quietWav, lowVolumeRmsThreshold = 250f).primaryStatus)

        // 3. Accidental short tap (100ms total)
        val tapWav = File(tempDir, "short_tap.wav")
        writePcmWav(tapWav, generateRoomTone(100, amp = 25))
        assertEquals("极短/空音", SpeechQualityEvaluator.evaluate(tapWav).primaryStatus)
    }

    @Test
    fun `NaturalAudioCoach maps technical statuses to gentle speaker language and suppresses creaky voice`() {
        val clipMsg = NaturalAudioCoach.fromStatus("削波/过载", recordedByPromptSelfControl = false)
        assertNotNull(clipMsg)
        assertTrue(clipMsg!!.speakerNaturalHint!!.contains("太响") || clipMsg.speakerNaturalHint!!.contains("稍远"))

        val offsetSelf = NaturalAudioCoach.fromStatus("收尾截断", recordedByPromptSelfControl = true)
        assertNotNull(offsetSelf)
        assertTrue(offsetSelf!!.speakerNaturalHint!!.contains("松手"))

        val offsetRemote = NaturalAudioCoach.fromStatus("收尾截断", recordedByPromptSelfControl = false)
        assertNotNull(offsetRemote)
        assertFalse(offsetRemote!!.speakerNaturalHint!!.contains("松手"))

        // Dialectal creaky voice must NEVER show speaker coaching on 看读端, only investigator hint on 遥控端
        val creaky = NaturalAudioCoach.fromStatus("嘎裂声", recordedByPromptSelfControl = true)
        assertNotNull(creaky)
        assertNull(creaky!!.speakerNaturalHint)
        assertNotNull(creaky.controllerTechnicalHint)

        assertNull(NaturalAudioCoach.fromStatus("正常", recordedByPromptSelfControl = true))
    }

    @Test
    fun `WirelessSessionArchiveManager stores isolated capsule exports zip and imports without colliding with existing db`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val createdProjectAudioDirs = mutableListOf<File>()
        try {
            val repo = ProjectRepository(db)

            // Pre-populate heterogeneous local Project #1 and Speaker #1 on the peer device
            val localSpkId = repo.createSpeaker("本地王老师", "女", 41, "南京", "本机原有发音人", "")
            val localProjId = repo.createProject(
                name = "南京入声字表",
                description = "本机原有独立项目",
                wordTexts = listOf("一", "七", "八"),
                speakerId = localSpkId
            )
            createdProjectAudioDirs.add(File(app.getExternalFilesDir(null), "project_$localProjId"))

            // Simulate wireless takes arriving from a completely different Host project ("温州声调调查" / "林阿公")
            val srcWav1 = File(tempDir, "remote_take_1.wav")
            val srcWav2 = File(tempDir, "remote_take_2.wav")
            val cleanPcm = generateRoomTone(130) + generatePeriodicVowel(320, peakAmp = 8200) + generateRoomTone(140)
            writePcmWav(srcWav1, cleanPcm)
            writePcmWav(srcWav2, cleanPcm)

            val snapshotWords = listOf(
                RemoteWordItem(seqPos = 0, wordId = 9001L, text = "东", remark = "阴平", groupName = "平声", isRecorded = true),
                RemoteWordItem(seqPos = 1, wordId = 9002L, text = "同", remark = "阳平", groupName = "平声", isRecorded = true),
                RemoteWordItem(seqPos = 2, wordId = 9003L, text = "懂", remark = "阴上", groupName = "上声", isRecorded = false)
            )

            val capDir1 = WirelessSessionArchiveManager.archiveTakeIntoCapsule(
                context = app,
                sessionCode = "849201",
                projectName = "温州声调调查",
                speakerName = "林阿公",
                speakerGender = "男",
                speakerAge = 73,
                speakerDialect = "温州鹿城",
                allSnapshotWords = snapshotWords,
                seqPos = 0,
                wordText = "东",
                wordRemark = "阴平",
                wordGroup = "平声",
                speakerNote = "[tʊŋ33]",
                sourceWavFile = srcWav1,
                deleteSourceAfterCopy = false
            )
            val capDir2 = WirelessSessionArchiveManager.archiveTakeIntoCapsule(
                context = app,
                sessionCode = "849201",
                projectName = "温州声调调查",
                speakerName = "林阿公",
                speakerGender = "男",
                speakerAge = 73,
                speakerDialect = "温州鹿城",
                allSnapshotWords = snapshotWords,
                seqPos = 1,
                wordText = "同",
                wordRemark = "阳平",
                wordGroup = "平声",
                speakerNote = "[dʊŋ31]",
                sourceWavFile = srcWav2,
                deleteSourceAfterCopy = false
            )

            assertNotNull(capDir1)
            assertNotNull(capDir2)
            assertTrue(capDir1!!.exists())
            assertTrue(capDir2!!.exists())
            val capsuleDir = capDir1.parentFile!!.parentFile!!
            assertEquals(capsuleDir, capDir2.parentFile!!.parentFile!!)

            val summaries = WirelessSessionArchiveManager.listCapsules(app)
            assertEquals(1, summaries.size)
            assertEquals(capsuleDir, summaries[0].capsuleDir)
            assertEquals("温州声调调查", summaries[0].projectName)
            assertEquals("林阿公", summaries[0].speakerName)
            assertEquals(2, summaries[0].recordedTakeCount)
            assertEquals(3, summaries[0].totalWordsInSnapshot)

            // Verify genuine OOXML .xlsx checklist and manifest inside capsule
            val xlsxFile = File(capsuleDir, "录音核对清单.xlsx")
            assertTrue(xlsxFile.exists())
            val xlsxBytes = xlsxFile.readBytes()
            assertEquals(0x50.toByte(), xlsxBytes[0]) // 'P'
            assertEquals(0x4B.toByte(), xlsxBytes[1]) // 'K'
            val capsuleSheets = com.example.utils.XlsxSpreadsheetWriter.readAllSheets(xlsxBytes)
            assertTrue(capsuleSheets.isNotEmpty())
            val sheetRows = capsuleSheets.values.first()
            assertTrue(sheetRows.size >= 4) // header + 3 words

            // Verify ZIP export
            val zipOut = ByteArrayOutputStream()
            val exportedFiles = WirelessSessionArchiveManager.exportCapsuleToZip(capsuleDir, zipOut)
            assertEquals(4, exportedFiles) // 2 wav + 1 xlsx + 1 manifest
            val zipEntries = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(zipOut.toByteArray())).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    zipEntries.add(e.name)
                    e = zis.nextEntry
                }
            }
            assertTrue(zipEntries.any { it.endsWith("/session_manifest.json") })
            assertTrue(zipEntries.any { it.endsWith("/录音核对清单.xlsx") })
            assertEquals(2, zipEntries.count { it.endsWith(".wav") })

            // Verify local DB was NOT touched before explicit on-demand import
            assertEquals(listOf("一", "七", "八"), repo.getWordsForProjectSync(localProjId).map { it.text })

            // Import capsule on-demand as an independent local Project
            val importRes = WirelessSessionArchiveManager.importCapsuleAsLocalProject(app, repo, capsuleDir)
            assertTrue("Import failed: ${importRes.exceptionOrNull()}", importRes.isSuccess)
            val importedProjId = importRes.getOrThrow()
            createdProjectAudioDirs.add(File(app.getExternalFilesDir(null), "project_$importedProjId"))
            assertTrue(importedProjId != localProjId)

            // Original local project is 100% untouched
            assertEquals(listOf("一", "七", "八"), repo.getWordsForProjectSync(localProjId).map { it.text })

            // Imported project has all 3 words (2 recorded + 1 unrecorded) and speaker notes intact!
            val importedProj = repo.getProjectById(importedProjId)!!
            val importedSpkId = importedProj.speakerId
            val importedWords = repo.getWordsForProjectWithSpeakerSync(importedProjId, importedSpkId)
                .sortedBy { it.originalIndex }
            assertEquals(3, importedWords.size)
            assertEquals("东", importedWords[0].text)
            assertTrue(importedWords[0].isRecorded)
            assertEquals("[tʊŋ33]", importedWords[0].speakerNote)
            assertEquals("正常", importedWords[0].qualityStatus)

            assertEquals("同", importedWords[1].text)
            assertTrue(importedWords[1].isRecorded)
            assertEquals("[dʊŋ31]", importedWords[1].speakerNote)

            assertEquals("懂", importedWords[2].text)
            assertFalse(importedWords[2].isRecorded)
        } finally {
            createdProjectAudioDirs.forEach { it.deleteRecursively() }
            db.close()
        }
    }

    @Test
    fun `RemoteSessionSnapshot round-trips shuffle state and capsule preserves canonical originalIndex when shuffled`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val createdProjectAudioDirs = mutableListOf<File>()
        try {
            val repo = ProjectRepository(db)

            // 1. Verify RemoteSessionSnapshot JSON round-trip of isShuffleEnabled, shufflingMode, and originalIndex
            val shuffledSnapshotWords = listOf(
                RemoteWordItem(seqPos = 0, wordId = 303L, text = "懂", remark = "阴上", groupName = "上声", originalIndex = 2),
                RemoteWordItem(seqPos = 1, wordId = 301L, text = "东", remark = "阴平", groupName = "平声", originalIndex = 0),
                RemoteWordItem(seqPos = 2, wordId = 302L, text = "同", remark = "阳平", groupName = "平声", originalIndex = 1)
            )
            val snap = com.example.link.RemoteSessionSnapshot(
                hasSnapshotArrived = true,
                sessionCode = "SHUF99",
                projectId = 77L,
                projectName = "乱序无线测试",
                speakerId = 12L,
                speakerName = "陈阿婆",
                currentSeqPos = 0,
                totalWords = 3,
                recordedCount = 0,
                isShuffleEnabled = true,
                shufflingMode = "BLOCK_BALANCED",
                words = shuffledSnapshotWords
            )
            val parsedFull = com.example.link.RemoteSessionSnapshot.fromJson(snap.toJson(includeWordList = true))
            assertTrue(parsedFull.isShuffleEnabled)
            assertEquals("BLOCK_BALANCED", parsedFull.shufflingMode)
            assertEquals(listOf("懂", "东", "同"), parsedFull.words.map { it.text })
            assertEquals(listOf(2, 0, 1), parsedFull.words.map { it.originalIndex })

            // Incremental snapshot (includeWordList = false) also preserves shuffle flags & fallback words
            val parsedLight = com.example.link.RemoteSessionSnapshot.fromJson(
                snap.copy(currentSeqPos = 2).toJson(includeWordList = false),
                fallbackWords = parsedFull.words
            )
            assertTrue(parsedLight.isShuffleEnabled)
            assertEquals("BLOCK_BALANCED", parsedLight.shufflingMode)
            assertEquals(2, parsedLight.currentSeqPos)
            assertEquals("同", parsedLight.currentWord?.text)
            assertEquals(1, parsedLight.currentWord?.originalIndex)

            // 2. Archive takes when session starts unshuffled (slot 0 = 东) and then switches to shuffled (seqPos 0 -> originalIndex 2 = 懂)
            val cleanPcm = generateRoomTone(130) + generatePeriodicVowel(320, peakAmp = 8200) + generateRoomTone(140)
            val wavDong = File(tempDir, "take_dong.wav").also { writePcmWav(it, cleanPcm) }
            val wavDong3 = File(tempDir, "take_dong3.wav").also { writePcmWav(it, cleanPcm) }

            val unshuffledWords = listOf(
                RemoteWordItem(seqPos = 0, wordId = 301L, text = "东", remark = "阴平", groupName = "平声", originalIndex = 0),
                RemoteWordItem(seqPos = 1, wordId = 302L, text = "同", remark = "阳平", groupName = "平声", originalIndex = 1),
                RemoteWordItem(seqPos = 2, wordId = 303L, text = "懂", remark = "阴上", groupName = "上声", originalIndex = 2)
            )
            WirelessSessionArchiveManager.archiveTakeIntoCapsule(
                context = app,
                sessionCode = "SHUF99",
                projectName = "乱序无线测试",
                speakerName = "陈阿婆",
                allSnapshotWords = unshuffledWords,
                seqPos = 0,
                wordText = "东",
                wordRemark = "阴平",
                wordGroup = "平声",
                speakerNote = "[tʊŋ55]",
                sourceWavFile = wavDong,
                deleteSourceAfterCopy = false
            )
            val dest2 = WirelessSessionArchiveManager.archiveTakeIntoCapsule(
                context = app,
                sessionCode = "SHUF99",
                projectName = "乱序无线测试",
                speakerName = "陈阿婆",
                allSnapshotWords = shuffledSnapshotWords,
                seqPos = 2, // canonical originalIndex of "懂"
                wordText = "懂",
                wordRemark = "阴上",
                wordGroup = "上声",
                speakerNote = "[tʊŋ35]",
                sourceWavFile = wavDong3,
                deleteSourceAfterCopy = false
            )
            assertNotNull(dest2)
            val capsuleDir = dest2!!.parentFile!!.parentFile!!
            val importRes = WirelessSessionArchiveManager.importCapsuleAsLocalProject(app, repo, capsuleDir)
            assertTrue(importRes.isSuccess)
            val importedProjId = importRes.getOrThrow()
            createdProjectAudioDirs.add(File(app.getExternalFilesDir(null), "project_$importedProjId"))
            val importedProj = repo.getProjectById(importedProjId)!!
            val importedWords = repo.getWordsForProjectWithSpeakerSync(importedProjId, importedProj.speakerId)
                .sortedBy { it.originalIndex }

            assertEquals(listOf("东", "同", "懂"), importedWords.map { it.text })
            assertTrue("First word '东' must still remain recorded after mid-session shuffle", importedWords[0].isRecorded)
            assertEquals("[tʊŋ55]", importedWords[0].speakerNote)
            assertFalse("Second word '同' was not recorded", importedWords[1].isRecorded)
            assertTrue("Third word '懂' must be recorded in canonical slot 2", importedWords[2].isRecorded)
            assertEquals("[tʊŋ35]", importedWords[2].speakerNote)
        } finally {
            createdProjectAudioDirs.forEach { it.deleteRecursively() }
            db.close()
        }
    }

    @Test
    fun `importWirelessCapsuleAsProject selects imported project and removes consumed capsule from home banner`(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ProjectRepository(db)
        val vm = com.example.viewmodel.RecordingViewModel(app, repo)
        val createdProjectAudioDirs = mutableListOf<File>()
        try {
            val cleanPcm = generateRoomTone(120) + generatePeriodicVowel(320, peakAmp = 8500) + generateRoomTone(130)
            val wavFile = File(tempDir, "take_vowel.wav").also { writePcmWav(it, cleanPcm) }
            val destWav = WirelessSessionArchiveManager.archiveTakeIntoCapsule(
                context = app,
                sessionCode = "VOWEL02",
                projectName = "基本元音舌位字表",
                speakerName = "发音人02",
                allSnapshotWords = listOf(
                    RemoteWordItem(seqPos = 0, wordId = 1L, text = "衣", remark = "[i]", originalIndex = 0)
                ),
                seqPos = 0,
                wordText = "衣",
                wordRemark = "[i]",
                wordGroup = "",
                speakerNote = "",
                sourceWavFile = wavFile,
                deleteSourceAfterCopy = false
            )
            assertNotNull(destWav)
            val capsuleDir = destWav!!.parentFile!!.parentFile!!
            assertEquals(1, vm.listWirelessSessionCapsules().size)
            val initialRefreshVer = vm.wirelessLink.capsulesRefreshVersion.value

            var callbackResult: Pair<Boolean, String>? = null
            vm.importWirelessCapsuleAsProject(capsuleDir) { ok, msg ->
                callbackResult = ok to msg
            }

            val deadline = System.currentTimeMillis() + 5_000L
            while (callbackResult == null && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            assertNotNull("Expected importWirelessCapsuleAsProject callback to complete", callbackResult)
            assertTrue("Import callback failed: ${callbackResult?.second}", callbackResult!!.first)
            assertFalse("Consumed capsule directory must be removed after import", capsuleDir.exists())
            assertTrue("Expected wirelessCapsules list to become empty after import", vm.listWirelessSessionCapsules().isEmpty())
            assertTrue(
                "Expected capsulesRefreshVersion to increment so home banner updates immediately",
                vm.wirelessLink.capsulesRefreshVersion.value > initialRefreshVer
            )

            val selectedProj = vm.currentProject.value
            assertNotNull("Expected imported project to be selected immediately", selectedProj)
            createdProjectAudioDirs.add(File(app.getExternalFilesDir(null), "project_${selectedProj!!.id}"))
            assertTrue(selectedProj.name.startsWith("基本元音舌位字表"))
            assertEquals("发音人02", vm.currentSpeaker.value?.name)

            // Also verify manifest-less wav-only backup folder still imports cleanly
            val orphanCapsuleDir = File(WirelessSessionArchiveManager.archivesRootDir(app), "基本元音舌位字表_发音人02_orphan")
            val orphanWav = File(File(orphanCapsuleDir, "wav"), "0001_乌_u_120000.wav")
            writePcmWav(orphanWav, cleanPcm)
            assertEquals(1, vm.listWirelessSessionCapsules().size)

            val fallbackRes = WirelessSessionArchiveManager.importCapsuleAsLocalProject(app, repo, orphanCapsuleDir)
            assertTrue("Expected manifest-less wav backup to import cleanly: ${fallbackRes.exceptionOrNull()}", fallbackRes.isSuccess)
            createdProjectAudioDirs.add(File(app.getExternalFilesDir(null), "project_${fallbackRes.getOrThrow()}"))
        } finally {
            vm.wirelessLink.cancelOrDisconnect()
            vm.viewModelScope.cancel()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            createdProjectAudioDirs.forEach { it.deleteRecursively() }
            db.close()
        }
    }

    private fun synthesizeDynamicKlattSyllable(
        durMs: Int,
        sampleRate: Int = 44100,
        f0At: (Double) -> Double,
        f1At: (Double) -> Double,
        f2At: (Double) -> Double,
        f3At: (Double) -> Double = { 2650.0 },
        ampAt: (Double) -> Double = { t -> kotlin.math.sqrt(kotlin.math.sin(PI * t.coerceIn(0.0, 1.0))) },
        creakyMidRegion: Boolean = false
    ): ShortArray {
        val nSamples = (sampleRate * durMs) / 1000
        val glottal = DoubleArray(nSamples)
        var phase = 0.0
        var cycleCount = 0
        for (i in 0 until nSamples) {
            val tNorm = i.toDouble() / nSamples.coerceAtLeast(1)
            val baseF0 = f0At(tNorm)
            val inCreak = creakyMidRegion && tNorm in 0.34..0.64
            // Simulate realistic vocal fry: period alternation (subharmonics) + cycle-to-cycle jitter
            val effF0 = if (inCreak) {
                val subFactor = if (cycleCount % 2 == 0) 0.62 else 1.18
                baseF0 * subFactor
            } else {
                baseF0
            }
            phase += effF0 / sampleRate
            if (phase >= 1.0) {
                phase -= 1.0
                cycleCount++
            }
            val pulse = when {
                phase < 0.38 -> 0.5 * (1.0 - kotlin.math.cos(PI * phase / 0.38))
                phase < 0.54 -> kotlin.math.cos(PI * (phase - 0.38) / (2.0 * 0.16))
                else -> 0.0
            }
            val creakAmpScale = if (inCreak) {
                if (cycleCount % 2 == 0) 0.36 else 0.52
            } else 1.0
            glottal[i] = (pulse + 0.08 * sin(2.0 * PI * phase)) * ampAt(tNorm) * creakAmpScale
        }

        // Time-varying 2-pole cascade resonators (F1 -> F2 -> F3)
        fun cascadeResonator(inp: DoubleArray, freqFn: (Double) -> Double, bwHz: Double): DoubleArray {
            val out = DoubleArray(inp.size)
            val r = kotlin.math.exp(-PI * bwHz / sampleRate)
            val a2 = -r * r
            var y1 = 0.0
            var y2 = 0.0
            for (i in inp.indices) {
                val tNorm = i.toDouble() / inp.size.coerceAtLeast(1)
                val freq = freqFn(tNorm).coerceIn(180.0, sampleRate * 0.44)
                val a1 = 2.0 * r * kotlin.math.cos(2.0 * PI * freq / sampleRate)
                val b0 = 1.0 - a1 - a2
                val y = b0 * inp[i] + a1 * y1 + a2 * y2
                out[i] = y
                y2 = y1
                y1 = y
            }
            return out
        }

        val r1 = cascadeResonator(glottal, f1At, 75.0)
        val r2 = cascadeResonator(r1, f2At, 105.0)
        val r3 = cascadeResonator(r2, f3At, 150.0)

        // Lip radiation (+6 dB/oct) & peak normalization
        val radiated = DoubleArray(nSamples)
        var peak = 1e-6
        for (i in 1 until nSamples) {
            radiated[i] = r3[i] - 0.95 * r3[i - 1]
            val a = kotlin.math.abs(radiated[i])
            if (a > peak) peak = a
        }
        return ShortArray(nSamples) { idx ->
            ((radiated[idx] / peak) * 18500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    @Test
    fun `extractSyllableUnits strips Pinyin IPA slashes numbering and keeps single-syllable items as 1 unit`() {
        assertEquals(listOf("啊"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("啊 a"))
        assertEquals(listOf("哀"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("哀 ai"))
        assertEquals(listOf("衣"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("衣 /i/"))
        assertEquals(listOf("衣"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("衣 | [i55]"))
        assertEquals(listOf("衣"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("01 衣"))
        assertEquals(listOf("衣"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("衣1"))
        assertEquals(1, com.example.audio.AcousticAnalyzer.extractSyllableUnits("/ai/").size)
        assertEquals(1, com.example.audio.AcousticAnalyzer.extractSyllableUnits("/aːi/").size)
        assertEquals(1, com.example.audio.AcousticAnalyzer.extractSyllableUnits("ɿ").size)
        assertEquals(1, com.example.audio.AcousticAnalyzer.extractSyllableUnits("ʅ").size)
        assertEquals(1, com.example.audio.AcousticAnalyzer.extractSyllableUnits("ma214").size)
        assertEquals(1, com.example.audio.AcousticAnalyzer.extractSyllableUnits("ma˥˩").size)
        assertEquals(listOf("葡", "萄"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("葡萄 putao"))
        assertEquals(listOf("T", "恤"), com.example.audio.AcousticAnalyzer.extractSyllableUnits("T恤"))
    }

    @Test
    fun `creaky dipping 214 monosyllable stays 1 syllable while pause-separated utterance promotes to 2`() {
        // 1. Long 540ms Tone [214] monosyllable with mid-syllable creaky voice (vocal fry) & amplitude dip
        val creaky214 = generateRoomTone(110, amp = 25, seed = 11) +
            synthesizeDynamicKlattSyllable(
                durMs = 540,
                f0At = { t ->
                    when {
                        t < 0.45 -> 185.0 - (185.0 - 96.0) * (t / 0.45)
                        else -> 96.0 + (215.0 - 96.0) * ((t - 0.45) / 0.55)
                    }
                },
                f1At = { 740.0 },
                f2At = { 1280.0 },
                creakyMidRegion = true
            ) +
            generateRoomTone(120, amp = 25, seed = 12)

        val creakyWav = File(tempDir, "mono_creaky_214.wav")
        writePcmWav(creakyWav, creaky214)

        val parts = com.example.audio.AcousticAnalyzer.extractSyllableUnits("马 ma214")
        assertEquals(1, parts.size)

        val creakyRes = com.example.audio.AcousticAnalyzer.analyzeByGender(
            file = creakyWav,
            speakerGender = "女",
            segmentCount = parts.size,
            pitchRange = Pair(105f, 235f),
            segmentLabels = parts
        )
        assertNotNull(creakyRes)
        assertEquals(
            "Creaky [214] monosyllable must NEVER be split into multiple syllables",
            1,
            creakyRes!!.segmentCount
        )

        // 2. Genuine 2-syllable utterance separated by 85ms silent closure still promotes cleanly when hint == 1
        val genuineTwo = generateRoomTone(100, amp = 20, seed = 21) +
            synthesizeDynamicKlattSyllable(durMs = 240, f0At = { 215.0 }, f1At = { 750.0 }, f2At = { 1280.0 }) +
            generateRoomTone(85, amp = 20, seed = 22) +
            synthesizeDynamicKlattSyllable(durMs = 250, f0At = { t -> 225.0 - 75.0 * t }, f1At = { 310.0 }, f2At = { 2260.0 }) +
            generateRoomTone(100, amp = 20, seed = 23)
        val twoWav = File(tempDir, "genuine_two_pause.wav")
        writePcmWav(twoWav, genuineTwo)

        val twoRes = com.example.audio.AcousticAnalyzer.analyzeByGender(
            file = twoWav,
            speakerGender = "女",
            segmentCount = 1,
            pitchRange = Pair(135f, 245f),
            segmentLabels = listOf("单条")
        )
        assertNotNull(twoRes)
        assertEquals(2, twoRes!!.segmentCount)
    }

    @Test
    fun `pure objective formant trajectory classifies steady vowels with consonant locus and steep 51 tone as MONOPHTHONG and real glides as DIPHTHONG or TRIPHTHONG`() {
        // 1. Monophthongs [a], [i], [u], [ɤ] with 35ms initial consonant F2 locus transition + steep [51] falling F0 (255 -> 115 Hz)
        data class MonoSpec(val name: String, val f1: Double, val f2: Double, val locusF2: Double)
        val monoSpecs = listOf(
            MonoSpec("a", f1 = 760.0, f2 = 1280.0, locusF2 = 1720.0),
            MonoSpec("i", f1 = 295.0, f2 = 2310.0, locusF2 = 1820.0),
            MonoSpec("u", f1 = 325.0, f2 = 820.0, locusF2 = 1320.0),
            MonoSpec("e_back", f1 = 485.0, f2 = 1240.0, locusF2 = 1680.0)
        )

        for (spec in monoSpecs) {
            val samples = generateRoomTone(100, amp = 20) +
                synthesizeDynamicKlattSyllable(
                    durMs = 340,
                    f0At = { t -> 255.0 - 140.0 * t }, // Steep [51] falling F0!
                    f1At = { t -> if (t < 0.10) (spec.f1 * 0.78 + spec.f1 * 0.22 * (t / 0.10)) else spec.f1 },
                    f2At = { t ->
                        when {
                            t < 0.11 -> spec.locusF2 + (spec.f2 - spec.locusF2) * (t / 0.11) // 37ms consonant locus
                            t > 0.90 -> spec.f2 + (1450.0 - spec.f2) * ((t - 0.90) / 0.10) * 0.25 // slight coda relaxation
                            else -> spec.f2
                        }
                    }
                ) +
                generateRoomTone(100, amp = 20)

            val wav = File(tempDir, "mono_${spec.name}.wav")
            writePcmWav(wav, samples)
            val res = com.example.audio.AcousticAnalyzer.analyzeByGender(
                file = wav,
                speakerGender = "女",
                segmentCount = 1,
                segmentLabels = listOf(spec.name)
            )
            assertNotNull(res)
            val traj = res!!.formantTrajectories.first()
            assertEquals(
                "Expected monophthong [${spec.name}] to be MONOPHTHONG, got ${traj.vowelType} (span=${traj.barkGlideSpan}, anchors=${traj.anchors.map { it.f1Hz to it.f2Hz }})",
                com.example.audio.VowelGlideType.MONOPHTHONG,
                traj.vowelType
            )
        }

        // 2. Genuine Diphthong [ai]: continuous movement across vowel core (770, 1260) -> (310, 2260)
        val aiSamples = generateRoomTone(100, amp = 20) +
            synthesizeDynamicKlattSyllable(
                durMs = 360,
                f0At = { t -> 235.0 - 15.0 * t },
                f1At = { t -> 770.0 + (310.0 - 770.0) * t.coerceIn(0.0, 1.0) },
                f2At = { t -> 1260.0 + (2260.0 - 1260.0) * t.coerceIn(0.0, 1.0) }
            ) +
            generateRoomTone(100, amp = 20)
        val aiWav = File(tempDir, "diph_ai.wav")
        writePcmWav(aiWav, aiSamples)
        val aiRes = com.example.audio.AcousticAnalyzer.analyzeByGender(aiWav, "女", 1, segmentLabels = listOf("哀"))!!
        val aiTraj = aiRes.formantTrajectories.first()
        assertEquals(
            "Expected [ai] to be DIPHTHONG, got ${aiTraj.vowelType} (span=${aiTraj.barkGlideSpan}, anchors=${aiTraj.anchors.map { it.f1Hz to it.f2Hz }})",
            com.example.audio.VowelGlideType.DIPHTHONG,
            aiTraj.vowelType
        )

        // 3. Genuine Back Diphthong [ou]: (560, 1140) -> (325, 760)
        val ouSamples = generateRoomTone(100, amp = 20) +
            synthesizeDynamicKlattSyllable(
                durMs = 360,
                f0At = { t -> 225.0 - 10.0 * t },
                f1At = { t -> 560.0 + (325.0 - 560.0) * t.coerceIn(0.0, 1.0) },
                f2At = { t -> 1140.0 + (760.0 - 1140.0) * t.coerceIn(0.0, 1.0) }
            ) +
            generateRoomTone(100, amp = 20)
        val ouWav = File(tempDir, "diph_ou.wav")
        writePcmWav(ouWav, ouSamples)
        val ouRes = com.example.audio.AcousticAnalyzer.analyzeByGender(ouWav, "女", 1, segmentLabels = listOf("欧"))!!
        val ouTraj = ouRes.formantTrajectories.first()
        assertEquals(
            "Expected [ou] to be DIPHTHONG, got ${ouTraj.vowelType} (span=${ouTraj.barkGlideSpan}, anchors=${ouTraj.anchors.map { it.f1Hz to it.f2Hz }})",
            com.example.audio.VowelGlideType.DIPHTHONG,
            ouTraj.vowelType
        )

        // 3b. Genuine Narrow Front Diphthong [ei]: (465, 1940) -> (295, 2295)
        val eiSamples = generateRoomTone(100, amp = 20) +
            synthesizeDynamicKlattSyllable(
                durMs = 360,
                f0At = { t -> 225.0 - 10.0 * t },
                f1At = { t -> 465.0 + (295.0 - 465.0) * t.coerceIn(0.0, 1.0) },
                f2At = { t -> 1940.0 + (2295.0 - 1940.0) * t.coerceIn(0.0, 1.0) }
            ) +
            generateRoomTone(100, amp = 20)
        val eiWav = File(tempDir, "diph_ei.wav")
        writePcmWav(eiWav, eiSamples)
        val eiRes = com.example.audio.AcousticAnalyzer.analyzeByGender(eiWav, "女", 1, segmentLabels = listOf("杯"))!!
        val eiTraj = eiRes.formantTrajectories.first()
        assertEquals(
            "Expected [ei] to be DIPHTHONG, got ${eiTraj.vowelType} (span=${eiTraj.barkGlideSpan}, anchors=${eiTraj.anchors.map { it.f1Hz to it.f2Hz }})",
            com.example.audio.VowelGlideType.DIPHTHONG,
            eiTraj.vowelType
        )

        // 4. Genuine Triphthong [iau]: (310, 2260) -> (760, 1320) -> (340, 820)
        val iauSamples = generateRoomTone(100, amp = 20) +
            synthesizeDynamicKlattSyllable(
                durMs = 420,
                f0At = { 220.0 },
                f1At = { t ->
                    if (t < 0.5) 310.0 + (760.0 - 310.0) * (t / 0.5)
                    else 760.0 + (340.0 - 760.0) * ((t - 0.5) / 0.5)
                },
                f2At = { t ->
                    if (t < 0.5) 2260.0 + (1320.0 - 2260.0) * (t / 0.5)
                    else 1320.0 + (820.0 - 1320.0) * ((t - 0.5) / 0.5)
                }
            ) +
            generateRoomTone(100, amp = 20)
        val iauWav = File(tempDir, "tri_iau.wav")
        writePcmWav(iauWav, iauSamples)
        val iauRes = com.example.audio.AcousticAnalyzer.analyzeByGender(iauWav, "女", 1, segmentLabels = listOf("腰"))!!
        val iauTraj = iauRes.formantTrajectories.first()
        assertEquals(
            "Expected [iau] to be TRIPHTHONG, got ${iauTraj.vowelType} (span=${iauTraj.barkGlideSpan}, anchors=${iauTraj.anchors.map { it.f1Hz to it.f2Hz }})",
            com.example.audio.VowelGlideType.TRIPHTHONG,
            iauTraj.vowelType
        )
    }

    @Test
    fun `multi speaker comparison engine calibrates partial-progress speaker via shared anchors, detects tone and glide splits, and exports cross-speaker excel matrices`() {
        fun mkWord(id: Long, idx: Int, text: String, group: String) = com.example.data.Word(
            id = id,
            projectId = 1L,
            originalIndex = idx,
            text = text,
            groupName = group,
            isRecorded = true,
            recordingFile = "w_${id}.wav",
            durationMs = 420L
        )

        fun mkToken(
            word: com.example.data.Word,
            spkId: Long,
            spkName: String,
            gKey: String,
            gLbl: String,
            f1: Int,
            f2: Int,
            vType: com.example.audio.VowelGlideType = com.example.audio.VowelGlideType.MONOPHTHONG,
            glideBark: Float = 0.35f,
            naiveZ1: Float = 0f,
            naiveZ2: Float = 0f
        ) = com.example.audio.NormalizedVowelToken(
            word = word,
            speakerId = spkId,
            speakerName = spkName,
            groupKey = gKey,
            groupLabel = gLbl,
            displayLabel = word.text,
            vowelType = vType,
            onsetF1Hz = f1,
            onsetF2Hz = f2,
            nucleusF1Hz = f1,
            nucleusF2Hz = f2,
            nucleusF3Hz = 2550,
            targetF1Hz = if (vType == com.example.audio.VowelGlideType.MONOPHTHONG) f1 else (f1 - 150),
            targetF2Hz = if (vType == com.example.audio.VowelGlideType.MONOPHTHONG) f2 else (f2 + 380),
            barkGlideSpan = glideBark,
            trajectorySummary = "test",
            anchorsHz = listOf(f1 to f2),
            z1Onset = naiveZ1,
            z2Onset = naiveZ2,
            z1Nucleus = naiveZ1,
            z2Nucleus = naiveZ2,
            z1Target = naiveZ1,
            z2Target = naiveZ2,
            anchorsLobanov = listOf(naiveZ1 to naiveZ2)
        )

        fun mkTone(
            word: com.example.data.Word,
            chao: String,
            pts: List<Float>,
            checked: Boolean = false
        ) = com.example.audio.CanonicalToneMember(
            word = word,
            rawChao = chao,
            rawToneCode = chao,
            canonicalChao = chao,
            canonicalCategoryName = word.groupName,
            contourPoints = pts,
            meanF0Hz = 185f,
            voicedDurationMs = 310,
            isCheckedTone = checked,
            deviationSigma = 0.2f,
            isOutlier = false
        )

        val w1 = mkWord(1L, 0, "衣", "阴平")
        val w2 = mkWord(2L, 1, "耶", "阳平")
        val w3 = mkWord(3L, 2, "杯", "阴平")
        val w4 = mkWord(4L, 3, "乌", "上声")
        val w5 = mkWord(5L, 4, "波", "去声")
        val w6 = mkWord(6L, 5, "巴", "阴平")
        val w7 = mkWord(7L, 6, "妈", "阴平")

        // Speaker 1 (Male): full 7-word vowel space spanning [i, e, ei, u, o, a]
        val spk1 = com.example.data.Speaker(id = 10L, name = "男受试者A", gender = "男", age = 56, dialect = "老派", groupName = "老派")
        val spk1Tokens = listOf(
            mkToken(w1, 10L, spk1.name, "v_i", "前高[i]", 290, 2150),
            mkToken(w2, 10L, spk1.name, "v_e", "前半高[e]", 430, 1920),
            mkToken(w3, 10L, spk1.name, "v_ei", "前响[ei]", 445, 1890, com.example.audio.VowelGlideType.DIPHTHONG, 1.85f),
            mkToken(w4, 10L, spk1.name, "v_u", "后高[u]", 320, 780),
            mkToken(w5, 10L, spk1.name, "v_o", "后中[o]", 490, 880),
            mkToken(w6, 10L, spk1.name, "v_a", "央低[a]", 750, 1260),
            mkToken(w7, 10L, spk1.name, "v_a", "央低[a]", 735, 1290)
        )
        val spk1Tones = mapOf(
            1L to mkTone(w1, "55", listOf(0.92f, 0.92f, 0.91f, 0.90f, 0.90f)),
            2L to mkTone(w2, "35", listOf(0.45f, 0.56f, 0.68f, 0.82f, 0.94f)),
            3L to mkTone(w3, "55", listOf(0.90f, 0.90f, 0.89f, 0.88f, 0.88f)),
            4L to mkTone(w4, "214", listOf(0.32f, 0.14f, 0.10f, 0.36f, 0.68f))
        )

        // Speaker 2 (Female): ONLY recorded 4 words (predominantly front vowels w1, w2, w3 + w4 with divergent tone [51] and monophthongized [e:] on w3!)
        // Without shared-anchor calibration, w2 (F1=500Hz female [e]) would be distorted to extreme +Z1 (low [a])!
        val spk2 = com.example.data.Speaker(id = 20L, name = "女受试者B", gender = "女", age = 23, dialect = "新派", groupName = "新派")
        val spk2Tokens = listOf(
            mkToken(w1, 20L, spk2.name, "v_i", "前高[i]", 335, 2510, naiveZ1 = -1.2f, naiveZ2 = 0.8f),
            mkToken(w2, 20L, spk2.name, "v_e", "前半高[e]", 495, 2230, naiveZ1 = 1.35f, naiveZ2 = -0.3f),
            mkToken(w3, 20L, spk2.name, "v_ei", "前响[ei]", 485, 2240, com.example.audio.VowelGlideType.MONOPHTHONG, 0.42f, naiveZ1 = 1.15f, naiveZ2 = -0.2f),
            mkToken(w4, 20L, spk2.name, "v_u", "后高[u]", 370, 910, naiveZ1 = -0.7f, naiveZ2 = -1.5f)
        )
        val spk2Tones = mapOf(
            1L to mkTone(w1, "55", listOf(0.90f, 0.91f, 0.90f, 0.89f, 0.89f)),
            2L to mkTone(w2, "35", listOf(0.42f, 0.54f, 0.66f, 0.80f, 0.92f)),
            3L to mkTone(w3, "55", listOf(0.89f, 0.88f, 0.88f, 0.87f, 0.87f)),
            4L to mkTone(w4, "51", listOf(0.94f, 0.72f, 0.48f, 0.25f, 0.08f)) // Tone split vs Speaker 1's [214]!
        )

        val rep1 = com.example.audio.SpeakerCorpusPhonologyReport(
            speakerId = 10L,
            speakerName = spk1.name,
            analyzedCount = 7,
            medianVoicedDurationMs = 310,
            calibratedPitchRangeHz = 95f to 210f,
            hasCustomWordGroups = true,
            mergedToneClusters = emptyList(),
            pureAcousticToneClusters = emptyList(),
            wordIdToCanonicalMember = spk1Tones,
            vowelTokens = spk1Tokens,
            vowelEllipses = emptyList(),
            lobanovValid = true
        )
        val rep2 = com.example.audio.SpeakerCorpusPhonologyReport(
            speakerId = 20L,
            speakerName = spk2.name,
            analyzedCount = 4,
            medianVoicedDurationMs = 300,
            calibratedPitchRangeHz = 165f to 320f,
            hasCustomWordGroups = true,
            mergedToneClusters = emptyList(),
            pureAcousticToneClusters = emptyList(),
            wordIdToCanonicalMember = spk2Tones,
            vowelTokens = spk2Tokens,
            vowelEllipses = emptyList(),
            lobanovValid = false
        )

        val multiReport = com.example.audio.MultiSpeakerVowelComparisonReport(
            speakerReports = listOf(rep1, rep2),
            allNormalizedTokens = spk1Tokens + spk2Tokens,
            speakerEllipses = emptyList(),
            speakersById = mapOf(10L to spk1, 20L to spk2)
        )

        val aligned = com.example.audio.MultiSpeakerComparisonEngine.buildAlignedAnalysis(
            multiReport = multiReport,
            selectedSpeakerIds = setOf(10L, 20L),
            cohortMode = com.example.audio.MultiSpeakerCohortMode.INDIVIDUAL,
            onlySharedVocabulary = false
        )

        // 1. Verify shared-anchor calibration rescued Speaker 2's [e] (w2) from false +1.35 low-vowel distortion!
        assertEquals(1, aligned.calibratedSpeakerCount)
        val spk2CalibratedW2 = aligned.calibratedTokens.first { it.speakerId == 20L && it.word.id == 2L }
        val spk1CalibratedW2 = aligned.calibratedTokens.first { it.speakerId == 10L && it.word.id == 2L }
        assertTrue(
            "Expected Speaker 2's calibrated [e] Z1 (${spk2CalibratedW2.z1Nucleus}) to stay close to Speaker 1's [e] Z1 (${spk1CalibratedW2.z1Nucleus}), not +1.35!",
            kotlin.math.abs(spk2CalibratedW2.z1Nucleus - spk1CalibratedW2.z1Nucleus) < 0.45f && spk2CalibratedW2.z1Nucleus < 0.25f
        )

        // 2. Verify Glide Split detected on w3 ("杯": DIPHTHONG vs MONOPHTHONG) and Tone Split detected on w4 ("乌": [214] vs [51])
        val compW3 = aligned.wordComparisons.first { it.wordId == 3L }
        assertTrue("Expected monophthong-diphthong split on 杯", compW3.hasGlideSplit)
        val compW4 = aligned.wordComparisons.first { it.wordId == 4L }
        assertTrue("Expected tone contour split on 乌 ([214] vs [51])", compW4.hasToneSplit)

        // 3. Verify Gender & SpeakerGroup Cohort aggregation modes work cleanly
        val byGender = com.example.audio.MultiSpeakerComparisonEngine.buildAlignedAnalysis(
            multiReport = multiReport,
            selectedSpeakerIds = setOf(10L, 20L),
            cohortMode = com.example.audio.MultiSpeakerCohortMode.BY_GENDER
        )
        assertEquals(2, byGender.cohorts.size)
        assertTrue(byGender.alignedToneCategories.isNotEmpty())

        // 4. Verify Workbook Export includes the new cross-speaker Word x Speaker matrix & Summary sheets
        val xlsxBytes = com.example.utils.MacroReportExporter.buildWorkbookBytes(
            projectName = "多发音人测试工程",
            report = rep1,
            clusters = emptyList(),
            multiSpeakerReport = multiReport,
            alignedMulti = aligned
        )
        assertTrue("Expected non-empty multi-speaker xlsx bytes", xlsxBytes.size > 1500)
        val zipEntries = ArrayList<String>()
        var workbookXml = ""
        java.util.zip.ZipInputStream(xlsxBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                if (entry.name == "xl/workbook.xml") {
                    workbookXml = zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        assertTrue("Expected 同词跨人声学对照表 sheet in workbook.xml, got: $workbookXml", workbookXml.contains("同词跨人声学对照表"))
        assertTrue("Expected 多发音人调类与元音汇总 sheet in workbook.xml, got: $workbookXml", workbookXml.contains("多发音人调类与元音汇总"))
    }

    @Test
    fun risingMedialDiphthongAndManualOverride_recognizesShortMedialGlidesAndSupportsManualOverride() {
        val sampleRate = 44100

        // Synthesize realistic voiced vowel waveform via 3-formant cascade IIR resonators driven by periodic glottal pulses
        fun synthesizeVowelRime(
            totalMs: Int,
            unvoicedLeadMs: Int = 25,
            f0Hz: Double = 145.0,
            formantAtProgress: (Double) -> Triple<Double, Double, Double>
        ): FloatArray {
            val nSamples = (sampleRate * totalMs) / 1000
            val leadSamples = (sampleRate * unvoicedLeadMs) / 1000
            val out = FloatArray(nSamples)
            var phase = 0.0
            val rng = java.util.Random(42L)

            // 3 cascade 2nd-order digital resonators: y[n] = a0*x[n] + b1*y[n-1] + b2*y[n-2]
            val y1 = DoubleArray(3)
            val y2 = DoubleArray(3)
            for (n in 0 until nSamples) {
                if (n < leadSamples) {
                    // Mild consonant release noise before vowel onset
                    out[n] = ((rng.nextDouble() * 2.0 - 1.0) * 0.035).toFloat()
                    continue
                }
                val voicedIdx = n - leadSamples
                val voicedTotal = (nSamples - leadSamples).coerceAtLeast(1)
                val prog = voicedIdx.toDouble() / voicedTotal.toDouble()

                phase += f0Hz / sampleRate
                var glottal = 0.0
                if (phase >= 1.0) {
                    phase -= 1.0
                    glottal = 1.0
                }
                // Clean periodic glottal impulse excitation (uniform harmonic comb across 0..5 kHz)
                val exc = glottal

                val (f1, f2, f3) = formantAtProgress(prog)
                val freqs = doubleArrayOf(f1, f2, f3)
                val bws = doubleArrayOf(75.0, 110.0, 150.0)

                var sig = exc
                for (k in 0..2) {
                    val r = exp(-Math.PI * bws[k] / sampleRate)
                    val b1 = 2.0 * r * cos(2.0 * Math.PI * freqs[k] / sampleRate)
                    val b2 = -(r * r)
                    val a0 = 1.0 - b1 - b2
                    val yn = a0 * sig + b1 * y1[k] + b2 * y2[k]
                    y2[k] = y1[k]
                    y1[k] = yn
                    sig = yn
                }

                // Smooth raised-cosine onset/offset envelope
                val env = when {
                    prog < 0.06 -> 0.5 - 0.5 * cos(Math.PI * (prog / 0.06))
                    prog > 0.90 -> 0.5 + 0.5 * cos(Math.PI * ((prog - 0.90) / 0.10))
                    else -> 1.0
                }
                out[n] = (sig * env).toFloat()
            }
            var peak = 1e-6f
            for (v in out) {
                val a = kotlin.math.abs(v)
                if (a > peak) peak = a
            }
            val scale = 0.65f / peak
            for (i in out.indices) out[i] *= scale
            return out
        }

        // 1. Rising diphthong "爹 [tiɛ]" (ie韵母组): brief [i] medial in first ~32% gliding into long steady [ɛ] nucleus
        val diePcm = synthesizeVowelRime(totalMs = 340, unvoicedLeadMs = 28, f0Hz = 150.0) { p ->
            val t = ((p - 0.08) / 0.26).coerceIn(0.0, 1.0)
            val smooth = t * t * (3.0 - 2.0 * t)
            val f1 = 305.0 + (535.0 - 305.0) * smooth
            val f2 = 2290.0 + (1860.0 - 2290.0) * smooth
            val f3 = 2960.0 + (2680.0 - 2960.0) * smooth
            Triple(f1, f2, f3)
        }
        val dieTraj = com.example.audio.FormantTrajectoryAndSpectrogramExtractor.extractSyllableFormantTrajectory(
            pcm = diePcm,
            segStart = 0,
            segEnd = diePcm.size,
            sampleRate = sampleRate,
            syllableIndex = 0,
            syllableLabel = "爹",
            syllableMeanF0 = 150f,
            isHighRegister = false,
            isMidRegister = false
        )
        assertEquals(
            "Expected rising diphthong 爹 [tiɛ] (ie韵母组) with short [i] medial + steady [ɛ] nucleus to be DIPHTHONG, got ${dieTraj.vowelType} (${dieTraj.trajectorySummary})",
            com.example.audio.VowelGlideType.DIPHTHONG,
            dieTraj.vowelType
        )
        assertTrue("Expected onset F1 < target F1 for [i -> ɛ], got ${dieTraj.onsetF1} -> ${dieTraj.targetF1}", dieTraj.onsetF1 < dieTraj.targetF1)
        assertTrue("Expected onset F2 > target F2 for [i -> ɛ], got ${dieTraj.onsetF2} -> ${dieTraj.targetF2}", dieTraj.onsetF2 > dieTraj.targetF2)

        // 2. Steady monophthong "衣 [i]" must still remain MONOPHTHONG
        val yiPcm = synthesizeVowelRime(totalMs = 320, unvoicedLeadMs = 20, f0Hz = 155.0) { _ ->
            Triple(300.0, 2280.0, 2960.0)
        }
        val yiTraj = com.example.audio.FormantTrajectoryAndSpectrogramExtractor.extractSyllableFormantTrajectory(
            pcm = yiPcm,
            segStart = 0,
            segEnd = yiPcm.size,
            sampleRate = sampleRate,
            syllableIndex = 0,
            syllableLabel = "衣",
            syllableMeanF0 = 155f,
            isHighRegister = false,
            isMidRegister = false
        )
        assertEquals(
            "Expected steady monophthong 衣 [i] to stay MONOPHTHONG, got ${yiTraj.vowelType}",
            com.example.audio.VowelGlideType.MONOPHTHONG,
            yiTraj.vowelType
        )

        // 3. Steady monophthong "鹅 [ɤ]" must also stay MONOPHTHONG
        val ePcm = synthesizeVowelRime(totalMs = 320, unvoicedLeadMs = 20, f0Hz = 140.0) { _ ->
            Triple(470.0, 1280.0, 2550.0)
        }
        val eTraj = com.example.audio.FormantTrajectoryAndSpectrogramExtractor.extractSyllableFormantTrajectory(
            pcm = ePcm,
            segStart = 0,
            segEnd = ePcm.size,
            sampleRate = sampleRate,
            syllableIndex = 0,
            syllableLabel = "鹅",
            syllableMeanF0 = 140f,
            isHighRegister = false,
            isMidRegister = false
        )
        assertEquals(
            "Expected steady monophthong 鹅 [ɤ] to stay MONOPHTHONG, got ${eTraj.vowelType}",
            com.example.audio.VowelGlideType.MONOPHTHONG,
            eTraj.vowelType
        )

        // 4. Manual override re-projection preserves actual measured anchors
        val forcedMono = com.example.audio.VowelGlideOverrideStore.applyOverrideToTrajectory(
            dieTraj,
            com.example.audio.VowelGlideType.MONOPHTHONG
        )
        assertEquals(com.example.audio.VowelGlideType.MONOPHTHONG, forcedMono.vowelType)
        assertEquals(forcedMono.nucleusF1, forcedMono.targetF1)
        assertTrue(forcedMono.trajectorySummary.contains("手动指定"))

        val forcedDiph = com.example.audio.VowelGlideOverrideStore.applyOverrideToTrajectory(
            yiTraj,
            com.example.audio.VowelGlideType.DIPHTHONG
        )
        assertEquals(com.example.audio.VowelGlideType.DIPHTHONG, forcedDiph.vowelType)
        assertTrue(forcedDiph.trajectorySummary.contains("手动指定"))
        assertEquals("ie韵母组", com.example.audio.VowelGlideOverrideStore.canonicalGroupKey("wg_ie韵母组"))
    }
}



