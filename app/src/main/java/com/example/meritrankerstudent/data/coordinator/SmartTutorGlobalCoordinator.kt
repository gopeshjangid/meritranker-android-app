package com.example.meritrankerstudent.data.coordinator

import android.content.Context
import android.util.Log
import com.example.meritrankerstudent.observability.AppObservability
import com.example.meritrankerstudent.observability.TelemetryEvent
import com.example.meritrankerstudent.util.notification.PracticeNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class NormalTurnTask(
    val conversationId: String,
    val turnId: String,
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * Authoritative Global Coordinator for ALL Smart Tutor Asynchronous Activities.
 *
 * Combines:
 * 1. Normal Smart Tutor Doubt Turns (Submitting, Thinking, Streaming)
 * 2. Practice & Mock Test Generation (Accepted, Generating, Finalizing)
 *
 * Enforces the Single Source of Truth invariant:
 * smartTutorBusy = anyNormalTutorRequestIsActive OR anyTrackedAssessmentGenerationIsActive
 */
class SmartTutorGlobalCoordinator(
    private val context: Context? = null,
    private val practiceCoordinator: PracticeGenerationCoordinator? = context?.let { PracticeGenerationCoordinator.getInstance(it) },
    private val notificationManager: PracticeNotificationManager? = context?.let { PracticeNotificationManager.getInstance(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    companion object {
        private const val TAG = "SmartTutorGlobalCoord"

        @Volatile
        private var instance: SmartTutorGlobalCoordinator? = null

        fun getInstance(context: Context): SmartTutorGlobalCoordinator {
            return instance ?: synchronized(this) {
                instance ?: SmartTutorGlobalCoordinator(
                    context = context.applicationContext,
                    practiceCoordinator = PracticeGenerationCoordinator.getInstance(context.applicationContext),
                    notificationManager = PracticeNotificationManager.getInstance(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val _activeNormalTurns = MutableStateFlow<Map<String, NormalTurnTask>>(emptyMap())
    val activeNormalTurns: StateFlow<Map<String, NormalTurnTask>> = _activeNormalTurns.asStateFlow()

    private val _isGlobalBusy = MutableStateFlow(false)
    val isGlobalBusy: StateFlow<Boolean> = _isGlobalBusy.asStateFlow()

    private val _totalActiveCount = MutableStateFlow(0)
    val totalActiveCount: StateFlow<Int> = _totalActiveCount.asStateFlow()

    // Visibility & screen tracking for contextual notification suppression
    @Volatile
    private var currentTabName: String = "DOUBT"
    @Volatile
    private var currentActiveConversationId: String? = null
    @Volatile
    private var isAppInForeground: Boolean = true

    init {
        // Combine normal turns count and practice coordinator active count
        scope.launch {
            val practiceFlow = practiceCoordinator?.activeCount ?: flowOf(0)
            combine(
                _activeNormalTurns,
                practiceFlow
            ) { normalTurns, practiceCount ->
                val normalCount = normalTurns.size
                val total = normalCount + practiceCount
                val busy = total > 0
                Pair(busy, total)
            }.collect { (busy, total) ->
                val previousBusy = _isGlobalBusy.value
                _isGlobalBusy.value = busy
                _totalActiveCount.value = total

                if (!previousBusy && busy) {
                    Log.d(TAG, "SMART_TUTOR_GLOBAL_ACTIVITY_STARTED totalCount=$total")
                    AppObservability.analytics.logEvent(
                        TelemetryEvent.SmartTutorGlobalActivityStarted(
                            activityType = if (_activeNormalTurns.value.isNotEmpty()) "NORMAL_DOUBT" else "PRACTICE_GENERATION"
                        )
                    )
                } else if (previousBusy && !busy) {
                    Log.d(TAG, "SMART_TUTOR_GLOBAL_ACTIVITY_STOPPED")
                    AppObservability.analytics.logEvent(
                        TelemetryEvent.SmartTutorGlobalActivityStopped(finalActivityCount = 0)
                    )
                }
            }
        }
    }

    fun updateScreenVisibility(tabName: String, conversationId: String? = null) {
        currentTabName = tabName
        if (conversationId != null) {
            currentActiveConversationId = conversationId
        }
        Log.d(TAG, "SCREEN_VISIBILITY_UPDATED tab=$tabName conversationId=$currentActiveConversationId foreground=$isAppInForeground")
    }

    fun setAppForeground(isForeground: Boolean) {
        isAppInForeground = isForeground
        Log.d(TAG, "APP_FOREGROUND_CHANGED isForeground=$isForeground")
    }

    fun isConversationVisible(conversationId: String?): Boolean {
        if (!isAppInForeground) return false
        if (currentTabName != "DOUBT") return false
        if (conversationId.isNullOrBlank()) return true
        return currentActiveConversationId == conversationId
    }

    fun markNormalTurnStarted(conversationId: String, turnId: String) {
        if (turnId.isBlank()) return
        val task = NormalTurnTask(conversationId = conversationId, turnId = turnId)
        _activeNormalTurns.update { it + (turnId to task) }
        Log.d(TAG, "NORMAL_TURN_STARTED turnId=$turnId conversationId=$conversationId")
    }

    fun markNormalTurnCompleted(conversationId: String, turnId: String, isSuccess: Boolean) {
        _activeNormalTurns.update { it - turnId }
        Log.d(TAG, "NORMAL_TURN_COMPLETED turnId=$turnId isSuccess=$isSuccess")

        if (isSuccess) {
            val isVisible = isConversationVisible(conversationId)
            if (!isVisible) {
                notificationManager?.notifyDoubtAnswerReady(conversationId, turnId)
            } else {
                Log.d(TAG, "DOUBT_NOTIFICATION_SUPPRESSED reason=VISIBLE_IN_TUTOR conversationId=$conversationId")
                AppObservability.analytics.logEvent(
                    TelemetryEvent.DoubtReadyNotificationSuppressed("VISIBLE_IN_TUTOR")
                )
            }
        }
    }

    fun markNormalTurnCancelled(turnId: String) {
        _activeNormalTurns.update { it - turnId }
        Log.d(TAG, "NORMAL_TURN_CANCELLED turnId=$turnId")
    }
}
