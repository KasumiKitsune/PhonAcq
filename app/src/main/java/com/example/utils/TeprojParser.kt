package com.example.utils

import com.example.data.ParsedWord
import com.example.i18n.S
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Unified, fault-tolerant parser for PhonTracer `.teproj` (`project.json`) and `.ptwl`
 * wordlist documents, plus charset-resilient (UTF-8 -> GB18030/GBK) bounded ZIP utilities.
 */
object TeprojParser {

    const val MAX_METADATA_ENTRY_BYTES = 32 * 1024 * 1024 // 32 MB
    const val MAX_SINGLE_MEDIA_ENTRY_BYTES = 512L * 1024L * 1024L // 512 MB
    const val MAX_TOTAL_EXTRACT_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB

    data class TeprojWordSlot(
        val seqIndex1Based: Int,
        val itemId: String,
        val groupName: String,
        val label: String,
        val remark: String,
        val imagePath: String,
        val occurrenceInGroupLabel: Int,
        val occurrenceInLabel: Int
    )

    data class TeprojSpeakerItemAudio(
        val itemId: String,
        val label: String,
        val groupName: String,
        val baseRemark: String,
        val speakerNote: String,
        val audioPath: String,
        val longAudioPath: String,
        val sliceStartSec: Double?,
        val sliceEndSec: Double?,
        val durationMs: Long,
        val importIndex: Int?,
        val occurrenceInGroupLabel: Int,
        val occurrenceInLabel: Int
    ) {
        val hasIndependentAudio: Boolean
            get() = audioPath.isNotBlank()

        val hasLongAudioSlice: Boolean
            get() = !hasIndependentAudio &&
                longAudioPath.isNotBlank() &&
                sliceStartSec != null &&
                sliceEndSec != null &&
                sliceStartSec.isFinite() &&
                sliceEndSec.isFinite() &&
                sliceEndSec > sliceStartSec + 0.005

        val hasAnyAudio: Boolean
            get() = hasIndependentAudio || hasLongAudioSlice
    }

    data class TeprojSpeakerRecord(
        val code: String,
        val name: String,
        val gender: String,
        val age: Int,
        val dialect: String,
        val group: String,
        val notes: String,
        val isActive: Boolean,
        val longAudioPath: String,
        val items: List<TeprojSpeakerItemAudio>
    )

    data class ParsedTeprojDocument(
        val title: String,
        val description: String,
        val enablePicturePrompts: Boolean,
        val activeSpeakerId: String,
        val words: List<ParsedWord>,
        val wordSlots: List<TeprojWordSlot>,
        val speakers: List<TeprojSpeakerRecord>,
        val longAudioPaths: Set<String>,
        val totalAudioCandidates: Int,
        val warnings: List<String> = emptyList()
    )

    private val GENERIC_IMPORT_TITLES = setOf("从表格导入", "未命名", "untitled", "imported")

    private val META_SKIP_KEYS = setOf(
        "image_path", "imagePath", "path", "audio_path", "pitch_data_file", "formant_data_file"
    )

    fun normalizeArchivePath(raw: String): String {
        return raw.replace('\\', '/').trim().trimStart('/')
    }

    fun normalizeGroupName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed == "默认组" || trimmed == S.common.defaultGroupFallback) "" else trimmed
    }

    /**
     * Decodes JSON / text bytes cleanly, stripping UTF-8 BOM and auto-detecting UTF-16 / GB18030.
     */
    fun decodeTextClean(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return SpreadsheetWordImporter.decodeTextSmart(bytes)
            .removePrefix("\uFEFF")
            .trim()
    }

    /**
     * Reads up to [maxBytes] from [input] without throwing OOM on corrupt/malicious entries.
     */
    fun readBytesBounded(input: InputStream, maxBytes: Int = MAX_METADATA_ENTRY_BYTES): ByteArray {
        val baos = ByteArrayOutputStream(minOf(maxBytes, 65536))
        val buf = ByteArray(16384)
        var total = 0
        while (true) {
            val remaining = maxBytes - total
            if (remaining <= 0) break
            val read = input.read(buf, 0, minOf(buf.size, remaining))
            if (read <= 0) break
            baos.write(buf, 0, read)
            total += read
        }
        return baos.toByteArray()
    }

    /**
     * Copies a ZIP entry stream to [destFile] with a hard per-entry byte limit.
     */
    fun copyEntryToFileBounded(
        input: InputStream,
        destFile: File,
        maxBytes: Long = MAX_SINGLE_MEDIA_ENTRY_BYTES
    ): Long {
        destFile.parentFile?.mkdirs()
        var written = 0L
        val buf = ByteArray(16384)
        FileOutputStream(destFile).use { fos ->
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                if (written + n > maxBytes) {
                    throw IllegalStateException("ZIP 条目解压体积超出安全上限 (${maxBytes / (1024 * 1024)} MB)")
                }
                fos.write(buf, 0, n)
                written += n
            }
        }
        return written
    }

    /**
     * Iterates all entries of [zipFile] using UTF-8 first, automatically falling back to
     * GB18030 / GBK when encountering legacy Windows non-UTF-8 Chinese entry names (`MALFORMED`).
     */
    fun forEachZipEntryResilient(
        zipFile: File,
        onBeforeRetry: () -> Unit = {},
        onEntry: (entry: ZipEntry, zis: ZipInputStream) -> Unit
    ) {
        val charsets = buildList {
            add(Charsets.UTF_8)
            runCatching { Charset.forName("GB18030") }.getOrNull()?.let { add(it) }
            runCatching { Charset.forName("GBK") }.getOrNull()?.let { if (it !in this) add(it) }
        }

        var lastError: Throwable? = null
        for ((idx, charset) in charsets.withIndex()) {
            if (idx > 0) {
                onBeforeRetry()
            }
            try {
                ZipInputStream(BufferedInputStream(FileInputStream(zipFile)), charset).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        onEntry(entry, zis)
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                return
            } catch (e: IllegalArgumentException) {
                // Standard JDK/Android ZipInputStream throws IllegalArgumentException("MALFORMED") on GBK names
                lastError = e
            } catch (e: ZipException) {
                lastError = e
            }
        }
        if (lastError != null) {
            throw lastError
        }
    }

    /**
     * Same charset-resilient ZIP iterator for an in-memory [ByteArray].
     */
    fun forEachZipBytesResilient(
        bytes: ByteArray,
        onBeforeRetry: () -> Unit = {},
        onEntry: (entry: ZipEntry, zis: ZipInputStream) -> Unit
    ) {
        val charsets = buildList {
            add(Charsets.UTF_8)
            runCatching { Charset.forName("GB18030") }.getOrNull()?.let { add(it) }
            runCatching { Charset.forName("GBK") }.getOrNull()?.let { if (it !in this) add(it) }
        }

        var lastError: Throwable? = null
        for ((idx, charset) in charsets.withIndex()) {
            if (idx > 0) {
                onBeforeRetry()
            }
            try {
                ZipInputStream(bytes.inputStream(), charset).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        onEntry(entry, zis)
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                return
            } catch (e: IllegalArgumentException) {
                lastError = e
            } catch (e: ZipException) {
                lastError = e
            }
        }
        if (lastError != null) {
            throw lastError
        }
    }

    /**
     * Parses a standalone JSON text (`.ptwl`, `project.json`, or PhonAcq group JSON).
     * Returns `null` if [rawText] is NOT a valid JSON object; returns a list (possibly empty)
     * when [rawText] IS a valid JSON object so callers will not misparse arbitrary JSON as text lines.
     */
    fun parseWordlistJsonIfValidObject(rawText: String): List<ParsedWord>? {
        val cleaned = rawText.removePrefix("\uFEFF").trim()
        if (!cleaned.startsWith("{")) return null
        val root = try {
            JSONObject(cleaned)
        } catch (_: Exception) {
            return null
        }
        return parseProjectJsonObject(root).words
    }

    /**
     * Full PhonTracer `.teproj` (`project.json`) / `.ptwl` parser supporting all schema generations.
     */
    fun parseProjectJson(rawText: String): ParsedTeprojDocument? {
        val cleaned = rawText.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null
        val root = try {
            JSONObject(cleaned)
        } catch (_: Exception) {
            return null
        }
        return parseProjectJsonObject(root)
    }

    fun parseProjectJsonObject(root: JSONObject): ParsedTeprojDocument {
        val warnings = mutableListOf<String>()
        val activeSpeakerId = optCleanString(root, "active_speaker_id")

        // 1. Parse speakers first (so we can use active speaker order & enrich metadata / layer 3 & 4 fallback)
        val rawSpeakers = parseSpeakers(root, activeSpeakerId, warnings)
        val orderedSpeakers = rawSpeakers.sortedByDescending { it.isActive }
        val longAudioPaths = rawSpeakers.mapNotNull { it.longAudioPath.takeIf(String::isNotBlank) }.toSet()

        // 2. Resolve title & description with full PhonTracer v1/v2 + PhonAcq priority chain
        val wordlistDocObj = root.optJSONObject("wordlist_doc")
        val wordlistMetaObj = root.optJSONObject("wordlist_meta")
        val activeSpeakerObj = root.optJSONObject("speakers")?.let { spks ->
            if (activeSpeakerId.isNotBlank()) spks.optJSONObject(activeSpeakerId) else null
        }
        val activeSpeakerWlDoc = activeSpeakerObj?.optJSONObject("wordlist_doc")

        val itemWordlistTitle = findItemLevelWordlistTitle(root, activeSpeakerId)
        val titleCandidates = listOf(
            optCleanString(root, "wordlist_title"),
            wordlistDocObj?.let { optCleanString(it, "title") }.orEmpty(),
            wordlistMetaObj?.let { optCleanString(it, "title") }.orEmpty(),
            optCleanString(root, "name"),
            optCleanString(root, "projectName"),
            optCleanString(root, "project_name"),
            optCleanString(root, "title"),
            activeSpeakerWlDoc?.let { optCleanString(it, "title") }.orEmpty(),
            itemWordlistTitle
        )
        // Only adopt non-generic titles so placeholders like "从表格导入" gracefully fall back to the .teproj filename
        val resolvedTitle = titleCandidates.firstOrNull { cand ->
            cand.isNotBlank() && cand.lowercase(Locale.ROOT) !in GENERIC_IMPORT_TITLES
        }.orEmpty()

        val resolvedDesc = listOf(
            optCleanString(root, "wordlist_note"),
            wordlistDocObj?.let { optCleanString(it, "note") }.orEmpty(),
            wordlistMetaObj?.let { optCleanString(it, "note") }.orEmpty(),
            optCleanString(root, "description"),
            optCleanString(root, "note"),
            activeSpeakerWlDoc?.let { optCleanString(it, "note") }.orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        var enablePicturePrompts = wordlistMetaObj?.optBoolean("enable_picture_prompts", false) == true ||
            root.optBoolean("enable_picture_prompts", false)

        // 3. Extract wordlist through 4-layer PhonTracer schema cascade
        data class RawSlot(
            val itemId: String,
            val groupName: String,
            val label: String,
            val remark: String,
            val imagePath: String
        )

        var rawSlots: List<RawSlot> = emptyList()

        // Layer 1: Top-level `groups` (JSONArray or JSONObject)
        if (root.has("groups")) {
            val extracted = extractSlotsFromGroupsValue(root.opt("groups"), warnings)
            if (extracted.isNotEmpty()) {
                rawSlots = extracted.map { RawSlot(it.itemId, it.groupName, it.label, it.remark, it.imagePath) }
            }
        }

        // Layer 2: Top-level `wordlist_doc.groups` (.ptwl v2) or flat top-level `words` / `items`
        if (rawSlots.isEmpty() && wordlistDocObj != null && wordlistDocObj.has("groups")) {
            val extracted = extractSlotsFromGroupsValue(wordlistDocObj.opt("groups"), warnings)
            if (extracted.isNotEmpty()) {
                rawSlots = extracted.map { RawSlot(it.itemId, it.groupName, it.label, it.remark, it.imagePath) }
            }
        }
        if (rawSlots.isEmpty()) {
            val flatCandidate = root.opt("words") ?: root.opt("items")
            if (flatCandidate != null) {
                val extracted = extractSlotsFromFlatItems(flatCandidate, defaultGroup = "", warnings = warnings)
                if (extracted.isNotEmpty()) {
                    rawSlots = extracted.map { RawSlot(it.itemId, it.groupName, it.label, it.remark, it.imagePath) }
                }
            }
        }

        // Layer 3: Speaker-level `speakers.<spk>.wordlist_doc.groups` (active speaker first)
        if (rawSlots.isEmpty()) {
            val speakersObj = root.optJSONObject("speakers")
            if (speakersObj != null) {
                val orderedKeys = orderedSpeakerKeys(speakersObj, activeSpeakerId)
                for (spkKey in orderedKeys) {
                    val spkObj = speakersObj.optJSONObject(spkKey) ?: continue
                    val spkWlDoc = spkObj.optJSONObject("wordlist_doc") ?: continue
                    val extracted = extractSlotsFromGroupsValue(spkWlDoc.opt("groups"), warnings)
                    if (extracted.isNotEmpty()) {
                        rawSlots = extracted.map { RawSlot(it.itemId, it.groupName, it.label, it.remark, it.imagePath) }
                        break
                    }
                }
            }
        }

        // Layer 4: Reconstruct wordlist from `speakers.<spk>.items` (active speaker first, preserving `project_groups` order)
        // Crucially includes items WITHOUT `path` (long-audio mode and unrecorded items)!
        if (rawSlots.isEmpty() && orderedSpeakers.isNotEmpty()) {
            val mergedBySlotKey = LinkedHashMap<Triple<String, String, Int>, RawSlot>()
            for (spk in orderedSpeakers) {
                for (item in spk.items) {
                    val slotKey = Triple(item.groupName, item.label, item.occurrenceInGroupLabel)
                    val existing = mergedBySlotKey[slotKey]
                    if (existing == null) {
                        mergedBySlotKey[slotKey] = RawSlot(
                            itemId = item.itemId,
                            groupName = item.groupName,
                            label = item.label,
                            remark = item.baseRemark,
                            imagePath = ""
                        )
                    } else if (existing.remark.isBlank() && item.baseRemark.isNotBlank()) {
                        mergedBySlotKey[slotKey] = existing.copy(remark = item.baseRemark)
                    }
                }
            }
            rawSlots = mergedBySlotKey.values.toList()
        }

        // Enrich blank remarks on Layer 1-3 words from speaker items if speaker items carry linguistic metadata
        if (rawSlots.isNotEmpty() && orderedSpeakers.isNotEmpty()) {
            val remarkById = HashMap<String, String>()
            val remarkByGroupLabelOcc = HashMap<Triple<String, String, Int>, String>()
            val remarkByLabelOcc = HashMap<Pair<String, Int>, String>()
            for (spk in orderedSpeakers) {
                for (item in spk.items) {
                    if (item.baseRemark.isNotBlank()) {
                        if (item.itemId.isNotBlank()) remarkById.putIfAbsent(item.itemId, item.baseRemark)
                        remarkByGroupLabelOcc.putIfAbsent(
                            Triple(item.groupName, item.label, item.occurrenceInGroupLabel),
                            item.baseRemark
                        )
                        remarkByLabelOcc.putIfAbsent(item.label to item.occurrenceInLabel, item.baseRemark)
                    }
                }
            }
            val groupLabelCounts = HashMap<Pair<String, String>, Int>()
            val labelCounts = HashMap<String, Int>()
            rawSlots = rawSlots.map { slot ->
                val glKey = slot.groupName to slot.label
                val glOcc = groupLabelCounts[glKey] ?: 0
                groupLabelCounts[glKey] = glOcc + 1
                val lOcc = labelCounts[slot.label] ?: 0
                labelCounts[slot.label] = lOcc + 1

                if (slot.remark.isNotBlank()) {
                    slot
                } else {
                    val enriched = remarkById[slot.itemId]
                        ?: remarkByGroupLabelOcc[Triple(slot.groupName, slot.label, glOcc)]
                        ?: remarkByLabelOcc[slot.label to lOcc]
                        ?: ""
                    if (enriched.isNotBlank()) slot.copy(remark = enriched) else slot
                }
            }
        }

        // Build final wordSlots & ParsedWord list
        val wordSlots = ArrayList<TeprojWordSlot>(rawSlots.size)
        val parsedWords = ArrayList<ParsedWord>(rawSlots.size)
        val finalGlCounts = HashMap<Pair<String, String>, Int>()
        val finalLCounts = HashMap<String, Int>()

        rawSlots.forEachIndexed { idx, slot ->
            val glKey = slot.groupName to slot.label
            val glOcc = finalGlCounts[glKey] ?: 0
            finalGlCounts[glKey] = glOcc + 1
            val lOcc = finalLCounts[slot.label] ?: 0
            finalLCounts[slot.label] = lOcc + 1

            if (slot.imagePath.isNotBlank()) {
                enablePicturePrompts = true
            }
            wordSlots.add(
                TeprojWordSlot(
                    seqIndex1Based = idx + 1,
                    itemId = slot.itemId.ifBlank { String.format(Locale.US, "%03d", idx + 1) },
                    groupName = slot.groupName,
                    label = slot.label,
                    remark = slot.remark,
                    imagePath = slot.imagePath,
                    occurrenceInGroupLabel = glOcc,
                    occurrenceInLabel = lOcc
                )
            )
            parsedWords.add(
                ParsedWord(
                    text = slot.label,
                    remark = slot.remark,
                    groupName = slot.groupName,
                    imagePath = slot.imagePath
                )
            )
        }

        val totalAudioCandidates = rawSpeakers.sumOf { spk -> spk.items.count { it.hasAnyAudio } }

        return ParsedTeprojDocument(
            title = resolvedTitle,
            description = resolvedDesc,
            enablePicturePrompts = enablePicturePrompts,
            activeSpeakerId = activeSpeakerId,
            words = parsedWords,
            wordSlots = wordSlots,
            speakers = orderedSpeakers,
            longAudioPaths = longAudioPaths,
            totalAudioCandidates = totalAudioCandidates,
            warnings = warnings
        )
    }

    private data class ExtractedSlot(
        val itemId: String,
        val groupName: String,
        val label: String,
        val remark: String,
        val imagePath: String
    )

    private fun extractSlotsFromGroupsValue(
        groupsVal: Any?,
        warnings: MutableList<String>
    ): List<ExtractedSlot> {
        val result = ArrayList<ExtractedSlot>()
        var runningIdx = 1
        when (groupsVal) {
            is JSONArray -> {
                for (g in 0 until groupsVal.length()) {
                    val elem = groupsVal.opt(g)
                    val gObj = elem as? JSONObject ?: continue
                    val rawGroupName = optCleanString(gObj, "name", "group", "title")
                    val cleanGroup = normalizeGroupName(rawGroupName)
                    val itemsVal = gObj.opt("items") ?: gObj.opt("words")
                    val sub = extractSlotsFromFlatItems(itemsVal, cleanGroup, warnings, runningIdx)
                    result.addAll(sub)
                    runningIdx += sub.size
                }
            }
            is JSONObject -> {
                val keys = groupsVal.keys()
                while (keys.hasNext()) {
                    val gKey = keys.next()
                    val gVal = groupsVal.opt(gKey)
                    when (gVal) {
                        is JSONObject -> {
                            val rawGroupName = optCleanString(gVal, "name", "group", "title").ifBlank { gKey }
                            val cleanGroup = normalizeGroupName(rawGroupName)
                            val itemsVal = gVal.opt("items") ?: gVal.opt("words")
                            val sub = extractSlotsFromFlatItems(itemsVal, cleanGroup, warnings, runningIdx)
                            result.addAll(sub)
                            runningIdx += sub.size
                        }
                        is JSONArray -> {
                            val cleanGroup = normalizeGroupName(gKey)
                            val sub = extractSlotsFromFlatItems(gVal, cleanGroup, warnings, runningIdx)
                            result.addAll(sub)
                            runningIdx += sub.size
                        }
                    }
                }
            }
        }
        return result
    }

    private fun extractSlotsFromFlatItems(
        itemsVal: Any?,
        defaultGroup: String,
        warnings: MutableList<String>,
        startRunningIdx: Int = 1
    ): List<ExtractedSlot> {
        val result = ArrayList<ExtractedSlot>()
        var runningIdx = startRunningIdx

        fun handleItemObject(itemObj: JSONObject, fallbackId: String) {
            val label = extractItemLabel(itemObj)
            if (label.isEmpty()) return
            val itemId = optCleanString(itemObj, "id").ifBlank { fallbackId }
            val itemGroup = optCleanString(itemObj, "group", "group_name", "groupName", "category")
                .let { if (it.isNotBlank()) normalizeGroupName(it) else defaultGroup }
            val remark = formatItemRemark(itemObj, itemGroup)
            val imgRel = optCleanString(itemObj, "image_path", "imagePath").ifEmpty {
                itemObj.optJSONObject("meta")?.let { optCleanString(it, "image_path", "imagePath") }.orEmpty()
            }.let { if (it.isNotBlank()) normalizeArchivePath(it) else "" }

            result.add(
                ExtractedSlot(
                    itemId = itemId,
                    groupName = itemGroup,
                    label = label,
                    remark = remark,
                    imagePath = imgRel
                )
            )
            runningIdx++
        }

        when (itemsVal) {
            is JSONArray -> {
                for (k in 0 until itemsVal.length()) {
                    val elem = itemsVal.opt(k)
                    when (elem) {
                        is JSONObject -> handleItemObject(elem, String.format(Locale.US, "%03d", runningIdx))
                        is String -> {
                            val clean = elem.trim()
                            if (clean.isNotEmpty()) {
                                result.add(
                                    ExtractedSlot(
                                        itemId = String.format(Locale.US, "%03d", runningIdx),
                                        groupName = defaultGroup,
                                        label = clean,
                                        remark = "",
                                        imagePath = ""
                                    )
                                )
                                runningIdx++
                            }
                        }
                    }
                }
            }
            is JSONObject -> {
                val keys = itemsVal.keys()
                while (keys.hasNext()) {
                    val iid = keys.next()
                    val itemObj = itemsVal.optJSONObject(iid) ?: continue
                    handleItemObject(itemObj, iid)
                }
            }
        }
        return result
    }

    private fun parseSpeakers(
        root: JSONObject,
        activeSpeakerId: String,
        warnings: MutableList<String>
    ): List<TeprojSpeakerRecord> {
        val speakersObj = root.optJSONObject("speakers") ?: return emptyList()
        val orderedKeys = orderedSpeakerKeys(speakersObj, activeSpeakerId)
        val records = ArrayList<TeprojSpeakerRecord>(orderedKeys.size)

        for (spkKey in orderedKeys) {
            val spkObj = speakersObj.optJSONObject(spkKey) ?: continue
            val spkCode = optCleanString(spkObj, "id", "speaker_code").ifBlank { spkKey }
            val spkName = optCleanString(spkObj, "name").ifBlank { spkCode }
            val gender = optCleanString(spkObj, "gender", "gender_identity")
            val age = spkObj.optInt("age", -1)
            val dialect = optCleanString(spkObj, "dialect")
            val spkGroup = optCleanString(spkObj, "group", "group_name", "groupName")
            val spkNotes = optCleanString(spkObj, "notes", "note")
            val rawLongAudio = optCleanString(spkObj, "long_audio_path", "longAudioPath")
            val normalizedLongAudio = if (rawLongAudio.isNotBlank()) normalizeArchivePath(rawLongAudio) else ""

            // Group order from speaker's `project_groups` if present
            val groupOrderMap = LinkedHashMap<String, Int>()
            val projGroupsArr = spkObj.optJSONArray("project_groups")
            if (projGroupsArr != null) {
                for (i in 0 until projGroupsArr.length()) {
                    val gn = normalizeGroupName(projGroupsArr.optString(i, ""))
                    groupOrderMap.putIfAbsent(gn, i)
                }
            }

            data class RawSpkItem(
                val insertionOrder: Int,
                val itemId: String,
                val itemObj: JSONObject,
                val groupName: String,
                val label: String,
                val importIndex: Int?
            )

            val rawItems = ArrayList<RawSpkItem>()
            val itemsVal = spkObj.opt("items")
            when (itemsVal) {
                is JSONObject -> {
                    val itemKeys = itemsVal.keys()
                    var ord = 0
                    while (itemKeys.hasNext()) {
                        val iid = itemKeys.next()
                        val itemObj = itemsVal.optJSONObject(iid) ?: continue
                        val label = extractItemLabel(itemObj)
                        if (label.isEmpty()) continue
                        val grp = normalizeGroupName(optCleanString(itemObj, "group", "group_name", "groupName", "category"))
                        val impIdx = if (itemObj.has("import_index") && !itemObj.isNull("import_index")) {
                            itemObj.optInt("import_index", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                        } else null
                        rawItems.add(RawSpkItem(ord++, iid, itemObj, grp, label, impIdx))
                    }
                }
                is JSONArray -> {
                    for (i in 0 until itemsVal.length()) {
                        val itemObj = itemsVal.optJSONObject(i) ?: continue
                        val label = extractItemLabel(itemObj)
                        if (label.isEmpty()) continue
                        val iid = optCleanString(itemObj, "id").ifBlank { String.format(Locale.US, "%03d", i + 1) }
                        val grp = normalizeGroupName(optCleanString(itemObj, "group", "group_name", "groupName", "category"))
                        val impIdx = if (itemObj.has("import_index") && !itemObj.isNull("import_index")) {
                            itemObj.optInt("import_index", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                        } else null
                        rawItems.add(RawSpkItem(i, iid, itemObj, grp, label, impIdx))
                    }
                }
            }

            // Sort items deterministically respecting `project_groups`, then `import_index` (if present), then insertion order
            val sortedRawItems = if (groupOrderMap.isNotEmpty() || rawItems.any { it.importIndex != null }) {
                rawItems.sortedWith(
                    compareBy<RawSpkItem>(
                        { item ->
                            if (groupOrderMap.isNotEmpty()) {
                                groupOrderMap[item.groupName] ?: Int.MAX_VALUE
                            } else {
                                0
                            }
                        },
                        { it.importIndex ?: Int.MAX_VALUE },
                        { it.insertionOrder }
                    )
                )
            } else {
                rawItems
            }

            val glOccMap = HashMap<Pair<String, String>, Int>()
            val lOccMap = HashMap<String, Int>()
            val parsedItems = ArrayList<TeprojSpeakerItemAudio>(sortedRawItems.size)

            for (raw in sortedRawItems) {
                val itemObj = raw.itemObj
                val glKey = raw.groupName to raw.label
                val glOcc = glOccMap[glKey] ?: 0
                glOccMap[glKey] = glOcc + 1
                val lOcc = lOccMap[raw.label] ?: 0
                lOccMap[raw.label] = lOcc + 1

                val rawPath = optCleanString(itemObj, "path", "audio_path", "wav_path", "file")
                val normAudioPath = if (rawPath.isNotBlank()) normalizeArchivePath(rawPath) else ""

                val sliceBounds = resolveItemSliceBounds(itemObj)
                val durationMs = when {
                    normAudioPath.isNotBlank() -> {
                        val macroEnd = itemObj.optDouble("macro_end", Double.NaN)
                        val macroStart = itemObj.optDouble("macro_start", 0.0).let { if (it.isFinite() && it >= 0.0) it else 0.0 }
                        if (macroEnd.isFinite() && macroEnd > macroStart) {
                            ((macroEnd - macroStart) * 1000.0).toLong().coerceAtLeast(0L)
                        } else 0L
                    }
                    sliceBounds != null -> {
                        ((sliceBounds.second - sliceBounds.first) * 1000.0).toLong().coerceAtLeast(0L)
                    }
                    else -> 0L
                }

                val baseRemark = formatItemRemark(itemObj, raw.groupName)
                val explicitSpkNote = optCleanString(itemObj, "speaker_note", "speakerNote")
                val rawNote = optCleanString(itemObj, "note", "item_note")
                val resolvedSpkNote = explicitSpkNote.ifBlank {
                    // Only treat rawNote as speakerNote if distinct from formatted baseRemark (handled further in ZipUtility)
                    if (rawNote.isNotBlank() && rawNote != baseRemark) rawNote else ""
                }

                parsedItems.add(
                    TeprojSpeakerItemAudio(
                        itemId = raw.itemId,
                        label = raw.label,
                        groupName = raw.groupName,
                        baseRemark = baseRemark,
                        speakerNote = resolvedSpkNote,
                        audioPath = normAudioPath,
                        longAudioPath = if (normAudioPath.isBlank()) normalizedLongAudio else "",
                        sliceStartSec = sliceBounds?.first,
                        sliceEndSec = sliceBounds?.second,
                        durationMs = durationMs,
                        importIndex = raw.importIndex,
                        occurrenceInGroupLabel = glOcc,
                        occurrenceInLabel = lOcc
                    )
                )
            }

            records.add(
                TeprojSpeakerRecord(
                    code = spkCode,
                    name = spkName,
                    gender = gender,
                    age = age,
                    dialect = dialect,
                    group = spkGroup,
                    notes = spkNotes,
                    isActive = activeSpeakerId.isNotBlank() &&
                        (spkKey == activeSpeakerId || spkCode == activeSpeakerId),
                    longAudioPath = normalizedLongAudio,
                    items = parsedItems
                )
            )
        }

        return records
    }

    private fun resolveItemSliceBounds(itemObj: JSONObject): Pair<Double, Double>? {
        val pairs = listOf(
            "macro_start" to "macro_end",
            "start" to "end",
            "raw_start" to "raw_end"
        )
        for ((sKey, eKey) in pairs) {
            if (!itemObj.has(sKey) || !itemObj.has(eKey) || itemObj.isNull(sKey) || itemObj.isNull(eKey)) continue
            val s = itemObj.optDouble(sKey, Double.NaN)
            val e = itemObj.optDouble(eKey, Double.NaN)
            if (s.isFinite() && e.isFinite() && e > s + 0.005) {
                return s.coerceAtLeast(0.0) to e
            }
        }
        return null
    }

    private fun extractItemLabel(itemObj: JSONObject): String {
        return optCleanString(itemObj, "label", "word", "text", "char", "character", "name", "title")
    }

    /**
     * Formats PhonTracer item linguistic metadata (`note`/`item_note`, `meta`/`item_meta`/`fields`/`custom_fields`,
     * `aliases`/`item_aliases`, `tags`/`item_tags`) into PhonAcq's `Word.remark` without losing information
     * while preserving 100% exact round-trip fidelity for PhonAcq-exported `.teproj` files.
     */
    private fun formatItemRemark(itemObj: JSONObject, groupName: String): String {
        val primaryNote = optCleanString(itemObj, "note", "item_note", "remark")
        val metadataSource = optCleanString(itemObj, "metadata_source")

        // Exact lossless round-trip for PhonAcq's own exported `.teproj`
        if (metadataSource.equals("phonacq", ignoreCase = true) && primaryNote.isNotEmpty()) {
            return primaryNote
        }

        val parts = ArrayList<String>()
        if (primaryNote.isNotEmpty()) {
            parts.add(primaryNote)
        }

        val coveredText = StringBuilder(primaryNote.lowercase(Locale.ROOT))
        fun alreadyCovered(token: String): Boolean {
            val clean = token.trim().lowercase(Locale.ROOT)
            return clean.isEmpty() || coveredText.contains(clean)
        }
        fun recordPart(display: String, rawValueForDedup: String = display) {
            if (display.isBlank()) return
            parts.add(display)
            coveredText.append(' ').append(rawValueForDedup.lowercase(Locale.ROOT))
        }

        // Collect structured linguistic metadata objects
        val metaObjects = listOfNotNull(
            itemObj.optJSONObject("meta"),
            itemObj.optJSONObject("item_meta"),
            itemObj.optJSONObject("fields"),
            itemObj.optJSONObject("custom_fields")
        )
        val priorityKeys = listOf("ipa", "IPA", "音标", "国际音标", "pinyin", "拼音", "声调", "声调组合", "调值", "tone", "韵母", "声母", "gloss", "释义", "extra")
        val handledMetaKeys = HashSet<String>()

        for (metaObj in metaObjects) {
            for (pk in priorityKeys) {
                if (metaObj.has(pk) && !metaObj.isNull(pk)) {
                    handledMetaKeys.add(pk)
                    val v = scalarToCleanString(metaObj.opt(pk))
                    if (v.isNotEmpty() && !alreadyCovered(v)) {
                        val isDirectField = pk.equals("ipa", true) || pk.equals("pinyin", true) ||
                            pk == "拼音" || pk.equals("gloss", true) || pk == "释义" || pk.equals("extra", true)
                        val display = if (isDirectField) v else "$pk:$v"
                        recordPart(display, v)
                    }
                }
            }
            val keys = metaObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k in handledMetaKeys || k in META_SKIP_KEYS || metaObj.isNull(k)) continue
                handledMetaKeys.add(k)
                val v = scalarToCleanString(metaObj.opt(k))
                if (v.isNotEmpty() && !alreadyCovered(v)) {
                    recordPart("$k:$v", v)
                }
            }
        }

        // Aliases (e.g. pinyin / romanization variants)
        val aliasArrays = listOfNotNull(itemObj.optJSONArray("aliases"), itemObj.optJSONArray("item_aliases"))
        for (arr in aliasArrays) {
            for (i in 0 until arr.length()) {
                val alias = arr.optString(i, "").trim()
                if (alias.isNotEmpty() && !alreadyCovered(alias)) {
                    recordPart(alias, alias)
                    break // Keep first non-redundant alias concise
                }
            }
        }

        // Tags (formatted as #tag to stay interoperable with PhonAcq's own #tag parser)
        val tagArrays = listOfNotNull(itemObj.optJSONArray("tags"), itemObj.optJSONArray("item_tags"))
        val tagsToAppend = LinkedHashSet<String>()
        for (arr in tagArrays) {
            for (i in 0 until arr.length()) {
                val tag = arr.optString(i, "").trim()
                if (tag.isEmpty()) continue
                if (tag == groupName || tag == "默认组" || tag == S.common.defaultGroupFallback) continue
                if (!alreadyCovered(tag)) {
                    tagsToAppend.add("#$tag")
                }
            }
        }

        val baseStr = parts.joinToString(" · ")
        val tagsStr = tagsToAppend.take(6).joinToString(" ")
        return when {
            baseStr.isNotEmpty() && tagsStr.isNotEmpty() -> "$baseStr $tagsStr"
            baseStr.isNotEmpty() -> baseStr
            else -> tagsStr
        }.trim()
    }

    private fun findItemLevelWordlistTitle(root: JSONObject, activeSpeakerId: String): String {
        val speakersObj = root.optJSONObject("speakers") ?: return ""
        val keys = orderedSpeakerKeys(speakersObj, activeSpeakerId)
        var fallbackGeneric = ""
        for (spkKey in keys) {
            val spkObj = speakersObj.optJSONObject(spkKey) ?: continue
            val itemsObj = spkObj.optJSONObject("items") ?: continue
            val itemKeys = itemsObj.keys()
            while (itemKeys.hasNext()) {
                val itemObj = itemsObj.optJSONObject(itemKeys.next()) ?: continue
                val t = optCleanString(itemObj, "wordlist_title")
                if (t.isNotBlank()) {
                    if (t.lowercase(Locale.ROOT) !in GENERIC_IMPORT_TITLES) {
                        return t
                    } else if (fallbackGeneric.isEmpty()) {
                        fallbackGeneric = t
                    }
                }
            }
        }
        return fallbackGeneric
    }

    private fun orderedSpeakerKeys(speakersObj: JSONObject, activeSpeakerId: String): List<String> {
        val keys = ArrayList<String>()
        val it = speakersObj.keys()
        while (it.hasNext()) {
            keys.add(it.next())
        }
        if (activeSpeakerId.isNotBlank() && activeSpeakerId in keys) {
            keys.remove(activeSpeakerId)
            keys.add(0, activeSpeakerId)
        }
        return keys
    }

    private fun optCleanString(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val raw = obj.opt(k)
            if (raw is String) {
                val trimmed = raw.trim()
                if (trimmed.isNotEmpty()) return trimmed
            }
        }
        return ""
    }

    private fun scalarToCleanString(raw: Any?): String {
        return when (raw) {
            null, JSONObject.NULL -> ""
            is String -> raw.trim()
            is Number, is Boolean -> raw.toString().trim()
            else -> ""
        }
    }
}
