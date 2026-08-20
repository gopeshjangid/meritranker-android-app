package com.example.meritrankerstudent.data.remote

import android.util.Log
import com.amplifyframework.api.aws.GsonVariablesSerializer
import com.amplifyframework.api.graphql.SimpleGraphQLRequest
import com.amplifyframework.core.Amplify
import com.example.meritrankerstudent.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class StudentPerformanceGraphQLClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val endpointUrl: String = "https://e7rfdkgqczag5bz34ov67locl4.appsync-api.ap-south-1.amazonaws.com/graphql"
) {

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val GET_STUDENT_PERFORMANCE_QUERY = """
            query GetStudentPerformance(
                ${'$'}examProfileId: String!,
                ${'$'}view: GetStudentPerformanceView,
                ${'$'}subjectId: String
            ) {
                getStudentPerformance(
                    examProfileId: ${'$'}examProfileId,
                    view: ${'$'}view,
                    subjectId: ${'$'}subjectId
                ) {
                    overall {
                        attempted
                        correct
                        wrong
                        skipped
                        accuracy
                        label
                        comment
                        averageTimeMs
                        targetPaceMs
                        speedLabel
                    }
                    items {
                        subjectId
                        subjectName
                        attempted
                        correct
                        wrong
                        skipped
                        accuracy
                        label
                        comment
                        averageTimeMs
                        targetPaceMs
                        speedLabel
                    }
                    insights {
                        subjectId
                        attemptId
                        questionId
                        descriptor
                        given
                        find
                        commonTrap
                        resultType
                        assessmentMode
                        createdAt
                    }
                    isUpdatingLatestPerformance
                    lastProcessedAt
                }
            }
        """

        internal fun parseResponse(data: JSONObject): StudentPerformanceResponse {
            val perfObj = data.optJSONObject("getStudentPerformance")
                ?: return emptyDefaultResponse()

            val overallObj = perfObj.optJSONObject("overall")
                ?: return emptyDefaultResponse()

            val overall = StudentPerformanceOverall(
                attempted = overallObj.optInt("attempted", 0),
                correct = overallObj.optInt("correct", 0),
                wrong = overallObj.optInt("wrong", 0),
                skipped = if (overallObj.has("skipped") && !overallObj.isNull("skipped")) overallObj.getInt("skipped") else null,
                accuracy = overallObj.optDouble("accuracy", 0.0),
                label = overallObj.optString("label", ""),
                comment = overallObj.optString("comment", ""),
                averageTimeMs = if (overallObj.has("averageTimeMs") && !overallObj.isNull("averageTimeMs")) overallObj.getLong("averageTimeMs") else null,
                targetPaceMs = if (overallObj.has("targetPaceMs") && !overallObj.isNull("targetPaceMs")) overallObj.getLong("targetPaceMs") else null,
                speedLabel = overallObj.optString("speedLabel").takeIf { it.isNotEmpty() }
            )

            val itemsArray = perfObj.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<StudentPerformanceItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(i) ?: continue
                items.add(
                    StudentPerformanceItem(
                        subjectId = itemObj.optString("subjectId"),
                        subjectName = itemObj.optString("subjectName"),
                        attempted = itemObj.optInt("attempted", 0),
                        correct = itemObj.optInt("correct", 0),
                        wrong = itemObj.optInt("wrong", 0),
                        skipped = if (itemObj.has("skipped") && !itemObj.isNull("skipped")) itemObj.getInt("skipped") else null,
                        accuracy = itemObj.optDouble("accuracy", 0.0),
                        label = itemObj.optString("label", ""),
                        comment = itemObj.optString("comment", ""),
                        averageTimeMs = if (itemObj.has("averageTimeMs") && !itemObj.isNull("averageTimeMs")) itemObj.getLong("averageTimeMs") else null,
                        targetPaceMs = if (itemObj.has("targetPaceMs") && !itemObj.isNull("targetPaceMs")) itemObj.getLong("targetPaceMs") else null,
                        speedLabel = itemObj.optString("speedLabel").takeIf { it.isNotEmpty() }
                    )
                )
            }

            val insightsArray = perfObj.optJSONArray("insights") ?: JSONArray()
            val insights = mutableListOf<StudentPerformanceInsight>()
            for (i in 0 until insightsArray.length()) {
                val insightObj = insightsArray.optJSONObject(i) ?: continue
                insights.add(
                    StudentPerformanceInsight(
                        subjectId = insightObj.optString("subjectId"),
                        attemptId = insightObj.optString("attemptId"),
                        questionId = insightObj.optString("questionId"),
                        descriptor = insightObj.optString("descriptor"),
                        given = insightObj.optString("given"),
                        find = insightObj.optString("find"),
                        commonTrap = insightObj.optString("commonTrap").takeIf { it.isNotEmpty() },
                        resultType = insightObj.optString("resultType", "UNKNOWN"),
                        assessmentMode = insightObj.optString("assessmentMode", "PRACTICE"),
                        createdAt = insightObj.optString("createdAt", "")
                    )
                )
            }

            val isUpdatingLatestPerformance = perfObj.optBoolean("isUpdatingLatestPerformance", false)
            val lastProcessedAt = perfObj.optString("lastProcessedAt").takeIf { it.isNotEmpty() }

            return StudentPerformanceResponse(
                overall = overall,
                items = items,
                insights = insights,
                isUpdatingLatestPerformance = isUpdatingLatestPerformance,
                lastProcessedAt = lastProcessedAt
            )
        }

        internal fun emptyDefaultResponse(): StudentPerformanceResponse {
            return StudentPerformanceResponse(
                overall = StudentPerformanceOverall(
                    attempted = 0,
                    correct = 0,
                    wrong = 0,
                    skipped = 0,
                    accuracy = 0.0,
                    label = "",
                    comment = ""
                ),
                items = emptyList(),
                insights = emptyList(),
                isUpdatingLatestPerformance = false,
                lastProcessedAt = null
            )
        }
    }

    suspend fun getStudentPerformance(
        examProfileId: String,
        view: GetStudentPerformanceView = GetStudentPerformanceView.PRACTICE,
        subjectId: String? = null,
        authToken: String? = null
    ): StudentPerformanceResponse {
        // 1. Try Amplify API query
        val amplifyResult = try {
            withTimeoutOrNull(6000L) {
                suspendCancellableCoroutine<StudentPerformanceResponse?> { continuation ->
                    val variables = mutableMapOf<String, Any>(
                        "examProfileId" to examProfileId,
                        "view" to view.name
                    )
                    if (!subjectId.isNullOrBlank()) {
                        variables["subjectId"] = subjectId
                    }

                    val request = SimpleGraphQLRequest<String>(
                        GET_STUDENT_PERFORMANCE_QUERY.trimIndent(),
                        variables,
                        String::class.java,
                        GsonVariablesSerializer()
                    )

                    try {
                        Amplify.API.query(
                            request,
                            { response ->
                                if (continuation.isActive) {
                                    if (response.hasErrors()) {
                                        Log.w("StudentPerformanceClient", "Amplify GraphQL error: ${response.errors.firstOrNull()?.message}")
                                        continuation.resume(null)
                                    } else if (response.data != null && response.data != "null") {
                                        try {
                                            val json = JSONObject(response.data)
                                            continuation.resume(parseResponse(json))
                                        } catch (e: Exception) {
                                            continuation.resume(null)
                                        }
                                    } else {
                                        continuation.resume(null)
                                    }
                                }
                            },
                            { error ->
                                Log.w("StudentPerformanceClient", "Amplify API query failure: ${error.message}")
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }

        if (amplifyResult != null) {
            return amplifyResult
        }

        // 2. Direct HTTP call with OkHttp
        val variables = JSONObject().apply {
            put("examProfileId", examProfileId)
            put("view", view.name)
            if (!subjectId.isNullOrBlank()) {
                put("subjectId", subjectId)
            }
        }

        return try {
            val data = withTimeoutOrNull(6000L) {
                executeGraphQL(GET_STUDENT_PERFORMANCE_QUERY, variables, authToken)
            }
            if (data != null) {
                parseResponse(data)
            } else {
                emptyDefaultResponse()
            }
        } catch (e: Exception) {
            Log.w("StudentPerformanceClient", "Direct HTTP failed: ${e.message}, returning empty default response")
            emptyDefaultResponse()
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
            .addHeader("Authorization", authToken)

        val response = client.newCall(requestBuilder.build()).execute()
        val bodyString = response.body?.string() ?: throw IOException("Empty response from AppSync")

        if (!response.isSuccessful) {
            throw IOException("GraphQL HTTP ${response.code}: $bodyString")
        }

        val json = JSONObject(bodyString)
        if (json.has("errors")) {
            val errors = json.getJSONArray("errors")
            if (errors.length() > 0) {
                val firstError = errors.getJSONObject(0)
                val message = firstError.optString("message", "Unknown GraphQL error")
                val errorType = firstError.optString("errorType", "")
                throw IOException("GraphQL Error ($errorType): $message")
            }
        }

        json.optJSONObject("data") ?: throw IOException("Missing data in GraphQL response: $bodyString")
    }
}
