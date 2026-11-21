package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.AnimatedDialogVisibility
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioHorizontalScrollStrip
import com.example.ui.components.StudioMotion
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.utils.ExportOptions
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveExplorerScreen(
    viewModel: RecordingViewModel,
    onNavigateBack: () -> Unit,
    onSelectedWordPosition: (Int) -> Unit,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val project by viewModel.currentProject.collectAsState()
    val words by viewModel.currentWords.collectAsState()
    val playingWordId by viewModel.playingWordId.collectAsState()
    val playbackProgressState = viewModel.playbackProgress.collectAsState()
    val playbackProgressProvider = remember(playbackProgressState) { { playbackProgressState.value } }
    val isContinuousPlaying by viewModel.isContinuousPlaying.collectAsState()

    val currentSpeaker by viewModel.currentSpeaker.collectAsState()
    val speakers by viewModel.allSpeakers.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val textGridConfig by viewModel.textGridFormatConfig.collectAsState()

    val context = LocalContext.current
    val showPhoneticWord by viewModel.showPhoneticWord.collectAsState()
    val showGlobalManagement by viewModel.showGlobalManagement.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var debouncedSearchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0: 全部, 1: 已采录, 2: 未采录, 3: 需重录(异常), 4: 多人已录
    var selectedGroupFilter by remember(project?.id) { mutableStateOf<String?>(null) }
    var onlyWithNotesFilter by remember(project?.id) { mutableStateOf(false) }
    var showMacroSummarySheet by remember { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        if (searchText.isEmpty()) {
            debouncedSearchText = ""
        } else {
            delay(120)
            debouncedSearchText = searchText
        }
    }

    LaunchedEffect(isSimpleMode) {
        if (isSimpleMode) {
            showMacroSummarySheet = false
        }
    }

    val projectSummaries by viewModel.projectSummaries.collectAsState()
    val currentProjectSummary = project?.id?.let { projectSummaries[it] }
    val currentRecordedCount = remember(words) { words.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() } }
    val totalProjectRecordings = currentProjectSummary?.totalRecordingsCount ?: currentRecordedCount
    val startedSpeakersCount = currentProjectSummary?.startedSpeakerCount ?: (if (currentRecordedCount > 0) 1 else 0)

    val availableWordGroups = remember(words) {
        val counts = LinkedHashMap<String, Int>()
        for (w in words) {
            val g = w.groupName.trim()
            if (g.isNotEmpty() && g != "默认组" && g != "未分组") {
                counts[g] = (counts[g] ?: 0) + 1
            }
        }
        if (counts.size >= 2) counts.entries.map { it.key to it.value } else emptyList()
    }
    val hasAnyNotes = remember(words) {
        words.any { it.speakerNote.isNotBlank() || it.remark.isNotBlank() }
    }

    LaunchedEffect(availableWordGroups) {
        val cur = selectedGroupFilter
        if (cur != null && availableWordGroups.none { it.first == cur }) {
            selectedGroupFilter = null
        }
    }
    LaunchedEffect(hasAnyNotes) {
        if (!hasAnyNotes && onlyWithNotesFilter) {
            onlyWithNotesFilter = false
        }
    }

    val multiSpeakerWordCounts by produceState<Map<Long, Int>>(
        initialValue = emptyMap(),
        key1 = project?.id,
        key2 = startedSpeakersCount,
        key3 = totalProjectRecordings
    ) {
        val pid = project?.id
        if (pid == null || startedSpeakersCount < 2) {
            value = emptyMap()
        } else {
            value = withContext(Dispatchers.IO) {
                val pairs = viewModel.getProjectMultiSpeakerWordPairs(pid)
                val counts = HashMap<Long, Int>()
                for ((_, wList) in pairs) {
                    for (w in wList) {
                        if (w.isRecorded && !w.recordingFile.isNullOrEmpty()) {
                            counts[w.id] = (counts[w.id] ?: 0) + 1
                        }
                    }
                }
                counts
            }
        }
    }

    val multiComparableWordCount = remember(words, multiSpeakerWordCounts) {
        if (multiSpeakerWordCounts.isEmpty()) 0 else words.count { (multiSpeakerWordCounts[it.id] ?: 0) >= 2 }
    }

    LaunchedEffect(startedSpeakersCount, multiComparableWordCount) {
        if (selectedFilter == 4 && (startedSpeakersCount < 2 || multiComparableWordCount == 0)) {
            selectedFilter = 0
        }
    }

    // Offload filtering to Dispatchers.Default for large 1,000-3,000 word dialect tables
    val filteredWords by produceState(
        initialValue = words,
        key1 = words,
        key2 = debouncedSearchText,
        key3 = Triple(selectedFilter, selectedGroupFilter, onlyWithNotesFilter to multiSpeakerWordCounts)
    ) {
        val query = debouncedSearchText.trim().lowercase()
        val groupFilter = selectedGroupFilter
        val notesOnly = onlyWithNotesFilter
        if (query.isEmpty() && selectedFilter == 0 && groupFilter == null && !notesOnly) {
            value = words
        } else {
            value = withContext(Dispatchers.Default) {
                words.filter { word ->
                    val matchesFilter = when (selectedFilter) {
                        1 -> word.isRecorded
                        2 -> !word.isRecorded
                        3 -> word.isRecorded && word.qualityStatus != null && word.qualityStatus != "正常"
                        4 -> (multiSpeakerWordCounts[word.id] ?: 0) >= 2
                        else -> true
                    }
                    if (!matchesFilter) return@filter false
                    if (groupFilter != null && word.groupName.trim() != groupFilter) return@filter false
                    if (notesOnly && word.speakerNote.isBlank() && word.remark.isBlank()) return@filter false
                    if (query.isEmpty()) return@filter true
                    word.text.lowercase().contains(query) ||
                        word.remark.lowercase().contains(query) ||
                        word.speakerNote.lowercase().contains(query) ||
                        word.groupName.lowercase().contains(query)
                }
            }
        }
    }

    val gridState = rememberLazyGridState()
    val archiveScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            filteredWords.size >= 40 && gridState.firstVisibleItemIndex >= 8
        }
    }
    LaunchedEffect(playingWordId, isContinuousPlaying) {
        if (isContinuousPlaying && playingWordId != null) {
            val targetIdx = filteredWords.indexOfFirst { it.id == playingWordId }
            if (targetIdx >= 0) {
                gridState.animateScrollToItem(targetIdx)
            }
        }
    }

    var showExportPage by remember { mutableStateOf(false) }
    var wordPendingDelete by remember { mutableStateOf<Word?>(null) }
    LaunchedEffect(project?.id) {
        showExportPage = false
    }
    // 0 = 标准压缩包 (.zip), 1 = Praat 工程 (.Collection), 2 = PhonTracer 工程 (.teproj)
    var selectedExportFormat by remember { mutableStateOf(0) }
    var simpleExportPraatCollection by remember { mutableStateOf(false) }
    var optPraatCollectionBinary by remember { mutableStateOf(true) }
    var optPraatSummaryTable by remember { mutableStateOf(true) }
    var optAudio by remember { mutableStateOf(true) }
    var optAnalysis by remember { mutableStateOf(true) }
    var optPraatTextGrid by remember { mutableStateOf(true) }
    var optSimplePraatTextGrid by remember { mutableStateOf(false) }
    var optTgAcousticTags by remember { mutableStateOf(false) }
    var optWords by remember { mutableStateOf(true) }
    var optSpeaker by remember { mutableStateOf(true) }
    var optExpr by remember { mutableStateOf(true) }
    var optWordImages by remember { mutableStateOf(true) }
    var optIncludeBackupTakes by remember { mutableStateOf(false) }
    var optExportAllSpeakers by remember(project?.id, startedSpeakersCount >= 2) {
        mutableStateOf(startedSpeakersCount >= 2)
    }
    val exportAppendNoteToFileName by viewModel.exportAppendNoteToFileName.collectAsState()
    val hasWordImages = remember(project, words) {
        project?.enablePicturePrompts == true && words.any { it.imagePath.isNotBlank() }
    }

    fun isExportingPraatCollection(): Boolean {
        return if (isSimpleMode) simpleExportPraatCollection else selectedExportFormat == 1
    }

    fun isExportingTeproj(): Boolean {
        return !isSimpleMode && selectedExportFormat == 2
    }

    fun buildExportOptions(): ExportOptions {
        return if (isSimpleMode) {
            if (simpleExportPraatCollection) {
                ExportOptions(
                    includeAudio = true,
                    includePraatTextGrid = true,
                    includeAcousticTagsInTextGrid = false,
                    includeWordImages = false,
                    exportAllSpeakers = optExportAllSpeakers,
                    exportAsPraatCollection = true,
                    praatCollectionBinary = true,
                    includePraatSummaryTable = false,
                    textGridConfig = textGridConfig
                )
            } else {
                ExportOptions(
                    includeAudio = true,
                    includeAnalysis = true,
                    includeWordList = false,
                    includeSpeakerProfile = false,
                    includeExperimentProfile = false,
                    includePraatTextGrid = optSimplePraatTextGrid,
                    includeAcousticTagsInTextGrid = false,
                    includeWordImages = hasWordImages && optWordImages,
                    exportAllSpeakers = optExportAllSpeakers,
                    exportAsTeproj = false,
                    simpleModeExport = true,
                    textGridConfig = textGridConfig
                )
            }
        } else {
            val usePraatCol = selectedExportFormat == 1
            val useTeproj = selectedExportFormat == 2
            ExportOptions(
                includeAudio = if (useTeproj) true else optAudio,
                includeAnalysis = if (useTeproj || usePraatCol) false else optAnalysis,
                includeWordList = if (useTeproj || usePraatCol) false else optWords,
                includeSpeakerProfile = if (useTeproj || usePraatCol) false else optSpeaker,
                includeExperimentProfile = if (useTeproj || usePraatCol) false else optExpr,
                includePraatTextGrid = optPraatTextGrid,
                includeAcousticTagsInTextGrid = optTgAcousticTags,
                includeWordImages = !usePraatCol && optWordImages,
                exportAllSpeakers = optExportAllSpeakers,
                exportAsTeproj = useTeproj,
                exportAsPraatCollection = usePraatCol,
                praatCollectionBinary = optPraatCollectionBinary,
                includePraatSummaryTable = optPraatSummaryTable,
                appendNoteToFileName = exportAppendNoteToFileName,
                includeBackupTakes = !usePraatCol && !useTeproj && optIncludeBackupTakes,
                textGridConfig = textGridConfig
            )
        }
    }

    val handleExportToUri: (android.net.Uri?) -> Unit = { uri ->
        if (uri != null && project != null) {
            try {
                val os = context.contentResolver.openOutputStream(uri)
                if (os != null) {
                    val options = buildExportOptions()
                    viewModel.exportProjectAsZip(project!!, options, os) { summary ->
                        Toast.makeText(context, summary.message, Toast.LENGTH_LONG).show()
                        if (summary.success && DesktopAdaptiveState.isDesktopPlatform) {
                            try {
                                val openIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                }
                                context.startActivity(openIntent)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, S.common.errorOccurred.withArgs("msg" to e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = handleExportToUri
    )

    val exportTeprojLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = handleExportToUri
    )

    val exportPraatCollectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = handleExportToUri
    )

    fun shareZip() {
        if (project == null) return
        val cacheDir = File(context.cacheDir, "exports")
        cacheDir.mkdirs()
        val safeProjName = project?.name?.replace("\\s+".toRegex(), "_") ?: S.archive.defaultExperimentFilePrefix
        val scopeLabel = if (optExportAllSpeakers) S.archive.allSpeakersFileTag else (currentSpeaker?.name?.replace("\\s+".toRegex(), "_") ?: S.archive.speakerFileFallback)
        val usePraatCol = isExportingPraatCollection()
        val useTeproj = isExportingTeproj()
        val ext = when {
            usePraatCol -> "Collection"
            useTeproj -> "teproj"
            else -> "zip"
        }
        val outFile = File(cacheDir, "${safeProjName}_${scopeLabel}.${ext}")
        val options = buildExportOptions()

        viewModel.exportProjectAsZip(project!!, options, FileOutputStream(outFile)) { summary ->
            if (summary.success) {
                Toast.makeText(context, summary.message, Toast.LENGTH_LONG).show()
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outFile
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = when {
                            usePraatCol && !options.praatCollectionBinary -> "text/plain"
                            usePraatCol || useTeproj -> "application/octet-stream"
                            else -> "application/zip"
                        }
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooserTitle = when {
                        usePraatCol -> S.archive.shareCollectionTitle
                        useTeproj -> S.archive.shareTeprojTitle
                        else -> S.archive.shareZipTitle
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, chooserTitle))
                } catch (e: Exception) {
                    Toast.makeText(context, S.archive.shareErrorToast.withArgs("msg" to e.message), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, summary.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val launchSaveLocal: () -> Unit = {
        val safeProjName = project?.name?.replace("\\s+".toRegex(), "_") ?: S.archive.defaultExperimentFilePrefix
        val scopeLabel = if (optExportAllSpeakers) S.archive.allSpeakersFileTag else (currentSpeaker?.name?.replace("\\s+".toRegex(), "_") ?: S.archive.speakerFileFallback)
        when {
            isExportingPraatCollection() -> exportPraatCollectionLauncher.launch("${safeProjName}_${scopeLabel}.Collection")
            isExportingTeproj() -> exportTeprojLauncher.launch("${safeProjName}_${scopeLabel}.teproj")
            else -> exportZipLauncher.launch("${safeProjName}_${scopeLabel}.zip")
        }
    }

    AnimatedContent(
        targetState = showExportPage && project != null,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(tween(220)) { it / 8 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(180)) { -it / 10 } + fadeOut(tween(140)))
            } else {
                (slideInHorizontally(tween(220)) { -it / 10 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally(tween(180)) { it / 8 } + fadeOut(tween(140)))
            }
        },
        label = "ArchiveExportPageTransition",
        modifier = modifier.fillMaxSize()
    ) { isExportPageOpen ->
        if (isExportPageOpen) {
            ProjectExportScreen(
                projectName = project?.name ?: "",
                currentSpeakerName = currentSpeaker?.name ?: S.common.defaultSpeakerFallback,
                isSimpleMode = isSimpleMode,
                currentRecordedCount = currentRecordedCount,
                totalProjectRecordings = totalProjectRecordings,
                startedSpeakersCount = startedSpeakersCount,
                selectedExportFormat = selectedExportFormat,
                onSelectExportFormat = { selectedExportFormat = it },
                simpleExportPraatCollection = simpleExportPraatCollection,
                onSimpleExportPraatCollectionChange = { simpleExportPraatCollection = it },
                optExportAllSpeakers = optExportAllSpeakers,
                onExportAllSpeakersChange = { optExportAllSpeakers = it },
                optPraatCollectionBinary = optPraatCollectionBinary,
                onPraatCollectionBinaryChange = { optPraatCollectionBinary = it },
                optPraatSummaryTable = optPraatSummaryTable,
                onPraatSummaryTableChange = { optPraatSummaryTable = it },
                optAudio = optAudio,
                onAudioChange = { optAudio = it },
                optPraatTextGrid = optPraatTextGrid,
                onPraatTextGridChange = { optPraatTextGrid = it },
                optSimplePraatTextGrid = optSimplePraatTextGrid,
                onSimplePraatTextGridChange = { optSimplePraatTextGrid = it },
                optTgAcousticTags = optTgAcousticTags,
                onTgAcousticTagsChange = { optTgAcousticTags = it },
                optAnalysis = optAnalysis,
                onAnalysisChange = { optAnalysis = it },
                optWords = optWords,
                onWordsChange = { optWords = it },
                optSpeaker = optSpeaker,
                onSpeakerChange = { optSpeaker = it },
                optExpr = optExpr,
                onExprChange = { optExpr = it },
                onBack = { showExportPage = false },
                onSaveLocal = launchSaveLocal,
                onShareExport = { shareZip() },
                hasWordImages = hasWordImages,
                optWordImages = optWordImages,
                onWordImagesChange = { optWordImages = it },
                optIncludeBackupTakes = optIncludeBackupTakes,
                onIncludeBackupTakesChange = { optIncludeBackupTakes = it },
                textGridConfig = textGridConfig,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompactTopBar = maxWidth < 460.dp
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = S.archive.screenTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                if (!isTablet) {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.common.back)
                                    }
                                }
                            },
                            actions = {
                                if (project != null) {
                                    if (!isSimpleMode) {
                                        if (isCompactTopBar) {
                                            TooltipBox(
                                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                                tooltip = { PlainTooltip { Text(S.archive.macroReportButton) } },
                                                state = rememberTooltipState()
                                            ) {
                                                FilledTonalIconButton(
                                                    onClick = { showMacroSummarySheet = true },
                                                    modifier = Modifier
                                                        .padding(end = 4.dp)
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("open_macro_acoustic_summary_button")
                                                ) {
                                                    Icon(
                                                        Icons.Default.Insights,
                                                        contentDescription = S.archive.macroReportDesc,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            FilledTonalButton(
                                                onClick = { showMacroSummarySheet = true },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .padding(end = 6.dp)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .testTag("open_macro_acoustic_summary_button")
                                            ) {
                                                Icon(Icons.Default.Insights, contentDescription = S.archive.macroReportDesc, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(S.archive.macroReportButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                    if (isCompactTopBar) {
                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                            tooltip = { PlainTooltip { Text(S.archive.exportProjectButton) } },
                                            state = rememberTooltipState()
                                        ) {
                                            FilledIconButton(
                                                onClick = {
                                                    if (isContinuousPlaying) {
                                                        viewModel.stopContinuousPlayback()
                                                    }
                                                    showExportPage = true
                                                },
                                                modifier = Modifier
                                                    .padding(end = 6.dp)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .testTag("export_zip_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Share,
                                                    contentDescription = S.archive.exportProjectDesc,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (isContinuousPlaying) {
                                                    viewModel.stopContinuousPlayback()
                                                }
                                                showExportPage = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .testTag("export_zip_button")
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = S.archive.exportProjectDesc, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(S.archive.exportProjectButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                val filterCounts = remember(words) {
                    var recCount = 0
                    var flagCount = 0
                    for (w in words) {
                        if (w.isRecorded) {
                            recCount++
                            if (w.qualityStatus != null && w.qualityStatus != "正常") {
                                flagCount++
                            }
                        }
                    }
                    Triple(recCount, words.size - recCount, flagCount)
                }
                val playableInFilter = remember(filteredWords) {
                    filteredWords.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
                }
                val currentPlayingWord = remember(playingWordId, words) {
                    playingWordId?.let { pid -> words.firstOrNull { it.id == pid } }
                }
                val projectGroup = project?.speakerGroupName ?: ""
                val sortedSpeakers = remember(speakers, projectGroup, isSimpleMode) {
                    if (!isSimpleMode && projectGroup.isNotBlank()) {
                        speakers.sortedByDescending { it.groupName == projectGroup }
                    } else {
                        speakers
                    }
                }
                val wordIdToIndexMap = remember(words) {
                    val map = HashMap<Long, Int>(words.size * 2)
                    words.forEachIndexed { index, w -> map[w.id] = index }
                    map
                }

                @Composable
                fun renderArchiveGridPane(paneModifier: Modifier) {
                    BoxWithConstraints(modifier = paneModifier) {
                        val useMultiColumnGrid = maxWidth >= 560.dp

                        AnimatedContent(
                            targetState = filteredWords.isEmpty(),
                            transitionSpec = {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                            },
                            label = "ArchiveGridEmptyTransition"
                        ) { isEmpty ->
                            if (isEmpty) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = S.archive.noMatchIconDesc,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = S.archive.emptyFilteredMessage,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyVerticalGrid(
                                        columns = if (useMultiColumnGrid) {
                                            GridCells.Adaptive(minSize = 290.dp)
                                        } else {
                                            GridCells.Fixed(1)
                                        },
                                        state = gridState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 14.dp,
                                            top = 10.dp,
                                            end = 16.dp,
                                            bottom = 24.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(
                                            items = filteredWords,
                                            key = { _, word -> word.id },
                                            contentType = { _, _ -> "archive_word_row" }
                                        ) { _, word ->
                                            ArchiveWordItemRow(
                                                word = word,
                                                isPlaying = word.id == playingWordId,
                                                playbackProgressProvider = playbackProgressProvider,
                                                onPlayToggle = remember(word) { { viewModel.playWordAudio(word) } },
                                                onToggleQualityFlag = remember(word) {
                                                    {
                                                        if (word.isRecorded) {
                                                            val wasNormal = word.qualityStatus == null || word.qualityStatus == "正常"
                                                            viewModel.toggleManualQualityFlag(word)
                                                            Toast.makeText(
                                                                context,
                                                                if (wasNormal) S.archive.markedWordRerecordToast.withArgs("word" to word.text)
                                                                else S.archive.confirmedWordNormalToast.withArgs("word" to word.text),
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                },
                                                onDeleteAudio = remember(word) { { wordPendingDelete = word } },
                                                onRowClicked = remember(word.id, wordIdToIndexMap) {
                                                    {
                                                        val idxInFull = wordIdToIndexMap[word.id] ?: -1
                                                        if (idxInFull != -1) {
                                                            val seq = viewModel.wordSequence.value
                                                            val seqPos = seq.indexOf(idxInFull)
                                                            if (seqPos != -1) {
                                                                onSelectedWordPosition(seqPos)
                                                            }
                                                        }
                                                    }
                                                },
                                                onShowPhoneticDetails = remember(word) { { viewModel.showPhoneticWordDetail(word) } },
                                                showAcousticDetailsButton = !isSimpleMode,
                                                multiSpeakerRecordedCount = multiSpeakerWordCounts[word.id] ?: 0,
                                                startedSpeakersCount = startedSpeakersCount,
                                                modifier = studioAnimateItem()
                                            )
                                        }
                                    }
                                    PlatformVerticalScrollbar(
                                        gridState = gridState,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollToTop,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140)),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 18.dp, bottom = 18.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    archiveScope.launch {
                                        gridState.animateScrollToItem(0)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.testTag("archive_scroll_to_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "回到顶部"
                                )
                            }
                        }
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val useLeftSidebarLayout = maxWidth >= 780.dp
                    val sidebarTargetWidth = if (maxWidth >= 920.dp) 276.dp else 236.dp
                    val animatedSidebarWidth by animateDpAsState(
                        targetValue = sidebarTargetWidth,
                        animationSpec = StudioMotion.mediumInTween(),
                        label = "archiveSidebarWidth"
                    )
                    val (recCount, unrecCount, flagCount) = filterCounts

                    if (useLeftSidebarLayout) {
                        val sidebarScrollState = rememberScrollState()
                        // Wide / Desktop / Tablet: Dedicated Left Control Sidebar + Full-Height Grid
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .width(animatedSidebarWidth)
                                    .fillMaxHeight()
                                    .padding(start = 16.dp, top = 10.dp, bottom = 16.dp)
                                    .testTag("archive_left_sidebar")
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(sidebarScrollState)
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                    // 1. Search Input
                                    OutlinedTextField(
                                        value = searchText,
                                        onValueChange = { searchText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("archive_search_input"),
                                        placeholder = {
                                            Text(
                                                text = if (isSimpleMode) S.archive.searchPlaceholderSimple else S.archive.searchPlaceholderAdv,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = S.common.search, modifier = Modifier.size(18.dp)) },
                                        trailingIcon = {
                                            if (searchText.isNotEmpty()) {
                                                IconButton(onClick = { searchText = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = S.archive.clearSearchDesc, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    // 2. Speaker Selector
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "发音人",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            var showSpeakerDropdown by remember { mutableStateOf(false) }
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .clickable { showSpeakerDropdown = true }
                                                    .testTag("speaker_filter_chip")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Person,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        val spkText = if (currentSpeaker != null) {
                                                            val dialectPart = currentSpeaker!!.dialect.takeIf { it.isNotBlank() }?.let {
                                                                S.archive.speakerDialectDotPrefix.withArgs("dialect" to it)
                                                            } ?: ""
                                                            "${currentSpeaker!!.name}$dialectPart"
                                                        } else {
                                                            S.archive.selectSpeakerFallback
                                                        }
                                                        Text(
                                                            text = spkText,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                    val dropDownRotation by animateFloatAsState(
                                                        targetValue = if (showSpeakerDropdown) 180f else 0f,
                                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                                                        label = "sidebarSpeakerDropDownRotation"
                                                    )
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        contentDescription = S.archive.switchSpeakerDesc,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .graphicsLayer { rotationZ = dropDownRotation },
                                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = showSpeakerDropdown,
                                                onDismissRequest = { showSpeakerDropdown = false },
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                sortedSpeakers.forEach { s ->
                                                    val grpTag = if (!isSimpleMode && s.groupName.isNotBlank()) {
                                                        S.archive.speakerGroupBracketTag.withArgs("group" to s.groupName)
                                                    } else ""
                                                    val itemLabel = if (isSimpleMode && s.dialect.isBlank()) {
                                                        s.name
                                                    } else {
                                                        S.archive.speakerDropdownItemWithDialect.withArgs(
                                                            "name" to s.name,
                                                            "groupTag" to grpTag,
                                                            "dialect" to s.dialect
                                                        )
                                                    }
                                                    DropdownMenuItem(
                                                        text = { Text(itemLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                        onClick = {
                                                            viewModel.selectSpeaker(s)
                                                            showSpeakerDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. Primary Actions: Continuous Playback & Stats/Cleanup
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (playableInFilter > 0 || isContinuousPlaying) {
                                            val sidebarContBtnBg by animateColorAsState(
                                                targetValue = if (isContinuousPlaying) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                },
                                                animationSpec = StudioMotion.shortInTween(),
                                                label = "sidebarContPlayBg"
                                            )
                                            val sidebarContBtnFg by animateColorAsState(
                                                targetValue = if (isContinuousPlaying) {
                                                    MaterialTheme.colorScheme.onErrorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                },
                                                animationSpec = StudioMotion.shortInTween(),
                                                label = "sidebarContPlayFg"
                                            )
                                            FilledTonalButton(
                                                onClick = {
                                                    if (isContinuousPlaying) {
                                                        viewModel.stopContinuousPlayback()
                                                    } else {
                                                        viewModel.startContinuousPlayback(filteredWords)
                                                    }
                                                },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = sidebarContBtnBg,
                                                    contentColor = sidebarContBtnFg
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(38.dp)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .testTag("continuous_playback_button")
                                            ) {
                                                AnimatedContent(
                                                    targetState = isContinuousPlaying,
                                                    transitionSpec = { StudioMotion.subtleFadeScale() },
                                                    label = "sidebarContPlayContent"
                                                ) { playing ->
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (playing) Icons.Default.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            text = if (playing) S.archive.stopContinuousPlay else S.archive.continuousListen,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        var lastPlayingWord by remember { mutableStateOf<Word?>(null) }
                                        if (currentPlayingWord != null) {
                                            lastPlayingWord = currentPlayingWord
                                        }
                                        AnimatedVisibility(
                                            visible = isContinuousPlaying && currentPlayingWord != null,
                                            enter = StudioMotion.verticalPanelEnter,
                                            exit = StudioMotion.verticalPanelExit
                                        ) {
                                            val displayPlayingWord = currentPlayingWord ?: lastPlayingWord
                                            if (displayPlayingWord != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.GraphicEq,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Text(
                                                                text = "正在试听：${displayPlayingWord.text}",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        ActiveWordPlaybackProgressBar(
                                                            playbackProgressProvider = playbackProgressProvider,
                                                            heightDp = 4.dp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (!isSimpleMode) {
                                            OutlinedButton(
                                                onClick = { viewModel.setShowGlobalManagement(true) },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Storage,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = S.archive.statsAndCleanup,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                                    // 4. Status Filters (stacked cleanly in left sidebar)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "筛选状态",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val filterLabels = buildList {
                                            add(S.archive.filterAll.withArgs("sep" to " ", "count" to words.size))
                                            add(S.archive.filterRecorded.withArgs("sep" to " ", "count" to recCount))
                                            add(S.archive.filterUnrecorded.withArgs("sep" to " ", "count" to unrecCount))
                                            add(S.archive.filterFlagged.withArgs("sep" to " ", "count" to flagCount))
                                            if (startedSpeakersCount >= 2 && multiComparableWordCount > 0) {
                                                add("多人已录 (${multiComparableWordCount})")
                                            }
                                        }
                                        @OptIn(ExperimentalLayoutApi::class)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                            verticalArrangement = Arrangement.spacedBy(7.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            filterLabels.forEachIndexed { index, label ->
                                                StudioFilterChip(
                                                    selected = selectedFilter == index,
                                                    onClick = { selectedFilter = index },
                                                    labelText = label,
                                                    horizontalPadding = 10.dp,
                                                    isErrorVariant = index == 3
                                                )
                                            }
                                        }
                                    }

                                    // 5. On-demand Group & Notes Filters
                                    if (availableWordGroups.isNotEmpty() || hasAnyNotes) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "更多筛选",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            @OptIn(ExperimentalLayoutApi::class)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                verticalArrangement = Arrangement.spacedBy(7.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (availableWordGroups.isNotEmpty()) {
                                                    var showGroupMenu by remember { mutableStateOf(false) }
                                                    Box {
                                                        StudioFilterChip(
                                                            selected = selectedGroupFilter != null,
                                                            onClick = { showGroupMenu = true },
                                                            labelText = selectedGroupFilter?.let { "$it ▾" } ?: "全部分组 ▾",
                                                            horizontalPadding = 10.dp,
                                                            modifier = Modifier.testTag("archive_group_filter_chip")
                                                        )
                                                        DropdownMenu(
                                                            expanded = showGroupMenu,
                                                            onDismissRequest = { showGroupMenu = false },
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("全部分组 (${words.size})") },
                                                                onClick = {
                                                                    selectedGroupFilter = null
                                                                    showGroupMenu = false
                                                                }
                                                            )
                                                            availableWordGroups.forEach { (grp, count) ->
                                                                DropdownMenuItem(
                                                                    text = { Text("$grp ($count)") },
                                                                    onClick = {
                                                                        selectedGroupFilter = grp
                                                                        showGroupMenu = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (hasAnyNotes) {
                                                    StudioFilterChip(
                                                        selected = onlyWithNotesFilter,
                                                        onClick = { onlyWithNotesFilter = !onlyWithNotesFilter },
                                                        labelText = "有笔记",
                                                        horizontalPadding = 10.dp,
                                                        modifier = Modifier.testTag("archive_notes_filter_chip")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    }
                                    PlatformVerticalScrollbar(
                                        scrollState = sidebarScrollState,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                    )
                                }
                            }

                            renderArchiveGridPane(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        // Narrow Mobile Layout: Compact Top Card + Vertical Grid
                        Column(modifier = Modifier.fillMaxSize()) {
                            ResponsiveContentContainer(
                                maxWidth = 720.dp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                        draggedElevation = 0.dp,
                                        disabledElevation = 0.dp
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchText,
                                        onValueChange = { searchText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("archive_search_input"),
                                        placeholder = {
                                            Text(
                                                text = if (isSimpleMode) S.archive.searchPlaceholderSimple else S.archive.searchPlaceholderAdv,
                                                fontSize = 14.sp
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = S.common.search, modifier = Modifier.size(20.dp)) },
                                        trailingIcon = {
                                            if (searchText.isNotEmpty()) {
                                                IconButton(onClick = { searchText = "" }) {
                                                    Icon(Icons.Default.Clear, contentDescription = S.archive.clearSearchDesc, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val isCompactToolbar = maxWidth < 380.dp
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                                var showSpeakerDropdown by remember { mutableStateOf(false) }
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { showSpeakerDropdown = true }
                                                        .testTag("speaker_filter_chip")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Person,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        val spkText = if (currentSpeaker != null) {
                                                            if (isCompactToolbar) {
                                                                currentSpeaker!!.name
                                                            } else {
                                                                val dialectPart = currentSpeaker!!.dialect.takeIf { it.isNotBlank() }?.let {
                                                                    S.archive.speakerDialectDotPrefix.withArgs("dialect" to it)
                                                                } ?: ""
                                                                "${currentSpeaker!!.name}$dialectPart"
                                                            }
                                                        } else {
                                                            S.archive.selectSpeakerFallback
                                                        }
                                                        Text(
                                                            text = spkText,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        val dropDownRotation by animateFloatAsState(
                                                            targetValue = if (showSpeakerDropdown) 180f else 0f,
                                                            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                                                            label = "speakerDropDownRotation"
                                                        )
                                                        Icon(
                                                            Icons.Default.ArrowDropDown,
                                                            contentDescription = S.archive.switchSpeakerDesc,
                                                            modifier = Modifier
                                                                .size(18.dp)
                                                                .graphicsLayer { rotationZ = dropDownRotation },
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = showSpeakerDropdown,
                                                    onDismissRequest = { showSpeakerDropdown = false },
                                                    shape = RoundedCornerShape(16.dp)
                                                ) {
                                                    sortedSpeakers.forEach { s ->
                                                        val grpTag = if (!isSimpleMode && s.groupName.isNotBlank()) {
                                                            S.archive.speakerGroupBracketTag.withArgs("group" to s.groupName)
                                                        } else ""
                                                        val itemLabel = if (isSimpleMode && s.dialect.isBlank()) {
                                                            s.name
                                                        } else {
                                                            S.archive.speakerDropdownItemWithDialect.withArgs(
                                                                "name" to s.name,
                                                                "groupTag" to grpTag,
                                                                "dialect" to s.dialect
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text(itemLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                            onClick = {
                                                                viewModel.selectSpeaker(s)
                                                                showSpeakerDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (playableInFilter > 0 || isContinuousPlaying) {
                                                    val compactContBtnBg by animateColorAsState(
                                                        targetValue = if (isContinuousPlaying) {
                                                            MaterialTheme.colorScheme.errorContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        },
                                                        animationSpec = StudioMotion.shortInTween(),
                                                        label = "compactContPlayBg"
                                                    )
                                                    val compactContBtnFg by animateColorAsState(
                                                        targetValue = if (isContinuousPlaying) {
                                                            MaterialTheme.colorScheme.onErrorContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        },
                                                        animationSpec = StudioMotion.shortInTween(),
                                                        label = "compactContPlayFg"
                                                    )
                                                    FilledTonalButton(
                                                        onClick = {
                                                            if (isContinuousPlaying) {
                                                                viewModel.stopContinuousPlayback()
                                                            } else {
                                                                viewModel.startContinuousPlayback(filteredWords)
                                                            }
                                                        },
                                                        colors = ButtonDefaults.filledTonalButtonColors(
                                                            containerColor = compactContBtnBg,
                                                            contentColor = compactContBtnFg
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = if (isCompactToolbar) 8.dp else 10.dp, vertical = 4.dp),
                                                        modifier = Modifier
                                                            .height(34.dp)
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .testTag("continuous_playback_button")
                                                    ) {
                                                        AnimatedContent(
                                                            targetState = isContinuousPlaying,
                                                            transitionSpec = { StudioMotion.subtleFadeScale() },
                                                            label = "compactContPlayContent"
                                                        ) { playing ->
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    imageVector = if (playing) Icons.Default.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(Modifier.width(3.dp))
                                                                Text(
                                                                    text = if (playing) S.archive.stopContinuousPlay else S.archive.continuousListen,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (!isSimpleMode) {
                                                    TextButton(
                                                        onClick = { viewModel.setShowGlobalManagement(true) },
                                                        contentPadding = PaddingValues(horizontal = if (isCompactToolbar) 6.dp else 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = S.archive.statsAndCleanup,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                        val isCompactFilters = maxWidth < 360.dp
                                        val sep = if (isCompactFilters) "" else " "
                                        val filterLabels = buildList {
                                            add(S.archive.filterAll.withArgs("sep" to sep, "count" to words.size))
                                            add(S.archive.filterRecorded.withArgs("sep" to sep, "count" to recCount))
                                            add(S.archive.filterUnrecorded.withArgs("sep" to sep, "count" to unrecCount))
                                            add(S.archive.filterFlagged.withArgs("sep" to sep, "count" to flagCount))
                                            if (startedSpeakersCount >= 2 && multiComparableWordCount > 0) {
                                                add("多人已录${sep}(${multiComparableWordCount})")
                                            }
                                        }
                                        val archiveFilterScroll = rememberScrollState()
                                        StudioHorizontalScrollStrip(
                                            state = archiveFilterScroll,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(archiveFilterScroll),
                                                horizontalArrangement = Arrangement.spacedBy(if (isCompactFilters) 6.dp else 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                filterLabels.forEachIndexed { index, label ->
                                                    StudioFilterChip(
                                                        selected = selectedFilter == index,
                                                        onClick = { selectedFilter = index },
                                                        labelText = label,
                                                        horizontalPadding = if (isCompactFilters) 8.dp else 11.dp,
                                                        isErrorVariant = index == 3
                                                    )
                                                }
                                                if (availableWordGroups.isNotEmpty()) {
                                                    var showGroupMenu by remember { mutableStateOf(false) }
                                                    Box {
                                                        StudioFilterChip(
                                                            selected = selectedGroupFilter != null,
                                                            onClick = { showGroupMenu = true },
                                                            labelText = selectedGroupFilter?.let { "$it ▾" } ?: "全部分组 ▾",
                                                            horizontalPadding = if (isCompactFilters) 8.dp else 11.dp,
                                                            modifier = Modifier.testTag("archive_group_filter_chip")
                                                        )
                                                        DropdownMenu(
                                                            expanded = showGroupMenu,
                                                            onDismissRequest = { showGroupMenu = false },
                                                            shape = RoundedCornerShape(14.dp)
                                                        ) {
                                                            DropdownMenuItem(
                                                                text = { Text("全部分组 (${words.size})") },
                                                                onClick = {
                                                                    selectedGroupFilter = null
                                                                    showGroupMenu = false
                                                                }
                                                            )
                                                            availableWordGroups.forEach { (grp, count) ->
                                                                DropdownMenuItem(
                                                                    text = { Text("$grp ($count)") },
                                                                    onClick = {
                                                                        selectedGroupFilter = grp
                                                                        showGroupMenu = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (hasAnyNotes) {
                                                    StudioFilterChip(
                                                        selected = onlyWithNotesFilter,
                                                        onClick = { onlyWithNotesFilter = !onlyWithNotesFilter },
                                                        labelText = "有笔记",
                                                        horizontalPadding = if (isCompactFilters) 8.dp else 11.dp,
                                                        modifier = Modifier.testTag("archive_notes_filter_chip")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }

                            renderArchiveGridPane(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }
            }
        }
    }

    var cachedMacroProject by remember { mutableStateOf(project) }
    if (project != null) {
        cachedMacroProject = project
    }
    AnimatedDialogVisibility(visible = showMacroSummarySheet && project != null) {
        val macroProj = project ?: cachedMacroProject
        if (macroProj != null) {
            ProjectAcousticSummarySheet(
                projectId = macroProj.id,
                projectName = macroProj.name,
                speaker = currentSpeaker,
                words = words,
                viewModel = viewModel,
                playingWordId = playingWordId,
                onPlayWord = { viewModel.playWordAudio(it) },
                onDismiss = { showMacroSummarySheet = false }
            )
        }
    }

    com.example.ui.components.AnimatedNullableDialogVisibility(value = wordPendingDelete) { targetWord ->
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { wordPendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = S.archive.deleteConfirmTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = S.archive.deleteConfirmBody.withArgs("word" to targetWord.text),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeRecordingOfWord(targetWord)
                        wordPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(S.project.confirmDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { wordPendingDelete = null }) {
                    Text(S.common.cancel)
                }
            }
        )
    }
}

@Composable
fun ArchiveWordItemRow(
    word: Word,
    isPlaying: Boolean,
    playbackProgressProvider: () -> Float,
    onPlayToggle: () -> Unit,
    onToggleQualityFlag: (() -> Unit)? = null,
    onDeleteAudio: () -> Unit,
    onRowClicked: () -> Unit,
    onShowPhoneticDetails: () -> Unit,
    showAcousticDetailsButton: Boolean = true,
    multiSpeakerRecordedCount: Int = 0,
    startedSpeakersCount: Int = 1,
    modifier: Modifier = Modifier
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val isFlagged = word.isRecorded && word.qualityStatus != null && word.qualityStatus != "正常"
    val colorScheme = MaterialTheme.colorScheme

    val cardBgColor by animateColorAsState(
        targetValue = when {
            isPlaying -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.65f)
            isHovered && isFlagged -> lerp(colorScheme.surface, colorScheme.errorContainer, 0.35f)
            isHovered -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.35f)
            else -> colorScheme.surface
        },
        animationSpec = tween(160),
        label = "archiveWordBg_${word.id}"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isPlaying -> colorScheme.primary
            isHovered && isFlagged -> colorScheme.error
            isHovered -> colorScheme.primary
            isFlagged -> lerp(colorScheme.outlineVariant, colorScheme.error, 0.45f)
            else -> colorScheme.outlineVariant
        },
        animationSpec = tween(160),
        label = "archiveWordBorder_${word.id}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isDesktop) {
                    Modifier
                        .hoverable(hoverSrc)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = hoverSrc,
                            indication = null,
                            onClick = onRowClicked
                        )
                } else {
                    Modifier.clickable(onClick = onRowClicked)
                }
            )
            .testTag("archive_row_${word.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(if (isPlaying) 2.dp else if (isHovered) 1.5.dp else 1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val badgeBgColor by animateColorAsState(
                        targetValue = when {
                            isPlaying -> colorScheme.primary
                            word.isRecorded -> colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else -> colorScheme.surfaceVariant
                        },
                        animationSpec = StudioMotion.shortInTween(),
                        label = "archiveBadgeBg_${word.id}"
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                color = badgeBgColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = { StudioMotion.subtleFadeScale() },
                            label = "archiveBadgeContent_${word.id}"
                        ) { playing ->
                            if (playing) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = S.common.play,
                                    tint = colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = (word.originalIndex + 1).toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (word.isRecorded) colorScheme.primary else colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val combinedAnnotation = remember(word.speakerNote, word.remark) {
                            listOf(
                                word.speakerNote.takeIf { it.isNotBlank() },
                                word.remark.takeIf { it.isNotBlank() }?.let {
                                    S.archive.wordRemarkBracketFormat.withArgs("remark" to it)
                                }
                            ).filterNotNull().joinToString(" ")
                        }
                        if (word.text.length <= 4) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = word.text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (combinedAnnotation.isNotEmpty()) {
                                    Text(
                                        text = combinedAnnotation,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = word.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (combinedAnnotation.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = combinedAnnotation,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        if (word.isRecorded) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val status = word.qualityStatus ?: "正常"
                                val isNormal = status == "正常"
                                val displayStatus = S.quality.displayStatus(
                                    canonical = status,
                                    simpleMode = !showAcousticDetailsButton
                                )
                                Text(
                                    text = displayStatus,
                                    fontSize = 11.sp,
                                    color = if (isNormal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = if (onToggleQualityFlag != null) {
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { onToggleQualityFlag() }
                                    } else {
                                        Modifier
                                    }
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = S.archive.durationSecondsShort.withArgs(
                                        "sec" to String.format(java.util.Locale.US, "%.2f", word.durationMs / 1000f)
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (word.groupName.isNotEmpty()) {
                                    Text(
                                        text = S.archive.wordGroupDotPrefix.withArgs("group" to word.groupName),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                if (startedSpeakersCount >= 2 && multiSpeakerRecordedCount >= 2) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                    ) {
                                        Text(
                                            text = "${multiSpeakerRecordedCount}人已录",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = S.archive.unrecordedJumpHint,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (startedSpeakersCount >= 2 && multiSpeakerRecordedCount >= 1) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "他人已录(${multiSpeakerRecordedCount})",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (word.isRecorded) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(38.dp)
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isPlaying,
                                enter = fadeIn(StudioMotion.shortInTween()),
                                exit = fadeOut(StudioMotion.shortOutTween())
                            ) {
                                ActiveWordCircularProgressRing(
                                    playbackProgressProvider = playbackProgressProvider,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            IconButton(
                                onClick = onPlayToggle,
                                modifier = Modifier
                                    .size(38.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("archive_play_${word.id}")
                            ) {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    transitionSpec = { StudioMotion.subtleFadeScale() },
                                    label = "archivePlayIcon_${word.id}"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) S.common.stop else S.common.play,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(if (playing) 18.dp else 22.dp)
                                    )
                                }
                            }
                        }

                        if (onToggleQualityFlag != null) {
                            val isFlaggedBtn = word.qualityStatus != null && word.qualityStatus != "正常"
                            val flagIconTint by animateColorAsState(
                                targetValue = if (isFlaggedBtn) colorScheme.primary else colorScheme.outline,
                                animationSpec = StudioMotion.shortInTween(),
                                label = "archiveFlagTint_${word.id}"
                            )
                            IconButton(
                                onClick = onToggleQualityFlag,
                                modifier = Modifier
                                    .size(38.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("archive_flag_${word.id}")
                            ) {
                                AnimatedContent(
                                    targetState = isFlaggedBtn,
                                    transitionSpec = { StudioMotion.subtleFadeScale() },
                                    label = "archiveFlagIcon_${word.id}"
                                ) { flagged ->
                                    Icon(
                                        imageVector = if (flagged) Icons.Default.CheckCircle else Icons.Default.Flag,
                                        contentDescription = if (flagged) S.archive.confirmAsNormalDesc else S.archive.markAsRerecordDesc,
                                        tint = flagIconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        if (showAcousticDetailsButton) {
                            IconButton(
                                onClick = onShowPhoneticDetails,
                                modifier = Modifier
                                    .size(38.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("archive_detail_${word.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = S.archive.acousticEvalDesc,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDeleteAudio,
                            modifier = Modifier
                                .size(38.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("archive_delete_audio_${word.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = S.archive.clearAudioDesc,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = S.archive.tapToRecordDesc,
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isPlaying,
                enter = StudioMotion.verticalPanelEnter,
                exit = StudioMotion.verticalPanelExit
            ) {
                ActiveWordPlaybackProgressBar(playbackProgressProvider = playbackProgressProvider)
            }
        }
    }
}

@Composable
private fun ActiveWordPlaybackProgressBar(
    playbackProgressProvider: () -> Float,
    heightDp: androidx.compose.ui.unit.Dp = 4.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = playbackProgressProvider().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 55, easing = LinearEasing),
        label = "archiveWordLinearProgress"
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun ActiveWordCircularProgressRing(
    playbackProgressProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = playbackProgressProvider().coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 55, easing = LinearEasing),
        label = "archiveWordRingProgress"
    )
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val activeColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val strokePx = 2.5.dp.toPx()
        val inset = strokePx / 2f
        val arcSize = Size(
            width = (size.width - inset * 2f).coerceAtLeast(1f),
            height = (size.height - inset * 2f).coerceAtLeast(1f)
        )
        val topLeft = Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        val sweep = (360f * animatedProgress).coerceIn(0f, 360f)
        if (sweep > 0.5f) {
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
    }
}
