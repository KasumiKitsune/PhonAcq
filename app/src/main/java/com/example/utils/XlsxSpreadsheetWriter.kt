package com.example.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin Office Open XML (.xlsx) workbook writer and reader.
 * Zero external dependencies; compatible with Excel, WPS, Numbers, LibreOffice, Python openpyxl, and R readxl.
 */
object XlsxSpreadsheetWriter {

    data class SheetSpec(
        val name: String,
        val headers: List<String>,
        val rows: List<List<Any?>>
    )

    private const val STYLE_DEFAULT = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_INT = 2
    private const val STYLE_FLOAT_2 = 3
    private const val STYLE_FLOAT_4 = 4

    fun toByteArray(sheets: List<SheetSpec>): ByteArray {
        val baos = ByteArrayOutputStream()
        writeWorkbook(baos, sheets)
        return baos.toByteArray()
    }

    fun writeWorkbook(outputStream: OutputStream, sheets: List<SheetSpec>) {
        val effectiveSheets = if (sheets.isEmpty()) {
            listOf(SheetSpec(name = "Sheet1", headers = emptyList(), rows = emptyList()))
        } else {
            sheets
        }

        // Deduplicate & sanitize sheet names (Excel max 31 chars, no : \ / ? * [ ])
        val usedNames = HashSet<String>()
        val safeSheetNames = effectiveSheets.mapIndexed { idx, spec ->
            var base = sanitizeSheetName(spec.name).ifBlank { "Sheet${idx + 1}" }
            if (base.length > 31) base = base.take(31)
            var candidate = base
            var suffix = 2
            while (!usedNames.add(candidate.lowercase(Locale.ROOT))) {
                val tail = "_$suffix"
                candidate = base.take(31 - tail.length) + tail
                suffix++
            }
            candidate
        }

        // Collect Shared Strings table so every spreadsheet reader (including SpreadsheetWordImporter) parses cells natively
        val sharedStringIndex = LinkedHashMap<String, Int>()
        var totalStringRefs = 0
        fun internString(raw: String): Int {
            totalStringRefs++
            val clean = sanitizeXmlChars(raw)
            return sharedStringIndex.getOrPut(clean) { sharedStringIndex.size }
        }

        // Pre-intern all headers and string cells so sharedStrings.xml is complete
        for (sheet in effectiveSheets) {
            for (h in sheet.headers) {
                internString(h)
            }
            for (row in sheet.rows) {
                for (cell in row) {
                    when (cell) {
                        null -> Unit
                        is Byte, is Short, is Int, is Long -> Unit
                        is Float -> if (!cell.isFinite()) internString("")
                        is Double -> if (!cell.isFinite()) internString("")
                        is Boolean -> internString(if (cell) "TRUE" else "FALSE")
                        else -> {
                            val s = cell.toString()
                            if (s.isNotEmpty()) internString(s)
                        }
                    }
                }
            }
        }

        val zos = ZipOutputStream(outputStream)
        try {
            // 1. [Content_Types].xml
            putXmlEntry(zos, "[Content_Types].xml", buildContentTypesXml(effectiveSheets.size))

            // 2. _rels/.rels
            putXmlEntry(zos, "_rels/.rels", buildRootRelsXml())

            // 3. xl/workbook.xml
            putXmlEntry(zos, "xl/workbook.xml", buildWorkbookXml(safeSheetNames))

            // 4. xl/_rels/workbook.xml.rels
            putXmlEntry(zos, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(effectiveSheets.size))

            // 5. xl/styles.xml
            putXmlEntry(zos, "xl/styles.xml", buildStylesXml())

            // 6. xl/sharedStrings.xml
            putXmlEntry(
                zos,
                "xl/sharedStrings.xml",
                buildSharedStringsXml(sharedStringIndex.keys.toList(), totalStringRefs)
            )

            // 7. xl/worksheets/sheet{i}.xml
            effectiveSheets.forEachIndexed { index, spec ->
                val sheetXml = buildWorksheetXml(spec, sharedStringIndex)
                putXmlEntry(zos, "xl/worksheets/sheet${index + 1}.xml", sheetXml)
            }

            zos.finish()
        } finally {
            // Do not close underlying outputStream when embedded inside outer stream unless caller owns it;
            // flushing zos after finish() writes central directory cleanly.
            zos.flush()
        }
    }

    private fun putXmlEntry(zos: ZipOutputStream, entryName: String, xmlContent: String) {
        zos.putNextEntry(ZipEntry(entryName))
        zos.write(xmlContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun buildWorkbookXml(sheetNames: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheets>""")
        sheetNames.forEachIndexed { idx, name ->
            val sheetId = idx + 1
            append("""<sheet name="${escapeXml(name)}" sheetId="$sheetId" r:id="rId$sheetId"/>""")
        }
        append("""</sheets>""")
        append("""</workbook>""")
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        val stylesId = sheetCount + 1
        val sstId = sheetCount + 2
        append("""<Relationship Id="rId$stylesId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""<Relationship Id="rId$sstId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("""</Relationships>""")
    }

    private fun buildStylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="2">""" +
            """<numFmt numFmtId="164" formatCode="0.00"/>""" +
            """<numFmt numFmtId="165" formatCode="0.0000"/>""" +
            """</numFmts>""" +
            """<fonts count="2">""" +
            """<font><sz val="11"/><color rgb="FF1F2937"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>""" +
            """</fonts>""" +
            """<fills count="3">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFEAF1F8"/><bgColor indexed="64"/></patternFill></fill>""" +
            """</fills>""" +
            """<borders count="2">""" +
            """<border><left/><right/><top/><bottom/><diagonal/></border>""" +
            """<border><left/><right/><top/><bottom style="thin"><color rgb="FFCBD5E1"/></bottom><diagonal/></border>""" +
            """</borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="5">""" +
            // 0: default text / general
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            // 1: bold header with soft blue-gray fill & thin bottom border
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            // 2: integer
            """<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            // 3: 2-decimal float
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            // 4: 4-decimal float
            """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """</cellXfs>""" +
            """</styleSheet>"""

    private fun buildSharedStringsXml(uniqueStrings: List<String>, totalCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="$totalCount" uniqueCount="${uniqueStrings.size}">""")
        for (str in uniqueStrings) {
            val preserve = str.startsWith(" ") || str.endsWith(" ") || str.contains('\n') || str.contains('\t')
            if (preserve) {
                append("""<si><t xml:space="preserve">${escapeXml(str)}</t></si>""")
            } else {
                append("""<si><t>${escapeXml(str)}</t></si>""")
            }
        }
        append("""</sst>""")
    }

    private fun buildWorksheetXml(
        spec: SheetSpec,
        sharedStringIndex: Map<String, Int>
    ): String {
        val colCount = max(
            spec.headers.size,
            spec.rows.maxOfOrNull { it.size } ?: 0
        )

        // Compute CJK-aware auto column widths
        val colMaxVisualWidth = IntArray(max(1, colCount)) { 10 }
        spec.headers.forEachIndexed { c, header ->
            colMaxVisualWidth[c] = max(colMaxVisualWidth[c], estimateVisualWidth(header) + 4)
        }
        for (row in spec.rows) {
            row.forEachIndexed { c, cell ->
                if (c < colMaxVisualWidth.size && cell != null) {
                    val text = when (cell) {
                        is Float -> if (cell.isFinite()) String.format(Locale.US, "%.2f", cell) else ""
                        is Double -> if (cell.isFinite()) String.format(Locale.US, "%.2f", cell) else ""
                        else -> cell.toString()
                    }
                    colMaxVisualWidth[c] = max(colMaxVisualWidth[c], min(56, estimateVisualWidth(text) + 3))
                }
            }
        }

        val hasHeader = spec.headers.isNotEmpty()
        val totalRows = (if (hasHeader) 1 else 0) + spec.rows.size
        val lastColRef = columnName(max(0, colCount - 1))
        val lastRowNum = max(1, totalRows)

        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<dimension ref="A1:$lastColRef$lastRowNum"/>""")
            if (hasHeader) {
                append("""<sheetViews><sheetView tabSelected="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            } else {
                append("""<sheetViews><sheetView workbookViewId="0"/></sheetViews>""")
            }
            append("""<sheetFormatPr defaultRowHeight="18"/>""")
            if (colCount > 0) {
                append("""<cols>""")
                for (c in 0 until colCount) {
                    val idx1 = c + 1
                    val width = colMaxVisualWidth[c].coerceIn(10, 58)
                    append("""<col min="$idx1" max="$idx1" width="$width" customWidth="1"/>""")
                }
                append("""</cols>""")
            }
            append("""<sheetData>""")
            var currentRowNum = 1
            if (hasHeader) {
                append("""<row r="1" ht="22" customHeight="1">""")
                spec.headers.forEachIndexed { c, header ->
                    val ref = "${columnName(c)}1"
                    val clean = sanitizeXmlChars(header)
                    val sstIdx = sharedStringIndex[clean] ?: 0
                    append("""<c r="$ref" s="$STYLE_HEADER" t="s"><v>$sstIdx</v></c>""")
                }
                append("""</row>""")
                currentRowNum++
            }
            for (row in spec.rows) {
                append("""<row r="$currentRowNum">""")
                row.forEachIndexed { c, cell ->
                    val ref = "${columnName(c)}$currentRowNum"
                    appendCellXml(this, ref, cell, sharedStringIndex)
                }
                append("""</row>""")
                currentRowNum++
            }
            append("""</sheetData>""")
            append("""</worksheet>""")
        }
    }

    private fun appendCellXml(
        sb: StringBuilder,
        cellRef: String,
        value: Any?,
        sharedStringIndex: Map<String, Int>
    ) {
        when (value) {
            null -> Unit
            is Byte, is Short, is Int, is Long -> {
                sb.append("""<c r="$cellRef" s="$STYLE_INT" t="n"><v>$value</v></c>""")
            }
            is Float -> {
                if (value.isFinite()) {
                    val formatted = trimTrailingZeroes(String.format(Locale.US, "%.4f", value))
                    val style = if (kotlin.math.abs(value) < 10f && value != value.toInt().toFloat()) STYLE_FLOAT_4 else STYLE_FLOAT_2
                    sb.append("""<c r="$cellRef" s="$style" t="n"><v>$formatted</v></c>""")
                }
            }
            is Double -> {
                if (value.isFinite()) {
                    val formatted = trimTrailingZeroes(String.format(Locale.US, "%.4f", value))
                    val style = if (kotlin.math.abs(value) < 10.0 && value != value.toInt().toDouble()) STYLE_FLOAT_4 else STYLE_FLOAT_2
                    sb.append("""<c r="$cellRef" s="$style" t="n"><v>$formatted</v></c>""")
                }
            }
            is Boolean -> {
                val clean = if (value) "TRUE" else "FALSE"
                val sstIdx = sharedStringIndex[clean] ?: 0
                sb.append("""<c r="$cellRef" s="$STYLE_DEFAULT" t="s"><v>$sstIdx</v></c>""")
            }
            else -> {
                val clean = sanitizeXmlChars(value.toString())
                if (clean.isNotEmpty()) {
                    val sstIdx = sharedStringIndex[clean]
                    if (sstIdx != null) {
                        sb.append("""<c r="$cellRef" s="$STYLE_DEFAULT" t="s"><v>$sstIdx</v></c>""")
                    } else {
                        sb.append("""<c r="$cellRef" s="$STYLE_DEFAULT" t="inlineStr"><is><t xml:space="preserve">${escapeXml(clean)}</t></is></c>""")
                    }
                }
            }
        }
    }

    private fun trimTrailingZeroes(numStr: String): String {
        if (!numStr.contains('.')) return numStr
        var s = numStr.trimEnd('0')
        if (s.endsWith('.')) s = s.dropLast(1)
        return if (s.isEmpty() || s == "-") "0" else s
    }

    private fun estimateVisualWidth(text: String): Int {
        var w = 0
        for (ch in text) {
            w += if (ch.code > 0x2E7F) 2 else 1
        }
        return w
    }

    fun columnName(colIndexZeroBased: Int): String {
        var idx = colIndexZeroBased
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A'.code + (idx % 26)).toChar())
            idx = (idx / 26) - 1
        }
        return sb.toString()
    }

    fun columnIndexFromRef(cellRef: String): Int {
        var col = 0
        var hasLetters = false
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
                hasLetters = true
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
                hasLetters = true
            } else {
                break
            }
        }
        return if (hasLetters) col - 1 else 0
    }

    private fun sanitizeSheetName(raw: String): String {
        return sanitizeXmlChars(raw)
            .replace(Regex("""[:\\/?*\[\]]"""), "_")
            .trim()
            .trim('\'')
    }

    fun sanitizeXmlChars(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val valid = cp == 0x9 || cp == 0xA || cp == 0xD ||
                (cp in 0x20..0xD7FF) ||
                (cp in 0xE000..0xFFFD) ||
                (cp in 0x10000..0x10FFFF)
            if (valid) {
                sb.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length + 16)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescapeXml(input: String): String {
        if (!input.contains('&')) return input
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("&#x([0-9a-fA-F]+);")) { mr ->
                mr.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: mr.value
            }
            .replace(Regex("&#([0-9]+);")) { mr ->
                mr.groupValues[1].toIntOrNull(10)?.let { String(Character.toChars(it)) } ?: mr.value
            }
            .replace("&amp;", "&")
    }

    // ==================== Lightweight Pure-Kotlin Reader (Archive Import & Tests) ====================

    /**
     * Reads all sheets of an `.xlsx` byte array as an ordered map of `sheetName -> rows`.
     * Works in both Android runtime and pure JVM unit tests (no `android.util.Xml` dependency).
     */
    fun readAllSheets(xlsxBytes: ByteArray): LinkedHashMap<String, List<List<String>>> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.toString(Charsets.UTF_8)
            ?.let { parseSharedStringsXml(it) }
            ?: emptyList()

        val workbookXml = entries["xl/workbook.xml"]?.toString(Charsets.UTF_8).orEmpty()
        val relsXml = entries["xl/_rels/workbook.xml.rels"]?.toString(Charsets.UTF_8).orEmpty()

        val relTargets = HashMap<String, String>()
        val relRegex = Regex("""<Relationship\b[^>]*/>|<Relationship\b[^>]*>.*?</Relationship>""", RegexOption.DOT_MATCHES_ALL)
        for (m in relRegex.findAll(relsXml)) {
            val tag = m.value
            val id = extractXmlAttr(tag, "Id") ?: continue
            val target = extractXmlAttr(tag, "Target") ?: continue
            val normalized = when {
                target.startsWith("/xl/") -> target.removePrefix("/")
                target.startsWith("xl/") -> target
                else -> "xl/${target.removePrefix("/")}"
            }
            relTargets[id] = normalized
        }

        val result = LinkedHashMap<String, List<List<String>>>()
        val sheetTagRegex = Regex("""<sheet\b[^>]*/?>""")
        var sheetCounter = 1
        for (m in sheetTagRegex.findAll(workbookXml)) {
            val tag = m.value
            val rawName = extractXmlAttr(tag, "name")?.let { unescapeXml(it) } ?: "Sheet$sheetCounter"
            val rId = extractXmlAttr(tag, "r:id") ?: extractXmlAttr(tag, "id")
            val targetPath = (if (rId != null) relTargets[rId] else null)
                ?: "xl/worksheets/sheet$sheetCounter.xml"
            val sheetXml = entries[targetPath]?.toString(Charsets.UTF_8)
            if (sheetXml != null) {
                result[rawName] = parseWorksheetXml(sheetXml, sharedStrings)
            }
            sheetCounter++
        }

        if (result.isEmpty()) {
            val fallback = entries["xl/worksheets/sheet1.xml"]?.toString(Charsets.UTF_8)
            if (fallback != null) {
                result["Sheet1"] = parseWorksheetXml(fallback, sharedStrings)
            }
        }
        return result
    }

    fun readSheetRows(xlsxBytes: ByteArray, sheetIndex: Int = 0): List<List<String>> {
        val sheets = readAllSheets(xlsxBytes).values.toList()
        return sheets.getOrElse(sheetIndex) { emptyList() }
    }

    private fun extractXmlAttr(tag: String, attrName: String): String? {
        val regex = Regex("""(?:^|\s)${Regex.escape(attrName)}\s*=\s*"([^"]*)"""")
        return regex.find(tag)?.groupValues?.get(1)
    }

    private fun parseSharedStringsXml(xml: String): List<String> {
        val items = ArrayList<String>()
        val siRegex = Regex("""<si\b[^>]*>(.*?)</si>""", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)
        for (siMatch in siRegex.findAll(xml)) {
            val inner = siMatch.groupValues[1]
            val sb = StringBuilder()
            for (tMatch in tRegex.findAll(inner)) {
                sb.append(unescapeXml(tMatch.groupValues[1]))
            }
            items.add(sb.toString())
        }
        return items
    }

    private fun parseWorksheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val rowRegex = Regex("""<row\b[^>]*>(.*?)</row>""", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("""<c\b([^/>]*)(?:/>|>(.*?)</c>)""", RegexOption.DOT_MATCHES_ALL)
        val vRegex = Regex("""<v\b[^>]*>(.*?)</v>""", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("""<t(?:\s[^>]*)?>(.*?)</t>""", RegexOption.DOT_MATCHES_ALL)

        for (rowMatch in rowRegex.findAll(xml)) {
            val rowBody = rowMatch.groupValues[1]
            val cellValuesByCol = HashMap<Int, String>()
            var maxCol = -1
            var sequentialCol = 0

            for (cellMatch in cellRegex.findAll(rowBody)) {
                val attrs = cellMatch.groupValues[1]
                val cellInner = cellMatch.groupValues[2]
                val ref = extractXmlAttr(attrs, "r")
                val colIdx = if (!ref.isNullOrBlank()) columnIndexFromRef(ref) else sequentialCol
                sequentialCol = colIdx + 1
                maxCol = max(maxCol, colIdx)

                val type = extractXmlAttr(attrs, "t")
                val resolved = when (type) {
                    "s" -> {
                        val idx = vRegex.find(cellInner)?.groupValues?.get(1)?.trim()?.toIntOrNull()
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                    }
                    "inlineStr" -> {
                        val sb = StringBuilder()
                        for (tm in tRegex.findAll(cellInner)) {
                            sb.append(unescapeXml(tm.groupValues[1]))
                        }
                        sb.toString()
                    }
                    "b" -> {
                        val raw = vRegex.find(cellInner)?.groupValues?.get(1)?.trim()
                        if (raw == "1" || raw.equals("true", ignoreCase = true)) "TRUE" else "FALSE"
                    }
                    else -> {
                        vRegex.find(cellInner)?.groupValues?.get(1)?.let { unescapeXml(it) }.orEmpty()
                    }
                }
                cellValuesByCol[colIdx] = resolved
            }

            if (maxCol >= 0) {
                val rowList = MutableList(maxCol + 1) { c -> cellValuesByCol[c].orEmpty() }
                rows.add(rowList)
            }
        }
        return rows
    }
}
