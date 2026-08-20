package com.example.meritrankerstudent.data.coordinator

import android.app.Activity
import com.amplifyframework.auth.result.AuthSignInResult
import com.example.meritrankerstudent.data.model.*
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.PracticeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeGenerationCoordinatorTest {

    private lateinit var fakePracticeRepo: FakePracticeRepository
    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var coordinator: PracticeGenerationCoordinator

    @Before
    fun setUp() {
        fakePracticeRepo = FakePracticeRepository()
        fakeAuthRepo = FakeAuthRepository()
        coordinator = PracticeGenerationCoordinator(
            context = null,
            practiceRepository = fakePracticeRepo,
            authRepository = fakeAuthRepo,
            database = null,
            notificationManager = null
        )
    }

    @Test
    fun trackGeneration_initializesStateAndIncrementsActiveCount() = runTest {
        coordinator.trackGeneration(
            testId = "test_101",
            conversationId = "conv_1",
            turnId = "turn_1",
            initialTitle = "Algebra Practice",
            initialType = "QUIZ",
            questionCount = 5
        )

        val task = coordinator.tasks.value["test_101"]
        assertNotNull(task)
        assertEquals("test_101", task?.testId)
        assertEquals("Algebra Practice", task?.title)
        assertEquals("QUIZ", task?.activityType)
        assertEquals(5, task?.questionCount)
        assertEquals(PracticeGenerationStatus.GENERATING, task?.status)
        assertEquals(1, coordinator.activeCount.value)
    }

    @Test
    fun reconcileAuthoritativeSummary_updatesTaskToReady() = runTest {
        fakePracticeRepo.setSummary(
            "test_101",
            PracticeActivitySummary(
                activityId = "test_101",
                title = "Algebra Practice",
                activityType = PracticeActivityType.QUIZ,
                questionCount = 5,
                readyCount = 5,
                progressPercent = 100,
                playable = true,
                generationStatus = "READY"
            )
        )

        coordinator.trackGeneration("test_101")
        coordinator.reconcileAuthoritativeSummary("test_101")

        val task = coordinator.tasks.value["test_101"]
        assertNotNull(task)
        assertEquals(PracticeGenerationStatus.READY, task?.status)
        assertTrue(task?.playable == true)
        assertEquals(5, task?.readyCount)
        assertEquals(0, coordinator.activeCount.value)
        assertEquals("test_101", coordinator.latestReadyTask.value?.testId)
    }

    @Test
    fun monotonicStateProtection_doesNotRegressFromReadyToGenerating() = runTest {
        fakePracticeRepo.setSummary(
            "test_101",
            PracticeActivitySummary(
                activityId = "test_101",
                title = "Algebra Practice",
                activityType = PracticeActivityType.QUIZ,
                questionCount = 5,
                readyCount = 5,
                progressPercent = 100,
                playable = true,
                generationStatus = "READY"
            )
        )

        coordinator.trackGeneration("test_101")
        coordinator.reconcileAuthoritativeSummary("test_101")
        assertEquals(PracticeGenerationStatus.READY, coordinator.tasks.value["test_101"]?.status)

        // Attempt to track or apply an older GENERATING progress event
        fakePracticeRepo.setSummary(
            "test_101",
            PracticeActivitySummary(
                activityId = "test_101",
                title = "Algebra Practice",
                activityType = PracticeActivityType.QUIZ,
                questionCount = 5,
                readyCount = 2,
                progressPercent = 40,
                playable = false,
                generationStatus = "GENERATING"
            )
        )
        coordinator.reconcileAuthoritativeSummary("test_101")

        // Status must remain READY
        assertEquals(PracticeGenerationStatus.READY, coordinator.tasks.value["test_101"]?.status)
        assertTrue(coordinator.tasks.value["test_101"]?.playable == true)
    }

    @Test
    fun clearUserGenerations_resetsAllTasksAndActiveCount() = runTest {
        coordinator.trackGeneration("test_101")
        coordinator.trackGeneration("test_102")
        assertEquals(2, coordinator.activeCount.value)

        coordinator.clearUserGenerations()
        assertTrue(coordinator.tasks.value.isEmpty())
        assertEquals(0, coordinator.activeCount.value)
        assertNull(coordinator.latestReadyTask.value)
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun isUserSignedIn(): Boolean = true
        override suspend fun fetchUserEmail(): String? = "student@meritranker.com"
        override suspend fun getAccessToken(): Result<String> = Result.success("valid_test_token")
        override suspend fun getCurrentUserId(): String? = "test_user_id"
        override suspend fun signOut() {}
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun signInWithGoogle(activity: Activity): AuthSignInResult = throw NotImplementedError()
    }

    private class FakePracticeRepository : PracticeRepository {
        private val summaries = mutableMapOf<String, PracticeActivitySummary>()
        val progressFlow = MutableSharedFlow<PracticeGenerationProgress>()

        fun setSummary(id: String, summary: PracticeActivitySummary) {
            summaries[id] = summary
        }

        override suspend fun getPracticeSummary(activityId: String): PracticeActivitySummary {
            return summaries[activityId] ?: PracticeActivitySummary(
                activityId = activityId,
                title = "Default Quiz",
                activityType = PracticeActivityType.QUIZ,
                questionCount = 5,
                generationStatus = "GENERATING"
            )
        }

        override fun observePracticeGeneration(testId: String): Flow<PracticeGenerationProgress> {
            return progressFlow
        }

        override suspend fun listAvailableActivities(
            examId: String?,
            activityType: PracticeActivityType?,
            subject: String?,
            topic: String?,
            language: String?,
            forceRefresh: Boolean
        ): List<PracticeActivitySummary> = emptyList()

        override suspend fun startOrResumeAttempt(activityId: String): PracticeStartPayload = throw NotImplementedError()
        override suspend fun saveResponse(attemptId: String, questionId: String, selectedOption: String?, markedForReview: Boolean, nextQuestionPosition: Int?, currentQuestionPosition: Int?): PracticeResponsePayload = throw NotImplementedError()
        override suspend fun checkAnswer(attemptId: String, questionId: String, selectedOption: String): PracticeFeedbackPayload = throw NotImplementedError()
        override suspend fun submitAttempt(attemptId: String, activityId: String): PracticeResultPayload = throw NotImplementedError()
        override suspend fun getReview(attemptId: String, activityId: String): List<PracticeReviewQuestionPayload> = emptyList()
        override fun getProgressSummary(): Flow<ProgressSummary> = flowOf(ProgressSummary(0, "", "", 0))
        override fun getQuizzes(): Flow<List<Quiz>> = flowOf(emptyList())
        override fun getMockTests(): Flow<List<MockTest>> = flowOf(emptyList())
        override fun getPyqs(): Flow<List<Pyq>> = flowOf(emptyList())
        override fun getWrongQuestions(): Flow<List<Question>> = flowOf(emptyList())
        override fun getRecommendedNext(): Flow<String> = flowOf("")
    }
}
