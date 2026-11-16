package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.ActiveMicOwner
import com.example.link.DiscoveredPeer
import com.example.link.LinkRole
import com.example.link.WirelessLinkTransport
import com.example.link.WirelessStorageMode
import com.example.ui.components.StudioDialogEnterAnimation
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.studioTactile
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.launch

/**
 * Symmetric, zero-PIN 1-tap wireless pairing dialog for connecting two PhonAcq devices
 * over the same Wi-Fi or personal mobile hotspot.
 */
@Composable
fun WirelessLinkPairingDialog(
    viewModel: RecordingViewModel,
    onDismiss: () -> Unit,
    onOpenLinkSettings: (() -> Unit)? = null
) {
    val wireless = viewModel.wirelessLink
    val scope = rememberCoroutineScope()

    val isHostingWaiting by wireless.isHostingWaiting.collectAsState()
    val isConnected by wireless.isConnected.collectAsState()
    val localIps by wireless.localIpAddresses.collectAsState()
    val discoveredPeers by wireless.discoveredPeers.collectAsState()
    val activeRole by wireless.activeRole.collectAsState()
    val promptPolicy by wireless.promptPolicy.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()

    val localHasProjects = currentProject != null || allProjects.isNotEmpty()

    var chosenRole by remember {
        mutableStateOf(
            when {
                activeRole != LinkRole.STANDALONE -> activeRole
                localHasProjects -> LinkRole.CONTROLLER
                else -> LinkRole.PROMPT_DISPLAY
            }
        )
    }
    var chosenMicOwner by remember { mutableStateOf(ActiveMicOwner.PROMPT_DISPLAY_MIC) }

    var showManualIp by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    var joinErrorText by remember { mutableStateOf<String?>(null) }
    var connectingPeerId by remember { mutableStateOf<String?>(null) }
    var isManualConnecting by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        wireless.startDiscoveryScan()
        onDispose {
            wireless.stopDiscoveryScan()
        }
    }

    // Auto-close pairing dialog once connected so user lands directly in 遥控端 or 看读端
    LaunchedEffect(isConnected) {
        if (isConnected) {
            onDismiss()
        }
    }

    fun connectToPeerOneTap(peer: DiscoveredPeer) {
        if (connectingPeerId != null || isManualConnecting) return
        connectingPeerId = peer.hostId
        joinErrorText = null
        scope.launch {
            val result = wireless.connectToDiscoveredPeer(
                peer = peer,
                localHasProjects = localHasProjects,
                preferredLocalRole = chosenRole
            )
            connectingPeerId = null
            result.onFailure { err ->
                joinErrorText = err.message ?: S.wireless.errConnectionFailedFallback
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        StudioDialogEnterAnimation {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 740.dp)
                    .testTag("wireless_pairing_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Devices,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = S.wireless.pairingDialogTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = S.wireless.pairingDialogSubtitle,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        val closeInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = onDismiss,
                            interactionSource = closeInteraction,
                            modifier = Modifier
                                .studioTactile(closeInteraction, pressScale = 0.90f)
                                .testTag("pair_dialog_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = S.wireless.closePairingDesc)
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    AnimatedContent(
                        targetState = isHostingWaiting,
                        transitionSpec = {
                            fadeIn(tween(180, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(120))
                        },
                        label = "PairingWaitingStateTransition",
                        modifier = Modifier.weight(1f, fill = false)
                    ) { waitingNow ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Live Discovered Peers — always surfaced at the very top when found!
                            if (discoveredPeers.isNotEmpty()) {
                                Text(
                                    text = S.wireless.joinStep1Title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                discoveredPeers.forEach { peer ->
                                    val isThisPeerConnecting = connectingPeerId == peer.hostId
                                    DiscoveredPeerOneTapCard(
                                        peer = peer,
                                        isConnecting = isThisPeerConnecting,
                                        onConnect = { connectToPeerOneTap(peer) }
                                    )
                                }
                            }

                            if (!waitingNow) {
                                // 2. Choose what THIS device is used for
                                Text(
                                    text = S.wireless.hostStep1Title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                RoleOptionCard(
                                    title = S.wireless.hostRoleControllerTitle,
                                    subtitle = S.wireless.hostRoleControllerSub,
                                    icon = Icons.Default.SettingsRemote,
                                    selected = chosenRole == LinkRole.CONTROLLER,
                                    onClick = { chosenRole = LinkRole.CONTROLLER },
                                    tag = "pair_role_controller"
                                )
                                RoleOptionCard(
                                    title = S.wireless.hostRolePromptTitle,
                                    subtitle = S.wireless.hostRolePromptSub,
                                    icon = Icons.Default.TabletAndroid,
                                    selected = chosenRole == LinkRole.PROMPT_DISPLAY,
                                    onClick = { chosenRole = LinkRole.PROMPT_DISPLAY },
                                    tag = "pair_role_prompt_display"
                                )

                                // Compact Mic Selector Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    StudioFilterChip(
                                        selected = chosenMicOwner == ActiveMicOwner.PROMPT_DISPLAY_MIC,
                                        onClick = { chosenMicOwner = ActiveMicOwner.PROMPT_DISPLAY_MIC },
                                        labelText = ActiveMicOwner.PROMPT_DISPLAY_MIC.label + S.wireless.recommendedSuffix,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pair_mic_prompt")
                                    )
                                    StudioFilterChip(
                                        selected = chosenMicOwner == ActiveMicOwner.CONTROLLER_MIC,
                                        onClick = { chosenMicOwner = ActiveMicOwner.CONTROLLER_MIC },
                                        labelText = ActiveMicOwner.CONTROLLER_MIC.label,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pair_mic_controller")
                                    )
                                }

                                // Single Clear Switch: Dual-Device Full Backup
                                val isDualBackup = promptPolicy.storageMode == WirelessStorageMode.DUAL_FULL_MIRROR
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            val nextMode = if (isDualBackup) {
                                                WirelessStorageMode.HOST_ONLY
                                            } else {
                                                WirelessStorageMode.DUAL_FULL_MIRROR
                                            }
                                            wireless.updatePromptPolicy { it.copy(storageMode = nextMode) }
                                        }
                                        .testTag("pair_dual_backup_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = S.wireless.storageBothFullTitle,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isDualBackup) {
                                                    S.wireless.storageBothFullSub
                                                } else {
                                                    S.wireless.storageHostOnlySub
                                                },
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isDualBackup,
                                            onCheckedChange = { enabled ->
                                                val nextMode = if (enabled) {
                                                    WirelessStorageMode.DUAL_FULL_MIRROR
                                                } else {
                                                    WirelessStorageMode.HOST_ONLY
                                                }
                                                wireless.updatePromptPolicy { it.copy(storageMode = nextMode) }
                                            },
                                            modifier = Modifier.testTag("pair_dual_backup_switch")
                                        )
                                    }
                                }

                                // Subtle auto-scan indicator when no peer has appeared yet
                                if (discoveredPeers.isEmpty()) {
                                    val scanTransition = rememberInfiniteTransition(label = "PeerScanPulse")
                                    val scanAlpha by scanTransition.animateFloat(
                                        initialValue = 0.35f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(850, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "PeerScanDotAlpha"
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .graphicsLayer { alpha = scanAlpha }
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                            Text(
                                                text = S.wireless.searchingDevices,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Primary Waiting Button (for the first device opened)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val startHostInteraction = remember { MutableInteractionSource() }
                                    val primaryLabel = if (chosenRole == LinkRole.PROMPT_DISPLAY) {
                                        "进入看读画面等待连接"
                                    } else {
                                        "准备就绪，等待对面连接"
                                    }
                                    Button(
                                        onClick = {
                                            wireless.startHostingSession(
                                                hostRole = chosenRole,
                                                initialMicOwner = chosenMicOwner
                                            )
                                            if (chosenRole == LinkRole.PROMPT_DISPLAY) {
                                                onDismiss()
                                            }
                                        },
                                        interactionSource = startHostInteraction,
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 50.dp)
                                            .studioTactile(startHostInteraction, pressScale = 0.975f)
                                            .testTag("pair_start_hosting_button")
                                    ) {
                                        Icon(
                                            Icons.Default.WifiTethering,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = primaryLabel,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    if (onOpenLinkSettings != null) {
                                        val linkSettingsInteraction = remember { MutableInteractionSource() }
                                        FilledTonalButton(
                                            onClick = {
                                                onDismiss()
                                                onOpenLinkSettings()
                                            },
                                            interactionSource = linkSettingsInteraction,
                                            shape = RoundedCornerShape(14.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .defaultMinSize(minHeight = 46.dp)
                                                .studioTactile(linkSettingsInteraction, pressScale = 0.975f)
                                        ) {
                                            Icon(
                                                Icons.Default.Tune,
                                                contentDescription = null,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = S.wireless.moreLinkSettingsButton,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Calm, zero-PIN waiting card
                                val pulseTransition = rememberInfiniteTransition(label = "WaitingPeerPulse")
                                val pulseScale by pulseTransition.animateFloat(
                                    initialValue = 0.85f,
                                    targetValue = 1.25f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(900, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "WaitingPeerDotScale"
                                )
                                val pulseAlpha by pulseTransition.animateFloat(
                                    initialValue = 0.45f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(900, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "WaitingPeerDotAlpha"
                                )
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = lerp(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        MaterialTheme.colorScheme.primaryContainer,
                                        0.35f
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .graphicsLayer {
                                                        scaleX = pulseScale
                                                        scaleY = pulseScale
                                                        alpha = pulseAlpha
                                                    }
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                            Text(
                                                text = S.wireless.statusWaitingLine,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = S.wireless.pinGuideText,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = S.wireless.hostActiveSummaryLine.withArgs(
                                                "role" to activeRole.label,
                                                "mic" to chosenMicOwner.label
                                            ),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                        if (localIps.isNotEmpty()) {
                                            Text(
                                                text = S.wireless.manualConnectIpFormat.withArgs(
                                                    "ips" to localIps.joinToString(" / ")
                                                ),
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val enterScreenInteraction = remember { MutableInteractionSource() }
                                            FilledTonalButton(
                                                onClick = onDismiss,
                                                interactionSource = enterScreenInteraction,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .defaultMinSize(minHeight = 46.dp)
                                                    .studioTactile(enterScreenInteraction, pressScale = 0.96f)
                                            ) {
                                                Text("先进入${activeRole.label}", maxLines = 1, softWrap = false)
                                            }
                                            val cancelHostInteraction = remember { MutableInteractionSource() }
                                            OutlinedButton(
                                                onClick = { wireless.cancelOrDisconnect() },
                                                interactionSource = cancelHostInteraction,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 46.dp)
                                                    .studioTactile(cancelHostInteraction, pressScale = 0.96f)
                                                    .testTag("pair_cancel_hosting_button")
                                            ) {
                                                Text(S.wireless.cancelWaitButton, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                }
                            }

                            if (joinErrorText != null) {
                                Text(
                                    text = joinErrorText!!,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Collapsible single-field manual IP fallback (for campus Wi-Fi AP isolation)
                            val manualIpToggleInteraction = remember { MutableInteractionSource() }
                            TextButton(
                                onClick = { showManualIp = !showManualIp },
                                interactionSource = manualIpToggleInteraction,
                                modifier = Modifier
                                    .studioTactile(manualIpToggleInteraction, pressScale = 0.96f)
                                    .testTag("pair_manual_ip_toggle")
                            ) {
                                Text(
                                    text = if (showManualIp) S.wireless.collapseManualIpButton else S.wireless.expandManualIpButton,
                                    fontSize = 12.5.sp
                                )
                            }

                            AnimatedVisibility(
                                visible = showManualIp,
                                enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                                exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualIpInput,
                                        onValueChange = {
                                            manualIpInput = it.trim()
                                            joinErrorText = null
                                        },
                                        label = {
                                            Text(
                                                S.wireless.peerIpLabel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("pair_manual_ip_input")
                                    )
                                    val connectInteraction = remember { MutableInteractionSource() }
                                    val canManualConnect = !isManualConnecting && manualIpInput.isNotBlank()
                                    Button(
                                        onClick = {
                                            val raw = manualIpInput.trim()
                                            if (raw.isBlank()) {
                                                joinErrorText = S.wireless.errSelectDeviceOrIp
                                                return@Button
                                            }
                                            val hostPart = raw.substringBefore(':').trim()
                                            val portPart = raw.substringAfter(':', "")
                                                .toIntOrNull() ?: WirelessLinkTransport.DEFAULT_TCP_PORT
                                            isManualConnecting = true
                                            joinErrorText = null
                                            scope.launch {
                                                val result = wireless.joinPeerSession(hostPart, portPart, "")
                                                isManualConnecting = false
                                                result.onFailure { err ->
                                                    joinErrorText = err.message ?: S.wireless.errConnectionFailedFallback
                                                }
                                            }
                                        },
                                        enabled = canManualConnect,
                                        interactionSource = connectInteraction,
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 52.dp)
                                            .studioTactile(connectInteraction, enabled = canManualConnect, pressScale = 0.97f)
                                            .testTag("pair_connect_button")
                                    ) {
                                        Text(
                                            text = if (isManualConnecting) S.wireless.connectingButton else S.wireless.connectButton,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Helpful hotspot tip
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = S.wireless.hotspotNoDataTip,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
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
private fun DiscoveredPeerOneTapCard(
    peer: DiscoveredPeer,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val peerInteraction = remember { MutableInteractionSource() }
    val isHovered by peerInteraction.collectIsHoveredAsState()
    val cardBg by animateColorAsState(
        targetValue = lerp(
            cs.surfaceContainerLow,
            cs.primaryContainer,
            if (isHovered) 0.48f else 0.34f
        ),
        animationSpec = tween(150),
        label = "DiscoveredPeerOneTapBg"
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.5.dp, cs.primary.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .studioTactile(peerInteraction, pressScale = 0.985f)
            .clip(RoundedCornerShape(16.dp))
            .hoverable(peerInteraction)
            .clickable(
                interactionSource = peerInteraction,
                indication = null,
                enabled = !isConnecting,
                onClick = onConnect
            )
            .testTag("discovered_peer_${peer.hostId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = cs.primary,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val titleLine = if (peer.hasActiveProject && peer.projectName.isNotBlank()) {
                    "${peer.deviceName} · 《${peer.projectName}》"
                } else {
                    "${peer.deviceName}（${peer.hostRole.label}）"
                }
                Text(
                    text = titleLine,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = S.wireless.peerExpectedRoleLine.withArgs("role" to peer.peerExpectedRole.label),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            }
            Button(
                onClick = onConnect,
                enabled = !isConnecting,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
            ) {
                Text(
                    text = if (isConnecting) S.wireless.connectingButton else S.wireless.connectButton,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val cardBg by animateColorAsState(
        targetValue = when {
            selected -> lerp(cs.surfaceContainerLow, cs.primaryContainer, if (isHovered) 0.48f else 0.38f)
            isHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.22f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = tween(160),
        label = "RoleCardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> cs.primary
            isHovered -> cs.primary.copy(alpha = 0.48f)
            else -> cs.outlineVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(160),
        label = "RoleCardBorder"
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .studioTactile(interactionSource, pressScale = 0.985f)
            .clip(RoundedCornerShape(16.dp))
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) cs.primary else cs.secondaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) cs.onPrimary else cs.onSecondaryContainer,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 13.8.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.8.sp,
                    lineHeight = 16.5.sp,
                    color = cs.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(tween(150)) + fadeIn(tween(130)),
                exit = scaleOut(tween(120)) + fadeOut(tween(100))
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = S.wireless.selectedItemDesc,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
