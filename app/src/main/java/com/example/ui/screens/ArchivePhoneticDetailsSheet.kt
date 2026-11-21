package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AcousticAnalyzer
import com.example.audio.CanonicalToneMember
import com.example.audio.CorpusPhonologyAnalyzer
import com.example.data.Speaker
import com.example.data.SpeakerRecording
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.DesktopAdaptiveState
import com.example.ui.components.PlatformVerticalScrollbar
import com.example.ui.components.StudioAdaptiveSheet
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioMotion
import com.example.ui.components.studioAnimateSize
import com.example.ui.theme.LocalPhonAcqExtendedColors
import com.example.utils.PraatCollectionExporter
import com.example.utils.ZipUtility
import com.example.viewmodel.RecordingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Compatibility delegate so existing callers can invoke analyzeWordPhonetics cleanly.
 */
fun analyzeWordPhonetics(
    context: Context,
    word: Word,
    speakerGender: String?
): AcousticAnalyzer.PhoneticAcousticResult {
    return AcousticAnalyzer.analyzeWordPhonetics(context, word, speakerGender)
}

@androidx.compose.runtime.Immutable
data class CrossSpeakerWordAnalysis(
    val speaker: Speaker,
    val recording: SpeakerRecording,
    val audioFile: File,
    val analysis: AcousticAnalyzer.PhoneticAcousticResult
)

@Composable
fun PhoneticDetailsBottomSheet(
    word: Word,
    speaker: Speaker?,
    viewModel: RecordingViewModel? = null,
    onDismiss: () -> Unit
) {
    StudioAdaptiveSheet(
        visible = true,
        onDismissRequest = onDismiss,
        maxWidth = 640.dp
    ) { onRequestClose ->
        PhoneticDetailsContent(
            word = word,
            speaker = speaker,
            viewModel = viewModel,
            onDismiss = onRequestClose,
            isDockedPane = false
        )
    }
}

@Composable
fun PhoneticDetailsContent(
    word: Word,
    speaker: Speaker?,
    viewModel: RecordingViewModel? = null,
    onDismiss: () -> Unit,
    isDockedPane: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playingWordId = viewModel?.playingWordId?.collectAsState()?.value
    val takesRefreshTrigger = viewModel?.takesRefreshTrigger?.collectAsState()?.value ?: 0
    var pitchCalibrateTick by remember { mutableStateOf(0) }
    var isCalibratingPitch by remember { mutableStateOf(false) }
    var showManualPitchDialog by remember { mutableStateOf(false) }

    // Asynchronous computation off the UI thread with brief frame yield on sheet entrance
    val analysisState by produceState<AcousticAnalyzer.PhoneticAcousticResult?>(
        initialValue = null,
        key1 = word.id,
        key2 = word.recordingFile,
        key3 = "${speaker?.id}_$pitchCalibrateTick"
    ) {
        if (!isDockedPane) delay(65)
        value = withContext(Dispatchers.Default) {
            AcousticAnalyzer.analyzeWordPhonetics(context, word, speaker?.gender)
        }
    }

    val canonicalMember by produceState<CanonicalToneMember?>(
        initialValue = CorpusPhonologyAnalyzer.lookupCachedCanonicalMember(
            projectId = word.projectId,
            speakerId = speaker?.id ?: -1L,
            wordId = word.id
        ),
        key1 = word.id,
        key2 = word.recordingFile,
        key3 = "${speaker?.id}_$pitchCalibrateTick"
    ) {
        val cached = CorpusPhonologyAnalyzer.lookupCachedCanonicalMember(
            projectId = word.projectId,
            speakerId = speaker?.id ?: -1L,
            wordId = word.id
        )
        if (cached != null) {
            value = cached
        } else if (viewModel != null) {
            // Let sheet entrance animation & single-word analysis finish first before background corpus clustering
            delay(220)
            value = withContext(Dispatchers.Default) {
                val spkWords = viewModel.getWordsForSpeakerSync(word.projectId, speaker?.id ?: -1L)
                val report = CorpusPhonologyAnalyzer.buildSpeakerPhonologyReport(
                    context = context,
                    projectId = word.projectId,
                    speaker = speaker,
                    words = spkWords
                )
                report.wordIdToCanonicalMember[word.id]
            }
        }
    }

    val activeAudioFile = remember(word.projectId, word.recordingFile) {
        word.recordingFile?.takeIf { it.isNotBlank() }?.let {
            File(File(context.getExternalFilesDir(null), "project_${word.projectId}"), it)
        }
    }
    val activeSpeakerPitchRange = remember(speaker?.id, pitchCalibrateTick) {
        speaker?.id?.let { viewModel?.getSpeakerPitchRange(it) }
    }

    // Load cross-speaker recordings & acoustic contours after sheet slide-in completes
    val crossSpeakerList by produceState<List<CrossSpeakerWordAnalysis>>(
        initialValue = emptyList(),
        key1 = word.id,
        key2 = word.recordingFile,
        key3 = takesRefreshTrigger
    ) {
        if (viewModel == null) {
            value = emptyList()
        } else {
            delay(180)
            value = withContext(Dispatchers.Default) {
                val pairs = viewModel.getCrossSpeakerRecordingsForWord(word.id)
                val projDir = File(context.getExternalFilesDir(null), "project_${word.projectId}")
                pairs.mapNotNull { (spk, rec) ->
                    val f = File(projDir, rec.recordingFile)
                    if (f.exists() && f.length() > 44L) {
                        val spkWord = word.copy(
                            isRecorded = true,
                            recordingFile = rec.recordingFile,
                            durationMs = rec.durationMs,
                            qualityStatus = rec.qualityStatus
                        )
                        val res = AcousticAnalyzer.analyzeWordPhonetics(context, spkWord, spk.gender)
                        CrossSpeakerWordAnalysis(spk, rec, f, res)
                    } else {
                        null
                    }
                }
            }
        }
    }

    var overlayOtherSpeakersOnToneChart by remember { mutableStateOf(true) }
    var hiddenOtherSpeakerIds by remember(word.id) { mutableStateOf(emptySet<Long>()) }
    var sequentialPlayJob by remember { mutableStateOf<Job?>(null) }
    var isSequentialPlaying by remember { mutableStateOf(false) }

    DisposableEffect(word.id) {
        onDispose {
            sequentialPlayJob?.cancel()
        }
    }

    val toggleOtherSpeakerVisibility: (Long) -> Unit = { targetSpkId ->
        hiddenOtherSpeakerIds = if (targetSpkId in hiddenOtherSpeakerIds) {
            hiddenOtherSpeakerIds - targetSpkId
        } else {
            hiddenOtherSpeakerIds + targetSpkId
        }
    }

    // Available multi-takes for the active speaker
    val availableTakes by produceState<List<File>>(
        initialValue = emptyList(),
        word.id,
        word.recordingFile,
        speaker?.id,
        takesRefreshTrigger
    ) {
        val sid = speaker?.id
        value = if (viewModel != null && sid != null) {
            withContext(Dispatchers.IO) {
                viewModel.getAvailableTakesForWord(word.projectId, word.id, sid)
            }
        } else {
            emptyList()
        }
    }

    // Consumes leftover downward scroll & fling from the scrollable content area so that
    // scrolling back to the top inside the page never drags the entire drawer closed.
    val contentScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = available

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity = available
        }
    }

    val horizontalPad = if (isDockedPane) 16.dp else 20.dp
    val bodyScrollState = rememberScrollState()
    val extendedColors = LocalPhonAcqExtendedColors.current
    val chartPalette = extendedColors.chartPalette
    AnimatedContent(
        targetState = analysisState,
        contentKey = { it != null },
        transitionSpec = { StudioMotion.subtleFadeScale() },
        label = "PhoneticDetailsAnalysisTransition",
        modifier = if (isDockedPane) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    ) { analysis ->
        if (analysis == null) {
            Box(
                modifier = if (isDockedPane) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(S.archive.analyzingFeatures, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
        val isFlagged = word.qualityStatus != null && word.qualityStatus != "正常"
        Column(
            modifier = if (isDockedPane) {
                Modifier.fillMaxSize().testTag("phonetic_details_docked_pane")
            } else {
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            }
        ) {
            // Fixed Top Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = horizontalPad,
                        end = horizontalPad,
                        top = if (isDockedPane) 14.dp else 0.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = S.archive.detailSheetTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = word.text,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.Bottom)
                            )
                            if (word.remark.isNotEmpty()) {
                                Text(
                                    text = S.archive.wordRemarkBracketFormat.withArgs("remark" to word.remark),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.Bottom)
                                        .padding(bottom = 3.dp)
                                )
                            }
                            if (word.speakerNote.isNotEmpty()) {
                                Text(
                                    text = S.recording.notePrefix.withArgs("note" to word.speakerNote),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.Bottom)
                                        .padding(bottom = 3.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (viewModel != null && word.isRecorded) {
                            val qualityChipBg by animateColorAsState(
                                targetValue = if (isFlagged) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                animationSpec = StudioMotion.shortInTween(),
                                label = "detailsQualityChipBg"
                            )
                            val qualityChipFg by animateColorAsState(
                                targetValue = if (isFlagged) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                animationSpec = StudioMotion.shortInTween(),
                                label = "detailsQualityChipFg"
                            )
                            Surface(
                                color = qualityChipBg,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .studioAnimateSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable {
                                        viewModel.toggleManualQualityFlag(word)
                                        Toast.makeText(
                                            context,
                                            if (isFlagged) S.archive.markedWordNormalToast else S.recording.vmMarkedRerecordToast,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .testTag("details_sheet_quality_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFlagged) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = qualityChipFg
                                    )
                                    Text(
                                        text = if (isFlagged) {
                                            S.archive.statusRerecordToggleChip.withArgs(
                                                "status" to S.quality.displayStatus(word.qualityStatus, simpleMode = false)
                                            )
                                        } else {
                                            S.archive.statusNormalToggleChip
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        color = qualityChipFg
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = if (analysis.isValidVoiced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (analysis.isValidVoiced) {
                                        if (analysis.isPitchCalibrated) S.quality.statusAlignedDisplay else S.archive.pronunciationNormalBadge
                                    } else {
                                        S.archive.lowVolumeShort
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = if (analysis.isValidVoiced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = S.archive.closeDetailDrawerDesc,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = S.archive.speakerMetaSummary.withArgs(
                            "name" to (speaker?.name ?: S.common.defaultLabel),
                            "gender" to S.speaker.displayGender(speaker?.gender ?: "未设"),
                            "dialect" to (speaker?.dialect?.ifBlank { S.archive.unsetDialectLabel } ?: S.speaker.defaultDialectMandarin),
                            "age" to (speaker?.age ?: 25)
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // Scrollable body (locked against accidental sheet-dismissal when inside ModalBottomSheet)
            Box(
                modifier = if (isDockedPane) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
            Column(
                modifier = if (isDockedPane) {
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(bodyScrollState)
                        .padding(start = horizontalPad, end = horizontalPad, top = 14.dp, bottom = 24.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .nestedScroll(contentScrollLock)
                        .verticalScroll(bodyScrollState)
                        .padding(start = horizontalPad, end = horizontalPad, top = 14.dp, bottom = 36.dp)
                }
            ) {
                // Clean 2x2 Summary Cards
                val volumeLabel = when {
                    analysis.peakDbfs > -1.5f -> S.archive.intensityLoud.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.intensityDbfs))
                    analysis.intensityDbfs > -32.0f -> S.archive.intensityOptimal.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.intensityDbfs))
                    else -> S.archive.intensityQuiet.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.intensityDbfs))
                }
                val quietnessLabel = when {
                    analysis.snrDb >= 20f -> S.archive.snrQuiet.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.snrDb))
                    analysis.snrDb >= 10f -> S.archive.snrFair.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.snrDb))
                    else -> S.archive.snrNoisy.withArgs("db" to String.format(java.util.Locale.US, "%.0f", analysis.snrDb))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(S.archive.metricVolumeTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = volumeLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = S.archive.peakSubFormat.withArgs("db" to String.format(java.util.Locale.US, "%.1f", analysis.peakDbfs)),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(S.archive.metricDurationTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = S.archive.secondsValueFormat.withArgs("sec" to String.format(java.util.Locale.US, "%.2f", word.durationMs / 1000f)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = S.archive.msValueFormat.withArgs("ms" to word.durationMs),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(S.archive.metricQuietnessTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = quietnessLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = S.archive.snrSubLabel,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(S.archive.metricChaoToneTitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (analysis.isValidVoiced) "[${analysis.chaoToneNumbers}]" else S.archive.unavailableLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = if (analysis.isValidVoiced) {
                                    S.archive.meanPitchHzFormat.withArgs("hz" to String.format(java.util.Locale.US, "%.0f", analysis.pitchMean))
                                } else {
                                    S.archive.lowVolumePitchNotDetected
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 1: Pitch contour & 5-level scale (with optional cross-speaker overlay)
                Text(
                    text = S.archive.toneContourSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                SingleWordCanonicalToneBanner(
                    analysis = analysis,
                    canonicalMember = canonicalMember,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                val otherSpeakersEntries = remember(crossSpeakerList, speaker?.id) {
                    crossSpeakerList.filter { it.speaker.id != speaker?.id && it.analysis.isValidVoiced && it.analysis.contourPoints.isNotEmpty() }
                }
                val comparePalette = remember(chartPalette) {
                    listOf(
                        chartPalette[1],
                        chartPalette[5],
                        chartPalette[3],
                        chartPalette[6],
                        chartPalette[2],
                        chartPalette[4]
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.archive.refTonePatternPrefix.withArgs("code" to analysis.toneCode),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (analysis.isValidVoiced) {
                                    if (analysis.isPitchCalibrated) {
                                        val calibratedBg by animateColorAsState(
                                            targetValue = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            animationSpec = StudioMotion.shortInTween(),
                                            label = "calibratedPitchPillBg"
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = calibratedBg,
                                            modifier = Modifier
                                                .studioAnimateSize()
                                                .clip(RoundedCornerShape(6.dp))
                                                .then(
                                                    if (viewModel != null && speaker != null) {
                                                        Modifier.pointerHoverIcon(PointerIcon.Hand)
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable(enabled = viewModel != null && speaker != null) {
                                                    showManualPitchDialog = true
                                                }
                                        ) {
                                            Text(
                                                text = activeSpeakerPitchRange?.let {
                                                    "${it.first.toInt()}–${it.second.toInt()} Hz"
                                                } ?: S.archive.personalPitchRangePill,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (viewModel != null && speaker != null) {
                                        val calibrateBtnBg by animateColorAsState(
                                            targetValue = if (isCalibratingPitch) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            },
                                            animationSpec = StudioMotion.shortInTween(),
                                            label = "calibratePitchPillBg"
                                        )
                                        val calibrateBtnFg by animateColorAsState(
                                            targetValue = if (isCalibratingPitch) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            },
                                            animationSpec = StudioMotion.shortInTween(),
                                            label = "calibratePitchPillFg"
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = calibrateBtnBg,
                                            modifier = Modifier
                                                .studioAnimateSize()
                                                .clip(RoundedCornerShape(6.dp))
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .clickable(enabled = !isCalibratingPitch) {
                                                    isCalibratingPitch = true
                                                    viewModel.estimatePitchRangeForSpeaker(speaker) { range ->
                                                        isCalibratingPitch = false
                                                        pitchCalibrateTick++
                                                        if (range != null) {
                                                            Toast.makeText(
                                                                context,
                                                                S.archive.calibratedPitchRangePill.withArgs(
                                                                    "min" to range.first.toInt(),
                                                                    "max" to range.second.toInt()
                                                                ),
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } else {
                                                            // Fewer than 3 voiced samples — open manual Hz dialog directly
                                                            showManualPitchDialog = true
                                                        }
                                                    }
                                                }
                                        ) {
                                            Text(
                                                text = if (isCalibratingPitch) S.archive.calibratingPitchBtn else S.archive.defaultPitchRangeCalibrateBtn,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = calibrateBtnFg,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .studioAnimateSize()
                                                .clip(RoundedCornerShape(6.dp))
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .clickable { showManualPitchDialog = true }
                                        ) {
                                            Text(
                                                text = S.recording.manualPitchButton,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                com.example.ui.components.AnimatedDialogVisibility(
                                    visible = showManualPitchDialog && viewModel != null && speaker != null
                                ) {
                                    if (viewModel != null && speaker != null) {
                                        ManualSpeakerPitchDialog(
                                            speaker = speaker,
                                            currentRange = activeSpeakerPitchRange,
                                            onSave = { minHz, maxHz ->
                                                viewModel.saveSpeakerPitchRange(speaker.id, minHz, maxHz)
                                                pitchCalibrateTick++
                                                showManualPitchDialog = false
                                                Toast.makeText(
                                                    context,
                                                    S.recording.manualPitchSavedToast.withArgs(
                                                        "min" to minHz.toInt(),
                                                        "max" to maxHz.toInt()
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onResetAuto = {
                                                viewModel.clearSpeakerPitchRange(speaker.id)
                                                pitchCalibrateTick++
                                                showManualPitchDialog = false
                                                Toast.makeText(
                                                    context,
                                                    S.recording.manualPitchResetToast,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onDismiss = { showManualPitchDialog = false }
                                        )
                                    }
                                }
                                Text(
                                    text = if (analysis.isValidVoiced) {
                                        S.archive.meanPitchShortHzFormat.withArgs("hz" to String.format(java.util.Locale.US, "%.0f", analysis.pitchMean))
                                    } else {
                                        S.archive.lowVolumeShort
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = analysis.toneTrajectory,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(end = 10.dp)
                            )
                            if (analysis.isValidVoiced) {
                                Text(
                                    text = analysis.pitchRange,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.End
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val plotSurfaceBg = MaterialTheme.colorScheme.surface
                        val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        val curveStrokeColor = MaterialTheme.colorScheme.primary
                        val secondarySyllablePalette = remember(curveStrokeColor, chartPalette) {
                            listOf(
                                curveStrokeColor,
                                chartPalette[5],
                                chartPalette[2],
                                chartPalette[1],
                                chartPalette[3],
                                chartPalette[6]
                            )
                        }

                        // Compute time-proportional horizontal weights for multi-syllable words
                        val syllableWeights = remember(analysis.segmentCount, analysis.segments, analysis.syllableVoicedMs) {
                            val count = analysis.segmentCount.coerceAtLeast(1)
                            if (count <= 1) {
                                listOf(1f)
                            } else {
                                val rawDurs = (0 until count).map { idx ->
                                    val segDur = analysis.segments.getOrNull(idx)?.let {
                                        ((it.endSec - it.startSec) * 1000.0).toFloat()
                                    } ?: 0f
                                    val vDur = analysis.syllableVoicedMs.getOrElse(idx) { 0 }.toFloat()
                                    when {
                                        segDur > 25f -> segDur
                                        vDur > 20f -> vDur
                                        else -> 180f
                                    }
                                }
                                val sumRaw = rawDurs.sum().coerceAtLeast(1f)
                                val minShare = (0.20f).coerceAtMost(0.75f / count)
                                val clamped = rawDurs.map { (it / sumRaw).coerceIn(minShare, 0.78f) }
                                val sumClamped = clamped.sum().coerceAtLeast(0.01f)
                                clamped.map { it / sumClamped }
                            }
                        }

                        // Per-syllable aligned header strip when segmentCount > 1
                        if (analysis.segmentCount > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 46.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (s in 0 until analysis.segmentCount) {
                                    val wWeight = syllableWeights.getOrElse(s) { 1f / analysis.segmentCount }
                                    val lbl = analysis.segments.getOrNull(s)?.label?.ifBlank { null }
                                        ?: S.common.syllableFallback.withArgs("index" to s + 1)
                                    val tVal = analysis.syllableToneNumbers.getOrNull(s) ?: "--"
                                    val isNeutral = analysis.syllableNeutralFlags.getOrElse(s) { false }
                                    val isChecked = analysis.syllableCheckedFlags.getOrElse(s) { false }
                                    val col = secondarySyllablePalette[s % secondarySyllablePalette.size]
                                    val suffix = when {
                                        isNeutral -> S.archive.neutralShortMark
                                        isChecked -> S.archive.checkedShortMark
                                        else -> ""
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = col.copy(alpha = 0.11f),
                                        border = BorderStroke(1.dp, col.copy(alpha = 0.34f)),
                                        modifier = Modifier.weight(wWeight)
                                    ) {
                                        Text(
                                            text = "${circledSyllableBadge(s + 1)} $lbl [$tVal$suffix]",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = col,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(46.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(S.archive.scale5High, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                Text(S.archive.scale4HalfHigh, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(S.archive.scale3Mid, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(S.archive.scale2HalfLow, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(S.archive.scale1Low, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(plotSurfaceBg, shape = RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val sectionsY = size.height / 4f
                                    for (i in 0..4) {
                                        val cy = i * sectionsY
                                        drawLine(
                                            color = gridLineColor,
                                            start = Offset(0f, cy),
                                            end = Offset(size.width, cy),
                                            strokeWidth = 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                        )
                                    }

                                    if (analysis.segmentCount <= 1) {
                                        val sectionsX = size.width / 4f
                                        for (i in 1..3) {
                                            val cx = i * sectionsX
                                            drawLine(
                                                color = gridLineColor,
                                                start = Offset(cx, 0f),
                                                end = Offset(cx, size.height),
                                                strokeWidth = 1.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                            )
                                        }
                                    }

                                    // Draw overlaid comparison curves from other speakers (syllable-column & checked-tone aligned!)
                                    if (overlayOtherSpeakersOnToneChart && otherSpeakersEntries.isNotEmpty()) {
                                        val ptsPerSeg = 5
                                        val refSegCount = analysis.segmentCount.coerceAtLeast(1)
                                        otherSpeakersEntries.forEachIndexed { idx, otherEntry ->
                                            if (otherEntry.speaker.id in hiddenOtherSpeakerIds) return@forEachIndexed
                                            val otherAna = otherEntry.analysis
                                            val otherPoints = otherAna.contourPoints
                                            if (otherPoints.size >= 2) {
                                                val col = comparePalette[idx % comparePalette.size].copy(alpha = 0.78f)
                                                val otherSegCnt = minOf(refSegCount, otherAna.segmentCount.coerceAtLeast(1))
                                                var otherCursorX = 0f
                                                for (s in 0 until otherSegCnt) {
                                                    val colW = if (refSegCount == 1) {
                                                        if (otherAna.isCheckedTone) {
                                                            size.width * otherAna.checkedToneRatio.coerceIn(0.40f, 0.62f)
                                                        } else {
                                                            size.width
                                                        }
                                                    } else {
                                                        size.width * syllableWeights.getOrElse(s) { 1f / refSegCount }
                                                    }
                                                    val sStart = s * ptsPerSeg
                                                    if (sStart + ptsPerSeg <= otherPoints.size) {
                                                        val oNeutral = otherAna.syllableNeutralFlags.getOrElse(s) { false }
                                                        val oChecked = if (otherAna.segmentCount <= 1) {
                                                            otherAna.isCheckedTone
                                                        } else {
                                                            otherAna.syllableCheckedFlags.getOrElse(s) { false }
                                                        }
                                                        val innerPad = if (refSegCount > 1) (colW * 0.10f).coerceAtMost(10.dp.toPx()) else 0f
                                                        val usableW = (colW - innerPad * 2f).coerceAtLeast(8f)
                                                        val drawSpanW = when {
                                                            oNeutral -> usableW * 0.45f
                                                            oChecked && refSegCount > 1 -> usableW * 0.56f
                                                            else -> usableW
                                                        }
                                                        val sx = otherCursorX + innerPad
                                                        val stepX = drawSpanW / (ptsPerSeg - 1)
                                                        val path = Path()
                                                        var endPx = sx
                                                        var endPy = 0f
                                                        for (i in 0 until ptsPerSeg) {
                                                            val p = otherPoints[sStart + i]
                                                            val px = sx + i * stepX
                                                            val py = (1f - p) * size.height * 0.84f + size.height * 0.08f
                                                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                            endPx = px
                                                            endPy = py
                                                        }
                                                        drawPath(
                                                            path = path,
                                                            color = col,
                                                            style = Stroke(
                                                                width = 2.1.dp.toPx(),
                                                                cap = StrokeCap.Round,
                                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                                                            )
                                                        )
                                                        if (oChecked) {
                                                            drawLine(
                                                                color = col,
                                                                start = Offset(endPx, (endPy - 6.dp.toPx()).coerceAtLeast(2f)),
                                                                end = Offset(endPx, (endPy + 6.dp.toPx()).coerceAtMost(size.height - 2f)),
                                                                strokeWidth = 2.dp.toPx(),
                                                                cap = StrokeCap.Round
                                                            )
                                                        }
                                                    } else if (s == 0 && otherPoints.size in 2..4) {
                                                        val path = Path()
                                                        otherPoints.forEachIndexed { i, p ->
                                                            val px = (i.toFloat() / (otherPoints.size - 1)) * colW
                                                            val py = (1f - p) * size.height * 0.84f + size.height * 0.08f
                                                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                        }
                                                        drawPath(
                                                            path = path,
                                                            color = col,
                                                            style = Stroke(
                                                                width = 2.dp.toPx(),
                                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                                                            )
                                                        )
                                                    }
                                                    if (refSegCount > 1) {
                                                        otherCursorX += size.width * syllableWeights.getOrElse(s) { 1f / refSegCount }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val curvePoints = analysis.contourPoints
                                    if (analysis.isValidVoiced && curvePoints.isNotEmpty()) {
                                        val pointsPerSegment = 5
                                        var cursorX = 0f
                                        var prevSyllableEndPt: Offset? = null

                                        for (s in 0 until analysis.segmentCount) {
                                            val colWidth = if (analysis.segmentCount == 1 && analysis.isCheckedTone) {
                                                size.width * analysis.checkedToneRatio.coerceIn(0.40f, 0.62f)
                                            } else {
                                                size.width * syllableWeights.getOrElse(s) { 1f / analysis.segmentCount.coerceAtLeast(1) }
                                            }

                                            if (s > 0) {
                                                drawLine(
                                                    color = gridLineColor.copy(alpha = 0.95f),
                                                    start = Offset(cursorX, 0f),
                                                    end = Offset(cursorX, size.height),
                                                    strokeWidth = 1.5.dp.toPx(),
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f), 0f)
                                                )
                                            }

                                            val startIdx = s * pointsPerSegment
                                            if (startIdx + pointsPerSegment <= curvePoints.size) {
                                                val sylCol = if (analysis.segmentCount > 1) {
                                                    secondarySyllablePalette[s % secondarySyllablePalette.size]
                                                } else {
                                                    curveStrokeColor
                                                }
                                                val isNeutralSyl = analysis.syllableNeutralFlags.getOrElse(s) { false }
                                                val isCheckedSyl = if (analysis.segmentCount == 1) {
                                                    analysis.isCheckedTone
                                                } else {
                                                    analysis.syllableCheckedFlags.getOrElse(s) { false }
                                                }

                                                val innerPad = if (analysis.segmentCount > 1) (colWidth * 0.10f).coerceAtMost(10.dp.toPx()) else 0f
                                                val usableW = (colWidth - innerPad * 2f).coerceAtLeast(8f)
                                                val drawSpanW = when {
                                                    isNeutralSyl -> usableW * 0.45f
                                                    isCheckedSyl && analysis.segmentCount > 1 -> usableW * 0.56f
                                                    else -> usableW
                                                }
                                                val sx = cursorX + innerPad
                                                val stepX = drawSpanW / (pointsPerSegment - 1)

                                                val firstPy = (1f - curvePoints[startIdx]) * size.height * 0.84f + size.height * 0.08f
                                                val firstPt = Offset(sx, firstPy)

                                                // Subtle dotted inter-syllable pitch bridge across consonant/juncture
                                                val prevEnd = prevSyllableEndPt
                                                if (prevEnd != null) {
                                                    drawLine(
                                                        color = gridLineColor.copy(alpha = 0.75f),
                                                        start = prevEnd,
                                                        end = firstPt,
                                                        strokeWidth = 1.3.dp.toPx(),
                                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)
                                                    )
                                                }

                                                if (isNeutralSyl) {
                                                    // Neutral tone (轻声): short dashed trace + prominent hollow pitch target circle
                                                    val nPath = Path()
                                                    var midPt = firstPt
                                                    var endPt = firstPt
                                                    for (i in 0 until pointsPerSegment) {
                                                        val p = curvePoints[startIdx + i]
                                                        val px = sx + i * stepX
                                                        val py = (1f - p) * size.height * 0.84f + size.height * 0.08f
                                                        if (i == 0) nPath.moveTo(px, py) else nPath.lineTo(px, py)
                                                        if (i == 2) midPt = Offset(px, py)
                                                        endPt = Offset(px, py)
                                                    }
                                                    drawPath(
                                                        path = nPath,
                                                        color = sylCol.copy(alpha = 0.65f),
                                                        style = Stroke(
                                                            width = 2.2.dp.toPx(),
                                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f)
                                                        )
                                                    )
                                                    drawCircle(color = plotSurfaceBg, radius = 4.8.dp.toPx(), center = midPt)
                                                    drawCircle(
                                                        color = sylCol,
                                                        radius = 4.8.dp.toPx(),
                                                        center = midPt,
                                                        style = Stroke(width = 2.2.dp.toPx())
                                                    )
                                                    prevSyllableEndPt = endPt
                                                } else {
                                                    val path = Path()
                                                    var lastPx = sx
                                                    var lastPy = firstPy
                                                    for (i in 0 until pointsPerSegment) {
                                                        val p = curvePoints[startIdx + i]
                                                        val px = sx + i * stepX
                                                        val py = (1f - p) * size.height * 0.84f + size.height * 0.08f
                                                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                        lastPx = px
                                                        lastPy = py
                                                        drawCircle(
                                                            color = sylCol,
                                                            radius = 3.3.dp.toPx(),
                                                            center = Offset(px, py)
                                                        )
                                                    }
                                                    drawPath(
                                                        path = path,
                                                        color = sylCol,
                                                        style = Stroke(width = 3.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                    )
                                                    if (isCheckedSyl) {
                                                        drawLine(
                                                            color = sylCol,
                                                            start = Offset(lastPx, (lastPy - 7.5.dp.toPx()).coerceAtLeast(2f)),
                                                            end = Offset(lastPx, (lastPy + 7.5.dp.toPx()).coerceAtMost(size.height - 2f)),
                                                            strokeWidth = 2.6.dp.toPx(),
                                                            cap = StrokeCap.Round
                                                        )
                                                    }
                                                    prevSyllableEndPt = Offset(lastPx, lastPy)
                                                }
                                            }
                                            cursorX += colWidth
                                        }
                                    }
                                }

                                if (!analysis.isValidVoiced || analysis.contourPoints.isEmpty()) {
                                    Text(
                                        text = S.archive.noClearPitchCurveHint,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        if (otherSpeakersEntries.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(8.dp).background(curveStrokeColor, CircleShape))
                                        Text(
                                            text = S.archive.currentSpeakerSolidLegend.withArgs("name" to (speaker?.name ?: S.archive.currentSpeakerShortFallback)),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (overlayOtherSpeakersOnToneChart) {
                                        otherSpeakersEntries.forEachIndexed { idx, entry ->
                                            val col = comparePalette[idx % comparePalette.size]
                                            val isHidden = entry.speaker.id in hiddenOtherSpeakerIds
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { toggleOtherSpeakerVisibility(entry.speaker.id) }
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(if (isHidden) MaterialTheme.colorScheme.outline else col, CircleShape)
                                                )
                                                Text(
                                                    text = S.archive.speakerChaoBracketItem.withArgs(
                                                        "name" to entry.speaker.name,
                                                        "chao" to entry.analysis.chaoToneNumbers
                                                    ),
                                                    fontSize = 11.sp,
                                                    color = if (isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                StudioFilterChip(
                                    selected = overlayOtherSpeakersOnToneChart,
                                    onClick = { overlayOtherSpeakersOnToneChart = !overlayOtherSpeakersOnToneChart },
                                    labelText = S.archive.overlayCompareChip
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = S.archive.chaoNormalizationExplanation,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Section 1B: Cross-Speaker Same-Word Comparison
                if (crossSpeakerList.size >= 2 && viewModel != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = S.archive.crossSpeakerCompareTitle.withArgs("count" to crossSpeakerList.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = S.archive.crossSpeakerCompareSubtitle.withArgs("word" to word.text),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                if (isSequentialPlaying && sequentialPlayJob?.isActive == true) {
                                    sequentialPlayJob?.cancel()
                                    sequentialPlayJob = null
                                    isSequentialPlaying = false
                                    viewModel.stopPlayback()
                                } else {
                                    sequentialPlayJob?.cancel()
                                    isSequentialPlaying = true
                                    sequentialPlayJob = scope.launch {
                                        try {
                                            for (item in crossSpeakerList) {
                                                if (!isActive) break
                                                val tagId = -(word.id * 1000 + item.speaker.id)
                                                viewModel.playSpecificAudioFile(item.audioFile, tagId)
                                                val waitMs = item.recording.durationMs.coerceIn(450L, 4000L) + 420L
                                                delay(waitMs)
                                            }
                                        } finally {
                                            isSequentialPlaying = false
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isSequentialPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (isSequentialPlaying) "停止连播" else S.archive.playSequentialButton,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            crossSpeakerList.forEach { item ->
                                val isCurrentSpk = item.speaker.id == speaker?.id
                                val otherIdx = otherSpeakersEntries.indexOfFirst { it.speaker.id == item.speaker.id }
                                val spkDotCol = if (isCurrentSpk) {
                                    MaterialTheme.colorScheme.primary
                                } else if (otherIdx >= 0) {
                                    comparePalette[otherIdx % comparePalette.size]
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                                val isChartHidden = !isCurrentSpk && item.speaker.id in hiddenOtherSpeakerIds
                                val tagId = -(word.id * 1000 + item.speaker.id)
                                val isPlayingThis = playingWordId == tagId
                                val primaryTraj = item.analysis.formantTrajectories.firstOrNull()
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCurrentSpk) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrentSpk) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(
                                                            if (isChartHidden) MaterialTheme.colorScheme.outline else spkDotCol,
                                                            CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = item.speaker.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = S.archive.crossSpeakerRowMeta.withArgs(
                                                        "gender" to S.speaker.displayGender(item.speaker.gender),
                                                        "age" to item.speaker.age,
                                                        "dialect" to item.speaker.dialect.ifBlank { S.speaker.defaultDialectMandarin }
                                                    ),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(
                                                    text = S.archive.crossSpeakerChaoValue.withArgs(
                                                        "chao" to item.analysis.chaoToneNumbers,
                                                        "code" to item.analysis.toneCode
                                                    ),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (item.analysis.isValidVoiced) {
                                                    Text(
                                                        text = S.archive.crossSpeakerF0Format.withArgs(
                                                            "hz" to String.format(java.util.Locale.US, "%.0f", item.analysis.pitchMean)
                                                        ),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                            if (primaryTraj != null && primaryTraj.nucleusF1 > 0) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = String.format(
                                                        java.util.Locale.US,
                                                        "%s · F1/F2: %d/%d Hz · 动程 %.1f Bark",
                                                        primaryTraj.vowelType.chineseLabel.substringBefore(' '),
                                                        primaryTraj.nucleusF1,
                                                        primaryTraj.nucleusF2,
                                                        primaryTraj.barkGlideSpan
                                                    ),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (!isCurrentSpk) {
                                                IconButton(
                                                    onClick = { toggleOtherSpeakerVisibility(item.speaker.id) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isChartHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = if (isChartHidden) "显示叠绘曲线" else "隐藏叠绘曲线",
                                                        tint = if (isChartHidden) MaterialTheme.colorScheme.outline else spkDotCol,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                            FilledTonalIconButton(
                                                onClick = {
                                                    if (isSequentialPlaying) {
                                                        sequentialPlayJob?.cancel()
                                                        isSequentialPlaying = false
                                                    }
                                                    viewModel.playSpecificAudioFile(item.audioFile, tagId)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = S.archive.listenSpeakerDesc.withArgs("name" to item.speaker.name),
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

                // Section 1C: Multi-Take Switcher inside Details Sheet (Feature #6)
                if (availableTakes.size >= 2 && viewModel != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = S.archive.multiTakeSectionTitle.withArgs("count" to availableTakes.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableTakes.forEachIndexed { idx, file ->
                                val isCurrentTake = file.name == word.recordingFile
                                val takePlayId = -(word.id * 100 + idx + 1)
                                val isPlayingTake = playingWordId == takePlayId
                                val durSec = remember(file.name, file.length()) {
                                    AcousticAnalyzer.getExactWavDurationSec(file)
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrentTake) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCurrentTake) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (idx == 0) {
                                                    S.archive.latestTakeLabel.withArgs("count" to availableTakes.size)
                                                } else {
                                                    S.archive.historyTakeLabel.withArgs("num" to availableTakes.size - idx)
                                                },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = S.archive.takeDurationAndState.withArgs(
                                                    "sec" to String.format(java.util.Locale.US, "%.2f", durSec),
                                                    "state" to if (isCurrentTake) S.archive.activeTakeState else S.archive.backupTakeState
                                                ),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilledTonalIconButton(
                                                onClick = { viewModel.playSpecificAudioFile(file, takePlayId) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingTake) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = S.archive.listenThisTakeDesc,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            if (!isCurrentTake) {
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.selectTakeForWord(word, file)
                                                        Toast.makeText(context, S.archive.switchedTakeToast, Toast.LENGTH_SHORT).show()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text(S.archive.adoptTakeButton, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 1C: Wideband Spectrogram (0..5000 Hz) + Formant & Pitch Track Overlay
                Text(
                    text = S.archive.widebandAndFormantTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                SingleWordWidebandSpectrogramCard(
                    audioFile = activeAudioFile,
                    speakerGender = speaker?.gender,
                    pitchRange = activeSpeakerPitchRange,
                    isPlaying = playingWordId == word.id,
                    onTogglePlay = if (viewModel != null) {
                        { viewModel.playWordAudio(word) }
                    } else {
                        null
                    },
                    segments = analysis.segments
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Section 2: Dynamic Vowel Formants (Monophthong Nucleus vs Diphthong/Triphthong Trajectory)
                Text(
                    text = S.archive.vowelSpaceTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                SingleWordDynamicVowelCard(
                    word = word,
                    speaker = speaker,
                    analysis = analysis,
                    otherSpeakersEntries = otherSpeakersEntries,
                    hiddenOtherSpeakerIds = hiddenOtherSpeakerIds,
                    onToggleOtherSpeakerVisibility = toggleOtherSpeakerVisibility
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Section 3: Clean File Info Card
                Text(
                    text = S.archive.audioFileInfoTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.archive.audioSpecLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(76.dp)
                            )
                            Text(
                                text = S.archive.audioSpecValue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.archive.fileSizeLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(76.dp)
                            )
                            Text(
                                text = S.archive.fileSizeKbFormat.withArgs("kb" to String.format(java.util.Locale.US, "%.1f", analysis.fileSizeKb)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = S.archive.fileNameLabel,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(76.dp)
                            )
                            Text(
                                text = word.recordingFile ?: S.archive.unrecordedPlaceholder,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 4: Praat .TextGrid Live Preview & Direct Single-File Share
                val singleWordTgConfig = viewModel?.textGridFormatConfig?.collectAsState()?.value
                    ?: com.example.utils.TextGridFormatConfig()
                val praatPreviewPair by produceState<Pair<String, AcousticAnalyzer.AnalysisResult?>>(
                    initialValue = "" to null,
                    keys = arrayOf(word.id, word.recordingFile, speaker?.id, singleWordTgConfig)
                ) {
                    value = withContext(Dispatchers.IO) {
                        val recFile = word.recordingFile?.let {
                            File(File(context.getExternalFilesDir(null), "project_${word.projectId}"), it)
                        }
                        val exactDur = AcousticAnalyzer.getExactWavDurationSec(recFile)
                        val spPrefs = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
                        val pMin = spPrefs.getFloat("pitch_min_${speaker?.id ?: -1L}", -1f)
                        val pMax = spPrefs.getFloat("pitch_max_${speaker?.id ?: -1L}", -1f)
                        val pRange = if (pMin > 40f && pMax > pMin + 15f) Pair(pMin, pMax) else null
                        val parts = ZipUtility.extractSyllableLabels(word.text)
                        val rawResult = if (recFile != null && recFile.exists()) {
                            AcousticAnalyzer.analyzeByGender(
                                file = recFile,
                                speakerGender = speaker?.gender,
                                segmentCount = parts.size.coerceAtLeast(1),
                                pitchRange = pRange,
                                segmentLabels = parts
                            )
                        } else null
                        ZipUtility.buildPraatTextGrid(
                            word = word,
                            analysis = rawResult,
                            exactDurationSec = exactDur,
                            config = singleWordTgConfig
                        ) to rawResult
                    }
                }
                val textGridPreview = praatPreviewPair.first
                val rawAnalysisResult = praatPreviewPair.second

                val saveSingleWordTextGridLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    if (uri != null && textGridPreview.isNotBlank()) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    context.contentResolver.openOutputStream(uri)?.use { os ->
                                        os.write(textGridPreview.toByteArray(Charsets.UTF_8))
                                        os.flush()
                                        true
                                    } ?: false
                                }.getOrDefault(false)
                            }
                            Toast.makeText(
                                context,
                                if (ok) S.archive.savedSingleTextGridToast else S.archive.exportFailedShortToast,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                val saveSingleWordCollectionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    val wav = activeAudioFile
                    if (uri != null && wav != null && wav.exists()) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri)?.use { os ->
                                    PraatCollectionExporter.exportSingleWordPraatCollection(
                                        wavFile = wav,
                                        word = word,
                                        speaker = speaker,
                                        analysis = rawAnalysisResult,
                                        textGridConfig = singleWordTgConfig,
                                        targetStream = os
                                    )
                                } ?: false
                            }
                            Toast.makeText(
                                context,
                                if (ok) S.archive.savedSinglePraatCollectionToast else S.archive.exportFailedShortToast,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = S.archive.praatExportSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = S.archive.currentTextGridFormatLine.withArgs("summary" to singleWordTgConfig.shortSummaryLabel()),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (textGridPreview.isNotBlank()) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Praat TextGrid", textGridPreview))
                                    Toast.makeText(context, S.archive.copiedTextGridToast, Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(S.common.copy, fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = {
                                if (textGridPreview.isNotBlank()) {
                                    val spkCode = String.format(java.util.Locale.US, "SPK%02d", speaker?.id ?: 1L)
                                    val idxStr = String.format(java.util.Locale.US, "%03d", word.originalIndex + 1)
                                    val safeWord = word.text.replace(Regex("[/\\\\:*?\"<>|\\s]+"), "_").take(20)
                                    val fileName = "${spkCode}_${idxStr}_${safeWord}.TextGrid"
                                    if (DesktopAdaptiveState.isDesktopPlatform) {
                                        saveSingleWordTextGridLauncher.launch(fileName)
                                    } else {
                                        try {
                                            val cacheDir = File(context.cacheDir, "exports")
                                            cacheDir.mkdirs()
                                            val tgFile = File(cacheDir, fileName)
                                            tgFile.writeText(textGridPreview, Charsets.UTF_8)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                tgFile
                                            )
                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                android.content.Intent.createChooser(shareIntent, S.archive.shareSingleTextGridTitle)
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(context, S.archive.exportFailedMsgToast.withArgs("msg" to e.message), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("export_single_word_textgrid_button")
                        ) {
                            Text(".TextGrid", fontSize = 11.sp, maxLines = 1, softWrap = false)
                        }
                        if (activeAudioFile != null && activeAudioFile.exists()) {
                            FilledTonalButton(
                                onClick = {
                                    val spkCode = String.format(java.util.Locale.US, "SPK%02d", speaker?.id ?: 1L)
                                    val idxStr = String.format(java.util.Locale.US, "%03d", word.originalIndex + 1)
                                    val safeWord = PraatCollectionExporter.sanitizePraatObjectName(word.text, "word").take(20)
                                    val fileName = "${spkCode}_${idxStr}_${safeWord}.Collection"
                                    if (DesktopAdaptiveState.isDesktopPlatform) {
                                        saveSingleWordCollectionLauncher.launch(fileName)
                                    } else {
                                        scope.launch {
                                            try {
                                                val cacheDir = File(context.cacheDir, "exports")
                                                cacheDir.mkdirs()
                                                val colFile = File(cacheDir, fileName)
                                                val ok = withContext(Dispatchers.IO) {
                                                    FileOutputStream(colFile).use { fos ->
                                                        PraatCollectionExporter.exportSingleWordPraatCollection(
                                                            wavFile = activeAudioFile,
                                                            word = word,
                                                            speaker = speaker,
                                                            analysis = rawAnalysisResult,
                                                            textGridConfig = singleWordTgConfig,
                                                            targetStream = fos
                                                        )
                                                    }
                                                }
                                                if (ok) {
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        colFile
                                                    )
                                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "application/octet-stream"
                                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(
                                                        android.content.Intent.createChooser(shareIntent, S.archive.shareSingleCollectionTitle)
                                                    )
                                                } else {
                                                    Toast.makeText(context, S.archive.readAudioFailedToast, Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, S.archive.exportFailedMsgToast.withArgs("msg" to e.message), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("export_single_word_praat_collection_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(".Collection", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = textGridPreview.ifBlank { S.archive.generatingTextGridPreview },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(14.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
            PlatformVerticalScrollbar(
                scrollState = bodyScrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
            }
        }
        }
    }
}
