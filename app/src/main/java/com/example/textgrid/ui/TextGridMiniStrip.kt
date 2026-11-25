package com.example.textgrid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.layout.DualScaleLayoutEngine
import com.example.textgrid.layout.DualScaleLayoutOptions
import com.example.textgrid.layout.TextGridSafeMath
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.model.TextGridOpenMode
import com.example.textgrid.model.TextGridSelection
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.model.TextGridWrapMode
import com.example.ui.theme.LocalPhoneticFontFamily
import java.util.Locale

/**
 * Compact read-only mini preview strip for Export, Smart Import, and Desktop File Inspector.
 *
 * Uses an OUTER unscrollable [BoxWithConstraints] (finite `targetWidthDp`) and INNER horizontal
 * scroll so `TextGridWrapMode.SINGLE` never receives `Dp.Infinity`.
 */
@Composable
fun TextGridMiniStrip(
    doc: TextGridDocument,
    visualConfig: TextGridVisualConfig,
    title: String? = null,
    subtitle: String? = null,
    timeDecimals: Int? = null,
    formatVariant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT,
    rawSourceOverride: String? = null,
    maxHeight: Dp = 176.dp,
    onOpenFullViewer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val str = TextGridUiStrings.current
    val palette = rememberTextGridScorePalette(visualConfig.scoreTheme)
    val density = LocalDensity.current
    val phoneticFont = LocalPhoneticFontFamily.current
    val textMeasurer = rememberTextMeasurer()
    val widthMeasurer = remember(textMeasurer, density, phoneticFont) {
        ComposeScoreTextWidthMeasurer(textMeasurer, density.density, phoneticFont)
    }

    var showSource by remember(visualConfig.defaultOpenMode) {
        mutableStateOf(visualConfig.defaultOpenMode == TextGridOpenMode.SOURCE)
    }
    var selection by remember(doc) { mutableStateOf<TextGridSelection?>(null) }

    val sourceText = remember(doc, formatVariant, timeDecimals, rawSourceOverride) {
        rawSourceOverride ?: TextGridCodec.serialize(doc, variant = formatVariant, timeDecimals = timeDecimals)
    }

    val safeDurationText = String.format(Locale.US, "%.2f", TextGridSafeMath.safeAtLeast(doc.duration, 0.0))
    val metaText = str.metaSummary(doc.tierCount, safeDurationText)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = palette.surfaceBg,
        border = BorderStroke(0.75.dp, palette.cardBorder),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact single-row header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title?.takeIf { it.isNotBlank() } ?: doc.name.ifBlank { "TextGrid" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (!subtitle.isNullOrBlank()) "$metaText · $subtitle" else metaText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextGridModePillToggle(
                    showSource = showSource,
                    onSelectScore = { showSource = false },
                    onSelectSource = { showSource = true },
                    scoreLabel = str.tabScore,
                    sourceLabel = str.tabSource,
                    palette = palette,
                    compact = true
                )

                if (onOpenFullViewer != null) {
                    IconButton(
                        onClick = onOpenFullViewer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = str.openFullViewer,
                            tint = palette.muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = palette.cardBorder.copy(alpha = 0.65f))

            if (showSource) {
                TextGridSourceViewer(
                    sourceText = sourceText,
                    palette = palette,
                    maxHeight = maxHeight
                )
            } else {
                // OUTER BoxWithConstraints guarantees finite width for SINGLE-row strip!
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val containerW = if (maxWidth.isSpecified && maxWidth.value.isFinite()) {
                        maxWidth.value.coerceIn(220f, 4096f)
                    } else {
                        320f
                    }
                    val layoutOptions = remember(visualConfig, containerW) {
                        DualScaleLayoutOptions.fromVisualConfig(
                            config = visualConfig,
                            targetWidthDp = containerW,
                            wrapOverride = TextGridWrapMode.SINGLE,
                            compactMode = true
                        )
                    }
                    val layout = remember(doc, layoutOptions, widthMeasurer) {
                        DualScaleLayoutEngine.computeLayout(doc, layoutOptions, widthMeasurer)
                    }
                    val needsHScroll = layout.widthDp > containerW + 2f
                    val hScroll = rememberScrollState()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .let { m -> if (needsHScroll) m.horizontalScroll(hScroll) else m }
                    ) {
                        DualScaleScoreCanvas(
                            doc = doc,
                            layout = layout,
                            palette = palette,
                            selection = selection,
                            onSelect = { selection = it }
                        )
                    }
                }
            }
        }
    }
}
