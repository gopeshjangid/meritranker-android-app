package com.example.meritrankerstudent.util.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

enum class OutcomeType {
    PRACTICE_COMPLETED,
    SMART_TUTOR_ANSWER_COMPLETED,
    MOCK_TEST_COMPLETED,
    QUIZ_COMPLETED
}

enum class ReviewTriggerMoment {
    PRACTICE_RESULT_VIEWED,
    SMART_TUTOR_IDLE
}

/**
 * Interface wrapping Google Play In-App Review operations.
 * Allows decoupling from Google Play Services for unit testing and offline robustness.
 */
interface PlayReviewManager {
    fun launchReviewFlow(activity: Activity, onComplete: (Boolean) -> Unit)
}

/**
 * Production implementation using the official Google Play In-App Review API.
 */
class GooglePlayReviewManager(
    private val context: Context,
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
) : PlayReviewManager {

    companion object {
        private const val TAG = "GooglePlayReviewMgr"
    }

    override fun launchReviewFlow(activity: Activity, onComplete: (Boolean) -> Unit) {
        try {
            Log.d(TAG, "Requesting In-App Review flow from Google Play")
            val requestTask = reviewManager.requestReviewFlow()
            requestTask.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    Log.d(TAG, "ReviewInfo obtained, launching native Google Play review flow")
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener { flowTask ->
                        // Flow completion does NOT tell us if user submitted a review;
                        // Google intentionally obscures this for user privacy.
                        Log.d(TAG, "Google Play In-App Review flow finished: isSuccessful=${flowTask.isSuccessful}")
                        onComplete(flowTask.isSuccessful)
                    }
                } else {
                    Log.w(TAG, "Google Play requestReviewFlow failed: ${task.exception?.message}")
                    onComplete(false)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error launching Google Play Review", e)
            onComplete(false)
        }
    }
}

/**
 * Fake implementation for unit tests and local UI preview.
 */
class FakePlayReviewManager(
    var shouldSucceed: Boolean = true
) : PlayReviewManager {
    var launchCount: Int = 0
        private set

    override fun launchReviewFlow(activity: Activity, onComplete: (Boolean) -> Unit) {
        launchCount++
        onComplete(shouldSucceed)
    }

    fun reset() {
        launchCount = 0
        shouldSucceed = true
    }
}
