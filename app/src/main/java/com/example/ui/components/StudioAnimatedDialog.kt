package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Material You spring scale + fade + gentle upward lift wrapper for custom Dialogs
 * across both Android and Windows Compose Desktop (where raw Dialog popups otherwise appear abruptly).
 */
@Composable
fun StudioDialogEnterAnimation(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        entered = true
    }

    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "StudioDialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 185, easing = FastOutSlowInEasing),
        label = "StudioDialogAlpha"
    )
    val liftPx = with(LocalDensity.current) { 14.dp.toPx() }
    val translationY by animateFloatAsState(
        targetValue = if (entered) 0f else liftPx,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "StudioDialogLift"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
            this.translationY = translationY
        },
        contentAlignment = contentAlignment
    ) {
        content()
    }
}
