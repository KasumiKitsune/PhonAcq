package com.example.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val speakerId: Long = 1L, // Last active speaker ID remembered per project
    val speakerGroupName: String = "", // Legacy associated speaker group (kept for backwards compatibility)
    val enablePicturePrompts: Boolean = false, // Opt-in Picture Wordlist mode (default off for zero clutter)
    val assignedSpeakerIds: String = "" // Explicit comma-separated speaker IDs assigned to this project (e.g. "1,2,5")
)

@Immutable
@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val text: String,
    val originalIndex: Int,
    val isRecorded: Boolean = false,
    val recordingFile: String? = null,
    val recordedAt: Long? = null,
    val durationMs: Long = 0L,
    val qualityStatus: String? = null,
    val avgRms: Float = 0f,
    val peakAmplitude: Int = 0,
    val remark: String = "",
    val groupName: String = "",
    val speakerNote: String = "",
    val imagePath: String = ""
)

@Immutable
@Entity(tableName = "speakers")
data class Speaker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val gender: String,
    val age: Int,
    val dialect: String,
    val notes: String = "",
    val groupName: String = "", // 发音人所属分组（如：苏州点、杭州点、老年组）
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
@Entity(
    tableName = "speaker_recordings",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Speaker::class,
            parentColumns = ["id"],
            childColumns = ["speakerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["wordId"]),
        Index(value = ["speakerId"]),
        Index(value = ["wordId", "speakerId"], unique = true)
    ]
)
data class SpeakerRecording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val speakerId: Long,
    val recordingFile: String,
    val recordedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val qualityStatus: String? = null,
    val avgRms: Float = 0f,
    val peakAmplitude: Int = 0
)

@Immutable
@Entity(
    tableName = "speaker_word_notes",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["id"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Speaker::class,
            parentColumns = ["id"],
            childColumns = ["speakerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["wordId"]),
        Index(value = ["speakerId"]),
        Index(value = ["wordId", "speakerId"], unique = true)
    ]
)
data class SpeakerWordNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val speakerId: Long,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Immutable
data class ParsedWord(
    val text: String,
    val remark: String = "",
    val groupName: String = "",
    val imagePath: String = ""
)

@Immutable
data class EditableWordDraft(
    val existingWordId: Long? = null,
    val text: String,
    val remark: String = "",
    val groupName: String = "",
    val imagePath: String = ""
)

@Immutable
data class SpeakerProgressInfo(
    val speaker: Speaker,
    val recordedCount: Int,
    val totalWords: Int
) {
    val recordedWords: Int get() = recordedCount
    val hasStarted: Boolean get() = recordedCount > 0
    val isCompleted: Boolean get() = totalWords > 0 && recordedCount >= totalWords
}

@Stable
data class ProjectMultiSpeakerSummary(
    val totalWords: Int = 0,
    val totalRecordingsAllSpeakers: Int = 0,
    val speakerProgressList: List<SpeakerProgressInfo> = emptyList()
) {
    val totalRecordingsCount: Int get() = totalRecordingsAllSpeakers
    val candidateSpeakerCount: Int get() = speakerProgressList.size
    val startedSpeakerCount: Int get() = speakerProgressList.count { it.hasStarted }
    val completedSpeakerCount: Int get() = speakerProgressList.count { it.isCompleted }
}

data class ProjectWordCountTuple(
    val projectId: Long,
    val wordCount: Int
)

data class ProjectSpeakerRecordingCountTuple(
    val projectId: Long,
    val speakerId: Long,
    val recordedCount: Int
)

data class ProjectRecordingEntryTuple(
    val projectId: Long,
    val wordId: Long,
    val speakerId: Long,
    val recordingFile: String,
    val durationMs: Long,
    val qualityStatus: String?
)

enum class ShufflingMode {
    STANDARD,
    RANDOM_ALL,
    BLOCK_BALANCED
}

private val SPEAKER_GROUP_SPLIT_REGEX = Regex("[,，、/|;；]+")
private val parsedSpeakerGroupsCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

fun parseSpeakerGroups(rawGroupName: String): List<String> {
    if (rawGroupName.isBlank()) return emptyList()
    parsedSpeakerGroupsCache[rawGroupName]?.let { return it }
    val parsed = rawGroupName
        .split(SPEAKER_GROUP_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (parsedSpeakerGroupsCache.size > 512) {
        parsedSpeakerGroupsCache.clear()
    }
    parsedSpeakerGroupsCache[rawGroupName] = parsed
    return parsed
}

fun formatSpeakerGroups(groups: Collection<String>): String {
    return groups
        .flatMap { parseSpeakerGroups(it) }
        .distinct()
        .joinToString("、")
}

fun normalizeSpeakerGroupsString(rawGroupName: String): String {
    return formatSpeakerGroups(parseSpeakerGroups(rawGroupName))
}

fun toggleSpeakerGroupInRaw(rawGroupName: String, targetGroup: String): String {
    val cleanTarget = targetGroup.trim()
    if (cleanTarget.isEmpty()) return rawGroupName
    val current = parseSpeakerGroups(rawGroupName).toMutableList()
    val existingIdx = current.indexOfFirst { it.equals(cleanTarget, ignoreCase = true) }
    if (existingIdx >= 0) {
        current.removeAt(existingIdx)
    } else {
        current.add(cleanTarget)
    }
    return formatSpeakerGroups(current)
}

val Speaker.groupList: List<String>
    get() = parseSpeakerGroups(groupName)

fun Speaker.belongsToGroup(targetGroup: String): Boolean {
    val cleanTarget = targetGroup.trim()
    if (cleanTarget.isEmpty()) return true
    return groupList.any { it.equals(cleanTarget, ignoreCase = true) }
}

fun Speaker.matchesProjectGroup(projectGroupName: String): Boolean {
    if (projectGroupName.isBlank()) return true
    val projGroups = parseSpeakerGroups(projectGroupName)
    if (projGroups.isEmpty()) return true
    val spkGroups = this.groupList
    return projGroups.any { pg -> spkGroups.any { sg -> sg.equals(pg, ignoreCase = true) } }
}

private val ASSIGNED_SPEAKER_ID_SPLIT_REGEX = Regex("[,，\\s;；]+")

fun parseAssignedSpeakerIds(raw: String): LinkedHashSet<Long> {
    val result = LinkedHashSet<Long>()
    if (raw.isBlank()) return result
    raw.split(ASSIGNED_SPEAKER_ID_SPLIT_REGEX).forEach { token ->
        val id = token.trim().toLongOrNull()
        if (id != null && id > 0L) {
            result.add(id)
        }
    }
    return result
}

fun formatAssignedSpeakerIds(ids: Collection<Long>): String {
    if (ids.isEmpty()) return ""
    val unique = LinkedHashSet<Long>(ids.size)
    ids.forEach { id ->
        if (id > 0L) unique.add(id)
    }
    return unique.joinToString(",")
}

val Project.assignedSpeakerIdSet: LinkedHashSet<Long>
    get() = parseAssignedSpeakerIds(assignedSpeakerIds)

/**
 * Three-state backward-compatible rule determining whether [speaker] belongs to this [Project]'s
 * candidate roster:
 * 1. Explicit [Project.assignedSpeakerIds] (highest priority, active in both Simple & Advanced modes):
 *    `speaker.id in assignedSet || recordedCount > 0`.
 * 2. Legacy [Project.speakerGroupName] when [Project.assignedSpeakerIds] is blank:
 *    `if (ignoreSpeakerGroups) true else speaker.matchesProjectGroup(speakerGroupName) || recordedCount > 0`.
 * 3. Both blank: all speakers in the global library are candidates.
 */
fun Project.isSpeakerCandidate(
    speaker: Speaker,
    recordedCount: Int,
    ignoreSpeakerGroups: Boolean = false,
    parsedAssignedIds: Set<Long> = this.assignedSpeakerIdSet
): Boolean {
    return when {
        assignedSpeakerIds.isNotBlank() && parsedAssignedIds.isNotEmpty() -> {
            speaker.id in parsedAssignedIds || recordedCount > 0
        }
        speakerGroupName.isNotBlank() -> {
            (if (ignoreSpeakerGroups) true else speaker.matchesProjectGroup(speakerGroupName)) || recordedCount > 0
        }
        else -> true
    }
}


