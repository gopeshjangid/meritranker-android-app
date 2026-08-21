package com.example.meritrankerstudent.data.coordinator

import android.content.Context
import android.util.Log
import com.example.meritrankerstudent.data.local.AppDatabase
import com.example.meritrankerstudent.data.local.CachedPracticeActivityEntity
import com.example.meritrankerstudent.data.local.EntityMappers
import com.example.meritrankerstudent.data.model.PracticeActivitySummary
import com.example.meritrankerstudent.data.model.PracticeGenerationProgress
import com.example.meritrankerstudent.data.model.PracticeGenerationStatus
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DefaultAuthRepository
import com.example.meritrankerstudent.data.repository.DefaultPracticeRepository
import com.example.meritrankerstudent.data.repository.PracticeRepository
import com.example.meritrankerstudent.util.notification.PracticeNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class GenerationTaskState(
    val testId: String,
    val conversationId: String? = null,
    val turnId: String? = null,
    val title: String? = null,
    val activityType: String? = null,
    val questionCount: Int = 0,
    val readyCount: Int = 0,
    val progressPercent: Int = 0,
    val status: PracticeGenerationStatus = PracticeGenerationStatus.GENERATING,
    val playable: Boolean = false,
    val errorCode: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

class PracticeGenerationCoordinator(
    private val context: Context? = null,
    private val practiceRepository: PracticeRepository = DefaultPracticeRepository(),
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val database: AppDatabase? = context?.let { AppDatabase.getInstance(it) },
    private val notificationManager: PracticeNotificationManager? = context?.let { PracticeNotificationManager.getInstance(it) },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "PracticeGenCoord"

        @Volatile
        private var instance: PracticeGenerationCoordinator? = null

        fun getInstance(context: Context): PracticeGenerationCoordinator {
            return instance ?: synchronized(this) {
                instance ?: PracticeGenerationCoordinator(
                    context = context.applicationContext,
                    practiceRepository = DefaultPracticeRepository(),
                    authRepository = DefaultAuthRepository(),
                    database = AppDatabase.getInstance(context.applicationContext),
                    notificationManager = PracticeNotificationManager.getInstance(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val _tasks = MutableStateFlow<Map<String, GenerationTaskState>>(emptyMap())
    val tasks: StateFlow<Map<String, GenerationTaskState>> = _tasks.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private val _latestReadyTask = MutableStateFlow<GenerationTaskState?>(null)
    val latestReadyTask: StateFlow<GenerationTaskState?> = _latestReadyTask.asStateFlow()

    private val activeSubscriptions = ConcurrentHashMap<String, Job>()
    private val activeReconciliations = ConcurrentHashMap<String, Job>()

    private fun normalizeStatus(
        statusStr: String?,
        playable: Boolean,
        readyCount: Int,
        totalQuestions: Int
    ): PracticeGenerationStatus {
        val normalized = statusStr?.trim()?.uppercase() ?: ""
        return when {
            normalized in setOf("READY", "COMPLETED", "PRACTICE_GENERATION_READY", "GENERATION_COMPLETED") ||
                playable ||
                (totalQuestions > 0 && readyCount >= totalQuestions) -> PracticeGenerationStatus.READY

            normalized in setOf("FAILED", "CANCELLED", "EXPIRED", "INTERRUPTED", "STALLED", "PRACTICE_GENERATION_STALLED") -> PracticeGenerationStatus.FAILED

            else -> PracticeGenerationStatus.GENERATING
        }
    }

    fun trackGeneration(
        testId: String,
        conversationId: String? = null,
        turnId: String? = null,
        initialTitle: String? = null,
        initialType: String? = null,
        questionCount: Int? = null
    ) {
        if (testId.isBlank()) return

        val existing = _tasks.value[testId]
        if (existing != null && (existing.status == PracticeGenerationStatus.READY || existing.status == PracticeGenerationStatus.FAILED)) {
            Log.d(TAG, "trackGeneration: testId=$testId is already terminal (${existing.status})")
            return
        }

        val initialTask = existing?.copy(
            conversationId = conversationId ?: existing.conversationId,
            turnId = turnId ?: existing.turnId
        ) ?: GenerationTaskState(
            testId = testId,
            conversationId = conversationId,
            turnId = turnId,
            title = initialTitle ?: "Practice Quiz",
            activityType = initialType ?: "QUIZ",
            questionCount = questionCount ?: 0,
            status = PracticeGenerationStatus.GENERATING,
            playable = false,
            updatedAt = System.currentTimeMillis()
        )

        updateTask(initialTask)
        startTrackingPipeline(testId)
    }

    private fun startTrackingPipeline(testId: String) {
        if (activeSubscriptions.containsKey(testId)) {
            return
        }

        val job = scope.launch {
            Log.d(TAG, "Starting tracking pipeline for testId=$testId")

            // 1. Immediate Authoritative Reconciliation (closes subscription-first race)
            reconcileAuthoritativeSummary(testId)

            // 2. Launch Subscription Listener
            val subJob = launch {
                try {
                    practiceRepository.observePracticeGeneration(testId).collect { progress ->
                        handleProgressUpdate(progress)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Subscription failed for testId=$testId: ${e.message}")
                }
            }

            // 3. Progressive Backoff Reconciler (up to 180s total window with activity extension)
            var totalElapsed = 0L
            val maxTimeoutMs = 180_000L // 3 minutes total window for multi-question AI generation
            var currentIntervalMs = 2000L
            var lastProgressSeen = _tasks.value[testId]?.progressPercent ?: 0

            while (totalElapsed < maxTimeoutMs) {
                delay(currentIntervalMs)
                totalElapsed += currentIntervalMs

                val current = _tasks.value[testId]
                if (current == null || current.status == PracticeGenerationStatus.READY || current.status == PracticeGenerationStatus.FAILED) {
                    break
                }

                // If progress made forward movement, extend tolerance
                if (current.progressPercent > lastProgressSeen) {
                    lastProgressSeen = current.progressPercent
                    // Reset interval to responsive polling
                    currentIntervalMs = 3000L
                } else {
                    // Step up polling interval smoothly up to 12s
                    currentIntervalMs = (currentIntervalMs + 2000L).coerceAtMost(12000L)
                }

                reconcileAuthoritativeSummary(testId)
            }

            subJob.cancel()

            // Final check: If still generating after max timeout window, transition to FAILED
            val finalCheck = _tasks.value[testId]
            if (finalCheck != null && finalCheck.status == PracticeGenerationStatus.GENERATING) {
                Log.w(TAG, "Generation timed out for testId=$testId after ${totalElapsed / 1000}s. Transitioning to STALLED.")
                val stalledTask = finalCheck.copy(
                    status = PracticeGenerationStatus.FAILED,
                    errorCode = "PRACTICE_GENERATION_STALLED",
                    updatedAt = System.currentTimeMillis()
                )
                updateTask(stalledTask)
                checkTerminalStateAndNotify(stalledTask)
            }
        }

        activeSubscriptions[testId] = job
    }

    suspend fun reconcileAuthoritativeSummary(testId: String) {
        try {
            val summary = practiceRepository.getPracticeSummary(testId)
            applySummaryUpdate(testId, summary)
        } catch (e: Exception) {
            Log.w(TAG, "Reconcile failed for testId=$testId: ${e.message}")
        }
    }

    private fun applySummaryUpdate(testId: String, summary: PracticeActivitySummary) {
        val current = _tasks.value[testId]
        if (current != null && (current.status == PracticeGenerationStatus.READY || current.status == PracticeGenerationStatus.FAILED)) {
            // Monotonic protection: terminal states never regress
            return
        }

        val questionCount = if (summary.questionCount > 0) summary.questionCount else current?.questionCount ?: 0
        val readyCount = summary.readyCount ?: (if (summary.playable) questionCount else current?.readyCount ?: 0)
        val normalizedStatus = normalizeStatus(
            statusStr = summary.generationStatus,
            playable = summary.playable,
            readyCount = readyCount,
            totalQuestions = questionCount
        )
        val isPlayable = summary.playable || normalizedStatus == PracticeGenerationStatus.READY

        val updatedTask = GenerationTaskState(
            testId = testId,
            conversationId = current?.conversationId,
            turnId = current?.turnId,
            title = summary.title.ifEmpty { current?.title ?: "Practice Quiz" },
            activityType = summary.activityType.name,
            questionCount = questionCount,
            readyCount = readyCount,
            progressPercent = summary.progressPercent ?: (if (normalizedStatus == PracticeGenerationStatus.READY) 100 else current?.progressPercent ?: 0),
            status = normalizedStatus,
            playable = isPlayable,
            errorCode = summary.errorCode,
            updatedAt = System.currentTimeMillis()
        )

        updateTask(updatedTask)

        // Write through to Room cache
        database?.let { db ->
            scope.launch {
                try {
                    val userId = authRepository.getCurrentUserId() ?: "authenticated_student_user"
                    db.practiceDao().upsertActivity(EntityMappers.practiceActivityToEntity(summary, userId))
                } catch (_: Exception) {}
            }
        }

        checkTerminalStateAndNotify(updatedTask)
    }

    private fun handleProgressUpdate(progress: PracticeGenerationProgress) {
        val testId = progress.testId
        val current = _tasks.value[testId]

        if (current != null && (current.status == PracticeGenerationStatus.READY || current.status == PracticeGenerationStatus.FAILED)) {
            return
        }

        val questionCount = if (progress.totalQuestions > 0) progress.totalQuestions else current?.questionCount ?: 0
        val normalizedStatus = normalizeStatus(
            statusStr = progress.status.name,
            playable = progress.playable,
            readyCount = progress.readyCount,
            totalQuestions = questionCount
        )
        val isPlayable = progress.playable || normalizedStatus == PracticeGenerationStatus.READY

        val updatedTask = GenerationTaskState(
            testId = testId,
            conversationId = current?.conversationId,
            turnId = current?.turnId,
            title = current?.title ?: "Practice Quiz",
            activityType = current?.activityType ?: "QUIZ",
            questionCount = questionCount,
            readyCount = progress.readyCount,
            progressPercent = progress.progressPercent,
            status = normalizedStatus,
            playable = isPlayable,
            errorCode = progress.errorCode,
            updatedAt = System.currentTimeMillis()
        )

        updateTask(updatedTask)
        checkTerminalStateAndNotify(updatedTask)
    }

    private fun checkTerminalStateAndNotify(task: GenerationTaskState) {
        if (task.status == PracticeGenerationStatus.READY) {
            _latestReadyTask.value = task
            val isVisible = context?.let { SmartTutorGlobalCoordinator.getInstance(it).isConversationVisible(task.conversationId) } ?: false
            if (!isVisible) {
                notificationManager?.notifyPracticeReady(
                    testId = task.testId,
                    title = task.title,
                    activityType = task.activityType,
                    questionCount = task.questionCount
                )
            } else {
                Log.d(TAG, "PRACTICE_NOTIFICATION_SUPPRESSED reason=VISIBLE_IN_TUTOR testId=${task.testId}")
                com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                    com.example.meritrankerstudent.observability.TelemetryEvent.PracticeReadyNotificationSuppressed("VISIBLE_IN_TUTOR")
                )
            }
            cleanUpTaskSubscription(task.testId)
        } else if (task.status == PracticeGenerationStatus.FAILED || task.status == PracticeGenerationStatus.CANCELLED) {
            cleanUpTaskSubscription(task.testId)
        }
    }

    private fun cleanUpTaskSubscription(testId: String) {
        activeSubscriptions.remove(testId)?.cancel()
        activeReconciliations.remove(testId)?.cancel()
    }

    private fun updateTask(task: GenerationTaskState) {
        _tasks.update { map ->
            map + (task.testId to task)
        }
        _activeCount.value = _tasks.value.values.count { it.status == PracticeGenerationStatus.GENERATING }
    }

    fun restoreActiveGenerationsOnStartup(userId: String) {
        scope.launch {
            try {
                database?.let { db ->
                    val nonTerminal = db.practiceDao().getNonTerminalActivities(userId)
                    for (entity in nonTerminal) {
                        val state = GenerationTaskState(
                            testId = entity.activityId,
                            title = entity.title,
                            activityType = entity.activityType,
                            questionCount = entity.questionCount,
                            readyCount = entity.readyCount ?: 0,
                            progressPercent = entity.progressPercent ?: 0,
                            status = PracticeGenerationStatus.GENERATING,
                            playable = entity.playable,
                            errorCode = entity.errorCode,
                            updatedAt = entity.lastSyncedAt
                        )
                        updateTask(state)
                        startTrackingPipeline(entity.activityId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error restoring active generations from Room: ${e.message}")
            }
        }
    }

    fun dismissLatestReady() {
        _latestReadyTask.value = null
    }

    fun clearUserGenerations() {
        activeSubscriptions.values.forEach { it.cancel() }
        activeSubscriptions.clear()
        activeReconciliations.values.forEach { it.cancel() }
        activeReconciliations.clear()
        _tasks.value = emptyMap()
        _activeCount.value = 0
        _latestReadyTask.value = null
        notificationManager?.clearNotifications()
    }
}
