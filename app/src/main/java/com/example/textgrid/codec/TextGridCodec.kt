package com.example.textgrid.codec

import com.example.textgrid.model.DiagnosticCode
import com.example.textgrid.model.DiagnosticSeverity
import com.example.textgrid.model.SourceLineSpan
import com.example.textgrid.model.SourceSpanMap
import com.example.textgrid.model.TextGridAnomaly
import com.example.textgrid.model.TextGridAnomalyKind
import com.example.textgrid.model.TextGridDiagnostic
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.model.TextGridInterval
import com.example.textgrid.model.TextGridParseResult
import com.example.textgrid.model.TextGridPoint
import com.example.textgrid.model.TextGridTier
import com.example.textgrid.model.TierAnomalySummary
import com.example.textgrid.model.TierSpanEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Pure Kotlin/JVM Praat `.TextGrid` & `.Collection` codec implementing the official Praat `oo`
 * serialization specification:
 * - Formats: Long Text, Short Text, Hybrid Text, Binary (`ooBinaryFile` Big-Endian),
 *   Chronological TextGrid, Gzip (`.gz`), standalone `IntervalTier`/`TextTier` (and legacy `MarkTier`),
 *   and `.Collection` (extracting embedded `TextGrid` objects alongside `Sound`/`Table`/nested `Collection`).
 * - Encoding: BOM auto-detection (`UTF-16BE`, `UTF-16LE`, `UTF-8`), NUL stripping + strict UTF-8
 *   validation with Windows-1252 (CP1252) fallback, unified `\n` line endings; writes BOM-less UTF-8 by default.
 * - Postel's Law: rejects fatal syntax errors with exact 1-based line/column diagnostics while preserving
 *   non-fatal structural anomalies (gaps, overlaps, zero-duration, out-of-domain) and producing a [SourceSpanMap].
 */
object TextGridCodec {

    private const val BINARY_MAGIC = "ooBinaryFile"
    private const val CHRONOLOGICAL_HEADER = "\"Praat chronological TextGrid text file\""

    private val INT_REGEX = Regex("^[+-]?[0-9]+$")
    private val DEC_FLOAT_REGEX = Regex("^[+-]?[0-9]+(?:\\.[0-9]*)?(?:[eE][+-]?[0-9]+)?%?$")
    private val HEX_FLOAT_REGEX = Regex("^[+-]?0[xX][0-9a-fA-F]+(?:\\.[0-9a-fA-F]*)?(?:[pP][+-]?[0-9]+)?$")

    // Windows-1252 mapping table for bytes 0x80..0x9F (undefined slots 0x81, 0x8D, 0x8F, 0x90, 0x9D stay identity)
    private val CP1252_HIGH_MAP: CharArray = charArrayOf(
        '\u20AC', '\u0081', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021',
        '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', '\u008D', '\u017D', '\u008F',
        '\u0090', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
        '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\u009D', '\u017E', '\u0178'
    )

    // =========================================================================
    // Public Parsing API
    // =========================================================================

    fun parseFile(file: File): TextGridParseResult {
        if (!file.exists() || !file.isFile) {
            return fatalResult(
                DiagnosticCode.INVALID_HEADER,
                "File does not exist: ${file.name}",
                line = 1,
                column = 1
            )
        }
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return fatalResult(
                DiagnosticCode.INVALID_HEADER,
                "Failed to read file: ${e.message ?: e.javaClass.simpleName}",
                line = 1,
                column = 1
            )
        }
        val baseName = file.name
            .removeSuffix(".gz")
            .removeSuffix(".GZ")
            .substringBeforeLast('.')
        return parseBytes(bytes, defaultName = baseName)
    }

    fun parseBytes(bytes: ByteArray, defaultName: String = ""): TextGridParseResult {
        if (bytes.isEmpty()) {
            return fatalResult(DiagnosticCode.EARLY_EOF, "Empty TextGrid input", 1, 1)
        }

        // 1. Gzip transparent decompression (0x1F 0x8B)
        if (bytes.size >= 10 && (bytes[0].toInt() and 0xFF) == 0x1F && (bytes[1].toInt() and 0xFF) == 0x8B) {
            val inflated = try {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } catch (e: Exception) {
                return fatalResult(
                    DiagnosticCode.INVALID_HEADER,
                    "Failed to decompress Gzip stream: ${e.message ?: "corrupt gzip"}",
                    1,
                    1
                )
            }
            return parseBytes(inflated, defaultName = defaultName)
        }

        // 2. Binary container detection (`ooBinaryFile` or `"BinaryFile"` in header < 40 bytes)
        if (isBinaryContainer(bytes)) {
            return parseBinary(bytes, defaultName = defaultName)
        }

        // 3. Text decoding with BOM / UTF-8 / Windows-1252 detection & line normalization
        val (decodedText, encodingName) = decodeTextBytes(bytes)
        return parseText(decodedText, defaultName = defaultName, detectedEncoding = encodingName)
    }

    fun parseText(
        rawText: String,
        defaultName: String = "",
        detectedEncoding: String = "UTF-8"
    ): TextGridParseResult {
        val stripped = if (rawText.startsWith("\uFEFF")) rawText.substring(1) else rawText
        val normalized = normalizeLineEndings(stripped, isUtf16 = false)
        if (normalized.isBlank()) {
            return fatalResult(
                DiagnosticCode.EARLY_EOF,
                "Empty TextGrid text",
                line = 1,
                column = 1,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding
            )
        }

        // Check Chronological TextGrid header (allow < 100 bytes and leading whitespace/BOM)
        val trimmedStart = normalized.trimStart()
        if (trimmedStart.startsWith(CHRONOLOGICAL_HEADER)) {
            return parseChronologicalText(normalized, defaultName, detectedEncoding)
        }

        val firstNl = normalized.indexOf('\n')
        val line1 = if (firstNl < 0) normalized else normalized.substring(0, firstNl)
        if (line1.contains("ooText2File")) {
            return fatalResult(
                DiagnosticCode.UNSUPPORTED_VERSION,
                "Unsupported future Praat format: ooText2File",
                line = 1,
                column = 1,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding
            )
        }
        if (!line1.contains("ooTextFile")) {
            return fatalResult(
                DiagnosticCode.INVALID_HEADER,
                "Missing 'ooTextFile' header on line 1",
                line = 1,
                column = 1,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding
            )
        }
        if (firstNl < 0) {
            return fatalResult(
                DiagnosticCode.EARLY_EOF,
                "Unexpected end of text after file header",
                line = 1,
                column = line1.length + 1,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding
            )
        }

        val detectedVariant = detectTextVariant(normalized)
        val scanner = AbcioTextScanner(normalized, startPos = firstNl + 1, startLine = 2, startCol = 1)
        val nonFatalDiagnostics = mutableListOf<TextGridDiagnostic>()

        return try {
            val classToken = scanner.readStringWithSpan()
            val (className, formatVersion) = splitClassToken(classToken.value, classToken.startLine, classToken.startCol)

            when (className) {
                "TextGrid" -> {
                    if (formatVersion > 0) {
                        throw ParseSyntaxException(
                            DiagnosticCode.UNSUPPORTED_VERSION,
                            "Unsupported TextGrid format version: $formatVersion",
                            classToken.startLine,
                            classToken.startCol
                        )
                    }
                    val (doc, spanMap) = readTextGridBodyFromText(
                        scanner = scanner,
                        docName = defaultName,
                        recordSpans = true,
                        nonFatalDiagnostics = nonFatalDiagnostics
                    )
                    val anomalies = detectAnomalies(doc)
                    val allDiags = nonFatalDiagnostics + anomaliesToDiagnostics(anomalies, spanMap)
                    TextGridParseResult(
                        document = doc,
                        collectionDocuments = listOf(doc),
                        detectedVariant = detectedVariant,
                        detectedEncoding = detectedEncoding,
                        normalizedSource = normalized,
                        spanMap = spanMap,
                        diagnostics = allDiags,
                        anomalies = anomalies
                    )
                }

                "IntervalTier" -> {
                    if (formatVersion > 0) {
                        throw ParseSyntaxException(
                            DiagnosticCode.UNSUPPORTED_VERSION,
                            "Unsupported IntervalTier format version: $formatVersion",
                            classToken.startLine,
                            classToken.startCol
                        )
                    }
                    val tierStartLine = classToken.startLine
                    val (tier, itemSpans) = readIntervalTierBodyFromText(
                        scanner = scanner,
                        tierName = defaultName.ifEmpty { "IntervalTier" },
                        tierIndex = 0,
                        repairEmpty = false,
                        recordSpans = true,
                        nonFatalDiagnostics = nonFatalDiagnostics
                    )
                    val doc = TextGridDocument(
                        name = defaultName,
                        xmin = tier.xmin,
                        xmax = tier.xmax,
                        tiers = listOf(tier)
                    )
                    val spanMap = SourceSpanMap(
                        lineCount = countLines(normalized),
                        tiers = mapOf(
                            0 to TierSpanEntry(
                                tierIndex = 0,
                                headerSpan = SourceLineSpan(tierStartLine, scanner.currentLine),
                                itemSpans = itemSpans
                            )
                        )
                    )
                    val anomalies = detectAnomalies(doc)
                    TextGridParseResult(
                        document = doc,
                        collectionDocuments = listOf(doc),
                        detectedVariant = detectedVariant,
                        detectedEncoding = detectedEncoding,
                        normalizedSource = normalized,
                        spanMap = spanMap,
                        diagnostics = nonFatalDiagnostics + anomaliesToDiagnostics(anomalies, spanMap),
                        anomalies = anomalies
                    )
                }

                "TextTier", "MarkTier" -> {
                    if (formatVersion > 0) {
                        throw ParseSyntaxException(
                            DiagnosticCode.UNSUPPORTED_VERSION,
                            "Unsupported $className format version: $formatVersion",
                            classToken.startLine,
                            classToken.startCol
                        )
                    }
                    val tierStartLine = classToken.startLine
                    val (tier, itemSpans) = readTextTierBodyFromText(
                        scanner = scanner,
                        tierName = defaultName.ifEmpty { "TextTier" },
                        recordSpans = true
                    )
                    val doc = TextGridDocument(
                        name = defaultName,
                        xmin = tier.xmin,
                        xmax = tier.xmax,
                        tiers = listOf(tier)
                    )
                    val spanMap = SourceSpanMap(
                        lineCount = countLines(normalized),
                        tiers = mapOf(
                            0 to TierSpanEntry(
                                tierIndex = 0,
                                headerSpan = SourceLineSpan(tierStartLine, scanner.currentLine),
                                itemSpans = itemSpans
                            )
                        )
                    )
                    val anomalies = detectAnomalies(doc)
                    TextGridParseResult(
                        document = doc,
                        collectionDocuments = listOf(doc),
                        detectedVariant = detectedVariant,
                        detectedEncoding = detectedEncoding,
                        normalizedSource = normalized,
                        spanMap = spanMap,
                        diagnostics = nonFatalDiagnostics + anomaliesToDiagnostics(anomalies, spanMap),
                        anomalies = anomalies
                    )
                }

                "Collection" -> {
                    if (formatVersion > 0) {
                        throw ParseSyntaxException(
                            DiagnosticCode.UNSUPPORTED_VERSION,
                            "Unsupported Collection format version: $formatVersion",
                            classToken.startLine,
                            classToken.startCol
                        )
                    }
                    val extracted = mutableListOf<Pair<TextGridDocument, SourceSpanMap>>()
                    readCollectionFromText(scanner, extracted, nonFatalDiagnostics)
                    if (extracted.isEmpty()) {
                        fatalResult(
                            DiagnosticCode.COLLECTION_NO_TEXTGRID,
                            "Praat Collection contains no TextGrid object",
                            line = 1,
                            column = 1,
                            normalizedSource = normalized,
                            detectedEncoding = detectedEncoding,
                            detectedVariant = detectedVariant
                        )
                    } else {
                        val primary = extracted.first()
                        val anomalies = detectAnomalies(primary.first)
                        TextGridParseResult(
                            document = primary.first,
                            collectionDocuments = extracted.map { it.first },
                            detectedVariant = detectedVariant,
                            detectedEncoding = detectedEncoding,
                            normalizedSource = normalized,
                            spanMap = primary.second,
                            diagnostics = nonFatalDiagnostics + anomaliesToDiagnostics(anomalies, primary.second),
                            anomalies = anomalies
                        )
                    }
                }

                else -> throw ParseSyntaxException(
                    DiagnosticCode.UNKNOWN_TIER_CLASS,
                    "Unsupported Praat object class \"$className\"",
                    classToken.startLine,
                    classToken.startCol
                )
            }
        } catch (e: ParseSyntaxException) {
            fatalResult(
                code = e.code,
                message = e.message ?: "Syntax error",
                line = e.line,
                column = e.column,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding,
                detectedVariant = detectedVariant
            )
        } catch (e: Exception) {
            fatalResult(
                code = DiagnosticCode.UNEXPECTED_TOKEN,
                message = e.message ?: "Unexpected parse failure",
                line = scanner.currentLine,
                column = scanner.currentCol,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding,
                detectedVariant = detectedVariant
            )
        }
    }

    // =========================================================================
    // Public Serialization API
    // =========================================================================

    fun serialize(
        doc: TextGridDocument,
        variant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT,
        timeDecimals: Int? = null
    ): String {
        val textVariant = if (variant == TextGridFormatVariant.BINARY) TextGridFormatVariant.LONG_TEXT else variant
        return serializeWithSpanMap(doc, textVariant, timeDecimals).first
    }

    fun serializeWithSpanMap(
        doc: TextGridDocument,
        variant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT,
        timeDecimals: Int? = null
    ): Pair<String, SourceSpanMap> {
        return when (variant) {
            TextGridFormatVariant.LONG_TEXT,
            TextGridFormatVariant.BINARY -> serializeLongTextWithSpanMap(doc, timeDecimals)
            TextGridFormatVariant.SHORT_TEXT -> serializeShortTextWithSpanMap(doc, timeDecimals)
            TextGridFormatVariant.CHRONOLOGICAL -> serializeChronologicalWithSpanMap(doc, timeDecimals)
        }
    }

    /**
     * Serializes [doc] to raw bytes in any supported format variant.
     * - Default text output is BOM-less UTF-8 with `\n`.
     * - When [utf16Be] is true (ignored for `BINARY` and `CHRONOLOGICAL`), writes `0xFE 0xFF` BOM + UTF-16BE.
     * - For `CHRONOLOGICAL`, NEVER writes a UTF-8 BOM (strict Praat compatibility).
     */
    fun serializeToBytes(
        doc: TextGridDocument,
        variant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT,
        timeDecimals: Int? = null,
        utf16Be: Boolean = false
    ): ByteArray {
        if (variant == TextGridFormatVariant.BINARY) {
            val baos = ByteArrayOutputStream()
            writeBinaryTextGrid(doc, baos)
            return baos.toByteArray()
        }
        val text = serialize(doc, variant, timeDecimals)
        if (utf16Be && variant != TextGridFormatVariant.CHRONOLOGICAL) {
            val payload = text.toByteArray(Charsets.UTF_16BE)
            val out = ByteArray(2 + payload.size)
            out[0] = 0xFE.toByte()
            out[1] = 0xFF.toByte()
            System.arraycopy(payload, 0, out, 2, payload.size)
            return out
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    // =========================================================================
    // Anomaly Detection (Postel Non-Fatal Structural Scan)
    // =========================================================================

    fun detectAnomalies(doc: TextGridDocument): List<TextGridAnomaly> {
        val out = mutableListOf<TextGridAnomaly>()
        val eps = 1e-9

        doc.tiers.forEachIndexed { tierIdx, tier ->
            if (abs(tier.xmin - doc.xmin) > eps || abs(tier.xmax - doc.xmax) > eps) {
                out.add(
                    TextGridAnomaly(
                        kind = TextGridAnomalyKind.OUT_OF_DOMAIN,
                        tierIndex = tierIdx,
                        itemIndex = null,
                        startTime = tier.xmin,
                        endTime = tier.xmax,
                        message = "Tier '${tier.name}' domain [${formatPraatDouble(tier.xmin)}, ${formatPraatDouble(tier.xmax)}] differs from TextGrid [${formatPraatDouble(doc.xmin)}, ${formatPraatDouble(doc.xmax)}]"
                    )
                )
            }

            when (tier) {
                is TextGridTier.IntervalTier -> {
                    val intervals = tier.intervals
                    if (intervals.isEmpty()) {
                        out.add(
                            TextGridAnomaly(
                                kind = TextGridAnomalyKind.GAP,
                                tierIndex = tierIdx,
                                itemIndex = null,
                                startTime = tier.xmin,
                                endTime = tier.xmax,
                                message = "IntervalTier '${tier.name}' has 0 intervals"
                            )
                        )
                    } else {
                        val first = intervals.first()
                        if (first.xmin - tier.xmin > eps) {
                            out.add(
                                TextGridAnomaly(
                                    kind = TextGridAnomalyKind.GAP,
                                    tierIndex = tierIdx,
                                    itemIndex = 0,
                                    startTime = tier.xmin,
                                    endTime = first.xmin,
                                    message = "Leading gap on tier '${tier.name}' (${formatPraatDouble(tier.xmin)}..${formatPraatDouble(first.xmin)})"
                                )
                            )
                        }
                        for (i in intervals.indices) {
                            val iv = intervals[i]
                            val dur = iv.xmax - iv.xmin
                            if (abs(dur) <= 1e-12) {
                                out.add(
                                    TextGridAnomaly(
                                        kind = TextGridAnomalyKind.ZERO_DURATION,
                                        tierIndex = tierIdx,
                                        itemIndex = i,
                                        startTime = iv.xmin,
                                        endTime = iv.xmax,
                                        message = "Zero-duration interval at ${formatPraatDouble(iv.xmin)} on tier '${tier.name}'"
                                    )
                                )
                            } else if (dur > 0.0 && dur < 0.005) {
                                out.add(
                                    TextGridAnomaly(
                                        kind = TextGridAnomalyKind.MICRO_SEGMENT,
                                        tierIndex = tierIdx,
                                        itemIndex = i,
                                        startTime = iv.xmin,
                                        endTime = iv.xmax,
                                        message = "Micro-segment (<5ms) on tier '${tier.name}'"
                                    )
                                )
                            }
                            if (i > 0) {
                                val prev = intervals[i - 1]
                                val diff = iv.xmin - prev.xmax
                                if (diff > eps) {
                                    out.add(
                                        TextGridAnomaly(
                                            kind = TextGridAnomalyKind.GAP,
                                            tierIndex = tierIdx,
                                            itemIndex = i,
                                            startTime = prev.xmax,
                                            endTime = iv.xmin,
                                            message = "Gap between intervals $i and ${i + 1} on tier '${tier.name}'"
                                        )
                                    )
                                } else if (diff < -eps) {
                                    out.add(
                                        TextGridAnomaly(
                                            kind = TextGridAnomalyKind.OVERLAP,
                                            tierIndex = tierIdx,
                                            itemIndex = i,
                                            startTime = iv.xmin,
                                            endTime = prev.xmax,
                                            message = "Overlap between intervals $i and ${i + 1} on tier '${tier.name}'"
                                        )
                                    )
                                }
                            }
                        }
                        val last = intervals.last()
                        if (tier.xmax - last.xmax > eps) {
                            out.add(
                                TextGridAnomaly(
                                    kind = TextGridAnomalyKind.GAP,
                                    tierIndex = tierIdx,
                                    itemIndex = intervals.lastIndex,
                                    startTime = last.xmax,
                                    endTime = tier.xmax,
                                    message = "Trailing gap on tier '${tier.name}' (${formatPraatDouble(last.xmax)}..${formatPraatDouble(tier.xmax)})"
                                )
                            )
                        }
                    }
                }

                is TextGridTier.TextTier -> {
                    tier.points.forEachIndexed { ptIdx, pt ->
                        if (pt.number < tier.xmin - eps || pt.number > tier.xmax + eps) {
                            out.add(
                                TextGridAnomaly(
                                    kind = TextGridAnomalyKind.POINT_OUT_OF_DOMAIN,
                                    tierIndex = tierIdx,
                                    itemIndex = ptIdx,
                                    startTime = pt.number,
                                    endTime = pt.number,
                                    message = "Point at ${formatPraatDouble(pt.number)} outside tier '${tier.name}' domain"
                                )
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    fun summarizeTierAnomalies(doc: TextGridDocument): List<TierAnomalySummary> {
        val anomalies = detectAnomalies(doc)
        val byTier = anomalies.groupBy { it.tierIndex }
        return doc.tiers.indices.map { ti ->
            val list = byTier[ti].orEmpty()
            TierAnomalySummary(
                tierIndex = ti,
                gapCount = list.count { it.kind == TextGridAnomalyKind.GAP },
                overlapCount = list.count { it.kind == TextGridAnomalyKind.OVERLAP },
                zeroDurationCount = list.count { it.kind == TextGridAnomalyKind.ZERO_DURATION },
                microSegmentCount = list.count { it.kind == TextGridAnomalyKind.MICRO_SEGMENT },
                outOfDomain = list.any { it.kind == TextGridAnomalyKind.OUT_OF_DOMAIN },
                pointOutOfDomainCount = list.count { it.kind == TextGridAnomalyKind.POINT_OUT_OF_DOMAIN }
            )
        }
    }

    // =========================================================================
    // Formatting & Escaping Primitives
    // =========================================================================

    /**
     * Shortest round-trip IEEE-754 formatting matching Praat's `Melder_double`.
     * - Non-finite (`NaN`, `±Inf`) -> `--undefined--`
     * - When [timeDecimals] is non-null -> fixed decimal precision (`%.Nf`)
     * - Always uses `.` as decimal separator and never emits `.5` without leading `0`.
     */
    fun formatPraatDouble(value: Double, timeDecimals: Int? = null): String {
        if (!value.isFinite()) return "--undefined--"
        val clean = if (value == 0.0) 0.0 else value // eliminate -0.0
        if (timeDecimals != null) {
            val d = timeDecimals.coerceIn(0, 12)
            val s = String.format(Locale.US, "%.${d}f", clean)
            return if (s == "-0" || s.startsWith("-0.") && s.drop(3).all { it == '0' }) {
                s.removePrefix("-")
            } else {
                s
            }
        }
        if (clean == 0.0) return "0"
        // Try shortest %.15g, %.16g, %.17g verifying bit-exact round-trip equality
        for (prec in 15..17) {
            val cand = normalizeGFormat(String.format(Locale.US, "%.${prec}g", clean))
            val parsed = cand.toDoubleOrNull()
            if (parsed != null && parsed == clean) {
                return cand
            }
        }
        val raw = clean.toString().lowercase(Locale.US)
        return if (raw.endsWith(".0") && !raw.contains('e')) raw.dropLast(2) else raw
    }

    private fun normalizeGFormat(raw: String): String {
        val lower = raw.lowercase(Locale.US)
        val eIdx = lower.indexOf('e')
        val mantissa = if (eIdx >= 0) lower.substring(0, eIdx) else lower
        val exponent = if (eIdx >= 0) lower.substring(eIdx) else ""
        val trimmedMantissa = if (mantissa.contains('.')) {
            mantissa.trimEnd('0').trimEnd('.')
        } else {
            mantissa
        }
        val normalizedExp = if (exponent.isNotEmpty()) {
            val sign = if (exponent.length > 1 && (exponent[1] == '+' || exponent[1] == '-')) exponent[1].toString() else ""
            val digits = exponent.substring(if (sign.isNotEmpty()) 2 else 1).trimStart('0').ifEmpty { "0" }
            if (sign == "+") "e+$digits" else "e$sign$digits"
        } else {
            ""
        }
        val combined = "$trimmedMantissa$normalizedExp"
        return if (combined == "-0") "0" else combined
    }

    /**
     * Sole string escape rule in Praat text files: double literal `"` to `""`.
     * Backslashes `\` are NEVER escaped.
     */
    fun escapePraatString(s: String): String = s.replace("\"", "\"\"")

    // =========================================================================
    // Encoding Detection & Line Normalization
    // =========================================================================

    private fun isBinaryContainer(bytes: ByteArray): Boolean {
        if (bytes.size >= 12) {
            var isExactMagic = true
            for (i in 0 until 12) {
                if (bytes[i].toInt().toChar() != BINARY_MAGIC[i]) {
                    isExactMagic = false
                    break
                }
            }
            if (isExactMagic) return true
        }
        if (bytes.size > 13) {
            val headerLimit = minOf(bytes.size, 64)
            var nulPos = headerLimit
            for (i in 0 until headerLimit) {
                if (bytes[i].toInt() == 0) {
                    nulPos = i
                    break
                }
            }
            val headAscii = String(bytes, 0, nulPos, Charsets.ISO_8859_1)
            val idx = headAscii.indexOf("BinaryFile")
            if (idx in 0 until 40) return true
        }
        return false
    }

    fun decodeTextBytes(bytes: ByteArray): Pair<String, String> {
        // 1. UTF-16BE BOM: FE FF
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFE && (bytes[1].toInt() and 0xFF) == 0xFF) {
            val raw = decodeUtf16(bytes, offset = 2, bigEndian = true)
            return normalizeLineEndings(raw, isUtf16 = true) to "UTF-16BE"
        }
        // 2. UTF-16LE BOM: FF FE
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xFE) {
            val raw = decodeUtf16(bytes, offset = 2, bigEndian = false)
            return normalizeLineEndings(raw, isUtf16 = true) to "UTF-16LE"
        }
        // 3. UTF-8 BOM: EF BB BF
        val startOffset = if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) 3 else 0

        // Optional BOM-less UTF-16 heuristic before stripping NULs
        if (startOffset == 0 && bytes.size >= 24) {
            val bomlessUtf16 = detectBomlessUtf16(bytes)
            if (bomlessUtf16 != null) {
                val raw = decodeUtf16(bytes, offset = 0, bigEndian = bomlessUtf16)
                val enc = if (bomlessUtf16) "UTF-16BE" else "UTF-16LE"
                return normalizeLineEndings(raw, isUtf16 = true) to enc
            }
        }

        // 4. Strip NUL bytes, validate strict UTF-8, else fall back to Windows-1252
        val clean = stripNulBytes(bytes, startOffset)
        return if (isValidUtf8(clean)) {
            val decoded = String(clean, Charsets.UTF_8)
            val enc = if (startOffset == 3) "UTF-8-BOM" else "UTF-8"
            normalizeLineEndings(decoded, isUtf16 = false) to enc
        } else {
            val decoded = decodeWindows1252(clean)
            normalizeLineEndings(decoded, isUtf16 = false) to "Windows-1252"
        }
    }

    private fun detectBomlessUtf16(bytes: ByteArray): Boolean? {
        val sampleLen = minOf(bytes.size, 100)
        var evenZeros = 0
        var oddZeros = 0
        var i = 0
        while (i + 1 < sampleLen) {
            if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() != 0) evenZeros++
            if (bytes[i].toInt() != 0 && bytes[i + 1].toInt() == 0) oddZeros++
            i += 2
        }
        val pairs = sampleLen / 2
        return when {
            evenZeros >= pairs / 2 && evenZeros > oddZeros * 3 -> true // UTF-16BE
            oddZeros >= pairs / 2 && oddZeros > evenZeros * 3 -> false // UTF-16LE
            else -> null
        }
    }

    private fun decodeUtf16(bytes: ByteArray, offset: Int, bigEndian: Boolean): String {
        val len = (bytes.size - offset) and 0x7FFFFFFE.toInt()
        val sb = StringBuilder(len / 2)
        var i = offset
        val end = offset + len
        while (i < end) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val unit = if (bigEndian) (b0 shl 8) or b1 else (b1 shl 8) or b0
            i += 2
            if (unit in 0xD800..0xDBFF) {
                if (i + 1 < end) {
                    val nb0 = bytes[i].toInt() and 0xFF
                    val nb1 = bytes[i + 1].toInt() and 0xFF
                    val low = if (bigEndian) (nb0 shl 8) or nb1 else (nb1 shl 8) or nb0
                    if (low in 0xDC00..0xDFFF) {
                        i += 2
                        sb.append(unit.toChar())
                        sb.append(low.toChar())
                    } else {
                        sb.append('\uFFFD')
                    }
                } else {
                    sb.append('\uFFFD')
                }
            } else if (unit in 0xDC00..0xDFFF) {
                sb.append('\uFFFD')
            } else {
                sb.append(unit.toChar())
            }
        }
        return sb.toString()
    }

    private fun stripNulBytes(bytes: ByteArray, startOffset: Int): ByteArray {
        var hasNul = false
        for (i in startOffset until bytes.size) {
            if (bytes[i].toInt() == 0) {
                hasNul = true
                break
            }
        }
        if (!hasNul) {
            return if (startOffset == 0) bytes else bytes.copyOfRange(startOffset, bytes.size)
        }
        val out = ByteArrayOutputStream(bytes.size - startOffset)
        for (i in startOffset until bytes.size) {
            val b = bytes[i]
            if (b.toInt() != 0) out.write(b.toInt() and 0xFF)
        }
        return out.toByteArray()
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        val n = bytes.size
        while (i < n) {
            val b0 = bytes[i].toInt() and 0xFF
            when {
                b0 <= 0x7F -> i++
                b0 in 0xC2..0xDF -> {
                    if (i + 1 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if ((b1 and 0xC0) != 0x80) return false
                    i += 2
                }
                b0 in 0xE0..0xEF -> {
                    if (i + 2 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    if ((b1 and 0xC0) != 0x80 || (b2 and 0xC0) != 0x80) return false
                    if (b0 == 0xE0 && b1 < 0xA0) return false // overlong
                    if (b0 == 0xED && b1 >= 0xA0) return false // UTF-16 surrogates
                    i += 3
                }
                b0 in 0xF0..0xF4 -> {
                    if (i + 3 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    if ((b1 and 0xC0) != 0x80 || (b2 and 0xC0) != 0x80 || (b3 and 0xC0) != 0x80) return false
                    if (b0 == 0xF0 && b1 < 0x90) return false // overlong
                    if (b0 == 0xF4 && b1 > 0x8F) return false // > U+10FFFF
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun decodeWindows1252(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 0x80..0x9F) {
                sb.append(CP1252_HIGH_MAP[v - 0x80])
            } else {
                sb.append(v.toChar())
            }
        }
        return sb.toString()
    }

    private fun normalizeLineEndings(text: String, isUtf16: Boolean): String {
        val sb = StringBuilder(text.length)
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when {
                ch == '\r' -> {
                    if (i + 1 < n && (text[i + 1] == '\n' || (isUtf16 && text[i + 1] == '\u0085'))) {
                        i += 2
                    } else {
                        i++
                    }
                    sb.append('\n')
                }
                isUtf16 && (ch == '\u0085' || ch == '\u000C' || ch == '\u2028' || ch == '\u2029') -> {
                    sb.append('\n')
                    i++
                }
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun detectTextVariant(normalized: String): TextGridFormatVariant {
        val sample = normalized.take(2048)
        return if (sample.contains("xmin =") || sample.contains("intervals [") || sample.contains("points [") || sample.contains("item [")) {
            TextGridFormatVariant.LONG_TEXT
        } else {
            TextGridFormatVariant.SHORT_TEXT
        }
    }

    // =========================================================================
    // Type-Driven `abcio` Text Scanner with Line/Column/Offset Tracking
    // =========================================================================

    private class ParseSyntaxException(
        val code: DiagnosticCode,
        message: String,
        val line: Int,
        val column: Int
    ) : Exception(message)

    private data class StringToken(
        val value: String,
        val startLine: Int,
        val startCol: Int,
        val endLine: Int,
        val endCol: Int,
        val innerStartOffset: Int,
        val innerEndOffset: Int
    )

    private data class DoubleToken(
        val value: Double,
        val startLine: Int,
        val startCol: Int,
        val endLine: Int
    )

    private data class IntToken(
        val value: Int,
        val startLine: Int,
        val startCol: Int,
        val endLine: Int
    )

    private class AbcioTextScanner(
        val s: String,
        startPos: Int = 0,
        startLine: Int = 1,
        startCol: Int = 1
    ) {
        var pos: Int = startPos
            private set
        var currentLine: Int = startLine
            private set
        var currentCol: Int = startCol
            private set
        private val n: Int = s.length

        private fun isPraatSpace(ch: Char): Boolean {
            val code = ch.code
            return when {
                code == 0x20 || code == 0x0A || code == 0x09 || code == 0x0D || code == 0x0B || code == 0x0C -> true
                code < 0x85 -> false
                code == 0x0085 || code == 0x00A0 || code == 0x1680 -> true
                code in 0x2000..0x200A -> true
                code == 0x2028 || code == 0x2029 || code == 0x202F || code == 0x205F || code == 0x3000 -> true
                else -> false
            }
        }

        private fun advanceChar(): Char {
            val c = s[pos++]
            if (c == '\n') {
                currentLine++
                currentCol = 1
            } else {
                currentCol++
            }
            return c
        }

        fun hasMoreTokens(): Boolean {
            val savedPos = pos
            val savedLine = currentLine
            val savedCol = currentCol
            try {
                while (pos < n) {
                    val c = s[pos]
                    if (isPraatSpace(c)) {
                        advanceChar()
                    } else if (c == '!') {
                        while (pos < n && s[pos] != '\n') advanceChar()
                        if (pos < n) advanceChar()
                    } else {
                        return true
                    }
                }
                return false
            } finally {
                pos = savedPos
                currentLine = savedLine
                currentCol = savedCol
            }
        }

        private fun scanToTokenStart(validStarts: String, errorStarts: String, expectedDesc: String): Triple<Char, Int, Int> {
            while (true) {
                if (pos >= n) {
                    throw ParseSyntaxException(
                        DiagnosticCode.EARLY_EOF,
                        "Early end of text while expecting $expectedDesc",
                        currentLine,
                        currentCol
                    )
                }
                val startLine = currentLine
                val startCol = currentCol
                val c = advanceChar()
                if (isPraatSpace(c)) continue
                if (c == '!') {
                    while (pos < n && s[pos] != '\n') advanceChar()
                    if (pos < n) advanceChar()
                    continue
                }
                if (validStarts.indexOf(c) >= 0) {
                    return Triple(c, startLine, startCol)
                }
                if (errorStarts.indexOf(c) >= 0) {
                    throw ParseSyntaxException(
                        DiagnosticCode.UNEXPECTED_TOKEN,
                        "Unexpected token starting with '$c' while expecting $expectedDesc",
                        startLine,
                        startCol
                    )
                }
                // Skip ordinary label word until whitespace
                while (true) {
                    if (pos >= n) {
                        throw ParseSyntaxException(
                            DiagnosticCode.EARLY_EOF,
                            "Early end of text after label word while expecting $expectedDesc",
                            currentLine,
                            currentCol
                        )
                    }
                    val nc = advanceChar()
                    if (isPraatSpace(nc)) break
                }
            }
        }

        fun readStringWithSpan(): StringToken {
            val (_, openLine, openCol) = scanToTokenStart("\"", "0123456789+-<", "string")
            val innerStartOffset = pos
            val sb = StringBuilder()
            while (true) {
                if (pos >= n) {
                    throw ParseSyntaxException(
                        DiagnosticCode.UNCLOSED_STRING,
                        "Unclosed string literal starting at line $openLine, column $openCol",
                        openLine,
                        openCol
                    )
                }
                val c = advanceChar()
                if (c == '"') {
                    if (pos >= n) {
                        val innerEndOffset = pos - 1
                        return StringToken(
                            value = sb.toString(),
                            startLine = openLine,
                            startCol = openCol,
                            endLine = currentLine,
                            endCol = currentCol,
                            innerStartOffset = innerStartOffset,
                            innerEndOffset = innerEndOffset
                        )
                    }
                    val nxt = s[pos]
                    if (nxt == '"') {
                        advanceChar()
                        sb.append('"')
                    } else if (isPraatSpace(nxt)) {
                        val innerEndOffset = pos - 1
                        val endLine = currentLine
                        val endCol = currentCol
                        advanceChar() // consume single trailing whitespace
                        return StringToken(
                            value = sb.toString(),
                            startLine = openLine,
                            startCol = openCol,
                            endLine = endLine,
                            endCol = endCol,
                            innerStartOffset = innerStartOffset,
                            innerEndOffset = innerEndOffset
                        )
                    } else {
                        throw ParseSyntaxException(
                            DiagnosticCode.INVALID_CHAR_AFTER_QUOTE,
                            "Character '$nxt' immediately following closing quote",
                            currentLine,
                            currentCol
                        )
                    }
                } else {
                    sb.append(c)
                }
            }
        }

        fun readString(): String = readStringWithSpan().value

        fun readEnum(): Pair<String, Pair<Int, Int>> {
            val (_, startLine, startCol) = scanToTokenStart("<", "0123456789+-\"", "<enum>")
            val sb = StringBuilder()
            while (true) {
                if (pos >= n) {
                    throw ParseSyntaxException(
                        DiagnosticCode.UNCLOSED_ENUM,
                        "Unclosed <enum> token starting at line $startLine",
                        startLine,
                        startCol
                    )
                }
                val c = advanceChar()
                if (c == '>') {
                    if (sb.isEmpty()) {
                        throw ParseSyntaxException(
                            DiagnosticCode.INVALID_ENUM,
                            "Empty <> enum token",
                            startLine,
                            startCol
                        )
                    }
                    return sb.toString() to (startLine to startCol)
                }
                if (isPraatSpace(c) && c != ' ') {
                    throw ParseSyntaxException(
                        DiagnosticCode.INVALID_ENUM,
                        "Illegal whitespace inside <enum> token",
                        currentLine,
                        currentCol
                    )
                }
                if (sb.length >= 39) {
                    throw ParseSyntaxException(
                        DiagnosticCode.INVALID_ENUM,
                        "<enum> token exceeds maximum length of 39 characters",
                        startLine,
                        startCol
                    )
                }
                sb.append(c)
            }
        }

        fun readExists(): Pair<Boolean, Pair<Int, Int>> {
            val (tok, loc) = readEnum()
            // First letter case-insensitive, remaining letters strictly lowercase
            if (tok.length == 6 && (tok[0] == 'e' || tok[0] == 'E') && tok.substring(1) == "xists") {
                return true to loc
            }
            if (tok.length == 6 && (tok[0] == 'a' || tok[0] == 'A') && tok.substring(1) == "bsent") {
                return false to loc
            }
            throw ParseSyntaxException(
                DiagnosticCode.INVALID_ENUM,
                "Invalid existence enum <$tok>; expected <exists> or <absent>",
                loc.first,
                loc.second
            )
        }

        private fun readNumericWord(firstChar: Char, startLine: Int, startCol: Int): String {
            val sb = StringBuilder(16)
            sb.append(firstChar)
            while (pos < n) {
                val c = advanceChar()
                if (isPraatSpace(c)) break
                if (c.code > 127 || sb.length >= 40) {
                    throw ParseSyntaxException(
                        DiagnosticCode.INVALID_NUMBER,
                        "Invalid or overlong numeric token",
                        startLine,
                        startCol
                    )
                }
                sb.append(c)
            }
            return sb.toString()
        }

        fun readIntToken(): IntToken {
            val (first, startLine, startCol) = scanToTokenStart("0123456789+-", "\"<", "integer")
            val word = readNumericWord(first, startLine, startCol)
            if (!INT_REGEX.matches(word)) {
                throw ParseSyntaxException(
                    DiagnosticCode.INVALID_NUMBER,
                    "Invalid integer token '$word'",
                    startLine,
                    startCol
                )
            }
            val parsedLong = word.toLongOrNull() ?: throw ParseSyntaxException(
                DiagnosticCode.INVALID_NUMBER,
                "Integer out of 32-bit range: '$word'",
                startLine,
                startCol
            )
            if (parsedLong < Int.MIN_VALUE || parsedLong > Int.MAX_VALUE) {
                throw ParseSyntaxException(
                    DiagnosticCode.INVALID_NUMBER,
                    "Integer out of 32-bit range: $parsedLong",
                    startLine,
                    startCol
                )
            }
            return IntToken(parsedLong.toInt(), startLine, startCol, currentLine)
        }

        fun readInt(): Int = readIntToken().value

        fun readDoubleToken(): DoubleToken {
            while (true) {
                val (first, startLine, startCol) = scanToTokenStart("0123456789+-", "\"<", "real number")
                val word = readNumericWord(first, startLine, startCol)
                if (word == "+") continue // isolated '+' is ignored per Praat spec
                val value = parsePraatReal(word)
                return DoubleToken(value, startLine, startCol, currentLine)
            }
        }

        fun readDouble(): Double = readDoubleToken().value

        private fun parsePraatReal(word: String): Double {
            val slashIdx = word.indexOf('/')
            if (slashIdx >= 0) {
                val num = melderAtof(word.substring(0, slashIdx))
                val den = melderAtof(word.substring(slashIdx + 1))
                if (!num.isFinite() || !den.isFinite() || den == 0.0) return Double.NaN
                val res = num / den
                return if (res.isFinite()) res else Double.NaN
            }
            return melderAtof(word)
        }

        private fun melderAtof(token: String): Double {
            if (token.isEmpty()) return Double.NaN
            val idx = if (token[0] == '+' || token[0] == '-') 1 else 0
            if (idx >= token.length || token[idx] !in '0'..'9') {
                // Covers "--undefined--", "+inf", "-inf", "+nan", "-nan"
                return Double.NaN
            }
            if (HEX_FLOAT_REGEX.matches(token)) {
                val v = try {
                    java.lang.Double.parseDouble(token)
                } catch (_: Exception) {
                    Double.NaN
                }
                return if (v.isFinite()) v else Double.NaN
            }
            if (!DEC_FLOAT_REGEX.matches(token)) return Double.NaN
            val isPercent = token.endsWith('%')
            val core = if (isPercent) token.dropLast(1) else token
            val parsed = core.toDoubleOrNull() ?: return Double.NaN
            val finalVal = if (isPercent) parsed * 0.01 else parsed
            return if (finalVal.isFinite()) finalVal else Double.NaN
        }
    }

    // =========================================================================
    // Text Format Body Parsers (TextGrid, Tiers, Collection, Chronological)
    // =========================================================================

    private fun splitClassToken(classToken: String, line: Int, col: Int): Pair<String, Int> {
        val spaceIdx = classToken.indexOf(' ')
        if (spaceIdx < 0) return classToken to 0
        val cls = classToken.substring(0, spaceIdx)
        val verStr = classToken.substring(spaceIdx + 1).trim()
        val ver = verStr.toIntOrNull() ?: throw ParseSyntaxException(
            DiagnosticCode.UNSUPPORTED_VERSION,
            "Invalid format version in class token \"$classToken\"",
            line,
            col
        )
        return cls to ver
    }

    private fun readTextGridBodyFromText(
        scanner: AbcioTextScanner,
        docName: String,
        recordSpans: Boolean,
        nonFatalDiagnostics: MutableList<TextGridDiagnostic>
    ): Pair<TextGridDocument, SourceSpanMap> {
        val xminTok = scanner.readDoubleToken()
        val xmaxTok = scanner.readDoubleToken()
        if (xminTok.value.isFinite() && xmaxTok.value.isFinite() && xminTok.value > xmaxTok.value) {
            throw ParseSyntaxException(
                DiagnosticCode.INVERTED_DOMAIN,
                "TextGrid xmin (${xminTok.value}) > xmax (${xmaxTok.value})",
                xmaxTok.startLine,
                xmaxTok.startCol
            )
        }
        val (tiersExist, existsLoc) = scanner.readExists()
        if (!tiersExist) {
            throw ParseSyntaxException(
                DiagnosticCode.ABSENT_TIERS,
                "TextGrid tiers? is <absent>",
                existsLoc.first,
                existsLoc.second
            )
        }
        val sizeTok = scanner.readIntToken()
        if (sizeTok.value < 0) {
            throw ParseSyntaxException(
                DiagnosticCode.NEGATIVE_SIZE,
                "Negative tier size: ${sizeTok.value}",
                sizeTok.startLine,
                sizeTok.startCol
            )
        }

        val tiers = ArrayList<TextGridTier>(sizeTok.value)
        val tierSpans = LinkedHashMap<Int, TierSpanEntry>()

        for (ti in 0 until sizeTok.value) {
            val tierClassTok = scanner.readStringWithSpan()
            val (tierClassName, tierVer) = splitClassToken(tierClassTok.value, tierClassTok.startLine, tierClassTok.startCol)
            if (tierVer > 0) {
                throw ParseSyntaxException(
                    DiagnosticCode.UNSUPPORTED_VERSION,
                    "Unsupported tier format version $tierVer for $tierClassName",
                    tierClassTok.startLine,
                    tierClassTok.startCol
                )
            }
            val tierNameTok = scanner.readStringWithSpan()
            val headerStartLine = tierClassTok.startLine

            when (tierClassName) {
                "IntervalTier" -> {
                    val (tier, itemSpans) = readIntervalTierBodyFromText(
                        scanner = scanner,
                        tierName = tierNameTok.value,
                        tierIndex = ti,
                        repairEmpty = true,
                        recordSpans = recordSpans,
                        nonFatalDiagnostics = nonFatalDiagnostics
                    )
                    tiers.add(tier)
                    if (recordSpans) {
                        tierSpans[ti] = TierSpanEntry(
                            tierIndex = ti,
                            headerSpan = SourceLineSpan(
                                startLine = headerStartLine,
                                endLine = tierNameTok.endLine,
                                textLine = tierNameTok.startLine,
                                textQuoteStartCol = tierNameTok.startCol + 1,
                                textQuoteEndCol = tierNameTok.endCol - 1,
                                textCharStartOffset = tierNameTok.innerStartOffset,
                                textCharEndOffset = tierNameTok.innerEndOffset
                            ),
                            itemSpans = itemSpans
                        )
                    }
                }

                "TextTier", "MarkTier" -> {
                    val (tier, itemSpans) = readTextTierBodyFromText(
                        scanner = scanner,
                        tierName = tierNameTok.value,
                        recordSpans = recordSpans
                    )
                    tiers.add(tier)
                    if (recordSpans) {
                        tierSpans[ti] = TierSpanEntry(
                            tierIndex = ti,
                            headerSpan = SourceLineSpan(
                                startLine = headerStartLine,
                                endLine = tierNameTok.endLine,
                                textLine = tierNameTok.startLine,
                                textQuoteStartCol = tierNameTok.startCol + 1,
                                textQuoteEndCol = tierNameTok.endCol - 1,
                                textCharStartOffset = tierNameTok.innerStartOffset,
                                textCharEndOffset = tierNameTok.innerEndOffset
                            ),
                            itemSpans = itemSpans
                        )
                    }
                }

                else -> throw ParseSyntaxException(
                    DiagnosticCode.UNKNOWN_TIER_CLASS,
                    "Unknown tier class \"$tierClassName\" (expected \"IntervalTier\" or \"TextTier\")",
                    tierClassTok.startLine,
                    tierClassTok.startCol
                )
            }
        }

        val doc = TextGridDocument(
            name = docName,
            xmin = xminTok.value,
            xmax = xmaxTok.value,
            tiers = tiers
        )
        val spanMap = if (recordSpans) {
            SourceSpanMap(lineCount = countLines(scanner.s), tiers = tierSpans)
        } else {
            SourceSpanMap()
        }
        return doc to spanMap
    }

    private fun readIntervalTierBodyFromText(
        scanner: AbcioTextScanner,
        tierName: String,
        tierIndex: Int,
        repairEmpty: Boolean,
        recordSpans: Boolean,
        nonFatalDiagnostics: MutableList<TextGridDiagnostic>
    ): Pair<TextGridTier.IntervalTier, Map<Int, SourceLineSpan>> {
        val xminTok = scanner.readDoubleToken()
        val xmaxTok = scanner.readDoubleToken()
        if (xminTok.value.isFinite() && xmaxTok.value.isFinite() && xminTok.value > xmaxTok.value) {
            throw ParseSyntaxException(
                DiagnosticCode.INVERTED_DOMAIN,
                "IntervalTier '$tierName' xmin (${xminTok.value}) > xmax (${xmaxTok.value})",
                xmaxTok.startLine,
                xmaxTok.startCol
            )
        }
        val sizeTok = scanner.readIntToken()
        if (sizeTok.value < 0) {
            throw ParseSyntaxException(
                DiagnosticCode.NEGATIVE_SIZE,
                "Negative intervals size (${sizeTok.value}) in tier '$tierName'",
                sizeTok.startLine,
                sizeTok.startCol
            )
        }

        val rawEntries = ArrayList<Pair<TextGridInterval, SourceLineSpan?>>(sizeTok.value)
        for (i in 0 until sizeTok.value) {
            val ivXmin = scanner.readDoubleToken()
            val ivXmax = scanner.readDoubleToken()
            if (ivXmin.value.isFinite() && ivXmax.value.isFinite() && ivXmin.value > ivXmax.value) {
                throw ParseSyntaxException(
                    DiagnosticCode.INVERTED_DOMAIN,
                    "Interval [${i + 1}] in tier '$tierName' has xmin (${ivXmin.value}) > xmax (${ivXmax.value})",
                    ivXmax.startLine,
                    ivXmax.startCol
                )
            }
            val ivText = scanner.readStringWithSpan()
            val span = if (recordSpans) {
                SourceLineSpan(
                    startLine = ivXmin.startLine,
                    endLine = ivText.endLine,
                    textLine = ivText.startLine,
                    textQuoteStartCol = ivText.startCol + 1,
                    textQuoteEndCol = ivText.endCol - 1,
                    textCharStartOffset = ivText.innerStartOffset,
                    textCharEndOffset = ivText.innerEndOffset
                )
            } else null
            rawEntries.add(TextGridInterval(ivXmin.value, ivXmax.value, ivText.value) to span)
        }

        // Praat SortedSetOfDoubleOf behavior: sort by xmin ascending and drop duplicate start times
        val sortedDeduped = sortAndDeduplicateIntervals(rawEntries)
        val intervals = sortedDeduped.map { it.first }.toMutableList()
        val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
        if (recordSpans) {
            sortedDeduped.forEachIndexed { idx, (_, sp) ->
                if (sp != null) itemSpans[idx] = sp
            }
        }

        // TextGrid::v_repair(): empty IntervalTier receives one [tier.xmin, tier.xmax, ""] interval
        if (repairEmpty && intervals.isEmpty()) {
            intervals.add(TextGridInterval(xminTok.value, xmaxTok.value, ""))
            nonFatalDiagnostics.add(
                TextGridDiagnostic(
                    severity = DiagnosticSeverity.NON_FATAL_ANOMALY,
                    code = DiagnosticCode.EMPTY_INTERVAL_TIER_REPAIRED,
                    message = "Empty IntervalTier '$tierName' auto-repaired with default interval",
                    line = sizeTok.startLine,
                    column = sizeTok.startCol,
                    tierIndex = tierIndex
                )
            )
        }

        val tier = TextGridTier.IntervalTier(
            name = tierName,
            xmin = xminTok.value,
            xmax = xmaxTok.value,
            intervals = intervals
        )
        return tier to itemSpans
    }

    private fun readTextTierBodyFromText(
        scanner: AbcioTextScanner,
        tierName: String,
        recordSpans: Boolean
    ): Pair<TextGridTier.TextTier, Map<Int, SourceLineSpan>> {
        val xminTok = scanner.readDoubleToken()
        val xmaxTok = scanner.readDoubleToken()
        if (xminTok.value.isFinite() && xmaxTok.value.isFinite() && xminTok.value > xmaxTok.value) {
            throw ParseSyntaxException(
                DiagnosticCode.INVERTED_DOMAIN,
                "TextTier '$tierName' xmin (${xminTok.value}) > xmax (${xmaxTok.value})",
                xmaxTok.startLine,
                xmaxTok.startCol
            )
        }
        val sizeTok = scanner.readIntToken()
        if (sizeTok.value < 0) {
            throw ParseSyntaxException(
                DiagnosticCode.NEGATIVE_SIZE,
                "Negative points size (${sizeTok.value}) in tier '$tierName'",
                sizeTok.startLine,
                sizeTok.startCol
            )
        }

        val rawEntries = ArrayList<Pair<TextGridPoint, SourceLineSpan?>>(sizeTok.value)
        for (i in 0 until sizeTok.value) {
            val numTok = scanner.readDoubleToken()
            val markTok = scanner.readStringWithSpan()
            val span = if (recordSpans) {
                SourceLineSpan(
                    startLine = numTok.startLine,
                    endLine = markTok.endLine,
                    textLine = markTok.startLine,
                    textQuoteStartCol = markTok.startCol + 1,
                    textQuoteEndCol = markTok.endCol - 1,
                    textCharStartOffset = markTok.innerStartOffset,
                    textCharEndOffset = markTok.innerEndOffset
                )
            } else null
            rawEntries.add(TextGridPoint(numTok.value, markTok.value) to span)
        }

        val sortedDeduped = sortAndDeduplicatePoints(rawEntries)
        val points = sortedDeduped.map { it.first }
        val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
        if (recordSpans) {
            sortedDeduped.forEachIndexed { idx, (_, sp) ->
                if (sp != null) itemSpans[idx] = sp
            }
        }
        return TextGridTier.TextTier(
            name = tierName,
            xmin = xminTok.value,
            xmax = xmaxTok.value,
            points = points
        ) to itemSpans
    }

    private fun readCollectionFromText(
        scanner: AbcioTextScanner,
        outDocs: MutableList<Pair<TextGridDocument, SourceSpanMap>>,
        nonFatalDiagnostics: MutableList<TextGridDiagnostic>
    ) {
        val sizeTok = scanner.readIntToken()
        if (sizeTok.value < 0) {
            throw ParseSyntaxException(
                DiagnosticCode.NEGATIVE_SIZE,
                "Negative Collection size: ${sizeTok.value}",
                sizeTok.startLine,
                sizeTok.startCol
            )
        }
        for (i in 0 until sizeTok.value) {
            val clsTok = scanner.readStringWithSpan()
            val nameTok = scanner.readStringWithSpan()
            val (clsName, ver) = splitClassToken(clsTok.value, clsTok.startLine, clsTok.startCol)
            when (clsName) {
                "TextGrid" -> {
                    if (ver > 0) {
                        throw ParseSyntaxException(
                            DiagnosticCode.UNSUPPORTED_VERSION,
                            "Unsupported TextGrid version $ver in Collection",
                            clsTok.startLine,
                            clsTok.startCol
                        )
                    }
                    outDocs.add(
                        readTextGridBodyFromText(
                            scanner = scanner,
                            docName = nameTok.value,
                            recordSpans = outDocs.isEmpty(),
                            nonFatalDiagnostics = nonFatalDiagnostics
                        )
                    )
                }

                "IntervalTier" -> {
                    val (tier, spans) = readIntervalTierBodyFromText(
                        scanner = scanner,
                        tierName = nameTok.value,
                        tierIndex = 0,
                        repairEmpty = false,
                        recordSpans = outDocs.isEmpty(),
                        nonFatalDiagnostics = nonFatalDiagnostics
                    )
                    val doc = TextGridDocument(nameTok.value, tier.xmin, tier.xmax, listOf(tier))
                    val sm = SourceSpanMap(countLines(scanner.s), mapOf(0 to TierSpanEntry(0, SourceLineSpan(clsTok.startLine, nameTok.endLine), spans)))
                    outDocs.add(doc to sm)
                }

                "TextTier", "MarkTier" -> {
                    val (tier, spans) = readTextTierBodyFromText(
                        scanner = scanner,
                        tierName = nameTok.value,
                        recordSpans = outDocs.isEmpty()
                    )
                    val doc = TextGridDocument(nameTok.value, tier.xmin, tier.xmax, listOf(tier))
                    val sm = SourceSpanMap(countLines(scanner.s), mapOf(0 to TierSpanEntry(0, SourceLineSpan(clsTok.startLine, nameTok.endLine), spans)))
                    outDocs.add(doc to sm)
                }

                "Collection" -> {
                    readCollectionFromText(scanner, outDocs, nonFatalDiagnostics)
                }

                "Sound" -> {
                    // Skip Sound 0/1/2 matrix payload cleanly
                    scanner.readDouble() // xmin
                    scanner.readDouble() // xmax
                    val nx = scanner.readInt()
                    scanner.readDouble() // dx
                    scanner.readDouble() // x1
                    scanner.readDouble() // ymin
                    scanner.readDouble() // ymax
                    val ny = scanner.readInt()
                    scanner.readDouble() // dy
                    scanner.readDouble() // y1
                    if (nx < 0 || ny < 0) {
                        throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative Sound dimensions", clsTok.startLine, clsTok.startCol)
                    }
                    val total = nx.toLong() * ny.toLong()
                    for (k in 0L until total) {
                        scanner.readDouble()
                    }
                }

                "Table" -> {
                    val colCount = scanner.readInt()
                    if (colCount < 0) {
                        throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative Table columns", clsTok.startLine, clsTok.startCol)
                    }
                    for (c in 0 until colCount) scanner.readString()
                    val rowCount = scanner.readInt()
                    if (rowCount < 0) {
                        throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative Table rows", clsTok.startLine, clsTok.startCol)
                    }
                    for (r in 0 until rowCount) {
                        for (c in 0 until colCount) {
                            scanner.readString()
                        }
                    }
                }

                else -> throw ParseSyntaxException(
                    DiagnosticCode.UNKNOWN_TIER_CLASS,
                    "Unsupported embedded class \"$clsName\" in text Collection",
                    clsTok.startLine,
                    clsTok.startCol
                )
            }
        }
    }

    private fun parseChronologicalText(
        normalized: String,
        defaultName: String,
        detectedEncoding: String
    ): TextGridParseResult {
        val scanner = AbcioTextScanner(normalized, startPos = 0, startLine = 1, startCol = 1)
        val nonFatalDiagnostics = mutableListOf<TextGridDiagnostic>()

        return try {
            val header = scanner.readStringWithSpan()
            if (header.value != "Praat chronological TextGrid text file") {
                throw ParseSyntaxException(
                    DiagnosticCode.INVALID_HEADER,
                    "Invalid chronological TextGrid header",
                    header.startLine,
                    header.startCol
                )
            }
            val xminTok = scanner.readDoubleToken()
            val xmaxTok = scanner.readDoubleToken()
            if (xminTok.value.isFinite() && xmaxTok.value.isFinite() && xminTok.value > xmaxTok.value) {
                throw ParseSyntaxException(
                    DiagnosticCode.INVERTED_DOMAIN,
                    "TextGrid xmin (${xminTok.value}) > xmax (${xmaxTok.value})",
                    xmaxTok.startLine,
                    xmaxTok.startCol
                )
            }
            val numTiersTok = scanner.readIntToken()
            if (numTiersTok.value < 0) {
                throw ParseSyntaxException(
                    DiagnosticCode.NEGATIVE_SIZE,
                    "Negative tier count: ${numTiersTok.value}",
                    numTiersTok.startLine,
                    numTiersTok.startCol
                )
            }

            class ChronTierBuilder(
                val isInterval: Boolean,
                val name: String,
                val xmin: Double,
                val xmax: Double,
                val headerSpan: SourceLineSpan
            ) {
                val intervals = mutableListOf<Pair<TextGridInterval, SourceLineSpan>>()
                val points = mutableListOf<Pair<TextGridPoint, SourceLineSpan>>()
            }

            val builders = ArrayList<ChronTierBuilder>(numTiersTok.value)
            for (ti in 0 until numTiersTok.value) {
                val clsTok = scanner.readStringWithSpan()
                val isInterval = when (clsTok.value) {
                    "IntervalTier" -> true
                    "TextTier" -> false
                    else -> throw ParseSyntaxException(
                        DiagnosticCode.UNKNOWN_TIER_CLASS,
                        "Unknown tier class \"${clsTok.value}\" in chronological TextGrid",
                        clsTok.startLine,
                        clsTok.startCol
                    )
                }
                val nameTok = scanner.readStringWithSpan()
                val tXmin = scanner.readDoubleToken()
                val tXmax = scanner.readDoubleToken()
                if (tXmin.value.isFinite() && tXmax.value.isFinite() && tXmin.value > tXmax.value) {
                    throw ParseSyntaxException(
                        DiagnosticCode.INVERTED_DOMAIN,
                        "Tier '${nameTok.value}' xmin (${tXmin.value}) > xmax (${tXmax.value})",
                        tXmax.startLine,
                        tXmax.startCol
                    )
                }
                builders.add(
                    ChronTierBuilder(
                        isInterval = isInterval,
                        name = nameTok.value,
                        xmin = tXmin.value,
                        xmax = tXmax.value,
                        headerSpan = SourceLineSpan(
                            startLine = clsTok.startLine,
                            endLine = tXmax.endLine,
                            textLine = nameTok.startLine,
                            textQuoteStartCol = nameTok.startCol + 1,
                            textQuoteEndCol = nameTok.endCol - 1,
                            textCharStartOffset = nameTok.innerStartOffset,
                            textCharEndOffset = nameTok.innerEndOffset
                        )
                    )
                )
            }

            // Interleaved chronological records until EOF
            while (scanner.hasMoreTokens()) {
                val tierNumTok = scanner.readIntToken()
                val idx = tierNumTok.value - 1
                if (idx !in builders.indices) {
                    throw ParseSyntaxException(
                        DiagnosticCode.UNEXPECTED_TOKEN,
                        "Chronological tier index ${tierNumTok.value} out of range 1..${builders.size}",
                        tierNumTok.startLine,
                        tierNumTok.startCol
                    )
                }
                val b = builders[idx]
                if (b.isInterval) {
                    val ivXmin = scanner.readDoubleToken()
                    val ivXmax = scanner.readDoubleToken()
                    if (ivXmin.value.isFinite() && ivXmax.value.isFinite() && ivXmin.value > ivXmax.value) {
                        throw ParseSyntaxException(
                            DiagnosticCode.INVERTED_DOMAIN,
                            "Interval xmin (${ivXmin.value}) > xmax (${ivXmax.value})",
                            ivXmax.startLine,
                            ivXmax.startCol
                        )
                    }
                    val txt = scanner.readStringWithSpan()
                    val span = SourceLineSpan(
                        startLine = tierNumTok.startLine,
                        endLine = txt.endLine,
                        textLine = txt.startLine,
                        textQuoteStartCol = txt.startCol + 1,
                        textQuoteEndCol = txt.endCol - 1,
                        textCharStartOffset = txt.innerStartOffset,
                        textCharEndOffset = txt.innerEndOffset
                    )
                    b.intervals.add(TextGridInterval(ivXmin.value, ivXmax.value, txt.value) to span)
                } else {
                    val numTok = scanner.readDoubleToken()
                    val markTok = scanner.readStringWithSpan()
                    val span = SourceLineSpan(
                        startLine = tierNumTok.startLine,
                        endLine = markTok.endLine,
                        textLine = markTok.startLine,
                        textQuoteStartCol = markTok.startCol + 1,
                        textQuoteEndCol = markTok.endCol - 1,
                        textCharStartOffset = markTok.innerStartOffset,
                        textCharEndOffset = markTok.innerEndOffset
                    )
                    b.points.add(TextGridPoint(numTok.value, markTok.value) to span)
                }
            }

            val tiers = ArrayList<TextGridTier>(builders.size)
            val tierSpans = LinkedHashMap<Int, TierSpanEntry>()
            builders.forEachIndexed { ti, b ->
                if (b.isInterval) {
                    val sorted = sortAndDeduplicateIntervals(b.intervals)
                    val list = sorted.map { it.first }.toMutableList()
                    val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
                    sorted.forEachIndexed { idx, (_, sp) -> if (sp != null) itemSpans[idx] = sp }
                    if (list.isEmpty()) {
                        list.add(TextGridInterval(b.xmin, b.xmax, ""))
                        nonFatalDiagnostics.add(
                            TextGridDiagnostic(
                                severity = DiagnosticSeverity.NON_FATAL_ANOMALY,
                                code = DiagnosticCode.EMPTY_INTERVAL_TIER_REPAIRED,
                                message = "Empty IntervalTier '${b.name}' auto-repaired with default interval",
                                line = b.headerSpan.startLine,
                                column = 1,
                                tierIndex = ti
                            )
                        )
                    }
                    tiers.add(TextGridTier.IntervalTier(b.name, b.xmin, b.xmax, list))
                    tierSpans[ti] = TierSpanEntry(ti, b.headerSpan, itemSpans)
                } else {
                    val sorted = sortAndDeduplicatePoints(b.points)
                    val list = sorted.map { it.first }
                    val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
                    sorted.forEachIndexed { idx, (_, sp) -> if (sp != null) itemSpans[idx] = sp }
                    tiers.add(TextGridTier.TextTier(b.name, b.xmin, b.xmax, list))
                    tierSpans[ti] = TierSpanEntry(ti, b.headerSpan, itemSpans)
                }
            }

            val doc = TextGridDocument(defaultName, xminTok.value, xmaxTok.value, tiers)
            val spanMap = SourceSpanMap(countLines(normalized), tierSpans)
            val anomalies = detectAnomalies(doc)
            TextGridParseResult(
                document = doc,
                collectionDocuments = listOf(doc),
                detectedVariant = TextGridFormatVariant.CHRONOLOGICAL,
                detectedEncoding = detectedEncoding,
                normalizedSource = normalized,
                spanMap = spanMap,
                diagnostics = nonFatalDiagnostics + anomaliesToDiagnostics(anomalies, spanMap),
                anomalies = anomalies
            )
        } catch (e: ParseSyntaxException) {
            fatalResult(
                code = e.code,
                message = e.message ?: "Syntax error",
                line = e.line,
                column = e.column,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding,
                detectedVariant = TextGridFormatVariant.CHRONOLOGICAL
            )
        } catch (e: Exception) {
            fatalResult(
                code = DiagnosticCode.UNEXPECTED_TOKEN,
                message = e.message ?: "Unexpected error parsing chronological TextGrid",
                line = scanner.currentLine,
                column = scanner.currentCol,
                normalizedSource = normalized,
                detectedEncoding = detectedEncoding,
                detectedVariant = TextGridFormatVariant.CHRONOLOGICAL
            )
        }
    }

    // =========================================================================
    // Binary Reader & Writer (`ooBinaryFile`, Big-Endian)
    // =========================================================================

    private class BinaryReader(private val input: InputStream) {
        private val scratch = ByteArray(8)
        private val buf = ByteBuffer.wrap(scratch).order(ByteOrder.BIG_ENDIAN)

        fun readExact(n: Int): ByteArray {
            if (n < 0) throw IllegalArgumentException("Negative byte count: $n")
            val out = ByteArray(n)
            var offset = 0
            while (offset < n) {
                val r = input.read(out, offset, n - offset)
                if (r < 0) throw EOFException("Unexpected end of binary stream ($offset/$n)")
                offset += r
            }
            return out
        }

        private fun fill(n: Int) {
            var offset = 0
            while (offset < n) {
                val r = input.read(scratch, offset, n - offset)
                if (r < 0) throw EOFException("Unexpected end of binary stream")
                offset += r
            }
        }

        fun u8(): Int {
            val v = input.read()
            if (v < 0) throw EOFException("Unexpected end of binary stream")
            return v and 0xFF
        }

        fun bool8(): Boolean = u8() != 0

        fun u16(): Int {
            fill(2)
            return buf.getShort(0).toInt() and 0xFFFF
        }

        fun i32(): Int {
            fill(4)
            return buf.getInt(0)
        }

        fun r32(): Double {
            fill(4)
            val v = buf.getFloat(0)
            return if (v.isNaN() || v.isInfinite()) Double.NaN else v.toDouble()
        }

        fun r64(): Double {
            fill(8)
            val v = buf.getDouble(0)
            return if (v.isNaN() || v.isInfinite()) Double.NaN else v
        }

        fun skipBytes(count: Long) {
            var remaining = count
            val skipBuf = ByteArray(8192)
            while (remaining > 0L) {
                val chunk = minOf(remaining, skipBuf.size.toLong()).toInt()
                val r = input.read(skipBuf, 0, chunk)
                if (r < 0) throw EOFException("Unexpected end of binary stream while skipping $count bytes")
                remaining -= r.toLong()
            }
        }

        fun w8(): String {
            val len = u8()
            if (len == 0xFF) {
                val cpCount = u8()
                return readUtf16BeCodePoints(cpCount)
            }
            return String(readExact(len), Charsets.ISO_8859_1)
        }

        fun w16(): String {
            val len = u16()
            if (len == 0xFFFF) {
                val cpCount = u16()
                return readUtf16BeCodePoints(cpCount)
            }
            return String(readExact(len), Charsets.ISO_8859_1)
        }

        private fun readUtf16BeCodePoints(cpCount: Int): String {
            val sb = StringBuilder(cpCount)
            for (i in 0 until cpCount) {
                val u1 = u16()
                when (u1) {
                    in 0xD800..0xDBFF -> {
                        val u2 = u16()
                        if (u2 !in 0xDC00..0xDFFF) {
                            throw IllegalArgumentException("Invalid UTF-16BE low surrogate: 0x${u2.toString(16)}")
                        }
                        val cp = 0x10000 + ((u1 - 0xD800) shl 10) + (u2 - 0xDC00)
                        sb.appendCodePoint(cp)
                    }
                    in 0xDC00..0xDFFF -> {
                        throw IllegalArgumentException("Lone UTF-16BE low surrogate: 0x${u1.toString(16)}")
                    }
                    else -> sb.append(u1.toChar())
                }
            }
            return sb.toString()
        }
    }

    private class BinaryWriter(private val out: OutputStream) {
        private val scratch = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)

        fun writeRawAscii(s: String) {
            out.write(s.toByteArray(Charsets.US_ASCII))
        }

        fun u8(v: Int) = out.write(v and 0xFF)

        fun bool8(v: Boolean) = out.write(if (v) 1 else 0)

        fun u16(v: Int) {
            scratch.clear()
            scratch.putShort((v and 0xFFFF).toShort())
            out.write(scratch.array(), 0, 2)
        }

        fun i32(v: Int) {
            scratch.clear()
            scratch.putInt(v)
            out.write(scratch.array(), 0, 4)
        }

        fun r64(v: Double) {
            scratch.clear()
            val bitsVal = if (v.isFinite()) v else Double.NaN
            scratch.putDouble(bitsVal)
            out.write(scratch.array(), 0, 8)
        }

        fun w8(s: String) {
            val (sub, cpCount) = truncateByCodePoints(s, 254)
            if (isPure7BitAscii(sub)) {
                u8(cpCount)
                out.write(sub.toByteArray(Charsets.US_ASCII))
            } else {
                u8(0xFF)
                u8(cpCount)
                out.write(sub.toByteArray(Charsets.UTF_16BE))
            }
        }

        fun w16(s: String) {
            val (sub, cpCount) = truncateByCodePoints(s, 65534)
            if (isPure7BitAscii(sub)) {
                u16(cpCount)
                out.write(sub.toByteArray(Charsets.US_ASCII))
            } else {
                u16(0xFFFF)
                u16(cpCount)
                out.write(sub.toByteArray(Charsets.UTF_16BE))
            }
        }

        private fun isPure7BitAscii(s: String): Boolean {
            for (i in s.indices) {
                if (s[i].code > 0x7F) return false
            }
            return true
        }

        private fun truncateByCodePoints(s: String, maxCp: Int): Pair<String, Int> {
            val total = s.codePointCount(0, s.length)
            return if (total > maxCp) {
                val endOffset = s.offsetByCodePoints(0, maxCp)
                s.substring(0, endOffset) to maxCp
            } else {
                s to total
            }
        }
    }

    private fun parseBinary(bytes: ByteArray, defaultName: String): TextGridParseResult {
        val reader = BinaryReader(ByteArrayInputStream(bytes))
        val nonFatal = mutableListOf<TextGridDiagnostic>()
        return try {
            val magic = String(reader.readExact(12), Charsets.US_ASCII)
            if (magic != BINARY_MAGIC) {
                return fatalResult(
                    DiagnosticCode.INVALID_HEADER,
                    "Invalid Praat binary magic header: '$magic'",
                    1,
                    1,
                    detectedVariant = TextGridFormatVariant.BINARY,
                    detectedEncoding = "BINARY"
                )
            }
            val classToken = reader.w8()
            val (className, ver) = splitClassToken(classToken, 1, 1)
            when (className) {
                "TextGrid" -> {
                    if (ver > 0) {
                        throw ParseSyntaxException(DiagnosticCode.UNSUPPORTED_VERSION, "Unsupported binary TextGrid version $ver", 1, 1)
                    }
                    val doc = readBinaryTextGridBody(reader, defaultName, nonFatal)
                    val anomalies = detectAnomalies(doc)
                    TextGridParseResult(
                        document = doc,
                        collectionDocuments = listOf(doc),
                        detectedVariant = TextGridFormatVariant.BINARY,
                        detectedEncoding = "BINARY",
                        normalizedSource = null,
                        spanMap = SourceSpanMap(),
                        diagnostics = nonFatal + anomaliesToDiagnostics(anomalies, SourceSpanMap()),
                        anomalies = anomalies
                    )
                }

                "IntervalTier" -> {
                    if (ver > 0) {
                        throw ParseSyntaxException(DiagnosticCode.UNSUPPORTED_VERSION, "Unsupported binary IntervalTier version $ver", 1, 1)
                    }
                    val tier = readBinaryIntervalTierBody(reader, defaultName.ifEmpty { "IntervalTier" }, 0, repairEmpty = false, nonFatal = nonFatal)
                    val doc = TextGridDocument(defaultName, tier.xmin, tier.xmax, listOf(tier))
                    val anomalies = detectAnomalies(doc)
                    TextGridParseResult(
                        document = doc,
                        collectionDocuments = listOf(doc),
                        detectedVariant = TextGridFormatVariant.BINARY,
                        detectedEncoding = "BINARY",
                        diagnostics = nonFatal + anomaliesToDiagnostics(anomalies, SourceSpanMap()),
                        anomalies = anomalies
                    )
                }

                "TextTier", "MarkTier" -> {
                    if (ver > 0) {
                        throw ParseSyntaxException(DiagnosticCode.UNSUPPORTED_VERSION, "Unsupported binary $className version $ver", 1, 1)
                    }
                    val tier = readBinaryTextTierBody(reader, defaultName.ifEmpty { "TextTier" })
                    val doc = TextGridDocument(defaultName, tier.xmin, tier.xmax, listOf(tier))
                    val anomalies = detectAnomalies(doc)
                    TextGridParseResult(
                        document = doc,
                        collectionDocuments = listOf(doc),
                        detectedVariant = TextGridFormatVariant.BINARY,
                        detectedEncoding = "BINARY",
                        diagnostics = nonFatal + anomaliesToDiagnostics(anomalies, SourceSpanMap()),
                        anomalies = anomalies
                    )
                }

                "Collection" -> {
                    if (ver > 0) {
                        throw ParseSyntaxException(DiagnosticCode.UNSUPPORTED_VERSION, "Unsupported binary Collection version $ver", 1, 1)
                    }
                    val extracted = mutableListOf<TextGridDocument>()
                    readBinaryCollection(reader, extracted, nonFatal)
                    if (extracted.isEmpty()) {
                        fatalResult(
                            DiagnosticCode.COLLECTION_NO_TEXTGRID,
                            "Binary Praat Collection contains no TextGrid object",
                            1,
                            1,
                            detectedVariant = TextGridFormatVariant.BINARY,
                            detectedEncoding = "BINARY"
                        )
                    } else {
                        val primary = extracted.first()
                        val anomalies = detectAnomalies(primary)
                        TextGridParseResult(
                            document = primary,
                            collectionDocuments = extracted,
                            detectedVariant = TextGridFormatVariant.BINARY,
                            detectedEncoding = "BINARY",
                            diagnostics = nonFatal + anomaliesToDiagnostics(anomalies, SourceSpanMap()),
                            anomalies = anomalies
                        )
                    }
                }

                else -> throw ParseSyntaxException(
                    DiagnosticCode.UNKNOWN_TIER_CLASS,
                    "Unsupported binary Praat class \"$className\"",
                    1,
                    1
                )
            }
        } catch (e: ParseSyntaxException) {
            fatalResult(
                code = e.code,
                message = e.message ?: "Binary syntax error",
                line = 1,
                column = 1,
                detectedVariant = TextGridFormatVariant.BINARY,
                detectedEncoding = "BINARY"
            )
        } catch (e: Exception) {
            fatalResult(
                code = DiagnosticCode.BINARY_ERROR,
                message = e.message ?: "Failed to parse Praat binary stream",
                line = 1,
                column = 1,
                detectedVariant = TextGridFormatVariant.BINARY,
                detectedEncoding = "BINARY"
            )
        }
    }

    private fun readBinaryTextGridBody(
        reader: BinaryReader,
        docName: String,
        nonFatal: MutableList<TextGridDiagnostic>
    ): TextGridDocument {
        val xmin = reader.r64()
        val xmax = reader.r64()
        if (xmin.isFinite() && xmax.isFinite() && xmin > xmax) {
            throw ParseSyntaxException(DiagnosticCode.INVERTED_DOMAIN, "Binary TextGrid xmin ($xmin) > xmax ($xmax)", 1, 1)
        }
        val tiersExist = reader.bool8()
        if (!tiersExist) {
            throw ParseSyntaxException(DiagnosticCode.ABSENT_TIERS, "Binary TextGrid tiers? is absent (0x00)", 1, 1)
        }
        val size = reader.i32()
        if (size < 0) {
            throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative binary tier size: $size", 1, 1)
        }
        val tiers = ArrayList<TextGridTier>(size)
        for (ti in 0 until size) {
            val clsToken = reader.w8()
            val (clsName, ver) = splitClassToken(clsToken, 1, 1)
            if (ver > 0) {
                throw ParseSyntaxException(DiagnosticCode.UNSUPPORTED_VERSION, "Unsupported binary tier version $ver", 1, 1)
            }
            val tierName = reader.w16()
            when (clsName) {
                "IntervalTier" -> tiers.add(readBinaryIntervalTierBody(reader, tierName, ti, repairEmpty = true, nonFatal = nonFatal))
                "TextTier", "MarkTier" -> tiers.add(readBinaryTextTierBody(reader, tierName))
                else -> throw ParseSyntaxException(
                    DiagnosticCode.UNKNOWN_TIER_CLASS,
                    "Unknown binary tier class \"$clsName\"",
                    1,
                    1
                )
            }
        }
        return TextGridDocument(name = docName, xmin = xmin, xmax = xmax, tiers = tiers)
    }

    private fun readBinaryIntervalTierBody(
        reader: BinaryReader,
        tierName: String,
        tierIndex: Int,
        repairEmpty: Boolean,
        nonFatal: MutableList<TextGridDiagnostic>
    ): TextGridTier.IntervalTier {
        val xmin = reader.r64()
        val xmax = reader.r64()
        if (xmin.isFinite() && xmax.isFinite() && xmin > xmax) {
            throw ParseSyntaxException(DiagnosticCode.INVERTED_DOMAIN, "Binary IntervalTier '$tierName' xmin > xmax", 1, 1)
        }
        val size = reader.i32()
        if (size < 0) {
            throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative binary intervals size: $size", 1, 1)
        }
        val raw = ArrayList<Pair<TextGridInterval, SourceLineSpan?>>(size)
        for (i in 0 until size) {
            val s = reader.r64()
            val e = reader.r64()
            if (s.isFinite() && e.isFinite() && s > e) {
                throw ParseSyntaxException(DiagnosticCode.INVERTED_DOMAIN, "Binary interval xmin ($s) > xmax ($e)", 1, 1)
            }
            val text = reader.w16()
            raw.add(TextGridInterval(s, e, text) to null)
        }
        val intervals = sortAndDeduplicateIntervals(raw).map { it.first }.toMutableList()
        if (repairEmpty && intervals.isEmpty()) {
            intervals.add(TextGridInterval(xmin, xmax, ""))
            nonFatal.add(
                TextGridDiagnostic(
                    severity = DiagnosticSeverity.NON_FATAL_ANOMALY,
                    code = DiagnosticCode.EMPTY_INTERVAL_TIER_REPAIRED,
                    message = "Empty binary IntervalTier '$tierName' auto-repaired with default interval",
                    tierIndex = tierIndex
                )
            )
        }
        return TextGridTier.IntervalTier(tierName, xmin, xmax, intervals)
    }

    private fun readBinaryTextTierBody(reader: BinaryReader, tierName: String): TextGridTier.TextTier {
        val xmin = reader.r64()
        val xmax = reader.r64()
        if (xmin.isFinite() && xmax.isFinite() && xmin > xmax) {
            throw ParseSyntaxException(DiagnosticCode.INVERTED_DOMAIN, "Binary TextTier '$tierName' xmin > xmax", 1, 1)
        }
        val size = reader.i32()
        if (size < 0) {
            throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative binary points size: $size", 1, 1)
        }
        val raw = ArrayList<Pair<TextGridPoint, SourceLineSpan?>>(size)
        for (i in 0 until size) {
            val number = reader.r64()
            val mark = reader.w16()
            raw.add(TextGridPoint(number, mark) to null)
        }
        val points = sortAndDeduplicatePoints(raw).map { it.first }
        return TextGridTier.TextTier(tierName, xmin, xmax, points)
    }

    private fun readBinaryCollection(
        reader: BinaryReader,
        outDocs: MutableList<TextGridDocument>,
        nonFatal: MutableList<TextGridDiagnostic>
    ) {
        val size = reader.i32()
        if (size < 0) {
            throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative binary Collection size: $size", 1, 1)
        }
        for (i in 0 until size) {
            val clsToken = reader.w8()
            val objName = reader.w16()
            val (clsName, ver) = splitClassToken(clsToken, 1, 1)
            when (clsName) {
                "TextGrid" -> {
                    if (ver > 0) throw ParseSyntaxException(DiagnosticCode.UNSUPPORTED_VERSION, "Unsupported TextGrid version $ver", 1, 1)
                    outDocs.add(readBinaryTextGridBody(reader, objName, nonFatal))
                }
                "IntervalTier" -> {
                    val tier = readBinaryIntervalTierBody(reader, objName, 0, repairEmpty = false, nonFatal = nonFatal)
                    outDocs.add(TextGridDocument(objName, tier.xmin, tier.xmax, listOf(tier)))
                }
                "TextTier", "MarkTier" -> {
                    val tier = readBinaryTextTierBody(reader, objName)
                    outDocs.add(TextGridDocument(objName, tier.xmin, tier.xmax, listOf(tier)))
                }
                "Collection" -> readBinaryCollection(reader, outDocs, nonFatal)
                "Sound" -> {
                    reader.r64() // xmin
                    reader.r64() // xmax
                    val nx = reader.i32()
                    reader.r64() // dx
                    reader.r64() // x1
                    reader.r64() // ymin
                    reader.r64() // ymax
                    val ny = reader.i32()
                    reader.r64() // dy
                    reader.r64() // y1
                    if (nx < 0 || ny < 0) {
                        throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative Sound matrix dimensions", 1, 1)
                    }
                    val bytesPerSample = if (ver >= 2) 8L else 4L
                    reader.skipBytes(nx.toLong() * ny.toLong() * bytesPerSample)
                }
                "Table" -> {
                    val colCount = reader.i32()
                    if (colCount < 0) throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative Table columns", 1, 1)
                    for (c in 0 until colCount) reader.w16()
                    val rowCount = reader.i32()
                    if (rowCount < 0) throw ParseSyntaxException(DiagnosticCode.NEGATIVE_SIZE, "Negative Table rows", 1, 1)
                    for (r in 0 until rowCount) {
                        for (c in 0 until colCount) reader.w16()
                    }
                }
                else -> throw ParseSyntaxException(
                    DiagnosticCode.UNKNOWN_TIER_CLASS,
                    "Unsupported embedded class \"$clsToken\" in binary Collection",
                    1,
                    1
                )
            }
        }
    }

    private fun writeBinaryTextGrid(doc: TextGridDocument, out: OutputStream) {
        val w = BinaryWriter(out)
        w.writeRawAscii(BINARY_MAGIC)
        w.w8("TextGrid")
        w.r64(doc.xmin)
        w.r64(doc.xmax)
        w.bool8(true) // tiers? <exists> == 0x01
        w.i32(doc.tiers.size)
        for (tier in doc.tiers) {
            when (tier) {
                is TextGridTier.IntervalTier -> {
                    w.w8("IntervalTier")
                    w.w16(tier.name)
                    w.r64(tier.xmin)
                    w.r64(tier.xmax)
                    val safeIntervals = if (tier.intervals.isEmpty()) {
                        listOf(TextGridInterval(tier.xmin, tier.xmax, ""))
                    } else {
                        tier.intervals
                    }
                    w.i32(safeIntervals.size)
                    for (iv in safeIntervals) {
                        w.r64(iv.xmin)
                        w.r64(iv.xmax)
                        w.w16(iv.text)
                    }
                }
                is TextGridTier.TextTier -> {
                    w.w8("TextTier") // NEVER "PointTier"
                    w.w16(tier.name)
                    w.r64(tier.xmin)
                    w.r64(tier.xmax)
                    w.i32(tier.points.size)
                    for (pt in tier.points) {
                        w.r64(pt.number)
                        w.w16(pt.mark)
                    }
                }
            }
        }
        out.flush()
    }

    // =========================================================================
    // Text Serializers with Live SourceSpanMap Generation
    // =========================================================================

    private class SpanTrackingBuilder {
        val sb = StringBuilder(1024)
        var line: Int = 1
            private set

        fun appendLine(content: String): Int {
            val startLine = line
            sb.append(content).append('\n')
            line += 1 + content.count { it == '\n' }
            return startLine
        }

        /**
         * Appends `<prefix>"<escaped>"<suffix>\n` and returns exact quote-inner line/col/offset coordinates.
         */
        fun appendQuotedLine(prefix: String, rawText: String, suffix: String = ""): SourceLineSpan {
            val startLine = line
            val escaped = escapePraatString(rawText)
            sb.append(prefix)
            sb.append('"')
            val innerStartOffset = sb.length
            val quoteStartCol = prefix.length + 2 // 1-based column right after opening '"'
            sb.append(escaped)
            val innerEndOffset = sb.length
            sb.append('"')
            sb.append(suffix)
            sb.append('\n')

            val newlineCount = escaped.count { it == '\n' }
            val endLine = startLine + newlineCount
            val lastLineLen = if (newlineCount == 0) {
                prefix.length + 1 + escaped.length
            } else {
                escaped.substringAfterLast('\n').length
            }
            val quoteEndCol = lastLineLen + 1 // 1-based column of closing '"'
            line = endLine + 1
            return SourceLineSpan(
                startLine = startLine,
                endLine = endLine,
                textLine = startLine,
                textQuoteStartCol = quoteStartCol,
                textQuoteEndCol = quoteEndCol,
                textCharStartOffset = innerStartOffset,
                textCharEndOffset = innerEndOffset
            )
        }
    }

    private fun serializeLongTextWithSpanMap(
        doc: TextGridDocument,
        timeDecimals: Int?
    ): Pair<String, SourceSpanMap> {
        val b = SpanTrackingBuilder()
        b.appendLine("File type = \"ooTextFile\"")
        b.appendLine("Object class = \"TextGrid\"")
        b.appendLine("")
        b.appendLine("xmin = ${formatPraatDouble(doc.xmin, timeDecimals)} ")
        b.appendLine("xmax = ${formatPraatDouble(doc.xmax, timeDecimals)} ")
        b.appendLine("tiers? <exists> ")
        b.appendLine("size = ${doc.tiers.size} ")
        if (doc.tiers.isEmpty()) {
            b.appendLine("item []: (empty)")
            return b.sb.toString() to SourceSpanMap(lineCount = b.line - 1, tiers = emptyMap())
        }
        b.appendLine("item []: ")

        val tierSpans = LinkedHashMap<Int, TierSpanEntry>()
        doc.tiers.forEachIndexed { ti, tier ->
            val tierStartLine = b.appendLine("    item [${ti + 1}]:")
            when (tier) {
                is TextGridTier.IntervalTier -> {
                    b.appendLine("        class = \"IntervalTier\" ")
                    val nameSpan = b.appendQuotedLine("        name = ", tier.name, " ")
                    b.appendLine("        xmin = ${formatPraatDouble(tier.xmin, timeDecimals)} ")
                    b.appendLine("        xmax = ${formatPraatDouble(tier.xmax, timeDecimals)} ")
                    val safeIntervals = if (tier.intervals.isEmpty()) {
                        listOf(TextGridInterval(tier.xmin, tier.xmax, ""))
                    } else {
                        tier.intervals
                    }
                    val headerEndLine = b.appendLine("        intervals: size = ${safeIntervals.size} ")
                    val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
                    safeIntervals.forEachIndexed { ii, iv ->
                        val itemStartLine = b.appendLine("        intervals [${ii + 1}]:")
                        b.appendLine("            xmin = ${formatPraatDouble(iv.xmin, timeDecimals)} ")
                        b.appendLine("            xmax = ${formatPraatDouble(iv.xmax, timeDecimals)} ")
                        val textSpan = b.appendQuotedLine("            text = ", iv.text, " ")
                        itemSpans[ii] = textSpan.copy(startLine = itemStartLine)
                    }
                    tierSpans[ti] = TierSpanEntry(
                        tierIndex = ti,
                        headerSpan = nameSpan.copy(startLine = tierStartLine, endLine = headerEndLine),
                        itemSpans = itemSpans
                    )
                }

                is TextGridTier.TextTier -> {
                    b.appendLine("        class = \"TextTier\" ") // NEVER "PointTier"
                    val nameSpan = b.appendQuotedLine("        name = ", tier.name, " ")
                    b.appendLine("        xmin = ${formatPraatDouble(tier.xmin, timeDecimals)} ")
                    b.appendLine("        xmax = ${formatPraatDouble(tier.xmax, timeDecimals)} ")
                    val headerEndLine = b.appendLine("        points: size = ${tier.points.size} ")
                    val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
                    tier.points.forEachIndexed { pi, pt ->
                        val itemStartLine = b.appendLine("        points [${pi + 1}]:")
                        b.appendLine("            number = ${formatPraatDouble(pt.number, timeDecimals)} ")
                        val markSpan = b.appendQuotedLine("            mark = ", pt.mark, " ")
                        itemSpans[pi] = markSpan.copy(startLine = itemStartLine)
                    }
                    tierSpans[ti] = TierSpanEntry(
                        tierIndex = ti,
                        headerSpan = nameSpan.copy(startLine = tierStartLine, endLine = headerEndLine),
                        itemSpans = itemSpans
                    )
                }
            }
        }
        return b.sb.toString() to SourceSpanMap(lineCount = b.line - 1, tiers = tierSpans)
    }

    private fun serializeShortTextWithSpanMap(
        doc: TextGridDocument,
        timeDecimals: Int?
    ): Pair<String, SourceSpanMap> {
        val b = SpanTrackingBuilder()
        b.appendLine("File type = \"ooTextFile\"")
        b.appendLine("Object class = \"TextGrid\"")
        b.appendLine("")
        b.appendLine(formatPraatDouble(doc.xmin, timeDecimals))
        b.appendLine(formatPraatDouble(doc.xmax, timeDecimals))
        b.appendLine("<exists>")
        b.appendLine(doc.tiers.size.toString())

        val tierSpans = LinkedHashMap<Int, TierSpanEntry>()
        doc.tiers.forEachIndexed { ti, tier ->
            when (tier) {
                is TextGridTier.IntervalTier -> {
                    val tierStartLine = b.appendLine("\"IntervalTier\"")
                    val nameSpan = b.appendQuotedLine("", tier.name)
                    b.appendLine(formatPraatDouble(tier.xmin, timeDecimals))
                    b.appendLine(formatPraatDouble(tier.xmax, timeDecimals))
                    val safeIntervals = if (tier.intervals.isEmpty()) {
                        listOf(TextGridInterval(tier.xmin, tier.xmax, ""))
                    } else {
                        tier.intervals
                    }
                    val headerEndLine = b.appendLine(safeIntervals.size.toString())
                    val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
                    safeIntervals.forEachIndexed { ii, iv ->
                        val itemStartLine = b.appendLine(formatPraatDouble(iv.xmin, timeDecimals))
                        b.appendLine(formatPraatDouble(iv.xmax, timeDecimals))
                        val textSpan = b.appendQuotedLine("", iv.text)
                        itemSpans[ii] = textSpan.copy(startLine = itemStartLine)
                    }
                    tierSpans[ti] = TierSpanEntry(
                        tierIndex = ti,
                        headerSpan = nameSpan.copy(startLine = tierStartLine, endLine = headerEndLine),
                        itemSpans = itemSpans
                    )
                }

                is TextGridTier.TextTier -> {
                    val tierStartLine = b.appendLine("\"TextTier\"")
                    val nameSpan = b.appendQuotedLine("", tier.name)
                    b.appendLine(formatPraatDouble(tier.xmin, timeDecimals))
                    b.appendLine(formatPraatDouble(tier.xmax, timeDecimals))
                    val headerEndLine = b.appendLine(tier.points.size.toString())
                    val itemSpans = LinkedHashMap<Int, SourceLineSpan>()
                    tier.points.forEachIndexed { pi, pt ->
                        val itemStartLine = b.appendLine(formatPraatDouble(pt.number, timeDecimals))
                        val markSpan = b.appendQuotedLine("", pt.mark)
                        itemSpans[pi] = markSpan.copy(startLine = itemStartLine)
                    }
                    tierSpans[ti] = TierSpanEntry(
                        tierIndex = ti,
                        headerSpan = nameSpan.copy(startLine = tierStartLine, endLine = headerEndLine),
                        itemSpans = itemSpans
                    )
                }
            }
        }
        return b.sb.toString() to SourceSpanMap(lineCount = b.line - 1, tiers = tierSpans)
    }

    private fun serializeChronologicalWithSpanMap(
        doc: TextGridDocument,
        timeDecimals: Int?
    ): Pair<String, SourceSpanMap> {
        val b = SpanTrackingBuilder()
        b.appendLine(CHRONOLOGICAL_HEADER)
        b.appendLine("${formatPraatDouble(doc.xmin, timeDecimals)} ${formatPraatDouble(doc.xmax, timeDecimals)}   ! Time domain.")
        b.appendLine("${doc.tiers.size}   ! Number of tiers.")

        val headerSpans = LinkedHashMap<Int, SourceLineSpan>()
        val itemSpansByTier = LinkedHashMap<Int, MutableMap<Int, SourceLineSpan>>()

        data class ChronEvent(
            val time: Double,
            val tierIndex: Int,
            val itemIndex: Int,
            val tierName: String,
            val isInterval: Boolean,
            val xminOrNum: Double,
            val xmax: Double,
            val text: String
        )

        val events = mutableListOf<ChronEvent>()
        doc.tiers.forEachIndexed { ti, tier ->
            itemSpansByTier[ti] = LinkedHashMap()
            val clsName = tier.kind.praatClassName
            val escName = escapePraatString(tier.name)
            val lineStart = b.appendLine(
                "\"$clsName\" \"$escName\" ${formatPraatDouble(tier.xmin, timeDecimals)} ${formatPraatDouble(tier.xmax, timeDecimals)}"
            )
            headerSpans[ti] = SourceLineSpan(startLine = lineStart, endLine = lineStart)

            when (tier) {
                is TextGridTier.IntervalTier -> {
                    val safeIntervals = if (tier.intervals.isEmpty()) {
                        listOf(TextGridInterval(tier.xmin, tier.xmax, ""))
                    } else {
                        tier.intervals
                    }
                    safeIntervals.forEachIndexed { ii, iv ->
                        events.add(ChronEvent(iv.xmin, ti, ii, tier.name, true, iv.xmin, iv.xmax, iv.text))
                    }
                }
                is TextGridTier.TextTier -> {
                    tier.points.forEachIndexed { pi, pt ->
                        events.add(ChronEvent(pt.number, ti, pi, tier.name, false, pt.number, pt.number, pt.mark))
                    }
                }
            }
        }

        events.sortWith(compareBy<ChronEvent>({ it.time }, { it.tierIndex }, { it.itemIndex }))

        for (ev in events) {
            b.appendLine("")
            val safeCommentName = ev.tierName.replace('\n', ' ').replace('\r', ' ')
            b.appendLine("! $safeCommentName:")
            val startLine = if (ev.isInterval) {
                b.appendLine("${ev.tierIndex + 1} ${formatPraatDouble(ev.xminOrNum, timeDecimals)} ${formatPraatDouble(ev.xmax, timeDecimals)}")
            } else {
                b.appendLine("${ev.tierIndex + 1} ${formatPraatDouble(ev.xminOrNum, timeDecimals)} ")
            }
            val textSpan = b.appendQuotedLine("", ev.text)
            itemSpansByTier[ev.tierIndex]?.put(ev.itemIndex, textSpan.copy(startLine = startLine))
        }

        val tierSpans = LinkedHashMap<Int, TierSpanEntry>()
        for (ti in doc.tiers.indices) {
            tierSpans[ti] = TierSpanEntry(
                tierIndex = ti,
                headerSpan = headerSpans[ti] ?: SourceLineSpan(1, 1),
                itemSpans = itemSpansByTier[ti].orEmpty()
            )
        }
        return b.sb.toString().trimEnd('\n') to SourceSpanMap(lineCount = b.line - 1, tiers = tierSpans)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun sortAndDeduplicateIntervals(
        raw: List<Pair<TextGridInterval, SourceLineSpan?>>
    ): List<Pair<TextGridInterval, SourceLineSpan?>> {
        if (raw.size <= 1) return raw
        val sorted = raw.sortedWith(compareBy<Pair<TextGridInterval, SourceLineSpan?>> {
            val v = it.first.xmin
            if (v.isNaN()) Double.POSITIVE_INFINITY else v
        })
        val out = ArrayList<Pair<TextGridInterval, SourceLineSpan?>>(sorted.size)
        for (entry in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.first.xmin == entry.first.xmin) {
                // Praat SortedSetOfDoubleOf silently discards later duplicate start timestamp
                continue
            }
            out.add(entry)
        }
        return out
    }

    private fun sortAndDeduplicatePoints(
        raw: List<Pair<TextGridPoint, SourceLineSpan?>>
    ): List<Pair<TextGridPoint, SourceLineSpan?>> {
        if (raw.size <= 1) return raw
        val sorted = raw.sortedWith(compareBy<Pair<TextGridPoint, SourceLineSpan?>> {
            val v = it.first.number
            if (v.isNaN()) Double.POSITIVE_INFINITY else v
        })
        val out = ArrayList<Pair<TextGridPoint, SourceLineSpan?>>(sorted.size)
        for (entry in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.first.number == entry.first.number) {
                continue
            }
            out.add(entry)
        }
        return out
    }

    private fun anomaliesToDiagnostics(
        anomalies: List<TextGridAnomaly>,
        spanMap: SourceSpanMap
    ): List<TextGridDiagnostic> {
        return anomalies.map { a ->
            val code = when (a.kind) {
                TextGridAnomalyKind.GAP -> DiagnosticCode.GAP
                TextGridAnomalyKind.OVERLAP -> DiagnosticCode.OVERLAP
                TextGridAnomalyKind.ZERO_DURATION -> DiagnosticCode.ZERO_DURATION
                TextGridAnomalyKind.MICRO_SEGMENT -> DiagnosticCode.MICRO_SEGMENT
                TextGridAnomalyKind.OUT_OF_DOMAIN -> DiagnosticCode.OUT_OF_DOMAIN
                TextGridAnomalyKind.POINT_OUT_OF_DOMAIN -> DiagnosticCode.POINT_OUT_OF_DOMAIN
            }
            val line = when {
                a.itemIndex != null -> spanMap.spanForItem(a.tierIndex, a.itemIndex)?.startLine
                else -> spanMap.spanForTier(a.tierIndex)?.startLine
            } ?: 1
            TextGridDiagnostic(
                severity = DiagnosticSeverity.NON_FATAL_ANOMALY,
                code = code,
                message = a.message,
                line = line,
                column = 1,
                tierIndex = a.tierIndex,
                itemIndex = a.itemIndex
            )
        }
    }

    private fun countLines(text: String): Int {
        if (text.isEmpty()) return 0
        return text.count { it == '\n' } + 1
    }

    private fun fatalResult(
        code: DiagnosticCode,
        message: String,
        line: Int,
        column: Int,
        normalizedSource: String? = null,
        detectedEncoding: String = "UTF-8",
        detectedVariant: TextGridFormatVariant = TextGridFormatVariant.LONG_TEXT
    ): TextGridParseResult {
        return TextGridParseResult(
            document = null,
            collectionDocuments = emptyList(),
            detectedVariant = detectedVariant,
            detectedEncoding = detectedEncoding,
            normalizedSource = normalizedSource,
            spanMap = SourceSpanMap(),
            diagnostics = listOf(
                TextGridDiagnostic(
                    severity = DiagnosticSeverity.FATAL_SYNTAX,
                    code = code,
                    message = message,
                    line = line,
                    column = column
                )
            ),
            anomalies = emptyList()
        )
    }
}
