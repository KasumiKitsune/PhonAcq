package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.S
import com.example.i18n.withArgs
import kotlinx.coroutines.delay

/**
 * Non-interactive full-screen perimeter edge-glow + Toast notifier triggered on the active microphone
 * capture endpoint for the first ~4.2 seconds upon wireless connection (or when mic routing switches
 * to this device mid-session).
 *
 * Ensures both the investigator and speaker immediately know WHICH physical device is capturing audio.
 */
@Composable
fun ActiveMicEdgeGlowOverlay(
    isConnected: Boolean,
    connectionEpoch: Int,
    isThisDeviceActiveMic: Boolean,
    thisRoleLabel: String,
    peerRoleLabel: String,
    glowColor: Color = MaterialTheme.colorScheme.primary,
    eInkCrispMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEdgeGlow by remember { mutableStateOf(false) }
    val glowAlpha = remember { Animatable(0f) }

    // Track previous active state within the same connectionEpoch to emit a clear switch Toast when toggled away
    var prevEpoch by remember { mutableStateOf(-1) }
    var prevWasActiveMic by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected, connectionEpoch, isThisDeviceActiveMic) {
        if (!isConnected || connectionEpoch <= 0) {
            showEdgeGlow = false
            glowAlpha.snapTo(0f)
            prevEpoch = -1
            prevWasActiveMic = false
            return@LaunchedEffect
        }

        val isFreshConnection = connectionEpoch != prevEpoch
        val switchedAwayFromThisDevice = !isFreshConnection && prevWasActiveMic && !isThisDeviceActiveMic

        prevEpoch = connectionEpoch
        prevWasActiveMic = isThisDeviceActiveMic

        if (isThisDeviceActiveMic) {
            Toast.makeText(
                context,
                S.wireless.localMicActiveToast.withArgs("role" to thisRoleLabel),
                Toast.LENGTH_LONG
            ).show()

            showEdgeGlow = true
            if (eInkCrispMode) {
                glowAlpha.snapTo(1f)
                delay(3800L)
                glowAlpha.snapTo(0f)
            } else {
                glowAlpha.snapTo(0f)
                glowAlpha.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
                delay(3000L)
                glowAlpha.animateTo(0f, animationSpec = tween(900, easing = FastOutSlowInEasing))
            }
            showEdgeGlow = false
        } else {
            showEdgeGlow = false
            glowAlpha.snapTo(0f)
            if (switchedAwayFromThisDevice) {
                Toast.makeText(
                    context,
                    S.wireless.switchedToPeerMicToast.withArgs("role" to peerRoleLabel),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "ActiveMicEdgePulse")
    val breath by pulseTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(880, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ActiveMicEdgeBreath"
    )

    if (showEdgeGlow && glowAlpha.value > 0.01f) {
        val alphaFactor = glowAlpha.value * (if (eInkCrispMode) 1f else breath)
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("active_mic_edge_glow_overlay")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val band = 26.dp.toPx()
                val corner = 24.dp.toPx()

                if (!eInkCrispMode) {
                    // Four-edge inward luminous wash
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(glowColor.copy(alpha = 0.42f * alphaFactor), Color.Transparent),
                            startY = 0f,
                            endY = band
                        ),
                        topLeft = Offset.Zero,
                        size = Size(w, band)
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, glowColor.copy(alpha = 0.42f * alphaFactor)),
                            startY = h - band,
                            endY = h
                        ),
                        topLeft = Offset(0f, h - band),
                        size = Size(w, band)
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(glowColor.copy(alpha = 0.38f * alphaFactor), Color.Transparent),
                            startX = 0f,
                            endX = band
                        ),
                        topLeft = Offset.Zero,
                        size = Size(band, h)
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, glowColor.copy(alpha = 0.38f * alphaFactor)),
                            startX = w - band,
                            endX = w
                        ),
                        topLeft = Offset(w - band, 0f),
                        size = Size(band, h)
                    )
                }

                // Crisp perimeter studio rim
                val strokePx = if (eInkCrispMode) 4.dp.toPx() else 3.5.dp.toPx()
                drawRoundRect(
                    color = glowColor.copy(alpha = (0.85f * alphaFactor).coerceIn(0f, 1f)),
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size((w - strokePx).coerceAtLeast(1f), (h - strokePx).coerceAtLeast(1f)),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = strokePx)
                )
            }

            AnimatedVisibility(
                visible = showEdgeGlow,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = glowColor,
                    shadowElevation = if (eInkCrispMode) 0.dp else 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = S.wireless.localMicBadgeBanner,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Persistent, unmistakable microphone endpoint status pill displayed on both 遥控端 and 看读端
 * so users never confuse which physical device is currently capturing audio.
 */
@Composable
fun MicEndpointStatusBadge(
    isThisDeviceActiveMic: Boolean,
    isRecording: Boolean,
    peerRoleShortName: String,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    activeDotColor: Color = MaterialTheme.colorScheme.primary,
    inactiveContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    testTag: String = "mic_endpoint_status_badge",
    modifier: Modifier = Modifier
) {
    val container = if (isThisDeviceActiveMic) activeContainerColor else inactiveContainerColor
    val content = if (isThisDeviceActiveMic) activeContentColor else inactiveContentColor
    val borderCol = if (isThisDeviceActiveMic) {
        activeDotColor.copy(alpha = if (isRecording) 0.85f else 0.45f)
    } else {
        inactiveContentColor.copy(alpha = 0.24f)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container,
        border = BorderStroke(if (isThisDeviceActiveMic) 1.4.dp else 1.dp, borderCol),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isThisDeviceActiveMic) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) MaterialTheme.colorScheme.error else activeDotColor)
                )
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isRecording) S.wireless.localMicRecordingBadge else S.wireless.localMicStandbyBadge,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = content,
                    maxLines = 1,
                    softWrap = false
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.78f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = S.wireless.peerMicBadge.withArgs("role" to peerRoleShortName),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
