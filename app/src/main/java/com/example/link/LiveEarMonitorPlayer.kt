package com.example.link

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Low-latency 16kHz PCM ear-monitor player for the 遥控端 (Remote Controller),
 * equipped with an automatic Headphone Gate to prevent speakerphone acoustic feedback
 * into the nearby fieldwork microphone.
 */
class LiveEarMonitorPlayer(private val context: Context) {

    companion object {
        const val MONITOR_SAMPLE_RATE = 16000

        /**
         * Downsamples an arbitrary PCM frame (e.g. 44.1kHz or 48kHz) to 16kHz mono shorts
         * using box-averaging anti-alias decimation so UDP packets stay compact (~680 bytes/frame).
         */
        fun downsampleTo16kHz(samples: ShortArray, count: Int, sourceRate: Int): ShortArray {
            if (count <= 0) return ShortArray(0)
            if (sourceRate <= MONITOR_SAMPLE_RATE) {
                return samples.copyOf(count)
            }
            val outLen = ((count.toLong() * MONITOR_SAMPLE_RATE) / sourceRate).toInt().coerceAtLeast(1)
            val out = ShortArray(outLen)
            val step = sourceRate.toDouble() / MONITOR_SAMPLE_RATE.toDouble()
            for (i in 0 until outLen) {
                val start = (i * step).toInt().coerceIn(0, count - 1)
                val end = (((i + 1) * step).toInt()).coerceIn(start + 1, count)
                var sum = 0
                for (k in start until end) {
                    sum += samples[k].toInt()
                }
                out[i] = (sum / (end - start)).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            return out
        }
    }

    private val _isMonitoringEnabled = MutableStateFlow(false)
    val isMonitoringEnabled = _isMonitoringEnabled.asStateFlow()

    private val _allowSpeakerOverride = MutableStateFlow(false)
    val allowSpeakerOverride = _allowSpeakerOverride.asStateFlow()

    private val _headphoneDetected = MutableStateFlow(false)
    val headphoneDetected = _headphoneDetected.asStateFlow()

    @Volatile
    private var audioTrack: AudioTrack? = null
    private val trackLock = Any()

    fun refreshHeadphoneState(): Boolean {
        val detected = try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val devices = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            devices.any { dev ->
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        } catch (_: Exception) {
            false
        }
        _headphoneDetected.value = detected
        return detected
    }

    /**
     * Enables live ear monitoring. Returns `false` if no headphones are detected and
     * [forceSpeakerOverride] is false (so UI can prompt the investigator first).
     */
    fun setMonitoringEnabled(enabled: Boolean, forceSpeakerOverride: Boolean = false): Boolean {
        if (!enabled) {
            _isMonitoringEnabled.value = false
            stopTrack()
            return true
        }
        val hasHeadphones = refreshHeadphoneState()
        if (!hasHeadphones && !forceSpeakerOverride && !_allowSpeakerOverride.value) {
            return false
        }
        if (forceSpeakerOverride) {
            _allowSpeakerOverride.value = true
        }
        ensureTrackStarted()
        _isMonitoringEnabled.value = true
        return true
    }

    private fun ensureTrackStarted() {
        synchronized(trackLock) {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play()
                return
            }
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    MONITOR_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(3200)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(MONITOR_SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.play()
                    audioTrack = track
                } else {
                    track.release()
                }
            } catch (_: Exception) {}
        }
    }

    fun feed16kHzPcmFrame(samples16k: ShortArray) {
        if (!_isMonitoringEnabled.value || samples16k.isEmpty()) return
        if (!_allowSpeakerOverride.value && !_headphoneDetected.value && !refreshHeadphoneState()) {
            // Headphone unplugged mid-session: immediately mute to prevent feedback howl
            _isMonitoringEnabled.value = false
            stopTrack()
            return
        }
        synchronized(trackLock) {
            val track = audioTrack ?: run {
                ensureTrackStarted()
                audioTrack
            } ?: return
            try {
                track.write(samples16k, 0, samples16k.size)
            } catch (_: Exception) {}
        }
    }

    fun stopTrack() {
        synchronized(trackLock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}
            audioTrack = null
        }
    }
}
