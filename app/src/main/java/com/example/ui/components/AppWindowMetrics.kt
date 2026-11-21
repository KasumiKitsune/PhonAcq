package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Single source of truth for window width breakpoints shared across Android and Desktop.
 * - [Compact]: `< 600.dp` (phone portrait / ultra-narrow desktop snap)
 * - [Medium]: `600..959.dp` (tablet portrait / Windows half-screen snap)
 * - [Expanded]: `960..1439.dp` (standard desktop window / tablet landscape)
 * - [UltraWide]: `>= 1440.dp` (wide monitor / maximized desktop)
 */
enum class AppWidthClass {
    Compact,
    Medium,
    Expanded,
    UltraWide
}

/**
 * Window height classification:
 * - [Short]: `< 540.dp` (landscape phone / short desktop window)
 * - [Regular]: `>= 540.dp`
 */
enum class AppHeightClass {
    Short,
    Regular
}

@Immutable
data class AppWindowMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    val widthClass: AppWidthClass,
    val heightClass: AppHeightClass
) {
    val isCompactWidth: Boolean get() = widthClass == AppWidthClass.Compact
    val isMediumWidth: Boolean get() = widthClass == AppWidthClass.Medium
    val isExpandedOrWider: Boolean get() = widthClass == AppWidthClass.Expanded || widthClass == AppWidthClass.UltraWide
    val isUltraWide: Boolean get() = widthClass == AppWidthClass.UltraWide
    val isShortHeight: Boolean get() = heightClass == AppHeightClass.Short
}

fun computeAppWindowMetrics(maxWidth: Dp, maxHeight: Dp): AppWindowMetrics {
    val wClass = when {
        maxWidth < 600.dp -> AppWidthClass.Compact
        maxWidth < 960.dp -> AppWidthClass.Medium
        maxWidth < 1440.dp -> AppWidthClass.Expanded
        else -> AppWidthClass.UltraWide
    }
    val hClass = if (maxHeight < 540.dp) AppHeightClass.Short else AppHeightClass.Regular
    return AppWindowMetrics(
        widthDp = maxWidth,
        heightDp = maxHeight,
        widthClass = wClass,
        heightClass = hClass
    )
}

val LocalAppWindowMetrics = compositionLocalOf {
    AppWindowMetrics(
        widthDp = 1280.dp,
        heightDp = 840.dp,
        widthClass = AppWidthClass.Expanded,
        heightClass = AppHeightClass.Regular
    )
}

/**
 * Centers content horizontally while clamping its width to [maxWidth] on wide monitors.
 * Transparent on mobile/narrow windows where viewport width is already below [maxWidth].
 */
@Composable
fun ResponsiveContentContainer(
    maxWidth: Dp = 840.dp,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Adaptive modal card surface that dynamically clamps its width and height to the available
 * window viewport so dialogs never clip off-screen in `480×560` or compact windows.
 */
@Composable
fun AdaptiveModalSurface(
    targetMaxWidth: Dp = 540.dp,
    maxHeightFraction: Float = 0.90f,
    shape: Shape = RoundedCornerShape(22.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border: BorderStroke? = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    ),
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val windowMetrics = LocalAppWindowMetrics.current
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val availWidth = if (maxWidth.value.isFinite() && maxWidth > 0.dp) maxWidth else windowMetrics.widthDp
        val availHeight = if (maxHeight.value.isFinite() && maxHeight > 0.dp) maxHeight else windowMetrics.heightDp

        val computedWidth = (availWidth - 24.dp)
            .coerceAtMost(targetMaxWidth)
            .coerceAtLeast(if (availWidth < 264.dp) (availWidth - 16.dp).coerceAtLeast(180.dp) else 240.dp)
        val computedMaxHeight = (availHeight * maxHeightFraction).coerceAtLeast(240.dp)

        Surface(
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = border,
            modifier = modifier
                .width(computedWidth)
                .heightIn(max = computedMaxHeight)
                .consumeDialogCardTaps()
        ) {
            Box(content = content)
        }
    }
}
