package com.example

import com.example.audio.AcousticAnalyzer
import com.example.audio.FormantTrajectoryAndSpectrogramExtractor
import com.example.audio.PhoneticScaleUtils
import com.example.audio.VowelGlideType
import com.example.data.ParsedWord
import com.example.data.Word
import com.example.utils.PraatCollectionExporter
import com.example.utils.SpreadsheetWordImporter
import com.example.utils.TextGridFormatConfig
import com.example.utils.TextGridPreset
import com.example.utils.ZipUtility
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun praatTextGrid_hasStrictContiguousIntervals_withAnalysis() {
        val word = Word(
            id = 1L,
            projectId = 1L,
            text = "中国",
            remark = "[tʂʊŋ55 kuo35] (central country)",
            groupName = "双音节词",
            originalIndex = 0,
            isRecorded = true,
            recordingFile = "word_1_speaker_1.wav",
            durationMs = 842L
        )
        val analysis = AcousticAnalyzer.AnalysisResult(
            isValidVoiced = true,
            intensityDbfs = -14.2f,
            peakDbfs = -3.1f,
            snrDb = 28.5f,
            jitterPercent = 0.42f,
            shimmerPercent = 2.15f,
            pitchMean = 185.0f,
            pitchMin = 152.0f,
            pitchMax = 215.0f,
            pitchRange = "152 - 215 Hz",
            isPitchCalibrated = true,
            chaoToneNumbers = "55",
            toneCode = "阴平 (55)",
            toneDesc = "高平调",
            toneTrajectory = "55 -> 55",
            contourPoints = listOf(0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.4f, 0.5f, 0.6f, 0.7f, 0.85f),
            rawVoicedPitches = listOf(180f, 185f, 190f),
            speakerF1 = listOf(420, 510),
            speakerF2 = listOf(1350, 980),
            segmentCount = 2,
            segments = listOf(
                AcousticAnalyzer.SegmentInterval(startSec = 0.0823, endSec = 0.4126, label = "中"),
                AcousticAnalyzer.SegmentInterval(startSec = 0.4126, endSec = 0.7654, label = "国")
            ),
            creakyBreaks = 0,
            voicedDurationMs = 620,
            totalDurationMs = 842
        )

        val analysisWithSylTones = analysis.copy(
            syllableToneNumbers = listOf("55", "35")
        )

        // 1. Default UNIVERSAL_STANDARD: 3 clean tiers (words, syllables, tones), 6-decimal precision
        val tgDefault = ZipUtility.buildPraatTextGrid(word, analysisWithSylTones, exactDurationSec = 0.842404)
        assertTrue(tgDefault.startsWith("File type = \"ooTextFile\"\nObject class = \"TextGrid\""))
        assertTrue(tgDefault.contains("size = 3"))
        assertTrue(tgDefault.contains("name = \"words\""))
        assertTrue(tgDefault.contains("name = \"syllables\""))
        assertTrue(tgDefault.contains("name = \"tones\""))
        assertFalse("Universal standard must not include non-generic groups tier by default", tgDefault.contains("name = \"groups\""))
        assertTrue(tgDefault.contains("text = \"中国\""))
        assertTrue(tgDefault.contains("text = \"中\""))
        assertTrue(tgDefault.contains("text = \"国\""))
        assertTrue(tgDefault.contains("text = \"55\""))
        assertTrue(tgDefault.contains("text = \"35\""))
        assertFalse("Clean default TextGrid should not embed [F1:..] tags", tgDefault.contains("F1:"))
        verifyPraatTierContinuity(tgDefault, expectedTierCount = 3, expectedXmin = "0.000000", expectedXmax = "0.842404")

        // 2. PhonTracer 4-tier compatibility preset
        val phonTracerCfg = TextGridFormatConfig.fromPreset(TextGridPreset.PHONTRACER_COMPAT)
        val tg = ZipUtility.buildPraatTextGrid(
            word = word,
            analysis = analysisWithSylTones,
            exactDurationSec = 0.8424,
            config = phonTracerCfg
        )
        assertTrue(tg.contains("size = 4"))
        assertTrue(tg.contains("name = \"groups\""))
        assertTrue(tg.contains("name = \"words\""))
        assertTrue(tg.contains("name = \"chars\""))
        assertTrue(tg.contains("name = \"segments\""))
        assertTrue(tg.contains("text = \"双音节词\""))

        val tgWithTags = ZipUtility.buildPraatTextGrid(
            word = word,
            analysis = analysisWithSylTones,
            exactDurationSec = 0.8424,
            includeAcousticTags = true,
            config = phonTracerCfg
        )
        assertTrue("Optional acoustic tags TextGrid should include F1:", tgWithTags.contains("F1:"))

        verifyPraatTierContinuity(tg, expectedTierCount = 4, expectedXmin = "0.0000", expectedXmax = "0.8424")
        verifyPraatTierContinuity(tgWithTags, expectedTierCount = 4, expectedXmin = "0.0000", expectedXmax = "0.8424")
    }

    @Test
    fun spreadsheetImporter_parsesGbkCsvHeaders_andBlocksLegacyBinaryFiles() {
        val csvContent = "序号,调类,例字,国际音标\n1,阴平,东,tʊŋ55\n2,阳入,局,dʑyʔ2\n"
        val gbkBytes = csvContent.toByteArray(Charset.forName("GB18030"))
        val res = SpreadsheetWordImporter.importFromBytes(gbkBytes, "调查字表.csv") { emptyList<ParsedWord>() }
        assertNull(res.errorMessage)
        assertEquals(2, res.words.size)
        assertEquals("东", res.words[0].text)
        assertEquals("阴平", res.words[0].groupName)
        assertTrue(res.words[0].remark.contains("tʊŋ55"))
        assertEquals("局", res.words[1].text)
        assertEquals("阳入", res.words[1].groupName)

        // Legacy binary .xls header (D0 CF 11 E0) must be blocked cleanly instead of producing garbled rows
        val fakeXls = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0x00, 0x01)
        val xlsRes = SpreadsheetWordImporter.importFromBytes(fakeXls, "legacy.xls") { emptyList() }
        assertTrue(xlsRes.words.isEmpty())
        assertNotNull(xlsRes.errorMessage)

        // Preserves valid IPA/Unicode symbols while normalizing tone bars and stripping path-illegal chars
        assertEquals("mā", ZipUtility.transliterateNoteForFileName("mā"))
        assertEquals("pʰa55", ZipUtility.transliterateNoteForFileName("pʰa˥˥"))
        assertEquals("tɕʰiaʔ53_老派", ZipUtility.transliterateNoteForFileName("tɕʰiaʔ53 / 老派 | 备注"))
    }

    @Test
    fun praatTextGrid_handlesFallbackAndMicroSilenceWithoutGaps() {
        val word = Word(
            id = 2L,
            projectId = 1L,
            text = "衣",
            remark = "i | clothes",
            groupName = "",
            originalIndex = 1,
            isRecorded = true,
            durationMs = 510L
        )
        // Null analysis fallback on both default universal preset and PhonTracer preset
        val tgDefaultFallback = ZipUtility.buildPraatTextGrid(word, null, exactDurationSec = 0.512345)
        assertTrue(tgDefaultFallback.contains("name = \"words\""))
        assertTrue(tgDefaultFallback.contains("name = \"syllables\""))
        assertTrue(tgDefaultFallback.contains("text = \"衣\""))
        verifyPraatTierContinuity(tgDefaultFallback, expectedTierCount = 3, expectedXmin = "0.000000", expectedXmax = "0.512345")

        val tgFallback = ZipUtility.buildPraatTextGrid(
            word = word,
            analysis = null,
            exactDurationSec = 0.5123,
            config = TextGridFormatConfig.fromPreset(TextGridPreset.PHONTRACER_COMPAT)
        )
        assertTrue(tgFallback.contains("name = \"groups\""))
        assertTrue(tgFallback.contains("text = \"默认组\""))
        assertTrue(tgFallback.contains("name = \"words\""))
        assertTrue(tgFallback.contains("name = \"chars\""))
        assertTrue(tgFallback.contains("text = \"衣\""))
        verifyPraatTierContinuity(tgFallback, expectedTierCount = 4, expectedXmin = "0.0000", expectedXmax = "0.5123")
    }

    @Test
    fun wavHeaderSanitizer_andPathNormalizer_complyWithPhonTracerSpec() {
        assertEquals("audio/SPK01_001_衣_i.wav", ZipUtility.normalizeZipEntryPath("/audio\\SPK01_001_衣_i.wav"))
        assertFalse(ZipUtility.normalizeZipEntryPath("../audio/bad<>:\"|?*.wav").contains(".."))

        val meta = ZipUtility.parseWordRemarkMetadata("[i] (clothes) #单元音 #前元音", "高元音")
        assertEquals("i", meta.ipa)
        assertEquals("clothes", meta.gloss)
        assertTrue(meta.tags.contains("单元音"))
        assertTrue(meta.tags.contains("前元音"))

        // Create a temporary WAV with deliberately corrupted nAvgBytesPerSec and verify sanitizer fixes it
        val tempWav = java.io.File.createTempFile("corrupt_header", ".wav")
        try {
            val pcmPayload = ByteArray(4410) // 0.05s at 44.1kHz 16-bit mono
            java.io.FileOutputStream(tempWav).use { fos ->
                val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(36 + pcmPayload.size)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16)
                header.putShort(1) // PCM
                header.putShort(1) // 1 channel
                header.putInt(44100) // sampleRate = 44100
                header.putInt(12345) // Intentionally wrong nAvgBytesPerSec! Should be 88200
                header.putShort(4) // Intentionally wrong blockAlign! Should be 2
                header.putShort(16) // bitsPerSample = 16
                header.put("data".toByteArray())
                header.putInt(pcmPayload.size)
                fos.write(header.array())
                fos.write(pcmPayload)
            }

            val baos = java.io.ByteArrayOutputStream()
            ZipUtility.writeSanitizedWavToOutputStream(tempWav, baos)
            val fixedBytes = baos.toByteArray()
            val bb = java.nio.ByteBuffer.wrap(fixedBytes, 0, 44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val sampleRate = bb.getInt(24)
            val channels = bb.getShort(22).toInt()
            val bitsPerSample = bb.getShort(34).toInt()
            val nAvgBytesPerSec = bb.getInt(28)
            val blockAlign = bb.getShort(32).toInt()

            assertEquals(44100, sampleRate)
            assertEquals(1, channels)
            assertEquals(16, bitsPerSample)
            assertEquals(sampleRate * channels * (bitsPerSample / 8), nAvgBytesPerSec)
            assertEquals(channels * (bitsPerSample / 8), blockAlign)
        } finally {
            tempWav.delete()
        }
    }

    private fun verifyPraatTierContinuity(
        tgContent: String,
        expectedTierCount: Int = 4,
        expectedXmin: String = "0.0000",
        expectedXmax: String
    ) {
        val tierBlocks = tgContent.split(Regex("""item \[\d+\]:""")).drop(1)
        assertEquals(expectedTierCount, tierBlocks.size)

        for (tierBlock in tierBlocks) {
            val intervalRegex = Regex("""intervals \[\d+\]:\s+xmin = ([0-9.]+)\s+xmax = ([0-9.]+)""")
            val matches = intervalRegex.findAll(tierBlock).toList()
            assertTrue("Each tier must have at least 1 interval", matches.isNotEmpty())
            assertEquals("First interval must start at $expectedXmin", expectedXmin, matches.first().groupValues[1])
            assertEquals("Last interval must end at $expectedXmax", expectedXmax, matches.last().groupValues[2])

            for (i in matches.indices) {
                val xmin = matches[i].groupValues[1].toDouble()
                val xmax = matches[i].groupValues[2].toDouble()
                assertTrue("Interval xmax ($xmax) must be strictly greater than xmin ($xmin)", xmax > xmin)
                if (i > 0) {
                    assertEquals(
                        "Adjacent intervals must share identical boundary string",
                        matches[i - 1].groupValues[2],
                        matches[i].groupValues[1]
                    )
                }
            }
        }
    }

    @Test
    fun acousticAnalyzer_accuratelyExtractsVowelFormants_tones_andRejectsFalseCreak() {
        data class VowelSpec(
            val name: String,
            val targetF1: Int,
            val targetF2: Int,
            val targetF3: Int,
            val f0Start: Double = 210.0,
            val f0Mid: Double = 210.0,
            val f0End: Double = 210.0
        )

        val vowels = listOf(
            VowelSpec("衣 [i]", targetF1 = 290, targetF2 = 2350, targetF3 = 3000),
            VowelSpec("迂 [y]", targetF1 = 300, targetF2 = 1850, targetF3 = 2500),
            VowelSpec("波 [o]", targetF1 = 480, targetF2 = 880, targetF3 = 2550),
            VowelSpec("鹅 [ɤ]", targetF1 = 510, targetF2 = 1260, targetF3 = 2530),
            VowelSpec("靴 [yɛ]", targetF1 = 550, targetF2 = 1750, targetF3 = 2550),
            VowelSpec("巴 [a]", targetF1 = 790, targetF2 = 1320, targetF3 = 2520),
            VowelSpec("妈 [a]", targetF1 = 780, targetF2 = 1280, targetF3 = 2500, f0Start = 155.0, f0Mid = 190.0, f0End = 230.0),
            VowelSpec("刀 [au]", targetF1 = 680, targetF2 = 1120, targetF3 = 2520, f0Start = 185.0, f0Mid = 135.0, f0End = 195.0)
        )

        val failures = mutableListOf<String>()
        val tempDir = kotlin.io.path.createTempDirectory("phonacq_dsp_test").toFile()
        try {
            for (v in vowels) {
                val wavFile = java.io.File(tempDir, "test_${v.name.take(1)}.wav")
                writeKlattVowelWav(
                    file = wavFile,
                    f0Start = v.f0Start,
                    f0Mid = v.f0Mid,
                    f0End = v.f0End,
                    f1 = v.targetF1.toDouble(),
                    f2 = v.targetF2.toDouble(),
                    f3 = v.targetF3.toDouble()
                )
                val res = AcousticAnalyzer.analyzeByGender(wavFile, "女", segmentCount = 1)
                assertNotNull("Analysis should not be null for ${v.name}", res)
                res!!
                assertTrue("Voiced detection failed for ${v.name}", res.isValidVoiced)

                val f1 = res.speakerF1.first()
                val f2 = res.speakerF2.first()
                println("Vowel ${v.name}: target=(${v.targetF1}/${v.targetF2}) -> extracted=($f1/$f2), f0=${res.pitchMean}, tone=${res.toneCode}, creak=${res.creakyBreaks}, jitter=${res.jitterPercent}%")

                if (kotlin.math.abs(f1 - v.targetF1) > 125) {
                    failures.add("F1 for ${v.name} was $f1 Hz, expected ~${v.targetF1} Hz")
                }
                if (kotlin.math.abs(f2 - v.targetF2) > 220) {
                    failures.add("F2 for ${v.name} was $f2 Hz, expected ~${v.targetF2} Hz")
                }
                assertEquals("Normal modal vowel ${v.name} must have 0 creakyBreaks", 0, res.creakyBreaks)
            }
            if (failures.isNotEmpty()) {
                fail(failures.joinToString("\n"))
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Synthesizes a realistic Klatt-style cascade all-pole vocal tract vowel with Rosenberg-like
     * glottal Rosenberg/LF excitation (including strong fundamental H1 at F0 and F1, F2, F3 resonances)
     * plus lead-in/lead-out room silence.
     */
    private fun writeKlattVowelWav(
        file: java.io.File,
        f0Start: Double,
        f0Mid: Double,
        f0End: Double,
        f1: Double,
        f2: Double,
        f3: Double,
        durationMs: Int = 680
    ) {
        val sampleRate = 44100
        val totalSamples = (sampleRate * durationMs) / 1000
        val silenceSamples = (totalSamples * 0.16).toInt()
        val rawSignal = DoubleArray(totalSamples)

        // Generate glottal impulse train with realistic glottal spectral tilt (strong H1 at F0!)
        var phase = 0.0
        var prevGlottal = 0.0
        for (n in silenceSamples until (totalSamples - silenceSamples)) {
            val tNorm = (n - silenceSamples).toDouble() / (totalSamples - 2 * silenceSamples).coerceAtLeast(1)
            val f0 = if (tNorm < 0.5) {
                f0Start + (f0Mid - f0Start) * (tNorm * 2.0)
            } else {
                f0Mid + (f0End - f0Mid) * ((tNorm - 0.5) * 2.0)
            }
            phase += f0 / sampleRate
            if (phase >= 1.0) phase -= 1.0

            // Rosenberg glottal volume velocity pulse (-12 dB/oct source spectrum) + natural voice-bar H1
            val pulse = if (phase < 0.40) {
                0.5 * (1.0 - kotlin.math.cos(Math.PI * phase / 0.40))
            } else if (phase < 0.56) {
                kotlin.math.cos(Math.PI * (phase - 0.40) / (2.0 * 0.16))
            } else {
                0.0
            }
            val glottal = pulse + 0.08 * kotlin.math.sin(2.0 * Math.PI * phase)
            prevGlottal = pulse
            val env = kotlin.math.sqrt(kotlin.math.sin(Math.PI * tNorm))
            rawSignal[n] = glottal * env
        }

        // Cascade 3 two-pole resonators for (F1, B1=75Hz), (F2, B2=110Hz), (F3, B3=150Hz)
        fun applyResonator(input: DoubleArray, freq: Double, bw: Double): DoubleArray {
            val out = DoubleArray(input.size)
            val r = kotlin.math.exp(-Math.PI * bw / sampleRate)
            val theta = 2.0 * Math.PI * freq / sampleRate
            val a1 = 2.0 * r * kotlin.math.cos(theta)
            val a2 = -r * r
            val b0 = 1.0 - a1 - a2
            var y1 = 0.0
            var y2 = 0.0
            for (i in input.indices) {
                val y = b0 * input[i] + a1 * y1 + a2 * y2
                out[i] = y
                y2 = y1
                y1 = y
            }
            return out
        }

        val r1 = applyResonator(rawSignal, f1, 75.0)
        val r2 = applyResonator(r1, f2, 110.0)
        val r3 = applyResonator(r2, f3, 150.0)

        // Lip radiation (+6 dB/oct first difference at the lips)
        val radiated = DoubleArray(totalSamples)
        var maxAbs = 1e-6
        for (i in 1 until totalSamples) {
            radiated[i] = r3[i] - 0.95 * r3[i - 1]
            val a = kotlin.math.abs(radiated[i])
            if (a > maxAbs) maxAbs = a
        }

        val pcmBytes = ByteArray(totalSamples * 2)
        val buf = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            val norm = if (i in silenceSamples until (totalSamples - silenceSamples)) {
                (radiated[i] / maxAbs) * 0.65
            } else {
                kotlin.math.sin(i * 1.3) * 0.0015
            }
            val s = (norm * 32767.0).toInt().coerceIn(-32767, 32767).toShort()
            buf.putShort(s)
        }

        java.io.FileOutputStream(file).use { fos ->
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + pcmBytes.size)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(pcmBytes.size)
            fos.write(header.array())
            fos.write(pcmBytes)
        }
    }

    @Test
    fun robustToneClassifier_preservesLevelTonesAgainstDeclination_andDetectsCheckedShortTones() {
        // High level [55] with natural physiological declination (-0.11 normalized units across syllable)
        val highLevelWithDeclination = listOf(0.88f, 0.85f, 0.83f, 0.80f, 0.77f)
        val highRes = AcousticAnalyzer.classifyContourRobust(
            contourPoints = highLevelWithDeclination,
            isValidVoiced = true,
            isCalibrated = true,
            isCheckedTone = false
        )
        assertEquals("55", highRes.chaoDigits)
        assertTrue("Expected 高平调 but got ${highRes.toneCode}", highRes.toneCode.contains("平调"))

        // Mid level [33] with micro-jitter and gentle declination
        val midLevelWithJitter = listOf(0.55f, 0.52f, 0.53f, 0.49f, 0.46f)
        val midRes = AcousticAnalyzer.classifyContourRobust(
            contourPoints = midLevelWithJitter,
            isValidVoiced = true,
            isCalibrated = true,
            isCheckedTone = false
        )
        assertEquals("33", midRes.chaoDigits)
        assertTrue("Expected 中平调 but got ${midRes.toneCode}", midRes.toneCode.contains("平调"))

        // Genuine rising tone [35]
        val risingContour = listOf(0.45f, 0.56f, 0.68f, 0.81f, 0.93f)
        val risingRes = AcousticAnalyzer.classifyContourRobust(
            contourPoints = risingContour,
            isValidVoiced = true,
            isCalibrated = true,
            isCheckedTone = false
        )
        assertTrue("Expected rising tone, got ${risingRes.chaoDigits}", risingRes.toneCode.contains("升调"))

        // Genuine falling tone [51]
        val fallingContour = listOf(0.94f, 0.74f, 0.51f, 0.28f, 0.08f)
        val fallingRes = AcousticAnalyzer.classifyContourRobust(
            contourPoints = fallingContour,
            isValidVoiced = true,
            isCalibrated = true,
            isCheckedTone = false
        )
        assertEquals("51", fallingRes.chaoDigits)
        assertTrue(fallingRes.toneCode.contains("降调"))

        // Genuine dipping tone [214]
        val dippingContour = listOf(0.36f, 0.14f, 0.08f, 0.35f, 0.68f)
        val dippingRes = AcousticAnalyzer.classifyContourRobust(
            contourPoints = dippingContour,
            isValidVoiced = true,
            isCalibrated = true,
            isCheckedTone = false
        )
        assertEquals(3, dippingRes.chaoDigits.length)
        assertTrue("Expected 曲折调, got ${dippingRes.toneCode}", dippingRes.toneCode.contains("曲折调"))

        // Checked / Entering short tone [5]
        val checkedHigh = AcousticAnalyzer.classifyContourRobust(
            contourPoints = listOf(0.86f, 0.84f, 0.83f, 0.81f, 0.80f),
            isValidVoiced = true,
            isCalibrated = true,
            isCheckedTone = true
        )
        assertEquals("5", checkedHigh.chaoDigits)
        assertTrue(checkedHigh.toneCode.contains("促声"))
    }

    @Test
    fun dynamicFormantExtractor_distinguishesMonophthongVsDiphthong_andExtractsSpectrogram() {
        // Bark scale round-trip and articulatory description sanity check
        for (hz in listOf(280f, 550f, 820f, 1350f, 2350f)) {
            val b = PhoneticScaleUtils.hzToBark(hz)
            val back = PhoneticScaleUtils.barkToHz(b)
            assertEquals(hz, back, 2.5f)
        }
        assertEquals("前高", PhoneticScaleUtils.describeArticulatoryRegion(290, 2320))
        assertEquals("后高", PhoneticScaleUtils.describeArticulatoryRegion(300, 800))
        assertEquals("央低", PhoneticScaleUtils.describeArticulatoryRegion(800, 1320))

        val tempDir = kotlin.io.path.createTempDirectory("phonacq_glide_test").toFile()
        try {
            val monoWav = java.io.File(tempDir, "mono_a.wav")
            writeTimeVaryingKlattWav(
                file = monoWav,
                f0 = 195.0,
                f1Start = 780.0, f1End = 775.0,
                f2Start = 1310.0, f2End = 1300.0,
                f3 = 2520.0,
                voicedDurationMs = 460
            )
            val monoRes = AcousticAnalyzer.analyzeByGender(monoWav, "女", segmentCount = 1, speakerMedianVoicedMs = 360)
            assertNotNull(monoRes)
            monoRes!!
            assertEquals(1, monoRes.formantTrajectories.size)
            val monoTraj = monoRes.formantTrajectories.first()
            assertEquals(VowelGlideType.MONOPHTHONG, monoTraj.vowelType)
            assertFalse(monoRes.isCheckedTone)

            val diphWav = java.io.File(tempDir, "diph_ai.wav")
            writeTimeVaryingKlattWav(
                file = diphWav,
                f0 = 195.0,
                f1Start = 800.0, f1End = 300.0,
                f2Start = 1280.0, f2End = 2320.0,
                f3 = 2850.0,
                voicedDurationMs = 500
            )
            val diphRes = AcousticAnalyzer.analyzeByGender(diphWav, "女", segmentCount = 1, speakerMedianVoicedMs = 360)
            assertNotNull(diphRes)
            diphRes!!
            val diphTraj = diphRes.formantTrajectories.first()
            assertNotEquals("Gliding [ai] must not be classified as MONOPHTHONG", VowelGlideType.MONOPHTHONG, diphTraj.vowelType)
            assertTrue("Expected F1 onset (${diphTraj.onsetF1}) > target (${diphTraj.targetF1})", diphTraj.onsetF1 > diphTraj.targetF1 + 180)
            assertTrue("Expected F2 target (${diphTraj.targetF2}) > onset (${diphTraj.onsetF2})", diphTraj.targetF2 > diphTraj.onsetF2 + 400)
            assertTrue("Expected Bark glide >= 1.25z, got ${diphTraj.barkGlideSpan}", diphTraj.barkGlideSpan >= 1.25f)

            // Wideband spectrogram extraction test
            val spec = FormantTrajectoryAndSpectrogramExtractor.extractWidebandSpectrogram(diphWav, "女", null)
            assertNotNull(spec)
            spec!!
            assertTrue(spec.timeCols >= 40)
            assertTrue(spec.freqBins >= 32)
            assertEquals(spec.timeCols * spec.freqBins, spec.normalizedDbGrid.size)
            assertTrue(spec.formantTrackPoints.size >= 5)

            // Short abruptly-checked entering tone [ak̚] (~95ms voiced rime vs 360ms speaker median)
            val checkedWav = java.io.File(tempDir, "checked_ak.wav")
            writeTimeVaryingKlattWav(
                file = checkedWav,
                f0 = 215.0,
                f1Start = 740.0, f1End = 710.0,
                f2Start = 1320.0, f2End = 1260.0,
                f3 = 2500.0,
                voicedDurationMs = 95,
                abruptStop = true
            )
            val checkedRes = AcousticAnalyzer.analyzeByGender(checkedWav, "女", segmentCount = 1, speakerMedianVoicedMs = 360)
            assertNotNull(checkedRes)
            checkedRes!!
            assertTrue("Short stopped rime (${checkedRes.voicedDurationMs}ms) should be detected as checked tone", checkedRes.isCheckedTone)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writeTimeVaryingKlattWav(
        file: java.io.File,
        f0: Double,
        f1Start: Double,
        f1End: Double,
        f2Start: Double,
        f2End: Double,
        f3: Double,
        voicedDurationMs: Int,
        abruptStop: Boolean = false
    ) {
        val sampleRate = 44100
        val padSamples = (sampleRate * 0.12).toInt()
        val voicedSamples = (sampleRate * voicedDurationMs) / 1000
        val totalSamples = voicedSamples + padSamples * 2
        val glottalSig = DoubleArray(totalSamples)

        var phase = 0.0
        for (i in 0 until voicedSamples) {
            val idx = padSamples + i
            val tNorm = i.toDouble() / voicedSamples.coerceAtLeast(1)
            phase += f0 / sampleRate
            if (phase >= 1.0) phase -= 1.0
            val pulse = if (phase < 0.40) {
                0.5 * (1.0 - kotlin.math.cos(Math.PI * phase / 0.40))
            } else if (phase < 0.56) {
                kotlin.math.cos(Math.PI * (phase - 0.40) / (2.0 * 0.16))
            } else {
                0.0
            }
            val env = if (abruptStop) {
                if (tNorm < 0.12) (tNorm / 0.12) else if (tNorm > 0.94) ((1.0 - tNorm) / 0.06) else 1.0
            } else {
                kotlin.math.sqrt(kotlin.math.sin(Math.PI * tNorm))
            }
            glottalSig[idx] = (pulse + 0.06 * kotlin.math.sin(2.0 * Math.PI * phase)) * env
        }

        fun applyVaryingResonator(input: DoubleArray, fStart: Double, fEnd: Double, bw: Double): DoubleArray {
            val out = DoubleArray(input.size)
            val r = kotlin.math.exp(-Math.PI * bw / sampleRate)
            var y1 = 0.0
            var y2 = 0.0
            for (i in input.indices) {
                val prog = ((i - padSamples).toDouble() / voicedSamples.coerceAtLeast(1)).coerceIn(0.0, 1.0)
                // Smooth S-curve transition across the vowel core
                val sProg = prog * prog * (3.0 - 2.0 * prog)
                val freq = fStart + (fEnd - fStart) * sProg
                val theta = 2.0 * Math.PI * freq / sampleRate
                val a1 = 2.0 * r * kotlin.math.cos(theta)
                val a2 = -r * r
                val b0 = 1.0 - a1 - a2
                val y = b0 * input[i] + a1 * y1 + a2 * y2
                out[i] = y
                y2 = y1
                y1 = y
            }
            return out
        }

        val r1 = applyVaryingResonator(glottalSig, f1Start, f1End, 75.0)
        val r2 = applyVaryingResonator(r1, f2Start, f2End, 110.0)
        val r3 = applyVaryingResonator(r2, f3, f3, 150.0)

        val radiated = DoubleArray(totalSamples)
        var maxAbs = 1e-6
        for (i in 1 until totalSamples) {
            radiated[i] = r3[i] - 0.95 * r3[i - 1]
            val a = kotlin.math.abs(radiated[i])
            if (a > maxAbs) maxAbs = a
        }

        val pcmBytes = ByteArray(totalSamples * 2)
        val buf = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            val norm = if (i in padSamples until (padSamples + voicedSamples)) {
                (radiated[i] / maxAbs) * 0.68
            } else {
                kotlin.math.sin(i * 1.7) * 0.0008
            }
            buf.putShort((norm * 32767.0).toInt().coerceIn(-32767, 32767).toShort())
        }

        java.io.FileOutputStream(file).use { fos ->
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + pcmBytes.size)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(pcmBytes.size)
            fos.write(header.array())
            fos.write(pcmBytes)
        }
    }

    @Test
    fun praatBinaryPrimitives_matchOfficialByteSpecAndSurrogateCodepoints() {
        fun encodeW16(s: String): ByteArray {
            val baos = ByteArrayOutputStream()
            val w = PraatCollectionExporter.PraatBinaryWriter(baos)
            w.w16(s)
            w.flush()
            return baos.toByteArray()
        }

        // 1. "" -> 00 00
        assertArrayEquals(byteArrayOf(0x00, 0x00), encodeW16(""))

        // 2. "tone" -> 00 04 74 6F 6E 65
        assertArrayEquals(
            byteArrayOf(0x00, 0x04, 0x74, 0x6F, 0x6E, 0x65),
            encodeW16("tone")
        )

        // 3. "你好" -> FF FF 00 02 4F 60 59 7D
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(),
                0x00, 0x02,
                0x4F, 0x60, 0x59, 0x7D
            ),
            encodeW16("你好")
        )

        // 4. Emoji surrogate pair "😀" (U+1F600): MUST count as 1 UTF-32 codepoint, NOT 2 UTF-16 units!
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(),
                0x00, 0x01,
                0xD8.toByte(), 0x3D, 0xDE.toByte(), 0x00
            ),
            encodeW16("\uD83D\uDE00")
        )

        // 5. Latin-1 > 0x7F ("é" U+00E9) must also use UTF-16BE escape when writing
        assertArrayEquals(
            byteArrayOf(
                0xFF.toByte(), 0xFF.toByte(),
                0x00, 0x01,
                0x00, 0xE9.toByte()
            ),
            encodeW16("é")
        )

        // 6. Object name sanitizer must remove '.' so Praat unpacker never strips extensions
        val sanitized = PraatCollectionExporter.sanitizePraatObjectName("01.intro test(wav)")
        assertFalse(sanitized.contains("."))
        assertFalse(sanitized.contains(" "))
        assertEquals("01_intro_test_wav", sanitized)
    }

    @Test
    fun praatCollection_binaryAndText_roundTripSoundTextGridAndTableWithQuotesAndUnicode() {
        val tempWav = java.io.File.createTempFile("praat_col_test", ".wav")
        try {
            val sampleRate = 16000
            val frameCount = 1603 // Odd sample count to test exact unrounded xmax = nx * dx alignment
            val shorts = ShortArray(frameCount) { idx ->
                when (idx) {
                    0 -> 0
                    1 -> 16384 // +0.5
                    2 -> -16384 // -0.5
                    else -> ((kotlin.math.sin(idx * 0.1) * 12000.0).toInt().toShort())
                }
            }
            java.io.FileOutputStream(tempWav).use { fos ->
                val pcm = ByteArray(frameCount * 2)
                val bb = java.nio.ByteBuffer.wrap(pcm).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (s in shorts) bb.putShort(s)
                val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(36 + pcm.size)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16)
                header.putShort(1)
                header.putShort(1)
                header.putInt(sampleRate)
                header.putInt(sampleRate * 2)
                header.putShort(2)
                header.putShort(16)
                header.put("data".toByteArray())
                header.putInt(pcm.size)
                fos.write(header.array())
                fos.write(pcm)
            }

            val word = Word(
                id = 5L,
                projectId = 1L,
                text = "桥",
                remark = "dʑiɔ24 | \"bridge\" 😀",
                speakerNote = "老派 \"白读\" [dʑiɔ˨˦]",
                groupName = "阳平组",
                originalIndex = 0,
                isRecorded = true,
                recordingFile = tempWav.name,
                durationMs = 100L
            )

            val dialectCfg = TextGridFormatConfig.fromPreset(TextGridPreset.CHINESE_DIALECT)

            // A. Binary single-word Collection round-trip (Chinese Dialect 4-tier preset: words/syllables/ipa/tones)
            val binOut = ByteArrayOutputStream()
            assertTrue(
                PraatCollectionExporter.exportSingleWordPraatCollection(
                    wavFile = tempWav,
                    word = word,
                    speaker = null,
                    analysis = null,
                    binaryFormat = true,
                    textGridConfig = dialectCfg,
                    targetStream = binOut
                )
            )
            val binBytes = binOut.toByteArray()
            // Check 23-byte magic header: "ooBinaryFile" + 0x0A + "Collection"
            val expectedHeader = "ooBinaryFile".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x0A) +
                "Collection".toByteArray(Charsets.US_ASCII)
            assertArrayEquals(expectedHeader, binBytes.copyOfRange(0, 23))

            val binItems = PraatCollectionExporter.readBinaryCollection(binBytes)
            assertEquals(2, binItems.size)
            val binSound = binItems[0] as PraatCollectionExporter.PraatSoundObject
            val binTg = binItems[1] as PraatCollectionExporter.PraatTextGridObject
            assertEquals("Paired Sound and TextGrid must share identical sanitized name", binSound.objectName, binTg.objectName)
            assertFalse(binSound.objectName.contains("."))
            assertEquals(frameCount, binSound.nx)
            assertEquals(1, binSound.ny)
            assertEquals(1.0 / sampleRate, binSound.dx, 1e-15)
            assertEquals(0.5 / sampleRate, binSound.x1, 1e-15)
            assertEquals(binSound.xmax, binTg.xmax, 1e-15)
            assertEquals(0.0, binSound.channels[0][0], 1e-12)
            assertEquals(0.5, binSound.channels[0][1], 1e-12)
            assertEquals(-0.5, binSound.channels[0][2], 1e-12)
            assertEquals(4, binTg.tiers.size)
            assertEquals(listOf("words", "syllables", "ipa", "tones"), binTg.tiers.map { (it as PraatCollectionExporter.PraatIntervalTier).name })

            // B. Full Text ooTextFile Collection round-trip (must be UTF-8 without BOM & pass PraatTextScanner)
            val txtOut = ByteArrayOutputStream()
            assertTrue(
                PraatCollectionExporter.exportSingleWordPraatCollection(
                    wavFile = tempWav,
                    word = word,
                    speaker = null,
                    analysis = null,
                    binaryFormat = false,
                    textGridConfig = dialectCfg,
                    targetStream = txtOut
                )
            )
            val txtBytes = txtOut.toByteArray()
            // Must NOT start with UTF-8 BOM (EF BB BF)
            assertFalse(txtBytes.size >= 3 && txtBytes[0] == 0xEF.toByte() && txtBytes[1] == 0xBB.toByte())
            val txtString = String(txtBytes, Charsets.UTF_8)
            assertTrue(txtString.startsWith("File type = \"ooTextFile\"\nObject class = \"Collection\"\n"))
            // Internal double quotes must be doubled as ""
            assertTrue(txtString.contains("\"\"白读\"\""))

            val txtItems = PraatCollectionExporter.readTextCollection(txtString)
            assertEquals(2, txtItems.size)
            val txtSound = txtItems[0] as PraatCollectionExporter.PraatSoundObject
            val txtTg = txtItems[1] as PraatCollectionExporter.PraatTextGridObject
            assertEquals(binSound.objectName, txtSound.objectName)
            assertEquals(binSound.nx, txtSound.nx)
            assertEquals(binSound.xmax, txtTg.xmax, 1e-14)
            val ipaTier = txtTg.tiers[2] as PraatCollectionExporter.PraatIntervalTier
            assertTrue(ipaTier.intervals.any { it.text.contains("老派 \"白读\" [dʑiɔ˨˦]") })

            // C. Table binary round-trip with repeated row column count
            val tableObj = PraatCollectionExporter.PraatTableObject(
                objectName = "test.summary table",
                columnHeaders = listOf("Word", "IPA", "F0_Hz"),
                rows = listOf(
                    listOf("东", "tʊŋ˥˥", "198.45"),
                    listOf("气", "tɕʰi˥˩", "--undefined--")
                )
            )
            val tblBytesOut = ByteArrayOutputStream()
            PraatCollectionExporter.writeItemsAsBinaryCollection(listOf(tableObj), tblBytesOut)
            val parsedTbl = PraatCollectionExporter.readBinaryCollection(tblBytesOut.toByteArray()).single()
                as PraatCollectionExporter.PraatTableObject
            assertEquals("test_summary_table", parsedTbl.objectName)
            assertEquals(listOf("Word", "IPA", "F0_Hz"), parsedTbl.columnHeaders)
            assertEquals(2, parsedTbl.rows.size)
            assertEquals("tʊŋ˥˥", parsedTbl.rows[0][1])
            assertEquals("--undefined--", parsedTbl.rows[1][2])
        } finally {
            tempWav.delete()
        }
    }

    @Test
    fun hybridPolysyllabicSegmentation_handlesGlossesAlphabetic_unevenCheckedSmooth_andNeutralTones() {
        // 1. Orthographic syllable unit extractor: strips parenthetical notes, numbering, splits CJK & alphabetic vowel nuclei
        assertEquals(listOf("日", "头"), AcousticAnalyzer.extractSyllableUnits("01. 日头（老派白读）"))
        assertEquals(listOf("葡", "萄"), AcousticAnalyzer.extractSyllableUnits("葡萄 [pʰu35 tʰau2]"))
        assertEquals(listOf("花", "儿"), AcousticAnalyzer.extractSyllableUnits("花儿"))
        assertEquals(2, AcousticAnalyzer.extractSyllableUnits("apple").size)
        assertEquals(3, AcousticAnalyzer.extractSyllableUnits("banana").size)
        assertEquals(1, AcousticAnalyzer.extractSyllableUnits("cake").size)
        assertEquals(2, AcousticAnalyzer.extractSyllableUnits("ma1ma2").size)

        val tempDir = kotlin.io.path.createTempDirectory("phonacq_polysyl_test").toFile()
        try {
            // 2. Uneven disyllable: Short checked 1st syllable [ak̚] (95ms) + 85ms consonant closure + Long smooth 2nd syllable [i] (330ms)
            val unevenWav = java.io.File(tempDir, "uneven_checked_smooth.wav")
            writeTwoSyllableKlattWav(
                file = unevenWav,
                syl1Ms = 95,
                syl1F0Start = 220.0, syl1F0End = 215.0,
                syl1F1 = 760.0, syl1F2 = 1300.0,
                syl1Amp = 0.72, syl1AbruptStop = true,
                gapMs = 85,
                syl2Ms = 330,
                syl2F0Start = 175.0, syl2F0End = 235.0,
                syl2F1 = 290.0, syl2F2 = 2320.0,
                syl2Amp = 0.68
            )
            val unevenRes = AcousticAnalyzer.analyzeByGender(
                file = unevenWav,
                speakerGender = "女",
                segmentCount = 2,
                pitchRange = Pair(145f, 245f),
                segmentLabels = listOf("八", "姨"),
                speakerMedianVoicedMs = 310
            )
            assertNotNull(unevenRes)
            unevenRes!!
            assertEquals(2, unevenRes.segmentCount)
            assertEquals(2, unevenRes.segments.size)
            assertEquals(2, unevenRes.formantTrajectories.size)
            // First segment should be significantly shorter than second segment (aligned to consonant valley, NOT blind 50/50 split!)
            val dur1 = unevenRes.segments[0].endSec - unevenRes.segments[0].startSec
            val dur2 = unevenRes.segments[1].endSec - unevenRes.segments[1].startSec
            assertTrue("Expected uneven valley split (dur1=$dur1 < dur2=$dur2 * 0.75)", dur1 < dur2 * 0.75)
            assertTrue("Expected 1st syllable checked flag", unevenRes.syllableCheckedFlags[0])
            assertFalse("Expected 2nd syllable smooth (not checked)", unevenRes.syllableCheckedFlags[1])
            // Verify distinct F1/F2 extracted per syllable ([a] -> high F1, [i] -> low F1 & high F2)
            assertTrue("Expected syl1 F1 (${unevenRes.formantTrajectories[0].nucleusF1}) > 550Hz", unevenRes.formantTrajectories[0].nucleusF1 > 550)
            assertTrue("Expected syl2 F1 (${unevenRes.formantTrajectories[1].nucleusF1}) < 430Hz", unevenRes.formantTrajectories[1].nucleusF1 < 430)
            assertTrue("Expected syl2 F2 (${unevenRes.formantTrajectories[1].nucleusF2}) > 1850Hz", unevenRes.formantTrajectories[1].nucleusF2 > 1850)

            // 3. Acoustic syllable promotion: Even if caller passes segmentCount = 1 (single-token label),
            // two unmistakable vocalic nuclei separated by an 85ms closure MUST be promoted to 2 syllables!
            val promotedWav = java.io.File(tempDir, "acoustic_promote_two.wav")
            writeTwoSyllableKlattWav(
                file = promotedWav,
                syl1Ms = 240,
                syl1F0Start = 225.0, syl1F0End = 222.0,
                syl1F1 = 760.0, syl1F2 = 1300.0,
                syl1Amp = 0.70, syl1AbruptStop = false,
                gapMs = 90,
                syl2Ms = 250,
                syl2F0Start = 230.0, syl2F0End = 150.0,
                syl2F1 = 310.0, syl2F2 = 2280.0,
                syl2Amp = 0.68
            )
            val promotedRes = AcousticAnalyzer.analyzeByGender(
                file = promotedWav,
                speakerGender = "女",
                segmentCount = 1,
                pitchRange = Pair(145f, 245f),
                segmentLabels = listOf("词条A"),
                speakerMedianVoicedMs = 260
            )
            assertNotNull(promotedRes)
            promotedRes!!
            assertEquals("Two clear acoustic syllables must not collapse into 1 syllable", 2, promotedRes.segmentCount)
            assertEquals(2, promotedRes.formantTrajectories.size)

            // 4. Disyllabic Neutral Tone (轻声): Full 280ms 1st syllable + weak low-energy 95ms 2nd syllable -> ·d neutral tone!
            val neutralWav = java.io.File(tempDir, "disyllable_neutral.wav")
            writeTwoSyllableKlattWav(
                file = neutralWav,
                syl1Ms = 285,
                syl1F0Start = 165.0, syl1F0End = 232.0,
                syl1F1 = 340.0, syl1F2 = 900.0,
                syl1Amp = 0.74, syl1AbruptStop = false,
                gapMs = 55,
                syl2Ms = 95,
                syl2F0Start = 168.0, syl2F0End = 158.0,
                syl2F1 = 520.0, syl2F2 = 1420.0,
                syl2Amp = 0.22
            )
            val neutralRes = AcousticAnalyzer.analyzeByGender(
                file = neutralWav,
                speakerGender = "女",
                segmentCount = 2,
                pitchRange = Pair(145f, 245f),
                segmentLabels = listOf("葡", "萄"),
                speakerMedianVoicedMs = 285
            )
            assertNotNull(neutralRes)
            neutralRes!!
            assertEquals(2, neutralRes.segmentCount)
            assertTrue("Weak short 2nd syllable should be flagged as neutral tone (got ${neutralRes.chaoToneNumbers})", neutralRes.syllableNeutralFlags[1])
            assertTrue("Expected · prefix on neutral syllable tone: ${neutralRes.chaoToneNumbers}", neutralRes.chaoToneNumbers.contains("·"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writeTwoSyllableKlattWav(
        file: java.io.File,
        syl1Ms: Int,
        syl1F0Start: Double,
        syl1F0End: Double,
        syl1F1: Double,
        syl1F2: Double,
        syl1Amp: Double,
        syl1AbruptStop: Boolean,
        gapMs: Int,
        syl2Ms: Int,
        syl2F0Start: Double,
        syl2F0End: Double,
        syl2F1: Double,
        syl2F2: Double,
        syl2Amp: Double
    ) {
        val sampleRate = 44100
        fun renderSyllablePcm(durMs: Int, f0S: Double, f0E: Double, f1: Double, f2: Double, amp: Double, abrupt: Boolean): DoubleArray {
            val nSamples = (sampleRate * durMs) / 1000
            val glottal = DoubleArray(nSamples)
            var phase = 0.0
            for (i in 0 until nSamples) {
                val tNorm = i.toDouble() / nSamples.coerceAtLeast(1)
                val f0 = f0S + (f0E - f0S) * tNorm
                phase += f0 / sampleRate
                if (phase >= 1.0) phase -= 1.0
                val pulse = if (phase < 0.40) {
                    0.5 * (1.0 - kotlin.math.cos(Math.PI * phase / 0.40))
                } else if (phase < 0.56) {
                    kotlin.math.cos(Math.PI * (phase - 0.40) / (2.0 * 0.16))
                } else 0.0
                val env = if (abrupt) {
                    if (tNorm < 0.12) (tNorm / 0.12) else if (tNorm > 0.93) ((1.0 - tNorm) / 0.07) else 1.0
                } else {
                    kotlin.math.sqrt(kotlin.math.sin(Math.PI * tNorm))
                }
                glottal[i] = (pulse + 0.07 * kotlin.math.sin(2.0 * Math.PI * phase)) * env
            }
            fun resFilter(inp: DoubleArray, freq: Double, bw: Double): DoubleArray {
                val out = DoubleArray(inp.size)
                val r = kotlin.math.exp(-Math.PI * bw / sampleRate)
                val theta = 2.0 * Math.PI * freq / sampleRate
                val a1 = 2.0 * r * kotlin.math.cos(theta)
                val a2 = -r * r
                val b0 = 1.0 - a1 - a2
                var y1 = 0.0
                var y2 = 0.0
                for (i in inp.indices) {
                    val y = b0 * inp[i] + a1 * y1 + a2 * y2
                    out[i] = y
                    y2 = y1
                    y1 = y
                }
                return out
            }
            val r1 = resFilter(glottal, f1, 75.0)
            val r2 = resFilter(r1, f2, 110.0)
            val r3 = resFilter(r2, 2550.0, 150.0)
            val rad = DoubleArray(nSamples)
            var peak = 1e-6
            for (i in 1 until nSamples) {
                rad[i] = r3[i] - 0.95 * r3[i - 1]
                val a = kotlin.math.abs(rad[i])
                if (a > peak) peak = a
            }
            for (i in rad.indices) {
                rad[i] = (rad[i] / peak) * amp
            }
            return rad
        }

        val padSamples = (sampleRate * 0.10).toInt()
        val gapSamples = (sampleRate * gapMs) / 1000
        val s1 = renderSyllablePcm(syl1Ms, syl1F0Start, syl1F0End, syl1F1, syl1F2, syl1Amp, syl1AbruptStop)
        val s2 = renderSyllablePcm(syl2Ms, syl2F0Start, syl2F0End, syl2F1, syl2F2, syl2Amp, false)
        val totalSamples = padSamples + s1.size + gapSamples + s2.size + padSamples

        val pcmBytes = ByteArray(totalSamples * 2)
        val buf = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until totalSamples) {
            val v = when {
                i in padSamples until (padSamples + s1.size) -> s1[i - padSamples]
                i in (padSamples + s1.size + gapSamples) until (padSamples + s1.size + gapSamples + s2.size) ->
                    s2[i - (padSamples + s1.size + gapSamples)]
                else -> kotlin.math.sin(i * 1.9) * 0.0006
            }
            buf.putShort((v * 32767.0).toInt().coerceIn(-32767, 32767).toShort())
        }

        java.io.FileOutputStream(file).use { fos ->
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + pcmBytes.size)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(pcmBytes.size)
            fos.write(header.array())
            fos.write(pcmBytes)
        }
    }

    @Test
    fun textGridPresets_customTiers_shortSyntax_andSilenceMarkers_parseCleanlyInPraatScanner() {
        val word = Word(
            id = 9L,
            projectId = 1L,
            text = "葡萄",
            remark = "[pʰu35 tʰau2] | grape",
            speakerNote = "",
            groupName = "轻声词",
            originalIndex = 2,
            isRecorded = true,
            durationMs = 720L
        )
        val analysis = AcousticAnalyzer.AnalysisResult(
            isValidVoiced = true,
            intensityDbfs = -15f,
            peakDbfs = -3f,
            snrDb = 26f,
            jitterPercent = 0.5f,
            shimmerPercent = 2.1f,
            pitchMean = 178f,
            pitchMin = 148f,
            pitchMax = 220f,
            pitchRange = "148 - 220 Hz",
            isPitchCalibrated = true,
            chaoToneNumbers = "35-·2",
            toneCode = "双音节调式 (35-·2)",
            toneDesc = "中升 + 轻声",
            toneTrajectory = "35 -> ·2",
            contourPoints = listOf(0.4f, 0.8f, 0.3f, 0.25f),
            rawVoicedPitches = listOf(178f),
            speakerF1 = listOf(350, 620),
            speakerF2 = listOf(880, 1280),
            segmentCount = 2,
            segments = listOf(
                AcousticAnalyzer.SegmentInterval(0.0750, 0.4150, "葡"),
                AcousticAnalyzer.SegmentInterval(0.4150, 0.6400, "萄")
            ),
            creakyBreaks = 0,
            voicedDurationMs = 565,
            totalDurationMs = 720,
            syllableToneNumbers = listOf("35", "·2"),
            syllableCheckedFlags = listOf(false, false),
            syllableNeutralFlags = listOf(false, true)
        )

        // 1. MFA 2-tier preset (words + phones)
        val mfaCfg = TextGridFormatConfig.fromPreset(TextGridPreset.MFA_WORDS_PHONES)
        val mfaTg = ZipUtility.buildPraatTextGrid(word, analysis, 0.720000, config = mfaCfg)
        assertTrue(mfaTg.contains("size = 2"))
        assertTrue(mfaTg.contains("name = \"words\""))
        assertTrue(mfaTg.contains("name = \"phones\""))
        assertTrue(mfaTg.contains("text = \"pʰu35\""))
        assertTrue(mfaTg.contains("text = \"tʰau2\""))
        verifyPraatTierContinuity(mfaTg, expectedTierCount = 2, expectedXmin = "0.000000", expectedXmax = "0.720000")

        // MFA fallback when word has no IPA remark -> phones tier falls back to syllable text
        val noIpaWord = word.copy(remark = "", speakerNote = "")
        val mfaFallbackTg = ZipUtility.buildPraatTextGrid(noIpaWord, analysis, 0.720000, config = mfaCfg)
        assertTrue(mfaFallbackTg.contains("text = \"葡\""))
        assertTrue(mfaFallbackTg.contains("text = \"萄\""))

        // 2. ProsodyPro single-tier preset
        val prosodyCfg = TextGridFormatConfig.fromPreset(TextGridPreset.PROSODY_SYLLABLES)
        val prosodyTg = ZipUtility.buildPraatTextGrid(word, analysis, 0.720000, config = prosodyCfg)
        assertTrue(prosodyTg.contains("size = 1"))
        assertTrue(prosodyTg.contains("name = \"syllables\""))
        verifyPraatTierContinuity(prosodyTg, expectedTierCount = 1, expectedXmin = "0.000000", expectedXmax = "0.720000")

        // 3. Custom tier names + Short TextGrid syntax + "sil" silence marker + JSON persistence round-trip
        val customCfg = TextGridFormatConfig(
            preset = TextGridPreset.CUSTOM,
            shortTextFormat = true,
            silenceMarker = "sil",
            timeDecimals = 6,
            includeGroupsTier = false,
            includeWordsTier = true,
            wordsTierName = "ort_word",
            includeSyllablesTier = true,
            syllablesTierName = "syl_unit",
            includeIpaTier = true,
            ipaTierName = "ipa_trans",
            includeTonesTier = true,
            tonesTierName = "chao_tone",
            includeSegmentsTier = false
        )
        val restoredCfg = TextGridFormatConfig.fromJsonString(customCfg.toJsonString())
        assertEquals(customCfg, restoredCfg)

        val shortTg = ZipUtility.buildPraatTextGrid(word, analysis, 0.720000, config = restoredCfg)
        assertFalse("Short TextGrid must omit verbose 'intervals [1]:' keys", shortTg.contains("intervals [1]:"))
        assertTrue(shortTg.contains("\"ort_word\""))
        assertTrue(shortTg.contains("\"syl_unit\""))
        assertTrue(shortTg.contains("\"ipa_trans\""))
        assertTrue(shortTg.contains("\"chao_tone\""))
        assertTrue(shortTg.contains("\"sil\""))

        // Verify Praat's official type-driven text scanner parses Short TextGrid cleanly
        val scanner = PraatCollectionExporter.PraatTextScanner(shortTg)
        assertEquals("TextGrid", scanner.getString())
        assertEquals(0.0, scanner.getDouble(), 1e-9)
        assertEquals(0.72, scanner.getDouble(), 1e-6)
        assertEquals("exists", scanner.getEnum())
        assertEquals(4, scanner.getInt())
        assertEquals("IntervalTier", scanner.getString())
        assertEquals("ort_word", scanner.getString())
        assertEquals(0.0, scanner.getDouble(), 1e-9)
        assertEquals(0.72, scanner.getDouble(), 1e-6)
        assertEquals(3, scanner.getInt())
        assertEquals(0.0, scanner.getDouble(), 1e-9)
        assertEquals(0.075, scanner.getDouble(), 1e-6)
        assertEquals("sil", scanner.getString())
    }
}




