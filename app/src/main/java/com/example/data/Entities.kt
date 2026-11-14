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
    val speakerGroupName: String = "", // Associated speaker group (empty = all groups)
    val enablePicturePrompts: Boolean = false // Opt-in Picture Wordlist mode (default off for zero clutter)
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


