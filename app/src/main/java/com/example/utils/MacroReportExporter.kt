package com.example.utils

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.audio.CanonicalToneCluster
import com.example.audio.MultiSpeakerAlignedAnalysis
import com.example.audio.MultiSpeakerComparisonEngine
import com.example.audio.MultiSpeakerVowelComparisonReport
import com.example.audio.NormalizedVowelToken
import com.example.audio.PhoneticScaleUtils
import com.example.audio.SpeakerCorpusPhonologyReport
import com.example.audio.VowelConfidenceEllipse
import com.example.audio.VowelGlideType
import kotlin.math.roundToInt
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Exports the Macro Acoustic & Phonology Report (`ProjectAcousticSummarySheet`) into a publication-ready `.zip`:
 *  1. `声学与音系归纳报告.xlsx` — Multi-sheet Excel workbook (声调音系聚类, 单字归类明细, 多音节连读调式, 元音共振峰与动程, 元音1σ椭圆, 多发音人Lobanov对比)
 *  2. `声调调域聚类图.png` — High-resolution (1600×1040) five-degree tone contour & ±1σ ribbon chart
 *  3. `元音格局动程图.png` — High-resolution (1600×1120) Bark F1-F2 acoustic vowel space with 1σ ellipses & diphthong trajectories
 *  4. `多发音人Lobanov对比图.png` — Included automatically when `multiSpeakerReport` has >= 2 speakers
 */
object MacroReportExporter {

    private val EXPORT_PALETTE = listOf(
        Color(0xFF1565C0), // Indigo Blue
        Color(0xFFD84315), // Deep Coral
        Color(0xFF2E7D32), // Emerald Green
        Color(0xFF8E24AA), // Royal Violet
        Color(0xFFEF6C00), // Amber Orange
        Color(0xFF00838F), // Cyan Teal
        Color(0xFFC2185B), // Crimson Rose
        Color(0xFF4E342E)  // Warm Umber
    )

    fun exportReportZip(
        outputStream: OutputStream,
        projectName: String,
        report: SpeakerCorpusPhonologyReport,
        useMergedToneClusters: Boolean = true,
        multiSpeakerReport: MultiSpeakerVowelComparisonReport? = null,
        textMeasurer: TextMeasurer? = null
    ): Boolean {
        return try {
            val clusters = if (useMergedToneClusters && report.mergedToneClusters.isNotEmpty()) {
                report.mergedToneClusters
            } else {
                report.pureAcousticToneClusters
            }

            val alignedMulti = if (multiSpeakerReport != null && multiSpeakerReport.speakerReports.size >= 2) {
                MultiSpeakerComparisonEngine.buildAlignedAnalysis(
                    multiReport = multiSpeakerReport,
                    selectedSpeakerIds = emptySet()
                )
            } else {
                null
            }

            val xlsxBytes = buildWorkbookBytes(
                projectName = projectName,
                report = report,
                clusters = clusters,
                multiSpeakerReport = multiSpeakerReport,
                alignedMulti = alignedMulti
            )

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                zos.putNextEntry(ZipEntry("声学与音系归纳报告.xlsx"))
                zos.write(xlsxBytes)
                zos.closeEntry()

                if (textMeasurer != null) {
                    if (clusters.isNotEmpty()) {
                        val tonePng = renderToneSystemPng(
                            projectName = projectName,
                            speakerName = report.speakerName,
                            clusters = clusters,
                            calibratedRange = report.calibratedPitchRangeHz,
                            textMeasurer = textMeasurer
                        )
                        if (tonePng.isNotEmpty()) {
                            zos.putNextEntry(ZipEntry("声调调域聚类图.png"))
                            zos.write(tonePng)
                            zos.closeEntry()
                        }
                    }

                    if (report.vowelTokens.isNotEmpty()) {
                        val vowelPng = renderVowelSpacePng(
                            projectName = projectName,
                            speakerName = report.speakerName,
                            tokens = report.vowelTokens,
                            ellipses = report.vowelEllipses,
                            textMeasurer = textMeasurer
                        )
                        if (vowelPng.isNotEmpty()) {
                            zos.putNextEntry(ZipEntry("元音格局动程图.png"))
                            zos.write(vowelPng)
                            zos.closeEntry()
                        }
                    }

                    if (multiSpeakerReport != null && multiSpeakerReport.speakerReports.size >= 2 && alignedMulti != null) {
                        if (alignedMulti.alignedToneCategories.isNotEmpty()) {
                            val multiTonePng = renderMultiSpeakerToneComparisonPng(
                                projectName = projectName,
                                aligned = alignedMulti,
                                textMeasurer = textMeasurer
                            )
                            if (multiTonePng.isNotEmpty()) {
                                zos.putNextEntry(ZipEntry("多发音人声调格局对比图.png"))
                                zos.write(multiTonePng)
                                zos.closeEntry()
                            }
                        }

                        val lobanovPng = renderLobanovMultiSpeakerPng(
                            projectName = projectName,
                            multiReport = multiSpeakerReport,
                            aligned = alignedMulti,
                            textMeasurer = textMeasurer
                        )
                        if (lobanovPng.isNotEmpty()) {
                            zos.putNextEntry(ZipEntry("多发音人Lobanov对比图.png"))
                            zos.write(lobanovPng)
                            zos.closeEntry()
                        }
                    }
                }
                zos.finish()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun buildWorkbookBytes(
        projectName: String,
        report: SpeakerCorpusPhonologyReport,
        clusters: List<CanonicalToneCluster>,
        multiSpeakerReport: MultiSpeakerVowelComparisonReport? = null,
        alignedMulti: MultiSpeakerAlignedAnalysis? = if (multiSpeakerReport != null && multiSpeakerReport.speakerReports.size >= 2) {
            MultiSpeakerComparisonEngine.buildAlignedAnalysis(multiSpeakerReport, emptySet())
        } else null
    ): ByteArray {
        val sheets = ArrayList<XlsxSpreadsheetWriter.SheetSpec>()

        // Sheet 1: 声调音系聚类
        val toneClusterRows = clusters.mapIndexed { idx, c ->
            listOf<Any?>(
                idx + 1,
                c.categoryName,
                c.canonicalChao,
                c.toneShapeLabel,
                if (c.isCheckedCategory) "是 (短促)" else "否 (舒声)",
                c.sampleCount,
                c.outlierCount,
                c.meanF0Hz,
                c.meanDurationMs,
                c.meanContour.joinToString(" -> ") { String.format(Locale.US, "%.2f", it) },
                c.exampleWords.joinToString("、")
            )
        }
        sheets.add(
            XlsxSpreadsheetWriter.SheetSpec(
                name = "声调音系聚类",
                headers = listOf(
                    "序号", "归纳调类", "五度代表值", "调型走势", "入声/促声",
                    "样本字数", "2σ偏离字数", "均值F0(Hz)", "平均韵母时长(ms)", "五点T值轨迹(0-100%)", "代表例字"
                ),
                rows = toneClusterRows
            )
        )

        // Sheet 2: 单字归类与实测明细
        val memberRows = clusters.flatMap { c ->
            c.members.map { m ->
                listOf<Any?>(
                    m.word.originalIndex + 1,
                    m.word.text,
                    m.word.groupName,
                    m.word.remark,
                    m.word.speakerNote,
                    m.canonicalCategoryName,
                    m.canonicalChao,
                    m.rawChao,
                    m.meanF0Hz,
                    m.voicedDurationMs,
                    if (m.isCheckedTone) "是" else "否",
                    m.deviationSigma,
                    if (m.isOutlier) "偏离(>2σ)" else "正常"
                )
            }
        }.sortedBy { (it[0] as? Int) ?: 0 }

        sheets.add(
            XlsxSpreadsheetWriter.SheetSpec(
                name = "单字归类明细",
                headers = listOf(
                    "词条序号", "字词", "预设分组", "预设备注", "田野速记",
                    "归属调类", "归属五度", "单字实测五度", "均值F0(Hz)", "带音时长(ms)",
                    "短促调标记", "类内偏离度(σ)", "离群判定"
                ),
                rows = memberRows
            )
        )

        // Sheet 3: 多音节连读调式 (when present)
        if (report.polysyllabicTonePatterns.isNotEmpty()) {
            val polyRows = report.polysyllabicTonePatterns.mapIndexed { idx, p ->
                listOf<Any?>(
                    idx + 1,
                    p.patternLabel,
                    p.patternChao,
                    p.syllableCount,
                    p.sampleCount,
                    p.syllableNeutralFlags.mapIndexedNotNull { sIdx, isN -> if (isN) "第${sIdx + 1}字轻声" else null }.joinToString(";").ifBlank { "无" },
                    p.exampleWords.joinToString("、")
                )
            }
            sheets.add(
                XlsxSpreadsheetWriter.SheetSpec(
                    name = "多音节连读调式",
                    headers = listOf("序号", "连读调式名称", "音节五度组合", "音节数", "词条样本数", "轻声特征", "代表例词"),
                    rows = polyRows
                )
            )
        }

        // Sheet 4: 元音共振峰与动程
        val vowelRows = report.vowelTokens.map { t ->
            listOf<Any?>(
                t.word.originalIndex + 1,
                t.word.text,
                if (t.wordSyllableCount > 1) "${t.syllableIndex + 1}/${t.wordSyllableCount} (${t.syllableLabel})" else t.displayLabel,
                t.groupLabel,
                t.vowelType.chineseLabel,
                t.nucleusF1Hz,
                t.nucleusF2Hz,
                t.nucleusF3Hz,
                t.onsetF1Hz,
                t.onsetF2Hz,
                t.targetF1Hz,
                t.targetF2Hz,
                t.barkGlideSpan,
                t.z1Nucleus,
                t.z2Nucleus,
                t.trajectorySummary
            )
        }
        sheets.add(
            XlsxSpreadsheetWriter.SheetSpec(
                name = "元音共振峰与动程",
                headers = listOf(
                    "词条序号", "字词", "音节定位", "音类/分组", "元音类型",
                    "稳态核F1(Hz)", "稳态核F2(Hz)", "稳态核F3(Hz)",
                    "起点20%F1(Hz)", "起点20%F2(Hz)", "终点80%F1(Hz)", "终点80%F2(Hz)",
                    "Bark动程跨度", "Lobanov_Z1", "Lobanov_Z2", "舌位轨迹描述"
                ),
                rows = vowelRows
            )
        )

        // Sheet 5: 元音1σ置信椭圆统计
        if (report.vowelEllipses.isNotEmpty()) {
            val ellipseRows = report.vowelEllipses.mapIndexed { idx, e ->
                listOf<Any?>(
                    idx + 1,
                    e.groupLabel,
                    e.sampleCount,
                    e.representativeWord,
                    e.centerF1Hz,
                    e.centerF2Hz,
                    e.centerBarkF1,
                    e.centerBarkF2,
                    e.semiMajorBark,
                    e.semiMinorBark,
                    e.centerLobanovZ1,
                    e.centerLobanovZ2
                )
            }
            sheets.add(
                XlsxSpreadsheetWriter.SheetSpec(
                    name = "元音1σ置信椭圆",
                    headers = listOf(
                        "序号", "音类分组", "样本数", "中心代表字",
                        "均值F1(Hz)", "均值F2(Hz)", "中心F1(Bark)", "中心F2(Bark)",
                        "长半轴(Bark)", "短半轴(Bark)", "中心Lobanov_Z1", "中心Lobanov_Z2"
                    ),
                    rows = ellipseRows
                )
            )
        }

        // Sheet 6: 同词跨人声学对照表 (Word × Speaker Matrix) & Sheet 7: 多发音人调类与元音汇总 & Sheet 8: 校准明细
        if (multiSpeakerReport != null && alignedMulti != null && multiSpeakerReport.speakerReports.size >= 2) {
            val spkReports = multiSpeakerReport.speakerReports
            if (alignedMulti.wordComparisons.isNotEmpty()) {
                val matrixHeaders = ArrayList<String>()
                matrixHeaders.addAll(
                    listOf("词条序号", "字词", "所属分组", "跨人异读诊断", "归一化舌位最大跨距(ΔZ)", "声调五度最大偏差")
                )
                for (sr in spkReports) {
                    matrixHeaders.add("${sr.speakerName}_五度调")
                    matrixHeaders.add("${sr.speakerName}_韵母判定")
                    matrixHeaders.add("${sr.speakerName}_核F1/F2(Hz)与Z值")
                }

                val matrixRows = alignedMulti.wordComparisons
                    .sortedBy { it.originalIndex }
                    .map { wc ->
                        val row = ArrayList<Any?>()
                        row.add(wc.originalIndex + 1)
                        row.add(wc.wordText)
                        row.add(wc.groupName)
                        row.add(wc.divergenceBadge)
                        row.add(wc.vowelMaxShiftZ)
                        row.add(wc.toneMaxRmse5Deg)
                        val realMap = wc.realizations.associateBy { it.speakerId }
                        for (sr in spkReports) {
                            val r = realMap[sr.speakerId]
                            if (r == null) {
                                row.add("未录")
                                row.add("-")
                                row.add("-")
                            } else {
                                row.add(r.chaoTone)
                                val tok = r.primaryToken
                                if (tok != null) {
                                    row.add("${tok.vowelType.chineseLabel.substringBefore(' ')} (${String.format(Locale.US, "%.1f", tok.barkGlideSpan)}B)")
                                    row.add(
                                        String.format(
                                            Locale.US,
                                            "%d/%d Hz (Z1=%+.2f, Z2=%+.2f)",
                                            tok.nucleusF1Hz,
                                            tok.nucleusF2Hz,
                                            tok.z1Nucleus,
                                            tok.z2Nucleus
                                        )
                                    )
                                } else {
                                    row.add("-")
                                    row.add("-")
                                }
                            }
                        }
                        row
                    }

                sheets.add(
                    XlsxSpreadsheetWriter.SheetSpec(
                        name = "同词跨人声学对照表",
                        headers = matrixHeaders,
                        rows = matrixRows
                    )
                )
            }

            // Sheet 7: 多发音人调类与元音汇总
            val summaryRows = ArrayList<List<Any?>>()
            for (tCat in alignedMulti.alignedToneCategories) {
                for (real in tCat.cohortRealizations) {
                    summaryRows.add(
                        listOf(
                            "声调调类",
                            tCat.categoryLabel,
                            real.cohortLabel,
                            real.sampleCount,
                            "[${real.representativeChao}] ${real.toneShapeLabel}",
                            String.format(Locale.US, "%.0f Hz", real.meanF0Hz),
                            if (tCat.hasCategoryDivergence) "跨人存在调型分化 (Δ=${String.format(Locale.US, "%.2f", tCat.maxPairwiseDiff5Deg)})" else "跨人一致",
                            real.exampleWords.joinToString(" ")
                        )
                    )
                }
            }
            for (vCat in alignedMulti.alignedVowelCategories) {
                val shiftDesc = vCat.shiftVectors.joinToString("; ") {
                    "${it.fromCohortLabel}→${it.toCohortLabel}:${it.directionSummary}"
                }.ifEmpty { "质心稳定" }
                for (st in vCat.cohortStats) {
                    summaryRows.add(
                        listOf(
                            "元音/韵母类",
                            vCat.groupLabel,
                            st.cohortLabel,
                            st.sampleCount,
                            st.dominantVowelType.chineseLabel.substringBefore(' '),
                            String.format(Locale.US, "%.0f / %.0f Hz (Z1=%+.2f, Z2=%+.2f)", st.meanF1Hz, st.meanF2Hz, st.centerZ1, st.centerZ2),
                            shiftDesc,
                            String.format(Locale.US, "平均动程 %.2f Bark", st.meanBarkGlide)
                        )
                    )
                }
            }
            if (summaryRows.isNotEmpty()) {
                sheets.add(
                    XlsxSpreadsheetWriter.SheetSpec(
                        name = "多发音人调类与元音汇总",
                        headers = listOf("维度", "音类/调类名称", "发音人/分组", "样本数", "代表调值/韵母态", "均值频率与归一化中心", "跨人同异/位移矢量", "例字与备注"),
                        rows = summaryRows
                    )
                )
            }

            // Sheet 8: 多发音人Lobanov对比 (calibrated tokens)
            val calibratedList = alignedMulti.calibratedTokens.ifEmpty { multiSpeakerReport.allNormalizedTokens }
            if (calibratedList.isNotEmpty()) {
                val multiRows = calibratedList.map { t ->
                    val spk = multiSpeakerReport.speakersById[t.speakerId]
                    listOf<Any?>(
                        t.speakerName,
                        spk?.gender ?: "",
                        spk?.age ?: 0,
                        spk?.dialect ?: "",
                        t.word.originalIndex + 1,
                        t.word.text,
                        t.groupLabel,
                        t.vowelType.chineseLabel,
                        t.nucleusF1Hz,
                        t.nucleusF2Hz,
                        t.z1Nucleus,
                        t.z2Nucleus,
                        PhoneticScaleUtils.lobanovZ1ToEquivHz(t.z1Nucleus),
                        PhoneticScaleUtils.lobanovZ2ToEquivHz(t.z2Nucleus),
                        t.barkGlideSpan
                    )
                }
                sheets.add(
                    XlsxSpreadsheetWriter.SheetSpec(
                        name = "多发音人Lobanov对比",
                        headers = listOf(
                            "发音人", "性别", "年龄", "方言点", "词条序号", "字词", "音类分组", "元音类型",
                            "原始核F1(Hz)", "原始核F2(Hz)", "Lobanov_Z1", "Lobanov_Z2",
                            "等效声道F1(Hz)", "等效声道F2(Hz)", "Bark动程跨度"
                        ),
                        rows = multiRows
                    )
                )
            }
        }

        return XlsxSpreadsheetWriter.toByteArray(sheets)
    }

    private fun renderDrawScopeToPngBytes(
        widthPx: Int,
        heightPx: Int,
        drawBlock: DrawScope.() -> Unit
    ): ByteArray {
        val imageBitmap = ImageBitmap(widthPx, heightPx)
        val canvas = Canvas(imageBitmap)
        val drawScope = CanvasDrawScope()
        drawScope.draw(
            density = Density(2f, 1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(widthPx.toFloat(), heightPx.toFloat()),
            block = drawBlock
        )
        val pixels = IntArray(widthPx * heightPx)
        imageBitmap.readPixels(pixels)
        val bmp = Bitmap.createBitmap(pixels, widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        try {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        } finally {
            bmp.recycle()
        }
        return baos.toByteArray()
    }

    private fun renderToneSystemPng(
        projectName: String,
        speakerName: String,
        clusters: List<CanonicalToneCluster>,
        calibratedRange: Pair<Float, Float>?,
        textMeasurer: TextMeasurer
    ): ByteArray {
        val widthPx = 1600
        val heightPx = 1040
        return renderDrawScopeToPngBytes(widthPx, heightPx) {
            drawRect(Color(0xFFFAFBFD))

            // Title & subtitle
            drawText(
                textMeasurer = textMeasurer,
                text = "$projectName · 声调五度调域聚类图 ($speakerName)",
                topLeft = Offset(64f, 36f),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            val rangeNote = calibratedRange?.let {
                String.format(Locale.US, "个人F0音域: %.0f–%.0f Hz  |  阴影带为类内 ±1σ 标准差包络，竖线标记入声/促声短调", it.first, it.second)
            } ?: "阴影带为类内 ±1σ 标准差包络，竖线标记入声/促声短调"
            drawText(
                textMeasurer = textMeasurer,
                text = rangeNote,
                topLeft = Offset(64f, 74f),
                style = TextStyle(color = Color(0xFF64748B), fontSize = 10.sp)
            )

            val plotLeft = 130f
            val plotTop = 132f
            val plotRight = size.width - 410f
            val plotBottom = size.height - 96f
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 2f)
            )

            val levelLabels = listOf("1 (低)", "2 (半低)", "3 (中)", "4 (半高)", "5 (高)")
            for (lvl in 1..5) {
                val ratio = (lvl - 1f) / 4f
                val y = plotBottom - ratio * plotH
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = if (lvl == 1 || lvl == 5) 2f else 1.4f,
                    pathEffect = if (lvl in 2..4) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
                )
                val ml = textMeasurer.measure(
                    levelLabels[lvl - 1],
                    TextStyle(color = Color(0xFF475569), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                )
                drawText(ml, topLeft = Offset(plotLeft - ml.size.width - 16f, y - ml.size.height / 2f))
            }

            for (pct in listOf(0, 25, 50, 75, 100)) {
                val x = plotLeft + (pct / 100f) * plotW
                if (pct in 1..99) {
                    drawLine(
                        color = Color(0xFFF1F5F9),
                        start = Offset(x, plotTop),
                        end = Offset(x, plotBottom),
                        strokeWidth = 1.2f
                    )
                }
                val tl = textMeasurer.measure(
                    "$pct%",
                    TextStyle(color = Color(0xFF64748B), fontSize = 9.sp)
                )
                drawText(tl, topLeft = Offset(x - tl.size.width / 2f, plotBottom + 14f))
            }

            fun tToY(t: Float): Float {
                val clamped = t.coerceIn(0.8f, 5.2f)
                return plotBottom - ((clamped - 1f) / 4f) * plotH
            }

            clusters.forEachIndexed { idx, cluster ->
                val color = EXPORT_PALETTE[idx % EXPORT_PALETTE.size]
                val pts = cluster.meanContour
                if (pts.size >= 2) {
                    val spanW = plotW * cluster.relativeDurationRatio.coerceIn(0.35f, 1.0f)
                    val n = pts.size
                    // Draw ±1σ translucent band
                    if (cluster.upperBand1Sigma.size == n && cluster.lowerBand1Sigma.size == n) {
                        val bandPath = Path()
                        for (i in 0 until n) {
                            val x = plotLeft + (i.toFloat() / (n - 1)) * spanW
                            val y = tToY(cluster.upperBand1Sigma[i])
                            if (i == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
                        }
                        for (i in (n - 1) downTo 0) {
                            val x = plotLeft + (i.toFloat() / (n - 1)) * spanW
                            val y = tToY(cluster.lowerBand1Sigma[i])
                            bandPath.lineTo(x, y)
                        }
                        bandPath.close()
                        drawPath(bandPath, color = color.copy(alpha = 0.14f), style = Fill)
                    }

                    // Smooth curve
                    val curvePath = Path()
                    val coords = List(n) { i ->
                        Offset(plotLeft + (i.toFloat() / (n - 1)) * spanW, tToY(pts[i]))
                    }
                    curvePath.moveTo(coords.first().x, coords.first().y)
                    for (i in 0 until coords.lastIndex) {
                        val p0 = coords[max(0, i - 1)]
                        val p1 = coords[i]
                        val p2 = coords[i + 1]
                        val p3 = coords[min(coords.lastIndex, i + 2)]
                        val cp1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
                        val cp2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
                        curvePath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
                    }
                    drawPath(
                        path = curvePath,
                        color = color,
                        style = Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    coords.forEach { pt ->
                        drawCircle(Color.White, radius = 6.5f, center = pt)
                        drawCircle(color, radius = 4.5f, center = pt)
                    }

                    // Checked tone glottal stop bar at tail
                    val endPt = coords.last()
                    if (cluster.isCheckedCategory) {
                        drawLine(
                            color = color,
                            start = Offset(endPt.x, endPt.y - 18f),
                            end = Offset(endPt.x, endPt.y + 18f),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Right Legend Card
            val legLeft = plotRight + 28f
            var legY = plotTop + 8f
            drawText(
                textMeasurer = textMeasurer,
                text = "归纳调类图例 (${clusters.size}类)",
                topLeft = Offset(legLeft, legY),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            legY += 42f
            clusters.take(12).forEachIndexed { idx, c ->
                val color = EXPORT_PALETTE[idx % EXPORT_PALETTE.size]
                drawRoundRect(
                    color = color,
                    topLeft = Offset(legLeft, legY + 6f),
                    size = Size(22f, 10f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
                val titleStr = "${c.categoryName} [${c.canonicalChao}] (n=${c.sampleCount})"
                drawText(
                    textMeasurer = textMeasurer,
                    text = titleStr,
                    topLeft = Offset(legLeft + 32f, legY),
                    style = TextStyle(color = Color(0xFF1E293B), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                )
                val exStr = c.exampleWords.take(4).joinToString("、")
                if (exStr.isNotBlank()) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "例字: $exStr · ${c.meanDurationMs}ms",
                        topLeft = Offset(legLeft + 32f, legY + 24f),
                        style = TextStyle(color = Color(0xFF64748B), fontSize = 8.5.sp)
                    )
                }
                legY += 62f
            }
        }
    }

    private fun renderVowelSpacePng(
        projectName: String,
        speakerName: String,
        tokens: List<NormalizedVowelToken>,
        ellipses: List<VowelConfidenceEllipse>,
        textMeasurer: TextMeasurer
    ): ByteArray {
        val widthPx = 1600
        val heightPx = 1120
        return renderDrawScopeToPngBytes(widthPx, heightPx) {
            drawRect(Color(0xFFFAFBFD))

            drawText(
                textMeasurer = textMeasurer,
                text = "$projectName · 声学元音格局与复合动程图 ($speakerName)",
                topLeft = Offset(64f, 36f),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "Bark 临界频带倒置声学坐标系（顶部 F2 前—后，右侧 F1 高—低）| 椭圆为 1σ 置信域，箭头为复合元音 20%→80% 动程",
                topLeft = Offset(64f, 74f),
                style = TextStyle(color = Color(0xFF64748B), fontSize = 10.sp)
            )

            val plotLeft = 110f
            val plotTop = 150f
            val plotRight = size.width - 410f
            val plotBottom = size.height - 86f
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 2f)
            )

            val f1MinBark = PhoneticScaleUtils.hzToBark(200f)
            val f1MaxBark = PhoneticScaleUtils.hzToBark(1050f)
            val f2MinBark = PhoneticScaleUtils.hzToBark(650f)
            val f2MaxBark = PhoneticScaleUtils.hzToBark(2850f)

            fun barkToCanvas(b1: Float, b2: Float): Offset {
                val x = plotLeft + ((f2MaxBark - b2) / (f2MaxBark - f2MinBark)).coerceIn(0.01f, 0.99f) * plotW
                val y = plotTop + ((b1 - f1MinBark) / (f1MaxBark - f1MinBark)).coerceIn(0.01f, 0.99f) * plotH
                return Offset(x, y)
            }
            fun hzToCanvas(f1: Float, f2: Float): Offset =
                barkToCanvas(PhoneticScaleUtils.hzToBark(f1), PhoneticScaleUtils.hzToBark(f2))

            // F2 top grid (Hz ticks)
            for (f2Tick in listOf(2600, 2200, 1800, 1400, 1000, 750)) {
                val x = hzToCanvas(500f, f2Tick.toFloat()).x
                drawLine(Color(0xFFF1F5F9), Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 1.4f)
                val lbl = textMeasurer.measure("$f2Tick Hz", TextStyle(color = Color(0xFF64748B), fontSize = 8.5.sp))
                drawText(lbl, topLeft = Offset(x - lbl.size.width / 2f, plotTop - lbl.size.height - 8f))
            }
            // F1 right grid (Hz ticks)
            for (f1Tick in listOf(250, 350, 500, 650, 800, 950)) {
                val y = hzToCanvas(f1Tick.toFloat(), 1500f).y
                drawLine(Color(0xFFF1F5F9), Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.4f)
                val lbl = textMeasurer.measure("$f1Tick Hz", TextStyle(color = Color(0xFF64748B), fontSize = 8.5.sp))
                drawText(lbl, topLeft = Offset(plotRight + 10f, y - lbl.size.height / 2f))
            }

            val groupKeys = (ellipses.map { it.groupKey } + tokens.map { it.groupKey }).distinct()
            val groupColorMap = groupKeys.withIndex().associate { (i, k) -> k to EXPORT_PALETTE[i % EXPORT_PALETTE.size] }

            // Draw 1-sigma Bark ellipses
            for (el in ellipses) {
                val color = groupColorMap[el.groupKey] ?: EXPORT_PALETTE[0]
                val ellipsePath = Path()
                val steps = 48
                val cosA = cos(el.angleRadBark)
                val sinA = sin(el.angleRadBark)
                for (s in 0..steps) {
                    val theta = (2.0 * PI * s / steps).toFloat()
                    val u = el.semiMajorBark * cos(theta)
                    val v = el.semiMinorBark * sin(theta)
                    val b1 = el.centerBarkF1 + u * cosA - v * sinA
                    val b2 = el.centerBarkF2 + u * sinA + v * cosA
                    val pt = barkToCanvas(b1, b2)
                    if (s == 0) ellipsePath.moveTo(pt.x, pt.y) else ellipsePath.lineTo(pt.x, pt.y)
                }
                ellipsePath.close()
                drawPath(ellipsePath, color = color.copy(alpha = 0.12f), style = Fill)
                drawPath(
                    ellipsePath,
                    color = color.copy(alpha = 0.55f),
                    style = Stroke(width = 2.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                )
            }

            // Draw vowel tokens & diphthong arrows
            tokens.forEachIndexed { idx, token ->
                val color = groupColorMap[token.groupKey] ?: EXPORT_PALETTE[0]
                if (token.vowelType != VowelGlideType.MONOPHTHONG && token.barkGlideSpan >= 0.85f) {
                    val startPt = hzToCanvas(token.onsetF1Hz.toFloat(), token.onsetF2Hz.toFloat())
                    val midPt = hzToCanvas(token.nucleusF1Hz.toFloat(), token.nucleusF2Hz.toFloat())
                    val endPt = hzToCanvas(token.targetF1Hz.toFloat(), token.targetF2Hz.toFloat())
                    val trajPath = Path().apply {
                        moveTo(startPt.x, startPt.y)
                        quadraticTo(midPt.x, midPt.y, endPt.x, endPt.y)
                    }
                    drawPath(trajPath, color = color.copy(alpha = 0.78f), style = Stroke(width = 3.2f, cap = StrokeCap.Round))
                    drawArrowHead(from = midPt, to = endPt, color = color, headLen = 13f)
                    drawCircle(color, radius = 5f, center = startPt)
                } else {
                    val pt = hzToCanvas(token.nucleusF1Hz.toFloat(), token.nucleusF2Hz.toFloat())
                    drawCircle(Color.White, radius = 7.5f, center = pt)
                    drawCircle(color, radius = 5.5f, center = pt)
                }

                // Draw non-crowded label for up to 65 tokens
                if (tokens.size <= 65 || idx % 2 == 0) {
                    val anchor = hzToCanvas(token.nucleusF1Hz.toFloat(), token.nucleusF2Hz.toFloat())
                    val lbl = textMeasurer.measure(
                        token.displayLabel.take(6),
                        TextStyle(color = color, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
                    )
                    val lx = (anchor.x + 8f).coerceAtMost(plotRight - lbl.size.width - 4f)
                    val ly = (anchor.y - lbl.size.height - 3f).coerceAtLeast(plotTop + 4f)
                    drawText(lbl, topLeft = Offset(lx, ly))
                }
            }

            // Legend on the right
            val legLeft = plotRight + 88f
            var legY = plotTop + 8f
            drawText(
                textMeasurer = textMeasurer,
                text = "音类/分组图例",
                topLeft = Offset(legLeft, legY),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            legY += 40f
            groupKeys.take(14).forEach { key ->
                val color = groupColorMap[key] ?: EXPORT_PALETTE[0]
                val el = ellipses.find { it.groupKey == key }
                val sampleCnt = tokens.count { it.groupKey == key }
                val labelName = el?.groupLabel ?: tokens.firstOrNull { it.groupKey == key }?.groupLabel ?: key
                drawCircle(color, radius = 7f, center = Offset(legLeft + 10f, legY + 13f))
                drawText(
                    textMeasurer = textMeasurer,
                    text = "$labelName (n=$sampleCnt)",
                    topLeft = Offset(legLeft + 26f, legY),
                    style = TextStyle(color = Color(0xFF1E293B), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                )
                if (el != null) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = String.format(Locale.US, "均值 F1=%.0f / F2=%.0f Hz", el.centerF1Hz, el.centerF2Hz),
                        topLeft = Offset(legLeft + 26f, legY + 23f),
                        style = TextStyle(color = Color(0xFF64748B), fontSize = 8.sp)
                    )
                    legY += 54f
                } else {
                    legY += 36f
                }
            }
        }
    }

    private fun renderMultiSpeakerToneComparisonPng(
        projectName: String,
        aligned: MultiSpeakerAlignedAnalysis,
        textMeasurer: TextMeasurer
    ): ByteArray {
        val widthPx = 1600
        val heightPx = 1040
        return renderDrawScopeToPngBytes(widthPx, heightPx) {
            drawRect(Color(0xFFFAFBFD))

            drawText(
                textMeasurer = textMeasurer,
                text = "$projectName · 多发音人五度声调格局对比图 (${aligned.cohorts.size}人 · 一致率 ${aligned.toneConsistencyPercent}%)",
                topLeft = Offset(64f, 36f),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "消除个人基频高低差异后的五度值 (1..5) 分栏跨人叠绘对比",
                topLeft = Offset(64f, 74f),
                style = TextStyle(color = Color(0xFF64748B), fontSize = 10.sp)
            )

            val plotLeft = 116f
            val plotTop = 154f
            val plotRight = size.width - 390f
            val plotBottom = size.height - 86f
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 2f)
            )

            val degLabels = listOf("5 高", "4 半高", "3 中", "2 半低", "1 低")
            for (d in 0..4) {
                val y = plotTop + 36f + (d / 4f) * (plotH - 64f)
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                val lbl = textMeasurer.measure(degLabels[d], TextStyle(color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
                drawText(lbl, topLeft = Offset(plotLeft - lbl.size.width - 12f, y - lbl.size.height / 2f))
            }

            fun pToY(p01: Float): Float = plotTop + 36f + (1f - p01.coerceIn(0f, 1f)) * (plotH - 64f)

            val cats = aligned.alignedToneCategories.take(7)
            val laneCount = max(1, cats.size)
            val laneW = plotW / laneCount

            cats.forEachIndexed { laneIdx, cat ->
                val lx = plotLeft + laneIdx * laneW
                if (laneIdx > 0) {
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(lx, plotTop),
                        end = Offset(lx, plotBottom),
                        strokeWidth = 1.6f
                    )
                }
                val title = textMeasurer.measure(
                    text = cat.categoryLabel.take(8),
                    style = TextStyle(color = Color(0xFF0F172A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                drawText(title, topLeft = Offset(lx + (laneW - title.size.width) * 0.5f, plotTop + 8f))

                val pad = (laneW * 0.12f).coerceIn(12f, 28f)
                val maxSpan = (laneW - pad * 2f).coerceAtLeast(24f)
                cat.cohortRealizations.forEach { real ->
                    val col = EXPORT_PALETTE[real.colorIndex % EXPORT_PALETTE.size]
                    val span = maxSpan * real.relativeDurationRatio.coerceIn(0.45f, 1.0f)
                    val path = Path()
                    val pts = real.meanContour.mapIndexed { idx, p ->
                        val pt = Offset(lx + pad + (idx / 4f) * span, pToY(p))
                        if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                        pt
                    }
                    drawPath(
                        path = path,
                        color = col,
                        style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    if (real.isChecked) {
                        val end = pts.last()
                        drawLine(col, Offset(end.x, end.y - 12f), Offset(end.x, end.y + 12f), strokeWidth = 3.5f)
                    } else if (pts.size >= 2) {
                        drawArrowHead(pts[pts.size - 2], pts.last(), col, 12f)
                    }
                }
            }

            val legLeft = plotRight + 34f
            var legY = plotTop + 8f
            drawText(
                textMeasurer = textMeasurer,
                text = "受试者声调摘要",
                topLeft = Offset(legLeft, legY),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            legY += 38f
            aligned.speakerToneInventories.forEach { inv ->
                val col = EXPORT_PALETTE[inv.colorIndex % EXPORT_PALETTE.size]
                drawCircle(col, radius = 7.5f, center = Offset(legLeft + 10f, legY + 13f))
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${inv.cohortLabel} (${inv.totalToneCount}调 · ${inv.pitchRangeText})",
                    topLeft = Offset(legLeft + 26f, legY),
                    style = TextStyle(color = Color(0xFF1E293B), fontSize = 9.2.sp, fontWeight = FontWeight.SemiBold)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = inv.inventoryChaoSummary.take(28),
                    topLeft = Offset(legLeft + 26f, legY + 22f),
                    style = TextStyle(color = Color(0xFF64748B), fontSize = 8.2.sp)
                )
                legY += 58f
            }
        }
    }

    private fun renderLobanovMultiSpeakerPng(
        projectName: String,
        multiReport: MultiSpeakerVowelComparisonReport,
        aligned: MultiSpeakerAlignedAnalysis,
        textMeasurer: TextMeasurer
    ): ByteArray {
        val widthPx = 1600
        val heightPx = 1080
        return renderDrawScopeToPngBytes(widthPx, heightPx) {
            drawRect(Color(0xFFFAFBFD))

            drawText(
                textMeasurer = textMeasurer,
                text = "$projectName · 多发音人 Lobanov Z-score 声道归一化对比图 (${multiReport.speakerReports.size}人)",
                topLeft = Offset(64f, 36f),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = aligned.calibrationNote,
                topLeft = Offset(64f, 74f),
                style = TextStyle(color = Color(0xFF64748B), fontSize = 9.5.sp)
            )

            val plotLeft = 110f
            val plotTop = 148f
            val plotRight = size.width - 400f
            val plotBottom = size.height - 86f
            val plotW = plotRight - plotLeft
            val plotH = plotBottom - plotTop

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotW, plotH),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 2f)
            )

            val zMin = -2.4f
            val zMax = 2.4f
            fun zToCanvas(z1: Float, z2: Float): Offset {
                val x = plotLeft + ((zMax - z2) / (zMax - zMin)).coerceIn(0.01f, 0.99f) * plotW
                val y = plotTop + ((z1 - zMin) / (zMax - zMin)).coerceIn(0.01f, 0.99f) * plotH
                return Offset(x, y)
            }

            for (tick in listOf(-2f, -1f, 0f, 1f, 2f)) {
                val x = zToCanvas(0f, tick).x
                val y = zToCanvas(tick, 0f).y
                drawLine(
                    color = if (tick == 0f) Color(0xFFCBD5E1) else Color(0xFFF1F5F9),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = if (tick == 0f) 2f else 1.3f
                )
                drawLine(
                    color = if (tick == 0f) Color(0xFFCBD5E1) else Color(0xFFF1F5F9),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = if (tick == 0f) 2f else 1.3f
                )
                val xLbl = textMeasurer.measure(String.format(Locale.US, "Z2=%+.0f", tick), TextStyle(color = Color(0xFF64748B), fontSize = 8.5.sp))
                drawText(xLbl, topLeft = Offset(x - xLbl.size.width / 2f, plotTop - xLbl.size.height - 8f))
                val yLbl = textMeasurer.measure(String.format(Locale.US, "Z1=%+.0f", tick), TextStyle(color = Color(0xFF64748B), fontSize = 8.5.sp))
                drawText(yLbl, topLeft = Offset(plotRight + 10f, y - yLbl.size.height / 2f))
            }

            // 1. Outer Convex Hull Polygons per Speaker Cohort
            aligned.cohorts.forEach { cohort ->
                val col = EXPORT_PALETTE[cohort.colorIndex % EXPORT_PALETTE.size]
                if (cohort.convexHullLobanov.size >= 3) {
                    val hullPath = Path()
                    cohort.convexHullLobanov.forEachIndexed { idx, (z2, z1) ->
                        val pt = zToCanvas(z1, z2)
                        if (idx == 0) hullPath.moveTo(pt.x, pt.y) else hullPath.lineTo(pt.x, pt.y)
                    }
                    hullPath.close()
                    drawPath(hullPath, col.copy(alpha = 0.08f), style = Fill)
                    drawPath(hullPath, col.copy(alpha = 0.62f), style = Stroke(width = 2.6f))
                }
            }

            // 2. Calibrated Tokens
            val spkColorMap = aligned.cohorts.flatMap { c ->
                c.speakerIds.map { sid -> sid to EXPORT_PALETTE[c.colorIndex % EXPORT_PALETTE.size] }
            }.toMap()

            for (token in aligned.calibratedTokens) {
                val color = spkColorMap[token.speakerId] ?: EXPORT_PALETTE[0]
                val pt = zToCanvas(token.z1Nucleus, token.z2Nucleus)
                drawCircle(Color.White, radius = 6.2f, center = pt)
                drawCircle(color, radius = 4.2f, center = pt)
            }

            // 3. Vowel Category Centroids & Shift Vectors + Category Labels
            aligned.alignedVowelCategories.forEach { vCat ->
                vCat.shiftVectors.forEach { vec ->
                    val from = zToCanvas(vec.fromZ1, vec.fromZ2)
                    val to = zToCanvas(vec.toZ1, vec.toZ2)
                    val col = EXPORT_PALETTE[vec.toColorIndex % EXPORT_PALETTE.size]
                    if ((to - from).getDistance() >= 14f) {
                        drawLine(
                            color = col,
                            start = from,
                            end = to,
                            strokeWidth = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)
                        )
                        drawArrowHead(from, to, col, 11f)
                    }
                }
                if (vCat.cohortStats.isNotEmpty()) {
                    val avgZ1 = vCat.cohortStats.map { it.centerZ1 }.average().toFloat()
                    val avgZ2 = vCat.cohortStats.map { it.centerZ2 }.average().toFloat()
                    val cPt = zToCanvas(avgZ1, avgZ2)
                    val lbl = textMeasurer.measure(
                        text = "[${vCat.groupLabel.substringBefore(' ')}]",
                        style = TextStyle(color = Color(0xFF0F172A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                    drawText(
                        lbl,
                        topLeft = Offset(
                            (cPt.x - lbl.size.width * 0.5f).coerceIn(plotLeft + 6f, plotRight - lbl.size.width - 6f),
                            (cPt.y - lbl.size.height - 6f).coerceIn(plotTop + 6f, plotBottom - lbl.size.height - 6f)
                        )
                    )
                }
            }

            val legLeft = plotRight + 88f
            var legY = plotTop + 8f
            drawText(
                textMeasurer = textMeasurer,
                text = "发音人外包络图例",
                topLeft = Offset(legLeft, legY),
                style = TextStyle(color = Color(0xFF0F172A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
            legY += 40f
            aligned.cohorts.forEach { cohort ->
                val color = EXPORT_PALETTE[cohort.colorIndex % EXPORT_PALETTE.size]
                drawCircle(color, radius = 7.5f, center = Offset(legLeft + 10f, legY + 13f))
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${cohort.label} · ${cohort.metaSubtitle}".take(24),
                    topLeft = Offset(legLeft + 26f, legY),
                    style = TextStyle(color = Color(0xFF1E293B), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format(Locale.US, "已分析 %d 词 · 包络面积 %.1f Z²", cohort.analyzedWordCount, cohort.vowelSpaceAreaZ2),
                    topLeft = Offset(legLeft + 26f, legY + 23f),
                    style = TextStyle(color = Color(0xFF64748B), fontSize = 8.5.sp)
                )
                legY += 56f
            }
        }
    }

    private fun DrawScope.drawArrowHead(from: Offset, to: Offset, color: Color, headLen: Float) {
        val angle = atan2(to.y - from.y, to.x - from.x)
        val spread = 0.48f
        val p1 = Offset(to.x - headLen * cos(angle - spread), to.y - headLen * sin(angle - spread))
        val p2 = Offset(to.x - headLen * cos(angle + spread), to.y - headLen * sin(angle + spread))
        val p = Path().apply {
            moveTo(to.x, to.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            close()
        }
        drawPath(p, color = color, style = Fill)
    }
}
