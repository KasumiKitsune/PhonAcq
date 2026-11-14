package com.example.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/**
 * Holds real-time gesture telemetry for Android's Predictive Back gesture.
 * Values are backed by primitive MutableFloatState / MutableIntState so reading them inside
 * [Modifier.graphicsLayer] executes strictly in the GPU Draw/RenderNode phase without triggering
 * Compose recomposition or layout passes.
 */
@Stable
class PredictiveBackVisualState {
    var progress by mutableFloatStateOf(0f)
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    var touchY by mutableFloatStateOf(0f)
}

@Composable
fun rememberPredictiveBackVisualState(): PredictiveBackVisualState {
    return remember { PredictiveBackVisualState() }
}

/**
 * Collects Android Predictive Back gesture events into [state] and invokes [onBack] when committed.
 * If the user cancels the swipe gesture, smoothly springs [state.progress] back to 0f.
 */
@Composable
fun PredictiveBackGestureHandler(
    enabled: Boolean = true,
    state: PredictiveBackVisualState,
    onBack: () -> Unit
) {
    val currentOnBack by rememberUpdatedState(onBack)

    PredictiveBackHandler(enabled = enabled) { progressFlow ->
        try {
            progressFlow.collect { event ->
                state.swipeEdge = event.swipeEdge
                state.touchY = event.touchY
                state.progress = event.progress
            }
            state.progress = 0f
            currentOnBack()
        } catch (e: CancellationException) {
            animate(
                initialValue = state.progress,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) { value, _ ->
                state.progress = value
            }
        }
    }
}

private val PREALLOCATED_CORNER_SHAPES = Array(37) { idx ->
    RoundedCornerShape(idx.dp)
}

/**
 * Applies Material 3 Predictive Back visual transformations (scale down, horizontal shift away from
 * the active gesture edge, vertical touch follow, and dynamic rounded corners) strictly in the
 * GPU RenderNode phase via [graphicsLayer].
 *
 * When idle (`progress == 0f`), `clip = false` and identity transforms are used for zero overhead.
 */
fun Modifier.predictiveBackLayer(
    state: PredictiveBackVisualState,
    maxScaleReduction: Float = 0.085f,
    cornerRadiusDp: Float = 28f
): Modifier = this.graphicsLayer {
    val rawProgress = state.progress.coerceIn(0f, 1f)
    if (rawProgress > 0.001f) {
        val eased = FastOutSlowInEasing.transform(rawProgress)
        val scale = 1f - (eased * maxScaleReduction)
        val isRightEdge = state.swipeEdge == BackEventCompat.EDGE_RIGHT
        val pivotX = if (isRightEdge) 0.35f else 0.65f
        transformOrigin = TransformOrigin(pivotX, 0.5f)
        scaleX = scale
        scaleY = scale

        val horizontalDirection = if (isRightEdge) -1f else 1f
        translationX = horizontalDirection * (size.width * 0.055f) * eased

        if (size.height > 0f && state.touchY > 0f) {
            val normY = ((state.touchY / size.height) - 0.5f).coerceIn(-0.5f, 0.5f)
            translationY = normY * (size.height * 0.025f) * eased
        } else {
            translationY = 0f
        }

        val radiusIdx = (eased * cornerRadiusDp).toInt().coerceIn(0, PREALLOCATED_CORNER_SHAPES.lastIndex)
        shape = PREALLOCATED_CORNER_SHAPES[radiusIdx]
        clip = true
        shadowElevation = (eased * 14f).dp.toPx()
    } else {
        transformOrigin = TransformOrigin.Center
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        clip = false
        shadowElevation = 0f
    }
}
