package com.example.ui.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.S
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val HOLD_TO_STOP_THRESHOLD_MS = 620L

/**
 * Tactile Material You studio field-recorder trigger button for Wireless Collaboration Console.
 *
 * Supports seamless dual-interaction out of the box:
 * - **Press & Hold (>= 620ms)**: Starts recording immediately on finger/mouse down, and automatically
 *   stops recording as soon as you release.
 * - **Quick Tap (< 620ms)**:
 *   - When [strictPushToTalk] is `false` (default): Toggles start/stop cleanly for hands-free long items.
 *   - When [strictPushToTalk] is `true`: Always stops immediately upon release.
 */
@Composable
fun StudioRecordTriggerButton(
    isRecording: Boolean,
    enabled: Boolean,
    strictPushToTalk: Boolean = false,
    dbfs: Float = -60f,
    outerSize: Dp = 90.dp,
    coreSize: Dp = 68.dp,
    testTag: String = "controller_record_button",
    onHoldActiveChanged: (Boolean) -> Unit = {},
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onStopFromHoldRelease: (() -> Unit)? = null,
    onToggleRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    var isPressed by remember { mutableStateOf(false) }
    var isHoldLatched by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            isHoldLatched = false
            onHoldStateSafe(onHoldActiveChanged, false)
        }
    }

    val currentEnabled by rememberUpdatedState(enabled)
    val currentIsRecording by rememberUpdatedState(isRecording)
    val currentStrictPtt by rememberUpdatedState(strictPushToTalk)
    val currentOnStart by rememberUpdatedState(onStartRecord)
    val currentOnStop by rememberUpdatedState(onStopRecord)
    val currentOnStopHoldRelease by rememberUpdatedState(onStopFromHoldRelease)
    val currentOnToggle by rememberUpdatedState(onToggleRecord)
    val currentOnHoldChanged by rememberUpdatedState(onHoldActiveChanged)

    val baseCoreColor by animateColorAsState(
        targetValue = when {
            !enabled -> cs.surfaceVariant
            isRecording -> cs.error
            else -> cs.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "StudioRecCoreColor"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.45f)
            isRecording -> cs.onError
            else -> cs.onPrimary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "StudioRecIconTint"
    )
    val socketFillColor by animateColorAsState(
        targetValue = when {
            isRecording -> cs.errorContainer.copy(alpha = 0.55f)
            enabled -> cs.surfaceContainerHighest.copy(alpha = 0.85f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = tween(200),
        label = "StudioRecSocketColor"
    )
    val socketRingColor by animateColorAsState(
        targetValue = when {
            isRecording -> cs.error.copy(alpha = 0.48f)
            isPressed -> cs.primary.copy(alpha = 0.55f)
            else -> cs.outlineVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(180),
        label = "StudioRecSocketRing"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.89f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 660f),
        label = "StudioRecPressScale"
    )
    val cornerPercent by animateIntAsState(
        targetValue = if (isRecording) 32 else 50,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 420f),
        label = "StudioRecCornerMorph"
    )

    val voiceNormalized = ((dbfs + 56f) / 50f).coerceIn(0f, 1f)
    val smoothedVoiceBoost by animateFloatAsState(
        targetValue = if (isRecording) voiceNormalized else 0f,
        animationSpec = tween(90),
        label = "StudioRecVoiceBoost"
    )

    val pulseTransition = rememberInfiniteTransition(label = "StudioRecPulse")
    val breathingPhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(920, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StudioRecBreathingPhase"
    )

    val coreShape = RoundedCornerShape(percent = cornerPercent)
    val coreBrush = remember(baseCoreColor, enabled) {
        if (!enabled) {
            Brush.verticalGradient(listOf(baseCoreColor, baseCoreColor))
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    lerp(baseCoreColor, Color.White, 0.16f),
                    baseCoreColor,
                    lerp(baseCoreColor, Color.Black, 0.12f)
                )
            )
        }
    }

    val tickColor = cs.onSurfaceVariant.copy(alpha = if (enabled) 0.24f else 0.10f)
    val activeHaloColor = cs.error

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(outerSize)
    ) {
        // Layer 1 & 2: Precision Recessed Studio Bezel + Live dBFS & Breathing Acoustic Halos
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f
            val socketR = maxR * 0.90f

            if (isRecording) {
                // Outer breathing aura
                val outerR = socketR * (1.02f + 0.11f * breathingPhase)
                drawCircle(
                    color = activeHaloColor.copy(alpha = 0.18f * (1f - 0.45f * breathingPhase)),
                    radius = outerR,
                    center = center
                )
                // Inner voice-reactive dBFS ring
                val voiceR = (coreSize.toPx() / 2f) + (socketR - coreSize.toPx() / 2f) * (0.45f + 0.55f * smoothedVoiceBoost)
                drawCircle(
                    color = activeHaloColor.copy(alpha = 0.28f + 0.30f * smoothedVoiceBoost),
                    radius = voiceR,
                    center = center,
                    style = Stroke(width = 2.2.dp.toPx())
                )
            }

            // Recessed studio socket well
            drawCircle(
                color = socketFillColor,
                radius = socketR,
                center = center
            )
            drawCircle(
                color = socketRingColor,
                radius = socketR,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 12 precision acoustic dial ticks along the bezel rim
            val tickInner = socketR - 4.2.dp.toPx()
            val tickOuter = socketR - 1.5.dp.toPx()
            for (i in 0 until 12) {
                val rad = Math.toRadians((i * 30).toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                drawLine(
                    color = if (isRecording && i % 3 == 0) activeHaloColor.copy(alpha = 0.55f) else tickColor,
                    start = Offset(center.x + cosA * tickInner, center.y + sinA * tickInner),
                    end = Offset(center.x + cosA * tickOuter, center.y + sinA * tickOuter),
                    strokeWidth = if (i % 3 == 0) 1.6.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Layer 3: Sculpted Tactile Core Keycap with Dual Tap / Hold-to-Record Gesture
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(coreSize)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .shadow(
                    elevation = if (!enabled) 0.dp else if (isRecording) 8.dp else 5.dp,
                    shape = coreShape,
                    ambientColor = baseCoreColor,
                    spotColor = baseCoreColor
                )
                .clip(coreShape)
                .background(brush = coreBrush, shape = coreShape)
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            iconTint.copy(alpha = if (enabled) 0.34f else 0.08f),
                            iconTint.copy(alpha = if (enabled) 0.08f else 0.02f)
                        )
                    ),
                    shape = coreShape
                )
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .semantics {
                    role = Role.Button
                    contentDescription = if (isRecording) S.recording.triggerEndRecDesc else S.recording.triggerStartRecDesc
                    onClick {
                        if (currentEnabled) {
                            currentOnToggle()
                            true
                        } else {
                            false
                        }
                    }
                }
                .pointerInput(Unit) {
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (!currentEnabled) return@awaitEachGesture
                            down.consume()

                            isPressed = true
                            val downUptimeMs = down.uptimeMillis
                            val downWallMs = System.currentTimeMillis()
                            val wasAlreadyRecording = currentIsRecording
                            var startedByThisGesture = false
                            var holdTimerJob: kotlinx.coroutines.Job? = null

                            if (wasAlreadyRecording) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnStop()
                            } else {
                                startedByThisGesture = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnStart()
                                if (currentStrictPtt) {
                                    isHoldLatched = true
                                    onHoldStateSafe(currentOnHoldChanged, true)
                                } else {
                                    holdTimerJob = launch {
                                        delay(HOLD_TO_STOP_THRESHOLD_MS)
                                        isHoldLatched = true
                                        onHoldStateSafe(currentOnHoldChanged, true)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }

                            var lastUptimeMs = downUptimeMs
                            var releasedCleanly = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { ch ->
                                        lastUptimeMs = maxOf(lastUptimeMs, ch.uptimeMillis)
                                        ch.consume()
                                    }
                                    if (event.changes.all { !it.pressed }) {
                                        releasedCleanly = true
                                        break
                                    }
                                }
                            } finally {
                                holdTimerJob?.cancel()
                                isPressed = false
                                val elapsedEventMs = lastUptimeMs - downUptimeMs
                                val heldMs = if (elapsedEventMs > 0L) {
                                    elapsedEventMs
                                } else {
                                    System.currentTimeMillis() - downWallMs
                                }

                                if (startedByThisGesture) {
                                    val shouldStopOnRelease = !releasedCleanly ||
                                        currentStrictPtt ||
                                        isHoldLatched ||
                                        heldMs >= HOLD_TO_STOP_THRESHOLD_MS
                                    if (shouldStopOnRelease) {
                                        isHoldLatched = false
                                        onHoldStateSafe(currentOnHoldChanged, false)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        (currentOnStopHoldRelease ?: currentOnStop)()
                                    }
                                } else {
                                    isHoldLatched = false
                                    onHoldStateSafe(currentOnHoldChanged, false)
                                }
                            }
                        }
                    }
                }
                .testTag(testTag)
        ) {
            // Subtle inner studio ring detail when idle
            if (!isRecording && enabled) {
                Box(
                    modifier = Modifier
                        .size(coreSize - 12.dp)
                        .border(1.dp, iconTint.copy(alpha = 0.16f), CircleShape)
                )
            }

            AnimatedContent(
                targetState = isRecording,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.65f, stiffness = 540f), initialScale = 0.60f) + fadeIn(tween(120))) togetherWith
                        (scaleOut(tween(100), targetScale = 0.60f) + fadeOut(tween(90)))
                },
                label = "StudioRecIconMorph"
            ) { rec ->
                if (rec) {
                    // Crisp sculpted rounded stop block
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(iconTint)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

/**
 * Gentle, non-intimidating speaker-facing record pill for 看读端 (when self-recording permission is granted),
 * supporting both single tap and press-and-hold without red/yellow engineering alarm colors.
 */
@Composable
fun PromptRecordTriggerButton(
    isRecording: Boolean,
    accentColor: Color,
    screenBgColor: Color,
    haloColor: Color,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onStopFromHoldRelease: (() -> Unit)? = null,
    onToggleRecord: () -> Unit,
    tag: String = "prompt_btn_record",
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (!isRecording) isHolding = false
    }

    val currentIsRecording by rememberUpdatedState(isRecording)
    val currentOnStart by rememberUpdatedState(onStartRecord)
    val currentOnStop by rememberUpdatedState(onStopRecord)
    val currentOnStopHoldRelease by rememberUpdatedState(onStopFromHoldRelease)
    val currentOnToggle by rememberUpdatedState(onToggleRecord)

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 600f),
        label = "PromptRecScale"
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor,
        contentColor = screenBgColor,
        border = BorderStroke(
            width = if (isRecording) 3.dp else 1.5.dp,
            color = if (isRecording) haloColor else screenBgColor.copy(alpha = 0.22f)
        ),
        shadowElevation = if (isRecording) 6.dp else 3.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics {
                role = Role.Button
                contentDescription = if (isRecording) S.recording.promptBtnDoneDesc else S.recording.promptBtnStartDesc
                onClick {
                    currentOnToggle()
                    true
                }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isPressed = true
                        val downUptimeMs = down.uptimeMillis
                        val downWallMs = System.currentTimeMillis()
                        val wasAlreadyRecording = currentIsRecording
                        var startedByThisGesture = false
                        var holdJob: kotlinx.coroutines.Job? = null

                        if (wasAlreadyRecording) {
                            currentOnStop()
                        } else {
                            startedByThisGesture = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnStart()
                            holdJob = launch {
                                delay(HOLD_TO_STOP_THRESHOLD_MS)
                                isHolding = true
                            }
                        }

                        var lastUptimeMs = downUptimeMs
                        var releasedCleanly = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { ch ->
                                    lastUptimeMs = maxOf(lastUptimeMs, ch.uptimeMillis)
                                    ch.consume()
                                }
                                if (event.changes.all { !it.pressed }) {
                                    releasedCleanly = true
                                    break
                                }
                            }
                        } finally {
                            holdJob?.cancel()
                            isPressed = false
                            val elapsedEventMs = lastUptimeMs - downUptimeMs
                            val heldMs = if (elapsedEventMs > 0L) {
                                elapsedEventMs
                            } else {
                                System.currentTimeMillis() - downWallMs
                            }
                            if (startedByThisGesture && (!releasedCleanly || isHolding || heldMs >= HOLD_TO_STOP_THRESHOLD_MS)) {
                                isHolding = false
                                (currentOnStopHoldRelease ?: currentOnStop)()
                            }
                        }
                    }
                }
            }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    isRecording && isHolding -> S.recording.promptHoldReleaseDone
                    isRecording -> S.recording.promptActiveDone
                    else -> S.recording.promptIdleHint
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun onHoldStateSafe(cb: (Boolean) -> Unit, active: Boolean) {
    cb(active)
}
