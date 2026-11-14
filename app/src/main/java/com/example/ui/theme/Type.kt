package com.example.ui.theme

import androidx.compose.material3.Typography
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

// Material 3 typography using proportional em line-heights and Trim.Both + Alignment.Center
// so custom fontSizes on buttons, badges, and pills never inherit oversized fixed line boxes
// and CJK/Latin glyphs stay vertically centered on both Android and Windows Desktop (Skia).
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 1.15.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 1.16.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 1.18.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 1.2.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 1.2.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 1.22.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 1.22.em,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.15.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.1.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.25.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 1.24.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 1.2.em,
        letterSpacing = 0.1.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 1.2.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 1.2.em,
        letterSpacing = 0.2.sp,
        lineHeightStyle = CenteredTrimmedLineHeightStyle
    )
)
