package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.LinkRole
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.KeycapBadge
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.viewmodel.RecordingViewModel
import kotlin.math.max
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    onNavigateBack: () -> Unit,
    onViewArchive: () -> Unit = {},
    onOpenFullSettings: () -> Unit = {},
    onOpenWirelessLink: (() -> Unit)? = null,
    onToggleQuickSettingsSidebar: (() -> Unit)? = null,
    isSplitPaneMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeProject by viewModel.currentProject.collectAsState()
    val rawWords by viewModel.currentWords.collectAsState()
    val wordSequence by viewModel.wordSequence.collectAsState()
    val currentSeqIndex by viewModel.currentSeqIndex.collectAsState()

    val isRecording by viewModel.isRecording.collectAsState()
    val isPushToTalk by viewModel.pushToTalkEnabled.collectAsState()
    val autoAdvanceOnHoldRelease by viewModel.autoAdvanceOnHoldRelease.collectAsState()
    val lastHoldQualityNotice by viewModel.lastHoldQualityNotice.collectAsState()
    val wirelessRole by viewModel.wirelessLink.activeRole.collectAsState()
    val isAutoNext by viewModel.autoNextEnabled.collectAsState()
    val autoPlayAfterRecord by viewModel.autoPlayAfterRecord.collectAsState()
    val skipSilenceOnPlayback by viewModel.skipSilenceOnPlayback.collectAsState()
    val volumeKeyControlEnabled by viewModel.volumeKeyControlEnabled.collectAsState()
    val participantCleanMode by viewModel.participantCleanMode.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val confirmOverwrite by viewModel.confirmOverwriteRecording.collectAsState()
    val hideRemarkInCleanMode by viewModel.hideRemarkInCleanMode.collectAsState()
    val hideTextInCleanMode by viewModel.hideTextInCleanMode.collectAsState()
    val enablePicturePrompts = activeProject?.enablePicturePrompts == true

    val vadThreshold by viewModel.vadThreshold.collectAsState()
    val silenceDelayMs by viewModel.silenceDelayMs.collectAsState()
    val lowVolumeThreshold by viewModel.lowVolumeThreshold.collectAsState()
    val creakySensitivity by viewModel.creakySensitivity.collectAsState()
    val characterSize by viewModel.characterSize.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val shufflingMode by viewModel.shufflingMode.collectAsState()
    val onlyPendingWords by viewModel.onlyPendingWords.collectAsState()
    val playingWordId by viewModel.playingWordId.collectAsState()

    val speakers by viewModel.allSpeakers.collectAsState()
    val activeSpeaker by viewModel.currentSpeaker.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val effectivePushToTalk = isPushToTalk

    // Ordered words following active sequence (original, randomized, or pending-only)
    val orderedWords = remember(rawWords, wordSequence) {
        if (rawWords.isEmpty()) {
            emptyList()
        } else if (wordSequence.isNotEmpty()) {
            wordSequence.mapNotNull { idx -> rawWords.getOrNull(idx) }
        } else {
            rawWords
        }
    }

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSpeakerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }
    var showOnboardingTip by remember { mutableStateOf(false) }
    var showOverwriteConfirmDialog by remember { mutableStateOf(false) }

    SuspendDesktopShortcutsWhen(
        showSettingsSheet ||
            showSpeakerDialog ||
            showDeleteConfirmDialog ||
            showClearAllConfirmDialog ||
            showOnboardingTip ||
            showOverwriteConfirmDialog
    )

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentView = LocalView.current

    DisposableEffect(keepScreenOn) {
        val prevKeepOn = currentView.keepScreenOn
        currentView.keepScreenOn = keepScreenOn
        onDispose {
            currentView.keepScreenOn = prevKeepOn
        }
    }

    val cleanModeBackState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = participantCleanMode,
        state = cleanModeBackState,
        onBack = { viewModel.setParticipantCleanMode(false) }
    )

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayback()
        }
    }

    LaunchedEffect(lastHoldQualityNotice) {
        val msg = lastHoldQualityNotice ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.clearHoldQualityNotice()
    }

    DisposableEffect(context, wirelessRole) {
        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (wirelessRole == LinkRole.STANDALONE && hasMicPerm) {
            viewModel.audioRecorder.setStandbyPreRollActive(true)
        }
        onDispose {
            if (viewModel.wirelessLink.activeRole.value == LinkRole.STANDALONE) {
                viewModel.audioRecorder.setStandbyPreRollActive(false)
            }
        }
    }

    val recordedCount = remember(rawWords) {
        rawWords.count { it.isRecorded }
    }
    val unrecordedCount = remember(rawWords) {
        rawWords.count { !it.isRecorded }
    }
    val flaggedCount = remember(rawWords) {
        rawWords.count { word ->
            word.isRecorded && word.qualityStatus != null && word.qualityStatus != "正常"
        }
    }

    var dismissBluetoothBanner by remember { mutableStateOf(false) }
    var hasBluetoothInputMic by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        fun checkBtInput(): Boolean {
            return try {
                val inputs = audioMgr?.getDevices(AudioManager.GET_DEVICES_INPUTS).orEmpty()
                val hasHigherPriorityWiredOrUsbMic = inputs.any { dev ->
                    dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                }
                if (hasHigherPriorityWiredOrUsbMic) return false
                inputs.any { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
            } catch (_: Exception) {
                false
            }
        }
        hasBluetoothInputMic = checkBtInput()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                hasBluetoothInputMic = checkBtInput()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                hasBluetoothInputMic = checkBtInput()
            }
        }
        try {
            audioMgr?.registerAudioDeviceCallback(callback, null)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                audioMgr?.unregisterAudioDeviceCallback(callback)
            } catch (_: Exception) {
            }
        }
    }
    var dismissSampleSpeakerTip by remember(activeSpeaker?.id) { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.predictiveBackLayer(cleanModeBackState)
    ) {
        val isShortScreenHeader = maxHeight < 460.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (isShortScreenHeader) {
                    // Compact 46.dp Studio Header for Mobile Landscape (360dp~430dp height)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                if (!isSplitPaneMode || participantCleanMode) {
                                    IconButton(
                                        onClick = {
                                            if (participantCleanMode) {
                                                viewModel.setParticipantCleanMode(false)
                                            } else {
                                                onNavigateBack()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("record_back_button")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = if (participantCleanMode) S.recording.exitFullScreenCleanDesc else S.common.back,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = activeProject?.name ?: S.recording.studioFallbackTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (orderedWords.isNotEmpty()) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (participantCleanMode) {
                                            S.recording.cleanModeProgress.withArgs(
                                                "pos" to currentSeqIndex + 1,
                                                "total" to orderedWords.size
                                            )
                                        } else {
                                            S.recording.normalModeProgress.withArgs(
                                                "recorded" to recordedCount,
                                                "total" to rawWords.size,
                                                "pos" to currentSeqIndex + 1
                                            )
                                        },
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (participantCleanMode) {
                                    if (enablePicturePrompts) {
                                        val isWordTextShown = !hideTextInCleanMode
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isWordTextShown) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.setHideTextInCleanMode(!hideTextInCleanMode) }
                                                .testTag("clean_mode_toggle_word_text_chip")
                                        ) {
                                            Text(
                                                text = if (isWordTextShown) S.recording.showTextLabel else S.recording.hideTextLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = if (isWordTextShown) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    val isRemarkShown = !hideRemarkInCleanMode
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isRemarkShown) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setHideRemarkInCleanMode(!hideRemarkInCleanMode) }
                                            .testTag("clean_mode_toggle_remark_chip")
                                    ) {
                                        Text(
                                            text = if (isRemarkShown) S.recording.shownRemarkLabel else S.recording.hiddenRemarkLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (isRemarkShown) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (participantCleanMode) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setParticipantCleanMode(!participantCleanMode) }
                                        .testTag("participant_clean_mode_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (participantCleanMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (participantCleanMode) S.recording.exitCleanModeDesc else S.recording.enterCleanModeDesc,
                                            tint = if (participantCleanMode) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = if (participantCleanMode) {
                                                if (DesktopAdaptiveState.isDesktopPlatform) S.recording.exitFullScreenCleanShort else S.recording.cleanModePillExit
                                            } else {
                                                S.recording.cleanModePillEnter
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (participantCleanMode) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                        if (participantCleanMode && DesktopAdaptiveState.isDesktopPlatform) {
                                            KeycapBadge("Esc")
                                        }
                                    }
                                }
                                if (!participantCleanMode) {
                                    IconButton(
                                        onClick = onViewArchive,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("view_archive_button")
                                    ) {
                                        Icon(
                                            Icons.Default.LibraryMusic,
                                            contentDescription = S.recording.openArchiveDesc,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isSplitPaneMode && onToggleQuickSettingsSidebar != null) {
                                                onToggleQuickSettingsSidebar()
                                            } else {
                                                showSettingsSheet = true
                                            }
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("vad_settings_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Tune,
                                            contentDescription = S.recording.openQuickSettingsDesc,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = activeProject?.name ?: S.recording.studioFallbackTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (orderedWords.isNotEmpty()) {
                                    Text(
                                        text = if (participantCleanMode) {
                                            S.recording.desktopCleanModeProgress.withArgs(
                                                "pos" to currentSeqIndex + 1,
                                                "total" to orderedWords.size
                                            )
                                        } else if (onlyPendingWords) {
                                            S.recording.desktopNormalModePendingProgress.withArgs(
                                                "recorded" to recordedCount,
                                                "total" to rawWords.size,
                                                "pos" to currentSeqIndex + 1,
                                                "remaining" to orderedWords.size
                                            )
                                        } else {
                                            S.recording.desktopNormalModeProgress.withArgs(
                                                "recorded" to recordedCount,
                                                "total" to rawWords.size,
                                                "pos" to currentSeqIndex + 1
                                            )
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (!isSplitPaneMode || participantCleanMode) {
                                IconButton(
                                    onClick = {
                                        if (participantCleanMode) {
                                            viewModel.setParticipantCleanMode(false)
                                        } else {
                                            onNavigateBack()
                                        }
                                    },
                                    modifier = Modifier.testTag("record_back_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = if (participantCleanMode) S.recording.exitFullScreenCleanDesc else S.common.back
                                    )
                                }
                            }
                        },
                        actions = {
                            if (participantCleanMode) {
                                if (enablePicturePrompts) {
                                    val isWordTextShown = !hideTextInCleanMode
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isWordTextShown) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.setHideTextInCleanMode(!hideTextInCleanMode) }
                                            .testTag("clean_mode_toggle_word_text_chip")
                                    ) {
                                        Text(
                                            text = if (isWordTextShown) S.recording.showTextLabel else S.recording.hideTextLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (isWordTextShown) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                val isRemarkShown = !hideRemarkInCleanMode
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isRemarkShown) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setHideRemarkInCleanMode(!hideRemarkInCleanMode) }
                                        .testTag("clean_mode_toggle_remark_chip")
                                ) {
                                    Text(
                                        text = if (isRemarkShown) S.recording.shownRemarkLabel else S.recording.hiddenRemarkLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = if (isRemarkShown) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (participantCleanMode) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                },
                                modifier = Modifier
                                    .padding(end = if (participantCleanMode) 4.dp else 2.dp)
                                    .defaultMinSize(minHeight = 36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.setParticipantCleanMode(!participantCleanMode) }
                                    .testTag("participant_clean_mode_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (participantCleanMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (participantCleanMode) S.recording.exitCleanModeDesc else S.recording.enterCleanModeDesc,
                                        tint = if (participantCleanMode) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (participantCleanMode) {
                                            if (DesktopAdaptiveState.isDesktopPlatform) S.recording.exitFullScreenCleanShort else S.recording.cleanModePillExit
                                        } else {
                                            S.recording.cleanModePillEnter
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = if (participantCleanMode) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (participantCleanMode && DesktopAdaptiveState.isDesktopPlatform) {
                                        KeycapBadge("Esc")
                                    }
                                }
                            }

                            if (!participantCleanMode) {
                                if (onOpenWirelessLink != null) {
                                    IconButton(
                                        onClick = onOpenWirelessLink,
                                        modifier = Modifier.testTag("recording_wireless_link_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Devices,
                                            contentDescription = S.nav.wirelessLink
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onViewArchive,
                                    modifier = Modifier.testTag("view_archive_button")
                                ) {
                                    Icon(
                                        Icons.Default.LibraryMusic,
                                        contentDescription = S.recording.openArchiveDesc,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isSplitPaneMode && onToggleQuickSettingsSidebar != null) {
                                            onToggleQuickSettingsSidebar()
                                        } else {
                                            showSettingsSheet = true
                                        }
                                    },
                                    modifier = Modifier.testTag("vad_settings_button")
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = S.recording.openQuickSettingsDesc)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        ) { padding ->
            if (orderedWords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(S.recording.emptyWordListTitle, style = MaterialTheme.typography.titleMedium)
                        Text(
                            S.recording.emptyWordListHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val safeCurrentIndex = currentSeqIndex.coerceIn(0, max(0, orderedWords.size - 1))
                val currentWord = orderedWords.getOrNull(safeCurrentIndex) ?: orderedWords.first()

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    val isWideWorkspace = maxWidth >= 620.dp && maxHeight >= 430.dp && !participantCleanMode
                    val isCompactStageHeight = maxHeight < 620.dp

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!participantCleanMode && !isShortScreenHeader) {
                            if (hasBluetoothInputMic && !dismissBluetoothBanner) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = S.recording.bluetoothCutBannerText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { dismissBluetoothBanner = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = S.recording.dismissTipDesc,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            } else if (activeSpeaker?.name == "示例发音人" && recordedCount == 0 && !dismissSampleSpeakerTip) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSpeakerDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = S.recording.sampleSpeakerBannerText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { dismissSampleSpeakerTip = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = S.recording.dismissTipDesc,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(
                                targetState = isWideWorkspace,
                                transitionSpec = {
                                    (fadeIn(tween(210)) + scaleIn(initialScale = 0.985f, animationSpec = tween(210))) togetherWith
                                        fadeOut(tween(150))
                                },
                                label = "RecordingWideModeTransition",
                                modifier = Modifier.fillMaxSize()
                            ) { wideMode ->
                                if (wideMode) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        TabletWordSidebar(
                                            words = orderedWords,
                                            currentIndex = safeCurrentIndex,
                                            activeSpeaker = activeSpeaker,
                                            recordedCount = recordedCount,
                                            unrecordedCount = unrecordedCount,
                                            flaggedCount = flaggedCount,
                                            isShuffleEnabled = isShuffleEnabled,
                                            onlyPendingWords = onlyPendingWords,
                                            enablePicturePrompts = enablePicturePrompts,
                                            onSelectWord = { idx ->
                                                viewModel.selectWordBySequencePosition(idx)
                                            },
                                            onTriggerSpeakerDialog = { showSpeakerDialog = true },
                                            onShuffle = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val nextState = !isShuffleEnabled
                                                viewModel.toggleShuffle()
                                                Toast.makeText(
                                                    context,
                                                    if (nextState) S.recording.shuffleEnabledToast else S.recording.shuffleRestoredToast,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onToggleOnlyPending = {
                                                val enabledNow = viewModel.toggleOnlyPendingWords()
                                                Toast.makeText(
                                                    context,
                                                    if (enabledNow) S.recording.onlyPendingEnabledToast else S.recording.showAllWordsToast,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onJumpNextUnrecorded = { viewModel.jumpToNextUnrecorded() },
                                            onJumpNextFlagged = { viewModel.jumpToNextFlaggedWord() },
                                            onRequestClearAll = { showClearAllConfirmDialog = true }
                                        )

                                        RecordingStagePane(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .padding(horizontal = 24.dp, vertical = 12.dp),
                                            viewModel = viewModel,
                                            words = orderedWords,
                                            totalProjectWords = rawWords.size,
                                            safeCurrentIndex = safeCurrentIndex,
                                            currentWord = currentWord,
                                            activeSpeaker = activeSpeaker,
                                            activeProject = activeProject,
                                            isRecording = isRecording,
                                            isPushToTalk = effectivePushToTalk,
                                            isShuffleEnabled = isShuffleEnabled,
                                            onlyPendingWords = onlyPendingWords,
                                            playingWordId = playingWordId,
                                            characterSize = characterSize,
                                            participantCleanMode = false,
                                            hideRemarkInCleanMode = hideRemarkInCleanMode,
                                            hideTextInCleanMode = hideTextInCleanMode,
                                            enablePicturePrompts = enablePicturePrompts,
                                            confirmOverwrite = confirmOverwrite,
                                            isTablet = true,
                                            isCompactStageHeight = isCompactStageHeight,
                                            recordedCount = recordedCount,
                                            unrecordedCount = unrecordedCount,
                                            flaggedCount = flaggedCount,
                                            onTriggerSpeakerDialog = { showSpeakerDialog = true },
                                            onRequestDeleteCurrent = { showDeleteConfirmDialog = true },
                                            onRequestClearAll = { showClearAllConfirmDialog = true },
                                            onRequestOverwriteConfirm = { showOverwriteConfirmDialog = true },
                                            onOpenArchive = onViewArchive
                                        )
                                    }
                                } else {
                                    RecordingStagePane(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        viewModel = viewModel,
                                        words = orderedWords,
                                        totalProjectWords = rawWords.size,
                                        safeCurrentIndex = safeCurrentIndex,
                                        currentWord = currentWord,
                                        activeSpeaker = activeSpeaker,
                                        activeProject = activeProject,
                                        isRecording = isRecording,
                                        isPushToTalk = effectivePushToTalk,
                                        isShuffleEnabled = isShuffleEnabled,
                                        onlyPendingWords = onlyPendingWords,
                                        playingWordId = playingWordId,
                                        characterSize = characterSize,
                                        participantCleanMode = participantCleanMode,
                                        hideRemarkInCleanMode = hideRemarkInCleanMode,
                                        hideTextInCleanMode = hideTextInCleanMode,
                                        enablePicturePrompts = enablePicturePrompts,
                                        confirmOverwrite = confirmOverwrite,
                                        isTablet = false,
                                        isCompactStageHeight = isCompactStageHeight,
                                        recordedCount = recordedCount,
                                        unrecordedCount = unrecordedCount,
                                        flaggedCount = flaggedCount,
                                        onTriggerSpeakerDialog = { showSpeakerDialog = true },
                                        onRequestDeleteCurrent = { showDeleteConfirmDialog = true },
                                        onRequestClearAll = { showClearAllConfirmDialog = true },
                                        onRequestOverwriteConfirm = { showOverwriteConfirmDialog = true },
                                        onOpenArchive = onViewArchive
                                    )
                                }
                            }
                        }
                    }
                }

                com.example.ui.components.AnimatedDialogVisibility(visible = showOverwriteConfirmDialog) {
                    com.example.ui.components.StudioAlertDialog(
                        onDismissRequest = { showOverwriteConfirmDialog = false },
                        title = { Text(S.recording.overwriteDialogTitle, fontWeight = FontWeight.Bold) },
                        text = {
                            Text(S.recording.overwriteDialogBody.withArgs("word" to currentWord.text))
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showOverwriteConfirmDialog = false
                                    viewModel.triggerStartRecording()
                                }
                            ) {
                                Text(S.recording.startRerecordButton)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showOverwriteConfirmDialog = false }) {
                                Text(S.common.cancel)
                            }
                        }
                    )
                }

                // Confirm Delete Single Recording Dialog
                com.example.ui.components.AnimatedDialogVisibility(visible = showDeleteConfirmDialog) {
                    com.example.ui.components.StudioAlertDialog(
                        onDismissRequest = { showDeleteConfirmDialog = false },
                        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text(S.recording.deleteCurrentTakeTitle) },
                        text = {
                            Text(
                                S.recording.deleteCurrentTakeBody.withArgs(
                                    "speaker" to (activeSpeaker?.name ?: S.common.defaultLabel),
                                    "word" to currentWord.text
                                )
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.removeRecordingOfWord(currentWord)
                                    showDeleteConfirmDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(S.project.confirmDelete)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                Text(S.common.cancel)
                            }
                        }
                    )
                }

                // Confirm Clear All Speaker Recordings in Project Dialog
                com.example.ui.components.AnimatedDialogVisibility(visible = showClearAllConfirmDialog) {
                    com.example.ui.components.StudioAlertDialog(
                        onDismissRequest = { showClearAllConfirmDialog = false },
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text(S.recording.clearSpeakerRecordingsTitle) },
                        text = {
                            Text(
                                S.recording.clearSpeakerRecordingsBody.withArgs(
                                    "speaker" to (activeSpeaker?.name ?: S.common.defaultLabel)
                                )
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val pid = activeProject?.id
                                    val sid = activeSpeaker?.id
                                    if (pid != null && sid != null) {
                                        viewModel.deleteSpeakerRecordingsInProject(pid, sid)
                                    }
                                    showClearAllConfirmDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(S.recording.clearAllButton)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearAllConfirmDialog = false }) {
                                Text(S.common.cancel)
                            }
                        }
                    )
                }
            }

            // Fieldwork Guide Dialog (Plain, human-friendly tips without geeky jargon)
            com.example.ui.components.AnimatedDialogVisibility(visible = showOnboardingTip) {
                com.example.ui.components.StudioAlertDialog(
                    onDismissRequest = { showOnboardingTip = false },
                    icon = { Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = { Text(S.recording.quickTipsDialogTitle, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                S.recording.quickTip1,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                S.recording.quickTip2,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                S.recording.quickTip3,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                S.recording.quickTip4,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showOnboardingTip = false }) {
                            Text(S.common.gotIt)
                        }
                    }
                )
            }

            // Speaker Management Dialog
            com.example.ui.components.AnimatedDialogVisibility(visible = showSpeakerDialog) {
                val speakerGroups by viewModel.speakerGroups.collectAsState()
                val projectSummary = viewModel.projectSummaries.collectAsState().value[activeProject?.id]
                SpeakerSelectionAndManagementDialog(
                    speakers = speakers,
                    activeSpeaker = activeSpeaker,
                    projectGroupName = activeProject?.speakerGroupName ?: "",
                    speakerGroups = speakerGroups,
                    speakerProgressMap = projectSummary?.speakerProgressList?.associateBy({ it.speaker.id }, { it }) ?: emptyMap(),
                    totalWords = rawWords.size,
                    viewModel = viewModel,
                    onSelectSpeaker = {
                        viewModel.selectSpeaker(it)
                        showSpeakerDialog = false
                    },
                    onAddSpeaker = { name, gender, age, dialect, notes, groupName ->
                        viewModel.createSpeaker(name, gender, age, dialect, notes, groupName) {}
                    },
                    onUpdateSpeaker = { spk ->
                        viewModel.updateSpeaker(spk) {}
                    },
                    onDeleteSpeaker = { viewModel.deleteSpeaker(it) },
                    onDismiss = { showSpeakerDialog = false }
                )
            }

            // Acoustic & VAD Settings BottomSheet
            if (showSettingsSheet) {
                val vadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val vadSheetScope = rememberCoroutineScope()
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false },
                    sheetState = vadSheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    modifier = Modifier.testTag("vad_settings_sheet")
                ) {
                    RecordingSettingsSheetContent(
                        isAutoNext = isAutoNext,
                        onAutoNextChange = { viewModel.setAutoNextEnabled(it) },
                        isPushToTalk = isPushToTalk,
                        onPushToTalkChange = { viewModel.setPushToTalkEnabled(it) },
                        autoAdvanceOnHoldRelease = autoAdvanceOnHoldRelease,
                        onAutoAdvanceOnHoldReleaseChange = { viewModel.setAutoAdvanceOnHoldRelease(it) },
                        autoPlayAfterRecord = autoPlayAfterRecord,
                        onAutoPlayChange = { viewModel.setAutoPlayAfterRecord(it) },
                        skipSilenceOnPlayback = skipSilenceOnPlayback,
                        onSkipSilenceOnPlaybackChange = { viewModel.setSkipSilenceOnPlayback(it) },
                        volumeKeyControlEnabled = volumeKeyControlEnabled,
                        onVolumeKeyChange = { viewModel.setVolumeKeyControlEnabled(it) },
                        vadThreshold = vadThreshold,
                        onVadThresholdChange = { viewModel.setVadThreshold(it) },
                        onApplyVadPreset = { viewModel.applyVadSensitivityPreset(it) },
                        silenceDelayMs = silenceDelayMs,
                        onSilenceDelayChange = { viewModel.setSilenceDelayMs(it) },
                        characterSize = characterSize,
                        onCharacterSizeChange = { viewModel.setCharacterSize(it) },
                        hideRemarkInCleanMode = hideRemarkInCleanMode,
                        onHideRemarkInCleanModeChange = { viewModel.setHideRemarkInCleanMode(it) },
                        isShuffleEnabled = isShuffleEnabled,
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        shufflingMode = shufflingMode,
                        onShufflingModeChange = { viewModel.setShufflingMode(it) },
                        lowVolumeThreshold = lowVolumeThreshold,
                        onLowVolumeThresholdChange = { viewModel.setLowVolumeThreshold(it) },
                        creakySensitivity = creakySensitivity,
                        onCreakySensitivityChange = { viewModel.setCreakySensitivity(it) },
                        recordedCount = recordedCount,
                        onRequestClearAll = {
                            showSettingsSheet = false
                            showClearAllConfirmDialog = true
                        },
                        onOpenFullSettings = {
                            showSettingsSheet = false
                            onOpenFullSettings()
                        },
                        onClose = {
                            vadSheetScope.launch { vadSheetState.hide() }.invokeOnCompletion {
                                showSettingsSheet = false
                            }
                        },
                        isSimpleMode = isSimpleMode
                    )
                }
            }
        }
    }
}
