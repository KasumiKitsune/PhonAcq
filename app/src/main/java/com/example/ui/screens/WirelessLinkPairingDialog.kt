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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
 * Material You dialog for pairing two PhonAcq devices over LAN Wi-Fi or personal mobile hotspot.
 * Supports both hosting ("发起联动") and joining ("接入联动") with auto-discovery + 6-digit PIN.
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
    val pairingPin by wireless.pairingPin.collectAsState()
    val localIps by wireless.localIpAddresses.collectAsState()
    val hostingPort by wireless.hostingTcpPort.collectAsState()
    val discoveredPeers by wireless.discoveredPeers.collectAsState()
    val activeRole by wireless.activeRole.collectAsState()
    val promptPolicy by wireless.promptPolicy.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()

    var selectedTab by remember { mutableIntStateOf(if (currentProject != null) 0 else 1) }
    var chosenHostRole by remember { mutableStateOf(LinkRole.CONTROLLER) }
    var chosenMicOwner by remember { mutableStateOf(ActiveMicOwner.PROMPT_DISPLAY_MIC) }

    var selectedPeer by remember { mutableStateOf<DiscoveredPeer?>(null) }
    var joinPinInput by remember { mutableStateOf("") }
    var showManualIp by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf(WirelessLinkTransport.DEFAULT_TCP_PORT.toString()) }
    var joinErrorText by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        wireless.startDiscoveryScan()
        onDispose {
            wireless.stopDiscoveryScan()
        }
    }

    // Auto-close pairing dialog once handshake succeeds so user lands right in 遥控端 or 看读端
    LaunchedEffect(isConnected) {
        if (isConnected) {
            onDismiss()
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
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 740.dp)
                    .testTag("wireless_pairing_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
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

                    // Segmented Mode Tabs: 发起互联 vs 接入互联
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
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
                            PairingTabButton(
                                title = S.wireless.modeHostTitle,
                                subtitle = S.wireless.modeHostSubtitle,
                                icon = Icons.Default.WifiTethering,
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pair_tab_host")
                            )
                            PairingTabButton(
                                title = S.wireless.modeJoinTitle,
                                subtitle = S.wireless.modeJoinSubtitle,
                                icon = Icons.Default.SettingsRemote,
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pair_tab_join")
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    AnimatedContent(
                        targetState = Pair(selectedTab, isHostingWaiting),
                        transitionSpec = {
                            val dir = when {
                                targetState.first != initialState.first -> if (targetState.first > initialState.first) 1 else -1
                                targetState.second != initialState.second -> if (targetState.second) 1 else -1
                                else -> 1
                            }
                            (fadeIn(tween(190, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(tween(210, easing = FastOutSlowInEasing)) { w -> dir * (w / 14) }) togetherWith
                                (fadeOut(tween(120)) +
                                    slideOutHorizontally(tween(140)) { w -> -dir * (w / 18) })
                        },
                        label = "PairingBodyTransition",
                        modifier = Modifier.weight(1f, fill = false)
                    ) { (tabIdx, waitingNow) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (tabIdx == 0) {
                                // ==================== TAB 0: HOST SESSION ====================
                                if (currentProject == null && !waitingNow) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = S.wireless.hostNoProjectNotice,
                                            fontSize = 12.sp,
                                            lineHeight = 17.5.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }

                                if (!waitingNow) {
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
                                        selected = chosenHostRole == LinkRole.CONTROLLER,
                                        onClick = { chosenHostRole = LinkRole.CONTROLLER },
                                        tag = "pair_role_controller"
                                    )
                                    RoleOptionCard(
                                        title = S.wireless.hostRolePromptTitle,
                                        subtitle = S.wireless.hostRolePromptSub,
                                        icon = Icons.Default.TabletAndroid,
                                        selected = chosenHostRole == LinkRole.PROMPT_DISPLAY,
                                        onClick = { chosenHostRole = LinkRole.PROMPT_DISPLAY },
                                        tag = "pair_role_prompt_display"
                                    )

                                    Text(
                                        text = S.wireless.hostStep2Title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    RoleOptionCard(
                                        title = ActiveMicOwner.PROMPT_DISPLAY_MIC.label + S.wireless.recommendedSuffix,
                                        subtitle = ActiveMicOwner.PROMPT_DISPLAY_MIC.description,
                                        icon = Icons.Default.Mic,
                                        selected = chosenMicOwner == ActiveMicOwner.PROMPT_DISPLAY_MIC,
                                        onClick = { chosenMicOwner = ActiveMicOwner.PROMPT_DISPLAY_MIC },
                                        tag = "pair_mic_prompt"
                                    )
                                    RoleOptionCard(
                                        title = ActiveMicOwner.CONTROLLER_MIC.label,
                                        subtitle = ActiveMicOwner.CONTROLLER_MIC.description,
                                        icon = Icons.Default.Mic,
                                        selected = chosenMicOwner == ActiveMicOwner.CONTROLLER_MIC,
                                        onClick = { chosenMicOwner = ActiveMicOwner.CONTROLLER_MIC },
                                        tag = "pair_mic_controller"
                                    )

                                    Text(
                                        text = S.wireless.hostStep3Title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        WirelessStorageMode.entries.forEach { mode ->
                                            StudioFilterChip(
                                                selected = promptPolicy.storageMode == mode,
                                                onClick = {
                                                    wireless.updatePromptPolicy { it.copy(storageMode = mode) }
                                                },
                                                labelText = mode.shortBadge,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("pair_storage_${mode.name}")
                                            )
                                        }
                                    }
                                    AnimatedContent(
                                        targetState = promptPolicy.storageMode,
                                        transitionSpec = {
                                            fadeIn(tween(150)) togetherWith fadeOut(tween(110))
                                        },
                                        label = "PairingStorageDescTransition"
                                    ) { selectedStorage ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${selectedStorage.title}：${selectedStorage.subtitle}",
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }

                                    // Clean full-width CTA stack — never wraps button labels vertically!
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val startHostInteraction = remember { MutableInteractionSource() }
                                        Button(
                                            onClick = {
                                                wireless.startHostingSession(
                                                    hostRole = chosenHostRole,
                                                    initialMicOwner = chosenMicOwner
                                                )
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
                                                text = S.wireless.generatePinButton,
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
                                    // Waiting for peer to connect with 6-digit PIN
                                    val pulseTransition = rememberInfiniteTransition(label = "WaitingPinPulse")
                                    val pulseScale by pulseTransition.animateFloat(
                                        initialValue = 0.85f,
                                        targetValue = 1.25f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(900, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "WaitingPinDotScale"
                                    )
                                    val pulseAlpha by pulseTransition.animateFloat(
                                        initialValue = 0.45f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(900, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "WaitingPinDotAlpha"
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
                                                    text = S.wireless.pinGuideText,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            Text(
                                                text = pairingPin.chunked(3).joinToString(" "),
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 3.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.testTag("pair_pin_code_text")
                                            )
                                            val ipLine = if (localIps.isNotEmpty()) {
                                                S.wireless.manualConnectIpFormat.withArgs(
                                                    "ips" to localIps.joinToString(" / "),
                                                    "port" to hostingPort
                                                )
                                            } else {
                                                S.wireless.sameWifiHint
                                            }
                                            Text(
                                                text = ipLine,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = S.wireless.hostActiveSummaryLine.withArgs(
                                                    "role" to activeRole.label,
                                                    "mic" to chosenMicOwner.label,
                                                    "storage" to promptPolicy.storageMode.shortBadge
                                                ),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                            val cancelHostInteraction = remember { MutableInteractionSource() }
                                            OutlinedButton(
                                                onClick = { wireless.cancelOrDisconnect() },
                                                interactionSource = cancelHostInteraction,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .defaultMinSize(minHeight = 46.dp)
                                                    .studioTactile(cancelHostInteraction, pressScale = 0.96f)
                                                    .testTag("pair_cancel_hosting_button")
                                            ) {
                                                Text(S.wireless.cancelWaitButton, maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // ==================== TAB 1: JOIN SESSION ====================
                                Text(
                                    text = S.wireless.joinStep1Title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                if (discoveredPeers.isEmpty() && !showManualIp) {
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
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(15.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Row(
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
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = S.wireless.manualIpFallbackHint,
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    discoveredPeers.forEach { peer ->
                                        val isSel = selectedPeer?.hostId == peer.hostId && !showManualIp
                                        val peerInteraction = remember { MutableInteractionSource() }
                                        val isPeerHovered by peerInteraction.collectIsHoveredAsState()
                                        val peerBg by animateColorAsState(
                                            targetValue = when {
                                                isSel -> lerp(
                                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    if (isPeerHovered) 0.52f else 0.42f
                                                )
                                                isPeerHovered -> lerp(
                                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    0.22f
                                                )
                                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                                            },
                                            animationSpec = tween(150),
                                            label = "DiscoveredPeerBg"
                                        )
                                        val peerBorder by animateColorAsState(
                                            targetValue = when {
                                                isSel -> MaterialTheme.colorScheme.primary
                                                isPeerHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            },
                                            animationSpec = tween(150),
                                            label = "DiscoveredPeerBorder"
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = peerBg,
                                            border = BorderStroke(
                                                width = if (isSel) 2.dp else 1.dp,
                                                color = peerBorder
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .studioTactile(peerInteraction, pressScale = 0.985f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .hoverable(peerInteraction)
                                                .clickable(
                                                    interactionSource = peerInteraction,
                                                    indication = null
                                                ) {
                                                    selectedPeer = peer
                                                    showManualIp = false
                                                }
                                                .testTag("discovered_peer_${peer.hostId}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Text(
                                                        text = "${peer.deviceName} · 《${peer.projectName}》",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = S.wireless.peerExpectedRoleLine.withArgs("role" to peer.peerExpectedRole.label),
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                AnimatedVisibility(
                                                    visible = isSel,
                                                    enter = scaleIn(tween(150)) + fadeIn(tween(130)),
                                                    exit = scaleOut(tween(120)) + fadeOut(tween(100))
                                                ) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = S.wireless.selectedItemDesc,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val manualIpToggleInteraction = remember { MutableInteractionSource() }
                                TextButton(
                                    onClick = { showManualIp = !showManualIp },
                                    interactionSource = manualIpToggleInteraction,
                                    modifier = Modifier
                                        .studioTactile(manualIpToggleInteraction, pressScale = 0.96f)
                                        .testTag("pair_manual_ip_toggle")
                                ) {
                                    Text(if (showManualIp) S.wireless.collapseManualIpButton else S.wireless.expandManualIpButton)
                                }

                                AnimatedVisibility(
                                    visible = showManualIp,
                                    enter = expandVertically(tween(180)) + fadeIn(tween(150)),
                                    exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = manualIpInput,
                                            onValueChange = { manualIpInput = it.trim() },
                                            label = { Text(S.wireless.peerIpLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("pair_manual_ip_input")
                                        )
                                        OutlinedTextField(
                                            value = manualPortInput,
                                            onValueChange = { manualPortInput = it.filter(Char::isDigit) },
                                            label = { Text(S.wireless.portLabel) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .width(96.dp)
                                                .testTag("pair_manual_port_input")
                                        )
                                    }
                                }

                                Text(
                                    text = S.wireless.joinStep2Title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                OutlinedTextField(
                                    value = joinPinInput,
                                    onValueChange = { raw ->
                                        joinPinInput = raw.filter(Char::isDigit).take(6)
                                        joinErrorText = null
                                    },
                                    placeholder = { Text(S.wireless.pinInputPlaceholder) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pair_join_pin_input")
                                )

                                if (joinErrorText != null) {
                                    Text(
                                        text = joinErrorText!!,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                val targetPeer = selectedPeer ?: discoveredPeers.firstOrNull()
                                val targetIp = if (showManualIp) manualIpInput else (targetPeer?.ipAddress ?: "")
                                val targetPort = if (showManualIp) {
                                    manualPortInput.toIntOrNull() ?: WirelessLinkTransport.DEFAULT_TCP_PORT
                                } else {
                                    targetPeer?.tcpPort ?: WirelessLinkTransport.DEFAULT_TCP_PORT
                                }

                                val connectInteraction = remember { MutableInteractionSource() }
                                val canConnect = !isConnecting && joinPinInput.length == 6
                                Button(
                                    onClick = {
                                        if (targetIp.isBlank()) {
                                            joinErrorText = S.wireless.errSelectDeviceOrIp
                                            return@Button
                                        }
                                        if (joinPinInput.length != 6) {
                                            joinErrorText = S.wireless.errEnter6DigitPin
                                            return@Button
                                        }
                                        isConnecting = true
                                        joinErrorText = null
                                        scope.launch {
                                            val result = wireless.joinPeerSession(targetIp, targetPort, joinPinInput)
                                            isConnecting = false
                                            result.onFailure { err ->
                                                joinErrorText = err.message ?: S.wireless.errConnectionFailedFallback
                                            }
                                        }
                                    },
                                    enabled = canConnect,
                                    interactionSource = connectInteraction,
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 50.dp)
                                        .studioTactile(connectInteraction, enabled = canConnect, pressScale = 0.975f)
                                        .testTag("pair_connect_button")
                                ) {
                                    Text(
                                        text = if (isConnecting) S.wireless.connectingButton else S.wireless.connectButton,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Helpful offline hotspot tip
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
private fun PairingTabButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> cs.primaryContainer
            isHovered -> lerp(cs.surfaceContainerLow, cs.primaryContainer, 0.25f)
            else -> cs.surfaceContainerLow
        },
        animationSpec = tween(160),
        label = "PairTabBg"
    )
    val fgColor by animateColorAsState(
        targetValue = when {
            selected -> cs.onPrimaryContainer
            isHovered -> cs.primary
            else -> cs.onSurfaceVariant
        },
        animationSpec = tween(160),
        label = "PairTabFg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> cs.primary.copy(alpha = 0.45f)
            isHovered -> cs.primary.copy(alpha = 0.30f)
            else -> cs.outlineVariant.copy(alpha = 0f)
        },
        animationSpec = tween(160),
        label = "PairTabBorder"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .studioTactile(interactionSource, pressScale = 0.975f)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected || isHovered) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = fgColor,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = fgColor.copy(alpha = 0.78f),
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
