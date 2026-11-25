package com.example.textgrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.textgrid.layout.BandLayout
import com.example.textgrid.layout.CachedTextWidthMeasurer
import com.example.textgrid.layout.RichTokenKind
import com.example.textgrid.layout.ScoreFontRole
import com.example.textgrid.layout.ScoreLayout
import com.example.textgrid.layout.TextGridRichText
import com.example.textgrid.layout.TextGridSafeMath
import com.example.textgrid.layout.TextWidthMeasurer
import com.example.textgrid.layout.TierLayout
import com.example.textgrid.layout.TierLayoutMeta
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridSelection
import com.example.textgrid.model.TextGridTier
import com.example.textgrid.model.TimeRange
import com.example.ui.theme.LocalPhoneticFontFamily
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure read-only Dual-Scale Speech Score Canvas:
 * - Compact 6-7dp rounded 1:1 true-duration overview bar (active speech tint + collapsed silence hatch)
 * - Restrained elastic time mapping bridge (only in ELASTIC mode; sparse/stubbed on dense spans)
 * - Crisp non-overlapping horizontal tabular time ticks with common-prefix dimming
 * - Refined left tier Gutter + crisp IntervalTier / TextTier lanes using [LocalPhoneticFontFamily]
 * - Smooth 60fps playhead vertical line + top indicator dot
 * - Tap interval/point/timebar to highlight & play; 2-finger pinch zoom without stealing 1-finger scroll
 */
@Composable
fun DualScaleScoreCanvas(
    doc: TextGridDocument,
    layout: ScoreLayout,
    palette: TextGridScorePalette,
    selection: TextGridSelection?,
    playheadTimeSec: Double? = null,
    activePlayRange: TimeRange? = null,
    onSelect: (TextGridSelection?) -> Unit = {},
    onSeekTime: ((Double) -> Unit)? = null,
    onPinchZoom: ((zoomDelta: Float, focusTimeSec: Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val phoneticFontFamily = LocalPhoneticFontFamily.current
    val textMeasurer = rememberTextMeasurer()
    val widthMeasurer = remember(textMeasurer, density, phoneticFontFamily) {
        ComposeScoreTextWidthMeasurer(textMeasurer, density.density, phoneticFontFamily)
    }

    val safeW = TextGridSafeMath.safeCoerceIn(layout.widthDp, 220f, 65536f, 360f)
    val safeH = TextGridSafeMath.safeCoerceIn(layout.heightDp, 84f, 65536f, 120f)

    val styles = remember(phoneticFontFamily, palette) {
        ScoreCanvasTextStyles(
            intervalText = TextStyle(
                fontFamily = phoneticFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = palette.ink
            ),
            pointText = TextStyle(
                fontFamily = phoneticFontFamily,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = palette.ink
            ),
            tierName = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = palette.muted
            ),
            tierIndex = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.faint
            ),
            tickLabel = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = palette.muted
            ),
            tickFaintPrefix = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Normal,
                color = palette.faintTick
            ),
            glossIpa = TextStyle(
                fontFamily = phoneticFontFamily,
                fontSize = 10.sp,
                color = palette.glossColor
            ),
            glossRaw = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = palette.faint
            ),
            ctrlTag = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.ctrlCharColor
            ),
            metaSymbol = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = palette.metaSymbolColor
            ),
            collapsedMarker = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.faint
            )
        )
    }

    val tapModifier = Modifier.pointerInput(doc, layout, onSelect, onSeekTime) {
        detectTapGestures { offsetPx ->
            val dpX = offsetPx.x / density.density
            val dpY = offsetPx.y / density.density
            if (!dpX.isFinite() || !dpY.isFinite()) return@detectTapGestures
            handleReadOnlyTap(
                dpX = dpX,
                dpY = dpY,
                layout = layout,
                onSelect = onSelect,
                onSeekTime = onSeekTime
            )
        }
    }

    val pinchModifier = if (onPinchZoom != null) {
        Modifier.pointerInput(layout, onPinchZoom) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        val zoomChange = event.calculateZoom()
                        if (zoomChange.isFinite() && abs(zoomChange - 1f) > 0.015f) {
                            val cx = ((pressed[0].position.x + pressed[1].position.x) * 0.5f) / density.density
                            val cy = ((pressed[0].position.y + pressed[1].position.y) * 0.5f) / density.density
                            val band = findBandAtY(layout, cy) ?: layout.bands.firstOrNull()
                            val focusT = band?.timeFromElasticX(cx) ?: doc.xmin
                            onPinchZoom(zoomChange, focusT)
                            pressed.forEach { it.consume() }
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(width = safeW.dp, height = safeH.dp)
            .then(tapModifier)
            .then(pinchModifier)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawScoreBaseLayer(
                doc = doc,
                layout = layout,
                palette = palette,
                selection = selection,
                styles = styles,
                textMeasurer = textMeasurer,
                widthMeasurer = widthMeasurer
            )
        }

        if (playheadTimeSec != null || activePlayRange != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawPlayheadOverlay(
                    layout = layout,
                    palette = palette,
                    playheadTimeSec = playheadTimeSec,
                    activePlayRange = activePlayRange
                )
            }
        }
    }
}

@Immutable
private data class ScoreCanvasTextStyles(
    val intervalText: TextStyle,
    val pointText: TextStyle,
    val tierName: TextStyle,
    val tierIndex: TextStyle,
    val tickLabel: TextStyle,
    val tickFaintPrefix: TextStyle,
    val glossIpa: TextStyle,
    val glossRaw: TextStyle,
    val ctrlTag: TextStyle,
    val metaSymbol: TextStyle,
    val collapsedMarker: TextStyle
)

/**
 * Compose-backed [TextWidthMeasurer] matching the exact typography drawn by [DualScaleScoreCanvas].
 */
class ComposeScoreTextWidthMeasurer(
    private val textMeasurer: TextMeasurer,
    private val density: Float,
    phoneticFontFamily: FontFamily
) : TextWidthMeasurer {
    private val stylesByRole = mapOf(
        ScoreFontRole.INTERVAL_TEXT to TextStyle(fontFamily = phoneticFontFamily, fontSize = 13.sp),
        ScoreFontRole.POINT_TEXT to TextStyle(fontFamily = phoneticFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
        ScoreFontRole.TIER_NAME to TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        ScoreFontRole.TICK_LABEL to TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, fontWeight = FontWeight.Medium),
        ScoreFontRole.GLOSS_IPA to TextStyle(fontFamily = phoneticFontFamily, fontSize = 10.sp),
        ScoreFontRole.GLOSS_RAW to TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.5.sp),
        ScoreFontRole.CTRL_TAG to TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    )

    private val cached = CachedTextWidthMeasurer(
        delegate = TextWidthMeasurer { text, role, fontScale ->
            val safeDensity = TextGridSafeMath.safeAtLeast(density, 0.5f)
            val safeScale = TextGridSafeMath.safeCoerceIn(fontScale, 0.6f, 2.5f, 1f)
            val base = stylesByRole[role] ?: TextStyle(fontSize = 12.sp)
            val scaled = if (abs(safeScale - 1f) > 0.01f) base.copy(fontSize = base.fontSize * safeScale) else base
            val measuredPx = textMeasurer.measure(text = AnnotatedString(text), style = scaled, maxLines = 1).size.width
            TextGridSafeMath.safeCoerceIn(measuredPx / safeDensity, 0f, 4096f, 0f)
        },
        maxEntries = 2048
    )

    override fun measurePx(text: String, role: ScoreFontRole, fontScale: Float): Float =
        cached.measurePx(text, role, fontScale)
}

private fun DrawScope.drawScoreBaseLayer(
    doc: TextGridDocument,
    layout: ScoreLayout,
    palette: TextGridScorePalette,
    selection: TextGridSelection?,
    styles: ScoreCanvasTextStyles,
    textMeasurer: TextMeasurer,
    widthMeasurer: TextWidthMeasurer
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density

    drawRect(color = palette.surfaceBg)

    val selectedTimes: Pair<Double, Double>? = when (selection) {
        is TextGridSelection.Interval -> {
            val iv = (doc.tiers.getOrNull(selection.tierIndex) as? TextGridTier.IntervalTier)
                ?.intervals?.getOrNull(selection.itemIndex)
            iv?.let { it.xmin to it.xmax }
        }
        is TextGridSelection.Point -> {
            val pt = (doc.tiers.getOrNull(selection.tierIndex) as? TextGridTier.TextTier)
                ?.points?.getOrNull(selection.itemIndex)
            pt?.let { it.number to it.number }
        }
        else -> null
    }

    for (band in layout.bands) {
        val bandTopPx = dp(band.tiersTopY)
        val bandBottomPx = dp(band.tiersBottomY)

        drawLinearOverviewBar(band, palette, selectedTimes, styles, textMeasurer)

        if (band.neckBottomY > band.neckTopY + 0.5f) {
            drawElasticMappingBridge(band, palette, selectedTimes)
        }

        if (!layout.hideTicks) {
            drawHorizontalTimeTicks(band, palette, styles, textMeasurer)
        }

        val gutterLeftPx = dp(layout.padX)
        val gutterRightPx = dp(band.originX - 4f)
        val gutterHeightPx = max(0f, bandBottomPx - bandTopPx)
        if (gutterRightPx > gutterLeftPx && gutterHeightPx > 1f) {
            drawRoundRect(
                color = palette.gutterBg,
                topLeft = Offset(gutterLeftPx, bandTopPx),
                size = Size(gutterRightPx - gutterLeftPx, gutterHeightPx),
                cornerRadius = CornerRadius(dp(6f), dp(6f))
            )
            drawLine(
                color = palette.gutterDivider,
                start = Offset(gutterRightPx, bandTopPx + dp(3f)),
                end = Offset(gutterRightPx, bandBottomPx - dp(3f)),
                strokeWidth = dp(0.75f)
            )
        }

        for ((idx, col) in band.spineCols.withIndex()) {
            if (idx == 0 || idx == band.spineCols.lastIndex || col.usage >= 2) {
                val xPx = dp(col.x)
                drawLine(
                    color = palette.bridgeMinor.copy(alpha = 0.42f),
                    start = Offset(xPx, bandTopPx),
                    end = Offset(xPx, bandBottomPx),
                    strokeWidth = dp(0.5f)
                )
            }
        }

        for (ti in band.displayOrder) {
            val tl = band.tiersLayout[ti] ?: continue
            val accent = palette.tierAccents[ti % palette.tierAccents.size]
            drawTierGutterHeader(
                tl = tl,
                band = band,
                padX = layout.padX,
                isTiny = layout.isTinyView,
                accent = accent,
                isSelectedTier = selection?.tierIndexOrNull == ti,
                palette = palette,
                styles = styles,
                textMeasurer = textMeasurer,
                widthMeasurer = widthMeasurer
            )

            when (tl.meta) {
                is TierLayoutMeta.IntervalMeta -> drawIntervalTier(
                    tl = tl,
                    band = band,
                    accent = accent,
                    palette = palette,
                    selection = selection,
                    styles = styles,
                    textMeasurer = textMeasurer
                )
                is TierLayoutMeta.TextMeta -> drawTextTier(
                    tl = tl,
                    band = band,
                    accent = accent,
                    palette = palette,
                    selection = selection,
                    styles = styles,
                    textMeasurer = textMeasurer
                )
            }
        }
    }
}

private fun DrawScope.drawLinearOverviewBar(
    band: BandLayout,
    palette: TextGridScorePalette,
    selectedTimes: Pair<Double, Double>?,
    styles: ScoreCanvasTextStyles,
    textMeasurer: TextMeasurer
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density

    val x = dp(band.originX)
    val y = dp(band.linearY)
    val w = dp(band.bandContentWidth)
    val h = dp(band.linearHeight)
    if (w <= 1f || h <= 1f) return
    val radius = CornerRadius(h * 0.5f, h * 0.5f)

    drawRoundRect(
        color = palette.linearTrackBg,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = radius
    )

    clipRect(left = x, top = y, right = x + w, bottom = y + h) {
        for (seg in band.linearSegs) {
            val sx0 = dp(seg.x0)
            val sx1 = dp(seg.x1)
            val sw = max(0f, sx1 - sx0)
            if (sw <= 0.4f) continue

            if (seg.collapsed) {
                drawRect(
                    color = palette.linearCollapsedBg,
                    topLeft = Offset(sx0, y),
                    size = Size(sw, h)
                )
                if (sw >= dp(14f)) {
                    val mark = textMeasurer.measure(AnnotatedString("//"), styles.collapsedMarker, maxLines = 1)
                    drawText(
                        textLayoutResult = mark,
                        topLeft = Offset(
                            sx0 + (sw - mark.size.width) * 0.5f,
                            y + (h - mark.size.height) * 0.5f
                        )
                    )
                }
            } else if (seg.hasActiveContent) {
                drawRect(
                    color = palette.linearActiveFill,
                    topLeft = Offset(sx0, y),
                    size = Size(sw, h)
                )
            }
        }

        if (selectedTimes != null) {
            val (t0, t1) = selectedTimes
            if (t1 >= band.startT && t0 <= band.endT) {
                val lx0 = dp(band.linearXForTime(max(t0, band.startT)))
                val lx1 = dp(band.linearXForTime(min(t1, band.endT)))
                val lw = max(dp(2.5f), lx1 - lx0)
                drawRect(
                    color = palette.selectionAccent.copy(alpha = 0.78f),
                    topLeft = Offset(lx0, y),
                    size = Size(lw, h)
                )
            }
        }
    }

    drawRoundRect(
        color = palette.rule.copy(alpha = 0.72f),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = radius,
        style = Stroke(width = dp(0.6f))
    )
}

private fun DrawScope.drawElasticMappingBridge(
    band: BandLayout,
    palette: TextGridScorePalette,
    selectedTimes: Pair<Double, Double>?
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density

    val topY = dp(band.neckTopY) + dp(0.5f)
    val botY = dp(band.neckBottomY)
    val midY = (topY + botY) * 0.5f
    if (botY <= topY + 1f) return

    val avgSpacing = if (band.spineCols.size > 1) {
        band.bandContentWidth / (band.spineCols.size - 1)
    } else 99f
    val dense = avgSpacing < 18f

    for ((idx, col) in band.spineCols.withIndex()) {
        val linX = dp(band.linearXForTime(col.time))
        val elX = dp(col.x)
        val isEndpoint = idx == 0 || idx == band.spineCols.lastIndex
        val isMajor = isEndpoint || col.usage >= 2

        if (dense && !isMajor) {
            drawLine(
                color = palette.bridgeMinor.copy(alpha = 0.65f),
                start = Offset(linX, topY),
                end = Offset(linX, topY + dp(2.5f)),
                strokeWidth = dp(0.75f)
            )
            drawLine(
                color = palette.bridgeMinor.copy(alpha = 0.65f),
                start = Offset(elX, botY - dp(2.5f)),
                end = Offset(elX, botY),
                strokeWidth = dp(0.75f)
            )
        } else {
            val path = Path().apply {
                moveTo(linX, topY)
                cubicTo(linX, midY, elX, midY, elX, botY)
            }
            drawPath(
                path = path,
                color = if (isMajor) palette.bridgeMajor else palette.bridgeMinor,
                style = Stroke(width = dp(if (isEndpoint) 0.9f else 0.7f))
            )
        }
    }

    if (selectedTimes != null) {
        val (t0, t1) = selectedTimes
        for (t in listOf(t0, t1)) {
            if (t >= band.startT && t <= band.endT) {
                val linX = dp(band.linearXForTime(t))
                val elX = dp(band.elasticXForTime(t))
                val path = Path().apply {
                    moveTo(linX, topY)
                    cubicTo(linX, midY, elX, midY, elX, botY)
                }
                drawPath(
                    path = path,
                    color = palette.selectionAccent.copy(alpha = 0.72f),
                    style = Stroke(width = dp(1.1f))
                )
            }
        }
    }
}

private fun DrawScope.drawHorizontalTimeTicks(
    band: BandLayout,
    palette: TextGridScorePalette,
    styles: ScoreCanvasTextStyles,
    textMeasurer: TextMeasurer
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density

    val tickY = dp(band.tickTopY)
    val tiersTopPx = dp(band.tiersTopY)
    val tickMarkBot = tiersTopPx - dp(1f)
    val tickMarkTop = tickMarkBot - dp(3f)

    for (col in band.spineCols) {
        val xPx = dp(col.x)
        drawLine(
            color = if (col.labelVisible) palette.border.copy(alpha = 0.55f) else palette.rule.copy(alpha = 0.45f),
            start = Offset(xPx, tickMarkTop),
            end = Offset(xPx, tickMarkBot),
            strokeWidth = dp(if (col.labelVisible) 0.85f else 0.6f)
        )
    }

    var prevLabel: String? = null
    for (col in band.spineCols) {
        if (!col.labelVisible || col.label.isEmpty()) continue
        drawDimmedTickText(
            label = col.label,
            prevLabel = prevLabel,
            leftPx = dp(col.labelLeftX),
            topPx = tickY + dp(1f),
            styles = styles,
            textMeasurer = textMeasurer
        )
        prevLabel = col.label
    }

    for (iso in band.isolatedPointTicks) {
        if (!iso.labelVisible || iso.label.isEmpty()) continue
        val res = textMeasurer.measure(AnnotatedString(iso.label), styles.tickFaintPrefix, maxLines = 1)
        drawText(
            textLayoutResult = res,
            topLeft = Offset(dp(iso.labelLeftX), tickY + dp(1f))
        )
    }
}

private fun DrawScope.drawDimmedTickText(
    label: String,
    prevLabel: String?,
    leftPx: Float,
    topPx: Float,
    styles: ScoreCanvasTextStyles,
    textMeasurer: TextMeasurer
) {
    var commonLen = 0
    if (prevLabel != null && label.contains('.') && prevLabel.contains('.')) {
        val maxCmp = min(label.length, prevLabel.length)
        while (commonLen < maxCmp && label[commonLen] == prevLabel[commonLen]) {
            commonLen++
        }
        val dotIdx = label.indexOf('.')
        if (commonLen <= dotIdx || commonLen >= label.length) {
            commonLen = 0
        }
    }

    if (commonLen > 0) {
        val prefix = label.substring(0, commonLen)
        val suffix = label.substring(commonLen)
        val prefixRes = textMeasurer.measure(AnnotatedString(prefix), styles.tickFaintPrefix, maxLines = 1)
        val suffixRes = textMeasurer.measure(AnnotatedString(suffix), styles.tickLabel, maxLines = 1)
        drawText(prefixRes, topLeft = Offset(leftPx, topPx))
        drawText(suffixRes, topLeft = Offset(leftPx + prefixRes.size.width, topPx))
    } else {
        val res = textMeasurer.measure(AnnotatedString(label), styles.tickLabel, maxLines = 1)
        drawText(res, topLeft = Offset(leftPx, topPx))
    }
}

private fun DrawScope.drawTierGutterHeader(
    tl: TierLayout,
    band: BandLayout,
    padX: Float,
    isTiny: Boolean,
    accent: Color,
    isSelectedTier: Boolean,
    palette: TextGridScorePalette,
    styles: ScoreCanvasTextStyles,
    textMeasurer: TextMeasurer,
    widthMeasurer: TextWidthMeasurer
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density

    val leftPx = dp(padX + 4f)
    val rightLimitPx = dp(band.originX - 8f)
    val midYPx = dp(tl.midY)

    val barTop = dp(tl.y + 4f)
    val barBot = dp(tl.y + tl.height - 4f)
    if (barBot > barTop + 2f) {
        drawLine(
            color = if (isSelectedTier) palette.selectionAccent else accent.copy(alpha = 0.52f),
            start = Offset(dp(padX + 1.5f), barTop),
            end = Offset(dp(padX + 1.5f), barBot),
            strokeWidth = dp(if (isSelectedTier) 2.2f else 1.6f),
            cap = StrokeCap.Round
        )
    }

    val numText = "${tl.displayNumber}"
    val numRes = textMeasurer.measure(
        AnnotatedString(numText),
        styles.tierIndex.copy(color = if (isSelectedTier) palette.selectionAccent else palette.faint),
        maxLines = 1
    )
    drawText(
        textLayoutResult = numRes,
        topLeft = Offset(leftPx + dp(2f), midYPx - numRes.size.height * 0.5f)
    )

    val showFullName = !(isTiny && band.bandIndex > 0)
    if (!showFullName) {
        val iconX = leftPx + numRes.size.width + dp(6f)
        if (tl.tier is TextGridTier.TextTier) {
            val d = dp(3f)
            val diamond = Path().apply {
                moveTo(iconX, midYPx - d)
                lineTo(iconX + d, midYPx)
                lineTo(iconX, midYPx + d)
                lineTo(iconX - d, midYPx)
                close()
            }
            drawPath(diamond, color = accent.copy(alpha = 0.7f))
        } else {
            drawRect(
                color = accent.copy(alpha = 0.45f),
                topLeft = Offset(iconX - dp(3f), midYPx - dp(2.5f)),
                size = Size(dp(7f), dp(5f)),
                style = Stroke(width = dp(0.9f))
            )
        }
        return
    }

    val nameStartPx = leftPx + numRes.size.width + dp(6f)
    val availDp = TextGridSafeMath.safeAtLeast((rightLimitPx - nameStartPx) / density, 16f)
    val rawName = tl.tier.name.ifEmpty { "∅" }
    val displayName = TextGridRichText.truncateMiddleEllipsis(
        name = rawName,
        maxWidthPx = availDp,
        measurer = widthMeasurer,
        role = ScoreFontRole.TIER_NAME
    )
    val nameStyle = styles.tierName.copy(
        color = if (isSelectedTier) palette.selectionAccent else palette.muted,
        fontWeight = if (isSelectedTier) FontWeight.SemiBold else FontWeight.Medium
    )
    val nameRes = textMeasurer.measure(AnnotatedString(displayName), nameStyle, maxLines = 1)
    clipRect(left = nameStartPx, top = dp(tl.y), right = rightLimitPx + dp(2f), bottom = dp(tl.y + tl.height)) {
        drawText(
            textLayoutResult = nameRes,
            topLeft = Offset(nameStartPx, midYPx - nameRes.size.height * 0.5f)
        )
    }
}

private fun DrawScope.drawIntervalTier(
    tl: TierLayout,
    band: BandLayout,
    accent: Color,
    palette: TextGridScorePalette,
    selection: TextGridSelection?,
    styles: ScoreCanvasTextStyles,
    textMeasurer: TextMeasurer
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density
    val meta = tl.meta as TierLayoutMeta.IntervalMeta

    val originPx = dp(band.originX)
    val tierTopPx = dp(tl.y)
    val tierWPx = dp(band.bandContentWidth)
    val tierHPx = dp(tl.height)

    drawRoundRect(
        color = palette.emptyCell,
        topLeft = Offset(originPx, tierTopPx),
        size = Size(tierWPx, tierHPx),
        cornerRadius = CornerRadius(dp(4f), dp(4f))
    )

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(dp(3.5f), dp(2.5f)))
    for (gap in meta.gaps) {
        val gx0 = dp(band.elasticXForTime(max(gap.xmin, band.startT)))
        val gx1 = dp(band.elasticXForTime(min(gap.xmax, band.endT)))
        val gw = gx1 - gx0
        if (gw > dp(1f)) {
            drawRect(color = palette.gapBg, topLeft = Offset(gx0, tierTopPx), size = Size(gw, tierHPx))
            drawRect(
                color = palette.gapStroke,
                topLeft = Offset(gx0, tierTopPx),
                size = Size(gw, tierHPx),
                style = Stroke(width = dp(0.85f), pathEffect = dashEffect)
            )
        }
    }

    for (viv in tl.visibleIntervals) {
        val laneY = tl.y + (tl.laneOffsets.getOrNull(viv.positioned.lane) ?: 0f)
        val laneH = tl.laneHeights.getOrNull(viv.positioned.lane) ?: tl.height
        val x0 = dp(viv.x0)
        val y0 = dp(laneY)
        val w = max(dp(1.5f), dp(viv.width))
        val h = dp(laneH)
        val isEmpty = viv.positioned.interval.text.isEmpty()
        val isSelected = selection is TextGridSelection.Interval &&
            selection.tierIndex == tl.tierIndex &&
            selection.itemIndex == viv.positioned.origIndex

        val fillColor = when {
            isSelected -> palette.selectionFill
            isEmpty -> palette.emptyCell
            else -> palette.cellFill
        }

        drawRect(color = fillColor, topLeft = Offset(x0, y0), size = Size(w, h))

        if (!isEmpty && !isSelected) {
            drawLine(
                color = accent.copy(alpha = if (palette.isBw) 0.18f else 0.30f),
                start = Offset(x0 + dp(0.5f), y0 + dp(0.8f)),
                end = Offset(x0 + w - dp(0.5f), y0 + dp(0.8f)),
                strokeWidth = dp(1.4f)
            )
        }

        drawRect(
            color = if (isSelected) palette.selectionAccent else palette.border.copy(alpha = if (isEmpty) 0.45f else 0.72f),
            topLeft = Offset(x0, y0),
            size = Size(w, h),
            style = Stroke(width = dp(if (isSelected) 1.5f else 0.8f))
        )

        val rich = viv.rich
        if (rich != null && w >= dp(8f)) {
            clipRect(left = x0 + dp(2f), top = y0 + dp(1f), right = x0 + w - dp(2f), bottom = y0 + h - dp(1f)) {
                val totalTextH = dp(rich.height)
                var curTop = y0 + (h - totalTextH) * 0.5f

                for (line in rich.lines) {
                    val lineWPx = dp(line.width)
                    var curX = x0 + (w - lineWPx) * 0.5f
                    for (tok in line.tokens) {
                        val tokStyle = when (tok.kind) {
                            RichTokenKind.TEXT -> styles.intervalText
                            RichTokenKind.CTRL -> styles.ctrlTag
                            else -> styles.metaSymbol
                        }
                        val res = textMeasurer.measure(AnnotatedString(tok.str), tokStyle, maxLines = 1)
                        val textY = curTop + (dp(rich.lineHeight) - res.size.height) * 0.5f
                        drawText(res, topLeft = Offset(curX, textY))
                        curX += res.size.width + if (tok.kind == RichTokenKind.TEXT) 0f else dp(1.5f)
                    }
                    curTop += dp(rich.lineHeight)
                }

                if (rich.gloss != null) {
                    val glossStyle = if (rich.glossIsRaw) styles.glossRaw else styles.glossIpa
                    val glossRes = textMeasurer.measure(AnnotatedString(rich.gloss), glossStyle, maxLines = 1)
                    drawText(
                        textLayoutResult = glossRes,
                        topLeft = Offset(
                            x0 + (w - glossRes.size.width) * 0.5f,
                            curTop + (dp(rich.glossHeight) - glossRes.size.height) * 0.5f
                        )
                    )
                }
            }
        }
    }

    for (ov in meta.overlaps) {
        val ox0 = dp(band.elasticXForTime(max(ov.xmin, band.startT)))
        val ox1 = dp(band.elasticXForTime(min(ov.xmax, band.endT)))
        if (ox1 > ox0 + dp(1f)) {
            drawLine(
                color = palette.overlapStroke,
                start = Offset(ox0, tierTopPx + dp(1.5f)),
                end = Offset(ox1, tierTopPx + dp(1.5f)),
                strokeWidth = dp(2f)
            )
        }
    }

    for (vz in tl.visibleZeros) {
        val zx = dp(vz.x)
        val zy = dp(tl.midY)
        val d = dp(4f)
        val diamond = Path().apply {
            moveTo(zx, zy - d)
            lineTo(zx + d, zy)
            lineTo(zx, zy + d)
            lineTo(zx - d, zy)
            close()
        }
        drawPath(diamond, color = palette.zeroBg)
        drawPath(diamond, color = palette.gapStroke, style = Stroke(width = dp(1f)))
    }
}

private fun DrawScope.drawTextTier(
    tl: TierLayout,
    band: BandLayout,
    accent: Color,
    palette: TextGridScorePalette,
    selection: TextGridSelection?,
    styles: ScoreCanvasTextStyles,
    textMeasurer: TextMeasurer
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density

    val wy = dp(tl.wireY ?: tl.midY)
    val leftPx = dp(band.originX)
    val rightPx = dp(band.originX + band.bandContentWidth)

    drawLine(
        color = palette.wire.copy(alpha = 0.55f),
        start = Offset(leftPx, wy),
        end = Offset(rightPx, wy),
        strokeWidth = dp(1f)
    )

    val sortedPts = tl.visiblePoints.sortedByDescending { it.level }
    for (vp in sortedPts) {
        val px = dp(vp.x)
        val isSelected = selection is TextGridSelection.Point &&
            selection.tierIndex == tl.tierIndex &&
            selection.itemIndex == vp.positioned.origIndex
        val strokeColor = if (isSelected) palette.selectionAccent else accent.copy(alpha = 0.85f)

        if (vp.rich != null && vp.level >= 0) {
            val boxTop = dp(tl.y + (tl.levelOffsetsY.getOrNull(vp.level) ?: 22f))
            val boxLeft = dp(vp.boxX)
            val boxW = dp(vp.boxWidth)
            val boxH = dp(vp.boxHeight)

            drawLine(
                color = if (isSelected) palette.selectionAccent else palette.rule,
                start = Offset(px, wy),
                end = Offset(px, boxTop),
                strokeWidth = dp(0.9f)
            )
            drawRoundRect(
                color = if (isSelected) palette.selectionFill else palette.cellFill,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(dp(4f), dp(4f))
            )
            drawRoundRect(
                color = if (isSelected) palette.selectionAccent else palette.border.copy(alpha = 0.72f),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(dp(4f), dp(4f)),
                style = Stroke(width = dp(if (isSelected) 1.3f else 0.8f))
            )

            val labelText = vp.rich.lines.joinToString(" ") { l -> l.tokens.joinToString("") { it.str } }
            val res = textMeasurer.measure(AnnotatedString(labelText), styles.pointText, maxLines = 1)
            clipRect(left = boxLeft + dp(3f), top = boxTop, right = boxLeft + boxW - dp(3f), bottom = boxTop + boxH) {
                drawText(
                    textLayoutResult = res,
                    topLeft = Offset(
                        boxLeft + (boxW - res.size.width) * 0.5f,
                        boxTop + (boxH - res.size.height) * 0.5f
                    )
                )
            }
        }

        val d = dp(3.8f)
        val diamond = Path().apply {
            moveTo(px, wy - d)
            lineTo(px + d, wy)
            lineTo(px, wy + d)
            lineTo(px - d, wy)
            close()
        }
        drawPath(diamond, color = if (vp.rich == null) palette.cellFill else strokeColor)
        drawPath(diamond, color = strokeColor, style = Stroke(width = dp(1f)))
    }
}

private fun DrawScope.drawPlayheadOverlay(
    layout: ScoreLayout,
    palette: TextGridScorePalette,
    playheadTimeSec: Double?,
    activePlayRange: TimeRange?
) {
    fun dp(v: Float): Float = TextGridSafeMath.safeFinite(v, 0f) * density

    for (band in layout.bands) {
        if (activePlayRange != null && activePlayRange.isValid) {
            if (activePlayRange.end >= band.startT && activePlayRange.start <= band.endT) {
                val rStart = max(activePlayRange.start, band.startT)
                val rEnd = min(activePlayRange.end, band.endT)
                val ex0 = dp(band.elasticXForTime(rStart))
                val ex1 = dp(band.elasticXForTime(rEnd))
                if (ex1 > ex0 + 1f) {
                    drawRect(
                        color = palette.playRangeFill,
                        topLeft = Offset(ex0, dp(band.tiersTopY)),
                        size = Size(ex1 - ex0, dp(band.tiersBottomY - band.tiersTopY))
                    )
                }
            }
        }

        if (playheadTimeSec != null && playheadTimeSec.isFinite() &&
            playheadTimeSec >= band.startT - 1e-6 && playheadTimeSec <= band.endT + 1e-6
        ) {
            val linX = dp(band.linearXForTime(playheadTimeSec))
            val elX = dp(band.elasticXForTime(playheadTimeSec))
            val linCenterY = dp(band.linearY + band.linearHeight * 0.5f)
            val neckTop = dp(band.neckTopY)
            val neckBot = dp(band.neckBottomY)
            val tiersBot = dp(band.tiersBottomY)

            drawLine(
                color = palette.playheadColor,
                start = Offset(elX, neckBot),
                end = Offset(elX, tiersBot),
                strokeWidth = dp(1.5f),
                cap = StrokeCap.Round
            )

            if (neckBot > neckTop + 1f) {
                val midY = (neckTop + neckBot) * 0.5f
                val bridgePath = Path().apply {
                    moveTo(linX, neckTop)
                    cubicTo(linX, midY, elX, midY, elX, neckBot)
                }
                drawPath(
                    path = bridgePath,
                    color = palette.playheadColor.copy(alpha = 0.85f),
                    style = Stroke(width = dp(1.4f))
                )
            }

            drawCircle(
                color = palette.surfaceBg,
                radius = dp(4.5f),
                center = Offset(linX, linCenterY)
            )
            drawCircle(
                color = palette.playheadColor,
                radius = dp(3.3f),
                center = Offset(linX, linCenterY)
            )
        }
    }
}

private fun findBandAtY(layout: ScoreLayout, dpY: Float): BandLayout? {
    for (band in layout.bands) {
        if (dpY >= band.linearY - 8f && dpY <= band.tiersBottomY + 12f) {
            return band
        }
    }
    return null
}

private fun handleReadOnlyTap(
    dpX: Float,
    dpY: Float,
    layout: ScoreLayout,
    onSelect: (TextGridSelection?) -> Unit,
    onSeekTime: ((Double) -> Unit)?
) {
    val band = findBandAtY(layout, dpY) ?: run {
        onSelect(null)
        return
    }

    if (dpY >= band.linearY - 6f && dpY <= band.neckBottomY + 2f &&
        dpX >= band.originX && dpX <= band.originX + band.bandContentWidth
    ) {
        val t = band.timeFromLinearX(dpX)
        onSeekTime?.invoke(t)
        return
    }

    for (ti in band.displayOrder) {
        val tl = band.tiersLayout[ti] ?: continue
        if (dpY < tl.y - 2f || dpY > tl.y + tl.height + 2f) continue

        if (dpX < band.originX) {
            onSelect(TextGridSelection.Tier(ti))
            return
        }

        when (tl.meta) {
            is TierLayoutMeta.IntervalMeta -> {
                for (viv in tl.visibleIntervals) {
                    val laneY = tl.y + (tl.laneOffsets.getOrNull(viv.positioned.lane) ?: 0f)
                    val laneH = tl.laneHeights.getOrNull(viv.positioned.lane) ?: tl.height
                    if (dpX >= viv.x0 && dpX <= viv.x1 && dpY >= laneY && dpY <= laneY + laneH) {
                        onSelect(TextGridSelection.Interval(ti, viv.positioned.origIndex))
                        return
                    }
                }
            }
            is TierLayoutMeta.TextMeta -> {
                var bestIdx = -1
                var bestDist = Float.POSITIVE_INFINITY
                for (vp in tl.visiblePoints) {
                    if (vp.rich != null && vp.level >= 0) {
                        val boxTop = tl.y + (tl.levelOffsetsY.getOrNull(vp.level) ?: 22f)
                        if (dpX >= vp.boxX && dpX <= vp.boxX + vp.boxWidth &&
                            dpY >= boxTop && dpY <= boxTop + vp.boxHeight
                        ) {
                            onSelect(TextGridSelection.Point(ti, vp.positioned.origIndex))
                            return
                        }
                    }
                    val dist = abs(dpX - vp.x)
                    if (dist <= 16f && dist < bestDist) {
                        bestDist = dist
                        bestIdx = vp.positioned.origIndex
                    }
                }
                if (bestIdx >= 0) {
                    onSelect(TextGridSelection.Point(ti, bestIdx))
                    return
                }
            }
        }
    }

    onSelect(null)
}
