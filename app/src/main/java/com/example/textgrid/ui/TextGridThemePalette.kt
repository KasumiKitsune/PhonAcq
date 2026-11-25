package com.example.textgrid.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.textgrid.model.TextGridScoreTheme
import com.example.ui.theme.LocalPhonAcqExtendedColors
import com.example.ui.theme.PhonAcqExtendedColors

/**
 * Refined, restrained semantic palette for pure read-only Dual-Scale Speech Score rendering
 * and synchronized source code highlighting across 3 themes:
 * - [TextGridScoreTheme.FOLLOW_APP] (default, deeply integrated with Material 3 surface hierarchy)
 * - [TextGridScoreTheme.WARM_PAPER] (refined archival warm paper)
 * - [TextGridScoreTheme.ACADEMIC_BW] (publication-grade crisp monochrome)
 */
@Immutable
data class TextGridScorePalette(
    val theme: TextGridScoreTheme,
    val isDark: Boolean,
    val isBw: Boolean,
    val surfaceBg: Color,
    val cardBorder: Color,
    val gutterBg: Color,
    val gutterDivider: Color,
    val ink: Color,
    val border: Color,
    val muted: Color,
    val faint: Color,
    val faintTick: Color,
    val rule: Color,
    val bridgeMajor: Color,
    val bridgeMinor: Color,
    val linearTrackBg: Color,
    val linearActiveFill: Color,
    val linearCollapsedBg: Color,
    val emptyCell: Color,
    val cellFill: Color,
    val wire: Color,
    val selectionAccent: Color,
    val selectionFill: Color,
    val playheadColor: Color,
    val playRangeFill: Color,
    val stretchHint: Color,
    val gapBg: Color,
    val gapStroke: Color,
    val oodBg: Color,
    val oodStroke: Color,
    val overlapStroke: Color,
    val zeroBg: Color,
    val glossColor: Color,
    val metaSymbolColor: Color,
    val ctrlCharColor: Color,
    val tierAccents: List<Color>,
    // Read-only source code syntax colors
    val codeBg: Color,
    val codeGutterBg: Color,
    val codeLineNum: Color,
    val codeKeyword: Color,
    val codeString: Color,
    val codeNumber: Color,
    val codeBracket: Color,
    val codeActiveLineBg: Color
)

object TextGridThemePaletteResolver {

    fun resolve(
        theme: TextGridScoreTheme,
        colorScheme: ColorScheme,
        extended: PhonAcqExtendedColors
    ): TextGridScorePalette {
        return when (theme) {
            TextGridScoreTheme.WARM_PAPER -> buildWarmPaperPalette()
            TextGridScoreTheme.ACADEMIC_BW -> buildAcademicBwPalette()
            TextGridScoreTheme.FOLLOW_APP -> buildFollowAppPalette(colorScheme, extended)
        }
    }

    private fun buildWarmPaperPalette(): TextGridScorePalette {
        return TextGridScorePalette(
            theme = TextGridScoreTheme.WARM_PAPER,
            isDark = false,
            isBw = false,
            surfaceBg = Color(0xFFFCFBF8),
            cardBorder = Color(0xFFDCD8CE),
            gutterBg = Color(0xFFF6F4EE),
            gutterDivider = Color(0xFFE3DFD6),
            ink = Color(0xFF1C1B19),
            border = Color(0xFF8C877C),
            muted = Color(0xFF66625A),
            faint = Color(0xFF948F84),
            faintTick = Color(0xFFABA69B),
            rule = Color(0xFFD2CEC4),
            bridgeMajor = Color(0xFFB5B0A4),
            bridgeMinor = Color(0xFFDCD8CE),
            linearTrackBg = Color(0xFFF1EEE7),
            linearActiveFill = Color(0xFF3E4C5E).copy(alpha = 0.24f),
            linearCollapsedBg = Color(0xFFE7E3DA),
            emptyCell = Color(0xFFF5F3EC),
            cellFill = Color(0xFFFFFFFF),
            wire = Color(0xFF68645B),
            selectionAccent = Color(0xFF2563EB),
            selectionFill = Color(0x182563EB),
            playheadColor = Color(0xFFC2410C),
            playRangeFill = Color(0x142563EB),
            stretchHint = Color(0xFFB45309),
            gapBg = Color(0xFFFEF8EC),
            gapStroke = Color(0xFFD97706),
            oodBg = Color(0xFFEEECE5),
            oodStroke = Color(0xFF9C978C),
            overlapStroke = Color(0xFFDC2626),
            zeroBg = Color(0xFFFFFBEB),
            glossColor = Color(0xFF6D28D9),
            metaSymbolColor = Color(0xFF0F766E),
            ctrlCharColor = Color(0xFF9A3412),
            tierAccents = listOf(
                Color(0xFF2C5282),
                Color(0xFF2F6F62),
                Color(0xFF7B5B3A),
                Color(0xFF5A4E82),
                Color(0xFF7C4355),
                Color(0xFF475569)
            ),
            codeBg = Color(0xFFFAF8F3),
            codeGutterBg = Color(0xFFF3F0E8),
            codeLineNum = Color(0xFF9A958A),
            codeKeyword = Color(0xFF3B5998),
            codeString = Color(0xFF1F6F5B),
            codeNumber = Color(0xFF9A4E1C),
            codeBracket = Color(0xFF6E6A61),
            codeActiveLineBg = Color(0x1C2563EB)
        )
    }

    private fun buildAcademicBwPalette(): TextGridScorePalette {
        return TextGridScorePalette(
            theme = TextGridScoreTheme.ACADEMIC_BW,
            isDark = false,
            isBw = true,
            surfaceBg = Color(0xFFFFFFFF),
            cardBorder = Color(0xFFD4D4D4),
            gutterBg = Color(0xFFFAFAFA),
            gutterDivider = Color(0xFFE5E5E5),
            ink = Color(0xFF111111),
            border = Color(0xFF404040),
            muted = Color(0xFF525252),
            faint = Color(0xFF7A7A7A),
            faintTick = Color(0xFF9A9A9A),
            rule = Color(0xFFC8C8C8),
            bridgeMajor = Color(0xFF9E9E9E),
            bridgeMinor = Color(0xFFDCDCDC),
            linearTrackBg = Color(0xFFF2F2F2),
            linearActiveFill = Color(0xFF171717).copy(alpha = 0.22f),
            linearCollapsedBg = Color(0xFFE5E5E5),
            emptyCell = Color(0xFFF7F7F7),
            cellFill = Color(0xFFFFFFFF),
            wire = Color(0xFF404040),
            selectionAccent = Color(0xFF1D4ED8),
            selectionFill = Color(0x161D4ED8),
            playheadColor = Color(0xFFB91C1C),
            playRangeFill = Color(0x12000000),
            stretchHint = Color(0xFF666666),
            gapBg = Color(0xFFF7F7F7),
            gapStroke = Color(0xFF666666),
            oodBg = Color(0xFFF0F0F0),
            oodStroke = Color(0xFF737373),
            overlapStroke = Color(0xFF262626),
            zeroBg = Color(0xFFF5F5F5),
            glossColor = Color(0xFF525252),
            metaSymbolColor = Color(0xFF666666),
            ctrlCharColor = Color(0xFF4D4D4D),
            tierAccents = listOf(
                Color(0xFF171717),
                Color(0xFF404040),
                Color(0xFF525252),
                Color(0xFF262626),
                Color(0xFF666666),
                Color(0xFF333333)
            ),
            codeBg = Color(0xFFFFFFFF),
            codeGutterBg = Color(0xFFF7F7F7),
            codeLineNum = Color(0xFF8C8C8C),
            codeKeyword = Color(0xFF171717),
            codeString = Color(0xFF404040),
            codeNumber = Color(0xFF262626),
            codeBracket = Color(0xFF666666),
            codeActiveLineBg = Color(0x141D4ED8)
        )
    }

    private fun buildFollowAppPalette(
        colorScheme: ColorScheme,
        extended: PhonAcqExtendedColors
    ): TextGridScorePalette {
        val isDark = colorScheme.surface.luminance() < 0.4f
        val chart = extended.chartPalette
        val surfaceBg = colorScheme.surfaceContainerLowest
        val cellFill = colorScheme.surface
        val emptyCell = if (isDark) {
            lerp(colorScheme.surfaceContainerLow, colorScheme.surfaceContainer, 0.35f)
        } else {
            colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
        }
        val crispBorder = if (isDark) {
            colorScheme.outline.copy(alpha = 0.56f)
        } else {
            colorScheme.outline.copy(alpha = 0.48f)
        }
        val mutedAccents = chart.take(6).map { raw ->
            lerp(raw, colorScheme.onSurfaceVariant, if (isDark) 0.16f else 0.18f)
        }

        return TextGridScorePalette(
            theme = TextGridScoreTheme.FOLLOW_APP,
            isDark = isDark,
            isBw = false,
            surfaceBg = surfaceBg,
            cardBorder = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.72f else 0.85f),
            gutterBg = colorScheme.surfaceContainerLow.copy(alpha = if (isDark) 0.55f else 0.65f),
            gutterDivider = colorScheme.outlineVariant.copy(alpha = 0.6f),
            ink = colorScheme.onSurface,
            border = crispBorder,
            muted = colorScheme.onSurfaceVariant,
            faint = colorScheme.outline,
            faintTick = colorScheme.outline.copy(alpha = 0.62f),
            rule = colorScheme.outlineVariant,
            bridgeMajor = colorScheme.outline.copy(alpha = if (isDark) 0.44f else 0.42f),
            bridgeMinor = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.48f else 0.60f),
            linearTrackBg = colorScheme.surfaceContainer,
            linearActiveFill = colorScheme.primary.copy(alpha = if (isDark) 0.32f else 0.22f),
            linearCollapsedBg = colorScheme.surfaceContainerHigh,
            emptyCell = emptyCell,
            cellFill = cellFill,
            wire = colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            selectionAccent = colorScheme.primary,
            selectionFill = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.38f else 0.50f),
            playheadColor = colorScheme.tertiary,
            playRangeFill = colorScheme.primary.copy(alpha = if (isDark) 0.18f else 0.12f),
            stretchHint = extended.warning,
            gapBg = extended.warningContainer.copy(alpha = if (isDark) 0.30f else 0.45f),
            gapStroke = extended.warning.copy(alpha = 0.82f),
            oodBg = colorScheme.surfaceContainerHigh,
            oodStroke = colorScheme.outline,
            overlapStroke = colorScheme.error.copy(alpha = 0.85f),
            zeroBg = extended.warningContainer,
            glossColor = chart.getOrElse(3) { colorScheme.tertiary },
            metaSymbolColor = if (isDark) Color(0xFF4ADE9B) else Color(0xFF0F766E),
            ctrlCharColor = if (isDark) Color(0xFFFB923C) else Color(0xFF9A3412),
            tierAccents = mutedAccents,
            codeBg = surfaceBg,
            codeGutterBg = colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
            codeLineNum = colorScheme.outline.copy(alpha = 0.75f),
            codeKeyword = colorScheme.primary,
            codeString = if (isDark) Color(0xFF7DD3A8) else Color(0xFF1B6B53),
            codeNumber = if (isDark) Color(0xFFF3B27A) else Color(0xFF9C4A1A),
            codeBracket = colorScheme.onSurfaceVariant,
            codeActiveLineBg = colorScheme.primaryContainer.copy(alpha = if (isDark) 0.34f else 0.48f)
        )
    }
}

@Composable
fun rememberTextGridScorePalette(theme: TextGridScoreTheme): TextGridScorePalette {
    val colorScheme = MaterialTheme.colorScheme
    val extended = LocalPhonAcqExtendedColors.current
    return remember(theme, colorScheme, extended) {
        TextGridThemePaletteResolver.resolve(theme, colorScheme, extended)
    }
}
