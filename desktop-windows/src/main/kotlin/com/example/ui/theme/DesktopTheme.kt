package com.example.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DarkColorScheme =
    darkColorScheme(
        primary = PrimaryBlueDark,
        onPrimary = BGDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = AccentTealDark,
        onSecondary = BGDark,
        secondaryContainer = AccentContainerTealDark,
        onSecondaryContainer = OnAccentContainerTealDark,
        background = BGDark,
        onBackground = TextLight,
        surface = SurfaceDark,
        onSurface = TextLight,
        surfaceVariant = SurfaceContainerHighDark,
        onSurfaceVariant = TextMutedDark,
        surfaceContainerLowest = BGDark,
        surfaceContainerLow = SurfaceContainerLowDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighestDark,
        outline = BorderDark,
        outlineVariant = BorderVariantDark
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PrimaryBlue,
        onPrimary = Color.White,
        primaryContainer = PrimaryContainerBlue,
        onPrimaryContainer = OnPrimaryContainerBlue,
        secondary = AccentTeal,
        onSecondary = Color.White,
        secondaryContainer = AccentContainerTeal,
        onSecondaryContainer = OnAccentContainerTeal,
        background = BGNeutral,
        onBackground = TextDark,
        surface = SurfaceNeutral,
        onSurface = TextDark,
        surfaceVariant = SurfaceContainerHighLight,
        onSurfaceVariant = TextMuted,
        surfaceContainerLowest = SurfaceNeutral,
        surfaceContainerLow = SurfaceContainerLowLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighestLight,
        outline = BorderMuted,
        outlineVariant = BorderLight
    )

/**
 * Custom desktop hover/press indication that replaces Compose Desktop's harsh, unrounded
 * dark-gray rectangle overlay with a smooth, rounded, primary-tinted studio highlight.
 */
private class StudioDesktopIndication(
    private val tintColor: Color
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return StudioDesktopIndicationNode(interactionSource, tintColor)
    }

    override fun hashCode(): Int = tintColor.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StudioDesktopIndication) return false
        return tintColor == other.tintColor
    }
}

private class StudioDesktopIndicationNode(
    private val interactionSource: InteractionSource,
    private val tintColor: Color
) : Modifier.Node(), DrawModifierNode {

    private val animatedAlpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            val activeHovers = mutableSetOf<HoverInteraction.Enter>()
            val activePresses = mutableSetOf<PressInteraction.Press>()
            val activeFocuses = mutableSetOf<FocusInteraction.Focus>()

            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is HoverInteraction.Enter -> activeHovers.add(interaction)
                    is HoverInteraction.Exit -> activeHovers.remove(interaction.enter)
                    is PressInteraction.Press -> activePresses.add(interaction)
                    is PressInteraction.Release -> activePresses.remove(interaction.press)
                    is PressInteraction.Cancel -> activePresses.remove(interaction.press)
                    is FocusInteraction.Focus -> activeFocuses.add(interaction)
                    is FocusInteraction.Unfocus -> activeFocuses.remove(interaction.focus)
                }

                val target = when {
                    activePresses.isNotEmpty() -> 0.18f
                    activeHovers.isNotEmpty() -> 0.10f
                    activeFocuses.isNotEmpty() -> 0.06f
                    else -> 0f
                }
                animatedAlpha.animateTo(target, tween(durationMillis = 120))
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = animatedAlpha.value
        if (alpha > 0.003f) {
            val minDim = size.minDimension
            val isCircularTarget = minDim <= 52.dp.toPx() && abs(size.width - size.height) <= 8.dp.toPx()
            val radius = if (isCircularTarget) minDim / 2f else 10.dp.toPx().coerceAtMost(minDim / 2f)
            val cornerRadius = CornerRadius(radius, radius)

            drawRoundRect(
                color = tintColor.copy(alpha = alpha),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = tintColor.copy(alpha = (alpha * 2.2f).coerceAtMost(0.28f)),
                topLeft = Offset(0.5f, 0.5f),
                size = Size((size.width - 1f).coerceAtLeast(0f), (size.height - 1f).coerceAtLeast(0f)),
                cornerRadius = cornerRadius,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    remember(fontFamilyResolver) {
        DesktopTypographyChain.installInto(fontFamilyResolver)
        true
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val studioIndication = remember(colorScheme.primary) {
        StudioDesktopIndication(colorScheme.primary)
    }
    MaterialTheme(colorScheme = colorScheme, typography = DesktopTypographyChain.typography) {
        CompositionLocalProvider(
            LocalIndication provides studioIndication,
            LocalPhoneticFontFamily provides DesktopTypographyChain.phoneticIpaFontFamily,
            content = content
        )
    }
}
