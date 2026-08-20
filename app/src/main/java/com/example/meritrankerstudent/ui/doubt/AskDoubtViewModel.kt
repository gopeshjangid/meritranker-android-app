package com.example.meritrankerstudent.ui.doubt

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meritrankerstudent.data.model.ConversationSession
import com.example.meritrankerstudent.data.model.DoubtMessage
import com.example.meritrankerstudent.data.model.DoubtRequest
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DefaultAuthRepository
import com.example.meritrankerstudent.data.repository.DefaultDoubtRepository
import com.example.meritrankerstudent.data.repository.DefaultUserProfileRepository
import com.example.meritrankerstudent.data.repository.DoubtRepository
import com.example.meritrankerstudent.data.repository.UserProfileRepository
import com.example.meritrankerstudent.util.speech.VoiceLanguageMode
import com.example.meritrankerstudent.util.speech.VoiceState
import com.example.meritrankerstudent.util.speech.VoiceTranscriptCleaner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class AttachmentSource {
    CAMERA,
    GALLERY
}

data class DoubtUiState(
    val userId: String = "",
    val activeConversationId: String = UUID.randomUUID().toString(),
    val currentTurnId: String = UUID.randomUUID().toString(),
    val sessions: List<ConversationSession> = emptyList(),
    val messages: List<DoubtMessage> = emptyList(),
    val isAiThinking: Boolean = false,
    val isStreaming: Boolean = false,
    val isLoadingSessions: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isHistoryDrawerOpen: Boolean = false,
    val isAttachmentSheetOpen: Boolean = false,
    val inputText: String = "",
    val selectedAttachmentUri: String? = null,
    val selectedAttachmentBase64: String? = null,
    val attachmentSource: AttachmentSource? = null,
    val isAttachmentPreparing: Boolean = false,
    val attachmentError: String? = null,
    val isCameraPermissionDenied: Boolean = false,
    val isCameraPermissionPermanentlyDenied: Boolean = false,
    val isMicPermissionDenied: Boolean = false,
    val isMicPermissionPermanentlyDenied: Boolean = false,
    val isVoiceModeActive: Boolean = false,
    val voiceState: VoiceState = VoiceState.IDLE,
    val voiceLanguageMode: VoiceLanguageMode = VoiceLanguageMode.AUTO,
    val voicePartialTranscript: String? = null,
    val voiceErrorMessage: String? = null,
    val selectedLanguage: String = "en",
    val selectedExam: String = "",
    val selectedExamProfile: com.example.meritrankerstudent.data.model.ExamProfile? = null,
    val availableExamProfiles: List<com.example.meritrankerstudent.data.model.ExamProfile> = emptyList(),
    val availableExams: List<String> = emptyList(),
    val examProfileId: String? = null,
    val examStage: String? = null,
    val isSending: Boolean = false,
    val sendError: String? = null,
    val reportingMessageId: String? = null,
    val isReporting: Boolean = false,
    val reportSuccessMessage: String? = null,
    val reportErrorMessage: String? = null
)

class AskDoubtViewModel(
    private val repository: DoubtRepository = DefaultDoubtRepository(),
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val userProfileRepository: UserProfileRepository = DefaultUserProfileRepository(),
    private val examProfileRepository: com.example.meritrankerstudent.data.repository.ExamProfileRepository = com.example.meritrankerstudent.data.repository.DefaultExamProfileRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoubtUiState())
    val uiState: StateFlow<DoubtUiState> = _uiState.asStateFlow()

    private val _conversationSessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    val conversationSessions: StateFlow<List<ConversationSession>> = _conversationSessions.asStateFlow()

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading.asStateFlow()

    private var activeStreamJob: Job? = null

    init {
        initializeUserAndSessions()
    }

    private fun initializeUserAndSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true) }
            val currentUserId = authRepository.getCurrentUserId() ?: "authenticated_student_user"
            _uiState.update { it.copy(userId = currentUserId) }

            repository.setActiveConversation(_uiState.value.activeConversationId)

            repository.sessions.collect { sessionList ->
                _uiState.update { it.copy(sessions = sessionList, isLoadingSessions = false) }
                _conversationSessions.value = sessionList
            }
        }

        // 1. Observe dynamic ExamProfiles from Room / AppSync repository
        viewModelScope.launch {
            examProfileRepository.observeAllActiveExamProfiles().collect { activeProfiles ->
                val availableNames = activeProfiles.map { it.examName }.distinct()
                _uiState.update { current ->
                    val resolvedProfile = activeProfiles.firstOrNull { it.examProfileId == current.examProfileId }
                        ?: activeProfiles.firstOrNull { it.examName.equals(current.selectedExam, ignoreCase = true) }
                        ?: activeProfiles.firstOrNull()

                    current.copy(
                        availableExamProfiles = activeProfiles,
                        availableExams = availableNames,
                        selectedExamProfile = resolvedProfile,
                        selectedExam = resolvedProfile?.examName ?: current.selectedExam,
                        examProfileId = resolvedProfile?.examProfileId ?: current.examProfileId,
                        examStage = resolvedProfile?.stage ?: current.examStage
                    )
                }
            }
        }

        // 2. Observe authoritative UserProfile for exam preference and language
        viewModelScope.launch {
            val currentUserId = authRepository.getCurrentUserId() ?: "authenticated_student_user"
            userProfileRepository.observeUserProfile(currentUserId).collect { profile ->
                if (profile != null) {
                    val lang = profile.language?.lowercase() ?: "en"
                    val normalizedLang = if (lang.startsWith("hi")) "hi" else "en"
                    _uiState.update { current ->
                        val matchedProfile = current.availableExamProfiles.firstOrNull { it.examProfileId == profile.examProfileId }
                            ?: current.availableExamProfiles.firstOrNull { it.examName.equals(profile.preparing, ignoreCase = true) }
                            ?: current.selectedExamProfile

                        current.copy(
                            selectedLanguage = normalizedLang,
                            selectedExamProfile = matchedProfile,
                            selectedExam = matchedProfile?.examName ?: profile.preparing ?: current.selectedExam,
                            examProfileId = matchedProfile?.examProfileId ?: profile.examProfileId ?: current.examProfileId,
                            examStage = matchedProfile?.stage ?: profile.examStage ?: current.examStage
                        )
                    }
                }
            }
        }

        // 3. Observe Messages
        viewModelScope.launch {
            repository.messages.collect { messageList ->
                _uiState.update { it.copy(messages = messageList, isLoadingMessages = false) }
            }
        }

        refreshSessions()
    }

    fun refreshSessions() {
        viewModelScope.launch {
            val uid = _uiState.value.userId.ifEmpty { "authenticated_student_user" }
            try {
                repository.fetchUserSessions(uid)
            } catch (e: Throwable) {
                Log.w("AskDoubtViewModel", "Could not fetch user sessions", e)
            }
        }
    }

    fun loadConversationHistory() {
        viewModelScope.launch {
            _isHistoryLoading.value = true
            val uid = _uiState.value.userId.ifEmpty { "authenticated_student_user" }
            val list = repository.fetchUserSessions(uid)
            _conversationSessions.value = list
            _isHistoryLoading.value = false
        }
    }

    fun startNewChat() {
        activeStreamJob?.cancel()
        voiceBaseText = ""
        voiceAccumulatedText = ""
        val newConvId = UUID.randomUUID().toString()
        val newTurnId = UUID.randomUUID().toString()

        repository.setActiveConversation(newConvId)
        android.util.Log.d("MeritRankerChat", "NEW_CHAT_REQUESTED newConvId=$newConvId")

        _uiState.update {
            it.copy(
                activeConversationId = newConvId,
                currentTurnId = newTurnId,
                messages = emptyList(),
                inputText = "",
                selectedAttachmentUri = null,
                selectedAttachmentBase64 = null,
                attachmentSource = null,
                isAttachmentPreparing = false,
                attachmentError = null,
                isVoiceModeActive = false,
                voiceState = VoiceState.IDLE,
                voicePartialTranscript = null,
                voiceErrorMessage = null,
                isHistoryDrawerOpen = false,
                isAttachmentSheetOpen = false,
                isSending = false,
                isStreaming = false,
                isAiThinking = false,
                sendError = null
            )
        }
    }

    fun selectConversation(conversationId: String) {
        if (_uiState.value.activeConversationId == conversationId) {
            _uiState.update { it.copy(isHistoryDrawerOpen = false) }
            return
        }

        activeStreamJob?.cancel()
        voiceBaseText = ""
        voiceAccumulatedText = ""
        repository.setActiveConversation(conversationId)
        val newTurnId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                activeConversationId = conversationId,
                currentTurnId = newTurnId,
                isLoadingMessages = true,
                isHistoryDrawerOpen = false,
                isAttachmentSheetOpen = false,
                isVoiceModeActive = false,
                voiceState = VoiceState.IDLE,
                voicePartialTranscript = null,
                voiceErrorMessage = null,
                isSending = false,
                isStreaming = false,
                isAiThinking = false,
                sendError = null
            )
        }

        viewModelScope.launch {
            val uid = _uiState.value.userId.ifEmpty { "authenticated_student_user" }
            if (_uiState.value.activeConversationId == conversationId) {
                repository.fetchConversationTurns(uid, conversationId)
            }
        }
    }

    fun selectConversationSession(session: ConversationSession) {
        selectConversation(session.id)
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
        if (!_uiState.value.isVoiceModeActive) {
            voiceBaseText = text.trim()
            voiceAccumulatedText = ""
        }
    }

    fun onLanguageSelected(languageCode: String) {
        val normalized = if (languageCode.lowercase().startsWith("hi")) "hi" else "en"
        _uiState.update { it.copy(selectedLanguage = normalized) }
        viewModelScope.launch {
            val uid = _uiState.value.userId
            if (uid.isNotEmpty() && uid != "authenticated_student_user") {
                try {
                    val current = userProfileRepository.fetchUserProfile(uid)
                    if (current != null) {
                        userProfileRepository.updateUserProfile(
                            userId = uid,
                            name = current.name ?: "",
                            preparing = current.preparing ?: "",
                            additionalExams = current.additionalExams,
                            dateOfBirth = current.dateOfBirth ?: "2000-08-15",
                            language = if (normalized == "hi") "hindi" else "english"
                        )
                    }
                } catch (e: Throwable) {
                    Log.w("AskDoubtViewModel", "Could not sync language to user profile", e)
                }
            }
        }
    }

    fun onExamProfileSelected(profile: com.example.meritrankerstudent.data.model.ExamProfile) {
        _uiState.update {
            it.copy(
                selectedExamProfile = profile,
                selectedExam = profile.examName,
                examProfileId = profile.examProfileId,
                examStage = profile.stage
            )
        }
        viewModelScope.launch {
            val currentUserId = authRepository.getCurrentUserId() ?: return@launch
            try {
                val current = userProfileRepository.fetchUserProfile(currentUserId)
                userProfileRepository.updateUserProfile(
                    userId = currentUserId,
                    name = current?.name ?: "Student",
                    preparing = profile.examName,
                    examProfileId = profile.examProfileId,
                    examStage = profile.stage,
                    additionalExams = current?.additionalExams ?: emptyList(),
                    dateOfBirth = current?.dateOfBirth ?: "2000-08-15",
                    language = current?.language ?: "ENGLISH"
                )
            } catch (e: Throwable) {
                Log.w("AskDoubtViewModel", "Could not sync selected exam profile to backend: ${e.message}")
            }
        }
    }

    fun onExamSelected(exam: String) {
        val matchedProfile = _uiState.value.availableExamProfiles.firstOrNull { it.examName.equals(exam, ignoreCase = true) }
        if (matchedProfile != null) {
            onExamProfileSelected(matchedProfile)
        } else {
            _uiState.update { it.copy(selectedExam = exam) }
        }
    }

    fun toggleHistoryDrawer() {
        _uiState.update { it.copy(isHistoryDrawerOpen = !it.isHistoryDrawerOpen) }
    }

    fun setHistoryDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isHistoryDrawerOpen = isOpen) }
    }

    fun setAttachmentSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isAttachmentSheetOpen = isOpen) }
    }

    // Camera Permissions
    fun onCameraPermissionResult(isGranted: Boolean, shouldShowRationale: Boolean) {
        if (isGranted) {
            _uiState.update {
                it.copy(
                    isCameraPermissionDenied = false,
                    isCameraPermissionPermanentlyDenied = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isCameraPermissionDenied = true,
                    isCameraPermissionPermanentlyDenied = !shouldShowRationale
                )
            }
        }
    }

    fun dismissCameraPermissionNotice() {
        _uiState.update {
            it.copy(
                isCameraPermissionDenied = false,
                isCameraPermissionPermanentlyDenied = false
            )
        }
    }

    // Microphone Permissions
    fun onMicPermissionResult(isGranted: Boolean, shouldShowRationale: Boolean) {
        if (isGranted) {
            _uiState.update {
                it.copy(
                    isMicPermissionDenied = false,
                    isMicPermissionPermanentlyDenied = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isMicPermissionDenied = true,
                    isMicPermissionPermanentlyDenied = !shouldShowRationale
                )
            }
        }
    }

    fun dismissMicPermissionNotice() {
        _uiState.update {
            it.copy(
                isMicPermissionDenied = false,
                isMicPermissionPermanentlyDenied = false
            )
        }
    }

    private var voiceBaseText: String = ""
    private var voiceAccumulatedText: String = ""

    private fun buildComposedText(base: String, accumulated: String, partial: String?): String {
        val parts = listOfNotNull(
            base.takeIf { it.isNotBlank() },
            accumulated.takeIf { it.isNotBlank() },
            partial?.takeIf { it.isNotBlank() }
        )
        return parts.joinToString(" ")
    }

    // Voice State Callbacks
    fun startVoiceMode() {
        voiceBaseText = _uiState.value.inputText.trim()
        voiceAccumulatedText = ""
        _uiState.update {
            it.copy(
                isVoiceModeActive = true,
                voiceState = VoiceState.LISTENING,
                voicePartialTranscript = null,
                voiceErrorMessage = null
            )
        }
    }

    fun onVoiceLanguageModeSelected(mode: VoiceLanguageMode) {
        _uiState.update { it.copy(voiceLanguageMode = mode) }
    }

    fun stopVoiceMode() {
        _uiState.update {
            it.copy(
                isVoiceModeActive = false,
                voiceState = VoiceState.IDLE,
                voicePartialTranscript = null
            )
        }
        voiceBaseText = ""
        voiceAccumulatedText = ""
    }

    fun onVoiceModeChanged(active: Boolean) {
        _uiState.update { it.copy(isVoiceModeActive = active) }
        if (!active) {
            voiceBaseText = ""
            voiceAccumulatedText = ""
        }
    }

    fun onVoiceStateChanged(state: VoiceState) {
        if (state == VoiceState.LISTENING) {
            if (voiceBaseText.isBlank() && voiceAccumulatedText.isBlank() && _uiState.value.inputText.isNotBlank()) {
                voiceBaseText = _uiState.value.inputText.trim()
            }
        }
        _uiState.update { it.copy(voiceState = state) }
    }

    fun onVoicePartialTranscript(partialText: String) {
        if (voiceBaseText.isBlank() && voiceAccumulatedText.isBlank() && _uiState.value.inputText.isNotBlank() && _uiState.value.voicePartialTranscript == null) {
            voiceBaseText = _uiState.value.inputText.trim()
        }
        val preview = VoiceTranscriptCleaner.cleanPreview(partialText, _uiState.value.selectedLanguage)
        val composedText = buildComposedText(voiceBaseText, voiceAccumulatedText, preview)
        _uiState.update {
            it.copy(
                inputText = composedText,
                voicePartialTranscript = preview
            )
        }
    }

    fun onVoiceFinalTranscript(finalText: String) {
        if (voiceBaseText.isBlank() && voiceAccumulatedText.isBlank() && _uiState.value.inputText.isNotBlank() && _uiState.value.voicePartialTranscript == null) {
            voiceBaseText = _uiState.value.inputText.trim()
        }
        val cleaned = VoiceTranscriptCleaner.cleanTranscript(finalText, _uiState.value.selectedLanguage)
        if (cleaned.isNotBlank()) {
            voiceAccumulatedText = if (voiceAccumulatedText.isBlank()) {
                cleaned
            } else {
                "$voiceAccumulatedText $cleaned"
            }
        }
        val fullText = buildComposedText(voiceBaseText, voiceAccumulatedText, null)
        _uiState.update { state ->
            state.copy(
                inputText = fullText,
                voicePartialTranscript = null,
                voiceState = if (state.isVoiceModeActive) VoiceState.LISTENING else VoiceState.IDLE,
                voiceErrorMessage = null
            )
        }
    }

    fun onVoiceError(errorMessage: String) {
        _uiState.update {
            it.copy(
                isVoiceModeActive = false,
                voiceState = VoiceState.ERROR,
                voiceErrorMessage = errorMessage,
                voicePartialTranscript = null
            )
        }
        voiceBaseText = ""
        voiceAccumulatedText = ""
    }

    fun dismissVoiceError() {
        _uiState.update {
            it.copy(
                isVoiceModeActive = false,
                voiceState = VoiceState.IDLE,
                voiceErrorMessage = null
            )
        }
    }

    fun cancelVoice() {
        val restored = if (voiceBaseText.isNotBlank()) voiceBaseText else _uiState.value.inputText
        _uiState.update {
            it.copy(
                inputText = restored,
                isVoiceModeActive = false,
                voiceState = VoiceState.IDLE,
                voicePartialTranscript = null
            )
        }
        voiceBaseText = ""
        voiceAccumulatedText = ""
    }

    // Image Attachment Handling
    fun onImagePreparationStarted() {
        _uiState.update {
            it.copy(
                isAttachmentPreparing = true,
                attachmentError = null,
                isAttachmentSheetOpen = false
            )
        }
    }

    fun onImagePreparationCompleted(uriString: String, base64: String? = null, source: AttachmentSource = AttachmentSource.GALLERY) {
        _uiState.update {
            it.copy(
                selectedAttachmentUri = uriString,
                selectedAttachmentBase64 = base64,
                attachmentSource = source,
                isAttachmentPreparing = false,
                attachmentError = null,
                isAttachmentSheetOpen = false
            )
        }
    }

    fun onImagePreparationCompleted(uriString: String, source: AttachmentSource) {
        onImagePreparationCompleted(uriString, null, source)
    }

    fun onImagePreparationError(errorMessage: String) {
        _uiState.update {
            it.copy(
                isAttachmentPreparing = false,
                attachmentError = errorMessage,
                isAttachmentSheetOpen = false
            )
        }
    }

    fun removeAttachment() {
        _uiState.update {
            it.copy(
                selectedAttachmentUri = null,
                selectedAttachmentBase64 = null,
                attachmentSource = null,
                isAttachmentPreparing = false,
                attachmentError = null
            )
        }
    }

    fun onSuggestionSelected(suggestionText: String) {
        _uiState.update { state ->
            val current = state.inputText
            val newText = if (current.isBlank()) {
                suggestionText
            } else if (current.endsWith(" ")) {
                current + suggestionText
            } else {
                "$current $suggestionText"
            }
            state.copy(inputText = newText)
        }
    }

    fun submitPromptDirectly(promptText: String) {
        _uiState.update { it.copy(inputText = promptText) }
        sendMessage()
    }

    // Unified Submission Path (Text, Voice Transcript, Image + Text)
    fun sendMessage() {
        val state = _uiState.value
        val trimmedQuery = state.inputText.trim()
        val attachmentUri = state.selectedAttachmentUri
        val attachmentBase64 = state.selectedAttachmentBase64

        if (trimmedQuery.isEmpty() && attachmentUri == null) return
        if (state.isSending || state.isAttachmentPreparing || state.attachmentError != null) return

        val targetConvId = state.activeConversationId
        val targetTurnId = state.currentTurnId

        voiceBaseText = ""
        voiceAccumulatedText = ""

        // Snapshot draft state and clear composer immediately upon send accepted
        _uiState.update {
            it.copy(
                inputText = "",
                selectedAttachmentUri = null,
                selectedAttachmentBase64 = null,
                attachmentSource = null,
                isAttachmentPreparing = false,
                attachmentError = null,
                isVoiceModeActive = false,
                voiceState = VoiceState.IDLE,
                voicePartialTranscript = null,
                voiceErrorMessage = null,
                isSending = true,
                isAiThinking = true,
                isStreaming = true,
                sendError = null
            )
        }

        activeStreamJob = viewModelScope.launch {
            val tokenResult = authRepository.getAccessToken()
            val authToken = tokenResult.getOrNull()

            val request = DoubtRequest(
                userId = state.userId.ifEmpty { "authenticated_student_user" },
                conversationId = targetConvId,
                turnId = targetTurnId,
                query = trimmedQuery,
                language = state.selectedLanguage,
                examProfileId = state.examProfileId,
                examId = state.selectedExam,
                examStage = state.examStage,
                imageUri = attachmentUri,
                imageBase64 = attachmentBase64,
                imageMimeType = "image/jpeg"
            )

            try {
                if (_uiState.value.activeConversationId == targetConvId) {
                    repository.sendDoubtQueryStream(request, authToken) { chunk ->
                        _uiState.update { it.copy(isAiThinking = false, isStreaming = true) }
                    }

                    _uiState.update {
                        it.copy(
                            currentTurnId = UUID.randomUUID().toString(),
                            isSending = false,
                            isStreaming = false,
                            isAiThinking = false,
                            sendError = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        isStreaming = false,
                        isAiThinking = false,
                        sendError = "Couldn't send your doubt. Tap retry to attempt again."
                    )
                }
            }
        }
    }

    fun stopGenerating() {
        activeStreamJob?.cancel()
        _uiState.update {
            it.copy(
                isSending = false,
                isStreaming = false,
                isAiThinking = false
            )
        }
    }

    fun retrySendMessage() {
        val lastMsg = _uiState.value.messages.lastOrNull { it.sender == "USER" }
        if (lastMsg != null) {
            _uiState.update { it.copy(inputText = lastMsg.text, selectedAttachmentUri = lastMsg.imageUri) }
            sendMessage()
        }
    }

    fun clearChat() {
        activeStreamJob?.cancel()
        viewModelScope.launch {
            val uid = _uiState.value.userId.ifEmpty { "authenticated_student_user" }
            repository.clearHistory(uid)
            startNewChat()
        }
    }

    fun openReportDialog(messageId: String) {
        _uiState.update { it.copy(reportingMessageId = messageId, reportSuccessMessage = null, reportErrorMessage = null) }
    }

    fun dismissReportDialog() {
        _uiState.update { it.copy(reportingMessageId = null, reportSuccessMessage = null, reportErrorMessage = null) }
    }

    fun submitContentReport(
        messageId: String,
        category: String,
        comment: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReporting = true, reportErrorMessage = null) }
            val token = authRepository.getAccessToken().getOrNull()
            val result = repository.reportAssistantResponse(
                messageId = messageId,
                conversationId = _uiState.value.activeConversationId,
                category = category,
                comment = comment,
                authToken = token
            )
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isReporting = false,
                        reportSuccessMessage = "Thanks. Your report has been submitted.",
                        reportingMessageId = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isReporting = false,
                        reportErrorMessage = "We couldn't submit your report. Please try again."
                    )
                }
            }
        }
    }

    fun clearReportFeedback() {
        _uiState.update { it.copy(reportSuccessMessage = null, reportErrorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        activeStreamJob?.cancel()
    }
}
