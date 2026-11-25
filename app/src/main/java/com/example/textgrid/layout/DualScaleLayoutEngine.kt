package com.example.textgrid.layout

import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridInterval
import com.example.textgrid.model.TextGridPoint
import com.example.textgrid.model.TextGridScaleMode
import com.example.textgrid.model.TextGridTickPrecision
import com.example.textgrid.model.TextGridTier
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.model.TextGridWrapMode
import com.example.textgrid.model.TimeRange
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class PositionedInterval(
    val interval: TextGridInterval,
    val origIndex: Int,
    var lane: Int = 0
)

data class PositionedPoint(
    val point: TextGridPoint,
    val origIndex: Int
)

data class VisibleInterval(
    val positioned: PositionedInterval,
    val x0: Float,
    val x1: Float,
    val width: Float,
    val clippedLeft: Boolean,
    val clippedRight: Boolean,
    val rich: RichTextLayout?,
    val linearWidth: Float,
    val stretchRatio: Float,
    val isStretched: Boolean
)

data class VisibleZeroInterval(
    val positioned: PositionedInterval,
    val x: Float,
    val rich: RichTextLayout?
)

data class VisiblePoint(
    val positioned: PositionedPoint,
    val x: Float,
    val boxX: Float,
    val rich: RichTextLayout?,
    val boxWidth: Float,
    val boxHeight: Float,
    var level: Int = 0
)

data class AnomalySpan(
    val xmin: Double,
    val xmax: Double
)

sealed interface TierLayoutMeta {
    data class IntervalMeta(
        val normalIntervals: List<PositionedInterval>,
        val zeroIntervals: List<PositionedInterval>,
        val laneCount: Int,
        val gaps: List<AnomalySpan>,
        val overlaps: List<AnomalySpan>,
        val unrepairedEmpty: Boolean
    ) : TierLayoutMeta

    data class TextMeta(
        val finitePoints: List<PositionedPoint>
    ) : TierLayoutMeta
}

data class RectF(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
}

data class TierLayout(
    val tierIndex: Int,
    val tier: TextGridTier,
    val displayNumber: Int,
    val meta: TierLayoutMeta,
    val y: Float,
    val height: Float,
    val midY: Float,
    val wireY: Float?,
    val headerBox: RectF,
    val laneOffsets: FloatArray = FloatArray(0),
    val laneHeights: FloatArray = FloatArray(0),
    val visibleIntervals: List<VisibleInterval> = emptyList(),
    val visibleZeros: List<VisibleZeroInterval> = emptyList(),
    val visiblePoints: List<VisiblePoint> = emptyList(),
    val levelOffsetsY: FloatArray = FloatArray(0)
)

data class SpineColumn(
    val globalIndex: Int,
    val time: Double,
    val x: Float,
    val label: String,
    val usage: Int,
    var labelVisible: Boolean = false,
    var labelLeftX: Float = x,
    var labelWidth: Float = 0f
)

data class IsolatedPointTick(
    val time: Double,
    val x: Float,
    val label: String,
    val labelVisible: Boolean,
    val labelLeftX: Float = x,
    val labelWidth: Float = 0f
)

data class BandLinearSegment(
    val globalIndex: Int,
    val t0: Double,
    val t1: Double,
    val x0: Float,
    val x1: Float,
    val collapsed: Boolean,
    val isStretched: Boolean,
    val hasActiveContent: Boolean = true
)

data class BandLayout(
    val bandIndex: Int,
    val startIdx: Int,
    val endIdx: Int,
    val startT: Double,
    val endT: Double,
    val gutterWidth: Float,
    val originX: Float,
    val bandContentWidth: Float,
    val linearY: Float,
    val linearHeight: Float,
    val neckTopY: Float,
    val neckBottomY: Float,
    val tickTopY: Float,
    val tiersTopY: Float,
    val tiersBottomY: Float,
    val spineCols: List<SpineColumn>,
    val isolatedPointTicks: List<IsolatedPointTick>,
    val linearSegs: List<BandLinearSegment>,
    val tiersLayout: Map<Int, TierLayout>,
    val displayOrder: List<Int>,
    val finestIntervalTierIdx: Int
) {
    fun linearXForTime(t: Double): Float {
        if (!t.isFinite() || linearSegs.isEmpty()) return originX
        if (t <= linearSegs.first().t0) return linearSegs.first().x0
        val last = linearSegs.last()
        if (t >= last.t1) return last.x1
        for (seg in linearSegs) {
            if (t >= seg.t0 && t <= seg.t1) {
                val dt = seg.t1 - seg.t0
                val raw = if (dt <= 0.0) seg.x0 else (seg.x0 + ((t - seg.t0) / dt) * (seg.x1 - seg.x0)).toFloat()
                return TextGridSafeMath.safeFinite(raw, seg.x0)
            }
        }
        return originX
    }

    fun elasticXForTime(t: Double): Float {
        if (!t.isFinite() || spineCols.isEmpty()) return originX
        if (t <= spineCols.first().time) return spineCols.first().x
        val last = spineCols.last()
        if (t >= last.time) return last.x
        var lo = 0
        var hi = spineCols.lastIndex - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val a = spineCols[mid]
            val b = spineCols[mid + 1]
            when {
                t < a.time -> hi = mid - 1
                t > b.time -> lo = mid + 1
                else -> {
                    val dt = b.time - a.time
                    val raw = if (dt <= 0.0) a.x else (a.x + ((t - a.time) / dt) * (b.x - a.x)).toFloat()
                    return TextGridSafeMath.safeFinite(raw, a.x)
                }
            }
        }
        return originX
    }

    fun timeFromElasticX(x: Float): Double {
        if (!x.isFinite() || spineCols.isEmpty()) return startT
        if (x <= spineCols.first().x) return spineCols.first().time
        val last = spineCols.last()
        if (x >= last.x) return last.time
        for (i in 0 until spineCols.lastIndex) {
            val a = spineCols[i]
            val b = spineCols[i + 1]
            if (x >= a.x && x <= b.x) {
                val span = b.x - a.x
                val raw = if (span <= 0f) a.time else a.time + ((x - a.x) / span.toDouble()) * (b.time - a.time)
                return TextGridSafeMath.safeFinite(raw, a.time)
            }
        }
        return last.time
    }

    fun timeFromLinearX(x: Float): Double {
        if (!x.isFinite() || linearSegs.isEmpty()) return startT
        if (x <= linearSegs.first().x0) return linearSegs.first().t0
        val last = linearSegs.last()
        if (x >= last.x1) return last.t1
        for (seg in linearSegs) {
            if (x >= seg.x0 && x <= seg.x1) {
                val w = seg.x1 - seg.x0
                val raw = if (w <= 0f) seg.t0 else seg.t0 + ((x - seg.x0) / w.toDouble()) * (seg.t1 - seg.t0)
                return TextGridSafeMath.safeFinite(raw, seg.t0)
            }
        }
        return last.t1
    }
}

data class DualScaleLayoutOptions(
    val wrapMode: TextGridWrapMode = TextGridWrapMode.AUTO,
    val targetWidthDp: Float = 380f,
    val scaleMode: TextGridScaleMode = TextGridScaleMode.ELASTIC,
    val tickPrecision: TextGridTickPrecision = TextGridTickPrecision.P3,
    val tickFixedDecimals: Boolean = false,
    val viewDomain: TimeRange? = null,
    val richOptions: RichLayoutOptions = RichLayoutOptions(),
    val fontScale: Float = 1f,
    val hiddenTierIndices: Set<Int> = emptySet(),
    val compactMode: Boolean = false
) {
    companion object {
        fun fromVisualConfig(
            config: TextGridVisualConfig,
            targetWidthDp: Float,
            viewDomain: TimeRange? = null,
            hiddenTierIndices: Set<Int> = emptySet(),
            wrapOverride: TextGridWrapMode? = null,
            scaleOverride: TextGridScaleMode? = null,
            compactMode: Boolean = false
        ): DualScaleLayoutOptions {
            return DualScaleLayoutOptions(
                wrapMode = wrapOverride ?: config.wrapMode,
                targetWidthDp = targetWidthDp,
                scaleMode = scaleOverride ?: config.scaleMode,
                tickPrecision = config.tickPrecision,
                viewDomain = viewDomain,
                richOptions = RichLayoutOptions(
                    hideMetaSymbols = !config.showRichWhitespace,
                    trigraphMode = if (config.showRichWhitespace) TrigraphDisplayMode.AUTO else TrigraphDisplayMode.RAW_ONLY
                ),
                hiddenTierIndices = hiddenTierIndices,
                compactMode = compactMode
            )
        }
    }
}

data class ScoreLayout(
    val widthDp: Float,
    val heightDp: Float,
    val padX: Float,
    val padTop: Float,
    val padBottom: Float,
    val hideTicks: Boolean,
    val isTinyView: Boolean,
    val scaleMode: TextGridScaleMode,
    val effectiveWrapMode: TextGridWrapMode,
    val autoVirtualizedSingleRow: Boolean,
    val spineT: DoubleArray,
    val colCollapsed: BooleanArray,
    val bands: List<BandLayout>
)

/**
 * Pure read-only Dual-Scale Speech Score layout engine (dp coordinate space).
 *
 * All geometry inputs, intermediates, and outputs are strictly finite-guarded against `NaN`,
 * `Infinity`, zero-duration files, empty tiers, and narrow/zero-width viewport constraints.
 */
object DualScaleLayoutEngine {

    const val VIRTUALIZATION_COLUMN_THRESHOLD = 2500
    const val SILENCE_THRESHOLD_SEC = 1.6

    fun computeLayout(
        doc: TextGridDocument,
        options: DualScaleLayoutOptions,
        measurer: TextWidthMeasurer = HeuristicTextWidthMeasurer
    ): ScoreLayout {
        val visibleIndices = doc.tiers.indices.filter { it !in options.hiddenTierIndices }
        val hiddenSet = if (visibleIndices.isNotEmpty()) options.hiddenTierIndices else emptySet()
        fun isTierVisible(ti: Int): Boolean = ti !in hiddenSet

        val isLinear = options.scaleMode == TextGridScaleMode.LINEAR
        val targetW = TextGridSafeMath.safeCoerceIn(options.targetWidthDp, 220f, 4096f, 360f)
        val fontScale = TextGridSafeMath.safeCoerceIn(options.fontScale, 0.75f, 1.8f, 1f)
        val isCompactView = targetW < 640f
        val isTinyView = targetW < 440f

        fun safeMeasure(text: String, role: ScoreFontRole): Float {
            if (text.isEmpty()) return 0f
            return TextGridSafeMath.safeCoerceIn(measurer.measurePx(text, role, fontScale), 0f, 4096f, 0f)
        }

        val padX = when {
            options.compactMode -> 8f
            isTinyView -> 10f
            isCompactView -> 14f
            else -> 18f
        }
        val padTop = when {
            options.compactMode -> 8f
            isTinyView -> 12f
            else -> 14f
        }
        val padBottom = when {
            options.compactMode -> 8f
            isTinyView -> 12f
            else -> 14f
        }

        // Pure read-only top geometry: sleek 1:1 true-duration bar -> restrained elastic bridge -> clean horizontal ticks
        val linearH = if (options.compactMode) 6f else 7f
        val neckH = when {
            isLinear -> 0f
            options.compactMode -> 8f
            else -> 13f
        }
        val tierGap = if (options.compactMode) 4f else 6f
        val bandGap = if (options.compactMode) 14f else if (isTinyView) 20f else 24f
        val minColW = if (isTinyView) 18f else 22f
        val silenceCollapseW = if (isTinyView) 36f else 44f
        val effectiveRichOpts = options.richOptions.copy(fontScale = fontScale)
        val hideTicks = options.tickPrecision == TextGridTickPrecision.NONE || options.compactMode
        val tickBandH = if (hideTicks) 4f else (18f * fontScale).coerceAtLeast(16f)

        val viewDomain = options.viewDomain?.takeIf { it.isValid }
        val vMin = viewDomain?.start ?: Double.NEGATIVE_INFINITY
        val vMax = viewDomain?.end ?: Double.POSITIVE_INFINITY

        // 1. Collect sorted finite spine timestamps
        val tsSet = LinkedHashSet<Double>()
        fun addTs(t: Double) {
            if (t.isFinite() && t >= vMin && t <= vMax) tsSet.add(t)
        }
        if (viewDomain != null) {
            addTs(vMin)
            addTs(vMax)
        } else {
            addTs(doc.xmin)
            addTs(doc.xmax)
        }
        for (ti in doc.tiers.indices) {
            if (!isTierVisible(ti)) continue
            val tier = doc.tiers[ti]
            addTs(tier.xmin)
            addTs(tier.xmax)
            if (tier is TextGridTier.IntervalTier) {
                for (iv in tier.intervals) {
                    addTs(iv.xmin)
                    addTs(iv.xmax)
                }
            }
        }
        if (tsSet.size <= 2) {
            val ptTimes = ArrayList<Double>()
            for (ti in doc.tiers.indices) {
                if (!isTierVisible(ti)) continue
                val tier = doc.tiers[ti]
                if (tier is TextGridTier.TextTier) {
                    for (pt in tier.points) {
                        if (pt.number.isFinite() && pt.number >= vMin && pt.number <= vMax) {
                            ptTimes.add(pt.number)
                        }
                    }
                }
            }
            ptTimes.sort()
            var lastAdded = Double.NEGATIVE_INFINITY
            for (t in ptTimes) {
                if (t - lastAdded >= 0.25) {
                    tsSet.add(t)
                    lastAdded = t
                }
            }
        }

        val spineT = if (tsSet.isEmpty()) doubleArrayOf(0.0) else tsSet.toDoubleArray().also { it.sort() }
        val tToIdx = HashMap<Double, Int>(spineT.size * 2)
        for (i in spineT.indices) tToIdx[spineT[i]] = i

        val spineUsage = IntArray(spineT.size)
        for (ti in doc.tiers.indices) {
            if (!isTierVisible(ti)) continue
            val tier = doc.tiers[ti]
            val used = HashSet<Int>()
            if (tier.xmin.isFinite() && tier.xmin != doc.xmin) tToIdx[tier.xmin]?.let { used.add(it) }
            if (tier.xmax.isFinite() && tier.xmax != doc.xmax) tToIdx[tier.xmax]?.let { used.add(it) }
            when (tier) {
                is TextGridTier.IntervalTier -> {
                    for (iv in tier.intervals) {
                        tToIdx[iv.xmin]?.let { used.add(it) }
                        tToIdx[iv.xmax]?.let { used.add(it) }
                    }
                }
                is TextGridTier.TextTier -> {
                    for (pt in tier.points) {
                        tToIdx[pt.number]?.let { used.add(it) }
                    }
                }
            }
            for (idx in used) spineUsage[idx]++
        }

        // 2. Refined Gutter width calculation
        var maxTierNameW = 24f
        for (ti in doc.tiers.indices) {
            if (!isTierVisible(ti)) continue
            val name = doc.tiers[ti].name
            val w = if (name.isEmpty()) 14f else safeMeasure(name, ScoreFontRole.TIER_NAME)
            if (w > maxTierNameW) maxTierNameW = w
        }
        val gutterMin = when {
            options.compactMode -> 52f
            isTinyView -> 64f
            isCompactView -> 78f
            else -> 92f
        }
        val gutterMax = when {
            options.compactMode -> 76f
            isTinyView -> 88f
            isCompactView -> 114f
            else -> 148f
        }
        val gutterFull = TextGridSafeMath.safeCoerceIn(
            ceil((if (isTinyView || options.compactMode) 28f else 36f) + maxTierNameW + 10f),
            gutterMin,
            gutterMax,
            gutterMin
        )
        val gutterSubsequent = if (isTinyView && !options.compactMode) 40f else gutterFull
        val rightOverhang = if (options.compactMode) 6f else 10f

        // 3. Adaptive tick labels (horizontal, zero overlap)
        val tickStrings = if (hideTicks) {
            Array(spineT.size) { "" }
        } else {
            TextGridRichText.buildDisplayTickStrings(
                spineT = spineT,
                precisionDigits = options.tickPrecision.maxDecimals,
                fixedDecimals = options.tickFixedDecimals
            )
        }

        // 4. Per-tier anomaly detection & overlap lane packing
        val tierMetas = Array<TierLayoutMeta>(doc.tiers.size) { ti ->
            when (val tier = doc.tiers[ti]) {
                is TextGridTier.IntervalTier -> buildIntervalTierMeta(tier)
                is TextGridTier.TextTier -> {
                    val finitePts = tier.points
                        .mapIndexed { idx, pt -> PositionedPoint(pt, idx) }
                        .filter { it.point.number.isFinite() }
                        .sortedBy { it.point.number }
                    TierLayoutMeta.TextMeta(finitePts)
                }
            }
        }

        // 5. Large-file virtualization safeguard
        val numCols = max(0, spineT.size - 1)
        val autoVirtualized = options.wrapMode == TextGridWrapMode.AUTO && numCols > VIRTUALIZATION_COLUMN_THRESHOLD
        val effectiveWrapMode = if (autoVirtualized) TextGridWrapMode.SINGLE else options.wrapMode

        val colCollapsed = BooleanArray(numCols)
        val colHasActive = BooleanArray(numCols)
        val dx = FloatArray(numCols) { minColW }
        val maxBandContentW = TextGridSafeMath.safeAtLeast(targetW - padX * 2f - gutterFull - rightOverhang, 120f)

        // Precompute whether each column carries active text/points (used for both silence collapse and 1:1 pill tint)
        for (i in 0 until numCols) {
            val a = spineT[i]
            val b = spineT[i + 1]
            var hasActive = false
            for (ti in doc.tiers.indices) {
                if (!isTierVisible(ti)) continue
                when (val tm = tierMetas[ti]) {
                    is TierLayoutMeta.IntervalMeta -> {
                        for (piv in tm.normalIntervals) {
                            val iv = piv.interval
                            if (iv.xmin <= a && iv.xmax >= b && iv.text.isNotEmpty()) {
                                hasActive = true
                                break
                            }
                        }
                    }
                    is TierLayoutMeta.TextMeta -> {
                        for (ppt in tm.finitePoints) {
                            if (ppt.point.number >= b) break
                            if (ppt.point.number > a) {
                                hasActive = true
                                break
                            }
                        }
                    }
                }
                if (hasActive) break
            }
            colHasActive[i] = hasActive
        }

        // 6. Solve column widths (LINEAR vs ELASTIC)
        if (isLinear) {
            val spanRaw = if (spineT.size >= 2) spineT.last() - spineT.first() else 0.0
            val totalSpanT = TextGridSafeMath.safeAtLeast(spanRaw, 1e-6)
            val basePxPerSec = TextGridSafeMath.safeCoerceIn(maxBandContentW.toDouble() / totalSpanT, 1.0, 20000.0, 100.0)
            val linearPxPerSec = if (effectiveWrapMode == TextGridWrapMode.SINGLE) {
                basePxPerSec
            } else {
                val targetDensity = TextGridSafeMath.safeCoerceIn((numCols * 24.0) / totalSpanT, 200.0, 460.0, 240.0)
                max(basePxPerSec, targetDensity)
            }
            for (i in 0 until numCols) {
                val dt = TextGridSafeMath.safeAtLeast(spineT[i + 1] - spineT[i], 0.0)
                dx[i] = TextGridSafeMath.safeCoerceIn((dt * linearPxPerSec).toFloat(), 1.5f, 4096f, minColW)
            }
        } else {
            for (i in 0 until numCols) {
                val dt = TextGridSafeMath.safeAtLeast(spineT[i + 1] - spineT[i], 0.0)
                if (!colHasActive[i] && dt >= SILENCE_THRESHOLD_SEC) {
                    colCollapsed[i] = true
                    dx[i] = silenceCollapseW
                } else {
                    val baseW = 14.0 + 54.0 * max(dt, 0.001).pow(0.55)
                    dx[i] = max(minColW, TextGridSafeMath.safeRoundToInt(baseW, minColW.toInt()).toFloat())
                }
            }

            // Duration-weighted text widening (dt^0.85)
            val maxSingleIntervalW = if (effectiveWrapMode == TextGridWrapMode.SINGLE) {
                220f
            } else {
                min(220f, max(84f, floor(maxBandContentW * 0.72f)))
            }

            for (ti in doc.tiers.indices) {
                if (!isTierVisible(ti)) continue
                val tm = tierMetas[ti] as? TierLayoutMeta.IntervalMeta ?: continue
                for (piv in tm.normalIntervals) {
                    val iv = piv.interval
                    if (iv.text.isEmpty()) continue
                    val aIdx = tToIdx[max(vMin, iv.xmin)] ?: continue
                    val bIdx = tToIdx[min(vMax, iv.xmax)] ?: continue
                    if (bIdx <= aIdx) continue

                    var curSpanW = 0f
                    for (c in aIdx until bIdx) curSpanW += dx[c]
                    val unwrapped = TextGridRichText.layoutRichText(
                        str = iv.text,
                        maxWidthPx = 0f,
                        role = ScoreFontRole.INTERVAL_TEXT,
                        measurer = measurer,
                        isEditMode = false,
                        options = effectiveRichOpts
                    )
                    val desiredW = min(unwrapped.width + 24f, max(curSpanW, maxSingleIntervalW))
                    if (curSpanW < desiredW) {
                        val extra = desiredW - curSpanW
                        var allCollapsed = true
                        for (c in aIdx until bIdx) {
                            if (!colCollapsed[c]) {
                                allCollapsed = false
                                break
                            }
                        }
                        var totalWeight = 0.0
                        for (c in aIdx until bIdx) {
                            if (!allCollapsed && colCollapsed[c]) continue
                            totalWeight += max(1e-4, spineT[c + 1] - spineT[c]).pow(0.85)
                        }
                        if (totalWeight > 0.0) {
                            for (c in aIdx until bIdx) {
                                if (!allCollapsed && colCollapsed[c]) continue
                                val w = max(1e-4, spineT[c + 1] - spineT[c]).pow(0.85)
                                val delta = TextGridSafeMath.safeCoerceIn((extra * (w / totalWeight)).toFloat(), 0f, 1024f, 0f)
                                dx[c] += delta
                            }
                        }
                    }
                }
            }

            // Stretch gently to fill available container width when content is narrower than viewport
            if (numCols > 0) {
                var totalNatural = 0f
                for (i in 0 until numCols) totalNatural += dx[i]
                val shouldFill = effectiveWrapMode == TextGridWrapMode.SINGLE || totalNatural < maxBandContentW
                if (shouldFill) {
                    var collapsedSum = 0f
                    var activeSum = 0f
                    for (i in 0 until numCols) {
                        if (colCollapsed[i]) collapsedSum += dx[i] else activeSum += dx[i]
                    }
                    val targetActive = maxBandContentW - collapsedSum
                    if (activeSum > 1e-3f && targetActive > activeSum) {
                        val factor = TextGridSafeMath.safeCoerceIn(targetActive / activeSum, 1f, 8f, 1f)
                        for (i in 0 until numCols) {
                            if (!colCollapsed[i]) {
                                dx[i] = TextGridSafeMath.safeCoerceIn(dx[i] * factor, minColW, 2048f, dx[i])
                            }
                        }
                    }
                }
            }
        }

        // 7. Determine finest IntervalTier & display tier order (natural top-to-bottom order)
        var finestIntervalTierIdx = -1
        var maxIvCount = -1
        for (ti in doc.tiers.indices) {
            if (!isTierVisible(ti)) continue
            val t = doc.tiers[ti]
            if (t is TextGridTier.IntervalTier && t.intervals.size > maxIvCount) {
                maxIvCount = t.intervals.size
                finestIntervalTierIdx = ti
            }
        }
        val displayOrder = doc.tiers.indices.filter { isTierVisible(it) }

        // 8. Pause-aware Knuth-Plass DP balanced line wrapping
        val bandRanges = ArrayList<Pair<Int, Int>>()
        if (effectiveWrapMode == TextGridWrapMode.SINGLE || numCols == 0) {
            bandRanges.add(0 to (spineT.size - 1))
        } else {
            val prefW = DoubleArray(numCols + 1)
            for (i in 0 until numCols) prefW[i + 1] = prefW[i] + dx[i].toDouble()

            val diff = DoubleArray(numCols + 1)
            for (ti in doc.tiers.indices) {
                if (!isTierVisible(ti)) continue
                val tm = tierMetas[ti] as? TierLayoutMeta.IntervalMeta ?: continue
                for (piv in tm.normalIntervals) {
                    val iv = piv.interval
                    if (iv.xmax <= vMin || iv.xmin >= vMax) continue
                    val aIdx = if (iv.xmin < vMin) 0 else (tToIdx[max(vMin, iv.xmin)] ?: continue)
                    val bIdx = if (iv.xmax > vMax) numCols else (tToIdx[min(vMax, iv.xmax)] ?: continue)
                    if (bIdx - aIdx >= 2) {
                        val w = if (iv.text.isNotEmpty()) 120.0 else 18.0
                        diff[aIdx + 1] += w
                        diff[bIdx] -= w
                    }
                }
            }
            val cutPenalty = DoubleArray(numCols + 1)
            var accPen = 0.0
            for (k in 1 until numCols) {
                accPen += diff[k]
                cutPenalty[k] = accPen
            }

            val breakCost = DoubleArray(numCols + 1)
            for (k in 1 until numCols) {
                breakCost[k] = cutPenalty[k] - min(spineUsage[k], 4) * 10.0 - (if (colCollapsed[k - 1]) 20.0 else 0.0)
            }

            val availW0 = maxBandContentW.toDouble()
            val availWRest = TextGridSafeMath.safeAtLeast(targetW - padX * 2f - gutterSubsequent - rightOverhang, 120f).toDouble()

            if (prefW[numCols] <= availW0 + 1.0) {
                bandRanges.add(0 to numCols)
            } else {
                val estBands = max(2, 1 + TextGridSafeMath.safeRoundToInt(ceil((prefW[numCols] - availW0) / availWRest), 1))
                val targetFillRatio = TextGridSafeMath.safeCoerceIn(
                    prefW[numCols] / (availW0 + (estBands - 1) * availWRest),
                    0.62,
                    0.92,
                    0.78
                )
                val dp = DoubleArray(numCols + 1) { Double.POSITIVE_INFINITY }
                dp[numCols] = 0.0
                val nextIdx = IntArray(numCols + 1)
                val bandBase = 28.0

                for (i in numCols - 1 downTo 0) {
                    val avail = if (i == 0) availW0 else availWRest
                    var maxK = i + 1
                    while (maxK < numCols && prefW[maxK + 1] - prefW[i] <= avail) {
                        maxK++
                    }
                    var bestCost = Double.POSITIVE_INFINITY
                    var bestK = maxK
                    for (k in (i + 1)..maxK) {
                        val runW = prefW[k] - prefW[i]
                        val fill = runW / avail
                        val cost = if (k == numCols) {
                            val shortage = if (i > 0) max(0.0, targetFillRatio - fill) else 0.0
                            bandBase + if (i > 0) {
                                160.0 * shortage * shortage + if (fill < 0.45) 90.0 * (0.45 - fill) else 0.0
                            } else 0.0
                        } else {
                            val dev = max(0.0, targetFillRatio - fill)
                            val excess = max(0.0, fill - targetFillRatio)
                            val tinyGuard = if (fill < 0.38 && k < maxK) 400.0 * (0.38 - fill).pow(2) else 0.0
                            bandBase + breakCost[k] +
                                220.0 * dev * dev +
                                55.0 * (1.0 - fill).pow(2) +
                                35.0 * excess * excess +
                                tinyGuard +
                                dp[k]
                        }
                        if (cost < bestCost) {
                            bestCost = cost
                            bestK = k
                        }
                    }
                    dp[i] = bestCost
                    nextIdx[i] = bestK
                }

                var cur = 0
                while (cur < numCols) {
                    val next = if (nextIdx[cur] > cur && nextIdx[cur] <= numCols) nextIdx[cur] else cur + 1
                    bandRanges.add(cur to next)
                    cur = next
                }
            }
        }

        // 9. Build concrete BandLayout geometries
        var curY = padTop
        var totalWidth = 0f
        val bandLayouts = ArrayList<BandLayout>(bandRanges.size)

        for ((bandIndex, range) in bandRanges.withIndex()) {
            val (startIdx, endIdx) = range
            val gutterW = if (bandIndex == 0) gutterFull else gutterSubsequent
            val originX = padX + gutterW
            val startT = spineT[startIdx]
            val endT = spineT[endIdx]

            val spineCols = ArrayList<SpineColumn>(endIdx - startIdx + 1)
            var cx = originX
            for (i in startIdx..endIdx) {
                val lbl = tickStrings[i]
                val lblW = if (hideTicks || lbl.isEmpty()) 0f else safeMeasure(lbl, ScoreFontRole.TICK_LABEL)
                spineCols.add(
                    SpineColumn(
                        globalIndex = i,
                        time = spineT[i],
                        x = cx,
                        label = lbl,
                        usage = spineUsage[i],
                        labelVisible = false,
                        labelLeftX = cx,
                        labelWidth = lblW
                    )
                )
                if (i < endIdx) cx += dx[i]
            }
            val bandContentW = if (endIdx == startIdx) {
                max(120f, maxBandContentW)
            } else {
                max(40f, TextGridSafeMath.safeFinite(cx - originX, maxBandContentW))
            }
            val bandRightX = originX + bandContentW
            val bandTotalW = originX + bandContentW + rightOverhang + padX
            if (bandTotalW.isFinite() && bandTotalW > totalWidth) totalWidth = bandTotalW

            fun xForGlobalIdx(gi: Int): Float = spineCols[(gi - startIdx).coerceIn(0, spineCols.lastIndex)].x
            fun elasticXLocal(t: Double): Float {
                if (!t.isFinite() || spineCols.isEmpty()) return originX
                if (t <= spineCols.first().time) return spineCols.first().x
                val last = spineCols.last()
                if (t >= last.time) return last.x
                for (i in 0 until spineCols.lastIndex) {
                    val a = spineCols[i]
                    val b = spineCols[i + 1]
                    if (t >= a.time && t <= b.time) {
                        val dt = b.time - a.time
                        val raw = if (dt <= 0.0) a.x else (a.x + ((t - a.time) / dt) * (b.x - a.x)).toFloat()
                        return TextGridSafeMath.safeFinite(raw, a.x)
                    }
                }
                return originX
            }

            // Priority-based horizontal tick placement with strict measured bounding-box non-overlap
            val placedBoxes = ArrayList<Pair<Float, Float>>(spineCols.size)
            val tickPadGap = if (isTinyView) 10f else 12f

            fun tryPlaceTickBox(anchorX: Float, labelW: Float, isFirst: Boolean, isLast: Boolean, minAnchorGap: Float): Float? {
                val desiredLeft = when {
                    isFirst -> originX
                    isLast -> (bandRightX - labelW).coerceAtLeast(originX)
                    else -> (anchorX - labelW * 0.5f).coerceIn(originX, (bandRightX - labelW).coerceAtLeast(originX))
                }
                val desiredRight = desiredLeft + labelW
                for ((pL, pR) in placedBoxes) {
                    val pCenter = (pL + pR) * 0.5f
                    if (desiredLeft < pR + tickPadGap && desiredRight + tickPadGap > pL) return null
                    if (minAnchorGap > 0f && abs(anchorX - pCenter) < minAnchorGap) return null
                }
                placedBoxes.add(desiredLeft to desiredRight)
                return desiredLeft
            }

            if (!hideTicks) {
                data class TickItem(val idx: Int, val x: Float, val prio: Int)
                val order = spineCols.mapIndexed { idx, col ->
                    val prio = when {
                        col.time == doc.xmin || col.time == doc.xmax -> 3
                        idx == 0 || idx == spineCols.lastIndex -> 2
                        col.usage >= 2 -> 1
                        else -> 0
                    }
                    TickItem(idx, col.x, prio)
                }.sortedWith(compareByDescending<TickItem> { it.prio }.thenBy { it.idx })

                for (item in order) {
                    val col = spineCols[item.idx]
                    val isFirst = item.idx == 0
                    val isLast = item.idx == spineCols.lastIndex && spineCols.size > 1
                    val minCenterGap = if (item.prio >= 2) 0f else if (isTinyView) 28f else 32f
                    val placedLeft = tryPlaceTickBox(col.x, col.labelWidth, isFirst, isLast, minCenterGap)
                    if (placedLeft != null) {
                        col.labelVisible = true
                        col.labelLeftX = placedLeft
                    }
                }
            }

            // Isolated point ticks (secondary priority, also non-overlapping)
            val isoTimeSet = LinkedHashSet<Double>()
            for (ti in doc.tiers.indices) {
                if (!isTierVisible(ti)) continue
                val tm = tierMetas[ti] as? TierLayoutMeta.TextMeta ?: continue
                for (ppt in tm.finitePoints) {
                    val t = ppt.point.number
                    val inBand = if (bandIndex == 0) t >= startT && t <= endT else t > startT && t <= endT
                    if (inBand && !tToIdx.containsKey(t)) {
                        isoTimeSet.add(t)
                    }
                }
            }
            val isoTimes = isoTimeSet.toDoubleArray().also { it.sort() }
            val isoLabels = if (hideTicks) {
                Array(isoTimes.size) { "" }
            } else {
                Array(isoTimes.size) {
                    TextGridRichText.formatDisplayTick(
                        isoTimes[it],
                        options.tickPrecision.maxDecimals,
                        options.tickFixedDecimals
                    )
                }
            }
            if (!hideTicks && isoTimes.isNotEmpty() && options.tickPrecision.maxDecimals != null) {
                val spineLabelSet = spineCols.mapTo(HashSet()) { it.label }
                val baseD = options.tickPrecision.maxDecimals!!
                for (d in (baseD + 1)..6) {
                    val colliding = HashSet<Int>()
                    for (i in isoTimes.indices) {
                        if (isoLabels[i] in spineLabelSet) colliding.add(i)
                        if (i > 0 && isoLabels[i] == isoLabels[i - 1]) {
                            colliding.add(i - 1)
                            colliding.add(i)
                        }
                    }
                    if (colliding.isEmpty()) break
                    for (idx in colliding) {
                        isoLabels[idx] = TextGridRichText.formatDisplayTick(isoTimes[idx], d, options.tickFixedDecimals)
                    }
                }
            }
            val isolatedPointTicks = ArrayList<IsolatedPointTick>(isoTimes.size)
            for (i in isoTimes.indices) {
                val t = isoTimes[i]
                val x = elasticXLocal(t)
                val lbl = isoLabels[i]
                val lblW = if (hideTicks || lbl.isEmpty()) 0f else safeMeasure(lbl, ScoreFontRole.TICK_LABEL)
                val placedLeft = if (hideTicks) null else tryPlaceTickBox(x, lblW, isFirst = false, isLast = false, minAnchorGap = 28f)
                isolatedPointTicks.add(
                    IsolatedPointTick(
                        time = t,
                        x = x,
                        label = lbl,
                        labelVisible = placedLeft != null,
                        labelLeftX = placedLeft ?: x,
                        labelWidth = lblW
                    )
                )
            }

            // Linear 1:1 true-duration bar segments
            val linearSegs = ArrayList<BandLinearSegment>()
            if (endIdx > startIdx) {
                if (isLinear) {
                    for (i in startIdx until endIdx) {
                        linearSegs.add(
                            BandLinearSegment(
                                globalIndex = i,
                                t0 = spineT[i],
                                t1 = spineT[i + 1],
                                x0 = xForGlobalIdx(i),
                                x1 = xForGlobalIdx(i + 1),
                                collapsed = false,
                                isStretched = false,
                                hasActiveContent = colHasActive[i]
                            )
                        )
                    }
                } else {
                    var activeTime = 0.0
                    var collapsedCount = 0
                    for (i in startIdx until endIdx) {
                        if (colCollapsed[i]) collapsedCount++ else activeTime += TextGridSafeMath.safeAtLeast(spineT[i + 1] - spineT[i], 0.0)
                    }
                    val breakLinearW = if (collapsedCount > 0) min(40f, max(22f, bandContentW * 0.08f)) else 0f
                    val availLinearW = max(20f, bandContentW - breakLinearW * collapsedCount)
                    val activeColCount = max(1, endIdx - startIdx - collapsedCount)
                    var lx = originX
                    for (i in startIdx until endIdx) {
                        val dt = TextGridSafeMath.safeAtLeast(spineT[i + 1] - spineT[i], 0.0)
                        val w = when {
                            colCollapsed[i] -> breakLinearW
                            activeTime > 1e-12 -> TextGridSafeMath.safeCoerceIn(((dt / activeTime) * availLinearW).toFloat(), 0f, bandContentW, 0f)
                            else -> availLinearW / activeColCount
                        }
                        val elasticW = dx[i]
                        val isStretched = !colCollapsed[i] && w > 0.5f && (elasticW / w) > 1.6f && (elasticW - w) >= 8f
                        linearSegs.add(
                            BandLinearSegment(
                                globalIndex = i,
                                t0 = spineT[i],
                                t1 = spineT[i + 1],
                                x0 = lx,
                                x1 = lx + w,
                                collapsed = colCollapsed[i],
                                isStretched = isStretched,
                                hasActiveContent = colHasActive[i]
                            )
                        )
                        lx += w
                    }
                }
            } else {
                linearSegs.add(
                    BandLinearSegment(
                        globalIndex = startIdx,
                        t0 = startT,
                        t1 = endT,
                        x0 = originX,
                        x1 = originX + bandContentW,
                        collapsed = false,
                        isStretched = false,
                        hasActiveContent = false
                    )
                )
            }

            fun linearXLocal(t: Double): Float {
                if (!t.isFinite() || linearSegs.isEmpty()) return originX
                if (t <= linearSegs.first().t0) return linearSegs.first().x0
                val last = linearSegs.last()
                if (t >= last.t1) return last.x1
                for (seg in linearSegs) {
                    if (t >= seg.t0 && t <= seg.t1) {
                        val dt = seg.t1 - seg.t0
                        val raw = if (dt <= 0.0) seg.x0 else (seg.x0 + ((t - seg.t0) / dt) * (seg.x1 - seg.x0)).toFloat()
                        return TextGridSafeMath.safeFinite(raw, seg.x0)
                    }
                }
                return originX
            }

            val linearY = curY
            val neckTopY = linearY + linearH
            val neckBotY = neckTopY + neckH
            val tickTopY = neckBotY
            val tiersTopY = tickTopY + tickBandH

            var ty = tiersTopY
            val tiersLayoutMap = LinkedHashMap<Int, TierLayout>(displayOrder.size)

            for (ti in displayOrder) {
                val tier = doc.tiers[ti]
                val dispNum = tier.displayNum ?: (ti + 1)
                when (val tm = tierMetas[ti]) {
                    is TierLayoutMeta.IntervalMeta -> {
                        val visibleIvs = ArrayList<VisibleInterval>()
                        val baseLaneH = (if (options.compactMode) 26f else 34f) * fontScale
                        val laneMaxH = FloatArray(tm.laneCount) { baseLaneH }

                        for (piv in tm.normalIntervals) {
                            val iv = piv.interval
                            if (iv.xmax <= startT || iv.xmin >= endT) continue
                            val clippedLeft = iv.xmin < startT
                            val clippedRight = iv.xmax > endT
                            val x0 = if (clippedLeft) originX else tToIdx[iv.xmin]?.let(::xForGlobalIdx) ?: elasticXLocal(iv.xmin)
                            val x1 = if (clippedRight) bandRightX else tToIdx[iv.xmax]?.let(::xForGlobalIdx) ?: elasticXLocal(iv.xmax)
                            val w = max(1.5f, TextGridSafeMath.safeFinite(x1 - x0, 1.5f))
                            val showText = !clippedLeft && iv.text.isNotEmpty()
                            val richMaxW = if (isLinear) max(12f, w - 8f) else max(24f, w - 16f)
                            val rich = if (showText) {
                                TextGridRichText.layoutRichText(
                                    str = iv.text,
                                    maxWidthPx = richMaxW,
                                    role = ScoreFontRole.INTERVAL_TEXT,
                                    measurer = measurer,
                                    isEditMode = false,
                                    options = effectiveRichOpts
                                )
                            } else null

                            val needH = if (rich != null) {
                                max(baseLaneH, rich.height + (if (options.compactMode) 8f else 14f))
                            } else baseLaneH
                            if (needH > laneMaxH[piv.lane]) laneMaxH[piv.lane] = needH

                            val segT0 = max(iv.xmin, startT)
                            val segT1 = min(iv.xmax, endT)
                            val linW = max(0f, TextGridSafeMath.safeFinite(linearXLocal(segT1) - linearXLocal(segT0), 0f))
                            var hasCollapsedCol = false
                            for (c in startIdx until endIdx) {
                                if (colCollapsed[c] && spineT[c] >= segT0 && spineT[c + 1] <= segT1) {
                                    hasCollapsedCol = true
                                    break
                                }
                            }
                            val stretchRatio = if (!isLinear && !hasCollapsedCol && linW > 0.5f) {
                                TextGridSafeMath.safeCoerceIn(w / linW, 0.1f, 100f, 1f)
                            } else 1f
                            val isStretched = !isLinear && !hasCollapsedCol && stretchRatio > 1.6f && (w - linW) >= 8f

                            visibleIvs.add(
                                VisibleInterval(
                                    positioned = piv,
                                    x0 = x0,
                                    x1 = x0 + w,
                                    width = w,
                                    clippedLeft = clippedLeft,
                                    clippedRight = clippedRight,
                                    rich = rich,
                                    linearWidth = linW,
                                    stretchRatio = stretchRatio,
                                    isStretched = isStretched
                                )
                            )
                        }

                        val visibleZeros = tm.zeroIntervals
                            .filter { it.interval.xmin >= startT && it.interval.xmin <= endT }
                            .map { piv ->
                                val x = tToIdx[piv.interval.xmin]?.let(::xForGlobalIdx) ?: elasticXLocal(piv.interval.xmin)
                                val rich = if (piv.interval.text.isNotEmpty()) {
                                    TextGridRichText.layoutRichText(
                                        str = piv.interval.text,
                                        maxWidthPx = 120f,
                                        role = ScoreFontRole.POINT_TEXT,
                                        measurer = measurer,
                                        isEditMode = false,
                                        options = effectiveRichOpts
                                    )
                                } else null
                                VisibleZeroInterval(piv, x, rich)
                            }

                        val laneOffsets = FloatArray(tm.laneCount)
                        var totalH = 0f
                        for (l in 0 until tm.laneCount) {
                            laneOffsets[l] = totalH
                            totalH += laneMaxH[l]
                        }
                        if (visibleZeros.isNotEmpty() && totalH < 34f) totalH = 34f

                        val headerBox = RectF(padX, ty, max(24f, gutterW - 6f), totalH)
                        tiersLayoutMap[ti] = TierLayout(
                            tierIndex = ti,
                            tier = tier,
                            displayNumber = dispNum,
                            meta = tm,
                            y = ty,
                            height = totalH,
                            midY = ty + totalH * 0.5f,
                            wireY = null,
                            headerBox = headerBox,
                            laneOffsets = laneOffsets,
                            laneHeights = laneMaxH,
                            visibleIntervals = visibleIvs,
                            visibleZeros = visibleZeros
                        )
                        ty += totalH + tierGap
                    }

                    is TierLayoutMeta.TextMeta -> {
                        val bandLeft = originX
                        val bandRight = bandRightX
                        val maxFlagW = min(184f, max(88f, bandContentW))
                        val visiblePts = tm.finitePoints
                            .filter { ppt ->
                                val t = ppt.point.number
                                (if (bandIndex == 0) t >= startT else t > startT) && t <= endT
                            }
                            .map { ppt ->
                                val x = elasticXLocal(ppt.point.number)
                                val rich = if (ppt.point.mark.isNotEmpty()) {
                                    TextGridRichText.layoutRichText(
                                        str = ppt.point.mark,
                                        maxWidthPx = max(48f, maxFlagW - 18f),
                                        role = ScoreFontRole.POINT_TEXT,
                                        measurer = measurer,
                                        isEditMode = false,
                                        options = effectiveRichOpts
                                    )
                                } else null
                                val boxW = if (rich != null) max(24f, ceil(rich.width) + 18f) else 0f
                                val boxH = if (rich != null) max(20f, rich.height + 6f) else 0f
                                val maxBoxLeft = max(bandLeft, bandRight - boxW)
                                val clampedBx = TextGridSafeMath.safeCoerceIn(x - boxW * 0.5f, bandLeft, maxBoxLeft, bandLeft)
                                VisiblePoint(ppt, x, clampedBx, rich, boxW, boxH, level = 0)
                            }

                        val levelRightEdge = ArrayList<Float>()
                        val levelMaxH = ArrayList<Float>()
                        for (vp in visiblePts) {
                            if (vp.rich == null) {
                                vp.level = -1
                                continue
                            }
                            val left = vp.boxX
                            val right = vp.boxX + vp.boxWidth
                            var lvl = 0
                            while (lvl < levelRightEdge.size && levelRightEdge[lvl] + 5f > left) {
                                lvl++
                            }
                            vp.level = lvl
                            if (lvl == levelRightEdge.size) {
                                levelRightEdge.add(right)
                                levelMaxH.add(vp.boxHeight)
                            } else {
                                levelRightEdge[lvl] = right
                                levelMaxH[lvl] = max(levelMaxH[lvl], vp.boxHeight)
                            }
                        }

                        val wireOffsetY = if (options.compactMode) 10f else 12f
                        var extraStemH = if (options.compactMode) 12f else 16f
                        val levelY = FloatArray(levelMaxH.size)
                        for (l in levelMaxH.indices) {
                            val top = wireOffsetY + 11f + if (l == 0) {
                                0f
                            } else {
                                (levelY[l - 1] - wireOffsetY - 11f) + levelMaxH[l - 1] + 5f
                            }
                            levelY[l] = top
                            extraStemH = top + levelMaxH[l] + 4f
                        }
                        val totalH = max(if (options.compactMode) 24f else 30f, extraStemH)
                        val wireY = ty + wireOffsetY
                        val headerBox = RectF(padX, wireY - 12f, max(24f, gutterW - 6f), 24f)

                        tiersLayoutMap[ti] = TierLayout(
                            tierIndex = ti,
                            tier = tier,
                            displayNumber = dispNum,
                            meta = tm,
                            y = ty,
                            height = totalH,
                            midY = wireY,
                            wireY = wireY,
                            headerBox = headerBox,
                            visiblePoints = visiblePts,
                            levelOffsetsY = levelY
                        )
                        ty += totalH + tierGap
                    }
                }
            }

            if (displayOrder.isEmpty()) ty += 32f else ty -= tierGap

            bandLayouts.add(
                BandLayout(
                    bandIndex = bandIndex,
                    startIdx = startIdx,
                    endIdx = endIdx,
                    startT = startT,
                    endT = endT,
                    gutterWidth = gutterW,
                    originX = originX,
                    bandContentWidth = bandContentW,
                    linearY = linearY,
                    linearHeight = linearH,
                    neckTopY = neckTopY,
                    neckBottomY = neckBotY,
                    tickTopY = tickTopY,
                    tiersTopY = tiersTopY,
                    tiersBottomY = ty,
                    spineCols = spineCols,
                    isolatedPointTicks = isolatedPointTicks,
                    linearSegs = linearSegs,
                    tiersLayout = tiersLayoutMap,
                    displayOrder = displayOrder,
                    finestIntervalTierIdx = finestIntervalTierIdx
                )
            )
            curY = ty + bandGap
        }

        val finalW = TextGridSafeMath.safeCoerceIn(TextGridSafeMath.safeCeil(totalWidth, targetW), 220f, 65536f, targetW)
        val finalH = TextGridSafeMath.safeCoerceIn(TextGridSafeMath.safeCeil(curY - bandGap + padBottom, 96f), 84f, 65536f, 120f)

        return ScoreLayout(
            widthDp = finalW,
            heightDp = finalH,
            padX = padX,
            padTop = padTop,
            padBottom = padBottom,
            hideTicks = hideTicks,
            isTinyView = isTinyView,
            scaleMode = options.scaleMode,
            effectiveWrapMode = effectiveWrapMode,
            autoVirtualizedSingleRow = autoVirtualized,
            spineT = spineT,
            colCollapsed = colCollapsed,
            bands = bandLayouts
        )
    }

    private fun buildIntervalTierMeta(tier: TextGridTier.IntervalTier): TierLayoutMeta.IntervalMeta {
        val finiteIvs = tier.intervals
            .mapIndexed { idx, iv -> PositionedInterval(iv, idx, lane = 0) }
            .filter { it.interval.xmin.isFinite() && it.interval.xmax.isFinite() && it.interval.xmax >= it.interval.xmin }
            .sortedWith(compareBy<PositionedInterval> { it.interval.xmin }.thenBy { it.interval.xmax })

        val normalIvs = ArrayList<PositionedInterval>(finiteIvs.size)
        val zeroIvs = ArrayList<PositionedInterval>()
        for (piv in finiteIvs) {
            if (piv.interval.xmin == piv.interval.xmax) zeroIvs.add(piv) else normalIvs.add(piv)
        }

        val laneEnds = ArrayList<Double>()
        for (piv in normalIvs) {
            var placed = -1
            for (l in laneEnds.indices) {
                if (laneEnds[l] <= piv.interval.xmin) {
                    placed = l
                    break
                }
            }
            if (placed == -1) {
                placed = laneEnds.size
                laneEnds.add(piv.interval.xmax)
            } else {
                laneEnds[placed] = piv.interval.xmax
            }
            piv.lane = placed
        }
        val laneCount = max(1, laneEnds.size)

        val gaps = ArrayList<AnomalySpan>()
        val overlaps = ArrayList<AnomalySpan>()
        if (normalIvs.isNotEmpty() && tier.xmin.isFinite() && tier.xmax.isFinite() && tier.xmax > tier.xmin) {
            data class SweepEvent(val t: Double, val delta: Int)
            val events = ArrayList<SweepEvent>(normalIvs.size * 2)
            for (piv in normalIvs) {
                events.add(SweepEvent(piv.interval.xmin, +1))
                events.add(SweepEvent(piv.interval.xmax, -1))
            }
            events.sortWith(compareBy<SweepEvent> { it.t }.thenBy { it.delta })

            var curT = tier.xmin
            var active = 0
            var pi = 0
            while (pi < events.size) {
                val nextT = events[pi].t
                if (nextT > curT) {
                    val segA = max(curT, tier.xmin)
                    val segB = min(nextT, tier.xmax)
                    if (segB > segA && active == 0) gaps.add(AnomalySpan(segA, segB))
                    if (active >= 2) overlaps.add(AnomalySpan(curT, nextT))
                }
                while (pi < events.size && events[pi].t == nextT) {
                    active += events[pi].delta
                    pi++
                }
                curT = nextT
            }
            if (curT < tier.xmax && active == 0) gaps.add(AnomalySpan(curT, tier.xmax))
        }

        return TierLayoutMeta.IntervalMeta(
            normalIntervals = normalIvs,
            zeroIntervals = zeroIvs,
            laneCount = laneCount,
            gaps = gaps,
            overlaps = overlaps,
            unrepairedEmpty = tier.intervals.isEmpty()
        )
    }

    /**
     * Generates a clean, self-contained publication-grade SVG string matching the read-only Dual-Scale layout.
     */
    fun exportStaticSvg(
        doc: TextGridDocument,
        layout: ScoreLayout,
        isBw: Boolean = false
    ): String {
        fun esc(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        val bg = "#FFFFFF"
        val ink = if (isBw) "#111111" else "#1C1B19"
        val borderCol = if (isBw) "#404040" else "#8C877C"
        val muted = if (isBw) "#525252" else "#66625A"
        val rule = if (isBw) "#C8C8C8" else "#D2CEC4"
        val emptyFill = if (isBw) "#F7F7F7" else "#F5F3EC"
        val activeBar = if (isBw) "#171717" else "#3E4C5E"
        val metaCol = if (isBw) "#666666" else "#0F766E"
        val gapCol = if (isBw) "#666666" else "#D97706"

        val w = TextGridSafeMath.safeCoerceIn(layout.widthDp, 220f, 65536f, 360f)
        val h = TextGridSafeMath.safeCoerceIn(layout.heightDp, 84f, 65536f, 120f)

        return buildString(4096) {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append(
                String.format(
                    Locale.US,
                    """<svg xmlns="http://www.w3.org/2000/svg" width="%.0f" height="%.0f" viewBox="0 0 %.1f %.1f">""",
                    w, h, w, h
                )
            ).append('\n')
            append("""<rect width="100%" height="100%" fill="$bg"/>""").append('\n')

            for (band in layout.bands) {
                val r = band.linearHeight * 0.5f
                append(
                    String.format(
                        Locale.US,
                        """<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s" stroke="%s" stroke-width="0.6"/>""",
                        band.originX, band.linearY, band.bandContentWidth, band.linearHeight, r, emptyFill, rule
                    )
                ).append('\n')

                for (seg in band.linearSegs) {
                    val segW = max(0f, seg.x1 - seg.x0)
                    if (segW > 0.5f && seg.hasActiveContent && !seg.collapsed) {
                        append(
                            String.format(
                                Locale.US,
                                """<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s" opacity="0.24"/>""",
                                seg.x0, band.linearY, segW, band.linearHeight, r * 0.7f, activeBar
                            )
                        ).append('\n')
                    }
                }

                // Restrained elastic mapping bridge (only in ELASTIC mode)
                if (band.neckBottomY > band.neckTopY + 1f) {
                    val avgSpacing = if (band.spineCols.size > 1) band.bandContentWidth / (band.spineCols.size - 1) else 99f
                    val dense = avgSpacing < 18f
                    val midY = (band.neckTopY + band.neckBottomY) * 0.5f
                    for ((idx, col) in band.spineCols.withIndex()) {
                        val isMajor = idx == 0 || idx == band.spineCols.lastIndex || col.usage >= 2
                        if (dense && !isMajor) continue
                        val linX = band.linearXForTime(col.time)
                        val opacity = if (isMajor) 0.55f else 0.30f
                        append(
                            String.format(
                                Locale.US,
                                """<path d="M %.1f %.1f C %.1f %.1f %.1f %.1f %.1f %.1f" fill="none" stroke="%s" stroke-width="0.7" opacity="%.2f"/>""",
                                linX, band.neckTopY, linX, midY, col.x, midY, col.x, band.neckBottomY, rule, opacity
                            )
                        ).append('\n')
                    }
                }

                // Clean horizontal non-overlapping time ticks
                if (!layout.hideTicks) {
                    val baselineY = band.tiersTopY - 4f
                    for (col in band.spineCols) {
                        if (!col.labelVisible) continue
                        append(
                            String.format(
                                Locale.US,
                                """<text x="%.1f" y="%.1f" font-family="monospace" font-size="9.5" fill="%s">%s</text>""",
                                col.labelLeftX, baselineY, muted, esc(col.label)
                            )
                        ).append('\n')
                    }
                    for (iso in band.isolatedPointTicks) {
                        if (!iso.labelVisible) continue
                        append(
                            String.format(
                                Locale.US,
                                """<text x="%.1f" y="%.1f" font-family="monospace" font-size="9" fill="%s" opacity="0.75">%s</text>""",
                                iso.labelLeftX, baselineY, muted, esc(iso.label)
                            )
                        ).append('\n')
                    }
                }

                // Tiers
                for (ti in band.displayOrder) {
                    val tl = band.tiersLayout[ti] ?: continue
                    val tierLabel = if (tl.tier.name.isEmpty()) "∅" else tl.tier.name
                    append(
                        String.format(
                            Locale.US,
                            """<text x="%.1f" y="%.1f" font-family="sans-serif" font-size="10.5" fill="%s">%d · %s</text>""",
                            layout.padX + 4f, tl.midY + 3.5f, muted, tl.displayNumber, esc(tierLabel)
                        )
                    ).append('\n')

                    when (val meta = tl.meta) {
                        is TierLayoutMeta.IntervalMeta -> {
                            for (gap in meta.gaps) {
                                val gx0 = band.elasticXForTime(max(gap.xmin, band.startT))
                                val gx1 = band.elasticXForTime(min(gap.xmax, band.endT))
                                if (gx1 > gx0 + 0.5f) {
                                    append(
                                        String.format(
                                            Locale.US,
                                            """<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="none" stroke="%s" stroke-dasharray="3,2" stroke-width="0.8"/>""",
                                            gx0, tl.y, gx1 - gx0, tl.height, gapCol
                                        )
                                    ).append('\n')
                                }
                            }
                            for (viv in tl.visibleIntervals) {
                                val laneY = tl.y + (tl.laneOffsets.getOrNull(viv.positioned.lane) ?: 0f)
                                val laneH = tl.laneHeights.getOrNull(viv.positioned.lane) ?: tl.height
                                val fill = if (viv.positioned.interval.text.isEmpty()) emptyFill else bg
                                append(
                                    String.format(
                                        Locale.US,
                                        """<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s" stroke="%s" stroke-width="0.8"/>""",
                                        viv.x0, laneY, viv.width, laneH, fill, borderCol
                                    )
                                ).append('\n')
                                if (viv.rich != null) {
                                    var ly = laneY + (laneH - viv.rich.height) * 0.5f + 12f
                                    for (line in viv.rich.lines) {
                                        val txt = line.tokens.joinToString("") { it.str }
                                        val col = if (line.tokens.all { it.kind != RichTokenKind.TEXT }) metaCol else ink
                                        append(
                                            String.format(
                                                Locale.US,
                                                """<text x="%.1f" y="%.1f" text-anchor="middle" font-family="sans-serif" font-size="12" fill="%s">%s</text>""",
                                                (viv.x0 + viv.x1) * 0.5f, ly, col, esc(txt)
                                            )
                                        ).append('\n')
                                        ly += viv.rich.lineHeight
                                    }
                                }
                            }
                        }
                        is TierLayoutMeta.TextMeta -> {
                            val wy = tl.wireY ?: tl.midY
                            append(
                                String.format(
                                    Locale.US,
                                    """<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="0.9"/>""",
                                    band.originX, wy, band.originX + band.bandContentWidth, wy, muted
                                )
                            ).append('\n')
                            val sortedPts = tl.visiblePoints.sortedByDescending { it.level }
                            for (vp in sortedPts) {
                                if (vp.rich != null && vp.level >= 0) {
                                    val boxTop = tl.y + (tl.levelOffsetsY.getOrNull(vp.level) ?: 22f)
                                    append(
                                        String.format(
                                            Locale.US,
                                            """<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="0.8"/>""",
                                            vp.x, wy, vp.x, boxTop, muted
                                        )
                                    ).append('\n')
                                    append(
                                        String.format(
                                            Locale.US,
                                            """<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="3" fill="%s" stroke="%s" stroke-width="0.8"/>""",
                                            vp.boxX, boxTop, vp.boxWidth, vp.boxHeight, bg, borderCol
                                        )
                                    ).append('\n')
                                    val txt = vp.rich.lines.joinToString(" ") { l -> l.tokens.joinToString("") { it.str } }
                                    append(
                                        String.format(
                                            Locale.US,
                                            """<text x="%.1f" y="%.1f" text-anchor="middle" font-family="sans-serif" font-size="11" fill="%s">%s</text>""",
                                            vp.boxX + vp.boxWidth * 0.5f, boxTop + vp.boxHeight * 0.5f + 4f, ink, esc(txt)
                                        )
                                    ).append('\n')
                                }
                                val d = 3.5f
                                val fill = if (vp.rich == null) bg else ink
                                append(
                                    String.format(
                                        Locale.US,
                                        """<polygon points="%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f" fill="%s" stroke="%s" stroke-width="0.9"/>""",
                                        vp.x, wy - d, vp.x + d, wy, vp.x, wy + d, vp.x - d, wy, fill, ink
                                    )
                                ).append('\n')
                            }
                        }
                    }
                }
            }
            append("</svg>")
        }
    }
}
