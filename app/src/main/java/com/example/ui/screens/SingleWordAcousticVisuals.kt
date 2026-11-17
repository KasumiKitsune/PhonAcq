package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.*
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.StudioFilterChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.*

/**
 * Dual-layer Tone Summary Banner showing Corpus Canonical Tone Category alongside Raw Token Value,
 * plus Checked Tone (入声/促声) badge and Sandhi/Outlier alert when applicable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleWordCanonicalToneBanner(
    analysis: AcousticAnalyzer.PhoneticAcousticResult,
    canonicalMember: CanonicalToneMember?,
    modifier: Modifier = Modifier
) {
    if (!analysis.isValidVoiced) return

    val isPolysyllabic = analysis.segmentCount > 1
    val canonicalChao = canonicalMember?.canonicalChao?.ifBlank { analysis.canonicalToneNumbers }
        ?: analysis.canonicalToneNumbers
    val canonicalName = canonicalMember?.canonicalCategoryName?.ifBlank { analysis.canonicalToneName }
        ?: analysis.canonicalToneName
    val isOutlier = canonicalMember?.isOutlier ?: analysis.isToneOutlier

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isPolysyllabic) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = S.acoustics.polysyllableSandhiHeader.withArgs(
                                "count" to analysis.segmentCount,
                                "chao" to analysis.chaoToneNumbers
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    for (idx in 0 until analysis.segmentCount) {
                        val rawLbl = analysis.segments.getOrNull(idx)?.label?.ifBlank { null }
                            ?: S.common.syllableFallback.withArgs("index" to idx + 1)
                        val tNum = analysis.syllableToneNumbers.getOrNull(idx) ?: "--"
                        val isNeutral = analysis.syllableNeutralFlags.getOrElse(idx) { false }
                        val isChecked = analysis.syllableCheckedFlags.getOrElse(idx) { false }
                        val vMs = analysis.syllableVoicedMs.getOrElse(idx) { 0 }
                        val tagSuffix = when {
                            isNeutral -> S.acoustics.neutralShortLabel
                            isChecked -> S.acoustics.checkedShortLabel
                            else -> ""
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isNeutral -> MaterialTheme.colorScheme.secondaryContainer
                                isChecked -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Text(
                                text = "${circledSyllableBadge(idx + 1)} $rawLbl [$tNum$tagSuffix]" +
                                    if (vMs > 0) " ${vMs}ms" else "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isNeutral -> MaterialTheme.colorScheme.onSecondaryContainer
                                    isChecked -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (canonicalChao.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = S.acoustics.corpusCanonicalBelongsPrefix.withArgs(
                                    "label" to canonicalName.ifBlank {
                                        S.acoustics.canonicalToneFallbackLabel.withArgs("chao" to canonicalChao)
                                    }
                                ),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        val durType = if (analysis.isCheckedTone) S.acoustics.checkedShortToneTag else S.acoustics.smoothToneTag
                        val voicedText = if (analysis.voicedDurationMs > 0) {
                            S.acoustics.voicedRimeDurationSegment.withArgs("ms" to analysis.voicedDurationMs)
                        } else ""
                        Text(
                            text = S.acoustics.tokenMeasuredLine.withArgs(
                                "chao" to analysis.chaoToneNumbers,
                                "durType" to durType,
                                "voicedText" to voicedText
                            ),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (analysis.isCheckedTone) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = S.acoustics.checkedShortRimeBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (isOutlier) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = S.acoustics.outlierDeviationWarning,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun circledSyllableBadge(idx1Based: Int): String {
    val badges = arrayOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧")
    return if (idx1Based in 1..badges.size) badges[idx1Based - 1] else "($idx1Based)"
}

/**
 * Wideband Spectrogram (0..5000 Hz) + F1/F2/F3 Formant Track + F0 Pitch Track + Active Core Window
 * with live synchronized audio playback cursor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleWordWidebandSpectrogramCard(
    audioFile: File?,
    speakerGender: String?,
    pitchRange: Pair<Float, Float>?,
    isPlaying: Boolean,
    onTogglePlay: (() -> Unit)?,
    segments: List<AcousticAnalyzer.SegmentInterval> = emptyList(),
    modifier: Modifier = Modifier
) {
    var showFormants by remember { mutableStateOf(true) }
    var showPitchTrack by remember { mutableStateOf(true) }
    var showCoreWindow by remember { mutableStateOf(true) }
    val specTextMeasurer = rememberTextMeasurer()

    val specData by produceState<WidebandSpectrogramData?>(
        initialValue = null,
        key1 = audioFile?.absolutePath,
        key2 = audioFile?.lastModified(),
        key3 = speakerGender
    ) {
        value = withContext(Dispatchers.Default) {
            FormantTrajectoryAndSpectrogramExtractor.extractWidebandSpectrogram(
                file = audioFile,
                speakerGender = speakerGender,
                pitchRange = pitchRange
            )
        }
    }

    // Track real-time playback cursor progress synced to VSync without recomposing the card
    var playProgressSec by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(isPlaying, specData) {
        val data = specData
        if (isPlaying && data != null && data.durationSec > 0.05f) {
            val startNs = withFrameNanos { it }
            while (isPlaying) {
                val nowNs = withFrameNanos { it }
                val elapsedSec = (nowNs - startNs) / 1_000_000_000f
                playProgressSec = elapsedSec.coerceAtMost(data.durationSec)
                if (elapsedSec > data.durationSec + 0.08f) break
            }
        } else {
            playProgressSec = -1f
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("single_word_spectrogram_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = S.acoustics.widebandTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = S.acoustics.widebandLongDescription,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onTogglePlay != null && audioFile != null && audioFile.exists()) {
                    FilledTonalIconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("spectrogram_play_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) S.acoustics.stopListenDesc else S.acoustics.syncPlaySpectrogramDesc,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StudioFilterChip(
                    selected = showFormants,
                    onClick = { showFormants = !showFormants },
                    labelText = S.acoustics.formantTrackLegend,
                    modifier = Modifier.testTag("toggle_spectrogram_formants")
                )
                StudioFilterChip(
                    selected = showPitchTrack,
                    onClick = { showPitchTrack = !showPitchTrack },
                    labelText = S.acoustics.f0TrackLegend,
                    modifier = Modifier.testTag("toggle_spectrogram_f0")
                )
                StudioFilterChip(
                    selected = showCoreWindow,
                    onClick = { showCoreWindow = !showCoreWindow },
                    labelText = S.acoustics.rimeWindowLegend,
                    modifier = Modifier.testTag("toggle_spectrogram_core_window")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val data = specData
            val colorScheme = MaterialTheme.colorScheme
            val isDark = colorScheme.surface.luminance() < 0.4f
            val specBg = remember(colorScheme, isDark) {
                if (isDark) {
                    lerp(colorScheme.surfaceContainerLowest, colorScheme.primaryContainer, 0.12f)
                } else {
                    lerp(colorScheme.surface, colorScheme.primaryContainer, 0.14f)
                }
            }
            val specPalette = remember(colorScheme, isDark, specBg) {
                val lowTone = if (isDark) {
                    lerp(specBg, colorScheme.primaryContainer, 0.88f)
                } else {
                    lerp(colorScheme.secondaryContainer, colorScheme.primaryContainer, 0.72f)
                }
                val midTone = if (isDark) {
                    lerp(colorScheme.primaryContainer, colorScheme.primary, 0.86f)
                } else {
                    lerp(colorScheme.primaryContainer, colorScheme.primary, 0.84f)
                }
                val peakTone = if (isDark) {
                    lerp(colorScheme.primary, colorScheme.onPrimaryContainer, 0.70f)
                } else {
                    lerp(colorScheme.primary, colorScheme.onPrimaryContainer, 0.86f)
                }
                Array(64) { idx ->
                    val u = idx / 63f
                    when {
                        u < 0.36f -> lerp(specBg, lowTone, u / 0.36f)
                        u < 0.74f -> lerp(lowTone, midTone, (u - 0.36f) / 0.38f)
                        else -> lerp(midTone, peakTone, (u - 0.74f) / 0.26f)
                    }
                }
            }
            val gridLineColor = colorScheme.outline.copy(alpha = if (isDark) 0.26f else 0.22f)
            val coreWinFill = colorScheme.tertiaryContainer.copy(alpha = if (isDark) 0.22f else 0.28f)
            val coreWinStroke = colorScheme.tertiary.copy(alpha = 0.88f)
            val f1Col = remember(colorScheme, isDark) {
                lerp(if (isDark) Color(0xFFFF7043) else Color(0xFFE64A19), colorScheme.error, 0.22f)
            }
            val f2Col = remember(colorScheme, isDark) {
                lerp(if (isDark) Color(0xFFFFCA28) else Color(0xFFF57F17), colorScheme.tertiary, 0.18f)
            }
            val f3Col = remember(colorScheme, isDark) {
                lerp(if (isDark) Color(0xFFCE93D8) else Color(0xFF8E24AA), colorScheme.secondary, 0.22f)
            }
            val f0Color = remember(colorScheme, isDark) {
                lerp(colorScheme.tertiary, if (isDark) Color(0xFF4FC3F7) else Color(0xFF00687A), 0.48f)
            }
            val cursorColor = colorScheme.tertiary

            val specBitmap = remember(data, specBg, specPalette) {
                if (data == null) {
                    null
                } else {
                    val cols = data.timeCols.coerceAtLeast(1)
                    val bins = data.freqBins.coerceAtLeast(1)
                    val bgArgb = specBg.toArgb()
                    val paletteArgb = IntArray(specPalette.size) { specPalette[it].toArgb() }
                    val pixels = IntArray(cols * bins) { bgArgb }
                    val grid = data.normalizedDbGrid
                    for (c in 0 until cols) {
                        val colBase = c * bins
                        for (b in 0 until bins) {
                            val idx = colBase + b
                            if (idx < grid.size) {
                                val v = grid[idx]
                                if (v > 0.05f) {
                                    val norm = ((v - 0.05f) / 0.95f).coerceIn(0f, 1f).pow(1.12f)
                                    val lutIdx = (norm * 63f).toInt().coerceIn(0, 63)
                                    val row = bins - 1 - b
                                    pixels[row * cols + c] = paletteArgb[lutIdx]
                                }
                            }
                        }
                    }
                    android.graphics.Bitmap.createBitmap(
                        pixels,
                        cols,
                        bins,
                        android.graphics.Bitmap.Config.ARGB_8888
                    ).asImageBitmap()
                }
            }

            if (data == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .background(specBg, RoundedCornerShape(10.dp))
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (audioFile == null || !audioFile.exists()) {
                        Text(S.acoustics.noAudioForSpectrogram, fontSize = 12.sp, color = colorScheme.outline)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Frequency Axis (5kHz -> 0Hz)
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(34.dp)
                            .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("5k", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = colorScheme.outline)
                        Text("3k", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = colorScheme.outline)
                        Text("2k", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = colorScheme.outline)
                        Text("1k", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = colorScheme.outline)
                        Text("0", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(specBg)
                            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // 1. Blit cached Material You wideband spectrogram bitmap in 1 GPU draw call
                            if (specBitmap != null) {
                                drawImage(
                                    image = specBitmap,
                                    dstSize = IntSize(
                                        w.roundToInt().coerceAtLeast(1),
                                        h.roundToInt().coerceAtLeast(1)
                                    ),
                                    filterQuality = FilterQuality.Low
                                )
                            }

                            val winSpan = (data.windowEndSec - data.windowStartSec).coerceAtLeast(0.02f)
                            fun timeToX(sec: Float): Float =
                                (((sec - data.windowStartSec) / winSpan) * w).coerceIn(0f, w)
                            fun freqToY(hz: Float): Float =
                                (h - (hz / data.maxFreqHz.toFloat()) * h).coerceIn(2f, h - 2f)

                            // Subtle horizontal kHz guide lines
                            for (khz in 1..4) {
                                val gy = freqToY(khz * 1000f)
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(0f, gy),
                                    end = Offset(w, gy),
                                    strokeWidth = 0.8.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                                )
                            }

                            // 2. Highlight active vowel rime measurement core window (or multi-syllable boundaries)
                            if (showCoreWindow && segments.size <= 1) {
                                val cx0 = timeToX(data.coreStartSec)
                                val cx1 = timeToX(data.coreEndSec)
                                if (cx1 > cx0 + 4f) {
                                    drawRect(
                                        color = coreWinFill,
                                        topLeft = Offset(cx0, 0f),
                                        size = Size(cx1 - cx0, h)
                                    )
                                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
                                    drawLine(
                                        color = coreWinStroke,
                                        start = Offset(cx0, 0f),
                                        end = Offset(cx0, h),
                                        strokeWidth = 1.3.dp.toPx(),
                                        pathEffect = dash
                                    )
                                    drawLine(
                                        color = coreWinStroke,
                                        start = Offset(cx1, 0f),
                                        end = Offset(cx1, h),
                                        strokeWidth = 1.3.dp.toPx(),
                                        pathEffect = dash
                                    )
                                }
                            } else if (segments.size > 1) {
                                val sylDash = PathEffect.dashPathEffect(floatArrayOf(7f, 5f), 0f)
                                segments.forEachIndexed { idx, seg ->
                                    val sx0 = timeToX(seg.startSec.toFloat())
                                    val sx1 = timeToX(seg.endSec.toFloat())
                                    if (idx > 0 && sx0 in 2f..(w - 2f)) {
                                        drawLine(
                                            color = coreWinStroke,
                                            start = Offset(sx0, 0f),
                                            end = Offset(sx0, h),
                                            strokeWidth = 1.5.dp.toPx(),
                                            pathEffect = sylDash
                                        )
                                    }
                                    val midX = ((sx0 + sx1) * 0.5f).coerceIn(10f, (w - 28f).coerceAtLeast(10f))
                                    val badgeTxt = "${circledSyllableBadge(idx + 1)}${seg.label.take(2)}"
                                    val measured = specTextMeasurer.measure(
                                        text = badgeTxt,
                                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    )
                                    drawRoundRect(
                                        color = specBg.copy(alpha = 0.85f),
                                        topLeft = Offset((midX - measured.size.width * 0.5f - 3.dp.toPx()).coerceAtLeast(2f), 3.dp.toPx()),
                                        size = Size(measured.size.width + 6.dp.toPx(), measured.size.height + 2.dp.toPx()),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                    drawText(
                                        textLayoutResult = measured,
                                        color = coreWinStroke,
                                        topLeft = Offset((midX - measured.size.width * 0.5f).coerceAtLeast(5f), 4.dp.toPx())
                                    )
                                }
                            }

                            // 3. Overlay F1 / F2 / F3 Formant Tracks with crisp surface halo
                            if (showFormants && data.formantTrackPoints.isNotEmpty()) {
                                val pts = data.formantTrackPoints
                                val haloColor = specBg.copy(alpha = 0.90f)
                                fun drawFormantBand(selector: (FormantAnchorFrame) -> Int, col: Color, radiusDp: Float) {
                                    val path = Path()
                                    var started = false
                                    val dotCenters = ArrayList<Offset>(pts.size)
                                    pts.forEach { frame ->
                                        val hz = selector(frame)
                                        if (hz in 150..data.maxFreqHz) {
                                            val px = timeToX(frame.timeSec)
                                            val py = freqToY(hz.toFloat())
                                            val pt = Offset(px, py)
                                            dotCenters.add(pt)
                                            if (!started) {
                                                path.moveTo(px, py)
                                                started = true
                                            } else {
                                                path.lineTo(px, py)
                                            }
                                        }
                                    }
                                    if (started) {
                                        drawPath(
                                            path = path,
                                            color = haloColor,
                                            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                        drawPath(
                                            path = path,
                                            color = col.copy(alpha = 0.90f),
                                            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                    }
                                    dotCenters.forEach { pt ->
                                        drawCircle(
                                            color = haloColor,
                                            radius = (radiusDp + 1.2f).dp.toPx(),
                                            center = pt
                                        )
                                        drawCircle(
                                            color = col,
                                            radius = radiusDp.dp.toPx(),
                                            center = pt
                                        )
                                    }
                                }
                                drawFormantBand({ it.f3Hz }, f3Col, 2.3f)
                                drawFormantBand({ it.f2Hz }, f2Col, 2.8f)
                                drawFormantBand({ it.f1Hz }, f1Col, 3.0f)
                            }

                            // 4. Overlay F0 Pitch Track (mapped to right-side scale with protective halo)
                            if (showPitchTrack && data.pitchTrackHz.isNotEmpty()) {
                                val validPitches = data.pitchTrackHz.filter { it > 0f }
                                if (validPitches.isNotEmpty()) {
                                    val f0Floor = (validPitches.minOrNull()!! * 0.82f).coerceIn(55f, 130f)
                                    val f0Ceil = (validPitches.maxOrNull()!! * 1.18f).coerceIn(f0Floor + 80f, 500f)

                                    val pPath = Path()
                                    var penDown = false
                                    for (i in data.pitchTrackHz.indices) {
                                        val hz = data.pitchTrackHz[i]
                                        if (hz > 0f) {
                                            val px = timeToX(data.pitchTrackTimeSec[i])
                                            val norm = ((hz - f0Floor) / (f0Ceil - f0Floor)).coerceIn(0.05f, 0.95f)
                                            val py = h - norm * h
                                            if (!penDown) {
                                                pPath.moveTo(px, py)
                                                penDown = true
                                            } else {
                                                pPath.lineTo(px, py)
                                            }
                                        } else {
                                            penDown = false
                                        }
                                    }
                                    drawPath(
                                        path = pPath,
                                        color = specBg.copy(alpha = 0.82f),
                                        style = Stroke(width = 4.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                    drawPath(
                                        path = pPath,
                                        color = f0Color,
                                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }

                        }

                        // 5. Isolated 60fps Live Playback Cursor Overlay (only redraws 2 lines, never invalidates spectrogram)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val curSec = playProgressSec
                            if (curSec >= 0f) {
                                val winSpan = (data.windowEndSec - data.windowStartSec).coerceAtLeast(0.02f)
                                val cursorX = (((curSec - data.windowStartSec) / winSpan) * size.width).coerceIn(0f, size.width)
                                drawLine(
                                    color = specBg.copy(alpha = 0.75f),
                                    start = Offset(cursorX, 0f),
                                    end = Offset(cursorX, size.height),
                                    strokeWidth = 4.0.dp.toPx()
                                )
                                drawLine(
                                    color = cursorColor,
                                    start = Offset(cursorX, 0f),
                                    end = Offset(cursorX, size.height),
                                    strokeWidth = 2.2.dp.toPx()
                                )
                            }
                        }
                    }

                    // Right F0 Axis indicator
                    if (showPitchTrack) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(32.dp)
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(S.acoustics.f0HighLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = f0Color)
                            Text(S.acoustics.f0MidLabel, fontSize = 8.sp, color = f0Color.copy(alpha = 0.78f))
                            Text(S.acoustics.f0LowLabel, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = f0Color)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 38.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "%.0f ms", data.windowStartSec * 1000f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.outline
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LegendDotLabel(f1Col, "F1")
                        LegendDotLabel(f2Col, "F2")
                        LegendDotLabel(f3Col, "F3")
                        LegendDotLabel(f0Color, S.acoustics.f0LegendShort)
                    }
                    Text(
                        text = String.format(Locale.US, "%.0f ms", data.windowEndSec * 1000f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDotLabel(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Dynamic Vowel Space Card for a single word:
 * - Automatically distinguishes Monophthongs (steady nucleus point) vs Diphthongs/Triphthongs
 *   (smooth curved trajectory with Onset hollow ring -> Nucleus -> Target directional arrow!).
 * - Supports optional cross-speaker trajectory overlay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleWordDynamicVowelCard(
    word: Word,
    speaker: Speaker?,
    analysis: AcousticAnalyzer.PhoneticAcousticResult,
    otherSpeakersEntries: List<CrossSpeakerWordAnalysis> = emptyList(),
    hiddenOtherSpeakerIds: Set<Long> = emptySet(),
    onToggleOtherSpeakerVisibility: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var overlayOtherSpeakers by remember { mutableStateOf(otherSpeakersEntries.isNotEmpty()) }
    var useVocalTractNormOnOverlay by remember { mutableStateOf(true) }
    var localHiddenIds by remember(word.id) { mutableStateOf(emptySet<Long>()) }
    val effectiveHiddenIds = if (onToggleOtherSpeakerVisibility != null) hiddenOtherSpeakerIds else localHiddenIds
    var selectedSyllableIndex by remember(word.id, analysis.formantTrajectories.size) { mutableStateOf(-1) } // -1 = 全部音节

    val trajectories = analysis.formantTrajectories
    val isPolysyllabic = trajectories.size > 1 || analysis.segmentCount > 1
    val focusedTraj = if (selectedSyllableIndex in trajectories.indices) {
        trajectories[selectedSyllableIndex]
    } else {
        trajectories.firstOrNull()
    }
    val vowelTextMeasurer = rememberTextMeasurer()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("single_word_dynamic_vowel_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                isPolysyllabic && selectedSyllableIndex < 0 ->
                                    S.acoustics.polysyllableTrajectoryTitle.withArgs(
                                        "count" to trajectories.size.coerceAtLeast(analysis.segmentCount)
                                    )
                                focusedTraj != null && focusedTraj.vowelType != VowelGlideType.MONOPHTHONG ->
                                    S.acoustics.diphthongTrajectoryTitle.withArgs("type" to focusedTraj.vowelType.chineseLabel)
                                else ->
                                    S.acoustics.monophthongSteadyBarkTitle
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = when {
                            isPolysyllabic && selectedSyllableIndex < 0 ->
                                S.acoustics.polysyllableTrajectoryExplanation
                            focusedTraj != null -> focusedTraj.trajectorySummary
                            else -> S.acoustics.f1F2ArticulatoryExplanation
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (otherSpeakersEntries.isNotEmpty()) {
                    StudioFilterChip(
                        selected = overlayOtherSpeakers,
                        onClick = { overlayOtherSpeakers = !overlayOtherSpeakers },
                        labelText = S.acoustics.multiSpeakerOverlayChip
                    )
                }
            }

            if (otherSpeakersEntries.isNotEmpty() && overlayOtherSpeakers) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "跨人坐标:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioFilterChip(
                        selected = useVocalTractNormOnOverlay,
                        onClick = { useVocalTractNormOnOverlay = true },
                        labelText = "声道归一化相对舌位"
                    )
                    StudioFilterChip(
                        selected = !useVocalTractNormOnOverlay,
                        onClick = { useVocalTractNormOnOverlay = false },
                        labelText = "原始 Bark 物理频率"
                    )
                }
            }

            // Syllable Selector Pills when Polysyllabic
            if (trajectories.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StudioFilterChip(
                        selected = selectedSyllableIndex == -1,
                        onClick = { selectedSyllableIndex = -1 },
                        labelText = S.acoustics.allSyllablesChip.withArgs("count" to trajectories.size)
                    )
                    trajectories.forEachIndexed { idx, t ->
                        val lbl = t.syllableLabel.ifBlank { S.common.syllableFallback.withArgs("index" to idx + 1) }
                        StudioFilterChip(
                            selected = selectedSyllableIndex == idx,
                            onClick = { selectedSyllableIndex = if (selectedSyllableIndex == idx) -1 else idx },
                            labelText = "${circledSyllableBadge(idx + 1)} $lbl (${t.vowelType.chineseLabel.substringBefore(' ')})"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Adaptive Formant Readout Strip:
            // - All-syllables mode on polysyllabic word: compact per-syllable F1/F2 cards
            // - Single-syllable / Diphthong focus: Onset -> Nucleus -> Target
            // - Monophthong focus: F1 / F2 / F3
            if (trajectories.size > 1 && selectedSyllableIndex == -1) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trajectories.forEachIndexed { idx, t ->
                        val vDesc = if (t.vowelType == VowelGlideType.MONOPHTHONG) {
                            S.acoustics.steadyRimeSuffix.withArgs(
                                "region" to PhoneticScaleUtils.describeArticulatoryRegion(t.nucleusF1, t.nucleusF2)
                            )
                        } else {
                            String.format(Locale.US, "%s · %.1f Bark", t.vowelType.chineseLabel.substringBefore(' '), t.barkGlideSpan)
                        }
                        val freqVal = if (t.vowelType == VowelGlideType.MONOPHTHONG) {
                            "${t.nucleusF1}/${t.nucleusF2} Hz"
                        } else {
                            "${t.onsetF1}→${t.targetF1} / ${t.onsetF2}→${t.targetF2}"
                        }
                        FormantMetricBox(
                            title = "${circledSyllableBadge(idx + 1)} ${t.syllableLabel.ifBlank { S.common.syllableFallback.withArgs("index" to idx + 1) }}",
                            value = freqVal,
                            sub = vDesc,
                            highlight = idx == 0,
                            modifier = Modifier.widthIn(min = 128.dp)
                        )
                    }
                }
            } else if (focusedTraj != null && focusedTraj.vowelType != VowelGlideType.MONOPHTHONG) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormantMetricBox(
                        title = S.acoustics.onsetTitle,
                        value = "${focusedTraj.onsetF1}/${focusedTraj.onsetF2} Hz",
                        sub = PhoneticScaleUtils.describeArticulatoryRegion(focusedTraj.onsetF1, focusedTraj.onsetF2),
                        modifier = Modifier.weight(1f)
                    )
                    FormantMetricBox(
                        title = if (focusedTraj.vowelType == VowelGlideType.TRIPHTHONG) S.acoustics.triphthongMidTitle else S.acoustics.transitionMidTitle,
                        value = "${focusedTraj.nucleusF1}/${focusedTraj.nucleusF2} Hz",
                        sub = "F3: ${if (focusedTraj.nucleusF3 > 0) "${focusedTraj.nucleusF3}Hz" else "-"}",
                        modifier = Modifier.weight(1f)
                    )
                    FormantMetricBox(
                        title = S.acoustics.targetTitle,
                        value = "${focusedTraj.targetF1}/${focusedTraj.targetF2} Hz",
                        sub = S.acoustics.glideSpanBarkFormat.withArgs("span" to String.format(Locale.US, "%.1f", focusedTraj.barkGlideSpan)),
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val f1Txt = if (focusedTraj != null && focusedTraj.nucleusF1 > 0) {
                        "${focusedTraj.nucleusF1} Hz"
                    } else if (analysis.speakerF1.isNotEmpty()) {
                        "${analysis.speakerF1.joinToString(", ")} Hz"
                    } else S.acoustics.pitchUnavailable
                    val f2Txt = if (focusedTraj != null && focusedTraj.nucleusF2 > 0) {
                        "${focusedTraj.nucleusF2} Hz"
                    } else if (analysis.speakerF2.isNotEmpty()) {
                        "${analysis.speakerF2.joinToString(", ")} Hz"
                    } else S.acoustics.pitchUnavailable
                    val f3Val = focusedTraj?.nucleusF3 ?: analysis.speakerF3.firstOrNull() ?: 0
                    FormantMetricBox(
                        title = S.acoustics.f1HeightTitle,
                        value = f1Txt,
                        sub = S.acoustics.f1HeightSubExplanation,
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                    FormantMetricBox(
                        title = S.acoustics.f2BacknessTitle,
                        value = f2Txt,
                        sub = S.acoustics.f2BacknessSubExplanation,
                        highlight = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (f3Val > 0) {
                        FormantMetricBox(
                            title = S.acoustics.f3RoundingTitle,
                            value = "$f3Val Hz",
                            sub = if (f3Val < 2150) S.acoustics.f3RetroflexSub else S.acoustics.f3NeutralSub,
                            modifier = Modifier.weight(0.95f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val vowelPlotBg = MaterialTheme.colorScheme.surface
            val boundaryColor = MaterialTheme.colorScheme.outlineVariant
            val textHintColor = MaterialTheme.colorScheme.onSurfaceVariant
            val interSylLinkColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(212.dp)
                    .background(vowelPlotBg, shape = RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(S.acoustics.cornerFrontHighI, fontSize = 10.sp, color = textHintColor, modifier = Modifier.align(Alignment.TopStart))
                Text(S.acoustics.cornerAxisF2, fontSize = 9.sp, color = textHintColor.copy(alpha = 0.72f), modifier = Modifier.align(Alignment.TopCenter))
                Text(S.acoustics.cornerBackHighU, fontSize = 10.sp, color = textHintColor, modifier = Modifier.align(Alignment.TopEnd))
                Text(S.acoustics.cornerCentralLowA, fontSize = 10.sp, color = textHintColor, modifier = Modifier.align(Alignment.BottomCenter))

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val padH = 18.dp.toPx()
                    val padV = 18.dp.toPx()
                    val plotW = (size.width - padH * 2).coerceAtLeast(10f)
                    val plotH = (size.height - padV * 2).coerceAtLeast(10f)

                    val b2Min = PhoneticScaleUtils.hzToBark(600f)
                    val b2Max = PhoneticScaleUtils.hzToBark(2680f)
                    val b1Min = PhoneticScaleUtils.hzToBark(220f)
                    val b1Max = PhoneticScaleUtils.hzToBark(960f)

                    fun mapToCanvas(f1: Int, f2: Int): Offset {
                        val xNorm = ((b2Max - PhoneticScaleUtils.hzToBark(f2.toFloat())) / (b2Max - b2Min)).coerceIn(0f, 1f)
                        val yNorm = ((PhoneticScaleUtils.hzToBark(f1.toFloat()) - b1Min) / (b1Max - b1Min)).coerceIn(0f, 1f)
                        return Offset(padH + xNorm * plotW, padV + yNorm * plotH)
                    }

                    // Standard IPA perceptual vowel trapezoid (i - u - o - a - e)
                    val pI = mapToCanvas(280, 2350)
                    val pU = mapToCanvas(310, 820)
                    val pO = mapToCanvas(500, 900)
                    val pA = mapToCanvas(820, 1320)
                    val pAE = mapToCanvas(740, 1720)
                    val trPath = Path().apply {
                        moveTo(pI.x, pI.y)
                        lineTo(pU.x, pU.y)
                        lineTo(pO.x, pO.y)
                        lineTo(pA.x, pA.y)
                        lineTo(pAE.x, pAE.y)
                        close()
                    }
                    drawPath(
                        path = trPath,
                        color = boundaryColor,
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f), 0f)
                        )
                    )

                    val sylPalette = listOf(
                        Color(0xFFD93025),
                        Color(0xFF1976D2),
                        Color(0xFF2E7D32),
                        Color(0xFFEF6C00),
                        Color(0xFF8E24AA)
                    )

                    val cmpCols = listOf(
                        Color(0xFF00897B),
                        Color(0xFF8E24AA),
                        Color(0xFF5D4037),
                        Color(0xFFC2185B),
                        Color(0xFFEF6C00)
                    )

                    fun normalizeHzForSpeaker(spk: Speaker?, rawF1: Int, rawF2: Int): Pair<Int, Int> {
                        if (!overlayOtherSpeakers || !useVocalTractNormOnOverlay) return rawF1 to rawF2
                        val cachedRep = spk?.id?.let { CorpusPhonologyAnalyzer.lookupCachedSpeakerReport(word.projectId, it) }
                        if (cachedRep != null && cachedRep.vowelTokens.size >= 5 && cachedRep.lobanovValid) {
                            val m1 = cachedRep.vowelTokens.map { it.nucleusF1Hz }.average().toFloat()
                            val m2 = cachedRep.vowelTokens.map { it.nucleusF2Hz }.average().toFloat()
                            val s1 = sqrt(cachedRep.vowelTokens.map { (it.nucleusF1Hz - m1) * (it.nucleusF1Hz - m1) }.average()).toFloat().coerceAtLeast(80f)
                            val s2 = sqrt(cachedRep.vowelTokens.map { (it.nucleusF2Hz - m2) * (it.nucleusF2Hz - m2) }.average()).toFloat().coerceAtLeast(240f)
                            val z1 = ((rawF1 - m1) / s1).coerceIn(-2.6f, 2.6f)
                            val z2 = ((rawF2 - m2) / s2).coerceIn(-2.6f, 2.6f)
                            return PhoneticScaleUtils.lobanovZ1ToEquivHz(z1).roundToInt() to
                                PhoneticScaleUtils.lobanovZ2ToEquivHz(z2).roundToInt()
                        }
                        val g = spk?.gender?.trim()?.lowercase(Locale.ROOT).orEmpty()
                        val tractFactor = when {
                            g.contains("女") || g == "f" || g.contains("female") -> 0.865f
                            g.contains("男") || g == "m" || g.contains("male") -> 1.055f
                            else -> 1.0f
                        }
                        return (rawF1 * tractFactor).roundToInt().coerceIn(200, 980) to
                            (rawF2 * tractFactor).roundToInt().coerceIn(580, 2720)
                    }

                    fun mapActiveSpeaker(f1: Int, f2: Int): Offset {
                        val (nf1, nf2) = normalizeHzForSpeaker(speaker, f1, f2)
                        return mapToCanvas(nf1, nf2)
                    }

                    // Optional cross-speaker trajectories (dashed, with per-speaker vocal-tract normalization & visibility filter)
                    if (overlayOtherSpeakers) {
                        otherSpeakersEntries.forEachIndexed { idx, other ->
                            if (other.speaker.id in effectiveHiddenIds) return@forEachIndexed
                            val oTrajs = other.analysis.formantTrajectories
                            val col = cmpCols[idx % cmpCols.size].copy(alpha = 0.72f)
                            oTrajs.forEachIndexed { sIdx, oTraj ->
                                if (selectedSyllableIndex != -1 && selectedSyllableIndex != sIdx) return@forEachIndexed
                                if (oTraj.nucleusF1 > 0) {
                                    drawSyllableVowelElement(
                                        traj = oTraj,
                                        mapFn = { f1, f2 ->
                                            val (nf1, nf2) = normalizeHzForSpeaker(other.speaker, f1, f2)
                                            mapToCanvas(nf1, nf2)
                                        },
                                        color = col,
                                        dashed = true
                                    )
                                }
                            }
                        }
                    }

                    // Active speaker syllable trajectories / monophthong targets + dashed inter-syllable transition connectors
                    if (trajectories.isNotEmpty()) {
                        // Step A: Draw thin dashed inter-syllable transition connectors (╌╌╌▶) first so syllable cores sit cleanly on top
                        if (trajectories.size > 1 && selectedSyllableIndex == -1) {
                            for (k in 0 until trajectories.size - 1) {
                                val tCur = trajectories[k]
                                val tNext = trajectories[k + 1]
                                if (tCur.nucleusF1 > 0 && tNext.nucleusF1 > 0) {
                                    val fromPt = if (tCur.vowelType == VowelGlideType.MONOPHTHONG) {
                                        mapActiveSpeaker(tCur.nucleusF1, tCur.nucleusF2)
                                    } else {
                                        mapActiveSpeaker(tCur.targetF1, tCur.targetF2)
                                    }
                                    val toPt = if (tNext.vowelType == VowelGlideType.MONOPHTHONG) {
                                        mapActiveSpeaker(tNext.nucleusF1, tNext.nucleusF2)
                                    } else {
                                        mapActiveSpeaker(tNext.onsetF1, tNext.onsetF2)
                                    }
                                    drawInterSyllableDashedLink(
                                        from = fromPt,
                                        to = toPt,
                                        color = interSylLinkColor
                                    )
                                }
                            }
                        }

                        // Step B: Draw each syllable's vowel nucleus / internal diphthong arrow + numbered syllable badge
                        trajectories.forEachIndexed { sIdx, traj ->
                            val isDimmed = selectedSyllableIndex != -1 && selectedSyllableIndex != sIdx
                            if (traj.nucleusF1 > 0 && traj.nucleusF2 > 0) {
                                val baseCol = sylPalette[sIdx % sylPalette.size]
                                val drawCol = if (isDimmed) baseCol.copy(alpha = 0.20f) else baseCol
                                drawSyllableVowelElement(
                                    traj = traj,
                                    mapFn = ::mapActiveSpeaker,
                                    color = drawCol,
                                    dashed = false
                                )

                                if (trajectories.size > 1 && !isDimmed) {
                                    val labelAnchor = if (traj.vowelType == VowelGlideType.MONOPHTHONG) {
                                        mapActiveSpeaker(traj.nucleusF1, traj.nucleusF2)
                                    } else {
                                        mapActiveSpeaker(traj.onsetF1, traj.onsetF2)
                                    }
                                    val badgeStr = "${circledSyllableBadge(sIdx + 1)}${traj.syllableLabel.take(2)}"
                                    val measured = vowelTextMeasurer.measure(
                                        text = badgeStr,
                                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    )
                                    val lx = (labelAnchor.x + 6.dp.toPx()).coerceIn(4f, (size.width - measured.size.width - 4f).coerceAtLeast(4f))
                                    val ly = (labelAnchor.y - measured.size.height - 4.dp.toPx()).coerceIn(4f, (size.height - measured.size.height - 4f).coerceAtLeast(4f))
                                    drawText(
                                        textLayoutResult = measured,
                                        color = baseCol,
                                        topLeft = Offset(lx, ly)
                                    )
                                }
                            }
                        }
                    } else {
                        val ptsList = ArrayList<Offset>()
                        for (s in 0 until analysis.segmentCount) {
                            val f1 = analysis.speakerF1.getOrNull(s) ?: 0
                            val f2 = analysis.speakerF2.getOrNull(s) ?: 0
                            if (f1 > 0 && f2 > 0) {
                                val pt = mapActiveSpeaker(f1, f2)
                                if (ptsList.isNotEmpty()) {
                                    drawInterSyllableDashedLink(ptsList.last(), pt, interSylLinkColor)
                                }
                                ptsList.add(pt)
                                val col = sylPalette[s % sylPalette.size]
                                drawCircle(color = col.copy(alpha = 0.22f), radius = 11.dp.toPx(), center = pt)
                                drawCircle(color = col, radius = 4.8.dp.toPx(), center = pt)
                            }
                        }
                    }
                }
            }

            // Cross-Speaker Vowel Overlay Color Legend & Interactive Visibility Toggles
            if (overlayOtherSpeakers && otherSpeakersEntries.isNotEmpty()) {
                val cmpCols = listOf(
                    Color(0xFF00897B),
                    Color(0xFF8E24AA),
                    Color(0xFF5D4037),
                    Color(0xFFC2185B),
                    Color(0xFFEF6C00)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFD93025), CircleShape))
                        Text(
                            text = "${speaker?.name ?: "当前发音人"} (实线)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    otherSpeakersEntries.forEachIndexed { idx, entry ->
                        val col = cmpCols[idx % cmpCols.size]
                        val isHidden = entry.speaker.id in effectiveHiddenIds
                        val t0 = entry.analysis.formantTrajectories.firstOrNull()
                        val detailTxt = if (t0 != null && t0.nucleusF1 > 0) {
                            " ${t0.vowelType.chineseLabel.substringBefore(' ')} ${t0.nucleusF1}/${t0.nucleusF2}Hz"
                        } else ""
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isHidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else col.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (isHidden) MaterialTheme.colorScheme.outlineVariant else col.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .clickable {
                                    if (onToggleOtherSpeakerVisibility != null) {
                                        onToggleOtherSpeakerVisibility(entry.speaker.id)
                                    } else {
                                        localHiddenIds = if (isHidden) localHiddenIds - entry.speaker.id else localHiddenIds + entry.speaker.id
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isHidden) MaterialTheme.colorScheme.outline else col, CircleShape)
                                )
                                Text(
                                    text = "${entry.speaker.name} (虚线)$detailTxt",
                                    fontSize = 11.sp,
                                    fontWeight = if (isHidden) FontWeight.Normal else FontWeight.SemiBold,
                                    color = if (isHidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Legend for Trajectory Symbols & Inter-Syllable Dashed Transitions
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trajectories.any { it.vowelType != VowelGlideType.MONOPHTHONG }) {
                    Text(S.acoustics.legendOnsetCircle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.acoustics.legendDiphthongArrow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                if (trajectories.size > 1) {
                    Text(S.acoustics.legendPolysyllableDashed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val sylPalette = listOf(Color(0xFFD93025), Color(0xFF1976D2), Color(0xFF2E7D32), Color(0xFFEF6C00), Color(0xFF8E24AA))
                    trajectories.forEachIndexed { idx, t ->
                        val col = sylPalette[idx % sylPalette.size]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(col, CircleShape))
                            Text("${circledSyllableBadge(idx + 1)} ${t.syllableLabel} (${t.vowelType.chineseLabel.substringBefore(' ')})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws a subtle dashed directed inter-syllable transition connector (╌╌╌▶) between two syllables'
 * vowel targets/nuclei so multi-syllable words show clear syllable sequence without being confused
 * with solid intra-syllabic diphthong glides.
 */
fun DrawScope.drawInterSyllableDashedLink(
    from: Offset,
    to: Offset,
    color: Color
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = hypot(dx, dy)
    if (dist < 10.dp.toPx()) return

    val ux = dx / dist
    val uy = dy / dist
    val trimStart = 6.dp.toPx()
    val trimEnd = 7.dp.toPx()
    if (dist <= trimStart + trimEnd + 4f) return

    val startPt = Offset(from.x + ux * trimStart, from.y + uy * trimStart)
    val endPt = Offset(to.x - ux * trimEnd, to.y - uy * trimEnd)

    drawLine(
        color = color,
        start = startPt,
        end = endPt,
        strokeWidth = 1.6.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f)
    )
    drawDirectedArrowHead(
        from = startPt,
        to = endPt,
        color = color,
        headLenPx = 6.8.dp.toPx(),
        headHalfWidthPx = 3.6.dp.toPx()
    )
}

@Composable
private fun FormantMetricBox(
    title: String,
    value: String,
    sub: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, lineHeight = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(sub, fontSize = 9.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Shared Canvas helper that draws either a steady-state glowing vowel dot (Monophthong)
 * or a smooth Catmull-Rom / Quadratic Bezier directed arrow curve (Diphthong / Triphthong).
 */
fun DrawScope.drawSyllableVowelElement(
    traj: SyllableFormantTrajectory,
    mapFn: (Int, Int) -> Offset,
    color: Color,
    dashed: Boolean = false
) {
    if (traj.vowelType == VowelGlideType.MONOPHTHONG) {
        val center = mapFn(traj.nucleusF1, traj.nucleusF2)
        drawCircle(
            color = color.copy(alpha = 0.20f),
            radius = 11.dp.toPx(),
            center = center,
            style = Fill
        )
        drawCircle(
            color = Color.White,
            radius = 5.6.dp.toPx(),
            center = center
        )
        drawCircle(
            color = color,
            radius = 4.4.dp.toPx(),
            center = center
        )
        return
    }

    val pStart = mapFn(traj.onsetF1, traj.onsetF2)
    val pMid = mapFn(traj.nucleusF1, traj.nucleusF2)
    val pEnd = mapFn(traj.targetF1, traj.targetF2)

    val rawPts = if (traj.anchors.size >= 4) {
        listOf(
            mapFn(traj.anchors[0].f1Hz, traj.anchors[0].f2Hz),
            mapFn(traj.anchors[2].f1Hz, traj.anchors[2].f2Hz),
            mapFn(traj.anchors[4].f1Hz, traj.anchors[4].f2Hz),
            mapFn(traj.anchors.last().f1Hz, traj.anchors.last().f2Hz)
        )
    } else {
        listOf(pStart, pMid, pEnd)
    }

    val curvePath = buildSmoothVowelCurvePath(rawPts)
    val strokeEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(9f, 6f), 0f) else null

    // Soft halo under trajectory
    if (!dashed) {
        drawPath(
            path = curvePath,
            color = color.copy(alpha = 0.18f),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    drawPath(
        path = curvePath,
        color = color,
        style = Stroke(
            width = 2.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = strokeEffect
        )
    )

    // Hollow onset circle ring
    drawCircle(
        color = Color.White,
        radius = 4.2.dp.toPx(),
        center = rawPts.first()
    )
    drawCircle(
        color = color,
        radius = 4.2.dp.toPx(),
        center = rawPts.first(),
        style = Stroke(width = 2.dp.toPx())
    )

    // Inflection / medial dot for triphthongs
    if (traj.vowelType == VowelGlideType.TRIPHTHONG) {
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = pMid
        )
    }

    // Directional filled arrowhead at target
    val prevPt = rawPts[rawPts.size - 2]
    val endPt = rawPts.last()
    drawDirectedArrowHead(
        from = prevPt,
        to = endPt,
        color = color,
        headLenPx = 9.5.dp.toPx(),
        headHalfWidthPx = 5.2.dp.toPx()
    )
}

fun buildSmoothVowelCurvePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

fun DrawScope.drawDirectedArrowHead(
    from: Offset,
    to: Offset,
    color: Color,
    headLenPx: Float,
    headHalfWidthPx: Float
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = hypot(dx, dy)
    if (len < 1f) {
        drawCircle(color = color, radius = headHalfWidthPx, center = to)
        return
    }
    val ux = dx / len
    val uy = dy / len
    val px = -uy
    val py = ux

    val baseX = to.x - ux * headLenPx
    val baseY = to.y - uy * headLenPx

    val arrow = Path().apply {
        moveTo(to.x, to.y)
        lineTo(baseX + px * headHalfWidthPx, baseY + py * headHalfWidthPx)
        lineTo(baseX - px * headHalfWidthPx, baseY - py * headHalfWidthPx)
        close()
    }
    drawPath(path = arrow, color = color, style = Fill)
}
