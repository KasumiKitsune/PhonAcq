package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.*
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.ui.components.StudioFilterChip
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.*

internal val MULTI_SPEAKER_PALETTE = listOf(
    Color(0xFF1565C0), // Indigo Blue
    Color(0xFFD84315), // Deep Coral
    Color(0xFF2E7D32), // Emerald Green
    Color(0xFF8E24AA), // Royal Violet
    Color(0xFFEF6C00), // Amber Orange
    Color(0xFF00838F), // Cyan Teal
    Color(0xFFC2185B), // Crimson Rose
    Color(0xFF4E342E)  // Warm Umber
)

internal fun cohortColor(index: Int): Color =
    MULTI_SPEAKER_PALETTE[index.mod(MULTI_SPEAKER_PALETTE.size)]

internal fun cohortStrokeDash(index: Int): PathEffect? = when (index % 3) {
    0 -> null
    1 -> PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
    else -> PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
}

/**
 * Quick active-speaker switcher strip inside `ProjectAcousticSummarySheet` when viewing
 * single-speaker phonology on a multi-speaker project (no need to close the modal to switch speakers).
 */
@Composable
fun MacroActiveSpeakerSwitcherStrip(
    speakerPairs: List<Pair<Speaker, List<Word>>>,
    selectedSpeakerId: Long?,
    onSelectSpeaker: (Speaker, List<Word>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (speakerPairs.size < 2) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "切换发音人:",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        speakerPairs.forEachIndexed { idx, (spk, wList) ->
            val recCnt = remember(wList) { wList.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() } }
            val isSelected = spk.id == selectedSpeakerId
            val accent = cohortColor(idx)
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (isSelected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    width = if (isSelected) 1.4.dp else 1.dp,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onSelectSpeaker(spk, wList) }
                    .testTag("macro_quick_speaker_${spk.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(accent, CircleShape)
                    )
                    Text(
                        text = "${spk.name} ($recCnt)",
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Complete Multi-Speaker Phonological & Acoustic Comparison Workbench:
 * - Multi-select speaker & demographic cohort picker + Shared-Vocabulary Anchor Lobanov Calibration
 * - Executive Cross-Speaker Divergence Radar (Tone consistency + Top divergent words with sequential play)
 * - Cross-Speaker 5-Degree Tone System Comparison Card (parallel lanes + zoomed single-category overlay + inventory matrix)
 * - 2nd-Gen Interactive Dynamic Vowel Workbench (1.0x..4.0x zoom/pan, convex hulls, rime focus + shift vectors, same-word linked inspector)
 */
@Composable
fun MultiSpeakerComparisonWorkbench(
    multiReport: MultiSpeakerVowelComparisonReport,
    projectId: Long,
    viewModel: RecordingViewModel?,
    playingWordId: Long?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allSpeakerIds = remember(multiReport.speakerReports) {
        multiReport.speakerReports.map { it.speakerId }.toSet()
    }
    var selectedSpeakerIds by remember(allSpeakerIds) {
        mutableStateOf(allSpeakerIds)
    }
    var cohortMode by remember { mutableStateOf(MultiSpeakerCohortMode.INDIVIDUAL) }
    var onlySharedVocabulary by remember { mutableStateOf(false) }

    var focusedWordId by remember { mutableStateOf<Long?>(null) }
    var focusedVowelGroupKey by remember { mutableStateOf<String?>(null) }

    // Cancellable sequential playback state across speakers
    var sequentialPlayJob by remember { mutableStateOf<Job?>(null) }
    var sequentialPlayingWordId by remember { mutableStateOf<Long?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            sequentialPlayJob?.cancel()
        }
    }

    fun stopSequentialPlay() {
        sequentialPlayJob?.cancel()
        sequentialPlayJob = null
        sequentialPlayingWordId = null
        viewModel?.stopPlayback()
    }

    fun triggerSequentialPlayForWord(comp: CrossSpeakerWordComparison) {
        if (viewModel == null) return
        if (sequentialPlayingWordId == comp.wordId && sequentialPlayJob?.isActive == true) {
            stopSequentialPlay()
            return
        }
        stopSequentialPlay()
        focusedWordId = comp.wordId
        val projDir = File(context.getExternalFilesDir(null), "project_$projectId")
        sequentialPlayingWordId = comp.wordId
        sequentialPlayJob = scope.launch {
            try {
                for (realization in comp.realizations) {
                    if (!isActive) break
                    val fn = realization.word.recordingFile ?: continue
                    val audioFile = File(projDir, fn)
                    if (!audioFile.exists()) continue
                    val tagId = -(realization.word.id * 1000 + realization.speakerId)
                    viewModel.playSpecificAudioFile(audioFile, tagId)
                    val durWait = realization.word.durationMs.coerceIn(480L, 3800L) + 380L
                    delay(durWait)
                }
            } finally {
                if (sequentialPlayingWordId == comp.wordId) {
                    sequentialPlayingWordId = null
                }
            }
        }
    }

    fun playSingleRealization(realization: SpeakerWordRealization) {
        if (viewModel == null) return
        stopSequentialPlay()
        val fn = realization.word.recordingFile ?: return
        val audioFile = File(File(context.getExternalFilesDir(null), "project_$projectId"), fn)
        if (audioFile.exists()) {
            val tagId = -(realization.word.id * 1000 + realization.speakerId)
            viewModel.playSpecificAudioFile(audioFile, tagId)
        }
    }

    val alignedAnalysis = remember(multiReport, selectedSpeakerIds, cohortMode, onlySharedVocabulary) {
        MultiSpeakerComparisonEngine.buildAlignedAnalysis(
            multiReport = multiReport,
            selectedSpeakerIds = selectedSpeakerIds,
            cohortMode = cohortMode,
            onlySharedVocabulary = onlySharedVocabulary
        )
    }

    // Keep focused word valid if filter changes
    LaunchedEffect(alignedAnalysis.wordComparisons) {
        if (focusedWordId != null && alignedAnalysis.wordComparisons.none { it.wordId == focusedWordId }) {
            focusedWordId = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Cohort & Shared-Vocabulary Calibration Control Card
        MultiSpeakerCohortControlCard(
            multiReport = multiReport,
            aligned = alignedAnalysis,
            selectedSpeakerIds = selectedSpeakerIds,
            onToggleSpeaker = { spkId ->
                val next = if (spkId in selectedSpeakerIds) {
                    if (selectedSpeakerIds.size > 2) selectedSpeakerIds - spkId else selectedSpeakerIds
                } else {
                    selectedSpeakerIds + spkId
                }
                selectedSpeakerIds = next
            },
            onSelectAllSpeakers = { selectedSpeakerIds = allSpeakerIds },
            cohortMode = alignedAnalysis.cohortMode,
            onCohortModeChange = { cohortMode = it },
            onlySharedVocabulary = onlySharedVocabulary,
            onOnlySharedVocabularyChange = { onlySharedVocabulary = it }
        )

        // 2. Cross-Speaker Divergence Summary & Top Divergent Words Radar
        if (alignedAnalysis.wordComparisons.isNotEmpty()) {
            MultiSpeakerDivergenceSummaryCard(
                aligned = alignedAnalysis,
                focusedWordId = focusedWordId,
                sequentialPlayingWordId = sequentialPlayingWordId,
                onSelectWord = { comp ->
                    focusedWordId = if (focusedWordId == comp.wordId) null else comp.wordId
                },
                onSequentialPlayWord = { comp -> triggerSequentialPlayForWord(comp) }
            )
        }

        // 3. Cross-Speaker 5-Degree Tone System Comparison Card
        if (alignedAnalysis.alignedToneCategories.isNotEmpty()) {
            MultiSpeakerToneComparisonCard(
                aligned = alignedAnalysis,
                focusedWordId = focusedWordId,
                sequentialPlayingWordId = sequentialPlayingWordId,
                onFocusAndPlayWord = { comp -> triggerSequentialPlayForWord(comp) }
            )
        }

        // 4. 2nd-Gen Interactive Multi-Speaker Dynamic Vowel Workbench
        MultiSpeakerVowelWorkbenchCard(
            aligned = alignedAnalysis,
            focusedWordId = focusedWordId,
            onFocusWordChange = { focusedWordId = it },
            focusedGroupKey = focusedVowelGroupKey,
            onFocusGroupKeyChange = { focusedVowelGroupKey = it },
            playingWordId = playingWordId,
            sequentialPlayingWordId = sequentialPlayingWordId,
            onPlayRealization = { playSingleRealization(it) },
            onSequentialPlayWord = { triggerSequentialPlayForWord(it) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSpeakerCohortControlCard(
    multiReport: MultiSpeakerVowelComparisonReport,
    aligned: MultiSpeakerAlignedAnalysis,
    selectedSpeakerIds: Set<Long>,
    onToggleSpeaker: (Long) -> Unit,
    onSelectAllSpeakers: () -> Unit,
    cohortMode: MultiSpeakerCohortMode,
    onCohortModeChange: (MultiSpeakerCohortMode) -> Unit,
    onlySharedVocabulary: Boolean,
    onOnlySharedVocabularyChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_speaker_cohort_control_card")
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "对比受试者与声道校准基准",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "可自由勾选 ≥2 位发音人局部 PK，或按性别/分组查看群体均值",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selectedSpeakerIds.size < multiReport.speakerReports.size) {
                    TextButton(
                        onClick = onSelectAllSpeakers,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("全选 (${multiReport.speakerReports.size}人)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Multi-select speaker chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                multiReport.speakerReports.forEachIndexed { idx, spkRep ->
                    val isChecked = spkRep.speakerId in selectedSpeakerIds
                    val spkMeta = multiReport.speakersById[spkRep.speakerId]
                    val cohortIdx = aligned.cohorts.indexOfFirst { spkRep.speakerId in it.speakerIds }
                    val col = cohortColor(if (cohortIdx >= 0) cohortIdx else idx)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isChecked) col.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isChecked) 1.4.dp else 1.dp,
                            color = if (isChecked) col else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleSpeaker(spkRep.speakerId) }
                            .testTag("multi_speaker_toggle_${spkRep.speakerId}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isChecked) col else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = buildString {
                                    append(spkRep.speakerName)
                                    val tags = buildList {
                                        spkMeta?.gender?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        spkMeta?.age?.takeIf { it > 0 }?.let { add("${it}岁") }
                                        spkMeta?.groupName?.takeIf { it.isNotBlank() && it != "默认分组" }?.let { add(it) }
                                    }
                                    if (tags.isNotEmpty()) append(" (${tags.joinToString("·")})")
                                    append(" · ${spkRep.analyzedCount}词")
                                },
                                fontSize = 11.5.sp,
                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                color = if (isChecked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Grouping & Shared Vocabulary Calibration Bar
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioFilterChip(
                    selected = cohortMode == MultiSpeakerCohortMode.INDIVIDUAL,
                    onClick = { onCohortModeChange(MultiSpeakerCohortMode.INDIVIDUAL) },
                    labelText = "按个人对比 (${aligned.cohorts.size.takeIf { cohortMode == MultiSpeakerCohortMode.INDIVIDUAL } ?: selectedSpeakerIds.size})"
                )
                if (aligned.availableGenders.size >= 2) {
                    StudioFilterChip(
                        selected = cohortMode == MultiSpeakerCohortMode.BY_GENDER,
                        onClick = { onCohortModeChange(MultiSpeakerCohortMode.BY_GENDER) },
                        labelText = "按性别均值 (${aligned.availableGenders.joinToString("/")})"
                    )
                }
                if (aligned.availableSpeakerGroups.size >= 2) {
                    StudioFilterChip(
                        selected = cohortMode == MultiSpeakerCohortMode.BY_SPEAKER_GROUP,
                        onClick = { onCohortModeChange(MultiSpeakerCohortMode.BY_SPEAKER_GROUP) },
                        labelText = "按分组均值 (${aligned.availableSpeakerGroups.size}组)"
                    )
                }
                if (aligned.sharedWordIds.isNotEmpty()) {
                    StudioFilterChip(
                        selected = onlySharedVocabulary,
                        onClick = { onOnlySharedVocabularyChange(!onlySharedVocabulary) },
                        labelText = "仅共同已录交集 (${aligned.sharedWordIds.size}词)",
                        modifier = Modifier.testTag("multi_speaker_shared_vocab_chip")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (aligned.calibratedSpeakerCount > 0) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (aligned.calibratedSpeakerCount > 0) Icons.Default.Tune else Icons.Default.Verified,
                        contentDescription = null,
                        tint = if (aligned.calibratedSpeakerCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = aligned.calibrationNote,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSpeakerDivergenceSummaryCard(
    aligned: MultiSpeakerAlignedAnalysis,
    focusedWordId: Long?,
    sequentialPlayingWordId: Long?,
    onSelectWord: (CrossSpeakerWordComparison) -> Unit,
    onSequentialPlayWord: (CrossSpeakerWordComparison) -> Unit
) {
    val notableDivergences = remember(aligned.wordComparisons) {
        aligned.wordComparisons.filter {
            it.hasGlideSplit || it.hasToneSplit || it.vowelMaxShiftZ >= 0.85f
        }
    }
    val displayList = remember(notableDivergences, aligned.topDivergentWords) {
        notableDivergences.take(6).ifEmpty { aligned.topDivergentWords.take(4) }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_speaker_divergence_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "跨发音人同异总览与读音分歧榜",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "基于同一字表词条自动比对各人调型曲线、单复韵母动程及归一化舌位距离，点击词条可联动定位并顺次试听",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactSummaryMetricTile(
                    title = "可横向对齐词条",
                    value = "${aligned.wordComparisons.size} 词",
                    sub = if (aligned.strictAllSpeakerSharedCount > 0) "全员共录 ${aligned.strictAllSpeakerSharedCount} 词" else "两两共录对比",
                    modifier = Modifier.weight(1f)
                )
                CompactSummaryMetricTile(
                    title = "声调调型一致率",
                    value = "${aligned.toneConsistencyPercent}%",
                    sub = "五度值与舒促吻合度",
                    highlight = aligned.toneConsistencyPercent >= 85,
                    modifier = Modifier.weight(1f)
                )
                CompactSummaryMetricTile(
                    title = "显著分歧词条",
                    value = "${notableDivergences.size} 词",
                    sub = if (notableDivergences.isEmpty()) "整体高度一致" else "声调/单复韵/舌位分化",
                    isAlert = notableDivergences.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }

            if (displayList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (notableDivergences.isNotEmpty()) "跨人差异最显著词条（点选可在下方元音图串联高亮，点右侧按钮依次连播）："
                    else "跨人最大相对间距词条（整体读音一致，点选可对比试听）：",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayList.forEach { comp ->
                        val isFocused = focusedWordId == comp.wordId
                        val isSeqPlaying = sequentialPlayingWordId == comp.wordId
                        val isDivergent = comp.hasGlideSplit || comp.hasToneSplit || comp.vowelMaxShiftZ >= 0.85f
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectWord(comp) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${comp.originalIndex + 1}. ${comp.wordText}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = if (isDivergent) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = comp.divergenceBadge,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDivergent) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (comp.groupName.isNotBlank()) {
                                            Text(
                                                text = "· ${comp.groupName}",
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = comp.divergenceDetail,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { onSequentialPlayWord(comp) },
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 3.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSeqPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = if (isSeqPlaying) "停止" else "依次试听",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSummaryMetricTile(
    title: String,
    value: String,
    sub: String,
    highlight: Boolean = false,
    isAlert: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = when {
            isAlert -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            highlight -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(text = title, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    isAlert -> MaterialTheme.colorScheme.error
                    highlight -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(text = sub, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Brand-New Cross-Speaker 5-Degree Tone System Comparison Card (Module 2):
 * - Parallel lane overview across all tone categories OR zoomed single-category overlay with ±1σ ribbon
 * - Cross-speaker tone inventory & pitch range summary table
 * - Automatic tone-divergent word chips with sequential audition
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSpeakerToneComparisonCard(
    aligned: MultiSpeakerAlignedAnalysis,
    focusedWordId: Long?,
    sequentialPlayingWordId: Long?,
    onFocusAndPlayWord: (CrossSpeakerWordComparison) -> Unit
) {
    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }
    var showSigmaRibbon by remember { mutableStateOf(true) }
    val textMeasurer = rememberTextMeasurer()

    val focusedCategory = remember(aligned.alignedToneCategories, selectedCategoryKey) {
        aligned.alignedToneCategories.find { it.categoryKey == selectedCategoryKey }
    }
    val toneDivergentWords = remember(aligned.wordComparisons) {
        aligned.wordComparisons.filter { it.hasToneSplit }.take(8)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_speaker_tone_comparison_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "跨发音人声调格局对比（五度调域归一化）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (aligned.toneAlignmentByWordGroup) {
                    "已按字表声调分组跨人对齐，消除男女声绝对音高差异，直观对比各调类曲线走势与分化"
                } else {
                    "已按同源字类与调型原型自动跨人对齐，点选下方任一调类可放大查看 ±1σ 离散度与代表五度值"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // Tone Category Focus Filter Pills
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioFilterChip(
                    selected = selectedCategoryKey == null,
                    onClick = { selectedCategoryKey = null },
                    labelText = "全部调类并排 (${aligned.alignedToneCategories.size})"
                )
                aligned.alignedToneCategories.forEach { cat ->
                    val label = buildString {
                        append(cat.categoryLabel)
                        if (cat.hasCategoryDivergence) append(" ⚡")
                    }
                    StudioFilterChip(
                        selected = selectedCategoryKey == cat.categoryKey,
                        onClick = {
                            selectedCategoryKey = if (selectedCategoryKey == cat.categoryKey) null else cat.categoryKey
                        },
                        labelText = label,
                        isErrorVariant = cat.hasCategoryDivergence && selectedCategoryKey != cat.categoryKey
                    )
                }
                if (focusedCategory != null) {
                    StudioFilterChip(
                        selected = showSigmaRibbon,
                        onClick = { showSigmaRibbon = !showSigmaRibbon },
                        labelText = "±1σ 离散带"
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Five-Degree Tone Comparison Canvas
            val plotBg = MaterialTheme.colorScheme.surface
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(248.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(plotBg)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val leftAxisW = 28.dp.toPx()
                    val topHeaderH = 22.dp.toPx()
                    val bottomPad = 12.dp.toPx()
                    val plotLeft = leftAxisW
                    val plotTop = topHeaderH
                    val plotW = (size.width - leftAxisW - 8.dp.toPx()).coerceAtLeast(20f)
                    val plotH = (size.height - topHeaderH - bottomPad).coerceAtLeast(20f)

                    // 5-degree horizontal grid lines (5 at top -> 1 at bottom)
                    val degreeLabels = arrayOf("5高", "4", "3中", "2", "1低")
                    for (degIdx in 0..4) {
                        val y = plotTop + (degIdx / 4f) * plotH
                        drawLine(
                            color = gridColor,
                            start = Offset(plotLeft, y),
                            end = Offset(plotLeft + plotW, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f)
                        )
                        val lbl = textMeasurer.measure(
                            text = degreeLabels[degIdx],
                            style = TextStyle(fontSize = 9.5.sp, color = axisLabelColor, fontWeight = FontWeight.Medium)
                        )
                        drawText(
                            textLayoutResult = lbl,
                            topLeft = Offset(0f, (y - lbl.size.height / 2f).coerceIn(0f, size.height - lbl.size.height))
                        )
                    }

                    fun normPitchToY(p01: Float): Float {
                        return plotTop + (1f - p01.coerceIn(0f, 1f)) * plotH
                    }

                    if (focusedCategory == null) {
                        // Overview Mode: Clean side-by-side vertical lanes for up to 7 tone categories
                        val cats = aligned.alignedToneCategories.take(7)
                        val laneCount = cats.size.coerceAtLeast(1)
                        val laneW = plotW / laneCount

                        cats.forEachIndexed { laneIdx, cat ->
                            val laneX = plotLeft + laneIdx * laneW
                            if (laneIdx > 0) {
                                drawLine(
                                    color = gridColor.copy(alpha = 0.85f),
                                    start = Offset(laneX, 2f),
                                    end = Offset(laneX, plotTop + plotH),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                            }

                            // Lane Title
                            val headerTxt = cat.categoryLabel.take(6) + if (cat.hasCategoryDivergence) "⚡" else ""
                            val hdrLayout = textMeasurer.measure(
                                text = headerTxt,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cat.hasCategoryDivergence) Color(0xFFD84315) else axisLabelColor
                                )
                            )
                            val hx = laneX + ((laneW - hdrLayout.size.width) * 0.5f).coerceAtLeast(2f)
                            drawText(hdrLayout, topLeft = Offset(hx, 0f))

                            val innerPad = (laneW * 0.12f).coerceIn(4.dp.toPx(), 12.dp.toPx())
                            val maxSpanW = (laneW - innerPad * 2f).coerceAtLeast(10f)

                            cat.cohortRealizations.forEach { real ->
                                val col = cohortColor(real.colorIndex)
                                val spanW = maxSpanW * real.relativeDurationRatio.coerceIn(0.42f, 1.0f)
                                val pts = real.meanContour.mapIndexed { i, p ->
                                    val px = laneX + innerPad + (i / 4f) * spanW
                                    Offset(px, normPitchToY(p))
                                }
                                val path = buildSmoothVowelCurvePath(pts)
                                drawPath(
                                    path = path,
                                    color = col,
                                    style = Stroke(
                                        width = 2.6.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                        pathEffect = cohortStrokeDash(real.colorIndex)
                                    )
                                )
                                drawCircle(color = col, radius = 2.8.dp.toPx(), center = pts.first())
                                if (real.isChecked) {
                                    val end = pts.last()
                                    drawLine(
                                        color = col,
                                        start = Offset(end.x, end.y - 6.dp.toPx()),
                                        end = Offset(end.x, end.y + 6.dp.toPx()),
                                        strokeWidth = 2.2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                } else {
                                    drawDirectedArrowHead(
                                        from = pts[pts.size - 2],
                                        to = pts.last(),
                                        color = col,
                                        headLenPx = 6.5.dp.toPx(),
                                        headHalfWidthPx = 3.6.dp.toPx()
                                    )
                                }
                            }
                        }
                    } else {
                        // Zoomed Single-Category Mode: Full width with optional ±1σ ribbons and Chao value tags
                        val hdrLayout = textMeasurer.measure(
                            text = "${focusedCategory.categoryLabel} · ${focusedCategory.alignmentSourceLabel}（例字：${
                                focusedCategory.cohortRealizations.flatMap { it.exampleWords }.distinct().take(5).joinToString(" ")
                            }）",
                            style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = axisLabelColor)
                        )
                        drawText(hdrLayout, topLeft = Offset(plotLeft, 0f))

                        val innerPad = 14.dp.toPx()
                        val maxSpanW = (plotW - innerPad * 2f).coerceAtLeast(20f)

                        focusedCategory.cohortRealizations.forEach { real ->
                            val col = cohortColor(real.colorIndex)
                            val spanW = maxSpanW * real.relativeDurationRatio.coerceIn(0.44f, 1.0f)

                            if (showSigmaRibbon && real.upperBand1Sigma.size >= 5 && real.lowerBand1Sigma.size >= 5) {
                                val rib = Path()
                                for (i in 0..4) {
                                    val px = plotLeft + innerPad + (i / 4f) * spanW
                                    val py = normPitchToY(real.upperBand1Sigma[i])
                                    if (i == 0) rib.moveTo(px, py) else rib.lineTo(px, py)
                                }
                                for (i in 4 downTo 0) {
                                    val px = plotLeft + innerPad + (i / 4f) * spanW
                                    val py = normPitchToY(real.lowerBand1Sigma[i])
                                    rib.lineTo(px, py)
                                }
                                rib.close()
                                drawPath(path = rib, color = col.copy(alpha = 0.12f), style = Fill)
                            }

                            val pts = real.meanContour.mapIndexed { i, p ->
                                Offset(plotLeft + innerPad + (i / 4f) * spanW, normPitchToY(p))
                            }
                            val path = buildSmoothVowelCurvePath(pts)
                            drawPath(
                                path = path,
                                color = col,
                                style = Stroke(
                                    width = 3.2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = cohortStrokeDash(real.colorIndex)
                                )
                            )
                            pts.forEach { pt ->
                                drawCircle(color = col, radius = 3.5.dp.toPx(), center = pt)
                            }
                            if (real.isChecked) {
                                val end = pts.last()
                                drawLine(
                                    color = col,
                                    start = Offset(end.x, end.y - 7.5.dp.toPx()),
                                    end = Offset(end.x, end.y + 7.5.dp.toPx()),
                                    strokeWidth = 2.8.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }

            // Focused Category Readout Strip (when a specific tone category is selected)
            if (focusedCategory != null) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    focusedCategory.cohortRealizations.forEach { real ->
                        val col = cohortColor(real.colorIndex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(9.dp).background(col, CircleShape))
                                Text(real.cohortLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    text = "[${real.representativeChao}] ${real.toneShapeLabel}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = col
                                )
                            }
                            Text(
                                text = "均值 ${String.format(Locale.US, "%.0f", real.meanF0Hz)}Hz · ${real.sampleCount}字",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Speaker Tone Inventory Correspondence Summary
            Text(
                text = "各发音人/分组声调格局摘要",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                aligned.speakerToneInventories.forEach { inv ->
                    val col = cohortColor(inv.colorIndex)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(9.dp).background(col, CircleShape))
                                    Text(inv.cohortLabel, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    Text(
                                        text = "共 ${inv.totalToneCount} 调类" +
                                            if (inv.checkedToneCount > 0) " (${inv.smoothToneCount}舒+${inv.checkedToneCount}促)" else "",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = col
                                    )
                                }
                                Text(
                                    text = "音域 ${inv.pitchRangeText}",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = inv.inventoryChaoSummary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Tone Divergent Word Quick Audition Chips
            if (toneDivergentWords.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "跨人调型异读词条（点击依次对比播放）：",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    toneDivergentWords.forEach { comp ->
                        val isPlayingSeq = sequentialPlayingWordId == comp.wordId
                        val isSel = focusedWordId == comp.wordId
                        val tonesStr = comp.realizations.take(3).joinToString("/") { it.chaoTone }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel || isPlayingSeq) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onFocusAndPlayWord(comp) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingSeq) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${comp.wordText} [$tonesStr]",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 2nd-Generation Interactive Multi-Speaker Dynamic Vowel Workbench (Module 3):
 * - 1.0x..4.0x Zoom & Pan on spacious 330dp canvas
 * - Two-layer visual clarity: Outer Convex Hulls + Category Centroids at overview vs 1σ Ellipses + Shift Arrows when a rime group is focused
 * - Same-Word Multi-Speaker Linked Highlighting & Side-by-Side Acoustic Inspector with cancellable sequential audition
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSpeakerVowelWorkbenchCard(
    aligned: MultiSpeakerAlignedAnalysis,
    focusedWordId: Long?,
    onFocusWordChange: (Long?) -> Unit,
    focusedGroupKey: String?,
    onFocusGroupKeyChange: (String?) -> Unit,
    playingWordId: Long?,
    sequentialPlayingWordId: Long?,
    onPlayRealization: (SpeakerWordRealization) -> Unit,
    onSequentialPlayWord: (CrossSpeakerWordComparison) -> Unit
) {
    var useLobanovNorm by remember { mutableStateOf(true) }
    var glideFilter by remember { mutableStateOf(0) } // 0 = 全部, 1 = 单元音, 2 = 复/三元音
    var showIndividualScatterInOverview by remember { mutableStateOf(true) }

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val textMeasurer = rememberTextMeasurer()
    val labelLayoutCache = remember(textMeasurer) { HashMap<String, TextLayoutResult>(160) }

    val focusedCategory = remember(aligned.alignedVowelCategories, focusedGroupKey) {
        aligned.alignedVowelCategories.find { it.groupKey == focusedGroupKey }
    }
    val focusedWordComparison = remember(aligned.wordComparisons, focusedWordId) {
        aligned.wordComparisons.find { it.wordId == focusedWordId }
    }

    val speakerColorIndexMap = remember(aligned.cohorts) {
        val map = HashMap<Long, Int>()
        aligned.cohorts.forEach { c ->
            c.speakerIds.forEach { sid -> map[sid] = c.colorIndex }
        }
        map
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_speaker_lobanov_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = S.acoustics.lobanovTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (useLobanovNorm) {
                            S.acoustics.lobanovActiveLongExplanation
                        } else {
                            S.acoustics.barkPhysicalLongExplanation
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Zoom Controls (1.0x .. 4.0x)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = {
                            val next = (zoomScale / 1.3f).coerceAtLeast(1f)
                            zoomScale = next
                            if (next <= 1.02f) panOffset = Offset.Zero
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = S.acoustics.zoomOutVowelDesc, modifier = Modifier.size(17.dp))
                    }
                    Text(
                        text = String.format(Locale.US, "%.1fx", zoomScale),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    IconButton(
                        onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(4.0f) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = S.acoustics.zoomInVowelDesc, modifier = Modifier.size(17.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Coordinate & Glide Type Filter Bar
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioFilterChip(
                    selected = useLobanovNorm,
                    onClick = { useLobanovNorm = true },
                    labelText = S.acoustics.chipLobanovNorm,
                    modifier = Modifier.testTag("lobanov_norm_on")
                )
                StudioFilterChip(
                    selected = !useLobanovNorm,
                    onClick = { useLobanovNorm = false },
                    labelText = S.acoustics.chipBarkPhysical,
                    modifier = Modifier.testTag("lobanov_norm_off")
                )
                StudioFilterChip(
                    selected = glideFilter == 0,
                    onClick = { glideFilter = 0 },
                    labelText = S.acoustics.chipAllRimes
                )
                StudioFilterChip(
                    selected = glideFilter == 1,
                    onClick = { glideFilter = 1 },
                    labelText = S.acoustics.chipOnlyMonophthongs
                )
                StudioFilterChip(
                    selected = glideFilter == 2,
                    onClick = { glideFilter = 2 },
                    labelText = S.acoustics.chipOnlyDiphthongs
                )
                if (focusedCategory == null) {
                    StudioFilterChip(
                        selected = showIndividualScatterInOverview,
                        onClick = { showIndividualScatterInOverview = !showIndividualScatterInOverview },
                        labelText = if (showIndividualScatterInOverview) "外包络+散点" else "仅看外包络与质心"
                    )
                }
            }

            // Rime / Vowel Category Focus Bar (Eliminates single-color soup!)
            if (aligned.alignedVowelCategories.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "音类聚焦:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioFilterChip(
                        selected = focusedGroupKey == null,
                        onClick = { onFocusGroupKeyChange(null) },
                        labelText = "全局格局包络"
                    )
                    aligned.alignedVowelCategories.forEach { vCat ->
                        val tag = buildString {
                            append(vCat.groupLabel)
                            if (vCat.hasGlideTypeSplit) {
                                append(" ⚡单复")
                            } else if (vCat.maxCentroidShiftZ >= 0.75f) {
                                append(String.format(Locale.US, " Δ%.1f", vCat.maxCentroidShiftZ))
                            }
                        }
                        StudioFilterChip(
                            selected = focusedGroupKey == vCat.groupKey,
                            onClick = {
                                onFocusGroupKeyChange(if (focusedGroupKey == vCat.groupKey) null else vCat.groupKey)
                            },
                            labelText = tag
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Interactive Vowel Space Canvas (330dp with Pinch/Pan + Same-Word Multi-Speaker Hit Testing)
            val hitList = remember { ArrayList<Pair<Offset, NormalizedVowelToken>>(256) }
            val textHintColor = MaterialTheme.colorScheme.onSurfaceVariant
            val gridCol = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            val linkHighlightColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4.0f)
                            zoomScale = newScale
                            if (newScale <= 1.01f) {
                                panOffset = Offset.Zero
                            } else {
                                val maxPanX = size.width * (newScale - 1f) * 0.5f
                                val maxPanY = size.height * (newScale - 1f) * 0.5f
                                panOffset = Offset(
                                    (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                    (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                )
                            }
                        }
                    }
                    .pointerInput(useLobanovNorm, glideFilter, focusedGroupKey, showIndividualScatterInOverview, zoomScale, panOffset) {
                        detectTapGestures(
                            onDoubleTap = {
                                zoomScale = if (zoomScale > 1.2f) 1f else 2.2f
                                panOffset = Offset.Zero
                            },
                            onTap = { tap ->
                                val hit = hitList.minByOrNull { (p, _) -> (p - tap).getDistance() }
                                if (hit != null && (hit.first - tap).getDistance() <= 28.dp.toPx()) {
                                    val tok = hit.second
                                    onFocusWordChange(tok.word.id)
                                    val comp = aligned.wordComparisons.find { it.wordId == tok.word.id }
                                    val spkReal = comp?.realizations?.find { it.speakerId == tok.speakerId }
                                    if (spkReal != null) {
                                        onPlayRealization(spkReal)
                                    }
                                } else {
                                    onFocusWordChange(null)
                                }
                            }
                        )
                    }
                    .padding(10.dp)
            ) {
                Text(S.acoustics.cornerLobanovFrontHigh, fontSize = 9.sp, color = textHintColor, modifier = Modifier.align(Alignment.TopStart))
                Text(S.acoustics.cornerLobanovBackHigh, fontSize = 9.sp, color = textHintColor, modifier = Modifier.align(Alignment.TopEnd))
                Text(S.acoustics.cornerLobanovCentralLow, fontSize = 9.sp, color = textHintColor, modifier = Modifier.align(Alignment.BottomCenter))

                Canvas(modifier = Modifier.fillMaxSize()) {
                    hitList.clear()
                    val padH = 24.dp.toPx()
                    val padV = 22.dp.toPx()
                    val plotW = (size.width - padH * 2).coerceAtLeast(20f)
                    val plotH = (size.height - padV * 2).coerceAtLeast(20f)
                    val pivot = Offset(size.width * 0.5f, size.height * 0.5f)

                    val zMin = -2.35f
                    val zMax = 2.35f
                    val b2Min = PhoneticScaleUtils.hzToBark(580f)
                    val b2Max = PhoneticScaleUtils.hzToBark(2720f)
                    val b1Min = PhoneticScaleUtils.hzToBark(210f)
                    val b1Max = PhoneticScaleUtils.hzToBark(980f)

                    fun applyViewport(raw: Offset): Offset {
                        return Offset(
                            x = pivot.x + (raw.x - pivot.x) * zoomScale + panOffset.x,
                            y = pivot.y + (raw.y - pivot.y) * zoomScale + panOffset.y
                        )
                    }

                    fun mapLobanov(z1: Float, z2: Float): Offset {
                        val xNorm = ((zMax - z2) / (zMax - zMin)).coerceIn(0f, 1f)
                        val yNorm = ((z1 - zMin) / (zMax - zMin)).coerceIn(0f, 1f)
                        return applyViewport(Offset(padH + xNorm * plotW, padV + yNorm * plotH))
                    }

                    fun mapBarkHz(f1: Float, f2: Float): Offset {
                        val xNorm = ((b2Max - PhoneticScaleUtils.hzToBark(f2)) / (b2Max - b2Min)).coerceIn(0f, 1f)
                        val yNorm = ((PhoneticScaleUtils.hzToBark(f1) - b1Min) / (b1Max - b1Min)).coerceIn(0f, 1f)
                        return applyViewport(Offset(padH + xNorm * plotW, padV + yNorm * plotH))
                    }

                    fun mapBarkDirect(b1: Float, b2: Float): Offset {
                        val xNorm = ((b2Max - b2) / (b2Max - b2Min)).coerceIn(0f, 1f)
                        val yNorm = ((b1 - b1Min) / (b1Max - b1Min)).coerceIn(0f, 1f)
                        return applyViewport(Offset(padH + xNorm * plotW, padV + yNorm * plotH))
                    }

                    // Reference Crosshairs
                    val zeroCenter = mapLobanov(0f, 0f)
                    drawLine(
                        color = gridCol,
                        start = Offset(0f, zeroCenter.y.coerceIn(0f, size.height)),
                        end = Offset(size.width, zeroCenter.y.coerceIn(0f, size.height)),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawLine(
                        color = gridCol,
                        start = Offset(zeroCenter.x.coerceIn(0f, size.width), 0f),
                        end = Offset(zeroCenter.x.coerceIn(0f, size.width), size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    val placedBoxes = ArrayList<Rect>()

                    // LAYER 1A: Global Overview -> Per-Cohort Outer Vowel Space Convex Hulls + Category Centroids
                    if (focusedCategory == null) {
                        aligned.cohorts.forEach { cohort ->
                            val hullPts = if (useLobanovNorm) {
                                cohort.convexHullLobanov.map { (z2, z1) -> mapLobanov(z1, z2) }
                            } else {
                                cohort.convexHullBark.map { (b2, b1) -> mapBarkDirect(b1, b2) }
                            }
                            if (hullPts.size >= 3) {
                                val col = cohortColor(cohort.colorIndex)
                                val hullPath = Path().apply {
                                    moveTo(hullPts[0].x, hullPts[0].y)
                                    for (i in 1 until hullPts.size) lineTo(hullPts[i].x, hullPts[i].y)
                                    close()
                                }
                                drawPath(
                                    path = hullPath,
                                    color = col.copy(alpha = if (focusedWordId != null) 0.03f else 0.07f),
                                    style = Fill
                                )
                                drawPath(
                                    path = hullPath,
                                    color = col.copy(alpha = if (focusedWordId != null) 0.25f else 0.65f),
                                    style = Stroke(
                                        width = 1.8.dp.toPx(),
                                        pathEffect = cohortStrokeDash(cohort.colorIndex)
                                    )
                                )
                            }
                        }

                        // Draw Category Centroid Markers & Labels in Overview when not focusing a single word
                        if (focusedWordId == null) {
                            aligned.alignedVowelCategories.forEach { vCat ->
                                val cPts = vCat.cohortStats.map { st ->
                                    val p = if (useLobanovNorm) {
                                        mapLobanov(st.centerZ1, st.centerZ2)
                                    } else {
                                        mapBarkHz(st.meanF1Hz, st.meanF2Hz)
                                    }
                                    st to p
                                }
                                // Connect same category centroids across cohorts with subtle shift arrow if >= 2
                                for (k in 1 until cPts.size) {
                                    val from = cPts[0].second
                                    val to = cPts[k].second
                                    val col = cohortColor(cPts[k].first.colorIndex)
                                    if ((to - from).getDistance() >= 8.dp.toPx()) {
                                        drawLine(
                                            color = col.copy(alpha = 0.55f),
                                            start = from,
                                            end = to,
                                            strokeWidth = 1.6.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                                        )
                                        drawDirectedArrowHead(
                                            from = from,
                                            to = to,
                                            color = col.copy(alpha = 0.72f),
                                            headLenPx = 6.5.dp.toPx(),
                                            headHalfWidthPx = 3.5.dp.toPx()
                                        )
                                    }
                                }
                                // Label category at mean centroid
                                if (cPts.isNotEmpty()) {
                                    val avgX = cPts.map { it.second.x }.average().toFloat()
                                    val avgY = cPts.map { it.second.y }.average().toFloat()
                                    drawSmartCanvasLabel(
                                        textMeasurer = textMeasurer,
                                        cache = labelLayoutCache,
                                        text = "[${vCat.groupLabel.substringBefore(' ')}]",
                                        anchor = Offset(avgX, avgY),
                                        color = Color(0xFF334155),
                                        placedBoxes = placedBoxes,
                                        forceDraw = false
                                    )
                                }
                            }
                        }
                    } else {
                        // LAYER 1B: Focused Rime Category -> Draw 1-sigma ellipses + bold Centroid Shift Vectors!
                        focusedCategory.cohortStats.forEach { st ->
                            val el = st.ellipse
                            val col = cohortColor(st.colorIndex)
                            val centerPx = if (useLobanovNorm) {
                                mapLobanov(el.centerLobanovZ1, el.centerLobanovZ2)
                            } else {
                                mapBarkHz(el.centerF1Hz, el.centerF2Hz)
                            }
                            val rxPx = (if (useLobanovNorm) {
                                (el.semiMajorLobanov / (zMax - zMin)) * plotW
                            } else {
                                (el.semiMajorBark / (b2Max - b2Min)) * plotW
                            }) * zoomScale
                            val ryPx = (if (useLobanovNorm) {
                                (el.semiMinorLobanov / (zMax - zMin)) * plotH
                            } else {
                                (el.semiMinorBark / (b1Max - b1Min)) * plotH
                            }) * zoomScale
                            val angRad = if (useLobanovNorm) el.angleRadLobanov else el.angleRadBark
                            val deg = (-Math.toDegrees(angRad.toDouble())).toFloat()

                            withTransform({ rotate(degrees = deg, pivot = centerPx) }) {
                                drawOval(
                                    color = col.copy(alpha = 0.14f),
                                    topLeft = Offset(centerPx.x - rxPx, centerPx.y - ryPx),
                                    size = Size(rxPx * 2f, ryPx * 2f)
                                )
                                drawOval(
                                    color = col.copy(alpha = 0.78f),
                                    topLeft = Offset(centerPx.x - rxPx, centerPx.y - ryPx),
                                    size = Size(rxPx * 2f, ryPx * 2f),
                                    style = Stroke(
                                        width = 1.8.dp.toPx(),
                                        pathEffect = cohortStrokeDash(st.colorIndex)
                                    )
                                )
                            }
                            // Diamond centroid marker
                            drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = centerPx)
                            drawCircle(color = col, radius = 4.dp.toPx(), center = centerPx)
                        }

                        // Bold Centroid Shift Vectors between cohorts
                        focusedCategory.shiftVectors.forEach { vec ->
                            val fromPt = if (useLobanovNorm) {
                                mapLobanov(vec.fromZ1, vec.fromZ2)
                            } else {
                                mapBarkHz(vec.fromF1Hz, vec.fromF2Hz)
                            }
                            val toPt = if (useLobanovNorm) {
                                mapLobanov(vec.toZ1, vec.toZ2)
                            } else {
                                mapBarkHz(vec.toF1Hz, vec.toF2Hz)
                            }
                            val col = cohortColor(vec.toColorIndex)
                            if ((toPt - fromPt).getDistance() >= 6.dp.toPx()) {
                                drawLine(
                                    color = col,
                                    start = fromPt,
                                    end = toPt,
                                    strokeWidth = 2.6.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 5f), 0f)
                                )
                                drawDirectedArrowHead(
                                    from = fromPt,
                                    to = toPt,
                                    color = col,
                                    headLenPx = 9.dp.toPx(),
                                    headHalfWidthPx = 5.dp.toPx()
                                )
                            }
                        }
                    }

                    // LAYER 2: Individual Tokens & Trajectories
                    val shouldDrawTokens = focusedCategory != null || showIndividualScatterInOverview || focusedWordId != null
                    val focusedWordMatchedPoints = ArrayList<Pair<Offset, NormalizedVowelToken>>()

                    if (shouldDrawTokens) {
                        aligned.calibratedTokens.forEach { tok ->
                            if (glideFilter == 1 && tok.vowelType != VowelGlideType.MONOPHTHONG) return@forEach
                            if (glideFilter == 2 && tok.vowelType == VowelGlideType.MONOPHTHONG) return@forEach
                            if (focusedCategory != null && tok.groupKey != focusedCategory.groupKey && tok.word.id != focusedWordId) {
                                return@forEach
                            }

                            val cIdx = speakerColorIndexMap[tok.speakerId] ?: 0
                            val col = cohortColor(cIdx)
                            val isFocusedWord = focusedWordId != null && tok.word.id == focusedWordId
                            val isDimmed = focusedWordId != null && !isFocusedWord
                            val alpha = when {
                                isFocusedWord -> 1.0f
                                isDimmed -> 0.14f
                                focusedCategory != null -> 0.90f
                                else -> 0.72f
                            }

                            if (tok.vowelType == VowelGlideType.MONOPHTHONG) {
                                val pt = if (useLobanovNorm) {
                                    mapLobanov(tok.z1Nucleus, tok.z2Nucleus)
                                } else {
                                    mapBarkHz(tok.nucleusF1Hz.toFloat(), tok.nucleusF2Hz.toFloat())
                                }
                                hitList.add(pt to tok)
                                if (isFocusedWord) focusedWordMatchedPoints.add(pt to tok)

                                drawSpeakerShapeMarker(
                                    center = pt,
                                    color = col.copy(alpha = alpha),
                                    shapeIndex = cIdx,
                                    radiusPx = if (isFocusedWord) 6.2.dp.toPx() else 3.8.dp.toPx()
                                )
                                if (focusedCategory != null && !isDimmed || zoomScale >= 1.55f && !isDimmed) {
                                    drawSmartCanvasLabel(
                                        textMeasurer = textMeasurer,
                                        cache = labelLayoutCache,
                                        text = tok.displayLabel,
                                        anchor = pt,
                                        color = col,
                                        placedBoxes = placedBoxes,
                                        forceDraw = false
                                    )
                                }
                            } else {
                                val pts = if (useLobanovNorm) {
                                    listOf(
                                        mapLobanov(tok.z1Onset, tok.z2Onset),
                                        mapLobanov(tok.z1Nucleus, tok.z2Nucleus),
                                        mapLobanov(tok.z1Target, tok.z2Target)
                                    )
                                } else {
                                    listOf(
                                        mapBarkHz(tok.onsetF1Hz.toFloat(), tok.onsetF2Hz.toFloat()),
                                        mapBarkHz(tok.nucleusF1Hz.toFloat(), tok.nucleusF2Hz.toFloat()),
                                        mapBarkHz(tok.targetF1Hz.toFloat(), tok.targetF2Hz.toFloat())
                                    )
                                }
                                hitList.add(pts[1] to tok)
                                hitList.add(pts.last() to tok)
                                if (isFocusedWord) focusedWordMatchedPoints.add(pts[1] to tok)

                                val path = buildSmoothVowelCurvePath(pts)
                                drawPath(
                                    path = path,
                                    color = col.copy(alpha = alpha),
                                    style = Stroke(
                                        width = if (isFocusedWord) 3.2.dp.toPx() else 2.1.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        pathEffect = cohortStrokeDash(cIdx)
                                    )
                                )
                                drawDirectedArrowHead(
                                    from = pts[pts.size - 2],
                                    to = pts.last(),
                                    color = col.copy(alpha = alpha),
                                    headLenPx = if (isFocusedWord) 9.dp.toPx() else 7.2.dp.toPx(),
                                    headHalfWidthPx = if (isFocusedWord) 4.8.dp.toPx() else 3.8.dp.toPx()
                                )
                                if (focusedCategory != null && !isDimmed || zoomScale >= 1.55f && !isDimmed) {
                                    drawSmartCanvasLabel(
                                        textMeasurer = textMeasurer,
                                        cache = labelLayoutCache,
                                        text = tok.displayLabel,
                                        anchor = pts.first(),
                                        color = col,
                                        placedBoxes = placedBoxes,
                                        forceDraw = false
                                    )
                                }
                            }
                        }
                    }

                    // LAYER 3: Same-Word Multi-Speaker Linked Highlight Overlay
                    if (focusedWordMatchedPoints.size >= 1) {
                        for (i in 0 until focusedWordMatchedPoints.size - 1) {
                            val pA = focusedWordMatchedPoints[i].first
                            val pB = focusedWordMatchedPoints[i + 1].first
                            drawLine(
                                color = linkHighlightColor.copy(alpha = 0.78f),
                                start = pA,
                                end = pB,
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
                            )
                        }
                        focusedWordMatchedPoints.forEach { (pt, tok) ->
                            val cIdx = speakerColorIndexMap[tok.speakerId] ?: 0
                            val col = cohortColor(cIdx)
                            drawCircle(color = col.copy(alpha = 0.24f), radius = 12.dp.toPx(), center = pt)
                            drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = pt)
                            drawCircle(color = col, radius = 5.dp.toPx(), center = pt)
                            drawSmartCanvasLabel(
                                textMeasurer = textMeasurer,
                                cache = labelLayoutCache,
                                text = "${tok.speakerName}:${tok.displayLabel}",
                                anchor = pt,
                                color = col,
                                placedBoxes = placedBoxes,
                                forceDraw = true
                            )
                        }
                    }
                }
            }

            // Focused Rime Category Shift Readout Banner
            if (focusedCategory != null && focusedCategory.shiftVectors.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "【${focusedCategory.groupLabel}】跨人质心位移量化：",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        focusedCategory.shiftVectors.forEach { vec ->
                            Text(
                                text = "• ${vec.fromCohortLabel} → ${vec.toCohortLabel}：${vec.directionSummary} " +
                                    "(${vec.fromF1Hz.roundToInt()}/${vec.fromF2Hz.roundToInt()}Hz → ${vec.toF1Hz.roundToInt()}/${vec.toF2Hz.roundToInt()}Hz)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Same-Word Multi-Speaker Linked Acoustic Inspector (replaces isolated 1-line text!)
            AnimatedVisibility(visible = focusedWordComparison != null) {
                if (focusedWordComparison != null) {
                    val isSeqPlaying = sequentialPlayingWordId == focusedWordComparison.wordId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .testTag("multi_speaker_word_inspector")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "同词跨人联动对比：「${focusedWordComparison.wordText}」(${focusedWordComparison.realizations.size}人已录)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${focusedWordComparison.divergenceBadge} · ${focusedWordComparison.divergenceDetail}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = { onSequentialPlayWord(focusedWordComparison) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSeqPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isSeqPlaying) "停止连播" else "A/B依次连播",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = { onFocusWordChange(null) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = S.common.close, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            focusedWordComparison.realizations.forEach { real ->
                                val col = cohortColor(real.colorIndex)
                                val tok = real.primaryToken
                                val tagId = -(real.word.id * 1000 + real.speakerId)
                                val isPlayingThis = playingWordId == tagId
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(modifier = Modifier.size(8.dp).background(col, CircleShape))
                                                Text(real.speakerName, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                Text(
                                                    text = "调[${real.chaoTone}]",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = col
                                                )
                                                if (tok != null) {
                                                    Text(
                                                        text = tok.vowelType.chineseLabel.substringBefore(' '),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                            if (tok != null) {
                                                Text(
                                                    text = String.format(
                                                        Locale.US,
                                                        "核 %d/%dHz · Z1=%+.2f, Z2=%+.2f · 动程 %.1f Bark",
                                                        tok.nucleusF1Hz,
                                                        tok.nucleusF2Hz,
                                                        tok.z1Nucleus,
                                                        tok.z2Nucleus,
                                                        tok.barkGlideSpan
                                                    ),
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        FilledTonalIconButton(
                                            onClick = { onPlayRealization(real) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                contentDescription = real.speakerName,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Cohort Legend with Distinct Shapes, Colors & Vowel Space Area
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                aligned.cohorts.forEach { cohort ->
                    val col = cohortColor(cohort.colorIndex)
                    val shapeGlyph = when (cohort.colorIndex % 3) {
                        0 -> "● 实线"
                        1 -> "▲ 长虚线"
                        else -> "■ 点虚线"
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(shapeGlyph, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = col)
                        Text(
                            text = buildString {
                                append(cohort.label)
                                if (cohort.vowelSpaceAreaZ2 > 0.1f) {
                                    append(String.format(Locale.US, " (面积 %.1f Z²)", cohort.vowelSpaceAreaZ2))
                                }
                                if (cohort.calibratedBySharedAnchors) {
                                    append(" · 锚点校准")
                                }
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSpeakerShapeMarker(
    center: Offset,
    color: Color,
    shapeIndex: Int,
    radiusPx: Float
) {
    when (shapeIndex % 3) {
        0 -> drawCircle(color = color, radius = radiusPx, center = center)
        1 -> {
            val tri = Path().apply {
                moveTo(center.x, center.y - radiusPx * 1.15f)
                lineTo(center.x - radiusPx * 1.05f, center.y + radiusPx * 0.9f)
                lineTo(center.x + radiusPx * 1.05f, center.y + radiusPx * 0.9f)
                close()
            }
            drawPath(path = tri, color = color, style = Fill)
        }
        else -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - radiusPx * 0.9f, center.y - radiusPx * 0.9f),
                size = Size(radiusPx * 1.8f, radiusPx * 1.8f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

private fun DrawScope.drawSmartCanvasLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    cache: MutableMap<String, TextLayoutResult>,
    text: String,
    anchor: Offset,
    color: Color,
    placedBoxes: MutableList<Rect>,
    forceDraw: Boolean
) {
    if (anchor.x !in 0f..size.width || anchor.y !in 0f..size.height) return
    val layout = cache.getOrPut(text) {
        textMeasurer.measure(
            text = text,
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
    }
    val tw = layout.size.width.toFloat()
    val th = layout.size.height.toFloat()
    val margin = 3.dp.toPx()

    val candidates = arrayOf(
        Offset(anchor.x + 6.dp.toPx(), anchor.y - th * 0.5f),
        Offset(anchor.x - tw - 6.dp.toPx(), anchor.y - th * 0.5f),
        Offset(anchor.x - tw * 0.5f, anchor.y - th - 5.dp.toPx()),
        Offset(anchor.x - tw * 0.5f, anchor.y + 5.dp.toPx())
    )

    for (cand in candidates) {
        val cx = cand.x.coerceIn(2f, (size.width - tw - 2f).coerceAtLeast(2f))
        val cy = cand.y.coerceIn(2f, (size.height - th - 2f).coerceAtLeast(2f))
        val rect = Rect(cx - margin, cy - margin, cx + tw + margin, cy + th + margin)
        if (placedBoxes.none { it.overlaps(rect) }) {
            placedBoxes.add(rect)
            drawText(textLayoutResult = layout, color = color, topLeft = Offset(cx, cy))
            return
        }
    }

    if (forceDraw) {
        val fallback = candidates[0]
        val cx = fallback.x.coerceIn(2f, (size.width - tw - 2f).coerceAtLeast(2f))
        val cy = fallback.y.coerceIn(2f, (size.height - th - 2f).coerceAtLeast(2f))
        drawText(textLayoutResult = layout, color = color, topLeft = Offset(cx, cy))
    }
}
