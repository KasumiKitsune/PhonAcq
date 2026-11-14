package com.example.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Part 2 of the Material You Dynamic-Color Scientific Illustration Suite:
 * Volumes 3 (Acoustic Charts, #12-#16), 4 (Recording Acoustics, #17-#20), and 5 (Archiving & Files, #21-#24).
 */
internal fun EncyclopediaCanvasHelper.renderDiagramPart2(id: EncyclopediaDiagramId) {
    when (id) {
        // ================= VOL 3: ACOUSTIC CHARTS (12-16) =================
        EncyclopediaDiagramId.ENC_09_WAVEFORM_CLIPPING_CUP -> draw12WaveformClipping()
        EncyclopediaDiagramId.ENC_10_F0_EDGE_PERTURBATION -> draw13F0EdgePerturbation()
        EncyclopediaDiagramId.ENC_11_SPECTROGRAM_LANDSCAPE -> draw14SpectrogramLandscape()
        EncyclopediaDiagramId.ENC_12_ORAL_CAVITY_TO_VOWEL_CHART -> draw15OralCavityToVowelChart()
        EncyclopediaDiagramId.ENC_13_VOCAL_TRACT_NORMALIZATION -> draw16VocalTractNormalization()

        // ================= VOL 4: AUDIO & RECORDING (17-20) =================
        EncyclopediaDiagramId.ENC_14_AI_DENOISE_ERASES_FRICATIVES -> draw17AiDenoiseHarm()
        EncyclopediaDiagramId.ENC_15_BLUETOOTH_NARROWBAND_CUT -> draw18BluetoothNarrowbandCut()
        EncyclopediaDiagramId.ENC_16_MIC_DISTANCE_ANGLE -> draw19MicDistanceAngle()
        EncyclopediaDiagramId.ENC_20_LOSSLESS_WAV_VS_MP3 -> draw20LosslessWavVsMp3()

        // ================= VOL 5: FILES & ARCHIVING (25-28) =================
        EncyclopediaDiagramId.ENC_17_ZIP_BOX_EXTRACT_FIRST -> draw21ZipExtractWorkflow()
        EncyclopediaDiagramId.ENC_18_WAV_TEXTGRID_COLLECTION_LAYERS -> draw22WavTextGridCollection()
        EncyclopediaDiagramId.ENC_23_CSV_UTF8_BOM_ENCODING -> draw23CsvUtf8BomEncoding()
        EncyclopediaDiagramId.ENC_24_WAV_TEXTGRID_STEM_PAIRING -> draw24WavTextGridStemPairing()

        // ================= NEW EXPANDED ENTRIES (25-28 DIAGRAMS) =================
        EncyclopediaDiagramId.ENC_25_PICTURE_ELICITATION_BYPASS -> draw25PictureElicitationBypass()
        EncyclopediaDiagramId.ENC_26_PHONATION_GLOTTAL_STATES -> draw26PhonationGlottalStates()
        EncyclopediaDiagramId.ENC_27_FRICATIVE_COG_SPECTRA -> draw27FricativeCogSpectra()
        EncyclopediaDiagramId.ENC_28_COARTICULATION_BOUNDARIES -> draw28CoarticulationBoundaries()
        else -> Unit
    }
}

// -------------------------------------------------------------------------
// #12 时域波形与削波失真（Clipping）
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw12WaveformClipping() {
    drawPanel(12f, 12f, 180f, 166f, p.primaryContainer.copy(alpha = 0.38f), p.primary.copy(alpha = 0.38f))
    drawPanel(208f, 12f, 180f, 166f, p.errorContainer.copy(alpha = 0.38f), p.error.copy(alpha = 0.42f))

    // Left: Clean headroom (-12 dBFS)
    drawPill("-12 dBFS 安全动态余量", 102f, 28f, p.surface, p.primary, fontPx = 8.2f)
    drawPanel(22f, 44f, 160f, 98f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.5f), radius = 9f)
    // 0 dBFS ceiling & floor lines
    scope.drawLine(p.outline, pt(26f, 52f), pt(178f, 52f), strokeWidth = u(1.1f), pathEffect = dashFine)
    scope.drawLine(p.outline, pt(26f, 134f), pt(178f, 134f), strokeWidth = u(1.1f), pathEffect = dashFine)
    scope.drawLine(p.outlineVariant.copy(alpha = 0.4f), pt(26f, 93f), pt(178f, 93f), strokeWidth = u(0.9f))

    val cleanWave = Path().apply {
        for (px in 0..144 step 2) {
            val t = px / 144f
            val env = sin(t * PI).toFloat()
            val yVal = 93f - (sin(t * 6f * PI) * 0.78 + sin(t * 12f * PI) * 0.22).toFloat() * 31f * env
            if (px == 0) moveTo(x(30f + px), y(yVal)) else lineTo(x(30f + px), y(yVal))
        }
    }
    scope.drawPath(cleanWave, p.primary, style = Stroke(width = u(2.4f), cap = StrokeCap.Round))
    drawPill("✓ 波峰圆润 · 频谱无畸变", 102f, 159f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8.4f)

    // Right: Hard clipped (> 0 dBFS)
    drawPill("> 0 dBFS 过载冲顶", 298f, 28f, p.errorContainer, p.error, fontPx = 8.2f)
    drawPanel(218f, 44f, 160f, 98f, p.surfaceLowest, p.error.copy(alpha = 0.4f), radius = 9f)
    val ceilY = 58f
    val floorY = 128f
    scope.drawLine(p.error, pt(222f, ceilY), pt(374f, ceilY), strokeWidth = u(1.5f), pathEffect = dashFine)
    scope.drawLine(p.error, pt(222f, floorY), pt(374f, floorY), strokeWidth = u(1.5f), pathEffect = dashFine)

    val clippedWave = Path().apply {
        for (px in 0..144 step 2) {
            val t = px / 144f
            val env = sin(t * PI).toFloat()
            val raw = 93f - (sin(t * 6f * PI) * 0.82 + sin(t * 12f * PI) * 0.18).toFloat() * 56f * env
            val clamped = raw.coerceIn(ceilY, floorY)
            if (px == 0) moveTo(x(226f + px), y(clamped)) else lineTo(x(226f + px), y(clamped))
        }
    }
    scope.drawPath(clippedWave, p.error, style = Stroke(width = u(2.5f), cap = StrokeCap.Round))
    drawLabel("0 dBFS 削顶线", 298f, 50f, p.error, 7.3f)
    drawPill("✕ 硬削波平顶 · 破音失真", 298f, 159f, p.errorContainer, p.error, fontPx = 8.4f)
}

// -------------------------------------------------------------------------
// #13 基频（F0）曲线与声母微扰（弯头与降尾）
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw13F0EdgePerturbation() {
    drawPanel(16f, 14f, 368f, 162f, p.surface, p.outlineVariant.copy(alpha = 0.55f), radius = 14f)
    drawSubtleGrid(42f, 26f, 326f, 114f, rows = 4, cols = 6)

    // Axes
    scope.drawLine(p.outline, pt(42f, 26f), pt(42f, 140f), strokeWidth = u(1.4f))
    scope.drawLine(p.outline, pt(42f, 140f), pt(368f, 140f), strokeWidth = u(1.4f))
    drawLabel("F0", 26f, 36f, p.onSurfaceVariant, 8.2f, FontWeight.Bold)
    drawLabel("Hz", 26f, 49f, p.onSurfaceVariant, 7.6f)

    // Highlighted Central Rhyme Measurement Window (韵母测量窗)
    drawPanel(116f, 28f, 184f, 110f, p.primaryContainer.copy(alpha = 0.52f), p.primary.copy(alpha = 0.55f), radius = 8f)
    scope.drawLine(p.primary, pt(116f, 28f), pt(116f, 138f), strokeWidth = u(1.5f), pathEffect = dashFine)
    scope.drawLine(p.primary, pt(300f, 28f), pt(300f, 138f), strokeWidth = u(1.5f), pathEffect = dashFine)

    // Left Zone: Voiceless consonant F0 perturbation hook (清声母弯头)
    val onsetHook = Path().apply {
        moveTo(x(58f), y(40f))
        cubicTo(x(74f), y(58f), x(94f), y(74f), x(116f), y(76f))
    }
    scope.drawPath(onsetHook, p.error.copy(alpha = 0.85f), style = Stroke(width = u(2.4f), pathEffect = dashFine, cap = StrokeCap.Round))
    scope.drawCircle(p.error, radius = u(3.2f), center = pt(58f, 40f))
    drawPill("声母微扰“弯头”", 79f, 114f, p.errorContainer.copy(alpha = 0.85f), p.error, fontPx = 7.6f, padH = 4.5f, padV = 1.8f)

    // Center Zone: Stable TBU Rhyme F0 contour with 5 sampling points
    val steadyF0 = Path().apply {
        moveTo(x(116f), y(76f))
        cubicTo(x(170f), y(75f), x(244f), y(76f), x(300f), y(78f))
    }
    scope.drawPath(steadyF0, p.primary, style = Stroke(width = u(3.4f), cap = StrokeCap.Round))
    for (i in 0..4) {
        val px = 132f + i * 38f
        scope.drawCircle(p.surfaceLowest, radius = u(4.2f), center = pt(px, 76.5f))
        scope.drawCircle(p.primary, radius = u(3f), center = pt(px, 76.5f))
    }
    drawPill("✓ 韵母稳态携调段（提取真实调值 55）", 208f, 44f, p.primary, p.onPrimary, fontPx = 8.3f)

    // Right Zone: Coda breathiness / creaky drop (末尾降尾/嘎裂跳点)
    val codaTail = Path().apply {
        moveTo(x(300f), y(78f))
        quadraticTo(x(324f), y(86f), x(342f), y(116f))
    }
    scope.drawPath(codaTail, p.secondary, style = Stroke(width = u(2.2f), pathEffect = dashFine))
    scope.drawCircle(p.secondary, radius = u(2.6f), center = pt(350f, 126f))
    scope.drawCircle(p.secondary, radius = u(2.6f), center = pt(358f, 131f))
    drawPill("气弱/嘎裂“降尾”", 336f, 58f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 7.5f, padH = 4.5f, padV = 1.8f)

    // Bottom phonetic segmentation bar
    drawLabel("[ pʰ / s ] 声母段", 79f, 156f, p.onSurfaceVariant, 8.2f)
    drawLabel("[ a / aŋ ] 韵母核心段", 208f, 156f, p.primary, 8.6f, FontWeight.ExtraBold)
    drawLabel("收尾段", 336f, 156f, p.onSurfaceVariant, 8.2f)
}

// -------------------------------------------------------------------------
// #14 宽带语图谱（Spectrogram）的声学判读
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw14SpectrogramLandscape() {
    drawPanel(38f, 14f, 344f, 144f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.65f), radius = 10f)

    // Frequency Y axis (0 - 5kHz)
    listOf("5k" to 22f, "3k" to 68f, "1k" to 118f, "0" to 150f).forEach { (lbl, fy) ->
        drawLabel(lbl, 22f, fy, p.onSurfaceVariant, 7.8f, monospace = true)
        scope.drawLine(p.outlineVariant.copy(alpha = 0.35f), pt(38f, fy), pt(382f, fy), strokeWidth = u(0.8f), pathEffect = dashFine)
    }

    // Segment 1 (44..90): Stop closure silence + crisp vertical release burst spike!
    drawLabel("闭塞空白", 65f, 92f, p.outline, 7.5f)
    scope.drawLine(p.onSurface.copy(alpha = 0.8f), pt(92f, 22f), pt(92f, 150f), strokeWidth = u(2.3f))
    drawPill("冲直条", 92f, 169f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 7.6f, padH = 5f, padV = 1.5f)

    // Segment 2 (102..166): High-frequency fricative noise cloud ([s / ʂ])
    for (col in 0..15) {
        val fx = 104f + col * 4f
        val topJit = 22f + (col * 7 % 11)
        val botJit = 78f + (col * 5 % 13)
        scope.drawLine(
            color = p.secondary.copy(alpha = 0.48f + (col % 3) * 0.14f),
            start = pt(fx, topJit),
            end = pt(fx, botJit),
            strokeWidth = u(2.2f),
            cap = StrokeCap.Round
        )
    }
    drawPill("擦音高频乱纹", 136f, 96f, p.secondaryContainer, p.secondary, fontPx = 7.6f, padH = 5f, padV = 1.5f)

    // Segment 3 (178..368): Vowel vertical glottal striations + dark horizontal F1/F2/F3 formant bands + tracking dots
    for (gx in 180..364 step 5) {
        scope.drawLine(
            color = p.primary.copy(alpha = 0.14f),
            start = pt(gx.toFloat(), 32f),
            end = pt(gx.toFloat(), 150f),
            strokeWidth = u(1.6f)
        )
    }

    val formants = listOf(
        Triple("F3", 52f, p.tertiary),
        Triple("F2", 92f, p.secondary),
        Triple("F1", 132f, p.primary)
    )
    formants.forEach { (name, baseY, dotColor) ->
        // Dark broadband formant energy bar
        scope.drawRoundRect(
            color = p.onSurface.copy(alpha = 0.22f),
            topLeft = pt(178f, baseY - 8.5f),
            size = sz(186f, 17f),
            cornerRadius = cr(8f)
        )
        // Tracked formant dots along the center of each band
        for (i in 0..9) {
            val dx = 188f + i * 18.5f
            val dy = baseY + sin(i * 0.45f) * 2.2f
            scope.drawCircle(p.surfaceLowest, radius = u(3.6f), center = pt(dx, dy))
            scope.drawCircle(dotColor, radius = u(2.5f), center = pt(dx, dy))
        }
        drawPill(name, 370f, baseY, dotColor.copy(alpha = 0.18f), dotColor, fontPx = 7.6f, padH = 4f, padV = 1f)
    }

    drawPill("元音共振峰横杠 (F1 / F2 / F3)", 272f, 26f, p.primaryContainer, p.primary, fontPx = 8f)
    drawLabel("时间 (ms)", 256f, 171f, p.onSurfaceVariant, 8.2f)
    drawStraightArrow(288f, 171f, 336f, 171f, p.onSurfaceVariant, strokeW = 1.6f, headLen = 5.8f)
}

// -------------------------------------------------------------------------
// #15 共振峰（F1、F2）与声学元音四边形
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw15OralCavityToVowelChart() {
    // Left: Sagittal vocal tract profile facing LEFT
    drawPanel(12f, 12f, 162f, 166f, p.secondaryContainer.copy(alpha = 0.36f), p.outlineVariant, radius = 14f)
    drawLabel("生理口腔舌位（面朝左）", 93f, 27f, p.onSurface, 8.8f, FontWeight.ExtraBold)

    // Upper palate + nasal/lip silhouette facing left
    val palate = Path().apply {
        moveTo(x(32f), y(72f)) // Upper lip
        quadraticTo(x(46f), y(54f), x(74f), y(46f)) // Alveolar ridge & hard palate
        quadraticTo(x(122f), y(42f), x(146f), y(68f)) // Soft palate / velum
        quadraticTo(x(158f), y(96f), x(154f), y(152f)) // Posterior pharyngeal wall
    }
    scope.drawPath(palate, p.onSurface.copy(alpha = 0.75f), style = Stroke(width = u(2.4f), cap = StrokeCap.Round))

    // Lower jaw & tongue contour
    val tongue = Path().apply {
        moveTo(x(34f), y(98f)) // Lower lip
        cubicTo(x(58f), y(68f), x(118f), y(72f), x(136f), y(148f))
    }
    scope.drawPath(tongue, p.primary.copy(alpha = 0.22f))
    scope.drawPath(tongue, p.primary, style = Stroke(width = u(2f), cap = StrokeCap.Round))

    // Three cardinal tongue highest-point positions (cleanly inside oral cavity)
    scope.drawCircle(p.primary, radius = u(5f), center = pt(66f, 66f))
    drawLabel("[i]", 51f, 66f, p.primary, 9f, FontWeight.ExtraBold)

    scope.drawCircle(p.secondary, radius = u(5f), center = pt(120f, 70f))
    drawLabel("[u]", 104f, 65f, p.secondary, 9f, FontWeight.ExtraBold)

    scope.drawCircle(p.tertiary, radius = u(5f), center = pt(98f, 126f))
    drawLabel("[a]", 98f, 141f, p.tertiary, 9f, FontWeight.ExtraBold)
    drawStraightArrow(68f, 164f, 44f, 164f, p.onSurfaceVariant, strokeW = 1.5f, headLen = 5.2f)
    drawLabel("前 · 舌位 · 后", 93f, 164f, p.onSurfaceVariant, 7.8f)
    drawStraightArrow(118f, 164f, 142f, 164f, p.onSurfaceVariant, strokeW = 1.5f, headLen = 5.2f)

    // Center双轴逆置映射
    drawStraightArrow(178f, 84f, 208f, 84f, p.primary, strokeW = 2.2f, headLen = 7f)
    drawLabel("逆置双轴", 193f, 69f, p.primary, 7.8f)
    drawLabel("一一映射", 193f, 99f, p.onSurfaceVariant, 7.6f)

    // Right: Inverted F1-F2 Acoustic Vowel Chart
    drawPanel(214f, 12f, 174f, 166f, p.primaryContainer.copy(alpha = 0.36f), p.primary.copy(alpha = 0.38f), radius = 14f)
    // Top F2 axis pointing LEFT, Right F1 axis pointing DOWN
    drawStraightArrow(356f, 30f, 236f, 30f, p.primary, strokeW = 1.7f, headLen = 6.2f)
    drawLabel("F2 递增 (Hz / Bark)", 298f, 20f, p.primary, 7.8f)
    drawStraightArrow(370f, 44f, 370f, 148f, p.tertiary, strokeW = 1.7f, headLen = 6.2f)
    drawLabel("F1", 370f, 160f, p.tertiary, 7.8f, FontWeight.Bold)

    val chartQuad = Path().apply {
        moveTo(x(238f), y(48f))
        lineTo(x(350f), y(48f))
        lineTo(x(332f), y(146f))
        lineTo(x(278f), y(146f))
        close()
    }
    scope.drawPath(chartQuad, p.surfaceLowest)
    scope.drawPath(chartQuad, p.primary.copy(alpha = 0.7f), style = Stroke(width = u(1.8f), join = StrokeJoin.Round))

    drawPill("[i] 高前", 258f, 62f, p.primaryContainer, p.primary, fontPx = 8.2f, padH = 5f, padV = 1.8f)
    drawPill("[u] 高后", 332f, 62f, p.secondaryContainer, p.secondary, fontPx = 8.2f, padH = 5f, padV = 1.8f)
    drawPill("[a] 低元音", 305f, 134f, p.tertiaryContainer, p.tertiary, fontPx = 8.2f, padH = 5f, padV = 1.8f)
    drawLabel("声学元音四边形", 296f, 164f, p.primary, 8.6f, FontWeight.ExtraBold)
}

// -------------------------------------------------------------------------
// #16 多发音人对比与声道归一化（Normalization）
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw16VocalTractNormalization() {
    drawPanel(12f, 12f, 170f, 166f, p.secondaryContainer.copy(alpha = 0.38f), p.outlineVariant, radius = 14f)
    drawPanel(218f, 12f, 170f, 166f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.4f), radius = 14f)

    // Left: Raw Hz space — Male (upper-right, smaller) vs Female (lower-left, shifted & larger)
    drawLabel("归一化前（原始 Hz 空间）", 97f, 27f, p.onSurface, 8.8f, FontWeight.ExtraBold)
    drawPanel(22f, 38f, 150f, 108f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.5f), radius = 9f)
    drawSubtleGrid(22f, 38f, 150f, 108f, rows = 3, cols = 3)

    val rawMale = Path().apply {
        moveTo(x(84f), y(48f)); lineTo(x(158f), y(48f)); lineTo(x(126f), y(98f)); close()
    }
    val rawFemale = Path().apply {
        moveTo(x(34f), y(74f)); lineTo(x(118f), y(74f)); lineTo(x(80f), y(136f)); close()
    }
    scope.drawPath(rawMale, p.primary.copy(alpha = 0.14f))
    scope.drawPath(rawMale, p.primary, style = Stroke(width = u(2.2f), join = StrokeJoin.Round))
    scope.drawPath(rawFemale, p.tertiary.copy(alpha = 0.14f))
    scope.drawPath(rawFemale, p.tertiary, style = Stroke(width = u(2.2f), pathEffect = dashFine, join = StrokeJoin.Round))
    drawLabel("男声", 136f, 62f, p.primary, 8f)
    drawLabel("女声", 64f, 108f, p.tertiary, 8f)
    drawPill("生理声道长短造成整体错位", 97f, 161f, p.surface, p.onSurfaceVariant, fontPx = 8f)

    // Center Z-score transform arrow
    drawStraightArrow(186f, 95f, 214f, 95f, p.primary, strokeW = 2.2f, headLen = 6.5f)
    drawLabel("质心", 200f, 78f, p.primary, 8f)
    drawLabel("对齐", 200f, 112f, p.primary, 8f)

    // Right: Normalized Phonological Space — aligned centroids!
    drawLabel("声道归一化后（相对音系空间）", 303f, 27f, p.primary, 8.8f, FontWeight.ExtraBold)
    drawPanel(228f, 38f, 150f, 108f, p.surfaceLowest, p.primary.copy(alpha = 0.35f), radius = 9f)
    drawSubtleGrid(228f, 38f, 150f, 108f, rows = 3, cols = 3)

    val normMale = Path().apply {
        moveTo(x(252f), y(52f)); lineTo(x(354f), y(52f)); lineTo(x(306f), y(130f)); close()
    }
    val normFemale = Path().apply {
        moveTo(x(258f), y(56f)); lineTo(x(350f), y(52f)); lineTo(x(304f), y(128f)); close()
    }
    scope.drawPath(normMale, p.primary.copy(alpha = 0.14f))
    scope.drawPath(normMale, p.primary, style = Stroke(width = u(2.4f), join = StrokeJoin.Round))
    scope.drawPath(normFemale, p.tertiary, style = Stroke(width = u(2.4f), pathEffect = dashFine, join = StrokeJoin.Round))
    // Shared centroid crosshair
    scope.drawLine(p.onSurface, pt(299f, 82f), pt(309f, 82f), strokeWidth = u(1.5f))
    scope.drawLine(p.onSurface, pt(304f, 77f), pt(304f, 87f), strokeWidth = u(1.5f))
    drawPill("✓ 剥离体型差异 · 显现真实音变", 303f, 161f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8f)
}

// -------------------------------------------------------------------------
// #17 为什么语音采录严禁开启通话降噪或 AI 降噪？
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw17AiDenoiseHarm() {
    // Upper card: Intact Linear PCM
    drawPanel(14f, 10f, 372f, 72f, p.primaryContainer.copy(alpha = 0.4f), p.primary.copy(alpha = 0.4f), radius = 12f)
    drawPill("✓ 原始线性 PCM（无降噪）", 310f, 24f, p.primary, p.onPrimary, fontPx = 7.9f)
    val cy1 = 56f
    scope.drawLine(p.outlineVariant, pt(28f, cy1), pt(370f, cy1), strokeWidth = u(0.9f))
    // [s] fricative turbulence (38..104)
    for (fx in 38..104 step 4) {
        val h = 6.5f + (fx % 7) * 1.25f
        scope.drawLine(p.tertiary, pt(fx.toFloat(), cy1 - h), pt(fx.toFloat(), cy1 + h), strokeWidth = u(1.9f), cap = StrokeCap.Round)
    }
    // Vowel harmonics (110..234)
    for (vx in 110..234 step 5) {
        val env = sin(((vx - 110f) / 124f) * PI).toFloat()
        val h = (16f * env).coerceAtLeast(3f)
        scope.drawLine(p.primary, pt(vx.toFloat(), cy1 - h), pt(vx.toFloat(), cy1 + h), strokeWidth = u(2.6f), cap = StrokeCap.Round)
    }
    // Checked [-ʔ / -k] coda impulse (244..272)
    for (cx in 244..272 step 5) {
        val h = if (cx == 254) 12f else 6.5f
        scope.drawLine(p.tertiary, pt(cx.toFloat(), cy1 - h), pt(cx.toFloat(), cy1 + h), strokeWidth = u(2f), cap = StrokeCap.Round)
    }
    drawPill("清擦音 [s]", 71f, 24f, p.surface, p.tertiary, p.tertiary.copy(alpha = 0.35f), fontPx = 7.6f, padH = 5f, padV = 1.4f)
    drawPill("元音谐波主体", 172f, 24f, p.surface, p.primary, p.primary.copy(alpha = 0.35f), fontPx = 7.6f, padH = 5f, padV = 1.4f)
    drawLabel("促声塞尾 [-ʔ]", 324f, 56f, p.tertiary, 8f)

    // Center AI Noise Suppression warning arrow + side-by-side badge (no overlap!)
    drawStraightArrow(92f, 84f, 92f, 105f, p.error, strokeW = 2.4f, headLen = 6.5f)
    drawPill("✕ 经过通话增强 / AI 智能降噪门限", 224f, 95f, p.errorContainer, p.error, fontPx = 8f)

    // Lower card: Damaged signal with wiped fricative & coda
    drawPanel(14f, 108f, 372f, 70f, p.errorContainer.copy(alpha = 0.35f), p.error.copy(alpha = 0.42f), radius = 12f)
    val cy2 = 148f
    scope.drawLine(p.outlineVariant, pt(28f, cy2), pt(370f, cy2), strokeWidth = u(1f))
    // Dead flat erased fricative region!
    scope.drawLine(p.error, pt(38f, cy2), pt(106f, cy2), strokeWidth = u(2.2f))
    drawPill("擦音被误判抹平", 72f, 125f, p.surface, p.error, p.error.copy(alpha = 0.4f), fontPx = 7.6f, padH = 4.5f, padV = 1.4f)
    // Distorted notched vowel
    for (vx in 114..228 step 6) {
        val h = if (vx in 156..174) 4f else 13f
        scope.drawLine(p.error.copy(alpha = 0.8f), pt(vx.toFloat(), cy2 - h), pt(vx.toFloat(), cy2 + h), strokeWidth = u(2.5f), cap = StrokeCap.Round)
    }
    // Dead flat erased coda!
    scope.drawLine(p.error, pt(236f, cy2), pt(288f, cy2), strokeWidth = u(2.2f))
    drawPill("弱塞尾被截断丢失", 274f, 125f, p.surface, p.error, p.error.copy(alpha = 0.4f), fontPx = 7.6f, padH = 4.5f, padV = 1.4f)
    drawLabel("频谱失真伪影", 342f, 148f, p.error, 8f)
}

// -------------------------------------------------------------------------
// #18 为什么不能使用蓝牙耳机麦克风采录语料？
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw18BluetoothNarrowbandCut() {
    drawPanel(12f, 12f, 180f, 166f, p.primaryContainer.copy(alpha = 0.38f), p.primary.copy(alpha = 0.38f))
    drawPanel(208f, 12f, 180f, 166f, p.errorContainer.copy(alpha = 0.36f), p.error.copy(alpha = 0.42f))

    // Left: Built-in / Wired USB Mic (0 - 22 kHz full bandwidth)
    drawStudioMicIcon(34f, 34f, 0.72f, p.primary, p.onSurface)
    drawLabel("机身内置麦 / 有线麦", 112f, 27f, p.onSurface, 9.2f, FontWeight.ExtraBold)
    drawLabel("全频段 PCM (0 ~ 22 kHz)", 112f, 41f, p.primary, 7.8f)

    drawPanel(22f, 54f, 160f, 88f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.5f), radius = 9f)
    val fullHeights = floatArrayOf(62f, 70f, 64f, 54f, 48f, 52f, 58f, 46f, 38f, 30f)
    fullHeights.forEachIndexed { idx, bh ->
        val bx = 30f + idx * 14.5f
        val barColor = if (idx >= 4) p.tertiary else p.primary
        scope.drawRoundRect(barColor, topLeft = pt(bx, 132f - bh), size = sz(9.5f, bh), cornerRadius = cr(3f))
    }
    drawLabel("0k", 34f, 138f, p.onSurfaceVariant, 7f, monospace = true)
    drawLabel("4k", 88f, 138f, p.onSurfaceVariant, 7f, monospace = true)
    drawLabel("8k", 132f, 138f, p.onSurfaceVariant, 7f, monospace = true)
    drawLabel("16k+", 166f, 138f, p.onSurfaceVariant, 7f, monospace = true)
    drawPill("✓ 完整保留 [s / ʂ / ɕ] 高频谱", 102f, 159f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8.2f)

    // Right: Bluetooth Headset Mic (HFP / SCO narrowband brick-wall cut at 4kHz)
    drawEarbudIcon(230f, 34f, 0.85f, p.error)
    drawLabel("蓝牙耳机麦克风", 308f, 27f, p.onSurface, 9.2f, FontWeight.ExtraBold)
    drawLabel("强制退化 HFP 通话窄带协议", 308f, 41f, p.error, 7.6f)

    drawPanel(218f, 54f, 160f, 88f, p.surfaceLowest, p.error.copy(alpha = 0.38f), radius = 9f)
    fullHeights.forEachIndexed { idx, bh ->
        val bx = 226f + idx * 14.5f
        if (idx < 3) {
            scope.drawRoundRect(p.error, topLeft = pt(bx, 132f - bh * 0.82f), size = sz(9.5f, bh * 0.82f), cornerRadius = cr(3f))
        } else {
            scope.drawRoundRect(p.outlineVariant.copy(alpha = 0.4f), topLeft = pt(bx, 126f), size = sz(9.5f, 6f), cornerRadius = cr(2f))
        }
    }
    // Brick-wall cutoff line at 4 kHz!
    scope.drawLine(p.error, pt(270f, 58f), pt(270f, 134f), strokeWidth = u(2f), pathEffect = dashFine)
    drawPill("4kHz 以上全被截断", 324f, 88f, p.errorContainer, p.error, fontPx = 7.6f, padH = 5f, padV = 2f)
    drawLabel("0k", 230f, 138f, p.onSurfaceVariant, 7f, monospace = true)
    drawLabel("4k", 270f, 138f, p.error, 7f, monospace = true)
    drawLabel("16k+", 362f, 138f, p.outline, 7f, monospace = true)
    drawPill("✕ 电话音发闷 · 擦音特征尽失", 298f, 159f, p.errorContainer, p.error, fontPx = 8.2f)
}

// -------------------------------------------------------------------------
// #19 近讲气流冲击（喷麦）与偏轴收音摆位
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw19MicDistanceAngle() {
    drawPanel(14f, 12f, 372f, 166f, p.surface, p.outlineVariant.copy(alpha = 0.55f), radius = 14f)

    // Top-view speaker head + nose tip pointing right at (92, 74)
    val headCx = 62f
    val headCy = 74f
    scope.drawCircle(p.secondaryContainer, radius = u(25f), center = pt(headCx, headCy))
    scope.drawCircle(p.onSurface.copy(alpha = 0.75f), radius = u(25f), center = pt(headCx, headCy), style = Stroke(width = u(2f)))
    // Nose bridge triangle pointing right (mouth axis = y:74)
    val nose = Path().apply {
        moveTo(x(85f), y(66f))
        lineTo(x(96f), y(74f))
        lineTo(x(85f), y(82f))
    }
    scope.drawPath(nose, p.secondaryContainer)
    scope.drawPath(nose, p.onSurface.copy(alpha = 0.75f), style = Stroke(width = u(2f), join = StrokeJoin.Round))
    drawLabel("俯视", headCx, headCy - 5f, p.onSurfaceVariant, 7.8f)
    drawLabel("发音人", headCx, headCy + 8f, p.onSurface, 8.5f, FontWeight.Bold)

    // 0° straight plosive air jet (RED danger zone)
    drawStraightArrow(102f, 74f, 248f, 74f, p.error, strokeW = 2.4f, dashed = true, headLen = 7.5f)
    drawPill("✕ 0° 正对轴线：[pʰ / tʰ] 强气流直击振膜喷麦", 242f, 46f, p.errorContainer, p.error, fontPx = 8f)

    // Spherical acoustic sound wave arcs radiating cleanly to 30°-35° off-axis
    for (ring in 1..3) {
        val rad = u(40f + ring * 24f)
        scope.drawArc(
            color = p.primary.copy(alpha = 0.75f - ring * 0.16f),
            startAngle = 10f,
            sweepAngle = 36f,
            useCenter = false,
            topLeft = Offset(x(94f) - rad, y(74f) - rad),
            size = Size(rad * 2f, rad * 2f),
            style = Stroke(width = u(2.2f), cap = StrokeCap.Round)
        )
    }

    // 30° off-axis guideline + studio condenser microphone at (216, 134)
    scope.drawLine(p.primary, pt(96f, 74f), pt(204f, 130f), strokeWidth = u(1.5f), pathEffect = dashFine)
    drawPill("偏轴 30°~45°", 142f, 122f, p.primaryContainer, p.primary, fontPx = 7.8f, padH = 5f, padV = 1.6f)
    drawPill("距离 15~20 cm（约一拳半）", 150f, 154f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 8f)

    // Highlighted safe mic placement zone
    scope.drawCircle(p.primaryContainer.copy(alpha = 0.7f), radius = u(24f), center = pt(222f, 134f))
    scope.drawCircle(p.primary, radius = u(24f), center = pt(222f, 134f), style = Stroke(width = u(1.4f)))
    drawStudioMicIcon(222f, 134f, 0.85f, p.primary, p.onSurface)

    drawPanel(258f, 108f, 116f, 54f, p.primaryContainer.copy(alpha = 0.45f), p.primary.copy(alpha = 0.38f), radius = 11f)
    drawLabel("✓ 黄金防喷摆位", 316f, 125f, p.primary, 9.2f, FontWeight.ExtraBold)
    drawLabel("球面声波无损拾取", 316f, 141f, p.onSurface, 8f)
    drawLabel("直线冷气从侧面掠过", 316f, 153f, p.onSurfaceVariant, 7.6f)
}

// -------------------------------------------------------------------------
// #20 采样率（44.1kHz / 48kHz）与无损 WAV vs 有损 MP3
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw20LosslessWavVsMp3() {
    drawPanel(12f, 12f, 180f, 166f, p.primaryContainer.copy(alpha = 0.38f), p.primary.copy(alpha = 0.38f))
    drawPanel(208f, 12f, 180f, 166f, p.errorContainer.copy(alpha = 0.35f), p.error.copy(alpha = 0.4f))

    // Left: 44.1kHz / 16-bit Lossless Linear PCM (.wav)
    drawPill("44.1kHz / 16-bit 无损 .wav", 102f, 28f, p.primary, p.onPrimary, fontPx = 8.2f)
    drawPanel(22f, 44f, 160f, 96f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.5f), radius = 9f)
    // Dense discrete PCM sampling stems matching continuous wave
    val baseLineY = 104f
    scope.drawLine(p.outlineVariant, pt(28f, baseLineY), pt(176f, baseLineY), strokeWidth = u(1f))
    val analog = Path()
    for (i in 0..20) {
        val sxPos = 32f + i * 7f
        val syPos = baseLineY - sin(i * 0.42f) * 34f - cos(i * 0.85f) * 8f
        if (i == 0) analog.moveTo(x(sxPos), y(syPos)) else analog.lineTo(x(sxPos), y(syPos))
        scope.drawLine(p.primary.copy(alpha = 0.45f), pt(sxPos, baseLineY), pt(sxPos, syPos), strokeWidth = u(1.3f))
        scope.drawCircle(p.primary, radius = u(2.2f), center = pt(sxPos, syPos))
    }
    scope.drawPath(analog, p.primary, style = Stroke(width = u(1.8f)))
    drawLabel("每秒 44,100 次高精度线性采样", 102f, 128f, p.primary, 7.8f)
    drawPill("✓ 全频谱无损保留 · 国际归档标准", 102f, 159f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8f)

    // Right: Psychoacoustic Lossy Compression (.mp3 / .m4a / 语音条)
    drawPill(".mp3 / 社交语音条 有损压缩", 298f, 28f, p.errorContainer, p.error, fontPx = 8.2f)
    drawPanel(218f, 44f, 160f, 96f, p.surfaceLowest, p.error.copy(alpha = 0.35f), radius = 9f)
    // Spectral grid with carved-out psychoacoustic "holes"
    for (row in 0..4) {
        for (col in 0..7) {
            val missing = (row == 0 && col >= 2) || (row == 1 && col in listOf(1, 4, 5, 7)) || (row == 2 && col in listOf(2, 6))
            val rx = 228f + col * 17.8f
            val ry = 52f + row * 13.5f
            if (missing) {
                scope.drawRoundRect(
                    color = p.error.copy(alpha = 0.45f),
                    topLeft = pt(rx, ry),
                    size = sz(14f, 10f),
                    cornerRadius = cr(2.5f),
                    style = Stroke(width = u(1f), pathEffect = dashFine)
                )
            } else {
                scope.drawRoundRect(
                    color = p.secondary.copy(alpha = 0.55f),
                    topLeft = pt(rx, ry),
                    size = sz(14f, 10f),
                    cornerRadius = cr(2.5f)
                )
            }
        }
    }
    drawLabel("虚线空洞 = 被掩蔽算法丢弃的频带", 298f, 128f, p.error, 7.6f)
    drawPill("✕ 频谱出现断层伪影 · 不宜测参数", 298f, 159f, p.errorContainer, p.error, fontPx = 8f)
}

// -------------------------------------------------------------------------
// #21 .zip 归档压缩包与先解压后分析流程
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw21ZipExtractWorkflow() {
    // Left: Zipped archive box
    drawPanel(14f, 14f, 136f, 162f, p.secondaryContainer.copy(alpha = 0.45f), p.outlineVariant, radius = 14f)
    // Archive carton with zipper track
    drawPanel(42f, 28f, 80f, 86f, p.surface, p.secondary.copy(alpha = 0.6f), radius = 12f, borderWidth = 1.5f)
    for (zy in 34..96 step 9) {
        val offset = if ((zy / 9) % 2 == 0) -3f else 1f
        scope.drawRoundRect(p.secondary, topLeft = pt(80f + offset, zy.toFloat()), size = sz(7f, 5f), cornerRadius = cr(1.5f))
    }
    drawPill(".zip 归档包", 82f, 102f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 8.2f, monospace = true)
    drawLabel("✕ 预览窗内勿直接拖入软件", 82f, 134f, p.error, 7.8f)
    drawLabel("（内部文件尚未释放到磁盘）", 82f, 149f, p.onSurfaceVariant, 7.3f)

    // Center Action Step
    drawStraightArrow(156f, 92f, 212f, 92f, p.primary, strokeW = 2.6f, headLen = 8f)
    drawPill("右键 → 全部解压缩", 184f, 70f, p.primary, p.onPrimary, fontPx = 8.2f)
    drawLabel("无损释放原文件", 184f, 112f, p.primary, 7.8f)

    // Right: Extracted regular folder with ready-to-use files
    drawPanel(218f, 14f, 168f, 162f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.42f), radius = 14f)
    // Folder tab silhouette
    val folderPath = Path().apply {
        moveTo(x(232f), y(40f))
        lineTo(x(278f), y(40f))
        lineTo(x(288f), y(50f))
        lineTo(x(372f), y(50f))
        lineTo(x(372f), y(136f))
        lineTo(x(232f), y(136f))
        close()
    }
    scope.drawPath(folderPath, p.surface)
    scope.drawPath(folderPath, p.primary.copy(alpha = 0.6f), style = Stroke(width = u(1.4f), join = StrokeJoin.Round))

    drawFileSheet(242f, 60f, 36f, 46f, p.primary, p.primaryContainer.copy(alpha = 0.35f), ".wav")
    drawFileSheet(284f, 60f, 36f, 46f, p.secondary, p.secondaryContainer.copy(alpha = 0.45f), ".TG")
    drawFileSheet(326f, 60f, 36f, 46f, p.tertiary, p.tertiaryContainer.copy(alpha = 0.4f), ".csv")
    drawLabel("音频 + 标注 + 统计表", 302f, 122f, p.onSurfaceVariant, 8f)
    drawPill("✓ 常规文件夹直接导入 Praat / Excel", 302f, 156f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8f)
}

// -------------------------------------------------------------------------
// #22 .wav、.csv、.TextGrid 与 .Collection 格式说明
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw22WavTextGridCollection() {
    // Left stacked synchronized architecture
    // 1. Top: .TextGrid annotation tier
    drawPanel(16f, 14f, 214f, 50f, p.primaryContainer.copy(alpha = 0.55f), p.primary.copy(alpha = 0.5f), radius = 11f)
    scope.drawLine(p.primary, pt(76f, 32f), pt(76f, 60f), strokeWidth = u(1.6f))
    scope.drawLine(p.primary, pt(168f, 32f), pt(168f, 60f), strokeWidth = u(1.6f))
    drawLabel("0.12s", 52f, 46f, p.onSurfaceVariant, 7.6f, monospace = true)
    drawPill("巴 · [pa⁵⁵]", 122f, 46f, p.surface, p.primary, fontPx = 8.4f)
    drawLabel("0.48s", 194f, 46f, p.onSurfaceVariant, 7.6f, monospace = true)
    drawPill(".TextGrid 纯文本标注层", 123f, 23f, p.primary, p.onPrimary, fontPx = 7.3f, padH = 5.5f, padV = 1.2f)

    // Alignment connector dashed lines into .wav
    scope.drawLine(p.primary.copy(alpha = 0.45f), pt(76f, 60f), pt(76f, 72f), strokeWidth = u(1.3f), pathEffect = dashFine)
    scope.drawLine(p.primary.copy(alpha = 0.45f), pt(168f, 60f), pt(168f, 72f), strokeWidth = u(1.3f), pathEffect = dashFine)

    // 2. Middle: .wav lossless waveform
    drawPanel(16f, 72f, 214f, 48f, p.secondaryContainer.copy(alpha = 0.5f), p.secondary.copy(alpha = 0.45f), radius = 11f)
    for (wx in 34..210 step 6) {
        val h = if (wx in 78..166) 13f * sin(((wx - 76f) / 92f) * PI).toFloat() + 3f else 2f
        scope.drawLine(p.secondary, pt(wx.toFloat(), 98f - h), pt(wx.toFloat(), 98f + h), strokeWidth = u(2.2f), cap = StrokeCap.Round)
    }
    drawPill(".wav 无损音频波形", 178f, 81f, p.secondary, p.surfaceLowest, fontPx = 7.2f, padH = 5f, padV = 1.1f)

    // 3. Bottom: .csv data spreadsheet
    drawPanel(16f, 128f, 214f, 50f, p.surface, p.outlineVariant, radius = 11f)
    scope.drawLine(p.outlineVariant, pt(24f, 154f), pt(222f, 154f), strokeWidth = u(1f))
    listOf(72f, 124f, 174f).forEach { cx ->
        scope.drawLine(p.outlineVariant, pt(cx, 138f), pt(cx, 173f), strokeWidth = u(1f))
    }
    drawLabel("词条  |  调值  |  F1   |  F2", 123f, 146f, p.onSurfaceVariant, 7.6f, monospace = true)
    drawLabel("001_巴 |  55   | 780Hz | 1240Hz", 123f, 165f, p.tertiary, 7.6f, monospace = true)
    drawPill(".csv 统计表", 50f, 128f, p.tertiaryContainer, p.tertiary, fontPx = 7.2f, padH = 5f, padV = 1.1f)

    // Right: Combined .Collection single-file bundle
    drawQuadArrow(234f, 40f, 254f, 52f, 268f, 68f, p.tertiary, strokeW = 2f, headLen = 6.5f)
    drawQuadArrow(234f, 94f, 254f, 90f, 268f, 82f, p.tertiary, strokeW = 2f, headLen = 6.5f)

    drawPanel(272f, 28f, 114f, 134f, p.tertiaryContainer.copy(alpha = 0.48f), p.tertiary.copy(alpha = 0.5f), radius = 14f)
    drawFileSheet(306f, 42f, 46f, 54f, p.tertiary, p.surface, ".Col")
    drawLabel(".Collection", 329f, 112f, p.tertiary, 10.2f, FontWeight.ExtraBold, monospace = true)
    drawLabel("音标配对合一工程", 329f, 129f, p.onSurface, 8.2f)
    drawLabel("Praat 单文件直读", 329f, 145f, p.onSurfaceVariant, 7.6f)
}

// -------------------------------------------------------------------------
// #23 .csv 字符编码（UTF-8 BOM 与 GBK）与音标显示
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw23CsvUtf8BomEncoding() {
    // Upper row: Missing BOM -> Misread as GBK -> Mojibake
    drawPanel(14f, 14f, 372f, 72f, p.errorContainer.copy(alpha = 0.34f), p.error.copy(alpha = 0.38f), radius = 13f)
    drawPill("无 BOM 纯字节流", 72f, 42f, p.surface, p.onSurfaceVariant, p.outlineVariant, fontPx = 8.2f)
    drawStraightArrow(128f, 42f, 162f, 42f, p.error, strokeW = 2f, headLen = 6f)
    drawPill("旧版 Excel 误按 GBK 解码", 224f, 42f, p.errorContainer, p.error, fontPx = 8f)
    drawStraightArrow(290f, 42f, 314f, 42f, p.error, strokeW = 2f, headLen = 6f)
    drawPill("æˆ‘ / ???", 350f, 42f, p.surface, p.error, p.error, fontPx = 8.8f, monospace = true)
    drawLabel("✕ 缺少编码标识头时，多字节汉字与国际音标易被错拆成乱码", 200f, 70f, p.error, 8f)

    // Lower row: UTF-8 with BOM (EF BB BF) -> Clean Unicode rendering
    drawPanel(14f, 102f, 372f, 74f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.45f), radius = 13f)
    drawPill("EF BB BF", 52f, 130f, p.primary, p.onPrimary, fontPx = 8.2f, monospace = true)
    drawPill("UTF-8 正文", 114f, 130f, p.surface, p.primary, p.primary.copy(alpha = 0.4f), fontPx = 8.2f)
    drawStraightArrow(154f, 130f, 184f, 130f, p.primary, strokeW = 2.2f, headLen = 6.5f)
    drawPill("自动识别 Unicode", 236f, 130f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 8f)
    drawStraightArrow(288f, 130f, 310f, 130f, p.primary, strokeW = 2.2f, headLen = 6.5f)
    drawPill("白读 [pʰa⁵⁵]", 348f, 130f, p.surface, p.primary, p.primary, fontPx = 8.4f)
    drawLabel("✓ 本软件导出自带 UTF-8 BOM 头 · Excel / WPS / R 双击正常显示音标", 200f, 160f, p.primary, 8f)
}

// -------------------------------------------------------------------------
// #24 音频 .wav 与标注 .TextGrid 的同名配对规范
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw24WavTextGridStemPairing() {
    drawPanel(12f, 12f, 182f, 166f, p.primaryContainer.copy(alpha = 0.4f), p.primary.copy(alpha = 0.4f), radius = 14f)
    drawPanel(206f, 12f, 182f, 166f, p.errorContainer.copy(alpha = 0.35f), p.error.copy(alpha = 0.4f), radius = 14f)

    // Left: Identical Filename Stem inside same folder
    drawLabel("同目录 · 同主文件名", 103f, 28f, p.primary, 9.2f, FontWeight.ExtraBold)

    drawPanel(22f, 44f, 162f, 36f, p.surface, p.primary.copy(alpha = 0.4f), radius = 9f)
    drawPill("001_巴", 66f, 62f, p.primaryContainer, p.primary, fontPx = 8.8f, monospace = true)
    drawLabel(".wav", 124f, 62f, p.onSurfaceVariant, 8.8f, monospace = true)

    drawPanel(22f, 92f, 162f, 36f, p.surface, p.primary.copy(alpha = 0.4f), radius = 9f)
    drawPill("001_巴", 66f, 110f, p.primaryContainer, p.primary, fontPx = 8.8f, monospace = true)
    drawLabel(".TextGrid", 136f, 110f, p.onSurfaceVariant, 8.8f, monospace = true)

    // Connecting stem link line
    scope.drawLine(p.primary, pt(66f, 75f), pt(66f, 97f), strokeWidth = u(2.2f))
    drawPill("✓ 脚本与 Praat 自动配对成功", 103f, 155f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8.2f)

    // Right: Mismatched stem breaks batch scripts
    drawLabel("单方重命名导致断链", 297f, 28f, p.error, 9.2f, FontWeight.ExtraBold)

    drawPanel(216f, 44f, 162f, 36f, p.surface, p.error.copy(alpha = 0.45f), radius = 9f)
    drawPill("001_巴_重录", 268f, 62f, p.errorContainer, p.error, fontPx = 8.4f, monospace = true)
    drawLabel(".wav", 342f, 62f, p.onSurfaceVariant, 8.8f, monospace = true)

    drawPanel(216f, 92f, 162f, 36f, p.surface, p.outlineVariant, radius = 9f)
    drawPill("001_巴", 256f, 110f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 8.4f, monospace = true)
    drawLabel(".TextGrid", 328f, 110f, p.onSurfaceVariant, 8.8f, monospace = true)

    drawLabel("≠ 主文件名不一致", 297f, 86f, p.error, 7.8f)
    drawPill("✕ 批处理脚本找不到对应标注", 297f, 155f, p.errorContainer, p.error, fontPx = 8.2f)
}

// -------------------------------------------------------------------------
// #7 看图引出法与“有音无字”词条采录
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw25PictureElicitationBypass() {
    drawPanel(12f, 12f, 180f, 166f, p.errorContainer.copy(alpha = 0.34f), p.error.copy(alpha = 0.4f), radius = 14f)
    drawPanel(208f, 12f, 180f, 166f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.45f), radius = 14f)

    // Left: Written Mandarin prompt induces literal character reading
    drawLabel("直接看普通话字面", 102f, 28f, p.error, 9.2f, FontWeight.ExtraBold)
    drawPanel(46f, 42f, 112f, 36f, p.surface, p.error.copy(alpha = 0.45f), radius = 9f)
    drawLabel("“蜻蜓 / 膝盖”", 102f, 60f, p.onSurface, 10f, FontWeight.ExtraBold)
    drawStraightArrow(102f, 82f, 102f, 104f, p.error, strokeW = 2f, headLen = 6f)
    drawPill("按字面逐字折算文读", 102f, 118f, p.errorContainer, p.error, fontPx = 8f)
    drawLabel("✕ 掩盖地道口语本词（如“丁丁猫 / 磕膝头”）", 102f, 154f, p.error, 7.4f)

    // Right: Visual object bypasses orthography -> authentic vernacular lexical form
    drawLabel("实物配图直接引出", 298f, 28f, p.primary, 9.2f, FontWeight.ExtraBold)
    drawPanel(242f, 40f, 112f, 42f, p.surface, p.primary.copy(alpha = 0.5f), radius = 9f)
    // Minimalist dragonfly vector icon inside picture card
    scope.drawLine(p.primary, pt(298f, 48f), pt(298f, 75f), strokeWidth = u(2.2f), cap = StrokeCap.Round)
    scope.drawCircle(p.primary, radius = u(3.2f), center = pt(298f, 47f))
    scope.drawOval(p.primary.copy(alpha = 0.28f), topLeft = pt(268f, 50f), size = sz(28f, 8f))
    scope.drawOval(p.primary, topLeft = pt(268f, 50f), size = sz(28f, 8f), style = Stroke(width = u(1.2f)))
    scope.drawOval(p.primary.copy(alpha = 0.28f), topLeft = pt(300f, 50f), size = sz(28f, 8f))
    scope.drawOval(p.primary, topLeft = pt(300f, 50f), size = sz(28f, 8f), style = Stroke(width = u(1.2f)))
    scope.drawOval(p.secondary, topLeft = pt(273f, 60f), size = sz(23f, 6.5f), style = Stroke(width = u(1.1f)))
    scope.drawOval(p.secondary, topLeft = pt(300f, 60f), size = sz(23f, 6.5f), style = Stroke(width = u(1.1f)))

    drawStraightArrow(298f, 86f, 298f, 106f, p.primary, strokeW = 2.2f, headLen = 6.5f)
    drawPill("直接激活方言概念与本词", 298f, 118f, p.surface, p.primary, p.primary.copy(alpha = 0.45f), fontPx = 8f)
    drawPill("✓ 自然说出地道口语词与有音无字词", 298f, 154f, p.primaryContainer, p.onPrimaryContainer, fontPx = 7.7f)
}

// -------------------------------------------------------------------------
// #12 发声态：常态浊音、气声与嘎裂声（紧喉）
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw26PhonationGlottalStates() {
    val cols = listOf(
        Triple("常态浊音 Modal", 12f, p.primary),
        Triple("气声 / 弛声 Breathy", 142f, p.secondary),
        Triple("嘎裂 / 紧喉 Creaky", 272f, p.tertiary)
    )
    cols.forEachIndexed { idx, (title, leftX, col) ->
        drawPanel(leftX, 12f, 116f, 166f, col.copy(alpha = 0.12f), col.copy(alpha = 0.42f), radius = 13f)
        drawLabel(title, leftX + 58f, 27f, col, 8.2f, FontWeight.ExtraBold)

        // Glottal fold aperture schematic
        val cx = leftX + 58f
        val gy = 60f
        when (idx) {
            0 -> {
                // Neat symmetrical vocal fold closure
                scope.drawOval(col.copy(alpha = 0.24f), topLeft = pt(cx - 18f, gy - 14f), size = sz(36f, 28f))
                scope.drawOval(col, topLeft = pt(cx - 18f, gy - 14f), size = sz(36f, 28f), style = Stroke(width = u(1.7f)))
                scope.drawLine(col, pt(cx, gy - 11f), pt(cx, gy + 11f), strokeWidth = u(2f), cap = StrokeCap.Round)
                drawLabel("声带规则闭合振动", cx, 85f, p.onSurfaceVariant, 7.3f)
            }
            1 -> {
                // Posterior glottal chink with airflow dots
                scope.drawOval(col.copy(alpha = 0.18f), topLeft = pt(cx - 18f, gy - 14f), size = sz(36f, 28f))
                scope.drawOval(col, topLeft = pt(cx - 18f, gy - 14f), size = sz(36f, 28f), style = Stroke(width = u(1.7f)))
                val chink = Path().apply {
                    moveTo(x(cx), y(gy - 11f))
                    lineTo(x(cx - 5f), y(gy + 10f))
                    lineTo(x(cx + 5f), y(gy + 10f))
                    close()
                }
                scope.drawPath(chink, col.copy(alpha = 0.55f))
                drawLabel("声门留隙 · 伴随漏气", cx, 85f, p.onSurfaceVariant, 7.3f)
            }
            else -> {
                // Tightly adducted vocal folds, anterior short irregular pulses
                scope.drawOval(col.copy(alpha = 0.28f), topLeft = pt(cx - 18f, gy - 14f), size = sz(36f, 28f))
                scope.drawOval(col, topLeft = pt(cx - 18f, gy - 14f), size = sz(36f, 28f), style = Stroke(width = u(2.2f)))
                scope.drawLine(col, pt(cx - 4f, gy + 2f), pt(cx + 4f, gy + 2f), strokeWidth = u(2.4f))
                scope.drawLine(col, pt(cx, gy - 11f), pt(cx, gy + 11f), strokeWidth = u(2.6f), cap = StrokeCap.Round)
                drawLabel("声门紧缩 · 低频双脉冲", cx, 85f, p.onSurfaceVariant, 7.3f)
            }
        }

        // Waveform panel
        drawPanel(leftX + 8f, 96f, 100f, 48f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.45f), radius = 8f)
        val wavePath = Path().apply {
            for (px in 0..88 step 2) {
                val t = px / 88f
                val yOff = when (idx) {
                    0 -> sin(t * 8f * PI).toFloat() * 15f
                    1 -> (sin(t * 7f * PI) * 9.5 + sin(t * 39f * PI) * 4.5).toFloat()
                    else -> {
                        val cyc = (t * 3.5f) % 1f
                        when {
                            cyc < 0.16f -> sin(cyc / 0.16f * 2f * PI).toFloat() * 16f
                            cyc < 0.30f -> sin((cyc - 0.16f) / 0.14f * 2f * PI).toFloat() * 8f
                            else -> 0f
                        }
                    }
                }
                val wy = 120f - yOff
                if (px == 0) moveTo(x(leftX + 14f + px), y(wy)) else lineTo(x(leftX + 14f + px), y(wy))
            }
        }
        scope.drawPath(wavePath, col, style = Stroke(width = u(1.9f), cap = StrokeCap.Round))

        val badge = when (idx) {
            0 -> "[a] 周期清晰"
            1 -> "[a̤ / pɦ] H1-H2↑"
            else -> "[a̰] 易八度跳降"
        }
        drawPill(badge, cx, 160f, p.surface, col, col.copy(alpha = 0.4f), fontPx = 7.6f)
    }
}

// -------------------------------------------------------------------------
// #13 擦音与塞擦音：平翘舌与舌面音的声学区分
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw27FricativeCogSpectra() {
    drawPanel(12f, 12f, 376f, 166f, p.surfaceHigh.copy(alpha = 0.35f), p.outlineVariant, radius = 14f)
    drawLabel("高频摩擦噪声能量集中区（频谱重心 CoG）对比", 200f, 26f, p.onSurface, 9.2f, FontWeight.ExtraBold)

    // Frequency scale on left (0 - 10 kHz)
    val chartL = 56f
    val chartT = 40f
    val chartW = 316f
    val chartH = 104f
    drawPanel(chartL, chartT, chartW, chartH, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.5f), radius = 9f)
    drawSubtleGrid(chartL, chartT, chartW, chartH, rows = 4, cols = 3, alpha = 0.25f)

    drawLabel("9kHz", 32f, 52f, p.onSurfaceVariant, 7.5f, monospace = true)
    drawLabel("6kHz", 32f, 80f, p.onSurfaceVariant, 7.5f, monospace = true)
    drawLabel("3kHz", 32f, 112f, p.onSurfaceVariant, 7.5f, monospace = true)
    drawLabel("0Hz", 32f, 138f, p.onSurfaceVariant, 7.5f, monospace = true)

    // Column 1: Alveolar [s] (6-9 kHz high CoG)
    scope.drawRoundRect(p.primary.copy(alpha = 0.32f), topLeft = pt(78f, 46f), size = sz(64f, 36f), cornerRadius = cr(7f))
    scope.drawRoundRect(p.primary, topLeft = pt(78f, 46f), size = sz(64f, 36f), cornerRadius = cr(7f), style = Stroke(width = u(1.6f)))
    drawLabel("CoG ~7.5kHz", 110f, 64f, p.primary, 7.8f, FontWeight.ExtraBold)
    drawPill("平舌 [s / ts]", 110f, 160f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8f)

    // Column 2: Retroflex [ʂ] (2.5-4.5 kHz low CoG)
    scope.drawRoundRect(p.tertiary.copy(alpha = 0.32f), topLeft = pt(182f, 92f), size = sz(64f, 36f), cornerRadius = cr(7f))
    scope.drawRoundRect(p.tertiary, topLeft = pt(182f, 92f), size = sz(64f, 36f), cornerRadius = cr(7f), style = Stroke(width = u(1.6f)))
    drawLabel("CoG ~3.5kHz", 214f, 110f, p.tertiary, 7.8f, FontWeight.ExtraBold)
    drawPill("翘舌 [ʂ / tʂ]", 214f, 160f, p.tertiaryContainer, p.onTertiaryContainer, fontPx = 8f)

    // Column 3: Alveolo-palatal [ɕ] (4-6.5 kHz mid-high CoG + high F2 transition)
    scope.drawRoundRect(p.secondary.copy(alpha = 0.32f), topLeft = pt(286f, 68f), size = sz(64f, 38f), cornerRadius = cr(7f))
    scope.drawRoundRect(p.secondary, topLeft = pt(286f, 68f), size = sz(64f, 38f), cornerRadius = cr(7f), style = Stroke(width = u(1.6f)))
    drawLabel("CoG ~5.2kHz", 318f, 87f, p.secondary, 7.8f, FontWeight.ExtraBold)
    drawLabel("+ 元音 F2 高起", 318f, 120f, p.secondary, 7.3f)
    drawPill("舌面 [ɕ / tɕ]", 318f, 160f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 8f)
}

// -------------------------------------------------------------------------
// #20 语流协同发音与声韵切分边界原则
// -------------------------------------------------------------------------
private fun EncyclopediaCanvasHelper.draw28CoarticulationBoundaries() {
    drawPanel(12f, 12f, 376f, 166f, p.surfaceHigh.copy(alpha = 0.32f), p.outlineVariant, radius = 14f)
    drawLabel("宽带语图谱与 TextGrid 声韵切分边界标志", 200f, 26f, p.onSurface, 9.2f, FontWeight.ExtraBold)

    // Spectrogram box
    val sl = 26f
    val st = 38f
    val sw = 348f
    val sh = 84f
    drawPanel(sl, st, sw, sh, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.55f), radius = 9f)

    val b1 = 92f   // stop release burst
    val b2 = 148f  // onset of regular vowel glottal pulses & F1/F2
    val b3 = 276f  // transition into nasal coda [-n/-ŋ]

    // Stop burst vertical spike + aspiration noise between b1..b2
    scope.drawLine(p.tertiary, pt(b1, st + 6f), pt(b1, st + sh - 6f), strokeWidth = u(2.4f))
    for (nx in (b1.toInt() + 6)..(b2.toInt() - 4) step 7) {
        scope.drawLine(p.tertiary.copy(alpha = 0.48f), pt(nx.toFloat(), st + 12f), pt(nx.toFloat(), st + 54f), strokeWidth = u(2f), pathEffect = dashFine)
    }

    // Vowel F1 & F2 smooth coarticulatory curves between b2..b3
    val f2Path = Path().apply {
        moveTo(x(b2), y(st + 32f))
        quadraticTo(x(210f), y(st + 44f), x(b3), y(st + 38f))
    }
    val f1Path = Path().apply {
        moveTo(x(b2), y(st + 66f))
        quadraticTo(x(210f), y(st + 60f), x(b3), y(st + 68f))
    }
    scope.drawPath(f2Path, p.primary, style = Stroke(width = u(4.2f), cap = StrokeCap.Round))
    scope.drawPath(f1Path, p.primary, style = Stroke(width = u(4.5f), cap = StrokeCap.Round))

    // Nasal coda weak murmur low band after b3
    scope.drawLine(p.secondary, pt(b3 + 4f, st + 72f), pt(sl + sw - 16f, st + 72f), strokeWidth = u(4.5f), cap = StrokeCap.Round)
    drawLabel("共振峰骤减", 322f, st + 42f, p.secondary, 7.4f)

    // Segmentation vertical landmark lines extending into TextGrid tier
    listOf(b1 to p.tertiary, b2 to p.primary, b3 to p.secondary).forEach { (bx, col) ->
        scope.drawLine(col, pt(bx, st + 2f), pt(bx, 162f), strokeWidth = u(1.5f), pathEffect = dashFine)
    }

    // Bottom TextGrid IntervalTier
    drawPanel(sl, 130f, sw, 34f, p.primaryContainer.copy(alpha = 0.36f), p.primary.copy(alpha = 0.45f), radius = 8f)
    drawLabel("闭塞", 58f, 147f, p.onSurfaceVariant, 7.8f)
    drawLabel("送气 [pʰ]", 120f, 147f, p.tertiary, 8f, FontWeight.ExtraBold)
    drawLabel("主要元音 [a]（首个规则声门脉冲起）", 212f, 147f, p.primary, 7.6f, FontWeight.ExtraBold)
    drawLabel("鼻尾 [ŋ]", 324f, 147f, p.secondary, 8f, FontWeight.ExtraBold)
}

