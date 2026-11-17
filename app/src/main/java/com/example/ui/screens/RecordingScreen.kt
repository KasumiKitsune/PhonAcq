package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.KeycapBadge
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioSwitchRow
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.ZoomableWordImage
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core. animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.compose.ui.layout.ContentScale
import com.example.audio.AcousticAnalyzer
import com.example.data.Project
import com.example.data.ShufflingMode
import com.example.data.Speaker
import com.example.data.Word
import com.example.data.belongsToGroup
import com.example.data.groupList
import com.example.data.matchesProjectGroup
import com.example.data.parseSpeakerGroups
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.LinkRole
import com.example.utils.WordImageManager
import com.example.utils.rememberWordImageBitmap
import com.example.viewmodel.RecordingViewModel
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                audioMgr?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.any { dev ->
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                } == true
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
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                            text = if (participantCleanMode) S.recording.cleanModePillExit else S.recording.cleanModePillEnter,
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
                                if (DesktopAdaptiveState.isDesktopPlatform) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.setParticipantCleanMode(false) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = S.recording.exitFullScreenCleanShort,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            KeycapBadge("Esc")
                                        }
                                    }
                                }
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (participantCleanMode) S.recording.cleanModePillExit else S.recording.cleanModePillEnter,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingStagePane(
    modifier: Modifier = Modifier,
    viewModel: RecordingViewModel,
    words: List<Word>,
    totalProjectWords: Int = words.size,
    safeCurrentIndex: Int,
    currentWord: Word,
    activeSpeaker: Speaker?,
    activeProject: Project?,
    isRecording: Boolean,
    isPushToTalk: Boolean,
    isShuffleEnabled: Boolean,
    onlyPendingWords: Boolean = false,
    playingWordId: Long?,
    characterSize: String,
    participantCleanMode: Boolean,
    hideRemarkInCleanMode: Boolean,
    hideTextInCleanMode: Boolean = false,
    enablePicturePrompts: Boolean = false,
    confirmOverwrite: Boolean,
    isTablet: Boolean,
    isCompactStageHeight: Boolean = false,
    recordedCount: Int,
    unrecordedCount: Int,
    flaggedCount: Int,
    onTriggerSpeakerDialog: () -> Unit,
    onRequestDeleteCurrent: () -> Unit,
    onRequestClearAll: () -> Unit,
    onRequestOverwriteConfirm: () -> Unit,
    onOpenArchive: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val pagerState = rememberPagerState(
        initialPage = safeCurrentIndex,
        pageCount = { words.size }
    )
    val rowListState = rememberLazyListState()
    val takesRefreshTrigger by viewModel.takesRefreshTrigger.collectAsState()
    val carrierSentenceEnabled by viewModel.carrierSentenceEnabled.collectAsState()
    val carrierSentenceTemplate by viewModel.carrierSentenceTemplate.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()

    var wordForQuickRemark by remember { mutableStateOf<Word?>(null) }
    var wordForTakeSwitcher by remember { mutableStateOf<Word?>(null) }

    val sequenceOrderSignature = remember(words) {
        var h = words.size
        for (i in words.indices) {
            h = 31 * h + words[i].id.hashCode()
        }
        (words.size.toLong() shl 32) or (h.toLong() and 0xFFFFFFFFL)
    }

    // When the word sequence order itself changes (e.g., shuffle toggled or project switched),
    // snap Pager and LazyRow cleanly to safeCurrentIndex (0) without animation fighting.
    LaunchedEffect(sequenceOrderSignature) {
        if (words.isNotEmpty()) {
            if (pagerState.currentPage != safeCurrentIndex || pagerState.currentPageOffsetFraction != 0f) {
                pagerState.scrollToPage(safeCurrentIndex)
            }
            if (!isTablet && !participantCleanMode) {
                rowListState.scrollToItem(max(0, safeCurrentIndex - 1))
            }
        }
    }

    // Synchronize ViewModel index -> Pager (when user clicks Prev/Next buttons or chips)
    LaunchedEffect(safeCurrentIndex, words.size) {
        if (words.isNotEmpty() && pagerState.currentPage != safeCurrentIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(safeCurrentIndex)
        }
        if (words.isNotEmpty() && !isTablet && !participantCleanMode) {
            rowListState.animateScrollToItem(max(0, safeCurrentIndex - 1))
        }
    }

    // Synchronize user swipe on Pager -> ViewModel using settledPage to eliminate scroll fighting
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled in words.indices && settled != safeCurrentIndex) {
            viewModel.selectWordBySequencePosition(settled)
        }
    }

    val currentWordTakeCount by produceState(
        initialValue = if (currentWord.isRecorded) 1 else 0,
        currentWord.id,
        currentWord.isRecorded,
        currentWord.recordingFile,
        activeProject?.id,
        activeSpeaker?.id,
        takesRefreshTrigger
    ) {
        val pid = activeProject?.id
        val sid = activeSpeaker?.id
        value = if (currentWord.isRecorded && pid != null && sid != null) {
            withContext(Dispatchers.IO) {
                viewModel.getAvailableTakesForWord(pid, currentWord.id, sid).size
            }
        } else {
            0
        }
    }

    var isCurrentPageImageZoomed by remember(safeCurrentIndex, currentWord.id, currentWord.imagePath) {
        mutableStateOf(false)
    }

    val renderPagerContent: @Composable (Modifier) -> Unit = { pagerMod ->
        HorizontalPager(
            state = pagerState,
            modifier = pagerMod,
            pageSpacing = 16.dp,
            userScrollEnabled = !isCurrentPageImageZoomed,
            key = { page -> words.getOrNull(page)?.id ?: page }
        ) { page ->
            val pageWord = words.getOrNull(page) ?: currentWord
            val isPlayingThis = playingWordId == pageWord.id
            val takeCount = if (pageWord.id == currentWord.id) {
                currentWordTakeCount
            } else {
                val neighborTakes by produceState(
                    initialValue = if (pageWord.isRecorded) 1 else 0,
                    pageWord.id,
                    pageWord.isRecorded,
                    pageWord.recordingFile,
                    activeProject?.id,
                    activeSpeaker?.id,
                    takesRefreshTrigger
                ) {
                    val pid = activeProject?.id
                    val sid = activeSpeaker?.id
                    value = if (pageWord.isRecorded && pid != null && sid != null) {
                        withContext(Dispatchers.IO) {
                            viewModel.getAvailableTakesForWord(pid, pageWord.id, sid).size
                        }
                    } else {
                        0
                    }
                }
                neighborTakes
            }

            PromptStageLightboxCard(
                word = pageWord,
                index = page,
                total = words.size,
                activeProjectId = activeProject?.id ?: 0L,
                enablePicturePrompts = enablePicturePrompts,
                characterSize = characterSize,
                participantCleanMode = participantCleanMode,
                hideRemarkInCleanMode = hideRemarkInCleanMode,
                hideTextInCleanMode = hideTextInCleanMode,
                carrierSentenceEnabled = !isSimpleMode && carrierSentenceEnabled,
                carrierSentenceTemplate = carrierSentenceTemplate,
                isCompactStageHeight = isCompactStageHeight,
                isPlayingAudio = isPlayingThis,
                takeCount = takeCount,
                showInspectAcoustics = !isSimpleMode,
                onImageZoomActiveChanged = { zoomed ->
                    if (page == safeCurrentIndex) {
                        isCurrentPageImageZoomed = zoomed
                    }
                },
                onPlayClick = {
                    if (page != safeCurrentIndex) {
                        viewModel.selectWordBySequencePosition(page)
                    }
                    viewModel.playWordAudio(pageWord)
                },
                onToggleQualityFlag = {
                    if (pageWord.isRecorded) {
                        val wasNormal = pageWord.qualityStatus == null || pageWord.qualityStatus == "正常"
                        viewModel.toggleManualQualityFlag(pageWord)
                        Toast.makeText(
                            context,
                            if (wasNormal) S.recording.vmMarkedRerecordToast else S.recording.vmConfirmedNormalToast,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onEditRemarkClick = {
                    wordForQuickRemark = pageWord
                },
                onOpenTakeSwitcher = {
                    wordForTakeSwitcher = pageWord
                },
                onShareClick = {
                    val pid = activeProject?.id ?: return@PromptStageLightboxCard
                    val recName = pageWord.recordingFile ?: return@PromptStageLightboxCard
                    val file = File(File(context.getExternalFilesDir(null), "project_$pid"), recName)
                    if (file.exists()) {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    S.recording.shareWordAudioTitle.withArgs("word" to pageWord.text)
                                )
                            )
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                S.recording.openOrShareFailedToast.withArgs("msg" to (e.message ?: S.common.unknownError)),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(context, S.recording.audioFileNotFoundToast, Toast.LENGTH_SHORT).show()
                    }
                },
                onInspectAcoustics = {
                    viewModel.showPhoneticWordDetail(pageWord)
                },
                onDeleteClick = onRequestDeleteCurrent
            )
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val isMobileLandscapeStage = maxHeight < 420.dp && maxWidth >= 480.dp

        if (isMobileLandscapeStage) {
            // Mobile Landscape (e.g. 800x360) 2-Column Studio Split: Left Prompt Stage + Right Controls & VU Monitor
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.12f)
                        .fillMaxHeight()
                ) {
                    if (!isTablet && !participantCleanMode) {
                        LazyRow(
                            state = rowListState,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            itemsIndexed(words, key = { idx, w -> "${idx}_${w.id}" }) { index, word ->
                                val isSelected = index == safeCurrentIndex
                                WordStripChip(
                                    index = index,
                                    word = word,
                                    isSelected = isSelected,
                                    enablePicturePrompts = enablePicturePrompts,
                                    wavFile = null,
                                    onClick = {
                                        viewModel.selectWordBySequencePosition(index)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    renderPagerContent(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.88f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isTablet && !participantCleanMode) {
                        HandheldSessionToolbar(
                            activeSpeaker = activeSpeaker,
                            recordedCount = recordedCount,
                            totalCount = totalProjectWords,
                            unrecordedCount = unrecordedCount,
                            flaggedCount = flaggedCount,
                            isShuffleEnabled = isShuffleEnabled,
                            onlyPendingWords = onlyPendingWords,
                            stackRows = true,
                            onTriggerSpeakerDialog = onTriggerSpeakerDialog,
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
                            onRequestClearAll = onRequestClearAll,
                            onOpenArchive = onOpenArchive
                        )
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }

                    LiveStudioMonitorSection(
                        viewModel = viewModel,
                        currentWord = currentWord,
                        activeProject = activeProject,
                        participantCleanMode = participantCleanMode,
                        isCompact = true,
                        takeCount = currentWordTakeCount,
                        onOpenTakeSwitcher = { wordForTakeSwitcher = currentWord }
                    )

                    RecordingTransportBar(
                        isRecording = isRecording,
                        isPushToTalk = isPushToTalk,
                        canGoPrev = safeCurrentIndex > 0,
                        canGoNext = safeCurrentIndex < words.size - 1,
                        isCompact = true,
                        onPrev = {
                            viewModel.prevWord()
                        },
                        onNext = {
                            viewModel.nextWord()
                        },
                        onToggleRecord = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isRecording && confirmOverwrite && currentWord.isRecorded) {
                                onRequestOverwriteConfirm()
                            } else {
                                viewModel.toggleRecording()
                            }
                        },
                        onStartRecord = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (confirmOverwrite && currentWord.isRecorded) {
                                onRequestOverwriteConfirm()
                            } else {
                                viewModel.triggerStartRecording()
                            }
                        },
                        onStopRecord = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.triggerStopRecording()
                        },
                        onStopFromHoldRelease = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.triggerStopRecordingFromHoldRelease()
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Handheld Top Speaker Bar & Quick Jump Strip
                if (!isTablet && !participantCleanMode) {
                    HandheldSessionToolbar(
                        activeSpeaker = activeSpeaker,
                        recordedCount = recordedCount,
                        totalCount = totalProjectWords,
                        unrecordedCount = unrecordedCount,
                        flaggedCount = flaggedCount,
                        isShuffleEnabled = isShuffleEnabled,
                        onlyPendingWords = onlyPendingWords,
                        onTriggerSpeakerDialog = onTriggerSpeakerDialog,
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
                        onRequestClearAll = onRequestClearAll,
                        onOpenArchive = onOpenArchive
                    )

                    Spacer(Modifier.height(8.dp))

                    LazyRow(
                        state = rowListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        itemsIndexed(words, key = { idx, w -> "${idx}_${w.id}" }) { index, word ->
                            val isSelected = index == safeCurrentIndex
                            WordStripChip(
                                index = index,
                                word = word,
                                isSelected = isSelected,
                                enablePicturePrompts = enablePicturePrompts,
                                wavFile = null,
                                onClick = {
                                    viewModel.selectWordBySequencePosition(index)
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // 2. Central Prompt Lightbox Stage Card (HorizontalPager)
                renderPagerContent(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(Modifier.height(if (isCompactStageHeight) 8.dp else 12.dp))

                // 3. Isolated Live Studio Monitor (VU dBFS Meter + Timer + Multi-Take Badge + Waveform + VAD Progress)
                LiveStudioMonitorSection(
                    viewModel = viewModel,
                    currentWord = currentWord,
                    activeProject = activeProject,
                    participantCleanMode = participantCleanMode,
                    isCompact = isCompactStageHeight,
                    takeCount = currentWordTakeCount,
                    onOpenTakeSwitcher = { wordForTakeSwitcher = currentWord }
                )

                Spacer(Modifier.height(if (isCompactStageHeight) 8.dp else 12.dp))

                // 4. Primary Transport & Recording Controls
                RecordingTransportBar(
                    isRecording = isRecording,
                    isPushToTalk = isPushToTalk,
                    canGoPrev = safeCurrentIndex > 0,
                    canGoNext = safeCurrentIndex < words.size - 1,
                    isCompact = isCompactStageHeight,
                    onPrev = {
                        viewModel.prevWord()
                    },
                    onNext = {
                        viewModel.nextWord()
                    },
                    onToggleRecord = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!isRecording && confirmOverwrite && currentWord.isRecorded) {
                            onRequestOverwriteConfirm()
                        } else {
                            viewModel.toggleRecording()
                        }
                    },
                    onStartRecord = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (confirmOverwrite && currentWord.isRecorded) {
                            onRequestOverwriteConfirm()
                        } else {
                            viewModel.triggerStartRecording()
                        }
                    },
                    onStopRecord = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.triggerStopRecording()
                    },
                    onStopFromHoldRelease = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.triggerStopRecordingFromHoldRelease()
                    }
                )
            }
        }
    }

    val quickToneSymbols by viewModel.quickToneSymbols.collectAsState()
    val quickIpaInitials by viewModel.quickIpaInitials.collectAsState()
    val quickIpaFinals by viewModel.quickIpaFinals.collectAsState()
    val quickFieldTags by viewModel.quickFieldTags.collectAsState()
    var showInlineQuickNoteSettings by remember { mutableStateOf(false) }

    SuspendDesktopShortcutsWhen(
        wordForQuickRemark != null ||
            showInlineQuickNoteSettings ||
            wordForTakeSwitcher != null
    )

    com.example.ui.components.AnimatedNullableDialogVisibility(value = wordForQuickRemark) { targetWord ->
        QuickRemarkEditorDialog(
            word = targetWord,
            speakerName = activeSpeaker?.name ?: S.recording.currentSpeakerFallback,
            toneSymbols = quickToneSymbols,
            ipaInitials = quickIpaInitials,
            ipaFinals = quickIpaFinals,
            fieldTags = quickFieldTags,
            isSimpleMode = isSimpleMode,
            onCustomizeItems = if (isSimpleMode) null else ({ showInlineQuickNoteSettings = true }),
            onDeriveVariant = if (isSimpleMode) null else { suffix, currentNote ->
                viewModel.deriveVariantWord(
                    baseWord = targetWord,
                    variantSuffix = suffix,
                    speakerNote = currentNote
                ) { derived ->
                    Toast.makeText(
                        context,
                        S.recording.deriveVariantCreatedToast.withArgs("word" to (derived?.text ?: targetWord.text)),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                wordForQuickRemark = null
            },
            onDismiss = { wordForQuickRemark = null },
            onSave = { updatedSpeakerNote, updatedRemark ->
                viewModel.updateWordNoteAndRemark(targetWord, updatedSpeakerNote, updatedRemark)
                wordForQuickRemark = null
                Toast.makeText(context, S.recording.savedNoteToast, Toast.LENGTH_SHORT).show()
            }
        )
    }

    com.example.ui.components.AnimatedDialogVisibility(visible = showInlineQuickNoteSettings) {
        QuickNoteCustomizationDialog(
            toneSymbols = quickToneSymbols,
            ipaInitials = quickIpaInitials,
            ipaFinals = quickIpaFinals,
            fieldTags = quickFieldTags,
            onAddToneSymbol = { viewModel.addQuickToneSymbol(it) },
            onRemoveToneSymbol = { viewModel.removeQuickToneSymbol(it) },
            onAddIpaInitial = { viewModel.addQuickIpaInitial(it) },
            onRemoveIpaInitial = { viewModel.removeQuickIpaInitial(it) },
            onAddIpaFinal = { viewModel.addQuickIpaFinal(it) },
            onRemoveIpaFinal = { viewModel.removeQuickIpaFinal(it) },
            onAddFieldTag = { viewModel.addQuickFieldTag(it) },
            onRemoveFieldTag = { viewModel.removeQuickFieldTag(it) },
            onResetDefaults = {
                viewModel.resetAllQuickNotesToDefault()
                Toast.makeText(context, S.recording.restoredDefaultQuickTagsToast, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showInlineQuickNoteSettings = false }
        )
    }

    com.example.ui.components.AnimatedNullableDialogVisibility(value = wordForTakeSwitcher) { targetWord ->
        val pid = activeProject?.id
        val sid = activeSpeaker?.id
        if (pid != null && sid != null) {
            val takes by produceState<List<File>>(
                initialValue = emptyList(),
                targetWord.id,
                targetWord.recordingFile,
                takesRefreshTrigger
            ) {
                value = withContext(Dispatchers.IO) {
                    viewModel.getAvailableTakesForWord(pid, targetWord.id, sid)
                }
            }
            MultiTakeSwitcherDialog(
                word = targetWord,
                takes = takes,
                playingWordId = playingWordId,
                onPlayTake = { file, index ->
                    viewModel.playSpecificAudioFile(file, -(targetWord.id * 100 + index + 1))
                },
                onSelectTake = { file ->
                    viewModel.selectTakeForWord(targetWord, file)
                    wordForTakeSwitcher = null
                    Toast.makeText(context, S.recording.switchedActiveTakeToast, Toast.LENGTH_SHORT).show()
                },
                onDeriveTakeAsVariant = if (isSimpleMode) null else { file ->
                    viewModel.stopPlayback()
                    viewModel.deriveVariantWord(
                        baseWord = targetWord,
                        variantSuffix = "异读",
                        specificTakeFile = file
                    ) { derived ->
                        Toast.makeText(
                            context,
                            S.recording.deriveVariantCreatedToast.withArgs("word" to (derived?.text ?: targetWord.text)),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    wordForTakeSwitcher = null
                },
                onDismiss = {
                    viewModel.stopPlayback()
                    wordForTakeSwitcher = null
                }
            )
        }
    }
}

@Composable
private fun HandheldSessionToolbar(
    activeSpeaker: Speaker?,
    recordedCount: Int,
    totalCount: Int,
    unrecordedCount: Int,
    flaggedCount: Int,
    isShuffleEnabled: Boolean,
    onlyPendingWords: Boolean = false,
    stackRows: Boolean = false,
    onTriggerSpeakerDialog: () -> Unit,
    onShuffle: () -> Unit,
    onToggleOnlyPending: () -> Unit = {},
    onJumpNextUnrecorded: () -> Unit,
    onJumpNextFlagged: () -> Unit,
    onRequestClearAll: () -> Unit,
    onOpenArchive: () -> Unit = {}
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val shouldStack = stackRows || maxWidth < 322.dp
        val showSpeakerDemographics = shouldStack || maxWidth >= 380.dp || !(unrecordedCount > 0 && flaggedCount > 0)

        val speakerPill: @Composable (Modifier) -> Unit = { pillMod ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = pillMod
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTriggerSpeakerDialog() }
                    .testTag("speaker_card_trigger")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(4.dp))
                    val speakerLabel = if (activeSpeaker != null) {
                        if (showSpeakerDemographics) {
                            val genderPart = if (activeSpeaker.gender.isBlank() || activeSpeaker.gender == "不愿透露") {
                                S.common.ageWithUnit.withArgs("age" to activeSpeaker.age)
                            } else {
                                "${S.speaker.displayGender(activeSpeaker.gender)}·${S.common.ageWithUnit.withArgs("age" to activeSpeaker.age)}"
                            }
                            S.recording.speakerChipLabelFormat.withArgs(
                                "name" to activeSpeaker.name,
                                "meta" to genderPart
                            )
                        } else {
                            activeSpeaker.name
                        }
                    } else {
                        S.recording.selectSpeakerPrompt
                    }
                    Text(
                        text = speakerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val quickActionButtons: @Composable (Modifier) -> Unit = { rowMod ->
            Row(
                modifier = rowMod,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (unrecordedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onJumpNextUnrecorded)
                            .testTag("jump_unrecorded_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.FastForward,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = S.recording.unrecordedFilterChip.withArgs("count" to unrecordedCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else if (totalCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenArchive)
                            .testTag("all_recorded_export_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = S.recording.allRecordedExportChip,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if (flaggedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onJumpNextFlagged)
                            .testTag("jump_flagged_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = S.recording.flaggedFilterChip.withArgs("count" to flaggedCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                if ((unrecordedCount + flaggedCount > 0 && recordedCount > 0) || onlyPendingWords) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (onlyPendingWords) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleOnlyPending)
                            .testTag("only_pending_filter_button")
                    ) {
                        Text(
                            text = S.recording.onlyPendingFilterChip,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (onlyPendingWords) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isShuffleEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onShuffle)
                        .testTag("shuffle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = S.recording.toggleShuffleDesc,
                            tint = if (isShuffleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isShuffleEnabled) S.recording.shuffledLabel else S.recording.shuffleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isShuffleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        val rawProgress = if (totalCount > 0) (recordedCount.toFloat() / totalCount).coerceIn(0f, 1f) else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = rawProgress,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "sessionProgress"
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (shouldStack) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    speakerPill(Modifier.weight(1f, fill = false).padding(end = 8.dp))
                    Text(
                        text = "$recordedCount / $totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                quickActionButtons(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    speakerPill(Modifier.weight(1f, fill = false).padding(end = 6.dp))
                    quickActionButtons(Modifier)
                }

                // Subtle session progress bar with visible track
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
}

@Composable
private fun WordStripChip(
    index: Int,
    word: Word,
    isSelected: Boolean,
    enablePicturePrompts: Boolean = false,
    @Suppress("UNUSED_PARAMETER") wavFile: File?,
    onClick: () -> Unit
) {
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val isRecorded = word.isRecorded
    val isFlagged = isRecorded && word.qualityStatus != null && word.qualityStatus != "正常"
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isFlagged -> lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.errorContainer,
            if (isHovered) 0.90f else 0.72f
        )
        isRecorded -> lerp(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.secondaryContainer,
            if (isHovered) 0.90f else 0.72f
        )
        isHovered -> lerp(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.primaryContainer,
            0.45f
        )
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isHovered -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isFlagged -> MaterialTheme.colorScheme.onErrorContainer
        isRecorded -> MaterialTheme.colorScheme.onSecondaryContainer
        isHovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = if (isSelected || isHovered) {
            BorderStroke(if (isSelected) 1.5.dp else 1.2.dp, borderColor)
        } else {
            null
        },
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .hoverable(hoverSrc)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = hoverSrc, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%02d", index + 1),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    contentColor.copy(alpha = 0.6f)
                },
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (enablePicturePrompts && word.imagePath.isNotBlank()) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = S.recording.hasImageBadgeDesc,
                    modifier = Modifier.size(13.dp),
                    tint = contentColor.copy(alpha = 0.78f)
                )
            }
            Text(
                text = word.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor,
                modifier = Modifier.widthIn(max = 96.dp)
            )
            if (isRecorded) {
                Icon(
                    imageVector = if (isFlagged) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isFlagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PromptStageLightboxCard(
    word: Word,
    index: Int,
    total: Int,
    activeProjectId: Long = 0L,
    enablePicturePrompts: Boolean = false,
    characterSize: String,
    participantCleanMode: Boolean,
    hideRemarkInCleanMode: Boolean = false,
    hideTextInCleanMode: Boolean = false,
    carrierSentenceEnabled: Boolean = false,
    carrierSentenceTemplate: String = S.recording.defaultCarrierTemplate,
    isCompactStageHeight: Boolean = false,
    isPlayingAudio: Boolean,
    takeCount: Int = 0,
    showInspectAcoustics: Boolean = true,
    onImageZoomActiveChanged: ((Boolean) -> Unit)? = null,
    onPlayClick: () -> Unit,
    onToggleQualityFlag: () -> Unit = {},
    onEditRemarkClick: () -> Unit = {},
    onOpenTakeSwitcher: () -> Unit = {},
    onShareClick: () -> Unit,
    onInspectAcoustics: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val isRecorded = word.isRecorded
    val quality = word.qualityStatus
    val isFlagged = isRecorded && quality != null && quality != "正常"
    val wordImgFile = remember(enablePicturePrompts, activeProjectId, word.imagePath) {
        if (enablePicturePrompts && word.imagePath.isNotBlank()) {
            WordImageManager.resolveWordImageFile(context, activeProjectId, word.imagePath)
        } else {
            null
        }
    }
    val wordBitmap by rememberWordImageBitmap(wordImgFile, maxEdgePx = 1280)
    val hasImage = wordBitmap != null
    // Safety fallback: never hide text on unpictured words in a mixed wordlist
    val effectiveHideWordText = hasImage && participantCleanMode && hideTextInCleanMode

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isFlagged -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                isRecorded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (participantCleanMode) 20.dp else 14.dp)
        ) {
            val isCompactCard = (isCompactStageHeight || maxHeight < 250.dp) && isRecorded && !participantCleanMode
            val isNarrowCardActions = maxWidth < 340.dp
            val configuredFontSize = when (characterSize) {
                "小" -> 58f
                "中" -> if (isCompactCard) 72f else 84f
                "大" -> if (isCompactCard) 84f else 108f
                "超大" -> if (isCompactCard) 92f else 124f
                else -> if (isCompactCard) 72f else 84f
            }
            val baseFontSize = if (participantCleanMode) max(configuredFontSize, 114f) else configuredFontSize
            val charScale = when {
                word.text.length <= 1 -> 1.0f
                word.text.length == 2 -> 0.85f
                word.text.length == 3 -> 0.70f
                word.text.length == 4 -> 0.58f
                word.text.length <= 8 -> 0.46f
                word.text.length <= 16 -> 0.34f
                word.text.length <= 30 -> 0.26f
                else -> 0.22f
            }
            val hasVisibleRemarkOrQuickNote =
                ((word.remark.isNotBlank() || word.speakerNote.isNotBlank()) && !(participantCleanMode && hideRemarkInCleanMode)) || !participantCleanMode
            val topRowReserveDp = 32f
            val bottomRowReserveDp = if (isRecorded && !participantCleanMode) 46f else 6f
            val remarkReserveDp = if (word.remark.isNotBlank() && word.speakerNote.isNotBlank() && !participantCleanMode) 56f else if (hasVisibleRemarkOrQuickNote) 38f else 6f
            val carrierReserveDp = if (carrierSentenceEnabled && !effectiveHideWordText) 26f else 0f
            val rawCenterHeightDp =
                (maxHeight.value - topRowReserveDp - bottomRowReserveDp - remarkReserveDp - carrierReserveDp - 10f).coerceAtLeast(32f)
            val availableTextHeightDp = if (hasImage && !effectiveHideWordText) {
                (rawCenterHeightDp * 0.36f).coerceAtLeast(28f)
            } else {
                rawCenterHeightDp
            }
            val estimatedLines = when {
                word.text.length <= 4 -> 1f
                word.text.length <= 10 -> 2f
                else -> 3f
            }
            val heightCappedFontSize = (availableTextHeightDp / (1.18f * estimatedLines)).coerceAtLeast(22f)
            val minFontSize = if (word.text.length > 8) 18f else if (hasImage) 22f else 28f
            val maxAllowedFontSize = if (hasImage) {
                if (participantCleanMode) 64f else 48f
            } else {
                132f
            }
            val effectiveFontSize = min(baseFontSize * charScale, heightCappedFontSize).coerceIn(minFontSize, maxAllowedFontSize).sp

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top metadata badges (Sequence number + Category group + Status badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = String.format(Locale.US, "#%03d / %03d", index + 1, total),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (word.groupName.isNotBlank() && !(participantCleanMode && hideRemarkInCleanMode)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = word.groupName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    QualityStatusBadge(
                        isRecorded = isRecorded,
                        quality = quality,
                        simpleMode = !showInspectAcoustics,
                        onClick = if (isRecorded && !participantCleanMode) onToggleQualityFlag else null
                    )
                }

                // Center Target Image ("上图") & Lexeme ("下字") Stage
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .testTag("active_word_text"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val bmp = wordBitmap
                    if (bmp != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = if (effectiveHideWordText) 0.dp else 8.dp)
                                .testTag("active_word_image_box")
                        ) {
                            ZoomableWordImage(
                                bitmap = bmp,
                                contentDescription = word.text,
                                resetKey = "${word.id}_${word.imagePath}",
                                cornerRadius = 12.dp,
                                onZoomActiveChanged = onImageZoomActiveChanged,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }
                    }

                    if (!effectiveHideWordText) {
                        if (carrierSentenceEnabled && carrierSentenceTemplate.isNotBlank()) {
                            val quotedWord = S.recording.carrierWordQuoteFormat.withArgs("word" to word.text)
                            val renderedCarrier = if (carrierSentenceTemplate.contains("___")) {
                                carrierSentenceTemplate.replace("___", quotedWord)
                            } else {
                                S.recording.carrierSentenceWithWordFormat.withArgs(
                                    "template" to carrierSentenceTemplate,
                                    "quotedWord" to quotedWord
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = if (isCompactCard || hasImage) 2.dp else 6.dp)
                            ) {
                                Text(
                                    text = renderedCarrier,
                                    style = if (participantCleanMode && !hasImage) {
                                        MaterialTheme.typography.titleMedium
                                    } else {
                                        MaterialTheme.typography.labelLarge
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = word.text,
                            fontSize = effectiveFontSize,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            lineHeight = effectiveFontSize * 1.08f,
                            maxLines = if (hasImage) 2 else 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("current_lexeme_word_display")
                        )
                    }

                    val showRemarkPill = word.remark.isNotBlank() && !(participantCleanMode && hideRemarkInCleanMode)
                    val showSpeakerNotePill = word.speakerNote.isNotBlank() && !participantCleanMode

                    if (showRemarkPill || showSpeakerNotePill) {
                        Spacer(Modifier.height(if (isCompactCard) 4.dp else 8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (showRemarkPill) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .then(
                                            if (!participantCleanMode) {
                                                Modifier.clickable(onClick = onEditRemarkClick)
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .testTag("active_word_remark")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = word.remark,
                                            style = if (participantCleanMode) {
                                                MaterialTheme.typography.headlineSmall
                                            } else if (isCompactCard) {
                                                MaterialTheme.typography.bodyMedium
                                            } else {
                                                MaterialTheme.typography.titleMedium
                                            },
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = if (participantCleanMode) 3 else 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("current_lexeme_pinyin_display")
                                        )
                                        if (!participantCleanMode && !showSpeakerNotePill) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = S.recording.editNoteDesc,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }

                            if (showSpeakerNotePill) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onEditRemarkClick)
                                        .then(
                                            if (!showRemarkPill) Modifier.testTag("active_word_remark")
                                            else Modifier.testTag("active_speaker_note")
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = S.recording.notePrefix.withArgs("note" to word.speakerNote),
                                            style = if (isCompactCard) {
                                                MaterialTheme.typography.bodySmall
                                            } else {
                                                MaterialTheme.typography.bodyMedium
                                            },
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = if (!showRemarkPill) Modifier.testTag("current_lexeme_pinyin_display") else Modifier
                                        )
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = S.recording.editShortNoteDesc,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            } else if (!participantCleanMode) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(onClick = onEditRemarkClick)
                                        .testTag("quick_add_remark_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.EditNote,
                                            contentDescription = S.recording.quickNoteShort,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = S.recording.quickNoteShort,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!participantCleanMode) {
                        Spacer(Modifier.height(if (isCompactCard) 4.dp else 8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onEditRemarkClick)
                                .testTag("quick_add_remark_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.EditNote,
                                    contentDescription = S.recording.quickNoteWithDesc,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = S.recording.quickNoteShort,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Bottom Quick Actions inside Stage Card when Recorded (never squishes circular buttons on 360dp!)
                if (isRecorded && !participantCleanMode) {
                    val iconBtnSize = if (isNarrowCardActions) 40.dp else 44.dp
                    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onPlayClick,
                            contentPadding = PaddingValues(
                                horizontal = if (isNarrowCardActions) 12.dp else 14.dp,
                                vertical = 8.dp
                            ),
                            modifier = Modifier
                                .height(iconBtnSize)
                                .testTag("play_word_audio_button")
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingAudio) S.recording.stopPlaybackDesc else S.recording.playCurrentWordDesc,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(if (isNarrowCardActions) 4.dp else 6.dp))
                            Text(
                                text = when {
                                    isPlayingAudio && isNarrowCardActions -> S.common.stop
                                    isPlayingAudio -> S.recording.stopListening
                                    isNarrowCardActions -> S.common.playback
                                    else -> S.recording.startListening
                                },
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(if (isNarrowCardActions) 5.dp else 6.dp)) {
                            FilledTonalIconButton(
                                onClick = onToggleQualityFlag,
                                modifier = Modifier
                                    .size(iconBtnSize)
                                    .testTag("toggle_manual_flag_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isFlagged) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = if (isFlagged) Icons.Default.CheckCircle else Icons.Default.Flag,
                                    contentDescription = if (isFlagged) S.archive.confirmAsNormalDesc else S.archive.markAsRerecordDesc,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isFlagged) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (showInspectAcoustics) {
                                FilledTonalIconButton(
                                    onClick = onInspectAcoustics,
                                    modifier = Modifier
                                        .size(iconBtnSize)
                                        .testTag("inspect_acoustics_button"),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = S.recording.viewAcousticDetailDesc,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            FilledTonalIconButton(
                                onClick = onShareClick,
                                modifier = Modifier
                                    .size(iconBtnSize)
                                    .testTag("share_recording_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Icon(
                                    imageVector = if (isDesktop) Icons.Default.FolderOpen else Icons.Default.Share,
                                    contentDescription = if (isDesktop) S.recording.openAudioFolderDesc else S.recording.shareAudioDesc,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            FilledTonalIconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier
                                    .size(iconBtnSize)
                                    .testTag("delete_this_recording_button"),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = S.recording.deleteAndRerecordDesc,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun QualityStatusBadge(
    isRecorded: Boolean,
    quality: String?,
    simpleMode: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    if (!isRecorded) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Text(
                text = S.recording.pendingRecordBadge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        return
    }

    val isNormal = quality == null || quality == "正常"
    val label = S.quality.displayStatus(quality, simpleMode = simpleMode)
    val (containerColor, contentColor) = when {
        isNormal -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        quality == "人工标重录" || quality == "需重录" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        quality == "削波/过载" || quality == "过载/破音" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        quality == "音量过低" || quality == "声音偏小" ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        quality == "嘎裂声" ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        quality == "开头截断" || quality == "收尾截断" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        quality == "底噪偏高" ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("quality_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isNormal) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

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
private fun QuickRemarkEditorDialog(
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
                                val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
                                StudioFilterChip(
                                    selected = false,
                                    onClick = {
                                        speakerNoteField = insertSymbolAtFieldCursor(speakerNoteField, sym)
                                    },
                                    labelText = displaySym
                                )
                            }
                            1 -> ipaInitials.forEach { sym ->
                                StudioFilterChip(
                                    selected = false,
                                    onClick = {
                                        speakerNoteField = insertSymbolAtFieldCursor(speakerNoteField, sym)
                                    },
                                    labelText = sym
                                )
                            }
                            2 -> ipaFinals.forEach { sym ->
                                val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
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
private fun MultiTakeSwitcherDialog(
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

/**
 * Isolated leaf composable for 43Hz real-time acoustic telemetry.
 * Keeping StateFlow collection inside this leaf prevents full-screen recomposition during recording.
 */
@Composable
private fun LiveStudioMonitorSection(
    viewModel: RecordingViewModel,
    currentWord: Word,
    activeProject: Project?,
    participantCleanMode: Boolean,
    isCompact: Boolean = false,
    takeCount: Int = 0,
    onOpenTakeSwitcher: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val liveDbfs by viewModel.liveDbfs.collectAsState()
    val liveElapsedMs by viewModel.liveElapsedMs.collectAsState()
    val audioSourceLabel by viewModel.activeSourceLabel.collectAsState()
    val waveformPoints by viewModel.recordingWaveform.collectAsState()
    val silenceProgress by viewModel.silenceProgress.collectAsState()
    val isClipping by viewModel.isClipping.collectAsState()
    val isTooQuiet by viewModel.isTooQuiet.collectAsState()
    val isAutoNext by viewModel.autoNextEnabled.collectAsState()
    val isPushToTalk by viewModel.pushToTalkEnabled.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()

    // Load saved WAV envelope when not actively recording
    val recordedEnvelope by produceState<List<Float>>(
        initialValue = emptyList(),
        isRecording,
        currentWord.isRecorded,
        currentWord.recordingFile,
        activeProject?.id
    ) {
        if (!isRecording && currentWord.isRecorded && activeProject != null && !currentWord.recordingFile.isNullOrBlank()) {
            val pid = activeProject.id
            val fileName = currentWord.recordingFile
            value = withContext(Dispatchers.IO) {
                val file = File(File(context.getExternalFilesDir(null), "project_$pid"), fileName)
                if (file.exists()) {
                    AcousticAnalyzer.getRealWaveformEnvelope(file, 48)
                } else {
                    emptyList()
                }
            }
        } else {
            value = emptyList()
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (isCompact) 7.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 8.dp)
        ) {
            // Top Status Row: Human-friendly recording status + multi-take badge + clear volume status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isRecording -> MaterialTheme.colorScheme.error
                                    currentWord.isRecorded -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Text(
                        text = if (isRecording) {
                            S.recording.recordingTimerLabel.withArgs(
                                "time" to String.format(
                                    Locale.US,
                                    "%02d:%04.1f",
                                    (liveElapsedMs / 1000) / 60,
                                    (liveElapsedMs % 60000) / 1000f
                                )
                            )
                        } else if (currentWord.isRecorded) {
                            val secStr = String.format(Locale.US, "%.1f", currentWord.durationMs / 1000f)
                            if (DesktopAdaptiveState.isDesktopPlatform) {
                                S.recording.desktopRecordedHint.withArgs("sec" to secStr)
                            } else {
                                S.recording.mobileRecordedHint.withArgs("sec" to secStr)
                            }
                        } else if (isPushToTalk) {
                            S.recording.holdToRecordHint
                        } else if (DesktopAdaptiveState.isDesktopPlatform) {
                            S.recording.desktopReadyHint
                        } else {
                            S.recording.mobileReadyHint
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!participantCleanMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isRecording && takeCount >= 2) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenTakeSwitcher)
                                    .testTag("multi_take_switcher_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = S.recording.switchTakeDesc,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = S.recording.takeCountBadge.withArgs("count" to takeCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        val levelHint = when {
                            !isRecording && currentWord.isRecorded -> S.recording.levelRerecordable
                            !isRecording -> ""
                            liveDbfs > -1.5f -> S.recording.levelClipping
                            liveDbfs > -24.0f -> S.recording.levelOptimal
                            liveDbfs > -42.0f -> S.recording.levelTooQuiet
                            else -> S.recording.levelWaiting
                        }
                        if (levelHint.isNotEmpty()) {
                            Text(
                                text = levelHint,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = when {
                                    isRecording && liveDbfs > -1.5f -> MaterialTheme.colorScheme.error
                                    isRecording && liveDbfs > -24.0f -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // Real-time Volume Bar
            DbfsVuMeterBar(
                dbfs = if (isRecording) liveDbfs else -60f,
                isRecording = isRecording
            )

            // Symmetrical Zero-Baseline Waveform
            SymmetricalAudioWaveform(
                amplitudes = if (isRecording) waveformPoints else recordedEnvelope,
                isRecording = isRecording,
                isClipping = isClipping,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        when {
                            isCompact -> 36.dp
                            participantCleanMode -> 44.dp
                            else -> 54.dp
                        }
                    )
            )

            // Friendly Guidance Banner or Auto-Flip Progress
            if (isRecording) {
                val statusMessage = when {
                    isClipping -> S.recording.promptClippingHint
                    isTooQuiet -> S.recording.promptTooQuietHint
                    else -> S.recording.promptNormalSpeakHint
                }
                val statusColor = when {
                    isClipping -> MaterialTheme.colorScheme.error
                    isTooQuiet -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isPushToTalk && isAutoNext && silenceProgress > 0.02f) {
                        Spacer(Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { silenceProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .width(64.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DbfsVuMeterBar(
    dbfs: Float,
    isRecording: Boolean
) {
    val normalizedLevel = if (isRecording) ((dbfs + 60f) / 60f).coerceIn(0f, 1f) else 0f
    val safeGreenColor = Color(0xFF2E7D32)
    val cautionAmberColor = Color(0xFFF9A825)
    val clipRedColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(4f, 4f)
        )

        if (normalizedLevel > 0.01f) {
            val activeWidth = size.width * normalizedLevel
            val barColor = when {
                dbfs >= -1.5f -> clipRedColor
                dbfs >= -6.0f -> cautionAmberColor
                dbfs >= -24.0f -> safeGreenColor
                else -> Color(0xFF1976D2)
            }
            drawRoundRect(
                color = barColor,
                size = Size(activeWidth, size.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        val tick24X = size.width * ((60f - 24f) / 60f)
        val tick6X = size.width * ((60f - 6f) / 60f)
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(tick24X, 0f),
            end = Offset(tick24X, size.height),
            strokeWidth = 1.5f
        )
        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = Offset(tick6X, 0f),
            end = Offset(tick6X, size.height),
            strokeWidth = 1.5f
        )
    }
}

@Composable
fun SymmetricalAudioWaveform(
    amplitudes: List<Float>,
    isRecording: Boolean,
    isClipping: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeColor = when {
        isClipping -> MaterialTheme.colorScheme.error
        isRecording -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val midY = size.height / 2f
        drawLine(
            color = baselineColor.copy(alpha = 0.5f),
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.5f
        )

        if (amplitudes.isEmpty()) return@Canvas

        val barCount = 48
        val slotWidth = size.width / barCount
        val strokeW = (slotWidth * 0.58f).coerceAtLeast(2f)
        val displayed = if (amplitudes.size > barCount) {
            amplitudes.takeLast(barCount)
        } else {
            amplitudes
        }

        displayed.forEachIndexed { idx, rawAmp ->
            val normalized = rawAmp.coerceIn(0.03f, 1f)
            val halfHeight = (normalized * (size.height * 0.46f)).coerceAtLeast(1.5f)
            val x = idx * slotWidth + slotWidth / 2f
            drawLine(
                color = activeColor,
                start = Offset(x, midY - halfHeight),
                end = Offset(x, midY + halfHeight),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun RecordingPulseRing(buttonSize: androidx.compose.ui.unit.Dp = 76.dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "recordPulse")
    val pulseScaleState = infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer {
                val s = pulseScaleState.value
                scaleX = s
                scaleY = s
                alpha = pulseAlphaState.value
            }
            .background(MaterialTheme.colorScheme.error, CircleShape)
    )
}

@Composable
private fun RecordingTransportBar(
    isRecording: Boolean,
    isPushToTalk: Boolean,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    isCompact: Boolean = false,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleRecord: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onStopFromHoldRelease: () -> Unit = onStopRecord
) {
    val navBtnSize = if (isCompact) 48.dp else 56.dp
    val micBtnSize = if (isCompact) 62.dp else 76.dp
    val outerBoxSize = if (isCompact) 74.dp else 92.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 0.dp else 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onPrev,
            enabled = canGoPrev,
            modifier = Modifier
                .size(navBtnSize)
                .pointerHoverIcon(if (canGoPrev) PointerIcon.Hand else PointerIcon.Default)
                .testTag("prev_word_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = S.recording.prevWordDesc,
                modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(outerBoxSize)
        ) {
            if (isRecording) {
                RecordingPulseRing(buttonSize = micBtnSize)
            }

            var isButtonPressed by remember { mutableStateOf(false) }

            val buttonColor by animateColorAsState(
                targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "recBtnColor"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "recIconColor"
            )
            val buttonScale by animateFloatAsState(
                targetValue = if (isButtonPressed) 0.91f else 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 680f),
                label = "recBtnScale"
            )
            val cornerPercent by animateIntAsState(
                targetValue = if (isRecording) 34 else 50,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                label = "recBtnCorner"
            )

            val currentIsRecording by rememberUpdatedState(isRecording)
            val currentIsPushToTalk by rememberUpdatedState(isPushToTalk)
            val currentOnToggleRecord by rememberUpdatedState(onToggleRecord)
            val currentOnStartRecord by rememberUpdatedState(onStartRecord)
            val currentOnStopRecord by rememberUpdatedState(onStopRecord)
            val currentOnStopFromHoldRelease by rememberUpdatedState(onStopFromHoldRelease)

            Surface(
                shape = RoundedCornerShape(percent = cornerPercent),
                color = buttonColor,
                shadowElevation = if (isRecording) 8.dp else 4.dp,
                modifier = Modifier
                    .size(micBtnSize)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("main_record_button")
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val downAtMs = System.currentTimeMillis()
                            val wasAlreadyRecording = currentIsRecording
                            isButtonPressed = true
                            try {
                                if (wasAlreadyRecording) {
                                    if (currentIsPushToTalk) {
                                        currentOnStopRecord()
                                    } else {
                                        currentOnToggleRecord()
                                    }
                                    waitForUpOrCancellation()
                                } else {
                                    currentOnStartRecord()
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        val heldMs = System.currentTimeMillis() - downAtMs
                                        if (currentIsPushToTalk || heldMs >= 620L) {
                                            if (currentIsRecording) {
                                                currentOnStopFromHoldRelease()
                                            }
                                        }
                                    }
                                }
                            } finally {
                                isButtonPressed = false
                            }
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isRecording,
                        transitionSpec = {
                            (scaleIn(spring(dampingRatio = 0.68f, stiffness = 550f), initialScale = 0.65f) + fadeIn(tween(120))) togetherWith
                                (scaleOut(tween(100), targetScale = 0.65f) + fadeOut(tween(90)))
                        },
                        label = "RecMicStopIconMorph"
                    ) { rec ->
                        Icon(
                            imageVector = if (rec) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (rec) S.recording.stopRecordingDesc else S.recording.startRecordingDesc,
                            tint = iconColor,
                            modifier = Modifier.size(if (isCompact) 28.dp else 34.dp)
                        )
                    }
                }
            }
        }

        FilledTonalIconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .size(navBtnSize)
                .pointerHoverIcon(if (canGoNext) PointerIcon.Hand else PointerIcon.Default)
                .testTag("next_word_button")
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = S.recording.nextWordDesc,
                modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
            )
        }
    }
}

@Composable
private fun TabletWordSidebar(
    words: List<Word>,
    currentIndex: Int,
    activeSpeaker: Speaker?,
    recordedCount: Int,
    unrecordedCount: Int,
    flaggedCount: Int,
    isShuffleEnabled: Boolean,
    onlyPendingWords: Boolean = false,
    enablePicturePrompts: Boolean = false,
    onSelectWord: (Int) -> Unit,
    onTriggerSpeakerDialog: () -> Unit,
    onShuffle: () -> Unit,
    onToggleOnlyPending: () -> Unit = {},
    onJumpNextUnrecorded: () -> Unit,
    onJumpNextFlagged: () -> Unit,
    onRequestClearAll: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(256.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTriggerSpeakerDialog() }
                    .testTag("speaker_card_trigger_tablet")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeSpeaker?.name ?: S.common.defaultSpeakerFallback,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = if (activeSpeaker != null) {
                                val genderPrefix = if (activeSpeaker.gender.isBlank() || activeSpeaker.gender == "不愿透露") {
                                    ""
                                } else {
                                    S.recording.speakerGenderAgeDot.withArgs(
                                        "gender" to S.speaker.displayGender(activeSpeaker.gender)
                                    )
                                }
                                S.recording.speakerGenderAgeWithDialect.withArgs(
                                    "genderPrefix" to genderPrefix,
                                    "age" to activeSpeaker.age,
                                    "dialect" to activeSpeaker.dialect
                                )
                            } else {
                                S.recording.tapToSwitchSpeaker
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(Icons.Default.Edit, contentDescription = S.recording.switchSpeakerDesc, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if ((unrecordedCount + flaggedCount > 0 && recordedCount > 0) || onlyPendingWords) {
                        StudioFilterChip(
                            selected = onlyPendingWords,
                            onClick = onToggleOnlyPending,
                            labelText = S.recording.onlyPendingFilterChip
                        )
                    }
                    StudioFilterChip(
                        selected = false,
                        onClick = onJumpNextUnrecorded,
                        enabled = unrecordedCount > 0,
                        labelText = S.recording.unrecordedFilterChip.withArgs("count" to unrecordedCount)
                    )
                    if (flaggedCount > 0) {
                        StudioFilterChip(
                            selected = false,
                            onClick = onJumpNextFlagged,
                            labelText = S.recording.flaggedFilterChip.withArgs("count" to flaggedCount),
                            isErrorVariant = true
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onShuffle, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = S.recording.shuffleWordListDesc,
                            tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (recordedCount > 0) {
                        IconButton(onClick = onRequestClearAll, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = S.recording.clearSpeakerAudioDesc,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(words, key = { _, w -> w.id }) { idx, word ->
                    val isSelected = idx == currentIndex
                    val isRec = word.isRecorded
                    val isFlagged = isRec && word.qualityStatus != null && word.qualityStatus != "正常"
                    val rowBg = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isFlagged -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        isRec -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = rowBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectWord(idx) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d.", idx + 1),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (enablePicturePrompts && word.imagePath.isNotBlank()) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = S.recording.hasImageBadgeDesc,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = word.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    val sideSub = listOf(word.speakerNote, word.remark)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (sideSub.isNotBlank()) {
                                        Text(
                                            text = sideSub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (word.speakerNote.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (isRec) {
                                Icon(
                                    imageVector = if (isFlagged) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isFlagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingQuickSettingsSidebarPane(
    viewModel: RecordingViewModel,
    onOpenFullSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAutoNext by viewModel.autoNextEnabled.collectAsState()
    val isPushToTalk by viewModel.pushToTalkEnabled.collectAsState()
    val autoAdvanceOnHoldRelease by viewModel.autoAdvanceOnHoldRelease.collectAsState()
    val autoPlayAfterRecord by viewModel.autoPlayAfterRecord.collectAsState()
    val volumeKeyControlEnabled by viewModel.volumeKeyControlEnabled.collectAsState()
    val vadThreshold by viewModel.vadThreshold.collectAsState()
    val silenceDelayMs by viewModel.silenceDelayMs.collectAsState()
    val characterSize by viewModel.characterSize.collectAsState()
    val hideRemarkInCleanMode by viewModel.hideRemarkInCleanMode.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val shufflingMode by viewModel.shufflingMode.collectAsState()
    val lowVolumeThreshold by viewModel.lowVolumeThreshold.collectAsState()
    val creakySensitivity by viewModel.creakySensitivity.collectAsState()
    val rawWords by viewModel.currentWords.collectAsState()
    val activeProject by viewModel.currentProject.collectAsState()
    val activeSpeaker by viewModel.currentSpeaker.collectAsState()
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val recordedCount = remember(rawWords) { rawWords.count { it.isRecorded } }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("vad_settings_sheet")
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
            onRequestClearAll = { showClearAllConfirmDialog = true },
            onOpenFullSettings = onOpenFullSettings,
            onClose = onClose,
            isSidebarMode = true,
            isSimpleMode = isSimpleMode
        )
    }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordingSettingsSheetContent(
    isAutoNext: Boolean,
    onAutoNextChange: (Boolean) -> Unit,
    isPushToTalk: Boolean,
    onPushToTalkChange: (Boolean) -> Unit,
    autoAdvanceOnHoldRelease: Boolean = true,
    onAutoAdvanceOnHoldReleaseChange: (Boolean) -> Unit = {},
    autoPlayAfterRecord: Boolean,
    onAutoPlayChange: (Boolean) -> Unit,
    volumeKeyControlEnabled: Boolean,
    onVolumeKeyChange: (Boolean) -> Unit,
    vadThreshold: Float,
    onVadThresholdChange: (Float) -> Unit,
    onApplyVadPreset: (String) -> Unit = {},
    silenceDelayMs: Long,
    onSilenceDelayChange: (Long) -> Unit,
    characterSize: String,
    onCharacterSizeChange: (String) -> Unit,
    hideRemarkInCleanMode: Boolean = true,
    onHideRemarkInCleanModeChange: (Boolean) -> Unit = {},
    isShuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    shufflingMode: ShufflingMode,
    onShufflingModeChange: (ShufflingMode) -> Unit,
    lowVolumeThreshold: Float,
    onLowVolumeThresholdChange: (Float) -> Unit,
    creakySensitivity: String,
    onCreakySensitivityChange: (String) -> Unit,
    recordedCount: Int = 0,
    onRequestClearAll: () -> Unit = {},
    onOpenFullSettings: () -> Unit = {},
    onClose: () -> Unit,
    isSidebarMode: Boolean = false,
    isSimpleMode: Boolean = false
) {
    val sheetScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = available
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isSidebarMode) Modifier.fillMaxHeight() else Modifier.navigationBarsPadding())
    ) {
        // Fixed Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isSidebarMode) 12.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSidebarMode) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.speaker.backToProjectsDesc,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = S.recording.quickSettingsTitle,
                    style = if (isSidebarMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onOpenFullSettings,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(S.recording.moreSettingsButton, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = onClose,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(S.common.done, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isSidebarMode) Modifier.weight(1f) else Modifier.nestedScroll(sheetScrollLock))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

        val currentHabitIndex = when {
            isPushToTalk -> 1
            isAutoNext -> 2
            else -> 0
        }

        if (isSimpleMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StudioFilterChip(
                            selected = currentHabitIndex == 0,
                            onClick = {
                                onAutoNextChange(false)
                                onPushToTalkChange(false)
                                onAutoAdvanceOnHoldReleaseChange(false)
                            },
                            labelText = "点按录"
                        )
                        StudioFilterChip(
                            selected = currentHabitIndex == 1,
                            onClick = {
                                onPushToTalkChange(true)
                                onAutoAdvanceOnHoldReleaseChange(true)
                            },
                            labelText = "按住松手跳词"
                        )
                        StudioFilterChip(
                            selected = currentHabitIndex == 2,
                            onClick = {
                                onAutoNextChange(true)
                                onAutoAdvanceOnHoldReleaseChange(false)
                            },
                            labelText = "读完自动跳词"
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.autoNextTitle,
                        subtitle = S.recording.autoNextSubtitle,
                        checked = isAutoNext,
                        onCheckedChange = onAutoNextChange,
                        switchTestTag = "auto_next_switch",
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.strictPushToTalkTitle,
                        subtitle = if (DesktopAdaptiveState.isDesktopPlatform) {
                            S.recording.strictPushToTalkDesktopSub
                        } else {
                            S.recording.strictPushToTalkMobileSub
                        },
                        checked = isPushToTalk,
                        onCheckedChange = onPushToTalkChange,
                        switchTestTag = "push_to_talk_switch",
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.holdReleaseNextTitle,
                        subtitle = S.recording.holdReleaseNextSubtitle,
                        checked = autoAdvanceOnHoldRelease,
                        onCheckedChange = onAutoAdvanceOnHoldReleaseChange,
                        switchTestTag = "hold_release_advance_switch",
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.autoPlayTitle,
                        subtitle = S.recording.autoPlaySubtitle,
                        checked = autoPlayAfterRecord,
                        onCheckedChange = onAutoPlayChange,
                        switchTestTag = "auto_play_after_record_switch",
                        compact = true
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(S.recording.fontSizeHeader, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fontOptions = listOf(
                            "小" to S.recording.sizeCompact,
                            "中" to S.recording.sizeStandard,
                            "大" to S.recording.sizeLarge,
                            "超大" to S.recording.sizeExtraLarge
                        )
                        fontOptions.forEach { (opt, label) ->
                            StudioFilterChip(
                                selected = characterSize == opt,
                                onClick = { onCharacterSizeChange(opt) },
                                labelText = label,
                                modifier = Modifier.testTag("font_size_selector_$opt")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.hideRemarksInCleanTitle,
                        subtitle = S.recording.hideRemarksInCleanSub,
                        checked = hideRemarkInCleanMode,
                        onCheckedChange = onHideRemarkInCleanModeChange,
                        compact = true
                    )
                }
            }
        } else {
            // 1. Ergonomic Workflow Switches
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StudioFilterChip(
                            selected = currentHabitIndex == 0,
                            onClick = {
                                onAutoNextChange(false)
                                onPushToTalkChange(false)
                                onAutoAdvanceOnHoldReleaseChange(false)
                            },
                            labelText = "点按录"
                        )
                        StudioFilterChip(
                            selected = currentHabitIndex == 1,
                            onClick = {
                                onPushToTalkChange(true)
                                onAutoAdvanceOnHoldReleaseChange(true)
                            },
                            labelText = "按住松手跳词"
                        )
                        StudioFilterChip(
                            selected = currentHabitIndex == 2,
                            onClick = {
                                onAutoNextChange(true)
                                onAutoAdvanceOnHoldReleaseChange(false)
                            },
                            labelText = "读完自动跳词"
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.desktopAutoNextTitle,
                        subtitle = S.recording.desktopAutoNextSubtitle,
                        checked = isAutoNext,
                        onCheckedChange = onAutoNextChange,
                        switchTestTag = "auto_next_switch",
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.strictPushToTalkTitle,
                        subtitle = if (DesktopAdaptiveState.isDesktopPlatform) {
                            S.recording.strictPushToTalkDesktopSub
                        } else {
                            S.recording.strictPushToTalkMobileSub
                        },
                        checked = isPushToTalk,
                        onCheckedChange = onPushToTalkChange,
                        switchTestTag = "push_to_talk_switch",
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.desktopHoldReleaseNextTitle,
                        subtitle = S.recording.desktopHoldReleaseNextSub,
                        checked = autoAdvanceOnHoldRelease,
                        onCheckedChange = onAutoAdvanceOnHoldReleaseChange,
                        switchTestTag = "hold_release_advance_switch",
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.desktopAutoPlayTitle,
                        subtitle = S.recording.desktopAutoPlaySub,
                        checked = autoPlayAfterRecord,
                        onCheckedChange = onAutoPlayChange,
                        switchTestTag = "auto_play_after_record_switch",
                        compact = true
                    )

                    if (!DesktopAdaptiveState.isDesktopPlatform) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        StudioSwitchRow(
                            title = S.recording.volumeKeyControlTitle,
                            subtitle = S.recording.volumeKeyControlSub,
                            checked = volumeKeyControlEnabled,
                            onCheckedChange = onVolumeKeyChange,
                            switchTestTag = "volume_key_control_switch",
                            compact = true
                        )
                    }
                }
            }

            // 2. Auto-flip Parameters
            AnimatedVisibility(
                visible = isAutoNext && !isPushToTalk,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val activePreset = when {
                            vadThreshold <= 850f -> "高灵敏"
                            vadThreshold >= 1650f -> "抗噪"
                            else -> "标准"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.recording.vadSensitivityValue.withArgs("value" to S.settings.displayVadPreset(activePreset)),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = S.recording.vadDirectionHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("高灵敏", "标准", "抗噪").forEach { preset ->
                                StudioFilterChip(
                                    selected = activePreset == preset,
                                    onClick = { onApplyVadPreset(preset) },
                                    labelText = S.settings.displayVadPreset(preset)
                                )
                            }
                        }
                        Slider(
                            value = vadThreshold,
                            onValueChange = onVadThresholdChange,
                            valueRange = 200f..3000f,
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("vad_threshold_slider")
                        )

                        Text(
                            text = S.recording.vadDelayValue.withArgs("ms" to silenceDelayMs),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = silenceDelayMs.toFloat(),
                            onValueChange = { onSilenceDelayChange(it.toLong()) },
                            valueRange = 400f..2500f,
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("silence_delay_slider")
                        )
                    }
                }
            }

            // 3. Prompt Font Size & Word List Shuffling Strategy
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(S.recording.cardSizeHeader, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fontOptions = listOf(
                            "小" to S.recording.sizeCompact,
                            "中" to S.recording.sizeStandard,
                            "大" to S.recording.sizeLarge,
                            "超大" to S.recording.sizeExtraLarge
                        )
                        fontOptions.forEach { (opt, label) ->
                            StudioFilterChip(
                                selected = characterSize == opt,
                                onClick = { onCharacterSizeChange(opt) },
                                labelText = label,
                                modifier = Modifier.testTag("font_size_selector_$opt")
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    StudioSwitchRow(
                        title = S.recording.hideRemarksInCleanTitle,
                        subtitle = S.recording.hideRemarksInCleanSub,
                        checked = hideRemarkInCleanMode,
                        onCheckedChange = onHideRemarkInCleanModeChange,
                        compact = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    StudioSwitchRow(
                        title = S.recording.shuffleOrderTitle,
                        checked = isShuffleEnabled,
                        onCheckedChange = { onToggleShuffle() },
                        compact = true
                    )
                    AnimatedVisibility(
                        visible = isShuffleEnabled,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val shuffleOptions = listOf(
                                ShufflingMode.RANDOM_ALL to S.recording.shuffleModeAll,
                                ShufflingMode.BLOCK_BALANCED to S.recording.shuffleModeGrouped
                            )
                            shuffleOptions.forEach { (mode, label) ->
                                StudioFilterChip(
                                    selected = shufflingMode == mode,
                                    onClick = { onShufflingModeChange(mode) },
                                    labelText = label,
                                    modifier = Modifier.testTag("shuffling_mode_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // 4. Quality Reminder Thresholds
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val lowVolLevelLabel = when {
                        lowVolumeThreshold < 400f -> "宽松"
                        lowVolumeThreshold > 950f -> "严格"
                        else -> "适中"
                    }
                    Text(S.recording.qualityReminderHeader, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = S.recording.lowVolThresholdValue.withArgs("value" to lowVolLevelLabel),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = lowVolumeThreshold,
                        onValueChange = onLowVolumeThresholdChange,
                        valueRange = 150f..2500f,
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .testTag("low_volume_threshold_slider")
                    )

                    Text(S.recording.creakySensitivityLabel, style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val creakyOptions = listOf("低", "中", "高", "关闭")
                        creakyOptions.forEach { option ->
                            StudioFilterChip(
                                selected = creakySensitivity == option,
                                onClick = { onCreakySensitivityChange(option) },
                                labelText = S.settings.displayCreakyOption(option),
                                modifier = Modifier.testTag("creaky_sensitivity_$option")
                            )
                        }
                    }
                }
            }

            if (recordedCount > 0) {
                OutlinedButton(
                    onClick = onRequestClearAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        S.recording.clearSpeakerRecordingsBtn.withArgs("count" to recordedCount),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(S.recording.doneSettingsButton, maxLines = 1, softWrap = false)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
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
    val context = androidx.compose.ui.platform.LocalContext.current
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

    com.example.ui.components.StudioModalDialog(onDismissRequest = onDismiss) {
        com.example.ui.components.StudioDialogEnterAnimation {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
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

