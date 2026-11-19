package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AcousticAnalyzer
import com.example.data.Project
import com.example.data.ShufflingMode
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioSwitchRow
import com.example.viewmodel.RecordingViewModel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LiveStudioMonitorSection(
    viewModel: RecordingViewModel,
    currentWord: Word,
    activeProject: Project?,
    participantCleanMode: Boolean,
    isCompact: Boolean = false,
    takeCount: Int = 0,
    onOpenTakeSwitcher: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val liveDbfs by viewModel.liveDbfs.collectAsState()
    val liveElapsedMs by viewModel.liveElapsedMs.collectAsState()
    val waveformPoints by viewModel.recordingWaveform.collectAsState()
    val silenceProgress by viewModel.silenceProgress.collectAsState()
    val isClipping by viewModel.isClipping.collectAsState()
    val isTooQuiet by viewModel.isTooQuiet.collectAsState()
    val isAutoNext by viewModel.autoNextEnabled.collectAsState()
    val isPushToTalk by viewModel.pushToTalkEnabled.collectAsState()

    // Load saved WAV envelope when not actively recording
    val recordedEnvelope by produceState<List<Float>>(
        initialValue = emptyList(),
        isRecording,
        currentWord.isRecorded,
        currentWord.recordingFile,
        activeProject?.id
    ) {
        if (!isRecording && currentWord.isRecorded && activeProject != null && !currentWord.recordingFile.isNullOrBlank()) {
            val pid = activeProject.id
            val fileName = currentWord.recordingFile
            value = withContext(Dispatchers.IO) {
                val file = File(File(context.getExternalFilesDir(null), "project_$pid"), fileName)
                if (file.exists()) {
                    AcousticAnalyzer.getRealWaveformEnvelope(file, 48)
                } else {
                    emptyList()
                }
            }
        } else {
            value = emptyList()
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 7.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 8.dp)
        ) {
            // Top Status Row: Human-friendly recording status + multi-take badge + clear volume status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRecording -> MaterialTheme.colorScheme.error
                                    currentWord.isRecorded -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Text(
                        text = if (isRecording) {
                            S.recording.recordingTimerLabel.withArgs(
                                "time" to String.format(
                                    Locale.US,
                                    "%02d:%04.1f",
                                    (liveElapsedMs / 1000) / 60,
                                    (liveElapsedMs % 60000) / 1000f
                                )
                            )
                        } else if (currentWord.isRecorded) {
                            val secStr = String.format(Locale.US, "%.1f", currentWord.durationMs / 1000f)
                            if (DesktopAdaptiveState.isDesktopPlatform) {
                                S.recording.desktopRecordedHint.withArgs("sec" to secStr)
                            } else {
                                S.recording.mobileRecordedHint.withArgs("sec" to secStr)
                            }
                        } else if (isPushToTalk) {
                            S.recording.holdToRecordHint
                        } else if (DesktopAdaptiveState.isDesktopPlatform) {
                            S.recording.desktopReadyHint
                        } else {
                            S.recording.mobileReadyHint
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!participantCleanMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isRecording && takeCount >= 2) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenTakeSwitcher)
                                    .testTag("multi_take_switcher_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = S.recording.switchTakeDesc,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = S.recording.takeCountBadge.withArgs("count" to takeCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        val levelHint = when {
                            !isRecording && currentWord.isRecorded -> S.recording.levelRerecordable
                            !isRecording -> ""
                            liveDbfs > -1.5f -> S.recording.levelClipping
                            liveDbfs > -24.0f -> S.recording.levelOptimal
                            liveDbfs > -42.0f -> S.recording.levelTooQuiet
                            else -> S.recording.levelWaiting
                        }
                        if (levelHint.isNotEmpty()) {
                            Text(
                                text = levelHint,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = when {
                                    isRecording && liveDbfs > -1.5f -> MaterialTheme.colorScheme.error
                                    isRecording && liveDbfs > -24.0f -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // Real-time Volume Bar
            DbfsVuMeterBar(
                dbfs = if (isRecording) liveDbfs else -60f,
                isRecording = isRecording
            )

            // Symmetrical Zero-Baseline Waveform
            SymmetricalAudioWaveform(
                amplitudes = if (isRecording) waveformPoints else recordedEnvelope,
                isRecording = isRecording,
                isClipping = isClipping,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        when {
                            isCompact -> 36.dp
                            participantCleanMode -> 44.dp
                            else -> 54.dp
                        }
                    )
            )

            // Friendly Guidance Banner or Auto-Flip Progress
            if (isRecording) {
                val statusMessage = when {
                    isClipping -> S.recording.promptClippingHint
                    isTooQuiet -> S.recording.promptTooQuietHint
                    else -> S.recording.promptNormalSpeakHint
                }
                val statusColor = when {
                    isClipping -> MaterialTheme.colorScheme.error
                    isTooQuiet -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isPushToTalk && isAutoNext && silenceProgress > 0.02f) {
                        Spacer(Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { silenceProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(64.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DbfsVuMeterBar(
    dbfs: Float,
    isRecording: Boolean
) {
    val normalizedLevel = if (isRecording) ((dbfs + 60f) / 60f).coerceIn(0f, 1f) else 0f
    val safeGreenColor = Color(0xFF2E7D32)
    val cautionAmberColor = Color(0xFFF9A825)
    val clipRedColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(4f, 4f)
        )

        if (normalizedLevel > 0.01f) {
            val activeWidth = size.width * normalizedLevel
            val barColor = when {
                dbfs >= -1.5f -> clipRedColor
                dbfs >= -6.0f -> cautionAmberColor
                dbfs >= -24.0f -> safeGreenColor
                else -> Color(0xFF1976D2)
            }
            drawRoundRect(
                color = barColor,
                size = Size(activeWidth, size.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        val tick24X = size.width * ((60f - 24f) / 60f)
        val tick6X = size.width * ((60f - 6f) / 60f)
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(tick24X, 0f),
            end = Offset(tick24X, size.height),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(tick6X, 0f),
            end = Offset(tick6X, size.height),
            strokeWidth = 1.5f
        )
    }
}

@Composable
fun SymmetricalAudioWaveform(
    amplitudes: List<Float>,
    isRecording: Boolean,
    isClipping: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeColor = when {
        isClipping -> MaterialTheme.colorScheme.error
        isRecording -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        drawLine(
            color = baselineColor.copy(alpha = 0.5f),
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.5f
        )

        if (amplitudes.isEmpty()) return@Canvas

        val barCount = 48
        val slotWidth = size.width / barCount
        val strokeW = (slotWidth * 0.58f).coerceAtLeast(2f)
        val displayed = if (amplitudes.size > barCount) {
            amplitudes.takeLast(barCount)
        } else {
            amplitudes
        }

        displayed.forEachIndexed { idx, rawAmp ->
            val normalized = rawAmp.coerceIn(0.03f, 1f)
            val halfHeight = (normalized * (size.height * 0.46f)).coerceAtLeast(1.5f)
            val x = idx * slotWidth + slotWidth / 2f
            drawLine(
                color = activeColor,
                start = Offset(x, midY - halfHeight),
                end = Offset(x, midY + halfHeight),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun RecordingPulseRing(buttonSize: androidx.compose.ui.unit.Dp = 76.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "recordPulse")
    val pulseScaleState = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                val s = pulseScaleState.value
                scaleX = s
                scaleY = s
                alpha = pulseAlphaState.value
            }
            .background(MaterialTheme.colorScheme.error, CircleShape)
    )
}

@Composable
internal fun RecordingTransportBar(
    isRecording: Boolean,
    isPushToTalk: Boolean,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    isCompact: Boolean = false,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleRecord: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onStopFromHoldRelease: () -> Unit = onStopRecord
) {
    val navBtnSize = if (isCompact) 48.dp else 56.dp
    val micBtnSize = if (isCompact) 62.dp else 76.dp
    val outerBoxSize = if (isCompact) 74.dp else 92.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 0.dp else 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onPrev,
            enabled = canGoPrev,
            modifier = Modifier
                .size(navBtnSize)
                .pointerHoverIcon(if (canGoPrev) PointerIcon.Hand else PointerIcon.Default)
                .testTag("prev_word_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = S.recording.prevWordDesc,
                modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(outerBoxSize)
        ) {
            if (isRecording) {
                RecordingPulseRing(buttonSize = micBtnSize)
            }

            var isButtonPressed by remember { mutableStateOf(false) }

            val buttonColor by animateColorAsState(
                targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "recBtnColor"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "recIconColor"
            )
            val buttonScale by animateFloatAsState(
                targetValue = if (isButtonPressed) 0.91f else 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 680f),
                label = "recBtnScale"
            )
            val cornerPercent by animateIntAsState(
                targetValue = if (isRecording) 34 else 50,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                label = "recBtnCorner"
            )

            val currentIsRecording by rememberUpdatedState(isRecording)
            val currentIsPushToTalk by rememberUpdatedState(isPushToTalk)
            val currentOnToggleRecord by rememberUpdatedState(onToggleRecord)
            val currentOnStartRecord by rememberUpdatedState(onStartRecord)
            val currentOnStopRecord by rememberUpdatedState(onStopRecord)
            val currentOnStopFromHoldRelease by rememberUpdatedState(onStopFromHoldRelease)

            Surface(
                shape = RoundedCornerShape(percent = cornerPercent),
                color = buttonColor,
                shadowElevation = if (isRecording) 8.dp else 4.dp,
                modifier = Modifier
                    .size(micBtnSize)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("main_record_button")
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val downAtMs = System.currentTimeMillis()
                            val wasAlreadyRecording = currentIsRecording
                            isButtonPressed = true
                            try {
                                if (wasAlreadyRecording) {
                                    if (currentIsPushToTalk) {
                                        currentOnStopRecord()
                                    } else {
                                        currentOnToggleRecord()
                                    }
                                    waitForUpOrCancellation()
                                } else {
                                    currentOnStartRecord()
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        val heldMs = System.currentTimeMillis() - downAtMs
                                        if (currentIsPushToTalk || heldMs >= 620L) {
                                            if (currentIsRecording) {
                                                currentOnStopFromHoldRelease()
                                            }
                                        }
                                    }
                                }
                            } finally {
                                isButtonPressed = false
                            }
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isRecording,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.68f, stiffness = 550f), initialScale = 0.65f) + fadeIn(tween(120))) togetherWith
                                (scaleOut(tween(100), targetScale = 0.65f) + fadeOut(tween(90)))
                        },
                        label = "RecMicStopIconMorph"
                    ) { rec ->
                        Icon(
                            imageVector = if (rec) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (rec) S.recording.stopRecordingDesc else S.recording.startRecordingDesc,
                            tint = iconColor,
                            modifier = Modifier.size(if (isCompact) 28.dp else 34.dp)
                        )
                    }
                }
            }
        }

        FilledTonalIconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .size(navBtnSize)
                .pointerHoverIcon(if (canGoNext) PointerIcon.Hand else PointerIcon.Default)
                .testTag("next_word_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = S.recording.nextWordDesc,
                modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
            )
        }
    }
}

@Composable
internal fun TabletWordSidebar(
    words: List<Word>,
    currentIndex: Int,
    activeSpeaker: Speaker?,
    recordedCount: Int,
    unrecordedCount: Int,
    flaggedCount: Int,
    isShuffleEnabled: Boolean,
    onlyPendingWords: Boolean = false,
    enablePicturePrompts: Boolean = false,
    onSelectWord: (Int) -> Unit,
    onTriggerSpeakerDialog: () -> Unit,
    onShuffle: () -> Unit,
    onToggleOnlyPending: () -> Unit = {},
    onJumpNextUnrecorded: () -> Unit,
    onJumpNextFlagged: () -> Unit,
    onRequestClearAll: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(256.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTriggerSpeakerDialog() }
                    .testTag("speaker_card_trigger_tablet")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSpeaker?.name ?: S.common.defaultSpeakerFallback,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (activeSpeaker != null) {
                                val genderPrefix = if (activeSpeaker.gender.isBlank() || activeSpeaker.gender == "不愿透露") {
                                    ""
                                } else {
                                    S.recording.speakerGenderAgeDot.withArgs(
                                        "gender" to S.speaker.displayGender(activeSpeaker.gender)
                                    )
                                }
                                S.recording.speakerGenderAgeWithDialect.withArgs(
                                    "genderPrefix" to genderPrefix,
                                    "age" to activeSpeaker.age,
                                    "dialect" to activeSpeaker.dialect
                                )
                            } else {
                                S.recording.tapToSwitchSpeaker
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(Icons.Default.Edit, contentDescription = S.recording.switchSpeakerDesc, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if ((unrecordedCount + flaggedCount > 0 && recordedCount > 0) || onlyPendingWords) {
                        StudioFilterChip(
                            selected = onlyPendingWords,
                            onClick = onToggleOnlyPending,
                            labelText = S.recording.onlyPendingFilterChip
                        )
                    }
                    StudioFilterChip(
                        selected = false,
                        onClick = onJumpNextUnrecorded,
                        enabled = unrecordedCount > 0,
                        labelText = S.recording.unrecordedFilterChip.withArgs("count" to unrecordedCount)
                    )
                    if (flaggedCount > 0) {
                        StudioFilterChip(
                            selected = false,
                            onClick = onJumpNextFlagged,
                            labelText = S.recording.flaggedFilterChip.withArgs("count" to flaggedCount),
                            isErrorVariant = true
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShuffle, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = S.recording.shuffleWordListDesc,
                            tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(words, key = { _, w -> w.id }) { idx, word ->
                    val isSelected = idx == currentIndex
                    val isRec = word.isRecorded
                    val isFlagged = isRec && word.qualityStatus != null && word.qualityStatus != "正常"
                    val rowBg = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isFlagged -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        isRec -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = rowBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectWord(idx) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d.", idx + 1),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (enablePicturePrompts && word.imagePath.isNotBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = S.recording.hasImageBadgeDesc,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = word.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val sideSub = listOf(word.speakerNote, word.remark)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (sideSub.isNotBlank()) {
                                        Text(
                                            text = sideSub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (word.speakerNote.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (isRec) {
                                Icon(
                                    imageVector = if (isFlagged) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isFlagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingQuickSettingsSidebarPane(
    viewModel: RecordingViewModel,
    onOpenFullSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAutoNext by viewModel.autoNextEnabled.collectAsState()
    val isPushToTalk by viewModel.pushToTalkEnabled.collectAsState()
    val autoAdvanceOnHoldRelease by viewModel.autoAdvanceOnHoldRelease.collectAsState()
    val autoPlayAfterRecord by viewModel.autoPlayAfterRecord.collectAsState()
    val skipSilenceOnPlayback by viewModel.skipSilenceOnPlayback.collectAsState()
    val volumeKeyControlEnabled by viewModel.volumeKeyControlEnabled.collectAsState()
    val vadThreshold by viewModel.vadThreshold.collectAsState()
    val silenceDelayMs by viewModel.silenceDelayMs.collectAsState()
    val characterSize by viewModel.characterSize.collectAsState()
    val hideRemarkInCleanMode by viewModel.hideRemarkInCleanMode.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val shufflingMode by viewModel.shufflingMode.collectAsState()
    val lowVolumeThreshold by viewModel.lowVolumeThreshold.collectAsState()
    val creakySensitivity by viewModel.creakySensitivity.collectAsState()
    val rawWords by viewModel.currentWords.collectAsState()
    val activeProject by viewModel.currentProject.collectAsState()
    val activeSpeaker by viewModel.currentSpeaker.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val recordedCount = remember(rawWords) { rawWords.count { it.isRecorded } }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("vad_settings_sheet")
    ) {
        RecordingSettingsSheetContent(
            isAutoNext = isAutoNext,
            onAutoNextChange = { viewModel.setAutoNextEnabled(it) },
            isPushToTalk = isPushToTalk,
            onPushToTalkChange = { viewModel.setPushToTalkEnabled(it) },
            autoAdvanceOnHoldRelease = autoAdvanceOnHoldRelease,
            onAutoAdvanceOnHoldReleaseChange = { viewModel.setAutoAdvanceOnHoldRelease(it) },
            autoPlayAfterRecord = autoPlayAfterRecord,
            onAutoPlayChange = { viewModel.setAutoPlayAfterRecord(it) },
            skipSilenceOnPlayback = skipSilenceOnPlayback,
            onSkipSilenceOnPlaybackChange = { viewModel.setSkipSilenceOnPlayback(it) },
            volumeKeyControlEnabled = volumeKeyControlEnabled,
            onVolumeKeyChange = { viewModel.setVolumeKeyControlEnabled(it) },
            vadThreshold = vadThreshold,
            onVadThresholdChange = { viewModel.setVadThreshold(it) },
            onApplyVadPreset = { viewModel.applyVadSensitivityPreset(it) },
            silenceDelayMs = silenceDelayMs,
            onSilenceDelayChange = { viewModel.setSilenceDelayMs(it) },
            characterSize = characterSize,
            onCharacterSizeChange = { viewModel.setCharacterSize(it) },
            hideRemarkInCleanMode = hideRemarkInCleanMode,
            onHideRemarkInCleanModeChange = { viewModel.setHideRemarkInCleanMode(it) },
            isShuffleEnabled = isShuffleEnabled,
            onToggleShuffle = { viewModel.toggleShuffle() },
            shufflingMode = shufflingMode,
            onShufflingModeChange = { viewModel.setShufflingMode(it) },
            lowVolumeThreshold = lowVolumeThreshold,
            onLowVolumeThresholdChange = { viewModel.setLowVolumeThreshold(it) },
            creakySensitivity = creakySensitivity,
            onCreakySensitivityChange = { viewModel.setCreakySensitivity(it) },
            recordedCount = recordedCount,
            onRequestClearAll = { showClearAllConfirmDialog = true },
            onOpenFullSettings = onOpenFullSettings,
            onClose = onClose,
            isSidebarMode = true,
            isSimpleMode = isSimpleMode
        )
    }

    com.example.ui.components.AnimatedDialogVisibility(visible = showClearAllConfirmDialog) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(S.recording.clearSpeakerRecordingsTitle) },
            text = {
                Text(
                    S.recording.clearSpeakerRecordingsBody.withArgs(
                        "speaker" to (activeSpeaker?.name ?: S.common.defaultLabel)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pid = activeProject?.id
                        val sid = activeSpeaker?.id
                        if (pid != null && sid != null) {
                            viewModel.deleteSpeakerRecordingsInProject(pid, sid)
                        }
                        showClearAllConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(S.recording.clearAllButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordingSettingsSheetContent(
    isAutoNext: Boolean,
    onAutoNextChange: (Boolean) -> Unit,
    isPushToTalk: Boolean,
    onPushToTalkChange: (Boolean) -> Unit,
    autoAdvanceOnHoldRelease: Boolean = true,
    onAutoAdvanceOnHoldReleaseChange: (Boolean) -> Unit = {},
    autoPlayAfterRecord: Boolean,
    onAutoPlayChange: (Boolean) -> Unit,
    skipSilenceOnPlayback: Boolean = false,
    onSkipSilenceOnPlaybackChange: (Boolean) -> Unit = {},
    volumeKeyControlEnabled: Boolean,
    onVolumeKeyChange: (Boolean) -> Unit,
    vadThreshold: Float,
    onVadThresholdChange: (Float) -> Unit,
    onApplyVadPreset: (String) -> Unit = {},
    silenceDelayMs: Long,
    onSilenceDelayChange: (Long) -> Unit,
    characterSize: String,
    onCharacterSizeChange: (String) -> Unit,
    hideRemarkInCleanMode: Boolean = true,
    onHideRemarkInCleanModeChange: (Boolean) -> Unit = {},
    isShuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    shufflingMode: ShufflingMode,
    onShufflingModeChange: (ShufflingMode) -> Unit,
    lowVolumeThreshold: Float,
    onLowVolumeThresholdChange: (Float) -> Unit,
    creakySensitivity: String,
    onCreakySensitivityChange: (String) -> Unit,
    recordedCount: Int = 0,
    onRequestClearAll: () -> Unit = {},
    onOpenFullSettings: () -> Unit = {},
    onClose: () -> Unit,
    isSidebarMode: Boolean = false,
    isSimpleMode: Boolean = false
) {
    val sheetScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = available
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSidebarMode) Modifier.fillMaxHeight() else Modifier.navigationBarsPadding())
    ) {
        // Fixed Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isSidebarMode) 12.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSidebarMode) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.speaker.backToProjectsDesc,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = S.recording.quickSettingsTitle,
                    style = if (isSidebarMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onOpenFullSettings,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(S.recording.moreSettingsButton, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(S.common.done, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isSidebarMode) Modifier.weight(1f) else Modifier.nestedScroll(sheetScrollLock))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val currentHabitIndex = when {
                isPushToTalk -> 1
                isAutoNext -> 2
                else -> 0
            }
            var showAdvancedQualitySection by remember { mutableStateOf(false) }

            // 1. Unified Recording Mode Card (eliminates mutually exclusive duplicate toggles)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "录音方式",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StudioFilterChip(
                            selected = currentHabitIndex == 0,
                            onClick = {
                                onAutoNextChange(false)
                                onPushToTalkChange(false)
                                onAutoAdvanceOnHoldReleaseChange(false)
                            },
                            labelText = "点按录",
                            modifier = Modifier.testTag("recording_mode_tap_chip")
                        )
                        StudioFilterChip(
                            selected = currentHabitIndex == 1,
                            onClick = {
                                onAutoNextChange(false)
                                onPushToTalkChange(true)
                            },
                            labelText = "按住录",
                            modifier = Modifier.testTag("push_to_talk_switch")
                        )
                        StudioFilterChip(
                            selected = currentHabitIndex == 2,
                            onClick = {
                                onPushToTalkChange(false)
                                onAutoNextChange(true)
                                onAutoAdvanceOnHoldReleaseChange(false)
                            },
                            labelText = "读完自动跳词",
                            modifier = Modifier.testTag("auto_next_switch")
                        )
                    }

                    // Contextual sub-control for Hold-to-Record
                    AnimatedVisibility(
                        visible = isPushToTalk,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(110))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            StudioSwitchRow(
                                title = if (isSimpleMode) S.recording.holdReleaseNextTitle else S.recording.desktopHoldReleaseNextTitle,
                                subtitle = if (isSimpleMode) S.recording.holdReleaseNextSubtitle else S.recording.desktopHoldReleaseNextSub,
                                checked = autoAdvanceOnHoldRelease,
                                onCheckedChange = onAutoAdvanceOnHoldReleaseChange,
                                switchTestTag = "hold_release_advance_switch",
                                compact = true
                            )
                        }
                    }

                    // Contextual sub-controls for Auto-Flip VAD (Advanced mode only)
                    AnimatedVisibility(
                        visible = !isSimpleMode && isAutoNext && !isPushToTalk,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(110))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            val activePreset = when {
                                vadThreshold <= 850f -> "高灵敏"
                                vadThreshold >= 1650f -> "抗噪"
                                else -> "标准"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S.recording.vadSensitivityValue.withArgs("value" to S.settings.displayVadPreset(activePreset)),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = S.recording.vadDirectionHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("高灵敏", "标准", "抗噪").forEach { preset ->
                                    StudioFilterChip(
                                        selected = activePreset == preset,
                                        onClick = { onApplyVadPreset(preset) },
                                        labelText = S.settings.displayVadPreset(preset)
                                    )
                                }
                            }
                            Slider(
                                value = vadThreshold,
                                onValueChange = onVadThresholdChange,
                                valueRange = 200f..3000f,
                                modifier = Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("vad_threshold_slider")
                            )
                            Text(
                                text = S.recording.vadDelayValue.withArgs("ms" to silenceDelayMs),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = silenceDelayMs.toFloat(),
                                onValueChange = { onSilenceDelayChange(it.toLong()) },
                                valueRange = 400f..2500f,
                                modifier = Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("silence_delay_slider")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    StudioSwitchRow(
                        title = if (isSimpleMode) S.recording.autoPlayTitle else S.recording.desktopAutoPlayTitle,
                        subtitle = if (isSimpleMode) S.recording.autoPlaySubtitle else S.recording.desktopAutoPlaySub,
                        checked = autoPlayAfterRecord,
                        onCheckedChange = onAutoPlayChange,
                        switchTestTag = "auto_play_after_record_switch",
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    StudioSwitchRow(
                        title = "试听跳过头尾静音",
                        subtitle = "试听与连播时跳过首尾空白，原音频不裁切",
                        checked = skipSilenceOnPlayback,
                        onCheckedChange = onSkipSilenceOnPlaybackChange,
                        switchTestTag = "skip_silence_on_playback_switch",
                        compact = true
                    )

                    if (!isSimpleMode && !DesktopAdaptiveState.isDesktopPlatform) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        StudioSwitchRow(
                            title = S.recording.volumeKeyControlTitle,
                            subtitle = S.recording.volumeKeyControlSub,
                            checked = volumeKeyControlEnabled,
                            onCheckedChange = onVolumeKeyChange,
                            switchTestTag = "volume_key_control_switch",
                            compact = true
                        )
                    }
                }
            }

            // 2. Prompt Display & Order Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isSimpleMode) S.recording.fontSizeHeader else S.recording.cardSizeHeader,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fontOptions = listOf(
                            "小" to S.recording.sizeCompact,
                            "中" to S.recording.sizeStandard,
                            "大" to S.recording.sizeLarge,
                            "超大" to S.recording.sizeExtraLarge
                        )
                        fontOptions.forEach { (opt, label) ->
                            StudioFilterChip(
                                selected = characterSize == opt,
                                onClick = { onCharacterSizeChange(opt) },
                                labelText = label,
                                modifier = Modifier.testTag("font_size_selector_$opt")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    StudioSwitchRow(
                        title = S.recording.hideRemarksInCleanTitle,
                        subtitle = S.recording.hideRemarksInCleanSub,
                        checked = hideRemarkInCleanMode,
                        onCheckedChange = onHideRemarkInCleanModeChange,
                        compact = true
                    )

                    if (!isSimpleMode) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        StudioSwitchRow(
                            title = S.recording.shuffleOrderTitle,
                            checked = isShuffleEnabled,
                            onCheckedChange = { onToggleShuffle() },
                            compact = true
                        )
                        AnimatedVisibility(
                            visible = isShuffleEnabled,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(110))
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val shuffleOptions = listOf(
                                    ShufflingMode.RANDOM_ALL to S.recording.shuffleModeAll,
                                    ShufflingMode.BLOCK_BALANCED to S.recording.shuffleModeGrouped
                                )
                                shuffleOptions.forEach { (mode, label) ->
                                    StudioFilterChip(
                                        selected = shufflingMode == mode,
                                        onClick = { onShufflingModeChange(mode) },
                                        labelText = label,
                                        modifier = Modifier.testTag("shuffling_mode_${mode.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Collapsible Low-Frequency Quality & Cleanup Section (Advanced mode only, collapsed by default)
            if (!isSimpleMode) {
                val lowVolLevelLabel = when {
                    lowVolumeThreshold < 400f -> "宽松"
                    lowVolumeThreshold > 950f -> "严格"
                    else -> "适中"
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedQualitySection = !showAdvancedQualitySection }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("quick_settings_advanced_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "更多质检与清理选项",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "音量阈值：$lowVolLevelLabel · 嘎裂声：${S.settings.displayCreakyOption(creakySensitivity)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (showAdvancedQualitySection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(
                            visible = showAdvancedQualitySection,
                            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(110))
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Text(
                                    text = S.recording.lowVolThresholdValue.withArgs("value" to lowVolLevelLabel),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = lowVolumeThreshold,
                                    onValueChange = onLowVolumeThresholdChange,
                                    valueRange = 150f..2500f,
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .testTag("low_volume_threshold_slider")
                                )

                                Text(S.recording.creakySensitivityLabel, style = MaterialTheme.typography.bodySmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val creakyOptions = listOf("低", "中", "高", "关闭")
                                    creakyOptions.forEach { option ->
                                        StudioFilterChip(
                                            selected = creakySensitivity == option,
                                            onClick = { onCreakySensitivityChange(option) },
                                            labelText = S.settings.displayCreakyOption(option),
                                            modifier = Modifier.testTag("creaky_sensitivity_$option")
                                        )
                                    }
                                }

                                if (recordedCount > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    OutlinedButton(
                                        onClick = onRequestClearAll,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            S.recording.clearSpeakerRecordingsBtn.withArgs("count" to recordedCount),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(S.recording.doneSettingsButton, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
