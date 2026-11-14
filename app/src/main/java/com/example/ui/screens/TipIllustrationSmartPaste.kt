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
import kotlin.math.min

private object SmartPastePaths {
    val bracketLeft by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 59 69 H 51 C 50.4 69 50 69.4 50 70 V 84 C 50 84.6 50.4 85 51 85 H 59 V 81.5 H 54.2 V 72.5 H 59 V 69 Z")
    }
    val bracketRight by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 113 69 H 121 C 121.6 69 122 69.4 122 70 V 84 C 122 84.6 121.6 85 121 85 H 113 V 81.5 H 117.8 V 72.5 H 113 V 69 Z")
    }
    val row2ParenL by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 79 105 C 74.5 109.2 74.5 116.8 79 121") }
    val row2ParenR by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 114 105 C 118.5 109.2 118.5 116.8 114 121") }
    val row3ParenL by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 79 139 C 74.5 143.2 74.5 150.8 79 155") }
    val row3ParenR by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 118 139 C 122.5 143.2 122.5 150.8 118 155") }

    val guideArc by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 146.8 104.9 A 36 36 0 0 1 199.4 104.0") }
    val guideArcInner by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 146.8 104.9 A 36 36 0 0 1 196.5 101.2") }
    val guideArrowHead by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 198.2 92.6 L 200.2 104.8 L 188.0 102.4") }

    val sparkleMain by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 174 51 Q 174 62 185 62 Q 174 62 174 73 Q 174 62 163 62 Q 174 62 174 51 Z") }
    val sparkleSub1 by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 152 67 Q 152 74 159 74 Q 152 74 152 81 Q 152 74 145 74 Q 152 74 152 67 Z") }
    val sparkleSub2 by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 195 68 Q 195 75 202 75 Q 195 75 195 82 Q 195 75 188 75 Q 195 75 195 68 Z") }

    val panelHeader by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 210 48 C 210 36.954 218.954 28 230 28 H 354 C 365.046 28 374 36.954 374 48 V 71 H 210 V 48 Z")
    }
    val groupBadgeBrackets by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 230 46.5 H 228.5 V 53.5 H 230 M 236 46.5 H 237.5 V 53.5 H 236")
    }

    val card1Spark by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 275 94 Q 275 103 284 103 Q 275 103 275 112 Q 275 103 266 103 Q 275 103 275 94 Z") }
    val hanziWen by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 246 89.5 L 248 93.5 M 236.5 95.5 H 257.5 M 252.5 95.5 C 250 104 244.5 110.5 236.5 114.5 M 241 99 C 245.5 105.5 251 111 257.5 114.5")
    }
    val card1GhostParenL by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 281 89 C 276 95 276 111 281 117") }
    val card1GhostParenR by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 359 89 C 364 95 364 111 359 117") }
    val card1PitchWave by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 293.5 105.5 L 297.5 99.5 L 302.5 103.5") }

    val card2Spark by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 275 154 Q 275 163 284 163 Q 275 163 275 172 Q 275 163 266 163 Q 275 163 275 154 Z") }
    val hanziYan by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 246 148.5 L 248 151.5 M 236 153.5 H 258 M 240 158.5 H 254 M 240 163.5 H 254 M 240.5 168.5 H 253.5 V 175.5 H 240.5 Z")
    }
    val card2GhostParenL by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 281 149 C 276 155 276 171 281 177") }
    val card2GhostParenR by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 359 149 C 364 155 364 171 359 177") }
    val card2PitchCurve by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 293.5 161.5 Q 298 167.5 302.5 160") }

    val successCheck by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 362.5 34.2 L 366.2 38 L 373.8 30.4") }
}

/**
 * 贴士 06｜复制文本即可智能建表 (5.0s Seamless Loop)
 */
@Composable
internal fun TipIllustrationSmartPaste(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val p = remember(colorScheme, isDark) { TipDynamicPalette(colorScheme, isDark) }
    val paths = SmartPastePaths

    val dashArc = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f) }
    val cOrbit = TipBezierCurve(0.42f, 0f, 0.22f, 1f)

    Canvas(modifier = modifier) {
        val contentW = 388f
        val contentH = 214f
        val scale = min(size.width / contentW, size.height / contentH)
        val offsetX = size.width / 2f - 199f * scale
        val offsetY = size.height / 2f - 114f * scale

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // 0. M3 Geometric Decoration

                withTipPivot(74f, 48f, rotDeg = -11f) {
                    drawRoundRect(color = p.decoLight, topLeft = Offset(-28f, -24f), size = Size(205f, 145f), cornerRadius = CornerRadius(72.5f, 72.5f))
                }
                drawRoundRect(color = p.decoMid.copy(alpha = 0.65f), topLeft = Offset(192f, 62f), size = Size(235f, 195f), cornerRadius = CornerRadius(97.5f, 97.5f))
                drawRoundRect(color = p.decoLight.copy(alpha = 0.7f), topLeft = Offset(122f, 76f), size = Size(116f, 72f), cornerRadius = CornerRadius(36f, 36f))

                // Decorative Fieldwork Grid Dots
                val dotPositions = listOf(
                    Offset(26f, 30f), Offset(38f, 30f), Offset(26f, 42f),
                    Offset(164f, 188f), Offset(176f, 188f), Offset(188f, 188f),
                    Offset(176f, 200f), Offset(188f, 200f)
                )
                dotPositions.forEach { pt ->
                    drawCircle(color = p.decoStroke, radius = 2.2f, center = pt)
                }

                // 1. Left Side: Raw Text Clipboard Card (tilted -4deg around 86, 117)
                withTipPivot(86f, 117f, rotDeg = -4f) {
                    // Copy Ripple 2
                    val r2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(15f, 0f, TipEaseOutExpo), TipKf(18f, 0.5f, TipEaseOutExpo), TipKf(34f, 0f), TipKf(100f, 0f))
                    if (r2Alpha > 0.01f) {
                        val r2Scale = evalTipKf(t, TipKf(0f, 1f), TipKf(15f, 1f, TipEaseOutExpo), TipKf(18f, 1.02f, TipEaseOutExpo), TipKf(34f, 1.32f), TipKf(100f, 1f))
                        withTipPivot(86f, 117f, sx = r2Scale) {
                            drawRoundRect(color = p.skyBlue.copy(alpha = r2Alpha), topLeft = Offset(28f, 40f), size = Size(116f, 154f), cornerRadius = CornerRadius(20f, 20f), style = Stroke(width = 2.5f))
                        }
                    }

                    // Copy Ripple 1
                    val r1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(11f, 0f, TipEaseOutExpo), TipKf(14f, 0.75f, TipEaseOutExpo), TipKf(30f, 0f), TipKf(100f, 0f))
                    if (r1Alpha > 0.01f) {
                        val r1Scale = evalTipKf(t, TipKf(0f, 1f), TipKf(11f, 1f, TipEaseOutExpo), TipKf(14f, 1.02f, TipEaseOutExpo), TipKf(30f, 1.24f), TipKf(100f, 1f))
                        withTipPivot(86f, 117f, sx = r1Scale) {
                            drawRoundRect(color = p.brightBlue.copy(alpha = r1Alpha), topLeft = Offset(28f, 40f), size = Size(116f, 154f), cornerRadius = CornerRadius(20f, 20f), style = Stroke(width = 3.5f))
                        }
                    }

                    // Clipboard Card Bounce (86, 117)
                    val cbTx = evalTipKf(t, TipKf(0f, 0f), TipKf(4f, 0f, TipEaseOutBack), TipKf(10f, 0f, TipEaseOutBack), TipKf(18f, 2f, TipEaseOutBack), TipKf(25f, 0f, TipEaseOutBack), TipKf(32f, 0f), TipKf(100f, 0f))
                    val cbTy = evalTipKf(t, TipKf(0f, 0f), TipKf(4f, 0f, TipEaseOutBack), TipKf(10f, 5f, TipEaseOutBack), TipKf(18f, -7f, TipEaseOutBack), TipKf(25f, 2f, TipEaseOutBack), TipKf(32f, 0f), TipKf(100f, 0f))
                    val cbSx = evalTipKf(t, TipKf(0f, 1f), TipKf(4f, 1f, TipEaseOutBack), TipKf(10f, 0.95f, TipEaseOutBack), TipKf(18f, 1.05f, TipEaseOutBack), TipKf(25f, 0.98f, TipEaseOutBack), TipKf(32f, 1f), TipKf(100f, 1f))
                    val cbSy = evalTipKf(t, TipKf(0f, 1f), TipKf(4f, 1f, TipEaseOutBack), TipKf(10f, 0.92f, TipEaseOutBack), TipKf(18f, 1.06f, TipEaseOutBack), TipKf(25f, 0.99f, TipEaseOutBack), TipKf(32f, 1f), TipKf(100f, 1f))
                    val cbRot = evalTipKf(t, TipKf(0f, 0f), TipKf(4f, 0f, TipEaseOutBack), TipKf(10f, -1.5f, TipEaseOutBack), TipKf(18f, 2.5f, TipEaseOutBack), TipKf(25f, -0.5f, TipEaseOutBack), TipKf(32f, 0f), TipKf(100f, 0f))

                    withTipPivot(86f, 117f, tx = cbTx, ty = cbTy, sx = cbSx, sy = cbSy, rotDeg = cbRot) {
                        drawRoundRect(color = p.shadowColor.copy(alpha = 0.55f), topLeft = Offset(30f, 48f), size = Size(112f, 150f), cornerRadius = CornerRadius(16f, 16f))
                        drawRoundRect(color = p.slateNavy, topLeft = Offset(30f, 42f), size = Size(112f, 150f), cornerRadius = CornerRadius(16f, 16f))
                        drawRoundRect(color = p.skyBlue.copy(alpha = 0.25f), topLeft = Offset(33f, 45f), size = Size(106f, 144f), cornerRadius = CornerRadius(13.5f, 13.5f), style = Stroke(width = 1f))
                        drawRoundRect(color = p.cardWarm, topLeft = Offset(36f, 51f), size = Size(100f, 135f), cornerRadius = CornerRadius(11f, 11f))

                        // Paper Corner Fold & TXT Indicator Pill
                        drawRoundRect(color = p.decoStroke, topLeft = Offset(44f, 169f), size = Size(38f, 6f), cornerRadius = CornerRadius(3f, 3f))
                        drawRoundRect(color = p.blueContainer, topLeft = Offset(104f, 167f), size = Size(24f, 10f), cornerRadius = CornerRadius(5f, 5f))
                        drawCircle(color = p.brightBlue, radius = 2f, center = Offset(111f, 172f))
                        drawCircle(color = p.brightBlue, radius = 2f, center = Offset(116f, 172f))
                        drawCircle(color = p.brightBlue, radius = 2f, center = Offset(121f, 172f))

                        // Row 1: Bold Square Bracket Title Block
                        val sel1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(8f, 0f, TipEaseOutQuart), TipKf(14f, 1f), TipKf(26f, 1f, TipEaseOutQuart), TipKf(34f, 0f), TipKf(100f, 0f))
                        if (sel1Alpha > 0.01f) {
                            val sel1Sx = evalTipKf(t, TipKf(0f, 0.15f), TipKf(8f, 0.15f, TipEaseOutQuart), TipKf(14f, 1f), TipKf(26f, 1f, TipEaseOutQuart), TipKf(34f, 0.15f), TipKf(100f, 0.15f))
                            withTipPivot(41f, 77f, sx = sel1Sx, sy = 1f) {
                                drawRoundRect(color = p.blueContainer.copy(alpha = sel1Alpha), topLeft = Offset(41f, 64f), size = Size(90f, 26f), cornerRadius = CornerRadius(6f, 6f))
                            }
                        }
                        val rp1S = evalTipKf(t, TipKf(0f, 1f), TipKf(10f, 1f, TipEaseOutBack), TipKf(16f, 1.06f, TipEaseOutBack), TipKf(26f, 1f), TipKf(100f, 1f))
                        val rp1Tx = evalTipKf(t, TipKf(0f, 0f), TipKf(10f, 0f, TipEaseOutBack), TipKf(16f, 2f, TipEaseOutBack), TipKf(26f, 0f), TipKf(100f, 0f))
                        withTipPivot(86f, 77f, tx = rp1Tx, sx = rp1S) {
                            drawPath(paths.bracketLeft, color = p.brightBlue)
                            drawRoundRect(color = p.deepNavy, topLeft = Offset(64f, 73f), size = Size(44f, 8f), cornerRadius = CornerRadius(4f, 4f))
                            drawPath(paths.bracketRight, color = p.brightBlue)
                        }

                        // Row 2: Compact Raw Text Line 1
                        val sel2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(11f, 0f, TipEaseOutQuart), TipKf(17f, 1f), TipKf(28f, 1f, TipEaseOutQuart), TipKf(36f, 0f), TipKf(100f, 0f))
                        if (sel2Alpha > 0.01f) {
                            val sel2Sx = evalTipKf(t, TipKf(0f, 0.15f), TipKf(11f, 0.15f, TipEaseOutQuart), TipKf(17f, 1f), TipKf(28f, 1f, TipEaseOutQuart), TipKf(36f, 0.15f), TipKf(100f, 0.15f))
                            withTipPivot(41f, 113f, sx = sel2Sx, sy = 1f) {
                                drawRoundRect(color = p.blueContainer.copy(alpha = sel2Alpha), topLeft = Offset(41f, 100f), size = Size(90f, 26f), cornerRadius = CornerRadius(6f, 6f))
                            }
                        }
                        val rp2S = evalTipKf(t, TipKf(0f, 1f), TipKf(13f, 1f, TipEaseOutBack), TipKf(19f, 1.06f, TipEaseOutBack), TipKf(29f, 1f), TipKf(100f, 1f))
                        val rp2Tx = evalTipKf(t, TipKf(0f, 0f), TipKf(13f, 0f, TipEaseOutBack), TipKf(19f, 2f, TipEaseOutBack), TipKf(29f, 0f), TipKf(100f, 0f))
                        withTipPivot(86f, 113f, tx = rp2Tx, sx = rp2S) {
                            drawRoundRect(color = p.deepNavy, topLeft = Offset(46f, 109f), size = Size(24f, 8f), cornerRadius = CornerRadius(4f, 4f))
                            drawPath(paths.row2ParenL, color = p.goldStrong, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
                            drawRoundRect(color = p.goldStrong, topLeft = Offset(83f, 110f), size = Size(27f, 6f), cornerRadius = CornerRadius(3f, 3f))
                            drawPath(paths.row2ParenR, color = p.goldStrong, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
                        }

                        // Row 3: Compact Raw Text Line 2
                        val sel3Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(14f, 0f, TipEaseOutQuart), TipKf(20f, 1f), TipKf(30f, 1f, TipEaseOutQuart), TipKf(38f, 0f), TipKf(100f, 0f))
                        if (sel3Alpha > 0.01f) {
                            val sel3Sx = evalTipKf(t, TipKf(0f, 0.15f), TipKf(14f, 0.15f, TipEaseOutQuart), TipKf(20f, 1f), TipKf(30f, 1f, TipEaseOutQuart), TipKf(38f, 0.15f), TipKf(100f, 0.15f))
                            withTipPivot(41f, 147f, sx = sel3Sx, sy = 1f) {
                                drawRoundRect(color = p.blueContainer.copy(alpha = sel3Alpha), topLeft = Offset(41f, 134f), size = Size(90f, 26f), cornerRadius = CornerRadius(6f, 6f))
                            }
                        }
                        val rp3S = evalTipKf(t, TipKf(0f, 1f), TipKf(16f, 1f, TipEaseOutBack), TipKf(22f, 1.06f, TipEaseOutBack), TipKf(32f, 1f), TipKf(100f, 1f))
                        val rp3Tx = evalTipKf(t, TipKf(0f, 0f), TipKf(16f, 0f, TipEaseOutBack), TipKf(22f, 2f, TipEaseOutBack), TipKf(32f, 0f), TipKf(100f, 0f))
                        withTipPivot(86f, 147f, tx = rp3Tx, sx = rp3S) {
                            drawRoundRect(color = p.deepNavy, topLeft = Offset(46f, 143f), size = Size(24f, 8f), cornerRadius = CornerRadius(4f, 4f))
                            drawPath(paths.row3ParenL, color = p.greenMid, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
                            drawRoundRect(color = p.greenMid, topLeft = Offset(83f, 144f), size = Size(31f, 6f), cornerRadius = CornerRadius(3f, 3f))
                            drawPath(paths.row3ParenR, color = p.greenMid, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
                        }

                        // Top Deep Blue Metal Clip (86, 42)
                        val clTy = evalTipKf(t, TipKf(0f, 0f), TipKf(6f, 0f, TipEaseOutBack), TipKf(11f, -4f, TipEaseOutBack), TipKf(17f, 2f, TipEaseOutBack), TipKf(24f, 0f), TipKf(100f, 0f))
                        val clSx = evalTipKf(t, TipKf(0f, 1f), TipKf(6f, 1f, TipEaseOutBack), TipKf(11f, 1.06f, TipEaseOutBack), TipKf(17f, 0.96f, TipEaseOutBack), TipKf(24f, 1f), TipKf(100f, 1f))
                        val clSy = evalTipKf(t, TipKf(0f, 1f), TipKf(6f, 1f, TipEaseOutBack), TipKf(11f, 0.88f, TipEaseOutBack), TipKf(17f, 1.08f, TipEaseOutBack), TipKf(24f, 1f), TipKf(100f, 1f))
                        withTipPivot(86f, 42f, ty = clTy, sx = clSx, sy = clSy) {
                            drawRoundRect(color = p.canvasBg, topLeft = Offset(74f, 26f), size = Size(24f, 14f), cornerRadius = CornerRadius(7f, 7f))
                            drawRoundRect(color = p.deepNavy, topLeft = Offset(74f, 26f), size = Size(24f, 14f), cornerRadius = CornerRadius(7f, 7f), style = Stroke(width = 3.5f))
                            drawRoundRect(color = p.shadowColor.copy(alpha = 0.6f), topLeft = Offset(60f, 38f), size = Size(52f, 18f), cornerRadius = CornerRadius(6f, 6f))
                            drawRoundRect(color = p.deepNavy, topLeft = Offset(60f, 35f), size = Size(52f, 18f), cornerRadius = CornerRadius(6f, 6f))
                            drawRoundRect(color = p.blueContainer, topLeft = Offset(70f, 42f), size = Size(32f, 4f), cornerRadius = CornerRadius(2f, 2f))
                        }
                    }
                }

                // 2. Middle Bridge: Curved Guide Arrow, Stream, Orb & Sparkles
                drawPath(paths.guideArc, color = p.decoStroke, style = Stroke(width = 6.5f, cap = StrokeCap.Round))
                drawPath(paths.guideArrowHead, color = p.decoStroke, style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(paths.guideArcInner, color = p.cardWarm, style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = dashArc))

                // Animated Highlight Stream
                val streamAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(14f, 0f, TipEaseStandard), TipKf(17f, 1f, TipEaseStandard), TipKf(29f, 1f, TipEaseStandard), TipKf(36f, 0f), TipKf(100f, 0f))
                if (streamAlpha > 0.01f) {
                    val streamOffset = evalTipKf(t, TipKf(0f, 64f), TipKf(14f, 64f, TipEaseStandard), TipKf(29f, 0f, TipEaseStandard), TipKf(36f, -64f), TipKf(100f, 64f))
                    drawPath(
                        paths.guideArc,
                        color = p.brightBlue.copy(alpha = streamAlpha),
                        style = Stroke(width = 6.5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(64f, 64f), streamOffset))
                    )
                }

                val headAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(25f, 0f, TipEaseOutQuart), TipKf(28f, 1f), TipKf(33f, 1f, TipEaseOutQuart), TipKf(38f, 0f), TipKf(100f, 0f))
                if (headAlpha > 0.01f) {
                    val headOffset = evalTipKf(t, TipKf(0f, 28f), TipKf(25f, 28f, TipEaseOutQuart), TipKf(28f, 0f), TipKf(100f, 0f))
                    drawPath(
                        paths.guideArrowHead,
                        color = p.brightBlue.copy(alpha = headAlpha),
                        style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 28f), headOffset))
                    )
                }

                // Magic Sparkles
                val spMainS = evalTipKf(t, TipKf(0f, 1f), TipKf(15f, 1f, TipEaseOutBack), TipKf(24f, 1.42f, TipEaseOutBack), TipKf(35f, 0.92f, TipEaseOutBack), TipKf(45f, 1f), TipKf(100f, 1f))
                val spMainRot = evalTipKf(t, TipKf(0f, 0f), TipKf(15f, 0f, TipEaseOutBack), TipKf(24f, 45f, TipEaseOutBack), TipKf(35f, 90f, TipEaseOutBack), TipKf(45f, 0f), TipKf(100f, 0f))
                withTipPivot(174f, 62f, sx = spMainS, rotDeg = spMainRot) {
                    drawPath(paths.sparkleMain, color = p.goldStrong)
                }
                drawCircle(color = p.cardWarm, radius = 2.2f, center = Offset(174f, 62f))

                val spSub1S = evalTipKf(t, TipKf(0f, 0.75f), TipKf(14f, 0.75f, TipEaseOutBack), TipKf(22f, 1.35f, TipEaseOutBack), TipKf(34f, 0.75f), TipKf(100f, 0.75f))
                val spSub1Rot = evalTipKf(t, TipKf(0f, 0f), TipKf(14f, 0f, TipEaseOutBack), TipKf(22f, -35f, TipEaseOutBack), TipKf(34f, 0f), TipKf(100f, 0f))
                val spSub1Alpha = evalTipKf(t, TipKf(0f, 0.55f), TipKf(14f, 0.55f), TipKf(22f, 1f), TipKf(34f, 0.55f), TipKf(100f, 0.55f))
                withTipPivot(152f, 74f, sx = spSub1S, rotDeg = spSub1Rot) {
                    drawPath(paths.sparkleSub1, color = p.skyBlue.copy(alpha = spSub1Alpha))
                }

                val spSub2S = evalTipKf(t, TipKf(0f, 0.75f), TipKf(18f, 0.75f, TipEaseOutBack), TipKf(27f, 1.38f, TipEaseOutBack), TipKf(39f, 0.75f), TipKf(100f, 0.75f))
                val spSub2Rot = evalTipKf(t, TipKf(0f, 0f), TipKf(18f, 0f, TipEaseOutBack), TipKf(27f, 35f, TipEaseOutBack), TipKf(39f, 0f), TipKf(100f, 0f))
                val spSub2Alpha = evalTipKf(t, TipKf(0f, 0.55f), TipKf(18f, 0.55f), TipKf(27f, 1f), TipKf(39f, 0.55f), TipKf(100f, 0.55f))
                withTipPivot(195f, 75f, sx = spSub2S, rotDeg = spSub2Rot) {
                    drawPath(paths.sparkleSub2, color = p.greenMid.copy(alpha = spSub2Alpha))
                }

                drawCircle(color = p.goldSoft, radius = 3f, center = Offset(174f, 119f))
                drawCircle(color = p.decoStroke, radius = 2f, center = Offset(163f, 125f))
                drawCircle(color = p.decoStroke, radius = 2f, center = Offset(185f, 125f))

                // Flying Energy Orb along R=36 circular arc around (173.5, 129)
                val orbAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(14f, 0f, TipEaseOutQuart), TipKf(18f, 1f), TipKf(29f, 1f, TipEaseOutQuart), TipKf(33f, 0f), TipKf(100f, 0f))
                if (orbAlpha > 0.01f) {
                    val orbRot = evalTipKf(t, TipKf(0f, -50f), TipKf(14f, -50f, cOrbit), TipKf(33f, 48f), TipKf(100f, 48f))
                    val orbScale = evalTipKf(t, TipKf(0f, 0.2f), TipKf(14f, 0.2f, TipEaseOutQuart), TipKf(18f, 1.05f), TipKf(29f, 1f), TipKf(33f, 1.45f), TipKf(100f, 0.2f))
                    withTipPivot(173.5f, 129f, rotDeg = orbRot) {
                        withTipPivot(173.5f, 93f, sx = orbScale) {
                            drawCircle(color = p.skyBlue.copy(alpha = 0.38f * orbAlpha), radius = 12.5f, center = Offset(173.5f, 93f))
                            drawCircle(color = p.brightBlue.copy(alpha = orbAlpha), radius = 7.2f, center = Offset(173.5f, 93f))
                            drawCircle(color = p.cardWarm.copy(alpha = orbAlpha), radius = 3.2f, center = Offset(173.5f, 93f))
                        }
                    }
                }

                // 3. Right Side: Structured Wordlist Panel (292, 113)
                val rpTx = evalTipKf(t, TipKf(0f, 0f), TipKf(30f, 0f, TipEaseOutBack), TipKf(34f, 4f, TipEaseOutBack), TipKf(39f, -2f, TipEaseOutBack), TipKf(45f, 0f), TipKf(76f, 0f, TipEaseOutBack), TipKf(88f, 0f), TipKf(100f, 0f))
                val rpTy = evalTipKf(t, TipKf(0f, 0f), TipKf(30f, 0f, TipEaseOutBack), TipKf(34f, 1f, TipEaseOutBack), TipKf(39f, -1f, TipEaseOutBack), TipKf(45f, -2f), TipKf(76f, -2f, TipEaseOutBack), TipKf(88f, 0f), TipKf(100f, 0f))
                val rpSx = evalTipKf(t, TipKf(0f, 1f), TipKf(30f, 1f, TipEaseOutBack), TipKf(34f, 1.025f, TipEaseOutBack), TipKf(39f, 0.99f, TipEaseOutBack), TipKf(45f, 1.008f), TipKf(76f, 1.008f, TipEaseOutBack), TipKf(88f, 1f), TipKf(100f, 1f))
                val rpSy = evalTipKf(t, TipKf(0f, 1f), TipKf(30f, 1f, TipEaseOutBack), TipKf(34f, 0.975f, TipEaseOutBack), TipKf(39f, 1.015f, TipEaseOutBack), TipKf(45f, 1.008f), TipKf(76f, 1.008f, TipEaseOutBack), TipKf(88f, 1f), TipKf(100f, 1f))

                withTipPivot(292f, 113f, tx = rpTx, ty = rpTy, sx = rpSx, sy = rpSy) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.55f), topLeft = Offset(208f, 33f), size = Size(168f, 174f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.cardWhite, topLeft = Offset(208f, 26f), size = Size(168f, 174f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.decoMid, topLeft = Offset(208f, 26f), size = Size(168f, 174f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 2f))

                    drawPath(paths.panelHeader, color = p.canvasBg)
                    drawLine(color = p.decoLight, start = Offset(210f, 71f), end = Offset(374f, 71f), strokeWidth = 1.5f)

                    val borderGlowAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(31f, 0f, TipEaseOutQuart), TipKf(36f, 1f), TipKf(75f, 1f, TipEaseOutQuart), TipKf(88f, 0f), TipKf(100f, 0f))
                    if (borderGlowAlpha > 0.01f) {
                        drawRoundRect(color = p.skyBlue.copy(alpha = borderGlowAlpha), topLeft = Offset(208f, 26f), size = Size(168f, 174f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 2.5f))
                    }

                    drawCircle(color = p.decoStroke, radius = 3f, center = Offset(342f, 50f))
                    drawCircle(color = p.decoStroke, radius = 3f, center = Offset(353f, 50f))

                    // Group Pill Badge (268, 51)
                    val gbSx = evalTipKf(t, TipKf(0f, 1f), TipKf(31f, 1f, TipEaseOutBack), TipKf(34f, 0.9f, TipEaseOutBack), TipKf(40f, 1.13f, TipEaseOutBack), TipKf(46f, 1.04f), TipKf(75f, 1.04f, TipEaseOutBack), TipKf(87f, 1f), TipKf(100f, 1f))
                    val gbSy = evalTipKf(t, TipKf(0f, 1f), TipKf(31f, 1f, TipEaseOutBack), TipKf(34f, 0.88f, TipEaseOutBack), TipKf(40f, 1.15f, TipEaseOutBack), TipKf(46f, 1.04f), TipKf(75f, 1.04f, TipEaseOutBack), TipKf(87f, 1f), TipKf(100f, 1f))
                    val gbTy = evalTipKf(t, TipKf(0f, 0f), TipKf(31f, 0f, TipEaseOutBack), TipKf(34f, 2f, TipEaseOutBack), TipKf(40f, -2f, TipEaseOutBack), TipKf(46f, -1f), TipKf(75f, -1f, TipEaseOutBack), TipKf(87f, 0f), TipKf(100f, 0f))
                    val gbActiveAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(33f, 0f, TipEaseOutQuart), TipKf(39f, 1f), TipKf(75f, 1f, TipEaseOutQuart), TipKf(87f, 0f), TipKf(100f, 0f))

                    withTipPivot(268f, 51f, ty = gbTy, sx = gbSx, sy = gbSy) {
                        drawRoundRect(color = p.blueContainer, topLeft = Offset(220f, 37f), size = Size(102f, 26f), cornerRadius = CornerRadius(13f, 13f))
                        drawRoundRect(color = p.skyBlue, topLeft = Offset(220f, 37f), size = Size(102f, 26f), cornerRadius = CornerRadius(13f, 13f), style = Stroke(width = 1.5f))
                        if (gbActiveAlpha > 0.01f) {
                            drawRoundRect(color = p.brightBlue.copy(alpha = gbActiveAlpha), topLeft = Offset(220f, 37f), size = Size(102f, 26f), cornerRadius = CornerRadius(13f, 13f))
                        }

                        drawCircle(color = p.deepNavy, radius = 9f, center = Offset(233f, 50f))
                        drawPath(paths.groupBadgeBrackets, color = p.cardWarm, style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawCircle(color = p.goldSoft, radius = 1.6f, center = Offset(233f, 50f))

                        drawRoundRect(color = p.deepNavy, topLeft = Offset(247f, 46.5f), size = Size(46f, 7f), cornerRadius = CornerRadius(3.5f, 3.5f))
                        drawRoundRect(color = p.skyBlue, topLeft = Offset(299f, 46.5f), size = Size(14f, 7f), cornerRadius = CornerRadius(3.5f, 3.5f))
                        if (gbActiveAlpha > 0.01f) {
                            drawRoundRect(color = Color.White.copy(alpha = gbActiveAlpha), topLeft = Offset(247f, 46.5f), size = Size(46f, 7f), cornerRadius = CornerRadius(3.5f, 3.5f))
                            drawRoundRect(color = p.goldSoft.copy(alpha = gbActiveAlpha), topLeft = Offset(299f, 46.5f), size = Size(14f, 7f), cornerRadius = CornerRadius(3.5f, 3.5f))
                        }
                    }

                    // Structured Entry Card 1 (292, 104)
                    val c1Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(31f, 0f, TipEaseOutBack), TipKf(36f, 6f, TipEaseOutBack), TipKf(43f, -3f, TipEaseOutBack), TipKf(50f, 0f), TipKf(100f, 0f))
                    val c1Sx = evalTipKf(t, TipKf(0f, 1f), TipKf(31f, 1f, TipEaseOutBack), TipKf(36f, 0.93f, TipEaseOutBack), TipKf(43f, 1.03f, TipEaseOutBack), TipKf(50f, 1f), TipKf(100f, 1f))
                    val c1Sy = evalTipKf(t, TipKf(0f, 1f), TipKf(31f, 1f, TipEaseOutBack), TipKf(36f, 0.94f, TipEaseOutBack), TipKf(43f, 1.02f, TipEaseOutBack), TipKf(50f, 1f), TipKf(100f, 1f))
                    withTipPivot(292f, 104f, ty = c1Ty, sx = c1Sx, sy = c1Sy) {
                        drawRoundRect(color = p.decoStroke.copy(alpha = 0.45f), topLeft = Offset(220f, 81f), size = Size(144f, 50f), cornerRadius = CornerRadius(14f, 14f))
                        drawRoundRect(color = p.cardWarm, topLeft = Offset(220f, 78f), size = Size(144f, 50f), cornerRadius = CornerRadius(14f, 14f))
                        drawRoundRect(color = p.decoMid, topLeft = Offset(220f, 78f), size = Size(144f, 50f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.5f))

                        drawCircle(color = p.decoStroke, radius = 1.8f, center = Offset(275f, 99f))
                        drawCircle(color = p.decoStroke, radius = 1.8f, center = Offset(275f, 107f))

                        val sp1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(37f, 0f, TipEaseOutBack), TipKf(41f, 1f, TipEaseOutBack), TipKf(48f, 0f), TipKf(100f, 0f))
                        if (sp1Alpha > 0.01f) {
                            val sp1S = evalTipKf(t, TipKf(0f, 0.3f), TipKf(37f, 0.3f, TipEaseOutBack), TipKf(41f, 1.25f, TipEaseOutBack), TipKf(48f, 0.3f), TipKf(100f, 0.3f))
                            withTipPivot(275f, 104f, sx = sp1S) {
                                drawPath(paths.card1Spark, color = p.goldStrong.copy(alpha = sp1Alpha))
                            }
                        }

                        // Card 1 Left Character Box (247, 104)
                        val c1CharTx = evalTipKf(t, TipKf(0f, 0f), TipKf(31f, 0f, TipEaseOutBack), TipKf(36f, 22f), TipKf(38f, 22f, TipEaseOutBack), TipKf(44f, -5f, TipEaseOutBack), TipKf(51f, 0f), TipKf(100f, 0f))
                        val c1CharS = evalTipKf(t, TipKf(0f, 1f), TipKf(31f, 1f, TipEaseOutBack), TipKf(36f, 0.88f), TipKf(38f, 0.88f, TipEaseOutBack), TipKf(44f, 1.08f, TipEaseOutBack), TipKf(51f, 1f), TipKf(100f, 1f))
                        withTipPivot(247f, 104f, tx = c1CharTx, sx = c1CharS) {
                            drawRoundRect(color = p.blueContainer, topLeft = Offset(228f, 84f), size = Size(38f, 38f), cornerRadius = CornerRadius(10f, 10f))
                            drawRoundRect(color = p.skyBlue, topLeft = Offset(228f, 84f), size = Size(38f, 38f), cornerRadius = CornerRadius(10f, 10f), style = Stroke(width = 1.5f))
                            drawPath(paths.hanziWen, color = p.deepNavy, style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }

                        // Card 1 Right Phonetic Pill (319, 104)
                        val c1NoteTx = evalTipKf(t, TipKf(0f, 0f), TipKf(31f, 0f, TipEaseOutBack), TipKf(36f, -24f), TipKf(38f, -24f, TipEaseOutBack), TipKf(44f, 6f, TipEaseOutBack), TipKf(51f, 0f), TipKf(100f, 0f))
                        val c1NoteS = evalTipKf(t, TipKf(0f, 1f), TipKf(31f, 1f, TipEaseOutBack), TipKf(36f, 0.86f), TipKf(38f, 0.86f, TipEaseOutBack), TipKf(44f, 1.1f, TipEaseOutBack), TipKf(51f, 1f), TipKf(100f, 1f))
                        withTipPivot(319f, 104f, tx = c1NoteTx, sx = c1NoteS) {
                            val gb1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(33f, 0f, TipEaseOutQuart), TipKf(37f, 1f, TipEaseOutQuart), TipKf(45f, 0f), TipKf(100f, 0f))
                            if (gb1Alpha > 0.01f) {
                                val gb1Sx = evalTipKf(t, TipKf(0f, 0.92f), TipKf(33f, 0.92f, TipEaseOutQuart), TipKf(37f, 1f, TipEaseOutQuart), TipKf(45f, 1.18f), TipKf(100f, 0.92f))
                                val gb1Sy = evalTipKf(t, TipKf(0f, 0.9f), TipKf(33f, 0.9f, TipEaseOutQuart), TipKf(37f, 1f, TipEaseOutQuart), TipKf(45f, 1.15f), TipKf(100f, 0.9f))
                                withTipPivot(320f, 103f, sx = gb1Sx, sy = gb1Sy) {
                                    drawPath(paths.card1GhostParenL, color = p.goldStrong.copy(alpha = gb1Alpha), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
                                    drawPath(paths.card1GhostParenR, color = p.goldStrong.copy(alpha = gb1Alpha), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
                                }
                            }

                            drawRoundRect(color = p.goldSoft, topLeft = Offset(284f, 88f), size = Size(72f, 30f), cornerRadius = CornerRadius(15f, 15f))
                            drawRoundRect(color = p.goldStrong, topLeft = Offset(284f, 88f), size = Size(72f, 30f), cornerRadius = CornerRadius(15f, 15f), style = Stroke(width = 1.5f))
                            drawCircle(color = p.goldStrong, radius = 9f, center = Offset(298f, 103f))
                            drawPath(paths.card1PitchWave, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawRoundRect(color = p.deepNavy, topLeft = Offset(311f, 98f), size = Size(34f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))
                            drawRoundRect(color = p.slateNavy.copy(alpha = 0.75f), topLeft = Offset(311f, 106f), size = Size(22f, 4f), cornerRadius = CornerRadius(2f, 2f))
                        }
                    }

                    // Structured Entry Card 2 (292, 164)
                    val c2Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(36f, 0f, TipEaseOutBack), TipKf(41f, 6f, TipEaseOutBack), TipKf(48f, -3f, TipEaseOutBack), TipKf(55f, 0f), TipKf(100f, 0f))
                    val c2Sx = evalTipKf(t, TipKf(0f, 1f), TipKf(36f, 1f, TipEaseOutBack), TipKf(41f, 0.93f, TipEaseOutBack), TipKf(48f, 1.03f, TipEaseOutBack), TipKf(55f, 1f), TipKf(100f, 1f))
                    val c2Sy = evalTipKf(t, TipKf(0f, 1f), TipKf(36f, 1f, TipEaseOutBack), TipKf(41f, 0.94f, TipEaseOutBack), TipKf(48f, 1.02f, TipEaseOutBack), TipKf(55f, 1f), TipKf(100f, 1f))
                    withTipPivot(292f, 164f, ty = c2Ty, sx = c2Sx, sy = c2Sy) {
                        drawRoundRect(color = p.decoStroke.copy(alpha = 0.45f), topLeft = Offset(220f, 141f), size = Size(144f, 50f), cornerRadius = CornerRadius(14f, 14f))
                        drawRoundRect(color = p.cardWarm, topLeft = Offset(220f, 138f), size = Size(144f, 50f), cornerRadius = CornerRadius(14f, 14f))
                        drawRoundRect(color = p.decoMid, topLeft = Offset(220f, 138f), size = Size(144f, 50f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.5f))

                        drawCircle(color = p.decoStroke, radius = 1.8f, center = Offset(275f, 159f))
                        drawCircle(color = p.decoStroke, radius = 1.8f, center = Offset(275f, 167f))

                        val sp2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(42f, 0f, TipEaseOutBack), TipKf(46f, 1f, TipEaseOutBack), TipKf(53f, 0f), TipKf(100f, 0f))
                        if (sp2Alpha > 0.01f) {
                            val sp2S = evalTipKf(t, TipKf(0f, 0.3f), TipKf(42f, 0.3f, TipEaseOutBack), TipKf(46f, 1.25f, TipEaseOutBack), TipKf(53f, 0.3f), TipKf(100f, 0.3f))
                            withTipPivot(275f, 164f, sx = sp2S) {
                                drawPath(paths.card2Spark, color = p.greenMid.copy(alpha = sp2Alpha))
                            }
                        }

                        // Card 2 Left Character Box (247, 164)
                        val c2CharTx = evalTipKf(t, TipKf(0f, 0f), TipKf(36f, 0f, TipEaseOutBack), TipKf(41f, 22f), TipKf(43f, 22f, TipEaseOutBack), TipKf(49f, -5f, TipEaseOutBack), TipKf(56f, 0f), TipKf(100f, 0f))
                        val c2CharS = evalTipKf(t, TipKf(0f, 1f), TipKf(36f, 1f, TipEaseOutBack), TipKf(41f, 0.88f), TipKf(43f, 0.88f, TipEaseOutBack), TipKf(49f, 1.08f, TipEaseOutBack), TipKf(56f, 1f), TipKf(100f, 1f))
                        withTipPivot(247f, 164f, tx = c2CharTx, sx = c2CharS) {
                            drawRoundRect(color = p.blueContainer, topLeft = Offset(228f, 144f), size = Size(38f, 38f), cornerRadius = CornerRadius(10f, 10f))
                            drawRoundRect(color = p.skyBlue, topLeft = Offset(228f, 144f), size = Size(38f, 38f), cornerRadius = CornerRadius(10f, 10f), style = Stroke(width = 1.5f))
                            drawPath(paths.hanziYan, color = p.deepNavy, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }

                        // Card 2 Right Phonetic Pill (319, 164)
                        val c2NoteTx = evalTipKf(t, TipKf(0f, 0f), TipKf(36f, 0f, TipEaseOutBack), TipKf(41f, -24f), TipKf(43f, -24f, TipEaseOutBack), TipKf(49f, 6f, TipEaseOutBack), TipKf(56f, 0f), TipKf(100f, 0f))
                        val c2NoteS = evalTipKf(t, TipKf(0f, 1f), TipKf(36f, 1f, TipEaseOutBack), TipKf(41f, 0.86f), TipKf(43f, 0.86f, TipEaseOutBack), TipKf(49f, 1.1f, TipEaseOutBack), TipKf(56f, 1f), TipKf(100f, 1f))
                        withTipPivot(319f, 164f, tx = c2NoteTx, sx = c2NoteS) {
                            val gb2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(38f, 0f, TipEaseOutQuart), TipKf(42f, 1f, TipEaseOutQuart), TipKf(50f, 0f), TipKf(100f, 0f))
                            if (gb2Alpha > 0.01f) {
                                val gb2Sx = evalTipKf(t, TipKf(0f, 0.92f), TipKf(38f, 0.92f, TipEaseOutQuart), TipKf(42f, 1f, TipEaseOutQuart), TipKf(50f, 1.18f), TipKf(100f, 0.92f))
                                val gb2Sy = evalTipKf(t, TipKf(0f, 0.9f), TipKf(38f, 0.9f, TipEaseOutQuart), TipKf(42f, 1f, TipEaseOutQuart), TipKf(50f, 1.15f), TipKf(100f, 0.9f))
                                withTipPivot(320f, 163f, sx = gb2Sx, sy = gb2Sy) {
                                    drawPath(paths.card2GhostParenL, color = p.greenMid.copy(alpha = gb2Alpha), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
                                    drawPath(paths.card2GhostParenR, color = p.greenMid.copy(alpha = gb2Alpha), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
                                }
                            }

                            drawRoundRect(color = p.greenContainer, topLeft = Offset(284f, 148f), size = Size(72f, 30f), cornerRadius = CornerRadius(15f, 15f))
                            drawRoundRect(color = p.greenMid, topLeft = Offset(284f, 148f), size = Size(72f, 30f), cornerRadius = CornerRadius(15f, 15f), style = Stroke(width = 1.5f))
                            drawCircle(color = p.greenStrong, radius = 9f, center = Offset(298f, 163f))
                            drawPath(paths.card2PitchCurve, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round))
                            drawRoundRect(color = p.greenStrong, topLeft = Offset(311f, 158f), size = Size(36f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))
                            drawRoundRect(color = p.greenMid, topLeft = Offset(311f, 166f), size = Size(24f, 4f), cornerRadius = CornerRadius(2f, 2f))
                        }
                    }

                    // Top-Right Green Checkmark Success Badge (368, 34)
                    val sbAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(47f, 0f, TipEaseOutBack), TipKf(54f, 1f), TipKf(76f, 1f, TipEaseOutBack), TipKf(86f, 0f), TipKf(100f, 0f))
                    if (sbAlpha > 0.01f) {
                        val sbScale = evalTipKf(t, TipKf(0f, 0f), TipKf(47f, 0f, TipEaseOutBack), TipKf(54f, 1.24f, TipEaseOutBack), TipKf(59f, 0.94f, TipEaseOutBack), TipKf(64f, 1f), TipKf(76f, 1f, TipEaseOutBack), TipKf(86f, 0f), TipKf(100f, 0f))
                        val sbRot = evalTipKf(t, TipKf(0f, -35f), TipKf(47f, -35f, TipEaseOutBack), TipKf(54f, 8f, TipEaseOutBack), TipKf(59f, -3f, TipEaseOutBack), TipKf(64f, 0f), TipKf(76f, 0f, TipEaseOutBack), TipKf(86f, -35f), TipKf(100f, -35f))
                        withTipPivot(368f, 34f, sx = sbScale, rotDeg = sbRot) {
                            drawCircle(color = p.greenContainer.copy(alpha = sbAlpha), radius = 16f, center = Offset(368f, 34f))
                            drawCircle(color = p.greenStrong.copy(alpha = sbAlpha), radius = 12f, center = Offset(368f, 34f))
                            drawCircle(color = Color.White.copy(alpha = sbAlpha), radius = 12f, center = Offset(368f, 34f), style = Stroke(width = 2.2f))
                            drawPath(paths.successCheck, color = Color.White.copy(alpha = sbAlpha), style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
        }
    }
}
