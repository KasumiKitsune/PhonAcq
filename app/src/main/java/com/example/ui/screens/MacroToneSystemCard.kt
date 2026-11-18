package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SpeakerCorpusPhonologyReport
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.ui.components.StudioFilterChip
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EnhancedMacroToneSystemCard(
    report: SpeakerCorpusPhonologyReport,
    clusterSourceMode: Int,
    onClusterSourceModeChange: (Int) -> Unit,
    playingWordId: Long?,
    onPlayWord: (Word) -> Unit,
    isDesktopExpanded: Boolean = false
) {
    var showStdRibbon by remember { mutableStateOf(true) }
    var onlyShowOutliers by remember { mutableStateOf(false) }
    var expandedClusterId by remember { mutableStateOf<String?>(null) }
    var highlightedClusterId by remember { mutableStateOf<String?>(null) }

    val activeClusters = if (clusterSourceMode == 1 && report.hasCustomWordGroups && report.mergedToneClusters.isNotEmpty()) {
        report.mergedToneClusters
    } else {
        report.pureAcousticToneClusters
    }

    val totalOutliers = remember(activeClusters) { activeClusters.sumOf { it.outlierCount } }
    val checkedCatCount = remember(activeClusters) { activeClusters.count { it.isCheckedCategory } }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("macro_tone_system_card")
    ) {
        Column(modifier = Modifier.padding(if (isDesktopExpanded) 20.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = S.acoustics.toneClusteringTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val pitchSpanText = report.calibratedPitchRangeHz?.let {
                        S.acoustics.personalRobustRangeFormat.withArgs(
                            "min" to String.format(Locale.US, "%.0f", it.first),
                            "max" to String.format(Locale.US, "%.0f", it.second)
                        )
                    } ?: S.acoustics.defaultRangeEstimateTag
                    Text(
                        text = buildString {
                            append(S.acoustics.clusterCountSummary.withArgs("count" to activeClusters.size))
                            if (checkedCatCount > 0) append(S.acoustics.checkedClusterAppend.withArgs("count" to checkedCatCount))
                            append(" · $pitchSpanText")
                        },
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (report.hasCustomWordGroups && report.mergedToneClusters.isNotEmpty()) {
                    MacroSegmentedTrack(modifier = Modifier.align(Alignment.CenterVertically)) {
                        MacroSegmentedItem(
                            selected = clusterSourceMode == 0,
                            onClick = {
                                onClusterSourceModeChange(0)
                                highlightedClusterId = null
                            },
                            label = S.acoustics.chipPureAcousticClusters.withArgs("count" to report.pureAcousticToneClusters.size),
                            compact = true,
                            modifier = Modifier.testTag("tone_mode_acoustic_cluster")
                        )
                        MacroSegmentedItem(
                            selected = clusterSourceMode == 1,
                            onClick = {
                                onClusterSourceModeChange(1)
                                highlightedClusterId = null
                            },
                            label = S.acoustics.chipWordlistGroups.withArgs("count" to report.mergedToneClusters.size),
                            compact = true,
                            modifier = Modifier.testTag("tone_mode_word_group")
                        )
                    }
                } else {
                    StudioFilterChip(
                        selected = true,
                        onClick = { highlightedClusterId = null },
                        labelText = S.acoustics.chipPureAcousticClusters.withArgs("count" to report.pureAcousticToneClusters.size),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .testTag("tone_mode_acoustic_cluster")
                    )
                }
                StudioFilterChip(
                    selected = showStdRibbon,
                    onClick = { showStdRibbon = !showStdRibbon },
                    labelText = S.acoustics.chipSigmaBand,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                if (totalOutliers > 0) {
                    StudioFilterChip(
                        selected = onlyShowOutliers,
                        onClick = { onlyShowOutliers = !onlyShowOutliers },
                        labelText = S.acoustics.chipOutlierHint.withArgs("count" to totalOutliers),
                        isErrorVariant = true,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isDesktopExpanded) 224.dp else 168.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(S.archive.scale5High, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale4HalfHigh, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale3Mid, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale2HalfLow, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(S.archive.scale1Low, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val sectionsY = h / 4f
                        for (i in 0..4) {
                            val cy = i * sectionsY
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, cy),
                                end = Offset(w, cy),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f)
                            )
                        }

                        fun normY(p: Float): Float = ((1f - p) * h * 0.86f + h * 0.07f).coerceIn(2f, h - 2f)

                        activeClusters.forEachIndexed { idx, cat ->
                            val baseCol = PHONOLOGY_PALETTE[idx % PHONOLOGY_PALETTE.size]
                            val isDimmed = highlightedClusterId != null && highlightedClusterId != cat.clusterId
                            val strokeAlpha = if (isDimmed) 0.16f else 1.0f
                            val ribbonAlpha = if (isDimmed) 0.03f else 0.14f

                            val pts = cat.meanContour
                            if (pts.size >= 2) {
                                // Checked / Entering tones occupy proportional short horizontal span (e.g. 45% width)
                                val spanW = w * cat.relativeDurationRatio.coerceIn(0.36f, 1.0f)

                                if (showStdRibbon && cat.upperBand1Sigma.size == pts.size && cat.lowerBand1Sigma.size == pts.size && cat.sampleCount >= 2) {
                                    val ribPath = Path()
                                    for (i in pts.indices) {
                                        val px = (i.toFloat() / (pts.size - 1)) * spanW
                                        val py = normY(cat.upperBand1Sigma[i])
                                        if (i == 0) ribPath.moveTo(px, py) else ribPath.lineTo(px, py)
                                    }
                                    for (i in pts.indices.reversed()) {
                                        val px = (i.toFloat() / (pts.size - 1)) * spanW
                                        val py = normY(cat.lowerBand1Sigma[i])
                                        ribPath.lineTo(px, py)
                                    }
                                    ribPath.close()
                                    drawPath(path = ribPath, color = baseCol.copy(alpha = ribbonAlpha), style = Fill)
                                }

                                val linePts = pts.mapIndexed { i, p ->
                                    Offset((i.toFloat() / (pts.size - 1)) * spanW, normY(p))
                                }
                                val curvePath = buildSmoothVowelCurvePath(linePts)
                                drawPath(
                                    path = curvePath,
                                    color = baseCol.copy(alpha = strokeAlpha),
                                    style = Stroke(
                                        width = if (highlightedClusterId == cat.clusterId) 3.8.dp.toPx() else 2.9.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )

                                // Draw end marker: glottal vertical stop bar for Checked Tones vs round nodes for Smooth Tones
                                if (cat.isCheckedCategory) {
                                    val endPt = linePts.last()
                                    drawLine(
                                        color = baseCol.copy(alpha = strokeAlpha),
                                        start = Offset(endPt.x, endPt.y - 6.dp.toPx()),
                                        end = Offset(endPt.x, endPt.y + 6.dp.toPx()),
                                        strokeWidth = 2.6.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawCircle(
                                        color = baseCol.copy(alpha = strokeAlpha),
                                        radius = 3.8.dp.toPx(),
                                        center = linePts.first()
                                    )
                                } else {
                                    linePts.forEach { pt ->
                                        drawCircle(
                                            color = baseCol.copy(alpha = strokeAlpha),
                                            radius = 3.dp.toPx(),
                                            center = pt
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = S.acoustics.toneClusterInteractiveHint,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(10.dp))

            // Expandable Tone Cluster Cards
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                activeClusters.forEachIndexed { idx, cat ->
                    val visibleMembers = if (onlyShowOutliers) {
                        cat.members.filter { it.isOutlier }
                    } else {
                        cat.members
                    }
                    if (onlyShowOutliers && visibleMembers.isEmpty()) return@forEachIndexed

                    val col = PHONOLOGY_PALETTE[idx % PHONOLOGY_PALETTE.size]
                    val isExpanded = expandedClusterId == cat.clusterId || onlyShowOutliers
                    val isHighlighted = highlightedClusterId == cat.clusterId

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isHighlighted) col.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isHighlighted) col.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                expandedClusterId = if (expandedClusterId == cat.clusterId) null else cat.clusterId
                                highlightedClusterId = if (highlightedClusterId == cat.clusterId) null else cat.clusterId
                            }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.size(11.dp).background(col, CircleShape))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = S.acoustics.clusterCardTitleFormat.withArgs(
                                                    "name" to cat.categoryName,
                                                    "chao" to cat.canonicalChao
                                                ),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (cat.isCheckedCategory) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                                ) {
                                                    Text(
                                                        text = S.acoustics.checkedTagBadge,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            if (cat.outlierCount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer
                                                ) {
                                                    Text(
                                                        text = S.acoustics.outlierCountBadge.withArgs("count" to cat.outlierCount),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = S.acoustics.representativeCharsLine.withArgs(
                                                "words" to cat.exampleWords.joinToString("、"),
                                                "count" to cat.sampleCount
                                            ),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.0fHz · %dms", cat.meanF0Hz, cat.meanDurationMs),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        visibleMembers.forEach { m ->
                                            val isPlaying = playingWordId == m.word.id
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = when {
                                                    isPlaying -> MaterialTheme.colorScheme.primaryContainer
                                                    m.isOutlier -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (m.isOutlier) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onPlayWord(m.word) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = S.acoustics.listenWordDesc.withArgs("word" to m.word.text),
                                                        tint = if (m.isOutlier) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = m.word.text,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = if (m.rawChao == m.canonicalChao) {
                                                            "[${m.canonicalChao}]"
                                                        } else {
                                                            S.acoustics.canonicalVsRawLine.withArgs(
                                                                "canonical" to m.canonicalChao,
                                                                "raw" to m.rawChao
                                                            )
                                                        },
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (m.isOutlier) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
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

            // Stratified Polysyllabic Tone Sandhi / Connected Pattern Summary (prevents polluting monosyllabic citation tone clusters)
            if (report.polysyllabicTonePatterns.isNotEmpty() && !onlyShowOutliers) {
                var expandedPolyKey by remember { mutableStateOf<String?>(null) }
                val totalPolyWords = remember(report.polysyllabicTonePatterns) {
                    report.polysyllabicTonePatterns.sumOf { it.sampleCount }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = S.acoustics.polysyllablePatternHeader.withArgs(
                                "words" to totalPolyWords,
                                "patterns" to report.polysyllabicTonePatterns.size
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = S.acoustics.polysyllablePatternExplanation,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    report.polysyllabicTonePatterns.forEachIndexed { pIdx, pat ->
                        val pCol = PHONOLOGY_PALETTE[(pIdx + 2) % PHONOLOGY_PALETTE.size]
                        val isExp = expandedPolyKey == pat.patternKey
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExp) pCol.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isExp) pCol.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { expandedPolyKey = if (isExp) null else pat.patternKey }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Mini multi-syllable pitch contour preview
                                        Box(
                                            modifier = Modifier
                                                .width(56.dp)
                                                .height(32.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                        ) {
                                            val dividerCol = MaterialTheme.colorScheme.outlineVariant
                                            val miniBg = MaterialTheme.colorScheme.surfaceContainerLow
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val sylCnt = pat.meanSyllableContours.size.coerceAtLeast(1)
                                                val colW = size.width / sylCnt.toFloat()
                                                pat.meanSyllableContours.forEachIndexed { sIdx, pts ->
                                                    val sx = sIdx * colW
                                                    if (sIdx > 0) {
                                                        drawLine(
                                                            color = dividerCol,
                                                            start = Offset(sx, 0f),
                                                            end = Offset(sx, size.height),
                                                            strokeWidth = 1.dp.toPx(),
                                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
                                                        )
                                                    }
                                                    val isNeut = pat.syllableNeutralFlags.getOrElse(sIdx) { false }
                                                    val isChk = pat.syllableCheckedFlags.getOrElse(sIdx) { false }
                                                    val padX = colW * 0.10f
                                                    val spanW = (colW - padX * 2f) * when {
                                                        isNeut -> 0.48f
                                                        isChk -> 0.60f
                                                        else -> 1f
                                                    }
                                                    if (pts.size >= 2) {
                                                        if (isNeut) {
                                                            val midP = pts[pts.size / 2]
                                                            val cx = sx + padX + spanW * 0.5f
                                                            val cy = ((1f - midP) * size.height * 0.82f + size.height * 0.09f)
                                                            drawCircle(color = miniBg, radius = 3.dp.toPx(), center = Offset(cx, cy))
                                                            drawCircle(
                                                                color = pCol,
                                                                radius = 3.dp.toPx(),
                                                                center = Offset(cx, cy),
                                                                style = Stroke(width = 1.6.dp.toPx())
                                                            )
                                                        } else {
                                                            val path = Path()
                                                            var lastPt = Offset(sx + padX, size.height * 0.5f)
                                                            pts.forEachIndexed { i, v ->
                                                                val px = sx + padX + (i.toFloat() / (pts.size - 1)) * spanW
                                                                val py = ((1f - v) * size.height * 0.82f + size.height * 0.09f)
                                                                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                                                lastPt = Offset(px, py)
                                                            }
                                                            drawPath(
                                                                path = path,
                                                                color = pCol,
                                                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                                            )
                                                            if (isChk) {
                                                                drawLine(
                                                                    color = pCol,
                                                                    start = Offset(lastPt.x, (lastPt.y - 4.dp.toPx()).coerceAtLeast(1f)),
                                                                    end = Offset(lastPt.x, (lastPt.y + 4.dp.toPx()).coerceAtMost(size.height - 1f)),
                                                                    strokeWidth = 1.8.dp.toPx()
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = S.acoustics.sandhiPatternBadge.withArgs("chao" to pat.patternChao),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = pCol.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = pat.patternLabel,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = pCol,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = S.acoustics.exampleWordsLine.withArgs(
                                                    "words" to pat.exampleWords.joinToString("、"),
                                                    "count" to pat.sampleCount
                                                ),
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isExp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                AnimatedVisibility(visible = isExp) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            pat.members.forEach { m ->
                                                val isPlaying = playingWordId == m.word.id
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onPlayWord(m.word) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                            contentDescription = S.acoustics.listenWordDesc.withArgs("word" to m.word.text),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = m.word.text,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "[${m.patternChao}]",
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}
