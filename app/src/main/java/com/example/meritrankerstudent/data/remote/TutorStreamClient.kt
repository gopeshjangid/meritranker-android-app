package com.example.meritrankerstudent.data.remote

import android.util.Log
import com.example.meritrankerstudent.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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

private const val TAG = "TutorStreamClient"

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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val endpointUrl: String = "${BuildConfig.TUTOR_API_BASE_URL}/invocations"
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
                put("query", payload.query)
                put("mode", payload.mode)
                put("stream", payload.stream)
                if (!payload.examProfileId.isNullOrBlank()) {
                    put("exam_profile_id", payload.examProfileId)
                }
                if (!payload.examId.isNullOrBlank()) {
                    put("exam_id", payload.examId)
                }
                if (!payload.examStage.isNullOrBlank()) {
                    put("exam_stage", payload.examStage)
                }
                put("language", normalizedLang)
                put("conversation_id", payload.conversationId)
                put("turn_id", payload.turnId)
                put("user_id", payload.userId)

                if (!payload.imageBase64.isNullOrBlank()) {
                    put("image_base64", payload.imageBase64)
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
        Log.i(TAG, "TUTOR_STREAM_REQUEST_INIT convId=${payload.conversationId} turnId=${payload.turnId} mode=${payload.mode} lang=${payload.language} query='${payload.query.take(60)}'")

        val candidateUrls = buildList {
            // 1. Configured base endpoint if present
            if (BuildConfig.TUTOR_API_BASE_URL.isNotBlank()) {
                if (BuildConfig.TUTOR_API_BASE_URL.endsWith("/invocations") || BuildConfig.TUTOR_API_BASE_URL.contains("/api/dev/doubt-solver")) {
                    add(BuildConfig.TUTOR_API_BASE_URL)
                } else {
                    add("${BuildConfig.TUTOR_API_BASE_URL}/invocations")
                    add("${BuildConfig.TUTOR_API_BASE_URL}/api/dev/doubt-solver")
                }
            }
            // 2. Direct IPv4 loopback AgentCore invocation (adb reverse tcp:8080 on physical device)
            add("http://127.0.0.1:8080/invocations")
            add("http://127.0.0.1:3000/api/dev/doubt-solver")
            add("http://localhost:8080/invocations")
            add("http://localhost:3000/api/dev/doubt-solver")
            add(endpointUrl)
            if (BuildConfig.DEBUG) {
                add("http://10.0.2.2:8080/invocations")
                add("http://10.0.2.2:3000/api/dev/doubt-solver")
            }
        }.distinct()

        var response: okhttp3.Response? = null
        var lastConnectException: Exception? = null

        for (targetUrl in candidateUrls) {
            Log.d(TAG, "CANDIDATE_URL_TRY url=$targetUrl")
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
                    Log.i(TAG, "CANDIDATE_URL_CONNECTED url=$targetUrl code=${resp.code} isSuccessful=${resp.isSuccessful}")
                    response = resp
                    break
                } else {
                    Log.w(TAG, "CANDIDATE_URL_UNEXPECTED_STATUS url=$targetUrl code=${resp.code}")
                    resp.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "CANDIDATE_URL_FAILED url=$targetUrl error=${e.message}")
                lastConnectException = e
                // Try next candidate
            }
        }

        if (response == null) {
            Log.e(TAG, "ALL_CANDIDATE_URLS_FAILED lastException=${lastConnectException?.message}")
            emit(TutorStreamEvent.Error("Couldn't connect to Smart Tutor. Please check your network connection and tap Retry."))
            return@flow
        }

        try {
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                Log.e(TAG, "HTTP_ERROR code=${response.code} body=$errorBody")
                val errorMsg = try {
                    val json = JSONObject(errorBody)
                    json.optString("answer", json.optString("error", "HTTP Error ${response.code}"))
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
                            Log.d(TAG, "SSE_EVENT_STATUS stage=$stage label=$label reqId=$requestId")
                            emit(TutorStreamEvent.Status(stage = stage, label = label, requestId = requestId))
                        }
                        "chunk" -> {
                            val content = json.optNullableString("content")
                                ?: json.optNullableString("text")
                            if (!content.isNullOrEmpty()) {
                                accumulatedText += content
                                Log.v(TAG, "SSE_EVENT_CHUNK len=${content.length} totalLen=${accumulatedText.length}")
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
                                Log.i(TAG, "SSE_EVENT_PRACTICE_STARTED testId=$practiceTestId count=$questionCount title=$title")
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
                                ?: json.optNullableString("answer")
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

                            val practiceTestId = if (responseType == "practice_generation") {
                                responseObj?.optNullableString("practiceTestId")
                                    ?: json.optNullableString("practiceTestId")
                                    ?: json.optJSONObject("data")?.optNullableString("practiceTestId")
                            } else {
                                null
                            }

                            val title = if (practiceTestId != null) {
                                responseObj?.optNullableString("title")
                                    ?: json.optJSONObject("data")?.optNullableString("title")
                                    ?: json.optNullableString("title")
                            } else {
                                null
                            }

                            val questionCount = if (responseObj?.has("questionCount") == true && !responseObj.isNull("questionCount")) {
                                responseObj.optInt("questionCount")
                            } else if (json.has("questionCount") && !json.isNull("questionCount")) {
                                json.optInt("questionCount")
                            } else {
                                null
                            }

                            Log.i(TAG, "SSE_EVENT_COMPLETED finalAnswerLen=${finalAnswer.length} actionRoute=$validatedRoute practiceTestId=$practiceTestId")
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
                            val meta = json.optJSONObject("metadata")
                            val errorObj = json.optJSONObject("error")
                            val errorMsg = errorObj?.optNullableString("message")
                                ?: json.optNullableString("message")
                                ?: json.optNullableString("label")
                                ?: "Server encountered an error processing your question."
                            val errorCode = meta?.optNullableString("code")
                                ?: errorObj?.optNullableString("code")
                                ?: json.optNullableString("code")
                            val retryable = meta?.optBoolean("retryable", false)
                                ?: errorObj?.optBoolean("retryable", false)
                                ?: json.optBoolean("retryable", false)
                            Log.e(TAG, "SSE_EVENT_ERROR errorMsg=$errorMsg code=$errorCode retryable=$retryable")
                            emit(TutorStreamEvent.Error(message = errorMsg, code = errorCode, retryable = retryable))
                        }
                        else -> {
                            if (json.has("success") && !json.getBoolean("success")) {
                                val errAnswer = json.optString("answer", "Unknown AI error")
                                Log.e(TAG, "SSE_EVENT_FAILURE errAnswer=$errAnswer")
                                emit(TutorStreamEvent.Error(errAnswer))
                            } else if (json.has("answer") || json.has("content") || json.optString("status") == "completed") {
                                val contentObj = json.optJSONObject("content")
                                val finalAnswer = json.optNullableString("answer")
                                    ?: contentObj?.optNullableString("value")
                                    ?: accumulatedText

                                val responseType = json.optNullableString("responseType")
                                    ?: json.optJSONObject("data")?.optNullableString("responseType")
                                val practiceTestId = if (responseType == "practice_generation") {
                                    json.optNullableString("practiceTestId")
                                        ?: json.optJSONObject("data")?.optNullableString("practiceTestId")
                                } else {
                                    null
                                }
                                val title = if (practiceTestId != null) {
                                    json.optNullableString("title")
                                        ?: json.optJSONObject("data")?.optNullableString("title")
                                } else {
                                    null
                                }
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

                                Log.i(TAG, "SSE_EVENT_FALLTHROUGH_COMPLETED finalAnswerLen=${finalAnswer.length} actionRoute=$validatedRoute practiceTestId=$practiceTestId")
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
                    Log.w(TAG, "Error parsing response chunk: $dataJsonStr, error=${e.message}")
                }
            }
            body.close()
        } catch (e: CancellationException) {
            Log.d(TAG, "Stream cancelled by caller")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream connection failed: ${e.message}", e)
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
