package com.example.ui.screens

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.abs
import kotlin.math.tan

internal class TipBezierCurve(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val isStepEnd: Boolean = false
) {
    fun evaluate(x: Float): Float {
        if (isStepEnd) return if (x >= 1f) 1f else 0f
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        if (x1 == y1 && x2 == y2) return x

        var lo = 0f
        var hi = 1f
        var u = x
        for (i in 0 until 8) {
            val bx = sampleCurve(u, x1, x2) - x
            if (abs(bx) < 1e-4f) break
            val dx = sampleCurveDerivative(u, x1, x2)
            if (abs(dx) > 1e-5f) {
                val nextU = u - bx / dx
                if (nextU in lo..hi) {
                    u = nextU
                    continue
                }
            }
            if (bx > 0f) hi = u else lo = u
            u = (lo + hi) * 0.5f
        }
        return sampleCurve(u, y1, y2)
    }

    private fun sampleCurve(u: Float, p1: Float, p2: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * u * p1 + 3f * inv * u * u * p2 + u * u * u
    }

    private fun sampleCurveDerivative(u: Float, p1: Float, p2: Float): Float {
        val inv = 1f - u
        return 3f * inv * inv * p1 + 6f * inv * u * (p2 - p1) + 3f * u * u * (1f - p2)
    }
}

internal val TipEaseInOut = TipBezierCurve(0.42f, 0f, 0.58f, 1f)
internal val TipEaseStandard = TipBezierCurve(0.4f, 0f, 0.2f, 1f)
internal val TipEaseSmooth = TipBezierCurve(0.45f, 0f, 0.55f, 1f)
internal val TipEaseSym = TipBezierCurve(0.4f, 0f, 0.6f, 1f)
internal val TipEaseOutBack = TipBezierCurve(0.34f, 1.56f, 0.64f, 1f)
internal val TipEaseOutQuart = TipBezierCurve(0.25f, 1f, 0.5f, 1f)
internal val TipEaseOutExpo = TipBezierCurve(0.16f, 1f, 0.3f, 1f)
internal val TipEaseSpringIn = TipBezierCurve(0.22f, 1f, 0.36f, 1f)
internal val TipEaseStepEnd = TipBezierCurve(0f, 0f, 1f, 1f, isStepEnd = true)

internal data class TipKf(
    val pct: Float,
    val v: Float,
    val curve: TipBezierCurve = TipEaseOutQuart
)

internal fun evalTipKf(t: Float, vararg frames: TipKf): Float {
    if (frames.isEmpty()) return 0f
    val p = t.coerceIn(0f, 1f) * 100f
    if (p <= frames.first().pct) return frames.first().v
    if (p >= frames.last().pct) return frames.last().v
    for (i in 0 until frames.size - 1) {
        val a = frames[i]
        val b = frames[i + 1]
        if (p <= b.pct) {
            val span = b.pct - a.pct
            if (span <= 1e-5f) return b.v
            val localT = (p - a.pct) / span
            val eased = a.curve.evaluate(localT)
            return a.v + (b.v - a.v) * eased
        }
    }
    return frames.last().v
}

internal fun lerpTipF(a: Float, b: Float, f: Float): Float = a + (b - a) * f

internal fun DrawScope.withTipPivot(
    pivotX: Float,
    pivotY: Float,
    tx: Float = 0f,
    ty: Float = 0f,
    sx: Float = 1f,
    sy: Float = sx,
    rotDeg: Float = 0f,
    skewXDeg: Float = 0f,
    block: DrawScope.() -> Unit
) {
    withTransform({
        translate(pivotX + tx, pivotY + ty)
        if (rotDeg != 0f) rotate(rotDeg, Offset.Zero)
        if (skewXDeg != 0f) {
            val skewMatrix = Matrix().apply {
                values[Matrix.SkewX] = tan(Math.toRadians(skewXDeg.toDouble())).toFloat()
            }
            transform(skewMatrix)
        }
        if (sx != 1f || sy != 1f) scale(sx, sy, Offset.Zero)
        translate(-pivotX, -pivotY)
    }, block)
}

internal fun parseTipSvgPath(pathData: String, evenOdd: Boolean = false): Path {
    return PathParser().parsePathString(pathData).toPath().apply {
        if (evenOdd) {
            fillType = PathFillType.EvenOdd
        }
    }
}

/**
 * Maps standard Material You SVG illustration tokens (#F0F4FA, #3B5386, #2563EB, #1E8E3E, #E0AB58, etc.)
 * to the active Android Material 3 dynamic [ColorScheme] while preserving contrast in both Light and Dark themes.
 */
internal class TipDynamicPalette(colorScheme: ColorScheme, isDark: Boolean) {
    // Canvas & geometric background surfaces (#F0F4FA, #E1E9F6, #D4DFED, #CAD7E5, #B5C5D8)
    val canvasBg = if (isDark) {
        lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.18f)
    } else {
        lerp(Color(0xFFF0F4FA), colorScheme.primaryContainer, 0.22f)
    }
    val decoLight = if (isDark) {
        colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        lerp(Color(0xFFE1E9F6), colorScheme.primaryContainer, 0.35f)
    }
    val decoMid = if (isDark) {
        colorScheme.primaryContainer.copy(alpha = 0.38f)
    } else {
        lerp(Color(0xFFD4DFED), colorScheme.primaryContainer, 0.4f)
    }
    val decoStroke = if (isDark) {
        colorScheme.outlineVariant.copy(alpha = 0.65f)
    } else {
        lerp(Color(0xFFCAD7E5), colorScheme.outlineVariant, 0.35f)
    }
    val shadowColor = if (isDark) {
        Color.Black.copy(alpha = 0.38f)
    } else {
        lerp(Color(0xFFB5C5D8), colorScheme.primary, 0.15f)
    }

    // Cards & warm surfaces (#FFFFFF, #FAF7F0, #F6F1E7, #E5DEC9)
    val cardWhite = if (isDark) colorScheme.surfaceContainerHigh else colorScheme.surface
    val cardWarm = if (isDark) {
        lerp(colorScheme.surfaceContainer, colorScheme.tertiaryContainer, 0.14f)
    } else {
        lerp(Color(0xFFFAF7F0), colorScheme.surface, 0.35f)
    }
    val slotWarm = if (isDark) {
        lerp(colorScheme.surfaceContainerHighest, colorScheme.tertiaryContainer, 0.22f)
    } else {
        lerp(Color(0xFFF6F1E7), colorScheme.secondaryContainer, 0.2f)
    }
    val borderWarm = if (isDark) {
        colorScheme.outlineVariant
    } else {
        Color(0xFFE5DEC9)
    }

    // Primary blues (#3B5386, #465C82, #2563EB, #60A5FA, #93C5FD, #D6E3FF)
    val deepNavy = if (isDark) {
        lerp(colorScheme.primary, Color.White, 0.22f)
    } else {
        lerp(Color(0xFF3B5386), colorScheme.primary, 0.45f)
    }
    val slateNavy = if (isDark) {
        lerp(colorScheme.onSurfaceVariant, colorScheme.primary, 0.35f)
    } else {
        lerp(Color(0xFF465C82), colorScheme.primary, 0.35f)
    }
    val brightBlue = if (isDark) {
        lerp(colorScheme.primary, Color(0xFF60A5FA), 0.35f)
    } else {
        lerp(Color(0xFF2563EB), colorScheme.primary, 0.55f)
    }
    val skyBlue = if (isDark) {
        lerp(colorScheme.primary, Color(0xFF93C5FD), 0.45f)
    } else {
        lerp(Color(0xFF60A5FA), colorScheme.primary, 0.35f)
    }
    val softBlue = if (isDark) {
        lerp(colorScheme.primaryContainer, Color(0xFF93C5FD), 0.35f)
    } else {
        lerp(Color(0xFF93C5FD), colorScheme.primaryContainer, 0.4f)
    }
    val blueContainer = if (isDark) {
        lerp(colorScheme.surfaceContainerHighest, colorScheme.primaryContainer, 0.65f)
    } else {
        lerp(Color(0xFFD6E3FF), colorScheme.primaryContainer, 0.5f)
    }
    val onDeepNavy = if (deepNavy.luminance() > 0.55f) Color(0xFF141C2B) else Color.White

    // Semantic greens (#1E8E3E, #5B9E77, #CEEAD6)
    val greenStrong = if (isDark) Color(0xFF4CC38A) else Color(0xFF1E8E3E)
    val greenMid = if (isDark) Color(0xFF67D29C) else Color(0xFF5B9E77)
    val greenContainer = if (isDark) {
        lerp(colorScheme.surfaceContainerHighest, Color(0xFF1E8E3E), 0.35f)
    } else {
        Color(0xFFCEEAD6)
    }

    // Semantic warm gold (#E0AB58, #F3C97E)
    val goldStrong = if (isDark) {
        lerp(Color(0xFFE0AB58), colorScheme.tertiary, 0.2f)
    } else {
        Color(0xFFE0AB58)
    }
    val goldSoft = if (isDark) {
        lerp(Color(0xFFF3C97E), colorScheme.tertiaryContainer, 0.35f)
    } else {
        Color(0xFFF3C97E)
    }

    // Semantic alert red (#DD5B51)
    val redAlert = if (isDark) Color(0xFFFF685D) else Color(0xFFDD5B51)
}
