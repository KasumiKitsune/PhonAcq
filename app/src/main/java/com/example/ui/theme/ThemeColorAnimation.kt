package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.components.StudioMotion

/**
 * Smoothly interpolates all core Material 3 semantic color slots over [durationMillis] (default 220ms)
 * so switching light/dark mode or theme palettes never flashes abruptly.
 */
@Composable
fun ColorScheme.animated(durationMillis: Int = StudioMotion.THEME_MS): ColorScheme {
    val spec = StudioMotion.themeTween<Color>(durationMillis)

    @Composable
    fun Color.anim(label: String): Color = animateColorAsState(
        targetValue = this,
        animationSpec = spec,
        label = label
    ).value

    return copy(
        primary = primary.anim("cs_primary"),
        onPrimary = onPrimary.anim("cs_onPrimary"),
        primaryContainer = primaryContainer.anim("cs_primaryContainer"),
        onPrimaryContainer = onPrimaryContainer.anim("cs_onPrimaryContainer"),
        inversePrimary = inversePrimary.anim("cs_inversePrimary"),
        secondary = secondary.anim("cs_secondary"),
        onSecondary = onSecondary.anim("cs_onSecondary"),
        secondaryContainer = secondaryContainer.anim("cs_secondaryContainer"),
        onSecondaryContainer = onSecondaryContainer.anim("cs_onSecondaryContainer"),
        tertiary = tertiary.anim("cs_tertiary"),
        onTertiary = onTertiary.anim("cs_onTertiary"),
        tertiaryContainer = tertiaryContainer.anim("cs_tertiaryContainer"),
        onTertiaryContainer = onTertiaryContainer.anim("cs_onTertiaryContainer"),
        background = background.anim("cs_background"),
        onBackground = onBackground.anim("cs_onBackground"),
        surface = surface.anim("cs_surface"),
        onSurface = onSurface.anim("cs_onSurface"),
        surfaceVariant = surfaceVariant.anim("cs_surfaceVariant"),
        onSurfaceVariant = onSurfaceVariant.anim("cs_onSurfaceVariant"),
        surfaceTint = Color.Transparent,
        inverseSurface = inverseSurface.anim("cs_inverseSurface"),
        inverseOnSurface = inverseOnSurface.anim("cs_inverseOnSurface"),
        error = error.anim("cs_error"),
        onError = onError.anim("cs_onError"),
        errorContainer = errorContainer.anim("cs_errorContainer"),
        onErrorContainer = onErrorContainer.anim("cs_onErrorContainer"),
        outline = outline.anim("cs_outline"),
        outlineVariant = outlineVariant.anim("cs_outlineVariant"),
        surfaceBright = surfaceBright.anim("cs_surfaceBright"),
        surfaceDim = surfaceDim.anim("cs_surfaceDim"),
        surfaceContainerLowest = surfaceContainerLowest.anim("cs_surfaceContainerLowest"),
        surfaceContainerLow = surfaceContainerLow.anim("cs_surfaceContainerLow"),
        surfaceContainer = surfaceContainer.anim("cs_surfaceContainer"),
        surfaceContainerHigh = surfaceContainerHigh.anim("cs_surfaceContainerHigh"),
        surfaceContainerHighest = surfaceContainerHighest.anim("cs_surfaceContainerHighest")
    )
}
