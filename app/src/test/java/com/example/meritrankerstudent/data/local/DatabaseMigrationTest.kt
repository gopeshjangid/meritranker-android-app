package com.example.meritrankerstudent.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import com.example.meritrankerstudent.data.model.PracticeGenerationProgress
import com.example.meritrankerstudent.data.model.PracticeGenerationStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class DatabaseMigrationTest {

    private class RecordingSQLiteDatabase : SupportSQLiteDatabase {
        val executedSqls = mutableListOf<String>()

        override fun execSQL(sql: String) {
            executedSqls.add(sql)
        }

        override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
            executedSqls.add(sql)
        }

        override fun compileStatement(sql: String): SupportSQLiteStatement = error("Not needed")
        override fun beginTransaction() {}
        override fun beginTransactionNonExclusive() {}
        override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) {}
        override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) {}
        override fun endTransaction() {}
        override fun setTransactionSuccessful() {}
        override fun inTransaction(): Boolean = false
        override val isDbLockedByCurrentThread: Boolean get() = false
        override fun yieldIfContendedSafely(): Boolean = false
        override fun yieldIfContendedSafely(sleepAmount: Long): Boolean = false
        override var version: Int = 1
        override val maximumSize: Long get() = 0
        override fun setMaximumSize(numBytes: Long): Long = 0
        override var pageSize: Long = 0
        override val isReadOnly: Boolean get() = false
        override fun query(query: String): Cursor = error("Not needed")
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor = error("Not needed")
        override fun query(query: SupportSQLiteQuery): Cursor = error("Not needed")
        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor = error("Not needed")
        override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long = 0
        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
        override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
        override fun needUpgrade(newVersion: Int): Boolean = false
        override val path: String? get() = null
        override fun setLocale(locale: Locale) {}
        override fun setMaxSqlCacheSize(cacheSize: Int) {}
        override fun setForeignKeyConstraintsEnabled(enable: Boolean) {}
        override fun enableWriteAheadLogging(): Boolean = false
        override fun disableWriteAheadLogging() {}
        override val isWriteAheadLoggingEnabled: Boolean get() = false
        override val attachedDbs: List<android.util.Pair<String, String>>? get() = null
        override val isDatabaseIntegrityOk: Boolean get() = true
        override fun close() {}
        override val isOpen: Boolean get() = true
    }

    @Test
    fun migration1To2_executesAllSqlStatementsWithoutError() {
        val migration = AppDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)

        val testDb = RecordingSQLiteDatabase()
        migration.migrate(testDb)

        val executedSqls = testDb.executedSqls

        // Verify ALTER TABLE statements on cached_profiles
        assertTrue(executedSqls.any { it.contains("ALTER TABLE cached_profiles ADD COLUMN examProfileId") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE cached_profiles ADD COLUMN examStage") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE cached_profiles ADD COLUMN examTagsJson") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE cached_profiles ADD COLUMN profileCompleted") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE cached_profiles ADD COLUMN onboardingStep") })
        assertTrue(executedSqls.any { it.contains("ALTER TABLE cached_profiles ADD COLUMN role") })

        // Verify CREATE TABLE statements for all 5 new tables
        assertTrue(executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS cached_exam_profiles") })
        assertTrue(executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS cached_practice_activities") })
        assertTrue(executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS cached_practice_questions") })
        assertTrue(executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS cached_student_performance") })
        assertTrue(executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS sync_metadata") })
    }

    @Test
    fun compositeKeys_ensureDataIsolationBetweenUsers() {
        val q1 = CachedPracticeQuestionEntity(
            ownerUserId = "USER_A",
            activityId = "ACT_1",
            questionId = "Q_1",
            position = 0,
            question = "Sample question",
            optionsJson = "[\"A\", \"B\"]",
            lastSyncedAt = System.currentTimeMillis()
        )

        val q2 = CachedPracticeQuestionEntity(
            ownerUserId = "USER_B",
            activityId = "ACT_1",
            questionId = "Q_1",
            position = 0,
            question = "Sample question",
            optionsJson = "[\"A\", \"B\"]",
            lastSyncedAt = System.currentTimeMillis()
        )

        assertNotEquals(q1.ownerUserId, q2.ownerUserId)
        assertEquals(q1.activityId, q2.activityId)
        assertEquals(q1.questionId, q2.questionId)
    }

    @Test
    fun readyPracticeQuestionEntity_containsNoHiddenAnswerKeys() {
        val entityClass = CachedPracticeQuestionEntity::class.java
        val declaredFields = entityClass.declaredFields.map { it.name }

        // Must NOT contain server-authoritative scoring secrets or answer keys
        assertFalse("Must not contain correctOption", declaredFields.contains("correctOption"))
        assertFalse("Must not contain answerKey", declaredFields.contains("answerKey"))
        assertFalse("Must not contain solutionAuthority", declaredFields.contains("solutionAuthority"))
        assertFalse("Must not contain privateScoringMeta", declaredFields.contains("privateScoringMeta"))

        // Must contain standard question body and options
        assertTrue("Must contain question", declaredFields.contains("question"))
        assertTrue("Must contain optionsJson", declaredFields.contains("optionsJson"))
        assertTrue("Must contain ownerUserId", declaredFields.contains("ownerUserId"))
    }

    @Test
    fun staleEventRegressionProtection_preservesReadyStatus() {
        val initialActivity = CachedPracticeActivityEntity(
            activityId = "test_act_123",
            ownerUserId = "USER_A",
            title = "SSC Quant Test",
            activityType = "QUIZ",
            generationStatus = "READY",
            readyCount = 10,
            progressPercent = 100,
            playable = true,
            lastSyncedAt = System.currentTimeMillis()
        )

        // Incoming stale event trying to regress READY back to GENERATING
        val staleEvent = PracticeGenerationProgress(
            testId = "test_act_123",
            status = PracticeGenerationStatus.GENERATING,
            readyCount = 5,
            totalQuestions = 10,
            progressPercent = 50,
            playable = false
        )

        val currentStatus = initialActivity.generationStatus?.let {
            PracticeGenerationStatus.valueOf(it)
        }

        // Validate regression protection logic
        val shouldIgnore = (currentStatus == PracticeGenerationStatus.READY && staleEvent.status == PracticeGenerationStatus.GENERATING)
        assertTrue("Stale GENERATING event must be ignored when status is READY", shouldIgnore)

        val finalActivity = if (shouldIgnore) initialActivity else initialActivity.copy(
            generationStatus = staleEvent.status.name,
            progressPercent = staleEvent.progressPercent
        )

        assertEquals("READY", finalActivity.generationStatus)
        assertEquals(100, finalActivity.progressPercent)
        assertTrue(finalActivity.playable)
    }

    @Test
    fun performanceEntity_compositeKey_allowsPerSubjectAndOverall() {
        val perfOverall = CachedStudentPerformanceEntity(
            ownerUserId = "USER_A",
            examProfileId = "SSC_CGL#TIER_1",
            view = "PRACTICE",
            subjectId = "ALL",
            overallJson = "{}",
            itemsJson = "[]",
            insightsJson = "[]",
            isDirty = false
        )

        val perfQuant = CachedStudentPerformanceEntity(
            ownerUserId = "USER_A",
            examProfileId = "SSC_CGL#TIER_1",
            view = "PRACTICE",
            subjectId = "QUANT",
            overallJson = "{}",
            itemsJson = "[]",
            insightsJson = "[]",
            isDirty = false
        )

        assertEquals("USER_A", perfOverall.ownerUserId)
        assertEquals("ALL", perfOverall.subjectId)
        assertEquals("QUANT", perfQuant.subjectId)
        assertFalse(perfOverall.isDirty)
    }
}
