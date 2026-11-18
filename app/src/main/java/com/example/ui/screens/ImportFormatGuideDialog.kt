package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.i18n.S
import com.example.i18n.SSmartImport
import com.example.ui.components.StudioDialogEnterAnimation
import com.example.ui.components.StudioModalDialog
import com.example.ui.components.consumeDialogCardTaps

/**
 * Pure Material You visual guide dialog illustrating recommended spreadsheet/text wordlist layouts,
 * audio/image filename token patterns, and multi-speaker directory trees using structured UI components
 * rather than raw ASCII text. Contains no text inputs, so clicking outside dismisses immediately.
 */
@Composable
fun ImportFormatGuideDialog(
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    StudioModalDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        StudioDialogEnterAnimation {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val maxModalHeight = (maxHeight * 0.90f).coerceIn(420.dp, 720.dp)
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = cs.surface,
                    tonalElevation = 3.dp,
                    border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxModalHeight)
                        .consumeDialogCardTaps()
                        .testTag("import_format_guide_dialog")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Dialog Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = SSmartImport.formatGuideTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cs.onSurface
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = SSmartImport.formatGuideSubtitle,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .testTag("format_guide_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = S.common.close,
                                    tint = cs.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))

                        // Scrollable Visual Sections
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 1. Wordlist Visual Section (Mini Table + Line-Numbered TXT Card)
                            VisualGuideSectionCard(
                                stepBadge = "1",
                                icon = Icons.Default.TableChart,
                                title = "调查字表格式",
                                subtitle = "支持带表头或无表头表格，也支持按行排版的纯文本与 Word 文档"
                            ) {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val sideBySide = maxWidth >= 540.dp
                                    if (sideBySide) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            MiniSpreadsheetVisualCard(modifier = Modifier.weight(1.12f))
                                            MiniTxtDocumentVisualCard(modifier = Modifier.weight(0.88f))
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            MiniSpreadsheetVisualCard(modifier = Modifier.fillMaxWidth())
                                            MiniTxtDocumentVisualCard(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }

                            // 2. Audio & Image Naming Visual Section
                            VisualGuideSectionCard(
                                stepBadge = "2",
                                icon = Icons.Default.AudioFile,
                                title = "录音与配图命名方式",
                                subtitle = "支持常见音频与图片格式混合放入，系统自动拆解文件名中的题号、词条与多遍信息"
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Supported Extension Pills Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(".wav", ".mp3", ".m4a", ".flac", ".ogg", ".aiff", ".png / .jpg").forEach { ext ->
                                            FormatExtensionPill(text = ext)
                                        }
                                    }

                                    AudioNamingRuleVisualCard(
                                        badgeText = "最推荐",
                                        badgeBg = cs.primaryContainer,
                                        badgeFg = cs.onPrimaryContainer,
                                        ruleTitle = "题号 + 词条（括号内可附带提示）",
                                        sampleFiles = listOf("001_坡.wav", "02-日头(太阳).m4a"),
                                        parsedPills = listOf(
                                            ParsedTokenChipData("题号 #1 / #2", cs.surfaceContainerHighest, cs.onSurface),
                                            ParsedTokenChipData("词条：坡 / 日头", cs.primaryContainer, cs.onPrimaryContainer),
                                            ParsedTokenChipData("提示：太阳", cs.secondaryContainer, cs.onSecondaryContainer)
                                        )
                                    )

                                    AudioNamingRuleVisualCard(
                                        badgeText = "按词条名",
                                        badgeBg = cs.secondaryContainer,
                                        badgeFg = cs.onSecondaryContainer,
                                        ruleTitle = "直接写词条（加方括号可区分同形字分组）",
                                        sampleFiles = listOf("坡.mp3", "【天文】日头.flac"),
                                        parsedPills = listOf(
                                            ParsedTokenChipData("分组：天文", cs.surfaceContainerHighest, cs.onSurfaceVariant),
                                            ParsedTokenChipData("精准对应同名词条", cs.secondaryContainer, cs.onSecondaryContainer)
                                        )
                                    )

                                    AudioNamingRuleVisualCard(
                                        badgeText = "按题号",
                                        badgeBg = cs.surfaceContainerHighest,
                                        badgeFg = cs.onSurface,
                                        ruleTitle = "纯数字或带通用录音前缀",
                                        sampleFiles = listOf("01.wav", "rec_002.m4a"),
                                        parsedPills = listOf(
                                            ParsedTokenChipData("自动忽略 rec_ 前缀", cs.surfaceContainerHighest, cs.onSurfaceVariant),
                                            ParsedTokenChipData("对应第 1、2 词", cs.primaryContainer, cs.onPrimaryContainer)
                                        )
                                    )

                                    AudioNamingRuleVisualCard(
                                        badgeText = "同词多遍",
                                        badgeBg = cs.tertiaryContainer,
                                        badgeFg = cs.onTertiaryContainer,
                                        ruleTitle = "末尾带遍数序号或括号数字",
                                        sampleFiles = listOf("坡_1.wav", "坡_2.wav", "坡(3).m4a"),
                                        parsedPills = listOf(
                                            ParsedTokenChipData("不拆成新词", cs.surfaceContainerHighest, cs.onSurfaceVariant),
                                            ParsedTokenChipData("合入「坡」第 1～3 遍历史", cs.tertiaryContainer, cs.onTertiaryContainer)
                                        )
                                    )
                                }
                            }

                            // 3. Visual Folder & Multi-Speaker Hierarchy Tree
                            VisualGuideSectionCard(
                                stepBadge = "3",
                                icon = Icons.Default.FolderOpen,
                                title = "整个文件夹与多位发音人目录",
                                subtitle = "直接选择或拖入整个调查文件夹，自动读取字表并按子文件夹归入对应发音人"
                            ) {
                                VisualFolderHierarchyCard()
                            }

                            // Bottom Tip Banner
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cs.secondaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, cs.secondary.copy(alpha = 0.22f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = cs.onSecondaryContainer,
                                        modifier = Modifier.padding(top = 2.dp).size(18.dp)
                                    )
                                    Text(
                                        text = SSmartImport.formatTipNote,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = cs.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))

                        // Dialog Footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Text(S.common.gotIt, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualGuideSectionCard(
    stepBadge: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerLow,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = cs.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = cs.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = stepBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

/**
 * Visual mini-spreadsheet table replacing raw ASCII pipe text.
 */
@Composable
private fun MiniSpreadsheetVisualCard(
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val gridColor = cs.outlineVariant.copy(alpha = 0.5f)
    val sampleRows = listOf(
        arrayOf("1", "声母组", "坡", "帮母"),
        arrayOf("2", "声母组", "拍", "滂母"),
        arrayOf("3", "天文", "日头", "太阳")
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Excel / CSV 表格",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FormatExtensionPill(".xlsx")
                    FormatExtensionPill(".csv")
                }
            }

            // Styled Table Grid
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cs.surface,
                border = BorderStroke(1.dp, gridColor),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(cs.surfaceContainerHigh),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpreadsheetHeaderCell("序号", Modifier.weight(0.18f), center = true)
                        VerticalDivider(color = gridColor, modifier = Modifier.fillMaxHeight())
                        SpreadsheetHeaderCell("分组", Modifier.weight(0.30f))
                        VerticalDivider(color = gridColor, modifier = Modifier.fillMaxHeight())
                        SpreadsheetHeaderCell("词条", Modifier.weight(0.25f))
                        VerticalDivider(color = gridColor, modifier = Modifier.fillMaxHeight())
                        SpreadsheetHeaderCell("提示", Modifier.weight(0.27f))
                    }

                    sampleRows.forEachIndexed { idx, row ->
                        HorizontalDivider(color = gridColor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(
                                    if (idx % 2 == 1) cs.surfaceContainerLow.copy(alpha = 0.5f)
                                    else Color.Transparent
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row[0],
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = cs.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.weight(0.18f).padding(vertical = 7.dp, horizontal = 4.dp)
                            )
                            VerticalDivider(color = gridColor, modifier = Modifier.fillMaxHeight())
                            Box(
                                modifier = Modifier.weight(0.30f).padding(horizontal = 6.dp, vertical = 5.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = cs.secondaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = row[1],
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            VerticalDivider(color = gridColor, modifier = Modifier.fillMaxHeight())
                            Text(
                                text = row[2],
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.25f).padding(horizontal = 8.dp, vertical = 7.dp)
                            )
                            VerticalDivider(color = gridColor, modifier = Modifier.fillMaxHeight())
                            Text(
                                text = row[3],
                                fontSize = 11.5.sp,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.27f).padding(horizontal = 8.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpreadsheetHeaderCell(
    text: String,
    modifier: Modifier,
    center: Boolean = false
) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        maxLines = 1,
        modifier = modifier.padding(horizontal = if (center) 2.dp else 8.dp, vertical = 6.dp)
    )
}

/**
 * Visual line-numbered TXT / Word document card replacing raw plain text.
 */
@Composable
private fun MiniTxtDocumentVisualCard(
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val borderCol = cs.outlineVariant.copy(alpha = 0.5f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "TXT / Word 文本",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FormatExtensionPill(".txt")
                    FormatExtensionPill(".docx")
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cs.surfaceContainerLow.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, borderCol),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TxtEditorVisualLine(lineNo = "01") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = cs.primaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "【声母组】",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    TxtEditorVisualLine(lineNo = "02") {
                        TxtWordAndRemarkTokens(indexPrefix = "1.", word = "坡", remark = "(帮母)")
                    }
                    TxtEditorVisualLine(lineNo = "03") {
                        TxtWordAndRemarkTokens(indexPrefix = "2.", word = "拍", remark = "(滂母)")
                    }
                    TxtEditorVisualLine(lineNo = "04") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = cs.primaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "【天文】",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    TxtEditorVisualLine(lineNo = "05") {
                        TxtWordAndRemarkTokens(indexPrefix = null, word = "日头", remark = "太阳")
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtEditorVisualLine(
    lineNo: String,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .background(cs.surfaceContainerHigh.copy(alpha = 0.55f))
                .padding(vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lineNo,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = cs.outline
            )
        }
        VerticalDivider(color = cs.outlineVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxHeight())
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun TxtWordAndRemarkTokens(
    indexPrefix: String?,
    word: String,
    remark: String
) {
    val cs = MaterialTheme.colorScheme
    if (indexPrefix != null) {
        Text(
            text = indexPrefix,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            color = cs.onSurfaceVariant
        )
    }
    Text(
        text = word,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = cs.onSurface
    )
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = cs.surfaceContainerHighest.copy(alpha = 0.7f)
    ) {
        Text(
            text = remark,
            fontSize = 10.5.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

private data class ParsedTokenChipData(
    val label: String,
    val bg: Color,
    val fg: Color
)

@Composable
private fun AudioNamingRuleVisualCard(
    badgeText: String,
    badgeBg: Color,
    badgeFg: Color,
    ruleTitle: String,
    sampleFiles: List<String>,
    parsedPills: List<ParsedTokenChipData>
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Top Rule Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                    )
                }
                Text(
                    text = ruleTitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Visual File Chips -> Parsed Result Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = cs.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    val wideRow = maxWidth >= 460.dp
                    if (wideRow) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sampleFiles.forEach { fn -> SampleAudioFileChip(fn) }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = cs.outline,
                                modifier = Modifier.size(15.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                parsedPills.forEach { pill ->
                                    Surface(shape = RoundedCornerShape(6.dp), color = pill.bg) {
                                        Text(
                                            text = pill.label,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = pill.fg,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sampleFiles.forEach { fn -> SampleAudioFileChip(fn) }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                parsedPills.forEach { pill ->
                                    Surface(shape = RoundedCornerShape(6.dp), color = pill.bg) {
                                        Text(
                                            text = pill.label,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = pill.fg,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
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

@Composable
private fun SampleAudioFileChip(fileName: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = fileName,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Graphical directory hierarchy tree using real Compose vector connectors, icons, and speaker badges.
 */
@Composable
private fun VisualFolderHierarchyCard() {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Root Directory Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cs.primaryContainer,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = cs.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = "苏州方言调查",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = cs.surfaceContainerHighest
                ) {
                    Text(
                        text = "拖入或选择此根目录",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Child 1: Wordlist spreadsheet at root
            VisualTreeRow(
                parentContinues = null,
                isLastChild = false
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "调查字表.xlsx",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = cs.primaryContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = "自动读取词表",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                    )
                }
            }

            // Child 2: Speaker 1 Subfolder ("张三")
            VisualTreeRow(
                parentContinues = null,
                isLastChild = false
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = cs.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "张三",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                SpeakerFolderBadge(speakerName = "张三")
            }

            VisualTreeRow(parentContinues = true, isLastChild = false) {
                TreeAudioLeafContent("001_坡.wav")
            }
            VisualTreeRow(parentContinues = true, isLastChild = true) {
                TreeAudioLeafContent("002_拍.wav")
            }

            // Child 3: Speaker 2 Subfolder ("李阿姨")
            VisualTreeRow(
                parentContinues = null,
                isLastChild = true
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = cs.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "李阿姨",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
                SpeakerFolderBadge(speakerName = "李阿姨")
            }

            VisualTreeRow(parentContinues = false, isLastChild = false) {
                TreeAudioLeafContent("001_坡.wav")
            }
            VisualTreeRow(parentContinues = false, isLastChild = true) {
                TreeAudioLeafContent("002_拍.wav")
            }
        }
    }
}

/**
 * Renders crisp vector tree guide lines without relying on ASCII box-drawing glyphs.
 * If [parentContinues] is null, this is a depth-1 node; otherwise it is a depth-2 leaf node.
 */
@Composable
private fun VisualTreeRow(
    parentContinues: Boolean?,
    isLastChild: Boolean,
    content: @Composable () -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (parentContinues != null) {
            // Outer trunk column for depth-2 nodes
            Canvas(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
            ) {
                if (parentContinues) {
                    val x = size.width * 0.52f
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Branch connector column (`├─` or `└─`)
        Canvas(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            val x = size.width * 0.52f
            val midY = size.height * 0.5f
            val stroke = 1.5.dp.toPx()
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, if (isLastChild) midY else size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = lineColor,
                start = Offset(x, midY),
                end = Offset(size.width - 2.dp.toPx(), midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f)
        ) {
            content()
        }
    }
}

@Composable
private fun SpeakerFolderBadge(speakerName: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = cs.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = cs.onSecondaryContainer,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = "发音人：$speakerName",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSecondaryContainer,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TreeAudioLeafContent(fileName: String) {
    val cs = MaterialTheme.colorScheme
    Icon(
        imageVector = Icons.Default.AudioFile,
        contentDescription = null,
        tint = cs.primary.copy(alpha = 0.85f),
        modifier = Modifier.size(14.dp)
    )
    Text(
        text = fileName,
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace,
        color = cs.onSurfaceVariant
    )
}

@Composable
private fun FormatExtensionPill(text: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = cs.surfaceContainerHighest.copy(alpha = 0.75f)
    ) {
        Text(
            text = text,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
