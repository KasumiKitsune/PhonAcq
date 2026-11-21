package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.components.StudioCheckbox
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Project
import com.example.data.Speaker
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioMotion
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.studioAnimateItem
import com.example.ui.components.studioRotateOn
import com.example.viewmodel.RecordingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StatsSortOrder {
    BY_SIZE_DESC,
    BY_TIME_DESC
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return if (kb < 10.0) String.format(Locale.US, "%.1f KB", kb)
        else String.format(Locale.US, "%.0f KB", kb)
    }
    val mb = kb / 1024.0
    if (mb < 1024.0) {
        return String.format(Locale.US, "%.1f MB", mb)
    }
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun formatCorpusDuration(durationMs: Long): String {
    if (durationMs <= 0L) return S.management.durationZero
    val totalSec = (durationMs / 1000L).toInt()
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return when {
        hours > 0 -> S.management.durationHoursMinutes.withArgs("hours" to hours, "mins" to mins)
        mins > 0 -> S.management.durationMinutesSeconds.withArgs("mins" to mins, "secs" to secs)
        else -> S.management.durationSecondsFormat.withArgs(
            "secs" to String.format(Locale.US, "%.1f", durationMs / 1000f)
        )
    }
}

/**
 * 手机端全屏「录音统计与清理」页面（支持 Android 预测性返回手势与多选防呆）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalManagementDialog(
    viewModel: RecordingViewModel,
    onDismiss: () -> Unit,
    onOpenProjectInWorkspace: () -> Unit = {}
) {
    val backVisualState = rememberPredictiveBackVisualState()
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedProjectIds = remember { mutableStateListOf<Long>() }

    PredictiveBackGestureHandler(
        enabled = true,
        state = backVisualState
    ) {
        if (isSelectionMode) {
            selectedProjectIds.clear()
            isSelectionMode = false
        } else {
            onDismiss()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .predictiveBackLayer(backVisualState)
            .testTag("global_management_screen"),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = { StudioMotion.subtleFadeScale() },
                        label = "GlobalMgmtTopTitle"
                    ) { selecting ->
                        if (selecting) {
                            Text(
                                text = S.management.selectedProjectsCount.withArgs("count" to selectedProjectIds.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Column {
                                Text(
                                    text = S.management.screenTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = S.management.screenSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                selectedProjectIds.clear()
                                isSelectionMode = false
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.testTag("close_global_management_button")
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.common.back
                        )
                    }
                },
                actions = {
                    val projects by viewModel.allProjects.collectAsState()
                    if (projects.isNotEmpty()) {
                        if (isSelectionMode) {
                            TextButton(
                                onClick = {
                                    if (selectedProjectIds.size == projects.size) {
                                        selectedProjectIds.clear()
                                    } else {
                                        selectedProjectIds.clear()
                                        selectedProjectIds.addAll(projects.map { it.id })
                                    }
                                }
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (selectedProjectIds.size == projects.size) S.management.deselectAll else S.management.selectAll,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    isSelectionMode = true
                                    selectedProjectIds.clear()
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(S.management.multiSelectManage, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        RecordingStatsAndCleanupBody(
            viewModel = viewModel,
            isSelectionMode = isSelectionMode,
            onSelectionModeChange = { isSelectionMode = it },
            selectedProjectIds = selectedProjectIds,
            onDismiss = onDismiss,
            onOpenProjectInWorkspace = onOpenProjectInWorkspace,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

/**
 * 平板端右侧分栏「录音统计与清理」面板（复用同一套统计与清理核心组件）
 */
@Composable
fun GlobalManagementPane(
    viewModel: RecordingViewModel,
    onDismiss: () -> Unit,
    onOpenProjectInWorkspace: () -> Unit = {}
) {
    val projects by viewModel.allProjects.collectAsState()
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedProjectIds = remember { mutableStateListOf<Long>() }

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = isSelectionMode,
            transitionSpec = { StudioMotion.subtleFadeScale() },
            label = "GlobalMgmtPaneTopBar"
        ) { selecting ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selecting) {
                    Text(
                        text = S.management.selectedProjectsCount.withArgs("count" to selectedProjectIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                if (selectedProjectIds.size == projects.size) {
                                    selectedProjectIds.clear()
                                } else {
                                    selectedProjectIds.clear()
                                    selectedProjectIds.addAll(projects.map { it.id })
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedProjectIds.size == projects.size) S.management.deselectAll else S.management.selectAll,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        TextButton(
                            onClick = {
                                selectedProjectIds.clear()
                                isSelectionMode = false
                            }
                        ) {
                            Text(S.common.cancel, fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = S.management.screenTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (projects.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    isSelectionMode = true
                                    selectedProjectIds.clear()
                                }
                            ) {
                                Text(S.management.multiSelectManage, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = S.common.close, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        RecordingStatsAndCleanupBody(
            viewModel = viewModel,
            isSelectionMode = isSelectionMode,
            onSelectionModeChange = { isSelectionMode = it },
            selectedProjectIds = selectedProjectIds,
            onDismiss = onDismiss,
            onOpenProjectInWorkspace = onOpenProjectInWorkspace,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RecordingStatsAndCleanupBody(
    viewModel: RecordingViewModel,
    isSelectionMode: Boolean,
    onSelectionModeChange: (Boolean) -> Unit,
    selectedProjectIds: MutableList<Long>,
    onDismiss: () -> Unit,
    onOpenProjectInWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projects by viewModel.allProjects.collectAsState()
    val allSpeakers by viewModel.allSpeakers.collectAsState()
    val projectSummaries by viewModel.projectSummaries.collectAsState()
    val takesRefreshTrigger by viewModel.takesRefreshTrigger.collectAsState()

    var manualRefreshTick by remember { mutableIntStateOf(0) }
    var sortOrder by remember { mutableStateOf(StatsSortOrder.BY_SIZE_DESC) }

    // Asynchronous storage & corpus report computed strictly on Dispatchers.IO
    val storageReport by produceState(
        initialValue = RecordingViewModel.CorpusStorageReport(),
        projects,
        allSpeakers,
        projectSummaries,
        takesRefreshTrigger,
        manualRefreshTick
    ) {
        value = viewModel.computeCorpusStorageReport()
    }

    var projectToDeleteSingle by remember { mutableStateOf<Project?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showGlobalCleanBackupsConfirm by remember { mutableStateOf(false) }
    var projectForBackupClean by remember { mutableStateOf<RecordingViewModel.ProjectStorageStat?>(null) }
    var speakerRecordingsToDelete by remember {
        mutableStateOf<Pair<Project, RecordingViewModel.SpeakerStorageStat>?>(null)
    }

    val sortedProjects = remember(projects, storageReport, sortOrder) {
        when (sortOrder) {
            StatsSortOrder.BY_SIZE_DESC -> projects.sortedWith(
                compareByDescending<Project> { storageReport.projectStats[it.id]?.totalBytes ?: 0L }
                    .thenByDescending { it.createdAt }
            )
            StatsSortOrder.BY_TIME_DESC -> projects.sortedByDescending { it.createdAt }
        }
    }

    val selectedTotalBytes = remember(selectedProjectIds.toList(), storageReport) {
        selectedProjectIds.sumOf { pid -> storageReport.projectStats[pid]?.totalBytes ?: 0L }
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier) {
        ResponsiveContentContainer(
            maxWidth = 860.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = if (isSelectionMode) 96.dp else 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // 模块 1：全局语料与存储看板 (Hero Card)
            item(key = "hero_storage_card", contentType = "hero_card") {
                GlobalCorpusStorageHeroCard(
                    report = storageReport,
                    projectCount = projects.size,
                    totalSpeakersCount = allSpeakers.size
                )
            }

            // 模块 2：一键清理无用文件（不删当前在用的录音）
            item(key = "safe_slimming_section", contentType = "safe_slimming") {
                SafeStorageSlimmingSection(
                    report = storageReport,
                    onCleanTempAndOrphan = {
                        if (storageReport.tempAndOrphanBytes <= 0L && storageReport.tempAndOrphanFileCount <= 0) {
                            Toast.makeText(context, S.management.noCacheToCleanToast, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.cleanTempAndOrphanFiles { freedBytes, count ->
                                manualRefreshTick++
                                Toast.makeText(
                                    context,
                                    S.management.cleanedCacheResultToast.withArgs(
                                        "count" to count,
                                        "size" to formatStorageSize(freedBytes)
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onRequestCleanAllBackups = {
                        if (storageReport.backupTakeCount <= 0) {
                            Toast.makeText(context, S.management.noBackupsToCleanToast, Toast.LENGTH_SHORT).show()
                        } else {
                            showGlobalCleanBackupsConfirm = true
                        }
                    }
                )
            }

            // 模块 3：各项目录音明细与清理标题栏
            item(key = "projects_section_header", contentType = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = S.management.projectDetailsHeader.withArgs("count" to projects.size),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSelectionMode) S.management.hintMultiSelectMode else S.management.hintTapExpandLongPressSelect,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (projects.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    sortOrder = if (sortOrder == StatsSortOrder.BY_SIZE_DESC) {
                                        StatsSortOrder.BY_TIME_DESC
                                    } else {
                                        StatsSortOrder.BY_SIZE_DESC
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = S.management.toggleSortDesc,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (sortOrder == StatsSortOrder.BY_SIZE_DESC) S.management.sortBySize else S.management.sortByTime,
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

            if (sortedProjects.isEmpty()) {
                item(key = "empty_projects_placeholder", contentType = "empty_state") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = S.management.emptyProjectsTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (DesktopAdaptiveState.isDesktopPlatform) {
                                    S.management.emptyProjectsDesktopHint
                                } else {
                                    S.management.emptyProjectsMobileHint
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(
                    items = sortedProjects,
                    key = { it.id },
                    contentType = { "project_storage_card" }
                ) { project ->
                    val projStat = storageReport.projectStats[project.id]
                    ProjectStorageExpandableCard(
                        project = project,
                        projectStat = projStat,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedProjectIds.contains(project.id),
                        onSelectedChange = { checked ->
                            if (checked) {
                                if (!selectedProjectIds.contains(project.id)) {
                                    selectedProjectIds.add(project.id)
                                }
                            } else {
                                selectedProjectIds.remove(project.id)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                onSelectionModeChange(true)
                                selectedProjectIds.clear()
                                selectedProjectIds.add(project.id)
                            }
                        },
                        onOpenProjectWithSpeaker = { speaker ->
                            viewModel.selectProjectAndSpeaker(project, speaker)
                            onDismiss()
                            onOpenProjectInWorkspace()
                        },
                        onRequestCleanProjectBackups = { stat ->
                            projectForBackupClean = stat
                        },
                        onRequestDeleteSpeakerRecordings = { spkStat ->
                            speakerRecordingsToDelete = project to spkStat
                        },
                        onRequestDeleteProject = {
                            projectToDeleteSingle = project
                        },
                        modifier = studioAnimateItem()
                    )
                }
            }
        }
        }

        PlatformVerticalScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // 多选模式底部悬浮操作栏
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = StudioMotion.verticalPanelEnter,
            exit = StudioMotion.verticalPanelExit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            ResponsiveContentContainer(maxWidth = 860.dp) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = S.management.selectedProjectsCountBar.withArgs("count" to selectedProjectIds.size),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = S.management.deleteWillFreeSize.withArgs("size" to formatStorageSize(selectedTotalBytes)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedProjectIds.clear()
                                onSelectionModeChange(false)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = S.common.cancel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Button(
                            onClick = {
                                if (selectedProjectIds.isNotEmpty()) {
                                    showBatchDeleteConfirm = true
                                }
                            },
                            enabled = selectedProjectIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = S.management.deleteSelectedButton,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // 1. 全局清理重录旧录音确认弹窗
    com.example.ui.components.AnimatedDialogVisibility(visible = showGlobalCleanBackupsConfirm) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showGlobalCleanBackupsConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(S.management.cleanBackupsDialogTitle, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = S.management.cleanBackupsDialogCount.withArgs(
                            "count" to storageReport.backupTakeCount,
                            "size" to formatStorageSize(storageReport.backupTakeBytes)
                        ),
                        fontSize = 14.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = S.management.cleanBackupsSafeNote,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGlobalCleanBackupsConfirm = false
                        viewModel.cleanBackupTakes(targetProjectId = null) { freedBytes, count ->
                            manualRefreshTick++
                            Toast.makeText(
                                context,
                                S.management.cleanedBackupsResultToast.withArgs(
                                    "count" to count,
                                    "size" to formatStorageSize(freedBytes)
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(S.management.confirmCleanButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGlobalCleanBackupsConfirm = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // 2. 单项目清理重录旧录音确认弹窗
    com.example.ui.components.AnimatedNullableDialogVisibility(value = projectForBackupClean) { stat ->
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { projectForBackupClean = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(S.management.cleanProjectBackupsTitle.withArgs("name" to stat.project.name), fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(
                    S.management.cleanProjectBackupsBody.withArgs(
                        "count" to stat.backupTakeCount,
                        "size" to formatStorageSize(stat.backupTakeBytes)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pid = stat.project.id
                        projectForBackupClean = null
                        viewModel.cleanBackupTakes(targetProjectId = pid) { freedBytes, count ->
                            manualRefreshTick++
                            Toast.makeText(
                                context,
                                S.management.cleanedProjectBackupsToast.withArgs(
                                    "count" to count,
                                    "size" to formatStorageSize(freedBytes)
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(S.management.confirmCleanButton, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectForBackupClean = null }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // 3. 清空某位发音人在某项目下的录音确认弹窗（防止误触！）
    com.example.ui.components.AnimatedNullableDialogVisibility(value = speakerRecordingsToDelete) { (proj, spkStat) ->
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { speakerRecordingsToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(S.management.clearSpeakerAudioTitle, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(
                    S.management.clearSpeakerAudioBody.withArgs(
                        "speaker" to spkStat.speaker.name,
                        "project" to proj.name,
                        "count" to spkStat.recordedCount,
                        "size" to formatStorageSize(spkStat.totalBytes)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        speakerRecordingsToDelete = null
                        viewModel.deleteSpeakerRecordingsInProject(proj.id, spkStat.speaker.id) {
                            manualRefreshTick++
                            Toast.makeText(
                                context,
                                S.management.clearedSpeakerAudioToast.withArgs(
                                    "speaker" to spkStat.speaker.name,
                                    "project" to proj.name
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(S.management.confirmClearSpeakerBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { speakerRecordingsToDelete = null }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // 4. 删除单个字表项目确认弹窗
    com.example.ui.components.AnimatedNullableDialogVisibility(value = projectToDeleteSingle) { project ->
        val sizeStr = formatStorageSize(storageReport.projectStats[project.id]?.totalBytes ?: 0L)
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { projectToDeleteSingle = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(S.management.deleteProjectTitle, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text(
                    S.management.deleteProjectBody.withArgs(
                        "name" to project.name,
                        "size" to sizeStr
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(project)
                        projectToDeleteSingle = null
                        manualRefreshTick++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(S.management.confirmDeleteProjectBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDeleteSingle = null }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // 5. 批量删除项目确认弹窗
    com.example.ui.components.AnimatedDialogVisibility(visible = showBatchDeleteConfirm) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    S.management.batchDeleteProjectsTitle.withArgs("count" to selectedProjectIds.size),
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    S.management.batchDeleteProjectsBody.withArgs(
                        "count" to selectedProjectIds.size,
                        "size" to formatStorageSize(selectedTotalBytes)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = projects.filter { it.id in selectedProjectIds }
                        toDelete.forEach { proj ->
                            viewModel.deleteProject(proj)
                        }
                        selectedProjectIds.clear()
                        onSelectionModeChange(false)
                        showBatchDeleteConfirm = false
                        manualRefreshTick++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(S.management.confirmBatchDeleteBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }
}

/**
 * 模块 1：全局语料与存储看板 (Hero Card)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlobalCorpusStorageHeroCard(
    report: RecordingViewModel.CorpusStorageReport,
    projectCount: Int,
    totalSpeakersCount: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    val activeColor = colorScheme.primary
    val backupColor = colorScheme.tertiary
    val cacheColor = colorScheme.outline

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colorScheme.primaryContainer.copy(alpha = 0.42f),
                            colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Top Twin Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left: Total Storage
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = S.management.usedStorageHeader,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatStorageSize(report.totalStorageBytes),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface
                        )
                        val reclaimableBytes = report.backupTakeBytes + report.tempAndOrphanBytes
                        Text(
                            text = if (reclaimableBytes > 0L) {
                                S.management.reclaimableSizeLine.withArgs("size" to formatStorageSize(reclaimableBytes))
                            } else {
                                S.management.noReclaimableSpace
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (reclaimableBytes > 0L) colorScheme.tertiary else colorScheme.onSurfaceVariant
                        )
                    }

                    // Right: Corpus Scale
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.surface.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = S.management.totalActiveRecordingsHeader,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = S.management.recordingsCountUnit.withArgs("count" to report.totalActiveRecordings),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = S.management.corpusSummaryLine.withArgs(
                                    "duration" to formatCorpusDuration(report.totalDurationMs),
                                    "projects" to projectCount,
                                    "speakers" to totalSpeakersCount
                                ),
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Segmented Storage Proportion Bar
                val totalB = report.totalStorageBytes.coerceAtLeast(1L).toFloat()
                val activeFrac = if (report.totalStorageBytes > 0L) {
                    (report.activeBytes.toFloat() / totalB).coerceIn(0f, 1f)
                } else 1f
                val backupFrac = if (report.totalStorageBytes > 0L) {
                    (report.backupTakeBytes.toFloat() / totalB).coerceIn(0f, 1f)
                } else 0f
                val cacheFrac = if (report.totalStorageBytes > 0L) {
                    (report.tempAndOrphanBytes.toFloat() / totalB).coerceIn(0f, 1f)
                } else 0f

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colorScheme.surfaceContainerHighest)
                    ) {
                        if (report.totalStorageBytes > 0L) {
                            if (activeFrac > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(activeFrac.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(activeColor)
                                )
                            }
                            if (backupFrac > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(backupFrac.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(backupColor)
                                )
                            }
                            if (cacheFrac > 0.005f) {
                                Box(
                                    modifier = Modifier
                                        .weight(cacheFrac.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(cacheColor)
                                )
                            }
                        }
                    }

                    // Legend Items
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StorageLegendDot(
                            color = activeColor,
                            label = S.management.legendActiveAudio,
                            valueText = formatStorageSize(report.activeBytes)
                        )
                        StorageLegendDot(
                            color = backupColor,
                            label = S.management.legendBackupAudio.withArgs("count" to report.backupTakeCount),
                            valueText = formatStorageSize(report.backupTakeBytes)
                        )
                        StorageLegendDot(
                            color = cacheColor,
                            label = S.management.legendTempCache,
                            valueText = formatStorageSize(report.tempAndOrphanBytes)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageLegendDot(
    color: Color,
    label: String,
    valueText: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = S.management.legendLabelWithColon.withArgs("label" to label),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = valueText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 模块 2：一键安全瘦身（不伤正式数据）
 */
@Composable
private fun SafeStorageSlimmingSection(
    report: RecordingViewModel.CorpusStorageReport,
    onCleanTempAndOrphan: () -> Unit,
    onRequestCleanAllBackups: () -> Unit
) {
    val hasCacheToClean = report.tempAndOrphanBytes > 0L || report.tempAndOrphanFileCount > 0
    val hasBackupsToClean = report.backupTakeCount > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: Clean Temp Export Cache & Orphans
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (hasCacheToClean) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = BorderStroke(
                1.dp,
                if (hasCacheToClean) MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onCleanTempAndOrphan)
                .testTag("clean_temp_cache_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = if (hasCacheToClean) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasCacheToClean) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = if (hasCacheToClean) S.management.goCleanButton else S.management.noNeedCleanButton,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCacheToClean) {
                                MaterialTheme.colorScheme.onSecondary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = S.management.cleanTempCacheTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasCacheToClean) {
                        S.management.cleanTempCacheSubCount.withArgs(
                            "size" to formatStorageSize(report.tempAndOrphanBytes),
                            "count" to report.tempAndOrphanFileCount
                        )
                    } else {
                        S.management.cleanTempCacheEmptySub
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Card 2: Clean Backup Takes (Keep Active Recordings)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (hasBackupsToClean) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = BorderStroke(
                1.dp,
                if (hasBackupsToClean) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onRequestCleanAllBackups)
                .testTag("clean_backup_takes_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = if (hasBackupsToClean) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasBackupsToClean) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = if (hasBackupsToClean) S.management.goCleanButton else S.management.noNeedCleanButton,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasBackupsToClean) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = S.management.cleanBackupAudioTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (hasBackupsToClean) {
                        S.management.cleanBackupAudioSubCount.withArgs(
                            "size" to formatStorageSize(report.backupTakeBytes),
                            "count" to report.backupTakeCount
                        )
                    } else {
                        S.management.cleanBackupAudioEmptySub
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 模块 3：按项目与发音人精细管理折叠卡片
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ProjectStorageExpandableCard(
    project: Project,
    projectStat: RecordingViewModel.ProjectStorageStat?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onLongClick: () -> Unit,
    onOpenProjectWithSpeaker: (Speaker?) -> Unit,
    onRequestCleanProjectBackups: (RecordingViewModel.ProjectStorageStat) -> Unit,
    onRequestDeleteSpeakerRecordings: (RecordingViewModel.SpeakerStorageStat) -> Unit,
    onRequestDeleteProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) {
            expanded = false
        }
    }

    val totalWords = projectStat?.totalWords ?: 0
    val totalRecordedCount = projectStat?.totalRecordedCount ?: 0
    val totalBytes = projectStat?.totalBytes ?: 0L
    val backupCount = projectStat?.backupTakeCount ?: 0
    val backupBytes = projectStat?.backupTakeBytes ?: 0L
    val speakerStats = projectStat?.speakerStats ?: emptyList()
    val startedSpeakerCount = remember(speakerStats) { speakerStats.count { it.recordedCount > 0 } }
    val completedSpeakerCount = remember(speakerStats, totalWords) {
        if (totalWords <= 0) 0 else speakerStats.count { it.recordedCount >= totalWords }
    }

    val overallProgress = remember(speakerStats, totalWords) {
        val activeList = speakerStats.filter { it.recordedCount > 0 }
        if (totalWords <= 0 || activeList.isEmpty()) {
            0f
        } else {
            val sumRec = activeList.sumOf { it.recordedCount }
            (sumRec.toFloat() / (activeList.size * totalWords)).coerceIn(0f, 1f)
        }
    }

    val dateStr = remember(project.createdAt) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(project.createdAt))
    }

    val cardShape = RoundedCornerShape(20.dp)
    val headerInteractionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stats_project_card_${project.id}"),
        shape = cardShape,
        color = if (isSelected && isSelectionMode) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected && isSelectionMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Card Header (isolated click target without circular ripple overflow when card expands)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        interactionSource = headerInteractionSource,
                        indication = null,
                        onClick = {
                            if (isSelectionMode) {
                                onSelectedChange(!isSelected)
                            } else {
                                expanded = !expanded
                            }
                        },
                        onLongClick = onLongClick
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSelectionMode) {
                            StudioCheckbox(
                                checked = isSelected,
                                onCheckedChange = { onSelectedChange(it) },
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = project.name,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (project.speakerGroupName.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = project.speakerGroupName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .widthIn(max = 96.dp)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = S.management.projectMetaRowBase.withArgs(
                                        "words" to totalWords,
                                        "started" to startedSpeakerCount
                                    ) +
                                        (if (completedSpeakerCount > 0) S.management.projectMetaRowFullSuffix.withArgs("completed" to completedSpeakerCount) else "") +
                                        S.management.projectMetaRowTail.withArgs(
                                            "tokens" to totalRecordedCount,
                                            "date" to dateStr
                                        ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (totalBytes > 0L) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                text = formatStorageSize(totalBytes),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (totalBytes > 0L) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (!isSelectionMode) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (expanded) S.management.collapseDetailsDesc else S.management.expandDetailsDesc,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.studioRotateOn(expanded)
                            )
                        }
                    }
                }

                // Project Overall Progress Bar
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (overallProgress >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }

            // Expanded Details: Speaker-level breakdown + Project Cleanup Actions
            AnimatedVisibility(
                visible = expanded && !isSelectionMode,
                enter = StudioMotion.verticalPanelEnter,
                exit = StudioMotion.verticalPanelExit
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f))
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val activeSpeakers = speakerStats.filter { it.recordedCount > 0 || it.totalBytes > 0L }

                        if (activeSpeakers.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = S.management.noSpeakerAudioInProject,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                FilledTonalButton(
                                    onClick = { onOpenProjectWithSpeaker(null) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(S.management.goToStudioButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        } else {
                            Text(
                                text = S.management.speakerDetailsHeader,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            activeSpeakers.forEach { spkStat ->
                                val spk = spkStat.speaker
                                val spkProgress = if (totalWords > 0) {
                                    (spkStat.recordedCount.toFloat() / totalWords).coerceIn(0f, 1f)
                                } else 0f
                                val isCompleted = totalWords > 0 && spkStat.recordedCount >= totalWords

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = if (isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Text(
                                                        text = spk.name,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (spk.dialect.isNotBlank()) {
                                                        Text(
                                                            text = "· ${spk.dialect}",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                val backupNote = if (spkStat.backupTakeCount > 0) {
                                                    S.management.backupTakeExtraSuffix.withArgs("count" to spkStat.backupTakeCount)
                                                } else ""
                                                Text(
                                                    text = S.management.speakerRecordedProgress.withArgs(
                                                        "recorded" to spkStat.recordedCount,
                                                        "total" to totalWords
                                                    ) +
                                                        "${formatCorpusDuration(spkStat.totalDurationMs)} · " +
                                                        "${formatStorageSize(spkStat.totalBytes)}$backupNote",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                FilledTonalIconButton(
                                                    onClick = { onOpenProjectWithSpeaker(spk) },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = S.management.switchToSpeakerContinueDesc,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onRequestDeleteSpeakerRecordings(spkStat) },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = S.management.clearSpeakerInProjectDesc,
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        LinearProgressIndicator(
                                            progress = { spkProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp)),
                                            color = if (isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            gapSize = 0.dp,
                                            drawStopIndicator = {}
                                        )
                                    }
                                }
                            }
                        }

                        // Project Card Bottom Action Row (adaptive for narrow 360dp/380dp panes)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        ) {
                            val isCompactRow = maxWidth < 310.dp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (backupCount > 0 && projectStat != null) {
                                    FilledTonalButton(
                                        onClick = { onRequestCleanProjectBackups(projectStat) },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .defaultMinSize(minHeight = 36.dp)
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isCompactRow) {
                                                S.management.cleanOldCompact.withArgs("size" to formatStorageSize(backupBytes))
                                            } else {
                                                S.management.cleanProjectOldFull.withArgs("size" to formatStorageSize(backupBytes))
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }

                                TextButton(
                                    onClick = onRequestDeleteProject,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isCompactRow && backupCount > 0) S.management.deleteProjectShort else S.management.deleteWholeProject,
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
            }
        }
    }
}
