package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.audio.*
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.KeycapBadge
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.consumeDialogCardTaps
import com.example.utils.MacroReportExporter
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal val PHONOLOGY_PALETTE = listOf(
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

    var activeSheetSpeaker by remember { mutableStateOf(speaker) }
    var activeSheetWords by remember { mutableStateOf(words) }
    LaunchedEffect(speaker?.id, words) {
        if (activeSheetSpeaker == null || speaker?.id == activeSheetSpeaker?.id) {
            activeSheetSpeaker = speaker
            activeSheetWords = words
        }
    }

    val recordedWords = remember(activeSheetWords) {
        activeSheetWords.filter { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
    }

    // Top-level mode: false = Active Speaker Phonology, true = Multi-Speaker Comparison Workbench
    var isMultiSpeakerCompareMode by remember { mutableStateOf(false) }
    val vowelOverrideRevision by VowelGlideOverrideStore.overrideRevision.collectAsState()

    var singleSpeakerProgress by remember { mutableStateOf<CorpusAnalysisProgress?>(null) }
    var multiSpeakerProgress by remember { mutableStateOf<CorpusAnalysisProgress?>(null) }

    val speakerReportState by produceState<SpeakerCorpusPhonologyReport?>(
        initialValue = null,
        key1 = effectiveProjectId,
        key2 = activeSheetSpeaker?.id,
        key3 = recordedWords.size * 10000 + vowelOverrideRevision
    ) {
        singleSpeakerProgress = CorpusAnalysisProgress(0, recordedWords.size, 0)
        if (vowelOverrideRevision == 0) delay(55)
        value = withContext(Dispatchers.Default) {
            CorpusPhonologyAnalyzer.buildSpeakerPhonologyReport(
                context = context,
                projectId = effectiveProjectId,
                speaker = activeSheetSpeaker,
                words = activeSheetWords,
                onProgress = { progress -> singleSpeakerProgress = progress },
                isCancelled = { !coroutineContext[kotlinx.coroutines.Job]!!.isActive }
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
        key2 = multiSpeakerPairsState.size * 10000 + vowelOverrideRevision,
        key3 = isMultiSpeakerCompareMode
    ) {
        if (!isMultiSpeakerCompareMode || multiSpeakerPairsState.size < 2) {
            if (multiSpeakerPairsState.size < 2) value = null
        } else {
            val totalRec = multiSpeakerPairsState.sumOf { (_, wList) ->
                wList.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
            }
            multiSpeakerProgress = CorpusAnalysisProgress(0, totalRec, 0)
            value = withContext(Dispatchers.Default) {
                CorpusPhonologyAnalyzer.buildMultiSpeakerComparisonReport(
                    context = context,
                    projectId = effectiveProjectId,
                    speakerWordPairs = multiSpeakerPairsState,
                    onProgress = { progress -> multiSpeakerProgress = progress },
                    isCancelled = { !coroutineContext[kotlinx.coroutines.Job]!!.isActive }
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

    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val multiReport = multiSpeakerReportState
    val recordedSpeakerCount = multiReport?.speakerReports?.size ?: multiSpeakerPairsState.size
    val canExportReport = speakerReportState != null && (
        speakerReportState!!.analyzedCount > 0 || (multiSpeakerReportState?.speakerReports?.isNotEmpty() == true)
    )

    val triggerExportReport: () -> Unit = {
        val safeProj = projectName.ifBlank { "项目" }.replace("\\s+".toRegex(), "_")
        val safeSpk = (activeSheetSpeaker?.name ?: S.common.defaultLabel).replace("\\s+".toRegex(), "_")
        val zipName = if (isMultiSpeakerCompareMode && (multiSpeakerPairsState.size >= 2)) {
            "${safeProj}_多发音人声学对比报告.zip"
        } else {
            "${safeProj}_${safeSpk}_声学格局报告.zip"
        }
        exportMacroZipLauncher.launch(zipName)
    }

    val playForActiveSheetSpeaker: (Word) -> Unit = { targetWord ->
        if (activeSheetSpeaker?.id == speaker?.id || viewModel == null) {
            onPlayWord(targetWord)
        } else {
            val fn = targetWord.recordingFile
            if (!fn.isNullOrEmpty()) {
                val f = File(File(context.getExternalFilesDir(null), "project_$effectiveProjectId"), fn)
                if (f.exists()) {
                    viewModel.playSpecificAudioFile(f, targetWord.id)
                }
            }
        }
    }

    if (isDesktop) {
        StudioModalDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeDialogCardTaps()
                    .testTag("desktop_macro_acoustic_fullscreen")
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideStudio = maxWidth >= 1020.dp
                    val isInlineModeBar = maxWidth >= 1100.dp
                    val isWideMultiSpeaker = maxWidth >= 1120.dp

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Material You Studio Header Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left: Brand Icon Tile + Title + Structured Metadata Badges
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Insights,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = S.acoustics.macroReportTitle,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                MacroMetaBadge(text = "项目 · ${projectName.ifBlank { S.common.defaultLabel }}")
                                                MacroMetaBadge(
                                                    text = "当前发音人 · ${activeSheetSpeaker?.name ?: S.common.defaultLabel}",
                                                    highlight = !isMultiSpeakerCompareMode
                                                )
                                                MacroMetaBadge(text = "已录样本 ${recordedWords.size} 词")
                                            }
                                        }
                                    }

                                    // Center: Segmented Mode Switcher when wide enough & >=2 speakers
                                    if (recordedSpeakerCount >= 2 && isInlineModeBar) {
                                        Spacer(Modifier.width(16.dp))
                                        MacroSegmentedTrack {
                                            MacroSegmentedItem(
                                                selected = !isMultiSpeakerCompareMode,
                                                onClick = { isMultiSpeakerCompareMode = false },
                                                label = S.acoustics.tabCurrentSpeakerPhonology,
                                                leadingIcon = {
                                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                                                },
                                                modifier = Modifier.testTag("macro_tab_single_speaker")
                                            )
                                            MacroSegmentedItem(
                                                selected = isMultiSpeakerCompareMode,
                                                onClick = { isMultiSpeakerCompareMode = true },
                                                label = S.acoustics.tabMultiSpeakerNormalized.withArgs("count" to recordedSpeakerCount),
                                                leadingIcon = {
                                                    Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                modifier = Modifier.testTag("macro_tab_multi_speaker_lobanov")
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(16.dp))

                                    // Right: Crafted Export Button + Close Fullscreen Button with Esc Keycap
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (canExportReport) {
                                            MacroStudioActionButton(
                                                onClick = triggerExportReport,
                                                enabled = !isExportingReport,
                                                isPrimary = true,
                                                modifier = Modifier.testTag("export_macro_report_button")
                                            ) {
                                                if (isExportingReport) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = S.acoustics.exportingMacroReportToast,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = S.acoustics.exportMacroReportBtn,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }

                                        MacroStudioActionButton(
                                            onClick = onDismiss,
                                            isPrimary = false,
                                            modifier = Modifier.testTag("close_macro_report_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = S.acoustics.closeMacroReportDesc,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(
                                                text = "返回工作台",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            KeycapBadge(keyText = "Esc")
                                        }
                                    }
                                }

                                // Secondary Sub-Toolbar for narrower desktop windows or multi-speaker active switcher
                                val showModeRowBelow = recordedSpeakerCount >= 2 && !isInlineModeBar
                                val showSpeakerStrip = !isMultiSpeakerCompareMode && multiSpeakerPairsState.size >= 2
                                if (showModeRowBelow || showSpeakerStrip) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (showModeRowBelow) {
                                            MacroSegmentedTrack {
                                                MacroSegmentedItem(
                                                    selected = !isMultiSpeakerCompareMode,
                                                    onClick = { isMultiSpeakerCompareMode = false },
                                                    label = S.acoustics.tabCurrentSpeakerPhonology,
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    },
                                                    modifier = Modifier.testTag("macro_tab_single_speaker")
                                                )
                                                MacroSegmentedItem(
                                                    selected = isMultiSpeakerCompareMode,
                                                    onClick = { isMultiSpeakerCompareMode = true },
                                                    label = S.acoustics.tabMultiSpeakerNormalized.withArgs("count" to recordedSpeakerCount),
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    },
                                                    modifier = Modifier.testTag("macro_tab_multi_speaker_lobanov")
                                                )
                                            }
                                        }
                                        if (showSpeakerStrip) {
                                            MacroActiveSpeakerSwitcherStrip(
                                                speakerPairs = multiSpeakerPairsState,
                                                selectedSpeakerId = activeSheetSpeaker?.id,
                                                onSelectSpeaker = { newSpk, newWords ->
                                                    activeSheetSpeaker = newSpk
                                                    activeSheetWords = newWords
                                                    viewModel?.selectSpeaker(newSpk)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }

                        // Full-Screen Studio Workspace Body
                        val report = speakerReportState
                        if (report == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                MacroAnalysisProgressIndicator(
                                    title = S.acoustics.computingMacroAnalysis,
                                    progress = singleSpeakerProgress
                                )
                            }
                        } else if (report.analyzedCount == 0 && !isMultiSpeakerCompareMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                    modifier = Modifier.widthIn(max = 520.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                        Text(
                                            text = S.acoustics.macroEmptyDataExplanation,
                                            fontSize = 13.5.sp,
                                            lineHeight = 20.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else if (isMultiSpeakerCompareMode) {
                            if (multiReport == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MacroAnalysisProgressIndicator(
                                        title = S.acoustics.computingLobanovAnalysis,
                                        progress = multiSpeakerProgress
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 1680.dp)
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 18.dp)
                                    ) {
                                        MultiSpeakerComparisonWorkbench(
                                            multiReport = multiReport,
                                            projectId = effectiveProjectId,
                                            viewModel = viewModel,
                                            playingWordId = playingWordId,
                                            isDesktopWide = isWideMultiSpeaker
                                        )
                                    }
                                }
                            }
                        } else if (isWideStudio) {
                            // Two-Pane Studio Workbench: Left = Tone System & Sandhi, Right = Dynamic F1/F2 Vowel Space
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp, vertical = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(0.45f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    EnhancedMacroToneSystemCard(
                                        report = report,
                                        clusterSourceMode = clusterSourceMode,
                                        onClusterSourceModeChange = { clusterSourceMode = it },
                                        playingWordId = playingWordId,
                                        onPlayWord = playForActiveSheetSpeaker,
                                        isDesktopExpanded = true
                                    )
                                    Spacer(Modifier.height(16.dp))
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(0.55f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    EnhancedMacroVowelSpaceCard(
                                        projectId = effectiveProjectId,
                                        report = report,
                                        playingWordId = playingWordId,
                                        onPlayWord = playForActiveSheetSpeaker,
                                        isDesktopExpanded = true
                                    )
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        } else {
                            // Narrower Desktop Window Fallback: Single-Column Full-Screen Scroll
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                EnhancedMacroToneSystemCard(
                                    report = report,
                                    clusterSourceMode = clusterSourceMode,
                                    onClusterSourceModeChange = { clusterSourceMode = it },
                                    playingWordId = playingWordId,
                                    onPlayWord = playForActiveSheetSpeaker,
                                    isDesktopExpanded = true
                                )
                                EnhancedMacroVowelSpaceCard(
                                    projectId = effectiveProjectId,
                                    report = report,
                                    playingWordId = playingWordId,
                                    onPlayWord = playForActiveSheetSpeaker,
                                    isDesktopExpanded = true
                                )
                                Spacer(Modifier.height(18.dp))
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Android Handheld Presentation: Native ModalBottomSheet
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
                            "speaker" to (activeSheetSpeaker?.name ?: S.common.defaultLabel),
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
                    if (canExportReport) {
                        FilledTonalButton(
                            onClick = triggerExportReport,
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
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_macro_report_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = S.acoustics.closeMacroReportDesc, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Multi-speaker comparison mode switcher (shown automatically when >=2 speakers have recordings)
            if (recordedSpeakerCount >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    MacroSegmentedTrack {
                        MacroSegmentedItem(
                            selected = !isMultiSpeakerCompareMode,
                            onClick = { isMultiSpeakerCompareMode = false },
                            label = S.acoustics.tabCurrentSpeakerPhonology,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            modifier = Modifier.testTag("macro_tab_single_speaker")
                        )
                        MacroSegmentedItem(
                            selected = isMultiSpeakerCompareMode,
                            onClick = { isMultiSpeakerCompareMode = true },
                            label = S.acoustics.tabMultiSpeakerNormalized.withArgs("count" to recordedSpeakerCount),
                            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("macro_tab_multi_speaker_lobanov")
                        )
                    }
                }
                if (!isMultiSpeakerCompareMode && multiSpeakerPairsState.size >= 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    MacroActiveSpeakerSwitcherStrip(
                        speakerPairs = multiSpeakerPairsState,
                        selectedSpeakerId = activeSheetSpeaker?.id,
                        onSelectSpeaker = { newSpk, newWords ->
                            activeSheetSpeaker = newSpk
                            activeSheetWords = newWords
                            viewModel?.selectSpeaker(newSpk)
                        },
                        modifier = Modifier.padding(horizontal = 20.dp)
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
                    MacroAnalysisProgressIndicator(
                        title = S.acoustics.computingMacroAnalysis,
                        progress = singleSpeakerProgress
                    )
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
                                MacroAnalysisProgressIndicator(
                                    title = S.acoustics.computingLobanovAnalysis,
                                    progress = multiSpeakerProgress
                                )
                            }
                        } else {
                            MultiSpeakerComparisonWorkbench(
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
                            onPlayWord = playForActiveSheetSpeaker
                        )

                        // 2. High-Density Dynamic Vowel Space & Diphthong Trajectory Card
                        EnhancedMacroVowelSpaceCard(
                            projectId = effectiveProjectId,
                            report = report,
                            playingWordId = playingWordId,
                            onPlayWord = playForActiveSheetSpeaker
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MacroMetaBadge(
    text: String,
    highlight: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (highlight) {
            lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer, 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * Tactile Material You studio button with zero gray hover flash (`indication = null` + `lerp` animated colors)
 * and zero Skia shadow ring artifacts.
 */
@Composable
internal fun MacroStudioActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = if (isDesktop && enabled) interactionSource.collectIsHoveredAsState().value else false
    val isPressed by interactionSource.collectIsPressedAsState()
    val cs = MaterialTheme.colorScheme

    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> lerp(cs.surface, cs.surfaceContainerHigh, 0.55f)
            isPrimary -> if (isHovered) lerp(cs.primary, cs.onPrimary, 0.10f) else cs.primary
            else -> if (isHovered) lerp(cs.surfaceContainerHigh, cs.primaryContainer, 0.42f) else cs.surfaceContainerHigh
        },
        animationSpec = tween(140),
        label = "MacroStudioActionBtnBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.45f)
            isPrimary -> cs.onPrimary
            isHovered -> cs.onPrimaryContainer
            else -> cs.onSurface
        },
        animationSpec = tween(140),
        label = "MacroStudioActionBtnFg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> cs.outlineVariant.copy(alpha = 0.25f)
            isPrimary -> cs.primary
            isHovered -> cs.primary.copy(alpha = 0.45f)
            else -> cs.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = tween(140),
        label = "MacroStudioActionBtnBorder"
    )
    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1f
            isPressed -> 0.96f
            isHovered && isDesktop -> 1.012f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 640f),
        label = "MacroStudioActionBtnScale"
    )

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(11.dp))
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(interactionSource)
                        .then(if (isDesktop) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * Material You Segmented Track container (flat tonal hierarchy, zero gradients).
 */
@Composable
internal fun MacroSegmentedTrack(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerHighest, 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            content = content
        )
    }
}

@Composable
internal fun MacroSegmentedItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = if (isDesktop) interactionSource.collectIsHoveredAsState().value else false
    val isPressed by interactionSource.collectIsPressedAsState()
    val cs = MaterialTheme.colorScheme

    val trackBg = lerp(cs.surface, cs.surfaceContainerHighest, 0.72f)
    val itemBg by animateColorAsState(
        targetValue = when {
            selected -> cs.primary
            isHovered -> lerp(trackBg, cs.primaryContainer, 0.55f)
            else -> trackBg.copy(alpha = 0f)
        },
        animationSpec = tween(140),
        label = "MacroSegItemBg"
    )
    val fgColor by animateColorAsState(
        targetValue = when {
            selected -> cs.onPrimary
            isHovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = tween(140),
        label = "MacroSegItemFg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 650f),
        label = "MacroSegItemScale"
    )

    val corner = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) 28.dp else 32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(corner)
            .background(itemBg, corner)
            .hoverable(interactionSource)
            .then(if (isDesktop) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 4.5.dp else 6.dp
            )
    ) {
        CompositionLocalProvider(LocalContentColor provides fgColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                }
                Text(
                    text = label,
                    fontSize = if (compact) 11.5.sp else 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = fgColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Clean Material You tonal surface container for manual vowel-glide override bars across
 * macro, multi-speaker, and single-word acoustic screens (replaces washed-out mint tint).
 */
@Composable
internal fun VowelOverrideBarSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerHigh, 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            content()
        }
    }
}

@Composable
private fun PhoneticGlideGlyph(
    glideType: VowelGlideType?,
    tint: Color
) {
    if (glideType == null) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        return
    }
    Canvas(modifier = Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 1.45.dp.toPx()
        when (glideType) {
            VowelGlideType.MONOPHTHONG -> {
                val c = Offset(w * 0.5f, h * 0.5f)
                drawCircle(color = tint.copy(alpha = 0.35f), radius = w * 0.38f, center = c, style = Stroke(width = 1.1.dp.toPx()))
                drawCircle(color = tint, radius = w * 0.20f, center = c)
            }
            VowelGlideType.DIPHTHONG -> {
                val start = Offset(w * 0.18f, h * 0.76f)
                val end = Offset(w * 0.84f, h * 0.22f)
                drawCircle(color = tint, radius = 1.7.dp.toPx(), center = start)
                drawLine(color = tint, start = start, end = end, strokeWidth = strokePx, cap = StrokeCap.Round)
                drawLine(color = tint, start = end, end = Offset(w * 0.54f, h * 0.22f), strokeWidth = strokePx, cap = StrokeCap.Round)
                drawLine(color = tint, start = end, end = Offset(w * 0.84f, h * 0.52f), strokeWidth = strokePx, cap = StrokeCap.Round)
            }
            VowelGlideType.TRIPHTHONG -> {
                val p1 = Offset(w * 0.12f, h * 0.68f)
                val p2 = Offset(w * 0.48f, h * 0.22f)
                val p3 = Offset(w * 0.88f, h * 0.62f)
                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                }
                drawPath(path = path, color = tint, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(color = tint, radius = 1.5.dp.toPx(), center = p2)
                drawLine(color = tint, start = p3, end = Offset(w * 0.64f, h * 0.62f), strokeWidth = strokePx, cap = StrokeCap.Round)
                drawLine(color = tint, start = p3, end = Offset(w * 0.88f, h * 0.38f), strokeWidth = strokePx, cap = StrokeCap.Round)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VowelTypeManualOverrideRow(
    titleLabel: String,
    currentOverride: VowelGlideType?,
    onSelectOverride: (VowelGlideType?) -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanTitle = titleLabel.trimEnd(':', '：')
    val options: List<Pair<VowelGlideType?, String>> = listOf(
        null to "自动识别",
        VowelGlideType.MONOPHTHONG to "单韵母",
        VowelGlideType.DIPHTHONG to "复韵母",
        VowelGlideType.TRIPHTHONG to "三合元音"
    )
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer, 0.55f),
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text = cleanTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (currentOverride != null) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.tertiaryContainer, 0.65f)
                ) {
                    Text(
                        text = "已人工锁定",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .horizontalScroll(rememberScrollState())
        ) {
            MacroSegmentedTrack {
                options.forEach { (typeVal, label) ->
                    val selected = currentOverride == typeVal
                    MacroSegmentedItem(
                        selected = selected,
                        onClick = { onSelectOverride(typeVal) },
                        label = label,
                        compact = true,
                        leadingIcon = {
                            PhoneticGlideGlyph(
                                glideType = typeVal,
                                tint = LocalContentColor.current
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroAnalysisProgressIndicator(
    title: String,
    progress: CorpusAnalysisProgress?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .widthIn(max = 360.dp)
            .padding(horizontal = 24.dp)
    ) {
        val total = progress?.total ?: 0
        val completed = (progress?.completed ?: 0).coerceIn(0, total.coerceAtLeast(1))
        val cacheHits = (progress?.cacheHits ?: 0).coerceAtMost(completed)
        if (total > 0) {
            val targetFraction = (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(160),
                label = "macroProgressFraction"
            )
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
            )
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            val detailText = if (cacheHits > 0) {
                "已提取 $completed / $total 词 · 命中缓存 $cacheHits"
            } else {
                "已提取 $completed / $total 词（多核并行加速中）"
            }
            Text(
                text = detailText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            CircularProgressIndicator()
            Text(
                text = title,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

