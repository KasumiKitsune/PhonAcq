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
import com.example.ui.components.StudioMotion
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Custom desktop hover/press/focus indication:
 * - Clean primary-tinted fill clipped to the component's own shape (no mismatched hardcoded outer border)
 * - Crisp `2.dp` keyboard focus ring (`primary.copy(alpha = 0.72f)`) for accessible Tab navigation
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
    private val animatedFocusAlpha = Animatable(0f)

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

                val targetFill = when {
                    activePresses.isNotEmpty() -> 0.16f
                    activeHovers.isNotEmpty() -> 0.08f
                    activeFocuses.isNotEmpty() -> 0.05f
                    else -> 0f
                }
                val targetFocus = if (activeFocuses.isNotEmpty()) 1f else 0f

                launch {
                    animatedAlpha.animateTo(targetFill, tween(durationMillis = StudioMotion.MICRO_MS))
                }
                launch {
                    animatedFocusAlpha.animateTo(targetFocus, tween(durationMillis = StudioMotion.MICRO_MS))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = animatedAlpha.value
        val focusAlpha = animatedFocusAlpha.value
        val minDim = size.minDimension
        val isCircularTarget = minDim <= 52.dp.toPx() && abs(size.width - size.height) <= 8.dp.toPx()

        if (alpha > 0.003f) {
            if (isCircularTarget) {
                drawCircle(
                    color = tintColor.copy(alpha = alpha),
                    radius = minDim / 2f,
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            } else {
                drawRect(
                    color = tintColor.copy(alpha = alpha),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
            }
        }

        if (focusAlpha > 0.01f) {
            val strokePx = 2.dp.toPx()
            val inset = strokePx / 2f
            val ringColor = tintColor.copy(alpha = 0.72f * focusAlpha)
            if (isCircularTarget) {
                drawCircle(
                    color = ringColor,
                    radius = (minDim / 2f - inset).coerceAtLeast(1f),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(width = strokePx)
                )
            } else {
                val r = 8.dp.toPx().coerceAtMost(minDim / 2f)
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (size.width - strokePx).coerceAtLeast(0f),
                        (size.height - strokePx).coerceAtLeast(0f)
                    ),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = strokePx)
                )
            }
        }
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    themePaletteId: String = PhonAcqThemePalette.STUDIO_BLUE.id,
    content: @Composable () -> Unit,
) {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    remember(fontFamilyResolver) {
        DesktopTypographyChain.installInto(fontFamilyResolver)
        true
    }
    val palette = PhonAcqThemePalette.fromId(themePaletteId)
    val rawColorScheme = buildPhonAcqColorScheme(palette = palette, darkTheme = darkTheme)
    val colorScheme = rawColorScheme.animated()
    val extendedColors = buildPhonAcqExtendedColors(darkTheme = darkTheme)

    val studioIndication = remember(rawColorScheme.primary) {
        StudioDesktopIndication(rawColorScheme.primary)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DesktopTypographyChain.typography,
        shapes = PhonAcqShapes
    ) {
        CompositionLocalProvider(
            LocalIndication provides studioIndication,
            LocalPhoneticFontFamily provides DesktopTypographyChain.phoneticIpaFontFamily,
            LocalPhonAcqExtendedColors provides extendedColors,
            content = content
        )
    }
}
