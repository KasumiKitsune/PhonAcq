package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.consumeDialogCardTaps
import com.example.link.ActiveMicOwner
import com.example.link.LinkRole
import com.example.link.RemoteProjectSummary
import com.example.link.RemoteSessionSnapshot
import com.example.link.RemoteSpeakerSummary
import com.example.link.RemoteWordItem
import com.example.ui.components.ActiveMicEdgeGlowOverlay
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioAdaptiveSheet
import com.example.ui.components.StudioDialogEnterAnimation
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioRecordTriggerButton
import com.example.ui.components.studioTactile
import com.example.utils.rememberWordImageBitmap
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Investigator-facing 遥控端 (Remote Controller) fieldwork console in Material You style.
 * Concentrates project & speaker switching, live dBFS level monitoring, headphone ear-monitoring,
 * single-word remote playback, intermission control ("中场休息"), and quick IPA / Chao tone shorthand.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemoteControllerLinkScreen(
    viewModel: RecordingViewModel,
    onExitSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wireless = viewModel.wirelessLink

    val snapshot by wireless.sessionSnapshot.collectAsState()
    val policy by wireless.promptPolicy.collectAsState()
    val isConnected by wireless.isConnected.collectAsState()
    val connectionEpoch by wireless.connectionEpoch.collectAsState()
    val isHostingWaiting by wireless.isHostingWaiting.collectAsState()
    val pairingPin by wireless.pairingPin.collectAsState()
    val peerName by wireless.peerDeviceName.collectAsState()
    val rttMs by wireless.rttMs.collectAsState()
    val activeMicOwner by wireless.activeMicOwner.collectAsState()
    val remoteDbfs by wireless.remoteDbfs.collectAsState()
    val remoteIsClipping by wireless.remoteIsClipping.collectAsState()
    val remoteWaveform by wireless.remoteWaveform.collectAsState()
    val isMonitoring by wireless.earMonitor.isMonitoringEnabled.collectAsState()
    val autoMutedOnDisconnectCount by wireless.earMonitor.autoMutedOnDisconnectCount.collectAsState()
    val isAuditionPlaying by wireless.isRemoteAuditionPlaying.collectAsState()
    val statusBanner by wireless.statusBanner.collectAsState()
    val pendingSyncCount by wireless.pendingSyncCount.collectAsState()
    val imageCacheVersion by wireless.imageCacheVersion.collectAsState()
    val isPushToTalk by viewModel.pushToTalkEnabled.collectAsState()
    val isThisDeviceActiveMic = activeMicOwner == ActiveMicOwner.CONTROLLER_MIC

    LaunchedEffect(autoMutedOnDisconnectCount) {
        if (autoMutedOnDisconnectCount > 0) {
            Toast.makeText(
                context,
                "检测到耳机断开，已自动关闭耳返以防啸叫",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var showHeadphoneGuardDialog by remember { mutableStateOf(false) }
    var showPolicyBottomSheet by remember { mutableStateOf(false) }
    var showWordDrawerSheet by remember { mutableStateOf(false) }
    // null = closed, 0 = 字表项目 tab, 1 = 发音人档案 tab
    var projectSpeakerPickerTab by remember { mutableStateOf<Int?>(null) }

    // Ensure RECORD_AUDIO permission if Controller microphone is selected
    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPerm = granted
    }
    LaunchedEffect(activeMicOwner) {
        if (activeMicOwner == ActiveMicOwner.CONTROLLER_MIC && !hasMicPerm) {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = true) {
        onExitSession()
    }

    val currentWord = snapshot.currentWord
    val currentWordImgFile = remember(
        snapshot.projectId,
        currentWord?.wordId,
        currentWord?.imageToken,
        imageCacheVersion
    ) {
        wireless.resolveRemoteWordImageFile(snapshot.projectId, currentWord)
    }
    val currentWordImage by rememberWordImageBitmap(
        file = currentWordImgFile,
        maxEdgePx = 320,
        extraKey = imageCacheVersion
    )
    val onToggleGlobalHideWordText: () -> Unit = {
        wireless.updatePromptPolicy { p ->
            val currentlyHidden = p.hideTextWhenImagePresent xor p.toggleTextTemporaryForCurrentWord
            p.copy(
                hideTextWhenImagePresent = !currentlyHidden,
                toggleTextTemporaryForCurrentWord = false
            )
        }
    }
    val onToggleShuffleWithToast: () -> Unit = {
        val enabledNow = wireless.requestToggleShuffle()
        Toast.makeText(
            context,
            if (enabledNow) S.recording.shuffleEnabledToast else S.recording.shuffleRestoredToast,
            Toast.LENGTH_SHORT
        ).show()
    }
    var noteField by remember(currentWord?.wordId) {
        val initial = currentWord?.speakerNote ?: ""
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    LaunchedEffect(currentWord?.wordId, currentWord?.speakerNote) {
        val remoteNote = currentWord?.speakerNote ?: ""
        if (remoteNote != noteField.text) {
            noteField = TextFieldValue(text = remoteNote, selection = TextRange(remoteNote.length))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("remote_controller_screen_root")
    ) {
        val isWideConsole = maxWidth >= 860.dp

        Scaffold(
            topBar = {
                ControllerTopBar(
                    snapshot = snapshot,
                    isConnected = isConnected,
                    isHostingWaiting = isHostingWaiting,
                    pairingPin = pairingPin,
                    peerName = peerName,
                    rttMs = rttMs,
                    activeMicOwner = activeMicOwner,
                    isMonitoring = isMonitoring,
                    isIntermission = policy.isIntermissionBreak,
                    pendingSyncCount = pendingSyncCount,
                    showWordDrawerAction = !isWideConsole,
                    onOpenProjectSwitcher = { projectSpeakerPickerTab = 0 },
                    onOpenSpeakerSwitcher = { projectSpeakerPickerTab = 1 },
                    onToggleShuffle = onToggleShuffleWithToast,
                    onToggleMicOwner = {
                        val next = if (activeMicOwner == ActiveMicOwner.PROMPT_DISPLAY_MIC) {
                            ActiveMicOwner.CONTROLLER_MIC
                        } else {
                            ActiveMicOwner.PROMPT_DISPLAY_MIC
                        }
                        wireless.setActiveMicOwner(next)
                    },
                    onToggleEarMonitor = {
                        if (isMonitoring) {
                            wireless.earMonitor.setMonitoringEnabled(false)
                        } else {
                            val ok = wireless.earMonitor.setMonitoringEnabled(true, forceSpeakerOverride = false)
                            if (!ok) showHeadphoneGuardDialog = true
                        }
                    },
                    onToggleIntermission = { wireless.toggleIntermissionBreak() },
                    onOpenPolicySheet = { showPolicyBottomSheet = true },
                    onOpenWordDrawer = { showWordDrawerSheet = true },
                    onDisconnect = onExitSession
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Sleek floating status banner with smooth expand/collapse animation
                AnimatedVisibility(
                    visible = statusBanner != null,
                    enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
                ) {
                    val bannerText = statusBanner.orEmpty()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiTethering,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = bannerText,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { wireless.clearBanner() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(S.common.gotIt, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                if (isWideConsole) {
                    // Wide Tablet / Windows Desktop 3-Zone Console clamped to 1360dp
                    ResponsiveContentContainer(
                        maxWidth = 1360.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                        // Left: Wordlist Navigator
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .width(256.dp)
                                .fillMaxHeight()
                        ) {
                            ControllerWordListPane(
                                snapshot = snapshot,
                                onOpenProjectSwitcher = { projectSpeakerPickerTab = 0 },
                                onToggleShuffle = onToggleShuffleWithToast,
                                onSelectSeqPos = { pos -> wireless.requestSelectWordSeqPos(pos) }
                            )
                        }

                        // Center: Live Acoustic Monitor & Primary Transport
                        Column(
                            modifier = Modifier
                                .weight(1.18f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ControllerCenterStageCard(
                                snapshot = snapshot,
                                currentWord = currentWord,
                                currentWordImage = currentWordImage,
                                isConnected = isConnected,
                                isThisDeviceActiveMic = isThisDeviceActiveMic,
                                isIntermission = policy.isIntermissionBreak,
                                isRemarkTempRevealed = policy.revealRemarkTemporaryForCurrentWord,
                                showRemarkGlobally = policy.showRemarkGlobally,
                                hideTextWhenImagePresent = policy.hideTextWhenImagePresent,
                                isTextTempToggled = policy.toggleTextTemporaryForCurrentWord,
                                dbfs = remoteDbfs,
                                isClipping = remoteIsClipping,
                                waveform = remoteWaveform,
                                isAuditionPlaying = isAuditionPlaying,
                                strictPushToTalk = isPushToTalk,
                                autoAdvanceOnHoldRelease = policy.autoAdvanceOnHoldRelease,
                                onToggleStrictPushToTalk = { viewModel.setPushToTalkEnabled(!isPushToTalk) },
                                onToggleAutoAdvanceHold = {
                                    viewModel.setAutoAdvanceOnHoldRelease(!policy.autoAdvanceOnHoldRelease)
                                },
                                onOpenProjectSwitcher = { projectSpeakerPickerTab = 0 },
                                onOpenSpeakerSwitcher = { projectSpeakerPickerTab = 1 },
                                onToggleTempRemark = { wireless.toggleTemporaryRemarkForCurrentWord() },
                                onToggleGlobalHideWordText = onToggleGlobalHideWordText,
                                onToggleTempWordText = { wireless.toggleTemporaryTextVisibilityForCurrentWord() },
                                onPrev = { wireless.requestPrevWord() },
                                onStartRecord = { wireless.requestStartRecording() },
                                onStopRecord = {
                                    wireless.requestStopRecording(
                                        fromHoldRelease = false,
                                        initiatedByRole = LinkRole.CONTROLLER
                                    )
                                },
                                onStopFromHoldRelease = {
                                    wireless.requestStopRecording(
                                        fromHoldRelease = true,
                                        initiatedByRole = LinkRole.CONTROLLER
                                    )
                                },
                                onToggleRecord = {
                                    wireless.requestToggleRecording(initiatedByRole = LinkRole.CONTROLLER)
                                },
                                onNext = { wireless.requestNextWord() },
                                onJumpNextUnrecorded = { wireless.requestJumpNextUnrecorded() },
                                onAudition = { wireless.requestAuditionCurrentWordOnThisDevice() },
                                onToggleFlag = { currentWord?.let { wireless.requestToggleQualityFlag(it.wordId) } }
                            )
                        }

                        // Right: Quick IPA / Tone / Fieldwork Shorthand
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(0.95f)
                                .fillMaxHeight()
                        ) {
                            ControllerShorthandPane(
                                snapshot = snapshot,
                                currentWord = currentWord,
                                noteField = noteField,
                                onNoteFieldChange = { updated ->
                                    val textChanged = updated.text != noteField.text
                                    noteField = updated
                                    if (textChanged) {
                                        currentWord?.let { wireless.requestUpdateSpeakerNote(it.wordId, updated.text) }
                                    }
                                }
                            )
                        }
                    }
                    }
                } else {
                    // Compact Phone / Narrow Window Console: Vertical stacked ergonomic layout clamped to 680dp
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        ResponsiveContentContainer(maxWidth = 680.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ControllerCenterStageCard(
                            snapshot = snapshot,
                            currentWord = currentWord,
                            currentWordImage = currentWordImage,
                            isConnected = isConnected,
                            isThisDeviceActiveMic = isThisDeviceActiveMic,
                            isIntermission = policy.isIntermissionBreak,
                            isRemarkTempRevealed = policy.revealRemarkTemporaryForCurrentWord,
                            showRemarkGlobally = policy.showRemarkGlobally,
                            hideTextWhenImagePresent = policy.hideTextWhenImagePresent,
                            isTextTempToggled = policy.toggleTextTemporaryForCurrentWord,
                            dbfs = remoteDbfs,
                            isClipping = remoteIsClipping,
                            waveform = remoteWaveform,
                            isAuditionPlaying = isAuditionPlaying,
                            strictPushToTalk = isPushToTalk,
                            autoAdvanceOnHoldRelease = policy.autoAdvanceOnHoldRelease,
                            onToggleStrictPushToTalk = { viewModel.setPushToTalkEnabled(!isPushToTalk) },
                            onToggleAutoAdvanceHold = {
                                viewModel.setAutoAdvanceOnHoldRelease(!policy.autoAdvanceOnHoldRelease)
                            },
                            onOpenProjectSwitcher = { projectSpeakerPickerTab = 0 },
                            onOpenSpeakerSwitcher = { projectSpeakerPickerTab = 1 },
                            onToggleTempRemark = { wireless.toggleTemporaryRemarkForCurrentWord() },
                            onToggleGlobalHideWordText = onToggleGlobalHideWordText,
                            onToggleTempWordText = { wireless.toggleTemporaryTextVisibilityForCurrentWord() },
                            onPrev = { wireless.requestPrevWord() },
                            onStartRecord = { wireless.requestStartRecording() },
                            onStopRecord = {
                                wireless.requestStopRecording(
                                    fromHoldRelease = false,
                                    initiatedByRole = LinkRole.CONTROLLER
                                )
                            },
                            onStopFromHoldRelease = {
                                wireless.requestStopRecording(
                                    fromHoldRelease = true,
                                    initiatedByRole = LinkRole.CONTROLLER
                                )
                            },
                            onToggleRecord = {
                                wireless.requestToggleRecording(initiatedByRole = LinkRole.CONTROLLER)
                            },
                            onNext = { wireless.requestNextWord() },
                            onJumpNextUnrecorded = { wireless.requestJumpNextUnrecorded() },
                            onAudition = { wireless.requestAuditionCurrentWordOnThisDevice() },
                            onToggleFlag = { currentWord?.let { wireless.requestToggleQualityFlag(it.wordId) } }
                        )

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ControllerShorthandPane(
                                snapshot = snapshot,
                                currentWord = currentWord,
                                noteField = noteField,
                                onNoteFieldChange = { updated ->
                                    val textChanged = updated.text != noteField.text
                                    noteField = updated
                                    if (textChanged) {
                                        currentWord?.let { wireless.requestUpdateSpeakerNote(it.wordId, updated.text) }
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

        // Project & Speaker Switcher Modal Dialog
        com.example.ui.components.AnimatedNullableDialogVisibility(value = projectSpeakerPickerTab) { tab ->
            ControllerProjectSpeakerDialog(
                snapshot = snapshot,
                initialTab = tab,
                onSelectProject = { projId ->
                    wireless.requestSelectProject(projId)
                    projectSpeakerPickerTab = null
                },
                onSelectSpeaker = { spkId ->
                    wireless.requestSelectSpeaker(spkId)
                    projectSpeakerPickerTab = null
                },
                onCreateSpeaker = { name, gender, age, dialect ->
                    wireless.requestCreateSpeaker(name, gender, age, dialect)
                },
                onDismiss = { projectSpeakerPickerTab = null }
            )
        }

        // Headphone Anti-Feedback Guard Dialog
        com.example.ui.components.AnimatedDialogVisibility(visible = showHeadphoneGuardDialog) {
            com.example.ui.components.StudioAlertDialog(
                onDismissRequest = { showHeadphoneGuardDialog = false },
                title = { Text(S.wireless.noHeadphonesTitle, fontWeight = FontWeight.Bold) },
                text = {
                    Text(S.wireless.noHeadphonesBody)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            wireless.earMonitor.setMonitoringEnabled(true, forceSpeakerOverride = true)
                            showHeadphoneGuardDialog = false
                        }
                    ) {
                        Text(S.wireless.enableAnywayButton, maxLines = 1, softWrap = false)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHeadphoneGuardDialog = false }) {
                        Text(S.common.cancel, maxLines = 1, softWrap = false)
                    }
                }
            )
        }

        // Live Quick Settings Sheet for 看读端 Permissions & Visual Presets
        StudioAdaptiveSheet(
            visible = showPolicyBottomSheet,
            onDismissRequest = { showPolicyBottomSheet = false },
            maxWidth = 580.dp
        ) { _ ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = S.wireless.linkSettingsSheetTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                MultiDeviceLinkCategoryContent(
                    viewModel = viewModel,
                    onOpenPairingDialog = {},
                    showLaunchCard = false
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Mobile Word List Sheet
        StudioAdaptiveSheet(
            visible = showWordDrawerSheet,
            onDismissRequest = { showWordDrawerSheet = false },
            maxWidth = 540.dp
        ) { onRequestClose ->
            Box(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                ControllerWordListPane(
                    snapshot = snapshot,
                    onOpenProjectSwitcher = {
                        onRequestClose()
                        projectSpeakerPickerTab = 0
                    },
                    onToggleShuffle = onToggleShuffleWithToast,
                    onSelectSeqPos = { pos ->
                        wireless.requestSelectWordSeqPos(pos)
                        onRequestClose()
                    }
                )
            }
        }

        // Full-Screen Perimeter Edge-Glow + Toast when this 遥控端 is the Active Microphone Endpoint
        ActiveMicEdgeGlowOverlay(
            isConnected = isConnected,
            connectionEpoch = connectionEpoch,
            isThisDeviceActiveMic = isThisDeviceActiveMic,
            thisRoleLabel = S.wireless.roleNameController,
            peerRoleLabel = S.wireless.roleNamePrompt
        )
    }
}

@Composable
private fun ControllerTopBar(
    snapshot: RemoteSessionSnapshot,
    isConnected: Boolean,
    isHostingWaiting: Boolean,
    pairingPin: String,
    peerName: String,
    rttMs: Int,
    activeMicOwner: ActiveMicOwner,
    isMonitoring: Boolean,
    isIntermission: Boolean,
    pendingSyncCount: Int,
    showWordDrawerAction: Boolean,
    onOpenProjectSwitcher: () -> Unit,
    onOpenSpeakerSwitcher: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleMicOwner: () -> Unit,
    onToggleEarMonitor: () -> Unit,
    onToggleIntermission: () -> Unit,
    onOpenPolicySheet: () -> Unit,
    onOpenWordDrawer: () -> Unit,
    onDisconnect: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isConnected) cs.primaryContainer else cs.secondaryContainer,
                        modifier = Modifier.testTag("controller_status_capsule")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) cs.primary else cs.outline)
                            )
                            Text(
                                text = when {
                                    isConnected -> S.wireless.topStatusConnected.withArgs("peer" to peerName)
                                    isHostingWaiting -> S.wireless.topStatusWaiting
                                    else -> S.wireless.topStatusDisconnected
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) cs.onPrimaryContainer else cs.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (pendingSyncCount > 0) {
                        Text(
                            text = S.wireless.pendingSyncChip.withArgs("count" to pendingSyncCount),
                            fontSize = 11.sp,
                            color = cs.tertiary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (showWordDrawerAction) {
                        val listInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = onOpenWordDrawer,
                            interactionSource = listInteraction,
                            modifier = Modifier
                                .size(40.dp)
                                .studioTactile(listInteraction, pressScale = 0.90f)
                                .testTag("controller_word_list_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = S.wireless.openCatalogDesc, modifier = Modifier.size(21.dp))
                        }
                    }
                    val policyInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onOpenPolicySheet,
                        interactionSource = policyInteraction,
                        modifier = Modifier
                            .size(40.dp)
                            .studioTactile(policyInteraction, pressScale = 0.90f)
                            .testTag("controller_policy_sheet_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = S.wireless.promptSettingsDesc, modifier = Modifier.size(21.dp))
                    }
                    val disconnectInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onDisconnect,
                        interactionSource = disconnectInteraction,
                        modifier = Modifier
                            .size(40.dp)
                            .studioTactile(disconnectInteraction, pressScale = 0.90f)
                            .testTag("controller_disconnect_button")
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = S.wireless.disconnectLinkDesc, modifier = Modifier.size(21.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Single-row horizontally scrollable action strip: no orphan second-row wrapping!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioFilterChip(
                    selected = snapshot.projectId > 0L,
                    onClick = onOpenProjectSwitcher,
                    labelText = if (snapshot.projectId > 0L) S.wireless.chipProjectPrefix.withArgs("name" to snapshot.projectName) else S.wireless.chipSelectProject,
                    showCheckmark = false,
                    leadingIcon = {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier.testTag("controller_switch_project_chip")
                )

                StudioFilterChip(
                    selected = false,
                    onClick = onOpenSpeakerSwitcher,
                    labelText = S.wireless.chipSpeakerPrefix.withArgs("name" to snapshot.speakerName),
                    showCheckmark = false,
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier.testTag("controller_switch_speaker_chip")
                )

                if (snapshot.projectId > 0L) {
                    StudioFilterChip(
                        selected = snapshot.isShuffleEnabled,
                        onClick = onToggleShuffle,
                        labelText = if (snapshot.isShuffleEnabled) S.recording.shuffledLabel else S.recording.shuffleLabel,
                        showCheckmark = false,
                        leadingIcon = {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.testTag("controller_shuffle_chip")
                    )
                }

                StudioFilterChip(
                    selected = activeMicOwner == ActiveMicOwner.CONTROLLER_MIC,
                    onClick = onToggleMicOwner,
                    labelText = if (activeMicOwner == ActiveMicOwner.CONTROLLER_MIC) {
                        S.wireless.chipLocalMicController
                    } else {
                        S.wireless.chipPeerMicPrompt
                    },
                    showCheckmark = false,
                    leadingIcon = {
                        Icon(
                            imageVector = if (activeMicOwner == ActiveMicOwner.CONTROLLER_MIC) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    modifier = Modifier.testTag("controller_mic_switch_chip")
                )

                StudioFilterChip(
                    selected = isMonitoring,
                    onClick = onToggleEarMonitor,
                    labelText = if (isMonitoring) S.wireless.chipMonitorOn else S.wireless.chipMonitorOff,
                    showCheckmark = false,
                    leadingIcon = {
                        Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier.testTag("controller_ear_monitor_chip")
                )

                StudioFilterChip(
                    selected = isIntermission,
                    onClick = onToggleIntermission,
                    labelText = if (isIntermission) S.wireless.chipResumePrompt else S.wireless.chipIntermission,
                    showCheckmark = false,
                    leadingIcon = {
                        Icon(Icons.Default.LocalCafe, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier.testTag("controller_intermission_button")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerCenterStageCard(
    snapshot: RemoteSessionSnapshot,
    currentWord: RemoteWordItem?,
    currentWordImage: ImageBitmap? = null,
    isConnected: Boolean,
    isThisDeviceActiveMic: Boolean = false,
    isIntermission: Boolean,
    isRemarkTempRevealed: Boolean,
    showRemarkGlobally: Boolean,
    hideTextWhenImagePresent: Boolean = false,
    isTextTempToggled: Boolean = false,
    dbfs: Float,
    isClipping: Boolean,
    waveform: List<Float>,
    isAuditionPlaying: Boolean,
    strictPushToTalk: Boolean,
    autoAdvanceOnHoldRelease: Boolean = true,
    onToggleStrictPushToTalk: () -> Unit,
    onToggleAutoAdvanceHold: () -> Unit = {},
    onOpenProjectSwitcher: () -> Unit,
    onOpenSpeakerSwitcher: () -> Unit,
    onToggleTempRemark: () -> Unit,
    onToggleGlobalHideWordText: () -> Unit = {},
    onToggleTempWordText: () -> Unit = {},
    onPrev: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onStopFromHoldRelease: () -> Unit = onStopRecord,
    onToggleRecord: () -> Unit,
    onNext: () -> Unit,
    onJumpNextUnrecorded: () -> Unit,
    onAudition: () -> Unit,
    onToggleFlag: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var isHoldingRecord by remember { mutableStateOf(false) }
    var recordingTenths by remember { mutableIntStateOf(0) }

    LaunchedEffect(snapshot.isRecording) {
        if (!snapshot.isRecording) {
            isHoldingRecord = false
            recordingTenths = 0
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            recordingTenths = (elapsed / 100L).toInt()
            delay(80L)
        }
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(
            width = if (snapshot.isRecording) 2.dp else 1.dp,
            color = if (snapshot.isRecording) cs.primary else cs.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            // Interactive Project & Speaker Switcher Header Row
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
                    val projPillInteraction = remember { MutableInteractionSource() }
                    val isProjPillHovered by projPillInteraction.collectIsHoveredAsState()
                    val projPillBg by animateColorAsState(
                        targetValue = if (isProjPillHovered) {
                            lerp(cs.surfaceContainerHigh, cs.primaryContainer, 0.45f)
                        } else {
                            cs.surfaceContainerHigh.copy(alpha = 0.72f)
                        },
                        animationSpec = tween(140),
                        label = "HeaderProjPillBg"
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = projPillBg,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .studioTactile(projPillInteraction, pressScale = 0.96f)
                            .clip(RoundedCornerShape(10.dp))
                            .hoverable(projPillInteraction)
                            .clickable(
                                interactionSource = projPillInteraction,
                                indication = null,
                                onClick = onOpenProjectSwitcher
                            )
                            .testTag("controller_header_project_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = snapshot.projectName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = S.wireless.switchProjectDesc,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    val spkPillInteraction = remember { MutableInteractionSource() }
                    val isSpkPillHovered by spkPillInteraction.collectIsHoveredAsState()
                    val spkPillBg by animateColorAsState(
                        targetValue = if (isSpkPillHovered) {
                            lerp(cs.surfaceContainerHigh, cs.primaryContainer, 0.45f)
                        } else {
                            cs.surfaceContainerHigh.copy(alpha = 0.72f)
                        },
                        animationSpec = tween(140),
                        label = "HeaderSpkPillBg"
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = spkPillBg,
                        modifier = Modifier
                            .studioTactile(spkPillInteraction, pressScale = 0.96f)
                            .clip(RoundedCornerShape(10.dp))
                            .hoverable(spkPillInteraction)
                            .clickable(
                                interactionSource = spkPillInteraction,
                                indication = null,
                                onClick = onOpenSpeakerSwitcher
                            )
                            .testTag("controller_header_speaker_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = cs.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = snapshot.speakerName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = S.wireless.switchSpeakerDesc,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (snapshot.projectId > 0L && snapshot.totalWords > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = S.wireless.progressCounterLine.withArgs(
                            "pos" to snapshot.currentSeqPos + 1,
                            "total" to snapshot.totalWords,
                            "recorded" to snapshot.recordedCount
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (isIntermission) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cs.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = S.wireless.intermissionControllerNotice,
                        fontSize = 12.sp,
                        color = cs.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Center Word Area OR Accurate Empty State
            when {
                !snapshot.hasSnapshotArrived && isConnected -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = S.wireless.loadingWordlistHint,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }

                snapshot.projectId <= 0L -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerLowest,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("controller_no_project_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = cs.primaryContainer,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = cs.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Text(
                                text = S.wireless.noProjectControllerTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onSurface
                            )
                            Text(
                                text = if (snapshot.availableProjects.isNotEmpty()) {
                                    S.wireless.pickProjectControllerHint
                                } else {
                                    S.wireless.createProjectFirstHint
                                },
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (snapshot.availableProjects.isNotEmpty()) {
                                val pickProjInteraction = remember { MutableInteractionSource() }
                                Button(
                                    onClick = onOpenProjectSwitcher,
                                    interactionSource = pickProjInteraction,
                                    shape = RoundedCornerShape(13.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .studioTactile(pickProjInteraction, pressScale = 0.965f)
                                        .testTag("controller_open_project_picker_button")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(S.wireless.selectProjectAndSpeakerBtn, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }

                currentWord == null -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = S.wireless.emptyWordlistControllerTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface
                            )
                            OutlinedButton(
                                onClick = onOpenProjectSwitcher,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(S.wireless.switchOtherWordlistBtn, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                else -> {
                    AnimatedContent(
                        targetState = currentWord,
                        transitionSpec = {
                            val dir = if (targetState.seqPos >= initialState.seqPos) 1 else -1
                            (fadeIn(tween(170)) + slideInVertically(tween(190, easing = FastOutSlowInEasing)) { h -> dir * (h / 8) }) togetherWith
                                (fadeOut(tween(110)) + slideOutVertically(tween(130)) { h -> -dir * (h / 10) })
                        },
                        label = "ControllerWordTransition"
                    ) { word ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (word.groupName.isNotBlank()) {
                                Text(
                                    text = S.wireless.wordGroupBracketFormat.withArgs("group" to word.groupName),
                                    fontSize = 12.sp,
                                    color = cs.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                            ) {
                                if (word.imageToken.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cs.surfaceContainerHighest,
                                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .size(60.dp)
                                            .testTag("controller_word_image_thumb")
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            if (currentWordImage != null) {
                                                Image(
                                                    bitmap = currentWordImage,
                                                    contentDescription = word.text,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(11.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = S.wireless.wordImageDesc,
                                                    tint = cs.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = word.text,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cs.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("controller_current_word_text")
                                )
                            }

                            if (word.remark.isNotBlank() || word.imageToken.isNotBlank()) {
                                val effectiveTextHiddenOnPrompt = hideTextWhenImagePresent xor isTextTempToggled
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (word.remark.isNotBlank()) {
                                        Text(
                                            text = S.wireless.investigationPromptPrefix.withArgs("remark" to word.remark),
                                            fontSize = 13.sp,
                                            color = cs.onSurfaceVariant,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                        if (!showRemarkGlobally) {
                                            val tempRemarkInteraction = remember { MutableInteractionSource() }
                                            val isTempRemarkHovered by tempRemarkInteraction.collectIsHoveredAsState()
                                            val tempRemarkBg by animateColorAsState(
                                                targetValue = when {
                                                    isRemarkTempRevealed -> cs.primaryContainer
                                                    isTempRemarkHovered -> lerp(cs.surfaceContainerHigh, cs.primaryContainer, 0.35f)
                                                    else -> cs.surfaceContainerHigh
                                                },
                                                animationSpec = tween(140),
                                                label = "TempRemarkPillBg"
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = tempRemarkBg,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isRemarkTempRevealed || isTempRemarkHovered) cs.primary.copy(alpha = 0.5f) else cs.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .studioTactile(tempRemarkInteraction, pressScale = 0.95f)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .hoverable(tempRemarkInteraction)
                                                    .clickable(
                                                        interactionSource = tempRemarkInteraction,
                                                        indication = null,
                                                        onClick = onToggleTempRemark
                                                    )
                                                    .testTag("controller_temp_reveal_remark_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isRemarkTempRevealed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = null,
                                                        tint = if (isRemarkTempRevealed) cs.primary else cs.onSurfaceVariant,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = if (isRemarkTempRevealed) S.wireless.remarkShownOnPrompt else S.wireless.revealRemarkForWord,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isRemarkTempRevealed) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (word.imageToken.isNotBlank()) {
                                        val hideTextInteraction = remember { MutableInteractionSource() }
                                        val isHideTextHovered by hideTextInteraction.collectIsHoveredAsState()
                                        val hideTextBg by animateColorAsState(
                                            targetValue = when {
                                                effectiveTextHiddenOnPrompt -> cs.primaryContainer
                                                isHideTextHovered -> lerp(cs.surfaceContainerHigh, cs.primaryContainer, 0.35f)
                                                else -> cs.surfaceContainerHigh
                                            },
                                            animationSpec = tween(140),
                                            label = "HideTextPillBg"
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = hideTextBg,
                                            border = BorderStroke(
                                                1.dp,
                                                if (effectiveTextHiddenOnPrompt || isHideTextHovered) cs.primary.copy(alpha = 0.5f) else cs.outlineVariant
                                            ),
                                            modifier = Modifier
                                                .studioTactile(hideTextInteraction, pressScale = 0.95f)
                                                .clip(RoundedCornerShape(999.dp))
                                                .hoverable(hideTextInteraction)
                                                .clickable(
                                                    interactionSource = hideTextInteraction,
                                                    indication = null,
                                                    onClick = onToggleGlobalHideWordText
                                                )
                                                .testTag("controller_toggle_word_text_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (effectiveTextHiddenOnPrompt) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = if (effectiveTextHiddenOnPrompt) cs.primary else cs.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = if (effectiveTextHiddenOnPrompt) S.wireless.textHiddenOnPrompt else S.wireless.hideTextOnPrompt,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (effectiveTextHiddenOnPrompt) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        if (hideTextWhenImagePresent) {
                                            val tempTextInteraction = remember { MutableInteractionSource() }
                                            val isTempTextHovered by tempTextInteraction.collectIsHoveredAsState()
                                            val tempTextBg by animateColorAsState(
                                                targetValue = when {
                                                    isTextTempToggled -> cs.secondaryContainer
                                                    isTempTextHovered -> lerp(cs.surfaceContainerHigh, cs.secondaryContainer, 0.40f)
                                                    else -> cs.surfaceContainerHigh
                                                },
                                                animationSpec = tween(140),
                                                label = "TempTextPillBg"
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = tempTextBg,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isTextTempToggled || isTempTextHovered) cs.secondary.copy(alpha = 0.5f) else cs.outlineVariant
                                                ),
                                                modifier = Modifier
                                                    .studioTactile(tempTextInteraction, pressScale = 0.95f)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .hoverable(tempTextInteraction)
                                                    .clickable(
                                                        interactionSource = tempTextInteraction,
                                                        indication = null,
                                                        onClick = onToggleTempWordText
                                                    )
                                                    .testTag("controller_temp_toggle_word_text_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Text(
                                                        text = if (isTextTempToggled) S.wireless.textTempToggledDone else S.wireless.tempToggleText,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isTextTempToggled) cs.onSecondaryContainer else cs.onSurfaceVariant,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Quality status pill + concise investigator coaching diagnostic
                            if (word.isRecorded) {
                                val status = word.qualityStatus ?: "正常"
                                val isWarn = status != "正常"
                                val statusDisplay = S.quality.displayStatus(status, simpleMode = false)
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isWarn) cs.errorContainer else cs.primaryContainer.copy(alpha = 0.72f)
                                ) {
                                    Text(
                                        text = S.wireless.recordedDurationAndStatus.withArgs(
                                            "sec" to String.format(Locale.US, "%.2f", word.durationMs / 1000f),
                                            "status" to statusDisplay
                                        ),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isWarn) cs.onErrorContainer else cs.onPrimaryContainer,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }

                                val techHint = snapshot.latestCoaching
                                    ?.takeIf { it.wordId == word.wordId && !snapshot.isRecording }
                                    ?.controllerHint
                                if (!techHint.isNullOrBlank()) {
                                    Text(
                                        text = techHint,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        color = cs.error,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .testTag("controller_coaching_tech_hint")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Acoustic Level Meter & Waveform (kept exclusively on 遥控端!)
            ControllerLiveMeterBox(
                isRecording = snapshot.isRecording,
                isThisDeviceActiveMic = isThisDeviceActiveMic,
                dbfs = dbfs,
                isClipping = isClipping,
                waveform = waveform
            )

            // Sculpted Studio Transport Stage (Flanking Nav Capsules + Tactile Studio Record Orb)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canPrev = currentWord != null && snapshot.currentSeqPos > 0
                    val prevInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = onPrev,
                        enabled = canPrev,
                        interactionSource = prevInteraction,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.surfaceContainerHighest,
                            contentColor = cs.onSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            cs.outlineVariant.copy(alpha = if (canPrev) 0.55f else 0.25f)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 50.dp)
                            .studioTactile(prevInteraction, enabled = canPrev, pressScale = 0.955f)
                            .testTag("controller_prev_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.wireless.btnPrevWord,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = S.wireless.btnPrevWord,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    StudioRecordTriggerButton(
                        isRecording = snapshot.isRecording,
                        enabled = currentWord != null,
                        strictPushToTalk = strictPushToTalk,
                        dbfs = dbfs,
                        outerSize = 90.dp,
                        coreSize = 68.dp,
                        testTag = "controller_record_button",
                        onHoldActiveChanged = { holding -> isHoldingRecord = holding },
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                        onStopFromHoldRelease = onStopFromHoldRelease,
                        onToggleRecord = onToggleRecord
                    )

                    val canNext = currentWord != null && snapshot.currentSeqPos < snapshot.totalWords - 1
                    val nextInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = onNext,
                        enabled = canNext,
                        interactionSource = nextInteraction,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.surfaceContainerHighest,
                            contentColor = cs.onSurface
                        ),
                        border = BorderStroke(
                            1.dp,
                            cs.outlineVariant.copy(alpha = if (canNext) 0.55f else 0.25f)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 50.dp)
                            .studioTactile(nextInteraction, enabled = canNext, pressScale = 0.955f)
                            .testTag("controller_next_button")
                    ) {
                        Text(
                            text = S.wireless.btnNextWord,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = S.wireless.btnNextWord,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Live Timer & Gesture Mode Hint / Toggle Strip
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val elapsedFormatted = S.wireless.secShortFormat.withArgs(
                        "sec" to String.format(Locale.US, "%.1f", recordingTenths / 10f)
                    )
                    val statusHintText = when {
                        snapshot.isRecording && (isHoldingRecord || strictPushToTalk) ->
                            if (autoAdvanceOnHoldRelease) {
                                S.wireless.statusHoldReleaseAdvance.withArgs("time" to elapsedFormatted)
                            } else {
                                S.wireless.statusReleaseStop.withArgs("time" to elapsedFormatted)
                            }
                        snapshot.isRecording ->
                            S.wireless.statusTapEnd.withArgs("time" to elapsedFormatted)
                        strictPushToTalk ->
                            if (autoAdvanceOnHoldRelease) S.wireless.statusPressHoldAdvance else S.wireless.statusPressHoldStop
                        else ->
                            if (autoAdvanceOnHoldRelease) S.wireless.statusTapOrHoldAdvance else S.wireless.statusTapOrHoldStop
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (snapshot.isRecording) {
                            cs.errorContainer
                        } else {
                            cs.surfaceContainerHigh.copy(alpha = 0.72f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (snapshot.isRecording) cs.error else cs.primary.copy(alpha = 0.75f)
                                    )
                            )
                            Text(
                                text = statusHintText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (snapshot.isRecording) cs.onErrorContainer else cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    val holdAdvInteraction = remember { MutableInteractionSource() }
                    val isHoldAdvHovered by holdAdvInteraction.collectIsHoveredAsState()
                    val holdAdvBg by animateColorAsState(
                        targetValue = when {
                            autoAdvanceOnHoldRelease -> cs.primaryContainer
                            isHoldAdvHovered -> lerp(cs.surfaceContainerHighest, cs.primaryContainer, 0.32f)
                            else -> cs.surfaceContainerHighest
                        },
                        animationSpec = tween(140),
                        label = "HoldAdvPillBg"
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = holdAdvBg,
                        border = BorderStroke(
                            1.dp,
                            if (autoAdvanceOnHoldRelease || isHoldAdvHovered) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .studioTactile(holdAdvInteraction, pressScale = 0.95f)
                            .clip(RoundedCornerShape(999.dp))
                            .hoverable(holdAdvInteraction)
                            .clickable(
                                interactionSource = holdAdvInteraction,
                                indication = null,
                                onClick = onToggleAutoAdvanceHold
                            )
                            .testTag("controller_hold_auto_advance_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = if (autoAdvanceOnHoldRelease) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (autoAdvanceOnHoldRelease) S.wireless.toggleHoldReleaseOn else S.wireless.toggleHoldReleaseOff,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (autoAdvanceOnHoldRelease) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    val pttInteraction = remember { MutableInteractionSource() }
                    val isPttHovered by pttInteraction.collectIsHoveredAsState()
                    val pttBg by animateColorAsState(
                        targetValue = when {
                            strictPushToTalk -> cs.primaryContainer
                            isPttHovered -> lerp(cs.surfaceContainerHighest, cs.primaryContainer, 0.32f)
                            else -> cs.surfaceContainerHighest
                        },
                        animationSpec = tween(140),
                        label = "PttPillBg"
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = pttBg,
                        border = BorderStroke(
                            1.dp,
                            if (strictPushToTalk || isPttHovered) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .studioTactile(pttInteraction, pressScale = 0.95f)
                            .clip(RoundedCornerShape(999.dp))
                            .hoverable(pttInteraction)
                            .clickable(
                                interactionSource = pttInteraction,
                                indication = null,
                                onClick = onToggleStrictPushToTalk
                            )
                            .testTag("controller_ptt_mode_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = if (strictPushToTalk) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (strictPushToTalk) S.wireless.toggleStrictPttOnly else S.wireless.toggleStrictPttBoth,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (strictPushToTalk) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Secondary Row: Remote Headphone Audition, Flag Retake, Jump Unrecorded
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val auditionEnabled = currentWord?.isRecorded == true
                val auditionInteraction = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = onAudition,
                    enabled = auditionEnabled,
                    interactionSource = auditionInteraction,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .defaultMinSize(minHeight = 48.dp)
                        .studioTactile(auditionInteraction, enabled = auditionEnabled, pressScale = 0.96f)
                        .testTag("controller_audition_button")
                ) {
                    Icon(
                        imageVector = if (isAuditionPlaying) Icons.Default.Stop else Icons.Default.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isAuditionPlaying) S.wireless.btnStopEarMonitor else S.wireless.btnEarMonitor,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                val flagEnabled = currentWord?.isRecorded == true
                val flagInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onToggleFlag,
                    enabled = flagEnabled,
                    interactionSource = flagInteraction,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .studioTactile(flagInteraction, enabled = flagEnabled, pressScale = 0.96f)
                        .testTag("controller_flag_retake_button")
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (currentWord?.qualityStatus == "人工标重录") S.wireless.btnUnflagRerecord else S.wireless.btnFlagRerecord,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                val jumpEnabled = currentWord != null && snapshot.totalWords > snapshot.recordedCount
                val jumpInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onJumpNextUnrecorded,
                    enabled = jumpEnabled,
                    interactionSource = jumpInteraction,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .defaultMinSize(minHeight = 48.dp)
                        .studioTactile(jumpInteraction, enabled = jumpEnabled, pressScale = 0.96f)
                        .testTag("controller_next_unrecorded_button")
                ) {
                    Text(
                        text = S.wireless.btnJumpUnrecorded,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Dialog allowing the investigator on 遥控端 to switch 字表项目 or 发音人档案 (and create new speakers)
 * at any point during a wireless fieldwork session.
 */
@Composable
private fun ControllerProjectSpeakerDialog(
    snapshot: RemoteSessionSnapshot,
    initialTab: Int,
    onSelectProject: (Long) -> Unit,
    onSelectSpeaker: (Long) -> Unit,
    onCreateSpeaker: (name: String, gender: String, age: Int, dialect: String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var showCreateSpeakerForm by remember { mutableStateOf(false) }
    var newSpkName by remember { mutableStateOf("") }
    var newSpkGender by remember { mutableStateOf("男") }
    var newSpkAge by remember { mutableStateOf("55") }
    var newSpkDialect by remember { mutableStateOf("") }

    val hasCreateSpeakerInputs = selectedTab == 1 && showCreateSpeakerForm
    com.example.ui.components.StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = !hasCreateSpeakerInputs,
            usePlatformDefaultWidth = false
        )
    ) {
        StudioDialogEnterAnimation {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = cs.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(0.93f)
                    .heightIn(max = 640.dp)
                    .consumeDialogCardTaps()
                    .testTag("controller_project_speaker_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = S.wireless.switchProjectSpeakerTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = S.wireless.switchProjectSpeakerSub,
                                fontSize = 12.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = S.common.close)
                        }
                    }

                    // Segmented Tabs
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cs.surfaceContainerLow,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple(0, S.wireless.tabProjectsCount.withArgs("count" to snapshot.availableProjects.size), Icons.Default.FolderOpen),
                                Triple(1, S.wireless.tabSpeakersCount.withArgs("count" to snapshot.availableSpeakers.size), Icons.Default.Person)
                            ).forEach { (idx, label, icon) ->
                                val isSel = selectedTab == idx
                                val tabInteraction = remember { MutableInteractionSource() }
                                val isTabHovered by tabInteraction.collectIsHoveredAsState()
                                val bg by animateColorAsState(
                                    targetValue = when {
                                        isSel -> cs.primaryContainer
                                        isTabHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.28f)
                                        else -> cs.surfaceContainerLow
                                    },
                                    animationSpec = tween(160),
                                    label = "PickerTabBg"
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = bg,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 42.dp)
                                        .studioTactile(tabInteraction, pressScale = 0.975f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .hoverable(tabInteraction)
                                        .clickable(
                                            interactionSource = tabInteraction,
                                            indication = null
                                        ) { selectedTab = idx }
                                        .testTag(if (idx == 0) "controller_picker_tab_projects" else "controller_picker_tab_speakers")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSel || isTabHovered) cs.primary else cs.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSel) cs.onPrimaryContainer else if (isTabHovered) cs.primary else cs.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = cs.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    if (selectedTab == 0) {
                        if (snapshot.availableProjects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = S.wireless.noProjectsNotice,
                                    fontSize = 13.sp,
                                    color = cs.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(snapshot.availableProjects, key = { it.id }) { proj ->
                                    RemoteProjectOptionItem(
                                        proj = proj,
                                        isCurrent = proj.id == snapshot.projectId,
                                        onClick = { onSelectProject(proj.id) }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCreateSpeakerForm = !showCreateSpeakerForm },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("controller_toggle_new_speaker_form")
                            ) {
                                Icon(
                                    imageVector = if (showCreateSpeakerForm) Icons.Default.ExpandLess else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showCreateSpeakerForm) S.wireless.collapseCreateSpeaker else S.wireless.createSpeakerArchiveBtn,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            AnimatedVisibility(
                                visible = showCreateSpeakerForm,
                                enter = expandVertically(tween(180)) + fadeIn(tween(140)),
                                exit = shrinkVertically(tween(150)) + fadeOut(tween(110))
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = cs.surfaceContainerLow,
                                    border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = newSpkName,
                                                onValueChange = { newSpkName = it },
                                                label = { Text(S.wireless.speakerNameCodeLabel) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .testTag("controller_new_speaker_name_input")
                                            )
                                            OutlinedTextField(
                                                value = newSpkAge,
                                                onValueChange = { newSpkAge = it.filter(Char::isDigit).take(3) },
                                                label = { Text(S.speaker.ageLabel) },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.width(86.dp)
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf("男", "女").forEach { g ->
                                                StudioFilterChip(
                                                    selected = newSpkGender == g,
                                                    onClick = { newSpkGender = g },
                                                    labelText = S.speaker.displayGender(g),
                                                    showCheckmark = false
                                                )
                                            }
                                            OutlinedTextField(
                                                value = newSpkDialect,
                                                onValueChange = { newSpkDialect = it },
                                                label = { Text(S.wireless.dialectOptionalLabel) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val clean = newSpkName.trim()
                                                if (clean.isNotEmpty()) {
                                                    onCreateSpeaker(
                                                        clean,
                                                        newSpkGender,
                                                        newSpkAge.toIntOrNull() ?: 50,
                                                        newSpkDialect.trim()
                                                    )
                                                    newSpkName = ""
                                                    showCreateSpeakerForm = false
                                                    onDismiss()
                                                }
                                            },
                                            enabled = newSpkName.isNotBlank(),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 44.dp)
                                                .testTag("controller_confirm_create_speaker_btn")
                                        ) {
                                            Text(S.wireless.createAndSwitchSpeakerBtn, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(snapshot.availableSpeakers, key = { it.id }) { spk ->
                                    RemoteSpeakerOptionItem(
                                        spk = spk,
                                        isCurrent = spk.id == snapshot.speakerId,
                                        onClick = { onSelectSpeaker(spk.id) }
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

@Composable
private fun RemoteProjectOptionItem(
    proj: RemoteProjectSummary,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val cardBg by animateColorAsState(
        targetValue = when {
            isCurrent -> lerp(cs.surfaceContainerLow, cs.primaryContainer, if (isHovered) 0.55f else 0.45f)
            isHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.25f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = tween(140),
        label = "RemoteProjItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrent -> cs.primary
            isHovered -> cs.primary.copy(alpha = 0.48f)
            else -> cs.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(140),
        label = "RemoteProjItemBorder"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isCurrent) 1.8.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .studioTactile(interactionSource, pressScale = 0.985f)
            .clip(RoundedCornerShape(14.dp))
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("controller_project_option_${proj.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = proj.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                val meta = buildString {
                    if (proj.wordCount > 0) append(S.wireless.projectWordCountLine.withArgs("count" to proj.wordCount))
                    if (proj.description.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(proj.description)
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isCurrent) {
                Icon(Icons.Default.CheckCircle, contentDescription = S.wireless.currentProjectIconDesc, tint = cs.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RemoteSpeakerOptionItem(
    spk: RemoteSpeakerSummary,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val cardBg by animateColorAsState(
        targetValue = when {
            isCurrent -> lerp(cs.surfaceContainerLow, cs.primaryContainer, if (isHovered) 0.55f else 0.45f)
            isHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.25f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = tween(140),
        label = "RemoteSpkItemBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrent -> cs.primary
            isHovered -> cs.primary.copy(alpha = 0.48f)
            else -> cs.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(140),
        label = "RemoteSpkItemBorder"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isCurrent) 1.8.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .studioTactile(interactionSource, pressScale = 0.985f)
            .clip(RoundedCornerShape(14.dp))
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("controller_speaker_option_${spk.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = spk.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                val details = listOfNotNull(
                    spk.gender.takeIf { it.isNotBlank() }?.let { S.speaker.displayGender(it) },
                    spk.age.takeIf { it > 0 }?.let { S.common.ageWithUnit.withArgs("age" to it) },
                    spk.dialect.takeIf { it.isNotBlank() },
                    spk.groupName.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isCurrent) {
                Icon(Icons.Default.CheckCircle, contentDescription = S.wireless.currentSpeakerIconDesc, tint = cs.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ControllerLiveMeterBox(
    isRecording: Boolean,
    isThisDeviceActiveMic: Boolean = false,
    dbfs: Float,
    isClipping: Boolean,
    waveform: List<Float>
) {
    val cs = MaterialTheme.colorScheme
    val levelFraction = ((dbfs + 60f) / 60f).coerceIn(0f, 1f)
    val smoothProgress by animateFloatAsState(
        targetValue = if (isRecording) levelFraction else 0.02f,
        animationSpec = tween(durationMillis = 65),
        label = "ControllerMeterProgress"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainerLowest,
        border = BorderStroke(1.dp, if (isClipping) cs.error else cs.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isClipping -> S.wireless.bannerClip
                        isRecording && isThisDeviceActiveMic -> S.wireless.bannerRecLocalController
                        isRecording -> S.wireless.bannerRecPeerPrompt
                        isThisDeviceActiveMic -> S.wireless.bannerStandbyLocalController
                        else -> S.wireless.bannerStandbyPeerPrompt
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isClipping) cs.error else if (isThisDeviceActiveMic) cs.primary else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val friendlyLevelLabel = when {
                    isClipping -> "声音过响"
                    !isRecording -> "待机"
                    dbfs < -38f -> "声音偏轻"
                    else -> "音量适中"
                }
                Text(
                    text = friendlyLevelLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isClipping -> cs.error
                        !isRecording -> cs.onSurfaceVariant
                        dbfs < -38f -> cs.tertiary
                        else -> cs.primary
                    },
                    maxLines = 1,
                    softWrap = false
                )
            }

            LinearProgressIndicator(
                progress = { smoothProgress },
                color = if (isClipping) cs.error else cs.primary,
                trackColor = cs.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                val barCount = 36
                val gap = 4.dp.toPx()
                val barW = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
                val pts = waveform.takeLast(barCount)
                val offsetStart = barCount - pts.size
                for (i in 0 until barCount) {
                    val v = if (i >= offsetStart && isRecording) pts[i - offsetStart] else 0.06f
                    val h = (size.height * v.coerceIn(0.06f, 1f))
                    val x = i * (barW + gap)
                    val y = (size.height - h) / 2f
                    drawRoundRect(
                        color = if (v > 0.92f) cs.error else cs.primary.copy(alpha = if (isRecording) 0.85f else 0.25f),
                        topLeft = Offset(x, y),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerShorthandPane(
    snapshot: RemoteSessionSnapshot,
    currentWord: RemoteWordItem?,
    noteField: TextFieldValue,
    onNoteFieldChange: (TextFieldValue) -> Unit
) {
    var selectedSymbolTab by remember { mutableStateOf(0) }
    val tabTitles = S.recording.quickNoteTabs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = S.wireless.fieldNoteHeader,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = noteField,
            onValueChange = onNoteFieldChange,
            enabled = currentWord != null,
            placeholder = { Text(S.wireless.fieldNotePlaceholder) },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("controller_note_input")
        )

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
                0 -> snapshot.quickToneSymbols.forEach { sym ->
                    val displaySym = com.example.ui.theme.formatQuickNoteSymbolForDisplay(sym)
                    StudioFilterChip(
                        selected = false,
                        onClick = {
                            if (currentWord != null) {
                                onNoteFieldChange(insertSymbolAtFieldCursor(noteField, sym))
                            }
                        },
                        labelText = displaySym,
                        showCheckmark = false
                    )
                }
                1 -> snapshot.quickIpaInitials.forEach { sym ->
                    val displaySym = com.example.ui.theme.formatQuickNoteSymbolForDisplay(sym)
                    StudioFilterChip(
                        selected = false,
                        onClick = {
                            if (currentWord != null) {
                                onNoteFieldChange(insertSymbolAtFieldCursor(noteField, sym))
                            }
                        },
                        labelText = displaySym,
                        showCheckmark = false
                    )
                }
                2 -> snapshot.quickIpaFinals.forEach { sym ->
                    val displaySym = com.example.ui.theme.formatQuickNoteSymbolForDisplay(sym)
                    StudioFilterChip(
                        selected = false,
                        onClick = {
                            if (currentWord != null) {
                                onNoteFieldChange(insertSymbolAtFieldCursor(noteField, sym))
                            }
                        },
                        labelText = displaySym,
                        showCheckmark = false
                    )
                }
                else -> snapshot.quickFieldTags.forEach { tag ->
                    val hasTag = hasQuickNoteTag(noteField.text, tag)
                    StudioFilterChip(
                        selected = hasTag,
                        onClick = {
                            if (currentWord != null) {
                                onNoteFieldChange(toggleQuickNoteTagInValue(noteField, tag))
                            }
                        },
                        labelText = tag,
                        showCheckmark = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerWordListPane(
    snapshot: RemoteSessionSnapshot,
    onOpenProjectSwitcher: () -> Unit,
    onToggleShuffle: () -> Unit = {},
    onSelectSeqPos: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (snapshot.projectId > 0L) {
                    S.wireless.drawerCatalogCount.withArgs(
                        "recorded" to snapshot.recordedCount,
                        "total" to snapshot.totalWords
                    )
                } else {
                    S.wireless.drawerCatalogTitle
                },
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = cs.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (snapshot.projectId > 0L && snapshot.totalWords > 1) {
                    val shuffleInteraction = remember { MutableInteractionSource() }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (snapshot.isShuffleEnabled) cs.primaryContainer else cs.surfaceContainerHigh,
                        modifier = Modifier
                            .studioTactile(shuffleInteraction, pressScale = 0.95f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = shuffleInteraction,
                                indication = null,
                                onClick = onToggleShuffle
                            )
                            .testTag("controller_wordlist_shuffle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = S.recording.toggleShuffleDesc,
                                tint = if (snapshot.isShuffleEnabled) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (snapshot.isShuffleEnabled) S.recording.shuffledLabel else S.recording.shuffleLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (snapshot.isShuffleEnabled) cs.onPrimaryContainer else cs.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                val changeListInteraction = remember { MutableInteractionSource() }
                TextButton(
                    onClick = onOpenProjectSwitcher,
                    interactionSource = changeListInteraction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.studioTactile(changeListInteraction, pressScale = 0.96f)
                ) {
                    Text(S.wireless.changeWordlistBtn, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }

        if (snapshot.projectId <= 0L) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = S.wireless.drawerNoProjectSelected,
                    fontSize = 12.5.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(snapshot.words, key = { "${it.seqPos}_${it.wordId}" }) { item ->
                    val isCurrent = item.seqPos == snapshot.currentSeqPos
                    val rowInteraction = remember { MutableInteractionSource() }
                    val isRowHovered by rowInteraction.collectIsHoveredAsState()
                    val rowBg by animateColorAsState(
                        targetValue = when {
                            isCurrent -> lerp(cs.surfaceContainerLow, cs.primaryContainer, if (isRowHovered) 0.65f else 0.55f)
                            isRowHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.28f)
                            else -> cs.surfaceContainerLow
                        },
                        animationSpec = tween(130),
                        label = "WordPaneRowBg"
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = rowBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .studioTactile(rowInteraction, pressScale = 0.98f)
                            .clip(RoundedCornerShape(12.dp))
                            .hoverable(rowInteraction)
                            .clickable(
                                interactionSource = rowInteraction,
                                indication = null
                            ) { onSelectSeqPos(item.seqPos) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.weight(1f).padding(end = 6.dp)
                            ) {
                                if (item.imageToken.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = S.wireless.drawerHasImageDesc,
                                        tint = cs.primary.copy(alpha = 0.85f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = S.wireless.seqWordItemFormat.withArgs(
                                        "pos" to item.seqPos + 1,
                                        "word" to item.text
                                    ),
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (item.isRecorded) {
                                Icon(
                                    imageVector = if (item.qualityStatus != null && item.qualityStatus != "正常") {
                                        Icons.Default.Flag
                                    } else {
                                        Icons.Default.CheckCircle
                                    },
                                    contentDescription = S.wireless.drawerRecordedDesc,
                                    tint = if (item.qualityStatus != null && item.qualityStatus != "正常") {
                                        cs.error
                                    } else {
                                        cs.primary
                                    },
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
