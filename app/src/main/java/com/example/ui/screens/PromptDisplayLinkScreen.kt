package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.core.content.ContextCompat
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.*
import com.example.ui.components.ActiveMicEdgeGlowOverlay
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.MicEndpointStatusBadge
import com.example.ui.components.ZoomableWordImage
import com.example.ui.components.studioTactile
import com.example.utils.rememberWordImageBitmap
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.delay

/**
 * Full-screen distraction-free 看读端 (Prompt Display) placed in front of the speaker.
 * Supports Material You + Warm Xuan-Paper / E-Ink / High-Contrast Chalkboard friendly presets,
 * intermission break ("中场休息"), permission-gated controls, and 3-second exit lock.
 */
@Composable
fun PromptDisplayLinkScreen(
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
    val activeMicOwner by wireless.activeMicOwner.collectAsState()
    val isHostingWaiting by wireless.isHostingWaiting.collectAsState()
    val isAuditionPlaying by wireless.isRemoteAuditionPlaying.collectAsState()
    val imageCacheVersion by wireless.imageCacheVersion.collectAsState()

    val colors = rememberPromptPresetSwatch(policy.visualPreset)
    val currentWord = snapshot.currentWord
    val isThisDeviceActiveMic = activeMicOwner == ActiveMicOwner.PROMPT_DISPLAY_MIC
    val isTextHiddenByPolicy = policy.hideTextWhenImagePresent xor policy.toggleTextTemporaryForCurrentWord

    // Auto-fading natural-language coaching prompt (4.5s gentle display after an abnormal take)
    val activeCoaching = snapshot.latestCoaching?.takeIf { fb ->
        policy.audioCoachingMode != PromptAudioCoachingMode.OFF &&
            !policy.isIntermissionBreak &&
            !snapshot.isRecording &&
            currentWord != null &&
            fb.wordId == currentWord.wordId &&
            !fb.speakerHint.isNullOrBlank()
    }
    var visibleCoachingText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeCoaching?.timestampMs, snapshot.isRecording, currentWord?.wordId, policy.audioCoachingMode) {
        if (activeCoaching == null) {
            visibleCoachingText = null
        } else {
            visibleCoachingText = activeCoaching.speakerHint
            delay(4600L)
            visibleCoachingText = null
        }
    }

    // Ensure RECORD_AUDIO permission is requested if 看读端近场拾音 is active
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
    LaunchedEffect(snapshot.activeMicOwner) {
        if (snapshot.activeMicOwner == ActiveMicOwner.PROMPT_DISPLAY_MIC && !hasMicPerm) {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var lockHintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(lockHintVisible) {
        if (lockHintVisible) {
            delay(2200L)
            lockHintVisible = false
        }
    }

    // Intercept accidental system back presses when 3-second exit lock is enabled
    BackHandler(enabled = true) {
        if (policy.immersiveExitLock) {
            lockHintVisible = true
        } else {
            onExitSession()
        }
    }

    // Gentle breathing halo animation during recording
    val infiniteTransition = rememberInfiniteTransition(label = "PromptBreathingHalo")
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    val cardShape = when (policy.visualPreset) {
        PromptVisualPreset.INK_EBOOK -> RoundedCornerShape(10.dp)
        PromptVisualPreset.WARM_PAPER -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(28.dp)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.screenBg)
            .testTag("prompt_display_screen_root")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
        // 1. Minimal Top Bar (Mic endpoint badge + optional progress counter + subtle 3s press-and-hold exit control)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isConnected && !policy.isIntermissionBreak) {
                    MicEndpointStatusBadge(
                        isThisDeviceActiveMic = isThisDeviceActiveMic,
                        isRecording = snapshot.isRecording,
                        peerRoleShortName = S.wireless.roleNameController,
                        activeContainerColor = colors.halo,
                        activeContentColor = colors.primaryText,
                        activeDotColor = colors.accent,
                        inactiveContainerColor = colors.cardBg,
                        inactiveContentColor = colors.secondaryText,
                        testTag = "prompt_mic_endpoint_badge"
                    )
                }

                if (policy.showProgressCounter && snapshot.totalWords > 0 && !policy.isIntermissionBreak) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.cardBg,
                        border = BorderStroke(1.dp, colors.secondaryText.copy(alpha = 0.22f)),
                        modifier = Modifier.testTag("prompt_progress_badge")
                    ) {
                        Text(
                            text = "${snapshot.currentSeqPos + 1} / ${snapshot.totalWords}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.secondaryText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                if (currentWord?.imageToken?.isNotBlank() == true &&
                    !policy.isIntermissionBreak &&
                    !policy.immersiveExitLock
                ) {
                    val toggleTextInteraction = remember { MutableInteractionSource() }
                    val isToggleHovered by toggleTextInteraction.collectIsHoveredAsState()
                    val toggleChipBg by animateColorAsState(
                        targetValue = when {
                            isTextHiddenByPolicy -> colors.halo
                            isToggleHovered -> lerp(colors.cardBg, colors.halo, 0.45f)
                            else -> colors.cardBg
                        },
                        animationSpec = tween(150),
                        label = "PromptToggleChipBg"
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = toggleChipBg,
                        border = BorderStroke(
                            1.dp,
                            if (isTextHiddenByPolicy || isToggleHovered) colors.accent.copy(alpha = 0.55f)
                            else colors.secondaryText.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .studioTactile(toggleTextInteraction, pressScale = 0.96f)
                            .clip(RoundedCornerShape(999.dp))
                            .hoverable(toggleTextInteraction)
                            .clickable(
                                interactionSource = toggleTextInteraction,
                                indication = null
                            ) {
                                wireless.updatePromptPolicy { p ->
                                    val currentlyHidden = p.hideTextWhenImagePresent xor p.toggleTextTemporaryForCurrentWord
                                    p.copy(
                                        hideTextWhenImagePresent = !currentlyHidden,
                                        toggleTextTemporaryForCurrentWord = false
                                    )
                                }
                            }
                            .testTag("prompt_toggle_word_text_chip")
                    ) {
                        Text(
                            text = if (isTextHiddenByPolicy) S.wireless.toggleTextHiddenShow else S.wireless.toggleTextHiddenHide,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isTextHiddenByPolicy) colors.primaryText else colors.secondaryText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                if (snapshot.isRecording &&
                    policy.recordingIndicatorStyle == PromptRecordingIndicatorStyle.MINIMAL_TEXT &&
                    !policy.isIntermissionBreak
                ) {
                    Text(
                        text = S.wireless.minimalIndicatorText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent,
                        modifier = Modifier.testTag("prompt_minimal_recording_cue")
                    )
                }
            }

            PromptExitControlButton(
                locked = policy.immersiveExitLock,
                fgColor = colors.secondaryText,
                accentColor = colors.accent,
                onTapWhenLocked = { lockHintVisible = true },
                onConfirmedExit = onExitSession
            )
        }

        // Gentle toast hint when speaker or investigator taps the locked exit icon
        AnimatedVisibility(
            visible = lockHintVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.cardBg,
                border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f))
            ) {
                Text(
                    text = S.wireless.holdLockToExitHint,
                    fontSize = 13.sp,
                    color = colors.primaryText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // 2. Center Main Stage: Waiting PIN Card OR Intermission Break ("中场休息") OR Empty Project OR Prompt Word Card
        val stageModeKey = when {
            isHostingWaiting && !isConnected -> "WAITING_PIN"
            policy.isIntermissionBreak -> "INTERMISSION"
            !isConnected -> "DISCONNECTED"
            !snapshot.hasSnapshotArrived -> "CONNECTING_SYNC"
            snapshot.projectId <= 0L -> "NO_PROJECT_SELECTED"
            currentWord == null -> "EMPTY_WORDLIST"
            else -> "WORD_CARD"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 900.dp)
                .align(Alignment.Center)
                .padding(vertical = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = stageModeKey,
                transitionSpec = {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(150))
                },
                label = "PromptMainStageTransition"
            ) { mode ->
                when (mode) {
                    "WAITING_PIN" -> {
                        Surface(
                            shape = cardShape,
                            color = colors.cardBg,
                            border = BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prompt_waiting_pin_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = S.wireless.waitingControllerTitle,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = S.wireless.waitingControllerPinHint,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = S.wireless.waitingControllerWifiHint,
                                    fontSize = 13.sp,
                                    color = colors.secondaryText.copy(alpha = 0.78f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    "INTERMISSION" -> {
                        Surface(
                            shape = cardShape,
                            color = colors.cardBg,
                            border = BorderStroke(1.5.dp, colors.accent.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prompt_intermission_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 52.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.halo.copy(alpha = 0.65f),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LocalCafe,
                                            contentDescription = S.wireless.intermissionIconDesc,
                                            tint = colors.accent,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = S.wireless.intermissionTitle,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.secondaryText
                                )
                                Text(
                                    text = policy.intermissionMessage,
                                    fontSize = (28f * policy.fontScaleMultiplier.coerceAtMost(1.5f)).sp,
                                    lineHeight = (38f * policy.fontScaleMultiplier.coerceAtMost(1.5f)).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    "DISCONNECTED" -> {
                        Text(
                            text = S.wireless.linkBrokenStatus,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }

                    "CONNECTING_SYNC" -> {
                        Text(
                            text = S.wireless.linkConnectingStatus,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }

                    "NO_PROJECT_SELECTED" -> {
                        Surface(
                            shape = cardShape,
                            color = colors.cardBg,
                            border = BorderStroke(1.2.dp, colors.secondaryText.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prompt_no_project_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = S.wireless.noWordlistTitle,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = S.wireless.noWordlistPromptSub,
                                    fontSize = 15.sp,
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    "EMPTY_WORDLIST" -> {
                        Surface(
                            shape = cardShape,
                            color = colors.cardBg,
                            border = BorderStroke(1.2.dp, colors.secondaryText.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = S.wireless.emptyWordlistTitle,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText
                                )
                                Text(
                                    text = S.wireless.emptyWordlistPromptSub,
                                    fontSize = 14.5.sp,
                                    color = colors.secondaryText
                                )
                            }
                        }
                    }

                    else -> {
                        val safeWord = currentWord ?: return@AnimatedContent
                        val showHalo = snapshot.isRecording &&
                            policy.recordingIndicatorStyle == PromptRecordingIndicatorStyle.SOFT_HALO
                        val borderWidth = if (showHalo) 4.dp else 1.5.dp
                        val borderColor = when {
                            showHalo -> colors.accent.copy(alpha = haloAlpha)
                            policy.visualPreset == PromptVisualPreset.INK_EBOOK -> colors.accent.copy(alpha = 0.65f)
                            policy.visualPreset == PromptVisualPreset.WARM_PAPER -> Color(0xFFD3C3AC)
                            else -> colors.secondaryText.copy(alpha = 0.22f)
                        }

                        val fontFamily = when (policy.visualPreset) {
                            PromptVisualPreset.WARM_PAPER, PromptVisualPreset.INK_EBOOK -> FontFamily.Serif
                            else -> FontFamily.Default
                        }

                        val charCount = safeWord.text.length
                    val baseFontSp = when {
                        charCount <= 2 -> 64f
                        charCount <= 4 -> 52f
                        charCount <= 7 -> 40f
                        else -> 32f
                    }
                    val wordFontSp = (baseFontSp * policy.fontScaleMultiplier).coerceIn(30f, 132f)

                    Surface(
                        shape = cardShape,
                        color = colors.cardBg,
                        border = BorderStroke(borderWidth, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 290.dp)
                            .then(
                                if (policy.visualPreset == PromptVisualPreset.WARM_PAPER) {
                                    Modifier.padding(2.dp)
                                } else Modifier
                            )
                            .testTag("prompt_word_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (policy.visualPreset == PromptVisualPreset.WARM_PAPER) {
                                        Modifier
                                            .padding(6.dp)
                                            .border(
                                                0.8.dp,
                                                Color(0xFFD8C8B0).copy(alpha = 0.7f),
                                                RoundedCornerShape(12.dp)
                                            )
                                    } else Modifier
                                )
                                .padding(horizontal = 28.dp, vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = safeWord,
                                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                                label = "PromptWordChange"
                            ) { wordItem ->
                                val wordImgFile = remember(
                                    snapshot.projectId,
                                    wordItem.wordId,
                                    wordItem.imageToken,
                                    imageCacheVersion
                                ) {
                                    wireless.resolveRemoteWordImageFile(snapshot.projectId, wordItem)
                                }
                                val wordBitmap by rememberWordImageBitmap(
                                    file = wordImgFile,
                                    maxEdgePx = 1280,
                                    extraKey = imageCacheVersion
                                )
                                val bmp = wordBitmap
                                val hasImage = bmp != null
                                // Safety fallback: never hide text if this word has no loaded image
                                val hideTextForImage = hasImage && isTextHiddenByPolicy
                                val effectiveWordFontSp = if (hasImage) {
                                    wordFontSp.coerceAtMost(56f)
                                } else {
                                    wordFontSp
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    if (bmp != null) {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = colors.screenBg.copy(alpha = 0.55f),
                                            border = BorderStroke(1.dp, colors.secondaryText.copy(alpha = 0.18f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(
                                                    min = 180.dp,
                                                    max = if (hideTextForImage) 370.dp else 265.dp
                                                )
                                                .testTag("prompt_word_image_box")
                                        ) {
                                            ZoomableWordImage(
                                                bitmap = bmp,
                                                contentDescription = wordItem.text,
                                                resetKey = "${snapshot.projectId}_${wordItem.wordId}_${wordItem.imageToken}_$imageCacheVersion",
                                                cornerRadius = 12.dp,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp)
                                            )
                                        }
                                    } else if (wordItem.imageToken.isNotBlank()) {
                                        Text(
                                            text = S.wireless.syncingWordImageHint,
                                            fontSize = 13.sp,
                                            color = colors.secondaryText
                                        )
                                    }

                                    // Optional Carrier Sentence
                                    if (!hideTextForImage && policy.showCarrierSentence && snapshot.carrierSentenceEnabled) {
                                        val filled = if (snapshot.carrierSentenceTemplate.contains("___")) {
                                            snapshot.carrierSentenceTemplate.replace("___", "「${wordItem.text}」")
                                        } else {
                                            "${snapshot.carrierSentenceTemplate} ${wordItem.text}"
                                        }
                                        Text(
                                            text = filled,
                                            fontSize = (18f * policy.fontScaleMultiplier.coerceAtMost(1.45f)).sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.secondaryText,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.testTag("prompt_carrier_sentence_text")
                                        )
                                    }

                                    // Target Word
                                    if (!hideTextForImage) {
                                        Text(
                                            text = wordItem.text,
                                            fontSize = effectiveWordFontSp.sp,
                                            lineHeight = (effectiveWordFontSp * 1.2f).sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = fontFamily,
                                            color = colors.primaryText,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.testTag("prompt_main_word_text")
                                        )
                                    }

                                    // Optional Remark / Gloss (Global OR Single-Word Temporary Reveal from Controller)
                                    val shouldShowRemark = (policy.showRemarkGlobally || policy.revealRemarkTemporaryForCurrentWord) &&
                                        wordItem.remark.isNotBlank()
                                    if (shouldShowRemark) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colors.halo.copy(alpha = 0.5f),
                                            modifier = Modifier.testTag("prompt_remark_chip")
                                        ) {
                                            Text(
                                                text = wordItem.remark,
                                                fontSize = (17f * policy.fontScaleMultiplier.coerceAtMost(1.4f)).sp,
                                                lineHeight = (24f * policy.fontScaleMultiplier.coerceAtMost(1.4f)).sp,
                                                color = colors.primaryText.copy(alpha = 0.88f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                            )
                                        }
                                    }

                                    if (showHalo) {
                                        Text(
                                            text = if (hideTextForImage) {
                                                S.wireless.activeMicSpeakPictureHint
                                            } else {
                                                S.wireless.activeMicSpeakWordHint
                                            },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.accent,
                                            modifier = Modifier.testTag("prompt_soft_halo_caption")
                                        )
                                    }

                                    // Optional Gentle Natural-Language Coaching Hint (Zero engineering jargon or red alarm!)
                                    AnimatedVisibility(
                                        visible = visibleCoachingText != null && !snapshot.isRecording,
                                        enter = fadeIn(tween(240)),
                                        exit = fadeOut(tween(320))
                                    ) {
                                        val hintMsg = visibleCoachingText.orEmpty()
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = colors.halo.copy(alpha = 0.72f),
                                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .testTag("prompt_natural_coaching_banner")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.RecordVoiceOver,
                                                    contentDescription = null,
                                                    tint = colors.accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = hintMsg,
                                                    fontSize = (15.5f * policy.fontScaleMultiplier.coerceIn(1.0f, 1.35f)).sp,
                                                    lineHeight = (22f * policy.fontScaleMultiplier.coerceIn(1.0f, 1.35f)).sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.primaryText,
                                                    textAlign = TextAlign.Center
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
            }
        }

        // 3. Permission-Gated Bottom Controls (Zero buttons shown when locked by Controller!)
        if (!policy.isIntermissionBreak && currentWord != null) {
            val showPrev = policy.pageTurnPolicy == PromptPageTurnPolicy.FREE_PREV_NEXT
            val showNext = policy.pageTurnPolicy == PromptPageTurnPolicy.NEXT_ONLY ||
                policy.pageTurnPolicy == PromptPageTurnPolicy.FREE_PREV_NEXT
            val showRecord = policy.allowPromptStartStopRecord
            val showReplay = policy.allowPromptReplayAndRetake && currentWord.isRecorded

            if (showPrev || showNext || showRecord || showReplay) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showPrev) {
                        PromptSoftActionButton(
                            label = S.wireless.btnPrevWord,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            colors = colors,
                            enabled = snapshot.currentSeqPos > 0,
                            onClick = { wireless.requestPrevWord() },
                            tag = "prompt_btn_prev"
                        )
                    }
                    if (showReplay) {
                        PromptSoftActionButton(
                            label = if (isAuditionPlaying) S.wireless.btnStopAudition else S.wireless.btnAudition,
                            icon = if (isAuditionPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            colors = colors,
                            onClick = { wireless.requestAuditionCurrentWordOnThisDevice() },
                            tag = "prompt_btn_replay"
                        )
                    }
                    if (showRecord) {
                        com.example.ui.components.PromptRecordTriggerButton(
                            isRecording = snapshot.isRecording,
                            accentColor = colors.accent,
                            screenBgColor = colors.screenBg,
                            haloColor = colors.halo,
                            onStartRecord = { wireless.requestStartRecording() },
                            onStopRecord = {
                                wireless.requestStopRecording(
                                    fromHoldRelease = false,
                                    initiatedByRole = LinkRole.PROMPT_DISPLAY
                                )
                            },
                            onStopFromHoldRelease = {
                                wireless.requestStopRecording(
                                    fromHoldRelease = true,
                                    initiatedByRole = LinkRole.PROMPT_DISPLAY
                                )
                            },
                            onToggleRecord = {
                                wireless.requestToggleRecording(initiatedByRole = LinkRole.PROMPT_DISPLAY)
                            },
                            tag = "prompt_btn_record"
                        )
                    }
                    if (showNext) {
                        PromptSoftActionButton(
                            label = S.wireless.btnNextWord,
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            colors = colors,
                            enabled = snapshot.currentSeqPos < snapshot.totalWords - 1,
                            onClick = { wireless.requestNextWord() },
                            tag = "prompt_btn_next"
                        )
                    }
                }
            }
        }
        }

        // 4. Full-Screen Perimeter Edge-Glow + Toast when this 看读端 is the Active Microphone Endpoint
        ActiveMicEdgeGlowOverlay(
            isConnected = isConnected,
            connectionEpoch = connectionEpoch,
            isThisDeviceActiveMic = isThisDeviceActiveMic,
            thisRoleLabel = S.wireless.roleNamePrompt,
            peerRoleLabel = S.wireless.roleNameController,
            glowColor = colors.accent,
            eInkCrispMode = policy.visualPreset == PromptVisualPreset.INK_EBOOK
        )
    }
}

@Composable
private fun PromptSoftActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: PromptPresetColors,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onClick: () -> Unit,
    tag: String
) {
    val bg = if (emphasized) colors.accent else colors.cardBg
    val fg = if (emphasized) colors.screenBg else colors.primaryText
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = colors.cardBg.copy(alpha = 0.45f),
            disabledContentColor = colors.secondaryText.copy(alpha = 0.4f)
        ),
        border = if (!emphasized) BorderStroke(1.dp, colors.secondaryText.copy(alpha = 0.28f)) else null,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        modifier = Modifier
            .defaultMinSize(minHeight = 54.dp)
            .studioTactile(interactionSource, enabled = enabled, pressScale = 0.96f)
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Anti-accidental-touch exit button:
 * - If [locked] is false: single tap exits immediately.
 * - If [locked] is true: requires continuous 3-second press-and-hold with animated ring.
 */
@Composable
private fun PromptExitControlButton(
    locked: Boolean,
    fgColor: Color,
    accentColor: Color,
    onTapWhenLocked: () -> Unit,
    onConfirmedExit: () -> Unit
) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val exitBg by animateColorAsState(
        targetValue = when {
            isHolding -> accentColor.copy(alpha = 0.16f)
            isHovered -> fgColor.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "PromptExitHoverBg"
    )
    val exitScale by animateFloatAsState(
        targetValue = if (isHolding) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 600f),
        label = "PromptExitScale"
    )

    LaunchedEffect(isHolding, locked) {
        if (!locked || !isHolding) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        while (isHolding) {
            val elapsed = System.currentTimeMillis() - startMs
            holdProgress = (elapsed / 3000f).coerceIn(0f, 1f)
            if (holdProgress >= 1f) {
                isHolding = false
                onConfirmedExit()
                break
            }
            delay(30L)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = exitScale
                scaleY = exitScale
            }
            .clip(CircleShape)
            .background(exitBg, CircleShape)
            .hoverable(interactionSource)
            .then(
                if (DesktopAdaptiveState.isDesktopPlatform) Modifier.pointerHoverIcon(PointerIcon.Hand)
                else Modifier
            )
            .pointerInput(locked) {
                if (!locked) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            val released = tryAwaitRelease()
                            isHolding = false
                            if (released) onConfirmedExit()
                        }
                    )
                } else {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            val releasedBeforeDone = tryAwaitRelease()
                            if (releasedBeforeDone && holdProgress < 1f) {
                                onTapWhenLocked()
                            }
                            isHolding = false
                        }
                    )
                }
            }
            .testTag("prompt_exit_hold_button")
    ) {
        if (locked && holdProgress > 0.02f) {
            CircularProgressIndicator(
                progress = { holdProgress },
                strokeWidth = 3.dp,
                color = accentColor,
                modifier = Modifier.size(42.dp)
            )
        }
        Icon(
            imageVector = if (locked) Icons.Default.Lock else Icons.Default.Close,
            contentDescription = if (locked) S.wireless.lockExitLongPressDesc else S.wireless.exitPromptModeDesc,
            tint = if (isHovered) accentColor else fgColor.copy(alpha = 0.65f),
            modifier = Modifier.size(20.dp)
        )
    }
}
