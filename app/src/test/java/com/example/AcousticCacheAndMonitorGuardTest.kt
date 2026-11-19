package com.example

import android.media.AudioDeviceInfo
import com.example.audio.AcousticAnalyzer
import com.example.audio.AcousticFeatureDiskCache
import com.example.audio.FormantAnchorFrame
import com.example.audio.SyllableFormantTrajectory
import com.example.audio.VowelGlideType
import com.example.link.LiveEarMonitorPlayer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AcousticCacheAndMonitorGuardTest {

    @Test
    fun diskCacheRoundTripPreservesAnalysisAndQuickPitchAcrossMemoryRestart() {
        val tempDir = Files.createTempDirectory("phonacq_cache_test").toFile()
        try {
            val wavFile = File(tempDir, "word_1_speaker_2_1700000000000.wav")
            wavFile.writeBytes(ByteArray(256) { it.toByte() })

            val sampleAnalysis = AcousticAnalyzer.AnalysisResult(
                isValidVoiced = true,
                intensityDbfs = -16.4f,
                peakDbfs = -3.2f,
                snrDb = 29.5f,
                jitterPercent = 0.42f,
                shimmerPercent = 2.15f,
                pitchMean = 212.5f,
                pitchMin = 188.0f,
                pitchMax = 246.0f,
                pitchRange = "188-246Hz",
                isPitchCalibrated = true,
                chaoToneNumbers = "35",
                toneCode = "T2",
                toneDesc = "高升调",
                toneTrajectory = "3->5",
                contourPoints = listOf(0.45f, 0.55f, 0.68f, 0.82f, 0.94f),
                rawVoicedPitches = listOf(190f, 205f, 220f, 240f),
                speakerF1 = listOf(520),
                speakerF2 = listOf(1480),
                segmentCount = 1,
                segments = listOf(AcousticAnalyzer.SegmentInterval(0.05, 0.38, "花")),
                creakyBreaks = 0,
                voicedDurationMs = 310,
                totalDurationMs = 460,
                syllableToneNumbers = listOf("35"),
                speakerF3 = listOf(2620),
                formantTrajectories = listOf(
                    SyllableFormantTrajectory(
                        syllableIndex = 0,
                        syllableLabel = "花",
                        vowelType = VowelGlideType.DIPHTHONG,
                        anchors = listOf(
                            FormantAnchorFrame(0.2f, 0.11f, 410, 920, 2500, 0.91f),
                            FormantAnchorFrame(0.8f, 0.31f, 760, 1340, 2610, 0.88f)
                        ),
                        onsetF1 = 410,
                        onsetF2 = 920,
                        nucleusF1 = 580,
                        nucleusF2 = 1150,
                        nucleusF3 = 2560,
                        targetF1 = 760,
                        targetF2 = 1340,
                        barkGlideSpan = 2.1f,
                        trajectoryAngleDeg = 42.5f,
                        trajectorySummary = "ua"
                    )
                ),
                isCheckedTone = false,
                checkedToneRatio = 1.0f,
                syllableCheckedFlags = listOf(false),
                syllableNeutralFlags = listOf(false),
                syllableVoicedMs = listOf(310)
            )

            val analysisKey = AcousticFeatureDiskCache.buildAnalysisKey(
                file = wavFile,
                normGender = "女",
                segmentCount = 1,
                pitchMin = 150,
                pitchMax = 310,
                medianDurMs = 290,
                labelSig = "花"
            )
            val qpKey = AcousticFeatureDiskCache.buildQuickPitchKey(wavFile, "女")
            val quickPitch = AcousticAnalyzer.QuickPitchSummary(
                isValidVoiced = true,
                perSyllableVoicedMs = 310,
                interiorCorePitches = floatArrayOf(195f, 210f, 228f)
            )

            AcousticFeatureDiskCache.putAnalysis(wavFile, analysisKey, sampleAnalysis, deferFlush = true)
            AcousticFeatureDiskCache.putQuickPitch(wavFile, qpKey, quickPitch, deferFlush = true)
            AcousticFeatureDiskCache.flushProject(tempDir)

            // Simulate full app restart by clearing the in-memory index
            AcousticFeatureDiskCache.clearMemoryIndex()

            val restoredAnalysis = AcousticFeatureDiskCache.getAnalysis(wavFile, analysisKey)
            assertNotNull(restoredAnalysis)
            assertEquals(sampleAnalysis, restoredAnalysis)

            val restoredQp = AcousticFeatureDiskCache.getQuickPitch(wavFile, qpKey)
            assertNotNull(restoredQp)
            assertEquals(quickPitch.isValidVoiced, restoredQp!!.isValidVoiced)
            assertEquals(quickPitch.perSyllableVoicedMs, restoredQp.perSyllableVoicedMs)
            assertArrayEquals(quickPitch.interiorCorePitches, restoredQp.interiorCorePitches, 0.001f)

            // Updating the WAV file invalidates the old key automatically
            wavFile.writeBytes(ByteArray(512) { 7 })
            val updatedKey = AcousticFeatureDiskCache.buildAnalysisKey(
                file = wavFile,
                normGender = "女",
                segmentCount = 1,
                pitchMin = 150,
                pitchMax = 310,
                medianDurMs = 290,
                labelSig = "花"
            )
            assertNull(AcousticFeatureDiskCache.getAnalysis(wavFile, updatedKey))
        } finally {
            AcousticFeatureDiskCache.deleteProjectCache(tempDir)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun diskCacheRecoversGracefullyFromCorruptFile() {
        val tempDir = Files.createTempDirectory("phonacq_corrupt_cache_test").toFile()
        try {
            val corruptFile = File(tempDir, ".phonacq_acoustic_cache_v1.bin")
            corruptFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C))
            val wavFile = File(tempDir, "word_9_speaker_1_1700000000001.wav")
            wavFile.writeBytes(ByteArray(128))

            AcousticFeatureDiskCache.clearMemoryIndex()
            val key = AcousticFeatureDiskCache.buildQuickPitchKey(wavFile, "男")
            assertNull(AcousticFeatureDiskCache.getQuickPitch(wavFile, key))
            assertFalse(corruptFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun earMonitorHeadphoneClassificationRejectsBuiltinSpeaker() {
        assertTrue(LiveEarMonitorPlayer.isHeadphoneOutputDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(LiveEarMonitorPlayer.isHeadphoneOutputDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertTrue(LiveEarMonitorPlayer.isHeadphoneOutputDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(LiveEarMonitorPlayer.isHeadphoneOutputDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertFalse(LiveEarMonitorPlayer.isHeadphoneOutputDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(LiveEarMonitorPlayer.isHeadphoneOutputDeviceType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
    }

    @Test
    fun detectPlaybackSpeechBoundsMsSkipsLeadingAndTrailingSilenceNonDestructively() {
        val tempDir = Files.createTempDirectory("phonacq_silence_bounds_test").toFile()
        try {
            val sr = 16000
            val totalSamples = sr * 2 // 2000ms total
            val pcm = ShortArray(totalSamples)
            // Speech burst between 600ms and 1200ms (220Hz sine wave at moderate amplitude)
            val startSample = (sr * 0.6).toInt()
            val endSample = (sr * 1.2).toInt()
            for (i in startSample until endSample) {
                val sample = (kotlin.math.sin(2.0 * Math.PI * 220.0 * i / sr) * 12000.0).toInt()
                pcm[i] = sample.toShort()
            }

            val wavFile = File(tempDir, "speech_with_silence.wav")
            val dataBytes = pcm.size * 2
            val bb = java.nio.ByteBuffer.allocate(44 + dataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.put("RIFF".toByteArray())
            bb.putInt(36 + dataBytes)
            bb.put("WAVE".toByteArray())
            bb.put("fmt ".toByteArray())
            bb.putInt(16)
            bb.putShort(1) // PCM
            bb.putShort(1) // mono
            bb.putInt(sr)
            bb.putInt(sr * 2)
            bb.putShort(2)
            bb.putShort(16)
            bb.put("data".toByteArray())
            bb.putInt(dataBytes)
            for (s in pcm) bb.putShort(s)
            val originalBytes = bb.array()
            wavFile.writeBytes(originalBytes)

            val bounds = AcousticAnalyzer.detectPlaybackSpeechBoundsMs(wavFile)
            assertNotNull(bounds)
            val (startMs, endMs) = bounds!!
            // With -85ms lead-in and +135ms tail-out margins around [600ms, 1200ms]
            assertTrue("Expected startMs around 515ms, got $startMs", startMs in 450..560)
            assertTrue("Expected endMs around 1335ms, got $endMs", endMs in 1280..1400)

            // Guarantee non-destructive behavior: original WAV file is byte-for-byte untouched
            assertEquals(originalBytes.size.toLong(), wavFile.length())
            assertArrayEquals(originalBytes, wavFile.readBytes())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
