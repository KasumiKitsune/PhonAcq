package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.example.ui.components.AdaptiveModalSurface
import com.example.ui.components.AnimatedDialogVisibility
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.LocalAppWindowMetrics
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.StudioMotion
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.ui.components.studioTactile
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.consumeDialogCardTaps
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: RecordingViewModel,
    onProjectOpened: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenWirelessLink: (() -> Unit)? = null,
    onOpenWordListEditor: ((Project?) -> Unit)? = null,
    onOpenImportPage: ((List<android.net.Uri>, Project?) -> Unit)? = null,
    openSpeakerManagerTrigger: Int = 0,
    onSpeakerManagerTriggerConsumed: () -> Unit = {},
    isSplitPaneMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.allProjects.collectAsState()
    val countsMap by viewModel.projectCounts.collectAsState()
    val summariesMap by viewModel.projectSummaries.collectAsState()
    val speakerGroups by viewModel.speakerGroups.collectAsState()
    val speakers by viewModel.allSpeakers.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()

    val context = LocalContext.current
    var showSpeakerManagerScreen by remember { mutableStateOf(false) }
    var initialSpeakerGroupFilter by remember { mutableStateOf<String?>(null) }
    var selectedProjectForDetails by remember { mutableStateOf<Project?>(null) }
    var showSmartImportHub by remember { mutableStateOf(false) }
    var pendingSmartImportUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var pendingMergeTargetProject by remember { mutableStateOf<Project?>(null) }
    var isGlobalDropHovering by remember { mutableStateOf(false) }

    fun launchImportWorkspace(
        uris: List<android.net.Uri> = emptyList(),
        mergeProject: Project? = null
    ) {
        if (onOpenImportPage != null) {
            onOpenImportPage(uris, mergeProject)
        } else {
            pendingSmartImportUris = uris
            pendingMergeTargetProject = mergeProject
            showSmartImportHub = true
        }
    }

    SuspendDesktopShortcutsWhen(showSpeakerManagerScreen || selectedProjectForDetails != null || showSmartImportHub)

    val canAcceptHomeGlobalDrop = onOpenImportPage == null &&
        com.example.ui.components.DesktopAdaptiveState.isDesktopPlatform &&
        !showSmartImportHub &&
        !showSpeakerManagerScreen &&
        selectedProjectForDetails == null &&
        com.example.ui.components.DesktopModalManager.entries.isEmpty()

    com.example.utils.SmartImportDesktopDropEffect(
        enabled = canAcceptHomeGlobalDrop,
        onHoverChanged = { isGlobalDropHovering = it },
        onDroppedUris = { dropped ->
            if (dropped.isNotEmpty()) {
                launchImportWorkspace(uris = dropped, mergeProject = null)
            }
        }
    )

    val wireless = viewModel.wirelessLink
    val isLinkConnected by wireless.isConnected.collectAsState()
    val pendingSyncCount by wireless.pendingSyncCount.collectAsState()
    val capsulesRefreshVersion by wireless.capsulesRefreshVersion.collectAsState()
    var homeCapsuleReloadTick by remember { mutableIntStateOf(0) }
    var wirelessCapsules by remember { mutableStateOf<List<com.example.link.WirelessSessionCapsuleSummary>>(emptyList()) }
    var homeCapsulePendingExport by remember { mutableStateOf<com.example.link.WirelessSessionCapsuleSummary?>(null) }
    var homeCapsulePendingDelete by remember { mutableStateOf<com.example.link.WirelessSessionCapsuleSummary?>(null) }

    LaunchedEffect(capsulesRefreshVersion, homeCapsuleReloadTick, pendingSyncCount, isLinkConnected) {
        wirelessCapsules = viewModel.listWirelessSessionCapsules()
    }

    val exportHomeCapsuleZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        val targetCapsule = homeCapsulePendingExport
        homeCapsulePendingExport = null
        if (targetUri != null && targetCapsule != null) {
            viewModel.exportWirelessCapsuleToUri(targetCapsule.capsuleDir, targetUri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(openSpeakerManagerTrigger) {
        if (openSpeakerManagerTrigger > 0) {
            initialSpeakerGroupFilter = null
            showSpeakerManagerScreen = true
            onSpeakerManagerTriggerConsumed()
        }
    }

    val windowMetrics = LocalAppWindowMetrics.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useModalSpeakerManager = maxWidth < 480.dp && windowMetrics.isExpandedOrWider

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (onOpenDrawer != null && !isSplitPaneMode) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier.testTag("open_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = S.nav.openDrawerMenu
                                )
                            }
                        }
                    },
                    title = {
                        if (isSplitPaneMode) {
                            Text(
                                text = S.project.screenTitle,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PhonAcqLogo(
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "PhonAcq",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { launchImportWorkspace() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("import_zip_archive_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = com.example.i18n.SSmartImport.entryButtonLabel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = com.example.i18n.SSmartImport.entryButtonLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (onOpenWirelessLink != null) {
                            IconButton(
                                onClick = onOpenWirelessLink,
                                modifier = Modifier.testTag("wireless_link_button")
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Devices,
                                    contentDescription = S.nav.wirelessLink
                                )
                            }
                        }
                        if (onOpenSettings != null && !isSplitPaneMode) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = S.nav.settings
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { onOpenWordListEditor?.invoke(null) },
                        icon = { Icon(Icons.Default.Add, contentDescription = S.project.newProject) },
                        text = {
                            Text(
                                text = S.project.newProject,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("create_project_fab")
                    )
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    val showWirelessHomeRescue = !isLinkConnected && (pendingSyncCount > 0 || wirelessCapsules.isNotEmpty())
                    val isEmptyHome = projects.isEmpty() && speakers.isEmpty()

                    AnimatedContent(
                        targetState = isEmptyHome,
                        transitionSpec = { StudioMotion.subtleFadeScale() },
                        label = "ProjectHomeEmptyOrList",
                        modifier = Modifier.fillMaxSize()
                    ) { emptyHome ->
                        if (emptyHome) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (showWirelessHomeRescue) {
                                    WirelessHomeBackupBanner(
                                        pendingSyncCount = pendingSyncCount,
                                        capsules = wirelessCapsules,
                                        onRescuePending = {
                                            viewModel.rescueWirelessPendingToCapsule { count ->
                                                homeCapsuleReloadTick++
                                                Toast.makeText(
                                                    context,
                                                    if (count > 0) {
                                                        S.wireless.savedCountToLocalToast.withArgs("count" to count)
                                                    } else {
                                                        S.wireless.noPendingToSaveToast
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onImportCapsule = { cap ->
                                            viewModel.importWirelessCapsuleAsProject(cap.capsuleDir) { ok, msg ->
                                                homeCapsuleReloadTick++
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                if (ok) {
                                                    onProjectOpened()
                                                }
                                            }
                                        },
                                        onExportCapsuleZip = { cap ->
                                            homeCapsulePendingExport = cap
                                            val safeName = S.wireless.capsuleZipFilename
                                                .withArgs("project" to cap.projectName, "speaker" to cap.speakerName)
                                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                            exportHomeCapsuleZipLauncher.launch(safeName)
                                        },
                                        onDeleteCapsule = { cap ->
                                            homeCapsulePendingDelete = cap
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = S.project.emptyIllustrationDesc,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = S.project.emptyTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isSimpleMode) {
                                        S.project.emptyHintSimple
                                    } else {
                                        S.project.emptyHintAdv
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { launchImportWorkspace() },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .testTag("empty_state_import_zip_button")
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(com.example.i18n.SSmartImport.hubTitle, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        } else {
                            val totalAllRecordings = summariesMap.values.sumOf { it.totalRecordingsCount }
                            val distinctGroupsCount = speakerGroups.size
                            val projectListState = rememberLazyListState()

                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = projectListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (showWirelessHomeRescue) {
                                        item(key = "wireless_home_backup_banner") {
                                            WirelessHomeBackupBanner(
                                                pendingSyncCount = pendingSyncCount,
                                                capsules = wirelessCapsules,
                                                onRescuePending = {
                                                    viewModel.rescueWirelessPendingToCapsule { count ->
                                                        homeCapsuleReloadTick++
                                                        Toast.makeText(
                                                            context,
                                                            if (count > 0) {
                                                                S.wireless.savedCountToLocalToast.withArgs("count" to count)
                                                            } else {
                                                                S.wireless.noPendingToSaveToast
                                                            },
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                                onImportCapsule = { cap ->
                                                    viewModel.importWirelessCapsuleAsProject(cap.capsuleDir) { ok, msg ->
                                                        homeCapsuleReloadTick++
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                        if (ok) {
                                                            onProjectOpened()
                                                        }
                                                    }
                                                },
                                                onExportCapsuleZip = { cap ->
                                                    homeCapsulePendingExport = cap
                                                    val safeName = S.wireless.capsuleZipFilename
                                                        .withArgs("project" to cap.projectName, "speaker" to cap.speakerName)
                                                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                                    exportHomeCapsuleZipLauncher.launch(safeName)
                                                },
                                                onDeleteCapsule = { cap ->
                                                    homeCapsulePendingDelete = cap
                                                }
                                            )
                                        }
                                    }
                                    // Material You Top Action Console (Speaker Roster & Groups + Corpus Storage)
                                    item(key = "top_action_console") {
                                        val cs = MaterialTheme.colorScheme
                                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                            val isCompactSidebar = maxWidth < 285.dp
                                            val (speakerCardSrc, speakerCardHoveredState) = rememberStudioInteraction()
                                            val speakerCardHovered by speakerCardHoveredState
                                            val speakerCardBg by animateColorAsState(
                                                targetValue = if (speakerCardHovered) {
                                                    lerp(cs.secondaryContainer.copy(alpha = 0.78f), cs.primaryContainer, 0.35f)
                                                } else {
                                                    cs.secondaryContainer.copy(alpha = 0.75f)
                                                },
                                                animationSpec = StudioMotion.microTween(),
                                                label = "ConsoleSpeakerCardBg"
                                            )
                                            val speakerCardBorder by animateColorAsState(
                                                targetValue = if (speakerCardHovered) {
                                                    cs.primary.copy(alpha = 0.45f)
                                                } else {
                                                    cs.primary.copy(alpha = 0f)
                                                },
                                                animationSpec = StudioMotion.microTween(),
                                                label = "ConsoleSpeakerCardBorder"
                                            )

                                            val (statsCardSrc, statsCardHoveredState) = rememberStudioInteraction()
                                            val statsCardHovered by statsCardHoveredState
                                            val statsCardBg by animateColorAsState(
                                                targetValue = if (statsCardHovered) {
                                                    lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.32f)
                                                } else {
                                                    cs.surfaceContainerLow
                                                },
                                                animationSpec = StudioMotion.microTween(),
                                                label = "ConsoleStatsCardBg"
                                            )
                                            val statsCardBorder by animateColorAsState(
                                                targetValue = if (statsCardHovered) {
                                                    cs.primary.copy(alpha = 0.45f)
                                                } else {
                                                    cs.outlineVariant.copy(alpha = 0.35f)
                                                },
                                                animationSpec = StudioMotion.microTween(),
                                                label = "ConsoleStatsCardBorder"
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Left Card: Speaker Roster & Groups
                                                Surface(
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = speakerCardBg,
                                                    border = BorderStroke(1.dp, speakerCardBorder),
                                                    modifier = Modifier
                                                        .weight(1.14f)
                                                        .studioTactile(speakerCardSrc, pressScale = 0.975f)
                                                        .clip(RoundedCornerShape(20.dp))
                                                        .hoverable(speakerCardSrc)
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .clickable(
                                                            interactionSource = speakerCardSrc,
                                                            indication = LocalIndication.current
                                                        ) {
                                                            initialSpeakerGroupFilter = null
                                                            showSpeakerManagerScreen = true
                                                        }
                                                        .testTag("speaker_archives_manager_card")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            if (!isCompactSidebar) {
                                                                Surface(
                                                                    shape = CircleShape,
                                                                    color = cs.secondary,
                                                                    modifier = Modifier.size(30.dp)
                                                                ) {
                                                                    Box(contentAlignment = Alignment.Center) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Groups,
                                                                            contentDescription = null,
                                                                            tint = cs.onSecondary,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Text(
                                                                    text = S.speaker.managerTitle,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    fontSize = 13.5.sp,
                                                                    color = cs.onSecondaryContainer,
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = if (!isSimpleMode && distinctGroupsCount > 0) {
                                                                        S.speaker.allSpeakersStatsPill.withArgs(
                                                                            "speakers" to speakers.size,
                                                                            "groups" to distinctGroupsCount
                                                                        )
                                                                    } else {
                                                                        S.project.speakerCountSimpleSummary.withArgs("count" to speakers.size)
                                                                    },
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = cs.onSecondaryContainer.copy(alpha = 0.8f),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        Icon(
                                                            imageVector = Icons.Default.ChevronRight,
                                                            contentDescription = null,
                                                            tint = cs.onSecondaryContainer,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                if (!isSimpleMode) {
                                                    // Right Card: Global Recording Overview & Cleanup
                                                    Surface(
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = statsCardBg,
                                                        border = BorderStroke(1.dp, statsCardBorder),
                                                        modifier = Modifier
                                                            .weight(0.86f)
                                                            .studioTactile(statsCardSrc, pressScale = 0.975f)
                                                            .clip(RoundedCornerShape(20.dp))
                                                            .hoverable(statsCardSrc)
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .clickable(
                                                                interactionSource = statsCardSrc,
                                                                indication = LocalIndication.current
                                                            ) {
                                                                viewModel.setShowGlobalManagement(true)
                                                            }
                                                            .testTag("current_speaker_card")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Column(
                                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Text(
                                                                    text = S.project.overviewRecordedCount.withArgs("count" to totalAllRecordings),
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    fontSize = 13.5.sp,
                                                                    color = cs.primary,
                                                                    maxLines = 1,
                                                                    softWrap = false,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = S.project.overviewStatsAndCleanup,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = cs.onSurfaceVariant,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                            if (!isCompactSidebar) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Analytics,
                                                                    contentDescription = null,
                                                                    tint = cs.primary.copy(alpha = 0.85f),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Section Header
                                    item(key = "wordlist_section_header") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = S.project.wordlistListHeader.withArgs("count" to projects.size),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = S.project.tapSpeakerHint,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Multi-Speaker Projects Listing
                                    items(
                                        items = projects,
                                        key = { it.id },
                                        contentType = { "project_card" }
                                    ) { project ->
                                        val counts = countsMap[project.id] ?: (0 to 0)
                                        val summary = summariesMap[project.id]

                                        ProjectItemCard(
                                            project = project,
                                            totalWords = summary?.totalWords ?: counts.first,
                                            summary = summary,
                                            isSimpleMode = isSimpleMode,
                                            onClick = remember(project.id) {
                                                {
                                                    viewModel.selectProject(project)
                                                    onProjectOpened()
                                                }
                                            },
                                            onSpeakerChipClick = remember(project.id) {
                                                { clickedSpeaker ->
                                                    viewModel.selectProjectAndSpeaker(project, clickedSpeaker)
                                                    onProjectOpened()
                                                }
                                            },
                                            onManageGroupSpeakers = remember(project.id, project.speakerGroupName, isSimpleMode) {
                                                {
                                                    initialSpeakerGroupFilter = if (isSimpleMode) null else project.speakerGroupName.takeIf { it.isNotBlank() }
                                                    showSpeakerManagerScreen = true
                                                }
                                            },
                                            onLongClick = remember(project.id) {
                                                {
                                                    selectedProjectForDetails = project
                                                }
                                            },
                                            onEditDetails = remember(project.id) {
                                                {
                                                    if (onOpenWordListEditor != null) {
                                                        onOpenWordListEditor(project)
                                                    } else {
                                                        selectedProjectForDetails = project
                                                    }
                                                }
                                            },
                                            onDelete = remember(project.id) {
                                                {
                                                    viewModel.deleteProject(project)
                                                }
                                            },
                                            modifier = studioAnimateItem()
                                        )
                                    }
                                }
                                PlatformVerticalScrollbar(
                                    listState = projectListState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }

                com.example.ui.components.AnimatedNullableDialogVisibility(value = selectedProjectForDetails) { targetProj ->
                    ProjectDetailEditDialog(
                        viewModel = viewModel,
                        project = targetProj,
                        speakerGroups = speakerGroups,
                        summary = summariesMap[targetProj.id],
                        onDismiss = { selectedProjectForDetails = null },
                        onOpenInWordEditor = if (onOpenWordListEditor != null) {
                            {
                                selectedProjectForDetails = null
                                onOpenWordListEditor(targetProj)
                            }
                        } else null,
                        onOpenSmartMergeImport = {
                            selectedProjectForDetails = null
                            launchImportWorkspace(uris = emptyList(), mergeProject = targetProj)
                        },
                        onUpdate = { name, desc, groupName ->
                            viewModel.updateProjectDetails(targetProj, name, desc, groupName) {
                                selectedProjectForDetails = null
                            }
                        }
                    )
                }
            }

        // Narrow sidebar inside wide desktop/tablet window: open SpeakerManager in centered modal!
        AnimatedDialogVisibility(visible = showSpeakerManagerScreen && useModalSpeakerManager) {
            StudioModalDialog(
                onDismissRequest = {
                    showSpeakerManagerScreen = false
                    initialSpeakerGroupFilter = null
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                AdaptiveModalSurface(
                    targetMaxWidth = 760.dp,
                    maxHeightFraction = 0.88f,
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SpeakerManagerPage(
                            viewModel = viewModel,
                            initialGroupFilter = initialSpeakerGroupFilter,
                            onBack = {
                                showSpeakerManagerScreen = false
                                initialSpeakerGroupFilter = null
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSpeakerManagerScreen && !useModalSpeakerManager,
            enter = slideInHorizontally(StudioMotion.pageInTween()) { width -> width / 3 } + fadeIn(StudioMotion.pageInTween()),
            exit = slideOutHorizontally(StudioMotion.pageOutTween()) { width -> width / 3 } + fadeOut(StudioMotion.pageOutTween()),
            modifier = Modifier.fillMaxSize()
        ) {
            SpeakerManagerPage(
                viewModel = viewModel,
                initialGroupFilter = initialSpeakerGroupFilter,
                onBack = {
                    showSpeakerManagerScreen = false
                    initialSpeakerGroupFilter = null
                }
            )
        }

        com.example.ui.components.AnimatedNullableDialogVisibility(value = homeCapsulePendingDelete) { targetCap ->
            com.example.ui.components.StudioAlertDialog(
                onDismissRequest = { homeCapsulePendingDelete = null },
                title = { Text(S.wireless.deleteCapsuleDialogTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        S.wireless.deleteCapsuleDialogBody.withArgs(
                            "project" to targetCap.projectName,
                            "speaker" to targetCap.speakerName,
                            "count" to targetCap.recordedTakeCount
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val dir = targetCap.capsuleDir
                            homeCapsulePendingDelete = null
                            viewModel.deleteWirelessCapsule(dir) {
                                homeCapsuleReloadTick++
                                Toast.makeText(context, S.wireless.deletedToast, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(S.common.delete)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { homeCapsulePendingDelete = null }) {
                        Text(S.common.cancel)
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = showSmartImportHub,
            enter = slideInHorizontally(StudioMotion.pageInTween()) { width -> width / 3 } + fadeIn(StudioMotion.pageInTween()),
            exit = slideOutHorizontally(StudioMotion.pageOutTween()) { width -> width / 3 } + fadeOut(StudioMotion.pageOutTween()),
            modifier = Modifier.fillMaxSize()
        ) {
            SmartImportWorkspaceScreen(
                viewModel = viewModel,
                initialUris = pendingSmartImportUris,
                initialMergeProject = pendingMergeTargetProject,
                onBack = {
                    showSmartImportHub = false
                    pendingSmartImportUris = emptyList()
                    pendingMergeTargetProject = null
                },
                onImportCompleted = {
                    showSmartImportHub = false
                    pendingSmartImportUris = emptyList()
                    pendingMergeTargetProject = null
                    onProjectOpened()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = isGlobalDropHovering,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    tonalElevation = 4.dp,
                    modifier = Modifier.widthIn(max = 560.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = com.example.i18n.SSmartImport.globalDropBannerText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectItemCard(
    project: Project,
    totalWords: Int,
    summary: ProjectMultiSpeakerSummary?,
    isSimpleMode: Boolean = false,
    onClick: () -> Unit,
    onSpeakerChipClick: (Speaker) -> Unit,
    onManageGroupSpeakers: () -> Unit,
    onLongClick: () -> Unit,
    onEditDetails: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateStr = remember(project.createdAt) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(project.createdAt))
    }

    val speakerProgressList = summary?.speakerProgressList ?: emptyList()
    val startedSpeakers = summary?.startedSpeakerCount ?: 0
    val completedSpeakers = summary?.completedSpeakerCount ?: 0
    val candidateCount = summary?.candidateSpeakerCount ?: 0
    val totalTokens = summary?.totalRecordingsCount ?: 0

    // Overall progress across candidate speakers who have started, or across the most progressed speaker
    val displayProgress = remember(summary, totalWords) {
        if (totalWords <= 0 || speakerProgressList.isEmpty()) {
            0f
        } else {
            val activeList = speakerProgressList.filter { it.recordedWords > 0 }
            if (activeList.isEmpty()) {
                0f
            } else {
                val sumRecorded = activeList.sumOf { it.recordedWords }
                (sumRecorded.toFloat() / (activeList.size * totalWords)).coerceIn(0f, 1f)
            }
        }
    }

    val (cardHoverSrc, isCardHoveredState) = rememberStudioInteraction()
    val isCardHovered by isCardHoveredState
    val isCardPressed by cardHoverSrc.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "projectCardPressScale_${project.id}"
    )
    val cardBgColor by animateColorAsState(
        targetValue = when {
            isCardPressed -> lerp(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.primaryContainer,
                0.42f
            )
            isCardHovered -> lerp(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.primaryContainer,
                0.28f
            )
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(130),
        label = "projectCardBg_${project.id}"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isCardPressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            isCardHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        animationSpec = tween(130),
        label = "projectCardBorder_${project.id}"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "projectCardProgress_${project.id}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(RoundedCornerShape(24.dp))
            .hoverable(cardHoverSrc)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(
                interactionSource = cardHoverSrc,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(if (isCardPressed || isCardHovered) 1.5.dp else 1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Title, Badges, Edit & Delete Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = S.project.cardWordCount.withArgs("count" to totalWords),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (!isSimpleMode && project.speakerGroupName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GroupWork,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = project.speakerGroupName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (project.description.isNotEmpty()) {
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 19.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cs = MaterialTheme.colorScheme
                    val (editSrc, editHoveredState) = rememberStudioInteraction()
                    val editHovered by editHoveredState
                    val editTint by animateColorAsState(
                        targetValue = if (editHovered) cs.primary else cs.primary.copy(alpha = 0.85f),
                        animationSpec = StudioMotion.microTween(),
                        label = "ProjEditIconTint"
                    )
                    IconButton(
                        onClick = onEditDetails,
                        interactionSource = editSrc,
                        modifier = Modifier
                            .size(36.dp)
                            .hoverable(editSrc)
                            .studioTactile(editSrc, pressScale = 0.90f)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = S.project.cardEditWordsDesc,
                            tint = editTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    val (delSrc, delHoveredState) = rememberStudioInteraction()
                    val delHovered by delHoveredState
                    val delTint by animateColorAsState(
                        targetValue = if (delHovered) cs.error else cs.outline,
                        animationSpec = StudioMotion.microTween(),
                        label = "ProjDelIconTint"
                    )
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        interactionSource = delSrc,
                        modifier = Modifier
                            .size(36.dp)
                            .hoverable(delSrc)
                            .studioTactile(delSrc, pressScale = 0.90f)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .testTag("delete_project_${project.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = S.project.cardDeleteProjectDesc,
                            tint = delTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Middle Row: Direct Multi-Speaker Progress Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(
                        items = speakerProgressList,
                        key = { it.speaker.id },
                        contentType = { "speaker_progress_chip" }
                    ) { spkInfo ->
                        val cs = MaterialTheme.colorScheme
                        val isDone = spkInfo.isCompleted
                        val isStarted = spkInfo.hasStarted
                        val (chipSrc, chipHoveredState) = rememberStudioInteraction()
                        val chipHovered by chipHoveredState
                        val baseBg = when {
                            isDone -> cs.secondaryContainer
                            isStarted -> cs.primaryContainer
                            else -> cs.surfaceContainerHigh
                        }
                        val chipBg by animateColorAsState(
                            targetValue = if (chipHovered) lerp(baseBg, cs.primary, 0.16f) else baseBg,
                            animationSpec = StudioMotion.microTween(),
                            label = "SpeakerChipBg_${spkInfo.speaker.id}"
                        )
                        val chipBorder by animateColorAsState(
                            targetValue = if (chipHovered) cs.primary.copy(alpha = 0.55f) else cs.primary.copy(alpha = 0f),
                            animationSpec = StudioMotion.microTween(),
                            label = "SpeakerChipBorder_${spkInfo.speaker.id}"
                        )
                        val chipContent = when {
                            isDone -> cs.onSecondaryContainer
                            isStarted -> cs.onPrimaryContainer
                            else -> cs.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = chipBg,
                            border = BorderStroke(1.dp, chipBorder),
                            modifier = studioAnimateItem()
                                .studioTactile(chipSrc, pressScale = 0.96f)
                                .clip(RoundedCornerShape(12.dp))
                                .hoverable(chipSrc)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = chipSrc,
                                    indication = LocalIndication.current
                                ) { onSpeakerChipClick(spkInfo.speaker) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        isDone -> Icons.Default.CheckCircle
                                        isStarted -> Icons.Default.GraphicEq
                                        else -> Icons.Default.PersonOutline
                                    },
                                    contentDescription = null,
                                    tint = chipContent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = spkInfo.speaker.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = chipContent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                                Text(
                                    text = "${spkInfo.recordedWords}/$totalWords",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = chipContent.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Quick "+ 发音人" chip
                    item(key = "add_speaker_chip") {
                        val cs = MaterialTheme.colorScheme
                        val (addSpkSrc, addSpkHoveredState) = rememberStudioInteraction()
                        val addSpkHovered by addSpkHoveredState
                        val addBg by animateColorAsState(
                            targetValue = if (addSpkHovered) lerp(cs.surfaceContainer, cs.primaryContainer, 0.45f) else cs.surfaceContainer,
                            animationSpec = StudioMotion.microTween(),
                            label = "AddSpeakerChipBg"
                        )
                        val addBorder by animateColorAsState(
                            targetValue = if (addSpkHovered) cs.primary.copy(alpha = 0.6f) else cs.outlineVariant.copy(alpha = 0.6f),
                            animationSpec = StudioMotion.microTween(),
                            label = "AddSpeakerChipBorder"
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = addBg,
                            border = BorderStroke(1.dp, addBorder),
                            modifier = studioAnimateItem()
                                .studioTactile(addSpkSrc, pressScale = 0.96f)
                                .clip(RoundedCornerShape(12.dp))
                                .hoverable(addSpkSrc)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = addSpkSrc,
                                    indication = LocalIndication.current
                                ) { onManageGroupSpeakers() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAddAlt,
                                    contentDescription = S.project.cardManageSpeakerDesc,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isSimpleMode) S.project.cardManageSpeakerSimple else S.project.cardManageSpeakerAdv,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Row: Multi-speaker summary & progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (startedSpeakers > 0) {
                            S.project.cardProgressSummary.withArgs(
                                "started" to startedSpeakers,
                                "total" to candidateCount,
                                "completed" to completedSpeakers,
                                "tokens" to totalTokens
                            )
                        } else if (isSimpleMode) {
                            S.project.cardCandidateCount.withArgs("count" to candidateCount, "date" to dateStr)
                        } else {
                            S.project.cardGroupCandidateCount.withArgs("count" to candidateCount, "date" to dateStr)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (startedSpeakers > 0) {
                        Text(
                            text = "${(displayProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (displayProgress >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (displayProgress >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }

    com.example.ui.components.AnimatedDialogVisibility(visible = showDeleteConfirm) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(S.project.deleteProjectDialogTitle, fontWeight = FontWeight.Bold) },
            text = { Text(S.project.deleteProjectDialogBody.withArgs("name" to project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(S.project.confirmDelete, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeakerManagerPage(
    viewModel: RecordingViewModel,
    initialGroupFilter: String? = null,
    onBack: () -> Unit
) {
    val speakers by viewModel.allSpeakers.collectAsState()
    val speakerGroups by viewModel.speakerGroups.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()

    var selectedGroupFilter by remember(initialGroupFilter) {
        mutableStateOf(initialGroupFilter ?: "ALL")
    }

    var showCreateSpeakerForm by remember { mutableStateOf(false) }
    var speakerToEdit by remember { mutableStateOf<Speaker?>(null) }

    val presetGenders = remember { listOf("男", "女", "多元/不限") }
    var nameField by remember { mutableStateOf("") }
    var genderField by remember { mutableStateOf("男") }
    var ageFieldText by remember { mutableStateOf("25") }
    var dialectField by remember { mutableStateOf("") }
    var groupField by remember { mutableStateOf("") }
    var notesField by remember { mutableStateOf("") }

    val resetForm = {
        nameField = ""
        genderField = "男"
        ageFieldText = "25"
        dialectField = ""
        groupField = if (!isSimpleMode && selectedGroupFilter != "ALL" && selectedGroupFilter != "UNGROUPED") selectedGroupFilter else ""
        notesField = ""
    }

    val effectiveGroupFilter = if (isSimpleMode) "ALL" else selectedGroupFilter
    val filteredSpeakers = remember(speakers, effectiveGroupFilter) {
        when (effectiveGroupFilter) {
            "ALL" -> speakers
            "UNGROUPED" -> speakers.filter { it.groupList.isEmpty() }
            else -> speakers.filter { it.belongsToGroup(effectiveGroupFilter) }
        }
    }

    val isFormOpen = showCreateSpeakerForm || speakerToEdit != null
    val pageBackState = rememberPredictiveBackVisualState()
    val formBackState = rememberPredictiveBackVisualState()

    PredictiveBackGestureHandler(
        enabled = !isFormOpen,
        state = pageBackState,
        onBack = onBack
    )

    PredictiveBackGestureHandler(
        enabled = isFormOpen,
        state = formBackState,
        onBack = {
            showCreateSpeakerForm = false
            speakerToEdit = null
        }
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .predictiveBackLayer(pageBackState),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(S.speaker.managerTitle, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(
                            text = if (isSimpleMode) {
                                S.speaker.managerSubtitleSimple
                            } else {
                                S.speaker.managerSubtitleAdv
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isFormOpen) {
                                showCreateSpeakerForm = false
                                speakerToEdit = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.speaker.backToProjectsDesc)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !showCreateSpeakerForm && speakerToEdit == null,
                enter = fadeIn(StudioMotion.shortInTween()) + scaleIn(StudioMotion.tactileSpring(), initialScale = 0.88f),
                exit = fadeOut(StudioMotion.shortOutTween()) + scaleOut(StudioMotion.shortOutTween(), targetScale = 0.88f)
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        resetForm()
                        showCreateSpeakerForm = true
                    },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = S.speaker.addSpeakerDesc) },
                    text = {
                        Text(
                            text = S.speaker.addSpeakerButton,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("add_speaker_fab")
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        ResponsiveContentContainer(
            maxWidth = 760.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            AnimatedContent(
                targetState = isFormOpen,
                transitionSpec = { StudioMotion.subtleFadeScale() },
                label = "SpeakerFormTransition"
            ) { showForm ->
                if (showForm) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .align(Alignment.TopCenter)
                            .predictiveBackLayer(formBackState),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (speakerToEdit != null) {
                                    S.speaker.editSpeakerTitle.withArgs("name" to speakerToEdit!!.name)
                                } else {
                                    S.speaker.addNewSpeakerTitle
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            OutlinedTextField(
                                value = nameField,
                                onValueChange = { nameField = it },
                                label = { Text(S.speaker.nameLabel) },
                                placeholder = { Text(S.speaker.namePlaceholder) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("speaker_form_name_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            if (!isSimpleMode) {
                                // Multi-Select Speaker Group Field + Quick Group Toggle Chips
                                MultiSelectSpeakerGroupField(
                                    groupField = groupField,
                                    onGroupFieldChange = { groupField = it },
                                    availableGroups = speakerGroups
                                )
                            }

                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = S.speaker.genderLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                presetGenders.forEach { gender ->
                                    val selected = genderField == gender
                                    StudioFilterChip(
                                        selected = selected,
                                        onClick = { genderField = gender },
                                        labelText = S.speaker.displayGender(gender),
                                        modifier = Modifier.testTag("speaker_form_gender_$gender")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = dialectField,
                                    onValueChange = { dialectField = it },
                                    label = { Text(S.speaker.dialectLabel) },
                                    placeholder = { Text(S.speaker.dialectPlaceholder) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                OutlinedTextField(
                                    value = ageFieldText,
                                    onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) ageFieldText = it },
                                    label = { Text(S.speaker.ageLabel, maxLines = 1, softWrap = false) },
                                    placeholder = { Text("25") },
                                    modifier = Modifier.width(104.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp)
                                )
                            }

                            val isDiverseGender = genderField == "多元/不限"
                            OutlinedTextField(
                                value = notesField,
                                onValueChange = { notesField = it },
                                label = {
                                    Text(
                                        if (isDiverseGender) {
                                            S.speaker.notesLabelDiverse
                                        } else {
                                            S.speaker.notesLabelStandard
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(
                                        if (isDiverseGender) {
                                            S.speaker.notesPlaceholderDiverse
                                        } else {
                                            S.speaker.notesPlaceholderStandard
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = RoundedCornerShape(14.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        showCreateSpeakerForm = false
                                        speakerToEdit = null
                                    }
                                ) {
                                    Text(S.common.cancel, maxLines = 1, softWrap = false)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val ageVal = ageFieldText.toIntOrNull() ?: 25
                                        val resolvedDialect = dialectField.trim().ifBlank { S.speaker.defaultDialectMandarin }
                                        if (speakerToEdit != null) {
                                            val updated = speakerToEdit!!.copy(
                                                name = nameField.trim(),
                                                gender = genderField,
                                                age = ageVal,
                                                dialect = resolvedDialect,
                                                notes = notesField.trim(),
                                                groupName = groupField.trim()
                                            )
                                            viewModel.updateSpeaker(updated) {
                                                speakerToEdit = null
                                            }
                                        } else {
                                            viewModel.createSpeaker(
                                                name = nameField.trim(),
                                                gender = genderField,
                                                age = ageVal,
                                                dialect = resolvedDialect,
                                                notes = notesField.trim(),
                                                groupName = groupField.trim()
                                            ) {
                                                showCreateSpeakerForm = false
                                            }
                                        }
                                    },
                                    enabled = nameField.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(S.speaker.saveSpeakerButton, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isSimpleMode) {
                            // Group Filter Bar
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                item {
                                    StudioFilterChip(
                                        selected = selectedGroupFilter == "ALL",
                                        onClick = { selectedGroupFilter = "ALL" },
                                        labelText = S.speaker.allFilterChip.withArgs("count" to speakers.size),
                                        leadingIcon = if (selectedGroupFilter == "ALL") {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                                items(speakerGroups) { grp ->
                                    val countInGrp = speakers.count { it.belongsToGroup(grp) }
                                    StudioFilterChip(
                                        selected = selectedGroupFilter == grp,
                                        onClick = { selectedGroupFilter = grp },
                                        labelText = "$grp ($countInGrp)",
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (selectedGroupFilter == grp) Icons.Default.Check else Icons.Default.FolderShared,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                                val ungroupedCount = speakers.count { it.groupList.isEmpty() }
                                if (ungroupedCount > 0 && speakerGroups.isNotEmpty()) {
                                    item {
                                        StudioFilterChip(
                                            selected = selectedGroupFilter == "UNGROUPED",
                                            onClick = { selectedGroupFilter = "UNGROUPED" },
                                            labelText = S.speaker.ungroupedFilterChip.withArgs("count" to ungroupedCount)
                                        )
                                    }
                                }
                            }
                        }

                        val speakerListState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = speakerListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(
                                    end = if (DesktopAdaptiveState.isDesktopPlatform) 11.dp else 0.dp,
                                    bottom = 88.dp
                                )
                            ) {
                                items(filteredSpeakers, key = { it.id }) { speaker ->
                                    var pitchRange by remember(speaker.id) { mutableStateOf(viewModel.getSpeakerPitchRange(speaker.id)) }
                                    var isEstimating by remember(speaker.id) { mutableStateOf(false) }
                                    var showManualPitchDialog by remember(speaker.id) { mutableStateOf(false) }
                                    var showDeleteConfirm by remember(speaker.id) { mutableStateOf(false) }
                                    val context = LocalContext.current

                                    SpeakerProfileCard(
                                        speaker = speaker,
                                        isSimpleMode = isSimpleMode,
                                        pitchRange = pitchRange,
                                        isEstimatingPitch = isEstimating,
                                        canDelete = speakers.size > 1,
                                        onEstimatePitch = {
                                            isEstimating = true
                                            viewModel.estimatePitchRangeForSpeaker(speaker) { res ->
                                                isEstimating = false
                                                pitchRange = res
                                                if (res == null) {
                                                    Toast.makeText(context, S.speaker.tooFewSamplesForEstimateToast, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onOpenManualPitch = { showManualPitchDialog = true },
                                        onEdit = {
                                            nameField = speaker.name
                                            genderField = if (speaker.gender in presetGenders) speaker.gender else "多元/不限"
                                            ageFieldText = speaker.age.toString()
                                            dialectField = speaker.dialect
                                            groupField = speaker.groupName
                                            notesField = speaker.notes
                                            speakerToEdit = speaker
                                        },
                                        onDeleteRequest = { showDeleteConfirm = true },
                                        modifier = studioAnimateItem()
                                    )

                                    if (!isSimpleMode) {
                                        com.example.ui.components.AnimatedDialogVisibility(visible = showManualPitchDialog) {
                                            ManualSpeakerPitchDialog(
                                                speaker = speaker,
                                                currentRange = pitchRange,
                                                onSave = { minHz, maxHz ->
                                                    viewModel.saveSpeakerPitchRange(speaker.id, minHz, maxHz)
                                                    pitchRange = minHz to maxHz
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
                                                    pitchRange = null
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
                                    }

                                    if (speakers.size > 1) {
                                        com.example.ui.components.AnimatedDialogVisibility(visible = showDeleteConfirm) {
                                            com.example.ui.components.StudioAlertDialog(
                                                onDismissRequest = { showDeleteConfirm = false },
                                                title = { Text(S.speaker.deleteSpeakerTitle, fontWeight = FontWeight.Bold) },
                                                text = { Text(S.speaker.deleteSpeakerBody.withArgs("name" to speaker.name)) },
                                                confirmButton = {
                                                    TextButton(
                                                        onClick = {
                                                            viewModel.deleteSpeaker(speaker)
                                                            showDeleteConfirm = false
                                                        },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                    ) {
                                                        Text(S.project.confirmDelete)
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                                        Text(S.common.cancel)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            PlatformVerticalScrollbar(
                                listState = speakerListState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Refined, studio-grade speaker profile card shared across Desktop & Mobile.
 * Structured into 3 crisp tiers:
 * 1) Top identity header (monogram badge + name/age/dialect/group metadata + top-right action pills)
 * 2) Full-width embedded acoustic pitch calibration strip (readout left + tactile action pills right)
 * 3) Optional subtle notes footer
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpeakerProfileCard(
    speaker: Speaker,
    isSimpleMode: Boolean,
    pitchRange: Pair<Float, Float>?,
    isEstimatingPitch: Boolean,
    canDelete: Boolean,
    onEstimatePitch: () -> Unit,
    onOpenManualPitch: () -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (cardSrc, cardHoveredState) = rememberStudioInteraction()
    val cardHovered by cardHoveredState
    val cardBorder by animateColorAsState(
        targetValue = if (cardHovered) cs.primary.copy(alpha = 0.36f) else cs.outlineVariant.copy(alpha = 0.65f),
        animationSpec = StudioMotion.microTween(),
        label = "SpeakerCardBorder_${speaker.id}"
    )

    val ageDisplay = S.common.ageWithUnit.withArgs("age" to speaker.age)
    val genderAgeBadge = if (speaker.gender.isBlank() || speaker.gender == "不愿透露") {
        ageDisplay
    } else {
        "${S.speaker.displayGender(speaker.gender)} · $ageDisplay"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .hoverable(cardSrc)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tier 1: Identity Header + Compact Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.primaryContainer,
                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.16f)),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = speaker.name.take(1).ifBlank { "?" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onPrimaryContainer
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = speaker.name,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = cs.surfaceContainerHigh,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = genderAgeBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Dialect metadata pill
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = cs.secondaryContainer.copy(alpha = 0.72f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = cs.onSecondaryContainer.copy(alpha = 0.78f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = S.speaker.speakerDialectPrefix.withArgs("dialect" to speaker.dialect),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!isSimpleMode && speaker.groupList.isNotEmpty()) {
                            speaker.groupList.forEach { grp ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = cs.tertiaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderShared,
                                            contentDescription = null,
                                            tint = cs.onTertiaryContainer.copy(alpha = 0.8f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = grp,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cs.onTertiaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 180.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Top-right crisp icon buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SpeakerCardIconAction(
                        icon = Icons.Default.Edit,
                        contentDescription = S.speaker.editInfoDesc,
                        isDestructive = false,
                        onClick = onEdit
                    )
                    if (canDelete) {
                        SpeakerCardIconAction(
                            icon = Icons.Default.DeleteOutline,
                            contentDescription = S.speaker.deleteSpeakerDesc,
                            isDestructive = true,
                            onClick = onDeleteRequest
                        )
                    }
                }
            }

            // Tier 2: Full-width Embedded Acoustic Pitch Calibration Strip (Advanced mode)
            if (!isSimpleMode) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.42f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val isCompactStrip = maxWidth < 340.dp
                        val pitchValueText = pitchRange?.let { "${it.first.toInt()}–${it.second.toInt()} Hz" }
                            ?: S.speaker.pitchPendingEstimate
                        val estimateLabel = when {
                            isEstimatingPitch -> S.speaker.estimatingPitch
                            pitchRange == null -> S.speaker.autoEstimatePitch
                            else -> S.speaker.reEstimatePitch
                        }

                        @Composable
                        fun PitchReadout() {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (pitchRange != null) {
                                        cs.primaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        cs.surfaceContainerHigh
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = if (pitchRange != null) cs.primary else cs.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = S.speaker.personalPitchRangePrefix.withArgs("range" to "").trimEnd(),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = pitchValueText,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = if (pitchRange != null) FontFamily.Monospace else FontFamily.Default,
                                    color = if (pitchRange != null) cs.primary else cs.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        @Composable
                        fun PitchActionPills() {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SpeakerPitchMiniPill(
                                    icon = Icons.Default.Refresh,
                                    label = estimateLabel,
                                    onClick = onEstimatePitch
                                )
                                SpeakerPitchMiniPill(
                                    icon = Icons.Default.Tune,
                                    label = S.recording.manualPitchButton,
                                    onClick = onOpenManualPitch
                                )
                            }
                        }

                        if (isCompactStrip) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PitchReadout()
                                PitchActionPills()
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PitchReadout()
                                PitchActionPills()
                            }
                        }
                    }
                }
            }

            // Tier 3: Optional Notes Footer
            if (speaker.notes.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(14.dp)
                    )
                    Text(
                        text = S.speaker.notesPrefix.withArgs("notes" to speaker.notes),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakerCardIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isDestructive: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (src, hoveredState) = rememberStudioInteraction()
    val hovered by hoveredState
    val bg by animateColorAsState(
        targetValue = when {
            hovered && isDestructive -> cs.errorContainer
            hovered -> cs.primaryContainer
            else -> cs.surfaceContainerLow
        },
        animationSpec = StudioMotion.microTween(),
        label = "SpkActionBg"
    )
    val border by animateColorAsState(
        targetValue = when {
            hovered && isDestructive -> cs.error.copy(alpha = 0.45f)
            hovered -> cs.primary.copy(alpha = 0.42f)
            else -> cs.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "SpkActionBorder"
    )
    val tint by animateColorAsState(
        targetValue = when {
            isDestructive -> if (hovered) cs.error else cs.error.copy(alpha = 0.82f)
            hovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "SpkActionTint"
    )

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .hoverable(src)
            .studioTactile(src, pressScale = 0.92f)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SpeakerPitchMiniPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (src, hoveredState) = rememberStudioInteraction()
    val hovered by hoveredState
    val bg by animateColorAsState(
        targetValue = if (hovered) cs.primaryContainer else cs.surface,
        animationSpec = StudioMotion.microTween(),
        label = "SpkPitchPillBg"
    )
    val border by animateColorAsState(
        targetValue = if (hovered) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.65f),
        animationSpec = StudioMotion.microTween(),
        label = "SpkPitchPillBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (hovered) cs.primary else cs.onSurfaceVariant,
        animationSpec = StudioMotion.microTween(),
        label = "SpkPitchPillFg"
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .hoverable(src)
            .studioTactile(src, pressScale = 0.96f)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MultiSelectSpeakerGroupField(
    groupField: String,
    onGroupFieldChange: (String) -> Unit,
    availableGroups: List<String>,
    modifier: Modifier = Modifier
) {
    val selectedGroups = remember(groupField) { parseSpeakerGroups(groupField) }
    val allDisplayGroups = remember(availableGroups, selectedGroups) {
        (availableGroups + selectedGroups).distinct()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = groupField,
            onValueChange = onGroupFieldChange,
            label = { Text(S.speaker.groupInputLabel) },
            placeholder = { Text(S.speaker.groupInputPlaceholder) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speaker_form_group_input"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )
        if (allDisplayGroups.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedGroups.isEmpty()) {
                        S.speaker.groupMultiSelectHint
                    } else {
                        S.speaker.groupMultiSelectCountHint.withArgs("count" to selectedGroups.size)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (selectedGroups.isNotEmpty()) {
                    Text(
                        text = S.speaker.clearSelectedGroups,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onGroupFieldChange("") }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                allDisplayGroups.forEach { grp ->
                    val isSelected = selectedGroups.any { it.equals(grp, ignoreCase = true) }
                    StudioFilterChip(
                        selected = isSelected,
                        onClick = {
                            onGroupFieldChange(toggleSpeakerGroupInRaw(groupField, grp))
                        },
                        labelText = grp,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Detailed view, metadata editing, and inline single-word CRUD editing of a project.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectDetailEditDialog(
    viewModel: RecordingViewModel,
    project: Project,
    speakerGroups: List<String>,
    summary: ProjectMultiSpeakerSummary?,
    onDismiss: () -> Unit,
    onOpenInWordEditor: (() -> Unit)? = null,
    onOpenSmartMergeImport: (() -> Unit)? = null,
    onUpdate: (String, String, String) -> Unit
) {
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    var isEditingMode by remember { mutableStateOf(false) }

    var edtName by remember { mutableStateOf(project.name) }
    var edtDesc by remember { mutableStateOf(project.description) }
    var edtSpeakerGroup by remember { mutableStateOf(project.speakerGroupName) }

    // Inline word editing / addition states
    var wordBeingEdited by remember { mutableStateOf<Word?>(null) }
    var showAddWordRow by remember { mutableStateOf(false) }
    var newWordText by remember { mutableStateOf("") }
    var newWordRemark by remember { mutableStateOf("") }

    val totalWords = summary?.totalWords ?: 0
    val startedCount = summary?.startedSpeakerCount ?: 0
    val completedCount = summary?.completedSpeakerCount ?: 0

    val hasProjectInputFields = isEditingMode || showAddWordRow
    com.example.ui.components.StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = !hasProjectInputFields,
            usePlatformDefaultWidth = false
        )
    ) {
        com.example.ui.components.StudioDialogEnterAnimation {
            AdaptiveModalSurface(
                targetMaxWidth = 540.dp,
                maxHeightFraction = 0.88f,
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEditingMode) {
                        if (isSimpleMode) S.project.editProjectTitleSimple else S.project.editProjectTitleAdv
                    } else {
                        S.project.detailDialogTitle
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isEditingMode) {
                    OutlinedTextField(
                        value = edtName,
                        onValueChange = { edtName = it },
                        label = { Text(S.project.projectNameLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        value = edtDesc,
                        onValueChange = { edtDesc = it },
                        label = { Text(S.project.projectDescLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (!isSimpleMode) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = edtSpeakerGroup,
                                onValueChange = { edtSpeakerGroup = it },
                                label = { Text(S.project.boundSpeakerGroupLabel) },
                                placeholder = { Text(S.project.boundSpeakerGroupPlaceholder) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    StudioFilterChip(
                                        selected = edtSpeakerGroup.isBlank(),
                                        onClick = { edtSpeakerGroup = "" },
                                        labelText = S.project.unlimitedGroupChip
                                    )
                                }
                                items(speakerGroups) { grp ->
                                    StudioFilterChip(
                                        selected = edtSpeakerGroup.trim() == grp,
                                        onClick = { edtSpeakerGroup = grp },
                                        labelText = grp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditingMode = false }) {
                            Text(S.project.backToDetailButton, maxLines = 1, softWrap = false)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (edtName.isNotBlank()) {
                                    onUpdate(edtName.trim(), edtDesc.trim(), edtSpeakerGroup.trim())
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(S.project.saveChangesButton, maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = project.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (project.description.isNotEmpty()) {
                                    Text(
                                        text = project.description,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val groupLabel = if (project.speakerGroupName.isNotBlank()) project.speakerGroupName else S.project.detailAllSpeakers
                                Text(
                                    text = if (isSimpleMode) {
                                        S.project.detailProgressSimple.withArgs(
                                            "words" to totalWords,
                                            "started" to startedCount,
                                            "completed" to completedCount
                                        )
                                    } else {
                                        S.project.detailProgressAdv.withArgs(
                                            "words" to totalWords,
                                            "group" to groupLabel,
                                            "started" to startedCount,
                                            "completed" to completedCount
                                        )
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.project.projectWordListTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onOpenInWordEditor != null) {
                                    TextButton(
                                        onClick = onOpenInWordEditor,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(S.project.openWordListEditor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                }
                                TextButton(
                                    onClick = { showAddWordRow = !showAddWordRow },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(if (showAddWordRow) S.project.addWordCollapse else S.project.addWordExpand, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        if (showAddWordRow) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newWordText,
                                    onValueChange = { newWordText = it },
                                    placeholder = { Text(S.project.wordPlaceholder, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newWordRemark,
                                    onValueChange = { newWordRemark = it },
                                    placeholder = { Text(S.project.notePlaceholder, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (newWordText.isNotBlank()) {
                                            viewModel.addWordToProject(project.id, newWordText, newWordRemark)
                                            newWordText = ""
                                            newWordRemark = ""
                                            showAddWordRow = false
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(S.common.add, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        val context = LocalContext.current
                        var wordsList by remember { mutableStateOf<List<Word>>(emptyList()) }
                        LaunchedEffect(project.id) {
                            viewModel.getWordsForProjectFlow(project.id).collect {
                                wordsList = it
                            }
                        }

                        if (wordsList.isEmpty()) {
                            Text(
                                text = S.project.loadingOrEmptyWordList,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            val wordsTextToCopy = remember(wordsList) {
                                val sb = StringBuilder()
                                var lastGroup = ""
                                wordsList.forEach { pw ->
                                    if (pw.groupName.isNotEmpty() && pw.groupName != lastGroup) {
                                        sb.append("【").append(pw.groupName).append("】\n")
                                        lastGroup = pw.groupName
                                    }
                                    if (pw.remark.isNotEmpty()) {
                                        sb.append(pw.text).append(" (").append(pw.remark).append(")\n")
                                    } else {
                                        sb.append(pw.text).append("\n")
                                    }
                                }
                                sb.toString().trim()
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                val detailWordsListState = rememberLazyListState()
                                Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                    LazyColumn(
                                        state = detailWordsListState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        itemsIndexed(wordsList, key = { _, w -> w.id }) { index, word ->
                                            Row(
                                                modifier = studioAnimateItem()
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        wordBeingEdited = word
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "${index + 1}. ${word.text}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (word.remark.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "(${word.remark})",
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.widthIn(max = 120.dp)
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = S.project.editWordDesc,
                                                        tint = MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    if (wordsList.size > 1) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = S.project.deleteWordDesc,
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .clickable {
                                                                    viewModel.deleteWordItem(word)
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    PlatformVerticalScrollbar(
                                        listState = detailWordsListState,
                                        modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }
                            }

                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val stackActionButtons = maxWidth < 400.dp
                                if (stackActionButtons) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("word_list", wordsTextToCopy)
                                                clipboard?.setPrimaryClip(clip)
                                                Toast.makeText(context, S.project.copiedFullWordListToast, Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("copy_all_words_button")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(S.project.copyFullWordList, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                        if (onOpenSmartMergeImport != null) {
                                            OutlinedButton(
                                                onClick = onOpenSmartMergeImport,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("merge_import_into_project_button")
                                            ) {
                                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(com.example.i18n.SSmartImport.modeMergeExisting, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("word_list", wordsTextToCopy)
                                                clipboard?.setPrimaryClip(clip)
                                                Toast.makeText(context, S.project.copiedFullWordListToast, Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("copy_all_words_button")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(S.project.copyFullWordList, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                        if (onOpenSmartMergeImport != null) {
                                            OutlinedButton(
                                                onClick = onOpenSmartMergeImport,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("merge_import_into_project_button")
                                            ) {
                                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(com.example.i18n.SSmartImport.modeMergeExisting, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextButton(onClick = { isEditingMode = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSimpleMode) S.project.modifyProjectInfo else S.project.modifyProjectAndGroup, maxLines = 1, softWrap = false)
                        }
                        Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                            Text(S.common.close, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
    }

    com.example.ui.components.AnimatedNullableDialogVisibility(value = wordBeingEdited) { target ->
        var editWText by remember(target.id) { mutableStateOf(target.text) }
        var editWRemark by remember(target.id) { mutableStateOf(target.remark) }

        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { wordBeingEdited = null },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            title = { Text(S.project.editWordDialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editWText,
                        onValueChange = { editWText = it },
                        label = { Text(S.project.targetWordLabel) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editWRemark,
                        onValueChange = { editWRemark = it },
                        label = { Text(S.project.wordRemarkEditLabel) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editWText.isNotBlank()) {
                            viewModel.updateWordItem(target, editWText, editWRemark)
                            wordBeingEdited = null
                        }
                    }
                ) {
                    Text(S.common.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { wordBeingEdited = null }) {
                    Text(S.common.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WirelessHomeBackupBanner(
    pendingSyncCount: Int,
    capsules: List<com.example.link.WirelessSessionCapsuleSummary>,
    onRescuePending: () -> Unit,
    onImportCapsule: (com.example.link.WirelessSessionCapsuleSummary) -> Unit,
    onExportCapsuleZip: (com.example.link.WirelessSessionCapsuleSummary) -> Unit,
    onDeleteCapsule: (com.example.link.WirelessSessionCapsuleSummary) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wireless_home_backup_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本机存有双机互联录音备份",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "归入本机项目后将自动移入字表列表；也可导出压缩包或清除备份",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (pendingSyncCount > 0) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "有 $pendingSyncCount 条互联录音暂存在本机",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onRescuePending,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("整理到本机备份", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            capsules.forEach { cap ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "《${cap.projectName}》 · ${cap.speakerName}（${cap.recordedTakeCount} 条录音）",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeleteCapsule(cap) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("wireless_home_backup_delete_${cap.folderName}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = S.common.delete,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { onImportCapsule(cap) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .testTag("wireless_home_backup_import_${cap.folderName}")
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("归入本机项目", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { onExportCapsuleZip(cap) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            ) {
                                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("导出压缩包", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

