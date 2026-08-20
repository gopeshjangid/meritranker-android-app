package com.example.meritrankerstudent.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_profiles"
)
data class CachedProfileEntity(
    @PrimaryKey val userId: String,
    val name: String?,
    val email: String?,
    val preparing: String?,
    val examProfileId: String?,
    val examStage: String?,
    val additionalExamsJson: String?,
    val examTagsJson: String?,
    val dateOfBirth: String?,
    val language: String?,
    val profileCompleted: Boolean?,
    val onboardingStep: String?,
    val role: String?,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cached_exam_profiles",
    indices = [
        Index(value = ["examId"]),
        Index(value = ["active"])
    ]
)
data class CachedExamProfileEntity(
    @PrimaryKey val examProfileId: String,
    val examId: String,
    val examName: String,
    val stage: String,
    val description: String?,
    val sectionsJson: String?,
    val previousCutoffJson: String?,
    val active: Boolean = true,
    val totalQuestions: Int = 0,
    val totalMarks: Double = 0.0,
    val totalTimeMinutes: Int = 0,
    val effectiveYear: Int? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cached_practice_activities",
    indices = [
        Index(value = ["ownerUserId"]),
        Index(value = ["ownerUserId", "activityType"]),
        Index(value = ["lastSyncedAt"])
    ]
)
data class CachedPracticeActivityEntity(
    @PrimaryKey val activityId: String,
    val ownerUserId: String,
    val title: String,
    val activityType: String,
    val language: String = "en",
    val subject: String? = null,
    val topic: String? = null,
    val examsJson: String? = null,
    val questionCount: Int = 0,
    val durationMinutes: Int? = null,
    val hasResumableAttempt: Boolean = false,
    val resumableAttemptId: String? = null,
    val playable: Boolean = true,
    val generationStatus: String? = null,
    val readyCount: Int? = null,
    val progressPercent: Int? = null,
    val errorCode: String? = null,
    val latestAttemptId: String? = null,
    val latestAttemptStatus: String? = null,
    val latestAttemptScore: Double? = null,
    val latestAttemptMaximumScore: Double? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cached_practice_questions",
    primaryKeys = ["ownerUserId", "activityId", "questionId"],
    indices = [
        Index(value = ["ownerUserId", "activityId", "position"])
    ]
)
data class CachedPracticeQuestionEntity(
    val ownerUserId: String,
    val activityId: String,
    val questionId: String,
    val position: Int,
    val question: String,
    val optionsJson: String,
    val section: String? = null,
    val subject: String? = null,
    val topic: String? = null,
    val marks: Double = 1.0,
    val negativeMarks: Double = 0.0,
    val format: String? = null,
    val language: String = "en",
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cached_student_performance",
    primaryKeys = ["ownerUserId", "examProfileId", "view", "subjectId"],
    indices = [
        Index(value = ["ownerUserId", "examProfileId"]),
        Index(value = ["ownerUserId", "view"])
    ]
)
data class CachedStudentPerformanceEntity(
    val ownerUserId: String,
    val examProfileId: String,
    val view: String,
    val subjectId: String = "ALL",
    val overallJson: String,
    val itemsJson: String,
    val insightsJson: String,
    val isUpdatingLatestPerformance: Boolean = false,
    val isDirty: Boolean = false,
    val lastProcessedAt: String? = null,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversation_sessions",
    indices = [Index(value = ["userId", "lastActivityAt"])]
)
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val lastActivityAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversation_turns",
    indices = [Index(value = ["conversationId", "createdAt"]), Index(value = ["userId"])]
)
data class ConversationTurnEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val conversationId: String,
    val originalQuery: String,
    val finalAnswer: String,
    val examId: String? = null,
    val language: String = "english",
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_drafts")
data class ChatDraftEntity(
    @PrimaryKey val draftKey: String,
    val userId: String,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["ownerUserId", "resourceType", "resourceScope"],
    indices = [
        Index(value = ["ownerUserId", "resourceType"])
    ]
)
data class SyncMetadataEntity(
    val ownerUserId: String,
    val resourceType: String,
    val resourceScope: String = "GLOBAL",
    val lastSuccessfulFetchAt: Long = System.currentTimeMillis(),
    val serverUpdatedAt: String? = null,
    val syncState: String = "IDLE"
)
