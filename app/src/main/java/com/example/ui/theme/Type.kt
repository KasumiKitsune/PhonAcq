package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val CenteredTrimmedLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

/**
 * Provides the bundled full-coverage IPA + Sinological phonetics sans-serif [FontFamily]
 * (covering apical vowels ɿ/ʅ/ʮ/ʯ, alveolo-palatals ȶ/ȡ/ȵ, small capitals ᴇ, Chao tone bars ˥˦˧˨˩,
 * dotted circle ◌, and OpenType GPOS combining marks such as ◌̃, ◌̥, ◌̱, p̚/t̚/k̚) with seamless OS CJK fallback.
 */
val LocalPhoneticFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * Formats a Quick-Note phonetic symbol for chip/badge UI presentation:
 * prepends a dotted circle carrier (`◌`, U+25CC) whenever [rawSymbol] begins with a Unicode combining mark
 * (such as nasalization `\u0303`, voiceless ring `\u0325`, macron below `\u0331`, or unreleased corner `\u031A`),
 * preventing bare combining marks from rendering blank or colliding with chip borders.
 */
fun formatQuickNoteSymbolForDisplay(rawSymbol: String): String {
    if (rawSymbol.isEmpty() || rawSymbol.startsWith("◌")) return rawSymbol
    val firstCp = rawSymbol.codePointAt(0)
    val category = Character.getType(firstCp)
    val isCombiningMark = category == Character.NON_SPACING_MARK.toInt() ||
        category == Character.ENCLOSING_MARK.toInt() ||
        category == Character.COMBINING_SPACING_MARK.toInt()
    return if (isCombiningMark) "◌$rawSymbol" else rawSymbol
}

/**
 * Returns true if [text] contains IPA phonetic extensions, Chao tone letters, dotted circle carriers,
 * or Unicode combining diacritics that require untrimmed vertical metrics (`LineHeightStyle.Trim.None`)
 * and the bundled IPA font family.
 */
fun containsPhoneticOrCombiningGlyphs(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val cat = Character.getType(cp)
        if (cat == Character.NON_SPACING_MARK.toInt() ||
            cat == Character.ENCLOSING_MARK.toInt() ||
            cat == Character.COMBINING_SPACING_MARK.toInt() ||
            cp == 0x25CC || // ◌ Dotted Circle
            cp in 0x014A..0x014B || // Ŋ ŋ
            cp in 0x0220..0x02FF || // Sinological ȡ/ȵ/ȶ + IPA Extensions + Spacing Modifier Letters (ʰ/ˀ/˞/˥..˩)
            cp in 0x1D00..0x1DBF // Phonetic Extensions (ᴇ, etc.)
        ) {
            return true
        }
        i += Character.charCount(cp)
    }
    return false
}

// Material 3 typography using proportional em line-heights and Trim.Both + Alignment.Center
// so custom fontSizes on buttons, badges, and pills never inherit oversized fixed line boxes
// and CJK/Latin glyphs stay vertically centered on both Android and Desktop (Skia).
fun buildPhonAcqTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 1.15.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 1.16.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 1.18.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 1.2.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 1.2.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 1.22.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 1.22.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.15.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.1.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.25.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 1.2.em,
        letterSpacing = 0.1.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 1.2.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 1.2.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    )
)

val Typography = buildPhonAcqTypography(FontFamily.Default)
