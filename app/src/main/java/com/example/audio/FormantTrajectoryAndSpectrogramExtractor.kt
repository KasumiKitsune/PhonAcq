package com.example.audio

import com.example.i18n.S
import com.example.i18n.withArgs
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

object FormantTrajectoryAndSpectrogramExtractor {

    private data class FormantPeakCand(
        val freqHz: Int,
        val bandwidthHz: Double,
        val powerDb: Double,
        val prominenceDb: Double
    )

    private data class AnchorTupleCand(
        val f1: Int,
        val f2: Int,
        val f3: Int,
        val emitScore: Double
    )

    private val spectrogramCache = ConcurrentHashMap<String, WidebandSpectrogramData>()

    fun clearCache() {
        spectrogramCache.clear()
    }

    /**
     * Extracts a 7-anchor Praat-grade LPC + Viterbi Formant Trajectory for a single syllable
     * within [segStart..segEnd], automatically classifying steady Monophthongs vs Diphthongs vs Triphthongs
     * in perceptual Bark space while rejecting consonant locus transitions.
     */
    fun extractSyllableFormantTrajectory(
        pcm: FloatArray,
        segStart: Int,
        segEnd: Int,
        sampleRate: Int,
        syllableIndex: Int,
        syllableLabel: String,
        syllableMeanF0: Float,
        isHighRegister: Boolean,
        isMidRegister: Boolean
    ): SyllableFormantTrajectory {
        val emptyFallback = SyllableFormantTrajectory(
            syllableIndex = syllableIndex,
            syllableLabel = syllableLabel,
            vowelType = VowelGlideType.MONOPHTHONG,
            anchors = emptyList(),
            onsetF1 = 0,
            onsetF2 = 0,
            nucleusF1 = 0,
            nucleusF2 = 0,
            nucleusF3 = 0,
            targetF1 = 0,
            targetF2 = 0,
            barkGlideSpan = 0f,
            trajectoryAngleDeg = 0f,
            trajectorySummary = S.acoustics.noStableFormantDetected
        )

        val segLen = segEnd - segStart
        if (segLen < 1024 || sampleRate < 8000) return emptyFallback

        // 1. Anti-aliased 4:1 decimation to ~11,025 Hz (Nyquist ~5.5 kHz)
        val decimation = (sampleRate / 11025).coerceIn(1, 8)
        val fsLpc = sampleRate / decimation
        val decLen = segLen / decimation
        val winLen = (fsLpc * 0.025).roundToInt().coerceIn(128, 512) // 25ms Praat Gaussian/Hamming LPC window
        if (decLen < winLen + 8) return emptyFallback

        val fir = doubleArrayOf(0.025, 0.065, 0.125, 0.185, 0.200, 0.185, 0.125, 0.065, 0.025)
        val decimated = DoubleArray(decLen)
        for (i in 0 until decLen) {
            val center = segStart + i * decimation
            var acc = 0.0
            if (decimation >= 2) {
                for (t in -4..4) {
                    val idx = (center + t).coerceIn(0, pcm.size - 1)
                    acc += pcm[idx] * fir[t + 4]
                }
            } else {
                acc = pcm[center.coerceIn(0, pcm.size - 1)].toDouble()
            }
            decimated[i] = acc
        }

        // 2. Scan frame RMS & ZCR to bound the voiced vowel rime core (excluding consonant burst/frication & coda tail)
        val scanHop = (fsLpc * 0.005).roundToInt().coerceAtLeast(16)
        data class ScanFrame(val startIdx: Int, val rms: Double, val zcr: Double)
        val scanFrames = ArrayList<ScanFrame>()
        var maxScanRms = 1e-6
        var p = 0
        while (p <= decLen - winLen) {
            var sumSq = 0.0
            var zc = 0
            for (i in 0 until winLen) {
                val v = decimated[p + i]
                sumSq += v * v
                if (i > 0 && (v >= 0.0) != (decimated[p + i - 1] >= 0.0)) zc++
            }
            val rms = sqrt(sumSq / winLen)
            val zcr = zc.toDouble() / winLen
            if (rms > maxScanRms) maxScanRms = rms
            scanFrames.add(ScanFrame(p, rms, zcr))
            p += scanHop
        }
        if (scanFrames.isEmpty()) return emptyFallback

        // Restrict to interior [16%..82%] and further trim low-energy/high-ZCR consonant edges
        val minBoundIdx = (decLen * 0.16).toInt().coerceIn(0, decLen - winLen)
        val maxBoundIdx = (decLen * 0.82).toInt().coerceIn(minBoundIdx, decLen - winLen)
        val resonantFrames = scanFrames.filter {
            it.startIdx in minBoundIdx..maxBoundIdx &&
                it.rms >= maxScanRms * 0.20 &&
                it.zcr <= 0.25
        }

        val coreStartDec = resonantFrames.firstOrNull()?.startIdx ?: minBoundIdx
        val coreEndDec = resonantFrames.lastOrNull()?.startIdx ?: maxBoundIdx

        // Sample 7 time-normalized anchors across the voiced rime core
        val anchorCount = 7
        val anchorStarts = IntArray(anchorCount) { idx ->
            if (coreEndDec <= coreStartDec) {
                coreStartDec
            } else {
                (coreStartDec + (coreEndDec - coreStartDec) * idx.toDouble() / (anchorCount - 1.0))
                    .roundToInt()
                    .coerceIn(0, decLen - winLen)
            }
        }

        val preEmphAlpha = exp(-2.0 * Math.PI * 50.0 / fsLpc)
        val lpcOrder = if (isHighRegister) 11 else if (isMidRegister) 12 else 12

        val anchorCandidatesList = ArrayList<List<AnchorTupleCand>>(anchorCount)
        val anchorConfidences = FloatArray(anchorCount)

        for (aIdx in 0 until anchorCount) {
            val fStart = anchorStarts[aIdx]
            var rawSumSq = 0.0
            for (i in 0 until winLen) {
                val s = decimated[fStart + i]
                rawSumSq += s * s
            }
            val localRms = sqrt(rawSumSq / winLen)
            anchorConfidences[aIdx] = (localRms / maxScanRms).toFloat().coerceIn(0.15f, 1.0f)

            val cands = extractFrameFormantTuples(
                decimated = decimated,
                frameStart = fStart,
                winLen = winLen,
                fsLpc = fsLpc,
                preEmphAlpha = preEmphAlpha,
                order = lpcOrder,
                syllableMeanF0 = syllableMeanF0,
                isHighRegister = isHighRegister,
                isMidRegister = isMidRegister
            )
            anchorCandidatesList.add(cands)
        }

        if (anchorCandidatesList.any { it.isEmpty() }) {
            return emptyFallback
        }

        // 3. Viterbi Dynamic Programming for smooth F1/F2/F3 track continuity across the 7 anchors
        val chosenTuples = solveViterbiFormantTrack(anchorCandidatesList)

        // 4. Light internal smoothing while preserving glide endpoints
        val rawF1 = chosenTuples.map { it.f1 }.toIntArray()
        val rawF2 = chosenTuples.map { it.f2 }.toIntArray()
        val rawF3 = chosenTuples.map { it.f3 }.toIntArray()

        val smoothF1 = IntArray(anchorCount)
        val smoothF2 = IntArray(anchorCount)
        val smoothF3 = IntArray(anchorCount)
        for (i in 0 until anchorCount) {
            when (i) {
                0 -> {
                    smoothF1[i] = (0.76 * rawF1[0] + 0.24 * rawF1[1]).roundToInt()
                    smoothF2[i] = (0.76 * rawF2[0] + 0.24 * rawF2[1]).roundToInt()
                    smoothF3[i] = (0.76 * rawF3[0] + 0.24 * rawF3[1]).roundToInt()
                }
                anchorCount - 1 -> {
                    val last = anchorCount - 1
                    smoothF1[i] = (0.24 * rawF1[last - 1] + 0.76 * rawF1[last]).roundToInt()
                    smoothF2[i] = (0.24 * rawF2[last - 1] + 0.76 * rawF2[last]).roundToInt()
                    smoothF3[i] = (0.24 * rawF3[last - 1] + 0.76 * rawF3[last]).roundToInt()
                }
                else -> {
                    smoothF1[i] = (0.22 * rawF1[i - 1] + 0.56 * rawF1[i] + 0.22 * rawF1[i + 1]).roundToInt()
                    smoothF2[i] = (0.22 * rawF2[i - 1] + 0.56 * rawF2[i] + 0.22 * rawF2[i + 1]).roundToInt()
                    smoothF3[i] = (0.22 * rawF3[i - 1] + 0.56 * rawF3[i] + 0.22 * rawF3[i + 1]).roundToInt()
                }
            }
        }

        val anchorFrames = (0 until anchorCount).map { i ->
            val centerSampleInPcm = segStart + (anchorStarts[i] + winLen / 2) * decimation
            val relPos = ((centerSampleInPcm - segStart).toFloat() / segLen.toFloat()).coerceIn(0.05f, 0.95f)
            FormantAnchorFrame(
                normPos = relPos,
                timeSec = centerSampleInPcm.toFloat() / sampleRate.toFloat(),
                f1Hz = smoothF1[i],
                f2Hz = smoothF2[i],
                f3Hz = smoothF3[i],
                confidence = anchorConfidences[i]
            )
        }

        // 5. Evaluate trajectory geometry in perceptual Bark space (B1, B2)
        val b1Arr = FloatArray(anchorCount) { PhoneticScaleUtils.hzToBark(smoothF1[it].toFloat()) }
        val b2Arr = FloatArray(anchorCount) { PhoneticScaleUtils.hzToBark(smoothF2[it].toFloat()) }

        val dB1 = b1Arr.last() - b1Arr.first()
        val dB2 = b2Arr.last() - b2Arr.first()
        val netSpanBark = hypot(dB1, dB2)

        var pathLenBark = 0f
        for (i in 0 until anchorCount - 1) {
            pathLenBark += hypot(b1Arr[i + 1] - b1Arr[i], b2Arr[i + 1] - b2Arr[i])
        }

        // Maximum perpendicular distance from internal points to the chord (Start -> End)
        var maxInflectionBark = 0f
        var inflectIdx = anchorCount / 2
        for (i in 1 until anchorCount - 1) {
            val dist = pointToSegmentDistanceBark(
                px = b2Arr[i], py = b1Arr[i],
                x1 = b2Arr.first(), y1 = b1Arr.first(),
                x2 = b2Arr.last(), y2 = b1Arr.last()
            )
            if (dist > maxInflectionBark) {
                maxInflectionBark = dist
                inflectIdx = i
            }
        }

        // Monotonicity ratio prevents erratic noise wobble from masquerading as a diphthong
        val directnessRatio = if (pathLenBark > 0.05f) netSpanBark / pathLenBark else 1f

        val vowelType = when {
            pathLenBark >= 1.80f && maxInflectionBark >= 0.72f -> VowelGlideType.TRIPHTHONG
            netSpanBark >= 1.08f && directnessRatio >= 0.58f -> VowelGlideType.DIPHTHONG
            pathLenBark >= 1.42f && netSpanBark >= 0.92f && directnessRatio >= 0.52f -> VowelGlideType.DIPHTHONG
            else -> VowelGlideType.MONOPHTHONG
        }

        // Steady-state median for monophthongs vs true Onset/Nucleus/Target for diphthongs & triphthongs
        val midIndices = listOf(1, 2, 3, 4, 5)
        val medF1 = midIndices.map { smoothF1[it] }.sorted()[2]
        val medF2 = midIndices.map { smoothF2[it] }.sorted()[2]
        val medF3 = midIndices.map { smoothF3[it] }.sorted()[2]

        val onsetF1: Int
        val onsetF2: Int
        val nucleusF1: Int
        val nucleusF2: Int
        val nucleusF3: Int
        val targetF1: Int
        val targetF2: Int
        val reportedGlideSpan: Float

        when (vowelType) {
            VowelGlideType.MONOPHTHONG -> {
                onsetF1 = medF1
                onsetF2 = medF2
                nucleusF1 = medF1
                nucleusF2 = medF2
                nucleusF3 = medF3
                targetF1 = medF1
                targetF2 = medF2
                reportedGlideSpan = netSpanBark
            }
            VowelGlideType.DIPHTHONG -> {
                onsetF1 = smoothF1.first()
                onsetF2 = smoothF2.first()
                nucleusF1 = smoothF1[anchorCount / 2]
                nucleusF2 = smoothF2[anchorCount / 2]
                nucleusF3 = smoothF3[anchorCount / 2]
                targetF1 = smoothF1.last()
                targetF2 = smoothF2.last()
                reportedGlideSpan = netSpanBark
            }
            VowelGlideType.TRIPHTHONG -> {
                onsetF1 = smoothF1.first()
                onsetF2 = smoothF2.first()
                nucleusF1 = smoothF1[inflectIdx]
                nucleusF2 = smoothF2[inflectIdx]
                nucleusF3 = smoothF3[inflectIdx]
                targetF1 = smoothF1.last()
                targetF2 = smoothF2.last()
                reportedGlideSpan = pathLenBark
            }
        }

        // Angle in IPA vowel chart screen space (left = higher F2, down = higher F1)
        val chartDx = -(b2Arr.last() - b2Arr.first())
        val chartDy = (b1Arr.last() - b1Arr.first())
        val angleDeg = Math.toDegrees(atan2(chartDy.toDouble(), chartDx.toDouble())).toFloat()

        val summary = when (vowelType) {
            VowelGlideType.MONOPHTHONG -> {
                val reg = PhoneticScaleUtils.describeArticulatoryRegion(nucleusF1, nucleusF2)
                S.acoustics.formantSteadyNucleusFormat.withArgs("reg" to reg, "f1" to nucleusF1, "f2" to nucleusF2)
            }
            VowelGlideType.DIPHTHONG -> {
                val rStart = PhoneticScaleUtils.describeArticulatoryRegion(onsetF1, onsetF2)
                val rEnd = PhoneticScaleUtils.describeArticulatoryRegion(targetF1, targetF2)
                S.acoustics.diphthongGlideSummaryFormat.withArgs(
                    "start" to rStart,
                    "end" to rEnd,
                    "span" to String.format(java.util.Locale.US, "%.1f", reportedGlideSpan)
                )
            }
            VowelGlideType.TRIPHTHONG -> {
                val rStart = PhoneticScaleUtils.describeArticulatoryRegion(onsetF1, onsetF2)
                val rMid = PhoneticScaleUtils.describeArticulatoryRegion(nucleusF1, nucleusF2)
                val rEnd = PhoneticScaleUtils.describeArticulatoryRegion(targetF1, targetF2)
                S.acoustics.triphthongInflectionSummaryFormat.withArgs(
                    "start" to rStart,
                    "mid" to rMid,
                    "end" to rEnd,
                    "span" to String.format(java.util.Locale.US, "%.1f", reportedGlideSpan)
                )
            }
        }

        return SyllableFormantTrajectory(
            syllableIndex = syllableIndex,
            syllableLabel = syllableLabel,
            vowelType = vowelType,
            anchors = anchorFrames,
            onsetF1 = onsetF1,
            onsetF2 = onsetF2,
            nucleusF1 = nucleusF1,
            nucleusF2 = nucleusF2,
            nucleusF3 = nucleusF3,
            targetF1 = targetF1,
            targetF2 = targetF2,
            barkGlideSpan = if (reportedGlideSpan.isFinite()) reportedGlideSpan else 0f,
            trajectoryAngleDeg = if (angleDeg.isFinite()) angleDeg else 0f,
            trajectorySummary = summary
        )
    }

    /**
     * Lazy-evaluated Praat-style Wideband Spectrogram (0..5000 Hz, ~6ms Gaussian window, 512-pt FFT)
     * paired with time-aligned F0 track and dense Formant (F1/F2/F3) track for single-word verification.
     */
    fun extractWidebandSpectrogram(
        file: File?,
        speakerGender: String?,
        pitchRange: Pair<Float, Float>? = null
    ): WidebandSpectrogramData? {
        if (file == null || !file.exists() || file.length() <= 44L) return null
        val cacheKey = "${file.absolutePath}_${file.lastModified()}_${speakerGender ?: ""}_${pitchRange?.first?.toInt() ?: 0}"
        spectrogramCache[cacheKey]?.let { return it }

        val fmt = AcousticAnalyzer.readWavHeaderFormat(file)
        val sampleRate = fmt.sampleRate.coerceIn(8000, 192000)
        val pcm = readMonoPcmFromWav(file, fmt) ?: return null
        if (pcm.size < 512) return null

        val totalSec = pcm.size.toFloat() / sampleRate.toFloat()

        // Locate active speech window via block RMS so silence padding doesn't squash the syllable
        val blockSize = (sampleRate * 0.020).roundToInt().coerceAtLeast(256)
        val numBlocks = (pcm.size / blockSize).coerceAtLeast(1)
        val blockRms = FloatArray(numBlocks)
        var maxRms = 1e-5f
        for (b in 0 until numBlocks) {
            val s0 = b * blockSize
            val s1 = ((b + 1) * blockSize).coerceAtMost(pcm.size)
            var sq = 0.0
            for (i in s0 until s1) sq += pcm[i] * pcm[i]
            val r = sqrt(sq / (s1 - s0).coerceAtLeast(1)).toFloat()
            blockRms[b] = r
            if (r > maxRms) maxRms = r
        }
        val physBlocks = blockRms.filter { it > 1.5e-4f }.ifEmpty { blockRms.toList() }.sorted()
        val floorRms = physBlocks.take((physBlocks.size * 0.2).toInt().coerceAtLeast(1)).average().toFloat()
        val vadThresh = maxOf(floorRms * 2.4f, maxRms * 0.09f, 0.0025f)

        val firstBlk = blockRms.indexOfFirst { it >= vadThresh }.takeIf { it >= 0 } ?: 0
        val lastBlk = blockRms.indexOfLast { it >= vadThresh }.takeIf { it >= firstBlk } ?: (numBlocks - 1)

        val speechStartSample = (firstBlk * blockSize).coerceAtLeast(0)
        val speechEndSample = ((lastBlk + 1) * blockSize).coerceAtMost(pcm.size)

        val marginSamples = (sampleRate * 0.065f).roundToInt()
        val viewStartSample = (speechStartSample - marginSamples).coerceAtLeast(0)
        val viewEndSample = (speechEndSample + marginSamples).coerceAtMost(pcm.size)
        val viewSamples = (viewEndSample - viewStartSample).coerceAtLeast(512)

        // Wideband ~6.0ms Gaussian window (resolves glottal pulses + crisp dark formant bars)
        val winSamples = (sampleRate * 0.0062f).roundToInt().coerceIn(96, 512)
        val fftSize = 512
        val maxFreqHz = minOf(5000, sampleRate / 2 - 100)
        val freqBins = ((maxFreqHz.toDouble() / sampleRate.toDouble()) * fftSize).roundToInt().coerceIn(24, fftSize / 2)

        val timeCols = 168
        val rawDbGrid = FloatArray(timeCols * freqBins)
        val preAlpha = exp(-2.0 * Math.PI * 50.0 / sampleRate).toFloat()

        val gaussWin = DoubleArray(winSamples) { n ->
            val x = (n - (winSamples - 1) / 2.0) / ((winSamples - 1) / 2.0)
            exp(-0.5 * (2.5 * x) * (2.5 * x))
        }

        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        var maxDb = -120f

        for (col in 0 until timeCols) {
            val centerSample = viewStartSample + ((viewSamples - 1) * col.toLong() / (timeCols - 1)).toInt()
            val halfWin = winSamples / 2
            java.util.Arrays.fill(re, 0.0)
            java.util.Arrays.fill(im, 0.0)

            for (n in 0 until winSamples) {
                val idx = (centerSample - halfWin + n).coerceIn(0, pcm.size - 1)
                val prevIdx = (idx - 1).coerceAtLeast(0)
                val pre = pcm[idx] - preAlpha * pcm[prevIdx]
                re[n] = pre * gaussWin[n]
            }

            fftRadix2(re, im, fftSize)

            val colOffset = col * freqBins
            for (b in 0 until freqBins) {
                val magSq = (re[b] * re[b] + im[b] * im[b]).coerceAtLeast(1e-12)
                val db = (10.0 * log10(magSq)).toFloat()
                rawDbGrid[colOffset + b] = db
                if (db > maxDb) maxDb = db
            }
        }

        // Praat standard 48 dB dynamic range compression into [0..1]
        val dynamicRangeDb = 48f
        val floorDb = maxDb - dynamicRangeDb
        val normGrid = FloatArray(rawDbGrid.size) { idx ->
            ((rawDbGrid[idx] - floorDb) / dynamicRangeDb).coerceIn(0f, 1f)
        }

        // Dense F0 track across [viewStartSample..viewEndSample]
        val normGender = speakerGender?.trim() ?: ""
        val minF0 = if (pitchRange != null && pitchRange.first > 40f) {
            (pitchRange.first * 0.72f).coerceAtLeast(60f)
        } else if (normGender == "女") 115f else 72f
        val maxF0 = if (pitchRange != null && pitchRange.second > 60f) {
            (pitchRange.second * 1.28f).coerceAtMost(520f)
        } else if (normGender == "男") 320f else 460f

        val pitchSteps = 72
        val pitchTimes = FloatArray(pitchSteps)
        val rawPitchTrack = FloatArray(pitchSteps)
        val acfWin = (sampleRate * 0.024f).roundToInt().coerceIn(256, 2048)
        val minLag = (sampleRate / maxF0).toInt().coerceAtLeast(20)
        val maxLag = (sampleRate / minF0).toInt().coerceAtMost(acfWin - 2)

        for (s in 0 until pitchSteps) {
            val center = viewStartSample + ((viewSamples - 1) * s.toLong() / (pitchSteps - 1)).toInt()
            pitchTimes[s] = center.toFloat() / sampleRate.toFloat()
            val start = (center - acfWin / 2).coerceIn(0, (pcm.size - acfWin).coerceAtLeast(0))
            if (start + acfWin + maxLag >= pcm.size) continue

            var sq = 0.0
            var zc = 0
            for (i in 0 until acfWin) {
                val v = pcm[start + i]
                sq += v * v
                if (i > 0 && (v >= 0f) != (pcm[start + i - 1] >= 0f)) zc++
            }
            val rms = sqrt(sq / acfWin).toFloat()
            val zcr = zc.toFloat() / acfWin
            if (rms < vadThresh * 0.70f || zcr > 0.27f) continue

            var bestLag = -1
            var bestScore = -1f
            var rawBest = 0f
            for (lag in minLag..maxLag) {
                var num = 0.0
                var denL = 0.0
                var denR = 0.0
                var i = 0
                while (i < acfWin) {
                    val a = pcm[start + i].toDouble()
                    val b = pcm[start + i + lag].toDouble()
                    num += a * b
                    denL += a * a
                    denR += b * b
                    i += 2 // 2x stride speedup for responsive UI
                }
                val denom = sqrt(denL * denR)
                val r = if (denom > 1e-7) (num / denom).toFloat() else 0f
                val penalty = 0.09f * (lag - minLag).toFloat() / (maxLag - minLag).coerceAtLeast(1)
                if (r > 0.50f && r - penalty > bestScore) {
                    bestScore = r - penalty
                    bestLag = lag
                    rawBest = r
                }
            }
            if (bestLag > 0 && rawBest > 0.50f) {
                val f0 = sampleRate.toFloat() / bestLag.toFloat()
                if (f0 in minF0..maxF0) rawPitchTrack[s] = f0
            }
        }

        // Median-filter isolated F0 glitches on the spectrogram overlay
        val validF0s = rawPitchTrack.filter { it > 0f }.sorted()
        val medF0 = if (validF0s.isNotEmpty()) validF0s[validF0s.size / 2] else (if (normGender == "女") 210f else 135f)
        val cleanPitchTrack = FloatArray(pitchSteps) { idx ->
            val v = rawPitchTrack[idx]
            if (v <= 0f) 0f
            else {
                val deOct = when {
                    v > medF0 * 1.70f -> v * 0.5f
                    v < medF0 * 0.56f -> v * 2.0f
                    else -> v
                }
                val neighbors = (-1..1).mapNotNull { d ->
                    val nv = rawPitchTrack[(idx + d).coerceIn(0, pitchSteps - 1)]
                    if (nv > 0f) nv else null
                }.sorted()
                if (neighbors.size >= 2) neighbors[neighbors.size / 2] else deOct
            }
        }

        // Dense formant track across active speech region (up to 15 Viterbi-smoothed frames)
        val denseFormants = extractDenseFormantOverlay(
            pcm = pcm,
            speechStart = speechStartSample,
            speechEnd = speechEndSample,
            sampleRate = sampleRate,
            medianF0 = medF0,
            isHighRegister = medF0 >= 170f || normGender == "女",
            isMidRegister = medF0 in 140f..185f
        )

        val coreStartSec = ((speechStartSample + (speechEndSample - speechStartSample) * 0.16f) / sampleRate.toFloat())
        val coreEndSec = ((speechStartSample + (speechEndSample - speechStartSample) * 0.82f) / sampleRate.toFloat())

        val specData = WidebandSpectrogramData(
            durationSec = totalSec,
            windowStartSec = viewStartSample.toFloat() / sampleRate.toFloat(),
            windowEndSec = viewEndSample.toFloat() / sampleRate.toFloat(),
            coreStartSec = coreStartSec,
            coreEndSec = coreEndSec,
            maxFreqHz = maxFreqHz,
            timeCols = timeCols,
            freqBins = freqBins,
            normalizedDbGrid = normGrid,
            pitchTrackTimeSec = pitchTimes,
            pitchTrackHz = cleanPitchTrack,
            formantTrackPoints = denseFormants
        )

        if (spectrogramCache.size > 16) spectrogramCache.clear()
        spectrogramCache[cacheKey] = specData
        return specData
    }

    private fun extractDenseFormantOverlay(
        pcm: FloatArray,
        speechStart: Int,
        speechEnd: Int,
        sampleRate: Int,
        medianF0: Float,
        isHighRegister: Boolean,
        isMidRegister: Boolean
    ): List<FormantAnchorFrame> {
        val segLen = speechEnd - speechStart
        if (segLen < 1024) return emptyList()

        val decimation = (sampleRate / 11025).coerceIn(1, 8)
        val fsLpc = sampleRate / decimation
        val decLen = segLen / decimation
        val winLen = (fsLpc * 0.025).roundToInt().coerceIn(128, 512)
        if (decLen < winLen + 8) return emptyList()

        val fir = doubleArrayOf(0.025, 0.065, 0.125, 0.185, 0.200, 0.185, 0.125, 0.065, 0.025)
        val decimated = DoubleArray(decLen)
        for (i in 0 until decLen) {
            val center = speechStart + i * decimation
            var acc = 0.0
            for (t in -4..4) {
                val idx = (center + t).coerceIn(0, pcm.size - 1)
                acc += pcm[idx] * fir[t + 4]
            }
            decimated[i] = acc
        }

        val numPts = 15
        val preEmphAlpha = exp(-2.0 * Math.PI * 50.0 / fsLpc)
        val order = if (isHighRegister) 11 else 12

        var maxRms = 1e-6
        val frameRms = DoubleArray(numPts)
        val frameZcr = DoubleArray(numPts)
        val starts = IntArray(numPts) { idx ->
            val frac = 0.08 + 0.84 * (idx.toDouble() / (numPts - 1.0))
            ((decLen - winLen) * frac).roundToInt().coerceIn(0, decLen - winLen)
        }

        for (i in 0 until numPts) {
            val st = starts[i]
            var sq = 0.0
            var zc = 0
            for (k in 0 until winLen) {
                val v = decimated[st + k]
                sq += v * v
                if (k > 0 && (v >= 0.0) != (decimated[st + k - 1] >= 0.0)) zc++
            }
            frameRms[i] = sqrt(sq / winLen)
            frameZcr[i] = zc.toDouble() / winLen
            if (frameRms[i] > maxRms) maxRms = frameRms[i]
        }

        val candLists = ArrayList<List<AnchorTupleCand>>()
        val validIndices = ArrayList<Int>()
        for (i in 0 until numPts) {
            if (frameRms[i] >= maxRms * 0.16 && frameZcr[i] <= 0.27) {
                val cands = extractFrameFormantTuples(
                    decimated = decimated,
                    frameStart = starts[i],
                    winLen = winLen,
                    fsLpc = fsLpc,
                    preEmphAlpha = preEmphAlpha,
                    order = order,
                    syllableMeanF0 = medianF0,
                    isHighRegister = isHighRegister,
                    isMidRegister = isMidRegister
                )
                if (cands.isNotEmpty()) {
                    candLists.add(cands)
                    validIndices.add(i)
                }
            }
        }
        if (candLists.isEmpty()) return emptyList()

        val chosen = solveViterbiFormantTrack(candLists)
        return chosen.mapIndexed { k, tup ->
            val origIdx = validIndices[k]
            val centerPcm = speechStart + (starts[origIdx] + winLen / 2) * decimation
            FormantAnchorFrame(
                normPos = ((centerPcm - speechStart).toFloat() / segLen.toFloat()).coerceIn(0f, 1f),
                timeSec = centerPcm.toFloat() / sampleRate.toFloat(),
                f1Hz = tup.f1,
                f2Hz = tup.f2,
                f3Hz = tup.f3,
                confidence = (frameRms[origIdx] / maxRms).toFloat().coerceIn(0.2f, 1f)
            )
        }
    }

    private fun extractFrameFormantTuples(
        decimated: DoubleArray,
        frameStart: Int,
        winLen: Int,
        fsLpc: Int,
        preEmphAlpha: Double,
        order: Int,
        syllableMeanF0: Float,
        isHighRegister: Boolean,
        isMidRegister: Boolean
    ): List<AnchorTupleCand> {
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
        if (r[0] <= 1e-10) return emptyList()

        val a = DoubleArray(order + 1)
        val aPrev = DoubleArray(order + 1)
        var error = r[0]
        for (i in 1..order) {
            var lambda = 0.0
            for (j in 1 until i) lambda += aPrev[j] * r[i - j]
            val k = (r[i] - lambda) / error
            a[i] = k
            for (j in 1 until i) a[j] = aPrev[j] - k * aPrev[i - j]
            for (j in 1..i) aPrev[j] = a[j]
            error *= (1.0 - k * k)
            if (error <= 1e-12) break
        }

        val minFreq = 180
        val maxFreq = 4200
        val freqStep = 12
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

        val peaks = extractSpectralPeaks(envPowerDb, minFreq, freqStep, numBins)

        val f1Floor = if (isHighRegister) 235 else if (isMidRegister) 220 else 210
        val f1Ceil = if (isHighRegister) 1040 else if (isMidRegister) 980 else 920
        val f0VoiceBarLimit = (syllableMeanF0 * 1.18f).roundToInt().coerceIn(185, 290)

        // Check low-vs-open vowel DFT energy ratio so strong H1 doesn't pull [a/ɛ/ɔ] F1 down to 280 Hz
        var closePower = 1e-12
        var closeCnt = 0
        var openPower = 1e-12
        var openCnt = 0
        var fTest = 210
        while (fTest <= 910) {
            val omega = 2.0 * Math.PI * fTest / fsLpc
            var re = 0.0
            var im = 0.0
            var n = 0
            while (n < winLen) {
                re += sig[n] * cos(omega * n)
                im -= sig[n] * sin(omega * n)
                n += 2
            }
            val p = re * re + im * im
            if (fTest <= 385) {
                closePower += p * (if (fTest <= f0VoiceBarLimit) 0.35 else 1.0)
                closeCnt++
            } else if (fTest >= 430) {
                openPower += p
                openCnt++
            }
            fTest += 35
        }
        val isMidOrOpen = (openPower / openCnt.coerceAtLeast(1)) > (closePower / closeCnt.coerceAtLeast(1)) * 0.56

        val f1Pool = peaks.filter { it.freqHz in f1Floor..f1Ceil && it.bandwidthHz <= 640.0 }
            .sortedByDescending { p ->
                val openBonus = if (isMidOrOpen && p.freqHz >= 385) 4.0 else if (!isMidOrOpen && p.freqHz <= 415) 3.5 else 0.0
                p.powerDb + p.prominenceDb * 1.5 - 0.014 * p.bandwidthHz + openBonus
            }
            .take(3)
            .ifEmpty {
                val fallbackF1 = if (isMidOrOpen) 560 else 310
                listOf(FormantPeakCand(fallbackF1, 180.0, 0.0, 1.0))
            }

        val tuples = ArrayList<AnchorTupleCand>(8)
        for (f1Cand in f1Pool) {
            val f1 = f1Cand.freqHz
            val f2Min = maxOf(if (isHighRegister) 720 else 660, f1 + 210)
            val f2Max = (2850 - ((f1 - 260).coerceAtLeast(0) * 1.25).toInt())
                .coerceIn(1550, if (isHighRegister) 2920 else 2680)

            val f2Pool = peaks.filter { it.freqHz in f2Min..f2Max && it.bandwidthHz <= 800.0 }
                .sortedByDescending { it.powerDb + it.prominenceDb * 1.55 - 0.012 * it.bandwidthHz }
                .take(3)
                .ifEmpty {
                    val shF2 = findShoulderFreq(envPowerDb, minFreq, freqStep, numBins, f2Min, f2Max)
                    listOf(FormantPeakCand(shF2, 240.0, -4.0, 0.8))
                }

            for (f2Cand in f2Pool) {
                val f2 = f2Cand.freqHz
                val f3Min = maxOf(f2 + 320, if (isHighRegister) 2150 else 1900)
                val f3Max = if (isHighRegister) 4100 else 3750
                val f3Best = peaks.filter { it.freqHz in f3Min..f3Max }
                    .maxByOrNull { it.powerDb + it.prominenceDb * 1.2 - 0.01 * it.bandwidthHz }
                    ?.freqHz ?: (if (isHighRegister) 2950 else 2620).coerceAtLeast(f2 + 380)

                val openBonus = if (isMidOrOpen && f1 >= 385) 3.5 else if (!isMidOrOpen && f1 <= 415) 3.0 else -1.5
                val emitScore = (f1Cand.powerDb * 0.45 + f1Cand.prominenceDb * 1.4 + openBonus) +
                    (f2Cand.powerDb * 0.45 + f2Cand.prominenceDb * 1.5)
                tuples.add(AnchorTupleCand(f1 = f1, f2 = f2, f3 = f3Best, emitScore = emitScore))
            }
        }

        return tuples.sortedByDescending { it.emitScore }.take(8)
    }

    private fun solveViterbiFormantTrack(candLists: List<List<AnchorTupleCand>>): List<AnchorTupleCand> {
        val T = candLists.size
        if (T == 1) return listOf(candLists[0].first())

        val dp = Array(T) { t -> DoubleArray(candLists[t].size) { -1e12 } }
        val backPtr = Array(T) { t -> IntArray(candLists[t].size) { 0 } }

        for (j in candLists[0].indices) {
            dp[0][j] = candLists[0][j].emitScore
        }

        for (t in 1 until T) {
            val currList = candLists[t]
            val prevList = candLists[t - 1]
            for (j in currList.indices) {
                val curr = currList[j]
                val b1Curr = PhoneticScaleUtils.hzToBark(curr.f1.toFloat())
                val b2Curr = PhoneticScaleUtils.hzToBark(curr.f2.toFloat())
                val b3Curr = PhoneticScaleUtils.hzToBark(curr.f3.toFloat())

                var bestPrevScore = -1e12
                var bestPrevIdx = 0
                for (i in prevList.indices) {
                    val prev = prevList[i]
                    val db1 = abs(b1Curr - PhoneticScaleUtils.hzToBark(prev.f1.toFloat()))
                    val db2 = abs(b2Curr - PhoneticScaleUtils.hzToBark(prev.f2.toFloat()))
                    val db3 = abs(b3Curr - PhoneticScaleUtils.hzToBark(prev.f3.toFloat()))
                    // Quadratic jump penalty suppresses spurious pole switching while allowing smooth glides
                    val jumpCost = 3.4 * db1 * db1 + 2.8 * db2 * db2 + 0.8 * db3 * db3 + 1.5 * (db1 + db2)
                    val total = dp[t - 1][i] + curr.emitScore - jumpCost
                    if (total > bestPrevScore) {
                        bestPrevScore = total
                        bestPrevIdx = i
                    }
                }
                dp[t][j] = bestPrevScore
                backPtr[t][j] = bestPrevIdx
            }
        }

        val result = ArrayList<AnchorTupleCand>(T)
        var bestLastIdx = 0
        var bestLastVal = -1e12
        for (j in candLists[T - 1].indices) {
            if (dp[T - 1][j] > bestLastVal) {
                bestLastVal = dp[T - 1][j]
                bestLastIdx = j
            }
        }

        var currIdx = bestLastIdx
        for (t in T - 1 downTo 0) {
            result.add(candLists[t][currIdx])
            currIdx = backPtr[t][currIdx]
        }
        result.reverse()
        return result
    }

    private fun extractSpectralPeaks(
        envPowerDb: DoubleArray,
        minFreq: Int,
        freqStep: Int,
        numBins: Int
    ): List<FormantPeakCand> {
        val peaks = ArrayList<FormantPeakCand>()
        for (b in 1 until numBins - 1) {
            if (envPowerDb[b] > envPowerDb[b - 1] && envPowerDb[b] > envPowerDb[b + 1]) {
                val y1 = envPowerDb[b - 1]
                val y2 = envPowerDb[b]
                val y3 = envPowerDb[b + 1]
                val denom = 2.0 * (2.0 * y2 - y1 - y3)
                val delta = if (abs(denom) > 1e-6) ((y1 - y3) / denom).coerceIn(-0.5, 0.5) else 0.0
                val exactFreq = (minFreq + (b + delta) * freqStep).roundToInt()

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
                peaks.add(FormantPeakCand(exactFreq, bw, y2, prominence))
            }
        }
        return peaks
    }

    private fun findShoulderFreq(
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

    private fun pointToSegmentDistanceBark(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-6f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }

    /**
     * In-place Radix-2 Cooley-Tukey FFT (n must be a power of 2).
     */
    private fun fftRadix2(re: DoubleArray, im: DoubleArray, n: Int) {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wlenRe = cos(ang)
            val wlenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun readMonoPcmFromWav(file: File, fmt: AcousticAnalyzer.WavHeaderFormat): FloatArray? {
        return try {
            val bytes = file.readBytes()
            val offset = fmt.dataOffset.coerceAtMost(bytes.size)
            val dataBytes = bytes.size - offset
            if (dataBytes <= 0) return null
            val bytesPerSample = fmt.bytesPerSample
            val blockAlign = fmt.blockAlign
            val frames = dataBytes / blockAlign
            if (frames <= 0) return null
            val pcm = FloatArray(frames)
            for (i in 0 until frames) {
                val base = offset + i * blockAlign
                val sample = when (bytesPerSample) {
                    1 -> ((bytes[base].toInt() and 0xFF) - 128) / 128f
                    else -> {
                        val b1 = bytes[base].toInt() and 0xFF
                        val b2 = bytes[base + 1].toInt()
                        ((b2 shl 8) or b1).toFloat() / 32768f
                    }
                }
                pcm[i] = sample
            }
            pcm
        } catch (e: Exception) {
            null
        }
    }
}
