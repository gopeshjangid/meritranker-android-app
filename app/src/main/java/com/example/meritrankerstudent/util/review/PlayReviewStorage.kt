package com.example.meritrankerstudent.util.review

import android.content.Context
import android.content.SharedPreferences

/**
 * Durable Local-Only Storage for Google Play In-App Review scheduling.
 * 
 * Guarantees:
 * - Stored strictly on device in private SharedPreferences.
 * - Zero personal or sensitive data stored.
 * - Does not store a fake 'hasUserReviewed' flag (Google intentionally keeps review submissions private).
 */
class PlayReviewStorage(context: Context) {

    companion object {
        private const val PREFS_NAME = "meritranker_play_review_prefs"
        private const val KEY_FIRST_MEANINGFUL_USE_AT = "first_meaningful_use_at"
        private const val KEY_MEANINGFUL_SESSION_COUNT = "meaningful_session_count"
        private const val KEY_LAST_SESSION_TIMESTAMP = "last_session_timestamp"
        private const val KEY_MEANINGFUL_OUTCOME_COUNT = "meaningful_outcome_count"
        private const val KEY_REVIEW_ATTEMPT_COUNT = "review_attempt_count"
        private const val KEY_LAST_REVIEW_ATTEMPT_AT = "last_review_attempt_at"
        private const val KEY_MANDATORY_UPDATE_PENDING = "mandatory_update_pending"
        private const val KEY_LAST_PRIVATE_FEEDBACK_NUDGE_AT = "last_private_feedback_nudge_at"

        // Minimum gap to count as a distinct meaningful session (e.g. 15 minutes)
        private const val SESSION_GAP_THRESHOLD_MS = 15 * 60 * 1000L
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var firstMeaningfulUseAt: Long
        get() = prefs.getLong(KEY_FIRST_MEANINGFUL_USE_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_FIRST_MEANINGFUL_USE_AT, value).apply()

    var meaningfulSessionCount: Int
        get() = prefs.getInt(KEY_MEANINGFUL_SESSION_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_MEANINGFUL_SESSION_COUNT, value).apply()

    var lastSessionTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SESSION_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SESSION_TIMESTAMP, value).apply()

    var meaningfulOutcomeCount: Int
        get() = prefs.getInt(KEY_MEANINGFUL_OUTCOME_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_MEANINGFUL_OUTCOME_COUNT, value).apply()

    var reviewAttemptCount: Int
        get() = prefs.getInt(KEY_REVIEW_ATTEMPT_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_REVIEW_ATTEMPT_COUNT, value).apply()

    var lastReviewAttemptAt: Long
        get() = prefs.getLong(KEY_LAST_REVIEW_ATTEMPT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REVIEW_ATTEMPT_AT, value).apply()

    var isMandatoryUpdatePending: Boolean
        get() = prefs.getBoolean(KEY_MANDATORY_UPDATE_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_MANDATORY_UPDATE_PENDING, value).apply()

    var lastPrivateFeedbackNudgeAt: Long
        get() = prefs.getLong(KEY_LAST_PRIVATE_FEEDBACK_NUDGE_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PRIVATE_FEEDBACK_NUDGE_AT, value).apply()

    fun recordSession(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = lastSessionTimestamp
        if (last == 0L || (nowMillis - last) > SESSION_GAP_THRESHOLD_MS) {
            lastSessionTimestamp = nowMillis
            meaningfulSessionCount += 1
            return true
        }
        return false
    }

    fun recordOutcome(nowMillis: Long = System.currentTimeMillis()) {
        if (firstMeaningfulUseAt == 0L) {
            firstMeaningfulUseAt = nowMillis
        }
        meaningfulOutcomeCount += 1
    }

    fun recordReviewAttempt(nowMillis: Long = System.currentTimeMillis()) {
        reviewAttemptCount += 1
        lastReviewAttemptAt = nowMillis
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
