package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.Project
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.SSmartImport
import com.example.i18n.withArgs
import com.example.ui.components.AnimatedDialogVisibility
import com.example.ui.components.AnimatedNullableDialogVisibility
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioDialogEnterAnimation
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioHorizontalScrollStrip
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.StudioMotion
import com.example.ui.components.StudioSegmentedControl
import com.example.ui.components.StudioSwitchRow
import com.example.ui.components.consumeDialogCardTaps
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.ui.components.studioAnimateSize
import com.example.ui.components.studioRotateOn
import com.example.utils.AudioWordPairingRow
import com.example.utils.ImportTargetMode
import com.example.utils.MergeAudioPolicy
import com.example.utils.PairingMatchKind
import com.example.utils.SmartImportDesktopDropEffect
import com.example.utils.SmartImportEngine
import com.example.utils.SmartImportPlanConfig
import com.example.utils.SmartImportProgress
import com.example.utils.StagedImportItem
import com.example.viewmodel.RecordingViewModel
import com.example.viewmodel.executeSmartImportPlan
import com.example.viewmodel.loadProjectWordsForSmartImport
import kotlinx.coroutines.launch

private enum class PairingPreviewFilter {
    ALL,
    MATCHED,
    AUTO_NEW,
    UNASSIGNED
}

/**
 * Full-screen Import Workspace Page supporting wordlists (.xlsx/.csv/.tsv/.txt/.docx/.json),
 * multi-format audio (.wav/.mp3/.m4a/.aac/.flac/.ogg/.aiff), prompt images, folders,
 * and project archives (.zip/.teproj) across both Mobile and Desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartImportWorkspaceScreen(
    viewModel: RecordingViewModel,
    initialUris: List<Uri> = emptyList(),
    initialMergeProject: Project? = null,
    onBack: () -> Unit,
    onImportCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val isDesktop = DesktopAdaptiveState.isDesktopPlatform

    val projects by viewModel.allProjects.collectAsState()
    val speakers by viewModel.allSpeakers.collectAsState()
    val currentSpeaker by viewModel.currentSpeaker.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    val sessionStagingDir = remember { SmartImportEngine.createSessionStagingDir(context) }
    val stagingCleanupGuard = remember {
        object {
            var isImportRunning: Boolean = false
            var cleanupRequestedOnDispose: Boolean = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (stagingCleanupGuard.isImportRunning) {
                stagingCleanupGuard.cleanupRequestedOnDispose = true
            } else {
                SmartImportEngine.cleanupSessionStagingDir(sessionStagingDir)
            }
        }
    }

    var stagedItems by remember { mutableStateOf<List<StagedImportItem>>(emptyList()) }
    var isScanningFiles by remember { mutableStateOf(false) }
    var isExecutingImport by remember { mutableStateOf(false) }
    var activeProgress by remember { mutableStateOf<SmartImportProgress?>(null) }
    var showFormatGuideDialog by remember { mutableStateOf(false) }

    var targetMode by remember(initialMergeProject?.id) {
        mutableStateOf(
            if (initialMergeProject != null) ImportTargetMode.MERGE_EXISTING_PROJECT
            else ImportTargetMode.CREATE_NEW_PROJECT
        )
    }
    var customProjectName by remember { mutableStateOf("") }
    var hasUserEditedProjectName by remember { mutableStateOf(false) }

    var selectedMergeProjectId by remember(projects, initialMergeProject?.id) {
        mutableStateOf(initialMergeProject?.id ?: currentProject?.id ?: projects.firstOrNull()?.id)
    }
    var mergeTargetWords by remember { mutableStateOf<List<Word>>(emptyList()) }

    var selectedSpeakerId by remember(speakers, currentSpeaker) {
        mutableStateOf(currentSpeaker?.id ?: speakers.firstOrNull()?.id)
    }
    val selectedSpeakerObj: Speaker? = remember(speakers, selectedSpeakerId) {
        speakers.firstOrNull { it.id == selectedSpeakerId } ?: speakers.firstOrNull()
    }

    var autoDetectSubfolderSpeakers by remember { mutableStateOf(true) }
    var mergeAudioPolicy by remember { mutableStateOf(MergeAudioPolicy.SAVE_AS_NEW_TAKE) }
    var autoCreateWordsForUnmatchedAudio by remember { mutableStateOf(true) }
    var pairBySequentialOrder by remember { mutableStateOf(false) }
    val manualWordOverrides = remember { mutableStateMapOf<String, String>() }

    var previewFilter by remember { mutableStateOf(PairingPreviewFilter.ALL) }
    var rebindTargetRow by remember { mutableStateOf<AudioWordPairingRow?>(null) }
    var mobilePairingsExpanded by remember { mutableStateOf(false) }
    var mobileStagedExpanded by remember { mutableStateOf(false) }
    var mobileShowAllPairings by remember { mutableStateOf(false) }
    var isDragHovering by remember { mutableStateOf(false) }

    val backVisualState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = !isExecutingImport && rebindTargetRow == null && !showFormatGuideDialog,
        state = backVisualState
    ) {
        if (!isExecutingImport) {
            onBack()
        }
    }

    // Load existing words whenever merge target project changes
    LaunchedEffect(targetMode, selectedMergeProjectId) {
        val pid = selectedMergeProjectId
        mergeTargetWords = if (targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT && pid != null) {
            viewModel.loadProjectWordsForSmartImport(pid)
        } else {
            emptyList()
        }
    }

    // Serialize URI staging batches so rapid drops/picks never overwrite each other
    val stagingQueueState = remember {
        object {
            val pendingBatches = ArrayDeque<List<Uri>>()
            var draining: Boolean = false
        }
    }

    fun appendUris(uris: List<Uri>) {
        if (uris.isEmpty() || isExecutingImport) return
        stagingQueueState.pendingBatches.addLast(uris)
        if (stagingQueueState.draining) return
        stagingQueueState.draining = true
        isScanningFiles = true
        scope.launch {
            try {
                while (stagingQueueState.pendingBatches.isNotEmpty()) {
                    val batch = stagingQueueState.pendingBatches.removeFirst()
                    val updated = SmartImportEngine.stageUrisIntoPool(
                        context = context,
                        sessionDir = sessionStagingDir,
                        existingStaged = stagedItems,
                        uris = batch
                    )
                    stagedItems = updated
                }
            } finally {
                stagingQueueState.draining = false
                isScanningFiles = false
            }
        }
    }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) {
            appendUris(initialUris)
        }
    }

    val multiDocsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            appendUris(uris)
        }
    }

    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            appendUris(listOf(treeUri))
        }
    }

    val planConfig = SmartImportPlanConfig(
        targetMode = targetMode,
        customNewProjectName = customProjectName,
        mergeTargetProjectId = selectedMergeProjectId,
        defaultSpeakerName = selectedSpeakerObj?.name ?: "默认发音人",
        autoDetectSubfolderSpeakers = autoDetectSubfolderSpeakers,
        mergeAudioPolicy = mergeAudioPolicy,
        autoCreateWordsForUnmatchedAudio = autoCreateWordsForUnmatchedAudio,
        pairBySequentialOrder = pairBySequentialOrder,
        manualWordOverrides = manualWordOverrides.toMap()
    )

    val plan = remember(stagedItems, mergeTargetWords, speakers, planConfig) {
        SmartImportEngine.buildImportPlan(
            stagedItems = stagedItems,
            existingWordsInMergeTarget = mergeTargetWords,
            knownSpeakers = speakers,
            config = planConfig
        )
    }

    // Auto-fill suggested project name when user hasn't typed a custom name yet
    LaunchedEffect(plan.suggestedProjectName, stagedItems.size) {
        if (!hasUserEditedProjectName && stagedItems.isNotEmpty()) {
            customProjectName = plan.suggestedProjectName
        } else if (stagedItems.isEmpty() && !hasUserEditedProjectName) {
            customProjectName = ""
        }
    }

    val filteredPairings = remember(plan.audioPairings, previewFilter) {
        when (previewFilter) {
            PairingPreviewFilter.ALL -> plan.audioPairings
            PairingPreviewFilter.MATCHED -> plan.audioPairings.filter {
                it.targetWordKey != null && it.matchKind != PairingMatchKind.AUTO_NEW_WORD
            }
            PairingPreviewFilter.AUTO_NEW -> plan.audioPairings.filter {
                it.matchKind == PairingMatchKind.AUTO_NEW_WORD
            }
            PairingPreviewFilter.UNASSIGNED -> plan.audioPairings.filter {
                it.targetWordKey == null
            }
        }
    }

    val matchedCount = remember(plan.audioPairings) {
        plan.audioPairings.count { it.targetWordKey != null && it.matchKind != PairingMatchKind.AUTO_NEW_WORD }
    }

    val canStartImport = !isExecutingImport && !isScanningFiles && (
        plan.archiveItems.isNotEmpty() ||
            (targetMode == ImportTargetMode.CREATE_NEW_PROJECT && (plan.candidateWords.isNotEmpty() || plan.readyAudioCount > 0)) ||
            (targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT && selectedMergeProjectId != null && (
                plan.candidateWords.any { it.existingWordId == null || it.stagedImageFile != null } || plan.readyAudioCount > 0
                ))
        )

    fun triggerImportExecution() {
        if (!canStartImport) return
        isExecutingImport = true
        stagingCleanupGuard.isImportRunning = true
        activeProgress = SmartImportProgress(0.05f, SSmartImport.progressScanningFiles)
        viewModel.executeSmartImportPlan(
            plan = plan,
            config = planConfig,
            defaultSpeakerId = selectedSpeakerObj?.id,
            onProgress = { prog -> activeProgress = prog },
            onFinished = { result ->
                stagingCleanupGuard.isImportRunning = false
                if (stagingCleanupGuard.cleanupRequestedOnDispose) {
                    SmartImportEngine.cleanupSessionStagingDir(sessionStagingDir)
                }
                isExecutingImport = false
                activeProgress = null
                Toast.makeText(context, result.summaryMessage, Toast.LENGTH_LONG).show()
                if (result.success) {
                    onImportCompleted()
                }
            }
        )
    }

    // Attach Desktop OS Drag-and-Drop while on the Import Page (unless a modal popup is open)
    SmartImportDesktopDropEffect(
        enabled = isDesktop && !isExecutingImport && rebindTargetRow == null && !showFormatGuideDialog,
        onHoverChanged = { isDragHovering = it },
        onDroppedUris = { uris -> appendUris(uris) }
    )

    val readySummary = remember(plan, targetMode, stagedItems.size) {
        if (stagedItems.isEmpty()) {
            SSmartImport.footerEmptySummary
        } else {
            val corpusWordCount = if (targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT) {
                plan.candidateWords.count { it.existingWordId == null }
            } else {
                plan.candidateWords.size
            }
            val archiveWordCount = plan.archiveItems.sumOf { it.archiveInspect?.estimatedWordCount ?: 0 }
            val archiveAudioCount = plan.archiveItems.sumOf { it.archiveInspect?.estimatedAudioCount ?: 0 }
            val totalWords = corpusWordCount + archiveWordCount
            val totalAudio = plan.readyAudioCount + archiveAudioCount
            val projPart = if (plan.archiveItems.isNotEmpty()) "${plan.archiveItems.size} 个项目包 · " else ""
            "就绪：${projPart}${totalWords} 个词条 · ${totalAudio} 条录音"
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        containerColor = cs.background,
        modifier = modifier
            .fillMaxSize()
            .predictiveBackLayer(backVisualState)
            .testTag("smart_import_workspace_page"),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { if (!isExecutingImport) onBack() },
                            enabled = !isExecutingImport,
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("smart_import_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = S.common.back
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = SSmartImport.hubTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = SSmartImport.hubSubtitle,
                                fontSize = 11.5.sp,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = { showFormatGuideDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .defaultMinSize(minHeight = 40.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("import_format_guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = SSmartImport.btnFormatExamples,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = SSmartImport.btnFormatExamples,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = cs.background
                    )
                )
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))
            }
        },
        bottomBar = {
            Surface(
                color = cs.surfaceContainerLow,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))
                    ResponsiveContentContainer(maxWidth = 1120.dp) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            val wideFooter = maxWidth >= 700.dp
                            val animatedProgFraction by animateFloatAsState(
                                targetValue = (activeProgress?.fraction ?: 0f).coerceIn(0f, 1f),
                                animationSpec = StudioMotion.mediumInTween(),
                                label = "SmartImportProgressBar"
                            )
                            var cachedStageMsg by remember { mutableStateOf("") }
                            activeProgress?.stageMessage?.let { cachedStageMsg = it }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AnimatedVisibility(
                                    visible = activeProgress != null,
                                    enter = StudioMotion.verticalPanelEnter,
                                    exit = StudioMotion.verticalPanelExit
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            text = activeProgress?.stageMessage ?: cachedStageMsg,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cs.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        LinearProgressIndicator(
                                            progress = { animatedProgFraction },
                                            color = cs.primary,
                                            trackColor = cs.surfaceContainerHighest,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(CircleShape)
                                        )
                                    }
                                }

                                if (wideFooter) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = readySummary,
                                            fontSize = 13.sp,
                                            fontWeight = if (stagedItems.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (stagedItems.isNotEmpty()) cs.onSurface else cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 16.dp)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            TextButton(
                                                onClick = onBack,
                                                enabled = !isExecutingImport,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 44.dp)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                            ) {
                                                Text(S.common.back, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                            }
                                            Button(
                                                onClick = { triggerImportExecution() },
                                                enabled = canStartImport,
                                                shape = RoundedCornerShape(12.dp),
                                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 44.dp)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .testTag("smart_import_confirm_button")
                                            ) {
                                                if (isExecutingImport) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = cs.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(SSmartImport.btnImporting, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                } else {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(SSmartImport.btnStartImport, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = readySummary,
                                        fontSize = 12.sp,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Button(
                                        onClick = { triggerImportExecution() },
                                        enabled = canStartImport,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("smart_import_confirm_button")
                                    ) {
                                        if (isExecutingImport) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = cs.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(SSmartImport.btnImporting, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        } else {
                                            Text(SSmartImport.btnStartImport, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideLayout = maxWidth >= 760.dp

            if (isWideLayout) {
                val stagedPoolListState = rememberLazyListState()
                val pairingPreviewListState = rememberLazyListState()

                ResponsiveContentContainer(
                    maxWidth = 1120.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Column (41%): Dropzone + Staged Files Tray
                        Column(
                            modifier = Modifier
                                .weight(0.41f)
                                .fillMaxHeight()
                                .background(cs.surfaceContainerLow.copy(alpha = 0.35f))
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SmartImportDropzoneBox(
                                isDesktop = isDesktop,
                                isDragHovering = isDragHovering,
                                isScanning = isScanningFiles,
                                enabled = !isExecutingImport,
                                onPickFiles = { multiDocsLauncher.launch(arrayOf("*/*")) },
                                onScanFolder = { folderTreeLauncher.launch(null) }
                            )

                            StagedCategorySummaryRow(
                                archiveCount = plan.archiveItems.size,
                                wordlistCount = plan.wordlistItems.size,
                                totalParsedWords = plan.wordlistItems.sumOf { it.parsedWords.size },
                                audioCount = plan.audioItems.size,
                                imageCount = plan.imageItems.size,
                                onClearAll = if (stagedItems.isNotEmpty() && !isExecutingImport) {
                                    {
                                        stagedItems = emptyList()
                                        manualWordOverrides.clear()
                                    }
                                } else null
                            )

                            AnimatedContent(
                                targetState = stagedItems.isEmpty(),
                                transitionSpec = { StudioMotion.subtleFadeScale() },
                                label = "StagedPoolEmptyOrList",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) { isPoolEmpty ->
                                if (isPoolEmpty) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = SSmartImport.stagedEmptyHint,
                                                fontSize = 12.5.sp,
                                                lineHeight = 19.sp,
                                                color = cs.onSurfaceVariant.copy(alpha = 0.85f)
                                            )
                                            TextButton(
                                                onClick = { showFormatGuideDialog = true },
                                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(SSmartImport.btnFormatExamples, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        LazyColumn(
                                            state = stagedPoolListState,
                                            contentPadding = PaddingValues(
                                                top = 2.dp,
                                                bottom = 2.dp,
                                                end = if (DesktopAdaptiveState.isDesktopPlatform) 11.dp else 0.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(items = stagedItems, key = { it.id }) { item ->
                                                StagedImportFileRow(
                                                    item = item,
                                                    enabled = !isExecutingImport,
                                                    onRemove = {
                                                        stagedItems = stagedItems.filterNot { it.id == item.id }
                                                        manualWordOverrides.remove(item.id)
                                                    },
                                                    modifier = studioAnimateItem()
                                                )
                                            }
                                        }
                                        PlatformVerticalScrollbar(
                                            listState = stagedPoolListState,
                                            modifier = Modifier.align(Alignment.CenterEnd)
                                        )
                                    }
                                }
                            }
                        }

                        VerticalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))

                        // Right Column (59%): Target Destination + Pairing Preview
                        Box(
                            modifier = Modifier
                                .weight(0.59f)
                                .fillMaxHeight()
                        ) {
                            LazyColumn(
                                state = pairingPreviewListState,
                                contentPadding = PaddingValues(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val onlyStandaloneArchives = plan.archiveItems.isNotEmpty() &&
                                    plan.wordlistItems.isEmpty() &&
                                    plan.audioItems.isEmpty() &&
                                    plan.imageItems.isEmpty()

                                if (plan.archiveItems.isNotEmpty()) {
                                    item(key = "archive_queue_card") {
                                        ArchiveQueuePreviewCard(
                                            archiveItems = plan.archiveItems,
                                            modifier = studioAnimateItem()
                                        )
                                    }
                                }

                                if (!onlyStandaloneArchives) {
                                    item(key = "target_config_section") {
                                        TargetDestinationSection(
                                            targetMode = targetMode,
                                            onTargetModeChanged = { targetMode = it },
                                            customProjectName = customProjectName,
                                            suggestedProjectName = plan.suggestedProjectName,
                                            onCustomProjectNameChanged = {
                                                customProjectName = it
                                                hasUserEditedProjectName = true
                                            },
                                            projects = projects,
                                            selectedMergeProjectId = selectedMergeProjectId,
                                            onSelectMergeProject = { selectedMergeProjectId = it },
                                            speakers = speakers,
                                            selectedSpeaker = selectedSpeakerObj,
                                            onSelectSpeaker = { selectedSpeakerId = it.id },
                                            mergeAudioPolicy = mergeAudioPolicy,
                                            onMergeAudioPolicyChanged = { mergeAudioPolicy = it },
                                            hasAudioItems = plan.audioItems.isNotEmpty(),
                                            hasBaseWordlist = plan.wordlistItems.isNotEmpty() ||
                                                (targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT && mergeTargetWords.isNotEmpty()),
                                            autoCreateWords = autoCreateWordsForUnmatchedAudio,
                                            onAutoCreateWordsChanged = { autoCreateWordsForUnmatchedAudio = it },
                                            pairSequential = pairBySequentialOrder,
                                            onPairSequentialChanged = { pairBySequentialOrder = it },
                                            autoDetectSpeakers = autoDetectSubfolderSpeakers,
                                            onAutoDetectSpeakersChanged = { autoDetectSubfolderSpeakers = it },
                                            enabled = !isExecutingImport
                                        )
                                    }
                                }

                                if (plan.audioItems.isNotEmpty()) {
                                    item(key = "pairing_header_and_filters") {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = SSmartImport.previewSectionTitle,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = cs.onSurface
                                            )
                                            val previewFilterScroll = rememberScrollState()
                                            StudioHorizontalScrollStrip(
                                                state = previewFilterScroll,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(previewFilterScroll),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    StudioFilterChip(
                                                        selected = previewFilter == PairingPreviewFilter.ALL,
                                                        onClick = { previewFilter = PairingPreviewFilter.ALL },
                                                        labelText = SSmartImport.filterAll.withArgs("count" to plan.audioPairings.size)
                                                    )
                                                    StudioFilterChip(
                                                        selected = previewFilter == PairingPreviewFilter.MATCHED,
                                                        onClick = { previewFilter = PairingPreviewFilter.MATCHED },
                                                        labelText = SSmartImport.filterMatched.withArgs("count" to matchedCount)
                                                    )
                                                    if (plan.autoCreatedWordCount > 0 || autoCreateWordsForUnmatchedAudio) {
                                                        val autoRows = plan.audioPairings.count { it.matchKind == PairingMatchKind.AUTO_NEW_WORD }
                                                        StudioFilterChip(
                                                            selected = previewFilter == PairingPreviewFilter.AUTO_NEW,
                                                            onClick = { previewFilter = PairingPreviewFilter.AUTO_NEW },
                                                            labelText = SSmartImport.filterAutoNew.withArgs("count" to autoRows)
                                                        )
                                                    }
                                                    if (plan.unassignedAudioCount > 0) {
                                                        StudioFilterChip(
                                                            selected = previewFilter == PairingPreviewFilter.UNASSIGNED,
                                                            onClick = { previewFilter = PairingPreviewFilter.UNASSIGNED },
                                                            isErrorVariant = true,
                                                            labelText = SSmartImport.filterUnassigned.withArgs("count" to plan.unassignedAudioCount)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    items(
                                        items = filteredPairings,
                                        key = { "pair_${it.audioItem.id}" }
                                    ) { row ->
                                        AudioPairingPreviewRow(
                                            row = row,
                                            enabled = !isExecutingImport,
                                            onClickRebind = { rebindTargetRow = row },
                                            modifier = studioAnimateItem()
                                        )
                                    }
                                } else if (plan.candidateWords.isNotEmpty()) {
                                    item(key = "wordlist_only_preview") {
                                        WordlistOnlyPreviewCard(
                                            candidateWords = plan.candidateWords,
                                            isMergeMode = targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT,
                                            modifier = studioAnimateItem()
                                        )
                                    }
                                } else if (!onlyStandaloneArchives) {
                                    item(key = "empty_preview_hint") {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = cs.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = SSmartImport.previewEmptyHint,
                                                fontSize = 12.5.sp,
                                                lineHeight = 19.sp,
                                                color = cs.onSurfaceVariant,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            PlatformVerticalScrollbar(
                                listState = pairingPreviewListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            } else {
                // Mobile / Compact Single-Column Scrollable Page
                val compactScrollState = rememberScrollState()
                ResponsiveContentContainer(
                    maxWidth = 680.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(compactScrollState)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SmartImportDropzoneBox(
                                isDesktop = isDesktop,
                                isDragHovering = isDragHovering,
                                isScanning = isScanningFiles,
                                enabled = !isExecutingImport,
                                onPickFiles = { multiDocsLauncher.launch(arrayOf("*/*")) },
                                onScanFolder = { folderTreeLauncher.launch(null) }
                            )

                    if (stagedItems.isNotEmpty()) {
                        StagedCategorySummaryRow(
                            archiveCount = plan.archiveItems.size,
                            wordlistCount = plan.wordlistItems.size,
                            totalParsedWords = plan.wordlistItems.sumOf { it.parsedWords.size },
                            audioCount = plan.audioItems.size,
                            imageCount = plan.imageItems.size,
                            onClearAll = if (!isExecutingImport) {
                                {
                                    stagedItems = emptyList()
                                    manualWordOverrides.clear()
                                }
                            } else null
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val visibleStaged = if (mobileStagedExpanded) stagedItems else stagedItems.take(6)
                            visibleStaged.forEach { item ->
                                StagedImportFileRow(
                                    item = item,
                                    enabled = !isExecutingImport,
                                    onRemove = {
                                        stagedItems = stagedItems.filterNot { it.id == item.id }
                                        manualWordOverrides.remove(item.id)
                                    }
                                )
                            }
                            if (stagedItems.size > 6) {
                                TextButton(
                                    onClick = { mobileStagedExpanded = !mobileStagedExpanded },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (mobileStagedExpanded) {
                                            "收起已添加列表（共 ${stagedItems.size} 个文件）"
                                        } else {
                                            "展开查看全部 ${stagedItems.size} 个已添加文件（还有 ${stagedItems.size - 6} 个）"
                                        },
                                        fontSize = 11.5.sp,
                                        color = cs.primary
                                    )
                                }
                            }
                        }
                    }

                    if (plan.archiveItems.isNotEmpty()) {
                        ArchiveQueuePreviewCard(archiveItems = plan.archiveItems)
                    }

                    val onlyStandaloneArchives = plan.archiveItems.isNotEmpty() &&
                        plan.wordlistItems.isEmpty() &&
                        plan.audioItems.isEmpty() &&
                        plan.imageItems.isEmpty()

                    if (!onlyStandaloneArchives) {
                        TargetDestinationSection(
                            targetMode = targetMode,
                            onTargetModeChanged = { targetMode = it },
                            customProjectName = customProjectName,
                            suggestedProjectName = plan.suggestedProjectName,
                            onCustomProjectNameChanged = {
                                customProjectName = it
                                hasUserEditedProjectName = true
                            },
                            projects = projects,
                            selectedMergeProjectId = selectedMergeProjectId,
                            onSelectMergeProject = { selectedMergeProjectId = it },
                            speakers = speakers,
                            selectedSpeaker = selectedSpeakerObj,
                            onSelectSpeaker = { selectedSpeakerId = it.id },
                            mergeAudioPolicy = mergeAudioPolicy,
                            onMergeAudioPolicyChanged = { mergeAudioPolicy = it },
                            hasAudioItems = plan.audioItems.isNotEmpty(),
                            hasBaseWordlist = plan.wordlistItems.isNotEmpty() ||
                                (targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT && mergeTargetWords.isNotEmpty()),
                            autoCreateWords = autoCreateWordsForUnmatchedAudio,
                            onAutoCreateWordsChanged = { autoCreateWordsForUnmatchedAudio = it },
                            pairSequential = pairBySequentialOrder,
                            onPairSequentialChanged = { pairBySequentialOrder = it },
                            autoDetectSpeakers = autoDetectSubfolderSpeakers,
                            onAutoDetectSpeakersChanged = { autoDetectSubfolderSpeakers = it },
                            enabled = !isExecutingImport
                        )
                    }

                    if (plan.audioItems.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cs.surfaceContainerLow,
                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { mobilePairingsExpanded = !mobilePairingsExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = SSmartImport.previewSectionTitle,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSurface
                                        )
                                        Text(
                                            text = if (mobilePairingsExpanded) {
                                                SSmartImport.collapsePairingsMobile
                                            } else {
                                                SSmartImport.expandPairingsMobile.withArgs("count" to plan.audioPairings.size)
                                            },
                                            fontSize = 11.5.sp,
                                            color = cs.primary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.studioRotateOn(mobilePairingsExpanded)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = mobilePairingsExpanded,
                                    enter = StudioMotion.verticalPanelEnter,
                                    exit = StudioMotion.verticalPanelExit
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val visiblePairings = if (mobileShowAllPairings) plan.audioPairings else plan.audioPairings.take(80)
                                        visiblePairings.forEach { row ->
                                            AudioPairingPreviewRow(
                                                row = row,
                                                enabled = !isExecutingImport,
                                                onClickRebind = { rebindTargetRow = row }
                                            )
                                        }
                                        if (plan.audioPairings.size > 80) {
                                            TextButton(
                                                onClick = { mobileShowAllPairings = !mobileShowAllPairings },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Text(
                                                    text = if (mobileShowAllPairings) {
                                                        "收起其余 ${plan.audioPairings.size - 80} 条配对明细"
                                                    } else {
                                                        "显示全部 ${plan.audioPairings.size} 条配对明细（当前仅展示前 80 条）"
                                                    },
                                                    fontSize = 11.5.sp,
                                                    color = cs.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (plan.candidateWords.isNotEmpty()) {
                        WordlistOnlyPreviewCard(
                            candidateWords = plan.candidateWords,
                            isMergeMode = targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT
                        )
                    }
                        }
                        PlatformVerticalScrollbar(
                            scrollState = compactScrollState,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }
    }

    // Format Examples Guide Popup (no input boxes -> clicking backdrop closes cleanly)
    AnimatedDialogVisibility(visible = showFormatGuideDialog) {
        ImportFormatGuideDialog(
            onDismiss = { showFormatGuideDialog = false }
        )
    }

    AnimatedNullableDialogVisibility(value = rebindTargetRow) { activeRebind ->
        WordRebindPickerDialog(
            row = activeRebind,
            candidateWords = plan.candidateWords,
            onSelectOverride = { chosenKey ->
                if (chosenKey == null) {
                    manualWordOverrides.remove(activeRebind.audioItem.id)
                } else {
                    manualWordOverrides[activeRebind.audioItem.id] = chosenKey
                }
                rebindTargetRow = null
            },
            onDismiss = { rebindTargetRow = null }
        )
    }
}

@Composable
private fun SmartImportDropzoneBox(
    isDesktop: Boolean,
    isDragHovering: Boolean,
    isScanning: Boolean,
    enabled: Boolean,
    onPickFiles: () -> Unit,
    onScanFolder: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    val bg by animateColorAsState(
        targetValue = when {
            isDragHovering -> cs.primaryContainer
            isHovered && enabled -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.25f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = tween(140),
        label = "DropzoneBg"
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            isDragHovering -> cs.primary
            isHovered && enabled -> cs.primary.copy(alpha = 0.55f)
            else -> cs.outlineVariant
        },
        animationSpec = tween(140),
        label = "DropzoneBorder"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bg,
        border = BorderStroke(if (isDragHovering) 2.dp else 1.2.dp, borderCol),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(hoverSrc)
            .testTag("smart_import_dropzone")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                Text(
                    text = SSmartImport.progressScanningFiles,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = when {
                        isDragHovering -> SSmartImport.dropzoneActiveHover
                        isDesktop -> SSmartImport.dropzoneHeadlineDesktop
                        else -> SSmartImport.dropzoneHeadlineMobile
                    },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDragHovering) cs.onPrimaryContainer else cs.onSurface
                )
                Text(
                    text = if (isDesktop) SSmartImport.dropzoneSublineDesktop else SSmartImport.dropzoneSublineMobile,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = cs.onSurfaceVariant
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stackButtons = maxWidth < 290.dp
                if (stackButtons) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onPickFiles,
                            enabled = enabled && !isScanning,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("smart_import_pick_files_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                SSmartImport.btnPickFiles,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onScanFolder,
                            enabled = enabled && !isScanning,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("smart_import_scan_folder_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                SSmartImport.btnScanFolder,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                    ) {
                        FilledTonalButton(
                            onClick = onPickFiles,
                            enabled = enabled && !isScanning,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("smart_import_pick_files_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                SSmartImport.btnPickFiles,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onScanFolder,
                            enabled = enabled && !isScanning,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("smart_import_scan_folder_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                SSmartImport.btnScanFolder,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StagedCategorySummaryRow(
    archiveCount: Int,
    wordlistCount: Int,
    totalParsedWords: Int,
    audioCount: Int,
    imageCount: Int,
    onClearAll: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val stagedSummaryScroll = rememberScrollState()
        StudioHorizontalScrollStrip(
            state = stagedSummaryScroll,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(stagedSummaryScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SSmartImport.stagedPoolTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurfaceVariant
                )
                if (archiveCount > 0) {
                    SummaryCountPill(
                        text = SSmartImport.badgeArchiveCount.withArgs("count" to archiveCount),
                        bg = cs.tertiaryContainer,
                        fg = cs.onTertiaryContainer
                    )
                }
                if (wordlistCount > 0) {
                    SummaryCountPill(
                        text = SSmartImport.badgeWordlistCount.withArgs(
                            "count" to wordlistCount,
                            "words" to totalParsedWords
                        ),
                        bg = cs.primaryContainer,
                        fg = cs.onPrimaryContainer
                    )
                }
                if (audioCount > 0) {
                    SummaryCountPill(
                        text = SSmartImport.badgeAudioCount.withArgs("count" to audioCount),
                        bg = cs.secondaryContainer,
                        fg = cs.onSecondaryContainer
                    )
                }
                if (imageCount > 0) {
                    SummaryCountPill(
                        text = SSmartImport.badgeImageCount.withArgs("count" to imageCount),
                        bg = cs.surfaceContainerHighest,
                        fg = cs.onSurfaceVariant
                    )
                }
            }
        }
        if (onClearAll != null) {
            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(SSmartImport.btnClearAll, fontSize = 11.5.sp, color = cs.error)
            }
        }
    }
}

@Composable
private fun SummaryCountPill(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(7.dp), color = bg) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
        )
    }
}

@Composable
private fun TargetDestinationSection(
    targetMode: ImportTargetMode,
    onTargetModeChanged: (ImportTargetMode) -> Unit,
    customProjectName: String,
    suggestedProjectName: String,
    onCustomProjectNameChanged: (String) -> Unit,
    projects: List<Project>,
    selectedMergeProjectId: Long?,
    onSelectMergeProject: (Long) -> Unit,
    speakers: List<Speaker>,
    selectedSpeaker: Speaker?,
    onSelectSpeaker: (Speaker) -> Unit,
    mergeAudioPolicy: MergeAudioPolicy,
    onMergeAudioPolicyChanged: (MergeAudioPolicy) -> Unit,
    hasAudioItems: Boolean,
    hasBaseWordlist: Boolean,
    autoCreateWords: Boolean,
    onAutoCreateWordsChanged: (Boolean) -> Unit,
    pairSequential: Boolean,
    onPairSequentialChanged: (Boolean) -> Unit,
    autoDetectSpeakers: Boolean,
    onAutoDetectSpeakersChanged: (Boolean) -> Unit,
    enabled: Boolean
) {
    val cs = MaterialTheme.colorScheme
    var showProjectDropdown by remember { mutableStateOf(false) }
    var showSpeakerDropdown by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .studioAnimateSize()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioSegmentedControl(
                options = listOf(SSmartImport.modeCreateNew, SSmartImport.modeMergeExisting),
                selectedIndex = if (targetMode == ImportTargetMode.CREATE_NEW_PROJECT) 0 else 1,
                onOptionSelected = { idx ->
                    if (enabled) {
                        onTargetModeChanged(
                            if (idx == 0) ImportTargetMode.CREATE_NEW_PROJECT
                            else ImportTargetMode.MERGE_EXISTING_PROJECT
                        )
                    }
                },
                optionTestTags = listOf("smart_import_mode_new", "smart_import_mode_merge")
            )

            AnimatedContent(
                targetState = targetMode,
                transitionSpec = { StudioMotion.subtleFadeScale() },
                label = "SmartImportTargetModeForm"
            ) { mode ->
                if (mode == ImportTargetMode.CREATE_NEW_PROJECT) {
                    OutlinedTextField(
                        value = customProjectName,
                        onValueChange = onCustomProjectNameChanged,
                        enabled = enabled,
                        label = { Text(SSmartImport.newProjectNameLabel, fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                suggestedProjectName.ifBlank { SSmartImport.newProjectNamePlaceholder },
                                fontSize = 12.5.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_import_project_name_input")
                    )
                } else {
                    val targetProj = projects.firstOrNull { it.id == selectedMergeProjectId }
                    if (projects.isEmpty()) {
                        Text(
                            text = SSmartImport.noExistingProjectsHint,
                            fontSize = 12.sp,
                            color = cs.error
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cs.surface,
                                border = BorderStroke(1.dp, cs.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable(enabled = enabled) { showProjectDropdown = true }
                                    .testTag("smart_import_merge_project_selector")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = cs.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = SSmartImport.selectExistingProjectLabel,
                                                fontSize = 10.5.sp,
                                                color = cs.onSurfaceVariant
                                            )
                                            Text(
                                                text = targetProj?.name ?: "",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = cs.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.studioRotateOn(showProjectDropdown)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showProjectDropdown,
                                onDismissRequest = { showProjectDropdown = false }
                            ) {
                                projects.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = p.name,
                                                fontWeight = if (p.id == selectedMergeProjectId) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onSelectMergeProject(p.id)
                                            showProjectDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (speakers.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cs.surface,
                        border = BorderStroke(1.dp, cs.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(enabled = enabled) { showSpeakerDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = cs.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = SSmartImport.defaultSpeakerLabel,
                                        fontSize = 10.5.sp,
                                        color = cs.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedSpeaker?.let { "${it.name} (${it.gender} · ${it.age}岁)" } ?: "",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.studioRotateOn(showSpeakerDropdown)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSpeakerDropdown,
                        onDismissRequest = { showSpeakerDropdown = false }
                    ) {
                        speakers.forEach { spk ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${spk.name} (${spk.gender} · ${spk.age}岁)",
                                        fontWeight = if (spk.id == selectedSpeaker?.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSelectSpeaker(spk)
                                    showSpeakerDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = hasAudioItems,
                enter = StudioMotion.verticalPanelEnter,
                exit = StudioMotion.verticalPanelExit
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedVisibility(
                        visible = targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT,
                        enter = StudioMotion.verticalPanelEnter,
                        exit = StudioMotion.verticalPanelExit
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = SSmartImport.mergePolicyTitle,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurfaceVariant
                            )
                            val mergePolicyScroll = rememberScrollState()
                            StudioHorizontalScrollStrip(
                                state = mergePolicyScroll,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(mergePolicyScroll),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StudioFilterChip(
                                        selected = mergeAudioPolicy == MergeAudioPolicy.SAVE_AS_NEW_TAKE,
                                        onClick = { onMergeAudioPolicyChanged(MergeAudioPolicy.SAVE_AS_NEW_TAKE) },
                                        enabled = enabled,
                                        labelText = SSmartImport.mergePolicyNewTake
                                    )
                                    StudioFilterChip(
                                        selected = mergeAudioPolicy == MergeAudioPolicy.ONLY_FILL_UNRECORDED,
                                        onClick = { onMergeAudioPolicyChanged(MergeAudioPolicy.ONLY_FILL_UNRECORDED) },
                                        enabled = enabled,
                                        labelText = SSmartImport.mergePolicyOnlyEmpty
                                    )
                                }
                            }
                        }
                    }

                    StudioSwitchRow(
                        title = SSmartImport.autoCreateWordsTitle,
                        subtitle = SSmartImport.autoCreateWordsSub,
                        checked = autoCreateWords,
                        onCheckedChange = onAutoCreateWordsChanged,
                        enabled = enabled,
                        elevatedSurface = true,
                        compact = true,
                        switchTestTag = "smart_import_auto_create_words_switch"
                    )

                    AnimatedVisibility(
                        visible = hasBaseWordlist,
                        enter = StudioMotion.verticalPanelEnter,
                        exit = StudioMotion.verticalPanelExit
                    ) {
                        StudioSwitchRow(
                            title = SSmartImport.pairSequentialTitle,
                            subtitle = SSmartImport.pairSequentialSub,
                            checked = pairSequential,
                            onCheckedChange = onPairSequentialChanged,
                            enabled = enabled,
                            elevatedSurface = true,
                            compact = true
                        )
                    }

                    StudioSwitchRow(
                        title = SSmartImport.autoSpeakerSubfolderSwitchTitle,
                        subtitle = SSmartImport.autoSpeakerSubfolderSwitchSub,
                        checked = autoDetectSpeakers,
                        onCheckedChange = onAutoDetectSpeakersChanged,
                        enabled = enabled,
                        elevatedSurface = true,
                        compact = true
                    )
                }
            }
        }
    }
}
