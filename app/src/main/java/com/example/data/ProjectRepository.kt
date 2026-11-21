package com.example.data

import android.content.Context
import com.example.i18n.S
import com.example.utils.WordImageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

class ProjectRepository(private val database: AppDatabase) {
    private val projectDao = database.projectDao()
    private val wordDao = database.wordDao()
    private val speakerDao = database.speakerDao()
    private val speakerRecordingDao = database.speakerRecordingDao()
    private val speakerWordNoteDao = database.speakerWordNoteDao()

    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()
    val allSpeakers: Flow<List<Speaker>> = speakerDao.getAllSpeakers()

    fun getWordsForProject(projectId: Long): Flow<List<Word>> {
        return wordDao.getWordsForProject(projectId)
    }

    fun getWordsForProjectWithSpeaker(projectId: Long, speakerId: Long): Flow<List<Word>> {
        return wordDao.getWordsForProjectWithSpeaker(projectId, speakerId)
    }

    suspend fun getWordsForProjectWithSpeakerSync(projectId: Long, speakerId: Long): List<Word> {
        return wordDao.getWordsForProjectWithSpeakerSync(projectId, speakerId)
    }

    suspend fun getWordsForProjectSync(projectId: Long): List<Word> {
        return wordDao.getWordsForProjectSync(projectId)
    }

    fun getProjectWordCounts(projectId: Long): Flow<Pair<Int, Int>> {
        return combine(
            wordDao.getWordCountFlow(projectId),
            wordDao.getRecordedCountFlow(projectId, 1L)
        ) { total, recorded ->
            total to recorded
        }
    }

    fun getProjectWordCountsForSpeaker(projectId: Long, speakerId: Long): Flow<Pair<Int, Int>> {
        return combine(
            wordDao.getWordCountFlow(projectId),
            wordDao.getRecordedCountFlow(projectId, speakerId)
        ) { total, recorded ->
            total to recorded
        }
    }

    suspend fun getProjectWordCountsSync(projectId: Long): Pair<Int, Int> {
        return wordDao.getWordCount(projectId) to wordDao.getRecordedCount(projectId, 1L)
    }

    suspend fun getProjectWordCountsForSpeakerSync(projectId: Long, speakerId: Long): Pair<Int, Int> {
        return wordDao.getWordCount(projectId) to wordDao.getRecordedCount(projectId, speakerId)
    }

    suspend fun getRecordedSpeakerIdsInProjectSync(projectId: Long): Set<Long> {
        val result = LinkedHashSet<Long>()
        for (t in wordDao.getAllProjectSpeakerRecordingCounts()) {
            if (t.projectId == projectId && t.recordedCount > 0) {
                result.add(t.speakerId)
            }
        }
        return result
    }

    suspend fun getProjectMultiSpeakerSummarySync(
        project: Project,
        allSpeakers: List<Speaker>,
        ignoreSpeakerGroups: Boolean = false
    ): ProjectMultiSpeakerSummary {
        val totalWords = wordDao.getWordCount(project.id)
        val totalRecs = wordDao.getTotalRecordingsInProject(project.id)
        val progressList = mutableListOf<SpeakerProgressInfo>()
        val assignedSet = project.assignedSpeakerIdSet

        for (spk in allSpeakers) {
            val recCount = wordDao.getRecordedCount(project.id, spk.id)
            if (project.isSpeakerCandidate(spk, recCount, ignoreSpeakerGroups, assignedSet)) {
                progressList.add(
                    SpeakerProgressInfo(
                        speaker = spk,
                        recordedCount = recCount,
                        totalWords = totalWords
                    )
                )
            }
        }
        return ProjectMultiSpeakerSummary(
            totalWords = totalWords,
            totalRecordingsAllSpeakers = totalRecs,
            speakerProgressList = progressList
        )
    }

    /**
     * Batch-computes counts and multi-speaker summaries for ALL projects in just 2 SQL queries
     * instead of O(Projects * Speakers) individual synchronous Room queries.
     */
    suspend fun getAllProjectsDashboardBatchSync(
        projects: List<Project>,
        allSpeakers: List<Speaker>,
        activeSpeakerId: Long?,
        ignoreSpeakerGroups: Boolean = false
    ): Pair<Map<Long, Pair<Int, Int>>, Map<Long, ProjectMultiSpeakerSummary>> {
        if (projects.isEmpty()) return emptyMap<Long, Pair<Int, Int>>() to emptyMap()

        val wordCountsByProject = wordDao.getAllProjectWordCounts()
            .associate { it.projectId to it.wordCount }

        val recTuples = wordDao.getAllProjectSpeakerRecordingCounts()
        val recByProjectAndSpeaker = HashMap<Pair<Long, Long>, Int>(recTuples.size * 2)
        val totalRecsByProject = HashMap<Long, Int>()
        for (t in recTuples) {
            recByProjectAndSpeaker[t.projectId to t.speakerId] = t.recordedCount
            totalRecsByProject[t.projectId] = (totalRecsByProject[t.projectId] ?: 0) + t.recordedCount
        }

        val fallbackSpkId = activeSpeakerId ?: 1L
        val countsMap = HashMap<Long, Pair<Int, Int>>(projects.size * 2)
        val summariesMap = HashMap<Long, ProjectMultiSpeakerSummary>(projects.size * 2)

        for (p in projects) {
            val totalWords = wordCountsByProject[p.id] ?: 0
            val activeRecorded = recByProjectAndSpeaker[p.id to fallbackSpkId] ?: 0
            countsMap[p.id] = totalWords to activeRecorded

            val totalRecs = totalRecsByProject[p.id] ?: 0
            val assignedSet = p.assignedSpeakerIdSet
            val progressList = ArrayList<SpeakerProgressInfo>(allSpeakers.size)
            for (spk in allSpeakers) {
                val recCount = recByProjectAndSpeaker[p.id to spk.id] ?: 0
                if (p.isSpeakerCandidate(spk, recCount, ignoreSpeakerGroups, assignedSet)) {
                    progressList.add(
                        SpeakerProgressInfo(
                            speaker = spk,
                            recordedCount = recCount,
                            totalWords = totalWords
                        )
                    )
                }
            }
            summariesMap[p.id] = ProjectMultiSpeakerSummary(
                totalWords = totalWords,
                totalRecordingsAllSpeakers = totalRecs,
                speakerProgressList = progressList
            )
        }
        return countsMap to summariesMap
    }

    suspend fun getProjectById(id: Long): Project? {
        return projectDao.getProjectById(id)
    }

    suspend fun getWordById(id: Long): Word? {
        return wordDao.getWordById(id)
    }

    suspend fun getWordByIdWithSpeaker(id: Long, speakerId: Long): Word? {
        return wordDao.getWordByIdWithSpeaker(id, speakerId)
    }

    private val bracketRegex = """^([^\s(（]+)\s*[((（]([^)）]+)[)）]""".toRegex()
    private val whitespaceSplitRegex = Regex("\\s+")

    fun parseWordAndRemark(line: String): Pair<String, String> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return "" to ""

        val matchResult = bracketRegex.find(trimmed)
        if (matchResult != null) {
            val word = matchResult.groupValues[1].trim()
            val remark = matchResult.groupValues[2].trim()
            return word to remark
        }

        val separators = listOf(",", "，", ":", "：", "-", "/", "\t")
        for (sep in separators) {
            if (trimmed.contains(sep)) {
                val parts = trimmed.split(sep, limit = 2)
                val w = parts[0].trim()
                val r = parts[1].trim()
                if (w.isNotEmpty()) {
                    return w to r
                }
            }
        }

        val spaceParts = trimmed.split(whitespaceSplitRegex, limit = 2)
        if (spaceParts.size == 2) {
            return spaceParts[0].trim() to spaceParts[1].trim()
        }

        return trimmed to ""
    }

    suspend fun createProjectWithParsedWords(
        name: String,
        description: String,
        parsedWords: List<ParsedWord>,
        speakerId: Long = 1L,
        speakerGroupName: String = "",
        enablePicturePrompts: Boolean = false,
        context: Context? = null,
        assignedSpeakerIds: String = ""
    ): Long {
        val normalizedAssigned = formatAssignedSpeakerIds(parseAssignedSpeakerIds(assignedSpeakerIds))
        val project = Project(
            name = name,
            description = description,
            speakerId = speakerId,
            speakerGroupName = if (normalizedAssigned.isNotBlank()) "" else speakerGroupName.trim(),
            enablePicturePrompts = enablePicturePrompts,
            assignedSpeakerIds = normalizedAssigned
        )
        val projectId = projectDao.insertProject(project)

        val words = parsedWords.mapIndexed { index, parsed ->
            val committedImg = if (context != null && parsed.imagePath.isNotBlank()) {
                WordImageManager.commitImageToProjectSync(context, projectId, parsed.imagePath)
            } else {
                parsed.imagePath.trim()
            }
            Word(
                projectId = projectId,
                text = parsed.text,
                originalIndex = index,
                remark = parsed.remark,
                groupName = parsed.groupName,
                imagePath = committedImg
            )
        }
        wordDao.insertWords(words)
        return projectId
    }

    suspend fun createProject(
        name: String,
        description: String,
        wordTexts: List<String>,
        speakerId: Long = 1L,
        speakerGroupName: String = "",
        enablePicturePrompts: Boolean = false,
        context: Context? = null,
        assignedSpeakerIds: String = ""
    ): Long {
        val parsed = wordTexts.map { text ->
            val (wText, wRemark) = parseWordAndRemark(text)
            ParsedWord(text = wText, remark = wRemark, groupName = "")
        }
        return createProjectWithParsedWords(
            name = name,
            description = description,
            parsedWords = parsed,
            speakerId = speakerId,
            speakerGroupName = speakerGroupName,
            enablePicturePrompts = enablePicturePrompts,
            context = context,
            assignedSpeakerIds = assignedSpeakerIds
        )
    }

    suspend fun updateProject(project: Project) {
        projectDao.updateProject(project)
    }

    suspend fun updateWord(word: Word) {
        wordDao.updateWord(word)
    }

    suspend fun addWordToProject(
        projectId: Long,
        text: String,
        remark: String,
        groupName: String = "",
        imagePath: String = "",
        context: Context? = null
    ): Long {
        val nextIndex = wordDao.getMaxOriginalIndex(projectId) + 1
        val committedImg = if (context != null && imagePath.isNotBlank()) {
            WordImageManager.commitImageToProjectSync(context, projectId, imagePath)
        } else {
            imagePath.trim()
        }
        val newWord = Word(
            projectId = projectId,
            text = text.trim(),
            originalIndex = nextIndex,
            remark = remark.trim(),
            groupName = groupName.trim(),
            imagePath = committedImg
        )
        return wordDao.insertWord(newWord)
    }

    /**
     * Inserts a literary/colloquial or multi-pronunciation variant word immediately after [baseWord],
     * optionally copying [copyFromWavFile] into a fresh recording for [speakerId].
     */
    suspend fun insertVariantWordAfter(
        baseWord: Word,
        variantText: String,
        variantRemark: String,
        copyFromWavFile: File?,
        speakerId: Long?,
        speakerNote: String = "",
        qualityStatus: String? = "正常",
        context: Context
    ): Word? {
        val cleanText = variantText.trim()
        if (cleanText.isEmpty()) return null

        wordDao.shiftOriginalIndicesAfter(baseWord.projectId, baseWord.originalIndex)
        val insertedId = wordDao.insertWord(
            Word(
                projectId = baseWord.projectId,
                text = cleanText,
                originalIndex = baseWord.originalIndex + 1,
                remark = variantRemark.trim(),
                groupName = baseWord.groupName.trim(),
                imagePath = baseWord.imagePath.trim()
            )
        )

        if (speakerId != null) {
            if (speakerNote.isNotBlank()) {
                saveSpeakerWordNote(insertedId, speakerId, speakerNote)
            }
            if (copyFromWavFile != null && copyFromWavFile.exists() && copyFromWavFile.length() > 44L) {
                val projDir = File(context.getExternalFilesDir(null), "project_${baseWord.projectId}").apply { mkdirs() }
                val destName = "word_${insertedId}_speaker_${speakerId}_${System.currentTimeMillis()}.wav"
                val destFile = File(projDir, destName)
                copyFromWavFile.copyTo(destFile, overwrite = true)

                val eval = com.example.audio.SpeechQualityEvaluator.evaluate(destFile)
                val durMs = eval.totalDurationMs.toLong().coerceAtLeast(100L)
                saveSpeakerRecording(
                    wordId = insertedId,
                    speakerId = speakerId,
                    recordingFile = destName,
                    durationMs = durMs,
                    qualityStatus = qualityStatus ?: eval.primaryStatus,
                    avgRms = eval.avgSpeechRms.coerceAtLeast(0f),
                    peakAmplitude = eval.peakAmplitude.coerceIn(0, 32767)
                )
            }
            return wordDao.getWordByIdWithSpeaker(insertedId, speakerId) ?: wordDao.getWordById(insertedId)
        }
        return wordDao.getWordById(insertedId)
    }

    suspend fun deleteWordFromProject(word: Word, context: Context) {
        val projDir = File(context.getExternalFilesDir(null), "project_${word.projectId}")
        if (projDir.exists()) {
            projDir.listFiles { _, name -> name.startsWith("word_${word.id}_") }?.forEach {
                try { it.delete() } catch (ignored: Exception) {}
            }
        }
        wordDao.deleteWordById(word.id)
        if (word.imagePath.isNotBlank()) {
            val remainingRefs = wordDao.getWordsForProjectSync(word.projectId)
                .mapNotNull { it.imagePath.takeIf { p -> p.isNotBlank() } }
                .toSet()
            WordImageManager.pruneUnreferencedProjectImages(context, word.projectId, remainingRefs)
        }
    }

    suspend fun saveEditedProjectWords(
        project: Project,
        name: String,
        description: String,
        speakerGroupName: String,
        drafts: List<EditableWordDraft>,
        context: Context,
        enablePicturePrompts: Boolean = project.enablePicturePrompts,
        assignedSpeakerIds: String = project.assignedSpeakerIds
    ): Project {
        val normalizedAssigned = formatAssignedSpeakerIds(parseAssignedSpeakerIds(assignedSpeakerIds))
        val updatedProject = project.copy(
            name = name.trim(),
            description = description.trim(),
            speakerGroupName = if (normalizedAssigned.isNotBlank()) "" else speakerGroupName.trim(),
            enablePicturePrompts = enablePicturePrompts,
            assignedSpeakerIds = normalizedAssigned
        )

        val validDrafts = drafts.filter { it.text.isNotBlank() }
        val existingWords = wordDao.getWordsForProjectSync(project.id)
        val existingById = existingWords.associateBy { it.id }
        val claimedExistingIds = HashSet<Long>(existingWords.size * 2)

        validDrafts.forEach { draft ->
            val id = draft.existingWordId
            if (id != null && existingById.containsKey(id)) {
                claimedExistingIds.add(id)
            }
        }

        // Pre-index unclaimed existing words by (group, text) and by (text) for O(1) lookup
        val unclaimedByGroupAndText = HashMap<Pair<String, String>, ArrayDeque<Word>>()
        val unclaimedByText = HashMap<String, ArrayDeque<Word>>()
        for (w in existingWords) {
            if (w.id !in claimedExistingIds) {
                val keyGT = w.groupName.trim() to w.text.trim()
                unclaimedByGroupAndText.getOrPut(keyGT) { ArrayDeque() }.addLast(w)
                unclaimedByText.getOrPut(w.text.trim()) { ArrayDeque() }.addLast(w)
            }
        }

        val resolvedDrafts = validDrafts.map { draft ->
            val cleanText = draft.text.trim()
            val cleanRemark = draft.remark.trim()
            val cleanGroup = draft.groupName.trim()
            val explicitId = draft.existingWordId
            if (explicitId != null && existingById.containsKey(explicitId)) {
                draft.copy(
                    text = cleanText,
                    remark = cleanRemark,
                    groupName = cleanGroup,
                    imagePath = draft.imagePath.trim()
                )
            } else {
                var matchedWord: Word? = null
                val gtQueue = unclaimedByGroupAndText[cleanGroup to cleanText]
                while (gtQueue != null && gtQueue.isNotEmpty()) {
                    val candidate = gtQueue.removeFirst()
                    if (candidate.id !in claimedExistingIds) {
                        matchedWord = candidate
                        break
                    }
                }
                if (matchedWord == null) {
                    val tQueue = unclaimedByText[cleanText]
                    while (tQueue != null && tQueue.isNotEmpty()) {
                        val candidate = tQueue.removeFirst()
                        if (candidate.id !in claimedExistingIds) {
                            matchedWord = candidate
                            break
                        }
                    }
                }
                if (matchedWord != null) {
                    claimedExistingIds.add(matchedWord.id)
                    val mergedImg = draft.imagePath.trim().ifEmpty { matchedWord.imagePath }
                    EditableWordDraft(
                        existingWordId = matchedWord.id,
                        text = cleanText,
                        remark = cleanRemark,
                        groupName = cleanGroup,
                        imagePath = mergedImg
                    )
                } else {
                    EditableWordDraft(
                        existingWordId = null,
                        text = cleanText,
                        remark = cleanRemark,
                        groupName = cleanGroup,
                        imagePath = draft.imagePath.trim()
                    )
                }
            }
        }

        val removedWords = existingWords.filter { it.id !in claimedExistingIds }
        if (removedWords.isNotEmpty()) {
            val projDir = File(context.getExternalFilesDir(null), "project_${project.id}")
            if (projDir.exists()) {
                val removedPrefixes = removedWords.map { "word_${it.id}_" }
                projDir.listFiles()?.forEach { file ->
                    if (file.isFile && removedPrefixes.any { file.name.startsWith(it) }) {
                        try { file.delete() } catch (ignored: Exception) {}
                    }
                }
            }
        }

        val wordsToUpdate = ArrayList<Word>(resolvedDrafts.size)
        val wordsToInsert = ArrayList<Word>()
        val activeImageNames = HashSet<String>()

        resolvedDrafts.forEachIndexed { index, draft ->
            val committedImg = if (draft.imagePath.isNotBlank()) {
                WordImageManager.commitImageToProjectSync(context, project.id, draft.imagePath)
            } else {
                ""
            }
            if (committedImg.isNotBlank()) {
                activeImageNames.add(committedImg)
            }
            val existing = draft.existingWordId?.let { existingById[it] }
            if (existing != null) {
                if (existing.text != draft.text ||
                    existing.originalIndex != index ||
                    existing.remark != draft.remark ||
                    existing.groupName != draft.groupName ||
                    existing.imagePath != committedImg
                ) {
                    wordsToUpdate.add(
                        existing.copy(
                            text = draft.text,
                            originalIndex = index,
                            remark = draft.remark,
                            groupName = draft.groupName,
                            imagePath = committedImg
                        )
                    )
                }
            } else {
                wordsToInsert.add(
                    Word(
                        projectId = project.id,
                        text = draft.text,
                        originalIndex = index,
                        remark = draft.remark,
                        groupName = draft.groupName,
                        imagePath = committedImg
                    )
                )
            }
        }

        projectDao.updateProject(updatedProject)
        if (removedWords.isNotEmpty()) {
            wordDao.deleteWordsByIds(removedWords.map { it.id })
        }
        if (wordsToUpdate.isNotEmpty()) {
            wordDao.updateWords(wordsToUpdate)
        }
        if (wordsToInsert.isNotEmpty()) {
            wordDao.insertWords(wordsToInsert)
        }
        WordImageManager.pruneUnreferencedProjectImages(context, project.id, activeImageNames)

        return updatedProject
    }

    // Speakers management with groupName support
    suspend fun createSpeaker(
        name: String,
        gender: String,
        age: Int,
        dialect: String,
        notes: String,
        groupName: String = ""
    ): Long {
        val speaker = Speaker(
            name = name,
            gender = gender,
            age = age,
            dialect = dialect,
            notes = notes,
            groupName = normalizeSpeakerGroupsString(groupName)
        )
        return speakerDao.insertSpeaker(speaker)
    }

    suspend fun deleteSpeaker(speakerId: Long) {
        speakerDao.deleteSpeakerById(speakerId)
    }

    suspend fun updateSpeaker(speaker: Speaker) {
        speakerDao.updateSpeaker(speaker)
    }

    suspend fun getSpeakerById(id: Long): Speaker? {
        return speakerDao.getSpeakerById(id)
    }

    // Speaker Recording operations
    suspend fun saveSpeakerRecording(
        wordId: Long,
        speakerId: Long,
        recordingFile: String,
        durationMs: Long,
        qualityStatus: String?,
        avgRms: Float,
        peakAmplitude: Int
    ) {
        val rec = SpeakerRecording(
            wordId = wordId,
            speakerId = speakerId,
            recordingFile = recordingFile,
            durationMs = durationMs,
            qualityStatus = qualityStatus,
            avgRms = avgRms,
            peakAmplitude = peakAmplitude
        )
        speakerRecordingDao.insertRecording(rec)
    }

    suspend fun deleteSpeakerRecording(wordId: Long, speakerId: Long) {
        speakerRecordingDao.deleteRecording(wordId, speakerId)
    }

    suspend fun getSpeakerRecording(wordId: Long, speakerId: Long): SpeakerRecording? {
        return speakerRecordingDao.getRecording(wordId, speakerId)
    }

    suspend fun getRecordingsForWord(wordId: Long): List<SpeakerRecording> {
        return speakerRecordingDao.getRecordingsForWord(wordId)
    }

    suspend fun saveSpeakerWordNote(wordId: Long, speakerId: Long, note: String) {
        val clean = note.trim()
        if (clean.isEmpty()) {
            speakerWordNoteDao.deleteNote(wordId, speakerId)
        } else {
            speakerWordNoteDao.upsertNote(
                SpeakerWordNote(
                    wordId = wordId,
                    speakerId = speakerId,
                    note = clean,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getSpeakerWordNote(wordId: Long, speakerId: Long): String {
        return speakerWordNoteDao.getNote(wordId, speakerId)?.note ?: ""
    }

    suspend fun getSpeakerNotesMapForWord(wordId: Long): Map<Long, String> {
        return speakerWordNoteDao.getNotesForWord(wordId).associate { it.speakerId to it.note }
    }

    suspend fun getAllProjectRecordingEntries(): List<ProjectRecordingEntryTuple> {
        return speakerRecordingDao.getAllProjectRecordingEntries()
    }

    suspend fun resetProjectRecordings(projectId: Long, context: Context) {
        val projectDir = File(context.getExternalFilesDir(null), "project_$projectId")
        if (projectDir.exists()) {
            projectDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".wav", ignoreCase = true)) {
                    try { f.delete() } catch (_: Exception) {}
                }
            }
        }
        wordDao.resetProjectRecordings(projectId)
    }

    suspend fun deleteProject(projectId: Long, context: Context) {
        val projectDir = File(context.getExternalFilesDir(null), "project_$projectId")
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
        projectDao.deleteProjectById(projectId)
    }

    suspend fun bootstrapDefaultProjectIfNeeded() {
        val existingSpeakers = database.speakerDao().getAllSpeakers().firstOrNull()
        if (existingSpeakers.isNullOrEmpty()) {
            createSpeaker(
                name = S.project.seedSpeakerName,
                gender = S.project.seedSpeakerGender,
                age = 25,
                dialect = S.project.seedSpeakerDialect,
                notes = S.project.seedSpeakerNotes,
                groupName = S.project.seedSpeakerGroup
            )
        }

        val existing = getFirstProjectSync()
        if (existing == null) {
            createProject(
                name = S.project.seedProjectName,
                description = S.project.seedProjectDescription,
                wordTexts = S.project.seedProjectWords,
                speakerGroupName = S.project.seedProjectGroup
            )
        }
    }

    private suspend fun getFirstProjectSync(): Project? {
        val list = database.projectDao().getAllProjects().firstOrNull()
        return list?.firstOrNull()
    }
}
