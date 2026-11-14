package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 麦克风设备检测信息
 */
private data class MicHardwareStatus(
    val deviceName: String = if (DesktopAdaptiveState.isDesktopPlatform) S.soundCheck.defaultMicDesktop else S.soundCheck.defaultMicMobile,
    val isBluetooth: Boolean = false,
    val isExternalWiredOrUsb: Boolean = false
)

/**
 * 环境安静度单项条件检查结果
 */
private data class AmbientSubCheck(
    val label: String,
    val grade: CheckGrade,
    val summary: String,
    val detail: String
)

/**
 * 4 秒多维环境安静度测量结论（一票否决制：任一条件不达标即提示改进）
 */
private data class AmbientNoiseCheckResult(
    val avgDb: Float,
    val maxPeakDb: Float,
    val fluctuationDb: Float,
    val burstCount: Int,
    val level: CheckGrade,
    val title: String,
    val advice: String,
    val subChecks: List<AmbientSubCheck>
)

private enum class CheckGrade {
    GOOD,
    WARN,
    BAD
}

/**
 * 5 秒试录四维诊断报告
 */
private data class TestRecordingReport(
    val durationSec: Float,
    val noiseFloorDb: Float,
    val speechDb: Float,
    val maxPeakRatio: Float,
    val cutoffHzEstimate: Int, // 0 means full-band (no cutoff), 4000 or 8000 means narrowband cutoff
    val noiseGrade: CheckGrade,
    val noiseTitle: String,
    val noiseDetail: String,
    val volumeGrade: CheckGrade,
    val volumeTitle: String,
    val volumeDetail: String,
    val clippingGrade: CheckGrade,
    val clippingTitle: String,
    val clippingDetail: String,
    val bandwidthGrade: CheckGrade,
    val bandwidthTitle: String,
    val bandwidthDetail: String
) {
    val allPassed: Boolean
        get() = noiseGrade == CheckGrade.GOOD &&
            volumeGrade == CheckGrade.GOOD &&
            clippingGrade == CheckGrade.GOOD &&
            bandwidthGrade == CheckGrade.GOOD
}

/**
 * 全屏「试音与环境检查」界面
 * 包含：
 * 1. 麦克风设备类型与采样率自检（自动预警蓝牙窄带麦克风）
 * 2. 实时环境音量分区监测 + 3 秒周围安静度定格检测
 * 3. 5 秒试录与原声回放 + 四维诊断（环境底噪、说话音量、有无喷麦爆音、有无高频一刀切裁切）
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun SoundCheckScreen(
    viewModel: RecordingViewModel,
    onBack: () -> Unit,
    onGoToRecordingWorkspace: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sampleRateHz by viewModel.sampleRateHz.collectAsState()
    val audioSourceStrategy by viewModel.audioSourceStrategy.collectAsState()

    val backVisualState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = true,
        state = backVisualState
    ) {
        onBack()
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var deviceRefreshTick by remember { mutableIntStateOf(0) }
    val micStatus = remember(deviceRefreshTick, hasAudioPermission) {
        detectActiveMicHardware(context)
    }

    // 实时监测状态（使用原生 FloatState 保证 120Hz 高刷零重组开销）
    var liveDb by remember { mutableFloatStateOf(-58f) }
    var livePeakRatio by remember { mutableFloatStateOf(0f) }
    val waveformBars = remember { FloatArray(44) { 0.04f } }
    var waveformWriteIdx by remember { mutableIntStateOf(0) }
    var waveformFrameTick by remember { mutableIntStateOf(0) }

    // 3 秒环境安静度测量状态
    var isMeasuringAmbient by remember { mutableStateOf(false) }
    var ambientProgress by remember { mutableFloatStateOf(0f) }
    var ambientResult by remember { mutableStateOf<AmbientNoiseCheckResult?>(null) }

    // 5 秒试录与回放状态
    val tempWavFile = remember { File(context.cacheDir, "sound_check_temp.wav") }
    var isTestRecording by remember { mutableStateOf(false) }
    var testRecordProgress by remember { mutableFloatStateOf(0f) }
    var testRecordElapsedSec by remember { mutableFloatStateOf(0f) }
    var stopTestRecordEarlySignal by remember { mutableStateOf(false) }
    var isAnalyzingTest by remember { mutableStateOf(false) }
    var testReport by remember { mutableStateOf<TestRecordingReport?>(null) }

    // 试录回放状态
    var isPlayingBack by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    val activePlayerRef = remember { arrayOfNulls<MediaPlayer>(1) }

    fun stopPlaybackIfNeeded() {
        try {
            activePlayerRef[0]?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (_: Exception) {
        } finally {
            activePlayerRef[0] = null
            isPlayingBack = false
            playbackProgress = 0f
        }
    }

    // 退出界面时自动释放播放器并清理临时试音文件
    DisposableEffect(Unit) {
        onDispose {
            stopPlaybackIfNeeded()
            try {
                if (tempWavFile.exists()) {
                    tempWavFile.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    // 后台音频监听与试录协程（在回放期间暂停占用麦克风，避免回声自激）
    LaunchedEffect(hasAudioPermission, sampleRateHz, audioSourceStrategy, isPlayingBack) {
        if (!hasAudioPermission || isPlayingBack) {
            liveDb = -58f
            livePeakRatio = 0f
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val rate = if (sampleRateHz in listOf(22050, 44100, 48000)) sampleRateHz else 44100
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = if (minBuf > 0) maxOf(minBuf * 2, 4096) else 8192

            val sourceCandidates = when (audioSourceStrategy) {
                "UNPROCESSED" -> listOf(
                    MediaRecorder.AudioSource.UNPROCESSED,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC
                )
                "MIC" -> listOf(
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                )
                else -> listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC
                )
            }

            var recorder: AudioRecord? = null
            for (src in sourceCandidates) {
                try {
                    val candidate = AudioRecord(
                        src,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufSize
                    )
                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        candidate.startRecording()
                        if (candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            recorder = candidate
                            break
                        }
                    }
                    candidate.release()
                } catch (_: Exception) {
                }
            }

            if (recorder == null) return@withContext

            val readBuf = ShortArray(1024)
            var smoothedDb = -55f

            // Ambient check accumulators (4-second multi-condition analysis)
            val ambientFrameDbs = ArrayList<Float>(180)
            val ambientPeakDbs = ArrayList<Float>(180)
            val ambientLowFreqDbs = ArrayList<Float>(180)
            val ambientLowFreqRatios = ArrayList<Float>(180)
            var ambientStartMs = 0L
            // 1st-order low-pass filter state for <240Hz rumble detection
            val lpAlpha = ((2.0 * PI * 240.0) / (2.0 * PI * 240.0 + rate)).toFloat().coerceIn(0.01f, 0.25f)
            var lpState = 0f

            // 5-second test recording buffer
            val maxTestSamples = rate * 5
            var testPcmBuffer: ShortArray? = null
            var testSampleCount = 0
            var testStartMs = 0L

            try {
                while (isActive) {
                    val read = recorder.read(readBuf, 0, readBuf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        var lowFreqSumSq = 0.0
                        var maxAbs = 0
                        for (i in 0 until read) {
                            val s = readBuf[i].toInt()
                            val a = abs(s)
                            if (a > maxAbs) maxAbs = a
                            val sf = s.toFloat()
                            sumSq += sf.toDouble() * sf.toDouble()
                            lpState += lpAlpha * (sf - lpState)
                            lowFreqSumSq += lpState.toDouble() * lpState.toDouble()
                        }
                        val rms = sqrt(sumSq / read).toFloat()
                        val normRms = (rms / 32768f).coerceAtLeast(1e-5f)
                        val rawDb = (20.0 * log10(normRms.toDouble())).toFloat().coerceIn(-60f, 0f)
                        smoothedDb = smoothedDb * 0.55f + rawDb * 0.45f
                        val peakRatio = (maxAbs / 32768f).coerceIn(0f, 1f)
                        val framePeakDb = (20.0 * log10(peakRatio.toDouble().coerceAtLeast(1e-5))).toFloat().coerceIn(-60f, 0f)

                        val lowRms = sqrt(lowFreqSumSq / read).toFloat()
                        val lowDb = (20.0 * log10((lowRms / 32768f).toDouble().coerceAtLeast(1e-5))).toFloat().coerceIn(-60f, 0f)
                        val lowRatio = if (sumSq > 1.0) (lowFreqSumSq / sumSq).toFloat().coerceIn(0f, 1f) else 0f

                        liveDb = smoothedDb
                        livePeakRatio = peakRatio

                        val normalizedBar = ((smoothedDb + 58f) / 56f).coerceIn(0.04f, 1f)
                        waveformBars[waveformWriteIdx] = normalizedBar
                        waveformWriteIdx = (waveformWriteIdx + 1) % waveformBars.size
                        waveformFrameTick++

                        val nowMs = System.currentTimeMillis()

                        // 1. Handle 4-second multi-condition ambient noise check
                        if (isMeasuringAmbient) {
                            if (ambientStartMs == 0L) {
                                ambientStartMs = nowMs
                                ambientFrameDbs.clear()
                                ambientPeakDbs.clear()
                                ambientLowFreqDbs.clear()
                                ambientLowFreqRatios.clear()
                            }
                            ambientFrameDbs.add(rawDb)
                            ambientPeakDbs.add(framePeakDb)
                            ambientLowFreqDbs.add(lowDb)
                            ambientLowFreqRatios.add(lowRatio)

                            val elapsed = nowMs - ambientStartMs
                            ambientProgress = (elapsed / 4000f).coerceIn(0f, 1f)
                            if (elapsed >= 4000L) {
                                ambientResult = evaluateMultiConditionAmbientNoise(
                                    frameDbs = ambientFrameDbs.toList(),
                                    framePeakDbs = ambientPeakDbs.toList(),
                                    lowFreqDbs = ambientLowFreqDbs.toList(),
                                    lowFreqRatios = ambientLowFreqRatios.toList()
                                )
                                isMeasuringAmbient = false
                                ambientStartMs = 0L
                                ambientProgress = 0f
                            }
                        } else {
                            ambientStartMs = 0L
                        }

                        // 2. Handle 5-second test recording
                        if (isTestRecording) {
                            if (testStartMs == 0L) {
                                testStartMs = nowMs
                                testPcmBuffer = ShortArray(maxTestSamples)
                                testSampleCount = 0
                                stopTestRecordEarlySignal = false
                            }
                            val targetBuf = testPcmBuffer
                            if (targetBuf != null) {
                                val canCopy = min(read, targetBuf.size - testSampleCount)
                                if (canCopy > 0) {
                                    System.arraycopy(readBuf, 0, targetBuf, testSampleCount, canCopy)
                                    testSampleCount += canCopy
                                }
                            }
                            val elapsedMs = nowMs - testStartMs
                            testRecordElapsedSec = elapsedMs / 1000f
                            testRecordProgress = (elapsedMs / 5000f).coerceIn(0f, 1f)

                            val finishedByTime = elapsedMs >= 5000L || testSampleCount >= maxTestSamples
                            val finishedEarly = stopTestRecordEarlySignal && elapsedMs >= 1200L

                            if (finishedByTime || finishedEarly) {
                                val recordedSamples = testPcmBuffer?.copyOfRange(0, testSampleCount)
                                isTestRecording = false
                                stopTestRecordEarlySignal = false
                                testStartMs = 0L
                                testPcmBuffer = null

                                if (recordedSamples != null && recordedSamples.size > rate / 2) {
                                    isAnalyzingTest = true
                                    writeMono16BitWav(tempWavFile, recordedSamples, rate)
                                    val latestMic = detectActiveMicHardware(context)
                                    val report = withContext(Dispatchers.Default) {
                                        analyzeTestRecording(
                                            samples = recordedSamples,
                                            sampleRate = rate,
                                            isBluetoothMic = latestMic.isBluetooth
                                        )
                                    }
                                    testReport = report
                                    isAnalyzingTest = false
                                }
                            }
                        } else {
                            testStartMs = 0L
                            testPcmBuffer = null
                        }
                    } else {
                        delay(15)
                    }
                }
            } finally {
                try {
                    recorder.stop()
                } catch (_: Exception) {
                }
                try {
                    recorder.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .predictiveBackLayer(backVisualState)
            .testTag("sound_check_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = S.soundCheck.screenTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = S.soundCheck.screenSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("sound_check_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.common.back
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { deviceRefreshTick++ },
                        modifier = Modifier.testTag("refresh_mic_device_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = S.soundCheck.refreshMicDesc
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val startTestRecordAction: () -> Unit = {
            stopPlaybackIfNeeded()
            isMeasuringAmbient = false
            testReport = null
            testRecordElapsedSec = 0f
            testRecordProgress = 0f
            isTestRecording = true
        }
        val togglePlaybackAction: () -> Unit = {
            if (isPlayingBack) {
                stopPlaybackIfNeeded()
            } else if (tempWavFile.exists()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val mp = MediaPlayer().apply {
                            setDataSource(tempWavFile.absolutePath)
                            prepare()
                            start()
                        }
                        activePlayerRef[0] = mp
                        isPlayingBack = true
                        val totalDur = mp.duration.coerceAtLeast(1)
                        mp.setOnCompletionListener {
                            stopPlaybackIfNeeded()
                        }
                        while (isPlayingBack && mp.isPlaying) {
                            playbackProgress = (mp.currentPosition.toFloat() / totalDur).coerceIn(0f, 1f)
                            delay(35)
                        }
                    } catch (_: Exception) {
                        stopPlaybackIfNeeded()
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideSoundCheck = maxWidth >= 820.dp
            if (isWideSoundCheck) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 1120.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!hasAudioPermission) {
                        PermissionRequiredBanner(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            MicHardwareInfoCard(
                                micStatus = micStatus,
                                sampleRateHz = sampleRateHz,
                                audioSourceStrategy = audioSourceStrategy
                            )
                            RealTimeMonitorCard(
                                hasPermission = hasAudioPermission,
                                liveDb = liveDb,
                                livePeakRatio = livePeakRatio,
                                waveformBars = waveformBars,
                                waveformWriteIdx = waveformWriteIdx,
                                waveformFrameTick = waveformFrameTick,
                                isMeasuringAmbient = isMeasuringAmbient,
                                ambientProgress = ambientProgress,
                                ambientResult = ambientResult,
                                enabled = hasAudioPermission && !isTestRecording && !isPlayingBack,
                                onStartAmbientCheck = {
                                    ambientResult = null
                                    isMeasuringAmbient = true
                                }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            TestRecordAndPlaybackCard(
                                hasPermission = hasAudioPermission,
                                isTestRecording = isTestRecording,
                                testRecordProgress = testRecordProgress,
                                testRecordElapsedSec = testRecordElapsedSec,
                                isAnalyzingTest = isAnalyzingTest,
                                testReport = testReport,
                                isPlayingBack = isPlayingBack,
                                playbackProgress = playbackProgress,
                                onStartTestRecord = startTestRecordAction,
                                onStopTestRecordEarly = { stopTestRecordEarlySignal = true },
                                onTogglePlayback = togglePlaybackAction
                            )
                            if (onGoToRecordingWorkspace != null) {
                                Button(
                                    onClick = {
                                        stopPlaybackIfNeeded()
                                        onGoToRecordingWorkspace()
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("sound_check_go_to_record_button")
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = S.soundCheck.finishEnterStudio,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!hasAudioPermission) {
                        PermissionRequiredBanner(
                            onRequestPermission = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }

                    // 模块 1：当前收音麦克风与采录参数卡片（含蓝牙高频裁切预警）
                    MicHardwareInfoCard(
                        micStatus = micStatus,
                        sampleRateHz = sampleRateHz,
                        audioSourceStrategy = audioSourceStrategy
                    )

                    // 模块 2：实时环境与说话音量监测看板 + 3 秒测安静度
                    RealTimeMonitorCard(
                        hasPermission = hasAudioPermission,
                        liveDb = liveDb,
                        livePeakRatio = livePeakRatio,
                        waveformBars = waveformBars,
                        waveformWriteIdx = waveformWriteIdx,
                        waveformFrameTick = waveformFrameTick,
                        isMeasuringAmbient = isMeasuringAmbient,
                        ambientProgress = ambientProgress,
                        ambientResult = ambientResult,
                        enabled = hasAudioPermission && !isTestRecording && !isPlayingBack,
                        onStartAmbientCheck = {
                            ambientResult = null
                            isMeasuringAmbient = true
                        }
                    )

                    // 模块 3：5 秒试录与回放自检（含底噪、音量、喷麦爆音、高频裁切四项诊断）
                    TestRecordAndPlaybackCard(
                        hasPermission = hasAudioPermission,
                        isTestRecording = isTestRecording,
                        testRecordProgress = testRecordProgress,
                        testRecordElapsedSec = testRecordElapsedSec,
                        isAnalyzingTest = isAnalyzingTest,
                        testReport = testReport,
                        isPlayingBack = isPlayingBack,
                        playbackProgress = playbackProgress,
                        onStartTestRecord = startTestRecordAction,
                        onStopTestRecordEarly = {
                            stopTestRecordEarlySignal = true
                        },
                        onTogglePlayback = togglePlaybackAction
                    )

                    // 底部放心开录按钮
                    if (onGoToRecordingWorkspace != null) {
                        Button(
                            onClick = {
                                stopPlaybackIfNeeded()
                                onGoToRecordingWorkspace()
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("sound_check_go_to_record_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = S.soundCheck.finishEnterStudio,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredBanner(
    onRequestPermission: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = S.permissions.soundCheckMicTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = S.permissions.soundCheckMicBody,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
                lineHeight = 19.sp
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(S.permissions.allowMicButton, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * 模块 1：麦克风硬件与采录规格状态卡片
 */
@Composable
private fun MicHardwareInfoCard(
    micStatus: MicHardwareStatus,
    sampleRateHz: Int,
    audioSourceStrategy: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val sourceModeLabel = when (audioSourceStrategy) {
        "UNPROCESSED" -> S.soundCheck.audioSourceUnprocessed
        "MIC" -> S.soundCheck.audioSourceMic
        else -> S.soundCheck.audioSourceVoiceClean
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (micStatus.isBluetooth) {
            colorScheme.errorContainer.copy(alpha = 0.7f)
        } else {
            colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (micStatus.isBluetooth) colorScheme.error.copy(alpha = 0.45f)
            else colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (micStatus.isBluetooth) {
                            colorScheme.error.copy(alpha = 0.16f)
                        } else {
                            colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    micStatus.isBluetooth -> Icons.Default.BluetoothAudio
                                    micStatus.isExternalWiredOrUsb -> Icons.Default.HeadsetMic
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = if (micStatus.isBluetooth) colorScheme.error else colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = S.soundCheck.currentDevicePrefix.withArgs("device" to micStatus.deviceName),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = S.soundCheck.audioSpecFormatLine.withArgs(
                                "rate" to sampleRateHz,
                                "mode" to sourceModeLabel
                            ),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (micStatus.isBluetooth) {
                        colorScheme.error
                    } else {
                        colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = if (micStatus.isBluetooth) S.soundCheck.bluetoothCutBadge else S.soundCheck.fullBandBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        color = if (micStatus.isBluetooth) colorScheme.onError else colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (micStatus.isBluetooth) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = colorScheme.error,
                            modifier = Modifier.size(18.dp).padding(top = 1.dp)
                        )
                        Text(
                            text = if (DesktopAdaptiveState.isDesktopPlatform) {
                                S.soundCheck.bluetoothWarningDesktop
                            } else {
                                S.soundCheck.bluetoothWarningMobile
                            },
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 模块 2：实时环境与音量监测看板 + 3 秒环境安静度检测
 */
@Composable
private fun RealTimeMonitorCard(
    hasPermission: Boolean,
    liveDb: Float,
    livePeakRatio: Float,
    waveformBars: FloatArray,
    waveformWriteIdx: Int,
    waveformFrameTick: Int,
    isMeasuringAmbient: Boolean,
    ambientProgress: Float,
    ambientResult: AmbientNoiseCheckResult?,
    enabled: Boolean,
    onStartAmbientCheck: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    // Live Zone Classification
    val isClippingNow = livePeakRatio >= 0.94f || liveDb > -3.5f
    val isSpeakingRange = !isClippingNow && liveDb >= -34f
    val isSlightNoise = !isClippingNow && liveDb in -42f..-34f

    val liveZoneTitle = when {
        !hasPermission -> S.soundCheck.meterNeedPermission
        !enabled -> S.soundCheck.meterRecordingOrPlaying
        isClippingNow -> if (DesktopAdaptiveState.isDesktopPlatform) {
            S.soundCheck.meterClippingDesktop
        } else {
            S.soundCheck.meterClippingMobile
        }
        isSpeakingRange -> S.soundCheck.meterOptimal
        isSlightNoise -> S.soundCheck.meterSlightNoise
        else -> S.soundCheck.meterQuiet
    }

    val liveZoneColor = when {
        !hasPermission || !enabled -> colorScheme.outline
        isClippingNow -> colorScheme.error
        isSpeakingRange -> colorScheme.primary
        isSlightNoise -> colorScheme.tertiary
        else -> colorScheme.secondary
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = S.soundCheck.step1Title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (hasPermission) {
                            S.soundCheck.dbValueFormat.withArgs("db" to String.format(Locale.US, "%.0f", liveDb))
                        } else {
                            S.soundCheck.dbUnavailableLabel
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = liveZoneColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 实时滚动声波画布（GPU 直接绘制，无重组负担）
            val barQuietColor = colorScheme.secondary
            val barSpeakColor = colorScheme.primary
            val barLoudColor = colorScheme.error
            val trackBgColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(trackBgColor)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Read tick inside DrawScope so only Canvas redraws at 25fps
                    val unusedTick = waveformFrameTick
                    val count = waveformBars.size
                    val totalW = size.width
                    val totalH = size.height
                    val step = totalW / count
                    val barW = (step * 0.62f).coerceAtLeast(2f)

                    for (i in 0 until count) {
                        val ringIndex = (waveformWriteIdx + i) % count
                        val norm = waveformBars[ringIndex].coerceIn(0.04f, 1f)
                        val barH = (norm * totalH).coerceAtLeast(4f)
                        val x = i * step + (step - barW) / 2f
                        val y = (totalH - barH) / 2f

                        val barColor = when {
                            norm > 0.88f -> barLoudColor
                            norm > 0.35f -> barSpeakColor
                            else -> barQuietColor.copy(alpha = 0.7f)
                        }

                        drawRoundRect(
                            color = if (unusedTick >= 0) barColor else barQuietColor,
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            // 三段式音量区间指示条
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = liveZoneTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = liveZoneColor
                )

                val normProgress = ((liveDb + 60f) / 60f).coerceIn(0.02f, 1f)
                LinearProgressIndicator(
                    progress = { normProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = liveZoneColor,
                    trackColor = colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compactLegend = maxWidth < 330.dp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (compactLegend) S.soundCheck.legendQuietCompact else S.soundCheck.legendQuietFull,
                            fontSize = 11.sp,
                            color = colorScheme.outline,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = if (compactLegend) S.soundCheck.legendSpeechCompact else S.soundCheck.legendSpeechFull,
                            fontSize = 11.sp,
                            color = colorScheme.outline,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = S.soundCheck.legendClipping,
                            fontSize = 11.sp,
                            color = colorScheme.outline,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 4 秒多维环境安静度一键测试
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = if (isMeasuringAmbient) S.soundCheck.measuringAmbientTitle else S.soundCheck.checkAmbientReadyTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (isMeasuringAmbient) {
                            S.soundCheck.measuringAmbientSub
                        } else {
                            S.soundCheck.checkAmbientItemsSub
                        },
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onStartAmbientCheck,
                    enabled = enabled && !isMeasuringAmbient,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("measure_ambient_noise_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isMeasuringAmbient) {
                            S.soundCheck.measuringProgress.withArgs("percent" to (ambientProgress * 100).toInt())
                        } else if (ambientResult != null) {
                            S.soundCheck.remeasureAmbient
                        } else {
                            S.soundCheck.measure4sAmbient
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            AnimatedVisibility(
                visible = ambientResult != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ambientResult?.let { res ->
                    val containerColor = when (res.level) {
                        CheckGrade.GOOD -> colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        CheckGrade.WARN -> colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        CheckGrade.BAD -> colorScheme.errorContainer.copy(alpha = 0.65f)
                    }
                    val accentColor = when (res.level) {
                        CheckGrade.GOOD -> colorScheme.secondary
                        CheckGrade.WARN -> colorScheme.tertiary
                        CheckGrade.BAD -> colorScheme.error
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = containerColor,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = when (res.level) {
                                        CheckGrade.GOOD -> Icons.Default.CheckCircle
                                        CheckGrade.WARN -> Icons.Default.Warning
                                        CheckGrade.BAD -> Icons.Default.ErrorOutline
                                    },
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp).padding(top = 1.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = res.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = res.advice,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 4 项环境指标明细（任一不满足均清晰标出）
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                res.subChecks.forEach { sub ->
                                    val subColor = when (sub.grade) {
                                        CheckGrade.GOOD -> colorScheme.secondary
                                        CheckGrade.WARN -> colorScheme.tertiary
                                        CheckGrade.BAD -> colorScheme.error
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colorScheme.surface.copy(alpha = 0.82f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (sub.grade == CheckGrade.GOOD) colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            else subColor.copy(alpha = 0.45f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when (sub.grade) {
                                                        CheckGrade.GOOD -> Icons.Default.CheckCircle
                                                        CheckGrade.WARN -> Icons.Default.Warning
                                                        CheckGrade.BAD -> Icons.Default.ErrorOutline
                                                    },
                                                    contentDescription = null,
                                                    tint = subColor,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = S.soundCheck.colonLabelFormat.withArgs("label" to sub.label),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = sub.summary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (sub.grade == CheckGrade.GOOD) colorScheme.onSurface else subColor
                                                )
                                            }
                                            if (sub.grade != CheckGrade.GOOD || sub.detail.isNotBlank()) {
                                                Text(
                                                    text = sub.detail,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                    color = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 21.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模块 3：5 秒试录与回放自检（含环境底噪、说话音量、喷麦爆音、高频完整度四项体检）
 */
@Composable
private fun TestRecordAndPlaybackCard(
    hasPermission: Boolean,
    isTestRecording: Boolean,
    testRecordProgress: Float,
    testRecordElapsedSec: Float,
    isAnalyzingTest: Boolean,
    testReport: TestRecordingReport?,
    isPlayingBack: Boolean,
    playbackProgress: Float,
    onStartTestRecord: () -> Unit,
    onStopTestRecordEarly: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = S.soundCheck.step2Title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = S.soundCheck.step2Subtitle,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            // 试读参考句卡片（包含擦音 q、c、s、sh 与不同元音，专门用于检测高频裁切与喷麦）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = S.soundCheck.testPromptGuide,
                        fontSize = 12.sp,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = S.soundCheck.testSentence,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = S.soundCheck.testSentenceHint,
                        fontSize = 11.sp,
                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        lineHeight = 16.sp
                    )
                }
            }

            // 试录进度条或操作按钮行
            if (isTestRecording) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = S.soundCheck.testRecordingProgress.withArgs(
                                "sec" to String.format(Locale.US, "%.1f", testRecordElapsedSec)
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.error
                        )
                        Text(
                            text = S.soundCheck.tapFinishEarlyHint,
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    LinearProgressIndicator(
                        progress = { testRecordProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colorScheme.error,
                        trackColor = colorScheme.surfaceContainerHighest,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )

                    Button(
                        onClick = onStopTestRecordEarly,
                        enabled = testRecordElapsedSec >= 1.2f,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (testRecordElapsedSec < 1.2f) S.soundCheck.waitSentenceFinish else S.soundCheck.generateDiagnosisNow,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartTestRecord,
                        enabled = hasPermission && !isAnalyzingTest,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("start_test_record_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (testReport == null) S.soundCheck.start5sTestRecord else S.soundCheck.reTestRecord,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (testReport != null) {
                        FilledTonalButton(
                            onClick = onTogglePlayback,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isPlayingBack) colorScheme.tertiaryContainer else colorScheme.secondaryContainer,
                                contentColor = if (isPlayingBack) colorScheme.onTertiaryContainer else colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("playback_test_record_button")
                        ) {
                            Icon(
                                imageVector = if (isPlayingBack) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isPlayingBack) S.common.stop else S.common.play,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (isPlayingBack) {
                LinearProgressIndicator(
                    progress = { playbackProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colorScheme.secondary,
                    trackColor = colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }

            // 四维试录体检报告展示
            AnimatedVisibility(
                visible = testReport != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                testReport?.let { rep ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (rep.allPassed) S.soundCheck.summaryAllPassed else S.soundCheck.summaryNeedsAttention,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (rep.allPassed) colorScheme.secondary else colorScheme.tertiary
                            )
                            Text(
                                text = S.soundCheck.testDurationFormat.withArgs(
                                    "sec" to String.format(Locale.US, "%.1f", rep.durationSec)
                                ),
                                fontSize = 11.sp,
                                color = colorScheme.outline
                            )
                        }

                        // 1. 说话音量
                        DiagnosticCheckItemRow(
                            label = S.soundCheck.metricVolume,
                            grade = rep.volumeGrade,
                            title = rep.volumeTitle,
                            detail = rep.volumeDetail
                        )

                        // 2. 有无喷麦爆音
                        DiagnosticCheckItemRow(
                            label = S.soundCheck.metricClipping,
                            grade = rep.clippingGrade,
                            title = rep.clippingTitle,
                            detail = rep.clippingDetail
                        )

                        // 3. 有无频率裁切（防蓝牙/通话降噪一刀切高频）
                        DiagnosticCheckItemRow(
                            label = S.soundCheck.metricFreqCut,
                            grade = rep.bandwidthGrade,
                            title = rep.bandwidthTitle,
                            detail = rep.bandwidthDetail
                        )

                        // 4. 背景底噪
                        DiagnosticCheckItemRow(
                            label = S.soundCheck.metricQuietness,
                            grade = rep.noiseGrade,
                            title = rep.noiseTitle,
                            detail = rep.noiseDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCheckItemRow(
    label: String,
    grade: CheckGrade,
    title: String,
    detail: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = when (grade) {
        CheckGrade.GOOD -> colorScheme.secondary
        CheckGrade.WARN -> colorScheme.tertiary
        CheckGrade.BAD -> colorScheme.error
    }
    val containerColor = when (grade) {
        CheckGrade.GOOD -> colorScheme.surfaceContainer
        CheckGrade.WARN -> colorScheme.tertiaryContainer.copy(alpha = 0.45f)
        CheckGrade.BAD -> colorScheme.errorContainer.copy(alpha = 0.55f)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = when (grade) {
                    CheckGrade.GOOD -> Icons.Default.CheckCircle
                    CheckGrade.WARN -> Icons.Default.Warning
                    CheckGrade.BAD -> Icons.Default.ErrorOutline
                },
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp).padding(top = 2.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = S.soundCheck.colonLabelFormat.withArgs("label" to label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface
                    )
                }
                Text(
                    text = detail,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun detectActiveMicHardware(context: Context): MicHardwareStatus {
    return try {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return MicHardwareStatus()
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val btDevice = inputs.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        if (btDevice != null) {
            val productName = btDevice.productName?.toString()?.takeIf { it.isNotBlank() }
            return MicHardwareStatus(
                deviceName = if (productName != null) S.soundCheck.bluetoothMicWithName.withArgs("name" to productName) else S.soundCheck.bluetoothHeadsetMic,
                isBluetooth = true,
                isExternalWiredOrUsb = false
            )
        }

        val usbDevice = inputs.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (usbDevice != null) {
            val productName = usbDevice.productName?.toString()?.takeIf { it.isNotBlank() }
            return MicHardwareStatus(
                deviceName = if (productName != null) S.soundCheck.usbMicWithName.withArgs("name" to productName) else S.soundCheck.usbMicGeneric,
                isBluetooth = false,
                isExternalWiredOrUsb = true
            )
        }

        val wiredDevice = inputs.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        if (wiredDevice != null) {
            return MicHardwareStatus(
                deviceName = S.soundCheck.wiredHeadsetMic,
                isBluetooth = false,
                isExternalWiredOrUsb = true
            )
        }

        MicHardwareStatus(
            deviceName = if (DesktopAdaptiveState.isDesktopPlatform) S.soundCheck.defaultMicDesktop else S.soundCheck.defaultMicMobile,
            isBluetooth = false,
            isExternalWiredOrUsb = false
        )
    } catch (_: Exception) {
        MicHardwareStatus()
    }
}

/**
 * 多条件环境安静度算法（一票否决制）：
 * 不仅看平均底噪，还同时严格检查：
 * 1. 持续底噪水平（平均/中位数分贝）
 * 2. 间歇性突发异响（峰值冲高、相对于平时底噪的突刺跳变、瞬态敲击碰撞次数）
 * 3. 环境声音平稳度（90分位与10分位跨度 + 标准差，抓忽大忽小的风声或远处断续说话）
 * 4. 低频电器嗡鸣（240Hz 以下能量占比与强度，抓风扇、空调、桌面共振）
 * 只要上述 4 项中有任意一项不达标，整体结果立即告警并指出具体原因。
 */
private fun evaluateMultiConditionAmbientNoise(
    frameDbs: List<Float>,
    framePeakDbs: List<Float>,
    lowFreqDbs: List<Float>,
    lowFreqRatios: List<Float>
): AmbientNoiseCheckResult {
    if (frameDbs.isEmpty()) {
        return AmbientNoiseCheckResult(
            avgDb = -55f,
            maxPeakDb = -50f,
            fluctuationDb = 0f,
            burstCount = 0,
            level = CheckGrade.GOOD,
            title = S.soundCheck.ambientAllCleanTitle,
            advice = S.soundCheck.ambientAllCleanAdvice,
            subChecks = emptyList()
        )
    }

    val n = frameDbs.size
    val sorted = frameDbs.sorted()
    val avgDb = frameDbs.sum() / n
    val medianDb = sorted[n / 2]
    val p10Db = sorted[(n * 0.10f).toInt().coerceIn(0, n - 1)]
    val p90Db = sorted[(n * 0.90f).toInt().coerceIn(0, n - 1)]
    val maxFrameDb = sorted.last()
    val maxSamplePeakDb = framePeakDbs.maxOrNull() ?: maxFrameDb

    // 计算标准差与 10%~90% 动态跨度
    var varianceSum = 0.0
    for (db in frameDbs) {
        val diff = (db - avgDb).toDouble()
        varianceSum += diff * diff
    }
    val stdDevDb = sqrt(varianceSum / n).toFloat()
    val fluctuationRangeDb = (p90Db - p10Db).coerceAtLeast(0f)

    // 计算间歇性突发异响次数（比平时中位底噪突然高出 7.5dB 以上，或单帧冲过 -35dB / 瞬态峰值冲过 -20dB）
    // 设置 7 帧（约 160ms）冷却窗口，把同一次敲击/咳嗽聚合成 1 次突发事件
    val burstThresholdDb = max(medianDb + 7.5f, -40f)
    var burstCount = 0
    var cooldownFrames = 0
    for (i in 0 until n) {
        if (cooldownFrames > 0) {
            cooldownFrames--
            continue
        }
        val fDb = frameDbs[i]
        val pDb = framePeakDbs.getOrElse(i) { fDb }
        val isSuddenSpike = fDb >= burstThresholdDb || fDb >= -34.5f || (pDb >= -19f && pDb - medianDb >= 14f)
        if (isSuddenSpike) {
            burstCount++
            cooldownFrames = 7
        }
    }
    val crestJumpDb = (maxFrameDb - medianDb).coerceAtLeast(0f)

    // 计算低频嗡鸣指标
    val avgLowFreqDb = if (lowFreqDbs.isNotEmpty()) lowFreqDbs.sum() / lowFreqDbs.size else -55f
    val avgLowRatio = if (lowFreqRatios.isNotEmpty()) lowFreqRatios.sum() / lowFreqRatios.size else 0f

    // --- 条件 1：持续底噪均值 ---
    val avgDbStr = String.format(Locale.US, "%.0f", avgDb)
    val steadyCheck = when {
        avgDb < -42f && medianDb < -43f -> AmbientSubCheck(
            label = S.soundCheck.itemSustainedNoise,
            grade = CheckGrade.GOOD,
            summary = S.soundCheck.itemSustainedNoiseLowSummary.withArgs("db" to avgDbStr),
            detail = S.soundCheck.itemSustainedNoiseLowDetail
        )
        avgDb < -33f -> AmbientSubCheck(
            label = S.soundCheck.itemSustainedNoise,
            grade = CheckGrade.WARN,
            summary = S.soundCheck.itemSustainedNoiseMedSummary.withArgs("db" to avgDbStr),
            detail = S.soundCheck.itemSustainedNoiseMedDetail
        )
        else -> AmbientSubCheck(
            label = S.soundCheck.itemSustainedNoise,
            grade = CheckGrade.BAD,
            summary = S.soundCheck.itemSustainedNoiseHighSummary.withArgs("db" to avgDbStr),
            detail = S.soundCheck.itemSustainedNoiseHighDetail
        )
    }

    // --- 条件 2：突发声响与偶发异响（专门抓“时不时传出声响”） ---
    val maxFrameDbStr = String.format(Locale.US, "%.0f", maxFrameDb)
    val crestJumpDbStr = String.format(Locale.US, "%.0f", crestJumpDb)
    val spikeCheck = when {
        maxFrameDb > -26f || maxSamplePeakDb > -13f || burstCount >= 3 || crestJumpDb >= 14.5f -> AmbientSubCheck(
            label = S.soundCheck.itemImpulseNoise,
            grade = CheckGrade.BAD,
            summary = S.soundCheck.itemImpulseHighSummary.withArgs(
                "count" to burstCount.coerceAtLeast(1),
                "db" to maxFrameDbStr
            ),
            detail = S.soundCheck.itemImpulseHighDetail.withArgs("jump" to crestJumpDbStr)
        )
        maxFrameDb > -35f || maxSamplePeakDb > -21f || burstCount >= 1 || crestJumpDb >= 8.5f -> AmbientSubCheck(
            label = S.soundCheck.itemImpulseNoise,
            grade = CheckGrade.WARN,
            summary = S.soundCheck.itemImpulseSlightSummary.withArgs(
                "count" to burstCount.coerceAtLeast(1),
                "db" to maxFrameDbStr
            ),
            detail = S.soundCheck.itemImpulseSlightDetail.withArgs("jump" to crestJumpDbStr)
        )
        else -> AmbientSubCheck(
            label = S.soundCheck.itemImpulseNoise,
            grade = CheckGrade.GOOD,
            summary = S.soundCheck.itemImpulseCleanSummary.withArgs("db" to maxFrameDbStr),
            detail = S.soundCheck.itemImpulseCleanDetail
        )
    }

    // --- 条件 3：背景平稳度（抓忽大忽小的声音） ---
    val fluctuationStr = String.format(Locale.US, "%.1f", fluctuationRangeDb)
    val stabilityCheck = when {
        fluctuationRangeDb >= 10.5f || stdDevDb >= 4.0f -> AmbientSubCheck(
            label = S.soundCheck.itemStability,
            grade = CheckGrade.BAD,
            summary = S.soundCheck.itemStabilityUnstableSummary.withArgs("range" to fluctuationStr),
            detail = S.soundCheck.itemStabilityUnstableDetail
        )
        fluctuationRangeDb >= 6.2f || stdDevDb >= 2.5f -> AmbientSubCheck(
            label = S.soundCheck.itemStability,
            grade = CheckGrade.WARN,
            summary = S.soundCheck.itemStabilitySlightSummary.withArgs("range" to fluctuationStr),
            detail = S.soundCheck.itemStabilitySlightDetail
        )
        else -> AmbientSubCheck(
            label = S.soundCheck.itemStability,
            grade = CheckGrade.GOOD,
            summary = S.soundCheck.itemStabilitySteadySummary.withArgs("range" to fluctuationStr),
            detail = S.soundCheck.itemStabilitySteadyDetail
        )
    }

    // --- 条件 4：低频电器嗡鸣与桌面震动 ---
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val rumbleCheck = when {
        avgLowFreqDb > -34f && avgLowRatio > 0.75f -> AmbientSubCheck(
            label = S.soundCheck.itemLowFreqHum,
            grade = CheckGrade.BAD,
            summary = S.soundCheck.itemLowFreqSevereSummary,
            detail = if (isDesktop) {
                S.soundCheck.itemLowFreqSevereDesktopDetail
            } else {
                S.soundCheck.itemLowFreqSevereMobileDetail
            }
        )
        avgLowFreqDb > -40f && avgLowRatio > 0.70f -> AmbientSubCheck(
            label = S.soundCheck.itemLowFreqHum,
            grade = CheckGrade.WARN,
            summary = S.soundCheck.itemLowFreqSlightSummary,
            detail = if (isDesktop) {
                S.soundCheck.itemLowFreqSlightDesktopDetail
            } else {
                S.soundCheck.itemLowFreqSlightMobileDetail
            }
        )
        else -> AmbientSubCheck(
            label = S.soundCheck.itemLowFreqHum,
            grade = CheckGrade.GOOD,
            summary = S.soundCheck.itemLowFreqCleanSummary,
            detail = S.soundCheck.itemLowFreqCleanDetail
        )
    }

    val allSubChecks = listOf(steadyCheck, spikeCheck, stabilityCheck, rumbleCheck)

    // 严格一票否决制：取 4 项中最差的一项作为总体结论
    val overallLevel = when {
        allSubChecks.any { it.grade == CheckGrade.BAD } -> CheckGrade.BAD
        allSubChecks.any { it.grade == CheckGrade.WARN } -> CheckGrade.WARN
        else -> CheckGrade.GOOD
    }

    val failedItems = allSubChecks.filter { it.grade != CheckGrade.GOOD }
    val (overallTitle, overallAdvice) = when (overallLevel) {
        CheckGrade.GOOD -> {
            S.soundCheck.ambientPassTitle to S.soundCheck.ambientPassDetail
        }
        CheckGrade.WARN -> {
            val failedNames = failedItems.joinToString(S.soundCheck.listSeparator) { it.label }
            val specificReason = when {
                steadyCheck.grade == CheckGrade.GOOD && spikeCheck.grade != CheckGrade.GOOD ->
                    S.soundCheck.ambientWarnImpulseDetail.withArgs("db" to avgDbStr, "max" to maxFrameDbStr)
                steadyCheck.grade == CheckGrade.GOOD && stabilityCheck.grade != CheckGrade.GOOD ->
                    S.soundCheck.ambientWarnFluctuationDetail.withArgs("range" to fluctuationStr)
                else ->
                    S.soundCheck.ambientWarnGenericDetail.withArgs("items" to failedNames)
            }
            S.soundCheck.ambientWarnTitle.withArgs("items" to failedNames) to specificReason
        }
        CheckGrade.BAD -> {
            val badNames = allSubChecks.filter { it.grade == CheckGrade.BAD }.joinToString(S.soundCheck.listSeparator) { it.label }
            val specificReason = when {
                steadyCheck.grade == CheckGrade.GOOD && (spikeCheck.grade == CheckGrade.BAD || stabilityCheck.grade == CheckGrade.BAD) ->
                    S.soundCheck.ambientFailCombinedDetail.withArgs("db" to avgDbStr, "max" to maxFrameDbStr)
                else ->
                    S.soundCheck.ambientFailGenericDetail.withArgs("items" to badNames)
            }
            S.soundCheck.ambientFailTitle.withArgs("items" to badNames) to specificReason
        }
    }

    return AmbientNoiseCheckResult(
        avgDb = avgDb,
        maxPeakDb = maxFrameDb,
        fluctuationDb = fluctuationRangeDb,
        burstCount = burstCount,
        level = overallLevel,
        title = overallTitle,
        advice = overallAdvice,
        subChecks = allSubChecks
    )
}

/**
 * 对 5 秒试录音频执行四维体检：
 * 1. 背景底噪与停顿间隙异响
 * 2. 说话音量
 * 3. 喷麦与爆音削波
 * 4. 高频完整度（通过 512 点 FFT 分析 4kHz / 8kHz 以上频段能量是否被蓝牙或通话滤波一刀切）
 */
private fun analyzeTestRecording(
    samples: ShortArray,
    sampleRate: Int,
    isBluetoothMic: Boolean
): TestRecordingReport {
    val frameSize = 512
    val hopSize = 256
    val frameDbs = ArrayList<Float>()
    var globalMaxAbs = 0
    var clippingSampleCount = 0

    for (s in samples) {
        val a = abs(s.toInt())
        if (a > globalMaxAbs) globalMaxAbs = a
        if (a >= 31800) clippingSampleCount++
    }

    var idx = 0
    while (idx + frameSize <= samples.size) {
        var sumSq = 0.0
        for (i in 0 until frameSize) {
            val v = samples[idx + i].toDouble()
            sumSq += v * v
        }
        val rms = sqrt(sumSq / frameSize).toFloat()
        val norm = (rms / 32768f).coerceAtLeast(1e-5f)
        val db = (20.0 * log10(norm.toDouble())).toFloat().coerceIn(-60f, 0f)
        frameDbs.add(db)
        idx += hopSize
    }

    val sortedDbs = frameDbs.sorted()
    val totalFrames = sortedDbs.size.coerceAtLeast(1)

    // Lowest 25% frames represent background silence / pauses
    val quietCount = (totalFrames * 0.25f).toInt().coerceAtLeast(1)
    val quietSlice = sortedDbs.subList(0, quietCount)
    val noiseFloorDb = quietSlice.sum() / quietCount
    val quietP10 = quietSlice[(quietSlice.size * 0.1f).toInt().coerceIn(0, quietSlice.size - 1)]
    val quietP90 = quietSlice[(quietSlice.size * 0.9f).toInt().coerceIn(0, quietSlice.size - 1)]
    val quietSpreadDb = (quietP90 - quietP10).coerceAtLeast(0f)

    // Also check initial 0.8s (the requested "quiet 1 second before speaking") for intermittent noises
    val introFramesCount = min((0.8f * sampleRate / hopSize).toInt().coerceAtLeast(1), frameDbs.size / 3)
    val introSlice = if (introFramesCount >= 4) frameDbs.subList(0, introFramesCount) else quietSlice
    val introMaxDb = introSlice.maxOrNull() ?: noiseFloorDb
    val introMedianDb = introSlice.sorted()[introSlice.size / 2]
    val introHasSpike = (introMaxDb - introMedianDb >= 9.5f && introMaxDb > -34f)

    // Top 25% frames represent active speech syllables
    val speechStart = (totalFrames * 0.75f).toInt().coerceIn(0, totalFrames - 1)
    val speechSlice = sortedDbs.subList(speechStart, totalFrames)
    val speechDb = speechSlice.sum() / speechSlice.size.coerceAtLeast(1)

    val maxPeakRatio = (globalMaxAbs / 32768f).coerceIn(0f, 1f)
    val durationSec = samples.size.toFloat() / sampleRate.coerceAtLeast(1)

    val isDesktop = DesktopAdaptiveState.isDesktopPlatform

    // 1. 说话音量诊断
    val speechDbStr = String.format(Locale.US, "%.0f", speechDb)
    val (volumeGrade, volumeTitle, volumeDetail) = when {
        speechDb < -35f -> Triple(
            CheckGrade.BAD,
            S.soundCheck.speechTooQuietTitle,
            if (isDesktop) S.soundCheck.speechTooQuietDesktopDetail else S.soundCheck.speechTooQuietMobileDetail
        )
        speechDb < -26f -> Triple(
            CheckGrade.WARN,
            S.soundCheck.speechSlightlyLowTitle,
            S.soundCheck.speechSlightlyLowDetail
        )
        speechDb <= -3.5f -> Triple(
            CheckGrade.GOOD,
            S.soundCheck.speechGoodTitle,
            S.soundCheck.speechGoodDetail.withArgs("db" to speechDbStr)
        )
        else -> Triple(
            CheckGrade.WARN,
            S.soundCheck.speechSlightlyLoudTitle,
            if (isDesktop) S.soundCheck.speechSlightlyLoudDesktopDetail else S.soundCheck.speechSlightlyLoudMobileDetail
        )
    }

    // 2. 喷麦与爆音诊断
    val (clippingGrade, clippingTitle, clippingDetail) = when {
        clippingSampleCount >= 12 || maxPeakRatio >= 0.98f -> Triple(
            CheckGrade.BAD,
            S.soundCheck.clippingDetectedTitle,
            if (isDesktop) S.soundCheck.clippingDetectedDesktopDetail else S.soundCheck.clippingDetectedMobileDetail
        )
        maxPeakRatio >= 0.91f -> Triple(
            CheckGrade.WARN,
            S.soundCheck.peakNearLimitTitle,
            if (isDesktop) S.soundCheck.peakNearLimitDesktopDetail else S.soundCheck.peakNearLimitMobileDetail
        )
        else -> Triple(
            CheckGrade.GOOD,
            S.soundCheck.noClippingTitle,
            S.soundCheck.noClippingDetail
        )
    }

    // 3. 背景底噪与间歇杂音综合诊断
    val noiseFloorDbStr = String.format(Locale.US, "%.0f", noiseFloorDb)
    val introMaxDbStr = String.format(Locale.US, "%.0f", introMaxDb)
    val quietSpreadDbStr = String.format(Locale.US, "%.1f", quietSpreadDb)
    val (noiseGrade, noiseTitle, noiseDetail) = when {
        noiseFloorDb >= -31f -> Triple(
            CheckGrade.BAD,
            S.soundCheck.pauseNoisyTitle,
            S.soundCheck.pauseNoisyDetail.withArgs("db" to noiseFloorDbStr)
        )
        introHasSpike -> Triple(
            CheckGrade.WARN,
            S.soundCheck.pauseImpulseTitle,
            S.soundCheck.pauseImpulseDetail.withArgs("db" to introMaxDbStr)
        )
        quietSpreadDb >= 6.5f || noiseFloorDb >= -41f -> Triple(
            CheckGrade.WARN,
            S.soundCheck.pauseSlightNoiseTitle,
            S.soundCheck.pauseSlightNoiseDetail.withArgs(
                "db" to noiseFloorDbStr,
                "spread" to quietSpreadDbStr
            )
        )
        else -> Triple(
            CheckGrade.GOOD,
            S.soundCheck.pauseCleanTitle,
            S.soundCheck.pauseCleanDetail.withArgs("db" to noiseFloorDbStr)
        )
    }

    // 4. 高频完整度（有无频率裁切）诊断
    // 使用 512 点 FFT 分析有效发音帧在 300-3400Hz、4200-7600Hz、8200-14500Hz 三个频段的能量比
    val cutoffEstimate = detectHighFrequencyCutoffHz(
        samples = samples,
        sampleRate = sampleRate,
        speechThresholdDb = max(noiseFloorDb + 8f, -38f)
    )

    val (bandwidthGrade, bandwidthTitle, bandwidthDetail) = when {
        speechDb < -36f -> Triple(
            CheckGrade.WARN,
            S.soundCheck.freqCutTooQuietTitle,
            S.soundCheck.freqCutTooQuietDetail
        )
        isBluetoothMic || cutoffEstimate == 4000 -> Triple(
            CheckGrade.BAD,
            S.soundCheck.freqCut4kTitle,
            if (isDesktop) S.soundCheck.freqCut4kDesktopDetail else S.soundCheck.freqCut4kMobileDetail
        )
        cutoffEstimate == 8000 -> Triple(
            CheckGrade.WARN,
            S.soundCheck.freqCut8kTitle,
            if (isDesktop) S.soundCheck.freqCut8kDesktopDetail else S.soundCheck.freqCut8kMobileDetail
        )
        else -> Triple(
            CheckGrade.GOOD,
            S.soundCheck.freqFullPassTitle,
            S.soundCheck.freqFullPassDetail
        )
    }

    return TestRecordingReport(
        durationSec = durationSec,
        noiseFloorDb = noiseFloorDb,
        speechDb = speechDb,
        maxPeakRatio = maxPeakRatio,
        cutoffHzEstimate = cutoffEstimate,
        noiseGrade = noiseGrade,
        noiseTitle = noiseTitle,
        noiseDetail = noiseDetail,
        volumeGrade = volumeGrade,
        volumeTitle = volumeTitle,
        volumeDetail = volumeDetail,
        clippingGrade = clippingGrade,
        clippingTitle = clippingTitle,
        clippingDetail = clippingDetail,
        bandwidthGrade = bandwidthGrade,
        bandwidthTitle = bandwidthTitle,
        bandwidthDetail = bandwidthDetail
    )
}

/**
 * 检测音频是否存在 4kHz（8kHz 采样率窄带）或 8kHz（16kHz 采样率宽带）的砖墙式高频一刀切。
 * 返回值：
 * - 0：全频段正常（无裁切）
 * - 4000：4kHz 以上几乎无能量（8kHz 蓝牙窄带裁切）
 * - 8000：8kHz 以上几乎无能量（16kHz 蓝牙/语音滤波裁切）
 */
private fun detectHighFrequencyCutoffHz(
    samples: ShortArray,
    sampleRate: Int,
    speechThresholdDb: Float
): Int {
    if (sampleRate < 32000 || samples.size < 1024) return 0

    val n = 512
    val hop = 256
    val window = FloatArray(n) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()
    }
    val real = FloatArray(n)
    val imag = FloatArray(n)

    val binHz = sampleRate.toFloat() / n
    val lowStartBin = (300f / binHz).toInt().coerceIn(1, n / 2 - 1)
    val lowEndBin = (3400f / binHz).toInt().coerceIn(lowStartBin + 1, n / 2 - 1)

    val midHighStartBin = (4300f / binHz).toInt().coerceIn(lowEndBin + 1, n / 2 - 1)
    val midHighEndBin = (7500f / binHz).toInt().coerceIn(midHighStartBin + 1, n / 2 - 1)

    val ultraHighStartBin = (8300f / binHz).toInt().coerceIn(midHighEndBin + 1, n / 2 - 1)
    val ultraHighEndBin = (15000f / binHz).toInt().coerceIn(ultraHighStartBin + 1, n / 2 - 1)

    var maxLowBandDb = -100f
    var maxMidHighBandDb = -100f
    var maxUltraHighBandDb = -100f
    var analyzedFrames = 0

    var offset = 0
    while (offset + n <= samples.size) {
        var sumSq = 0.0
        for (i in 0 until n) {
            val s = samples[offset + i].toFloat() / 32768f
            sumSq += s * s
            real[i] = s * window[i]
            imag[i] = 0f
        }
        val rms = sqrt(sumSq / n).toFloat().coerceAtLeast(1e-5f)
        val frameDb = (20.0 * log10(rms.toDouble())).toFloat()

        if (frameDb >= speechThresholdDb) {
            fftRadix2(real, imag, n)
            analyzedFrames++

            var lowSum = 0.0
            for (b in lowStartBin..lowEndBin) {
                lowSum += (real[b] * real[b] + imag[b] * imag[b]).toDouble()
            }
            val lowMean = (lowSum / (lowEndBin - lowStartBin + 1)).coerceAtLeast(1e-12)
            val lowDb = (10.0 * log10(lowMean)).toFloat()
            if (lowDb > maxLowBandDb) maxLowBandDb = lowDb

            var midHighSum = 0.0
            for (b in midHighStartBin..midHighEndBin) {
                midHighSum += (real[b] * real[b] + imag[b] * imag[b]).toDouble()
            }
            val midHighMean = (midHighSum / (midHighEndBin - midHighStartBin + 1)).coerceAtLeast(1e-12)
            val midHighDb = (10.0 * log10(midHighMean)).toFloat()
            if (midHighDb > maxMidHighBandDb) maxMidHighBandDb = midHighDb

            var ultraHighSum = 0.0
            for (b in ultraHighStartBin..ultraHighEndBin) {
                ultraHighSum += (real[b] * real[b] + imag[b] * imag[b]).toDouble()
            }
            val ultraHighMean = (ultraHighSum / (ultraHighEndBin - ultraHighStartBin + 1)).coerceAtLeast(1e-12)
            val ultraHighDb = (10.0 * log10(ultraHighMean)).toFloat()
            if (ultraHighDb > maxUltraHighBandDb) maxUltraHighBandDb = ultraHighDb
        }
        offset += hop
    }

    if (analyzedFrames < 3 || maxLowBandDb < -65f) return 0

    // If 4.3kHz-7.5kHz peak energy is > 40 dB below low band AND absolute energy is near digital silence
    val drop4k = maxLowBandDb - maxMidHighBandDb
    val drop8k = maxLowBandDb - maxUltraHighBandDb

    return when {
        drop4k > 42f && maxMidHighBandDb < -72f -> 4000
        drop8k > 45f && maxUltraHighBandDb < -76f -> 8000
        else -> 0
    }
}

private fun fftRadix2(real: FloatArray, imag: FloatArray, n: Int) {
    var j = 0
    for (i in 0 until n) {
        if (i < j) {
            val tr = real[i]
            real[i] = real[j]
            real[j] = tr
            val ti = imag[i]
            imag[i] = imag[j]
            imag[j] = ti
        }
        var m = n shr 1
        while (m >= 1 && j >= m) {
            j -= m
            m = m shr 1
        }
        j += m
    }

    var len = 2
    while (len <= n) {
        val halfLen = len shr 1
        val angle = -2.0 * PI / len
        val wLenR = cos(angle).toFloat()
        val wLenI = kotlin.math.sin(angle).toFloat()
        var i = 0
        while (i < n) {
            var wR = 1f
            var wI = 0f
            for (k in 0 until halfLen) {
                val uIdx = i + k
                val vIdx = i + k + halfLen
                val tR = wR * real[vIdx] - wI * imag[vIdx]
                val tI = wR * imag[vIdx] + wI * real[vIdx]
                real[vIdx] = real[uIdx] - tR
                imag[vIdx] = imag[uIdx] - tI
                real[uIdx] = real[uIdx] + tR
                imag[uIdx] = imag[uIdx] + tI
                val nextWR = wR * wLenR - wI * wLenI
                wI = wR * wLenI + wI * wLenR
                wR = nextWR
            }
            i += len
        }
        len = len shl 1
    }
}

private fun writeMono16BitWav(file: File, samples: ShortArray, sampleRate: Int) {
    try {
        val dataBytes = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // PCM chunk size
        header.putShort(1) // PCM format
        header.putShort(1) // Mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // ByteRate
        header.putShort(2) // BlockAlign
        header.putShort(16) // BitsPerSample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes)

        val pcmBytes = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            pcmBytes.putShort(s)
        }

        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            fos.write(pcmBytes.array())
            fos.flush()
        }
    } catch (_: Exception) {
    }
}
