package com.example.meritrankerstudent.ui.progress

import com.example.meritrankerstudent.data.model.*
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.StudentPerformanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepository(val userId: String = "user_123") : AuthRepository {
        override suspend fun isUserSignedIn(): Boolean = true
        override suspend fun getCurrentUserId(): String = userId
        override suspend fun fetchUserEmail(): String = "test@example.com"
        override suspend fun getAccessToken(): Result<String> = Result.success("fake_token")
        override suspend fun signInWithGoogle(activity: android.app.Activity): com.amplifyframework.auth.result.AuthSignInResult = error("Not needed")
        override suspend fun signOut() {}
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
    }

    private class FakePerformanceRepository(
        var responseResult: Result<StudentPerformanceResponse>
    ) : StudentPerformanceRepository {
        override fun observePerformance(
            examProfileId: String,
            view: GetStudentPerformanceView,
            subjectId: String?
        ): kotlinx.coroutines.flow.Flow<StudentPerformanceResponse?> {
            return kotlinx.coroutines.flow.flowOf(responseResult.getOrNull())
        }

        override suspend fun getPerformance(
            examProfileId: String,
            view: GetStudentPerformanceView,
            subjectId: String?,
            forceRefresh: Boolean
        ): Result<StudentPerformanceResponse> = responseResult
    }

    @Test
    fun loadPerformance_emitsContentState_whenEvidenceExists() = runTest {
        val sampleResponse = StudentPerformanceResponse(
            overall = StudentPerformanceOverall(
                attempted = 10,
                correct = 8,
                wrong = 2,
                accuracy = 80.0,
                label = "Strong",
                comment = "Strong accuracy."
            ),
            items = listOf(
                StudentPerformanceItem(
                    subjectId = "QUANT",
                    subjectName = "Quantitative Aptitude",
                    attempted = 10,
                    correct = 8,
                    wrong = 2,
                    accuracy = 80.0,
                    label = "Strong",
                    comment = "Strong accuracy."
                )
            ),
            isUpdatingLatestPerformance = false
        )

        val fakeRepo = FakePerformanceRepository(Result.success(sampleResponse))
        val fakeAuth = FakeAuthRepository()
        val viewModel = ProgressViewModel(
            repository = fakeRepo,
            authRepository = fakeAuth,
            localProfileStore = null,
            initialExamProfileId = "exam_123"
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected ProgressUiState.Content but got $state", state is ProgressUiState.Content)
        val content = state as ProgressUiState.Content
        assertEquals(80.0, content.response.overall.accuracy, 0.01)
        assertEquals("Strong", content.response.overall.label)
        assertEquals(1, content.response.items.size)
        assertFalse(content.isRefreshing)
    }

    @Test
    fun loadPerformance_emitsEmptyState_whenNoAttemptEvidence() = runTest {
        val emptyResponse = StudentPerformanceResponse(
            overall = StudentPerformanceOverall(
                attempted = 0,
                correct = 0,
                wrong = 0,
                accuracy = 0.0,
                label = "Not enough practice",
                comment = "Practice a few more questions to understand your level."
            ),
            items = emptyList(),
            insights = emptyList(),
            isUpdatingLatestPerformance = false
        )

        val fakeRepo = FakePerformanceRepository(Result.success(emptyResponse))
        val fakeAuth = FakeAuthRepository()
        val viewModel = ProgressViewModel(
            repository = fakeRepo,
            authRepository = fakeAuth,
            localProfileStore = null,
            initialExamProfileId = "exam_123"
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected ProgressUiState.Empty but got $state", state is ProgressUiState.Empty)
        val empty = state as ProgressUiState.Empty
        assertTrue(empty.message.contains("Your progress will appear here"))
    }

    @Test
    fun subjectProgressViewModel_staleRequestCancellationGuard() = runTest {
        val quantResponse = StudentPerformanceResponse(
            overall = StudentPerformanceOverall(10, 8, 2, null, 80.0, "Strong", "Strong"),
            items = listOf(
                StudentPerformanceItem("QUANT", "Quantitative Aptitude", 10, 8, 2, null, 80.0, "Strong", "Strong")
            ),
            insights = listOf(
                StudentPerformanceInsight("QUANT", "att_1", "q_1", "Descriptor 1", "G", "F", null, "CORRECT", "PRACTICE", "2026-08-18")
            )
        )

        val fakeRepo = FakePerformanceRepository(Result.success(quantResponse))
        val subjectViewModel = SubjectProgressViewModel(repository = fakeRepo)

        subjectViewModel.loadSubjectPerformance("exam_123", "QUANT", GetStudentPerformanceView.PRACTICE)
        advanceUntilIdle()

        val state = subjectViewModel.uiState.value
        assertTrue(state is SubjectProgressUiState.Content)
        val content = state as SubjectProgressUiState.Content
        assertEquals("QUANT", content.subjectItem?.subjectId)
        assertEquals(1, content.insights.size)
    }
}
