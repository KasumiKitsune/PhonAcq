package com.example.link

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.audio.SpeechQualityEvaluator
import com.example.data.ParsedWord
import com.example.data.ProjectRepository
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.utils.XlsxSpreadsheetWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Summary of a self-contained Wireless Session Capsule stored on a device (`wireless_archives/`).
 * Completely decoupled from local Room auto-increment IDs so two heterogeneous devices never collide.
 */
@Immutable
data class WirelessSessionCapsuleSummary(
    val folderName: String,
    val capsuleDir: File,
    val sessionCode: String,
    val projectName: String,
    val speakerName: String,
    val speakerGender: String,
    val speakerAge: Int,
    val speakerDialect: String,
    val totalWordsInSnapshot: Int,
    val recordedTakeCount: Int,
    val totalBytes: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

/**
 * Self-contained metadata for a single word entry inside `session_manifest.json`.
 */
data class CapsuleWordEntry(
    val seqPos: Int,
    val text: String,
    val remark: String = "",
    val groupName: String = "",
    val speakerNote: String = "",
    val wavFileName: String = "",
    val durationMs: Long = 0L,
    val qualityStatus: String? = null,
    val avgRms: Float = 0f,
    val peakAmplitude: Int = 0,
    val recordedAtMs: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("seqPos", seqPos)
        put("text", text)
        put("remark", remark)
        put("groupName", groupName)
        put("speakerNote", speakerNote)
        put("wavFileName", wavFileName)
        put("durationMs", durationMs)
        put("qualityStatus", qualityStatus ?: "")
        put("avgRms", avgRms.toDouble())
        put("peakAmplitude", peakAmplitude)
        put("recordedAtMs", recordedAtMs)
    }

    companion object {
        fun fromJson(j: JSONObject): CapsuleWordEntry = CapsuleWordEntry(
            seqPos = j.optInt("seqPos", 0),
            text = j.optString("text", ""),
            remark = j.optString("remark", ""),
            groupName = j.optString("groupName", ""),
            speakerNote = j.optString("speakerNote", ""),
            wavFileName = j.optString("wavFileName", ""),
            durationMs = j.optLong("durationMs", 0L),
            qualityStatus = j.optString("qualityStatus", "").takeIf { it.isNotBlank() },
            avgRms = j.optDouble("avgRms", 0.0).toFloat(),
            peakAmplitude = j.optInt("peakAmplitude", 0),
            recordedAtMs = j.optLong("recordedAtMs", 0L)
        )
    }
}

/**
 * Manages self-contained Wireless Session Capsules (`wireless_archives/`) on secondary/peer devices.
 *
 * Structure of each capsule:
 * ```
 * wireless_archives/20261112_1530_苏州方言_张阿婆_S8F2A/
 *   ├── session_manifest.json
 *   ├── 录音核对清单.csv          (UTF-8 with BOM for Excel)
 *   └── wav/
 *         ├── 0001_东_阴平_1731402012.wav
 *         └── ...
 * ```
 * Allows dual-device backup & full mirroring even when the two devices have completely independent
 * wordlists and speaker databases, with 1-click ZIP export or on-demand import as a fresh local Project.
 */
object WirelessSessionArchiveManager {

    private const val ROOT_FOLDER_NAME = "wireless_archives"
    private const val MANIFEST_FILE_NAME = "session_manifest.json"
    private val CSV_FILE_NAME: String get() = S.wireless.capsuleCsvName

    fun archivesRootDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, ROOT_FOLDER_NAME).apply { mkdirs() }
    }

    @Synchronized
    fun archiveTakeIntoCapsule(
        context: Context,
        sessionCode: String,
        projectName: String,
        speakerName: String,
        speakerGender: String = "",
        speakerAge: Int = 0,
        speakerDialect: String = "",
        allSnapshotWords: List<RemoteWordItem> = emptyList(),
        seqPos: Int,
        wordText: String,
        wordRemark: String = "",
        wordGroup: String = "",
        speakerNote: String = "",
        sourceWavFile: File,
        deleteSourceAfterCopy: Boolean,
        lowVolumeRmsThreshold: Float = 250f
    ): File? {
        if (!sourceWavFile.exists() || sourceWavFile.length() <= 44L) return null

        val safeCode = sanitizeSegment(sessionCode).ifBlank { "LINK" }.take(12)
        val safeProj = sanitizeSegment(projectName).ifBlank { S.wireless.capsuleUnnamedProject }.take(24)
        val safeSpk = sanitizeSegment(speakerName).ifBlank { S.common.defaultSpeakerFallback }.take(18)

        val root = archivesRootDir(context)
        // Reuse existing capsule folder matching (sessionCode + safeProj + safeSpk) if already created in this session
        val suffixKey = "_${safeProj}_${safeSpk}_${safeCode}"
        val existingDir = root.listFiles { f -> f.isDirectory && f.name.endsWith(suffixKey) }
            ?.maxByOrNull { it.lastModified() }

        val now = System.currentTimeMillis()
        val capsuleDir = existingDir ?: run {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(now))
            File(root, "${stamp}${suffixKey}").apply { mkdirs() }
        }
        val wavDir = File(capsuleDir, "wav").apply { mkdirs() }

        // Evaluate WAV acoustically so CSV & manifest contain accurate quality metrics
        val eval = SpeechQualityEvaluator.evaluate(sourceWavFile, lowVolumeRmsThreshold)
        val seqOneBased = (seqPos + 1).coerceAtLeast(1)
        val safeWord = sanitizeSegment(wordText).ifBlank { S.wireless.capsuleWordFallback.withArgs("index" to seqOneBased) }.take(20)
        val safeRem = sanitizeSegment(wordRemark).take(12)
        val tsSec = now / 1000L
        val wavName = buildString {
            append(String.format(Locale.US, "%04d", seqOneBased))
            append("_")
            append(safeWord)
            if (safeRem.isNotEmpty()) {
                append("_")
                append(safeRem)
            }
            append("_")
            append(tsSec)
            append(".wav")
        }

        val destWav = File(wavDir, wavName)
        if (deleteSourceAfterCopy) {
            val renamed = sourceWavFile.renameTo(destWav)
            if (!renamed) {
                sourceWavFile.copyTo(destWav, overwrite = true)
                sourceWavFile.delete()
            }
        } else {
            sourceWavFile.copyTo(destWav, overwrite = true)
        }

        // Load or initialize manifest
        val manifestFile = File(capsuleDir, MANIFEST_FILE_NAME)
        val existingJson = if (manifestFile.exists()) {
            runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)) }.getOrNull()
        } else null

        val createdAtMs = existingJson?.optLong("createdAtMs", now) ?: now
        val entriesBySeq = LinkedHashMap<Int, CapsuleWordEntry>()

        // Seed existing entries first
        existingJson?.optJSONArray("words")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = CapsuleWordEntry.fromJson(arr.getJSONObject(i))
                entriesBySeq[e.seqPos] = e
            }
        }

        // Merge full snapshot word skeleton by canonical originalIndex so mid-session shuffling never collides
        for (sw in allSnapshotWords) {
            val canonicalSlot = sw.originalIndex.coerceAtLeast(0)
            val prev = entriesBySeq[canonicalSlot]
            if (prev == null) {
                entriesBySeq[canonicalSlot] = CapsuleWordEntry(
                    seqPos = canonicalSlot,
                    text = sw.text,
                    remark = sw.remark,
                    groupName = sw.groupName,
                    speakerNote = sw.speakerNote
                )
            } else {
                entriesBySeq[canonicalSlot] = prev.copy(
                    text = sw.text.ifBlank { prev.text },
                    remark = sw.remark.ifBlank { prev.remark },
                    groupName = sw.groupName.ifBlank { prev.groupName },
                    speakerNote = sw.speakerNote.ifBlank { prev.speakerNote }
                )
            }
        }

        // Remove older superseded WAV file for this seqPos if more than 2 takes exist in wavDir
        val prefixForSeq = String.format(Locale.US, "%04d_", seqOneBased)
        val seqFiles = wavDir.listFiles { _, name -> name.startsWith(prefixForSeq) && name.endsWith(".wav") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        seqFiles.drop(2).forEach { old ->
            if (old.name != destWav.name) {
                runCatching { old.delete() }
            }
        }

        val prevEntry = entriesBySeq[seqPos]
        entriesBySeq[seqPos] = CapsuleWordEntry(
            seqPos = seqPos,
            text = wordText.ifBlank { prevEntry?.text.orEmpty() },
            remark = wordRemark.ifBlank { prevEntry?.remark.orEmpty() },
            groupName = wordGroup.ifBlank { prevEntry?.groupName.orEmpty() },
            speakerNote = speakerNote.ifBlank { prevEntry?.speakerNote.orEmpty() },
            wavFileName = destWav.name,
            durationMs = eval.totalDurationMs.toLong().coerceAtLeast(100L),
            qualityStatus = eval.primaryStatus,
            avgRms = eval.avgSpeechRms,
            peakAmplitude = eval.peakAmplitude,
            recordedAtMs = now
        )

        val sortedEntries = entriesBySeq.values.sortedBy { it.seqPos }
        val wordsJson = JSONArray()
        sortedEntries.forEach { wordsJson.put(it.toJson()) }

        val manifestOut = JSONObject().apply {
            put("version", 1)
            put("sessionCode", safeCode)
            put("projectName", projectName.ifBlank { S.wireless.capsuleUnnamedProject })
            put("speakerName", speakerName.ifBlank { S.common.defaultSpeakerFallback })
            put("speakerGender", speakerGender)
            put("speakerAge", speakerAge)
            put("speakerDialect", speakerDialect)
            put("createdAtMs", createdAtMs)
            put("updatedAtMs", now)
            put("words", wordsJson)
        }
        manifestFile.writeText(manifestOut.toString(2), Charsets.UTF_8)

        // Write genuine .xlsx workbook checklist
        File(capsuleDir, CSV_FILE_NAME).takeIf { it.exists() }?.delete()
        writeChecklistXlsx(File(capsuleDir, S.wireless.capsuleXlsxName), sortedEntries)
        return destWav
    }

    private fun writeChecklistXlsx(xlsxFile: File, entries: List<CapsuleWordEntry>) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val headers = S.wireless.capsuleCsvHeader.split(",").map { it.trim() }
        val rows = entries.map { e ->
            val timeStr = if (e.recordedAtMs > 0L) fmt.format(Date(e.recordedAtMs)) else ""
            val displayQuality = if (e.qualityStatus != null) {
                S.quality.displayStatus(e.qualityStatus)
            } else if (e.wavFileName.isNotBlank()) {
                S.quality.statusNormalDisplay
            } else {
                S.archive.unrecordedPlaceholder
            }
            listOf<Any?>(
                e.seqPos + 1,
                e.text,
                e.groupName,
                e.remark,
                e.speakerNote,
                e.wavFileName,
                if (e.wavFileName.isNotBlank()) e.durationMs else "",
                displayQuality,
                timeStr
            )
        }
        val bytes = XlsxSpreadsheetWriter.toByteArray(
            listOf(
                XlsxSpreadsheetWriter.SheetSpec(
                    name = S.wireless.capsuleXlsxName.substringBeforeLast('.'),
                    headers = headers,
                    rows = rows
                )
            )
        )
        xlsxFile.writeBytes(bytes)
    }

    private fun writeChecklistCsv(csvFile: File, entries: List<CapsuleWordEntry>) {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Excel
        sb.appendLine(S.wireless.capsuleCsvHeader)
        for (e in entries) {
            val timeStr = if (e.recordedAtMs > 0L) fmt.format(Date(e.recordedAtMs)) else ""
            sb.append(e.seqPos + 1).append(',')
            sb.append(escapeCsv(e.text)).append(',')
            sb.append(escapeCsv(e.groupName)).append(',')
            sb.append(escapeCsv(e.remark)).append(',')
            sb.append(escapeCsv(e.speakerNote)).append(',')
            sb.append(escapeCsv(e.wavFileName)).append(',')
            sb.append(if (e.wavFileName.isNotBlank()) e.durationMs else "").append(',')
            val displayQuality = if (e.qualityStatus != null) {
                S.quality.displayStatus(e.qualityStatus)
            } else if (e.wavFileName.isNotBlank()) {
                S.quality.statusNormalDisplay
            } else {
                S.archive.unrecordedPlaceholder
            }
            sb.append(escapeCsv(displayQuality)).append(',')
            sb.appendLine(escapeCsv(timeStr))
        }
        csvFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    suspend fun listCapsules(context: Context): List<WirelessSessionCapsuleSummary> =
        withContext(Dispatchers.IO) {
            val root = archivesRootDir(context)
            val dirs = root.listFiles { f -> f.isDirectory }.orEmpty()
            dirs.mapNotNull { dir ->
                val mf = File(dir, MANIFEST_FILE_NAME)
                val wavDir = File(dir, "wav")
                val wavFiles = wavDir.listFiles { f -> f.isFile && f.name.endsWith(".wav", true) }.orEmpty()
                if (!mf.exists() && wavFiles.isEmpty()) return@mapNotNull null

                val json = if (mf.exists()) {
                    runCatching { JSONObject(mf.readText(Charsets.UTF_8)) }.getOrNull()
                } else null

                val totalBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val wordsArr = json?.optJSONArray("words")
                var recordedInManifest = 0
                val totalWords = wordsArr?.length() ?: wavFiles.size
                if (wordsArr != null) {
                    for (i in 0 until wordsArr.length()) {
                        val fn = wordsArr.getJSONObject(i).optString("wavFileName", "")
                        if (fn.isNotBlank() && File(wavDir, fn).exists()) {
                            recordedInManifest++
                        }
                    }
                }
                val recCount = if (recordedInManifest > 0) recordedInManifest else wavFiles.size

                WirelessSessionCapsuleSummary(
                    folderName = dir.name,
                    capsuleDir = dir,
                    sessionCode = json?.optString("sessionCode", "") ?: "",
                    projectName = json?.optString("projectName", dir.name) ?: dir.name,
                    speakerName = json?.optString("speakerName", S.common.defaultSpeakerFallback) ?: S.common.defaultSpeakerFallback,
                    speakerGender = json?.optString("speakerGender", "") ?: "",
                    speakerAge = json?.optInt("speakerAge", 0) ?: 0,
                    speakerDialect = json?.optString("speakerDialect", "") ?: "",
                    totalWordsInSnapshot = totalWords.coerceAtLeast(recCount),
                    recordedTakeCount = recCount,
                    totalBytes = totalBytes,
                    createdAtMs = json?.optLong("createdAtMs", dir.lastModified()) ?: dir.lastModified(),
                    updatedAtMs = json?.optLong("updatedAtMs", dir.lastModified()) ?: dir.lastModified()
                )
            }.sortedByDescending { it.updatedAtMs }
        }

    suspend fun exportCapsuleToZip(capsuleDir: File, outputStream: OutputStream): Int =
        withContext(Dispatchers.IO) {
            if (!capsuleDir.exists()) return@withContext 0
            var fileCount = 0
            val buf = ByteArray(16 * 1024)
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
                capsuleDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val relPath = f.relativeTo(capsuleDir).path.replace('\\', '/')
                    val entry = ZipEntry("${capsuleDir.name}/$relPath")
                    entry.time = f.lastModified()
                    zos.putNextEntry(entry)
                    FileInputStream(f).use { fis ->
                        while (true) {
                            val n = fis.read(buf)
                            if (n <= 0) break
                            zos.write(buf, 0, n)
                        }
                    }
                    zos.closeEntry()
                    fileCount++
                }
            }
            fileCount
        }

    /**
     * Imports a self-contained Wireless Session Capsule as a brand-new, independent local [com.example.data.Project]
     * with freshly assigned Room primary keys so zero existing local projects or speakers are overwritten.
     */
    suspend fun importCapsuleAsLocalProject(
        context: Context,
        repository: ProjectRepository,
        capsuleDir: File
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val mf = File(capsuleDir, MANIFEST_FILE_NAME)
            val wavDir = File(capsuleDir, "wav")
            val wavFiles = wavDir.listFiles { f -> f.isFile && f.name.endsWith(".wav", true) }
                ?.sortedBy { it.name }
                .orEmpty()
            if (!mf.exists() && wavFiles.isEmpty()) error(S.wireless.capsuleIncompleteError)

            val json = if (mf.exists()) {
                runCatching { JSONObject(mf.readText(Charsets.UTF_8)) }.getOrNull() ?: JSONObject()
            } else {
                JSONObject()
            }
            val origProjName = json.optString("projectName", capsuleDir.name).trim().ifEmpty { S.wireless.linkedWordlistFallback }
            val origSpkName = json.optString("speakerName", S.wireless.linkedSpeakerFallback).trim().ifEmpty { S.wireless.linkedSpeakerFallback }
            val spkGender = json.optString("speakerGender", "男").ifBlank { "男" }
            val spkAge = json.optInt("speakerAge", 30).takeIf { it > 0 } ?: 30
            val spkDialect = json.optString("speakerDialect", "")
            val updatedAt = json.optLong("updatedAtMs", capsuleDir.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis())

            val arr = json.optJSONArray("words")
            val manifestEntries = if (arr != null && arr.length() > 0) {
                List(arr.length()) { i -> CapsuleWordEntry.fromJson(arr.getJSONObject(i)) }
                    .sortedBy { it.seqPos }
            } else {
                emptyList()
            }
            val entries = manifestEntries.ifEmpty {
                wavFiles.mapIndexed { idx, wf ->
                    val stem = wf.nameWithoutExtension
                    val parts = stem.split("_")
                    val parsedSeq = parts.firstOrNull()?.toIntOrNull()?.let { (it - 1).coerceAtLeast(0) } ?: idx
                    val wordText = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        ?: S.wireless.capsuleWordFallback.withArgs("index" to (parsedSeq + 1))
                    val remark = if (parts.size >= 4) parts.subList(2, parts.size - 1).joinToString("_") else ""
                    val eval = SpeechQualityEvaluator.evaluate(wf, 250f)
                    CapsuleWordEntry(
                        seqPos = parsedSeq,
                        text = wordText,
                        remark = remark,
                        wavFileName = wf.name,
                        durationMs = eval.totalDurationMs.toLong().coerceAtLeast(100L),
                        qualityStatus = eval.primaryStatus,
                        avgRms = eval.avgSpeechRms,
                        peakAmplitude = eval.peakAmplitude,
                        recordedAtMs = wf.lastModified()
                    )
                }.sortedBy { it.seqPos }
            }
            if (entries.isEmpty()) error(S.wireless.capsuleNoEntriesError)

            // Reuse exact matching local speaker if present, otherwise create an independent speaker profile
            val existingSpeakers = repository.allSpeakers.firstOrNull().orEmpty()
            val targetSpeakerId = existingSpeakers.find {
                it.name == origSpkName && (spkDialect.isBlank() || it.dialect == spkDialect)
            }?.id ?: repository.createSpeaker(
                name = origSpkName,
                gender = spkGender,
                age = spkAge,
                dialect = spkDialect,
                notes = S.wireless.capsuleImportSourceDesc,
                groupName = ""
            )

            val dateBadge = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(updatedAt))
            val newProjectName = "$origProjName ($dateBadge)"
            val parsedWords = entries.map { e ->
                ParsedWord(
                    text = e.text.ifBlank { S.wireless.capsuleWordFallback.withArgs("index" to (e.seqPos + 1)) },
                    remark = e.remark,
                    groupName = e.groupName
                )
            }

            val newProjectId = repository.createProjectWithParsedWords(
                name = newProjectName,
                description = S.wireless.capsuleImportSourceDesc,
                parsedWords = parsedWords,
                speakerId = targetSpeakerId,
                context = context
            )

            val createdWords = repository.getWordsForProjectSync(newProjectId).sortedBy { it.originalIndex }
            val projectAudioDir = File(context.getExternalFilesDir(null), "project_$newProjectId").apply { mkdirs() }

            entries.forEachIndexed { idx, entry ->
                val wordEntity = createdWords.getOrNull(idx) ?: return@forEachIndexed
                if (entry.speakerNote.isNotBlank()) {
                    repository.saveSpeakerWordNote(wordEntity.id, targetSpeakerId, entry.speakerNote)
                }
                if (entry.wavFileName.isNotBlank()) {
                    val srcWav = File(wavDir, entry.wavFileName)
                    if (srcWav.exists() && srcWav.length() > 44L) {
                        val ts = if (entry.recordedAtMs > 0L) entry.recordedAtMs else System.currentTimeMillis()
                        val dstName = "word_${wordEntity.id}_speaker_${targetSpeakerId}_$ts.wav"
                        val dstWav = File(projectAudioDir, dstName)
                        srcWav.copyTo(dstWav, overwrite = true)

                        repository.saveSpeakerRecording(
                            wordId = wordEntity.id,
                            speakerId = targetSpeakerId,
                            recordingFile = dstName,
                            durationMs = entry.durationMs.coerceAtLeast(100L),
                            qualityStatus = entry.qualityStatus ?: "正常",
                            avgRms = entry.avgRms,
                            peakAmplitude = entry.peakAmplitude
                        )
                    }
                }
            }

            newProjectId
        }
    }

    suspend fun deleteCapsule(capsuleDir: File): Boolean = withContext(Dispatchers.IO) {
        if (!capsuleDir.exists()) return@withContext false
        capsuleDir.deleteRecursively()
    }

    private fun sanitizeSegment(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
    }

    private fun escapeCsv(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
