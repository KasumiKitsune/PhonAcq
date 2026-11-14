package com.example.utils

import android.content.Context
import com.example.audio.AcousticAnalyzer
import com.example.audio.CorpusPhonologyAnalyzer
import com.example.audio.VowelGlideType
import com.example.data.Project
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Native Praat / Praat_ZH `.Collection` project file exporter and round-trip parser.
 *
 * Implements the official Praat v6.4.x `Daata` / `Collection` serialization specification:
 * - Binary mode (`ooBinaryFile`, Big-Endian, zero padding, `w8` class + `w16` UTF-32 codepoint-counted name)
 * - Full Text mode (`ooTextFile`, UTF-8 without BOM, `""` quote escaping, `--undefined--` non-finite numbers)
 * - Child object schemas:
 *   1. `"Sound 2"` (72-byte `Matrix 2` header + channel-planar `r64` normalized samples streamed from WAV)
 *   2. `"TextGrid"` (`version = 0`, 4 `IntervalTier`s: `groups`, `words`, `chars`, `segments`)
 *   3. `"Table"` (`version = 0`, corpus acoustic & phonological summary table)
 */
object PraatCollectionExporter {

    private const val BINARY_MAGIC = "ooBinaryFile"
    private const val COLLECTION_CLASS = "Collection"

    // Exact character set replaced with '_' by Praat's `praat_newWithFile` upon unpacking a Collection
    private val PRAAT_FORBIDDEN_NAME_CHARS: Set<Char> = setOf(
        ' ', ',', '.', ':', ';', '\\', '/', '(', ')', '[', ']', '{', '}',
        '~', '`', '\'', '<', '>', '*', '&', '^', '%', '#', '@', '!', '?', '$', '"', '|'
    )
    private val MULTI_UNDERSCORE_REGEX = Regex("_+")

    // =========================================================================
    // Domain Models (Used for TextGrid/Table serialization & Round-Trip Tests)
    // =========================================================================

    sealed interface PraatCollectionItem {
        val objectName: String
    }

    data class PraatSoundObject(
        override val objectName: String,
        val xmin: Double,
        val xmax: Double,
        val dx: Double,
        val x1: Double,
        val channels: List<DoubleArray>
    ) : PraatCollectionItem {
        val ny: Int get() = channels.size
        val nx: Int get() = channels.firstOrNull()?.size ?: 0
    }

    data class PraatTextInterval(
        val xmin: Double,
        val xmax: Double,
        val text: String
    )

    sealed interface PraatTier {
        val name: String
        val xmin: Double
        val xmax: Double
    }

    data class PraatIntervalTier(
        override val name: String,
        override val xmin: Double,
        override val xmax: Double,
        val intervals: List<PraatTextInterval>
    ) : PraatTier

    data class PraatTextPoint(
        val number: Double,
        val mark: String
    )

    data class PraatTextTier(
        override val name: String,
        override val xmin: Double,
        override val xmax: Double,
        val points: List<PraatTextPoint>
    ) : PraatTier

    data class PraatTextGridObject(
        override val objectName: String,
        val xmin: Double,
        val xmax: Double,
        val tiers: List<PraatTier>
    ) : PraatCollectionItem

    data class PraatTableObject(
        override val objectName: String,
        val columnHeaders: List<String>,
        val rows: List<List<String>>
    ) : PraatCollectionItem

    data class WavPcmDataSpec(
        val audioFormat: Int, // 1 = PCM integer, 3 = IEEE 754 float
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataByteLength: Long,
        val frameCount: Int
    ) {
        val dx: Double get() = 1.0 / sampleRate.toDouble()
        val xmax: Double get() = frameCount.toDouble() * dx
        val x1: Double get() = 0.5 * dx
    }

    private data class PlannedCollectionWord(
        val objectName: String,
        val wavFile: File?,
        val wavSpec: WavPcmDataSpec?,
        val textGrid: PraatTextGridObject?
    )

    // =========================================================================
    // Name Sanitization & Formatting Utilities
    // =========================================================================

    /**
     * Sanitizes an object name for `.Collection` storage so Praat's `praat_newWithFile`
     * never truncates after `.` and never alters the name when unpacking, ensuring paired
     * `Sound` and `TextGrid` objects share the exact same name in Praat's Objects list.
     */
    fun sanitizePraatObjectName(raw: String, fallback: String = "untitled"): String {
        if (raw.isBlank()) return fallback
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch in PRAAT_FORBIDDEN_NAME_CHARS || ch.isWhitespace() || ch.code < 0x20) {
                sb.append('_')
            } else {
                sb.append(ch)
            }
        }
        val collapsed = sb.toString()
            .replace(MULTI_UNDERSCORE_REGEX, "_")
            .trim('_')
        return collapsed.ifEmpty { sanitizePraatObjectName(fallback, "untitled") }
    }

    /**
     * Formats an IEEE 754 `Double` for Praat `ooTextFile` without precision loss:
     * - Non-finite values (`NaN`, `±Infinity`) become unquoted `--undefined--`
     * - Finite values always begin with an optional `-` and a decimal digit (`0.5`, never `.5`)
     */
    fun formatPraatDouble(v: Double): String {
        if (!v.isFinite()) return "--undefined--"
        if (v == 0.0) return "0"
        val raw = v.toString().lowercase(Locale.US)
        return if (raw.endsWith(".0") && !raw.contains('e')) {
            raw.substring(0, raw.length - 2)
        } else {
            raw
        }
    }

    /**
     * Escapes a string literal for Praat `ooTextFile` by doubling internal `"` as `""`
     * (backslashes have no escape meaning in Praat text files).
     */
    fun escapePraatTextString(s: String): String = s.replace("\"", "\"\"")

    // =========================================================================
    // Binary Primitives (Big-Endian + Praat w8 / w16 UTF-32 Codepoint Count)
    // =========================================================================

    class PraatBinaryWriter(private val out: OutputStream) {
        private val scratch = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)

        fun writeRawAscii(ascii: String) {
            out.write(ascii.toByteArray(Charsets.US_ASCII))
        }

        fun u8(v: Int) {
            out.write(v and 0xFF)
        }

        fun bool8(v: Boolean) {
            out.write(if (v) 1 else 0)
        }

        fun i16(v: Int) {
            scratch.clear()
            scratch.putShort(v.toShort())
            out.write(scratch.array(), 0, 2)
        }

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
            scratch.putDouble(v)
            out.write(scratch.array(), 0, 8)
        }

        fun w8(s: String) {
            val (sub, cpCount) = truncateByCodePoints(s, maxCodePoints = 254)
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
            val (sub, cpCount) = truncateByCodePoints(s, maxCodePoints = 65534)
            if (isPure7BitAscii(sub)) {
                u16(cpCount)
                out.write(sub.toByteArray(Charsets.US_ASCII))
            } else {
                u16(0xFFFF)
                u16(cpCount)
                out.write(sub.toByteArray(Charsets.UTF_16BE))
            }
        }

        fun flush() {
            out.flush()
        }

        private fun isPure7BitAscii(s: String): Boolean {
            for (i in 0 until s.length) {
                if (s[i].code > 0x7F) return false
            }
            return true
        }

        private fun truncateByCodePoints(s: String, maxCodePoints: Int): Pair<String, Int> {
            val totalCp = s.codePointCount(0, s.length)
            return if (totalCp > maxCodePoints) {
                val endCharOffset = s.offsetByCodePoints(0, maxCodePoints)
                s.substring(0, endCharOffset) to maxCodePoints
            } else {
                s to totalCp
            }
        }
    }

    class PraatBinaryReader(private val input: InputStream) {
        private val scratch = ByteArray(8)
        private val scratchBuf = ByteBuffer.wrap(scratch).order(ByteOrder.BIG_ENDIAN)

        fun readExact(n: Int): ByteArray {
            val out = ByteArray(n)
            var offset = 0
            while (offset < n) {
                val r = input.read(out, offset, n - offset)
                if (r < 0) throw EOFException("Early end of Praat binary stream ($offset / $n bytes).")
                offset += r
            }
            return out
        }

        private fun fillScratch(n: Int) {
            var offset = 0
            while (offset < n) {
                val r = input.read(scratch, offset, n - offset)
                if (r < 0) throw EOFException("Early end of Praat binary stream.")
                offset += r
            }
        }

        fun u8(): Int {
            val b = input.read()
            if (b < 0) throw EOFException("Early end of Praat binary stream.")
            return b and 0xFF
        }

        fun bool8(): Boolean = u8() != 0

        fun i16(): Int {
            fillScratch(2)
            return scratchBuf.getShort(0).toInt()
        }

        fun u16(): Int {
            fillScratch(2)
            return scratchBuf.getShort(0).toInt() and 0xFFFF
        }

        fun i32(): Int {
            fillScratch(4)
            return scratchBuf.getInt(0)
        }

        fun r32(): Double {
            fillScratch(4)
            val v = scratchBuf.getFloat(0)
            return if (v.isNaN() || v.isInfinite()) Double.NaN else v.toDouble()
        }

        fun r64(): Double {
            fillScratch(8)
            val v = scratchBuf.getDouble(0)
            return if (v.isNaN() || v.isInfinite()) Double.NaN else v
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
                val lead = u16()
                if (lead in 0xD800..0xDBFF) {
                    val trail = u16()
                    if (trail in 0xDC00..0xDFFF) {
                        val cp = 0x10000 + ((lead - 0xD800) shl 10) + (trail - 0xDC00)
                        sb.appendCodePoint(cp)
                    } else {
                        sb.append('\uFFFD')
                    }
                } else if (lead in 0xDC00..0xDFFF) {
                    sb.append('\uFFFD')
                } else {
                    sb.append(lead.toChar())
                }
            }
            return sb.toString()
        }
    }

    // =========================================================================
    // Praat Type-Driven Text Scanner (`melder/abcio.cpp` Reference Replica)
    // =========================================================================

    class PraatTextScanner(rawText: String) {
        private val s: String
        private var pos: Int = 0
        private val n: Int

        init {
            val strippedBom = if (rawText.startsWith("\uFEFF")) rawText.substring(1) else rawText
            val normalized = strippedBom.replace("\r\n", "\n").replace('\r', '\n')
            val nl = normalized.indexOf('\n')
            if (nl < 0 || !normalized.substring(0, nl).contains("ooTextFile")) {
                throw IllegalArgumentException("Not a valid Praat ooTextFile.")
            }
            s = normalized
            pos = nl + 1
            n = normalized.length
        }

        private fun isPraatSpace(ch: Char): Boolean {
            return ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' ||
                ch == '\u000B' || ch == '\u000C' || ch.isWhitespace()
        }

        private fun skipToTokenStart(): Char {
            while (pos < n) {
                val c = s[pos]
                when {
                    isPraatSpace(c) -> pos++
                    c == '!' -> {
                        val nl = s.indexOf('\n', pos)
                        pos = if (nl < 0) n else nl + 1
                    }
                    else -> return c
                }
            }
            throw EOFException("Early end of text in Praat ooTextFile.")
        }

        private fun consumeWord(): String {
            val start = pos
            while (pos < n && !isPraatSpace(s[pos])) {
                pos++
            }
            val word = s.substring(start, pos)
            if (pos < n) pos++ // consume single trailing whitespace
            return word
        }

        fun getString(): String {
            while (true) {
                val c = skipToTokenStart()
                if (c == '"') break
                if (c == '+' || c == '-' || c in '0'..'9' || c == '<') {
                    throw IllegalArgumentException("Unexpected token '$c' while looking for string.")
                }
                consumeWord()
            }
            pos++ // skip opening quote
            val sb = StringBuilder()
            while (pos < n) {
                val ch = s[pos++]
                if (ch == '"') {
                    if (pos < n && s[pos] == '"') {
                        sb.append('"')
                        pos++
                    } else {
                        if (pos < n && !isPraatSpace(s[pos])) {
                            throw IllegalArgumentException("Non-whitespace character right after closing quote.")
                        }
                        if (pos < n) pos++
                        return sb.toString()
                    }
                } else {
                    sb.append(ch)
                }
            }
            throw EOFException("Unclosed string literal in Praat ooTextFile.")
        }

        fun getInt(): Int {
            while (true) {
                val c = skipToTokenStart()
                if (c == '+' || c == '-' || c in '0'..'9') {
                    return consumeWord().toInt(10)
                }
                if (c == '"' || c == '<') {
                    throw IllegalArgumentException("Unexpected token '$c' while looking for integer.")
                }
                consumeWord()
            }
        }

        fun getDouble(): Double {
            while (true) {
                val c = skipToTokenStart()
                if (c == '+' || c == '-' || c in '0'..'9') {
                    val word = consumeWord()
                    if (word == "+") continue
                    if (word == "--undefined--") return Double.NaN
                    if (word.contains('/')) {
                        val parts = word.split('/', limit = 2)
                        val den = parts[1].toDoubleOrNull() ?: return Double.NaN
                        val num = parts[0].toDoubleOrNull() ?: return Double.NaN
                        return if (den == 0.0) Double.NaN else num / den
                    }
                    val parsed = if (word.endsWith('%')) {
                        word.dropLast(1).toDoubleOrNull()?.times(0.01)
                    } else {
                        word.toDoubleOrNull()
                    }
                    return if (parsed == null || parsed.isInfinite()) Double.NaN else parsed
                }
                if (c == '"' || c == '<') {
                    throw IllegalArgumentException("Unexpected token '$c' while looking for real.")
                }
                consumeWord()
            }
        }

        fun getEnum(): String {
            while (true) {
                val c = skipToTokenStart()
                if (c == '<') {
                    val end = s.indexOf('>', pos + 1)
                    if (end < 0) throw EOFException("Unclosed <enum> token in Praat ooTextFile.")
                    val value = s.substring(pos + 1, end)
                    pos = end + 1
                    if (pos < n && !isPraatSpace(s[pos])) {
                        throw IllegalArgumentException("Non-whitespace character right after '>'.")
                    }
                    if (pos < n) pos++
                    return value
                }
                if (c == '+' || c == '-' || c in '0'..'9' || c == '"') {
                    throw IllegalArgumentException("Unexpected token '$c' while looking for <enum>.")
                }
                consumeWord()
            }
        }
    }

    // =========================================================================
    // Project & Single-Word `.Collection` Export Entry Points
    // =========================================================================

    /**
     * Exports a full or single-speaker PhonAcq project directly to a native Praat `.Collection`
     * stream (either `ooBinaryFile` or `ooTextFile`).
     */
    fun exportProjectAsPraatCollection(
        context: Context,
        project: Project,
        speakerWordPairs: List<Pair<Speaker?, List<Word>>>,
        options: ExportOptions,
        targetStream: OutputStream
    ): ExportSummary {
        val projectDir = File(context.getExternalFilesDir(null), "project_${project.id}")
        val spPrefs = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)

        val includeAudio = options.includeAudio
        val includeTextGrid = options.includePraatTextGrid
        val includeTable = options.includePraatSummaryTable

        val usedObjectNames = HashSet<String>()
        fun allocateUniquePraatName(rawBase: String): String {
            val base = sanitizePraatObjectName(rawBase, "item")
            var candidate = base
            var suffix = 2
            while (!usedObjectNames.add(candidate)) {
                candidate = "${base}_$suffix"
                suffix++
            }
            return candidate
        }

        val plannedWords = mutableListOf<PlannedCollectionWord>()
        val tableColumns = listOf(
            "Index",
            "SpeakerCode",
            "SpeakerName",
            "Gender",
            "Age",
            "Dialect",
            "WordGroup",
            "Word",
            "Remark",
            "SpeakerNote",
            "ObjectName",
            "Duration_ms",
            "Voiced_ms",
            "F0_Mean_Hz",
            "Chao_Tone_T",
            "Canonical_Tone_T",
            "Tone_Category",
            "Checked_Tone",
            "F1_Hz",
            "F2_Hz",
            "F3_Hz",
            "Vowel_Type",
            "F1_Onset_Hz",
            "F2_Onset_Hz",
            "F1_Target_Hz",
            "F2_Target_Hz",
            "Bark_Glide_Span"
        )
        val tableRows = mutableListOf<List<String>>()
        var wavCount = 0
        var textGridCount = 0

        try {
            val shouldAnalyze = includeTextGrid || includeTable

            for ((speaker, words) in speakerWordPairs) {
                val speakerId = speaker?.id ?: 1L
                val speakerCode = String.format(Locale.US, "SPK%02d", speakerId)
                val pMin = spPrefs.getFloat("pitch_min_$speakerId", -1f)
                val pMax = spPrefs.getFloat("pitch_max_$speakerId", -1f)
                val manualPitchRange = if (pMin > 40f && pMax > pMin + 15f) Pair(pMin, pMax) else null
                val speakerGender = speaker?.gender ?: ""
                val sortedWords = words.sortedBy { it.originalIndex }

                val corpusStats = if (shouldAnalyze) {
                    AcousticAnalyzer.resolveSpeakerCorpusStats(
                        projDir = projectDir,
                        speakerId = speakerId,
                        speakerGender = speakerGender,
                        manualRange = manualPitchRange
                    )
                } else null
                val effectivePitchRange = corpusStats?.pitchRange ?: manualPitchRange
                val medianVoicedMs = corpusStats?.medianVoicedDurationMs ?: 280
                val phonologyReport = if (includeTable) {
                    CorpusPhonologyAnalyzer.buildSpeakerPhonologyReport(
                        context = context,
                        projectId = project.id,
                        speaker = speaker,
                        words = sortedWords
                    )
                } else null

                for (word in sortedWords) {
                    val recFileName = word.recordingFile
                    if (!word.isRecorded || recFileName.isNullOrEmpty()) continue
                    val wavFile = File(projectDir, recFileName)
                    if (!wavFile.exists() || wavFile.length() <= 44L) continue

                    val wavSpec = scanWavPcmSpec(wavFile) ?: continue
                    val exactDurationSec = wavSpec.xmax

                    val idxStr = String.format(Locale.US, "%03d", word.originalIndex + 1)
                    val safeWordText = sanitizePraatObjectName(word.text, "word_${word.id}").take(24)
                    val safeSpkName = sanitizePraatObjectName(speaker?.name ?: speakerCode, speakerCode).take(16)
                    val safeRemark = ZipUtility.transliterateNoteForFileName(
                        word.speakerNote.ifBlank { word.remark }
                    ).take(16)

                    val effectiveNamingStyle = if (speakerWordPairs.size > 1 && options.namingStyle == "INDEX_WORD") {
                        "CODE_INDEX_WORD"
                    } else {
                        options.namingStyle
                    }
                    val prefix = when (effectiveNamingStyle) {
                        "NAME_INDEX_WORD" -> "${safeSpkName}_${idxStr}_${safeWordText}"
                        "INDEX_WORD" -> "${idxStr}_${safeWordText}"
                        else -> "${speakerCode}_${idxStr}_${safeWordText}"
                    }
                    val rawObjName = if (safeRemark.isNotEmpty() && effectiveNamingStyle != "INDEX_WORD") {
                        "${prefix}_${safeRemark}"
                    } else {
                        prefix
                    }
                    val objectName = allocateUniquePraatName(rawObjName)

                    val parts = ZipUtility.extractSyllableLabels(word.text)
                    val analysis = if (shouldAnalyze) {
                        AcousticAnalyzer.analyzeByGender(
                            file = wavFile,
                            speakerGender = speakerGender,
                            segmentCount = parts.size.coerceAtLeast(1),
                            pitchRange = effectivePitchRange,
                            segmentLabels = parts,
                            speakerMedianVoicedMs = medianVoicedMs
                        )
                    } else null

                    val tgObj = if (includeTextGrid) {
                        buildPraatTextGridObject(
                            objectName = objectName,
                            word = word,
                            analysis = analysis,
                            exactDomainXmax = exactDurationSec,
                            includeAcousticTags = options.includeAcousticTagsInTextGrid,
                            textGridConfig = options.textGridConfig
                        )
                    } else null

                    if (includeAudio || tgObj != null) {
                        plannedWords.add(
                            PlannedCollectionWord(
                                objectName = objectName,
                                wavFile = if (includeAudio) wavFile else null,
                                wavSpec = if (includeAudio) wavSpec else null,
                                textGrid = tgObj
                            )
                        )
                        if (includeAudio) wavCount++
                        if (tgObj != null) textGridCount++
                    }

                    if (includeTable) {
                        val totalDurMs = analysis?.totalDurationMs ?: (exactDurationSec * 1000.0).toLong().coerceAtLeast(1L)
                        val voicedDurMs = analysis?.voicedDurationMs ?: 0L
                        val pitchMean = analysis?.pitchMean ?: 0f
                        val chaoTone = analysis?.chaoToneNumbers ?: "--"
                        val toneCode = analysis?.toneCode ?: S.praat.undetectedF0
                        val isChecked = analysis?.isCheckedTone ?: false
                        val canonicalMember = phonologyReport?.wordIdToCanonicalMember?.get(word.id)
                        val canonicalChao = canonicalMember?.canonicalChao ?: chaoTone
                        val canonicalCat = canonicalMember?.canonicalCategoryName ?: toneCode
                        val f1Str = (analysis?.speakerF1 ?: emptyList()).joinToString(";")
                        val f2Str = (analysis?.speakerF2 ?: emptyList()).joinToString(";")
                        val f3Str = (analysis?.speakerF3 ?: emptyList()).joinToString(";")
                        val trajs = analysis?.formantTrajectories ?: emptyList()

                        tableRows.add(
                            listOf(
                                (word.originalIndex + 1).toString(),
                                speakerCode,
                                speaker?.name ?: S.export.defaultSpeakerFallbackName,
                                speakerGender,
                                (speaker?.age ?: 25).toString(),
                                speaker?.dialect ?: "",
                                word.groupName.ifBlank { S.common.defaultGroupFallback },
                                word.text,
                                word.remark,
                                word.speakerNote,
                                objectName,
                                totalDurMs.toString(),
                                voicedDurMs.toString(),
                                if (pitchMean.isFinite() && pitchMean > 0f) String.format(Locale.US, "%.2f", pitchMean) else "--undefined--",
                                chaoTone,
                                canonicalChao,
                                canonicalCat,
                                if (isChecked) "YES" else "NO",
                                f1Str.ifEmpty { "--undefined--" },
                                f2Str.ifEmpty { "--undefined--" },
                                f3Str.ifEmpty { "--undefined--" },
                                trajs.joinToString(";") { it.vowelType.name },
                                trajs.joinToString(";") { it.onsetF1.toString() }.ifEmpty { "--undefined--" },
                                trajs.joinToString(";") { it.onsetF2.toString() }.ifEmpty { "--undefined--" },
                                trajs.joinToString(";") { it.targetF1.toString() }.ifEmpty { "--undefined--" },
                                trajs.joinToString(";") { it.targetF2.toString() }.ifEmpty { "--undefined--" },
                                trajs.joinToString(";") {
                                    if (it.barkGlideSpan.isFinite()) String.format(Locale.US, "%.2f", it.barkGlideSpan) else "0.00"
                                }.ifEmpty { "0.00" }
                            )
                        )
                    }
                }
            }

            val summaryTableObject = if (includeTable && tableRows.isNotEmpty()) {
                val safeProjTable = allocateUniquePraatName("${project.name}${S.praat.summaryTableSuffix}")
                PraatTableObject(
                    objectName = safeProjTable,
                    columnHeaders = tableColumns,
                    rows = tableRows
                )
            } else null

            val totalObjectCount = plannedWords.sumOf {
                (if (it.wavFile != null && it.wavSpec != null) 1 else 0) + (if (it.textGrid != null) 1 else 0)
            } + (if (summaryTableObject != null) 1 else 0)

            if (options.praatCollectionBinary) {
                writeProjectBinaryCollection(
                    targetStream = targetStream,
                    totalObjectCount = totalObjectCount,
                    plannedWords = plannedWords,
                    summaryTable = summaryTableObject
                )
            } else {
                writeProjectTextCollection(
                    targetStream = targetStream,
                    totalObjectCount = totalObjectCount,
                    plannedWords = plannedWords,
                    summaryTable = summaryTableObject
                )
            }

            val exportedSpeakerCount = speakerWordPairs.count { pair ->
                pair.second.any { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
            }
            val fmtLabel = if (options.praatCollectionBinary) S.praat.formatBinaryLabel else S.praat.formatTextLabel
            val msg = if (totalObjectCount == 0) {
                S.praat.emptyCollectionMessage
            } else {
                buildString {
                    append(S.praat.successPrefix.withArgs("format" to fmtLabel, "total" to totalObjectCount))
                    val partsMsg = mutableListOf<String>()
                    if (wavCount > 0) partsMsg.add(S.praat.soundPartCount.withArgs("count" to wavCount))
                    if (textGridCount > 0) {
                        partsMsg.add(
                            S.praat.textGridPartCount.withArgs(
                                "count" to textGridCount,
                                "summary" to options.textGridConfig.shortSummaryLabel()
                            )
                        )
                    }
                    if (summaryTableObject != null) partsMsg.add(S.praat.tablePartCount)
                    append(partsMsg.joinToString("、"))
                    append(S.praat.successSuffix)
                }
            }

            return ExportSummary(
                success = true,
                wavCount = wavCount,
                textGridCount = textGridCount,
                csvRowCount = tableRows.size,
                speakerCount = exportedSpeakerCount,
                isPraatCollection = true,
                objectCount = totalObjectCount,
                message = msg
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return ExportSummary(
                success = false,
                isPraatCollection = true,
                message = S.praat.exportFailedMessage.withArgs("msg" to (e.localizedMessage ?: S.praat.writeErrorFallback))
            )
        }
    }

    /**
     * Exports a single recorded word (`Sound 2` + 4-tier `TextGrid` sharing an identical object name)
     * as a binary or text `.Collection` file so the user can open both together in Praat in one step.
     */
    fun exportSingleWordPraatCollection(
        wavFile: File,
        word: Word,
        speaker: Speaker?,
        analysis: AcousticAnalyzer.AnalysisResult?,
        includeAcousticTags: Boolean = false,
        binaryFormat: Boolean = true,
        textGridConfig: TextGridFormatConfig = TextGridFormatConfig(),
        targetStream: OutputStream
    ): Boolean {
        val wavSpec = scanWavPcmSpec(wavFile) ?: return false
        val spkCode = String.format(Locale.US, "SPK%02d", speaker?.id ?: 1L)
        val idxStr = String.format(Locale.US, "%03d", word.originalIndex + 1)
        val safeWord = sanitizePraatObjectName(word.text, "word_${word.id}").take(24)
        val objName = sanitizePraatObjectName("${spkCode}_${idxStr}_${safeWord}", "word")

        val tg = buildPraatTextGridObject(
            objectName = objName,
            word = word,
            analysis = analysis,
            exactDomainXmax = wavSpec.xmax,
            includeAcousticTags = includeAcousticTags,
            textGridConfig = textGridConfig
        )
        val planned = listOf(
            PlannedCollectionWord(
                objectName = objName,
                wavFile = wavFile,
                wavSpec = wavSpec,
                textGrid = tg
            )
        )
        if (binaryFormat) {
            writeProjectBinaryCollection(
                targetStream = targetStream,
                totalObjectCount = 2,
                plannedWords = planned,
                summaryTable = null
            )
        } else {
            writeProjectTextCollection(
                targetStream = targetStream,
                totalObjectCount = 2,
                plannedWords = planned,
                summaryTable = null
            )
        }
        return true
    }

    // =========================================================================
    // TextGrid Object Construction (Exact Domain Alignment with Paired Sound 2)
    // =========================================================================

    fun buildPraatTextGridObject(
        objectName: String,
        word: Word,
        analysis: AcousticAnalyzer.AnalysisResult?,
        exactDomainXmax: Double,
        includeAcousticTags: Boolean = false,
        textGridConfig: TextGridFormatConfig = TextGridFormatConfig()
    ): PraatTextGridObject {
        val safeXmax = if (exactDomainXmax.isFinite() && exactDomainXmax > 0.002) exactDomainXmax else 0.5
        val segInfo = ZipUtility.resolveWordSegmentation(word, analysis, safeXmax)
        val speechStart = segInfo.startSec.coerceIn(0.0, safeXmax).let {
            if (it < 0.015 || safeXmax - it < 0.002) 0.0 else it
        }
        val speechEnd = segInfo.endSec.coerceIn(speechStart, safeXmax).let {
            if (safeXmax - it < 0.015 || it <= speechStart + 0.001) safeXmax else it
        }

        val configuredTiers = TextGridFormatConfig.buildConfiguredTiers(
            config = textGridConfig,
            word = word,
            analysis = analysis,
            domainXmax = safeXmax,
            speechStartRaw = speechStart,
            speechEndRaw = speechEnd,
            rawCharIntervals = segInfo.charIntervals,
            includeAcousticTags = includeAcousticTags,
            quantizeDecimals = null
        )

        val praatTiers = configuredTiers.map { built ->
            val rawIntervals = built.intervals.map { (s, e, lbl) ->
                PraatTextInterval(s, e, lbl)
            }
            PraatIntervalTier(
                name = built.name,
                xmin = 0.0,
                xmax = safeXmax,
                intervals = enforceIntervalContinuity(rawIntervals, safeXmax)
            )
        }

        return PraatTextGridObject(
            objectName = sanitizePraatObjectName(objectName),
            xmin = 0.0,
            xmax = safeXmax,
            tiers = praatTiers
        )
    }

    private fun buildSingleSpanTierIntervals(
        xmax: Double,
        startSec: Double,
        endSec: Double,
        label: String
    ): List<PraatTextInterval> {
        val list = mutableListOf<PraatTextInterval>()
        if (startSec > 0.0) {
            list.add(PraatTextInterval(0.0, startSec, ""))
        }
        val safeEnd = if (endSec <= startSec) xmax else endSec
        list.add(PraatTextInterval(startSec, safeEnd, label))
        if (safeEnd < xmax) {
            list.add(PraatTextInterval(safeEnd, xmax, ""))
        }
        return enforceIntervalContinuity(list, xmax)
    }

    private fun enforceIntervalContinuity(
        raw: List<PraatTextInterval>,
        xmax: Double
    ): List<PraatTextInterval> {
        if (raw.isEmpty()) return listOf(PraatTextInterval(0.0, xmax, ""))
        val out = mutableListOf<PraatTextInterval>()
        var cursor = 0.0
        for (i in raw.indices) {
            val end = if (i == raw.lastIndex) xmax else raw[i].xmax.coerceIn(cursor, xmax)
            if (end - cursor >= 1e-6) {
                out.add(PraatTextInterval(cursor, end, raw[i].text))
                cursor = end
            }
        }
        if (out.isEmpty()) return listOf(PraatTextInterval(0.0, xmax, raw.first().text))
        val last = out.last()
        if (last.xmax != xmax) {
            out[out.lastIndex] = last.copy(xmax = xmax)
        }
        return out
    }

    // =========================================================================
    // Streaming Binary & Full-Text Writers + In-Memory Round-Trip Helpers
    // =========================================================================

    private fun writeProjectBinaryCollection(
        targetStream: OutputStream,
        totalObjectCount: Int,
        plannedWords: List<PlannedCollectionWord>,
        summaryTable: PraatTableObject?
    ) {
        val buffered = BufferedOutputStream(targetStream, 32768)
        val w = PraatBinaryWriter(buffered)

        // 1. 23-byte file header: "ooBinaryFile" + w8("Collection")
        w.writeRawAscii(BINARY_MAGIC)
        w.w8(COLLECTION_CLASS)

        // 2. Collection size (i32BE)
        w.i32(totalObjectCount)

        // 3. Stream items in order
        for (entry in plannedWords) {
            if (entry.wavFile != null && entry.wavSpec != null) {
                writeWavAsBinarySound2(w, entry.objectName, entry.wavFile, entry.wavSpec)
            }
            if (entry.textGrid != null) {
                writeBinaryTextGridItem(w, entry.textGrid)
            }
        }
        if (summaryTable != null) {
            writeBinaryTableItem(w, summaryTable)
        }
        w.flush()
    }

    private fun writeProjectTextCollection(
        targetStream: OutputStream,
        totalObjectCount: Int,
        plannedWords: List<PlannedCollectionWord>,
        summaryTable: PraatTableObject?
    ) {
        // UTF-8 without BOM per Praat Melder_str8IsValidUtf8 specification
        val writer = BufferedWriter(OutputStreamWriter(targetStream, Charsets.UTF_8), 32768)
        writer.write("File type = \"ooTextFile\"\n")
        writer.write("Object class = \"Collection\"\n\n")
        writer.write("size = $totalObjectCount \n")
        if (totalObjectCount == 0) {
            writer.write("item []: (empty)\n")
            writer.flush()
            return
        }
        writer.write("item []: \n")
        var itemIdx = 1
        for (entry in plannedWords) {
            if (entry.wavFile != null && entry.wavSpec != null) {
                writeWavAsTextSound2(writer, itemIdx++, entry.objectName, entry.wavFile, entry.wavSpec)
            }
            if (entry.textGrid != null) {
                writeTextTextGridItem(writer, itemIdx++, entry.textGrid)
            }
        }
        if (summaryTable != null) {
            writeTextTableItem(writer, itemIdx, summaryTable)
        }
        writer.flush()
    }

    fun writeItemsAsBinaryCollection(items: List<PraatCollectionItem>, out: OutputStream) {
        val buffered = BufferedOutputStream(out, 16384)
        val w = PraatBinaryWriter(buffered)
        w.writeRawAscii(BINARY_MAGIC)
        w.w8(COLLECTION_CLASS)
        w.i32(items.size)
        for (item in items) {
            when (item) {
                is PraatSoundObject -> {
                    val safeName = sanitizePraatObjectName(item.objectName)
                    w.w8("Sound 2")
                    w.w16(safeName)
                    w.r64(item.xmin)
                    w.r64(item.xmax)
                    w.i32(item.nx)
                    w.r64(item.dx)
                    w.r64(item.x1)
                    w.r64(1.0)
                    w.r64(item.ny.toDouble())
                    w.i32(item.ny)
                    w.r64(1.0)
                    w.r64(1.0)
                    for (ch in item.channels) {
                        for (sample in ch) {
                            w.r64(sample)
                        }
                    }
                }
                is PraatTextGridObject -> writeBinaryTextGridItem(w, item)
                is PraatTableObject -> writeBinaryTableItem(w, item)
            }
        }
        w.flush()
    }

    private fun writeBinaryTextGridItem(w: PraatBinaryWriter, tg: PraatTextGridObject) {
        val safeName = sanitizePraatObjectName(tg.objectName)
        w.w8("TextGrid")
        w.w16(safeName)
        w.r64(tg.xmin)
        w.r64(tg.xmax)
        w.bool8(true) // tiers? <exists>
        w.i32(tg.tiers.size)
        for (tier in tg.tiers) {
            when (tier) {
                is PraatIntervalTier -> {
                    w.w8("IntervalTier")
                    w.w16(tier.name)
                    w.r64(tier.xmin)
                    w.r64(tier.xmax)
                    w.i32(tier.intervals.size)
                    for (iv in tier.intervals) {
                        w.r64(iv.xmin)
                        w.r64(iv.xmax)
                        w.w16(iv.text)
                    }
                }
                is PraatTextTier -> {
                    w.w8("TextTier")
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
    }

    private fun writeBinaryTableItem(w: PraatBinaryWriter, table: PraatTableObject) {
        val safeName = sanitizePraatObjectName(table.objectName)
        val colCount = table.columnHeaders.size
        w.w8("Table")
        w.w16(safeName)
        w.i32(colCount)
        for (header in table.columnHeaders) {
            w.w16(header)
        }
        w.i32(table.rows.size)
        for (row in table.rows) {
            w.i32(colCount) // Repeated inside every TableRow!
            for (c in 0 until colCount) {
                w.w16(row.getOrElse(c) { "" })
            }
        }
    }

    private fun writeTextTextGridItem(writer: BufferedWriter, itemIndex: Int, tg: PraatTextGridObject) {
        val safeName = escapePraatTextString(sanitizePraatObjectName(tg.objectName))
        writer.write("    item [$itemIndex]:\n")
        writer.write("        class = \"TextGrid\" \n")
        writer.write("        name = \"$safeName\" \n")
        writer.write("        xmin = ${formatPraatDouble(tg.xmin)} \n")
        writer.write("        xmax = ${formatPraatDouble(tg.xmax)} \n")
        writer.write("        tiers? <exists> \n")
        writer.write("        size = ${tg.tiers.size} \n")
        if (tg.tiers.isEmpty()) {
            writer.write("        item []: (empty)\n")
            return
        }
        writer.write("        item []: \n")
        tg.tiers.forEachIndexed { tIdx, tier ->
            val tNum = tIdx + 1
            writer.write("            item [$tNum]:\n")
            when (tier) {
                is PraatIntervalTier -> {
                    writer.write("                class = \"IntervalTier\" \n")
                    writer.write("                name = \"${escapePraatTextString(tier.name)}\" \n")
                    writer.write("                xmin = ${formatPraatDouble(tier.xmin)} \n")
                    writer.write("                xmax = ${formatPraatDouble(tier.xmax)} \n")
                    writer.write("                intervals: size = ${tier.intervals.size} \n")
                    tier.intervals.forEachIndexed { ivIdx, iv ->
                        writer.write("                intervals [${ivIdx + 1}]:\n")
                        writer.write("                    xmin = ${formatPraatDouble(iv.xmin)} \n")
                        writer.write("                    xmax = ${formatPraatDouble(iv.xmax)} \n")
                        writer.write("                    text = \"${escapePraatTextString(iv.text)}\" \n")
                    }
                }
                is PraatTextTier -> {
                    writer.write("                class = \"TextTier\" \n")
                    writer.write("                name = \"${escapePraatTextString(tier.name)}\" \n")
                    writer.write("                xmin = ${formatPraatDouble(tier.xmin)} \n")
                    writer.write("                xmax = ${formatPraatDouble(tier.xmax)} \n")
                    writer.write("                points: size = ${tier.points.size} \n")
                    tier.points.forEachIndexed { ptIdx, pt ->
                        writer.write("                points [${ptIdx + 1}]:\n")
                        writer.write("                    number = ${formatPraatDouble(pt.number)} \n")
                        writer.write("                    mark = \"${escapePraatTextString(pt.mark)}\" \n")
                    }
                }
            }
        }
    }

    private fun writeTextTableItem(writer: BufferedWriter, itemIndex: Int, table: PraatTableObject) {
        val safeName = escapePraatTextString(sanitizePraatObjectName(table.objectName))
        val colCount = table.columnHeaders.size
        writer.write("    item [$itemIndex]:\n")
        writer.write("        class = \"Table\" \n")
        writer.write("        name = \"$safeName\" \n")
        writer.write("        numberOfColumns = $colCount \n")
        if (colCount == 0) {
            writer.write("        columnHeaders []: (empty)\n")
        } else {
            writer.write("        columnHeaders []: \n")
            table.columnHeaders.forEachIndexed { cIdx, header ->
                writer.write("            columnHeaders [${cIdx + 1}]:\n")
                writer.write("                label = \"${escapePraatTextString(header)}\" \n")
            }
        }
        writer.write("        rows: size = ${table.rows.size} \n")
        table.rows.forEachIndexed { rIdx, row ->
            writer.write("        rows [${rIdx + 1}]:\n")
            writer.write("            numberOfColumns = $colCount \n")
            if (colCount == 0) {
                writer.write("            cells []: (empty)\n")
            } else {
                writer.write("            cells []: \n")
                for (cIdx in 0 until colCount) {
                    val cellText = escapePraatTextString(row.getOrElse(cIdx) { "" })
                    writer.write("                cells [${cIdx + 1}]:\n")
                    writer.write("                    string = \"$cellText\" \n")
                }
            }
        }
    }

    // =========================================================================
    // RIFF WAV Chunk Scanner & Channel-Planar Streaming to `Sound 2`
    // =========================================================================

    fun scanWavPcmSpec(file: File): WavPcmDataSpec? {
        val fileLen = file.length()
        if (!file.exists() || fileLen <= 44L) return null
        return try {
            FileInputStream(file).use { fis ->
                val riffHeader = ByteArray(12)
                if (readFullyUpTo(fis, riffHeader, 12) < 12) return null
                val isRiffWave = riffHeader[0] == 'R'.code.toByte() &&
                    riffHeader[1] == 'I'.code.toByte() &&
                    riffHeader[2] == 'F'.code.toByte() &&
                    riffHeader[3] == 'F'.code.toByte() &&
                    riffHeader[8] == 'W'.code.toByte() &&
                    riffHeader[9] == 'A'.code.toByte() &&
                    riffHeader[10] == 'V'.code.toByte() &&
                    riffHeader[11] == 'E'.code.toByte()
                if (!isRiffWave) return null

                var pos = 12L
                var audioFormat = 1
                var channels = 1
                var sampleRate = 44100
                var bitsPerSample = 16
                var foundFmt = false
                val chunkHead = ByteArray(8)
                val chunkHeadBuf = ByteBuffer.wrap(chunkHead).order(ByteOrder.LITTLE_ENDIAN)

                while (pos + 8L <= fileLen) {
                    if (readFullyUpTo(fis, chunkHead, 8) < 8) break
                    val c0 = chunkHead[0].toInt().toChar()
                    val c1 = chunkHead[1].toInt().toChar()
                    val c2 = chunkHead[2].toInt().toChar()
                    val c3 = chunkHead[3].toInt().toChar()
                    val chunkSize = chunkHeadBuf.getInt(4).toLong() and 0xFFFFFFFFL
                    pos += 8L

                    if (c0 == 'f' && c1 == 'm' && c2 == 't' && c3 == ' ') {
                        val toRead = chunkSize.coerceIn(0L, 64L).toInt()
                        val fmtBytes = ByteArray(toRead)
                        val actual = readFullyUpTo(fis, fmtBytes, toRead)
                        if (actual >= 16) {
                            val fb = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                            audioFormat = fb.getShort(0).toInt() and 0xFFFF
                            channels = (fb.getShort(2).toInt() and 0xFFFF).coerceIn(1, 8)
                            sampleRate = fb.getInt(4).let { if (it in 4000..384000) it else 44100 }
                            bitsPerSample = (fb.getShort(14).toInt() and 0xFFFF).let {
                                if (it in listOf(8, 16, 24, 32)) it else 16
                            }
                            foundFmt = true
                        }
                        val remaining = chunkSize - actual + (chunkSize % 2L)
                        if (remaining > 0L) skipExact(fis, remaining)
                        pos += chunkSize + (chunkSize % 2L)
                    } else if (c0 == 'd' && c1 == 'a' && c2 == 't' && c3 == 'a') {
                        val availableDataBytes = (fileLen - pos).coerceAtLeast(0L)
                        val effectiveBytes = if (chunkSize in 1L..availableDataBytes) chunkSize else availableDataBytes
                        val bytesPerFrame = channels * (bitsPerSample / 8)
                        val frameCount = (effectiveBytes / bytesPerFrame.coerceAtLeast(1)).toInt()
                        if (frameCount <= 0) return null
                        return WavPcmDataSpec(
                            audioFormat = if (foundFmt) audioFormat else 1,
                            channels = channels,
                            sampleRate = sampleRate,
                            bitsPerSample = bitsPerSample,
                            dataOffset = pos,
                            dataByteLength = frameCount.toLong() * bytesPerFrame,
                            frameCount = frameCount
                        )
                    } else {
                        val skipLen = chunkSize + (chunkSize % 2L)
                        if (skipLen <= 0L || pos + skipLen > fileLen) break
                        skipExact(fis, skipLen)
                        pos += skipLen
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeWavAsBinarySound2(
        w: PraatBinaryWriter,
        objectName: String,
        wavFile: File,
        spec: WavPcmDataSpec
    ) {
        val safeName = sanitizePraatObjectName(objectName)
        val ny = spec.channels
        val nx = spec.frameCount
        w.w8("Sound 2")
        w.w16(safeName)
        w.r64(0.0)
        w.r64(spec.xmax)
        w.i32(nx)
        w.r64(spec.dx)
        w.r64(spec.x1)
        w.r64(1.0)
        w.r64(ny.toDouble())
        w.i32(ny)
        w.r64(1.0)
        w.r64(1.0)

        // Channel-planar order: write all `nx` samples for channel 0, then channel 1, etc.
        for (ch in 0 until ny) {
            streamWavChannelSamples(wavFile, spec, ch) { sample ->
                w.r64(sample)
            }
        }
    }

    private fun writeWavAsTextSound2(
        writer: BufferedWriter,
        itemIndex: Int,
        objectName: String,
        wavFile: File,
        spec: WavPcmDataSpec
    ) {
        val safeName = escapePraatTextString(sanitizePraatObjectName(objectName))
        val ny = spec.channels
        val nx = spec.frameCount
        writer.write("    item [$itemIndex]:\n")
        writer.write("        class = \"Sound 2\" \n")
        writer.write("        name = \"$safeName\" \n")
        writer.write("        xmin = 0 \n")
        writer.write("        xmax = ${formatPraatDouble(spec.xmax)} \n")
        writer.write("        nx = $nx \n")
        writer.write("        dx = ${formatPraatDouble(spec.dx)} \n")
        writer.write("        x1 = ${formatPraatDouble(spec.x1)} \n")
        writer.write("        ymin = 1 \n")
        writer.write("        ymax = $ny \n")
        writer.write("        ny = $ny \n")
        writer.write("        dy = 1 \n")
        writer.write("        y1 = 1 \n")
        writer.write("        z [] []: \n")
        for (ch in 0 until ny) {
            val rowNum = ch + 1
            writer.write("            z [$rowNum] []:\n")
            var colNum = 1
            streamWavChannelSamples(wavFile, spec, ch) { sample ->
                writer.write("                z [$rowNum] [$colNum] = ${formatPraatDouble(sample)} \n")
                colNum++
            }
        }
    }

    private inline fun streamWavChannelSamples(
        wavFile: File,
        spec: WavPcmDataSpec,
        channelIndex: Int,
        onSample: (Double) -> Unit
    ) {
        val bytesPerSample = spec.bitsPerSample / 8
        val bytesPerFrame = spec.channels * bytesPerSample
        val framesPerChunk = (8192 / bytesPerFrame).coerceAtLeast(1)
        val chunkBuf = ByteArray(framesPerChunk * bytesPerFrame)
        val bb = ByteBuffer.wrap(chunkBuf).order(ByteOrder.LITTLE_ENDIAN)

        BufferedInputStream(FileInputStream(wavFile), 16384).use { bis ->
            skipExact(bis, spec.dataOffset)
            var framesRemaining = spec.frameCount
            while (framesRemaining > 0) {
                val targetFrames = minOf(framesRemaining, framesPerChunk)
                val targetBytes = targetFrames * bytesPerFrame
                val actualBytes = readFullyUpTo(bis, chunkBuf, targetBytes)
                val actualFrames = actualBytes / bytesPerFrame
                for (f in 0 until actualFrames) {
                    val sampleOffset = f * bytesPerFrame + channelIndex * bytesPerSample
                    val normalized = decodeNormalizedPcmSample(
                        buf = chunkBuf,
                        bb = bb,
                        offset = sampleOffset,
                        bitsPerSample = spec.bitsPerSample,
                        audioFormat = spec.audioFormat
                    )
                    onSample(normalized)
                }
                // Pad trailing zeros only if file ended unexpectedly mid-stream
                for (pad in actualFrames until targetFrames) {
                    onSample(0.0)
                }
                framesRemaining -= targetFrames
                if (actualFrames == 0) {
                    while (framesRemaining > 0) {
                        onSample(0.0)
                        framesRemaining--
                    }
                }
            }
        }
    }

    private fun decodeNormalizedPcmSample(
        buf: ByteArray,
        bb: ByteBuffer,
        offset: Int,
        bitsPerSample: Int,
        audioFormat: Int
    ): Double {
        return when (bitsPerSample) {
            8 -> {
                val u = buf[offset].toInt() and 0xFF
                (u - 128) / 128.0
            }
            16 -> {
                val s = bb.getShort(offset).toInt()
                s / 32768.0
            }
            24 -> {
                val b0 = buf[offset].toInt() and 0xFF
                val b1 = buf[offset + 1].toInt() and 0xFF
                val b2 = buf[offset + 2].toInt() // sign-extended MSB!
                val s24 = b0 or (b1 shl 8) or (b2 shl 16)
                s24 / 8388608.0
            }
            32 -> {
                if (audioFormat == 3) {
                    val flt = bb.getFloat(offset).toDouble()
                    if (flt.isFinite()) flt else 0.0
                } else {
                    val s32 = bb.getInt(offset)
                    s32 / 2147483648.0
                }
            }
            else -> 0.0
        }
    }

    private fun readFullyUpTo(input: InputStream, target: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val r = input.read(target, total, length - total)
            if (r <= 0) break
            total += r
        }
        return total
    }

    private fun skipExact(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val skipScratch = ByteArray(2048)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val toRead = minOf(remaining, skipScratch.size.toLong()).toInt()
                val r = input.read(skipScratch, 0, toRead)
                if (r <= 0) break
                remaining -= r
            }
        }
    }

    // =========================================================================
    // Binary & Text `.Collection` Readers (For Verification & Future Import)
    // =========================================================================

    private fun parseClassAndVersion(raw: String): Pair<String, Int> {
        val spaceIdx = raw.indexOf(' ')
        return if (spaceIdx > 0) {
            val cls = raw.substring(0, spaceIdx)
            val ver = raw.substring(spaceIdx + 1).trim().toIntOrNull() ?: 0
            cls to ver
        } else {
            raw to 0
        }
    }

    fun readBinaryCollection(bytes: ByteArray): List<PraatCollectionItem> {
        if (bytes.size < 27) {
            throw IllegalArgumentException("Binary data too short to be a Praat Collection.")
        }
        val magic = String(bytes, 0, 12, Charsets.US_ASCII)
        if (magic != BINARY_MAGIC) {
            throw IllegalArgumentException("Missing ooBinaryFile magic header (got '$magic').")
        }
        val r = PraatBinaryReader(bytes.inputStream(12, bytes.size - 12))
        val (topCls, _) = parseClassAndVersion(r.w8())
        if (topCls != COLLECTION_CLASS) {
            throw IllegalArgumentException("Expected top-level Collection, got '$topCls'.")
        }
        val size = r.i32()
        val items = ArrayList<PraatCollectionItem>(size.coerceAtLeast(0))
        for (i in 0 until size) {
            val (clsName, ver) = parseClassAndVersion(r.w8())
            val objName = r.w16()
            when (clsName) {
                "Sound" -> {
                    val xmin = r.r64()
                    val xmax = r.r64()
                    val nx = r.i32()
                    val dx = r.r64()
                    val x1 = r.r64()
                    r.r64() // ymin
                    r.r64() // ymax
                    val ny = r.i32()
                    r.r64() // dy
                    r.r64() // y1
                    val channels = List(ny) {
                        DoubleArray(nx) { if (ver < 2) r.r32() else r.r64() }
                    }
                    items.add(PraatSoundObject(objName, xmin, xmax, dx, x1, channels))
                }
                "TextGrid" -> {
                    val xmin = r.r64()
                    val xmax = r.r64()
                    val tiers = mutableListOf<PraatTier>()
                    if (r.bool8()) {
                        val nTiers = r.i32()
                        for (t in 0 until nTiers) {
                            val (tCls, _) = parseClassAndVersion(r.w8())
                            val tName = r.w16()
                            val tXmin = r.r64()
                            val tXmax = r.r64()
                            when (tCls) {
                                "IntervalTier" -> {
                                    val nIv = r.i32()
                                    val ivs = MutableList(nIv) {
                                        PraatTextInterval(r.r64(), r.r64(), r.w16())
                                    }
                                    if (ivs.isEmpty()) ivs.add(PraatTextInterval(tXmin, tXmax, ""))
                                    tiers.add(PraatIntervalTier(tName, tXmin, tXmax, ivs))
                                }
                                "TextTier", "MarkTier" -> {
                                    val nPt = r.i32()
                                    val pts = List(nPt) { PraatTextPoint(r.r64(), r.w16()) }
                                    tiers.add(PraatTextTier(tName, tXmin, tXmax, pts))
                                }
                                else -> throw UnsupportedOperationException("Unsupported tier class: $tCls")
                            }
                        }
                    }
                    items.add(PraatTextGridObject(objName, xmin, xmax, tiers))
                }
                "Table" -> {
                    val nCols = r.i32()
                    val headers = List(nCols) { r.w16() }
                    val nRows = r.i32()
                    val rows = List(nRows) {
                        val rowCols = r.i32()
                        List(rowCols) { r.w16() }
                    }
                    items.add(PraatTableObject(objName, headers, rows))
                }
                else -> throw UnsupportedOperationException("Unsupported Praat class in binary reader: $clsName $ver")
            }
        }
        return items
    }

    fun readTextCollection(text: String): List<PraatCollectionItem> {
        val sc = PraatTextScanner(text)
        val (topCls, _) = parseClassAndVersion(sc.getString())
        if (topCls != COLLECTION_CLASS) {
            throw IllegalArgumentException("Expected top-level Collection, got '$topCls'.")
        }
        val size = sc.getInt()
        val items = ArrayList<PraatCollectionItem>(size.coerceAtLeast(0))
        for (i in 0 until size) {
            val (clsName, _) = parseClassAndVersion(sc.getString())
            val objName = sc.getString()
            when (clsName) {
                "Sound" -> {
                    val xmin = sc.getDouble()
                    val xmax = sc.getDouble()
                    val nx = sc.getInt()
                    val dx = sc.getDouble()
                    val x1 = sc.getDouble()
                    sc.getDouble() // ymin
                    sc.getDouble() // ymax
                    val ny = sc.getInt()
                    sc.getDouble() // dy
                    sc.getDouble() // y1
                    val channels = List(ny) {
                        DoubleArray(nx) { sc.getDouble() }
                    }
                    items.add(PraatSoundObject(objName, xmin, xmax, dx, x1, channels))
                }
                "TextGrid" -> {
                    val xmin = sc.getDouble()
                    val xmax = sc.getDouble()
                    val tiers = mutableListOf<PraatTier>()
                    if (sc.getEnum() == "exists") {
                        val nTiers = sc.getInt()
                        for (t in 0 until nTiers) {
                            val (tCls, _) = parseClassAndVersion(sc.getString())
                            val tName = sc.getString()
                            val tXmin = sc.getDouble()
                            val tXmax = sc.getDouble()
                            when (tCls) {
                                "IntervalTier" -> {
                                    val nIv = sc.getInt()
                                    val ivs = MutableList(nIv) {
                                        PraatTextInterval(sc.getDouble(), sc.getDouble(), sc.getString())
                                    }
                                    if (ivs.isEmpty()) ivs.add(PraatTextInterval(tXmin, tXmax, ""))
                                    tiers.add(PraatIntervalTier(tName, tXmin, tXmax, ivs))
                                }
                                "TextTier", "MarkTier" -> {
                                    val nPt = sc.getInt()
                                    val pts = List(nPt) { PraatTextPoint(sc.getDouble(), sc.getString()) }
                                    tiers.add(PraatTextTier(tName, tXmin, tXmax, pts))
                                }
                                else -> throw UnsupportedOperationException("Unsupported tier class: $tCls")
                            }
                        }
                    }
                    items.add(PraatTextGridObject(objName, xmin, xmax, tiers))
                }
                "Table" -> {
                    val nCols = sc.getInt()
                    val headers = List(nCols) { sc.getString() }
                    val nRows = sc.getInt()
                    val rows = List(nRows) {
                        val rowCols = sc.getInt()
                        List(rowCols) { sc.getString() }
                    }
                    items.add(PraatTableObject(objName, headers, rows))
                }
                else -> throw UnsupportedOperationException("Unsupported Praat class in text reader: $clsName")
            }
        }
        return items
    }
}
