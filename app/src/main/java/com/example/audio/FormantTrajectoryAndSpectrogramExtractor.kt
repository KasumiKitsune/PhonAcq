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

        // 2. Scan 5ms hops for RMS, ZCR, and harmonic periodicity to isolate the true voiced vowel rime
        //    (excluding aspirated/fricative consonant bursts, stop release loci, and unvoiced tails).
        val scanHop = (fsLpc * 0.005).roundToInt().coerceAtLeast(16)
        data class ScanFrame(
            val startIdx: Int,
            val rms: Double,
            val zcr: Double,
            val acfPeak: Double,
            val localF0Hz: Float
        )
        val scanFrames = ArrayList<ScanFrame>()
        var maxScanRms = 1e-6
        var maxVoicedScanRms = 1e-6
        val minF0Lag = (fsLpc / 460.0).roundToInt().coerceAtLeast(12)
        val maxF0Lag = (fsLpc / 68.0).roundToInt().coerceAtMost((winLen * 3) / 4)

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

            var acfPeak = 0.0
            var estF0 = 0f
            if (rms > 1e-5 && zcr <= 0.26) {
                val corrLen = (winLen - maxF0Lag).coerceAtLeast(32)
                var eLeft = 0.0
                var k = 0
                while (k < corrLen) {
                    val v = decimated[p + k]
                    eLeft += v * v
                    k += 2
                }
                if (eLeft > 1e-10) {
                    var bestLag = -1
                    for (lag in minF0Lag..maxF0Lag step 2) {
                        var num = 0.0
                        var eRight = 0.0
                        var j = 0
                        while (j < corrLen) {
                            val a = decimated[p + j]
                            val b = decimated[p + j + lag]
                            num += a * b
                            eRight += b * b
                            j += 2
                        }
                        val den = sqrt(eLeft * eRight)
                        val r = if (den > 1e-10) num / den else 0.0
                        if (r > acfPeak) {
                            acfPeak = r
                            bestLag = lag
                        }
                    }
                    if (bestLag > 0 && acfPeak >= 0.36) {
                        estF0 = (fsLpc.toFloat() / bestLag.toFloat()).coerceIn(65f, 460f)
                    }
                }
            }
            if (acfPeak >= 0.40 && zcr <= 0.22 && rms > maxVoicedScanRms) {
                maxVoicedScanRms = rms
            }
            scanFrames.add(ScanFrame(p, rms, zcr, acfPeak, estF0))
            p += scanHop
        }
        if (scanFrames.isEmpty()) return emptyFallback

        val refRms = if (maxVoicedScanRms > maxScanRms * 0.25) maxVoicedScanRms else maxScanRms
        val isFrameVoicedVowel = BooleanArray(scanFrames.size) { idx ->
            val sf = scanFrames[idx]
            sf.rms >= refRms * 0.24 && sf.zcr <= 0.22 && (sf.acfPeak >= 0.38 || (sf.zcr <= 0.14 && sf.rms >= refRms * 0.34))
        }

        // Find the dominant contiguous voiced vowel core (bridging 1-2 frame creaky micro-dips)
        var bestRunStart = -1
        var bestRunEnd = -1
        var bestRunEnergy = -1.0
        var idxScan = 0
        while (idxScan < scanFrames.size) {
            if (!isFrameVoicedVowel[idxScan]) {
                idxScan++
                continue
            }
            val rStart = idxScan
            var rEnd = idxScan
            var rEnergy = 0.0
            while (idxScan < scanFrames.size) {
                if (isFrameVoicedVowel[idxScan]) {
                    rEnd = idxScan
                    rEnergy += scanFrames[idxScan].rms
                    idxScan++
                } else if (idxScan + 1 < scanFrames.size && isFrameVoicedVowel[idxScan + 1] &&
                    scanFrames[idxScan].rms >= refRms * 0.16 && scanFrames[idxScan].zcr <= 0.18
                ) {
                    rEnd = idxScan + 1
                    rEnergy += scanFrames[idxScan].rms
                    idxScan++
                } else {
                    break
                }
            }
            if (rEnergy > bestRunEnergy) {
                bestRunEnergy = rEnergy
                bestRunStart = rStart
                bestRunEnd = rEnd
            }
        }

        val voicedStartDec: Int
        val voicedEndDec: Int
        if (bestRunStart >= 0 && bestRunEnd >= bestRunStart) {
            voicedStartDec = scanFrames[bestRunStart].startIdx
            voicedEndDec = scanFrames[bestRunEnd].startIdx
        } else {
            voicedStartDec = (decLen * 0.20).toInt().coerceIn(0, decLen - winLen)
            voicedEndDec = (decLen * 0.80).toInt().coerceIn(voicedStartDec, decLen - winLen)
        }

        val voicedSpanSamples = (voicedEndDec + winLen - voicedStartDec).coerceAtLeast(winLen)
        val voicedDurMs = ((voicedSpanSamples * 1000L) / fsLpc.coerceAtLeast(1)).toInt()
        val usableVoicedTravel = (voicedEndDec - voicedStartDec).coerceAtLeast(0)

        // Adaptive boundary trim:
        // - If the earliest voiced frames already have clean harmonic periodicity (acfPeak >= 0.45 && zcr <= 0.17),
        //   consonant burst/frication (e.g. [ɕ] in 些, [t] release in 爹) is ALREADY behind us; use a gentle ~11ms / 8%
        //   margin so short vocalic medials ([i, u, y] in ie/ia/uo/ua/üe) are preserved intact!
        // - Otherwise (noisy stop/affricate transition), keep the ~22ms / 15% consonant-locus trim.
        val earlyCleanHarmonic = bestRunStart >= 0 && run {
            val f0 = scanFrames[bestRunStart]
            val f1 = scanFrames[(bestRunStart + 1).coerceAtMost(bestRunEnd)]
            ((f0.acfPeak + f1.acfPeak) * 0.5 >= 0.45) && ((f0.zcr + f1.zcr) * 0.5 <= 0.17)
        }
        val lateCleanHarmonic = bestRunEnd >= 0 && run {
            val fLast = scanFrames[bestRunEnd]
            val fPrev = scanFrames[(bestRunEnd - 1).coerceAtLeast(bestRunStart)]
            ((fLast.acfPeak + fPrev.acfPeak) * 0.5 >= 0.45) && ((fLast.zcr + fPrev.zcr) * 0.5 <= 0.17)
        }

        val onsetLocusTrim = if (earlyCleanHarmonic) {
            minOf(
                maxOf((fsLpc * 0.011).roundToInt(), (usableVoicedTravel * 0.075).roundToInt()),
                (usableVoicedTravel * 0.115).roundToInt()
            )
        } else {
            minOf(
                maxOf((fsLpc * 0.022).roundToInt(), (usableVoicedTravel * 0.145).roundToInt()),
                (usableVoicedTravel * 0.195).roundToInt()
            )
        }
        val offsetRelaxTrim = if (lateCleanHarmonic) {
            minOf(
                maxOf((fsLpc * 0.012).roundToInt(), (usableVoicedTravel * 0.085).roundToInt()),
                (usableVoicedTravel * 0.130).roundToInt()
            )
        } else {
            minOf(
                maxOf((fsLpc * 0.018).roundToInt(), (usableVoicedTravel * 0.130).roundToInt()),
                (usableVoicedTravel * 0.180).roundToInt()
            )
        }

        val coreStartDec = (voicedStartDec + onsetLocusTrim).coerceIn(0, decLen - winLen)
        val coreEndDec = (voicedEndDec - offsetRelaxTrim).coerceIn(coreStartDec, decLen - winLen)

        // Sample 9 time-normalized anchors across the pure voiced vowel rime core
        val anchorCount = 9
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
        // Odd LPC order 11 (or 12 for low male register) lets 1 real-axis root absorb steep back-vowel [u/o]
        // spectral tilt while preserving all 5 complex-conjugate formant pairs below 5.5 kHz.
        val lpcOrder = if (isHighRegister) 11 else 12

        val anchorCandidatesList = ArrayList<List<AnchorTupleCand>>(anchorCount)
        val anchorConfidences = FloatArray(anchorCount)
        val anchorLocalF0 = FloatArray(anchorCount)

        for (aIdx in 0 until anchorCount) {
            val fStart = anchorStarts[aIdx]
            var rawSumSq = 0.0
            for (i in 0 until winLen) {
                val s = decimated[fStart + i]
                rawSumSq += s * s
            }
            val localRms = sqrt(rawSumSq / winLen)
            anchorConfidences[aIdx] = (localRms / refRms).toFloat().coerceIn(0.15f, 1.0f)

            val nearestScan = scanFrames.minByOrNull { abs(it.startIdx - fStart) }
            val localF0 = if (nearestScan != null && nearestScan.localF0Hz > 60f) {
                nearestScan.localF0Hz
            } else {
                syllableMeanF0
            }
            anchorLocalF0[aIdx] = localF0

            val cands = extractFrameFormantTuples(
                decimated = decimated,
                frameStart = fStart,
                winLen = winLen,
                fsLpc = fsLpc,
                preEmphAlpha = preEmphAlpha,
                order = lpcOrder,
                syllableMeanF0 = localF0,
                isHighRegister = isHighRegister,
                isMidRegister = isMidRegister
            )
            anchorCandidatesList.add(cands)
        }

        if (anchorCandidatesList.any { it.isEmpty() }) {
            return emptyFallback
        }

        // 3. Viterbi Dynamic Programming for smooth F1/F2/F3 track continuity across the 9 anchors
        val chosenTuples = solveViterbiFormantTrack(anchorCandidatesList)

        // 4. 3-tap Hann smoothing to suppress single-frame harmonic crossings while preserving glide slopes
        val rawF1 = chosenTuples.map { it.f1 }.toIntArray()
        val rawF2 = chosenTuples.map { it.f2 }.toIntArray()
        val rawF3 = chosenTuples.map { it.f3 }.toIntArray()

        val smoothF1 = IntArray(anchorCount)
        val smoothF2 = IntArray(anchorCount)
        val smoothF3 = IntArray(anchorCount)
        for (i in 0 until anchorCount) {
            when (i) {
                0 -> {
                    smoothF1[i] = (0.84 * rawF1[0] + 0.16 * rawF1[1]).roundToInt()
                    smoothF2[i] = (0.84 * rawF2[0] + 0.16 * rawF2[1]).roundToInt()
                    smoothF3[i] = (0.84 * rawF3[0] + 0.16 * rawF3[1]).roundToInt()
                }
                anchorCount - 1 -> {
                    val last = anchorCount - 1
                    smoothF1[i] = (0.16 * rawF1[last - 1] + 0.84 * rawF1[last]).roundToInt()
                    smoothF2[i] = (0.16 * rawF2[last - 1] + 0.84 * rawF2[last]).roundToInt()
                    smoothF3[i] = (0.16 * rawF3[last - 1] + 0.84 * rawF3[last]).roundToInt()
                }
                else -> {
                    smoothF1[i] = (0.25 * rawF1[i - 1] + 0.50 * rawF1[i] + 0.25 * rawF1[i + 1]).roundToInt()
                    smoothF2[i] = (0.25 * rawF2[i - 1] + 0.50 * rawF2[i] + 0.25 * rawF2[i + 1]).roundToInt()
                    smoothF3[i] = (0.25 * rawF3[i - 1] + 0.50 * rawF3[i] + 0.25 * rawF3[i + 1]).roundToInt()
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

        // 5. Pure Objective Trajectory Geometry in Perceptual Bark Space (B1, B2)
        val b1Arr = FloatArray(anchorCount) { PhoneticScaleUtils.hzToBark(smoothF1[it].toFloat()) }
        val b2Arr = FloatArray(anchorCount) { PhoneticScaleUtils.hzToBark(smoothF2[it].toFloat()) }

        // Edge-spike guard:
        // Distinguish an isolated 1-frame consonant locus spike (which stalls or reverses on the next step)
        // from a genuine rapid rising/falling vocalic glide (e.g. [i -> ɛ] in 爹/些, [u -> a] in 瓜) where
        // consecutive steps continue moving coherently in the same direction!
        val sb01_1 = b1Arr[1] - b1Arr[0]
        val sb01_2 = b2Arr[1] - b2Arr[0]
        val sb12_1 = b1Arr[2] - b1Arr[1]
        val sb12_2 = b2Arr[2] - b2Arr[1]
        val onsetEdgeJumpBark = hypot(sb01_1, sb01_2)
        val secondStepBark = hypot(sb12_1, sb12_2)
        val onsetCosSim = if (onsetEdgeJumpBark > 1e-4f && secondStepBark > 1e-4f) {
            (sb01_1 * sb12_1 + sb01_2 * sb12_2) / (onsetEdgeJumpBark * secondStepBark)
        } else 0f
        val isCoherentOnsetGlide = onsetEdgeJumpBark <= 1.58f && secondStepBark >= 0.16f && onsetCosSim >= 0.45f

        val lastIdx = anchorCount - 1
        val sb78_1 = b1Arr[lastIdx] - b1Arr[lastIdx - 1]
        val sb78_2 = b2Arr[lastIdx] - b2Arr[lastIdx - 1]
        val sb67_1 = b1Arr[lastIdx - 1] - b1Arr[lastIdx - 2]
        val sb67_2 = b2Arr[lastIdx - 1] - b2Arr[lastIdx - 2]
        val offsetEdgeJumpBark = hypot(sb78_1, sb78_2)
        val penultStepBark = hypot(sb67_1, sb67_2)
        val offsetCosSim = if (offsetEdgeJumpBark > 1e-4f && penultStepBark > 1e-4f) {
            (sb78_1 * sb67_1 + sb78_2 * sb67_2) / (offsetEdgeJumpBark * penultStepBark)
        } else 0f
        val isCoherentOffsetGlide = offsetEdgeJumpBark <= 1.58f && penultStepBark >= 0.16f && offsetCosSim >= 0.45f

        val useCleanStart = onsetEdgeJumpBark <= 0.58f || isCoherentOnsetGlide
        val useCleanEnd = offsetEdgeJumpBark <= 0.58f || isCoherentOffsetGlide
        val effStartIdx = if (useCleanStart) 0 else 1
        val effEndIdx = (if (useCleanEnd) lastIdx else lastIdx - 1).coerceAtLeast(effStartIdx + 1)

        val b1Start = b1Arr[effStartIdx]
        val b2Start = b2Arr[effStartIdx]
        val b1End = b1Arr[effEndIdx]
        val b2End = b2Arr[effEndIdx]

        val rawDb1 = b1End - b1Start
        val rawDb2 = b2End - b2Start
        val rawNetSpanBark = hypot(rawDb1, rawDb2)

        // Compensate sympathetic F1 harmonic pull during steep tone glides (e.g. [51] / [35] / [214]):
        // physically, F0 drift can only pull F1 in the SAME sign as ΔF0 and when F2 is stationary (|ΔB2| < 0.38 Bark).
        val f0StartValid = listOf(anchorLocalF0[0], anchorLocalF0[1]).filter { it > 60f }
        val f0EndValid = listOf(anchorLocalF0[anchorCount - 2], anchorLocalF0[anchorCount - 1]).filter { it > 60f }
        val f0SignedDeltaHz = if (f0StartValid.isNotEmpty() && f0EndValid.isNotEmpty()) {
            f0EndValid.average().toFloat() - f0StartValid.average().toFloat()
        } else 0f
        val f0ExcursionHz = abs(f0SignedDeltaHz)
        val sameSignAsF0 = (rawDb1 * f0SignedDeltaHz) > 0f
        val f1HarmonicPullDiscountBark = if (sameSignAsF0 && f0ExcursionHz >= 30f && abs(rawDb2) < 0.38f) {
            ((f0ExcursionHz - 26f) * 0.0042f).coerceIn(0f, 0.46f)
        } else 0f
        val adjDb1Mag = (abs(rawDb1) - f1HarmonicPullDiscountBark).coerceAtLeast(0f)
        val effectiveNetSpanBark = hypot(adjDb1Mag, abs(rawDb2))

        // Interior core movement across [1 -> 7] (~central 52%) and [2 -> 6] (~central 35%)
        val coreSpan17Bark = hypot(b1Arr[7] - b1Arr[1], b2Arr[7] - b2Arr[1])
        val coreSpanBark = hypot(b1Arr[6] - b1Arr[2], b2Arr[6] - b2Arr[2])

        val dirNorm = rawNetSpanBark.coerceAtLeast(1e-4f)
        val unitB1 = rawDb1 / dirNorm
        val unitB2 = rawDb2 / dirNorm

        var pathLenBark = 0f
        var steadyStepCount = 0
        var forwardStepCount = 0
        var maxSingleStepBark = 0f
        var maxStepIdx = effStartIdx
        val stepDists = FloatArray(anchorCount - 1)
        for (i in 0 until anchorCount - 1) {
            val sb1 = b1Arr[i + 1] - b1Arr[i]
            val sb2 = b2Arr[i + 1] - b2Arr[i]
            stepDists[i] = hypot(sb1, sb2)
        }
        val activeStepCount = (effEndIdx - effStartIdx).coerceAtLeast(1)
        for (i in effStartIdx until effEndIdx) {
            val sb1 = b1Arr[i + 1] - b1Arr[i]
            val sb2 = b2Arr[i + 1] - b2Arr[i]
            val stepDist = stepDists[i]
            pathLenBark += stepDist
            if (stepDist > maxSingleStepBark) {
                maxSingleStepBark = stepDist
                maxStepIdx = i
            }
            if (stepDist < 0.145f) steadyStepCount++
            val forwardProj = sb1 * unitB1 + sb2 * unitB2
            if (forwardProj >= 0.035f) forwardStepCount++
        }
        val steadyStepRatio = steadyStepCount.toFloat() / activeStepCount.toFloat()
        val prevNeighborStep = if (maxStepIdx > effStartIdx) stepDists[maxStepIdx - 1] else 0f
        val nextNeighborStep = if (maxStepIdx < effEndIdx - 1) stepDists[maxStepIdx + 1] else 0f
        // Reject unphysiological 1-frame LPC pole flips where a single isolated step accounts for >= 54% of motion
        // while its immediate neighbors are flat (< 0.14 Bark); do NOT penalize genuine 2-4 step medial glides!
        val isStepArtifact = steadyStepRatio >= 0.50f &&
            pathLenBark > 0.5f &&
            maxSingleStepBark >= 0.50f &&
            (maxSingleStepBark / pathLenBark) >= 0.54f &&
            prevNeighborStep < 0.14f &&
            nextNeighborStep < 0.14f

        var maxInflectionBark = 0f
        var inflectIdx = anchorCount / 2
        for (i in (effStartIdx + 1)..(effEndIdx - 1)) {
            val dist = pointToSegmentDistanceBark(
                px = b2Arr[i], py = b1Arr[i],
                x1 = b2Start, y1 = b1Start,
                x2 = b2End, y2 = b1End
            )
            if (dist > maxInflectionBark) {
                maxInflectionBark = dist
                inflectIdx = i
            }
        }

        val leg1ChordBark = hypot(b1Arr[inflectIdx] - b1Start, b2Arr[inflectIdx] - b2Start)
        val leg2ChordBark = hypot(b1End - b1Arr[inflectIdx], b2End - b2Arr[inflectIdx])
        var leg1Path = 0f
        for (i in effStartIdx until inflectIdx) {
            leg1Path += stepDists[i]
        }
        var leg2Path = 0f
        for (i in inflectIdx until effEndIdx) {
            leg2Path += stepDists[i]
        }
        val leg1Directness = if (leg1Path > 0.05f) leg1ChordBark / leg1Path else 1f
        val leg2Directness = if (leg2Path > 0.05f) leg2ChordBark / leg2Path else 1f

        val fullChordBark = hypot(b1End - b1Start, b2End - b2Start)
        val directnessRatio = if (pathLenBark > 0.05f) (fullChordBark / pathLenBark).coerceIn(0f, 1f) else 1f

        val robustOnsetF1 = smoothF1[effStartIdx]
        val robustOnsetF2 = smoothF2[effStartIdx]
        val robustTargetF1 = smoothF1[effEndIdx]
        val robustTargetF2 = smoothF2[effEndIdx]

        val deltaF1Hz = abs(robustTargetF1 - robustOnsetF1)
        val deltaF2Hz = abs(robustTargetF2 - robustOnsetF2)
        val hasWideArticulatoryShift = deltaF1Hz >= 100 || deltaF2Hz >= 210 || (deltaF1Hz >= 70 && deltaF2Hz >= 150)

        val isGenuineTriphthong = voicedDurMs >= 130 &&
            pathLenBark >= 2.05f &&
            maxInflectionBark >= 0.80f &&
            leg1ChordBark >= 0.88f &&
            leg2ChordBark >= 0.88f &&
            leg1Directness >= 0.62f &&
            leg2Directness >= 0.62f

        val isStandardOrWideDiphthong = voicedDurMs >= 80 &&
            !isStepArtifact &&
            hasWideArticulatoryShift &&
            effectiveNetSpanBark >= 1.18f &&
            coreSpan17Bark >= 0.68f &&
            coreSpanBark >= 0.48f &&
            directnessRatio >= 0.64f

        // Rising diphthongs (后响复韵母: ie [iɛ], ia [ia], ua [ua], uo [uɔ], üe [yɛ] such as 爹 [tiɛ], 些 [ɕiɛ], 多 [tuɔ]):
        // The high vocalic medial ([i/y/u]) glides rapidly during the early 30-45% (anchors [0..4]) and then settles
        // into a long steady main vowel ([ɛ/a/ɔ]) across [4..8], causing central [2..6] span to look small despite a
        // clear multi-step early articulatory opening glide!
        val earlySpan04Bark = hypot(b1Arr[4] - b1Start, b2Arr[4] - b2Start)
        val earlySpanNext4Bark = hypot(b1Arr[4] - b1Arr[(effStartIdx + 1).coerceAtMost(4)], b2Arr[4] - b2Arr[(effStartIdx + 1).coerceAtMost(4)])
        val earlySpan03Bark = hypot(b1Arr[3] - b1Start, b2Arr[3] - b2Start)
        val earlySpanNext3Bark = hypot(b1Arr[3] - b1Arr[(effStartIdx + 1).coerceAtMost(3)], b2Arr[3] - b2Arr[(effStartIdx + 1).coerceAtMost(3)])
        val startsInHighMedialZone = robustOnsetF1 <= 450 && (robustOnsetF2 >= 1750 || robustOnsetF2 <= 1170)
        val opensIntoLowerOrShiftedNucleus = (robustTargetF1 - robustOnsetF1) >= 70 ||
            ((robustTargetF1 - robustOnsetF1) >= 46 && deltaF2Hz >= 170) ||
            deltaF2Hz >= 235
        val hasMultiStepEarlyGlide = (earlySpan04Bark >= 0.74f && earlySpanNext4Bark >= 0.34f) ||
            (earlySpan03Bark >= 0.70f && earlySpanNext3Bark >= 0.32f && stepDists[effStartIdx] >= 0.15f && stepDists[(effStartIdx + 1).coerceAtMost(lastIdx - 1)] >= 0.14f)

        val isRisingMedialDiphthong = voicedDurMs >= 80 &&
            !isStepArtifact &&
            startsInHighMedialZone &&
            opensIntoLowerOrShiftedNucleus &&
            hasMultiStepEarlyGlide &&
            effectiveNetSpanBark >= 0.78f &&
            directnessRatio >= 0.65f

        // Symmetrically, falling diphthongs with a prolonged initial nucleus ([0..4]) and rapid closing offglide ([4..8])
        val lateSpan48Bark = hypot(b1End - b1Arr[4], b2End - b2Arr[4])
        val lateSpan4PrevBark = hypot(b1Arr[(effEndIdx - 1).coerceAtLeast(4)] - b1Arr[4], b2Arr[(effEndIdx - 1).coerceAtLeast(4)] - b2Arr[4])
        val endsInHighOffglideZone = robustTargetF1 <= 460 && (robustTargetF2 >= 1750 || robustTargetF2 <= 1170)
        val closesIntoHighOffglide = (robustOnsetF1 - robustTargetF1) >= 70 ||
            ((robustOnsetF1 - robustTargetF1) >= 46 && deltaF2Hz >= 170)
        val isLateOffglideDiphthong = voicedDurMs >= 85 &&
            !isStepArtifact &&
            endsInHighOffglideZone &&
            closesIntoHighOffglide &&
            lateSpan48Bark >= 0.74f &&
            lateSpan4PrevBark >= 0.34f &&
            effectiveNetSpanBark >= 0.78f &&
            directnessRatio >= 0.65f

        // Narrow closing diphthongs ([ou], [ei]) move 1 height tier continuously across the entire vowel core:
        // require unidirectional forward steps, high linearity (>= 0.78), and sustained interior motion.
        val hasOpposingArticulatoryGlide = (rawDb1 * rawDb2 < 0f) &&
            abs(rawDb1) >= 0.36f &&
            abs(rawDb2) >= 0.38f &&
            deltaF1Hz >= 52 &&
            deltaF2Hz >= 158

        val isCoherentNarrowDiphthong = voicedDurMs >= 90 &&
            !isStepArtifact &&
            directnessRatio >= 0.76f &&
            (
                (effectiveNetSpanBark >= 0.94f &&
                    coreSpan17Bark >= 0.68f &&
                    coreSpanBark >= 0.42f &&
                    forwardStepCount >= 6 &&
                    (deltaF2Hz >= 160 || (adjDb1Mag >= 0.72f && deltaF1Hz >= 90))) ||
                (hasOpposingArticulatoryGlide &&
                    effectiveNetSpanBark >= 0.60f &&
                    coreSpan17Bark >= 0.45f &&
                    coreSpanBark >= 0.30f &&
                    forwardStepCount >= 6)
            )

        val isGenuineDiphthong = isStandardOrWideDiphthong ||
            isRisingMedialDiphthong ||
            isLateOffglideDiphthong ||
            isCoherentNarrowDiphthong

        val vowelType = when {
            isGenuineTriphthong -> VowelGlideType.TRIPHTHONG
            isGenuineDiphthong -> VowelGlideType.DIPHTHONG
            else -> VowelGlideType.MONOPHTHONG
        }

        // Steady-state median of central 5 anchors [2..6] for monophthongs vs Onset/Nucleus/Target for glides
        val midIndices = listOf(2, 3, 4, 5, 6)
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
                reportedGlideSpan = effectiveNetSpanBark
            }
            VowelGlideType.DIPHTHONG -> {
                onsetF1 = robustOnsetF1
                onsetF2 = robustOnsetF2
                nucleusF1 = smoothF1[anchorCount / 2]
                nucleusF2 = smoothF2[anchorCount / 2]
                nucleusF3 = smoothF3[anchorCount / 2]
                targetF1 = robustTargetF1
                targetF2 = robustTargetF2
                reportedGlideSpan = rawNetSpanBark
            }
            VowelGlideType.TRIPHTHONG -> {
                onsetF1 = robustOnsetF1
                onsetF2 = robustOnsetF2
                nucleusF1 = smoothF1[inflectIdx]
                nucleusF2 = smoothF2[inflectIdx]
                nucleusF3 = smoothF3[inflectIdx]
                targetF1 = robustTargetF1
                targetF2 = robustTargetF2
                reportedGlideSpan = pathLenBark
            }
        }

        // Angle in IPA vowel chart screen space (left = higher F2, down = higher F1)
        val chartDx = -(b2End - b2Start)
        val chartDy = (b1End - b1Start)
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
        val order = if (isHighRegister) 10 else 11

        var maxRms = 1e-6
        val frameRms = DoubleArray(numPts)
        val frameZcr = DoubleArray(numPts)
        val starts = IntArray(numPts) { idx ->
            val frac = 0.10 + 0.80 * (idx.toDouble() / (numPts - 1.0))
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
            if (frameRms[i] >= maxRms * 0.18 && frameZcr[i] <= 0.25) {
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

    /**
     * Solves all complex poles z_i of the LPC inverse filter polynomial
     * P(z) = z^p - a[1]*z^{p-1} - ... - a[p] = 0 via simultaneous Durand-Kerner iteration.
     * Complex root solving separates closely-spaced resonances (e.g. back rounded [u, o, ɑ])
     * even when their spectral envelopes merge into a single peak.
     */
    private fun solveLpcComplexPolesDurandKerner(
        a: DoubleArray,
        order: Int,
        fsLpc: Int
    ): List<FormantPeakCand> {
        if (order < 4) return emptyList()
        val rootsRe = DoubleArray(order)
        val rootsIm = DoubleArray(order)
        val initRadius = 0.885
        for (k in 0 until order) {
            val angle = 2.0 * Math.PI * (k + 0.37) / order.toDouble()
            rootsRe[k] = initRadius * cos(angle)
            rootsIm[k] = initRadius * sin(angle)
        }

        val maxIter = 52
        for (iter in 0 until maxIter) {
            var maxDeltaSq = 0.0
            for (i in 0 until order) {
                val zRe = rootsRe[i]
                val zIm = rootsIm[i]

                // Horner evaluation of P(z) = z^p - a[1]*z^{p-1} - ... - a[p]
                var pRe = 1.0
                var pIm = 0.0
                for (m in 1..order) {
                    val nextRe = pRe * zRe - pIm * zIm - a[m]
                    val nextIm = pRe * zIm + pIm * zRe
                    pRe = nextRe
                    pIm = nextIm
                }

                // Denominator product prod_{j != i} (z_i - z_j)
                var dRe = 1.0
                var dIm = 0.0
                for (j in 0 until order) {
                    if (j == i) continue
                    var diffRe = zRe - rootsRe[j]
                    var diffIm = zIm - rootsIm[j]
                    if (abs(diffRe) < 1e-12 && abs(diffIm) < 1e-12) {
                        diffRe = 1e-7 * (i - j)
                        diffIm = 1e-7
                    }
                    val nextDRe = dRe * diffRe - dIm * diffIm
                    val nextDIm = dRe * diffIm + dIm * diffRe
                    dRe = nextDRe
                    dIm = nextDIm
                }

                val dMagSq = dRe * dRe + dIm * dIm
                if (dMagSq <= 1e-24 || !dMagSq.isFinite()) continue
                val stepRe = (pRe * dRe + pIm * dIm) / dMagSq
                val stepIm = (pIm * dRe - pRe * dIm) / dMagSq
                rootsRe[i] -= stepRe
                rootsIm[i] -= stepIm
                val deltaSq = stepRe * stepRe + stepIm * stepIm
                if (deltaSq > maxDeltaSq) maxDeltaSq = deltaSq
            }
            if (maxDeltaSq < 1e-16) break
        }

        val poles = ArrayList<FormantPeakCand>(order / 2 + 1)
        for (i in 0 until order) {
            val re = rootsRe[i]
            val im = rootsIm[i]
            if (!re.isFinite() || !im.isFinite() || im <= 1e-4) continue
            val radius = hypot(re, im)
            if (radius !in 0.52..0.9995) continue

            val theta = atan2(im, re)
            val freqHz = (theta * fsLpc / (2.0 * Math.PI)).roundToInt()
            if (freqHz !in 185..4350) continue
            val bandwidthHz = (-ln(radius) * fsLpc / Math.PI).coerceAtLeast(28.0)
            if (bandwidthHz > 740.0) continue

            var aRe = 1.0
            var aIm = 0.0
            for (k in 1..order) {
                aRe -= a[k] * cos(theta * k)
                aIm += a[k] * sin(theta * k)
            }
            val magSq = (aRe * aRe + aIm * aIm).coerceAtLeast(1e-10)
            val powerDb = 10.0 * log10(1.0 / magSq)
            val sharpnessProminence = (14.0 * (1.0 - (bandwidthHz / 740.0).coerceIn(0.0, 1.0))).coerceAtLeast(1.0)
            poles.add(FormantPeakCand(freqHz, bandwidthHz, powerDb, sharpnessProminence))
        }

        return poles.sortedBy { it.freqHz }
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
        // Praat-style Gaussian-like window + 50 Hz pre-emphasis
        val sig = DoubleArray(winLen)
        val half = (winLen - 1) * 0.5
        for (i in 0 until winLen) {
            val curr = decimated[frameStart + i]
            val prev = decimated[(frameStart + i - 1).coerceAtLeast(0)]
            val pre = curr - preEmphAlpha * prev
            val x = (i - half) / half.coerceAtLeast(1.0)
            val gaussWin = exp(-0.5 * (2.35 * x) * (2.35 * x))
            sig[i] = pre * gaussWin
        }

        val r = DoubleArray(order + 1)
        for (lag in 0..order) {
            var sum = 0.0
            for (i in 0 until winLen - lag) {
                sum += sig[i] * sig[i + lag]
            }
            r[lag] = sum * (0.997).pow(lag.toDouble())
        }
        if (r[0] <= 1e-10) return emptyList()

        val a = DoubleArray(order + 1)
        val aPrev = DoubleArray(order + 1)
        var error = r[0]
        var effectiveOrder = 0
        for (i in 1..order) {
            var lambda = 0.0
            for (j in 1 until i) lambda += aPrev[j] * r[i - j]
            val k = ((r[i] - lambda) / error).coerceIn(-0.999, 0.999)
            a[i] = k
            for (j in 1 until i) a[j] = aPrev[j] - k * aPrev[i - j]
            for (j in 1..i) aPrev[j] = a[j]
            error *= (1.0 - k * k)
            effectiveOrder = i
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
            for (k in 1..effectiveOrder) {
                re -= a[k] * cos(omega * k)
                im += a[k] * sin(omega * k)
            }
            val magSq = (re * re + im * im).coerceAtLeast(1e-10)
            envPowerDb[b] = 10.0 * log10(1.0 / magSq)
        }

        // Combine exact complex polynomial poles (Durand-Kerner) with any distinct spectral peaks
        val rootPoles = solveLpcComplexPolesDurandKerner(a, effectiveOrder, fsLpc)
        val specPeaks = extractSpectralPeaks(envPowerDb, minFreq, freqStep, numBins)
        val mergedPoles = ArrayList<FormantPeakCand>(rootPoles.size + specPeaks.size)
        mergedPoles.addAll(rootPoles)
        for (sp in specPeaks) {
            if (rootPoles.none { abs(it.freqHz - sp.freqHz) <= 85 }) {
                mergedPoles.add(sp)
            }
        }
        val peaks = mergedPoles.sortedBy { it.freqHz }

        val f1Floor = if (isHighRegister) 235 else if (isMidRegister) 220 else 210
        val f1Ceil = if (isHighRegister) 1040 else if (isMidRegister) 980 else 920
        val f0VoiceBarLimit = (syllableMeanF0 * 1.15f).roundToInt().coerceIn(180, 285)

        // Check low-vs-open vowel DFT energy ratio (with 1/f^2 compensation for pre-emphasis +6 dB/oct tilt)
        // so strong H1 doesn't pull [a/ɛ/ɔ] F1 down to F0, while back-vowel [u/o] F2 (650..980 Hz) doesn't masquerade as open F1.
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
            val tiltComp = (400.0 * 400.0) / (fTest.toDouble() * fTest.toDouble())
            val p = (re * re + im * im) * tiltComp
            if (fTest <= 395) {
                closePower += p * (if (fTest <= f0VoiceBarLimit) 0.30 else 1.0)
                closeCnt++
            } else if (fTest >= 440) {
                openPower += p
                openCnt++
            }
            fTest += 35
        }
        val rawMidOrOpen = (openPower / openCnt.coerceAtLeast(1)) > (closePower / closeCnt.coerceAtLeast(1)) * 0.85

        // Physical two-pole disambiguation: if a genuine vocal-tract resonance pole already exists in
        // (f0VoiceBarLimit + 20)..430 Hz alongside a second pole >= +250 Hz higher (e.g. [u/o/ou] with F1 ~320 Hz, F2 ~760 Hz),
        // the lower pole is F1 and any pole >= lowerPole + 250 Hz is F2 (cannot be F1).
        val lowResonantPole = peaks.firstOrNull { p ->
            p.freqHz in (f0VoiceBarLimit + 20)..430 &&
                p.bandwidthHz <= 360.0 &&
                p.prominenceDb >= 1.2
        }
        val hasBackTwoPoleSignature = lowResonantPole != null && peaks.any { p2 ->
            p2.freqHz in (lowResonantPole.freqHz + 240)..1120 &&
                p2.bandwidthHz <= 560.0 &&
                lowResonantPole.powerDb >= p2.powerDb - 8.5
        }
        val isMidOrOpen = rawMidOrOpen && !hasBackTwoPoleSignature

        fun higherFormantAsF1Penalty(cand: FormantPeakCand): Double {
            val hasValidLowerF1 = peaks.any { lower ->
                lower.freqHz in (f0VoiceBarLimit + 20)..580 &&
                    cand.freqHz >= lower.freqHz + 250 &&
                    lower.bandwidthHz <= 360.0 &&
                    lower.prominenceDb >= 1.4 &&
                    lower.powerDb >= cand.powerDb - 8.5
            }
            return if (hasValidLowerF1) -7.0 else 0.0
        }

        val f1Pool = peaks.filter { it.freqHz in f1Floor..f1Ceil && it.bandwidthHz <= 500.0 }
            .ifEmpty { peaks.filter { it.freqHz in f1Floor..f1Ceil && it.bandwidthHz <= 720.0 } }
            .sortedByDescending { p ->
                val openBonus = if (isMidOrOpen && p.freqHz >= 395) 4.2 else if (!isMidOrOpen && p.freqHz <= 430) 3.8 else 0.0
                val voiceBarPenalty = if (isMidOrOpen && p.freqHz <= f0VoiceBarLimit) -5.5 else 0.0
                p.powerDb * 0.55 + p.prominenceDb * 1.25 - 0.020 * p.bandwidthHz + openBonus + voiceBarPenalty + higherFormantAsF1Penalty(p)
            }
            .take(3)
            .ifEmpty {
                val fallbackF1 = if (isMidOrOpen) 560 else 310
                listOf(FormantPeakCand(fallbackF1, 160.0, 0.0, 2.0))
            }

        val tuples = ArrayList<AnchorTupleCand>(9)
        for (f1Cand in f1Pool) {
            val f1 = f1Cand.freqHz
            // Back close rounded vowels [u, o] routinely have F2 in 560..850 Hz even in female voices!
            val f2Min = maxOf(560, f1 + 180)
            val f2Max = (2880 - ((f1 - 260).coerceAtLeast(0) * 1.20).toInt())
                .coerceIn(1550, if (isHighRegister) 2950 else 2700)

            val f2Pool = peaks.filter { it.freqHz in f2Min..f2Max && it.bandwidthHz <= 720.0 }
                .sortedByDescending { it.powerDb * 0.55 + it.prominenceDb * 1.30 - 0.016 * it.bandwidthHz }
                .take(3)
                .ifEmpty {
                    // Locate actual spectral maximum/shoulder in [f2Min..f2Max] — NEVER jump to a hardcoded 1650 Hz!
                    val envBestF2 = findShoulderFreq(envPowerDb, minFreq, freqStep, numBins, f2Min, f2Max)
                    listOf(FormantPeakCand(envBestF2, 240.0, -3.0, 1.0))
                }

            for (f2Cand in f2Pool) {
                val f2 = f2Cand.freqHz
                val f3Min = maxOf(f2 + 300, if (isHighRegister) 2100 else 1880)
                val f3Max = if (isHighRegister) 4150 else 3800
                val f3Best = peaks.filter { it.freqHz in f3Min..f3Max }
                    .maxByOrNull { it.powerDb * 0.5 + it.prominenceDb * 1.1 - 0.014 * it.bandwidthHz }
                    ?.freqHz ?: (if (isHighRegister) 2950 else 2620).coerceAtLeast(f2 + 360)

                val openBonus = if (isMidOrOpen && f1 >= 395) 3.8 else if (!isMidOrOpen && f1 <= 430) 3.4 else -1.2
                val voiceBarPenalty = if (isMidOrOpen && f1 <= f0VoiceBarLimit) -5.0 else 0.0
                val emitScore = (f1Cand.powerDb * 0.42 + f1Cand.prominenceDb * 1.25 - 0.016 * f1Cand.bandwidthHz + openBonus + voiceBarPenalty + higherFormantAsF1Penalty(f1Cand)) +
                    (f2Cand.powerDb * 0.42 + f2Cand.prominenceDb * 1.30 - 0.014 * f2Cand.bandwidthHz)
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
                    // Strong quadratic jump penalty eliminates abrupt pole-hopping (> 0.6 Bark/step)
                    // while allowing continuous diphthong glides (~0.20..0.42 Bark/step across 9 anchors).
                    val jumpCost = 4.6 * db1 * db1 + 3.8 * db2 * db2 + 0.9 * db3 * db3 + 1.8 * (db1 + db2)
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
