package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PredictiveBackGestureHandler
import com.example.ui.components.predictiveBackLayer
import com.example.ui.components.rememberPredictiveBackVisualState
import com.example.utils.TextGridFormatConfig

/**
 * Dedicated full-screen Export Page replacing the cramped modal AlertDialog.
 * Renders all export formats as icon-equipped Material You cards in a single horizontal row,
 * with responsive two-column layout adaptation for Desktop & Tablet views.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectExportScreen(
    projectName: String,
    currentSpeakerName: String,
    isSimpleMode: Boolean,
    currentRecordedCount: Int,
    totalProjectRecordings: Int,
    startedSpeakersCount: Int,
    selectedExportFormat: Int,
    onSelectExportFormat: (Int) -> Unit,
    simpleExportPraatCollection: Boolean,
    onSimpleExportPraatCollectionChange: (Boolean) -> Unit,
    optExportAllSpeakers: Boolean,
    onExportAllSpeakersChange: (Boolean) -> Unit,
    optPraatCollectionBinary: Boolean,
    onPraatCollectionBinaryChange: (Boolean) -> Unit,
    optPraatSummaryTable: Boolean,
    onPraatSummaryTableChange: (Boolean) -> Unit,
    optAudio: Boolean,
    onAudioChange: (Boolean) -> Unit,
    optPraatTextGrid: Boolean,
    onPraatTextGridChange: (Boolean) -> Unit,
    optSimplePraatTextGrid: Boolean,
    onSimplePraatTextGridChange: (Boolean) -> Unit,
    optTgAcousticTags: Boolean,
    onTgAcousticTagsChange: (Boolean) -> Unit,
    optAnalysis: Boolean,
    onAnalysisChange: (Boolean) -> Unit,
    optWords: Boolean,
    onWordsChange: (Boolean) -> Unit,
    optSpeaker: Boolean,
    onSpeakerChange: (Boolean) -> Unit,
    optExpr: Boolean,
    onExprChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSaveLocal: () -> Unit,
    onShareExport: () -> Unit,
    hasWordImages: Boolean = false,
    optWordImages: Boolean = true,
    onWordImagesChange: (Boolean) -> Unit = {},
    optIncludeBackupTakes: Boolean = false,
    onIncludeBackupTakesChange: (Boolean) -> Unit = {},
    textGridConfig: TextGridFormatConfig = TextGridFormatConfig(),
    modifier: Modifier = Modifier
) {
    val backVisualState = rememberPredictiveBackVisualState()
    PredictiveBackGestureHandler(
        enabled = true,
        state = backVisualState,
        onBack = onBack
    )

    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
    val colorScheme = MaterialTheme.colorScheme
    val targetRecordingsCount = if (optExportAllSpeakers) totalProjectRecordings else currentRecordedCount
    val usePraatCol = if (isSimpleMode) simpleExportPraatCollection else selectedExportFormat == 1
    val useTeproj = !isSimpleMode && selectedExportFormat == 2

    val safeProjName = projectName.ifBlank { S.export.defaultExportProjectTag }.replace("\\s+".toRegex(), "_")
    val scopeLabel = if (optExportAllSpeakers) {
        S.export.defaultExportAllSpeakersTag
    } else {
        currentSpeakerName.ifBlank { S.export.defaultExportSpeakerTag }.replace("\\s+".toRegex(), "_")
    }
    val ext = when {
        usePraatCol -> "Collection"
        useTeproj -> "teproj"
        else -> "zip"
    }
    val previewFileName = "${safeProjName}_${scopeLabel}.$ext"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = S.export.screenTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (projectName.isNotBlank()) {
                            Text(
                                text = projectName,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("export_page_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = S.export.backToArchiveDesc
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isSimpleMode) S.export.modeSimpleBadge else S.export.modeAdvBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                color = colorScheme.surface,
                shadowElevation = 0.dp,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDesktop) {
                            Row(
                                modifier = Modifier
                                    .widthIn(max = 980.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f).padding(end = 16.dp)
                                ) {
                                    Text(
                                        text = S.export.outputFileNamePrefix,
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = previewFileName,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = onBack,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text(S.export.backToArchiveDesc, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                    }
                                    Button(
                                        onClick = onSaveLocal,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(44.dp)
                                            .testTag("confirm_share_export_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isSimpleMode) {
                                                S.export.saveToComputerExt.withArgs("ext" to ext)
                                            } else {
                                                S.export.exportLocalExt.withArgs("ext" to ext)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .widthIn(max = 980.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onSaveLocal,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_local_export_button")
                                ) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(S.export.saveAsLocal, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                }
                                Button(
                                    onClick = onShareExport,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .height(48.dp)
                                        .testTag("confirm_share_export_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            usePraatCol -> S.export.shareCollectionBtn
                                            isSimpleMode -> S.export.shareZipPackageBtn
                                            useTeproj -> S.export.shareTeprojBtn
                                            else -> S.export.shareZipExtBtn
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .predictiveBackLayer(backVisualState)
            .testTag("project_export_screen")
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideLayout = maxWidth >= 700.dp
            val isMediumScopeWidth = maxWidth >= 520.dp
            val horizontalPad = if (isWideLayout) 24.dp else 14.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 980.dp)
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = if (isWideLayout) 18.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isWideLayout) 16.dp else 12.dp)
                ) {
                    // 1. Overview Status Banner
                    ExportStatusBanner(
                        targetRecordingsCount = targetRecordingsCount,
                        isSimpleMode = isSimpleMode,
                        usePraatCol = usePraatCol,
                        useTeproj = useTeproj,
                        previewFileName = previewFileName,
                        isWideLayout = isWideLayout
                    )

                    // 2. Single-Row Format Cards (Strictly 1 horizontal Row on both Phone and Desktop)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isSimpleMode) S.export.sectionMethodSimple else S.export.sectionMethodAdv,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(if (isWideLayout) 14.dp else 8.dp)
                        ) {
                            if (isSimpleMode) {
                                ExportFormatCard(
                                    selected = !simpleExportPraatCollection,
                                    onClick = { onSimpleExportPraatCollectionChange(false) },
                                    icon = Icons.Default.FolderZip,
                                    title = S.export.simpleZipTitle,
                                    extensionBadge = ".zip",
                                    description = if (isWideLayout) {
                                        S.export.simpleZipDescWide
                                    } else {
                                        S.export.simpleZipDescCompact
                                    },
                                    isWideLayout = isWideLayout,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("simple_export_format_zip_chip")
                                )
                                ExportFormatCard(
                                    selected = simpleExportPraatCollection,
                                    onClick = { onSimpleExportPraatCollectionChange(true) },
                                    icon = Icons.Default.GraphicEq,
                                    title = S.export.praatTitle,
                                    extensionBadge = ".Collection",
                                    description = if (isWideLayout) {
                                        S.export.simplePraatDescWide
                                    } else {
                                        S.export.simplePraatDescCompact
                                    },
                                    isWideLayout = isWideLayout,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("simple_export_format_praat_chip")
                                )
                            } else {
                                ExportFormatCard(
                                    selected = selectedExportFormat == 0,
                                    onClick = { onSelectExportFormat(0) },
                                    icon = Icons.Default.FolderZip,
                                    title = S.export.advZipTitle,
                                    extensionBadge = ".zip",
                                    description = if (isWideLayout) {
                                        S.export.advZipDescWide
                                    } else {
                                        S.export.advZipDescCompact
                                    },
                                    isWideLayout = isWideLayout,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("export_format_zip_chip")
                                )
                                ExportFormatCard(
                                    selected = selectedExportFormat == 1,
                                    onClick = { onSelectExportFormat(1) },
                                    icon = Icons.Default.GraphicEq,
                                    title = S.export.praatTitle,
                                    extensionBadge = ".Collection",
                                    description = if (isWideLayout) {
                                        S.export.advPraatDescWide
                                    } else {
                                        S.export.advPraatDescCompact
                                    },
                                    isWideLayout = isWideLayout,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("export_format_praat_collection_chip")
                                )
                                ExportFormatCard(
                                    selected = selectedExportFormat == 2,
                                    onClick = { onSelectExportFormat(2) },
                                    icon = Icons.Default.Tune,
                                    title = if (isWideLayout) S.export.phonTracerTitleWide else S.export.phonTracerTitleCompact,
                                    extensionBadge = ".teproj",
                                    description = if (isWideLayout) {
                                        S.export.phonTracerDescWide
                                    } else {
                                        S.export.phonTracerDescCompact
                                    },
                                    isWideLayout = isWideLayout,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("export_format_teproj_chip")
                                )
                            }
                        }
                    }

                    // 3. Adaptive Configuration Section (2 Columns on Desktop/Tablet, Stacked on Phone)
                    if (isWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(0.95f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                SpeakerScopeSectionCard(
                                    currentSpeakerName = currentSpeakerName,
                                    currentRecordedCount = currentRecordedCount,
                                    startedSpeakersCount = startedSpeakersCount,
                                    totalProjectRecordings = totalProjectRecordings,
                                    optExportAllSpeakers = optExportAllSpeakers,
                                    onExportAllSpeakersChange = onExportAllSpeakersChange,
                                    sideBySide = false
                                )
                                ExportStructurePreviewCard(
                                    isSimpleMode = isSimpleMode,
                                    usePraatCol = usePraatCol,
                                    useTeproj = useTeproj,
                                    optPraatCollectionBinary = optPraatCollectionBinary,
                                    optPraatSummaryTable = optPraatSummaryTable,
                                    optPraatTextGrid = if (isSimpleMode) optSimplePraatTextGrid else optPraatTextGrid,
                                    includeWordImages = hasWordImages && optWordImages,
                                    includeBackupTakes = !isSimpleMode && !usePraatCol && !useTeproj && optIncludeBackupTakes,
                                    textGridConfig = textGridConfig
                                )
                            }

                            Box(modifier = Modifier.weight(1.25f)) {
                                ExportDetailedOptionsCard(
                                    isSimpleMode = isSimpleMode,
                                    isWideLayout = true,
                                    selectedExportFormat = selectedExportFormat,
                                    simpleExportPraatCollection = simpleExportPraatCollection,
                                    optPraatCollectionBinary = optPraatCollectionBinary,
                                    onPraatCollectionBinaryChange = onPraatCollectionBinaryChange,
                                    optPraatSummaryTable = optPraatSummaryTable,
                                    onPraatSummaryTableChange = onPraatSummaryTableChange,
                                    optAudio = optAudio,
                                    onAudioChange = onAudioChange,
                                    optPraatTextGrid = optPraatTextGrid,
                                    onPraatTextGridChange = onPraatTextGridChange,
                                    optSimplePraatTextGrid = optSimplePraatTextGrid,
                                    onSimplePraatTextGridChange = onSimplePraatTextGridChange,
                                    optTgAcousticTags = optTgAcousticTags,
                                    onTgAcousticTagsChange = onTgAcousticTagsChange,
                                    optAnalysis = optAnalysis,
                                    onAnalysisChange = onAnalysisChange,
                                    optWords = optWords,
                                    onWordsChange = onWordsChange,
                                    optSpeaker = optSpeaker,
                                    onSpeakerChange = onSpeakerChange,
                                    optExpr = optExpr,
                                    onExprChange = onExprChange,
                                    hasWordImages = hasWordImages,
                                    optWordImages = optWordImages,
                                    onWordImagesChange = onWordImagesChange,
                                    optIncludeBackupTakes = optIncludeBackupTakes,
                                    onIncludeBackupTakesChange = onIncludeBackupTakesChange,
                                    textGridConfig = textGridConfig
                                )
                            }
                        }
                    } else {
                        SpeakerScopeSectionCard(
                            currentSpeakerName = currentSpeakerName,
                            currentRecordedCount = currentRecordedCount,
                            startedSpeakersCount = startedSpeakersCount,
                            totalProjectRecordings = totalProjectRecordings,
                            optExportAllSpeakers = optExportAllSpeakers,
                            onExportAllSpeakersChange = onExportAllSpeakersChange,
                            sideBySide = isMediumScopeWidth
                        )
                        ExportDetailedOptionsCard(
                            isSimpleMode = isSimpleMode,
                            isWideLayout = false,
                            selectedExportFormat = selectedExportFormat,
                            simpleExportPraatCollection = simpleExportPraatCollection,
                            optPraatCollectionBinary = optPraatCollectionBinary,
                            onPraatCollectionBinaryChange = onPraatCollectionBinaryChange,
                            optPraatSummaryTable = optPraatSummaryTable,
                            onPraatSummaryTableChange = onPraatSummaryTableChange,
                            optAudio = optAudio,
                            onAudioChange = onAudioChange,
                            optPraatTextGrid = optPraatTextGrid,
                            onPraatTextGridChange = onPraatTextGridChange,
                            optSimplePraatTextGrid = optSimplePraatTextGrid,
                            onSimplePraatTextGridChange = onSimplePraatTextGridChange,
                            optTgAcousticTags = optTgAcousticTags,
                            onTgAcousticTagsChange = onTgAcousticTagsChange,
                            optAnalysis = optAnalysis,
                            onAnalysisChange = onAnalysisChange,
                            optWords = optWords,
                            onWordsChange = onWordsChange,
                            optSpeaker = optSpeaker,
                            onSpeakerChange = onSpeakerChange,
                            optExpr = optExpr,
                            onExprChange = onExprChange,
                            hasWordImages = hasWordImages,
                            optWordImages = optWordImages,
                            onWordImagesChange = onWordImagesChange,
                            optIncludeBackupTakes = optIncludeBackupTakes,
                            onIncludeBackupTakesChange = onIncludeBackupTakesChange,
                            textGridConfig = textGridConfig
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ExportStatusBanner(
    targetRecordingsCount: Int,
    isSimpleMode: Boolean,
    usePraatCol: Boolean,
    useTeproj: Boolean,
    previewFileName: String,
    isWideLayout: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasRecordings = targetRecordingsCount > 0
    val bannerBg = if (hasRecordings) {
        lerp(colorScheme.surface, colorScheme.primaryContainer, 0.58f)
    } else {
        lerp(colorScheme.surface, colorScheme.errorContainer, 0.62f)
    }
    val bannerBorder = if (hasRecordings) {
        colorScheme.primary.copy(alpha = 0.28f)
    } else {
        colorScheme.error.copy(alpha = 0.32f)
    }
    val contentCol = if (hasRecordings) colorScheme.onPrimaryContainer else colorScheme.onErrorContainer

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bannerBg,
        border = BorderStroke(1.dp, bannerBorder),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isWideLayout) 18.dp else 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (hasRecordings) colorScheme.primary else colorScheme.error)
            ) {
                Icon(
                    imageVector = if (hasRecordings) Icons.Default.Inventory2 else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (hasRecordings) colorScheme.onPrimary else colorScheme.onError,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (hasRecordings) {
                            if (isSimpleMode) {
                                S.export.readyCountSimple.withArgs("count" to targetRecordingsCount)
                            } else {
                                S.export.readyCountAdv.withArgs("count" to targetRecordingsCount)
                            }
                        } else {
                            S.export.noAudioInScopeTitle
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentCol
                    )
                    if (!DesktopAdaptiveState.isDesktopPlatform && isWideLayout) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorScheme.surface.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = previewFileName,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = contentCol,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (!hasRecordings) {
                        S.export.noAudioInScopeHint
                    } else {
                        when {
                            usePraatCol -> S.export.formatSummaryPraatCol
                            useTeproj -> S.export.formatSummaryTeproj
                            else -> S.export.formatSummaryZip
                        }
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = contentCol.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun ExportFormatCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    extensionBadge: String,
    description: String,
    isWideLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val cardBg by animateColorAsState(
        targetValue = when {
            selected -> lerp(colorScheme.surface, colorScheme.primaryContainer, if (isHovered) 0.72f else 0.58f)
            isHovered -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.28f)
            else -> colorScheme.surfaceContainerLow
        },
        animationSpec = tween(150),
        label = "FormatCardBg"
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            selected -> colorScheme.primary
            isHovered -> colorScheme.primary.copy(alpha = 0.55f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.65f)
        },
        animationSpec = tween(150),
        label = "FormatCardBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 580f),
        label = "FormatCardScale"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(if (selected) 1.8.dp else 1.dp, borderCol),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isWideLayout) 15.dp else 10.dp,
                    vertical = if (isWideLayout) 14.dp else 11.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (isWideLayout) 8.dp else 5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(if (isWideLayout) 38.dp else 30.dp)
                            .clip(RoundedCornerShape(if (isWideLayout) 11.dp else 9.dp))
                            .background(
                                if (selected) colorScheme.primary else colorScheme.secondaryContainer
                            )
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(if (isWideLayout) 20.dp else 17.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isWideLayout) {
                            ExtensionPill(extensionBadge = extensionBadge, selected = selected)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(if (isWideLayout) 20.dp else 18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) colorScheme.primary else colorScheme.surface
                                )
                                .border(
                                    width = 1.3.dp,
                                    color = if (selected) colorScheme.primary else colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = S.export.selectedBadgeDesc,
                                    tint = colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = title,
                    fontSize = if (isWideLayout) 15.sp else 12.5.sp,
                    lineHeight = if (isWideLayout) 19.sp else 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                )

                if (!isWideLayout) {
                    ExtensionPill(extensionBadge = extensionBadge, selected = selected)
                }
            }

            Spacer(modifier = Modifier.height(if (isWideLayout) 8.dp else 5.dp))

            Text(
                text = description,
                fontSize = if (isWideLayout) 12.sp else 10.5.sp,
                lineHeight = if (isWideLayout) 16.5.sp else 14.sp,
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
private fun ExtensionPill(extensionBadge: String, selected: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) {
            colorScheme.primary.copy(alpha = 0.14f)
        } else {
            colorScheme.surfaceContainerHighest
        }
    ) {
        Text(
            text = extensionBadge,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SpeakerScopeSectionCard(
    currentSpeakerName: String,
    currentRecordedCount: Int,
    startedSpeakersCount: Int,
    totalProjectRecordings: Int,
    optExportAllSpeakers: Boolean,
    onExportAllSpeakersChange: (Boolean) -> Unit,
    sideBySide: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = S.export.speakerScopeHeader,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            if (sideBySide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScopeChoiceTile(
                        selected = !optExportAllSpeakers,
                        onClick = { onExportAllSpeakersChange(false) },
                        icon = Icons.Default.Person,
                        title = currentSpeakerName.ifBlank { S.export.currentSpeakerShortTitle },
                        subtitle = S.export.speakerRecordedCountSub.withArgs("count" to currentRecordedCount),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ScopeChoiceTile(
                        selected = optExportAllSpeakers,
                        onClick = { onExportAllSpeakersChange(true) },
                        icon = Icons.Default.Group,
                        title = S.export.allRecordedSpeakersTitle,
                        subtitle = S.export.allSpeakersSummarySub.withArgs(
                            "speakers" to startedSpeakersCount,
                            "recordings" to totalProjectRecordings
                        ),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScopeChoiceTile(
                        selected = !optExportAllSpeakers,
                        onClick = { onExportAllSpeakersChange(false) },
                        icon = Icons.Default.Person,
                        title = S.export.currentSpeakerFullTitle.withArgs(
                            "name" to currentSpeakerName.ifBlank { S.export.defaultSpeakerFallbackName }
                        ),
                        subtitle = S.export.currentSpeakerFullSub.withArgs("count" to currentRecordedCount),
                        modifier = Modifier.fillMaxWidth()
                    )
                    ScopeChoiceTile(
                        selected = optExportAllSpeakers,
                        onClick = { onExportAllSpeakersChange(true) },
                        icon = Icons.Default.Group,
                        title = S.export.allSpeakersFullTitle.withArgs("count" to startedSpeakersCount),
                        subtitle = S.export.allSpeakersFullSub.withArgs("recordings" to totalProjectRecordings),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val colorScheme = MaterialTheme.colorScheme

    val bg by animateColorAsState(
        targetValue = when {
            selected -> lerp(colorScheme.surface, colorScheme.primaryContainer, 0.50f)
            isHovered -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.24f)
            else -> colorScheme.surfaceContainerLow
        },
        animationSpec = tween(140),
        label = "ScopeTileBg"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shadowElevation = 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExportStructurePreviewCard(
    isSimpleMode: Boolean,
    usePraatCol: Boolean,
    useTeproj: Boolean,
    optPraatCollectionBinary: Boolean,
    optPraatSummaryTable: Boolean,
    optPraatTextGrid: Boolean,
    includeWordImages: Boolean = false,
    includeBackupTakes: Boolean = false,
    textGridConfig: TextGridFormatConfig
) {
    val colorScheme = MaterialTheme.colorScheme
    val tgSummary = textGridConfig.shortSummaryLabel()
    val items = when {
        usePraatCol -> buildList {
            add(if (optPraatCollectionBinary) S.export.treePraatBinaryHeader else S.export.treePraatTextHeader)
            add(S.export.treePraatSoundAndTg.withArgs("summary" to tgSummary))
            if (!isSimpleMode && optPraatSummaryTable) {
                add(S.export.treePraatSummaryTable)
            }
        }
        useTeproj -> buildList {
            add(S.export.treeTeprojJson)
            add(S.export.treeTeprojWav)
            if (optPraatTextGrid) add(S.export.treeTeprojTg)
            if (includeWordImages) add(S.export.treeTeprojImages)
        }
        isSimpleMode -> buildList {
            add(S.export.treeSimpleWavDir)
            if (optPraatTextGrid) add(S.export.treeSimpleTgDir.withArgs("summary" to tgSummary))
            if (includeWordImages) add(S.export.treeSimpleImagesDir)
            add(S.export.treeSimpleCsv)
        }
        else -> buildList {
            add(S.export.treeAdvWavAndTg.withArgs("summary" to tgSummary))
            if (includeBackupTakes) add(S.export.treeAdvTakesDir)
            add(S.export.treeAdvCsv)
            add(S.export.treeAdvMeta)
            if (includeWordImages) add(S.export.treeAdvImages)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = S.export.artifactTreeHeader,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            items.forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(colorScheme.primary, CircleShape)
                    )
                    Text(
                        text = line,
                        fontSize = 11.5.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class ExportOptionSpec(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val lockedBadgeText: String? = null,
    val isRadioChoice: Boolean = false,
    val testTag: String? = null,
    val onToggle: (() -> Unit)? = null
)

@Composable
private fun ExportDetailedOptionsCard(
    isSimpleMode: Boolean,
    isWideLayout: Boolean,
    selectedExportFormat: Int,
    simpleExportPraatCollection: Boolean,
    optPraatCollectionBinary: Boolean,
    onPraatCollectionBinaryChange: (Boolean) -> Unit,
    optPraatSummaryTable: Boolean,
    onPraatSummaryTableChange: (Boolean) -> Unit,
    optAudio: Boolean,
    onAudioChange: (Boolean) -> Unit,
    optPraatTextGrid: Boolean,
    onPraatTextGridChange: (Boolean) -> Unit,
    optSimplePraatTextGrid: Boolean,
    onSimplePraatTextGridChange: (Boolean) -> Unit,
    optTgAcousticTags: Boolean,
    onTgAcousticTagsChange: (Boolean) -> Unit,
    optAnalysis: Boolean,
    onAnalysisChange: (Boolean) -> Unit,
    optWords: Boolean,
    onWordsChange: (Boolean) -> Unit,
    optSpeaker: Boolean,
    onSpeakerChange: (Boolean) -> Unit,
    optExpr: Boolean,
    onExprChange: (Boolean) -> Unit,
    hasWordImages: Boolean = false,
    optWordImages: Boolean = true,
    onWordImagesChange: (Boolean) -> Unit = {},
    optIncludeBackupTakes: Boolean = false,
    onIncludeBackupTakesChange: (Boolean) -> Unit = {},
    textGridConfig: TextGridFormatConfig
) {
    val colorScheme = MaterialTheme.colorScheme
    val tgSummary = textGridConfig.shortSummaryLabel()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.export.configSectionTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = S.export.configToggleHint,
                    fontSize = 11.sp,
                    color = colorScheme.outline
                )
            }

            val modeKey = if (isSimpleMode) {
                if (simpleExportPraatCollection) "SIMPLE_PRAAT" else "SIMPLE_ZIP"
            } else {
                "ADV_$selectedExportFormat"
            }

            val isTgRelevant = when (modeKey) {
                "SIMPLE_PRAAT" -> true
                "SIMPLE_ZIP" -> optSimplePraatTextGrid
                "ADV_1" -> optPraatTextGrid
                "ADV_2" -> false // teproj uses PhonTracer 4-tier schema
                else -> optPraatTextGrid
            }
            if (isTgRelevant) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = S.export.currentTgFormatNote.withArgs(
                            "preset" to textGridConfig.preset.title,
                            "summary" to tgSummary
                        ),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = modeKey,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "ExportOptionsAnimatedSwitch"
            ) { key ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (key) {
                        "SIMPLE_PRAAT" -> {
                            ExportOptionCardsGrid(
                                useTwoColumns = false,
                                items = listOf(
                                    ExportOptionSpec(
                                        icon = Icons.Default.GraphicEq,
                                        title = S.export.lockedCardSoundAndTgTitle.withArgs("summary" to tgSummary),
                                        description = S.export.lockedCardSoundAndTgDesc,
                                        selected = true,
                                        enabled = false,
                                        lockedBadgeText = S.export.lockedBadgeDefaultBuiltIn
                                    )
                                )
                            )
                        }

                        "SIMPLE_ZIP" -> {
                            val simpleZipItems = buildList {
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.GraphicEq,
                                        title = S.export.lockedCardSimpleAudioCsvTitle,
                                        description = S.export.lockedCardSimpleAudioCsvDesc,
                                        selected = true,
                                        enabled = false,
                                        lockedBadgeText = S.export.lockedBadgeDefaultBuiltIn
                                    )
                                )
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.EditNote,
                                        title = S.export.toggleSimpleTextGridTitle,
                                        description = S.export.toggleSimpleTextGridDesc.withArgs("summary" to tgSummary),
                                        selected = optSimplePraatTextGrid,
                                        testTag = "simple_export_praat_checkbox",
                                        onToggle = { onSimplePraatTextGridChange(!optSimplePraatTextGrid) }
                                    )
                                )
                                if (hasWordImages) {
                                    add(
                                        ExportOptionSpec(
                                            icon = Icons.Default.Image,
                                            title = S.export.simpleIncludeImagesTitle,
                                            description = S.export.simpleIncludeImagesDesc,
                                            selected = optWordImages,
                                            testTag = "export_word_images_checkbox",
                                            onToggle = { onWordImagesChange(!optWordImages) }
                                        )
                                    )
                                }
                            }
                            ExportOptionCardsGrid(
                                useTwoColumns = isWideLayout,
                                items = simpleZipItems
                            )
                        }

                        "ADV_1" -> {
                            Text(
                                text = S.export.praatEncodingHeader,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            ExportOptionCardsGrid(
                                useTwoColumns = isWideLayout,
                                items = listOf(
                                    ExportOptionSpec(
                                        icon = Icons.Default.Storage,
                                        title = S.export.praatBinaryTitle,
                                        description = S.export.praatBinaryDesc,
                                        selected = optPraatCollectionBinary,
                                        isRadioChoice = true,
                                        testTag = "praat_collection_binary_chip",
                                        onToggle = { onPraatCollectionBinaryChange(true) }
                                    ),
                                    ExportOptionSpec(
                                        icon = Icons.Default.Description,
                                        title = S.export.praatTextTitle,
                                        description = S.export.praatTextDesc,
                                        selected = !optPraatCollectionBinary,
                                        isRadioChoice = true,
                                        testTag = "praat_collection_text_chip",
                                        onToggle = { onPraatCollectionBinaryChange(false) }
                                    )
                                )
                            )

                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = S.export.praatBuiltInObjectsHeader,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            val colItems = buildList {
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.GraphicEq,
                                        title = S.export.objSound2Title,
                                        description = S.export.objSound2Desc,
                                        selected = optAudio,
                                        onToggle = {
                                            val next = !optAudio
                                            if (next || optPraatTextGrid || optPraatSummaryTable) {
                                                onAudioChange(next)
                                            }
                                        }
                                    )
                                )
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.EditNote,
                                        title = S.export.objTextGridTitle,
                                        description = S.export.objTextGridDesc.withArgs("summary" to tgSummary),
                                        selected = optPraatTextGrid,
                                        onToggle = {
                                            val next = !optPraatTextGrid
                                            if (next || optAudio || optPraatSummaryTable) {
                                                onPraatTextGridChange(next)
                                            }
                                        }
                                    )
                                )
                                if (optPraatTextGrid) {
                                    add(
                                        ExportOptionSpec(
                                            icon = Icons.Default.Analytics,
                                            title = S.export.objAcousticTagsTitle,
                                            description = S.export.objAcousticTagsDesc,
                                            selected = optTgAcousticTags,
                                            onToggle = { onTgAcousticTagsChange(!optTgAcousticTags) }
                                        )
                                    )
                                }
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.PieChart,
                                        title = S.export.objPraatTableTitle,
                                        description = S.export.objPraatTableDesc,
                                        selected = optPraatSummaryTable,
                                        onToggle = {
                                            val next = !optPraatSummaryTable
                                            if (next || optAudio || optPraatTextGrid) {
                                                onPraatSummaryTableChange(next)
                                            }
                                        }
                                    )
                                )
                            }
                            ExportOptionCardsGrid(
                                useTwoColumns = isWideLayout,
                                items = colItems
                            )
                        }

                        "ADV_2" -> {
                            val teprojItems = buildList {
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.Tune,
                                        title = S.export.teprojManifestTitle,
                                        description = S.export.teprojManifestDesc,
                                        selected = true,
                                        enabled = false,
                                        lockedBadgeText = S.export.lockedBadgeCoreRequired
                                    )
                                )
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.EditNote,
                                        title = S.export.teprojFourTierTitle,
                                        description = S.export.teprojFourTierDesc,
                                        selected = optPraatTextGrid,
                                        onToggle = { onPraatTextGridChange(!optPraatTextGrid) }
                                    )
                                )
                                if (optPraatTextGrid) {
                                    add(
                                        ExportOptionSpec(
                                            icon = Icons.Default.Analytics,
                                            title = S.export.objAcousticTagsTitle,
                                            description = S.export.teprojAcousticTagsDesc,
                                            selected = optTgAcousticTags,
                                            onToggle = { onTgAcousticTagsChange(!optTgAcousticTags) }
                                        )
                                    )
                                }
                                if (hasWordImages) {
                                    add(
                                        ExportOptionSpec(
                                            icon = Icons.Default.Image,
                                            title = S.export.includeImagesTitle,
                                            description = S.export.includeImagesDesc,
                                            selected = optWordImages,
                                            testTag = "export_word_images_checkbox",
                                            onToggle = { onWordImagesChange(!optWordImages) }
                                        )
                                    )
                                }
                            }
                            ExportOptionCardsGrid(
                                useTwoColumns = isWideLayout,
                                items = teprojItems
                            )
                        }

                        else -> {
                            val zipItems = buildList {
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.GraphicEq,
                                        title = S.export.advWavTitle,
                                        description = S.export.advWavDesc,
                                        selected = optAudio,
                                        onToggle = { onAudioChange(!optAudio) }
                                    )
                                )
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.EditNote,
                                        title = S.export.advTgTitle,
                                        description = S.export.advTgDesc.withArgs("summary" to tgSummary),
                                        selected = optPraatTextGrid,
                                        onToggle = { onPraatTextGridChange(!optPraatTextGrid) }
                                    )
                                )
                                if (optPraatTextGrid) {
                                    add(
                                        ExportOptionSpec(
                                            icon = Icons.Default.Analytics,
                                            title = S.export.objAcousticTagsTitle,
                                            description = S.export.objAcousticTagsDesc,
                                            selected = optTgAcousticTags,
                                            onToggle = { onTgAcousticTagsChange(!optTgAcousticTags) }
                                        )
                                    )
                                }
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.PieChart,
                                        title = S.export.advCsvTitle,
                                        description = S.export.advCsvDesc,
                                        selected = optAnalysis,
                                        onToggle = { onAnalysisChange(!optAnalysis) }
                                    )
                                )
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.Description,
                                        title = S.export.advWordlistTxtTitle,
                                        description = S.export.advWordlistTxtDesc,
                                        selected = optWords,
                                        onToggle = { onWordsChange(!optWords) }
                                    )
                                )
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.Person,
                                        title = S.export.advSpeakerProfileTitle,
                                        description = S.export.advSpeakerProfileDesc,
                                        selected = optSpeaker,
                                        onToggle = { onSpeakerChange(!optSpeaker) }
                                    )
                                )
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.Info,
                                        title = S.export.advProjectInfoTitle,
                                        description = S.export.advProjectInfoDesc,
                                        selected = optExpr,
                                        onToggle = { onExprChange(!optExpr) }
                                    )
                                )
                                if (hasWordImages) {
                                    add(
                                        ExportOptionSpec(
                                            icon = Icons.Default.Image,
                                            title = S.export.includeImagesTitle,
                                            description = S.export.includeImagesDesc,
                                            selected = optWordImages,
                                            testTag = "export_word_images_checkbox",
                                            onToggle = { onWordImagesChange(!optWordImages) }
                                        )
                                    )
                                }
                                add(
                                    ExportOptionSpec(
                                        icon = Icons.Default.History,
                                        title = S.export.advBackupTakesTitle,
                                        description = S.export.advBackupTakesDesc,
                                        selected = optIncludeBackupTakes,
                                        testTag = "export_backup_takes_checkbox",
                                        onToggle = { onIncludeBackupTakesChange(!optIncludeBackupTakes) }
                                    )
                                )
                            }
                            ExportOptionCardsGrid(
                                useTwoColumns = isWideLayout,
                                items = zipItems
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionCardsGrid(
    useTwoColumns: Boolean,
    items: List<ExportOptionSpec>
) {
    if (!useTwoColumns || items.size <= 1) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { spec ->
                ExportOptionItemCard(
                    spec = spec,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(2).forEach { rowPair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportOptionItemCard(
                        spec = rowPair[0],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    if (rowPair.size == 2) {
                        ExportOptionItemCard(
                            spec = rowPair[1],
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
    }
}

@Composable
private fun ExportOptionItemCard(
    spec: ExportOptionSpec,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val cardBg by animateColorAsState(
        targetValue = when {
            spec.selected && !spec.enabled -> lerp(colorScheme.surfaceContainerLow, colorScheme.secondaryContainer, 0.38f)
            spec.selected -> lerp(colorScheme.surface, colorScheme.primaryContainer, if (isHovered) 0.56f else 0.42f)
            isHovered && spec.enabled -> lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.22f)
            else -> colorScheme.surfaceContainerLow
        },
        animationSpec = tween(140),
        label = "ExportOptCardBg"
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            spec.selected && !spec.enabled -> colorScheme.secondary.copy(alpha = 0.42f)
            spec.selected -> colorScheme.primary.copy(alpha = 0.85f)
            isHovered && spec.enabled -> colorScheme.primary.copy(alpha = 0.45f)
            else -> colorScheme.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = tween(140),
        label = "ExportOptCardBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed && spec.enabled) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 620f),
        label = "ExportOptCardScale"
    )

    val taggedModifier = if (spec.testTag != null) {
        modifier.testTag(spec.testTag)
    } else {
        modifier
    }

    val clickableModifier = if (spec.enabled && spec.onToggle != null) {
        taggedModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(13.dp))
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = spec.onToggle
            )
    } else {
        taggedModifier.clip(RoundedCornerShape(13.dp))
    }

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = cardBg,
        border = BorderStroke(if (spec.selected) 1.4.dp else 1.dp, borderCol),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = clickableModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f).padding(end = 6.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                when {
                                    spec.selected && !spec.enabled -> colorScheme.secondaryContainer
                                    spec.selected -> colorScheme.primary
                                    else -> colorScheme.surfaceContainerHighest
                                }
                            )
                    ) {
                        Icon(
                            imageVector = spec.icon,
                            contentDescription = null,
                            tint = when {
                                spec.selected && !spec.enabled -> colorScheme.onSecondaryContainer
                                spec.selected -> colorScheme.onPrimary
                                else -> colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = spec.title,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (spec.selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (spec.selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                    )
                }

                if (spec.lockedBadgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = spec.lockedBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    val indicatorShape = if (spec.isRadioChoice) CircleShape else RoundedCornerShape(6.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(indicatorShape)
                            .background(if (spec.selected) colorScheme.primary else colorScheme.surface)
                            .border(
                                width = 1.4.dp,
                                color = if (spec.selected) colorScheme.primary else colorScheme.outlineVariant,
                                shape = indicatorShape
                            )
                    ) {
                        if (spec.selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = S.export.enabledBadgeDesc,
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = spec.description,
                fontSize = 11.5.sp,
                lineHeight = 16.5.sp,
                color = if (spec.selected) {
                    colorScheme.onPrimaryContainer.copy(alpha = 0.86f)
                } else {
                    colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}
