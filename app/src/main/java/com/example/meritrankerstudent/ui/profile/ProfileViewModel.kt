package com.example.meritrankerstudent.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.UserProfile
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DefaultAuthRepository
import com.example.meritrankerstudent.data.repository.DefaultExamProfileRepository
import com.example.meritrankerstudent.data.repository.DefaultUserProfileRepository
import com.example.meritrankerstudent.data.repository.ExamProfileRepository
import com.example.meritrankerstudent.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(
        val profile: UserProfile,
        val availableExamProfiles: List<ExamProfile> = emptyList(),
        val availableExams: List<String> = emptyList(),
        val isEditMode: Boolean = false,
        val draftName: String = "",
        val draftPrimaryExam: String = "",
        val draftExamProfileId: String? = null,
        val draftStage: String? = null,
        val draftAdditionalExams: List<String> = emptyList(),
        val draftDateOfBirth: String = "2000-08-15",
        val draftLanguage: String = "english",
        val isSaving: Boolean = false,
        val saveSuccess: Boolean = false,
        val nameError: String? = null,
        val dobError: String? = null,
        val examError: String? = null,
        val generalError: String? = null
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

class ProfileViewModel(
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val userProfileRepository: UserProfileRepository = DefaultUserProfileRepository(),
    private val examProfileRepository: ExamProfileRepository = DefaultExamProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                _uiState.value = ProfileUiState.Error("Unable to identify authenticated user")
                return@launch
            }

            val cognitoEmail = authRepository.fetchUserEmail()
            if (cognitoEmail.isNullOrBlank()) {
                Log.d("MeritRankerProfile", "Cognito email attribute is missing or unverified for userId=$userId")
            }

            try {
                val appSyncProfile = userProfileRepository.fetchUserProfile(userId)
                val resolvedEmail = cognitoEmail ?: "Email unavailable"

                val activeProfiles = try {
                    examProfileRepository.listAllActiveExamProfiles()
                } catch (e: Exception) {
                    emptyList()
                }
                val availableExams: List<String> = activeProfiles.map { it.examName }.distinct()

                val resolvedProfile = (appSyncProfile ?: UserProfile(
                    userId = userId,
                    email = resolvedEmail,
                    name = "MeritRanker Student",
                    preparing = availableExams.firstOrNull() ?: "",
                    examProfileId = activeProfiles.firstOrNull()?.examProfileId,
                    examStage = activeProfiles.firstOrNull()?.stage,
                    additionalExams = emptyList(),
                    dateOfBirth = "2000-08-15",
                    language = "english",
                    profileCompleted = true,
                    role = "STUDENT"
                )).copy(email = resolvedEmail)

                val matchedExamProfile = activeProfiles.firstOrNull { it.examProfileId == resolvedProfile.examProfileId }
                    ?: activeProfiles.firstOrNull { it.examName.equals(resolvedProfile.preparing, ignoreCase = true) }
                    ?: activeProfiles.firstOrNull()

                _uiState.value = ProfileUiState.Success(
                    profile = resolvedProfile,
                    availableExamProfiles = activeProfiles,
                    availableExams = availableExams,
                    draftName = resolvedProfile.name ?: "",
                    draftPrimaryExam = matchedExamProfile?.examName ?: resolvedProfile.preparing ?: availableExams.firstOrNull() ?: "",
                    draftExamProfileId = matchedExamProfile?.examProfileId ?: resolvedProfile.examProfileId,
                    draftStage = matchedExamProfile?.stage ?: resolvedProfile.examStage,
                    draftAdditionalExams = resolvedProfile.additionalExams,
                    draftDateOfBirth = resolvedProfile.dateOfBirth ?: "2000-08-15",
                    draftLanguage = resolvedProfile.language ?: "english"
                )
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Failed to load profile", e)
                _uiState.value = ProfileUiState.Error("Error loading profile: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    fun enterEditMode() {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            val matchedProfile = state.availableExamProfiles.firstOrNull { it.examProfileId == state.profile.examProfileId }
                ?: state.availableExamProfiles.firstOrNull { it.examName.equals(state.profile.preparing, ignoreCase = true) }
                ?: state.availableExamProfiles.firstOrNull()

            _uiState.value = state.copy(
                isEditMode = true,
                draftName = state.profile.name ?: "",
                draftPrimaryExam = matchedProfile?.examName ?: state.profile.preparing ?: state.availableExams.firstOrNull() ?: "",
                draftExamProfileId = matchedProfile?.examProfileId ?: state.profile.examProfileId,
                draftStage = matchedProfile?.stage ?: state.profile.examStage,
                draftAdditionalExams = state.profile.additionalExams,
                draftDateOfBirth = state.profile.dateOfBirth ?: "2000-08-15",
                draftLanguage = state.profile.language ?: "english",
                nameError = null,
                dobError = null,
                examError = null,
                generalError = null,
                saveSuccess = false
            )
        }
    }

    fun cancelEditMode() {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            _uiState.value = state.copy(
                isEditMode = false,
                draftName = state.profile.name ?: "",
                draftPrimaryExam = state.profile.preparing ?: state.availableExams.firstOrNull() ?: "",
                draftExamProfileId = state.profile.examProfileId,
                draftStage = state.profile.examStage,
                draftAdditionalExams = state.profile.additionalExams,
                draftDateOfBirth = state.profile.dateOfBirth ?: "2000-08-15",
                draftLanguage = state.profile.language ?: "english",
                nameError = null,
                dobError = null,
                examError = null,
                generalError = null
            )
        }
    }

    fun onNameChanged(newName: String) {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            _uiState.value = state.copy(
                draftName = newName,
                nameError = if (newName.trim().isEmpty()) "Name cannot be empty" else null
            )
        }
    }

    fun onDraftNameChanged(newName: String) = onNameChanged(newName)

    fun onPrimaryExamProfileChanged(profile: ExamProfile) {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            val updatedAdditional = state.draftAdditionalExams.filter { it != profile.examName }
            _uiState.value = state.copy(
                draftPrimaryExam = profile.examName,
                draftExamProfileId = profile.examProfileId,
                draftStage = profile.stage,
                draftAdditionalExams = updatedAdditional,
                examError = null
            )
        }
    }

    fun onPrimaryExamChanged(exam: String) {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            val matched = state.availableExamProfiles.firstOrNull { it.examName.equals(exam, ignoreCase = true) }
            if (matched != null) {
                onPrimaryExamProfileChanged(matched)
            } else {
                val updatedAdditional = state.draftAdditionalExams.filter { it != exam }
                _uiState.value = state.copy(
                    draftPrimaryExam = exam,
                    draftAdditionalExams = updatedAdditional,
                    examError = null
                )
            }
        }
    }

    fun onToggleAdditionalExam(exam: String) {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            if (exam == state.draftPrimaryExam) return // Cannot add primary as additional
            val currentList = state.draftAdditionalExams.toMutableList()
            if (currentList.contains(exam)) {
                currentList.remove(exam)
            } else {
                currentList.add(exam)
            }
            _uiState.value = state.copy(draftAdditionalExams = currentList)
        }
    }

    fun onDateOfBirthChanged(newDob: String) {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            val isValidFormat = newDob.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))
            _uiState.value = state.copy(
                draftDateOfBirth = newDob,
                dobError = if (!isValidFormat && newDob.isNotBlank()) "Format must be YYYY-MM-DD" else null
            )
        }
    }

    fun onLanguageChanged(newLanguage: String) {
        val state = _uiState.value
        if (state is ProfileUiState.Success) {
            _uiState.value = state.copy(draftLanguage = newLanguage)
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state !is ProfileUiState.Success) return

        if (state.draftName.trim().isEmpty()) {
            _uiState.value = state.copy(nameError = "Name cannot be empty")
            return
        }

        if (state.draftPrimaryExam.isBlank()) {
            _uiState.value = state.copy(examError = "Primary exam is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                isSaving = true,
                nameError = null,
                dobError = null,
                examError = null,
                generalError = null,
                saveSuccess = false
            )
            val userId = state.profile.userId
            try {
                val updatedProfile = userProfileRepository.updateUserProfile(
                    userId = userId,
                    name = state.draftName.trim(),
                    preparing = state.draftPrimaryExam,
                    examProfileId = state.draftExamProfileId,
                    examStage = state.draftStage,
                    additionalExams = state.draftAdditionalExams.distinct(),
                    dateOfBirth = state.draftDateOfBirth,
                    language = state.draftLanguage
                )
                com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                    com.example.meritrankerstudent.observability.TelemetryEvent.ProfileUpdated(
                        examProfileId = updatedProfile.examProfileId,
                        language = updatedProfile.language
                    )
                )
                com.example.meritrankerstudent.observability.AppObservability.analytics.setUserProperty("exam_profile_id", updatedProfile.examProfileId)
                com.example.meritrankerstudent.observability.AppObservability.analytics.setUserProperty("study_language", updatedProfile.language)

                _uiState.value = ProfileUiState.Success(
                    profile = updatedProfile,
                    availableExamProfiles = state.availableExamProfiles,
                    availableExams = state.availableExams,
                    isEditMode = false,
                    draftName = updatedProfile.name ?: "",
                    draftPrimaryExam = updatedProfile.preparing ?: state.availableExams.firstOrNull() ?: "",
                    draftExamProfileId = updatedProfile.examProfileId,
                    draftStage = updatedProfile.examStage,
                    draftAdditionalExams = updatedProfile.additionalExams,
                    draftDateOfBirth = updatedProfile.dateOfBirth ?: "2000-08-15",
                    draftLanguage = updatedProfile.language ?: "english",
                    isSaving = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Failed to update profile", e)
                val category = com.example.meritrankerstudent.observability.BackendErrorClassifier.classify(e)
                com.example.meritrankerstudent.observability.AppObservability.crashReporter.recordBackendFailure(
                    operation = com.example.meritrankerstudent.observability.OperationName.PROFILE_UPDATE.key,
                    category = category,
                    throwable = e
                )
                _uiState.value = state.copy(
                    isSaving = false,
                    generalError = "Failed to save: ${e.localizedMessage ?: "Unknown network error"}"
                )
            }
        }
    }

    fun clearSuccessMessage() {
        val currentState = _uiState.value
        if (currentState is ProfileUiState.Success) {
            _uiState.value = currentState.copy(saveSuccess = false)
        }
    }
}
