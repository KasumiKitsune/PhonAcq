package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AcousticAnalyzer
import com.example.data.Speaker
import com.example.data.Word
import com.example.data.belongsToGroup
import com.example.data.groupList
import com.example.data.parseSpeakerGroups
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.consumeDialogCardTaps
import com.example.ui.theme.formatQuickNoteSymbolForDisplay
import com.example.viewmodel.RecordingViewModel
import java.io.File
import java.util.Locale

internal fun insertSymbolAtFieldCursor(tfv: TextFieldValue, rawSymbol: String): TextFieldValue {
    val cleanSym = if (rawSymbol.startsWith("◌") && rawSymbol.length > 1) {
        rawSymbol.removePrefix("◌")
    } else {
        rawSymbol
    }
    val text = tfv.text
    val minSel = tfv.selection.min.coerceIn(0, text.length)
    val maxSel = tfv.selection.max.coerceIn(minSel, text.length)
    val newText = buildString {
        append(text.substring(0, minSel))
        append(cleanSym)
        append(text.substring(maxSel))
    }
    val nextCursor = (minSel + cleanSym.length).coerceIn(0, newText.length)
    return tfv.copy(text = newText, selection = TextRange(nextCursor))
}

internal fun hasQuickNoteTag(text: String, tag: String): Boolean {
    val cleanTag = tag.trim()
    if (cleanTag.isEmpty() || text.isBlank()) return false
    val bracketed = "[$cleanTag]"
    if (text.contains(bracketed)) return true
    return text.split("·").any { it.trim() == cleanTag }
}

internal fun toggleQuickNoteTagInValue(tfv: TextFieldValue, tag: String): TextFieldValue {
    val cleanTag = tag.trim()
    if (cleanTag.isEmpty()) return tfv
    val current = tfv.text
    val bracketed = "[$cleanTag]"
    val updated = if (current.contains(bracketed)) {
        current.replace(bracketed, "").replace("  ", " ").trim()
    } else {
        val parts = current.split("·").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.any { it == cleanTag }) {
            parts.filterNot { it == cleanTag }.joinToString(" · ")
        } else if (current.isBlank()) {
            cleanTag
        } else {
            "${current.trimEnd()} · $cleanTag"
        }
    }
    return TextFieldValue(text = updated, selection = TextRange(updated.length))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickRemarkEditorDialog(
    word: Word,
    speakerName: String = S.recording.currentSpeakerFallback,
    toneSymbols: List<String>,
    ipaInitials: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_INITIALS,
    ipaFinals: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_FINALS,
    fieldTags: List<String>,
    isSimpleMode: Boolean = false,
    onCustomizeItems: (() -> Unit)? = null,
    onDeriveVariant: ((suffix: String, currentNote: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var speakerNoteField by remember(word.id, word.speakerNote) {
        mutableStateOf(TextFieldValue(text = word.speakerNote, selection = TextRange(word.speakerNote.length)))
    }
    var wordRemarkText by remember(word.id, word.remark) { mutableStateOf(word.remark) }
    var editingSharedPrompt by remember(word.id) { mutableStateOf(false) }
    var showDeriveVariantPanel by remember(word.id) { mutableStateOf(false) }
    var variantSuffixInput by remember(word.id) { mutableStateOf("白读") }
    var selectedSymbolTab by remember { mutableStateOf(0) }

    val simpleBasicTags = S.recording.quickNoteDialogTagsFallback

    com.example.ui.components.StudioAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.recording.quickNoteDialogTitle.withArgs("word" to word.text),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!isSimpleMode && onCustomizeItems != null) {
                    TextButton(
                        onClick = onCustomizeItems,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(S.common.custom, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        text = {
            if (isSimpleMode) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = speakerNoteField,
                        onValueChange = { speakerNoteField = it },
                        label = { Text(S.recording.noteFieldLabel) },
                        placeholder = { Text(S.recording.noteFieldPlaceholder) },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_remark_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        simpleBasicTags.forEach { tag ->
                            val isSelected = hasQuickNoteTag(speakerNoteField.text, tag)
                            StudioFilterChip(
                                selected = isSelected,
                                onClick = {
                                    speakerNoteField = toggleQuickNoteTagInValue(speakerNoteField, tag)
                                },
                                labelText = tag
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = speakerNoteField,
                        onValueChange = { speakerNoteField = it },
                        label = { Text(S.recording.speakerNoteFieldLabel.withArgs("speaker" to speakerName)) },
                        placeholder = { Text(S.recording.speakerNoteFieldPlaceholder) },
                        singleLine = false,
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_remark_input")
                    )

                    val tabTitles = S.recording.quickNoteTabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tabTitles.forEachIndexed { idx, tabLabel ->
                            StudioFilterChip(
                                selected = selectedSymbolTab == idx,
                                onClick = { selectedSymbolTab = idx },
                                labelText = tabLabel
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (selectedSymbolTab) {
                            0 -> toneSymbols.forEach { sym ->
                                val displaySym = formatQuickNoteSymbolForDisplay(sym)
                                StudioFilterChip(
                                    selected = false,
                                    onClick = {
                                        speakerNoteField = insertSymbolAtFieldCursor(speakerNoteField, sym)
                                    },
                                    labelText = displaySym
                                )
                            }
                            1 -> ipaInitials.forEach { sym ->
                                val displaySym = formatQuickNoteSymbolForDisplay(sym)
                                StudioFilterChip(
                                    selected = false,
                                    onClick = {
                                        speakerNoteField = insertSymbolAtFieldCursor(speakerNoteField, sym)
                                    },
                                    labelText = displaySym
                                )
                            }
                            2 -> ipaFinals.forEach { sym ->
                                val displaySym = formatQuickNoteSymbolForDisplay(sym)
                                StudioFilterChip(
                                    selected = false,
                                    onClick = {
                                        speakerNoteField = insertSymbolAtFieldCursor(speakerNoteField, sym)
                                    },
                                    labelText = displaySym
                                )
                            }
                            else -> fieldTags.forEach { tag ->
                                val isSelected = hasQuickNoteTag(speakerNoteField.text, tag)
                                StudioFilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        speakerNoteField = toggleQuickNoteTagInValue(speakerNoteField, tag)
                                    },
                                    labelText = tag
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    if (!editingSharedPrompt) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.recording.sharedPromptLabel.withArgs(
                                    "prompt" to wordRemarkText.ifBlank { S.common.none }
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (onDeriveVariant != null) {
                                    TextButton(
                                        onClick = { showDeriveVariantPanel = !showDeriveVariantPanel },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("derive_variant_toggle_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(2.dp))
                                        Text(S.recording.deriveVariantButton, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                TextButton(
                                    onClick = { editingSharedPrompt = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(S.recording.editPromptButton, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = wordRemarkText,
                            onValueChange = { wordRemarkText = it },
                            label = { Text(S.recording.sharedPromptFieldLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    AnimatedVisibility(visible = showDeriveVariantPanel && onDeriveVariant != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = S.recording.deriveVariantSuffixLabel,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    S.recording.deriveVariantSuffixPresets.forEach { preset ->
                                        StudioFilterChip(
                                            selected = variantSuffixInput == preset,
                                            onClick = { variantSuffixInput = preset },
                                            labelText = preset
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = variantSuffixInput,
                                        onValueChange = { variantSuffixInput = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            onDeriveVariant?.invoke(
                                                variantSuffixInput.trim().ifBlank { "异读" },
                                                speakerNoteField.text
                                            )
                                        },
                                        enabled = variantSuffixInput.isNotBlank(),
                                        modifier = Modifier.testTag("confirm_derive_variant_button")
                                    ) {
                                        Text(S.recording.deriveVariantShortBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(speakerNoteField.text, wordRemarkText) },
                modifier = Modifier.testTag("save_quick_remark_button")
            ) {
                Text(S.common.save)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (speakerNoteField.text.isNotEmpty()) {
                    TextButton(onClick = { speakerNoteField = TextFieldValue("") }) {
                        Text(S.common.clear)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(S.common.cancel)
                }
            }
        }
    )
}

@Composable
internal fun MultiTakeSwitcherDialog(
    word: Word,
    takes: List<File>,
    playingWordId: Long?,
    onPlayTake: (File, Int) -> Unit,
    onSelectTake: (File) -> Unit,
    onDeriveTakeAsVariant: ((File) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    com.example.ui.components.StudioAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                text = S.recording.takeSwitcherTitle.withArgs("word" to word.text),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = S.recording.takeSwitcherDescription.withArgs("count" to takes.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                takes.forEachIndexed { idx, file ->
                    val isCurrentActive = file.name == word.recordingFile
                    val virtualPlayId = -(word.id * 100 + idx + 1)
                    val isPlayingThis = playingWordId == virtualPlayId
                    val durSec = remember(file.name, file.length()) {
                        AcousticAnalyzer.getExactWavDurationSec(file)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrentActive) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (isCurrentActive) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (idx == 0) {
                                            S.recording.latestTakeTitle.withArgs("count" to takes.size)
                                        } else {
                                            S.recording.historyTakeTitle.withArgs("num" to (takes.size - idx))
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (isCurrentActive) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = S.recording.currentTakeBadge,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = S.recording.takeMetaLine.withArgs(
                                        "sec" to String.format(Locale.US, "%.2f", durSec),
                                        "kb" to (file.length() / 1024L).coerceAtLeast(1L)
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalIconButton(
                                    onClick = { onPlayTake(file, idx) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = S.recording.listenTakeDesc,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (onDeriveTakeAsVariant != null) {
                                    OutlinedButton(
                                        onClick = { onDeriveTakeAsVariant(file) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                    ) {
                                        Text(S.recording.deriveVariantShortBtn, fontSize = 11.5.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                                if (!isCurrentActive) {
                                    Button(
                                        onClick = { onSelectTake(file) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                    ) {
                                        Text(S.recording.selectThisTakeButton, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(S.common.close)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeakerSelectionAndManagementDialog(
    speakers: List<Speaker>,
    activeSpeaker: Speaker?,
    projectGroupName: String = "",
    speakerGroups: List<String> = emptyList(),
    speakerProgressMap: Map<Long, com.example.data.SpeakerProgressInfo> = emptyMap(),
    totalWords: Int = 0,
    viewModel: RecordingViewModel? = null,
    onSelectSpeaker: (Speaker) -> Unit,
    onAddSpeaker: (String, String, Int, String, String, String) -> Unit,
    onUpdateSpeaker: (Speaker) -> Unit,
    onDeleteSpeaker: (Speaker) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isSimpleMode = if (viewModel != null) {
        viewModel.isSimpleMode.collectAsState().value
    } else {
        false
    }
    var editingSpeaker by remember { mutableStateOf<Speaker?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var pitchRefreshTick by remember { mutableStateOf(0) }
    var estimatingSpeakerId by remember { mutableStateOf<Long?>(null) }
    var speakerForManualPitch by remember { mutableStateOf<Speaker?>(null) }

    val defaultProjGroup = remember(projectGroupName) {
        parseSpeakerGroups(projectGroupName).firstOrNull() ?: ""
    }
    val hasMatchingProjectGroup = remember(speakers, defaultProjGroup) {
        defaultProjGroup.isNotBlank() && speakers.any { it.belongsToGroup(defaultProjGroup) }
    }
    var selectedFilterGroup by remember(defaultProjGroup, hasMatchingProjectGroup) {
        mutableStateOf(if (hasMatchingProjectGroup) defaultProjGroup else "ALL")
    }

    val presetGenders = remember { listOf("男", "女", "多元/不限") }
    var name by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf(if (isSimpleMode) "" else projectGroupName) }
    var notes by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("25") }
    var gender by remember { mutableStateOf("男") }
    var dialect by remember { mutableStateOf("普通话") }

    val effectiveFilterGroup = if (isSimpleMode) "ALL" else selectedFilterGroup
    val displayedSpeakers = remember(speakers, effectiveFilterGroup) {
        if (effectiveFilterGroup == "ALL") {
            speakers
        } else {
            speakers.filter { it.belongsToGroup(effectiveFilterGroup) }
        }
    }

    val hasActiveSpeakerFormInput = isCreatingNew || editingSpeaker != null
    com.example.ui.components.StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnClickOutside = !hasActiveSpeakerFormInput
        )
    ) {
        com.example.ui.components.StudioDialogEnterAnimation {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .consumeDialogCardTaps()
                    .testTag("speaker_manager_dialog")
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCreatingNew) {
                                    S.recording.switchSpeakerModalNewTitle
                                } else if (editingSpeaker != null) {
                                    S.recording.switchSpeakerModalEditTitle
                                } else {
                                    S.recording.switchSpeakerModalSelectTitle
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (!isSimpleMode && !isCreatingNew && editingSpeaker == null && projectGroupName.isNotBlank()) {
                                Text(
                                    text = S.recording.currentProjectGroupPrefix.withArgs("group" to projectGroupName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (!isCreatingNew && editingSpeaker == null) {
                            FilledTonalButton(
                                onClick = {
                                    isCreatingNew = true
                                    name = ""
                                    groupName = if (isSimpleMode) "" else if (selectedFilterGroup != "ALL") selectedFilterGroup else projectGroupName
                                    notes = ""
                                    ageText = "30"
                                    gender = "男"
                                    dialect = ""
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(S.speaker.addSpeakerButton, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    var speakerPendingDelete by remember { mutableStateOf<Speaker?>(null) }
                    val currentEditing = editingSpeaker
                    if (isCreatingNew || currentEditing != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(S.recording.speakerNameReqLabel) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!isSimpleMode) {
                                MultiSelectSpeakerGroupField(
                                    groupField = groupName,
                                    onGroupFieldChange = { groupName = it },
                                    availableGroups = speakerGroups
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = dialect,
                                    onValueChange = { dialect = it },
                                    label = { Text(S.speaker.dialectLabel) },
                                    placeholder = { Text(S.speaker.dialectPlaceholder) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = ageText,
                                    onValueChange = { ageText = it.filter { c -> c.isDigit() } },
                                    label = { Text(S.speaker.ageLabel, maxLines = 1, softWrap = false) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.width(104.dp)
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = S.speaker.genderLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                presetGenders.forEach { g ->
                                    StudioFilterChip(
                                        selected = gender == g,
                                        onClick = { gender = g },
                                        labelText = S.speaker.displayGender(g)
                                    )
                                }
                            }
                            val isDiverseGender = gender == "多元/不限"
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
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
                                maxLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        isCreatingNew = false
                                        editingSpeaker = null
                                    }
                                ) {
                                    Text(S.recording.backToListButton)
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val age = ageText.toIntOrNull() ?: 25
                                        if (isCreatingNew && name.isNotBlank()) {
                                            onAddSpeaker(name.trim(), gender, age, dialect.trim().ifBlank { S.speaker.defaultDialectMandarin }, notes.trim(), groupName.trim())
                                        } else if (currentEditing != null && name.isNotBlank()) {
                                            onUpdateSpeaker(
                                                currentEditing.copy(
                                                    name = name.trim(),
                                                    gender = gender,
                                                    age = age,
                                                    dialect = dialect.trim().ifBlank { S.speaker.defaultDialectMandarin },
                                                    notes = notes.trim(),
                                                    groupName = groupName.trim()
                                                )
                                            )
                                        }
                                        isCreatingNew = false
                                        editingSpeaker = null
                                    },
                                    enabled = name.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(S.common.save)
                                }
                            }
                        }
                    } else {
                        // Group filter chips inside dialog
                        if (!isSimpleMode && speakerGroups.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                item {
                                    StudioFilterChip(
                                        selected = selectedFilterGroup == "ALL",
                                        onClick = { selectedFilterGroup = "ALL" },
                                        labelText = S.recording.allSpeakersChip.withArgs("count" to speakers.size)
                                    )
                                }
                                items(speakerGroups.size) { idx ->
                                    val grp = speakerGroups[idx]
                                    val count = speakers.count { it.belongsToGroup(grp) }
                                    val isProjectGrp = parseSpeakerGroups(projectGroupName).any { it.equals(grp, ignoreCase = true) }
                                    StudioFilterChip(
                                        selected = selectedFilterGroup == grp,
                                        onClick = { selectedFilterGroup = grp },
                                        labelText = if (isProjectGrp) {
                                            S.recording.projectGroupChip.withArgs("group" to grp, "count" to count)
                                        } else {
                                            S.recording.groupChip.withArgs("group" to grp, "count" to count)
                                        }
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(displayedSpeakers, key = { _, s -> s.id }) { _, speaker ->
                                val isSelected = speaker.id == activeSpeaker?.id
                                val progInfo = speakerProgressMap[speaker.id]
                                val recCount = progInfo?.recordedWords ?: 0
                                val isDone = totalWords > 0 && recCount >= totalWords

                                val cardBg = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = cardBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectSpeaker(speaker) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = speaker.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (totalWords > 0) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = if (isDone) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                                    ) {
                                                        Text(
                                                            text = if (isDone) {
                                                                S.recording.recordedFullProgress.withArgs("rec" to recCount, "total" to totalWords)
                                                            } else {
                                                                S.recording.recordedPartialProgress.withArgs("rec" to recCount, "total" to totalWords)
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            color = if (isDone) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val genderPrefix = if (speaker.gender.isBlank() || speaker.gender == "不愿透露") {
                                                    ""
                                                } else {
                                                    S.recording.speakerGenderAgeDot.withArgs(
                                                        "gender" to S.speaker.displayGender(speaker.gender)
                                                    )
                                                }
                                                Text(
                                                    text = S.recording.speakerGenderAgeWithDialect.withArgs(
                                                        "genderPrefix" to genderPrefix,
                                                        "age" to speaker.age,
                                                        "dialect" to speaker.dialect.ifBlank { S.recording.unsetDialect }
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.align(Alignment.CenterVertically)
                                                )
                                                if (!isSimpleMode) {
                                                    speaker.groupList.forEach { grp ->
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                                            modifier = Modifier.align(Alignment.CenterVertically)
                                                        ) {
                                                            Text(
                                                                text = grp,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                modifier = Modifier
                                                                    .widthIn(max = 110.dp)
                                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (!isSimpleMode && viewModel != null) {
                                                val pitchRange = remember(speaker.id, pitchRefreshTick) {
                                                    viewModel.getSpeakerPitchRange(speaker.id)
                                                }
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (pitchRange != null) {
                                                            S.recording.calibratedPitchRangeLine.withArgs(
                                                                "min" to pitchRange.first.toInt(),
                                                                "max" to pitchRange.second.toInt()
                                                            )
                                                        } else {
                                                            S.recording.defaultPitchRangeLine.withArgs(
                                                                "range" to if (speaker.gender == "女") S.recording.defaultPitchFemaleRange else S.recording.defaultPitchMaleRange
                                                            )
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (pitchRange != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.align(Alignment.CenterVertically)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                                        modifier = Modifier
                                                            .align(Alignment.CenterVertically)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .clickable(enabled = estimatingSpeakerId != speaker.id) {
                                                                estimatingSpeakerId = speaker.id
                                                                viewModel.estimatePitchRangeForSpeaker(speaker) { res ->
                                                                    estimatingSpeakerId = null
                                                                    if (res != null) {
                                                                        viewModel.saveSpeakerPitchRange(speaker.id, res.first, res.second)
                                                                        pitchRefreshTick++
                                                                        Toast.makeText(
                                                                            context,
                                                                            S.recording.calibratedSpeakerToast.withArgs(
                                                                                "name" to speaker.name,
                                                                                "min" to res.first.toInt(),
                                                                                "max" to res.second.toInt()
                                                                            ),
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    } else {
                                                                        Toast.makeText(
                                                                            context,
                                                                            S.recording.insufficientRecordingsToast,
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }
                                                                }
                                                            }
                                                    ) {
                                                        Text(
                                                            text = if (estimatingSpeakerId == speaker.id) {
                                                                S.recording.calculatingShort
                                                            } else if (pitchRange != null) {
                                                                S.recording.recalculatePitchButton
                                                            } else {
                                                                S.recording.autoCalculatePitchButton
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        modifier = Modifier
                                                            .align(Alignment.CenterVertically)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .clickable { speakerForManualPitch = speaker }
                                                            .testTag("manual_pitch_range_button_${speaker.id}")
                                                    ) {
                                                        Text(
                                                            text = S.recording.manualPitchButton,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingSpeaker = speaker
                                                    name = speaker.name
                                                    groupName = speaker.groupName
                                                    notes = speaker.notes
                                                    ageText = speaker.age.toString()
                                                    gender = if (speaker.gender in presetGenders) speaker.gender else "多元/不限"
                                                    dialect = speaker.dialect
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = S.common.edit, modifier = Modifier.size(18.dp))
                                            }
                                            if (speakers.size > 1) {
                                                IconButton(onClick = { speakerPendingDelete = speaker }) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = S.common.delete,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(S.common.close, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    com.example.ui.components.AnimatedNullableDialogVisibility(value = speakerPendingDelete) { pending ->
                        com.example.ui.components.StudioAlertDialog(
                            onDismissRequest = { speakerPendingDelete = null },
                            icon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            title = { Text(S.speaker.deleteSpeakerTitle, fontWeight = FontWeight.Bold) },
                            text = {
                                Text(S.recording.deleteSpeakerModalBody.withArgs("name" to pending.name))
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        onDeleteSpeaker(pending)
                                        speakerPendingDelete = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(S.project.confirmDelete, maxLines = 1, softWrap = false)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { speakerPendingDelete = null }) {
                                    Text(S.common.cancel, maxLines = 1, softWrap = false)
                                }
                            }
                        )
                    }
                    com.example.ui.components.AnimatedNullableDialogVisibility(value = speakerForManualPitch) { targetSpk ->
                        if (viewModel != null) {
                            ManualSpeakerPitchDialog(
                                speaker = targetSpk,
                                currentRange = viewModel.getSpeakerPitchRange(targetSpk.id),
                                onSave = { minHz, maxHz ->
                                    viewModel.saveSpeakerPitchRange(targetSpk.id, minHz, maxHz)
                                    pitchRefreshTick++
                                    speakerForManualPitch = null
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
                                    viewModel.clearSpeakerPitchRange(targetSpk.id)
                                    pitchRefreshTick++
                                    speakerForManualPitch = null
                                    Toast.makeText(context, S.recording.manualPitchResetToast, Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = { speakerForManualPitch = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManualSpeakerPitchDialog(
    speaker: Speaker,
    currentRange: Pair<Float, Float>?,
    onSave: (Float, Float) -> Unit,
    onResetAuto: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val defaultMin = currentRange?.first?.toInt() ?: if (speaker.gender == "女") 145 else 80
    val defaultMax = currentRange?.second?.toInt() ?: if (speaker.gender == "女") 330 else 210
    var minText by remember(speaker.id, currentRange) { mutableStateOf(defaultMin.toString()) }
    var maxText by remember(speaker.id, currentRange) { mutableStateOf(defaultMax.toString()) }

    com.example.ui.components.StudioAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                text = S.recording.manualPitchDialogTitle.withArgs("name" to speaker.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = S.recording.manualPitchDialogHint,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) minText = it },
                        label = { Text(S.recording.manualPitchMinLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_pitch_min_input")
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) maxText = it },
                        label = { Text(S.recording.manualPitchMaxLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_pitch_max_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minHz = minText.toFloatOrNull()
                    val maxHz = maxText.toFloatOrNull()
                    if (minHz != null && maxHz != null && minHz in 45f..480f && maxHz in 65f..520f && (maxHz - minHz) >= 20f) {
                        onSave(minHz, maxHz)
                    } else {
                        Toast.makeText(context, S.recording.manualPitchInvalidToast, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("manual_pitch_save_button")
            ) {
                Text(S.common.save)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (currentRange != null) {
                    TextButton(
                        onClick = onResetAuto,
                        modifier = Modifier.testTag("manual_pitch_reset_button")
                    ) {
                        Text(S.recording.manualPitchResetAutoBtn)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(S.common.cancel)
                }
            }
        }
    )
}
