package com.example.meritrankerstudent.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.model.PracticeReviewQuestionPayload
import com.example.meritrankerstudent.data.repository.PracticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PracticeReviewUiState(
    val isLoading: Boolean = true,
    val questions: List<PracticeReviewQuestionPayload> = emptyList(),
    val errorMessage: String? = null
)

class PracticeReviewViewModel(
    private val repository: PracticeRepository,
    private val attemptId: String,
    private val activityId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeReviewUiState())
    val uiState: StateFlow<PracticeReviewUiState> = _uiState.asStateFlow()

    init {
        loadReview()
    }

    fun loadReview() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val reviewList = repository.getReview(attemptId, activityId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        questions = reviewList,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load review questions."
                    )
                }
            }
        }
    }
}
