package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShufflingMode
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.ActiveMicOwner
import com.example.link.LinkRole
import com.example.link.PromptAudioCoachingMode
import com.example.link.PromptDisplayPolicy
import com.example.link.PromptPageTurnPolicy
import com.example.link.PromptRecordingIndicatorStyle
import com.example.link.PromptVisualPreset
import com.example.link.WirelessSessionCapsuleSummary
import com.example.link.WirelessStorageMode
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioRadioRow
import com.example.ui.components.studioTactile
import com.example.viewmodel.RecordingViewModel
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings detail page & reusable policy configurator for Multi-Device Wireless Collaboration ("无线互联").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiDeviceLinkCategoryContent(
    viewModel: RecordingViewModel,
    onOpenPairingDialog: () -> Unit,
    showLaunchCard: Boolean = true
) {
    val context = LocalContext.current
    val wireless = viewModel.wirelessLink
    val policy by wireless.promptPolicy.collectAsState()
    val isConnected by wireless.isConnected.collectAsState()
    val isProjectHost by wireless.isProjectHost.collectAsState()
    val snapshot by wireless.sessionSnapshot.collectAsState()
    val isWaiting by wireless.isHostingWaiting.collectAsState()
    val activeRole by wireless.activeRole.collectAsState()
    val activeMicOwner by wireless.activeMicOwner.collectAsState()
    val peerName by wireless.peerDeviceName.collectAsState()
    val pendingSyncCount by wireless.pendingSyncCount.collectAsState()
    val capsulesRefreshVersion by wireless.capsulesRefreshVersion.collectAsState()
    val autoAdvanceHold by viewModel.autoAdvanceOnHoldRelease.collectAsState()
    val holdOnQuality by viewModel.holdOnQualityIssue.collectAsState()
    val localShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val localShufflingMode by viewModel.shufflingMode.collectAsState()
    val effectiveShuffleEnabled = if (isConnected && !isProjectHost) snapshot.isShuffleEnabled else localShuffleEnabled
    val effectiveShufflingMode = if (isConnected && !isProjectHost) snapshot.shufflingMode else localShufflingMode.name

    var localCapsuleReloadTick by remember { mutableIntStateOf(0) }
    var capsules by remember { mutableStateOf<List<WirelessSessionCapsuleSummary>>(emptyList()) }
    var capsulePendingExport by remember { mutableStateOf<WirelessSessionCapsuleSummary?>(null) }
    var capsulePendingDelete by remember { mutableStateOf<WirelessSessionCapsuleSummary?>(null) }

    LaunchedEffect(capsulesRefreshVersion, localCapsuleReloadTick) {
        capsules = viewModel.listWirelessSessionCapsules()
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { targetUri ->
        val targetCapsule = capsulePendingExport
        capsulePendingExport = null
        if (targetUri != null && targetCapsule != null) {
            viewModel.exportWirelessCapsuleToUri(targetCapsule.capsuleDir, targetUri) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showLaunchCard) {
        LinkConnectionOverviewCard(
            isConnected = isConnected,
            isWaiting = isWaiting,
            activeRole = activeRole,
            activeMicOwner = activeMicOwner,
            storageMode = policy.storageMode,
            peerName = peerName,
            onOpenPairingDialog = onOpenPairingDialog,
            onDisconnect = { wireless.cancelOrDisconnect() }
        )
    }

    // 1. Recording Backup Switch & Local Backups
    SettingsGroupCard(title = S.wireless.sectionStorageLocation) {
        val isDualBackup = policy.storageMode == WirelessStorageMode.DUAL_FULL_MIRROR
        SettingsSwitchRow(
            title = S.wireless.storageBothFullTitle,
            subtitle = if (isDualBackup) S.wireless.storageBothFullSub else S.wireless.storageHostOnlySub,
            checked = isDualBackup,
            onCheckedChange = { enabled ->
                val nextMode = if (enabled) {
                    WirelessStorageMode.DUAL_FULL_MIRROR
                } else {
                    WirelessStorageMode.HOST_ONLY
                }
                wireless.updatePromptPolicy { it.copy(storageMode = nextMode) }
            }
        )

        if (!isConnected && pendingSyncCount > 0) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = S.wireless.pendingSyncBanner.withArgs("count" to pendingSyncCount),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = S.wireless.exportOrImportCapsuleHint,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    val rescueInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = {
                            viewModel.rescueWirelessPendingToCapsule { count ->
                                localCapsuleReloadTick++
                                Toast.makeText(
                                    context,
                                    if (count > 0) {
                                        S.wireless.savedCountToLocalToast.withArgs("count" to count)
                                    } else {
                                        S.wireless.noPendingToSaveToast
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        interactionSource = rescueInteraction,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .studioTactile(rescueInteraction, pressScale = 0.96f)
                            .testTag("rescue_pending_capsule_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(S.wireless.saveToLocalButton, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        if (capsules.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = S.wireless.localCapsulesHeader.withArgs("count" to capsules.size),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                capsules.forEach { cap ->
                    WirelessCapsuleItemCard(
                        capsule = cap,
                        onExportZip = {
                            capsulePendingExport = cap
                            val safeName = S.wireless.capsuleZipFilename
                                .withArgs("project" to cap.projectName, "speaker" to cap.speakerName)
                                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            exportZipLauncher.launch(safeName)
                        },
                        onImportAsProject = {
                            viewModel.importWirelessCapsuleAsProject(cap.capsuleDir) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        onDelete = {
                            capsulePendingDelete = cap
                        }
                    )
                }
            }
        }
    }

    // 2. Rhythm & Hold-Release Auto-Advance
    SettingsGroupCard(title = S.wireless.sectionAutoAdvance) {
        SettingsSwitchRow(
            title = S.wireless.holdReleaseJumpTitle,
            subtitle = S.wireless.holdReleaseJumpSub,
            checked = autoAdvanceHold && policy.autoAdvanceOnHoldRelease,
            onCheckedChange = { enabled ->
                viewModel.setAutoAdvanceOnHoldRelease(enabled)
            }
        )

        SettingsSwitchRow(
            title = S.wireless.holdOnQualityIssueTitle,
            subtitle = S.wireless.holdOnQualityIssueSub,
            checked = holdOnQuality && policy.holdOnQualityIssueWhenAutoAdvance,
            onCheckedChange = { enabled ->
                viewModel.setHoldOnQualityIssue(enabled)
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SettingsSwitchRow(
            title = S.settings.shuffleTitle,
            subtitle = S.settings.shuffleSub,
            checked = effectiveShuffleEnabled,
            onCheckedChange = { enabled ->
                wireless.requestSetShuffleEnabled(enabled)
                Toast.makeText(
                    context,
                    if (enabled) S.recording.shuffleEnabledToast else S.recording.shuffleRestoredToast,
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        AnimatedVisibility(
            visible = effectiveShuffleEnabled,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shuffleOptions = listOf(
                    ShufflingMode.RANDOM_ALL.name to S.settings.shuffleRandomAll,
                    ShufflingMode.BLOCK_BALANCED.name to S.settings.shuffleBlockBalanced
                )
                shuffleOptions.forEach { (modeName, label) ->
                    StudioFilterChip(
                        selected = effectiveShufflingMode == modeName,
                        onClick = { wireless.requestSetShufflingMode(modeName) },
                        labelText = label,
                        modifier = Modifier.testTag("wireless_shuffle_mode_${modeName.lowercase()}")
                    )
                }
                StudioFilterChip(
                    selected = false,
                    onClick = {
                        wireless.requestReshuffle()
                        Toast.makeText(context, S.recording.reshuffledToast, Toast.LENGTH_SHORT).show()
                    },
                    labelText = S.recording.reshuffleLabel,
                    showCheckmark = false,
                    leadingIcon = {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    modifier = Modifier.testTag("wireless_reshuffle_chip")
                )
            }
        }
    }

    // 3. Friendly Visual Presets for 看读端
    SettingsGroupCard(title = S.wireless.sectionPromptAppearance) {
        PromptVisualPresetGrid(
            selectedPreset = policy.visualPreset,
            onSelectPreset = { preset ->
                wireless.updatePromptPolicy { it.copy(visualPreset = preset) }
            }
        )
    }

    // 4. Speaker Interaction Mode & Display Size (2 clear choices + collapsible details)
    var showMorePromptDetails by remember { mutableStateOf(false) }
    val isControllerOnlyMode = policy.pageTurnPolicy == PromptPageTurnPolicy.LOCKED_BY_CONTROLLER &&
        !policy.allowPromptStartStopRecord &&
        !policy.allowPromptReplayAndRetake

    SettingsGroupCard(title = S.wireless.sectionPromptPermissions) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioRadioRow(
                selected = isControllerOnlyMode,
                label = "发音人只看字，由我统一遥控（推荐，防误触）",
                onClick = {
                    wireless.updatePromptPolicy {
                        it.copy(
                            pageTurnPolicy = PromptPageTurnPolicy.LOCKED_BY_CONTROLLER,
                            allowPromptStartStopRecord = false,
                            allowPromptReplayAndRetake = false,
                            immersiveExitLock = true
                        )
                    }
                },
                modifier = Modifier.testTag("policy_turn_LOCKED_BY_CONTROLLER")
            )
            StudioRadioRow(
                selected = !isControllerOnlyMode,
                label = "允许发音人自己在屏幕上翻页",
                onClick = {
                    wireless.updatePromptPolicy {
                        it.copy(
                            pageTurnPolicy = PromptPageTurnPolicy.FREE_PREV_NEXT
                        )
                    }
                },
                modifier = Modifier.testTag("policy_turn_FREE_PREV_NEXT")
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.wireless.fontScaleLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${(policy.fontScaleMultiplier * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }
            Slider(
                value = policy.fontScaleMultiplier,
                onValueChange = { v ->
                    wireless.updatePromptPolicy { it.copy(fontScaleMultiplier = v.coerceIn(1.0f, 2.4f)) }
                },
                valueRange = 1.0f..2.4f,
                steps = 13,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("policy_font_scale_slider")
            )
        }

        SettingsSwitchRow(
            title = S.wireless.hideTextWithImageTitle,
            subtitle = S.wireless.hideTextWithImageSub,
            checked = policy.hideTextWhenImagePresent,
            onCheckedChange = { enabled ->
                wireless.updatePromptPolicy {
                    it.copy(
                        hideTextWhenImagePresent = enabled,
                        toggleTextTemporaryForCurrentWord = false
                    )
                }
            }
        )

        TextButton(
            onClick = { showMorePromptDetails = !showMorePromptDetails },
            modifier = Modifier.testTag("toggle_more_prompt_details_button")
        ) {
            Text(
                text = if (showMorePromptDetails) "收起更多看读细节设置" else "展开更多看读细节设置（释义 / 进度 / 自助录音）",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(
            visible = showMorePromptDetails,
            enter = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSwitchRow(
                    title = S.wireless.allowPromptRecordTitle,
                    subtitle = S.wireless.allowPromptRecordSub,
                    checked = policy.allowPromptStartStopRecord,
                    onCheckedChange = { enabled ->
                        wireless.updatePromptPolicy { it.copy(allowPromptStartStopRecord = enabled) }
                    }
                )

                SettingsSwitchRow(
                    title = S.wireless.allowPromptPlaybackTitle,
                    subtitle = S.wireless.allowPromptPlaybackSub,
                    checked = policy.allowPromptReplayAndRetake,
                    onCheckedChange = { enabled ->
                        wireless.updatePromptPolicy { it.copy(allowPromptReplayAndRetake = enabled) }
                    }
                )

                SettingsSwitchRow(
                    title = S.wireless.longPressExitTitle,
                    subtitle = S.wireless.longPressExitSub,
                    checked = policy.immersiveExitLock,
                    onCheckedChange = { enabled ->
                        wireless.updatePromptPolicy { it.copy(immersiveExitLock = enabled) }
                    }
                )

                SettingsSwitchRow(
                    title = S.wireless.showCarrierTitle,
                    subtitle = S.wireless.showCarrierSub,
                    checked = policy.showCarrierSentence,
                    onCheckedChange = { enabled ->
                        wireless.updatePromptPolicy { it.copy(showCarrierSentence = enabled) }
                    }
                )

                SettingsSwitchRow(
                    title = S.wireless.alwaysShowRemarkTitle,
                    subtitle = S.wireless.alwaysShowRemarkSub,
                    checked = policy.showRemarkGlobally,
                    onCheckedChange = { enabled ->
                        wireless.updatePromptPolicy { it.copy(showRemarkGlobally = enabled) }
                    }
                )

                SettingsSwitchRow(
                    title = S.wireless.showProgressNumberTitle,
                    subtitle = S.wireless.showProgressNumberSub,
                    checked = policy.showProgressCounter,
                    onCheckedChange = { enabled ->
                        wireless.updatePromptPolicy { it.copy(showProgressCounter = enabled) }
                    }
                )

                Text(
                    text = S.wireless.audioCoachingHeader,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PromptAudioCoachingMode.entries.forEach { mode ->
                        StudioRadioRow(
                            selected = policy.audioCoachingMode == mode,
                            label = "${mode.title} · ${mode.subtitle}",
                            onClick = {
                                wireless.updatePromptPolicy { it.copy(audioCoachingMode = mode) }
                            },
                            modifier = Modifier.testTag("policy_coaching_${mode.name}")
                        )
                    }
                }

                Text(
                    text = S.wireless.recordingIndicatorHeader,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PromptRecordingIndicatorStyle.entries.forEach { style ->
                        StudioRadioRow(
                            selected = policy.recordingIndicatorStyle == style,
                            label = "${style.title} · ${style.subtitle}",
                            onClick = {
                                wireless.updatePromptPolicy { it.copy(recordingIndicatorStyle = style) }
                            },
                            modifier = Modifier.testTag("policy_rec_style_${style.name}")
                        )
                    }
                }
            }
        }
    }

    com.example.ui.components.AnimatedNullableDialogVisibility(value = capsulePendingDelete) { targetCap ->
        com.example.ui.components.StudioAlertDialog(
            onDismissRequest = { capsulePendingDelete = null },
            title = { Text(S.wireless.deleteCapsuleDialogTitle) },
            text = {
                Text(
                    S.wireless.deleteCapsuleDialogBody.withArgs(
                        "project" to targetCap.projectName,
                        "speaker" to targetCap.speakerName,
                        "count" to targetCap.recordedTakeCount
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dir = targetCap.capsuleDir
                        capsulePendingDelete = null
                        viewModel.deleteWirelessCapsule(dir) {
                            localCapsuleReloadTick++
                            Toast.makeText(context, S.wireless.deletedToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(S.common.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { capsulePendingDelete = null }) {
                    Text(S.common.cancel)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WirelessCapsuleItemCard(
    capsule: WirelessSessionCapsuleSummary,
    onExportZip: () -> Unit,
    onImportAsProject: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sizeMb = String.format(Locale.US, "%.1f MB", capsule.totalBytes / (1024.0 * 1024.0))
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceContainerLowest,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wireless_capsule_${capsule.folderName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "《${capsule.projectName}》 · ${capsule.speakerName}",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = S.wireless.capsuleTakeMeta.withArgs("count" to capsule.recordedTakeCount, "size" to sizeMb),
                    fontSize = 11.5.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val exportInteraction = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = onExportZip,
                    interactionSource = exportInteraction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .studioTactile(exportInteraction, pressScale = 0.96f)
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(S.wireless.exportZipButton, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                }
                val importInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onImportAsProject,
                    interactionSource = importInteraction,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .studioTactile(importInteraction, pressScale = 0.96f)
                ) {
                    Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(S.wireless.importToProjectButton, fontSize = 12.5.sp, maxLines = 1, softWrap = false)
                }
                val deleteInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onDelete,
                    interactionSource = deleteInteraction,
                    modifier = Modifier
                        .size(48.dp)
                        .studioTactile(deleteInteraction, pressScale = 0.90f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = S.common.delete,
                        tint = cs.error
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkConnectionOverviewCard(
    isConnected: Boolean,
    isWaiting: Boolean,
    activeRole: LinkRole,
    activeMicOwner: ActiveMicOwner,
    storageMode: WirelessStorageMode,
    peerName: String,
    onOpenPairingDialog: () -> Unit,
    onDisconnect: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isLocalMic = (activeRole == LinkRole.PROMPT_DISPLAY && activeMicOwner == ActiveMicOwner.PROMPT_DISPLAY_MIC) ||
        (activeRole == LinkRole.CONTROLLER && activeMicOwner == ActiveMicOwner.CONTROLLER_MIC)
    val micDesc = if (isLocalMic) S.wireless.localMicDesc else S.wireless.peerMicDesc

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isConnected) {
            lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.36f)
        } else {
            cs.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (isConnected) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isConnected) cs.primary else cs.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Link else Icons.Default.Devices,
                            contentDescription = null,
                            tint = if (isConnected) cs.onPrimary else cs.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = when {
                            isConnected -> S.wireless.statusConnectedLine.withArgs("peer" to peerName, "role" to activeRole.label)
                            isWaiting -> S.wireless.statusWaitingLine
                            else -> S.wireless.statusDisconnectedLine
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = cs.onSurface
                    )
                    Text(
                        text = if (isConnected) {
                            "$micDesc · ${storageMode.shortBadge}"
                        } else {
                            S.wireless.settingsLinkIntro
                        },
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val openLinkInteraction = remember { MutableInteractionSource() }
                Button(
                    onClick = onOpenPairingDialog,
                    interactionSource = openLinkInteraction,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .studioTactile(openLinkInteraction, pressScale = 0.97f)
                        .testTag("settings_open_wireless_link_button")
                ) {
                    Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected || isWaiting) S.wireless.btnStatusAndSwitch else S.wireless.btnStartLink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (isConnected || isWaiting) {
                    val disconnectInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onDisconnect,
                        interactionSource = disconnectInteraction,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .studioTactile(disconnectInteraction, pressScale = 0.96f)
                            .testTag("settings_disconnect_wireless_button")
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(S.wireless.btnDisconnect, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
fun PromptVisualPresetGrid(
    selectedPreset: PromptVisualPreset,
    onSelectPreset: (PromptVisualPreset) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PromptVisualPreset.entries.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowPresets.forEach { preset ->
                    val isSelected = selectedPreset == preset
                    val (previewBg, previewCardBg, previewFg, accentCol) = rememberPromptPresetSwatch(preset)
                    val presetInteraction = remember { MutableInteractionSource() }
                    val isHovered by presetInteraction.collectIsHoveredAsState()
                    val presetBg by animateColorAsState(
                        targetValue = when {
                            isSelected -> lerp(cs.surfaceContainerLow, cs.primaryContainer, if (isHovered) 0.45f else 0.35f)
                            isHovered -> lerp(cs.surfaceContainerLowest, cs.primaryContainer, 0.22f)
                            else -> cs.surfaceContainerLowest
                        },
                        animationSpec = tween(160),
                        label = "PresetCardBg"
                    )
                    val presetBorder by animateColorAsState(
                        targetValue = when {
                            isSelected -> cs.primary
                            isHovered -> cs.primary.copy(alpha = 0.55f)
                            else -> cs.outlineVariant.copy(alpha = 0.5f)
                        },
                        animationSpec = tween(160),
                        label = "PresetCardBorder"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = presetBg,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = presetBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .studioTactile(presetInteraction, pressScale = 0.975f, hoverScale = 1.015f)
                            .clip(RoundedCornerShape(16.dp))
                            .hoverable(presetInteraction)
                            .clickable(
                                interactionSource = presetInteraction,
                                indication = null
                            ) { onSelectPreset(preset) }
                            .testTag("preset_card_${preset.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(previewBg)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(previewCardBg)
                                        .border(
                                            width = 1.dp,
                                            color = accentCol.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(7.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = S.wireless.previewSampleChar,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = previewFg
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = preset.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = scaleIn(tween(150)) + fadeIn(tween(130)),
                                    exit = scaleOut(tween(120)) + fadeOut(tween(100))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = S.wireless.currentPresetDesc,
                                        tint = cs.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = preset.subtitle,
                                fontSize = 11.5.sp,
                                lineHeight = 15.5.sp,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberPromptPresetSwatch(preset: PromptVisualPreset): PromptPresetColors {
    val cs = MaterialTheme.colorScheme
    return when (preset) {
        PromptVisualPreset.MATERIAL_YOU -> PromptPresetColors(
            screenBg = cs.surfaceContainerLowest,
            cardBg = cs.surfaceContainerLow,
            primaryText = cs.onSurface,
            secondaryText = cs.onSurfaceVariant,
            accent = cs.primary,
            halo = cs.primaryContainer
        )
        PromptVisualPreset.WARM_PAPER -> PromptPresetColors(
            screenBg = Color(0xFFFAF6EE),
            cardBg = Color(0xFFF3ECE0),
            primaryText = Color(0xFF231F1B),
            secondaryText = Color(0xFF6B5E50),
            accent = Color(0xFF9E5328),
            halo = Color(0xFFE7D3BE)
        )
        PromptVisualPreset.INK_EBOOK -> PromptPresetColors(
            screenBg = Color(0xFFEFEFEA),
            cardBg = Color(0xFFF7F7F3),
            primaryText = Color(0xFF161616),
            secondaryText = Color(0xFF52524E),
            accent = Color(0xFF2B2B29),
            halo = Color(0xFFD5D5CE)
        )
        PromptVisualPreset.HIGH_CONTRAST_BOARD -> PromptPresetColors(
            screenBg = Color(0xFF121918),
            cardBg = Color(0xFF1B2523),
            primaryText = Color(0xFFF6F6F0),
            secondaryText = Color(0xFFB7C7C3),
            accent = Color(0xFF6EE7B7),
            halo = Color(0xFF234B3E)
        )
    }
}

internal data class PromptPresetColors(
    val screenBg: Color,
    val cardBg: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val halo: Color
)

fun formatPromptPolicySummary(
    policy: PromptDisplayPolicy,
    isConnected: Boolean,
    activeRole: LinkRole,
    peerName: String
): String {
    if (isConnected) {
        return S.wireless.summaryConnected.withArgs("peer" to peerName, "role" to activeRole.label)
    }
    return S.wireless.summaryStandby.withArgs("preset" to policy.visualPreset.title)
}
