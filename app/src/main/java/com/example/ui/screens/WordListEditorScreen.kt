package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import com.example.data.EditableWordDraft
import com.example.data.ParsedWord
import com.example.data.Project
import com.example.data.assignedSpeakerIdSet
import com.example.data.formatAssignedSpeakerIds
import com.example.data.isSpeakerCandidate
import com.example.data.parseAssignedSpeakerIds
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.LocalAppWindowMetrics
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioHorizontalLazyStrip
import com.example.ui.components.StudioMotion
import com.example.ui.components.StudioSegmentedControl
import com.example.ui.components.StudioSwitch
import com.example.ui.components.ZoomableWordImage
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.ui.components.studioRotateOn
import com.example.utils.SpreadsheetWordImporter
import com.example.utils.WordImageManager
import com.example.utils.rememberWordImageBitmap
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private val nextWordEditorLocalKeyId = AtomicLong(1L)

@Immutable
data class WordEditorUiItem(
    val localKey: String = "draft_${nextWordEditorLocalKeyId.getAndIncrement()}",
    val existingWordId: Long? = null,
    val text: String,
    val remark: String = "",
    val groupName: String = "",
    val imagePath: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordListEditorScreen(
    viewModel: RecordingViewModel,
    initialProject: Project? = null,
    onBack: () -> Unit,
    onSavedAndOpenRecording: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allProjects by viewModel.allProjects.collectAsState()
    val allSpeakers by viewModel.allSpeakers.collectAsState()
    val summariesMap by viewModel.projectSummaries.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()

    // null = Create New Project; non-null = Edit Existing Project
    var targetProject by remember(initialProject?.id) { mutableStateOf(initialProject) }
    val liveTargetProject = remember(targetProject, allProjects) {
        targetProject?.let { tp -> allProjects.firstOrNull { it.id == tp.id } ?: tp }
    }

    var projectName by remember { mutableStateOf(initialProject?.name ?: "") }
    var projectDesc by remember { mutableStateOf(initialProject?.description ?: "") }
    var speakerGroupName by remember { mutableStateOf(initialProject?.speakerGroupName ?: "") }
    var enablePicturePrompts by remember { mutableStateOf(initialProject?.enablePicturePrompts ?: false) }
    var draftAssignedSpeakerIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var hasEditedDraftSpeakers by remember { mutableStateOf(false) }
    var showRosterDialog by remember { mutableStateOf(false) }

    val draftItems = remember { mutableStateListOf<WordEditorUiItem>() }
    var isLoadingProjectWords by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    // 0 = 列表编辑, 1 = TXT 文本粘贴/批量编辑
    var editTab by remember { mutableIntStateOf(0) }
    var rawTxtContent by remember { mutableStateOf("") }
    var rawTxtDirty by remember { mutableStateOf(false) }

    var showProjectInfoExpanded by remember { mutableStateOf(initialProject == null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf("ALL") }

    // Dialog states
    var showTemplateDialog by remember { mutableStateOf(false) }
    var pendingImportParsedWords by remember { mutableStateOf<List<ParsedWord>?>(null) }
    var pendingTemplateMeta by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddOrEditWordDialog by remember { mutableStateOf(false) }
    var editingWordIndex by remember { mutableStateOf<Int?>(null) }
    var showFormatHelpDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var pendingSwitchTargetProject by remember { mutableStateOf<Pair< Boolean, Project? >?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Picture Wordlist states
    var activeImageMenuRowIndex by remember { mutableStateOf<Int?>(null) }
    var previewImageTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingSingleImageCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var pendingCameraTempFile by remember { mutableStateOf<File?>(null) }
    var isBatchImportingImages by remember { mutableStateOf(false) }

    fun buildTxtFromCurrentDrafts(): String {
        return viewModel.formatDraftsToTxt(
            draftItems.map {
                EditableWordDraft(
                    existingWordId = it.existingWordId,
                    text = it.text,
                    remark = it.remark,
                    groupName = it.groupName,
                    imagePath = it.imagePath
                )
            }
        )
    }

    // Merge parsed TXT back into draftItems while preserving existingWordId for matching words (O(N) lookup)
    fun syncTxtToDrafts(txt: String) {
        val parsed = viewModel.parseImportTextToParsedWords(txt)
        val oldList = draftItems.toList()
        val claimedKeys = HashSet<String>(oldList.size * 2)
        val byGroupAndText = HashMap<Pair<String, String>, ArrayDeque<WordEditorUiItem>>(oldList.size)
        val byTextOnly = HashMap<String, ArrayDeque<WordEditorUiItem>>(oldList.size)

        for (old in oldList) {
            val gKey = old.groupName.trim() to old.text.trim()
            byGroupAndText.getOrPut(gKey) { ArrayDeque() }.addLast(old)
            byTextOnly.getOrPut(old.text.trim()) { ArrayDeque() }.addLast(old)
        }

        fun pollUnclaimed(queue: ArrayDeque<WordEditorUiItem>?): WordEditorUiItem? {
            if (queue == null) return null
            while (queue.isNotEmpty()) {
                val candidate = queue.removeFirst()
                if (claimedKeys.add(candidate.localKey)) {
                    return candidate
                }
            }
            return null
        }

        val newItems = parsed.map { pw ->
            val cleanText = pw.text.trim()
            val cleanRemark = pw.remark.trim()
            val cleanGroup = pw.groupName.trim()

            // First try matching same text & same group, then fallback to same text
            val matchedOld = pollUnclaimed(byGroupAndText[cleanGroup to cleanText])
                ?: pollUnclaimed(byTextOnly[cleanText])

            if (matchedOld != null) {
                WordEditorUiItem(
                    localKey = matchedOld.localKey,
                    existingWordId = matchedOld.existingWordId,
                    text = cleanText,
                    remark = cleanRemark,
                    groupName = cleanGroup,
                    imagePath = pw.imagePath.ifBlank { matchedOld.imagePath }
                )
            } else {
                WordEditorUiItem(
                    existingWordId = null,
                    text = cleanText,
                    remark = cleanRemark,
                    groupName = cleanGroup,
                    imagePath = pw.imagePath
                )
            }
        }

        draftItems.clear()
        draftItems.addAll(newItems)
        rawTxtDirty = false
    }

    fun loadTargetProject(proj: Project?) {
        targetProject = proj
        searchQuery = ""
        selectedGroupFilter = "ALL"
        if (proj == null) {
            projectName = ""
            projectDesc = ""
            speakerGroupName = ""
            enablePicturePrompts = false
            draftAssignedSpeakerIds = emptySet()
            hasEditedDraftSpeakers = false
            draftItems.clear()
            rawTxtContent = ""
            rawTxtDirty = false
            hasUnsavedChanges = false
            showProjectInfoExpanded = true
        } else {
            projectName = proj.name
            projectDesc = proj.description
            speakerGroupName = proj.speakerGroupName
            enablePicturePrompts = proj.enablePicturePrompts
            draftAssignedSpeakerIds = parseAssignedSpeakerIds(proj.assignedSpeakerIds)
            hasEditedDraftSpeakers = false
            showProjectInfoExpanded = false
            isLoadingProjectWords = true
            scope.launch {
                val mappedItems = withContext(Dispatchers.IO) {
                    val list = viewModel.getWordsForProjectFlow(proj.id).first()
                    list.map { w ->
                        WordEditorUiItem(
                            localKey = "existing_${w.id}",
                            existingWordId = w.id,
                            text = w.text,
                            remark = w.remark,
                            groupName = w.groupName,
                            imagePath = w.imagePath
                        )
                    }
                }
                if (isLoadingProjectWords && targetProject?.id == proj.id) {
                    draftItems.clear()
                    draftItems.addAll(mappedItems)
                    if (editTab == 1) {
                        rawTxtContent = buildTxtFromCurrentDrafts()
                    } else {
                        rawTxtContent = ""
                    }
                    rawTxtDirty = false
                    hasUnsavedChanges = false
                    isLoadingProjectWords = false
                }
            }
        }
    }

    LaunchedEffect(initialProject?.id) {
        loadTargetProject(initialProject)
    }

    fun applyParsedWords(parsed: List<ParsedWord>, replace: Boolean, templateMeta: Pair<String, String>? = null) {
        if (editTab == 1 && rawTxtDirty) {
            syncTxtToDrafts(rawTxtContent)
        }
        if (replace) {
            val oldList = draftItems.toList()
            val byTextOnly = HashMap<String, ArrayDeque<WordEditorUiItem>>(oldList.size)
            for (old in oldList) {
                byTextOnly.getOrPut(old.text.trim()) { ArrayDeque() }.addLast(old)
            }
            val newItems = parsed.map { pw ->
                val cleanText = pw.text.trim()
                val matchedOld = byTextOnly[cleanText]?.let { q -> if (q.isNotEmpty()) q.removeFirst() else null }
                if (matchedOld != null) {
                    WordEditorUiItem(
                        localKey = matchedOld.localKey,
                        existingWordId = matchedOld.existingWordId,
                        text = cleanText,
                        remark = pw.remark.trim(),
                        groupName = pw.groupName.trim(),
                        imagePath = pw.imagePath.ifBlank { matchedOld.imagePath }
                    )
                } else {
                    WordEditorUiItem(
                        existingWordId = null,
                        text = cleanText,
                        remark = pw.remark.trim(),
                        groupName = pw.groupName.trim(),
                        imagePath = pw.imagePath
                    )
                }
            }
            draftItems.clear()
            draftItems.addAll(newItems)
        } else {
            draftItems.addAll(
                parsed.map { pw ->
                    WordEditorUiItem(
                        existingWordId = null,
                        text = pw.text.trim(),
                        remark = pw.remark.trim(),
                        groupName = pw.groupName.trim(),
                        imagePath = pw.imagePath
                    )
                }
            )
        }

        if (templateMeta != null) {
            if (projectName.isBlank()) projectName = templateMeta.first
            if (projectDesc.isBlank()) projectDesc = templateMeta.second
        }

        if (draftItems.isNotEmpty() && projectName.isNotBlank()) {
            showProjectInfoExpanded = false
        }

        if (editTab == 1) {
            rawTxtContent = buildTxtFromCurrentDrafts()
        } else {
            rawTxtContent = ""
        }
        rawTxtDirty = false
        hasUnsavedChanges = true
    }

    fun handlePasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
        if (clipText.isEmpty()) {
            Toast.makeText(context, S.wordEditor.clipboardEmptyFormatToast, Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = viewModel.parseImportTextToParsedWords(clipText)
        if (parsed.isEmpty()) {
            Toast.makeText(context, S.wordEditor.noValidWordsInTextToast, Toast.LENGTH_SHORT).show()
            return
        }
        if (editTab == 1 && rawTxtDirty) {
            syncTxtToDrafts(rawTxtContent)
        }
        if (draftItems.isEmpty()) {
            applyParsedWords(parsed, replace = true)
            Toast.makeText(context, S.wordEditor.importedFromClipboardToast.withArgs("count" to parsed.size), Toast.LENGTH_SHORT).show()
        } else {
            pendingTemplateMeta = null
            pendingImportParsedWords = parsed
        }
    }

    val txtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val fileName = WordImageManager.getUriDisplayName(context, uri)
                        .takeIf { it.isNotBlank() }
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.readBytes()
                        } ?: ByteArray(0)
                    }
                    val importResult = withContext(Dispatchers.IO) {
                        SpreadsheetWordImporter.importFromBytes(
                            bytes = bytes,
                            fileName = fileName,
                            fallbackTextParser = viewModel::parseImportTextToParsedWords
                        )
                    }
                    if (importResult.errorMessage != null) {
                        Toast.makeText(context, importResult.errorMessage, Toast.LENGTH_LONG).show()
                    } else if (importResult.words.isEmpty()) {
                        Toast.makeText(context, S.wordEditor.noValidWordsInFileToast, Toast.LENGTH_SHORT).show()
                    } else if (draftItems.isEmpty()) {
                        applyParsedWords(importResult.words, replace = true)
                        Toast.makeText(context, S.wordEditor.importedWordsCountToast.withArgs("count" to importResult.words.size), Toast.LENGTH_SHORT).show()
                    } else {
                        pendingTemplateMeta = null
                        pendingImportParsedWords = importResult.words
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, S.wordEditor.readFileFailedToast.withArgs("msg" to (e.localizedMessage ?: "")), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val singleImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val cb = pendingSingleImageCallback
        pendingSingleImageCallback = null
        if (uri != null && cb != null) {
            scope.launch {
                val staged = WordImageManager.stageImageFromUri(context, uri)
                if (staged != null) {
                    cb(staged)
                    hasUnsavedChanges = true
                } else {
                    Toast.makeText(context, S.wordEditor.readImageFailedRetryToast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured: Boolean ->
        val cb = pendingSingleImageCallback
        val tempFile = pendingCameraTempFile
        pendingSingleImageCallback = null
        pendingCameraTempFile = null
        if (captured && cb != null && tempFile != null) {
            scope.launch {
                val staged = WordImageManager.stageImageFromFile(context, tempFile)
                runCatching { tempFile.delete() }
                if (staged != null) {
                    cb(staged)
                    hasUnsavedChanges = true
                } else {
                    Toast.makeText(context, S.wordEditor.savePhotoFailedRetryToast, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            runCatching { tempFile?.delete() }
        }
    }

    fun launchSinglePhotoPick(onStaged: (String) -> Unit) {
        pendingSingleImageCallback = onStaged
        singleImagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun launchCameraPhotoCapture(onStaged: (String) -> Unit) {
        try {
            val tempFile = WordImageManager.getCameraCaptureTempFile(context)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            pendingCameraTempFile = tempFile
            pendingSingleImageCallback = onStaged
            cameraCaptureLauncher.launch(uri)
        } catch (e: Exception) {
            pendingCameraTempFile = null
            pendingSingleImageCallback = null
            Toast.makeText(context, S.wordEditor.cameraUnavailableToast, Toast.LENGTH_SHORT).show()
        }
    }

    val batchImagesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                if (editTab == 1 && rawTxtDirty) {
                    syncTxtToDrafts(rawTxtContent)
                }
                editTab = 0
                isBatchImportingImages = true
                val ingestedList = withContext(Dispatchers.IO) {
                    val result = ArrayList<Pair<ParsedWord, String>>(uris.size)
                    for ((idx, uri) in uris.withIndex()) {
                        val displayName = WordImageManager.getUriDisplayName(context, uri)
                        val staged = WordImageManager.stageImageFromUri(context, uri) ?: continue
                        val parsed = WordImageManager.parseWordFromImageFileName(
                            rawFileName = displayName,
                            fallbackIndex = draftItems.size + idx + 1,
                            wordAndRemarkParser = { raw ->
                                val first = viewModel.parseImportTextToParsedWords(raw).firstOrNull()
                                if (first != null) first.text to first.remark else raw to ""
                            }
                        )
                        result.add(parsed to staged)
                    }
                    result
                }
                isBatchImportingImages = false
                if (ingestedList.isEmpty()) {
                    Toast.makeText(context, S.wordEditor.readSelectedImageFailedToast, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                var matchedCount = 0
                var createdCount = 0
                val updatedIndicesInBatch = HashSet<Int>()
                for ((parsed, staged) in ingestedList) {
                    val targetWordText = parsed.text.trim()
                    val unpicturedMatchIdx = draftItems.indexOfFirst {
                        it.text.trim() == targetWordText && it.imagePath.isBlank()
                    }
                    val matchIdx = if (unpicturedMatchIdx >= 0) {
                        unpicturedMatchIdx
                    } else {
                        draftItems.indexOfFirst { idxItem ->
                            idxItem.text.trim() == targetWordText
                        }.takeIf { it >= 0 && it !in updatedIndicesInBatch } ?: -1
                    }

                    if (matchIdx >= 0) {
                        val existing = draftItems[matchIdx]
                        draftItems[matchIdx] = existing.copy(
                            imagePath = staged,
                            remark = existing.remark.ifBlank { parsed.remark },
                            groupName = existing.groupName.ifBlank { parsed.groupName }
                        )
                        updatedIndicesInBatch.add(matchIdx)
                        matchedCount++
                    } else {
                        draftItems.add(
                            WordEditorUiItem(
                                existingWordId = null,
                                text = targetWordText,
                                remark = parsed.remark,
                                groupName = parsed.groupName,
                                imagePath = staged
                            )
                        )
                        createdCount++
                    }
                }
                if (projectName.isBlank() && createdCount > 0) {
                    projectName = S.wordEditor.pictureProjectDefaultName
                }
                enablePicturePrompts = true
                showProjectInfoExpanded = false
                hasUnsavedChanges = true
                Toast.makeText(
                    context,
                    S.wordEditor.batchImagesImportedToast.withArgs(
                        "total" to (matchedCount + createdCount),
                        "matched" to matchedCount,
                        "created" to createdCount
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun handleSave(openRecordingAfterSave: Boolean) {
        if (editTab == 1 && rawTxtDirty) {
            syncTxtToDrafts(rawTxtContent)
        }
        val validDrafts = draftItems
            .filter { it.text.isNotBlank() }
            .map {
                EditableWordDraft(
                    existingWordId = it.existingWordId,
                    text = it.text.trim(),
                    remark = it.remark.trim(),
                    groupName = it.groupName.trim(),
                    imagePath = it.imagePath.trim()
                )
            }

        if (validDrafts.isEmpty()) {
            Toast.makeText(context, S.wordEditor.emptyWordListSaveGuardToast, Toast.LENGTH_SHORT).show()
            return
        }

        val finalName = projectName.trim().ifEmpty {
            targetProject?.name ?: S.wordEditor.newWordListDefaultName.withArgs("count" to validDrafts.size)
        }

        val existingProj = liveTargetProject ?: targetProject
        if (existingProj == null) {
            val resolvedDraftAssigned = if (hasEditedDraftSpeakers) {
                formatAssignedSpeakerIds(draftAssignedSpeakerIds)
            } else {
                ""
            }
            viewModel.createProjectFromDrafts(
                name = finalName,
                description = projectDesc.trim(),
                speakerGroupName = "",
                drafts = validDrafts,
                enablePicturePrompts = enablePicturePrompts,
                assignedSpeakerIds = resolvedDraftAssigned
            ) { created ->
                hasUnsavedChanges = false
                Toast.makeText(
                    context,
                    S.wordEditor.createdWordListToast.withArgs("name" to finalName, "count" to validDrafts.size),
                    Toast.LENGTH_SHORT
                ).show()
                if (openRecordingAfterSave && created != null) {
                    onSavedAndOpenRecording(created)
                } else {
                    onBack()
                }
            }
        } else {
            viewModel.saveEditedProjectWords(
                project = existingProj,
                name = finalName,
                description = projectDesc.trim(),
                speakerGroupName = existingProj.speakerGroupName,
                drafts = validDrafts,
                enablePicturePrompts = enablePicturePrompts,
                assignedSpeakerIds = existingProj.assignedSpeakerIds
            ) { updated ->
                hasUnsavedChanges = false
                Toast.makeText(
                    context,
                    S.wordEditor.savedWordListToast.withArgs("name" to finalName, "count" to validDrafts.size),
                    Toast.LENGTH_SHORT
                ).show()
                if (openRecordingAfterSave) {
                    viewModel.selectProject(updated)
                    onSavedAndOpenRecording(updated)
                } else {
                    onBack()
                }
            }
        }
    }

    fun requestBack() {
        if (hasUnsavedChanges || rawTxtDirty) {
            showDiscardConfirmDialog = true
        } else {
            onBack()
        }
    }

    val editorBackState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = true,
        state = editorBackState
    ) {
        requestBack()
    }

    val groupSummary by remember {
        derivedStateOf {
            val counts = LinkedHashMap<String, Int>()
            for (item in draftItems) {
                val g = item.groupName.trim()
                if (g.isNotEmpty()) {
                    counts[g] = (counts[g] ?: 0) + 1
                }
            }
            counts
        }
    }
    val distinctWordGroups by remember {
        derivedStateOf { groupSummary.keys.toList() }
    }

    val filteredIndexedItems by remember {
        derivedStateOf {
            val filter = selectedGroupFilter
            val q = searchQuery.trim()
            if (filter == "ALL" && q.isEmpty()) {
                draftItems.mapIndexed { index, item -> index to item }
            } else {
                draftItems.mapIndexedNotNull { index, item ->
                    val matchGroup = when (filter) {
                        "ALL" -> true
                        "UNGROUPED" -> item.groupName.isBlank()
                        else -> item.groupName.trim() == filter
                    }
                    if (!matchGroup) return@mapIndexedNotNull null
                    val matchQuery = q.isEmpty() ||
                        item.text.contains(q, ignoreCase = true) ||
                        item.remark.contains(q, ignoreCase = true) ||
                        item.groupName.contains(q, ignoreCase = true)
                    if (matchQuery) index to item else null
                }
            }
        }
    }

    val liveParsedPreview by produceState<List<ParsedWord>>(
        initialValue = if (editTab == 1 && rawTxtContent.length <= 1200) {
            viewModel.parseImportTextToParsedWords(rawTxtContent)
        } else {
            emptyList()
        },
        key1 = rawTxtContent,
        key2 = editTab
    ) {
        if (editTab != 1 || rawTxtContent.isBlank()) {
            value = emptyList()
        } else if (rawTxtContent.length <= 1200) {
            value = viewModel.parseImportTextToParsedWords(rawTxtContent)
        } else {
            delay(180)
            value = withContext(Dispatchers.Default) {
                viewModel.parseImportTextToParsedWords(rawTxtContent)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = S.wordEditor.screenTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (targetProject == null) {
                                S.wordEditor.newWordListSubtitle
                            } else {
                                S.wordEditor.editingProjectSubtitle.withArgs("name" to (targetProject?.name ?: ""))
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { requestBack() },
                        modifier = Modifier.testTag("word_editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.common.back)
                    }
                },
                actions = {
                    val isCompactTopBar = LocalAppWindowMetrics.current.widthDp < 460.dp
                    TextButton(
                        onClick = { handleSave(openRecordingAfterSave = false) },
                        contentPadding = PaddingValues(horizontal = if (isCompactTopBar) 8.dp else 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 38.dp)
                            .testTag("word_editor_save_button")
                    ) {
                        Text(S.common.save, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                    if (isCompactTopBar) {
                        Button(
                            onClick = { handleSave(openRecordingAfterSave = true) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .defaultMinSize(minWidth = 40.dp, minHeight = 38.dp)
                                .testTag("word_editor_save_and_record_button")
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = S.wordEditor.saveAndRecordButton,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = { handleSave(openRecordingAfterSave = true) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .defaultMinSize(minHeight = 38.dp)
                                .testTag("word_editor_save_and_record_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(S.wordEditor.saveAndRecordButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.predictiveBackLayer(editorBackState)
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            val isWideEditor = maxWidth >= 780.dp
            val editorViewportHeight = maxHeight

            @Composable
            fun ProjectSelectorAndMetaSection(alwaysExpanded: Boolean) {
                // 1. Target Project Selector (Switch between New Word List and Existing Projects)
                val targetProjectStripState = rememberLazyListState()
                StudioHorizontalLazyStrip(
                    state = targetProjectStripState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    LazyRow(
                        state = targetProjectStripState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            StudioFilterChip(
                                selected = targetProject == null,
                                onClick = {
                                    if (targetProject != null) {
                                        if (hasUnsavedChanges || rawTxtDirty) {
                                            pendingSwitchTargetProject = true to null
                                        } else {
                                            loadTargetProject(null)
                                        }
                                    }
                                },
                                labelText = S.wordEditor.newWordListBadge,
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                        items(allProjects, key = { it.id }) { proj ->
                            StudioFilterChip(
                                selected = targetProject?.id == proj.id,
                                onClick = {
                                    if (targetProject?.id != proj.id) {
                                        if (hasUnsavedChanges || rawTxtDirty) {
                                            pendingSwitchTargetProject = true to proj
                                        } else {
                                            loadTargetProject(proj)
                                        }
                                    }
                                },
                                labelText = proj.name,
                                leadingIcon = {
                                    Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                // 2. Project Metadata Card (Collapsible on mobile, always expanded on desktop sidebar)
                val expanded = alwaysExpanded || showProjectInfoExpanded
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!alwaysExpanded) {
                                        Modifier.clickable { showProjectInfoExpanded = !showProjectInfoExpanded }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = S.wordEditor.nameDisplayPrefix.withArgs(
                                        "name" to if (projectName.isBlank()) S.wordEditor.unnamedPleaseFill else projectName
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val effectiveAssignedSpeakers = remember(
                                    liveTargetProject,
                                    summariesMap,
                                    allSpeakers,
                                    draftAssignedSpeakerIds,
                                    hasEditedDraftSpeakers,
                                    isSimpleMode
                                ) {
                                    if (liveTargetProject != null) {
                                        val projSummary = summariesMap[liveTargetProject.id]
                                        if (projSummary != null) {
                                            projSummary.speakerProgressList.map { it.speaker }
                                        } else {
                                            val assignedSet = liveTargetProject.assignedSpeakerIdSet
                                            allSpeakers.filter { spk ->
                                                liveTargetProject.isSpeakerCandidate(spk, 0, isSimpleMode, assignedSet)
                                            }
                                        }
                                    } else {
                                        if (hasEditedDraftSpeakers) {
                                            allSpeakers.filter { it.id in draftAssignedSpeakerIds }
                                        } else {
                                            allSpeakers
                                        }
                                    }
                                }
                                val assignedSummaryText = remember(effectiveAssignedSpeakers, allSpeakers.size) {
                                    formatProjectRosterSummary(effectiveAssignedSpeakers, allSpeakers.size)
                                }
                                val picturedCount = draftItems.count { it.imagePath.isNotBlank() }
                                val picBadgeText = if (enablePicturePrompts) {
                                    S.wordEditor.imageCardEnabledBadge.withArgs("pictured" to picturedCount, "total" to draftItems.size)
                                } else {
                                    ""
                                }
                                val rosterHint = S.wordEditor.assignedSpeakersSubtitlePrefix.withArgs("summary" to assignedSummaryText)
                                val subtitleHint = buildString {
                                    append(rosterHint)
                                    if (projectDesc.isNotBlank()) {
                                        append(" · ")
                                        append(projectDesc)
                                    }
                                    append(picBadgeText)
                                }
                                Text(
                                    text = subtitleHint,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!alwaysExpanded) {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = S.wordEditor.toggleMetaDesc,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.studioRotateOn(showProjectInfoExpanded)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = expanded,
                            enter = StudioMotion.verticalPanelEnter,
                            exit = StudioMotion.verticalPanelExit
                        ) {
                            val mobileMetaScrollModifier = if (!alwaysExpanded) {
                                Modifier
                                    .heightIn(max = (editorViewportHeight * 0.42f).coerceAtLeast(185.dp))
                                    .verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(mobileMetaScrollModifier)
                                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = projectName,
                                    onValueChange = {
                                        projectName = it
                                        hasUnsavedChanges = true
                                    },
                                    label = { Text(S.wordEditor.nameInputLabel) },
                                    placeholder = { Text(S.wordEditor.nameInputPlaceholder) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("editor_project_name_input")
                                )

                                OutlinedTextField(
                                    value = projectDesc,
                                    onValueChange = {
                                        projectDesc = it
                                        hasUnsavedChanges = true
                                    },
                                    label = { Text(S.wordEditor.descInputLabel) },
                                    placeholder = { Text(S.wordEditor.descInputPlaceholder) },
                                    singleLine = !alwaysExpanded,
                                    maxLines = if (alwaysExpanded) 2 else 1,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                run {
                                    val cs = MaterialTheme.colorScheme
                                    val (picRowInteraction, isPicRowHoveredState) = rememberStudioInteraction()
                                    val isPicRowHovered by isPicRowHoveredState
                                    val picRowBg by animateColorAsState(
                                        targetValue = when {
                                            enablePicturePrompts -> lerp(
                                                cs.surface,
                                                cs.primaryContainer,
                                                if (isPicRowHovered) 0.52f else 0.36f
                                            )
                                            isPicRowHovered -> lerp(cs.surface, cs.primaryContainer, 0.28f)
                                            else -> cs.surface
                                        },
                                        animationSpec = StudioMotion.microTween(),
                                        label = "EditorPictureToggleRowBg"
                                    )
                                    val picRowBorder by animateColorAsState(
                                        targetValue = when {
                                            enablePicturePrompts -> cs.primary.copy(alpha = if (isPicRowHovered) 0.65f else 0.46f)
                                            isPicRowHovered -> cs.primary.copy(alpha = 0.38f)
                                            else -> cs.outlineVariant.copy(alpha = 0.65f)
                                        },
                                        animationSpec = StudioMotion.microTween(),
                                        label = "EditorPictureToggleRowBorder"
                                    )
                                    val picRowTitleColor by animateColorAsState(
                                        targetValue = if (isPicRowHovered) cs.primary else cs.onSurface,
                                        animationSpec = StudioMotion.microTween(),
                                        label = "EditorPictureToggleTitleColor"
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = picRowBg,
                                        border = BorderStroke(1.dp, picRowBorder),
                                        shadowElevation = 0.dp,
                                        tonalElevation = 0.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .hoverable(picRowInteraction)
                                            .then(if (DesktopAdaptiveState.isDesktopPlatform) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                                            .clickable(
                                                interactionSource = picRowInteraction,
                                                indication = null
                                            ) {
                                                enablePicturePrompts = !enablePicturePrompts
                                                hasUnsavedChanges = true
                                            }
                                            .testTag("editor_enable_picture_prompts_row")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PhotoLibrary,
                                                    contentDescription = null,
                                                    tint = if (enablePicturePrompts) cs.primary else cs.onSurfaceVariant,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Text(
                                                    text = S.wordEditor.pictureCardToggleTitle,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = picRowTitleColor,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                TooltipBox(
                                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                                    tooltip = {
                                                        PlainTooltip {
                                                            Text(S.wordEditor.pictureCardToggleSubtitle)
                                                        }
                                                    },
                                                    state = rememberTooltipState()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Info,
                                                        contentDescription = S.wordEditor.pictureCardToggleSubtitle,
                                                        tint = cs.onSurfaceVariant.copy(alpha = 0.72f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            StudioSwitch(
                                                checked = enablePicturePrompts,
                                                onCheckedChange = {
                                                    enablePicturePrompts = it
                                                    hasUnsavedChanges = true
                                                },
                                                modifier = Modifier
                                                    .scale(0.84f)
                                                    .testTag("editor_enable_picture_prompts_switch")
                                            )
                                        }
                                    }
                                }

                                run {
                                    val editorRosterSpeakers = remember(
                                        liveTargetProject,
                                        summariesMap,
                                        allSpeakers,
                                        draftAssignedSpeakerIds,
                                        hasEditedDraftSpeakers,
                                        isSimpleMode
                                    ) {
                                        if (liveTargetProject != null) {
                                            val projSummary = summariesMap[liveTargetProject.id]
                                            if (projSummary != null) {
                                                projSummary.speakerProgressList.map { it.speaker }
                                            } else {
                                                val assignedSet = liveTargetProject.assignedSpeakerIdSet
                                                allSpeakers.filter { spk ->
                                                    liveTargetProject.isSpeakerCandidate(spk, 0, isSimpleMode, assignedSet)
                                                }
                                            }
                                        } else {
                                            if (hasEditedDraftSpeakers) {
                                                allSpeakers.filter { it.id in draftAssignedSpeakerIds }
                                            } else {
                                                allSpeakers
                                            }
                                        }
                                    }
                                    val editorRosterSummary = remember(editorRosterSpeakers, allSpeakers.size) {
                                        formatProjectRosterSummary(editorRosterSpeakers, allSpeakers.size)
                                    }
                                    ProjectAssignedSpeakersActionRow(
                                        selectedCount = editorRosterSpeakers.size,
                                        summaryText = editorRosterSummary,
                                        onClick = { showRosterDialog = true },
                                        modifier = Modifier.testTag("editor_assigned_speakers_row")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            @Composable
            fun ColumnScope.EditorMainContent(showBottomImportBar: Boolean) {
                LaunchedEffect(isSimpleMode) {
                    if (isSimpleMode && editTab == 1) {
                        if (rawTxtDirty) {
                            syncTxtToDrafts(rawTxtContent)
                        }
                        editTab = 0
                    }
                }

                // 3. Mode Switcher (Visual List vs Raw TXT Editor — hidden in Simple Mode)
                if (!isSimpleMode) {
                    val count = if (editTab == 1 && rawTxtDirty) liveParsedPreview.size else draftItems.size
                    StudioSegmentedControl(
                        options = listOf(
                            S.wordEditor.viewModeWordList.withArgs("count" to count),
                            S.wordEditor.viewModeTxtBatch
                        ),
                        selectedIndex = editTab,
                        onOptionSelected = { newTab ->
                            if (newTab == 0 && editTab == 1) {
                                if (rawTxtDirty) {
                                    syncTxtToDrafts(rawTxtContent)
                                }
                                editTab = 0
                            } else if (newTab == 1 && editTab == 0) {
                                rawTxtContent = buildTxtFromCurrentDrafts()
                                rawTxtDirty = false
                                editTab = 1
                            }
                        },
                        optionTestTags = listOf("tab_visual_word_list", "tab_raw_txt_editor"),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // 4. Main Editor Area
                val isVisualListMode = isSimpleMode || editTab == 0
                AnimatedContent(
                    targetState = isVisualListMode,
                    transitionSpec = { StudioMotion.subtleFadeScale() },
                    label = "WordEditorTabContent",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { visualMode ->
                    if (visualMode) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Visual List Mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Compact custom search box with zero vertical truncation
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
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
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = S.wordEditor.searchPlaceholder,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
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
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ),
                                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchQuery = "" },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = S.wordEditor.clearSearchDesc,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        editingWordIndex = null
                                        showAddOrEditWordDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(44.dp)
                                        .testTag("add_single_word_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(S.wordEditor.addWordButton, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (distinctWordGroups.isNotEmpty() || draftItems.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val wordGroupStripState = rememberLazyListState()
                                    StudioHorizontalLazyStrip(
                                        state = wordGroupStripState,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        LazyRow(
                                            state = wordGroupStripState,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            contentPadding = PaddingValues(end = 8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            item {
                                                StudioFilterChip(
                                                    selected = selectedGroupFilter == "ALL",
                                                    onClick = { selectedGroupFilter = "ALL" },
                                                    labelText = S.wordEditor.allFilterChip.withArgs("count" to draftItems.size)
                                                )
                                            }
                                            items(distinctWordGroups, key = { it }) { grp ->
                                                val c = groupSummary[grp] ?: 0
                                                StudioFilterChip(
                                                    selected = selectedGroupFilter == grp,
                                                    onClick = { selectedGroupFilter = grp },
                                                    labelText = "$grp ($c)"
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        VerticalDivider(
                                            modifier = Modifier
                                                .height(16.dp)
                                                .padding(horizontal = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                        if (draftItems.size > 1) {
                                            TextButton(
                                                onClick = {
                                                    val seen = mutableSetOf<String>()
                                                    val deduped = draftItems.filter { item ->
                                                        val key = "${item.groupName.trim()}::${item.text.trim()}"
                                                        seen.add(key)
                                                    }
                                                    val removedCount = draftItems.size - deduped.size
                                                    if (removedCount > 0) {
                                                        draftItems.clear()
                                                        draftItems.addAll(deduped)
                                                        hasUnsavedChanges = true
                                                        Toast.makeText(
                                                            context,
                                                            S.wordEditor.removedDuplicatesToast.withArgs("count" to removedCount),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(context, S.wordEditor.noDuplicatesFoundToast, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(S.wordEditor.deduplicateButton, fontSize = 11.sp)
                                            }
                                        }
                                        if (draftItems.isNotEmpty()) {
                                            TextButton(
                                                onClick = { showClearConfirmDialog = true },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(S.wordEditor.clearAllButton, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedContent(
                                targetState = draftItems.isEmpty(),
                                transitionSpec = { StudioMotion.subtleFadeScale() },
                                label = "WordEditorEmptyOrList",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) { isEmptyList ->
                                if (isEmptyList) {
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 6.dp)
                                    ) {
                                        BoxWithConstraints(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val isCompactEmptyCard = maxHeight < 235.dp
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(
                                                        horizontal = if (isCompactEmptyCard) 16.dp else 22.dp,
                                                        vertical = if (isCompactEmptyCard) 14.dp else 20.dp
                                                    ),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                if (isCompactEmptyCard) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Text(
                                                            text = S.wordEditor.emptyListTitle,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(40.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(
                                                        text = S.wordEditor.emptyListTitle,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(if (isCompactEmptyCard) 6.dp else 8.dp))
                                                Text(
                                                    text = if (isSimpleMode) {
                                                        S.wordEditor.emptyListHintMobile
                                                    } else {
                                                        S.wordEditor.emptyListHintDesktop
                                                    },
                                                    fontSize = if (isCompactEmptyCard) 12.sp else 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = if (isCompactEmptyCard) 17.sp else 19.sp
                                                )
                                                Spacer(modifier = Modifier.height(if (isCompactEmptyCard) 12.dp else 16.dp))

                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { showTemplateDialog = true },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                        modifier = Modifier.defaultMinSize(minHeight = 42.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(S.wordEditor.chooseTemplateButton, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { handlePasteFromClipboard() },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                        modifier = Modifier.defaultMinSize(minHeight = 42.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(S.wordEditor.pasteAsWordListButton, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                                    }
                                                }

                                                AnimatedVisibility(
                                                    visible = enablePicturePrompts,
                                                    enter = StudioMotion.verticalPanelEnter,
                                                    exit = StudioMotion.verticalPanelExit
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        FilledTonalButton(
                                                            onClick = {
                                                                batchImagesPickerLauncher.launch(
                                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                                )
                                                            },
                                                            enabled = !isBatchImportingImages,
                                                            shape = RoundedCornerShape(12.dp),
                                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                            modifier = Modifier
                                                                .defaultMinSize(minHeight = 42.dp)
                                                                .testTag("empty_state_batch_import_images_button")
                                                        ) {
                                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(17.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = if (isBatchImportingImages) {
                                                                    S.wordEditor.batchImportImagesLoading
                                                                } else {
                                                                    S.wordEditor.batchImportImagesAutoHint
                                                                },
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
                                } else {
                                    val onEditRow = remember {
                                        { idx: Int ->
                                            editingWordIndex = idx
                                            showAddOrEditWordDialog = true
                                        }
                                    }
                                    val onClickImageSlotRow = remember {
                                        { idx: Int ->
                                            activeImageMenuRowIndex = idx
                                        }
                                    }
                                    val onMoveUpRow = remember {
                                        { idx: Int ->
                                            if (idx > 0 && idx < draftItems.size) {
                                                val removed = draftItems.removeAt(idx)
                                                draftItems.add(idx - 1, removed)
                                                hasUnsavedChanges = true
                                            }
                                        }
                                    }
                                    val onMoveDownRow = remember {
                                        { idx: Int ->
                                            if (idx >= 0 && idx < draftItems.size - 1) {
                                                val removed = draftItems.removeAt(idx)
                                                draftItems.add(idx + 1, removed)
                                                hasUnsavedChanges = true
                                            }
                                        }
                                    }
                                    val onDeleteRow = remember {
                                        { idx: Int ->
                                            if (idx in draftItems.indices) {
                                                draftItems.removeAt(idx)
                                                hasUnsavedChanges = true
                                            }
                                        }
                                    }
                                    val onDragMoveItem = remember {
                                        { fromIdx: Int, toIdx: Int, adoptGroup: String? ->
                                            if (fromIdx in draftItems.indices && toIdx in draftItems.indices && fromIdx != toIdx) {
                                                val moved = draftItems.removeAt(fromIdx)
                                                val updated = if (adoptGroup != null && moved.groupName != adoptGroup) {
                                                    moved.copy(groupName = adoptGroup)
                                                } else {
                                                    moved
                                                }
                                                draftItems.add(toIdx, updated)
                                                hasUnsavedChanges = true
                                            }
                                        }
                                    }
                                    val totalDraftCount = draftItems.size
                                    val currentProjectId = targetProject?.id ?: 0L
                                    val showGroupPinHeaders = remember(filteredIndexedItems, selectedGroupFilter) {
                                        selectedGroupFilter == "ALL" &&
                                            filteredIndexedItems.any { it.second.groupName.isNotBlank() }
                                    }
                                    val gridEntries: List<WordEditorGridEntry> = remember(filteredIndexedItems, showGroupPinHeaders) {
                                        if (!showGroupPinHeaders) {
                                            filteredIndexedItems.map { (realIdx, item) ->
                                                WordEditorGridEntry.WordCell(
                                                    realIndex = realIdx,
                                                    item = item,
                                                    showInlineGroupBadge = item.groupName.isNotBlank() && selectedGroupFilter == "ALL"
                                                )
                                            }
                                        } else {
                                            buildList<WordEditorGridEntry> {
                                                var cursor = 0
                                                var sectionSeq = 0
                                                while (cursor < filteredIndexedItems.size) {
                                                    val grp = filteredIndexedItems[cursor].second.groupName
                                                    var end = cursor + 1
                                                    while (end < filteredIndexedItems.size &&
                                                        filteredIndexedItems[end].second.groupName == grp
                                                    ) {
                                                        end++
                                                    }
                                                    add(
                                                        WordEditorGridEntry.GroupHeader(
                                                            sectionIndex = sectionSeq++,
                                                            groupName = grp,
                                                            itemCount = end - cursor
                                                        )
                                                    )
                                                    for (i in cursor until end) {
                                                        val (realIdx, item) = filteredIndexedItems[i]
                                                        add(
                                                            WordEditorGridEntry.WordCell(
                                                                realIndex = realIdx,
                                                                item = item,
                                                                showInlineGroupBadge = false
                                                            )
                                                        )
                                                    }
                                                    cursor = end
                                                }
                                            }
                                        }
                                    }

                                    val wordGridState = rememberLazyGridState()
                                    val gridDensity = LocalDensity.current
                                    var draggingLocalKey by remember { mutableStateOf<String?>(null) }
                                    var dragStartItemOffset by remember { mutableStateOf(IntOffset.Zero) }
                                    var accumulatedDragDelta by remember { mutableStateOf(Offset.Zero) }
                                    var activeDragPointerY by remember { mutableFloatStateOf(0f) }
                                    var lastSwapLayoutInfo by remember { mutableStateOf<Any?>(null) }

                                    val latestGridEntries by rememberUpdatedState(gridEntries)
                                    val latestAllowCrossGroupAdopt by rememberUpdatedState(selectedGroupFilter == "ALL" && showGroupPinHeaders)

                                    // Edge auto-scroll while dragging near viewport top/bottom
                                    LaunchedEffect(draggingLocalKey) {
                                        if (draggingLocalKey == null) return@LaunchedEffect
                                        val edgePx = with(gridDensity) { 48.dp.toPx() }
                                        val maxStepPx = with(gridDensity) { 14.dp.toPx() }
                                        while (draggingLocalKey != null) {
                                            val layout = wordGridState.layoutInfo
                                            val vpTop = layout.viewportStartOffset.toFloat()
                                            val vpBottom = layout.viewportEndOffset.toFloat()
                                            val y = activeDragPointerY
                                            val delta = when {
                                                y < vpTop + edgePx && wordGridState.canScrollBackward -> {
                                                    val ratio = ((vpTop + edgePx - y) / edgePx).coerceIn(0f, 1f)
                                                    -maxStepPx * ratio
                                                }
                                                y > vpBottom - edgePx && wordGridState.canScrollForward -> {
                                                    val ratio = ((y - (vpBottom - edgePx)) / edgePx).coerceIn(0f, 1f)
                                                    maxStepPx * ratio
                                                }
                                                else -> 0f
                                            }
                                            if (delta != 0f) {
                                                wordGridState.scrollBy(delta)
                                            }
                                            delay(16L)
                                        }
                                    }

                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                        val columns = when {
                                            enablePicturePrompts -> when {
                                                maxWidth >= 780.dp -> 3
                                                maxWidth >= 520.dp -> 2
                                                else -> 1
                                            }
                                            else -> when {
                                                maxWidth >= 700.dp -> 3
                                                maxWidth >= 460.dp -> 2
                                                else -> 1
                                            }
                                        }
                                        val isMultiColumn = columns >= 2
                                        val dragEnabled = filteredIndexedItems.size >= 2

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(columns),
                                            state = wordGridState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                top = 6.dp,
                                                bottom = 12.dp,
                                                end = if (DesktopAdaptiveState.isDesktopPlatform) 11.dp else 0.dp
                                            ),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(
                                                items = gridEntries,
                                                key = { it.key },
                                                span = { entry ->
                                                    when (entry) {
                                                        is WordEditorGridEntry.GroupHeader -> GridItemSpan(maxLineSpan)
                                                        is WordEditorGridEntry.WordCell -> GridItemSpan(1)
                                                    }
                                                },
                                                contentType = { entry ->
                                                    when (entry) {
                                                        is WordEditorGridEntry.GroupHeader -> "word_editor_group_header"
                                                        is WordEditorGridEntry.WordCell -> "word_editor_draft_card"
                                                    }
                                                }
                                            ) { entry ->
                                                when (entry) {
                                                    is WordEditorGridEntry.GroupHeader -> {
                                                        WordEditorGroupPinDivider(
                                                            groupName = entry.groupName,
                                                            itemCount = entry.itemCount,
                                                            isFirstSection = entry.sectionIndex == 0,
                                                            modifier = studioAnimateItem()
                                                        )
                                                    }

                                                    is WordEditorGridEntry.WordCell -> {
                                                        val isDragging = draggingLocalKey == entry.item.localKey
                                                        val liftScale by animateFloatAsState(
                                                            targetValue = if (isDragging) 1.035f else 1f,
                                                            animationSpec = StudioMotion.tactileSpring(),
                                                            label = "WordDragLift_${entry.item.localKey}"
                                                        )
                                                        val cellDragModifier = if (dragEnabled) {
                                                            Modifier.pointerInput(entry.item.localKey, DesktopAdaptiveState.isDesktopPlatform) {
                                                                val handleDragStart: () -> Unit = {
                                                                    draggingLocalKey = entry.item.localKey
                                                                    accumulatedDragDelta = Offset.Zero
                                                                    lastSwapLayoutInfo = null
                                                                    val info = wordGridState.layoutInfo.visibleItemsInfo
                                                                        .firstOrNull { it.key == entry.key }
                                                                    if (info != null) {
                                                                        dragStartItemOffset = info.offset
                                                                        activeDragPointerY = info.offset.y + info.size.height / 2f
                                                                    } else {
                                                                        dragStartItemOffset = IntOffset.Zero
                                                                    }
                                                                }
                                                                val handleDragDelta: (Offset) -> Unit = { delta ->
                                                                    accumulatedDragDelta += delta
                                                                    val layout = wordGridState.layoutInfo
                                                                    val visible = layout.visibleItemsInfo
                                                                    val draggedInfo = visible.firstOrNull { it.key == entry.key }
                                                                    if (draggedInfo != null) {
                                                                        val desiredX = dragStartItemOffset.x + accumulatedDragDelta.x
                                                                        val desiredY = dragStartItemOffset.y + accumulatedDragDelta.y
                                                                        val centerX = desiredX + draggedInfo.size.width / 2f
                                                                        val centerY = desiredY + draggedInfo.size.height / 2f
                                                                        activeDragPointerY = centerY
                                                                        if (layout !== lastSwapLayoutInfo) {
                                                                            val targetInfo = visible.firstOrNull { info ->
                                                                                info.key != entry.key &&
                                                                                    (info.key as? String)?.startsWith("word_") == true &&
                                                                                    centerX >= info.offset.x &&
                                                                                    centerX <= info.offset.x + info.size.width &&
                                                                                    centerY >= info.offset.y &&
                                                                                    centerY <= info.offset.y + info.size.height
                                                                            }
                                                                            if (targetInfo != null) {
                                                                                val currentEntries = latestGridEntries
                                                                                val fromCell = currentEntries.firstOrNull {
                                                                                    it is WordEditorGridEntry.WordCell && it.key == entry.key
                                                                                } as? WordEditorGridEntry.WordCell
                                                                                val toCell = currentEntries.firstOrNull {
                                                                                    it is WordEditorGridEntry.WordCell && it.key == targetInfo.key
                                                                                } as? WordEditorGridEntry.WordCell
                                                                                if (fromCell != null && toCell != null && fromCell.realIndex != toCell.realIndex) {
                                                                                    val adoptGrp = if (latestAllowCrossGroupAdopt) {
                                                                                        toCell.item.groupName
                                                                                    } else {
                                                                                        null
                                                                                    }
                                                                                    lastSwapLayoutInfo = layout
                                                                                    onDragMoveItem(fromCell.realIndex, toCell.realIndex, adoptGrp)
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                val handleDragEnd: () -> Unit = {
                                                                    draggingLocalKey = null
                                                                    accumulatedDragDelta = Offset.Zero
                                                                    lastSwapLayoutInfo = null
                                                                }
                                                                if (DesktopAdaptiveState.isDesktopPlatform) {
                                                                    detectDragGestures(
                                                                        onDragStart = { handleDragStart() },
                                                                        onDrag = { change, amount ->
                                                                            change.consume()
                                                                            handleDragDelta(amount)
                                                                        },
                                                                        onDragEnd = handleDragEnd,
                                                                        onDragCancel = handleDragEnd
                                                                    )
                                                                } else {
                                                                    detectDragGesturesAfterLongPress(
                                                                        onDragStart = { handleDragStart() },
                                                                        onDrag = { change, amount ->
                                                                            change.consume()
                                                                            handleDragDelta(amount)
                                                                        },
                                                                        onDragEnd = handleDragEnd,
                                                                        onDragCancel = handleDragEnd
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            Modifier
                                                        }

                                                        WordEditorDraftRow(
                                                            realIndex = entry.realIndex,
                                                            totalCount = totalDraftCount,
                                                            item = entry.item,
                                                            projectId = currentProjectId,
                                                            enablePicturePrompts = enablePicturePrompts,
                                                            compactGridCard = isMultiColumn,
                                                            showInlineGroupBadge = entry.showInlineGroupBadge,
                                                            isDragging = isDragging,
                                                            dragEnabled = dragEnabled,
                                                            onEdit = onEditRow,
                                                            onClickImageSlot = onClickImageSlotRow,
                                                            onMoveUp = onMoveUpRow,
                                                            onMoveDown = onMoveDownRow,
                                                            onDelete = onDeleteRow,
                                                            modifier = (if (!isDragging) studioAnimateItem() else Modifier)
                                                                .zIndex(if (isDragging) 10f else 0f)
                                                                .then(cellDragModifier)
                                                                .graphicsLayer {
                                                                    if (isDragging) {
                                                                        val curOffset = wordGridState.layoutInfo.visibleItemsInfo
                                                                            .firstOrNull { it.key == entry.key }?.offset
                                                                        if (curOffset != null) {
                                                                            translationX = dragStartItemOffset.x + accumulatedDragDelta.x - curOffset.x
                                                                            translationY = dragStartItemOffset.y + accumulatedDragDelta.y - curOffset.y
                                                                        } else {
                                                                            translationX = accumulatedDragDelta.x
                                                                            translationY = accumulatedDragDelta.y
                                                                        }
                                                                    }
                                                                    scaleX = liftScale
                                                                    scaleY = liftScale
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        PlatformVerticalScrollbar(
                                            gridState = wordGridState,
                                            modifier = Modifier.align(Alignment.CenterEnd)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Mode 1: Raw TXT Batch Edit & Paste Mode
                        val parsedGroupsCount = remember(liveParsedPreview) {
                            liveParsedPreview.mapNotNull { it.groupName.takeIf { g -> g.isNotEmpty() } }.distinct().size
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
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
                                        text = S.wordEditor.recognizedPreviewCount.withArgs("count" to liveParsedPreview.size) +
                                            if (parsedGroupsCount > 0) S.wordEditor.recognizedPreviewGroups.withArgs("groups" to parsedGroupsCount) else "",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = S.wordEditor.txtSyntaxHintLine,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                                TextButton(
                                    onClick = { showFormatHelpDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(S.wordEditor.formatExampleButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                    if (clipText.isEmpty()) {
                                        Toast.makeText(context, S.wordEditor.clipboardEmptyToast, Toast.LENGTH_SHORT).show()
                                    } else {
                                        rawTxtContent = if (rawTxtContent.isBlank()) {
                                            clipText
                                        } else {
                                            rawTxtContent.trimEnd() + "\n" + clipText
                                        }
                                        rawTxtDirty = true
                                        hasUnsavedChanges = true
                                        Toast.makeText(context, S.wordEditor.pastedToTextBoxToast, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(S.wordEditor.pasteTextButton, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }

                            OutlinedButton(
                                onClick = {
                                    val txtToCopy = if (rawTxtDirty) rawTxtContent else buildTxtFromCurrentDrafts()
                                    if (txtToCopy.isBlank()) {
                                        Toast.makeText(context, S.wordEditor.noContentToCopyToast, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("wordlist_txt", txtToCopy))
                                        Toast.makeText(context, S.wordEditor.copiedTxtToClipboardToast, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(S.wordEditor.copyAllButton, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }

                            if (rawTxtDirty) {
                                Button(
                                    onClick = {
                                        syncTxtToDrafts(rawTxtContent)
                                        editTab = 0
                                        Toast.makeText(
                                            context,
                                            S.wordEditor.parsedAndSyncedToast.withArgs("count" to draftItems.size),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                                    modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(S.wordEditor.parseAndSyncButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = rawTxtContent,
                            onValueChange = {
                                rawTxtContent = it
                                rawTxtDirty = true
                                hasUnsavedChanges = true
                            },
                            placeholder = {
                                Text(
                                    text = S.wordEditor.txtExamplePlaceholder,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("raw_txt_editor_input")
                        )
                    }
                    }
                }

                if (showBottomImportBar) {
                    // 5. Bottom Quick Import & Template Bar (Placed below the word list / TXT batch editor on mobile)
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                    AnimatedVisibility(
                        visible = enablePicturePrompts,
                        enter = StudioMotion.verticalPanelEnter,
                        exit = StudioMotion.verticalPanelExit
                    ) {
                        val picturedCnt = draftItems.count { it.imagePath.isNotBlank() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.wordEditor.picturedSummaryLine.withArgs("pictured" to picturedCnt, "total" to draftItems.size),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            FilledTonalButton(
                                onClick = {
                                    batchImagesPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isBatchImportingImages,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .testTag("batch_import_images_button")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isBatchImportingImages) S.project.importingProject else S.wordEditor.batchImportImagesButton,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showTemplateDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 42.dp)
                                .testTag("open_templates_in_editor_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                S.wordEditor.quickTemplatesShort,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = { handlePasteFromClipboard() },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .defaultMinSize(minHeight = 42.dp)
                                .testTag("paste_txt_as_wordlist_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                S.wordEditor.quickPasteShort,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { txtFileLauncher.launch("*/*") },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 42.dp)
                                .testTag("import_txt_file_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                S.wordEditor.quickImportFileShort,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (isWideEditor) {
                // Desktop / Wide 2-Column Split Layout
                ResponsiveContentContainer(
                    maxWidth = 1180.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Left Column (310.dp): Scrollable so 640dp-tall windows never clip bottom import buttons
                        Column(
                            modifier = Modifier
                                .width(310.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProjectSelectorAndMetaSection(alwaysExpanded = true)

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = S.wordEditor.quickCreateSectionTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    AnimatedVisibility(
                                        visible = enablePicturePrompts,
                                        enter = StudioMotion.verticalPanelEnter,
                                        exit = StudioMotion.verticalPanelExit
                                    ) {
                                        Button(
                                            onClick = {
                                                batchImagesPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            enabled = !isBatchImportingImages,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 42.dp)
                                                .testTag("batch_import_images_button")
                                        ) {
                                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isBatchImportingImages) {
                                                    S.wordEditor.batchImportImagesLoading
                                                } else {
                                                    S.wordEditor.batchImportImagesLongButton
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = { showTemplateDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 42.dp)
                                            .testTag("open_templates_in_editor_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(S.wordEditor.commonTemplatesButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                    FilledTonalButton(
                                        onClick = { handlePasteFromClipboard() },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 42.dp)
                                            .testTag("paste_txt_as_wordlist_button")
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(S.wordEditor.pasteClipboardAsWordListButton, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    }
                                    OutlinedButton(
                                        onClick = { txtFileLauncher.launch("*/*") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 42.dp)
                                            .testTag("import_txt_file_button")
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            S.wordEditor.importLocalFileButton,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Right Column: Mode Switcher + Compact Word List / TXT Editor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 820.dp)
                                    .fillMaxSize()
                            ) {
                                EditorMainContent(showBottomImportBar = false)
                            }
                        }
                    }
                }
            } else {
                ResponsiveContentContainer(
                    maxWidth = 720.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ProjectSelectorAndMetaSection(alwaysExpanded = false)
                        EditorMainContent(showBottomImportBar = true)
                    }
                }
            }
        }
    }

    // 1. Built-in Word List Templates Picker Dialog
    com.example.ui.components.AnimatedDialogVisibility(visible = showTemplateDialog) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(S.wordEditor.chooseTemplateDialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = S.wordEditor.chooseTemplateDialogHint,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    S.wordEditor.builtinTemplates.forEach { tpl ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    showTemplateDialog = false
                                    val parsed = viewModel.parseImportTextToParsedWords(tpl.rawContent)
                                    if (editTab == 1 && rawTxtDirty) {
                                        syncTxtToDrafts(rawTxtContent)
                                    }
                                    if (draftItems.isEmpty()) {
                                        applyParsedWords(
                                            parsed = parsed,
                                            replace = true,
                                            templateMeta = tpl.title to tpl.defaultDescription
                                        )
                                        Toast.makeText(
                                            context,
                                            S.wordEditor.loadedTemplateToast.withArgs("title" to tpl.title),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        pendingTemplateMeta = tpl.title to tpl.defaultDescription
                                        pendingImportParsedWords = parsed
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(tpl.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                    Text(
                                        text = tpl.subtitle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tpl.defaultDescription,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = S.wordEditor.loadTemplateDesc,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text(S.common.close)
                }
            }
        )
    }

    // 2. Replace vs Append Confirmation Dialog when importing into non-empty editor
    com.example.ui.components.AnimatedNullableDialogVisibility(value = pendingImportParsedWords) { incoming ->
        val meta = pendingTemplateMeta
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = {
                pendingImportParsedWords = null
                pendingTemplateMeta = null
            },
            title = { Text(S.wordEditor.importConflictTitle.withArgs("count" to incoming.size), fontWeight = FontWeight.Bold) },
            text = {
                Text(S.wordEditor.importConflictBody.withArgs("existing" to draftItems.size))
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            applyParsedWords(incoming, replace = true, templateMeta = meta)
                            pendingImportParsedWords = null
                            pendingTemplateMeta = null
                            Toast.makeText(
                                context,
                                S.wordEditor.replacedWordListToast.withArgs("count" to incoming.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(S.wordEditor.replaceCurrentListButton, maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = {
                            applyParsedWords(incoming, replace = false, templateMeta = meta)
                            pendingImportParsedWords = null
                            pendingTemplateMeta = null
                            Toast.makeText(
                                context,
                                S.wordEditor.appendedWordsToast.withArgs("count" to incoming.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(S.wordEditor.appendToEndButton, maxLines = 1, softWrap = false)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingImportParsedWords = null
                        pendingTemplateMeta = null
                    }
                ) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // 3. Add or Edit Single Word Dialog
    com.example.ui.components.AnimatedDialogVisibility(visible = showAddOrEditWordDialog) {
        val idx = editingWordIndex
        val existingItem = idx?.let { draftItems.getOrNull(it) }
        var wordText by remember(existingItem) { mutableStateOf(existingItem?.text ?: "") }
        var wordRemark by remember(existingItem) { mutableStateOf(existingItem?.remark ?: "") }
        var wordGroup by remember(existingItem) {
            mutableStateOf(
                existingItem?.groupName
                    ?: (selectedGroupFilter.takeIf { it != "ALL" && it != "UNGROUPED" } ?: "")
            )
        }
        var wordImagePath by remember(existingItem) { mutableStateOf(existingItem?.imagePath ?: "") }

        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showAddOrEditWordDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
            title = {
                Text(
                    text = if (existingItem == null) {
                        S.wordEditor.addWordDialogTitle
                    } else {
                        S.wordEditor.editWordDialogTitle.withArgs("index" to ((idx ?: 0) + 1))
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = wordText,
                        onValueChange = { wordText = it },
                        label = { Text(S.wordEditor.wordContentRequiredLabel) },
                        placeholder = { Text(S.wordEditor.wordContentPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wordRemark,
                        onValueChange = { wordRemark = it },
                        label = { Text(S.wordEditor.wordRemarkOptionalLabel) },
                        placeholder = { Text(S.wordEditor.wordRemarkOptionalPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = wordGroup,
                        onValueChange = { wordGroup = it },
                        label = { Text(S.wordEditor.wordGroupOptionalLabel) },
                        placeholder = { Text(S.wordEditor.wordGroupOptionalPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (distinctWordGroups.isNotEmpty()) {
                        val dialogGroupStripState = rememberLazyListState()
                        StudioHorizontalLazyStrip(
                            state = dialogGroupStripState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                state = dialogGroupStripState,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(distinctWordGroups) { grp ->
                                    StudioFilterChip(
                                        selected = wordGroup == grp,
                                        onClick = { wordGroup = grp },
                                        labelText = grp
                                    )
                                }
                            }
                        }
                    }
                    if (enablePicturePrompts) {
                        val dialogImgFile = remember(wordImagePath, targetProject?.id) {
                            WordImageManager.resolveWordImageFile(context, targetProject?.id ?: 0L, wordImagePath)
                        }
                        val dialogBitmap by rememberWordImageBitmap(dialogImgFile, maxEdgePx = 512)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    if (dialogBitmap != null) {
                                        Image(
                                            bitmap = dialogBitmap!!,
                                            contentDescription = S.wordEditor.wordImagePreviewDesc,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (wordImagePath.isNotBlank()) S.wordEditor.wordImageBoundLabel else S.wordEditor.wordImageOptionalLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { launchSinglePhotoPick { wordImagePath = it } },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 34.dp)
                                        ) {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(S.wordEditor.pickImageShort, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                        if (!DesktopAdaptiveState.isDesktopPlatform) {
                                            OutlinedButton(
                                                onClick = { launchCameraPhotoCapture { wordImagePath = it } },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.defaultMinSize(minHeight = 34.dp)
                                            ) {
                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(S.wordEditor.takePhotoShort, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                            }
                                        }
                                        if (wordImagePath.isNotBlank()) {
                                            TextButton(
                                                onClick = { wordImagePath = "" },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                                                modifier = Modifier.defaultMinSize(minHeight = 34.dp)
                                            ) {
                                                Text(S.common.remove, fontSize = 11.sp, maxLines = 1, softWrap = false)
                                            }
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
                    onClick = {
                        val cleanW = wordText.trim()
                        if (cleanW.isEmpty()) {
                            Toast.makeText(context, S.wordEditor.enterWordContentToast, Toast.LENGTH_SHORT).show()
                        } else {
                            if (idx != null && idx in draftItems.indices) {
                                val old = draftItems[idx]
                                draftItems[idx] = old.copy(
                                    text = cleanW,
                                    remark = wordRemark.trim(),
                                    groupName = wordGroup.trim(),
                                    imagePath = wordImagePath.trim()
                                )
                            } else {
                                draftItems.add(
                                    WordEditorUiItem(
                                        existingWordId = null,
                                        text = cleanW,
                                        remark = wordRemark.trim(),
                                        groupName = wordGroup.trim(),
                                        imagePath = wordImagePath.trim()
                                    )
                                )
                            }
                            if (editTab == 1) {
                                rawTxtContent = buildTxtFromCurrentDrafts()
                            } else {
                                rawTxtContent = ""
                            }
                            rawTxtDirty = false
                            hasUnsavedChanges = true
                            showAddOrEditWordDialog = false
                        }
                    }
                ) {
                    Text(S.common.ok)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOrEditWordDialog = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // 3b. Per-Row Quick Image Assignment Menu Dialog
    if (activeImageMenuRowIndex != null) {
        val rowIdx = activeImageMenuRowIndex!!
        val rowItem = draftItems.getOrNull(rowIdx)
        if (rowItem == null) {
            activeImageMenuRowIndex = null
        } else {
            val rowImgFile = remember(rowItem.imagePath, targetProject?.id) {
                WordImageManager.resolveWordImageFile(context, targetProject?.id ?: 0L, rowItem.imagePath)
            }
            val rowBitmap by rememberWordImageBitmap(rowImgFile, maxEdgePx = 720)
            com.example.ui.components.StudioAlertDialog(
                onDismissRequest = { activeImageMenuRowIndex = null },
                title = {
                    Text(
                        text = S.wordEditor.configImageForWordTitle.withArgs("word" to rowItem.text),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (rowBitmap != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        activeImageMenuRowIndex = null
                                        previewImageTarget = rowItem.text to rowItem.imagePath
                                    }
                            ) {
                                Image(
                                    bitmap = rowBitmap!!,
                                    contentDescription = S.wordEditor.wordImagePreviewForWordDesc.withArgs("word" to rowItem.text),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }
                        } else {
                            Text(
                                text = S.wordEditor.configImageGuideBody,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }

                        Button(
                            onClick = {
                                val targetIdx = rowIdx
                                activeImageMenuRowIndex = null
                                launchSinglePhotoPick { staged ->
                                    if (targetIdx in draftItems.indices) {
                                        draftItems[targetIdx] = draftItems[targetIdx].copy(imagePath = staged)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pick_word_image_from_gallery_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (rowItem.imagePath.isNotBlank()) S.wordEditor.replaceImageFromAlbum else S.wordEditor.pickImageFromAlbum,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!DesktopAdaptiveState.isDesktopPlatform) {
                            FilledTonalButton(
                                onClick = {
                                    val targetIdx = rowIdx
                                    activeImageMenuRowIndex = null
                                    launchCameraPhotoCapture { staged ->
                                        if (targetIdx in draftItems.indices) {
                                            draftItems[targetIdx] = draftItems[targetIdx].copy(imagePath = staged)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("capture_word_image_with_camera_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(S.wordEditor.takePhotoForWord, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (rowItem.imagePath.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        activeImageMenuRowIndex = null
                                        previewImageTarget = rowItem.text to rowItem.imagePath
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(S.wordEditor.viewLargeImage, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (rowIdx in draftItems.indices) {
                                            draftItems[rowIdx] = draftItems[rowIdx].copy(imagePath = "")
                                            hasUnsavedChanges = true
                                        }
                                        activeImageMenuRowIndex = null
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("remove_word_image_button")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(S.wordEditor.removeImage, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeImageMenuRowIndex = null }) {
                        Text(S.common.close)
                    }
                }
            )
        }
    }

    // 3c. Full-Size Image Preview Dialog (supports two-finger pinch-to-zoom & pan)
    com.example.ui.components.AnimatedNullableDialogVisibility(value = previewImageTarget) { (titleText, imgPath) ->
        val fullFile = remember(imgPath, targetProject?.id) {
            WordImageManager.resolveWordImageFile(context, targetProject?.id ?: 0L, imgPath)
        }
        val fullBitmap by rememberWordImageBitmap(fullFile, maxEdgePx = 1280)
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { previewImageTarget = null },
            title = { Text(S.wordEditor.previewImageDialogTitle.withArgs("word" to titleText), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        if (fullBitmap != null) {
                            ZoomableWordImage(
                                bitmap = fullBitmap!!,
                                contentDescription = S.wordEditor.previewOriginalImageDesc.withArgs("word" to titleText),
                                resetKey = imgPath,
                                cornerRadius = 12.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(S.wordEditor.loadingImage, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(
                        text = S.wordEditor.previewZoomHint,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { previewImageTarget = null }) {
                    Text(S.common.close)
                }
            }
        )
    }

    // 4. TXT Format Help Dialog
    com.example.ui.components.AnimatedDialogVisibility(visible = showFormatHelpDialog) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showFormatHelpDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(S.wordEditor.txtFormatHelpTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = S.wordEditor.txtFormatHelpIntro,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = S.wordEditor.txtFormatHelpExample,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(S.wordEditor.txtFormatRule1, fontSize = 12.sp)
                    Text(S.wordEditor.txtFormatRule2, fontSize = 12.sp)
                    Text(S.wordEditor.txtFormatRule3, fontSize = 12.sp)
                    Text(S.wordEditor.txtFormatRule4, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = { showFormatHelpDialog = false }) {
                    Text(S.common.gotIt)
                }
            }
        )
    }

    // 5. Clear All Confirm Dialog
    com.example.ui.components.AnimatedDialogVisibility(visible = showClearConfirmDialog) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(S.wordEditor.clearConfirmTitle, fontWeight = FontWeight.Bold) },
            text = { Text(S.wordEditor.clearConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        draftItems.clear()
                        rawTxtContent = ""
                        rawTxtDirty = false
                        hasUnsavedChanges = true
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(S.wordEditor.confirmClearButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(S.common.cancel)
                }
            }
        )
    }

    // 6. Unsaved Changes Confirmation Dialog (Back or Project Switch)
    com.example.ui.components.AnimatedDialogVisibility(visible = showDiscardConfirmDialog || pendingSwitchTargetProject != null) {
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = {
                showDiscardConfirmDialog = false
                pendingSwitchTargetProject = null
            },
            title = { Text(S.wordEditor.discardChangesTitle, fontWeight = FontWeight.Bold) },
            text = { Text(S.wordEditor.discardChangesBody) },
            confirmButton = {
                Button(
                    onClick = {
                        val switchTarget = pendingSwitchTargetProject
                        showDiscardConfirmDialog = false
                        pendingSwitchTargetProject = null
                        if (switchTarget != null) {
                            loadTargetProject(switchTarget.second)
                        } else {
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(S.wordEditor.discardChangesButton)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        pendingSwitchTargetProject = null
                    }
                ) {
                    Text(S.wordEditor.continueEditingButton)
                }
            }
        )
    }

    // 7. Project Participating Speakers Roster Dialog (supports both draft & saved project modes)
    com.example.ui.components.AnimatedDialogVisibility(visible = showRosterDialog) {
        val initialDraftIds = remember(hasEditedDraftSpeakers, draftAssignedSpeakerIds, allSpeakers) {
            if (hasEditedDraftSpeakers) {
                draftAssignedSpeakerIds
            } else {
                allSpeakers.mapTo(LinkedHashSet()) { it.id }
            }
        }
        ProjectSpeakerRosterDialog(
            viewModel = viewModel,
            project = liveTargetProject,
            projectDisplayName = projectName.trim().ifEmpty { liveTargetProject?.name.orEmpty() },
            draftSelectedSpeakerIds = initialDraftIds,
            onDraftConfirm = { chosenIds ->
                draftAssignedSpeakerIds = chosenIds
                hasEditedDraftSpeakers = true
                hasUnsavedChanges = true
            },
            onStartRecordingWithSpeaker = null,
            onDismiss = { showRosterDialog = false }
        )
    }
}

private sealed class WordEditorGridEntry {
    abstract val key: String

    data class GroupHeader(
        val sectionIndex: Int,
        val groupName: String,
        val itemCount: Int
    ) : WordEditorGridEntry() {
        override val key: String = "grp_hdr_${sectionIndex}_$groupName"
    }

    data class WordCell(
        val realIndex: Int,
        val item: WordEditorUiItem,
        val showInlineGroupBadge: Boolean
    ) : WordEditorGridEntry() {
        override val key: String = "word_${item.localKey}"
    }
}

/**
 * Pushpin-style full-span group divider (`● 组名 (N) ——————`) that forces a clean line break
 * between word groups in multi-column desktop grids and provides clear section anchors on mobile.
 */
@Composable
private fun WordEditorGroupPinDivider(
    groupName: String,
    itemCount: Int,
    isFirstSection: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val displayTitle = groupName.ifBlank { S.recording.ungroupedHeader.trim('【', '】', '[', ']', ' ') }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = if (isFirstSection) 2.dp else 10.dp,
                bottom = 2.dp,
                start = 2.dp,
                end = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Pushpin dot with subtle halo ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (groupName.isNotBlank()) cs.primaryContainer else cs.surfaceContainerHighest
                    )
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (groupName.isNotBlank()) cs.primary else cs.onSurfaceVariant
                    )
            )
        }

        Text(
            text = displayTitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (groupName.isNotBlank()) cs.onSurface else cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = cs.surfaceContainerHigh
        ) {
            Text(
                text = itemCount.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 5.5.dp, vertical = 1.dp)
            )
        }

        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = cs.outlineVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun WordEditorDragGrip(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 10.dp, height = 16.dp)) {
        val r = 1.3.dp.toPx()
        val cols = listOf(size.width * 0.28f, size.width * 0.72f)
        val rows = listOf(size.height * 0.20f, size.height * 0.50f, size.height * 0.80f)
        for (y in rows) {
            for (x in cols) {
                drawCircle(color = tint, radius = r, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun WordEditorDraftRow(
    realIndex: Int,
    totalCount: Int,
    item: WordEditorUiItem,
    projectId: Long,
    enablePicturePrompts: Boolean,
    compactGridCard: Boolean = false,
    showInlineGroupBadge: Boolean = true,
    isDragging: Boolean = false,
    dragEnabled: Boolean = true,
    onEdit: (Int) -> Unit,
    onClickImageSlot: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val (cardInteraction, cardHoveredState) = rememberStudioInteraction()
    val cardHovered by cardHoveredState

    val cardBg by animateColorAsState(
        targetValue = when {
            isDragging -> lerp(cs.surface, cs.primaryContainer, 0.42f)
            cardHovered -> lerp(cs.surface, cs.primaryContainer, 0.16f)
            else -> cs.surface
        },
        animationSpec = StudioMotion.microTween(),
        label = "WordCardBg_${item.localKey}"
    )
    val cardBorder by animateColorAsState(
        targetValue = when {
            isDragging -> cs.primary
            cardHovered -> cs.primary.copy(alpha = 0.42f)
            else -> cs.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "WordCardBorder_${item.localKey}"
    )
    val gripTint by animateColorAsState(
        targetValue = when {
            isDragging -> cs.primary
            cardHovered -> cs.onSurfaceVariant.copy(alpha = 0.78f)
            else -> cs.onSurfaceVariant.copy(alpha = 0.36f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "WordGripTint_${item.localKey}"
    )

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = cardBg,
        border = BorderStroke(if (isDragging) 1.5.dp else 1.dp, cardBorder),
        shadowElevation = if (isDragging) 6.dp else 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .hoverable(cardInteraction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = cardInteraction,
                indication = null
            ) {
                if (!isDragging) onEdit(realIndex)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (compactGridCard) 8.dp else 10.dp,
                    end = if (compactGridCard) 6.dp else 8.dp,
                    top = if (compactGridCard) 9.dp else 10.dp,
                    bottom = if (compactGridCard) 9.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compactGridCard) 7.dp else 9.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (dragEnabled) {
                    WordEditorDragGrip(tint = gripTint)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDragging) cs.primary else cs.primaryContainer,
                    modifier = Modifier.size(if (compactGridCard) 24.dp else 26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${realIndex + 1}",
                            fontSize = if (compactGridCard) 10.5.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDragging) cs.onPrimary else cs.onPrimaryContainer
                        )
                    }
                }

                if (enablePicturePrompts) {
                    val imgFile = remember(item.imagePath, projectId) {
                        if (item.imagePath.isNotBlank()) {
                            WordImageManager.resolveWordImageFile(context, projectId, item.imagePath)
                        } else null
                    }
                    val thumbBitmap by rememberWordImageBitmap(imgFile, maxEdgePx = 256)
                    val imgSize = if (compactGridCard) 40.dp else 46.dp
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (thumbBitmap != null) {
                            cs.surfaceContainerHighest
                        } else {
                            cs.surfaceContainerLow
                        },
                        border = BorderStroke(
                            1.dp,
                            if (thumbBitmap != null) {
                                cs.primary.copy(alpha = 0.45f)
                            } else {
                                cs.outlineVariant.copy(alpha = 0.8f)
                            }
                        ),
                        modifier = Modifier
                            .size(imgSize)
                            .clip(RoundedCornerShape(9.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onClickImageSlot(realIndex) }
                            .testTag("word_row_image_slot_$realIndex")
                    ) {
                        if (thumbBitmap != null) {
                            Image(
                                bitmap = thumbBitmap!!,
                                contentDescription = S.wordEditor.editWordImageDesc.withArgs("word" to item.text),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = S.wordEditor.addWordImageDesc.withArgs("word" to item.text),
                                    tint = cs.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                if (!compactGridCard) {
                                    Text(
                                        text = S.wordEditor.imageButtonShort,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = item.text,
                            fontSize = if (compactGridCard) 15.sp else 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.existingWordId != null && !compactGridCard) {
                            Text(
                                text = S.wordEditor.originalWordLabel,
                                fontSize = 10.sp,
                                color = cs.outline,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    if ((showInlineGroupBadge && item.groupName.isNotBlank()) || item.remark.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (showInlineGroupBadge && item.groupName.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = cs.secondaryContainer
                                ) {
                                    Text(
                                        text = item.groupName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .widthIn(max = 88.dp)
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            if (item.remark.isNotBlank()) {
                                Text(
                                    text = item.remark,
                                    fontSize = 11.5.sp,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!compactGridCard) {
                    IconButton(
                        onClick = { onMoveUp(realIndex) },
                        enabled = realIndex > 0,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(if (realIndex > 0) PointerIcon.Hand else PointerIcon.Default)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = S.wordEditor.moveUpDesc,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoveDown(realIndex) },
                        enabled = realIndex < totalCount - 1,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerHoverIcon(if (realIndex < totalCount - 1) PointerIcon.Hand else PointerIcon.Default)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = S.wordEditor.moveDownDesc,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                val (delInteraction, delHoveredState) = rememberStudioInteraction()
                val delHovered by delHoveredState
                val delTint by animateColorAsState(
                    targetValue = when {
                        delHovered -> cs.error
                        compactGridCard && !cardHovered -> cs.onSurfaceVariant.copy(alpha = 0.45f)
                        else -> cs.error.copy(alpha = 0.82f)
                    },
                    animationSpec = StudioMotion.microTween(),
                    label = "WordDelTint_${item.localKey}"
                )
                IconButton(
                    onClick = { onDelete(realIndex) },
                    interactionSource = delInteraction,
                    modifier = Modifier
                        .size(if (compactGridCard) 30.dp else 32.dp)
                        .hoverable(delInteraction)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = S.wordEditor.deleteRowDesc,
                        tint = delTint,
                        modifier = Modifier.size(if (compactGridCard) 17.dp else 18.dp)
                    )
                }
            }
        }
    }
}

