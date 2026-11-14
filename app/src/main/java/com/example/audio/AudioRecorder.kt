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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Isolates standby pre-roll audio between consecutive word recordings so the tail or second half
 * of a previous word can never bleed into the start of the next recording.
 */
internal class PreRollIsolationGate(
    private val postStopCooldownMs: Long = 240L,
    private val quietFramesRequiredAfterStop: Int = 2,
    private val maxPreRollDurationMs: Int = 110,
    private val noisyEnvFallbackArmMs: Long = 950L
) {
    private val ring = ArrayDeque<ShortArray>(8)
    private var bufferedSamples: Int = 0
    private var lastStopCompletedAtMs: Long = 0L
    private var silenceConfirmedSinceStop: Boolean = true
    private var consecutiveQuietChunks: Int = 0

    @Synchronized
    fun clear() {
        ring.clear()
        bufferedSamples = 0
    }

    @Synchronized
    fun onRecordingStopped(nowMs: Long) {
        ring.clear()
        bufferedSamples = 0
        lastStopCompletedAtMs = nowMs
        silenceConfirmedSinceStop = false
        consecutiveQuietChunks = 0
    }

    @Synchronized
    fun resetForFreshStandby(nowMs: Long) {
        ring.clear()
        bufferedSamples = 0
        if (lastStopCompletedAtMs > 0L && (nowMs - lastStopCompletedAtMs) < noisyEnvFallbackArmMs) {
            silenceConfirmedSinceStop = false
            consecutiveQuietChunks = 0
        } else {
            silenceConfirmedSinceStop = true
            consecutiveQuietChunks = quietFramesRequiredAfterStop
        }
    }

    @Synchronized
    fun offerStandbyChunk(
        samples: ShortArray,
        count: Int,
        sampleRate: Int,
        nowMs: Long,
        quietRmsCeiling: Float
    ) {
        if (count <= 0) return

        if (lastStopCompletedAtMs > 0L) {
            val sinceStopMs = (nowMs - lastStopCompletedAtMs).coerceAtLeast(0L)
            if (sinceStopMs < postStopCooldownMs) {
                ring.clear()
                bufferedSamples = 0
                consecutiveQuietChunks = 0
                return
            }

            if (!silenceConfirmedSinceStop) {
                val rms = computeRms(samples, count)
                if (rms <= quietRmsCeiling) {
                    consecutiveQuietChunks++
                    if (consecutiveQuietChunks >= quietFramesRequiredAfterStop) {
                        silenceConfirmedSinceStop = true
                    }
                } else if (sinceStopMs >= noisyEnvFallbackArmMs) {
                    // Nearly a full second has elapsed since the previous word ended; safe to arm in noisy outdoor sites
                    silenceConfirmedSinceStop = true
                } else {
                    // Speaker is still finishing the tail of the previous word: keep draining and discarding!
                    consecutiveQuietChunks = 0
                    ring.clear()
                    bufferedSamples = 0
                    return
                }

                if (!silenceConfirmedSinceStop) {
                    return
                }
            }
        }

        val copy = samples.copyOf(count)
        ring.addLast(copy)
        bufferedSamples += count
        trimToMaxDuration(sampleRate)
    }

    @Synchronized
    fun consumeForRecordingStart(sampleRate: Int): List<ShortArray> {
        if (!silenceConfirmedSinceStop || ring.isEmpty()) {
            ring.clear()
            bufferedSamples = 0
            return emptyList()
        }
        trimToMaxDuration(sampleRate)
        val out = ring.toList()
        ring.clear()
        bufferedSamples = 0
        return out
    }

    private fun trimToMaxDuration(sampleRate: Int) {
        val maxSamples = ((sampleRate.coerceAtLeast(8000) * maxPreRollDurationMs) / 1000).coerceAtLeast(256)
        while (bufferedSamples > maxSamples && ring.isNotEmpty()) {
            val first = ring.first()
            val excess = bufferedSamples - maxSamples
            if (excess >= first.size) {
                ring.removeFirst()
                bufferedSamples -= first.size
            } else {
                val sliced = first.copyOfRange(excess, first.size)
                ring.removeFirst()
                ring.addFirst(sliced)
                bufferedSamples -= excess
                break
            }
        }
    }

    companion object {
        fun computeRms(samples: ShortArray, count: Int): Float {
            if (count <= 0) return 0f
            var sumSq = 0.0
            val n = minOf(count, samples.size)
            for (i in 0 until n) {
                val v = samples[i].toDouble()
                sumSq += v * v
            }
            return sqrt(sumSq / n).toFloat()
        }
    }
}

/**
 * Dual-threshold Voice Activity Detector with startup click guard and sustained-onset verification
 * so dipping tones (214 / 412) and disyllabic words are never split prematurely across two items.
 */
internal class SpeechTurnVadTracker(
    private val vadThreshold: Float,
    private val lowVolumeThreshold: Float,
    private val silenceDurationMs: Long,
    private val startupClickGuardMs: Long = 85L,
    private val minSustainedSpeechMs: Long = 110L
) {
    var hasDetectedSpeech: Boolean = false
        private set

    var silenceProgress: Float = 0f
        private set

    private var captureStartMs: Long = -1L
    private var accumulatedSpeechMs: Long = 0L
    private var briefOnsetGapMs: Long = 0L
    private var silenceStartMs: Long = 0L
    private var ambientFloorRms: Float = 120f
    private var ambientSamplesCount: Int = 0

    fun step(rms: Float, frameDurationMs: Long, nowMs: Long): Boolean {
        val frameMs = frameDurationMs.coerceIn(5L, 120L)
        if (captureStartMs < 0L) {
            captureStartMs = nowMs
        }
        val elapsedFromStart = (nowMs - captureStartMs).coerceAtLeast(0L)

        if (!hasDetectedSpeech) {
            // Update ambient noise floor estimate on non-speech frames
            if (rms < vadThreshold * 0.65f) {
                ambientFloorRms = if (ambientSamplesCount == 0) {
                    rms.coerceIn(30f, 600f)
                } else {
                    (ambientFloorRms * 0.85f + rms * 0.15f).coerceIn(30f, 600f)
                }
                ambientSamplesCount++
            }

            // Guard against initial button/mouse click transient during first 85ms
            if (elapsedFromStart < startupClickGuardMs && accumulatedSpeechMs == 0L && rms > vadThreshold) {
                return false
            }

            if (rms > vadThreshold) {
                accumulatedSpeechMs += frameMs
                briefOnsetGapMs = 0L
                if (accumulatedSpeechMs >= minSustainedSpeechMs) {
                    hasDetectedSpeech = true
                    silenceStartMs = 0L
                    silenceProgress = 0f
                }
            } else if (accumulatedSpeechMs > 0L) {
                // Allow brief <=65ms aspiration/closure dip during syllable onset without losing speech accumulation
                val onsetContinueFloor = (vadThreshold * 0.45f).coerceAtLeast(lowVolumeThreshold)
                if (rms > onsetContinueFloor) {
                    accumulatedSpeechMs += frameMs
                    if (accumulatedSpeechMs >= minSustainedSpeechMs) {
                        hasDetectedSpeech = true
                        silenceStartMs = 0L
                        silenceProgress = 0f
                    }
                } else {
                    briefOnsetGapMs += frameMs
                    if (briefOnsetGapMs > 65L) {
                        accumulatedSpeechMs = 0L
                        briefOnsetGapMs = 0L
                    }
                }
            }
            return false
        }

        // Hysteresis sustain threshold: prevents dipping tones (214/412) or softer 2nd syllables from counting as silence
        val sustainThreshold = maxOf(
            lowVolumeThreshold * 1.15f,
            ambientFloorRms * 2.2f,
            vadThreshold * 0.36f
        ).coerceAtMost((vadThreshold * 0.75f).coerceAtLeast(lowVolumeThreshold))

        if (rms > sustainThreshold) {
            silenceStartMs = 0L
            silenceProgress = 0f
            return false
        }

        if (silenceStartMs == 0L) {
            silenceStartMs = nowMs
        }
        val silenceElapsed = (nowMs - silenceStartMs).coerceAtLeast(0L)
        val targetSilenceMs = silenceDurationMs.coerceAtLeast(250L)
        silenceProgress = (silenceElapsed.toFloat() / targetSilenceMs.toFloat()).coerceIn(0f, 1f)
        return silenceElapsed >= targetSilenceMs
    }
}

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

    // Serializes hardware start/stop/standby transitions so overlapping rapid taps never corrupt AudioRecord
    private val lifecycleMutex = Mutex()
    private val captureTokenGenerator = AtomicLong(0L)
    @Volatile
    private var activeCaptureToken = 0L
    @Volatile
    private var activeHardwareToken = 0L

    private var audioRecord: AudioRecord? = null
    private var activeWriterJob: Job? = null
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

    @Volatile
    private var lowVolumeThreshold = 250f
    @Volatile
    private var totalRecordedBytes = 0L
    @Volatile
    private var sessionMaxPeak = 0
    @Volatile
    private var activeOutputFile: File? = null

    // Isolated Standby Pre-Roll Buffer (~110ms max, gated on post-stop cooldown & ambient silence)
    private val preRollGate = PreRollIsolationGate()
    @Volatile
    private var standbyDesired = false
    @Volatile
    private var standbyHandoffRequested = false
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

    /**
     * Immediately flips UI recording state off and invalidates the specified capture token
     * when the user explicitly switches words while recording, preventing any cross-word hangover.
     */
    fun markStoppingImmediate(expectedToken: Long = 0L) {
        val cur = activeCaptureToken
        if (expectedToken == 0L || cur == expectedToken) {
            activeCaptureToken = 0L
            _isRecording.value = false
            _silenceProgress.value = 0f
        }
    }

    fun configureHardware(
        targetSampleRate: Int,
        sourceStrategy: String,
        enablePreRoll: Boolean
    ) {
        val normalizedRate = if (targetSampleRate in listOf(22050, 44100, 48000)) targetSampleRate else 44100
        val changed = sampleRate != normalizedRate ||
            audioSourceStrategy != sourceStrategy ||
            preRollEnabled != enablePreRoll
        sampleRate = normalizedRate
        audioSourceStrategy = sourceStrategy
        preRollEnabled = enablePreRoll
        if (!enablePreRoll) {
            coroutineScope.launch {
                lifecycleMutex.withLock {
                    stopStandbyLoopLocked()
                }
            }
        } else if (changed && standbyDesired && !_isRecording.value) {
            coroutineScope.launch {
                lifecycleMutex.withLock {
                    if (!_isRecording.value && activeCaptureToken == 0L) {
                        stopStandbyLoopLocked()
                        startStandbyLoopLocked()
                    }
                }
            }
        }
    }

    /**
     * Enables or disables lightweight live standby pre-roll buffering (~110ms) when the user is
     * actively on a recording screen and [preRollEnabled] is true, eliminating hardware cold-start
     * plosive/sibilant loss without ever bleeding previous-word audio into the next word.
     */
    fun setStandbyPreRollActive(active: Boolean) {
        standbyDesired = active
        coroutineScope.launch {
            lifecycleMutex.withLock {
                if (!active || !preRollEnabled) {
                    stopStandbyLoopLocked()
                } else if (!_isRecording.value && activeCaptureToken == 0L) {
                    startStandbyLoopLocked()
                }
            }
        }
    }

    private fun quietCeilingForStandby(): Float = maxOf(lowVolumeThreshold * 1.35f, 420f)

    private fun startStandbyLoopLocked() {
        if (!standbyDesired || !preRollEnabled || _isRecording.value || activeCaptureToken != 0L) return
        if (standbyJob?.isActive == true) return
        standbyHandoffRequested = false
        preRollGate.resetForFreshStandby(System.currentTimeMillis())

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

                val buf = ShortArray(512) // ~11.6ms at 44.1kHz for fine-grained pre-roll & fast handoff
                while (isActive && standbyDesired && preRollEnabled && !standbyHandoffRequested) {
                    val activeStandby = standbyAudioRecord ?: break
                    val n = activeStandby.read(buf, 0, buf.size)
                    if (n > 0) {
                        preRollGate.offerStandbyChunk(
                            samples = buf,
                            count = n,
                            sampleRate = sampleRate,
                            nowMs = System.currentTimeMillis(),
                            quietRmsCeiling = quietCeilingForStandby()
                        )
                    } else if (n < 0) {
                        break
                    } else {
                        delay(4)
                    }
                }
            } catch (_: CancellationException) {
                // Normal handoff to active recording or screen exit
            } catch (e: Exception) {
                Log.w(tag, "Standby pre-roll stopped: ${e.message}")
            } finally {
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

    private suspend fun stopStandbyLoopLocked() {
        standbyHandoffRequested = true
        val job = standbyJob
        standbyJob = null
        val rec = standbyAudioRecord
        standbyAudioRecord = null
        preRollGate.clear()
        job?.cancel()
        if (rec != null && rec !== audioRecord) {
            try {
                rec.stop()
            } catch (_: Exception) {}
        }
        try {
            withTimeoutOrNull(300L) {
                job?.join()
            }
        } catch (_: Exception) {}
        if (rec != null && rec !== audioRecord) {
            try {
                rec.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Drains any unread PCM samples sitting in the OS/JavaSound kernel buffer at the moment of handoff
     * so no stale backlog can accumulate beyond the isolated 110ms pre-roll window.
     */
    private fun drainHardwareBacklogIntoGate(rec: AudioRecord, currentRate: Int) {
        val drainBuf = ShortArray(512)
        var safetyLoops = 0
        val nowMs = System.currentTimeMillis()
        val ceiling = quietCeilingForStandby()
        while (safetyLoops < 32) {
            safetyLoops++
            val readCount = try {
                rec.read(drainBuf, 0, drainBuf.size, AudioRecord.READ_NON_BLOCKING)
            } catch (_: Exception) {
                0
            }
            if (readCount <= 0) break
            preRollGate.offerStandbyChunk(
                samples = drainBuf,
                count = readCount,
                sampleRate = currentRate,
                nowMs = nowMs,
                quietRmsCeiling = ceiling
            )
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
    ): Long {
        val existingToken = activeCaptureToken
        if (_isRecording.value && existingToken != 0L) return existingToken

        val myToken = captureTokenGenerator.incrementAndGet()
        activeCaptureToken = myToken
        _isRecording.value = true
        _silenceProgress.value = 0f
        _elapsedMs.value = 0L
        _currentDbfs.value = -60f
        _isClipping.value = false
        _isTooQuiet.value = false
        _waveformPoints.value = emptyList()

        coroutineScope.launch {
            lifecycleMutex.withLock {
                // Ensure any leftover writer from a just-ended previous session is completely flushed first
                val staleWriter = activeWriterJob
                if (staleWriter != null) {
                    activeWriterJob = null
                    activeHardwareToken = 0L
                    try { audioRecord?.stop() } catch (_: Exception) {}
                    try { withTimeoutOrNull(1200L) { staleWriter.join() } } catch (_: Exception) {}
                    try { audioRecord?.release() } catch (_: Exception) {}
                    audioRecord = null
                    preRollGate.onRecordingStopped(System.currentTimeMillis())
                }

                // If user already tapped Stop or switched words while waiting on previous teardown, abort cleanly
                if (activeCaptureToken != myToken || !_isRecording.value) {
                    stopStandbyLoopLocked()
                    if (standbyDesired && preRollEnabled) {
                        startStandbyLoopLocked()
                    }
                    return@withLock
                }

                totalRecordedBytes = 0L
                sessionMaxPeak = 0
                activeOutputFile = outputFile

                // Coordinate zero-bleed handoff from live standby AudioRecord
                standbyHandoffRequested = true
                val handedOffStandby = standbyAudioRecord
                standbyAudioRecord = null
                val prevStandbyJob = standbyJob
                standbyJob = null

                if (handedOffStandby != null) {
                    // Promote reference BEFORE joining standbyJob so its finally-block won't stop/release it
                    if (preRollEnabled &&
                        handedOffStandby.state == AudioRecord.STATE_INITIALIZED &&
                        handedOffStandby.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    ) {
                        audioRecord = handedOffStandby
                    }
                }

                try {
                    withTimeoutOrNull(300L) {
                        prevStandbyJob?.cancelAndJoin()
                    }
                } catch (_: Exception) {}

                val livePreRollChunks: List<ShortArray>
                val preparedRec = audioRecord
                if (preRollEnabled &&
                    preparedRec != null &&
                    preparedRec === handedOffStandby &&
                    preparedRec.state == AudioRecord.STATE_INITIALIZED &&
                    preparedRec.recordingState == AudioRecord.RECORDSTATE_RECORDING
                ) {
                    _activeSourceLabel.value = standbyLabel
                    val negotiated = preparedRec.sampleRate
                    if (negotiated in 8000..192000) sampleRate = negotiated
                    // Drain any kernel-buffered frames through the post-stop silence gate & cap at 110ms
                    drainHardwareBacklogIntoGate(preparedRec, sampleRate)
                    livePreRollChunks = preRollGate.consumeForRecordingStart(sampleRate)
                } else {
                    try {
                        handedOffStandby?.stop()
                        handedOffStandby?.release()
                    } catch (_: Exception) {}
                    preRollGate.clear()
                    audioRecord = null

                    val (freshRec, label) = createAndStartAudioRecord()
                    audioRecord = freshRec
                    _activeSourceLabel.value = label
                    livePreRollChunks = emptyList()
                }

                val activeRec = audioRecord
                if (activeRec == null ||
                    activeRec.state != AudioRecord.STATE_INITIALIZED ||
                    activeRec.recordingState != AudioRecord.RECORDSTATE_RECORDING
                ) {
                    Log.e(tag, "AudioRecord initialization or startRecording failed.")
                    try { activeRec?.release() } catch (_: Exception) {}
                    audioRecord = null
                    activeHardwareToken = 0L
                    if (activeCaptureToken == myToken) {
                        activeCaptureToken = 0L
                        _isRecording.value = false
                    }
                    return@withLock
                }

                val negotiatedRate = activeRec.sampleRate
                if (negotiatedRate in 8000..192000) {
                    sampleRate = negotiatedRate
                }

                activeHardwareToken = myToken
                activeWriterJob = coroutineScope.launch {
                    writePcmAndMonitor(
                        sessionToken = myToken,
                        rec = activeRec,
                        outputFile = outputFile,
                        livePreRollChunks = livePreRollChunks,
                        autoNextEnabled = autoNextEnabled,
                        vadThreshold = vadThreshold,
                        silenceDurationMs = silenceDurationMs,
                        onAutoNextTriggered = onAutoNextTriggered
                    )
                }
            }
        }
        return myToken
    }

    private suspend fun writePcmAndMonitor(
        sessionToken: Long,
        rec: AudioRecord,
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

            val vadTracker = SpeechTurnVadTracker(
                vadThreshold = vadThreshold,
                lowVolumeThreshold = lowVolumeThreshold,
                silenceDurationMs = silenceDurationMs
            )
            val visualPoints = ArrayDeque<Float>(56)
            var consecutiveErrors = 0
            var lastUiEmitMs = 0L

            while (isActive && activeCaptureToken == sessionToken && _isRecording.value) {
                if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    break
                }

                val readResult = rec.read(shortBuffer, 0, shortBuffer.size)
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

                val peakNorm = (currentBlockMax / 26000f).coerceIn(0.03f, 1f)
                val rmsNorm = (rms / 10000f).coerceIn(0.03f, 1f)
                val normalizedPoint = maxOf(peakNorm * 0.7f, rmsNorm)
                visualPoints.addLast(normalizedPoint)
                if (visualPoints.size > 48) {
                    visualPoints.removeFirst()
                }

                byteBuffer.clear()
                for (i in 0 until readResult) {
                    byteBuffer.putShort(shortBuffer[i])
                }
                raf.write(byteBufferArray, 0, readResult * 2)
                totalBytesWritten += readResult * 2L
                totalRecordedBytes = totalBytesWritten

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

                // Voice Activity Detection & Auto-Next with dual-threshold hysteresis
                if (autoNextEnabled) {
                    val frameMs = ((readResult * 1000L) / sampleRate.coerceAtLeast(8000)).coerceAtLeast(10L)
                    val reachedEndSilence = vadTracker.step(rms = rms, frameDurationMs = frameMs, nowMs = nowMs)
                    val progress = vadTracker.silenceProgress
                    if (shouldEmitUi || progress >= 1f || progress == 0f) {
                        _silenceProgress.value = progress
                    }
                    if (reachedEndSilence) {
                        Log.d(tag, "Silence threshold reached. Triggering automatic advance.")
                        withContext(Dispatchers.Main) {
                            onAutoNextTriggered()
                        }
                        break
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
     *        Pass `0L` when stopping via VAD silence timeout, word switch, or lifecycle teardown.
     */
    suspend fun stopRecording(
        tailHangoverMs: Long = 115L,
        expectedToken: Long = 0L,
        expectedOutputFile: File? = null
    ): RecordingQualityInfo {
        val targetToken = if (expectedToken != 0L) expectedToken else activeCaptureToken
        if (!_isRecording.value && targetToken == 0L && activeWriterJob == null && expectedOutputFile == null) {
            return RecordingQualityInfo("未录制", 0, 0f, 0)
        }

        if (_isRecording.value && targetToken != 0L && activeCaptureToken == targetToken && tailHangoverMs > 0L) {
            delay(tailHangoverMs.coerceIn(0L, 260L))
        }

        // Clear global recording state only if it still belongs to the session we are stopping
        if (targetToken != 0L && activeCaptureToken == targetToken) {
            activeCaptureToken = 0L
            _isRecording.value = false
            _silenceProgress.value = 0f
        } else if (expectedToken == 0L && activeCaptureToken == 0L) {
            _isRecording.value = false
            _silenceProgress.value = 0f
        }

        var capturedOutFile: File? = expectedOutputFile
        var capturedPeak = 0
        var capturedBytes = 0L
        var capturedRate = sampleRate

        lifecycleMutex.withLock {
            val shouldStopCurrentHardware = when {
                targetToken != 0L -> activeHardwareToken == targetToken
                else -> activeCaptureToken == 0L && activeWriterJob != null
            }

            if (shouldStopCurrentHardware) {
                val writer = activeWriterJob
                activeWriterJob = null
                activeHardwareToken = 0L
                val rec = audioRecord
                audioRecord = null

                try {
                    rec?.stop()
                } catch (ignored: Exception) {}

                try {
                    withTimeoutOrNull(1500L) {
                        writer?.join()
                    }
                } catch (ignored: Exception) {}

                try {
                    rec?.release()
                } catch (ignored: Exception) {}

                if (capturedOutFile == null) {
                    capturedOutFile = activeOutputFile
                }
                capturedPeak = sessionMaxPeak.coerceAtLeast(_peakAmplitude.value)
                capturedBytes = totalRecordedBytes
                capturedRate = sampleRate

                // Mark stop timestamp & require post-stop cooldown + ambient silence before pre-roll buffers again
                preRollGate.onRecordingStopped(System.currentTimeMillis())

                if (standbyDesired && preRollEnabled && !_isRecording.value && activeCaptureToken == 0L) {
                    startStandbyLoopLocked()
                }
            }
        }

        val finalFile = capturedOutFile
        if (finalFile != null && finalFile.exists() && finalFile.length() > 44L) {
            val report = SpeechQualityEvaluator.evaluate(
                wavFile = finalFile,
                lowVolumeRmsThreshold = lowVolumeThreshold
            )
            return RecordingQualityInfo(
                status = report.primaryStatus,
                peakAmplitude = report.peakAmplitude.coerceAtLeast(capturedPeak),
                avgRms = report.avgSpeechRms,
                durationMs = report.totalDurationMs
            )
        }

        val duration = ((capturedBytes * 1000L) / (capturedRate.coerceAtLeast(8000) * 2L)).toInt()
        return RecordingQualityInfo(
            status = if (duration < 240) "极短/空音" else "音量过低",
            peakAmplitude = capturedPeak,
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
