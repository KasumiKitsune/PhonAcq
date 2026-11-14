package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.i18n.S
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class AudioRecorder {
    private val tag = "AudioRecorder"

    @Volatile
    private var sampleRate = 44100
    @Volatile
    private var audioSourceStrategy = "VOICE_RECOGNITION" // "UNPROCESSED", "VOICE_RECOGNITION", "MIC"
    @Volatile
    private var preRollEnabled = true

    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = 0

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State Flows for UI monitoring
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _currentRms = MutableStateFlow(0f)
    val currentRms = _currentRms.asStateFlow()

    private val _currentDbfs = MutableStateFlow(-60f)
    val currentDbfs = _currentDbfs.asStateFlow()

    private val _peakAmplitude = MutableStateFlow(0)
    val peakAmplitude = _peakAmplitude.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs = _elapsedMs.asStateFlow()

    private val _activeSourceLabel = MutableStateFlow(S.soundCheck.audioSourceHdOriginal)
    val activeSourceLabel = _activeSourceLabel.asStateFlow()

    private val _isClipping = MutableStateFlow(false)
    val isClipping = _isClipping.asStateFlow()

    private val _isTooQuiet = MutableStateFlow(false)
    val isTooQuiet = _isTooQuiet.asStateFlow()

    // Real-time flowing buffer for visualizer waveform points (normalized 0.02f..1.0f)
    private val _waveformPoints = MutableStateFlow<List<Float>>(emptyList())
    val waveformPoints = _waveformPoints.asStateFlow()

    // Auto-next silence progress state (0f..1f)
    private val _silenceProgress = MutableStateFlow(0f)
    val silenceProgress = _silenceProgress.asStateFlow()

    private var lowVolumeThreshold = 250f
    private var totalRecordedBytes = 0L
    private var sessionMaxPeak = 0
    @Volatile
    private var lastOutputFile: File? = null

    // Genuine Standby Pre-Roll Ring Buffer (~150ms live ambient audio while on Recording Studio)
    private val standbyRingBuffer = ArrayDeque<ShortArray>(8)
    @Volatile
    private var standbyDesired = false
    private var standbyJob: Job? = null
    private var standbyAudioRecord: AudioRecord? = null
    @Volatile
    private var standbyLabel: String = S.soundCheck.audioSourceHdOriginal

    // Optional real-time PCM callback for low-latency wireless ear-monitoring & dBFS telemetry
    @Volatile
    var onLivePcmFrame: ((samples: ShortArray, count: Int, sampleRate: Int, dbfs: Float, isClipping: Boolean) -> Unit)? = null

    fun setLowVolumeThreshold(thresh: Float) {
        lowVolumeThreshold = thresh
    }

    fun configureHardware(
        targetSampleRate: Int,
        sourceStrategy: String,
        enablePreRoll: Boolean
    ) {
        val changed = sampleRate != targetSampleRate ||
            audioSourceStrategy != sourceStrategy ||
            preRollEnabled != enablePreRoll
        sampleRate = if (targetSampleRate in listOf(22050, 44100, 48000)) targetSampleRate else 44100
        audioSourceStrategy = sourceStrategy
        preRollEnabled = enablePreRoll
        if (!enablePreRoll) {
            stopStandbyLoopInternal()
        } else if (changed && standbyDesired && !_isRecording.value) {
            stopStandbyLoopInternal()
            startStandbyLoopIfPossible()
        }
    }

    /**
     * Enables or disables lightweight live standby pre-roll buffering (~150ms) when the user is
     * actively on a recording screen and [preRollEnabled] is true, eliminating hardware cold-start
     * plosive/sibilant loss without ever bleeding previous-word audio into the next word.
     */
    fun setStandbyPreRollActive(active: Boolean) {
        standbyDesired = active
        if (!active || !preRollEnabled) {
            stopStandbyLoopInternal()
        } else if (!_isRecording.value) {
            startStandbyLoopIfPossible()
        }
    }

    private fun startStandbyLoopIfPossible() {
        if (!standbyDesired || !preRollEnabled || _isRecording.value) return
        if (standbyJob?.isActive == true) return
        standbyJob = coroutineScope.launch {
            var rec: AudioRecord? = null
            try {
                val (created, label) = createAndStartAudioRecord()
                if (created == null || created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    try { created?.release() } catch (_: Exception) {}
                    return@launch
                }
                rec = created
                standbyAudioRecord = created
                standbyLabel = label
                val negotiated = created.sampleRate
                if (negotiated in 8000..192000) sampleRate = negotiated

                val buf = ShortArray(1024)
                val maxChunks = ((sampleRate * 160) / (1000 * 1024)).coerceIn(4, 8)
                synchronized(standbyRingBuffer) { standbyRingBuffer.clear() }

                while (isActive && standbyDesired && preRollEnabled && !_isRecording.value) {
                    val activeStandby = standbyAudioRecord ?: break
                    val n = activeStandby.read(buf, 0, buf.size)
                    if (n > 0) {
                        val copy = buf.copyOf(n)
                        synchronized(standbyRingBuffer) {
                            while (standbyRingBuffer.size >= maxChunks) {
                                standbyRingBuffer.removeFirst()
                            }
                            standbyRingBuffer.addLast(copy)
                        }
                    } else if (n < 0) {
                        break
                    } else {
                        delay(5)
                    }
                }
            } catch (_: CancellationException) {
                // Normal handoff to active recording or screen exit
            } catch (e: Exception) {
                Log.w(tag, "Standby pre-roll stopped: ${e.message}")
            } finally {
                // Only release if it was NOT handed off to active audioRecord
                val currentStandby = rec
                if (currentStandby != null && currentStandby !== audioRecord) {
                    try {
                        currentStandby.stop()
                        currentStandby.release()
                    } catch (_: Exception) {}
                    if (standbyAudioRecord === currentStandby) {
                        standbyAudioRecord = null
                    }
                }
            }
        }
    }

    private fun stopStandbyLoopInternal() {
        val job = standbyJob
        standbyJob = null
        val rec = standbyAudioRecord
        standbyAudioRecord = null
        synchronized(standbyRingBuffer) {
            standbyRingBuffer.clear()
        }
        job?.cancel()
        if (rec != null && rec !== audioRecord) {
            try {
                rec.stop()
                rec.release()
            } catch (_: Exception) {}
        }
    }

    init {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        bufferSize = if (minBuf > 0) maxOf(minBuf * 2, 4096) else 8192
    }

    /**
     * Initializes and starts an AudioRecord instance, verifying that it actually enters
     * RECORDSTATE_RECORDING. Prioritizes the user's selected audio source strategy,
     * then falls back gracefully.
     */
    @SuppressLint("MissingPermission")
    private fun createAndStartAudioRecord(): Pair<AudioRecord?, String> {
        val candidates = when (audioSourceStrategy) {
            "UNPROCESSED" -> listOf(
                MediaRecorder.AudioSource.UNPROCESSED to S.soundCheck.audioSourceUnprocessed,
                MediaRecorder.AudioSource.VOICE_RECOGNITION to S.soundCheck.audioSourceVoiceClean,
                MediaRecorder.AudioSource.MIC to S.soundCheck.audioSourceMic,
                MediaRecorder.AudioSource.DEFAULT to S.soundCheck.audioSourceDefault
            )
            "MIC" -> listOf(
                MediaRecorder.AudioSource.MIC to S.soundCheck.audioSourceMic,
                MediaRecorder.AudioSource.VOICE_RECOGNITION to S.soundCheck.audioSourceVoiceClean,
                MediaRecorder.AudioSource.DEFAULT to S.soundCheck.audioSourceDefault
            )
            else -> listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to S.soundCheck.audioSourceVoiceClean,
                MediaRecorder.AudioSource.MIC to S.soundCheck.audioSourceMic,
                MediaRecorder.AudioSource.DEFAULT to S.soundCheck.audioSourceDefault
            )
        }

        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val safeBufferSize = if (minBuf > 0) maxOf(minBuf * 2, 4096) else 8192
        bufferSize = safeBufferSize

        for ((source, label) in candidates) {
            var rec: AudioRecord? = null
            try {
                rec = AudioRecord(
                    source,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    safeBufferSize
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    rec.startRecording()
                    if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        return rec to label
                    }
                }
                rec.release()
            } catch (e: Exception) {
                Log.w(tag, "AudioSource $label failed to start: ${e.message}")
                try {
                    rec?.release()
                } catch (ignored: Exception) {}
            }
        }
        return null to S.soundCheck.micNotReady
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        outputFile: File,
        autoNextEnabled: Boolean,
        vadThreshold: Float,
        silenceDurationMs: Long,
        onAutoNextTriggered: () -> Unit
    ): Boolean {
        if (_isRecording.value) return true

        _isRecording.value = true
        _silenceProgress.value = 0f
        _elapsedMs.value = 0L
        _currentDbfs.value = -60f
        _isClipping.value = false
        _isTooQuiet.value = false
        _waveformPoints.value = emptyList()
        totalRecordedBytes = 0L
        sessionMaxPeak = 0
        lastOutputFile = outputFile

        val prevJob = recordingJob
        val prevStandbyJob = standbyJob
        standbyJob = null

        recordingJob = coroutineScope.launch {
            try {
                prevJob?.cancelAndJoin()
            } catch (ignored: Exception) {}

            // Attempt zero-latency handoff from live standby AudioRecord if healthy
            val handedOffStandby = standbyAudioRecord
            standbyAudioRecord = null
            try {
                prevStandbyJob?.cancelAndJoin()
            } catch (ignored: Exception) {}

            val livePreRollChunks: List<ShortArray>
            if (!_isRecording.value) {
                try {
                    handedOffStandby?.stop()
                    handedOffStandby?.release()
                } catch (_: Exception) {}
                synchronized(standbyRingBuffer) { standbyRingBuffer.clear() }
                return@launch
            }

            if (preRollEnabled &&
                handedOffStandby != null &&
                handedOffStandby.state == AudioRecord.STATE_INITIALIZED &&
                handedOffStandby.recordingState == AudioRecord.RECORDSTATE_RECORDING
            ) {
                audioRecord = handedOffStandby
                _activeSourceLabel.value = standbyLabel
                livePreRollChunks = synchronized(standbyRingBuffer) {
                    val list = standbyRingBuffer.toList()
                    standbyRingBuffer.clear()
                    list
                }
            } else {
                try {
                    handedOffStandby?.stop()
                    handedOffStandby?.release()
                } catch (_: Exception) {}
                synchronized(standbyRingBuffer) { standbyRingBuffer.clear() }

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (ignored: Exception) {}
                audioRecord = null

                val (rec, label) = createAndStartAudioRecord()
                audioRecord = rec
                _activeSourceLabel.value = label
                livePreRollChunks = emptyList()
            }

            val rec = audioRecord
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED || rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(tag, "AudioRecord initialization or startRecording failed.")
                _isRecording.value = false
                return@launch
            }

            val negotiatedRate = rec.sampleRate
            if (negotiatedRate in 8000..192000) {
                sampleRate = negotiatedRate
            }

            writePcmAndMonitor(
                outputFile = outputFile,
                livePreRollChunks = livePreRollChunks,
                autoNextEnabled = autoNextEnabled,
                vadThreshold = vadThreshold,
                silenceDurationMs = silenceDurationMs,
                onAutoNextTriggered = onAutoNextTriggered
            )
        }
        return true
    }

    private suspend fun writePcmAndMonitor(
        outputFile: File,
        livePreRollChunks: List<ShortArray>,
        autoNextEnabled: Boolean,
        vadThreshold: Float,
        silenceDurationMs: Long,
        onAutoNextTriggered: () -> Unit
    ) = withContext(Dispatchers.IO) {
        var raf: RandomAccessFile? = null
        try {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.parentFile?.mkdirs()
            raf = RandomAccessFile(outputFile, "rw")
            // Pre-reserve 44 bytes for WAV header
            raf.write(ByteArray(44))

            val readChunkShorts = 1024 // ~23.2ms per frame at 44.1kHz
            val shortBuffer = ShortArray(readChunkShorts)
            val byteBuffer = ByteBuffer.allocate(readChunkShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
            val byteBufferArray = byteBuffer.array()
            var totalBytesWritten = 0L

            // Write genuine continuous live standby pre-roll frames (~150ms) if available.
            // Never inject fake digital silence dither or previous-word audio!
            var isFirstWrittenChunk = true
            if (livePreRollChunks.isNotEmpty()) {
                for (chunk in livePreRollChunks) {
                    if (chunk.isEmpty()) continue
                    val work = if (isFirstWrittenChunk) {
                        isFirstWrittenChunk = false
                        chunk.copyOf().also { arr ->
                            val fadeLen = minOf(48, arr.size)
                            for (i in 0 until fadeLen) {
                                arr[i] = ((arr[i].toInt() * i) / fadeLen).toShort()
                            }
                        }
                    } else {
                        chunk
                    }
                    var chunkPeak = 0
                    for (s in work) {
                        val a = abs(s.toInt())
                        if (a > chunkPeak) chunkPeak = a
                    }
                    if (chunkPeak > sessionMaxPeak) sessionMaxPeak = chunkPeak

                    var offset = 0
                    while (offset < work.size) {
                        val n = minOf(readChunkShorts, work.size - offset)
                        byteBuffer.clear()
                        for (i in 0 until n) {
                            byteBuffer.putShort(work[offset + i])
                        }
                        raf.write(byteBufferArray, 0, n * 2)
                        totalBytesWritten += n * 2L
                        offset += n
                    }
                }
                totalRecordedBytes = totalBytesWritten
            }

            var hasDetectedSpeech = false
            var silenceStartMs = 0L
            val visualPoints = ArrayDeque<Float>(56)
            var consecutiveErrors = 0
            var lastUiEmitMs = 0L

            while (isActive && _isRecording.value) {
                val activeRec = audioRecord ?: break
                if (activeRec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    break
                }

                val readResult = activeRec.read(shortBuffer, 0, shortBuffer.size)
                if (readResult < 0) {
                    consecutiveErrors++
                    Log.w(tag, "AudioRecord.read returned error code: $readResult")
                    if (consecutiveErrors > 10) break
                    delay(10)
                    continue
                }
                if (readResult == 0) {
                    delay(5)
                    continue
                }
                consecutiveErrors = 0

                // Micro 32-sample (~0.7ms) click-prevention ramp on very first hardware chunk when cold-started
                if (isFirstWrittenChunk) {
                    isFirstWrittenChunk = false
                    val fadeLen = minOf(32, readResult)
                    for (i in 0 until fadeLen) {
                        shortBuffer[i] = ((shortBuffer[i].toInt() * i) / fadeLen).toShort()
                    }
                }

                val nowMs = System.currentTimeMillis()
                var sumSquares = 0.0
                var currentBlockMax = 0
                for (i in 0 until readResult) {
                    val sample = shortBuffer[i].toInt()
                    val absSample = abs(sample)
                    if (absSample > currentBlockMax) {
                        currentBlockMax = absSample
                    }
                    sumSquares += sample.toDouble() * sample.toDouble()
                }

                val rms = sqrt(sumSquares / readResult).toFloat()
                _currentRms.value = rms
                _peakAmplitude.value = currentBlockMax
                if (currentBlockMax > sessionMaxPeak) {
                    sessionMaxPeak = currentBlockMax
                }

                // Append waveform visualization point
                val peakNorm = (currentBlockMax / 26000f).coerceIn(0.03f, 1f)
                val rmsNorm = (rms / 10000f).coerceIn(0.03f, 1f)
                val normalizedPoint = maxOf(peakNorm * 0.7f, rmsNorm)
                visualPoints.addLast(normalizedPoint)
                if (visualPoints.size > 48) {
                    visualPoints.removeFirst()
                }

                // Write shortBuffer to output random access file as bytes (Little-Endian)
                byteBuffer.clear()
                for (i in 0 until readResult) {
                    byteBuffer.putShort(shortBuffer[i])
                }
                raf.write(byteBufferArray, 0, readResult * 2)
                totalBytesWritten += readResult * 2L
                totalRecordedBytes = totalBytesWritten

                // Throttle UI StateFlow emissions to ~25Hz (every 38ms) to prevent main-thread recomposition storms
                val normRms = (rms / 32768f).coerceAtLeast(1e-5f)
                val frameDbfs = (20.0 * log10(normRms.toDouble())).toFloat().coerceIn(-60f, 0f)
                val frameClip = currentBlockMax > 30500
                val shouldEmitUi = (nowMs - lastUiEmitMs) >= 38L || frameClip
                if (shouldEmitUi) {
                    lastUiEmitMs = nowMs
                    _currentDbfs.value = frameDbfs
                    _isClipping.value = frameClip
                    _isTooQuiet.value = rms < lowVolumeThreshold
                    _waveformPoints.value = visualPoints.toList()
                    _elapsedMs.value = (totalBytesWritten * 1000L) / (sampleRate * 2L)
                }

                onLivePcmFrame?.let { cb ->
                    try {
                        cb(shortBuffer, readResult, sampleRate, frameDbfs, frameClip)
                    } catch (_: Exception) {}
                }

                // Voice Activity Detection & Auto-Next
                if (autoNextEnabled) {
                    if (rms > vadThreshold) {
                        hasDetectedSpeech = true
                        silenceStartMs = 0L
                        _silenceProgress.value = 0f
                    } else if (hasDetectedSpeech) {
                        if (silenceStartMs == 0L) {
                            silenceStartMs = nowMs
                        }
                        val silenceElapsed = nowMs - silenceStartMs
                        val progress = (silenceElapsed.toFloat() / silenceDurationMs).coerceIn(0f, 1f)
                        if (shouldEmitUi || progress >= 1f) {
                            _silenceProgress.value = progress
                        }

                        if (silenceElapsed >= silenceDurationMs) {
                            Log.d(tag, "Silence threshold reached. Triggering automatic advance.")
                            withContext(Dispatchers.Main) {
                                onAutoNextTriggered()
                            }
                            break
                        }
                    }
                } else if (_silenceProgress.value != 0f) {
                    _silenceProgress.value = 0f
                }
            }

            // Always write a valid RIFF WAVE header so the file is playable even if short
            raf.seek(0)
            writeWavHeader(raf, totalBytesWritten)
        } catch (e: Exception) {
            Log.e(tag, "Recording background thread loop crashed: ${e.message}", e)
        } finally {
            try {
                raf?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Stops active recording and evaluates acoustic quality directly on the flushed WAV file.
     *
     * @param tailHangoverMs Optional post-release capture cushion (default `115ms` for manual/hold stops)
     *        so releasing the button right as the speaker finishes a nasal coda or falling tone never clips the tail.
     *        Pass `0L` when stopping via VAD silence timeout or lifecycle teardown.
     */
    suspend fun stopRecording(tailHangoverMs: Long = 115L): RecordingQualityInfo {
        if (!_isRecording.value && recordingJob == null) {
            return RecordingQualityInfo("未录制", 0, 0f, 0)
        }

        if (_isRecording.value && tailHangoverMs > 0L) {
            delay(tailHangoverMs.coerceIn(0L, 260L))
        }

        _isRecording.value = false
        _silenceProgress.value = 0f

        try {
            audioRecord?.stop()
        } catch (ignored: Exception) {}

        // Wait for the background writer to finish flushing PCM data and writing the WAV header
        try {
            withTimeoutOrNull(1500L) {
                recordingJob?.join()
            }
        } catch (ignored: Exception) {}
        recordingJob = null

        try {
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null

        // Resume standby pre-roll buffer in background for the next word if still on recording screen
        if (standbyDesired && preRollEnabled) {
            startStandbyLoopIfPossible()
        }

        val outFile = lastOutputFile
        if (outFile != null && outFile.exists() && outFile.length() > 44L) {
            val report = SpeechQualityEvaluator.evaluate(
                wavFile = outFile,
                lowVolumeRmsThreshold = lowVolumeThreshold
            )
            return RecordingQualityInfo(
                status = report.primaryStatus,
                peakAmplitude = report.peakAmplitude.coerceAtLeast(sessionMaxPeak),
                avgRms = report.avgSpeechRms,
                durationMs = report.totalDurationMs
            )
        }

        val peak = sessionMaxPeak.coerceAtLeast(_peakAmplitude.value)
        val duration = ((totalRecordedBytes * 1000L) / (sampleRate * 2L)).toInt()
        return RecordingQualityInfo(
            status = if (duration < 240) "极短/空音" else "音量过低",
            peakAmplitude = peak,
            avgRms = 0f,
            durationMs = duration
        )
    }

    private fun writeWavHeader(raf: RandomAccessFile, pcmDataLength: Long) {
        val totalDataLen = pcmDataLength + 36
        val channels = 1
        val byteRate = sampleRate * channels * 16 / 8

        raf.writeBytes("RIFF")
        raf.write(intToByteArray(totalDataLen.toInt()))
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.write(intToByteArray(16))
        raf.write(shortToByteArray(1.toShort()))
        raf.write(shortToByteArray(channels.toShort()))
        raf.write(intToByteArray(sampleRate))
        raf.write(intToByteArray(byteRate))
        raf.write(shortToByteArray(2.toShort()))
        val bitsPerSample = 16
        raf.write(shortToByteArray(bitsPerSample.toShort()))
        raf.writeBytes("data")
        raf.write(intToByteArray(pcmDataLength.toInt()))
    }

    private fun intToByteArray(value: Int): ByteArray {
        val b = ByteArray(4)
        b[0] = (value and 0xff).toByte()
        b[1] = ((value shr 8) and 0xff).toByte()
        b[2] = ((value shr 16) and 0xff).toByte()
        b[3] = ((value shr 24) and 0xff).toByte()
        return b
    }

    private fun shortToByteArray(value: Short): ByteArray {
        val b = ByteArray(2)
        b[0] = (value.toInt() and 0xff).toByte()
        b[1] = ((value.toInt() shr 8) and 0xff).toByte()
        return b
    }
}

data class RecordingQualityInfo(
    val status: String,
    val peakAmplitude: Int,
    val avgRms: Float,
    val durationMs: Int
)
