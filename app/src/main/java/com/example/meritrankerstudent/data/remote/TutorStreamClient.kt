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
    data class Chunk(val text: String, val requestId: String?) : TutorStreamEvent
    data class Completed(
        val finalAnswer: String,
        val requestId: String?,
        val actionRoute: String? = null,
        val actionText: String? = null,
        val practiceTestId: String? = null
    ) : TutorStreamEvent
    data class PracticeGenerationStarted(
        val practiceTestId: String,
        val status: String,
        val message: String,
        val requestId: String?
    ) : TutorStreamEvent
    data class Error(val message: String) : TutorStreamEvent
}

data class TutorRequestPayload(
    val conversationId: String,
    val turnId: String,
    val userId: String,
    val query: String,
    val examProfileId: String? = null,
    val examId: String? = "SSC_CGL",
    val examStage: String? = "Tier 1",
    val language: String = "en",
    val mode: String = "doubt_solver",
    val stream: Boolean = true,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

class TutorStreamClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val endpointUrl: String = "${BuildConfig.TUTOR_API_BASE_URL}/api/dev/doubt-solver"
) {

    companion object {
        val ALLOWED_ACTION_ROUTES = setOf("QUIZ", "MOCK", "PYQ")

        fun buildJsonPayload(payload: TutorRequestPayload): JSONObject {
            return JSONObject().apply {
                put("conversation_id", payload.conversationId)
                put("turn_id", payload.turnId)
                put("user_id", payload.userId)
                put("query", payload.query)
                payload.examProfileId?.let { put("exam_profile_id", it) }
                payload.examId?.let { put("exam_id", it) }
                payload.examStage?.let { put("exam_stage", it) }
                put("language", payload.language)
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

        val candidateUrls = if (endpointUrl.contains("10.0.2.2:3000")) {
            listOf(
                endpointUrl,
                endpointUrl.replace("10.0.2.2:3000", "127.0.0.1:3000"),
                endpointUrl.replace("10.0.2.2:3000", "localhost:3000")
            ).distinct()
        } else {
            listOf(endpointUrl)
        }

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
                response = resp
                break
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
                if (currentLine.startsWith("data:")) {
                    val dataJsonStr = currentLine.removePrefix("data:").trim()
                    if (dataJsonStr.isEmpty()) continue

                    try {
                        val json = JSONObject(dataJsonStr)
                        val type = json.optString("type")
                        val requestId = json.optNullableString("request_id")

                        when (type) {
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
                                val message = dataObj?.optNullableString("message") ?: "Preparing your practice set..."
                                if (!practiceTestId.isNullOrEmpty()) {
                                    emit(
                                        TutorStreamEvent.PracticeGenerationStarted(
                                            practiceTestId = practiceTestId,
                                            status = status,
                                            message = message,
                                            requestId = requestId
                                        )
                                    )
                                }
                            }
                            "complete" -> {
                                val responseObj = json.optJSONObject("response")
                                val finalAnswer = responseObj?.optNullableString("answer")
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

                                val practiceTestId = responseObj?.optNullableString("practiceTestId")
                                    ?: json.optNullableString("practiceTestId")
                                    ?: (if (json.optJSONObject("data")?.optString("responseType") == "practice_generation")
                                        json.optJSONObject("data")?.optNullableString("practiceTestId") else null)

                                emit(
                                    TutorStreamEvent.Completed(
                                        finalAnswer = finalAnswer,
                                        requestId = requestId,
                                        actionRoute = validatedRoute,
                                        actionText = if (validatedRoute != null) actionText else null,
                                        practiceTestId = practiceTestId
                                    )
                                )
                            }
                            else -> {
                                if (json.has("success") && !json.getBoolean("success")) {
                                    val errAnswer = json.optString("answer", "Unknown AI error")
                                    emit(TutorStreamEvent.Error(errAnswer))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        System.err.println("TutorStreamClient: Error parsing SSE JSON chunk: $dataJsonStr")
                    }
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
