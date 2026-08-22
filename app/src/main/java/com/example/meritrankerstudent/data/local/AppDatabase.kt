package com.example.meritrankerstudent.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedProfileEntity::class,
        CachedExamProfileEntity::class,
        CachedPracticeActivityEntity::class,
        CachedPracticeQuestionEntity::class,
        CachedStudentPerformanceEntity::class,
        ConversationSessionEntity::class,
        ConversationTurnEntity::class,
        ChatDraftEntity::class,
        SyncMetadataEntity::class,
        PurchaseTransactionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun examProfileDao(): ExamProfileDao
    abstract fun practiceDao(): PracticeDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun conversationDao(): ConversationDao
    abstract fun draftDao(): DraftDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun purchaseTransactionDao(): PurchaseTransactionDao

    suspend fun clearUserCache(userId: String) {
        // Purge USER-PRIVATE data only, retain shared ExamProfile catalog
        profileDao().deleteProfile(userId)
        practiceDao().deleteActivitiesByUser(userId)
        practiceDao().deleteQuestionsByUser(userId)
        performanceDao().deletePerformanceByUser(userId)
        conversationDao().deleteSessionsByUser(userId)
        conversationDao().deleteTurnsByUser(userId)
        draftDao().clearDraftsByUser(userId)
        syncMetadataDao().deleteMetadataByUser(userId)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Alter cached_profiles to add new columns
                db.execSQL("ALTER TABLE cached_profiles ADD COLUMN examProfileId TEXT")
                db.execSQL("ALTER TABLE cached_profiles ADD COLUMN examStage TEXT")
                db.execSQL("ALTER TABLE cached_profiles ADD COLUMN examTagsJson TEXT")
                db.execSQL("ALTER TABLE cached_profiles ADD COLUMN profileCompleted INTEGER")
                db.execSQL("ALTER TABLE cached_profiles ADD COLUMN onboardingStep TEXT")
                db.execSQL("ALTER TABLE cached_profiles ADD COLUMN role TEXT")

                // Alter conversation_turns to add lastSyncedAt
                db.execSQL("ALTER TABLE conversation_turns ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")

                // Create cached_exam_profiles table & indices (Global shared catalog)
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

                // Create cached_practice_activities table & indices
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

                // Create cached_practice_questions with composite primary key (ownerUserId, activityId, questionId)
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

                // Create cached_student_performance with composite primary key (ownerUserId, examProfileId, view, subjectId)
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

                // Create sync_metadata with composite primary key (ownerUserId, resourceType, resourceScope)
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
        }

        fun getInstance(context: Context): AppDatabase {
            return AppDatabase_Impl.getInstance(context)
        }
    }
}
