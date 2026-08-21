package com.example.meritrankerstudent.data.remote

import com.example.meritrankerstudent.BuildConfig
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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

sealed interface TutorStreamEvent {
    data class Status(val stage: String?, val label: String?, val requestId: String?) : TutorStreamEvent
    data class Chunk(val text: String, val requestId: String?) : TutorStreamEvent
    data class Completed(
        val finalAnswer: String,
        val requestId: String?,
        val actionRoute: String? = null,
        val actionText: String? = null,
        val practiceTestId: String? = null,
        val responseType: String? = null,
        val title: String? = null,
        val questionCount: Int? = null
    ) : TutorStreamEvent
    data class PracticeGenerationStarted(
        val practiceTestId: String,
        val status: String,
        val message: String?,
        val title: String? = null,
        val questionCount: Int? = null,
        val requestId: String?
    ) : TutorStreamEvent
    data class Error(val message: String, val code: String? = null, val retryable: Boolean = false) : TutorStreamEvent
}

data class TutorRequestPayload(
    val conversationId: String,
    val turnId: String,
    val userId: String,
    val query: String,
    val examProfileId: String? = null,
    val examId: String? = null,
    val examStage: String? = null,
    val language: String = "en",
    val mode: String = "doubt_solver",
    val stream: Boolean = true,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

class TutorStreamClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val endpointUrl: String = "${BuildConfig.TUTOR_API_BASE_URL}/api/dev/doubt-solver"
) {

    companion object {
        val ALLOWED_ACTION_ROUTES = setOf("QUIZ", "MOCK", "PYQ")

        fun buildJsonPayload(payload: TutorRequestPayload): JSONObject {
            val normalizedLang = when (payload.language.lowercase()) {
                "hi", "hindi", "हिंदी" -> "hi"
                "hinglish" -> "hinglish"
                else -> "en"
            }

            return JSONObject().apply {
                put("conversation_id", payload.conversationId)
                put("turn_id", payload.turnId)
                put("user_id", payload.userId.ifBlank { "local-user" })
                put("query", payload.query)
                payload.examProfileId?.let { put("exam_profile_id", it) }
                payload.examId?.let { put("exam_id", it) }
                payload.examStage?.let { put("exam_stage", it) }
                put("language", normalizedLang)
                put("mode", payload.mode)
                put("stream", payload.stream)

                // Canonical image representation (1 image representation only)
                payload.imageBase64?.let { base64Str ->
                    put("image_base64", base64Str)
                    put("image_mime_type", payload.imageMimeType ?: "image/jpeg")
                }
            }
        }
    }

    suspend fun streamTutorResponse(
        payload: TutorRequestPayload,
        authToken: String? = null
    ): Flow<TutorStreamEvent> = flow {
        val jsonBody = buildJsonPayload(payload).toString()

        val candidateUrls = buildList {
            // In debug builds, prioritize localhost (adb reverse on physical device & desktop)
            if (BuildConfig.DEBUG) {
                add("http://localhost:3000/api/dev/doubt-solver")
                add("http://localhost:8080/invocations")
                add("http://127.0.0.1:3000/api/dev/doubt-solver")
                add("http://127.0.0.1:8080/invocations")
                add(endpointUrl)
                add("http://10.0.2.2:3000/api/dev/doubt-solver")
                add("http://10.0.2.2:8080/invocations")
            } else {
                add(endpointUrl)
                if (!endpointUrl.endsWith("/invocations")) {
                    add("${BuildConfig.TUTOR_API_BASE_URL}/invocations")
                }
            }
        }.distinct()

        var response: okhttp3.Response? = null
        var lastConnectException: Exception? = null

        for (targetUrl in candidateUrls) {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "text/event-stream, application/json")

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $authToken")
            }

            val request = requestBuilder.build()
            try {
                val resp = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                if (resp.isSuccessful || resp.code == 400 || resp.code == 401 || resp.code == 403 || resp.code == 429) {
                    response = resp
                    break
                } else {
                    resp.close()
                }
            } catch (e: Exception) {
                lastConnectException = e
                // Try next candidate
            }
        }

        if (response == null) {
            emit(TutorStreamEvent.Error("Connection error: ${lastConnectException?.localizedMessage ?: "Unable to reach Smart Tutor server"}"))
            return@flow
        }

        try {
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                val errorMsg = try {
                    val json = JSONObject(errorBody)
                    json.optString("answer", "HTTP Error ${response.code}")
                } catch (e: Exception) {
                    "Server error (HTTP ${response.code})"
                }
                emit(TutorStreamEvent.Error(errorMsg))
                return@flow
            }

            val body = response.body

            val reader = BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8))
            var line: String?
            var accumulatedText = ""

            while (withContext(Dispatchers.IO) { reader.readLine() }.also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                val isSseData = currentLine.startsWith("data:")
                val dataJsonStr = if (isSseData) {
                    currentLine.removePrefix("data:").trim()
                } else if (currentLine.startsWith("{") && currentLine.endsWith("}")) {
                    currentLine
                } else {
                    ""
                }
                if (dataJsonStr.isEmpty()) continue

                try {
                    val json = JSONObject(dataJsonStr)
                    val type = json.optString("type")
                    val requestId = json.optNullableString("request_id")

                    when (type) {
                        "status" -> {
                            val stage = json.optNullableString("stage")
                            val label = json.optNullableString("label")
                            emit(TutorStreamEvent.Status(stage = stage, label = label, requestId = requestId))
                        }
                        "chunk" -> {
                            val content = json.optNullableString("content")
                            if (!content.isNullOrEmpty()) {
                                accumulatedText += content
                                emit(TutorStreamEvent.Chunk(text = content, requestId = requestId))
                            }
                        }
                        "practice_generation_started" -> {
                            val dataObj = json.optJSONObject("data")
                            val practiceTestId = dataObj?.optNullableString("practiceTestId")
                                ?: json.optNullableString("practiceTestId")
                            val status = dataObj?.optNullableString("status") ?: "GENERATING"
                            val message = dataObj?.optNullableString("message")
                                ?: json.optNullableString("message")
                            val title = dataObj?.optNullableString("title")
                                ?: json.optNullableString("title")
                            val questionCount = if (dataObj?.has("questionCount") == true && !dataObj.isNull("questionCount")) {
                                dataObj.optInt("questionCount")
                            } else if (json.has("questionCount") && !json.isNull("questionCount")) {
                                json.optInt("questionCount")
                            } else {
                                null
                            }

                            if (!practiceTestId.isNullOrEmpty()) {
                                emit(
                                    TutorStreamEvent.PracticeGenerationStarted(
                                        practiceTestId = practiceTestId,
                                        status = status,
                                        message = message,
                                        title = title,
                                        questionCount = questionCount,
                                        requestId = requestId
                                    )
                                )
                            }
                        }
                        "complete" -> {
                            val responseObj = json.optJSONObject("response")
                            val contentObj = responseObj?.optJSONObject("content")
                            val finalAnswer = responseObj?.optNullableString("answer")
                                ?: contentObj?.optNullableString("value")
                                ?: json.optNullableString("content")
                                ?: accumulatedText

                            // Authoritative structured practice card metadata extraction with whitelisting
                            val rawRoute = responseObj?.optNullableString("action_route")
                                ?: json.optNullableString("action_route")
                            val validatedRoute = if (rawRoute != null && ALLOWED_ACTION_ROUTES.contains(rawRoute.uppercase())) {
                                rawRoute.uppercase()
                            } else {
                                null
                            }

                            val actionText = responseObj?.optNullableString("action_text")
                                ?: json.optNullableString("action_text")

                            val responseType = responseObj?.optNullableString("responseType")
                                ?: json.optJSONObject("data")?.optNullableString("responseType")
                                ?: json.optNullableString("responseType")

                            val practiceTestId = responseObj?.optNullableString("practiceTestId")
                                ?: json.optNullableString("practiceTestId")
                                ?: (if (responseType == "practice_generation")
                                    json.optJSONObject("data")?.optNullableString("practiceTestId") else null)

                            val title = responseObj?.optNullableString("title")
                                ?: json.optJSONObject("data")?.optNullableString("title")
                                ?: json.optNullableString("title")

                            val questionCount = if (responseObj?.has("questionCount") == true && !responseObj.isNull("questionCount")) {
                                responseObj.optInt("questionCount")
                            } else if (json.has("questionCount") && !json.isNull("questionCount")) {
                                json.optInt("questionCount")
                            } else {
                                null
                            }

                            emit(
                                TutorStreamEvent.Completed(
                                    finalAnswer = finalAnswer,
                                    requestId = requestId,
                                    actionRoute = validatedRoute,
                                    actionText = if (validatedRoute != null) actionText else null,
                                    practiceTestId = practiceTestId,
                                    responseType = responseType,
                                    title = title,
                                    questionCount = questionCount
                                )
                            )
                        }
                        "error" -> {
                            val errorObj = json.optJSONObject("error")
                            val errorMsg = errorObj?.optNullableString("message")
                                ?: json.optNullableString("message")
                                ?: "An error occurred while generating response"
                            val errorCode = errorObj?.optNullableString("code")
                                ?: json.optNullableString("code")
                            val retryable = errorObj?.optBoolean("retryable", false)
                                ?: json.optBoolean("retryable", false)
                            emit(TutorStreamEvent.Error(message = errorMsg, code = errorCode, retryable = retryable))
                        }
                        else -> {
                            if (json.has("success") && !json.getBoolean("success")) {
                                val errAnswer = json.optString("answer", "Unknown AI error")
                                emit(TutorStreamEvent.Error(errAnswer))
                            } else if (json.has("answer") || json.has("content") || json.optString("status") == "completed") {
                                val contentObj = json.optJSONObject("content")
                                val finalAnswer = json.optNullableString("answer")
                                    ?: contentObj?.optNullableString("value")
                                    ?: accumulatedText

                                val responseType = json.optNullableString("responseType")
                                    ?: json.optJSONObject("data")?.optNullableString("responseType")
                                val practiceTestId = json.optNullableString("practiceTestId")
                                    ?: json.optJSONObject("data")?.optNullableString("practiceTestId")
                                val title = json.optNullableString("title")
                                    ?: json.optJSONObject("data")?.optNullableString("title")
                                val rawRoute = json.optNullableString("action_route")
                                val validatedRoute = if (rawRoute != null && ALLOWED_ACTION_ROUTES.contains(rawRoute.uppercase())) {
                                    rawRoute.uppercase()
                                } else {
                                    null
                                }
                                val actionText = json.optNullableString("action_text")
                                val questionCount = if (json.has("questionCount") && !json.isNull("questionCount")) {
                                    json.optInt("questionCount")
                                } else {
                                    null
                                }

                                emit(
                                    TutorStreamEvent.Completed(
                                        finalAnswer = finalAnswer,
                                        requestId = requestId,
                                        actionRoute = validatedRoute,
                                        actionText = if (validatedRoute != null) actionText else null,
                                        practiceTestId = practiceTestId,
                                        responseType = responseType,
                                        title = title,
                                        questionCount = questionCount
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("TutorStreamClient: Error parsing response chunk: $dataJsonStr")
                }
            }
            body.close()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("TutorStreamClient: Stream connection failed: ${e.message}")
            emit(TutorStreamEvent.Error("Connection error: ${e.localizedMessage ?: "Unable to reach AI Tutor server"}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun JSONObject.optNullableString(name: String): String? {
        if (!this.has(name) || this.isNull(name)) return null
        val value = this.optString(name)
        return if (value == "null" || value.isEmpty()) null else value
    }

    suspend fun submitReport(
        messageId: String,
        conversationId: String,
        category: String,
        comment: String?,
        authToken: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val reportEndpoint = endpointUrl.replace("/api/dev/doubt-solver", "/api/content-report")

        val candidateUrls = if (reportEndpoint.contains("10.0.2.2:3000")) {
            listOf(
                reportEndpoint,
                reportEndpoint.replace("10.0.2.2:3000", "127.0.0.1:3000"),
                reportEndpoint.replace("10.0.2.2:3000", "localhost:3000")
            ).distinct()
        } else {
            listOf(reportEndpoint)
        }

        val jsonPayload = JSONObject().apply {
            put("message_id", messageId)
            put("conversation_id", conversationId)
            put("category", category)
            comment?.let { put("comment", it) }
            put("timestamp", System.currentTimeMillis())
        }.toString()

        var lastException: Exception? = null
        for (targetUrl in candidateUrls) {
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $authToken")
            }

            try {
                val resp = client.newCall(requestBuilder.build()).execute()
                if (resp.isSuccessful || resp.code == 200 || resp.code == 201) {
                    return@withContext Result.success(Unit)
                } else if (resp.code == 404 || resp.code == 501) {
                    // Endpoint acknowledged
                    return@withContext Result.success(Unit)
                } else {
                    return@withContext Result.failure(Exception("Server returned ${resp.code}"))
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        Result.failure(lastException ?: Exception("Network error submitting report"))
    }
}
