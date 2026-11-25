package com.example.textgrid.model

import kotlin.math.abs

/**
 * Supported Praat TextGrid serialization variants.
 */
enum class TextGridFormatVariant(val id: String) {
    LONG_TEXT("LONG_TEXT"),
    SHORT_TEXT("SHORT_TEXT"),
    BINARY("BINARY"),
    CHRONOLOGICAL("CHRONOLOGICAL");

    companion object {
        fun fromId(id: String?): TextGridFormatVariant {
            if (id.isNullOrBlank()) return LONG_TEXT
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: LONG_TEXT
        }
    }
}

/**
 * Tier classification strictly aligned with Praat's `IntervalTier` and `TextTier`.
 * Note: Praat never registers `"PointTier"`; serialization always emits `"TextTier"`.
 */
enum class TextGridTierKind(val praatClassName: String) {
    INTERVAL_TIER("IntervalTier"),
    TEXT_TIER("TextTier")
}

/**
 * Closed-open interval `[xmin, xmax)` with verbatim Unicode label text.
 */
data class TextGridInterval(
    val xmin: Double,
    val xmax: Double,
    val text: String
) {
    val duration: Double
        get() = xmax - xmin

    val isEmptyText: Boolean
        get() = text.isEmpty()

    val isZeroDuration: Boolean
        get() = abs(xmax - xmin) <= 1e-12
}

/**
 * Instantaneous point annotation (`number` = timestamp in seconds, `mark` = label text).
 */
data class TextGridPoint(
    val number: Double,
    val mark: String
) {
    val isEmptyMark: Boolean
        get() = mark.isEmpty()
}

/**
 * Polymorphic Praat TextGrid tier (`IntervalTier` or `TextTier`).
 */
sealed interface TextGridTier {
    val name: String
    val xmin: Double
    val xmax: Double
    val kind: TextGridTierKind
    /** Optional 1-based original tier index preserved when slicing or reordering views. */
    val displayNum: Int?

    data class IntervalTier(
        override val name: String,
        override val xmin: Double,
        override val xmax: Double,
        val intervals: List<TextGridInterval>,
        override val displayNum: Int? = null
    ) : TextGridTier {
        override val kind: TextGridTierKind
            get() = TextGridTierKind.INTERVAL_TIER
    }

    data class TextTier(
        override val name: String,
        override val xmin: Double,
        override val xmax: Double,
        val points: List<TextGridPoint>,
        override val displayNum: Int? = null
    ) : TextGridTier {
        override val kind: TextGridTierKind
            get() = TextGridTierKind.TEXT_TIER
    }
}

/**
 * Complete Praat `TextGrid` document (format version 0).
 */
data class TextGridDocument(
    val name: String = "",
    val xmin: Double,
    val xmax: Double,
    val tiers: List<TextGridTier>
) {
    val duration: Double
        get() = if (xmin.isFinite() && xmax.isFinite() && xmax > xmin) xmax - xmin else 0.0

    val tierCount: Int
        get() = tiers.size

    val intervalTierCount: Int
        get() = tiers.count { it is TextGridTier.IntervalTier }

    val textTierCount: Int
        get() = tiers.count { it is TextGridTier.TextTier }

    val totalIntervalCount: Int
        get() = tiers.sumOf { (it as? TextGridTier.IntervalTier)?.intervals?.size ?: 0 }

    val totalPointCount: Int
        get() = tiers.sumOf { (it as? TextGridTier.TextTier)?.points?.size ?: 0 }

    companion object {
        fun empty(
            xmax: Double = 1.0,
            xmin: Double = 0.0,
            name: String = "untitled",
            tierName: String = "words"
        ): TextGridDocument {
            val safeXmin = if (xmin.isFinite()) xmin else 0.0
            val safeXmax = if (xmax.isFinite() && xmax > safeXmin) xmax else safeXmin + 1.0
            return TextGridDocument(
                name = name,
                xmin = safeXmin,
                xmax = safeXmax,
                tiers = listOf(
                    TextGridTier.IntervalTier(
                        name = tierName,
                        xmin = safeXmin,
                        xmax = safeXmax,
                        intervals = listOf(TextGridInterval(safeXmin, safeXmax, ""))
                    )
                )
            )
        }
    }
}

/**
 * Closed time window `[start, end]` in seconds (used for view domains and playback ranges).
 */
data class TimeRange(
    val start: Double,
    val end: Double
) {
    val duration: Double
        get() = (end - start).coerceAtLeast(0.0)

    val isValid: Boolean
        get() = start.isFinite() && end.isFinite() && end > start
}

/**
 * Interactive selection inside a Dual-Scale TextGrid editor.
 */
sealed interface TextGridSelection {
    data class Interval(val tierIndex: Int, val itemIndex: Int) : TextGridSelection
    data class Point(val tierIndex: Int, val itemIndex: Int) : TextGridSelection
    data class Boundary(val tierIndex: Int, val time: Double) : TextGridSelection
    data class Spine(val time: Double) : TextGridSelection
    data class Tier(val tierIndex: Int) : TextGridSelection

    val tierIndexOrNull: Int?
        get() = when (this) {
            is Interval -> tierIndex
            is Point -> tierIndex
            is Boundary -> tierIndex
            is Tier -> tierIndex
            is Spine -> null
        }
}

/**
 * Source location span for a Tier, Interval, or Point in normalized `\n` TextGrid source code.
 * Line and column numbers are 1-based; character offsets are 0-based indices into the normalized source string.
 */
data class SourceLineSpan(
    val startLine: Int,
    val endLine: Int,
    val textLine: Int? = null,
    val textQuoteStartCol: Int? = null,
    val textQuoteEndCol: Int? = null,
    val textCharStartOffset: Int? = null,
    val textCharEndOffset: Int? = null
)

/**
 * Span entry for a single tier and all of its indexed child items (intervals or points).
 */
data class TierSpanEntry(
    val tierIndex: Int,
    val headerSpan: SourceLineSpan,
    val itemSpans: Map<Int, SourceLineSpan> = emptyMap()
)

/**
 * Bidirectional source-to-model mapping used for cursor synchronization and playback line highlighting.
 */
data class SourceSpanMap(
    val lineCount: Int = 0,
    val tiers: Map<Int, TierSpanEntry> = emptyMap()
) {
    fun spanForTier(tierIndex: Int): SourceLineSpan? = tiers[tierIndex]?.headerSpan

    fun spanForItem(tierIndex: Int, itemIndex: Int): SourceLineSpan? =
        tiers[tierIndex]?.itemSpans?.get(itemIndex)

    fun spanForSelection(selection: TextGridSelection?, doc: TextGridDocument? = null): SourceLineSpan? {
        if (selection == null) return null
        return when (selection) {
            is TextGridSelection.Interval -> spanForItem(selection.tierIndex, selection.itemIndex)
                ?: spanForTier(selection.tierIndex)

            is TextGridSelection.Point -> spanForItem(selection.tierIndex, selection.itemIndex)
                ?: spanForTier(selection.tierIndex)

            is TextGridSelection.Tier -> spanForTier(selection.tierIndex)

            is TextGridSelection.Boundary -> {
                val tier = doc?.tiers?.getOrNull(selection.tierIndex) as? TextGridTier.IntervalTier
                if (tier != null) {
                    val rightIdx = tier.intervals.indexOfFirst { abs(it.xmin - selection.time) <= 1e-9 }
                    val leftIdx = tier.intervals.indexOfFirst { abs(it.xmax - selection.time) <= 1e-9 }
                    val idx = if (rightIdx >= 0) rightIdx else leftIdx
                    if (idx >= 0) spanForItem(selection.tierIndex, idx) else spanForTier(selection.tierIndex)
                } else {
                    spanForTier(selection.tierIndex)
                }
            }

            is TextGridSelection.Spine -> {
                if (doc != null) {
                    for ((ti, tier) in doc.tiers.withIndex()) {
                        when (tier) {
                            is TextGridTier.IntervalTier -> {
                                val idx = tier.intervals.indexOfFirst {
                                    abs(it.xmin - selection.time) <= 1e-9 || abs(it.xmax - selection.time) <= 1e-9
                                }
                                if (idx >= 0) return spanForItem(ti, idx)
                            }
                            is TextGridTier.TextTier -> {
                                val idx = tier.points.indexOfFirst { abs(it.number - selection.time) <= 1e-9 }
                                if (idx >= 0) return spanForItem(ti, idx)
                            }
                        }
                    }
                }
                null
            }
        }
    }

    /**
     * Reverse-maps a 1-based line number in source mode to the most specific [TextGridSelection].
     */
    fun selectionForLine(line1Based: Int, doc: TextGridDocument): TextGridSelection? {
        if (line1Based <= 0) return null
        // 1. Check exact item spans first (most specific)
        for ((tierIdx, entry) in tiers) {
            val tier = doc.tiers.getOrNull(tierIdx) ?: continue
            for ((itemIdx, span) in entry.itemSpans) {
                if (line1Based in span.startLine..span.endLine) {
                    return when (tier) {
                        is TextGridTier.IntervalTier ->
                            if (itemIdx in tier.intervals.indices) TextGridSelection.Interval(tierIdx, itemIdx) else null
                        is TextGridTier.TextTier ->
                            if (itemIdx in tier.points.indices) TextGridSelection.Point(tierIdx, itemIdx) else null
                    }
                }
            }
        }
        // 2. Fallback to tier header span
        for ((tierIdx, entry) in tiers) {
            if (tierIdx in doc.tiers.indices && line1Based in entry.headerSpan.startLine..entry.headerSpan.endLine) {
                return TextGridSelection.Tier(tierIdx)
            }
        }
        return null
    }

    /**
     * Returns 1-based source lines covering [timeSec] across tiers (preferring [activeTierIndex] first).
     */
    fun linesForTime(
        timeSec: Double,
        doc: TextGridDocument,
        activeTierIndex: Int? = null
    ): List<Int> {
        if (!timeSec.isFinite() || tiers.isEmpty()) return emptyList()
        val orderedTierIndices = buildList {
            if (activeTierIndex != null && activeTierIndex in doc.tiers.indices) {
                add(activeTierIndex)
            }
            for (ti in doc.tiers.indices) {
                if (ti != activeTierIndex) add(ti)
            }
        }
        val lines = LinkedHashSet<Int>()
        for (ti in orderedTierIndices) {
            val tier = doc.tiers[ti]
            when (tier) {
                is TextGridTier.IntervalTier -> {
                    val idx = tier.intervals.indexOfFirst { iv ->
                        timeSec >= iv.xmin - 1e-9 && (timeSec < iv.xmax || (abs(timeSec - iv.xmax) <= 1e-9 && iv == tier.intervals.lastOrNull()))
                    }
                    if (idx >= 0) {
                        val span = spanForItem(ti, idx)
                        if (span != null) {
                            for (ln in span.startLine..span.endLine) lines.add(ln)
                        }
                    }
                }
                is TextGridTier.TextTier -> {
                    val idx = tier.points.indexOfFirst { abs(it.number - timeSec) <= 0.025 }
                    if (idx >= 0) {
                        val span = spanForItem(ti, idx)
                        if (span != null) {
                            for (ln in span.startLine..span.endLine) lines.add(ln)
                        }
                    }
                }
            }
        }
        return lines.toList()
    }

    fun primaryLineForTime(
        timeSec: Double,
        doc: TextGridDocument,
        activeTierIndex: Int? = null
    ): Int? = linesForTime(timeSec, doc, activeTierIndex).firstOrNull()
}

enum class DiagnosticSeverity {
    FATAL_SYNTAX,
    NON_FATAL_ANOMALY
}

enum class DiagnosticCode {
    INVALID_HEADER,
    UNSUPPORTED_VERSION,
    UNEXPECTED_TOKEN,
    UNCLOSED_STRING,
    UNCLOSED_ENUM,
    INVALID_CHAR_AFTER_QUOTE,
    INVALID_NUMBER,
    INVALID_ENUM,
    ABSENT_TIERS,
    NEGATIVE_SIZE,
    UNKNOWN_TIER_CLASS,
    INVERTED_DOMAIN,
    EARLY_EOF,
    BINARY_ERROR,
    COLLECTION_NO_TEXTGRID,
    GAP,
    OVERLAP,
    ZERO_DURATION,
    MICRO_SEGMENT,
    OUT_OF_DOMAIN,
    POINT_OUT_OF_DOMAIN,
    EMPTY_INTERVAL_TIER_REPAIRED
}

/**
 * Syntax error or structural anomaly diagnostic with precise 1-based line/column coordinates.
 */
data class TextGridDiagnostic(
    val severity: DiagnosticSeverity,
    val code: DiagnosticCode,
    val message: String,
    val line: Int = 1,
    val column: Int = 1,
    val tierIndex: Int? = null,
    val itemIndex: Int? = null
)

enum class TextGridAnomalyKind {
    GAP,
    OVERLAP,
    ZERO_DURATION,
    MICRO_SEGMENT,
    OUT_OF_DOMAIN,
    POINT_OUT_OF_DOMAIN
}

/**
 * Non-fatal structural anomaly preserved under Postel's Law for visual warning rendering.
 */
data class TextGridAnomaly(
    val kind: TextGridAnomalyKind,
    val tierIndex: Int,
    val itemIndex: Int? = null,
    val startTime: Double,
    val endTime: Double,
    val message: String
)

data class TierAnomalySummary(
    val tierIndex: Int,
    val gapCount: Int,
    val overlapCount: Int,
    val zeroDurationCount: Int,
    val microSegmentCount: Int,
    val outOfDomain: Boolean,
    val pointOutOfDomainCount: Int
) {
    val hasAnyIssue: Boolean
        get() = gapCount > 0 || overlapCount > 0 || zeroDurationCount > 0 || outOfDomain || pointOutOfDomainCount > 0
}

/**
 * Unified result from [com.example.textgrid.codec.TextGridCodec].
 */
data class TextGridParseResult(
    val document: TextGridDocument?,
    val collectionDocuments: List<TextGridDocument> = emptyList(),
    val detectedVariant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT,
    val detectedEncoding: String = "UTF-8",
    val normalizedSource: String? = null,
    val spanMap: SourceSpanMap = SourceSpanMap(),
    val diagnostics: List<TextGridDiagnostic> = emptyList(),
    val anomalies: List<TextGridAnomaly> = emptyList()
) {
    val isSuccess: Boolean
        get() = document != null && diagnostics.none { it.severity == DiagnosticSeverity.FATAL_SYNTAX }

    val fatalDiagnostics: List<TextGridDiagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.FATAL_SYNTAX }

    val nonFatalDiagnostics: List<TextGridDiagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.NON_FATAL_ANOMALY }
}
