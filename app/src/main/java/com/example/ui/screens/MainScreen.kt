package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Project
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.LinkRole
import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.model.TextGridAnomaly
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.ui.TextGridFullViewerScreen
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.LocalAppWindowMetrics
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.StudioAlertDialog
import com.example.ui.components.StudioMotion
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.computeAppWindowMetrics
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.viewmodel.RecordingViewModel
import com.example.viewmodel.TextGridViewerSession
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class AppScreen(val index: Int) {
    object ProjectList : AppScreen(0)
    object RecordingWorkspace : AppScreen(1)
    object ArchiveExplorer : AppScreen(2)
    object Settings : AppScreen(3)
}

@Composable
fun MainScreen(
    viewModel: RecordingViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.ProjectList) }
    val currentProject by viewModel.currentProject.collectAsState()
    val showGlobalManagement by viewModel.showGlobalManagement.collectAsState()
    val showPhoneticWord by viewModel.showPhoneticWord.collectAsState()
    val currentSpeaker by viewModel.currentSpeaker.collectAsState()
    val allSpeakers by viewModel.allSpeakers.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val hasSeenRecordingTips by viewModel.hasSeenRecordingTips.collectAsState()
    val participantCleanMode by viewModel.participantCleanMode.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var openSpeakerManagerTrigger by remember { mutableIntStateOf(0) }
    var showUserManualOverlay by remember { mutableStateOf(false) }
    var showEncyclopediaOverlay by remember { mutableStateOf(false) }
    var showWordListEditor by remember { mutableStateOf(false) }
    var wordEditorInitialProject by remember { mutableStateOf<Project?>(null) }
    var showImportWorkspace by remember { mutableStateOf(false) }
    var importInitialUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var importInitialMergeProject by remember { mutableStateOf<Project?>(null) }
    var isGlobalDropHovering by remember { mutableStateOf(false) }

    var showRoomNoiseCheck by remember { mutableStateOf(false) }
    var showRecordingTips by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showQuickRecordingSettingsInSidebar by remember { mutableStateOf(false) }
    var showWirelessPairingDialog by remember { mutableStateOf(false) }

    val activeLinkRole by viewModel.wirelessLink.activeRole.collectAsState()
    val isLinkConnected by viewModel.wirelessLink.isConnected.collectAsState()
    val textGridViewerSession by viewModel.textGridViewerSession.collectAsState()
    var pendingSettingsCategoryId by remember { mutableStateOf<String?>(null) }
    var settingsCategoryJumpSeq by remember { mutableIntStateOf(0) }

    fun closePrimaryOverlays() {
        viewModel.closeTextGridViewer()
        showWordListEditor = false
        showImportWorkspace = false
        showUserManualOverlay = false
        showEncyclopediaOverlay = false
        showRoomNoiseCheck = false
    }

    fun navigateToTextGridSettings() {
        closePrimaryOverlays()
        viewModel.showPhoneticWordDetail(null)
        viewModel.setShowGlobalManagement(false)
        showQuickRecordingSettingsInSidebar = false
        pendingSettingsCategoryId = SettingsCategory.PRAAT_TEXTGRID.id
        settingsCategoryJumpSeq++
        currentScreen = AppScreen.Settings
    }

    LaunchedEffect(Unit) {
        RecordingViewModel.externalTextGridOpenRequests.collect { (tgFile, pairedAudio) ->
            viewModel.openExternalTextGridViewer(tgFile, pairedAudio)
        }
    }

    LaunchedEffect(Unit) {
        RecordingViewModel.navigateToTextGridSettingsRequests.collect {
            navigateToTextGridSettings()
        }
    }

    val canAcceptWindowGlobalDrop = DesktopAdaptiveState.isDesktopPlatform &&
        textGridViewerSession == null &&
        !showImportWorkspace &&
        !showWordListEditor &&
        !showWirelessPairingDialog &&
        activeLinkRole == LinkRole.STANDALONE &&
        !showAboutDialog &&
        !showRecordingTips &&
        hasSeenRecordingTips &&
        com.example.ui.components.DesktopModalManager.entries.isEmpty()

    com.example.utils.SmartImportDesktopDropEffect(
        enabled = canAcceptWindowGlobalDrop,
        onHoverChanged = { isGlobalDropHovering = it },
        onDroppedUris = { dropped ->
            if (dropped.isNotEmpty()) {
                showUserManualOverlay = false
                showEncyclopediaOverlay = false
                showRoomNoiseCheck = false
                importInitialUris = dropped
                importInitialMergeProject = null
                showImportWorkspace = true
            }
        }
    )

    BoxWithConstraints(modifier = modifier) {
        val windowMetrics = remember(maxWidth, maxHeight) {
            computeAppWindowMetrics(maxWidth, maxHeight)
        }
        val isCompactWidth = windowMetrics.isCompactWidth
        val isCompactHeightMobile = maxHeight < 500.dp && !DesktopAdaptiveState.isDesktopPlatform
        val isExpandedSplit = windowMetrics.isExpandedOrWider && !isCompactHeightMobile
        val showNavRail = !isCompactWidth
        val canDockRightInspector = maxWidth >= 1120.dp && isExpandedSplit
        val isUltraWide = windowMetrics.isUltraWide

        val hasTextGridViewerOpen = textGridViewerSession != null

        val isPrimaryOverlayOpen = hasTextGridViewerOpen ||
            showWordListEditor ||
            showImportWorkspace ||
            showUserManualOverlay ||
            showEncyclopediaOverlay ||
            showRoomNoiseCheck

        val hasDockedSideOverlay = canDockRightInspector && !hasTextGridViewerOpen && (showGlobalManagement || showPhoneticWord != null)
        val showFullGlobalOverlay = showGlobalManagement && !canDockRightInspector

        val isInRecordingOrSplitTab = currentScreen == AppScreen.ProjectList || currentScreen == AppScreen.RecordingWorkspace
        val isFullWindowImmersive = participantCleanMode &&
            currentProject != null &&
            !isPrimaryOverlayOpen &&
            !showFullGlobalOverlay &&
            (if (isExpandedSplit) isInRecordingOrSplitTab else currentScreen == AppScreen.RecordingWorkspace)

        // Inform DesktopTopStudioBar whether a collapsible left project sidebar currently exists on screen
        val hasCollapsibleSidebarNow = isExpandedSplit &&
            !isPrimaryOverlayOpen &&
            !showFullGlobalOverlay &&
            isInRecordingOrSplitTab &&
            !isFullWindowImmersive
        SideEffect {
            DesktopAdaptiveState.hasCollapsibleProjectSidebar = hasCollapsibleSidebarNow
        }

        // Automatically suspend bare-key recording shortcuts when any non-recording screen or modal is active
        val shouldSuspendBareShortcuts = isPrimaryOverlayOpen ||
            showWirelessPairingDialog ||
            activeLinkRole != LinkRole.STANDALONE ||
            showGlobalManagement ||
            showPhoneticWord != null ||
            showRecordingTips ||
            !hasSeenRecordingTips ||
            showAboutDialog ||
            currentProject == null ||
            currentScreen == AppScreen.ArchiveExplorer ||
            currentScreen == AppScreen.Settings ||
            (!isExpandedSplit && currentScreen != AppScreen.RecordingWorkspace)

        SuspendDesktopShortcutsWhen(shouldSuspendBareShortcuts)

        val mainTabBackState = rememberPredictiveBackVisualState()
        val tabletSidePaneBackState = rememberPredictiveBackVisualState()

        PredictiveBackGestureHandler(
            enabled = !isPrimaryOverlayOpen && (
                (isCompactWidth && drawerState.isOpen) ||
                    showGlobalManagement ||
                    showPhoneticWord != null ||
                    showQuickRecordingSettingsInSidebar ||
                    currentScreen != AppScreen.ProjectList
                ),
            state = if (hasDockedSideOverlay) tabletSidePaneBackState else mainTabBackState
        ) {
            when {
                isCompactWidth && drawerState.isOpen -> scope.launch { drawerState.close() }
                showPhoneticWord != null -> viewModel.showPhoneticWordDetail(null)
                showGlobalManagement -> viewModel.setShowGlobalManagement(false)
                showQuickRecordingSettingsInSidebar -> showQuickRecordingSettingsInSidebar = false
                !isExpandedSplit && currentScreen == AppScreen.ArchiveExplorer -> currentScreen = AppScreen.RecordingWorkspace
                else -> currentScreen = AppScreen.ProjectList
            }
        }

        // Cache last non-null docked inspector payload so the 145ms exit slide never flashes empty white
        var cachedSideOverlay by remember { mutableStateOf<Pair<Boolean, Word?>>(false to null) }
        if (hasDockedSideOverlay) {
            cachedSideOverlay = showGlobalManagement to showPhoneticWord
        }

        CompositionLocalProvider(LocalAppWindowMetrics provides windowMetrics) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Outermost Desktop Studio Top Bar: stays accessible across Compact, Medium, and Expanded widths
                AnimatedVisibility(
                    visible = !isFullWindowImmersive && DesktopAdaptiveState.desktopTopBarSlot != null,
                    enter = StudioMotion.verticalPanelEnter,
                    exit = StudioMotion.verticalPanelExit
                ) {
                    DesktopAdaptiveState.desktopTopBarSlot?.invoke()
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val drawerGesturesEnabled = isCompactWidth &&
                        !isFullWindowImmersive &&
                        !isPrimaryOverlayOpen &&
                        !showFullGlobalOverlay &&
                        currentScreen == AppScreen.ProjectList

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerGesturesEnabled || (isCompactWidth && drawerState.isOpen),
                        drawerContent = {
                            AppNavigationDrawerContent(
                                projectCount = allProjects.size,
                                speakerCount = allSpeakers.size,
                                themeMode = themeMode,
                                isSimpleMode = isSimpleMode,
                                onCycleTheme = {
                                    val nextMode = when (themeMode) {
                                        "SYSTEM" -> "LIGHT"
                                        "LIGHT" -> "DARK"
                                        else -> "SYSTEM"
                                    }
                                    viewModel.setThemeMode(nextMode)
                                },
                                onOpenSpeakerManager = {
                                    scope.launch { drawerState.close() }
                                    closePrimaryOverlays()
                                    currentScreen = AppScreen.ProjectList
                                    openSpeakerManagerTrigger++
                                },
                                onOpenRecordingStats = {
                                    scope.launch { drawerState.close() }
                                    viewModel.setShowGlobalManagement(true)
                                },
                                onOpenWordListEditor = {
                                    scope.launch { drawerState.close() }
                                    closePrimaryOverlays()
                                    wordEditorInitialProject = currentProject
                                    showWordListEditor = true
                                },
                                onOpenUserManual = {
                                    scope.launch { drawerState.close() }
                                    closePrimaryOverlays()
                                    showUserManualOverlay = true
                                },
                                onOpenEncyclopedia = {
                                    scope.launch { drawerState.close() }
                                    closePrimaryOverlays()
                                    showEncyclopediaOverlay = true
                                },
                                onOpenRoomNoiseCheck = {
                                    scope.launch { drawerState.close() }
                                    closePrimaryOverlays()
                                    showRoomNoiseCheck = true
                                },
                                onOpenRecordingTips = {
                                    scope.launch { drawerState.close() }
                                    showRecordingTips = true
                                },
                                onOpenSettings = {
                                    scope.launch { drawerState.close() }
                                    closePrimaryOverlays()
                                    currentScreen = AppScreen.Settings
                                },
                                onOpenWirelessLink = {
                                    scope.launch { drawerState.close() }
                                    showWirelessPairingDialog = true
                                },
                                onOpenAbout = {
                                    scope.launch { drawerState.close() }
                                    showAboutDialog = true
                                }
                            )
                        }
                    ) {
                        var lastCompactTabScreen by remember { mutableStateOf<AppScreen>(AppScreen.ProjectList) }
                        if (currentScreen != AppScreen.Settings) {
                            lastCompactTabScreen = currentScreen
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                                // Unified NavigationRail for Medium (600..959dp) and Expanded (>=960dp)
                                AnimatedVisibility(
                                    visible = showNavRail && !isFullWindowImmersive,
                                    enter = StudioMotion.horizontalPaneEnter,
                                    exit = StudioMotion.horizontalPaneExit
                                ) {
                                    Row(modifier = Modifier.fillMaxHeight()) {
                                        NavigationRail(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier
                                                .width(if (isExpandedSplit) 80.dp else 72.dp)
                                                .testTag("app_nav_rail")
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                PhonAcqLogo(
                                                    modifier = Modifier
                                                        .padding(vertical = 6.dp)
                                                        .size(if (isExpandedSplit) 36.dp else 30.dp)
                                                )

                                                // Primary navigation group
                                                NavigationRailItem(
                                                    selected = !isPrimaryOverlayOpen &&
                                                        !showFullGlobalOverlay &&
                                                        !showQuickRecordingSettingsInSidebar &&
                                                        (if (isExpandedSplit) isInRecordingOrSplitTab else currentScreen == AppScreen.ProjectList),
                                                    onClick = {
                                                        closePrimaryOverlays()
                                                        viewModel.setShowGlobalManagement(false)
                                                        showQuickRecordingSettingsInSidebar = false
                                                        DesktopAdaptiveState.isProjectSidebarCollapsed = false
                                                        currentScreen = AppScreen.ProjectList
                                                    },
                                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = S.nav.tabProjects) },
                                                    label = { Text(S.nav.tabProjects, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag(if (isExpandedSplit) "rail_project_list" else "tab_project_list")
                                                )

                                                if (!isExpandedSplit) {
                                                    NavigationRailItem(
                                                        selected = !isPrimaryOverlayOpen &&
                                                            !showFullGlobalOverlay &&
                                                            currentScreen == AppScreen.RecordingWorkspace,
                                                        onClick = {
                                                            closePrimaryOverlays()
                                                            viewModel.setShowGlobalManagement(false)
                                                            currentScreen = AppScreen.RecordingWorkspace
                                                        },
                                                        icon = { Icon(Icons.Default.Mic, contentDescription = S.nav.tabRecordingStudio) },
                                                        label = { Text(S.nav.tabRecordingStudio, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                        modifier = Modifier
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .testTag("tab_recording_workspace")
                                                    )
                                                }

                                                NavigationRailItem(
                                                    selected = !isPrimaryOverlayOpen &&
                                                        !showFullGlobalOverlay &&
                                                        currentScreen == AppScreen.ArchiveExplorer,
                                                    onClick = {
                                                        closePrimaryOverlays()
                                                        viewModel.setShowGlobalManagement(false)
                                                        showQuickRecordingSettingsInSidebar = false
                                                        currentScreen = AppScreen.ArchiveExplorer
                                                    },
                                                    icon = { Icon(Icons.Default.Folder, contentDescription = S.nav.tabArchive) },
                                                    label = { Text(S.nav.tabArchive, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_archive_explorer")
                                                )

                                                NavigationRailItem(
                                                    selected = showWordListEditor,
                                                    onClick = {
                                                        showImportWorkspace = false
                                                        showUserManualOverlay = false
                                                        showEncyclopediaOverlay = false
                                                        showRoomNoiseCheck = false
                                                        viewModel.setShowGlobalManagement(false)
                                                        wordEditorInitialProject = currentProject
                                                        showWordListEditor = true
                                                    },
                                                    icon = { Icon(Icons.Default.EditNote, contentDescription = S.nav.wordListEditor) },
                                                    label = { Text(S.nav.editorShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_word_editor")
                                                )

                                                NavigationRailItem(
                                                    selected = !isPrimaryOverlayOpen &&
                                                        !showFullGlobalOverlay &&
                                                        currentScreen == AppScreen.Settings,
                                                    onClick = {
                                                        closePrimaryOverlays()
                                                        viewModel.setShowGlobalManagement(false)
                                                        showQuickRecordingSettingsInSidebar = false
                                                        currentScreen = AppScreen.Settings
                                                    },
                                                    icon = { Icon(Icons.Default.Settings, contentDescription = S.nav.settings) },
                                                    label = { Text(S.nav.settings, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_settings")
                                                )

                                                NavigationRailItem(
                                                    selected = showWirelessPairingDialog || activeLinkRole != LinkRole.STANDALONE,
                                                    onClick = { showWirelessPairingDialog = true },
                                                    icon = { Icon(Icons.Default.Devices, contentDescription = S.nav.wirelessLink) },
                                                    label = { Text(S.nav.wirelessShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_wireless_link")
                                                )

                                                HorizontalDivider(
                                                    modifier = Modifier
                                                        .width(36.dp)
                                                        .padding(vertical = 6.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                )

                                                // Utility & reference group
                                                if (!isExpandedSplit) {
                                                    NavigationRailItem(
                                                        selected = false,
                                                        onClick = {
                                                            closePrimaryOverlays()
                                                            viewModel.setShowGlobalManagement(false)
                                                            currentScreen = AppScreen.ProjectList
                                                            openSpeakerManagerTrigger++
                                                        },
                                                        icon = { Icon(Icons.Default.Groups, contentDescription = S.nav.speakerManager) },
                                                        label = { Text(S.nav.speakerShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                        modifier = Modifier
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .testTag("rail_speakers")
                                                    )
                                                    if (!isSimpleMode) {
                                                        NavigationRailItem(
                                                            selected = showGlobalManagement,
                                                            onClick = {
                                                                closePrimaryOverlays()
                                                                viewModel.setShowGlobalManagement(true)
                                                            },
                                                            icon = { Icon(Icons.Default.PieChart, contentDescription = S.nav.recordingStatsAndCleanup) },
                                                            label = { Text(S.nav.statsShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                            modifier = Modifier
                                                                .pointerHoverIcon(PointerIcon.Hand)
                                                                .testTag("rail_stats")
                                                        )
                                                    }
                                                }

                                                NavigationRailItem(
                                                    selected = showUserManualOverlay,
                                                    onClick = {
                                                        showWordListEditor = false
                                                        showImportWorkspace = false
                                                        showEncyclopediaOverlay = false
                                                        showRoomNoiseCheck = false
                                                        viewModel.setShowGlobalManagement(false)
                                                        showUserManualOverlay = !showUserManualOverlay
                                                    },
                                                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = S.nav.userManual) },
                                                    label = { Text(S.nav.manualShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_item_user_manual")
                                                )

                                                NavigationRailItem(
                                                    selected = showEncyclopediaOverlay,
                                                    onClick = {
                                                        showWordListEditor = false
                                                        showImportWorkspace = false
                                                        showUserManualOverlay = false
                                                        showRoomNoiseCheck = false
                                                        viewModel.setShowGlobalManagement(false)
                                                        showEncyclopediaOverlay = !showEncyclopediaOverlay
                                                    },
                                                    icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = S.nav.encyclopedia) },
                                                    label = { Text(S.nav.encyclopediaShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_item_encyclopedia")
                                                )

                                                NavigationRailItem(
                                                    selected = showRoomNoiseCheck,
                                                    onClick = {
                                                        showWordListEditor = false
                                                        showImportWorkspace = false
                                                        showUserManualOverlay = false
                                                        showEncyclopediaOverlay = false
                                                        viewModel.setShowGlobalManagement(false)
                                                        showRoomNoiseCheck = !showRoomNoiseCheck
                                                    },
                                                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = S.nav.soundCheck) },
                                                    label = { Text(S.nav.soundCheckShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_sound_check")
                                                )

                                                NavigationRailItem(
                                                    selected = showRecordingTips,
                                                    onClick = { showRecordingTips = true },
                                                    icon = { Icon(Icons.Default.Lightbulb, contentDescription = S.nav.recordingTips) },
                                                    label = { Text(S.nav.tipsShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_tips")
                                                )

                                                HorizontalDivider(
                                                    modifier = Modifier
                                                        .width(36.dp)
                                                        .padding(vertical = 6.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                                )

                                                // Appearance & About group
                                                val themeLabel = when (themeMode) {
                                                    "LIGHT" -> S.nav.themeLightShort
                                                    "DARK" -> S.nav.themeDarkShort
                                                    else -> S.nav.themeSystemShort
                                                }
                                                NavigationRailItem(
                                                    selected = false,
                                                    onClick = {
                                                        val nextMode = when (themeMode) {
                                                            "SYSTEM" -> "LIGHT"
                                                            "LIGHT" -> "DARK"
                                                            else -> "SYSTEM"
                                                        }
                                                        viewModel.setThemeMode(nextMode)
                                                    },
                                                    icon = {
                                                        AnimatedContent(
                                                            targetState = themeMode,
                                                            transitionSpec = { StudioMotion.subtleFadeScale() },
                                                            label = "RailThemeIconMorph"
                                                        ) { mode ->
                                                            val icon = when (mode) {
                                                                "LIGHT" -> Icons.Default.LightMode
                                                                "DARK" -> Icons.Default.DarkMode
                                                                else -> Icons.Default.BrightnessAuto
                                                            }
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = S.nav.switchThemeDesc.withArgs("label" to themeLabel)
                                                            )
                                                        }
                                                    },
                                                    label = { Text(themeLabel, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_theme_toggle")
                                                )

                                                NavigationRailItem(
                                                    selected = showAboutDialog,
                                                    onClick = { showAboutDialog = true },
                                                    icon = { Icon(Icons.Default.Info, contentDescription = S.nav.aboutPhonAcq) },
                                                    label = { Text(S.nav.aboutShort, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                                    modifier = Modifier
                                                        .pointerHoverIcon(PointerIcon.Hand)
                                                        .testTag("rail_about")
                                                )
                                            }
                                        }

                                        VerticalDivider(
                                            modifier = Modifier.fillMaxHeight().width(1.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                // Single Unified Main Workspace Router (zero duplicate overlay declarations; state survives breakpoint resizing)
                                val activeWorkspaceKey = when {
                                    hasTextGridViewerOpen -> "TEXTGRID_VIEWER"
                                    isFullWindowImmersive -> "IMMERSIVE_RECORDING"
                                    showFullGlobalOverlay -> "GLOBAL_MANAGEMENT"
                                    showImportWorkspace -> "IMPORT_WORKSPACE"
                                    showWordListEditor -> "WORD_EDITOR"
                                    showUserManualOverlay -> "USER_MANUAL"
                                    showEncyclopediaOverlay -> "ENCYCLOPEDIA"
                                    showRoomNoiseCheck -> "SOUND_CHECK"
                                    currentScreen == AppScreen.Settings -> "SETTINGS"
                                    isCompactWidth -> "COMPACT_TABS"
                                    currentScreen == AppScreen.ArchiveExplorer -> "ARCHIVE_EXPLORER"
                                    isExpandedSplit -> "SPLIT_WORKSPACE"
                                    currentScreen == AppScreen.RecordingWorkspace -> "RECORDING_WORKSPACE"
                                    else -> "PROJECT_LIST"
                                }

                                val androidNavBarInsetModifier = if (DesktopAdaptiveState.isDesktopPlatform) {
                                    Modifier
                                } else {
                                    Modifier.navigationBarsPadding()
                                }

                                AnimatedContent(
                                    targetState = activeWorkspaceKey,
                                    transitionSpec = {
                                        if (isCompactWidth) {
                                            val leavingRootTabs = initialState == "COMPACT_TABS"
                                            val enteringRootTabs = targetState == "COMPACT_TABS"
                                            when {
                                                leavingRootTabs -> {
                                                    // Entering full-screen subpage (Settings, WordEditor, Manual, etc.) from bottom-tab root
                                                    (slideInHorizontally(StudioMotion.pageInTween()) { it / 4 } +
                                                        fadeIn(StudioMotion.pageInTween())) togetherWith
                                                        (slideOutHorizontally(StudioMotion.pageOutTween()) { -it / 8 } +
                                                            fadeOut(StudioMotion.pageOutTween()))
                                                }
                                                enteringRootTabs -> {
                                                    // Returning from full-screen subpage back to bottom-tab root
                                                    (slideInHorizontally(StudioMotion.pageInTween()) { -it / 8 } +
                                                        fadeIn(StudioMotion.pageInTween())) togetherWith
                                                        (slideOutHorizontally(StudioMotion.pageOutTween()) { it / 4 } +
                                                            fadeOut(StudioMotion.pageOutTween()))
                                                }
                                                else -> {
                                                    fadeIn(StudioMotion.pageInTween()) togetherWith
                                                        fadeOut(StudioMotion.pageOutTween())
                                                }
                                            }.using(SizeTransform(clip = false))
                                        } else {
                                            // Desktop / Tablet: calm cross-fade with subtle scale (no vertical bobbing)
                                            (fadeIn(StudioMotion.pageInTween()) +
                                                scaleIn(initialScale = 0.99f, animationSpec = StudioMotion.pageInTween())) togetherWith
                                                fadeOut(StudioMotion.pageOutTween())
                                        }
                                    },
                                    label = "UnifiedMainWorkspaceTransition",
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clipToBounds()
                                ) { paneKey ->
                                    when (paneKey) {
                                        "COMPACT_TABS" -> {
                                            Scaffold(
                                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                                bottomBar = {
                                                    NavigationBar(
                                                        containerColor = MaterialTheme.colorScheme.surface,
                                                        tonalElevation = 0.dp,
                                                        modifier = Modifier.testTag("app_bottom_bar")
                                                    ) {
                                                        NavigationBarItem(
                                                            selected = lastCompactTabScreen == AppScreen.ProjectList,
                                                            onClick = { currentScreen = AppScreen.ProjectList },
                                                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = S.nav.tabProjects) },
                                                            label = {
                                                                Text(
                                                                    text = S.nav.tabProjects,
                                                                    fontWeight = if (lastCompactTabScreen == AppScreen.ProjectList) FontWeight.Bold else FontWeight.Normal,
                                                                    fontSize = 12.sp,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            },
                                                            colors = NavigationBarItemDefaults.colors(
                                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                                            ),
                                                            modifier = Modifier
                                                                .pointerHoverIcon(PointerIcon.Hand)
                                                                .testTag("tab_project_list")
                                                        )
                                                        NavigationBarItem(
                                                            selected = lastCompactTabScreen == AppScreen.RecordingWorkspace,
                                                            onClick = { currentScreen = AppScreen.RecordingWorkspace },
                                                            icon = { Icon(Icons.Default.Mic, contentDescription = S.nav.tabRecordingStudio) },
                                                            label = {
                                                                Text(
                                                                    text = S.nav.tabRecordingStudio,
                                                                    fontWeight = if (lastCompactTabScreen == AppScreen.RecordingWorkspace) FontWeight.Bold else FontWeight.Normal,
                                                                    fontSize = 12.sp,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            },
                                                            colors = NavigationBarItemDefaults.colors(
                                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                                            ),
                                                            modifier = Modifier
                                                                .pointerHoverIcon(PointerIcon.Hand)
                                                                .testTag("tab_recording_workspace")
                                                        )
                                                        NavigationBarItem(
                                                            selected = lastCompactTabScreen == AppScreen.ArchiveExplorer,
                                                            onClick = { currentScreen = AppScreen.ArchiveExplorer },
                                                            icon = { Icon(Icons.Default.Folder, contentDescription = S.nav.tabArchive) },
                                                            label = {
                                                                Text(
                                                                    text = S.nav.tabArchive,
                                                                    fontWeight = if (lastCompactTabScreen == AppScreen.ArchiveExplorer) FontWeight.Bold else FontWeight.Normal,
                                                                    fontSize = 12.sp,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            },
                                                            colors = NavigationBarItemDefaults.colors(
                                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                                            ),
                                                            modifier = Modifier
                                                                .pointerHoverIcon(PointerIcon.Hand)
                                                                .testTag("tab_archive_explorer")
                                                        )
                                                    }
                                                },
                                                containerColor = MaterialTheme.colorScheme.background,
                                                modifier = Modifier.fillMaxSize()
                                            ) { tabPadding ->
                                                BoxTransition(
                                                    targetState = lastCompactTabScreen,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(tabPadding)
                                                ) { tabScreen ->
                                                    when (tabScreen) {
                                                        AppScreen.ProjectList -> {
                                                            ProjectListScreen(
                                                                viewModel = viewModel,
                                                                onProjectOpened = {
                                                                    currentScreen = AppScreen.RecordingWorkspace
                                                                },
                                                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                                                onOpenSettings = { currentScreen = AppScreen.Settings },
                                                                onOpenWirelessLink = { showWirelessPairingDialog = true },
                                                                onOpenWordListEditor = { proj ->
                                                                    wordEditorInitialProject = proj
                                                                    showWordListEditor = true
                                                                },
                                                                onOpenImportPage = { uris, mergeProj ->
                                                                    importInitialUris = uris
                                                                    importInitialMergeProject = mergeProj
                                                                    showImportWorkspace = true
                                                                },
                                                                openSpeakerManagerTrigger = openSpeakerManagerTrigger,
                                                                onSpeakerManagerTriggerConsumed = { openSpeakerManagerTrigger = 0 },
                                                                isSplitPaneMode = false,
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .predictiveBackLayer(mainTabBackState)
                                                            )
                                                        }

                                                        AppScreen.RecordingWorkspace -> {
                                                            if (currentProject == null) {
                                                                NoProjectPlaceholder(
                                                                    title = S.nav.selectProjectFirstTitle,
                                                                    description = S.nav.selectProjectRecordingHint,
                                                                    icon = Icons.Default.Mic,
                                                                    buttonText = S.nav.goSelectProject,
                                                                    onNavigateToProjects = { currentScreen = AppScreen.ProjectList }
                                                                )
                                                            } else {
                                                                RecordingScreen(
                                                                    viewModel = viewModel,
                                                                    onNavigateBack = { currentScreen = AppScreen.ProjectList },
                                                                    onViewArchive = { currentScreen = AppScreen.ArchiveExplorer },
                                                                    onOpenFullSettings = { currentScreen = AppScreen.Settings },
                                                                    onOpenWirelessLink = { showWirelessPairingDialog = true },
                                                                    isSplitPaneMode = false,
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .predictiveBackLayer(mainTabBackState)
                                                                )
                                                            }
                                                        }

                                                        AppScreen.ArchiveExplorer -> {
                                                            if (currentProject == null) {
                                                                NoProjectPlaceholder(
                                                                    title = S.nav.selectProjectFirstTitle,
                                                                    description = if (isSimpleMode) {
                                                                        S.nav.selectProjectArchiveHintSimple
                                                                    } else {
                                                                        S.nav.selectProjectArchiveHintAdv
                                                                    },
                                                                    icon = Icons.Default.Folder,
                                                                    buttonText = S.nav.goSelectProject,
                                                                    onNavigateToProjects = { currentScreen = AppScreen.ProjectList }
                                                                )
                                                            } else {
                                                                ArchiveExplorerScreen(
                                                                    viewModel = viewModel,
                                                                    onNavigateBack = { currentScreen = AppScreen.RecordingWorkspace },
                                                                    onSelectedWordPosition = { seqPos ->
                                                                        viewModel.selectWordBySequencePosition(seqPos)
                                                                        currentScreen = AppScreen.RecordingWorkspace
                                                                    },
                                                                    isTablet = false,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                        }

                                                        else -> Unit
                                                    }
                                                }
                                            }
                                        }

                                        "TEXTGRID_VIEWER" -> {
                                            val activeSession = textGridViewerSession
                                            if (activeSession != null) {
                                                TextGridViewerHostOverlay(
                                                    session = activeSession,
                                                    viewModel = viewModel,
                                                    onClose = { viewModel.closeTextGridViewer() },
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .then(androidNavBarInsetModifier)
                                                )
                                            }
                                        }

                                        "IMMERSIVE_RECORDING" -> {
                                            RecordingScreen(
                                                viewModel = viewModel,
                                                onNavigateBack = {
                                                    viewModel.setParticipantCleanMode(false)
                                                },
                                                onViewArchive = {
                                                    viewModel.setParticipantCleanMode(false)
                                                    currentScreen = AppScreen.ArchiveExplorer
                                                },
                                                onOpenFullSettings = {
                                                    viewModel.setParticipantCleanMode(false)
                                                    showQuickRecordingSettingsInSidebar = false
                                                    currentScreen = AppScreen.Settings
                                                },
                                                onToggleQuickSettingsSidebar = if (isExpandedSplit) {
                                                    {
                                                        viewModel.setParticipantCleanMode(false)
                                                        DesktopAdaptiveState.isProjectSidebarCollapsed = false
                                                        showQuickRecordingSettingsInSidebar = true
                                                    }
                                                } else {
                                                    null
                                                },
                                                isSplitPaneMode = isExpandedSplit,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(androidNavBarInsetModifier)
                                            )
                                        }

                                        "GLOBAL_MANAGEMENT" -> {
                                            GlobalManagementPane(
                                                viewModel = viewModel,
                                                onDismiss = { viewModel.setShowGlobalManagement(false) },
                                                onOpenProjectInWorkspace = {
                                                    if (!isExpandedSplit) {
                                                        currentScreen = AppScreen.RecordingWorkspace
                                                    }
                                                }
                                            )
                                        }

                                        "IMPORT_WORKSPACE" -> {
                                            SmartImportWorkspaceScreen(
                                                viewModel = viewModel,
                                                initialUris = importInitialUris,
                                                initialMergeProject = importInitialMergeProject,
                                                onBack = {
                                                    showImportWorkspace = false
                                                    importInitialUris = emptyList()
                                                    importInitialMergeProject = null
                                                },
                                                onImportCompleted = {
                                                    showImportWorkspace = false
                                                    importInitialUris = emptyList()
                                                    importInitialMergeProject = null
                                                    currentScreen = if (isExpandedSplit) AppScreen.ProjectList else AppScreen.RecordingWorkspace
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        "WORD_EDITOR" -> {
                                            WordListEditorScreen(
                                                viewModel = viewModel,
                                                initialProject = wordEditorInitialProject,
                                                onBack = { showWordListEditor = false },
                                                onSavedAndOpenRecording = { savedProj ->
                                                    viewModel.selectProject(savedProj)
                                                    showWordListEditor = false
                                                    currentScreen = if (isExpandedSplit) AppScreen.ProjectList else AppScreen.RecordingWorkspace
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        "USER_MANUAL" -> {
                                            UserManualScreen(
                                                onBack = { showUserManualOverlay = false },
                                                isSimpleMode = isSimpleMode,
                                                onOpenEncyclopedia = {
                                                    showUserManualOverlay = false
                                                    showEncyclopediaOverlay = true
                                                },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(androidNavBarInsetModifier)
                                            )
                                        }

                                        "ENCYCLOPEDIA" -> {
                                            FieldworkEncyclopediaScreen(
                                                onBack = { showEncyclopediaOverlay = false },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(androidNavBarInsetModifier)
                                            )
                                        }

                                        "SOUND_CHECK" -> {
                                            SoundCheckScreen(
                                                viewModel = viewModel,
                                                onBack = { showRoomNoiseCheck = false },
                                                onGoToRecordingWorkspace = {
                                                    showRoomNoiseCheck = false
                                                    currentScreen = if (isExpandedSplit) AppScreen.ProjectList else AppScreen.RecordingWorkspace
                                                },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(androidNavBarInsetModifier)
                                            )
                                        }

                                        "SETTINGS" -> {
                                            SettingsScreen(
                                                viewModel = viewModel,
                                                onBack = {
                                                    pendingSettingsCategoryId = null
                                                    currentScreen = AppScreen.ProjectList
                                                },
                                                onOpenRecordingsCleanup = { viewModel.setShowGlobalManagement(true) },
                                                initialCategoryId = pendingSettingsCategoryId,
                                                categoryJumpSeq = settingsCategoryJumpSeq,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        "ARCHIVE_EXPLORER" -> {
                                            if (currentProject == null) {
                                                NoProjectPlaceholder(
                                                    title = S.nav.selectProjectFirstTitle,
                                                    description = if (isSimpleMode) {
                                                        S.nav.selectProjectArchiveHintSimple
                                                    } else {
                                                        S.nav.selectProjectArchiveHintAdv
                                                    },
                                                    icon = Icons.Default.Folder,
                                                    buttonText = if (isExpandedSplit) S.nav.backToProjectAndStudio else S.nav.goSelectProject,
                                                    onNavigateToProjects = { currentScreen = AppScreen.ProjectList }
                                                )
                                            } else {
                                                ArchiveExplorerScreen(
                                                    viewModel = viewModel,
                                                    onNavigateBack = {
                                                        currentScreen = if (isExpandedSplit) AppScreen.ProjectList else AppScreen.RecordingWorkspace
                                                    },
                                                    onSelectedWordPosition = { seqPos ->
                                                        viewModel.selectWordBySequencePosition(seqPos)
                                                        currentScreen = if (isExpandedSplit) AppScreen.ProjectList else AppScreen.RecordingWorkspace
                                                    },
                                                    isTablet = !isCompactWidth,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .then(androidNavBarInsetModifier)
                                                )
                                            }
                                        }

                                        "PROJECT_LIST" -> {
                                            ProjectListScreen(
                                                viewModel = viewModel,
                                                onProjectOpened = {
                                                    currentScreen = AppScreen.RecordingWorkspace
                                                },
                                                onOpenDrawer = null,
                                                onOpenSettings = { currentScreen = AppScreen.Settings },
                                                onOpenWirelessLink = { showWirelessPairingDialog = true },
                                                onOpenWordListEditor = { proj ->
                                                    wordEditorInitialProject = proj
                                                    showWordListEditor = true
                                                },
                                                onOpenImportPage = { uris, mergeProj ->
                                                    importInitialUris = uris
                                                    importInitialMergeProject = mergeProj
                                                    showImportWorkspace = true
                                                },
                                                openSpeakerManagerTrigger = openSpeakerManagerTrigger,
                                                onSpeakerManagerTriggerConsumed = { openSpeakerManagerTrigger = 0 },
                                                isSplitPaneMode = !isCompactWidth,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .then(androidNavBarInsetModifier)
                                                    .predictiveBackLayer(mainTabBackState)
                                            )
                                        }

                                        "RECORDING_WORKSPACE" -> {
                                            if (currentProject == null) {
                                                NoProjectPlaceholder(
                                                    title = S.nav.selectProjectFirstTitle,
                                                    description = S.nav.selectProjectRecordingHint,
                                                    icon = Icons.Default.Mic,
                                                    buttonText = S.nav.goSelectProject,
                                                    onNavigateToProjects = { currentScreen = AppScreen.ProjectList }
                                                )
                                            } else {
                                                RecordingScreen(
                                                    viewModel = viewModel,
                                                    onNavigateBack = { currentScreen = AppScreen.ProjectList },
                                                    onViewArchive = { currentScreen = AppScreen.ArchiveExplorer },
                                                    onOpenFullSettings = { currentScreen = AppScreen.Settings },
                                                    onOpenWirelessLink = { showWirelessPairingDialog = true },
                                                    isSplitPaneMode = false,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .then(androidNavBarInsetModifier)
                                                        .predictiveBackLayer(mainTabBackState)
                                                )
                                            }
                                        }

                                        else -> {
                                            // Expanded / UltraWide Split Workspace: Collapsible Left Project/QuickSettings Sidebar + Recording Stage
                                            val showLeftSidebar = !DesktopAdaptiveState.isProjectSidebarCollapsed &&
                                                !(hasDockedSideOverlay && !isUltraWide)

                                            Row(modifier = Modifier.fillMaxSize()) {
                                                AnimatedVisibility(
                                                    visible = showLeftSidebar,
                                                    enter = StudioMotion.horizontalPaneEnter,
                                                    exit = StudioMotion.horizontalPaneExit
                                                ) {
                                                    Row(modifier = Modifier.fillMaxHeight()) {
                                                        val leftSidebarMode = if (showQuickRecordingSettingsInSidebar) 1 else 0
                                                        val leftSidebarWidth = if (windowMetrics.widthDp >= 1200.dp) 348.dp else 320.dp

                                                        AnimatedContent(
                                                            targetState = leftSidebarMode,
                                                            transitionSpec = {
                                                                if (targetState > initialState) {
                                                                    (slideInHorizontally(StudioMotion.mediumInTween()) { it / 5 } + fadeIn(StudioMotion.shortInTween())) togetherWith
                                                                        (slideOutHorizontally(StudioMotion.mediumOutTween()) { -it / 6 } + fadeOut(StudioMotion.shortOutTween()))
                                                                } else {
                                                                    (slideInHorizontally(StudioMotion.mediumInTween()) { -it / 6 } + fadeIn(StudioMotion.shortInTween())) togetherWith
                                                                        (slideOutHorizontally(StudioMotion.mediumOutTween()) { it / 5 } + fadeOut(StudioMotion.shortOutTween()))
                                                                }
                                                            },
                                                            label = "LeftSidebarAnimatedTransition",
                                                            modifier = Modifier
                                                                .width(leftSidebarWidth)
                                                                .fillMaxHeight()
                                                                .clipToBounds()
                                                                .predictiveBackLayer(mainTabBackState)
                                                        ) { sidebarMode ->
                                                            when (sidebarMode) {
                                                                1 -> {
                                                                    RecordingQuickSettingsSidebarPane(
                                                                        viewModel = viewModel,
                                                                        onOpenFullSettings = {
                                                                            showQuickRecordingSettingsInSidebar = false
                                                                            currentScreen = AppScreen.Settings
                                                                        },
                                                                        onClose = {
                                                                            showQuickRecordingSettingsInSidebar = false
                                                                        },
                                                                        modifier = Modifier.fillMaxSize()
                                                                    )
                                                                }
                                                                else -> {
                                                                    ProjectListScreen(
                                                                        viewModel = viewModel,
                                                                        onProjectOpened = { },
                                                                        onOpenSettings = {
                                                                            showQuickRecordingSettingsInSidebar = false
                                                                            currentScreen = AppScreen.Settings
                                                                        },
                                                                        onOpenWirelessLink = {
                                                                            showWirelessPairingDialog = true
                                                                        },
                                                                        onOpenWordListEditor = { proj ->
                                                                            wordEditorInitialProject = proj
                                                                            showWordListEditor = true
                                                                        },
                                                                        onOpenImportPage = { uris, mergeProj ->
                                                                            importInitialUris = uris
                                                                            importInitialMergeProject = mergeProj
                                                                            showImportWorkspace = true
                                                                        },
                                                                        openSpeakerManagerTrigger = openSpeakerManagerTrigger,
                                                                        onSpeakerManagerTriggerConsumed = { openSpeakerManagerTrigger = 0 },
                                                                        isSplitPaneMode = true,
                                                                        modifier = Modifier.fillMaxSize()
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        VerticalDivider(
                                                            modifier = Modifier.fillMaxHeight().width(1.dp),
                                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                        )
                                                    }
                                                }

                                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                                    AnimatedContent(
                                                        targetState = currentProject?.id ?: -1L,
                                                        transitionSpec = {
                                                            StudioMotion.subtleFadeScale()
                                                        },
                                                        label = "TabletWorkspaceTransition"
                                                    ) { activeProjectId ->
                                                        if (activeProjectId <= 0L || currentProject == null) {
                                                            NoProjectPlaceholder(
                                                                title = S.nav.selectProjectFirstTitle,
                                                                description = S.nav.selectProjectRecordingSidebarHint,
                                                                icon = Icons.Default.Mic,
                                                                buttonText = S.nav.expandProjectList,
                                                                onNavigateToProjects = {
                                                                    showQuickRecordingSettingsInSidebar = false
                                                                    DesktopAdaptiveState.isProjectSidebarCollapsed = false
                                                                    currentScreen = AppScreen.ProjectList
                                                                }
                                                            )
                                                        } else {
                                                            RecordingScreen(
                                                                viewModel = viewModel,
                                                                onNavigateBack = {
                                                                    showQuickRecordingSettingsInSidebar = false
                                                                    viewModel.selectProject(null)
                                                                },
                                                                onViewArchive = {
                                                                    showQuickRecordingSettingsInSidebar = false
                                                                    currentScreen = AppScreen.ArchiveExplorer
                                                                },
                                                                onOpenFullSettings = {
                                                                    showQuickRecordingSettingsInSidebar = false
                                                                    currentScreen = AppScreen.Settings
                                                                },
                                                                onOpenWirelessLink = {
                                                                    showWirelessPairingDialog = true
                                                                },
                                                                onToggleQuickSettingsSidebar = {
                                                                    if (showQuickRecordingSettingsInSidebar && !DesktopAdaptiveState.isProjectSidebarCollapsed) {
                                                                        showQuickRecordingSettingsInSidebar = false
                                                                    } else {
                                                                        DesktopAdaptiveState.isProjectSidebarCollapsed = false
                                                                        showQuickRecordingSettingsInSidebar = true
                                                                    }
                                                                },
                                                                isSplitPaneMode = true,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // P0-1 Fixed Right Docked Inspector: constant 390dp width + permanent divider + horizontal squeeze + cached exit content
                                AnimatedVisibility(
                                    visible = hasDockedSideOverlay,
                                    enter = StudioMotion.horizontalPaneEnterFromEnd(),
                                    exit = StudioMotion.horizontalPaneExitToEnd()
                                ) {
                                    Row(modifier = Modifier.fillMaxHeight()) {
                                        VerticalDivider(
                                            modifier = Modifier.fillMaxHeight().width(1.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .width(390.dp)
                                                .fillMaxHeight()
                                                .clipToBounds()
                                                .predictiveBackLayer(tabletSidePaneBackState)
                                                .background(MaterialTheme.colorScheme.surface)
                                        ) {
                                            val displayedOverlay = if (hasDockedSideOverlay) {
                                                showGlobalManagement to showPhoneticWord
                                            } else {
                                                cachedSideOverlay
                                            }
                                            AnimatedContent(
                                                targetState = displayedOverlay,
                                                contentKey = { Pair(it.first, it.second?.id) },
                                                transitionSpec = { StudioMotion.subtleFadeScale() },
                                                label = "SideOverlayContentTransition"
                                            ) { (isGlobal, phoneticWord) ->
                                                if (isGlobal) {
                                                    GlobalManagementPane(
                                                        viewModel = viewModel,
                                                        onDismiss = { viewModel.setShowGlobalManagement(false) }
                                                    )
                                                } else if (phoneticWord != null) {
                                                    PhoneticDetailsPane(
                                                        word = phoneticWord,
                                                        speaker = currentSpeaker,
                                                        viewModel = viewModel,
                                                        onDismiss = { viewModel.showPhoneticWordDetail(null) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    // Non-docked PhoneticDetails modal (Compact / Medium / <1120dp windows)
                    if (showPhoneticWord != null && !canDockRightInspector && !hasTextGridViewerOpen) {
                        PhoneticDetailsBottomSheet(
                            word = showPhoneticWord!!,
                            speaker = currentSpeaker,
                            viewModel = viewModel,
                            onDismiss = { viewModel.showPhoneticWordDetail(null) }
                        )
                    }

                    // Full-Screen Multi-Device Wireless Collaboration Workspaces (看读端 / 遥控端)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = activeLinkRole != LinkRole.STANDALONE && (isLinkConnected || !showWirelessPairingDialog),
                        enter = fadeIn(StudioMotion.pageInTween()) + scaleIn(initialScale = 0.975f, animationSpec = StudioMotion.pageInTween()),
                        exit = fadeOut(StudioMotion.pageOutTween()) + scaleOut(targetScale = 0.985f, animationSpec = StudioMotion.pageOutTween()),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AnimatedContent(
                            targetState = activeLinkRole,
                            transitionSpec = { StudioMotion.subtleFadeScale() },
                            label = "WirelessLinkRoleTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { linkRole ->
                            when (linkRole) {
                                LinkRole.PROMPT_DISPLAY -> {
                                    PromptDisplayLinkScreen(
                                        viewModel = viewModel,
                                        onExitSession = { viewModel.wirelessLink.cancelOrDisconnect() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                LinkRole.CONTROLLER -> {
                                    RemoteControllerLinkScreen(
                                        viewModel = viewModel,
                                        onExitSession = { viewModel.wirelessLink.cancelOrDisconnect() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                else -> {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }

                    com.example.ui.components.AnimatedDialogVisibility(visible = showWirelessPairingDialog) {
                        WirelessLinkPairingDialog(
                            viewModel = viewModel,
                            onDismiss = { showWirelessPairingDialog = false },
                            onOpenLinkSettings = {
                                closePrimaryOverlays()
                                currentScreen = AppScreen.Settings
                            }
                        )
                    }

                    com.example.ui.components.AnimatedDialogVisibility(visible = showRecordingTips || !hasSeenRecordingTips) {
                        RecordingTipsDialog(
                            isFirstLaunch = !hasSeenRecordingTips,
                            isSimpleMode = isSimpleMode,
                            onDismiss = {
                                showRecordingTips = false
                                viewModel.markRecordingTipsSeen()
                            }
                        )
                    }

                    com.example.ui.components.AnimatedDialogVisibility(visible = showAboutDialog) {
                        AboutPhonAcqDialog(
                            onDismiss = { showAboutDialog = false }
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isGlobalDropHovering,
                        enter = fadeIn(StudioMotion.microTween()),
                        exit = fadeOut(StudioMotion.microTween()),
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
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
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
        }
    }
}

@Composable
private fun AppNavigationDrawerContent(
    projectCount: Int,
    speakerCount: Int,
    themeMode: String,
    isSimpleMode: Boolean = false,
    onCycleTheme: () -> Unit,
    onOpenSpeakerManager: () -> Unit,
    onOpenRecordingStats: () -> Unit,
    onOpenWordListEditor: () -> Unit,
    onOpenUserManual: () -> Unit,
    onOpenEncyclopedia: () -> Unit,
    onOpenRoomNoiseCheck: () -> Unit,
    onOpenRecordingTips: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWirelessLink: (() -> Unit)? = null,
    onOpenAbout: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier
            .width(300.dp)
            .testTag("app_navigation_drawer"),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            // Drawer Header
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PhonAcqLogo(
                                modifier = Modifier.size(38.dp)
                            )
                            Column {
                                Text(
                                    text = S.nav.drawerTitle,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = S.nav.drawerSubtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = onCycleTheme,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .testTag("drawer_theme_toggle_button")
                        ) {
                            val icon = when (themeMode) {
                                "LIGHT" -> Icons.Default.LightMode
                                "DARK" -> Icons.Default.DarkMode
                                else -> Icons.Default.BrightnessAuto
                            }
                            val labelText = when (themeMode) {
                                "LIGHT" -> S.nav.themeLightShort
                                "DARK" -> S.nav.themeDarkShort
                                else -> S.nav.themeSystemShort
                            }
                            Icon(icon, contentDescription = labelText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(labelText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        text = S.nav.drawerSummary.withArgs("projects" to projectCount, "speakers" to speakerCount),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = S.nav.drawerGroupCommonManagement,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            NavigationDrawerItem(
                label = { Text(S.nav.speakerManager, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                selected = false,
                onClick = onOpenSpeakerManager,
                modifier = Modifier.testTag("drawer_item_speakers")
            )

            if (!isSimpleMode) {
                NavigationDrawerItem(
                    label = { Text(S.nav.recordingStatsAndCleanup, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.PieChart, contentDescription = null) },
                    selected = false,
                    onClick = onOpenRecordingStats,
                    modifier = Modifier.testTag("drawer_item_stats")
                )
            }

            NavigationDrawerItem(
                label = { Text(S.nav.wordListEditor, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                selected = false,
                onClick = onOpenWordListEditor,
                modifier = Modifier.testTag("drawer_item_word_editor")
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                text = S.nav.drawerGroupUtilities,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (onOpenWirelessLink != null) {
                NavigationDrawerItem(
                    label = { Text(S.nav.wirelessLink, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                    selected = false,
                    onClick = onOpenWirelessLink,
                    modifier = Modifier.testTag("drawer_item_wireless_link")
                )
            }

            NavigationDrawerItem(
                label = { Text(S.nav.soundCheck, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                selected = false,
                onClick = onOpenRoomNoiseCheck,
                modifier = Modifier.testTag("drawer_item_noise_check")
            )

            NavigationDrawerItem(
                label = { Text(S.nav.recordingTips, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
                selected = false,
                onClick = onOpenRecordingTips,
                modifier = Modifier.testTag("drawer_item_tips")
            )

            NavigationDrawerItem(
                label = { Text(S.nav.userManual, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                selected = false,
                onClick = onOpenUserManual,
                modifier = Modifier.testTag("drawer_item_user_manual")
            )

            NavigationDrawerItem(
                label = { Text(S.nav.encyclopedia, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                selected = false,
                onClick = onOpenEncyclopedia,
                modifier = Modifier.testTag("drawer_item_encyclopedia")
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            NavigationDrawerItem(
                label = { Text(S.nav.settings, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.testTag("drawer_item_settings")
            )

            NavigationDrawerItem(
                label = { Text(S.nav.aboutPhonAcq, fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                selected = false,
                onClick = onOpenAbout,
                modifier = Modifier.testTag("drawer_item_about")
            )
        }
    }
}

@Composable
fun NoProjectPlaceholder(
    title: String,
    description: String,
    icon: ImageVector,
    buttonText: String,
    onNavigateToProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.size(104.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNavigateToProjects,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .height(48.dp)
                .testTag("onboarding_go_to_projects_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun BoxTransition(
    targetState: AppScreen,
    modifier: Modifier = Modifier,
    content: @Composable (AppScreen) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
                durationMillis = 240,
                easing = FastOutSlowInEasing
            )
            val isMovingRight = targetState.index >= initialState.index
            val enter = slideInHorizontally(slideSpec) { width ->
                if (isMovingRight) width / 5 else -width / 5
            } + fadeIn(tween(durationMillis = 200, easing = LinearOutSlowInEasing))
            val exit = slideOutHorizontally(slideSpec) { width ->
                if (isMovingRight) -width / 6 else width / 6
            } + fadeOut(tween(durationMillis = 150, easing = FastOutLinearInEasing))

            (enter togetherWith exit).apply {
                targetContentZIndex = 1f
            }
        },
        modifier = modifier.clipToBounds(),
        label = "AppScreenTransition"
    ) { screen ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            content(screen)
        }
    }
}

@Composable
fun PhoneticDetailsPane(
    word: Word,
    speaker: Speaker?,
    viewModel: RecordingViewModel? = null,
    onDismiss: () -> Unit
) {
    PhoneticDetailsContent(
        word = word,
        speaker = speaker,
        viewModel = viewModel,
        onDismiss = onDismiss,
        isDockedPane = true
    )
}

private data class LoadedViewerDocumentPayload(
    val doc: TextGridDocument,
    val collectionDocs: List<TextGridDocument>,
    val anomalies: List<TextGridAnomaly>,
    val rawSource: String,
    val formatVariant: TextGridFormatVariant,
    val timeDecimals: Int?
)

@Composable
private fun TextGridViewerHostOverlay(
    session: TextGridViewerSession,
    viewModel: RecordingViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tgVis = S.textGrid.visual

    val visualConfig by viewModel.textGridVisualConfig.collectAsState()
    val formatConfig by viewModel.textGridFormatConfig.collectAsState()
    val allSpeakers by viewModel.allSpeakers.collectAsState()
    val currentSpeaker by viewModel.currentSpeaker.collectAsState()
    val playingWordId by viewModel.playingWordId.collectAsState()
    val isRangePlaybackActive by viewModel.isRangePlaybackActive.collectAsState()
    val playbackCurrentTimeSec by viewModel.playbackCurrentTimeSec.collectAsState()

    val isPlayingViewerAudio = playingWordId == RecordingViewModel.TEXTGRID_PLAYBACK_TAG_ID && isRangePlaybackActive

    val sessionKey = remember(session) {
        when (session) {
            is TextGridViewerSession.WordEntry -> "word_${session.word.id}_${session.audioFile?.absolutePath.orEmpty()}"
            is TextGridViewerSession.ExternalFile -> "ext_${session.textGridFile.absolutePath}_${session.audioFile?.absolutePath.orEmpty()}"
        }
    }

    val loadedPayload by produceState<LoadedViewerDocumentPayload?>(
        initialValue = null,
        key1 = sessionKey
    ) {
        value = when (session) {
            is TextGridViewerSession.WordEntry -> {
                val targetSpeaker = session.speakerId?.let { sid -> allSpeakers.firstOrNull { it.id == sid } } ?: currentSpeaker
                val loaded = viewModel.loadWordTextGridDocument(
                    word = session.word,
                    audioFile = session.audioFile,
                    speaker = targetSpeaker
                )
                LoadedViewerDocumentPayload(
                    doc = loaded.document,
                    collectionDocs = listOf(loaded.document),
                    anomalies = emptyList(),
                    rawSource = loaded.rawSource,
                    formatVariant = loaded.formatVariant,
                    timeDecimals = formatConfig.effectiveTimeDecimals()
                )
            }
            is TextGridViewerSession.ExternalFile -> {
                withContext(Dispatchers.IO) {
                    val parsed = TextGridCodec.parseFile(session.textGridFile)
                    val doc = parsed.document
                    if (doc != null) {
                        val rawSrc = parsed.normalizedSource ?: TextGridCodec.serialize(doc, parsed.detectedVariant)
                        LoadedViewerDocumentPayload(
                            doc = doc,
                            collectionDocs = parsed.collectionDocuments.ifEmpty { listOf(doc) },
                            anomalies = parsed.anomalies,
                            rawSource = rawSrc,
                            formatVariant = parsed.detectedVariant,
                            timeDecimals = null
                        )
                    } else {
                        val errMsg = parsed.fatalDiagnostics.firstOrNull()?.message ?: S.common.unknownError
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                tgVis.loadTextGridFailedToast.withArgs("msg" to errMsg),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        val fallbackDoc = TextGridDocument.empty(xmax = 1.0, name = session.textGridFile.nameWithoutExtension)
                        LoadedViewerDocumentPayload(
                            doc = fallbackDoc,
                            collectionDocs = listOf(fallbackDoc),
                            anomalies = emptyList(),
                            rawSource = runCatching { session.textGridFile.readText(Charsets.UTF_8) }
                                .getOrElse { TextGridCodec.serialize(fallbackDoc, TextGridFormatVariant.LONG_TEXT) },
                            formatVariant = TextGridFormatVariant.LONG_TEXT,
                            timeDecimals = null
                        )
                    }
                }
            }
        }
    }

    var pendingExportTextGridContent by remember { mutableStateOf<String?>(null) }
    var pendingExportSvgContent by remember { mutableStateOf<String?>(null) }

    val saveTextGridLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingExportTextGridContent
        pendingExportTextGridContent = null
        if (uri != null && !content.isNullOrBlank()) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(content.toByteArray(Charsets.UTF_8))
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

    val saveSvgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        val svgContent = pendingExportSvgContent
        pendingExportSvgContent = null
        if (uri != null && !svgContent.isNullOrBlank()) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(svgContent.toByteArray(Charsets.UTF_8))
                            os.flush()
                            true
                        } ?: false
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) tgVis.exportSvgSuccess(".svg") else S.archive.exportFailedShortToast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val backVisualState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = true,
        state = backVisualState
    ) {
        viewModel.stopPlayback()
        onClose()
    }

    val payload = loadedPayload
    if (payload == null) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = S.common.loading,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    fun exportTextGridSource(src: String) {
        val safeStem = session.title.replace(Regex("[/\\\\:*?\"<>|\\s]+"), "_").ifBlank { "annotation" }.take(32)
        val fileName = if (safeStem.endsWith(".TextGrid", ignoreCase = true)) safeStem else "$safeStem.TextGrid"
        if (DesktopAdaptiveState.isDesktopPlatform) {
            pendingExportTextGridContent = src
            saveTextGridLauncher.launch(fileName)
        } else {
            try {
                val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val tgFile = File(cacheDir, fileName)
                tgFile.writeText(src, Charsets.UTF_8)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tgFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, S.archive.shareSingleTextGridTitle))
            } catch (e: Exception) {
                Toast.makeText(context, S.archive.exportFailedMsgToast.withArgs("msg" to e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportSvgFigure(svg: String) {
        val safeStem = session.title.replace(Regex("[/\\\\:*?\"<>|\\s]+"), "_").ifBlank { "textgrid_score" }.take(32)
        val fileName = "$safeStem.svg"
        if (DesktopAdaptiveState.isDesktopPlatform) {
            pendingExportSvgContent = svg
            saveSvgLauncher.launch(fileName)
        } else {
            try {
                val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val svgFile = File(cacheDir, fileName)
                svgFile.writeText(svg, Charsets.UTF_8)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    svgFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/svg+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, tgVis.exportSvg))
            } catch (e: Exception) {
                Toast.makeText(context, S.archive.exportFailedMsgToast.withArgs("msg" to e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .predictiveBackLayer(backVisualState)
    ) {
        TextGridFullViewerScreen(
            title = session.title,
            document = payload.doc,
            collectionDocuments = payload.collectionDocs,
            anomalies = payload.anomalies,
            pairedAudioFile = session.audioFile,
            visualConfig = visualConfig,
            timeDecimals = payload.timeDecimals,
            formatVariant = payload.formatVariant,
            rawSourceOverride = payload.rawSource,
            isPlaying = isPlayingViewerAudio,
            playheadTimeSec = if (isPlayingViewerAudio) playbackCurrentTimeSec else null,
            onVisualConfigChange = { viewModel.setTextGridVisualConfig(it) },
            onPlayRange = { startSec, endSec, speed, loop ->
                viewModel.playAudioRange(
                    audioFile = session.audioFile,
                    startSec = startSec,
                    endSec = endSec,
                    loop = loop,
                    rate = speed,
                    totalDurationSec = payload.doc.duration
                )
            },
            onStopPlayback = { viewModel.stopPlayback() },
            onExportTextGrid = { _, src -> exportTextGridSource(src) },
            onExportSvg = { _, svg -> exportSvgFigure(svg) },
            onNotify = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
            onClose = {
                viewModel.stopPlayback()
                onClose()
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private object CachedPhonAcqLogoPaths {
    private fun parseEvenOdd(d: String) = PathParser().parsePathString(d).toPath().apply {
        fillType = PathFillType.EvenOdd
    }

    val waveBars by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            parseEvenOdd("M2232.85 1072.89C2238.46 1053.67 2258.59 1042.63 2277.81 1048.24C2297.04 1053.84 2308.07 1073.97 2302.47 1093.2L2195.77 1458.95C2190.17 1478.18 2170.04 1489.21 2150.81 1483.61C2131.59 1478 2120.55 1457.87 2126.16 1438.64Z"),
            parseEvenOdd("M2221.49 1172.62C2215.37 1153.15 2226.2 1132.41 2245.67 1126.29C2265.14 1120.17 2285.89 1131 2292.01 1150.47L2357.6 1359.22C2363.72 1378.69 2352.89 1399.44 2333.42 1405.55C2313.95 1411.67 2293.2 1400.85 2287.08 1381.38Z"),
            parseEvenOdd("M2437 1193 l290 43 l-263.09 61 Z"),
            parseEvenOdd("M2367.78 1187.87C2355.61 1171.48 2359.03 1148.33 2375.42 1136.16C2391.81 1123.99 2414.95 1127.41 2427.12 1143.8L2499.19 1240.84C2511.36 1257.23 2507.94 1280.38 2491.55 1292.55C2475.17 1304.72 2452.02 1301.3 2439.85 1284.91Z"),
            parseEvenOdd("M2356.18 1149.5C2362.09 1129.97 2382.72 1118.92 2402.25 1124.83C2421.79 1130.74 2432.83 1151.37 2426.92 1170.91L2359.8 1392.72C2353.89 1412.26 2333.26 1423.31 2313.73 1417.39C2294.19 1411.48 2283.15 1390.85 2289.06 1371.32Z"),
            parseEvenOdd("M2095.06 1089.57C2100.65 1070.4 2120.72 1059.39 2139.89 1064.98C2159.06 1070.58 2170.07 1090.65 2164.48 1109.82L2099.08 1334.02C2093.49 1353.19 2073.41 1364.2 2054.24 1358.6C2035.07 1353.01 2024.07 1332.94 2029.66 1313.77Z"),
            parseEvenOdd("M1938.48 1191.69C1950.53 1175.47 1973.44 1172.09 1989.66 1184.14C2005.89 1196.18 2009.27 1219.1 1997.22 1235.32L1958.39 1287.6C1946.34 1303.82 1923.42 1307.2 1907.2 1295.16C1890.98 1283.11 1887.6 1260.19 1899.65 1243.97Z"),
            parseEvenOdd("M1944.59 1230.24C1932.13 1214.33 1934.92 1191.34 1950.83 1178.87C1966.73 1166.41 1989.73 1169.2 2002.19 1185.11L2093.48 1301.61C2105.94 1317.51 2103.15 1340.51 2087.24 1352.97C2071.34 1365.43 2048.34 1362.64 2035.88 1346.74Z")
        )
    }

    val leftPointerWing by lazy(LazyThreadSafetyMode.NONE) {
        parseEvenOdd("M0 0 L290 54.747 L26.907 108 Z")
    }

    val crescentRing by lazy(LazyThreadSafetyMode.NONE) {
        parseEvenOdd(
            "M1663.2 1278.4 l216.2 52.69 l1.47 5.71 C1923.18 1472.74 2050.07 1571.43 2200.02 1571.43 C2349.98 1571.43 2476.86 1472.74 2519.17 1336.8 L2520.42 1331.97 L2736.8 1279.23 L2736.13 1292.56 C2708.53 1564.1 2479.04 1776 2200.02 1776 C1921 1776 1691.51 1564.1 1663.92 1292.56 Z " +
                "M2200.02 699 C2479.04 699 2708.53 910.899 2736.13 1182.44 L2737 1199.72 L2521.38 1146.77 L2519.17 1138.2 C2476.86 1002.26 2349.98 903.565 2200.02 903.565 C2050.07 903.565 1923.18 1002.26 1880.87 1138.2 L1878.44 1147.66 L1663 1200.56 L1663.92 1182.44 C1691.51 910.899 1921 699 2200.02 699 Z"
        )
    }
}

/**
 * 动态跟随 Material You 与明暗主题取色的应用内 PhonAcq Logo
 */
@Composable
fun PhonAcqLogo(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val ringColor = colorScheme.primary
    val waveColor = lerp(colorScheme.tertiary, colorScheme.secondary, 0.35f)

    Canvas(modifier = modifier) {
        val scale = size.minDimension / 108f
        withTransform({
            scale(scale, scale, Offset.Zero)
            translate(14.15f, 14.15f)
            scale(0.074f, 0.074f, Offset.Zero)
            translate(-1661f, -699f)
        }) {
            CachedPhonAcqLogoPaths.waveBars.forEach { path ->
                drawPath(path = path, color = waveColor)
            }
            withTransform({
                translate(1962f, 1186f)
                scale(-1f, 1f, Offset.Zero)
            }) {
                drawPath(path = CachedPhonAcqLogoPaths.leftPointerWing, color = waveColor)
            }
            drawPath(path = CachedPhonAcqLogoPaths.crescentRing, color = ringColor)
        }
    }
}

