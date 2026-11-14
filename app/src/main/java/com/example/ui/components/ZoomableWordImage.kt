package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.S
import com.example.i18n.withArgs
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Pinch-to-zoom, pan-to-move, and double-tap-to-zoom image container designed to coexist
 * seamlessly inside `HorizontalPager` and dialogs across Android and Windows Desktop:
 * - At `1.0x` scale with a single finger, horizontal swipes pass through to `HorizontalPager`.
 * - With 2+ fingers (pinch), or when zoomed in (`scale > 1.02f`), pointer changes are consumed
 *   so users can freely pinch and pan around fine details of dialect elicitation pictures.
 * - Double-tapping toggles between `1.0x` and `2.5x` centered on the tapped point.
 */
@Composable
fun ZoomableWordImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 4.5f,
    cornerRadius: Dp = 12.dp,
    showZoomBadge: Boolean = true,
    onZoomActiveChanged: ((Boolean) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim = remember(resetKey) { Animatable(1f) }
    val offsetAnim = remember(resetKey) { Animatable(Offset.Zero, Offset.VectorConverter) }

    val currentOnZoomActiveChanged by rememberUpdatedState(onZoomActiveChanged)
    val isZoomed = scaleAnim.value > 1.02f

    LaunchedEffect(isZoomed, resetKey) {
        currentOnZoomActiveChanged?.invoke(isZoomed)
    }

    DisposableEffect(resetKey) {
        onDispose {
            currentOnZoomActiveChanged?.invoke(false)
        }
    }

    fun clampOffset(rawOffset: Offset, targetScale: Float, viewport: IntSize): Offset {
        if (targetScale <= 1.001f || viewport.width <= 0 || viewport.height <= 0) {
            return Offset.Zero
        }
        val vw = viewport.width.toFloat()
        val vh = viewport.height.toFloat()
        val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
        val imgH = bitmap.height.toFloat().coerceAtLeast(1f)

        // Compute actual ContentScale.Fit fitted dimensions inside viewport
        val fitScale = minOf(vw / imgW, vh / imgH)
        val fittedW = imgW * fitScale
        val fittedH = imgH * fitScale

        val scaledW = fittedW * targetScale
        val scaledH = fittedH * targetScale

        val maxX = ((scaledW - vw).coerceAtLeast(0f) / 2f).coerceAtLeast((vw * (targetScale - 1f)) * 0.35f)
        val maxY = ((scaledH - vh).coerceAtLeast(0f) / 2f).coerceAtLeast((vh * (targetScale - 1f)) * 0.35f)

        return Offset(
            x = rawOffset.x.coerceIn(-maxX, maxX),
            y = rawOffset.y.coerceIn(-maxY, maxY)
        )
    }

    fun animateResetZoom() {
        scope.launch {
            launch {
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                )
            }
            launch {
                offsetAnim.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .onSizeChanged { containerSize = it }
            .pointerInput(resetKey, bitmap) {
                detectTapGestures(
                    onDoubleTap = { tapPos ->
                        val curScale = scaleAnim.value
                        if (curScale > 1.08f) {
                            animateResetZoom()
                        } else {
                            val targetScale = 2.5f.coerceAtMost(maxScale)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val focusDelta = (center - tapPos) * (targetScale - 1f)
                            val targetOffset = clampOffset(focusDelta, targetScale, size)
                            scope.launch {
                                launch {
                                    scaleAnim.animateTo(
                                        targetValue = targetScale,
                                        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                                launch {
                                    offsetAnim.animateTo(
                                        targetValue = targetOffset,
                                        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(resetKey, bitmap) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = scaleAnim.value
                    var pan = offsetAnim.value
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    var totalPanDistance = 0f
                    var totalZoomMotion = 0f

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        val pressedCount = event.changes.count { it.pressed }

                        if (!canceled && pressedCount > 0) {
                            val isMultiTouch = pressedCount >= 2
                            val zoomChange = if (isMultiTouch) event.calculateZoom() else 1f
                            val panChange = event.calculatePan()

                            if (!pastTouchSlop) {
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1f - zoomChange) * centroidSize
                                totalZoomMotion += zoomMotion
                                totalPanDistance += panChange.getDistance()

                                if (isMultiTouch || (zoom > 1.02f && totalPanDistance > touchSlop) || totalZoomMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop && (isMultiTouch || zoom > 1.02f)) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val oldZoom = zoom
                                val newZoom = (oldZoom * zoomChange).coerceIn(minScale, maxScale)
                                val zoomRatio = if (oldZoom > 0f) newZoom / oldZoom else 1f

                                val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                val rawNewOffset = if (isMultiTouch && centroid != Offset.Unspecified) {
                                    // Anchor zoom around the two-finger pinch centroid
                                    val centroidRel = centroid - viewportCenter
                                    (pan - centroidRel) * zoomRatio + centroidRel + panChange
                                } else {
                                    pan + panChange
                                }

                                zoom = newZoom
                                pan = if (newZoom <= 1.005f) {
                                    Offset.Zero
                                } else {
                                    clampOffset(rawNewOffset, newZoom, size)
                                }

                                scope.launch {
                                    scaleAnim.snapTo(zoom)
                                    offsetAnim.snapTo(pan)
                                }

                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Snap cleanly back to 1.0f if released very close to unzoomed scale
                    if (scaleAnim.value in 1.0001f..1.04f) {
                        animateResetZoom()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetAnim.value.x
                    translationY = offsetAnim.value.y
                }
                .testTag("zoomable_word_image_canvas")
        )

        if (showZoomBadge) {
            AnimatedVisibility(
                visible = scaleAnim.value > 1.06f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.78f),
                    border = BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.22f)
                    ),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { animateResetZoom() }
                        .testTag("reset_word_image_zoom_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = S.recording.restoreImageSizeDesc,
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = S.recording.zoomRestoreLabel.withArgs("scale" to String.format(Locale.US, "%.1f", scaleAnim.value)),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
