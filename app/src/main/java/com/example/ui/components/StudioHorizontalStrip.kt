package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Desktop-friendly horizontal strip shell for [androidx.compose.foundation.lazy.LazyRow].
 *
 * - Soft dynamic 22.dp edge alpha-fade mask via [BlendMode.DstIn] whenever overflow exists
 *   (seamlessly adapts to any light/dark/tinted container background on both Android & Desktop).
 * - On Desktop only: hover-revealed `<` / `>` mini circular step buttons, Shift+Wheel horizontal
 *   scroll (without hijacking plain vertical wheel scrolling), and primary-button drag-to-pan
 *   with touchSlop threshold so chip clicks remain 100% crisp.
 * - On Android touch devices: zero visual clutter and full native touch swiping.
 */
@Composable
fun StudioHorizontalLazyStrip(
    state: LazyListState,
    modifier: Modifier = Modifier,
    scrollStep: Dp = 160.dp,
    content: @Composable BoxScope.() -> Unit
) {
    StudioHorizontalStripShell(
        scrollableState = state,
        modifier = modifier,
        scrollStep = scrollStep,
        content = content
    )
}

/**
 * Desktop-friendly horizontal strip shell for containers using `Modifier.horizontalScroll(state)`.
 */
@Composable
fun StudioHorizontalScrollStrip(
    state: ScrollState,
    modifier: Modifier = Modifier,
    scrollStep: Dp = 160.dp,
    content: @Composable BoxScope.() -> Unit
) {
    StudioHorizontalStripShell(
        scrollableState = state,
        modifier = modifier,
        scrollStep = scrollStep,
        content = content
    )
}

@Composable
private fun StudioHorizontalStripShell(
    scrollableState: ScrollableState,
    modifier: Modifier,
    scrollStep: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val stepPx = with(density) { scrollStep.toPx() }
    val wheelStepPx = with(density) { 56.dp.toPx() }
    val maxWheelDeltaPx = stepPx * 1.5f

    val canScrollBackward by remember(scrollableState) {
        derivedStateOf { scrollableState.canScrollBackward }
    }
    val canScrollForward by remember(scrollableState) {
        derivedStateOf { scrollableState.canScrollForward }
    }
    val hasOverflow = canScrollBackward || canScrollForward

    val (stripInteraction, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    var activeScrollJob by remember(scrollableState) { mutableStateOf<Job?>(null) }

    val leftFadeStrength by animateFloatAsState(
        targetValue = if (canScrollBackward) 1f else 0f,
        animationSpec = StudioMotion.microTween(),
        label = "StudioStripLeftFade"
    )
    val rightFadeStrength by animateFloatAsState(
        targetValue = if (canScrollForward) 1f else 0f,
        animationSpec = StudioMotion.microTween(),
        label = "StudioStripRightFade"
    )
    val needsFade = leftFadeStrength > 0.005f || rightFadeStrength > 0.005f

    val contentEdgeFadeModifier = if (needsFade) {
        Modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (size.width > 1f) {
                    val fadePx = 22.dp.toPx().coerceAtMost(size.width * 0.35f)
                    val fadeFraction = (fadePx / size.width).coerceIn(0.001f, 0.35f)
                    val leftEdgeAlpha = (1f - leftFadeStrength).coerceIn(0f, 1f)
                    val rightEdgeAlpha = (1f - rightFadeStrength).coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = leftEdgeAlpha),
                            fadeFraction to Color.Black,
                            (1f - fadeFraction) to Color.Black,
                            1f to Color.Black.copy(alpha = rightEdgeAlpha),
                            startX = 0f,
                            endX = size.width
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    } else {
        Modifier
    }

    val desktopPointerModifier = if (isDesktop && hasOverflow) {
        Modifier
            .pointerInput(scrollableState, hasOverflow, wheelStepPx) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isShiftPressed) {
                            val change = event.changes.firstOrNull() ?: continue
                            val scrollY = change.scrollDelta.y
                            // Convert Shift + vertical wheel to horizontal scroll; leave native horizontal wheel untouched
                            if (abs(scrollY) > 0.01f && abs(change.scrollDelta.x) < 0.01f) {
                                change.consume()
                                val deltaPx = (scrollY * wheelStepPx).coerceIn(-maxWheelDeltaPx, maxWheelDeltaPx)
                                activeScrollJob?.cancel()
                                activeScrollJob = coroutineScope.launch {
                                    scrollableState.animateScrollBy(deltaPx, StudioMotion.shortInTween())
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(scrollableState, hasOverflow) {
                val touchSlop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                        if (downEvent.type != PointerEventType.Press) continue
                        if (!downEvent.buttons.isPrimaryPressed) continue
                        val downChange = downEvent.changes.firstOrNull { it.pressed && !it.isConsumed } ?: continue
                        val pointerId = downChange.id
                        val startPos = downChange.position
                        var lastX = startPos.x
                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                if (isDragging) {
                                    change.consume()
                                }
                                break
                            }
                            val dxFromStart = change.position.x - startPos.x
                            val dyFromStart = change.position.y - startPos.y
                            if (!isDragging) {
                                if (abs(dyFromStart) > touchSlop && abs(dyFromStart) > abs(dxFromStart)) {
                                    break
                                }
                                if (abs(dxFromStart) > touchSlop && abs(dxFromStart) >= abs(dyFromStart)) {
                                    isDragging = true
                                    activeScrollJob?.cancel()
                                    val stepDx = change.position.x - lastX
                                    lastX = change.position.x
                                    change.consume()
                                    if (abs(stepDx) > 0.01f) {
                                        scrollableState.dispatchRawDelta(-stepDx)
                                    }
                                }
                            } else {
                                val stepDx = change.position.x - lastX
                                lastX = change.position.x
                                change.consume()
                                if (abs(stepDx) > 0.01f) {
                                    scrollableState.dispatchRawDelta(-stepDx)
                                }
                            }
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.then(
            if (isDesktop) Modifier.hoverable(stripInteraction) else Modifier
        )
    ) {
        Box(
            modifier = Modifier
                .then(desktopPointerModifier)
                .then(contentEdgeFadeModifier),
            content = content
        )

        if (isDesktop) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isHovered && canScrollBackward,
                enter = fadeIn(StudioMotion.shortInTween()),
                exit = fadeOut(StudioMotion.shortOutTween()),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp)
            ) {
                StudioStripNavButton(
                    directionForward = false,
                    onClick = {
                        activeScrollJob?.cancel()
                        activeScrollJob = coroutineScope.launch {
                            scrollableState.animateScrollBy(-stepPx, StudioMotion.mediumInTween())
                        }
                    }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isHovered && canScrollForward,
                enter = fadeIn(StudioMotion.shortInTween()),
                exit = fadeOut(StudioMotion.shortOutTween()),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp)
            ) {
                StudioStripNavButton(
                    directionForward = true,
                    onClick = {
                        activeScrollJob?.cancel()
                        activeScrollJob = coroutineScope.launch {
                            scrollableState.animateScrollBy(stepPx, StudioMotion.mediumInTween())
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StudioStripNavButton(
    directionForward: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (btnInteraction, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    val bgColor by animateColorAsState(
        targetValue = if (isHovered) {
            lerp(colorScheme.surface, colorScheme.primaryContainer, 0.55f)
        } else {
            colorScheme.surface.copy(alpha = 0.94f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "StudioStripNavBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isHovered) {
            colorScheme.primary.copy(alpha = 0.65f)
        } else {
            colorScheme.outlineVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "StudioStripNavBorder"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isHovered) {
            colorScheme.primary
        } else {
            colorScheme.onSurfaceVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "StudioStripNavIcon"
    )

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .size(22.dp)
            .studioTactile(btnInteraction, pressScale = 0.90f)
            .clip(CircleShape)
            .hoverable(btnInteraction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = btnInteraction,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (directionForward) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                },
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}
