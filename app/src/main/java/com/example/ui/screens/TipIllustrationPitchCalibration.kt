package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.min

private object PitchCalibrationPaths {
    val avatarShoulders by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M72 75.5 C72 70, 76.2 67.8, 82 67.8 C87.8 67.8, 92 70, 92 75.5")
    }
    val avatarBadgeWaves by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M94.2 79 V79.8 M97 76.8 V81.2 M99.8 78.2 V80")
    }
    val tuningFork by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M49 160 V166.5 C49 169.5, 55 169.5, 55 166.5 V160 M52 169.5 V176.5")
    }
    val rulerTicks by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M59.5 161 H63.5 M59.5 166 H62 M59.5 171 H63.5 M59.5 176 H62")
    }

    val scale5 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M179.5 53 H175.5 V55.6 H178 C179.1 55.6, 179.8 56.2, 179.8 57.3 C179.8 58.4, 179 59, 177.6 59 H175.5")
    }
    val scale4 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M178.8 87 V81 L175 85.2 H180")
    }
    val scale3 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M175.5 109.2 H179.5 L177.3 111.7 C178.8 111.7, 179.8 112.4, 179.8 113.5 C179.8 114.5, 178.9 115, 177.5 115 H175.5")
    }
    val scale2 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M175.5 138.4 C175.5 137.5, 176.4 137, 177.6 137 C178.8 137, 179.6 137.6, 179.6 138.6 C179.6 139.5, 178.8 140.2, 175.5 143 H179.8")
    }
    val scale1 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M176.2 166.2 L177.8 165 V171 M176.2 171 H179.4")
    }

    val toneCurve214 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 206 140 C 224 140, 236 168, 256 168 C 284 168, 308 84, 336 84")
    }
    val toneCurveArea by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 206 140 C 224 140, 236 168, 256 168 C 284 168, 308 84, 336 84 L 336 176 L 206 176 Z")
    }

    val badgeBracketL by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M297 42 H294 V54 H297") }
    val badgeDigit2 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M304.5 45 C304.5 43.1, 306.1 42.3, 308.1 42.3 C310.1 42.3, 311.5 43.4, 311.5 45.1 C311.5 46.7, 310.2 47.8, 304.5 53.3 H311.8")
    }
    val badgeDigit1 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M319.5 44.6 L322.2 42.3 V53.3 M319.5 53.3 H324.8")
    }
    val badgeDigit4 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M337.8 53.3 V42.3 L332.2 50.1 H339.8")
    }
    val badgeBracketR by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M347 42 H350 V54 H347") }
    val badgeCheck by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M352.6 38.1 L354.3 39.8 L357.5 36.4") }
}

/**
 * 贴士 08｜测算音高让标调更准 (5.0s Seamless Loop)
 */
@Composable
internal fun TipIllustrationPitchCalibration(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val p = remember(colorScheme, isDark) { TipDynamicPalette(colorScheme, isDark) }
    val paths = PitchCalibrationPaths

    val coordViewportClip = remember {
        Path().apply { addRoundRect(RoundRect(Rect(168f, 34f, 364f, 191f), CornerRadius(16f, 16f))) }
    }

    val dash3 = remember { PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f) }
    val dash4 = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) }
    val cScanSweep = TipBezierCurve(0.32f, 0f, 0.22f, 1f)

    Canvas(modifier = modifier) {
        val contentW = 388f
        val contentH = 214f
        val scale = min(size.width / contentW, size.height / contentH)
        val offsetX = size.width / 2f - 199f * scale
        val offsetY = size.height / 2f - 111.5f * scale

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // 1. M3 Geometric Decor

                val bg1Tx = evalTipKf(t, TipKf(0f, 0f, TipEaseSmooth), TipKf(50f, 4f, TipEaseSmooth), TipKf(100f, 0f))
                val bg1Ty = evalTipKf(t, TipKf(0f, 0f, TipEaseSmooth), TipKf(50f, 3f, TipEaseSmooth), TipKf(100f, 0f))
                val bg1S = evalTipKf(t, TipKf(0f, 1f, TipEaseSmooth), TipKf(50f, 1.04f, TipEaseSmooth), TipKf(100f, 1f))
                withTipPivot(60f, 25f, tx = bg1Tx, ty = bg1Ty, sx = bg1S) {
                    drawCircle(color = p.decoLight, radius = 92f, center = Offset(60f, 25f))
                }

                val bg2Rot = evalTipKf(t, TipKf(0f, -16f, TipEaseSmooth), TipKf(50f, -13f, TipEaseSmooth), TipKf(100f, -16f))
                val bg2S = evalTipKf(t, TipKf(0f, 1f, TipEaseSmooth), TipKf(50f, 1.05f, TipEaseSmooth), TipKf(100f, 1f))
                withTipPivot(350f, 190f, rotDeg = bg2Rot, sx = bg2S) {
                    drawRoundRect(color = p.decoMid.copy(alpha = 0.65f), topLeft = Offset(260f, 110f), size = Size(180f, 145f), cornerRadius = CornerRadius(72.5f, 72.5f))
                }

                drawRoundRect(color = p.decoLight.copy(alpha = 0.4f), topLeft = Offset(16f, 14f), size = Size(368f, 197f), cornerRadius = CornerRadius(32f, 32f))

                // 2. Signal Bridge between Left & Right Panels
                drawRoundRect(color = p.decoMid, topLeft = Offset(136f, 163f), size = Size(24f, 10f), cornerRadius = CornerRadius(5f, 5f))
                drawLine(color = p.greenStrong.copy(alpha = 0.7f), start = Offset(140f, 168f), end = Offset(156f, 168f), strokeWidth = 2f, cap = StrokeCap.Round, pathEffect = dash3)

                // 3. Left Speaker Pitch Card (x: 24..140)
                drawRoundRect(color = p.shadowColor.copy(alpha = 0.45f), topLeft = Offset(24f, 26f), size = Size(116f, 181f), cornerRadius = CornerRadius(22f, 22f))
                drawRoundRect(color = p.cardWhite, topLeft = Offset(24f, 22f), size = Size(116f, 181f), cornerRadius = CornerRadius(22f, 22f))
                drawRoundRect(color = p.decoMid, topLeft = Offset(24f, 22f), size = Size(116f, 181f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 1.5f))

                // 3.1 Top Half: Avatar & Voice Wave Container
                drawRoundRect(color = p.canvasBg, topLeft = Offset(34f, 32f), size = Size(96f, 106f), cornerRadius = CornerRadius(16f, 16f))
                drawRoundRect(color = p.decoLight, topLeft = Offset(42f, 38f), size = Size(28f, 8f), cornerRadius = CornerRadius(4f, 4f))
                drawCircle(color = p.brightBlue, radius = 2f, center = Offset(46.5f, 42f))
                drawRoundRect(color = p.slateNavy.copy(alpha = 0.6f), topLeft = Offset(51.5f, 40.8f), size = Size(14f, 2.4f), cornerRadius = CornerRadius(1.2f, 1.2f))
                drawRoundRect(color = p.decoLight, topLeft = Offset(104f, 38f), size = Size(18f, 8f), cornerRadius = CornerRadius(4f, 4f))
                drawCircle(color = p.greenStrong.copy(alpha = 0.75f), radius = 2f, center = Offset(113f, 42f))

                // Avatar Ring Pulse (82, 66)
                val arAlpha = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(2f, 0f),
                    TipKf(6f, 0.65f, TipEaseOutExpo), TipKf(16f, 0f),
                    TipKf(17f, 0f), TipKf(20f, 0.6f, TipEaseOutExpo),
                    TipKf(30f, 0f), TipKf(100f, 0f)
                )
                if (arAlpha > 0.01f) {
                    val arScale = evalTipKf(
                        t,
                        TipKf(0f, 0.88f), TipKf(2f, 0.88f),
                        TipKf(6f, 0.95f, TipEaseOutExpo), TipKf(16f, 1.42f),
                        TipKf(17f, 0.88f), TipKf(20f, 0.95f, TipEaseOutExpo),
                        TipKf(30f, 1.42f), TipKf(100f, 0.88f)
                    )
                    withTipPivot(82f, 66f, sx = arScale) {
                        drawCircle(color = p.skyBlue.copy(alpha = arAlpha), radius = 21f, center = Offset(82f, 66f), style = Stroke(width = 2f))
                    }
                }

                // Avatar Core (82, 66)
                val acScale = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(2f, 1f),
                    TipKf(7f, 1.06f, TipEaseOutBack),
                    TipKf(14f, 0.97f),
                    TipKf(21f, 1.06f, TipEaseOutBack),
                    TipKf(30f, 1f), TipKf(100f, 1f)
                )
                withTipPivot(82f, 66f, sx = acScale) {
                    drawCircle(color = p.blueContainer, radius = 20f, center = Offset(82f, 66f))
                    drawCircle(color = p.brightBlue, radius = 20f, center = Offset(82f, 66f), style = Stroke(width = 1.5f))
                    drawCircle(color = p.deepNavy, radius = 5.5f, center = Offset(82f, 60.5f))
                    drawPath(paths.avatarShoulders, color = p.deepNavy, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
                    drawCircle(color = p.brightBlue, radius = 7f, center = Offset(97f, 79f))
                    drawCircle(color = p.cardWhite, radius = 7f, center = Offset(97f, 79f), style = Stroke(width = 1.5f))
                    drawPath(paths.avatarBadgeWaves, color = Color.White, style = Stroke(width = 1.35f, cap = StrokeCap.Round))
                }

                // 7 Voice Wave Bars (centered at y=115)
                drawLine(color = p.decoMid, start = Offset(44f, 115f), end = Offset(120f, 115f), strokeWidth = 1f, cap = StrokeCap.Round)
                val vw1 = evalTipKf(t, TipKf(0f, 1f), TipKf(5f, 1.85f), TipKf(11f, 0.65f), TipKf(17f, 2.1f), TipKf(24f, 0.8f), TipKf(31f, 1f), TipKf(100f, 1f))
                val vw2 = evalTipKf(t, TipKf(0f, 1f), TipKf(6f, 0.6f), TipKf(12f, 1.65f), TipKf(18f, 0.75f), TipKf(25f, 1.5f), TipKf(31f, 1f), TipKf(100f, 1f))
                val vw3 = evalTipKf(t, TipKf(0f, 1f), TipKf(4.5f, 1.5f), TipKf(10f, 0.55f), TipKf(16f, 1.6f), TipKf(22f, 0.7f), TipKf(27f, 1.3f), TipKf(31f, 1f), TipKf(100f, 1f))
                val vw4 = evalTipKf(t, TipKf(0f, 1f), TipKf(5.5f, 0.55f), TipKf(11.5f, 1.4f), TipKf(17.5f, 0.65f), TipKf(23.5f, 1.35f), TipKf(31f, 1f), TipKf(100f, 1f))
                val vw5 = evalTipKf(t, TipKf(0f, 1f), TipKf(7f, 1.65f), TipKf(13f, 0.6f), TipKf(19f, 1.5f), TipKf(25f, 0.75f), TipKf(31f, 1f), TipKf(100f, 1f))
                val vw6 = evalTipKf(t, TipKf(0f, 1f), TipKf(5f, 0.65f), TipKf(11f, 1.75f), TipKf(17f, 0.7f), TipKf(23f, 1.6f), TipKf(31f, 1f), TipKf(100f, 1f))
                val vw7 = evalTipKf(t, TipKf(0f, 1f), TipKf(6.5f, 2.0f), TipKf(12.5f, 0.7f), TipKf(18.5f, 2.2f), TipKf(24.5f, 1.2f), TipKf(31f, 1f), TipKf(100f, 1f))

                withTipPivot(52f, 115f, sx = 1f, sy = vw1) { drawRoundRect(color = p.slateNavy, topLeft = Offset(50f, 109f), size = Size(4f, 12f), cornerRadius = CornerRadius(2f, 2f)) }
                withTipPivot(62f, 115f, sx = 1f, sy = vw2) { drawRoundRect(color = p.brightBlue, topLeft = Offset(60f, 105f), size = Size(4f, 20f), cornerRadius = CornerRadius(2f, 2f)) }
                withTipPivot(72f, 115f, sx = 1f, sy = vw3) { drawRoundRect(color = p.skyBlue, topLeft = Offset(70f, 102f), size = Size(4f, 26f), cornerRadius = CornerRadius(2f, 2f)) }
                withTipPivot(82f, 115f, sx = 1f, sy = vw4) { drawRoundRect(color = p.brightBlue, topLeft = Offset(80f, 100f), size = Size(4f, 30f), cornerRadius = CornerRadius(2f, 2f)) }
                withTipPivot(92f, 115f, sx = 1f, sy = vw5) { drawRoundRect(color = p.greenStrong, topLeft = Offset(90f, 103f), size = Size(4f, 24f), cornerRadius = CornerRadius(2f, 2f)) }
                withTipPivot(102f, 115f, sx = 1f, sy = vw6) { drawRoundRect(color = p.goldStrong, topLeft = Offset(100f, 106f), size = Size(4f, 18f), cornerRadius = CornerRadius(2f, 2f)) }
                withTipPivot(112f, 115f, sx = 1f, sy = vw7) { drawRoundRect(color = p.goldSoft, topLeft = Offset(110f, 109f), size = Size(4f, 12f), cornerRadius = CornerRadius(2f, 2f)) }

                // 3.2 Bottom Half: Green Auto-Calculate Button & Sonar Waves (82, 168)
                val sn1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(20f, 0f), TipKf(22.5f, 0.85f, TipEaseOutExpo), TipKf(39f, 0f), TipKf(100f, 0f))
                if (sn1Alpha > 0.01f) {
                    val sn1Sx = evalTipKf(t, TipKf(0f, 0.88f), TipKf(20f, 0.88f), TipKf(22.5f, 0.96f, TipEaseOutExpo), TipKf(39f, 1.42f), TipKf(100f, 0.88f))
                    val sn1Sy = evalTipKf(t, TipKf(0f, 0.88f), TipKf(20f, 0.88f), TipKf(22.5f, 0.96f, TipEaseOutExpo), TipKf(39f, 1.68f), TipKf(100f, 0.88f))
                    withTipPivot(82f, 168f, sx = sn1Sx, sy = sn1Sy) {
                        drawRoundRect(color = p.greenStrong.copy(alpha = sn1Alpha), topLeft = Offset(34f, 147f), size = Size(96f, 42f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2.2f))
                    }
                }
                val sn2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(24f, 0f), TipKf(26.5f, 0.65f, TipEaseOutExpo), TipKf(43f, 0f), TipKf(100f, 0f))
                if (sn2Alpha > 0.01f) {
                    val sn2Sx = evalTipKf(t, TipKf(0f, 0.88f), TipKf(24f, 0.88f), TipKf(26.5f, 0.96f, TipEaseOutExpo), TipKf(43f, 1.56f), TipKf(100f, 0.88f))
                    val sn2Sy = evalTipKf(t, TipKf(0f, 0.88f), TipKf(24f, 0.88f), TipKf(26.5f, 0.96f, TipEaseOutExpo), TipKf(43f, 1.88f), TipKf(100f, 0.88f))
                    withTipPivot(82f, 168f, sx = sn2Sx, sy = sn2Sy) {
                        drawRoundRect(color = p.greenMid.copy(alpha = sn2Alpha), topLeft = Offset(34f, 147f), size = Size(96f, 42f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.6f))
                    }
                }

                val btnScale = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(15f, 1f, TipEaseStandard),
                    TipKf(20f, 0.86f, TipEaseOutBack),
                    TipKf(26.5f, 1.16f, TipEaseOutQuart),
                    TipKf(31f, 0.97f, TipEaseOutBack),
                    TipKf(35f, 1f), TipKf(100f, 1f)
                )
                withTipPivot(82f, 168f, sx = btnScale) {
                    drawRoundRect(color = p.greenContainer, topLeft = Offset(34f, 150f), size = Size(96f, 42f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.greenStrong, topLeft = Offset(34f, 147f), size = Size(96f, 42f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.greenMid.copy(alpha = 0.45f), topLeft = Offset(41f, 154f), size = Size(28f, 28f), cornerRadius = CornerRadius(9f, 9f))
                    drawPath(paths.tuningFork, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(paths.rulerTicks, color = p.greenContainer, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
                    drawRoundRect(color = Color.White, topLeft = Offset(75f, 161f), size = Size(42f, 5.5f), cornerRadius = CornerRadius(2.75f, 2.75f))
                    drawRoundRect(color = p.greenContainer, topLeft = Offset(75f, 170.5f), size = Size(28f, 4f), cornerRadius = CornerRadius(2f, 2f))
                }

                // 4. Right Five-Level Pitch Panel (x: 156..376)
                drawRoundRect(color = p.shadowColor.copy(alpha = 0.45f), topLeft = Offset(156f, 26f), size = Size(220f, 181f), cornerRadius = CornerRadius(22f, 22f))
                drawRoundRect(color = p.cardWhite, topLeft = Offset(156f, 22f), size = Size(220f, 181f), cornerRadius = CornerRadius(22f, 22f))
                drawRoundRect(color = p.decoMid, topLeft = Offset(156f, 22f), size = Size(220f, 181f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 1.5f))
                drawRoundRect(color = p.canvasBg, topLeft = Offset(168f, 34f), size = Size(196f, 157f), cornerRadius = CornerRadius(16f, 16f))

                val calGlowAlpha = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(40f, 0f, TipEaseOutQuart),
                    TipKf(46f, 1f), TipKf(77f, 1f, TipEaseStandard),
                    TipKf(88f, 0f), TipKf(100f, 0f)
                )

                // 4.1 Clipped Coordinate Viewport
                clipPath(coordViewportClip) {
                    drawLine(color = p.decoStroke, start = Offset(192f, 56f), end = Offset(278f, 56f), strokeWidth = 1.2f, cap = StrokeCap.Round, pathEffect = dash4)
                    drawLine(color = p.decoStroke, start = Offset(192f, 84f), end = Offset(354f, 84f), strokeWidth = 1.2f, cap = StrokeCap.Round, pathEffect = dash4)
                    drawLine(color = p.decoStroke, start = Offset(192f, 112f), end = Offset(354f, 112f), strokeWidth = 1.2f, cap = StrokeCap.Round, pathEffect = dash4)
                    drawLine(color = p.decoStroke, start = Offset(192f, 140f), end = Offset(354f, 140f), strokeWidth = 1.2f, cap = StrokeCap.Round, pathEffect = dash4)
                    drawLine(color = p.decoStroke, start = Offset(192f, 168f), end = Offset(354f, 168f), strokeWidth = 1.2f, cap = StrokeCap.Round, pathEffect = dash4)

                    if (calGlowAlpha > 0.01f) {
                        drawLine(color = p.skyBlue.copy(alpha = calGlowAlpha), start = Offset(192f, 84f), end = Offset(336f, 84f), strokeWidth = 1.5f, cap = StrokeCap.Round, pathEffect = dash3)
                        drawLine(color = p.skyBlue.copy(alpha = calGlowAlpha), start = Offset(192f, 140f), end = Offset(206f, 140f), strokeWidth = 1.5f, cap = StrokeCap.Round, pathEffect = dash3)
                        drawLine(color = p.skyBlue.copy(alpha = calGlowAlpha), start = Offset(192f, 168f), end = Offset(256f, 168f), strokeWidth = 1.5f, cap = StrokeCap.Round, pathEffect = dash3)
                    }

                    // Pitch Bounds Lock (266, 112)
                    val pbSy = evalTipKf(
                        t,
                        TipKf(0f, 1.18f), TipKf(35f, 1.18f, TipEaseOutBack),
                        TipKf(43.5f, 0.94f, TipEaseOutBack),
                        TipKf(48.5f, 1f), TipKf(78f, 1f, TipEaseStandard),
                        TipKf(89f, 1.18f), TipKf(100f, 1.18f)
                    )
                    withTipPivot(266f, 112f, sx = 1f, sy = pbSy) {
                        drawLine(color = p.goldStrong.copy(alpha = 0.85f), start = Offset(192f, 56f), end = Offset(276f, 56f), strokeWidth = 2f, cap = StrokeCap.Round)
                        drawRoundRect(color = p.goldStrong, topLeft = Offset(258f, 56f), size = Size(18f, 4.5f), cornerRadius = CornerRadius(2.25f, 2.25f))
                        drawLine(color = p.goldStrong.copy(alpha = 0.85f), start = Offset(192f, 168f), end = Offset(354f, 168f), strokeWidth = 2f, cap = StrokeCap.Round)
                        drawRoundRect(color = p.goldStrong, topLeft = Offset(336f, 163.5f), size = Size(18f, 4.5f), cornerRadius = CornerRadius(2.25f, 2.25f))

                        if (calGlowAlpha > 0.01f) {
                            drawLine(color = p.greenStrong.copy(alpha = calGlowAlpha), start = Offset(192f, 56f), end = Offset(276f, 56f), strokeWidth = 2f, cap = StrokeCap.Round)
                            drawRoundRect(color = p.greenStrong.copy(alpha = calGlowAlpha), topLeft = Offset(256f, 56f), size = Size(20f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))
                            drawLine(color = p.greenStrong.copy(alpha = calGlowAlpha), start = Offset(192f, 168f), end = Offset(354f, 168f), strokeWidth = 2f, cap = StrokeCap.Round)
                            drawRoundRect(color = p.greenStrong.copy(alpha = calGlowAlpha), topLeft = Offset(334f, 163f), size = Size(20f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))
                        }
                    }

                    // Horizontal Scan Beam Sweep (33%..48%)
                    val sbAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(32.9f, 0f), TipKf(33f, 1f), TipKf(48f, 1f), TipKf(48.1f, 0f), TipKf(100f, 0f))
                    if (sbAlpha > 0.01f) {
                        val sbTy = evalTipKf(t, TipKf(0f, 0f), TipKf(33f, 0f, cScanSweep), TipKf(48f, -186f), TipKf(100f, 0f))
                        withTransform({ translate(0f, sbTy) }) {
                            val scanBrush = Brush.verticalGradient(
                                0f to p.greenStrong.copy(alpha = 0.55f * sbAlpha),
                                0.35f to p.greenMid.copy(alpha = 0.25f * sbAlpha),
                                1f to p.greenContainer.copy(alpha = 0f),
                                startY = 196f,
                                endY = 228f
                            )
                            drawRect(brush = scanBrush, topLeft = Offset(168f, 196f), size = Size(196f, 32f))
                            drawLine(color = p.greenStrong.copy(alpha = sbAlpha), start = Offset(168f, 196f), end = Offset(364f, 196f), strokeWidth = 2.5f)
                            drawLine(color = p.greenContainer.copy(alpha = sbAlpha), start = Offset(168f, 196f), end = Offset(364f, 196f), strokeWidth = 1f)
                        }
                    }
                }

                // 4.2 Left Scale Rail (184, 112)
                val srSy = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(35f, 1f),
                    TipKf(40f, 1.06f, TipEaseOutBack),
                    TipKf(45f, 0.97f, TipEaseOutQuart),
                    TipKf(50f, 1f), TipKf(100f, 1f)
                )
                withTipPivot(184f, 112f, sx = 1f, sy = srSy) {
                    drawLine(color = p.shadowColor, start = Offset(188f, 48f), end = Offset(188f, 176f), strokeWidth = 2f, cap = StrokeCap.Round)

                    val numStroke = Stroke(width = 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(paths.scale5, color = p.slateNavy, style = numStroke)
                    drawPath(paths.scale4, color = p.slateNavy, style = numStroke)
                    drawPath(paths.scale3, color = p.slateNavy, style = numStroke)
                    drawPath(paths.scale2, color = p.slateNavy, style = numStroke)
                    drawPath(paths.scale1, color = p.slateNavy, style = numStroke)

                    val ys = floatArrayOf(56f, 84f, 112f, 140f, 168f)
                    ys.forEach { y ->
                        drawCircle(color = p.cardWhite, radius = 3.5f, center = Offset(188f, y))
                        drawCircle(color = p.slateNavy, radius = 3.5f, center = Offset(188f, y), style = Stroke(width = 1.8f))
                    }

                    if (calGlowAlpha > 0.01f) {
                        drawCircle(color = p.greenStrong.copy(alpha = calGlowAlpha), radius = 4.5f, center = Offset(188f, 56f))
                        drawCircle(color = p.cardWhite.copy(alpha = calGlowAlpha), radius = 4.5f, center = Offset(188f, 56f), style = Stroke(width = 1.5f))
                        floatArrayOf(84f, 140f, 168f).forEach { y ->
                            drawCircle(color = p.brightBlue.copy(alpha = calGlowAlpha), radius = 4.5f, center = Offset(188f, y))
                            drawCircle(color = p.cardWhite.copy(alpha = calGlowAlpha), radius = 4.5f, center = Offset(188f, y), style = Stroke(width = 1.5f))
                        }
                    }
                }

                // 4.3 Smooth Blue Tone Curve 214 (270, 126)
                val tcTy = evalTipKf(
                    t,
                    TipKf(0f, -10f), TipKf(36f, -10f, TipEaseOutBack),
                    TipKf(44.5f, 2f, TipEaseOutBack),
                    TipKf(49.5f, 0f), TipKf(78f, 0f, TipEaseStandard),
                    TipKf(89f, -10f), TipKf(100f, -10f)
                )
                val tcSy = evalTipKf(
                    t,
                    TipKf(0f, 0.74f), TipKf(36f, 0.74f, TipEaseOutBack),
                    TipKf(44.5f, 1.05f, TipEaseOutBack),
                    TipKf(49.5f, 1f), TipKf(78f, 1f, TipEaseStandard),
                    TipKf(89f, 0.74f), TipKf(100f, 0.74f)
                )
                val dotUnwarpSy = 1f / tcSy.coerceAtLeast(0.2f)

                withTipPivot(270f, 126f, ty = tcTy, sx = 1f, sy = tcSy) {
                    if (calGlowAlpha > 0.01f) {
                        val areaBrush = Brush.verticalGradient(
                            0f to p.brightBlue.copy(alpha = 0.18f * calGlowAlpha),
                            1f to p.skyBlue.copy(alpha = 0f),
                            startY = 84f,
                            endY = 176f
                        )
                        drawPath(paths.toneCurveArea, brush = areaBrush)
                        drawPath(paths.toneCurve214, color = p.skyBlue.copy(alpha = 0.35f * calGlowAlpha), style = Stroke(width = 9f, cap = StrokeCap.Round))
                    }

                    drawPath(paths.toneCurve214, color = p.brightBlue, style = Stroke(width = 4f, cap = StrokeCap.Round))

                    withTipPivot(206f, 140f, sx = 1f, sy = dotUnwarpSy) { drawCircle(color = p.skyBlue, radius = 3.8f, center = Offset(206f, 140f)) }
                    withTipPivot(256f, 168f, sx = 1f, sy = dotUnwarpSy) { drawCircle(color = p.skyBlue, radius = 3.8f, center = Offset(256f, 168f)) }
                    withTipPivot(336f, 84f, sx = 1f, sy = dotUnwarpSy) { drawCircle(color = p.skyBlue, radius = 3.8f, center = Offset(336f, 84f)) }

                    // Staggered Target Nodes 2 -> 1 -> 4
                    val n2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(40f, 0f, TipEaseOutBack), TipKf(46f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(87f, 0f), TipKf(100f, 0f))
                    if (n2Alpha > 0.01f) {
                        val n2S = evalTipKf(t, TipKf(0f, 0.2f), TipKf(40f, 0.2f, TipEaseOutBack), TipKf(46f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(87f, 0.2f), TipKf(100f, 0.2f))
                        withTipPivot(206f, 140f, sx = n2S, sy = n2S * dotUnwarpSy) {
                            drawCircle(color = p.brightBlue.copy(alpha = 0.2f * n2Alpha), radius = 8f, center = Offset(206f, 140f))
                            drawCircle(color = p.cardWhite.copy(alpha = n2Alpha), radius = 5f, center = Offset(206f, 140f))
                            drawCircle(color = p.brightBlue.copy(alpha = n2Alpha), radius = 5f, center = Offset(206f, 140f), style = Stroke(width = 2.5f))
                        }
                    }

                    val n1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(42.5f, 0f, TipEaseOutBack), TipKf(48.5f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(87f, 0f), TipKf(100f, 0f))
                    if (n1Alpha > 0.01f) {
                        val n1S = evalTipKf(t, TipKf(0f, 0.2f), TipKf(42.5f, 0.2f, TipEaseOutBack), TipKf(48.5f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(87f, 0.2f), TipKf(100f, 0.2f))
                        withTipPivot(256f, 168f, sx = n1S, sy = n1S * dotUnwarpSy) {
                            drawCircle(color = p.brightBlue.copy(alpha = 0.2f * n1Alpha), radius = 8f, center = Offset(256f, 168f))
                            drawCircle(color = p.cardWhite.copy(alpha = n1Alpha), radius = 5f, center = Offset(256f, 168f))
                            drawCircle(color = p.brightBlue.copy(alpha = n1Alpha), radius = 5f, center = Offset(256f, 168f), style = Stroke(width = 2.5f))
                        }
                    }

                    val n4Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(45f, 0f, TipEaseOutBack), TipKf(51f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(87f, 0f), TipKf(100f, 0f))
                    if (n4Alpha > 0.01f) {
                        val n4S = evalTipKf(t, TipKf(0f, 0.2f), TipKf(45f, 0.2f, TipEaseOutBack), TipKf(51f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(87f, 0.2f), TipKf(100f, 0.2f))
                        withTipPivot(336f, 84f, sx = n4S, sy = n4S * dotUnwarpSy) {
                            drawCircle(color = p.brightBlue.copy(alpha = 0.22f * n4Alpha), radius = 8.5f, center = Offset(336f, 84f))
                            drawCircle(color = p.cardWhite.copy(alpha = n4Alpha), radius = 5.2f, center = Offset(336f, 84f))
                            drawCircle(color = p.brightBlue.copy(alpha = n4Alpha), radius = 5.2f, center = Offset(336f, 84f), style = Stroke(width = 2.5f))
                        }
                    }
                }

                // 4.4 Top-Right Floating [214] Badge (322, 48)
                val bScale = evalTipKf(
                    t,
                    TipKf(0f, 0.88f), TipKf(42f, 0.88f, TipEaseSym),
                    TipKf(45f, 0.72f, TipEaseOutBack),
                    TipKf(50.5f, 1.22f, TipEaseOutBack),
                    TipKf(55f, 0.96f, TipEaseOutQuart),
                    TipKf(59f, 1.05f), TipKf(77f, 1.05f, TipEaseStandard),
                    TipKf(89f, 0.88f), TipKf(100f, 0.88f)
                )
                val bRot = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(45f, 0f, TipEaseOutBack),
                    TipKf(50.5f, -2f, TipEaseOutBack),
                    TipKf(55f, 1f, TipEaseOutQuart),
                    TipKf(59f, 0f), TipKf(100f, 0f)
                )

                withTipPivot(322f, 48f, sx = bScale, rotDeg = bRot) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.55f), topLeft = Offset(284f, 36f), size = Size(76f, 28f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.deepNavy, topLeft = Offset(284f, 34f), size = Size(76f, 28f), cornerRadius = CornerRadius(14f, 14f))
                    if (calGlowAlpha > 0.01f) {
                        drawRoundRect(color = p.skyBlue.copy(alpha = calGlowAlpha), topLeft = Offset(284f, 34f), size = Size(76f, 28f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.8f))
                    }

                    drawPath(paths.badgeBracketL, color = p.skyBlue, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(paths.badgeBracketR, color = p.skyBlue, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    val d2Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(48f, 0f), TipKf(52.5f, -4.5f, TipEaseOutBack), TipKf(60f, 0f), TipKf(100f, 0f))
                    val d2S = evalTipKf(t, TipKf(0f, 1f), TipKf(48f, 1f), TipKf(52.5f, 1.15f, TipEaseOutBack), TipKf(60f, 1f), TipKf(100f, 1f))
                    withTipPivot(308f, 48f, ty = d2Ty, sx = d2S) {
                        drawPath(paths.badgeDigit2, color = Color.White, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    val d1Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(51.5f, 0f), TipKf(56f, -4.5f, TipEaseOutBack), TipKf(63.5f, 0f), TipKf(100f, 0f))
                    val d1S = evalTipKf(t, TipKf(0f, 1f), TipKf(51.5f, 1f), TipKf(56f, 1.15f, TipEaseOutBack), TipKf(63.5f, 1f), TipKf(100f, 1f))
                    withTipPivot(322f, 48f, ty = d1Ty, sx = d1S) {
                        drawPath(paths.badgeDigit1, color = Color.White, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    val d4Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(55f, 0f), TipKf(59.5f, -4.5f, TipEaseOutBack), TipKf(67f, 0f), TipKf(100f, 0f))
                    val d4S = evalTipKf(t, TipKf(0f, 1f), TipKf(55f, 1f), TipKf(59.5f, 1.15f, TipEaseOutBack), TipKf(67f, 1f), TipKf(100f, 1f))
                    withTipPivot(336f, 48f, ty = d4Ty, sx = d4S) {
                        drawPath(paths.badgeDigit4, color = p.goldSoft, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    if (calGlowAlpha > 0.01f) {
                        drawCircle(color = p.greenStrong.copy(alpha = calGlowAlpha), radius = 6f, center = Offset(355f, 38f))
                        drawCircle(color = Color.White.copy(alpha = calGlowAlpha), radius = 6f, center = Offset(355f, 38f), style = Stroke(width = 1.5f))
                        drawPath(paths.badgeCheck, color = Color.White.copy(alpha = calGlowAlpha), style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
        }
    }
}
