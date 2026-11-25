package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.StudioMotion
import com.example.ui.components.StudioSidebarNavItem
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import com.example.viewmodel.RecordingViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RecordingViewModel,
    onBack: () -> Unit,
    onOpenRecordingsCleanup: () -> Unit,
    initialCategoryId: String? = null,
    categoryJumpSeq: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Core settings states
    val isSimpleMode by viewModel.isSimpleMode.collectAsState()
    val isAutoNext by viewModel.autoNextEnabled.collectAsState()
    val isPushToTalk by viewModel.pushToTalkEnabled.collectAsState()
    val autoPlayAfterRecord by viewModel.autoPlayAfterRecord.collectAsState()
    val volumeKeyControlEnabled by viewModel.volumeKeyControlEnabled.collectAsState()
    val volumeKeyAction by viewModel.volumeKeyAction.collectAsState()
    val confirmOverwrite by viewModel.confirmOverwriteRecording.collectAsState()

    val characterSize by viewModel.characterSize.collectAsState()
    val hideRemarkInCleanMode by viewModel.hideRemarkInCleanMode.collectAsState()
    val carrierSentenceEnabled by viewModel.carrierSentenceEnabled.collectAsState()
    val carrierSentenceTemplate by viewModel.carrierSentenceTemplate.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val shufflingMode by viewModel.shufflingMode.collectAsState()

    val exportNamingStyle by viewModel.exportNamingStyle.collectAsState()
    val exportFolderPerSpeaker by viewModel.exportFolderPerSpeaker.collectAsState()
    val textGridConfig by viewModel.textGridFormatConfig.collectAsState()
    val textGridVisualConfig by viewModel.textGridVisualConfig.collectAsState()

    val themeMode by viewModel.themeMode.collectAsState()
    val themePaletteId by viewModel.themePaletteId.collectAsState()
    val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()

    // Advanced acoustic & hardware states
    val vadThreshold by viewModel.vadThreshold.collectAsState()
    val silenceDelayMs by viewModel.silenceDelayMs.collectAsState()
    val lowVolumeThreshold by viewModel.lowVolumeThreshold.collectAsState()
    val creakySensitivity by viewModel.creakySensitivity.collectAsState()
    val sampleRateHz by viewModel.sampleRateHz.collectAsState()
    val audioSourceStrategy by viewModel.audioSourceStrategy.collectAsState()
    val preRollEnabled by viewModel.preRollEnabled.collectAsState()
    val maxRetainedTakes by viewModel.maxRetainedTakes.collectAsState()
    val quickToneSymbols by viewModel.quickToneSymbols.collectAsState()
    val quickIpaInitials by viewModel.quickIpaInitials.collectAsState()
    val quickIpaFinals by viewModel.quickIpaFinals.collectAsState()
    val quickFieldTags by viewModel.quickFieldTags.collectAsState()

    val promptPolicy by viewModel.wirelessLink.promptPolicy.collectAsState()
    val isWirelessConnected by viewModel.wirelessLink.isConnected.collectAsState()
    val activeLinkRole by viewModel.wirelessLink.activeRole.collectAsState()
    val peerDeviceName by viewModel.wirelessLink.peerDeviceName.collectAsState()
    var showPairingDialogInSettings by remember { mutableStateOf(false) }

    // Hierarchical navigation state:
    // - Mobile: null = Main Menu (主菜单), non-null = Secondary Page (二级页面)
    // - Desktop / Wide: Sidebar selection (侧栏) + Right Main Pane (主栏)
    var mobileActiveCategoryId by rememberSaveable { mutableStateOf<String?>(initialCategoryId) }
    var desktopSelectedCategoryId by rememberSaveable {
        mutableStateOf(initialCategoryId ?: SettingsCategory.INTERFACE_MODE.id)
    }

    LaunchedEffect(initialCategoryId, categoryJumpSeq) {
        if (!initialCategoryId.isNullOrBlank()) {
            mobileActiveCategoryId = initialCategoryId
            desktopSelectedCategoryId = initialCategoryId
        }
    }

    val visibleCategories = remember(isSimpleMode) {
        SettingsCategory.visibleCategories(isSimpleMode)
    }

    val mobileActiveCategory: SettingsCategory? = remember(mobileActiveCategoryId, visibleCategories) {
        mobileActiveCategoryId?.let { id -> visibleCategories.find { it.id == id } }
    }
    val desktopSelectedCategory: SettingsCategory = remember(desktopSelectedCategoryId, visibleCategories) {
        visibleCategories.find { it.id == desktopSelectedCategoryId } ?: SettingsCategory.INTERFACE_MODE
    }

    // Reset orphaned selection if user switches from Advanced -> Simple Mode while viewing an Advanced-only category
    LaunchedEffect(isSimpleMode) {
        if (mobileActiveCategoryId != null && visibleCategories.none { it.id == mobileActiveCategoryId }) {
            mobileActiveCategoryId = null
        }
        if (visibleCategories.none { it.id == desktopSelectedCategoryId }) {
            desktopSelectedCategoryId = SettingsCategory.INTERFACE_MODE.id
        }
    }

    var showResetConfirm by remember { mutableStateOf(false) }
    var isTextGridCustomInputActive by remember { mutableStateOf(false) }

    val storageSizeText by produceState(initialValue = S.settings.storageCalculating) {
        value = withContext(Dispatchers.IO) {
            val root = context.getExternalFilesDir(null)
            var totalBytes = 0L
            root?.walkTopDown()?.forEach { file ->
                if (file.isFile) totalBytes += file.length()
            }
            val mb = totalBytes / (1024.0 * 1024.0)
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    // Dynamic live summary strings for each category in both Mobile Main Menu and Desktop Sidebar
    fun summaryFor(category: SettingsCategory): String {
        return when (category) {
            SettingsCategory.INTERFACE_MODE -> if (isSimpleMode) {
                S.settings.modeSummarySimple
            } else {
                S.settings.modeSummaryAdv
            }

            SettingsCategory.RECORDING_CONTROL -> if (isSimpleMode) {
                buildString {
                    if (isPushToTalk) {
                        append(S.settings.simplePttSummary)
                    } else {
                        append(if (isAutoNext) S.settings.autoNextOnSummary else S.settings.manualNextSummary)
                    }
                    append(" · ")
                    append(if (autoPlayAfterRecord) S.settings.autoPlayOnSummary else S.settings.manualPlaySummary)
                    if (!DesktopAdaptiveState.isDesktopPlatform && volumeKeyControlEnabled) {
                        append(S.settings.volumeKeySuffix)
                    }
                }
            } else {
                buildString {
                    if (isPushToTalk) {
                        append(S.settings.simplePttSummary)
                    } else if (isAutoNext) {
                        append(
                            S.settings.simpleAutoDelaySummary.withArgs(
                                "sec" to String.format(Locale.US, "%.1f", silenceDelayMs / 1000f)
                            )
                        )
                    } else {
                        append(S.settings.simpleManualTapSummary)
                    }
                    append(S.settings.retainTakesSummary.withArgs("count" to maxRetainedTakes))
                    if (!DesktopAdaptiveState.isDesktopPlatform && volumeKeyControlEnabled) {
                        append(S.settings.volumeKeySuffix)
                    }
                }
            }

            SettingsCategory.DISPLAY_PROMPT -> {
                val sizeLabel = when (characterSize) {
                    "小" -> S.settings.sizeSmallLabel
                    "中" -> S.settings.sizeStandardLabel
                    "大" -> S.settings.sizeLargeLabel
                    else -> S.settings.sizeExtraLargeLabel
                }
                if (isSimpleMode) {
                    if (DesktopAdaptiveState.isDesktopPlatform) {
                        sizeLabel
                    } else {
                        S.settings.displaySummaryLine.withArgs(
                            "size" to sizeLabel,
                            "screenOn" to if (keepScreenOn) S.settings.screenOnTrue else S.settings.screenOnFalse
                        )
                    }
                } else {
                    buildString {
                        append(sizeLabel)
                        append(" · ")
                        append(if (carrierSentenceEnabled) S.settings.carrierEnabledSummary else S.settings.carrierDisabledSummary)
                        append(" · ")
                        append(if (isShuffleEnabled) S.settings.shuffleEnabledSummary else S.settings.shuffleDisabledSummary)
                    }
                }
            }

            SettingsCategory.MULTI_DEVICE_LINK ->
                formatPromptPolicySummary(
                    policy = promptPolicy,
                    isConnected = isWirelessConnected,
                    activeRole = activeLinkRole,
                    peerName = peerDeviceName
                )

            SettingsCategory.QUICK_NOTES ->
                S.settings.quickNotesSummaryLine.withArgs(
                    "symbols" to (quickToneSymbols.size + quickIpaInitials.size + quickIpaFinals.size),
                    "tags" to quickFieldTags.size
                )

            SettingsCategory.PRAAT_TEXTGRID ->
                "${textGridConfig.preset.title} · ${textGridConfig.shortSummaryLabel()}"

            SettingsCategory.AUDIO_HARDWARE -> {
                val rateLabel = when (sampleRateHz) {
                    48000 -> "48 kHz"
                    22050 -> "22.05 kHz"
                    else -> "44.1 kHz"
                }
                val srcLabel = when (audioSourceStrategy) {
                    "UNPROCESSED" -> S.settings.micUnprocessedShort
                    "MIC" -> S.settings.micDefaultShort
                    else -> S.settings.micCleanShort
                }
                "$rateLabel · $srcLabel"
            }

            SettingsCategory.EXPORT_STORAGE -> if (isSimpleMode) {
                S.settings.usedSpaceSummary.withArgs("size" to storageSizeText)
            } else {
                val namingShort = when (exportNamingStyle) {
                    "NAME_INDEX_WORD" -> S.settings.namingNameIndexWord
                    "INDEX_WORD" -> S.settings.namingIndexWord
                    else -> S.settings.namingCodeIndexWord
                }
                S.settings.exportSummaryLine.withArgs(
                    "naming" to namingShort,
                    "size" to storageSizeText
                )
            }

            SettingsCategory.APPEARANCE -> {
                val themeLabel = when (themeMode) {
                    "LIGHT" -> S.settings.themeLightLabel
                    "DARK" -> S.settings.themeDarkLabel
                    else -> S.settings.themeSystemLabel
                }
                if (DesktopAdaptiveState.isDesktopPlatform || !dynamicColor) {
                    "$themeLabel · ${S.settings.displayThemePalette(themePaletteId)}"
                } else {
                    "$themeLabel${S.settings.dynamicColorSuffix}"
                }
            }

            SettingsCategory.ABOUT ->
                "v${com.example.BuildConfig.VERSION_NAME.removePrefix("v")}"
        }
    }

    @Composable
    fun RenderCategoryDetailContent(category: SettingsCategory) {
        when (category) {
            SettingsCategory.INTERFACE_MODE -> {
                InterfaceModeCategoryContent(
                    isSimpleMode = isSimpleMode,
                    onSelectSimpleMode = { simple -> viewModel.setSimpleMode(simple) }
                )
            }

            SettingsCategory.RECORDING_CONTROL -> {
                RecordingControlCategoryContent(
                    viewModel = viewModel,
                    isSimpleMode = isSimpleMode,
                    isAutoNext = isAutoNext,
                    silenceDelayMs = silenceDelayMs,
                    isPushToTalk = isPushToTalk,
                    autoPlayAfterRecord = autoPlayAfterRecord,
                    volumeKeyControlEnabled = volumeKeyControlEnabled,
                    volumeKeyAction = volumeKeyAction,
                    confirmOverwrite = confirmOverwrite,
                    maxRetainedTakes = maxRetainedTakes
                )
            }

            SettingsCategory.DISPLAY_PROMPT -> {
                DisplayPromptCategoryContent(
                    viewModel = viewModel,
                    isSimpleMode = isSimpleMode,
                    characterSize = characterSize,
                    hideRemarkInCleanMode = hideRemarkInCleanMode,
                    carrierSentenceEnabled = carrierSentenceEnabled,
                    carrierSentenceTemplate = carrierSentenceTemplate,
                    isShuffleEnabled = isShuffleEnabled,
                    shufflingMode = shufflingMode,
                    keepScreenOn = keepScreenOn
                )
            }

            SettingsCategory.MULTI_DEVICE_LINK -> {
                MultiDeviceLinkCategoryContent(
                    viewModel = viewModel,
                    onOpenPairingDialog = { showPairingDialogInSettings = true }
                )
            }

            SettingsCategory.QUICK_NOTES -> {
                QuickNotesCategoryContent(
                    context = context,
                    toneSymbols = quickToneSymbols,
                    fieldTags = quickFieldTags,
                    onAddToneSymbol = { viewModel.addQuickToneSymbol(it) },
                    onRemoveToneSymbol = { viewModel.removeQuickToneSymbol(it) },
                    onAddFieldTag = { viewModel.addQuickFieldTag(it) },
                    onRemoveFieldTag = { viewModel.removeQuickFieldTag(it) },
                    onResetDefaults = { viewModel.resetAllQuickNotesToDefault() },
                    ipaInitials = quickIpaInitials,
                    ipaFinals = quickIpaFinals,
                    onAddIpaInitial = { viewModel.addQuickIpaInitial(it) },
                    onRemoveIpaInitial = { viewModel.removeQuickIpaInitial(it) },
                    onAddIpaFinal = { viewModel.addQuickIpaFinal(it) },
                    onRemoveIpaFinal = { viewModel.removeQuickIpaFinal(it) }
                )
            }

            SettingsCategory.PRAAT_TEXTGRID -> {
                PraatTextGridCategoryContent(
                    isSimpleMode = isSimpleMode,
                    textGridConfig = textGridConfig,
                    visualConfig = textGridVisualConfig,
                    onSelectPreset = { preset -> viewModel.selectTextGridPreset(preset) },
                    onUpdateConfig = { updated -> viewModel.setTextGridFormatConfig(updated) },
                    onUpdateVisualConfig = { updated -> viewModel.setTextGridVisualConfig(updated) },
                    onSwitchToAdvancedMode = { viewModel.setSimpleMode(false) },
                    onCustomInputActiveChange = { active -> isTextGridCustomInputActive = active }
                )
            }

            SettingsCategory.AUDIO_HARDWARE -> {
                AudioHardwareCategoryContent(
                    viewModel = viewModel,
                    vadThreshold = vadThreshold,
                    lowVolumeThreshold = lowVolumeThreshold,
                    creakySensitivity = creakySensitivity,
                    sampleRateHz = sampleRateHz,
                    audioSourceStrategy = audioSourceStrategy,
                    preRollEnabled = preRollEnabled
                )
            }

            SettingsCategory.EXPORT_STORAGE -> {
                ExportStorageCategoryContent(
                    context = context,
                    viewModel = viewModel,
                    isSimpleMode = isSimpleMode,
                    exportNamingStyle = exportNamingStyle,
                    exportFolderPerSpeaker = exportFolderPerSpeaker,
                    storageSizeText = storageSizeText,
                    onOpenRecordingsCleanup = onOpenRecordingsCleanup
                )
            }

            SettingsCategory.APPEARANCE -> {
                AppearanceCategoryContent(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    themePaletteId = themePaletteId,
                    dynamicColor = dynamicColor
                )
            }

            SettingsCategory.ABOUT -> {
                AboutCategoryContent()
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_root")
    ) {
        val isTwoPaneLayout = maxWidth >= 760.dp
        val sidebarWidth by animateDpAsState(
            targetValue = if (maxWidth >= 920.dp) 288.dp else 248.dp,
            animationSpec = StudioMotion.mediumInTween(),
            label = "SettingsSidebarWidth"
        )
        val activeCategoryForShortcuts = if (isTwoPaneLayout) desktopSelectedCategory else mobileActiveCategory

        val shouldSuspendDesktopShortcuts = showResetConfirm ||
            activeCategoryForShortcuts == SettingsCategory.QUICK_NOTES ||
            (activeCategoryForShortcuts == SettingsCategory.DISPLAY_PROMPT && carrierSentenceEnabled) ||
            (activeCategoryForShortcuts == SettingsCategory.PRAAT_TEXTGRID && isTextGridCustomInputActive)
        SuspendDesktopShortcutsWhen(shouldSuspendDesktopShortcuts)

        val rootBackState = rememberPredictiveBackVisualState()
        val subPageBackState = rememberPredictiveBackVisualState()

        // Root back handler (active in desktop two-pane OR mobile main menu)
        PredictiveBackGestureHandler(
            enabled = isTwoPaneLayout || mobileActiveCategory == null,
            state = rootBackState
        ) {
            onBack()
        }

        // Mobile secondary page back handler -> returns to Settings Main Menu
        PredictiveBackGestureHandler(
            enabled = !isTwoPaneLayout && mobileActiveCategory != null,
            state = subPageBackState
        ) {
            mobileActiveCategoryId = null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .predictiveBackLayer(rootBackState)
        ) {
            if (isTwoPaneLayout) {
                // =========================================================================
                // DESKTOP & WIDE TABLET VIEW: Left Sidebar (侧栏) + Right Main Pane (主栏)
                // =========================================================================
                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = S.settings.screenTitle,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = if (isSimpleMode) S.settings.modeSimpleCardTitle else S.settings.modeAdvCardTitle,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = onBack,
                                        modifier = Modifier.testTag("settings_back_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.settings.backDesc)
                                    }
                                },
                                actions = {
                                    TextButton(
                                        onClick = { showResetConfirm = true },
                                        modifier = Modifier.testTag("settings_reset_all_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Restore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(S.settings.resetDefaultButton, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Left Sidebar (主菜单侧栏)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier
                                .width(sidebarWidth)
                                .fillMaxHeight()
                                .testTag("settings_desktop_sidebar")
                        ) {
                            val sidebarScrollState = rememberScrollState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(sidebarScrollState)
                                        .padding(
                                            start = 12.dp,
                                            end = if (DesktopAdaptiveState.isDesktopPlatform) 14.dp else 12.dp,
                                            top = 14.dp,
                                            bottom = 14.dp
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    SettingsMenuGroup.entries.forEach { group ->
                                        val groupItems = visibleCategories.filter { it.group == group }
                                        if (groupItems.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = group.title,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                                                )
                                                groupItems.forEach { category ->
                                                    SettingsDesktopSidebarItem(
                                                        category = category,
                                                        summary = summaryFor(category),
                                                        selected = desktopSelectedCategory == category,
                                                        onClick = {
                                                            desktopSelectedCategoryId = category.id
                                                            mobileActiveCategoryId = category.id
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                PlatformVerticalScrollbar(scrollState = sidebarScrollState)
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight().width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )

                        // Right Main Pane (设置详情主栏)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.background)
                                .testTag("settings_desktop_detail_pane"),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AnimatedContent(
                                targetState = desktopSelectedCategory,
                                transitionSpec = {
                                    val forward = targetState.ordinal >= initialState.ordinal
                                    (androidx.compose.animation.slideInVertically(StudioMotion.mediumInTween()) { fullH ->
                                        if (forward) fullH / 18 else -fullH / 18
                                    } + fadeIn(StudioMotion.shortInTween())) togetherWith
                                        (androidx.compose.animation.slideOutVertically(StudioMotion.mediumOutTween()) { fullH ->
                                            if (forward) -fullH / 22 else fullH / 22
                                        } + fadeOut(StudioMotion.shortOutTween()))
                                },
                                label = "DesktopSettingsDetailSwitch",
                                modifier = Modifier.fillMaxSize()
                            ) { selectedCat ->
                                val detailScrollState = rememberScrollState()
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 780.dp)
                                            .fillMaxSize()
                                            .verticalScroll(detailScrollState)
                                            .padding(horizontal = 24.dp, vertical = 18.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        CategoryDetailHeaderCard(category = selectedCat)
                                        RenderCategoryDetailContent(selectedCat)
                                        Spacer(modifier = Modifier.height(28.dp))
                                    }
                                    PlatformVerticalScrollbar(scrollState = detailScrollState)
                                }
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // MOBILE / COMPACT VIEW: Main Menu (主菜单) -> Secondary Page (二级页面)
                // =========================================================================
                AnimatedContent(
                    targetState = mobileActiveCategory,
                    transitionSpec = {
                        if (targetState != null && initialState == null) {
                            // Entering secondary page from main menu
                            (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(180))) togetherWith
                                (slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut(tween(140)))
                        } else if (targetState == null && initialState != null) {
                            // Returning to main menu from secondary page
                            (slideInHorizontally(tween(200)) { -it / 6 } + fadeIn(tween(160))) togetherWith
                                (slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(130)))
                        } else {
                            fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "MobileSettingsHierarchyTransition",
                    modifier = Modifier.fillMaxSize()
                ) { currentSubPage ->
                    if (currentSubPage == null) {
                        // 1. Mobile Main Menu (手机端设置主菜单)
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = S.settings.screenTitle,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = onBack,
                                            modifier = Modifier.testTag("settings_back_button")
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = S.settings.backDesc)
                                        }
                                    },
                                    actions = {
                                        TextButton(
                                            onClick = { showResetConfirm = true },
                                            modifier = Modifier.testTag("settings_reset_all_button")
                                        ) {
                                            Text(S.settings.resetDefaultButton, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = MaterialTheme.colorScheme.background,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("settings_mobile_main_menu")
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .verticalScroll(rememberScrollState())
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SettingsMenuGroup.entries.forEach { group ->
                                    val groupItems = visibleCategories.filter { it.group == group }
                                    if (groupItems.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = group.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp)
                                            )
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    groupItems.forEachIndexed { idx, category ->
                                                        SettingsMobileMenuRow(
                                                            category = category,
                                                            summary = summaryFor(category),
                                                            onClick = {
                                                                mobileActiveCategoryId = category.id
                                                                desktopSelectedCategoryId = category.id
                                                            }
                                                        )
                                                        if (idx < groupItems.lastIndex) {
                                                            HorizontalDivider(
                                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                                                                modifier = Modifier.padding(start = 70.dp, end = 16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    } else {
                        // 2. Mobile Secondary Detail Page (手机端二级设置页面)
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                text = currentSubPage.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = currentSubPage.group.title,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = { mobileActiveCategoryId = null },
                                            modifier = Modifier.testTag("settings_subpage_back_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = S.settings.backToMainMenuDesc
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            },
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            containerColor = MaterialTheme.colorScheme.background,
                            modifier = Modifier
                                .fillMaxSize()
                                .predictiveBackLayer(subPageBackState)
                                .testTag("settings_mobile_subpage_${currentSubPage.id}")
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .verticalScroll(rememberScrollState())
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CategoryDetailHeaderCard(category = currentSubPage)
                                RenderCategoryDetailContent(currentSubPage)
                                Spacer(modifier = Modifier.height(28.dp))
                            }
                        }
                    }
                }
            }

            com.example.ui.components.AnimatedDialogVisibility(visible = showResetConfirm) {
                com.example.ui.components.StudioAlertDialog(
                    onDismissRequest = { showResetConfirm = false },
                    title = { Text(S.settings.resetConfirmTitle, fontWeight = FontWeight.Bold) },
                    text = {
                        Text(S.settings.resetConfirmBody)
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.resetAllSettingsToDefault()
                                showResetConfirm = false
                                Toast.makeText(context, S.settings.resetSuccessToast, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(S.settings.confirmResetButton)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirm = false }) {
                            Text(S.common.cancel)
                        }
                    }
                )
            }

            com.example.ui.components.AnimatedDialogVisibility(visible = showPairingDialogInSettings) {
                WirelessLinkPairingDialog(
                    viewModel = viewModel,
                    onDismiss = { showPairingDialogInSettings = false }
                )
            }
        }
    }
}

@Composable
private fun SettingsMobileMenuRow(
    category: SettingsCategory,
    summary: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (iconBg, iconFg) = categoryIconTintPair(category)
    val (interactionSource, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val isPressed by interactionSource.collectIsPressedAsState()

    val rowBg by animateColorAsState(
        targetValue = if (isHovered) {
            lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.25f)
        } else {
            colorScheme.surfaceContainerLow
        },
        animationSpec = tween(140),
        label = "mobileMenuRowBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.99f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "mobileMenuRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(rowBg)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .testTag("settings_nav_${category.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).padding(end = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = iconFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = category.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = S.settings.enterCategoryDesc.withArgs("title" to category.title),
            tint = colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsDesktopSidebarItem(
    category: SettingsCategory,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val (iconBg, iconFg) = categoryIconTintPair(category)
    StudioSidebarNavItem(
        title = category.title,
        subtitle = summary,
        icon = category.icon,
        iconContainerColor = iconBg,
        iconTint = iconFg,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag("settings_nav_${category.id}")
    )
}
