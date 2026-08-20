package com.example.meritrankerstudent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM cached_profiles WHERE userId = :userId LIMIT 1")
    fun getProfile(userId: String): Flow<CachedProfileEntity?>

    @Query("SELECT * FROM cached_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileSync(userId: String): CachedProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: CachedProfileEntity)

    @Query("DELETE FROM cached_profiles WHERE userId = :userId")
    suspend fun deleteProfile(userId: String)

    @Query("DELETE FROM cached_profiles")
    suspend fun clearAllProfiles()
}

@Dao
interface ExamProfileDao {
    @Query("SELECT * FROM cached_exam_profiles WHERE examProfileId = :examProfileId LIMIT 1")
    fun getExamProfile(examProfileId: String): Flow<CachedExamProfileEntity?>

    @Query("SELECT * FROM cached_exam_profiles WHERE examProfileId = :examProfileId LIMIT 1")
    suspend fun getExamProfileSync(examProfileId: String): CachedExamProfileEntity?

    @Query("SELECT * FROM cached_exam_profiles WHERE active = 1 ORDER BY examName ASC, stage ASC")
    fun getAllActiveExamProfiles(): Flow<List<CachedExamProfileEntity>>

    @Query("SELECT * FROM cached_exam_profiles WHERE active = 1 ORDER BY examName ASC, stage ASC")
    suspend fun getAllActiveExamProfilesSync(): List<CachedExamProfileEntity>

    @Query("SELECT * FROM cached_exam_profiles WHERE examId = :examId AND active = 1")
    fun getProfilesForExam(examId: String): Flow<List<CachedExamProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamProfile(profile: CachedExamProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamProfiles(profiles: List<CachedExamProfileEntity>)

    @Query("DELETE FROM cached_exam_profiles WHERE examProfileId = :examProfileId")
    suspend fun deleteExamProfile(examProfileId: String)

    @Query("DELETE FROM cached_exam_profiles")
    suspend fun clearAllExamProfiles()
}

@Dao
interface PracticeDao {
    @Query("SELECT * FROM cached_practice_activities WHERE ownerUserId = :ownerUserId ORDER BY lastSyncedAt DESC")
    fun getActivities(ownerUserId: String): Flow<List<CachedPracticeActivityEntity>>

    @Query("SELECT * FROM cached_practice_activities WHERE ownerUserId = :ownerUserId AND activityType = :activityType ORDER BY lastSyncedAt DESC")
    fun getActivitiesByType(ownerUserId: String, activityType: String): Flow<List<CachedPracticeActivityEntity>>

    @Query("SELECT * FROM cached_practice_activities WHERE activityId = :activityId LIMIT 1")
    fun getActivity(activityId: String): Flow<CachedPracticeActivityEntity?>

    @Query("SELECT * FROM cached_practice_activities WHERE activityId = :activityId LIMIT 1")
    suspend fun getActivitySync(activityId: String): CachedPracticeActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: CachedPracticeActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(activities: List<CachedPracticeActivityEntity>)

    @Query("SELECT * FROM cached_practice_questions WHERE ownerUserId = :ownerUserId AND activityId = :activityId ORDER BY position ASC")
    fun getQuestions(ownerUserId: String, activityId: String): Flow<List<CachedPracticeQuestionEntity>>

    @Query("SELECT * FROM cached_practice_questions WHERE ownerUserId = :ownerUserId AND activityId = :activityId ORDER BY position ASC")
    suspend fun getQuestionsSync(ownerUserId: String, activityId: String): List<CachedPracticeQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuestions(questions: List<CachedPracticeQuestionEntity>)

    @Query("DELETE FROM cached_practice_activities WHERE ownerUserId = :ownerUserId")
    suspend fun deleteActivitiesByUser(ownerUserId: String)

    @Query("DELETE FROM cached_practice_questions WHERE ownerUserId = :ownerUserId")
    suspend fun deleteQuestionsByUser(ownerUserId: String)

    @Query("DELETE FROM cached_practice_questions WHERE ownerUserId = :ownerUserId AND activityId = :activityId")
    suspend fun deleteQuestionsByActivity(ownerUserId: String, activityId: String)

    @Query("DELETE FROM cached_practice_activities")
    suspend fun clearAllActivities()

    @Query("DELETE FROM cached_practice_questions")
    suspend fun clearAllQuestions()
}

@Dao
interface PerformanceDao {
    @Query("SELECT * FROM cached_student_performance WHERE ownerUserId = :ownerUserId AND examProfileId = :examProfileId AND view = :view AND subjectId = :subjectId LIMIT 1")
    fun getPerformance(ownerUserId: String, examProfileId: String, view: String, subjectId: String): Flow<CachedStudentPerformanceEntity?>

    @Query("SELECT * FROM cached_student_performance WHERE ownerUserId = :ownerUserId AND examProfileId = :examProfileId AND view = :view AND subjectId = :subjectId LIMIT 1")
    suspend fun getPerformanceSync(ownerUserId: String, examProfileId: String, view: String, subjectId: String): CachedStudentPerformanceEntity?

    @Query("SELECT * FROM cached_student_performance WHERE ownerUserId = :ownerUserId AND examProfileId = :examProfileId LIMIT 1")
    fun getPerformanceByProfile(ownerUserId: String, examProfileId: String): Flow<CachedStudentPerformanceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerformance(entity: CachedStudentPerformanceEntity)

    @Query("UPDATE cached_student_performance SET isDirty = 1 WHERE ownerUserId = :ownerUserId AND examProfileId = :examProfileId")
    suspend fun markPerformanceDirty(ownerUserId: String, examProfileId: String)

    @Query("DELETE FROM cached_student_performance WHERE ownerUserId = :ownerUserId")
    suspend fun deletePerformanceByUser(ownerUserId: String)

    @Query("DELETE FROM cached_student_performance")
    suspend fun clearAllPerformance()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_sessions WHERE userId = :userId ORDER BY lastActivityAt DESC")
    fun getSessionsByUser(userId: String): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions WHERE userId = :userId ORDER BY lastActivityAt DESC")
    suspend fun getSessionsByUserSync(userId: String): List<ConversationSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<ConversationSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ConversationSessionEntity)

    @Query("DELETE FROM conversation_sessions WHERE userId = :userId")
    suspend fun deleteSessionsByUser(userId: String)

    @Query("SELECT * FROM conversation_turns WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getTurnsByConversation(conversationId: String): Flow<List<ConversationTurnEntity>>

    @Query("SELECT * FROM conversation_turns WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getTurnsByConversationSync(conversationId: String): List<ConversationTurnEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTurns(turns: List<ConversationTurnEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTurn(turn: ConversationTurnEntity)

    @Query("DELETE FROM conversation_turns WHERE userId = :userId")
    suspend fun deleteTurnsByUser(userId: String)

    @Query("DELETE FROM conversation_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM conversation_turns")
    suspend fun clearAllTurns()
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM chat_drafts WHERE draftKey = :draftKey LIMIT 1")
    suspend fun getDraft(draftKey: String): ChatDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: ChatDraftEntity)

    @Query("DELETE FROM chat_drafts WHERE draftKey = :draftKey")
    suspend fun deleteDraft(draftKey: String)

    @Query("DELETE FROM chat_drafts WHERE userId = :userId")
    suspend fun clearDraftsByUser(userId: String)

    @Query("DELETE FROM chat_drafts")
    suspend fun clearAllDrafts()
}

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE ownerUserId = :ownerUserId AND resourceType = :resourceType AND resourceScope = :resourceScope LIMIT 1")
    suspend fun getMetadata(ownerUserId: String, resourceType: String, resourceScope: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE ownerUserId = :ownerUserId")
    suspend fun deleteMetadataByUser(ownerUserId: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun clearAllMetadata()
}
