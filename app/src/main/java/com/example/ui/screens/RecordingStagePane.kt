package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.Project
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.ResponsiveContentContainer
import com.example.ui.components.StudioHorizontalLazyStrip
import com.example.ui.components.StudioHorizontalScrollStrip
import com.example.ui.components.StudioMotion
import com.example.ui.components.SuspendDesktopShortcutsWhen
import com.example.ui.components.ZoomableWordImage
import com.example.ui.components.rememberStudioInteraction
import com.example.ui.components.studioAnimateItem
import com.example.ui.components.studioAnimateSize
import com.example.ui.components.studioTactile
import com.example.ui.theme.LocalPhonAcqExtendedColors
import com.example.utils.WordImageManager
import com.example.utils.rememberWordImageBitmap
import com.example.viewmodel.RecordingViewModel
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecordingStagePane(
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
    val playbackProgressState = viewModel.playbackProgress.collectAsState()
    val playbackProgressProvider = remember(playbackProgressState) { { playbackProgressState.value } }

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
                playbackProgressProvider = playbackProgressProvider,
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
        val needsVerticalScrollFallback = !isMobileLandscapeStage && maxHeight < 480.dp
        val stageMaxWidth = if (participantCleanMode) 1040.dp else 860.dp

        if (isMobileLandscapeStage) {
            // Mobile Landscape (e.g. 800x360) 2-Column Studio Split: Left Prompt Stage + Right Controls & VU Monitor
            ResponsiveContentContainer(
                maxWidth = stageMaxWidth,
                modifier = Modifier.fillMaxSize()
            ) {
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
                            StudioHorizontalLazyStrip(
                                state = rowListState,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LazyRow(
                                    state = rowListState,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                ) {
                                    itemsIndexed(words, key = { _, w -> w.id }) { index, word ->
                                        val isSelected = index == safeCurrentIndex
                                        WordStripChip(
                                            index = index,
                                            word = word,
                                            isSelected = isSelected,
                                            enablePicturePrompts = enablePicturePrompts,
                                            wavFile = null,
                                            onClick = {
                                                viewModel.selectWordBySequencePosition(index)
                                            },
                                            modifier = studioAnimateItem()
                                        )
                                    }
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
            }
        } else {
            ResponsiveContentContainer(
                maxWidth = stageMaxWidth,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (needsVerticalScrollFallback) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        ),
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

                        StudioHorizontalLazyStrip(
                            state = rowListState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                state = rowListState,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                itemsIndexed(words, key = { _, w -> w.id }) { index, word ->
                                    val isSelected = index == safeCurrentIndex
                                    WordStripChip(
                                        index = index,
                                        word = word,
                                        isSelected = isSelected,
                                        enablePicturePrompts = enablePicturePrompts,
                                        wavFile = null,
                                        onClick = {
                                            viewModel.selectWordBySequencePosition(index)
                                        },
                                        modifier = studioAnimateItem()
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }

                    // 2. Central Prompt Lightbox Stage Card (HorizontalPager)
                    renderPagerContent(
                        if (needsVerticalScrollFallback) {
                            Modifier
                                .fillMaxWidth()
                                .height(216.dp)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        }
                    )

                    Spacer(Modifier.height(if (isCompactStageHeight || needsVerticalScrollFallback) 8.dp else 12.dp))

                    // 3. Isolated Live Studio Monitor (VU dBFS Meter + Timer + Multi-Take Badge + Waveform + VAD Progress)
                    LiveStudioMonitorSection(
                        viewModel = viewModel,
                        currentWord = currentWord,
                        activeProject = activeProject,
                        participantCleanMode = participantCleanMode,
                        isCompact = isCompactStageHeight || needsVerticalScrollFallback,
                        takeCount = currentWordTakeCount,
                        onOpenTakeSwitcher = { wordForTakeSwitcher = currentWord }
                    )

                    Spacer(Modifier.height(if (isCompactStageHeight || needsVerticalScrollFallback) 8.dp else 12.dp))

                    // 4. Primary Transport & Recording Controls
                    RecordingTransportBar(
                        isRecording = isRecording,
                        isPushToTalk = isPushToTalk,
                        canGoPrev = safeCurrentIndex > 0,
                        canGoNext = safeCurrentIndex < words.size - 1,
                        isCompact = isCompactStageHeight || needsVerticalScrollFallback,
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
            var takes by remember(pid, targetWord.id, sid, targetWord.recordingFile, takesRefreshTrigger) {
                mutableStateOf(viewModel.getAvailableTakesForWord(pid, targetWord.id, sid))
            }
            LaunchedEffect(pid, targetWord.id, sid, targetWord.recordingFile, takesRefreshTrigger) {
                takes = withContext(Dispatchers.IO) {
                    viewModel.getAvailableTakesForWord(pid, targetWord.id, sid, forceRescan = true)
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
internal fun HandheldSessionToolbar(
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
    @Suppress("UNUSED_PARAMETER") onRequestClearAll: () -> Unit,
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
                    .pointerHoverIcon(PointerIcon.Hand)
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
                            .pointerHoverIcon(PointerIcon.Hand)
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
                            .pointerHoverIcon(PointerIcon.Hand)
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
                            .pointerHoverIcon(PointerIcon.Hand)
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
                            .pointerHoverIcon(PointerIcon.Hand)
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
                        .pointerHoverIcon(PointerIcon.Hand)
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
                val quickActionsScroll = rememberScrollState()
                StudioHorizontalScrollStrip(
                    state = quickActionsScroll,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickActionButtons(Modifier.fillMaxWidth().horizontalScroll(quickActionsScroll))
                }
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
internal fun WordStripChip(
    index: Int,
    word: Word,
    isSelected: Boolean,
    enablePicturePrompts: Boolean = false,
    @Suppress("UNUSED_PARAMETER") wavFile: File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState
    val isRecorded = word.isRecorded
    val isFlagged = isRecorded && word.qualityStatus != null && word.qualityStatus != "正常"
    val extendedColors = LocalPhonAcqExtendedColors.current

    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isFlagged -> lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.errorContainer,
                if (isHovered) 0.90f else 0.72f
            )
            isHovered -> lerp(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.primaryContainer,
                0.42f
            )
            isRecorded -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = StudioMotion.microTween(),
        label = "WordStripChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            isRecorded -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "WordStripChipBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            isFlagged -> MaterialTheme.colorScheme.onErrorContainer
            isHovered -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = StudioMotion.microTween(),
        label = "WordStripChipText"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
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
                    tint = if (isFlagged) MaterialTheme.colorScheme.error else extendedColors.success
                )
            }
        }
    }
}

@Composable
internal fun PromptStageLightboxCard(
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
    playbackProgressProvider: () -> Float = { 0f },
    @Suppress("UNUSED_PARAMETER") takeCount: Int = 0,
    showInspectAcoustics: Boolean = true,
    onImageZoomActiveChanged: ((Boolean) -> Unit)? = null,
    onPlayClick: () -> Unit,
    onToggleQualityFlag: () -> Unit = {},
    onEditRemarkClick: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenTakeSwitcher: () -> Unit = {},
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

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isFlagged -> MaterialTheme.colorScheme.error.copy(alpha = 0.48f)
            isRecorded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
        },
        animationSpec = StudioMotion.microTween(),
        label = "PromptCardBorderColor"
    )

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = cardBorderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                                Modifier
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                                    .clickable(onClick = onEditRemarkClick)
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
                                        .pointerHoverIcon(PointerIcon.Hand)
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
                                        .pointerHoverIcon(PointerIcon.Hand)
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
                                .pointerHoverIcon(PointerIcon.Hand)
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

                // Bottom Quick Actions inside Stage Card when Recorded (smooth vertical expand/shrink)
                AnimatedVisibility(
                    visible = isRecorded && !participantCleanMode,
                    enter = StudioMotion.verticalPanelEnter,
                    exit = StudioMotion.verticalPanelExit
                ) {
                    val iconBtnSize = if (isNarrowCardActions) 40.dp else 44.dp
                    val isDesktop = DesktopAdaptiveState.isDesktopPlatform
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AuditionPlayProgressButton(
                            isDesktopPill = isDesktop,
                            isPlayingAudio = isPlayingAudio,
                            isNarrow = isNarrowCardActions,
                            buttonSize = iconBtnSize,
                            playbackProgressProvider = playbackProgressProvider,
                            onClick = onPlayClick
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(if (isNarrowCardActions) 5.dp else 6.dp)) {
                            FilledTonalIconButton(
                                onClick = onToggleQualityFlag,
                                modifier = Modifier
                                    .size(iconBtnSize)
                                    .pointerHoverIcon(PointerIcon.Hand)
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
                                        .pointerHoverIcon(PointerIcon.Hand)
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
                                    .pointerHoverIcon(PointerIcon.Hand)
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
                                    .pointerHoverIcon(PointerIcon.Hand)
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
                }
            }
        }
    }
}

@Composable
private fun AuditionPlayProgressButton(
    isDesktopPill: Boolean,
    isPlayingAudio: Boolean,
    isNarrow: Boolean,
    buttonSize: androidx.compose.ui.unit.Dp,
    playbackProgressProvider: () -> Float,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val rawProgress = if (isPlayingAudio) playbackProgressProvider().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 55, easing = LinearEasing),
        label = "auditionPlayProgress"
    )
    val (interactionSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    if (isDesktopPill) {
        val baseBg by animateColorAsState(
            targetValue = when {
                isPlayingAudio -> lerp(colorScheme.surfaceContainerHigh, colorScheme.primaryContainer, 0.55f)
                isHovered -> lerp(colorScheme.secondaryContainer, colorScheme.primaryContainer, 0.45f)
                else -> colorScheme.secondaryContainer
            },
            animationSpec = tween(150),
            label = "desktopAuditionPillBg"
        )
        val borderColor by animateColorAsState(
            targetValue = when {
                isPlayingAudio -> colorScheme.primary.copy(alpha = 0.72f)
                isHovered -> colorScheme.primary.copy(alpha = 0.45f)
                else -> colorScheme.outlineVariant.copy(alpha = 0.35f)
            },
            animationSpec = tween(150),
            label = "desktopAuditionPillBorder"
        )
        val fillOverlayColor = colorScheme.primary.copy(alpha = 0.28f)
        val fillEdgeColor = colorScheme.primary.copy(alpha = 0.75f)
        val contentTint by animateColorAsState(
            targetValue = if (isPlayingAudio) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
            animationSpec = StudioMotion.microTween(),
            label = "desktopAuditionPillTint"
        )

        Surface(
            shape = CircleShape,
            color = baseBg,
            border = BorderStroke(if (isPlayingAudio) 1.5.dp else 1.dp, borderColor),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .height(buttonSize)
                .studioAnimateSize()
                .studioTactile(interactionSrc, pressScale = 0.96f)
                .clip(CircleShape)
                .hoverable(interactionSrc)
                .clickable(
                    interactionSource = interactionSrc,
                    indication = null,
                    onClick = onClick
                )
                .testTag("play_word_audio_button")
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(buttonSize)
                    .drawBehind {
                        if (isPlayingAudio && animatedProgress > 0.002f) {
                            val fillW = (size.width * animatedProgress).coerceIn(0f, size.width)
                            drawRect(
                                color = fillOverlayColor,
                                topLeft = Offset.Zero,
                                size = Size(fillW, size.height)
                            )
                            val edgeW = 2.dp.toPx()
                            drawRect(
                                color = fillEdgeColor,
                                topLeft = Offset((fillW - edgeW).coerceAtLeast(0f), 0f),
                                size = Size(edgeW, size.height)
                            )
                        }
                    }
                    .padding(horizontal = if (isNarrow) 14.dp else 16.dp)
            ) {
                AnimatedContent(
                    targetState = isPlayingAudio to isNarrow,
                    transitionSpec = { StudioMotion.subtleFadeScale() },
                    label = "AuditionPlayButtonContent"
                ) { (playing, narrow) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (narrow) 5.dp else 6.dp)
                    ) {
                        Icon(
                            imageVector = if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (playing) S.recording.stopPlaybackDesc else S.recording.playCurrentWordDesc,
                            tint = if (playing) colorScheme.primary else contentTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                playing && narrow -> S.common.stop
                                playing -> S.recording.stopListening
                                narrow -> S.common.playback
                                else -> S.recording.startListening
                            },
                            color = contentTint,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    } else {
        // Mobile circular audition button with crisp outer ring progress around the button perimeter
        val ringTrackColor = colorScheme.primary.copy(alpha = 0.20f)
        val ringActiveColor = colorScheme.primary
        val circleBg by animateColorAsState(
            targetValue = if (isPlayingAudio) colorScheme.primaryContainer else colorScheme.secondaryContainer,
            animationSpec = tween(150),
            label = "mobileAuditionCircleBg"
        )
        val iconTint = if (isPlayingAudio) colorScheme.primary else colorScheme.onSecondaryContainer
        val outerSize = (buttonSize + 6.dp).coerceAtLeast(48.dp)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(outerSize)
                .studioTactile(interactionSrc, pressScale = 0.92f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSrc,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
                .testTag("play_word_audio_button")
        ) {
            if (isPlayingAudio) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 3.dp.toPx()
                    val inset = strokePx / 2f + 1.dp.toPx()
                    val arcSize = Size(
                        width = (size.width - inset * 2f).coerceAtLeast(1f),
                        height = (size.height - inset * 2f).coerceAtLeast(1f)
                    )
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = ringTrackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    val sweep = (360f * animatedProgress).coerceIn(0f, 360f)
                    if (sweep > 0.5f) {
                        drawArc(
                            color = ringActiveColor,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = circleBg,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier.size(buttonSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingAudio) S.recording.stopPlaybackDesc else S.recording.playCurrentWordDesc,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun QualityStatusBadge(
    isRecorded: Boolean,
    quality: String?,
    simpleMode: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val extendedColors = LocalPhonAcqExtendedColors.current
    val isNormal = quality == null || quality == "正常"
    val (targetContainer, targetContent) = when {
        !isRecorded ->
            MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        isNormal ->
            extendedColors.successContainer to extendedColors.onSuccessContainer
        quality == "人工标重录" || quality == "需重录" ||
            quality == "削波/过载" || quality == "过载/破音" ||
            quality == "开头截断" || quality == "收尾截断" ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        quality == "音量过低" || quality == "声音偏小" || quality == "底噪偏高" ->
            extendedColors.warningContainer to extendedColors.onWarningContainer
        quality == "嘎裂声" ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = StudioMotion.microTween(),
        label = "QualityBadgeBg"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = StudioMotion.microTween(),
        label = "QualityBadgeFg"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = Modifier
            .studioAnimateSize()
            .clip(RoundedCornerShape(50))
            .then(
                if (isRecorded && onClick != null) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .then(if (isRecorded) Modifier.testTag("quality_status_badge") else Modifier)
    ) {
        AnimatedContent(
            targetState = Triple(isRecorded, quality, simpleMode),
            transitionSpec = { StudioMotion.subtleFadeScale() },
            label = "QualityBadgeContent"
        ) { (rec, qual, simple) ->
            if (!rec) {
                Text(
                    text = S.recording.pendingRecordBadge,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            } else {
                val normal = qual == null || qual == "正常"
                val label = S.quality.displayStatus(qual, simpleMode = simple)
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (normal) Icons.Default.Check else Icons.Default.Warning,
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
    }
}
