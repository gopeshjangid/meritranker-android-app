package com.example.meritrankerstudent.ui.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.MeritRankerApplication
import com.example.meritrankerstudent.data.local.LocalProfileStore
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.GetStudentPerformanceView
import com.example.meritrankerstudent.data.model.StudentPerformanceResponse
import com.example.meritrankerstudent.data.repository.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Content(
        val response: StudentPerformanceResponse,
        val selectedView: GetStudentPerformanceView,
        val isRefreshing: Boolean,
        val examName: String,
        val examProfileId: String,
        val selectedLanguage: String = "en",
        val selectedExamProfile: ExamProfile? = null,
        val availableExamProfiles: List<ExamProfile> = emptyList()
    ) : ProgressUiState
    data class Empty(
        val selectedView: GetStudentPerformanceView,
        val examName: String,
        val examProfileId: String,
        val message: String,
        val selectedLanguage: String = "en",
        val selectedExamProfile: ExamProfile? = null,
        val availableExamProfiles: List<ExamProfile> = emptyList()
    ) : ProgressUiState
    data class Error(
        val message: String,
        val selectedLanguage: String = "en",
        val selectedExamProfile: ExamProfile? = null,
        val availableExamProfiles: List<ExamProfile> = emptyList()
    ) : ProgressUiState
}

class ProgressViewModel(
    private val repository: StudentPerformanceRepository = DefaultStudentPerformanceRepository(),
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val userProfileRepository: UserProfileRepository = DefaultUserProfileRepository(),
    private val examProfileRepository: ExamProfileRepository = DefaultExamProfileRepository(),
    private val localProfileStore: LocalProfileStore? = MeritRankerApplication.instance?.let { LocalProfileStore(it) },
    private val initialExamProfileId: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProgressUiState>(ProgressUiState.Loading)
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    private val _selectedView = MutableStateFlow(GetStudentPerformanceView.PRACTICE)
    val selectedView: StateFlow<GetStudentPerformanceView> = _selectedView.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _availableExamProfiles = MutableStateFlow<List<ExamProfile>>(emptyList())
    val availableExamProfiles: StateFlow<List<ExamProfile>> = _availableExamProfiles.asStateFlow()

    private val _selectedExamProfile = MutableStateFlow<ExamProfile?>(null)
    val selectedExamProfile: StateFlow<ExamProfile?> = _selectedExamProfile.asStateFlow()

    private var observeJob: Job? = null
    private var fetchJob: Job? = null

    init {
        observeExamProfiles()
        observeUserProfile()
        loadPerformance()
    }

    private fun observeExamProfiles() {
        viewModelScope.launch {
            examProfileRepository.observeAllActiveExamProfiles().collect { profiles ->
                _availableExamProfiles.value = profiles
                if (_selectedExamProfile.value == null && profiles.isNotEmpty()) {
                    val currentId = _selectedExamProfile.value?.examProfileId
                    val matched = if (currentId != null) {
                        profiles.firstOrNull { it.examProfileId == currentId }
                    } else {
                        profiles.firstOrNull()
                    }
                    _selectedExamProfile.value = matched
                }
            }
        }
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentUserId() ?: ""
                if (userId.isNotBlank()) {
                    val profile = try {
                        userProfileRepository.fetchUserProfile(userId)
                    } catch (e: Exception) {
                        null
                    }
                    if (profile != null) {
                        val lang = if (profile.language?.startsWith("hi", ignoreCase = true) == true) "hi" else "en"
                        _selectedLanguage.value = lang
                        if (!profile.examProfileId.isNullOrBlank()) {
                            val examProfile = examProfileRepository.getExamProfile(profile.examProfileId)
                            if (examProfile != null) {
                                _selectedExamProfile.value = examProfile
                            }
                        }
                    }
                    userProfileRepository.observeUserProfile(userId).collect { liveProfile ->
                        if (liveProfile != null) {
                            val lang = if (liveProfile.language?.startsWith("hi", ignoreCase = true) == true) "hi" else "en"
                            val langChanged = _selectedLanguage.value != lang
                            if (langChanged) {
                                _selectedLanguage.value = lang
                            }
                            if (!liveProfile.examProfileId.isNullOrBlank() && liveProfile.examProfileId != _selectedExamProfile.value?.examProfileId) {
                                val examProfile = examProfileRepository.getExamProfile(liveProfile.examProfileId)
                                if (examProfile != null) {
                                    _selectedExamProfile.value = examProfile
                                    loadPerformance(view = _selectedView.value, isRefresh = false)
                                }
                            } else if (langChanged) {
                                loadPerformance(view = _selectedView.value, isRefresh = false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent fallback for test and offline environments
            }
        }
    }

    fun switchView(view: GetStudentPerformanceView) {
        if (_selectedView.value == view) return
        _selectedView.value = view
        loadPerformance(view = view, isRefresh = false)
    }

    fun selectExamProfile(profile: ExamProfile) {
        _selectedExamProfile.value = profile
        loadPerformance(view = _selectedView.value, isRefresh = false)
    }

    fun refresh() {
        loadPerformance(view = _selectedView.value, isRefresh = true)
    }

    fun loadPerformance(
        view: GetStudentPerformanceView = _selectedView.value,
        isRefresh: Boolean = false
    ) {
        observeJob?.cancel()
        fetchJob?.cancel()

        observeJob = viewModelScope.launch {
            val (examProfileId, examName) = resolveExamProfile()
            val currentLang = _selectedLanguage.value
            val isHindi = currentLang.startsWith("hi", ignoreCase = true)
            val currentActiveProfiles = _availableExamProfiles.value
            val currentSelectedProfile = _selectedExamProfile.value

            // 1. Observe Room cache immediately (0ms instant render)
            launch {
                repository.observePerformance(examProfileId, view, null).collectLatest { cachedResponse ->
                    if (cachedResponse != null) {
                        val hasEvidence = cachedResponse.overall.attempted > 0 || cachedResponse.items.isNotEmpty()
                        if (!hasEvidence && !cachedResponse.isUpdatingLatestPerformance) {
                            _uiState.value = ProgressUiState.Empty(
                                selectedView = view,
                                examName = examName,
                                examProfileId = examProfileId,
                                message = if (isHindi) "अभ्यास पूरा करने के बाद आपकी प्रगति यहाँ दिखाई देगी।" else "Your progress will appear here after you complete some practice.",
                                selectedLanguage = currentLang,
                                selectedExamProfile = currentSelectedProfile,
                                availableExamProfiles = currentActiveProfiles
                            )
                        } else {
                            val current = _uiState.value
                            val isRef = if (current is ProgressUiState.Content) current.isRefreshing else false
                            _uiState.value = ProgressUiState.Content(
                                response = cachedResponse,
                                selectedView = view,
                                isRefreshing = isRef,
                                examName = examName,
                                examProfileId = examProfileId,
                                selectedLanguage = currentLang,
                                selectedExamProfile = currentSelectedProfile,
                                availableExamProfiles = currentActiveProfiles
                            )
                        }
                    }
                }
            }

            // 2. Perform silent revalidation query
            val currentState = _uiState.value
            if (isRefresh && currentState is ProgressUiState.Content) {
                _uiState.value = currentState.copy(isRefreshing = true)
            } else if (currentState !is ProgressUiState.Content && currentState !is ProgressUiState.Empty) {
                _uiState.value = ProgressUiState.Loading
            }

            val result = repository.getPerformance(
                examProfileId = examProfileId,
                view = view,
                subjectId = null,
                forceRefresh = isRefresh
            )

            result.fold(
                onSuccess = { response ->
                    com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                        com.example.meritrankerstudent.observability.TelemetryEvent.ProgressViewed(
                            examProfileId = examProfileId,
                            view = view.name
                        )
                    )
                    val hasEvidence = response.overall.attempted > 0 || response.items.isNotEmpty()
                    if (!hasEvidence && !response.isUpdatingLatestPerformance) {
                        _uiState.value = ProgressUiState.Empty(
                            selectedView = view,
                            examName = examName,
                            examProfileId = examProfileId,
                            message = if (isHindi) "अभ्यास पूरा करने के बाद आपकी प्रगति यहाँ दिखाई देगी।" else "Your progress will appear here after you complete some practice.",
                            selectedLanguage = currentLang,
                            selectedExamProfile = _selectedExamProfile.value,
                            availableExamProfiles = _availableExamProfiles.value
                        )
                    } else {
                        _uiState.value = ProgressUiState.Content(
                            response = response,
                            selectedView = view,
                            isRefreshing = false,
                            examName = examName,
                            examProfileId = examProfileId,
                            selectedLanguage = currentLang,
                            selectedExamProfile = _selectedExamProfile.value,
                            availableExamProfiles = _availableExamProfiles.value
                        )
                    }
                },
                onFailure = { error ->
                    val category = com.example.meritrankerstudent.observability.BackendErrorClassifier.classify(error)
                    com.example.meritrankerstudent.observability.AppObservability.analytics.logEvent(
                        com.example.meritrankerstudent.observability.TelemetryEvent.ProgressRefreshFailed(
                            examProfileId = examProfileId,
                            errorCategory = category
                        )
                    )
                    com.example.meritrankerstudent.observability.AppObservability.crashReporter.recordBackendFailure(
                        operation = com.example.meritrankerstudent.observability.OperationName.GET_STUDENT_PERFORMANCE.key,
                        category = category,
                        throwable = error
                    )

                    val latest = _uiState.value
                    if (latest is ProgressUiState.Content) {
                        _uiState.value = latest.copy(isRefreshing = false)
                    } else if (latest !is ProgressUiState.Empty) {
                        _uiState.value = ProgressUiState.Error(
                            message = error.localizedMessage ?: if (isHindi) "प्रगति लोड करने में समस्या हुई।" else "Failed to load progress.",
                            selectedLanguage = currentLang,
                            selectedExamProfile = _selectedExamProfile.value,
                            availableExamProfiles = _availableExamProfiles.value
                        )
                    }
                }
            )
        }
    }

    private suspend fun resolveExamProfile(): Pair<String, String> {
        if (!initialExamProfileId.isNullOrBlank()) {
            return Pair(initialExamProfileId, initialExamProfileId)
        }
        val explicitlySelected = _selectedExamProfile.value
        if (explicitlySelected != null) {
            return Pair(explicitlySelected.examProfileId, explicitlySelected.examName)
        }

        val userId = authRepository.getCurrentUserId() ?: ""
        val local = if (userId.isNotBlank()) localProfileStore?.getUserProfile(userId) else null

        val resolvedId = local?.examProfileId?.ifBlank { null }
            ?: local?.preparing?.ifBlank { null }
            ?: local?.examTags?.firstOrNull()?.ifBlank { null }
            ?: local?.additionalExams?.firstOrNull()?.ifBlank { null }
            ?: _availableExamProfiles.value.firstOrNull()?.examProfileId
            ?: "DEFAULT_EXAM_PROFILE"

        val examProfile = _availableExamProfiles.value.firstOrNull { it.examProfileId == resolvedId }
            ?: localProfileStore?.getExamProfile(resolvedId)
        val examName = examProfile?.examName ?: local?.preparing ?: resolvedId
        return Pair(resolvedId, examName)
    }
}
