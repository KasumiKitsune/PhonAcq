package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  themePaletteId: String = PhonAcqThemePalette.STUDIO_BLUE.id,
  content: @Composable () -> Unit,
) {
  val palette = PhonAcqThemePalette.fromId(themePaletteId)
  val rawColorScheme =
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val context = LocalContext.current
      val dyn = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      dyn.copy(surfaceTint = Color.Transparent)
    } else {
      buildPhonAcqColorScheme(palette = palette, darkTheme = darkTheme)
    }

  val animatedColorScheme = rawColorScheme.animated()
  val extendedColors = buildPhonAcqExtendedColors(darkTheme = darkTheme)

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

  MaterialTheme(
    colorScheme = animatedColorScheme,
    typography = AndroidPhonAcqTypography,
    shapes = PhonAcqShapes
  ) {
    CompositionLocalProvider(
      LocalPhoneticFontFamily provides AndroidPhonAcqIpaFontFamily,
      LocalPhonAcqExtendedColors provides extendedColors,
      content = content
    )
  }
}
