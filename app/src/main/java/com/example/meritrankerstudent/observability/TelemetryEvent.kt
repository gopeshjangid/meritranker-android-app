package com.example.meritrankerstudent.observability

import android.os.Bundle

sealed class TelemetryEvent(
    val eventName: String,
    val params: Map<String, Any> = emptyMap()
) {

    fun toBundle(): Bundle {
        val sanitized = PiiSanitizer.sanitizeMap(params)
        val bundle = Bundle()
        for ((key, value) in sanitized) {
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putFloat(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        return bundle
    }

    // -------------------------------------------------------------
    // Screen View
    // -------------------------------------------------------------
    data class ScreenView(val screen: CanonicalScreen) : TelemetryEvent(
        eventName = "screen_view",
        params = mapOf(
            "screen_name" to screen.screenName,
            "screen_class" to screen.name
        )
    )

    // -------------------------------------------------------------
    // Auth & Onboarding Funnel
    // -------------------------------------------------------------
    data class LoginStarted(val method: String = "google") : TelemetryEvent(
        eventName = "login_started",
        params = mapOf("method" to method)
    )

    data class LoginSucceeded(val method: String = "google") : TelemetryEvent(
        eventName = "login_succeeded",
        params = mapOf("method" to method)
    )

    data class LoginFailed(val method: String = "google", val errorCategory: ErrorCategory) : TelemetryEvent(
        eventName = "login_failed",
        params = mapOf(
            "method" to method,
            "error_category" to errorCategory.key
        )
    )

    object OnboardingStarted : TelemetryEvent(eventName = "onboarding_started")

    data class OnboardingCompleted(
        val examProfileId: String,
        val stage: String?,
        val language: String?
    ) : TelemetryEvent(
        eventName = "onboarding_completed",
        params = buildMap {
            put("exam_profile_id", examProfileId)
            stage?.let { put("stage", it) }
            language?.let { put("study_language", it) }
        }
    )

    data class OnboardingFailed(val errorCategory: ErrorCategory) : TelemetryEvent(
        eventName = "onboarding_failed",
        params = mapOf("error_category" to errorCategory.key)
    )

    data class ExamProfileSelected(
        val examProfileId: String,
        val stage: String?
    ) : TelemetryEvent(
        eventName = "exam_profile_selected",
        params = buildMap {
            put("exam_profile_id", examProfileId)
            stage?.let { put("stage", it) }
        }
    )

    // -------------------------------------------------------------
    // Smart Tutor Funnel
    // -------------------------------------------------------------
    data class DoubtSubmitted(
        val examProfileId: String?,
        val hasAttachment: Boolean,
        val isVoice: Boolean
    ) : TelemetryEvent(
        eventName = "doubt_submitted",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("has_attachment", hasAttachment)
            put("is_voice", isVoice)
        }
    )

    data class DoubtStreamStarted(val examProfileId: String?) : TelemetryEvent(
        eventName = "doubt_stream_started",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
        }
    )

    data class DoubtFirstResponseReceived(
        val examProfileId: String?,
        val latencyBucket: String
    ) : TelemetryEvent(
        eventName = "doubt_first_response_received",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("latency_bucket", latencyBucket)
        }
    )

    data class DoubtCompleted(
        val examProfileId: String?,
        val durationBucket: String,
        val isFollowUp: Boolean
    ) : TelemetryEvent(
        eventName = "doubt_completed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("duration_bucket", durationBucket)
            put("is_follow_up", isFollowUp)
        }
    )

    data class DoubtFailed(
        val examProfileId: String?,
        val errorCategory: ErrorCategory,
        val durationBucket: String? = null
    ) : TelemetryEvent(
        eventName = "doubt_failed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("error_category", errorCategory.key)
            durationBucket?.let { put("duration_bucket", it) }
        }
    )

    data class DoubtReportSubmitted(val category: String) : TelemetryEvent(
        eventName = "doubt_report_submitted",
        params = mapOf("report_category" to category)
    )

    // -------------------------------------------------------------
    // Voice UX
    // -------------------------------------------------------------
    data class VoiceStarted(val mode: String) : TelemetryEvent(
        eventName = "voice_started",
        params = mapOf("mode" to mode)
    )

    data class VoiceCompleted(val mode: String, val durationBucket: String) : TelemetryEvent(
        eventName = "voice_completed",
        params = mapOf(
            "mode" to mode,
            "duration_bucket" to durationBucket
        )
    )

    data class VoiceCancelled(val mode: String) : TelemetryEvent(
        eventName = "voice_cancelled",
        params = mapOf("mode" to mode)
    )

    data class VoiceFailed(val mode: String, val errorCategory: ErrorCategory) : TelemetryEvent(
        eventName = "voice_failed",
        params = mapOf(
            "mode" to mode,
            "error_category" to errorCategory.key
        )
    )

    // -------------------------------------------------------------
    // Practice Funnel
    // -------------------------------------------------------------
    data class PracticeGenerationStarted(
        val examProfileId: String?,
        val subjectId: String?,
        val practiceType: String,
        val questionCountBucket: String
    ) : TelemetryEvent(
        eventName = "practice_generation_started",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            subjectId?.let { put("subject_id", it) }
            put("practice_type", practiceType)
            put("question_count_bucket", questionCountBucket)
        }
    )

    data class PracticeGenerationReady(
        val examProfileId: String?,
        val subjectId: String?,
        val practiceType: String,
        val durationBucket: String
    ) : TelemetryEvent(
        eventName = "practice_generation_ready",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            subjectId?.let { put("subject_id", it) }
            put("practice_type", practiceType)
            put("duration_bucket", durationBucket)
        }
    )

    data class PracticeGenerationFailed(
        val examProfileId: String?,
        val subjectId: String?,
        val practiceType: String,
        val errorCategory: ErrorCategory
    ) : TelemetryEvent(
        eventName = "practice_generation_failed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            subjectId?.let { put("subject_id", it) }
            put("practice_type", practiceType)
            put("error_category", errorCategory.key)
        }
    )

    data class PracticeGenerationStalled(
        val examProfileId: String?,
        val practiceType: String
    ) : TelemetryEvent(
        eventName = "practice_generation_stalled",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("practice_type", practiceType)
        }
    )

    data class PracticeViewed(val examProfileId: String?) : TelemetryEvent(
        eventName = "practice_viewed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
        }
    )

    data class PracticeStarted(
        val examProfileId: String?,
        val practiceType: String,
        val questionCountBucket: String
    ) : TelemetryEvent(
        eventName = "practice_started",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("practice_type", practiceType)
            put("question_count_bucket", questionCountBucket)
        }
    )

    data class PracticeResumed(
        val examProfileId: String?,
        val practiceType: String
    ) : TelemetryEvent(
        eventName = "practice_resumed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("practice_type", practiceType)
        }
    )

    data class PracticeSubmitted(
        val examProfileId: String?,
        val practiceType: String,
        val answeredCountBucket: String
    ) : TelemetryEvent(
        eventName = "practice_submitted",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("practice_type", practiceType)
            put("answered_count_bucket", answeredCountBucket)
        }
    )

    data class PracticeCompleted(
        val examProfileId: String?,
        val practiceType: String,
        val durationBucket: String
    ) : TelemetryEvent(
        eventName = "practice_completed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("practice_type", practiceType)
            put("duration_bucket", durationBucket)
        }
    )

    data class PracticeAbandoned(
        val examProfileId: String?,
        val practiceType: String,
        val progressBucket: String
    ) : TelemetryEvent(
        eventName = "practice_abandoned",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("practice_type", practiceType)
            put("progress_bucket", progressBucket)
        }
    )

    data class PracticeResultViewed(
        val examProfileId: String?,
        val practiceType: String
    ) : TelemetryEvent(
        eventName = "practice_result_viewed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("practice_type", practiceType)
        }
    )

    // -------------------------------------------------------------
    // Progress Usage
    // -------------------------------------------------------------
    data class ProgressViewed(
        val examProfileId: String?,
        val view: String
    ) : TelemetryEvent(
        eventName = "progress_viewed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("view", view)
        }
    )

    data class SubjectProgressViewed(
        val examProfileId: String?,
        val subjectId: String,
        val view: String
    ) : TelemetryEvent(
        eventName = "subject_progress_viewed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("subject_id", subjectId)
            put("view", view)
        }
    )

    data class ProgressRefreshFailed(
        val examProfileId: String?,
        val errorCategory: ErrorCategory
    ) : TelemetryEvent(
        eventName = "progress_refresh_failed",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            put("error_category", errorCategory.key)
        }
    )

    // -------------------------------------------------------------
    // Profile & Support
    // -------------------------------------------------------------
    object ProfileViewed : TelemetryEvent(eventName = "profile_viewed")

    data class ProfileUpdated(
        val examProfileId: String?,
        val language: String?
    ) : TelemetryEvent(
        eventName = "profile_updated",
        params = buildMap {
            examProfileId?.let { put("exam_profile_id", it) }
            language?.let { put("study_language", it) }
        }
    )

    object ProductFeedbackOpened : TelemetryEvent(eventName = "product_feedback_opened")

    data class ProductFeedbackSubmitted(val category: String) : TelemetryEvent(
        eventName = "product_feedback_submitted",
        params = mapOf("category" to category)
    )

    data class ProductFeedbackFailed(val errorCategory: ErrorCategory) : TelemetryEvent(
        eventName = "product_feedback_failed",
        params = mapOf("error_category" to errorCategory.key)
    )

    data class AiReportSubmitted(val category: String) : TelemetryEvent(
        eventName = "ai_report_submitted",
        params = mapOf("report_category" to category)
    )

    object AccountDeletionStarted : TelemetryEvent(eventName = "account_deletion_started")

    // -------------------------------------------------------------
    // Reliability & System
    // -------------------------------------------------------------
    data class FeatureError(
        val feature: String,
        val operation: String,
        val errorCategory: ErrorCategory,
        val isRetryable: Boolean
    ) : TelemetryEvent(
        eventName = "feature_error",
        params = mapOf(
            "feature" to feature,
            "operation" to operation,
            "error_category" to errorCategory.key,
            "is_retryable" to isRetryable
        )
    )

    data class BackendOperationFailed(
        val feature: String,
        val operation: String,
        val errorCategory: ErrorCategory,
        val statusBucket: String,
        val isRetryable: Boolean,
        val networkState: String
    ) : TelemetryEvent(
        eventName = "backend_operation_failed",
        params = mapOf(
            "feature" to feature,
            "operation" to operation,
            "error_category" to errorCategory.key,
            "status_bucket" to statusBucket,
            "is_retryable" to isRetryable,
            "network_state" to networkState
        )
    )

    data class BackendOperation(
        val feature: String,
        val operation: String,
        val outcome: String, // "success" or "failure"
        val durationBucket: String,
        val errorCategory: ErrorCategory? = null,
        val statusBucket: String? = null,
        val networkState: String? = null
    ) : TelemetryEvent(
        eventName = "backend_operation",
        params = buildMap {
            put("feature", feature)
            put("operation", operation)
            put("outcome", outcome)
            put("duration_bucket", durationBucket)
            errorCategory?.let { put("error_category", it.key) }
            statusBucket?.let { put("status_bucket", it) }
            networkState?.let { put("network_state", it) }
        }
    )

    data class UpdateRequired(
        val currentVersion: String,
        val targetVersion: String
    ) : TelemetryEvent(
        eventName = "update_required",
        params = mapOf(
            "current_version" to currentVersion,
            "target_version" to targetVersion
        )
    )

    object UpdateStarted : TelemetryEvent(eventName = "update_started")
    object UpdateCancelled : TelemetryEvent(eventName = "update_cancelled")

    data class UpdateFailed(val errorCategory: ErrorCategory) : TelemetryEvent(
        eventName = "update_failed",
        params = mapOf("error_category" to errorCategory.key)
    )
}
