package com.example.meritrankerstudent.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class AppDatabase_Impl(context: Context) : AppDatabase() {

    private val dbHelper = DatabaseOpenHelper(context.applicationContext)
    private val db get() = dbHelper.writableDatabase

    private val tableChangeNotifier = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private class DatabaseOpenHelper(context: Context) : SQLiteOpenHelper(context, "meritranker_cache.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_profiles (
                    userId TEXT PRIMARY KEY NOT NULL,
                    email TEXT,
                    name TEXT,
                    preparing TEXT,
                    examProfileId TEXT,
                    examStage TEXT,
                    additionalExamsJson TEXT NOT NULL,
                    examTagsJson TEXT,
                    dateOfBirth TEXT,
                    language TEXT,
                    profileCompleted INTEGER,
                    onboardingStep TEXT,
                    role TEXT,
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_sessions (
                    id TEXT PRIMARY KEY NOT NULL,
                    userId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    lastActivityAt INTEGER NOT NULL,
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_sessions_userId_lastActivityAt ON conversation_sessions (userId, lastActivityAt)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_turns (
                    id TEXT PRIMARY KEY NOT NULL,
                    userId TEXT NOT NULL,
                    conversationId TEXT NOT NULL,
                    originalQuery TEXT NOT NULL,
                    finalAnswer TEXT NOT NULL,
                    examId TEXT,
                    language TEXT NOT NULL DEFAULT 'english',
                    createdAt INTEGER NOT NULL,
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_turns_conversationId_createdAt ON conversation_turns (conversationId, createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_turns_userId ON conversation_turns (userId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_drafts (
                    draftKey TEXT PRIMARY KEY NOT NULL,
                    userId TEXT NOT NULL,
                    text TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_drafts_userId ON chat_drafts (userId)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_exam_profiles (
                    examProfileId TEXT PRIMARY KEY NOT NULL,
                    examId TEXT NOT NULL,
                    examName TEXT NOT NULL,
                    stage TEXT NOT NULL,
                    description TEXT,
                    sectionsJson TEXT,
                    previousCutoffJson TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    totalQuestions INTEGER NOT NULL DEFAULT 0,
                    totalMarks REAL NOT NULL DEFAULT 0.0,
                    totalTimeMinutes INTEGER NOT NULL DEFAULT 0,
                    effectiveYear INTEGER,
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_exam_profiles_examId ON cached_exam_profiles (examId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_exam_profiles_active ON cached_exam_profiles (active)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_practice_activities (
                    activityId TEXT PRIMARY KEY NOT NULL,
                    ownerUserId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    activityType TEXT NOT NULL,
                    language TEXT NOT NULL DEFAULT 'en',
                    subject TEXT,
                    topic TEXT,
                    examsJson TEXT,
                    questionCount INTEGER NOT NULL DEFAULT 0,
                    durationMinutes INTEGER,
                    hasResumableAttempt INTEGER NOT NULL DEFAULT 0,
                    resumableAttemptId TEXT,
                    playable INTEGER NOT NULL DEFAULT 1,
                    generationStatus TEXT,
                    readyCount INTEGER,
                    progressPercent INTEGER,
                    errorCode TEXT,
                    latestAttemptId TEXT,
                    latestAttemptStatus TEXT,
                    latestAttemptScore REAL,
                    latestAttemptMaximumScore REAL,
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_activities_ownerUserId ON cached_practice_activities (ownerUserId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_activities_ownerUserId_activityType ON cached_practice_activities (ownerUserId, activityType)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_activities_lastSyncedAt ON cached_practice_activities (lastSyncedAt)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_practice_questions (
                    ownerUserId TEXT NOT NULL,
                    activityId TEXT NOT NULL,
                    questionId TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    question TEXT NOT NULL,
                    optionsJson TEXT NOT NULL,
                    section TEXT,
                    subject TEXT,
                    topic TEXT,
                    marks REAL NOT NULL DEFAULT 1.0,
                    negativeMarks REAL NOT NULL DEFAULT 0.0,
                    format TEXT,
                    language TEXT NOT NULL DEFAULT 'en',
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (ownerUserId, activityId, questionId)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_questions_ownerUserId_activityId_position ON cached_practice_questions (ownerUserId, activityId, position)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_student_performance (
                    ownerUserId TEXT NOT NULL,
                    examProfileId TEXT NOT NULL,
                    view TEXT NOT NULL,
                    subjectId TEXT NOT NULL DEFAULT 'ALL',
                    overallJson TEXT NOT NULL,
                    itemsJson TEXT NOT NULL,
                    insightsJson TEXT NOT NULL,
                    isUpdatingLatestPerformance INTEGER NOT NULL DEFAULT 0,
                    isDirty INTEGER NOT NULL DEFAULT 0,
                    lastProcessedAt TEXT,
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (ownerUserId, examProfileId, view, subjectId)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_student_performance_ownerUserId_examProfileId ON cached_student_performance (ownerUserId, examProfileId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_student_performance_ownerUserId_view ON cached_student_performance (ownerUserId, view)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_metadata (
                    ownerUserId TEXT NOT NULL,
                    resourceType TEXT NOT NULL,
                    resourceScope TEXT NOT NULL DEFAULT 'GLOBAL',
                    lastSuccessfulFetchAt INTEGER NOT NULL,
                    serverUpdatedAt TEXT,
                    syncState TEXT NOT NULL DEFAULT 'IDLE',
                    PRIMARY KEY (ownerUserId, resourceType, resourceScope)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_metadata_ownerUserId_resourceType ON sync_metadata (ownerUserId, resourceType)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE cached_profiles ADD COLUMN examProfileId TEXT")
                    db.execSQL("ALTER TABLE cached_profiles ADD COLUMN examStage TEXT")
                    db.execSQL("ALTER TABLE cached_profiles ADD COLUMN examTagsJson TEXT")
                    db.execSQL("ALTER TABLE cached_profiles ADD COLUMN profileCompleted INTEGER")
                    db.execSQL("ALTER TABLE cached_profiles ADD COLUMN onboardingStep TEXT")
                    db.execSQL("ALTER TABLE cached_profiles ADD COLUMN role TEXT")
                } catch (_: Exception) {}

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_exam_profiles (
                        examProfileId TEXT PRIMARY KEY NOT NULL,
                        examId TEXT NOT NULL,
                        examName TEXT NOT NULL,
                        stage TEXT NOT NULL,
                        description TEXT,
                        sectionsJson TEXT,
                        previousCutoffJson TEXT,
                        active INTEGER NOT NULL DEFAULT 1,
                        totalQuestions INTEGER NOT NULL DEFAULT 0,
                        totalMarks REAL NOT NULL DEFAULT 0.0,
                        totalTimeMinutes INTEGER NOT NULL DEFAULT 0,
                        effectiveYear INTEGER,
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_exam_profiles_examId ON cached_exam_profiles (examId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_exam_profiles_active ON cached_exam_profiles (active)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_practice_activities (
                        activityId TEXT PRIMARY KEY NOT NULL,
                        ownerUserId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        activityType TEXT NOT NULL,
                        language TEXT NOT NULL DEFAULT 'en',
                        subject TEXT,
                        topic TEXT,
                        examsJson TEXT,
                        questionCount INTEGER NOT NULL DEFAULT 0,
                        durationMinutes INTEGER,
                        hasResumableAttempt INTEGER NOT NULL DEFAULT 0,
                        resumableAttemptId TEXT,
                        playable INTEGER NOT NULL DEFAULT 1,
                        generationStatus TEXT,
                        readyCount INTEGER,
                        progressPercent INTEGER,
                        errorCode TEXT,
                        latestAttemptId TEXT,
                        latestAttemptStatus TEXT,
                        latestAttemptScore REAL,
                        latestAttemptMaximumScore REAL,
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_activities_ownerUserId ON cached_practice_activities (ownerUserId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_activities_ownerUserId_activityType ON cached_practice_activities (ownerUserId, activityType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_activities_lastSyncedAt ON cached_practice_activities (lastSyncedAt)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_practice_questions (
                        ownerUserId TEXT NOT NULL,
                        activityId TEXT NOT NULL,
                        questionId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        question TEXT NOT NULL,
                        optionsJson TEXT NOT NULL,
                        section TEXT,
                        subject TEXT,
                        topic TEXT,
                        marks REAL NOT NULL DEFAULT 1.0,
                        negativeMarks REAL NOT NULL DEFAULT 0.0,
                        format TEXT,
                        language TEXT NOT NULL DEFAULT 'en',
                        lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (ownerUserId, activityId, questionId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_practice_questions_ownerUserId_activityId_position ON cached_practice_questions (ownerUserId, activityId, position)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cached_student_performance (
                    ownerUserId TEXT NOT NULL,
                    examProfileId TEXT NOT NULL,
                    view TEXT NOT NULL,
                    subjectId TEXT NOT NULL DEFAULT 'ALL',
                    overallJson TEXT NOT NULL,
                    itemsJson TEXT NOT NULL,
                    insightsJson TEXT NOT NULL,
                    isUpdatingLatestPerformance INTEGER NOT NULL DEFAULT 0,
                    isDirty INTEGER NOT NULL DEFAULT 0,
                    lastProcessedAt TEXT,
                    lastSyncedAt INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (ownerUserId, examProfileId, view, subjectId)
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_metadata (
                    ownerUserId TEXT NOT NULL,
                    resourceType TEXT NOT NULL,
                    resourceScope TEXT NOT NULL DEFAULT 'GLOBAL',
                    lastSuccessfulFetchAt INTEGER NOT NULL,
                    serverUpdatedAt TEXT,
                    syncState TEXT NOT NULL DEFAULT 'IDLE',
                    PRIMARY KEY (ownerUserId, resourceType, resourceScope)
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_metadata_ownerUserId_resourceType_resourceScope ON sync_metadata (ownerUserId, resourceType, resourceScope)")
            }
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS purchase_transactions")
        }
    }

    private fun notifyTableChanged(table: String) {
        tableChangeNotifier.tryEmit(table)
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(this, "cached_profiles", "conversation_sessions", "conversation_turns", "chat_drafts", "cached_exam_profiles", "cached_practice_activities", "cached_practice_questions", "cached_student_performance", "sync_metadata")
    }

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        error("Using direct SQLiteOpenHelper implementation")
    }

    override fun clearAllTables() {
        val tables = listOf("cached_profiles", "conversation_sessions", "conversation_turns", "chat_drafts", "cached_exam_profiles", "cached_practice_activities", "cached_practice_questions", "cached_student_performance", "sync_metadata", "purchase_transactions")
        db.beginTransaction()
        try {
            for (t in tables) {
                db.execSQL("DELETE FROM $t")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private val profileDaoImpl = object : ProfileDao {
        override fun getProfile(userId: String): Flow<CachedProfileEntity?> = flow {
            emit(getProfileSync(userId))
            tableChangeNotifier.collect { if (it == "cached_profiles") emit(getProfileSync(userId)) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getProfileSync(userId: String): CachedProfileEntity? = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM cached_profiles WHERE userId = ? LIMIT 1", arrayOf(userId))
            cursor.use {
                if (it.moveToFirst()) {
                    CachedProfileEntity(
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        email = it.getStringOrNull("email"),
                        name = it.getStringOrNull("name"),
                        preparing = it.getStringOrNull("preparing"),
                        examProfileId = it.getStringOrNull("examProfileId"),
                        examStage = it.getStringOrNull("examStage"),
                        additionalExamsJson = it.getStringOrNull("additionalExamsJson") ?: "[]",
                        examTagsJson = it.getStringOrNull("examTagsJson"),
                        dateOfBirth = it.getStringOrNull("dateOfBirth"),
                        language = it.getStringOrNull("language"),
                        profileCompleted = if (it.isNull(it.getColumnIndexOrThrow("profileCompleted"))) null else it.getInt(it.getColumnIndexOrThrow("profileCompleted")) == 1,
                        onboardingStep = it.getStringOrNull("onboardingStep"),
                        role = it.getStringOrNull("role"),
                        lastSyncedAt = it.getLong(it.getColumnIndexOrThrow("lastSyncedAt"))
                    )
                } else null
            }
        }

        override suspend fun upsertProfile(profile: CachedProfileEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("userId", profile.userId)
                put("email", profile.email)
                put("name", profile.name)
                put("preparing", profile.preparing)
                put("examProfileId", profile.examProfileId)
                put("examStage", profile.examStage)
                put("additionalExamsJson", profile.additionalExamsJson ?: "[]")
                put("examTagsJson", profile.examTagsJson)
                put("dateOfBirth", profile.dateOfBirth)
                put("language", profile.language)
                put("profileCompleted", profile.profileCompleted?.let { if (it) 1 else 0 })
                put("onboardingStep", profile.onboardingStep)
                put("role", profile.role)
                put("lastSyncedAt", profile.lastSyncedAt)
            }
            db.insertWithOnConflict("cached_profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("cached_profiles")
        }

        override suspend fun deleteProfile(userId: String) = withContext(Dispatchers.IO) {
            db.delete("cached_profiles", "userId = ?", arrayOf(userId))
            notifyTableChanged("cached_profiles")
            Unit
        }

        override suspend fun clearAllProfiles() = withContext(Dispatchers.IO) {
            db.delete("cached_profiles", null, null)
            notifyTableChanged("cached_profiles")
            Unit
        }
    }

    private val examProfileDaoImpl = object : ExamProfileDao {
        override fun getExamProfile(examProfileId: String): Flow<CachedExamProfileEntity?> = flow {
            emit(getExamProfileSync(examProfileId))
            tableChangeNotifier.collect { if (it == "cached_exam_profiles") emit(getExamProfileSync(examProfileId)) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getExamProfileSync(examProfileId: String): CachedExamProfileEntity? = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM cached_exam_profiles WHERE examProfileId = ? LIMIT 1", arrayOf(examProfileId))
            cursor.use {
                if (it.moveToFirst()) mapExamProfile(it) else null
            }
        }

        override fun getAllActiveExamProfiles(): Flow<List<CachedExamProfileEntity>> = flow {
            emit(getAllActiveExamProfilesSync())
            tableChangeNotifier.collect { if (it == "cached_exam_profiles") emit(getAllActiveExamProfilesSync()) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getAllActiveExamProfilesSync(): List<CachedExamProfileEntity> = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM cached_exam_profiles WHERE active = 1 ORDER BY examName ASC, stage ASC", null)
            cursor.use {
                val list = mutableListOf<CachedExamProfileEntity>()
                while (it.moveToNext()) {
                    list.add(mapExamProfile(it))
                }
                list
            }
        }

        override fun getProfilesForExam(examId: String): Flow<List<CachedExamProfileEntity>> = flow {
            val cursor = db.rawQuery("SELECT * FROM cached_exam_profiles WHERE examId = ? AND active = 1", arrayOf(examId))
            val list = cursor.use {
                val items = mutableListOf<CachedExamProfileEntity>()
                while (it.moveToNext()) items.add(mapExamProfile(it))
                items
            }
            emit(list)
            tableChangeNotifier.collect { if (it == "cached_exam_profiles") {
                val updatedCursor = db.rawQuery("SELECT * FROM cached_exam_profiles WHERE examId = ? AND active = 1", arrayOf(examId))
                emit(updatedCursor.use { c ->
                    val items = mutableListOf<CachedExamProfileEntity>()
                    while (c.moveToNext()) items.add(mapExamProfile(c))
                    items
                })
            }}
        }.flowOn(Dispatchers.IO)

        override suspend fun upsertExamProfile(profile: CachedExamProfileEntity) = withContext(Dispatchers.IO) {
            val values = examProfileToContentValues(profile)
            db.insertWithOnConflict("cached_exam_profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("cached_exam_profiles")
        }

        override suspend fun upsertExamProfiles(profiles: List<CachedExamProfileEntity>) = withContext(Dispatchers.IO) {
            db.beginTransaction()
            try {
                for (p in profiles) {
                    db.insertWithOnConflict("cached_exam_profiles", null, examProfileToContentValues(p), SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyTableChanged("cached_exam_profiles")
        }

        override suspend fun deleteExamProfile(examProfileId: String) = withContext(Dispatchers.IO) {
            db.delete("cached_exam_profiles", "examProfileId = ?", arrayOf(examProfileId))
            notifyTableChanged("cached_exam_profiles")
            Unit
        }

        override suspend fun clearAllExamProfiles() = withContext(Dispatchers.IO) {
            db.delete("cached_exam_profiles", null, null)
            notifyTableChanged("cached_exam_profiles")
            Unit
        }

        private fun mapExamProfile(c: Cursor) = CachedExamProfileEntity(
            examProfileId = c.getString(c.getColumnIndexOrThrow("examProfileId")),
            examId = c.getString(c.getColumnIndexOrThrow("examId")),
            examName = c.getString(c.getColumnIndexOrThrow("examName")),
            stage = c.getString(c.getColumnIndexOrThrow("stage")),
            description = c.getStringOrNull("description"),
            sectionsJson = c.getStringOrNull("sectionsJson"),
            previousCutoffJson = c.getStringOrNull("previousCutoffJson"),
            active = c.getInt(c.getColumnIndexOrThrow("active")) == 1,
            totalQuestions = c.getInt(c.getColumnIndexOrThrow("totalQuestions")),
            totalMarks = c.getDouble(c.getColumnIndexOrThrow("totalMarks")),
            totalTimeMinutes = c.getInt(c.getColumnIndexOrThrow("totalTimeMinutes")),
            effectiveYear = if (c.isNull(c.getColumnIndexOrThrow("effectiveYear"))) null else c.getInt(c.getColumnIndexOrThrow("effectiveYear")),
            lastSyncedAt = c.getLong(c.getColumnIndexOrThrow("lastSyncedAt"))
        )

        private fun examProfileToContentValues(p: CachedExamProfileEntity) = ContentValues().apply {
            put("examProfileId", p.examProfileId)
            put("examId", p.examId)
            put("examName", p.examName)
            put("stage", p.stage)
            put("description", p.description)
            put("sectionsJson", p.sectionsJson)
            put("previousCutoffJson", p.previousCutoffJson)
            put("active", if (p.active) 1 else 0)
            put("totalQuestions", p.totalQuestions)
            put("totalMarks", p.totalMarks)
            put("totalTimeMinutes", p.totalTimeMinutes)
            put("effectiveYear", p.effectiveYear)
            put("lastSyncedAt", p.lastSyncedAt)
        }
    }

    private val practiceDaoImpl = object : PracticeDao {
        override fun getActivities(ownerUserId: String): Flow<List<CachedPracticeActivityEntity>> = flow {
            emit(queryActivities("ownerUserId = ?", arrayOf(ownerUserId)))
            tableChangeNotifier.collect { if (it == "cached_practice_activities") emit(queryActivities("ownerUserId = ?", arrayOf(ownerUserId))) }
        }.flowOn(Dispatchers.IO)

        override fun getActivitiesByType(ownerUserId: String, activityType: String): Flow<List<CachedPracticeActivityEntity>> = flow {
            emit(queryActivities("ownerUserId = ? AND activityType = ?", arrayOf(ownerUserId, activityType)))
            tableChangeNotifier.collect { if (it == "cached_practice_activities") emit(queryActivities("ownerUserId = ? AND activityType = ?", arrayOf(ownerUserId, activityType))) }
        }.flowOn(Dispatchers.IO)

        override fun getActivity(activityId: String): Flow<CachedPracticeActivityEntity?> = flow {
            emit(getActivitySync(activityId))
            tableChangeNotifier.collect { if (it == "cached_practice_activities") emit(getActivitySync(activityId)) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getActivitySync(activityId: String): CachedPracticeActivityEntity? = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM cached_practice_activities WHERE activityId = ? LIMIT 1", arrayOf(activityId))
            cursor.use { if (it.moveToFirst()) mapActivity(it) else null }
        }

        override suspend fun getNonTerminalActivities(ownerUserId: String): List<CachedPracticeActivityEntity> = withContext(Dispatchers.IO) {
            queryActivities(
                "ownerUserId = ? AND (generationStatus IS NULL OR generationStatus NOT IN ('READY', 'FAILED', 'CANCELLED', 'EXPIRED', 'INTERRUPTED'))",
                arrayOf(ownerUserId)
            )
        }

        override fun observeNonTerminalActivities(ownerUserId: String): Flow<List<CachedPracticeActivityEntity>> = flow {
            emit(getNonTerminalActivities(ownerUserId))
            tableChangeNotifier.collect {
                if (it == "cached_practice_activities") emit(getNonTerminalActivities(ownerUserId))
            }
        }.flowOn(Dispatchers.IO)

        override suspend fun upsertActivity(activity: CachedPracticeActivityEntity) = withContext(Dispatchers.IO) {
            db.insertWithOnConflict("cached_practice_activities", null, activityToValues(activity), SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("cached_practice_activities")
        }

        override suspend fun upsertActivities(activities: List<CachedPracticeActivityEntity>) = withContext(Dispatchers.IO) {
            db.beginTransaction()
            try {
                for (a in activities) {
                    db.insertWithOnConflict("cached_practice_activities", null, activityToValues(a), SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyTableChanged("cached_practice_activities")
        }

        override fun getQuestions(ownerUserId: String, activityId: String): Flow<List<CachedPracticeQuestionEntity>> = flow {
            emit(getQuestionsSync(ownerUserId, activityId))
            tableChangeNotifier.collect { if (it == "cached_practice_questions") emit(getQuestionsSync(ownerUserId, activityId)) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getQuestionsSync(ownerUserId: String, activityId: String): List<CachedPracticeQuestionEntity> = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM cached_practice_questions WHERE ownerUserId = ? AND activityId = ? ORDER BY position ASC", arrayOf(ownerUserId, activityId))
            cursor.use {
                val list = mutableListOf<CachedPracticeQuestionEntity>()
                while (it.moveToNext()) list.add(mapQuestion(it))
                list
            }
        }

        override suspend fun upsertQuestions(questions: List<CachedPracticeQuestionEntity>) = withContext(Dispatchers.IO) {
            db.beginTransaction()
            try {
                for (q in questions) {
                    db.insertWithOnConflict("cached_practice_questions", null, questionToValues(q), SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyTableChanged("cached_practice_questions")
        }

        override suspend fun deleteActivitiesByUser(ownerUserId: String) = withContext(Dispatchers.IO) {
            db.delete("cached_practice_activities", "ownerUserId = ?", arrayOf(ownerUserId))
            notifyTableChanged("cached_practice_activities")
            Unit
        }

        override suspend fun deleteQuestionsByUser(ownerUserId: String) = withContext(Dispatchers.IO) {
            db.delete("cached_practice_questions", "ownerUserId = ?", arrayOf(ownerUserId))
            notifyTableChanged("cached_practice_questions")
            Unit
        }

        override suspend fun deleteQuestionsByActivity(ownerUserId: String, activityId: String) = withContext(Dispatchers.IO) {
            db.delete("cached_practice_questions", "ownerUserId = ? AND activityId = ?", arrayOf(ownerUserId, activityId))
            notifyTableChanged("cached_practice_questions")
            Unit
        }

        override suspend fun clearAllActivities() = withContext(Dispatchers.IO) {
            db.delete("cached_practice_activities", null, null)
            notifyTableChanged("cached_practice_activities")
            Unit
        }

        override suspend fun clearAllQuestions() = withContext(Dispatchers.IO) {
            db.delete("cached_practice_questions", null, null)
            notifyTableChanged("cached_practice_questions")
            Unit
        }

        private fun queryActivities(selection: String?, args: Array<String>?): List<CachedPracticeActivityEntity> {
            val cursor = db.query("cached_practice_activities", null, selection, args, null, null, "lastSyncedAt DESC")
            return cursor.use {
                val list = mutableListOf<CachedPracticeActivityEntity>()
                while (it.moveToNext()) list.add(mapActivity(it))
                list
            }
        }

        private fun mapActivity(c: Cursor) = CachedPracticeActivityEntity(
            activityId = c.getString(c.getColumnIndexOrThrow("activityId")),
            ownerUserId = c.getString(c.getColumnIndexOrThrow("ownerUserId")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            activityType = c.getString(c.getColumnIndexOrThrow("activityType")),
            language = c.getString(c.getColumnIndexOrThrow("language")),
            subject = c.getStringOrNull("subject"),
            topic = c.getStringOrNull("topic"),
            examsJson = c.getStringOrNull("examsJson"),
            questionCount = c.getInt(c.getColumnIndexOrThrow("questionCount")),
            durationMinutes = if (c.isNull(c.getColumnIndexOrThrow("durationMinutes"))) null else c.getInt(c.getColumnIndexOrThrow("durationMinutes")),
            hasResumableAttempt = c.getInt(c.getColumnIndexOrThrow("hasResumableAttempt")) == 1,
            resumableAttemptId = c.getStringOrNull("resumableAttemptId"),
            playable = c.getInt(c.getColumnIndexOrThrow("playable")) == 1,
            generationStatus = c.getStringOrNull("generationStatus"),
            readyCount = if (c.isNull(c.getColumnIndexOrThrow("readyCount"))) null else c.getInt(c.getColumnIndexOrThrow("readyCount")),
            progressPercent = if (c.isNull(c.getColumnIndexOrThrow("progressPercent"))) null else c.getInt(c.getColumnIndexOrThrow("progressPercent")),
            errorCode = c.getStringOrNull("errorCode"),
            latestAttemptId = c.getStringOrNull("latestAttemptId"),
            latestAttemptStatus = c.getStringOrNull("latestAttemptStatus"),
            latestAttemptScore = if (c.isNull(c.getColumnIndexOrThrow("latestAttemptScore"))) null else c.getDouble(c.getColumnIndexOrThrow("latestAttemptScore")),
            latestAttemptMaximumScore = if (c.isNull(c.getColumnIndexOrThrow("latestAttemptMaximumScore"))) null else c.getDouble(c.getColumnIndexOrThrow("latestAttemptMaximumScore")),
            lastSyncedAt = c.getLong(c.getColumnIndexOrThrow("lastSyncedAt"))
        )

        private fun activityToValues(a: CachedPracticeActivityEntity) = ContentValues().apply {
            put("activityId", a.activityId)
            put("ownerUserId", a.ownerUserId)
            put("title", a.title)
            put("activityType", a.activityType)
            put("language", a.language)
            put("subject", a.subject)
            put("topic", a.topic)
            put("examsJson", a.examsJson)
            put("questionCount", a.questionCount)
            put("durationMinutes", a.durationMinutes)
            put("hasResumableAttempt", if (a.hasResumableAttempt) 1 else 0)
            put("resumableAttemptId", a.resumableAttemptId)
            put("playable", if (a.playable) 1 else 0)
            put("generationStatus", a.generationStatus)
            put("readyCount", a.readyCount)
            put("progressPercent", a.progressPercent)
            put("errorCode", a.errorCode)
            put("latestAttemptId", a.latestAttemptId)
            put("latestAttemptStatus", a.latestAttemptStatus)
            put("latestAttemptScore", a.latestAttemptScore)
            put("latestAttemptMaximumScore", a.latestAttemptMaximumScore)
            put("lastSyncedAt", a.lastSyncedAt)
        }

        private fun mapQuestion(c: Cursor) = CachedPracticeQuestionEntity(
            ownerUserId = c.getString(c.getColumnIndexOrThrow("ownerUserId")),
            activityId = c.getString(c.getColumnIndexOrThrow("activityId")),
            questionId = c.getString(c.getColumnIndexOrThrow("questionId")),
            position = c.getInt(c.getColumnIndexOrThrow("position")),
            question = c.getString(c.getColumnIndexOrThrow("question")),
            optionsJson = c.getString(c.getColumnIndexOrThrow("optionsJson")),
            section = c.getStringOrNull("section"),
            subject = c.getStringOrNull("subject"),
            topic = c.getStringOrNull("topic"),
            marks = c.getDouble(c.getColumnIndexOrThrow("marks")),
            negativeMarks = c.getDouble(c.getColumnIndexOrThrow("negativeMarks")),
            format = c.getStringOrNull("format"),
            language = c.getString(c.getColumnIndexOrThrow("language")),
            lastSyncedAt = c.getLong(c.getColumnIndexOrThrow("lastSyncedAt"))
        )

        private fun questionToValues(q: CachedPracticeQuestionEntity) = ContentValues().apply {
            put("ownerUserId", q.ownerUserId)
            put("activityId", q.activityId)
            put("questionId", q.questionId)
            put("position", q.position)
            put("question", q.question)
            put("optionsJson", q.optionsJson)
            put("section", q.section)
            put("subject", q.subject)
            put("topic", q.topic)
            put("marks", q.marks)
            put("negativeMarks", q.negativeMarks)
            put("format", q.format)
            put("language", q.language)
            put("lastSyncedAt", q.lastSyncedAt)
        }
    }

    private val performanceDaoImpl = object : PerformanceDao {
        override fun getPerformance(ownerUserId: String, examProfileId: String, view: String, subjectId: String): Flow<CachedStudentPerformanceEntity?> = flow {
            emit(getPerformanceSync(ownerUserId, examProfileId, view, subjectId))
            tableChangeNotifier.collect { if (it == "cached_student_performance") emit(getPerformanceSync(ownerUserId, examProfileId, view, subjectId)) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getPerformanceSync(ownerUserId: String, examProfileId: String, view: String, subjectId: String): CachedStudentPerformanceEntity? = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM cached_student_performance WHERE ownerUserId = ? AND examProfileId = ? AND view = ? AND subjectId = ? LIMIT 1", arrayOf(ownerUserId, examProfileId, view, subjectId))
            cursor.use {
                if (it.moveToFirst()) {
                    CachedStudentPerformanceEntity(
                        ownerUserId = it.getString(it.getColumnIndexOrThrow("ownerUserId")),
                        examProfileId = it.getString(it.getColumnIndexOrThrow("examProfileId")),
                        view = it.getString(it.getColumnIndexOrThrow("view")),
                        subjectId = it.getString(it.getColumnIndexOrThrow("subjectId")),
                        overallJson = it.getString(it.getColumnIndexOrThrow("overallJson")),
                        itemsJson = it.getString(it.getColumnIndexOrThrow("itemsJson")),
                        insightsJson = it.getString(it.getColumnIndexOrThrow("insightsJson")),
                        isUpdatingLatestPerformance = it.getInt(it.getColumnIndexOrThrow("isUpdatingLatestPerformance")) == 1,
                        isDirty = it.getInt(it.getColumnIndexOrThrow("isDirty")) == 1,
                        lastProcessedAt = it.getStringOrNull("lastProcessedAt"),
                        lastSyncedAt = it.getLong(it.getColumnIndexOrThrow("lastSyncedAt"))
                    )
                } else null
            }
        }

        override fun getPerformanceByProfile(ownerUserId: String, examProfileId: String): Flow<CachedStudentPerformanceEntity?> = flow {
            val cursor = db.rawQuery("SELECT * FROM cached_student_performance WHERE ownerUserId = ? AND examProfileId = ? LIMIT 1", arrayOf(ownerUserId, examProfileId))
            val initial = cursor.use {
                if (it.moveToFirst()) {
                    CachedStudentPerformanceEntity(
                        ownerUserId = it.getString(it.getColumnIndexOrThrow("ownerUserId")),
                        examProfileId = it.getString(it.getColumnIndexOrThrow("examProfileId")),
                        view = it.getString(it.getColumnIndexOrThrow("view")),
                        subjectId = it.getString(it.getColumnIndexOrThrow("subjectId")),
                        overallJson = it.getString(it.getColumnIndexOrThrow("overallJson")),
                        itemsJson = it.getString(it.getColumnIndexOrThrow("itemsJson")),
                        insightsJson = it.getString(it.getColumnIndexOrThrow("insightsJson")),
                        isUpdatingLatestPerformance = it.getInt(it.getColumnIndexOrThrow("isUpdatingLatestPerformance")) == 1,
                        isDirty = it.getInt(it.getColumnIndexOrThrow("isDirty")) == 1,
                        lastProcessedAt = it.getStringOrNull("lastProcessedAt"),
                        lastSyncedAt = it.getLong(it.getColumnIndexOrThrow("lastSyncedAt"))
                    )
                } else null
            }
            emit(initial)
            tableChangeNotifier.collect { if (it == "cached_student_performance") {
                val c = db.rawQuery("SELECT * FROM cached_student_performance WHERE ownerUserId = ? AND examProfileId = ? LIMIT 1", arrayOf(ownerUserId, examProfileId))
                emit(c.use { row ->
                    if (row.moveToFirst()) {
                        CachedStudentPerformanceEntity(
                            ownerUserId = row.getString(row.getColumnIndexOrThrow("ownerUserId")),
                            examProfileId = row.getString(row.getColumnIndexOrThrow("examProfileId")),
                            view = row.getString(row.getColumnIndexOrThrow("view")),
                            subjectId = row.getString(row.getColumnIndexOrThrow("subjectId")),
                            overallJson = row.getString(row.getColumnIndexOrThrow("overallJson")),
                            itemsJson = row.getString(row.getColumnIndexOrThrow("itemsJson")),
                            insightsJson = row.getString(row.getColumnIndexOrThrow("insightsJson")),
                            isUpdatingLatestPerformance = row.getInt(row.getColumnIndexOrThrow("isUpdatingLatestPerformance")) == 1,
                            isDirty = row.getInt(row.getColumnIndexOrThrow("isDirty")) == 1,
                            lastProcessedAt = row.getStringOrNull("lastProcessedAt"),
                            lastSyncedAt = row.getLong(row.getColumnIndexOrThrow("lastSyncedAt"))
                        )
                    } else null
                })
            }}
        }.flowOn(Dispatchers.IO)

        override suspend fun upsertPerformance(entity: CachedStudentPerformanceEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("ownerUserId", entity.ownerUserId)
                put("examProfileId", entity.examProfileId)
                put("view", entity.view)
                put("subjectId", entity.subjectId)
                put("overallJson", entity.overallJson)
                put("itemsJson", entity.itemsJson)
                put("insightsJson", entity.insightsJson)
                put("isUpdatingLatestPerformance", if (entity.isUpdatingLatestPerformance) 1 else 0)
                put("isDirty", if (entity.isDirty) 1 else 0)
                put("lastProcessedAt", entity.lastProcessedAt)
                put("lastSyncedAt", entity.lastSyncedAt)
            }
            db.insertWithOnConflict("cached_student_performance", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("cached_student_performance")
        }

        override suspend fun markPerformanceDirty(ownerUserId: String, examProfileId: String) = withContext(Dispatchers.IO) {
            db.execSQL("UPDATE cached_student_performance SET isDirty = 1 WHERE ownerUserId = ? AND examProfileId = ?", arrayOf(ownerUserId, examProfileId))
            notifyTableChanged("cached_student_performance")
        }

        override suspend fun deletePerformanceByUser(ownerUserId: String) = withContext(Dispatchers.IO) {
            db.delete("cached_student_performance", "ownerUserId = ?", arrayOf(ownerUserId))
            notifyTableChanged("cached_student_performance")
            Unit
        }

        override suspend fun clearAllPerformance() = withContext(Dispatchers.IO) {
            db.delete("cached_student_performance", null, null)
            notifyTableChanged("cached_student_performance")
            Unit
        }
    }

    private val conversationDaoImpl = object : ConversationDao {
        override fun getSessionsByUser(userId: String): Flow<List<ConversationSessionEntity>> = flow {
            emit(getSessionsByUserSync(userId))
            tableChangeNotifier.collect { if (it == "conversation_sessions") emit(getSessionsByUserSync(userId)) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getSessionsByUserSync(userId: String): List<ConversationSessionEntity> = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM conversation_sessions WHERE userId = ? ORDER BY lastActivityAt DESC", arrayOf(userId))
            cursor.use {
                val list = mutableListOf<ConversationSessionEntity>()
                while (it.moveToNext()) {
                    list.add(
                        ConversationSessionEntity(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            userId = it.getString(it.getColumnIndexOrThrow("userId")),
                            title = it.getString(it.getColumnIndexOrThrow("title")),
                            lastActivityAt = it.getLong(it.getColumnIndexOrThrow("lastActivityAt")),
                            lastSyncedAt = it.getLong(it.getColumnIndexOrThrow("lastSyncedAt"))
                        )
                    )
                }
                list
            }
        }

        override suspend fun upsertSessions(sessions: List<ConversationSessionEntity>) = withContext(Dispatchers.IO) {
            db.beginTransaction()
            try {
                for (s in sessions) {
                    val values = ContentValues().apply {
                        put("id", s.id)
                        put("userId", s.userId)
                        put("title", s.title)
                        put("lastActivityAt", s.lastActivityAt)
                        put("lastSyncedAt", s.lastSyncedAt)
                    }
                    db.insertWithOnConflict("conversation_sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyTableChanged("conversation_sessions")
        }

        override suspend fun upsertSession(session: ConversationSessionEntity) = withContext(Dispatchers.IO) {
            upsertSessions(listOf(session))
        }

        override fun getTurnsByConversation(conversationId: String): Flow<List<ConversationTurnEntity>> = flow {
            emit(getTurnsByConversationSync(conversationId))
            tableChangeNotifier.collect { if (it == "conversation_turns") emit(getTurnsByConversationSync(conversationId)) }
        }.flowOn(Dispatchers.IO)

        override suspend fun getTurnsByConversationSync(conversationId: String): List<ConversationTurnEntity> = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM conversation_turns WHERE conversationId = ? ORDER BY createdAt ASC", arrayOf(conversationId))
            cursor.use {
                val list = mutableListOf<ConversationTurnEntity>()
                while (it.moveToNext()) {
                    list.add(
                        ConversationTurnEntity(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            userId = it.getString(it.getColumnIndexOrThrow("userId")),
                            conversationId = it.getString(it.getColumnIndexOrThrow("conversationId")),
                            originalQuery = it.getString(it.getColumnIndexOrThrow("originalQuery")),
                            finalAnswer = it.getString(it.getColumnIndexOrThrow("finalAnswer")),
                            examId = it.getStringOrNull("examId"),
                            language = it.getString(it.getColumnIndexOrThrow("language")),
                            createdAt = it.getLong(it.getColumnIndexOrThrow("createdAt")),
                            lastSyncedAt = it.getLong(it.getColumnIndexOrThrow("lastSyncedAt"))
                        )
                    )
                }
                list
            }
        }

        override suspend fun upsertTurns(turns: List<ConversationTurnEntity>) = withContext(Dispatchers.IO) {
            db.beginTransaction()
            try {
                for (t in turns) {
                    val values = ContentValues().apply {
                        put("id", t.id)
                        put("userId", t.userId)
                        put("conversationId", t.conversationId)
                        put("originalQuery", t.originalQuery)
                        put("finalAnswer", t.finalAnswer)
                        put("examId", t.examId)
                        put("language", t.language)
                        put("createdAt", t.createdAt)
                        put("lastSyncedAt", t.lastSyncedAt)
                    }
                    db.insertWithOnConflict("conversation_turns", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            notifyTableChanged("conversation_turns")
        }

        override suspend fun upsertTurn(turn: ConversationTurnEntity) = withContext(Dispatchers.IO) {
            upsertTurns(listOf(turn))
        }

        override suspend fun deleteSessionsByUser(userId: String) = withContext(Dispatchers.IO) {
            db.delete("conversation_sessions", "userId = ?", arrayOf(userId))
            notifyTableChanged("conversation_sessions")
            Unit
        }

        override suspend fun deleteTurnsByUser(userId: String) = withContext(Dispatchers.IO) {
            db.delete("conversation_turns", "userId = ?", arrayOf(userId))
            notifyTableChanged("conversation_turns")
            Unit
        }

        override suspend fun clearAllSessions() = withContext(Dispatchers.IO) {
            db.delete("conversation_sessions", null, null)
            notifyTableChanged("conversation_sessions")
            Unit
        }

        override suspend fun clearAllTurns() = withContext(Dispatchers.IO) {
            db.delete("conversation_turns", null, null)
            notifyTableChanged("conversation_turns")
            Unit
        }
    }

    private val draftDaoImpl = object : DraftDao {
        override suspend fun getDraft(draftKey: String): ChatDraftEntity? = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM chat_drafts WHERE draftKey = ? LIMIT 1", arrayOf(draftKey))
            cursor.use {
                if (it.moveToFirst()) {
                    ChatDraftEntity(
                        draftKey = it.getString(it.getColumnIndexOrThrow("draftKey")),
                        userId = it.getString(it.getColumnIndexOrThrow("userId")),
                        text = it.getString(it.getColumnIndexOrThrow("text")),
                        updatedAt = it.getLong(it.getColumnIndexOrThrow("updatedAt"))
                    )
                } else null
            }
        }

        override suspend fun saveDraft(draft: ChatDraftEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("draftKey", draft.draftKey)
                put("userId", draft.userId)
                put("text", draft.text)
                put("updatedAt", draft.updatedAt)
            }
            db.insertWithOnConflict("chat_drafts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("chat_drafts")
        }

        override suspend fun deleteDraft(draftKey: String) = withContext(Dispatchers.IO) {
            db.delete("chat_drafts", "draftKey = ?", arrayOf(draftKey))
            notifyTableChanged("chat_drafts")
            Unit
        }

        override suspend fun clearDraftsByUser(userId: String) = withContext(Dispatchers.IO) {
            db.delete("chat_drafts", "userId = ?", arrayOf(userId))
            notifyTableChanged("chat_drafts")
            Unit
        }

        override suspend fun clearAllDrafts() = withContext(Dispatchers.IO) {
            db.delete("chat_drafts", null, null)
            notifyTableChanged("chat_drafts")
            Unit
        }
    }

    private val syncMetadataDaoImpl = object : SyncMetadataDao {
        override suspend fun getMetadata(ownerUserId: String, resourceType: String, resourceScope: String): SyncMetadataEntity? = withContext(Dispatchers.IO) {
            val cursor = db.rawQuery("SELECT * FROM sync_metadata WHERE ownerUserId = ? AND resourceType = ? AND resourceScope = ? LIMIT 1", arrayOf(ownerUserId, resourceType, resourceScope))
            cursor.use {
                if (it.moveToFirst()) {
                    SyncMetadataEntity(
                        ownerUserId = it.getString(it.getColumnIndexOrThrow("ownerUserId")),
                        resourceType = it.getString(it.getColumnIndexOrThrow("resourceType")),
                        resourceScope = it.getString(it.getColumnIndexOrThrow("resourceScope")),
                        lastSuccessfulFetchAt = it.getLong(it.getColumnIndexOrThrow("lastSuccessfulFetchAt")),
                        serverUpdatedAt = it.getStringOrNull("serverUpdatedAt"),
                        syncState = it.getString(it.getColumnIndexOrThrow("syncState"))
                    )
                } else null
            }
        }

        override suspend fun upsertMetadata(metadata: SyncMetadataEntity) = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("ownerUserId", metadata.ownerUserId)
                put("resourceType", metadata.resourceType)
                put("resourceScope", metadata.resourceScope)
                put("lastSuccessfulFetchAt", metadata.lastSuccessfulFetchAt)
                put("serverUpdatedAt", metadata.serverUpdatedAt)
                put("syncState", metadata.syncState)
            }
            db.insertWithOnConflict("sync_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            notifyTableChanged("sync_metadata")
        }

        override suspend fun deleteMetadataByUser(ownerUserId: String) = withContext(Dispatchers.IO) {
            db.delete("sync_metadata", "ownerUserId = ?", arrayOf(ownerUserId))
            notifyTableChanged("sync_metadata")
            Unit
        }

        override suspend fun clearAllMetadata() = withContext(Dispatchers.IO) {
            db.delete("sync_metadata", null, null)
            notifyTableChanged("sync_metadata")
            Unit
        }
    }

    override fun profileDao(): ProfileDao = profileDaoImpl
    override fun examProfileDao(): ExamProfileDao = examProfileDaoImpl
    override fun practiceDao(): PracticeDao = practiceDaoImpl
    override fun performanceDao(): PerformanceDao = performanceDaoImpl
    override fun conversationDao(): ConversationDao = conversationDaoImpl
    override fun draftDao(): DraftDao = draftDaoImpl
    override fun syncMetadataDao(): SyncMetadataDao = syncMetadataDaoImpl

    companion object {
        @Volatile
        private var instance: AppDatabase_Impl? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: AppDatabase_Impl(context).also { instance = it }
            }
        }
    }
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndexOrThrow(columnName)
    return if (isNull(index)) null else getString(index)
}



