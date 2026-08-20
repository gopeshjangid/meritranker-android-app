package com.example.meritrankerstudent.ui

import com.example.meritrankerstudent.data.model.ConversationSession
import com.example.meritrankerstudent.data.model.ConversationTurn
import com.example.meritrankerstudent.data.model.DoubtMessage
import com.example.meritrankerstudent.data.model.DoubtRequest
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.DoubtRepository
import com.example.meritrankerstudent.ui.doubt.AskDoubtViewModel
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
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AIContentReportingTest {

    private val testDispatcher = StandardTestDispatcher()

    class FakeDoubtRepository : DoubtRepository {
        var lastReportedMessageId: String? = null
        var lastReportedCategory: String? = null
        var lastReportedComment: String? = null
        var shouldSucceed: Boolean = true

        override val messages: Flow<List<DoubtMessage>> = MutableStateFlow(emptyList())
        override val sessions: Flow<List<ConversationSession>> = MutableStateFlow(emptyList())

        override fun setActiveConversation(conversationId: String) {}
        override suspend fun fetchUserSessions(userId: String): List<ConversationSession> = emptyList()
        override suspend fun fetchConversationTurns(userId: String, conversationId: String): List<ConversationTurn> = emptyList()
        override suspend fun sendDoubtQueryStream(
            request: DoubtRequest,
            authToken: String?,
            onChunk: (String) -> Unit
        ): DoubtMessage = DoubtMessage(id = "msg_1", sender = "AI", text = "Test", timestamp = 0)
        override suspend fun clearHistory(userId: String) {}

        override suspend fun reportAssistantResponse(
            messageId: String,
            conversationId: String,
            category: String,
            comment: String?,
            authToken: String?
        ): Result<Unit> {
            lastReportedMessageId = messageId
            lastReportedCategory = category
            lastReportedComment = comment
            return if (shouldSucceed) Result.success(Unit) else Result.failure(Exception("Network error"))
        }
    }

    class FakeAuthRepository : AuthRepository {
        override suspend fun isUserSignedIn(): Boolean = true
        override suspend fun getCurrentUserId(): String? = "student_123"
        override suspend fun fetchUserEmail(): String? = "student@example.com"
        override suspend fun getAccessToken(): Result<String> = Result.success("mock_access_token")
        override suspend fun signInWithGoogle(activity: android.app.Activity): com.amplifyframework.auth.result.AuthSignInResult = mock()
        override suspend fun signOut() {}
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOpenAndDismissReportDialog() {
        val fakeRepo = FakeDoubtRepository()
        val viewModel = AskDoubtViewModel(
            repository = fakeRepo,
            authRepository = FakeAuthRepository()
        )

        assertNull(viewModel.uiState.value.reportingMessageId)
        viewModel.openReportDialog("ai_msg_99")
        assertEquals("ai_msg_99", viewModel.uiState.value.reportingMessageId)

        viewModel.dismissReportDialog()
        assertNull(viewModel.uiState.value.reportingMessageId)
    }

    @Test
    fun testSubmitContentReport_success() = runTest(testDispatcher) {
        val fakeRepo = FakeDoubtRepository().apply { shouldSucceed = true }
        val viewModel = AskDoubtViewModel(
            repository = fakeRepo,
            authRepository = FakeAuthRepository()
        )

        viewModel.openReportDialog("ai_msg_42")
        viewModel.submitContentReport(
            messageId = "ai_msg_42",
            category = "Incorrect or misleading",
            comment = "The formula in step 2 is wrong"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("ai_msg_42", fakeRepo.lastReportedMessageId)
        assertEquals("Incorrect or misleading", fakeRepo.lastReportedCategory)
        assertEquals("The formula in step 2 is wrong", fakeRepo.lastReportedComment)
        assertFalse(viewModel.uiState.value.isReporting)
        assertEquals("Thanks. Your report has been submitted.", viewModel.uiState.value.reportSuccessMessage)
        assertNull(viewModel.uiState.value.reportingMessageId)
    }

    @Test
    fun testSubmitContentReport_failure() = runTest(testDispatcher) {
        val fakeRepo = FakeDoubtRepository().apply { shouldSucceed = false }
        val viewModel = AskDoubtViewModel(
            repository = fakeRepo,
            authRepository = FakeAuthRepository()
        )

        viewModel.openReportDialog("ai_msg_42")
        viewModel.submitContentReport(
            messageId = "ai_msg_42",
            category = "Inappropriate content",
            comment = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isReporting)
        assertEquals("We couldn't submit your report. Please try again.", viewModel.uiState.value.reportErrorMessage)
        assertNull(viewModel.uiState.value.reportSuccessMessage)
    }
}
