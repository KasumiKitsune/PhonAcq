package com.example.ui.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AcousticAnalyzer
import com.example.audio.CanonicalToneMember
import com.example.audio.CorpusPhonologyAnalyzer
import com.example.data.Project
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.LinkRole
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.viewmodel.RecordingViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var tabletLeftPane by remember { mutableStateOf<AppScreen>(AppScreen.ProjectList) }
    val currentProject by viewModel.currentProject.collectAsState()
    val showGlobalManagement by viewModel.showGlobalManagement.collectAsState()
    val showPhoneticWord by viewModel.showPhoneticWord.collectAsState()
    val currentSpeaker by viewModel.currentSpeaker.collectAsState()
    val allSpeakers by viewModel.allSpeakers.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val hasSelectedUiMode by viewModel.hasSelectedUiMode.collectAsState()
    val hasSeenRecordingTips by viewModel.hasSeenRecordingTips.collectAsState()
    val participantCleanMode by viewModel.participantCleanMode.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var openSpeakerManagerTrigger by remember { mutableIntStateOf(0) }
    var showSettingsOverlay by remember { mutableStateOf(false) }
    var showUserManualOverlay by remember { mutableStateOf(false) }
    var showEncyclopediaOverlay by remember { mutableStateOf(false) }
    var showWordListEditor by remember { mutableStateOf(false) }
    var wordEditorInitialProject by remember { mutableStateOf<Project?>(null) }

    var showRoomNoiseCheck by remember { mutableStateOf(false) }
    var showRecordingTips by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showQuickRecordingSettingsInSidebar by remember { mutableStateOf(false) }
    var showWirelessPairingDialog by remember { mutableStateOf(false) }

    val activeLinkRole by viewModel.wirelessLink.activeRole.collectAsState()
    val isLinkConnected by viewModel.wirelessLink.isConnected.collectAsState()

    BoxWithConstraints(modifier = modifier) {
        val isCompactHeight = maxHeight < 500.dp && !DesktopAdaptiveState.isDesktopPlatform
        val isSplitWorkspace = DesktopAdaptiveState.isDesktopPlatform || (maxWidth >= 700.dp && !isCompactHeight)
        val isMobileLandscape = !isSplitWorkspace && maxWidth >= 600.dp
        val canDockRightInspector = maxWidth >= 1120.dp
        val isUltraWide = maxWidth >= 1440.dp

        // Automatically suspend bare-key recording shortcuts when any non-recording screen or modal is active
        val shouldSuspendBareShortcuts = showWordListEditor ||
            showWirelessPairingDialog ||
            activeLinkRole != LinkRole.STANDALONE ||
            showUserManualOverlay ||
            showEncyclopediaOverlay ||
            showRoomNoiseCheck ||
            showSettingsOverlay ||
            showGlobalManagement ||
            showPhoneticWord != null ||
            !hasSelectedUiMode ||
            showRecordingTips ||
            !hasSeenRecordingTips ||
            showAboutDialog ||
            currentProject == null ||
            (isSplitWorkspace && (tabletLeftPane == AppScreen.ArchiveExplorer || tabletLeftPane == AppScreen.Settings)) ||
            (!isSplitWorkspace && currentScreen != AppScreen.RecordingWorkspace)

        SuspendDesktopShortcutsWhen(shouldSuspendBareShortcuts)

        val mainTabBackState = rememberPredictiveBackVisualState()
        val tabletSidePaneBackState = rememberPredictiveBackVisualState()

        if (isSplitWorkspace) {
            PredictiveBackGestureHandler(
                enabled = !showWordListEditor && !showUserManualOverlay && !showEncyclopediaOverlay && !showRoomNoiseCheck &&
                    (showGlobalManagement || showPhoneticWord != null || showQuickRecordingSettingsInSidebar || tabletLeftPane != AppScreen.ProjectList),
                state = if (showGlobalManagement || showPhoneticWord != null) tabletSidePaneBackState else mainTabBackState
            ) {
                when {
                    showPhoneticWord != null -> viewModel.showPhoneticWordDetail(null)
                    showGlobalManagement -> viewModel.setShowGlobalManagement(false)
                    showQuickRecordingSettingsInSidebar -> showQuickRecordingSettingsInSidebar = false
                    tabletLeftPane != AppScreen.ProjectList -> tabletLeftPane = AppScreen.ProjectList
                }
            }
        } else {
            PredictiveBackGestureHandler(
                enabled = !showWordListEditor && !showUserManualOverlay && !showEncyclopediaOverlay && !showSettingsOverlay && !showGlobalManagement && !showRoomNoiseCheck &&
                    (drawerState.isOpen || currentScreen != AppScreen.ProjectList),
                state = mainTabBackState
            ) {
                when {
                    drawerState.isOpen -> scope.launch { drawerState.close() }
                    currentScreen == AppScreen.ArchiveExplorer -> currentScreen = AppScreen.RecordingWorkspace
                    else -> currentScreen = AppScreen.ProjectList
                }
            }
        }

        if (isSplitWorkspace) {
            val isFullWindowImmersive = participantCleanMode &&
                currentProject != null &&
                !showWordListEditor &&
                !showUserManualOverlay &&
                !showEncyclopediaOverlay &&
                !showRoomNoiseCheck &&
                tabletLeftPane == AppScreen.ProjectList

            AnimatedContent(
                targetState = isFullWindowImmersive,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.975f, animationSpec = tween(220))) togetherWith
                        fadeOut(tween(160))
                },
                label = "FullWindowImmersiveTransition",
                modifier = Modifier.fillMaxSize()
            ) { fullImmersive ->
                if (fullImmersive) {
                    // Full-window immersive mode ("小眼睛"大字看读模式): covers 100% of the window without sidebars or top bars
                    RecordingScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            viewModel.setParticipantCleanMode(false)
                        },
                        onViewArchive = {
                            viewModel.setParticipantCleanMode(false)
                            tabletLeftPane = AppScreen.ArchiveExplorer
                        },
                        onOpenFullSettings = {
                            viewModel.setParticipantCleanMode(false)
                            showQuickRecordingSettingsInSidebar = false
                            tabletLeftPane = AppScreen.Settings
                        },
                        onToggleQuickSettingsSidebar = {
                            viewModel.setParticipantCleanMode(false)
                            DesktopAdaptiveState.isProjectSidebarCollapsed = false
                            showQuickRecordingSettingsInSidebar = true
                        },
                        isSplitPaneMode = true,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val isPrimaryOverlayOpen = showWordListEditor || showUserManualOverlay || showEncyclopediaOverlay || showRoomNoiseCheck
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.testTag("app_nav_rail")
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
                                    modifier = Modifier.padding(vertical = 8.dp).size(38.dp)
                                )
                                NavigationRailItem(
                                    selected = !isPrimaryOverlayOpen && !showQuickRecordingSettingsInSidebar && tabletLeftPane == AppScreen.ProjectList,
                                    onClick = {
                                        showWordListEditor = false
                                        showUserManualOverlay = false
                                        showEncyclopediaOverlay = false
                                        showRoomNoiseCheck = false
                                        showQuickRecordingSettingsInSidebar = false
                                        DesktopAdaptiveState.isProjectSidebarCollapsed = false
                                        tabletLeftPane = AppScreen.ProjectList
                                    },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = S.nav.tabProjects) },
                                    label = { Text(S.nav.tabProjects, fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_project_list")
                                )
                                NavigationRailItem(
                                    selected = !isPrimaryOverlayOpen && tabletLeftPane == AppScreen.ArchiveExplorer,
                                    onClick = {
                                        showWordListEditor = false
                                        showUserManualOverlay = false
                                        showEncyclopediaOverlay = false
                                        showRoomNoiseCheck = false
                                        showQuickRecordingSettingsInSidebar = false
                                        tabletLeftPane = AppScreen.ArchiveExplorer
                                    },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = S.nav.tabArchive) },
                                    label = { Text(S.nav.tabArchive, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_archive_explorer")
                                )
                                NavigationRailItem(
                                    selected = showWordListEditor,
                                    onClick = {
                                        showUserManualOverlay = false
                                        showEncyclopediaOverlay = false
                                        showRoomNoiseCheck = false
                                        wordEditorInitialProject = currentProject
                                        showWordListEditor = true
                                    },
                                    icon = { Icon(Icons.Default.EditNote, contentDescription = S.nav.wordListEditor) },
                                    label = { Text(S.nav.editorShort, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_word_editor")
                                )
                                NavigationRailItem(
                                    selected = !isPrimaryOverlayOpen && tabletLeftPane == AppScreen.Settings,
                                    onClick = {
                                        showWordListEditor = false
                                        showUserManualOverlay = false
                                        showEncyclopediaOverlay = false
                                        showRoomNoiseCheck = false
                                        showQuickRecordingSettingsInSidebar = false
                                        tabletLeftPane = AppScreen.Settings
                                    },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = S.nav.settings) },
                                    label = { Text(S.nav.settings, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_settings")
                                )
                                NavigationRailItem(
                                    selected = showWirelessPairingDialog || activeLinkRole != LinkRole.STANDALONE,
                                    onClick = { showWirelessPairingDialog = true },
                                    icon = { Icon(Icons.Default.Devices, contentDescription = S.nav.wirelessLink) },
                                    label = { Text(S.nav.wirelessShort, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_wireless_link")
                                )

                                HorizontalDivider(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .padding(vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                )

                                NavigationRailItem(
                                    selected = showUserManualOverlay,
                                    onClick = {
                                        showWordListEditor = false
                                        showEncyclopediaOverlay = false
                                        showRoomNoiseCheck = false
                                        showUserManualOverlay = !showUserManualOverlay
                                    },
                                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = S.nav.userManual) },
                                    label = { Text(S.nav.manualShort, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_item_user_manual")
                                )
                                NavigationRailItem(
                                    selected = showEncyclopediaOverlay,
                                    onClick = {
                                        showWordListEditor = false
                                        showUserManualOverlay = false
                                        showRoomNoiseCheck = false
                                        showEncyclopediaOverlay = !showEncyclopediaOverlay
                                    },
                                    icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = S.nav.encyclopedia) },
                                    label = { Text(S.nav.encyclopediaShort, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_item_encyclopedia")
                                )
                                NavigationRailItem(
                                    selected = showRoomNoiseCheck,
                                    onClick = {
                                        showWordListEditor = false
                                        showUserManualOverlay = false
                                        showEncyclopediaOverlay = false
                                        showRoomNoiseCheck = !showRoomNoiseCheck
                                    },
                                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = S.nav.soundCheck) },
                                    label = { Text(S.nav.soundCheckShort, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_sound_check")
                                )
                                NavigationRailItem(
                                    selected = showRecordingTips,
                                    onClick = { showRecordingTips = true },
                                    icon = { Icon(Icons.Default.Lightbulb, contentDescription = S.nav.recordingTips) },
                                    label = { Text(S.nav.tipsShort, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_tips")
                                )
                                val themeIcon = when (themeMode) {
                                    "LIGHT" -> Icons.Default.LightMode
                                    "DARK" -> Icons.Default.DarkMode
                                    else -> Icons.Default.BrightnessAuto
                                }
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
                                    icon = { Icon(themeIcon, contentDescription = S.nav.switchThemeDesc.withArgs("label" to themeLabel)) },
                                    label = { Text(themeLabel, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_theme_toggle")
                                )
                                NavigationRailItem(
                                    selected = showAboutDialog,
                                    onClick = { showAboutDialog = true },
                                    icon = { Icon(Icons.Default.Info, contentDescription = S.nav.aboutPhonAcq) },
                                    label = { Text(S.nav.aboutShort, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("rail_about")
                                )
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Right-hand Main Column (Desktop Studio Top Bar + Workspace Content)
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            DesktopAdaptiveState.desktopTopBarSlot?.invoke()

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                val hasDockedSideOverlay = (showGlobalManagement && canDockRightInspector) ||
                                    (showPhoneticWord != null && canDockRightInspector)
                                val showFullGlobalOverlay = showGlobalManagement && !canDockRightInspector

                                Row(modifier = Modifier.fillMaxSize()) {
                                    val rightWorkspaceKey = when {
                                        showFullGlobalOverlay -> "GLOBAL_MANAGEMENT"
                                        showWordListEditor -> "WORD_EDITOR"
                                        showUserManualOverlay -> "USER_MANUAL"
                                        showEncyclopediaOverlay -> "ENCYCLOPEDIA"
                                        showRoomNoiseCheck -> "SOUND_CHECK"
                                        tabletLeftPane == AppScreen.ArchiveExplorer -> "ARCHIVE_EXPLORER"
                                        tabletLeftPane == AppScreen.Settings -> "SETTINGS"
                                        else -> "SPLIT_WORKSPACE"
                                    }

                                    AnimatedContent(
                                        targetState = rightWorkspaceKey,
                                        transitionSpec = {
                                            (fadeIn(tween(220)) +
                                                androidx.compose.animation.slideInVertically(tween(220)) { it / 28 } +
                                                scaleIn(initialScale = 0.985f, animationSpec = tween(220))) togetherWith
                                                (fadeOut(tween(150)) + scaleOut(targetScale = 0.99f, animationSpec = tween(150)))
                                        },
                                        label = "RightWorkspaceAnimatedTransition",
                                        modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()
                                    ) { paneKey ->
                                        when (paneKey) {
                                            "GLOBAL_MANAGEMENT" -> {
                                                GlobalManagementPane(
                                                    viewModel = viewModel,
                                                    onDismiss = { viewModel.setShowGlobalManagement(false) }
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
                                                            tabletLeftPane = AppScreen.ProjectList
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
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                "ENCYCLOPEDIA" -> {
                                                    FieldworkEncyclopediaScreen(
                                                        onBack = { showEncyclopediaOverlay = false },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                "SOUND_CHECK" -> {
                                                    SoundCheckScreen(
                                                        viewModel = viewModel,
                                                        onBack = { showRoomNoiseCheck = false },
                                                        onGoToRecordingWorkspace = {
                                                            showRoomNoiseCheck = false
                                                            tabletLeftPane = AppScreen.ProjectList
                                                        },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                "SETTINGS" -> {
                                                    SettingsScreen(
                                                        viewModel = viewModel,
                                                        onBack = {
                                                            tabletLeftPane = AppScreen.ProjectList
                                                        },
                                                        onOpenRecordingsCleanup = {
                                                            viewModel.setShowGlobalManagement(true)
                                                        },
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
                                                            buttonText = S.nav.backToProjectAndStudio,
                                                            onNavigateToProjects = { tabletLeftPane = AppScreen.ProjectList }
                                                        )
                                                    } else {
                                                        ArchiveExplorerScreen(
                                                            viewModel = viewModel,
                                                            onNavigateBack = {
                                                                tabletLeftPane = AppScreen.ProjectList
                                                            },
                                                            onSelectedWordPosition = { seqPos ->
                                                                viewModel.selectWordBySequencePosition(seqPos)
                                                                tabletLeftPane = AppScreen.ProjectList
                                                            },
                                                            isTablet = true,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                                else -> {
                                                    // Main Project / Quick-Settings Left Sidebar + Recording Right Workspace
                                                    // Auto-collapse left sidebar when right inspector is docked on <1440dp so RecordingScreen stays spacious
                                                    val showLeftSidebar = !DesktopAdaptiveState.isProjectSidebarCollapsed &&
                                                        !(hasDockedSideOverlay && !isUltraWide)

                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        AnimatedVisibility(
                                                            visible = showLeftSidebar,
                                                            enter = expandHorizontally(animationSpec = tween(220)) + fadeIn(tween(180)),
                                                            exit = shrinkHorizontally(animationSpec = tween(200)) + fadeOut(tween(150))
                                                        ) {
                                                            Row(modifier = Modifier.fillMaxHeight()) {
                                                                val leftSidebarMode = if (showQuickRecordingSettingsInSidebar) 1 else 0

                                                                AnimatedContent(
                                                                    targetState = leftSidebarMode,
                                                                    transitionSpec = {
                                                                        if (targetState > initialState) {
                                                                            (slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(180))) togetherWith
                                                                                (slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut(tween(140)))
                                                                        } else {
                                                                            (slideInHorizontally(tween(220)) { -it / 6 } + fadeIn(tween(180))) togetherWith
                                                                                (slideOutHorizontally(tween(180)) { it / 5 } + fadeOut(tween(140)))
                                                                        }
                                                                    },
                                                                    label = "LeftSidebarAnimatedTransition",
                                                                    modifier = Modifier
                                                                        .width(if (DesktopAdaptiveState.isDesktopPlatform) 356.dp else 340.dp)
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
                                                                                    tabletLeftPane = AppScreen.Settings
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
                                                                                    tabletLeftPane = AppScreen.Settings
                                                                                },
                                                                                onOpenWirelessLink = {
                                                                                    showWirelessPairingDialog = true
                                                                                },
                                                                                onOpenWordListEditor = { proj ->
                                                                                    wordEditorInitialProject = proj
                                                                                    showWordListEditor = true
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
                                                                    (fadeIn(tween(210)) +
                                                                        androidx.compose.animation.slideInVertically(tween(210)) { it / 32 }) togetherWith
                                                                        fadeOut(tween(150))
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
                                                                            tabletLeftPane = AppScreen.ProjectList
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
                                                                            tabletLeftPane = AppScreen.ArchiveExplorer
                                                                        },
                                                                        onOpenFullSettings = {
                                                                            showQuickRecordingSettingsInSidebar = false
                                                                            tabletLeftPane = AppScreen.Settings
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

                                    AnimatedVisibility(
                                        visible = hasDockedSideOverlay,
                                        enter = slideInHorizontally(animationSpec = tween(250)) { it } + fadeIn(tween(200)),
                                        exit = slideOutHorizontally(animationSpec = tween(220)) { it } + fadeOut(tween(180))
                                    ) {
                                        Row(modifier = Modifier.fillMaxHeight()) {
                                            if (hasDockedSideOverlay) {
                                                VerticalDivider(
                                                    modifier = Modifier.fillMaxHeight().width(1.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .then(if (hasDockedSideOverlay) Modifier.width(390.dp) else Modifier.fillMaxWidth())
                                                    .fillMaxHeight()
                                                    .clipToBounds()
                                                    .predictiveBackLayer(tabletSidePaneBackState)
                                                    .background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                AnimatedContent(
                                                    targetState = Pair(showGlobalManagement, showPhoneticWord),
                                                    contentKey = { Pair(it.first, it.second?.id) },
                                                    transitionSpec = {
                                                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                                                    },
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

                                // On compact desktop/tablet (<1120dp), use full-featured PhoneticDetailsBottomSheet modal
                                if (showPhoneticWord != null && !canDockRightInspector) {
                                    PhoneticDetailsBottomSheet(
                                        word = showPhoneticWord!!,
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
        } else {
            // Mobile Portrait (Bottom Bar) & Mobile Landscape (Scrollable Left NavigationRail for full vertical height)
            val gesturesEnabled = (currentScreen == AppScreen.ProjectList || drawerState.isOpen) &&
                !showSettingsOverlay && !showUserManualOverlay && !showEncyclopediaOverlay && !showWordListEditor && !showGlobalManagement && !showRoomNoiseCheck

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = gesturesEnabled,
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
                            currentScreen = AppScreen.ProjectList
                            openSpeakerManagerTrigger++
                        },
                        onOpenRecordingStats = {
                            scope.launch { drawerState.close() }
                            viewModel.setShowGlobalManagement(true)
                        },
                        onOpenWordListEditor = {
                            scope.launch { drawerState.close() }
                            wordEditorInitialProject = currentProject
                            showWordListEditor = true
                        },
                        onOpenUserManual = {
                            scope.launch { drawerState.close() }
                            showUserManualOverlay = true
                        },
                        onOpenEncyclopedia = {
                            scope.launch { drawerState.close() }
                            showEncyclopediaOverlay = true
                        },
                        onOpenRoomNoiseCheck = {
                            scope.launch { drawerState.close() }
                            showRoomNoiseCheck = true
                        },
                        onOpenRecordingTips = {
                            scope.launch { drawerState.close() }
                            showRecordingTips = true
                        },
                        onOpenSettings = {
                            scope.launch { drawerState.close() }
                            showSettingsOverlay = true
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
                Scaffold(
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = !isMobileLandscape && !(currentScreen == AppScreen.RecordingWorkspace && participantCleanMode),
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp,
                                modifier = Modifier.testTag("app_bottom_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.ProjectList,
                                    onClick = { currentScreen = AppScreen.ProjectList },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = S.nav.tabProjects) },
                                    label = {
                                        Text(
                                            text = S.nav.tabProjects,
                                            fontWeight = if (currentScreen == AppScreen.ProjectList) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("tab_project_list")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.RecordingWorkspace,
                                    onClick = { currentScreen = AppScreen.RecordingWorkspace },
                                    icon = { Icon(Icons.Default.Mic, contentDescription = S.nav.tabRecordingStudio) },
                                    label = {
                                        Text(
                                            text = S.nav.tabRecordingStudio,
                                            fontWeight = if (currentScreen == AppScreen.RecordingWorkspace) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("tab_recording_workspace")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.ArchiveExplorer,
                                    onClick = { currentScreen = AppScreen.ArchiveExplorer },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = S.nav.tabArchive) },
                                    label = {
                                        Text(
                                            text = S.nav.tabArchive,
                                            fontWeight = if (currentScreen == AppScreen.ArchiveExplorer) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("tab_archive_explorer")
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedVisibility(
                            visible = isMobileLandscape && !(currentScreen == AppScreen.RecordingWorkspace && participantCleanMode),
                            enter = expandHorizontally(tween(200)) + fadeIn(tween(160)),
                            exit = shrinkHorizontally(tween(180)) + fadeOut(tween(140))
                        ) {
                            Row(modifier = Modifier.fillMaxHeight()) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .width(72.dp)
                                        .testTag("app_nav_rail")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .verticalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        PhonAcqLogo(
                                            modifier = Modifier.padding(vertical = 4.dp).size(28.dp)
                                        )
                                        NavigationRailItem(
                                            selected = currentScreen == AppScreen.ProjectList,
                                            onClick = { currentScreen = AppScreen.ProjectList },
                                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = S.nav.tabProjects) },
                                            label = { Text(S.nav.tabProjects, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("tab_project_list")
                                        )
                                        NavigationRailItem(
                                            selected = currentScreen == AppScreen.RecordingWorkspace,
                                            onClick = { currentScreen = AppScreen.RecordingWorkspace },
                                            icon = { Icon(Icons.Default.Mic, contentDescription = S.nav.tabRecordingStudio) },
                                            label = { Text(S.nav.tabRecordingStudio, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("tab_recording_workspace")
                                        )
                                        NavigationRailItem(
                                            selected = currentScreen == AppScreen.ArchiveExplorer,
                                            onClick = { currentScreen = AppScreen.ArchiveExplorer },
                                            icon = { Icon(Icons.Default.Folder, contentDescription = S.nav.tabArchive) },
                                            label = { Text(S.nav.tabArchive, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("tab_archive_explorer")
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.width(32.dp).padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                        )
                                        NavigationRailItem(
                                            selected = false,
                                            onClick = {
                                                currentScreen = AppScreen.ProjectList
                                                openSpeakerManagerTrigger++
                                            },
                                            icon = { Icon(Icons.Default.Groups, contentDescription = S.nav.speakerManager) },
                                            label = { Text(S.nav.speakerShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_speakers")
                                        )
                                        if (!isSimpleMode) {
                                            NavigationRailItem(
                                                selected = showGlobalManagement,
                                                onClick = { viewModel.setShowGlobalManagement(true) },
                                                icon = { Icon(Icons.Default.PieChart, contentDescription = S.nav.recordingStatsAndCleanup) },
                                                label = { Text(S.nav.statsShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                                modifier = Modifier.testTag("rail_stats")
                                            )
                                        }
                                        NavigationRailItem(
                                            selected = showWordListEditor,
                                            onClick = {
                                                wordEditorInitialProject = currentProject
                                                showWordListEditor = true
                                            },
                                            icon = { Icon(Icons.Default.EditNote, contentDescription = S.nav.wordListEditor) },
                                            label = { Text(S.nav.editorShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_word_editor")
                                        )
                                        NavigationRailItem(
                                            selected = showSettingsOverlay,
                                            onClick = { showSettingsOverlay = true },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = S.nav.settings) },
                                            label = { Text(S.nav.settings, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_settings")
                                        )
                                        NavigationRailItem(
                                            selected = showWirelessPairingDialog || activeLinkRole != LinkRole.STANDALONE,
                                            onClick = { showWirelessPairingDialog = true },
                                            icon = { Icon(Icons.Default.Devices, contentDescription = S.nav.wirelessLink) },
                                            label = { Text(S.nav.wirelessShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_wireless_link")
                                        )
                                        NavigationRailItem(
                                            selected = showRoomNoiseCheck,
                                            onClick = { showRoomNoiseCheck = true },
                                            icon = { Icon(Icons.Default.GraphicEq, contentDescription = S.nav.soundCheck) },
                                            label = { Text(S.nav.soundCheckShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_sound_check")
                                        )
                                        NavigationRailItem(
                                            selected = showUserManualOverlay,
                                            onClick = { showUserManualOverlay = true },
                                            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = S.nav.userManual) },
                                            label = { Text(S.nav.manualShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_item_user_manual")
                                        )
                                        NavigationRailItem(
                                            selected = showEncyclopediaOverlay,
                                            onClick = { showEncyclopediaOverlay = true },
                                            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = S.nav.encyclopedia) },
                                            label = { Text(S.nav.encyclopediaShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_item_encyclopedia")
                                        )
                                        NavigationRailItem(
                                            selected = showRecordingTips,
                                            onClick = { showRecordingTips = true },
                                            icon = { Icon(Icons.Default.Lightbulb, contentDescription = S.nav.recordingTips) },
                                            label = { Text(S.nav.tipsShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_tips")
                                        )
                                        val landscapeThemeIcon = when (themeMode) {
                                            "LIGHT" -> Icons.Default.LightMode
                                            "DARK" -> Icons.Default.DarkMode
                                            else -> Icons.Default.BrightnessAuto
                                        }
                                        val landscapeThemeLabel = when (themeMode) {
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
                                            icon = { Icon(landscapeThemeIcon, contentDescription = S.nav.switchThemeDesc.withArgs("label" to landscapeThemeLabel)) },
                                            label = { Text(landscapeThemeLabel, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_theme_toggle")
                                        )
                                        NavigationRailItem(
                                            selected = showAboutDialog,
                                            onClick = { showAboutDialog = true },
                                            icon = { Icon(Icons.Default.Info, contentDescription = S.nav.aboutPhonAcq) },
                                            label = { Text(S.nav.aboutShort, fontSize = 10.sp, maxLines = 1, softWrap = false) },
                                            modifier = Modifier.testTag("rail_about")
                                        )
                                    }
                                }
                                VerticalDivider(
                                    modifier = Modifier.fillMaxHeight().width(1.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        BoxTransition(
                            targetState = currentScreen,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .predictiveBackLayer(mainTabBackState)
                        ) { screen ->
                            when (screen) {
                                AppScreen.ProjectList -> {
                                    ProjectListScreen(
                                        viewModel = viewModel,
                                        onProjectOpened = {
                                            currentScreen = AppScreen.RecordingWorkspace
                                        },
                                        onOpenDrawer = {
                                            scope.launch { drawerState.open() }
                                        },
                                        onOpenSettings = {
                                            showSettingsOverlay = true
                                        },
                                        onOpenWirelessLink = {
                                            showWirelessPairingDialog = true
                                        },
                                        onOpenWordListEditor = { proj ->
                                            wordEditorInitialProject = proj
                                            showWordListEditor = true
                                        },
                                        openSpeakerManagerTrigger = openSpeakerManagerTrigger,
                                        onSpeakerManagerTriggerConsumed = { openSpeakerManagerTrigger = 0 },
                                        isSplitPaneMode = isMobileLandscape,
                                        modifier = Modifier.fillMaxSize()
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
                                            onNavigateBack = {
                                                currentScreen = AppScreen.ProjectList
                                            },
                                            onViewArchive = {
                                                currentScreen = AppScreen.ArchiveExplorer
                                            },
                                            onOpenFullSettings = {
                                                showSettingsOverlay = true
                                            },
                                            onOpenWirelessLink = {
                                                showWirelessPairingDialog = true
                                            },
                                            isSplitPaneMode = isMobileLandscape,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                else -> {
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
                                            onNavigateBack = {
                                                currentScreen = AppScreen.RecordingWorkspace
                                            },
                                            onSelectedWordPosition = { seqPos ->
                                                viewModel.selectWordBySequencePosition(seqPos)
                                                currentScreen = AppScreen.RecordingWorkspace
                                            },
                                            isTablet = isMobileLandscape,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showPhoneticWord != null) {
                        PhoneticDetailsBottomSheet(
                            word = showPhoneticWord!!,
                            speaker = currentSpeaker,
                            viewModel = viewModel,
                            onDismiss = { viewModel.showPhoneticWordDetail(null) }
                        )
                    }
                }
            }

            // Full-Screen Settings Overlay (Zero bottomBar layout shift / zero inset jitter)
            AnimatedVisibility(
                visible = showSettingsOverlay,
                enter = slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(180)),
                exit = slideOutHorizontally(animationSpec = tween(180)) { it / 4 } + fadeOut(tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { showSettingsOverlay = false },
                    onOpenRecordingsCleanup = {
                        viewModel.setShowGlobalManagement(true)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Full-Screen Word List Editor Overlay on Mobile (SplitWorkspace renders inside right workspace)
        AnimatedVisibility(
            visible = !isSplitWorkspace && showWordListEditor,
            enter = slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(animationSpec = tween(180)) { it / 4 } + fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            WordListEditorScreen(
                viewModel = viewModel,
                initialProject = wordEditorInitialProject,
                onBack = {
                    showWordListEditor = false
                },
                onSavedAndOpenRecording = { savedProj ->
                    viewModel.selectProject(savedProj)
                    showWordListEditor = false
                    currentScreen = AppScreen.RecordingWorkspace
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Full-Screen User Manual Overlay on Mobile (SplitWorkspace renders inside right workspace)
        AnimatedVisibility(
            visible = !isSplitWorkspace && showUserManualOverlay,
            enter = slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(animationSpec = tween(180)) { it / 4 } + fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            UserManualScreen(
                onBack = { showUserManualOverlay = false },
                isSimpleMode = isSimpleMode,
                onOpenEncyclopedia = {
                    showUserManualOverlay = false
                    showEncyclopediaOverlay = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Full-Screen Fieldwork Encyclopedia Overlay on Mobile (SplitWorkspace renders inside right workspace)
        AnimatedVisibility(
            visible = !isSplitWorkspace && showEncyclopediaOverlay,
            enter = slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(animationSpec = tween(180)) { it / 4 } + fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            FieldworkEncyclopediaScreen(
                onBack = { showEncyclopediaOverlay = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = !isSplitWorkspace && showGlobalManagement,
            enter = slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(animationSpec = tween(180)) { it / 4 } + fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            GlobalManagementDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.setShowGlobalManagement(false) },
                onOpenProjectInWorkspace = {
                    showSettingsOverlay = false
                    currentScreen = AppScreen.RecordingWorkspace
                }
            )
        }

        // Full-Screen Sound Check Overlay on Mobile (SplitWorkspace renders inside right workspace)
        AnimatedVisibility(
            visible = !isSplitWorkspace && showRoomNoiseCheck,
            enter = slideInHorizontally(animationSpec = tween(220)) { it / 4 } + fadeIn(tween(180)),
            exit = slideOutHorizontally(animationSpec = tween(180)) { it / 4 } + fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            SoundCheckScreen(
                viewModel = viewModel,
                onBack = { showRoomNoiseCheck = false },
                onGoToRecordingWorkspace = {
                    showRoomNoiseCheck = false
                    currentScreen = AppScreen.RecordingWorkspace
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Full-Screen Multi-Device Wireless Collaboration Workspaces (看读端 / 遥控端)
        AnimatedVisibility(
            visible = activeLinkRole != LinkRole.STANDALONE && (isLinkConnected || !showWirelessPairingDialog),
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.98f, animationSpec = tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = activeLinkRole,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
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

        if (showWirelessPairingDialog) {
            WirelessLinkPairingDialog(
                viewModel = viewModel,
                onDismiss = { showWirelessPairingDialog = false },
                onOpenLinkSettings = {
                    if (isSplitWorkspace) {
                        tabletLeftPane = AppScreen.Settings
                    } else {
                        showSettingsOverlay = true
                    }
                }
            )
        }

        if (!hasSelectedUiMode) {
            ModeSelectionOnboardingDialog(
                onSelectMode = { simple ->
                    viewModel.completeInitialModeSelection(simple)
                }
            )
        } else if (showRecordingTips || !hasSeenRecordingTips) {
            RecordingTipsDialog(
                isFirstLaunch = !hasSeenRecordingTips,
                isSimpleMode = isSimpleMode,
                onDismiss = {
                    showRecordingTips = false
                    viewModel.markRecordingTipsSeen()
                }
            )
        }

        if (showAboutDialog) {
            AboutPhonAcqDialog(
                onDismiss = { showAboutDialog = false }
            )
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

