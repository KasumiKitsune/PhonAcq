package com.example.utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure-Kotlin / Pure-JVM lossless PCM audio normalizer shared across Android and Desktop.
 *
 * Converts any standard uncompressed RIFF/WAVE (8/16/24/32-bit PCM, 32/64-bit IEEE Float,
 * extensible WAVE_FORMAT_EXTENSIBLE, mono or multi-channel, arbitrary chunk ordering) or
 * AIFF/AIFC uncompressed PCM into PhonAcq's canonical 44-byte RIFF 16-bit Mono PCM `.wav`
 * so `AcousticAnalyzer` (F0, Formants, Spectrogram) and `SpeechQualityEvaluator` work identically.
 */
object AudioWavNormalizer {

    private const val MIN_SAMPLE_RATE = 16000
    private const val MAX_SAMPLE_RATE = 48000
    private const val DEFAULT_TARGET_RATE = 44100

    data class QuickWavHeaderInfo(
        val isCanonicalMono16Pcm: Boolean,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val formatTag: Int,
        val durationMs: Long
    )

    /**
     * Fast non-allocating header scan for `.wav` files to estimate duration and check whether
     * a direct byte copy is already canonical 16-bit mono PCM with a standard 44-byte header.
     */
    fun inspectWavHeader(file: File): QuickWavHeaderInfo? {
        if (!file.exists() || !file.isFile || file.length() < 44L) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(12)
                if (raf.read(header) != 12) return null
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                val wave = String(header, 8, 4, Charsets.US_ASCII)
                if (riff != "RIFF" || wave != "WAVE") return null

                var fmtFound = false
                var formatTag = 0
                var channels = 1
                var sampleRate = 44100
                var byteRate = 0
                var bitsPerSample = 16
                var dataOffset = -1L
                var dataSize = 0L

                val chunkHeader = ByteArray(8)
                while (raf.filePointer + 8 <= raf.length()) {
                    if (raf.read(chunkHeader) != 8) break
                    val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .toLong() and 0xFFFFFFFFL

                    val chunkStart = raf.filePointer
                    if (chunkId == "fmt " && chunkSize >= 16L) {
                        val fmtBytes = ByteArray(min(chunkSize, 40L).toInt())
                        raf.readFully(fmtBytes)
                        val bb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val rawTag = bb.short.toInt() and 0xFFFF
                        channels = (bb.short.toInt() and 0xFFFF).coerceAtLeast(1)
                        sampleRate = bb.int.coerceAtLeast(1)
                        byteRate = bb.int
                        bb.short // blockAlign
                        bitsPerSample = (bb.short.toInt() and 0xFFFF)
                        formatTag = if (rawTag == 0xFFFE && fmtBytes.size >= 26) {
                            ByteBuffer.wrap(fmtBytes, 24, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                        } else {
                            rawTag
                        }
                        fmtFound = true
                    } else if (chunkId == "data") {
                        dataOffset = chunkStart
                        dataSize = min(chunkSize, raf.length() - chunkStart).coerceAtLeast(0L)
                        break
                    }

                    val padded = chunkSize + (chunkSize and 1L)
                    val nextPos = chunkStart + padded
                    if (nextPos <= chunkStart || nextPos > raf.length()) break
                    raf.seek(nextPos)
                }

                if (!fmtFound || dataOffset < 0L || dataSize <= 0L) return null
                val effectiveByteRate = if (byteRate > 0) {
                    byteRate.toLong()
                } else {
                    sampleRate.toLong() * channels * max(1, bitsPerSample / 8)
                }
                val durationMs = if (effectiveByteRate > 0L) {
                    ((dataSize * 1000L) / effectiveByteRate).coerceAtLeast(1L)
                } else {
                    0L
                }
                val isCanonical = formatTag == 1 &&
                    channels == 1 &&
                    bitsPerSample == 16 &&
                    dataOffset == 44L &&
                    sampleRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE

                QuickWavHeaderInfo(
                    isCanonicalMono16Pcm = isCanonical,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    formatTag = formatTag,
                    durationMs = durationMs
                )
            }
        }.getOrNull()
    }

    /**
     * Attempts to decode RIFF/WAVE or AIFF/AIFC uncompressed PCM/Float audio directly in pure Kotlin
     * and write a canonical 44-byte 16-bit Mono PCM `.wav` to [outputWavFile].
     */
    fun tryNormalizeRiffOrAiffToMono16Wav(inputFile: File, outputWavFile: File): Boolean {
        if (!inputFile.exists() || !inputFile.isFile || inputFile.length() < 16L) return false

        // Fast path: already canonical 44-byte header 16-bit mono PCM WAV in [16kHz..48kHz]
        val wavInfo = inspectWavHeader(inputFile)
        if (wavInfo != null && wavInfo.isCanonicalMono16Pcm) {
            return runCatching {
                outputWavFile.parentFile?.mkdirs()
                if (inputFile.canonicalPath != outputWavFile.canonicalPath) {
                    inputFile.copyTo(outputWavFile, overwrite = true)
                }
                outputWavFile.exists() && outputWavFile.length() > 44L
            }.getOrDefault(false)
        }

        val magic = ByteArray(12)
        val readMagic = runCatching {
            FileInputStream(inputFile).use { it.read(magic) }
        }.getOrDefault(-1)
        if (readMagic < 12) return false

        val head4 = String(magic, 0, 4, Charsets.US_ASCII)
        val tail4 = String(magic, 8, 4, Charsets.US_ASCII)

        return when {
            head4 == "RIFF" && tail4 == "WAVE" -> decodeRiffWaveToMono16Wav(inputFile, outputWavFile)
            head4 == "FORM" && (tail4 == "AIFF" || tail4 == "AIFC") -> decodeAiffToMono16Wav(inputFile, outputWavFile, isAifc = tail4 == "AIFC")
            else -> false
        }
    }

    private fun decodeRiffWaveToMono16Wav(inputFile: File, outputWavFile: File): Boolean {
        return runCatching {
            RandomAccessFile(inputFile, "r").use { raf ->
                raf.seek(12L)
                var formatTag = 0
                var channels = 0
                var sampleRate = 0
                var blockAlign = 0
                var bitsPerSample = 0
                var dataOffset = -1L
                var dataSize = 0L

                val chunkHdr = ByteArray(8)
                while (raf.filePointer + 8 <= raf.length()) {
                    if (raf.read(chunkHdr) != 8) break
                    val id = String(chunkHdr, 0, 4, Charsets.US_ASCII)
                    val sz = ByteBuffer.wrap(chunkHdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val start = raf.filePointer
                    if (id == "fmt " && sz >= 16L) {
                        val fmtBuf = ByteArray(min(sz, 64L).toInt())
                        raf.readFully(fmtBuf)
                        val bb = ByteBuffer.wrap(fmtBuf).order(ByteOrder.LITTLE_ENDIAN)
                        val rawTag = bb.short.toInt() and 0xFFFF
                        channels = (bb.short.toInt() and 0xFFFF)
                        sampleRate = bb.int
                        bb.int // byteRate
                        blockAlign = (bb.short.toInt() and 0xFFFF)
                        bitsPerSample = (bb.short.toInt() and 0xFFFF)
                        formatTag = if (rawTag == 0xFFFE && fmtBuf.size >= 26) {
                            ByteBuffer.wrap(fmtBuf, 24, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                        } else {
                            rawTag
                        }
                    } else if (id == "data") {
                        dataOffset = start
                        dataSize = min(sz, raf.length() - start).coerceAtLeast(0L)
                        break
                    }
                    val next = start + sz + (sz and 1L)
                    if (next <= start || next > raf.length()) break
                    raf.seek(next)
                }

                if (channels <= 0 || sampleRate <= 0 || dataOffset < 0L || dataSize <= 0L) return false
                // Only PCM (1) or IEEE Float (3) can be decoded directly here
                if (formatTag != 1 && formatTag != 3) return false
                val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
                val frameSize = if (blockAlign >= channels * bytesPerSample) blockAlign else channels * bytesPerSample
                if (frameSize <= 0) return false

                val frameCountLong = dataSize / frameSize
                // Cap single clip decode at ~30 minutes to protect heap
                val frameCount = min(frameCountLong, 48000L * 60L * 30L).toInt()
                if (frameCount <= 0) return false

                val monoFloat = FloatArray(frameCount)
                raf.seek(dataOffset)
                BufferedInputStream(FileInputStream(raf.fd), 65536).use { bis ->
                    val frameBuf = ByteArray(frameSize)
                    val bb = ByteBuffer.wrap(frameBuf).order(ByteOrder.LITTLE_ENDIAN)
                    var actualFrames = 0
                    for (i in 0 until frameCount) {
                        var off = 0
                        while (off < frameSize) {
                            val r = bis.read(frameBuf, off, frameSize - off)
                            if (r <= 0) break
                            off += r
                        }
                        if (off < frameSize) break
                        var sum = 0f
                        for (ch in 0 until channels) {
                            val base = ch * bytesPerSample
                            val sampleVal = when {
                                formatTag == 3 && bitsPerSample == 32 -> bb.getFloat(base)
                                formatTag == 3 && bitsPerSample == 64 -> bb.getDouble(base).toFloat()
                                formatTag == 1 && bitsPerSample == 8 -> {
                                    val u = frameBuf[base].toInt() and 0xFF
                                    (u - 128) / 128f
                                }
                                formatTag == 1 && bitsPerSample == 16 -> {
                                    bb.getShort(base).toFloat() / 32768f
                                }
                                formatTag == 1 && bitsPerSample == 24 -> {
                                    val b0 = frameBuf[base].toInt() and 0xFF
                                    val b1 = frameBuf[base + 1].toInt() and 0xFF
                                    val b2 = frameBuf[base + 2].toInt() // sign-extended
                                    val v24 = (b2 shl 16) or (b1 shl 8) or b0
                                    v24.toFloat() / 8388608f
                                }
                                formatTag == 1 && bitsPerSample == 32 -> {
                                    (bb.getInt(base).toDouble() / 2147483648.0).toFloat()
                                }
                                else -> return false
                            }
                            sum += if (sampleVal.isFinite()) sampleVal else 0f
                        }
                        monoFloat[actualFrames] = (sum / channels.toFloat()).coerceIn(-1f, 1f)
                        actualFrames++
                    }
                    if (actualFrames <= 0) return false
                    val trimmed = if (actualFrames == monoFloat.size) monoFloat else monoFloat.copyOf(actualFrames)
                    val (finalSamples, finalRate) = resampleIfOutOfBounds(trimmed, sampleRate)
                    writeMono16BitWavFromFloats(outputWavFile, finalSamples, finalRate)
                }
            }
        }.getOrDefault(false)
    }

    private fun decodeAiffToMono16Wav(inputFile: File, outputWavFile: File, isAifc: Boolean): Boolean {
        return runCatching {
            RandomAccessFile(inputFile, "r").use { raf ->
                raf.seek(12L)
                var channels = 0
                var numSampleFrames = 0L
                var sampleSizeBits = 0
                var sampleRate = 0
                var compressionType = "NONE"
                var ssndDataOffset = -1L
                var ssndDataBytes = 0L

                val chunkHdr = ByteArray(8)
                while (raf.filePointer + 8 <= raf.length()) {
                    if (raf.read(chunkHdr) != 8) break
                    val id = String(chunkHdr, 0, 4, Charsets.US_ASCII)
                    val sz = ByteBuffer.wrap(chunkHdr, 4, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
                    val start = raf.filePointer
                    if (id == "COMM" && sz >= 18L) {
                        val comm = ByteArray(min(sz, 64L).toInt())
                        raf.readFully(comm)
                        val bb = ByteBuffer.wrap(comm).order(ByteOrder.BIG_ENDIAN)
                        channels = bb.short.toInt() and 0xFFFF
                        numSampleFrames = bb.int.toLong() and 0xFFFFFFFFL
                        sampleSizeBits = bb.short.toInt() and 0xFFFF
                        val ext80 = ByteArray(10)
                        bb.get(ext80)
                        sampleRate = parseIeeeExtended80BitSampleRate(ext80)
                        if (isAifc && comm.size >= 22) {
                            compressionType = String(comm, 18, 4, Charsets.US_ASCII)
                        }
                    } else if (id == "SSND" && sz >= 8L) {
                        val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                        raf.readInt() // blockSize
                        ssndDataOffset = raf.filePointer + offset
                        ssndDataBytes = (sz - 8L - offset).coerceAtLeast(0L)
                    }
                    val next = start + sz + (sz and 1L)
                    if (next <= start || next > raf.length()) break
                    raf.seek(next)
                }

                val isLittleEndianSowt = compressionType.equals("sowt", ignoreCase = true)
                val isUncompressedBig = compressionType == "NONE" || compressionType.equals("raw ", ignoreCase = true) || compressionType.equals("twos", ignoreCase = true)
                if (!isLittleEndianSowt && !isUncompressedBig) return false
                if (channels <= 0 || sampleRate <= 0 || ssndDataOffset < 0L || ssndDataBytes <= 0L) return false

                val bytesPerSample = ((sampleSizeBits + 7) / 8).coerceAtLeast(1)
                val frameSize = channels * bytesPerSample
                val maxFrames = min(
                    if (numSampleFrames > 0L) numSampleFrames else (ssndDataBytes / frameSize),
                    ssndDataBytes / frameSize
                )
                val frameCount = min(maxFrames, 48000L * 60L * 30L).toInt()
                if (frameCount <= 0) return false

                val monoFloat = FloatArray(frameCount)
                raf.seek(ssndDataOffset)
                DataInputStream(BufferedInputStream(FileInputStream(raf.fd), 65536)).use { dis ->
                    val frameBuf = ByteArray(frameSize)
                    val bb = ByteBuffer.wrap(frameBuf).order(if (isLittleEndianSowt) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
                    var actualFrames = 0
                    for (i in 0 until frameCount) {
                        var off = 0
                        while (off < frameSize) {
                            val r = dis.read(frameBuf, off, frameSize - off)
                            if (r <= 0) break
                            off += r
                        }
                        if (off < frameSize) break
                        var sum = 0f
                        for (ch in 0 until channels) {
                            val base = ch * bytesPerSample
                            val v = when (bytesPerSample) {
                                1 -> frameBuf[base].toFloat() / 128f
                                2 -> bb.getShort(base).toFloat() / 32768f
                                3 -> {
                                    val v24 = if (isLittleEndianSowt) {
                                        ((frameBuf[base + 2].toInt()) shl 16) or
                                            ((frameBuf[base + 1].toInt() and 0xFF) shl 8) or
                                            (frameBuf[base].toInt() and 0xFF)
                                    } else {
                                        ((frameBuf[base].toInt()) shl 16) or
                                            ((frameBuf[base + 1].toInt() and 0xFF) shl 8) or
                                            (frameBuf[base + 2].toInt() and 0xFF)
                                    }
                                    v24.toFloat() / 8388608f
                                }
                                4 -> (bb.getInt(base).toDouble() / 2147483648.0).toFloat()
                                else -> return false
                            }
                            sum += v
                        }
                        monoFloat[actualFrames] = (sum / channels.toFloat()).coerceIn(-1f, 1f)
                        actualFrames++
                    }
                    if (actualFrames <= 0) return false
                    val trimmed = if (actualFrames == monoFloat.size) monoFloat else monoFloat.copyOf(actualFrames)
                    val (finalSamples, finalRate) = resampleIfOutOfBounds(trimmed, sampleRate)
                    writeMono16BitWavFromFloats(outputWavFile, finalSamples, finalRate)
                }
            }
        }.getOrDefault(false)
    }

    private fun parseIeeeExtended80BitSampleRate(bytes: ByteArray): Int {
        if (bytes.size < 10) return 44100
        val expon = ((bytes[0].toInt() and 0x7F) shl 8) or (bytes[1].toInt() and 0xFF)
        var hiMant = 0L
        for (i in 2..5) {
            hiMant = (hiMant shl 8) or (bytes[i].toLong() and 0xFFL)
        }
        if (expon == 0 && hiMant == 0L) return 44100
        val value = hiMant.toDouble() * 2.0.pow((expon - 16383 - 31).toDouble())
        return value.roundToInt().coerceIn(4000, 192000)
    }

    fun resampleIfOutOfBounds(samples: FloatArray, srcRate: Int): Pair<FloatArray, Int> {
        if (srcRate in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE || samples.size < 2) {
            return samples to srcRate.coerceIn(MIN_SAMPLE_RATE, MAX_SAMPLE_RATE)
        }
        val targetRate = DEFAULT_TARGET_RATE
        val ratio = srcRate.toDouble() / targetRate.toDouble()
        val outLen = max(1, (samples.size / ratio).roundToInt())
        val out = FloatArray(outLen)
        val lastIdx = samples.lastIndex
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceIn(0, lastIdx)
            val i1 = min(i0 + 1, lastIdx)
            val frac = (pos - i0).toFloat()
            out[i] = samples[i0] * (1f - frac) + samples[i1] * frac
        }
        return out to targetRate
    }

    fun writeMono16BitWavFromFloats(destFile: File, samples: FloatArray, sampleRate: Int): Boolean {
        if (samples.isEmpty()) return false
        return runCatching {
            destFile.parentFile?.mkdirs()
            val dataBytesLen = samples.size * 2
            val header = buildCanonicalWavHeader(dataBytesLen, sampleRate)
            BufferedOutputStream(FileOutputStream(destFile), 65536).use { bos ->
                bos.write(header)
                val chunk = ByteArray(8192)
                var bytePos = 0
                for (s in samples) {
                    val clamped = (s.coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)
                    chunk[bytePos++] = (clamped and 0xFF).toByte()
                    chunk[bytePos++] = ((clamped ushr 8) and 0xFF).toByte()
                    if (bytePos >= chunk.size) {
                        bos.write(chunk, 0, bytePos)
                        bytePos = 0
                    }
                }
                if (bytePos > 0) {
                    bos.write(chunk, 0, bytePos)
                }
            }
            destFile.exists() && destFile.length() > 44L
        }.getOrDefault(false)
    }

    fun writeMono16BitWavFromShorts(destFile: File, samples: ShortArray, sampleRate: Int): Boolean {
        if (samples.isEmpty()) return false
        return runCatching {
            destFile.parentFile?.mkdirs()
            val dataBytesLen = samples.size * 2
            val header = buildCanonicalWavHeader(dataBytesLen, sampleRate)
            BufferedOutputStream(FileOutputStream(destFile), 65536).use { bos ->
                bos.write(header)
                val chunk = ByteArray(8192)
                var bytePos = 0
                for (s in samples) {
                    val v = s.toInt()
                    chunk[bytePos++] = (v and 0xFF).toByte()
                    chunk[bytePos++] = ((v ushr 8) and 0xFF).toByte()
                    if (bytePos >= chunk.size) {
                        bos.write(chunk, 0, bytePos)
                        bytePos = 0
                    }
                }
                if (bytePos > 0) {
                    bos.write(chunk, 0, bytePos)
                }
            }
            destFile.exists() && destFile.length() > 44L
        }.getOrDefault(false)
    }

    fun buildCanonicalWavHeader(pcmDataBytes: Int, sampleRate: Int): ByteArray {
        val totalRiffChunkLen = 36 + pcmDataBytes
        val byteRate = sampleRate * 2 // mono * 16-bit
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(totalRiffChunkLen)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16) // PCM fmt chunk size
        bb.putShort(1) // AudioFormat = 1 (PCM)
        bb.putShort(1) // NumChannels = 1 (Mono)
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort(2) // BlockAlign = 2
        bb.putShort(16) // BitsPerSample = 16
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(pcmDataBytes)
        return bb.array()
    }

    /**
     * Slices `[startSec, endSec]` from an already-normalized 44-byte canonical 16-bit mono PCM WAV
     * into [outputWavFile] in O(1) seek time without loading the full long audio into memory.
     */
    fun sliceCanonicalMono16Wav(
        canonicalWavFile: File,
        startSec: Double,
        endSec: Double,
        outputWavFile: File
    ): Boolean {
        if (!canonicalWavFile.exists() || !canonicalWavFile.isFile || canonicalWavFile.length() <= 44L) {
            return false
        }
        if (!startSec.isFinite() || !endSec.isFinite() || endSec <= startSec) {
            return false
        }
        val info = inspectWavHeader(canonicalWavFile) ?: return false
        if (!info.isCanonicalMono16Pcm || info.sampleRate <= 0) return false

        val totalDataBytes = (canonicalWavFile.length() - 44L).coerceAtLeast(0L)
        val totalFrames = totalDataBytes / 2L
        if (totalFrames <= 0L) return false

        val startFrame = kotlin.math.floor(startSec.coerceAtLeast(0.0) * info.sampleRate).toLong()
            .coerceIn(0L, totalFrames)
        val endFrame = kotlin.math.ceil(endSec.coerceAtLeast(0.0) * info.sampleRate).toLong()
            .coerceIn(startFrame, totalFrames)
        val sliceFrames = endFrame - startFrame
        if (sliceFrames < 16L) return false

        val sliceBytesLong = sliceFrames * 2L
        if (sliceBytesLong > Int.MAX_VALUE.toLong() - 64L) return false
        val sliceBytes = sliceBytesLong.toInt()

        return runCatching {
            outputWavFile.parentFile?.mkdirs()
            RandomAccessFile(canonicalWavFile, "r").use { raf ->
                raf.seek(44L + startFrame * 2L)
                BufferedOutputStream(FileOutputStream(outputWavFile), 65536).use { bos ->
                    bos.write(buildCanonicalWavHeader(sliceBytes, info.sampleRate))
                    val buf = ByteArray(16384)
                    var remaining = sliceBytes
                    while (remaining > 0) {
                        val toRead = min(buf.size, remaining)
                        val n = raf.read(buf, 0, toRead)
                        if (n <= 0) break
                        bos.write(buf, 0, n)
                        remaining -= n
                    }
                }
            }
            outputWavFile.exists() && outputWavFile.length() > 44L
        }.getOrDefault(false)
    }
}
