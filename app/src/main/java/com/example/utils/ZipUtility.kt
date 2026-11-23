package com.example.utils

import android.content.Context
import com.example.audio.AcousticAnalyzer
import com.example.audio.CorpusPhonologyAnalyzer
import com.example.audio.SpeechQualityEvaluator
import com.example.audio.VowelGlideType
import com.example.data.ParsedWord
import com.example.data.Project
import com.example.data.ProjectRepository
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExportOptions(
    val includeAudio: Boolean = true,
    val includeAnalysis: Boolean = true,
    val includeWordList: Boolean = true,
    val includeSpeakerProfile: Boolean = true,
    val includeExperimentProfile: Boolean = true,
    val includePraatTextGrid: Boolean = true,
    val exportAllSpeakers: Boolean = false,
    val namingStyle: String = "CODE_INDEX_WORD", // "CODE_INDEX_WORD", "NAME_INDEX_WORD", "INDEX_WORD"
    val folderPerSpeaker: Boolean = false,
    val exportAsTeproj: Boolean = false,
    val exportAsPraatCollection: Boolean = false,
    val praatCollectionBinary: Boolean = true,
    val includePraatSummaryTable: Boolean = true,
    val includeAcousticTagsInTextGrid: Boolean = false,
    val includeWordImages: Boolean = true,
    val simpleModeExport: Boolean = false,
    val appendNoteToFileName: Boolean = false,
    val includeBackupTakes: Boolean = false,
    val textGridConfig: TextGridFormatConfig = TextGridFormatConfig()
)

data class ExportSummary(
    val success: Boolean,
    val wavCount: Int = 0,
    val textGridCount: Int = 0,
    val csvRowCount: Int = 0,
    val speakerCount: Int = 0,
    val isTeproj: Boolean = false,
    val isPraatCollection: Boolean = false,
    val objectCount: Int = 0,
    val message: String = ""
)

data class ImportArchiveSummary(
    val success: Boolean,
    val projectId: Long = -1L,
    val projectName: String = "",
    val wordCount: Int = 0,
    val wavCount: Int = 0,
    val speakerCount: Int = 0,
    val message: String = "",
    val primarySpeakerId: Long = -1L
)

object ZipUtility {

    data class ResolvedWordSegmentation(
        val durationSec: Double,
        val startSec: Double,
        val endSec: Double,
        val charIntervals: List<Triple<Double, Double, String>>,
        val charsBounds: List<Pair<Double, Double>>,
        val innerSplits: List<Double>
    )

    data class ExportedWordAudioRecord(
        val speakerId: Long,
        val wordId: Long,
        val originalIndex: Int,
        val zipAudioPath: String,
        val segmentation: ResolvedWordSegmentation
    )

    data class ParsedWordMetadata(
        val ipa: String,
        val gloss: String,
        val extra: String = "",
        val tags: List<String> = emptyList()
    ) {
        fun toMetaJsonObject(): JSONObject {
            return JSONObject().apply {
                put("ipa", ipa)
                put("gloss", gloss)
                if (extra.isNotBlank()) {
                    put("extra", extra)
                }
            }
        }

        fun toTagsJsonArray(): JSONArray {
            return JSONArray().apply {
                tags.forEach { put(it) }
            }
        }
    }

    private val WINDOWS_ILLEGAL_CHARS_REGEX = Regex("[<>:\"|?*\\\\/\\u0000-\\u001F]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val IPA_OR_ASCII_TOKEN_REGEX = Regex("^[A-Za-z0-9\\[\\]/ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃ˥˦˧˨˩ːˑ̃̌̀́̄̂ˌˈ.\\-_]+$")
    private val KNOWN_FIELD_TAGS: Set<String>
        get() = S.recording.defaultQuickFieldTags.toSet()

    /**
     * Sanitizes a single filename component so it never contains path separators, `..`,
     * or Windows illegal filename characters (`<>:"|?*`).
     */
    fun sanitizeFileNameComponent(raw: String, fallback: String = "item"): String {
        val cleaned = raw
            .replace(WINDOWS_ILLEGAL_CHARS_REGEX, "_")
            .replace(WHITESPACE_REGEX, "_")
            .replace("..", "_")
            .trim('_', '.', ' ')
        return cleaned.ifEmpty { fallback }
    }

    /**
     * Normalizes an archive entry path to strictly comply with PhonTracer / ZIP standards:
     * - Forward slashes `/` only
     * - Never starts with `/`
     * - Never contains `..` or Windows illegal characters (`<>:"|?*`)
     */
    fun normalizeZipEntryPath(rawPath: String): String {
        val segments = rawPath
            .replace('\\', '/')
            .split('/')
            .map { seg ->
                seg.replace(Regex("[<>:\"|?*\\u0000-\\u001F]+"), "_")
                    .replace("..", "_")
                    .trim(' ', '.')
            }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
        return segments.joinToString("/").ifEmpty { "entry" }
    }

    /**
     * Exports a native PhonTracer `.teproj` archive directly to [targetStream].
     * Contains root `project.json`, `audio/<file>.wav` with strictly validated PCM headers,
     * and paired `audio/<file>.TextGrid` files with `groups`, `words`, `chars`, and `segments` tiers.
     */
    fun exportPhonTracerTeproj(
        context: Context,
        project: Project,
        speakerWordPairs: List<Pair<Speaker?, List<Word>>>,
        includePraatTextGrid: Boolean = true,
        namingStyle: String = "CODE_INDEX_WORD",
        activeSpeakerId: Long? = null,
        targetStream: OutputStream
    ): ExportSummary {
        val teprojOptions = ExportOptions(
            includeAudio = true,
            includeAnalysis = false,
            includeWordList = false,
            includeSpeakerProfile = false,
            includeExperimentProfile = false,
            includePraatTextGrid = includePraatTextGrid,
            exportAllSpeakers = speakerWordPairs.size > 1,
            namingStyle = namingStyle,
            folderPerSpeaker = false,
            exportAsTeproj = true
        )
        return zipProjectMultiSpeaker(
            context = context,
            project = project,
            speakerWordPairs = speakerWordPairs,
            options = teprojOptions,
            targetStream = targetStream,
            preferredActiveSpeakerId = activeSpeakerId
        )
    }

    /**
     * Packages the project corpus into an academic-standard archive AND/OR native PhonTracer `.teproj`:
     * - project.json (100% PhonTracer `.teproj` v1.0 schema compliant)
     * - experiment_profile.json (when !exportAsTeproj && includeExperimentProfile)
     * - speaker_profile.json (when !exportAsTeproj && includeSpeakerProfile)
     * - word_list.txt (when !exportAsTeproj && includeWordList)
     * - acoustic_summary.xlsx & analysis_results.json (when !exportAsTeproj && includeAnalysis)
     * - audio/SPK{speakerId}_{index3d}_{word}.wav (validated PCM header with nAvgBytesPerSec == sample_rate * channels * (bits_per_sample // 8))
     * - audio/SPK{speakerId}_{index3d}_{word}.TextGrid (with `groups`, `words`, `chars`, `segments` IntervalTiers)
     */
    fun zipProjectMultiSpeaker(
        context: Context,
        project: Project,
        speakerWordPairs: List<Pair<Speaker?, List<Word>>>,
        options: ExportOptions,
        targetStream: OutputStream,
        preferredActiveSpeakerId: Long? = null
    ): ExportSummary {
        if (options.exportAsPraatCollection) {
            return PraatCollectionExporter.exportProjectAsPraatCollection(
                context = context,
                project = project,
                speakerWordPairs = speakerWordPairs,
                options = options,
                targetStream = targetStream
            )
        }
        if (options.simpleModeExport) {
            return zipProjectSimpleMode(
                context = context,
                project = project,
                speakerWordPairs = speakerWordPairs,
                options = options,
                targetStream = targetStream
            )
        }

        var zipOut: ZipOutputStream? = null
        var wavCount = 0
        var textGridCount = 0
        var csvRowCount = 0
        var imageCount = 0

        try {
            zipOut = ZipOutputStream(BufferedOutputStream(targetStream)).apply {
                setMethod(ZipOutputStream.DEFLATED)
                setLevel(Deflater.DEFAULT_COMPRESSION)
            }
            val usedZipPaths = HashSet<String>()

            fun allocateUniqueZipPath(rawPath: String): String {
                val cleanPath = normalizeZipEntryPath(rawPath)
                var candidate = cleanPath
                var suffix = 2
                while (!usedZipPaths.add(candidate)) {
                    val slashIdx = cleanPath.lastIndexOf('/')
                    val dirPart = if (slashIdx >= 0) cleanPath.substring(0, slashIdx + 1) else ""
                    val filePart = if (slashIdx >= 0) cleanPath.substring(slashIdx + 1) else cleanPath
                    val dotIdx = filePart.lastIndexOf('.')
                    candidate = if (dotIdx > 0) {
                        "$dirPart${filePart.substring(0, dotIdx)}_$suffix${filePart.substring(dotIdx)}"
                    } else {
                        "$dirPart${filePart}_$suffix"
                    }
                    suffix++
                }
                return candidate
            }

            fun putUniqueEntry(rawPath: String): String {
                val uniquePath = allocateUniqueZipPath(rawPath)
                zipOut.putNextEntry(ZipEntry(uniquePath))
                return uniquePath
            }

            val projectDir = File(context.getExternalFilesDir(null), "project_${project.id}")
            val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val configuredSampleRate = appPrefs.getInt("sample_rate_hz", 44100)
            val firstExistingWav = speakerWordPairs.asSequence()
                .flatMap { it.second.asSequence() }
                .mapNotNull { w ->
                    val fn = w.recordingFile
                    if (w.isRecorded && !fn.isNullOrEmpty()) File(projectDir, fn) else null
                }
                .firstOrNull { it.exists() && it.length() > 44L }
            val actualExportSampleRate = if (firstExistingWav != null) {
                AcousticAnalyzer.readWavHeaderFormat(firstExistingWav).sampleRate
            } else {
                configuredSampleRate
            }

            val referenceWords = speakerWordPairs
                .firstOrNull { it.second.isNotEmpty() }
                ?.second
                ?.sortedBy { it.originalIndex }
                ?: emptyList()
            val exportedImagePathsByWordId = LinkedHashMap<Long, String>()
            val exportedImagePathsByIndex = LinkedHashMap<Int, String>()

            if (options.includeWordImages && project.enablePicturePrompts) {
                for (word in referenceWords) {
                    if (word.imagePath.isBlank()) continue
                    val imgFile = WordImageManager.resolveWordImageFile(context, project.id, word.imagePath)
                    if (imgFile == null || !imgFile.exists() || imgFile.length() <= 0L) continue
                    val ext = imgFile.extension.lowercase(Locale.US).ifBlank { "jpg" }
                    val idxStr = String.format(Locale.US, "%03d", word.originalIndex + 1)
                    val safeWord = sanitizeFileNameComponent(word.text, "word_${word.id}").take(24)
                    val zipImgPath = putUniqueEntry("images/${idxStr}_${safeWord}.${ext}")
                    FileInputStream(imgFile).use { fis ->
                        fis.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                    imageCount++
                    if (word.id != 0L) {
                        exportedImagePathsByWordId[word.id] = zipImgPath
                    }
                    exportedImagePathsByIndex[word.originalIndex] = zipImgPath
                }
            }

            if (!options.exportAsTeproj && options.includeExperimentProfile) {
                val metaJson = JSONObject().apply {
                    put("id", project.id)
                    put("name", project.name)
                    put("description", project.description)
                    put("created_at", project.createdAt)
                    put("speaker_group_name", project.speakerGroupName)
                    if (project.assignedSpeakerIds.isNotBlank()) {
                        put("assigned_speaker_ids", project.assignedSpeakerIds)
                    }
                    put("enable_picture_prompts", project.enablePicturePrompts)
                    if (imageCount > 0) {
                        put("exported_image_count", imageCount)
                    }
                    put("exported_speaker_count", speakerWordPairs.size)
                    put("sample_rate_hz", actualExportSampleRate)
                    put("encoding", "PCM_16BIT_MONO")
                    put("export_standard", "PhonAcq Academic Fieldwork Archive v2.2 (PhonTracer Compatible)")
                }
                putUniqueEntry("experiment_profile.json")
                zipOut.write(metaJson.toString(2).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            val spPrefs = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
            if (!options.exportAsTeproj && options.includeSpeakerProfile) {
                val speakersArr = JSONArray()
                speakerWordPairs.mapNotNull { it.first }.distinctBy { it.id }.forEach { spk ->
                    val pMin = spPrefs.getFloat("pitch_min_${spk.id}", -1f)
                    val pMax = spPrefs.getFloat("pitch_max_${spk.id}", -1f)
                    val spJson = JSONObject().apply {
                        put("id", spk.id)
                        put("speaker_code", String.format(Locale.US, "SPK%02d", spk.id))
                        put("name", spk.name)
                        put("gender_identity", spk.gender)
                        put("age", spk.age)
                        put("dialect", spk.dialect)
                        put("group_name", spk.groupName)
                        put("notes", spk.notes)
                        if (pMin > 0 && pMax > 0 && pMin.isFinite() && pMax.isFinite()) {
                            put("calibrated_f0_min_hz", safeDouble(pMin.toDouble()))
                            put("calibrated_f0_max_hz", safeDouble(pMax.toDouble()))
                        }
                    }
                    speakersArr.put(spJson)
                }
                val profileRoot = if (speakersArr.length() == 1) {
                    speakersArr.getJSONObject(0).toString(2)
                } else {
                    JSONObject().apply {
                        put("speakers", speakersArr)
                    }.toString(2)
                }
                putUniqueEntry("speaker_profile.json")
                zipOut.write(profileRoot.toString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            if (!options.exportAsTeproj && options.includeWordList && referenceWords.isNotEmpty()) {
                val sb = StringBuilder()
                var currentGroup = ""
                referenceWords.forEach { word ->
                    if (word.groupName.isNotEmpty() && word.groupName != currentGroup) {
                        sb.append("【").append(word.groupName).append("】\n")
                        currentGroup = word.groupName
                    }
                    val indexLabel = String.format(Locale.US, "%03d", word.originalIndex + 1)
                    if (word.remark.isNotEmpty()) {
                        sb.append("$indexLabel\t${word.text}\t(${word.remark})\n")
                    } else {
                        sb.append("$indexLabel\t${word.text}\n")
                    }
                }
                putUniqueEntry("word_list.txt")
                zipOut.write(sb.toString().toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            val analysisArray = JSONArray()
            val xlsxSummaryHeaders = listOf(
                "Index", "SpeakerCode", "SpeakerName", "GenderIdentity", "Age", "Dialect", "SpeakerGroup",
                "WordGroup", "Word", "Remark", "SpeakerNote", "AudioFile", "TextGridFile",
                "Duration_ms", "Voiced_ms", "QualityStatus", "Intensity_dBFS", "Peak_dBFS", "SNR_dB",
                "F0_Mean_Hz", "F0_Min_Hz", "F0_Max_Hz", "Chao_Tone_T", "Tone_Category",
                "Jitter_Local_Pct", "Shimmer_Local_Pct", "F1_Hz", "F2_Hz", "F3_Hz", "Vowel_Type",
                "F1_Onset_Hz", "F2_Onset_Hz", "F1_Target_Hz", "F2_Target_Hz", "Bark_Glide_Span",
                "Checked_Tone", "Canonical_Tone_T", "Canonical_Tone_Category", "Tone_Outlier"
            )
            val xlsxSummaryRows = mutableListOf<List<Any?>>()
            val csvBuilder = StringBuilder()
            // UTF-8 BOM so Excel/R/Python opens Chinese characters cleanly
            csvBuilder.append("\uFEFF")
            csvBuilder.append(xlsxSummaryHeaders.joinToString(",")).append("\n")

            val exportedAudioRecords = mutableListOf<ExportedWordAudioRecord>()
            val shouldWriteAudio = options.exportAsTeproj || options.includeAudio
            val shouldRunAnalysis = shouldWriteAudio || options.includeAnalysis || options.includePraatTextGrid

            for ((speaker, words) in speakerWordPairs) {
                val speakerId = speaker?.id ?: 1L
                val speakerCode = String.format(Locale.US, "SPK%02d", speakerId)
                val pMin = spPrefs.getFloat("pitch_min_$speakerId", -1f)
                val pMax = spPrefs.getFloat("pitch_max_$speakerId", -1f)
                val pitchRange = if (pMin > 40f && pMax > pMin + 15f) Pair(pMin, pMax) else null
                val speakerGender = speaker?.gender ?: ""

                val sortedWords = words.sortedBy { it.originalIndex }
                val corpusStats = if (shouldRunAnalysis) {
                    AcousticAnalyzer.resolveSpeakerCorpusStats(
                        projDir = projectDir,
                        speakerId = speakerId,
                        speakerGender = speakerGender,
                        manualRange = pitchRange
                    )
                } else null
                val effectivePitchRange = corpusStats?.pitchRange ?: pitchRange
                val medianVoicedMs = corpusStats?.medianVoicedDurationMs ?: 280
                val speakerPhonologyReport = if (!options.exportAsTeproj && options.includeAnalysis) {
                    CorpusPhonologyAnalyzer.buildSpeakerPhonologyReport(
                        context = context,
                        projectId = project.id,
                        speaker = speaker,
                        words = sortedWords
                    )
                } else null

                for (word in sortedWords) {
                    val originalFileName = word.recordingFile
                    if (!word.isRecorded || originalFileName.isNullOrEmpty()) continue

                    val file = File(projectDir, originalFileName)
                    if (!file.exists() || file.length() <= 44L) continue

                    val idxStr = String.format(Locale.US, "%03d", word.originalIndex + 1)
                    val safeText = sanitizeFileNameComponent(word.text, "word_${word.id}").take(24)
                    val safeRemark = transliterateNoteForFileName(
                        word.speakerNote.ifBlank { word.remark }
                    ).take(16)
                    val safeSpkName = sanitizeFileNameComponent(speaker?.name ?: S.export.defaultExportSpeakerTag, speakerCode).take(16)

                    // In .teproj mode with multiple speakers, ensure speaker prefix is always present so flat audio/ paths never collide
                    val effectiveNamingStyle = if (options.exportAsTeproj && speakerWordPairs.size > 1 && options.namingStyle == "INDEX_WORD") {
                        "CODE_INDEX_WORD"
                    } else {
                        options.namingStyle
                    }

                    val prefixPart = when (effectiveNamingStyle) {
                        "NAME_INDEX_WORD" -> "${safeSpkName}_${idxStr}_${safeText}"
                        "INDEX_WORD" -> "${idxStr}_${safeText}"
                        else -> "${speakerCode}_${idxStr}_${safeText}"
                    }
                    val baseName = if (options.appendNoteToFileName && safeRemark.isNotEmpty() && effectiveNamingStyle != "INDEX_WORD") {
                        "${prefixPart}_${safeRemark}"
                    } else {
                        prefixPart
                    }
                    val audioSubDir = if (!options.exportAsTeproj && options.folderPerSpeaker) {
                        "audio/${safeSpkName}/"
                    } else {
                        "audio/"
                    }

                    val parts = extractSyllableLabels(word.text)
                    val segCount = parts.size.coerceAtLeast(1)
                    val exactDurationSec = AcousticAnalyzer.getExactWavDurationSec(file)
                    val exactDurationMs = (exactDurationSec * 1000.0).toLong().coerceAtLeast(1L)

                    val analysisResult = if (shouldRunAnalysis) {
                        AcousticAnalyzer.analyzeByGender(
                            file = file,
                            speakerGender = speakerGender,
                            segmentCount = segCount,
                            pitchRange = effectivePitchRange,
                            segmentLabels = parts,
                            speakerMedianVoicedMs = medianVoicedMs
                        )
                    } else {
                        null
                    }

                    val segmentation = resolveWordSegmentation(word, analysisResult, exactDurationSec)

                    // Allocate the exact WAV entry path first so .TextGrid, CSV, and project.json all reference the identical stem
                    val actualWavZipPath = if (shouldWriteAudio) {
                        putUniqueEntry("$audioSubDir$baseName.wav").also {
                            writeSanitizedWavToOutputStream(file, zipOut)
                            zipOut.closeEntry()
                            wavCount++
                        }
                    } else {
                        normalizeZipEntryPath("$audioSubDir$baseName.wav")
                    }

                    val actualWavFileName = actualWavZipPath.substringAfterLast('/')
                    val actualStemPath = actualWavZipPath.substringBeforeLast('.')
                    val actualTgFileName = "${actualWavFileName.substringBeforeLast('.')}.TextGrid"

                    if (shouldWriteAudio) {
                        exportedAudioRecords.add(
                            ExportedWordAudioRecord(
                                speakerId = speakerId,
                                wordId = word.id,
                                originalIndex = word.originalIndex,
                                zipAudioPath = actualWavZipPath,
                                segmentation = segmentation
                            )
                        )
                        if (!options.exportAsTeproj && options.includeBackupTakes) {
                            val takePrefix = "word_${word.id}_speaker_${speakerId}_"
                            val backupFiles = projectDir.listFiles { _, name ->
                                name != originalFileName &&
                                    name.startsWith(takePrefix) &&
                                    name.endsWith(".wav", ignoreCase = true)
                            }?.filter { it.isFile && it.length() > 44L }
                                ?.sortedByDescending { it.lastModified() }
                                .orEmpty()
                            val stemOnly = actualWavFileName.substringBeforeLast('.')
                            backupFiles.forEachIndexed { bIdx, bFile ->
                                putUniqueEntry("${audioSubDir}takes/${stemOnly}_take${bIdx + 2}.wav")
                                writeSanitizedWavToOutputStream(bFile, zipOut)
                                zipOut.closeEntry()
                            }
                        }
                    }

                    if (options.includePraatTextGrid) {
                        val effectiveTgConfig = if (options.exportAsTeproj) {
                            TextGridFormatConfig.fromPreset(TextGridPreset.PHONTRACER_COMPAT)
                        } else {
                            options.textGridConfig
                        }
                        val tgContent = buildPraatTextGrid(
                            word = word,
                            analysis = analysisResult,
                            exactDurationSec = exactDurationSec,
                            includeAcousticTags = options.includeAcousticTagsInTextGrid,
                            config = effectiveTgConfig
                        )
                        putUniqueEntry("$actualStemPath.TextGrid")
                        zipOut.write(tgContent.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                        textGridCount++
                    }

                    if (!options.exportAsTeproj && options.includeAnalysis) {
                        val totalDurMs = analysisResult?.totalDurationMs ?: exactDurationMs
                        val voicedDurMs = analysisResult?.voicedDurationMs ?: 0L
                        val isValidVoiced = analysisResult?.isValidVoiced ?: false
                        val intensityDbfs = analysisResult?.intensityDbfs ?: -96f
                        val peakDbfs = analysisResult?.peakDbfs ?: -96f
                        val snrDb = analysisResult?.snrDb ?: 0f
                        val pitchMean = analysisResult?.pitchMean ?: 0f
                        val pitchMin = analysisResult?.pitchMin ?: 0f
                        val pitchMax = analysisResult?.pitchMax ?: 0f
                        val chaoTone = analysisResult?.chaoToneNumbers ?: "--"
                        val toneCode = analysisResult?.toneCode ?: S.praat.undetectedF0
                        val jitter = analysisResult?.jitterPercent ?: 0f
                        val shimmer = analysisResult?.shimmerPercent ?: 0f
                        val f1List = analysisResult?.speakerF1 ?: emptyList()
                        val f2List = analysisResult?.speakerF2 ?: emptyList()
                        val f3List = analysisResult?.speakerF3 ?: emptyList()
                        val trajs = analysisResult?.formantTrajectories ?: emptyList()
                        val vowelTypesStr = trajs.joinToString(";") { it.vowelType.name }
                        val f1OnsetStr = trajs.joinToString(";") { it.onsetF1.toString() }
                        val f2OnsetStr = trajs.joinToString(";") { it.onsetF2.toString() }
                        val f1TargetStr = trajs.joinToString(";") { it.targetF1.toString() }
                        val f2TargetStr = trajs.joinToString(";") { it.targetF2.toString() }
                        val barkSpanStr = trajs.joinToString(";") { String.format(Locale.US, "%.2f", safeFloat(it.barkGlideSpan, 0f)) }
                        val isChecked = analysisResult?.isCheckedTone ?: false
                        val canonicalMember = speakerPhonologyReport?.wordIdToCanonicalMember?.get(word.id)
                        val canonicalChao = canonicalMember?.canonicalChao ?: chaoTone
                        val canonicalCatName = canonicalMember?.canonicalCategoryName ?: toneCode
                        val isOutlier = canonicalMember?.isOutlier ?: false

                        val accObj = JSONObject().apply {
                            put("index", word.originalIndex + 1)
                            put("speaker_code", speakerCode)
                            put("speaker_name", speaker?.name ?: S.export.defaultSpeakerFallbackName)
                            put("speaker_gender_identity", speakerGender)
                            put("speaker_group", speaker?.groupName ?: "")
                            put("word_text", word.text)
                            put("remark", word.remark)
                            put("speaker_note", word.speakerNote)
                            put("word_group", word.groupName.ifBlank { S.common.defaultGroupFallback })
                            put("file", actualWavFileName)
                            put("textgrid_file", actualTgFileName)
                            put("duration_ms", totalDurMs)
                            put("voiced_duration_ms", voicedDurMs)
                            put("quality_status", word.qualityStatus ?: "正常")
                            put("is_valid_voiced", isValidVoiced)
                            put("intensity_dbfs", round2(intensityDbfs))
                            put("peak_dbfs", round2(peakDbfs))
                            put("snr_db", round2(snrDb))
                            put("pitch_mean_hz", round2(pitchMean))
                            put("pitch_min_hz", round2(pitchMin))
                            put("pitch_max_hz", round2(pitchMax))
                            put("chao_tone_letters", chaoTone)
                            put("tone_classification", toneCode)
                            put("is_checked_tone", isChecked)
                            put("canonical_tone_letters", canonicalChao)
                            put("canonical_tone_category", canonicalCatName)
                            put("is_tone_outlier", isOutlier)
                            put("jitter_local_percent", round3(jitter))
                            put("shimmer_local_percent", round3(shimmer))
                            put("f1_hz", JSONArray(f1List))
                            put("f2_hz", JSONArray(f2List))
                            put("f3_hz", JSONArray(f3List))
                            put("vowel_types", vowelTypesStr)
                            put("f1_onset_hz", JSONArray(trajs.map { it.onsetF1 }))
                            put("f2_onset_hz", JSONArray(trajs.map { it.onsetF2 }))
                            put("f1_target_hz", JSONArray(trajs.map { it.targetF1 }))
                            put("f2_target_hz", JSONArray(trajs.map { it.targetF2 }))
                            put("bark_glide_span", JSONArray(trajs.map { round2(it.barkGlideSpan) }))
                        }
                        analysisArray.put(accObj)

                        val f1Str = f1List.joinToString(";")
                        val f2Str = f2List.joinToString(";")
                        val f3Str = f3List.joinToString(";")
                        xlsxSummaryRows.add(
                            listOf<Any?>(
                                word.originalIndex + 1,
                                speakerCode,
                                speaker?.name ?: S.export.defaultSpeakerFallbackName,
                                speakerGender,
                                speaker?.age ?: 25,
                                speaker?.dialect ?: "",
                                speaker?.groupName ?: "",
                                word.groupName.ifBlank { S.common.defaultGroupFallback },
                                word.text,
                                word.remark,
                                word.speakerNote,
                                actualWavFileName,
                                actualTgFileName,
                                totalDurMs,
                                voicedDurMs,
                                word.qualityStatus ?: "正常",
                                round2(safeFloat(intensityDbfs, -96f)),
                                round2(safeFloat(peakDbfs, -96f)),
                                round2(safeFloat(snrDb, 0f)),
                                round2(safeFloat(pitchMean, 0f)),
                                round2(safeFloat(pitchMin, 0f)),
                                round2(safeFloat(pitchMax, 0f)),
                                chaoTone,
                                toneCode,
                                round3(safeFloat(jitter, 0f)),
                                round3(safeFloat(shimmer, 0f)),
                                f1Str,
                                f2Str,
                                f3Str,
                                vowelTypesStr,
                                f1OnsetStr,
                                f2OnsetStr,
                                f1TargetStr,
                                f2TargetStr,
                                barkSpanStr,
                                if (isChecked) "YES" else "NO",
                                canonicalChao,
                                canonicalCatName,
                                if (isOutlier) "YES" else "NO"
                            )
                        )
                        csvBuilder.append(
                            listOf(
                                (word.originalIndex + 1).toString(),
                                csvEscape(speakerCode),
                                csvEscape(speaker?.name ?: S.export.defaultSpeakerFallbackName),
                                csvEscape(speakerGender),
                                (speaker?.age ?: 25).toString(),
                                csvEscape(speaker?.dialect ?: ""),
                                csvEscape(speaker?.groupName ?: ""),
                                csvEscape(word.groupName.ifBlank { S.common.defaultGroupFallback }),
                                csvEscape(word.text),
                                csvEscape(word.remark),
                                csvEscape(word.speakerNote),
                                csvEscape(actualWavFileName),
                                csvEscape(actualTgFileName),
                                totalDurMs.toString(),
                                voicedDurMs.toString(),
                                csvEscape(word.qualityStatus ?: "正常"),
                                String.format(Locale.US, "%.2f", safeFloat(intensityDbfs, -96f)),
                                String.format(Locale.US, "%.2f", safeFloat(peakDbfs, -96f)),
                                String.format(Locale.US, "%.2f", safeFloat(snrDb, 0f)),
                                String.format(Locale.US, "%.2f", safeFloat(pitchMean, 0f)),
                                String.format(Locale.US, "%.2f", safeFloat(pitchMin, 0f)),
                                String.format(Locale.US, "%.2f", safeFloat(pitchMax, 0f)),
                                csvEscape(chaoTone),
                                csvEscape(toneCode),
                                String.format(Locale.US, "%.3f", safeFloat(jitter, 0f)),
                                String.format(Locale.US, "%.3f", safeFloat(shimmer, 0f)),
                                csvEscape(f1Str),
                                csvEscape(f2Str),
                                csvEscape(f3Str),
                                csvEscape(vowelTypesStr),
                                csvEscape(f1OnsetStr),
                                csvEscape(f2OnsetStr),
                                csvEscape(f1TargetStr),
                                csvEscape(f2TargetStr),
                                csvEscape(barkSpanStr),
                                if (isChecked) "YES" else "NO",
                                csvEscape(canonicalChao),
                                csvEscape(canonicalCatName),
                                if (isOutlier) "YES" else "NO"
                            ).joinToString(",")
                        ).append("\n")
                        csvRowCount++
                    }
                }
            }

            // Always write PhonTracer-compliant `project.json` at the root of the archive
            val teprojJsonObj = buildPhonTracerProjectJson(
                project = project,
                speakerWordPairs = speakerWordPairs,
                exportedAudioRecords = exportedAudioRecords,
                preferredActiveSpeakerId = preferredActiveSpeakerId ?: project.speakerId,
                exportedImagePathsByWordId = exportedImagePathsByWordId,
                exportedImagePathsByOriginalIndex = exportedImagePathsByIndex
            )
            putUniqueEntry("project.json")
            zipOut.write(teprojJsonObj.toString(2).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            if (!options.exportAsTeproj && options.includeAnalysis) {
                putUniqueEntry("analysis_results.json")
                zipOut.write(analysisArray.toString(2).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                val xlsxBytes = XlsxSpreadsheetWriter.toByteArray(
                    listOf(
                        XlsxSpreadsheetWriter.SheetSpec(
                            name = "acoustic_summary",
                            headers = xlsxSummaryHeaders,
                            rows = xlsxSummaryRows
                        )
                    )
                )
                putUniqueEntry("acoustic_summary.xlsx")
                zipOut.write(xlsxBytes)
                zipOut.closeEntry()
            }

            val exportedSpeakerCount = speakerWordPairs.count { pair ->
                pair.second.any { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
            }

            val msg = if (options.exportAsTeproj) {
                if (wavCount == 0) {
                    buildString {
                        append(S.zip.teprojNoAudioIntro)
                        if (imageCount > 0) append(S.zip.teprojImageCountSuffix.withArgs("count" to imageCount))
                        append(S.zip.teprojNoAudioSuffix)
                    }
                } else {
                    buildString {
                        append(S.zip.teprojSuccessPrefix.withArgs("count" to wavCount))
                        if (textGridCount > 0) append(S.zip.teprojTextGridCountSuffix.withArgs("count" to textGridCount))
                        if (imageCount > 0) append(S.zip.teprojImageCountSuffix.withArgs("count" to imageCount))
                    }
                }
            } else if (wavCount == 0 && textGridCount == 0 && csvRowCount == 0) {
                buildString {
                    append(S.zip.zipNoAudioPrefix)
                    if (imageCount > 0) append(S.zip.zipNoAudioImageSuffix.withArgs("count" to imageCount))
                    append(S.zip.zipNoAudioSuffix)
                }
            } else {
                buildString {
                    append(S.zip.zipSuccessPrefix)
                    val items = mutableListOf<String>()
                    if (wavCount > 0) items.add(S.zip.zipWavPart.withArgs("count" to wavCount))
                    if (textGridCount > 0) items.add(S.zip.zipTextGridPart.withArgs("count" to textGridCount))
                    if (csvRowCount > 0) items.add(S.zip.zipCsvPart.withArgs("count" to csvRowCount))
                    if (imageCount > 0) items.add(S.zip.zipImagesPart.withArgs("count" to imageCount))
                    items.add("PhonTracer project.json")
                    append(items.joinToString("、"))
                }
            }

            return ExportSummary(
                success = true,
                wavCount = wavCount,
                textGridCount = textGridCount,
                csvRowCount = csvRowCount,
                speakerCount = exportedSpeakerCount,
                isTeproj = options.exportAsTeproj,
                message = msg
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return ExportSummary(
                success = false,
                isTeproj = options.exportAsTeproj,
                message = S.zip.exportFailedPrefix.withArgs("msg" to (e.localizedMessage ?: S.zip.writeErrorFallback))
            )
        } finally {
            try {
                zipOut?.flush()
                zipOut?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Packages the project in Simple Mode:
     * - Clean Chinese folder `录音文件/` (flat `001_词.wav` for single speaker, `录音文件/<发音人>/001_词.wav` for multi-speaker)
     * - Optional `词卡图片/` folder when `options.includeWordImages && project.enablePicturePrompts`
     * - Optional `.TextGrid` alongside audio if `options.includePraatTextGrid` is enabled
     * - Root `录音汇总表.xlsx` genuine Excel workbook (Sheet 1 `录音汇总表` + Sheet 2 `完整调查字表`)
     * - Zero engineering `.json` configuration files
     */
    private fun zipProjectSimpleMode(
        context: Context,
        project: Project,
        speakerWordPairs: List<Pair<Speaker?, List<Word>>>,
        options: ExportOptions,
        targetStream: OutputStream
    ): ExportSummary {
        var zipOut: ZipOutputStream? = null
        var wavCount = 0
        var textGridCount = 0
        var csvRowCount = 0
        var imageCount = 0

        try {
            zipOut = ZipOutputStream(BufferedOutputStream(targetStream)).apply {
                setMethod(ZipOutputStream.DEFLATED)
                setLevel(Deflater.DEFAULT_COMPRESSION)
            }
            val usedZipPaths = HashSet<String>()

            fun allocateUniqueZipPath(rawPath: String): String {
                val cleanPath = normalizeZipEntryPath(rawPath)
                var candidate = cleanPath
                var suffix = 2
                while (!usedZipPaths.add(candidate)) {
                    val slashIdx = cleanPath.lastIndexOf('/')
                    val dirPart = if (slashIdx >= 0) cleanPath.substring(0, slashIdx + 1) else ""
                    val filePart = if (slashIdx >= 0) cleanPath.substring(slashIdx + 1) else cleanPath
                    val dotIdx = filePart.lastIndexOf('.')
                    candidate = if (dotIdx > 0) {
                        "$dirPart${filePart.substring(0, dotIdx)}_$suffix${filePart.substring(dotIdx)}"
                    } else {
                        "$dirPart${filePart}_$suffix"
                    }
                    suffix++
                }
                return candidate
            }

            fun putUniqueEntry(rawPath: String): String {
                val uniquePath = allocateUniqueZipPath(rawPath)
                zipOut.putNextEntry(ZipEntry(uniquePath))
                return uniquePath
            }

            val projectDir = File(context.getExternalFilesDir(null), "project_${project.id}")
            val spPrefs = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
            val useSpeakerSubfolders = options.exportAllSpeakers || speakerWordPairs.size > 1

            val referenceWords = speakerWordPairs
                .firstOrNull { it.second.isNotEmpty() }
                ?.second
                ?.sortedBy { it.originalIndex }
                ?: emptyList()
            val exportedImagePathsByIndex = LinkedHashMap<Int, String>()

            if (options.includeWordImages && project.enablePicturePrompts) {
                val imgRootFolder = S.zip.simpleImagesFolder
                for (word in referenceWords) {
                    if (word.imagePath.isBlank()) continue
                    val imgFile = WordImageManager.resolveWordImageFile(context, project.id, word.imagePath)
                    if (imgFile == null || !imgFile.exists() || imgFile.length() <= 0L) continue
                    val ext = imgFile.extension.lowercase(Locale.US).ifBlank { "jpg" }
                    val idxStr = String.format(Locale.US, "%03d", word.originalIndex + 1)
                    val safeWord = sanitizeFileNameComponent(word.text, "word_${word.id}").take(24)
                    val zipImgPath = putUniqueEntry("$imgRootFolder/${idxStr}_${safeWord}.${ext}")
                    FileInputStream(imgFile).use { fis ->
                        fis.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                    imageCount++
                    exportedImagePathsByIndex[word.originalIndex] = zipImgPath
                }
            }

            val summaryHeaders = S.zip.simpleCsvHeader.split(",").map { it.trim() }
            val summaryRows = mutableListOf<List<Any?>>()

            val audioRootFolder = S.zip.simpleAudioFolder
            for ((speaker, words) in speakerWordPairs) {
                val speakerId = speaker?.id ?: 1L
                val speakerCode = String.format(Locale.US, "SPK%02d", speakerId)
                val speakerName = speaker?.name?.ifBlank { S.export.defaultSpeakerFallbackName } ?: S.export.defaultSpeakerFallbackName
                val safeSpkName = sanitizeFileNameComponent(speakerName, speakerCode).take(24)
                val pMin = spPrefs.getFloat("pitch_min_$speakerId", -1f)
                val pMax = spPrefs.getFloat("pitch_max_$speakerId", -1f)
                val pitchRange = if (pMin > 40f && pMax > pMin + 15f) Pair(pMin, pMax) else null
                val speakerGender = speaker?.gender ?: ""

                val sortedWords = words.sortedBy { it.originalIndex }
                for (word in sortedWords) {
                    val originalFileName = word.recordingFile
                    if (!word.isRecorded || originalFileName.isNullOrEmpty()) continue

                    val file = File(projectDir, originalFileName)
                    if (!file.exists() || file.length() <= 44L) continue

                    val idxStr = String.format(Locale.US, "%03d", word.originalIndex + 1)
                    val safeText = sanitizeFileNameComponent(word.text, "word_${word.id}").take(24)
                    val audioSubDir = if (useSpeakerSubfolders) {
                        "$audioRootFolder/${safeSpkName}/"
                    } else {
                        "$audioRootFolder/"
                    }

                    val exactDurationSec = AcousticAnalyzer.getExactWavDurationSec(file)
                    val actualWavZipPath = putUniqueEntry("$audioSubDir${idxStr}_${safeText}.wav").also {
                        writeSanitizedWavToOutputStream(file, zipOut)
                        zipOut.closeEntry()
                        wavCount++
                    }

                    if (options.includePraatTextGrid) {
                        val parts = extractSyllableLabels(word.text)
                        val segCount = parts.size.coerceAtLeast(1)
                        val analysisResult = AcousticAnalyzer.analyzeByGender(
                            file = file,
                            speakerGender = speakerGender,
                            segmentCount = segCount,
                            pitchRange = pitchRange,
                            segmentLabels = parts
                        )
                        val tgContent = buildPraatTextGrid(
                            word = word,
                            analysis = analysisResult,
                            exactDurationSec = exactDurationSec,
                            includeAcousticTags = false,
                            config = options.textGridConfig
                        )
                        val actualStemPath = actualWavZipPath.substringBeforeLast('.')
                        putUniqueEntry("$actualStemPath.TextGrid")
                        zipOut.write(tgContent.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                        textGridCount++
                    }

                    val relativeAudioDisplay = actualWavZipPath.removePrefix("$audioRootFolder/")
                    summaryRows.add(
                        listOf<Any?>(
                            word.originalIndex + 1,
                            word.groupName.ifBlank { S.common.defaultGroupFallback },
                            word.text,
                            word.remark,
                            word.speakerNote,
                            speakerName,
                            speaker?.dialect ?: "",
                            Math.round(safeDouble(exactDurationSec) * 100.0) / 100.0,
                            relativeAudioDisplay
                        )
                    )
                    csvRowCount++
                }
            }

            val sheets = mutableListOf(
                XlsxSpreadsheetWriter.SheetSpec(
                    name = S.zip.simpleXlsxSummarySheet,
                    headers = summaryHeaders,
                    rows = summaryRows
                )
            )
            if (referenceWords.isNotEmpty()) {
                val wordlistRows = referenceWords.mapIndexed { idx, w ->
                    listOf<Any?>(
                        w.originalIndex + 1,
                        w.groupName.ifBlank { S.common.defaultGroupFallback },
                        w.text,
                        w.remark,
                        exportedImagePathsByIndex[w.originalIndex].orEmpty(),
                        if (idx == 0) project.name else "",
                        if (idx == 0) project.description else ""
                    )
                }
                sheets.add(
                    XlsxSpreadsheetWriter.SheetSpec(
                        name = S.zip.simpleXlsxWordlistSheet,
                        headers = listOf("序号", "分组", "词条", "词表提示", "配图文件", "项目名称", "项目说明"),
                        rows = wordlistRows
                    )
                )
            }

            val xlsxBytes = XlsxSpreadsheetWriter.toByteArray(sheets)
            putUniqueEntry(S.zip.simpleCsvFileName)
            zipOut.write(xlsxBytes)
            zipOut.closeEntry()

            val exportedSpeakerCount = speakerWordPairs.count { pair ->
                pair.second.any { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
            }

            val msg = if (wavCount == 0) {
                S.zip.simpleEmptyCsvMsg
            } else {
                buildString {
                    append(S.zip.simpleSuccessMsg.withArgs("count" to wavCount))
                    if (textGridCount > 0) append(S.zip.simpleTextGridSuffix.withArgs("count" to textGridCount))
                    if (imageCount > 0) append("，${S.zip.zipImagesPart.withArgs("count" to imageCount)}")
                }
            }

            return ExportSummary(
                success = true,
                wavCount = wavCount,
                textGridCount = textGridCount,
                csvRowCount = csvRowCount,
                speakerCount = exportedSpeakerCount,
                isTeproj = false,
                message = msg
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return ExportSummary(
                success = false,
                isTeproj = false,
                message = S.zip.exportFailedPrefix.withArgs("msg" to (e.localizedMessage ?: S.zip.writeErrorFallback))
            )
        } finally {
            try {
                zipOut?.flush()
                zipOut?.close()
            } catch (ignored: Exception) {}
        }
    }

    fun zipProject(
        context: Context,
        project: Project,
        words: List<Word>,
        speaker: Speaker?,
        options: ExportOptions,
        targetStream: OutputStream
    ): Boolean {
        return zipProjectMultiSpeaker(
            context = context,
            project = project,
            speakerWordPairs = listOf(speaker to words),
            options = options,
            targetStream = targetStream,
            preferredActiveSpeakerId = speaker?.id
        ).success
    }

    /**
     * Builds the complete PhonTracer v1.0 `project.json` object.
     * Strictly guarantees:
     * - No NaN or Infinity values anywhere
     * - `groups` and `speakers[spk_id].project_groups` preserve exact group order
     * - Every item's `group` matches its group's `name` (`"默认组"` when ungrouped)
     * - Recorded items have `path` starting with `"audio/"`, `0.0 <= start < end <= macro_end`,
     *   matching `raw_start`/`raw_end`, `chars_bounds`, and `inner_splits`
     * - Unrecorded items omit `"path"` and are excluded from `pending_batch_paths`
     * - `pitch_data_file` and `formant_data_file` are omitted so PhonTracer computes F0/formants on open
     */
    fun buildPhonTracerProjectJson(
        project: Project,
        speakerWordPairs: List<Pair<Speaker?, List<Word>>>,
        exportedAudioRecords: List<ExportedWordAudioRecord>,
        preferredActiveSpeakerId: Long? = null,
        saveTimeIso: String? = null,
        exportedImagePathsByWordId: Map<Long, String> = emptyMap(),
        exportedImagePathsByOriginalIndex: Map<Int, String> = emptyMap()
    ): JSONObject {
        val timestamp = saveTimeIso ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())

        val referenceWords = speakerWordPairs
            .firstOrNull { it.second.isNotEmpty() }
            ?.second
            ?.sortedBy { it.originalIndex }
            ?: emptyList()

        // Deterministic 3-digit item ID ("001", "002", ...) per word
        val wordIdToItemId = HashMap<Long, String>(referenceWords.size * 2)
        val originalIndexToItemId = HashMap<Int, String>(referenceWords.size * 2)
        val usedItemIds = HashSet<String>(referenceWords.size * 2)

        for ((seqIdx, w) in referenceWords.withIndex()) {
            var candidate = String.format(Locale.US, "%03d", w.originalIndex + 1)
            if (!usedItemIds.add(candidate)) {
                candidate = String.format(Locale.US, "%03d", seqIdx + 1)
                var extra = 1
                while (!usedItemIds.add(candidate)) {
                    candidate = String.format(Locale.US, "%03d_%d", seqIdx + 1, extra++)
                }
            }
            if (w.id != 0L) {
                wordIdToItemId[w.id] = candidate
            }
            originalIndexToItemId[w.originalIndex] = candidate
        }

        fun resolveItemId(word: Word, fallbackIndex: Int): String {
            if (word.id != 0L) {
                wordIdToItemId[word.id]?.let { return it }
            }
            originalIndexToItemId[word.originalIndex]?.let { return it }
            return String.format(Locale.US, "%03d", (word.originalIndex.takeIf { it >= 0 } ?: fallbackIndex) + 1)
        }

        // Preserve exact group order from the word list
        val groupedWordsMap = LinkedHashMap<String, MutableList<Pair<String, Word>>>()
        referenceWords.forEachIndexed { idx, word ->
            val groupName = word.groupName.trim().ifEmpty { S.common.defaultGroupFallback }
            val itemId = resolveItemId(word, idx)
            groupedWordsMap.getOrPut(groupName) { mutableListOf() }.add(itemId to word)
        }
        val orderedGroupNames = groupedWordsMap.keys.toList()

        val groupsJsonArray = JSONArray()
        orderedGroupNames.forEachIndexed { grpIdx, groupName ->
            val groupItems = groupedWordsMap[groupName] ?: emptyList()
            val itemsJsonArray = JSONArray()
            for ((itemId, word) in groupItems) {
                val parsedMeta = parseWordRemarkMetadata(word.remark, groupName)
                val exportedImgPath = (if (word.id != 0L) exportedImagePathsByWordId[word.id] else null)
                    ?: exportedImagePathsByOriginalIndex[word.originalIndex]
                val metaObj = parsedMeta.toMetaJsonObject().apply {
                    if (!exportedImgPath.isNullOrBlank()) {
                        put("image_path", exportedImgPath)
                    }
                }
                val itemObj = JSONObject().apply {
                    put("id", itemId)
                    put("label", word.text.trim().ifEmpty { "token" })
                    put("note", word.remark.trim())
                    if (!exportedImgPath.isNullOrBlank()) {
                        put("image_path", exportedImgPath)
                    }
                    put("tags", parsedMeta.toTagsJsonArray())
                    put("aliases", JSONArray())
                    put("meta", metaObj)
                    put("metadata_source", "phonacq")
                }
                itemsJsonArray.put(itemObj)
            }

            val grpObj = JSONObject().apply {
                put("id", String.format(Locale.US, "grp_%02d", grpIdx + 1))
                put("name", groupName)
                put("note", "")
                put("tags", JSONArray().apply { put(groupName) })
                put("meta", JSONObject())
                put("items", itemsJsonArray)
            }
            groupsJsonArray.put(grpObj)
        }

        // Index exported audio records by (speakerId, wordId) and fallback (speakerId, originalIndex)
        val audioBySpkAndWordId = HashMap<Pair<Long, Long>, ExportedWordAudioRecord>()
        val audioBySpkAndIndex = HashMap<Pair<Long, Int>, ExportedWordAudioRecord>()
        for (rec in exportedAudioRecords) {
            if (rec.wordId != 0L) {
                audioBySpkAndWordId[rec.speakerId to rec.wordId] = rec
            }
            audioBySpkAndIndex[rec.speakerId to rec.originalIndex] = rec
        }

        val speakersJsonObj = JSONObject()
        val effectivePairs = if (speakerWordPairs.isNotEmpty()) {
            speakerWordPairs
        } else {
            listOf(null to referenceWords)
        }

        var resolvedActiveSpeakerCode = ""
        val preferredCode = preferredActiveSpeakerId?.let { String.format(Locale.US, "SPK%02d", it) }

        for ((speaker, spkWords) in effectivePairs) {
            val spkId = speaker?.id ?: 1L
            val spkCode = String.format(Locale.US, "SPK%02d", spkId)
            if (resolvedActiveSpeakerCode.isEmpty() || spkCode == preferredCode) {
                resolvedActiveSpeakerCode = spkCode
            }

            val sortedWords = (if (spkWords.isNotEmpty()) spkWords else referenceWords).sortedBy { it.originalIndex }
            val pendingBatchPathsArr = JSONArray()
            val speakerItemsObj = JSONObject()
            var firstRecordedItemId: String? = null
            var importIndex = 0

            sortedWords.forEachIndexed { idx, word ->
                val itemId = resolveItemId(word, idx)
                val groupName = word.groupName.trim().ifEmpty { S.common.defaultGroupFallback }
                val parsedMeta = parseWordRemarkMetadata(word.remark, groupName)
                val audioRec = (if (word.id != 0L) audioBySpkAndWordId[spkId to word.id] else null)
                    ?: audioBySpkAndIndex[spkId to word.originalIndex]

                if (audioRec != null && audioRec.zipAudioPath.startsWith("audio/")) {
                    if (firstRecordedItemId == null) {
                        firstRecordedItemId = itemId
                    }
                    pendingBatchPathsArr.put(audioRec.zipAudioPath)

                    val seg = audioRec.segmentation
                    val safeDuration = safeDouble(seg.durationSec, 0.5000).coerceAtLeast(0.0500)
                    var safeStart = safeDouble(seg.startSec, 0.0).coerceIn(0.0, safeDuration)
                    var safeEnd = safeDouble(seg.endSec, safeDuration).coerceIn(safeStart, safeDuration)
                    if (safeEnd <= safeStart) {
                        safeStart = 0.0
                        safeEnd = safeDuration
                    }

                    val charsBoundsArr = JSONArray()
                    val validBounds = seg.charsBounds.filter { (cs, ce) ->
                        cs.isFinite() && ce.isFinite() && ce > cs
                    }
                    if (validBounds.isNotEmpty()) {
                        for ((cs, ce) in validBounds) {
                            val clampedS = safeDouble(cs, safeStart).coerceIn(0.0, safeDuration)
                            val clampedE = safeDouble(ce, safeEnd).coerceIn(clampedS, safeDuration)
                            if (clampedE > clampedS) {
                                charsBoundsArr.put(JSONArray().apply {
                                    put(clampedS)
                                    put(clampedE)
                                })
                            }
                        }
                    }
                    if (charsBoundsArr.length() == 0) {
                        charsBoundsArr.put(JSONArray().apply {
                            put(safeStart)
                            put(safeEnd)
                        })
                    }

                    val innerSplitsArr = JSONArray()
                    for (splitSec in seg.innerSplits) {
                        if (splitSec.isFinite() && splitSec > safeStart && splitSec < safeEnd) {
                            innerSplitsArr.put(safeDouble(splitSec))
                        }
                    }

                    val effectiveItemNote = word.speakerNote.trim().ifEmpty { word.remark.trim() }
                    val spkItemObj = JSONObject().apply {
                        put("id", itemId)
                        put("label", word.text.trim().ifEmpty { "token" })
                        put("group", groupName)
                        put("path", audioRec.zipAudioPath)
                        put("macro_start", 0.0)
                        put("macro_end", safeDuration)
                        put("start", safeStart)
                        put("end", safeEnd)
                        put("raw_start", safeStart)
                        put("raw_end", safeEnd)
                        put("chars_bounds", charsBoundsArr)
                        put("inner_splits", innerSplitsArr)
                        put("note", effectiveItemNote)
                        if (word.speakerNote.isNotBlank()) {
                            put("speaker_note", word.speakerNote.trim())
                        }
                        put("tags", parsedMeta.toTagsJsonArray())
                        put("aliases", JSONArray())
                        put("meta", parsedMeta.toMetaJsonObject())
                        put("metadata_source", "phonacq")
                        put("import_index", importIndex)
                    }
                    speakerItemsObj.put(itemId, spkItemObj)
                    importIndex++
                } else {
                    // Unrecorded word: MUST NOT include "path" field
                    val effectiveItemNote = word.speakerNote.trim().ifEmpty { word.remark.trim() }
                    val spkItemObj = JSONObject().apply {
                        put("id", itemId)
                        put("label", word.text.trim().ifEmpty { "token" })
                        put("group", groupName)
                        put("note", effectiveItemNote)
                        if (word.speakerNote.isNotBlank()) {
                            put("speaker_note", word.speakerNote.trim())
                        }
                        put("tags", parsedMeta.toTagsJsonArray())
                        put("aliases", JSONArray())
                        put("meta", parsedMeta.toMetaJsonObject())
                        put("metadata_source", "phonacq")
                    }
                    speakerItemsObj.put(itemId, spkItemObj)
                }
            }

            val lastSelectedIid: Any = firstRecordedItemId
                ?: (if (sortedWords.isNotEmpty()) resolveItemId(sortedWords.first(), 0) else JSONObject.NULL)

            val projectGroupsArr = JSONArray().apply {
                orderedGroupNames.forEach { put(it) }
            }

            val lastParamsObj = JSONObject().apply {
                put("pts", 11)
                put("db", 15.0)
                put("skip_front", 0.03)
                put("pitch_floor", 75)
                put("pitch_ceiling", 600)
                put("voicing_threshold", 0.25)
                put("analysis_mode", "formant")
                put("formant_max_hz", 5500.0)
                put("formant_count", 5)
                put("formant_window_length", 0.025)
                put("formant_pre_emphasis", 50.0)
                put("formant_sample_strategy", S.zip.phonTracerSampleStrategy)
                put("show_f3", false)
            }

            val speakerObj = JSONObject().apply {
                put("id", spkCode)
                put("name", speaker?.name?.trim()?.ifEmpty { S.project.seedSpeakerName } ?: S.project.seedSpeakerName)
                put("tab_mode", S.zip.phonTracerTabMode)
                put("long_audio_path", JSONObject.NULL)
                put("pending_batch_paths", pendingBatchPathsArr)
                put("current_macro_segments", JSONArray())
                put("manual_segments", JSONObject.NULL)
                put("last_selected_iid", lastSelectedIid)
                put("project_groups", projectGroupsArr)
                put("last_params", lastParamsObj)
                put("items", speakerItemsObj)
            }
            speakersJsonObj.put(spkCode, speakerObj)
        }

        if (resolvedActiveSpeakerCode.isEmpty()) {
            resolvedActiveSpeakerCode = "SPK01"
        }

        val wordlistMetaObj = JSONObject().apply {
            put("title", project.name.trim().ifEmpty { S.zip.defaultWordlistTitle })
            put("note", project.description.trim())
            put("enable_picture_prompts", project.enablePicturePrompts)
            put("tags", JSONArray())
        }

        return JSONObject().apply {
            put("version", "1.0")
            put("software_version", com.example.BuildConfig.VERSION_NAME)
            put("report_format_version", "1.0")
            put("save_time", timestamp)
            put("active_speaker_id", resolvedActiveSpeakerCode)
            put("export_numbering_rule", "continuous")
            put("trim_silence", true)
            put("wordlist_meta", wordlistMetaObj)
            put("groups", groupsJsonArray)
            put("speakers", speakersJsonObj)
        }
    }

    /**
     * Parses `word.remark` and `groupName` into structured `ipa`, `gloss`, `extra`, and `tags`
     * for PhonTracer `meta` and `tags` fields.
     */
    fun parseWordRemarkMetadata(rawRemark: String, groupName: String = ""): ParsedWordMetadata {
        val tagsSet = LinkedHashSet<String>()
        if (groupName.isNotBlank() && groupName != "默认组" && groupName != S.common.defaultGroupFallback) {
            tagsSet.add(groupName)
        }

        // Extract #hashtags (e.g. #单元音 #前元音) into tagsSet and strip them from remark text
        val hashtagRegex = Regex("#([^\\s#,;，；|｜]+)")
        hashtagRegex.findAll(rawRemark).forEach { match ->
            val tag = match.groupValues[1].trim()
            if (tag.isNotEmpty()) {
                tagsSet.add(tag)
            }
        }
        val withoutHashtags = rawRemark.replace(hashtagRegex, "").trim()

        for (knownTag in KNOWN_FIELD_TAGS) {
            if (withoutHashtags.contains(knownTag)) {
                tagsSet.add(knownTag)
            }
        }

        fun cleanGlossToken(g: String): String {
            val t = g.trim()
            return if ((t.startsWith("(") && t.endsWith(")")) || (t.startsWith("（") && t.endsWith("）"))) {
                t.substring(1, t.length - 1).trim()
            } else {
                t
            }
        }

        if (withoutHashtags.isEmpty()) {
            return ParsedWordMetadata(ipa = "", gloss = "", tags = tagsSet.toList())
        }

        // Check pipe / semicolon delimiter first (e.g. "i | clothes" or "pa55; 爸爸")
        val explicitSeparators = listOf("|", "｜", ";", "；")
        for (sep in explicitSeparators) {
            if (withoutHashtags.contains(sep)) {
                val parts = withoutHashtags.split(sep).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.isNotEmpty()) {
                    val first = parts[0].trim('[', ']', '/')
                    val second = cleanGlossToken(parts.getOrNull(1) ?: "")
                    val extra = if (parts.size > 2) parts.drop(2).joinToString(" | ") else ""
                    return if (IPA_OR_ASCII_TOKEN_REGEX.matches(first)) {
                        ParsedWordMetadata(ipa = first, gloss = second, extra = extra, tags = tagsSet.toList())
                    } else {
                        ParsedWordMetadata(ipa = "", gloss = cleanGlossToken(parts[0]), extra = parts.drop(1).joinToString(" | "), tags = tagsSet.toList())
                    }
                }
            }
        }

        // Check space-separated tokens (e.g. "[i] (clothes)" or "i 展唇前高")
        val tokens = withoutHashtags.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            val firstClean = tokens[0].trim('[', ']', '/', ',', '，')
            if (firstClean.isNotEmpty() && IPA_OR_ASCII_TOKEN_REGEX.matches(firstClean)) {
                val restGloss = cleanGlossToken(tokens.drop(1).joinToString(" "))
                return ParsedWordMetadata(ipa = firstClean, gloss = restGloss, tags = tagsSet.toList())
            }
        }

        val singleClean = withoutHashtags.trim('[', ']', '/')
        return if (singleClean.isNotEmpty() && IPA_OR_ASCII_TOKEN_REGEX.matches(singleClean)) {
            ParsedWordMetadata(ipa = singleClean, gloss = "", tags = tagsSet.toList())
        } else {
            ParsedWordMetadata(ipa = "", gloss = cleanGlossToken(withoutHashtags), tags = tagsSet.toList())
        }
    }

    /**
     * Streams a `.wav` file to [out] while validating and repairing its 44-byte RIFF PCM header
     * so that `nAvgBytesPerSec == sample_rate * channels * (bits_per_sample // 8)` and
     * `nBlockAlign == channels * (bits_per_sample // 8)` strictly hold for Praat / parselmouth.
     */
    fun writeSanitizedWavToOutputStream(file: File, out: OutputStream) {
        val fileLen = file.length()
        if (fileLen <= 44L) {
            FileInputStream(file).use { it.copyTo(out) }
            return
        }

        FileInputStream(file).use { fis ->
            val header = ByteArray(44)
            var readTotal = 0
            while (readTotal < 44) {
                val r = fis.read(header, readTotal, 44 - readTotal)
                if (r <= 0) break
                readTotal += r
            }

            if (readTotal == 44 &&
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                header[8] == 'W'.code.toByte() && header[9] == 'A'.code.toByte() &&
                header[10] == 'V'.code.toByte() && header[11] == 'E'.code.toByte() &&
                header[12] == 'f'.code.toByte() && header[13] == 'm'.code.toByte() &&
                header[14] == 't'.code.toByte() && header[15] == ' '.code.toByte()
            ) {
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = (buf.getShort(20).toInt() and 0xFFFF).let { if (it == 1) 1 else 1 }
                val channels = (buf.getShort(22).toInt() and 0xFFFF).let { if (it in 1..8) it else 1 }
                val sampleRate = buf.getInt(24).let { if (it in 8000..192000) it else 44100 }
                val bitsPerSample = (buf.getShort(34).toInt() and 0xFFFF).let {
                    if (it in listOf(8, 16, 24, 32)) it else 16
                }
                val bytesPerSample = bitsPerSample / 8
                val blockAlign = channels * bytesPerSample
                val avgBytesPerSec = sampleRate * channels * bytesPerSample

                // Patch fmt chunk fields to guarantee Praat / parselmouth strict header check
                buf.putShort(20, audioFormat.toShort())
                buf.putShort(22, channels.toShort())
                buf.putInt(24, sampleRate)
                buf.putInt(28, avgBytesPerSec)
                buf.putShort(32, blockAlign.toShort())
                buf.putShort(34, bitsPerSample.toShort())

                if (header[36] == 'd'.code.toByte() && header[37] == 'a'.code.toByte() &&
                    header[38] == 't'.code.toByte() && header[39] == 'a'.code.toByte()
                ) {
                    val pcmDataBytes = (fileLen - 44L).coerceAtLeast(0L).toInt()
                    buf.putInt(4, pcmDataBytes + 36)
                    buf.putInt(40, pcmDataBytes)
                }
            }

            out.write(header, 0, readTotal)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
        }
    }

    fun extractSyllableLabels(text: String): List<String> {
        return AcousticAnalyzer.extractSyllableUnits(text)
    }

    /**
     * Computes clean, snapped word and syllable boundaries shared between Praat `.TextGrid`
     * and PhonTracer `project.json` (`start`, `end`, `chars_bounds`, `inner_splits`).
     */
    fun resolveWordSegmentation(
        word: Word,
        analysis: AcousticAnalyzer.AnalysisResult?,
        exactDurationSec: Double,
        decimals: Int = 4
    ): ResolvedWordSegmentation {
        fun roundN(v: Double): Double = TextGridFormatConfig.roundToDecimals(v, decimals)

        val rawTotalSec = when {
            exactDurationSec.isFinite() && exactDurationSec > 0.02 -> exactDurationSec
            analysis != null && analysis.totalDurationMs > 20 -> analysis.totalDurationMs / 1000.0
            word.durationMs > 20 -> word.durationMs / 1000.0
            else -> 0.5000
        }
        val totalSec = roundN(rawTotalSec.coerceAtLeast(0.0500))
        val segs = analysis?.segments ?: emptyList()

        var speechStart = if (segs.isNotEmpty() && segs.first().startSec.isFinite()) {
            roundN(segs.first().startSec.coerceIn(0.0, totalSec))
        } else {
            0.0
        }
        var speechEnd = if (segs.isNotEmpty() && segs.last().endSec.isFinite()) {
            roundN(segs.last().endSec.coerceIn(speechStart, totalSec))
        } else {
            totalSec
        }

        // Snap near-edge boundaries to 0.0 or totalSec so we never create micro-intervals < 15ms
        if (speechStart < 0.0150) speechStart = 0.0
        if (totalSec - speechEnd < 0.0150) speechEnd = totalSec
        if (speechEnd - speechStart < 0.0200) {
            speechStart = 0.0
            speechEnd = totalSec
        }

        val parts = extractSyllableLabels(word.text)
        val charIntervals = mutableListOf<Triple<Double, Double, String>>()

        if (parts.size <= 1 && segs.size <= 1) {
            val singleLabel = segs.firstOrNull()?.label?.ifBlank { null }
                ?: parts.firstOrNull()?.ifBlank { "token" }
                ?: word.text.trim().ifBlank { "token" }
            charIntervals.add(Triple(speechStart, speechEnd, singleLabel))
        } else if (segs.isNotEmpty()) {
            var cursor = speechStart
            segs.forEachIndexed { idx, seg ->
                val lbl = when {
                    segs.size == parts.size -> parts.getOrNull(idx)?.ifBlank { seg.label } ?: seg.label
                    seg.label.isNotBlank() -> seg.label
                    else -> parts.getOrNull(idx) ?: S.common.syllableFallback.withArgs("index" to (idx + 1))
                }.ifBlank { S.common.syllableFallback.withArgs("index" to (idx + 1)) }
                val rawEnd = if (idx == segs.lastIndex) {
                    speechEnd
                } else {
                    roundN(seg.endSec.coerceIn(cursor + 0.0100, speechEnd))
                }
                val safeEnd = if (rawEnd <= cursor) {
                    roundN((cursor + 0.0100).coerceAtMost(speechEnd))
                } else {
                    rawEnd
                }
                if (safeEnd > cursor) {
                    charIntervals.add(Triple(cursor, safeEnd, lbl))
                    cursor = safeEnd
                }
            }
            if (charIntervals.isEmpty()) {
                charIntervals.add(Triple(speechStart, speechEnd, word.text.trim().ifBlank { "token" }))
            } else if (charIntervals.last().second < speechEnd) {
                val last = charIntervals.removeAt(charIntervals.lastIndex)
                charIntervals.add(Triple(last.first, speechEnd, last.third))
            }
        } else {
            val span = (speechEnd - speechStart).coerceAtLeast(0.0200)
            val step = span / parts.size.coerceAtLeast(1)
            var cursor = speechStart
            parts.forEachIndexed { idx, partLabel ->
                val nextCursor = if (idx == parts.lastIndex) {
                    speechEnd
                } else {
                    roundN(speechStart + (idx + 1) * step)
                }
                if (nextCursor > cursor) {
                    charIntervals.add(Triple(cursor, nextCursor, partLabel))
                    cursor = nextCursor
                }
            }
            if (charIntervals.isEmpty()) {
                charIntervals.add(Triple(speechStart, speechEnd, word.text.trim().ifBlank { "token" }))
            }
        }

        val charsBounds = charIntervals.map { it.first to it.second }
        val innerSplits = if (charIntervals.size >= 2) {
            charIntervals.dropLast(1).map { it.second }
        } else {
            emptyList()
        }

        return ResolvedWordSegmentation(
            durationSec = totalSec,
            startSec = speechStart,
            endSec = speechEnd,
            charIntervals = charIntervals,
            charsBounds = charsBounds,
            innerSplits = innerSplits
        )
    }

    /**
     * Generates a strictly Praat-compliant `ooTextFile` TextGrid (UTF-8) using the user's
     * configured `TextGridFormatConfig` (defaults to `UNIVERSAL_STANDARD`: `words` + `syllables` + `tones`,
     * 6-decimal high precision, empty silence intervals, Long `ooTextFile`).
     *
     * Guarantees Praat's strict invariants:
     * - `xmin = 0` and `xmax = totalSec` across the root and all IntervalTiers
     * - Every tier starts at `0` and ends at `xmax`
     * - Every interval has `xmax > xmin`
     * - Adjacent intervals share the exact same formatted boundary string (`intervals[i].xmax == intervals[i+1].xmin`)
     */
    fun buildPraatTextGrid(
        word: Word,
        analysis: AcousticAnalyzer.AnalysisResult?,
        exactDurationSec: Double,
        includeAcousticTags: Boolean = false,
        config: TextGridFormatConfig = TextGridFormatConfig()
    ): String {
        val decimals = config.effectiveTimeDecimals()
        val segInfo = resolveWordSegmentation(
            word = word,
            analysis = analysis,
            exactDurationSec = exactDurationSec,
            decimals = decimals
        )
        val tiers = TextGridFormatConfig.buildConfiguredTiers(
            config = config,
            word = word,
            analysis = analysis,
            domainXmax = segInfo.durationSec,
            speechStartRaw = segInfo.startSec,
            speechEndRaw = segInfo.endSec,
            rawCharIntervals = segInfo.charIntervals,
            includeAcousticTags = includeAcousticTags,
            quantizeDecimals = decimals
        )
        return TextGridFormatConfig.renderPraatTextGridText(
            config = config,
            totalSecRaw = segInfo.durationSec,
            tiers = tiers
        )
    }

    fun transliterateNoteForFileName(rawNote: String): String {
        if (rawNote.isBlank()) return ""
        val firstPart = rawNote
            .substringBefore('|')
            .substringBefore('｜')
            .substringBefore(';')
            .substringBefore('；')
            .substringBefore('#')
            .trim()
        val sb = StringBuilder(firstPart.length + 4)
        for (ch in firstPart) {
            when (ch) {
                '˥' -> sb.append('5')
                '˦' -> sb.append('4')
                '˧' -> sb.append('3')
                '˨' -> sb.append('2')
                '˩' -> sb.append('1')
                ' ', '\t', '\n', '\r', '·', '・', '/', '\\', ':', '*', '?', '"', '<', '>' -> {
                    if (sb.isNotEmpty() && sb.last() != '_') sb.append('_')
                }
                in 'a'..'z', in 'A'..'Z', in '0'..'9', '_', '-' -> sb.append(ch)
                else -> {
                    val type = Character.getType(ch).toByte()
                    val isLetterOrMark = Character.isLetterOrDigit(ch) ||
                        type == Character.MODIFIER_LETTER ||
                        type == Character.MODIFIER_SYMBOL ||
                        type == Character.NON_SPACING_MARK ||
                        type == Character.COMBINING_SPACING_MARK
                    if (isLetterOrMark) {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString().trim('_').take(48).trim('_')
    }

    private fun parseCsvRowsSimple(csvContent: String): List<List<String>> {
        val clean = csvContent.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val rows = ArrayList<List<String>>()
        val currentRow = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < clean.length && clean[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(sb.toString())
                        sb.setLength(0)
                    }
                    '\n' -> {
                        currentRow.add(sb.toString())
                        sb.setLength(0)
                        if (currentRow.any { it.isNotBlank() }) {
                            rows.add(ArrayList(currentRow))
                        }
                        currentRow.clear()
                    }
                    else -> sb.append(ch)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(sb.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(ArrayList(currentRow))
            }
        }
        return rows
    }

    /**
     * Guarantees Praat's continuity requirement:
     * - First interval starts at 0.0000
     * - Each subsequent interval starts at the exact `xmax` of the previous interval
     * - Last interval ends at `totalSec`
     * - Every interval has `xmax > xmin`
     */
    private fun enforceStrictContinuity(
        intervals: List<Triple<Double, Double, String>>,
        totalSec: Double
    ): List<Triple<Double, Double, String>> {
        if (intervals.isEmpty()) {
            return listOf(Triple(0.0, totalSec, ""))
        }
        val cleaned = mutableListOf<Triple<Double, Double, String>>()
        var cursor = 0.0
        for (i in intervals.indices) {
            val rawEnd = if (i == intervals.lastIndex) totalSec else round4(intervals[i].second.coerceIn(cursor, totalSec))
            if (rawEnd - cursor >= 0.0010) {
                cleaned.add(Triple(cursor, rawEnd, intervals[i].third))
                cursor = rawEnd
            }
        }
        if (cleaned.isEmpty()) {
            return listOf(Triple(0.0, totalSec, intervals.first().third))
        }
        val last = cleaned.last()
        if (last.second != totalSec) {
            cleaned[cleaned.lastIndex] = Triple(last.first, totalSec, last.third)
        }
        return cleaned
    }

    private fun appendIntervalTier(
        sb: StringBuilder,
        tierIndex: Int,
        tierName: String,
        totalSec: Double,
        intervals: List<Triple<Double, Double, String>>
    ) {
        sb.append("    item [$tierIndex]:\n")
        sb.append("        class = \"IntervalTier\"\n")
        sb.append("        name = \"$tierName\"\n")
        sb.append(String.format(Locale.US, "        xmin = 0.0000\n        xmax = %.4f\n", totalSec))
        sb.append("        intervals: size = ${intervals.size}\n")
        intervals.forEachIndexed { idx, (s, e, lbl) ->
            // Praat escapes double quotes inside strings by doubling them ("")
            val escapedText = lbl.replace("\"", "\"\"")
            sb.append("        intervals [${idx + 1}]:\n")
            sb.append(String.format(Locale.US, "            xmin = %.4f\n            xmax = %.4f\n", s, e))
            sb.append("            text = \"$escapedText\"\n")
        }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun safeFloat(v: Float, fallback: Float = 0f): Float = if (v.isFinite()) v else fallback
    private fun safeDouble(v: Double, fallback: Double = 0.0): Double = if (v.isFinite()) round4(v) else fallback
    private fun round2(v: Float): Double = if (v.isFinite()) Math.round(v * 100.0) / 100.0 else 0.0
    private fun round3(v: Float): Double = if (v.isFinite()) Math.round(v * 1000.0) / 1000.0 else 0.0
    private fun round4(v: Double): Double = if (v.isFinite()) Math.round(v * 10000.0) / 10000.0 else 0.0

    /**
     * Imports a previously exported PhonAcq `.zip` archive OR PhonTracer `.teproj` archive,
     * restoring the project metadata, speakers, word list, prompt images, and `.wav` recordings
     * (including both independent WAVs and long-audio time-sliced WAVs) into local storage and Room.
     */
    suspend fun importProjectArchiveZip(
        context: Context,
        repository: ProjectRepository,
        inputStream: InputStream,
        fallbackProjectName: String? = null
    ): ImportArchiveSummary {
        val tempDir = File(context.cacheDir, "zip_import_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000, 9999)}")
        tempDir.mkdirs()

        var experimentJsonStr: String? = null
        var experimentDepth = Int.MAX_VALUE
        var speakerJsonStr: String? = null
        var speakerDepth = Int.MAX_VALUE
        var wordListStr: String? = null
        var wordListDepth = Int.MAX_VALUE
        var analysisJsonStr: String? = null
        var analysisDepth = Int.MAX_VALUE
        var teprojJsonStr: String? = null
        var teprojDepth = Int.MAX_VALUE
        var simpleSummaryXlsxBytes: ByteArray? = null
        var simpleSummaryXlsxDepth = Int.MAX_VALUE
        var simpleSummaryCsvStr: String? = null
        var simpleSummaryCsvDepth = Int.MAX_VALUE

        val stagedWavByRelPath = LinkedHashMap<String, File>()
        val stagedWavByLowerRelPath = LinkedHashMap<String, File>()
        val stagedWavByBaseName = LinkedHashMap<String, File>()
        val stagedWavByLowerBaseName = LinkedHashMap<String, File>()
        val extractedWavEntries = mutableListOf<Pair<String, File>>()

        val stagedImageFiles = mutableMapOf<String, File>()
        val extractedImageEntries = mutableListOf<Pair<String, File>>()
        val supportedImgExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")

        val createdSpeakerIds = mutableListOf<Long>()
        var createdProjectId: Long? = null

        try {
            // Spool incoming stream to a temp archive file and guarantee immediate stream closure.
            // This enables UTF-8 -> GB18030/GBK charset fallback even on non-resettable SAF content:// streams.
            val archiveCopyFile = File(tempDir, "_incoming_archive.zip")
            inputStream.use { ins ->
                TeprojParser.copyEntryToFileBounded(
                    input = ins,
                    destFile = archiveCopyFile,
                    maxBytes = TeprojParser.MAX_TOTAL_EXTRACT_BYTES
                )
            }
            if (!archiveCopyFile.exists() || archiveCopyFile.length() <= 0L) {
                return ImportArchiveSummary(
                    success = false,
                    message = S.zip.importFailedMissingContent
                )
            }

            val mediaDir = File(tempDir, "media").apply { mkdirs() }
            var totalExtractedBytes = 0L

            fun resetExtractionState() {
                experimentJsonStr = null; experimentDepth = Int.MAX_VALUE
                speakerJsonStr = null; speakerDepth = Int.MAX_VALUE
                wordListStr = null; wordListDepth = Int.MAX_VALUE
                analysisJsonStr = null; analysisDepth = Int.MAX_VALUE
                teprojJsonStr = null; teprojDepth = Int.MAX_VALUE
                simpleSummaryXlsxBytes = null; simpleSummaryXlsxDepth = Int.MAX_VALUE
                simpleSummaryCsvStr = null; simpleSummaryCsvDepth = Int.MAX_VALUE
                stagedWavByRelPath.clear()
                stagedWavByLowerRelPath.clear()
                stagedWavByBaseName.clear()
                stagedWavByLowerBaseName.clear()
                extractedWavEntries.clear()
                stagedImageFiles.clear()
                extractedImageEntries.clear()
                totalExtractedBytes = 0L
                runCatching {
                    mediaDir.deleteRecursively()
                    mediaDir.mkdirs()
                }
            }

            TeprojParser.forEachZipEntryResilient(
                zipFile = archiveCopyFile,
                onBeforeRetry = { resetExtractionState() }
            ) { entry, zis ->
                if (entry.isDirectory) return@forEachZipEntryResilient
                val rawName = TeprojParser.normalizeArchivePath(entry.name)
                if (rawName.isEmpty()) return@forEachZipEntryResilient
                val baseName = rawName.substringAfterLast('/')
                val extLower = baseName.substringAfterLast('.', "").lowercase(Locale.US)
                val isHiddenMacMeta = baseName.startsWith("._") || rawName.startsWith("__MACOSX/")
                if (isHiddenMacMeta) return@forEachZipEntryResilient

                val depth = rawName.count { it == '/' }

                when {
                    baseName.equals("project.json", ignoreCase = true) -> {
                        if (depth < teprojDepth || teprojJsonStr == null) {
                            teprojJsonStr = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                            teprojDepth = depth
                        }
                    }
                    baseName.equals("experiment_profile.json", ignoreCase = true) -> {
                        if (depth < experimentDepth || experimentJsonStr == null) {
                            experimentJsonStr = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                            experimentDepth = depth
                        }
                    }
                    baseName.equals("speaker_profile.json", ignoreCase = true) -> {
                        if (depth < speakerDepth || speakerJsonStr == null) {
                            speakerJsonStr = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                            speakerDepth = depth
                        }
                    }
                    baseName.equals("word_list.txt", ignoreCase = true) -> {
                        if (depth < wordListDepth || wordListStr == null) {
                            wordListStr = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                            wordListDepth = depth
                        }
                    }
                    baseName.equals("analysis_results.json", ignoreCase = true) -> {
                        if (depth < analysisDepth || analysisJsonStr == null) {
                            analysisJsonStr = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                            analysisDepth = depth
                        }
                    }
                    baseName.equals("录音汇总表.xlsx", ignoreCase = true) -> {
                        if (depth < simpleSummaryXlsxDepth || simpleSummaryXlsxBytes == null) {
                            simpleSummaryXlsxBytes = TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            simpleSummaryXlsxDepth = depth
                        }
                    }
                    baseName.equals("录音汇总表.csv", ignoreCase = true) -> {
                        if (depth < simpleSummaryCsvDepth || simpleSummaryCsvStr == null) {
                            simpleSummaryCsvStr = TeprojParser.decodeTextClean(
                                TeprojParser.readBytesBounded(zis, TeprojParser.MAX_METADATA_ENTRY_BYTES)
                            )
                            simpleSummaryCsvDepth = depth
                        }
                    }
                    extLower == "wav" -> {
                        if (!rawName.contains("/takes/", ignoreCase = true)) {
                            val remainingBudget = (TeprojParser.MAX_TOTAL_EXTRACT_BYTES - totalExtractedBytes)
                                .coerceAtLeast(0L)
                            val safeWavStem = sanitizeFileNameComponent(baseName.substringBeforeLast('.'), "wav").take(48)
                            val outWav = File(mediaDir, "w_${extractedWavEntries.size}_${safeWavStem}.wav")
                            val written = TeprojParser.copyEntryToFileBounded(
                                input = zis,
                                destFile = outWav,
                                maxBytes = minOf(TeprojParser.MAX_SINGLE_MEDIA_ENTRY_BYTES, remainingBudget)
                            )
                            totalExtractedBytes += written
                            stagedWavByRelPath[rawName] = outWav
                            stagedWavByLowerRelPath[rawName.lowercase(Locale.ROOT)] = outWav
                            stagedWavByBaseName.putIfAbsent(baseName, outWav)
                            stagedWavByLowerBaseName.putIfAbsent(baseName.lowercase(Locale.ROOT), outWav)
                            extractedWavEntries.add(rawName to outWav)
                        }
                    }
                    extLower in supportedImgExtensions -> {
                        val remainingBudget = (TeprojParser.MAX_TOTAL_EXTRACT_BYTES - totalExtractedBytes)
                            .coerceAtLeast(0L)
                        val safeLocalName = sanitizeFileNameComponent(baseName.substringBeforeLast('.'), "img").take(48) + "." + extLower
                        val outImg = File(mediaDir, "img_${extractedImageEntries.size}_$safeLocalName")
                        val written = TeprojParser.copyEntryToFileBounded(
                            input = zis,
                            destFile = outImg,
                            maxBytes = minOf(64L * 1024L * 1024L, remainingBudget)
                        )
                        totalExtractedBytes += written
                        stagedImageFiles[rawName] = outImg
                        stagedImageFiles.putIfAbsent(baseName, outImg)
                        extractedImageEntries.add(rawName to outImg)
                    }
                }
            }

            if (experimentJsonStr == null &&
                teprojJsonStr == null &&
                wordListStr == null &&
                simpleSummaryXlsxBytes == null &&
                simpleSummaryCsvStr == null &&
                extractedWavEntries.isEmpty() &&
                extractedImageEntries.isEmpty()
            ) {
                return ImportArchiveSummary(
                    success = false,
                    message = S.zip.importFailedMissingContent
                )
            }

            // 1. Parse Project Metadata (pure in-memory, zero DB writes yet!)
            val teprojDoc = teprojJsonStr?.let { TeprojParser.parseProjectJson(it) }
            var projectName = ""
            var projectDesc = ""
            var speakerGroupName = ""
            var rawAssignedSpeakerIds = ""
            var enablePicturePrompts = false

            experimentJsonStr?.let { jsonStr ->
                runCatching {
                    val obj = JSONObject(jsonStr)
                    val expName = obj.optString("name", obj.optString("project_name", "")).trim()
                    if (expName.isNotEmpty()) projectName = expName
                    val expDesc = obj.optString("description", "").trim()
                    if (expDesc.isNotEmpty()) projectDesc = expDesc
                    speakerGroupName = obj.optString("speaker_group_name", "").trim()
                    rawAssignedSpeakerIds = obj.optString("assigned_speaker_ids", "").trim()
                    if (obj.optBoolean("enable_picture_prompts", false)) {
                        enablePicturePrompts = true
                    }
                }
            }
            if (teprojDoc != null) {
                if (projectName.isBlank() && teprojDoc.title.isNotBlank()) {
                    projectName = teprojDoc.title
                }
                if (projectDesc.isBlank() && teprojDoc.description.isNotBlank()) {
                    projectDesc = teprojDoc.description
                }
                if (teprojDoc.enablePicturePrompts) {
                    enablePicturePrompts = true
                }
            }

            val simpleSheetsMap: Map<String, List<List<String>>> = simpleSummaryXlsxBytes?.let { bytes ->
                runCatching { XlsxSpreadsheetWriter.readAllSheets(bytes) }.getOrDefault(emptyMap())
            } ?: emptyMap()
            val simpleWordlistSheetRows = simpleSheetsMap[S.zip.simpleXlsxWordlistSheet]
                ?: simpleSheetsMap.entries.firstOrNull { it.key.contains("字表") || it.key.contains("词表") }?.value
            if (simpleWordlistSheetRows != null && simpleWordlistSheetRows.size >= 2) {
                val firstDataRow = simpleWordlistSheetRows[1]
                val sheetProjName = firstDataRow.getOrNull(5)?.trim().orEmpty()
                val sheetProjDesc = firstDataRow.getOrNull(6)?.trim().orEmpty()
                if (projectName.isBlank() && sheetProjName.isNotEmpty()) projectName = sheetProjName
                if (projectDesc.isBlank() && sheetProjDesc.isNotEmpty()) projectDesc = sheetProjDesc
            }

            if (projectName.isBlank()) {
                projectName = fallbackProjectName?.trim()?.takeIf { it.isNotBlank() }
                    ?: S.zip.importDefaultProjectName
            }
            if (projectDesc.isBlank()) {
                projectDesc = S.zip.importDefaultProjectDesc
            }

            // 2. Collect Pending Speakers in Memory (deferred DB creation until wordlist is confirmed non-empty)
            data class PendingSpeakerSpec(
                val oldId: Long = -1L,
                val code: String = "",
                val name: String,
                val gender: String = "",
                val age: Int = -1,
                val dialect: String = "",
                val group: String = "",
                val notes: String = "",
                val f0Min: Float = -1f,
                val f0Max: Float = -1f,
                val isActive: Boolean = false
            )
            val pendingSpeakers = mutableListOf<PendingSpeakerSpec>()
            val seenPendingSpeakerCodes = HashSet<String>()
            val seenPendingSpeakerNames = HashSet<String>()

            fun enqueuePendingSpeaker(spec: PendingSpeakerSpec) {
                val upperCode = spec.code.trim().uppercase(Locale.US)
                val cleanName = spec.name.trim().ifEmpty { S.zip.importDefaultSpeakerName }
                if (upperCode.isNotEmpty() && upperCode in seenPendingSpeakerCodes) return
                if (upperCode.isEmpty() && cleanName in seenPendingSpeakerNames) return
                if (upperCode.isNotEmpty()) seenPendingSpeakerCodes.add(upperCode)
                seenPendingSpeakerNames.add(cleanName)
                pendingSpeakers.add(spec.copy(name = cleanName))
            }

            speakerJsonStr?.let { sJson ->
                runCatching {
                    val trimmed = sJson.trim()
                    val arr = if (trimmed.startsWith("{")) {
                        val rootObj = JSONObject(trimmed)
                        if (rootObj.has("speakers")) {
                            rootObj.getJSONArray("speakers")
                        } else {
                            JSONArray().put(rootObj)
                        }
                    } else {
                        JSONArray(trimmed)
                    }
                    for (i in 0 until arr.length()) {
                        val spObj = arr.optJSONObject(i) ?: continue
                        val oldId = spObj.optLong("id", (i + 1).toLong())
                        val code = spObj.optString("speaker_code", String.format(Locale.US, "SPK%02d", oldId))
                        enqueuePendingSpeaker(
                            PendingSpeakerSpec(
                                oldId = oldId,
                                code = code,
                                name = spObj.optString("name", S.zip.importDefaultSpeakerName),
                                gender = spObj.optString("gender_identity", "男"),
                                age = spObj.optInt("age", 25),
                                dialect = spObj.optString("dialect", ""),
                                group = spObj.optString("group_name", speakerGroupName),
                                notes = spObj.optString("notes", ""),
                                f0Min = spObj.optDouble("calibrated_f0_min_hz", -1.0).toFloat(),
                                f0Max = spObj.optDouble("calibrated_f0_max_hz", -1.0).toFloat()
                            )
                        )
                    }
                }
            }

            // 3. Parse Analysis Results / PhonTracer Speakers & Audio Records
            data class AnalysisRecordMeta(
                val index1Based: Int,
                val speakerCode: String,
                val speakerName: String,
                val speakerGender: String,
                val speakerGroup: String,
                val wordText: String,
                val remark: String,
                val speakerNote: String,
                val wordGroup: String,
                val wavRelPath: String,
                val longAudioRelPath: String = "",
                val sliceStartSec: Double? = null,
                val sliceEndSec: Double? = null,
                val durationMs: Long = 0L,
                val explicitQualityStatus: String = "",
                val occurrenceInGroupLabel: Int = 0,
                val occurrenceInLabel: Int = 0
            )
            val analysisRecords = mutableListOf<AnalysisRecordMeta>()
            analysisJsonStr?.let { aJson ->
                runCatching {
                    val arr = JSONArray(aJson)
                    val glOcc = HashMap<Triple<String, String, String>, Int>()
                    val lOcc = HashMap<Pair<String, String>, Int>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val wText = obj.optString("word_text", "").trim()
                        if (wText.isBlank()) continue
                        val spkCode = obj.optString("speaker_code", "SPK01").trim()
                        val spkName = obj.optString("speaker_name", S.common.defaultSpeakerFallback).trim()
                        val wGroup = TeprojParser.normalizeGroupName(obj.optString("word_group", ""))
                        val gKey = Triple(spkCode, wGroup, wText)
                        val gIdx = glOcc[gKey] ?: 0
                        glOcc[gKey] = gIdx + 1
                        val lKey = spkCode to wText
                        val lIdx = lOcc[lKey] ?: 0
                        lOcc[lKey] = lIdx + 1

                        val meta = AnalysisRecordMeta(
                            index1Based = obj.optInt("index", i + 1),
                            speakerCode = spkCode,
                            speakerName = spkName,
                            speakerGender = obj.optString("speaker_gender_identity", "男"),
                            speakerGroup = obj.optString("speaker_group", speakerGroupName),
                            wordText = wText,
                            remark = obj.optString("remark", ""),
                            speakerNote = obj.optString("speaker_note", ""),
                            wordGroup = wGroup,
                            wavRelPath = TeprojParser.normalizeArchivePath(obj.optString("file", "")),
                            durationMs = obj.optLong("duration_ms", 0L),
                            explicitQualityStatus = obj.optString("quality_status", "").trim(),
                            occurrenceInGroupLabel = gIdx,
                            occurrenceInLabel = lIdx
                        )
                        analysisRecords.add(meta)
                        enqueuePendingSpeaker(
                            PendingSpeakerSpec(
                                code = meta.speakerCode,
                                name = meta.speakerName,
                                gender = meta.speakerGender,
                                age = 25,
                                dialect = "",
                                group = meta.speakerGroup
                            )
                        )
                    }
                }
            }

            // Parse PhonTracer `project.json` speakers, wordlist, and ALL speakers' audio records (independent + long-audio slices)
            val teprojParsedWords = mutableListOf<ParsedWord>()
            val teprojBaseRemarkByIid = mutableMapOf<String, String>()
            val teprojSeqByIid = mutableMapOf<String, Int>()
            val teprojImagePathBySeqIndex = mutableMapOf<Int, String>()
            val teprojImagePathByWordText = mutableMapOf<String, String>()

            if (teprojDoc != null) {
                teprojParsedWords.addAll(teprojDoc.words)
                for (slot in teprojDoc.wordSlots) {
                    teprojSeqByIid[slot.itemId] = slot.seqIndex1Based
                    teprojBaseRemarkByIid[slot.itemId] = slot.remark
                    if (slot.imagePath.isNotBlank()) {
                        teprojImagePathBySeqIndex[slot.seqIndex1Based] = slot.imagePath
                        teprojImagePathByWordText.putIfAbsent(slot.label, slot.imagePath)
                    }
                }

                // IMPORTANT: Evaluate BEFORE looping speakers so ALL speakers' recordings are imported!
                val shouldPopulateFromTeproj = analysisRecords.isEmpty()
                for (spk in teprojDoc.speakers) {
                    enqueuePendingSpeaker(
                        PendingSpeakerSpec(
                            code = spk.code,
                            name = spk.name,
                            gender = spk.gender,
                            age = spk.age,
                            dialect = spk.dialect, // Keep empty if absent so existing local dialect speaker matches!
                            group = spk.group.ifBlank { speakerGroupName },
                            notes = spk.notes,
                            isActive = spk.isActive
                        )
                    )
                    if (shouldPopulateFromTeproj) {
                        for (item in spk.items) {
                            if (!item.hasAnyAudio) continue
                            val seqIdx = teprojSeqByIid[item.itemId]
                                ?: item.importIndex?.let { it + 1 }
                                ?: item.itemId.toIntOrNull()
                                ?: 1
                            val baseRmk = teprojBaseRemarkByIid[item.itemId].orEmpty()
                            val resolvedSpkNote = when {
                                item.speakerNote.isNotBlank() && item.speakerNote != baseRmk -> item.speakerNote
                                else -> ""
                            }
                            analysisRecords.add(
                                AnalysisRecordMeta(
                                    index1Based = seqIdx,
                                    speakerCode = spk.code,
                                    speakerName = spk.name,
                                    speakerGender = spk.gender,
                                    speakerGroup = spk.group.ifBlank { speakerGroupName },
                                    wordText = item.label,
                                    remark = baseRmk.ifEmpty { item.baseRemark },
                                    speakerNote = resolvedSpkNote,
                                    wordGroup = item.groupName,
                                    wavRelPath = item.audioPath,
                                    longAudioRelPath = item.longAudioPath,
                                    sliceStartSec = item.sliceStartSec,
                                    sliceEndSec = item.sliceEndSec,
                                    durationMs = item.durationMs,
                                    explicitQualityStatus = "",
                                    occurrenceInGroupLabel = item.occurrenceInGroupLabel,
                                    occurrenceInLabel = item.occurrenceInLabel
                                )
                            )
                        }
                    }
                }
            }

            // Also parse Simple Mode `录音汇总表.xlsx` / `录音汇总表.csv` when present
            val simpleParsedWords = mutableListOf<ParsedWord>()
            if (simpleWordlistSheetRows != null && simpleWordlistSheetRows.size >= 2) {
                for (rIdx in 1 until simpleWordlistSheetRows.size) {
                    val row = simpleWordlistSheetRows[rIdx]
                    val cleanGrp = TeprojParser.normalizeGroupName(row.getOrNull(1).orEmpty())
                    val wText = row.getOrNull(2)?.trim().orEmpty()
                    val wRmk = row.getOrNull(3)?.trim().orEmpty()
                    val wImgRel = TeprojParser.normalizeArchivePath(row.getOrNull(4).orEmpty())
                    if (wText.isNotEmpty()) {
                        if (wImgRel.isNotEmpty()) enablePicturePrompts = true
                        simpleParsedWords.add(
                            ParsedWord(
                                text = wText,
                                remark = wRmk,
                                groupName = cleanGrp,
                                imagePath = wImgRel
                            )
                        )
                    }
                }
            }
            val simpleSummaryRows: List<List<String>> = when {
                simpleSheetsMap.isNotEmpty() -> {
                    simpleSheetsMap[S.zip.simpleXlsxSummarySheet]
                        ?: simpleSheetsMap.values.firstOrNull().orEmpty()
                }
                !simpleSummaryCsvStr.isNullOrBlank() -> parseCsvRowsSimple(simpleSummaryCsvStr!!)
                else -> emptyList()
            }
            if (analysisRecords.isEmpty() && simpleSummaryRows.size >= 2) {
                val glOcc = HashMap<Triple<String, String, String>, Int>()
                val lOcc = HashMap<Pair<String, String>, Int>()
                for (rIdx in 1 until simpleSummaryRows.size) {
                    val row = simpleSummaryRows[rIdx]
                    val seqIdx = row.getOrNull(0)?.trim()?.toIntOrNull() ?: rIdx
                    val cleanGrp = TeprojParser.normalizeGroupName(row.getOrNull(1).orEmpty())
                    val wText = row.getOrNull(2)?.trim().orEmpty()
                    val wRmk = row.getOrNull(3)?.trim().orEmpty()
                    val spkNote = row.getOrNull(4)?.trim().orEmpty()
                    val spkName = row.getOrNull(5)?.trim().orEmpty().ifEmpty { S.common.defaultSpeakerFallback }
                    val spkDialect = row.getOrNull(6)?.trim().orEmpty()
                    val durSec = row.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0
                    val relAudio = TeprojParser.normalizeArchivePath(row.getOrNull(8).orEmpty())
                    if (wText.isNotEmpty()) {
                        enqueuePendingSpeaker(
                            PendingSpeakerSpec(
                                name = spkName,
                                gender = "未设",
                                age = 25,
                                dialect = spkDialect,
                                group = speakerGroupName
                            )
                        )
                        if (relAudio.isNotEmpty()) {
                            val gKey = Triple(spkName, cleanGrp, wText)
                            val gIdx = glOcc[gKey] ?: 0
                            glOcc[gKey] = gIdx + 1
                            val lKey = spkName to wText
                            val lIdx = lOcc[lKey] ?: 0
                            lOcc[lKey] = lIdx + 1

                            analysisRecords.add(
                                AnalysisRecordMeta(
                                    index1Based = seqIdx,
                                    speakerCode = "",
                                    speakerName = spkName,
                                    speakerGender = "未设",
                                    speakerGroup = speakerGroupName,
                                    wordText = wText,
                                    remark = wRmk,
                                    speakerNote = spkNote,
                                    wordGroup = cleanGrp,
                                    wavRelPath = relAudio,
                                    durationMs = if (durSec > 0.0) (durSec * 1000.0).toLong() else 0L,
                                    occurrenceInGroupLabel = gIdx,
                                    occurrenceInLabel = lIdx
                                )
                            )
                        }
                    }
                }
            }

            fun resolveExtractedImageFileForWord(
                explicitRelPath: String,
                index1Based: Int,
                wordText: String
            ): File? {
                if (extractedImageEntries.isEmpty()) return null
                val candidateRel = explicitRelPath.ifBlank {
                    teprojImagePathBySeqIndex[index1Based] ?: teprojImagePathByWordText[wordText].orEmpty()
                }
                if (candidateRel.isNotBlank()) {
                    val normCand = TeprojParser.normalizeArchivePath(candidateRel)
                    stagedImageFiles[normCand]?.let { return it }
                    stagedImageFiles[normCand.substringAfterLast('/')]?.let { return it }
                }
                val idx3d = String.format(Locale.US, "%03d_", index1Based)
                val safeWord = sanitizeFileNameComponent(wordText, "")
                for ((rawEntryName, imgFile) in extractedImageEntries) {
                    val baseName = rawEntryName.substringAfterLast('/')
                    if (safeWord.isNotEmpty() && baseName.startsWith(idx3d + safeWord, ignoreCase = true)) {
                        return imgFile
                    }
                    val parsedFromImg = WordImageManager.parseWordFromImageFileName(
                        rawFileName = baseName,
                        wordAndRemarkParser = repository::parseWordAndRemark
                    )
                    if (parsedFromImg.text == wordText) {
                        return imgFile
                    }
                }
                return null
            }

            // 4. Reconstruct Word List (still zero DB writes!)
            val parsedWords = mutableListOf<ParsedWord>()
            if (!wordListStr.isNullOrBlank()) {
                var currentGroup = ""
                val lines = wordListStr!!.lines()
                val leadingIndexRegex = Regex("^\\d{1,4}(?:\\s*[\\t.、)）]\\s*|\\s+-\\s+)")
                for (rawLine in lines) {
                    val line = rawLine.removePrefix("\uFEFF").trim()
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
                    if ((line.startsWith("【") && line.endsWith("】")) ||
                        (line.startsWith("[") && line.endsWith("]"))
                    ) {
                        currentGroup = line.substring(1, line.length - 1).trim()
                        continue
                    }
                    val withoutLeadingIndex = line.replaceFirst(leadingIndexRegex, "").trim()
                    val (wText, wRemark) = repository.parseWordAndRemark(withoutLeadingIndex)
                    if (wText.isNotBlank()) {
                        parsedWords.add(
                            ParsedWord(
                                text = wText,
                                remark = wRemark,
                                groupName = currentGroup
                            )
                        )
                    }
                }
            }

            if (parsedWords.isEmpty() && teprojParsedWords.isNotEmpty()) {
                parsedWords.addAll(teprojParsedWords)
            }

            if (parsedWords.isEmpty() && simpleParsedWords.isNotEmpty()) {
                parsedWords.addAll(simpleParsedWords)
            }

            if (parsedWords.isEmpty() && analysisRecords.isNotEmpty()) {
                analysisRecords
                    .distinctBy { Triple(it.wordGroup, it.wordText, it.occurrenceInGroupLabel) }
                    .sortedBy { it.index1Based }
                    .forEach { rec ->
                        parsedWords.add(
                            ParsedWord(
                                text = rec.wordText,
                                remark = rec.remark,
                                groupName = TeprojParser.normalizeGroupName(rec.wordGroup)
                            )
                        )
                    }
            }

            val teprojLongAudioLowerSet = teprojDoc?.longAudioPaths
                ?.map { it.lowercase(Locale.ROOT) }
                ?.toSet()
                ?: emptySet()

            // Only derive words from raw WAV filenames if NOT a long-audio-only teproj
            if (parsedWords.isEmpty() && extractedWavEntries.isNotEmpty()) {
                extractedWavEntries
                    .filter { (rel, _) ->
                        val lower = rel.lowercase(Locale.ROOT)
                        lower !in teprojLongAudioLowerSet &&
                            lower.substringAfterLast('/') !in teprojLongAudioLowerSet.map { it.substringAfterLast('/') }
                    }
                    .sortedBy { it.first }
                    .forEach { (rel, _) ->
                        val wavName = rel.substringAfterLast('/')
                        val stem = wavName.substringBeforeLast('.')
                        val cleaned = stem
                            .replace(Regex("^SPK\\d+_\\d+_"), "")
                            .replace(Regex("^\\d+_"), "")
                        if (cleaned.isNotBlank()) {
                            parsedWords.add(ParsedWord(text = cleaned, remark = "", groupName = ""))
                        }
                    }
            }

            // Support importing a plain ZIP pack of pictures directly into a Picture Wordlist project
            if (parsedWords.isEmpty() && extractedImageEntries.isNotEmpty()) {
                enablePicturePrompts = true
                if (experimentJsonStr == null && teprojJsonStr == null) {
                    projectName = fallbackProjectName?.trim()?.takeIf { it.isNotBlank() }
                        ?: S.zip.importPicProjectName
                    projectDesc = S.zip.importPicProjectDesc
                }
                extractedImageEntries
                    .sortedBy { it.first }
                    .forEach { (rawEntryName, imgFile) ->
                        val baseName = rawEntryName.substringAfterLast('/')
                        val folderGroup = rawEntryName.substringBeforeLast('/', "")
                            .substringAfterLast('/')
                            .takeIf {
                                it.isNotBlank() &&
                                    !it.equals("images", ignoreCase = true) &&
                                    !it.equals(S.zip.simpleImagesFolder, ignoreCase = true)
                            }
                            .orEmpty()
                        val parsedImg = WordImageManager.parseWordFromImageFileName(
                            rawFileName = baseName,
                            fallbackIndex = parsedWords.size + 1,
                            wordAndRemarkParser = repository::parseWordAndRemark
                        )
                        if (parsedImg.text.isNotBlank()) {
                            val stagedToken = WordImageManager.stageImageFromFile(context, imgFile).orEmpty()
                            parsedWords.add(
                                ParsedWord(
                                    text = parsedImg.text,
                                    remark = parsedImg.remark,
                                    groupName = parsedImg.groupName.ifBlank { folderGroup },
                                    imagePath = stagedToken
                                )
                            )
                        }
                    }
            } else if (extractedImageEntries.isNotEmpty()) {
                for (i in parsedWords.indices) {
                    val pw = parsedWords[i]
                    if (pw.imagePath.startsWith("staged:")) continue
                    val matchedImgFile = resolveExtractedImageFileForWord(
                        explicitRelPath = pw.imagePath,
                        index1Based = i + 1,
                        wordText = pw.text
                    )
                    val stagedToken = if (matchedImgFile != null) {
                        WordImageManager.stageImageFromFile(context, matchedImgFile).orEmpty()
                    } else {
                        ""
                    }
                    parsedWords[i] = pw.copy(imagePath = stagedToken)
                }
            }

            if (parsedWords.isEmpty()) {
                return ImportArchiveSummary(
                    success = false,
                    message = S.zip.importFailedNoWords
                )
            }

            // Deduplicate project name cleanly (supports multiple re-imports without duplicate titles)
            val existingProjects = repository.allProjects.firstOrNull() ?: emptyList()
            val existingNames = existingProjects.map { it.name }.toSet()
            if (projectName in existingNames) {
                val baseWithSuffix = "$projectName${S.zip.importSuffix}"
                if (baseWithSuffix !in existingNames) {
                    projectName = baseWithSuffix
                } else {
                    var counter = 2
                    while ("$baseWithSuffix ($counter)" in existingNames) {
                        counter++
                    }
                    projectName = "$baseWithSuffix ($counter)"
                }
            }

            // 5. Commit Speakers, Project, Words, and Recordings (with rollback on exception)
            val existingSpeakers = (repository.allSpeakers.firstOrNull() ?: emptyList()).toMutableList()
            val speakerCodeToId = mutableMapOf<String, Long>()
            val speakerNameToId = mutableMapOf<String, Long>()
            val oldSpeakerIdToLocalId = mutableMapOf<Long, Long>()
            val spPrefs = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
            var activeImportedSpeakerId: Long? = null

            suspend fun materializeSpeaker(spec: PendingSpeakerSpec): Long {
                val cleanName = spec.name.trim().ifEmpty { S.zip.importDefaultSpeakerName }
                val cleanGroup = spec.group.trim().ifEmpty { speakerGroupName }
                val cleanDialect = spec.dialect.trim()
                val matched = existingSpeakers.find {
                    it.name.equals(cleanName, ignoreCase = true) &&
                        (cleanDialect.isEmpty() || it.dialect.equals(cleanDialect, ignoreCase = true))
                }
                val spkId = if (matched != null) {
                    matched.id
                } else {
                    val resolvedGender = spec.gender.ifBlank { "未设" }
                    val resolvedAge = if (spec.age in 1..120) spec.age else 25
                    val resolvedDialect = cleanDialect.ifBlank { "普通话" }
                    val newId = repository.createSpeaker(
                        name = cleanName,
                        gender = resolvedGender,
                        age = resolvedAge,
                        dialect = resolvedDialect,
                        notes = spec.notes,
                        groupName = cleanGroup
                    )
                    createdSpeakerIds.add(newId)
                    existingSpeakers.add(
                        Speaker(
                            id = newId,
                            name = cleanName,
                            gender = resolvedGender,
                            age = resolvedAge,
                            dialect = resolvedDialect,
                            notes = spec.notes,
                            groupName = cleanGroup
                        )
                    )
                    newId
                }
                if (spec.code.isNotBlank()) {
                    speakerCodeToId[spec.code.trim().uppercase(Locale.US)] = spkId
                }
                speakerNameToId[cleanName] = spkId
                if (spec.oldId > 0L) {
                    oldSpeakerIdToLocalId[spec.oldId] = spkId
                }
                if (spec.isActive) {
                    activeImportedSpeakerId = spkId
                }
                if (spec.f0Min > 40f && spec.f0Max > spec.f0Min + 15f) {
                    spPrefs.edit()
                        .putFloat("pitch_min_$spkId", spec.f0Min)
                        .putFloat("pitch_max_$spkId", spec.f0Max)
                        .apply()
                }
                return spkId
            }

            for (spec in pendingSpeakers) {
                materializeSpeaker(spec)
            }

            val defaultSpeakerId = activeImportedSpeakerId
                ?: speakerCodeToId.values.firstOrNull()
                ?: speakerNameToId.values.firstOrNull()
                ?: existingSpeakers.firstOrNull()?.id
                ?: repository.createSpeaker(S.common.defaultSpeakerFallback, "未设", 25, "普通话", "", speakerGroupName).also {
                    createdSpeakerIds.add(it)
                }

            val finalEnablePicturePrompts = enablePicturePrompts || parsedWords.any { it.imagePath.isNotBlank() }

            val resolvedAssignedSpeakerIds = if (rawAssignedSpeakerIds.isNotBlank()) {
                val mapped = com.example.data.parseAssignedSpeakerIds(rawAssignedSpeakerIds)
                    .mapNotNull { oldId -> oldSpeakerIdToLocalId[oldId] }
                com.example.data.formatAssignedSpeakerIds(mapped)
            } else {
                ""
            }

            val newProjectId = repository.createProjectWithParsedWords(
                name = projectName,
                description = projectDesc,
                parsedWords = parsedWords,
                speakerId = defaultSpeakerId,
                speakerGroupName = speakerGroupName,
                enablePicturePrompts = finalEnablePicturePrompts,
                context = context,
                assignedSpeakerIds = resolvedAssignedSpeakerIds
            )
            createdProjectId = newProjectId

            val insertedWords = repository.getWordsForProjectSync(newProjectId).sortedBy { it.originalIndex }
            val restoredImageCount = insertedWords.count { it.imagePath.isNotBlank() }
            val projectDir = File(context.getExternalFilesDir(null), "project_$newProjectId")
            projectDir.mkdirs()

            // Index inserted words by (group, text, occ) and (text, occ) for duplicate-safe matching
            val insertedByGroupTextOcc = HashMap<Triple<String, String, Int>, Word>(insertedWords.size * 2)
            val insertedByTextOcc = HashMap<Pair<String, Int>, Word>(insertedWords.size * 2)
            val insGlCounter = HashMap<Pair<String, String>, Int>()
            val insLCounter = HashMap<String, Int>()
            for (w in insertedWords) {
                val gNorm = TeprojParser.normalizeGroupName(w.groupName)
                val gKey = gNorm to w.text
                val gOcc = insGlCounter[gKey] ?: 0
                insGlCounter[gKey] = gOcc + 1
                insertedByGroupTextOcc[Triple(gNorm, w.text, gOcc)] = w

                val lOcc = insLCounter[w.text] ?: 0
                insLCounter[w.text] = lOcc + 1
                insertedByTextOcc[w.text to lOcc] = w
            }

            fun findStagedWav(rawRelOrBase: String): File? {
                val norm = TeprojParser.normalizeArchivePath(rawRelOrBase)
                if (norm.isEmpty()) return null
                val normLower = norm.lowercase(Locale.ROOT)
                stagedWavByRelPath[norm]?.let { return it }
                stagedWavByLowerRelPath[normLower]?.let { return it }
                stagedWavByLowerRelPath["audio/$normLower"]?.let { return it }
                stagedWavByLowerRelPath["${S.zip.simpleAudioFolder.lowercase(Locale.ROOT)}/$normLower"]?.let { return it }
                stagedWavByLowerRelPath.entries.firstOrNull { it.key.endsWith("/$normLower") }?.value?.let { return it }
                val base = norm.substringAfterLast('/')
                stagedWavByBaseName[base]?.let { return it }
                return stagedWavByLowerBaseName[base.lowercase(Locale.ROOT)]
            }

            val normalizedLongAudioCache = HashMap<File, File>()
            val longAudioSourceFiles = HashSet<File>()
            for (longRel in teprojDoc?.longAudioPaths.orEmpty()) {
                findStagedWav(longRel)?.let { longAudioSourceFiles.add(it) }
            }

            fun resolveNormalizedLongAudio(rawLongWav: File): File? {
                normalizedLongAudioCache[rawLongWav]?.let { return it }
                val info = AudioWavNormalizer.inspectWavHeader(rawLongWav)
                if (info != null && info.isCanonicalMono16Pcm) {
                    normalizedLongAudioCache[rawLongWav] = rawLongWav
                    return rawLongWav
                }
                val normFile = File(tempDir, "norm_long_${normalizedLongAudioCache.size}.wav")
                val ok = AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(rawLongWav, normFile)
                return if (ok && normFile.exists() && normFile.length() > 44L) {
                    normalizedLongAudioCache[rawLongWav] = normFile
                    normFile
                } else {
                    null
                }
            }

            var importedWavCount = 0
            val importedSpeakerIds = LinkedHashSet<Long>()
            val consumedStagedFiles = HashSet<File>()
            val claimedWordForSpeaker = HashSet<Pair<Long, Long>>()

            suspend fun persistImportedWav(
                destFile: File,
                destFileName: String,
                targetWord: Word,
                spkId: Long,
                fallbackDurationMs: Long,
                explicitQualityStatus: String,
                speakerNote: String
            ) {
                val report = runCatching { SpeechQualityEvaluator.evaluate(destFile) }.getOrNull()
                val durMs = when {
                    report != null && report.totalDurationMs > 20 -> report.totalDurationMs.toLong()
                    fallbackDurationMs > 0L -> fallbackDurationMs
                    else -> (AcousticAnalyzer.getExactWavDurationSec(destFile) * 1000.0).toLong().coerceAtLeast(100L)
                }
                val avgRms = report?.avgSpeechRms?.takeIf { it.isFinite() && it >= 0f } ?: 0f
                val peakAmp = report?.peakAmplitude?.coerceIn(0, 32767) ?: 0
                val qualityStatus = explicitQualityStatus.ifBlank { report?.primaryStatus ?: "正常" }

                repository.saveSpeakerRecording(
                    wordId = targetWord.id,
                    speakerId = spkId,
                    recordingFile = destFileName,
                    durationMs = durMs,
                    qualityStatus = qualityStatus,
                    avgRms = avgRms,
                    peakAmplitude = peakAmp
                )
                if (speakerNote.isNotBlank()) {
                    repository.saveSpeakerWordNote(targetWord.id, spkId, speakerNote)
                }
                claimedWordForSpeaker.add(spkId to targetWord.id)
                importedWavCount++
                importedSpeakerIds.add(spkId)
            }

            // 6A. Match WAVs using analysis_results.json / project.json (both independent & long-audio sliced) / 录音汇总表.xlsx
            for (rec in analysisRecords) {
                val spkId = speakerCodeToId[rec.speakerCode.trim().uppercase(Locale.US)]
                    ?: speakerNameToId[rec.speakerName.trim()]
                    ?: defaultSpeakerId

                val recGroupNorm = TeprojParser.normalizeGroupName(rec.wordGroup)
                val targetWord = insertedByGroupTextOcc[Triple(recGroupNorm, rec.wordText, rec.occurrenceInGroupLabel)]
                    ?: insertedByTextOcc[rec.wordText to rec.occurrenceInLabel]
                    ?: insertedWords.getOrNull(rec.index1Based - 1)?.takeIf { it.text == rec.wordText }
                    ?: insertedWords.firstOrNull {
                        TeprojParser.normalizeGroupName(it.groupName) == recGroupNorm &&
                            it.text == rec.wordText &&
                            (spkId to it.id) !in claimedWordForSpeaker
                    }
                    ?: insertedWords.firstOrNull {
                        it.text == rec.wordText && (spkId to it.id) !in claimedWordForSpeaker
                    }
                    ?: insertedWords.find { it.text == rec.wordText }
                    ?: continue

                val destFileName = "word_${targetWord.id}_speaker_${spkId}_${System.currentTimeMillis()}_${importedWavCount}.wav"
                val destFile = File(projectDir, destFileName)

                val writtenOk: Boolean = if (rec.wavRelPath.isNotBlank()) {
                    val stagedFile = findStagedWav(rec.wavRelPath)
                    if (stagedFile != null && stagedFile.exists() && stagedFile.length() > 44L) {
                        consumedStagedFiles.add(stagedFile)
                        val normalized = AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(stagedFile, destFile)
                        if (normalized && destFile.exists() && destFile.length() > 44L) {
                            true
                        } else {
                            stagedFile.copyTo(destFile, overwrite = true)
                            destFile.exists() && destFile.length() > 44L
                        }
                    } else {
                        false
                    }
                } else if (rec.longAudioRelPath.isNotBlank() &&
                    rec.sliceStartSec != null &&
                    rec.sliceEndSec != null &&
                    rec.sliceEndSec > rec.sliceStartSec + 0.005
                ) {
                    val rawLongFile = findStagedWav(rec.longAudioRelPath)
                    if (rawLongFile != null && rawLongFile.exists() && rawLongFile.length() > 44L) {
                        consumedStagedFiles.add(rawLongFile)
                        longAudioSourceFiles.add(rawLongFile)
                        val normLong = resolveNormalizedLongAudio(rawLongFile)
                        if (normLong != null) {
                            AudioWavNormalizer.sliceCanonicalMono16Wav(
                                canonicalWavFile = normLong,
                                startSec = rec.sliceStartSec,
                                endSec = rec.sliceEndSec,
                                outputWavFile = destFile
                            )
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                } else {
                    false
                }

                if (writtenOk) {
                    persistImportedWav(
                        destFile = destFile,
                        destFileName = destFileName,
                        targetWord = targetWord,
                        spkId = spkId,
                        fallbackDurationMs = rec.durationMs,
                        explicitQualityStatus = rec.explicitQualityStatus,
                        speakerNote = rec.speakerNote
                    )
                } else {
                    runCatching { destFile.delete() }
                }
            }

            // 6B. Fallback filename matcher for WAVs not covered by structured metadata (excluding long-audio source files)
            val remainingWavs = extractedWavEntries
                .filter { (_, f) -> f !in consumedStagedFiles && f !in longAudioSourceFiles && f.length() > 44L }
            val codeIndexRegex = Regex("^(SPK\\d+)_(\\d{1,4})_([^._]+).*\\.wav$", RegexOption.IGNORE_CASE)
            val nameIndexRegex = Regex("^([^_]+)_(\\d{1,4})_([^._]+).*\\.wav$", RegexOption.IGNORE_CASE)
            val indexWordRegex = Regex("^(\\d{1,4})_([^._]+).*\\.wav$", RegexOption.IGNORE_CASE)

            for ((rawRelPath, stagedFile) in remainingWavs) {
                val wavName = rawRelPath.substringAfterLast('/')
                var matchedWord: Word? = null
                var matchedSpeakerId: Long = defaultSpeakerId

                val m1 = codeIndexRegex.matchEntire(wavName)
                val m2 = if (m1 == null) nameIndexRegex.matchEntire(wavName) else null
                val m3 = if (m1 == null && m2 == null) indexWordRegex.matchEntire(wavName) else null

                when {
                    m1 != null -> {
                        val spkCode = m1.groupValues[1].uppercase(Locale.US)
                        val idx1 = m1.groupValues[2].toIntOrNull() ?: 1
                        val wText = m1.groupValues[3]
                        matchedSpeakerId = speakerCodeToId[spkCode] ?: defaultSpeakerId
                        matchedWord = insertedWords.getOrNull(idx1 - 1)?.takeIf { it.text == wText }
                            ?: insertedWords.find { it.text == wText }
                    }
                    m2 != null -> {
                        val spkName = m2.groupValues[1]
                        val idx1 = m2.groupValues[2].toIntOrNull() ?: 1
                        val wText = m2.groupValues[3]
                        matchedSpeakerId = speakerNameToId[spkName] ?: defaultSpeakerId
                        matchedWord = insertedWords.getOrNull(idx1 - 1)?.takeIf { it.text == wText }
                            ?: insertedWords.find { it.text == wText }
                    }
                    m3 != null -> {
                        val idx1 = m3.groupValues[1].toIntOrNull() ?: 1
                        val wText = m3.groupValues[2]
                        matchedWord = insertedWords.getOrNull(idx1 - 1)?.takeIf { it.text == wText }
                            ?: insertedWords.find { it.text == wText }
                    }
                    else -> {
                        val stem = wavName.substringBeforeLast('.')
                        matchedWord = insertedWords.find { it.text == stem || stem.contains(it.text) }
                    }
                }

                if (matchedWord != null) {
                    val destFileName = "word_${matchedWord.id}_speaker_${matchedSpeakerId}_${System.currentTimeMillis()}_${importedWavCount}.wav"
                    val destFile = File(projectDir, destFileName)
                    val normalized = AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(stagedFile, destFile)
                    if (!normalized || !destFile.exists() || destFile.length() <= 44L) {
                        stagedFile.copyTo(destFile, overwrite = true)
                    }
                    if (destFile.exists() && destFile.length() > 44L) {
                        persistImportedWav(
                            destFile = destFile,
                            destFileName = destFileName,
                            targetWord = matchedWord,
                            spkId = matchedSpeakerId,
                            fallbackDurationMs = 0L,
                            explicitQualityStatus = "",
                            speakerNote = ""
                        )
                    }
                }
            }

            val resolvedPrimarySpeakerId = when {
                activeImportedSpeakerId != null && (importedSpeakerIds.isEmpty() || activeImportedSpeakerId in importedSpeakerIds) ->
                    activeImportedSpeakerId!!
                importedSpeakerIds.isNotEmpty() -> importedSpeakerIds.first()
                else -> defaultSpeakerId
            }
            if (resolvedPrimarySpeakerId != defaultSpeakerId) {
                repository.getProjectById(newProjectId)?.let { proj ->
                    repository.updateProject(proj.copy(speakerId = resolvedPrimarySpeakerId))
                }
            }

            val spkCount = importedSpeakerIds.size.coerceAtLeast(1)
            val imgSuffix = if (restoredImageCount > 0) S.zip.importImageSummarySuffix.withArgs("count" to restoredImageCount) else ""
            return ImportArchiveSummary(
                success = true,
                projectId = newProjectId,
                projectName = projectName,
                wordCount = insertedWords.size,
                wavCount = importedWavCount,
                speakerCount = spkCount,
                message = S.zip.importSuccessSummary.withArgs(
                    "name" to projectName,
                    "words" to insertedWords.size,
                    "imgSuffix" to imgSuffix,
                    "wavs" to importedWavCount
                ),
                primarySpeakerId = resolvedPrimarySpeakerId
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Rollback any partially created project & newly created speakers so no dirty records remain
            createdProjectId?.let { pid ->
                runCatching { repository.deleteProject(pid, context) }
            }
            for (spkId in createdSpeakerIds) {
                runCatching { repository.deleteSpeaker(spkId) }
            }
            return ImportArchiveSummary(
                success = false,
                message = S.zip.importArchiveFailed.withArgs("msg" to (e.localizedMessage ?: e.javaClass.simpleName))
            )
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (ignored: Exception) {}
        }
    }
}
