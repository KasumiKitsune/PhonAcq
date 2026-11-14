package com.example.audio

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

object AcousticAnalyzer {
    private const val TAG = "AcousticAnalyzer"

    private fun <K, V> createBoundedLruMap(maxEntries: Int): MutableMap<K, V> {
        return Collections.synchronizedMap(
            object : LinkedHashMap<K, V>(maxEntries + 16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                    return size > maxEntries
                }
            }
        )
    }

    @Immutable
    data class SegmentInterval(
        val startSec: Double,
        val endSec: Double,
        val label: String
    )

    @Immutable
    data class AnalysisResult(
        val isValidVoiced: Boolean,
        val intensityDbfs: Float,
        val peakDbfs: Float,
        val snrDb: Float,
        val jitterPercent: Float,
        val shimmerPercent: Float,
        val pitchMean: Float,
        val pitchMin: Float,
        val pitchMax: Float,
        val pitchRange: String,
        val isPitchCalibrated: Boolean,
        val chaoToneNumbers: String,
        val toneCode: String,
        val toneDesc: String,
        val toneTrajectory: String,
        val contourPoints: List<Float>, // Normalized 0f..1f (mapping to Chao 1..5 in log scale)
        val rawVoicedPitches: List<Float>,
        val speakerF1: List<Int>,
        val speakerF2: List<Int>,
        val segmentCount: Int,
        val segments: List<SegmentInterval>,
        val creakyBreaks: Int,
        val voicedDurationMs: Int,
        val totalDurationMs: Int,
        val syllableToneNumbers: List<String> = emptyList(),
        val speakerF3: List<Int> = emptyList(),
        val formantTrajectories: List<SyllableFormantTrajectory> = emptyList(),
        val isCheckedTone: Boolean = false,
        val checkedToneRatio: Float = 1.0f,
        val syllableCheckedFlags: List<Boolean> = emptyList(),
        val syllableNeutralFlags: List<Boolean> = emptyList(),
        val syllableVoicedMs: List<Int> = emptyList()
    )

    @Immutable
    data class PhoneticAcousticResult(
        val isValidVoiced: Boolean,
        val pitchMean: Float,
        val pitchMin: Float,
        val pitchMax: Float,
        val pitchRange: String,
        val isPitchCalibrated: Boolean,
        val chaoToneNumbers: String,
        val toneCode: String,
        val toneDesc: String,
        val toneTrajectory: String,
        val speakerF1: List<Int>,
        val speakerF2: List<Int>,
        val segmentCount: Int,
        val segments: List<SegmentInterval>,
        val intensityDbfs: Float,
        val peakDbfs: Float,
        val filePath: String,
        val fileSizeKb: Double,
        val contourPoints: List<Float>,
        val snrDb: Float,
        val jitterPercent: Float,
        val shimmerPercent: Float,
        val syllableToneNumbers: List<String> = emptyList(),
        val speakerF3: List<Int> = emptyList(),
        val formantTrajectories: List<SyllableFormantTrajectory> = emptyList(),
        val voicedDurationMs: Int = 0,
        val isCheckedTone: Boolean = false,
        val checkedToneRatio: Float = 1.0f,
        val canonicalToneNumbers: String = "",
        val canonicalToneName: String = "",
        val isToneOutlier: Boolean = false,
        val syllableCheckedFlags: List<Boolean> = emptyList(),
        val syllableNeutralFlags: List<Boolean> = emptyList(),
        val syllableVoicedMs: List<Int> = emptyList()
    )

    private data class CacheKey(
        val path: String,
        val lastModified: Long,
        val genderKey: String,
        val segmentCount: Int,
        val pitchMin: Int,
        val pitchMax: Int,
        val medianDurMs: Int = -1
    )

    private data class QuickPitchSummary(
        val isValidVoiced: Boolean,
        val perSyllableVoicedMs: Int,
        val interiorCorePitches: FloatArray
    )

    @Immutable
    data class SpeakerCorpusAcousticStats(
        val fileCount: Int,
        val latestModified: Long,
        val pitchRange: Pair<Float, Float>,
        val medianVoicedDurationMs: Int
    )

    private val analysisCache = createBoundedLruMap<CacheKey, AnalysisResult>(1200)
    private val envelopeCache = createBoundedLruMap<String, List<Float>>(1000)
    private val quickPitchCache = createBoundedLruMap<String, QuickPitchSummary>(1500)
    private val autoPitchCache = ConcurrentHashMap<String, SpeakerCorpusAcousticStats>()
    private val corpusStatsLocks = ConcurrentHashMap<String, Any>()

    data class WavHeaderFormat(
        val sampleRate: Int = 44100,
        val channels: Int = 1,
        val bitsPerSample: Int = 16,
        val dataOffset: Int = 44
    ) {
        val bytesPerSample: Int get() = (bitsPerSample / 8).coerceAtLeast(1)
        val blockAlign: Int get() = (channels * bytesPerSample).coerceAtLeast(1)
        val avgBytesPerSec: Int get() = (sampleRate * blockAlign).coerceAtLeast(1)
    }

    fun readWavHeaderFormat(file: File?): WavHeaderFormat {
        if (file == null || !file.exists() || file.length() < 44L) return WavHeaderFormat()
        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(44)
                if (raf.read(header) == 44 &&
                    header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'A'.code.toByte() &&
                    header[10] == 'V'.code.toByte() && header[11] == 'E'.code.toByte()
                ) {
                    val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val ch = (buf.getShort(22).toInt() and 0xFFFF).let { if (it in 1..8) it else 1 }
                    val sr = buf.getInt(24).let { if (it in 8000..192000) it else 44100 }
                    val bps = (buf.getShort(34).toInt() and 0xFFFF).let { if (it in listOf(8, 16, 24, 32)) it else 16 }
                    WavHeaderFormat(sampleRate = sr, channels = ch, bitsPerSample = bps, dataOffset = 44)
                } else {
                    WavHeaderFormat()
                }
            }
        } catch (ignored: Exception) {
            WavHeaderFormat()
        }
    }

    /**
     * Returns exact sample-accurate duration in seconds of a WAV file by inspecting its RIFF header.
     */
    fun getExactWavDurationSec(file: File?): Double {
        if (file == null || !file.exists() || file.length() <= 44L) return 0.1
        val fmt = readWavHeaderFormat(file)
        val pcmBytes = (file.length() - fmt.dataOffset).coerceAtLeast(0L)
        return (pcmBytes.toDouble() / fmt.avgBytesPerSec.toDouble()).coerceAtLeast(0.05)
    }

    /**
     * Extracts genuine amplitude envelope points from a recorded WAV file.
     * Returns emptyList() if the file does not exist or has no audio data.
     */
    fun getRealWaveformEnvelope(file: File?, bins: Int = 28): List<Float> {
        if (file == null || !file.exists() || file.length() <= 44L) return emptyList()
        val cacheKey = "${file.absolutePath}_${file.lastModified()}_$bins"
        envelopeCache[cacheKey]?.let { return it }

        val pcm = loadWavPcm(file) ?: return emptyList()
        if (pcm.isEmpty()) return emptyList()

        val chunkSize = (pcm.size / bins).coerceAtLeast(1)
        val rawRms = FloatArray(bins)
        var maxRms = 0.001f

        for (b in 0 until bins) {
            val start = b * chunkSize
            val end = if (b == bins - 1) pcm.size else ((b + 1) * chunkSize).coerceAtMost(pcm.size)
            if (start >= end) continue
            var sumSq = 0.0
            for (i in start until end) {
                val s = pcm[i].toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / (end - start)).toFloat()
            rawRms[b] = rms
            if (rms > maxRms) maxRms = rms
        }

        // Normalize relative to peak RMS while preserving dynamic range
        val scaleRef = maxRms.coerceAtLeast(0.02f)
        val result = rawRms.map { (it / scaleRef).coerceIn(0.04f, 1.0f) }
        envelopeCache[cacheKey] = result
        return result
    }

    fun analyze(
        file: File,
        isFemale: Boolean,
        segmentCount: Int = 1,
        pitchRange: Pair<Float, Float>? = null,
        segmentLabels: List<String> = emptyList()
    ): AnalysisResult? {
        return analyzeByGender(
            file = file,
            speakerGender = if (isFemale) "女" else "男",
            segmentCount = segmentCount,
            pitchRange = pitchRange,
            segmentLabels = segmentLabels
        )
    }

    /**
     * Inclusive, self-adapting acoustic DSP analysis pipeline:
     * 1. Adaptive VAD trimming & SNR calculation
     * 2. Boersma-style Hann-windowed Autocorrelation F0 tracking with octave-cost & zero-crossing guard
     * 3. Consonant onset/offset micro-prosody trimming + 5-point median/moving-average contour smoothing
     * 4. Multi-feature vowel-core Creaky Voice (Vocal Fry) detection (immune to single-frame pitch jumps & low-tone tails)
     * 5. Praat-grade anti-aliased LPC + Complex Polynomial Root (Durand-Kerner) + Voice-Bar-Suppressed Formant (F1, F2) tracking
     */
    fun analyzeByGender(
        file: File,
        speakerGender: String?,
        segmentCount: Int = 1,
        pitchRange: Pair<Float, Float>? = null,
        segmentLabels: List<String> = emptyList(),
        speakerMedianVoicedMs: Int = -1
    ): AnalysisResult? {
        if (!file.exists() || file.length() <= 44L) return null
        val normGender = speakerGender?.trim() ?: ""
        val labelSig = segmentLabels.joinToString("|")
        val key = CacheKey(
            path = "$normGender|$labelSig|${file.absolutePath}",
            lastModified = file.lastModified(),
            genderKey = normGender,
            segmentCount = segmentCount,
            pitchMin = pitchRange?.first?.toInt() ?: -1,
            pitchMax = pitchRange?.second?.toInt() ?: -1,
            medianDurMs = speakerMedianVoicedMs
        )
        analysisCache[key]?.let { return it }

        val pcm = loadWavPcm(file) ?: return null
        if (pcm.size < 1024) return null

        val wavFmt = readWavHeaderFormat(file)
        val sampleRate = wavFmt.sampleRate
        val totalDurationMs = (pcm.size * 1000L / sampleRate).toInt()

        // 1. Short-time RMS & Peak in 1024-sample blocks (23.2ms)
        val blockSize = 1024
        val blockCount = (pcm.size / blockSize).coerceAtLeast(1)

        val blockRms = FloatArray(blockCount)
        var maxBlockRms = 0f
        var globalPeak = 0f

        for (b in 0 until blockCount) {
            var sumSquare = 0.0
            val offset = b * blockSize
            val endIdx = (offset + blockSize).coerceAtMost(pcm.size)
            for (i in offset until endIdx) {
                val v = pcm[i]
                val absV = abs(v)
                if (absV > globalPeak) globalPeak = absV
                sumSquare += v * v
            }
            val rms = sqrt(sumSquare / (endIdx - offset).coerceAtLeast(1)).toFloat()
            blockRms[b] = rms
            if (rms > maxBlockRms) maxBlockRms = rms
        }

        // 2. Adaptive Voice Activity Detection (VAD) — exclude cold-start dither/zero blocks (rms <= 1.5e-4f)
        // so artificial pre-roll padding never skews the 20th-percentile noise floor estimate toward 0.
        val physicalBlocks = blockRms.filter { it > 1.5e-4f }.ifEmpty { blockRms.toList() }
        val sortedRms = physicalBlocks.sorted()
        val noiseFloorEstimate = sortedRms.take((sortedRms.size * 0.2).toInt().coerceAtLeast(1)).average().toFloat()
        val vadThreshold = maxOf(noiseFloorEstimate * 2.5f, maxBlockRms * 0.10f, 0.003f)

        val speechBlocks = blockRms.filter { it >= vadThreshold }
        val noiseBlocks = physicalBlocks.filter { it < vadThreshold }

        val activeRmsMean = if (speechBlocks.isNotEmpty()) {
            sqrt(speechBlocks.map { it.toDouble() * it.toDouble() }.average()).toFloat()
        } else {
            sqrt(physicalBlocks.map { it.toDouble() * it.toDouble() }.average()).toFloat()
        }
        val noiseRmsMean = if (noiseBlocks.isNotEmpty()) {
            sqrt(noiseBlocks.map { it.toDouble() * it.toDouble() }.average()).toFloat()
        } else {
            noiseFloorEstimate.coerceAtLeast(1e-5f)
        }

        val intensityDbfs = (20.0 * log10(activeRmsMean.toDouble().coerceAtLeast(3e-5))).toFloat().coerceIn(-90f, 0f)
        val peakDbfs = (20.0 * log10(globalPeak.toDouble().coerceAtLeast(3e-5))).toFloat().coerceIn(-90f, 0f)

        val snrDb = (20.0 * log10(activeRmsMean.toDouble().coerceAtLeast(1e-5) / noiseRmsMean.toDouble().coerceAtLeast(1e-5)))
            .toFloat().coerceIn(0f, 65f)

        // Locate active speech start and end sample indices (VAD trimming)
        val firstSpeechBlock = blockRms.indexOfFirst { it >= vadThreshold }.takeIf { it >= 0 } ?: 0
        val lastSpeechBlock = blockRms.indexOfLast { it >= vadThreshold }.takeIf { it >= firstSpeechBlock } ?: (blockCount - 1)
        val speechStartSample = (firstSpeechBlock * blockSize).coerceAtLeast(0)
        val speechEndSample = ((lastSpeechBlock + 1) * blockSize).coerceAtMost(pcm.size)

        // 3. Boersma-style F0 Tracking with Octave-Halving Suppression
        val searchMinHz = if (pitchRange != null && pitchRange.first > 40f) {
            (pitchRange.first * 0.72f).coerceAtLeast(60f)
        } else {
            when (normGender) {
                "女" -> 115f
                "男" -> 70f
                else -> 75f
            }
        }
        val searchMaxHz = if (pitchRange != null && pitchRange.second > 60f) {
            (pitchRange.second * 1.28f).coerceAtMost(520f)
        } else {
            when (normGender) {
                "女" -> 450f
                "男" -> 310f
                else -> 460f
            }
        }

        val minLag = (sampleRate / searchMaxHz).toInt().coerceAtLeast(45)
        val maxLag = (sampleRate / searchMinHz).toInt().coerceAtMost(880)

        val frameSize = 2048
        val hopSize = 512 // 11.6ms hop
        val totalFrames = if (pcm.size >= frameSize) (pcm.size - frameSize) / hopSize + 1 else 0

        val framePitchHz = FloatArray(totalFrames) { 0f }
        val frameMaxAcf = FloatArray(totalFrames) { 0f }
        val frameRmsArr = FloatArray(totalFrames) { 0f }
        val frameSubharmonicRatio = FloatArray(totalFrames) { 0f }
        val frameIsVoicedSeq = BooleanArray(totalFrames)
        val windowLen = 1024

        var maxVoicedFrameRms = 0.001f
        val acf = FloatArray(maxLag + 2)

        for (f in 0 until totalFrames) {
            val offset = f * hopSize
            var sumSq = 0.0
            for (i in 0 until frameSize) {
                val v = pcm[offset + i]
                sumSq += v * v
            }
            val fRms = sqrt(sumSq / frameSize).toFloat()
            frameRmsArr[f] = fRms
            if (fRms < vadThreshold * 0.75f) continue

            // Zero-crossing rate check to reject voiceless fricatives / aspiration (s, sh, f, x, pʰ, tʰ)
            var zcr = 0
            for (i in 0 until frameSize - 1) {
                if ((pcm[offset + i] >= 0f) != (pcm[offset + i + 1] >= 0f)) zcr++
            }
            val zcrRate = zcr.toFloat() / frameSize
            if (zcrRate > 0.28f) continue

            var dSumLeft = 0.0
            for (i in 0 until windowLen) {
                val x = pcm[offset + i].toDouble()
                dSumLeft += x * x
            }
            if (dSumLeft <= 1e-7) continue

            var dSumRight = 0.0
            for (i in 0 until windowLen) {
                val y = pcm[offset + i + minLag].toDouble()
                dSumRight += y * y
            }

            acf[minLag - 1] = 0f
            acf[maxLag + 1] = 0f
            for (lag in minLag..maxLag) {
                if (lag > minLag) {
                    val oldY = pcm[offset + lag - 1].toDouble()
                    val newY = pcm[offset + lag + windowLen - 1].toDouble()
                    dSumRight = (dSumRight - oldY * oldY + newY * newY).coerceAtLeast(0.0)
                }
                var rSum = 0.0
                for (i in 0 until windowLen) {
                    rSum += pcm[offset + i] * pcm[offset + i + lag]
                }
                val denom = sqrt(dSumLeft * dSumRight)
                val rNorm = if (denom > 1e-7) (rSum / denom).toFloat() else 0f
                acf[lag] = rNorm
            }

            // Wait until ACF has descended from the main lobe (either negative or past a clear valley)
            var searchStartLag = minLag + 1
            while (searchStartLag < maxLag - 2 && acf[searchStartLag] > acf[searchStartLag + 1] && acf[searchStartLag] > 0.25f) {
                searchStartLag++
            }

            // Collect all local peaks in ACF and apply Boersma octave-cost preference for shorter lags
            var bestLag = -1
            var bestWeightedScore = -1f
            var rawPeakAtBest = 0f

            for (lag in searchStartLag until maxLag) {
                val c = acf[lag]
                if (c > 0.48f && c >= acf[lag - 1] && c >= acf[lag + 1]) {
                    // Slight preference for shorter lags (higher F0) to prevent 2*T0 octave-halving
                    val octavePenalty = 0.10f * (lag - minLag).toFloat() / (maxLag - minLag).coerceAtLeast(1)
                    val weightedScore = c - octavePenalty
                    if (weightedScore > bestWeightedScore) {
                        bestWeightedScore = weightedScore
                        bestLag = lag
                        rawPeakAtBest = c
                    }
                }
            }

            // Explicit octave-halving guard: if bestLag is ~2x an earlier clear peak, prefer the fundamental T0
            if (bestLag > minLag * 2) {
                val halfLag = bestLag / 2
                if (halfLag >= searchStartLag) {
                    val cHalf = maxOf(
                        acf[(halfLag - 1).coerceAtLeast(minLag)],
                        acf[halfLag],
                        acf[(halfLag + 1).coerceAtMost(maxLag)]
                    )
                    if (cHalf >= 0.55f && cHalf >= rawPeakAtBest * 0.82f) {
                        for (candidate in (halfLag - 2).coerceAtLeast(searchStartLag)..(halfLag + 2).coerceAtMost(maxLag - 1)) {
                            if (acf[candidate] >= acf[candidate - 1] && acf[candidate] >= acf[candidate + 1]) {
                                bestLag = candidate
                                rawPeakAtBest = acf[candidate]
                                break
                            }
                        }
                    }
                }
            }

            if (bestLag != -1 && rawPeakAtBest > 0.48f) {
                val y1 = acf[bestLag - 1]
                val y2 = acf[bestLag]
                val y3 = acf[bestLag + 1]
                val denom = 2f * (2f * y2 - y1 - y3)
                val delta = if (abs(denom) > 1e-5f) ((y1 - y3) / denom).coerceIn(-0.5f, 0.5f) else 0f
                val preciseLag = bestLag + delta
                val f0 = sampleRate / preciseLag
                if (f0 in searchMinHz..searchMaxHz) {
                    framePitchHz[f] = f0
                    frameMaxAcf[f] = rawPeakAtBest
                    frameIsVoicedSeq[f] = true
                    if (fRms > maxVoicedFrameRms) maxVoicedFrameRms = fRms

                    val doubleLag = (bestLag * 2)
                    if (doubleLag <= maxLag) {
                        frameSubharmonicRatio[f] = acf[doubleLag] / rawPeakAtBest.coerceAtLeast(0.1f)
                    }
                }
            }
        }

        // Global outlier rejection + 5-point median smoothing on voiced F0 frames
        val rawVoicedList = mutableListOf<Float>()
        for (f in 0 until totalFrames) {
            if (frameIsVoicedSeq[f] && framePitchHz[f] > 0f) {
                rawVoicedList.add(framePitchHz[f])
            }
        }

        val smoothedVoicedPitches = cleanAndSmoothPitchSequence(rawVoicedList)
        // Also write back cleaned F0 to voiced frames so per-syllable extraction uses octave-corrected F0
        if (smoothedVoicedPitches.isNotEmpty()) {
            val globalMedianF0 = smoothedVoicedPitches.sorted()[smoothedVoicedPitches.size / 2]
            for (f in 0 until totalFrames) {
                if (frameIsVoicedSeq[f]) {
                    val p = framePitchHz[f]
                    // Correct isolated octave-halving/doubling frames against global median
                    if (p > globalMedianF0 * 1.72f && (p * 0.5f) in searchMinHz..searchMaxHz) {
                        framePitchHz[f] = p * 0.5f
                    } else if (p < globalMedianF0 * 0.56f && (p * 2.0f) in searchMinHz..searchMaxHz) {
                        framePitchHz[f] = p * 2.0f
                    }
                }
            }
        }

        val isValidVoiced = smoothedVoicedPitches.size >= 3
        val voicedDurationMs = ((smoothedVoicedPitches.size * hopSize) * 1000L / sampleRate).toInt()

        // 4. Pitch-synchronous Glottal Pulse Jitter (local %) and Shimmer (local %)
        val pitchMean = if (isValidVoiced) smoothedVoicedPitches.average().toFloat() else 0f
        val sortedPitches = smoothedVoicedPitches.sorted()
        val pitchMin = if (isValidVoiced) sortedPitches[(sortedPitches.size * 0.05).toInt()] else 0f
        val pitchMax = if (isValidVoiced) sortedPitches[(sortedPitches.size * 0.95).toInt().coerceAtMost(sortedPitches.size - 1)] else 0f
        val pitchRangeText = if (isValidVoiced) {
            S.acoustics.pitchRangeFormat.withArgs(
                "min" to String.format(Locale.US, "%.0f", pitchMin),
                "max" to String.format(Locale.US, "%.0f", pitchMax)
            )
        } else {
            S.acoustics.noVoicedDetected
        }

        val (jitterPercent, shimmerPercent) = if (isValidVoiced) {
            computePitchSynchronousPerturbation(pcm, speechStartSample, speechEndSample, pitchMean, sampleRate)
        } else {
            0f to 0f
        }

        // 5. Redesigned Multi-Feature Vowel-Core Creaky Voice (Vocal Fry) Detector
        // Only inspects the steady-state interior [20%..76%] of the voiced region, ignoring
        // initial consonant release perturbation and natural low-tone tail decay (e.g., Mandarin 214/51).
        val creakyBreaks = detectSustainedVowelCoreCreak(
            frameIsVoicedSeq = frameIsVoicedSeq,
            framePitchHz = framePitchHz,
            frameMaxAcf = frameMaxAcf,
            frameRmsArr = frameRmsArr,
            frameSubharmonicRatio = frameSubharmonicRatio,
            maxVoicedFrameRms = maxVoicedFrameRms,
            medianF0 = if (sortedPitches.isNotEmpty()) sortedPitches[sortedPitches.size / 2] else 0f,
            jitterPercent = jitterPercent,
            shimmerPercent = shimmerPercent
        )

        // Determine vocal register adaptively from actual detected pitchMean (or gender hint if unvoiced)
        val isHighRegister = when {
            isValidVoiced -> pitchMean >= 170f
            normGender == "女" -> true
            else -> false
        }
        val isMidRegister = when {
            isValidVoiced -> pitchMean in 140f..185f && normGender != "男" && normGender != "女"
            normGender != "男" && normGender != "女" -> true
            else -> false
        }

        val isPitchCalibrated = pitchRange != null && pitchRange.first > 40f && pitchRange.second > pitchRange.first + 15f
        // Physiological register anchor so that when a word is uncalibrated, a High Level (55) or
        // Low Level (11) tone sits at its true register height instead of always centering at 0.50 ([33]).
        val regNormFloor = when {
            normGender == "女" || (normGender != "男" && isHighRegister) -> 142f
            normGender == "男" -> 86f
            isMidRegister -> 112f
            else -> 95f
        }
        val regNormCeil = when {
            normGender == "女" || (normGender != "男" && isHighRegister) -> 305f
            normGender == "男" -> 192f
            isMidRegister -> 248f
            else -> 220f
        }
        val refMinHz = if (isPitchCalibrated) {
            pitchRange!!.first
        } else if (isValidVoiced) {
            val blendedFloor = (pitchMean * 0.72f + regNormFloor) * 0.5f
            minOf(pitchMin * 0.92f, blendedFloor).coerceAtLeast(60f)
        } else {
            regNormFloor
        }
        val refMaxHz = if (isPitchCalibrated) {
            pitchRange!!.second
        } else if (isValidVoiced) {
            val blendedCeil = (pitchMean * 1.28f + regNormCeil) * 0.5f
            maxOf(pitchMax * 1.06f, blendedCeil).coerceAtLeast(refMinHz + 42f)
        } else {
            regNormCeil
        }

        // 6. Hybrid Acoustic-Orthographic Multi-Syllable Detection & Valley Segmentation
        val hasErhuaHint = segmentLabels.size >= 2 &&
            (segmentLabels.last() == "儿" || segmentLabels.last() == "兒") &&
            segmentLabels[segmentLabels.size - 2] !in ERHUA_NON_FUSING_PREFIXES
        val syllableBounds = detectAndAlignSyllableSegments(
            pcm = pcm,
            startSample = speechStartSample,
            endSample = speechEndSample,
            expectedCountHint = segmentCount,
            sampleRate = sampleRate,
            labels = segmentLabels,
            frameIsVoicedSeq = frameIsVoicedSeq,
            hopSize = hopSize,
            hasErhuaSuffix = hasErhuaHint
        )
        val effectiveSegmentCount = syllableBounds.size.coerceAtLeast(1)
        val segmentsList = syllableBounds.map { (sIdx, eIdx, label) ->
            SegmentInterval(
                startSec = sIdx.toDouble() / sampleRate,
                endSec = eIdx.toDouble() / sampleRate,
                label = label
            )
        }

        // Extract 5 contour points per syllable using logarithmic T-value formula,
        // with amplitude-weighted consonant onset perturbation & breathy tail trimming,
        // plus Checked Tone (入声 / 促声短调) and Neutral Tone (轻声 / 弱读音节) detection.
        val logMin = log10(refMinHz.toDouble().coerceAtLeast(40.0))
        val logMax = log10(refMaxHz.toDouble().coerceAtLeast(refMinHz + 20.0))
        val logSpan = (logMax - logMin).coerceAtLeast(0.08)

        val contourPoints = mutableListOf<Float>()
        val perSyllableCheckedFlags = mutableListOf<Boolean>()
        val perSyllableNeutralFlags = mutableListOf<Boolean>()
        val perSyllableVoicedMs = mutableListOf<Int>()
        val perSyllableDurRatios = mutableListOf<Float>()
        val pointsPerSeg = 5
        val refMedianDurMs = if (speakerMedianVoicedMs >= 160) speakerMedianVoicedMs else 285

        data class RawSyllableAcousticSlice(
            val segStartSample: Int,
            val segEndSample: Int,
            val label: String,
            val voicedFrameIndices: List<Int>,
            val segVoicedMs: Int,
            val segPeakRms: Float,
            val isAbruptCoda: Boolean
        )

        if (isValidVoiced) {
            val rawSlices = syllableBounds.map { (segStartSample, segEndSample, lbl) ->
                val startFrame = (segStartSample / hopSize).coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
                val endFrame = (segEndSample / hopSize).coerceIn(startFrame, (totalFrames - 1).coerceAtLeast(0))

                val voicedFrameIndices = ArrayList<Int>()
                var segPeakRms = 1e-4f
                for (f in startFrame..endFrame) {
                    if (frameIsVoicedSeq[f] && framePitchHz[f] > 0f) {
                        voicedFrameIndices.add(f)
                        if (frameRmsArr[f] > segPeakRms) segPeakRms = frameRmsArr[f]
                    }
                }
                // If a weak neutral syllable had low ACF periodicity, also inspect raw active frames for peak RMS
                if (voicedFrameIndices.isEmpty()) {
                    for (f in startFrame..endFrame) {
                        if (frameRmsArr[f] > segPeakRms) segPeakRms = frameRmsArr[f]
                    }
                }

                val segVoicedMs = ((voicedFrameIndices.size * hopSize) * 1000L / sampleRate).toInt()
                val lastVoicedIdx = voicedFrameIndices.lastOrNull() ?: endFrame
                val postCodaIdx = (lastVoicedIdx + 2).coerceAtMost((totalFrames - 1).coerceAtLeast(0))
                val postCodaRms = frameRmsArr.getOrElse(postCodaIdx) { 0f }
                val isAbruptCoda = postCodaRms <= segPeakRms * 0.24f

                RawSyllableAcousticSlice(
                    segStartSample = segStartSample,
                    segEndSample = segEndSample,
                    label = lbl,
                    voicedFrameIndices = voicedFrameIndices,
                    segVoicedMs = segVoicedMs,
                    segPeakRms = segPeakRms,
                    isAbruptCoda = isAbruptCoda
                )
            }

            val maxWordSegPeakRms = rawSlices.maxOfOrNull { it.segPeakRms }?.coerceAtLeast(1e-4f) ?: 1e-4f
            val maxWordSegVoicedMs = rawSlices.maxOfOrNull { it.segVoicedMs }?.coerceAtLeast(80) ?: 200

            rawSlices.forEachIndexed { sIdx, slice ->
                val durRatio = (slice.segVoicedMs.toFloat() / refMedianDurMs.toFloat()).coerceIn(0.20f, 1.80f)

                // Neutral / Unstressed Syllable (轻声): non-initial, markedly shorter AND weaker in amplitude than primary stressed syllable
                val isNeutralSyllable = effectiveSegmentCount >= 2 && sIdx >= 1 && (
                    (slice.segVoicedMs in 12..135 &&
                        slice.segVoicedMs <= maxWordSegVoicedMs * 0.48f &&
                        slice.segPeakRms <= maxWordSegPeakRms * 0.54f) ||
                        (slice.voicedFrameIndices.size <= 2 && slice.segPeakRms <= maxWordSegPeakRms * 0.60f)
                )

                // Checked / Entering Short Tone (入声 / 促声): short voiced rime with strong modal intensity & abrupt stop cutoff
                val isCheckedSyllable = !isNeutralSyllable &&
                    slice.voicedFrameIndices.isNotEmpty() &&
                    slice.segVoicedMs in 32..168 &&
                    slice.segPeakRms >= maxWordSegPeakRms * 0.45f &&
                    ((speakerMedianVoicedMs >= 195 && slice.segVoicedMs <= speakerMedianVoicedMs * 0.58f) ||
                        (slice.segVoicedMs <= 135 && slice.isAbruptCoda))

                perSyllableNeutralFlags.add(isNeutralSyllable)
                perSyllableCheckedFlags.add(isCheckedSyllable)
                perSyllableVoicedMs.add(slice.segVoicedMs)
                perSyllableDurRatios.add(durRatio)

                val ampThreshold = slice.segPeakRms * 0.21f
                val energyCoreIndices = slice.voicedFrameIndices.filter { frameRmsArr[it] >= ampThreshold }
                    .ifEmpty { slice.voicedFrameIndices }

                val rawSegVoiced = energyCoreIndices.map { framePitchHz[it] }
                val cleanedSeg = cleanAndSmoothPitchSequence(rawSegVoiced)
                val sourcePitches = when {
                    cleanedSeg.size >= 2 -> cleanedSeg
                    rawSegVoiced.isNotEmpty() -> rawSegVoiced
                    else -> listOf(smoothedVoicedPitches.lastOrNull() ?: pitchMean)
                }

                val corePitches = if (isCheckedSyllable || isNeutralSyllable) {
                    if (sourcePitches.size >= 5) sourcePitches.subList(1, sourcePitches.size) else sourcePitches
                } else if (sourcePitches.size >= 8) {
                    val headTrim = (sourcePitches.size * 0.16f).roundToInt().coerceAtLeast(2)
                    val tailTrim = (sourcePitches.size * 0.14f).roundToInt().coerceAtLeast(1)
                    if (sourcePitches.size - headTrim - tailTrim >= 3) {
                        sourcePitches.subList(headTrim, sourcePitches.size - tailTrim)
                    } else {
                        sourcePitches
                    }
                } else {
                    sourcePitches
                }

                for (i in 0 until pointsPerSeg) {
                    val pos = i.toDouble() / (pointsPerSeg - 1) * (corePitches.size - 1)
                    val idx0 = pos.toInt().coerceIn(0, corePitches.size - 1)
                    val idx1 = (idx0 + 1).coerceIn(0, corePitches.size - 1)
                    val frac = (pos - idx0).toFloat()
                    val interpHz = corePitches[idx0] * (1f - frac) + corePitches[idx1] * frac
                    val tNorm = ((log10(interpHz.toDouble()) - logMin) / logSpan).toFloat().coerceIn(0.02f, 0.98f)
                    contourPoints.add(tNorm)
                }
            }
        }

        val wordIsCheckedTone = perSyllableCheckedFlags.size == 1 && perSyllableCheckedFlags.first()
        val wordCheckedRatio = perSyllableDurRatios.firstOrNull() ?: 1.0f

        // Derive Chao 5-level tone numbers (1..5) and contour classification per syllable from real log-F0 curve
        val syllableToneNumbersList = mutableListOf<String>()
        val (chaoToneNumbers, toneCode, toneDesc, toneTrajectory) = if (
            isValidVoiced && effectiveSegmentCount > 1 && contourPoints.size >= effectiveSegmentCount * pointsPerSeg
        ) {
            val perSyllableQuads = (0 until effectiveSegmentCount).map { sIdx ->
                val slice = contourPoints.subList(sIdx * pointsPerSeg, (sIdx + 1) * pointsPerSeg)
                val sylNeutral = perSyllableNeutralFlags.getOrElse(sIdx) { false }
                val sylChecked = perSyllableCheckedFlags.getOrElse(sIdx) { false }
                if (sylNeutral) {
                    val midNorm = 0.25f * slice[1] + 0.50f * slice[2] + 0.25f * slice[3]
                    val dNeutral = ((midNorm.coerceIn(0f, 0.9999f)) * 5f).toInt().coerceIn(0, 4) + 1
                    val chaoNeutral = "·$dNeutral"
                    ToneClassificationQuad(
                        chaoNeutral,
                        S.acoustics.toneLightNeutralCode.withArgs("chao" to chaoNeutral),
                        S.acoustics.toneLightNeutralDesc,
                        S.acoustics.toneLightNeutralTrajectory.withArgs("chao" to chaoNeutral)
                    )
                } else {
                    classifyContourRobust(slice, true, isPitchCalibrated, sylChecked)
                }
            }
            perSyllableQuads.forEach { syllableToneNumbersList.add(it.first) }
            val combinedChao = syllableToneNumbersList.joinToString("-")
            val calTag = if (isPitchCalibrated) S.acoustics.calibratedTag else S.acoustics.singleWordEstimateTag
            val perSylDesc = perSyllableQuads.mapIndexed { idx, q ->
                val lbl = syllableBounds[idx].third
                val suffix = when {
                    perSyllableNeutralFlags.getOrElse(idx) { false } -> S.acoustics.neutralFlagShort
                    perSyllableCheckedFlags.getOrElse(idx) { false } -> S.acoustics.checkedFlagShort
                    else -> ""
                }
                "$lbl[${q.first}$suffix]"
            }.joinToString(" · ")
            val sylCountPrefix = when (effectiveSegmentCount) {
                2 -> S.acoustics.disyllabicSandhiTitle
                3 -> S.acoustics.trisyllabicSandhiTitle
                else -> S.acoustics.polysyllabicSandhiTitle.withArgs("count" to effectiveSegmentCount)
            }
            ToneClassificationQuad(
                combinedChao,
                "$sylCountPrefix [$combinedChao]",
                "$perSylDesc ($calTag)",
                S.acoustics.perSyllableMeasuredTrajectory.withArgs("detail" to perSylDesc)
            )
        } else {
            val singleQuad = classifyContourRobust(contourPoints, isValidVoiced, isPitchCalibrated, wordIsCheckedTone)
            if (singleQuad.first != "--") {
                syllableToneNumbersList.add(singleQuad.first)
            }
            singleQuad
        }

        // 7. Dynamic 7-Anchor LPC + Viterbi Formant Trajectory Extraction (Monophthong vs Diphthong/Triphthong)
        val speakerF1List = mutableListOf<Int>()
        val speakerF2List = mutableListOf<Int>()
        val speakerF3List = mutableListOf<Int>()
        val trajectoriesList = mutableListOf<SyllableFormantTrajectory>()

        if (isValidVoiced) {
            syllableBounds.forEachIndexed { sylIdx, (segStartSample, segEndSample, sylLabel) ->
                val traj = FormantTrajectoryAndSpectrogramExtractor.extractSyllableFormantTrajectory(
                    pcm = pcm,
                    segStart = segStartSample,
                    segEnd = segEndSample,
                    sampleRate = sampleRate,
                    syllableIndex = sylIdx,
                    syllableLabel = sylLabel,
                    syllableMeanF0 = pitchMean,
                    isHighRegister = isHighRegister,
                    isMidRegister = isMidRegister
                )
                if (traj.nucleusF1 > 0 && traj.nucleusF2 > 0) {
                    trajectoriesList.add(traj)
                    speakerF1List.add(traj.nucleusF1)
                    speakerF2List.add(traj.nucleusF2)
                    speakerF3List.add(traj.nucleusF3)
                } else {
                    val (f1Fallback, f2Fallback) = extractFormantsPraatLPC(
                        pcm = pcm,
                        segStart = segStartSample,
                        segEnd = segEndSample,
                        sampleRate = sampleRate,
                        syllableMeanF0 = pitchMean,
                        isHighRegister = isHighRegister,
                        isMidRegister = isMidRegister
                    )
                    speakerF1List.add(f1Fallback)
                    speakerF2List.add(f2Fallback)
                    speakerF3List.add(0)
                    if (f1Fallback > 0 && f2Fallback > 0) {
                        trajectoriesList.add(
                            SyllableFormantTrajectory(
                                syllableIndex = sylIdx,
                                syllableLabel = sylLabel,
                                vowelType = VowelGlideType.MONOPHTHONG,
                                anchors = emptyList(),
                                onsetF1 = f1Fallback,
                                onsetF2 = f2Fallback,
                                nucleusF1 = f1Fallback,
                                nucleusF2 = f2Fallback,
                                nucleusF3 = 0,
                                targetF1 = f1Fallback,
                                targetF2 = f2Fallback,
                                barkGlideSpan = 0f,
                                trajectoryAngleDeg = 0f,
                                trajectorySummary = S.acoustics.monophthongSteadyNucleusSummary.withArgs(
                                    "f1" to f1Fallback,
                                    "f2" to f2Fallback
                                )
                            )
                        )
                    }
                }
            }
        }

        val result = AnalysisResult(
            isValidVoiced = isValidVoiced,
            intensityDbfs = intensityDbfs,
            peakDbfs = peakDbfs,
            snrDb = snrDb,
            jitterPercent = jitterPercent,
            shimmerPercent = shimmerPercent,
            pitchMean = pitchMean,
            pitchMin = pitchMin,
            pitchMax = pitchMax,
            pitchRange = pitchRangeText,
            isPitchCalibrated = isPitchCalibrated,
            chaoToneNumbers = chaoToneNumbers,
            toneCode = toneCode,
            toneDesc = toneDesc,
            toneTrajectory = toneTrajectory,
            contourPoints = contourPoints,
            rawVoicedPitches = smoothedVoicedPitches,
            speakerF1 = speakerF1List,
            speakerF2 = speakerF2List,
            segmentCount = effectiveSegmentCount,
            segments = segmentsList,
            creakyBreaks = creakyBreaks,
            voicedDurationMs = voicedDurationMs,
            totalDurationMs = totalDurationMs,
            syllableToneNumbers = syllableToneNumbersList,
            speakerF3 = speakerF3List,
            formantTrajectories = trajectoriesList,
            isCheckedTone = wordIsCheckedTone,
            checkedToneRatio = wordCheckedRatio,
            syllableCheckedFlags = perSyllableCheckedFlags,
            syllableNeutralFlags = perSyllableNeutralFlags,
            syllableVoicedMs = perSyllableVoicedMs
        )

        analysisCache[key] = result
        return result
    }

    fun clearCache() {
        analysisCache.clear()
        envelopeCache.clear()
        quickPitchCache.clear()
        autoPitchCache.clear()
        FormantTrajectoryAndSpectrogramExtractor.clearCache()
        CorpusPhonologyAnalyzer.clearCache()
    }

    /**
     * Removes isolated octave jumps and smooths a voiced F0 sequence using a 5-point median
     * filter followed by a 3-point moving average.
     */
    private fun cleanAndSmoothPitchSequence(raw: List<Float>): List<Float> {
        if (raw.size < 3) return raw.toList()
        val sorted = raw.sorted()
        val globalMed = sorted[sorted.size / 2]

        // Step 1: Correct octave-halving/doubling outliers relative to global median
        val deOctaved = raw.map { f0 ->
            when {
                f0 > globalMed * 1.68f -> f0 * 0.5f
                f0 < globalMed * 0.56f -> f0 * 2.0f
                else -> f0
            }
        }

        // Step 2: 5-point median filter to remove consonant release spikes
        val medFiltered = deOctaved.indices.map { i ->
            val window = (-2..2).map { d ->
                deOctaved[(i + d).coerceIn(0, deOctaved.size - 1)]
            }.sorted()
            window[2]
        }

        // Step 3: 3-point weighted moving average (0.25, 0.50, 0.25) for smooth tone contour
        return medFiltered.indices.map { i ->
            val prev = medFiltered[(i - 1).coerceAtLeast(0)]
            val curr = medFiltered[i]
            val next = medFiltered[(i + 1).coerceAtMost(medFiltered.size - 1)]
            0.25f * prev + 0.50f * curr + 0.25f * next
        }
    }

    /**
     * Multi-feature vowel-core Creaky Voice (Vocal Fry) detector:
     * - Restricts analysis to the steady-state interior [20%..76%] of the voiced syllable
     *   (never penalizes initial consonant transitions or natural low-tone tail drop on 214/51).
     * - Requires sufficient voiced duration (>= 14 frames, ~160ms) and active vowel amplitude.
     * - Identifies a frame as creaky only when harmonic periodicity is degraded OR subharmonic
     *   diplophonia is strong AND frame-to-frame pitch perturbation is irregular.
     * - Counts a creaky episode only when >= 4 consecutive vowel-core frames (~46ms) are creaky.
     */
    private fun detectSustainedVowelCoreCreak(
        frameIsVoicedSeq: BooleanArray,
        framePitchHz: FloatArray,
        frameMaxAcf: FloatArray,
        frameRmsArr: FloatArray,
        frameSubharmonicRatio: FloatArray,
        maxVoicedFrameRms: Float,
        medianF0: Float,
        jitterPercent: Float,
        shimmerPercent: Float
    ): Int {
        val firstVoiced = frameIsVoicedSeq.indexOfFirst { it }
        val lastVoiced = frameIsVoicedSeq.indexOfLast { it }
        if (firstVoiced == -1 || lastVoiced - firstVoiced < 14 || medianF0 <= 50f) return 0

        // If overall glottal pulse jitter and shimmer are well within normal modal voice bounds,
        // it is physically modal speech, not creaky voice.
        if (jitterPercent < 3.2f && shimmerPercent < 9.5f) return 0

        val span = lastVoiced - firstVoiced
        val coreStart = firstVoiced + (span * 0.20f).toInt()
        val coreEnd = firstVoiced + (span * 0.76f).toInt()
        if (coreEnd - coreStart < 6) return 0

        val minActiveRms = maxVoicedFrameRms * 0.32f
        var consecutiveCreakyFrames = 0
        var sustainedCreakyEpisodes = 0

        for (f in coreStart..coreEnd) {
            val rms = frameRmsArr[f]
            if (rms < minActiveRms || !frameIsVoicedSeq[f]) {
                if (consecutiveCreakyFrames >= 4) {
                    sustainedCreakyEpisodes++
                }
                consecutiveCreakyFrames = 0
                continue
            }

            val f0 = framePitchHz[f]
            val prevF0 = if (f > firstVoiced && frameIsVoicedSeq[f - 1]) framePitchHz[f - 1] else f0
            val relJump = abs(f0 - prevF0) / maxOf(f0, prevF0, 1f)
            val acfPeak = frameMaxAcf[f]
            val subHarm = frameSubharmonicRatio[f]

            // True creaky phonation in active vowel core:
            // 1. Degraded periodicity (acfPeak in 0.48..0.63) with irregular cycle-to-cycle F0 or severe F0 drop
            // 2. Or strong subharmonic diplophonia (T0/2T0 alternation) with low F0
            val isRoughAcf = acfPeak in 0.48f..0.62f && (relJump in 0.10f..0.45f || f0 < medianF0 * 0.72f)
            val isSubharmonicFry = subHarm > 0.96f && f0 < medianF0 * 0.76f && relJump > 0.06f

            if (isRoughAcf || isSubharmonicFry) {
                consecutiveCreakyFrames++
            } else {
                if (consecutiveCreakyFrames >= 4) {
                    sustainedCreakyEpisodes++
                }
                consecutiveCreakyFrames = 0
            }
        }
        if (consecutiveCreakyFrames >= 4) {
            sustainedCreakyEpisodes++
        }

        return sustainedCreakyEpisodes
    }

    /**
     * Automatically derives a speaker's personal pitch range (P08..P92 on core vowel frames,
     * rejecting consonant onset spikes and <72Hz subharmonic creaky tails) and median voiced
     * syllable duration across recorded WAV files in the project directory.
     */
    /**
     * Lightweight F0 + voiced syllable duration extractor used exclusively by [resolveSpeakerCorpusStats].
     * Skips anti-aliased LPC decimation, Durand-Kerner complex polynomial root-finding, formant trajectories,
     * Jitter/Shimmer, and creaky detection (~8x faster per file than full [analyzeByGender]), and caches
     * per-file summaries so recording 1 new word in a 200-word project only scans that 1 new WAV file!
     */
    private fun extractQuickPitchSummary(file: File, speakerGender: String?): QuickPitchSummary? {
        if (!file.exists() || file.length() <= 1024L) return null
        val normGender = speakerGender?.trim() ?: ""
        val qKey = "${file.absolutePath}_${file.lastModified()}_$normGender"
        quickPitchCache[qKey]?.let { return it }

        val pcm = loadWavPcm(file) ?: return null
        if (pcm.size < 1024) return null
        val sampleRate = readWavHeaderFormat(file).sampleRate

        val blockSize = 1024
        val blockCount = (pcm.size / blockSize).coerceAtLeast(1)
        val blockRms = FloatArray(blockCount)
        var maxBlockRms = 0f
        for (b in 0 until blockCount) {
            var sumSquare = 0.0
            val offset = b * blockSize
            val endIdx = (offset + blockSize).coerceAtMost(pcm.size)
            for (i in offset until endIdx) {
                val v = pcm[i]
                sumSquare += v * v
            }
            val rms = sqrt(sumSquare / (endIdx - offset).coerceAtLeast(1)).toFloat()
            blockRms[b] = rms
            if (rms > maxBlockRms) maxBlockRms = rms
        }

        val physicalBlocks = blockRms.filter { it > 1.5e-4f }.ifEmpty { blockRms.toList() }
        val sortedRms = physicalBlocks.sorted()
        val noiseFloorEstimate = sortedRms.take((sortedRms.size * 0.2).toInt().coerceAtLeast(1)).average().toFloat()
        val vadThreshold = maxOf(noiseFloorEstimate * 2.5f, maxBlockRms * 0.10f, 0.003f)

        val firstSpeechBlock = blockRms.indexOfFirst { it >= vadThreshold }.takeIf { it >= 0 } ?: 0
        val lastSpeechBlock = blockRms.indexOfLast { it >= vadThreshold }.takeIf { it >= firstSpeechBlock } ?: (blockCount - 1)
        val speechStartSample = (firstSpeechBlock * blockSize).coerceAtLeast(0)
        val speechEndSample = ((lastSpeechBlock + 1) * blockSize).coerceAtMost(pcm.size)

        val searchMinHz = when (normGender) {
            "女" -> 115f
            "男" -> 70f
            else -> 75f
        }
        val searchMaxHz = when (normGender) {
            "女" -> 450f
            "男" -> 310f
            else -> 460f
        }
        val minLag = (sampleRate / searchMaxHz).toInt().coerceAtLeast(45)
        val maxLag = (sampleRate / searchMinHz).toInt().coerceAtMost(880)

        val frameSize = 2048
        val hopSize = 512
        val totalFrames = if (pcm.size >= frameSize) (pcm.size - frameSize) / hopSize + 1 else 0
        val frameIsVoicedSeq = BooleanArray(totalFrames)
        val rawVoicedList = ArrayList<Float>(totalFrames.coerceAtLeast(8))
        val windowLen = 1024
        val acf = FloatArray(maxLag + 2)

        for (f in 0 until totalFrames) {
            val offset = f * hopSize
            var sumSq = 0.0
            for (i in 0 until frameSize) {
                val v = pcm[offset + i]
                sumSq += v * v
            }
            val fRms = sqrt(sumSq / frameSize).toFloat()
            if (fRms < vadThreshold * 0.75f) continue

            var zcr = 0
            for (i in 0 until frameSize - 1) {
                if ((pcm[offset + i] >= 0f) != (pcm[offset + i + 1] >= 0f)) zcr++
            }
            if (zcr.toFloat() / frameSize > 0.28f) continue

            var dSumLeft = 0.0
            for (i in 0 until windowLen) {
                val x = pcm[offset + i].toDouble()
                dSumLeft += x * x
            }
            if (dSumLeft <= 1e-7) continue

            var dSumRight = 0.0
            for (i in 0 until windowLen) {
                val y = pcm[offset + i + minLag].toDouble()
                dSumRight += y * y
            }

            acf[minLag - 1] = 0f
            acf[maxLag + 1] = 0f
            for (lag in minLag..maxLag) {
                if (lag > minLag) {
                    val oldY = pcm[offset + lag - 1].toDouble()
                    val newY = pcm[offset + lag + windowLen - 1].toDouble()
                    dSumRight = (dSumRight - oldY * oldY + newY * newY).coerceAtLeast(0.0)
                }
                var rSum = 0.0
                for (i in 0 until windowLen) {
                    rSum += pcm[offset + i] * pcm[offset + i + lag]
                }
                val denom = sqrt(dSumLeft * dSumRight)
                acf[lag] = if (denom > 1e-7) (rSum / denom).toFloat() else 0f
            }

            var searchStartLag = minLag + 1
            while (searchStartLag < maxLag - 2 && acf[searchStartLag] > acf[searchStartLag + 1] && acf[searchStartLag] > 0.25f) {
                searchStartLag++
            }

            var bestLag = -1
            var bestWeightedScore = -1f
            var rawPeakAtBest = 0f
            for (lag in searchStartLag until maxLag) {
                val c = acf[lag]
                if (c > 0.48f && c >= acf[lag - 1] && c >= acf[lag + 1]) {
                    val octavePenalty = 0.10f * (lag - minLag).toFloat() / (maxLag - minLag).coerceAtLeast(1)
                    val weightedScore = c - octavePenalty
                    if (weightedScore > bestWeightedScore) {
                        bestWeightedScore = weightedScore
                        bestLag = lag
                        rawPeakAtBest = c
                    }
                }
            }

            if (bestLag > minLag * 2) {
                val halfLag = bestLag / 2
                if (halfLag >= searchStartLag) {
                    val cHalf = maxOf(
                        acf[(halfLag - 1).coerceAtLeast(minLag)],
                        acf[halfLag],
                        acf[(halfLag + 1).coerceAtMost(maxLag)]
                    )
                    if (cHalf >= 0.55f && cHalf >= rawPeakAtBest * 0.82f) {
                        for (candidate in (halfLag - 2).coerceAtLeast(searchStartLag)..(halfLag + 2).coerceAtMost(maxLag - 1)) {
                            if (acf[candidate] >= acf[candidate - 1] && acf[candidate] >= acf[candidate + 1]) {
                                bestLag = candidate
                                rawPeakAtBest = acf[candidate]
                                break
                            }
                        }
                    }
                }
            }

            if (bestLag != -1 && rawPeakAtBest > 0.48f) {
                val y1 = acf[bestLag - 1]
                val y2 = acf[bestLag]
                val y3 = acf[bestLag + 1]
                val denom = 2f * (2f * y2 - y1 - y3)
                val delta = if (abs(denom) > 1e-5f) ((y1 - y3) / denom).coerceIn(-0.5f, 0.5f) else 0f
                val f0 = sampleRate / (bestLag + delta)
                if (f0 in searchMinHz..searchMaxHz) {
                    frameIsVoicedSeq[f] = true
                    rawVoicedList.add(f0)
                }
            }
        }

        val smoothed = cleanAndSmoothPitchSequence(rawVoicedList)
        val isValid = smoothed.size >= 3
        if (!isValid) {
            val invalidSummary = QuickPitchSummary(false, 0, FloatArray(0))
            quickPitchCache[qKey] = invalidSummary
            return invalidSummary
        }

        val voicedDurationMs = ((smoothed.size * hopSize) * 1000L / sampleRate).toInt()
        val syllableBounds = detectAndAlignSyllableSegments(
            pcm = pcm,
            startSample = speechStartSample,
            endSample = speechEndSample,
            expectedCountHint = 0,
            sampleRate = sampleRate,
            labels = emptyList(),
            frameIsVoicedSeq = frameIsVoicedSeq,
            hopSize = hopSize,
            hasErhuaSuffix = false
        )
        val perSylDur = voicedDurationMs / syllableBounds.size.coerceAtLeast(1)

        val sIdx = (smoothed.size * 0.20f).toInt().coerceIn(0, smoothed.size - 1)
        val eIdx = (smoothed.size * 0.78f).toInt().coerceIn(sIdx, smoothed.size - 1)
        val interiorList = ArrayList<Float>((eIdx - sIdx + 1).coerceAtLeast(1))
        for (idx in sIdx..eIdx) {
            val hz = smoothed[idx]
            if (hz in 72f..480f) {
                interiorList.add(hz)
            }
        }
        val summary = QuickPitchSummary(true, perSylDur, interiorList.toFloatArray())
        quickPitchCache[qKey] = summary
        return summary
    }

    /**
     * Automatically derives a speaker's personal pitch range (P08..P92 on core vowel frames,
     * rejecting consonant onset spikes and <72Hz subharmonic creaky tails) and median voiced
     * syllable duration across recorded WAV files in the project directory.
     */
    fun resolveSpeakerCorpusStats(
        projDir: File,
        speakerId: Long,
        speakerGender: String?,
        manualRange: Pair<Float, Float>?
    ): SpeakerCorpusAcousticStats? {
        if (!projDir.exists()) {
            return manualRange?.let { SpeakerCorpusAcousticStats(0, 0L, it, 280) }
        }

        val speakerFiles = if (speakerId > 0L) {
            projDir.listFiles { _, name ->
                name.endsWith(".wav") && name.contains("_speaker_${speakerId}_")
            }
        } else {
            projDir.listFiles { _, name -> name.endsWith(".wav") }
        } ?: return manualRange?.let { SpeakerCorpusAcousticStats(0, 0L, it, 280) }

        if (speakerFiles.size < 3) {
            return manualRange?.let { SpeakerCorpusAcousticStats(speakerFiles.size, 0L, it, 280) }
        }

        // Fast O(N) filename timestamp extraction before falling back to stat() if needed
        var latestMod = 0L
        for (f in speakerFiles) {
            val tsStr = f.name.substringAfterLast('_').removeSuffix(".wav")
            val parsedTs = tsStr.toLongOrNull() ?: f.lastModified()
            if (parsedTs > latestMod) latestMod = parsedTs
        }

        val cacheKey = "${projDir.absolutePath}_${speakerId}_${manualRange?.first?.toInt() ?: -1}_${manualRange?.second?.toInt() ?: -1}"
        autoPitchCache[cacheKey]?.let { cached ->
            if (cached.fileCount == speakerFiles.size && cached.latestModified == latestMod) {
                return cached
            }
        }

        val lock = corpusStatsLocks.getOrPut(cacheKey) { Any() }
        synchronized(lock) {
            autoPitchCache[cacheKey]?.let { cached ->
                if (cached.fileCount == speakerFiles.size && cached.latestModified == latestMod) {
                    return cached
                }
            }

            val corePitches = ArrayList<Float>(speakerFiles.size * 16)
            val syllableVoicedDurs = ArrayList<Int>(speakerFiles.size)

            // Deduplicate by word prefix (pick latest take per word by filename timestamp) up to 120 tokens
            val latestPerWord = speakerFiles
                .groupBy { it.name.substringBeforeLast('_') }
                .values
                .mapNotNull { list ->
                    list.maxByOrNull { f ->
                        f.name.substringAfterLast('_').removeSuffix(".wav").toLongOrNull() ?: f.lastModified()
                    }
                }
                .take(120)

            for (f in latestPerWord) {
                val quick = extractQuickPitchSummary(f, speakerGender)
                if (quick != null && quick.isValidVoiced) {
                    if (quick.perSyllableVoicedMs in 35..1000) {
                        syllableVoicedDurs.add(quick.perSyllableVoicedMs)
                    }
                    for (hz in quick.interiorCorePitches) {
                        corePitches.add(hz)
                    }
                }
            }

            val medianDur = if (syllableVoicedDurs.size >= 3) {
                val sortedDurs = syllableVoicedDurs.sorted()
                sortedDurs[(sortedDurs.size * 0.60).toInt().coerceIn(0, sortedDurs.size - 1)]
            } else {
                280
            }

            if (manualRange != null) {
                val entry = SpeakerCorpusAcousticStats(speakerFiles.size, latestMod, manualRange, medianDur)
                autoPitchCache[cacheKey] = entry
                return entry
            }

            if (corePitches.size < 10) return null

            val sorted = corePitches.sorted()
            var pLow = sorted[(sorted.size * 0.08).toInt().coerceIn(0, sorted.size - 1)].coerceAtLeast(62f)
            var pHigh = sorted[(sorted.size * 0.92).toInt().coerceIn(0, sorted.size - 1)].coerceAtLeast(pLow + 25f)

            val logSpan = log10(pHigh.toDouble() / pLow.toDouble())
            if (logSpan < 0.168) {
                val geoMean = sqrt((pLow * pHigh).toDouble()).toFloat()
                val normG = speakerGender?.trim() ?: ""
                val regCenter = when {
                    normG == "女" || geoMean >= 175f -> 205f
                    normG == "男" || geoMean <= 145f -> 130f
                    else -> 165f
                }
                val anchoredCenter = geoMean * 0.65f + regCenter * 0.35f
                val halfSpanFactor = 10.0.pow(0.168 / 2.0).toFloat()
                pLow = minOf(pLow, anchoredCenter / halfSpanFactor).coerceAtLeast(60f)
                pHigh = maxOf(pHigh, anchoredCenter * halfSpanFactor).coerceAtLeast(pLow + 38f)
            }

            val entry = SpeakerCorpusAcousticStats(speakerFiles.size, latestMod, Pair(pLow, pHigh), medianDur)
            autoPitchCache[cacheKey] = entry
            return entry
        }
    }

    /**
     * Unified helper for UI screens to analyze a Word without any fake fallback values.
     */
    fun analyzeWordPhonetics(context: Context, word: Word, speakerGender: String?): PhoneticAcousticResult {
        val parts = extractSyllableUnits(word.text)
        val segmentCount = parts.size.coerceAtLeast(1)

        val sp = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
        val speakerId = word.recordingFile?.split("_speaker_")?.getOrNull(1)?.split("_")?.firstOrNull()?.toLongOrNull() ?: -1L
        val pMin = sp.getFloat("pitch_min_$speakerId", -1f)
        val pMax = sp.getFloat("pitch_max_$speakerId", -1f)
        val manualPitchRange = if (pMin > 40f && pMax > pMin + 15f) Pair(pMin, pMax) else null

        val recordingFile = word.recordingFile
        var audioFile: File? = null
        var realAnalysis: AnalysisResult? = null

        if (!recordingFile.isNullOrEmpty()) {
            val projDir = File(context.getExternalFilesDir(null), "project_${word.projectId}")
            val file = File(projDir, recordingFile)
            if (file.exists() && file.length() > 44L) {
                audioFile = file
                val corpusStats = resolveSpeakerCorpusStats(projDir, speakerId, speakerGender, manualPitchRange)
                realAnalysis = analyzeByGender(
                    file = file,
                    speakerGender = speakerGender,
                    segmentCount = segmentCount,
                    pitchRange = corpusStats?.pitchRange ?: manualPitchRange,
                    segmentLabels = parts,
                    speakerMedianVoicedMs = corpusStats?.medianVoicedDurationMs ?: -1
                )
            }
        }

        val filePath = "project_${word.projectId}/${word.recordingFile ?: S.archive.unrecordedPlaceholder}"
        val fileSizeKb = if (audioFile != null && audioFile.exists()) {
            audioFile.length().toDouble() / 1024.0
        } else {
            0.0
        }

        if (realAnalysis != null) {
            // Check if a cached speaker phonology report already has the corpus-level canonical category for this word
            val cachedCanonical = CorpusPhonologyAnalyzer.lookupCachedCanonicalMember(word.projectId, speakerId, word.id)
            return PhoneticAcousticResult(
                isValidVoiced = realAnalysis.isValidVoiced,
                pitchMean = realAnalysis.pitchMean,
                pitchMin = realAnalysis.pitchMin,
                pitchMax = realAnalysis.pitchMax,
                pitchRange = realAnalysis.pitchRange,
                isPitchCalibrated = realAnalysis.isPitchCalibrated,
                chaoToneNumbers = realAnalysis.chaoToneNumbers,
                toneCode = realAnalysis.toneCode,
                toneDesc = realAnalysis.toneDesc,
                toneTrajectory = realAnalysis.toneTrajectory,
                speakerF1 = realAnalysis.speakerF1,
                speakerF2 = realAnalysis.speakerF2,
                segmentCount = realAnalysis.segmentCount,
                segments = realAnalysis.segments,
                intensityDbfs = realAnalysis.intensityDbfs,
                peakDbfs = realAnalysis.peakDbfs,
                filePath = filePath,
                fileSizeKb = fileSizeKb,
                contourPoints = realAnalysis.contourPoints,
                snrDb = realAnalysis.snrDb,
                jitterPercent = realAnalysis.jitterPercent,
                shimmerPercent = realAnalysis.shimmerPercent,
                syllableToneNumbers = realAnalysis.syllableToneNumbers,
                speakerF3 = realAnalysis.speakerF3,
                formantTrajectories = realAnalysis.formantTrajectories,
                voicedDurationMs = realAnalysis.voicedDurationMs,
                isCheckedTone = realAnalysis.isCheckedTone,
                checkedToneRatio = realAnalysis.checkedToneRatio,
                canonicalToneNumbers = cachedCanonical?.canonicalChao ?: "",
                canonicalToneName = cachedCanonical?.canonicalCategoryName ?: "",
                isToneOutlier = cachedCanonical?.isOutlier ?: false,
                syllableCheckedFlags = realAnalysis.syllableCheckedFlags,
                syllableNeutralFlags = realAnalysis.syllableNeutralFlags,
                syllableVoicedMs = realAnalysis.syllableVoicedMs
            )
        }

        // Honest empty state when no audio file or no voiced frames exist (zero fabricated data)
        return PhoneticAcousticResult(
            isValidVoiced = false,
            pitchMean = 0f,
            pitchMin = 0f,
            pitchMax = 0f,
            pitchRange = S.acoustics.pitchUnavailable,
            isPitchCalibrated = false,
            chaoToneNumbers = "--",
            toneCode = S.acoustics.toneLowOrNoPitchCode,
            toneDesc = S.acoustics.toneLowOrLightDesc,
            toneTrajectory = S.acoustics.toneLowOrLightTrajectory,
            speakerF1 = emptyList(),
            speakerF2 = emptyList(),
            segmentCount = segmentCount,
            segments = emptyList(),
            intensityDbfs = -90f,
            peakDbfs = -90f,
            filePath = filePath,
            fileSizeKb = fileSizeKb,
            contourPoints = emptyList(),
            snrDb = 0f,
            jitterPercent = 0f,
            shimmerPercent = 0f
        )
    }

    data class ToneClassificationQuad(
        val chaoDigits: String,
        val toneCode: String,
        val shortDesc: String,
        val trajectoryText: String
    ) {
        val first: String get() = chaoDigits
        val second: String get() = toneCode
        val third: String get() = shortDesc
        val fourth: String get() = trajectoryText
        operator fun component5(): Boolean = false
    }

    /**
     * Statistically robust Chao 5-level tone classifier:
     * - Compensates natural physiological F0 declination (~0.18 Chao unit gentle tilt) so genuine
     *   level tones ([55], [44], [33], [22], [11]) are NOT misclassified as falling or dipping tones.
     * - Requires linear trend R² significance (>= 0.52) and >= 0.74 Chao-step excursion before
     *   rejecting the Level Tone null hypothesis.
     * - Recognizes Checked / Entering Short Tones (入声 / 促声短调: [5], [3], [21] 促).
     */
    fun classifyContourRobust(
        contourPoints: List<Float>,
        isValidVoiced: Boolean,
        isCalibrated: Boolean,
        isCheckedTone: Boolean = false
    ): ToneClassificationQuad {
        if (!isValidVoiced || contourPoints.size < 5) {
            return ToneClassificationQuad(
                "--",
                S.acoustics.toneLowOrNoPitchCode,
                S.acoustics.toneQuietOrShortDesc,
                S.acoustics.toneQuietOrShortTrajectory
            )
        }

        // Shi Feng (石锋) 5-equal-interval T-value mapping: [0, 0.2)->1, [0.2, 0.4)->2, [0.4, 0.6)->3, [0.6, 0.8)->4, [0.8, 1.0]->5
        fun toChaoDigit(norm: Float): Int = ((norm.coerceIn(0f, 0.9999f)) * 5f).toInt().coerceIn(0, 4) + 1

        val p0 = contourPoints[0]
        val p1 = contourPoints[1]
        val p2 = contourPoints[2]
        val p3 = contourPoints[3]
        val p4 = contourPoints[4]

        val calTag = if (isCalibrated) S.acoustics.calibratedTag else S.acoustics.singleWordEstimateTag

        // 1. Checked / Entering Short Tone (入声 / 促声短调)
        if (isCheckedTone) {
            val netDiff = p4 - p0
            val dStart = toChaoDigit(0.65f * p0 + 0.35f * p1)
            val dEnd = toChaoDigit(0.35f * p3 + 0.65f * p4)
            return when {
                netDiff <= -0.20f -> {
                    val fStart = maxOf(dStart, dEnd + 1).coerceAtMost(5)
                    val fEnd = minOf(dEnd, fStart - 1).coerceAtLeast(1)
                    val chaoStr = "$fStart$fEnd"
                    ToneClassificationQuad(
                        chaoStr,
                        S.acoustics.toneCheckedFallCode.withArgs("chao" to chaoStr),
                        S.acoustics.toneCheckedFallDesc.withArgs("calTag" to calTag),
                        S.acoustics.toneCheckedFallTrajectory.withArgs("chao" to chaoStr)
                    )
                }
                netDiff >= 0.20f -> {
                    val rStart = minOf(dStart, dEnd - 1).coerceAtLeast(1)
                    val rEnd = maxOf(dEnd, rStart + 1).coerceAtMost(5)
                    val chaoStr = "$rStart$rEnd"
                    ToneClassificationQuad(
                        chaoStr,
                        S.acoustics.toneCheckedRiseCode.withArgs("chao" to chaoStr),
                        S.acoustics.toneCheckedRiseDesc.withArgs("calTag" to calTag),
                        S.acoustics.toneCheckedRiseTrajectory.withArgs("chao" to chaoStr)
                    )
                }
                else -> {
                    val coreMean = (0.25f * p1 + 0.50f * p2 + 0.25f * p3)
                    val dShort = toChaoDigit(coreMean)
                    val chaoStr = "$dShort"
                    ToneClassificationQuad(
                        chaoStr,
                        S.acoustics.toneCheckedShortCode.withArgs("chao" to chaoStr),
                        S.acoustics.toneCheckedShortDesc.withArgs("calTag" to calTag),
                        S.acoustics.toneCheckedShortTrajectory.withArgs("chao" to chaoStr)
                    )
                }
            }
        }

        // 2. Smooth / Long Tone (舒声): Linear regression R² & physiological declination compensation
        val pts = floatArrayOf(p0, p1, p2, p3, p4)
        val meanY = pts.average().toFloat()
        // x_i in [-2, -1, 0, 1, 2], sum(x_i^2) = 10
        val slope = ((-2f * p0) + (-1f * p1) + (1f * p3) + (2f * p4)) / 10f
        var ssTot = 0f
        var ssRes = 0f
        for (i in 0..4) {
            val x = (i - 2).toFloat()
            val yHat = meanY + slope * x
            val dy = pts[i] - meanY
            val res = pts[i] - yHat
            ssTot += dy * dy
            ssRes += res * res
        }
        val linearR2 = if (ssTot > 1e-5f) (1f - ssRes / ssTot).coerceIn(0f, 1f) else 0f

        val minInternal = minOf(p1, p2, p3)
        val maxInternal = maxOf(p1, p2, p3)

        // Use smoothed endpoint estimates so single-point residual edge jitter cannot flip digits
        val effStart = 0.70f * p0 + 0.30f * p1
        val effEnd = 0.30f * p3 + 0.70f * p4
        val rawDiff = effEnd - effStart

        // Natural speech has ~0.045 normalized units (~0.18 Chao step) of subglottal declination
        val declinationCompFall = (effStart - effEnd) - 0.045f

        val dipDepth = minOf(effStart, effEnd) - minInternal
        val peakHeight = maxInternal - maxOf(effStart, effEnd)

        val dStart = toChaoDigit(effStart)
        val dEnd = toChaoDigit(effEnd)

        return when {
            // Dipping tone (e.g. 214, 313, 424): requires genuine >= 0.70 Chao-step valley & bilateral rise
            dipDepth >= 0.175f && (effStart - minInternal) >= 0.14f && (effEnd - minInternal) >= 0.15f -> {
                val dLow = minOf(toChaoDigit(minInternal), minOf(dStart, dEnd) - 1).coerceAtLeast(1)
                val sDig = maxOf(dStart, dLow + 1).coerceAtMost(5)
                val eDig = maxOf(dEnd, dLow + 1).coerceAtMost(5)
                val chaoStr = "$sDig$dLow$eDig"
                ToneClassificationQuad(
                    chaoStr,
                    S.acoustics.toneFallRiseCode.withArgs("chao" to chaoStr),
                    S.acoustics.toneFallRiseDesc.withArgs("calTag" to calTag),
                    S.acoustics.toneFallRiseTrajectory.withArgs("chao" to chaoStr)
                )
            }
            // Peaking / Convex tone (e.g. 242, 353): requires genuine >= 0.72 Chao-step peak & bilateral fall
            peakHeight >= 0.180f && (maxInternal - effStart) >= 0.15f && (maxInternal - effEnd) >= 0.15f -> {
                val dHigh = maxOf(toChaoDigit(maxInternal), maxOf(dStart, dEnd) + 1).coerceAtMost(5)
                val sDig = minOf(dStart, dHigh - 1).coerceAtLeast(1)
                val eDig = minOf(dEnd, dHigh - 1).coerceAtLeast(1)
                val chaoStr = "$sDig$dHigh$eDig"
                ToneClassificationQuad(
                    chaoStr,
                    S.acoustics.toneRiseFallCode.withArgs("chao" to chaoStr),
                    S.acoustics.toneRiseFallDesc.withArgs("calTag" to calTag),
                    S.acoustics.toneRiseFallTrajectory.withArgs("chao" to chaoStr)
                )
            }
            // Rising tone (e.g. 35, 24, 13): requires >= 0.74 Chao-step rise and monotonic linear fit
            rawDiff >= 0.185f && linearR2 >= 0.52f -> {
                val (rStart, rEnd) = if (dStart < dEnd) {
                    dStart to dEnd
                } else {
                    val s = minOf(dStart, 4)
                    s to (s + 1).coerceAtMost(5)
                }
                val chaoStr = "$rStart$rEnd"
                ToneClassificationQuad(
                    chaoStr,
                    S.acoustics.toneRisingCode.withArgs("chao" to chaoStr),
                    S.acoustics.toneRisingDesc.withArgs("calTag" to calTag),
                    S.acoustics.toneRisingTrajectory.withArgs("chao" to chaoStr)
                )
            }
            // Falling tone (e.g. 51, 53, 42, 31): requires >= 0.74 Chao-step fall AFTER physiological declination compensation
            declinationCompFall >= 0.185f && linearR2 >= 0.55f -> {
                val (fStart, fEnd) = if (dStart > dEnd) {
                    dStart to dEnd
                } else {
                    val s = maxOf(dStart, 2)
                    s to (s - 1).coerceAtLeast(1)
                }
                val chaoStr = "$fStart$fEnd"
                ToneClassificationQuad(
                    chaoStr,
                    S.acoustics.toneFallingCode.withArgs("chao" to chaoStr),
                    S.acoustics.toneFallingDesc.withArgs("calTag" to calTag),
                    S.acoustics.toneFallingTrajectory.withArgs("chao" to chaoStr)
                )
            }
            // Level tone (55, 44, 33, 22, 11): steady core average with +0.02f declination recovery
            else -> {
                val steadyNorm = (0.18f * p0 + 0.26f * p1 + 0.30f * p2 + 0.20f * p3 + 0.06f * p4) + 0.020f
                val dLevel = toChaoDigit(steadyNorm)
                val chaoStr = "$dLevel$dLevel"
                ToneClassificationQuad(
                    chaoStr,
                    S.acoustics.toneLevelCode.withArgs("chao" to chaoStr),
                    S.acoustics.toneLevelDesc.withArgs("calTag" to calTag),
                    S.acoustics.toneLevelTrajectory.withArgs("chao" to chaoStr)
                )
            }
        }
    }

    private val ERHUA_NON_FUSING_PREFIXES = setOf("女", "童", "婴", "嬰", "幼", "孤", "胎", "宠", "寵", "健", "少")
    private val PAREN_GLOSS_REGEX = Regex("(\\([^)]*\\)|（[^）]*）|\\[[^\\]]*\\]|【[^】]*】|\\{[^}]*\\})")
    private val LEADING_INDEX_REGEX = Regex("^\\d+[.、:：\\-)）\\s]+(?=\\S)")
    private val EXPLICIT_SYLLABLE_SPLIT_REGEX = Regex("[/|｜·•]+")
    private val LATIN_VOWEL_REGEX = Regex("[aeiouyvüɑɛəɔɪʊɯɤɨɐɒæøœāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ]+", RegexOption.IGNORE_CASE)
    private val PINYIN_TONE_SYL_REGEX = Regex("[a-zA-ZüÜvV]+[1-5]")

    /**
     * Parses orthographic text into clean syllable unit labels without blindly treating punctuation,
     * parenthetical glosses (e.g. `日头(白读)`), numbering (`01. 葡萄`), or alphabetic words (`apple`, `putao`)
     * as 1-character-per-syllable.
     */
    fun extractSyllableUnits(rawText: String): List<String> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return listOf(S.common.syllableFallback.withArgs("index" to 1))

        // Strip parenthetical annotations unless the entire label was enclosed in brackets
        val strippedGloss = PAREN_GLOSS_REGEX.replace(trimmed, " ").trim()
        val baseText = (if (strippedGloss.isNotEmpty()) strippedGloss else trimmed.trim('(', ')', '（', '）', '[', ']', '【', '】'))
            .let { LEADING_INDEX_REGEX.replace(it, "").trim() }
            .ifEmpty { trimmed }

        if (EXPLICIT_SYLLABLE_SPLIT_REGEX.containsMatchIn(baseText)) {
            val explicitParts = baseText.split(EXPLICIT_SYLLABLE_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (explicitParts.isNotEmpty()) return explicitParts
        }

        val units = mutableListOf<String>()
        val alphaBuffer = StringBuilder()

        fun flushAlphaBuffer() {
            if (alphaBuffer.isEmpty()) return
            val wordChunk = alphaBuffer.toString()
            alphaBuffer.clear()
            units.addAll(splitAlphabeticOrPinyinIntoSyllables(wordChunk))
        }

        for (ch in baseText) {
            when {
                Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.HAN -> {
                    flushAlphaBuffer()
                    units.add(ch.toString())
                }
                ch.isLetterOrDigit() || ch in "ɑɛəɔɪʊɯɤɨɐɒæøœüʰŋɲɕʑʂʐʈɖʔ" -> {
                    alphaBuffer.append(ch)
                }
                else -> {
                    flushAlphaBuffer()
                }
            }
        }
        flushAlphaBuffer()

        return units.ifEmpty { listOf(trimmed.take(8).ifBlank { S.common.syllableFallback.withArgs("index" to 1) }) }
    }

    private fun splitAlphabeticOrPinyinIntoSyllables(token: String): List<String> {
        val clean = token.trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.all { it.isDigit() }) return listOf(clean)

        // 1. Pinyin / Jyutping with explicit tone digits (e.g. "ma1ma2" or "pu2tao5")
        val toneMatches = PINYIN_TONE_SYL_REGEX.findAll(clean).map { it.value }.toList()
        if (toneMatches.size >= 2 && toneMatches.sumOf { it.length } >= clean.length - 1) {
            return toneMatches
        }

        // 2. Count vowel-cluster nuclei for alphabetic / IPA words (e.g. "apple" -> 2, "banana" -> 3, "cake" -> 1)
        val vowelMatches = LATIN_VOWEL_REGEX.findAll(clean).toList()
        if (vowelMatches.size <= 1) return listOf(clean)

        val effectiveVowelSpans = if (
            vowelMatches.size >= 2 &&
            clean.length >= 4 &&
            (clean.endsWith("e", ignoreCase = true)) &&
            vowelMatches.last().range.last == clean.lastIndex &&
            vowelMatches.last().value.length == 1 &&
            !(clean.length >= 4 && clean.endsWith("le", ignoreCase = true) && clean[clean.length - 3] !in "aeiouAEIOU")
        ) {
            // Trailing silent '-e' in words like "cake", "time", "stone" (excluding syllabic "-Cle" like "apple", "table")
            vowelMatches.dropLast(1)
        } else {
            vowelMatches
        }

        val sylCount = effectiveVowelSpans.size.coerceIn(1, 6)
        if (sylCount <= 1) return listOf(clean)

        // Slice readable orthographic chunks at midpoints between consecutive vowel nuclei
        val cuts = ArrayList<Int>(sylCount + 1)
        cuts.add(0)
        for (i in 0 until sylCount - 1) {
            val leftEnd = effectiveVowelSpans[i].range.last + 1
            val rightStart = effectiveVowelSpans[i + 1].range.first
            val cut = ((leftEnd + rightStart + 1) / 2).coerceIn(cuts.last() + 1, clean.length - 1)
            cuts.add(cut)
        }
        cuts.add(clean.length)

        val res = ArrayList<String>(sylCount)
        for (i in 0 until cuts.size - 1) {
            if (cuts[i + 1] > cuts[i]) {
                res.add(clean.substring(cuts[i], cuts[i + 1]))
            }
        }
        return res.ifEmpty { listOf(clean) }
    }

    /**
     * Hybrid Acoustic + Orthographic Syllable Detector & Boundary Aligner:
     * - Builds a 10ms sonorant/voiced-weighted energy envelope (suppressing high-ZCR voiceless fricatives
     *   so sibilants never masquerade as vowel nuclei and intervocalic consonants form deep valleys).
     * - Fuses orthographic expectation (`expectedCountHint`) with prominent acoustic syllable nuclei:
     *   * Prevents multi-syllable speech from being collapsed into 1 syllable even if labeled as 1 unit
     *     (e.g. dialect disyllable under a single-char gloss, numeric code, or `segmentCount <= 1`),
     *     while protecting genuine monosyllabic diphthongs (`[ai]`) and dipping tones (`[214]`).
     *   * Handles monosyllabic rhotacized fusion (`花儿` -> 1 syllable when unimodal, or 2 when pronounced separately).
     * - Aligns syllable boundaries to actual inter-nuclear consonant/voicing valleys across arbitrary uneven
     *   syllable durations (e.g. 80ms checked tone + 320ms smooth tone) instead of rigid equal `1/N` windows.
     */
    private fun detectAndAlignSyllableSegments(
        pcm: FloatArray,
        startSample: Int,
        endSample: Int,
        expectedCountHint: Int,
        sampleRate: Int,
        labels: List<String>,
        frameIsVoicedSeq: BooleanArray,
        hopSize: Int,
        hasErhuaSuffix: Boolean = false
    ): List<Triple<Int, Int, String>> {
        val activeLen = (endSample - startSample).coerceAtLeast(512)
        val activeDurMs = (activeLen * 1000L / sampleRate.coerceAtLeast(8000)).toInt()
        if (activeLen < 4096 || activeDurMs < 150) {
            val singleLbl = if (labels.isNotEmpty()) labels.joinToString("") else S.common.syllableFallback.withArgs("index" to 1)
            return listOf(Triple(startSample, endSample, singleLbl))
        }

        val step = (sampleRate / 100).coerceAtLeast(80) // 10ms step
        val numSteps = (activeLen / step).coerceAtLeast(8)
        val rawSonorant = FloatArray(numSteps)
        val stepVoiced = BooleanArray(numSteps)

        for (i in 0 until numSteps) {
            val s = startSample + i * step
            val e = (s + step * 2).coerceAtMost(endSample)
            val winN = (e - s).coerceAtLeast(1)
            var sumSq = 0.0
            var zc = 0
            for (k in s until e) {
                val v = pcm[k]
                sumSq += v * v
                if (k > s && (pcm[k] >= 0f) != (pcm[k - 1] >= 0f)) zc++
            }
            val rms = sqrt(sumSq / winN).toFloat()
            val zcr = zc.toFloat() / winN
            val fIdx = if (hopSize > 0 && frameIsVoicedSeq.isNotEmpty()) {
                (((s + e) / 2) / hopSize).coerceIn(0, frameIsVoicedSeq.lastIndex)
            } else -1
            val isVoiced = if (fIdx >= 0) frameIsVoicedSeq[fIdx] else (zcr < 0.24f)
            stepVoiced[i] = isVoiced

            val zcrWeight = (1.0f - ((zcr - 0.17f).coerceAtLeast(0f) * 3.0f)).coerceIn(0.12f, 1.0f)
            val voiceWeight = if (isVoiced) 1.0f else 0.30f
            rawSonorant[i] = rms * zcrWeight * voiceWeight
        }

        // 5-tap Gaussian smoothing (~40ms effective kernel)
        val kernel = floatArrayOf(0.12f, 0.22f, 0.32f, 0.22f, 0.12f)
        val smoothEnv = FloatArray(numSteps)
        var maxEnv = 1e-6f
        for (i in 0 until numSteps) {
            var acc = 0f
            for (k in -2..2) {
                val idx = (i + k).coerceIn(0, numSteps - 1)
                acc += rawSonorant[idx] * kernel[k + 2]
            }
            smoothEnv[i] = acc
            if (acc > maxEnv) maxEnv = acc
        }

        // Locate candidate local maxima (vowel nuclei)
        val rawPeaks = ArrayList<Int>()
        for (i in 2 until numSteps - 2) {
            val v = smoothEnv[i]
            if (v >= maxEnv * 0.18f &&
                v >= smoothEnv[i - 1] && v >= smoothEnv[i + 1] &&
                v >= smoothEnv[i - 2] && v >= smoothEnv[i + 2]
            ) {
                if (rawPeaks.isNotEmpty() && i - rawPeaks.last() < 8) {
                    if (v > smoothEnv[rawPeaks.last()]) {
                        rawPeaks[rawPeaks.lastIndex] = i
                    }
                } else {
                    rawPeaks.add(i)
                }
            }
        }
        if (rawPeaks.isEmpty()) {
            var bestIdx = numSteps / 2
            for (i in 0 until numSteps) {
                if (smoothEnv[i] > smoothEnv[bestIdx]) bestIdx = i
            }
            rawPeaks.add(bestIdx)
        }

        fun valleyStatsBetween(pLeft: Int, pRight: Int): Triple<Int, Float, Int> {
            var minStep = (pLeft + pRight) / 2
            var minVal = Float.MAX_VALUE
            var unvoicedCount = 0
            for (k in (pLeft + 1) until pRight) {
                if (!stepVoiced[k]) unvoicedCount++
                val penalty = if (!stepVoiced[k]) 0.75f else 1.0f
                val eff = smoothEnv[k] * penalty
                if (eff < minVal) {
                    minVal = eff
                    minStep = k
                }
            }
            val refPeak = minOf(smoothEnv[pLeft], smoothEnv[pRight]).coerceAtLeast(1e-6f)
            val dipRatio = (smoothEnv[minStep] / refPeak).coerceIn(0f, 1f)
            return Triple(minStep, dipRatio, unvoicedCount)
        }

        // Prune micro-ripples within a single vowel plateau (dipRatio > 0.76 and zero unvoiced break)
        val candidatePeaks = ArrayList<Int>(rawPeaks)
        var changed = true
        while (changed && candidatePeaks.size > 1) {
            changed = false
            for (i in 0 until candidatePeaks.size - 1) {
                val pA = candidatePeaks[i]
                val pB = candidatePeaks[i + 1]
                val (_, dipRatio, unvCnt) = valleyStatsBetween(pA, pB)
                if ((pB - pA) < 9 || (dipRatio > 0.78f && unvCnt == 0)) {
                    val dropIdx = if (smoothEnv[pA] >= smoothEnv[pB]) i + 1 else i
                    candidatePeaks.removeAt(dropIdx)
                    changed = true
                    break
                }
            }
        }

        // Count strictly separated acoustic syllables (requiring deep consonant/closure valley so [ai] & [214] never split)
        val strongPeaks = ArrayList<Int>()
        for (p in candidatePeaks) {
            if (smoothEnv[p] < maxEnv * 0.24f) continue
            if (strongPeaks.isEmpty()) {
                strongPeaks.add(p)
            } else {
                val prev = strongPeaks.last()
                val (_, dipRatio, unvCnt) = valleyStatsBetween(prev, p)
                val clearConsonantOrPauseBreak = (p - prev) >= 13 &&
                    (dipRatio <= 0.32f || (unvCnt >= 4 && dipRatio <= 0.48f))
                if (clearConsonantOrPauseBreak) {
                    strongPeaks.add(p)
                } else if (smoothEnv[p] > smoothEnv[prev] && dipRatio > 0.60f) {
                    strongPeaks[strongPeaks.lastIndex] = p
                }
            }
        }
        val strongAcousticCount = strongPeaks.size.coerceIn(1, 6)

        // Fuse orthographic hint with acoustic syllable evidence
        val targetCount = when {
            expectedCountHint <= 0 -> {
                // Pure acoustic auto-detection mode (e.g. corpus stats scan)
                val medPeaks = ArrayList<Int>()
                for (p in candidatePeaks) {
                    if (smoothEnv[p] < maxEnv * 0.22f) continue
                    if (medPeaks.isEmpty()) {
                        medPeaks.add(p)
                    } else {
                        val prev = medPeaks.last()
                        val (_, dipRatio, unvCnt) = valleyStatsBetween(prev, p)
                        if ((p - prev) >= 11 && (dipRatio <= 0.44f || (unvCnt >= 3 && dipRatio <= 0.56f))) {
                            medPeaks.add(p)
                        }
                    }
                }
                medPeaks.size.coerceIn(1, 6)
            }
            expectedCountHint == 1 -> {
                // Upgrade from 1 to N if audio unmistakably contains multiple separated syllables!
                if (activeDurMs >= 360 && strongAcousticCount >= 2) {
                    strongAcousticCount
                } else {
                    1
                }
            }
            else -> {
                // expectedCountHint >= 2
                if (hasErhuaSuffix && candidatePeaks.size < expectedCountHint && activeDurMs < expectedCountHint * 245) {
                    (expectedCountHint - 1).coerceAtLeast(1)
                } else if (strongAcousticCount > expectedCountHint && activeDurMs >= strongAcousticCount * 220) {
                    strongAcousticCount
                } else {
                    expectedCountHint
                }
            }
        }

        // Resolve clean syllable labels matching targetCount
        val resolvedLabels: List<String> = when {
            targetCount == 1 -> {
                val merged = if (labels.isNotEmpty()) {
                    if (hasErhuaSuffix && labels.size == 2) labels.joinToString("") else labels.first()
                } else S.common.syllableFallback.withArgs("index" to 1)
                listOf(merged)
            }
            labels.size == targetCount -> labels
            hasErhuaSuffix && labels.size == targetCount + 1 && targetCount >= 1 -> {
                labels.dropLast(2) + listOf(labels[labels.size - 2] + labels.last())
            }
            labels.size == 1 && targetCount > 1 -> {
                val base = labels.first().ifBlank { S.common.wordFallback.withArgs("index" to "") }
                (1..targetCount).map { idx -> "$base·$idx" }
            }
            else -> {
                (0 until targetCount).map { idx -> labels.getOrNull(idx) ?: S.common.syllableFallback.withArgs("index" to idx + 1) }
            }
        }

        if (targetCount <= 1) {
            return listOf(Triple(startSample, endSample, resolvedLabels.first()))
        }

        // Place (targetCount - 1) boundaries at true inter-nuclear valleys!
        val boundarySteps = ArrayList<Int>(targetCount - 1)
        if (candidatePeaks.size >= targetCount) {
            // Select top-`targetCount` prominent peaks sorted chronologically
            val chosenPeaks = if (candidatePeaks.size == targetCount) {
                candidatePeaks.sorted()
            } else if (strongPeaks.size == targetCount) {
                strongPeaks.sorted()
            } else {
                candidatePeaks
                    .sortedByDescending { smoothEnv[it] }
                    .take(targetCount)
                    .sorted()
            }
            for (m in 0 until targetCount - 1) {
                val pLeft = chosenPeaks[m]
                val pRight = chosenPeaks[m + 1]
                val searchFrom = (pLeft + 2).coerceAtMost(pRight)
                val searchTo = (pRight - 2).coerceAtLeast(searchFrom)
                var bestStep = (pLeft + pRight) / 2
                var bestScore = Float.MAX_VALUE
                for (k in searchFrom..searchTo) {
                    val voiceFactor = if (!stepVoiced[k]) 0.65f else 1.0f
                    val score = smoothEnv[k] * voiceFactor
                    if (score < bestScore) {
                        bestScore = score
                        bestStep = k
                    }
                }
                boundarySteps.add(bestStep)
            }
        } else {
            // Fewer distinct peaks than expected syllables (e.g. vocalic hiatus without stops):
            // search wide flexible regions [b/K - 0.30/K .. b/K + 0.30/K] enforcing >= 6 steps spacing
            var prevStep = 0
            for (b in 1 until targetCount) {
                val remaining = targetCount - b
                val minAllow = (prevStep + 6).coerceAtMost(numSteps - remaining * 5 - 1).coerceAtLeast(1)
                val maxAllow = (numSteps - remaining * 6).coerceAtLeast(minAllow)
                val expectedStep = (numSteps * b) / targetCount
                val radius = (numSteps / (targetCount * 2)).coerceAtLeast(4)
                val from = (expectedStep - radius).coerceIn(minAllow, maxAllow)
                val to = (expectedStep + radius).coerceIn(from, maxAllow)

                var bestStep = expectedStep.coerceIn(from, to)
                var bestScore = Float.MAX_VALUE
                for (k in from..to) {
                    val voiceFactor = if (!stepVoiced[k]) 0.68f else 1.0f
                    val score = smoothEnv[k] * voiceFactor
                    if (score < bestScore) {
                        bestScore = score
                        bestStep = k
                    }
                }
                boundarySteps.add(bestStep)
                prevStep = bestStep
            }
        }

        val sampleBounds = ArrayList<Int>(targetCount + 1)
        sampleBounds.add(startSample)
        for (bStep in boundarySteps) {
            val minBound = (sampleBounds.last() + 256).coerceAtMost(endSample)
            val maxBound = (endSample - 256).coerceAtLeast(minBound)
            sampleBounds.add((startSample + bStep * step).coerceIn(minBound, maxBound))
        }
        sampleBounds.add(endSample)

        return (0 until targetCount).map { idx ->
            Triple(sampleBounds[idx], sampleBounds[idx + 1], resolvedLabels[idx])
        }
    }

    /**
     * Pitch-synchronous glottal pulse detection for standard Praat-compatible
     * Jitter (local, %) and Shimmer (local, %).
     */
    private fun computePitchSynchronousPerturbation(
        pcm: FloatArray,
        startSample: Int,
        endSample: Int,
        meanF0: Float,
        sampleRate: Int
    ): Pair<Float, Float> {
        if (meanF0 <= 40f || endSample - startSample < 2048) return 0f to 0f
        val expectedPeriod = (sampleRate / meanF0).toInt()
        val minPeriod = (expectedPeriod * 0.65f).toInt().coerceAtLeast(30)
        val maxPeriod = (expectedPeriod * 1.35f).toInt().coerceAtMost(1000)

        val span = endSample - startSample
        val steadyStart = startSample + (span * 0.22f).toInt()
        val steadyEnd = startSample + (span * 0.78f).toInt()

        var currentPeakIdx = steadyStart
        var maxInit = -1f
        val initWindow = (steadyStart + maxPeriod).coerceAtMost(steadyEnd)
        for (i in steadyStart until initWindow) {
            if (pcm[i] > maxInit) {
                maxInit = pcm[i]
                currentPeakIdx = i
            }
        }

        val periods = mutableListOf<Double>()
        val peakToPeakAmps = mutableListOf<Double>()

        while (currentPeakIdx + maxPeriod < steadyEnd) {
            val searchFrom = currentPeakIdx + minPeriod
            val searchTo = (currentPeakIdx + maxPeriod).coerceAtMost(steadyEnd)
            var nextPeakIdx = -1
            var maxVal = -1f
            for (i in searchFrom..searchTo) {
                if (pcm[i] > maxVal) {
                    maxVal = pcm[i]
                    nextPeakIdx = i
                }
            }
            if (nextPeakIdx == -1 || maxVal < 0.01f) break

            val prevVal = pcm[(nextPeakIdx - 1).coerceAtLeast(0)]
            val nextVal = pcm[(nextPeakIdx + 1).coerceAtMost(pcm.size - 1)]
            val denom = 2.0 * (2.0 * maxVal - prevVal - nextVal)
            val delta = if (abs(denom) > 1e-6) ((prevVal - nextVal) / denom).coerceIn(-0.5, 0.5) else 0.0

            val periodSamples = (nextPeakIdx + delta) - currentPeakIdx
            if (periodSamples in minPeriod.toDouble()..maxPeriod.toDouble()) {
                periods.add(periodSamples)
                var minValley = 0f
                for (i in currentPeakIdx..nextPeakIdx) {
                    if (pcm[i] < minValley) minValley = pcm[i]
                }
                peakToPeakAmps.add((maxVal - minValley).toDouble())
            }
            currentPeakIdx = nextPeakIdx
        }

        if (periods.size < 4) return 0f to 0f

        var periodDiffSum = 0.0
        for (i in 0 until periods.size - 1) {
            periodDiffSum += abs(periods[i] - periods[i + 1])
        }
        val meanPeriod = periods.average()
        val jitterLocal = if (meanPeriod > 0) {
            ((periodDiffSum / (periods.size - 1)) / meanPeriod * 100.0).toFloat()
        } else 0f

        var ampDiffSum = 0.0
        for (i in 0 until peakToPeakAmps.size - 1) {
            ampDiffSum += abs(peakToPeakAmps[i] - peakToPeakAmps[i + 1])
        }
        val meanAmp = peakToPeakAmps.average()
        val shimmerLocal = if (meanAmp > 0) {
            ((ampDiffSum / (peakToPeakAmps.size - 1)) / meanAmp * 100.0).toFloat()
        } else 0f

        return jitterLocal.coerceIn(0f, 25f) to shimmerLocal.coerceIn(0f, 35f)
    }

    private data class FormantPeak(
        val freqHz: Int,
        val bandwidthHz: Double,
        val powerDb: Double,
        val prominenceDb: Double
    )

    /**
     * Praat-grade Formant (F1, F2) extraction:
     * 1. Anti-aliased 4:1 Low-pass Decimation from 44,100 Hz to 11,025 Hz (Nyquist = 5,512.5 Hz)
     * 2. Standard Praat 50 Hz Pre-emphasis applied AT the decimated sample rate:
     *    y[n] = x[n] - exp(-2*pi*50/fs)*x[n-1]
     * 3. Multi-frame steady-state vowel nucleus (韵腹) selection within [20%..75%] of the syllable,
     *    weighting high resonant energy and rejecting high-ZCR consonant bursts/fricatives.
     * 4. Levinson-Durbin LPC (order p = 10..11) with corrected inverse filter sign:
     *    A(z) = 1 - sum_{k=1..p} a[k] z^-k
     * 5. Parabolic-interpolated LPC spectral peak extraction with local prominence & 3dB bandwidth,
     *    explicit F0 voice-bar suppression, and physiological frequency ordering (F1 < F2 < F3).
     */
    private fun extractFormantsPraatLPC(
        pcm: FloatArray,
        segStart: Int,
        segEnd: Int,
        sampleRate: Int,
        syllableMeanF0: Float,
        isHighRegister: Boolean,
        isMidRegister: Boolean = false
    ): Pair<Int, Int> {
        val segLen = segEnd - segStart
        if (segLen < 1024) return 0 to 0

        // 1. Anti-aliased 4:1 decimation of the syllable to fs = 11025 Hz
        val decimation = 4
        val fsLpc = sampleRate / decimation // 11025 Hz
        val decLen = segLen / decimation
        if (decLen < 128) return 0 to 0

        // 9-tap symmetric low-pass anti-aliasing filter (cutoff ~4.5kHz at 44.1kHz)
        val fir = doubleArrayOf(0.025, 0.065, 0.125, 0.185, 0.200, 0.185, 0.125, 0.065, 0.025)
        val decimated = DoubleArray(decLen)
        for (i in 0 until decLen) {
            val center = segStart + i * decimation
            var acc = 0.0
            for (t in -4..4) {
                val idx = (center + t).coerceIn(0, pcm.size - 1)
                acc += pcm[idx] * fir[t + 4]
            }
            decimated[i] = acc
        }

        // 2. Locate the steady-state vowel nucleus (韵腹) within [20%..75%] of the decimated syllable
        val winLen = 276 // ~25.0ms at 11025 Hz (standard Praat formant window)
        val hop = 44     // ~4.0ms step
        if (decLen < winLen) return 0 to 0

        val searchFrom = (decLen * 0.20).toInt().coerceIn(0, (decLen - winLen).coerceAtLeast(0))
        val searchTo = (decLen * 0.75).toInt().coerceIn(searchFrom, (decLen - winLen).coerceAtLeast(0))

        data class NucleusCandidate(val startIdx: Int, val score: Double)
        val candidates = mutableListOf<NucleusCandidate>()

        var pos = searchFrom
        while (pos <= searchTo) {
            var sumSq = 0.0
            var zc = 0
            for (i in 0 until winLen) {
                val s = decimated[pos + i]
                sumSq += s * s
                if (i > 0 && (decimated[pos + i] >= 0.0) != (decimated[pos + i - 1] >= 0.0)) {
                    zc++
                }
            }
            val rms = sqrt(sumSq / winLen)
            val zcr = zc.toDouble() / winLen
            val zcrPenalty = ((zcr - 0.18).coerceAtLeast(0.0) * 2.5).coerceAtMost(0.85)
            val score = rms * (1.0 - zcrPenalty)
            candidates.add(NucleusCandidate(pos, score))
            pos += hop
        }

        if (candidates.isEmpty()) {
            candidates.add(NucleusCandidate((decLen - winLen) / 2, 1.0))
        }

        val bestCenter = candidates.maxByOrNull { it.score }!!.startIdx
        val selectedStarts = candidates
            .sortedBy { abs(it.startIdx - bestCenter) }
            .take(5)
            .map { it.startIdx }

        val frameF1s = mutableListOf<Int>()
        val frameF2s = mutableListOf<Int>()

        val preEmphAlpha = exp(-2.0 * Math.PI * 50.0 / fsLpc) // ~0.9719 (Praat 50 Hz pre-emphasis)
        val order = if (isHighRegister) 12 else if (isMidRegister) 12 else 13

        for (frameStart in selectedStarts) {
            val sig = DoubleArray(winLen)
            for (i in 0 until winLen) {
                val curr = decimated[frameStart + i]
                val prev = decimated[(frameStart + i - 1).coerceAtLeast(0)]
                val pre = curr - preEmphAlpha * prev
                val hamming = 0.54 - 0.46 * cos(2.0 * Math.PI * i / (winLen - 1))
                sig[i] = pre * hamming
            }

            val r = DoubleArray(order + 1)
            for (lag in 0..order) {
                var sum = 0.0
                for (i in 0 until winLen - lag) {
                    sum += sig[i] * sig[i + lag]
                }
                r[lag] = sum * (0.996).pow(lag.toDouble())
            }
            if (r[0] <= 1e-10) continue

            // Levinson-Durbin recursion for predictor coefficients a[1..order]
            // where the inverse filter is A(z) = 1 - sum_{k=1..order} a[k] z^-k
            val a = DoubleArray(order + 1)
            val aPrev = DoubleArray(order + 1)
            var error = r[0]

            for (i in 1..order) {
                var lambda = 0.0
                for (j in 1 until i) {
                    lambda += aPrev[j] * r[i - j]
                }
                val k = (r[i] - lambda) / error
                a[i] = k
                for (j in 1 until i) {
                    a[j] = aPrev[j] - k * aPrev[i - j]
                }
                for (j in 1..i) {
                    aPrev[j] = a[j]
                }
                error *= (1.0 - k * k)
                if (error <= 1e-12) break
            }

            // Evaluate correct LPC power spectrum: P(omega) = 1 / |1 - sum_{k=1..order} a[k] e^{-j*omega*k}|^2
            val minFreq = 180
            val maxFreq = 3600
            val freqStep = 10
            val numBins = (maxFreq - minFreq) / freqStep + 1
            val envPowerDb = DoubleArray(numBins)

            for (b in 0 until numBins) {
                val freq = minFreq + b * freqStep
                val omega = 2.0 * Math.PI * freq / fsLpc
                var re = 1.0
                var im = 0.0
                for (k in 1..order) {
                    re -= a[k] * cos(omega * k)
                    im += a[k] * sin(omega * k)
                }
                val magSq = (re * re + im * im).coerceAtLeast(1e-10)
                envPowerDb[b] = 10.0 * log10(1.0 / magSq)
            }

            // Extract all true local peaks with parabolic sub-bin interpolation, 3dB bandwidth, and prominence
            val peaks = extractSpectralFormantPeaks(envPowerDb, minFreq, freqStep, numBins)

            val f1Floor = when {
                isHighRegister -> 240
                isMidRegister -> 225
                else -> 215
            }
            val f1Ceil = when {
                isHighRegister -> 1020
                isMidRegister -> 960
                else -> 900
            }

            // Compute narrowband DFT power on the pre-emphasized signal in the F1 region [200..960 Hz]
            // to prevent strong H1 (F0 voice-bar) from pulling mid-vowel F1 (420..600 Hz) down to ~300 Hz.
            val f1DftMin = 200
            val f1DftMax = 960
            val f1DftStep = 10
            val f1DftBins = (f1DftMax - f1DftMin) / f1DftStep + 1
            val rawDftPower = DoubleArray(f1DftBins)
            val f0VoiceBarLimit = (syllableMeanF0 * 1.18f).roundToInt().coerceIn(190, 290)

            var closeBandEnergy = 1e-12
            var closeCount = 0
            var openBandEnergy = 1e-12
            var openCount = 0

            for (b in 0 until f1DftBins) {
                val freq = f1DftMin + b * f1DftStep
                val omega = 2.0 * Math.PI * freq / fsLpc
                var re = 0.0
                var im = 0.0
                for (n in 0 until winLen) {
                    re += sig[n] * cos(omega * n)
                    im -= sig[n] * sin(omega * n)
                }
                val p = re * re + im * im
                rawDftPower[b] = p
                if (freq in 200..390) {
                    val weight = if (freq <= f0VoiceBarLimit) 0.35 else 1.0
                    closeBandEnergy += p * weight
                    closeCount++
                } else if (freq in 430..920) {
                    openBandEnergy += p
                    openCount++
                }
            }
            val meanClosePower = if (closeCount > 0) closeBandEnergy / closeCount else 1e-12
            val meanOpenPower = if (openCount > 0) openBandEnergy / openCount else 1e-12
            val isMidOrOpenVowel = meanOpenPower > meanClosePower * 0.55

            val f1Candidates = peaks.filter { it.freqHz in f1Floor..f1Ceil && it.bandwidthHz <= 620.0 }
            val f1 = if (isMidOrOpenVowel) {
                val midOpenLpcCandidates = f1Candidates.filter { it.freqHz in 385..f1Ceil }
                if (midOpenLpcCandidates.isNotEmpty()) {
                    midOpenLpcCandidates.maxByOrNull {
                        it.powerDb + it.prominenceDb * 1.5 - 0.015 * it.bandwidthHz
                    }!!.freqHz
                } else {
                    // When H1 and mid-vowel F1 merge in low-order LPC, locate the peak of the
                    // Gaussian-smoothed harmonic spectrum in [390..f1Ceil] (suppressing H1 <= f0VoiceBarLimit)
                    val sigmaBins = (maxOf(95f, syllableMeanF0 * 0.62f) / f1DftStep).toDouble()
                    var bestFreq = 500
                    var maxSmoothPower = -1.0
                    for (b in 0 until f1DftBins) {
                        val freq = f1DftMin + b * f1DftStep
                        if (freq !in 390..f1Ceil) continue
                        var weightedSum = 0.0
                        var weightTotal = 0.0
                        for (k in 0 until f1DftBins) {
                            val kf = f1DftMin + k * f1DftStep
                            if (kf <= f0VoiceBarLimit) continue
                            val dist = (b - k).toDouble() / sigmaBins
                            val w = exp(-0.5 * dist * dist)
                            weightedSum += rawDftPower[k] * w
                            weightTotal += w
                        }
                        val smoothP = if (weightTotal > 0.0) weightedSum / weightTotal else 0.0
                        if (smoothP > maxSmoothPower) {
                            maxSmoothPower = smoothP
                            bestFreq = freq
                        }
                    }
                    bestFreq
                }
            } else {
                val closeLpcCandidates = f1Candidates.filter { it.freqHz in f1Floor..410 }.ifEmpty { f1Candidates }
                if (closeLpcCandidates.isNotEmpty()) {
                    closeLpcCandidates.maxByOrNull {
                        it.powerDb + it.prominenceDb * 1.5 - 0.015 * it.bandwidthHz
                    }!!.freqHz
                } else {
                    300
                }
            }

            // Physiological vocal-tract triangle constraint for F2:
            val f2Min = maxOf(if (isHighRegister) 740 else 680, f1 + 220)
            val vocalTractF2Max = (2800 - ((f1 - 260).coerceAtLeast(0) * 1.35).toInt())
                .coerceIn(1550, if (isHighRegister) 2850 else 2650)

            val f2Candidates = peaks.filter {
                it.freqHz in f2Min..vocalTractF2Max && it.bandwidthHz <= 780.0
            }

            val f2 = if (f2Candidates.isNotEmpty()) {
                val maxCandPower = f2Candidates.maxOf { it.powerDb }
                // Formants are ordered by frequency (F1 < F2 < F3).
                // Adapt primary F2 ceiling by vocal register so female front vowels [y, e, ɛ] (2200..2480 Hz)
                // are not artificially capped at male 2150 Hz.
                val f2PrimaryCeil = when {
                    isHighRegister -> 2480
                    isMidRegister -> 2320
                    else -> 2180
                }
                val primaryF2Pool = f2Candidates.filter {
                    it.freqHz <= f2PrimaryCeil && it.prominenceDb >= 1.2 && it.powerDb >= maxCandPower - 14.0
                }.ifEmpty { f2Candidates }

                primaryF2Pool.maxByOrNull { peak ->
                    peak.powerDb + peak.prominenceDb * 1.6 - 0.012 * peak.bandwidthHz
                }!!.freqHz
            } else {
                findShoulderFrequency(envPowerDb, minFreq, freqStep, numBins, f2Min, vocalTractF2Max)
            }

            frameF1s.add(f1)
            frameF2s.add(f2)
        }

        if (frameF1s.isEmpty() || frameF2s.isEmpty()) return 0 to 0
        val medianF1 = frameF1s.sorted()[frameF1s.size / 2]
        val medianF2 = frameF2s.sorted()[frameF2s.size / 2]
        return medianF1 to medianF2
    }

    /**
     * Extracts all local peaks from the LPC log-power spectrum, computing parabolic-interpolated
     * center frequency, -3 dB bandwidth, and local prominence above adjacent valleys.
     */
    private fun extractSpectralFormantPeaks(
        envPowerDb: DoubleArray,
        minFreq: Int,
        freqStep: Int,
        numBins: Int
    ): List<FormantPeak> {
        val peaks = mutableListOf<FormantPeak>()
        for (b in 1 until numBins - 1) {
            if (envPowerDb[b] > envPowerDb[b - 1] && envPowerDb[b] > envPowerDb[b + 1]) {
                val y1 = envPowerDb[b - 1]
                val y2 = envPowerDb[b]
                val y3 = envPowerDb[b + 1]
                val denom = 2.0 * (2.0 * y2 - y1 - y3)
                val delta = if (abs(denom) > 1e-6) ((y1 - y3) / denom).coerceIn(-0.5, 0.5) else 0.0
                val exactFreq = (minFreq + (b + delta) * freqStep).roundToInt()

                // Walk left and right to find -3 dB points and local valleys
                var leftBin = b
                var leftMinDb = y2
                var left3dB = -1.0
                while (leftBin > 0 && envPowerDb[leftBin - 1] <= envPowerDb[leftBin]) {
                    leftBin--
                    if (envPowerDb[leftBin] < leftMinDb) leftMinDb = envPowerDb[leftBin]
                    if (left3dB < 0.0 && y2 - envPowerDb[leftBin] >= 3.0) {
                        left3dB = (minFreq + leftBin * freqStep).toDouble()
                    }
                }
                if (left3dB < 0.0) left3dB = (minFreq + leftBin * freqStep).toDouble()

                var rightBin = b
                var rightMinDb = y2
                var right3dB = -1.0
                while (rightBin < numBins - 1 && envPowerDb[rightBin + 1] <= envPowerDb[rightBin]) {
                    rightBin++
                    if (envPowerDb[rightBin] < rightMinDb) rightMinDb = envPowerDb[rightBin]
                    if (right3dB < 0.0 && y2 - envPowerDb[rightBin] >= 3.0) {
                        right3dB = (minFreq + rightBin * freqStep).toDouble()
                    }
                }
                if (right3dB < 0.0) right3dB = (minFreq + rightBin * freqStep).toDouble()

                val bw = (right3dB - left3dB).coerceAtLeast(40.0)
                val prominence = y2 - maxOf(leftMinDb, rightMinDb)
                peaks.add(FormantPeak(exactFreq, bw, y2, prominence))
            }
        }
        return peaks
    }

    /**
     * Finds the strongest convex shoulder (minimum of 2nd derivative d2P/df2 < 0) when two close
     * formants (such as F1 and F2 in back rounded vowels [u, o]) merge into a single spectral hump.
     */
    private fun findShoulderFrequency(
        envPowerDb: DoubleArray,
        minFreq: Int,
        freqStep: Int,
        numBins: Int,
        searchMinHz: Int,
        searchMaxHz: Int
    ): Int {
        var bestFreq = (searchMinHz + searchMaxHz) / 2
        var minSecondDeriv = Double.MAX_VALUE
        for (b in 2 until numBins - 2) {
            val f = minFreq + b * freqStep
            if (f !in searchMinHz..searchMaxHz) continue
            val d2 = envPowerDb[b + 1] - 2.0 * envPowerDb[b] + envPowerDb[b - 1]
            if (d2 < minSecondDeriv) {
                minSecondDeriv = d2
                bestFreq = f
            }
        }
        return bestFreq
    }

    /**
     * Computes average DFT power across [minHz..maxHz] for vowel openness verification.
     */
    private fun computeBandPower(
        signal: DoubleArray,
        startIdx: Int,
        length: Int,
        fs: Int,
        minHz: Int,
        maxHz: Int,
        stepHz: Int
    ): Double {
        var totalPower = 0.0
        var count = 0
        var freq = minHz
        while (freq <= maxHz) {
            val omega = 2.0 * Math.PI * freq / fs
            var re = 0.0
            var im = 0.0
            for (n in 0 until length) {
                val w = 0.5 - 0.5 * cos(2.0 * Math.PI * n / (length - 1))
                val s = signal[startIdx + n] * w
                re += s * cos(omega * n)
                im -= s * sin(omega * n)
            }
            totalPower += (re * re + im * im)
            count++
            freq += stepHz
        }
        return if (count > 0) totalPower / count else 0.0
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun loadWavPcm(file: File): FloatArray? {
        if (!file.exists() || file.length() <= 44) return null
        return try {
            val bytes = file.readBytes()
            val dataBytesCount = bytes.size - 44
            if (dataBytesCount <= 0) return null
            val sampleCount = dataBytesCount / 2
            val pcm = FloatArray(sampleCount)
            for (i in 0 until sampleCount) {
                val byte1 = bytes[44 + i * 2].toInt() and 0xFF
                val byte2 = bytes[44 + i * 2 + 1].toInt()
                val sampleShort = (byte2 shl 8) or byte1
                pcm[i] = sampleShort.toFloat() / 32768f
            }
            pcm
        } catch (e: Exception) {
            Log.e(TAG, "Error loading PCM: ${e.message}")
            null
        }
    }
}
