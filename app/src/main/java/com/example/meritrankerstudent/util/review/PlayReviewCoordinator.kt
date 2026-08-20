package com.example.meritrankerstudent.util.review

import android.app.Activity
import android.content.Context
import android.util.Log

/**
 * Centralized Coordinator for Google Play In-App Review scheduling & policy guardrails.
 * 
 * Strict Google Policy Guarantees:
 * 1. Zero Review Gating (no "Are you enjoying MeritRanker?" sentiment pre-filtering).
 * 2. Zero Custom Star Rating Dialogs (native Google Play bottom sheet only).
 * 3. Zero Rating Incentives (no rewards, credits, or unlocking features).
 * 4. First Review: Requires >= 4 days since first meaningful usage, >= 3 active sessions, and >= 1 educational outcome.
 * 5. Second Review: Requires >= 30 days cooldown from previous attempt and continued engagement.
 * 6. Maximum Automatic Attempts: 2.
 * 7. Mandatory App Update Priority: Strictly suppresses review flow when an update is pending.
 * 8. Non-Disruptive Failure: If Review API fails or quota suppresses it, the app continues silently.
 */
class PlayReviewCoordinator(
    private val storage: PlayReviewStorage,
    private val reviewManager: PlayReviewManager
) {
    companion object {
        private const val TAG = "PlayReviewCoordinator"

        const val FIRST_ELIGIBILITY_DAYS = 4
        const val FIRST_ELIGIBILITY_MIN_SESSIONS = 3
        const val FIRST_ELIGIBILITY_MIN_OUTCOMES = 1

        const val SECOND_ELIGIBILITY_COOLDOWN_DAYS = 30
        const val SECOND_ELIGIBILITY_MIN_OUTCOMES = 2

        const val MAX_AUTOMATIC_REVIEW_ATTEMPTS = 2

        private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

        @Volatile
        private var instance: PlayReviewCoordinator? = null

        fun getInstance(context: Context): PlayReviewCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PlayReviewCoordinator(
                    storage = PlayReviewStorage(context.applicationContext),
                    reviewManager = GooglePlayReviewManager(context.applicationContext)
                ).also { instance = it }
            }
        }

        fun setTestInstance(testCoordinator: PlayReviewCoordinator?) {
            instance = testCoordinator
        }
    }

    fun recordAppSession(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val recorded = storage.recordSession(nowMillis)
        if (recorded) {
            Log.d(TAG, "Recorded active app session. Total sessions=${storage.meaningfulSessionCount}")
        }
        return recorded
    }

    fun recordMeaningfulOutcome(outcome: OutcomeType, nowMillis: Long = System.currentTimeMillis()) {
        storage.recordOutcome(nowMillis)
        Log.d(TAG, "Recorded meaningful educational outcome: $outcome. Total outcomes=${storage.meaningfulOutcomeCount}")
    }

    fun setMandatoryUpdatePending(isPending: Boolean) {
        storage.isMandatoryUpdatePending = isPending
        Log.d(TAG, "Mandatory update status changed: isPending=$isPending")
    }

    fun isEligibleForReview(nowMillis: Long = System.currentTimeMillis()): Boolean {
        // 1. Mandatory App Update Priority
        if (storage.isMandatoryUpdatePending) {
            Log.d(TAG, "Review ineligible: Mandatory app update is pending")
            return false
        }

        // 2. Maximum attempts cap
        if (storage.reviewAttemptCount >= MAX_AUTOMATIC_REVIEW_ATTEMPTS) {
            Log.d(TAG, "Review ineligible: Maximum automatic attempts reached (${storage.reviewAttemptCount}/$MAX_AUTOMATIC_REVIEW_ATTEMPTS)")
            return false
        }

        // 3. First Attempt Eligibility Rules
        if (storage.reviewAttemptCount == 0) {
            val firstUse = storage.firstMeaningfulUseAt
            if (firstUse == 0L) {
                Log.d(TAG, "Review ineligible: First meaningful use not yet recorded")
                return false
            }

            val elapsedDays = (nowMillis - firstUse) / DAY_IN_MILLIS
            val hasEnoughDays = elapsedDays >= FIRST_ELIGIBILITY_DAYS
            val hasEnoughSessions = storage.meaningfulSessionCount >= FIRST_ELIGIBILITY_MIN_SESSIONS
            val hasEnoughOutcomes = storage.meaningfulOutcomeCount >= FIRST_ELIGIBILITY_MIN_OUTCOMES

            val isEligible = hasEnoughDays && hasEnoughSessions && hasEnoughOutcomes
            Log.d(
                TAG,
                "First review eligibility check: elapsedDays=$elapsedDays (need >=$FIRST_ELIGIBILITY_DAYS), " +
                "sessions=${storage.meaningfulSessionCount} (need >=$FIRST_ELIGIBILITY_MIN_SESSIONS), " +
                "outcomes=${storage.meaningfulOutcomeCount} (need >=$FIRST_ELIGIBILITY_MIN_OUTCOMES) -> isEligible=$isEligible"
            )
            return isEligible
        }

        // 4. Second Attempt Eligibility Rules (>= 30 days cooldown)
        if (storage.reviewAttemptCount == 1) {
            val lastAttempt = storage.lastReviewAttemptAt
            val elapsedDaysSinceLastAttempt = (nowMillis - lastAttempt) / DAY_IN_MILLIS
            val hasMetCooldown = elapsedDaysSinceLastAttempt >= SECOND_ELIGIBILITY_COOLDOWN_DAYS
            val hasContinuedUsage = storage.meaningfulOutcomeCount >= SECOND_ELIGIBILITY_MIN_OUTCOMES

            val isEligible = hasMetCooldown && hasContinuedUsage
            Log.d(
                TAG,
                "Second review eligibility check: cooldownDays=$elapsedDaysSinceLastAttempt (need >=$SECOND_ELIGIBILITY_COOLDOWN_DAYS), " +
                "outcomes=${storage.meaningfulOutcomeCount} (need >=$SECOND_ELIGIBILITY_MIN_OUTCOMES) -> isEligible=$isEligible"
            )
            return isEligible
        }

        return false
    }

    fun maybeRequestReview(
        activity: Activity,
        moment: ReviewTriggerMoment,
        nowMillis: Long = System.currentTimeMillis(),
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!isEligibleForReview(nowMillis)) {
            onComplete(false)
            return
        }

        Log.i(TAG, "Triggering Google Play In-App Review at moment=$moment (attempt #${storage.reviewAttemptCount + 1})")
        // Record attempt timestamp immediately before launching
        storage.recordReviewAttempt(nowMillis)

        try {
            reviewManager.launchReviewFlow(activity) { success ->
                Log.d(TAG, "In-App Review flow finished: success=$success")
                onComplete(success)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Safe silent catch during review flow execution", e)
            onComplete(false)
        }
    }

    fun resetForTesting() {
        storage.clearAll()
    }
}
