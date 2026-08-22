package com.example.meritrankerstudent.ui.player

import com.example.meritrankerstudent.data.model.*
import com.example.meritrankerstudent.data.repository.PracticeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionPlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeStartPayload = PracticeStartPayload(
        summary = PracticeActivitySummary(
            activityId = "act-1",
            title = "Polity Quiz",
            questionCount = 2
        ),
        attempt = PracticeAttemptPayload(
            attemptId = "att-1",
            activityId = "act-1",
            currentQuestionPosition = 1
        ),
        questions = listOf(
            PracticeQuestionPayload(
                questionId = "q-1",
                position = 1,
                question = "Which article guarantees Right to Equality?",
                options = listOf("Article 14", "Article 19", "Article 21", "Article 32")
            ),
            PracticeQuestionPayload(
                questionId = "q-2",
                position = 2,
                question = "What is the capital of India?",
                options = listOf("Mumbai", "New Delhi", "Kolkata", "Chennai")
            )
        ),
        responses = emptyList()
    )

    private class FakePracticeRepository(
        val startPayload: PracticeStartPayload
    ) : PracticeRepository {
        var checkAnswerCalls = 0
        var saveResponseCalls = 0
        var submitAttemptCalls = 0

        override suspend fun listAvailableActivities(
            examId: String?,
            activityType: PracticeActivityType?,
            subject: String?,
            topic: String?,
            language: String?,
            forceRefresh: Boolean
        ): List<PracticeActivitySummary> = emptyList()

        override suspend fun getPracticeSummary(activityId: String): PracticeActivitySummary = startPayload.summary

        override suspend fun startOrResumeAttempt(activityId: String): PracticeStartPayload = startPayload

        override suspend fun saveResponse(
            attemptId: String,
            questionId: String,
            selectedOption: String?,
            markedForReview: Boolean,
            nextQuestionPosition: Int?,
            currentQuestionPosition: Int?
        ): PracticeResponsePayload {
            saveResponseCalls++
            return PracticeResponsePayload(
                questionId = questionId,
                selectedOption = selectedOption,
                isAnswerLocked = false,
                isMarkedForReview = markedForReview
            )
        }

        override suspend fun checkAnswer(
            attemptId: String,
            questionId: String,
            selectedOption: String
        ): PracticeFeedbackPayload {
            checkAnswerCalls++
            return PracticeFeedbackPayload(
                isCorrect = selectedOption == "Article 14",
                correctAnswer = "Article 14",
                explanation = "Article 14 guarantees equality before law."
            )
        }

        override suspend fun submitAttempt(
            attemptId: String,
            activityId: String
        ): PracticeResultPayload {
            submitAttemptCalls++
            return PracticeResultPayload(
                attemptId = attemptId,
                activityId = activityId,
                score = 2.0,
                maximumScore = 2.0,
                correctCount = 2,
                incorrectCount = 0,
                unansweredCount = 0,
                negativeMarks = 0.0,
                submittedAt = "2026-08-18T10:00:00Z"
            )
        }

        override suspend fun getReview(
            attemptId: String,
            activityId: String
        ): List<PracticeReviewQuestionPayload> = emptyList()

        override fun observePracticeGeneration(testId: String): Flow<PracticeGenerationProgress> = emptyFlow()

        override fun getProgressSummary(): Flow<ProgressSummary> = emptyFlow()
        override fun getQuizzes(): Flow<List<Quiz>> = emptyFlow()
        override fun getMockTests(): Flow<List<MockTest>> = emptyFlow()
        override fun getPyqs(): Flow<List<Pyq>> = emptyFlow()
        override fun getWrongQuestions(): Flow<List<Question>> = emptyFlow()
        override fun getRecommendedNext(): Flow<String> = emptyFlow()
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
    fun loadAttempt_populatesQuestionsAndInitialIndex() = runTest {
        val repo = FakePracticeRepository(fakeStartPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.questions.size)
        assertEquals(0, state.currentIndex)
        assertEquals("q-1", state.questions[0].questionId)
    }

    @Test
    fun selectOption_andCheckAnswer_locksQuestionAndShowsServerFeedback() = runTest {
        val repo = FakePracticeRepository(fakeStartPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        viewModel.selectOption("q-1", "Article 14")
        assertEquals("Article 14", viewModel.uiState.value.selectedOptions["q-1"])

        viewModel.checkAnswer("q-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, repo.checkAnswerCalls)
        assertTrue(state.lockedQuestions["q-1"] == true)
        val feedback = state.feedback["q-1"]
        assertNotNull(feedback)
        assertTrue(feedback!!.isCorrect)
        assertEquals("Article 14", feedback.correctAnswer)

        // Attempting to select another option while locked must be ignored
        viewModel.selectOption("q-1", "Article 19")
        assertEquals("Article 14", viewModel.uiState.value.selectedOptions["q-1"])
    }

    @Test
    fun submitAttempt_invokesBackendFinalizationAndReceivesAuthoritativeScore() = runTest {
        val repo = FakePracticeRepository(fakeStartPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        var submittedResult: PracticeResultPayload? = null
        viewModel.submitAttempt { result ->
            submittedResult = result
        }
        advanceUntilIdle()

        assertEquals(1, repo.submitAttemptCalls)
        assertNotNull(submittedResult)
        assertEquals(2.0, submittedResult!!.score, 0.001)
        assertEquals(100, submittedResult!!.accuracyPercentage)
        assertTrue(viewModel.uiState.value.isSubmitted)
    }

    @Test
    fun duplicateCheck_doesNotCallBackendAgainIfAlreadyLocked() = runTest {
        val repo = FakePracticeRepository(fakeStartPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        viewModel.selectOption("q-1", "Article 14")
        viewModel.checkAnswer("q-1")
        advanceUntilIdle()
        assertEquals(1, repo.checkAnswerCalls)

        // Second tap on Check Answer
        viewModel.checkAnswer("q-1")
        advanceUntilIdle()
        assertEquals(1, repo.checkAnswerCalls) // Must not duplicate
    }

    @Test
    fun duplicateSubmit_invokesBackendFinalizationOnlyOnce() = runTest {
        val repo = FakePracticeRepository(fakeStartPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        viewModel.submitAttempt {}
        viewModel.submitAttempt {}
        advanceUntilIdle()

        assertEquals(1, repo.submitAttemptCalls) // Guarded in-flight and post-submit
    }

    @Test
    fun resumeAttempt_restoresSavedResponsesAndCurrentIndex() = runTest {
        val resumedPayload = fakeStartPayload.copy(
            attempt = fakeStartPayload.attempt.copy(
                currentQuestionPosition = 2
            ),
            responses = listOf(
                PracticeResponsePayload(
                    questionId = "q-1",
                    selectedOption = "Article 14",
                    isAnswerLocked = true,
                    isMarkedForReview = false
                )
            )
        )

        val repo = FakePracticeRepository(resumedPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PlayerMode.RESUME_ATTEMPT, state.playerMode)
        assertEquals(1, state.currentIndex) // 0-indexed position for position=2
        assertEquals("Article 14", state.selectedOptions["q-1"])
        assertTrue(state.lockedQuestions["q-1"] == true)
    }

    @Test
    fun newAttempt_startsCleanWithZeroAnswersAndNewMode() = runTest {
        val newPayload = fakeStartPayload.copy(
            attempt = fakeStartPayload.attempt.copy(
                status = PracticeAttemptStatus.IN_PROGRESS,
                currentQuestionPosition = 1
            ),
            responses = emptyList()
        )

        val repo = FakePracticeRepository(newPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PlayerMode.NEW_ATTEMPT, state.playerMode)
        assertEquals(0, state.currentIndex)
        assertTrue(state.selectedOptions.isEmpty())
        assertTrue(state.lockedQuestions.isEmpty())
        assertFalse(state.isSubmitted)
    }

    @Test
    fun completedAttempt_opensInReviewModeAndDisablesOptionChanges() = runTest {
        val completedPayload = fakeStartPayload.copy(
            attempt = fakeStartPayload.attempt.copy(
                status = PracticeAttemptStatus.SUBMITTED,
                currentQuestionPosition = 2
            ),
            responses = listOf(
                PracticeResponsePayload(
                    questionId = "q-1",
                    selectedOption = "Article 14",
                    isAnswerLocked = true
                ),
                PracticeResponsePayload(
                    questionId = "q-2",
                    selectedOption = "New Delhi",
                    isAnswerLocked = true
                )
            )
        )

        val repo = FakePracticeRepository(completedPayload)
        val viewModel = QuestionPlayerViewModel(repo, "QUIZ", "act-1", startTimer = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PlayerMode.REVIEW_COMPLETED_ATTEMPT, state.playerMode)
        assertTrue(state.isSubmitted)
        assertEquals("Article 14", state.selectedOptions["q-1"])
        assertEquals("New Delhi", state.selectedOptions["q-2"])

        // Attempting to select or check in review mode must be ignored
        viewModel.selectOption("q-1", "Article 19")
        assertEquals("Article 14", viewModel.uiState.value.selectedOptions["q-1"])

        viewModel.checkAnswer("q-1")
        advanceUntilIdle()
        assertEquals(0, repo.checkAnswerCalls)
    }
}
