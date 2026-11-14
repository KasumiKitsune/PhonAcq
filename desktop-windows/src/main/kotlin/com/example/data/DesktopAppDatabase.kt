package com.example.data

import android.content.Context
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun wordDao(): WordDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun speakerRecordingDao(): SpeakerRecordingDao
    abstract fun speakerWordNoteDao(): SpeakerWordNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = File(Context.appDataRootDir, "phonetics_recording_database.db")
                val instance = DesktopSqliteAppDatabase(dbFile)
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Pure JVM / Windows Desktop SQLite implementation of [AppDatabase] backed by `org.xerial:sqlite-jdbc`.
 * Uses identical SQLite table schemas and SQL queries as Android Room, with reactive [MutableStateFlow]
 * invalidation so all UI [Flow] collectors update instantaneously without requiring KSP codegen on Desktop.
 */
private class DesktopSqliteAppDatabase(private val dbFile: File) : AppDatabase() {
    private val dbLock = Any()
    private val connection: Connection
    private val changeVersion = MutableStateFlow(0L)

    init {
        dbFile.parentFile?.mkdirs()
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        synchronized(dbLock) {
            connection.createStatement().use { stmt ->
                stmt.execute("PRAGMA foreign_keys = ON;")
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS projects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        speakerId INTEGER NOT NULL DEFAULT 1,
                        speakerGroupName TEXT NOT NULL DEFAULT '',
                        enablePicturePrompts INTEGER NOT NULL DEFAULT 0
                    );
                    """.trimIndent()
                )
                try {
                    stmt.execute("ALTER TABLE projects ADD COLUMN enablePicturePrompts INTEGER NOT NULL DEFAULT 0;")
                } catch (_: Exception) {
                }
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS words (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        originalIndex INTEGER NOT NULL,
                        isRecorded INTEGER NOT NULL DEFAULT 0,
                        recordingFile TEXT,
                        recordedAt INTEGER,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        qualityStatus TEXT,
                        avgRms REAL NOT NULL DEFAULT 0,
                        peakAmplitude INTEGER NOT NULL DEFAULT 0,
                        remark TEXT NOT NULL DEFAULT '',
                        groupName TEXT NOT NULL DEFAULT '',
                        speakerNote TEXT NOT NULL DEFAULT '',
                        imagePath TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE
                    );
                    """.trimIndent()
                )
                try {
                    stmt.execute("ALTER TABLE words ADD COLUMN speakerNote TEXT NOT NULL DEFAULT '';")
                } catch (_: Exception) {
                }
                try {
                    stmt.execute("ALTER TABLE words ADD COLUMN imagePath TEXT NOT NULL DEFAULT '';")
                } catch (_: Exception) {
                }
                stmt.execute("CREATE INDEX IF NOT EXISTS index_words_projectId ON words(projectId);")
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS speakers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        gender TEXT NOT NULL,
                        age INTEGER NOT NULL,
                        dialect TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        groupName TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    );
                    """.trimIndent()
                )
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS speaker_recordings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        wordId INTEGER NOT NULL,
                        speakerId INTEGER NOT NULL,
                        recordingFile TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        qualityStatus TEXT,
                        avgRms REAL NOT NULL DEFAULT 0,
                        peakAmplitude INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(wordId) REFERENCES words(id) ON DELETE CASCADE,
                        FOREIGN KEY(speakerId) REFERENCES speakers(id) ON DELETE CASCADE
                    );
                    """.trimIndent()
                )
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_speaker_recordings_wordId_speakerId ON speaker_recordings(wordId, speakerId);")
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS speaker_word_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        wordId INTEGER NOT NULL,
                        speakerId INTEGER NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(wordId) REFERENCES words(id) ON DELETE CASCADE,
                        FOREIGN KEY(speakerId) REFERENCES speakers(id) ON DELETE CASCADE
                    );
                    """.trimIndent()
                )
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_speaker_word_notes_wordId_speakerId ON speaker_word_notes(wordId, speakerId);")
            }
        }
    }

    private fun notifyChanged() {
        changeVersion.value = changeVersion.value + 1L
    }

    private val projectDaoImpl = object : ProjectDao {
        override fun getAllProjects(): Flow<List<Project>> = changeVersion.map {
            withContext(Dispatchers.IO) {
                synchronized(dbLock) {
                    val list = ArrayList<Project>()
                    connection.prepareStatement("SELECT id, name, createdAt, description, speakerId, speakerGroupName, enablePicturePrompts FROM projects ORDER BY createdAt DESC").use { ps ->
                        ps.executeQuery().use { rs ->
                            while (rs.next()) {
                                list.add(
                                    Project(
                                        id = rs.getLong(1),
                                        name = rs.getString(2) ?: "",
                                        createdAt = rs.getLong(3),
                                        description = rs.getString(4) ?: "",
                                        speakerId = rs.getLong(5),
                                        speakerGroupName = rs.getString(6) ?: "",
                                        enablePicturePrompts = rs.getInt(7) != 0
                                    )
                                )
                            }
                        }
                    }
                    list
                }
            }
        }

        override suspend fun getProjectById(id: Long): Project? = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("SELECT id, name, createdAt, description, speakerId, speakerGroupName, enablePicturePrompts FROM projects WHERE id = ?").use { ps ->
                    ps.setLong(1, id)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            Project(
                                id = rs.getLong(1),
                                name = rs.getString(2) ?: "",
                                createdAt = rs.getLong(3),
                                description = rs.getString(4) ?: "",
                                speakerId = rs.getLong(5),
                                speakerGroupName = rs.getString(6) ?: "",
                                enablePicturePrompts = rs.getInt(7) != 0
                            )
                        } else null
                    }
                }
            }
        }

        override suspend fun insertProject(project: Project): Long = withContext(Dispatchers.IO) {
            val id = synchronized(dbLock) {
                if (project.id > 0L) {
                    connection.prepareStatement(
                        "INSERT OR REPLACE INTO projects (id, name, createdAt, description, speakerId, speakerGroupName, enablePicturePrompts) VALUES (?, ?, ?, ?, ?, ?, ?)"
                    ).use { ps ->
                        ps.setLong(1, project.id)
                        ps.setString(2, project.name)
                        ps.setLong(3, project.createdAt)
                        ps.setString(4, project.description)
                        ps.setLong(5, project.speakerId)
                        ps.setString(6, project.speakerGroupName)
                        ps.setInt(7, if (project.enablePicturePrompts) 1 else 0)
                        ps.executeUpdate()
                    }
                    project.id
                } else {
                    connection.prepareStatement(
                        "INSERT INTO projects (name, createdAt, description, speakerId, speakerGroupName, enablePicturePrompts) VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS
                    ).use { ps ->
                        ps.setString(1, project.name)
                        ps.setLong(2, project.createdAt)
                        ps.setString(3, project.description)
                        ps.setLong(4, project.speakerId)
                        ps.setString(5, project.speakerGroupName)
                        ps.setInt(6, if (project.enablePicturePrompts) 1 else 0)
                        ps.executeUpdate()
                        ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 1L }
                    }
                }
            }
            notifyChanged()
            id
        }

        override suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "UPDATE projects SET name = ?, createdAt = ?, description = ?, speakerId = ?, speakerGroupName = ?, enablePicturePrompts = ? WHERE id = ?"
                ).use { ps ->
                    ps.setString(1, project.name)
                    ps.setLong(2, project.createdAt)
                    ps.setString(3, project.description)
                    ps.setLong(4, project.speakerId)
                    ps.setString(5, project.speakerGroupName)
                    ps.setInt(6, if (project.enablePicturePrompts) 1 else 0)
                    ps.setLong(7, project.id)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun deleteProject(project: Project) {
            deleteProjectById(project.id)
        }

        override suspend fun deleteProjectById(id: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("DELETE FROM projects WHERE id = ?").use { ps ->
                    ps.setLong(1, id)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    private val wordDaoImpl = object : WordDao {
        private fun readWordRow(rs: java.sql.ResultSet): Word {
            val recAtVal = rs.getLong(7)
            val recAt = if (rs.wasNull()) null else recAtVal
            val colCount = rs.metaData.columnCount
            val spkNote = if (colCount >= 14) (rs.getString(14) ?: "") else ""
            val imgPath = if (colCount >= 15) (rs.getString(15) ?: "") else ""
            return Word(
                id = rs.getLong(1),
                projectId = rs.getLong(2),
                text = rs.getString(3) ?: "",
                originalIndex = rs.getInt(4),
                isRecorded = rs.getInt(5) != 0,
                recordingFile = rs.getString(6),
                recordedAt = recAt,
                durationMs = rs.getLong(8),
                qualityStatus = rs.getString(9),
                avgRms = rs.getFloat(10),
                peakAmplitude = rs.getInt(11),
                remark = rs.getString(12) ?: "",
                groupName = rs.getString(13) ?: "",
                speakerNote = spkNote,
                imagePath = imgPath
            )
        }

        override fun getWordsForProject(projectId: Long): Flow<List<Word>> = changeVersion.map {
            getWordsForProjectSync(projectId)
        }

        override fun getWordsForProjectWithSpeaker(projectId: Long, speakerId: Long): Flow<List<Word>> = changeVersion.map {
            getWordsForProjectWithSpeakerSync(projectId, speakerId)
        }

        override suspend fun getWordsForProjectWithSpeakerSync(projectId: Long, speakerId: Long): List<Word> = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                val list = ArrayList<Word>()
                connection.prepareStatement(
                    """
                    SELECT w.id, w.projectId, w.text, w.originalIndex,
                           (CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END) as isRecorded,
                           r.recordingFile as recordingFile,
                           r.recordedAt as recordedAt,
                           COALESCE(r.durationMs, 0) as durationMs,
                           r.qualityStatus as qualityStatus,
                           COALESCE(r.avgRms, 0) as avgRms,
                           COALESCE(r.peakAmplitude, 0) as peakAmplitude,
                           w.remark as remark,
                           w.groupName as groupName,
                           COALESCE(n.note, '') as speakerNote,
                           w.imagePath as imagePath
                    FROM words w
                    LEFT JOIN speaker_recordings r ON w.id = r.wordId AND r.speakerId = ?
                    LEFT JOIN speaker_word_notes n ON w.id = n.wordId AND n.speakerId = ?
                    WHERE w.projectId = ?
                    ORDER BY w.originalIndex ASC
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, speakerId)
                    ps.setLong(2, speakerId)
                    ps.setLong(3, projectId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(readWordRow(rs))
                        }
                    }
                }
                list
            }
        }

        override suspend fun getWordsForProjectSync(projectId: Long): List<Word> = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                val list = ArrayList<Word>()
                connection.prepareStatement(
                    """
                    SELECT id, projectId, text, originalIndex, isRecorded, recordingFile,
                           recordedAt, durationMs, qualityStatus, avgRms, peakAmplitude, remark, groupName, speakerNote, imagePath
                    FROM words WHERE projectId = ? ORDER BY originalIndex ASC
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, projectId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(readWordRow(rs))
                        }
                    }
                }
                list
            }
        }

        override suspend fun getWordById(id: Long): Word? = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    SELECT id, projectId, text, originalIndex, isRecorded, recordingFile,
                           recordedAt, durationMs, qualityStatus, avgRms, peakAmplitude, remark, groupName, speakerNote, imagePath
                    FROM words WHERE id = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, id)
                    ps.executeQuery().use { rs -> if (rs.next()) readWordRow(rs) else null }
                }
            }
        }

        override suspend fun getWordByIdWithSpeaker(id: Long, speakerId: Long): Word? = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    SELECT w.id, w.projectId, w.text, w.originalIndex,
                           (CASE WHEN r.id IS NOT NULL THEN 1 ELSE 0 END) as isRecorded,
                           r.recordingFile as recordingFile,
                           r.recordedAt as recordedAt,
                           COALESCE(r.durationMs, 0) as durationMs,
                           r.qualityStatus as qualityStatus,
                           COALESCE(r.avgRms, 0) as avgRms,
                           COALESCE(r.peakAmplitude, 0) as peakAmplitude,
                           w.remark as remark,
                           w.groupName as groupName,
                           COALESCE(n.note, '') as speakerNote,
                           w.imagePath as imagePath
                    FROM words w
                    LEFT JOIN speaker_recordings r ON w.id = r.wordId AND r.speakerId = ?
                    LEFT JOIN speaker_word_notes n ON w.id = n.wordId AND n.speakerId = ?
                    WHERE w.id = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, speakerId)
                    ps.setLong(2, speakerId)
                    ps.setLong(3, id)
                    ps.executeQuery().use { rs -> if (rs.next()) readWordRow(rs) else null }
                }
            }
        }

        override suspend fun insertWords(words: List<Word>) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    INSERT INTO words (projectId, text, originalIndex, isRecorded, recordingFile, recordedAt, durationMs, qualityStatus, avgRms, peakAmplitude, remark, groupName, imagePath)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { ps ->
                    for (w in words) {
                        ps.setLong(1, w.projectId)
                        ps.setString(2, w.text)
                        ps.setInt(3, w.originalIndex)
                        ps.setInt(4, if (w.isRecorded) 1 else 0)
                        ps.setString(5, w.recordingFile)
                        if (w.recordedAt != null) ps.setLong(6, w.recordedAt) else ps.setNull(6, java.sql.Types.INTEGER)
                        ps.setLong(7, w.durationMs)
                        ps.setString(8, w.qualityStatus)
                        ps.setFloat(9, w.avgRms)
                        ps.setInt(10, w.peakAmplitude)
                        ps.setString(11, w.remark)
                        ps.setString(12, w.groupName)
                        ps.setString(13, w.imagePath)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            notifyChanged()
        }

        override suspend fun insertWord(word: Word): Long = withContext(Dispatchers.IO) {
            val id = synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    INSERT INTO words (projectId, text, originalIndex, isRecorded, recordingFile, recordedAt, durationMs, qualityStatus, avgRms, peakAmplitude, remark, groupName, imagePath)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setLong(1, word.projectId)
                    ps.setString(2, word.text)
                    ps.setInt(3, word.originalIndex)
                    ps.setInt(4, if (word.isRecorded) 1 else 0)
                    ps.setString(5, word.recordingFile)
                    if (word.recordedAt != null) ps.setLong(6, word.recordedAt) else ps.setNull(6, java.sql.Types.INTEGER)
                    ps.setLong(7, word.durationMs)
                    ps.setString(8, word.qualityStatus)
                    ps.setFloat(9, word.avgRms)
                    ps.setInt(10, word.peakAmplitude)
                    ps.setString(11, word.remark)
                    ps.setString(12, word.groupName)
                    ps.setString(13, word.imagePath)
                    ps.executeUpdate()
                    ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 1L }
                }
            }
            notifyChanged()
            id
        }

        override suspend fun updateWord(word: Word) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    UPDATE words SET projectId = ?, text = ?, originalIndex = ?, isRecorded = ?, recordingFile = ?,
                    recordedAt = ?, durationMs = ?, qualityStatus = ?, avgRms = ?, peakAmplitude = ?, remark = ?, groupName = ?, imagePath = ?
                    WHERE id = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setLong(1, word.projectId)
                    ps.setString(2, word.text)
                    ps.setInt(3, word.originalIndex)
                    ps.setInt(4, if (word.isRecorded) 1 else 0)
                    ps.setString(5, word.recordingFile)
                    if (word.recordedAt != null) ps.setLong(6, word.recordedAt) else ps.setNull(6, java.sql.Types.INTEGER)
                    ps.setLong(7, word.durationMs)
                    ps.setString(8, word.qualityStatus)
                    ps.setFloat(9, word.avgRms)
                    ps.setInt(10, word.peakAmplitude)
                    ps.setString(11, word.remark)
                    ps.setString(12, word.groupName)
                    ps.setString(13, word.imagePath)
                    ps.setLong(14, word.id)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun updateWords(words: List<Word>) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    UPDATE words SET projectId = ?, text = ?, originalIndex = ?, isRecorded = ?, recordingFile = ?,
                    recordedAt = ?, durationMs = ?, qualityStatus = ?, avgRms = ?, peakAmplitude = ?, remark = ?, groupName = ?, imagePath = ?
                    WHERE id = ?
                    """.trimIndent()
                ).use { ps ->
                    for (word in words) {
                        ps.setLong(1, word.projectId)
                        ps.setString(2, word.text)
                        ps.setInt(3, word.originalIndex)
                        ps.setInt(4, if (word.isRecorded) 1 else 0)
                        ps.setString(5, word.recordingFile)
                        if (word.recordedAt != null) ps.setLong(6, word.recordedAt) else ps.setNull(6, java.sql.Types.INTEGER)
                        ps.setLong(7, word.durationMs)
                        ps.setString(8, word.qualityStatus)
                        ps.setFloat(9, word.avgRms)
                        ps.setInt(10, word.peakAmplitude)
                        ps.setString(11, word.remark)
                        ps.setString(12, word.groupName)
                        ps.setString(13, word.imagePath)
                        ps.setLong(14, word.id)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            notifyChanged()
        }

        override suspend fun deleteWordById(wordId: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("DELETE FROM words WHERE id = ?").use { ps ->
                    ps.setLong(1, wordId)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun deleteWordsByIds(wordIds: List<Long>) = withContext(Dispatchers.IO) {
            if (wordIds.isEmpty()) return@withContext
            synchronized(dbLock) {
                connection.prepareStatement("DELETE FROM words WHERE id = ?").use { ps ->
                    for (id in wordIds) {
                        ps.setLong(1, id)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            notifyChanged()
        }

        override suspend fun getMaxOriginalIndex(projectId: Long): Int = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("SELECT COALESCE(MAX(originalIndex), -1) FROM words WHERE projectId = ?").use { ps ->
                    ps.setLong(1, projectId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else -1 }
                }
            }
        }

        override suspend fun shiftOriginalIndicesAfter(projectId: Long, afterIndex: Int) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "UPDATE words SET originalIndex = originalIndex + 1 WHERE projectId = ? AND originalIndex > ?"
                ).use { ps ->
                    ps.setLong(1, projectId)
                    ps.setInt(2, afterIndex)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun deleteWordsForProject(projectId: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("DELETE FROM words WHERE projectId = ?").use { ps ->
                    ps.setLong(1, projectId)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun resetProjectRecordings(projectId: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "DELETE FROM speaker_recordings WHERE wordId IN (SELECT id FROM words WHERE projectId = ?)"
                ).use { ps ->
                    ps.setLong(1, projectId)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override fun getWordCountFlow(projectId: Long): Flow<Int> = changeVersion.map {
            getWordCount(projectId)
        }

        override fun getRecordedCountFlow(projectId: Long, speakerId: Long): Flow<Int> = changeVersion.map {
            getRecordedCount(projectId, speakerId)
        }

        override suspend fun getWordCount(projectId: Long): Int = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("SELECT COUNT(*) FROM words WHERE projectId = ?").use { ps ->
                    ps.setLong(1, projectId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            }
        }

        override suspend fun getRecordedCount(projectId: Long, speakerId: Long): Int = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "SELECT COUNT(*) FROM speaker_recordings r JOIN words w ON r.wordId = w.id WHERE w.projectId = ? AND r.speakerId = ?"
                ).use { ps ->
                    ps.setLong(1, projectId)
                    ps.setLong(2, speakerId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            }
        }

        override suspend fun getTotalRecordingsInProject(projectId: Long): Int = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "SELECT COUNT(*) FROM speaker_recordings r JOIN words w ON r.wordId = w.id WHERE w.projectId = ?"
                ).use { ps ->
                    ps.setLong(1, projectId)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            }
        }

        override suspend fun getAllProjectWordCounts(): List<ProjectWordCountTuple> = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                val list = ArrayList<ProjectWordCountTuple>()
                connection.prepareStatement("SELECT projectId, COUNT(*) as wordCount FROM words GROUP BY projectId").use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(ProjectWordCountTuple(projectId = rs.getLong(1), wordCount = rs.getInt(2)))
                        }
                    }
                }
                list
            }
        }

        override suspend fun getAllProjectSpeakerRecordingCounts(): List<ProjectSpeakerRecordingCountTuple> = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                val list = ArrayList<ProjectSpeakerRecordingCountTuple>()
                connection.prepareStatement(
                    "SELECT w.projectId as projectId, r.speakerId as speakerId, COUNT(*) as recordedCount FROM speaker_recordings r JOIN words w ON r.wordId = w.id GROUP BY w.projectId, r.speakerId"
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(
                                ProjectSpeakerRecordingCountTuple(
                                    projectId = rs.getLong(1),
                                    speakerId = rs.getLong(2),
                                    recordedCount = rs.getInt(3)
                                )
                            )
                        }
                    }
                }
                list
            }
        }
    }

    private val speakerDaoImpl = object : SpeakerDao {
        private fun readSpeaker(rs: java.sql.ResultSet): Speaker = Speaker(
            id = rs.getLong(1),
            name = rs.getString(2) ?: "",
            gender = rs.getString(3) ?: "",
            age = rs.getInt(4),
            dialect = rs.getString(5) ?: "",
            notes = rs.getString(6) ?: "",
            groupName = rs.getString(7) ?: "",
            createdAt = rs.getLong(8)
        )

        override fun getAllSpeakers(): Flow<List<Speaker>> = changeVersion.map {
            withContext(Dispatchers.IO) {
                synchronized(dbLock) {
                    val list = ArrayList<Speaker>()
                    connection.prepareStatement(
                        "SELECT id, name, gender, age, dialect, notes, groupName, createdAt FROM speakers ORDER BY groupName ASC, name ASC"
                    ).use { ps ->
                        ps.executeQuery().use { rs ->
                            while (rs.next()) list.add(readSpeaker(rs))
                        }
                    }
                    list
                }
            }
        }

        override suspend fun getSpeakerById(id: Long): Speaker? = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "SELECT id, name, gender, age, dialect, notes, groupName, createdAt FROM speakers WHERE id = ?"
                ).use { ps ->
                    ps.setLong(1, id)
                    ps.executeQuery().use { rs -> if (rs.next()) readSpeaker(rs) else null }
                }
            }
        }

        override suspend fun insertSpeaker(speaker: Speaker): Long = withContext(Dispatchers.IO) {
            val id = synchronized(dbLock) {
                connection.prepareStatement(
                    "INSERT INTO speakers (name, gender, age, dialect, notes, groupName, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setString(1, speaker.name)
                    ps.setString(2, speaker.gender)
                    ps.setInt(3, speaker.age)
                    ps.setString(4, speaker.dialect)
                    ps.setString(5, speaker.notes)
                    ps.setString(6, speaker.groupName)
                    ps.setLong(7, speaker.createdAt)
                    ps.executeUpdate()
                    ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 1L }
                }
            }
            notifyChanged()
            id
        }

        override suspend fun updateSpeaker(speaker: Speaker) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "UPDATE speakers SET name = ?, gender = ?, age = ?, dialect = ?, notes = ?, groupName = ?, createdAt = ? WHERE id = ?"
                ).use { ps ->
                    ps.setString(1, speaker.name)
                    ps.setString(2, speaker.gender)
                    ps.setInt(3, speaker.age)
                    ps.setString(4, speaker.dialect)
                    ps.setString(5, speaker.notes)
                    ps.setString(6, speaker.groupName)
                    ps.setLong(7, speaker.createdAt)
                    ps.setLong(8, speaker.id)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun deleteSpeaker(speaker: Speaker) {
            deleteSpeakerById(speaker.id)
        }

        override suspend fun deleteSpeakerById(id: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("DELETE FROM speakers WHERE id = ?").use { ps ->
                    ps.setLong(1, id)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    private val speakerRecordingDaoImpl = object : SpeakerRecordingDao {
        private fun readRec(rs: java.sql.ResultSet): SpeakerRecording = SpeakerRecording(
            id = rs.getLong(1),
            wordId = rs.getLong(2),
            speakerId = rs.getLong(3),
            recordingFile = rs.getString(4) ?: "",
            recordedAt = rs.getLong(5),
            durationMs = rs.getLong(6),
            qualityStatus = rs.getString(7),
            avgRms = rs.getFloat(8),
            peakAmplitude = rs.getInt(9)
        )

        override suspend fun getRecording(wordId: Long, speakerId: Long): SpeakerRecording? = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "SELECT id, wordId, speakerId, recordingFile, recordedAt, durationMs, qualityStatus, avgRms, peakAmplitude FROM speaker_recordings WHERE wordId = ? AND speakerId = ?"
                ).use { ps ->
                    ps.setLong(1, wordId)
                    ps.setLong(2, speakerId)
                    ps.executeQuery().use { rs -> if (rs.next()) readRec(rs) else null }
                }
            }
        }

        override suspend fun getRecordingsForWord(wordId: Long): List<SpeakerRecording> = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                val list = ArrayList<SpeakerRecording>()
                connection.prepareStatement(
                    "SELECT id, wordId, speakerId, recordingFile, recordedAt, durationMs, qualityStatus, avgRms, peakAmplitude FROM speaker_recordings WHERE wordId = ?"
                ).use { ps ->
                    ps.setLong(1, wordId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) list.add(readRec(rs))
                    }
                }
                list
            }
        }

        override suspend fun insertRecording(recording: SpeakerRecording): Long = withContext(Dispatchers.IO) {
            val id = synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    INSERT OR REPLACE INTO speaker_recordings (wordId, speakerId, recordingFile, recordedAt, durationMs, qualityStatus, avgRms, peakAmplitude)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setLong(1, recording.wordId)
                    ps.setLong(2, recording.speakerId)
                    ps.setString(3, recording.recordingFile)
                    ps.setLong(4, recording.recordedAt)
                    ps.setLong(5, recording.durationMs)
                    ps.setString(6, recording.qualityStatus)
                    ps.setFloat(7, recording.avgRms)
                    ps.setInt(8, recording.peakAmplitude)
                    ps.executeUpdate()
                    ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 1L }
                }
            }
            notifyChanged()
            id
        }

        override suspend fun deleteRecording(wordId: Long, speakerId: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("DELETE FROM speaker_recordings WHERE wordId = ? AND speakerId = ?").use { ps ->
                    ps.setLong(1, wordId)
                    ps.setLong(2, speakerId)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun deleteRecordingsForSpeaker(speakerId: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement("DELETE FROM speaker_recordings WHERE speakerId = ?").use { ps ->
                    ps.setLong(1, speakerId)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }

        override suspend fun getAllProjectRecordingEntries(): List<ProjectRecordingEntryTuple> = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                val list = ArrayList<ProjectRecordingEntryTuple>()
                connection.prepareStatement(
                    """
                    SELECT w.projectId as projectId,
                           r.wordId as wordId,
                           r.speakerId as speakerId,
                           r.recordingFile as recordingFile,
                           r.durationMs as durationMs,
                           r.qualityStatus as qualityStatus
                    FROM speaker_recordings r
                    JOIN words w ON r.wordId = w.id
                    """.trimIndent()
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(
                                ProjectRecordingEntryTuple(
                                    projectId = rs.getLong(1),
                                    wordId = rs.getLong(2),
                                    speakerId = rs.getLong(3),
                                    recordingFile = rs.getString(4) ?: "",
                                    durationMs = rs.getLong(5),
                                    qualityStatus = rs.getString(6)
                                )
                            )
                        }
                    }
                }
                list
            }
        }
    }

    private val speakerWordNoteDaoImpl = object : SpeakerWordNoteDao {
        private fun readNote(rs: java.sql.ResultSet): SpeakerWordNote = SpeakerWordNote(
            id = rs.getLong(1),
            wordId = rs.getLong(2),
            speakerId = rs.getLong(3),
            note = rs.getString(4) ?: "",
            updatedAt = rs.getLong(5)
        )

        override suspend fun getNote(wordId: Long, speakerId: Long): SpeakerWordNote? = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "SELECT id, wordId, speakerId, note, updatedAt FROM speaker_word_notes WHERE wordId = ? AND speakerId = ?"
                ).use { ps ->
                    ps.setLong(1, wordId)
                    ps.setLong(2, speakerId)
                    ps.executeQuery().use { rs -> if (rs.next()) readNote(rs) else null }
                }
            }
        }

        override suspend fun getNotesForWord(wordId: Long): List<SpeakerWordNote> = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                val list = ArrayList<SpeakerWordNote>()
                connection.prepareStatement(
                    "SELECT id, wordId, speakerId, note, updatedAt FROM speaker_word_notes WHERE wordId = ?"
                ).use { ps ->
                    ps.setLong(1, wordId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) list.add(readNote(rs))
                    }
                }
                list
            }
        }

        override suspend fun upsertNote(note: SpeakerWordNote): Long = withContext(Dispatchers.IO) {
            val id = synchronized(dbLock) {
                connection.prepareStatement(
                    """
                    INSERT OR REPLACE INTO speaker_word_notes (wordId, speakerId, note, updatedAt)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setLong(1, note.wordId)
                    ps.setLong(2, note.speakerId)
                    ps.setString(3, note.note)
                    ps.setLong(4, note.updatedAt)
                    ps.executeUpdate()
                    ps.generatedKeys.use { keys -> if (keys.next()) keys.getLong(1) else 1L }
                }
            }
            notifyChanged()
            id
        }

        override suspend fun deleteNote(wordId: Long, speakerId: Long) = withContext(Dispatchers.IO) {
            synchronized(dbLock) {
                connection.prepareStatement(
                    "DELETE FROM speaker_word_notes WHERE wordId = ? AND speakerId = ?"
                ).use { ps ->
                    ps.setLong(1, wordId)
                    ps.setLong(2, speakerId)
                    ps.executeUpdate()
                }
            }
            notifyChanged()
        }
    }

    override fun projectDao(): ProjectDao = projectDaoImpl
    override fun wordDao(): WordDao = wordDaoImpl
    override fun speakerDao(): SpeakerDao = speakerDaoImpl
    override fun speakerRecordingDao(): SpeakerRecordingDao = speakerRecordingDaoImpl
    override fun speakerWordNoteDao(): SpeakerWordNoteDao = speakerWordNoteDaoImpl
}
