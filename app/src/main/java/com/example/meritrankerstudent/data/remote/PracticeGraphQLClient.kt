package com.example.meritrankerstudent.data.remote

import com.example.meritrankerstudent.BuildConfig
import com.example.meritrankerstudent.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PracticeGraphQLClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val endpointUrl: String = "https://e7rfdkgqczag5bz34ov67locl4.appsync-api.ap-south-1.amazonaws.com/graphql"
) {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val LIST_AVAILABLE_ACTIVITIES_QUERY = """
            query ListAvailablePracticeActivities(
                ${'$'}examId: String,
                ${'$'}activityType: PracticeActivityType,
                ${'$'}subject: String,
                ${'$'}topic: String,
                ${'$'}language: UserLang,
                ${'$'}limit: Int,
                ${'$'}nextToken: String
            ) {
                listAvailablePracticeActivities(
                    examId: ${'$'}examId,
                    activityType: ${'$'}activityType,
                    subject: ${'$'}subject,
                    topic: ${'$'}topic,
                    language: ${'$'}language,
                    limit: ${'$'}limit,
                    nextToken: ${'$'}nextToken
                ) {
                    activityId
                    title
                    activityType
                    language
                    subject
                    topic
                    exams
                    questionCount
                    durationMinutes
                    hasResumableAttempt
                    resumableAttemptId
                    generationStatus
                    readyCount
                    progressPercent
                    playable
                    errorCode
                    latestAttemptId
                    latestAttemptStatus
                    latestAttemptScore
                    latestAttemptMaximumScore
                }
            }
        """

        const val GET_ACTIVITY_SUMMARY_QUERY = """
            query GetPracticeActivitySummary(${'$'}activityId: String!) {
                getPracticeActivitySummary(activityId: ${'$'}activityId) {
                    activityId
                    title
                    activityType
                    language
                    subject
                    topic
                    exams
                    questionCount
                    durationMinutes
                    hasResumableAttempt
                    resumableAttemptId
                    generationStatus
                    readyCount
                    progressPercent
                    playable
                    errorCode
                    latestAttemptId
                    latestAttemptStatus
                    latestAttemptScore
                    latestAttemptMaximumScore
                }
            }
        """

        const val START_PRACTICE_ATTEMPT_MUTATION = """
            mutation StartPracticeAttempt(${'$'}activityId: String!) {
                startPracticeAttempt(activityId: ${'$'}activityId) {
                    summary {
                        activityId
                        title
                        activityType
                        language
                        subject
                        topic
                        exams
                        questionCount
                        durationMinutes
                        hasResumableAttempt
                        resumableAttemptId
                        generationStatus
                        readyCount
                        progressPercent
                        playable
                        errorCode
                        latestAttemptId
                        latestAttemptStatus
                        latestAttemptScore
                        latestAttemptMaximumScore
                    }
                    attempt {
                        attemptId
                        activityId
                        activityType
                        status
                        startedAt
                        expiresAt
                        currentQuestionPosition
                        score
                        maximumScore
                        correctCount
                        incorrectCount
                        unansweredCount
                        negativeMarks
                        submittedAt
                    }
                    questions {
                        questionId
                        position
                        question
                        options
                        section
                        subject
                        topic
                        marks
                        negativeMarks
                        format
                        language
                    }
                    responses {
                        questionId
                        selectedOption
                        isAnswerLocked
                        isMarkedForReview
                    }
                }
            }
        """

        const val SAVE_PRACTICE_RESPONSE_MUTATION = """
            mutation SavePracticeResponse(
                ${'$'}attemptId: String!,
                ${'$'}questionId: String!,
                ${'$'}selectedOption: String,
                ${'$'}markedForReview: Boolean!,
                ${'$'}nextQuestionPosition: Int,
                ${'$'}currentQuestionPosition: Int
            ) {
                savePracticeResponse(
                    attemptId: ${'$'}attemptId,
                    questionId: ${'$'}questionId,
                    selectedOption: ${'$'}selectedOption,
                    markedForReview: ${'$'}markedForReview,
                    nextQuestionPosition: ${'$'}nextQuestionPosition,
                    currentQuestionPosition: ${'$'}currentQuestionPosition
                ) {
                    questionId
                    selectedOption
                    isAnswerLocked
                    isMarkedForReview
                }
            }
        """

        const val CHECK_QUIZ_ANSWER_MUTATION = """
            mutation CheckQuizAnswer(
                ${'$'}attemptId: String!,
                ${'$'}questionId: String!,
                ${'$'}selectedOption: String!
            ) {
                checkQuizAnswer(
                    attemptId: ${'$'}attemptId,
                    questionId: ${'$'}questionId,
                    selectedOption: ${'$'}selectedOption
                ) {
                    isCorrect
                    correctAnswer
                    explanation
                    errorCode
                }
            }
        """

        const val SUBMIT_PRACTICE_ATTEMPT_MUTATION = """
            mutation SubmitPracticeAttempt(
                ${'$'}attemptId: String!,
                ${'$'}activityId: String!
            ) {
                submitPracticeAttempt(
                    attemptId: ${'$'}attemptId,
                    activityId: ${'$'}activityId
                ) {
                    attemptId
                    activityId
                    status
                    score
                    maximumScore
                    correctCount
                    incorrectCount
                    unansweredCount
                    negativeMarks
                    submittedAt
                }
            }
        """

        const val GET_PRACTICE_REVIEW_QUERY = """
            query GetPracticeReview(
                ${'$'}attemptId: String!,
                ${'$'}activityId: String!
            ) {
                getPracticeReview(
                    attemptId: ${'$'}attemptId,
                    activityId: ${'$'}activityId
                ) {
                    questionId
                    position
                    question
                    options
                    selectedOption
                    correctAnswer
                    explanation
                    isCorrect
                }
            }
        """

        const val ON_PRACTICE_GENERATION_PROGRESS_SUBSCRIPTION = """
            subscription OnPracticeGenerationProgress(${'$'}testId: String!) {
                onPracticeGenerationProgress(testId: ${'$'}testId) {
                    testId
                    status
                    totalQuestions
                    meta
                    updatedAt
                }
            }
        """

        internal fun parseGenerationProgress(obj: JSONObject): PracticeGenerationProgress {
            val testId = obj.optString("testId")
            val statusStr = obj.optString("status", "GENERATING")
            val totalQuestions = obj.optInt("totalQuestions", 0)
            val updatedAt = obj.optString("updatedAt", "")

            var readyCount = 0
            var progressPercent = 0
            var playable = false
            var errorCode: String? = null
            var cancelRequested = false

            val metaObj = when (val meta = obj.opt("meta")) {
                is JSONObject -> meta
                is String -> try { JSONObject(meta) } catch (e: Exception) { null }
                else -> null
            }

            if (metaObj != null) {
                readyCount = metaObj.optInt("readyCount", 0)
                progressPercent = metaObj.optInt("progressPercent", 0)
                playable = metaObj.optBoolean("playable", false)
                errorCode = metaObj.optString("errorCode").takeIf { it.isNotEmpty() }
                cancelRequested = metaObj.optBoolean("cancelRequested", false)
            }

            val status = try {
                PracticeGenerationStatus.valueOf(statusStr)
            } catch (e: Exception) {
                PracticeGenerationStatus.GENERATING
            }

            return PracticeGenerationProgress(
                testId = testId,
                status = status,
                readyCount = readyCount,
                totalQuestions = totalQuestions,
                progressPercent = progressPercent,
                playable = playable,
                errorCode = errorCode,
                cancelRequested = cancelRequested,
                updatedAt = updatedAt
            )
        }

        internal fun parseActivitySummary(obj: JSONObject): PracticeActivitySummary {
            val examsArray = obj.optJSONArray("exams") ?: JSONArray()
            val exams = mutableListOf<String>()
            for (j in 0 until examsArray.length()) {
                exams.add(examsArray.optString(j))
            }

            return PracticeActivitySummary(
                activityId = obj.optString("activityId"),
                title = obj.optString("title", "Practice Activity"),
                activityType = PracticeActivityType.valueOf(obj.optString("activityType", "QUIZ")),
                language = obj.optString("language", "en"),
                subject = obj.optString("subject").takeIf { it.isNotEmpty() },
                topic = obj.optString("topic").takeIf { it.isNotEmpty() },
                exams = exams,
                questionCount = obj.optInt("questionCount", 0),
                durationMinutes = if (obj.has("durationMinutes") && !obj.isNull("durationMinutes")) obj.getInt("durationMinutes") else null,
                hasResumableAttempt = obj.optBoolean("hasResumableAttempt", false),
                resumableAttemptId = obj.optString("resumableAttemptId").takeIf { it.isNotEmpty() },
                generationStatus = obj.optString("generationStatus").takeIf { it.isNotEmpty() },
                readyCount = if (obj.has("readyCount") && !obj.isNull("readyCount")) obj.getInt("readyCount") else null,
                progressPercent = if (obj.has("progressPercent") && !obj.isNull("progressPercent")) obj.getInt("progressPercent") else null,
                playable = obj.optBoolean("playable", true),
                errorCode = obj.optString("errorCode").takeIf { it.isNotEmpty() },
                latestAttemptId = obj.optString("latestAttemptId").takeIf { it.isNotEmpty() },
                latestAttemptStatus = obj.optString("latestAttemptStatus").takeIf { it.isNotEmpty() },
                latestAttemptScore = if (obj.has("latestAttemptScore") && !obj.isNull("latestAttemptScore")) obj.getDouble("latestAttemptScore") else null,
                latestAttemptMaximumScore = if (obj.has("latestAttemptMaximumScore") && !obj.isNull("latestAttemptMaximumScore")) obj.getDouble("latestAttemptMaximumScore") else null
            )
        }

        internal fun parseQuestionPayload(q: JSONObject): PracticeQuestionPayload {
            val optionsArray = q.optJSONArray("options") ?: JSONArray()
            val options = mutableListOf<String>()
            for (j in 0 until optionsArray.length()) {
                options.add(optionsArray.optString(j))
            }
            return PracticeQuestionPayload(
                questionId = q.optString("questionId"),
                position = q.optInt("position", 1),
                question = q.optString("question"),
                options = options,
                section = q.optString("section").takeIf { it.isNotEmpty() },
                subject = q.optString("subject").takeIf { it.isNotEmpty() },
                topic = q.optString("topic").takeIf { it.isNotEmpty() },
                marks = q.optDouble("marks", 1.0),
                negativeMarks = q.optDouble("negativeMarks", 0.0),
                format = q.optString("format").takeIf { it.isNotEmpty() },
                language = q.optString("language", "en")
            )
        }

        internal fun parseAttemptPayload(obj: JSONObject): PracticeAttemptPayload {
            return PracticeAttemptPayload(
                attemptId = obj.optString("attemptId"),
                activityId = obj.optString("activityId"),
                activityType = PracticeActivityType.valueOf(obj.optString("activityType", "QUIZ")),
                status = PracticeAttemptStatus.valueOf(obj.optString("status", "IN_PROGRESS")),
                startedAt = obj.optString("startedAt"),
                expiresAt = obj.optString("expiresAt").takeIf { it.isNotEmpty() },
                currentQuestionPosition = obj.optInt("currentQuestionPosition", 1),
                score = if (obj.has("score") && !obj.isNull("score")) obj.getDouble("score") else null,
                maximumScore = if (obj.has("maximumScore") && !obj.isNull("maximumScore")) obj.getDouble("maximumScore") else null,
                correctCount = if (obj.has("correctCount") && !obj.isNull("correctCount")) obj.getInt("correctCount") else null,
                incorrectCount = if (obj.has("incorrectCount") && !obj.isNull("incorrectCount")) obj.getInt("incorrectCount") else null,
                unansweredCount = if (obj.has("unansweredCount") && !obj.isNull("unansweredCount")) obj.getInt("unansweredCount") else null,
                negativeMarks = if (obj.has("negativeMarks") && !obj.isNull("negativeMarks")) obj.getDouble("negativeMarks") else null,
                submittedAt = obj.optString("submittedAt").takeIf { it.isNotEmpty() }
            )
        }

        internal fun parseResponsePayload(r: JSONObject): PracticeResponsePayload {
            return PracticeResponsePayload(
                questionId = r.optString("questionId"),
                selectedOption = r.optString("selectedOption").takeIf { it.isNotEmpty() },
                isAnswerLocked = r.optBoolean("isAnswerLocked", false),
                isMarkedForReview = r.optBoolean("isMarkedForReview", false)
            )
        }
    }

    private suspend fun executeGraphQL(
        query: String,
        variables: JSONObject,
        authToken: String?
    ): JSONObject = withContext(Dispatchers.IO) {
        if (authToken.isNullOrBlank()) {
            throw IOException("AUTH_TOKEN_MISSING")
        }

        val payload = JSONObject().apply {
            put("query", query.trimIndent())
            put("variables", variables)
        }

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $authToken")

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("GraphQL HTTP error ${response.code}: $responseBody")
        }

        val json = JSONObject(responseBody)
        if (json.has("errors")) {
            val errors = json.getJSONArray("errors")
            if (errors.length() > 0) {
                val firstError = errors.getJSONObject(0)
                val errorMessage = firstError.optString("message", "Unknown GraphQL error")
                throw IOException(errorMessage)
            }
        }
        json.optJSONObject("data") ?: JSONObject()
    }

    suspend fun listAvailablePracticeActivities(
        examId: String? = null,
        activityType: PracticeActivityType? = null,
        subject: String? = null,
        topic: String? = null,
        language: String? = null,
        limit: Int? = 20,
        nextToken: String? = null,
        authToken: String? = null
    ): List<PracticeActivitySummary> {
        val variables = JSONObject().apply {
            examId?.let { put("examId", it) }
            activityType?.let { put("activityType", it.name) }
            subject?.let { put("subject", it) }
            topic?.let { put("topic", it) }
            language?.let { put("language", it) }
            limit?.let { put("limit", it) }
            nextToken?.let { put("nextToken", it) }
        }

        val data = executeGraphQL(LIST_AVAILABLE_ACTIVITIES_QUERY, variables, authToken)
        val array = data.optJSONArray("listAvailablePracticeActivities") ?: JSONArray()
        val result = mutableListOf<PracticeActivitySummary>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(parseActivitySummary(obj))
        }
        return result
    }

    suspend fun getPracticeActivitySummary(
        activityId: String,
        authToken: String? = null
    ): PracticeActivitySummary {
        val variables = JSONObject().apply {
            put("activityId", activityId)
        }
        val data = executeGraphQL(GET_ACTIVITY_SUMMARY_QUERY, variables, authToken)
        val obj = data.optJSONObject("getPracticeActivitySummary")
            ?: throw IOException("Activity not found")
        return parseActivitySummary(obj)
    }

    suspend fun startPracticeAttempt(
        activityId: String,
        authToken: String? = null
    ): PracticeStartPayload {
        val variables = JSONObject().apply {
            put("activityId", activityId)
        }
        val data = executeGraphQL(START_PRACTICE_ATTEMPT_MUTATION, variables, authToken)
        val startObj = data.optJSONObject("startPracticeAttempt")
            ?: throw IOException("Could not start practice attempt")

        val summaryObj = startObj.optJSONObject("summary")
            ?: throw IOException("Missing activity summary in start payload")
        val attemptObj = startObj.optJSONObject("attempt")
            ?: throw IOException("Missing attempt in start payload")

        val questionsArray = startObj.optJSONArray("questions") ?: JSONArray()
        val responsesArray = startObj.optJSONArray("responses") ?: JSONArray()

        val questions = mutableListOf<PracticeQuestionPayload>()
        for (i in 0 until questionsArray.length()) {
            val q = questionsArray.optJSONObject(i) ?: continue
            questions.add(parseQuestionPayload(q))
        }

        val responses = mutableListOf<PracticeResponsePayload>()
        for (i in 0 until responsesArray.length()) {
            val r = responsesArray.optJSONObject(i) ?: continue
            responses.add(parseResponsePayload(r))
        }

        return PracticeStartPayload(
            summary = parseActivitySummary(summaryObj),
            attempt = parseAttemptPayload(attemptObj),
            questions = questions,
            responses = responses
        )
    }

    suspend fun savePracticeResponse(
        attemptId: String,
        questionId: String,
        selectedOption: String?,
        markedForReview: Boolean,
        nextQuestionPosition: Int? = null,
        currentQuestionPosition: Int? = null,
        authToken: String? = null
    ): PracticeResponsePayload {
        val variables = JSONObject().apply {
            put("attemptId", attemptId)
            put("questionId", questionId)
            if (selectedOption != null) {
                put("selectedOption", selectedOption)
            } else {
                put("selectedOption", JSONObject.NULL)
            }
            put("markedForReview", markedForReview)
            nextQuestionPosition?.let { put("nextQuestionPosition", it) }
            currentQuestionPosition?.let { put("currentQuestionPosition", it) }
        }

        val data = executeGraphQL(SAVE_PRACTICE_RESPONSE_MUTATION, variables, authToken)
        val respObj = data.optJSONObject("savePracticeResponse")
            ?: throw IOException("Failed to save practice response")
        return parseResponsePayload(respObj)
    }

    suspend fun checkQuizAnswer(
        attemptId: String,
        questionId: String,
        selectedOption: String,
        authToken: String? = null
    ): PracticeFeedbackPayload {
        val variables = JSONObject().apply {
            put("attemptId", attemptId)
            put("questionId", questionId)
            put("selectedOption", selectedOption)
        }

        val data = executeGraphQL(CHECK_QUIZ_ANSWER_MUTATION, variables, authToken)
        val obj = data.optJSONObject("checkQuizAnswer")
            ?: throw IOException("Failed to check quiz answer")

        val errorCode = if (obj.has("errorCode") && !obj.isNull("errorCode")) obj.getString("errorCode") else null
        if (errorCode == "ANSWER_LOCKED") {
            throw IOException("ANSWER_LOCKED")
        }

        return PracticeFeedbackPayload(
            isCorrect = obj.optBoolean("isCorrect", false),
            correctAnswer = obj.optString("correctAnswer", ""),
            explanation = obj.optString("explanation").takeIf { it.isNotEmpty() },
            errorCode = errorCode
        )
    }

    suspend fun submitPracticeAttempt(
        attemptId: String,
        activityId: String,
        authToken: String? = null
    ): PracticeResultPayload {
        val variables = JSONObject().apply {
            put("attemptId", attemptId)
            put("activityId", activityId)
        }

        val data = executeGraphQL(SUBMIT_PRACTICE_ATTEMPT_MUTATION, variables, authToken)
        val obj = data.optJSONObject("submitPracticeAttempt")
            ?: throw IOException("Failed to submit practice attempt")

        return PracticeResultPayload(
            attemptId = obj.optString("attemptId", attemptId),
            activityId = obj.optString("activityId", activityId),
            status = PracticeAttemptStatus.valueOf(obj.optString("status", "SUBMITTED")),
            score = obj.optDouble("score", 0.0),
            maximumScore = obj.optDouble("maximumScore", 0.0),
            correctCount = obj.optInt("correctCount", 0),
            incorrectCount = obj.optInt("incorrectCount", 0),
            unansweredCount = obj.optInt("unansweredCount", 0),
            negativeMarks = obj.optDouble("negativeMarks", 0.0),
            submittedAt = obj.optString("submittedAt", "")
        )
    }

    suspend fun getPracticeReview(
        attemptId: String,
        activityId: String,
        authToken: String? = null
    ): List<PracticeReviewQuestionPayload> {
        val variables = JSONObject().apply {
            put("attemptId", attemptId)
            put("activityId", activityId)
        }

        val data = executeGraphQL(GET_PRACTICE_REVIEW_QUERY, variables, authToken)
        val array = data.optJSONArray("getPracticeReview") ?: JSONArray()
        val result = mutableListOf<PracticeReviewQuestionPayload>()
        for (i in 0 until array.length()) {
            val q = array.optJSONObject(i) ?: continue
            val optionsArray = q.optJSONArray("options") ?: JSONArray()
            val options = mutableListOf<String>()
            for (j in 0 until optionsArray.length()) {
                options.add(optionsArray.optString(j))
            }
            result.add(
                PracticeReviewQuestionPayload(
                    questionId = q.optString("questionId"),
                    position = q.optInt("position", i + 1),
                    question = q.optString("question"),
                    options = options,
                    selectedOption = q.optString("selectedOption").takeIf { it.isNotEmpty() },
                    correctAnswer = q.optString("correctAnswer"),
                    explanation = q.optString("explanation").takeIf { it.isNotEmpty() },
                    isCorrect = if (q.has("isCorrect") && !q.isNull("isCorrect")) q.getBoolean("isCorrect") else null
                )
            )
        }
        return result
    }

    private fun parseActivitySummary(obj: JSONObject): PracticeActivitySummary {
        val examsArray = obj.optJSONArray("exams") ?: JSONArray()
        val exams = mutableListOf<String>()
        for (j in 0 until examsArray.length()) {
            exams.add(examsArray.optString(j))
        }

        return PracticeActivitySummary(
            activityId = obj.optString("activityId"),
            title = obj.optString("title", "Practice Activity"),
            activityType = PracticeActivityType.valueOf(obj.optString("activityType", "QUIZ")),
            language = obj.optString("language", "en"),
            subject = obj.optString("subject").takeIf { it.isNotEmpty() },
            topic = obj.optString("topic").takeIf { it.isNotEmpty() },
            exams = exams,
            questionCount = obj.optInt("questionCount", 0),
            durationMinutes = if (obj.has("durationMinutes") && !obj.isNull("durationMinutes")) obj.getInt("durationMinutes") else null,
            hasResumableAttempt = obj.optBoolean("hasResumableAttempt", false),
            resumableAttemptId = obj.optString("resumableAttemptId").takeIf { it.isNotEmpty() },
            generationStatus = obj.optString("generationStatus").takeIf { it.isNotEmpty() },
            readyCount = if (obj.has("readyCount") && !obj.isNull("readyCount")) obj.getInt("readyCount") else null,
            progressPercent = if (obj.has("progressPercent") && !obj.isNull("progressPercent")) obj.getInt("progressPercent") else null,
            playable = obj.optBoolean("playable", true),
            errorCode = obj.optString("errorCode").takeIf { it.isNotEmpty() },
            latestAttemptId = obj.optString("latestAttemptId").takeIf { it.isNotEmpty() },
            latestAttemptStatus = obj.optString("latestAttemptStatus").takeIf { it.isNotEmpty() },
            latestAttemptScore = if (obj.has("latestAttemptScore") && !obj.isNull("latestAttemptScore")) obj.getDouble("latestAttemptScore") else null,
            latestAttemptMaximumScore = if (obj.has("latestAttemptMaximumScore") && !obj.isNull("latestAttemptMaximumScore")) obj.getDouble("latestAttemptMaximumScore") else null
        )
    }

    private fun parseQuestionPayload(q: JSONObject): PracticeQuestionPayload {
        val optionsArray = q.optJSONArray("options") ?: JSONArray()
        val options = mutableListOf<String>()
        for (j in 0 until optionsArray.length()) {
            options.add(optionsArray.optString(j))
        }
        return PracticeQuestionPayload(
            questionId = q.optString("questionId"),
            position = q.optInt("position", 1),
            question = q.optString("question"),
            options = options,
            section = q.optString("section").takeIf { it.isNotEmpty() },
            subject = q.optString("subject").takeIf { it.isNotEmpty() },
            topic = q.optString("topic").takeIf { it.isNotEmpty() },
            marks = q.optDouble("marks", 1.0),
            negativeMarks = q.optDouble("negativeMarks", 0.0),
            format = q.optString("format").takeIf { it.isNotEmpty() },
            language = q.optString("language", "en")
        )
    }

    private fun parseAttemptPayload(obj: JSONObject): PracticeAttemptPayload {
        return PracticeAttemptPayload(
            attemptId = obj.optString("attemptId"),
            activityId = obj.optString("activityId"),
            activityType = PracticeActivityType.valueOf(obj.optString("activityType", "QUIZ")),
            status = PracticeAttemptStatus.valueOf(obj.optString("status", "IN_PROGRESS")),
            startedAt = obj.optString("startedAt"),
            expiresAt = obj.optString("expiresAt").takeIf { it.isNotEmpty() },
            currentQuestionPosition = obj.optInt("currentQuestionPosition", 1),
            score = if (obj.has("score") && !obj.isNull("score")) obj.getDouble("score") else null,
            maximumScore = if (obj.has("maximumScore") && !obj.isNull("maximumScore")) obj.getDouble("maximumScore") else null,
            correctCount = if (obj.has("correctCount") && !obj.isNull("correctCount")) obj.getInt("correctCount") else null,
            incorrectCount = if (obj.has("incorrectCount") && !obj.isNull("incorrectCount")) obj.getInt("incorrectCount") else null,
            unansweredCount = if (obj.has("unansweredCount") && !obj.isNull("unansweredCount")) obj.getInt("unansweredCount") else null,
            negativeMarks = if (obj.has("negativeMarks") && !obj.isNull("negativeMarks")) obj.getDouble("negativeMarks") else null,
            submittedAt = obj.optString("submittedAt").takeIf { it.isNotEmpty() }
        )
    }

    private fun parseResponsePayload(r: JSONObject): PracticeResponsePayload {
        return PracticeResponsePayload(
            questionId = r.optString("questionId"),
            selectedOption = r.optString("selectedOption").takeIf { it.isNotEmpty() },
            isAnswerLocked = r.optBoolean("isAnswerLocked", false),
            isMarkedForReview = r.optBoolean("isMarkedForReview", false)
        )
    }
}
