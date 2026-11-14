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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.min

private object ShuffleAndFocusPaths {
    val shuffleIcon by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath(
            "M 50.5 31.5 H 53.5 C 56.8 31.5 58.8 40.5 62.2 40.5 H 64.8 " +
                "M 62.8 38.2 L 65.3 40.5 L 62.8 42.8 " +
                "M 50.5 40.5 H 53.5 C 56.8 40.5 58.8 31.5 62.2 31.5 H 64.8 " +
                "M 62.8 29.2 L 65.3 31.5 L 62.8 33.8"
        )
    }
    val eyeShape by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 245.5 36 C 248.2 31.2 259.8 31.2 262.5 36 C 259.8 40.8 248.2 40.8 245.5 36 Z")
    }
    val eyeSlash by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 247.5 29.5 L 260.5 42.5")
    }
    val num1 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 60.5 100.2 L 62.5 98.8 V 107.2 M 60.2 107.2 H 64.8")
    }
    val hanziShan by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 84 116 V 146 M 69 127 V 143 C 69 145.5 70.5 147 73 147 H 95 C 97.5 147 99 145.5 99 143 V 127")
    }
    val num3 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 291.5 99.2 H 296.5 L 293.8 102.5 C 295.8 102.5 297 103.5 297 105 C 297 106.5 295.6 107.4 293.8 107.4 C 292.3 107.4 291.3 106.8 290.8 105.8")
    }
    val hanziYun by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath(
            "M 304 119 H 328 M 298 130 H 334 " +
                "M 313 130 L 303 144.5 C 301.8 146.2 303 147.5 305.2 147.2 L 326.5 144.5 " +
                "M 321.5 138.5 L 329.5 148"
        )
    }
    val num2 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 168.2 93.2 C 168.2 91.6 169.5 90.8 171.2 90.8 C 172.9 90.8 174 91.8 174 93.2 C 174 94.5 173 95.5 168.2 99.5 H 174.3")
    }
    val aaGlyph by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 225.5 98.5 V 94.8 C 225.5 93.5 222.2 93.5 222.2 96.2 C 222.2 98.8 225.5 98.8 225.5 97.5 M 228.5 98.8 L 231.5 91.5 L 234.5 98.8 M 229.3 96.8 H 233.7")
    }
    val focusCorners by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 161 119 V 112 H 168 M 239 119 V 112 H 232 M 161 165 V 172 H 168 M 239 165 V 172 H 232")
    }
    val hanziFang by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath(
            "M 198.5 109.5 L 201.2 115 " +
                "M 178 119 H 222 " +
                "M 198.5 128 H 212.5 C 215.2 128 216.5 129.5 215.6 132.8 L 211.5 145.5 C 210.2 149 207.2 150 203.5 148.2 L 200 146.2 " +
                "M 199 119 C 198 131.5 191.8 142.2 179.5 149"
        )
    }
    val focusCheck by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 241.5 188 L 244.5 191 L 250.8 184.8")
    }
}

/**
 * 贴士 07｜打乱顺序防“唱书调” & 大字模式隐藏备注 (5.4s Seamless Loop)
 */
@Composable
internal fun TipIllustrationShuffleAndFocus(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val p = remember(colorScheme, isDark) { TipDynamicPalette(colorScheme, isDark) }
    val paths = ShuffleAndFocusPaths

    val mainCardClip = remember {
        Path().apply { addRoundRect(RoundRect(Rect(148f, 78f, 252f, 198f), CornerRadius(20f, 20f))) }
    }

    val cArc1 = TipBezierCurve(0.35f, 0f, 0.65f, 0.45f)
    val cArc2 = TipBezierCurve(0.28f, 0.44f, 0.22f, 1f)
    val dashEraser = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f) }

    Canvas(modifier = modifier) {
        val contentW = 388f
        val contentH = 214f
        val scale = min(size.width / contentW, size.height / contentH)
        val offsetX = size.width / 2f - 200f * scale
        val offsetY = size.height / 2f - 112.5f * scale

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // 1. M3 Geometric Decor
            drawCircle(color = p.decoLight.copy(alpha = 0.75f), radius = 78f, center = Offset(38f, 18f))

                withTipPivot(350f, 185f, rotDeg = -14f) {
                    drawRoundRect(color = p.decoMid.copy(alpha = 0.6f), topLeft = Offset(265f, 132f), size = Size(170f, 106f), cornerRadius = CornerRadius(53f, 53f))
                }
                withTipPivot(35f, 195f, rotDeg = 10f) {
                    drawRoundRect(color = p.decoLight.copy(alpha = 0.65f), topLeft = Offset(-25f, 155f), size = Size(120f, 80f), cornerRadius = CornerRadius(40f, 40f))
                }

                // Center stage tray
                drawRoundRect(color = p.decoLight.copy(alpha = 0.65f), topLeft = Offset(22f, 66f), size = Size(356f, 145f), cornerRadius = CornerRadius(28f, 28f))

                // 2. Top Control Bar
                // 2.1 Left Shuffle Pill & Button
                drawRoundRect(color = p.shadowColor.copy(alpha = 0.42f), topLeft = Offset(36f, 18.5f), size = Size(132f, 40f), cornerRadius = CornerRadius(20f, 20f))
                drawRoundRect(color = p.cardWhite, topLeft = Offset(36f, 16f), size = Size(132f, 40f), cornerRadius = CornerRadius(20f, 20f))

                // Shuffle Ripples
                val sr1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(6f, 0f, TipEaseOutExpo), TipKf(9f, 0.75f, TipEaseOutExpo), TipKf(26f, 0f), TipKf(100f, 0f))
                if (sr1Alpha > 0.01f) {
                    val sr1Scale = evalTipKf(t, TipKf(0f, 0.8f), TipKf(6f, 0.8f, TipEaseOutExpo), TipKf(9f, 1f, TipEaseOutExpo), TipKf(26f, 2.35f), TipKf(100f, 0.8f))
                    withTipPivot(58f, 36f, sx = sr1Scale) {
                        drawCircle(color = p.skyBlue.copy(alpha = sr1Alpha), radius = 16f, center = Offset(58f, 36f), style = Stroke(width = 2.5f))
                    }
                }
                val sr2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(10f, 0f, TipEaseOutExpo), TipKf(13f, 0.55f, TipEaseOutExpo), TipKf(31f, 0f), TipKf(100f, 0f))
                if (sr2Alpha > 0.01f) {
                    val sr2Scale = evalTipKf(t, TipKf(0f, 0.8f), TipKf(10f, 0.8f, TipEaseOutExpo), TipKf(13f, 1f, TipEaseOutExpo), TipKf(31f, 2.7f), TipKf(100f, 0.8f))
                    withTipPivot(58f, 36f, sx = sr2Scale) {
                        drawCircle(color = p.blueContainer.copy(alpha = sr2Alpha), radius = 16f, center = Offset(58f, 36f), style = Stroke(width = 2f))
                    }
                }

                // Shuffle Button Spin (58, 36)
                val shScale = evalTipKf(t, TipKf(0f, 1f), TipKf(3f, 1f, TipEaseOutBack), TipKf(6f, 0.84f, TipEaseOutBack), TipKf(14f, 1.16f, TipEaseOutBack), TipKf(20f, 1f), TipKf(82f, 1f, TipEaseOutQuart), TipKf(92f, 1f), TipKf(100f, 1f))
                val shRot = evalTipKf(t, TipKf(0f, 0f), TipKf(3f, 0f, TipEaseOutBack), TipKf(6f, -18f, TipEaseOutBack), TipKf(14f, 192f, TipEaseOutBack), TipKf(20f, 180f), TipKf(82f, 180f, TipEaseOutQuart), TipKf(92f, 0f), TipKf(100f, 0f))
                val shColorMix = evalTipKf(t, TipKf(0f, 0f), TipKf(5f, 0f), TipKf(10f, 1f), TipKf(42f, 1f), TipKf(48f, 0f), TipKf(100f, 0f))
                withTipPivot(58f, 36f, sx = shScale, rotDeg = shRot) {
                    drawCircle(color = lerp(p.deepNavy, p.brightBlue, shColorMix), radius = 15.5f, center = Offset(58f, 36f))
                    drawPath(paths.shuffleIcon, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                drawRoundRect(color = p.deepNavy, topLeft = Offset(82f, 26f), size = Size(52f, 5.5f), cornerRadius = CornerRadius(2.75f, 2.75f))
                drawRoundRect(color = p.blueContainer, topLeft = Offset(138f, 26f), size = Size(16f, 5.5f), cornerRadius = CornerRadius(2.75f, 2.75f))

                val dotLTx = evalTipKf(t, TipKf(0f, 0f), TipKf(12f, 0f, TipEaseOutBack), TipKf(28f, 24f), TipKf(83f, 24f, TipEaseOutBack), TipKf(94f, 0f), TipKf(100f, 0f))
                val dotRTx = evalTipKf(t, TipKf(0f, 0f), TipKf(12f, 0f, TipEaseOutBack), TipKf(28f, -24f), TipKf(83f, -24f, TipEaseOutBack), TipKf(94f, 0f), TipKf(100f, 0f))
                drawCircle(color = p.goldStrong, radius = 4f, center = Offset(86f + dotLTx, 42f))
                drawCircle(color = p.deepNavy, radius = 4f, center = Offset(98f, 42f))
                drawCircle(color = p.greenMid, radius = 4f, center = Offset(110f + dotRTx, 42f))
                drawRoundRect(color = p.decoLight, topLeft = Offset(122f, 39.5f), size = Size(28f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))

                // 2.2 Right Hide Note Toggle Pill (298, 36)
                val hnRipAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(46f, 0f, TipEaseOutExpo), TipKf(49f, 0.65f, TipEaseOutExpo), TipKf(63f, 0f), TipKf(100f, 0f))
                if (hnRipAlpha > 0.01f) {
                    val hnRipScale = evalTipKf(t, TipKf(0f, 0.85f), TipKf(46f, 0.85f, TipEaseOutExpo), TipKf(49f, 1f, TipEaseOutExpo), TipKf(63f, 1.45f), TipKf(100f, 0.85f))
                    withTipPivot(298f, 36f, sx = hnRipScale) {
                        drawRoundRect(color = p.skyBlue.copy(alpha = hnRipAlpha), topLeft = Offset(232f, 16f), size = Size(132f, 40f), cornerRadius = CornerRadius(20f, 20f), style = Stroke(width = 2.5f))
                    }
                }

                val hnScale = evalTipKf(t, TipKf(0f, 1f), TipKf(44f, 1f, TipEaseOutBack), TipKf(47f, 0.92f, TipEaseOutBack), TipKf(52f, 1.06f, TipEaseOutBack), TipKf(57f, 1f), TipKf(81f, 1f, TipEaseOutBack), TipKf(85f, 0.96f, TipEaseOutBack), TipKf(90f, 1f), TipKf(100f, 1f))
                withTipPivot(298f, 36f, sx = hnScale) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.42f), topLeft = Offset(232f, 18.5f), size = Size(132f, 40f), cornerRadius = CornerRadius(20f, 20f))
                    drawRoundRect(color = p.cardWhite, topLeft = Offset(232f, 16f), size = Size(132f, 40f), cornerRadius = CornerRadius(20f, 20f))

                    drawCircle(color = p.decoLight, radius = 13.5f, center = Offset(254f, 36f))
                    drawPath(paths.eyeShape, color = p.deepNavy, style = Stroke(width = 1.8f, join = StrokeJoin.Round))
                    drawCircle(color = p.deepNavy, radius = 2.5f, center = Offset(254f, 36f))

                    val slashAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(45f, 0f, TipEaseOutQuart), TipKf(51f, 1f), TipKf(81f, 1f, TipEaseOutQuart), TipKf(87f, 0f), TipKf(100f, 0f))
                    if (slashAlpha > 0.01f) {
                        val slashOffset = evalTipKf(t, TipKf(0f, 22f), TipKf(45f, 22f, TipEaseOutQuart), TipKf(51f, 0f), TipKf(81f, 0f, TipEaseOutQuart), TipKf(87f, 22f), TipKf(100f, 22f))
                        val slashEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 22f), slashOffset)
                        drawPath(paths.eyeSlash, color = p.cardWhite.copy(alpha = slashAlpha), style = Stroke(width = 4f, cap = StrokeCap.Round, pathEffect = slashEffect))
                        drawPath(paths.eyeSlash, color = p.redAlert.copy(alpha = slashAlpha), style = Stroke(width = 2.2f, cap = StrokeCap.Round, pathEffect = slashEffect))
                    }

                    drawRoundRect(color = p.slateNavy, topLeft = Offset(273f, 27f), size = Size(34f, 5.5f), cornerRadius = CornerRadius(2.75f, 2.75f))
                    drawRoundRect(color = p.shadowColor, topLeft = Offset(273f, 37f), size = Size(24f, 4.5f), cornerRadius = CornerRadius(2.25f, 2.25f))

                    val trackMix = evalTipKf(t, TipKf(0f, 0f), TipKf(45f, 0f), TipKf(50f, 1f), TipKf(82f, 1f), TipKf(88f, 0f), TipKf(100f, 0f))
                    drawRoundRect(color = lerp(p.decoStroke, p.brightBlue, trackMix), topLeft = Offset(317f, 26f), size = Size(34f, 20f), cornerRadius = CornerRadius(10f, 10f))

                    val thumbTx = evalTipKf(t, TipKf(0f, 0f), TipKf(45f, 0f, TipEaseOutBack), TipKf(52f, 15.5f, TipEaseOutBack), TipKf(56f, 14f), TipKf(81f, 14f, TipEaseOutQuart), TipKf(88f, 0f), TipKf(100f, 0f))
                    val thumbS = evalTipKf(t, TipKf(0f, 1f), TipKf(45f, 1f, TipEaseOutBack), TipKf(52f, 1.1f, TipEaseOutBack), TipKf(56f, 1f), TipKf(100f, 1f))
                    withTipPivot(327f, 36f, tx = thumbTx, sx = thumbS) {
                        drawCircle(color = Color.White, radius = 7f, center = Offset(327f, 36f))
                    }
                }

                // 3. Center Stage: 3 Word Cards
                // 3.1 Left Card (Card 1 · 山 · 84, 140)
                val clTx = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(8f, 0f, TipEaseSym),
                    TipKf(11f, 0f, cArc1),
                    TipKf(22f, 116f, cArc2),
                    TipKf(34f, 232f, TipEaseOutBack),
                    TipKf(41f, 232f), TipKf(47f, 232f, TipEaseOutBack),
                    TipKf(54f, 240f), TipKf(79f, 240f, cArc1),
                    TipKf(88f, 116f, cArc2),
                    TipKf(96f, 0f), TipKf(100f, 0f)
                )
                val clTy = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(8f, 0f, TipEaseSym),
                    TipKf(11f, 4f, cArc1),
                    TipKf(22f, -18f, cArc2),
                    TipKf(34f, 3f, TipEaseOutBack),
                    TipKf(41f, 0f), TipKf(47f, 0f, TipEaseOutBack),
                    TipKf(54f, 2f), TipKf(79f, 2f, cArc1),
                    TipKf(88f, -12f, cArc2),
                    TipKf(96f, 0f), TipKf(100f, 0f)
                )
                val clSx = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(8f, 1f, TipEaseSym),
                    TipKf(11f, 1.04f, cArc1),
                    TipKf(22f, 1.02f, cArc2),
                    TipKf(34f, 1.04f, TipEaseOutBack),
                    TipKf(41f, 1f), TipKf(47f, 1f, TipEaseOutBack),
                    TipKf(54f, 0.92f), TipKf(79f, 0.92f, cArc1),
                    TipKf(88f, 0.98f, cArc2),
                    TipKf(96f, 1f), TipKf(100f, 1f)
                )
                val clSy = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(8f, 1f, TipEaseSym),
                    TipKf(11f, 0.95f, cArc1),
                    TipKf(22f, 1.02f, cArc2),
                    TipKf(34f, 0.96f, TipEaseOutBack),
                    TipKf(41f, 1f), TipKf(47f, 1f, TipEaseOutBack),
                    TipKf(54f, 0.92f), TipKf(79f, 0.92f, cArc1),
                    TipKf(88f, 1.02f, cArc2),
                    TipKf(96f, 1f), TipKf(100f, 1f)
                )
                val clRot = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(8f, 0f, TipEaseSym),
                    TipKf(11f, -2f, cArc1),
                    TipKf(22f, 3f, cArc2),
                    TipKf(34f, 1f, TipEaseOutBack),
                    TipKf(41f, 0f), TipKf(47f, 0f, TipEaseOutBack),
                    TipKf(54f, 2f), TipKf(79f, 2f, cArc1),
                    TipKf(88f, -2f, cArc2),
                    TipKf(96f, 0f), TipKf(100f, 0f)
                )
                val sideAlpha = evalTipKf(t, TipKf(0f, 1f), TipKf(47f, 1f, TipEaseOutBack), TipKf(54f, 0.65f), TipKf(79f, 0.65f, cArc1), TipKf(88f, 1f), TipKf(100f, 1f))

                withTipPivot(84f, 140f, tx = clTx, ty = clTy, sx = clSx, sy = clSy, rotDeg = clRot) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.45f * sideAlpha), topLeft = Offset(40f, 91f), size = Size(88f, 108f), cornerRadius = CornerRadius(18f, 18f))
                    drawRoundRect(color = p.cardWarm.copy(alpha = sideAlpha), topLeft = Offset(40f, 86f), size = Size(88f, 108f), cornerRadius = CornerRadius(18f, 18f))
                    drawRoundRect(color = p.decoMid.copy(alpha = sideAlpha), topLeft = Offset(40f, 86f), size = Size(88f, 108f), cornerRadius = CornerRadius(18f, 18f), style = Stroke(width = 1.5f))

                    drawRoundRect(color = p.goldSoft.copy(alpha = sideAlpha), topLeft = Offset(50f, 95f), size = Size(25f, 16f), cornerRadius = CornerRadius(8f, 8f))
                    drawPath(paths.num1, color = p.deepNavy.copy(alpha = sideAlpha), style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(color = p.decoStroke.copy(alpha = sideAlpha), radius = 3f, center = Offset(113f, 103f))
                    drawCircle(color = p.decoLight.copy(alpha = sideAlpha), radius = 3f, center = Offset(104f, 103f))

                    drawPath(paths.hanziShan, color = p.slateNavy.copy(alpha = sideAlpha), style = Stroke(width = 4.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    drawRoundRect(color = p.canvasBg.copy(alpha = sideAlpha), topLeft = Offset(53f, 164f), size = Size(62f, 16f), cornerRadius = CornerRadius(8f, 8f))
                    drawRoundRect(color = p.shadowColor.copy(alpha = sideAlpha), topLeft = Offset(59f, 169f), size = Size(32f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    drawRoundRect(color = p.blueContainer.copy(alpha = sideAlpha), topLeft = Offset(95f, 169f), size = Size(14f, 6f), cornerRadius = CornerRadius(3f, 3f))
                }

                // 3.2 Right Card (Card 3 · 云 · 316, 140)
                val crTx = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(8f, 0f, TipEaseSym),
                    TipKf(11f, 0f, cArc1),
                    TipKf(22f, -116f, cArc2),
                    TipKf(34f, -232f, TipEaseOutBack),
                    TipKf(41f, -232f), TipKf(47f, -232f, TipEaseOutBack),
                    TipKf(54f, -240f), TipKf(79f, -240f, cArc1),
                    TipKf(88f, -116f, cArc2),
                    TipKf(96f, 0f), TipKf(100f, 0f)
                )
                val crTy = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(8f, 0f, TipEaseSym),
                    TipKf(11f, 4f, cArc1),
                    TipKf(22f, 15f, cArc2),
                    TipKf(34f, 3f, TipEaseOutBack),
                    TipKf(41f, 0f), TipKf(47f, 0f, TipEaseOutBack),
                    TipKf(54f, 2f), TipKf(79f, 2f, cArc1),
                    TipKf(88f, 11f, cArc2),
                    TipKf(96f, 0f), TipKf(100f, 0f)
                )
                val crSx = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(8f, 1f, TipEaseSym),
                    TipKf(11f, 1.04f, cArc1),
                    TipKf(22f, 0.95f, cArc2),
                    TipKf(34f, 1.04f, TipEaseOutBack),
                    TipKf(41f, 1f), TipKf(47f, 1f, TipEaseOutBack),
                    TipKf(54f, 0.92f), TipKf(79f, 0.92f, cArc1),
                    TipKf(88f, 0.97f, cArc2),
                    TipKf(96f, 1f), TipKf(100f, 1f)
                )
                val crSy = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(8f, 1f, TipEaseSym),
                    TipKf(11f, 0.95f, cArc1),
                    TipKf(22f, 0.95f, cArc2),
                    TipKf(34f, 0.96f, TipEaseOutBack),
                    TipKf(41f, 1f), TipKf(47f, 1f, TipEaseOutBack),
                    TipKf(54f, 0.92f), TipKf(79f, 0.92f, cArc1),
                    TipKf(88f, 1.01f, cArc2),
                    TipKf(96f, 1f), TipKf(100f, 1f)
                )
                val crRot = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(8f, 0f, TipEaseSym),
                    TipKf(11f, 2f, cArc1),
                    TipKf(22f, -3f, cArc2),
                    TipKf(34f, -1f, TipEaseOutBack),
                    TipKf(41f, 0f), TipKf(47f, 0f, TipEaseOutBack),
                    TipKf(54f, -2f), TipKf(79f, -2f, cArc1),
                    TipKf(88f, 2f, cArc2),
                    TipKf(96f, 0f), TipKf(100f, 0f)
                )

                withTipPivot(316f, 140f, tx = crTx, ty = crTy, sx = crSx, sy = crSy, rotDeg = crRot) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.45f * sideAlpha), topLeft = Offset(272f, 91f), size = Size(88f, 108f), cornerRadius = CornerRadius(18f, 18f))
                    drawRoundRect(color = p.cardWarm.copy(alpha = sideAlpha), topLeft = Offset(272f, 86f), size = Size(88f, 108f), cornerRadius = CornerRadius(18f, 18f))
                    drawRoundRect(color = p.decoMid.copy(alpha = sideAlpha), topLeft = Offset(272f, 86f), size = Size(88f, 108f), cornerRadius = CornerRadius(18f, 18f), style = Stroke(width = 1.5f))

                    drawRoundRect(color = p.greenContainer.copy(alpha = sideAlpha), topLeft = Offset(282f, 95f), size = Size(25f, 16f), cornerRadius = CornerRadius(8f, 8f))
                    drawPath(paths.num3, color = p.greenStrong.copy(alpha = sideAlpha), style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(color = p.decoStroke.copy(alpha = sideAlpha), radius = 3f, center = Offset(345f, 103f))
                    drawCircle(color = p.decoLight.copy(alpha = sideAlpha), radius = 3f, center = Offset(336f, 103f))

                    drawPath(paths.hanziYun, color = p.slateNavy.copy(alpha = sideAlpha), style = Stroke(width = 4.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    drawRoundRect(color = p.canvasBg.copy(alpha = sideAlpha), topLeft = Offset(285f, 164f), size = Size(62f, 16f), cornerRadius = CornerRadius(8f, 8f))
                    drawRoundRect(color = p.shadowColor.copy(alpha = sideAlpha), topLeft = Offset(291f, 169f), size = Size(32f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    drawRoundRect(color = p.goldSoft.copy(alpha = sideAlpha), topLeft = Offset(327f, 169f), size = Size(14f, 6f), cornerRadius = CornerRadius(3f, 3f))
                }

                // 3.3 Center Hero Card (Card 2 · 方 · 200, 138)
                val ccTy = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(8f, 0f, TipEaseOutBack),
                    TipKf(11f, 4f, TipEaseOutQuart),
                    TipKf(22f, -10f, TipEaseOutBack),
                    TipKf(34f, 3f, TipEaseOutBack),
                    TipKf(41f, 0f), TipKf(47f, 0f, TipEaseOutBack),
                    TipKf(55f, -3f, TipEaseOutBack),
                    TipKf(59f, -2f), TipKf(79f, -2f, TipEaseOutQuart),
                    TipKf(89f, 2f, TipEaseOutBack),
                    TipKf(95f, 0f), TipKf(100f, 0f)
                )
                val ccSx = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(8f, 1f, TipEaseOutBack),
                    TipKf(11f, 1.04f, TipEaseOutQuart),
                    TipKf(22f, 1.05f, TipEaseOutBack),
                    TipKf(34f, 1.03f, TipEaseOutBack),
                    TipKf(41f, 1f), TipKf(47f, 1f, TipEaseOutBack),
                    TipKf(55f, 1.13f, TipEaseOutBack),
                    TipKf(59f, 1.10f), TipKf(79f, 1.10f, TipEaseOutQuart),
                    TipKf(89f, 0.99f, TipEaseOutBack),
                    TipKf(95f, 1f), TipKf(100f, 1f)
                )
                val ccSy = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(8f, 1f, TipEaseOutBack),
                    TipKf(11f, 0.95f, TipEaseOutQuart),
                    TipKf(22f, 1.05f, TipEaseOutBack),
                    TipKf(34f, 0.97f, TipEaseOutBack),
                    TipKf(41f, 1f), TipKf(47f, 1f, TipEaseOutBack),
                    TipKf(55f, 1.13f, TipEaseOutBack),
                    TipKf(59f, 1.10f), TipKf(79f, 1.10f, TipEaseOutQuart),
                    TipKf(89f, 0.99f, TipEaseOutBack),
                    TipKf(95f, 1f), TipKf(100f, 1f)
                )

                withTipPivot(200f, 138f, ty = ccTy, sx = ccSx, sy = ccSy) {
                    // Focus Halo Ring (Act 2)
                    val haloAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(56f, 0f, TipEaseOutBack), TipKf(62f, 1f, TipEaseOutBack), TipKf(67f, 0.95f), TipKf(79f, 0.95f, TipEaseOutBack), TipKf(86f, 0f), TipKf(100f, 0f))
                    val haloScale = evalTipKf(t, TipKf(0f, 0.92f), TipKf(56f, 0.92f, TipEaseOutBack), TipKf(62f, 1.02f, TipEaseOutBack), TipKf(67f, 1f), TipKf(79f, 1f, TipEaseOutBack), TipKf(86f, 0.92f), TipKf(100f, 0.92f))
                    if (haloAlpha > 0.01f) {
                        withTipPivot(200f, 136f, sx = haloScale) {
                            drawRoundRect(color = p.blueContainer.copy(alpha = 0.36f * haloAlpha), topLeft = Offset(139f, 69f), size = Size(122f, 138f), cornerRadius = CornerRadius(27f, 27f))
                            drawRoundRect(color = p.skyBlue.copy(alpha = 0.65f * haloAlpha), topLeft = Offset(143f, 73f), size = Size(114f, 130f), cornerRadius = CornerRadius(24f, 24f), style = Stroke(width = 1.6f))
                        }
                    }

                    // Hero Card Body & Shadow
                    drawRoundRect(color = p.deepNavy.copy(alpha = 0.16f), topLeft = Offset(148f, 85f), size = Size(104f, 120f), cornerRadius = CornerRadius(20f, 20f))
                    drawRoundRect(color = p.cardWhite, topLeft = Offset(148f, 78f), size = Size(104f, 120f), cornerRadius = CornerRadius(20f, 20f))
                    drawRoundRect(color = p.decoStroke, topLeft = Offset(148f, 78f), size = Size(104f, 120f), cornerRadius = CornerRadius(20f, 20f), style = Stroke(width = 1.8f))

                    clipPath(mainCardClip) {
                        drawRect(color = p.canvasBg.copy(alpha = 0.55f), topLeft = Offset(148f, 78f), size = Size(104f, 32f))

                        // Number 2 Pill
                        drawRoundRect(color = p.blueContainer, topLeft = Offset(158f, 87f), size = Size(26f, 17f), cornerRadius = CornerRadius(8.5f, 8.5f))
                        drawPath(paths.num2, color = p.brightBlue, style = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                        // Top-Right aA Badge
                        val aaMix = evalTipKf(t, TipKf(0f, 0f), TipKf(50f, 0f), TipKf(56f, 1f), TipKf(80f, 1f), TipKf(88f, 0f), TipKf(100f, 0f))
                        drawRoundRect(color = lerp(p.decoLight, p.greenContainer, aaMix), topLeft = Offset(216f, 87f), size = Size(26f, 17f), cornerRadius = CornerRadius(8.5f, 8.5f))
                        drawPath(paths.aaGlyph, color = lerp(p.slateNavy, p.greenStrong, aaMix), style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                        // Focus Corners around Hanzi
                        if (haloAlpha > 0.01f) {
                            withTipPivot(200f, 136f, sx = haloScale) {
                                drawPath(paths.focusCorners, color = p.greenStrong.copy(alpha = 0.85f * haloAlpha), style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }

                        // Main Hanzi 「方」 (200, 131)
                        val hzTy = evalTipKf(t, TipKf(0f, 0f), TipKf(53f, 0f, TipEaseOutBack), TipKf(61f, 5.5f, TipEaseOutBack), TipKf(66f, 5f), TipKf(80f, 5f, TipEaseOutQuart), TipKf(89f, -1f, TipEaseOutBack), TipKf(94f, 0f), TipKf(100f, 0f))
                        val hzScale = evalTipKf(t, TipKf(0f, 1f), TipKf(53f, 1f, TipEaseOutBack), TipKf(61f, 1.25f, TipEaseOutBack), TipKf(66f, 1.22f), TipKf(80f, 1.22f, TipEaseOutQuart), TipKf(89f, 0.98f, TipEaseOutBack), TipKf(94f, 1f), TipKf(100f, 1f))
                        val hzColorMix = evalTipKf(t, TipKf(0f, 0f), TipKf(51f, 0f), TipKf(58f, 1f), TipKf(80f, 1f), TipKf(88f, 0f), TipKf(100f, 0f))
                        withTipPivot(200f, 131f, ty = hzTy, sx = hzScale) {
                            drawPath(paths.hanziFang, color = lerp(p.deepNavy, p.brightBlue, hzColorMix), style = Stroke(width = 5.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }

                        // Bottom Note Bar (200, 173)
                        val nbAlpha = evalTipKf(t, TipKf(0f, 1f), TipKf(48f, 1f, TipEaseOutBack), TipKf(56f, 0f), TipKf(81f, 0f, TipEaseOutBack), TipKf(89f, 1f), TipKf(100f, 1f))
                        if (nbAlpha > 0.01f) {
                            val nbTy = evalTipKf(t, TipKf(0f, 0f), TipKf(48f, 0f, TipEaseOutBack), TipKf(56f, 12f), TipKf(81f, 12f, TipEaseOutBack), TipKf(89f, -1f, TipEaseOutBack), TipKf(94f, 0f), TipKf(100f, 0f))
                            val nbSx = evalTipKf(t, TipKf(0f, 1f), TipKf(48f, 1f, TipEaseOutBack), TipKf(56f, 0.82f), TipKf(81f, 0.82f, TipEaseOutBack), TipKf(89f, 1.02f, TipEaseOutBack), TipKf(94f, 1f), TipKf(100f, 1f))
                            val nbSy = evalTipKf(t, TipKf(0f, 1f), TipKf(48f, 1f, TipEaseOutBack), TipKf(56f, 0.45f), TipKf(81f, 0.45f, TipEaseOutBack), TipKf(89f, 1.02f, TipEaseOutBack), TipKf(94f, 1f), TipKf(100f, 1f))
                            withTipPivot(200f, 173f, ty = nbTy, sx = nbSx, sy = nbSy) {
                                drawRoundRect(color = p.canvasBg.copy(alpha = nbAlpha), topLeft = Offset(162f, 163f), size = Size(76f, 19f), cornerRadius = CornerRadius(9.5f, 9.5f))
                                drawRoundRect(color = p.shadowColor.copy(alpha = nbAlpha), topLeft = Offset(169f, 169.5f), size = Size(38f, 6f), cornerRadius = CornerRadius(3f, 3f))
                                drawRoundRect(color = p.softBlue.copy(alpha = nbAlpha), topLeft = Offset(212f, 169.5f), size = Size(19f, 6f), cornerRadius = CornerRadius(3f, 3f))
                            }
                        }

                        // Eraser Dash Box & Scan Beam
                        val edAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(46f, 0f, TipEaseOutQuart), TipKf(49f, 0.95f), TipKf(54f, 0.95f, TipEaseOutQuart), TipKf(57f, 0f), TipKf(100f, 0f))
                        if (edAlpha > 0.01f) {
                            val edScale = evalTipKf(t, TipKf(0f, 0.94f), TipKf(46f, 0.94f, TipEaseOutQuart), TipKf(49f, 1f), TipKf(54f, 1f, TipEaseOutQuart), TipKf(57f, 0.92f), TipKf(100f, 0.94f))
                            withTipPivot(200f, 173f, sx = edScale) {
                                drawRoundRect(color = p.blueContainer.copy(alpha = 0.28f * edAlpha), topLeft = Offset(160f, 161f), size = Size(80f, 23f), cornerRadius = CornerRadius(9f, 9f))
                                drawRoundRect(color = p.brightBlue.copy(alpha = edAlpha), topLeft = Offset(160f, 161f), size = Size(80f, 23f), cornerRadius = CornerRadius(9f, 9f), style = Stroke(width = 1.5f, pathEffect = dashEraser))
                            }
                        }

                        val ebAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(47f, 0f, TipEaseOutQuart), TipKf(49f, 1f), TipKf(55f, 1f, TipEaseOutQuart), TipKf(57f, 0f), TipKf(100f, 0f))
                        if (ebAlpha > 0.01f) {
                            val ebTy = evalTipKf(t, TipKf(0f, 0f), TipKf(47f, 0f, TipEaseOutQuart), TipKf(49f, 2f, TipEaseOutQuart), TipKf(55f, 20f, TipEaseOutQuart), TipKf(57f, 22f), TipKf(100f, 0f))
                            val ebSx = evalTipKf(t, TipKf(0f, 0.75f), TipKf(47f, 0.75f, TipEaseOutQuart), TipKf(49f, 1f), TipKf(55f, 1f, TipEaseOutQuart), TipKf(57f, 0.75f), TipKf(100f, 0.75f))
                            withTipPivot(200f, 162f, ty = ebTy, sx = ebSx, sy = 1f) {
                                drawRoundRect(color = p.skyBlue.copy(alpha = 0.35f * ebAlpha), topLeft = Offset(158f, 159f), size = Size(84f, 6f), cornerRadius = CornerRadius(3f, 3f))
                                drawLine(color = p.brightBlue.copy(alpha = ebAlpha), start = Offset(158f, 162f), end = Offset(242f, 162f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                            }
                        }
                    }

                    // Bottom-Right Focus Check Badge (246, 188)
                    val fcAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(55f, 0f, TipEaseOutBack), TipKf(62f, 1f), TipKf(79f, 1f, TipEaseOutBack), TipKf(85f, 0f), TipKf(100f, 0f))
                    if (fcAlpha > 0.01f) {
                        val fcScale = evalTipKf(t, TipKf(0f, 0f), TipKf(55f, 0f, TipEaseOutBack), TipKf(62f, 1.15f, TipEaseOutBack), TipKf(67f, 1f), TipKf(79f, 1f, TipEaseOutBack), TipKf(85f, 0f), TipKf(100f, 0f))
                        withTipPivot(246f, 188f, sx = fcScale) {
                            drawCircle(color = p.greenStrong.copy(alpha = fcAlpha), radius = 11f, center = Offset(246f, 188f))
                            drawCircle(color = p.cardWhite.copy(alpha = fcAlpha), radius = 11f, center = Offset(246f, 188f), style = Stroke(width = 2f))
                            drawPath(paths.focusCheck, color = Color.White.copy(alpha = fcAlpha), style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
        }
    }
}
