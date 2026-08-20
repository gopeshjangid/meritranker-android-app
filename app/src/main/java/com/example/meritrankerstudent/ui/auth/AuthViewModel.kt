package com.example.meritrankerstudent.ui.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.auth.result.step.AuthSignInStep
import com.amplifyframework.auth.cognito.exceptions.service.UserCancelledException
import com.example.meritrankerstudent.MeritRankerApplication
import com.example.meritrankerstudent.data.local.LocalProfileStore
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Initialising : SessionState
    data class SignedOut(val error: String? = null, val isSigningIn: Boolean = false) : SessionState
    data object LoadingProfile : SessionState
    data class Ready(val profile: UserProfile, val examProfile: ExamProfile) : SessionState
    data class IncompleteProfile(val profile: UserProfile) : SessionState
    data class Error(val message: String) : SessionState
}

class AuthViewModel(
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val userProfileRepository: UserProfileRepository = DefaultUserProfileRepository(),
    private val examProfileRepository: ExamProfileRepository = DefaultExamProfileRepository(),
    private val localProfileStore: LocalProfileStore? = MeritRankerApplication.instance?.let { LocalProfileStore(it) }
) : ViewModel() {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Initialising)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            _sessionState.value = SessionState.Initialising
            Log.d("AuthViewModel", "checkSession starting, Amplify status=${MeritRankerApplication.initStatus.value}")
            val currentStatus = MeritRankerApplication.initStatus.value
            if (currentStatus !is MeritRankerApplication.Companion.InitStatus.Success) {
                val status = MeritRankerApplication.initStatus
                    .filter { it is MeritRankerApplication.Companion.InitStatus.Success || it is MeritRankerApplication.Companion.InitStatus.Failure }
                    .first()
                if (status is MeritRankerApplication.Companion.InitStatus.Failure) {
                    _sessionState.value = SessionState.Error("Amplify initialization failed: ${status.exception.localizedMessage}")
                    return@launch
                }
            }

            Log.d("AuthViewModel", "Checking authRepository.isUserSignedIn()...")
            val signedIn = authRepository.isUserSignedIn()
            Log.d("AuthViewModel", "authRepository.isUserSignedIn() = $signedIn")
            if (signedIn) {
                loadProfile()
            } else {
                _sessionState.value = SessionState.SignedOut()
            }
        }
    }

    private suspend fun loadProfile() {
        Log.d("AuthViewModel", "loadProfile() entry")
        val userId = authRepository.getCurrentUserId()
        Log.d("AuthViewModel", "loadProfile() userId=$userId")
        if (userId == null) {
            _sessionState.value = SessionState.SignedOut("Unable to resolve Cognito User Identity")
            return
        }

        // Fast path: Check local persistent storage for instant hydration
        val localProfile = localProfileStore?.getUserProfile(userId)
        if (localProfile != null) {
            val isLocalComplete = localProfile.profileCompleted == true ||
                    localProfile.onboardingStep?.equals("COMPLETED", ignoreCase = true) == true ||
                    localProfile.onboardingStep?.equals("DONE", ignoreCase = true) == true ||
                    localProfile.onboardingStep?.equals("COMPLETE", ignoreCase = true) == true ||
                    (!localProfile.name.isNullOrEmpty() && !localProfile.preparing.isNullOrEmpty())

            if (isLocalComplete) {
                val targetExamId = localProfile.preparing
                    ?: localProfile.examTags.firstOrNull()
                    ?: "CAT Management"
                val targetProfileId = localProfile.examProfileId ?: "${targetExamId}#${localProfile.examStage ?: "PRE"}"
                val localExamProfile = localProfileStore.getExamProfile(targetProfileId)
                if (localExamProfile != null && localExamProfile.active) {
                    Log.i("AuthViewModel", "Instant hydration from LocalProfileStore: userId=$userId, examProfileId=${localExamProfile.examProfileId}")
                    _sessionState.value = SessionState.Ready(localProfile, localExamProfile)
                }
            }
        }

        if (_sessionState.value !is SessionState.Ready) {
            _sessionState.value = SessionState.LoadingProfile
        }

        try {
            var profile = userProfileRepository.fetchUserProfile(userId)
            if (profile == null) {
                // If profile is missing in backend DB, create initial profile entry
                val email = authRepository.fetchUserEmail()
                val tempName = email?.substringBefore("@")
                profile = userProfileRepository.createUserProfile(userId, email, tempName)
            }

            val isComplete = profile.profileCompleted == true ||
                    profile.onboardingStep?.equals("COMPLETED", ignoreCase = true) == true ||
                    profile.onboardingStep?.equals("DONE", ignoreCase = true) == true ||
                    profile.onboardingStep?.equals("COMPLETE", ignoreCase = true) == true ||
                    (!profile.name.isNullOrEmpty() && !profile.preparing.isNullOrEmpty())

            Log.i("AuthViewModel", "loadProfile: userId=${profile.userId}, name='${profile.name}', preparing='${profile.preparing}', stage='${profile.examStage}', examProfileId='${profile.examProfileId}', onboardingStep='${profile.onboardingStep}', profileCompleted=${profile.profileCompleted}, isComplete=$isComplete")

            if (isComplete) {
                val targetExamId = profile.preparing
                    ?: profile.examTags.firstOrNull()
                    ?: "CAT Management"
                val targetStage = profile.examStage
                val targetProfileId = profile.examProfileId

                val examProfile = if (!targetProfileId.isNullOrBlank()) {
                    examProfileRepository.getExamProfile(targetProfileId) ?: examProfileRepository.fetchExamProfileForUser(targetExamId, targetStage)
                } else {
                    examProfileRepository.fetchExamProfileForUser(targetExamId, targetStage)
                }

                Log.i("AuthViewModel", "Resolved examProfile: ${examProfile?.examProfileId}, active=${examProfile?.active}")

                if (examProfile != null && examProfile.active) {
                    _sessionState.value = SessionState.Ready(profile, examProfile)
                } else {
                    val activeProfiles = examProfileRepository.listActiveProfilesForExam(targetExamId)
                    Log.i("AuthViewModel", "listActiveProfilesForExam('$targetExamId') returned ${activeProfiles.size} items: ${activeProfiles.map { it.examProfileId }}")
                    if (activeProfiles.isNotEmpty()) {
                        val fallbackProfile = activeProfiles.first()
                        _sessionState.value = SessionState.Ready(profile, fallbackProfile)
                    } else if (profile.preparing.isNullOrBlank()) {
                        val allActive = examProfileRepository.listAllActiveExamProfiles()
                        if (allActive.isNotEmpty()) {
                            val firstActive = allActive.first()
                            _sessionState.value = SessionState.Ready(profile, firstActive)
                        } else {
                            _sessionState.value = SessionState.IncompleteProfile(profile)
                        }
                    } else {
                        Log.e("AuthViewModel", "Unsupported or inactive exam profile for examId: '$targetExamId', stage: '$targetStage'")
                        if (_sessionState.value !is SessionState.Ready) {
                            _sessionState.value = SessionState.Error(
                                "This exam setup isn't available yet. Please select another exam."
                            )
                        }
                    }
                }
            } else {
                Log.i("AuthViewModel", "Profile incomplete, transitioning to SessionState.IncompleteProfile")
                _sessionState.value = SessionState.IncompleteProfile(profile)
            }
        } catch (e: Exception) {
            Log.e("AuthViewModel", "Failed to load/create profile", e)
            if (_sessionState.value !is SessionState.Ready) {
                _sessionState.value = SessionState.Error(
                    "We couldn't connect to MeritRanker right now. Please check your network connection and tap retry."
                )
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            val currentState = _sessionState.value
            if (currentState is SessionState.SignedOut && currentState.isSigningIn) {
                return@launch // Prevent duplicate sign-in calls
            }
            _sessionState.value = SessionState.SignedOut(isSigningIn = true)
            try {
                val result = authRepository.signInWithGoogle(activity)
                if (result.nextStep.signInStep == AuthSignInStep.DONE) {
                    loadProfile()
                } else {
                    _sessionState.value = SessionState.SignedOut("Sign-in process incomplete: ${result.nextStep.signInStep}")
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google sign-in failed", e)
                val isCancel = e is UserCancelledException ||
                        e.cause is UserCancelledException ||
                        e.message?.contains("cancelled", ignoreCase = true) == true
                
                val errorMessage = if (isCancel) {
                    null // Safe cancellation behavior - don't show error message, just return to idle
                } else {
                    "Google sign-in could not be completed. Please try again."
                }
                _sessionState.value = SessionState.SignedOut(error = errorMessage, isSigningIn = false)
            }
        }
    }

    fun completeProfile(
        name: String,
        preparing: String,
        examStage: String? = null,
        examProfileId: String? = null,
        additionalExams: List<String> = emptyList(),
        dateOfBirth: String? = null,
        language: String? = "ENGLISH",
        onError: ((String) -> Unit)? = null
    ) {
        val currentState = _sessionState.value
        val profile = when (currentState) {
            is SessionState.IncompleteProfile -> currentState.profile
            is SessionState.Ready -> currentState.profile
            else -> return
        }
        viewModelScope.launch {
            try {
                val examProfile = (if (!examProfileId.isNullOrBlank()) {
                    examProfileRepository.getExamProfile(examProfileId)
                } else null) ?: examProfileRepository.fetchExamProfileForUser(preparing, examStage)
                  ?: examProfileRepository.listActiveProfilesForExam(preparing).firstOrNull()
                  ?: examProfileRepository.listAllActiveExamProfiles().firstOrNull()

                Log.i("AuthViewModel", "completeProfile resolved examProfile: ${examProfile?.examProfileId}, active=${examProfile?.active}")

                if (examProfile != null && examProfile.active) {
                    val updatedProfile = userProfileRepository.updateUserProfile(
                        userId = profile.userId,
                        name = name,
                        preparing = preparing,
                        examProfileId = examProfile.examProfileId,
                        examStage = examProfile.stage,
                        additionalExams = additionalExams,
                        dateOfBirth = dateOfBirth,
                        language = language
                    )
                    Log.i("AuthViewModel", "completeProfile updatedProfile: userId=${updatedProfile.userId}, onboardingStep=${updatedProfile.onboardingStep}, profileCompleted=${updatedProfile.profileCompleted}")
                    _sessionState.value = SessionState.Ready(updatedProfile, examProfile)
                } else {
                    Log.e("AuthViewModel", "Selected exam profile unavailable: preparing='$preparing', stage='$examStage', id='$examProfileId'")
                    onError?.invoke("This exam setup isn't available yet. Please select another exam.")
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to complete onboarding profile", e)
                onError?.invoke("We couldn't save your preferences. Please try again.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _sessionState.value = SessionState.Initialising
            try {
                authRepository.signOut()
                userProfileRepository.clearCache()
                examProfileRepository.clearCache()
                localProfileStore?.clear()
                _sessionState.value = SessionState.SignedOut()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign-out failed", e)
                userProfileRepository.clearCache()
                examProfileRepository.clearCache()
                localProfileStore?.clear()
                _sessionState.value = SessionState.SignedOut("Sign-out failed: ${e.localizedMessage}")
            }
        }
    }

    fun deleteAccount(onError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val currentState = _sessionState.value
            _sessionState.value = SessionState.Initialising
            try {
                val result = authRepository.deleteAccount()
                if (result.isSuccess) {
                    userProfileRepository.clearCache()
                    examProfileRepository.clearCache()
                    localProfileStore?.clear()
                    _sessionState.value = SessionState.SignedOut()
                } else {
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete account on server. Please try again."
                    Log.e("AuthViewModel", "Account deletion failed: $errorMsg")
                    _sessionState.value = currentState
                    onError?.invoke(errorMsg)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Account deletion error", e)
                _sessionState.value = currentState
                onError?.invoke(e.localizedMessage ?: "Failed to delete account. Please check your network connection.")
            }
        }
    }
}
