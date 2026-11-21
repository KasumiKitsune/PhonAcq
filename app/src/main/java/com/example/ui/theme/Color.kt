package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 5 built-in studio theme palettes shared across Android and Desktop.
 * Default is [STUDIO_BLUE] (青石蓝).
 */
enum class PhonAcqThemePalette(val id: String) {
    STUDIO_BLUE("STUDIO_BLUE"),
    GRAPHITE_MONO("GRAPHITE_MONO"),
    ACADEMIC_SAGE("ACADEMIC_SAGE"),
    WARM_TERRACOTTA("WARM_TERRACOTTA"),
    DEEP_INDIGO("DEEP_INDIGO");

    fun previewSwatches(darkTheme: Boolean): Triple<Color, Color, Color> {
        val scheme = buildPhonAcqColorScheme(this, darkTheme)
        return Triple(scheme.primary, scheme.secondaryContainer, scheme.tertiary)
    }

    companion object {
        fun fromId(id: String?): PhonAcqThemePalette {
            return entries.firstOrNull { it.id == id } ?: STUDIO_BLUE
        }
    }
}

/**
 * Semantic extended colors not covered by standard Material 3 ColorScheme slots:
 * - restrained emerald success / warm amber warning
 * - hardware-inspired VU meter zones with legible ticks in both light & dark modes
 * - 8-color chart palette (high-luminance in dark mode so curves stay clear on dark surfaces)
 * - 5 file-type badge pairs for Desktop file picker
 */
@Immutable
data class PhonAcqExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val vuLow: Color,
    val vuSafe: Color,
    val vuWarn: Color,
    val vuClip: Color,
    val vuTick: Color,
    val chartPalette: List<Color>,
    val fileBadgeAudioBg: Color,
    val fileBadgeAudioFg: Color,
    val fileBadgeArchiveBg: Color,
    val fileBadgeArchiveFg: Color,
    val fileBadgeTableBg: Color,
    val fileBadgeTableFg: Color,
    val fileBadgeImageBg: Color,
    val fileBadgeImageFg: Color,
    val fileBadgeTextBg: Color,
    val fileBadgeTextFg: Color
)

private val LightExtendedColors = PhonAcqExtendedColors(
    success = Color(0xFF1E7E5A),
    successContainer = Color(0xFFE6F4EE),
    onSuccessContainer = Color(0xFF0E4D35),
    warning = Color(0xFFB4690E),
    warningContainer = Color(0xFFFEF3DC),
    onWarningContainer = Color(0xFF6E3E05),
    vuLow = Color(0xFF3B82F6),
    vuSafe = Color(0xFF1E7E5A),
    vuWarn = Color(0xFFD97706),
    vuClip = Color(0xFFC93B3B),
    vuTick = Color(0x471E222B),
    chartPalette = listOf(
        Color(0xFF2557B8), // Studio Cobalt
        Color(0xFFC2410C), // Terracotta Orange
        Color(0xFF1E7E5A), // Field Emerald
        Color(0xFF7C3AED), // Academic Violet
        Color(0xFFB45309), // Ochre Amber
        Color(0xFF0E7490), // Deep Cyan
        Color(0xFFBE185D), // Crimson Rose
        Color(0xFF4B5563)  // Slate Graphite
    ),
    fileBadgeAudioBg = Color(0xFFE4ECFA),
    fileBadgeAudioFg = Color(0xFF1E40AF),
    fileBadgeArchiveBg = Color(0xFFFEF3DC),
    fileBadgeArchiveFg = Color(0xFF92400E),
    fileBadgeTableBg = Color(0xFFE6F4EE),
    fileBadgeTableFg = Color(0xFF166534),
    fileBadgeImageBg = Color(0xFFF3E8FF),
    fileBadgeImageFg = Color(0xFF6B21A8),
    fileBadgeTextBg = Color(0xFFE9EEF6),
    fileBadgeTextFg = Color(0xFF334155)
)

private val DarkExtendedColors = PhonAcqExtendedColors(
    success = Color(0xFF4ADE9B),
    successContainer = Color(0xFF143629),
    onSuccessContainer = Color(0xFFC7F9E2),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF3B2B12),
    onWarningContainer = Color(0xFFFDE68A),
    vuLow = Color(0xFF60A5FA),
    vuSafe = Color(0xFF34D399),
    vuWarn = Color(0xFFFBBF24),
    vuClip = Color(0xFFF87171),
    vuTick = Color(0x6BFFFFFF),
    chartPalette = listOf(
        Color(0xFF7BA6FF), // Bright Cobalt (L>=68%)
        Color(0xFFFB923C), // Bright Amber-Orange
        Color(0xFF4ADE9B), // Bright Emerald
        Color(0xFFC084FC), // Bright Violet
        Color(0xFFFBBF24), // Bright Gold
        Color(0xFF38BDF8), // Bright Sky Cyan
        Color(0xFFF472B6), // Bright Rose
        Color(0xFFCBD5E1)  // Bright Silver
    ),
    fileBadgeAudioBg = Color(0xFF1D325C),
    fileBadgeAudioFg = Color(0xFFBFDBFE),
    fileBadgeArchiveBg = Color(0xFF3B2B12),
    fileBadgeArchiveFg = Color(0xFFFDE68A),
    fileBadgeTableBg = Color(0xFF143629),
    fileBadgeTableFg = Color(0xFFA7F3D0),
    fileBadgeImageBg = Color(0xFF33204A),
    fileBadgeImageFg = Color(0xFFE9D5FF),
    fileBadgeTextBg = Color(0xFF252D3D),
    fileBadgeTextFg = Color(0xFFCBD5E1)
)

fun buildPhonAcqExtendedColors(darkTheme: Boolean): PhonAcqExtendedColors {
    return if (darkTheme) DarkExtendedColors else LightExtendedColors
}

val LocalPhonAcqExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * Shared corner-radius design tokens across Android and Desktop.
 */
val PhonAcqShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

private data class PaletteAccentTokens(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val inversePrimary: Color
)

private fun accentTokensFor(palette: PhonAcqThemePalette, darkTheme: Boolean): PaletteAccentTokens {
    return when (palette) {
        PhonAcqThemePalette.STUDIO_BLUE -> if (!darkTheme) {
            PaletteAccentTokens(
                primary = Color(0xFF2557B8),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE4ECFA),
                onPrimaryContainer = Color(0xFF173673),
                secondary = Color(0xFF4B5C78),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE9EEF6),
                onSecondaryContainer = Color(0xFF263348),
                tertiary = Color(0xFFB4690E),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFBF0DD),
                onTertiaryContainer = Color(0xFF693B04),
                inversePrimary = Color(0xFF7BA6FF)
            )
        } else {
            PaletteAccentTokens(
                primary = Color(0xFF7BA6FF),
                onPrimary = Color(0xFF0C1B38),
                primaryContainer = Color(0xFF1D325C),
                onPrimaryContainer = Color(0xFFDCE8FF),
                secondary = Color(0xFF99ACC9),
                onSecondary = Color(0xFF141D2E),
                secondaryContainer = Color(0xFF252D3D),
                onSecondaryContainer = Color(0xFFD6E1F2),
                tertiary = Color(0xFFE5A94D),
                onTertiary = Color(0xFF2A1A03),
                tertiaryContainer = Color(0xFF3D2E16),
                onTertiaryContainer = Color(0xFFFCE6C2),
                inversePrimary = Color(0xFF2557B8)
            )
        }

        PhonAcqThemePalette.GRAPHITE_MONO -> if (!darkTheme) {
            PaletteAccentTokens(
                primary = Color(0xFF262B36),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE7E9EE),
                onPrimaryContainer = Color(0xFF15181F),
                secondary = Color(0xFF5A6270),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFECEEF2),
                onSecondaryContainer = Color(0xFF2B303A),
                tertiary = Color(0xFF2E6F64),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE3F1EE),
                onTertiaryContainer = Color(0xFF16423B),
                inversePrimary = Color(0xFFD8DEE9)
            )
        } else {
            PaletteAccentTokens(
                primary = Color(0xFFD8DEE9),
                onPrimary = Color(0xFF14171E),
                primaryContainer = Color(0xFF2F3542),
                onPrimaryContainer = Color(0xFFF4F6FA),
                secondary = Color(0xFFA3ACB9),
                onSecondary = Color(0xFF181C24),
                secondaryContainer = Color(0xFF262B35),
                onSecondaryContainer = Color(0xFFDCE1EA),
                tertiary = Color(0xFF5AC2AE),
                onTertiary = Color(0xFF0A2621),
                tertiaryContainer = Color(0xFF183631),
                onTertiaryContainer = Color(0xFFCEF2EA),
                inversePrimary = Color(0xFF262B36)
            )
        }

        PhonAcqThemePalette.ACADEMIC_SAGE -> if (!darkTheme) {
            PaletteAccentTokens(
                primary = Color(0xFF246B5B),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE2F0EC),
                onPrimaryContainer = Color(0xFF144238),
                secondary = Color(0xFF4E6660),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE9F0EE),
                onSecondaryContainer = Color(0xFF243833),
                tertiary = Color(0xFF9A6324),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFF7ECDC),
                onTertiaryContainer = Color(0xFF5C380E),
                inversePrimary = Color(0xFF62C4AE)
            )
        } else {
            PaletteAccentTokens(
                primary = Color(0xFF62C4AE),
                onPrimary = Color(0xFF09241E),
                primaryContainer = Color(0xFF193D35),
                onPrimaryContainer = Color(0xFFD6F3EC),
                secondary = Color(0xFF98B5AE),
                onSecondary = Color(0xFF122420),
                secondaryContainer = Color(0xFF23302D),
                onSecondaryContainer = Color(0xFFD5E6E2),
                tertiary = Color(0xFFDFA55B),
                onTertiary = Color(0xFF291905),
                tertiaryContainer = Color(0xFF3B2B17),
                onTertiaryContainer = Color(0xFFFAE4C5),
                inversePrimary = Color(0xFF246B5B)
            )
        }

        PhonAcqThemePalette.WARM_TERRACOTTA -> if (!darkTheme) {
            PaletteAccentTokens(
                primary = Color(0xFFA64B2A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFF7E6DF),
                onPrimaryContainer = Color(0xFF662A15),
                secondary = Color(0xFF6E5A50),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFEFECE6),
                onSecondaryContainer = Color(0xFF3B2E28),
                tertiary = Color(0xFF2D6A6A),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE2F0F0),
                onTertiaryContainer = Color(0xFF154040),
                inversePrimary = Color(0xFFE89272)
            )
        } else {
            PaletteAccentTokens(
                primary = Color(0xFFE89272),
                onPrimary = Color(0xFF331409),
                primaryContainer = Color(0xFF4D2719),
                onPrimaryContainer = Color(0xFFFCE3D9),
                secondary = Color(0xFFBCA89E),
                onSecondary = Color(0xFF241B17),
                secondaryContainer = Color(0xFF2E2825),
                onSecondaryContainer = Color(0xFFEAE0DA),
                tertiary = Color(0xFF64BABA),
                onTertiary = Color(0xFF0A2626),
                tertiaryContainer = Color(0xFF1A3838),
                onTertiaryContainer = Color(0xFFD2F0F0),
                inversePrimary = Color(0xFFA64B2A)
            )
        }

        PhonAcqThemePalette.DEEP_INDIGO -> if (!darkTheme) {
            PaletteAccentTokens(
                primary = Color(0xFF4F46C5),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFEAE9FC),
                onPrimaryContainer = Color(0xFF2D2680),
                secondary = Color(0xFF5B587A),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFEDECF6),
                onSecondaryContainer = Color(0xFF2E2B47),
                tertiary = Color(0xFF0F766E),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE2F2F0),
                onTertiaryContainer = Color(0xFF0B4742),
                inversePrimary = Color(0xFF9B96FF)
            )
        } else {
            PaletteAccentTokens(
                primary = Color(0xFF9B96FF),
                onPrimary = Color(0xFF171252),
                primaryContainer = Color(0xFF2A2761),
                onPrimaryContainer = Color(0xFFE6E5FF),
                secondary = Color(0xFFA7A4C4),
                onSecondary = Color(0xFF1B1930),
                secondaryContainer = Color(0xFF282738),
                onSecondaryContainer = Color(0xFFDFDDF2),
                tertiary = Color(0xFF4FC3B5),
                onTertiary = Color(0xFF072622),
                tertiaryContainer = Color(0xFF173834),
                onTertiaryContainer = Color(0xFFCFF2EE),
                inversePrimary = Color(0xFF4F46C5)
            )
        }
    }
}

/**
 * Single Source of Truth Material 3 [ColorScheme] factory for both Android and Desktop.
 * Populates 100% of M3 semantic slots (including tertiary, error, inverse, scrim) and forces
 * `surfaceTint = Color.Transparent` so tonal elevation never casts blue/purple haze over modals.
 */
fun buildPhonAcqColorScheme(
    palette: PhonAcqThemePalette = PhonAcqThemePalette.STUDIO_BLUE,
    darkTheme: Boolean
): ColorScheme {
    val accents = accentTokensFor(palette, darkTheme)
    val isWarmCanvas = palette == PhonAcqThemePalette.WARM_TERRACOTTA

    return if (!darkTheme) {
        val bg = if (isWarmCanvas) Color(0xFFF6F4EF) else Color(0xFFF4F5F7)
        val surface = if (isWarmCanvas) Color(0xFFFFFDF9) else Color(0xFFFFFFFF)
        val containerLowest = Color(0xFFFFFFFF)
        val containerLow = if (isWarmCanvas) Color(0xFFFBF9F5) else Color(0xFFFAFBFC)
        val container = if (isWarmCanvas) Color(0xFFF2EFE9) else Color(0xFFF1F3F6)
        val containerHigh = if (isWarmCanvas) Color(0xFFE8E3DB) else Color(0xFFE6E9EF)
        val containerHighest = if (isWarmCanvas) Color(0xFFDDD6CC) else Color(0xFFDAE0E8)
        val onSurface = if (isWarmCanvas) Color(0xFF1C1714) else Color(0xFF111827)
        val onSurfaceVariant = if (isWarmCanvas) Color(0xFF5E544C) else Color(0xFF525B6B)
        val outline = if (isWarmCanvas) Color(0xFF998D82) else Color(0xFF8C96A6)
        val outlineVariant = if (isWarmCanvas) Color(0xFFE4DDD4) else Color(0xFFDFE3EA)

        lightColorScheme(
            primary = accents.primary,
            onPrimary = accents.onPrimary,
            primaryContainer = accents.primaryContainer,
            onPrimaryContainer = accents.onPrimaryContainer,
            inversePrimary = accents.inversePrimary,
            secondary = accents.secondary,
            onSecondary = accents.onSecondary,
            secondaryContainer = accents.secondaryContainer,
            onSecondaryContainer = accents.onSecondaryContainer,
            tertiary = accents.tertiary,
            onTertiary = accents.onTertiary,
            tertiaryContainer = accents.tertiaryContainer,
            onTertiaryContainer = accents.onTertiaryContainer,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = containerHigh,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFF1E222B),
            inverseOnSurface = Color(0xFFF4F5F7),
            error = Color(0xFFC93B3B),
            onError = Color.White,
            errorContainer = Color(0xFFFCEAEA),
            onErrorContainer = Color(0xFF7A1C1C),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color(0xFF000000),
            surfaceBright = surface,
            surfaceDim = containerHigh,
            surfaceContainerLowest = containerLowest,
            surfaceContainerLow = containerLow,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest
        )
    } else {
        val bg = if (isWarmCanvas) Color(0xFF151311) else Color(0xFF111317)
        val surface = if (isWarmCanvas) Color(0xFF1C1916) else Color(0xFF171A20)
        val containerLowest = if (isWarmCanvas) Color(0xFF100E0C) else Color(0xFF0D0F12)
        val containerLow = if (isWarmCanvas) Color(0xFF231F1B) else Color(0xFF1D2129)
        val container = if (isWarmCanvas) Color(0xFF2A2520) else Color(0xFF232832)
        val containerHigh = if (isWarmCanvas) Color(0xFF332D27) else Color(0xFF2B313D)
        val containerHighest = if (isWarmCanvas) Color(0xFF3E3730) else Color(0xFF353C4B)
        val onSurface = if (isWarmCanvas) Color(0xFFF4EFEA) else Color(0xFFF1F3F7)
        val onSurfaceVariant = if (isWarmCanvas) Color(0xFFB2A59A) else Color(0xFF9BA4B5)
        val outline = if (isWarmCanvas) Color(0xFF6E6258) else Color(0xFF5C6578)
        val outlineVariant = if (isWarmCanvas) Color(0xFF38312B) else Color(0xFF2E3442)

        darkColorScheme(
            primary = accents.primary,
            onPrimary = accents.onPrimary,
            primaryContainer = accents.primaryContainer,
            onPrimaryContainer = accents.onPrimaryContainer,
            inversePrimary = accents.inversePrimary,
            secondary = accents.secondary,
            onSecondary = accents.onSecondary,
            secondaryContainer = accents.secondaryContainer,
            onSecondaryContainer = accents.onSecondaryContainer,
            tertiary = accents.tertiary,
            onTertiary = accents.onTertiary,
            tertiaryContainer = accents.tertiaryContainer,
            onTertiaryContainer = accents.onTertiaryContainer,
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = containerHigh,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = Color.Transparent,
            inverseSurface = Color(0xFFE8ECF2),
            inverseOnSurface = Color(0xFF14171D),
            error = Color(0xFFF87171),
            onError = Color(0xFF2B0B0E),
            errorContainer = Color(0xFF4A1D21),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color(0xFF000000),
            surfaceBright = containerHighest,
            surfaceDim = bg,
            surfaceContainerLowest = containerLowest,
            surfaceContainerLow = containerLow,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest
        )
    }
}

// Backward-compatible aliases mapped to the new STUDIO_BLUE default tokens
val PrimaryBlue = Color(0xFF2557B8)
val PrimaryContainerBlue = Color(0xFFE4ECFA)
val OnPrimaryContainerBlue = Color(0xFF173673)

val AccentTeal = Color(0xFF4B5C78)
val AccentContainerTeal = Color(0xFFE9EEF6)
val OnAccentContainerTeal = Color(0xFF263348)

val BGNeutral = Color(0xFFF4F5F7)
val SurfaceNeutral = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFFAFBFC)
val SurfaceContainerLight = Color(0xFFF1F3F6)
val SurfaceContainerHighLight = Color(0xFFE6E9EF)
val SurfaceContainerHighestLight = Color(0xFFDAE0E8)
val TextDark = Color(0xFF111827)
val TextMuted = Color(0xFF525B6B)
val BorderMuted = Color(0xFF8C96A6)
val BorderLight = Color(0xFFDFE3EA)

val PrimaryBlueDark = Color(0xFF7BA6FF)
val PrimaryContainerDark = Color(0xFF1D325C)
val OnPrimaryContainerDark = Color(0xFFDCE8FF)

val AccentTealDark = Color(0xFF99ACC9)
val AccentContainerTealDark = Color(0xFF252D3D)
val OnAccentContainerTealDark = Color(0xFFD6E1F2)

val BGDark = Color(0xFF111317)
val SurfaceDark = Color(0xFF171A20)
val SurfaceContainerLowDark = Color(0xFF1D2129)
val SurfaceContainerDark = Color(0xFF232832)
val SurfaceContainerHighDark = Color(0xFF2B313D)
val SurfaceContainerHighestDark = Color(0xFF353C4B)
val TextLight = Color(0xFFF1F3F7)
val TextMutedDark = Color(0xFF9BA4B5)
val BorderDark = Color(0xFF5C6578)
val BorderVariantDark = Color(0xFF2E3442)
