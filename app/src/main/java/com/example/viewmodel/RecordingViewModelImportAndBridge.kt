package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AcousticAnalyzer
import com.example.audio.AudioRecorder
import com.example.audio.SpeechQualityEvaluator
import com.example.data.*
import com.example.i18n.S
import com.example.i18n.withArgs
import com.example.link.ActiveMicOwner
import com.example.link.CompletedHostTakeInfo
import com.example.link.PromptCoachingFeedback
import com.example.link.PromptDisplayPolicy
import com.example.link.RemoteProjectSummary
import com.example.link.RemoteSessionSnapshot
import com.example.link.RemoteSpeakerSummary
import com.example.link.RemoteWordItem
import com.example.link.WirelessLinkHostBridge
import com.example.link.WirelessSessionArchiveManager
import com.example.utils.ExportOptions
import com.example.utils.ExportSummary
import com.example.utils.ImportArchiveSummary
import com.example.utils.SpreadsheetWordImporter
import com.example.utils.WordImageManager
import com.example.utils.ZipUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private val LEADING_NUM_REGEX = """^\d+\s*[.、)）]\s*(?=\S)""".toRegex()
private val SINGLE_PAREN_REGEX = """^([^\s(（]+)\s*[((（]([^)）]*)[)）]$""".toRegex()
private val MULTI_PAREN_REGEX = """([^\s,，;；、\t(（]+)\s*[((（]([^)）]*)[)）]""".toRegex()
private val ASCII_OR_TONE_REGEX = """[A-Za-z0-9ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃ˥˦˧˨˩ːˑ]""".toRegex()
private val PUNCTUATION_SPLIT_REGEX = Regex("[,，;；、]+")
private val DASH_SPLIT_REGEX = Regex("\\s+[-—]\\s+")
private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")

internal fun RecordingViewModel.executeImportProjectZip(
    inputStream: InputStream,
    onFinished: (ImportArchiveSummary) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        val summary = ZipUtility.importProjectArchiveZip(
            context = context,
            repository = repository,
            inputStream = inputStream
        )
        if (summary.success && summary.projectId > 0L) {
            forceRefreshCounts()
            val importedProject = repository.getProjectById(summary.projectId)
            withContext(Dispatchers.Main) {
                if (importedProject != null) {
                    selectProject(importedProject)
                }
                onFinished(summary)
            }
        } else {
            withContext(Dispatchers.Main) {
                onFinished(summary)
            }
        }
    }
}

suspend fun RecordingViewModel.loadProjectWordsForSmartImport(projectId: Long): List<Word> =
    withContext(Dispatchers.IO) {
        repository.getWordsForProjectSync(projectId)
    }

fun RecordingViewModel.executeSmartImportPlan(
    plan: com.example.utils.SmartImportPlan,
    config: com.example.utils.SmartImportPlanConfig,
    defaultSpeakerId: Long?,
    onProgress: (com.example.utils.SmartImportProgress) -> Unit,
    onFinished: (com.example.utils.SmartImportResult) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        val result = com.example.utils.SmartImportEngine.executeImportPlan(
            context = context,
            repository = repository,
            plan = plan,
            config = config,
            existingProjects = allProjects.value,
            existingSpeakers = allSpeakers.value,
            defaultSpeakerId = defaultSpeakerId ?: currentSpeaker.value?.id,
            lowVolumeThreshold = lowVolumeThreshold.value,
            onProgress = { prog ->
                viewModelScope.launch(Dispatchers.Main) {
                    onProgress(prog)
                }
            }
        )
        notifyExternalAudioImported(result.primaryProjectId)
        val openedProject = result.primaryProjectId?.let { repository.getProjectById(it) }
        val openedSpeaker = result.primarySpeakerId?.let { repository.getSpeakerById(it) }
        withContext(Dispatchers.Main) {
            if (openedProject != null) {
                selectProjectAndSpeaker(openedProject, openedSpeaker)
            }
            onFinished(result)
        }
    }
}

internal fun RecordingViewModel.executeExportProjectAsZip(
    project: Project,
    options: ExportOptions,
    outputStream: OutputStream,
    onFinished: (ExportSummary) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        val speakerWordPairs: List<Pair<Speaker?, List<Word>>> = if (options.exportAllSpeakers) {
            val allSpks = allSpeakers.value
            val pairs = mutableListOf<Pair<Speaker?, List<Word>>>()
            for (spk in allSpks) {
                val wordsForSpk = repository.getWordsForProjectWithSpeakerSync(project.id, spk.id)
                if (wordsForSpk.any { it.isRecorded && !it.recordingFile.isNullOrEmpty() }) {
                    pairs.add(spk to wordsForSpk)
                }
            }
            if (pairs.isEmpty()) {
                val activeSpk = currentSpeaker.value ?: allSpks.firstOrNull()
                val fallbackWords = if (activeSpk != null) {
                    repository.getWordsForProjectWithSpeakerSync(project.id, activeSpk.id)
                } else {
                    repository.getWordsForProjectSync(project.id)
                }
                listOf(activeSpk to fallbackWords)
            } else {
                pairs
            }
        } else {
            val speaker = currentSpeaker.value ?: allSpeakers.value.firstOrNull()
            val wordList = if (speaker != null) {
                repository.getWordsForProjectWithSpeakerSync(project.id, speaker.id)
            } else {
                repository.getWordsForProjectSync(project.id)
            }
            listOf(speaker to wordList)
        }

        val enrichedOptions = if (options.simpleModeExport) {
            options.copy(
                textGridConfig = textGridFormatConfig.value
            )
        } else {
            options.copy(
                namingStyle = exportNamingStyle.value,
                folderPerSpeaker = if (options.exportAsTeproj) false else exportFolderPerSpeaker.value,
                appendNoteToFileName = exportAppendNoteToFileName.value,
                textGridConfig = textGridFormatConfig.value
            )
        }
        val summary = ZipUtility.zipProjectMultiSpeaker(
            context = context,
            project = project,
            speakerWordPairs = speakerWordPairs,
            options = enrichedOptions,
            targetStream = outputStream,
            preferredActiveSpeakerId = currentSpeaker.value?.id ?: project.speakerId
        )
        withContext(Dispatchers.Main) {
            onFinished(summary)
        }
    }
}

internal fun parseImportTextToParsedWordsImpl(text: String): List<ParsedWord> {
    val list = ArrayList<ParsedWord>()
    val trimmedText = text.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n").trim()
    if (trimmedText.isEmpty()) return list

    if (trimmedText.startsWith("{")) {
        try {
            val obj = org.json.JSONObject(trimmedText)
            if (obj.has("groups")) {
                val groupsArr = obj.getJSONArray("groups")
                for (i in 0 until groupsArr.length()) {
                    val groupObj = groupsArr.getJSONObject(i)
                    val groupId = groupObj.optString("id", (i + 1).toString())
                    val groupName = groupObj.optString("name", S.recording.groupFallbackName.withArgs("id" to groupId))
                    val itemsArr = groupObj.optJSONArray("items")
                    if (itemsArr != null) {
                        for (j in 0 until itemsArr.length()) {
                            val itemObj = itemsArr.getJSONObject(j)
                            val wText = itemObj.optString("text", itemObj.optString("label", "")).trim()
                            val wNote = itemObj.optString("note", itemObj.optString("remark", "")).trim()
                            if (wText.isNotEmpty()) {
                                list.add(ParsedWord(text = wText, remark = wNote, groupName = groupName))
                            }
                        }
                    }
                }
                return list
            }
        } catch (_: Exception) {}
    }

    return SpreadsheetWordImporter.parseSmartClipboardOrText(trimmedText, ::parseUnstructuredTextToParsedWords)
}

private fun parseUnstructuredTextToParsedWords(trimmedText: String): List<ParsedWord> {
    val list = ArrayList<ParsedWord>()
    var currentGroup = ""
    trimmedText.split("\n").forEach { rawLine ->
        val rawTrimmed = rawLine.replace("\uFEFF", "").trim()
        if (rawTrimmed.isEmpty() || rawTrimmed.startsWith("//")) return@forEach

        val isHeader = (rawTrimmed.startsWith("【") && rawTrimmed.endsWith("】")) ||
            (rawTrimmed.startsWith("[") && rawTrimmed.endsWith("]")) ||
            rawTrimmed.startsWith("#") ||
            (rawTrimmed.startsWith("==") && rawTrimmed.endsWith("=="))

        if (isHeader) {
            currentGroup = rawTrimmed.trim('[', ']', '【', '】', '#', '=').trim()
            return@forEach
        }

        // Strip optional leading numbering like "1. " or "01、"
        val line = rawTrimmed.replaceFirst(LEADING_NUM_REGEX, "").trim()
        if (line.isEmpty()) return@forEach

        // 1. Tab-separated columns (e.g. Excel / TSV copy-paste: Word \t Remark [\t Group])
        if (line.contains("\t")) {
            val cols = line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
            if (cols.size == 1) {
                val m = SINGLE_PAREN_REGEX.find(cols[0])
                if (m != null) {
                    list.add(ParsedWord(text = m.groupValues[1].trim(), remark = m.groupValues[2].trim(), groupName = currentGroup))
                } else {
                    list.add(ParsedWord(text = cols[0], remark = "", groupName = currentGroup))
                }
            } else if (cols.size >= 2) {
                val w = cols[0]
                val r = cols[1]
                val g = if (cols.size >= 3 && currentGroup.isEmpty()) cols[2] else currentGroup
                if (w.isNotEmpty()) {
                    list.add(ParsedWord(text = w, remark = r, groupName = g))
                }
            }
            return@forEach
        }

        // 2. Single "字词 (备注)" on the line
        val singleMatch = SINGLE_PAREN_REGEX.find(line)
        if (singleMatch != null) {
            val w = singleMatch.groupValues[1].trim()
            val r = singleMatch.groupValues[2].trim()
            if (w.isNotEmpty()) {
                list.add(ParsedWord(text = w, remark = r, groupName = currentGroup))
            }
            return@forEach
        }

        // 3. Multiple "字词(备注)" on the same line
        val allParenMatches = MULTI_PAREN_REGEX.findAll(line).toList()
        if (allParenMatches.size >= 2) {
            allParenMatches.forEach { m ->
                val w = m.groupValues[1].trim()
                val r = m.groupValues[2].trim()
                if (w.isNotEmpty()) {
                    list.add(ParsedWord(text = w, remark = r, groupName = currentGroup))
                }
            }
            return@forEach
        }

        // 4. Comma / semicolon / pause-mark (、) separated items on one line
        if (line.contains("，") || line.contains(",") || line.contains("；") || line.contains(";") || line.contains("、")) {
            val parts = line.split(PUNCTUATION_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size == 2 && !line.contains("、") && !line.contains(";") && !line.contains("；") && ASCII_OR_TONE_REGEX.containsMatchIn(parts[1])) {
                // e.g. "巴, pa55" -> single word with remark
                list.add(ParsedWord(text = parts[0], remark = parts[1], groupName = currentGroup))
            } else {
                parts.forEach { part ->
                    val cleanedPart = part.replaceFirst(LEADING_NUM_REGEX, "").trim()
                    val pm = SINGLE_PAREN_REGEX.find(cleanedPart)
                    if (pm != null) {
                        list.add(ParsedWord(text = pm.groupValues[1].trim(), remark = pm.groupValues[2].trim(), groupName = currentGroup))
                    } else {
                        val colonIdx = cleanedPart.indexOfAny(charArrayOf(':', '：'))
                        if (colonIdx > 0 && colonIdx < cleanedPart.length - 1) {
                            val w = cleanedPart.substring(0, colonIdx).trim()
                            val r = cleanedPart.substring(colonIdx + 1).trim()
                            list.add(ParsedWord(text = w, remark = r, groupName = currentGroup))
                        } else {
                            list.add(ParsedWord(text = cleanedPart, remark = "", groupName = currentGroup))
                        }
                    }
                }
            }
            return@forEach
        }

        // 5. Explicit colon or dash key-value on a single line: "巴: pa55" or "巴：pa55" or "巴 - pa55"
        val colonIdx = line.indexOfAny(charArrayOf(':', '：'))
        if (colonIdx > 0 && colonIdx < line.length - 1) {
            val w = line.substring(0, colonIdx).trim()
            val r = line.substring(colonIdx + 1).trim()
            if (w.isNotEmpty()) {
                list.add(ParsedWord(text = w, remark = r, groupName = currentGroup))
                return@forEach
            }
        }
        val dashSplit = line.split(DASH_SPLIT_REGEX, limit = 2)
        if (dashSplit.size == 2 && dashSplit[0].isNotBlank()) {
            list.add(ParsedWord(text = dashSplit[0].trim(), remark = dashSplit[1].trim(), groupName = currentGroup))
            return@forEach
        }

        // 6. Space-separated tokens on the line
        val tokens = line.split(WHITESPACE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
        when {
            tokens.size == 1 -> {
                val tok = tokens[0]
                val dashIdx = tok.indexOf('-')
                if (dashIdx > 0 && dashIdx < tok.length - 1 && ASCII_OR_TONE_REGEX.containsMatchIn(tok.substring(dashIdx + 1))) {
                    list.add(ParsedWord(text = tok.substring(0, dashIdx).trim(), remark = tok.substring(dashIdx + 1).trim(), groupName = currentGroup))
                } else {
                    list.add(ParsedWord(text = tok, remark = "", groupName = currentGroup))
                }
            }
            tokens.size == 2 -> {
                val first = tokens[0]
                val second = tokens[1]
                val secondLooksLikeRemark = ASCII_OR_TONE_REGEX.containsMatchIn(second) ||
                    second.startsWith("[") || second.startsWith("/") ||
                    first.length != second.length || first.length >= 2
                if (secondLooksLikeRemark) {
                    list.add(ParsedWord(text = first, remark = second, groupName = currentGroup))
                } else {
                    list.add(ParsedWord(text = first, remark = "", groupName = currentGroup))
                    list.add(ParsedWord(text = second, remark = "", groupName = currentGroup))
                }
            }
            else -> {
                val allShortNoAscii = tokens.all { it.length <= 3 && !ASCII_OR_TONE_REGEX.containsMatchIn(it) }
                if (allShortNoAscii) {
                    tokens.forEach { tok ->
                        list.add(ParsedWord(text = tok, remark = "", groupName = currentGroup))
                    }
                } else {
                    val w = tokens[0]
                    val r = tokens.drop(1).joinToString(" ")
                    list.add(ParsedWord(text = w, remark = r, groupName = currentGroup))
                }
            }
        }
    }
    return list
}

internal fun formatWordDraftsToTxt(drafts: List<EditableWordDraft>): String {
    val sb = StringBuilder(drafts.size * 16)
    var lastGroup = ""
    drafts.forEach { item ->
        val text = item.text.trim()
        if (text.isEmpty()) return@forEach
        val grp = item.groupName.trim()
        if (grp.isNotEmpty() && grp != lastGroup) {
            sb.append("【").append(grp).append("】\n")
            lastGroup = grp
        } else if (grp.isEmpty() && lastGroup.isNotEmpty()) {
            sb.append(S.recording.ungroupedHeader).append("\n")
            lastGroup = ""
        }
        val rmk = item.remark.trim()
        if (rmk.isNotEmpty()) {
            sb.append(text).append(" (").append(rmk).append(")\n")
        } else {
            sb.append(text).append("\n")
        }
    }
    return sb.toString().trim()
}

internal fun RecordingViewModel.createWirelessHostBridge(): WirelessLinkHostBridge {
    val vm = this
    return object : WirelessLinkHostBridge {
        override val sharedRecorder: AudioRecorder
            get() = vm.audioRecorder

        override fun buildCurrentSessionSnapshot(
            activeMicOwner: ActiveMicOwner,
            policy: PromptDisplayPolicy,
            overrideIsRecording: Boolean?,
            sessionCode: String,
            latestCoaching: PromptCoachingFeedback?
        ): RemoteSessionSnapshot {
            val proj = vm.currentProject.value
            val spk = vm.currentSpeaker.value ?: vm.allSpeakers.value.firstOrNull()
            val countsMap = vm.projectCounts.value

            val projectSummaries = vm.allProjects.value.map { p ->
                val pair = countsMap[p.id]
                RemoteProjectSummary(
                    id = p.id,
                    name = p.name,
                    wordCount = pair?.second ?: 0,
                    recordedCount = pair?.first ?: 0,
                    description = p.description
                )
            }
            val speakerSummaries = vm.allSpeakers.value.map { s ->
                RemoteSpeakerSummary(
                    id = s.id,
                    name = s.name,
                    gender = s.gender,
                    age = s.age,
                    dialect = s.dialect,
                    groupName = s.groupName
                )
            }

            val rawWords = if (proj != null) {
                vm.currentWords.value.filter { it.projectId == proj.id }
            } else {
                emptyList()
            }
            val seq = vm.wordSequence.value
            val orderedWords: List<RemoteWordItem> = if (proj == null) {
                emptyList()
            } else if (seq.isNotEmpty() && rawWords.isNotEmpty()) {
                seq.mapIndexedNotNull { pos, wIdx ->
                    val w = rawWords.getOrNull(wIdx) ?: return@mapIndexedNotNull null
                    RemoteWordItem(
                        seqPos = pos,
                        wordId = w.id,
                        text = w.text,
                        remark = w.remark,
                        groupName = w.groupName,
                        speakerNote = w.speakerNote,
                        isRecorded = w.isRecorded,
                        qualityStatus = w.qualityStatus,
                        durationMs = w.durationMs,
                        imageToken = if (proj.enablePicturePrompts) w.imagePath else "",
                        originalIndex = w.originalIndex.takeIf { it >= 0 } ?: wIdx
                    )
                }
            } else {
                rawWords.mapIndexed { pos, w ->
                    RemoteWordItem(
                        seqPos = pos,
                        wordId = w.id,
                        text = w.text,
                        remark = w.remark,
                        groupName = w.groupName,
                        speakerNote = w.speakerNote,
                        isRecorded = w.isRecorded,
                        qualityStatus = w.qualityStatus,
                        durationMs = w.durationMs,
                        imageToken = if (proj.enablePicturePrompts) w.imagePath else "",
                        originalIndex = w.originalIndex.takeIf { it >= 0 } ?: pos
                    )
                }
            }
            val recCount = orderedWords.count { it.isRecorded }
            val safePos = if (proj == null) 0 else vm.currentSeqIndex.value.coerceIn(0, (orderedWords.size - 1).coerceAtLeast(0))
            return RemoteSessionSnapshot(
                hasSnapshotArrived = true,
                sessionCode = sessionCode,
                projectId = proj?.id ?: 0L,
                projectName = proj?.name ?: S.recording.unselectedProjectFallback,
                speakerId = spk?.id ?: 0L,
                speakerName = spk?.name ?: S.common.defaultSpeakerFallback,
                currentSeqPos = safePos,
                totalWords = orderedWords.size,
                recordedCount = recCount,
                isRecording = overrideIsRecording ?: vm.audioRecorder.isRecording.value,
                isShuffleEnabled = vm.isShuffleEnabled.value,
                shufflingMode = vm.shufflingMode.value.name,
                activeMicOwner = activeMicOwner,
                carrierSentenceEnabled = vm.carrierSentenceEnabled.value,
                carrierSentenceTemplate = vm.carrierSentenceTemplate.value,
                quickToneSymbols = vm.quickToneSymbols.value,
                quickIpaInitials = vm.quickIpaInitials.value,
                quickIpaFinals = vm.quickIpaFinals.value,
                quickFieldTags = vm.quickFieldTags.value,
                availableProjects = projectSummaries,
                availableSpeakers = speakerSummaries,
                words = orderedWords,
                policy = policy,
                latestCoaching = latestCoaching
            )
        }

        override fun performLocalStartRecording() {
            vm.triggerStartRecording()
        }

        override fun performLocalStopRecording() {
            vm.triggerStopRecording()
        }

        override suspend fun performLocalStopRecordingAwait(tailHangoverMs: Long): CompletedHostTakeInfo? {
            return vm.stopAndPersistForWirelessAwait(tailHangoverMs = tailHangoverMs)
        }

        override fun performLocalSelectSeqPos(seqPos: Int) {
            vm.selectWordBySequencePosition(seqPos)
        }

        override fun performLocalNextWord(): Boolean = vm.nextWord()

        override fun performLocalPrevWord(): Boolean = vm.prevWord()

        override fun performLocalJumpNextUnrecorded(): Boolean = vm.jumpToNextUnrecorded()

        override fun performLocalToggleShuffle() {
            vm.toggleShuffle()
        }

        override fun performLocalSetShuffleEnabled(enabled: Boolean) {
            vm.setShuffleEnabled(enabled)
        }

        override fun performLocalSetShufflingMode(modeName: String) {
            val parsed = when (modeName) {
                ShufflingMode.BLOCK_BALANCED.name -> ShufflingMode.BLOCK_BALANCED
                ShufflingMode.STANDARD.name -> ShufflingMode.STANDARD
                else -> ShufflingMode.RANDOM_ALL
            }
            vm.setShufflingMode(parsed)
        }

        override fun performLocalReshuffle() {
            vm.reshuffle()
        }

        override fun performLocalSelectProject(projectId: Long) {
            vm.viewModelScope.launch(Dispatchers.Main) {
                val target = vm.allProjects.value.find { it.id == projectId }
                    ?: withContext(Dispatchers.IO) { vm.repository.allProjects.firstOrNull()?.find { it.id == projectId } }
                if (target != null) {
                    vm.selectProject(target)
                }
            }
        }

        override fun performLocalSelectSpeaker(speakerId: Long) {
            vm.viewModelScope.launch(Dispatchers.Main) {
                val target = vm.allSpeakers.value.find { it.id == speakerId }
                    ?: withContext(Dispatchers.IO) { vm.repository.getSpeakerById(speakerId) }
                if (target != null) {
                    vm.selectSpeaker(target)
                }
            }
        }

        override fun performLocalCreateSpeaker(name: String, gender: String, age: Int, dialect: String) {
            vm.createSpeaker(
                name = name,
                gender = gender,
                age = age,
                dialect = dialect,
                notes = "",
                groupName = vm.currentProject.value?.speakerGroupName ?: ""
            )
        }

        override fun performLocalToggleQualityFlag(wordId: Long) {
            val w = vm.currentWords.value.find { it.id == wordId } ?: return
            vm.toggleManualQualityFlag(w)
        }

        override fun performLocalUpdateSpeakerNote(wordId: Long, newNote: String) {
            val w = vm.currentWords.value.find { it.id == wordId } ?: return
            vm.updateSpeakerWordNote(w, newNote)
        }

        override fun resolveRecordedWavFile(projectId: Long, wordId: Long, speakerId: Long): File? {
            val w = vm.currentWords.value.find { it.id == wordId }
            val projectDir = File(vm.context.getExternalFilesDir(null), "project_$projectId")
            val explicitName = w?.recordingFile
            if (!explicitName.isNullOrBlank()) {
                val f = File(projectDir, explicitName)
                if (f.exists() && f.length() > 44L) return f
            }
            return vm.getAvailableTakesForWord(projectId, wordId, speakerId).firstOrNull()
        }

        override fun resolveWordImageFile(projectId: Long, wordId: Long, imageToken: String): File? {
            if (projectId <= 0L || imageToken.isBlank()) return null
            return WordImageManager.resolveWordImageFile(vm.context, projectId, imageToken)
        }

        override suspend fun ingestRemoteRecordedWav(
            projectId: Long,
            wordId: Long,
            speakerId: Long,
            tempWavFile: File
        ): String {
            if (!tempWavFile.exists() || tempWavFile.length() <= 44L) return "极短/空音"
            val targetSpeakerId = if (speakerId > 0L) speakerId else (vm.currentSpeaker.value?.id ?: 1L)
            val speakerGender = vm.allSpeakers.value.find { it.id == targetSpeakerId }?.gender
                ?: vm.currentSpeaker.value?.gender ?: "男"

            val projectDir = File(vm.context.getExternalFilesDir(null), "project_$projectId").apply { mkdirs() }
            val destName = "word_${wordId}_speaker_${targetSpeakerId}_${System.currentTimeMillis()}.wav"
            val destFile = File(projectDir, destName)
            tempWavFile.copyTo(destFile, overwrite = true)

            AcousticAnalyzer.getRealWaveformEnvelope(destFile, 48)
            val report = SpeechQualityEvaluator.evaluate(destFile, vm.lowVolumeThreshold.value)
            val exactDurMs = report.totalDurationMs.toLong().coerceAtLeast(100L)
            val estPeak = report.peakAmplitude.coerceIn(0, 32767)
            val estRms = report.avgSpeechRms.coerceAtLeast(0f)
            val finalStatus = vm.determineDetailedQuality(
                initialStatus = report.primaryStatus,
                peak = estPeak,
                avgRms = estRms,
                durationMs = exactDurMs,
                file = destFile,
                speakerGender = speakerGender
            )

            vm.saveRecordingWithSessionTracking(
                wordId = wordId,
                speakerId = targetSpeakerId,
                projectId = projectId,
                recordingFile = destFile.name,
                durationMs = exactDurMs,
                qualityStatus = finalStatus,
                avgRms = estRms,
                peakAmplitude = estPeak
            )
            val (files, _) = vm.collectCandidateTakesAfterStop(projectId, wordId, targetSpeakerId, destFile)
            vm.pruneOldTakesKeepingRecent(files, destFile, wordId, projectId)
            return finalStatus
        }
    }
}

internal fun RecordingViewModel.executeExportWirelessCapsuleToUri(
    capsuleDir: File,
    targetUri: Uri,
    onComplete: (Boolean, String) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        val res = runCatching {
            val out = context.contentResolver.openOutputStream(targetUri)
                ?: error(S.recording.cannotWriteExportFileErr)
            out.use { stream ->
                WirelessSessionArchiveManager.exportCapsuleToZip(capsuleDir, stream)
            }
        }
        withContext(Dispatchers.Main) {
            res.fold(
                onSuccess = {
                    onComplete(true, S.recording.exportedZipSuccessToast)
                },
                onFailure = { err ->
                    onComplete(false, S.recording.exportFailedWithErr.withArgs("msg" to (err.message ?: S.common.unknownError)))
                }
            )
        }
    }
}

internal fun RecordingViewModel.executeImportWirelessCapsuleAsProject(
    capsuleDir: File,
    onComplete: (Boolean, String) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        val res = WirelessSessionArchiveManager.importCapsuleAsLocalProject(
            context = context,
            repository = repository,
            capsuleDir = capsuleDir
        )
        var createdProject: Project? = null
        var createdSpeaker: Speaker? = null
        if (res.isSuccess) {
            val newProjId = res.getOrThrow()
            createdProject = repository.getProjectById(newProjId)
            createdSpeaker = createdProject?.let { proj ->
                repository.allSpeakers.firstOrNull()?.find { it.id == proj.speakerId }
            }
            WirelessSessionArchiveManager.deleteCapsule(capsuleDir)
            wirelessLink.notifyCapsulesChanged()
        }
        forceRefreshCounts()
        withContext(Dispatchers.Main) {
            res.fold(
                onSuccess = {
                    val proj = createdProject
                    val spk = createdSpeaker
                    if (proj != null && spk != null) {
                        selectProjectAndSpeaker(proj, spk)
                    } else if (proj != null) {
                        selectProject(proj)
                    }
                    onComplete(true, S.recording.importedToProjectListToast)
                },
                onFailure = { err ->
                    onComplete(false, S.recording.importFailedWithErr.withArgs("msg" to (err.message ?: S.common.unknownError)))
                }
            )
        }
    }
}

class RecordingViewModelFactory(
    private val application: Application,
    private val repository: ProjectRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecordingViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Parses `(wordId, speakerId)` from any PhonAcq WAV filename, including:
 * - Standard takes: `word_<wordId>_speaker_<speakerId>_<timestamp>.wav`
 * - Imported `.teproj` takes: `word_<wordId>_speaker_<speakerId>_<timestamp>_<importSeq>.wav`
 */
internal fun parseWordAndSpeakerFromWavFileName(fileName: String): Pair<Long, Long>? {
    if (!fileName.startsWith("word_") || !fileName.endsWith(".wav")) return null
    val spkMarker = "_speaker_"
    val spkIdx = fileName.indexOf(spkMarker)
    if (spkIdx <= 5) return null
    val wordId = fileName.substring(5, spkIdx).toLongOrNull() ?: return null
    val afterSpk = spkIdx + spkMarker.length
    val nextUnderscore = fileName.indexOf('_', afterSpk)
    val endOfSpk = if (nextUnderscore > afterSpk) nextUnderscore else (fileName.length - 4)
    if (endOfSpk <= afterSpk) return null
    val speakerId = fileName.substring(afterSpk, endOfSpk).toLongOrNull() ?: return null
    return wordId to speakerId
}

/**
 * Extracts the millisecond capture timestamp token immediately following `_speaker_<speakerId>_`,
 * falling back to [fallbackLastModified] when no numeric timestamp segment exists.
 */
internal fun extractWavTimestampFromFileName(fileName: String, fallbackLastModified: Long = 0L): Long {
    val spkMarker = "_speaker_"
    val spkIdx = fileName.indexOf(spkMarker)
    if (spkIdx > 0) {
        val afterSpk = spkIdx + spkMarker.length
        val tsStartUnderscore = fileName.indexOf('_', afterSpk)
        if (tsStartUnderscore > afterSpk) {
            val remainder = fileName.removeSuffix(".wav").substring(tsStartUnderscore + 1)
            val firstToken = remainder.substringBefore('_')
            firstToken.toLongOrNull()?.let { return it }
        }
    }
    val tailToken = fileName.substringAfterLast('_').removeSuffix(".wav").toLongOrNull()
    return tailToken ?: fallbackLastModified
}
