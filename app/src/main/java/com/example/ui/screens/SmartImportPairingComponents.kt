package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.S
import com.example.i18n.SSmartImport
import com.example.i18n.withArgs
import com.example.ui.components.AdaptiveModalSurface
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioHorizontalScrollStrip
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.ui.TextGridMiniStrip
import com.example.utils.AudioWordPairingRow
import com.example.utils.CandidateTargetWord
import com.example.utils.ImportItemKind
import com.example.utils.PairingMatchKind
import com.example.utils.SmartImportEngine
import com.example.utils.StagedImportItem
import com.example.utils.TextGridPairingRow
import java.io.File
import java.util.Locale

internal data class KindVisualStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val badgeLabel: String
)

internal fun resolveKindVisualStyle(kind: ImportItemKind, cs: ColorScheme): KindVisualStyle {
    return when (kind) {
        ImportItemKind.PROJECT_ARCHIVE -> KindVisualStyle(
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer,
            icon = Icons.Default.Archive,
            badgeLabel = SSmartImport.kindArchiveLabel
        )
        ImportItemKind.WORDLIST_DOC -> KindVisualStyle(
            containerColor = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            icon = Icons.Default.TableChart,
            badgeLabel = SSmartImport.kindWordlistLabel
        )
        ImportItemKind.AUDIO_CLIP -> KindVisualStyle(
            containerColor = cs.secondaryContainer,
            contentColor = cs.onSecondaryContainer,
            icon = Icons.Default.AudioFile,
            badgeLabel = SSmartImport.kindAudioLabel
        )
        ImportItemKind.PROMPT_IMAGE -> KindVisualStyle(
            containerColor = cs.surfaceContainerHighest,
            contentColor = cs.onSurfaceVariant,
            icon = Icons.Default.Image,
            badgeLabel = SSmartImport.kindImageLabel
        )
        ImportItemKind.TEXTGRID -> KindVisualStyle(
            containerColor = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            icon = Icons.Default.GraphicEq,
            badgeLabel = SSmartImport.kindTextGridLabel
        )
        ImportItemKind.PRAAT_COLLECTION -> KindVisualStyle(
            containerColor = cs.tertiaryContainer,
            contentColor = cs.onTertiaryContainer,
            icon = Icons.Default.GraphicEq,
            badgeLabel = SSmartImport.kindPraatCollectionLabel
        )
        ImportItemKind.UNSUPPORTED -> KindVisualStyle(
            containerColor = cs.errorContainer,
            contentColor = cs.onErrorContainer,
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            badgeLabel = SSmartImport.kindUnsupportedLabel
        )
    }
}

internal fun formatCompactSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

@Composable
internal fun StagedImportFileRow(
    item: StagedImportItem,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val style = resolveKindVisualStyle(item.kind, cs)
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    val rowBg by animateColorAsState(
        targetValue = if (isHovered && enabled) {
            lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.24f)
        } else {
            cs.surfaceContainerLow
        },
        animationSpec = tween(120),
        label = "StagedFileRowBg"
    )

    val subtitleText = remember(item) {
        buildList {
            if (item.relativeFolder.isNotBlank()) add(item.relativeFolder)
            when (item.kind) {
                ImportItemKind.WORDLIST_DOC -> add("${item.parsedWords.size} 词")
                ImportItemKind.PROJECT_ARCHIVE -> {
                    val ins = item.archiveInspect
                    if (ins != null) {
                        if (ins.estimatedSpeakerCount > 1) {
                            add("${ins.estimatedSpeakerCount} 位发音人")
                        }
                        if (ins.estimatedWordCount > 0 || ins.estimatedAudioCount > 0) {
                            add(
                                SSmartImport.archiveSummaryMeta.withArgs(
                                    "words" to ins.estimatedWordCount,
                                    "audio" to ins.estimatedAudioCount
                                )
                            )
                        } else if (!ins.parseErrorHint.isNullOrBlank()) {
                            add(ins.parseErrorHint)
                        }
                    }
                }
                ImportItemKind.AUDIO_CLIP -> {
                    if (item.estimatedDurationMs > 0L) {
                        add(String.format(Locale.US, "%.1fs", item.estimatedDurationMs / 1000.0))
                    }
                    if (item.needsAudioTranscode) {
                        add(item.extensionLower.uppercase(Locale.ROOT) + " → WAV")
                    }
                }
                ImportItemKind.PROMPT_IMAGE -> {
                    val sz = formatCompactSize(item.sizeBytes)
                    if (sz.isNotEmpty()) add(sz)
                }
                ImportItemKind.TEXTGRID, ImportItemKind.PRAAT_COLLECTION -> {
                    val tg = item.textGridInspect
                    if (tg != null) {
                        add("${tg.tierCount} 层")
                        if (tg.totalIntervals > 0) add("${tg.totalIntervals} 区间")
                        if (tg.totalPoints > 0) add("${tg.totalPoints} 点")
                        if (tg.durationSec > 0.0) add(String.format(Locale.US, "%.2fs", tg.durationSec))
                    }
                    if (item.pairedAudioFile != null) {
                        add("已配对音频")
                    }
                }
                ImportItemKind.UNSUPPORTED -> {
                    if (!item.errorHint.isNullOrBlank()) add(item.errorHint)
                }
            }
        }.joinToString(" · ")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = rowBg,
        border = BorderStroke(
            1.dp,
            if (item.kind == ImportItemKind.UNSUPPORTED) {
                cs.error.copy(alpha = 0.4f)
            } else if (isHovered && enabled) {
                cs.primary.copy(alpha = 0.35f)
            } else {
                cs.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .hoverable(hoverSrc)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = style.containerColor,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = style.badgeLabel,
                        tint = style.contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.displayName,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = style.containerColor.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = style.badgeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = style.contentColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (subtitleText.isNotEmpty()) {
                    Text(
                        text = subtitleText,
                        fontSize = 11.sp,
                        color = if (item.kind == ImportItemKind.UNSUPPORTED) cs.error else cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier
                    .size(40.dp)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("remove_staged_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = S.common.remove,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun TextGridQueuePreviewCard(
    textGridPairings: List<TextGridPairingRow>,
    visualConfig: TextGridVisualConfig,
    enabled: Boolean,
    onOpenVisualViewer: (File, File?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (textGridPairings.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        textGridPairings.forEach { pair ->
            val item = pair.textGridItem
            val inspect = item.textGridInspect ?: return@forEach
            val openTargetFile = item.sourceLocalFile ?: item.localTempFile
            val pairedAudio = pair.pairedAudioFile

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = cs.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = cs.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = inspect.formatLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onPrimaryContainer
                                )
                            }
                        }

                        if (pairedAudio != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = cs.secondaryContainer
                            ) {
                                Text(
                                    text = "配对音频：${pairedAudio.name}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 180.dp)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (openTargetFile != null) {
                            FilledTonalButton(
                                onClick = { onOpenVisualViewer(openTargetFile, pairedAudio) },
                                enabled = enabled,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("smart_import_view_tg_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "全屏查看",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = item.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val metaLine = buildString {
                            append("${inspect.tierCount} 层")
                            if (inspect.intervalTierCount > 0) append("（区间层 ${inspect.intervalTierCount}）")
                            if (inspect.textTierCount > 0) append("（点层 ${inspect.textTierCount}）")
                            append(" · ${inspect.totalIntervals} 区间")
                            if (inspect.totalPoints > 0) append(" / ${inspect.totalPoints} 标点")
                            append(String.format(Locale.US, " · %.2fs", inspect.durationSec))
                            if (pair.targetWord != null) {
                                append(" · 已匹配词条「${pair.targetWord.text}」")
                            }
                        }
                        Text(
                            text = metaLine,
                            fontSize = 11.5.sp,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (inspect.anomalyCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = cs.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = cs.error,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "检测到 ${inspect.anomalyCount} 处间隙/重叠提示（谱面已如实标注）。",
                                    fontSize = 11.sp,
                                    color = cs.onErrorContainer
                                )
                            }
                        }
                    }

                    TextGridMiniStrip(
                        doc = inspect.document,
                        visualConfig = visualConfig,
                        rawSourceOverride = inspect.rawSourceText,
                        maxHeight = 164.dp,
                        onOpenFullViewer = if (openTargetFile != null && enabled) {
                            { onOpenVisualViewer(openTargetFile, pairedAudio) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
internal fun ArchiveQueuePreviewCard(
    archiveItems: List<StagedImportItem>,
    modifier: Modifier = Modifier
) {
    if (archiveItems.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        archiveItems.forEach { arch ->
            SingleArchivePreviewCard(
                archiveItem = arch,
                isSingleArchive = archiveItems.size == 1
            )
        }
    }
}

@Composable
private fun SingleArchivePreviewCard(
    archiveItem: StagedImportItem,
    isSingleArchive: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val inspect = archiveItem.archiveInspect
    val allWords = inspect?.previewWords.orEmpty()
    val projectTitle = inspect?.archiveProjectName?.ifBlank { archiveItem.displayName } ?: archiveItem.displayName

    // Distinct groups in original order with item counts
    val groupStats = remember(allWords) {
        val counts = LinkedHashMap<String, Int>()
        for (w in allWords) {
            val g = w.groupName.trim()
            if (g.isNotEmpty()) {
                counts[g] = (counts[g] ?: 0) + 1
            }
        }
        counts.entries.map { it.key to it.value }
    }

    var selectedGroup by remember(archiveItem.id) { mutableStateOf<String?>(null) }
    var searchQuery by remember(archiveItem.id) { mutableStateOf("") }
    var showSearchBar by remember(archiveItem.id) { mutableStateOf(false) }

    val initialLimit = if (isSingleArchive) 8 else 5
    var visibleLimit by remember(archiveItem.id, selectedGroup, searchQuery) {
        mutableIntStateOf(initialLimit)
    }
    val expandedRemarkIndices = remember(archiveItem.id) { mutableStateMapOf<Int, Boolean>() }

    // Keep original 1-based word numbers even when filtering by group or keyword
    val indexedFilteredWords = remember(allWords, selectedGroup, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        allWords.mapIndexedNotNull { idx, pw ->
            val groupMatch = selectedGroup == null || pw.groupName.trim() == selectedGroup
            val queryMatch = q.isEmpty() ||
                pw.text.lowercase(Locale.ROOT).contains(q) ||
                pw.remark.lowercase(Locale.ROOT).contains(q) ||
                pw.groupName.lowercase(Locale.ROOT).contains(q)
            if (groupMatch && queryMatch) (idx + 1) to pw else null
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Clean Header Section (project identity, format badge, metrics, speakers)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.14f))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = cs.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = inspect?.formatLabel?.ifBlank { SSmartImport.kindArchiveLabel }
                                    ?: SSmartImport.kindArchiveLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimaryContainer
                            )
                        }
                    }

                    if (inspect?.usesLongAudioSlicing == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = cs.secondaryContainer
                        ) {
                            Text(
                                text = "长音频自动切片",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "独立还原为新项目",
                        fontSize = 11.5.sp,
                        color = cs.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = projectTitle,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!projectTitle.equals(archiveItem.displayName.substringBeforeLast('.'), ignoreCase = true)) {
                        Text(
                            text = archiveItem.displayName,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Key Metrics Row
                if (inspect != null) {
                    val metricsScroll = rememberScrollState()
                    StudioHorizontalScrollStrip(
                        state = metricsScroll,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.horizontalScroll(metricsScroll),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArchiveMetricPill(
                                label = "词条",
                                value = "${inspect.estimatedWordCount}",
                                accent = true
                            )
                            if (inspect.groupCount > 0) {
                                ArchiveMetricPill(
                                    label = "分组",
                                    value = "${inspect.groupCount}"
                                )
                            }
                            if (inspect.estimatedSpeakerCount > 0) {
                                ArchiveMetricPill(
                                    label = "发音人",
                                    value = "${inspect.estimatedSpeakerCount}"
                                )
                            }
                            ArchiveMetricPill(
                                label = "录音",
                                value = "${inspect.estimatedAudioCount}"
                            )
                        }
                    }

                    // Speaker names strip
                    if (inspect.speakerNames.isNotEmpty()) {
                        val spkScroll = rememberScrollState()
                        StudioHorizontalScrollStrip(
                            state = spkScroll,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(spkScroll),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                inspect.speakerNames.forEach { spkName ->
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = cs.surface.copy(alpha = 0.85f),
                                        border = BorderStroke(0.8.dp, cs.outlineVariant.copy(alpha = 0.55f))
                                    ) {
                                        Text(
                                            text = spkName,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = cs.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

            // 2. Spacious Expandable Wordlist Inspector Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (allWords.isNotEmpty()) {
                    // Section Header + Search toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "已解析字表预览",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            Text(
                                text = "点击词条可展开完整备注与元数据",
                                fontSize = 11.sp,
                                color = cs.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (allWords.size > 12) {
                                IconButton(
                                    onClick = {
                                        showSearchBar = !showSearchBar
                                        if (!showSearchBar) searchQuery = ""
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Icon(
                                        imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = "搜索词条",
                                        tint = if (showSearchBar) cs.primary else cs.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            if (indexedFilteredWords.size > initialLimit) {
                                val isShowingAll = visibleLimit >= indexedFilteredWords.size
                                TextButton(
                                    onClick = {
                                        visibleLimit = if (isShowingAll) {
                                            initialLimit
                                        } else {
                                            minOf(indexedFilteredWords.size, maxOf(visibleLimit + 100, 100))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(
                                        text = if (isShowingAll) "收起" else "展开全部",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = if (isShowingAll) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Optional search box for large wordlists
                    if (showSearchBar) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            placeholder = { Text("搜索词条、分组或备注...", fontSize = 12.5.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = S.common.close,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Group filter chips (when multiple groups exist)
                    if (groupStats.size > 1) {
                        val grpScroll = rememberScrollState()
                        StudioHorizontalScrollStrip(
                            state = grpScroll,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.horizontalScroll(grpScroll),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StudioFilterChip(
                                    selected = selectedGroup == null,
                                    onClick = { selectedGroup = null },
                                    labelText = "全部 (${allWords.size})"
                                )
                                groupStats.forEach { (grpName, count) ->
                                    StudioFilterChip(
                                        selected = selectedGroup == grpName,
                                        onClick = {
                                            selectedGroup = if (selectedGroup == grpName) null else grpName
                                        },
                                        labelText = "$grpName ($count)"
                                    )
                                }
                            }
                        }
                    }

                    // Word items list with comfortable vertical rhythm
                    if (indexedFilteredWords.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cs.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "未找到匹配的词条",
                                fontSize = 12.5.sp,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                            )
                        }
                    } else {
                        val shownRows = indexedFilteredWords.take(visibleLimit)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shownRows.forEach { (origIndex, pw) ->
                                val isRemarkExpanded = expandedRemarkIndices[origIndex] == true
                                ArchiveWordPreviewItemRow(
                                    index1Based = origIndex,
                                    text = pw.text,
                                    groupName = pw.groupName,
                                    remark = pw.remark,
                                    expanded = isRemarkExpanded,
                                    onToggleExpand = {
                                        if (pw.remark.isNotBlank()) {
                                            expandedRemarkIndices[origIndex] = !isRemarkExpanded
                                        }
                                    }
                                )
                            }
                        }

                        // Prominent bottom Expand / Load More / Collapse action bar
                        val remaining = indexedFilteredWords.size - shownRows.size
                        if (remaining > 0 || visibleLimit > initialLimit) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (remaining > 0) {
                                    val nextStep = minOf(remaining, 100)
                                    FilledTonalButton(
                                        onClick = {
                                            visibleLimit = if (remaining <= 100) {
                                                indexedFilteredWords.size
                                            } else {
                                                visibleLimit + nextStep
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 38.dp)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .testTag("archive_expand_words_${archiveItem.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (remaining <= 100) {
                                                "展开查看全部 ${indexedFilteredWords.size} 个词条（剩余 $remaining 条）"
                                            } else {
                                                "继续展开 $nextStep 条（已显示 ${shownRows.size} / ${indexedFilteredWords.size}）"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (visibleLimit > initialLimit) {
                                    TextButton(
                                        onClick = { visibleLimit = initialLimit },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 38.dp)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandLess,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "收起",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!inspect?.parseErrorHint.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cs.errorContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "预检提示：${inspect?.parseErrorHint}",
                            fontSize = 12.sp,
                            color = cs.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "未在压缩包内检测到可预览的字表词条，导入时将尝试从音频或汇总表恢复。",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveMetricPill(
    label: String,
    value: String,
    accent: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (accent) cs.primaryContainer.copy(alpha = 0.85f) else cs.surface.copy(alpha = 0.9f),
        border = BorderStroke(
            0.8.dp,
            if (accent) cs.primary.copy(alpha = 0.35f) else cs.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = if (accent) cs.onPrimaryContainer else cs.onSurface
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (accent) cs.onPrimaryContainer.copy(alpha = 0.85f) else cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArchiveWordPreviewItemRow(
    index1Based: Int,
    text: String,
    groupName: String,
    remark: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    hasImage: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val hasExpandableRemark = remark.isNotBlank()

    val cardBg by animateColorAsState(
        targetValue = if (isHovered && hasExpandableRemark) {
            lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.22f)
        } else {
            cs.surfaceContainerLow.copy(alpha = 0.72f)
        },
        animationSpec = tween(120),
        label = "ArchiveWordRowBg"
    )

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = cardBg,
        border = BorderStroke(
            0.9.dp,
            if (expanded) {
                cs.primary.copy(alpha = 0.45f)
            } else if (isHovered && hasExpandableRemark) {
                cs.primary.copy(alpha = 0.3f)
            } else {
                cs.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .then(
                if (hasExpandableRemark) {
                    Modifier
                        .hoverable(hoverSrc)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = hoverSrc, indication = null, onClick = onToggleExpand)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "#%03d", index1Based),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurfaceVariant
                )
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (groupName.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = cs.secondaryContainer.copy(alpha = 0.78f)
                    ) {
                        Text(
                            text = groupName,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 150.dp)
                                .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        )
                    }
                }
                if (hasImage) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = SSmartImport.kindImageLabel,
                        tint = cs.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (hasExpandableRemark && remark.length > 36) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (remark.isNotBlank()) {
                Text(
                    text = remark,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun WordlistOnlyPreviewCard(
    candidateWords: List<CandidateTargetWord>,
    isMergeMode: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val newWords = remember(candidateWords, isMergeMode) {
        if (isMergeMode) candidateWords.filter { it.existingWordId == null } else candidateWords
    }
    if (newWords.isEmpty()) return

    val initialLimit = 10
    var visibleLimit by remember(newWords.size, isMergeMode) { mutableIntStateOf(initialLimit) }
    val expandedRemarkKeys = remember(newWords.size) { mutableStateMapOf<String, Boolean>() }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isMergeMode) "待追加词条预览（新增 ${newWords.size} 词）" else "待建词表预览（共 ${newWords.size} 词）",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                val groupCount = remember(newWords) {
                    newWords.map { it.groupName }.filter { it.isNotBlank() }.distinct().size
                }
                if (groupCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = cs.primaryContainer
                    ) {
                        Text(
                            text = "$groupCount 个分组",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            val shown = newWords.take(visibleLimit)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { cw ->
                    val isExp = expandedRemarkKeys[cw.key] == true
                    ArchiveWordPreviewItemRow(
                        index1Based = cw.displayIndex,
                        text = cw.text,
                        groupName = cw.groupName,
                        remark = cw.remark,
                        expanded = isExp,
                        onToggleExpand = {
                            if (cw.remark.isNotBlank()) {
                                expandedRemarkKeys[cw.key] = !isExp
                            }
                        },
                        hasImage = cw.stagedImageFile != null
                    )
                }
            }

            val remaining = newWords.size - shown.size
            if (remaining > 0 || visibleLimit > initialLimit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (remaining > 0) {
                        val nextStep = minOf(remaining, 100)
                        FilledTonalButton(
                            onClick = {
                                visibleLimit = if (remaining <= 100) newWords.size else visibleLimit + nextStep
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 38.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (remaining <= 100) {
                                    "展开查看全部 ${newWords.size} 个词条（剩余 $remaining 条）"
                                } else {
                                    "继续展开 $nextStep 条（已显示 ${shown.size} / ${newWords.size}）"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (visibleLimit > initialLimit) {
                        TextButton(
                            onClick = { visibleLimit = initialLimit },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 38.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "收起",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudioPairingPreviewRow(
    row: AudioWordPairingRow,
    enabled: Boolean,
    onClickRebind: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    val (badgeBg, badgeFg, badgeLabel) = when (row.matchKind) {
        PairingMatchKind.EXACT_INDEX_AND_WORD -> Triple(
            cs.primaryContainer,
            cs.onPrimaryContainer,
            SSmartImport.matchExactBadge
        )
        PairingMatchKind.WORD_TEXT -> Triple(
            cs.primaryContainer.copy(alpha = 0.8f),
            cs.onPrimaryContainer,
            SSmartImport.matchWordBadge
        )
        PairingMatchKind.INDEX_ONLY -> Triple(
            cs.secondaryContainer,
            cs.onSecondaryContainer,
            SSmartImport.matchIndexBadge
        )
        PairingMatchKind.SEQUENTIAL_ORDER -> Triple(
            cs.secondaryContainer,
            cs.onSecondaryContainer,
            SSmartImport.matchSeqBadge
        )
        PairingMatchKind.AUTO_NEW_WORD -> Triple(
            cs.tertiaryContainer,
            cs.onTertiaryContainer,
            SSmartImport.matchAutoNewBadge
        )
        PairingMatchKind.MANUAL_OVERRIDE -> Triple(
            cs.primaryContainer,
            cs.onPrimaryContainer,
            SSmartImport.matchManualBadge
        )
        PairingMatchKind.UNASSIGNED -> Triple(
            cs.errorContainer.copy(alpha = 0.7f),
            cs.onErrorContainer,
            SSmartImport.matchUnassignedBadge
        )
    }

    val rowBg by animateColorAsState(
        targetValue = if (isHovered && enabled) {
            lerp(cs.surface, cs.primaryContainer, 0.25f)
        } else {
            cs.surface
        },
        animationSpec = tween(120),
        label = "AudioPairingRowBg"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = rowBg,
        border = BorderStroke(
            1.dp,
            if (isHovered && enabled) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(hoverSrc)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = hoverSrc, indication = null, onClick = onClickRebind)
                } else {
                    Modifier
                }
            )
            .testTag("pairing_row_${row.audioItem.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Source audio filename
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = row.audioItem.displayName,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = cs.outline,
                    modifier = Modifier.size(14.dp)
                )

                // Target word pill
                val targetWord = row.targetWord
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (targetWord != null) cs.surfaceContainerHigh else cs.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isHovered && enabled) cs.primary.copy(alpha = 0.5f) else cs.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (targetWord != null) {
                            Text(
                                text = String.format(Locale.US, "#%03d", targetWord.displayIndex),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = cs.onSurfaceVariant
                            )
                            Text(
                                text = targetWord.text,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                        } else {
                            Text(
                                text = SSmartImport.matchUnassignedBadge,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onErrorContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = SSmartImport.clickToRebindHint,
                            tint = cs.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Metadata & status badges line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }

                if (row.totalTakesForTarget > 1 && row.targetWordKey != null) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = cs.secondaryContainer
                    ) {
                        Text(
                            text = SSmartImport.multiTakeBadge.withArgs("take" to row.takeSequenceInBatch),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }

                if (row.audioItem.needsAudioTranscode && row.targetWordKey != null) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = cs.surfaceContainerHighest
                    ) {
                        Text(
                            text = SSmartImport.transcodeBadge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = row.resolvedSpeakerName,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun WordRebindPickerDialog(
    row: AudioWordPairingRow,
    candidateWords: List<CandidateTargetWord>,
    onSelectOverride: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }

    val selectableWords = remember(candidateWords, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        candidateWords.filter { cw ->
            q.isEmpty() ||
                cw.text.lowercase(Locale.ROOT).contains(q) ||
                cw.remark.lowercase(Locale.ROOT).contains(q) ||
                cw.groupName.lowercase(Locale.ROOT).contains(q) ||
                cw.displayIndex.toString().contains(q)
        }
    }

    val autoStemLabel = row.cleanedWordStem.ifBlank {
        row.audioItem.displayName.substringBeforeLast('.')
    }

    StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = searchQuery.isEmpty(),
            usePlatformDefaultWidth = false
        )
    ) {
        AdaptiveModalSurface(
            targetMaxWidth = 480.dp,
            maxHeightFraction = 0.88f,
            shape = RoundedCornerShape(24.dp),
            containerColor = cs.surface,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = SSmartImport.rebindDialogTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onSurface
                )
                Text(
                    text = row.audioItem.displayName,
                    fontSize = 12.sp,
                    color = cs.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(SSmartImport.rebindSearchPlaceholder, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick special actions: Auto-create word OR Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cs.tertiaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                onSelectOverride(SmartImportEngine.OVERRIDE_AUTO_CREATE)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = cs.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = SSmartImport.rebindAutoCreateOption.withArgs("word" to autoStemLabel.take(8)),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = cs.surfaceContainerHigh,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                onSelectOverride(SmartImportEngine.OVERRIDE_SKIP)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = SSmartImport.rebindSkipOption,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))

                val rebindListState = rememberLazyListState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 120.dp, max = 300.dp)
                ) {
                    LazyColumn(
                        state = rebindListState,
                        contentPadding = PaddingValues(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(items = selectableWords, key = { it.key }) { cw ->
                            val isCurrent = cw.key == row.targetWordKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) cs.primaryContainer else cs.surfaceContainerLow,
                                modifier = studioAnimateItem()
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onSelectOverride(cw.key) }
                            ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "#%03d", cw.displayIndex),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isCurrent) cs.onPrimaryContainer else cs.onSurfaceVariant
                                )
                                Text(
                                    text = cw.text,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) cs.onPrimaryContainer else cs.onSurface
                                )
                                if (cw.remark.isNotBlank()) {
                                    Text(
                                        text = cw.remark,
                                        fontSize = 11.5.sp,
                                        color = if (isCurrent) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    }
                    PlatformVerticalScrollbar(
                        listState = rebindListState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onSelectOverride(null) }) {
                        Text("恢复自动识别", fontSize = 12.5.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(S.common.cancel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
