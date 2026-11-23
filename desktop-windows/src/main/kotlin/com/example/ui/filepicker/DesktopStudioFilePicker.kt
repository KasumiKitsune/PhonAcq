package com.example.ui.filepicker

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.StudioAlertDialog
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioHorizontalScrollStrip
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.StudioMotion
import com.example.ui.components.consumeDialogCardTaps
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.ui.components.studioRotateOn
import com.example.ui.components.studioTactile
import com.example.ui.theme.LocalPhonAcqExtendedColors
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.util.prefs.Preferences
import javax.swing.filechooser.FileSystemView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DesktopFilePickerRequest(
    val id: Long,
    val mode: DesktopFilePickerMode,
    val title: String,
    val initialDirectory: File,
    val suggestedFileName: String = "",
    val requiredSaveExtension: String = "",
    val filters: List<FileFilterCategory>,
    val preferGridByDefault: Boolean = false,
    val maxSelectionCount: Int = 100,
    val onComplete: (List<File>) -> Unit
)

enum class BrowserSortField {
    NAME,
    MODIFIED,
    TYPE,
    SIZE
}

private enum class PickerBrowserViewMode {
    EMPTY,
    GRID,
    LIST
}

private data class QuickPlaceItem(
    val label: String,
    val icon: ImageVector,
    val dir: File,
    val subtitle: String? = null
)

private data class VolumeDriveItem(
    val root: File,
    val displayName: String,
    val totalBytes: Long,
    val freeBytes: Long
)

object DesktopFilePickerController {
    private val prefs = Preferences.userNodeForPackage(DesktopFilePickerController::class.java)
    private const val KEY_LAST_OPEN_DIR = "last_open_dir_v2"
    private const val KEY_LAST_SAVE_DIR = "last_save_dir_v2"
    private const val KEY_RECENT_DIRS = "recent_dirs_v2"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nextReqId = 1L

    var mainWindow: ComposeWindow? = null
        private set

    var activeRequest by mutableStateOf<DesktopFilePickerRequest?>(null)
        private set

    val recentDirectories = mutableStateListOf<File>()

    init {
        loadRecentDirectoriesFromPrefs()
    }

    fun attachMainWindow(window: ComposeWindow?) {
        mainWindow = window
    }

    fun buildImportFilters(wantImageOnly: Boolean): List<FileFilterCategory> {
        return if (wantImageOnly) {
            listOf(
                FileFilterCategory(
                    id = "img_all",
                    label = "全部图片",
                    extensions = listOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
                ),
                FileFilterCategory(
                    id = "img_png_webp",
                    label = "PNG / WebP",
                    extensions = listOf("png", "webp")
                ),
                FileFilterCategory(
                    id = "img_jpg",
                    label = "JPG 照片",
                    extensions = listOf("jpg", "jpeg")
                ),
                FileFilterCategory(
                    id = "all",
                    label = "所有文件 (*.*)",
                    extensions = emptyList()
                )
            )
        } else {
            listOf(
                FileFilterCategory(
                    id = "supported_all",
                    label = "全部支持格式",
                    extensions = listOf(
                        "xlsx", "csv", "tsv", "txt", "docx", "json", "ptwl",
                        "wav", "mp3", "m4a", "aac", "flac", "ogg", "aiff",
                        "zip", "teproj",
                        "png", "jpg", "jpeg", "webp", "bmp",
                        "textgrid"
                    )
                ),
                FileFilterCategory(
                    id = "spreadsheets",
                    label = "字表 (.xlsx/.csv/.txt/.docx/.ptwl)",
                    extensions = listOf("xlsx", "csv", "tsv", "txt", "docx", "json", "ptwl")
                ),
                FileFilterCategory(
                    id = "audio",
                    label = "录音 (.wav/.mp3/.m4a/.flac)",
                    extensions = listOf("wav", "mp3", "m4a", "aac", "flac", "ogg", "aiff")
                ),
                FileFilterCategory(
                    id = "archives",
                    label = "项目包 (.zip/.teproj)",
                    extensions = listOf("zip", "teproj")
                ),
                FileFilterCategory(
                    id = "images",
                    label = "配图 (.png/.jpg/.webp)",
                    extensions = listOf("png", "jpg", "jpeg", "webp", "bmp")
                ),
                FileFilterCategory(
                    id = "all",
                    label = "所有文件 (*.*)",
                    extensions = emptyList()
                )
            )
        }
    }

    fun buildSaveFilters(suggestedFileName: String, mimeType: String): Pair<String, List<FileFilterCategory>> {
        val rawExt = suggestedFileName.substringAfterLast('.', "").trim()
        val extLower = rawExt.lowercase()
        val resolvedExt = when {
            rawExt.isNotEmpty() -> rawExt
            mimeType.contains("zip", ignoreCase = true) -> "zip"
            mimeType.contains("text", ignoreCase = true) -> "TextGrid"
            else -> ""
        }
        val primaryFilter = when (extLower) {
            "teproj" -> FileFilterCategory("save_teproj", "PhonTracer 工程 (*.teproj)", listOf("teproj"))
            "zip" -> FileFilterCategory("save_zip", "语料压缩包 (*.zip)", listOf("zip"))
            "collection" -> FileFilterCategory("save_collection", "Praat 集合 (*.Collection)", listOf("collection"))
            "textgrid" -> FileFilterCategory("save_tg", "Praat 标注 (*.TextGrid)", listOf("textgrid"))
            "xlsx" -> FileFilterCategory("save_xlsx", "Excel 表格 (*.xlsx)", listOf("xlsx"))
            "csv" -> FileFilterCategory("save_csv", "CSV 表格 (*.csv)", listOf("csv"))
            else -> if (resolvedExt.isNotEmpty()) {
                FileFilterCategory("save_ext", "${resolvedExt.uppercase()} 文件 (*.$resolvedExt)", listOf(resolvedExt.lowercase()))
            } else {
                FileFilterCategory("save_all", "所有文件 (*.*)", emptyList())
            }
        }
        val list = if (primaryFilter.extensions.isEmpty()) {
            listOf(primaryFilter)
        } else {
            listOf(primaryFilter, FileFilterCategory("all", "所有文件 (*.*)", emptyList()))
        }
        return resolvedExt to list
    }

    fun launchOpenSingle(
        title: String,
        wantImageOnly: Boolean,
        onResult: (File?) -> Unit
    ) {
        val startDir = resolveDefaultDirectory(isSave = false, preferPictures = wantImageOnly)
        activeRequest = DesktopFilePickerRequest(
            id = nextReqId++,
            mode = DesktopFilePickerMode.OPEN_SINGLE,
            title = title,
            initialDirectory = startDir,
            filters = buildImportFilters(wantImageOnly),
            preferGridByDefault = wantImageOnly,
            maxSelectionCount = 1,
            onComplete = { files ->
                files.firstOrNull()?.parentFile?.let { rememberVisitedDirectory(it, isSave = false) }
                onResult(files.firstOrNull())
            }
        )
    }

    fun launchOpenMultiple(
        title: String,
        wantImageOnly: Boolean,
        maxItems: Int,
        onResult: (List<File>) -> Unit
    ) {
        val startDir = resolveDefaultDirectory(isSave = false, preferPictures = wantImageOnly)
        activeRequest = DesktopFilePickerRequest(
            id = nextReqId++,
            mode = DesktopFilePickerMode.OPEN_MULTIPLE,
            title = title,
            initialDirectory = startDir,
            filters = buildImportFilters(wantImageOnly),
            preferGridByDefault = wantImageOnly,
            maxSelectionCount = maxItems.coerceAtLeast(1),
            onComplete = { files ->
                files.firstOrNull()?.parentFile?.let { rememberVisitedDirectory(it, isSave = false) }
                onResult(files)
            }
        )
    }

    fun launchOpenDirectory(
        title: String,
        onResult: (File?) -> Unit
    ) {
        val startDir = resolveDefaultDirectory(isSave = false, preferPictures = false)
        activeRequest = DesktopFilePickerRequest(
            id = nextReqId++,
            mode = DesktopFilePickerMode.OPEN_DIRECTORY,
            title = title,
            initialDirectory = startDir,
            filters = listOf(FileFilterCategory("folders", "文件夹", emptyList())),
            preferGridByDefault = false,
            maxSelectionCount = 1,
            onComplete = { files ->
                val dir = files.firstOrNull { it.isDirectory }
                if (dir != null) rememberVisitedDirectory(dir, isSave = false)
                onResult(dir)
            }
        )
    }

    fun launchSaveFile(
        title: String,
        suggestedFileName: String,
        mimeType: String,
        onResult: (File?) -> Unit
    ) {
        val startDir = resolveDefaultDirectory(isSave = true, preferPictures = false)
        val (reqExt, saveFilters) = buildSaveFilters(suggestedFileName, mimeType)
        activeRequest = DesktopFilePickerRequest(
            id = nextReqId++,
            mode = DesktopFilePickerMode.SAVE_FILE,
            title = title,
            initialDirectory = startDir,
            suggestedFileName = suggestedFileName,
            requiredSaveExtension = reqExt,
            filters = saveFilters,
            preferGridByDefault = false,
            maxSelectionCount = 1,
            onComplete = { files ->
                files.firstOrNull()?.parentFile?.let { rememberVisitedDirectory(it, isSave = true) }
                onResult(files.firstOrNull())
            }
        )
    }

    fun dismissAndCancel() {
        val req = activeRequest ?: return
        activeRequest = null
        req.onComplete(emptyList())
    }

    fun completeSelection(files: List<File>) {
        val req = activeRequest ?: return
        activeRequest = null
        req.onComplete(files)
    }

    fun switchToSystemNativeWindow(
        currentDirectory: File,
        currentSaveFileName: String,
        activeFilterIndex: Int
    ) {
        val req = activeRequest ?: return
        activeRequest = null
        val win = mainWindow
        val hwnd = try {
            win?.windowHandle
        } catch (_: Throwable) {
            null
        }
        ioScope.launch {
            // Allow built-in modal 148ms exit animation to settle smoothly before opening OS dialog
            delay(150)
            val chosen = ModernNativeFileDialogs.showNativeFileDialog(
                ownerFrame = win,
                windowHandle = hwnd,
                mode = req.mode,
                title = req.title,
                initialDirectory = currentDirectory,
                suggestedFileName = currentSaveFileName.ifBlank { req.suggestedFileName },
                filters = req.filters,
                activeFilterIndex = activeFilterIndex,
                maxSelectionCount = req.maxSelectionCount
            )
            chosen.firstOrNull()?.parentFile?.let {
                rememberVisitedDirectory(it, isSave = req.mode == DesktopFilePickerMode.SAVE_FILE)
            }
            req.onComplete(chosen)
        }
    }

    fun rememberVisitedDirectory(dir: File, isSave: Boolean) {
        if (!dir.exists() || !dir.isDirectory) return
        val path = dir.absolutePath
        prefs.put(if (isSave) KEY_LAST_SAVE_DIR else KEY_LAST_OPEN_DIR, path)
        recentDirectories.removeAll { it.absolutePath.equals(path, ignoreCase = true) }
        recentDirectories.add(0, dir)
        while (recentDirectories.size > 5) {
            recentDirectories.removeAt(recentDirectories.lastIndex)
        }
        val serialized = recentDirectories.joinToString("\n") { it.absolutePath }
        prefs.put(KEY_RECENT_DIRS, serialized)
    }

    private fun loadRecentDirectoriesFromPrefs() {
        val raw = prefs.get(KEY_RECENT_DIRS, "")
        if (raw.isBlank()) return
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
            .take(5)
            .forEach { recentDirectories.add(it) }
    }

    private fun resolveDefaultDirectory(isSave: Boolean, preferPictures: Boolean): File {
        val savedKey = if (isSave) KEY_LAST_SAVE_DIR else KEY_LAST_OPEN_DIR
        val savedPath = prefs.get(savedKey, null)
        if (!savedPath.isNullOrBlank()) {
            val savedDir = File(savedPath)
            if (savedDir.exists() && savedDir.isDirectory) return savedDir
        }

        val home = File(System.getProperty("user.home", "."))
        val candidates = buildList {
            if (preferPictures) {
                add(File(home, "Pictures"))
                add(File(home, "图片"))
            }
            add(File(home, "Desktop"))
            add(File(home, "桌面"))
            add(File(home, "Downloads"))
            add(File(home, "下载"))
            add(File(home, "Documents"))
            add(File(home, "文档"))
            add(home)
        }
        return candidates.firstOrNull { it.exists() && it.isDirectory } ?: home
    }
}

/**
 * Mounts the built-in Material 3 Acoustic Studio File Picker inside `DesktopMain.kt`.
 * Uses [StudioModalDialog] so both opening and closing share the exact smooth spring + fade transitions.
 */
@Composable
fun DesktopStudioFilePickerHost() {
    val request = DesktopFilePickerController.activeRequest
    if (request != null) {
        StudioModalDialog(
            onDismissRequest = { DesktopFilePickerController.dismissAndCancel() },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            DesktopStudioFilePickerSurface(request = request)
        }
    }
}

@Composable
private fun DesktopStudioFilePickerSurface(request: DesktopFilePickerRequest) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val dialogFocusRequester = remember { FocusRequester() }
    val pathFieldFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    val isSaveMode = request.mode == DesktopFilePickerMode.SAVE_FILE
    val isMultiMode = request.mode == DesktopFilePickerMode.OPEN_MULTIPLE
    val isDirMode = request.mode == DesktopFilePickerMode.OPEN_DIRECTORY

    var currentDir by remember(request.id) { mutableStateOf(request.initialDirectory) }
    val historyBackStack = remember(request.id) { mutableStateListOf<File>() }
    val historyForwardStack = remember(request.id) { mutableStateListOf<File>() }

    var activeFilterIndex by remember(request.id) { mutableIntStateOf(0) }
    var searchQuery by remember(request.id) { mutableStateOf("") }
    var showHiddenFiles by remember(request.id) { mutableStateOf(false) }
    var isGridView by remember(request.id) { mutableStateOf(request.preferGridByDefault) }
    var showInspectorPane by remember(request.id) { mutableStateOf(true) }

    var sortField by remember(request.id) { mutableStateOf(BrowserSortField.NAME) }
    var sortAscending by remember(request.id) { mutableStateOf(true) }

    var isEditingPathBar by remember(request.id) { mutableStateOf(false) }
    var editablePathText by remember(request.id) { mutableStateOf(request.initialDirectory.absolutePath) }
    var pathErrorHint by remember(request.id) { mutableStateOf<String?>(null) }

    var showNewFolderInput by remember(request.id) { mutableStateOf(false) }
    var newFolderName by remember(request.id) { mutableStateOf("") }

    var saveFileNameInput by remember(request.id) { mutableStateOf(request.suggestedFileName) }
    var pendingOverwriteTarget by remember(request.id) { mutableStateOf<File?>(null) }

    val selectedFiles = remember(request.id) { mutableStateListOf<File>() }
    var focusedSingleItem by remember(request.id) { mutableStateOf<File?>(null) }

    var reloadTick by remember(request.id) { mutableIntStateOf(0) }
    var lastClickPath by remember(request.id) { mutableStateOf<String?>(null) }
    var lastClickTimeMs by remember(request.id) { mutableStateOf(0L) }

    var isExternalDragHovering by remember(request.id) { mutableStateOf(false) }

    fun navigateToDirectory(target: File, pushHistory: Boolean = true) {
        val canonical = try {
            target.canonicalFile
        } catch (_: Exception) {
            target.absoluteFile
        }
        if (!canonical.exists() || !canonical.isDirectory) {
            pathErrorHint = "路径不存在或不是文件夹"
            return
        }
        pathErrorHint = null
        isEditingPathBar = false
        if (pushHistory && canonical.absolutePath != currentDir.absolutePath) {
            historyBackStack.add(currentDir)
            historyForwardStack.clear()
        }
        currentDir = canonical
        editablePathText = canonical.absolutePath
        searchQuery = ""
        if (!isMultiMode) {
            selectedFiles.clear()
        }
        focusedSingleItem = null
        DesktopFilePickerController.rememberVisitedDirectory(canonical, isSave = isSaveMode)
    }

    // Attach OS Drag-and-Drop listener onto ComposeWindow while picker is open
    DisposableEffect(request.id) {
        val win = DesktopFilePickerController.mainWindow
        val previousDropTarget = win?.dropTarget
        if (win != null) {
            win.dropTarget = DropTarget(
                win,
                DnDConstants.ACTION_COPY_OR_MOVE,
                object : DropTargetAdapter() {
                    override fun dragEnter(dtde: DropTargetDragEvent) {
                        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrag(DnDConstants.ACTION_COPY)
                            isExternalDragHovering = true
                        } else {
                            dtde.rejectDrag()
                        }
                    }

                    override fun dragOver(dtde: DropTargetDragEvent) {
                        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrag(DnDConstants.ACTION_COPY)
                            isExternalDragHovering = true
                        }
                    }

                    override fun dragExit(dte: DropTargetEvent) {
                        isExternalDragHovering = false
                    }

                    override fun drop(dtde: DropTargetDropEvent) {
                        isExternalDragHovering = false
                        try {
                            if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                dtde.rejectDrop()
                                return
                            }
                            dtde.acceptDrop(DnDConstants.ACTION_COPY)
                            @Suppress("UNCHECKED_CAST")
                            val dropped = (dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>)
                                .orEmpty()
                            dtde.dropComplete(true)
                            if (dropped.isEmpty()) return

                            if (isDirMode) {
                                val firstDir = dropped.firstOrNull { it.isDirectory } ?: dropped.firstOrNull()?.parentFile
                                if (firstDir != null && firstDir.isDirectory) {
                                    DesktopFilePickerController.completeSelection(listOf(firstDir))
                                }
                                return
                            }
                            if (dropped.size == 1 && dropped[0].isDirectory) {
                                navigateToDirectory(dropped[0])
                                return
                            }
                            val validFiles = if (isMultiMode) {
                                dropped.filter { it.isFile || it.isDirectory }
                            } else {
                                dropped.filter { it.isFile }
                            }
                            if (validFiles.isEmpty()) return

                            if (isSaveMode) {
                                val first = validFiles.first()
                                first.parentFile?.let { navigateToDirectory(it) }
                                saveFileNameInput = first.name
                            } else if (isMultiMode) {
                                DesktopFilePickerController.completeSelection(
                                    validFiles.take(request.maxSelectionCount)
                                )
                            } else {
                                DesktopFilePickerController.completeSelection(listOf(validFiles.first()))
                            }
                        } catch (_: Exception) {
                            dtde.dropComplete(false)
                        }
                    }
                },
                true
            )
        }
        onDispose {
            isExternalDragHovering = false
            if (win != null) {
                win.dropTarget = previousDropTarget
            }
        }
    }

    LaunchedEffect(request.id) {
        try {
            dialogFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    val rawDirectoryItems by produceState<List<File>>(
        initialValue = emptyList(),
        key1 = currentDir.absolutePath,
        key2 = showHiddenFiles,
        key3 = reloadTick
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                currentDir.listFiles()
                    ?.asSequence()
                    ?.filter { showHiddenFiles || (!it.isHidden && !it.name.startsWith(".")) }
                    ?.toList()
                    .orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    val filterCounts = remember(rawDirectoryItems, request.filters) {
        request.filters.map { cat ->
            rawDirectoryItems.count { f -> f.isFile && cat.matches(f) }
        }
    }

    val activeFilter = request.filters.getOrElse(activeFilterIndex) {
        FileFilterCategory("all", "所有文件 (*.*)", emptyList())
    }

    val displayedItems = remember(
        rawDirectoryItems,
        activeFilter,
        searchQuery,
        sortField,
        sortAscending
    ) {
        val q = searchQuery.trim().lowercase()
        val filtered = rawDirectoryItems.filter { f ->
            val matchesFilter = if (isDirMode) f.isDirectory else (f.isDirectory || activeFilter.matches(f))
            val matchesSearch = q.isEmpty() || f.name.lowercase().contains(q)
            matchesFilter && matchesSearch
        }

        val comparator = Comparator<File> { a, b ->
            // Folders always cleanly grouped before files
            if (a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val rawCmp = when (sortField) {
                BrowserSortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                BrowserSortField.MODIFIED -> a.lastModified().compareTo(b.lastModified())
                BrowserSortField.TYPE -> {
                    val extCmp = a.extension.compareTo(b.extension, ignoreCase = true)
                    if (extCmp != 0) extCmp else a.name.compareTo(b.name, ignoreCase = true)
                }
                BrowserSortField.SIZE -> {
                    val lenA = if (a.isDirectory) 0L else a.length()
                    val lenB = if (b.isDirectory) 0L else b.length()
                    lenA.compareTo(lenB)
                }
            }
            if (sortAscending) rawCmp else -rawCmp
        }
        filtered.sortedWith(comparator)
    }

    val hiddenByFilterCount = remember(rawDirectoryItems, displayedItems, searchQuery) {
        if (searchQuery.isNotBlank()) 0
        else (rawDirectoryItems.count { it.isFile } - displayedItems.count { it.isFile }).coerceAtLeast(0)
    }

    val quickPlaces = remember { buildQuickPlaces() }
    val mountedVolumes by produceState<List<VolumeDriveItem>>(initialValue = emptyList(), key1 = reloadTick) {
        value = withContext(Dispatchers.IO) { discoverMountedVolumes() }
    }

    val inspectorTargetFile = focusedSingleItem ?: selectedFiles.lastOrNull()
    val inspectorPreview by produceState<FileInspectorPreview?>(
        initialValue = null,
        key1 = inspectorTargetFile?.absolutePath,
        key2 = inspectorTargetFile?.lastModified()
    ) {
        val target = inspectorTargetFile
        value = if (target != null && target.exists()) {
            DesktopFileInspector.inspectFile(target)
        } else {
            null
        }
    }

    fun resolveFinalSaveFile(): File? {
        val trimmed = saveFileNameInput.trim()
        if (trimmed.isEmpty()) return null
        val reqExt = request.requiredSaveExtension
        val finalName = if (reqExt.isNotEmpty() && !trimmed.contains('.')) {
            "$trimmed.$reqExt"
        } else if (reqExt.isNotEmpty() && !trimmed.lowercase().endsWith(".${reqExt.lowercase()}")) {
            "$trimmed.$reqExt"
        } else {
            trimmed
        }
        return File(currentDir, finalName)
    }

    fun triggerPrimaryConfirm() {
        if (isSaveMode) {
            val out = resolveFinalSaveFile() ?: return
            if (out.exists() && out.isDirectory) {
                navigateToDirectory(out)
                return
            }
            if (out.exists()) {
                pendingOverwriteTarget = out
            } else {
                DesktopFilePickerController.completeSelection(listOf(out))
            }
        } else if (isDirMode) {
            val targetDir = focusedSingleItem?.takeIf { it.isDirectory } ?: currentDir
            DesktopFilePickerController.completeSelection(listOf(targetDir))
        } else if (isMultiMode) {
            if (selectedFiles.isNotEmpty()) {
                DesktopFilePickerController.completeSelection(selectedFiles.toList())
            } else if (focusedSingleItem?.isDirectory == true) {
                navigateToDirectory(focusedSingleItem!!)
            }
        } else {
            val target = selectedFiles.firstOrNull() ?: focusedSingleItem
            if (target != null) {
                if (target.isDirectory) {
                    navigateToDirectory(target)
                } else if (target.isFile) {
                    DesktopFilePickerController.completeSelection(listOf(target))
                }
            }
        }
    }

    fun handleItemClick(item: File, isCtrlOrShiftHeld: Boolean = false) {
        val now = System.currentTimeMillis()
        val isDoubleClick = lastClickPath == item.absolutePath && (now - lastClickTimeMs) <= 360L
        lastClickPath = item.absolutePath
        lastClickTimeMs = now

        focusedSingleItem = item

        if (isDoubleClick) {
            if (item.isDirectory) {
                navigateToDirectory(item)
            } else if (isSaveMode) {
                saveFileNameInput = item.name
                pendingOverwriteTarget = item
            } else if (isMultiMode) {
                if (selectedFiles.none { it.absolutePath == item.absolutePath }) {
                    if (selectedFiles.size < request.maxSelectionCount) {
                        selectedFiles.add(item)
                    }
                }
                DesktopFilePickerController.completeSelection(selectedFiles.toList())
            } else {
                DesktopFilePickerController.completeSelection(listOf(item))
            }
            return
        }

        if (item.isDirectory) {
            if (!isMultiMode) {
                selectedFiles.clear()
            }
        } else {
            if (isSaveMode) {
                selectedFiles.clear()
                selectedFiles.add(item)
                saveFileNameInput = item.name
            } else if (isMultiMode) {
                val existingIdx = selectedFiles.indexOfFirst { it.absolutePath == item.absolutePath }
                if (isCtrlOrShiftHeld) {
                    if (existingIdx >= 0) {
                        selectedFiles.removeAt(existingIdx)
                    } else if (selectedFiles.size < request.maxSelectionCount) {
                        selectedFiles.add(item)
                    }
                } else {
                    if (existingIdx >= 0 && selectedFiles.size == 1) {
                        selectedFiles.clear()
                    } else if (existingIdx >= 0) {
                        selectedFiles.removeAt(existingIdx)
                    } else if (selectedFiles.size < request.maxSelectionCount) {
                        selectedFiles.add(item)
                    }
                }
            } else {
                selectedFiles.clear()
                selectedFiles.add(item)
            }
        }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val dialogWidth = (maxWidth * 0.94f).coerceAtMost(960.dp)
        val dialogHeight = (maxHeight * 0.90f).coerceAtMost(640.dp)
        val isCompactSidebar = dialogWidth < 680.dp
        val isCompactBottomBar = dialogWidth < 580.dp
        val showRightInspector = showInspectorPane && dialogWidth >= 820.dp

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = cs.surface,
            tonalElevation = 4.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
            modifier = Modifier
                .size(dialogWidth, dialogHeight)
                .consumeDialogCardTaps()
                .focusRequester(dialogFocusRequester)
                .focusable()
                .testTag("desktop_studio_file_picker")
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val ctrl = ev.isCtrlPressed || ev.isMetaPressed
                    when {
                        ctrl && ev.key == Key.L -> {
                            editablePathText = currentDir.absolutePath
                            pathErrorHint = null
                            isEditingPathBar = true
                            scope.launch {
                                delay(20)
                                try {
                                    pathFieldFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                            }
                            true
                        }
                        ctrl && ev.key == Key.F -> {
                            scope.launch {
                                try {
                                    searchFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                }
                            }
                            true
                        }
                        ev.isAltPressed && ev.key == Key.DirectionLeft -> {
                            if (historyBackStack.isNotEmpty()) {
                                val prev = historyBackStack.removeAt(historyBackStack.lastIndex)
                                historyForwardStack.add(currentDir)
                                navigateToDirectory(prev, pushHistory = false)
                            }
                            true
                        }
                        ev.isAltPressed && ev.key == Key.DirectionRight -> {
                            if (historyForwardStack.isNotEmpty()) {
                                val next = historyForwardStack.removeAt(historyForwardStack.lastIndex)
                                historyBackStack.add(currentDir)
                                navigateToDirectory(next, pushHistory = false)
                            }
                            true
                        }
                        ev.isAltPressed && ev.key == Key.DirectionUp -> {
                            currentDir.parentFile?.let { navigateToDirectory(it) }
                            true
                        }
                        ev.key == Key.F5 -> {
                            reloadTick++
                            true
                        }
                        !isEditingPathBar && !showNewFolderInput && !isSaveMode && ev.key == Key.DirectionDown -> {
                            if (displayedItems.isNotEmpty()) {
                                val curIdx = displayedItems.indexOfFirst { it.absolutePath == focusedSingleItem?.absolutePath }
                                val nextIdx = (curIdx + 1).coerceIn(0, displayedItems.lastIndex)
                                val item = displayedItems[nextIdx]
                                focusedSingleItem = item
                                if (item.isFile && !isMultiMode) {
                                    selectedFiles.clear()
                                    selectedFiles.add(item)
                                }
                                scope.launch { listState.animateScrollToItem(nextIdx) }
                            }
                            true
                        }
                        !isEditingPathBar && !showNewFolderInput && !isSaveMode && ev.key == Key.DirectionUp -> {
                            if (displayedItems.isNotEmpty()) {
                                val curIdx = displayedItems.indexOfFirst { it.absolutePath == focusedSingleItem?.absolutePath }
                                val prevIdx = if (curIdx <= 0) 0 else curIdx - 1
                                val item = displayedItems[prevIdx]
                                focusedSingleItem = item
                                if (item.isFile && !isMultiMode) {
                                    selectedFiles.clear()
                                    selectedFiles.add(item)
                                }
                                scope.launch { listState.animateScrollToItem(prevIdx) }
                            }
                            true
                        }
                        !isEditingPathBar && !showNewFolderInput && (ev.key == Key.Enter || ev.key == Key.NumPadEnter) -> {
                            triggerPrimaryConfirm()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cs.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (request.mode) {
                                            DesktopFilePickerMode.SAVE_FILE -> Icons.Default.Download
                                            DesktopFilePickerMode.OPEN_DIRECTORY -> Icons.Default.FolderSpecial
                                            DesktopFilePickerMode.OPEN_MULTIPLE -> Icons.Default.UploadFile
                                            DesktopFilePickerMode.OPEN_SINGLE -> Icons.Default.UploadFile
                                        },
                                        contentDescription = null,
                                        tint = cs.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = request.title,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = cs.secondaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = when (request.mode) {
                                                DesktopFilePickerMode.SAVE_FILE -> "导出保存"
                                                DesktopFilePickerMode.OPEN_DIRECTORY -> "选择整个文件夹"
                                                DesktopFilePickerMode.OPEN_MULTIPLE -> "可多选 (支持拖拽)"
                                                DesktopFilePickerMode.OPEN_SINGLE -> "支持拖拽文件直达"
                                            },
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Grid vs Table List Toggle
                            PickerIconPillButton(
                                icon = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                tooltipDesc = if (isGridView) "切换列表视图" else "切换缩略图网格",
                                active = isGridView,
                                onClick = { isGridView = !isGridView }
                            )
                            // New Folder Button
                            PickerIconPillButton(
                                icon = Icons.Default.CreateNewFolder,
                                tooltipDesc = "新建文件夹",
                                active = showNewFolderInput,
                                onClick = {
                                    showNewFolderInput = !showNewFolderInput
                                    newFolderName = ""
                                }
                            )
                            if (dialogWidth >= 820.dp) {
                                PickerIconPillButton(
                                    icon = Icons.Default.Info,
                                    tooltipDesc = "右侧详情预览栏",
                                    active = showInspectorPane,
                                    onClick = { showInspectorPane = !showInspectorPane }
                                )
                            }
                            IconButton(
                                onClick = { DesktopFilePickerController.dismissAndCancel() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 2. Omnibar: Nav Buttons + Breadcrumbs/Address Bar + Instant Search
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            PickerNavIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "后退",
                                enabled = historyBackStack.isNotEmpty(),
                                onClick = {
                                    if (historyBackStack.isNotEmpty()) {
                                        val prev = historyBackStack.removeAt(historyBackStack.lastIndex)
                                        historyForwardStack.add(currentDir)
                                        navigateToDirectory(prev, pushHistory = false)
                                    }
                                }
                            )
                            PickerNavIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "前进",
                                enabled = historyForwardStack.isNotEmpty(),
                                onClick = {
                                    if (historyForwardStack.isNotEmpty()) {
                                        val next = historyForwardStack.removeAt(historyForwardStack.lastIndex)
                                        historyBackStack.add(currentDir)
                                        navigateToDirectory(next, pushHistory = false)
                                    }
                                }
                            )
                            PickerNavIconButton(
                                icon = Icons.Default.ArrowUpward,
                                contentDescription = "上一级目录",
                                enabled = currentDir.parentFile != null,
                                onClick = {
                                    currentDir.parentFile?.let { navigateToDirectory(it) }
                                }
                            )
                            PickerNavIconButton(
                                icon = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                enabled = true,
                                onClick = { reloadTick++ }
                            )
                        }

                        // Breadcrumb / Editable Path Box
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = cs.surfaceContainerLow,
                            border = BorderStroke(
                                1.dp,
                                when {
                                    pathErrorHint != null -> cs.error
                                    isEditingPathBar -> cs.primary
                                    else -> cs.outlineVariant.copy(alpha = 0.65f)
                                }
                            ),
                            modifier = Modifier
                                .weight(if (isCompactSidebar) 1.25f else 1f)
                                .height(36.dp)
                        ) {
                            AnimatedContent(
                                targetState = isEditingPathBar,
                                transitionSpec = { StudioMotion.subtleFadeScale() },
                                label = "PickerPathBarMode",
                                modifier = Modifier.fillMaxSize()
                            ) { editingPath ->
                                if (editingPath) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = cs.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        BasicTextField(
                                            value = editablePathText,
                                            onValueChange = {
                                                editablePathText = it
                                                pathErrorHint = null
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = cs.onSurface,
                                                fontSize = 12.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            cursorBrush = SolidColor(cs.primary),
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(pathFieldFocusRequester)
                                                .onPreviewKeyEvent { keyEv ->
                                                    if (keyEv.type == KeyEventType.KeyDown) {
                                                        if (keyEv.key == Key.Enter || keyEv.key == Key.NumPadEnter) {
                                                            val expanded = expandUserPath(editablePathText)
                                                            if (expanded.exists() && expanded.isFile) {
                                                                expanded.parentFile?.let { navigateToDirectory(it) }
                                                                handleItemClick(expanded)
                                                            } else {
                                                                navigateToDirectory(expanded)
                                                            }
                                                            return@onPreviewKeyEvent true
                                                        } else if (keyEv.key == Key.Escape) {
                                                            isEditingPathBar = false
                                                            pathErrorHint = null
                                                            editablePathText = currentDir.absolutePath
                                                            return@onPreviewKeyEvent true
                                                        }
                                                    }
                                                    false
                                                }
                                        )
                                        if (pathErrorHint != null) {
                                            Text(
                                                text = pathErrorHint!!,
                                                fontSize = 11.sp,
                                                color = cs.error,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "跳转",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .clickable {
                                                    navigateToDirectory(expandUserPath(editablePathText))
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    val segments = remember(currentDir.absolutePath) { buildPathSegments(currentDir) }
                                    val crumbScroll = rememberScrollState()
                                    LaunchedEffect(currentDir.absolutePath) {
                                        crumbScroll.animateScrollTo(crumbScroll.maxValue)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StudioHorizontalScrollStrip(
                                            state = crumbScroll,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(crumbScroll),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                segments.forEachIndexed { idx, (label, segFile) ->
                                                    val isLast = idx == segments.lastIndex
                                                    Surface(
                                                        shape = RoundedCornerShape(7.dp),
                                                        color = if (isLast) cs.primaryContainer.copy(alpha = 0.65f) else Color.Transparent,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(7.dp))
                                                            .pointerHoverIcon(PointerIcon.Hand)
                                                            .clickable { navigateToDirectory(segFile) }
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isLast) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                                            maxLines = 1,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                    if (!isLast) {
                                                        Icon(
                                                            imageVector = Icons.Default.ChevronRight,
                                                            contentDescription = null,
                                                            tint = cs.outline,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                editablePathText = currentDir.absolutePath
                                                pathErrorHint = null
                                                isEditingPathBar = true
                                                scope.launch {
                                                    delay(20)
                                                    try {
                                                        pathFieldFocusRequester.requestFocus()
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "编辑路径 (Ctrl+L)",
                                                tint = cs.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Instant Search Box
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = cs.surfaceContainerLow,
                            border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f)),
                            modifier = (if (isCompactSidebar) Modifier.weight(1f) else Modifier.width(205.dp))
                                .height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "筛选当前目录 (Ctrl+F)",
                                            fontSize = 11.5.sp,
                                            color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = cs.onSurface,
                                            fontSize = 12.sp
                                        ),
                                        cursorBrush = SolidColor(cs.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester)
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清空搜索",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { searchQuery = "" }
                                    )
                                }
                            }
                        }
                    }

                    // Optional Inline New Folder Bar
                    AnimatedVisibility(
                        visible = showNewFolderInput,
                        enter = StudioMotion.verticalPanelEnter,
                        exit = StudioMotion.verticalPanelExit
                    ) {
                        Surface(
                            color = cs.primaryContainer.copy(alpha = 0.32f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text("新建文件夹：", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                                OutlinedTextField(
                                    value = newFolderName,
                                    onValueChange = { newFolderName = it },
                                    placeholder = { Text("输入文件夹名称", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val clean = newFolderName.trim()
                                        if (clean.isNotEmpty()) {
                                            val created = File(currentDir, clean)
                                            if (created.exists() || created.mkdirs()) {
                                                showNewFolderInput = false
                                                newFolderName = ""
                                                reloadTick++
                                                navigateToDirectory(created)
                                            }
                                        }
                                    },
                                    enabled = newFolderName.isNotBlank(),
                                    shape = RoundedCornerShape(9.dp)
                                ) {
                                    Text("创建并进入", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { showNewFolderInput = false }) {
                                    Text("取消", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 3. Format Filter Pills Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val formatFilterScroll = rememberScrollState()
                        StudioHorizontalScrollStrip(
                            state = formatFilterScroll,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(formatFilterScroll),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                request.filters.forEachIndexed { idx, cat ->
                                    val count = filterCounts.getOrElse(idx) { 0 }
                                    val chipLabel = if (cat.extensions.isEmpty()) {
                                        cat.label
                                    } else {
                                        "${cat.label} ($count)"
                                    }
                                    StudioFilterChip(
                                        selected = activeFilterIndex == idx,
                                        onClick = { activeFilterIndex = idx },
                                        labelText = chipLabel,
                                        horizontalPadding = 10.dp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StudioFilterChip(
                            selected = showHiddenFiles,
                            onClick = { showHiddenFiles = !showHiddenFiles },
                            labelText = "隐藏文件",
                            horizontalPadding = 9.dp
                        )
                    }

                    // Compact Quick-Places Chips Row when sidebar is hidden (<680dp)
                    AnimatedVisibility(
                        visible = isCompactSidebar,
                        enter = StudioMotion.verticalPanelEnter,
                        exit = StudioMotion.verticalPanelExit
                    ) {
                        val corpusDir = Context.appDataRootDir
                        val compactQuickPlacesScroll = rememberScrollState()
                        StudioHorizontalScrollStrip(
                            state = compactQuickPlacesScroll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(compactQuickPlacesScroll),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quickPlaces.forEach { place ->
                                    StudioFilterChip(
                                        selected = currentDir.absolutePath.equals(place.dir.absolutePath, ignoreCase = true),
                                        onClick = { navigateToDirectory(place.dir) },
                                        labelText = place.label,
                                        showCheckmark = false,
                                        horizontalPadding = 9.dp
                                    )
                                }
                                StudioFilterChip(
                                    selected = currentDir.absolutePath.equals(corpusDir.absolutePath, ignoreCase = true),
                                    onClick = { navigateToDirectory(corpusDir) },
                                    labelText = "本地语料目录",
                                    showCheckmark = false,
                                    horizontalPadding = 9.dp
                                )
                                mountedVolumes.forEach { vol ->
                                    StudioFilterChip(
                                        selected = currentDir.absolutePath.equals(vol.root.absolutePath, ignoreCase = true),
                                        onClick = { navigateToDirectory(vol.root) },
                                        labelText = vol.displayName,
                                        showCheckmark = false,
                                        horizontalPadding = 9.dp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))

                    // 4. Main 3-Column Studio Body
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 4a. Left Sidebar: Quick Places + Corpus + Recent + Mounted Volumes
                        if (!isCompactSidebar) {
                            Column(
                                modifier = Modifier
                                    .width(192.dp)
                                    .fillMaxHeight()
                                    .background(cs.surfaceContainerLow.copy(alpha = 0.55f))
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                SidebarSectionTitle("常用位置")
                                quickPlaces.forEach { place ->
                                    SidebarNavRow(
                                        label = place.label,
                                        icon = place.icon,
                                        selected = currentDir.absolutePath.equals(place.dir.absolutePath, ignoreCase = true),
                                        onClick = { navigateToDirectory(place.dir) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                SidebarSectionTitle("语料与最近")
                                val corpusDir = Context.appDataRootDir
                                SidebarNavRow(
                                    label = "本地语料目录",
                                    icon = Icons.Default.FolderSpecial,
                                    selected = currentDir.absolutePath.equals(corpusDir.absolutePath, ignoreCase = true),
                                    accentTint = cs.primary,
                                    onClick = { navigateToDirectory(corpusDir) }
                                )
                                DesktopFilePickerController.recentDirectories.take(4).forEach { recentDir ->
                                    if (!recentDir.absolutePath.equals(corpusDir.absolutePath, ignoreCase = true) &&
                                        quickPlaces.none { it.dir.absolutePath.equals(recentDir.absolutePath, ignoreCase = true) }
                                    ) {
                                        SidebarNavRow(
                                            label = recentDir.name.ifBlank { recentDir.absolutePath },
                                            icon = Icons.Default.History,
                                            selected = currentDir.absolutePath.equals(recentDir.absolutePath, ignoreCase = true),
                                            onClick = { navigateToDirectory(recentDir) }
                                        )
                                    }
                                }

                                if (mountedVolumes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    SidebarSectionTitle("本机磁盘")
                                    mountedVolumes.forEach { vol ->
                                        val isCurrentRoot = currentDir.absolutePath.startsWith(vol.root.absolutePath, ignoreCase = true)
                                        VolumeSidebarCard(
                                            volume = vol,
                                            selected = isCurrentRoot,
                                            onClick = { navigateToDirectory(vol.root) }
                                        )
                                    }
                                }
                            }

                            VerticalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))
                        }

                        // 4b. Center File Browser (Sortable Table List OR Thumbnail Grid)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (!isGridView) {
                                // Sortable Column Header Strip
                                Surface(
                                    color = cs.surfaceContainerLow.copy(alpha = 0.72f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SortableHeaderCell(
                                            title = "名称",
                                            field = BrowserSortField.NAME,
                                            currentField = sortField,
                                            ascending = sortAscending,
                                            onSort = {
                                                if (sortField == BrowserSortField.NAME) sortAscending = !sortAscending
                                                else {
                                                    sortField = BrowserSortField.NAME
                                                    sortAscending = true
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!isCompactSidebar) {
                                            SortableHeaderCell(
                                                title = "修改时间",
                                                field = BrowserSortField.MODIFIED,
                                                currentField = sortField,
                                                ascending = sortAscending,
                                                onSort = {
                                                    if (sortField == BrowserSortField.MODIFIED) sortAscending = !sortAscending
                                                    else {
                                                        sortField = BrowserSortField.MODIFIED
                                                        sortAscending = false
                                                    }
                                                },
                                                modifier = Modifier.width(128.dp)
                                            )
                                        }
                                        SortableHeaderCell(
                                            title = "类型",
                                            field = BrowserSortField.TYPE,
                                            currentField = sortField,
                                            ascending = sortAscending,
                                            onSort = {
                                                if (sortField == BrowserSortField.TYPE) sortAscending = !sortAscending
                                                else {
                                                    sortField = BrowserSortField.TYPE
                                                    sortAscending = true
                                                }
                                            },
                                            modifier = Modifier.width(92.dp)
                                        )
                                        if (!isCompactSidebar) {
                                            SortableHeaderCell(
                                                title = "大小",
                                                field = BrowserSortField.SIZE,
                                                currentField = sortField,
                                                ascending = sortAscending,
                                                onSort = {
                                                    if (sortField == BrowserSortField.SIZE) sortAscending = !sortAscending
                                                    else {
                                                        sortField = BrowserSortField.SIZE
                                                        sortAscending = false
                                                    }
                                                },
                                                modifier = Modifier.width(76.dp)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
                            }

                            val browserViewMode = when {
                                displayedItems.isEmpty() -> PickerBrowserViewMode.EMPTY
                                isGridView -> PickerBrowserViewMode.GRID
                                else -> PickerBrowserViewMode.LIST
                            }
                            AnimatedContent(
                                targetState = browserViewMode,
                                transitionSpec = { StudioMotion.subtleFadeScale() },
                                label = "PickerBrowserContentMode",
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) { mode ->
                                when (mode) {
                                    PickerBrowserViewMode.EMPTY -> {
                                        EmptyDirectoryPlaceholder(
                                            hasSearch = searchQuery.isNotBlank(),
                                            hiddenByFilterCount = hiddenByFilterCount,
                                            activeFilterLabel = activeFilter.label,
                                            onClearSearch = { searchQuery = "" },
                                            onShowAllFiles = {
                                                val allIdx = request.filters.indexOfFirst { it.extensions.isEmpty() }
                                                if (allIdx >= 0) activeFilterIndex = allIdx
                                            }
                                        )
                                    }
                                    PickerBrowserViewMode.GRID -> {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyVerticalGrid(
                                                columns = GridCells.Adaptive(minSize = 122.dp),
                                                state = gridState,
                                                contentPadding = PaddingValues(12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                items(
                                                    items = displayedItems,
                                                    key = { it.absolutePath }
                                                ) { item ->
                                                    val multiIdx = selectedFiles.indexOfFirst { it.absolutePath == item.absolutePath }
                                                    val isSelected = multiIdx >= 0 ||
                                                        (!isMultiMode && focusedSingleItem?.absolutePath == item.absolutePath)
                                                    FileGridCardItem(
                                                        file = item,
                                                        isSelected = isSelected,
                                                        multiBadgeNumber = if (isMultiMode && multiIdx >= 0) multiIdx + 1 else null,
                                                        onClick = { handleItemClick(item) },
                                                        modifier = studioAnimateItem()
                                                    )
                                                }
                                            }
                                            PlatformVerticalScrollbar(
                                                gridState = gridState,
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        }
                                    }
                                    PickerBrowserViewMode.LIST -> {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            LazyColumn(
                                                state = listState,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                itemsIndexed(
                                                    items = displayedItems,
                                                    key = { _, f -> f.absolutePath }
                                                ) { _, item ->
                                                    val multiIdx = selectedFiles.indexOfFirst { it.absolutePath == item.absolutePath }
                                                    val isSelected = multiIdx >= 0 ||
                                                        (!isMultiMode && focusedSingleItem?.absolutePath == item.absolutePath)
                                                    FileTableRowItem(
                                                        file = item,
                                                        isSelected = isSelected,
                                                        isMultiSelectMode = isMultiMode,
                                                        multiBadgeNumber = if (isMultiMode && multiIdx >= 0) multiIdx + 1 else null,
                                                        compactColumns = isCompactSidebar,
                                                        onClick = { handleItemClick(item) },
                                                        modifier = studioAnimateItem()
                                                    )
                                                }
                                            }
                                            PlatformVerticalScrollbar(
                                                listState = listState,
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4c. Right Live File Inspector Pane
                        AnimatedVisibility(
                            visible = showRightInspector,
                            enter = StudioMotion.horizontalPaneEnterFromEnd(),
                            exit = StudioMotion.horizontalPaneExitToEnd()
                        ) {
                            Row(modifier = Modifier.fillMaxHeight()) {
                                VerticalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))
                                FileInspectorRightPane(
                                    preview = inspectorPreview,
                                    currentDir = currentDir,
                                    totalVisibleCount = displayedItems.size,
                                    modifier = Modifier
                                        .width(220.dp)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.65f))

                    // 5. Bottom Action Bar: Left「切换系统窗口」+ Right Save Filename / Selection Summary + Buttons
                    val canConfirm = when {
                        isSaveMode -> saveFileNameInput.isNotBlank()
                        isDirMode -> true
                        isMultiMode -> selectedFiles.isNotEmpty() || focusedSingleItem?.isDirectory == true
                        else -> selectedFiles.isNotEmpty() || focusedSingleItem != null
                    }
                    val primaryBtnLabel = when {
                        isSaveMode -> "保存至此"
                        isDirMode -> if (focusedSingleItem?.isDirectory == true) {
                            "选择「${focusedSingleItem!!.name.take(10)}」"
                        } else {
                            "选择此文件夹"
                        }
                        focusedSingleItem?.isDirectory == true && selectedFiles.isEmpty() -> "打开文件夹"
                        isMultiMode -> if (selectedFiles.isEmpty()) "选择文件" else "添加 (${selectedFiles.size})"
                        else -> "打开"
                    }
                    val summaryLabel = when {
                        isDirMode && focusedSingleItem?.isDirectory == true -> "将扫描子目录：${focusedSingleItem!!.name}"
                        isDirMode -> "将扫描当前目录：${currentDir.name.ifBlank { currentDir.absolutePath }}"
                        isMultiMode && selectedFiles.isNotEmpty() -> "已选 ${selectedFiles.size} 个文件"
                        selectedFiles.isNotEmpty() -> selectedFiles.first().name
                        focusedSingleItem?.isDirectory == true -> "文件夹：${focusedSingleItem!!.name}"
                        else -> "单击预览 · 双击直接打开"
                    }

                    if (isCompactBottomBar) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cs.surfaceContainerLow.copy(alpha = 0.5f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSaveMode) {
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = cs.surface,
                                    border = BorderStroke(1.dp, cs.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "文件名：",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cs.onSurfaceVariant
                                        )
                                        BasicTextField(
                                            value = saveFileNameInput,
                                            onValueChange = { saveFileNameInput = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = cs.onSurface,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            cursorBrush = SolidColor(cs.primary),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (request.requiredSaveExtension.isNotEmpty() &&
                                            !saveFileNameInput.lowercase().endsWith(".${request.requiredSaveExtension.lowercase()}")
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = cs.primaryContainer
                                            ) {
                                                Text(
                                                    text = ".${request.requiredSaveExtension}",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = cs.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = summaryLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedFiles.isNotEmpty() || isDirMode) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedFiles.isNotEmpty() || isDirMode) cs.onSurface else cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val switchNativeInteraction = remember { MutableInteractionSource() }
                                OutlinedButton(
                                    onClick = {
                                        DesktopFilePickerController.switchToSystemNativeWindow(
                                            currentDirectory = currentDir,
                                            currentSaveFileName = saveFileNameInput,
                                            activeFilterIndex = activeFilterIndex
                                        )
                                    },
                                    interactionSource = switchNativeInteraction,
                                    shape = RoundedCornerShape(11.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .studioTactile(switchNativeInteraction, pressScale = 0.96f)
                                        .testTag("switch_to_native_os_file_dialog_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "系统窗口",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        onClick = { DesktopFilePickerController.dismissAndCancel() },
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("取消", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Button(
                                        onClick = { triggerPrimaryConfirm() },
                                        enabled = canConfirm,
                                        shape = RoundedCornerShape(11.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .height(36.dp)
                                            .testTag("studio_file_picker_confirm_button")
                                    ) {
                                        Text(
                                            text = primaryBtnLabel,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cs.surfaceContainerLow.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val switchNativeInteraction = remember { MutableInteractionSource() }
                            OutlinedButton(
                                onClick = {
                                    DesktopFilePickerController.switchToSystemNativeWindow(
                                        currentDirectory = currentDir,
                                        currentSaveFileName = saveFileNameInput,
                                        activeFilterIndex = activeFilterIndex
                                    )
                                },
                                interactionSource = switchNativeInteraction,
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .studioTactile(switchNativeInteraction, pressScale = 0.96f)
                                    .testTag("switch_to_native_os_file_dialog_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "切换系统窗口",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                if (isSaveMode) {
                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        color = cs.surface,
                                        border = BorderStroke(1.dp, cs.outlineVariant),
                                        modifier = Modifier
                                            .widthIn(min = 200.dp, max = 380.dp)
                                            .height(38.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 11.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "文件名：",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = cs.onSurfaceVariant
                                            )
                                            BasicTextField(
                                                value = saveFileNameInput,
                                                onValueChange = { saveFileNameInput = it },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = cs.onSurface,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                cursorBrush = SolidColor(cs.primary),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (request.requiredSaveExtension.isNotEmpty() &&
                                                !saveFileNameInput.lowercase().endsWith(".${request.requiredSaveExtension.lowercase()}")
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(5.dp),
                                                    color = cs.primaryContainer
                                                ) {
                                                    Text(
                                                        text = ".${request.requiredSaveExtension}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = cs.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        text = summaryLabel,
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedFiles.isNotEmpty() || isDirMode) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selectedFiles.isNotEmpty() || isDirMode) cs.onSurface else cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    )
                                }

                                TextButton(
                                    onClick = { DesktopFilePickerController.dismissAndCancel() },
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("取消", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = { triggerPrimaryConfirm() },
                                    enabled = canConfirm,
                                    shape = RoundedCornerShape(11.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("studio_file_picker_confirm_button")
                                ) {
                                    Text(
                                        text = primaryBtnLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Glowing Studio Drag-and-Drop Overlay when dragging external OS files into window
                AnimatedVisibility(
                    visible = isExternalDragHovering,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(120)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cs.primaryContainer.copy(alpha = 0.88f))
                            .border(2.dp, cs.primary, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "松开鼠标即可直接选用拖入的文件或目录",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    // Overwrite Confirmation Modal in Save Mode
    if (pendingOverwriteTarget != null) {
        val target = pendingOverwriteTarget!!
        StudioAlertDialog(
            onDismissRequest = { pendingOverwriteTarget = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = cs.error
                )
            },
            title = {
                Text("确认覆盖已有文件？", fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Text("当前目录下已存在「${target.name}」，继续保存将覆盖原文件内容。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOverwriteTarget = null
                        DesktopFilePickerController.completeSelection(listOf(target))
                    },
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("确认覆盖", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOverwriteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun PickerIconPillButton(
    icon: ImageVector,
    tooltipDesc: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (interaction, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val bg by animateColorAsState(
        targetValue = when {
            active -> cs.primaryContainer
            isHovered -> cs.surfaceContainerHigh
            else -> cs.surfaceContainerLow
        },
        animationSpec = StudioMotion.microTween(),
        label = "PickerPillBtnBg"
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            active -> cs.primary.copy(alpha = 0.55f)
            isHovered -> cs.primary.copy(alpha = 0.35f)
            else -> cs.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "PickerPillBtnBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            active -> cs.onPrimaryContainer
            isHovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "PickerPillBtnIcon"
    )
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = bg,
        border = BorderStroke(1.dp, borderCol),
        modifier = Modifier
            .size(32.dp)
            .studioTactile(interaction, pressScale = 0.93f)
            .clip(RoundedCornerShape(9.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tooltipDesc,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun PickerNavIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (interaction, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val navBg by animateColorAsState(
        targetValue = if (enabled && isHovered) {
            lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.55f)
        } else {
            cs.surfaceContainerLow
        },
        animationSpec = StudioMotion.microTween(),
        label = "PickerNavBtnBg"
    )
    val navBorder by animateColorAsState(
        targetValue = when {
            !enabled -> cs.outlineVariant.copy(alpha = 0.25f)
            isHovered -> cs.primary.copy(alpha = 0.4f)
            else -> cs.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "PickerNavBtnBorder"
    )
    val navIconTint by animateColorAsState(
        targetValue = when {
            !enabled -> cs.onSurfaceVariant.copy(alpha = 0.35f)
            isHovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "PickerNavBtnIcon"
    )
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = navBg,
        border = BorderStroke(1.dp, navBorder),
        modifier = Modifier
            .size(32.dp)
            .studioTactile(interaction, enabled = enabled, pressScale = 0.92f)
            .clip(RoundedCornerShape(9.dp))
            .then(
                if (enabled) {
                    Modifier
                        .hoverable(interaction)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = navIconTint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SidebarSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun SidebarNavRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentTint: Color? = null,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (interaction, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val bg by animateColorAsState(
        targetValue = when {
            selected -> cs.primaryContainer.copy(alpha = 0.82f)
            isHovered -> cs.primaryContainer.copy(alpha = 0.34f)
            else -> cs.primaryContainer.copy(alpha = 0f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "SidebarNavBg"
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            selected -> cs.primary.copy(alpha = 0.38f)
            isHovered -> cs.outlineVariant.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = StudioMotion.microTween(),
        label = "SidebarNavBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            selected -> cs.onPrimaryContainer
            accentTint != null -> accentTint
            isHovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = StudioMotion.microTween(),
        label = "SidebarNavIcon"
    )
    val textCol by animateColorAsState(
        targetValue = when {
            selected -> cs.onPrimaryContainer
            isHovered -> cs.primary
            else -> cs.onSurface
        },
        animationSpec = StudioMotion.microTween(),
        label = "SidebarNavText"
    )
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = bg,
        border = BorderStroke(1.dp, borderCol),
        modifier = Modifier
            .fillMaxWidth()
            .height(33.dp)
            .studioTactile(interaction, pressScale = 0.975f)
            .clip(RoundedCornerShape(9.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun VolumeSidebarCard(
    volume: VolumeDriveItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val (interaction, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val usedRatio = if (volume.totalBytes > 0L) {
        ((volume.totalBytes - volume.freeBytes).toFloat() / volume.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val volBg by animateColorAsState(
        targetValue = when {
            selected -> lerp(cs.surface, cs.primaryContainer, 0.72f)
            isHovered -> lerp(cs.surface, cs.primaryContainer, 0.26f)
            else -> cs.surface
        },
        animationSpec = StudioMotion.microTween(),
        label = "VolumeCardBg"
    )
    val volBorder by animateColorAsState(
        targetValue = when {
            selected -> cs.primary.copy(alpha = 0.52f)
            isHovered -> cs.primary.copy(alpha = 0.32f)
            else -> cs.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "VolumeCardBorder"
    )
    val volTitleColor by animateColorAsState(
        targetValue = when {
            selected -> cs.onPrimaryContainer
            isHovered -> cs.primary
            else -> cs.onSurface
        },
        animationSpec = StudioMotion.microTween(),
        label = "VolumeCardTitle"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = volBg,
        border = BorderStroke(1.dp, volBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .studioTactile(interaction, pressScale = 0.975f)
            .clip(RoundedCornerShape(10.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = if (selected) cs.onPrimaryContainer else cs.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = volume.displayName,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = volTitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (volume.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { usedRatio },
                    color = if (usedRatio > 0.90f) cs.error else cs.primary,
                    trackColor = cs.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "可用 ${DesktopFileInspector.formatBytes(volume.freeBytes)} / ${DesktopFileInspector.formatBytes(volume.totalBytes)}",
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SortableHeaderCell(
    title: String,
    field: BrowserSortField,
    currentField: BrowserSortField,
    ascending: Boolean,
    onSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val isCurrent = field == currentField
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onSort)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isCurrent) cs.primary else cs.onSurfaceVariant
        )
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier
                    .size(12.dp)
                    .studioRotateOn(!ascending)
            )
        }
    }
}

@Composable
private fun FileTableRowItem(
    file: File,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    multiBadgeNumber: Int?,
    compactColumns: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val kind = remember(file.name, file.isDirectory) { DesktopFileInspector.classifyFile(file) }
    val (badgeBg, badgeFg, iconVec) = semanticStyleForKind(kind)

    // Interpolate purely between opaque cs.surface and tinted primaryContainer (never Color.Transparent / 0x00000000,
    // which interpolates through mid-gray L=0.5 in Oklab space during hover!)
    val rowBg by animateColorAsState(
        targetValue = when {
            isSelected -> lerp(cs.surface, cs.primaryContainer, if (isHovered) 0.76f else 0.64f)
            isHovered -> lerp(cs.surface, cs.primaryContainer, 0.28f)
            else -> cs.surface
        },
        animationSpec = tween(115),
        label = "FileRowBg"
    )
    val rowBorderColor by animateColorAsState(
        targetValue = when {
            isSelected -> cs.primary.copy(alpha = 0.55f)
            isHovered -> cs.primary.copy(alpha = 0.24f)
            else -> cs.primary.copy(alpha = 0f)
        },
        animationSpec = tween(115),
        label = "FileRowBorder"
    )

    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file.absolutePath) {
        if (kind == StudioFileSemanticKind.IMAGE) {
            value = DesktopFileInspector.loadThumbnail(file, maxEdgePx = 72)
        }
    }

    Surface(
        shape = RoundedCornerShape(9.dp),
        color = rowBg,
        border = BorderStroke(1.dp, rowBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (isMultiSelectMode && file.isFile) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (multiBadgeNumber != null) cs.primary else Color.Transparent,
                        border = BorderStroke(
                            1.5.dp,
                            if (multiBadgeNumber != null) cs.primary else cs.outline
                        ),
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = multiBadgeNumber != null,
                                enter = fadeIn(StudioMotion.microTween()) + scaleIn(StudioMotion.tactileSpring(), initialScale = 0.7f),
                                exit = fadeOut(StudioMotion.shortOutTween()) + scaleOut(StudioMotion.shortOutTween(), targetScale = 0.7f)
                            ) {
                                Text(
                                    text = (multiBadgeNumber ?: 0).toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cs.onPrimary
                                )
                            }
                        }
                    }
                }

                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = badgeBg,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Text(
                    text = file.name,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected || file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!compactColumns) {
                Text(
                    text = remember(file.lastModified()) { DesktopFileInspector.formatTimestamp(file.lastModified()) },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(128.dp)
                )
            }

            Text(
                text = kind.typeLabel,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(92.dp)
            )

            if (!compactColumns) {
                Text(
                    text = if (file.isDirectory) "—" else remember(file.length()) { DesktopFileInspector.formatBytes(file.length()) },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(76.dp)
                )
            }
        }
    }
}

@Composable
private fun FileGridCardItem(
    file: File,
    isSelected: Boolean,
    multiBadgeNumber: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val kind = remember(file.name, file.isDirectory) { DesktopFileInspector.classifyFile(file) }
    val (badgeBg, badgeFg, iconVec) = semanticStyleForKind(kind)

    val thumbBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file.absolutePath) {
        if (kind == StudioFileSemanticKind.IMAGE) {
            value = DesktopFileInspector.loadThumbnail(file, maxEdgePx = 220)
        }
    }

    val cardBg by animateColorAsState(
        targetValue = when {
            isSelected -> lerp(cs.surface, cs.primaryContainer, if (isHovered) 0.74f else 0.64f)
            isHovered -> lerp(cs.surface, cs.primaryContainer, 0.26f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = tween(115),
        label = "FileGridCardBg"
    )
    val cardBorder by animateColorAsState(
        targetValue = when {
            isSelected -> cs.primary
            isHovered -> cs.primary.copy(alpha = 0.35f)
            else -> cs.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(115),
        label = "FileGridCardBorder"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = cardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(14.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(cs.surface)
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap!!,
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeBg,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = null,
                                    tint = badgeFg,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = file.name,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (file.isDirectory) "文件夹" else DesktopFileInspector.formatBytes(file.length()),
                        fontSize = 10.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            val showSelectionBadge = multiBadgeNumber != null || (isSelected && file.isFile)
            AnimatedVisibility(
                visible = showSelectionBadge,
                enter = fadeIn(StudioMotion.microTween()) + scaleIn(StudioMotion.tactileSpring(), initialScale = 0.72f),
                exit = fadeOut(StudioMotion.shortOutTween()) + scaleOut(StudioMotion.shortOutTween(), targetScale = 0.72f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = cs.primary,
                    modifier = Modifier.size(if (multiBadgeNumber != null) 22.dp else 20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (multiBadgeNumber != null) {
                            Text(
                                text = multiBadgeNumber.toString(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = cs.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInspectorRightPane(
    preview: FileInspectorPreview?,
    currentDir: File,
    totalVisibleCount: Int,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(cs.surfaceContainerLow.copy(alpha = 0.45f))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (preview == null) {
            Text(
                text = "目录概览",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = currentDir.name.ifBlank { currentDir.absolutePath },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onSurface
                    )
                    Text(
                        text = "当前筛选可见 $totalVisibleCount 项",
                        fontSize = 11.5.sp,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = "单击任意文件可在此实时预览表格、TextGrid、压缩包条目或配图分辨率。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = cs.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            return
        }

        val (badgeBg, badgeFg, iconVec) = semanticStyleForKind(preview.kind)
        val previewBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = preview.file.absolutePath) {
            if (preview.kind == StudioFileSemanticKind.IMAGE) {
                value = DesktopFileInspector.loadThumbnail(preview.file, maxEdgePx = 360)
            }
        }

        Text(
            text = "实时预览",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurfaceVariant
        )

        if (previewBitmap != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                Image(
                    bitmap = previewBitmap!!,
                    contentDescription = preview.file.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeBg,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVec,
                        contentDescription = null,
                        tint = badgeFg,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Text(
            text = preview.file.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = cs.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!preview.summaryHighlight.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cs.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = preview.summaryHighlight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InspectorMetaRow("类型", preview.kind.typeLabel)
            InspectorMetaRow("大小", preview.formattedSize)
            InspectorMetaRow("修改", preview.formattedModified)
        }

        if (preview.archiveDetails.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    preview.archiveDetails.forEach { line ->
                        Text(
                            text = "• $line",
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!preview.textSnippet.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = preview.textSnippet,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurface,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(9.dp)
                )
            }
        }
    }
}

@Composable
private fun InspectorMetaRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = cs.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyDirectoryPlaceholder(
    hasSearch: Boolean,
    hiddenByFilterCount: Int,
    activeFilterLabel: String,
    onClearSearch: () -> Unit,
    onShowAllFiles: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (hasSearch) Icons.Default.Search else Icons.Default.Folder,
                contentDescription = null,
                tint = cs.outline,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = when {
                    hasSearch -> "未找到匹配关键字的文件"
                    hiddenByFilterCount > 0 -> "当前目录没有符合「$activeFilterLabel」的文件"
                    else -> "此文件夹为空"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
            if (hasSearch) {
                FilledTonalButton(
                    onClick = onClearSearch,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("清空搜索条件", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else if (hiddenByFilterCount > 0) {
                Text(
                    text = "该目录下另有 $hiddenByFilterCount 个其他格式文件已被当前筛选器隐藏",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onShowAllFiles,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("显示所有文件 (*.*)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun semanticStyleForKind(kind: StudioFileSemanticKind): Triple<Color, Color, ImageVector> {
    val cs = MaterialTheme.colorScheme
    val ext = LocalPhonAcqExtendedColors.current
    return when (kind) {
        StudioFileSemanticKind.FOLDER -> Triple(
            cs.primaryContainer.copy(alpha = 0.65f),
            cs.primary,
            Icons.Default.Folder
        )
        StudioFileSemanticKind.SPREADSHEET,
        StudioFileSemanticKind.CSV_TSV -> Triple(
            ext.fileBadgeTableBg,
            ext.fileBadgeTableFg,
            Icons.Default.TableChart
        )
        StudioFileSemanticKind.DOCUMENT,
        StudioFileSemanticKind.JSON -> Triple(
            ext.fileBadgeTextBg,
            ext.fileBadgeTextFg,
            Icons.Default.Description
        )
        StudioFileSemanticKind.TEPROJ -> Triple(
            cs.primaryContainer,
            cs.onPrimaryContainer,
            Icons.Default.GraphicEq
        )
        StudioFileSemanticKind.ARCHIVE -> Triple(
            ext.fileBadgeArchiveBg,
            ext.fileBadgeArchiveFg,
            Icons.Default.Archive
        )
        StudioFileSemanticKind.TEXTGRID -> Triple(
            ext.fileBadgeTextBg,
            ext.fileBadgeTextFg,
            Icons.Default.GraphicEq
        )
        StudioFileSemanticKind.IMAGE -> Triple(
            ext.fileBadgeImageBg,
            ext.fileBadgeImageFg,
            Icons.Default.Image
        )
        StudioFileSemanticKind.AUDIO -> Triple(
            ext.fileBadgeAudioBg,
            ext.fileBadgeAudioFg,
            Icons.Default.AudioFile
        )
        StudioFileSemanticKind.OTHER -> Triple(
            cs.surfaceContainerHighest,
            cs.onSurfaceVariant,
            Icons.AutoMirrored.Filled.InsertDriveFile
        )
    }
}

private fun expandUserPath(raw: String): File {
    val trimmed = raw.trim().removeSurrounding("\"")
    if (trimmed == "~" || trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
        val home = System.getProperty("user.home", ".")
        val rest = trimmed.drop(1).trimStart('/', '\\')
        return if (rest.isEmpty()) File(home) else File(home, rest)
    }
    return File(trimmed)
}

private fun buildPathSegments(dir: File): List<Pair<String, File>> {
    val chain = ArrayList<File>()
    var cur: File? = dir
    while (cur != null) {
        chain.add(cur)
        cur = cur.parentFile
    }
    chain.reverse()
    return chain.mapIndexed { index, f ->
        val rawName = f.name
        val label = when {
            rawName.isNotEmpty() -> rawName
            f.path.length >= 2 && f.path[1] == ':' -> "磁盘 ${f.path.take(2)}"
            else -> "/"
        }
        if (index == 0) label to f else rawName to f
    }
}

private fun buildQuickPlaces(): List<QuickPlaceItem> {
    val home = File(System.getProperty("user.home", "."))
    fun pickExisting(enName: String, zhName: String): File? {
        return listOf(File(home, enName), File(home, zhName))
            .firstOrNull { it.exists() && it.isDirectory }
    }
    return buildList {
        pickExisting("Desktop", "桌面")?.let {
            add(QuickPlaceItem("桌面", Icons.Default.DesktopWindows, it))
        }
        pickExisting("Downloads", "下载")?.let {
            add(QuickPlaceItem("下载", Icons.Default.Download, it))
        }
        pickExisting("Documents", "文档")?.let {
            add(QuickPlaceItem("文档", Icons.Default.Description, it))
        }
        pickExisting("Pictures", "图片")?.let {
            add(QuickPlaceItem("图片", Icons.Default.Image, it))
        }
        if (home.exists() && home.isDirectory) {
            add(QuickPlaceItem("个人主目录", Icons.Default.Home, home))
        }
    }
}

private fun discoverMountedVolumes(): List<VolumeDriveItem> {
    val results = ArrayList<VolumeDriveItem>()
    val fsv = try {
        FileSystemView.getFileSystemView()
    } catch (_: Throwable) {
        null
    }

    val roots = File.listRoots()?.toList().orEmpty()
    for (root in roots) {
        if (!root.exists()) continue
        val sysName = try {
            fsv?.getSystemDisplayName(root)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
        val fallbackName = if (root.path == "/") "系统根目录 (/)" else root.path.trimEnd('\\')
        results.add(
            VolumeDriveItem(
                root = root,
                displayName = sysName ?: fallbackName,
                totalBytes = root.totalSpace,
                freeBytes = root.usableSpace
            )
        )
    }

    // macOS /Volumes external drives
    val macVolumes = File("/Volumes")
    if (macVolumes.exists() && macVolumes.isDirectory) {
        macVolumes.listFiles()?.forEach { vol ->
            if (vol.isDirectory && !vol.name.startsWith(".")) {
                results.add(
                    VolumeDriveItem(
                        root = vol,
                        displayName = vol.name,
                        totalBytes = vol.totalSpace,
                        freeBytes = vol.usableSpace
                    )
                )
            }
        }
    }

    return results.distinctBy { it.root.absolutePath }
}
