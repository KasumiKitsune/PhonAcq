package com.example.textgrid.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.StudioMotion
import com.example.ui.components.rememberStudioInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.layout.DualScaleLayoutEngine
import com.example.textgrid.layout.DualScaleLayoutOptions
import com.example.textgrid.layout.TextGridSafeMath
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.model.TextGridOpenMode
import com.example.textgrid.model.TextGridSelection
import com.example.textgrid.model.TextGridTier
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.model.TimeRange
import com.example.ui.theme.LocalPhoneticFontFamily
import java.util.Locale

/**
 * Refined, pure read-only TextGrid card with single-row header and seamless `[Score | Source]` toggle.
 *
 * Uses an OUTER unscrollable [BoxWithConstraints] so viewport width is strictly finite (eliminating
 * `Cannot round NaN value.` crashes) and applies scroll containers ONLY on inner content when overflowing.
 */
@Composable
fun TextGridPreviewCard(
    doc: TextGridDocument,
    visualConfig: TextGridVisualConfig,
    title: String? = null,
    subtitleHint: String? = null,
    timeDecimals: Int? = null,
    formatVariant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT,
    rawSourceOverride: String? = null,
    hasPairedAudio: Boolean = false,
    isPlaying: Boolean = false,
    playheadTimeSec: Double? = null,
    activePlayRange: TimeRange? = null,
    enableDemoPlaySweep: Boolean = false,
    maxScoreHeight: Dp = Dp.Unspecified,
    sourceHeight: Dp = 260.dp,
    onPlayRange: ((Double, Double) -> Unit)? = null,
    onStopPlayback: (() -> Unit)? = null,
    onOpenFullViewer: (() -> Unit)? = null,
    onCopiedMessage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val str = TextGridUiStrings.current
    val palette = rememberTextGridScorePalette(visualConfig.scoreTheme)
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val phoneticFont = LocalPhoneticFontFamily.current
    val textMeasurer = rememberTextMeasurer()
    val widthMeasurer = remember(textMeasurer, density, phoneticFont) {
        ComposeScoreTextWidthMeasurer(textMeasurer, density.density, phoneticFont)
    }

    var showSource by remember(visualConfig.defaultOpenMode) {
        mutableStateOf(visualConfig.defaultOpenMode == TextGridOpenMode.SOURCE)
    }
    var selection by remember(doc) { mutableStateOf<TextGridSelection?>(null) }

    // Optional lightweight 60fps demo sweep when previewing without real audio (e.g., Settings card)
    var demoSweepRange by remember(doc) { mutableStateOf<TimeRange?>(null) }
    var demoPlayhead by remember(doc) { mutableDoubleStateOf(Double.NaN) }

    LaunchedEffect(demoSweepRange) {
        val range = demoSweepRange ?: run {
            demoPlayhead = Double.NaN
            return@LaunchedEffect
        }
        if (!range.isValid) {
            demoSweepRange = null
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            val elapsedSec = (withFrameNanos { it } - startNanos) / 1_000_000_000.0
            val t = range.start + elapsedSec
            if (t >= range.end) {
                demoPlayhead = Double.NaN
                demoSweepRange = null
                break
            }
            demoPlayhead = t
        }
    }

    val effectivePlayhead = when {
        playheadTimeSec != null && playheadTimeSec.isFinite() -> playheadTimeSec
        demoPlayhead.isFinite() -> demoPlayhead
        else -> null
    }
    val effectivePlayRange = activePlayRange ?: demoSweepRange
    val effectiveIsPlaying = isPlaying || demoSweepRange != null

    val serialized = remember(doc, formatVariant, timeDecimals, rawSourceOverride) {
        if (rawSourceOverride != null) {
            val parsed = TextGridCodec.parseText(rawSourceOverride)
            rawSourceOverride to parsed.spanMap
        } else {
            val (text, spans) = TextGridCodec.serializeWithSpanMap(
                doc = doc,
                variant = formatVariant,
                timeDecimals = timeDecimals
            )
            text to spans
        }
    }
    val sourceText = serialized.first
    val spanMap = serialized.second

    val highlightedLines = remember(effectivePlayhead, selection, doc, spanMap) {
        val set = LinkedHashSet<Int>()
        if (effectivePlayhead != null) {
            set.addAll(spanMap.linesForTime(effectivePlayhead, doc, selection?.tierIndexOrNull))
        }
        val selSpan = spanMap.spanForSelection(selection, doc)
        if (selSpan != null) {
            for (ln in selSpan.startLine..selSpan.endLine) set.add(ln)
        }
        set
    }
    val scrollToLine = remember(effectivePlayhead, selection, doc, spanMap) {
        if (effectivePlayhead != null) {
            spanMap.primaryLineForTime(effectivePlayhead, doc, selection?.tierIndexOrNull)
        } else {
            spanMap.spanForSelection(selection, doc)?.startLine
        }
    }

    val safeDurationText = String.format(Locale.US, "%.2f", TextGridSafeMath.safeAtLeast(doc.duration, 0.0))
    val metaText = str.metaSummary(doc.tierCount, safeDurationText)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = palette.surfaceBg,
        border = BorderStroke(0.75.dp, palette.cardBorder),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Single-row clean aligned header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left: title + compact metadata
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: sleek pill segmented toggle [Score | Source]
                TextGridModePillToggle(
                    showSource = showSource,
                    onSelectScore = { showSource = false },
                    onSelectSource = { showSource = true },
                    scoreLabel = str.tabScore,
                    sourceLabel = str.tabSource,
                    palette = palette
                )

                // Minimal icon actions (Play/Stop, Copy, Full Screen)
                if (hasPairedAudio && onPlayRange != null) {
                    IconButton(
                        onClick = {
                            if (effectiveIsPlaying) {
                                demoSweepRange = null
                                onStopPlayback?.invoke()
                            } else {
                                val (s, e) = selectedRangeOrDoc(selection, doc)
                                onPlayRange(s, e)
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (effectiveIsPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (effectiveIsPlaying) str.stopPlayback else str.playAll,
                            tint = if (effectiveIsPlaying) palette.playheadColor else palette.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (enableDemoPlaySweep) {
                    IconButton(
                        onClick = {
                            if (demoSweepRange != null) {
                                demoSweepRange = null
                            } else {
                                val (s, e) = selectedRangeOrDoc(selection, doc)
                                if (e > s) demoSweepRange = TimeRange(s, e)
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (demoSweepRange != null) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = str.demoPlayHint,
                            tint = if (demoSweepRange != null) palette.playheadColor else palette.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(sourceText))
                        onCopiedMessage?.invoke(str.copiedSource)
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = str.copySource,
                        tint = palette.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (onOpenFullViewer != null) {
                    IconButton(
                        onClick = onOpenFullViewer,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = str.openFullViewer,
                            tint = palette.muted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = palette.cardBorder.copy(alpha = 0.7f))

            // Main content area: Score Canvas OR Read-Only Source Viewer
            if (showSource) {
                TextGridSourceViewer(
                    sourceText = sourceText,
                    palette = palette,
                    highlightedLines = highlightedLines,
                    scrollToLine = scrollToLine,
                    maxHeight = sourceHeight
                )
            } else {
                // OUTER BoxWithConstraints has NO scroll modifiers -> maxWidth is strictly finite!
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val containerW = if (maxWidth.isSpecified && maxWidth.value.isFinite()) {
                        maxWidth.value.coerceIn(220f, 4096f)
                    } else {
                        360f
                    }
                    val layoutOptions = remember(visualConfig, containerW) {
                        DualScaleLayoutOptions.fromVisualConfig(
                            config = visualConfig,
                            targetWidthDp = containerW
                        )
                    }
                    val layout = remember(doc, layoutOptions, widthMeasurer) {
                        DualScaleLayoutEngine.computeLayout(doc, layoutOptions, widthMeasurer)
                    }

                    val needsHScroll = layout.widthDp > containerW + 2f
                    val needsVScroll = maxScoreHeight != Dp.Unspecified && layout.heightDp > maxScoreHeight.value + 2f
                    val hScroll = rememberScrollState()
                    val vScroll = rememberScrollState()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { m ->
                                if (maxScoreHeight != Dp.Unspecified) m.heightIn(max = maxScoreHeight) else m
                            }
                            .let { m -> if (needsVScroll) m.verticalScroll(vScroll) else m }
                            .let { m -> if (needsHScroll) m.horizontalScroll(hScroll) else m }
                    ) {
                        DualScaleScoreCanvas(
                            doc = doc,
                            layout = layout,
                            palette = palette,
                            selection = selection,
                            playheadTimeSec = effectivePlayhead,
                            activePlayRange = effectivePlayRange,
                            onSelect = { nextSel ->
                                selection = nextSel
                                when (nextSel) {
                                    is TextGridSelection.Interval -> {
                                        val iv = (doc.tiers.getOrNull(nextSel.tierIndex) as? TextGridTier.IntervalTier)
                                            ?.intervals?.getOrNull(nextSel.itemIndex)
                                        if (iv != null && iv.xmax > iv.xmin) {
                                            if (hasPairedAudio && onPlayRange != null) {
                                                onPlayRange(iv.xmin, iv.xmax)
                                            } else if (enableDemoPlaySweep) {
                                                demoSweepRange = TimeRange(iv.xmin, iv.xmax)
                                            }
                                        }
                                    }
                                    is TextGridSelection.Point -> {
                                        val pt = (doc.tiers.getOrNull(nextSel.tierIndex) as? TextGridTier.TextTier)
                                            ?.points?.getOrNull(nextSel.itemIndex)
                                        if (pt != null && pt.number.isFinite()) {
                                            val start = (pt.number - 0.12).coerceAtLeast(doc.xmin)
                                            val end = (pt.number + 0.12).coerceAtMost(doc.xmax)
                                            if (end > start) {
                                                if (hasPairedAudio && onPlayRange != null) {
                                                    onPlayRange(start, end)
                                                } else if (enableDemoPlaySweep) {
                                                    demoSweepRange = TimeRange(start, end)
                                                }
                                            }
                                        }
                                    }
                                    else -> Unit
                                }
                            },
                            onSeekTime = { t ->
                                if (t.isFinite() && doc.xmax > t) {
                                    if (hasPairedAudio && onPlayRange != null) {
                                        onPlayRange(t, doc.xmax)
                                    } else if (enableDemoPlaySweep) {
                                        demoSweepRange = TimeRange(t, doc.xmax)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Understated single-line selection readout or optional hint footer
            val footerText = buildSelectionSummaryText(selection, doc, str) ?: subtitleHint
            if (!footerText.isNullOrBlank()) {
                HorizontalDivider(thickness = 0.5.dp, color = palette.cardBorder.copy(alpha = 0.55f))
                Text(
                    text = footerText,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.gutterBg.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Sleek pill segmented toggle `[Score | Source]` shared across preview cards, mini strips, and full viewer.
 */
@Composable
fun TextGridModePillToggle(
    showSource: Boolean,
    onSelectScore: () -> Unit,
    onSelectSource: () -> Unit,
    scoreLabel: String,
    sourceLabel: String,
    palette: TextGridScorePalette,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val pillShape = RoundedCornerShape(999.dp)
    val hPad = if (compact) 8.dp else 10.dp
    val vPad = if (compact) 3.dp else 4.dp
    val fontSize = if (compact) 11.sp else 11.5.sp
    val colorScheme = MaterialTheme.colorScheme

    val (scoreSrc, scoreHoverState) = rememberStudioInteraction()
    val scoreHovered by scoreHoverState
    val (sourceSrc, sourceHoverState) = rememberStudioInteraction()
    val sourceHovered by sourceHoverState

    val scoreBg by animateColorAsState(
        targetValue = when {
            !showSource -> palette.cellFill
            scoreHovered -> lerp(palette.gutterBg, colorScheme.primaryContainer, 0.32f)
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = StudioMotion.microTween(),
        label = "TgPillScoreBg"
    )
    val scoreFg by animateColorAsState(
        targetValue = when {
            !showSource -> palette.ink
            scoreHovered -> colorScheme.primary
            else -> palette.muted
        },
        animationSpec = StudioMotion.microTween(),
        label = "TgPillScoreFg"
    )
    val sourceBg by animateColorAsState(
        targetValue = when {
            showSource -> palette.cellFill
            sourceHovered -> lerp(palette.gutterBg, colorScheme.primaryContainer, 0.32f)
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = StudioMotion.microTween(),
        label = "TgPillSourceBg"
    )
    val sourceFg by animateColorAsState(
        targetValue = when {
            showSource -> palette.ink
            sourceHovered -> colorScheme.primary
            else -> palette.muted
        },
        animationSpec = StudioMotion.microTween(),
        label = "TgPillSourceFg"
    )

    Surface(
        modifier = modifier,
        shape = pillShape,
        color = palette.gutterBg,
        border = BorderStroke(0.75.dp, palette.cardBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(scoreBg)
                    .hoverable(scoreSrc)
                    .then(if (isDesktop) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    .clickable(
                        interactionSource = scoreSrc,
                        indication = null,
                        onClick = onSelectScore
                    )
                    .padding(horizontal = hPad, vertical = vPad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = scoreLabel,
                    fontSize = fontSize,
                    fontWeight = if (!showSource) FontWeight.SemiBold else FontWeight.Medium,
                    color = scoreFg,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .background(sourceBg)
                    .hoverable(sourceSrc)
                    .then(if (isDesktop) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    .clickable(
                        interactionSource = sourceSrc,
                        indication = null,
                        onClick = onSelectSource
                    )
                    .padding(horizontal = hPad, vertical = vPad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sourceLabel,
                    fontSize = fontSize,
                    fontWeight = if (showSource) FontWeight.SemiBold else FontWeight.Medium,
                    color = sourceFg,
                    maxLines = 1
                )
            }
        }
    }
}

internal fun selectedRangeOrDoc(selection: TextGridSelection?, doc: TextGridDocument): Pair<Double, Double> {
    return when (selection) {
        is TextGridSelection.Interval -> {
            val iv = (doc.tiers.getOrNull(selection.tierIndex) as? TextGridTier.IntervalTier)
                ?.intervals?.getOrNull(selection.itemIndex)
            if (iv != null && iv.xmax > iv.xmin) iv.xmin to iv.xmax else doc.xmin to doc.xmax
        }
        is TextGridSelection.Point -> {
            val pt = (doc.tiers.getOrNull(selection.tierIndex) as? TextGridTier.TextTier)
                ?.points?.getOrNull(selection.itemIndex)
            if (pt != null && pt.number.isFinite()) {
                val s = (pt.number - 0.15).coerceAtLeast(doc.xmin)
                val e = (pt.number + 0.15).coerceAtMost(doc.xmax)
                if (e > s) s to e else doc.xmin to doc.xmax
            } else {
                doc.xmin to doc.xmax
            }
        }
        else -> doc.xmin to doc.xmax
    }
}

internal fun buildSelectionSummaryText(
    selection: TextGridSelection?,
    doc: TextGridDocument,
    str: com.example.i18n.TextGridVisualStrings
): String? {
    return when (selection) {
        is TextGridSelection.Interval -> {
            val tier = doc.tiers.getOrNull(selection.tierIndex) as? TextGridTier.IntervalTier ?: return null
            val iv = tier.intervals.getOrNull(selection.itemIndex) ?: return null
            val dur = TextGridSafeMath.safeAtLeast(iv.xmax - iv.xmin, 0.0)
            val label = iv.text.ifEmpty { str.emptyIntervalLabel }
            String.format(Locale.US, "%s · %.3f–%.3fs (%.3fs) · %s", tier.name.ifEmpty { "∅" }, iv.xmin, iv.xmax, dur, label)
        }
        is TextGridSelection.Point -> {
            val tier = doc.tiers.getOrNull(selection.tierIndex) as? TextGridTier.TextTier ?: return null
            val pt = tier.points.getOrNull(selection.itemIndex) ?: return null
            val tStr = String.format(Locale.US, "%.3f", TextGridSafeMath.safeFinite(pt.number, 0.0))
            val mark = pt.mark.ifEmpty { str.pointAtLabel(tStr) }
            "${tier.name.ifEmpty { "∅" }} · ${tStr}s · $mark"
        }
        is TextGridSelection.Tier -> {
            val tier = doc.tiers.getOrNull(selection.tierIndex) ?: return null
            when (tier) {
                is TextGridTier.IntervalTier -> "${tier.name.ifEmpty { "∅" }} · ${tier.intervals.size}"
                is TextGridTier.TextTier -> "${tier.name.ifEmpty { "∅" }} · ${tier.points.size}"
            }
        }
        else -> null
    }
}
