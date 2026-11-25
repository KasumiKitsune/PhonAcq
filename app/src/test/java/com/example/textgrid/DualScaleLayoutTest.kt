package com.example.textgrid

import com.example.textgrid.layout.DualScaleLayoutEngine
import com.example.textgrid.layout.DualScaleLayoutOptions
import com.example.textgrid.layout.HeuristicTextWidthMeasurer
import com.example.textgrid.layout.PraatTrigraphDecoder
import com.example.textgrid.layout.RichLayoutOptions
import com.example.textgrid.layout.RichTokenKind
import com.example.textgrid.layout.ScoreFontRole
import com.example.textgrid.layout.ScoreLayout
import com.example.textgrid.layout.TextGridRichText
import com.example.textgrid.layout.TextGridSafeMath
import com.example.textgrid.layout.TextWidthMeasurer
import com.example.textgrid.layout.TrigraphDisplayMode
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridInterval
import com.example.textgrid.model.TextGridPoint
import com.example.textgrid.model.TextGridScaleMode
import com.example.textgrid.model.TextGridTickPrecision
import com.example.textgrid.model.TextGridTier
import com.example.textgrid.model.TextGridWrapMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualScaleLayoutTest {

    @Test
    fun silenceCollapse_collapsesLongEmptyColumnButKeepsLabeledColumn() {
        val doc = TextGridDocument(
            name = "silence_test",
            xmin = 0.0,
            xmax = 5.0,
            tiers = listOf(
                TextGridTier.IntervalTier(
                    name = "words",
                    xmin = 0.0,
                    xmax = 5.0,
                    intervals = listOf(
                        TextGridInterval(0.0, 2.2, ""),
                        TextGridInterval(2.2, 4.2, "hello"),
                        TextGridInterval(4.2, 5.0, "")
                    )
                )
            )
        )

        val layout = DualScaleLayoutEngine.computeLayout(
            doc = doc,
            options = DualScaleLayoutOptions(
                wrapMode = TextGridWrapMode.SINGLE,
                targetWidthDp = 400f,
                scaleMode = TextGridScaleMode.ELASTIC
            )
        )

        assertEquals(3, layout.colCollapsed.size)
        assertTrue("Long empty column >= 1.6s should be collapsed", layout.colCollapsed[0])
        assertFalse("Long non-empty column must not collapse", layout.colCollapsed[1])
        assertFalse("Short empty column < 1.6s must not collapse", layout.colCollapsed[2])
        assertLayoutStrictlyFinite(layout)
    }

    @Test
    fun dpLineWrap_prefersSharedBoundaryOverSplittingNonEmptyInterval() {
        val wordIntervals = listOf(
            TextGridInterval(0.0, 0.8, "sentence_one_long"),
            TextGridInterval(0.8, 1.6, "sentence_two_long"),
            TextGridInterval(1.6, 2.4, "sentence_three_long")
        )
        val phoneIntervals = listOf(
            TextGridInterval(0.0, 0.4, "s1a"),
            TextGridInterval(0.4, 0.8, "s1b"),
            TextGridInterval(0.8, 1.2, "s2a"),
            TextGridInterval(1.2, 1.6, "s2b"),
            TextGridInterval(1.6, 2.0, "s3a"),
            TextGridInterval(2.0, 2.4, "s3b")
        )
        val doc = TextGridDocument(
            name = "dp_wrap",
            xmin = 0.0,
            xmax = 2.4,
            tiers = listOf(
                TextGridTier.IntervalTier("words", 0.0, 2.4, wordIntervals),
                TextGridTier.IntervalTier("phones", 0.0, 2.4, phoneIntervals)
            )
        )

        val layout = DualScaleLayoutEngine.computeLayout(
            doc = doc,
            options = DualScaleLayoutOptions(
                wrapMode = TextGridWrapMode.AUTO,
                targetWidthDp = 340f,
                scaleMode = TextGridScaleMode.ELASTIC
            )
        )

        assertTrue("Should wrap into multiple bands on narrow width", layout.bands.size >= 2)
        val firstBreakTime = layout.bands[0].endT
        assertTrue(
            "DP wrap should prefer shared word boundary (0.8 or 1.6), got $firstBreakTime",
            firstBreakTime == 0.8 || firstBreakTime == 1.6
        )
        assertLayoutStrictlyFinite(layout)
    }

    @Test
    fun tickStrings_expandCollidingAdjacentTimestampsUpTo6Decimals() {
        val spine = doubleArrayOf(0.0, 0.1231, 0.1234, 0.5)
        val labels = TextGridRichText.buildDisplayTickStrings(spine, precisionDigits = 3, fixedDecimals = false)

        assertEquals("0", labels[0])
        assertEquals("0.1231", labels[1])
        assertEquals("0.1234", labels[2])
        assertEquals("0.5", labels[3])

        val fixedLabels = TextGridRichText.buildDisplayTickStrings(
            doubleArrayOf(0.0, 1.5, 1.5004),
            precisionDigits = 2,
            fixedDecimals = true
        )
        assertEquals("0.00", fixedLabels[0])
        assertEquals("1.5000", fixedLabels[1])
        assertEquals("1.5004", fixedLabels[2])
    }

    @Test
    fun horizontalTicks_neverOverlapEvenOnNarrowWidths() {
        val intervals = (0 until 12).map { i ->
            TextGridInterval(i * 0.08, (i + 1) * 0.08, "p$i")
        }
        val doc = TextGridDocument(
            name = "dense_ticks",
            xmin = 0.0,
            xmax = 0.96,
            tiers = listOf(TextGridTier.IntervalTier("phones", 0.0, 0.96, intervals))
        )

        for (widthDp in listOf(240f, 320f, 480f)) {
            for (scale in TextGridScaleMode.entries) {
                val layout = DualScaleLayoutEngine.computeLayout(
                    doc = doc,
                    options = DualScaleLayoutOptions(
                        wrapMode = TextGridWrapMode.SINGLE,
                        targetWidthDp = widthDp,
                        scaleMode = scale,
                        tickPrecision = TextGridTickPrecision.P4
                    )
                )
                assertLayoutStrictlyFinite(layout)
                for (band in layout.bands) {
                    val visibleTicks = band.spineCols
                        .filter { it.labelVisible && it.label.isNotEmpty() }
                        .sortedBy { it.labelLeftX }
                    for (i in 1 until visibleTicks.size) {
                        val prevRight = visibleTicks[i - 1].labelLeftX + visibleTicks[i - 1].labelWidth
                        val curLeft = visibleTicks[i].labelLeftX
                        assertTrue(
                            "Ticks overlapped at width=$widthDp scale=$scale: prevRight=$prevRight curLeft=$curLeft",
                            curLeft >= prevRight + 8f
                        )
                    }
                }
            }
        }
    }

    @Test
    fun richText_tokenizesWhitespaceControlsSoftWrapAndPraatTrigraphs() {
        val tokens = TextGridRichText.tokenizeLine("  a b\tc\u00A0\u200B ", hasTrailingNewline = true)
        assertEquals(RichTokenKind.SPACE_META, tokens[0].kind)
        assertEquals("␣", tokens[0].str)
        assertEquals(RichTokenKind.SPACE_META, tokens[1].kind)
        assertTrue(tokens.any { it.kind == RichTokenKind.TEXT && it.str == " " })
        assertTrue(tokens.any { it.kind == RichTokenKind.TAB && it.str == "⇥" })
        assertTrue(tokens.any { it.kind == RichTokenKind.CTRL && it.str == "[00A0]" })
        assertTrue(tokens.any { it.kind == RichTokenKind.CTRL && it.str == "[200B]" })
        assertEquals(RichTokenKind.NEWLINE, tokens.last().kind)
        assertEquals("↵", tokens.last().str)

        val wrapped = TextGridRichText.layoutRichText(
            str = "alpha beta gamma",
            maxWidthPx = 46f,
            role = ScoreFontRole.INTERVAL_TEXT,
            measurer = HeuristicTextWidthMeasurer,
            isEditMode = true
        )
        assertTrue("Expected multi-line soft wrap", wrapped.lines.size >= 2)
        assertEquals(RichTokenKind.SPACE_META, wrapped.lines[0].tokens.last().kind)
        assertEquals("␣", wrapped.lines[0].tokens.last().str)
        assertEquals(RichTokenKind.WRAP, wrapped.lines[1].tokens.first().kind)
        assertEquals("↪", wrapped.lines[1].tokens.first().str)

        val decoded = PraatTrigraphDecoder.decodeGlossOrNull("\\sh\\sw")
        assertNotNull(decoded)
        assertEquals("ʃə", decoded)

        val richWithGloss = TextGridRichText.layoutRichText(
            str = "\\sh",
            maxWidthPx = 0f,
            role = ScoreFontRole.INTERVAL_TEXT,
            measurer = HeuristicTextWidthMeasurer,
            isEditMode = true,
            options = RichLayoutOptions(trigraphMode = TrigraphDisplayMode.AUTO)
        )
        assertEquals("ʃ", richWithGloss.gloss)
    }

    @Test
    fun largeDocument_over2500ColumnsAutoVirtualizesToSingleRow() {
        val count = 2600
        val intervals = List(count) { i ->
            TextGridInterval(i * 0.01, (i + 1) * 0.01, if (i % 5 == 0) "a" else "")
        }
        val doc = TextGridDocument(
            name = "large_mfa",
            xmin = 0.0,
            xmax = count * 0.01,
            tiers = listOf(TextGridTier.IntervalTier("phones", 0.0, count * 0.01, intervals))
        )

        val layout = DualScaleLayoutEngine.computeLayout(
            doc = doc,
            options = DualScaleLayoutOptions(
                wrapMode = TextGridWrapMode.AUTO,
                targetWidthDp = 380f
            )
        )

        assertTrue("Documents with >2500 columns must auto-virtualize", layout.autoVirtualizedSingleRow)
        assertEquals(TextGridWrapMode.SINGLE, layout.effectiveWrapMode)
        assertEquals(1, layout.bands.size)
        assertLayoutStrictlyFinite(layout)
    }

    @Test
    fun pointTier_clampsBoundaryLabelsAndStacksOverlappingLevels() {
        val doc = TextGridDocument(
            name = "point_clamp",
            xmin = 0.0,
            xmax = 1.0,
            tiers = listOf(
                TextGridTier.IntervalTier("words", 0.0, 1.0, listOf(TextGridInterval(0.0, 1.0, "test"))),
                TextGridTier.TextTier(
                    name = "tones",
                    xmin = 0.0,
                    xmax = 1.0,
                    points = listOf(
                        TextGridPoint(0.0, "StartLongLabel"),
                        TextGridPoint(0.02, "SecondNearbyLabel"),
                        TextGridPoint(1.0, "EndLongLabel")
                    )
                )
            )
        )

        val layout = DualScaleLayoutEngine.computeLayout(
            doc = doc,
            options = DualScaleLayoutOptions(
                wrapMode = TextGridWrapMode.SINGLE,
                targetWidthDp = 360f
            )
        )

        val band = layout.bands.first()
        val ptTier = band.tiersLayout[1]!!
        assertEquals(3, ptTier.visiblePoints.size)
        val bandLeft = band.originX
        val bandRight = band.originX + band.bandContentWidth
        for (vp in ptTier.visiblePoints) {
            assertTrue("Point box left must clamp >= bandLeft", vp.boxX >= bandLeft - 0.01f)
            assertTrue("Point box right must clamp <= bandRight", vp.boxX + vp.boxWidth <= bandRight + 0.01f)
        }
        assertTrue(
            "Nearby overlapping points must stack onto different levels",
            ptTier.visiblePoints[0].level != ptTier.visiblePoints[1].level
        )
        assertLayoutStrictlyFinite(layout)
    }

    // =========================================================================
    // Regression Tests for `Cannot round NaN value.` & Edge-Case Geometries
    // =========================================================================

    @Test
    fun nanAndInfinityGuard_handlesInfiniteWidthsZeroWidthsZeroDurationEmptyTiersAndBadMeasurer() {
        val normalDoc = TextGridDocument(
            name = "normal",
            xmin = 0.0,
            xmax = 0.84,
            tiers = listOf(
                TextGridTier.IntervalTier(
                    name = "words",
                    xmin = 0.0,
                    xmax = 0.84,
                    intervals = listOf(
                        TextGridInterval(0.0, 0.085, ""),
                        TextGridInterval(0.085, 0.768, "中国"),
                        TextGridInterval(0.768, 0.84, "")
                    )
                )
            )
        )
        val zeroDurDoc = TextGridDocument(
            name = "zero_dur",
            xmin = 0.0,
            xmax = 0.0,
            tiers = listOf(
                TextGridTier.IntervalTier(
                    name = "words",
                    xmin = 0.0,
                    xmax = 0.0,
                    intervals = listOf(TextGridInterval(0.0, 0.0, "empty_span"))
                )
            )
        )
        val nonFiniteDoc = TextGridDocument(
            name = "nan_doc",
            xmin = Double.NaN,
            xmax = Double.POSITIVE_INFINITY,
            tiers = listOf(
                TextGridTier.IntervalTier(
                    name = "broken",
                    xmin = Double.NaN,
                    xmax = Double.POSITIVE_INFINITY,
                    intervals = listOf(TextGridInterval(Double.NaN, Double.POSITIVE_INFINITY, "bad"))
                ),
                TextGridTier.TextTier(
                    name = "empty_pts",
                    xmin = 0.0,
                    xmax = 0.0,
                    points = listOf(TextGridPoint(Double.NaN, "nan_pt"))
                )
            )
        )
        val emptyTiersDoc = TextGridDocument(
            name = "no_tiers",
            xmin = 0.0,
            xmax = 0.0,
            tiers = emptyList()
        )
        val emptyIntervalTierDoc = TextGridDocument(
            name = "empty_iv_tier",
            xmin = 0.0,
            xmax = 1.0,
            tiers = listOf(TextGridTier.IntervalTier("blank", 0.0, 1.0, emptyList()))
        )

        val brokenMeasurer = TextWidthMeasurer { _, _, _ -> Float.NaN }
        val infMeasurer = TextWidthMeasurer { _, _, _ -> Float.POSITIVE_INFINITY }

        val badWidths = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -120f)
        val badScales = listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -2f)
        val docs = listOf(normalDoc, zeroDurDoc, nonFiniteDoc, emptyTiersDoc, emptyIntervalTierDoc)

        for (doc in docs) {
            for (wrap in TextGridWrapMode.entries) {
                for (scale in TextGridScaleMode.entries) {
                    for (w in badWidths) {
                        val layout = DualScaleLayoutEngine.computeLayout(
                            doc = doc,
                            options = DualScaleLayoutOptions(
                                wrapMode = wrap,
                                targetWidthDp = w,
                                scaleMode = scale,
                                fontScale = badScales[(wrap.ordinal + scale.ordinal) % badScales.size],
                                compactMode = wrap == TextGridWrapMode.SINGLE
                            ),
                            measurer = if (scale == TextGridScaleMode.LINEAR) brokenMeasurer else infMeasurer
                        )
                        assertLayoutStrictlyFinite(layout)
                        val svg = DualScaleLayoutEngine.exportStaticSvg(doc, layout)
                        assertFalse("SVG must never contain NaN", svg.contains("NaN"))
                        assertFalse("SVG must never contain Infinity", svg.contains("Infinity"))
                    }
                }
            }
        }

        // Verify safe math primitives directly
        assertEquals(0, TextGridSafeMath.safeRoundToInt(Float.NaN))
        assertEquals(7, TextGridSafeMath.safeRoundToInt(Float.POSITIVE_INFINITY, fallback = 7))
        assertEquals(0, TextGridSafeMath.safeRoundToInt(Double.NaN))
        assertEquals(0L, TextGridSafeMath.safeRoundToLong(Double.NEGATIVE_INFINITY))
        assertEquals(220f, TextGridSafeMath.safeCoerceIn(Float.NaN, 220f, 4096f, 220f), 0.001f)
    }

    private fun assertLayoutStrictlyFinite(layout: ScoreLayout) {
        assertTrue("widthDp must be finite & positive: ${layout.widthDp}", layout.widthDp.isFinite() && layout.widthDp >= 220f)
        assertTrue("heightDp must be finite & positive: ${layout.heightDp}", layout.heightDp.isFinite() && layout.heightDp >= 84f)
        assertTrue("Must have at least one band", layout.bands.isNotEmpty())
        for (band in layout.bands) {
            assertTrue("bandContentWidth must be finite & positive", band.bandContentWidth.isFinite() && band.bandContentWidth > 0f)
            assertTrue("originX must be finite", band.originX.isFinite())
            assertTrue("linearY/Height must be finite", band.linearY.isFinite() && band.linearHeight.isFinite())
            assertTrue("tiersTop/Bottom must be finite", band.tiersTopY.isFinite() && band.tiersBottomY.isFinite())
            for (col in band.spineCols) {
                assertTrue("spineCol.x must be finite", col.x.isFinite())
                assertTrue("spineCol.labelLeftX must be finite", col.labelLeftX.isFinite())
                assertTrue("spineCol.labelWidth must be finite", col.labelWidth.isFinite())
            }
            for (seg in band.linearSegs) {
                assertTrue("linearSeg x0/x1 must be finite", seg.x0.isFinite() && seg.x1.isFinite())
                assertTrue("linearSeg x1 >= x0", seg.x1 >= seg.x0)
            }
            for (tl in band.tiersLayout.values) {
                assertTrue("tier y/height must be finite", tl.y.isFinite() && tl.height.isFinite() && tl.height > 0f)
                for (iv in tl.visibleIntervals) {
                    assertTrue("interval geometry must be finite", iv.x0.isFinite() && iv.x1.isFinite() && iv.width.isFinite())
                    assertTrue("interval stretchRatio must be finite", iv.stretchRatio.isFinite())
                }
                for (pt in tl.visiblePoints) {
                    assertTrue("point geometry must be finite", pt.x.isFinite() && pt.boxX.isFinite() && pt.boxWidth.isFinite())
                }
            }
        }
    }
}
