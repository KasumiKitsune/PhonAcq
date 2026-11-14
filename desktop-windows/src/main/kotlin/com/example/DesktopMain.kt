package com.example

import android.app.Application
import android.content.Context
import android.media.DesktopAudioDeviceSelector
import android.widget.DesktopToastHostState
import android.widget.Toast
import androidx.activity.compose.DesktopBackDispatcher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.data.AppDatabase
import com.example.data.ProjectRepository
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.KeycapBadge
import com.example.ui.components.studioTactile
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.RecordingViewModel
import java.awt.Desktop
import java.awt.Dimension

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

fun main() = application {
    LaunchedEffect(Unit) {
        DesktopAdaptiveState.isDesktopPlatform = true
    }

    val appContext = remember { Application.instance }
    val database = remember { AppDatabase.getDatabase(appContext) }
    val repository = remember { ProjectRepository(database) }
    val viewModel = remember { RecordingViewModel(appContext, repository) }

    val currentProject by viewModel.currentProject.collectAsState()
    val windowIconPainter = DesktopIconRenderer.rememberWindowIconPainter()

    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 840.dp),
        position = WindowPosition(Alignment.Center)
    )

    val windowTitle = remember(currentProject?.name) {
        if (!currentProject?.name.isNullOrBlank()) {
            "PhonAcq · ${currentProject!!.name}"
        } else {
            S.desktop.windowTitle
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = windowTitle,
        icon = windowIconPainter,
        onPreviewKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                val bareAllowed = DesktopAdaptiveState.canTriggerBareKeyShortcuts
                when (keyEvent.key) {
                    Key.Escape -> {
                        if (viewModel.isRecording.value) {
                            viewModel.triggerStopRecording()
                            return@Window true
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
        // Apply multi-resolution AWT Window icons (16, 24, 32, 48, 64, 128, 256) for crisp Windows taskbar & titlebar
        LaunchedEffect(Unit) {
            try {
                window.minimumSize = Dimension(960, 640)
                window.iconImages = DesktopIconRenderer.getMultiResolutionWindowIcons()
            } catch (_: Exception) {
            }
        }

        val themeMode by viewModel.themeMode.collectAsState()
        val systemInDark = isSystemInDarkTheme()
        val useDarkTheme = when (themeMode) {
            "LIGHT" -> false
            "DARK" -> true
            else -> systemInDark
        }

        val toastMessage by DesktopToastHostState.currentMessage.collectAsState()

        // Register the integrated studio bar slot into MainScreen so NavigationRail spans full window height
        DisposableEffect(Unit) {
            DesktopAdaptiveState.desktopTopBarSlot = {
                DesktopTopStudioBar(appContext = appContext)
            }
            onDispose {
                DesktopAdaptiveState.desktopTopBarSlot = null
            }
        }

        MyApplicationTheme(darkTheme = useDarkTheme, dynamicColor = false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )

                if (DesktopAdaptiveState.showShortcutCheatSheet) {
                    DesktopShortcutCheatSheetDialog(
                        onDismiss = { DesktopAdaptiveState.showShortcutCheatSheet = false }
                    )
                }

                // Floating Desktop Toast Overlay
                AnimatedVisibility(
                    visible = !toastMessage.isNullOrBlank(),
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = 8.dp
                    ) {
                        Text(
                            text = toastMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopTopStudioBar(appContext: Context) {
    val inputDevices by DesktopAudioDeviceSelector.availableInputDevices.collectAsState()
    val selectedDevice by DesktopAudioDeviceSelector.selectedInputDevice.collectAsState()
    var showDeviceMenu by remember { mutableStateOf(false) }

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

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Sidebar Collapse Toggle + Microphone / External Soundcard Selector Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val (sidebarInteraction, isSidebarHoveredState) = com.example.ui.components.rememberStudioInteraction()
                        val isSidebarHovered = isSidebarHoveredState.value
                        val sidebarBg by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSidebarHovered) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            animationSpec = androidx.compose.animation.core.tween(150),
                            label = "sidebarToggleBg"
                        )
                        val sidebarBorder by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isSidebarHovered) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                            animationSpec = androidx.compose.animation.core.tween(150),
                            label = "sidebarToggleBorder"
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = sidebarBg,
                            border = BorderStroke(1.dp, sidebarBorder),
                            modifier = Modifier
                                .height(30.dp)
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
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (DesktopAdaptiveState.isProjectSidebarCollapsed) {
                                        Icons.Default.Menu
                                    } else {
                                        Icons.AutoMirrored.Filled.MenuOpen
                                    },
                                    contentDescription = S.desktop.toggleSidebarDesc,
                                    tint = if (isSidebarHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (DesktopAdaptiveState.isProjectSidebarCollapsed) S.desktop.expandSidebarLabel else S.desktop.collapseSidebarLabel,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (isSidebarHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Microphone Selector Pill with Status Dot, Border & Dropdown Arrow
                        Box {
                            val (micInteraction, isMicHoveredState) = com.example.ui.components.rememberStudioInteraction()
                            val isMicHovered = isMicHoveredState.value
                            val micBg by androidx.compose.animation.animateColorAsState(
                                targetValue = if (isMicHovered || showDeviceMenu) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                animationSpec = androidx.compose.animation.core.tween(150),
                                label = "micPillBg"
                            )
                            val micBorder by androidx.compose.animation.animateColorAsState(
                                targetValue = if (isMicHovered || showDeviceMenu) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                },
                                animationSpec = androidx.compose.animation.core.tween(150),
                                label = "micPillBorder"
                            )

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = micBg,
                                border = BorderStroke(1.dp, micBorder),
                                modifier = Modifier
                                    .height(30.dp)
                                    .studioTactile(micInteraction, pressScale = 0.97f)
                                    .clip(RoundedCornerShape(9.dp))
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
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isMicAvailable) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                            )
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
                                            min = 96.dp,
                                            max = if (barWidth < 980.dp) 168.dp else 230.dp
                                        )
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = S.desktop.selectSoundcardDesc,
                                        tint = if (isMicHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
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

                    // Center: Keycap Shortcut Badges (Responsive to window width, clickable to open Shortcut Cheat Sheet)
                    val (shortcutInteraction, isShortcutHoveredState) = com.example.ui.components.rememberStudioInteraction()
                    val isShortcutHovered = isShortcutHoveredState.value
                    val shortcutBg by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isShortcutHovered) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
                        },
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "shortcutBarBg"
                    )
                    val shortcutBorder by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isShortcutHovered) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        },
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "shortcutBarBorder"
                    )

                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = shortcutBg,
                        border = BorderStroke(1.dp, shortcutBorder),
                        modifier = Modifier
                            .height(30.dp)
                            .studioTactile(shortcutInteraction, pressScale = 0.975f)
                            .clip(RoundedCornerShape(9.dp))
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
                            when {
                                showFullShortcuts -> {
                                    KeycapBadge(keyText = "Space", actionText = S.desktop.keyRecStop)
                                    KeycapBadge(keyText = "← / →", actionText = S.desktop.keyPrevNext)
                                    KeycapBadge(keyText = "Enter", actionText = S.desktop.keyListen)
                                    KeycapBadge(keyText = "M", actionText = S.desktop.keyFullClean)
                                    KeycapBadge(keyText = "Esc", actionText = S.desktop.keyBack)
                                }
                                showCompactShortcuts -> {
                                    KeycapBadge(keyText = "Space", actionText = S.desktop.keyRecCompact)
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

                    // Right: Open Windows Local Corpus Folder
                    val corpusInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = {
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
                        },
                        interactionSource = corpusInteraction,
                        shape = RoundedCornerShape(9.dp),
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
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
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

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        com.example.ui.components.StudioDialogEnterAnimation {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.widthIn(min = 440.dp, max = 560.dp)
            ) {
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
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(S.common.gotIt, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
