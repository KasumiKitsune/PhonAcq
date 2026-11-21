package com.example

import android.app.Application
import android.content.Context
import android.media.DesktopAudioDeviceSelector
import android.widget.DesktopToastHostState
import android.widget.Toast
import androidx.activity.compose.DesktopBackDispatcher
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.AdaptiveModalSurface
import com.example.ui.components.AnimatedDialogVisibility
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.DesktopModalHost
import com.example.ui.components.DesktopModalManager
import com.example.ui.components.KeycapBadge
import com.example.ui.components.LocalAppWindowMetrics
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.StudioMotion
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateSize
import com.example.ui.components.studioRotateOn
import com.example.ui.components.studioTactile
import com.example.ui.filepicker.DesktopFilePickerController
import com.example.ui.filepicker.DesktopStudioFilePickerHost
import com.example.ui.screens.MainScreen
import com.example.ui.theme.LocalPhonAcqExtendedColors
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RecordingViewModel
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowStateListener
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MIN_WINDOW_WIDTH = 480
private const val MIN_WINDOW_HEIGHT = 560

private data class RestoredWindowBounds(
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement
)

private object DesktopWindowBoundsStore {
    private val prefs: Preferences? by lazy {
        runCatching {
            Preferences.userRoot().node("io/github/kasumikitsune/phonacq/window")
        }.getOrNull()
    }

    fun computeInitialBounds(): RestoredWindowBounds {
        val maxUsable: Rectangle = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        }.getOrElse {
            Rectangle(0, 0, 1440, 900)
        }

        val defaultW = minOf(1280, (maxUsable.width * 0.88).toInt()).coerceAtLeast(MIN_WINDOW_WIDTH)
        val defaultH = minOf(840, (maxUsable.height * 0.90).toInt()).coerceAtLeast(MIN_WINDOW_HEIGHT)

        val p = prefs ?: return RestoredWindowBounds(
            size = DpSize(defaultW.dp, defaultH.dp),
            position = WindowPosition(Alignment.Center),
            placement = WindowPlacement.Floating
        )

        val savedW = p.getInt("win_w", -1)
        val savedH = p.getInt("win_h", -1)
        val savedX = p.getInt("win_x", Int.MIN_VALUE)
        val savedY = p.getInt("win_y", Int.MIN_VALUE)
        val savedMaximized = p.getBoolean("win_maximized", false)

        val w = if (savedW >= MIN_WINDOW_WIDTH) {
            savedW.coerceAtMost(maxUsable.width.coerceAtLeast(MIN_WINDOW_WIDTH))
        } else {
            defaultW
        }
        val h = if (savedH >= MIN_WINDOW_HEIGHT) {
            savedH.coerceAtMost(maxUsable.height.coerceAtLeast(MIN_WINDOW_HEIGHT))
        } else {
            defaultH
        }

        val restoredPos = if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE && isVisibleOnAnyScreen(savedX, savedY, w, h)) {
            WindowPosition(savedX.dp, savedY.dp)
        } else {
            WindowPosition(Alignment.Center)
        }

        return RestoredWindowBounds(
            size = DpSize(w.dp, h.dp),
            position = restoredPos,
            placement = if (savedMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
        )
    }

    private fun isVisibleOnAnyScreen(x: Int, y: Int, w: Int, h: Int): Boolean {
        return runCatching {
            val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            if (devices.isEmpty()) return false
            val titleProbe = Rectangle(x + 40, y + 10, (w - 80).coerceAtLeast(80), 40)
            devices.any { dev ->
                dev.defaultConfiguration.bounds.intersects(titleProbe)
            }
        }.getOrDefault(false)
    }

    fun saveFromWindow(window: java.awt.Window) {
        val p = prefs ?: return
        runCatching {
            val isMaximized = (window as? Frame)?.let {
                (it.extendedState and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
            } ?: false
            p.putBoolean("win_maximized", isMaximized)
            if (!isMaximized) {
                val b = window.bounds
                if (b.width >= MIN_WINDOW_WIDTH && b.height >= MIN_WINDOW_HEIGHT) {
                    p.putInt("win_x", b.x)
                    p.putInt("win_y", b.y)
                    p.putInt("win_w", b.width)
                    p.putInt("win_h", b.height)
                }
            }
            p.flush()
        }
    }
}

/**
 * Synchronizes native OS titlebar appearance (Windows 10/11 DWM immersive dark mode & macOS Aqua appearance).
 * Fully exception-safe on older OS versions, Linux, and Wine.
 */
private suspend fun syncNativeWindowDarkTitleBar(
    window: androidx.compose.ui.awt.ComposeWindow,
    darkTheme: Boolean
) = withContext(Dispatchers.IO) {
    val osName = System.getProperty("os.name", "").lowercase()
    when {
        osName.contains("mac") || osName.contains("darwin") -> {
            val appearance = if (darkTheme) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
            runCatching {
                System.setProperty("apple.awt.application.appearance", appearance)
                window.rootPane?.putClientProperty("apple.awt.windowAppearance", appearance)
            }
        }

        osName.contains("win") -> {
            if (!System.getenv("WINEPREFIX").isNullOrBlank()) return@withContext
            val hwnd = runCatching { window.windowHandle }.getOrNull() ?: return@withContext
            if (hwnd == 0L) return@withContext
            val darkInt = if (darkTheme) 1 else 0
            val psScript = """
                ${'$'}ErrorActionPreference = 'SilentlyContinue'
                Add-Type -TypeDefinition @"
                using System;
                using System.Runtime.InteropServices;
                public static class PhonAcqDwm {
                    [DllImport("dwmapi.dll")]
                    public static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);
                    [DllImport("user32.dll")]
                    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
                }
"@
                ${'$'}h = [IntPtr]::new(${hwnd}L)
                ${'$'}v = $darkInt
                [PhonAcqDwm]::DwmSetWindowAttribute(${'$'}h, 20, [ref]${'$'}v, 4) | Out-Null
                [PhonAcqDwm]::DwmSetWindowAttribute(${'$'}h, 19, [ref]${'$'}v, 4) | Out-Null
                [PhonAcqDwm]::SetWindowPos(${'$'}h, [IntPtr]::Zero, 0, 0, 0, 0, 0x0027) | Out-Null
            """.trimIndent()

            runCatching {
                val encoded = Base64.getEncoder().encodeToString(psScript.toByteArray(StandardCharsets.UTF_16LE))
                val proc = ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-WindowStyle",
                    "Hidden",
                    "-EncodedCommand",
                    encoded
                ).redirectErrorStream(true).start()
                proc.waitFor()
            }
        }
    }
}

private fun formatDesktopMicName(rawName: String?): String {
    if (rawName.isNullOrBlank()) return S.desktop.defaultMicName
    val trimmed = rawName.trim()
    return when {
        trimmed == "主声音捕获驱动程序" || trimmed.equals("Primary Sound Capture Driver", ignoreCase = true) ->
            S.desktop.primarySoundCaptureLabel
        trimmed.startsWith("Port ") -> trimmed.removePrefix("Port ").trim()
        else -> trimmed
    }
}

fun main(args: Array<String>) = application {
    LaunchedEffect(Unit) {
        DesktopAdaptiveState.isDesktopPlatform = true
    }

    val appContext = remember { Application.instance }
    val database = remember { AppDatabase.getDatabase(appContext) }
    val repository = remember { ProjectRepository(database) }
    val viewModel = remember { RecordingViewModel(appContext, repository) }

    // Handle double-clicking an associated .teproj or .zip archive on Windows / macOS
    LaunchedEffect(Unit) {
        val launchPath = args.firstOrNull { !it.startsWith("-") }?.trim()?.removeSurrounding("\"")
        if (!launchPath.isNullOrEmpty()) {
            val f = java.io.File(launchPath)
            val ext = f.extension.lowercase()
            if (f.isFile && (ext == "teproj" || ext == "zip")) {
                try {
                    viewModel.importProjectZip(java.io.FileInputStream(f)) { summary ->
                        val msg = if (summary.success) {
                            "已打开工程：${summary.projectName}"
                        } else {
                            "无法导入工程文件：${f.name}"
                        }
                        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    val currentProject by viewModel.currentProject.collectAsState()
    val pushToTalkEnabled by viewModel.pushToTalkEnabled.collectAsState()
    var isSpaceKeyDown by remember { mutableStateOf(false) }
    var isSpacePushToTalkHeld by remember { mutableStateOf(false) }
    val windowIconPainter = DesktopIconRenderer.rememberWindowIconPainter()

    val initialBounds = remember { DesktopWindowBoundsStore.computeInitialBounds() }
    val windowState = rememberWindowState(
        size = initialBounds.size,
        position = initialBounds.position,
        placement = initialBounds.placement
    )

    val windowTitle = remember(currentProject?.name) {
        if (!currentProject?.name.isNullOrBlank()) {
            "PhonAcq · ${currentProject!!.name}"
        } else {
            S.desktop.windowTitle
        }
    }

    var activeAwtWindow by remember { mutableStateOf<java.awt.Window?>(null) }

    Window(
        onCloseRequest = {
            activeAwtWindow?.let { DesktopWindowBoundsStore.saveFromWindow(it) }
            exitApplication()
        },
        state = windowState,
        title = windowTitle,
        icon = windowIconPainter,
        onPreviewKeyEvent = { keyEvent ->
            if (keyEvent.key == Key.Spacebar && keyEvent.type == KeyEventType.KeyUp) {
                isSpaceKeyDown = false
                if (isSpacePushToTalkHeld) {
                    isSpacePushToTalkHeld = false
                    if (viewModel.isRecording.value) {
                        viewModel.triggerStopRecordingFromHoldRelease()
                    }
                    return@Window true
                }
                return@Window false
            }

            if (keyEvent.type == KeyEventType.KeyDown) {
                val bareAllowed = DesktopAdaptiveState.canTriggerBareKeyShortcuts
                when (keyEvent.key) {
                    Key.Escape -> {
                        isSpaceKeyDown = false
                        isSpacePushToTalkHeld = false
                        if (viewModel.isRecording.value) {
                            viewModel.triggerStopRecording()
                            return@Window true
                        }
                        if (DesktopModalManager.entries.any { !it.isExiting }) {
                            if (DesktopBackDispatcher.dispatchBack()) {
                                return@Window true
                            }
                        }
                        if (DesktopAdaptiveState.showShortcutCheatSheet) {
                            DesktopAdaptiveState.showShortcutCheatSheet = false
                            return@Window true
                        }
                        if (viewModel.participantCleanMode.value) {
                            viewModel.setParticipantCleanMode(false)
                            return@Window true
                        }
                        if (DesktopBackDispatcher.dispatchBack()) {
                            return@Window true
                        }
                    }

                    Key.F5 -> {
                        val list = DesktopAudioDeviceSelector.refreshDevices()
                        Toast.makeText(appContext, S.desktop.refreshedInputDevicesToast.withArgs("count" to list.size), Toast.LENGTH_SHORT).show()
                        return@Window true
                    }

                    Key.F9 -> {
                        if (viewModel.currentProject.value != null) {
                            if (viewModel.isRecording.value) {
                                viewModel.triggerStopRecording()
                            } else {
                                viewModel.triggerStartRecording()
                            }
                            return@Window true
                        }
                    }

                    Key.Spacebar -> {
                        if (bareAllowed && viewModel.currentProject.value != null) {
                            if (viewModel.pushToTalkEnabled.value) {
                                if (!isSpacePushToTalkHeld) {
                                    isSpaceKeyDown = true
                                    isSpacePushToTalkHeld = true
                                    if (!viewModel.isRecording.value) {
                                        viewModel.triggerStartRecording()
                                    }
                                }
                                return@Window true
                            }
                            if (isSpaceKeyDown) {
                                return@Window true
                            }
                            isSpaceKeyDown = true
                            if (viewModel.isRecording.value) {
                                viewModel.triggerStopRecording()
                            } else {
                                viewModel.triggerStartRecording()
                            }
                            return@Window true
                        }
                    }

                    Key.DirectionLeft, Key.DirectionUp, Key.PageUp -> {
                        if ((keyEvent.isAltPressed || bareAllowed) && viewModel.currentProject.value != null) {
                            viewModel.prevWord()
                            return@Window true
                        }
                    }

                    Key.DirectionRight, Key.DirectionDown, Key.PageDown -> {
                        if ((keyEvent.isAltPressed || bareAllowed) && viewModel.currentProject.value != null) {
                            viewModel.nextWord()
                            return@Window true
                        }
                    }

                    Key.Enter, Key.NumPadEnter, Key.P -> {
                        if (bareAllowed && !viewModel.isRecording.value && viewModel.currentProject.value != null) {
                            val w = viewModel.getCurrentWord()
                            if (w != null && w.isRecorded) {
                                viewModel.playWordAudio(w)
                                return@Window true
                            }
                        }
                    }

                    Key.F -> {
                        if (bareAllowed && !viewModel.isRecording.value && viewModel.currentProject.value != null) {
                            val w = viewModel.getCurrentWord()
                            if (w != null && w.isRecorded) {
                                val wasNormal = w.qualityStatus == null || w.qualityStatus == "正常"
                                viewModel.toggleManualQualityFlag(w)
                                Toast.makeText(
                                    appContext,
                                    if (wasNormal) S.desktop.markedRerecordToast else S.desktop.confirmedNormalToast,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Window true
                            }
                        }
                    }

                    Key.M -> {
                        if (bareAllowed && viewModel.currentProject.value != null) {
                            val nextClean = !viewModel.participantCleanMode.value
                            viewModel.setParticipantCleanMode(nextClean)
                            Toast.makeText(
                                appContext,
                                if (nextClean) S.desktop.enteredCleanModeToast else S.desktop.exitedCleanModeToast,
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Window true
                        }
                    }
                }
            }
            false
        }
    ) {
        // Apply multi-resolution AWT Window icons, minimum size 480x560, bounds persistence, and focus guard
        DisposableEffect(window) {
            activeAwtWindow = window
            DesktopFilePickerController.attachMainWindow(window)
            val focusListener = object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {}
                override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                    isSpaceKeyDown = false
                    if (isSpacePushToTalkHeld) {
                        isSpacePushToTalkHeld = false
                        if (viewModel.isRecording.value) {
                            viewModel.triggerStopRecordingFromHoldRelease()
                        }
                    }
                }
            }
            val boundsListener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    DesktopWindowBoundsStore.saveFromWindow(window)
                }

                override fun componentMoved(e: ComponentEvent?) {
                    DesktopWindowBoundsStore.saveFromWindow(window)
                }
            }
            val stateListener = WindowStateListener {
                DesktopWindowBoundsStore.saveFromWindow(window)
            }
            window.addWindowFocusListener(focusListener)
            window.addComponentListener(boundsListener)
            window.addWindowStateListener(stateListener)
            try {
                window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT)
                window.iconImages = DesktopIconRenderer.getMultiResolutionWindowIcons()
            } catch (_: Exception) {
            }
            onDispose {
                DesktopWindowBoundsStore.saveFromWindow(window)
                window.removeWindowFocusListener(focusListener)
                window.removeComponentListener(boundsListener)
                window.removeWindowStateListener(stateListener)
                DesktopFilePickerController.attachMainWindow(null)
                activeAwtWindow = null
            }
        }

        val themeMode by viewModel.themeMode.collectAsState()
        val themePaletteId by viewModel.themePaletteId.collectAsState()
        val systemInDark = isSystemInDarkTheme()
        val useDarkTheme = when (themeMode) {
            "LIGHT" -> false
            "DARK" -> true
            else -> systemInDark
        }

        // Sync native OS dark title bar whenever light/dark mode changes
        LaunchedEffect(window, useDarkTheme) {
            syncNativeWindowDarkTitleBar(window, useDarkTheme)
        }

        val toastMessage by DesktopToastHostState.currentMessage.collectAsState()

        // Register Desktop studio bar and native Compose Desktop VerticalScrollbar slots
        DisposableEffect(Unit) {
            DesktopAdaptiveState.desktopTopBarSlot = {
                DesktopTopStudioBar(
                    appContext = appContext,
                    isPushToTalk = pushToTalkEnabled
                )
            }
            DesktopAdaptiveState.listScrollbarSlot = { listState, mod ->
                val cs = MaterialTheme.colorScheme
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    style = defaultScrollbarStyle().copy(
                        minimalHeight = 28.dp,
                        thickness = 5.dp,
                        shape = RoundedCornerShape(3.dp),
                        unhoverColor = cs.onSurface.copy(alpha = 0.16f),
                        hoverColor = cs.onSurface.copy(alpha = 0.40f)
                    ),
                    modifier = mod
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 1.dp)
                )
            }
            DesktopAdaptiveState.gridScrollbarSlot = { gridState, mod ->
                val cs = MaterialTheme.colorScheme
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    style = defaultScrollbarStyle().copy(
                        minimalHeight = 28.dp,
                        thickness = 5.dp,
                        shape = RoundedCornerShape(3.dp),
                        unhoverColor = cs.onSurface.copy(alpha = 0.16f),
                        hoverColor = cs.onSurface.copy(alpha = 0.40f)
                    ),
                    modifier = mod
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 1.dp)
                )
            }
            DesktopAdaptiveState.scrollScrollbarSlot = { scrollState, mod ->
                val cs = MaterialTheme.colorScheme
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = defaultScrollbarStyle().copy(
                        minimalHeight = 28.dp,
                        thickness = 5.dp,
                        shape = RoundedCornerShape(3.dp),
                        unhoverColor = cs.onSurface.copy(alpha = 0.16f),
                        hoverColor = cs.onSurface.copy(alpha = 0.40f)
                    ),
                    modifier = mod
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp, horizontal = 1.dp)
                )
            }
            onDispose {
                DesktopAdaptiveState.desktopTopBarSlot = null
                DesktopAdaptiveState.listScrollbarSlot = null
                DesktopAdaptiveState.gridScrollbarSlot = null
                DesktopAdaptiveState.scrollScrollbarSlot = null
            }
        }

        MyApplicationTheme(
            darkTheme = useDarkTheme,
            dynamicColor = false,
            themePaletteId = themePaletteId
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                AnimatedDialogVisibility(visible = DesktopAdaptiveState.showShortcutCheatSheet) {
                    DesktopShortcutCheatSheetDialog(
                        onDismiss = { DesktopAdaptiveState.showShortcutCheatSheet = false }
                    )
                }

                // Built-in Studio File Picker (mounts into DesktopModalHost with smooth spring enter/exit)
                DesktopStudioFilePickerHost()

                // Unified Desktop Modal Overlay Stack (renders all StudioModalDialog & StudioAlertDialog entries)
                DesktopModalHost(modifier = Modifier.fillMaxSize())

                // Floating Desktop Toast Overlay (smooth text & width morph, crisp flat border)
                AnimatedVisibility(
                    visible = !toastMessage.isNullOrBlank(),
                    enter = fadeIn(StudioMotion.shortInTween()) + slideInVertically(StudioMotion.mediumInTween()) { it / 2 },
                    exit = fadeOut(StudioMotion.shortOutTween()) + slideOutVertically(StudioMotion.mediumOutTween()) { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp, start = 16.dp, end = 16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier.studioAnimateSize()
                    ) {
                        AnimatedContent(
                            targetState = toastMessage.orEmpty(),
                            transitionSpec = { StudioMotion.subtleFadeScale() },
                            label = "DesktopToastTextMorph"
                        ) { msg ->
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopTopStudioBar(
    appContext: Context,
    isPushToTalk: Boolean = false
) {
    val inputDevices by DesktopAudioDeviceSelector.availableInputDevices.collectAsState()
    val selectedDevice by DesktopAudioDeviceSelector.selectedInputDevice.collectAsState()
    var showDeviceMenu by remember { mutableStateOf(false) }
    val windowMetrics = LocalAppWindowMetrics.current
    val extendedColors = LocalPhonAcqExtendedColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                val barWidth = maxWidth
                val showFullShortcuts = barWidth >= 1080.dp
                val showCompactShortcuts = barWidth >= 820.dp
                val showShortcutPill = barWidth >= 600.dp
                val compactCorpusBtn = barWidth < 600.dp
                val showSidebarToggle = DesktopAdaptiveState.hasCollapsibleProjectSidebar && windowMetrics.isExpandedOrWider

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left: Conditional Sidebar Collapse Toggle + Microphone Selector + Refresh
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedVisibility(
                            visible = showSidebarToggle,
                            enter = StudioMotion.horizontalPaneEnter,
                            exit = StudioMotion.horizontalPaneExit
                        ) {
                            val (sidebarInteraction, isSidebarHoveredState) = rememberStudioInteraction()
                            val isSidebarHovered = isSidebarHoveredState.value
                            val sidebarBg by animateColorAsState(
                                targetValue = if (isSidebarHovered) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                animationSpec = StudioMotion.microTween(),
                                label = "sidebarToggleBg"
                            )
                            val sidebarBorder by animateColorAsState(
                                targetValue = if (isSidebarHovered) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                },
                                animationSpec = StudioMotion.microTween(),
                                label = "sidebarToggleBorder"
                            )
                            val sidebarFg by animateColorAsState(
                                targetValue = if (isSidebarHovered) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = StudioMotion.microTween(),
                                label = "sidebarToggleFg"
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = sidebarBg,
                                border = BorderStroke(1.dp, sidebarBorder),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .height(30.dp)
                                    .studioAnimateSize()
                                    .studioTactile(sidebarInteraction, pressScale = 0.96f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .hoverable(sidebarInteraction)
                                    .clickable(
                                        interactionSource = sidebarInteraction,
                                        indication = null
                                    ) {
                                        DesktopAdaptiveState.isProjectSidebarCollapsed =
                                            !DesktopAdaptiveState.isProjectSidebarCollapsed
                                    }
                            ) {
                                AnimatedContent(
                                    targetState = DesktopAdaptiveState.isProjectSidebarCollapsed,
                                    transitionSpec = { StudioMotion.subtleFadeScale() },
                                    label = "sidebarToggleState"
                                ) { collapsed ->
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (collapsed) {
                                                Icons.Default.Menu
                                            } else {
                                                Icons.AutoMirrored.Filled.MenuOpen
                                            },
                                            contentDescription = S.desktop.toggleSidebarDesc,
                                            tint = sidebarFg,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (collapsed) S.desktop.expandSidebarLabel else S.desktop.collapseSidebarLabel,
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = sidebarFg
                                        )
                                    }
                                }
                            }
                        }

                        // Microphone Selector Pill with Status Dot, Unified 8dp Radius & Rotating Arrow
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            val (micInteraction, isMicHoveredState) = rememberStudioInteraction()
                            val isMicHovered = isMicHoveredState.value
                            val micBg by animateColorAsState(
                                targetValue = if (isMicHovered || showDeviceMenu) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                animationSpec = StudioMotion.microTween(),
                                label = "micPillBg"
                            )
                            val micBorder by animateColorAsState(
                                targetValue = if (isMicHovered || showDeviceMenu) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                },
                                animationSpec = StudioMotion.microTween(),
                                label = "micPillBorder"
                            )
                            val micArrowTint by animateColorAsState(
                                targetValue = if (isMicHovered || showDeviceMenu) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = StudioMotion.microTween(),
                                label = "micArrowTint"
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = micBg,
                                border = BorderStroke(1.dp, micBorder),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .height(30.dp)
                                    .studioTactile(micInteraction, pressScale = 0.97f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .hoverable(micInteraction)
                                    .clickable(
                                        interactionSource = micInteraction,
                                        indication = null
                                    ) {
                                        DesktopAudioDeviceSelector.refreshDevices()
                                        showDeviceMenu = true
                                    }
                            ) {
                                val isMicAvailable by DesktopAudioDeviceSelector.isMicAvailable.collectAsState()
                                val dotColor by animateColorAsState(
                                    targetValue = if (isMicAvailable) extendedColors.success else MaterialTheme.colorScheme.error,
                                    animationSpec = StudioMotion.microTween(),
                                    label = "micStatusDot"
                                )
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = S.desktop.inputSoundcardDesc,
                                        tint = if (isMicAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isMicAvailable) {
                                            formatDesktopMicName(selectedDevice ?: inputDevices.firstOrNull())
                                        } else {
                                            S.desktop.noMicDetected
                                        },
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isMicAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(
                                            min = 80.dp,
                                            max = if (barWidth < 980.dp) 168.dp else 260.dp
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = S.desktop.selectSoundcardDesc,
                                        tint = micArrowTint,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .studioRotateOn(showDeviceMenu)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showDeviceMenu,
                                onDismissRequest = { showDeviceMenu = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        if (selectedDevice == null) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = S.desktop.systemDefaultMicFollowWindows,
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedDevice == null) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        DesktopAudioDeviceSelector.selectDevice(null)
                                        showDeviceMenu = false
                                        Toast.makeText(appContext, S.desktop.switchedToDefaultMicToast, Toast.LENGTH_SHORT).show()
                                    }
                                )
                                if (inputDevices.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                                inputDevices.forEach { devName ->
                                    val isCurrent = devName == selectedDevice
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            if (isCurrent) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        text = {
                                            Text(
                                                text = formatDesktopMicName(devName),
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            DesktopAudioDeviceSelector.selectDevice(devName)
                                            showDeviceMenu = false
                                            Toast.makeText(
                                                appContext,
                                                S.desktop.switchedInputSoundcardToast.withArgs("device" to formatDesktopMicName(devName)),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            }
                        }

                        val refreshInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = {
                                val list = DesktopAudioDeviceSelector.refreshDevices()
                                Toast.makeText(appContext, S.desktop.refreshedSoundcardCountToast.withArgs("count" to list.size), Toast.LENGTH_SHORT).show()
                            },
                            interactionSource = refreshInteraction,
                            modifier = Modifier
                                .size(28.dp)
                                .studioTactile(refreshInteraction, pressScale = 0.90f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = S.desktop.refreshSoundcardDesc,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Center: Keycap Shortcut Badges (Hidden gracefully on <600dp narrow snap)
                    AnimatedVisibility(
                        visible = showShortcutPill,
                        enter = StudioMotion.horizontalPaneEnter,
                        exit = StudioMotion.horizontalPaneExit
                    ) {
                        val (shortcutInteraction, isShortcutHoveredState) = rememberStudioInteraction()
                        val isShortcutHovered = isShortcutHoveredState.value
                        val shortcutBg by animateColorAsState(
                            targetValue = if (isShortcutHovered) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.75f)
                            },
                            animationSpec = StudioMotion.microTween(),
                            label = "shortcutBarBg"
                        )
                        val shortcutBorder by animateColorAsState(
                            targetValue = if (isShortcutHovered) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            },
                            animationSpec = StudioMotion.microTween(),
                            label = "shortcutBarBorder"
                        )
                        val shortcutTier = when {
                            showFullShortcuts -> 2
                            showCompactShortcuts -> 1
                            else -> 0
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = shortcutBg,
                            border = BorderStroke(1.dp, shortcutBorder),
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp,
                            modifier = Modifier
                                .height(30.dp)
                                .studioAnimateSize()
                                .studioTactile(shortcutInteraction, pressScale = 0.975f)
                                .clip(RoundedCornerShape(8.dp))
                                .hoverable(shortcutInteraction)
                                .clickable(
                                    interactionSource = shortcutInteraction,
                                    indication = null
                                ) { DesktopAdaptiveState.showShortcutCheatSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = S.desktop.shortcutsIconDesc,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                AnimatedContent(
                                    targetState = shortcutTier to isPushToTalk,
                                    transitionSpec = { StudioMotion.subtleFadeScale() },
                                    label = "ShortcutTierTransition"
                                ) { (tier, ptt) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        when (tier) {
                                            2 -> {
                                                KeycapBadge(
                                                    keyText = "Space",
                                                    actionText = if (ptt) "按住录音" else S.desktop.keyRecStop
                                                )
                                                KeycapBadge(keyText = "← / →", actionText = S.desktop.keyPrevNext)
                                                KeycapBadge(keyText = "Enter", actionText = S.desktop.keyListen)
                                                KeycapBadge(keyText = "M", actionText = S.desktop.keyFullClean)
                                                KeycapBadge(keyText = "Esc", actionText = S.desktop.keyBack)
                                            }
                                            1 -> {
                                                KeycapBadge(
                                                    keyText = "Space",
                                                    actionText = if (ptt) "按住录" else S.desktop.keyRecCompact
                                                )
                                                KeycapBadge(keyText = "← / →", actionText = S.desktop.keyTurnCompact)
                                                KeycapBadge(keyText = "M", actionText = S.desktop.keyFullCompact)
                                                Text(
                                                    text = S.desktop.shortcutsBadgeAll,
                                                    fontSize = 11.sp,
                                                    lineHeight = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = S.desktop.shortcutsBadgeShort,
                                                    fontSize = 11.sp,
                                                    lineHeight = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    // Right: Open Local Corpus Folder (Degrades cleanly to icon + Tooltip on <600dp)
                    val openCorpusAction: () -> Unit = {
                        try {
                            val dir = Context.appDataRootDir
                            val osName = System.getProperty("os.name", "").lowercase()
                            if (osName.contains("win")) {
                                ProcessBuilder("explorer.exe", dir.absolutePath).start()
                            } else if (osName.contains("mac") || osName.contains("darwin")) {
                                ProcessBuilder("open", dir.absolutePath).start()
                            } else if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(dir)
                            }
                        } catch (_: Exception) {
                            Toast.makeText(
                                appContext,
                                S.desktop.corpusDirFullLine.withArgs("path" to Context.appDataRootDir.absolutePath),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val corpusInteraction = remember { MutableInteractionSource() }
                    if (compactCorpusBtn) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text(S.desktop.corpusDirShortLabel)
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            FilledTonalIconButton(
                                onClick = openCorpusAction,
                                interactionSource = corpusInteraction,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .size(30.dp)
                                    .studioTactile(corpusInteraction, pressScale = 0.94f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = S.desktop.corpusDirShortLabel,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    } else {
                        FilledTonalButton(
                            onClick = openCorpusAction,
                            interactionSource = corpusInteraction,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .studioTactile(corpusInteraction, pressScale = 0.96f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = S.desktop.corpusDirShortLabel,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun DesktopShortcutCheatSheetDialog(
    onDismiss: () -> Unit
) {
    val shortcuts = remember(S) { S.desktop.shortcutRows }

    StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AdaptiveModalSurface(targetMaxWidth = 540.dp) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = S.desktop.shortcutsDialogTitle,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = S.desktop.shortcutsDialogIntro,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shortcuts.forEach { (keyName, desc) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                KeycapBadge(keyText = keyName)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Text(S.common.gotIt, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
