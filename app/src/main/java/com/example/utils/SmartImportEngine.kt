package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.audio.SpeechQualityEvaluator
import com.example.data.ParsedWord
import com.example.data.Project
import com.example.data.ProjectRepository
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.SSmartImport
import com.example.i18n.withArgs
import com.example.viewmodel.parseImportTextToParsedWordsImpl
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class ImportItemKind {
    PROJECT_ARCHIVE,
    WORDLIST_DOC,
    AUDIO_CLIP,
    PROMPT_IMAGE,
    UNSUPPORTED
}

enum class ImportTargetMode {
    CREATE_NEW_PROJECT,
    MERGE_EXISTING_PROJECT
}

enum class MergeAudioPolicy {
    SAVE_AS_NEW_TAKE,
    ONLY_FILL_UNRECORDED
}

enum class PairingMatchKind {
    EXACT_INDEX_AND_WORD,
    WORD_TEXT,
    INDEX_ONLY,
    SEQUENTIAL_ORDER,
    AUTO_NEW_WORD,
    MANUAL_OVERRIDE,
    UNASSIGNED
}

data class ArchiveQuickInspect(
    val archiveProjectName: String,
    val estimatedWordCount: Int,
    val estimatedAudioCount: Int,
    val estimatedSpeakerCount: Int = 0,
    val previewWords: List<ParsedWord> = emptyList(),
    val parseErrorHint: String? = null
)

data class StagedImportItem(
    val id: String,
    val dedupKey: String,
    val displayName: String,
    val relativeFolder: String,
    val rootOriginName: String,
    val sizeBytes: Long,
    val extensionLower: String,
    val kind: ImportItemKind,
    val localTempFile: File?,
    val parsedWords: List<ParsedWord> = emptyList(),
    val wordlistSummary: String = "",
    val archiveInspect: ArchiveQuickInspect? = null,
    val estimatedDurationMs: Long = 0L,
    val needsAudioTranscode: Boolean = false,
    val errorHint: String? = null
)

data class CandidateTargetWord(
    val key: String,
    val existingWordId: Long?,
    val displayIndex: Int,
    val text: String,
    val remark: String,
    val groupName: String,
    val stagedImageFile: File? = null,
    val existingImagePath: String = "",
    val isAutoCreatedFromAudio: Boolean = false,
    val isAutoCreatedFromImage: Boolean = false
)

data class AudioWordPairingRow(
    val audioItem: StagedImportItem,
    val parsedIndexHint: Int?,
    val cleanedWordStem: String,
    val inferredSpeakerName: String?,
    val resolvedSpeakerName: String,
    val takeSequenceInBatch: Int,
    val totalTakesForTarget: Int,
    val targetWordKey: String?,
    val targetWord: CandidateTargetWord?,
    val matchKind: PairingMatchKind
)

data class SmartImportPlanConfig(
    val targetMode: ImportTargetMode = ImportTargetMode.CREATE_NEW_PROJECT,
    val customNewProjectName: String = "",
    val mergeTargetProjectId: Long? = null,
    val defaultSpeakerName: String = "",
    val autoDetectSubfolderSpeakers: Boolean = true,
    val mergeAudioPolicy: MergeAudioPolicy = MergeAudioPolicy.SAVE_AS_NEW_TAKE,
    val autoCreateWordsForUnmatchedAudio: Boolean = true,
    val pairBySequentialOrder: Boolean = false,
    val manualWordOverrides: Map<String, String> = emptyMap()
)

data class SmartImportPlan(
    val stagedItems: List<StagedImportItem>,
    val archiveItems: List<StagedImportItem>,
    val wordlistItems: List<StagedImportItem>,
    val audioItems: List<StagedImportItem>,
    val imageItems: List<StagedImportItem>,
    val unsupportedItems: List<StagedImportItem>,
    val suggestedProjectName: String,
    val candidateWords: List<CandidateTargetWord>,
    val matchedImageCount: Int,
    val audioPairings: List<AudioWordPairingRow>,
    val detectedSpeakerNames: List<String>,
    val transcodeRequiredCount: Int,
    val readyAudioCount: Int,
    val autoCreatedWordCount: Int,
    val unassignedAudioCount: Int
)

data class SmartImportProgress(
    val fraction: Float,
    val stageMessage: String
)

data class SmartImportResult(
    val success: Boolean,
    val primaryProjectId: Long?,
    val primarySpeakerId: Long?,
    val importedArchiveCount: Int,
    val createdOrAddedWordCount: Int,
    val importedAudioCount: Int,
    val skippedCount: Int,
    val summaryMessage: String
)

/**
 * Core cross-platform inspection, pairing, and corpus ingestion engine for the Smart Import Hub.
 */
object SmartImportEngine {

    const val OVERRIDE_AUTO_CREATE = "__AUTO_CREATE__"
    const val OVERRIDE_SKIP = "__SKIP__"

    private val WORDLIST_EXTENSIONS = setOf("xlsx", "csv", "tsv", "txt", "docx", "json", "md", "ptwl")
    private val stagedItemSeqCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private val AUDIO_EXTENSIONS = setOf("wav", "mp3", "m4a", "aac", "flac", "ogg", "opus", "aiff", "aif", "wma")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp")
    private val GENERIC_AUDIO_STEMS = setOf(
        "audio", "track", "rec", "record", "recording", "voice", "clip", "sound", "take", "sample",
        "录音", "音频", "语音", "新录音", "未命名"
    )

    private val GROUP_BRACKET_REGEX = Regex("""^(?:【([^】]+)】|\[([^\]]+)])\s*[-_.\s]*""")
    private val TAKE_SUFFIX_REGEX = Regex(
        """(?:[-_.\s]+(?:take|tk|t|v|ver|第)?\s*(\d{1,2})\s*(?:遍|次)?|\((\d{1,2})\)|（(\d{1,2})）|[-_.\s]+(重录|补录))$""",
        RegexOption.IGNORE_CASE
    )
    private val LEADING_INDEX_TOKEN_REGEX = Regex("""^#?0*(\d{1,5})(?:[-_.、\s:：)）\]]+|$)""")
    private val PURE_DIGITS_REGEX = Regex("""^\d{1,6}$""")
    private val PAREN_REMARK_REGEX = Regex("""^([^\s(（]+)\s*[(（]([^)）]+)[)）]$""")
    private val SPEAKER_FOLDER_PREFIX_REGEX = Regex(
        """^(?:发音人|被试|合作人|speaker|spk|informant)[-_.\s:：]*(\S.*)$""",
        RegexOption.IGNORE_CASE
    )

    fun createSessionStagingDir(context: Context): File {
        return File(context.cacheDir, "smart_import_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    fun cleanupSessionStagingDir(sessionDir: File?) {
        if (sessionDir != null && sessionDir.exists()) {
            runCatching { sessionDir.deleteRecursively() }
        }
    }

    /**
     * Stages newly picked or dropped [uris] (which may include individual files, entire folders,
     * project archives, or plain asset `.zip` bundles) and merges them with [existingStaged] without duplicates.
     */
    suspend fun stageUrisIntoPool(
        context: Context,
        sessionDir: File,
        existingStaged: List<StagedImportItem>,
        uris: List<Uri>
    ): List<StagedImportItem> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext existingStaged

        val merged = ArrayList<StagedImportItem>(existingStaged.size + uris.size * 2)
        val seenKeys = HashSet<String>(existingStaged.size * 2)
        for (item in existingStaged) {
            merged.add(item)
            seenKeys.add(item.dedupKey)
        }

        // Monotonically increasing sequence IDs prevent LazyColumn key collisions when users remove and re-add files
        val existingMaxSeq = existingStaged.mapNotNull { item ->
            Regex("""^item_(\d+)""").find(item.id)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }.maxOrNull() ?: 0L
        stagedItemSeqCounter.updateAndGet { cur -> maxOf(cur, existingMaxSeq) }
        fun nextSeqInt(): Int = stagedItemSeqCounter.incrementAndGet().toInt()

        for (uri in uris) {
            if (SmartImportPlatformBridge.isDirectoryUri(context, uri)) {
                val scannedDocs = SmartImportPlatformBridge.scanDirectoryTree(context, uri)
                for (doc in scannedDocs) {
                    val dedupKey = "tree:${doc.rootFolderName}/${doc.relativeFolder}/${doc.displayName}:${doc.sizeBytes}"
                    if (!seenKeys.add(dedupKey)) continue
                    val inspected = stageAndClassifySingleSource(
                        context = context,
                        sessionDir = sessionDir,
                        seqNumber = nextSeqInt(),
                        dedupKey = dedupKey,
                        sourceUri = doc.uri,
                        displayName = doc.displayName,
                        relativeFolder = doc.relativeFolder,
                        rootOriginName = doc.rootFolderName,
                        declaredSize = doc.sizeBytes
                    )
                    for (sub in inspected) {
                        if (seenKeys.add(sub.dedupKey) || sub.dedupKey == dedupKey) {
                            merged.add(sub)
                        }
                    }
                }
            } else {
                val (displayName, sizeBytes) = SmartImportPlatformBridge.queryDisplayNameAndSize(context, uri)
                if (displayName.isBlank() || displayName.startsWith(".")) continue
                val dedupKey = "uri:${uri}:$displayName:$sizeBytes"
                if (!seenKeys.add(dedupKey)) continue
                val inspected = stageAndClassifySingleSource(
                    context = context,
                    sessionDir = sessionDir,
                    seqNumber = nextSeqInt(),
                    dedupKey = dedupKey,
                    sourceUri = uri,
                    displayName = displayName,
                    relativeFolder = "",
                    rootOriginName = "",
                    declaredSize = sizeBytes
                )
                for (sub in inspected) {
                    if (seenKeys.add(sub.dedupKey) || sub.dedupKey == dedupKey) {
                        merged.add(sub)
                    }
                }
            }
        }

        merged
    }

    /**
     * Stages bytes directly from a local [File] list (convenient for JVM unit tests and direct file imports).
     */
    suspend fun stageLocalFilesIntoPool(
        context: Context,
        sessionDir: File,
        existingStaged: List<StagedImportItem>,
        files: List<File>
    ): List<StagedImportItem> {
        return stageUrisIntoPool(
            context = context,
            sessionDir = sessionDir,
            existingStaged = existingStaged,
            uris = files.map { Uri.fromFile(it) }
        )
    }

    private fun stageAndClassifySingleSource(
        context: Context,
        sessionDir: File,
        seqNumber: Int,
        dedupKey: String,
        sourceUri: Uri,
        displayName: String,
        relativeFolder: String,
        rootOriginName: String,
        declaredSize: Long
    ): List<StagedImportItem> {
        val extLower = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val safeBase = displayName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val tempFile = File(sessionDir, "stg_${seqNumber}_$safeBase")

        val copied = SmartImportPlatformBridge.copyUriToTempFile(context, sourceUri, tempFile)
        if (!copied || !tempFile.exists() || tempFile.length() <= 0L) {
            return listOf(
                StagedImportItem(
                    id = "item_$seqNumber",
                    dedupKey = dedupKey,
                    displayName = displayName,
                    relativeFolder = relativeFolder,
                    rootOriginName = rootOriginName,
                    sizeBytes = declaredSize,
                    extensionLower = extLower,
                    kind = ImportItemKind.UNSUPPORTED,
                    localTempFile = null,
                    errorHint = "无法读取文件"
                )
            )
        }

        return inspectLocalTempFile(
            sessionDir = sessionDir,
            seqPrefix = "item_$seqNumber",
            dedupKey = dedupKey,
            tempFile = tempFile,
            displayName = displayName,
            relativeFolder = relativeFolder,
            rootOriginName = rootOriginName
        )
    }

    private fun inspectLocalTempFile(
        sessionDir: File,
        seqPrefix: String,
        dedupKey: String,
        tempFile: File,
        displayName: String,
        relativeFolder: String,
        rootOriginName: String
    ): List<StagedImportItem> {
        val extLower = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val actualSize = tempFile.length()
        val magic = ByteArray(16)
        val magicLen = runCatching {
            FileInputStream(tempFile).use { it.read(magic) }
        }.getOrDefault(0)

        val isZipMagic = magicLen >= 4 &&
            magic[0] == 'P'.code.toByte() &&
            magic[1] == 'K'.code.toByte() &&
            ((magic[2] == 3.toByte() && magic[3] == 4.toByte()) ||
                (magic[2] == 5.toByte() && magic[3] == 6.toByte()) ||
                (magic[2] == 7.toByte() && magic[3] == 8.toByte()))

        // 1. Handle .teproj or .zip containers (distinguishing OOXML .xlsx/.docx, Project Archive, and Plain Asset Bundle)
        if (extLower == "teproj" && !isZipMagic) {
            // Handle non-ZIP .teproj (e.g., plain JSON wordlist or corrupt file) cleanly instead of masking as 0-word archive
            val rawBytes = runCatching { tempFile.readBytes() }.getOrNull()
            val plainDoc = rawBytes?.let { TeprojParser.parseProjectJson(TeprojParser.decodeTextClean(it)) }
            if (plainDoc != null && plainDoc.words.isNotEmpty()) {
                return listOf(
                    StagedImportItem(
                        id = seqPrefix,
                        dedupKey = dedupKey,
                        displayName = displayName,
                        relativeFolder = relativeFolder,
                        rootOriginName = rootOriginName,
                        sizeBytes = actualSize,
                        extensionLower = "teproj",
                        kind = ImportItemKind.WORDLIST_DOC,
                        localTempFile = tempFile,
                        parsedWords = plainDoc.words,
                        wordlistSummary = "PhonTracer JSON"
                    )
                )
            }
            return listOf(
                StagedImportItem(
                    id = seqPrefix,
                    dedupKey = dedupKey,
                    displayName = displayName,
                    relativeFolder = relativeFolder,
                    rootOriginName = rootOriginName,
                    sizeBytes = actualSize,
                    extensionLower = "teproj",
                    kind = ImportItemKind.UNSUPPORTED,
                    localTempFile = null.also { runCatching { tempFile.delete() } },
                    errorHint = "该 .teproj 不是有效的 ZIP 工程包"
                )
            )
        }

        if (extLower == "teproj" || extLower == "zip" || (isZipMagic && extLower !in setOf("xlsx", "docx"))) {
            val zipNature = classifyZipContainer(tempFile, displayName, extLower)
            when (zipNature) {
                ZipContainerNature.OOXML_DOCUMENT -> {
                    return listOf(
                        buildWordlistStagedItem(
                            id = seqPrefix,
                            dedupKey = dedupKey,
                            tempFile = tempFile,
                            displayName = displayName,
                            relativeFolder = relativeFolder,
                            rootOriginName = rootOriginName,
                            extLower = extLower
                        )
                    )
                }
                is ZipContainerNature.PROJECT_ARCHIVE -> {
                    return listOf(
                        StagedImportItem(
                            id = seqPrefix,
                            dedupKey = dedupKey,
                            displayName = displayName,
                            relativeFolder = relativeFolder,
                            rootOriginName = rootOriginName,
                            sizeBytes = actualSize,
                            extensionLower = extLower.ifEmpty { "zip" },
                            kind = ImportItemKind.PROJECT_ARCHIVE,
                            localTempFile = tempFile,
                            archiveInspect = zipNature.inspect
                        )
                    )
                }
                ZipContainerNature.PLAIN_ASSET_BUNDLE -> {
                    val unpacked = unpackPlainZipBundle(
                        sessionDir = sessionDir,
                        seqPrefix = seqPrefix,
                        zipFile = tempFile,
                        zipDisplayName = displayName.substringBeforeLast('.').ifBlank { displayName }
                    )
                    if (unpacked.isNotEmpty()) {
                        runCatching { tempFile.delete() }
                        return unpacked
                    }
                }
            }
        }

        // 2. Sniff Audio by magic bytes or extension
        val detectedAudioExt = sniffAudioFormat(magic, magicLen, extLower)
        if (detectedAudioExt != null) {
            val wavInfo = if (detectedAudioExt == "wav") AudioWavNormalizer.inspectWavHeader(tempFile) else null
            val needsTranscode = wavInfo == null || !wavInfo.isCanonicalMono16Pcm
            return listOf(
                StagedImportItem(
                    id = seqPrefix,
                    dedupKey = dedupKey,
                    displayName = displayName,
                    relativeFolder = relativeFolder,
                    rootOriginName = rootOriginName,
                    sizeBytes = actualSize,
                    extensionLower = detectedAudioExt,
                    kind = ImportItemKind.AUDIO_CLIP,
                    localTempFile = tempFile,
                    estimatedDurationMs = wavInfo?.durationMs ?: 0L,
                    needsAudioTranscode = needsTranscode
                )
            )
        }

        // 3. Sniff Image by magic bytes or extension
        if (sniffImageFormat(magic, magicLen, extLower)) {
            return listOf(
                StagedImportItem(
                    id = seqPrefix,
                    dedupKey = dedupKey,
                    displayName = displayName,
                    relativeFolder = relativeFolder,
                    rootOriginName = rootOriginName,
                    sizeBytes = actualSize,
                    extensionLower = extLower.ifEmpty { "jpg" },
                    kind = ImportItemKind.PROMPT_IMAGE,
                    localTempFile = tempFile
                )
            )
        }

        // 4. Wordlist document (.xlsx, .docx, .csv, .tsv, .txt, .json)
        if (extLower in WORDLIST_EXTENSIONS || extLower.isEmpty()) {
            val wlItem = buildWordlistStagedItem(
                id = seqPrefix,
                dedupKey = dedupKey,
                tempFile = tempFile,
                displayName = displayName,
                relativeFolder = relativeFolder,
                rootOriginName = rootOriginName,
                extLower = extLower
            )
            if (wlItem.kind == ImportItemKind.WORDLIST_DOC) {
                return listOf(wlItem)
            }
        }

        return listOf(
            StagedImportItem(
                id = seqPrefix,
                dedupKey = dedupKey,
                displayName = displayName,
                relativeFolder = relativeFolder,
                rootOriginName = rootOriginName,
                sizeBytes = actualSize,
                extensionLower = extLower,
                kind = ImportItemKind.UNSUPPORTED,
                localTempFile = null.also { runCatching { tempFile.delete() } },
                errorHint = "暂不支持此格式"
            )
        )
    }

    private fun buildWordlistStagedItem(
        id: String,
        dedupKey: String,
        tempFile: File,
        displayName: String,
        relativeFolder: String,
        rootOriginName: String,
        extLower: String
    ): StagedImportItem {
        val maxBytes = 16 * 1024 * 1024L
        if (tempFile.length() > maxBytes) {
            return StagedImportItem(
                id = id,
                dedupKey = dedupKey,
                displayName = displayName,
                relativeFolder = relativeFolder,
                rootOriginName = rootOriginName,
                sizeBytes = tempFile.length(),
                extensionLower = extLower,
                kind = ImportItemKind.UNSUPPORTED,
                localTempFile = null,
                errorHint = "文档体积过大"
            )
        }
        val bytes = runCatching { tempFile.readBytes() }.getOrNull() ?: ByteArray(0)
        val res = SpreadsheetWordImporter.importFromBytes(
            bytes = bytes,
            fileName = displayName,
            fallbackTextParser = ::parseImportTextToParsedWordsImpl
        )
        return if (res.words.isNotEmpty()) {
            StagedImportItem(
                id = id,
                dedupKey = dedupKey,
                displayName = displayName,
                relativeFolder = relativeFolder,
                rootOriginName = rootOriginName,
                sizeBytes = tempFile.length(),
                extensionLower = extLower.ifEmpty { "txt" },
                kind = ImportItemKind.WORDLIST_DOC,
                localTempFile = tempFile,
                parsedWords = res.words,
                wordlistSummary = res.sourceSummary
            )
        } else {
            StagedImportItem(
                id = id,
                dedupKey = dedupKey,
                displayName = displayName,
                relativeFolder = relativeFolder,
                rootOriginName = rootOriginName,
                sizeBytes = tempFile.length(),
                extensionLower = extLower,
                kind = ImportItemKind.UNSUPPORTED,
                localTempFile = null.also { runCatching { tempFile.delete() } },
                errorHint = res.errorMessage ?: "未提取到有效词条"
            )
        }
    }

    private sealed interface ZipContainerNature {
        object OOXML_DOCUMENT : ZipContainerNature
        data class PROJECT_ARCHIVE(val inspect: ArchiveQuickInspect) : ZipContainerNature
        object PLAIN_ASSET_BUNDLE : ZipContainerNature
    }

    private fun classifyZipContainer(file: File, displayName: String, extLower: String): ZipContainerNature {
        val fallbackName = displayName.substringBeforeLast('.').trim().ifBlank { displayName }
        var hasContentTypesXml = false
        var hasXlOrWordDir = false
        var hasProjectMarker = extLower == "teproj"
        var expProjectName = ""
        var expWordCount = 0
        var expDepth = Int.MAX_VALUE
        var teprojJsonStr: String? = null
        var teprojDepth = Int.MAX_VALUE
        var wordListText: String? = null
        var wordListDepth = Int.MAX_VALUE
        var simpleSummaryXlsxBytes: ByteArray? = null
        var simpleSummaryDepth = Int.MAX_VALUE
        var wavCount = 0
        var zipReadError: String? = null

        fun resetScan() {
            hasContentTypesXml = false
            hasXlOrWordDir = false
            hasProjectMarker = extLower == "teproj"
            expProjectName = ""
            expWordCount = 0
            expDepth = Int.MAX_VALUE
            teprojJsonStr = null
            teprojDepth = Int.MAX_VALUE
            wordListText = null
            wordListDepth = Int.MAX_VALUE
            simpleSummaryXlsxBytes = null
            simpleSummaryDepth = Int.MAX_VALUE
            wavCount = 0
        }

        try {
            TeprojParser.forEachZipEntryResilient(
                zipFile = file,
                onBeforeRetry = { resetScan() }
            ) { entry, zis ->
                if (entry.isDirectory) return@forEachZipEntryResilient
                val norm = TeprojParser.normalizeArchivePath(entry.name)
                if (norm.isEmpty()) return@forEachZipEntryResilient
                val base = norm.substringAfterLast('/')
                val isHidden = base.startsWith("._") || norm.startsWith("__MACOSX/")
                if (isHidden) return@forEachZipEntryResilient

                val depth = norm.count { it == '/' }
                when {
                    norm.equals("[Content_Types].xml", ignoreCase = true) -> {
                        hasContentTypesXml = true
                    }
                    norm.startsWith("xl/", ignoreCase = true) || norm.startsWith("word/", ignoreCase = true) -> {
                        hasXlOrWordDir = true
                    }
                    base.equals("experiment_profile.json", ignoreCase = true) -> {
                        hasProjectMarker = true
                        if (depth < expDepth) {
                            expDepth = depth
                            val txt = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                            runCatching {
                                val obj = JSONObject(txt)
                                val pn = obj.optString("project_name", obj.optString("name", "")).trim()
                                if (pn.isNotEmpty()) expProjectName = pn
                                val wc = obj.optInt("total_words", 0)
                                if (wc > 0) expWordCount = wc
                            }
                        }
                    }
                    base.equals("project.json", ignoreCase = true) -> {
                        hasProjectMarker = true
                        if (depth < teprojDepth || teprojJsonStr == null) {
                            teprojDepth = depth
                            teprojJsonStr = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                        }
                    }
                    base.equals("word_list.txt", ignoreCase = true) -> {
                        hasProjectMarker = true
                        if (depth < wordListDepth || wordListText == null) {
                            wordListDepth = depth
                            wordListText = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                        }
                    }
                    base.equals("录音汇总表.xlsx", ignoreCase = true) -> {
                        hasProjectMarker = true
                        if (depth < simpleSummaryDepth || simpleSummaryXlsxBytes == null) {
                            simpleSummaryDepth = depth
                            simpleSummaryXlsxBytes = TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                        }
                    }
                    base.equals("录音汇总表.csv", ignoreCase = true) ||
                        base.equals("speaker_profile.json", ignoreCase = true) -> {
                        hasProjectMarker = true
                    }
                    base.endsWith(".wav", ignoreCase = true) && !norm.contains("/takes/", ignoreCase = true) -> {
                        wavCount++
                    }
                }
            }
        } catch (e: Exception) {
            zipReadError = e.localizedMessage ?: e.javaClass.simpleName
        }

        if (hasContentTypesXml && hasXlOrWordDir) {
            return ZipContainerNature.OOXML_DOCUMENT
        }
        if (!hasProjectMarker) {
            return ZipContainerNature.PLAIN_ASSET_BUNDLE
        }

        val teprojDoc = teprojJsonStr?.let { TeprojParser.parseProjectJson(it) }
        val wordListPreview: List<ParsedWord> = when {
            !wordListText.isNullOrBlank() -> {
                parseImportTextToParsedWordsImpl(wordListText!!)
            }
            teprojDoc != null && teprojDoc.words.isNotEmpty() -> {
                teprojDoc.words
            }
            simpleSummaryXlsxBytes != null -> {
                val sheets = runCatching { XlsxSpreadsheetWriter.readAllSheets(simpleSummaryXlsxBytes!!) }.getOrDefault(emptyMap())
                val wlRows = sheets.entries.firstOrNull { it.key.contains("字表") || it.key.contains("词表") }?.value
                if (wlRows != null && wlRows.size >= 2) {
                    wlRows.drop(1).mapNotNull { row ->
                        val t = row.getOrNull(2)?.trim().orEmpty()
                        if (t.isEmpty()) null else ParsedWord(
                            text = t,
                            remark = row.getOrNull(3)?.trim().orEmpty(),
                            groupName = TeprojParser.normalizeGroupName(row.getOrNull(1).orEmpty())
                        )
                    }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }

        val resolvedName = expProjectName
            .ifBlank { teprojDoc?.title.orEmpty() }
            .ifBlank { fallbackName }
        val resolvedWordCount = maxOf(
            wordListPreview.size,
            teprojDoc?.words?.size ?: 0,
            expWordCount
        )
        val resolvedAudioCount = when {
            teprojDoc != null && teprojDoc.totalAudioCandidates > 0 -> teprojDoc.totalAudioCandidates
            else -> wavCount
        }
        val resolvedSpeakerCount = teprojDoc?.speakers?.size ?: 0

        return ZipContainerNature.PROJECT_ARCHIVE(
            ArchiveQuickInspect(
                archiveProjectName = resolvedName,
                estimatedWordCount = resolvedWordCount,
                estimatedAudioCount = resolvedAudioCount,
                estimatedSpeakerCount = resolvedSpeakerCount,
                previewWords = wordListPreview,
                parseErrorHint = zipReadError
            )
        )
    }

    private fun unpackPlainZipBundle(
        sessionDir: File,
        seqPrefix: String,
        zipFile: File,
        zipDisplayName: String
    ): List<StagedImportItem> {
        val results = ArrayList<StagedImportItem>()
        val createdTemps = ArrayList<File>()
        var subIdx = 1
        var totalBytes = 0L

        runCatching {
            TeprojParser.forEachZipEntryResilient(
                zipFile = zipFile,
                onBeforeRetry = {
                    results.clear()
                    createdTemps.forEach { runCatching { it.delete() } }
                    createdTemps.clear()
                    subIdx = 1
                    totalBytes = 0L
                }
            ) { entry, zis ->
                if (entry.isDirectory || subIdx > 2000) return@forEachZipEntryResilient
                val norm = TeprojParser.normalizeArchivePath(entry.name)
                if (norm.isEmpty()) return@forEachZipEntryResilient
                val base = norm.substringAfterLast('/')
                val relFolder = if (norm.contains('/')) norm.substringBeforeLast('/') else ""
                val isHidden = base.startsWith(".") || base.startsWith("._") || norm.startsWith("__MACOSX/")
                if (!isHidden && base.isNotBlank()) {
                    val safeBase = base.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").take(120)
                    val outTemp = File(sessionDir, "${seqPrefix}_u${subIdx}_$safeBase")
                    val remaining = (TeprojParser.MAX_TOTAL_EXTRACT_BYTES - totalBytes).coerceAtLeast(0L)
                    val written = TeprojParser.copyEntryToFileBounded(
                        input = zis,
                        destFile = outTemp,
                        maxBytes = minOf(TeprojParser.MAX_SINGLE_MEDIA_ENTRY_BYTES, remaining)
                    )
                    totalBytes += written
                    if (outTemp.length() > 0L) {
                        createdTemps.add(outTemp)
                        val subItems = inspectLocalTempFile(
                            sessionDir = sessionDir,
                            seqPrefix = "${seqPrefix}_u$subIdx",
                            dedupKey = "zip:$zipDisplayName/$norm:${outTemp.length()}",
                            tempFile = outTemp,
                            displayName = base,
                            relativeFolder = relFolder,
                            rootOriginName = zipDisplayName
                        )
                        results.addAll(subItems.filter { it.kind != ImportItemKind.UNSUPPORTED })
                        subIdx++
                    } else {
                        runCatching { outTemp.delete() }
                    }
                }
            }
        }
        return results
    }

    private fun sniffAudioFormat(magic: ByteArray, len: Int, extLower: String): String? {
        if (len >= 12) {
            val h0 = String(magic, 0, 4, Charsets.US_ASCII)
            val h8 = String(magic, 8, 4, Charsets.US_ASCII)
            if (h0 == "RIFF" && h8 == "WAVE") return "wav"
            if (h0 == "FORM" && (h8 == "AIFF" || h8 == "AIFC")) return "aiff"
            if (h0 == "fLaC") return "flac"
            if (h0 == "OggS") return "ogg"
            if (String(magic, 4, 4, Charsets.US_ASCII) == "ftyp") {
                val brand = String(magic, 8, 4, Charsets.US_ASCII).trim().lowercase(Locale.ROOT)
                if (brand in setOf("m4a", "mp42", "isom", "3gp4")) {
                    if (extLower in setOf("m4a", "aac", "mp4", "")) return "m4a"
                }
            }
        }
        if (len >= 3) {
            if (magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte()) {
                return "mp3"
            }
            val b0 = magic[0].toInt() and 0xFF
            val b1 = magic[1].toInt() and 0xE0
            if (b0 == 0xFF && b1 == 0xE0 && extLower in setOf("mp3", "aac", "")) {
                return extLower.ifEmpty { "mp3" }
            }
        }
        return if (extLower in AUDIO_EXTENSIONS) extLower else null
    }

    private fun sniffImageFormat(magic: ByteArray, len: Int, extLower: String): Boolean {
        if (len >= 8 &&
            (magic[0].toInt() and 0xFF) == 0x89 &&
            magic[1] == 'P'.code.toByte() &&
            magic[2] == 'N'.code.toByte() &&
            magic[3] == 'G'.code.toByte()
        ) return true
        if (len >= 3 &&
            (magic[0].toInt() and 0xFF) == 0xFF &&
            (magic[1].toInt() and 0xFF) == 0xD8 &&
            (magic[2].toInt() and 0xFF) == 0xFF
        ) return true
        if (len >= 12 &&
            String(magic, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(magic, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) return true
        return extLower in IMAGE_EXTENSIONS
    }

    private data class ParsedAudioTokens(
        val speakerHint: String?,
        val indexHint: Int?,
        val cleanedWordStem: String,
        val remarkHint: String,
        val groupHint: String,
        val takeOrder: Int
    )

    private fun parseAudioTokens(
        item: StagedImportItem,
        knownSpeakerNames: Set<String>
    ): ParsedAudioTokens {
        var speakerHint: String? = null
        var groupFromFolder = ""

        // 1. Check relativeFolder for speaker or group
        if (item.relativeFolder.isNotBlank()) {
            val segments = item.relativeFolder.split('/').map { it.trim() }.filter { it.isNotEmpty() }
            for (seg in segments) {
                val spkPrefix = SPEAKER_FOLDER_PREFIX_REGEX.find(seg)
                if (spkPrefix != null) {
                    speakerHint = spkPrefix.groupValues[1].trim()
                } else if (knownSpeakerNames.any { it.equals(seg, ignoreCase = true) }) {
                    speakerHint = knownSpeakerNames.first { it.equals(seg, ignoreCase = true) }
                } else if (seg.lowercase(Locale.ROOT) !in setOf("audio", "wav", "wavs", "recordings", "录音", "音频", "media")) {
                    groupFromFolder = seg
                }
            }
        }

        // 2. Tokenize filename stem
        var working = item.displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .trim()

        var takeOrder = 1
        val takeMatch = TAKE_SUFFIX_REGEX.find(working)
        if (takeMatch != null) {
            val numStr = takeMatch.groupValues[1]
                .ifEmpty { takeMatch.groupValues[2] }
                .ifEmpty { takeMatch.groupValues[3] }
            val parsedTake = numStr.toIntOrNull()
            // Only strip trailing _2 / (2) when something meaningful precedes it (avoid turning pure "02.wav" into empty!)
            val prefixBeforeTake = working.substring(0, takeMatch.range.first).trim()
            if (prefixBeforeTake.isNotEmpty() && !PURE_DIGITS_REGEX.matches(prefixBeforeTake)) {
                takeOrder = parsedTake?.coerceAtLeast(1) ?: 2
                working = prefixBeforeTake
            }
        }

        // 3. Check leading speaker prefix in filename (e.g., `张三_001_坡` or `王老师-坡`)
        if (speakerHint == null) {
            for (spk in knownSpeakerNames.sortedByDescending { it.length }) {
                if (spk.isNotBlank() && working.length > spk.length + 1 &&
                    working.startsWith(spk, ignoreCase = true) &&
                    working[spk.length] in charArrayOf('_', '-', ' ', '.')
                ) {
                    speakerHint = spk
                    working = working.substring(spk.length + 1).trim()
                    break
                }
            }
        }

        // 4. Extract bracketed group `【声母】` or `[天文]`
        var groupHint = groupFromFolder
        val grpMatch = GROUP_BRACKET_REGEX.find(working)
        if (grpMatch != null) {
            val extractedGrp = grpMatch.groupValues[1].ifBlank { grpMatch.groupValues[2] }.trim()
            if (extractedGrp.isNotEmpty()) groupHint = extractedGrp
            working = working.removeRange(grpMatch.range).trim()
        }

        // 5. Extract leading index (`001_坡`, `01.坡`, `001`)
        var indexHint: Int? = null
        val idxMatch = LEADING_INDEX_TOKEN_REGEX.find(working)
        if (idxMatch != null) {
            indexHint = idxMatch.groupValues[1].toIntOrNull()
            working = working.removeRange(idxMatch.range).trim()
        } else {
            // Check `rec_005` / `audio_12`
            val genericIdx = Regex("""^([A-Za-z\u4e00-\u9fa5]+)[-_.\s]*0*(\d{1,5})$""").matchEntire(working)
            if (genericIdx != null && genericIdx.groupValues[1].lowercase(Locale.ROOT) in GENERIC_AUDIO_STEMS) {
                indexHint = genericIdx.groupValues[2].toIntOrNull()
                working = ""
            }
        }

        // Check group again if `001_【天文】日头`
        val grpAfterIdx = GROUP_BRACKET_REGEX.find(working)
        if (grpAfterIdx != null) {
            val extractedGrp = grpAfterIdx.groupValues[1].ifBlank { grpAfterIdx.groupValues[2] }.trim()
            if (extractedGrp.isNotEmpty()) groupHint = extractedGrp
            working = working.removeRange(grpAfterIdx.range).trim()
        }

        // Check if remaining stem starts with a known speaker after index `001_张三_坡`
        if (speakerHint == null) {
            for (spk in knownSpeakerNames.sortedByDescending { it.length }) {
                if (spk.isNotBlank() && working.length > spk.length + 1 &&
                    working.startsWith(spk, ignoreCase = true) &&
                    working[spk.length] in charArrayOf('_', '-', ' ')
                ) {
                    speakerHint = spk
                    working = working.substring(spk.length + 1).trim()
                    break
                }
            }
        }

        // 6. Extract parenthetical or underscore remark (`日头(太阳)` or `坡_帮母`)
        var remarkHint = ""
        val parenMatch = PAREN_REMARK_REGEX.matchEntire(working)
        if (parenMatch != null) {
            working = parenMatch.groupValues[1].trim()
            remarkHint = parenMatch.groupValues[2].trim()
        } else if (working.count { it == '_' } == 1) {
            val parts = working.split('_', limit = 2).map { it.trim() }
            if (parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                // If first token is not a speaker name, treat second token as remark unless first token looks like a speaker prefix
                if (speakerHint == null && parts[0].length in 2..4 && indexHint == null && parts[1].length <= 4) {
                    // Leave ambiguous `A_B` intact unless matched against wordlist below
                } else {
                    working = parts[0]
                    remarkHint = parts[1]
                }
            }
        }

        if (working.lowercase(Locale.ROOT) in GENERIC_AUDIO_STEMS) {
            working = ""
        }

        return ParsedAudioTokens(
            speakerHint = speakerHint,
            indexHint = indexHint,
            cleanedWordStem = working,
            remarkHint = remarkHint,
            groupHint = groupHint,
            takeOrder = takeOrder
        )
    }

    /**
     * Pure deterministic planner: combines staged items, existing project words (when merging),
     * speaker roster, and user overrides into a live [SmartImportPlan] preview.
     */
    fun buildImportPlan(
        stagedItems: List<StagedImportItem>,
        existingWordsInMergeTarget: List<Word>,
        knownSpeakers: List<Speaker>,
        config: SmartImportPlanConfig
    ): SmartImportPlan {
        val archiveItems = stagedItems.filter { it.kind == ImportItemKind.PROJECT_ARCHIVE }
        val wordlistItems = stagedItems.filter { it.kind == ImportItemKind.WORDLIST_DOC }
        val audioItems = stagedItems.filter { it.kind == ImportItemKind.AUDIO_CLIP }
        val imageItems = stagedItems.filter { it.kind == ImportItemKind.PROMPT_IMAGE }
        val unsupportedItems = stagedItems.filter { it.kind == ImportItemKind.UNSUPPORTED }

        // 1. Build base candidate wordlist
        val candidateWords = ArrayList<CandidateTargetWord>()
        val seenWordDedup = HashSet<String>()

        if (config.targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT) {
            existingWordsInMergeTarget.sortedBy { it.originalIndex }.forEachIndexed { idx, w ->
                val cKey = "existing:${w.id}"
                candidateWords.add(
                    CandidateTargetWord(
                        key = cKey,
                        existingWordId = w.id,
                        displayIndex = idx + 1,
                        text = w.text.trim(),
                        remark = w.remark.trim(),
                        groupName = w.groupName.trim(),
                        existingImagePath = w.imagePath
                    )
                )
                seenWordDedup.add("${w.groupName.trim()}|${w.text.trim()}|${w.remark.trim()}")
            }
        }

        var nextNewIdx = 0
        for (wl in wordlistItems) {
            val defaultGroupFromRel = wl.relativeFolder.substringAfterLast('/').trim()
            for (pw in wl.parsedWords) {
                val cleanText = pw.text.trim()
                if (cleanText.isEmpty()) continue
                val cleanGroup = pw.groupName.trim().ifEmpty { defaultGroupFromRel }
                val cleanRemark = pw.remark.trim()
                val sig = "$cleanGroup|$cleanText|$cleanRemark"
                if (config.targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT && sig in seenWordDedup) {
                    continue
                }
                if (wordlistItems.size > 1 && !seenWordDedup.add(sig)) {
                    continue
                }
                seenWordDedup.add(sig)
                val key = "new:${nextNewIdx++}"
                candidateWords.add(
                    CandidateTargetWord(
                        key = key,
                        existingWordId = null,
                        displayIndex = candidateWords.size + 1,
                        text = cleanText,
                        remark = cleanRemark,
                        groupName = cleanGroup
                    )
                )
            }
        }

        // 2. Pair staged prompt images onto candidate words (or auto-create picture words if no wordlists/audio)
        var matchedImageCount = 0
        if (imageItems.isNotEmpty()) {
            val wordIdxByText = HashMap<String, Int>()
            candidateWords.forEachIndexed { i, cw ->
                wordIdxByText.putIfAbsent(cw.text, i)
            }
            imageItems.forEachIndexed { imgOrd, imgItem ->
                val parsedImgWord = WordImageManager.parseWordFromImageFileName(imgItem.displayName, imgOrd + 1)
                val leadingNum = LEADING_INDEX_TOKEN_REGEX.find(imgItem.displayName)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val hitIdx = wordIdxByText[parsedImgWord.text]
                    ?: (if (leadingNum != null && leadingNum in 1..candidateWords.size) leadingNum - 1 else null)

                if (hitIdx != null && imgItem.localTempFile != null) {
                    val existingCw = candidateWords[hitIdx]
                    if (existingCw.stagedImageFile == null) {
                        candidateWords[hitIdx] = existingCw.copy(stagedImageFile = imgItem.localTempFile)
                        matchedImageCount++
                    }
                } else if (wordlistItems.isEmpty() && audioItems.isEmpty() &&
                    config.targetMode == ImportTargetMode.CREATE_NEW_PROJECT &&
                    imgItem.localTempFile != null
                ) {
                    val key = "new:${nextNewIdx++}"
                    candidateWords.add(
                        CandidateTargetWord(
                            key = key,
                            existingWordId = null,
                            displayIndex = candidateWords.size + 1,
                            text = parsedImgWord.text,
                            remark = parsedImgWord.remark,
                            groupName = parsedImgWord.groupName,
                            stagedImageFile = imgItem.localTempFile,
                            isAutoCreatedFromImage = true
                        )
                    )
                    matchedImageCount++
                }
            }
        }

        // 3. Pair audio clips against candidateWords (and optionally auto-create words for unmatched audio)
        val knownSpeakerNameSet = knownSpeakers.map { it.name.trim() }.filter { it.isNotEmpty() }.toSet()
        val fallbackSpeakerName = config.defaultSpeakerName.ifBlank {
            knownSpeakers.firstOrNull()?.name ?: "默认发音人"
        }

        val baseWordCountBeforeAutoAudio = candidateWords.size
        val wordsByKey = HashMap<String, CandidateTargetWord>(candidateWords.size * 2)
        val wordsByIndex1 = HashMap<Int, CandidateTargetWord>(candidateWords.size * 2)
        val wordsByGroupAndText = HashMap<Pair<String, String>, List<CandidateTargetWord>>()
        val wordsByText = HashMap<String, MutableList<CandidateTargetWord>>()

        for (cw in candidateWords) {
            wordsByKey[cw.key] = cw
            wordsByIndex1[cw.displayIndex] = cw
            wordsByText.getOrPut(cw.text) { ArrayList() }.add(cw)
        }
        candidateWords.groupBy { it.groupName to it.text }.forEach { (k, v) ->
            wordsByGroupAndText[k] = v
        }

        val autoCreatedByStem = LinkedHashMap<String, CandidateTargetWord>()

        data class RawAudioMatch(
            val item: StagedImportItem,
            val tokens: ParsedAudioTokens,
            val resolvedSpeaker: String,
            val targetKey: String?,
            val matchKind: PairingMatchKind
        )

        val sortedAudioItems = audioItems.sortedWith(
            compareBy<StagedImportItem>(
                { it.relativeFolder.lowercase(Locale.ROOT) },
                { extractNaturalSortNumber(it.displayName) },
                { it.displayName.lowercase(Locale.ROOT) }
            )
        )

        val sequentialPerSpeakerCounter = HashMap<String, Int>()
        val rawMatches = ArrayList<RawAudioMatch>(sortedAudioItems.size)

        for (audio in sortedAudioItems) {
            val tokens = parseAudioTokens(audio, knownSpeakerNameSet)
            val resolvedSpeaker = if (config.autoDetectSubfolderSpeakers && !tokens.speakerHint.isNullOrBlank()) {
                tokens.speakerHint
            } else {
                fallbackSpeakerName
            }

            val manualOverride = config.manualWordOverrides[audio.id]
            if (manualOverride == OVERRIDE_SKIP) {
                rawMatches.add(RawAudioMatch(audio, tokens, resolvedSpeaker, null, PairingMatchKind.UNASSIGNED))
                continue
            }
            if (manualOverride != null && manualOverride != OVERRIDE_AUTO_CREATE && wordsByKey.containsKey(manualOverride)) {
                rawMatches.add(RawAudioMatch(audio, tokens, resolvedSpeaker, manualOverride, PairingMatchKind.MANUAL_OVERRIDE))
                continue
            }

            if (manualOverride != OVERRIDE_AUTO_CREATE && config.pairBySequentialOrder && baseWordCountBeforeAutoAudio > 0) {
                val seqIdx = sequentialPerSpeakerCounter.getOrDefault(resolvedSpeaker, 0) + 1
                sequentialPerSpeakerCounter[resolvedSpeaker] = seqIdx
                val seqWord = wordsByIndex1[seqIdx]
                if (seqWord != null) {
                    rawMatches.add(RawAudioMatch(audio, tokens, resolvedSpeaker, seqWord.key, PairingMatchKind.SEQUENTIAL_ORDER))
                    continue
                }
            }

            var matchedCw: CandidateTargetWord? = null
            var matchKind = PairingMatchKind.UNASSIGNED

            if (manualOverride != OVERRIDE_AUTO_CREATE && baseWordCountBeforeAutoAudio > 0) {
                val stem = tokens.cleanedWordStem
                val idxHint = tokens.indexHint

                // Tier 1: Index + Word Text both match
                if (idxHint != null && stem.isNotEmpty()) {
                    val by1 = wordsByIndex1[idxHint]
                    val by0 = if (idxHint == 0) wordsByIndex1[1] else null
                    val idxCandidate = listOfNotNull(by1, by0).firstOrNull {
                        it.text.equals(stem, ignoreCase = true) ||
                            stem.startsWith(it.text) ||
                            it.text.startsWith(stem)
                    }
                    if (idxCandidate != null) {
                        matchedCw = idxCandidate
                        matchKind = PairingMatchKind.EXACT_INDEX_AND_WORD
                    }
                }

                // Tier 2: Word Text match (preferring group match first, then exact text, then `speaker_word` split)
                if (matchedCw == null && stem.isNotEmpty()) {
                    val gtHits = if (tokens.groupHint.isNotEmpty()) {
                        wordsByGroupAndText[tokens.groupHint to stem]
                    } else null
                    val exactHits = gtHits ?: wordsByText[stem]
                    if (!exactHits.isNullOrEmpty()) {
                        matchedCw = exactHits.first()
                        matchKind = PairingMatchKind.WORD_TEXT
                    } else if (stem.contains('_') || stem.contains('-')) {
                        val tailAfterSep = stem.substringAfterLast('_').substringAfterLast('-').trim()
                        val tailHits = wordsByText[tailAfterSep]
                        if (!tailHits.isNullOrEmpty()) {
                            matchedCw = tailHits.first()
                            matchKind = PairingMatchKind.WORD_TEXT
                        }
                    }
                }

                // Tier 3: Pure Index match (when stem is empty, generic, or auto-create is disabled)
                if (matchedCw == null && idxHint != null) {
                    val allowIndexFallback = stem.isEmpty() || !config.autoCreateWordsForUnmatchedAudio
                    if (allowIndexFallback) {
                        val idxTarget = wordsByIndex1[idxHint]
                        if (idxTarget != null) {
                            matchedCw = idxTarget
                            matchKind = PairingMatchKind.INDEX_ONLY
                        }
                    }
                }
            }

            if (matchedCw != null) {
                rawMatches.add(RawAudioMatch(audio, tokens, resolvedSpeaker, matchedCw.key, matchKind))
            } else if (manualOverride == OVERRIDE_AUTO_CREATE || config.autoCreateWordsForUnmatchedAudio) {
                val fallbackStem = tokens.cleanedWordStem.ifBlank {
                    audio.displayName.substringBeforeLast('.').trim().ifBlank { "未命名词条_${candidateWords.size + 1}" }
                }
                val autoDedupKey = "${tokens.groupHint}|$fallbackStem"
                val autoWord = autoCreatedByStem.getOrPut(autoDedupKey) {
                    val newKey = "new:${nextNewIdx++}"
                    val created = CandidateTargetWord(
                        key = newKey,
                        existingWordId = null,
                        displayIndex = candidateWords.size + 1,
                        text = fallbackStem,
                        remark = tokens.remarkHint,
                        groupName = tokens.groupHint,
                        isAutoCreatedFromAudio = true
                    )
                    candidateWords.add(created)
                    wordsByKey[newKey] = created
                    created
                }
                rawMatches.add(
                    RawAudioMatch(
                        item = audio,
                        tokens = tokens,
                        resolvedSpeaker = resolvedSpeaker,
                        targetKey = autoWord.key,
                        matchKind = if (manualOverride == OVERRIDE_AUTO_CREATE) {
                            PairingMatchKind.MANUAL_OVERRIDE
                        } else {
                            PairingMatchKind.AUTO_NEW_WORD
                        }
                    )
                )
            } else {
                rawMatches.add(RawAudioMatch(audio, tokens, resolvedSpeaker, null, PairingMatchKind.UNASSIGNED))
            }
        }

        // Compute multi-take counts per (targetKey, resolvedSpeaker)
        val groupCounts = HashMap<Pair<String, String>, Int>()
        for (rm in rawMatches) {
            if (rm.targetKey != null) {
                val k = rm.targetKey to rm.resolvedSpeaker
                groupCounts[k] = (groupCounts[k] ?: 0) + 1
            }
        }
        val groupSeenSoFar = HashMap<Pair<String, String>, Int>()

        val finalPairings = rawMatches
            .sortedWith(
                compareBy<RawAudioMatch>(
                    { rm -> rm.targetKey?.let { wordsByKey[it]?.displayIndex } ?: Int.MAX_VALUE },
                    { it.resolvedSpeaker },
                    { it.tokens.takeOrder },
                    { it.item.displayName.lowercase(Locale.ROOT) }
                )
            )
            .map { rm ->
                val totalTakes = if (rm.targetKey != null) {
                    groupCounts[rm.targetKey to rm.resolvedSpeaker] ?: 1
                } else 1
                val curSeq = if (rm.targetKey != null) {
                    val k = rm.targetKey to rm.resolvedSpeaker
                    val next = (groupSeenSoFar[k] ?: 0) + 1
                    groupSeenSoFar[k] = next
                    next
                } else 1

                AudioWordPairingRow(
                    audioItem = rm.item,
                    parsedIndexHint = rm.tokens.indexHint,
                    cleanedWordStem = rm.tokens.cleanedWordStem,
                    inferredSpeakerName = rm.tokens.speakerHint,
                    resolvedSpeakerName = rm.resolvedSpeaker,
                    takeSequenceInBatch = curSeq,
                    totalTakesForTarget = totalTakes,
                    targetWordKey = rm.targetKey,
                    targetWord = rm.targetKey?.let { wordsByKey[it] },
                    matchKind = rm.matchKind
                )
            }

        val suggestedProjectName = deriveSuggestedProjectName(stagedItems, archiveItems, wordlistItems)
        val detectedSpeakers = finalPairings.map { it.resolvedSpeakerName }.distinct()
        val transcodeCount = finalPairings.count { it.targetWordKey != null && it.audioItem.needsAudioTranscode }
        val readyAudioCount = finalPairings.count { it.targetWordKey != null }
        val autoWordCount = candidateWords.count { it.isAutoCreatedFromAudio }
        val unassignedCount = finalPairings.count { it.targetWordKey == null }

        return SmartImportPlan(
            stagedItems = stagedItems,
            archiveItems = archiveItems,
            wordlistItems = wordlistItems,
            audioItems = audioItems,
            imageItems = imageItems,
            unsupportedItems = unsupportedItems,
            suggestedProjectName = suggestedProjectName,
            candidateWords = candidateWords,
            matchedImageCount = matchedImageCount,
            audioPairings = finalPairings,
            detectedSpeakerNames = detectedSpeakers,
            transcodeRequiredCount = transcodeCount,
            readyAudioCount = readyAudioCount,
            autoCreatedWordCount = autoWordCount,
            unassignedAudioCount = unassignedCount
        )
    }

    private fun deriveSuggestedProjectName(
        stagedItems: List<StagedImportItem>,
        archiveItems: List<StagedImportItem>,
        wordlistItems: List<StagedImportItem>
    ): String {
        val rootDirName = stagedItems.firstOrNull { it.rootOriginName.isNotBlank() }?.rootOriginName
        if (!rootDirName.isNullOrBlank()) return rootDirName

        val wlName = wordlistItems.firstOrNull()?.displayName?.substringBeforeLast('.')?.trim()
        if (!wlName.isNullOrBlank()) return wlName

        val archName = archiveItems.firstOrNull()?.archiveInspect?.archiveProjectName
        if (!archName.isNullOrBlank()) return archName

        val firstFolder = stagedItems.firstOrNull { it.relativeFolder.isNotBlank() }
            ?.relativeFolder?.substringBefore('/')?.trim()
        if (!firstFolder.isNullOrBlank()) return firstFolder

        return "导入调查项目"
    }

    private fun extractNaturalSortNumber(name: String): Int {
        val m = Regex("""\d{1,6}""").find(name)
        return m?.value?.toIntOrNull() ?: Int.MAX_VALUE
    }

    /**
     * Executes a finalized [SmartImportPlan], writing project archives, wordlists, prompt images,
     * and normalized 16-bit PCM WAV audio recordings into [repository].
     */
    suspend fun executeImportPlan(
        context: Context,
        repository: ProjectRepository,
        plan: SmartImportPlan,
        config: SmartImportPlanConfig,
        existingProjects: List<Project>,
        existingSpeakers: List<Speaker>,
        defaultSpeakerId: Long?,
        lowVolumeThreshold: Float = 250f,
        onProgress: (SmartImportProgress) -> Unit = {}
    ): SmartImportResult = withContext(Dispatchers.IO) {
        var importedArchives = 0
        var lastArchiveProjectId: Long? = null
        var lastArchiveSpeakerId: Long? = null
        var lastArchiveSummaryMsg: String? = null
        val archiveFailureMessages = mutableListOf<String>()
        var skippedFiles = plan.unsupportedItems.size + plan.unassignedAudioCount

        val totalSteps = (plan.archiveItems.size + 1 + plan.readyAudioCount).coerceAtLeast(1)
        var currentStep = 0

        // 1. Import standalone Project Archives (.zip / .teproj)
        for (arch in plan.archiveItems) {
            val f = arch.localTempFile
            if (f == null || !f.exists()) {
                skippedFiles++
                archiveFailureMessages.add("${arch.displayName}：临时文件不可读")
                continue
            }
            currentStep++
            onProgress(
                SmartImportProgress(
                    fraction = (currentStep.toFloat() / totalSteps).coerceIn(0.05f, 0.95f),
                    stageMessage = SSmartImport.progressImportingArchive.withArgs("name" to arch.displayName)
                )
            )
            val fallbackName = arch.displayName.substringBeforeLast('.').trim().ifBlank { null }
            val summaryResult = runCatching {
                FileInputStream(f).use { fis ->
                    ZipUtility.importProjectArchiveZip(
                        context = context,
                        repository = repository,
                        inputStream = fis,
                        fallbackProjectName = fallbackName
                    )
                }
            }
            val summary = summaryResult.getOrNull()
            if (summary != null && summary.success && summary.projectId > 0L) {
                importedArchives++
                lastArchiveProjectId = summary.projectId
                if (summary.primarySpeakerId > 0L) {
                    lastArchiveSpeakerId = summary.primarySpeakerId
                }
                lastArchiveSummaryMsg = summary.message
            } else {
                skippedFiles++
                val errMsg = summary?.message?.takeIf { it.isNotBlank() }
                    ?: summaryResult.exceptionOrNull()?.localizedMessage
                    ?: "压缩包解析失败"
                archiveFailureMessages.add("${arch.displayName}：$errMsg")
            }
        }

        val hasCorpusPayload = plan.candidateWords.isNotEmpty() || plan.readyAudioCount > 0
        if (!hasCorpusPayload) {
            val ok = importedArchives > 0
            val baseMsg = when {
                ok && importedArchives == 1 && !lastArchiveSummaryMsg.isNullOrBlank() -> lastArchiveSummaryMsg!!
                ok -> SSmartImport.resultSuccessArchivesOnly.withArgs("count" to importedArchives)
                archiveFailureMessages.isNotEmpty() -> archiveFailureMessages.joinToString("；")
                else -> "没有可导入的有效内容"
            }
            val finalMsg = if (skippedFiles > 0 && ok) {
                baseMsg + SSmartImport.resultPartialSkippedSuffix.withArgs("skipped" to skippedFiles)
            } else baseMsg

            return@withContext SmartImportResult(
                success = ok,
                primaryProjectId = lastArchiveProjectId,
                primarySpeakerId = lastArchiveSpeakerId ?: defaultSpeakerId,
                importedArchiveCount = importedArchives,
                createdOrAddedWordCount = 0,
                importedAudioCount = 0,
                skippedCount = skippedFiles,
                summaryMessage = finalMsg
            )
        }

        // Validate merge target BEFORE creating any speakers or mutating database state
        val mergeTargetProject = if (config.targetMode == ImportTargetMode.MERGE_EXISTING_PROJECT) {
            val resolved = existingProjects.firstOrNull { it.id == config.mergeTargetProjectId }
                ?: config.mergeTargetProjectId?.let { repository.getProjectById(it) }
            if (resolved == null) {
                val errMsg = "导入失败：所选并入目标项目已不存在，请重新选择目标项目"
                return@withContext SmartImportResult(
                    success = importedArchives > 0,
                    primaryProjectId = lastArchiveProjectId,
                    primarySpeakerId = lastArchiveSpeakerId ?: defaultSpeakerId,
                    importedArchiveCount = importedArchives,
                    createdOrAddedWordCount = 0,
                    importedAudioCount = 0,
                    skippedCount = skippedFiles,
                    summaryMessage = errMsg
                )
            }
            resolved
        } else null

        // 2. Resolve Speakers (matching existing by name or creating new speakers when subfolder/prefix speakers detected)
        val speakerIdByName = HashMap<String, Long>()
        for (spk in existingSpeakers) {
            speakerIdByName[spk.name.trim().lowercase(Locale.ROOT)] = spk.id
        }
        val resolvedDefaultSpeakerId = defaultSpeakerId
            ?: existingSpeakers.firstOrNull()?.id
            ?: repository.createSpeaker(
                name = config.defaultSpeakerName.ifBlank { "发音人1" },
                gender = "男",
                age = 30,
                dialect = "",
                notes = ""
            ).also { newId ->
                speakerIdByName[(config.defaultSpeakerName.ifBlank { "发音人1" }).lowercase(Locale.ROOT)] = newId
            }

        suspend fun resolveSpeakerIdForName(rawName: String): Long {
            val clean = rawName.trim()
            if (clean.isEmpty()) return resolvedDefaultSpeakerId
            val hit = speakerIdByName[clean.lowercase(Locale.ROOT)]
            if (hit != null) return hit
            val createdId = repository.createSpeaker(
                name = clean,
                gender = "男",
                age = 30,
                dialect = "",
                notes = "文件导入时创建"
            )
            speakerIdByName[clean.lowercase(Locale.ROOT)] = createdId
            return createdId
        }

        // 3. Create New Project OR Merge Candidate Words into Existing Project
        currentStep++
        onProgress(
            SmartImportProgress(
                fraction = (currentStep.toFloat() / totalSteps).coerceIn(0.08f, 0.95f),
                stageMessage = SSmartImport.progressPreparingWords
            )
        )

        val targetProjectId: Long
        var targetProjectName: String
        var newlyCreatedOrAddedWords = 0
        val wordIdByCandidateKey = HashMap<String, Long>(plan.candidateWords.size * 2)

        if (mergeTargetProject != null) {
            targetProjectId = mergeTargetProject.id
            targetProjectName = mergeTargetProject.name
            var anyPictureAdded = false

            for (cw in plan.candidateWords) {
                var stagedToken = cw.existingImagePath
                if (cw.stagedImageFile != null && cw.stagedImageFile.exists()) {
                    val token = WordImageManager.stageImageFromFile(context, cw.stagedImageFile)
                    if (!token.isNullOrBlank()) {
                        stagedToken = WordImageManager.commitImageToProjectSync(context, targetProjectId, token)
                        anyPictureAdded = true
                    }
                }
                if (cw.existingWordId != null) {
                    wordIdByCandidateKey[cw.key] = cw.existingWordId
                    if (stagedToken != cw.existingImagePath && stagedToken.isNotBlank()) {
                        val existingEntity = repository.getWordById(cw.existingWordId)
                        if (existingEntity != null) {
                            repository.updateWord(existingEntity.copy(imagePath = stagedToken))
                        }
                    }
                } else {
                    val insertedId = repository.addWordToProject(
                        projectId = targetProjectId,
                        text = cw.text,
                        remark = cw.remark,
                        groupName = cw.groupName,
                        imagePath = stagedToken,
                        context = context
                    )
                    wordIdByCandidateKey[cw.key] = insertedId
                    newlyCreatedOrAddedWords++
                }
            }

            if (anyPictureAdded && !mergeTargetProject.enablePicturePrompts) {
                repository.updateProject(mergeTargetProject.copy(enablePicturePrompts = true))
            }
        } else {
            // CREATE_NEW_PROJECT
            targetProjectName = config.customNewProjectName.trim().ifBlank { plan.suggestedProjectName }
            var anyPictureStaged = false
            val parsedList = plan.candidateWords.map { cw ->
                var imgToken = ""
                if (cw.stagedImageFile != null && cw.stagedImageFile.exists()) {
                    val staged = WordImageManager.stageImageFromFile(context, cw.stagedImageFile)
                    if (!staged.isNullOrBlank()) {
                        imgToken = staged
                        anyPictureStaged = true
                    }
                }
                ParsedWord(
                    text = cw.text,
                    remark = cw.remark,
                    groupName = cw.groupName,
                    imagePath = imgToken
                )
            }
            targetProjectId = repository.createProjectWithParsedWords(
                name = targetProjectName,
                description = "",
                parsedWords = parsedList,
                speakerId = resolvedDefaultSpeakerId,
                speakerGroupName = "",
                enablePicturePrompts = anyPictureStaged,
                context = context
            )
            newlyCreatedOrAddedWords = parsedList.size
            val insertedWords = repository.getWordsForProjectSync(targetProjectId).sortedBy { it.originalIndex }
            plan.candidateWords.forEachIndexed { index, cw ->
                insertedWords.getOrNull(index)?.let { w ->
                    wordIdByCandidateKey[cw.key] = w.id
                }
            }
        }

        // 4. Normalize / Transcode & Save Paired Audio Recordings
        val projDir = File(context.getExternalFilesDir(null), "project_$targetProjectId").apply { mkdirs() }
        val activeRows = plan.audioPairings.filter { it.targetWordKey != null }
        var importedAudioCount = 0
        var primaryImportedSpeakerId: Long? = null
        val baseEpoch = System.currentTimeMillis()
        var takeCounterOffset = 1L

        // Track whether we already checked/backed up prior custom-named recording per (wordId, speakerId)
        val handledPriorCustomRecording = HashSet<Pair<Long, Long>>()

        activeRows.forEachIndexed { idx, row ->
            currentStep++
            onProgress(
                SmartImportProgress(
                    fraction = (currentStep.toFloat() / totalSteps).coerceIn(0.12f, 0.99f),
                    stageMessage = SSmartImport.progressNormalizingAudio.withArgs(
                        "current" to (idx + 1),
                        "total" to activeRows.size,
                        "name" to row.audioItem.displayName
                    )
                )
            )

            val wordId = row.targetWordKey?.let { wordIdByCandidateKey[it] }
            val srcTemp = row.audioItem.localTempFile
            if (wordId == null || srcTemp == null || !srcTemp.exists()) {
                skippedFiles++
                return@forEachIndexed
            }

            val speakerId = resolveSpeakerIdForName(row.resolvedSpeakerName)
            if (primaryImportedSpeakerId == null) {
                primaryImportedSpeakerId = speakerId
            }

            if (mergeTargetProject != null) {
                val existingRec = repository.getSpeakerRecording(wordId, speakerId)
                if (existingRec != null) {
                    if (config.mergeAudioPolicy == MergeAudioPolicy.ONLY_FILL_UNRECORDED) {
                        skippedFiles++
                        return@forEachIndexed
                    }
                    // Preserve custom-named prior recording in Multi-Take history before overwriting active pointer
                    val pairKey = wordId to speakerId
                    if (handledPriorCustomRecording.add(pairKey)) {
                        val expectedPrefix = "word_${wordId}_speaker_${speakerId}_"
                        if (!existingRec.recordingFile.startsWith(expectedPrefix)) {
                            val oldFile = File(projDir, existingRec.recordingFile)
                            if (oldFile.exists() && oldFile.length() > 44L) {
                                val backupName = "${expectedPrefix}${baseEpoch - 10_000L}.wav"
                                val backupFile = File(projDir, backupName)
                                if (!backupFile.exists()) {
                                    runCatching { oldFile.copyTo(backupFile, overwrite = false) }
                                }
                            }
                        }
                    }
                }
            }

            val takeTs = baseEpoch + (takeCounterOffset++) * 10L
            val destWavName = "word_${wordId}_speaker_${speakerId}_${takeTs}.wav"
            val destWavFile = File(projDir, destWavName)

            val normalizedOk = SmartImportPlatformBridge.transcodeAudioToMono16Wav(
                context = context,
                inputFile = srcTemp,
                outputWavFile = destWavFile
            )
            if (!normalizedOk || !destWavFile.exists() || destWavFile.length() <= 44L) {
                runCatching { destWavFile.delete() }
                skippedFiles++
                return@forEachIndexed
            }

            val report = SpeechQualityEvaluator.evaluate(destWavFile, lowVolumeThreshold)
            val durationMs = report.totalDurationMs.toLong().coerceAtLeast(100L)
            val peak = report.peakAmplitude.coerceIn(0, 32767)
            val rms = report.avgSpeechRms.coerceAtLeast(0f)

            repository.saveSpeakerRecording(
                wordId = wordId,
                speakerId = speakerId,
                recordingFile = destWavName,
                durationMs = durationMs,
                qualityStatus = report.primaryStatus,
                avgRms = rms,
                peakAmplitude = peak
            )
            importedAudioCount++
        }

        val archivePrefix = if (importedArchives > 0) {
            "已还原 $importedArchives 个项目包；"
        } else {
            ""
        }
        val baseMessage = archivePrefix + if (mergeTargetProject != null) {
            SSmartImport.resultSuccessMerged.withArgs(
                "name" to targetProjectName,
                "words" to newlyCreatedOrAddedWords,
                "audio" to importedAudioCount
            )
        } else {
            SSmartImport.resultSuccessNewProject.withArgs(
                "name" to targetProjectName,
                "words" to newlyCreatedOrAddedWords,
                "audio" to importedAudioCount
            )
        }
        val fullMessage = if (skippedFiles > 0) {
            baseMessage + SSmartImport.resultPartialSkippedSuffix.withArgs("skipped" to skippedFiles)
        } else {
            baseMessage
        }

        SmartImportResult(
            success = true,
            primaryProjectId = targetProjectId,
            primarySpeakerId = primaryImportedSpeakerId ?: resolvedDefaultSpeakerId,
            importedArchiveCount = importedArchives,
            createdOrAddedWordCount = newlyCreatedOrAddedWords,
            importedAudioCount = importedAudioCount,
            skippedCount = skippedFiles,
            summaryMessage = fullMessage
        )
    }
}
