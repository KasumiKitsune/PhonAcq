package com.example.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Low-latency 16kHz PCM ear-monitor player for the 遥控端 (Remote Controller),
 * equipped with a real-time Headphone Gate (AudioDeviceCallback + ACTION_AUDIO_BECOMING_NOISY)
 * to instantly cut off audio if headphones are unplugged mid-session, preventing acoustic
 * feedback howl into nearby fieldwork microphones.
 */
class LiveEarMonitorPlayer(private val context: Context) {

    companion object {
        const val MONITOR_SAMPLE_RATE = 16000
        private const val HEADPHONE_POLL_INTERVAL_MS = 1200L

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

        internal fun isHeadphoneOutputDeviceType(type: Int): Boolean {
            return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }

    private val _isMonitoringEnabled = MutableStateFlow(false)
    val isMonitoringEnabled = _isMonitoringEnabled.asStateFlow()

    private val _allowSpeakerOverride = MutableStateFlow(false)
    val allowSpeakerOverride = _allowSpeakerOverride.asStateFlow()

    private val _headphoneDetected = MutableStateFlow(false)
    val headphoneDetected = _headphoneDetected.asStateFlow()

    private val _autoMutedOnDisconnectCount = MutableStateFlow(0)
    val autoMutedOnDisconnectCount = _autoMutedOnDisconnectCount.asStateFlow()

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var lastHeadphoneCheckMs: Long = 0L

    private val trackLock = Any()
    private val guardLock = Any()

    @Volatile
    private var guardsRegistered = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshHeadphoneState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val stillHasHeadphones = refreshHeadphoneState()
            if (!stillHasHeadphones) {
                emergencyMuteIfUnprotected()
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                _headphoneDetected.value = false
                emergencyMuteIfUnprotected()
            }
        }
    }

    /**
     * Immediately halts monitoring if headphones disappear and speaker override is not granted.
     * Safe to invoke from main thread, audio thread, or broadcast receiver.
     */
    internal fun emergencyMuteIfUnprotected(): Boolean {
        if (_allowSpeakerOverride.value || !_isMonitoringEnabled.value) return false
        _isMonitoringEnabled.value = false
        _autoMutedOnDisconnectCount.value = _autoMutedOnDisconnectCount.value + 1
        stopTrack()
        return true
    }

    private fun registerDisconnectGuards() {
        synchronized(guardLock) {
            if (guardsRegistered) return
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.registerAudioDeviceCallback(audioDeviceCallback, null)
            } catch (_: Exception) {
            }
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.applicationContext.registerReceiver(
                        becomingNoisyReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.applicationContext.registerReceiver(becomingNoisyReceiver, filter)
                }
            } catch (_: Exception) {
            }
            guardsRegistered = true
        }
    }

    private fun unregisterDisconnectGuards() {
        synchronized(guardLock) {
            if (!guardsRegistered) return
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (_: Exception) {
            }
            try {
                context.applicationContext.unregisterReceiver(becomingNoisyReceiver)
            } catch (_: Exception) {
            }
            guardsRegistered = false
        }
    }

    fun refreshHeadphoneState(): Boolean {
        val detected = try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val devices = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            devices.any { dev -> isHeadphoneOutputDeviceType(dev.type) }
        } catch (_: Exception) {
            false
        }
        lastHeadphoneCheckMs = System.currentTimeMillis()
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
        registerDisconnectGuards()
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
                val builder = AudioTrack.Builder()
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                val track = builder.build()
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
        if (!_allowSpeakerOverride.value) {
            val now = System.currentTimeMillis()
            val needsRecheck = !_headphoneDetected.value || (now - lastHeadphoneCheckMs >= HEADPHONE_POLL_INTERVAL_MS)
            if (needsRecheck && !refreshHeadphoneState()) {
                // Headphone unplugged mid-session: immediately mute to prevent feedback howl
                emergencyMuteIfUnprotected()
                return
            }
        }
        synchronized(trackLock) {
            if (!_isMonitoringEnabled.value) return
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
        unregisterDisconnectGuards()
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
