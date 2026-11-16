package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.i18n.S
import com.example.i18n.withArgs
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
    var isImportingZip by remember { mutableStateOf(false) }

    SuspendDesktopShortcutsWhen(showSpeakerManagerScreen || selectedProjectForDetails != null)

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImportingZip = true
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.importProjectZip(inputStream) { summary ->
                    isImportingZip = false
                    Toast.makeText(context, summary.message, Toast.LENGTH_LONG).show()
                }
            } else {
                isImportingZip = false
                Toast.makeText(context, S.project.cannotReadArchive, Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    Box(modifier = modifier.fillMaxSize()) {
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
                        if (!isSimpleMode) {
                            TextButton(
                                onClick = {
                                    if (!isImportingZip) {
                                        importZipLauncher.launch(
                                            arrayOf(
                                                "application/zip",
                                                "application/x-zip-compressed",
                                                "application/octet-stream",
                                                "*/*"
                                            )
                                        )
                                    }
                                },
                                enabled = !isImportingZip,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("import_zip_archive_button")
                            ) {
                                if (isImportingZip) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = S.project.importingProject,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.FolderZip,
                                        contentDescription = S.project.importProject,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = S.project.importProject,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
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

                    if (projects.isEmpty() && speakers.isEmpty()) {
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
                            if (!isSimpleMode) {
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = {
                                        if (!isImportingZip) {
                                            importZipLauncher.launch(
                                                arrayOf(
                                                    "application/zip",
                                                    "application/x-zip-compressed",
                                                    "application/octet-stream",
                                                    "*/*"
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("empty_state_import_zip_button")
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(S.project.importProject, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    } else {
                        val totalAllRecordings = summariesMap.values.sumOf { it.totalRecordingsCount }
                        val distinctGroupsCount = speakerGroups.size

                        LazyColumn(
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
                            item {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val isCompactSidebar = maxWidth < 285.dp
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Left Card: Speaker Roster & Groups
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                            modifier = Modifier
                                                .weight(1.14f)
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable {
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
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Groups,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onSecondary,
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
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        if (!isSimpleMode) {
                                            // Right Card: Global Recording Overview & Cleanup
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                modifier = Modifier
                                                    .weight(0.86f)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .clickable {
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
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = S.project.overviewStatsAndCleanup,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (!isCompactSidebar) {
                                                        Icon(
                                                            imageVector = Icons.Default.Analytics,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
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
                            item {
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
                                    }
                                )
                            }
                        }
                    }
                }

                if (selectedProjectForDetails != null) {
                    val targetProj = selectedProjectForDetails!!
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
                        onUpdate = { name, desc, groupName ->
                            viewModel.updateProjectDetails(targetProj, name, desc, groupName) {
                                selectedProjectForDetails = null
                            }
                        }
                    )
                }
            }

        AnimatedVisibility(
            visible = showSpeakerManagerScreen,
            enter = slideInHorizontally { width -> width / 3 } + fadeIn(),
            exit = slideOutHorizontally { width -> width / 3 } + fadeOut(),
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

        homeCapsulePendingDelete?.let { targetCap ->
            AlertDialog(
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
    val cardBgColor = if (isCardHovered) {
        lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.primaryContainer, 0.28f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBorderColor = if (isCardHovered) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val animatedProgress by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "projectCardProgress_${project.id}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hoverable(cardHoverSrc)
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(
                interactionSource = cardHoverSrc,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("project_card_${project.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(if (isCardHovered) 1.5.dp else 1.dp, cardBorderColor),
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
                    IconButton(
                        onClick = onEditDetails,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = S.project.cardEditWordsDesc,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_project_${project.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = S.project.cardDeleteProjectDesc,
                            tint = MaterialTheme.colorScheme.outline,
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
                        val isDone = spkInfo.isCompleted
                        val isStarted = spkInfo.hasStarted
                        val chipBg = when {
                            isDone -> MaterialTheme.colorScheme.secondaryContainer
                            isStarted -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val chipContent = when {
                            isDone -> MaterialTheme.colorScheme.onSecondaryContainer
                            isStarted -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = chipBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSpeakerChipClick(spkInfo.speaker) }
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
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onManageGroupSpeakers() }
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

    if (showDeleteConfirm) {
        AlertDialog(
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
            if (!showCreateSpeakerForm && speakerToEdit == null) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            AnimatedContent(
                targetState = isFormOpen,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
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

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(filteredSpeakers, key = { it.id }) { speaker ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(46.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = speaker.name.take(1),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = speaker.name,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        val ageDisplay = S.common.ageWithUnit.withArgs("age" to speaker.age)
                                                        val badgeLabel = if (speaker.gender.isBlank() || speaker.gender == "不愿透露") {
                                                            ageDisplay
                                                        } else {
                                                            "${S.speaker.displayGender(speaker.gender)} · $ageDisplay"
                                                        }
                                                        Text(
                                                            text = badgeLabel,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (!isSimpleMode && speaker.groupList.isNotEmpty()) {
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        speaker.groupList.forEach { grp ->
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                                            ) {
                                                                Text(
                                                                    text = grp,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    modifier = Modifier
                                                                        .widthIn(max = 180.dp)
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = S.speaker.speakerDialectPrefix.withArgs("dialect" to speaker.dialect),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (!isSimpleMode) {
                                                    var pitchRange by remember(speaker.id) { mutableStateOf(viewModel.getSpeakerPitchRange(speaker.id)) }
                                                    var isEstimating by remember(speaker.id) { mutableStateOf(false) }
                                                    var showManualPitchDialog by remember(speaker.id) { mutableStateOf(false) }
                                                    val context = LocalContext.current

                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = S.speaker.personalPitchRangePrefix.withArgs(
                                                                "range" to (pitchRange?.let { "${it.first.toInt()}–${it.second.toInt()} Hz" } ?: S.speaker.pitchPendingEstimate)
                                                            ),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            color = if (pitchRange != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                            modifier = Modifier.align(Alignment.CenterVertically)
                                                        )
                                                        TextButton(
                                                            onClick = {
                                                                isEstimating = true
                                                                viewModel.estimatePitchRangeForSpeaker(speaker) { res ->
                                                                    isEstimating = false
                                                                    pitchRange = res
                                                                    if (res == null) {
                                                                        Toast.makeText(context, S.speaker.tooFewSamplesForEstimateToast, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            modifier = Modifier
                                                                .align(Alignment.CenterVertically)
                                                                .defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isEstimating) {
                                                                    S.speaker.estimatingPitch
                                                                } else if (pitchRange == null) {
                                                                    S.speaker.autoEstimatePitch
                                                                } else {
                                                                    S.speaker.reEstimatePitch
                                                                },
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                        TextButton(
                                                            onClick = { showManualPitchDialog = true },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                            modifier = Modifier
                                                                .align(Alignment.CenterVertically)
                                                                .defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                                                        ) {
                                                            Text(
                                                                text = S.recording.manualPitchButton,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    }

                                                    if (showManualPitchDialog) {
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

                                                if (speaker.notes.isNotEmpty()) {
                                                    Text(
                                                        text = S.speaker.notesPrefix.withArgs("notes" to speaker.notes),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    nameField = speaker.name
                                                    genderField = if (speaker.gender in presetGenders) speaker.gender else "多元/不限"
                                                    ageFieldText = speaker.age.toString()
                                                    dialectField = speaker.dialect
                                                    groupField = speaker.groupName
                                                    notesField = speaker.notes
                                                    speakerToEdit = speaker
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = S.speaker.editInfoDesc,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            if (speakers.size > 1) {
                                                var showDeleteConfirm by remember { mutableStateOf(false) }

                                                IconButton(onClick = { showDeleteConfirm = true }) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = S.speaker.deleteSpeakerDesc,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                if (showDeleteConfirm) {
                                                    AlertDialog(
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
                                }
                            }
                        }
                    }
                }
            }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailEditDialog(
    viewModel: RecordingViewModel,
    project: Project,
    speakerGroups: List<String>,
    summary: ProjectMultiSpeakerSummary?,
    onDismiss: () -> Unit,
    onOpenInWordEditor: (() -> Unit)? = null,
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

    Dialog(onDismissRequest = onDismiss) {
        com.example.ui.components.StudioDialogEnterAnimation {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        itemsIndexed(wordsList, key = { _, w -> w.id }) { index, word ->
                                            Row(
                                                modifier = Modifier
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
                                }
                            }

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
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { isEditingMode = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSimpleMode) S.project.modifyProjectInfo else S.project.modifyProjectAndGroup, maxLines = 1, softWrap = false)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                            Text(S.common.close, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
    }

    if (wordBeingEdited != null) {
        val target = wordBeingEdited!!
        var editWText by remember(target.id) { mutableStateOf(target.text) }
        var editWRemark by remember(target.id) { mutableStateOf(target.remark) }

        AlertDialog(
            onDismissRequest = { wordBeingEdited = null },
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

