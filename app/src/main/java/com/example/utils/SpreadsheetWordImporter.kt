package com.example.utils

import com.example.data.ParsedWord
import com.example.i18n.S
import com.example.i18n.withArgs
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Zero-dependency wordlist file importer designed for humanities & linguistics users:
 * - Native `.xlsx` Excel workbook parsing (sharedStrings + worksheet XML cells)
 * - Native `.docx` Word table & paragraph parsing
 * - Smart charset auto-detection for `.csv` / `.tsv` / `.txt` (UTF-8, GB18030/GBK, UTF-16LE/BE)
 * - Header-aware multi-column CSV / TSV column mapping (Index, Group, Word, Remark/IPA/Gloss)
 * - Safe binary file guardrails (prevents importing `.xls`, `.pdf`, or media bytes as garbled words)
 */
object SpreadsheetWordImporter {

    data class WordFileImportResult(
        val words: List<ParsedWord>,
        val errorMessage: String? = null,
        val sourceSummary: String = ""
    )

    private val INDEX_HEADERS = setOf(
        "序号", "编号", "题号", "词号", "行号", "id", "index", "no", "no.", "num", "#"
    )
    private val WORD_HEADERS = setOf(
        "词条", "例字", "汉字", "字词", "调查词", "调查字", "单字", "词语", "词汇",
        "目标字", "目标词", "条目", "本字", "word", "words", "text", "label", "item", "lexeme", "char", "character"
    )
    private val GROUP_HEADERS = setOf(
        "分组", "类别", "分类", "组别", "韵摄", "韵部", "调类", "声调", "声母", "韵母",
        "古音分类", "古调类", "词类", "主题", "group", "groups", "category", "type", "section", "tone_category"
    )
    private val REMARK_HEADERS = setOf(
        "备注", "释义", "提示", "说明", "音标", "国际音标", "参考音", "读音", "拼音",
        "古音", "古音地位", "普通话", "引导语", "注解", "例句", "note", "notes", "remark",
        "ipa", "gloss", "meaning", "pinyin", "hint", "description", "comment"
    )

    private val PURE_NUMBER_INDEX_REGEX = Regex("^#?\\d{1,5}[.、)）]?$")

    fun importFromBytes(
        bytes: ByteArray,
        fileName: String? = null,
        fallbackTextParser: (String) -> List<ParsedWord>
    ): WordFileImportResult {
        if (bytes.isEmpty()) {
            return WordFileImportResult(emptyList(), errorMessage = S.wordEditor.importErrEmptyFile)
        }

        val lowerName = fileName?.lowercase(Locale.ROOT).orEmpty()

        // 1. Legacy OLE2 Compound Document (.xls / .doc)
        if (bytes.size >= 4 &&
            bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        ) {
            return WordFileImportResult(
                emptyList(),
                errorMessage = S.wordEditor.importErrLegacyBinary
            )
        }

        // 2. PDF Document
        if (bytes.size >= 4 &&
            bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
        ) {
            return WordFileImportResult(
                emptyList(),
                errorMessage = S.wordEditor.importErrPdfUnsupported
            )
        }

        // 3. ZIP-based Office Open XML (.xlsx / .docx) or archive
        if (bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        ) {
            return parseZipContainer(bytes, fallbackTextParser)
        }

        // 4. Guard against arbitrary binary files (e.g. WAV, PNG, JPG) when not UTF-16
        val hasUtf16Bom = bytes.size >= 2 && (
            (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            )
        if (!hasUtf16Bom && hasLikelyBinaryNullBytes(bytes)) {
            return WordFileImportResult(
                emptyList(),
                errorMessage = S.wordEditor.importErrUnsupportedBinary
            )
        }

        // 5. Decode text with smart UTF-8 / GB18030 / UTF-16 detection
        val decodedText = decodeTextSmart(bytes)
        if (decodedText.isBlank()) {
            return WordFileImportResult(emptyList(), errorMessage = S.wordEditor.importErrNoValidText)
        }

        // 6. If CSV/TSV or structured multi-column lines, parse via header-aware tabular parser first
        val isCsvOrTsv = lowerName.endsWith(".csv") || lowerName.endsWith(".tsv") || looksLikeStructuredTableText(decodedText)
        if (isCsvOrTsv) {
            val tabularWords = parseStructuredTableString(decodedText)
            if (tabularWords.isNotEmpty()) {
                return WordFileImportResult(tabularWords, sourceSummary = S.wordEditor.importSourceTable)
            }
        }

        val parsed = fallbackTextParser(decodedText)
        return if (parsed.isEmpty()) {
            WordFileImportResult(emptyList(), errorMessage = S.wordEditor.importErrNoValidWords)
        } else {
            WordFileImportResult(parsed, sourceSummary = S.wordEditor.importSourceText)
        }
    }

    /**
     * Parses clipboard or raw pasted text, automatically handling multi-column Excel TSV / CSV
     * with headers before delegating unstructured lines to [fallbackTextParser].
     */
    fun parseSmartClipboardOrText(
        rawText: String,
        fallbackTextParser: (String) -> List<ParsedWord>
    ): List<ParsedWord> {
        val clean = rawText.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n").trim()
        if (clean.isEmpty()) return emptyList()
        if (!clean.startsWith("{") && looksLikeStructuredTableText(clean)) {
            val tabular = parseStructuredTableString(clean)
            if (tabular.isNotEmpty()) return tabular
        }
        return fallbackTextParser(clean)
    }

    private fun parseZipContainer(
        bytes: ByteArray,
        fallbackTextParser: (String) -> List<ParsedWord>
    ): WordFileImportResult {
        val entries = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.replace('\\', '/')
                        if (name == "xl/sharedStrings.xml" ||
                            (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) ||
                            name == "word/document.xml"
                        ) {
                            entries[name] = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return WordFileImportResult(
                emptyList(),
                errorMessage = S.wordEditor.importErrCorruptFile.withArgs(
                    "msg" to (e.localizedMessage ?: S.wordEditor.importErrFormatAnomaly)
                )
            )
        }

        val sheetEntries = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { name ->
                Regex("sheet(\\d+)\\.xml").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 999
            }

        if (sheetEntries.isNotEmpty()) {
            val sharedStringsBytes = entries["xl/sharedStrings.xml"]
            val sharedStrings = if (sharedStringsBytes != null) {
                parseXlsxSharedStrings(sharedStringsBytes)
            } else {
                emptyList()
            }

            val allParsed = ArrayList<ParsedWord>()
            for (sheetName in sheetEntries) {
                val sheetBytes = entries[sheetName] ?: continue
                val rows = parseXlsxWorksheetRows(sheetBytes, sharedStrings)
                val sheetWords = parseTabularRowsToParsedWords(rows)
                allParsed.addAll(sheetWords)
            }
            return if (allParsed.isEmpty()) {
                WordFileImportResult(emptyList(), errorMessage = S.wordEditor.importErrExcelEmpty)
            } else {
                WordFileImportResult(allParsed, sourceSummary = "Excel")
            }
        }

        val docxBytes = entries["word/document.xml"]
        if (docxBytes != null) {
            val docxWords = parseDocxDocument(docxBytes, fallbackTextParser)
            return if (docxWords.isEmpty()) {
                WordFileImportResult(emptyList(), errorMessage = S.wordEditor.importErrWordEmpty)
            } else {
                WordFileImportResult(docxWords, sourceSummary = "Word")
            }
        }

        return WordFileImportResult(
            emptyList(),
            errorMessage = S.wordEditor.importErrZipNoExcel
        )
    }

    private fun createSafeDocumentBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        try {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {}
        try {
            setFeature("http://xml.org/sax/features/external-general-entities", false)
        } catch (_: Exception) {}
        try {
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        } catch (_: Exception) {}
    }.newDocumentBuilder()

    private fun parseXlsxSharedStrings(xmlBytes: ByteArray): List<String> {
        return try {
            val doc = createSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            val siNodes = doc.getElementsByTagName("si")
            val result = ArrayList<String>(siNodes.length)
            for (i in 0 until siNodes.length) {
                val si = siNodes.item(i)
                val sb = StringBuilder()
                collectSharedStringText(si, sb)
                result.add(sb.toString())
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun collectSharedStringText(node: Node, out: StringBuilder) {
        val nodeName = node.nodeName.substringAfter(':')
        // Skip phonetic reading furigana (<rPh>) inside Excel cells
        if (nodeName == "rPh" || nodeName == "phoneticPr") return
        if (nodeName == "t") {
            out.append(node.textContent.orEmpty())
            return
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectSharedStringText(children.item(i), out)
        }
    }

    private fun parseXlsxWorksheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        return try {
            val doc = createSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            val rowNodes = doc.getElementsByTagName("row")
            val rows = ArrayList<List<String>>(rowNodes.length)
            for (rIdx in 0 until rowNodes.length) {
                val rowEl = rowNodes.item(rIdx) as? Element ?: continue
                val cellNodes = rowEl.childNodes
                val rowMap = LinkedHashMap<Int, String>()
                var maxCol = -1
                var sequentialCol = 0

                for (cIdx in 0 until cellNodes.length) {
                    val cellEl = cellNodes.item(cIdx) as? Element ?: continue
                    if (cellEl.tagName.substringAfter(':') != "c") continue
                    val ref = cellEl.getAttribute("r")
                    val colIndex = if (ref.isNotEmpty()) {
                        colRefToIndex(ref).takeIf { it >= 0 } ?: sequentialCol
                    } else {
                        sequentialCol
                    }
                    sequentialCol = colIndex + 1
                    val cellType = cellEl.getAttribute("t")
                    val rawVal = extractXlsxCellValue(cellEl, cellType, sharedStrings).trim()
                    if (rawVal.isNotEmpty()) {
                        rowMap[colIndex] = rawVal
                        if (colIndex > maxCol) maxCol = colIndex
                    }
                }

                if (maxCol >= 0) {
                    val rowCells = MutableList(maxCol + 1) { col -> rowMap[col] ?: "" }
                    rows.add(rowCells)
                }
            }
            rows
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractXlsxCellValue(cellEl: Element, cellType: String, sharedStrings: List<String>): String {
        if (cellType == "inlineStr") {
            val sb = StringBuilder()
            val children = cellEl.childNodes
            for (i in 0 until children.length) {
                collectSharedStringText(children.item(i), sb)
            }
            return sb.toString()
        }

        var vText = ""
        val children = cellEl.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeName.substringAfter(':') == "v") {
                vText = child.textContent.orEmpty()
                break
            }
        }
        if (vText.isEmpty()) return ""

        return when (cellType) {
            "s" -> {
                val idx = vText.trim().toIntOrNull()
                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
            }
            "b" -> if (vText == "1") "TRUE" else "FALSE"
            "e" -> ""
            "str" -> vText
            else -> {
                // Numeric cell: clean trailing ".0" on integer numbers (e.g., 1.0 -> 1)
                if (vText.endsWith(".0") && vText.dropLast(2).all { it.isDigit() || it == '-' }) {
                    vText.dropLast(2)
                } else {
                    vText
                }
            }
        }
    }

    private fun colRefToIndex(cellRef: String): Int {
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
        return if (hasLetters) col - 1 else -1
    }

    private fun parseDocxDocument(
        xmlBytes: ByteArray,
        fallbackTextParser: (String) -> List<ParsedWord>
    ): List<ParsedWord> {
        return try {
            val doc = createSafeDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
            // First check if the Word document contains tables (<w:tbl>)
            val trNodes = doc.getElementsByTagName("w:tr").let {
                if (it.length > 0) it else doc.getElementsByTagName("tr")
            }
            if (trNodes.length >= 2) {
                val rows = ArrayList<List<String>>(trNodes.length)
                for (i in 0 until trNodes.length) {
                    val tr = trNodes.item(i) as? Element ?: continue
                    val cells = ArrayList<String>()
                    val childNodes = tr.childNodes
                    for (j in 0 until childNodes.length) {
                        val tc = childNodes.item(j)
                        if (tc.nodeName.substringAfter(':') == "tc") {
                            cells.add(extractWordNodeText(tc).trim())
                        }
                    }
                    if (cells.any { it.isNotEmpty() }) {
                        rows.add(cells)
                    }
                }
                val fromTable = parseTabularRowsToParsedWords(rows)
                if (fromTable.isNotEmpty()) return fromTable
            }

            // Otherwise extract paragraph lines (<w:p>)
            val pNodes = doc.getElementsByTagName("w:p").let {
                if (it.length > 0) it else doc.getElementsByTagName("p")
            }
            val sb = StringBuilder()
            for (i in 0 until pNodes.length) {
                val line = extractWordNodeText(pNodes.item(i)).trim()
                if (line.isNotEmpty()) {
                    sb.append(line).append('\n')
                }
            }
            fallbackTextParser(sb.toString())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractWordNodeText(node: Node): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            val local = n.nodeName.substringAfter(':')
            if (local == "t") {
                sb.append(n.textContent.orEmpty())
                return
            } else if (local == "tab") {
                sb.append('\t')
                return
            }
            val kids = n.childNodes
            for (i in 0 until kids.length) {
                walk(kids.item(i))
            }
        }
        walk(node)
        return sb.toString()
    }

    fun decodeTextSmart(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (isStrictValidUtf8(bytes)) {
            return String(bytes, Charsets.UTF_8)
        }
        val gbCharset = try {
            Charset.forName("GB18030")
        } catch (_: Exception) {
            try {
                Charset.forName("GBK")
            } catch (_: Exception) {
                Charsets.UTF_8
            }
        }
        return String(bytes, gbCharset)
    }

    private fun isStrictValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        val len = bytes.size
        while (i < len) {
            val b0 = bytes[i].toInt() and 0xFF
            when {
                b0 <= 0x7F -> i++
                b0 in 0xC2..0xDF -> {
                    if (i + 1 >= len) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 and 0xC0 != 0x80) return false
                    i += 2
                }
                b0 in 0xE0..0xEF -> {
                    if (i + 2 >= len) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    if ((b1 and 0xC0 != 0x80) || (b2 and 0xC0 != 0x80)) return false
                    if (b0 == 0xE0 && b1 < 0xA0) return false
                    if (b0 == 0xED && b1 >= 0xA0) return false // UTF-16 surrogates invalid in UTF-8
                    i += 3
                }
                b0 in 0xF0..0xF4 -> {
                    if (i + 3 >= len) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    val b2 = bytes[i + 2].toInt() and 0xFF
                    val b3 = bytes[i + 3].toInt() and 0xFF
                    if ((b1 and 0xC0 != 0x80) || (b2 and 0xC0 != 0x80) || (b3 and 0xC0 != 0x80)) return false
                    if (b0 == 0xF0 && b1 < 0x90) return false
                    if (b0 == 0xF4 && b1 > 0x8F) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun hasLikelyBinaryNullBytes(bytes: ByteArray): Boolean {
        val checkLen = minOf(bytes.size, 2048)
        var nulCount = 0
        for (i in 0 until checkLen) {
            if (bytes[i] == 0.toByte()) nulCount++
        }
        return nulCount >= 2
    }

    private fun looksLikeStructuredTableText(text: String): Boolean {
        val nonEmptyLines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .take(12)
            .toList()
        if (nonEmptyLines.isEmpty()) return false

        // Tab-separated lines from Excel copy-paste
        val tabLines = nonEmptyLines.count { it.contains('\t') }
        if (tabLines >= 1 && (nonEmptyLines.size == 1 || tabLines * 2 >= nonEmptyLines.size)) {
            return true
        }

        // Check if first row looks like a CSV header OR multiple lines share consistent comma column counts >= 2
        val firstCells = splitDelimitedLine(nonEmptyLines.first(), ',')
        if (firstCells.size >= 2 && isHeaderRow(firstCells)) {
            return true
        }
        if (nonEmptyLines.size >= 2) {
            val colCounts = nonEmptyLines.map { splitDelimitedLine(it, ',').size }
            val multiColLines = colCounts.filter { it >= 3 }
            if (multiColLines.size >= 2 && multiColLines.distinct().size <= 2) {
                return true
            }
        }
        return false
    }

    private fun parseStructuredTableString(text: String): List<ParsedWord> {
        val normalized = text.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val sampleLines = normalized.lineSequence().filter { it.isNotBlank() }.take(10).toList()
        val delimiter = if (sampleLines.count { it.contains('\t') } >= sampleLines.count { it.contains(',') }.coerceAtLeast(1)) {
            '\t'
        } else {
            ','
        }
        val rows = parseDelimitedRows(normalized, delimiter)
        return parseTabularRowsToParsedWords(rows)
    }

    private fun parseDelimitedRows(text: String, delimiter: Char): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val currentRow = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    currentRow.add(cell.toString().trim())
                    cell.setLength(0)
                }
                ch == '\n' && !inQuotes -> {
                    currentRow.add(cell.toString().trim())
                    cell.setLength(0)
                    if (currentRow.any { it.isNotEmpty() }) {
                        rows.add(ArrayList(currentRow))
                    }
                    currentRow.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString().trim())
            if (currentRow.any { it.isNotEmpty() }) {
                rows.add(ArrayList(currentRow))
            }
        }
        return rows
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        return parseDelimitedRows(line, delimiter).firstOrNull() ?: emptyList()
    }

    private fun normalizeHeaderToken(raw: String): String {
        return raw.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s_()（）\\[\\]【】*#:：/\\\\-]+"), "")
    }

    private fun isHeaderRow(cells: List<String>): Boolean {
        val nonEmpty = cells.map { normalizeHeaderToken(it) }.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return false
        var matchedKnown = 0
        for (tok in nonEmpty) {
            if (tok in INDEX_HEADERS || tok in WORD_HEADERS || tok in GROUP_HEADERS || tok in REMARK_HEADERS ||
                WORD_HEADERS.any { it.length >= 2 && tok.contains(it) } ||
                GROUP_HEADERS.any { it.length >= 2 && tok.contains(it) } ||
                REMARK_HEADERS.any { it.length >= 2 && tok.contains(it) }
            ) {
                matchedKnown++
            }
        }
        return matchedKnown >= 2 || (nonEmpty.size == 1 && nonEmpty[0] in WORD_HEADERS)
    }

    fun parseTabularRowsToParsedWords(rawRows: List<List<String>>): List<ParsedWord> {
        val cleanedRows = rawRows
            .map { row -> row.map { it.replace("\uFEFF", "").trim() } }
            .filter { row -> row.any { it.isNotEmpty() } && !row.firstOrNull().orEmpty().startsWith("//") }

        if (cleanedRows.isEmpty()) return emptyList()

        // Detect optional section header rows like single-cell "【阴平】" inside a table
        val firstDataCandidate = cleanedRows.firstOrNull { it.count { c -> c.isNotEmpty() } >= 2 } ?: cleanedRows.first()
        val hasHeader = isHeaderRow(firstDataCandidate)
        val headerRowIndex = if (hasHeader) cleanedRows.indexOf(firstDataCandidate) else -1

        var wordCol = -1
        var groupCol = -1
        val remarkCols = ArrayList<Int>()
        val indexCols = HashSet<Int>()

        if (hasHeader) {
            firstDataCandidate.forEachIndexed { colIdx, rawCell ->
                val norm = normalizeHeaderToken(rawCell)
                if (norm.isEmpty()) return@forEachIndexed
                when {
                    norm in INDEX_HEADERS || norm.endsWith("序号") || norm.endsWith("编号") -> {
                        indexCols.add(colIdx)
                    }
                    wordCol == -1 && (norm in WORD_HEADERS || WORD_HEADERS.any { it.length >= 2 && norm.contains(it) }) -> {
                        wordCol = colIdx
                    }
                    groupCol == -1 && (norm in GROUP_HEADERS || GROUP_HEADERS.any { it.length >= 2 && norm.contains(it) }) -> {
                        groupCol = colIdx
                    }
                    norm in REMARK_HEADERS || REMARK_HEADERS.any { it.length >= 2 && norm.contains(it) } -> {
                        remarkCols.add(colIdx)
                    }
                }
            }
        }

        val dataRows = cleanedRows.filterIndexed { idx, _ -> idx != headerRowIndex }
        if (dataRows.isEmpty()) return emptyList()

        val maxCols = dataRows.maxOf { it.size }
        if (wordCol == -1) {
            // Heuristic column inference when spreadsheet has no header row
            val multiColRows = dataRows.filter { r -> r.count { it.isNotEmpty() } >= 2 }
            if (multiColRows.isNotEmpty() && maxCols >= 2) {
                for (c in 0 until maxCols) {
                    val colVals = multiColRows.mapNotNull { it.getOrNull(c)?.takeIf { s -> s.isNotEmpty() } }
                    if (colVals.isNotEmpty() && colVals.count { PURE_NUMBER_INDEX_REGEX.matches(it) } * 5 >= colVals.size * 4) {
                        indexCols.add(c)
                    }
                }
                val candidateCols = (0 until maxCols).filter { it !in indexCols }
                if (candidateCols.size == 1) {
                    wordCol = candidateCols[0]
                } else if (candidateCols.size >= 2) {
                    // Check if the first non-index column is a repeated category/group column (e.g. "阴平" repeated across many rows)
                    val c0 = candidateCols[0]
                    val c1 = candidateCols[1]
                    val vals0 = multiColRows.mapNotNull { it.getOrNull(c0)?.takeIf { s -> s.isNotEmpty() } }
                    val vals1 = multiColRows.mapNotNull { it.getOrNull(c1)?.takeIf { s -> s.isNotEmpty() } }
                    val distinctRatio0 = if (vals0.size >= 4) vals0.distinct().size.toFloat() / vals0.size else 1f
                    val distinctRatio1 = if (vals1.size >= 4) vals1.distinct().size.toFloat() / vals1.size else 1f

                    if (vals0.size >= 4 && distinctRatio0 <= 0.5f && distinctRatio1 > 0.7f) {
                        groupCol = c0
                        wordCol = c1
                        candidateCols.drop(2).forEach { remarkCols.add(it) }
                    } else {
                        wordCol = c0
                        if (candidateCols.size == 2) {
                            remarkCols.add(c1)
                        } else {
                            // 3+ non-index columns: check whether last column is group or second column is remark
                            val cLast = candidateCols.last()
                            val valsLast = multiColRows.mapNotNull { it.getOrNull(cLast)?.takeIf { s -> s.isNotEmpty() } }
                            val distinctRatioLast = if (valsLast.size >= 4) valsLast.distinct().size.toFloat() / valsLast.size else 1f
                            if (groupCol == -1 && valsLast.size >= 4 && distinctRatioLast <= 0.55f) {
                                groupCol = cLast
                                candidateCols.drop(1).dropLast(1).forEach { remarkCols.add(it) }
                            } else {
                                candidateCols.drop(1).forEach { remarkCols.add(it) }
                            }
                        }
                    }
                }
            }
            if (wordCol == -1) wordCol = (0 until maxCols).firstOrNull { it !in indexCols } ?: 0
        } else {
            // Header matched wordCol; assign remaining unclaimed non-index columns as remarks
            for (c in 0 until maxCols) {
                if (c != wordCol && c != groupCol && c !in indexCols && c !in remarkCols) {
                    val hasContent = dataRows.any { !it.getOrNull(c).isNullOrBlank() }
                    if (hasContent) remarkCols.add(c)
                }
            }
        }

        val result = ArrayList<ParsedWord>(dataRows.size)
        var stickySectionGroup = ""

        for (row in dataRows) {
            val nonEmptyCells = row.filter { it.isNotEmpty() }
            if (nonEmptyCells.isEmpty()) continue

            // Single-cell section banner row inside spreadsheet, e.g. "【阴平】" or "# 高元音"
            if (nonEmptyCells.size == 1) {
                val single = nonEmptyCells[0]
                if ((single.startsWith("【") && single.endsWith("】")) ||
                    (single.startsWith("[") && single.endsWith("]")) ||
                    single.startsWith("#") ||
                    (single.startsWith("==") && single.endsWith("=="))
                ) {
                    stickySectionGroup = single.trim('[', ']', '【', '】', '#', '=').trim()
                    continue
                }
            }

            val rawWordCell = row.getOrNull(wordCol)?.trim().orEmpty()
            if (rawWordCell.isEmpty()) continue

            // Also support inline "巴 (bā)" if someone put parentheses inside the word cell
            val parenMatch = INLINE_PAREN_REGEX.find(rawWordCell)
            val cleanWord = if (parenMatch != null) parenMatch.groupValues[1].trim() else rawWordCell
            val inlineRemark = if (parenMatch != null) parenMatch.groupValues[2].trim() else ""
            if (cleanWord.isEmpty()) continue

            val rowGroup = if (groupCol >= 0) row.getOrNull(groupCol)?.trim().orEmpty() else ""
            if (rowGroup.isNotEmpty()) {
                stickySectionGroup = rowGroup
            }
            val effectiveGroup = rowGroup.ifEmpty { stickySectionGroup }

            val extraRemarks = remarkCols.mapNotNull { col ->
                row.getOrNull(col)?.trim()?.takeIf { it.isNotEmpty() }
            }
            val combinedRemark = buildList {
                if (inlineRemark.isNotEmpty()) add(inlineRemark)
                addAll(extraRemarks)
            }.distinct().joinToString(" · ")

            result.add(
                ParsedWord(
                    text = cleanWord,
                    remark = combinedRemark,
                    groupName = effectiveGroup
                )
            )
        }

        return result
    }

    private val INLINE_PAREN_REGEX = Regex("^([^\\s(（]+)\\s*[((（]([^)）]+)[)）]$")
}
