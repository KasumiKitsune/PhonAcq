package com.example.audio

import com.example.i18n.S
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detailed acoustic quality & boundary truncation diagnostic result computed directly from a 16-bit PCM WAV file.
 * Used uniformly across local recording, wireless peer sync (`ingestRemoteRecordedWav`),
 * wireless local pre-check, and multi-take switching (`selectTakeForWord`).
 */
data class SpeechQualityReport(
    val primaryStatus: String,
    val allIssues: List<String>,
    val peakAmplitude: Int,
    val peakDbfs: Float,
    val avgSpeechRms: Float,
    val speechP85Dbfs: Float,
    val noiseFloorDbfs: Float,
    val snrDb: Float,
    val totalDurationMs: Int,
    val activeSpeechDurationMs: Int,
    val onsetMarginMs: Int,
    val offsetMarginMs: Int,
    val clippedSampleCount: Int,
    val flatTopRunCount: Int
)

/**
 * Speaker-friendly natural language coaching & investigator technical summary mapper.
 */
data class AudioCoachingMessages(
    val qualityStatus: String,
    val speakerNaturalHint: String?,
    val controllerTechnicalHint: String?
)

object NaturalAudioCoach {

    /**
     * Translates a technical [qualityStatus] into warm, non-jargon spoken guidance for the speaker
     * on 看读端 (and a concise acoustic diagnostic for the investigator on 遥控端).
     *
     * Note: `"嘎裂声"` intentionally produces `null` for [AudioCoachingMessages.speakerNaturalHint]
     * because tonal dialects frequently exhibit natural physiological creaky phonation in low/falling
     * or checked tones; prompting a speaker about creakiness would distort authentic dialect values.
     */
    fun fromStatus(
        qualityStatus: String?,
        recordedByPromptSelfControl: Boolean
    ): AudioCoachingMessages? {
        val status = qualityStatus?.trim().orEmpty()
        if (status.isEmpty() || status == "正常") return null

        return when (status) {
            "削波/过载", "过载/破音" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = S.quality.coachClipSpeaker,
                controllerTechnicalHint = S.quality.coachClipController
            )

            "音量过低", "声音偏小" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = S.quality.coachLowSpeaker,
                controllerTechnicalHint = S.quality.coachLowController
            )

            "开头截断" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = if (recordedByPromptSelfControl) {
                    S.quality.coachOnsetCutSpeakerSelf
                } else {
                    S.quality.coachOnsetCutSpeakerRemote
                },
                controllerTechnicalHint = S.quality.coachOnsetCutController
            )

            "收尾截断" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = if (recordedByPromptSelfControl) {
                    S.quality.coachOffsetCutSpeakerSelf
                } else {
                    S.quality.coachOffsetCutSpeakerRemote
                },
                controllerTechnicalHint = S.quality.coachOffsetCutController
            )

            "起止截断" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = if (recordedByPromptSelfControl) {
                    S.quality.coachBothCutSpeakerSelf
                } else {
                    S.quality.coachBothCutSpeakerRemote
                },
                controllerTechnicalHint = S.quality.coachBothCutController
            )

            "极短/空音" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = S.quality.coachEmptySpeaker,
                controllerTechnicalHint = S.quality.coachEmptyController
            )

            "底噪偏高" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = S.quality.coachNoiseSpeaker,
                controllerTechnicalHint = S.quality.coachNoiseController
            )

            "嘎裂声" -> AudioCoachingMessages(
                qualityStatus = status,
                speakerNaturalHint = null, // Never disturb speaker for dialectal creakiness
                controllerTechnicalHint = S.quality.coachCreakyController
            )

            else -> null
        }
    }
}

/**
 * Unified, pure-Kotlin WAV quality and phoneme boundary truncation detector:
 *  - Scans 10ms frames with 5ms hop (DC-offset removed)
 *  - Extracts wideband dBFS + 1st-order pre-emphasized high-frequency dBFS + zero-crossing rate
 *    so voiceless sibilants/aspirates ([s, ɕ, f, tʰ]) and weak nasal/falling-tone codas (-m, -n, -ŋ, 51/21)
 *    are accurately tracked relative to the 15th-percentile adaptive room noise floor.
 */
object SpeechQualityEvaluator {

    /**
     * Returns true if [status] represents an objective capture defect that should pause
     * press-and-hold auto-advance when `holdOnQualityIssue` is enabled.
     * Note: `"嘎裂声"` is intentionally excluded so dialectal creaky tones do not interrupt flow.
     */
    fun isBlockingForAutoAdvance(status: String?): Boolean {
        return when (status) {
            "削波/过载",
            "过载/破音",
            "开头截断",
            "收尾截断",
            "起止截断",
            "极短/空音",
            "音量过低",
            "底噪偏高" -> true
            else -> false
        }
    }

    fun evaluate(
        wavFile: File,
        lowVolumeRmsThreshold: Float = 250f
    ): SpeechQualityReport {
        val parsed = readMono16BitWav(wavFile)
            ?: return emptyReport("极短/空音")

        val samples = parsed.samples
        val sampleRate = parsed.sampleRate.coerceIn(8000, 192000)
        if (samples.isEmpty()) return emptyReport("极短/空音")

        val totalDurationMs = ((samples.size * 1000L) / sampleRate).toInt()

        // 1. Sample-level peak, saturation, and hardware hard-limiter flat-top scan
        var maxAbsSample = 0
        var clippedSamples = 0
        var flatTopRuns = 0
        var currentFlatRun = 1

        for (i in samples.indices) {
            val v = samples[i].toInt()
            val absV = abs(v)
            if (absV > maxAbsSample) maxAbsSample = absV
            if (absV >= 32500) {
                clippedSamples++
            }
            if (i > 0) {
                val prev = samples[i - 1].toInt()
                // Detect sustained flat-topping near full scale or hardware limiter ceiling (>= -0.9 dBFS)
                if (absV >= 29500 && abs(v - prev) <= 2 && ((v > 0 && prev > 0) || (v < 0 && prev < 0))) {
                    currentFlatRun++
                    if (currentFlatRun == 3) {
                        flatTopRuns++
                    }
                } else {
                    currentFlatRun = 1
                }
            }
        }

        val peakDbfs = amplitudeToDbfs(maxAbsSample.toFloat())
        if (totalDurationMs < 220) {
            val status = if (clippedSamples >= 8 || flatTopRuns >= 2 || maxAbsSample >= 32600) {
                "削波/过载"
            } else {
                "极短/空音"
            }
            return SpeechQualityReport(
                primaryStatus = status,
                allIssues = listOf(status),
                peakAmplitude = maxAbsSample,
                peakDbfs = peakDbfs,
                avgSpeechRms = 0f,
                speechP85Dbfs = -60f,
                noiseFloorDbfs = -60f,
                snrDb = 0f,
                totalDurationMs = totalDurationMs,
                activeSpeechDurationMs = 0,
                onsetMarginMs = 0,
                offsetMarginMs = 0,
                clippedSampleCount = clippedSamples,
                flatTopRunCount = flatTopRuns
            )
        }

        // 2. Short-time 10ms window / 5ms hop multi-feature extraction
        val winLen = ((sampleRate * 10) / 1000).coerceAtLeast(80)
        val hopLen = ((sampleRate * 5) / 1000).coerceAtLeast(40)
        val numFrames = if (samples.size >= winLen) {
            1 + (samples.size - winLen) / hopLen
        } else {
            1
        }

        val frameRms = FloatArray(numFrames)
        val frameDbfs = FloatArray(numFrames)
        val frameHfDbfs = FloatArray(numFrames)
        val frameZcrPerMs = FloatArray(numFrames)
        val frameDurationMs = (winLen * 1000f) / sampleRate

        var peakFrameDbfs = -90f
        var peakHfDbfs = -90f

        for (f in 0 until numFrames) {
            val start = f * hopLen
            val end = min(samples.size, start + winLen)
            val count = (end - start).coerceAtLeast(1)

            // Remove local DC offset so mic bias never masquerades as tail energy
            var sum = 0.0
            for (i in start until end) {
                sum += samples[i].toInt()
            }
            val dcMean = (sum / count).toFloat()

            var sqSum = 0.0
            var hfSqSum = 0.0
            var zeroCrossings = 0
            var prevCentered = samples[start].toFloat() - dcMean

            for (i in start until end) {
                val centered = samples[i].toFloat() - dcMean
                sqSum += centered.toDouble() * centered.toDouble()

                val preEmph = if (i == start) {
                    val prevRaw = if (start > 0) samples[start - 1].toFloat() - dcMean else centered
                    centered - 0.97f * prevRaw
                } else {
                    centered - 0.97f * prevCentered
                }
                hfSqSum += preEmph.toDouble() * preEmph.toDouble()

                if (i > start && ((centered >= 0f && prevCentered < 0f) || (centered < 0f && prevCentered >= 0f))) {
                    zeroCrossings++
                }
                prevCentered = centered
            }

            val rms = sqrt(sqSum / count).toFloat()
            val hfRms = sqrt(hfSqSum / count).toFloat()
            val db = amplitudeToDbfs(rms)
            val hfDb = amplitudeToDbfs(hfRms)

            frameRms[f] = rms
            frameDbfs[f] = db
            frameHfDbfs[f] = hfDb
            frameZcrPerMs[f] = zeroCrossings / frameDurationMs.coerceAtLeast(1f)

            if (db > peakFrameDbfs) peakFrameDbfs = db
            if (hfDb > peakHfDbfs) peakHfDbfs = hfDb
        }

        // 3. Adaptive 15th-percentile room noise floor
        val sortedDb = frameDbfs.copyOf().apply { sort() }
        val sortedHfDb = frameHfDbfs.copyOf().apply { sort() }
        val p15Index = ((numFrames - 1) * 0.15f).toInt().coerceIn(0, numFrames - 1)
        val noiseFloorDbfs = sortedDb[p15Index].coerceIn(-72f, -28f)
        val hfNoiseFloorDbfs = sortedHfDb[p15Index].coerceIn(-75f, -30f)

        // 4. Dual-threshold speech activity mask (vowels/nasals + high-freq voiceless consonants)
        val widebandGateDb = maxOf(noiseFloorDbfs + 9.5f, peakFrameDbfs - 27.0f, -49.0f)
        val fricativeGateDb = maxOf(hfNoiseFloorDbfs + 10.5f, peakHfDbfs - 22.0f, -47.0f)

        val rawMask = BooleanArray(numFrames)
        for (f in 0 until numFrames) {
            val voicedOrSonorant = frameDbfs[f] >= widebandGateDb
            val voicelessFricative = frameHfDbfs[f] >= fricativeGateDb && frameZcrPerMs[f] in 1.9f..11.5f
            rawMask[f] = voicedOrSonorant || voicelessFricative
        }

        // Suppress isolated single-frame electrical clicks (5ms) in the interior
        val despikedMask = rawMask.copyOf()
        for (f in 1 until numFrames - 1) {
            if (despikedMask[f] && !rawMask[f - 1] && !rawMask[f + 1]) {
                despikedMask[f] = false
            }
        }

        // Morphological closing: bridge short intra-word stop closures (up to 80ms = 16 frames at 5ms hop)
        val closedMask = despikedMask.copyOf()
        val maxBridgeFrames = (80 * 1000 / (hopLen * 1000_000 / sampleRate).coerceAtLeast(1)).coerceIn(4, 20)
        var lastActive = -1
        for (f in 0 until numFrames) {
            if (despikedMask[f]) {
                if (lastActive >= 0 && (f - lastActive) in 2..maxBridgeFrames) {
                    for (k in (lastActive + 1) until f) {
                        closedMask[k] = true
                    }
                }
                lastActive = f
            }
        }

        // Filter out tiny isolated button-tap transients (< 20ms = 4 frames) separated from main speech
        val cleanMask = filterShortDetachedIslands(closedMask, minFrames = 4)

        var firstSpeechFrame = -1
        var lastSpeechFrame = -1
        var activeFrameCount = 0
        val activeDbList = ArrayList<Float>(numFrames)
        var activeRmsSum = 0.0

        for (f in 0 until numFrames) {
            if (cleanMask[f]) {
                if (firstSpeechFrame < 0) firstSpeechFrame = f
                lastSpeechFrame = f
                activeFrameCount++
                activeDbList.add(frameDbfs[f])
                activeRmsSum += frameRms[f]
            }
        }

        val hopMs = (hopLen * 1000f) / sampleRate
        val winMs = (winLen * 1000f) / sampleRate
        val activeSpeechDurationMs = if (firstSpeechFrame >= 0 && lastSpeechFrame >= firstSpeechFrame) {
            ((lastSpeechFrame - firstSpeechFrame) * hopMs + winMs).toInt()
        } else {
            0
        }

        val onsetMarginMs = if (firstSpeechFrame >= 0) {
            (firstSpeechFrame * hopMs).toInt()
        } else {
            totalDurationMs
        }
        val offsetEndMs = if (lastSpeechFrame >= 0) {
            lastSpeechFrame * hopMs + winMs
        } else {
            0f
        }
        val offsetMarginMs = (totalDurationMs - offsetEndMs).toInt().coerceAtLeast(0)

        activeDbList.sort()
        val speechP85Dbfs = if (activeDbList.isNotEmpty()) {
            val idx85 = ((activeDbList.size - 1) * 0.85f).toInt().coerceIn(0, activeDbList.size - 1)
            activeDbList[idx85]
        } else {
            peakFrameDbfs
        }
        val avgSpeechRms = if (activeFrameCount > 0) {
            (activeRmsSum / activeFrameCount).toFloat()
        } else {
            frameRms.average().toFloat()
        }
        val snrDb = (speechP85Dbfs - noiseFloorDbfs).coerceAtLeast(0f)

        // 5. Boundary Truncation Evaluation
        // 5A. Onset truncation ("开头截断"): speech/fricative is already hot in the very first 20ms
        val headCount = min(3, numFrames)
        var headMaxDb = -90f
        var headMeanDb = 0f
        var headMaxHfDb = -90f
        var headMaxZcr = 0f
        for (i in 0 until headCount) {
            headMeanDb += frameDbfs[i]
            if (frameDbfs[i] > headMaxDb) headMaxDb = frameDbfs[i]
            if (frameHfDbfs[i] > headMaxHfDb) headMaxHfDb = frameHfDbfs[i]
            if (frameZcrPerMs[i] > headMaxZcr) headMaxZcr = frameZcrPerMs[i]
        }
        headMeanDb /= headCount.coerceAtLeast(1)

        val onsetEnergyHot = headMeanDb >= maxOf(noiseFloorDbfs + 11.5f, peakFrameDbfs - 22.0f, -42.5f) ||
            (headMaxDb >= maxOf(noiseFloorDbfs + 14.0f, peakFrameDbfs - 18.0f, -39.0f) && frameDbfs[0] >= noiseFloorDbfs + 9.5f)
        val onsetFricativeHot = headMaxHfDb >= maxOf(hfNoiseFloorDbfs + 12.0f, peakHfDbfs - 18.0f, -41.5f) &&
            headMaxZcr >= 2.1f && frameHfDbfs[0] >= hfNoiseFloorDbfs + 9.5f

        val isOnsetTruncated = activeSpeechDurationMs >= 80 &&
            onsetMarginMs <= 20 &&
            (onsetEnergyHot || onsetFricativeHot)

        // 5B. Offset truncation ("收尾截断"):
        // Case 1: Speech/nasal coda/falling tone extends right to the last <= 30ms without decaying to room floor
        val tailCount = min(4, numFrames)
        var tailMeanDb = 0f
        var tailMaxHfDb = -90f
        var tailMeanZcr = 0f
        for (i in (numFrames - tailCount) until numFrames) {
            tailMeanDb += frameDbfs[i]
            if (frameHfDbfs[i] > tailMaxHfDb) tailMaxHfDb = frameHfDbfs[i]
            tailMeanZcr += frameZcrPerMs[i]
        }
        tailMeanDb /= tailCount.coerceAtLeast(1)
        tailMeanZcr /= tailCount.coerceAtLeast(1)

        // Guard against a brief mechanical touch/button release click in the very last 15ms
        // preceded by >= 60ms of clean silence: cleanMask already removed detached < 20ms islands,
        // so offsetMarginMs <= 30 guarantees the continuous speech body itself reached the end!
        val tailSonorantHot = tailMeanDb >= maxOf(noiseFloorDbfs + 8.5f, peakFrameDbfs - 25.0f, -44.0f)
        val tailFricativeHot = tailMaxHfDb >= maxOf(hfNoiseFloorDbfs + 11.0f, peakHfDbfs - 20.0f, -43.0f) &&
            tailMeanZcr >= 2.1f

        // Case 2: Abrupt cliff cut within the last 35ms (e.g., hardware stop zeroed the final frame while vowel/coda was active)
        var preEndMaxDb = -90f
        val cliffStart = (numFrames - 7).coerceAtLeast(0)
        val cliffEnd = (numFrames - 2).coerceAtLeast(0)
        for (i in cliffStart..cliffEnd) {
            if (frameDbfs[i] > preEndMaxDb) preEndMaxDb = frameDbfs[i]
        }
        val abruptCliffAtEnd = offsetMarginMs <= 35 &&
            preEndMaxDb >= maxOf(noiseFloorDbfs + 13.5f, peakFrameDbfs - 19.0f, -38.0f) &&
            (preEndMaxDb - frameDbfs[numFrames - 1]) >= 13.0f &&
            cleanMask[cliffEnd]

        val isOffsetTruncated = activeSpeechDurationMs >= 80 &&
            ((offsetMarginMs <= 30 && (tailSonorantHot || tailFricativeHot)) || abruptCliffAtEnd)

        // 6. Ordered multi-issue collection & highest-priority primary status
        val issues = mutableListOf<String>()

        val isEmptyOrTooShort = totalDurationMs < 240 ||
            activeSpeechDurationMs < 75 ||
            ((peakFrameDbfs - noiseFloorDbfs) < 6.5f && maxAbsSample < 950)

        if (isEmptyOrTooShort) {
            issues.add("极短/空音")
        }

        val isClipped = clippedSamples >= 8 || flatTopRuns >= 2 || maxAbsSample >= 32600
        if (isClipped) {
            issues.add("削波/过载")
        }

        if (!isEmptyOrTooShort) {
            if (isOnsetTruncated) {
                issues.add("开头截断")
            }
            if (isOffsetTruncated) {
                issues.add("收尾截断")
            }

            // Calibrate user's RMS threshold (default 250f) to speech P85 dBFS threshold (~ -34.5 dBFS at default)
            val calibratedCoreMinDb = amplitudeToDbfs(lowVolumeRmsThreshold * 2.45f).coerceIn(-44f, -26f)
            val isTooQuiet = speechP85Dbfs < calibratedCoreMinDb ||
                avgSpeechRms < lowVolumeRmsThreshold * 0.95f ||
                maxAbsSample < (lowVolumeRmsThreshold * 2.1f).toInt()

            if (isTooQuiet) {
                issues.add("音量过低")
            } else if (noiseFloorDbfs > -35.5f && snrDb < 11.5f) {
                issues.add("底噪偏高")
            }
        }

        val primary = issues.firstOrNull() ?: "正常"

        return SpeechQualityReport(
            primaryStatus = primary,
            allIssues = issues,
            peakAmplitude = maxAbsSample,
            peakDbfs = peakDbfs,
            avgSpeechRms = avgSpeechRms,
            speechP85Dbfs = speechP85Dbfs,
            noiseFloorDbfs = noiseFloorDbfs,
            snrDb = snrDb,
            totalDurationMs = totalDurationMs,
            activeSpeechDurationMs = activeSpeechDurationMs,
            onsetMarginMs = onsetMarginMs,
            offsetMarginMs = offsetMarginMs,
            clippedSampleCount = clippedSamples,
            flatTopRunCount = flatTopRuns
        )
    }

    private fun filterShortDetachedIslands(mask: BooleanArray, minFrames: Int): BooleanArray {
        if (mask.isEmpty()) return mask
        val out = mask.copyOf()
        val runs = ArrayList<IntRange>()
        var idx = 0
        while (idx < out.size) {
            if (out[idx]) {
                val start = idx
                while (idx < out.size && out[idx]) idx++
                runs.add(start until idx)
            } else {
                idx++
            }
        }
        if (runs.size <= 1) return out

        val longestLen = runs.maxOf { it.last - it.first + 1 }
        if (longestLen < minFrames) return out

        for (r in runs) {
            val len = r.last - r.first + 1
            if (len < minFrames) {
                for (k in r) out[k] = false
            }
        }
        return out
    }

    private fun amplitudeToDbfs(amp: Float): Float {
        val norm = (amp / 32768f).coerceAtLeast(1e-5f)
        return (20.0 * log10(norm.toDouble())).toFloat().coerceIn(-90f, 0f)
    }

    fun dbfsToAmplitude(dbfs: Float): Int {
        return (10.0.pow(dbfs.toDouble() / 20.0) * 32768.0).toInt().coerceIn(0, 32767)
    }

    private fun emptyReport(status: String) = SpeechQualityReport(
        primaryStatus = status,
        allIssues = listOf(status),
        peakAmplitude = 0,
        peakDbfs = -90f,
        avgSpeechRms = 0f,
        speechP85Dbfs = -90f,
        noiseFloorDbfs = -90f,
        snrDb = 0f,
        totalDurationMs = 0,
        activeSpeechDurationMs = 0,
        onsetMarginMs = 0,
        offsetMarginMs = 0,
        clippedSampleCount = 0,
        flatTopRunCount = 0
    )

    private class ParsedWavData(
        val sampleRate: Int,
        val samples: ShortArray
    )

    private fun readMono16BitWav(file: File): ParsedWavData? {
        if (!file.exists() || file.length() <= 44L) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                val headerHead = ByteArray(12)
                if (raf.read(headerHead) < 12) return null
                val riff = String(headerHead, 0, 4, Charsets.US_ASCII)
                val wave = String(headerHead, 8, 4, Charsets.US_ASCII)
                if (riff != "RIFF" || wave != "WAVE") return null

                var sampleRate = 44100
                var channels = 1
                var bitsPerSample = 16
                var dataOffset = 44L
                var dataByteLen = (fileLen - 44L).coerceAtLeast(0L)
                var foundData = false

                val chunkHeader = ByteArray(8)
                while (raf.filePointer + 8L <= fileLen) {
                    if (raf.read(chunkHeader) < 8) break
                    val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .toLong() and 0xFFFFFFFFL

                    val chunkStart = raf.filePointer
                    if (chunkId == "fmt " && chunkSize >= 16L && chunkStart + 16L <= fileLen) {
                        val fmtBuf = ByteArray(16)
                        raf.readFully(fmtBuf)
                        val bb = ByteBuffer.wrap(fmtBuf).order(ByteOrder.LITTLE_ENDIAN)
                        bb.short // audioFormat
                        channels = bb.short.toInt().coerceAtLeast(1)
                        sampleRate = bb.int.coerceIn(8000, 192000)
                        bb.int // byteRate
                        bb.short // blockAlign
                        bitsPerSample = bb.short.toInt()
                    } else if (chunkId == "data") {
                        dataOffset = chunkStart
                        dataByteLen = min(chunkSize, fileLen - chunkStart).coerceAtLeast(0L)
                        foundData = true
                        break
                    }

                    val paddedSize = chunkSize + (chunkSize and 1L)
                    val nextPos = chunkStart + paddedSize
                    if (nextPos <= chunkStart || nextPos > fileLen) break
                    raf.seek(nextPos)
                }

                if (!foundData) {
                    dataOffset = 44L
                    dataByteLen = (fileLen - 44L).coerceAtLeast(0L)
                }
                if (bitsPerSample != 16 || dataByteLen < 4L) return null

                // Cap analysis read at ~25 seconds mono 48kHz (~2.4 MB) to prevent OOM on abnormally huge files
                val maxBytesToRead = min(dataByteLen, 2_400_000L).toInt() and -2
                raf.seek(dataOffset)
                val rawBytes = ByteArray(maxBytesToRead)
                raf.readFully(rawBytes)

                val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val totalShorts = shortBuf.remaining()
                if (channels <= 1) {
                    val mono = ShortArray(totalShorts)
                    shortBuf.get(mono)
                    ParsedWavData(sampleRate, mono)
                } else {
                    val frames = totalShorts / channels
                    val mono = ShortArray(frames)
                    for (f in 0 until frames) {
                        var acc = 0
                        for (c in 0 until channels) {
                            acc += shortBuf.get(f * channels + c).toInt()
                        }
                        mono[f] = (acc / channels).toShort()
                    }
                    ParsedWavData(sampleRate, mono)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
