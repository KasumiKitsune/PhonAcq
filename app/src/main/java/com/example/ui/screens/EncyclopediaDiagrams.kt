package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.StudioDialogEnterAnimation
import com.example.ui.components.rememberStudioInteraction
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Full Material You tonal palette for publication-grade dynamic-color encyclopedia diagrams.
 * Automatically adapts to Light Mode, Dark Mode, and Android 12+ wallpaper dynamic colors.
 */
@Immutable
data class EncyclopediaDynamicPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val surface: Color,
    val surfaceLowest: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color
) {
    companion object {
        fun fromColorScheme(cs: ColorScheme): EncyclopediaDynamicPalette {
            return EncyclopediaDynamicPalette(
                primary = cs.primary,
                onPrimary = cs.onPrimary,
                primaryContainer = cs.primaryContainer,
                onPrimaryContainer = cs.onPrimaryContainer,
                secondary = cs.secondary,
                secondaryContainer = cs.secondaryContainer,
                onSecondaryContainer = cs.onSecondaryContainer,
                tertiary = cs.tertiary,
                tertiaryContainer = cs.tertiaryContainer,
                onTertiaryContainer = cs.onTertiaryContainer,
                error = cs.error,
                errorContainer = cs.errorContainer,
                onErrorContainer = cs.onErrorContainer,
                surface = cs.surface,
                surfaceLowest = cs.surfaceContainerLowest,
                surfaceHigh = cs.surfaceContainerHigh,
                onSurface = cs.onSurface,
                onSurfaceVariant = cs.onSurfaceVariant,
                outline = cs.outline,
                outlineVariant = cs.outlineVariant
            )
        }

        fun mapSvgSemanticHex(hex: String, cs: ColorScheme): Color {
            return when (hex.trim().uppercase()) {
                "#6750A4" -> cs.primary
                "#EADDFF" -> cs.primaryContainer
                "#625B71" -> cs.secondary
                "#E8DEF8" -> cs.secondaryContainer
                "#7D5260" -> cs.tertiary
                "#B3261E" -> cs.error
                "#F9DEDC" -> cs.errorContainer
                "#1D1B20" -> cs.onSurface
                "#CAC4D0" -> cs.outlineVariant
                else -> cs.primary
            }
        }
    }
}

@Composable
internal fun EncyclopediaDynamicDiagramCard(
    diagramId: EncyclopediaDiagramId,
    caption: String?,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val palette = remember(cs) { EncyclopediaDynamicPalette.fromColorScheme(cs) }
    val measurer = rememberTextMeasurer()
    var showZoomLightbox by remember { mutableStateOf(false) }
    val (hoverSrc, isHoveredState) = rememberStudioInteraction()
    val isHovered by isHoveredState

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHovered) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            // Balanced textbook figure width (max 456.dp => ~216.dp height on Desktop) with click-to-zoom
            Box(
                modifier = Modifier
                    .widthIn(max = 456.dp)
                    .fillMaxWidth()
                    .aspectRatio(400f / 190f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceContainerLowest)
                    .border(0.8.dp, cs.outlineVariant.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                    .hoverable(hoverSrc)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = hoverSrc, indication = null) {
                        showZoomLightbox = true
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val helper = EncyclopediaCanvasHelper(this, palette, measurer)
                    helper.renderDiagram(diagramId)
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = cs.surfaceContainerHigh.copy(alpha = if (isHovered) 0.94f else 0.72f),
                    border = BorderStroke(0.8.dp, cs.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "放大查看图解",
                            tint = if (isHovered) cs.primary else cs.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        if (isHovered) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "放大",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.primary
                            )
                        }
                    }
                }
            }

            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }

    if (showZoomLightbox) {
        Dialog(
            onDismissRequest = { showZoomLightbox = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            StudioDialogEnterAnimation {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surface,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = caption ?: "百科声学与田野图解",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = CircleShape,
                                color = cs.surfaceContainerHigh,
                                modifier = Modifier.size(34.dp)
                            ) {
                                IconButton(onClick = { showZoomLightbox = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭大图",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(400f / 190f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cs.surfaceContainerLowest)
                                .border(1.dp, cs.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val zoomMeasurer = measurer
                                val helper = EncyclopediaCanvasHelper(this, palette, zoomMeasurer)
                                helper.renderDiagram(diagramId)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-precision responsive coordinate & vector illustration DSL operating on a 400 x 190 virtual canvas.
 */
internal class EncyclopediaCanvasHelper(
    val scope: DrawScope,
    val p: EncyclopediaDynamicPalette,
    val measurer: TextMeasurer
) {
    val sx: Float = scope.size.width / 400f
    val sy: Float = scope.size.height / 190f
    val s: Float = min(sx, sy)

    fun x(v: Float): Float = v * sx
    fun y(v: Float): Float = v * sy
    fun u(v: Float): Float = v * s
    fun pt(vx: Float, vy: Float): Offset = Offset(x(vx), y(vy))
    fun sz(vw: Float, vh: Float): Size = Size(x(vw), y(vh))
    fun cr(r: Float): CornerRadius = CornerRadius(u(r), u(r))

    val dashFine: PathEffect = PathEffect.dashPathEffect(floatArrayOf(u(4.5f), u(3.5f)), 0f)
    val dashWide: PathEffect = PathEffect.dashPathEffect(floatArrayOf(u(7f), u(4.5f)), 0f)

    fun drawPanel(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        fill: Color,
        border: Color? = null,
        radius: Float = 14f,
        borderWidth: Float = 1.1f
    ) {
        scope.drawRoundRect(
            color = fill,
            topLeft = pt(left, top),
            size = sz(width, height),
            cornerRadius = cr(radius)
        )
        if (border != null) {
            scope.drawRoundRect(
                color = border,
                topLeft = pt(left, top),
                size = sz(width, height),
                cornerRadius = cr(radius),
                style = Stroke(width = u(borderWidth))
            )
        }
    }

    fun drawSubtleGrid(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        rows: Int = 4,
        cols: Int = 5,
        alpha: Float = 0.28f
    ) {
        val gridColor = p.outlineVariant.copy(alpha = alpha)
        for (r in 1 until rows) {
            val gy = top + height * r / rows
            scope.drawLine(gridColor, pt(left, gy), pt(left + width, gy), strokeWidth = u(0.8f), pathEffect = dashFine)
        }
        for (c in 1 until cols) {
            val gx = left + width * c / cols
            scope.drawLine(gridColor, pt(gx, top), pt(gx, top + height), strokeWidth = u(0.8f), pathEffect = dashFine)
        }
    }

    fun drawLabel(
        text: String,
        cx: Float,
        cy: Float,
        color: Color,
        fontPx: Float = 9.5f,
        weight: FontWeight = FontWeight.Bold,
        monospace: Boolean = false
    ) {
        val spSize = with(scope) { (fontPx * s).toSp() }
        val res = measurer.measure(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = spSize,
                fontWeight = weight,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
            ),
            maxLines = 1,
            softWrap = false
        )
        scope.drawText(
            textLayoutResult = res,
            topLeft = Offset(x(cx) - res.size.width / 2f, y(cy) - res.size.height / 2f)
        )
    }

    fun drawPill(
        text: String,
        cx: Float,
        cy: Float,
        bg: Color,
        fg: Color,
        border: Color? = null,
        fontPx: Float = 9.2f,
        padH: Float = 7.5f,
        padV: Float = 3.2f,
        radius: Float = 99f,
        monospace: Boolean = false
    ) {
        val spSize = with(scope) { (fontPx * s).toSp() }
        val res = measurer.measure(
            text = text,
            style = TextStyle(
                color = fg,
                fontSize = spSize,
                fontWeight = FontWeight.Bold,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
            ),
            maxLines = 1,
            softWrap = false
        )
        val wPx = res.size.width + u(padH * 2f)
        val hPx = res.size.height + u(padV * 2f)
        val topLeft = Offset(x(cx) - wPx / 2f, y(cy) - hPx / 2f)
        val pillCr = CornerRadius(min(hPx / 2f, u(radius)), min(hPx / 2f, u(radius)))
        scope.drawRoundRect(color = bg, topLeft = topLeft, size = Size(wPx, hPx), cornerRadius = pillCr)
        if (border != null) {
            scope.drawRoundRect(
                color = border,
                topLeft = topLeft,
                size = Size(wPx, hPx),
                cornerRadius = pillCr,
                style = Stroke(width = u(1f))
            )
        }
        scope.drawText(
            textLayoutResult = res,
            topLeft = Offset(x(cx) - res.size.width / 2f, y(cy) - res.size.height / 2f)
        )
    }

    fun drawArrowHead(tipX: Float, tipY: Float, fromX: Float, fromY: Float, color: Color, headLen: Float = 8f) {
        val angle = atan2((y(tipY) - y(fromY)).toDouble(), (x(tipX) - x(fromX)).toDouble()).toFloat()
        val spread = 0.46f
        val lenPx = u(headLen)
        val tx = x(tipX)
        val ty = y(tipY)
        val path = Path().apply {
            moveTo(tx, ty)
            lineTo(tx - lenPx * cos(angle - spread), ty - lenPx * sin(angle - spread))
            lineTo(tx - lenPx * 0.74f * cos(angle), ty - lenPx * 0.74f * sin(angle))
            lineTo(tx - lenPx * cos(angle + spread), ty - lenPx * sin(angle + spread))
            close()
        }
        scope.drawPath(path, color)
    }

    fun drawStraightArrow(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Color,
        strokeW: Float = 2.1f,
        dashed: Boolean = false,
        headLen: Float = 8f
    ) {
        val sx1 = x(x1)
        val sy1 = y(y1)
        val tx = x(x2)
        val ty = y(y2)
        val dx = tx - sx1
        val dy = ty - sy1
        val dist = hypot(dx, dy)
        val lenPx = u(headLen)
        if (dist > 1f) {
            // Pull back shaft end so StrokeCap never protrudes past the sharp arrowhead tip
            val pullBack = min(lenPx * 0.68f, dist * 0.65f)
            val endX = tx - (dx / dist) * pullBack
            val endY = ty - (dy / dist) * pullBack
            if (!dashed) {
                scope.drawCircle(color, radius = u(strokeW) / 2f, center = Offset(sx1, sy1))
            }
            scope.drawLine(
                color = color,
                start = Offset(sx1, sy1),
                end = Offset(endX, endY),
                strokeWidth = u(strokeW),
                cap = StrokeCap.Butt,
                pathEffect = if (dashed) dashFine else null
            )
        }
        drawArrowHead(x2, y2, x1, y1, color, headLen)
    }

    fun drawQuadArrow(
        x1: Float,
        y1: Float,
        ctrlX: Float,
        ctrlY: Float,
        x2: Float,
        y2: Float,
        color: Color,
        strokeW: Float = 2.3f,
        headLen: Float = 8.2f
    ) {
        val sx1 = x(x1)
        val sy1 = y(y1)
        val cxPx = x(ctrlX)
        val cyPx = y(ctrlY)
        val tx = x(x2)
        val ty = y(y2)
        val tdx = tx - cxPx
        val tdy = ty - cyPx
        val tDist = hypot(tdx, tdy)
        val lenPx = u(headLen)
        val pullBack = if (tDist > 1f) min(lenPx * 0.66f, tDist * 0.6f) else 0f
        val endX = if (tDist > 1f) tx - (tdx / tDist) * pullBack else tx
        val endY = if (tDist > 1f) ty - (tdy / tDist) * pullBack else ty

        scope.drawCircle(color, radius = u(strokeW) / 2f, center = Offset(sx1, sy1))
        val path = Path().apply {
            moveTo(sx1, sy1)
            quadraticTo(cxPx, cyPx, endX, endY)
        }
        scope.drawPath(path, color, style = Stroke(width = u(strokeW), cap = StrokeCap.Butt))
        drawArrowHead(x2, y2, ctrlX, ctrlY, color, headLen)
    }

    fun drawAvatarBadge(cx: Float, cy: Float, r: Float, bg: Color, fg: Color) {
        scope.drawCircle(bg, radius = u(r), center = pt(cx, cy))
        scope.drawCircle(fg.copy(alpha = 0.25f), radius = u(r), center = pt(cx, cy), style = Stroke(width = u(1.1f)))
        // Head
        scope.drawCircle(fg, radius = u(r * 0.34f), center = pt(cx, cy - r * 0.2f))
        // Shoulders arc
        val shoulders = Path().apply {
            moveTo(x(cx - r * 0.56f), y(cy + r * 0.56f))
            quadraticTo(x(cx), y(cy + r * 0.08f), x(cx + r * 0.56f), y(cy + r * 0.56f))
        }
        scope.drawPath(shoulders, fg, style = Stroke(width = u(2.4f), cap = StrokeCap.Round))
    }

    fun drawStudioMicIcon(cx: Float, cy: Float, scale: Float, accent: Color, body: Color) {
        val mw = 13f * scale
        val mh = 21f * scale
        // Capsule grille
        scope.drawRoundRect(
            color = accent.copy(alpha = 0.22f),
            topLeft = pt(cx - mw / 2f, cy - mh * 0.72f),
            size = sz(mw, mh),
            cornerRadius = cr(mw / 2f)
        )
        scope.drawRoundRect(
            color = accent,
            topLeft = pt(cx - mw / 2f, cy - mh * 0.72f),
            size = sz(mw, mh),
            cornerRadius = cr(mw / 2f),
            style = Stroke(width = u(1.8f))
        )
        // Grille mesh ribs
        for (i in -1..1) {
            val ry = cy - mh * 0.32f + i * 3.6f * scale
            scope.drawLine(accent.copy(alpha = 0.65f), pt(cx - mw * 0.32f, ry), pt(cx + mw * 0.32f, ry), strokeWidth = u(1f))
        }
        // U-cradle & stand
        val cradleW = mw * 1.48f
        scope.drawArc(
            color = body,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = pt(cx - cradleW / 2f, cy - mh * 0.32f),
            size = sz(cradleW, mh * 0.78f),
            style = Stroke(width = u(1.8f), cap = StrokeCap.Round)
        )
        val stemTop = cy + mh * 0.46f
        val stemBot = cy + mh * 0.78f
        scope.drawLine(body, pt(cx, stemTop), pt(cx, stemBot), strokeWidth = u(1.8f), cap = StrokeCap.Round)
        scope.drawLine(body, pt(cx - mw * 0.55f, stemBot), pt(cx + mw * 0.55f, stemBot), strokeWidth = u(2f), cap = StrokeCap.Round)
    }

    fun drawEarbudIcon(cx: Float, cy: Float, scale: Float, color: Color) {
        // Wireless earbud head + stem + Bluetooth rune
        scope.drawOval(
            color = color.copy(alpha = 0.2f),
            topLeft = pt(cx - 9f * scale, cy - 11f * scale),
            size = sz(13f * scale, 10f * scale)
        )
        scope.drawOval(
            color = color,
            topLeft = pt(cx - 9f * scale, cy - 11f * scale),
            size = sz(13f * scale, 10f * scale),
            style = Stroke(width = u(1.7f))
        )
        scope.drawRoundRect(
            color = color,
            topLeft = pt(cx - 1.5f * scale, cy - 3f * scale),
            size = sz(4.2f * scale, 16f * scale),
            cornerRadius = cr(2f * scale)
        )
        // Mini Bluetooth rune on right
        val bx = cx + 9f * scale
        val by = cy
        val bt = Path().apply {
            moveTo(x(bx), y(by - 7f * scale))
            lineTo(x(bx), y(by + 7f * scale))
            lineTo(x(bx + 4.5f * scale), y(by + 3.5f * scale))
            lineTo(x(bx - 3.5f * scale), y(by - 3.5f * scale))
            moveTo(x(bx), y(by - 7f * scale))
            lineTo(x(bx + 4.5f * scale), y(by - 3.5f * scale))
            lineTo(x(bx - 3.5f * scale), y(by + 3.5f * scale))
        }
        scope.drawPath(bt, color, style = Stroke(width = u(1.3f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun drawFileSheet(
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        accent: Color,
        fill: Color,
        extLabel: String
    ) {
        val fold = min(w, h) * 0.24f
        val sheet = Path().apply {
            moveTo(x(left), y(top))
            lineTo(x(left + w - fold), y(top))
            lineTo(x(left + w), y(top + fold))
            lineTo(x(left + w), y(top + h))
            lineTo(x(left), y(top + h))
            close()
        }
        scope.drawPath(sheet, fill)
        scope.drawPath(sheet, accent.copy(alpha = 0.75f), style = Stroke(width = u(1.3f), join = StrokeJoin.Round))
        val dogEar = Path().apply {
            moveTo(x(left + w - fold), y(top))
            lineTo(x(left + w - fold), y(top + fold))
            lineTo(x(left + w), y(top + fold))
        }
        scope.drawPath(dogEar, accent.copy(alpha = 0.75f), style = Stroke(width = u(1.2f), join = StrokeJoin.Round))
        drawPill(
            text = extLabel,
            cx = left + w / 2f,
            cy = top + h * 0.66f,
            bg = accent.copy(alpha = 0.16f),
            fg = accent,
            fontPx = 8.2f,
            padH = 4.5f,
            padV = 1.8f,
            monospace = true
        )
    }

    fun renderDiagram(id: EncyclopediaDiagramId) {
        when (id) {
            // ================= VOL 1: FIELDWORK (1-6) =================
            EncyclopediaDiagramId.ENC_01_SPEAKER_COHORT_COMPARE -> draw01SpeakerCohort()
            EncyclopediaDiagramId.ENC_01_SING_SONG_SHUFFLE -> draw02SingSongShuffle()
            EncyclopediaDiagramId.ENC_02_CARRIER_SENTENCE_FRAME -> draw03CarrierSentence()
            EncyclopediaDiagramId.ENC_03_WEN_BAI_LAYERS -> draw04WenBaiLayers()
            EncyclopediaDiagramId.ENC_04_TONE_SANDHI_CHAIN -> draw05ToneSandhiChain()
            EncyclopediaDiagramId.ENC_06_CLEAN_PROMPT_CARD -> draw06CleanPromptCard()

            // ================= VOL 2: PHONETICS (7-11) =================
            EncyclopediaDiagramId.ENC_05_CHAO_FIVE_PITCH_SCALE -> draw07ChaoFivePitchScale()
            EncyclopediaDiagramId.ENC_06_CHECKED_VS_SMOOTH_TONE -> draw08CheckedVsSmooth()
            EncyclopediaDiagramId.ENC_07_VOWEL_MONOPHTHONG_GLIDE -> draw09VowelMonophthongGlide()
            EncyclopediaDiagramId.ENC_08_VOICING_AND_ASPIRATION -> draw10VoicingAspirationVot()
            EncyclopediaDiagramId.ENC_11_IPA_NARROW_BROAD -> draw11IpaNarrowBroad()

            // ================= VOL 3, 4, 5: DELEGATED TO PART 2 =================
            else -> renderDiagramPart2(id)
        }
    }

    // -------------------------------------------------------------------------
    // #1 发音合作人的遴选标准（老派与新派代际对照）
    // -------------------------------------------------------------------------
    private fun draw01SpeakerCohort() {
        drawPanel(12f, 12f, 168f, 134f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.35f))
        drawPanel(220f, 12f, 168f, 134f, p.secondaryContainer.copy(alpha = 0.55f), p.tertiary.copy(alpha = 0.38f))

        // Left: Conservative Older Speaker (老派)
        drawAvatarBadge(40f, 38f, 15f, p.primaryContainer, p.primary)
        drawLabel("老派合作人", 108f, 31f, p.onSurface, 10.2f, FontWeight.ExtraBold)
        drawLabel("60+ 岁 · 世居本地", 108f, 46f, p.onSurfaceVariant, 8.6f, FontWeight.Medium)

        // Three preserved phonological distinctions with crisp downward mapping arrows
        val oldCats = listOf("精组 [ts]" to 40f, "庄章 [tʂ]" to 96f, "见组 [tɕ]" to 152f)
        oldCats.forEach { (lbl, cx) ->
            drawPill(lbl, cx, 74f, p.surface, p.primary, p.primary.copy(alpha = 0.4f), fontPx = 8.1f, padH = 4.5f, padV = 2.2f)
            drawStraightArrow(cx, 85f, cx, 106f, p.primary, strokeW = 1.8f, headLen = 5.8f)
            scope.drawCircle(p.primary, radius = u(4f), center = pt(cx, 113f))
        }
        drawLabel("音韵格局完整保持三分", 96f, 132f, p.primary, 8.8f)

        // Center diachronic bridge
        drawStraightArrow(183f, 75f, 217f, 75f, p.tertiary, strokeW = 2.2f, headLen = 7.8f)
        drawLabel("代际", 200f, 58f, p.tertiary, 8.5f)
        drawLabel("演变", 200f, 92f, p.tertiary, 8.5f)

        // Right: Innovative Younger Speaker (新派)
        drawAvatarBadge(248f, 38f, 15f, p.tertiaryContainer, p.tertiary)
        drawLabel("新派合作人", 316f, 31f, p.onSurface, 10.2f, FontWeight.ExtraBold)
        drawLabel("20~30 岁 · 青年层", 316f, 46f, p.onSurfaceVariant, 8.6f, FontWeight.Medium)

        // Merger of two categories into one + preserved third with crisp converging arrows
        drawPill("精 [ts]", 254f, 74f, p.surface, p.secondary, p.outlineVariant, fontPx = 8.1f, padH = 5f, padV = 2.2f)
        drawPill("庄章 [ts]", 310f, 74f, p.surface, p.secondary, p.outlineVariant, fontPx = 8.1f, padH = 5f, padV = 2.2f)
        drawPill("见 [tɕ]", 362f, 74f, p.surface, p.tertiary, p.tertiary.copy(alpha = 0.4f), fontPx = 8.1f, padH = 5f, padV = 2.2f)

        drawStraightArrow(255f, 85f, 277f, 106f, p.tertiary, strokeW = 1.9f, headLen = 5.8f)
        drawStraightArrow(309f, 85f, 287f, 106f, p.tertiary, strokeW = 1.9f, headLen = 5.8f)
        scope.drawCircle(p.tertiary, radius = u(4.5f), center = pt(282f, 113f))

        drawStraightArrow(362f, 85f, 362f, 106f, p.tertiary, strokeW = 1.8f, headLen = 5.8f)
        scope.drawCircle(p.tertiary, radius = u(4f), center = pt(362f, 113f))
        drawLabel("平翘合流 · 反映进行中音变", 304f, 132f, p.tertiary, 8.8f)

        // Bottom universal screening criteria strip
        drawPanel(12f, 154f, 376f, 26f, p.surfaceHigh.copy(alpha = 0.55f), p.outlineVariant.copy(alpha = 0.45f), radius = 9f)
        drawLabel("遴选基准： ✓ 父母及本人世居当地    ✓ 无长期外居史    ✓ 齿列完整无擦音漏风", 200f, 167f, p.onSurface, 8.8f, FontWeight.SemiBold)
    }

    // -------------------------------------------------------------------------
    // #2 列表语调（“唱书调”）与词表乱序
    // -------------------------------------------------------------------------
    private fun draw02SingSongShuffle() {
        drawPanel(12f, 12f, 170f, 166f, p.errorContainer.copy(alpha = 0.36f), p.error.copy(alpha = 0.35f))
        drawPanel(218f, 12f, 170f, 166f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.38f))

        // Left: Same-tone sequential list ("巴 波 衣 乌" all 55)
        val sameWords = listOf("巴⁵⁵", "波⁵⁵", "衣⁵⁵", "乌⁵⁵")
        sameWords.forEachIndexed { i, w ->
            val cx = 38f + i * 39f
            drawPanel(cx - 16f, 24f, 32f, 24f, p.surface, p.error.copy(alpha = 0.35f), radius = 6f)
            drawLabel(w, cx, 36f, p.onSurface, 8.6f)
        }

        // Pitch box left
        drawPanel(22f, 58f, 150f, 82f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.45f), radius = 9f)
        drawSubtleGrid(22f, 58f, 150f, 82f, rows = 4, cols = 4)
        val chantWave = Path().apply {
            moveTo(x(28f), y(98f))
            for (i in 0..3) {
                val bx = 28f + i * 35f
                cubicTo(x(bx + 11f), y(68f), x(bx + 23f), y(128f), x(bx + 35f), y(98f))
            }
        }
        scope.drawPath(chantWave, p.error, style = Stroke(width = u(2.6f), cap = StrokeCap.Round))
        drawPill("✕ 同调顺读：吟诵唱书调", 97f, 158f, p.errorContainer, p.error, fontPx = 8.6f)

        // Center shuffle badge with crisp S-curve crossing shuffle arrows
        scope.drawCircle(p.secondaryContainer, radius = u(15.5f), center = pt(200f, 95f))
        scope.drawCircle(p.primary.copy(alpha = 0.35f), radius = u(15.5f), center = pt(200f, 95f), style = Stroke(width = u(1f)))
        val shuf1 = Path().apply {
            moveTo(x(191f), y(90f))
            cubicTo(x(198f), y(90f), x(201f), y(100f), x(206f), y(100f))
        }
        val shuf2 = Path().apply {
            moveTo(x(191f), y(100f))
            cubicTo(x(198f), y(100f), x(201f), y(90f), x(206f), y(90f))
        }
        scope.drawPath(shuf1, p.primary, style = Stroke(width = u(1.7f), cap = StrokeCap.Round))
        drawArrowHead(210f, 100f, 204f, 100f, p.primary, headLen = 5.2f)
        scope.drawPath(shuf2, p.tertiary, style = Stroke(width = u(1.7f), cap = StrokeCap.Round))
        drawArrowHead(210f, 90f, 204f, 90f, p.tertiary, headLen = 5.2f)
        drawLabel("乱序", 200f, 121f, p.primary, 8.4f)

        // Right: Interleaved tones ("巴⁵⁵ 麻³⁵ 马²¹⁴ 骂⁵¹")
        val mixedWords = listOf("巴⁵⁵", "麻³⁵", "马²¹⁴", "骂⁵¹")
        mixedWords.forEachIndexed { i, w ->
            val cx = 244f + i * 39f
            drawPanel(cx - 17f, 24f, 34f, 24f, p.surface, p.primary.copy(alpha = 0.4f), radius = 6f)
            drawLabel(w, cx, 36f, p.primary, 8.4f)
        }

        // Pitch box right showing authentic citation contours
        drawPanel(228f, 58f, 150f, 82f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.45f), radius = 9f)
        drawSubtleGrid(228f, 58f, 150f, 82f, rows = 4, cols = 4)
        // 55 level
        scope.drawLine(p.primary, pt(234f, 70f), pt(258f, 70f), strokeWidth = u(2.8f), cap = StrokeCap.Round)
        // 35 rising
        scope.drawLine(p.tertiary, pt(271f, 98f), pt(295f, 70f), strokeWidth = u(2.8f), cap = StrokeCap.Round)
        // 214 dipping
        val dip = Path().apply {
            moveTo(x(308f), y(108f))
            quadraticTo(x(318f), y(132f), x(333f), y(80f))
        }
        scope.drawPath(dip, p.primary, style = Stroke(width = u(2.8f), cap = StrokeCap.Round))
        // 51 falling
        scope.drawLine(p.tertiary, pt(346f, 70f), pt(370f, 128f), strokeWidth = u(2.8f), cap = StrokeCap.Round)
        drawPill("✓ 随机穿插：还原自然本调", 303f, 158f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8.6f)
    }

    // -------------------------------------------------------------------------
    // #3 载体句（框架句）在声学实验中的作用
    // -------------------------------------------------------------------------
    private fun draw03CarrierSentence() {
        drawPanel(12f, 12f, 174f, 166f, p.errorContainer.copy(alpha = 0.32f), p.error.copy(alpha = 0.32f))
        drawPanel(202f, 12f, 186f, 166f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.38f))

        // Left: Isolated citation word
        drawPill("孤立单念：「 巴 」", 99f, 32f, p.surface, p.onSurface, p.error.copy(alpha = 0.4f), fontPx = 9.2f)
        drawPanel(24f, 52f, 150f, 90f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.45f), radius = 9f)
        drawSubtleGrid(24f, 52f, 150f, 90f, rows = 4, cols = 4)
        // Target 55 reference dashed line
        scope.drawLine(p.outline, pt(32f, 76f), pt(166f, 76f), strokeWidth = u(1.1f), pathEffect = dashFine)
        // Declination sagging contour
        val sagCurve = Path().apply {
            moveTo(x(42f), y(64f))
            cubicTo(x(76f), y(72f), x(116f), y(90f), x(154f), y(124f))
        }
        scope.drawPath(sagCurve, p.error, style = Stroke(width = u(3f), cap = StrokeCap.Round))
        drawLabel("起音偏高", 58f, 59f, p.error, 7.8f)
        drawLabel("句末下倾", 142f, 132f, p.error, 7.8f)
        drawPill("✕ 首尾气流与句调干扰", 99f, 159f, p.errorContainer, p.error, fontPx = 8.6f)

        // Right: Carrier sentence frame ("我说 [ 巴 ] 这个词")
        drawPill("我说", 236f, 32f, p.surface, p.secondary, p.outlineVariant, fontPx = 8.8f, padH = 6f)
        drawPill("巴", 286f, 32f, p.primary, p.onPrimary, fontPx = 9.8f, padH = 10f)
        drawPill("这个词", 344f, 32f, p.surface, p.secondary, p.outlineVariant, fontPx = 8.8f, padH = 6f)

        drawPanel(214f, 52f, 162f, 90f, p.surfaceLowest, p.outlineVariant.copy(alpha = 0.45f), radius = 9f)
        // Highlighted target slot inside plot
        drawPanel(264f, 58f, 46f, 78f, p.primaryContainer.copy(alpha = 0.6f), p.primary.copy(alpha = 0.5f), radius = 6f)
        scope.drawLine(p.outlineVariant, pt(222f, 82f), pt(368f, 82f), strokeWidth = u(1.1f), pathEffect = dashFine)

        // Preceding carrier F0
        val preF0 = Path().apply {
            moveTo(x(224f), y(104f))
            quadraticTo(x(240f), y(114f), x(258f), y(90f))
        }
        scope.drawPath(preF0, p.secondary.copy(alpha = 0.65f), style = Stroke(width = u(2.2f), cap = StrokeCap.Round))

        // Steady target syllable 55 F0
        scope.drawLine(p.primary, pt(269f, 82f), pt(305f, 82f), strokeWidth = u(3.4f), cap = StrokeCap.Round)
        drawLabel("55 稳态", 287f, 70f, p.primary, 8.2f)

        // Following carrier F0 absorbs the final declination!
        val postF0 = Path().apply {
            moveTo(x(316f), y(90f))
            cubicTo(x(334f), y(98f), x(350f), y(112f), x(366f), y(126f))
        }
        scope.drawPath(postF0, p.secondary.copy(alpha = 0.65f), style = Stroke(width = u(2.2f), cap = StrokeCap.Round))
        drawPill("✓ 衬字托举 · 稳住音高基线", 295f, 159f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8.6f)
    }

    // -------------------------------------------------------------------------
    // #4 文白异读与历史语音层次
    // -------------------------------------------------------------------------
    private fun draw04WenBaiLayers() {
        // Top etymon node
        drawPanel(134f, 12f, 132f, 38f, p.secondaryContainer, p.primary.copy(alpha = 0.45f), radius = 12f)
        drawLabel("同一语素汉字「 血 / 学 」", 200f, 31f, p.onSecondaryContainer, 9.8f, FontWeight.ExtraBold)

        // Smooth diverging Bezier branches
        drawQuadArrow(168f, 50f, 132f, 68f, 102f, 82f, p.primary, strokeW = 2.4f, headLen = 7f)
        drawQuadArrow(232f, 50f, 268f, 68f, 298f, 82f, p.tertiary, strokeW = 2.4f, headLen = 7f)

        // Left Card: Colloquial Stratum (白读)
        drawPanel(16f, 86f, 172f, 92f, p.primaryContainer.copy(alpha = 0.52f), p.primary.copy(alpha = 0.42f), radius = 14f)
        // Speech bubble icon
        drawPanel(28f, 98f, 26f, 20f, p.primary, radius = 6f)
        drawLabel("口", 41f, 108f, p.onPrimary, 8.8f)
        drawLabel("白读 · 口语底层音", 114f, 104f, p.primary, 10f, FontWeight.ExtraBold)
        drawLabel("早期本土历史层次传承", 114f, 118f, p.onSurfaceVariant, 8f, FontWeight.Medium)
        drawPill("例词：流血 · 大人 · 飞鸟", 102f, 142f, p.surface, p.onSurface, p.primary.copy(alpha = 0.3f), fontPx = 8.5f)
        drawLabel("日常核心高频词汇", 102f, 165f, p.primary, 8.2f)

        // Right Card: Literary Stratum (文读)
        drawPanel(212f, 86f, 172f, 92f, p.tertiaryContainer.copy(alpha = 0.52f), p.tertiary.copy(alpha = 0.42f), radius = 14f)
        // Book badge icon
        drawPanel(224f, 98f, 26f, 20f, p.tertiary, radius = 6f)
        drawLabel("书", 237f, 108f, p.onTertiaryContainer.let { p.surfaceLowest }, 8.8f)
        drawLabel("文读 · 书面读书音", 310f, 104f, p.tertiary, 10f, FontWeight.ExtraBold)
        drawLabel("权威官话或诵读传统借入", 310f, 118f, p.onSurfaceVariant, 8f, FontWeight.Medium)
        drawPill("例词：心血 · 大学 · 益鸟", 298f, 142f, p.surface, p.onSurface, p.tertiary.copy(alpha = 0.3f), fontPx = 8.5f)
        drawLabel("书面合成词、成语与新词", 298f, 165f, p.tertiary, 8.2f)
    }

    // -------------------------------------------------------------------------
    // #5 单字调与连读变调
    // -------------------------------------------------------------------------
    private fun draw05ToneSandhiChain() {
        // Upper: Two separate citation tones
        drawPanel(26f, 12f, 152f, 66f, p.secondaryContainer.copy(alpha = 0.45f), p.outlineVariant, radius = 12f)
        drawPanel(222f, 12f, 152f, 66f, p.secondaryContainer.copy(alpha = 0.45f), p.outlineVariant, radius = 12f)
        scope.drawCircle(p.secondaryContainer, radius = u(10f), center = pt(200f, 45f))
        scope.drawLine(p.primary, pt(195f, 45f), pt(205f, 45f), strokeWidth = u(2f), cap = StrokeCap.Round)
        scope.drawLine(p.primary, pt(200f, 40f), pt(200f, 50f), strokeWidth = u(2f), cap = StrokeCap.Round)

        drawPill("单字本调：你 [214]", 102f, 26f, p.surface, p.onSurface, fontPx = 8.6f)
        drawPill("单字本调：好 [214]", 298f, 26f, p.surface, p.onSurface, fontPx = 8.6f)

        val dipLeft = Path().apply {
            moveTo(x(54f), y(48f))
            quadraticTo(x(92f), y(74f), x(148f), y(40f))
        }
        val dipRight = Path().apply {
            moveTo(x(250f), y(48f))
            quadraticTo(x(288f), y(74f), x(344f), y(40f))
        }
        scope.drawPath(dipLeft, p.secondary, style = Stroke(width = u(2.8f), cap = StrokeCap.Round))
        scope.drawPath(dipRight, p.secondary, style = Stroke(width = u(2.8f), cap = StrokeCap.Round))

        // Center downward derivation arrow
        drawStraightArrow(200f, 80f, 200f, 100f, p.primary, strokeW = 2.2f, headLen = 6.5f)
        drawPill("连读组配成词", 254f, 90f, p.primaryContainer.copy(alpha = 0.7f), p.primary, fontPx = 8f, padH = 6f, padV = 1.8f)

        // Lower: Connected prosodic word domain
        drawPanel(26f, 104f, 348f, 74f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.45f), radius = 14f)
        drawSubtleGrid(40f, 114f, 320f, 42f, rows = 3, cols = 2)
        scope.drawLine(p.outlineVariant, pt(200f, 112f), pt(200f, 158f), strokeWidth = u(1.2f), pathEffect = dashFine)

        // Sandhi rising 35 on first syllable (Tertiary highlight)
        val sandhiRise = Path().apply {
            moveTo(x(62f), y(146f))
            quadraticTo(x(118f), y(136f), x(176f), y(116f))
        }
        scope.drawPath(sandhiRise, p.tertiary, style = Stroke(width = u(3.3f), cap = StrokeCap.Round))
        scope.drawCircle(p.tertiary, radius = u(3.5f), center = pt(62f, 146f))
        scope.drawCircle(p.tertiary, radius = u(3.5f), center = pt(176f, 116f))

        // Base 214 on second syllable
        val secondDip = Path().apply {
            moveTo(x(222f), y(130f))
            quadraticTo(x(268f), y(154f), x(334f), y(118f))
        }
        scope.drawPath(secondDip, p.primary, style = Stroke(width = u(3.1f), cap = StrokeCap.Round))

        drawPill("前字变调：你 [35]", 120f, 165f, p.tertiaryContainer, p.tertiary, fontPx = 8.4f)
        drawPill("后字本调：好 [214]", 280f, 165f, p.primaryContainer, p.primary, fontPx = 8.4f)
    }

    // -------------------------------------------------------------------------
    // #6 看读提示干扰与大字无备注界面
    // -------------------------------------------------------------------------
    private fun draw06CleanPromptCard() {
        drawPanel(12f, 12f, 168f, 166f, p.errorContainer.copy(alpha = 0.32f), p.error.copy(alpha = 0.35f))
        drawPanel(220f, 12f, 168f, 166f, p.primaryContainer.copy(alpha = 0.42f), p.primary.copy(alpha = 0.4f))

        // Left: Cluttered screen with phonetic & Mandarin hints causing interference
        drawPanel(26f, 24f, 140f, 116f, p.surface, p.outlineVariant, radius = 11f)
        drawPill("遇合三御澄 · [tʂu⁵¹]", 96f, 39f, p.errorContainer.copy(alpha = 0.7f), p.error, fontPx = 7.8f, padH = 5f, padV = 1.8f)
        drawLabel("箸", 96f, 76f, p.onSurface, 22f, FontWeight.ExtraBold)
        drawPill("普通话释义：筷子", 96f, 108f, p.errorContainer, p.error, fontPx = 8.2f, padH = 6f, padV = 2f)
        drawLabel("↑ 易诱导折算成普通话音", 96f, 128f, p.error, 7.8f)
        drawPill("✕ 学术注记与释义视觉诱导", 96f, 159f, p.errorContainer, p.error, fontPx = 8.4f)

        // Center toggle arrow
        drawStraightArrow(184f, 92f, 216f, 92f, p.primary, strokeW = 2.2f, headLen = 6.5f)
        drawPill("大字看读", 200f, 74f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 7.8f, padH = 5f, padV = 1.8f)

        // Right: Clean distraction-free large character display
        drawPanel(234f, 24f, 140f, 116f, p.surface, p.primary.copy(alpha = 0.45f), radius = 11f, borderWidth = 1.4f)
        // Minimal progress dot in corner
        drawPill("12 / 120", 346f, 36f, p.surfaceHigh, p.onSurfaceVariant, fontPx = 7.2f, padH = 4.5f, padV = 1.2f)
        drawLabel("箸", 304f, 84f, p.primary, 31f, FontWeight.ExtraBold)
        drawLabel("界面纯净无剧透", 304f, 124f, p.onSurfaceVariant, 8f, FontWeight.Medium)
        drawPill("✓ 直觉读出地道方言母语", 304f, 159f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8.4f)
    }

    // -------------------------------------------------------------------------
    // #7 五度标调法与个体相对音域
    // -------------------------------------------------------------------------
    private fun draw07ChaoFivePitchScale() {
        // Left vertical absolute Hz ruler
        scope.drawLine(p.outline, pt(34f, 18f), pt(34f, 168f), strokeWidth = u(1.5f))
        val hzMarks = listOf("340Hz" to 24f, "240Hz" to 64f, "160Hz" to 108f, "90Hz" to 158f)
        hzMarks.forEach { (lbl, hy) ->
            scope.drawLine(p.outline, pt(30f, hy), pt(35f, hy), strokeWidth = u(1.3f))
            drawLabel(lbl, 17f, hy, p.onSurfaceVariant, 7.2f, FontWeight.SemiBold)
        }

        // Male low pitch range panel (90Hz - 175Hz -> y: 84..160)
        drawPanel(52f, 78f, 122f, 90f, p.secondaryContainer.copy(alpha = 0.48f), p.secondary.copy(alpha = 0.4f), radius = 11f)
        for (deg in 1..5) {
            val ly = 158f - (deg - 1) * 17.5f
            scope.drawLine(p.outlineVariant.copy(alpha = 0.6f), pt(68f, ly), pt(164f, ly), strokeWidth = u(1f), pathEffect = dashFine)
            drawLabel("$deg", 60f, ly, p.secondary, 7.8f)
        }
        // Male 55 line at his top degree 5 (y = 88f)
        scope.drawLine(p.primary, pt(76f, 88f), pt(156f, 88f), strokeWidth = u(3.4f), cap = StrokeCap.Round)
        drawPill("男低音调域 (85~170Hz)", 113f, 64f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 8f)

        // Female high pitch range panel (170Hz - 330Hz -> y: 20..96)
        drawPanel(258f, 16f, 126f, 90f, p.tertiaryContainer.copy(alpha = 0.48f), p.tertiary.copy(alpha = 0.4f), radius = 11f)
        for (deg in 1..5) {
            val ry = 96f - (deg - 1) * 17.5f
            scope.drawLine(p.outlineVariant.copy(alpha = 0.6f), pt(274f, ry), pt(374f, ry), strokeWidth = u(1f), pathEffect = dashFine)
            drawLabel("$deg", 266f, ry, p.tertiary, 7.8f)
        }
        // Female 55 line at her top degree 5 (y = 26f)
        scope.drawLine(p.primary, pt(286f, 26f), pt(366f, 26f), strokeWidth = u(3.4f), cap = StrokeCap.Round)
        drawPill("女高音调域 (170~340Hz)", 321f, 120f, p.tertiaryContainer, p.onTertiaryContainer, fontPx = 8f)

        // Center unifying callout linking both 5-level bars cleanly without crossing degree numbers
        drawQuadArrow(160f, 88f, 192f, 88f, 212f, 77f, p.primary, strokeW = 2f, headLen = 6f)
        drawQuadArrow(284f, 30f, 250f, 46f, 226f, 51f, p.primary, strokeW = 2f, headLen = 6f)
        drawPill("同标高平调 55", 216f, 64f, p.primary, p.onPrimary, fontPx = 9.2f, padH = 8.5f, padV = 3.5f)
        drawPill("个体相对比例尺 · 消除绝对音高差异", 268f, 158f, p.primaryContainer, p.onPrimaryContainer, fontPx = 8.5f)
    }

    // -------------------------------------------------------------------------
    // #8 舒声与促声（入声塞尾与喉塞尾）
    // -------------------------------------------------------------------------
    private fun draw08CheckedVsSmooth() {
        // Upper track: Smooth / Open or Nasal Coda (舒声)
        drawPanel(16f, 14f, 368f, 68f, p.secondaryContainer.copy(alpha = 0.42f), p.secondary.copy(alpha = 0.35f), radius = 12f)
        drawPill("舒声韵（开音节 / 鼻尾 -m -n -ŋ）", 108f, 27f, p.surface, p.secondary, fontPx = 8f)
        val midY1 = 56f
        scope.drawLine(p.outlineVariant, pt(28f, midY1), pt(368f, midY1), strokeWidth = u(0.9f))
        for (i in 0..42) {
            val wx = 34f + i * 5.6f
            val env = sin((i / 42f) * PI).toFloat() * (1f - i / 54f)
            val amp = (16f * env).coerceAtLeast(1.2f)
            scope.drawLine(p.secondary, pt(wx, midY1 - amp), pt(wx, midY1 + amp), strokeWidth = u(2.3f), cap = StrokeCap.Round)
        }
        drawPill("时长舒展 (~380 ms) · 自然衰减", 308f, 27f, p.secondaryContainer, p.onSecondaryContainer, fontPx = 7.9f)

        // Lower track: Checked / Entering Tone (促声 / 入声)
        drawPanel(16f, 92f, 368f, 68f, p.primaryContainer.copy(alpha = 0.45f), p.primary.copy(alpha = 0.42f), radius = 12f)
        drawPill("促声/入声韵（唯闭塞尾 -p -t -k / -ʔ）", 114f, 105f, p.surface, p.primary, fontPx = 8f)
        val midY2 = 135f
        scope.drawLine(p.outlineVariant, pt(28f, midY2), pt(368f, midY2), strokeWidth = u(0.9f))
        for (i in 0..14) {
            val wx = 34f + i * 5.6f
            val amp = if (i < 2) 9f + i * 4f else 16f
            scope.drawLine(p.primary, pt(wx, midY2 - amp), pt(wx, midY2 + amp), strokeWidth = u(2.6f), cap = StrokeCap.Round)
        }
        // Abrupt oral/glottal closure stop wall!
        val stopX = 122f
        scope.drawLine(p.error, pt(stopX, 116f), pt(stopX, 154f), strokeWidth = u(3f), cap = StrokeCap.Round)
        drawPill("唯闭截断 [-p/-t/-k/-ʔ]", 198f, 135f, p.errorContainer, p.error, fontPx = 8.2f)
        drawPill("短促立停 (~120 ms)", 318f, 105f, p.primaryContainer, p.primary, fontPx = 7.9f)

        // Shared bottom millisecond ruler
        scope.drawLine(p.outline, pt(34f, 172f), pt(354f, 172f), strokeWidth = u(1.2f))
        listOf("0 ms" to 34f, "120 ms" to 122f, "250 ms" to 216f, "400 ms" to 346f).forEach { (t, tx) ->
            scope.drawLine(p.outline, pt(tx, 169f), pt(tx, 175f), strokeWidth = u(1.2f))
            drawLabel(t, tx, 182f, p.onSurfaceVariant, 7.6f, monospace = true)
        }
    }

    // -------------------------------------------------------------------------
    // #9 单元音稳态与复元音动程
    // -------------------------------------------------------------------------
    private fun draw09VowelMonophthongGlide() {
        // Acoustic Vowel Trapezoid on Left-Center
        val vTopL = pt(42f, 28f)
        val vTopR = pt(222f, 28f)
        val vBotR = pt(196f, 162f)
        val vBotL = pt(104f, 162f)

        val trap = Path().apply {
            moveTo(vTopL.x, vTopL.y)
            lineTo(vTopR.x, vTopR.y)
            lineTo(vBotR.x, vBotR.y)
            lineTo(vBotL.x, vBotL.y)
            close()
        }
        scope.drawPath(trap, p.secondaryContainer.copy(alpha = 0.35f))
        // Internal grid lines
        scope.drawLine(p.outlineVariant.copy(alpha = 0.5f), pt(63f, 72f), pt(213f, 72f), strokeWidth = u(1f), pathEffect = dashFine)
        scope.drawLine(p.outlineVariant.copy(alpha = 0.5f), pt(84f, 117f), pt(204f, 117f), strokeWidth = u(1f), pathEffect = dashFine)
        scope.drawLine(p.outlineVariant.copy(alpha = 0.5f), pt(132f, 28f), pt(150f, 162f), strokeWidth = u(1f), pathEffect = dashFine)
        scope.drawPath(trap, p.onSurface.copy(alpha = 0.7f), style = Stroke(width = u(1.8f), join = StrokeJoin.Round))

        drawStraightArrow(208f, 15f, 58f, 15f, p.onSurfaceVariant, strokeW = 1.5f, headLen = 5.8f)
        drawPill("F2 (舌位前伸)", 136f, 15f, p.surfaceLowest, p.onSurfaceVariant, fontPx = 7.8f, padH = 5f, padV = 1f)
        drawStraightArrow(24f, 42f, 24f, 142f, p.onSurfaceVariant, strokeW = 1.5f, headLen = 5.8f)
        drawPill("F1", 24f, 88f, p.surfaceLowest, p.onSurfaceVariant, fontPx = 7.8f, padH = 3.5f, padV = 1f)

        // Monophthong [u] with 1-sigma ellipse + steady centroid (label placed cleanly inside left of ellipse)
        scope.drawOval(p.primary.copy(alpha = 0.2f), topLeft = pt(178f, 38f), size = sz(32f, 24f))
        scope.drawOval(p.primary, topLeft = pt(178f, 38f), size = sz(32f, 24f), style = Stroke(width = u(1.3f), pathEffect = dashFine))
        scope.drawCircle(p.primary, radius = u(5f), center = pt(194f, 50f))
        drawLabel("[u]", 166f, 50f, p.primary, 9.5f, FontWeight.ExtraBold)

        // Monophthong [i] steady target (label placed cleanly inside right of ellipse)
        scope.drawOval(p.primary.copy(alpha = 0.2f), topLeft = pt(52f, 38f), size = sz(28f, 22f))
        scope.drawCircle(p.primary, radius = u(4.5f), center = pt(66f, 49f))
        drawLabel("[i]", 90f, 49f, p.primary, 9.5f, FontWeight.ExtraBold)

        // Diphthong [ai]: open start ring at [a] -> smooth curved trajectory -> arrowhead toward [i]
        scope.drawCircle(p.surfaceLowest, radius = u(5f), center = pt(148f, 146f))
        scope.drawCircle(p.tertiary, radius = u(5f), center = pt(148f, 146f), style = Stroke(width = u(2.2f)))
        drawQuadArrow(148f, 146f, 102f, 114f, 75f, 62f, p.tertiary, strokeW = 2.8f, headLen = 8.4f)
        drawPill("[ai] 动程", 118f, 105f, p.tertiaryContainer, p.tertiary, fontPx = 8.4f, padH = 5.5f, padV = 1.8f)
        drawLabel("[a]", 164f, 148f, p.tertiary, 9.5f, FontWeight.ExtraBold)

        // Right Legend Cards
        drawPanel(240f, 34f, 146f, 54f, p.primaryContainer.copy(alpha = 0.48f), p.primary.copy(alpha = 0.4f), radius = 12f)
        scope.drawCircle(p.primary, radius = u(4.2f), center = pt(270f, 50f))
        drawLabel("单元音稳态", 322f, 50f, p.primary, 9.6f, FontWeight.ExtraBold)
        drawLabel("舌位保持靶点 · 1σ 椭圆聚类", 313f, 69f, p.onSurfaceVariant, 8f)

        drawPanel(240f, 102f, 146f, 54f, p.tertiaryContainer.copy(alpha = 0.48f), p.tertiary.copy(alpha = 0.4f), radius = 12f)
        drawStraightArrow(258f, 118f, 280f, 118f, p.tertiary, strokeW = 2f, headLen = 6f)
        drawLabel("复元音动程", 324f, 118f, p.tertiary, 9.6f, FontWeight.ExtraBold)
        drawLabel("单音节内舌位与共振峰连续滑移", 313f, 137f, p.onSurfaceVariant, 8f)
    }

    // -------------------------------------------------------------------------
    // #10 清浊对立与送气特征（VOT 嗓音起始时间三轨时序图）
    // -------------------------------------------------------------------------
    private fun draw10VoicingAspirationVot() {
        val releaseX = 192f

        // 3 horizontal timing lanes with dedicated left label column (14..138)
        val lanes = listOf(
            Triple("真浊塞音 [b]", "负 VOT (< 0 ms · 超前振动)", 24f),
            Triple("清不送气 [p]", "零/短 VOT (≈ +10 ms)", 78f),
            Triple("清送气音 [pʰ]", "长正 VOT (≈ +80 ms 送气)", 132f)
        )

        lanes.forEachIndexed { idx, (title, sub, topY) ->
            val bg = if (idx == 0) p.primaryContainer.copy(alpha = 0.36f)
            else if (idx == 1) p.secondaryContainer.copy(alpha = 0.36f)
            else p.tertiaryContainer.copy(alpha = 0.42f)
            drawPanel(14f, topY, 372f, 44f, bg, p.outlineVariant.copy(alpha = 0.5f), radius = 10f)
            drawLabel(title, 76f, topY + 15f, p.onSurface, 9f, FontWeight.ExtraBold)
            drawLabel(sub, 76f, topY + 31f, p.onSurfaceVariant, 7.3f, FontWeight.Medium)
        }

        // Shared vertical Release Burst line (除阻冲直条 0 ms)
        scope.drawLine(p.error, pt(releaseX, 18f), pt(releaseX, 182f), strokeWidth = u(2f), pathEffect = dashFine)
        drawPill("除阻零点 0 ms", releaseX, 12f, p.errorContainer, p.error, fontPx = 7.6f, padH = 5.5f, padV = 1.4f)

        fun drawVoicingSine(startX: Float, endX: Float, cy: Float, color: Color) {
            val path = Path()
            val steps = ((endX - startX) * 1.5f).toInt().coerceAtLeast(8)
            for (sIdx in 0..steps) {
                val vx = startX + (endX - startX) * sIdx / steps
                val vy = cy - sin(sIdx * 0.65f) * 9.5f
                if (sIdx == 0) path.moveTo(x(vx), y(vy)) else path.lineTo(x(vx), y(vy))
            }
            scope.drawPath(path, color, style = Stroke(width = u(2.2f), cap = StrokeCap.Round))
        }

        // Track 1: Pre-voiced [b] — sine starts at x=144 BEFORE releaseX=192!
        drawVoicingSine(144f, 374f, 46f, p.primary)

        // Track 2: Unaspirated [p] — flat closure 144..192, sine starts at 200
        scope.drawLine(p.outline, pt(144f, 100f), pt(releaseX, 100f), strokeWidth = u(1.6f))
        drawVoicingSine(200f, 374f, 100f, p.secondary)

        // Track 3: Aspirated [pʰ] — flat closure 144..192, turbulent noise 196..274, sine 280..374
        scope.drawLine(p.outline, pt(144f, 154f), pt(releaseX, 154f), strokeWidth = u(1.6f))
        for (nx in 196..274 step 5) {
            val h = if ((nx / 5) % 2 == 0) 9.5f else 5.5f
            scope.drawLine(p.tertiary, pt(nx.toFloat(), 158f - h), pt(nx.toFloat(), 158f + h), strokeWidth = u(1.7f), cap = StrokeCap.Round)
        }
        drawPill("ʰ 送气湍流段", 235f, 139f, p.surface, p.tertiary, p.tertiary.copy(alpha = 0.45f), fontPx = 7.2f, padH = 4.5f, padV = 1f)
        drawVoicingSine(280f, 374f, 154f, p.primary)
    }

    // -------------------------------------------------------------------------
    // #11 国际音标（IPA）与宽式、严式记音
    // -------------------------------------------------------------------------
    private fun draw11IpaNarrowBroad() {
        // Left: Why Pinyin letter 'i' cannot transcribe dialects ("一符多音" vs IPA "一音一符")
        drawPanel(12f, 12f, 174f, 166f, p.secondaryContainer.copy(alpha = 0.42f), p.outlineVariant, radius = 14f)
        drawLabel("拼音一符多音 vs 音标专符", 99f, 28f, p.onSurface, 9.2f, FontWeight.ExtraBold)

        drawPill("拼音 i", 46f, 96f, p.errorContainer, p.error, fontPx = 9.5f, padH = 7f, padV = 3.5f, monospace = true)

        val targets = listOf(
            "yi 衣 → [ i ] 前高" to 56f,
            "zi 资 → [ ɿ ] 舌尖前" to 96f,
            "zhi 知 → [ ʅ ] 舌尖后" to 136f
        )
        targets.forEach { (lbl, ty) ->
            drawStraightArrow(74f, 96f, 94f, ty, p.secondary, strokeW = 1.7f, headLen = 5.5f)
            drawPill(lbl, 138f, ty, p.surface, p.primary, p.primary.copy(alpha = 0.35f), fontPx = 7.9f, padH = 5f, padV = 2.4f)
        }
        drawLabel("IPA 严格遵循「一音一符」", 99f, 164f, p.primary, 8.3f)

        // Right: Narrow [ ] vs Broad / / transcription hierarchy
        drawPanel(198f, 12f, 190f, 78f, p.primaryContainer.copy(alpha = 0.48f), p.primary.copy(alpha = 0.4f), radius = 14f)
        drawPill("严式记音 · 方括号 [  ]", 293f, 29f, p.primary, p.onPrimary, fontPx = 8.6f)
        drawLabel("[ kʰuɐ̃˥˩ ]", 293f, 54f, p.primary, 13.5f, FontWeight.ExtraBold)
        drawLabel("忠实描写实际音值、鼻化与调值细节", 293f, 75f, p.onSurfaceVariant, 7.8f)

        drawPanel(198f, 100f, 190f, 78f, p.tertiaryContainer.copy(alpha = 0.48f), p.tertiary.copy(alpha = 0.4f), radius = 14f)
        drawPill("宽式记音 · 双斜杠 /  /", 293f, 117f, p.tertiary, p.surfaceLowest, fontPx = 8.6f)
        drawLabel("/ kuan⁵¹ /", 293f, 142f, p.tertiary, 13.5f, FontWeight.ExtraBold)
        drawLabel("归纳音系对立，仅保留区别词义音位", 293f, 163f, p.onSurfaceVariant, 7.8f)
    }
}
