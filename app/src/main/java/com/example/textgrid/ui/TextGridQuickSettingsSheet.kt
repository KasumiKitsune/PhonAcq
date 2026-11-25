package com.example.textgrid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.textgrid.model.TextGridScaleMode
import com.example.textgrid.model.TextGridScoreTheme
import com.example.textgrid.model.TextGridTickPrecision
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.model.TextGridWrapMode
import com.example.ui.components.StudioAdaptiveSheet
import com.example.ui.components.StudioFilterChip
import com.example.ui.components.StudioSwitchRow

/**
 * Clean, minimal quick display settings sheet for the full-screen read-only TextGrid viewer.
 * Uses PhonAcq's unified [StudioAdaptiveSheet], [StudioFilterChip], and [StudioSwitchRow]
 * so desktop hover, switch drag/tap physics, and mobile bottom-sheet framing stay 100% consistent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextGridQuickSettingsSheet(
    visible: Boolean,
    config: TextGridVisualConfig,
    onConfigChange: (TextGridVisualConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val str = TextGridUiStrings.current
    val colorScheme = MaterialTheme.colorScheme

    StudioAdaptiveSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        maxWidth = 520.dp
    ) { requestClose ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = str.quickSettingsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                IconButton(
                    onClick = requestClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 1. Scale mode
            QuickOptionRow(label = str.prefScaleMode) {
                TextGridScaleMode.entries.forEach { mode ->
                    StudioFilterChip(
                        selected = config.scaleMode == mode,
                        onClick = { onConfigChange(config.copy(scaleMode = mode)) },
                        labelText = TextGridUiStrings.labelForScaleMode(mode)
                    )
                }
            }

            // 2. Wrap mode
            QuickOptionRow(label = str.prefWrapMode) {
                TextGridWrapMode.entries.forEach { mode ->
                    StudioFilterChip(
                        selected = config.wrapMode == mode,
                        onClick = { onConfigChange(config.copy(wrapMode = mode)) },
                        labelText = TextGridUiStrings.labelForWrapMode(mode)
                    )
                }
            }

            // 3. Tick precision (3 dec / 4 dec / Hidden)
            QuickOptionRow(label = str.prefTickPrecision) {
                listOf(
                    TextGridTickPrecision.P3,
                    TextGridTickPrecision.P4,
                    TextGridTickPrecision.NONE
                ).forEach { prec ->
                    StudioFilterChip(
                        selected = config.tickPrecision == prec,
                        onClick = { onConfigChange(config.copy(tickPrecision = prec)) },
                        labelText = TextGridUiStrings.labelForTickPrecision(prec)
                    )
                }
            }

            // 4. Score palette
            QuickOptionRow(label = str.prefScoreTheme) {
                TextGridScoreTheme.entries.forEach { theme ->
                    StudioFilterChip(
                        selected = config.scoreTheme == theme,
                        onClick = { onConfigChange(config.copy(scoreTheme = theme)) },
                        labelText = TextGridUiStrings.labelForScoreTheme(theme)
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

            // 5. Whitespace & IPA gloss toggle using unified StudioSwitchRow
            StudioSwitchRow(
                title = str.prefShowWhitespace,
                subtitle = str.prefShowWhitespaceDesc,
                checked = config.showRichWhitespace,
                onCheckedChange = { onConfigChange(config.copy(showRichWhitespace = it)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickOptionRow(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}
