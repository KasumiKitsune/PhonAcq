@file:Suppress("PackageDirectoryMismatch")

package android.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import com.example.i18n.S
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.util.concurrent.CopyOnWriteArraySet
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import javax.sound.sampled.AudioFormat as JavaAudioFormat

/**
 * Global Windows microphone / soundcard selector state for Desktop.
 * Allows users on Windows to pick a specific external USB audio interface, headset, or built-in mic.
 */
object DesktopAudioDeviceSelector {
    private val _availableInputDevices = MutableStateFlow<List<String>>(emptyList())
    val availableInputDevices = _availableInputDevices.asStateFlow()

    private val _selectedInputDevice = MutableStateFlow<String?>(null)
    val selectedInputDevice = _selectedInputDevice.asStateFlow()

    private val _isMicAvailable = MutableStateFlow(false)
    val isMicAvailable = _isMicAvailable.asStateFlow()

    init {
        refreshDevices()
    }

    fun refreshDevices(): List<String> {
        val format = JavaAudioFormat(44100f, 16, 1, true, false)
        val lineInfo = DataLine.Info(TargetDataLine::class.java, format)
        val names = try {
            AudioSystem.getMixerInfo()
                .filter { info ->
                    try {
                        AudioSystem.getMixer(info).isLineSupported(lineInfo)
                    } catch (_: Exception) {
                        false
                    }
                }
                .map { it.name }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
        _availableInputDevices.value = names
        _isMicAvailable.value = names.isNotEmpty() || try {
            AudioSystem.isLineSupported(lineInfo)
        } catch (_: Exception) {
            false
        }
        if (_selectedInputDevice.value != null && _selectedInputDevice.value !in names) {
            _selectedInputDevice.value = names.firstOrNull()
        }
        return names
    }

    private val deviceCallbacks = CopyOnWriteArraySet<AudioDeviceCallback>()

    internal fun addDeviceCallback(cb: AudioDeviceCallback) {
        deviceCallbacks.add(cb)
    }

    internal fun removeDeviceCallback(cb: AudioDeviceCallback) {
        deviceCallbacks.remove(cb)
    }

    private fun notifyDeviceCallbacks() {
        val current = AudioManager.instance.getDevices(AudioManager.GET_DEVICES_INPUTS)
        deviceCallbacks.forEach { cb ->
            try {
                cb.onAudioDevicesAdded(current)
            } catch (_: Exception) {}
        }
    }

    fun selectDevice(deviceName: String?) {
        _selectedInputDevice.value = deviceName
        notifyDeviceCallbacks()
    }

    fun openTargetDataLine(sampleRate: Int, bufferSizeInBytes: Int): TargetDataLine? {
        val candidateRates = listOf(sampleRate, 44100, 48000, 22050).distinct()
        val preferredName = _selectedInputDevice.value

        for (rate in candidateRates) {
            val format = JavaAudioFormat(rate.toFloat(), 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            try {
                if (!preferredName.isNullOrBlank()) {
                    val matchedMixerInfo = AudioSystem.getMixerInfo().firstOrNull { it.name == preferredName }
                    if (matchedMixerInfo != null) {
                        val mixer: Mixer = AudioSystem.getMixer(matchedMixerInfo)
                        if (mixer.isLineSupported(info)) {
                            val line = mixer.getLine(info) as TargetDataLine
                            line.open(format, bufferSizeInBytes.coerceAtLeast(4096))
                            _isMicAvailable.value = true
                            return line
                        }
                    }
                }
                if (AudioSystem.isLineSupported(info)) {
                    val line = AudioSystem.getLine(info) as TargetDataLine
                    line.open(format, bufferSizeInBytes.coerceAtLeast(4096))
                    _isMicAvailable.value = true
                    return line
                }
            } catch (_: Exception) {}
        }
        _isMicAvailable.value = false
        return null
    }
}

class AudioFormat(
    val sampleRate: Int = 16000,
    val encoding: Int = ENCODING_PCM_16BIT,
    val channelMask: Int = CHANNEL_OUT_MONO
) {
    companion object {
        const val CHANNEL_IN_MONO = 16
        const val CHANNEL_OUT_MONO = 4
        const val ENCODING_PCM_16BIT = 2
    }

    class Builder {
        private var sampleRate = 16000
        private var encoding = ENCODING_PCM_16BIT
        private var channelMask = CHANNEL_OUT_MONO

        fun setSampleRate(rate: Int) = apply { sampleRate = rate }
        fun setEncoding(enc: Int) = apply { encoding = enc }
        fun setChannelMask(mask: Int) = apply { channelMask = mask }
        fun build(): AudioFormat = AudioFormat(sampleRate, encoding, channelMask)
    }
}

class AudioAttributes private constructor(val usage: Int, val contentType: Int) {
    companion object {
        const val USAGE_MEDIA = 1
        const val CONTENT_TYPE_SPEECH = 1
    }

    class Builder {
        private var usage = USAGE_MEDIA
        private var contentType = CONTENT_TYPE_SPEECH
        fun setUsage(u: Int) = apply { usage = u }
        fun setContentType(c: Int) = apply { contentType = c }
        fun build(): AudioAttributes = AudioAttributes(usage, contentType)
    }
}

class AudioTrack private constructor(
    private val sampleRate: Int,
    private val bufferSizeInBytes: Int
) {
    companion object {
        const val MODE_STREAM = 1
        const val STATE_UNINITIALIZED = 0
        const val STATE_INITIALIZED = 1
        const val WRITE_NON_BLOCKING = 1

        fun getMinBufferSize(sampleRateInHz: Int, channelConfig: Int, audioFormat: Int): Int {
            return (sampleRateInHz * 2 / 12).coerceAtLeast(2048)
        }
    }

    class Builder {
        private var sampleRate = 16000
        private var bufferSize = 4096

        fun setAudioAttributes(attrs: AudioAttributes) = apply {}
        fun setAudioFormat(fmt: AudioFormat) = apply { sampleRate = fmt.sampleRate }
        fun setBufferSizeInBytes(size: Int) = apply { bufferSize = size }
        fun setTransferMode(mode: Int) = apply {}
        fun build(): AudioTrack = AudioTrack(sampleRate, bufferSize)
    }

    private var sourceLine: SourceDataLine? = null
    var state: Int = STATE_UNINITIALIZED
        private set

    init {
        try {
            val fmt = JavaAudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, fmt)
            if (AudioSystem.isLineSupported(info)) {
                val line = AudioSystem.getLine(info) as SourceDataLine
                line.open(fmt, bufferSizeInBytes.coerceAtLeast(2048))
                sourceLine = line
                state = STATE_INITIALIZED
            }
        } catch (_: Exception) {
            state = STATE_UNINITIALIZED
        }
    }

    fun play() {
        try {
            sourceLine?.start()
        } catch (_: Exception) {}
    }

    fun pause() {
        try {
            sourceLine?.stop()
        } catch (_: Exception) {}
    }

    fun flush() {
        try {
            sourceLine?.flush()
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            sourceLine?.stop()
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            sourceLine?.stop()
            sourceLine?.close()
        } catch (_: Exception) {}
        sourceLine = null
        state = STATE_UNINITIALIZED
    }

    fun write(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        val line = sourceLine ?: return -1
        if (sizeInShorts <= 0) return 0
        return try {
            val bytes = ByteArray(sizeInShorts * 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                .put(audioData, offsetInShorts, sizeInShorts)
            line.write(bytes, 0, bytes.size) / 2
        } catch (_: Exception) {
            -1
        }
    }
}

object MediaRecorder {
    object AudioSource {
        const val DEFAULT = 0
        const val MIC = 1
        const val VOICE_RECOGNITION = 6
        const val UNPROCESSED = 9
    }
}

class AudioRecord(
    val audioSource: Int,
    val sampleRateInHz: Int,
    val channelConfig: Int,
    val audioFormat: Int,
    val bufferSizeInBytes: Int
) {
    companion object {
        const val STATE_UNINITIALIZED = 0
        const val STATE_INITIALIZED = 1
        const val RECORDSTATE_STOPPED = 1
        const val RECORDSTATE_RECORDING = 3

        fun getMinBufferSize(sampleRateInHz: Int, channelConfig: Int, audioFormat: Int): Int {
            return (sampleRateInHz * 2 / 10).coerceAtLeast(4096)
        }
    }

    private var targetLine: TargetDataLine? = null
    private var rawByteBuffer = ByteArray(bufferSizeInBytes.coerceAtLeast(4096))

    var state: Int = STATE_UNINITIALIZED
        private set

    var recordingState: Int = RECORDSTATE_STOPPED
        private set

    val sampleRate: Int
        get() = targetLine?.format?.sampleRate?.toInt()?.takeIf { it in 8000..192000 } ?: sampleRateInHz

    init {
        try {
            val line = DesktopAudioDeviceSelector.openTargetDataLine(sampleRateInHz, bufferSizeInBytes)
            if (line != null) {
                targetLine = line
                state = STATE_INITIALIZED
            }
        } catch (_: Exception) {
            state = STATE_UNINITIALIZED
        }
    }

    fun startRecording() {
        val line = targetLine ?: return
        try {
            line.start()
            recordingState = RECORDSTATE_RECORDING
        } catch (_: Exception) {
            recordingState = RECORDSTATE_STOPPED
        }
    }

    fun read(audioData: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
        val line = targetLine ?: return -1
        if (recordingState != RECORDSTATE_RECORDING) return -1

        val bytesToRead = sizeInShorts * 2
        if (rawByteBuffer.size < bytesToRead) {
            rawByteBuffer = ByteArray(bytesToRead)
        }
        return try {
            val bytesRead = line.read(rawByteBuffer, 0, bytesToRead)
            if (bytesRead <= 0) return bytesRead
            val shortsRead = bytesRead / 2
            val bb = ByteBuffer.wrap(rawByteBuffer, 0, shortsRead * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until shortsRead) {
                audioData[offsetInShorts + i] = bb.short
            }
            shortsRead
        } catch (_: Exception) {
            -1
        }
    }

    fun stop() {
        recordingState = RECORDSTATE_STOPPED
        try {
            targetLine?.stop()
            targetLine?.flush()
        } catch (_: Exception) {}
    }

    fun release() {
        stop()
        try {
            targetLine?.close()
        } catch (_: Exception) {}
        targetLine = null
        state = STATE_UNINITIALIZED
    }
}

class MediaPlayer {
    fun interface OnCompletionListener {
        fun onCompletion(mp: MediaPlayer)
    }

    private var sourceFile: File? = null
    private var clip: Clip? = null
    private var completionListener: OnCompletionListener? = null

    fun setDataSource(path: String) {
        sourceFile = File(path)
    }

    fun setDataSource(context: Context, uri: Uri) {
        sourceFile = uri.toFile()
    }

    fun prepare() {
        val file = sourceFile ?: return
        if (!file.exists() || file.length() <= 44L) return
        try {
            clip?.close()
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val audioStream = AudioSystem.getAudioInputStream(bis)
                val newClip = AudioSystem.getClip()
                newClip.open(audioStream)
                newClip.addLineListener { event ->
                    if (event.type == LineEvent.Type.STOP) {
                        val c = clip
                        if (c != null && c.microsecondPosition >= c.microsecondLength - 15_000L) {
                            completionListener?.onCompletion(this)
                        }
                    }
                }
                clip = newClip
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun start() {
        try {
            clip?.start()
        } catch (_: Exception) {}
    }

    fun pause() {
        try {
            clip?.stop()
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            clip?.stop()
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            clip?.stop()
            clip?.close()
        } catch (_: Exception) {}
        clip = null
    }

    val isPlaying: Boolean
        get() = clip?.isRunning == true

    val duration: Int
        get() = ((clip?.microsecondLength ?: 0L) / 1000L).toInt()

    val currentPosition: Int
        get() = ((clip?.microsecondPosition ?: 0L) / 1000L).toInt()

    fun setOnCompletionListener(listener: OnCompletionListener?) {
        completionListener = listener
    }
}

class AudioDeviceInfo(
    val productName: CharSequence?,
    val type: Int
) {
    companion object {
        const val TYPE_BUILTIN_SPEAKER = 2
        const val TYPE_WIRED_HEADSET = 3
        const val TYPE_WIRED_HEADPHONES = 4
        const val TYPE_BLUETOOTH_SCO = 7
        const val TYPE_BLUETOOTH_A2DP = 8
        const val TYPE_USB_DEVICE = 11
        const val TYPE_USB_ACCESSORY = 12
        const val TYPE_BUILTIN_MIC = 15
        const val TYPE_USB_HEADSET = 22
        const val TYPE_BLE_HEADSET = 26
    }
}

open class AudioDeviceCallback {
    open fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {}
    open fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {}
}

class AudioManager {
    companion object {
        const val GET_DEVICES_INPUTS = 1
        const val GET_DEVICES_OUTPUTS = 2
        val instance = AudioManager()
    }

    fun registerAudioDeviceCallback(callback: AudioDeviceCallback?, handler: Handler? = null) {
        if (callback != null) {
            DesktopAudioDeviceSelector.addDeviceCallback(callback)
        }
    }

    fun unregisterAudioDeviceCallback(callback: AudioDeviceCallback?) {
        if (callback != null) {
            DesktopAudioDeviceSelector.removeDeviceCallback(callback)
        }
    }

    fun getDevices(flags: Int): Array<AudioDeviceInfo> {
        if (flags == GET_DEVICES_OUTPUTS) {
            val fmt = JavaAudioFormat(16000f, 16, 1, true, false)
            val outInfo = DataLine.Info(SourceDataLine::class.java, fmt)
            val outputs = try {
                AudioSystem.getMixerInfo().filter { info ->
                    try {
                        AudioSystem.getMixer(info).isLineSupported(outInfo)
                    } catch (_: Exception) {
                        false
                    }
                }.map { info ->
                    val lower = info.name.lowercase()
                    val t = when {
                        lower.contains("headphone") || lower.contains("headset") || lower.contains("耳机") || lower.contains("earphone") ->
                            AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        lower.contains("usb") || lower.contains("scarlett") || lower.contains("dac") ->
                            AudioDeviceInfo.TYPE_USB_HEADSET
                        lower.contains("bluetooth") || lower.contains("airpods") ->
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        else -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    AudioDeviceInfo(info.name, t)
                }
            } catch (_: Exception) {
                emptyList()
            }
            return if (outputs.isNotEmpty()) outputs.toTypedArray() else arrayOf(
                AudioDeviceInfo(S.desktop.bridgeDefaultSpeakerOut, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            )
        }

        val names = DesktopAudioDeviceSelector.refreshDevices()
        val chosen = DesktopAudioDeviceSelector.selectedInputDevice.value ?: names.firstOrNull()
        if (chosen == null) {
            return arrayOf(AudioDeviceInfo(S.desktop.bridgeDefaultMicIn, AudioDeviceInfo.TYPE_BUILTIN_MIC))
        }
        val lower = chosen.lowercase()
        val type = when {
            lower.contains("bluetooth") || lower.contains("hands-free") || lower.contains("airpods") ->
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            lower.contains("usb") || lower.contains("scarlett") || lower.contains("shure") ||
                lower.contains("rodecaster") || lower.contains("zoom") || lower.contains("external") ->
                AudioDeviceInfo.TYPE_USB_HEADSET
            lower.contains("headset") || lower.contains("耳机") ->
                AudioDeviceInfo.TYPE_WIRED_HEADSET
            else ->
                AudioDeviceInfo.TYPE_USB_DEVICE
        }
        return arrayOf(AudioDeviceInfo(chosen, type))
    }
}

/**
 * Minimal Desktop bridge for `android.media.ExifInterface` so camera/photo EXIF orientation
 * inspection compiles identically across Android and Windows Desktop.
 */
class ExifInterface {
    constructor(filename: String)
    constructor(inputStream: java.io.InputStream)

    fun getAttributeInt(tag: String, defaultValue: Int): Int = defaultValue

    companion object {
        const val TAG_ORIENTATION = "Orientation"
        const val ORIENTATION_UNDEFINED = 0
        const val ORIENTATION_NORMAL = 1
        const val ORIENTATION_ROTATE_90 = 6
        const val ORIENTATION_ROTATE_180 = 3
        const val ORIENTATION_ROTATE_270 = 8
    }
}

