package com.example.textgrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.textgrid.layout.TextGridSafeMath

/**
 * Clean, refined read-only Praat TextGrid source code viewer:
 * - Soft line-number gutter
 * - Restrained syntax coloring (keywords, strings, numeric timestamps, brackets)
 * - Soft highlight on active playback / selected source lines
 * - Smooth vertical & horizontal scroll + native text selection via [SelectionContainer]
 * - Strictly finite scroll-offset math (zero NaN risk)
 */
@Composable
fun TextGridSourceViewer(
    sourceText: String,
    palette: TextGridScorePalette,
    highlightedLines: Set<Int> = emptySet(),
    scrollToLine: Int? = null,
    maxHeight: Dp = 320.dp,
    modifier: Modifier = Modifier
) {
    val lines = remember(sourceText) {
        sourceText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    }
    val gutterWidth = remember(lines.size) {
        val digits = lines.size.coerceAtLeast(1).toString().length
        (digits * 8 + 18).coerceIn(34, 58).dp
    }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    LaunchedEffect(scrollToLine, lines.size) {
        val target = scrollToLine ?: return@LaunchedEffect
        if (target !in 1..lines.size || lines.isEmpty()) return@LaunchedEffect
        val maxVal = vScroll.maxValue
        if (maxVal > 0 && lines.size > 1) {
            val fraction = TextGridSafeMath.safeCoerceIn((target - 1).toFloat() / (lines.size - 1).toFloat(), 0f, 1f, 0f)
            val targetPx = TextGridSafeMath.safeRoundToInt(fraction * maxVal, 0).coerceIn(0, maxVal)
            vScroll.animateScrollTo(targetPx)
        }
    }

    val codeStyle = remember(palette) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = palette.ink
        )
    }
    val lineNumStyle = remember(palette) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            lineHeight = 18.sp,
            color = palette.codeLineNum,
            textAlign = TextAlign.End
        )
    }

    val styledLines = remember(lines, palette) {
        lines.map { line -> highlightPraatSourceLine(line, palette) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.codeBg)
            .let { base ->
                if (maxHeight != Dp.Unspecified) base.height(maxHeight) else base
            }
            .verticalScroll(vScroll)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Fixed-width muted line number gutter
            Column(
                modifier = Modifier
                    .width(gutterWidth)
                    .background(palette.codeGutterBg)
                    .padding(vertical = 8.dp)
            ) {
                for (idx in lines.indices) {
                    val lineNum = idx + 1
                    val isActive = lineNum in highlightedLines
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isActive) palette.codeActiveLineBg else Color.Transparent)
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = lineNum.toString(),
                            style = lineNumStyle.copy(
                                color = if (isActive) palette.selectionAccent else palette.codeLineNum,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            // Thin vertical separator
            Box(
                modifier = Modifier
                    .width(0.75.dp)
                    .background(palette.gutterDivider)
            )

            // Selectable horizontally-scrollable code area
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll)
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 240.dp)
                        .padding(vertical = 8.dp)
                ) {
                    for (idx in styledLines.indices) {
                        val lineNum = idx + 1
                        val isActive = lineNum in highlightedLines
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isActive) palette.codeActiveLineBg else Color.Transparent)
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = styledLines[idx],
                                style = codeStyle,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PRAAT_KEY_PATTERN = Regex(
    """^(\s*)(File type|Object class|xmin|xmax|tiers\?|size|item|class|name|intervals|points|number|mark|text)(\s*)(\[[^\]]*])?(\s*=)?(.*)$"""
)
private val NUMBER_TOKEN_PATTERN = Regex("""(?<![A-Za-z_])-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b""")

private fun highlightPraatSourceLine(
    line: String,
    palette: TextGridScorePalette
): AnnotatedString {
    if (line.isEmpty()) return AnnotatedString(" ")
    return buildAnnotatedString {
        append(line)

        // 1. Structural Praat key + optional [index]
        val match = PRAAT_KEY_PATTERN.matchEntire(line)
        if (match != null) {
            val keyGroup = match.groups[2]
            if (keyGroup != null) {
                addStyle(
                    SpanStyle(color = palette.codeKeyword, fontWeight = FontWeight.Medium),
                    keyGroup.range.first,
                    keyGroup.range.last + 1
                )
            }
            val bracketGroup = match.groups[4]
            if (bracketGroup != null) {
                addStyle(
                    SpanStyle(color = palette.codeBracket),
                    bracketGroup.range.first,
                    bracketGroup.range.last + 1
                )
            }
        }

        // 2. Quoted strings (`"..."`, handling Praat doubled `""` escapes)
        val stringRanges = ArrayList<IntRange>()
        var i = 0
        val n = line.length
        while (i < n) {
            if (line[i] == '"') {
                val start = i
                i++
                while (i < n) {
                    if (line[i] == '"') {
                        if (i + 1 < n && line[i + 1] == '"') {
                            i += 2
                        } else {
                            i++
                            break
                        }
                    } else {
                        i++
                    }
                }
                stringRanges.add(start until i)
                addStyle(SpanStyle(color = palette.codeString), start, i)
            } else {
                i++
            }
        }

        // 3. Numeric literals outside quoted strings
        for (numMatch in NUMBER_TOKEN_PATTERN.findAll(line)) {
            val r = numMatch.range
            val insideString = stringRanges.any { r.first >= it.first && r.last <= it.last }
            if (!insideString) {
                addStyle(SpanStyle(color = palette.codeNumber), r.first, r.last + 1)
            }
        }
    }
}
