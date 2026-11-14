package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)
}

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE projectId = :projectId ORDER BY originalIndex ASC")
    fun getWordsForProject(projectId: Long): Flow<List<Word>>

    @Query("""
        SELECT w.id, w.projectId, w.text, w.originalIndex, 
               (CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END) as isRecorded,
               r.recordingFile as recordingFile,
               r.recordedAt as recordedAt,
               r.durationMs as durationMs,
               r.qualityStatus as qualityStatus,
               r.avgRms as avgRms,
               r.peakAmplitude as peakAmplitude,
               w.remark as remark,
               w.groupName as groupName,
               COALESCE(n.note, '') as speakerNote,
               w.imagePath as imagePath
         FROM words w
         LEFT JOIN speaker_recordings r ON w.id = r.wordId AND r.speakerId = :speakerId
         LEFT JOIN speaker_word_notes n ON w.id = n.wordId AND n.speakerId = :speakerId
         WHERE w.projectId = :projectId
         ORDER BY w.originalIndex ASC
     """)
    fun getWordsForProjectWithSpeaker(projectId: Long, speakerId: Long): Flow<List<Word>>

    @Query("""
         SELECT w.id, w.projectId, w.text, w.originalIndex, 
                (CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END) as isRecorded,
                r.recordingFile as recordingFile,
                r.recordedAt as recordedAt,
                r.durationMs as durationMs,
                r.qualityStatus as qualityStatus,
                r.avgRms as avgRms,
                r.peakAmplitude as peakAmplitude,
                w.remark as remark,
                w.groupName as groupName,
                COALESCE(n.note, '') as speakerNote,
                w.imagePath as imagePath
         FROM words w
         LEFT JOIN speaker_recordings r ON w.id = r.wordId AND r.speakerId = :speakerId
         LEFT JOIN speaker_word_notes n ON w.id = n.wordId AND n.speakerId = :speakerId
         WHERE w.projectId = :projectId
         ORDER BY w.originalIndex ASC
     """)
    suspend fun getWordsForProjectWithSpeakerSync(projectId: Long, speakerId: Long): List<Word>

    @Query("SELECT * FROM words WHERE projectId = :projectId ORDER BY originalIndex ASC")
    suspend fun getWordsForProjectSync(projectId: Long): List<Word>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Long): Word?

    @Query("""
        SELECT w.id, w.projectId, w.text, w.originalIndex, 
               (CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END) as isRecorded,
               r.recordingFile as recordingFile,
               r.recordedAt as recordedAt,
               r.durationMs as durationMs,
               r.qualityStatus as qualityStatus,
               r.avgRms as avgRms,
               r.peakAmplitude as peakAmplitude,
               w.remark as remark,
               w.groupName as groupName,
               COALESCE(n.note, '') as speakerNote,
               w.imagePath as imagePath
        FROM words w
        LEFT JOIN speaker_recordings r ON w.id = r.wordId AND r.speakerId = :speakerId
        LEFT JOIN speaker_word_notes n ON w.id = n.wordId AND n.speakerId = :speakerId
        WHERE w.id = :id
    """)
    suspend fun getWordByIdWithSpeaker(id: Long, speakerId: Long): Word?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<Word>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word): Long

    @Update
    suspend fun updateWord(word: Word)

    @Update
    suspend fun updateWords(words: List<Word>)

    @Query("DELETE FROM words WHERE id = :wordId")
    suspend fun deleteWordById(wordId: Long)

    @Query("DELETE FROM words WHERE id IN (:wordIds)")
    suspend fun deleteWordsByIds(wordIds: List<Long>)

    @Query("SELECT COALESCE(MAX(originalIndex), -1) FROM words WHERE projectId = :projectId")
    suspend fun getMaxOriginalIndex(projectId: Long): Int

    @Query("UPDATE words SET originalIndex = originalIndex + 1 WHERE projectId = :projectId AND originalIndex > :afterIndex")
    suspend fun shiftOriginalIndicesAfter(projectId: Long, afterIndex: Int)

    @Query("DELETE FROM words WHERE projectId = :projectId")
    suspend fun deleteWordsForProject(projectId: Long)

    @Query("DELETE FROM speaker_recordings WHERE wordId IN (SELECT id FROM words WHERE projectId = :projectId)")
    suspend fun resetProjectRecordings(projectId: Long)

    @Query("SELECT COUNT(*) FROM words WHERE projectId = :projectId")
    fun getWordCountFlow(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM speaker_recordings r JOIN words w ON r.wordId = w.id WHERE w.projectId = :projectId AND r.speakerId = :speakerId")
    fun getRecordedCountFlow(projectId: Long, speakerId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE projectId = :projectId")
    suspend fun getWordCount(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM speaker_recordings r JOIN words w ON r.wordId = w.id WHERE w.projectId = :projectId AND r.speakerId = :speakerId")
    suspend fun getRecordedCount(projectId: Long, speakerId: Long): Int

    @Query("SELECT COUNT(*) FROM speaker_recordings r JOIN words w ON r.wordId = w.id WHERE w.projectId = :projectId")
    suspend fun getTotalRecordingsInProject(projectId: Long): Int

    @Query("SELECT projectId, COUNT(*) as wordCount FROM words GROUP BY projectId")
    suspend fun getAllProjectWordCounts(): List<ProjectWordCountTuple>

    @Query("SELECT w.projectId as projectId, r.speakerId as speakerId, COUNT(*) as recordedCount FROM speaker_recordings r JOIN words w ON r.wordId = w.id GROUP BY w.projectId, r.speakerId")
    suspend fun getAllProjectSpeakerRecordingCounts(): List<ProjectSpeakerRecordingCountTuple>
}

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speakers ORDER BY groupName ASC, name ASC")
    fun getAllSpeakers(): Flow<List<Speaker>>

    @Query("SELECT * FROM speakers WHERE id = :id")
    suspend fun getSpeakerById(id: Long): Speaker?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeaker(speaker: Speaker): Long

    @Update
    suspend fun updateSpeaker(speaker: Speaker)

    @Delete
    suspend fun deleteSpeaker(speaker: Speaker)

    @Query("DELETE FROM speakers WHERE id = :id")
    suspend fun deleteSpeakerById(id: Long)
}

@Dao
interface SpeakerRecordingDao {
    @Query("SELECT * FROM speaker_recordings WHERE wordId = :wordId AND speakerId = :speakerId")
    suspend fun getRecording(wordId: Long, speakerId: Long): SpeakerRecording?

    @Query("SELECT * FROM speaker_recordings WHERE wordId = :wordId")
    suspend fun getRecordingsForWord(wordId: Long): List<SpeakerRecording>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: SpeakerRecording): Long

    @Query("DELETE FROM speaker_recordings WHERE wordId = :wordId AND speakerId = :speakerId")
    suspend fun deleteRecording(wordId: Long, speakerId: Long)

    @Query("DELETE FROM speaker_recordings WHERE speakerId = :speakerId")
    suspend fun deleteRecordingsForSpeaker(speakerId: Long)

    @Query("""
        SELECT w.projectId as projectId,
               r.wordId as wordId,
               r.speakerId as speakerId,
               r.recordingFile as recordingFile,
               r.durationMs as durationMs,
               r.qualityStatus as qualityStatus
        FROM speaker_recordings r
        JOIN words w ON r.wordId = w.id
    """)
    suspend fun getAllProjectRecordingEntries(): List<ProjectRecordingEntryTuple>
}

@Dao
interface SpeakerWordNoteDao {
    @Query("SELECT * FROM speaker_word_notes WHERE wordId = :wordId AND speakerId = :speakerId")
    suspend fun getNote(wordId: Long, speakerId: Long): SpeakerWordNote?

    @Query("SELECT * FROM speaker_word_notes WHERE wordId = :wordId")
    suspend fun getNotesForWord(wordId: Long): List<SpeakerWordNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: SpeakerWordNote): Long

    @Query("DELETE FROM speaker_word_notes WHERE wordId = :wordId AND speakerId = :speakerId")
    suspend fun deleteNote(wordId: Long, speakerId: Long)
}
