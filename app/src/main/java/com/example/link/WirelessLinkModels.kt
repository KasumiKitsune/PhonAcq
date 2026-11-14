package com.example.link

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.example.i18n.S
import org.json.JSONArray
import org.json.JSONObject

/**
 * Role of the local device in a wireless fieldwork collaboration session.
 * Uses unambiguous linguistic fieldwork terminology ("看读端" vs "遥控端").
 */
enum class LinkRole {
    STANDALONE,
    CONTROLLER,
    PROMPT_DISPLAY;

    val label: String
        get() = when (this) {
            STANDALONE -> S.wireless.roleStandaloneLabel
            CONTROLLER -> S.wireless.roleControllerLabel
            PROMPT_DISPLAY -> S.wireless.rolePromptLabel
        }

    val shortSubtitle: String
        get() = when (this) {
            STANDALONE -> S.wireless.roleStandaloneSub
            CONTROLLER -> S.wireless.roleControllerSub
            PROMPT_DISPLAY -> S.wireless.rolePromptSub
        }
}

/**
 * Determines which device's physical microphone captures lossless PCM audio.
 */
enum class ActiveMicOwner {
    PROMPT_DISPLAY_MIC,
    CONTROLLER_MIC;

    val label: String
        get() = when (this) {
            PROMPT_DISPLAY_MIC -> S.wireless.micPromptOwnerLabel
            CONTROLLER_MIC -> S.wireless.micControllerOwnerLabel
        }

    val description: String
        get() = when (this) {
            PROMPT_DISPLAY_MIC -> S.wireless.micPromptOwnerDesc
            CONTROLLER_MIC -> S.wireless.micControllerOwnerDesc
        }

    fun isActiveOnRole(role: LinkRole): Boolean = when (this) {
        PROMPT_DISPLAY_MIC -> role == LinkRole.PROMPT_DISPLAY
        CONTROLLER_MIC -> role == LinkRole.CONTROLLER
    }
}

/**
 * Multi-mode audio storage strategy for wireless collaboration.
 */
enum class WirelessStorageMode {
    HOST_ONLY,
    HOST_AND_CAPTURE_BACKUP,
    DUAL_FULL_MIRROR;

    val title: String
        get() = when (this) {
            HOST_ONLY -> S.wireless.storageHostOnlyTitle
            HOST_AND_CAPTURE_BACKUP -> S.wireless.storageDualMicBackupTitle
            DUAL_FULL_MIRROR -> S.wireless.storageBothFullTitle
        }

    val shortBadge: String
        get() = when (this) {
            HOST_ONLY -> S.wireless.storageHostOnlyBadge
            HOST_AND_CAPTURE_BACKUP -> S.wireless.storageDualMicBackupBadge
            DUAL_FULL_MIRROR -> S.wireless.storageBothFullBadge
        }

    val subtitle: String
        get() = when (this) {
            HOST_ONLY -> S.wireless.storageHostOnlySub
            HOST_AND_CAPTURE_BACKUP -> S.wireless.storageDualMicBackupSub
            DUAL_FULL_MIRROR -> S.wireless.storageBothFullSub
        }
}

/**
 * Controls optional speaker-friendly natural language coaching hints on 看读端 after a problematic take.
 */
enum class PromptAudioCoachingMode {
    OFF,
    GENTLE_TEXT,
    GENTLE_AND_HOLD;

    val title: String
        get() = when (this) {
            OFF -> S.wireless.coachingOffTitle
            GENTLE_TEXT -> S.wireless.coachingGentleTextTitle
            GENTLE_AND_HOLD -> S.wireless.coachingTextAndHoldTitle
        }

    val subtitle: String
        get() = when (this) {
            OFF -> S.wireless.coachingOffSub
            GENTLE_TEXT -> S.wireless.coachingGentleTextSub
            GENTLE_AND_HOLD -> S.wireless.coachingTextAndHoldSub
        }
}

/**
 * Synchronized coaching diagnostic for the most recently completed take.
 */
@Immutable
data class PromptCoachingFeedback(
    val wordId: Long,
    val seqPos: Int,
    val qualityStatus: String,
    val speakerHint: String?,
    val controllerHint: String?,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("wordId", wordId)
        put("seqPos", seqPos)
        put("qualityStatus", qualityStatus)
        put("speakerHint", speakerHint ?: "")
        put("controllerHint", controllerHint ?: "")
        put("timestampMs", timestampMs)
    }

    companion object {
        fun fromJson(j: JSONObject?): PromptCoachingFeedback? {
            if (j == null) return null
            val status = j.optString("qualityStatus", "").trim()
            if (status.isEmpty()) return null
            return PromptCoachingFeedback(
                wordId = j.optLong("wordId", 0L),
                seqPos = j.optInt("seqPos", 0),
                qualityStatus = status,
                speakerHint = j.optString("speakerHint", "").takeIf { it.isNotBlank() },
                controllerHint = j.optString("controllerHint", "").takeIf { it.isNotBlank() },
                timestampMs = j.optLong("timestampMs", 0L)
            )
        }
    }
}

/**
 * Page-turning authority granted to the speaker in front of the 看读端.
 */
enum class PromptPageTurnPolicy {
    LOCKED_BY_CONTROLLER,
    NEXT_ONLY,
    FREE_PREV_NEXT;

    val label: String
        get() = when (this) {
            LOCKED_BY_CONTROLLER -> S.wireless.pageTurnLockedTitle
            NEXT_ONLY -> S.wireless.pageTurnNextOnlyTitle
            FREE_PREV_NEXT -> S.wireless.pageTurnFreeTitle
        }

    val subtitle: String
        get() = when (this) {
            LOCKED_BY_CONTROLLER -> S.wireless.pageTurnLockedSub
            NEXT_ONLY -> S.wireless.pageTurnNextOnlySub
            FREE_PREV_NEXT -> S.wireless.pageTurnFreeSub
        }
}

/**
 * Friendly visual appearance presets exclusively for the 看读端 (Prompt Display).
 */
enum class PromptVisualPreset {
    MATERIAL_YOU,
    WARM_PAPER,
    INK_EBOOK,
    HIGH_CONTRAST_BOARD;

    val title: String
        get() = when (this) {
            MATERIAL_YOU -> S.wireless.presetSoftSystemTitle
            WARM_PAPER -> S.wireless.presetWarmPaperTitle
            INK_EBOOK -> S.wireless.presetInkPaperTitle
            HIGH_CONTRAST_BOARD -> S.wireless.presetHighContrastTitle
        }

    val subtitle: String
        get() = when (this) {
            MATERIAL_YOU -> S.wireless.presetSoftSystemSub
            WARM_PAPER -> S.wireless.presetWarmPaperSub
            INK_EBOOK -> S.wireless.presetInkPaperSub
            HIGH_CONTRAST_BOARD -> S.wireless.presetHighContrastSub
        }
}

/**
 * Recording state cue shown on the 看读端 so the speaker knows when to speak without waveform anxiety.
 */
enum class PromptRecordingIndicatorStyle {
    SOFT_HALO,
    MINIMAL_TEXT,
    SILENT_STEALTH;

    val title: String
        get() = when (this) {
            SOFT_HALO -> S.wireless.indicatorHaloTitle
            MINIMAL_TEXT -> S.wireless.indicatorMinimalTitle
            SILENT_STEALTH -> S.wireless.indicatorStealthTitle
        }

    val subtitle: String
        get() = when (this) {
            SOFT_HALO -> S.wireless.indicatorHaloSub
            MINIMAL_TEXT -> S.wireless.indicatorMinimalSub
            SILENT_STEALTH -> S.wireless.indicatorStealthSub
        }
}

/**
 * Complete permission, storage, auto-advance & visibility policy governing wireless collaboration.
 */
@Immutable
data class PromptDisplayPolicy(
    val pageTurnPolicy: PromptPageTurnPolicy = PromptPageTurnPolicy.LOCKED_BY_CONTROLLER,
    val allowPromptStartStopRecord: Boolean = false,
    val allowPromptReplayAndRetake: Boolean = false,
    val immersiveExitLock: Boolean = true,
    val visualPreset: PromptVisualPreset = PromptVisualPreset.WARM_PAPER,
    val fontScaleMultiplier: Float = 1.45f,
    val showCarrierSentence: Boolean = true,
    val showRemarkGlobally: Boolean = false,
    val revealRemarkTemporaryForCurrentWord: Boolean = false,
    val hideTextWhenImagePresent: Boolean = false,
    val toggleTextTemporaryForCurrentWord: Boolean = false,
    val showProgressCounter: Boolean = false,
    val recordingIndicatorStyle: PromptRecordingIndicatorStyle = PromptRecordingIndicatorStyle.SOFT_HALO,
    val storageMode: WirelessStorageMode = WirelessStorageMode.HOST_ONLY,
    val autoAdvanceOnHoldRelease: Boolean = true,
    val holdOnQualityIssueWhenAutoAdvance: Boolean = true,
    val audioCoachingMode: PromptAudioCoachingMode = PromptAudioCoachingMode.OFF,
    val isIntermissionBreak: Boolean = false,
    val intermissionMessage: String = S.wireless.defaultIntermissionMessage
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pageTurnPolicy", pageTurnPolicy.name)
        put("allowPromptStartStopRecord", allowPromptStartStopRecord)
        put("allowPromptReplayAndRetake", allowPromptReplayAndRetake)
        put("immersiveExitLock", immersiveExitLock)
        put("visualPreset", visualPreset.name)
        put("fontScaleMultiplier", fontScaleMultiplier.toDouble())
        put("showCarrierSentence", showCarrierSentence)
        put("showRemarkGlobally", showRemarkGlobally)
        put("revealRemarkTemporaryForCurrentWord", revealRemarkTemporaryForCurrentWord)
        put("hideTextWhenImagePresent", hideTextWhenImagePresent)
        put("toggleTextTemporaryForCurrentWord", toggleTextTemporaryForCurrentWord)
        put("showProgressCounter", showProgressCounter)
        put("recordingIndicatorStyle", recordingIndicatorStyle.name)
        put("storageMode", storageMode.name)
        put("autoAdvanceOnHoldRelease", autoAdvanceOnHoldRelease)
        put("holdOnQualityIssueWhenAutoAdvance", holdOnQualityIssueWhenAutoAdvance)
        put("audioCoachingMode", audioCoachingMode.name)
        put("isIntermissionBreak", isIntermissionBreak)
        put("intermissionMessage", intermissionMessage)
    }

    fun saveToPrefs(prefs: SharedPreferences) {
        prefs.edit()
            .putString("link_policy_v1", toJson().toString())
            .apply()
    }

    companion object {
        fun fromJson(json: JSONObject?): PromptDisplayPolicy {
            if (json == null) return PromptDisplayPolicy()
            val turnPolicy = runCatching {
                PromptPageTurnPolicy.valueOf(json.optString("pageTurnPolicy", PromptPageTurnPolicy.LOCKED_BY_CONTROLLER.name))
            }.getOrDefault(PromptPageTurnPolicy.LOCKED_BY_CONTROLLER)
            val preset = runCatching {
                PromptVisualPreset.valueOf(json.optString("visualPreset", PromptVisualPreset.WARM_PAPER.name))
            }.getOrDefault(PromptVisualPreset.WARM_PAPER)
            val recStyle = runCatching {
                PromptRecordingIndicatorStyle.valueOf(
                    json.optString("recordingIndicatorStyle", PromptRecordingIndicatorStyle.SOFT_HALO.name)
                )
            }.getOrDefault(PromptRecordingIndicatorStyle.SOFT_HALO)
            val storage = runCatching {
                WirelessStorageMode.valueOf(
                    json.optString("storageMode", WirelessStorageMode.HOST_ONLY.name)
                )
            }.getOrDefault(WirelessStorageMode.HOST_ONLY)
            val coaching = runCatching {
                PromptAudioCoachingMode.valueOf(
                    json.optString("audioCoachingMode", PromptAudioCoachingMode.OFF.name)
                )
            }.getOrDefault(PromptAudioCoachingMode.OFF)

            return PromptDisplayPolicy(
                pageTurnPolicy = turnPolicy,
                allowPromptStartStopRecord = json.optBoolean("allowPromptStartStopRecord", false),
                allowPromptReplayAndRetake = json.optBoolean("allowPromptReplayAndRetake", false),
                immersiveExitLock = json.optBoolean("immersiveExitLock", true),
                visualPreset = preset,
                fontScaleMultiplier = json.optDouble("fontScaleMultiplier", 1.45).toFloat().coerceIn(1.0f, 2.4f),
                showCarrierSentence = json.optBoolean("showCarrierSentence", true),
                showRemarkGlobally = json.optBoolean("showRemarkGlobally", false),
                revealRemarkTemporaryForCurrentWord = json.optBoolean("revealRemarkTemporaryForCurrentWord", false),
                hideTextWhenImagePresent = json.optBoolean("hideTextWhenImagePresent", false),
                toggleTextTemporaryForCurrentWord = json.optBoolean("toggleTextTemporaryForCurrentWord", false),
                showProgressCounter = json.optBoolean("showProgressCounter", false),
                recordingIndicatorStyle = recStyle,
                storageMode = storage,
                autoAdvanceOnHoldRelease = json.optBoolean("autoAdvanceOnHoldRelease", true),
                holdOnQualityIssueWhenAutoAdvance = json.optBoolean("holdOnQualityIssueWhenAutoAdvance", true),
                audioCoachingMode = coaching,
                isIntermissionBreak = json.optBoolean("isIntermissionBreak", false),
                intermissionMessage = json.optString("intermissionMessage", S.wireless.defaultIntermissionMessage)
                    .ifBlank { S.wireless.defaultIntermissionMessage }
            )
        }

        fun loadFromPrefs(prefs: SharedPreferences): PromptDisplayPolicy {
            val raw = prefs.getString("link_policy_v1", null) ?: return PromptDisplayPolicy()
            return runCatching {
                fromJson(JSONObject(raw)).copy(
                    isIntermissionBreak = false,
                    revealRemarkTemporaryForCurrentWord = false,
                    toggleTextTemporaryForCurrentWord = false
                )
            }.getOrDefault(PromptDisplayPolicy())
        }
    }
}

/**
 * Lightweight word representation synced across devices so a secondary device
 * can act as either 看读端 or 遥控端 without needing the Room DB pre-populated locally.
 */
@Immutable
data class RemoteWordItem(
    val seqPos: Int,
    val wordId: Long,
    val text: String,
    val remark: String = "",
    val groupName: String = "",
    val speakerNote: String = "",
    val isRecorded: Boolean = false,
    val qualityStatus: String? = null,
    val durationMs: Long = 0L,
    val imageToken: String = "",
    val originalIndex: Int = seqPos
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("seqPos", seqPos)
        put("wordId", wordId)
        put("text", text)
        put("remark", remark)
        put("groupName", groupName)
        put("speakerNote", speakerNote)
        put("isRecorded", isRecorded)
        put("qualityStatus", qualityStatus ?: "")
        put("durationMs", durationMs)
        put("imageToken", imageToken)
        put("originalIndex", originalIndex)
    }

    companion object {
        fun fromJson(j: JSONObject): RemoteWordItem {
            val parsedSeqPos = j.optInt("seqPos", 0)
            return RemoteWordItem(
                seqPos = parsedSeqPos,
                wordId = j.optLong("wordId", 0L),
                text = j.optString("text", ""),
                remark = j.optString("remark", ""),
                groupName = j.optString("groupName", ""),
                speakerNote = j.optString("speakerNote", ""),
                isRecorded = j.optBoolean("isRecorded", false),
                qualityStatus = j.optString("qualityStatus", "").takeIf { it.isNotBlank() },
                durationMs = j.optLong("durationMs", 0L),
                imageToken = j.optString("imageToken", ""),
                originalIndex = j.optInt("originalIndex", parsedSeqPos)
            )
        }
    }
}

/**
 * Lightweight project summary synced to 遥控端 so the investigator can switch wordlists remotely.
 */
@Immutable
data class RemoteProjectSummary(
    val id: Long,
    val name: String,
    val wordCount: Int = 0,
    val recordedCount: Int = 0,
    val description: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("wordCount", wordCount)
        put("recordedCount", recordedCount)
        put("description", description)
    }

    companion object {
        fun fromJson(j: JSONObject): RemoteProjectSummary = RemoteProjectSummary(
            id = j.optLong("id", 0L),
            name = j.optString("name", ""),
            wordCount = j.optInt("wordCount", 0),
            recordedCount = j.optInt("recordedCount", 0),
            description = j.optString("description", "")
        )
    }
}

/**
 * Lightweight speaker profile summary synced to 遥控端 so the investigator can switch or add speakers remotely.
 */
@Immutable
data class RemoteSpeakerSummary(
    val id: Long,
    val name: String,
    val gender: String = "",
    val age: Int = 0,
    val dialect: String = "",
    val groupName: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("gender", gender)
        put("age", age)
        put("dialect", dialect)
        put("groupName", groupName)
    }

    companion object {
        fun fromJson(j: JSONObject): RemoteSpeakerSummary = RemoteSpeakerSummary(
            id = j.optLong("id", 0L),
            name = j.optString("name", ""),
            gender = j.optString("gender", ""),
            age = j.optInt("age", 0),
            dialect = j.optString("dialect", ""),
            groupName = j.optString("groupName", "")
        )
    }
}

/**
 * Full synchronized session state seen by both 看读端 and 遥控端.
 */
@Immutable
data class RemoteSessionSnapshot(
    val hasSnapshotArrived: Boolean = false,
    val sessionCode: String = "",
    val projectId: Long = 0L,
    val projectName: String = S.recording.unselectedProjectFallback,
    val speakerId: Long = 0L,
    val speakerName: String = S.common.defaultSpeakerFallback,
    val currentSeqPos: Int = 0,
    val totalWords: Int = 0,
    val recordedCount: Int = 0,
    val isRecording: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val shufflingMode: String = "RANDOM_ALL",
    val activeMicOwner: ActiveMicOwner = ActiveMicOwner.PROMPT_DISPLAY_MIC,
    val carrierSentenceEnabled: Boolean = false,
    val carrierSentenceTemplate: String = S.recording.defaultCarrierTemplate,
    val quickToneSymbols: List<String> = emptyList(),
    val quickIpaInitials: List<String> = emptyList(),
    val quickIpaFinals: List<String> = emptyList(),
    val quickFieldTags: List<String> = emptyList(),
    val availableProjects: List<RemoteProjectSummary> = emptyList(),
    val availableSpeakers: List<RemoteSpeakerSummary> = emptyList(),
    val words: List<RemoteWordItem> = emptyList(),
    val policy: PromptDisplayPolicy = PromptDisplayPolicy(),
    val latestCoaching: PromptCoachingFeedback? = null
) {
    val currentWord: RemoteWordItem?
        get() = if (projectId <= 0L) null else (words.getOrNull(currentSeqPos) ?: words.find { it.seqPos == currentSeqPos })

    val currentSpeakerSummary: RemoteSpeakerSummary?
        get() = availableSpeakers.find { it.id == speakerId } ?: availableSpeakers.firstOrNull { it.name == speakerName }

    fun toJson(includeWordList: Boolean = true): JSONObject = JSONObject().apply {
        put("hasSnapshotArrived", true)
        put("sessionCode", sessionCode)
        put("projectId", projectId)
        put("projectName", projectName)
        put("speakerId", speakerId)
        put("speakerName", speakerName)
        put("currentSeqPos", currentSeqPos)
        put("totalWords", totalWords)
        put("recordedCount", recordedCount)
        put("isRecording", isRecording)
        put("isShuffleEnabled", isShuffleEnabled)
        put("shufflingMode", shufflingMode)
        put("activeMicOwner", activeMicOwner.name)
        put("carrierSentenceEnabled", carrierSentenceEnabled)
        put("carrierSentenceTemplate", carrierSentenceTemplate)
        put("quickToneSymbols", JSONArray(quickToneSymbols))
        put("quickIpaInitials", JSONArray(quickIpaInitials))
        put("quickIpaFinals", JSONArray(quickIpaFinals))
        put("quickFieldTags", JSONArray(quickFieldTags))
        put("policy", policy.toJson())
        latestCoaching?.let { put("latestCoaching", it.toJson()) }

        val projArr = JSONArray()
        availableProjects.forEach { projArr.put(it.toJson()) }
        put("availableProjects", projArr)

        val spkArr = JSONArray()
        availableSpeakers.forEach { spkArr.put(it.toJson()) }
        put("availableSpeakers", spkArr)

        put("hasWordList", includeWordList)
        if (includeWordList) {
            val arr = JSONArray()
            words.forEach { arr.put(it.toJson()) }
            put("words", arr)
        }
    }

    companion object {
        fun fromJson(
            j: JSONObject,
            fallbackWords: List<RemoteWordItem> = emptyList(),
            fallbackProjects: List<RemoteProjectSummary> = emptyList(),
            fallbackSpeakers: List<RemoteSpeakerSummary> = emptyList()
        ): RemoteSessionSnapshot {
            val micOwner = runCatching {
                ActiveMicOwner.valueOf(j.optString("activeMicOwner", ActiveMicOwner.PROMPT_DISPLAY_MIC.name))
            }.getOrDefault(ActiveMicOwner.PROMPT_DISPLAY_MIC)

            val tones = mutableListOf<String>()
            j.optJSONArray("quickToneSymbols")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) tones.add(s)
                }
            }
            val initials = mutableListOf<String>()
            j.optJSONArray("quickIpaInitials")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) initials.add(s)
                }
            }
            val finals = mutableListOf<String>()
            j.optJSONArray("quickIpaFinals")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) finals.add(s)
                }
            }
            val tags = mutableListOf<String>()
            j.optJSONArray("quickFieldTags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) tags.add(s)
                }
            }
            val parsedProjects = j.optJSONArray("availableProjects")?.let { arr ->
                List(arr.length()) { idx -> RemoteProjectSummary.fromJson(arr.getJSONObject(idx)) }
            } ?: fallbackProjects

            val parsedSpeakers = j.optJSONArray("availableSpeakers")?.let { arr ->
                List(arr.length()) { idx -> RemoteSpeakerSummary.fromJson(arr.getJSONObject(idx)) }
            } ?: fallbackSpeakers

            val projId = j.optLong("projectId", 0L)
            val parsedWords = if (projId <= 0L) {
                emptyList()
            } else if (j.optBoolean("hasWordList", true) && j.has("words")) {
                val arr = j.optJSONArray("words")
                if (arr != null) {
                    List(arr.length()) { idx -> RemoteWordItem.fromJson(arr.getJSONObject(idx)) }
                } else fallbackWords
            } else {
                fallbackWords
            }

            return RemoteSessionSnapshot(
                hasSnapshotArrived = j.optBoolean("hasSnapshotArrived", true),
                sessionCode = j.optString("sessionCode", ""),
                projectId = projId,
                projectName = j.optString("projectName", S.recording.unselectedProjectFallback),
                speakerId = j.optLong("speakerId", 0L),
                speakerName = j.optString("speakerName", S.common.defaultSpeakerFallback),
                currentSeqPos = j.optInt("currentSeqPos", 0),
                totalWords = if (projId <= 0L) 0 else j.optInt("totalWords", parsedWords.size),
                recordedCount = if (projId <= 0L) 0 else j.optInt("recordedCount", 0),
                isRecording = j.optBoolean("isRecording", false),
                isShuffleEnabled = j.optBoolean("isShuffleEnabled", false),
                shufflingMode = j.optString("shufflingMode", "RANDOM_ALL").ifBlank { "RANDOM_ALL" },
                activeMicOwner = micOwner,
                carrierSentenceEnabled = j.optBoolean("carrierSentenceEnabled", false),
                carrierSentenceTemplate = j.optString("carrierSentenceTemplate", S.recording.defaultCarrierTemplate),
                quickToneSymbols = tones,
                quickIpaInitials = initials,
                quickIpaFinals = finals,
                quickFieldTags = tags,
                availableProjects = parsedProjects,
                availableSpeakers = parsedSpeakers,
                words = parsedWords,
                policy = PromptDisplayPolicy.fromJson(j.optJSONObject("policy")),
                latestCoaching = PromptCoachingFeedback.fromJson(j.optJSONObject("latestCoaching"))
            )
        }
    }
}

/**
 * Auto-discovered LAN / Hotspot peer hosting a PhonAcq wireless session.
 */
@Immutable
data class DiscoveredPeer(
    val hostId: String,
    val deviceName: String,
    val ipAddress: String,
    val tcpPort: Int,
    val hostRole: LinkRole,
    val projectName: String,
    val lastSeenMs: Long = System.currentTimeMillis()
) {
    val peerExpectedRole: LinkRole
        get() = if (hostRole == LinkRole.CONTROLLER) LinkRole.PROMPT_DISPLAY else LinkRole.CONTROLLER
}
