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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.audio.*
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.StudioFilterChip
import com.example.utils.MacroReportExporter
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.*

private val PHONOLOGY_PALETTE = listOf(
    Color(0xFF1565C0), // Indigo Blue
    Color(0xFFD84315), // Deep Coral
    Color(0xFF2E7D32), // Emerald Green
    Color(0xFF8E24AA), // Royal Violet
    Color(0xFFEF6C00), // Amber Orange
    Color(0xFF00838F), // Cyan Teal
    Color(0xFFC2185B), // Crimson Rose
    Color(0xFF4E342E)  // Warm Umber
)

/**
 * Upgraded Project-Level Macro Acoustic & Phonological Summary Sheet (Advanced Mode):
 * 1. Corpus-Level Tone System Discovery (eliminating one-word-one-tone fragmentation,
 *    with +/-1 sigma ribbons, Checked/Entering short tones, and 2-sigma outlier inspection).
 * 2. High-Density Dynamic Vowel Space (1-sigma confidence ellipses, smooth curved diphthong/triphthong
 *    directed arrows, 4-way glide filtering, pinch/button zoom + LOD label thinning, and tap-to-play).
 * 3. Multi-Speaker Lobanov Z-score Vocal-Tract Normalized Vowel Space Comparison.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectAcousticSummarySheet(
    projectId: Long = -1L,
    projectName: String,
    speaker: Speaker?,
    words: List<Word>,
    viewModel: RecordingViewModel? = null,
    playingWordId: Long?,
    onPlayWord: (Word) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveProjectId = remember(projectId, words) {
        if (projectId > 0L) projectId else (words.firstOrNull()?.projectId ?: 1L)
    }
    val recordedWords = remember(words) {
        words.filter { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
    }

    // Top-level mode: false = Active Speaker Phonology, true = Multi-Speaker Lobanov Comparison
    var isMultiSpeakerCompareMode by remember { mutableStateOf(false) }

    val speakerReportState by produceState<SpeakerCorpusPhonologyReport?>(
        initialValue = null,
        key1 = effectiveProjectId,
        key2 = speaker?.id,
        key3 = recordedWords.size
    ) {
        delay(65)
        value = withContext(Dispatchers.Default) {
            CorpusPhonologyAnalyzer.buildSpeakerPhonologyReport(
                context = context,
                projectId = effectiveProjectId,
                speaker = speaker,
                words = words
            )
        }
    }

    val multiSpeakerPairsState by produceState<List<Pair<Speaker, List<Word>>>>(
        initialValue = emptyList(),
        key1 = effectiveProjectId,
        key2 = viewModel,
        key3 = recordedWords.size
    ) {
        if (viewModel == null) {
            value = emptyList()
        } else {
            delay(90)
            value = withContext(Dispatchers.IO) {
                viewModel.getProjectMultiSpeakerWordPairs(effectiveProjectId)
                    .filter { (_, wList) -> wList.any { it.isRecorded && !it.recordingFile.isNullOrEmpty() } }
            }
        }
    }

    val multiSpeakerReportState by produceState<MultiSpeakerVowelComparisonReport?>(
        initialValue = null,
        key1 = effectiveProjectId,
        key2 = multiSpeakerPairsState.size,
        key3 = isMultiSpeakerCompareMode
    ) {
        if (!isMultiSpeakerCompareMode || multiSpeakerPairsState.size < 2) {
            if (multiSpeakerPairsState.size < 2) value = null
        } else {
            value = withContext(Dispatchers.Default) {
                CorpusPhonologyAnalyzer.buildMultiSpeakerComparisonReport(
                    context = context,
                    projectId = effectiveProjectId,
                    speakerWordPairs = multiSpeakerPairsState
                )
            }
        }
    }

    val contentScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    var isExportingReport by remember { mutableStateOf(false) }
    var clusterSourceMode by remember(speakerReportState?.hasCustomWordGroups) {
        mutableStateOf(0)
    }

    val exportMacroZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val activeReport = speakerReportState
        if (uri != null && activeReport != null && !isExportingReport) {
            isExportingReport = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val effectiveMultiReport = multiSpeakerReportState ?: if (multiSpeakerPairsState.size >= 2) {
                            CorpusPhonologyAnalyzer.buildMultiSpeakerComparisonReport(
                                context = context,
                                projectId = effectiveProjectId,
                                speakerWordPairs = multiSpeakerPairsState
                            )
                        } else {
                            null
                        }
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            MacroReportExporter.exportReportZip(
                                outputStream = os,
                                projectName = projectName,
                                report = activeReport,
                                useMergedToneClusters = clusterSourceMode == 1,
                                multiSpeakerReport = effectiveMultiReport,
                                textMeasurer = textMeasurer
                            )
                        } ?: false
                    } catch (_: Exception) {
                        false
                    }
                }
                isExportingReport = false
                Toast.makeText(
                    context,
                    if (ok) S.acoustics.exportMacroReportSuccessToast else S.acoustics.exportMacroReportFailedToast,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = S.acoustics.macroReportTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = S.acoustics.macroReportSubtitle.withArgs(
                            "project" to projectName,
                            "speaker" to (speaker?.name ?: S.common.defaultLabel),
                            "count" to recordedWords.size
                        ),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val canExportReport = speakerReportState != null && (
                        speakerReportState!!.analyzedCount > 0 || (multiSpeakerReportState?.speakerReports?.isNotEmpty() == true)
                    )
                    if (canExportReport) {
                        FilledTonalButton(
                            onClick = {
                                val safeProj = projectName.ifBlank { "项目" }.replace("\\s+".toRegex(), "_")
                                val safeSpk = (speaker?.name ?: S.common.defaultLabel).replace("\\s+".toRegex(), "_")
                                exportMacroZipLauncher.launch("${safeProj}_${safeSpk}_声学格局报告.zip")
                            },
                            enabled = !isExportingReport,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("export_macro_report_button")
                        ) {
                            if (isExportingReport) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(5.dp))
                                Text(S.acoustics.exportingMacroReportToast, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(S.acoustics.exportMacroReportBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) onDismiss()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = S.acoustics.closeMacroReportDesc, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Multi-speaker comparison mode switcher (shown automatically when >=2 speakers have recordings)
            val multiReport = multiSpeakerReportState
            val recordedSpeakerCount = multiReport?.speakerReports?.size ?: multiSpeakerPairsState.size
            if (recordedSpeakerCount >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioFilterChip(
                        selected = !isMultiSpeakerCompareMode,
                        onClick = { isMultiSpeakerCompareMode = false },
                        labelText = S.acoustics.tabCurrentSpeakerPhonology,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        modifier = Modifier.testTag("macro_tab_single_speaker")
                    )
                    StudioFilterChip(
                        selected = isMultiSpeakerCompareMode,
                        onClick = { isMultiSpeakerCompareMode = true },
                        labelText = S.acoustics.tabMultiSpeakerNormalized.withArgs("count" to recordedSpeakerCount),
                        leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("macro_tab_multi_speaker_lobanov")
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp, start = 20.dp, end = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            val report = speakerReportState
            if (report == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = S.acoustics.computingMacroAnalysis,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else if (report.analyzedCount == 0 && !isMultiSpeakerCompareMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = S.acoustics.macroEmptyDataExplanation,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .nestedScroll(contentScrollLock)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (isMultiSpeakerCompareMode) {
                        if (multiReport == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = S.acoustics.computingLobanovAnalysis,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        } else {
                            MultiSpeakerLobanovVowelComparisonCard(
                                multiReport = multiReport,
                                projectId = effectiveProjectId,
                                viewModel = viewModel,
                                playingWordId = playingWordId
                            )
                        }
                    } else {
                        // 1. Corpus-Level Tone System & Ribbon Card
                        EnhancedMacroToneSystemCard(
                            report = report,
                            clusterSourceMode = clusterSourceMode,
                            onClusterSourceModeChange = { clusterSourceMode = it },
                            playingWordId = playingWordId,
                            onPlayWord = onPlayWord
                        )

                        // 2. High-Density Dynamic Vowel Space & Diphthong Trajectory Card
                        EnhancedMacroVowelSpaceCard(
                            report = report,
                            playingWordId = playingWordId,
                            onPlayWord = onPlayWord
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnhancedMacroToneSystemCard(
    report: SpeakerCorpusPhonologyReport,
    clusterSourceMode: Int,
    onClusterSourceModeChange: (Int) -> Unit,
    playingWordId: Long?,
    onPlayWord: (Word) -> Unit
) {
    var showStdRibbon by remember { mutableStateOf(true) }
    var onlyShowOutliers by remember { mutableStateOf(false) }
    var expandedClusterId by remember { mutableStateOf<String?>(null) }
    var highlightedClusterId by remember { mutableStateOf<String?>(null) }

    val activeClusters = if (clusterSourceMode == 1 && report.hasCustomWordGroups && report.mergedToneClusters.isNotEmpty()) {
        report.mergedToneClusters
    } else {
        report.pureAcousticToneClusters
    }

    val totalOutliers = remember(activeClusters) { activeClusters.sumOf { it.outlierCount } }
    val checkedCatCount = remember(activeClusters) { activeClusters.count { it.isCheckedCategory } }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("macro_tone_system_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = S.acoustics.toneClusteringTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val pitchSpanText = report.calibratedPitchRangeHz?.let {
                        S.acoustics.personalRobustRangeFormat.withArgs(
                            "min" to String.format(Locale.US, "%.0f", it.first),
                            "max" to String.format(Locale.US, "%.0f", it.second)
                        )
                    } ?: S.acoustics.defaultRangeEstimateTag
                    Text(
                        text = buildString {
                            append(S.acoustics.clusterCountSummary.withArgs("count" to activeClusters.size))
                            if (checkedCatCount > 0) append(S.acoustics.checkedClusterAppend.withArgs("count" to checkedCatCount))
                            append(" · $pitchSpanText")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioFilterChip(
                    selected = clusterSourceMode == 0,
                    onClick = {
                        onClusterSourceModeChange(0)
                        highlightedClusterId = null
                    },
                    labelText = S.acoustics.chipPureAcousticClusters.withArgs("count" to report.pureAcousticToneClusters.size),
                    modifier = Modifier.testTag("tone_mode_acoustic_cluster")
                )
                if (report.hasCustomWordGroups && report.mergedToneClusters.isNotEmpty()) {
                    StudioFilterChip(
                        selected = clusterSourceMode == 1,
                        onClick = {
                            onClusterSourceModeChange(1)
                            highlightedClusterId = null
                        },
                        labelText = S.acoustics.chipWordlistGroups.withArgs("count" to report.mergedToneClusters.size),
                        modifier = Modifier.testTag("tone_mode_word_group")
                    )
                }
                StudioFilterChip(
                    selected = showStdRibbon,
                    onClick = { showStdRibbon = !showStdRibbon },
                    labelText = S.acoustics.chipSigmaBand
                )
                if (totalOutliers > 0) {
                    StudioFilterChip(
                        selected = onlyShowOutliers,
                        onClick = { onlyShowOutliers = !onlyShowOutliers },
                        labelText = S.acoustics.chipOutlierHint.withArgs("count" to totalOutliers),
                        isErrorVariant = true
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(46.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(S.archive.scale5High, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale4HalfHigh, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale3Mid, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale2HalfLow, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale1Low, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val sectionsY = h / 4f
                        for (i in 0..4) {
                            val cy = i * sectionsY
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, cy),
                                end = Offset(w, cy),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f)
                            )
                        }

                        fun normY(p: Float): Float = ((1f - p) * h * 0.86f + h * 0.07f).coerceIn(2f, h - 2f)

                        activeClusters.forEachIndexed { idx, cat ->
                            val baseCol = PHONOLOGY_PALETTE[idx % PHONOLOGY_PALETTE.size]
                            val isDimmed = highlightedClusterId != null && highlightedClusterId != cat.clusterId
                            val strokeAlpha = if (isDimmed) 0.16f else 1.0f
                            val ribbonAlpha = if (isDimmed) 0.03f else 0.14f

                            val pts = cat.meanContour
                            if (pts.size >= 2) {
                                // Checked / Entering tones occupy proportional short horizontal span (e.g. 45% width)
                                val spanW = w * cat.relativeDurationRatio.coerceIn(0.36f, 1.0f)

                                if (showStdRibbon && cat.upperBand1Sigma.size == pts.size && cat.lowerBand1Sigma.size == pts.size && cat.sampleCount >= 2) {
                                    val ribPath = Path()
                                    for (i in pts.indices) {
                                        val px = (i.toFloat() / (pts.size - 1)) * spanW
                                        val py = normY(cat.upperBand1Sigma[i])
                                        if (i == 0) ribPath.moveTo(px, py) else ribPath.lineTo(px, py)
                                    }
                                    for (i in pts.indices.reversed()) {
                                        val px = (i.toFloat() / (pts.size - 1)) * spanW
                                        val py = normY(cat.lowerBand1Sigma[i])
                                        ribPath.lineTo(px, py)
                                    }
                                    ribPath.close()
                                    drawPath(path = ribPath, color = baseCol.copy(alpha = ribbonAlpha), style = Fill)
                                }

                                val linePts = pts.mapIndexed { i, p ->
                                    Offset((i.toFloat() / (pts.size - 1)) * spanW, normY(p))
                                }
                                val curvePath = buildSmoothVowelCurvePath(linePts)
                                drawPath(
                                    path = curvePath,
                                    color = baseCol.copy(alpha = strokeAlpha),
                                    style = Stroke(
                                        width = if (highlightedClusterId == cat.clusterId) 3.8.dp.toPx() else 2.9.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )

                                // Draw end marker: glottal vertical stop bar for Checked Tones vs round nodes for Smooth Tones
                                if (cat.isCheckedCategory) {
                                    val endPt = linePts.last()
                                    drawLine(
                                        color = baseCol.copy(alpha = strokeAlpha),
                                        start = Offset(endPt.x, endPt.y - 6.dp.toPx()),
                                        end = Offset(endPt.x, endPt.y + 6.dp.toPx()),
                                        strokeWidth = 2.6.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(
                                        color = baseCol.copy(alpha = strokeAlpha),
                                        radius = 3.8.dp.toPx(),
                                        center = linePts.first()
                                    )
                                } else {
                                    linePts.forEach { pt ->
                                        drawCircle(
                                            color = baseCol.copy(alpha = strokeAlpha),
                                            radius = 3.dp.toPx(),
                                            center = pt
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = S.acoustics.toneClusterInteractiveHint,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(10.dp))

            // Expandable Tone Cluster Cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeClusters.forEachIndexed { idx, cat ->
                    val visibleMembers = if (onlyShowOutliers) {
                        cat.members.filter { it.isOutlier }
                    } else {
                        cat.members
                    }
                    if (onlyShowOutliers && visibleMembers.isEmpty()) return@forEachIndexed

                    val col = PHONOLOGY_PALETTE[idx % PHONOLOGY_PALETTE.size]
                    val isExpanded = expandedClusterId == cat.clusterId || onlyShowOutliers
                    val isHighlighted = highlightedClusterId == cat.clusterId

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isHighlighted) col.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isHighlighted) col.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                expandedClusterId = if (expandedClusterId == cat.clusterId) null else cat.clusterId
                                highlightedClusterId = if (highlightedClusterId == cat.clusterId) null else cat.clusterId
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.size(11.dp).background(col, CircleShape))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = S.acoustics.clusterCardTitleFormat.withArgs(
                                                    "name" to cat.categoryName,
                                                    "chao" to cat.canonicalChao
                                                ),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (cat.isCheckedCategory) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        text = S.acoustics.checkedTagBadge,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (cat.outlierCount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        text = S.acoustics.outlierCountBadge.withArgs("count" to cat.outlierCount),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = S.acoustics.representativeCharsLine.withArgs(
                                                "words" to cat.exampleWords.joinToString("、"),
                                                "count" to cat.sampleCount
                                            ),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.0fHz · %dms", cat.meanF0Hz, cat.meanDurationMs),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        visibleMembers.forEach { m ->
                                            val isPlaying = playingWordId == m.word.id
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = when {
                                                    isPlaying -> MaterialTheme.colorScheme.primaryContainer
                                                    m.isOutlier -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (m.isOutlier) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onPlayWord(m.word) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = S.acoustics.listenWordDesc.withArgs("word" to m.word.text),
                                                        tint = if (m.isOutlier) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = m.word.text,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (m.rawChao == m.canonicalChao) {
                                                            "[${m.canonicalChao}]"
                                                        } else {
                                                            S.acoustics.canonicalVsRawLine.withArgs(
                                                                "canonical" to m.canonicalChao,
                                                                "raw" to m.rawChao
                                                            )
                                                        },
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (m.isOutlier) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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
            }

            // Stratified Polysyllabic Tone Sandhi / Connected Pattern Summary (prevents polluting monosyllabic citation tone clusters)
            if (report.polysyllabicTonePatterns.isNotEmpty() && !onlyShowOutliers) {
                var expandedPolyKey by remember { mutableStateOf<String?>(null) }
                val totalPolyWords = remember(report.polysyllabicTonePatterns) {
                    report.polysyllabicTonePatterns.sumOf { it.sampleCount }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = S.acoustics.polysyllablePatternHeader.withArgs(
                                "words" to totalPolyWords,
                                "patterns" to report.polysyllabicTonePatterns.size
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = S.acoustics.polysyllablePatternExplanation,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.polysyllabicTonePatterns.forEachIndexed { pIdx, pat ->
                        val pCol = PHONOLOGY_PALETTE[(pIdx + 2) % PHONOLOGY_PALETTE.size]
                        val isExp = expandedPolyKey == pat.patternKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExp) pCol.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isExp) pCol.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { expandedPolyKey = if (isExp) null else pat.patternKey }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Mini multi-syllable pitch contour preview
                                        Box(
                                            modifier = Modifier
                                                .width(56.dp)
                                                .height(32.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                        ) {
                                            val dividerCol = MaterialTheme.colorScheme.outlineVariant
                                            val miniBg = MaterialTheme.colorScheme.surfaceContainerLow
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val sylCnt = pat.meanSyllableContours.size.coerceAtLeast(1)
                                                val colW = size.width / sylCnt.toFloat()
                                                pat.meanSyllableContours.forEachIndexed { sIdx, pts ->
                                                    val sx = sIdx * colW
                                                    if (sIdx > 0) {
                                                        drawLine(
                                                            color = dividerCol,
                                                            start = Offset(sx, 0f),
                                                            end = Offset(sx, size.height),
                                                            strokeWidth = 1.dp.toPx(),
                                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
                                                        )
                                                    }
                                                    val isNeut = pat.syllableNeutralFlags.getOrElse(sIdx) { false }
                                                    val isChk = pat.syllableCheckedFlags.getOrElse(sIdx) { false }
                                                    val padX = colW * 0.10f
                                                    val spanW = (colW - padX * 2f) * when {
                                                        isNeut -> 0.48f
                                                        isChk -> 0.60f
                                                        else -> 1f
                                                    }
                                                    if (pts.size >= 2) {
                                                        if (isNeut) {
                                                            val midP = pts[pts.size / 2]
                                                            val cx = sx + padX + spanW * 0.5f
                                                            val cy = ((1f - midP) * size.height * 0.82f + size.height * 0.09f)
                                                            drawCircle(color = miniBg, radius = 3.dp.toPx(), center = Offset(cx, cy))
                                                            drawCircle(
                                                                color = pCol,
                                                                radius = 3.dp.toPx(),
                                                                center = Offset(cx, cy),
                                                                style = Stroke(width = 1.6.dp.toPx())
                                                            )
                                                        } else {
                                                            val path = Path()
                                                            var lastPt = Offset(sx + padX, size.height * 0.5f)
                                                            pts.forEachIndexed { i, v ->
                                                                val px = sx + padX + (i.toFloat() / (pts.size - 1)) * spanW
                                                                val py = ((1f - v) * size.height * 0.82f + size.height * 0.09f)
                                                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                                lastPt = Offset(px, py)
                                                            }
                                                            drawPath(
                                                                path = path,
                                                                color = pCol,
                                                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                                            )
                                                            if (isChk) {
                                                                drawLine(
                                                                    color = pCol,
                                                                    start = Offset(lastPt.x, (lastPt.y - 4.dp.toPx()).coerceAtLeast(1f)),
                                                                    end = Offset(lastPt.x, (lastPt.y + 4.dp.toPx()).coerceAtMost(size.height - 1f)),
                                                                    strokeWidth = 1.8.dp.toPx()
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = S.acoustics.sandhiPatternBadge.withArgs("chao" to pat.patternChao),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = pCol.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = pat.patternLabel,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = pCol,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = S.acoustics.exampleWordsLine.withArgs(
                                                    "words" to pat.exampleWords.joinToString("、"),
                                                    "count" to pat.sampleCount
                                                ),
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isExp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                AnimatedVisibility(visible = isExp) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            pat.members.forEach { m ->
                                                val isPlaying = playingWordId == m.word.id
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onPlayWord(m.word) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                            contentDescription = S.acoustics.listenWordDesc.withArgs("word" to m.word.text),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = m.word.text,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "[${m.patternChao}]",
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnhancedMacroVowelSpaceCard(
    report: SpeakerCorpusPhonologyReport,
    playingWordId: Long?,
    onPlayWord: (Word) -> Unit
) {
    val allTokens = report.vowelTokens
    val ellipses = report.vowelEllipses

    // Auto-enable clutter-free Ellipse + Representative Glide view when word count > 18
    var aggregateEllipsesMode by remember(allTokens.size) {
        mutableStateOf(allTokens.size > 18 && ellipses.isNotEmpty())
    }
    var useBarkScale by remember { mutableStateOf(true) }
    var glideFilter by remember { mutableStateOf(0) } // 0 = 全部韵母, 1 = 仅单元音(点), 2 = 仅复元音(动程箭头)
    var soloGroupKey by remember { mutableStateOf<String?>(null) }
    var selectedToken by remember { mutableStateOf<NormalizedVowelToken?>(null) }

    val polysyllabicWordCount = remember(allTokens) {
        allTokens.filter { it.wordSyllableCount > 1 }.map { it.word.id }.distinct().size
    }
    var showPolysyllableLinks by remember(polysyllabicWordCount) {
        mutableStateOf(polysyllabicWordCount in 1..14)
    }

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val monoCount = remember(allTokens) { allTokens.count { it.vowelType == VowelGlideType.MONOPHTHONG } }
    val glideCount = remember(allTokens) { allTokens.count { it.vowelType != VowelGlideType.MONOPHTHONG } }

    val filteredTokens = remember(allTokens, glideFilter) {
        when (glideFilter) {
            1 -> allTokens.filter { it.vowelType == VowelGlideType.MONOPHTHONG }
            2 -> allTokens.filter { it.vowelType != VowelGlideType.MONOPHTHONG }
            else -> allTokens
        }
    }

    // Distinct groups for interactive legend chips
    val groupLegendEntries = remember(allTokens) {
        allTokens.groupBy { it.groupKey }
            .entries
            .map { (k, list) -> Triple(k, list.first().groupLabel, list.size) }
            .sortedByDescending { it.third }
    }
    val groupColorMap = remember(groupLegendEntries) {
        groupLegendEntries.mapIndexed { idx, (k, _, _) ->
            k to PHONOLOGY_PALETTE[idx % PHONOLOGY_PALETTE.size]
        }.toMap()
    }

    val polyGroups = remember(allTokens) {
        allTokens
            .filter { it.wordSyllableCount > 1 }
            .groupBy { it.word.id }
            .mapValues { (_, sylTokens) -> sylTokens.sortedBy { it.syllableIndex } }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelLayoutCache = remember(textMeasurer) { HashMap<String, TextLayoutResult>(128) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("macro_vowel_space_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = S.acoustics.macroVowelSpaceTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = S.acoustics.macroVowelSpaceLongDescription,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudioFilterChip(
                        selected = useBarkScale,
                        onClick = { useBarkScale = true },
                        labelText = "Bark",
                        showCheckmark = false,
                        horizontalPadding = 10.dp
                    )
                    StudioFilterChip(
                        selected = !useBarkScale,
                        onClick = { useBarkScale = false },
                        labelText = "Hz",
                        showCheckmark = false,
                        horizontalPadding = 10.dp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Anti-clutter & Glide Filter Bar
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (ellipses.isNotEmpty()) {
                    StudioFilterChip(
                        selected = aggregateEllipsesMode,
                        onClick = { aggregateEllipsesMode = !aggregateEllipsesMode },
                        labelText = if (aggregateEllipsesMode) S.acoustics.chipSigmaEllipseActive else S.acoustics.chipSigmaEllipse,
                        leadingIcon = { Icon(Icons.Default.BubbleChart, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.testTag("vowel_toggle_ellipse_mode")
                    )
                }
                StudioFilterChip(
                    selected = glideFilter == 0,
                    onClick = { glideFilter = 0 },
                    labelText = S.acoustics.chipAllTokens.withArgs("count" to allTokens.size)
                )
                StudioFilterChip(
                    selected = glideFilter == 1,
                    onClick = { glideFilter = 1 },
                    labelText = S.acoustics.chipMonophthongs.withArgs("count" to monoCount),
                    modifier = Modifier.testTag("vowel_filter_monophthong")
                )
                StudioFilterChip(
                    selected = glideFilter == 2,
                    onClick = { glideFilter = 2 },
                    labelText = S.acoustics.chipDiphthongs.withArgs("count" to glideCount),
                    modifier = Modifier.testTag("vowel_filter_diphthong")
                )
                if (polysyllabicWordCount > 0) {
                    StudioFilterChip(
                        selected = showPolysyllableLinks,
                        onClick = { showPolysyllableLinks = !showPolysyllableLinks },
                        labelText = S.acoustics.chipPolysyllabicLinks.withArgs("count" to polysyllabicWordCount),
                        modifier = Modifier.testTag("vowel_toggle_polysyllable_links")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val boundaryColor = MaterialTheme.colorScheme.outlineVariant
            val textHintColor = MaterialTheme.colorScheme.onSurfaceVariant
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            val polyLinkColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.62f)
            val polyLinkHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)

            // Keep latest interactive screen positions in a plain non-state list so Canvas draw never mutates Compose Snapshot state
            val renderedHitTargets = remember { ArrayList<Pair<Offset, NormalizedVowelToken>>(128) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(292.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(filteredTokens, aggregateEllipsesMode, useBarkScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newZoom = (zoomScale * zoom).coerceIn(1f, 3.6f)
                            zoomScale = newZoom
                            if (newZoom <= 1.02f) {
                                panOffset = Offset.Zero
                            } else {
                                val maxPanX = size.width * (newZoom - 1f) * 0.55f
                                val maxPanY = size.height * (newZoom - 1f) * 0.55f
                                panOffset = Offset(
                                    (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                    (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                )
                            }
                        }
                    }
                    .pointerInput(filteredTokens, aggregateEllipsesMode, zoomScale, panOffset) {
                        detectTapGestures { tapOffset ->
                            val nearest = renderedHitTargets.minByOrNull { (pos, _) ->
                                (pos - tapOffset).getDistance()
                            }
                            if (nearest != null && (nearest.first - tapOffset).getDistance() <= 28.dp.toPx()) {
                                selectedToken = nearest.second
                                onPlayWord(nearest.second.word)
                            } else {
                                selectedToken = null
                            }
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    renderedHitTargets.clear()
                    val padLeft = 24.dp.toPx()
                    val padRight = 38.dp.toPx()
                    val padTop = 24.dp.toPx()
                    val padBottom = 24.dp.toPx()
                    val plotW = (size.width - padLeft - padRight).coerceAtLeast(20f)
                    val plotH = (size.height - padTop - padBottom).coerceAtLeast(20f)

                    val f2Min = 580f
                    val f2Max = 2720f
                    val f1Min = 210f
                    val f1Max = 980f
                    val b2Min = PhoneticScaleUtils.hzToBark(f2Min)
                    val b2Max = PhoneticScaleUtils.hzToBark(f2Max)
                    val b1Min = PhoneticScaleUtils.hzToBark(f1Min)
                    val b1Max = PhoneticScaleUtils.hzToBark(f1Max)

                    val centerPivot = Offset(size.width * 0.5f, size.height * 0.5f)

                    fun baseToZoomed(raw: Offset): Offset {
                        return Offset(
                            centerPivot.x + (raw.x - centerPivot.x) * zoomScale + panOffset.x,
                            centerPivot.y + (raw.y - centerPivot.y) * zoomScale + panOffset.y
                        )
                    }

                    fun mapHz(f1: Float, f2: Float): Offset {
                        val xNorm = if (useBarkScale) {
                            (b2Max - PhoneticScaleUtils.hzToBark(f2)) / (b2Max - b2Min)
                        } else {
                            (f2Max - f2) / (f2Max - f2Min)
                        }
                        val yNorm = if (useBarkScale) {
                            (PhoneticScaleUtils.hzToBark(f1) - b1Min) / (b1Max - b1Min)
                        } else {
                            (f1 - f1Min) / (f1Max - f1Min)
                        }
                        val raw = Offset(
                            padLeft + xNorm.coerceIn(-0.04f, 1.04f) * plotW,
                            padTop + yNorm.coerceIn(-0.04f, 1.04f) * plotH
                        )
                        return baseToZoomed(raw)
                    }

                    // 1. IPA Reference Trapezoid
                    val pI = mapHz(280f, 2360f)
                    val pU = mapHz(310f, 800f)
                    val pO = mapHz(510f, 890f)
                    val pA = mapHz(830f, 1320f)
                    val pAE = mapHz(730f, 1750f)
                    val trapPath = Path().apply {
                        moveTo(pI.x, pI.y)
                        lineTo(pU.x, pU.y)
                        lineTo(pO.x, pO.y)
                        lineTo(pA.x, pA.y)
                        lineTo(pAE.x, pAE.y)
                        close()
                    }
                    drawPath(
                        path = trapPath,
                        color = boundaryColor,
                        style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f), 0f))
                    )

                    val placedBoxes = ArrayList<androidx.compose.ui.geometry.Rect>()

                    // 2. Draw 1-Sigma Confidence Ellipses when enabled (and glideFilter != 2)
                    if (aggregateEllipsesMode && glideFilter != 2) {
                        ellipses.forEach { el ->
                            val col = groupColorMap[el.groupKey] ?: PHONOLOGY_PALETTE[0]
                            val isSolo = soloGroupKey == el.groupKey
                            val isDim = soloGroupKey != null && !isSolo
                            val fillAlpha = if (isDim) 0.03f else 0.14f
                            val strokeAlpha = if (isDim) 0.14f else 0.72f

                            val centerPx = mapHz(el.centerF1Hz, el.centerF2Hz)
                            val rxPx = (el.semiMajorBark / (b2Max - b2Min)) * plotW * zoomScale
                            val ryPx = (el.semiMinorBark / (b1Max - b1Min)) * plotH * zoomScale
                            // Screen X flips F2 direction, so screen angle negates data angle
                            val deg = (-Math.toDegrees(el.angleRadBark.toDouble())).toFloat()

                            withTransform({
                                rotate(degrees = deg, pivot = centerPx)
                            }) {
                                drawOval(
                                    color = col.copy(alpha = fillAlpha),
                                    topLeft = Offset(centerPx.x - rxPx, centerPx.y - ryPx),
                                    size = Size(rxPx * 2f, ryPx * 2f)
                                )
                                drawOval(
                                    color = col.copy(alpha = strokeAlpha),
                                    topLeft = Offset(centerPx.x - rxPx, centerPx.y - ryPx),
                                    size = Size(rxPx * 2f, ryPx * 2f),
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                            }

                            drawCircle(color = col.copy(alpha = strokeAlpha), radius = 4.2.dp.toPx(), center = centerPx)

                            if (!isDim && zoomScale < 1.75f && !isSolo) {
                                val lblText = "${el.representativeWord} (${el.sampleCount})"
                                drawNonOverlappingLabel(
                                    textMeasurer = textMeasurer,
                                    labelLayoutCache = labelLayoutCache,
                                    text = lblText,
                                    anchor = centerPx,
                                    color = col,
                                    placedBoxes = placedBoxes,
                                    forceDraw = true
                                )
                            }
                        }
                    }

                    // 2B. Draw thin dashed inter-syllable transition connectors (╌╌╌▶) for polysyllabic words
                    if (polysyllabicWordCount > 0 && glideFilter == 0 && (showPolysyllableLinks || selectedToken != null)) {
                        polyGroups.forEach { (wId, ordered) ->
                            val isSelectedWord = selectedToken?.word?.id == wId
                            if (!showPolysyllableLinks && !isSelectedWord) return@forEach
                            for (k in 0 until ordered.size - 1) {
                                val cur = ordered[k]
                                val nxt = ordered[k + 1]
                                val fromPt = if (cur.vowelType == VowelGlideType.MONOPHTHONG) {
                                    mapHz(cur.nucleusF1Hz.toFloat(), cur.nucleusF2Hz.toFloat())
                                } else {
                                    mapHz(cur.targetF1Hz.toFloat(), cur.targetF2Hz.toFloat())
                                }
                                val toPt = if (nxt.vowelType == VowelGlideType.MONOPHTHONG) {
                                    mapHz(nxt.nucleusF1Hz.toFloat(), nxt.nucleusF2Hz.toFloat())
                                } else {
                                    mapHz(nxt.onsetF1Hz.toFloat(), nxt.onsetF2Hz.toFloat())
                                }
                                drawInterSyllableDashedLink(
                                    from = fromPt,
                                    to = toPt,
                                    color = if (isSelectedWord) polyLinkHighlightColor else polyLinkColor
                                )
                            }
                        }
                    }

                    // 3. Draw Individual Monophthongs & Diphthong/Triphthong Smooth Arrow Trajectories
                    // In aggregateEllipsesMode (at low zoom & not soloed), monophthongs show subtle dots without text clutter,
                    // while diphthongs show representative trajectories (up to 2 per group at 1x, or all when zoomed/soloed!)
                    val diphthongsShownPerGroup = HashMap<String, Int>()
                    filteredTokens.forEach { tok ->
                        val col = groupColorMap[tok.groupKey] ?: PHONOLOGY_PALETTE[0]
                        val isSolo = soloGroupKey == tok.groupKey
                        val isDim = soloGroupKey != null && !isSolo
                        val isSelected = selectedToken?.word?.id == tok.word.id &&
                            selectedToken?.syllableIndex == tok.syllableIndex
                        val isSameWordSelected = selectedToken?.word?.id == tok.word.id

                        val expandDetails = !aggregateEllipsesMode || zoomScale >= 1.68f || isSolo || isSameWordSelected

                        if (tok.vowelType == VowelGlideType.MONOPHTHONG) {
                            val pt = mapHz(tok.nucleusF1Hz.toFloat(), tok.nucleusF2Hz.toFloat())
                            if (pt.x in -10f..(size.width + 10f) && pt.y in -10f..(size.height + 10f)) {
                                renderedHitTargets.add(pt to tok)
                            }
                            val dotAlpha = when {
                                isSelected -> 1f
                                isDim -> 0.12f
                                aggregateEllipsesMode && !expandDetails -> 0.42f
                                else -> 0.90f
                            }
                            if (isSelected) {
                                drawCircle(color = col.copy(alpha = 0.28f), radius = 11.dp.toPx(), center = pt)
                            }
                            drawCircle(
                                color = col.copy(alpha = dotAlpha),
                                radius = if (expandDetails) 4.0.dp.toPx() else 2.6.dp.toPx(),
                                center = pt
                            )
                            if (expandDetails && !isDim) {
                                drawNonOverlappingLabel(
                                    textMeasurer = textMeasurer,
                                    labelLayoutCache = labelLayoutCache,
                                    text = tok.displayLabel,
                                    anchor = pt,
                                    color = onSurfaceColor,
                                    placedBoxes = placedBoxes,
                                    forceDraw = isSelected
                                )
                            }
                        } else {
                            val grpShown = diphthongsShownPerGroup.getOrDefault(tok.groupKey, 0)
                            val showFullArrow = expandDetails || grpShown < 3 || filteredTokens.size <= 24
                            diphthongsShownPerGroup[tok.groupKey] = grpShown + 1

                            val ptsScreen = if (tok.anchorsHz.size >= 4) {
                                listOf(
                                    mapHz(tok.anchorsHz[0].first.toFloat(), tok.anchorsHz[0].second.toFloat()),
                                    mapHz(tok.anchorsHz[tok.anchorsHz.size / 3].first.toFloat(), tok.anchorsHz[tok.anchorsHz.size / 3].second.toFloat()),
                                    mapHz(tok.anchorsHz[(tok.anchorsHz.size * 2) / 3].first.toFloat(), tok.anchorsHz[(tok.anchorsHz.size * 2) / 3].second.toFloat()),
                                    mapHz(tok.anchorsHz.last().first.toFloat(), tok.anchorsHz.last().second.toFloat())
                                )
                            } else {
                                listOf(
                                    mapHz(tok.onsetF1Hz.toFloat(), tok.onsetF2Hz.toFloat()),
                                    mapHz(tok.nucleusF1Hz.toFloat(), tok.nucleusF2Hz.toFloat()),
                                    mapHz(tok.targetF1Hz.toFloat(), tok.targetF2Hz.toFloat())
                                )
                            }
                            val endPt = ptsScreen.last()
                            val midPt = ptsScreen[ptsScreen.size / 2]
                            renderedHitTargets.add(endPt to tok)
                            renderedHitTargets.add(midPt to tok)

                            if (showFullArrow) {
                                val alpha = when {
                                    isSelected -> 1f
                                    isDim -> 0.12f
                                    else -> 0.86f
                                }
                                val path = buildSmoothVowelCurvePath(ptsScreen)
                                if (isSelected) {
                                    drawPath(
                                        path = path,
                                        color = col.copy(alpha = 0.24f),
                                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = col.copy(alpha = alpha),
                                    style = Stroke(
                                        width = if (isSelected) 3.0.dp.toPx() else 2.2.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                                drawCircle(color = Color.White.copy(alpha = alpha), radius = 3.2.dp.toPx(), center = ptsScreen.first())
                                drawCircle(
                                    color = col.copy(alpha = alpha),
                                    radius = 3.2.dp.toPx(),
                                    center = ptsScreen.first(),
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                                drawDirectedArrowHead(
                                    from = ptsScreen[ptsScreen.size - 2],
                                    to = endPt,
                                    color = col.copy(alpha = alpha),
                                    headLenPx = 8.2.dp.toPx(),
                                    headHalfWidthPx = 4.4.dp.toPx()
                                )
                                if (!isDim) {
                                    drawNonOverlappingLabel(
                                        textMeasurer = textMeasurer,
                                        labelLayoutCache = labelLayoutCache,
                                        text = tok.displayLabel,
                                        anchor = ptsScreen.first(),
                                        color = col,
                                        placedBoxes = placedBoxes,
                                        forceDraw = isSelected
                                    )
                                }
                            }
                        }
                    }
                }

                // Corner Anatomical Orientation Hints
                Text(S.acoustics.cornerFrontHighI, fontSize = 10.sp, color = textHintColor, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                Text(
                    text = if (useBarkScale) S.acoustics.axisLabelBarkPerceptual else S.acoustics.axisLabelF2Physical,
                    fontSize = 9.sp,
                    color = textHintColor.copy(alpha = 0.78f),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
                Text(S.acoustics.cornerBackHighU, fontSize = 10.sp, color = textHintColor, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                Text(S.acoustics.cornerCentralLowA, fontSize = 10.sp, color = textHintColor, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))

                // Accessible Zoom Controls (+ / - / Reset) for both Touch & Desktop Mouse
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val nz = (zoomScale / 1.35f).coerceAtLeast(1f)
                                zoomScale = nz
                                if (nz <= 1.02f) panOffset = Offset.Zero
                            },
                            modifier = Modifier.size(28.dp).testTag("vowel_zoom_out")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = S.acoustics.zoomOutVowelDesc, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = String.format(Locale.US, "%.1fx", zoomScale),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                }
                                .padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { zoomScale = (zoomScale * 1.35f).coerceAtMost(3.5f) },
                            modifier = Modifier.size(28.dp).testTag("vowel_zoom_in")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = S.acoustics.zoomInVowelDesc, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // Floating Selected Token Inspector Bar
            val sel = selectedToken
            AnimatedVisibility(visible = sel != null) {
                if (sel != null) {
                    val isPlaying = playingWordId == sel.word.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                val titleStr = if (sel.wordSyllableCount > 1) {
                                    S.acoustics.selectedSyllableDetailFormat.withArgs(
                                        "word" to sel.word.text,
                                        "index" to sel.syllableIndex + 1,
                                        "syllable" to sel.syllableLabel,
                                        "group" to sel.groupLabel
                                    )
                                } else {
                                    S.acoustics.selectedWordOnlyDetailFormat.withArgs(
                                        "word" to sel.word.text,
                                        "group" to sel.groupLabel
                                    )
                                }
                                Text(
                                    text = titleStr,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = sel.trajectorySummary,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = { onPlayWord(sel.word) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPlaying) S.common.stop else S.common.playback, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Interactive Rime / Vowel Group Solo Chips
            if (groupLegendEntries.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = S.acoustics.tapRimeGroupHint,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupLegendEntries.forEach { (gKey, gLabel, count) ->
                        val col = groupColorMap[gKey] ?: PHONOLOGY_PALETTE[0]
                        val selected = soloGroupKey == gKey
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) col.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) col else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    soloGroupKey = if (soloGroupKey == gKey) null else gKey
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).background(col, CircleShape))
                                Text(
                                    text = "$gLabel ($count)",
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
 * Multi-Speaker Vocal-Tract Normalized (Lobanov Z-score) Vowel Space & Diphthong Comparison Card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSpeakerLobanovVowelComparisonCard(
    multiReport: MultiSpeakerVowelComparisonReport,
    projectId: Long,
    viewModel: RecordingViewModel?,
    playingWordId: Long?
) {
    val context = LocalContext.current
    var useLobanovNorm by remember { mutableStateOf(true) }
    var glideFilter by remember { mutableStateOf(0) } // 0 = 全部, 1 = 单元音椭圆+点, 2 = 复元音箭头
    var soloSpeakerId by remember { mutableStateOf<Long?>(null) }
    var inspectedToken by remember { mutableStateOf<NormalizedVowelToken?>(null) }

    val textMeasurer = rememberTextMeasurer()
    val labelLayoutCache = remember(textMeasurer) { HashMap<String, TextLayoutResult>(128) }

    val speakerColorMap = remember(multiReport.speakerReports) {
        multiReport.speakerReports.mapIndexed { idx, r ->
            r.speakerId to PHONOLOGY_PALETTE[idx % PHONOLOGY_PALETTE.size]
        }.toMap()
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

            Spacer(Modifier.height(10.dp))

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
            }

            Spacer(Modifier.height(12.dp))

            val hitList = remember { ArrayList<Pair<Offset, NormalizedVowelToken>>(128) }
            val textHintColor = MaterialTheme.colorScheme.onSurfaceVariant
            val gridCol = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(useLobanovNorm, glideFilter, soloSpeakerId) {
                        detectTapGestures { tap ->
                            val hit = hitList.minByOrNull { (p, _) -> (p - tap).getDistance() }
                            if (hit != null && (hit.first - tap).getDistance() <= 26.dp.toPx()) {
                                val tok = hit.second
                                inspectedToken = tok
                                val fn = tok.word.recordingFile
                                if (viewModel != null && !fn.isNullOrEmpty()) {
                                    val f = File(File(context.getExternalFilesDir(null), "project_$projectId"), fn)
                                    if (f.exists()) {
                                        val tagId = -(tok.word.id * 1000 + tok.speakerId)
                                        viewModel.playSpecificAudioFile(f, tagId)
                                    }
                                }
                            }
                        }
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

                    val zMin = -2.35f
                    val zMax = 2.35f
                    val b2Min = PhoneticScaleUtils.hzToBark(580f)
                    val b2Max = PhoneticScaleUtils.hzToBark(2720f)
                    val b1Min = PhoneticScaleUtils.hzToBark(210f)
                    val b1Max = PhoneticScaleUtils.hzToBark(980f)

                    fun mapLobanov(z1: Float, z2: Float): Offset {
                        // Left = Front (+Z2), Right = Back (-Z2); Top = High (-Z1), Bottom = Low (+Z1)
                        val xNorm = ((zMax - z2) / (zMax - zMin)).coerceIn(0f, 1f)
                        val yNorm = ((z1 - zMin) / (zMax - zMin)).coerceIn(0f, 1f)
                        return Offset(padH + xNorm * plotW, padV + yNorm * plotH)
                    }

                    fun mapBark(f1: Float, f2: Float): Offset {
                        val xNorm = ((b2Max - PhoneticScaleUtils.hzToBark(f2)) / (b2Max - b2Min)).coerceIn(0f, 1f)
                        val yNorm = ((PhoneticScaleUtils.hzToBark(f1) - b1Min) / (b1Max - b1Min)).coerceIn(0f, 1f)
                        return Offset(padH + xNorm * plotW, padV + yNorm * plotH)
                    }

                    // Zero-center crosshairs in Lobanov space
                    val zeroCenter = Offset(padH + 0.5f * plotW, padV + 0.5f * plotH)
                    drawLine(
                        color = gridCol,
                        start = Offset(padH, zeroCenter.y),
                        end = Offset(padH + plotW, zeroCenter.y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    drawLine(
                        color = gridCol,
                        start = Offset(zeroCenter.x, padV),
                        end = Offset(zeroCenter.x, padV + plotH),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    val placedBoxes = ArrayList<androidx.compose.ui.geometry.Rect>()

                    // 1. Speaker vowel category ellipses
                    if (glideFilter != 2) {
                        multiReport.speakerEllipses.forEach { el ->
                            val col = speakerColorMap[el.speakerId] ?: PHONOLOGY_PALETTE[0]
                            val isDim = soloSpeakerId != null && soloSpeakerId != el.speakerId
                            val fillAlpha = if (isDim) 0.02f else 0.11f
                            val strokeAlpha = if (isDim) 0.12f else 0.62f

                            val centerPx = if (useLobanovNorm) {
                                mapLobanov(el.centerLobanovZ1, el.centerLobanovZ2)
                            } else {
                                mapBark(el.centerF1Hz, el.centerF2Hz)
                            }
                            val rxPx = if (useLobanovNorm) {
                                (el.semiMajorLobanov / (zMax - zMin)) * plotW
                            } else {
                                (el.semiMajorBark / (b2Max - b2Min)) * plotW
                            }
                            val ryPx = if (useLobanovNorm) {
                                (el.semiMinorLobanov / (zMax - zMin)) * plotH
                            } else {
                                (el.semiMinorBark / (b1Max - b1Min)) * plotH
                            }
                            val angRad = if (useLobanovNorm) el.angleRadLobanov else el.angleRadBark
                            val deg = (-Math.toDegrees(angRad.toDouble())).toFloat()

                            withTransform({ rotate(degrees = deg, pivot = centerPx) }) {
                                drawOval(
                                    color = col.copy(alpha = fillAlpha),
                                    topLeft = Offset(centerPx.x - rxPx, centerPx.y - ryPx),
                                    size = Size(rxPx * 2f, ryPx * 2f)
                                )
                                drawOval(
                                    color = col.copy(alpha = strokeAlpha),
                                    topLeft = Offset(centerPx.x - rxPx, centerPx.y - ryPx),
                                    size = Size(rxPx * 2f, ryPx * 2f),
                                    style = Stroke(width = 1.4.dp.toPx())
                                )
                            }
                        }
                    }

                    // 2. Speaker tokens & trajectories
                    multiReport.allNormalizedTokens.forEach { tok ->
                        if (glideFilter == 1 && tok.vowelType != VowelGlideType.MONOPHTHONG) return@forEach
                        if (glideFilter == 2 && tok.vowelType == VowelGlideType.MONOPHTHONG) return@forEach

                        val col = speakerColorMap[tok.speakerId] ?: PHONOLOGY_PALETTE[0]
                        val isDim = soloSpeakerId != null && soloSpeakerId != tok.speakerId
                        val alpha = if (isDim) 0.12f else 0.88f

                        if (tok.vowelType == VowelGlideType.MONOPHTHONG) {
                            val pt = if (useLobanovNorm) {
                                mapLobanov(tok.z1Nucleus, tok.z2Nucleus)
                            } else {
                                mapBark(tok.nucleusF1Hz.toFloat(), tok.nucleusF2Hz.toFloat())
                            }
                            hitList.add(pt to tok)
                            drawCircle(color = col.copy(alpha = alpha), radius = 3.6.dp.toPx(), center = pt)
                            if (!isDim) {
                                drawNonOverlappingLabel(
                                    textMeasurer = textMeasurer,
                                    labelLayoutCache = labelLayoutCache,
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
                                    mapBark(tok.onsetF1Hz.toFloat(), tok.onsetF2Hz.toFloat()),
                                    mapBark(tok.nucleusF1Hz.toFloat(), tok.nucleusF2Hz.toFloat()),
                                    mapBark(tok.targetF1Hz.toFloat(), tok.targetF2Hz.toFloat())
                                )
                            }
                            hitList.add(pts.last() to tok)
                            val path = buildSmoothVowelCurvePath(pts)
                            drawPath(path = path, color = col.copy(alpha = alpha), style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                            drawDirectedArrowHead(
                                from = pts[pts.size - 2],
                                to = pts.last(),
                                color = col.copy(alpha = alpha),
                                headLenPx = 7.8.dp.toPx(),
                                headHalfWidthPx = 4.2.dp.toPx()
                            )
                            if (!isDim) {
                                drawNonOverlappingLabel(
                                    textMeasurer = textMeasurer,
                                    labelLayoutCache = labelLayoutCache,
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
            }

            val insp = inspectedToken
            if (insp != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = S.acoustics.lobanovSelectedPointLine.withArgs(
                        "speaker" to insp.speakerName,
                        "word" to insp.word.text,
                        "summary" to insp.trajectorySummary,
                        "z1" to String.format(Locale.US, "%.2f", insp.z1Nucleus),
                        "z2" to String.format(Locale.US, "%.2f", insp.z2Nucleus)
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))

            // Speaker Filter Legend
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                multiReport.speakerReports.forEach { spkRep ->
                    val col = speakerColorMap[spkRep.speakerId] ?: PHONOLOGY_PALETTE[0]
                    val spkMeta = multiReport.speakersById[spkRep.speakerId]
                    val selected = soloSpeakerId == spkRep.speakerId
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) col.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) col else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                soloSpeakerId = if (soloSpeakerId == spkRep.speakerId) null else spkRep.speakerId
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(9.dp).background(col, CircleShape))
                            Text(
                                text = buildString {
                                    append(spkRep.speakerName)
                                    if (spkMeta != null) {
                                        append(
                                            S.acoustics.speakerChipMetaAge.withArgs(
                                                "gender" to spkMeta.gender,
                                                "age" to spkMeta.age
                                            )
                                        )
                                    }
                                    append(S.acoustics.speakerChipTokenCount.withArgs("count" to spkRep.vowelTokens.size))
                                    if (!spkRep.lobanovValid) append(S.acoustics.speakerChipSmallSampleTag)
                                },
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Collision-aware Canvas text label drawer with Level-of-Detail (LOD) culling:
 * tries 4 candidate offsets around [anchor]; if all 4 collide with already-placed labels and
 * [forceDraw] is false, cleanly skips drawing the text so high-density charts never turn into a blur!
 */
private fun DrawScope.drawNonOverlappingLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    labelLayoutCache: MutableMap<String, TextLayoutResult>,
    text: String,
    anchor: Offset,
    color: Color,
    placedBoxes: MutableList<androidx.compose.ui.geometry.Rect>,
    forceDraw: Boolean
) {
    if (anchor.x !in 0f..size.width || anchor.y !in 0f..size.height) return

    val layout = labelLayoutCache.getOrPut(text) {
        textMeasurer.measure(
            text = text,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
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
        val rect = androidx.compose.ui.geometry.Rect(
            left = cx - margin,
            top = cy - margin,
            right = cx + tw + margin,
            bottom = cy + th + margin
        )
        val collides = placedBoxes.any { it.overlaps(rect) }
        if (!collides) {
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
