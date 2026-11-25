package com.example.textgrid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.StudioFilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.layout.DualScaleLayoutEngine
import com.example.textgrid.layout.DualScaleLayoutOptions
import com.example.textgrid.layout.TextGridSafeMath
import com.example.textgrid.model.TextGridAnomaly
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.model.TextGridOpenMode
import com.example.textgrid.model.TextGridSelection
import com.example.textgrid.model.TextGridTier
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.model.TimeRange
import com.example.ui.theme.LocalPhoneticFontFamily
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Pure read-only full-screen Dual-Scale TextGrid viewer (mobile portrait/landscape + desktop).
 *
 * Features:
 * - Seamless `[Score | Source]` mode switching
 * - 2-finger pinch zoom & zoom-to-selection / fit-all
 * - Optional tier solo filtering & Praat `.Collection` multi-object switching
 * - Synchronized interval playback, playback speed (`0.5×–1.5×`), and loop toggle
 * - Copy source, export `.TextGrid`, and export publication-grade `.svg`
 * - Zero editing controls, zero undo/redo, zero unsaved-changes dialogs
 */
@Composable
fun TextGridFullViewerScreen(
    title: String,
    document: TextGridDocument,
    collectionDocuments: List<TextGridDocument> = emptyList(),
    anomalies: List<TextGridAnomaly> = emptyList(),
    pairedAudioFile: File? = null,
    visualConfig: TextGridVisualConfig,
    timeDecimals: Int? = null,
    formatVariant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT,
    rawSourceOverride: String? = null,
    isPlaying: Boolean = false,
    playheadTimeSec: Double? = null,
    activePlayRange: TimeRange? = null,
    onVisualConfigChange: (TextGridVisualConfig) -> Unit,
    onPlayRange: (startSec: Double, endSec: Double, speed: Float, loop: Boolean) -> Unit,
    onStopPlayback: () -> Unit,
    onExportTextGrid: ((TextGridDocument, String) -> Unit)? = null,
    onExportSvg: ((TextGridDocument, String) -> Unit)? = null,
    onNotify: (String) -> Unit = {},
    onClose: () -> Unit,
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

    val docs = remember(document, collectionDocuments) {
        if (collectionDocuments.size > 1) collectionDocuments else listOf(document)
    }
    var activeCollectionIdx by remember(docs) { mutableIntStateOf(0) }
    val activeDoc = docs.getOrElse(activeCollectionIdx) { document }

    var showSource by remember(visualConfig.defaultOpenMode) {
        mutableStateOf(visualConfig.defaultOpenMode == TextGridOpenMode.SOURCE)
    }
    var selection by remember(activeDoc) { mutableStateOf<TextGridSelection?>(null) }
    var viewDomain by remember(activeDoc) { mutableStateOf<TimeRange?>(null) }
    var soloTierIndex by remember(activeDoc) { mutableStateOf<Int?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var loopEnabled by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val hiddenTierIndices = remember(soloTierIndex, activeDoc.tiers.size) {
        val solo = soloTierIndex
        if (solo != null && solo in activeDoc.tiers.indices) {
            activeDoc.tiers.indices.filter { it != solo }.toSet()
        } else {
            emptySet()
        }
    }

    val serialized = remember(activeDoc, formatVariant, timeDecimals, rawSourceOverride, activeCollectionIdx) {
        if (rawSourceOverride != null && activeCollectionIdx == 0 && docs.size == 1) {
            val parsed = TextGridCodec.parseText(rawSourceOverride)
            rawSourceOverride to parsed.spanMap
        } else {
            TextGridCodec.serializeWithSpanMap(
                doc = activeDoc,
                variant = formatVariant,
                timeDecimals = timeDecimals
            )
        }
    }
    val sourceText = serialized.first
    val spanMap = serialized.second

    val highlightedLines = remember(playheadTimeSec, selection, activeDoc, spanMap) {
        val set = LinkedHashSet<Int>()
        if (playheadTimeSec != null && playheadTimeSec.isFinite()) {
            set.addAll(spanMap.linesForTime(playheadTimeSec, activeDoc, selection?.tierIndexOrNull))
        }
        val selSpan = spanMap.spanForSelection(selection, activeDoc)
        if (selSpan != null) {
            for (ln in selSpan.startLine..selSpan.endLine) set.add(ln)
        }
        set
    }
    val scrollToLine = remember(playheadTimeSec, selection, activeDoc, spanMap) {
        if (playheadTimeSec != null && playheadTimeSec.isFinite()) {
            spanMap.primaryLineForTime(playheadTimeSec, activeDoc, selection?.tierIndexOrNull)
        } else {
            spanMap.spanForSelection(selection, activeDoc)?.startLine
        }
    }

    val hasAudio = pairedAudioFile != null && pairedAudioFile.isFile
    val durText = String.format(Locale.US, "%.2f", TextGridSafeMath.safeAtLeast(activeDoc.duration, 0.0))
    val detailMeta = str.metaDetailSummary(
        activeDoc.tierCount,
        activeDoc.totalIntervalCount,
        activeDoc.totalPointCount,
        durText
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = palette.surfaceBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Clean 52dp Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = {
                    onStopPlayback()
                    onClose()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = palette.ink
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { activeDoc.name.ifBlank { "TextGrid" } },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = detailMeta,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextGridModePillToggle(
                    showSource = showSource,
                    onSelectScore = { showSource = false },
                    onSelectSource = { showSource = true },
                    scoreLabel = str.tabScore,
                    sourceLabel = str.tabSource,
                    palette = palette
                )

                IconButton(
                    onClick = { showQuickSettings = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = str.quickSettings,
                        tint = palette.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = str.moreActions,
                            tint = palette.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(str.copySource) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMoreMenu = false
                                clipboard.setText(AnnotatedString(sourceText))
                                onNotify(str.copiedSource)
                            }
                        )
                        if (onExportTextGrid != null) {
                            DropdownMenuItem(
                                text = { Text(str.exportTextGrid) },
                                leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMoreMenu = false
                                    onExportTextGrid(activeDoc, sourceText)
                                }
                            )
                        }
                        if (onExportSvg != null) {
                            DropdownMenuItem(
                                text = { Text(str.exportSvg) },
                                leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMoreMenu = false
                                    val svgLayout = DualScaleLayoutEngine.computeLayout(
                                        doc = activeDoc,
                                        options = DualScaleLayoutOptions.fromVisualConfig(
                                            config = visualConfig,
                                            targetWidthDp = 960f,
                                            viewDomain = viewDomain,
                                            hiddenTierIndices = hiddenTierIndices
                                        )
                                    )
                                    val svg = DualScaleLayoutEngine.exportStaticSvg(
                                        doc = activeDoc,
                                        layout = svgLayout,
                                        isBw = palette.isBw
                                    )
                                    onExportSvg(activeDoc, svg)
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = palette.cardBorder)

            // 2. Optional Tier Filter / Collection Object Strip (only shown when useful)
            if (docs.size > 1 || activeDoc.tiers.size > 1 || anomalies.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.gutterBg.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (docs.size > 1) {
                        itemsIndexed(docs) { idx, item ->
                            StudioFilterChip(
                                selected = activeCollectionIdx == idx,
                                onClick = { activeCollectionIdx = idx },
                                labelText = item.name.ifBlank { str.collectionItemBadge(idx + 1, docs.size) }
                            )
                        }
                    }

                    if (activeDoc.tiers.size > 1) {
                        item {
                            StudioFilterChip(
                                selected = soloTierIndex == null,
                                onClick = { soloTierIndex = null },
                                labelText = str.allTiersFilter
                            )
                        }
                        itemsIndexed(activeDoc.tiers) { idx, tier ->
                            StudioFilterChip(
                                selected = soloTierIndex == idx,
                                onClick = { soloTierIndex = if (soloTierIndex == idx) null else idx },
                                labelText = "${idx + 1} · ${tier.name.ifEmpty { "∅" }}"
                            )
                        }
                    }

                    if (anomalies.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = palette.gapBg,
                                border = BorderStroke(0.75.dp, palette.gapStroke.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = str.anomaliesCountBadge(anomalies.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.gapStroke,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = palette.cardBorder.copy(alpha = 0.6f))
            }

            // 3. Main Canvas / Source Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showSource) {
                    TextGridSourceViewer(
                        sourceText = sourceText,
                        palette = palette,
                        highlightedLines = highlightedLines,
                        scrollToLine = scrollToLine,
                        maxHeight = Dp.Unspecified,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // OUTER BoxWithConstraints has NO scroll modifiers -> strictly finite width!
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val containerW = if (maxWidth.isSpecified && maxWidth.value.isFinite()) {
                            maxWidth.value.coerceIn(220f, 4096f)
                        } else {
                            360f
                        }
                        val layoutOptions = remember(visualConfig, containerW, viewDomain, hiddenTierIndices) {
                            DualScaleLayoutOptions.fromVisualConfig(
                                config = visualConfig,
                                targetWidthDp = containerW,
                                viewDomain = viewDomain,
                                hiddenTierIndices = hiddenTierIndices
                            )
                        }
                        val layout = remember(activeDoc, layoutOptions, widthMeasurer) {
                            DualScaleLayoutEngine.computeLayout(activeDoc, layoutOptions, widthMeasurer)
                        }

                        val vScroll = rememberScrollState()
                        val hScroll = rememberScrollState()
                        val needsHScroll = layout.widthDp > containerW + 2f

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(vScroll)
                        ) {
                            if (layout.autoVirtualizedSingleRow) {
                                Text(
                                    text = str.largeFileVirtualizedHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { m -> if (needsHScroll) m.horizontalScroll(hScroll) else m }
                            ) {
                                DualScaleScoreCanvas(
                                    doc = activeDoc,
                                    layout = layout,
                                    palette = palette,
                                    selection = selection,
                                    playheadTimeSec = playheadTimeSec,
                                    activePlayRange = activePlayRange,
                                    onSelect = { nextSel ->
                                        selection = nextSel
                                        if (hasAudio && nextSel is TextGridSelection.Interval) {
                                            val iv = (activeDoc.tiers.getOrNull(nextSel.tierIndex) as? TextGridTier.IntervalTier)
                                                ?.intervals?.getOrNull(nextSel.itemIndex)
                                            if (iv != null && iv.xmax > iv.xmin) {
                                                onPlayRange(iv.xmin, iv.xmax, playbackSpeed, loopEnabled)
                                            }
                                        } else if (hasAudio && nextSel is TextGridSelection.Point) {
                                            val pt = (activeDoc.tiers.getOrNull(nextSel.tierIndex) as? TextGridTier.TextTier)
                                                ?.points?.getOrNull(nextSel.itemIndex)
                                            if (pt != null && pt.number.isFinite()) {
                                                val s = (pt.number - 0.15).coerceAtLeast(activeDoc.xmin)
                                                val e = (pt.number + 0.15).coerceAtMost(activeDoc.xmax)
                                                if (e > s) onPlayRange(s, e, playbackSpeed, loopEnabled)
                                            }
                                        }
                                    },
                                    onSeekTime = { t ->
                                        if (hasAudio && t.isFinite() && activeDoc.xmax > t) {
                                            onPlayRange(t, activeDoc.xmax, playbackSpeed, loopEnabled)
                                        }
                                    },
                                    onPinchZoom = { zoomDelta, focusT ->
                                        val totalSpan = activeDoc.duration
                                        if (totalSpan > 0.05 && zoomDelta.isFinite() && zoomDelta > 0.1f) {
                                            val curStart = viewDomain?.start ?: activeDoc.xmin
                                            val curEnd = viewDomain?.end ?: activeDoc.xmax
                                            val curSpan = max(0.04, curEnd - curStart)
                                            val nextSpan = (curSpan / zoomDelta.toDouble()).coerceIn(0.04, totalSpan)
                                            if (nextSpan >= totalSpan * 0.985) {
                                                viewDomain = null
                                            } else {
                                                val anchor = TextGridSafeMath.safeCoerceIn(focusT, curStart, curEnd, (curStart + curEnd) * 0.5)
                                                val ratio = TextGridSafeMath.safeCoerceIn((anchor - curStart) / curSpan, 0.0, 1.0, 0.5)
                                                var ns = anchor - nextSpan * ratio
                                                var ne = ns + nextSpan
                                                if (ns < activeDoc.xmin) {
                                                    ns = activeDoc.xmin
                                                    ne = min(activeDoc.xmax, ns + nextSpan)
                                                }
                                                if (ne > activeDoc.xmax) {
                                                    ne = activeDoc.xmax
                                                    ns = max(activeDoc.xmin, ne - nextSpan)
                                                }
                                                if (ne > ns) viewDomain = TimeRange(ns, ne)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Clean Selection Readout Strip (only when an interval or point is selected)
            val selSummary = buildSelectionSummaryText(selection, activeDoc, str)
            if (selSummary != null) {
                HorizontalDivider(thickness = 0.5.dp, color = palette.cardBorder.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.gutterBg.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = selSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (selection is TextGridSelection.Interval && !showSource) {
                        val iv = (activeDoc.tiers.getOrNull(selection!!.tierIndexOrNull ?: -1) as? TextGridTier.IntervalTier)
                            ?.intervals?.getOrNull((selection as TextGridSelection.Interval).itemIndex)
                        if (iv != null && iv.xmax > iv.xmin) {
                            TextButton(
                                onClick = {
                                    val pad = max(0.05, (iv.xmax - iv.xmin) * 0.35)
                                    val s = max(activeDoc.xmin, iv.xmin - pad)
                                    val e = min(activeDoc.xmax, iv.xmax + pad)
                                    if (e > s) viewDomain = TimeRange(s, e)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(str.zoomToSelection, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = palette.cardBorder)

            // 5. Clean 52dp Bottom Playback & View Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(palette.surfaceBg)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Play / Stop button
                Surface(
                    shape = CircleShape,
                    color = if (hasAudio) {
                        if (isPlaying) palette.playheadColor else palette.selectionAccent
                    } else {
                        palette.gutterBg
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        enabled = hasAudio,
                        onClick = {
                            if (isPlaying) {
                                onStopPlayback()
                            } else {
                                val (s, e) = selectedRangeOrDoc(selection, activeDoc)
                                onPlayRange(s, e, playbackSpeed, loopEnabled)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) str.stopPlayback else str.playAll,
                            tint = if (hasAudio) androidx.compose.ui.graphics.Color.White else palette.faint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Audio status / live time readout
                Column(modifier = Modifier.weight(1f)) {
                    val curSec = playheadTimeSec?.takeIf { it.isFinite() } ?: activeDoc.xmin
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.2fs / %.2fs",
                            TextGridSafeMath.safeAtLeast(curSec, 0.0),
                            TextGridSafeMath.safeAtLeast(activeDoc.xmax, 0.0)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = palette.ink,
                        maxLines = 1
                    )
                    Text(
                        text = if (hasAudio) {
                            str.pairedAudioLabel(pairedAudioFile!!.name)
                        } else {
                            str.noPairedAudioLabel
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (viewDomain != null) {
                    TextButton(
                        onClick = { viewDomain = null },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(str.fitFullView, style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (hasAudio) {
                    // Speed toggle chip (0.5x -> 0.75x -> 1.0x -> 1.25x -> 1.5x)
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
                    StudioFilterChip(
                        selected = kotlin.math.abs(playbackSpeed - 1.0f) > 0.05f,
                        onClick = {
                            val nextIdx = (speeds.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.05f } + 1) % speeds.size
                            playbackSpeed = speeds[nextIdx]
                        },
                        labelText = String.format(Locale.US, "%.2g×", playbackSpeed),
                        showCheckmark = false,
                        horizontalPadding = 10.dp
                    )

                    // Loop toggle button
                    IconButton(
                        onClick = { loopEnabled = !loopEnabled },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = str.loopToggle,
                            tint = if (loopEnabled) palette.selectionAccent else palette.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    TextGridQuickSettingsSheet(
        visible = showQuickSettings,
        config = visualConfig,
        onConfigChange = onVisualConfigChange,
        onDismiss = { showQuickSettings = false }
    )
}
