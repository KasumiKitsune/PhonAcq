package com.example.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Unified, restrained studio motion token system shared across Android & Desktop.
 * Crisp desktop-grade timing (max 220ms, exits 25%-35% faster than entrances).
 */
object StudioMotion {
    const val MICRO_MS = 120
    const val SHORT_IN_MS = 160
    const val SHORT_OUT_MS = 110
    const val MEDIUM_IN_MS = 200
    const val MEDIUM_OUT_MS = 145
    const val PAGE_IN_MS = 220
    const val PAGE_OUT_MS = 150
    const val THEME_MS = 220

    fun <T> microTween(): TweenSpec<T> = tween(
        durationMillis = MICRO_MS,
        easing = FastOutSlowInEasing
    )

    fun <T> shortInTween(): TweenSpec<T> = tween(
        durationMillis = SHORT_IN_MS,
        easing = LinearOutSlowInEasing
    )

    fun <T> shortOutTween(): TweenSpec<T> = tween(
        durationMillis = SHORT_OUT_MS,
        easing = FastOutLinearInEasing
    )

    fun <T> mediumInTween(): TweenSpec<T> = tween(
        durationMillis = MEDIUM_IN_MS,
        easing = FastOutSlowInEasing
    )

    fun <T> mediumOutTween(): TweenSpec<T> = tween(
        durationMillis = MEDIUM_OUT_MS,
        easing = FastOutLinearInEasing
    )

    fun <T> pageInTween(): TweenSpec<T> = tween(
        durationMillis = PAGE_IN_MS,
        easing = FastOutSlowInEasing
    )

    fun <T> pageOutTween(): TweenSpec<T> = tween(
        durationMillis = PAGE_OUT_MS,
        easing = FastOutLinearInEasing
    )

    fun <T> themeTween(durationMillis: Int = THEME_MS): TweenSpec<T> = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    )

    fun <T> tactileSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = 0.74f,
        stiffness = 620f,
        visibilityThreshold = visibilityThreshold
    )

    fun <T> listItemSpring(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = 480f,
        visibilityThreshold = visibilityThreshold
    )

    fun subtleFadeScale(): ContentTransform =
        (fadeIn(shortInTween()) + scaleIn(shortInTween(), initialScale = 0.985f)) togetherWith
            fadeOut(shortOutTween())

    fun pageFadeScale(): ContentTransform =
        (fadeIn(pageInTween()) + scaleIn(pageInTween(), initialScale = 0.985f)) togetherWith
            fadeOut(pageOutTween())

    val verticalPanelEnter: EnterTransition
        get() = expandVertically(
            animationSpec = tween(MEDIUM_IN_MS, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top
        ) + fadeIn(shortInTween())

    val verticalPanelExit: ExitTransition
        get() = shrinkVertically(
            animationSpec = tween(MEDIUM_OUT_MS, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(shortOutTween())

    val horizontalPaneEnter: EnterTransition
        get() = expandHorizontally(
            animationSpec = tween(MEDIUM_IN_MS, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Start
        ) + fadeIn(shortInTween())

    val horizontalPaneExit: ExitTransition
        get() = shrinkHorizontally(
            animationSpec = tween(MEDIUM_OUT_MS, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.Start
        ) + fadeOut(shortOutTween())

    fun horizontalPaneEnterFromEnd(): EnterTransition =
        expandHorizontally(
            animationSpec = tween(MEDIUM_IN_MS, easing = FastOutSlowInEasing),
            expandFrom = Alignment.End
        ) + fadeIn(shortInTween())

    fun horizontalPaneExitToEnd(): ExitTransition =
        shrinkHorizontally(
            animationSpec = tween(MEDIUM_OUT_MS, easing = FastOutLinearInEasing),
            shrinkTowards = Alignment.End
        ) + fadeOut(shortOutTween())
}

/**
 * Smoothly rotates a chevron/dropdown icon by [targetDegrees] (default 180°) when [expanded] changes.
 */
@Composable
fun Modifier.studioRotateOn(
    expanded: Boolean,
    targetDegrees: Float = 180f
): Modifier {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) targetDegrees else 0f,
        animationSpec = StudioMotion.tactileSpring(),
        label = "StudioChevronRotation"
    )
    return this.graphicsLayer { rotationZ = rotation }
}

/**
 * Smoothly animates container size changes (pills, expandable cards, status badges) using the
 * unified [StudioMotion.MEDIUM_IN_MS] curve.
 */
fun Modifier.studioAnimateSize(): Modifier = this.animateContentSize(
    animationSpec = tween<IntSize>(
        durationMillis = StudioMotion.MEDIUM_IN_MS,
        easing = FastOutSlowInEasing
    )
)

/**
 * Smoothly animates item reordering, filtering, insertion, and removal inside a [LazyColumn] or [LazyRow].
 */
fun LazyItemScope.studioAnimateItem(modifier: Modifier = Modifier): Modifier = modifier.animateItem(
    fadeInSpec = StudioMotion.shortInTween(),
    placementSpec = StudioMotion.listItemSpring(IntOffset.VisibilityThreshold),
    fadeOutSpec = StudioMotion.shortOutTween()
)

/**
 * Smoothly animates item reordering, filtering, insertion, and removal inside a [LazyVerticalGrid].
 */
fun LazyGridItemScope.studioAnimateItem(modifier: Modifier = Modifier): Modifier = modifier.animateItem(
    fadeInSpec = StudioMotion.shortInTween(),
    placementSpec = StudioMotion.listItemSpring(IntOffset.VisibilityThreshold),
    fadeOutSpec = StudioMotion.shortOutTween()
)
