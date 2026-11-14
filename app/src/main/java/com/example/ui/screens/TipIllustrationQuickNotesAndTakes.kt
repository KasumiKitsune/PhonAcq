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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.min

private object QuickNotesAndTakesPaths {
    val hanziWen by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 67 49 L 69 53.5 M 54 57 H 82 M 75 57 C 72.5 66.5 66 73.5 55 78 M 60 61 C 65.5 68.5 72.5 74.5 81 78")
    }
    val pencilIcon by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 50 121 L 50 117.5 L 56.5 111 C 57.3 110.2 58.5 110.2 59.3 111 L 60 111.7 C 60.8 112.5 60.8 113.7 60 114.5 L 53.5 121 H 50 Z")
    }
    val slot55 by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath(
            "M 81 114 H 76.5 V 117.5 H 79.2 C 80.6 117.5 81.5 118.3 81.5 119.8 C 81.5 121.2 80.5 122 78.8 122 H 76.2 " +
                "M 90 114 H 85.5 V 117.5 H 88.2 C 89.6 117.5 90.5 118.3 90.5 119.8 C 90.5 121.2 89.5 122 87.8 122 H 85.2"
        )
    }
    val chip55Glyph by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath(
            "M 51.5 168.5 H 46.5 V 172.2 H 49.5 C 51 172.2 52 173.2 52 174.8 C 52 176.4 50.8 177.5 49 177.5 H 46.2 " +
                "M 61.5 168.5 H 56.5 V 172.2 H 59.5 C 61 172.2 62 173.2 62 174.8 C 62 176.4 60.8 177.5 59 177.5 H 56.2"
        )
    }
    val chip35Glyph by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath(
            "M 96.5 168.8 H 101.2 L 98.8 172.2 C 100.6 172.2 101.6 173.3 101.6 174.8 C 101.6 176.4 100.4 177.5 98.5 177.5 C 97.3 177.5 96.4 177 96 176.2 " +
                "M 111 168.5 H 106 V 172.2 H 109 C 110.5 172.2 111.5 173.2 111.5 174.8 C 111.5 176.4 110.3 177.5 108.5 177.5 H 105.8"
        )
    }

    val take1Play by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 241 58.2 L 247.2 62 L 241 65.8 Z") }
    val take1Num by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 259.5 59 L 261.5 57.5 V 66.5 M 259.2 66.5 H 263.8") }

    val take3Play by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 241 158.2 L 247.2 162 L 241 165.8 Z") }
    val take3Num by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 258.5 158 H 263.5 L 260.8 161.5 C 262.8 161.5 264 162.6 264 164.2 C 264 165.8 262.6 166.8 260.8 166.8 C 259.3 166.8 258.3 166.2 257.8 165.2")
    }

    val take2Play by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 241 108 L 247.5 112 L 241 116 Z") }
    val take2Num by lazy(LazyThreadSafetyMode.NONE) {
        parseTipSvgPath("M 258.5 109.5 C 258.5 107.8 259.8 107 261.5 107 C 263.2 107 264.3 108 264.3 109.5 C 264.3 110.8 263.2 112 258.5 116.2 H 264.6")
    }
    val bestCheck by lazy(LazyThreadSafetyMode.NONE) { parseTipSvgPath("M 345.6 112.4 L 348.6 115.4 L 354.6 108.8") }
}

/**
 * 贴士 09｜随手记音与多遍择优 (5.2s Seamless Loop)
 */
@Composable
internal fun TipIllustrationQuickNotesAndTakes(
    colorScheme: ColorScheme,
    isDark: Boolean,
    t: Float,
    modifier: Modifier = Modifier
) {
    val p = remember(colorScheme, isDark) { TipDynamicPalette(colorScheme, isDark) }
    val paths = QuickNotesAndTakesPaths

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
            drawCircle(color = p.decoLight.copy(alpha = 0.75f), radius = 76f, center = Offset(36f, 20f))

                withTipPivot(345f, 185f, rotDeg = -12f) {
                    drawRoundRect(color = p.decoMid.copy(alpha = 0.55f), topLeft = Offset(260f, 135f), size = Size(175f, 105f), cornerRadius = CornerRadius(52.5f, 52.5f))
                }
                withTipPivot(35f, 195f, rotDeg = 8f) {
                    drawRoundRect(color = p.decoLight.copy(alpha = 0.6f), topLeft = Offset(-20f, 158f), size = Size(115f, 75f), cornerRadius = CornerRadius(37.5f, 37.5f))
                }

                // Left & Right Half Trays
                drawRoundRect(color = p.decoLight.copy(alpha = 0.65f), topLeft = Offset(20f, 18f), size = Size(172f, 189f), cornerRadius = CornerRadius(24f, 24f))
                drawRoundRect(color = p.decoLight.copy(alpha = 0.65f), topLeft = Offset(204f, 18f), size = Size(176f, 189f), cornerRadius = CornerRadius(24f, 24f))

                // 2. Left Half: Quick Note Card & Chips
                drawRoundRect(color = p.shadowColor.copy(alpha = 0.45f), topLeft = Offset(32f, 32f), size = Size(148f, 116f), cornerRadius = CornerRadius(20f, 20f))
                drawRoundRect(color = p.cardWhite, topLeft = Offset(32f, 28f), size = Size(148f, 116f), cornerRadius = CornerRadius(20f, 20f))
                drawRoundRect(color = p.decoStroke, topLeft = Offset(32f, 28f), size = Size(148f, 116f), cornerRadius = CornerRadius(20f, 20f), style = Stroke(width = 1.5f))

                // Top-Left Hanzi Tile 「文」
                drawRoundRect(color = p.cardWarm, topLeft = Offset(44f, 40f), size = Size(48f, 48f), cornerRadius = CornerRadius(14f, 14f))
                drawRoundRect(color = p.decoMid, topLeft = Offset(44f, 40f), size = Size(48f, 48f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.4f))
                drawPath(paths.hanziWen, color = p.deepNavy, style = Stroke(width = 3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Top-Right Entry Badge & Phonetic Bars
                drawRoundRect(color = p.blueContainer, topLeft = Offset(102f, 42f), size = Size(30f, 13f), cornerRadius = CornerRadius(6.5f, 6.5f))
                drawCircle(color = p.brightBlue, radius = 2.5f, center = Offset(109f, 48.5f))
                drawRoundRect(color = p.brightBlue, topLeft = Offset(115f, 46.5f), size = Size(11f, 4f), cornerRadius = CornerRadius(2f, 2f))

                drawRoundRect(color = p.slateNavy, topLeft = Offset(102f, 64f), size = Size(40f, 7f), cornerRadius = CornerRadius(3.5f, 3.5f))
                val supMix = evalTipKf(t, TipKf(0f, 0f), TipKf(16f, 0f), TipKf(20f, 1f), TipKf(83f, 1f), TipKf(91f, 0f), TipKf(100f, 0f))
                val supScale = evalTipKf(t, TipKf(0f, 1f), TipKf(16f, 1f, TipEaseOutBack), TipKf(20f, 1.18f, TipEaseOutBack), TipKf(25f, 1f), TipKf(100f, 1f))
                withTipPivot(155f, 68f, sx = supScale) {
                    drawRoundRect(color = lerp(p.decoLight, p.brightBlue, supMix), topLeft = Offset(146f, 61f), size = Size(18f, 10f), cornerRadius = CornerRadius(5f, 5f))
                }
                drawRoundRect(color = p.shadowColor, topLeft = Offset(102f, 77f), size = Size(26f, 5f), cornerRadius = CornerRadius(2.5f, 2.5f))

                // Note Slot (106, 118)
                val nsSx = evalTipKf(t, TipKf(0f, 1f), TipKf(14f, 1f, TipEaseOutBack), TipKf(18f, 1.03f, TipEaseOutBack), TipKf(23f, 1.01f), TipKf(27f, 1.01f, TipEaseOutBack), TipKf(32f, 1.03f, TipEaseOutBack), TipKf(38f, 1.01f), TipKf(83f, 1.01f, TipEaseOutQuart), TipKf(91f, 1f), TipKf(100f, 1f))
                val nsSy = evalTipKf(t, TipKf(0f, 1f), TipKf(14f, 1f, TipEaseOutBack), TipKf(18f, 1.06f, TipEaseOutBack), TipKf(23f, 1.02f), TipKf(27f, 1.02f, TipEaseOutBack), TipKf(32f, 1.06f, TipEaseOutBack), TipKf(38f, 1.01f), TipKf(83f, 1.01f, TipEaseOutQuart), TipKf(91f, 1f), TipKf(100f, 1f))
                val nsFlash = evalTipKf(t, TipKf(0f, 0f), TipKf(14f, 0f), TipKf(17f, 1f), TipKf(22f, 1f), TipKf(25f, 0.25f), TipKf(28f, 0.25f), TipKf(31f, 1f), TipKf(37f, 1f), TipKf(42f, 0.25f), TipKf(82f, 0.25f), TipKf(91f, 0f), TipKf(100f, 0f))

                withTipPivot(106f, 118f, sx = nsSx, sy = nsSy) {
                    val slotFill = lerp(p.canvasBg, p.blueContainer, (nsFlash - 0.25f).coerceAtLeast(0f) / 0.75f)
                    val slotStroke = when {
                        nsFlash <= 0.25f -> lerp(p.decoMid, p.skyBlue, nsFlash / 0.25f)
                        else -> lerp(p.skyBlue, p.brightBlue, (nsFlash - 0.25f) / 0.75f)
                    }
                    drawRoundRect(color = slotFill, topLeft = Offset(42f, 100f), size = Size(128f, 34f), cornerRadius = CornerRadius(12f, 12f))
                    drawRoundRect(color = slotStroke, topLeft = Offset(42f, 100f), size = Size(128f, 34f), cornerRadius = CornerRadius(12f, 12f), style = Stroke(width = 1.6f))

                    drawPath(paths.pencilIcon, color = p.slateNavy, style = Stroke(width = 1.6f, join = StrokeJoin.Round))

                    val phAlpha = evalTipKf(t, TipKf(0f, 1f), TipKf(14f, 1f), TipKf(18f, 0f), TipKf(84f, 0f), TipKf(91f, 1f), TipKf(100f, 1f))
                    if (phAlpha > 0.01f) {
                        drawRoundRect(color = p.decoStroke.copy(alpha = phAlpha), topLeft = Offset(66f, 114f), size = Size(54f, 6f), cornerRadius = CornerRadius(3f, 3f))
                    }

                    // Tag [55] inside Slot (83, 118)
                    val t55Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(15f, 0f, TipEaseOutBack), TipKf(22f, 1f), TipKf(83f, 1f, TipEaseOutQuart), TipKf(90f, 0f), TipKf(100f, 0f))
                    if (t55Alpha > 0.01f) {
                        val t55Scale = evalTipKf(t, TipKf(0f, 0.4f), TipKf(15f, 0.4f, TipEaseOutBack), TipKf(22f, 1f), TipKf(83f, 1f, TipEaseOutQuart), TipKf(90f, 0.4f), TipKf(100f, 0.4f))
                        withTipPivot(83f, 118f, sx = t55Scale) {
                            drawRoundRect(color = p.blueContainer.copy(alpha = t55Alpha), topLeft = Offset(65f, 108.5f), size = Size(36f, 19f), cornerRadius = CornerRadius(9.5f, 9.5f))
                            drawRoundRect(color = p.skyBlue.copy(alpha = t55Alpha), topLeft = Offset(65f, 108.5f), size = Size(36f, 19f), cornerRadius = CornerRadius(9.5f, 9.5f), style = Stroke(width = 1.2f))
                            drawPath(paths.slot55, color = p.brightBlue.copy(alpha = t55Alpha), style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }

                    // Tag [老派] inside Slot (131, 118)
                    val tlpAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(29f, 0f, TipEaseOutBack), TipKf(36f, 1f), TipKf(83f, 1f, TipEaseOutQuart), TipKf(90f, 0f), TipKf(100f, 0f))
                    if (tlpAlpha > 0.01f) {
                        val tlpScale = evalTipKf(t, TipKf(0f, 0.4f), TipKf(29f, 0.4f, TipEaseOutBack), TipKf(36f, 1f), TipKf(83f, 1f, TipEaseOutQuart), TipKf(90f, 0.4f), TipKf(100f, 0.4f))
                        withTipPivot(131f, 118f, sx = tlpScale) {
                            drawRoundRect(color = p.goldSoft.copy(alpha = tlpAlpha), topLeft = Offset(106f, 108.5f), size = Size(50f, 19f), cornerRadius = CornerRadius(9.5f, 9.5f))
                            drawRoundRect(color = p.goldStrong.copy(alpha = tlpAlpha), topLeft = Offset(106f, 108.5f), size = Size(50f, 19f), cornerRadius = CornerRadius(9.5f, 9.5f), style = Stroke(width = 1.2f))
                            drawCircle(color = p.deepNavy.copy(alpha = tlpAlpha), radius = 3.2f, center = Offset(116f, 118f))
                            drawRoundRect(color = p.deepNavy.copy(alpha = tlpAlpha), topLeft = Offset(123f, 115f), size = Size(24f, 6f), cornerRadius = CornerRadius(3f, 3f))
                        }
                    }
                }

                // 2.2 Flying Stream Particles
                val st55Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(7.5f, 0f, TipEaseSpringIn), TipKf(8.5f, 0.95f, TipEaseSpringIn), TipKf(16.5f, 0.9f), TipKf(18f, 0f), TipKf(100f, 0f))
                if (st55Alpha > 0.01f) {
                    val st55Tx = evalTipKf(t, TipKf(0f, 0f), TipKf(7.5f, 0f, TipEaseSpringIn), TipKf(16.5f, 29f), TipKf(100f, 29f))
                    val st55Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(7.5f, 0f, TipEaseSpringIn), TipKf(16.5f, -55f), TipKf(100f, -55f))
                    val st55S = evalTipKf(t, TipKf(0f, 0.7f), TipKf(7.5f, 0.7f, TipEaseSpringIn), TipKf(16.5f, 0.9f), TipKf(18f, 0.4f), TipKf(100f, 0.4f))
                    withTipPivot(54f, 173f, tx = st55Tx, ty = st55Ty, sx = st55S) {
                        val blueBrush = Brush.linearGradient(
                            0f to p.skyBlue.copy(alpha = 0.2f * st55Alpha),
                            1f to p.brightBlue.copy(alpha = 0.95f * st55Alpha),
                            start = Offset(38f, 182f),
                            end = Offset(70f, 162f)
                        )
                        drawRoundRect(brush = blueBrush, topLeft = Offset(38f, 162f), size = Size(32f, 20f), cornerRadius = CornerRadius(10f, 10f))
                        drawCircle(color = Color.White.copy(alpha = st55Alpha), radius = 4f, center = Offset(54f, 172f))
                    }
                }

                val stLpAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(21.5f, 0f, TipEaseSpringIn), TipKf(22.5f, 0.95f, TipEaseSpringIn), TipKf(30.5f, 0.9f), TipKf(32f, 0f), TipKf(100f, 0f))
                if (stLpAlpha > 0.01f) {
                    val stLpTx = evalTipKf(t, TipKf(0f, 0f), TipKf(21.5f, 0f, TipEaseSpringIn), TipKf(30.5f, -26f), TipKf(100f, -26f))
                    val stLpTy = evalTipKf(t, TipKf(0f, 0f), TipKf(21.5f, 0f, TipEaseSpringIn), TipKf(30.5f, -55f), TipKf(100f, -55f))
                    val stLpS = evalTipKf(t, TipKf(0f, 0.7f), TipKf(21.5f, 0.7f, TipEaseSpringIn), TipKf(30.5f, 0.9f), TipKf(32f, 0.4f), TipKf(100f, 0.4f))
                    withTipPivot(156f, 173f, tx = stLpTx, ty = stLpTy, sx = stLpS) {
                        val goldBrush = Brush.linearGradient(
                            0f to p.goldSoft.copy(alpha = 0.2f * stLpAlpha),
                            1f to p.goldStrong.copy(alpha = 0.95f * stLpAlpha),
                            start = Offset(172f, 182f),
                            end = Offset(140f, 162f)
                        )
                        drawRoundRect(brush = goldBrush, topLeft = Offset(140f, 162f), size = Size(32f, 20f), cornerRadius = CornerRadius(10f, 10f))
                        drawCircle(color = Color.White.copy(alpha = stLpAlpha), radius = 4f, center = Offset(156f, 172f))
                    }
                }

                // 2.3 Bottom Quick Note Chips ([55] / [35] / [老派])
                val c55RipAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(6f, 0f, TipEaseOutExpo), TipKf(9f, 0.65f, TipEaseOutExpo), TipKf(21f, 0f), TipKf(100f, 0f))
                if (c55RipAlpha > 0.01f) {
                    val c55RipS = evalTipKf(t, TipKf(0f, 0.85f), TipKf(6f, 0.85f, TipEaseOutExpo), TipKf(9f, 1f, TipEaseOutExpo), TipKf(21f, 1.45f), TipKf(100f, 0.85f))
                    withTipPivot(54f, 173f, sx = c55RipS) {
                        drawRoundRect(color = p.skyBlue.copy(alpha = c55RipAlpha), topLeft = Offset(32f, 159f), size = Size(44f, 28f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2f))
                    }
                }

                val cLpRipAlpha = evalTipKf(t, TipKf(0f, 0f), TipKf(20f, 0f, TipEaseOutExpo), TipKf(23f, 0.65f, TipEaseOutExpo), TipKf(35f, 0f), TipKf(100f, 0f))
                if (cLpRipAlpha > 0.01f) {
                    val cLpRipS = evalTipKf(t, TipKf(0f, 0.85f), TipKf(20f, 0.85f, TipEaseOutExpo), TipKf(23f, 1f, TipEaseOutExpo), TipKf(35f, 1.45f), TipKf(100f, 0.85f))
                    withTipPivot(156f, 173f, sx = cLpRipS) {
                        drawRoundRect(color = p.goldStrong.copy(alpha = cLpRipAlpha), topLeft = Offset(132f, 159f), size = Size(48f, 28f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 2f))
                    }
                }

                // Chip 1: [55] (54, 173)
                val c55Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(4f, 0f, TipEaseOutBack), TipKf(7f, 3f, TipEaseOutBack), TipKf(12f, -4f, TipEaseOutBack), TipKf(18f, 0f), TipKf(100f, 0f))
                val c55Sx = evalTipKf(t, TipKf(0f, 1f), TipKf(4f, 1f, TipEaseOutBack), TipKf(7f, 0.88f, TipEaseOutBack), TipKf(12f, 1.06f, TipEaseOutBack), TipKf(18f, 1f), TipKf(100f, 1f))
                val c55Sy = evalTipKf(t, TipKf(0f, 1f), TipKf(4f, 1f, TipEaseOutBack), TipKf(7f, 0.84f, TipEaseOutBack), TipKf(12f, 1.08f, TipEaseOutBack), TipKf(18f, 1f), TipKf(100f, 1f))
                val c55Mix = evalTipKf(t, TipKf(0f, 0f), TipKf(5f, 0f), TipKf(8f, 1f), TipKf(38f, 1f), TipKf(44f, 0f), TipKf(100f, 0f))
                withTipPivot(54f, 173f, ty = c55Ty, sx = c55Sx, sy = c55Sy) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.42f), topLeft = Offset(32f, 161.5f), size = Size(44f, 28f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = lerp(p.cardWhite, p.blueContainer, c55Mix), topLeft = Offset(32f, 159f), size = Size(44f, 28f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = lerp(p.softBlue, p.brightBlue, c55Mix), topLeft = Offset(32f, 159f), size = Size(44f, 28f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.5f))
                    drawPath(paths.chip55Glyph, color = p.brightBlue, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // Chip 2: [35]
                drawRoundRect(color = p.shadowColor.copy(alpha = 0.42f), topLeft = Offset(82f, 161.5f), size = Size(44f, 28f), cornerRadius = CornerRadius(14f, 14f))
                drawRoundRect(color = p.cardWhite, topLeft = Offset(82f, 159f), size = Size(44f, 28f), cornerRadius = CornerRadius(14f, 14f))
                drawRoundRect(color = p.decoStroke, topLeft = Offset(82f, 159f), size = Size(44f, 28f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.5f))
                drawPath(paths.chip35Glyph, color = p.slateNavy, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Chip 3: [老派] (156, 173)
                val cLpTy = evalTipKf(t, TipKf(0f, 0f), TipKf(18f, 0f, TipEaseOutBack), TipKf(21f, 3f, TipEaseOutBack), TipKf(26f, -4f, TipEaseOutBack), TipKf(32f, 0f), TipKf(100f, 0f))
                val cLpSx = evalTipKf(t, TipKf(0f, 1f), TipKf(18f, 1f, TipEaseOutBack), TipKf(21f, 0.88f, TipEaseOutBack), TipKf(26f, 1.06f, TipEaseOutBack), TipKf(32f, 1f), TipKf(100f, 1f))
                val cLpSy = evalTipKf(t, TipKf(0f, 1f), TipKf(18f, 1f, TipEaseOutBack), TipKf(21f, 0.84f, TipEaseOutBack), TipKf(26f, 1.08f, TipEaseOutBack), TipKf(32f, 1f), TipKf(100f, 1f))
                val cLpMix = evalTipKf(t, TipKf(0f, 0f), TipKf(19f, 0f), TipKf(22f, 1f), TipKf(41f, 1f), TipKf(46f, 0f), TipKf(100f, 0f))
                withTipPivot(156f, 173f, ty = cLpTy, sx = cLpSx, sy = cLpSy) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.42f), topLeft = Offset(132f, 161.5f), size = Size(48f, 28f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = lerp(p.cardWarm, p.goldSoft, cLpMix), topLeft = Offset(132f, 159f), size = Size(48f, 28f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = lerp(p.goldStrong, p.deepNavy, cLpMix), topLeft = Offset(132f, 159f), size = Size(48f, 28f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.5f))
                    drawCircle(color = lerp(p.goldStrong, p.deepNavy, cLpMix), radius = 3.5f, center = Offset(143f, 173f))
                    drawRoundRect(color = p.deepNavy, topLeft = Offset(150f, 170f), size = Size(21f, 6f), cornerRadius = CornerRadius(3f, 3f))
                }

                // 3. Right Half: Multi-Take History Cards (Take 1 / Take 2 / Take 3)
                val sideTakeAlpha = evalTipKf(t, TipKf(0f, 0.9f), TipKf(43f, 0.9f, TipEaseOutBack), TipKf(52f, 0.52f), TipKf(80f, 0.52f, TipEaseOutQuart), TipKf(92f, 0.9f), TipKf(100f, 0.9f))
                val sideTakeScale = evalTipKf(t, TipKf(0f, 0.96f), TipKf(43f, 0.96f, TipEaseOutBack), TipKf(52f, 0.92f), TipKf(80f, 0.92f, TipEaseOutQuart), TipKf(92f, 0.96f), TipKf(100f, 0.96f))
                val sideTakeTx = evalTipKf(t, TipKf(0f, 0f), TipKf(43f, 0f, TipEaseOutBack), TipKf(52f, 3f), TipKf(80f, 3f, TipEaseOutQuart), TipKf(92f, 0f), TipKf(100f, 0f))

                // 3.1 Top Take Card: Take 1 (296, 63)
                val t1Ty = evalTipKf(t, TipKf(0f, 8f), TipKf(43f, 8f, TipEaseOutBack), TipKf(52f, -6f), TipKf(80f, -6f, TipEaseOutQuart), TipKf(92f, 8f), TipKf(100f, 8f))
                withTipPivot(296f, 63f, tx = sideTakeTx, ty = t1Ty, sx = sideTakeScale) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.4f * sideTakeAlpha), topLeft = Offset(224f, 44f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.cardWarm.copy(alpha = sideTakeAlpha), topLeft = Offset(224f, 40f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.decoMid.copy(alpha = sideTakeAlpha), topLeft = Offset(224f, 40f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.5f))

                    drawCircle(color = p.decoLight.copy(alpha = sideTakeAlpha), radius = 10.5f, center = Offset(243f, 62f))
                    drawPath(paths.take1Play, color = p.slateNavy.copy(alpha = sideTakeAlpha))
                    drawPath(paths.take1Play, color = p.slateNavy.copy(alpha = sideTakeAlpha), style = Stroke(width = 1f, join = StrokeJoin.Round))
                    drawPath(paths.take1Num, color = p.slateNavy.copy(alpha = sideTakeAlpha), style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    val t1Bars = listOf(57f to 10f, 54f to 16f, 58f to 8f, 53f to 18f, 56f to 12f, 55f to 14f, 58f to 8f, 56f to 12f)
                    t1Bars.forEachIndexed { idx, (y, h) ->
                        drawRoundRect(color = p.shadowColor.copy(alpha = sideTakeAlpha), topLeft = Offset(272f + idx * 7f, y), size = Size(3.2f, h), cornerRadius = CornerRadius(1.6f, 1.6f))
                    }
                    drawCircle(color = p.decoStroke.copy(alpha = sideTakeAlpha), radius = 8.5f, center = Offset(350f, 62f), style = Stroke(width = 1.6f))
                }

                // 3.2 Bottom Take Card: Take 3 (296, 161)
                val t3Ty = evalTipKf(t, TipKf(0f, -8f), TipKf(43f, -8f, TipEaseOutBack), TipKf(52f, 6f), TipKf(80f, 6f, TipEaseOutQuart), TipKf(92f, -8f), TipKf(100f, -8f))
                withTipPivot(296f, 161f, tx = sideTakeTx, ty = t3Ty, sx = sideTakeScale) {
                    drawRoundRect(color = p.shadowColor.copy(alpha = 0.4f * sideTakeAlpha), topLeft = Offset(224f, 144f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.cardWarm.copy(alpha = sideTakeAlpha), topLeft = Offset(224f, 140f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.decoMid.copy(alpha = sideTakeAlpha), topLeft = Offset(224f, 140f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f), style = Stroke(width = 1.5f))

                    drawCircle(color = p.decoLight.copy(alpha = sideTakeAlpha), radius = 10.5f, center = Offset(243f, 162f))
                    drawPath(paths.take3Play, color = p.slateNavy.copy(alpha = sideTakeAlpha))
                    drawPath(paths.take3Play, color = p.slateNavy.copy(alpha = sideTakeAlpha), style = Stroke(width = 1f, join = StrokeJoin.Round))
                    drawPath(paths.take3Num, color = p.slateNavy.copy(alpha = sideTakeAlpha), style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    val t3Bars = listOf(158f to 8f, 155f to 14f, 153f to 18f, 157f to 10f, 154f to 16f, 157f to 10f, 155f to 14f, 158f to 8f)
                    t3Bars.forEachIndexed { idx, (y, h) ->
                        drawRoundRect(color = p.shadowColor.copy(alpha = sideTakeAlpha), topLeft = Offset(272f + idx * 7f, y), size = Size(3.2f, h), cornerRadius = CornerRadius(1.6f, 1.6f))
                    }
                    drawCircle(color = p.decoStroke.copy(alpha = sideTakeAlpha), radius = 8.5f, center = Offset(350f, 162f), style = Stroke(width = 1.6f))
                }

                // 3.3 Middle Hero Take Card: Take 2 (296, 112)
                val t2Tx = evalTipKf(t, TipKf(0f, 0f), TipKf(43f, 0f, TipEaseOutBack), TipKf(46f, 1.5f, TipEaseOutBack), TipKf(55f, -5f), TipKf(80f, -5f, TipEaseOutQuart), TipKf(92f, 0f), TipKf(100f, 0f))
                val t2Ty = evalTipKf(t, TipKf(0f, 0f), TipKf(43f, 0f, TipEaseOutBack), TipKf(46f, 1.5f, TipEaseOutBack), TipKf(55f, -5f), TipKf(80f, -5f, TipEaseOutQuart), TipKf(92f, 0f), TipKf(100f, 0f))
                val t2Scale = evalTipKf(t, TipKf(0f, 1f), TipKf(43f, 1f, TipEaseOutBack), TipKf(46f, 0.97f, TipEaseOutBack), TipKf(55f, 1.12f), TipKf(80f, 1.12f, TipEaseOutQuart), TipKf(92f, 1f), TipKf(100f, 1f))
                val t2ActiveMix = evalTipKf(t, TipKf(0f, 0f), TipKf(45f, 0f), TipKf(52f, 1f), TipKf(80f, 1f), TipKf(91f, 0f), TipKf(100f, 0f))

                withTipPivot(296f, 112f, tx = t2Tx, ty = t2Ty, sx = t2Scale) {
                    drawRoundRect(color = p.deepNavy.copy(alpha = 0.16f), topLeft = Offset(224f, 95f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(color = p.cardWhite, topLeft = Offset(224f, 90f), size = Size(144f, 44f), cornerRadius = CornerRadius(14f, 14f))
                    drawRoundRect(
                        color = lerp(p.decoStroke, p.greenStrong, t2ActiveMix),
                        topLeft = Offset(224f, 90f),
                        size = Size(144f, 44f),
                        cornerRadius = CornerRadius(14f, 14f),
                        style = Stroke(width = lerpTipF(1.6f, 2f, t2ActiveMix))
                    )

                    drawCircle(color = lerp(p.blueContainer, p.greenStrong, t2ActiveMix), radius = 11f, center = Offset(243f, 112f))
                    drawPath(paths.take2Play, color = lerp(p.brightBlue, Color.White, t2ActiveMix))
                    drawPath(paths.take2Num, color = p.deepNavy, style = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    // Wave Bars A, B, C
                    val syA = evalTipKf(t, TipKf(0f, 1f), TipKf(45f, 1f), TipKf(52f, 1.32f), TipKf(60f, 0.76f), TipKf(68f, 1.26f), TipKf(76f, 1.05f), TipKf(80f, 1.05f), TipKf(91f, 1f), TipKf(100f, 1f))
                    val syB = evalTipKf(t, TipKf(0f, 1f), TipKf(45f, 1f), TipKf(52f, 0.75f), TipKf(60f, 1.34f), TipKf(68f, 0.82f), TipKf(76f, 1.12f), TipKf(80f, 1.12f), TipKf(91f, 1f), TipKf(100f, 1f))
                    val syC = evalTipKf(t, TipKf(0f, 1f), TipKf(45f, 1f), TipKf(52f, 1.18f), TipKf(60f, 0.88f), TipKf(68f, 1.35f), TipKf(76f, 0.95f), TipKf(80f, 0.95f), TipKf(91f, 1f), TipKf(100f, 1f))

                    val colA = lerp(p.slateNavy, p.greenStrong, t2ActiveMix)
                    val colB = lerp(p.skyBlue, p.greenMid, t2ActiveMix)
                    val colC = lerp(p.slateNavy, p.greenStrong, t2ActiveMix)
                    val colC2 = lerp(p.skyBlue, p.greenStrong, t2ActiveMix)

                    withTipPivot(304f, 112f, sx = 1f, sy = syA) {
                        drawRoundRect(color = colA, topLeft = Offset(272f, 107f), size = Size(3.4f, 10f), cornerRadius = CornerRadius(1.7f, 1.7f))
                        drawRoundRect(color = colA, topLeft = Offset(300f, 105f), size = Size(3.4f, 14f), cornerRadius = CornerRadius(1.7f, 1.7f))
                        drawRoundRect(color = colA, topLeft = Offset(314f, 107f), size = Size(3.4f, 10f), cornerRadius = CornerRadius(1.7f, 1.7f))
                    }
                    withTipPivot(304f, 112f, sx = 1f, sy = syB) {
                        drawRoundRect(color = colB, topLeft = Offset(279f, 103f), size = Size(3.4f, 18f), cornerRadius = CornerRadius(1.7f, 1.7f))
                        drawRoundRect(color = colB, topLeft = Offset(293f, 100f), size = Size(3.4f, 24f), cornerRadius = CornerRadius(1.7f, 1.7f))
                        drawRoundRect(color = colB, topLeft = Offset(321f, 104f), size = Size(3.4f, 16f), cornerRadius = CornerRadius(1.7f, 1.7f))
                    }
                    withTipPivot(304f, 112f, sx = 1f, sy = syC) {
                        drawRoundRect(color = colC, topLeft = Offset(286f, 106f), size = Size(3.4f, 12f), cornerRadius = CornerRadius(1.7f, 1.7f))
                        drawRoundRect(color = colC2, topLeft = Offset(307f, 102f), size = Size(3.4f, 20f), cornerRadius = CornerRadius(1.7f, 1.7f))
                    }

                    // Best Check Ripples (350, 112)
                    val bcRip1Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(50f, 0f, TipEaseOutExpo), TipKf(53f, 0.8f, TipEaseOutExpo), TipKf(69f, 0f), TipKf(100f, 0f))
                    if (bcRip1Alpha > 0.01f) {
                        val bcRip1S = evalTipKf(t, TipKf(0f, 0.8f), TipKf(50f, 0.8f, TipEaseOutExpo), TipKf(53f, 1f, TipEaseOutExpo), TipKf(69f, 2.4f), TipKf(100f, 0.8f))
                        withTipPivot(350f, 112f, sx = bcRip1S) {
                            drawCircle(color = p.greenStrong.copy(alpha = bcRip1Alpha), radius = 10f, center = Offset(350f, 112f), style = Stroke(width = 2.2f))
                        }
                    }
                    val bcRip2Alpha = evalTipKf(t, TipKf(0f, 0f), TipKf(54f, 0f, TipEaseOutExpo), TipKf(57f, 0.55f, TipEaseOutExpo), TipKf(74f, 0f), TipKf(100f, 0f))
                    if (bcRip2Alpha > 0.01f) {
                        val bcRip2S = evalTipKf(t, TipKf(0f, 0.8f), TipKf(54f, 0.8f, TipEaseOutExpo), TipKf(57f, 1f, TipEaseOutExpo), TipKf(74f, 2.8f), TipKf(100f, 0.8f))
                        withTipPivot(350f, 112f, sx = bcRip2S) {
                            drawCircle(color = p.greenContainer.copy(alpha = bcRip2Alpha), radius = 10f, center = Offset(350f, 112f), style = Stroke(width = 2f))
                        }
                    }

                    // Best Check Badge Spin Pop (350, 112)
                    val bcScale = evalTipKf(t, TipKf(0f, 1f), TipKf(46f, 1f, TipEaseOutBack), TipKf(49f, 0.68f, TipEaseOutBack), TipKf(57f, 1.08f), TipKf(80f, 1.08f, TipEaseOutQuart), TipKf(91f, 1f), TipKf(100f, 1f))
                    val bcRot = evalTipKf(t, TipKf(0f, 0f), TipKf(46f, 0f, TipEaseOutBack), TipKf(49f, -22f, TipEaseOutBack), TipKf(57f, 0f), TipKf(100f, 0f))
                    withTipPivot(350f, 112f, sx = bcScale, rotDeg = bcRot) {
                        drawCircle(color = p.greenStrong, radius = 10f, center = Offset(350f, 112f))
                        drawPath(paths.bestCheck, color = Color.White, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
        }
    }
}
