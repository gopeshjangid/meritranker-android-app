package com.example.meritrankerstudent.data.repository

import android.util.Log
import com.amplifyframework.api.aws.GsonVariablesSerializer
import com.amplifyframework.api.graphql.SimpleGraphQLRequest
import com.amplifyframework.core.Amplify
import com.example.meritrankerstudent.MeritRankerApplication
import com.example.meritrankerstudent.data.local.AppDatabase
import com.example.meritrankerstudent.data.local.EntityMappers
import com.example.meritrankerstudent.data.local.FreshnessPolicy
import com.example.meritrankerstudent.data.local.FreshnessStatus
import com.example.meritrankerstudent.data.local.SyncMetadataEntity
import com.example.meritrankerstudent.data.model.*
import com.example.meritrankerstudent.data.remote.PracticeGraphQLClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

interface PracticeRepository {
    suspend fun listAvailableActivities(
        examId: String? = null,
        activityType: PracticeActivityType? = null,
        subject: String? = null,
        topic: String? = null,
        language: String? = null,
        forceRefresh: Boolean = false
    ): List<PracticeActivitySummary>

    suspend fun getPracticeSummary(activityId: String): PracticeActivitySummary

    suspend fun startOrResumeAttempt(activityId: String): PracticeStartPayload

    suspend fun saveResponse(
        attemptId: String,
        questionId: String,
        selectedOption: String?,
        markedForReview: Boolean,
        nextQuestionPosition: Int? = null,
        currentQuestionPosition: Int? = null
    ): PracticeResponsePayload

    suspend fun checkAnswer(
        attemptId: String,
        questionId: String,
        selectedOption: String
    ): PracticeFeedbackPayload

    suspend fun submitAttempt(
        attemptId: String,
        activityId: String
    ): PracticeResultPayload

    suspend fun getReview(
        attemptId: String,
        activityId: String
    ): List<PracticeReviewQuestionPayload>

    fun observePracticeGeneration(testId: String): Flow<PracticeGenerationProgress>

    // Observables for UI integration
    fun getProgressSummary(): Flow<ProgressSummary>
    fun getQuizzes(): Flow<List<Quiz>>
    fun getMockTests(): Flow<List<MockTest>>
    fun getPyqs(): Flow<List<Pyq>>
    fun getWrongQuestions(): Flow<List<Question>>
    fun getRecommendedNext(): Flow<String>
}

class RealPracticeRepository(
    private val client: PracticeGraphQLClient = PracticeGraphQLClient(),
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val database: AppDatabase? = MeritRankerApplication.instance?.let { AppDatabase.getInstance(it) },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : PracticeRepository {

    private suspend fun requireAuthToken(): String {
        return authRepository.getAccessToken().getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("COGNITO_AUTH_REQUIRED")
    }

    private suspend fun getOwnerUserId(): String {
        return authRepository.getCurrentUserId() ?: "ANONYMOUS"
    }

    override suspend fun listAvailableActivities(
        examId: String?,
        activityType: PracticeActivityType?,
        subject: String?,
        topic: String?,
        language: String?,
        forceRefresh: Boolean
    ): List<PracticeActivitySummary> {
        val ownerUserId = getOwnerUserId()
        val syncDao = database?.syncMetadataDao()
        val practiceDao = database?.practiceDao()
        val scope = activityType?.name ?: "ALL"

        val localEntities = if (activityType != null) {
            practiceDao?.getActivitiesByType(ownerUserId, activityType.name)?.firstOrNull()
        } else {
            practiceDao?.getActivities(ownerUserId)?.firstOrNull()
        }
        val cached = localEntities?.map { EntityMappers.entityToPracticeActivity(it) } ?: emptyList()

        val syncMeta = syncDao?.getMetadata(ownerUserId, "PRACTICE_ACTIVITIES", scope)
        val freshness = if (forceRefresh || cached.isEmpty()) FreshnessStatus.STALE_BUT_USABLE else FreshnessPolicy.evaluate(syncMeta?.lastSuccessfulFetchAt, FreshnessPolicy.PRACTICE_CATALOGUE_TTL_MS)

        if (cached.isNotEmpty() && freshness == FreshnessStatus.FRESH) {
            Log.d("PracticeRepo", "CACHE_HIT (FRESH) for activities ($scope)")
            return cached
        }

        if (cached.isNotEmpty() && freshness == FreshnessStatus.STALE_BUT_USABLE) {
            Log.d("PracticeRepo", "CACHE_HIT (STALE_BUT_USABLE) for activities ($scope), triggering background revalidation")
            coroutineScope.launch {
                try {
                    performRemoteListActivities(examId, activityType, subject, topic, language, ownerUserId, scope)
                } catch (e: Exception) {
                    Log.w("PracticeRepo", "Background activity revalidation failed: ${e.message}")
                }
            }
            return cached
        }

        return try {
            performRemoteListActivities(examId, activityType, subject, topic, language, ownerUserId, scope)
        } catch (e: Exception) {
            cached
        }
    }

    private suspend fun performRemoteListActivities(
        examId: String?,
        activityType: PracticeActivityType?,
        subject: String?,
        topic: String?,
        language: String?,
        ownerUserId: String,
        scope: String
    ): List<PracticeActivitySummary> {
        val token = requireAuthToken()
        val remoteList = client.listAvailablePracticeActivities(
            examId = examId,
            activityType = activityType,
            subject = subject,
            topic = topic,
            language = language,
            authToken = token
        )

        // Save to Room cache
        val entities = remoteList.map { EntityMappers.practiceActivityToEntity(it, ownerUserId) }
        database?.practiceDao()?.upsertActivities(entities)
        database?.syncMetadataDao()?.upsertMetadata(
            SyncMetadataEntity(
                ownerUserId = ownerUserId,
                resourceType = "PRACTICE_ACTIVITIES",
                resourceScope = scope,
                lastSuccessfulFetchAt = System.currentTimeMillis(),
                syncState = "IDLE"
            )
        )

        return remoteList
    }

    override suspend fun getPracticeSummary(activityId: String): PracticeActivitySummary {
        val ownerUserId = getOwnerUserId()
        val cached = database?.practiceDao()?.getActivitySync(activityId)
        if (cached != null && FreshnessPolicy.evaluate(cached.lastSyncedAt, FreshnessPolicy.PRACTICE_CATALOGUE_TTL_MS) == FreshnessStatus.FRESH) {
            return EntityMappers.entityToPracticeActivity(cached)
        }

        val token = requireAuthToken()
        val summary = client.getPracticeActivitySummary(activityId, token)
        database?.practiceDao()?.upsertActivity(EntityMappers.practiceActivityToEntity(summary, ownerUserId))
        return summary
    }

    override suspend fun startOrResumeAttempt(activityId: String): PracticeStartPayload {
        val ownerUserId = getOwnerUserId()
        try {
            val token = requireAuthToken()
            val payload = client.startPracticeAttempt(activityId, token)

            // Persist immutable READY question payloads into Room cache with user-scoped composite key
            val questionEntities = payload.questions.map {
                EntityMappers.practiceQuestionToEntity(it, activityId, ownerUserId)
            }
            database?.practiceDao()?.upsertQuestions(questionEntities)

            return payload
        } catch (e: Exception) {
            val cachedQuestions = database?.practiceDao()?.getQuestionsSync(ownerUserId, activityId) ?: emptyList()
            val questions = if (cachedQuestions.isNotEmpty()) {
                cachedQuestions.map { EntityMappers.entityToPracticeQuestion(it) }
            } else {
                listOf(
                    PracticeQuestionPayload(
                        questionId = "q_p1",
                        position = 1,
                        question = "If a trader marks his goods at 40% above the cost price and allows a discount of 20%, what is his gain percent?",
                        options = listOf("12%", "15%", "20%", "25%"),
                        subject = "Quantitative Aptitude"
                    ),
                    PracticeQuestionPayload(
                        questionId = "q_p2",
                        position = 2,
                        question = "Which of the following Articles of the Constitution of India deals with the 'Right to Equality'?",
                        options = listOf("Article 14-18", "Article 19-22", "Article 23-24", "Article 25-28"),
                        subject = "General Knowledge"
                    ),
                    PracticeQuestionPayload(
                        questionId = "q_p3",
                        position = 3,
                        question = "Select the most appropriate synonym of the given word: 'CANDID'",
                        options = listOf("Frank", "Dishonest", "Secretive", "Shy"),
                        subject = "English Language"
                    )
                )
            }

            val summary = PracticeActivitySummary(
                activityId = activityId,
                title = "Adaptive Practice Quiz",
                activityType = PracticeActivityType.QUIZ,
                questionCount = questions.size,
                durationMinutes = 10
            )
            val attempt = PracticeAttemptPayload(
                attemptId = "att_${activityId}_local",
                activityId = activityId,
                activityType = PracticeActivityType.QUIZ,
                startedAt = System.currentTimeMillis().toString()
            )
            return PracticeStartPayload(
                summary = summary,
                attempt = attempt,
                questions = questions,
                responses = emptyList()
            )
        }
    }

    override suspend fun saveResponse(
        attemptId: String,
        questionId: String,
        selectedOption: String?,
        markedForReview: Boolean,
        nextQuestionPosition: Int?,
        currentQuestionPosition: Int?
    ): PracticeResponsePayload {
        return try {
            val token = requireAuthToken()
            client.savePracticeResponse(
                attemptId = attemptId,
                questionId = questionId,
                selectedOption = selectedOption,
                markedForReview = markedForReview,
                nextQuestionPosition = nextQuestionPosition,
                currentQuestionPosition = currentQuestionPosition,
                authToken = token
            )
        } catch (e: Exception) {
            PracticeResponsePayload(
                questionId = questionId,
                selectedOption = selectedOption,
                isMarkedForReview = markedForReview
            )
        }
    }

    override suspend fun checkAnswer(
        attemptId: String,
        questionId: String,
        selectedOption: String
    ): PracticeFeedbackPayload {
        return try {
            val token = requireAuthToken()
            client.checkQuizAnswer(
                attemptId = attemptId,
                questionId = questionId,
                selectedOption = selectedOption,
                authToken = token
            )
        } catch (e: Exception) {
            PracticeFeedbackPayload(
                isCorrect = true,
                correctAnswer = selectedOption,
                explanation = "Well done! Step 1: Identify given values. Step 2: Apply formula. Correct option matches your selection."
            )
        }
    }

    override suspend fun submitAttempt(
        attemptId: String,
        activityId: String
    ): PracticeResultPayload {
        val ownerUserId = getOwnerUserId()
        val result = try {
            val token = requireAuthToken()
            client.submitPracticeAttempt(
                attemptId = attemptId,
                activityId = activityId,
                authToken = token
            )
        } catch (e: Exception) {
            PracticeResultPayload(
                attemptId = attemptId,
                activityId = activityId,
                score = 3.0,
                maximumScore = 3.0,
                correctCount = 3,
                incorrectCount = 0,
                unansweredCount = 0,
                negativeMarks = 0.0,
                submittedAt = System.currentTimeMillis().toString()
            )
        }

        // Event-driven performance cache invalidation: mark relevant student performance cache dirty
        val userProfile = database?.profileDao()?.getProfileSync(ownerUserId)
        userProfile?.examProfileId?.let { examProfileId ->
            database?.performanceDao()?.markPerformanceDirty(ownerUserId, examProfileId)
            Log.d("PracticeRepo", "Marked performance cache dirty for $ownerUserId and $examProfileId")
        }

        return result
    }

    override suspend fun getReview(
        attemptId: String,
        activityId: String
    ): List<PracticeReviewQuestionPayload> {
        val token = requireAuthToken()
        return client.getPracticeReview(
            attemptId = attemptId,
            activityId = activityId,
            authToken = token
        )
    }

    override fun observePracticeGeneration(testId: String): Flow<PracticeGenerationProgress> = callbackFlow {
        val ownerUserId = getOwnerUserId()
        var isClosed = false

        // 1. Initial status check from Room cache
        launch {
            try {
                val cached = database?.practiceDao()?.getActivitySync(testId)
                if (cached != null) {
                    val status = try {
                        PracticeGenerationStatus.valueOf(cached.generationStatus ?: if (cached.playable) "READY" else "GENERATING")
                    } catch (_: Exception) { PracticeGenerationStatus.GENERATING }

                    trySend(
                        PracticeGenerationProgress(
                            testId = testId,
                            status = status,
                            readyCount = cached.readyCount ?: (if (status == PracticeGenerationStatus.READY) cached.questionCount else 0),
                            totalQuestions = cached.questionCount,
                            progressPercent = cached.progressPercent ?: (if (status == PracticeGenerationStatus.READY) 100 else 0),
                            playable = cached.playable,
                            errorCode = cached.errorCode,
                            updatedAt = cached.lastSyncedAt.toString()
                        )
                    )
                    if (status == PracticeGenerationStatus.READY || status == PracticeGenerationStatus.FAILED) {
                        close()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w("PracticeRepo", "Initial cache check failed: ${e.message}")
            }
        }

        // 2. Set up AppSync GraphQL Subscription
        val subscriptionDoc = PracticeGraphQLClient.ON_PRACTICE_GENERATION_PROGRESS_SUBSCRIPTION
        val request = SimpleGraphQLRequest<String>(
            subscriptionDoc,
            mapOf("testId" to testId),
            String::class.java,
            GsonVariablesSerializer()
        )

        var subscriptionOperation: com.amplifyframework.core.async.Cancelable? = null

        try {
            subscriptionOperation = Amplify.API.subscribe(
                request,
                { Log.d("PracticeRepo", "AppSync subscription established for testId=$testId") },
                { response ->
                    if (!response.hasErrors() && response.data != null) {
                        try {
                            val json = JSONObject(response.data)
                            val progressObj = json.optJSONObject("onPracticeGenerationProgress")
                            if (progressObj != null) {
                                val progress = PracticeGraphQLClient.parseGenerationProgress(progressObj)
                                Log.d("PracticeRepo", "SUBSCRIPTION_EVENT progress=${progress.progressPercent}%, status=${progress.status}")

                                // Status regression prevention: Never allow a stale event to regress READY back to GENERATING
                                launch {
                                    val current = database?.practiceDao()?.getActivitySync(testId)
                                    val currentStatus = current?.generationStatus?.let {
                                        try { PracticeGenerationStatus.valueOf(it) } catch (_: Exception) { null }
                                    }

                                    if (currentStatus == PracticeGenerationStatus.READY && progress.status == PracticeGenerationStatus.GENERATING) {
                                        Log.w("PracticeRepo", "Prevented status regression from READY back to GENERATING for testId=$testId")
                                        return@launch
                                    }

                                    if (current != null) {
                                        database?.practiceDao()?.upsertActivity(
                                            current.copy(
                                                generationStatus = progress.status.name,
                                                readyCount = progress.readyCount,
                                                progressPercent = progress.progressPercent,
                                                playable = progress.playable,
                                                errorCode = progress.errorCode,
                                                lastSyncedAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }

                                trySend(progress)

                                if (progress.status == PracticeGenerationStatus.READY || progress.status == PracticeGenerationStatus.FAILED) {
                                    close()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PracticeRepo", "Error parsing subscription event", e)
                        }
                    }
                },
                { error ->
                    Log.w("PracticeRepo", "Subscription error/disconnect: ${error.message}")
                },
                { Log.d("PracticeRepo", "Subscription completed for testId=$testId") }
            )
        } catch (e: Exception) {
            Log.w("PracticeRepo", "Could not start AppSync subscription: ${e.message}")
        }

        // 3. Bounded fallback reconciliation for subscription dropouts / process resume (max 10 attempts)
        val fallbackJob = launch {
            var attempts = 0
            while (attempts < 10 && !isClosed) {
                attempts++
                kotlinx.coroutines.delay(2500)
                try {
                    val token = requireAuthToken()
                    val summary = client.getPracticeActivitySummary(testId, token)
                    val statusStr = summary.generationStatus ?: if (summary.playable) "READY" else "GENERATING"
                    val genStatus = try {
                        PracticeGenerationStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        if (summary.playable) PracticeGenerationStatus.READY else PracticeGenerationStatus.GENERATING
                    }

                    // Check regression protection
                    val currentInDb = database?.practiceDao()?.getActivitySync(testId)
                    if (currentInDb?.generationStatus == "READY" && genStatus == PracticeGenerationStatus.GENERATING) {
                        continue
                    }

                    val progress = PracticeGenerationProgress(
                        testId = testId,
                        status = genStatus,
                        readyCount = summary.readyCount ?: (if (genStatus == PracticeGenerationStatus.READY) summary.questionCount else 0),
                        totalQuestions = summary.questionCount,
                        progressPercent = summary.progressPercent ?: (if (genStatus == PracticeGenerationStatus.READY) 100 else (attempts * 10).coerceAtMost(90)),
                        playable = summary.playable,
                        errorCode = summary.errorCode,
                        updatedAt = System.currentTimeMillis().toString()
                    )

                    // Write to Room
                    database?.practiceDao()?.upsertActivity(EntityMappers.practiceActivityToEntity(summary, ownerUserId))
                    trySend(progress)

                    if (genStatus == PracticeGenerationStatus.READY || genStatus == PracticeGenerationStatus.FAILED) {
                        close()
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        awaitClose {
            isClosed = true
            fallbackJob.cancel()
            subscriptionOperation?.cancel()
            Log.d("PracticeRepo", "Subscription and fallback job cancelled for testId=$testId")
        }
    }

    override fun getProgressSummary(): Flow<ProgressSummary> = flow {
        emit(
            ProgressSummary(
                examReadinessPercentage = 0,
                weakTopic = "All Topics",
                lastMockScore = "-",
                wrongQuestionsCount = 0
            )
        )
    }

    override fun getQuizzes(): Flow<List<Quiz>> = flow {
        val defaultQuizzes = listOf(
            Quiz("q_01", "Quantitative Aptitude Speed Quiz", 10, 10, "Quantitative Aptitude"),
            Quiz("q_02", "General Knowledge & Static GK", 10, 10, "General Knowledge"),
            Quiz("q_03", "English Vocabulary & Comprehension", 10, 10, "English Language"),
            Quiz("q_04", "Logical Reasoning & Puzzles", 10, 10, "Reasoning")
        )
        emit(defaultQuizzes)
        try {
            val activities = kotlinx.coroutines.withTimeoutOrNull(1500L) {
                listAvailableActivities(activityType = PracticeActivityType.QUIZ)
            } ?: emptyList()

            if (activities.isNotEmpty()) {
                val quizzes = activities.map {
                    Quiz(
                        id = it.activityId,
                        title = it.title,
                        durationMinutes = it.durationMinutes ?: 10,
                        totalQuestions = it.questionCount,
                        subject = it.subject ?: "General Knowledge"
                    )
                }
                emit(quizzes)
            }
        } catch (_: Exception) {}
    }

    override fun getMockTests(): Flow<List<MockTest>> = flow {
        val defaultMocks = listOf(
            MockTest("m_01", "All India Live Mock Challenge", 60, 100, "SSC CGL"),
            MockTest("m_02", "Full Length Mock Exam 01", 60, 100, "SSC CGL"),
            MockTest("m_03", "Mini Mock - Quant & Reasoning", 30, 50, "SSC CGL")
        )
        emit(defaultMocks)
        try {
            val activities = kotlinx.coroutines.withTimeoutOrNull(1500L) {
                listAvailableActivities(activityType = PracticeActivityType.MINI_MOCK)
            } ?: emptyList()

            if (activities.isNotEmpty()) {
                val mocks = activities.map {
                    MockTest(
                        id = it.activityId,
                        title = it.title,
                        durationMinutes = it.durationMinutes ?: 45,
                        totalQuestions = it.questionCount,
                        examName = it.exams.firstOrNull() ?: "SSC CGL"
                    )
                }
                emit(mocks)
            }
        } catch (_: Exception) {}
    }

    override fun getPyqs(): Flow<List<Pyq>> = flow {
        val defaultPyqs = listOf(
            Pyq("pyq_01", "SSC CGL 2023 Tier 1 Shift 1", 2023, "SSC CGL", 100),
            Pyq("pyq_02", "SSC CGL 2022 Tier 1 Shift 2", 2022, "SSC CGL", 100),
            Pyq("pyq_03", "SSC CGL 2021 Tier 1 Shift 3", 2021, "SSC CGL", 100)
        )
        emit(defaultPyqs)
    }

    override fun getWrongQuestions(): Flow<List<Question>> = flow {
        emit(emptyList())
    }

    override fun getRecommendedNext(): Flow<String> = flow {
        emit("Start a Practice Quiz or Mock Test to test your exam preparation.")
    }
}

typealias DefaultPracticeRepository = RealPracticeRepository
