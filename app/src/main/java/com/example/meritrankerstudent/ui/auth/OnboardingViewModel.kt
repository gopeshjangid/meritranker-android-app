package com.example.meritrankerstudent.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.repository.DefaultExamProfileRepository
import com.example.meritrankerstudent.data.repository.ExamProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StageOption(
    val examProfileId: String,
    val examId: String,
    val stage: String,
    val examName: String,
    val description: String? = null
)

data class ExamGoal(
    val examId: String,
    val examName: String,
    val stages: List<StageOption>
)

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState
    data class Ready(
        val goals: List<ExamGoal>,
        val selectedGoal: ExamGoal?,
        val selectedStageOption: StageOption?,
        val selectedLanguage: String = "ENGLISH",
        val isSaving: Boolean = false,
        val saveError: String? = null
    ) : OnboardingUiState
    data object ZeroResults : OnboardingUiState
    data class Error(val message: String) : OnboardingUiState
}

class OnboardingViewModel(
    private val examProfileRepository: ExamProfileRepository = DefaultExamProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var loadCatalogJob: Job? = null
    private var selectionVersion: Long = 0L

    init {
        loadExamCatalog()
    }

    fun loadExamCatalog() {
        _uiState.value = OnboardingUiState.Loading
        loadCatalogJob?.cancel()

        loadCatalogJob = viewModelScope.launch {
            try {
                val activeProfiles = examProfileRepository.listAllActiveExamProfiles()

                if (activeProfiles.isEmpty()) {
                    _uiState.value = OnboardingUiState.ZeroResults
                    return@launch
                }

                // Group by examId to build authoritative ExamGoals
                val groupedByExam = activeProfiles.groupBy { it.examId }
                val goals = groupedByExam.map { (examId, profiles) ->
                    val examName = profiles.firstOrNull()?.examName ?: examId
                    val stageOptions = profiles.map { profile ->
                        StageOption(
                            examProfileId = profile.examProfileId,
                            examId = profile.examId,
                            stage = profile.stage,
                            examName = profile.examName,
                            description = profile.description
                        )
                    }
                    ExamGoal(
                        examId = examId,
                        examName = examName,
                        stages = stageOptions
                    )
                }

                if (goals.isEmpty()) {
                    _uiState.value = OnboardingUiState.ZeroResults
                    return@launch
                }

                val initialGoal = goals.first()
                val initialStage = if (initialGoal.stages.isNotEmpty()) {
                    initialGoal.stages.first()
                } else {
                    null
                }

                _uiState.value = OnboardingUiState.Ready(
                    goals = goals,
                    selectedGoal = initialGoal,
                    selectedStageOption = initialStage,
                    selectedLanguage = "ENGLISH",
                    isSaving = false,
                    saveError = null
                )
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Failed to load active exam catalog", e)
                _uiState.value = OnboardingUiState.Error(
                    "We couldn't load exam options. Please check your connection and try again."
                )
            }
        }
    }

    fun selectExam(goal: ExamGoal) {
        val currentVersion = ++selectionVersion
        val currentState = _uiState.value
        if (currentState !is OnboardingUiState.Ready) return

        // Section 9: Exam change MUST immediately invalidate previous stage and profileId
        val initialStage = if (goal.stages.isNotEmpty()) {
            goal.stages.first()
        } else {
            null
        }

        if (selectionVersion == currentVersion) {
            _uiState.value = currentState.copy(
                selectedGoal = goal,
                selectedStageOption = initialStage,
                saveError = null
            )
        }
    }

    fun selectStageOption(option: StageOption) {
        val currentState = _uiState.value
        if (currentState is OnboardingUiState.Ready) {
            _uiState.value = currentState.copy(
                selectedStageOption = option,
                saveError = null
            )
        }
    }

    fun selectLanguage(language: String) {
        val currentState = _uiState.value
        if (currentState is OnboardingUiState.Ready) {
            _uiState.value = currentState.copy(
                selectedLanguage = language,
                saveError = null
            )
        }
    }

    fun setSaving(isSaving: Boolean, error: String? = null) {
        val currentState = _uiState.value
        if (currentState is OnboardingUiState.Ready) {
            _uiState.value = currentState.copy(
                isSaving = isSaving,
                saveError = error
            )
        }
    }

    fun retry() {
        loadExamCatalog()
    }
}
