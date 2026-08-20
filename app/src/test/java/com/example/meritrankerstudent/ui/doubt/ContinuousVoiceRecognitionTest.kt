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
import com.example.meritrankerstudent.util.speech.SpeechRecognitionManager
import com.example.meritrankerstudent.util.speech.VoiceState
import com.example.meritrankerstudent.util.speech.VoiceTranscriptCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ContinuousVoiceRecognitionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeContinuousDoubtRepository
    private lateinit var fakeAuthRepository: FakeContinuousAuthRepository
    private lateinit var fakeUserProfileRepository: FakeContinuousUserProfileRepository
    private lateinit var viewModel: AskDoubtViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeContinuousDoubtRepository()
        fakeAuthRepository = FakeContinuousAuthRepository()
        fakeUserProfileRepository = FakeContinuousUserProfileRepository()
        viewModel = AskDoubtViewModel(fakeRepository, fakeAuthRepository, fakeUserProfileRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_voiceModeIsIdle() {
        val state = viewModel.uiState.value
        assertFalse(state.isVoiceModeActive)
        assertEquals(VoiceState.IDLE, state.voiceState)
        assertNull(state.voicePartialTranscript)
        assertNull(state.voiceErrorMessage)
    }

    @Test
    fun testStartVoiceMode_capturesBaseTextAndActivatesVoiceMode() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Previous typed text")
        viewModel.startVoiceMode()

        val state = viewModel.uiState.value
        assertTrue(state.isVoiceModeActive)
        assertEquals(VoiceState.LISTENING, state.voiceState)
        assertEquals("Previous typed text", state.inputText)
        assertNull(state.voicePartialTranscript)
    }

    @Test
    fun testPartialTranscript_previewsWithoutOverwritingBaseText() = runTest(testDispatcher) {
        viewModel.onInputTextChange("Solve:")
        viewModel.startVoiceMode()

        viewModel.onVoicePartialTranscript("how to find")
        assertEquals("Solve: How to find", viewModel.uiState.value.inputText)
        assertEquals("How to find", viewModel.uiState.value.voicePartialTranscript)

        viewModel.onVoicePartialTranscript("how to find the roots")
        assertEquals("Solve: How to find the roots", viewModel.uiState.value.inputText)
        assertEquals("How to find the roots", viewModel.uiState.value.voicePartialTranscript)
    }

    @Test
    fun testFinalTranscript_normalCompletionDoesNotDisableVoiceMode() = runTest(testDispatcher) {
        viewModel.startVoiceMode()

        // Session 1 completes (cleanTranscript appends '?' for direct questions starting with 'How')
        viewModel.onVoiceFinalTranscript("How can I solve percentage questions quickly")

        val state = viewModel.uiState.value
        assertTrue(state.isVoiceModeActive)
        assertEquals(VoiceState.LISTENING, state.voiceState)
        assertEquals("How can I solve percentage questions quickly?", state.inputText)
        assertNull(state.voicePartialTranscript)
    }

    @Test
    fun testMultiSessionAppend_appendsAcrossNaturalPausesWithCleanSpacing() = runTest(testDispatcher) {
        viewModel.startVoiceMode()

        // First sentence spoken
        viewModel.onVoiceFinalTranscript("First part of my doubt")
        assertEquals("First part of my doubt", viewModel.uiState.value.inputText)

        // Recognizer restarts for sentence 2, student speaks more
        viewModel.onVoicePartialTranscript("and here is part two")
        assertEquals("First part of my doubt And here is part two", viewModel.uiState.value.inputText)

        // Sentence 2 finalizes
        viewModel.onVoiceFinalTranscript("and here is part two")
        assertEquals("First part of my doubt and here is part two", viewModel.uiState.value.inputText)
    }

    @Test
    fun testExplicitDone_disablesVoiceModeAndKeepsComposedTextEditable() = runTest(testDispatcher) {
        viewModel.startVoiceMode()
        viewModel.onVoiceFinalTranscript("Explain Article 14 of Indian Constitution")

        // Student taps Done button
        viewModel.stopVoiceMode()

        val state = viewModel.uiState.value
        assertFalse(state.isVoiceModeActive)
        assertEquals(VoiceState.IDLE, state.voiceState)
        assertEquals("Explain Article 14 of Indian Constitution", state.inputText)
        assertNull(state.voicePartialTranscript)
        assertEquals(0, fakeRepository.sentRequests.size) // Proves Done does NOT auto-send!
    }

    @Test
    fun testExplicitCancel_discardsAllVoiceTranscriptAndRestoresBaseText() = runTest(testDispatcher) {
        // Initial typed draft before starting voice
        viewModel.onInputTextChange("Explain this question:")
        viewModel.startVoiceMode()

        // Student speaks for a while
        viewModel.onVoicePartialTranscript("percentage ka formula kya hai")
        assertEquals("Explain this question: Percentage ka formula kya hai", viewModel.uiState.value.inputText)

        viewModel.onVoiceFinalTranscript("what is compound interest")
        assertEquals("Explain this question: what is compound interest?", viewModel.uiState.value.inputText)

        // Student taps Cancel button
        viewModel.cancelVoice()

        val state = viewModel.uiState.value
        assertFalse(state.isVoiceModeActive)
        assertEquals(VoiceState.IDLE, state.voiceState)
        assertEquals("Explain this question:", state.inputText) // EXACT rollback!
        assertNull(state.voicePartialTranscript)
        assertEquals(0, fakeRepository.sentRequests.size) // Proves Cancel does NOT auto-send!
    }

    @Test
    fun testSendMessage_whileListening_stopsVoiceModeAndDispatchesMessage() = runTest(testDispatcher) {
        viewModel.startVoiceMode()
        viewModel.onVoiceFinalTranscript("What is Pythagoras theorem")

        // Student taps Send while listening
        viewModel.sendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isVoiceModeActive)
        assertEquals(VoiceState.IDLE, state.voiceState)
        assertEquals(1, fakeRepository.sentRequests.size)
        assertEquals("What is Pythagoras theorem?", fakeRepository.sentRequests.first().query)
    }

    @Test
    fun testRecoverableError_retainsVoiceModeForContinuousSpeech() = runTest(testDispatcher) {
        viewModel.startVoiceMode()
        viewModel.onVoicePartialTranscript("What is the capital")

        // State changes to processing between utterances
        viewModel.onVoiceStateChanged(VoiceState.PROCESSING)
        assertTrue(viewModel.uiState.value.isVoiceModeActive)

        // Reconnects to listening
        viewModel.onVoiceStateChanged(VoiceState.LISTENING)
        assertTrue(viewModel.uiState.value.isVoiceModeActive)
    }

    @Test
    fun testTerminalVoiceError_displaysErrorAndExitsVoiceMode() = runTest(testDispatcher) {
        viewModel.startVoiceMode()
        viewModel.onVoiceError("Voice input stopped. Tap the mic to try again.")

        val state = viewModel.uiState.value
        assertFalse(state.isVoiceModeActive)
        assertEquals(VoiceState.ERROR, state.voiceState)
        assertEquals("Voice input stopped. Tap the mic to try again.", state.voiceErrorMessage)

        viewModel.dismissVoiceError()
        assertEquals(VoiceState.IDLE, viewModel.uiState.value.voiceState)
        assertNull(viewModel.uiState.value.voiceErrorMessage)
    }

    @Test
    fun testLanguageResolution_andTranscriptCleaning() {
        assertEquals("en-IN", SpeechRecognitionManager.resolveLanguageCode("en"))
        assertEquals("en-IN", SpeechRecognitionManager.resolveLanguageCode("English"))
        assertEquals("hi-IN", SpeechRecognitionManager.resolveLanguageCode("hi"))
        assertEquals("hi-IN", SpeechRecognitionManager.resolveLanguageCode("Hindi"))
        assertEquals("hi-IN", SpeechRecognitionManager.resolveLanguageCode("Hinglish"))

        // Basic trimming and preview
        assertEquals("Hello world", VoiceTranscriptCleaner.cleanTranscript("  Hello   world  ", "en"))
        assertEquals("Testing hindi...", VoiceTranscriptCleaner.cleanPreview(" Testing hindi... ", "en"))
    }

    @Test
    fun testSelectBestCandidate_prioritizesConfidenceAndUtteranceLength() {
        val candidatesEn = listOf(
            "c",
            "what is compound interest formula",
            "uh compund rest formula"
        )
        val bestEn = VoiceTranscriptCleaner.selectBestCandidate(candidatesEn, floatArrayOf(0.1f, 0.9f, 0.4f), "en")
        assertEquals("what is compound interest formula?", bestEn)

        val candidatesHi = listOf(
            "p",
            "प्रतिशत के सवाल कैसे हल करें"
        )
        val bestHi = VoiceTranscriptCleaner.selectBestCandidate(candidatesHi, floatArrayOf(0.1f, 0.9f, 0.4f), "hi")
        assertEquals("प्रतिशत के सवाल कैसे हल करें?", bestHi)
    }

    @Test
    fun testPreservesExactScript_noArtificialTranslationOrTransliteration() {
        // Hindi speech remains pure Devanagari
        val hiText = "प्रतिशत के सवाल जल्दी कैसे हल करें"
        assertEquals("प्रतिशत के सवाल जल्दी कैसे हल करें?", VoiceTranscriptCleaner.cleanTranscript(hiText, "hi"))

        // English speech remains pure English
        val enText = "explain percentage using a simple example"
        assertEquals("explain percentage using a simple example", VoiceTranscriptCleaner.cleanTranscript(enText, "en"))

        // Natural mixed Hinglish speech remains preserved without artificial rewriting
        val mixedText = "percentage ka formula kya hai"
        assertEquals("percentage ka formula kya hai?", VoiceTranscriptCleaner.cleanTranscript(mixedText, "en"))
    }

    @Test
    fun testVoiceLanguageModeSelection_updatesModeAndKeepsComposerEditable() = runTest(testDispatcher) {
        viewModel.onVoiceLanguageModeSelected(com.example.meritrankerstudent.util.speech.VoiceLanguageMode.HI)
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceLanguageMode.HI, viewModel.uiState.value.voiceLanguageMode)

        viewModel.onVoiceLanguageModeSelected(com.example.meritrankerstudent.util.speech.VoiceLanguageMode.EN)
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceLanguageMode.EN, viewModel.uiState.value.voiceLanguageMode)

        viewModel.onVoiceLanguageModeSelected(com.example.meritrankerstudent.util.speech.VoiceLanguageMode.AUTO)
        assertEquals(com.example.meritrankerstudent.util.speech.VoiceLanguageMode.AUTO, viewModel.uiState.value.voiceLanguageMode)
    }

    @Test
    fun testMultiUtteranceHindiTranscription_preservesDevanagariAccurately() = runTest(testDispatcher) {
        viewModel.startVoiceMode()

        viewModel.onVoiceFinalTranscript("प्रतिशत के सवाल जल्दी कैसे हल करें")
        assertEquals("प्रतिशत के सवाल जल्दी कैसे हल करें?", viewModel.uiState.value.inputText)

        viewModel.onVoiceFinalTranscript("और चक्रवृद्धि ब्याज का आसान तरीका बताइए")
        assertEquals("प्रतिशत के सवाल जल्दी कैसे हल करें? और चक्रवृद्धि ब्याज का आसान तरीका बताइए", viewModel.uiState.value.inputText)
    }
}

// Fakes for ContinuousVoiceRecognitionTest
private class FakeContinuousDoubtRepository : DoubtRepository {
    private val _messages = MutableStateFlow<List<DoubtMessage>>(emptyList())
    override val messages: Flow<List<DoubtMessage>> = _messages

    private val _sessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    override val sessions: Flow<List<ConversationSession>> = _sessions

    val sentRequests = mutableListOf<DoubtRequest>()

    override fun setActiveConversation(conversationId: String) {
        _messages.value = emptyList()
    }

    override suspend fun fetchUserSessions(userId: String): List<ConversationSession> = emptyList()
    override suspend fun fetchConversationTurns(userId: String, conversationId: String): List<ConversationTurn> = emptyList()

    override suspend fun sendDoubtQueryStream(
        request: DoubtRequest,
        authToken: String?,
        onChunk: (String) -> Unit
    ): DoubtMessage {
        sentRequests.add(request)
        onChunk("Echo response")
        return DoubtMessage(
            id = "msg-${UUID.randomUUID()}",
            sender = "AI",
            text = "Echo response",
            timestamp = System.currentTimeMillis()
        )
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

private class FakeContinuousAuthRepository : AuthRepository {
    override suspend fun isUserSignedIn(): Boolean = true
    override suspend fun getCurrentUserId(): String? = "user_123"
    override suspend fun fetchUserEmail(): String? = "student@meritranker.com"
    override suspend fun getAccessToken(): Result<String> = Result.success("mock_token")
    override suspend fun signInWithGoogle(activity: Activity): AuthSignInResult = throw NotImplementedError()
    override suspend fun signOut() {}
    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
}

private class FakeContinuousUserProfileRepository : UserProfileRepository {
    override fun observeUserProfile(userId: String): Flow<UserProfile?> {
        return flowOf(
            UserProfile(
                userId = userId,
                examProfileId = "SSC_CGL#TIER_1",
                examStage = "TIER_1",
                preparing = "SSC CGL",
                language = "en"
            )
        )
    }

    override suspend fun fetchUserProfile(userId: String, forceRefresh: Boolean): UserProfile? {
        return UserProfile(
            userId = userId,
            examProfileId = "SSC_CGL#TIER_1",
            examStage = "TIER_1",
            preparing = "SSC CGL",
            language = "en"
        )
    }

    override suspend fun createUserProfile(userId: String, email: String?, name: String?): UserProfile = throw NotImplementedError()

    override suspend fun updateUserProfile(
        userId: String,
        name: String,
        preparing: String,
        examProfileId: String?,
        examStage: String?,
        additionalExams: List<String>,
        dateOfBirth: String?,
        language: String?
    ): UserProfile = throw NotImplementedError()

    override suspend fun deleteUserProfile(userId: String): Result<Unit> = Result.success(Unit)
    override fun clearCache() {}
}
