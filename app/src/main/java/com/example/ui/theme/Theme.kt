package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.example.R

private val AndroidPhonAcqIpaFontFamily = FontFamily(
  Font(R.font.phonacq_ipa_sans, FontWeight.Light),
  Font(R.font.phonacq_ipa_sans, FontWeight.Normal),
  Font(R.font.phonacq_ipa_sans, FontWeight.Medium),
  Font(R.font.phonacq_ipa_sans_bold, FontWeight.SemiBold),
  Font(R.font.phonacq_ipa_sans_bold, FontWeight.Bold),
  Font(R.font.phonacq_ipa_sans_bold, FontWeight.ExtraBold)
)

private val AndroidPhonAcqTypography = buildPhonAcqTypography(AndroidPhonAcqIpaFontFamily)

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

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = AndroidPhonAcqTypography) {
    CompositionLocalProvider(
      LocalPhoneticFontFamily provides AndroidPhonAcqIpaFontFamily,
      content = content
    )
  }
}
