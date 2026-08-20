package com.example.meritrankerstudent.ui.doubt

import android.app.Activity
import com.amplifyframework.auth.result.AuthSignInResult
import com.example.meritrankerstudent.data.model.ConversationSession
import com.example.meritrankerstudent.data.model.ConversationTurn
import com.example.meritrankerstudent.data.model.DoubtMessage
import com.example.meritrankerstudent.data.model.DoubtRequest
import com.example.meritrankerstudent.data.model.UserProfile
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DoubtRepository
import com.example.meritrankerstudent.data.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AskDoubtViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDoubtRepository
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var fakeUserProfileRepository: FakeUserProfileRepository
    private lateinit var fakeExamProfileRepository: FakeExamProfileRepository
    private lateinit var viewModel: AskDoubtViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDoubtRepository()
        fakeAuthRepository = FakeAuthRepository()
        fakeUserProfileRepository = FakeUserProfileRepository()
        fakeExamProfileRepository = FakeExamProfileRepository()
        viewModel = AskDoubtViewModel(fakeRepository, fakeAuthRepository, fakeUserProfileRepository, fakeExamProfileRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun multilineDraft_preservedAcrossAttachmentSelection() = runTest(testDispatcher) {
        val longDraft = "Line 1: Solve equation\nLine 2: 3x + 5 = 20\nLine 3: Show shortcuts"
        viewModel.onInputTextChange(longDraft)
        assertEquals(longDraft, viewModel.uiState.value.inputText)

        viewModel.setAttachmentSheetOpen(true)
        assertTrue(viewModel.uiState.value.isAttachmentSheetOpen)
        assertEquals(longDraft, viewModel.uiState.value.inputText)

        viewModel.onImagePreparationCompleted("content://media/external/images/1", AttachmentSource.GALLERY)
        assertEquals("content://media/external/images/1", viewModel.uiState.value.selectedAttachmentUri)
        assertEquals(longDraft, viewModel.uiState.value.inputText)
    }

    @Test
    fun imageRemoval_doesNotClearCaption() = runTest(testDispatcher) {
        val caption = "Explain this profit and loss problem"
        viewModel.onInputTextChange(caption)
        viewModel.onImagePreparationCompleted("file:///tmp/camera.jpg", AttachmentSource.CAMERA)

        assertEquals("file:///tmp/camera.jpg", viewModel.uiState.value.selectedAttachmentUri)
        assertEquals(caption, viewModel.uiState.value.inputText)

        viewModel.removeAttachment()
        assertNull(viewModel.uiState.value.selectedAttachmentUri)
        assertNull(viewModel.uiState.value.attachmentSource)
        assertEquals(caption, viewModel.uiState.value.inputText)
    }

    @Test
    fun imageReplacement_updatesUri_andRetainsCaption() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Caption text")
        viewModel.onImagePreparationCompleted("uri_1", AttachmentSource.GALLERY)
        assertEquals("uri_1", viewModel.uiState.value.selectedAttachmentUri)

        // Replace with new image
        viewModel.onImagePreparationCompleted("uri_2", AttachmentSource.CAMERA)
        assertEquals("uri_2", viewModel.uiState.value.selectedAttachmentUri)
        assertEquals(AttachmentSource.CAMERA, viewModel.uiState.value.attachmentSource)
        assertEquals("Caption text", viewModel.uiState.value.inputText)
    }

    @Test
    fun textOnlySend_clearsInputOnSuccess() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Explain Syllogism rules")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.inputText)
        assertNull(viewModel.uiState.value.selectedAttachmentUri)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun imageAndCaptionSend_clearsDraftOnSuccess() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Solve question 4")
        viewModel.onImagePreparationCompleted("content://media/1", AttachmentSource.GALLERY)

        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.inputText)
        assertNull(viewModel.uiState.value.selectedAttachmentUri)
        assertFalse(viewModel.uiState.value.isSending)
        assertEquals(1, fakeRepository.sentRequests.size)
        assertEquals("Solve question 4", fakeRepository.sentRequests.first().query)
        assertEquals("content://media/1", fakeRepository.sentRequests.first().imageUri)
    }

    @Test
    fun suggestionSelected_populatesComposer_andDoesNotSend() = runTest(testDispatcher) {
        val suggestion = "Explain quadratic equations with quick shortcuts"
        viewModel.onSuggestionSelected(suggestion)

        assertEquals(suggestion, viewModel.uiState.value.inputText)
        assertFalse(viewModel.uiState.value.isSending)
        assertEquals(0, fakeRepository.sentRequests.size)
    }

    @Test
    fun sendFailure_enablesRetryAction() = runTest(testDispatcher) {
        fakeRepository.shouldFail = true
        val text = "Hard calculus problem"
        val imageUri = "content://media/2"

        viewModel.onInputTextChange(text)
        viewModel.onImagePreparationCompleted(imageUri, AttachmentSource.GALLERY)

        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.sendError)
        assertEquals("Couldn't send your doubt. Tap retry to attempt again.", viewModel.uiState.value.sendError)
        assertFalse(viewModel.uiState.value.isSending)
    }
    @Test
    fun newChat_generatesNewConversationId_andClearsMessages() = runTest(testDispatcher) {
        val oldConvId = viewModel.uiState.value.activeConversationId
        viewModel.onInputTextChange("Old chat query")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startNewChat()
        val newConvId = viewModel.uiState.value.activeConversationId

        assertNotEquals(oldConvId, newConvId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals("", viewModel.uiState.value.inputText)
    }

    @Test
    fun sendAfterNewChat_usesNewConversationId_andDoesNotIncludeOldMessages() = runTest(testDispatcher) {
        viewModel.startNewChat()
        val newConvId = viewModel.uiState.value.activeConversationId

        viewModel.onInputTextChange("New quadratic doubt")
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        val sentReq = fakeRepository.sentRequests.last()
        assertEquals(newConvId, sentReq.conversationId)
        assertEquals("New quadratic doubt", sentReq.query)
    }

    @Test
    fun voiceStateTransitions_andPartialTranscripts() = runTest(testDispatcher) {
        viewModel.onVoiceStateChanged(com.example.meritrankerstudent.util.speech.VoiceState.LISTENING)
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceState.LISTENING, viewModel.uiState.value.voiceState)

        viewModel.onVoicePartialTranscript("How to solve")
        assertEquals("How to solve", viewModel.uiState.value.voicePartialTranscript)

        viewModel.onVoicePartialTranscript("How to solve percentage problems")
        assertEquals("How to solve percentage problems", viewModel.uiState.value.voicePartialTranscript)
    }

    @Test
    fun voiceFinalTranscript_appendsToExistingDraft_andDoesNotAutoSubmit() = runTest(testDispatcher) {
        // User typed a partial query first
        viewModel.onInputTextChange("Solve this question:")
        
        // Voice recognition completes
        val spokenTranscript = "find the roots of x^2 + 5x + 6 = 0"
        viewModel.onVoiceFinalTranscript(spokenTranscript)

        // Verifications:
        // 1. Text is merged cleanly in the editable composer
        assertEquals("Solve this question: find the roots of x^2 + 5x + 6 = 0", viewModel.uiState.value.inputText)
        // 2. Voice state reset to IDLE and partial text cleared
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceState.IDLE, viewModel.uiState.value.voiceState)
        assertNull(viewModel.uiState.value.voicePartialTranscript)
        // 3. CRITICAL RULE: Voice NEVER auto-submits!
        assertEquals(0, fakeRepository.sentRequests.size)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun voiceError_setsErrorMessage_andDismissClearsError() = runTest(testDispatcher) {
        viewModel.onVoiceError("We couldn't understand that. Try speaking again.")
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceState.ERROR, viewModel.uiState.value.voiceState)
        assertEquals("We couldn't understand that. Try speaking again.", viewModel.uiState.value.voiceErrorMessage)

        viewModel.dismissVoiceError()
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceState.IDLE, viewModel.uiState.value.voiceState)
        assertNull(viewModel.uiState.value.voiceErrorMessage)
    }

    @Test
    fun micPermissionResult_updatesState() = runTest(testDispatcher) {
        viewModel.onMicPermissionResult(isGranted = false, shouldShowRationale = true)
        assertTrue(viewModel.uiState.value.isMicPermissionDenied)
        assertFalse(viewModel.uiState.value.isMicPermissionPermanentlyDenied)

        viewModel.dismissMicPermissionNotice()
        assertFalse(viewModel.uiState.value.isMicPermissionDenied)

        viewModel.onMicPermissionResult(isGranted = false, shouldShowRationale = false)
        assertTrue(viewModel.uiState.value.isMicPermissionDenied)
        assertTrue(viewModel.uiState.value.isMicPermissionPermanentlyDenied)

        viewModel.onMicPermissionResult(isGranted = true, shouldShowRationale = false)
        assertFalse(viewModel.uiState.value.isMicPermissionDenied)
        assertFalse(viewModel.uiState.value.isMicPermissionPermanentlyDenied)
    }

    @Test
    fun imageAttachment_withBase64_passesBase64InRequest() = runTest(testDispatcher) {
        val imageUri = "content://media/external/images/99"
        val imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="

        viewModel.onInputTextChange("What is the step in this diagram?")
        viewModel.onImagePreparationCompleted(imageUri, imageBase64, AttachmentSource.GALLERY)

        assertEquals(imageUri, viewModel.uiState.value.selectedAttachmentUri)
        assertEquals(imageBase64, viewModel.uiState.value.selectedAttachmentBase64)

        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeRepository.sentRequests.size)
        val sent = fakeRepository.sentRequests.first()
        assertEquals("What is the step in this diagram?", sent.query)
        assertEquals(imageUri, sent.imageUri)
        assertEquals(imageBase64, sent.imageBase64)
    }

    @Test
    fun newChat_resetsVoiceAndAttachmentState() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Some draft")
        viewModel.onVoiceStateChanged(com.example.meritrankerstudent.util.speech.VoiceState.LISTENING)
        viewModel.onVoicePartialTranscript("Partial voice")
        viewModel.onImagePreparationCompleted("uri://1", "base64", AttachmentSource.CAMERA)

        viewModel.startNewChat()

        val state = viewModel.uiState.value
        assertEquals("", state.inputText)
        assertNull(state.selectedAttachmentUri)
        assertNull(state.selectedAttachmentBase64)
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceState.IDLE, state.voiceState)
        assertNull(state.voicePartialTranscript)
        assertNull(state.voiceErrorMessage)
    }

    @Test
    fun cancelVoice_preservesExistingDraftText() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Explain theorem 12")
        viewModel.onVoiceStateChanged(com.example.meritrankerstudent.util.speech.VoiceState.LISTENING)
        viewModel.onVoicePartialTranscript("and proof")

        viewModel.cancelVoice()

        val state = viewModel.uiState.value
        assertEquals("Explain theorem 12", state.inputText)
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceState.IDLE, state.voiceState)
        assertNull(state.voicePartialTranscript)
    }

    @Test
    fun voicePartialAndFinal_noDuplicationInDraft() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Math doubt:")
        viewModel.onVoiceStateChanged(com.example.meritrankerstudent.util.speech.VoiceState.LISTENING)
        
        // Emitted partial stream
        viewModel.onVoicePartialTranscript("calculate")
        viewModel.onVoicePartialTranscript("calculate speed")
        viewModel.onVoicePartialTranscript("calculate speed and distance")

        // Final recognized transcript
        viewModel.onVoiceFinalTranscript("calculate speed and distance")

        val state = viewModel.uiState.value
        assertEquals("Math doubt: calculate speed and distance", state.inputText)
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceState.IDLE, state.voiceState)
        assertNull(state.voicePartialTranscript)
    }

    @Test
    fun examProfileSelection_updatesIdentityAndSendsAuthoritativeId() = runTest(testDispatcher) {
        val newProfile = com.example.meritrankerstudent.data.model.ExamProfile(
            examProfileId = "RRB_NTPC_CBT_1_2024",
            examId = "RRB_NTPC",
            examName = "RRB NTPC",
            stage = "CBT 1",
            description = "Railway Recruitment Board NTPC",
            sections = emptyList(),
            totalQuestions = 100,
            totalMarks = 100.0,
            totalTimeMinutes = 90,
            active = true
        )

        viewModel.onExamProfileSelected(newProfile)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("RRB NTPC", viewModel.uiState.value.selectedExam)
        assertEquals("RRB_NTPC_CBT_1_2024", viewModel.uiState.value.examProfileId)
        assertEquals("CBT 1", viewModel.uiState.value.examStage)
        assertEquals(newProfile, viewModel.uiState.value.selectedExamProfile)
    }
}

class FakeDoubtRepository : DoubtRepository {
    private val _messages = MutableStateFlow<List<DoubtMessage>>(emptyList())
    override val messages: Flow<List<DoubtMessage>> = _messages

    private val _sessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    override val sessions: Flow<List<ConversationSession>> = _sessions

    val sentRequests = mutableListOf<DoubtRequest>()
    var shouldFail = false
    var currentActiveConvId: String? = null

    override fun setActiveConversation(conversationId: String) {
        currentActiveConvId = conversationId
        _messages.value = emptyList()
    }

    override suspend fun fetchUserSessions(userId: String): List<ConversationSession> = emptyList()
    override suspend fun fetchConversationTurns(userId: String, conversationId: String): List<ConversationTurn> = emptyList()

    override suspend fun sendDoubtQueryStream(
        request: DoubtRequest,
        authToken: String?,
        onChunk: (String) -> Unit
    ): DoubtMessage {
        if (shouldFail) throw RuntimeException("Network error")
        sentRequests.add(request)
        onChunk("Response chunk")
        return DoubtMessage("id_1", "AI", "Response chunk", System.currentTimeMillis())
    }

    override suspend fun clearHistory(userId: String) {}

    override suspend fun reportAssistantResponse(
        messageId: String,
        conversationId: String,
        category: String,
        comment: String?,
        authToken: String?
    ): Result<Unit> = Result.success(Unit)
}

class FakeAuthRepository : AuthRepository {
    override suspend fun isUserSignedIn(): Boolean = true
    override suspend fun getCurrentUserId(): String? = "test_user_123"
    override suspend fun fetchUserEmail(): String? = "student@meritranker.com"
    override suspend fun getAccessToken(): Result<String> = Result.success("mock_token")
    override suspend fun signInWithGoogle(activity: Activity): AuthSignInResult {
        throw NotImplementedError()
    }
    override suspend fun signOut() {}
    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
}

class FakeExamProfileRepository : com.example.meritrankerstudent.data.repository.ExamProfileRepository {
    private val profiles = mutableListOf(
        com.example.meritrankerstudent.data.model.ExamProfile(
            examProfileId = "SSC_CGL#TIER_1",
            examId = "SSC_CGL",
            examName = "SSC CGL",
            stage = "Tier 1",
            description = "Staff Selection Commission Combined Graduate Level",
            sections = emptyList(),
            totalQuestions = 100,
            totalMarks = 200.0,
            totalTimeMinutes = 60,
            active = true
        )
    )

    override fun observeExamProfile(examProfileId: String): Flow<com.example.meritrankerstudent.data.model.ExamProfile?> =
        kotlinx.coroutines.flow.flowOf(profiles.firstOrNull { it.examProfileId == examProfileId })

    override fun observeAllActiveExamProfiles(): Flow<List<com.example.meritrankerstudent.data.model.ExamProfile>> =
        kotlinx.coroutines.flow.flowOf(profiles)

    override suspend fun getExamProfile(examProfileId: String, forceRefresh: Boolean): com.example.meritrankerstudent.data.model.ExamProfile? =
        profiles.firstOrNull { it.examProfileId == examProfileId }

    override suspend fun fetchExamProfileForUser(preparing: String, stage: String?): com.example.meritrankerstudent.data.model.ExamProfile? =
        profiles.firstOrNull { it.examName == preparing }

    override suspend fun listActiveProfilesForExam(examId: String): List<com.example.meritrankerstudent.data.model.ExamProfile> =
        profiles.filter { it.examId == examId }

    override suspend fun listAllActiveExamProfiles(forceRefresh: Boolean): List<com.example.meritrankerstudent.data.model.ExamProfile> =
        profiles

    override fun clearCache() {}
}

class FakeUserProfileRepository : UserProfileRepository {
    private var currentProfile = UserProfile(
        userId = "test_user_123",
        examProfileId = "SSC_CGL#TIER_1",
        examStage = "Tier 1",
        preparing = "SSC CGL",
        language = "en"
    )

    override fun observeUserProfile(userId: String): kotlinx.coroutines.flow.Flow<UserProfile?> {
        return kotlinx.coroutines.flow.flowOf(currentProfile)
    }

    override suspend fun fetchUserProfile(userId: String, forceRefresh: Boolean): UserProfile? {
        return currentProfile
    }

    override suspend fun createUserProfile(userId: String, email: String?, name: String?): UserProfile {
        currentProfile = UserProfile(userId = userId, email = email, name = name)
        return currentProfile
    }

    override suspend fun updateUserProfile(
        userId: String, name: String, preparing: String, examProfileId: String?,
        examStage: String?, additionalExams: List<String>, dateOfBirth: String?, language: String?
    ): UserProfile {
        currentProfile = currentProfile.copy(
            name = name,
            preparing = preparing,
            examProfileId = examProfileId,
            examStage = examStage,
            additionalExams = additionalExams,
            dateOfBirth = dateOfBirth,
            language = language
        )
        return currentProfile
    }

    override suspend fun deleteUserProfile(userId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override fun clearCache() {}
}
