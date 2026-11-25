package com.example.textgrid.layout

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Defensive finite-number helpers preventing `IllegalArgumentException: Cannot round NaN value.`
 * and propagation of `NaN` / `Infinity` through layout, canvas drawing, and scroll offsets.
 */
object TextGridSafeMath {

    fun safeFinite(value: Float, fallback: Float = 0f): Float =
        if (value.isFinite()) value else fallback

    fun safeFinite(value: Double, fallback: Double = 0.0): Double =
        if (value.isFinite()) value else fallback

    fun safeCoerceIn(value: Float, minVal: Float, maxVal: Float, fallback: Float = minVal): Float {
        val lo = if (minVal.isFinite()) minVal else 0f
        val hi = if (maxVal.isFinite() && maxVal >= lo) maxVal else lo
        val v = if (value.isFinite()) value else fallback.coerceIn(lo, hi)
        return v.coerceIn(lo, hi)
    }

    fun safeCoerceIn(value: Double, minVal: Double, maxVal: Double, fallback: Double = minVal): Double {
        val lo = if (minVal.isFinite()) minVal else 0.0
        val hi = if (maxVal.isFinite() && maxVal >= lo) maxVal else lo
        val v = if (value.isFinite()) value else fallback.coerceIn(lo, hi)
        return v.coerceIn(lo, hi)
    }

    fun safeAtLeast(value: Float, minVal: Float): Float {
        val lo = if (minVal.isFinite()) minVal else 0f
        return if (value.isFinite() && value > lo) value else lo
    }

    fun safeAtLeast(value: Double, minVal: Double): Double {
        val lo = if (minVal.isFinite()) minVal else 0.0
        return if (value.isFinite() && value > lo) value else lo
    }

    fun safeCeil(value: Float, fallback: Float = 0f): Float =
        if (value.isFinite()) ceil(value) else fallback

    fun safeFloor(value: Float, fallback: Float = 0f): Float =
        if (value.isFinite()) floor(value) else fallback

    fun safeRoundToInt(value: Float, fallback: Int = 0): Int =
        if (value.isFinite()) {
            value.coerceIn(Int.MIN_VALUE.toFloat() * 0.5f, Int.MAX_VALUE.toFloat() * 0.5f).roundToInt()
        } else {
            fallback
        }

    fun safeRoundToInt(value: Double, fallback: Int = 0): Int =
        if (value.isFinite()) {
            value.coerceIn(Int.MIN_VALUE.toDouble() * 0.5, Int.MAX_VALUE.toDouble() * 0.5).roundToInt()
        } else {
            fallback
        }

    fun safeRoundToLong(value: Double, fallback: Long = 0L): Long =
        if (value.isFinite()) {
            value.coerceIn(Long.MIN_VALUE.toDouble() * 0.5, Long.MAX_VALUE.toDouble() * 0.5).roundToLong()
        } else {
            fallback
        }
}
