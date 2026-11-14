package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.StudioCheckboxRow
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.rememberStudioInteraction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Velocity
import com.example.audio.AcousticAnalyzer
import com.example.audio.CanonicalToneMember
import com.example.audio.CorpusPhonologyAnalyzer
import com.example.data.Speaker
import com.example.data.SpeakerRecording
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.utils.ExportOptions
import com.example.utils.PraatCollectionExporter
import com.example.utils.ZipUtility
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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
    var selectedFilter by remember { mutableStateOf(0) } // 0: 全部, 1: 已采录, 2: 未采录, 3: 需重录(异常)
    var showMacroSummarySheet by remember { mutableStateOf(false) }

    LaunchedEffect(isSimpleMode) {
        if (isSimpleMode) {
            showMacroSummarySheet = false
        }
    }

    val filteredWords = remember(words, searchText, selectedFilter) {
        words.filter { word ->
            val matchesSearch = word.text.contains(searchText, ignoreCase = true) ||
                word.remark.contains(searchText, ignoreCase = true) ||
                word.speakerNote.contains(searchText, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                1 -> word.isRecorded
                2 -> !word.isRecorded
                3 -> word.isRecorded && word.qualityStatus != null && word.qualityStatus != "正常"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val gridState = rememberLazyGridState()
    LaunchedEffect(playingWordId, isContinuousPlaying) {
        if (isContinuousPlaying && playingWordId != null) {
            val targetIdx = filteredWords.indexOfFirst { it.id == playingWordId }
            if (targetIdx >= 0) {
                gridState.animateScrollToItem(targetIdx)
            }
        }
    }

    val projectSummaries by viewModel.projectSummaries.collectAsState()
    val currentProjectSummary = project?.id?.let { projectSummaries[it] }
    val currentRecordedCount = remember(words) { words.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() } }
    val totalProjectRecordings = currentProjectSummary?.totalRecordingsCount ?: currentRecordedCount
    val startedSpeakersCount = currentProjectSummary?.startedSpeakerCount ?: (if (currentRecordedCount > 0) 1 else 0)

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
                                    FilledTonalButton(
                                        onClick = { showMacroSummarySheet = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .testTag("open_macro_acoustic_summary_button")
                                    ) {
                                        Icon(Icons.Default.Insights, contentDescription = S.archive.macroReportDesc, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(S.archive.macroReportButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }
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
                                        .testTag("export_zip_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = S.archive.exportProjectDesc, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(S.archive.exportProjectButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                val projectGroup = project?.speakerGroupName ?: ""
                                val sortedSpeakers = remember(speakers, projectGroup, isSimpleMode) {
                                    if (!isSimpleMode && projectGroup.isNotBlank()) {
                                        speakers.sortedByDescending { it.groupName == projectGroup }
                                    } else {
                                        speakers
                                    }
                                }
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
                                val playableInFilter = remember(filteredWords) {
                                    filteredWords.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
                                }
                                if (playableInFilter > 0 || isContinuousPlaying) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (isContinuousPlaying) {
                                                viewModel.stopContinuousPlayback()
                                            } else {
                                                viewModel.startContinuousPlayback(filteredWords)
                                            }
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isContinuousPlaying) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer
                                            },
                                            contentColor = if (isContinuousPlaying) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            }
                                        ),
                                        contentPadding = PaddingValues(horizontal = if (isCompactToolbar) 8.dp else 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("continuous_playback_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isContinuousPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text(
                                            text = if (isContinuousPlaying) S.archive.stopContinuousPlay else S.archive.continuousListen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
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

                    // Filter capsules row selector (unified with RecordingScreen & ProjectListScreen: 已录 / 未录 / 需重录)
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
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompactFilters = maxWidth < 360.dp
                        val (recCount, unrecCount, flagCount) = filterCounts
                        val sep = if (isCompactFilters) "" else " "
                        val filterLabels = listOf(
                            S.archive.filterAll.withArgs("sep" to sep, "count" to words.size),
                            S.archive.filterRecorded.withArgs("sep" to sep, "count" to recCount),
                            S.archive.filterUnrecorded.withArgs("sep" to sep, "count" to unrecCount),
                            S.archive.filterFlagged.withArgs("sep" to sep, "count" to flagCount)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactFilters) 6.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            filterLabels.forEachIndexed { index, label ->
                                val selected = selectedFilter == index
                                StudioFilterChip(
                                    selected = selected,
                                    onClick = { selectedFilter = index },
                                    labelText = label,
                                    horizontalPadding = if (isCompactFilters) 8.dp else 11.dp,
                                    isErrorVariant = index == 3
                                )
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val useMultiColumnGrid = maxWidth >= 600.dp
                val wordIdToIndexMap = remember(words) {
                    val map = HashMap<Long, Int>(words.size * 2)
                    words.forEachIndexed { index, w -> map[w.id] = index }
                    map
                }

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
                        LazyVerticalGrid(
                            columns = if (useMultiColumnGrid) {
                                GridCells.Adaptive(minSize = 300.dp)
                            } else {
                                GridCells.Fixed(1)
                            },
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp, 2.dp, 16.dp, 24.dp),
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
                                    showAcousticDetailsButton = !isSimpleMode
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

    if (showMacroSummarySheet && project != null) {
        ProjectAcousticSummarySheet(
            projectId = project!!.id,
            projectName = project!!.name,
            speaker = currentSpeaker,
            words = words,
            viewModel = viewModel,
            playingWordId = playingWordId,
            onPlayWord = { viewModel.playWordAudio(it) },
            onDismiss = { showMacroSummarySheet = false }
        )
    }

    wordPendingDelete?.let { targetWord ->
        AlertDialog(
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
    modifier: Modifier = Modifier
) {
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val isFlagged = word.isRecorded && word.qualityStatus != null && word.qualityStatus != "正常"
    val colorScheme = MaterialTheme.colorScheme

    val cardBgColor = when {
        isPlaying -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.50f)
        isHovered && isFlagged -> lerp(colorScheme.surface, colorScheme.errorContainer, 0.35f)
        isHovered -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.35f)
        else -> colorScheme.surface
    }
    val cardBorderColor = when {
        isPlaying -> colorScheme.primary
        isHovered && isFlagged -> colorScheme.error
        isHovered -> colorScheme.primary
        isFlagged -> lerp(colorScheme.outlineVariant, colorScheme.error, 0.45f)
        else -> colorScheme.outlineVariant
    }

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
        border = BorderStroke(if (isPlaying || isHovered) 1.5.dp else 1.dp, cardBorderColor),
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                color = if (word.isRecorded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (word.originalIndex + 1).toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (word.isRecorded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
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
                            }
                        } else {
                            Text(
                                text = S.archive.unrecordedJumpHint,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (word.isRecorded) {
                        IconButton(
                            onClick = onPlayToggle,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("archive_play_${word.id}")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) S.common.stop else S.common.play,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (onToggleQualityFlag != null) {
                            val isFlagged = word.qualityStatus != null && word.qualityStatus != "正常"
                            IconButton(
                                onClick = onToggleQualityFlag,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("archive_flag_${word.id}")
                            ) {
                                Icon(
                                    imageVector = if (isFlagged) Icons.Default.CheckCircle else Icons.Default.Flag,
                                    contentDescription = if (isFlagged) S.archive.confirmAsNormalDesc else S.archive.markAsRerecordDesc,
                                    tint = if (isFlagged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (showAcousticDetailsButton) {
                            IconButton(
                                onClick = onShowPhoneticDetails,
                                modifier = Modifier
                                    .size(38.dp)
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

            if (isPlaying) {
                ActiveWordPlaybackProgressBar(playbackProgressProvider = playbackProgressProvider)
            }
        }
    }
}

@Composable
private fun ActiveWordPlaybackProgressBar(
    playbackProgressProvider: () -> Float
) {
    LinearProgressIndicator(
        progress = playbackProgressProvider,
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/**
 * Compatibility delegate so existing callers can invoke analyzeWordPhonetics cleanly.
 */
fun analyzeWordPhonetics(
    context: Context,
    word: Word,
    speakerGender: String?
): AcousticAnalyzer.PhoneticAcousticResult {
    return AcousticAnalyzer.analyzeWordPhonetics(context, word, speakerGender)
}

@androidx.compose.runtime.Immutable
data class CrossSpeakerWordAnalysis(
    val speaker: Speaker,
    val recording: SpeakerRecording,
    val audioFile: File,
    val analysis: AcousticAnalyzer.PhoneticAcousticResult
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneticDetailsBottomSheet(
    word: Word,
    speaker: Speaker?,
    viewModel: RecordingViewModel? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        PhoneticDetailsContent(
            word = word,
            speaker = speaker,
            viewModel = viewModel,
            onDismiss = onDismiss,
            isDockedPane = false
        )
    }
}

@Composable
fun PhoneticDetailsContent(
    word: Word,
    speaker: Speaker?,
    viewModel: RecordingViewModel? = null,
    onDismiss: () -> Unit,
    isDockedPane: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playingWordId = viewModel?.playingWordId?.collectAsState()?.value
    val takesRefreshTrigger = viewModel?.takesRefreshTrigger?.collectAsState()?.value ?: 0
    var pitchCalibrateTick by remember { mutableStateOf(0) }
    var isCalibratingPitch by remember { mutableStateOf(false) }
    var showManualPitchDialog by remember { mutableStateOf(false) }

    // Asynchronous computation off the UI thread with brief frame yield on sheet entrance
    val analysisState by produceState<AcousticAnalyzer.PhoneticAcousticResult?>(
        initialValue = null,
        key1 = word.id,
        key2 = word.recordingFile,
        key3 = "${speaker?.id}_$pitchCalibrateTick"
    ) {
        if (!isDockedPane) delay(65)
        value = withContext(Dispatchers.Default) {
            AcousticAnalyzer.analyzeWordPhonetics(context, word, speaker?.gender)
        }
    }

    val canonicalMember by produceState<CanonicalToneMember?>(
        initialValue = CorpusPhonologyAnalyzer.lookupCachedCanonicalMember(
            projectId = word.projectId,
            speakerId = speaker?.id ?: -1L,
            wordId = word.id
        ),
        key1 = word.id,
        key2 = word.recordingFile,
        key3 = "${speaker?.id}_$pitchCalibrateTick"
    ) {
        val cached = CorpusPhonologyAnalyzer.lookupCachedCanonicalMember(
            projectId = word.projectId,
            speakerId = speaker?.id ?: -1L,
            wordId = word.id
        )
        if (cached != null) {
            value = cached
        } else if (viewModel != null) {
            // Let sheet entrance animation & single-word analysis finish first before background corpus clustering
            delay(220)
            value = withContext(Dispatchers.Default) {
                val spkWords = viewModel.getWordsForSpeakerSync(word.projectId, speaker?.id ?: -1L)
                val report = CorpusPhonologyAnalyzer.buildSpeakerPhonologyReport(
                    context = context,
                    projectId = word.projectId,
                    speaker = speaker,
                    words = spkWords
                )
                report.wordIdToCanonicalMember[word.id]
            }
        }
    }

    val activeAudioFile = remember(word.projectId, word.recordingFile) {
        word.recordingFile?.takeIf { it.isNotBlank() }?.let {
            File(File(context.getExternalFilesDir(null), "project_${word.projectId}"), it)
        }
    }
    val activeSpeakerPitchRange = remember(speaker?.id, pitchCalibrateTick) {
        speaker?.id?.let { viewModel?.getSpeakerPitchRange(it) }
    }

    // Load cross-speaker recordings & acoustic contours after sheet slide-in completes
    val crossSpeakerList by produceState<List<CrossSpeakerWordAnalysis>>(
        initialValue = emptyList(),
        key1 = word.id,
        key2 = word.recordingFile,
        key3 = takesRefreshTrigger
    ) {
        if (viewModel == null) {
            value = emptyList()
        } else {
            delay(180)
            value = withContext(Dispatchers.Default) {
                val pairs = viewModel.getCrossSpeakerRecordingsForWord(word.id)
                val projDir = File(context.getExternalFilesDir(null), "project_${word.projectId}")
                pairs.mapNotNull { (spk, rec) ->
                    val f = File(projDir, rec.recordingFile)
                    if (f.exists() && f.length() > 44L) {
                        val spkWord = word.copy(
                            isRecorded = true,
                            recordingFile = rec.recordingFile,
                            durationMs = rec.durationMs,
                            qualityStatus = rec.qualityStatus
                        )
                        val res = AcousticAnalyzer.analyzeWordPhonetics(context, spkWord, spk.gender)
                        CrossSpeakerWordAnalysis(spk, rec, f, res)
                    } else {
                        null
                    }
                }
            }
        }
    }

    var overlayOtherSpeakersOnToneChart by remember { mutableStateOf(true) }

    // Available multi-takes for the active speaker
    val availableTakes by produceState<List<File>>(
        initialValue = emptyList(),
        word.id,
        word.recordingFile,
        speaker?.id,
        takesRefreshTrigger
    ) {
        val sid = speaker?.id
        value = if (viewModel != null && sid != null) {
            withContext(Dispatchers.IO) {
                viewModel.getAvailableTakesForWord(word.projectId, word.id, sid)
            }
        } else {
            emptyList()
        }
    }

    // Consumes leftover downward scroll & fling from the scrollable content area so that
    // scrolling back to the top inside the page never drags the entire drawer closed.
    val contentScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = available
        }
    }

    val horizontalPad = if (isDockedPane) 16.dp else 20.dp
    val analysis = analysisState
    if (analysis == null) {
        Box(
            modifier = if (isDockedPane) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(S.archive.analyzingFeatures, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        val isFlagged = word.qualityStatus != null && word.qualityStatus != "正常"
        Column(
            modifier = if (isDockedPane) {
                Modifier.fillMaxSize().testTag("phonetic_details_docked_pane")
            } else {
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            }
        ) {
            // Fixed Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = horizontalPad,
                        end = horizontalPad,
                        top = if (isDockedPane) 14.dp else 0.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = S.archive.detailSheetTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = word.text,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.Bottom)
                                )
                                if (word.remark.isNotEmpty()) {
                                    Text(
                                        text = S.archive.wordRemarkBracketFormat.withArgs("remark" to word.remark),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.Bottom)
                                            .padding(bottom = 3.dp)
                                    )
                                }
                                if (word.speakerNote.isNotEmpty()) {
                                    Text(
                                        text = S.recording.notePrefix.withArgs("note" to word.speakerNote),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.Bottom)
                                            .padding(bottom = 3.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (viewModel != null && word.isRecorded) {
                                Surface(
                                    color = if (isFlagged) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.toggleManualQualityFlag(word)
                                            Toast.makeText(
                                                context,
                                                if (isFlagged) S.archive.markedWordNormalToast else S.recording.vmMarkedRerecordToast,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        .testTag("details_sheet_quality_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFlagged) Icons.Default.Warning else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isFlagged) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = if (isFlagged) {
                                                S.archive.statusRerecordToggleChip.withArgs(
                                                    "status" to S.quality.displayStatus(word.qualityStatus, simpleMode = false)
                                                )
                                            } else {
                                                S.archive.statusNormalToggleChip
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (isFlagged) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    color = if (analysis.isValidVoiced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (analysis.isValidVoiced) {
                                            if (analysis.isPitchCalibrated) S.quality.statusAlignedDisplay else S.archive.pronunciationNormalBadge
                                        } else {
                                            S.archive.lowVolumeShort
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = if (analysis.isValidVoiced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = S.archive.closeDetailDrawerDesc,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = S.archive.speakerMetaSummary.withArgs(
                                "name" to (speaker?.name ?: S.common.defaultLabel),
                                "gender" to S.speaker.displayGender(speaker?.gender ?: "未设"),
                                "dialect" to (speaker?.dialect?.ifBlank { S.archive.unsetDialectLabel } ?: S.speaker.defaultDialectMandarin),
                                "age" to (speaker?.age ?: 25)
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // Scrollable body (locked against accidental sheet-dismissal when inside ModalBottomSheet)
                Column(
                    modifier = if (isDockedPane) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = horizontalPad, end = horizontalPad, top = 14.dp, bottom = 24.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .nestedScroll(contentScrollLock)
                            .verticalScroll(rememberScrollState())
                            .padding(start = horizontalPad, end = horizontalPad, top = 14.dp, bottom = 36.dp)
                    }
                ) {
                    // Clean 2x2 Summary Cards
                    val volumeLabel = when {
                        analysis.peakDbfs > -1.5f -> S.archive.intensityLoud.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.intensityDbfs))
                        analysis.intensityDbfs > -32.0f -> S.archive.intensityOptimal.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.intensityDbfs))
                        else -> S.archive.intensityQuiet.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.intensityDbfs))
                    }
                    val quietnessLabel = when {
                        analysis.snrDb >= 20f -> S.archive.snrQuiet.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.snrDb))
                        analysis.snrDb >= 10f -> S.archive.snrFair.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.snrDb))
                        else -> S.archive.snrNoisy.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.snrDb))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(S.archive.metricVolumeTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = volumeLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = S.archive.peakSubFormat.withArgs("db" to String.format(java.util.Locale.US, "%.1f", analysis.peakDbfs)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(S.archive.metricDurationTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = S.archive.secondsValueFormat.withArgs("sec" to String.format(java.util.Locale.US, "%.2f", word.durationMs / 1000f)),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = S.archive.msValueFormat.withArgs("ms" to word.durationMs),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(S.archive.metricQuietnessTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = quietnessLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = S.archive.snrSubLabel,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(S.archive.metricChaoToneTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (analysis.isValidVoiced) "[${analysis.chaoToneNumbers}]" else S.archive.unavailableLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (analysis.isValidVoiced) {
                                        S.archive.meanPitchHzFormat.withArgs("hz" to String.format(java.util.Locale.US, "%.0f", analysis.pitchMean))
                                    } else {
                                        S.archive.lowVolumePitchNotDetected
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 1: Pitch contour & 5-level scale (with optional cross-speaker overlay)
                    Text(
                        text = S.archive.toneContourSectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SingleWordCanonicalToneBanner(
                        analysis = analysis,
                        canonicalMember = canonicalMember,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val otherSpeakersEntries = remember(crossSpeakerList, speaker?.id) {
                        crossSpeakerList.filter { it.speaker.id != speaker?.id && it.analysis.isValidVoiced && it.analysis.contourPoints.isNotEmpty() }
                    }
                    val comparePalette = remember {
                        listOf(
                            Color(0xFFE65100),
                            Color(0xFF00897B),
                            Color(0xFF8E24AA),
                            Color(0xFFC62828),
                            Color(0xFF2E7D32)
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S.archive.refTonePatternPrefix.withArgs("code" to analysis.toneCode),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (analysis.isValidVoiced) {
                                        if (analysis.isPitchCalibrated) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable(enabled = viewModel != null && speaker != null) {
                                                        showManualPitchDialog = true
                                                    }
                                            ) {
                                                Text(
                                                    text = activeSpeakerPitchRange?.let {
                                                        "${it.first.toInt()}–${it.second.toInt()} Hz"
                                                    } ?: S.archive.personalPitchRangePill,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else if (viewModel != null && speaker != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable(enabled = !isCalibratingPitch) {
                                                        isCalibratingPitch = true
                                                        viewModel.estimatePitchRangeForSpeaker(speaker) { range ->
                                                            isCalibratingPitch = false
                                                            pitchCalibrateTick++
                                                            if (range != null) {
                                                                Toast.makeText(
                                                                    context,
                                                                    S.archive.calibratedPitchRangePill.withArgs(
                                                                        "min" to range.first.toInt(),
                                                                        "max" to range.second.toInt()
                                                                    ),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            } else {
                                                                // Fewer than 3 voiced samples — open manual Hz dialog directly
                                                                showManualPitchDialog = true
                                                            }
                                                        }
                                                    }
                                            ) {
                                                Text(
                                                    text = if (isCalibratingPitch) S.archive.calibratingPitchBtn else S.archive.defaultPitchRangeCalibrateBtn,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { showManualPitchDialog = true }
                                            ) {
                                                Text(
                                                    text = S.recording.manualPitchButton,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (showManualPitchDialog && viewModel != null && speaker != null) {
                                        ManualSpeakerPitchDialog(
                                            speaker = speaker,
                                            currentRange = activeSpeakerPitchRange,
                                            onSave = { minHz, maxHz ->
                                                viewModel.saveSpeakerPitchRange(speaker.id, minHz, maxHz)
                                                pitchCalibrateTick++
                                                showManualPitchDialog = false
                                                Toast.makeText(
                                                    context,
                                                    S.recording.manualPitchSavedToast.withArgs(
                                                        "min" to minHz.toInt(),
                                                        "max" to maxHz.toInt()
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onResetAuto = {
                                                viewModel.clearSpeakerPitchRange(speaker.id)
                                                pitchCalibrateTick++
                                                showManualPitchDialog = false
                                                Toast.makeText(
                                                    context,
                                                    S.recording.manualPitchResetToast,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onDismiss = { showManualPitchDialog = false }
                                        )
                                    }
                                    Text(
                                        text = if (analysis.isValidVoiced) {
                                            S.archive.meanPitchShortHzFormat.withArgs("hz" to String.format(java.util.Locale.US, "%.0f", analysis.pitchMean))
                                        } else {
                                            S.archive.lowVolumeShort
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = analysis.toneTrajectory,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                                )
                                if (analysis.isValidVoiced) {
                                    Text(
                                        text = analysis.pitchRange,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val plotSurfaceBg = MaterialTheme.colorScheme.surface
                            val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            val curveStrokeColor = MaterialTheme.colorScheme.primary
                            val secondarySyllablePalette = listOf(
                                MaterialTheme.colorScheme.primary,
                                Color(0xFF1976D2),
                                Color(0xFF2E7D32),
                                Color(0xFFEF6C00),
                                Color(0xFF8E24AA)
                            )

                            // Compute time-proportional horizontal weights for multi-syllable words
                            val syllableWeights = remember(analysis.segmentCount, analysis.segments, analysis.syllableVoicedMs) {
                                val count = analysis.segmentCount.coerceAtLeast(1)
                                if (count <= 1) {
                                    listOf(1f)
                                } else {
                                    val rawDurs = (0 until count).map { idx ->
                                        val segDur = analysis.segments.getOrNull(idx)?.let {
                                            ((it.endSec - it.startSec) * 1000.0).toFloat()
                                        } ?: 0f
                                        val vDur = analysis.syllableVoicedMs.getOrElse(idx) { 0 }.toFloat()
                                        when {
                                            segDur > 25f -> segDur
                                            vDur > 20f -> vDur
                                            else -> 180f
                                        }
                                    }
                                    val sumRaw = rawDurs.sum().coerceAtLeast(1f)
                                    val minShare = (0.20f).coerceAtMost(0.75f / count)
                                    val clamped = rawDurs.map { (it / sumRaw).coerceIn(minShare, 0.78f) }
                                    val sumClamped = clamped.sum().coerceAtLeast(0.01f)
                                    clamped.map { it / sumClamped }
                                }
                            }

                            // Per-syllable aligned header strip when segmentCount > 1
                            if (analysis.segmentCount > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 46.dp, bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (s in 0 until analysis.segmentCount) {
                                        val wWeight = syllableWeights.getOrElse(s) { 1f / analysis.segmentCount }
                                        val lbl = analysis.segments.getOrNull(s)?.label?.ifBlank { null }
                                            ?: S.common.syllableFallback.withArgs("index" to s + 1)
                                        val tVal = analysis.syllableToneNumbers.getOrNull(s) ?: "--"
                                        val isNeutral = analysis.syllableNeutralFlags.getOrElse(s) { false }
                                        val isChecked = analysis.syllableCheckedFlags.getOrElse(s) { false }
                                        val col = secondarySyllablePalette[s % secondarySyllablePalette.size]
                                        val suffix = when {
                                            isNeutral -> S.archive.neutralShortMark
                                            isChecked -> S.archive.checkedShortMark
                                            else -> ""
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(7.dp),
                                            color = col.copy(alpha = 0.11f),
                                            border = BorderStroke(1.dp, col.copy(alpha = 0.34f)),
                                            modifier = Modifier.weight(wWeight)
                                        ) {
                                            Text(
                                                text = "${circledSyllableBadge(s + 1)} $lbl [$tVal$suffix]",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = col,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(132.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(46.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(S.archive.scale5High, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                    Text(S.archive.scale4HalfHigh, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(S.archive.scale3Mid, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(S.archive.scale2HalfLow, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(S.archive.scale1Low, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(plotSurfaceBg, shape = RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val sectionsY = size.height / 4f
                                        for (i in 0..4) {
                                            val cy = i * sectionsY
                                            drawLine(
                                                color = gridLineColor,
                                                start = Offset(0f, cy),
                                                end = Offset(size.width, cy),
                                                strokeWidth = 1.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                            )
                                        }

                                        if (analysis.segmentCount <= 1) {
                                            val sectionsX = size.width / 4f
                                            for (i in 1..3) {
                                                val cx = i * sectionsX
                                                drawLine(
                                                    color = gridLineColor,
                                                    start = Offset(cx, 0f),
                                                    end = Offset(cx, size.height),
                                                    strokeWidth = 1.dp.toPx(),
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                                )
                                            }
                                        }

                                        // Draw overlaid comparison curves from other speakers (dashed)
                                        if (overlayOtherSpeakersOnToneChart && otherSpeakersEntries.isNotEmpty()) {
                                            otherSpeakersEntries.forEachIndexed { idx, otherEntry ->
                                                val otherPoints = otherEntry.analysis.contourPoints
                                                if (otherPoints.size >= 2) {
                                                    val col = comparePalette[idx % comparePalette.size].copy(alpha = 0.72f)
                                                    val path = Path()
                                                    otherPoints.forEachIndexed { i, p ->
                                                        val px = (i.toFloat() / (otherPoints.size - 1)) * size.width
                                                        val py = (1f - p) * size.height * 0.84f + size.height * 0.08f
                                                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                    }
                                                    drawPath(
                                                        path = path,
                                                        color = col,
                                                        style = Stroke(
                                                            width = 2.dp.toPx(),
                                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        val curvePoints = analysis.contourPoints
                                        if (analysis.isValidVoiced && curvePoints.isNotEmpty()) {
                                            val pointsPerSegment = 5
                                            var cursorX = 0f
                                            var prevSyllableEndPt: Offset? = null

                                            for (s in 0 until analysis.segmentCount) {
                                                val colWidth = if (analysis.segmentCount == 1 && analysis.isCheckedTone) {
                                                    size.width * analysis.checkedToneRatio.coerceIn(0.40f, 0.62f)
                                                } else {
                                                    size.width * syllableWeights.getOrElse(s) { 1f / analysis.segmentCount.coerceAtLeast(1) }
                                                }

                                                if (s > 0) {
                                                    drawLine(
                                                        color = gridLineColor.copy(alpha = 0.95f),
                                                        start = Offset(cursorX, 0f),
                                                        end = Offset(cursorX, size.height),
                                                        strokeWidth = 1.5.dp.toPx(),
                                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f), 0f)
                                                    )
                                                }

                                                val startIdx = s * pointsPerSegment
                                                if (startIdx + pointsPerSegment <= curvePoints.size) {
                                                    val sylCol = if (analysis.segmentCount > 1) {
                                                        secondarySyllablePalette[s % secondarySyllablePalette.size]
                                                    } else {
                                                        curveStrokeColor
                                                    }
                                                    val isNeutralSyl = analysis.syllableNeutralFlags.getOrElse(s) { false }
                                                    val isCheckedSyl = if (analysis.segmentCount == 1) {
                                                        analysis.isCheckedTone
                                                    } else {
                                                        analysis.syllableCheckedFlags.getOrElse(s) { false }
                                                    }

                                                    val innerPad = if (analysis.segmentCount > 1) (colWidth * 0.10f).coerceAtMost(10.dp.toPx()) else 0f
                                                    val usableW = (colWidth - innerPad * 2f).coerceAtLeast(8f)
                                                    val drawSpanW = when {
                                                        isNeutralSyl -> usableW * 0.45f
                                                        isCheckedSyl && analysis.segmentCount > 1 -> usableW * 0.56f
                                                        else -> usableW
                                                    }
                                                    val sx = cursorX + innerPad
                                                    val stepX = drawSpanW / (pointsPerSegment - 1)

                                                    val firstPy = (1f - curvePoints[startIdx]) * size.height * 0.84f + size.height * 0.08f
                                                    val firstPt = Offset(sx, firstPy)

                                                    // Subtle dotted inter-syllable pitch bridge across consonant/juncture
                                                    val prevEnd = prevSyllableEndPt
                                                    if (prevEnd != null) {
                                                        drawLine(
                                                            color = gridLineColor.copy(alpha = 0.75f),
                                                            start = prevEnd,
                                                            end = firstPt,
                                                            strokeWidth = 1.3.dp.toPx(),
                                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)
                                                        )
                                                    }

                                                    if (isNeutralSyl) {
                                                        // Neutral tone (轻声): short dashed trace + prominent hollow pitch target circle
                                                        val nPath = Path()
                                                        var midPt = firstPt
                                                        var endPt = firstPt
                                                        for (i in 0 until pointsPerSegment) {
                                                            val p = curvePoints[startIdx + i]
                                                            val px = sx + i * stepX
                                                            val py = (1f - p) * size.height * 0.84f + size.height * 0.08f
                                                            if (i == 0) nPath.moveTo(px, py) else nPath.lineTo(px, py)
                                                            if (i == 2) midPt = Offset(px, py)
                                                            endPt = Offset(px, py)
                                                        }
                                                        drawPath(
                                                            path = nPath,
                                                            color = sylCol.copy(alpha = 0.65f),
                                                            style = Stroke(
                                                                width = 2.2.dp.toPx(),
                                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                                                            )
                                                        )
                                                        drawCircle(color = plotSurfaceBg, radius = 4.8.dp.toPx(), center = midPt)
                                                        drawCircle(
                                                            color = sylCol,
                                                            radius = 4.8.dp.toPx(),
                                                            center = midPt,
                                                            style = Stroke(width = 2.2.dp.toPx())
                                                        )
                                                        prevSyllableEndPt = endPt
                                                    } else {
                                                        val path = Path()
                                                        var lastPx = sx
                                                        var lastPy = firstPy
                                                        for (i in 0 until pointsPerSegment) {
                                                            val p = curvePoints[startIdx + i]
                                                            val px = sx + i * stepX
                                                            val py = (1f - p) * size.height * 0.84f + size.height * 0.08f
                                                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                            lastPx = px
                                                            lastPy = py
                                                            drawCircle(
                                                                color = sylCol,
                                                                radius = 3.3.dp.toPx(),
                                                                center = Offset(px, py)
                                                            )
                                                        }
                                                        drawPath(
                                                            path = path,
                                                            color = sylCol,
                                                            style = Stroke(width = 3.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                        )
                                                        if (isCheckedSyl) {
                                                            drawLine(
                                                                color = sylCol,
                                                                start = Offset(lastPx, (lastPy - 7.5.dp.toPx()).coerceAtLeast(2f)),
                                                                end = Offset(lastPx, (lastPy + 7.5.dp.toPx()).coerceAtMost(size.height - 2f)),
                                                                strokeWidth = 2.6.dp.toPx(),
                                                                cap = StrokeCap.Round
                                                            )
                                                        }
                                                        prevSyllableEndPt = Offset(lastPx, lastPy)
                                                    }
                                                }
                                                cursorX += colWidth
                                            }
                                        }
                                    }

                                    if (!analysis.isValidVoiced || analysis.contourPoints.isEmpty()) {
                                        Text(
                                            text = S.archive.noClearPitchCurveHint,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            if (otherSpeakersEntries.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(modifier = Modifier.size(8.dp).background(curveStrokeColor, CircleShape))
                                            Text(
                                                text = S.archive.currentSpeakerSolidLegend.withArgs("name" to (speaker?.name ?: S.archive.currentSpeakerShortFallback)),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (overlayOtherSpeakersOnToneChart) {
                                            otherSpeakersEntries.forEachIndexed { idx, entry ->
                                                val col = comparePalette[idx % comparePalette.size]
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Box(modifier = Modifier.size(8.dp).background(col, CircleShape))
                                                    Text(
                                                        text = S.archive.speakerChaoBracketItem.withArgs(
                                                            "name" to entry.speaker.name,
                                                            "chao" to entry.analysis.chaoToneNumbers
                                                        ),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    StudioFilterChip(
                                        selected = overlayOtherSpeakersOnToneChart,
                                        onClick = { overlayOtherSpeakersOnToneChart = !overlayOtherSpeakersOnToneChart },
                                        labelText = S.archive.overlayCompareChip
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = S.archive.chaoNormalizationExplanation,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Section 1B: Cross-Speaker Same-Word Comparison (Feature #5)
                    if (crossSpeakerList.size >= 2 && viewModel != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = S.archive.crossSpeakerCompareTitle.withArgs("count" to crossSpeakerList.size),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = S.archive.crossSpeakerCompareSubtitle.withArgs("word" to word.text),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        for (item in crossSpeakerList) {
                                            val tagId = -(word.id * 1000 + item.speaker.id)
                                            viewModel.playSpecificAudioFile(item.audioFile, tagId)
                                            val waitMs = item.recording.durationMs.coerceIn(450L, 4000L) + 420L
                                            delay(waitMs)
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(S.archive.playSequentialButton, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                crossSpeakerList.forEach { item ->
                                    val isCurrentSpk = item.speaker.id == speaker?.id
                                    val tagId = -(word.id * 1000 + item.speaker.id)
                                    val isPlayingThis = playingWordId == tagId
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCurrentSpk) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrentSpk) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = item.speaker.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = S.archive.crossSpeakerRowMeta.withArgs(
                                                            "gender" to S.speaker.displayGender(item.speaker.gender),
                                                            "age" to item.speaker.age,
                                                            "dialect" to item.speaker.dialect.ifBlank { S.speaker.defaultDialectMandarin }
                                                        ),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(
                                                        text = S.archive.crossSpeakerChaoValue.withArgs(
                                                            "chao" to item.analysis.chaoToneNumbers,
                                                            "code" to item.analysis.toneCode
                                                        ),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (item.analysis.isValidVoiced) {
                                                        Text(
                                                            text = S.archive.crossSpeakerF0Format.withArgs(
                                                                "hz" to String.format(java.util.Locale.US, "%.0f", item.analysis.pitchMean)
                                                            ),
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    }
                                                }
                                            }
                                            FilledTonalIconButton(
                                                onClick = { viewModel.playSpecificAudioFile(item.audioFile, tagId) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = S.archive.listenSpeakerDesc.withArgs("name" to item.speaker.name),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 1C: Multi-Take Switcher inside Details Sheet (Feature #6)
                    if (availableTakes.size >= 2 && viewModel != null) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = S.archive.multiTakeSectionTitle.withArgs("count" to availableTakes.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableTakes.forEachIndexed { idx, file ->
                                    val isCurrentTake = file.name == word.recordingFile
                                    val takePlayId = -(word.id * 100 + idx + 1)
                                    val isPlayingTake = playingWordId == takePlayId
                                    val durSec = remember(file.name, file.length()) {
                                        AcousticAnalyzer.getExactWavDurationSec(file)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isCurrentTake) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrentTake) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (idx == 0) {
                                                        S.archive.latestTakeLabel.withArgs("count" to availableTakes.size)
                                                    } else {
                                                        S.archive.historyTakeLabel.withArgs("num" to availableTakes.size - idx)
                                                    },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = S.archive.takeDurationAndState.withArgs(
                                                        "sec" to String.format(java.util.Locale.US, "%.2f", durSec),
                                                        "state" to if (isCurrentTake) S.archive.activeTakeState else S.archive.backupTakeState
                                                    ),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FilledTonalIconButton(
                                                    onClick = { viewModel.playSpecificAudioFile(file, takePlayId) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlayingTake) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                        contentDescription = S.archive.listenThisTakeDesc,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                if (!isCurrentTake) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            viewModel.selectTakeForWord(word, file)
                                                            Toast.makeText(context, S.archive.switchedTakeToast, Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(32.dp)
                                                    ) {
                                                        Text(S.archive.adoptTakeButton, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 1C: Wideband Spectrogram (0..5000 Hz) + Formant & Pitch Track Overlay
                    Text(
                        text = S.archive.widebandAndFormantTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    SingleWordWidebandSpectrogramCard(
                        audioFile = activeAudioFile,
                        speakerGender = speaker?.gender,
                        pitchRange = activeSpeakerPitchRange,
                        isPlaying = playingWordId == word.id,
                        onTogglePlay = if (viewModel != null) {
                            { viewModel.playWordAudio(word) }
                        } else {
                            null
                        },
                        segments = analysis.segments
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 2: Dynamic Vowel Formants (Monophthong Nucleus vs Diphthong/Triphthong Trajectory)
                    Text(
                        text = S.archive.vowelSpaceTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    SingleWordDynamicVowelCard(
                        word = word,
                        speaker = speaker,
                        analysis = analysis,
                        otherSpeakersEntries = otherSpeakersEntries
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 3: Clean File Info Card
                    Text(
                        text = S.archive.audioFileInfoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S.archive.audioSpecLabel,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.width(76.dp)
                                )
                                Text(
                                    text = S.archive.audioSpecValue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S.archive.fileSizeLabel,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.width(76.dp)
                                )
                                Text(
                                    text = S.archive.fileSizeKbFormat.withArgs("kb" to String.format(java.util.Locale.US, "%.1f", analysis.fileSizeKb)),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S.archive.fileNameLabel,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.width(76.dp)
                                )
                                Text(
                                    text = word.recordingFile ?: S.archive.unrecordedPlaceholder,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Section 4: Praat .TextGrid Live Preview & Direct Single-File Share
                    val singleWordTgConfig = viewModel?.textGridFormatConfig?.collectAsState()?.value
                        ?: com.example.utils.TextGridFormatConfig()
                    val praatPreviewPair by produceState<Pair<String, AcousticAnalyzer.AnalysisResult?>>(
                        initialValue = "" to null,
                        keys = arrayOf(word.id, word.recordingFile, speaker?.id, singleWordTgConfig)
                    ) {
                        value = withContext(Dispatchers.IO) {
                            val recFile = word.recordingFile?.let {
                                File(File(context.getExternalFilesDir(null), "project_${word.projectId}"), it)
                            }
                            val exactDur = AcousticAnalyzer.getExactWavDurationSec(recFile)
                            val spPrefs = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
                            val pMin = spPrefs.getFloat("pitch_min_${speaker?.id ?: -1L}", -1f)
                            val pMax = spPrefs.getFloat("pitch_max_${speaker?.id ?: -1L}", -1f)
                            val pRange = if (pMin > 40f && pMax > pMin + 15f) Pair(pMin, pMax) else null
                            val parts = ZipUtility.extractSyllableLabels(word.text)
                            val rawResult = if (recFile != null && recFile.exists()) {
                                AcousticAnalyzer.analyzeByGender(
                                    file = recFile,
                                    speakerGender = speaker?.gender,
                                    segmentCount = parts.size.coerceAtLeast(1),
                                    pitchRange = pRange,
                                    segmentLabels = parts
                                )
                            } else null
                            ZipUtility.buildPraatTextGrid(
                                word = word,
                                analysis = rawResult,
                                exactDurationSec = exactDur,
                                config = singleWordTgConfig
                            ) to rawResult
                        }
                    }
                    val textGridPreview = praatPreviewPair.first
                    val rawAnalysisResult = praatPreviewPair.second

                    val saveSingleWordTextGridLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("text/plain")
                    ) { uri ->
                        if (uri != null && textGridPreview.isNotBlank()) {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        context.contentResolver.openOutputStream(uri)?.use { os ->
                                            os.write(textGridPreview.toByteArray(Charsets.UTF_8))
                                            os.flush()
                                            true
                                        } ?: false
                                    }.getOrDefault(false)
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) S.archive.savedSingleTextGridToast else S.archive.exportFailedShortToast,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    val saveSingleWordCollectionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
                    ) { uri ->
                        val wav = activeAudioFile
                        if (uri != null && wav != null && wav.exists()) {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    context.contentResolver.openOutputStream(uri)?.use { os ->
                                        PraatCollectionExporter.exportSingleWordPraatCollection(
                                            wavFile = wav,
                                            word = word,
                                            speaker = speaker,
                                            analysis = rawAnalysisResult,
                                            textGridConfig = singleWordTgConfig,
                                            targetStream = os
                                        )
                                    } ?: false
                                }
                                Toast.makeText(
                                    context,
                                    if (ok) S.archive.savedSinglePraatCollectionToast else S.archive.exportFailedShortToast,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = S.archive.praatExportSectionTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = S.archive.currentTextGridFormatLine.withArgs("summary" to singleWordTgConfig.shortSummaryLabel()),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (textGridPreview.isNotBlank()) {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("Praat TextGrid", textGridPreview))
                                        Toast.makeText(context, S.archive.copiedTextGridToast, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(S.common.copy, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (textGridPreview.isNotBlank()) {
                                        val spkCode = String.format(java.util.Locale.US, "SPK%02d", speaker?.id ?: 1L)
                                        val idxStr = String.format(java.util.Locale.US, "%03d", word.originalIndex + 1)
                                        val safeWord = word.text.replace(Regex("[/\\\\:*?\"<>|\\s]+"), "_").take(20)
                                        val fileName = "${spkCode}_${idxStr}_${safeWord}.TextGrid"
                                        if (DesktopAdaptiveState.isDesktopPlatform) {
                                            saveSingleWordTextGridLauncher.launch(fileName)
                                        } else {
                                            try {
                                                val cacheDir = File(context.cacheDir, "exports")
                                                cacheDir.mkdirs()
                                                val tgFile = File(cacheDir, fileName)
                                                tgFile.writeText(textGridPreview, Charsets.UTF_8)
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    tgFile
                                                )
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(
                                                    android.content.Intent.createChooser(shareIntent, S.archive.shareSingleTextGridTitle)
                                                )
                                            } catch (e: Exception) {
                                                Toast.makeText(context, S.archive.exportFailedMsgToast.withArgs("msg" to e.message), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("export_single_word_textgrid_button")
                            ) {
                                Text(".TextGrid", fontSize = 11.sp, maxLines = 1, softWrap = false)
                            }
                            if (activeAudioFile != null && activeAudioFile.exists()) {
                                FilledTonalButton(
                                    onClick = {
                                        val spkCode = String.format(java.util.Locale.US, "SPK%02d", speaker?.id ?: 1L)
                                        val idxStr = String.format(java.util.Locale.US, "%03d", word.originalIndex + 1)
                                        val safeWord = PraatCollectionExporter.sanitizePraatObjectName(word.text, "word").take(20)
                                        val fileName = "${spkCode}_${idxStr}_${safeWord}.Collection"
                                        if (DesktopAdaptiveState.isDesktopPlatform) {
                                            saveSingleWordCollectionLauncher.launch(fileName)
                                        } else {
                                            scope.launch {
                                                try {
                                                    val cacheDir = File(context.cacheDir, "exports")
                                                    cacheDir.mkdirs()
                                                    val colFile = File(cacheDir, fileName)
                                                    val ok = withContext(Dispatchers.IO) {
                                                        FileOutputStream(colFile).use { fos ->
                                                            PraatCollectionExporter.exportSingleWordPraatCollection(
                                                                wavFile = activeAudioFile,
                                                                word = word,
                                                                speaker = speaker,
                                                                analysis = rawAnalysisResult,
                                                                textGridConfig = singleWordTgConfig,
                                                                targetStream = fos
                                                            )
                                                        }
                                                    }
                                                    if (ok) {
                                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            colFile
                                                        )
                                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                            type = "application/octet-stream"
                                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(
                                                            android.content.Intent.createChooser(shareIntent, S.archive.shareSingleCollectionTitle)
                                                        )
                                                    } else {
                                                        Toast.makeText(context, S.archive.readAudioFailedToast, Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, S.archive.exportFailedMsgToast.withArgs("msg" to e.message), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("export_single_word_praat_collection_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text(".Collection", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = textGridPreview.ifBlank { S.archive.generatingTextGridPreview },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(14.dp)
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
    }
}

