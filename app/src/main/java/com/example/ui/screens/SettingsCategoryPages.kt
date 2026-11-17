package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ShufflingMode
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.KeycapBadge
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioRadioRow
import com.example.ui.components.StudioSwitchRow
import com.example.ui.components.TextGridSettingsSection
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.ui.components.rememberStudioInteraction
import com.example.utils.TextGridFormatConfig
import com.example.utils.TextGridPreset
import com.example.viewmodel.RecordingViewModel
import java.io.File
import java.util.Locale

enum class SettingsMenuGroup {
    CORE_WORKFLOW,
    ACOUSTICS_AND_FORMAT,
    DATA_AND_SYSTEM;

    val title: String
        get() = when (this) {
            CORE_WORKFLOW -> S.settings.groupCoreWorkflow
            ACOUSTICS_AND_FORMAT -> S.settings.groupAcousticsFormat
            DATA_AND_SYSTEM -> S.settings.groupDataSystem
        }
}

enum class SettingsCategory(
    val id: String,
    val group: SettingsMenuGroup,
    val icon: ImageVector,
    val availableInSimpleMode: Boolean
) {
    INTERFACE_MODE(
        id = "interface_mode",
        group = SettingsMenuGroup.CORE_WORKFLOW,
        icon = Icons.Default.AutoAwesome,
        availableInSimpleMode = true
    ),
    RECORDING_CONTROL(
        id = "recording_control",
        group = SettingsMenuGroup.CORE_WORKFLOW,
        icon = Icons.Default.Mic,
        availableInSimpleMode = true
    ),
    DISPLAY_PROMPT(
        id = "display_prompt",
        group = SettingsMenuGroup.CORE_WORKFLOW,
        icon = Icons.Default.TextFields,
        availableInSimpleMode = true
    ),
    MULTI_DEVICE_LINK(
        id = "multi_device_link",
        group = SettingsMenuGroup.CORE_WORKFLOW,
        icon = Icons.Default.Devices,
        availableInSimpleMode = true
    ),
    QUICK_NOTES(
        id = "quick_notes",
        group = SettingsMenuGroup.CORE_WORKFLOW,
        icon = Icons.Default.EditNote,
        availableInSimpleMode = false
    ),
    PRAAT_TEXTGRID(
        id = "praat_textgrid",
        group = SettingsMenuGroup.ACOUSTICS_AND_FORMAT,
        icon = Icons.Default.AccountTree,
        availableInSimpleMode = false
    ),
    AUDIO_HARDWARE(
        id = "audio_hardware",
        group = SettingsMenuGroup.ACOUSTICS_AND_FORMAT,
        icon = Icons.Default.GraphicEq,
        availableInSimpleMode = false
    ),
    EXPORT_STORAGE(
        id = "export_storage",
        group = SettingsMenuGroup.DATA_AND_SYSTEM,
        icon = Icons.Default.FolderZip,
        availableInSimpleMode = true
    ),
    APPEARANCE(
        id = "appearance",
        group = SettingsMenuGroup.DATA_AND_SYSTEM,
        icon = Icons.Default.Palette,
        availableInSimpleMode = true
    ),
    ABOUT(
        id = "about_phonacq",
        group = SettingsMenuGroup.DATA_AND_SYSTEM,
        icon = Icons.Default.Info,
        availableInSimpleMode = true
    );

    val title: String
        get() = when (this) {
            INTERFACE_MODE -> S.settings.catWorkModeTitle
            RECORDING_CONTROL -> S.settings.catRecordingControlTitle
            DISPLAY_PROMPT -> S.settings.catDisplayPromptTitle
            MULTI_DEVICE_LINK -> S.settings.catWirelessTitle
            QUICK_NOTES -> S.settings.catQuickNotesTitle
            PRAAT_TEXTGRID -> S.settings.catTextGridTitle
            AUDIO_HARDWARE -> S.settings.catAcousticInputTitle
            EXPORT_STORAGE -> S.settings.catExportStorageTitle
            APPEARANCE -> S.settings.catThemeAppearanceTitle
            ABOUT -> S.settings.catAboutTitle
        }

    val detailSubtitle: String
        get() = when (this) {
            INTERFACE_MODE -> S.settings.catWorkModeDesc
            RECORDING_CONTROL -> S.settings.catRecordingControlDesc
            DISPLAY_PROMPT -> S.settings.catDisplayPromptDesc
            MULTI_DEVICE_LINK -> S.settings.catWirelessDesc
            QUICK_NOTES -> S.settings.catQuickNotesDesc
            PRAAT_TEXTGRID -> S.settings.catTextGridDesc
            AUDIO_HARDWARE -> S.settings.catAcousticInputDesc
            EXPORT_STORAGE -> S.settings.catExportStorageDesc
            APPEARANCE -> S.settings.catThemeAppearanceDesc
            ABOUT -> S.settings.catAboutDesc
        }

    companion object {
        fun visibleCategories(isSimpleMode: Boolean): List<SettingsCategory> {
            return entries.filter { !isSimpleMode || it.availableInSimpleMode }
        }
    }
}

@Composable
internal fun categoryIconTintPair(category: SettingsCategory): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (category) {
        SettingsCategory.INTERFACE_MODE -> cs.primaryContainer to cs.onPrimaryContainer
        SettingsCategory.RECORDING_CONTROL -> cs.secondaryContainer to cs.onSecondaryContainer
        SettingsCategory.DISPLAY_PROMPT -> cs.tertiaryContainer to cs.onTertiaryContainer
        SettingsCategory.MULTI_DEVICE_LINK -> cs.primaryContainer to cs.primary
        SettingsCategory.QUICK_NOTES -> cs.secondaryContainer.copy(alpha = 0.85f) to cs.onSecondaryContainer
        SettingsCategory.PRAAT_TEXTGRID -> cs.primaryContainer.copy(alpha = 0.85f) to cs.primary
        SettingsCategory.AUDIO_HARDWARE -> cs.tertiaryContainer.copy(alpha = 0.85f) to cs.onTertiaryContainer
        SettingsCategory.EXPORT_STORAGE -> cs.secondaryContainer to cs.onSecondaryContainer
        SettingsCategory.APPEARANCE -> cs.primaryContainer.copy(alpha = 0.75f) to cs.onPrimaryContainer
        SettingsCategory.ABOUT -> cs.tertiaryContainer.copy(alpha = 0.8f) to cs.onTertiaryContainer
    }
}

@Composable
internal fun CategoryDetailHeaderCard(
    category: SettingsCategory,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val (iconBg, iconFg) = categoryIconTintPair(category)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.18f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iconBg,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = iconFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = category.detailSubtitle,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailingAction != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailingAction()
            }
        }
    }
}

@Composable
internal fun InterfaceModeCategoryContent(
    isSimpleMode: Boolean,
    onSelectSimpleMode: (Boolean) -> Unit
) {
    SettingsGroupCard(title = S.settings.selectWorkModeCardTitle) {
        SettingsUiModeSelectorSection(
            isSimpleMode = isSimpleMode,
            onSelectSimpleMode = onSelectSimpleMode
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecordingControlCategoryContent(
    viewModel: RecordingViewModel,
    isSimpleMode: Boolean,
    isAutoNext: Boolean,
    silenceDelayMs: Long,
    isPushToTalk: Boolean,
    autoPlayAfterRecord: Boolean,
    volumeKeyControlEnabled: Boolean,
    volumeKeyAction: String,
    confirmOverwrite: Boolean,
    maxRetainedTakes: Int
) {
    val autoAdvanceOnHoldRelease by viewModel.autoAdvanceOnHoldRelease.collectAsState()
    val holdOnQualityIssue by viewModel.holdOnQualityIssue.collectAsState()

    val currentHabitIndex = when {
        isPushToTalk -> 1
        isAutoNext -> 2
        else -> 0
    }
    var showFineSwitchDetails by remember { mutableStateOf(!isSimpleMode) }

    SettingsGroupCard(title = S.settings.cardRhythmPlaybackTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "常用操作习惯（一键切换）",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioFilterChip(
                    selected = currentHabitIndex == 0,
                    onClick = {
                        viewModel.setAutoNextEnabled(false)
                        viewModel.setPushToTalkEnabled(false)
                        viewModel.setAutoAdvanceOnHoldRelease(false)
                    },
                    labelText = "点按开始/结束"
                )
                StudioFilterChip(
                    selected = currentHabitIndex == 1,
                    onClick = {
                        viewModel.setPushToTalkEnabled(true)
                        viewModel.setAutoAdvanceOnHoldRelease(true)
                    },
                    labelText = "按住说话，松手下一词"
                )
                StudioFilterChip(
                    selected = currentHabitIndex == 2,
                    onClick = {
                        viewModel.setAutoNextEnabled(true)
                        viewModel.setAutoAdvanceOnHoldRelease(false)
                    },
                    labelText = "读完停顿自动下一词"
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        if (isSimpleMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showFineSwitchDetails = !showFineSwitchDetails }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "展开细分操控开关",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showFineSwitchDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = !isSimpleMode || showFineSwitchDetails) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSwitchRow(
                    title = S.settings.autoNextTitle,
                    subtitle = S.settings.autoNextSub,
                    checked = isAutoNext,
                    onCheckedChange = { viewModel.setAutoNextEnabled(it) }
                )

        if (!isSimpleMode) {
            AnimatedVisibility(
                visible = isAutoNext && !isPushToTalk,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(S.settings.silenceDelayLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                S.settings.silenceDelaySecFormat.withArgs(
                                    "sec" to String.format(Locale.US, "%.1f", silenceDelayMs / 1000f)
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Slider(
                        value = silenceDelayMs.toFloat(),
                        onValueChange = { viewModel.setSilenceDelayMs(it.toLong()) },
                        valueRange = 400f..2500f,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SettingsSwitchRow(
            title = S.settings.holdOnlyTitle,
            subtitle = if (DesktopAdaptiveState.isDesktopPlatform) {
                S.settings.holdOnlyDesktopSub
            } else {
                S.settings.holdOnlyMobileSub
            },
            checked = isPushToTalk,
            onCheckedChange = { viewModel.setPushToTalkEnabled(it) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SettingsSwitchRow(
            title = S.settings.holdReleaseNextTitle,
            subtitle = S.settings.holdReleaseNextSub,
            checked = autoAdvanceOnHoldRelease,
            onCheckedChange = { viewModel.setAutoAdvanceOnHoldRelease(it) }
        )

        if (!isSimpleMode && autoAdvanceOnHoldRelease) {
            SettingsSwitchRow(
                title = S.settings.holdOnQualityTitle,
                subtitle = S.settings.holdOnQualitySub,
                checked = holdOnQualityIssue,
                onCheckedChange = { viewModel.setHoldOnQualityIssue(it) }
            )
        }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SettingsSwitchRow(
            title = S.settings.autoPlayTitle,
            subtitle = S.settings.autoPlaySub,
            checked = autoPlayAfterRecord,
            onCheckedChange = { viewModel.setAutoPlayAfterRecord(it) }
        )
    }

    SettingsGroupCard(title = S.settings.cardAntiMistouchTitle) {
        SettingsSwitchRow(
            title = S.settings.confirmOverwriteTitle,
            subtitle = S.settings.confirmOverwriteSub,
            checked = confirmOverwrite,
            onCheckedChange = { viewModel.setConfirmOverwriteRecording(it) }
        )

        if (!isSimpleMode) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S.settings.retainTakesTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    S.settings.retainTakesSub,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val takeOptions = listOf(
                        1 to S.settings.retain1Take,
                        3 to S.settings.retain3Takes,
                        5 to S.settings.retain5Takes
                    )
                    takeOptions.forEach { (count, label) ->
                        StudioFilterChip(
                            selected = maxRetainedTakes == count,
                            onClick = { viewModel.setMaxRetainedTakes(count) },
                            labelText = label
                        )
                    }
                }
            }
        }
    }

    SettingsGroupCard(
        title = if (DesktopAdaptiveState.isDesktopPlatform) {
            S.settings.cardKeyboardShortcutsTitle
        } else {
            S.settings.cardVolumeKeyTitle
        }
    ) {
        if (DesktopAdaptiveState.isDesktopPlatform) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    S.settings.desktopShortcutsGuide,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeycapBadge("Space / F9")
                        Text(S.settings.shortcutRecordStop, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeycapBadge("← / →")
                        Text(S.settings.shortcutPrevNext, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeycapBadge("Enter / P")
                        Text(S.settings.shortcutListen, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeycapBadge("F")
                        Text(S.settings.shortcutFlag, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeycapBadge("M")
                        Text(S.settings.shortcutCleanMode, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeycapBadge("Esc")
                        Text(S.settings.shortcutBack, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            SettingsSwitchRow(
                title = S.settings.volumeKeyToggleTitle,
                subtitle = S.settings.volumeKeyToggleSub,
                checked = volumeKeyControlEnabled,
                onCheckedChange = { viewModel.setVolumeKeyControlEnabled(it) }
            )

            AnimatedVisibility(
                visible = volumeKeyControlEnabled,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
            ) {
                val volOptions = listOf(
                    "RECORD_NEXT" to S.settings.volumeModeRecordNext,
                    "PREV_NEXT" to S.settings.volumeModePrevNext,
                    "RECORD_ONLY" to S.settings.volumeModeRecordOnly
                )
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    volOptions.forEach { (code, label) ->
                        StudioRadioRow(
                            selected = volumeKeyAction == code,
                            label = label,
                            onClick = { viewModel.setVolumeKeyAction(code) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DisplayPromptCategoryContent(
    viewModel: RecordingViewModel,
    isSimpleMode: Boolean,
    characterSize: String,
    hideRemarkInCleanMode: Boolean,
    carrierSentenceEnabled: Boolean,
    carrierSentenceTemplate: String,
    isShuffleEnabled: Boolean,
    shufflingMode: ShufflingMode,
    keepScreenOn: Boolean
) {
    var carrierInputDraft by remember(carrierSentenceTemplate) { mutableStateOf(carrierSentenceTemplate) }

    SettingsGroupCard(title = S.settings.cardTargetDisplayTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(S.settings.displayFontSizeLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                S.settings.elderFontSizeHint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sizes = listOf(
                    "小" to S.settings.sizeChipSmall,
                    "中" to S.settings.sizeChipMedium,
                    "大" to S.settings.sizeChipLarge,
                    "超大" to S.settings.sizeChipExtraLarge
                )
                sizes.forEach { (opt, label) ->
                    StudioFilterChip(
                        selected = characterSize == opt,
                        onClick = { viewModel.setCharacterSize(opt) },
                        labelText = label
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SettingsSwitchRow(
            title = S.settings.hideRemarksCleanTitle,
            subtitle = S.settings.hideRemarksCleanSub,
            checked = hideRemarkInCleanMode,
            onCheckedChange = { viewModel.setHideRemarkInCleanMode(it) }
        )

        if (!DesktopAdaptiveState.isDesktopPlatform) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsSwitchRow(
                title = S.settings.keepScreenOnTitle,
                subtitle = S.settings.keepScreenOnSub,
                checked = keepScreenOn,
                onCheckedChange = { viewModel.setKeepScreenOn(it) }
            )
        }
    }

    if (!isSimpleMode) {
        SettingsGroupCard(title = S.settings.cardCarrierOrderTitle) {
            SettingsSwitchRow(
                title = S.settings.showCarrierTitle,
                subtitle = S.settings.showCarrierSub,
                checked = carrierSentenceEnabled,
                onCheckedChange = { viewModel.setCarrierSentenceEnabled(it) }
            )

            AnimatedVisibility(
                visible = carrierSentenceEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val presets = S.settings.presetCarriers
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            StudioFilterChip(
                                selected = carrierSentenceTemplate == preset,
                                onClick = {
                                    carrierInputDraft = preset
                                    viewModel.setCarrierSentenceTemplate(preset)
                                },
                                labelText = preset
                            )
                        }
                    }
                    OutlinedTextField(
                        value = carrierInputDraft,
                        onValueChange = {
                            carrierInputDraft = it
                            viewModel.setCarrierSentenceTemplate(it)
                        },
                        label = { Text(S.settings.customCarrierLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsSwitchRow(
                title = S.settings.shuffleTitle,
                subtitle = S.settings.shuffleSub,
                checked = isShuffleEnabled,
                onCheckedChange = { viewModel.toggleShuffle() }
            )

            AnimatedVisibility(
                visible = isShuffleEnabled,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val shuffleOptions = listOf(
                        ShufflingMode.RANDOM_ALL to S.settings.shuffleRandomAll,
                        ShufflingMode.BLOCK_BALANCED to S.settings.shuffleBlockBalanced
                    )
                    shuffleOptions.forEach { (mode, label) ->
                        StudioFilterChip(
                            selected = shufflingMode == mode,
                            onClick = { viewModel.setShufflingMode(mode) },
                            labelText = label
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickNotesCategoryContent(
    context: Context,
    toneSymbols: List<String>,
    fieldTags: List<String>,
    onAddToneSymbol: (String) -> Unit,
    onRemoveToneSymbol: (String) -> Unit,
    onAddFieldTag: (String) -> Unit,
    onRemoveFieldTag: (String) -> Unit,
    onResetDefaults: () -> Unit,
    ipaInitials: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_INITIALS,
    ipaFinals: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_FINALS,
    onAddIpaInitial: (String) -> Unit = {},
    onRemoveIpaInitial: (String) -> Unit = {},
    onAddIpaFinal: (String) -> Unit = {},
    onRemoveIpaFinal: (String) -> Unit = {}
) {
    var newToneInput by remember { mutableStateOf("") }
    var newInitialInput by remember { mutableStateOf("") }
    var newFinalInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }

    SettingsGroupCard(title = S.settings.cardToneSymbolsTitle.withArgs("count" to toneSymbols.size)) {
        Text(
            text = S.settings.toneSymbolsIntro,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (toneSymbols.isEmpty()) {
            Text(S.settings.emptyToneSymbolsHint, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                toneSymbols.forEach { sym ->
                    val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
                    StudioFilterChip(
                        selected = false,
                        onClick = { onRemoveToneSymbol(sym) },
                        labelText = displaySym,
                        showCheckmark = false,
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = S.settings.deleteSymbolDesc.withArgs("symbol" to displaySym),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newToneInput,
                onValueChange = { newToneInput = it },
                placeholder = { Text(S.settings.addSymbolPlaceholder, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    if (newToneInput.isNotBlank()) {
                        onAddToneSymbol(newToneInput)
                        newToneInput = ""
                    }
                },
                enabled = newToneInput.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    SettingsGroupCard(title = S.settings.cardIpaInitialsTitle.withArgs("count" to ipaInitials.size)) {
        Text(
            text = S.settings.ipaInitialsIntro,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (ipaInitials.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ipaInitials.forEach { sym ->
                    val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
                    StudioFilterChip(
                        selected = false,
                        onClick = { onRemoveIpaInitial(sym) },
                        labelText = displaySym,
                        showCheckmark = false,
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = S.settings.deleteSymbolDesc.withArgs("symbol" to displaySym),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newInitialInput,
                onValueChange = { newInitialInput = it },
                placeholder = { Text(S.settings.addIpaInitialPlaceholder, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    if (newInitialInput.isNotBlank()) {
                        onAddIpaInitial(newInitialInput)
                        newInitialInput = ""
                    }
                },
                enabled = newInitialInput.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    SettingsGroupCard(title = S.settings.cardIpaFinalsTitle.withArgs("count" to ipaFinals.size)) {
        Text(
            text = S.settings.ipaFinalsIntro,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (ipaFinals.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ipaFinals.forEach { sym ->
                    val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
                    StudioFilterChip(
                        selected = false,
                        onClick = { onRemoveIpaFinal(sym) },
                        labelText = displaySym,
                        showCheckmark = false,
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = S.settings.deleteSymbolDesc.withArgs("symbol" to displaySym),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newFinalInput,
                onValueChange = { newFinalInput = it },
                placeholder = { Text(S.settings.addIpaFinalPlaceholder, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    if (newFinalInput.isNotBlank()) {
                        onAddIpaFinal(newFinalInput)
                        newFinalInput = ""
                    }
                },
                enabled = newFinalInput.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    SettingsGroupCard(title = S.settings.cardFieldTagsTitle.withArgs("count" to fieldTags.size)) {
        Text(
            text = S.settings.fieldTagsIntro,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (fieldTags.isEmpty()) {
            Text(S.settings.emptyFieldTagsHint, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fieldTags.forEach { tag ->
                    StudioFilterChip(
                        selected = false,
                        onClick = { onRemoveFieldTag(tag) },
                        labelText = tag,
                        showCheckmark = false,
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = S.settings.deleteTagDesc.withArgs("tag" to tag),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newTagInput,
                onValueChange = { newTagInput = it },
                placeholder = { Text(S.settings.addTagPlaceholder, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    if (newTagInput.isNotBlank()) {
                        onAddFieldTag(newTagInput)
                        newTagInput = ""
                    }
                },
                enabled = newTagInput.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = {
                    onResetDefaults()
                    Toast.makeText(context, S.settings.restoredDefaultQuickNotesToast, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.testTag("quick_note_reset_defaults_inline_button")
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(S.settings.restoreDefaultQuickNotesBtn, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PraatTextGridCategoryContent(
    isSimpleMode: Boolean,
    textGridConfig: TextGridFormatConfig,
    onSelectPreset: (TextGridPreset) -> Unit,
    onUpdateConfig: (TextGridFormatConfig) -> Unit,
    onSwitchToAdvancedMode: () -> Unit,
    onCustomInputActiveChange: (Boolean) -> Unit
) {
    if (isSimpleMode) {
        SettingsGroupCard(title = S.settings.cardPraatPresetsTitle) {
            Text(
                text = S.settings.praatPresetsGuide,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    TextGridPreset.UNIVERSAL_STANDARD to S.settings.presetOptionUniversal,
                    TextGridPreset.MFA_WORDS_PHONES to S.settings.presetOptionMfa,
                    TextGridPreset.CHINESE_DIALECT to S.settings.presetOptionDialect,
                    TextGridPreset.PROSODY_SYLLABLES to S.settings.presetOptionProsody,
                    TextGridPreset.PHONTRACER_COMPAT to S.settings.presetOptionPhonTracer
                ).forEach { (preset, label) ->
                    StudioFilterChip(
                        selected = textGridConfig.preset == preset,
                        onClick = { onSelectPreset(preset) },
                        labelText = label
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = S.settings.currentActivePresetLine.withArgs(
                            "preset" to textGridConfig.preset.title,
                            "summary" to textGridConfig.shortSummaryLabel()
                        ),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = textGridConfig.preset.description,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.settings.switchToAdvForCustomTgNote,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 10.dp)
                )
                FilledTonalButton(
                    onClick = onSwitchToAdvancedMode,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(S.settings.switchToAdvCustomButton, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        SettingsGroupCard(title = S.settings.cardAdvTextGridTitle) {
            TextGridSettingsSection(
                config = textGridConfig,
                onSelectPreset = onSelectPreset,
                onUpdateConfig = onUpdateConfig,
                onCustomInputActiveChange = onCustomInputActiveChange
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioHardwareCategoryContent(
    viewModel: RecordingViewModel,
    vadThreshold: Float,
    lowVolumeThreshold: Float,
    creakySensitivity: String,
    sampleRateHz: Int,
    audioSourceStrategy: String,
    preRollEnabled: Boolean
) {
    SettingsGroupCard(title = S.settings.cardVadQualityTitle) {
        // 自动翻页收音灵敏度
        val activePreset = when {
            vadThreshold <= 850f -> "高灵敏"
            vadThreshold >= 1650f -> "抗噪"
            else -> "标准"
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(S.settings.autoNextVadSensitivityTitle, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        S.settings.displayVadPreset(activePreset),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("高灵敏", "标准", "抗噪").forEach { preset ->
                    StudioFilterChip(
                        selected = activePreset == preset,
                        onClick = { viewModel.applyVadSensitivityPreset(preset) },
                        labelText = S.settings.displayVadPreset(preset)
                    )
                }
            }
            Text(
                S.settings.vadSensitivityHint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = vadThreshold,
                onValueChange = { viewModel.setVadThreshold(it) },
                valueRange = 200f..3000f,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 声音偏小提醒门槛
        val lowVolLevelLabel = when {
            lowVolumeThreshold < 400f -> "宽松"
            lowVolumeThreshold > 950f -> "严格"
            else -> "适中"
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(S.settings.lowVolumeThresholdTitle, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        lowVolLevelLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                S.settings.lowVolumeThresholdHint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = lowVolumeThreshold,
                onValueChange = { viewModel.setLowVolumeThreshold(it) },
                valueRange = 150f..2500f,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // 气泡音/沙哑提醒
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(S.settings.creakyReminderTitle, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                S.settings.creakyReminderHint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("低", "中", "高", "关闭").forEach { option ->
                    StudioFilterChip(
                        selected = creakySensitivity == option,
                        onClick = { viewModel.setCreakySensitivity(option) },
                        labelText = S.settings.displayCreakyOption(option)
                    )
                }
            }
        }
    }

    SettingsGroupCard(title = S.settings.cardMicSamplingTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(S.settings.sampleRateTitle, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val rates = listOf(
                    44100 to S.settings.sampleRate44kRec,
                    48000 to S.settings.sampleRate48kExt,
                    22050 to "22.05 kHz"
                )
                rates.forEach { (rate, label) ->
                    StudioFilterChip(
                        selected = sampleRateHz == rate,
                        onClick = { viewModel.setSampleRateHz(rate) },
                        labelText = label
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(S.settings.micInputModeTitle, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                S.settings.micInputModeHint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sources = listOf(
                    "VOICE_RECOGNITION" to S.settings.micOptVoiceRecognition,
                    "UNPROCESSED" to S.settings.micOptUnprocessed,
                    "MIC" to if (DesktopAdaptiveState.isDesktopPlatform) S.settings.micOptMicDesktop else S.settings.micOptMicMobile
                )
                sources.forEach { (src, label) ->
                    StudioFilterChip(
                        selected = audioSourceStrategy == src,
                        onClick = { viewModel.setAudioSourceStrategy(src) },
                        labelText = label
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SettingsSwitchRow(
            title = S.settings.onsetPreRollTitle,
            subtitle = S.settings.onsetPreRollSub,
            checked = preRollEnabled,
            onCheckedChange = { viewModel.setPreRollEnabled(it) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExportStorageCategoryContent(
    context: Context,
    viewModel: RecordingViewModel,
    isSimpleMode: Boolean,
    exportNamingStyle: String,
    exportFolderPerSpeaker: Boolean,
    storageSizeText: String,
    onOpenRecordingsCleanup: () -> Unit
) {
    val exportAppendNoteToFileName by viewModel.exportAppendNoteToFileName.collectAsState()
    var isImportingBackupZip by remember { mutableStateOf(false) }
    val importBackupZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !isImportingBackupZip) {
            try {
                val ins = context.contentResolver.openInputStream(uri)
                if (ins != null) {
                    isImportingBackupZip = true
                    viewModel.importProjectZip(ins) { summary ->
                        isImportingBackupZip = false
                        Toast.makeText(context, summary.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                isImportingBackupZip = false
                Toast.makeText(context, S.common.errorOccurred.withArgs("msg" to e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    if (!isSimpleMode) {
        SettingsGroupCard(title = S.settings.cardExportNamingTitle) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(S.settings.exportFilenameFormatTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val namingOptions = listOf(
                    "CODE_INDEX_WORD" to S.settings.namingOptCodeDesc,
                    "NAME_INDEX_WORD" to S.settings.namingOptNameDesc,
                    "INDEX_WORD" to S.settings.namingOptIndexDesc
                )
                namingOptions.forEach { (style, label) ->
                    StudioRadioRow(
                        selected = exportNamingStyle == style,
                        label = label,
                        onClick = { viewModel.setExportNamingStyle(style) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsSwitchRow(
                title = S.settings.folderPerSpeakerTitle,
                subtitle = S.settings.folderPerSpeakerSub,
                checked = exportFolderPerSpeaker,
                onCheckedChange = { viewModel.setExportFolderPerSpeaker(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsSwitchRow(
                title = S.settings.appendNoteToFileNameTitle,
                subtitle = S.settings.appendNoteToFileNameSub,
                checked = exportAppendNoteToFileName,
                onCheckedChange = { viewModel.setExportAppendNoteToFileName(it) }
            )
        }
    }

    SettingsGroupCard(title = S.settings.cardStorageTitle) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    S.settings.usedSpaceLine.withArgs("size" to storageSizeText),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    S.settings.cleanCacheNote,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    S.settings.importBackupZipHint,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (!isImportingBackupZip) {
                            importBackupZipLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        }
                    },
                    enabled = !isImportingBackupZip,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("settings_import_backup_zip_button")
                ) {
                    if (isImportingBackupZip) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(S.project.importingProject, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(S.settings.importBackupZipButton, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = {
                        val cacheExports = File(context.cacheDir, "exports")
                        cacheExports.deleteRecursively()
                        Toast.makeText(context, S.settings.cleanedCacheToast, Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("simple_clear_cache_button")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(S.settings.cleanCacheButton, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!isSimpleMode) {
                    FilledTonalButton(
                        onClick = onOpenRecordingsCleanup,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(S.settings.manageRecordingsSpeakersButton, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceCategoryContent(
    viewModel: RecordingViewModel,
    themeMode: String,
    dynamicColor: Boolean
) {
    SettingsGroupCard(title = S.settings.cardThemeTitle) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val themes = listOf(
                "SYSTEM" to S.settings.themeOptSystem,
                "LIGHT" to S.settings.themeOptLight,
                "DARK" to S.settings.themeOptDark
            )
            themes.forEach { (mode, label) ->
                StudioFilterChip(
                    selected = themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    labelText = label
                )
            }
        }

        if (!DesktopAdaptiveState.isDesktopPlatform) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsSwitchRow(
                title = S.settings.dynamicColorTitle,
                subtitle = S.settings.dynamicColorSub,
                checked = dynamicColor,
                onCheckedChange = { viewModel.setDynamicColorEnabled(it) }
            )
        }
    }
}

@Composable
internal fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    StudioSwitchRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

private data class ModeFeatureTag(
    val icon: ImageVector,
    val label: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsUiModeSelectorSection(
    isSimpleMode: Boolean,
    onSelectSimpleMode: (Boolean) -> Unit
) {
    val simpleFeatures = remember(S.settings) {
        listOf(
            ModeFeatureTag(Icons.Default.EditNote, S.settings.tagSimpleImport),
            ModeFeatureTag(Icons.Default.GraphicEq, S.settings.tagSimpleMeter),
            ModeFeatureTag(Icons.Default.FolderZip, S.settings.tagSimpleZip)
        )
    }
    val advancedFeatures = remember(S.settings) {
        listOf(
            ModeFeatureTag(Icons.Default.Insights, S.settings.tagAdvToneFormant),
            ModeFeatureTag(Icons.Default.History, S.settings.tagAdvTakesIpa),
            ModeFeatureTag(Icons.Default.Tune, "Praat / PhonTracer")
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useSideBySide = maxWidth >= 500.dp
            if (useSideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsModeOptionCard(
                        title = S.settings.modeSimpleCardTitle,
                        tagline = S.settings.modeSimpleCardTagline,
                        description = S.settings.modeSimpleCardDesc,
                        icon = Icons.Default.AutoAwesome,
                        features = simpleFeatures,
                        selected = isSimpleMode,
                        onClick = { onSelectSimpleMode(true) },
                        testTag = "settings_mode_simple",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    SettingsModeOptionCard(
                        title = S.settings.modeAdvCardTitle,
                        tagline = S.settings.modeAdvCardTagline,
                        description = S.settings.modeAdvCardDesc,
                        icon = Icons.Default.Analytics,
                        features = advancedFeatures,
                        selected = !isSimpleMode,
                        onClick = { onSelectSimpleMode(false) },
                        testTag = "settings_mode_advanced",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsModeOptionCard(
                        title = S.settings.modeSimpleCardTitle,
                        tagline = S.settings.modeSimpleCardTagline,
                        description = S.settings.modeSimpleCardDesc,
                        icon = Icons.Default.AutoAwesome,
                        features = simpleFeatures,
                        selected = isSimpleMode,
                        onClick = { onSelectSimpleMode(true) },
                        testTag = "settings_mode_simple",
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsModeOptionCard(
                        title = S.settings.modeAdvCardTitle,
                        tagline = S.settings.modeAdvCardTagline,
                        description = S.settings.modeAdvCardDesc,
                        icon = Icons.Default.Analytics,
                        features = advancedFeatures,
                        selected = !isSimpleMode,
                        onClick = { onSelectSimpleMode(false) },
                        testTag = "settings_mode_advanced",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = S.settings.modeSwitchInstantNote,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsModeOptionCard(
    title: String,
    tagline: String,
    description: String,
    icon: ImageVector,
    features: List<ModeFeatureTag>,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val (interactionSource, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val containerColor by animateColorAsState(
        targetValue = when {
            selected && isHovered -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.48f)
            selected -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.34f)
            isHovered -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.18f)
            else -> colorScheme.surface
        },
        animationSpec = tween(160),
        label = "ModeCardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> colorScheme.primary
            isHovered -> colorScheme.primary.copy(alpha = 0.55f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(160),
        label = "ModeCardBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "ModeCardScale"
    )
    val accentGlowColor = colorScheme.primary.copy(alpha = if (selected) 0.08f else 0f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 1.6.dp else 1.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                if (selected) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(accentGlowColor, Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.85f, size.height * 0.85f)
                        )
                    )
                }
            }
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) {
                                colorScheme.primary
                            } else {
                                colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) {
                                        colorScheme.onPrimary
                                    } else {
                                        colorScheme.primary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = title,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = tagline,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (selected) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = S.settings.currentCheckDesc,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = S.settings.currentBadge,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isHovered) colorScheme.primary else colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Text(
                    text = description,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.86f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                features.forEach { feat ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) {
                            colorScheme.surface.copy(alpha = 0.82f)
                        } else {
                            colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = 0.8.dp,
                            color = if (selected) {
                                colorScheme.primary.copy(alpha = 0.25f)
                            } else {
                                colorScheme.outlineVariant.copy(alpha = 0.45f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = feat.icon,
                                contentDescription = null,
                                tint = if (selected) {
                                    colorScheme.primary
                                } else {
                                    colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(12.5.dp)
                            )
                            Text(
                                text = feat.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) {
                                    colorScheme.onSurface
                                } else {
                                    colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickNoteCustomizationDialog(
    toneSymbols: List<String>,
    fieldTags: List<String>,
    onAddToneSymbol: (String) -> Unit,
    onRemoveToneSymbol: (String) -> Unit,
    onAddFieldTag: (String) -> Unit,
    onRemoveFieldTag: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit,
    ipaInitials: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_INITIALS,
    ipaFinals: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_FINALS,
    onAddIpaInitial: (String) -> Unit = {},
    onRemoveIpaInitial: (String) -> Unit = {},
    onAddIpaFinal: (String) -> Unit = {},
    onRemoveIpaFinal: (String) -> Unit = {}
) {
    com.example.ui.components.StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        com.example.ui.components.StudioDialogEnterAnimation(modifier = Modifier.fillMaxSize()) {
            QuickNoteCustomizationScreen(
                toneSymbols = toneSymbols,
                fieldTags = fieldTags,
                onAddToneSymbol = onAddToneSymbol,
                onRemoveToneSymbol = onRemoveToneSymbol,
                onAddFieldTag = onAddFieldTag,
                onRemoveFieldTag = onRemoveFieldTag,
                onResetDefaults = onResetDefaults,
                onBack = onDismiss,
                ipaInitials = ipaInitials,
                ipaFinals = ipaFinals,
                onAddIpaInitial = onAddIpaInitial,
                onRemoveIpaInitial = onRemoveIpaInitial,
                onAddIpaFinal = onAddIpaFinal,
                onRemoveIpaFinal = onRemoveIpaFinal
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickNoteCustomizationScreen(
    toneSymbols: List<String>,
    fieldTags: List<String>,
    onAddToneSymbol: (String) -> Unit,
    onRemoveToneSymbol: (String) -> Unit,
    onAddFieldTag: (String) -> Unit,
    onRemoveFieldTag: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onBack: () -> Unit,
    ipaInitials: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_INITIALS,
    ipaFinals: List<String> = RecordingViewModel.DEFAULT_DIALECT_IPA_FINALS,
    onAddIpaInitial: (String) -> Unit = {},
    onRemoveIpaInitial: (String) -> Unit = {},
    onAddIpaFinal: (String) -> Unit = {},
    onRemoveIpaFinal: (String) -> Unit = {}
) {
    val quickNoteBackState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = true,
        state = quickNoteBackState
    ) {
        onBack()
    }

    var newToneInput by remember { mutableStateOf("") }
    var newInitialInput by remember { mutableStateOf("") }
    var newFinalInput by remember { mutableStateOf("") }
    var newTagInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = S.settings.customQuickNotesPageTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("quick_note_editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.settings.backDesc
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.testTag("quick_note_reset_defaults_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(S.settings.restoreDefaultItemsBtn, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("quick_note_done_button")
                    ) {
                        Text(S.common.done, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .predictiveBackLayer(quickNoteBackState)
            .testTag("quick_note_customization_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = S.settings.customQuickNotesIntro,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = S.settings.toneSymbolsSectionTitle.withArgs("count" to toneSymbols.size),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (toneSymbols.isEmpty()) {
                            Text(S.settings.emptyToneSymbolsHintBottom, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                toneSymbols.forEach { sym ->
                                    val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
                                    StudioFilterChip(
                                        selected = false,
                                        onClick = { onRemoveToneSymbol(sym) },
                                        labelText = displaySym,
                                        showCheckmark = false,
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = S.settings.deleteSymbolDesc.withArgs("symbol" to displaySym),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newToneInput,
                                onValueChange = { newToneInput = it },
                                placeholder = { Text(S.settings.addSymbolPlaceholder, fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (newToneInput.isNotBlank()) {
                                        onAddToneSymbol(newToneInput)
                                        newToneInput = ""
                                    }
                                },
                                enabled = newToneInput.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = S.settings.cardIpaInitialsTitle.withArgs("count" to ipaInitials.size),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (ipaInitials.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ipaInitials.forEach { sym ->
                                    val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
                                    StudioFilterChip(
                                        selected = false,
                                        onClick = { onRemoveIpaInitial(sym) },
                                        labelText = displaySym,
                                        showCheckmark = false,
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = S.settings.deleteSymbolDesc.withArgs("symbol" to displaySym),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newInitialInput,
                                onValueChange = { newInitialInput = it },
                                placeholder = { Text(S.settings.addIpaInitialPlaceholder, fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (newInitialInput.isNotBlank()) {
                                        onAddIpaInitial(newInitialInput)
                                        newInitialInput = ""
                                    }
                                },
                                enabled = newInitialInput.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = S.settings.cardIpaFinalsTitle.withArgs("count" to ipaFinals.size),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (ipaFinals.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ipaFinals.forEach { sym ->
                                    val displaySym = if (sym.length == 1 && sym[0] in '\u0300'..'\u036F') "◌$sym" else sym
                                    StudioFilterChip(
                                        selected = false,
                                        onClick = { onRemoveIpaFinal(sym) },
                                        labelText = displaySym,
                                        showCheckmark = false,
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = S.settings.deleteSymbolDesc.withArgs("symbol" to displaySym),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newFinalInput,
                                onValueChange = { newFinalInput = it },
                                placeholder = { Text(S.settings.addIpaFinalPlaceholder, fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (newFinalInput.isNotBlank()) {
                                        onAddIpaFinal(newFinalInput)
                                        newFinalInput = ""
                                    }
                                },
                                enabled = newFinalInput.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = S.settings.fieldTagsSectionTitle.withArgs("count" to fieldTags.size),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (fieldTags.isEmpty()) {
                            Text(S.settings.emptyFieldTagsHintBottom, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                fieldTags.forEach { tag ->
                                    StudioFilterChip(
                                        selected = false,
                                        onClick = { onRemoveFieldTag(tag) },
                                        labelText = tag,
                                        showCheckmark = false,
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = S.settings.deleteTagDesc.withArgs("tag" to tag),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                placeholder = { Text(S.settings.addTagPlaceholder, fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (newTagInput.isNotBlank()) {
                                        onAddFieldTag(newTagInput)
                                        newTagInput = ""
                                    }
                                },
                                enabled = newTagInput.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                Text(S.common.add, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
