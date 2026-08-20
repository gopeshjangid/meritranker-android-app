package com.example.meritrankerstudent.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.model.*
import com.example.meritrankerstudent.data.repository.PracticeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val mode: String = "",
    val id: String = "",
    val summary: PracticeActivitySummary? = null,
    val attempt: PracticeAttemptPayload? = null,
    val questions: List<PracticeQuestionPayload> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptions: Map<String, String> = emptyMap(), // questionId -> selectedOptionText/Id
    val feedback: Map<String, PracticeFeedbackPayload> = emptyMap(), // questionId -> server feedback
    val lockedQuestions: Map<String, Boolean> = emptyMap(), // questionId -> isLocked
    val elapsedSeconds: Int = 0,
    val isChecking: Boolean = false,
    val isSaving: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val result: PracticeResultPayload? = null,
    val errorMessage: String? = null
)

class QuestionPlayerViewModel(
    private val repository: PracticeRepository,
    private val mode: String,
    private val id: String,
    startTimer: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState(mode = mode, id = id))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        loadAttempt()
        if (startTimer) {
            startTimer()
        }
    }

    fun loadAttempt() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val startPayload = repository.startOrResumeAttempt(id)
                val restoredAnswers = mutableMapOf<String, String>()
                val restoredLocked = mutableMapOf<String, Boolean>()

                startPayload.responses.forEach { resp ->
                    resp.selectedOption?.let { opt ->
                        restoredAnswers[resp.questionId] = opt
                    }
                    if (resp.isAnswerLocked) {
                        restoredLocked[resp.questionId] = true
                    }
                }

                val targetIndex = (startPayload.attempt.currentQuestionPosition - 1)
                    .coerceIn(0, (startPayload.questions.size - 1).coerceAtLeast(0))

                val isResumed = startPayload.attempt.currentQuestionPosition > 1 || restoredAnswers.isNotEmpty()
                val examId = startPayload.summary.exams.firstOrNull()
                if (isResumed) {
                    com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                        com.example.meritrankerstudent.observability.TelemetryEvent.PracticeResumed(
                            examProfileId = examId,
                            practiceType = mode
                        )
                    )
                } else {
                    com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                        com.example.meritrankerstudent.observability.TelemetryEvent.PracticeStarted(
                            examProfileId = examId,
                            practiceType = mode,
                            questionCountBucket = com.example.meritrankerstudent.observability.Buckets.questionCountBucket(startPayload.questions.size)
                        )
                    )
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = startPayload.summary,
                        attempt = startPayload.attempt,
                        questions = startPayload.questions,
                        currentIndex = targetIndex,
                        selectedOptions = restoredAnswers,
                        lockedQuestions = restoredLocked,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load practice questions. Please retry."
                    )
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    fun selectOption(questionId: String, optionText: String) {
        val state = _uiState.value
        if (state.lockedQuestions[questionId] == true || state.isChecking || state.isSubmitting) return

        _uiState.update {
            val updated = it.selectedOptions.toMutableMap()
            updated[questionId] = optionText
            it.copy(selectedOptions = updated)
        }
    }

    fun checkAnswer(questionId: String) {
        val state = _uiState.value
        val attemptId = state.attempt?.attemptId ?: return
        val selectedOption = state.selectedOptions[questionId] ?: return
        if (state.isChecking || state.lockedQuestions[questionId] == true) return

        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, errorMessage = null) }
            try {
                val feedbackResult = repository.checkAnswer(
                    attemptId = attemptId,
                    questionId = questionId,
                    selectedOption = selectedOption
                )

                _uiState.update { current ->
                    val updatedFeedback = current.feedback.toMutableMap()
                    updatedFeedback[questionId] = feedbackResult
                    val updatedLocked = current.lockedQuestions.toMutableMap()
                    updatedLocked[questionId] = true

                    current.copy(
                        isChecking = false,
                        feedback = updatedFeedback,
                        lockedQuestions = updatedLocked
                    )
                }
            } catch (e: Exception) {
                val isLocked = e.message == "ANSWER_LOCKED"
                _uiState.update { current ->
                    if (isLocked) {
                        val updatedLocked = current.lockedQuestions.toMutableMap()
                        updatedLocked[questionId] = true
                        current.copy(isChecking = false, lockedQuestions = updatedLocked)
                    } else {
                        current.copy(
                            isChecking = false,
                            errorMessage = "Couldn't verify answer. Please check your network and retry."
                        )
                    }
                }
            }
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex < state.questions.size - 1) {
            val currentQ = state.questions[state.currentIndex]
            val attemptId = state.attempt?.attemptId
            val selected = state.selectedOptions[currentQ.questionId]

            if (attemptId != null && selected != null && state.lockedQuestions[currentQ.questionId] != true) {
                viewModelScope.launch {
                    try {
                        repository.saveResponse(
                            attemptId = attemptId,
                            questionId = currentQ.questionId,
                            selectedOption = selected,
                            markedForReview = false,
                            nextQuestionPosition = state.currentIndex + 2,
                            currentQuestionPosition = state.currentIndex + 1
                        )
                    } catch (e: Exception) {
                        // Background sync best-effort; local state preserved
                    }
                }
            }

            _uiState.update { it.copy(currentIndex = it.currentIndex + 1) }
        }
    }

    fun previousQuestion() {
        val state = _uiState.value
        if (state.currentIndex > 0) {
            _uiState.update { it.copy(currentIndex = it.currentIndex - 1) }
        }
    }

    fun submitAttempt(onSuccess: (PracticeResultPayload) -> Unit) {
        val state = _uiState.value
        val attemptId = state.attempt?.attemptId ?: return
        val activityId = state.summary?.activityId ?: state.attempt.activityId
        if (state.isSubmitting || state.isSubmitted) return

        val answeredCount = state.selectedOptions.size
        val examId = state.summary?.exams?.firstOrNull()
        com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
            com.example.meritrankerstudent.observability.TelemetryEvent.PracticeSubmitted(
                examProfileId = examId,
                practiceType = mode,
                answeredCountBucket = com.example.meritrankerstudent.observability.Buckets.questionCountBucket(answeredCount)
            )
        )

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = repository.submitAttempt(attemptId, activityId)
                val duration = com.example.meritrankerstudent.observability.Buckets.latencyBucket(state.elapsedSeconds * 1000L)
                com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                    com.example.meritrankerstudent.observability.TelemetryEvent.PracticeCompleted(
                        examProfileId = examId,
                        practiceType = mode,
                        durationBucket = duration
                    )
                )

                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        isSubmitted = true,
                        result = result
                    )
                }
                onSuccess(result)
            } catch (e: Exception) {
                val category = com.example.meritrankerstudent.observability.BackendErrorClassifier.classify(e)
                com.example.meritrankerstudent.observability.AppObservability.crashReporter.recordBackendFailure(
                    operation = com.example.meritrankerstudent.observability.OperationName.SUBMIT_PRACTICE_ATTEMPT.key,
                    category = category,
                    throwable = e
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "Submission failed: ${e.message}. Please retry."
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        val state = _uiState.value
        if (!state.isSubmitted && state.questions.isNotEmpty()) {
            val percentage = (state.currentIndex * 100) / state.questions.size
            val examId = state.summary?.exams?.firstOrNull()
            com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                com.example.meritrankerstudent.observability.TelemetryEvent.PracticeAbandoned(
                    examProfileId = examId,
                    practiceType = mode,
                    progressBucket = com.example.meritrankerstudent.observability.Buckets.progressBucket(percentage)
                )
            )
        }
    }
}
