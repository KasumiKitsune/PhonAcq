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

private object SharedWordlistPaths {
    val starLeft by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M42 108 C42 113, 44 115, 49 115 C44 115, 42 117, 42 122 C42 117, 40 115, 35 115 C40 115, 42 113, 42 108 Z")
    }
    val starRight by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M358 52 C358 56, 359.5 57.5, 363.5 57.5 C359.5 57.5, 358 59, 358 63 C358 59, 356.5 57.5, 352.5 57.5 C356.5 57.5, 358 56, 358 52 Z")
    }
    val connLeft by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 82 156 L 82 148 C 82 120, 184 134, 184 108")
    }
    val connMid by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 200 156 L 200 108")
    }
    val connRight by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 318 156 L 318 148 C 318 120, 216 134, 216 108")
    }
    val pulseMidTrack by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 200 184 L 200 72")
    }
    val pulseRightTrack by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 318 184 L 318 148 C 318 120, 216 134, 216 108 L 216 72")
    }
    val miniWen by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M97 46.8 V48 M94.2 48.2 H99.8 M98.6 48.2 C98 50.4, 96.5 51.8, 94.3 52.6 M95.4 49.2 C96.5 50.6, 98 51.8, 99.7 52.6")
    }
    val miniDing by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M94.3 65.8 H99.7 M97.2 65.8 V70.2 C97.2 70.8, 96.6 71, 95.8 70.6")
    }
    val branchLines by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M126 87.5 L123.5 90 M126 87.5 L128.5 90")
    }
    val spk2Shoulders by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M146.5 46 C146.5 43.8, 148 43, 150 43 C152 43, 153.5 43.8, 153.5 46")
    }
    val spk3Shoulders by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M146 46 C146 43.8, 147.5 43, 149.5 43 C151.5 43, 153 43.8, 153 46")
    }
    val hanziZi by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M162 65.2 V67.8 M154.8 70.6 V67.8 H169.2 V70.6 M157.5 72.2 H166.2 L162 75.6 V82.8 C162 84, 161 84.3, 159.6 83.6 M154.5 78.2 H169.5")
    }
    val pitchCurveBlue by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M182 81 C190 73, 198 73, 206 79 C212 83, 220 75, 228 71")
    }
    val pitchCurveGold by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M182 79 C191 83, 201 71, 212 74 C220 76, 226 69, 234 68")
    }
    val masterBlueCheck by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M238.2 67.2 L240.2 69.2 L244 65.2")
    }
    val chipLeftCheck by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M46.5 178.2 L50.2 182 L57.5 174.5")
    }
    val chipMidShoulders by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M163.5 184 C163.5 180.5, 166.2 179, 170 179 C173.8 179, 176.5 180.5, 176.5 184")
    }
    val chipMidCheck by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M164.5 178.2 L168.2 182 L175.5 174.5")
    }
    val chipRightShoulders by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M281.5 184 C281.5 180.5, 284.2 179, 288 179 C291.8 179, 294.5 180.5, 294.5 184")
    }
}

/**
 * 贴士 05｜一份字表可多人共用 (5.0s Seamless Loop)
 */
@Composable
internal fun TipIllustrationSharedWordlist(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val p = remember(colorScheme, isDark) { TipDynamicPalette(colorScheme, isDark) }
    val paths = SharedWordlistPaths

    val clipProgMaster = remember {
        Path().apply { addRoundRect(RoundRect(Rect(180f, 38f, 252f, 46f), CornerRadius(4f, 4f))) }
    }
    val clipProgChipMid = remember {
        Path().apply { addRoundRect(RoundRect(Rect(190f, 182f, 238f, 188f), CornerRadius(3f, 3f))) }
    }
    val clipProgChipRight = remember {
        Path().apply { addRoundRect(RoundRect(Rect(308f, 182f, 356f, 188f), CornerRadius(3f, 3f))) }
    }

    val cPulseShoot = TipBezierCurve(0.35f, 0f, 0.25f, 1f)
    val cFlipIn = TipBezierCurve(0.55f, 0f, 0.85f, 0.35f)
    val dash4 = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f) }
    val dash2 = remember { PathEffect.dashPathEffect(floatArrayOf(2f, 2f), 0f) }

    Canvas(modifier = modifier) {
        val contentW = 388f
        val contentH = 214f
        val scale = min(size.width / contentW, size.height / contentH)
        val offsetX = size.width / 2f - 200f * scale
        val offsetY = size.height / 2f - 113f * scale

        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // 1. Background decorative M3 shapes

                val bg1Tx = evalTipKf(t, TipKf(0f, 0f, TipEaseSmooth), TipKf(50f, 4f, TipEaseSmooth), TipKf(100f, 0f))
                val bg1Ty = evalTipKf(t, TipKf(0f, 0f, TipEaseSmooth), TipKf(50f, 3f, TipEaseSmooth), TipKf(100f, 0f))
                val bg1S = evalTipKf(t, TipKf(0f, 1f, TipEaseSmooth), TipKf(50f, 1.04f, TipEaseSmooth), TipKf(100f, 1f))
                withTipPivot(45f, 30f, tx = bg1Tx, ty = bg1Ty, sx = bg1S) {
                    drawCircle(color = p.decoLight, radius = 88f, center = Offset(45f, 30f))
                }

                val bg2Rot = evalTipKf(t, TipKf(0f, -18f, TipEaseSmooth), TipKf(50f, -15f, TipEaseSmooth), TipKf(100f, -18f))
                val bg2S = evalTipKf(t, TipKf(0f, 1f, TipEaseSmooth), TipKf(50f, 1.05f, TipEaseSmooth), TipKf(100f, 1f))
                withTipPivot(355f, 180f, rotDeg = bg2Rot, sx = bg2S) {
                    drawRoundRect(
                        color = p.decoMid.copy(alpha = 0.65f),
                        topLeft = Offset(265f, 105f),
                        size = Size(175f, 140f),
                        cornerRadius = CornerRadius(70f, 70f)
                    )
                }

                drawRoundRect(
                    color = p.decoLight.copy(alpha = 0.45f),
                    topLeft = Offset(44f, 14f),
                    size = Size(312f, 198f),
                    cornerRadius = CornerRadius(44f, 44f)
                )

                drawPath(paths.starLeft, color = p.decoStroke)
                drawPath(paths.starRight, color = p.shadowColor)
                drawCircle(color = p.decoStroke, radius = 3.5f, center = Offset(350f, 118f))
                drawCircle(color = p.decoStroke, radius = 3f, center = Offset(52f, 62f))

                // Shared state layers
                val chipMidDoneAlpha = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(33f, 0f, TipEaseOutQuart),
                    TipKf(38f, 1f), TipKf(77f, 1f, TipEaseStandard),
                    TipKf(85f, 0f), TipKf(100f, 0f)
                )
                val chipRightActiveAlpha = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(40f, 0f, TipEaseOutQuart),
                    TipKf(45f, 1f), TipKf(74f, 1f, TipEaseStandard),
                    TipKf(83f, 0f), TipKf(100f, 0f)
                )

                // 2. Connector Lines & Energy Pulses
                drawPath(paths.connLeft, color = p.greenMid.copy(alpha = 0.75f), style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = dash4))
                drawPath(paths.connMid, color = p.deepNavy.copy(alpha = 0.65f), style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = dash4))
                if (chipMidDoneAlpha > 0.01f) {
                    drawPath(paths.connMid, color = p.greenMid.copy(alpha = chipMidDoneAlpha), style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = dash4))
                }
                drawPath(paths.connRight, color = p.shadowColor, style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = dash4))
                if (chipRightActiveAlpha > 0.01f) {
                    drawPath(paths.connRight, color = p.goldStrong.copy(alpha = chipRightActiveAlpha), style = Stroke(width = 2f, cap = StrokeCap.Round, pathEffect = dash4))
                }

                // Act 1 Mid Blue Energy Pulse (5%..15%)
                val pulseMidAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(4.9f, 0f), TipKf(5f, 1f), TipKf(15f, 1f), TipKf(15.1f, 0f), TipKf(100f, 0f))
                if (pulseMidAlpha > 0.01f) {
                    val offset = evalTipKf(t, TipKf(0f, 0f), TipKf(5f, 0f, cPulseShoot), TipKf(15f, -90f), TipKf(100f, 0f))
                    val pulseEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 400f), offset)
                    drawPath(paths.pulseMidTrack, color = p.skyBlue.copy(alpha = pulseMidAlpha), style = Stroke(width = 8f, cap = StrokeCap.Round, pathEffect = pulseEffect))
                    drawPath(paths.pulseMidTrack, color = p.brightBlue.copy(alpha = pulseMidAlpha), style = Stroke(width = 3.5f, cap = StrokeCap.Round, pathEffect = pulseEffect))
                }

                // Act 2 Right Gold Energy Pulse (42.5%..52.5%)
                val pulseRightAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(42.4f, 0f), TipKf(42.5f, 1f), TipKf(52.5f, 1f), TipKf(52.6f, 0f), TipKf(100f, 0f))
                if (pulseRightAlpha > 0.01f) {
                    val offset = evalTipKf(t, TipKf(0f, 0f), TipKf(42.5f, 0f, cPulseShoot), TipKf(52.5f, -156f), TipKf(100f, 0f))
                    val pulseEffect = PathEffect.dashPathEffect(floatArrayOf(26f, 400f), offset)
                    drawPath(paths.pulseRightTrack, color = p.goldSoft.copy(alpha = pulseRightAlpha), style = Stroke(width = 8f, cap = StrokeCap.Round, pathEffect = pulseEffect))
                    drawPath(paths.pulseRightTrack, color = p.goldStrong.copy(alpha = pulseRightAlpha), style = Stroke(width = 3.5f, cap = StrokeCap.Round, pathEffect = pulseEffect))
                }

                // 3. Top Master Wordlist Card
                val mcTy = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(10f, 0f, TipEaseOutBack),
                    TipKf(13.5f, -3.5f, TipEaseOutQuart),
                    TipKf(19f, 0f), TipKf(45.5f, 0f, TipEaseSym),
                    TipKf(48.5f, 1.5f, TipEaseOutBack),
                    TipKf(52.5f, -4f, TipEaseOutQuart),
                    TipKf(58f, 0f), TipKf(83f, 0f, TipEaseStandard),
                    TipKf(86.5f, 1f, TipEaseOutBack),
                    TipKf(91f, 0f), TipKf(100f, 0f)
                )
                val mcSx = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(10f, 1f, TipEaseOutBack),
                    TipKf(13.5f, 1.015f, TipEaseOutQuart),
                    TipKf(19f, 1f), TipKf(45.5f, 1f, TipEaseSym),
                    TipKf(48.5f, 1.02f, TipEaseOutBack),
                    TipKf(52.5f, 0.99f, TipEaseOutQuart),
                    TipKf(58f, 1f), TipKf(83f, 1f, TipEaseStandard),
                    TipKf(86.5f, 1.01f, TipEaseOutBack),
                    TipKf(91f, 1f), TipKf(100f, 1f)
                )
                val mcSy = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(10f, 1f, TipEaseOutBack),
                    TipKf(13.5f, 1.02f, TipEaseOutQuart),
                    TipKf(19f, 1f), TipKf(45.5f, 1f, TipEaseSym),
                    TipKf(48.5f, 0.96f, TipEaseOutBack),
                    TipKf(52.5f, 1.03f, TipEaseOutQuart),
                    TipKf(58f, 1f), TipKf(83f, 1f, TipEaseStandard),
                    TipKf(86.5f, 0.98f, TipEaseOutBack),
                    TipKf(91f, 1f), TipKf(100f, 1f)
                )

                withTipPivot(200f, 65f, ty = mcTy, sx = mcSx, sy = mcSy) {
                    // Shadow & Base Card
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.45f), topLeft = Offset(68f, 26f), size = Size(264f, 86f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.cardWhite, topLeft = Offset(68f, 22f), size = Size(264f, 86f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.decoMid, topLeft = Offset(68f, 22f), size = Size(264f, 86f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 1.5f))

                    // Bottom Connector Dock
                    drawRoundRect(color = p.decoLight, topLeft = Offset(172f, 103f), size = Size(56f, 10f), cornerRadius = CornerRadius(5f, 5f))
                    drawRoundRect(color = p.cardWhite, topLeft = Offset(172f, 103f), size = Size(56f, 10f), cornerRadius = CornerRadius(5f, 5f), style = Stroke(width = 1.5f))
                    drawCircle(color = p.greenStrong, radius = 2.5f, center = Offset(184f, 108f))
                    drawCircle(color = p.brightBlue, radius = 2.5f, center = Offset(200f, 108f))
                    if (chipMidDoneAlpha > 0.01f) {
                        drawCircle(color = p.greenStrong.copy(alpha = chipMidDoneAlpha), radius = 2.5f, center = Offset(200f, 108f))
                    }
                    drawCircle(color = p.shadowColor, radius = 2.5f, center = Offset(216f, 108f))
                    if (chipRightActiveAlpha > 0.01f) {
                        drawCircle(color = p.goldStrong.copy(alpha = chipRightActiveAlpha), radius = 2.5f, center = Offset(216f, 108f))
                    }

                    // 3.1 Left Fixed Shared Wordlist Icon
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.35f), topLeft = Offset(82f, 36f), size = Size(50f, 62f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.deepNavy, topLeft = Offset(82f, 34f), size = Size(50f, 62f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.skyBlue, topLeft = Offset(95f, 30f), size = Size(24f, 7f), cornerRadius = CornerRadius(3.5f, 3.5f))
                    drawRoundRect(color = p.cardWhite, topLeft = Offset(88f, 40f), size = Size(38f, 49f), cornerRadius = CornerRadius(8f, 8f))

                    // Row 1
                    drawRoundRect(color = p.blueContainer, topLeft = Offset(92f, 45f), size = Size(10f, 10f), cornerRadius = CornerRadius(2.5f, 2.5f))
                    drawPath(paths.miniWen, color = p.brightBlue, style = Stroke(width = 1.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawRoundRect(color = p.deepNavy, topLeft = Offset(105f, 46.5f), size = Size(17f, 3f), cornerRadius = CornerRadius(1.5f, 1.5f))
                    drawRoundRect(color = p.skyBlue, topLeft = Offset(105f, 51.5f), size = Size(11f, 2.5f), cornerRadius = CornerRadius(1.25f, 1.25f))
                    drawLine(color = p.decoLight, start = Offset(92f, 59f), end = Offset(122f, 59f), strokeWidth = 1.2f, cap = StrokeCap.Round)

                    // Row 2
                    drawRoundRect(color = p.canvasBg, topLeft = Offset(92f, 63f), size = Size(10f, 10f), cornerRadius = CornerRadius(2.5f, 2.5f))
                    drawPath(paths.miniDing, color = p.slateNavy, style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawRoundRect(color = p.shadowColor, topLeft = Offset(105f, 64.5f), size = Size(15f, 3f), cornerRadius = CornerRadius(1.5f, 1.5f))
                    drawRoundRect(color = p.decoMid, topLeft = Offset(105f, 69.5f), size = Size(9f, 2.5f), cornerRadius = CornerRadius(1.25f, 1.25f))

                    // Row 3
                    drawRoundRect(color = p.canvasBg, topLeft = Offset(92f, 77f), size = Size(10f, 7f), cornerRadius = CornerRadius(2f, 2f))
                    drawRoundRect(color = p.decoStroke, topLeft = Offset(105f, 78.5f), size = Size(16f, 3f), cornerRadius = CornerRadius(1.5f, 1.5f))

                    // Shared Badge Pulse (126, 89)
                    val sbScale = evalTipKf(
                        t,
                        TipKf(0f, 1f), TipKf(11f, 1f),
                        TipKf(14.5f, 1.28f, TipEaseOutBack),
                        TipKf(20f, 1f), TipKf(46f, 1f),
                        TipKf(50.5f, 1.28f, TipEaseOutBack),
                        TipKf(58f, 1f), TipKf(100f, 1f)
                    )
                    withTipPivot(126f, 89f, sx = sbScale) {
                        drawCircle(color = p.brightBlue, radius = 8.5f, center = Offset(126f, 89f))
                        drawCircle(color = p.cardWhite, radius = 8.5f, center = Offset(126f, 89f), style = Stroke(width = 2f))
                        drawCircle(color = Color.White, radius = 1.4f, center = Offset(126f, 86.2f))
                        drawCircle(color = Color.White, radius = 1.4f, center = Offset(123f, 91.2f))
                        drawCircle(color = Color.White, radius = 1.4f, center = Offset(129f, 91.2f))
                        drawPath(paths.branchLines, color = Color.White, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
                    }

                    // 3.2 Session A (Blue Speaker 2 Workspace)
                    val sBlueAlpha = evalTipKf(
                        t,
                        TipKf(0f, 1f), TipKf(45.5f, 1f, cFlipIn),
                        TipKf(48.5f, 0f), TipKf(86f, 0f, TipEaseOutBack),
                        TipKf(91f, 1f), TipKf(100f, 1f)
                    )
                    if (sBlueAlpha > 0.01f) {
                        val sBlueTy = evalTipKf(
                            t,
                            TipKf(0f, 0f), TipKf(45.5f, 0f, cFlipIn),
                            TipKf(48.5f, 4f), TipKf(86f, 4f, TipEaseOutBack),
                            TipKf(91f, 0f), TipKf(100f, 0f)
                        )
                        val sBlueSy = evalTipKf(
                            t,
                            TipKf(0f, 1f), TipKf(45.5f, 1f, cFlipIn),
                            TipKf(48.5f, 0.05f), TipKf(86f, 0.05f, TipEaseOutBack),
                            TipKf(91f, 1f), TipKf(100f, 1f)
                        )
                        withTipPivot(230f, 65f, ty = sBlueTy, sx = 1f, sy = sBlueSy) {
                            drawRoundRect(color = p.blueContainer.copy(alpha = sBlueAlpha), topLeft = Offset(142f, 35f), size = Size(32f, 14f), cornerRadius = CornerRadius(7f, 7f))
                            drawCircle(color = p.brightBlue.copy(alpha = sBlueAlpha), radius = 2.2f, center = Offset(150f, 40f))
                            drawPath(paths.spk2Shoulders, color = p.brightBlue.copy(alpha = sBlueAlpha), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                            drawRoundRect(color = p.brightBlue.copy(alpha = sBlueAlpha), topLeft = Offset(160f, 39f), size = Size(2.2f, 6f), cornerRadius = CornerRadius(1.1f, 1.1f))
                            drawRoundRect(color = p.brightBlue.copy(alpha = sBlueAlpha), topLeft = Offset(164f, 39f), size = Size(2.2f, 6f), cornerRadius = CornerRadius(1.1f, 1.1f))

                            // Progress bar
                            drawRoundRect(color = p.decoLight.copy(alpha = sBlueAlpha), topLeft = Offset(180f, 38f), size = Size(72f, 8f), cornerRadius = CornerRadius(4f, 4f))
                            val progBlueTx = evalTipKf(t, TipKf(0f, -42f), TipKf(11f, -42f, TipEaseSpringIn), TipKf(31f, 0f), TipKf(48f, 0f), TipKf(50f, -42f), TipKf(100f, -42f))
                            clipPath(clipProgMaster) {
                                drawRoundRect(color = p.brightBlue.copy(alpha = sBlueAlpha), topLeft = Offset(180f + progBlueTx, 38f), size = Size(72f, 8f), cornerRadius = CornerRadius(4f, 4f))
                            }

                            // Word & Pitch Card
                            drawRoundRect(color = p.canvasBg.copy(alpha = sBlueAlpha), topLeft = Offset(142f, 55f), size = Size(110f, 40f), cornerRadius = CornerRadius(11f, 11f))
                            drawRoundRect(color = p.cardWhite.copy(alpha = sBlueAlpha), topLeft = Offset(148f, 61f), size = Size(28f, 28f), cornerRadius = CornerRadius(8f, 8f))
                            drawRoundRect(color = p.decoMid.copy(alpha = sBlueAlpha), topLeft = Offset(148f, 61f), size = Size(28f, 28f), cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 1f))
                            drawPath(paths.hanziZi, color = p.deepNavy.copy(alpha = sBlueAlpha), style = Stroke(width = 1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                            drawRoundRect(color = p.deepNavy.copy(alpha = 0.8f * sBlueAlpha), topLeft = Offset(182f, 63f), size = Size(38f, 5.5f), cornerRadius = CornerRadius(2.75f, 2.75f))
                            drawLine(color = p.decoMid.copy(alpha = sBlueAlpha), start = Offset(182f, 73f), end = Offset(236f, 73f), strokeWidth = 1f, cap = StrokeCap.Round, pathEffect = dash2)
                            drawLine(color = p.decoMid.copy(alpha = sBlueAlpha), start = Offset(182f, 83f), end = Offset(236f, 83f), strokeWidth = 1f, cap = StrokeCap.Round, pathEffect = dash2)
                            drawPath(paths.pitchCurveBlue, color = p.skyBlue.copy(alpha = sBlueAlpha), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
                            drawCircle(color = p.brightBlue.copy(alpha = sBlueAlpha), radius = 2.2f, center = Offset(228f, 71f))

                            // Master Blue Check pop
                            val mbCheckAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(29f, 0f, TipEaseOutBack), TipKf(34f, 1f), TipKf(47f, 1f), TipKf(49f, 0f), TipKf(100f, 0f)) * sBlueAlpha
                            if (mbCheckAlpha > 0.01f) {
                                val mbCheckScale = evalTipKf(t, TipKf(0f, 0.2f), TipKf(29f, 0.2f, TipEaseOutBack), TipKf(34f, 1f), TipKf(47f, 1f), TipKf(49f, 0.2f), TipKf(100f, 0.2f))
                                withTipPivot(241f, 67f, sx = mbCheckScale) {
                                    drawCircle(color = p.greenStrong.copy(alpha = mbCheckAlpha), radius = 6.5f, center = Offset(241f, 67f))
                                    drawPath(paths.masterBlueCheck, color = Color.White.copy(alpha = mbCheckAlpha), style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }

                            // Mini Soundwaves Box (Blue)
                            drawRoundRect(color = p.canvasBg.copy(alpha = sBlueAlpha), topLeft = Offset(260f, 35f), size = Size(58f, 60f), cornerRadius = CornerRadius(14f, 14f))
                            drawLine(color = p.decoMid.copy(alpha = sBlueAlpha), start = Offset(267f, 66f), end = Offset(311f, 66f), strokeWidth = 1f, cap = StrokeCap.Round)
                            drawCircle(color = p.redAlert.copy(alpha = 0.22f * sBlueAlpha), radius = 4.5f, center = Offset(309f, 43f))
                            drawCircle(color = p.redAlert.copy(alpha = sBlueAlpha), radius = 2.5f, center = Offset(309f, 43f))

                            val wb1 = evalTipKf(t, TipKf(0f, 1f), TipKf(11f, 1f), TipKf(15f, 1.9f), TipKf(19f, 0.6f), TipKf(23f, 2.2f), TipKf(28f, 0.75f), TipKf(33f, 1f), TipKf(100f, 1f))
                            val wb2 = evalTipKf(t, TipKf(0f, 1f), TipKf(11f, 1f), TipKf(14f, 0.55f), TipKf(18f, 1.55f), TipKf(22f, 0.7f), TipKf(26f, 1.45f), TipKf(30f, 0.8f), TipKf(33f, 1f), TipKf(100f, 1f))
                            val wb3 = evalTipKf(t, TipKf(0f, 1f), TipKf(11f, 1f), TipKf(15f, 1.3f), TipKf(19.5f, 0.5f), TipKf(24f, 1.35f), TipKf(28.5f, 0.65f), TipKf(33f, 1f), TipKf(100f, 1f))
                            val wb4 = evalTipKf(t, TipKf(0f, 1f), TipKf(11f, 1f), TipKf(16f, 0.6f), TipKf(20f, 1.7f), TipKf(25f, 0.65f), TipKf(29f, 1.5f), TipKf(33f, 1f), TipKf(100f, 1f))
                            val wb5 = evalTipKf(t, TipKf(0f, 1f), TipKf(11f, 1f), TipKf(14.5f, 2.1f), TipKf(19f, 0.7f), TipKf(23.5f, 1.8f), TipKf(28f, 1.2f), TipKf(33f, 1f), TipKf(100f, 1f))

                            withTipPivot(273f, 66f, sx = 1f, sy = wb1) { drawRoundRect(color = p.skyBlue.copy(alpha = sBlueAlpha), topLeft = Offset(271f, 59f), size = Size(4f, 14f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(281.5f, 66f, sx = 1f, sy = wb2) { drawRoundRect(color = p.brightBlue.copy(alpha = sBlueAlpha), topLeft = Offset(279.5f, 54f), size = Size(4f, 24f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(290f, 66f, sx = 1f, sy = wb3) { drawRoundRect(color = p.brightBlue.copy(alpha = sBlueAlpha), topLeft = Offset(288f, 50f), size = Size(4f, 32f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(298.5f, 66f, sx = 1f, sy = wb4) { drawRoundRect(color = p.brightBlue.copy(alpha = sBlueAlpha), topLeft = Offset(296.5f, 56f), size = Size(4f, 20f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(307f, 66f, sx = 1f, sy = wb5) { drawRoundRect(color = p.skyBlue.copy(alpha = sBlueAlpha), topLeft = Offset(305f, 60f), size = Size(4f, 12f), cornerRadius = CornerRadius(2f, 2f)) }
                        }
                    }

                    // 3.3 Session B (Gold/Green Speaker 3 Workspace)
                    val sGoldAlpha = evalTipKf(
                        t,
                        TipKf(0f, 0f), TipKf(48f, 0f, TipEaseOutBack),
                        TipKf(53f, 1f), TipKf(83f, 1f, cFlipIn),
                        TipKf(86.5f, 0f), TipKf(100f, 0f)
                    )
                    if (sGoldAlpha > 0.01f) {
                        val sGoldTy = evalTipKf(
                            t,
                            TipKf(0f, -4f), TipKf(48f, -4f, TipEaseOutBack),
                            TipKf(53f, 0f), TipKf(83f, 0f, cFlipIn),
                            TipKf(86.5f, 4f), TipKf(100f, 4f)
                        )
                        val sGoldSy = evalTipKf(
                            t,
                            TipKf(0f, 0.05f), TipKf(48f, 0.05f, TipEaseOutBack),
                            TipKf(53f, 1f), TipKf(83f, 1f, cFlipIn),
                            TipKf(86.5f, 0.05f), TipKf(100f, 0.05f)
                        )
                        withTipPivot(230f, 65f, ty = sGoldTy, sx = 1f, sy = sGoldSy) {
                            drawRoundRect(color = p.goldSoft.copy(alpha = 0.65f * sGoldAlpha), topLeft = Offset(142f, 35f), size = Size(34f, 14f), cornerRadius = CornerRadius(7f, 7f))
                            drawCircle(color = p.deepNavy.copy(alpha = sGoldAlpha), radius = 2.2f, center = Offset(149.5f, 40f))
                            drawPath(paths.spk3Shoulders, color = p.deepNavy.copy(alpha = sGoldAlpha), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                            drawRoundRect(color = p.deepNavy.copy(alpha = sGoldAlpha), topLeft = Offset(158f, 39f), size = Size(2f, 6f), cornerRadius = CornerRadius(1f, 1f))
                            drawRoundRect(color = p.deepNavy.copy(alpha = sGoldAlpha), topLeft = Offset(162f, 39f), size = Size(2f, 6f), cornerRadius = CornerRadius(1f, 1f))
                            drawRoundRect(color = p.deepNavy.copy(alpha = sGoldAlpha), topLeft = Offset(166f, 39f), size = Size(2f, 6f), cornerRadius = CornerRadius(1f, 1f))

                            // Progress bar
                            drawRoundRect(color = p.slotWarm.copy(alpha = sGoldAlpha), topLeft = Offset(180f, 38f), size = Size(72f, 8f), cornerRadius = CornerRadius(4f, 4f))
                            val progGoldTx = evalTipKf(t, TipKf(0f, -66f), TipKf(51f, -66f, TipEaseSpringIn), TipKf(72f, -20f), TipKf(85f, -20f), TipKf(88f, -66f), TipKf(100f, -66f))
                            clipPath(clipProgMaster) {
                                drawRoundRect(color = p.goldStrong.copy(alpha = sGoldAlpha), topLeft = Offset(180f + progGoldTx, 38f), size = Size(72f, 8f), cornerRadius = CornerRadius(4f, 4f))
                            }

                            // Word & Pitch Card
                            drawRoundRect(color = p.slotWarm.copy(alpha = sGoldAlpha), topLeft = Offset(142f, 55f), size = Size(110f, 40f), cornerRadius = CornerRadius(11f, 11f))
                            drawRoundRect(color = p.cardWhite.copy(alpha = sGoldAlpha), topLeft = Offset(148f, 61f), size = Size(28f, 28f), cornerRadius = CornerRadius(8f, 8f))
                            drawRoundRect(color = p.goldStrong.copy(alpha = 0.35f * sGoldAlpha), topLeft = Offset(148f, 61f), size = Size(28f, 28f), cornerRadius = CornerRadius(8f, 8f), style = Stroke(width = 1f))
                            drawPath(paths.hanziZi, color = p.deepNavy.copy(alpha = sGoldAlpha), style = Stroke(width = 1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                            drawRoundRect(color = p.goldStrong.copy(alpha = sGoldAlpha), topLeft = Offset(182f, 63f), size = Size(34f, 5.5f), cornerRadius = CornerRadius(2.75f, 2.75f))
                            drawLine(color = p.borderWarm.copy(alpha = sGoldAlpha), start = Offset(182f, 73f), end = Offset(236f, 73f), strokeWidth = 1f, cap = StrokeCap.Round, pathEffect = dash2)
                            drawLine(color = p.borderWarm.copy(alpha = sGoldAlpha), start = Offset(182f, 83f), end = Offset(236f, 83f), strokeWidth = 1f, cap = StrokeCap.Round, pathEffect = dash2)
                            drawPath(paths.pitchCurveGold, color = p.greenMid.copy(alpha = sGoldAlpha), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
                            drawCircle(color = p.greenStrong.copy(alpha = sGoldAlpha), radius = 2.2f, center = Offset(234f, 68f))

                            // Mini Soundwaves Box (Gold/Green)
                            drawRoundRect(color = p.slotWarm.copy(alpha = sGoldAlpha), topLeft = Offset(260f, 35f), size = Size(58f, 60f), cornerRadius = CornerRadius(14f, 14f))
                            drawLine(color = p.borderWarm.copy(alpha = sGoldAlpha), start = Offset(267f, 66f), end = Offset(311f, 66f), strokeWidth = 1f, cap = StrokeCap.Round)
                            drawCircle(color = p.redAlert.copy(alpha = 0.22f * sGoldAlpha), radius = 4.5f, center = Offset(309f, 43f))
                            drawCircle(color = p.redAlert.copy(alpha = sGoldAlpha), radius = 2.5f, center = Offset(309f, 43f))

                            val wg1 = evalTipKf(t, TipKf(0f, 1f), TipKf(51f, 1f), TipKf(55f, 2.0f), TipKf(59f, 0.65f), TipKf(64f, 2.25f), TipKf(69f, 0.8f), TipKf(74f, 1f), TipKf(100f, 1f))
                            val wg2 = evalTipKf(t, TipKf(0f, 1f), TipKf(51f, 1f), TipKf(54f, 0.6f), TipKf(58.5f, 1.5f), TipKf(63f, 0.65f), TipKf(67.5f, 1.6f), TipKf(71f, 0.85f), TipKf(74f, 1f), TipKf(100f, 1f))
                            val wg3 = evalTipKf(t, TipKf(0f, 1f), TipKf(51f, 1f), TipKf(55.5f, 1.35f), TipKf(60f, 0.5f), TipKf(65f, 1.3f), TipKf(69.5f, 0.65f), TipKf(74f, 1f), TipKf(100f, 1f))
                            val wg4 = evalTipKf(t, TipKf(0f, 1f), TipKf(51f, 1f), TipKf(56f, 0.6f), TipKf(60.5f, 1.75f), TipKf(65.5f, 0.7f), TipKf(70f, 1.55f), TipKf(74f, 1f), TipKf(100f, 1f))
                            val wg5 = evalTipKf(t, TipKf(0f, 1f), TipKf(51f, 1f), TipKf(54.5f, 2.15f), TipKf(59f, 0.75f), TipKf(64f, 1.9f), TipKf(68.5f, 1.3f), TipKf(74f, 1f), TipKf(100f, 1f))

                            withTipPivot(273f, 66f, sx = 1f, sy = wg1) { drawRoundRect(color = p.goldSoft.copy(alpha = sGoldAlpha), topLeft = Offset(271f, 59f), size = Size(4f, 14f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(281.5f, 66f, sx = 1f, sy = wg2) { drawRoundRect(color = p.goldStrong.copy(alpha = sGoldAlpha), topLeft = Offset(279.5f, 54f), size = Size(4f, 24f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(290f, 66f, sx = 1f, sy = wg3) { drawRoundRect(color = p.greenStrong.copy(alpha = sGoldAlpha), topLeft = Offset(288f, 50f), size = Size(4f, 32f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(298.5f, 66f, sx = 1f, sy = wg4) { drawRoundRect(color = p.goldStrong.copy(alpha = sGoldAlpha), topLeft = Offset(296.5f, 56f), size = Size(4f, 20f), cornerRadius = CornerRadius(2f, 2f)) }
                            withTipPivot(307f, 66f, sx = 1f, sy = wg5) { drawRoundRect(color = p.goldSoft.copy(alpha = sGoldAlpha), topLeft = Offset(305f, 60f), size = Size(4f, 12f), cornerRadius = CornerRadius(2f, 2f)) }
                        }
                    }
                }

                // 4. Bottom 3 Speaker Chips
                // 4.1 Left Chip (Speaker 1 - Completed)
                val clTy = evalTipKf(t, TipKf(0f, 0f), TipKf(34f, 0f), TipKf(39f, -2f, TipEaseOutBack), TipKf(46f, 0f), TipKf(100f, 0f))
                val clS = evalTipKf(t, TipKf(0f, 1f), TipKf(34f, 1f), TipKf(39f, 1.02f, TipEaseOutBack), TipKf(46f, 1f), TipKf(100f, 1f))
                withTipPivot(82f, 178f, ty = clTy, sx = clS) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.4f), topLeft = Offset(30f, 160f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.greenContainer, topLeft = Offset(30f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.greenMid, topLeft = Offset(30f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 1.5f))
                    drawCircle(color = p.greenStrong, radius = 3f, center = Offset(82f, 156f))
                    drawCircle(color = p.cardWhite, radius = 3f, center = Offset(82f, 156f), style = Stroke(width = 1.2f))

                    drawCircle(color = p.greenStrong, radius = 14f, center = Offset(52f, 178f))
                    drawPath(paths.chipLeftCheck, color = Color.White, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawRoundRect(color = p.greenStrong, topLeft = Offset(72f, 169f), size = Size(36f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    drawCircle(color = p.greenMid, radius = 3f, center = Offset(116f, 172f))
                    drawRoundRect(color = p.greenStrong.copy(alpha = 0.85f), topLeft = Offset(72f, 182f), size = Size(48f, 6f), cornerRadius = CornerRadius(3f, 3f))
                }

                // 4.2 Middle Chip (Speaker 2 - Active in Act 1 -> Completed in Act 2)
                val ripMidAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(4.5f, 0f), TipKf(6.5f, 0.75f, TipEaseOutExpo), TipKf(21f, 0f), TipKf(100f, 0f))
                if (ripMidAlpha > 0.01f) {
                    val ripMidSx = evalTipKf(t, TipKf(0f, 0.88f), TipKf(4.5f, 0.88f), TipKf(6.5f, 0.96f, TipEaseOutExpo), TipKf(21f, 1.32f), TipKf(100f, 0.88f))
                    val ripMidSy = evalTipKf(t, TipKf(0f, 0.88f), TipKf(4.5f, 0.88f), TipKf(6.5f, 0.96f, TipEaseOutExpo), TipKf(21f, 1.48f), TipKf(100f, 0.88f))
                    withTipPivot(200f, 178f, sx = ripMidSx, sy = ripMidSy) {
                        drawRoundRect(color = p.skyBlue.copy(alpha = ripMidAlpha), topLeft = Offset(148f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 2.5f))
                    }
                }

                val cmTy = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(2f, 0f, TipEaseSym),
                    TipKf(4.5f, 2.5f, TipEaseOutBack),
                    TipKf(10f, -8f, TipEaseOutQuart),
                    TipKf(14f, -6f), TipKf(33f, -6f, TipEaseStandard),
                    TipKf(38f, 1.5f, TipEaseOutBack),
                    TipKf(42f, 0f), TipKf(100f, 0f)
                )
                val cmSx = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(2f, 1f, TipEaseSym),
                    TipKf(4.5f, 1.03f, TipEaseOutBack),
                    TipKf(10f, 0.98f, TipEaseOutQuart),
                    TipKf(14f, 1.04f), TipKf(33f, 1.04f, TipEaseStandard),
                    TipKf(38f, 1.02f, TipEaseOutBack),
                    TipKf(42f, 1f), TipKf(100f, 1f)
                )
                val cmSy = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(2f, 1f, TipEaseSym),
                    TipKf(4.5f, 0.95f, TipEaseOutBack),
                    TipKf(10f, 1.06f, TipEaseOutQuart),
                    TipKf(14f, 1.04f), TipKf(33f, 1.04f, TipEaseStandard),
                    TipKf(38f, 0.97f, TipEaseOutBack),
                    TipKf(42f, 1f), TipKf(100f, 1f)
                )

                withTipPivot(200f, 178f, ty = cmTy, sx = cmSx, sy = cmSy) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.45f), topLeft = Offset(148f, 160f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.blueContainer, topLeft = Offset(148f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.brightBlue, topLeft = Offset(148f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 1.5f))

                    if (chipMidDoneAlpha > 0.01f) {
                        drawRoundRect(color = p.greenContainer.copy(alpha = chipMidDoneAlpha), topLeft = Offset(148f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                        drawRoundRect(color = p.greenStrong.copy(alpha = chipMidDoneAlpha), topLeft = Offset(148f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 1.5f))
                    }

                    drawCircle(color = p.brightBlue, radius = 3.2f, center = Offset(200f, 156f))
                    drawCircle(color = p.cardWhite, radius = 3.2f, center = Offset(200f, 156f), style = Stroke(width = 1.2f))
                    if (chipMidDoneAlpha > 0.01f) {
                        drawCircle(color = p.greenStrong.copy(alpha = chipMidDoneAlpha), radius = 3.2f, center = Offset(200f, 156f))
                        drawCircle(color = p.cardWhite.copy(alpha = chipMidDoneAlpha), radius = 3.2f, center = Offset(200f, 156f), style = Stroke(width = 1.2f))
                    }

                    // Avatar
                    drawCircle(color = p.brightBlue, radius = 14f, center = Offset(170f, 178f))
                    drawCircle(color = Color.White, radius = 3.5f, center = Offset(170f, 174.2f))
                    drawPath(paths.chipMidShoulders, color = Color.White, style = Stroke(width = 2.2f, cap = StrokeCap.Round))

                    // Pop Check Badge on Avatar
                    val cmCheckAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(33f, 0f, TipEaseOutBack), TipKf(39f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(85f, 0f), TipKf(100f, 0f))
                    if (cmCheckAlpha > 0.01f) {
                        val cmCheckScale = evalTipKf(t, TipKf(0f, 0.3f), TipKf(33f, 0.3f, TipEaseOutBack), TipKf(39f, 1f), TipKf(77f, 1f, TipEaseStandard), TipKf(85f, 0.3f), TipKf(100f, 0.3f))
                        val cmCheckRot = evalTipKf(t, TipKf(0f, -25f), TipKf(33f, -25f, TipEaseOutBack), TipKf(39f, 0f), TipKf(100f, 0f))
                        withTipPivot(170f, 178f, sx = cmCheckScale, rotDeg = cmCheckRot) {
                            drawCircle(color = p.greenStrong.copy(alpha = cmCheckAlpha), radius = 14f, center = Offset(170f, 178f))
                            drawPath(paths.chipMidCheck, color = Color.White.copy(alpha = cmCheckAlpha), style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }

                    drawRoundRect(color = p.deepNavy, topLeft = Offset(190f, 169f), size = Size(36f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    drawCircle(color = p.brightBlue, radius = 3f, center = Offset(234f, 172f))
                    if (chipMidDoneAlpha > 0.01f) {
                        drawRoundRect(color = p.greenStrong.copy(alpha = chipMidDoneAlpha), topLeft = Offset(190f, 169f), size = Size(36f, 6f), cornerRadius = CornerRadius(3f, 3f))
                        drawCircle(color = p.greenMid.copy(alpha = chipMidDoneAlpha), radius = 3f, center = Offset(234f, 172f))
                    }

                    drawRoundRect(color = p.cardWhite.copy(alpha = 0.75f), topLeft = Offset(190f, 182f), size = Size(48f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    val progMidTx = evalTipKf(t, TipKf(0f, -28f), TipKf(11f, -28f, TipEaseSpringIn), TipKf(31f, 0f), TipKf(78f, 0f, TipEaseStandard), TipKf(88f, -28f), TipKf(100f, -28f))
                    clipPath(clipProgChipMid) {
                        drawRoundRect(color = p.brightBlue, topLeft = Offset(190f + progMidTx, 182f), size = Size(48f, 6f), cornerRadius = CornerRadius(3f, 3f))
                        if (chipMidDoneAlpha > 0.01f) {
                            drawRoundRect(color = p.greenStrong.copy(alpha = chipMidDoneAlpha), topLeft = Offset(190f, 182f), size = Size(48f, 6f), cornerRadius = CornerRadius(3f, 3f))
                        }
                    }
                }

                // 4.3 Right Chip (Speaker 3 - Idle -> Active in Act 2)
                val ripRightAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(41.5f, 0f), TipKf(43.5f, 0.75f, TipEaseOutExpo), TipKf(58f, 0f), TipKf(100f, 0f))
                if (ripRightAlpha > 0.01f) {
                    val ripRightSx = evalTipKf(t, TipKf(0f, 0.88f), TipKf(41.5f, 0.88f), TipKf(43.5f, 0.96f, TipEaseOutExpo), TipKf(58f, 1.32f), TipKf(100f, 0.88f))
                    val ripRightSy = evalTipKf(t, TipKf(0f, 0.88f), TipKf(41.5f, 0.88f), TipKf(43.5f, 0.96f, TipEaseOutExpo), TipKf(58f, 1.48f), TipKf(100f, 0.88f))
                    withTipPivot(318f, 178f, sx = ripRightSx, sy = ripRightSy) {
                        drawRoundRect(color = p.goldStrong.copy(alpha = ripRightAlpha), topLeft = Offset(266f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 2.5f))
                    }
                }

                val crTy = evalTipKf(
                    t,
                    TipKf(0f, 0f), TipKf(39f, 0f, TipEaseSym),
                    TipKf(41.5f, 2.5f, TipEaseOutBack),
                    TipKf(47f, -8f, TipEaseOutQuart),
                    TipKf(51f, -6f), TipKf(74f, -6f, TipEaseStandard),
                    TipKf(79f, 1.5f, TipEaseOutBack),
                    TipKf(84f, 0f), TipKf(100f, 0f)
                )
                val crSx = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(39f, 1f, TipEaseSym),
                    TipKf(41.5f, 1.03f, TipEaseOutBack),
                    TipKf(47f, 0.98f, TipEaseOutQuart),
                    TipKf(51f, 1.04f), TipKf(74f, 1.04f, TipEaseStandard),
                    TipKf(79f, 1.02f, TipEaseOutBack),
                    TipKf(84f, 1f), TipKf(100f, 1f)
                )
                val crSy = evalTipKf(
                    t,
                    TipKf(0f, 1f), TipKf(39f, 1f, TipEaseSym),
                    TipKf(41.5f, 0.95f, TipEaseOutBack),
                    TipKf(47f, 1.06f, TipEaseOutQuart),
                    TipKf(51f, 1.04f), TipKf(74f, 1.04f, TipEaseStandard),
                    TipKf(79f, 0.97f, TipEaseOutBack),
                    TipKf(84f, 1f), TipKf(100f, 1f)
                )

                withTipPivot(318f, 178f, ty = crTy, sx = crSx, sy = crSy) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.4f), topLeft = Offset(266f, 160f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.cardWarm, topLeft = Offset(266f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                    drawRoundRect(color = p.decoStroke, topLeft = Offset(266f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 1.5f))

                    if (chipRightActiveAlpha > 0.01f) {
                        drawRoundRect(color = p.cardWarm.copy(alpha = chipRightActiveAlpha), topLeft = Offset(266f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f))
                        drawRoundRect(color = p.goldStrong.copy(alpha = chipRightActiveAlpha), topLeft = Offset(266f, 156f), size = Size(104f, 44f), cornerRadius = CornerRadius(22f, 22f), style = Stroke(width = 2f))
                    }

                    drawCircle(color = p.shadowColor, radius = 3f, center = Offset(318f, 156f))
                    drawCircle(color = p.cardWhite, radius = 3f, center = Offset(318f, 156f), style = Stroke(width = 1.2f))
                    if (chipRightActiveAlpha > 0.01f) {
                        drawCircle(color = p.goldStrong.copy(alpha = chipRightActiveAlpha), radius = 3f, center = Offset(318f, 156f))
                        drawCircle(color = p.cardWhite.copy(alpha = chipRightActiveAlpha), radius = 3f, center = Offset(318f, 156f), style = Stroke(width = 1.2f))
                    }

                    // Avatar idle
                    drawCircle(color = p.decoMid, radius = 14f, center = Offset(288f, 178f))
                    drawCircle(color = p.slateNavy, radius = 3.5f, center = Offset(288f, 174.2f))
                    drawPath(paths.chipRightShoulders, color = p.slateNavy, style = Stroke(width = 2.2f, cap = StrokeCap.Round))

                    // Avatar active gold overlay
                    if (chipRightActiveAlpha > 0.01f) {
                        drawCircle(color = p.goldStrong.copy(alpha = chipRightActiveAlpha), radius = 14f, center = Offset(288f, 178f))
                        drawCircle(color = Color.White.copy(alpha = chipRightActiveAlpha), radius = 3.5f, center = Offset(288f, 174.2f))
                        drawPath(paths.chipRightShoulders, color = Color.White.copy(alpha = chipRightActiveAlpha), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
                    }

                    drawRoundRect(color = p.slateNavy.copy(alpha = 0.65f), topLeft = Offset(308f, 169f), size = Size(36f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    drawCircle(color = p.decoStroke, radius = 3f, center = Offset(352f, 172f))
                    if (chipRightActiveAlpha > 0.01f) {
                        drawCircle(color = p.goldStrong.copy(alpha = chipRightActiveAlpha), radius = 3f, center = Offset(352f, 172f))
                    }

                    drawRoundRect(color = p.decoLight, topLeft = Offset(308f, 182f), size = Size(48f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    val progRightTx = evalTipKf(t, TipKf(0f, -48f), TipKf(49f, -48f, TipEaseSpringIn), TipKf(71f, -13f), TipKf(75f, -13f, TipEaseStandard), TipKf(84f, -48f), TipKf(100f, -48f))
                    clipPath(clipProgChipRight) {
                        drawRoundRect(color = p.goldStrong, topLeft = Offset(308f + progRightTx, 182f), size = Size(48f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    }
                }
        }
    }
}
