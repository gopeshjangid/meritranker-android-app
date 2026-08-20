package com.example.meritrankerstudent.data.model

/**
 * Authoritative GraphQL data models matching Amplify Gen2 Practice schema (practiceHandler).
 */

enum class PracticeActivityType {
    QUIZ,
    MINI_MOCK
}

enum class PracticeAttemptStatus {
    IN_PROGRESS,
    SUBMITTED,
    EXPIRED
}

enum class PracticeGenerationStatus {
    GENERATING,
    READY,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED
}

data class PracticeActivitySummary(
    val activityId: String,
    val title: String,
    val activityType: PracticeActivityType = PracticeActivityType.QUIZ,
    val language: String = "en",
    val subject: String? = null,
    val topic: String? = null,
    val exams: List<String> = emptyList(),
    val questionCount: Int = 0,
    val durationMinutes: Int? = null,
    val hasResumableAttempt: Boolean = false,
    val resumableAttemptId: String? = null,
    val generationStatus: String? = null,
    val readyCount: Int? = null,
    val progressPercent: Int? = null,
    val playable: Boolean = true,
    val errorCode: String? = null,
    val latestAttemptId: String? = null,
    val latestAttemptStatus: String? = null,
    val latestAttemptScore: Double? = null,
    val latestAttemptMaximumScore: Double? = null
)

data class PracticeQuestionPayload(
    val questionId: String,
    val position: Int,
    val question: String,
    val options: List<String> = emptyList(),
    val section: String? = null,
    val subject: String? = null,
    val topic: String? = null,
    val marks: Double = 1.0,
    val negativeMarks: Double = 0.0,
    val format: String? = null,
    val language: String = "en",
    val imageUrl: String? = null
)

data class PracticeAttemptPayload(
    val attemptId: String,
    val activityId: String,
    val activityType: PracticeActivityType = PracticeActivityType.QUIZ,
    val status: PracticeAttemptStatus = PracticeAttemptStatus.IN_PROGRESS,
    val startedAt: String = "",
    val expiresAt: String? = null,
    val currentQuestionPosition: Int = 1,
    val score: Double? = null,
    val maximumScore: Double? = null,
    val correctCount: Int? = null,
    val incorrectCount: Int? = null,
    val unansweredCount: Int? = null,
    val negativeMarks: Double? = null,
    val submittedAt: String? = null
)

data class PracticeResponsePayload(
    val questionId: String,
    val selectedOption: String? = null,
    val isAnswerLocked: Boolean = false,
    val isMarkedForReview: Boolean = false
)

data class PracticeFeedbackPayload(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val explanation: String? = null,
    val errorCode: String? = null
)

data class PracticeResultPayload(
    val attemptId: String,
    val activityId: String,
    val status: PracticeAttemptStatus = PracticeAttemptStatus.SUBMITTED,
    val score: Double,
    val maximumScore: Double,
    val correctCount: Int,
    val incorrectCount: Int,
    val unansweredCount: Int,
    val negativeMarks: Double,
    val submittedAt: String
) {
    val accuracyPercentage: Int
        get() {
            val attempted = correctCount + incorrectCount
            return if (attempted > 0) ((correctCount.toDouble() / attempted) * 100).toInt() else 0
        }
}

data class PracticeReviewQuestionPayload(
    val questionId: String,
    val position: Int,
    val question: String,
    val options: List<String> = emptyList(),
    val selectedOption: String? = null,
    val correctAnswer: String,
    val explanation: String? = null,
    val isCorrect: Boolean? = null
)

data class PracticeStartPayload(
    val summary: PracticeActivitySummary,
    val attempt: PracticeAttemptPayload,
    val questions: List<PracticeQuestionPayload>,
    val responses: List<PracticeResponsePayload>
)

data class PracticeGenerationProgress(
    val testId: String,
    val status: PracticeGenerationStatus,
    val readyCount: Int,
    val totalQuestions: Int,
    val progressPercent: Int,
    val playable: Boolean,
    val errorCode: String? = null,
    val cancelRequested: Boolean = false,
    val updatedAt: String = ""
)
