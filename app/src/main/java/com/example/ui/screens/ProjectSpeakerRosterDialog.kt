package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.Project
import com.example.data.Speaker
import com.example.data.SpeakerProgressInfo
import com.example.data.belongsToGroup
import com.example.data.isSpeakerCandidate
import com.example.data.parseAssignedSpeakerIds
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.AdaptiveModalSurface
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.StudioCheckbox
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioHorizontalLazyStrip
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.StudioMotion
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.ui.components.studioTactile
import com.example.ui.theme.LocalPhonAcqExtendedColors
import com.example.viewmodel.RecordingViewModel

/**
 * Formats a concise human-readable summary of participating speakers for cards and editor rows.
 */
fun formatProjectRosterSummary(
    participatingSpeakers: List<Speaker>,
    totalLibrarySpeakers: Int
): String {
    val count = participatingSpeakers.size
    if (count == 0) return S.wordEditor.assignedSpeakersSummaryNone
    if (totalLibrarySpeakers > 2 && count >= totalLibrarySpeakers) {
        return S.wordEditor.assignedSpeakersSummaryAll.withArgs("count" to count)
    }
    return when {
        count <= 2 -> participatingSpeakers.joinToString("、") { it.name }
        else -> {
            val head = participatingSpeakers.take(2).joinToString("、") { it.name }
            S.wordEditor.assignedSpeakersSummaryTwoPlus.withArgs(
                "names" to head,
                "count" to count
            )
        }
    }
}

/**
 * Cross-platform (Android 360dp+ & Desktop) modal dialog for managing participating speakers
 * of a single wordlist project.
 *
 * Supports two modes:
 * 1. Saved project mode ([project] != null): displays real per-speaker recording progress,
 *    locks speakers who already have recordings in this project, supports 1-tap "Go Record",
 *    and persists changes directly to [Project.assignedSpeakerIds].
 * 2. Draft mode ([project] == null): manages an in-memory [draftSelectedSpeakerIds] set for
 *    an unsaved new wordlist in WordListEditorScreen, with group quick-select and inline
 *    speaker creation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectSpeakerRosterDialog(
    viewModel: RecordingViewModel,
    project: Project?,
    projectDisplayName: String = project?.name ?: "",
    draftSelectedSpeakerIds: Set<Long> = emptySet(),
    onDraftConfirm: ((Set<Long>) -> Unit)? = null,
    onStartRecordingWithSpeaker: ((Speaker) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allSpeakers by viewModel.allSpeakers.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()
    val speakerGroups by viewModel.speakerGroups.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val summariesMap by viewModel.projectSummaries.collectAsState()

    val liveProject = remember(project, allProjects) {
        if (project != null) {
            allProjects.firstOrNull { it.id == project.id } ?: project
        } else {
            null
        }
    }
    val summary = liveProject?.let { summariesMap[it.id] }
    val totalWords = summary?.totalWords ?: 0
    val progressBySpeakerId = remember(summary) {
        summary?.speakerProgressList?.associateBy { it.speaker.id } ?: emptyMap()
    }

    // Authoritative recorded speaker IDs for this project (never allow removing recorded speakers)
    var dbRecordedSpeakerIds by remember(liveProject?.id) {
        mutableStateOf<Set<Long>>(
            progressBySpeakerId.values
                .filter { it.recordedCount > 0 }
                .mapTo(LinkedHashSet()) { it.speaker.id }
        )
    }
    LaunchedEffect(liveProject?.id) {
        if (liveProject != null) {
            dbRecordedSpeakerIds = viewModel.getRecordedSpeakerIdsForProject(liveProject.id)
        } else {
            dbRecordedSpeakerIds = emptySet()
        }
    }
    val lockedRecordedIds = remember(dbRecordedSpeakerIds, progressBySpeakerId) {
        val set = LinkedHashSet<Long>(dbRecordedSpeakerIds)
        progressBySpeakerId.values.forEach { info ->
            if (info.recordedCount > 0) set.add(info.speaker.id)
        }
        set
    }

    // Compute initial selection once per dialog open (keyed on project id)
    var selectedIds by remember(liveProject?.id) {
        val initial = LinkedHashSet<Long>()
        if (liveProject != null) {
            val assignedSet = parseAssignedSpeakerIds(liveProject.assignedSpeakerIds)
            when {
                liveProject.assignedSpeakerIds.isNotBlank() && assignedSet.isNotEmpty() -> {
                    val validLibraryIds = allSpeakers.mapTo(HashSet()) { it.id }
                    assignedSet.forEach { id -> if (id in validLibraryIds) initial.add(id) }
                }
                summary != null -> {
                    summary.speakerProgressList.forEach { initial.add(it.speaker.id) }
                }
                else -> {
                    allSpeakers.forEach { spk ->
                        val recCount = progressBySpeakerId[spk.id]?.recordedCount ?: 0
                        if (liveProject.isSpeakerCandidate(spk, recCount, isSimpleMode, assignedSet)) {
                            initial.add(spk.id)
                        }
                    }
                }
            }
            initial.addAll(lockedRecordedIds)
        } else {
            if (draftSelectedSpeakerIds.isNotEmpty()) {
                val validLibraryIds = allSpeakers.mapTo(HashSet()) { it.id }
                draftSelectedSpeakerIds.forEach { id -> if (id in validLibraryIds) initial.add(id) }
            } else {
                allSpeakers.forEach { initial.add(it.id) }
            }
        }
        mutableStateOf<Set<Long>>(initial)
    }

    // Always ensure asynchronously fetched recorded speaker IDs remain selected & locked
    LaunchedEffect(lockedRecordedIds) {
        if (lockedRecordedIds.any { it !in selectedIds }) {
            selectedIds = selectedIds + lockedRecordedIds
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isCreatingNewSpeaker by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Inline "Create Speaker & Join" form fields
    val presetGenders = remember { listOf("男", "女", "多元/不限") }
    var newName by remember { mutableStateOf("") }
    var newGender by remember { mutableStateOf("男") }
    var newAgeText by remember { mutableStateOf("25") }
    var newDialect by remember { mutableStateOf("") }
    var newGroupName by remember { mutableStateOf("") }
    var newNotes by remember { mutableStateOf("") }
    var isCreatingSubmitting by remember { mutableStateOf(false) }

    fun resetCreateForm() {
        newName = ""
        newGender = "男"
        newAgeText = "25"
        newDialect = ""
        newGroupName = ""
        newNotes = ""
    }

    // Keep ordering stable while toggling checkboxes (recorded speakers stay first, then library order)
    val orderedSpeakers = remember(allSpeakers, lockedRecordedIds) {
        allSpeakers.sortedByDescending { it.id in lockedRecordedIds }
    }
    val filteredSpeakers = remember(orderedSpeakers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            orderedSpeakers
        } else {
            orderedSpeakers.filter { spk ->
                spk.name.contains(q, ignoreCase = true) ||
                    spk.dialect.contains(q, ignoreCase = true) ||
                    spk.groupName.contains(q, ignoreCase = true)
            }
        }
    }
    val nonEmptyGroups = remember(speakerGroups, allSpeakers) {
        speakerGroups.filter { grp -> allSpeakers.any { it.belongsToGroup(grp) } }
    }

    val headerTitle = remember(projectDisplayName) {
        val cleanName = projectDisplayName.trim()
        if (cleanName.isNotEmpty()) {
            S.speaker.rosterDialogTitle.withArgs("project" to cleanName)
        } else {
            S.speaker.rosterDialogDraftTitle
        }
    }

    StudioModalDialog(
        onDismissRequest = {
            if (isCreatingNewSpeaker) {
                isCreatingNewSpeaker = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = !isCreatingNewSpeaker,
            usePlatformDefaultWidth = false
        )
    ) {
        AdaptiveModalSurface(
            targetMaxWidth = 580.dp,
            maxHeightFraction = 0.90f,
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .imePadding()
                .testTag("project_speaker_roster_dialog")
        ) {
            AnimatedContent(
                targetState = isCreatingNewSpeaker,
                transitionSpec = { StudioMotion.subtleFadeScale() },
                label = "ProjectRosterViewSwitch",
                modifier = Modifier.fillMaxWidth()
            ) { creatingMode ->
                if (!creatingMode) {
                    val cs = MaterialTheme.colorScheme
                    val extendedColors = LocalPhonAcqExtendedColors.current
                    val listState = rememberLazyListState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Header
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
                                Surface(
                                    shape = CircleShape,
                                    color = cs.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Groups,
                                            contentDescription = null,
                                            tint = cs.onPrimaryContainer,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = headerTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitleText = if (lockedRecordedIds.isNotEmpty()) {
                                        S.speaker.rosterSelectedWithLockedCount.withArgs(
                                            "count" to selectedIds.size,
                                            "locked" to lockedRecordedIds.size
                                        )
                                    } else {
                                        S.speaker.rosterSelectedCount.withArgs("count" to selectedIds.size)
                                    }
                                    Text(
                                        text = subtitleText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedIds.isEmpty()) cs.error else cs.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(34.dp)
                                    .then(if (DesktopAdaptiveState.isDesktopPlatform) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = S.common.close,
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // 2. Compact 40.dp Search Bar (when global library has >= 5 speakers)
                        if (allSpeakers.size >= 5) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cs.surfaceContainerLow,
                                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.75f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = S.speaker.rosterSearchPlaceholder,
                                                fontSize = 13.sp,
                                                color = cs.onSurfaceVariant.copy(alpha = 0.75f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 13.sp,
                                                color = cs.onSurface
                                            ),
                                            cursorBrush = SolidColor(cs.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("roster_search_input")
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = S.common.clear,
                                                tint = cs.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. One-tap Group Quick-Select Strip (Advanced mode with non-empty groups)
                        if (!isSimpleMode && nonEmptyGroups.isNotEmpty()) {
                            val groupStripState = rememberLazyListState()
                            val allIds = remember(allSpeakers) { allSpeakers.mapTo(LinkedHashSet()) { it.id } }
                            val isAllSelected = allIds.isNotEmpty() && allIds.all { it in selectedIds }

                            StudioHorizontalLazyStrip(
                                state = groupStripState,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LazyRow(
                                    state = groupStripState,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item(key = "roster_select_all_chip") {
                                        StudioFilterChip(
                                            selected = isAllSelected,
                                            onClick = {
                                                if (isAllSelected) {
                                                    if (lockedRecordedIds.size >= allIds.size) {
                                                        Toast.makeText(
                                                            context,
                                                            S.speaker.rosterLockedHasRecordingToast,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        selectedIds = lockedRecordedIds
                                                    }
                                                } else {
                                                    selectedIds = allIds
                                                }
                                            },
                                            labelText = S.speaker.rosterSelectAllChip
                                        )
                                    }
                                    items(nonEmptyGroups, key = { "grp_$it" }) { grp ->
                                        val members = remember(allSpeakers, grp) {
                                            allSpeakers.filter { it.belongsToGroup(grp) }
                                        }
                                        val memberIds = remember(members) { members.mapTo(LinkedHashSet()) { it.id } }
                                        val selectedInGroup = memberIds.count { it in selectedIds }
                                        val allMembersSelected = memberIds.isNotEmpty() && selectedInGroup == memberIds.size

                                        StudioFilterChip(
                                            selected = allMembersSelected,
                                            onClick = {
                                                if (allMembersSelected) {
                                                    val removable = memberIds.filterNot { it in lockedRecordedIds }
                                                    if (removable.isEmpty()) {
                                                        Toast.makeText(
                                                            context,
                                                            S.speaker.rosterGroupAllLockedToast,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        selectedIds = selectedIds - removable.toSet()
                                                    }
                                                } else {
                                                    selectedIds = selectedIds + memberIds
                                                }
                                            },
                                            labelText = S.speaker.rosterGroupChipFormat.withArgs(
                                                "group" to grp,
                                                "selected" to selectedInGroup,
                                                "total" to memberIds.size
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Main Speaker Checklist
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(min = 120.dp)
                        ) {
                            if (filteredSpeakers.isEmpty()) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp)
                                ) {
                                    Text(
                                        text = if (allSpeakers.isEmpty()) {
                                            S.speaker.rosterEmptyLibraryTitle
                                        } else {
                                            S.speaker.rosterEmptySearchTitle
                                        },
                                        fontSize = 13.sp,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(
                                        end = if (DesktopAdaptiveState.isDesktopPlatform) 11.dp else 0.dp,
                                        top = 2.dp,
                                        bottom = 4.dp
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(filteredSpeakers, key = { it.id }) { speaker ->
                                        val prog = progressBySpeakerId[speaker.id]
                                        val recordedCount = prog?.recordedCount ?: 0
                                        val isLocked = speaker.id in lockedRecordedIds
                                        val isSelected = isLocked || speaker.id in selectedIds
                                        val isDone = totalWords > 0 && recordedCount >= totalWords
                                        val hasStarted = recordedCount > 0

                                        val (rowSrc, isHoveredState) = rememberStudioInteraction()
                                        val isHovered by isHoveredState
                                        val rowBg by animateColorAsState(
                                            targetValue = when {
                                                isSelected && isHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.50f)
                                                isSelected -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.30f)
                                                isHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.18f)
                                                else -> cs.surfaceContainerLow
                                            },
                                            animationSpec = StudioMotion.microTween(),
                                            label = "RosterRowBg_${speaker.id}"
                                        )
                                        val rowBorder by animateColorAsState(
                                            targetValue = when {
                                                isSelected -> cs.primary.copy(alpha = if (isHovered) 0.58f else 0.38f)
                                                isHovered -> cs.outlineVariant
                                                else -> cs.outlineVariant.copy(alpha = 0.35f)
                                            },
                                            animationSpec = StudioMotion.microTween(),
                                            label = "RosterRowBorder_${speaker.id}"
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = rowBg,
                                            border = BorderStroke(1.dp, rowBorder),
                                            shadowElevation = 0.dp,
                                            tonalElevation = 0.dp,
                                            modifier = studioAnimateItem()
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 56.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .hoverable(rowSrc)
                                                .then(if (DesktopAdaptiveState.isDesktopPlatform) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                                                .clickable(
                                                    interactionSource = rowSrc,
                                                    indication = null
                                                ) {
                                                    if (isLocked) {
                                                        Toast.makeText(
                                                            context,
                                                            S.speaker.rosterLockedHasRecordingToast,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else if (speaker.id in selectedIds) {
                                                        selectedIds = selectedIds - speaker.id
                                                    } else {
                                                        selectedIds = selectedIds + speaker.id
                                                    }
                                                }
                                                .testTag("roster_speaker_row_${speaker.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                StudioCheckbox(
                                                    checked = isSelected,
                                                    onCheckedChange = null,
                                                    enabled = !isLocked
                                                )

                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Text(
                                                            text = speaker.name,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = cs.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (isLocked) {
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = S.speaker.rosterLockedHasRecordingToast,
                                                                tint = cs.outline,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }
                                                    }
                                                    val metaParts = buildList {
                                                        val genderDisp = S.speaker.displayGender(speaker.gender)
                                                        if (genderDisp.isNotBlank()) add(genderDisp)
                                                        if (speaker.age > 0) add(S.common.ageWithUnit.withArgs("age" to speaker.age))
                                                        if (speaker.dialect.isNotBlank()) add(speaker.dialect)
                                                        if (!isSimpleMode && speaker.groupName.isNotBlank()) add(speaker.groupName)
                                                    }
                                                    if (metaParts.isNotEmpty()) {
                                                        Text(
                                                            text = metaParts.joinToString(" · "),
                                                            fontSize = 11.5.sp,
                                                            color = cs.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                // Project recording progress badge (Saved project mode only)
                                                if (liveProject != null) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = when {
                                                            isDone -> extendedColors.successContainer
                                                            hasStarted -> cs.primaryContainer
                                                            else -> cs.surfaceContainerHigh
                                                        }
                                                    ) {
                                                        Text(
                                                            text = if (hasStarted) {
                                                                S.speaker.rosterProgressBadge.withArgs(
                                                                    "recorded" to recordedCount,
                                                                    "total" to totalWords
                                                                )
                                                            } else {
                                                                S.speaker.rosterNotStartedBadge
                                                            },
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = if (hasStarted) FontFamily.Monospace else null,
                                                            color = when {
                                                                isDone -> extendedColors.onSuccessContainer
                                                                hasStarted -> cs.onPrimaryContainer
                                                                else -> cs.onSurfaceVariant
                                                            },
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                        )
                                                    }

                                                    // 1-tap "Go Record" icon button
                                                    if (onStartRecordingWithSpeaker != null) {
                                                        val (micSrc, micHoveredState) = rememberStudioInteraction()
                                                        val micHovered by micHoveredState
                                                        val micBg by animateColorAsState(
                                                            targetValue = if (micHovered) cs.primary else cs.primaryContainer,
                                                            animationSpec = StudioMotion.microTween(),
                                                            label = "RosterMicBg_${speaker.id}"
                                                        )
                                                        val micTint by animateColorAsState(
                                                            targetValue = if (micHovered) cs.onPrimary else cs.onPrimaryContainer,
                                                            animationSpec = StudioMotion.microTween(),
                                                            label = "RosterMicTint_${speaker.id}"
                                                        )
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = micBg,
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .studioTactile(micSrc, pressScale = 0.90f)
                                                                .clip(CircleShape)
                                                                .hoverable(micSrc)
                                                                .then(if (DesktopAdaptiveState.isDesktopPlatform) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                                                                .clickable(
                                                                    interactionSource = micSrc,
                                                                    indication = null,
                                                                    enabled = !isSaving
                                                                ) {
                                                                    isSaving = true
                                                                    val finalIds = selectedIds + speaker.id + lockedRecordedIds
                                                                    viewModel.updateProjectAssignedSpeakers(
                                                                        projectId = liveProject.id,
                                                                        speakerIds = finalIds
                                                                    ) {
                                                                        isSaving = false
                                                                        onStartRecordingWithSpeaker(speaker)
                                                                    }
                                                                }
                                                                .testTag("roster_go_record_${speaker.id}")
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Mic,
                                                                    contentDescription = S.speaker.rosterGoRecordDesc,
                                                                    tint = micTint,
                                                                    modifier = Modifier.size(17.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                PlatformVerticalScrollbar(
                                    listState = listState,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }

                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                        // 6. Responsive Bottom Action Bar (adapts cleanly down to 360dp phones)
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val isCompactBar = maxWidth < 385.dp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        resetCreateForm()
                                        isCreatingNewSpeaker = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = if (isCompactBar) 10.dp else 13.dp,
                                        vertical = 8.dp
                                    ),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 38.dp)
                                        .testTag("roster_open_create_speaker_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isCompactBar) {
                                            S.speaker.rosterAddNewShortButton
                                        } else {
                                            S.speaker.rosterAddNewAndJoinButton
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(if (isCompactBar) 4.dp else 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = onDismiss,
                                        contentPadding = PaddingValues(
                                            horizontal = if (isCompactBar) 8.dp else 12.dp,
                                            vertical = 8.dp
                                        ),
                                        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 38.dp)
                                    ) {
                                        Text(
                                            text = S.common.cancel,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (selectedIds.isEmpty()) {
                                                Toast.makeText(
                                                    context,
                                                    S.speaker.rosterAtLeastOneHint,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@Button
                                            }
                                            val finalIds = selectedIds + lockedRecordedIds
                                            if (liveProject != null) {
                                                isSaving = true
                                                viewModel.updateProjectAssignedSpeakers(
                                                    projectId = liveProject.id,
                                                    speakerIds = finalIds
                                                ) {
                                                    isSaving = false
                                                    Toast.makeText(
                                                        context,
                                                        S.speaker.rosterSavedToast.withArgs("count" to finalIds.size),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    onDismiss()
                                                }
                                            } else {
                                                onDraftConfirm?.invoke(finalIds)
                                                onDismiss()
                                            }
                                        },
                                        enabled = selectedIds.isNotEmpty() && !isSaving,
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = if (isCompactBar) 14.dp else 18.dp,
                                            vertical = 8.dp
                                        ),
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 1.dp, minHeight = 38.dp)
                                            .testTag("roster_save_button")
                                    ) {
                                        Text(
                                            text = S.common.save,
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
                } else {
                    // 5. Inline "Create New Speaker & Join" Sub-view
                    val cs = MaterialTheme.colorScheme
                    val formScrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = { isCreatingNewSpeaker = false },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = S.speaker.rosterBackToListButton,
                                        tint = cs.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = S.speaker.rosterCreateSubTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cs.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = S.common.close,
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(formScrollState)
                                    .padding(end = if (DesktopAdaptiveState.isDesktopPlatform) 10.dp else 0.dp),
                                verticalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text(S.speaker.nameLabel) },
                                    placeholder = { Text(S.speaker.namePlaceholder) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("roster_create_speaker_name")
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = S.speaker.genderLabel,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    presetGenders.forEach { g ->
                                        StudioFilterChip(
                                            selected = newGender == g,
                                            onClick = { newGender = g },
                                            labelText = S.speaker.displayGender(g)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newDialect,
                                        onValueChange = { newDialect = it },
                                        label = { Text(S.speaker.dialectLabel) },
                                        placeholder = { Text(S.speaker.dialectPlaceholder) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = newAgeText,
                                        onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) newAgeText = it },
                                        label = { Text(S.speaker.ageLabel, maxLines = 1, softWrap = false) },
                                        placeholder = { Text("25") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.width(96.dp)
                                    )
                                }

                                if (!isSimpleMode) {
                                    MultiSelectSpeakerGroupField(
                                        groupField = newGroupName,
                                        onGroupFieldChange = { newGroupName = it },
                                        availableGroups = speakerGroups
                                    )
                                }

                                val isDiverseGender = newGender == "多元/不限"
                                OutlinedTextField(
                                    value = newNotes,
                                    onValueChange = { newNotes = it },
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
                                    maxLines = 3,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            PlatformVerticalScrollbar(
                                scrollState = formScrollState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }

                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { isCreatingNewSpeaker = false },
                                enabled = !isCreatingSubmitting
                            ) {
                                Text(
                                    text = S.speaker.rosterBackToListButton,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val cleanName = newName.trim()
                                    if (cleanName.isEmpty()) return@Button
                                    isCreatingSubmitting = true
                                    val ageVal = newAgeText.toIntOrNull()?.coerceIn(1, 120) ?: 25
                                    val resolvedDialect = newDialect.trim().ifBlank { S.speaker.defaultDialectMandarin }
                                    val currentSelectedSnapshot = selectedIds + lockedRecordedIds
                                    viewModel.createSpeakerAndAssignToProject(
                                        projectId = liveProject?.id ?: 0L,
                                        name = cleanName,
                                        gender = newGender,
                                        age = ageVal,
                                        dialect = resolvedDialect,
                                        notes = newNotes.trim(),
                                        groupName = if (isSimpleMode) "" else newGroupName.trim(),
                                        existingVisibleSpeakerIds = currentSelectedSnapshot
                                    ) { createdSpeaker ->
                                        isCreatingSubmitting = false
                                        selectedIds = currentSelectedSnapshot + createdSpeaker.id
                                        isCreatingNewSpeaker = false
                                        Toast.makeText(
                                            context,
                                            S.speaker.rosterCreatedAndJoinedToast.withArgs("name" to createdSpeaker.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                enabled = newName.isNotBlank() && !isCreatingSubmitting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("roster_create_speaker_confirm")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = S.speaker.rosterCreateAndJoinConfirm,
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
