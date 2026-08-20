package com.example.meritrankerstudent.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.model.GetStudentPerformanceView
import com.example.meritrankerstudent.data.model.StudentPerformanceInsight
import com.example.meritrankerstudent.data.model.StudentPerformanceItem
import com.example.meritrankerstudent.data.repository.DefaultStudentPerformanceRepository
import com.example.meritrankerstudent.data.repository.StudentPerformanceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SubjectProgressUiState {
    data object Loading : SubjectProgressUiState
    data class Content(
        val subjectItem: StudentPerformanceItem?,
        val insights: List<StudentPerformanceInsight>,
        val selectedView: GetStudentPerformanceView,
        val isUpdatingLatestPerformance: Boolean,
        val isRefreshing: Boolean
    ) : SubjectProgressUiState
    data class Error(val message: String) : SubjectProgressUiState
}

class SubjectProgressViewModel(
    private val repository: StudentPerformanceRepository = DefaultStudentPerformanceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<SubjectProgressUiState>(SubjectProgressUiState.Loading)
    val uiState: StateFlow<SubjectProgressUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var currentSubjectId: String? = null

    fun loadSubjectPerformance(
        examProfileId: String,
        subjectId: String,
        view: GetStudentPerformanceView,
        isRefresh: Boolean = false
    ) {
        // Guard against race conditions when switching subjects quickly
        currentSubjectId = subjectId
        activeJob?.cancel()

        val currentState = _uiState.value
        if (isRefresh && currentState is SubjectProgressUiState.Content) {
            _uiState.value = currentState.copy(isRefreshing = true)
        } else if (!isRefresh) {
            _uiState.value = SubjectProgressUiState.Loading
        }

        activeJob = viewModelScope.launch {
            val result = repository.getPerformance(
                examProfileId = examProfileId,
                view = view,
                subjectId = subjectId
            )

            // Ensure response belongs to the most recently requested subject
            if (currentSubjectId != subjectId) return@launch

            result.fold(
                onSuccess = { response ->
                    val subjectItem = response.items.firstOrNull { it.subjectId == subjectId }
                        ?: response.items.firstOrNull()
                    _uiState.value = SubjectProgressUiState.Content(
                        subjectItem = subjectItem,
                        insights = response.insights,
                        selectedView = view,
                        isUpdatingLatestPerformance = response.isUpdatingLatestPerformance,
                        isRefreshing = false
                    )
                },
                onFailure = { error ->
                    if (currentState is SubjectProgressUiState.Content) {
                        _uiState.value = currentState.copy(isRefreshing = false)
                    } else {
                        _uiState.value = SubjectProgressUiState.Error(
                            error.localizedMessage ?: "Failed to load subject performance."
                        )
                    }
                }
            )
        }
    }
}
