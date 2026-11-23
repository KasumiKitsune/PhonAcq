package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ColorScheme
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
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.utils.AudioWordPairingRow
import com.example.utils.CandidateTargetWord
import com.example.utils.ImportItemKind
import com.example.utils.PairingMatchKind
import com.example.utils.SmartImportEngine
import com.example.utils.StagedImportItem
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
internal fun ArchiveQueuePreviewCard(
    archiveItems: List<StagedImportItem>,
    modifier: Modifier = Modifier
) {
    if (archiveItems.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.tertiaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, cs.tertiary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = cs.tertiary,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = SSmartImport.archiveQueueCardTitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
            }
            archiveItems.forEach { arch ->
                val inspect = arch.archiveInspect
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = inspect?.archiveProjectName?.ifBlank { arch.displayName } ?: arch.displayName,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (inspect != null) {
                            val spkPrefix = if (inspect.estimatedSpeakerCount > 1) "${inspect.estimatedSpeakerCount} 位发音人 · " else ""
                            Text(
                                text = spkPrefix + SSmartImport.archiveSummaryMeta.withArgs(
                                    "words" to inspect.estimatedWordCount,
                                    "audio" to inspect.estimatedAudioCount
                                ),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                    if (inspect != null && inspect.previewWords.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            inspect.previewWords.take(8).forEachIndexed { idx, pw ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(cs.surface.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "#%03d", idx + 1),
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = cs.onSurfaceVariant
                                    )
                                    if (pw.groupName.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = cs.secondaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = pw.groupName,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = cs.onSecondaryContainer,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = pw.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onSurface,
                                        maxLines = 1
                                    )
                                    if (pw.remark.isNotBlank()) {
                                        Text(
                                            text = pw.remark,
                                            fontSize = 10.5.sp,
                                            color = cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            if (inspect.previewWords.size > 8) {
                                Text(
                                    text = "… 其余 ${inspect.previewWords.size - 8} 个词条已解析就绪",
                                    fontSize = 10.5.sp,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    } else if (!inspect?.parseErrorHint.isNullOrBlank()) {
                        Text(
                            text = "预检提示：${inspect?.parseErrorHint}",
                            fontSize = 11.sp,
                            color = cs.error
                        )
                    }
                }
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

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isMergeMode) "待追加词条预览（新增 ${newWords.size} 词）" else "待建词表预览（共 ${newWords.size} 词）",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                val groupCount = remember(newWords) {
                    newWords.map { it.groupName }.filter { it.isNotBlank() }.distinct().size
                }
                if (groupCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = cs.primaryContainer
                    ) {
                        Text(
                            text = "$groupCount 个分组",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            newWords.take(12).forEach { cw ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(cs.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "#%03d", cw.displayIndex),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurfaceVariant
                    )
                    if (cw.groupName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = cs.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = cw.groupName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = cw.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    if (cw.remark.isNotBlank()) {
                        Text(
                            text = cw.remark,
                            fontSize = 11.5.sp,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (cw.stagedImageFile != null) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = SSmartImport.kindImageLabel,
                            tint = cs.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            if (newWords.size > 12) {
                Text(
                    text = "… 其余 ${newWords.size - 12} 个词条已就绪",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
