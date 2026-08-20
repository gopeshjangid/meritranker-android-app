package com.example.meritrankerstudent.ui.practice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.model.*
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DefaultAuthRepository
import com.example.meritrankerstudent.data.repository.DefaultExamProfileRepository
import com.example.meritrankerstudent.data.repository.DefaultPracticeRepository
import com.example.meritrankerstudent.data.repository.DefaultUserProfileRepository
import com.example.meritrankerstudent.data.repository.ExamProfileRepository
import com.example.meritrankerstudent.data.repository.PracticeRepository
import com.example.meritrankerstudent.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PracticeUiState(
    val isLoading: Boolean = true,
    val activities: List<PracticeActivitySummary> = emptyList(),
    val progress: ProgressSummary? = null,
    val quizzes: List<Quiz> = emptyList(),
    val mockTests: List<MockTest> = emptyList(),
    val pyqs: List<Pyq> = emptyList(),
    val wrongQuestions: List<Question> = emptyList(),
    val recommendedText: String = "",
    val selectedExamProfile: ExamProfile? = null,
    val availableExamProfiles: List<ExamProfile> = emptyList(),
    val selectedExam: String = "",
    val availableExams: List<String> = emptyList(),
    val examProfileId: String? = null,
    val selectedLanguage: String = "en",
    val error: String? = null
)

class PracticeViewModel(
    private val repository: PracticeRepository = DefaultPracticeRepository(),
    private val examProfileRepository: ExamProfileRepository = DefaultExamProfileRepository(),
    private val userProfileRepository: UserProfileRepository = DefaultUserProfileRepository(),
    private val authRepository: AuthRepository = DefaultAuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    init {
        observeExamProfiles()
        observeUserProfile()
        loadData()
    }

    private fun observeExamProfiles() {
        viewModelScope.launch {
            examProfileRepository.observeAllActiveExamProfiles().collect { activeProfiles ->
                val examNames = activeProfiles.map { it.examName }.distinct()
                _uiState.update { current ->
                    val resolvedProfile = activeProfiles.firstOrNull { it.examProfileId == current.examProfileId }
                        ?: activeProfiles.firstOrNull { it.examName.equals(current.selectedExam, ignoreCase = true) }
                        ?: activeProfiles.firstOrNull()

                    current.copy(
                        availableExamProfiles = activeProfiles,
                        availableExams = examNames,
                        selectedExamProfile = resolvedProfile,
                        selectedExam = resolvedProfile?.examName ?: current.selectedExam,
                        examProfileId = resolvedProfile?.examProfileId ?: current.examProfileId
                    )
                }
            }
        }
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            userProfileRepository.observeUserProfile(userId).collect { liveProfile ->
                if (liveProfile != null) {
                    val lang = if (liveProfile.language?.startsWith("hi", ignoreCase = true) == true) "hi" else "en"
                    _uiState.update { current ->
                        val matchedProfile = current.availableExamProfiles.firstOrNull { it.examProfileId == liveProfile.examProfileId }
                            ?: current.availableExamProfiles.firstOrNull { it.examName.equals(liveProfile.preparing, ignoreCase = true) }
                            ?: current.selectedExamProfile

                        current.copy(
                            selectedLanguage = lang,
                            selectedExamProfile = matchedProfile,
                            selectedExam = matchedProfile?.examName ?: liveProfile.preparing ?: current.selectedExam,
                            examProfileId = matchedProfile?.examProfileId ?: liveProfile.examProfileId ?: current.examProfileId
                        )
                    }
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                combine(
                    repository.getProgressSummary(),
                    repository.getQuizzes(),
                    repository.getMockTests(),
                    repository.getPyqs(),
                    repository.getWrongQuestions(),
                    repository.getRecommendedNext()
                ) { array ->
                    val progress = array[0] as ProgressSummary
                    val quizzes = array[1] as List<Quiz>
                    val mocks = array[2] as List<MockTest>
                    val pyqs = array[3] as List<Pyq>
                    val wrong = array[4] as List<Question>
                    val recommended = array[5] as String

                    _uiState.value.copy(
                        isLoading = false,
                        progress = progress,
                        quizzes = quizzes,
                        mockTests = mocks,
                        pyqs = pyqs,
                        wrongQuestions = wrong,
                        recommendedText = recommended,
                        error = null
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load practice activities") }
            }
        }
    }

    fun selectExamProfile(profile: ExamProfile) {
        _uiState.update {
            it.copy(
                selectedExamProfile = profile,
                selectedExam = profile.examName,
                examProfileId = profile.examProfileId
            )
        }
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            try {
                val current = userProfileRepository.fetchUserProfile(userId)
                userProfileRepository.updateUserProfile(
                    userId = userId,
                    name = current?.name ?: "Student",
                    preparing = profile.examName,
                    examProfileId = profile.examProfileId,
                    examStage = profile.stage,
                    additionalExams = current?.additionalExams ?: emptyList(),
                    dateOfBirth = current?.dateOfBirth ?: "2000-08-15",
                    language = current?.language ?: "ENGLISH"
                )
            } catch (e: Exception) {
                Log.w("PracticeViewModel", "Failed to sync selected exam profile: ${e.message}")
            }
        }
        loadData()
    }

    fun selectExam(examName: String) {
        val matchedProfile = _uiState.value.availableExamProfiles.firstOrNull { it.examName.equals(examName, ignoreCase = true) }
        if (matchedProfile != null) {
            selectExamProfile(matchedProfile)
        } else {
            _uiState.update { it.copy(selectedExam = examName) }
            loadData()
        }
    }
}
