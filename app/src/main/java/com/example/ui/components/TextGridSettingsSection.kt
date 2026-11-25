package com.example.ui.components

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.model.TextGridOpenMode
import com.example.textgrid.model.TextGridScaleMode
import com.example.textgrid.model.TextGridScoreTheme
import com.example.textgrid.model.TextGridTickPrecision
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.model.TextGridWrapMode
import com.example.textgrid.ops.TextGridOps
import com.example.textgrid.ui.TextGridPreviewCard
import com.example.textgrid.ui.TextGridUiStrings
import com.example.ui.screens.SettingsGroupCard
import com.example.utils.TextGridFormatConfig
import com.example.utils.TextGridPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextGridSettingsSection(
    config: TextGridFormatConfig,
    visualConfig: TextGridVisualConfig = TextGridVisualConfig(),
    isSimpleMode: Boolean = false,
    onSelectPreset: (TextGridPreset) -> Unit,
    onUpdateConfig: (TextGridFormatConfig) -> Unit,
    onUpdateVisualConfig: (TextGridVisualConfig) -> Unit = {},
    onSwitchToAdvancedMode: () -> Unit = {},
    onCustomInputActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val tgVis = S.textGrid.visual
    var manualExpandCustom by remember { mutableStateOf(false) }
    val isCustomExpanded = !isSimpleMode && (config.preset == TextGridPreset.CUSTOM || manualExpandCustom)

    LaunchedEffect(isCustomExpanded) {
        onCustomInputActiveChange(isCustomExpanded)
    }
    DisposableEffect(Unit) {
        onDispose { onCustomInputActiveChange(false) }
    }

    val sampleRawSource = remember(config) {
        TextGridFormatConfig.buildLiveSamplePreview(config)
    }
    val sampleDocument = remember(config, sampleRawSource) {
        TextGridCodec.parseText(sampleRawSource, defaultName = S.textGrid.sampleWordText).document
            ?: TextGridOps.buildSampleDocumentFromFormatConfig(config)
    }
    val sampleVariant = remember(config.shortTextFormat) {
        if (config.shortTextFormat) TextGridFormatVariant.SHORT_TEXT else TextGridFormatVariant.LONG_TEXT
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isSimpleMode) {
            // Simple Mode: Preset Switching + Switch-to-Advanced Prompt + Read-Only Live Dual-Mode Sample Preview
            SettingsGroupCard(title = S.settings.cardPraatPresetsTitle) {
                Text(
                    text = tgVis.simpleModeSettingsBanner,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = colorScheme.onSurfaceVariant
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
                            selected = config.preset == preset,
                            onClick = { onSelectPreset(preset) },
                            labelText = label,
                            modifier = Modifier.testTag("tg_simple_preset_${preset.id.lowercase()}")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = S.settings.currentActivePresetLine.withArgs(
                                "preset" to config.preset.title,
                                "summary" to config.shortSummaryLabel()
                            ),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                        Text(
                            text = config.preset.description,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = colorScheme.onSurfaceVariant
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
                        color = colorScheme.onSurfaceVariant,
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

            // Read-Only Dual-Mode Live Sample Preview in Simple Mode
            TextGridPreviewCard(
                doc = sampleDocument,
                visualConfig = visualConfig,
                title = S.textGrid.sampleWordText,
                subtitleHint = tgVis.settingsPreviewHint,
                timeDecimals = config.effectiveTimeDecimals(),
                formatVariant = sampleVariant,
                rawSourceOverride = sampleRawSource,
                enableDemoPlaySweep = true,
                maxScoreHeight = 280.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tg_settings_live_preview_card")
            )
            return@Column
        }

        // =========================================================================
        // ADVANCED MODE: 1. Export Structure & Format Settings Card
        // =========================================================================
        SettingsGroupCard(title = S.settings.cardAdvTextGridTitle) {
            Text(
                text = S.textGrid.sectionIntro,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = colorScheme.onSurfaceVariant
            )

            // 1.1 Preset Cards Grid
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useTwoCols = maxWidth >= 520.dp
                val presets = TextGridPreset.entries
                if (useTwoCols) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        presets.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextGridPresetCard(
                                    preset = pair[0],
                                    selected = config.preset == pair[0],
                                    customActiveTiers = if (pair[0] == TextGridPreset.CUSTOM) config.activeTierNames() else null,
                                    onClick = {
                                        if (pair[0] == TextGridPreset.CUSTOM) manualExpandCustom = true
                                        onSelectPreset(pair[0])
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                if (pair.size == 2) {
                                    TextGridPresetCard(
                                        preset = pair[1],
                                        selected = config.preset == pair[1],
                                        customActiveTiers = if (pair[1] == TextGridPreset.CUSTOM) config.activeTierNames() else null,
                                        onClick = {
                                            if (pair[1] == TextGridPreset.CUSTOM) manualExpandCustom = true
                                            onSelectPreset(pair[1])
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        presets.forEach { preset ->
                            TextGridPresetCard(
                                preset = preset,
                                selected = config.preset == preset,
                                customActiveTiers = if (preset == TextGridPreset.CUSTOM) config.activeTierNames() else null,
                                onClick = {
                                    if (preset == TextGridPreset.CUSTOM) manualExpandCustom = true
                                    onSelectPreset(preset)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 1.2 Syntax, Silence Marker & Timestamp Precision
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = S.textGrid.syntaxSectionTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioFilterChip(
                        selected = !config.shortTextFormat,
                        onClick = { onUpdateConfig(config.copy(shortTextFormat = false)) },
                        labelText = S.textGrid.syntaxLongOption,
                        modifier = Modifier.testTag("tg_syntax_long_chip")
                    )
                    StudioFilterChip(
                        selected = config.shortTextFormat,
                        onClick = { onUpdateConfig(config.copy(shortTextFormat = true)) },
                        labelText = S.textGrid.syntaxShortOption,
                        modifier = Modifier.testTag("tg_syntax_short_chip")
                    )
                }
                Text(
                    text = if (config.shortTextFormat) S.textGrid.syntaxShortNote else S.textGrid.syntaxLongNote,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = S.textGrid.silenceSectionTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                val silenceChoices = listOf(
                    "" to S.textGrid.silenceEmptyLabel,
                    "sil" to S.textGrid.silenceSilLabel,
                    "sp" to S.textGrid.silenceSpLabel,
                    "#" to S.textGrid.silenceHashLabel
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    silenceChoices.forEach { (marker, label) ->
                        StudioFilterChip(
                            selected = config.silenceMarker == marker,
                            onClick = { onUpdateConfig(config.copy(silenceMarker = marker)) },
                            labelText = label,
                            modifier = Modifier.testTag("tg_silence_${if (marker.isEmpty()) "empty" else marker}_chip")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = S.textGrid.precisionSectionTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioFilterChip(
                        selected = config.effectiveTimeDecimals() == 6,
                        onClick = { onUpdateConfig(config.copy(timeDecimals = 6)) },
                        labelText = S.textGrid.precision6DigitsLabel
                    )
                    StudioFilterChip(
                        selected = config.effectiveTimeDecimals() == 4,
                        onClick = { onUpdateConfig(config.copy(timeDecimals = 4)) },
                        labelText = S.textGrid.precision4DigitsLabel
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 1.3 Custom Tier Builder & Tier Renaming
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { manualExpandCustom = !manualExpandCustom }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("tg_toggle_custom_tiers_header"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = S.textGrid.customTiersHeader,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = S.textGrid.currentTierOrderLine.withArgs("order" to config.activeTierNames().joinToString(" → ")),
                                    fontSize = 11.5.sp,
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (config.preset != TextGridPreset.CUSTOM) {
                            val chevronRot by animateFloatAsState(
                                targetValue = if (isCustomExpanded) 180f else 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "customTierChevron"
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = if (isCustomExpanded) S.textGrid.collapseTierEditorDesc else S.textGrid.expandTierEditorDesc,
                                tint = colorScheme.primary,
                                modifier = Modifier.graphicsLayer { rotationZ = chevronRot }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isCustomExpanded,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(120))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.35f))

                            Text(
                                text = S.textGrid.customTiersGuide,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = colorScheme.onSurfaceVariant
                            )

                            val enabledCount = listOf(
                                config.includeGroupsTier,
                                config.includeWordsTier,
                                config.includeSyllablesTier,
                                config.includeIpaTier,
                                config.includeTonesTier,
                                config.includeSegmentsTier
                            ).count { it }

                            CustomTierConfigRow(
                                title = S.textGrid.tierGroupTitle,
                                sampleHint = S.textGrid.tierGroupSampleHint,
                                checked = config.includeGroupsTier,
                                canUncheck = enabledCount > 1 || !config.includeGroupsTier,
                                tierName = config.groupsTierName,
                                defaultTierName = "groups",
                                onCheckedChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, includeGroupsTier = it))
                                },
                                onTierNameChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, groupsTierName = it))
                                }
                            )

                            CustomTierConfigRow(
                                title = S.textGrid.tierWordTitle,
                                sampleHint = S.textGrid.tierWordSampleHint,
                                checked = config.includeWordsTier,
                                canUncheck = enabledCount > 1 || !config.includeWordsTier,
                                tierName = config.wordsTierName,
                                defaultTierName = "words",
                                onCheckedChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, includeWordsTier = it))
                                },
                                onTierNameChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, wordsTierName = it))
                                }
                            )

                            CustomTierConfigRow(
                                title = S.textGrid.tierSyllableTitle,
                                sampleHint = S.textGrid.tierSyllableSampleHint,
                                checked = config.includeSyllablesTier,
                                canUncheck = enabledCount > 1 || !config.includeSyllablesTier,
                                tierName = config.syllablesTierName,
                                defaultTierName = "syllables",
                                onCheckedChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, includeSyllablesTier = it))
                                },
                                onTierNameChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, syllablesTierName = it))
                                }
                            )

                            CustomTierConfigRow(
                                title = S.textGrid.tierIpaTitle,
                                sampleHint = S.textGrid.tierIpaSampleHint,
                                checked = config.includeIpaTier,
                                canUncheck = enabledCount > 1 || !config.includeIpaTier,
                                tierName = config.ipaTierName,
                                defaultTierName = "ipa",
                                onCheckedChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, includeIpaTier = it))
                                },
                                onTierNameChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, ipaTierName = it))
                                },
                                extraContent = if (config.includeIpaTier) {
                                    {
                                        StudioCheckboxRow(
                                            checked = config.ipaFallbackToSyllable,
                                            label = S.textGrid.ipaFallbackSyllableLabel,
                                            onCheckedChange = {
                                                onUpdateConfig(
                                                    config.copy(
                                                        preset = TextGridPreset.CUSTOM,
                                                        ipaFallbackToSyllable = it
                                                    )
                                                )
                                            }
                                        )
                                    }
                                } else null
                            )

                            CustomTierConfigRow(
                                title = S.textGrid.tierToneTitle,
                                sampleHint = S.textGrid.tierToneSampleHint,
                                checked = config.includeTonesTier,
                                canUncheck = enabledCount > 1 || !config.includeTonesTier,
                                tierName = config.tonesTierName,
                                defaultTierName = "tones",
                                onCheckedChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, includeTonesTier = it))
                                },
                                onTierNameChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, tonesTierName = it))
                                }
                            )

                            CustomTierConfigRow(
                                title = S.textGrid.tierLegacyPhonTracerTitle,
                                sampleHint = S.textGrid.tierLegacyPhonTracerSampleHint,
                                checked = config.includeSegmentsTier,
                                canUncheck = enabledCount > 1 || !config.includeSegmentsTier,
                                tierName = config.segmentsTierName,
                                defaultTierName = "segments",
                                onCheckedChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, includeSegmentsTier = it))
                                },
                                onTierNameChange = {
                                    onUpdateConfig(config.copy(preset = TextGridPreset.CUSTOM, segmentsTierName = it))
                                }
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. Live Read-Only Dual-Mode Sample Preview Card ([谱面 | 源码] + 演示扫描)
        // =========================================================================
        TextGridPreviewCard(
            doc = sampleDocument,
            visualConfig = visualConfig,
            title = S.textGrid.sampleWordText,
            subtitleHint = tgVis.settingsPreviewHint,
            timeDecimals = config.effectiveTimeDecimals(),
            formatVariant = sampleVariant,
            rawSourceOverride = sampleRawSource,
            enableDemoPlaySweep = true,
            maxScoreHeight = 300.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tg_settings_live_preview_card")
        )

        // =========================================================================
        // 3. Streamlined Score Viewer Preferences Card (6 Read-Only Options)
        // =========================================================================
        SettingsGroupCard(title = tgVis.prefsCardTitle) {
            Text(
                text = tgVis.prefsCardSubtitle,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colorScheme.onSurfaceVariant
            )

            // 1) Default open mode: Score / Source
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tgVis.prefDefaultView,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextGridOpenMode.entries.forEach { modeOpt ->
                        StudioFilterChip(
                            selected = visualConfig.defaultOpenMode == modeOpt,
                            onClick = { onUpdateVisualConfig(visualConfig.copy(defaultOpenMode = modeOpt)) },
                            labelText = TextGridUiStrings.labelForOpenMode(modeOpt),
                            modifier = Modifier.testTag("tg_vis_openmode_${modeOpt.id.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 2) Time scale: Adaptive Elastic / 1:1 Linear
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tgVis.prefScaleMode,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextGridScaleMode.entries.forEach { mode ->
                        StudioFilterChip(
                            selected = visualConfig.scaleMode == mode,
                            onClick = { onUpdateVisualConfig(visualConfig.copy(scaleMode = mode)) },
                            labelText = TextGridUiStrings.labelForScaleMode(mode),
                            modifier = Modifier.testTag("tg_vis_scale_${mode.id.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 3) Layout wrap: Auto Wrap / Single Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tgVis.prefWrapMode,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextGridWrapMode.entries.forEach { mode ->
                        StudioFilterChip(
                            selected = visualConfig.wrapMode == mode,
                            onClick = { onUpdateVisualConfig(visualConfig.copy(wrapMode = mode)) },
                            labelText = TextGridUiStrings.labelForWrapMode(mode),
                            modifier = Modifier.testTag("tg_vis_wrap_${mode.id.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 4) Tick precision: 3 dec / 4 dec / Hidden
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tgVis.prefTickPrecision,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TextGridTickPrecision.P3,
                        TextGridTickPrecision.P4,
                        TextGridTickPrecision.NONE
                    ).forEach { prec ->
                        StudioFilterChip(
                            selected = visualConfig.tickPrecision == prec,
                            onClick = { onUpdateVisualConfig(visualConfig.copy(tickPrecision = prec)) },
                            labelText = TextGridUiStrings.labelForTickPrecision(prec),
                            modifier = Modifier.testTag("tg_vis_tick_${prec.id.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 5) Whitespace & IPA gloss hint switch
            StudioSwitchRow(
                title = tgVis.prefShowWhitespace,
                subtitle = tgVis.prefShowWhitespaceDesc,
                checked = visualConfig.showRichWhitespace,
                onCheckedChange = { onUpdateVisualConfig(visualConfig.copy(showRichWhitespace = it)) }
            )

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 6) Score palette: Follow Theme / Warm Paper / Academic B&W
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tgVis.prefScoreTheme,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextGridScoreTheme.entries.forEach { themeOpt ->
                        StudioFilterChip(
                            selected = visualConfig.scoreTheme == themeOpt,
                            onClick = { onUpdateVisualConfig(visualConfig.copy(scoreTheme = themeOpt)) },
                            labelText = TextGridUiStrings.labelForScoreTheme(themeOpt),
                            modifier = Modifier.testTag("tg_vis_theme_${themeOpt.id.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextGridPresetCard(
    preset: TextGridPreset,
    selected: Boolean,
    customActiveTiers: List<String>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val cardBg by animateColorAsState(
        targetValue = when {
            selected -> lerp(colorScheme.surface, colorScheme.primaryContainer, if (isHovered) 0.62f else 0.48f)
            isHovered -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.22f)
            else -> colorScheme.surface
        },
        animationSpec = tween(140),
        label = "TgPresetCardBg"
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            selected -> colorScheme.primary
            isHovered -> colorScheme.primary.copy(alpha = 0.50f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.60f)
        },
        animationSpec = tween(140),
        label = "TgPresetCardBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 560f),
        label = "TgPresetCardScale"
    )

    val icon: ImageVector = when (preset) {
        TextGridPreset.UNIVERSAL_STANDARD -> Icons.Default.Verified
        TextGridPreset.MFA_WORDS_PHONES -> Icons.Default.Translate
        TextGridPreset.CHINESE_DIALECT -> Icons.Default.RecordVoiceOver
        TextGridPreset.PROSODY_SYLLABLES -> Icons.Default.LinearScale
        TextGridPreset.PHONTRACER_COMPAT -> Icons.Default.Tune
        TextGridPreset.CUSTOM -> Icons.Default.DashboardCustomize
    }

    val tierPreviewNames = customActiveTiers ?: remember(preset) {
        TextGridFormatConfig.fromPreset(preset).activeTierNames()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(if (selected) 1.6.dp else 1.dp, borderCol),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("tg_preset_${preset.id.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) colorScheme.primary else colorScheme.secondaryContainer)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = preset.title,
                            fontSize = 13.5.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (selected) {
                                colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                colorScheme.surfaceContainerHighest
                            }
                        ) {
                            Text(
                                text = preset.badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) colorScheme.primary else colorScheme.surface)
                        .border(
                            width = 1.4.dp,
                            color = if (selected) colorScheme.primary else colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = S.textGrid.selectedCheckDesc,
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                tierPreviewNames.forEachIndexed { idx, tName ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (selected) {
                            colorScheme.surface.copy(alpha = 0.82f)
                        } else {
                            colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = 0.8.dp,
                            color = if (selected) {
                                colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Text(
                            text = "${idx + 1}.$tName",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = preset.description,
                fontSize = 11.5.sp,
                lineHeight = 16.5.sp,
                color = if (selected) {
                    colorScheme.onPrimaryContainer.copy(alpha = 0.86f)
                } else {
                    colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun CustomTierConfigRow(
    title: String,
    sampleHint: String,
    checked: Boolean,
    canUncheck: Boolean,
    tierName: String,
    defaultTierName: String,
    onCheckedChange: (Boolean) -> Unit,
    onTierNameChange: (String) -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) {
            lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.25f)
        } else {
            colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) colorScheme.primary.copy(alpha = 0.45f) else colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canUncheck) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCheckedChange(!checked) }
                        } else {
                            Modifier
                        }
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (checked) colorScheme.onSurface else colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = sampleHint,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                StudioSwitch(
                    checked = checked,
                    enabled = canUncheck,
                    onCheckedChange = onCheckedChange
                )
            }

            AnimatedVisibility(
                visible = checked,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = tierName,
                        onValueChange = onTierNameChange,
                        label = { Text(S.textGrid.tierNameFieldLabel.withArgs("name" to defaultTierName), fontSize = 12.sp) },
                        placeholder = { Text(defaultTierName, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    extraContent?.invoke()
                }
            }
        }
    }
}
