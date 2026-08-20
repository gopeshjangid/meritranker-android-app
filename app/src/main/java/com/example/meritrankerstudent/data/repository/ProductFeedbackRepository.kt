package com.example.meritrankerstudent.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.amplifyframework.api.graphql.SimpleGraphQLRequest
import com.amplifyframework.api.graphql.model.ModelMutation
import com.amplifyframework.core.Amplify
import com.amplifyframework.api.aws.GsonVariablesSerializer
import com.example.meritrankerstudent.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ProductFeedbackRepository {
    suspend fun submitFeedback(
        category: String,
        message: String,
        screenContext: String? = null
    ): Result<String>
}

class AppSyncProductFeedbackRepository(
    private val context: Context,
    private val authRepository: AuthRepository
) : ProductFeedbackRepository {

    companion object {
        private const val TAG = "ProductFeedbackRepo"
    }

    override suspend fun submitFeedback(
        category: String,
        message: String,
        screenContext: String?
    ): Result<String> = runCatching {
        val currentUserId = authRepository.getCurrentUserId()
            ?: throw IllegalStateException("User must be authenticated to submit feedback")

        val ticketId = UUID.randomUUID().toString()
        val subject = "[$category] MeritRanker App Feedback"
        val formattedDescription = buildString {
            append("Category: ").append(category).append("\n")
            if (message.isNotBlank()) {
                append("Comments: ").append(message).append("\n\n")
            } else {
                append("Comments: (None provided)\n\n")
            }
            if (!screenContext.isNullOrBlank()) {
                append("Context: ").append(screenContext).append("\n")
            }
            append("App Version: v").append(BuildConfig.VERSION_NAME).append(" (Build ").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Android SDK: ").append(Build.VERSION.SDK_INT).append("\n")
            append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
            append("Submitted At: ").append(java.time.Instant.now().toString())
        }

        val mutationDoc = """
            mutation CreateSupport(${'$'}input: CreateSupportInput!) {
                createSupport(input: ${'$'}input) {
                    id
                    userId
                    subject
                    description
                    status
                    priority
                    createdAt
                }
            }
        """.trimIndent()

        val input = mapOf<String, Any>(
            "id" to ticketId,
            "userId" to currentUserId,
            "subject" to subject,
            "description" to formattedDescription,
            "status" to "OPEN",
            "priority" to "MEDIUM"
        )

        val request = SimpleGraphQLRequest<String>(
            mutationDoc,
            mapOf("input" to input),
            String::class.java,
            GsonVariablesSerializer()
        )

        suspendCancellableCoroutine { continuation ->
            Amplify.API.mutate(
                request,
                { response ->
                    if (continuation.isActive) {
                        if (response.hasErrors()) {
                            val errMsg = response.errors.firstOrNull()?.message ?: "Feedback submission error"
                            Log.e(TAG, "GraphQL error submitting feedback: $errMsg")
                            continuation.resumeWithException(Exception(errMsg))
                        } else if (response.data == null || response.data == "null") {
                            Log.e(TAG, "Empty response received on feedback submission")
                            continuation.resumeWithException(Exception("Empty response from feedback service"))
                        } else {
                            try {
                                val json = JSONObject(response.data)
                                val created = json.optJSONObject("createSupport")
                                val createdId = created?.optString("id") ?: ticketId
                                Log.i(TAG, "Feedback submitted successfully. Ticket ID: $createdId")
                                continuation.resume(createdId)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse createSupport response, using ticket ID", e)
                                continuation.resume(ticketId)
                            }
                        }
                    }
                },
                { failure ->
                    Log.e(TAG, "Network failure submitting feedback: ${failure.message}", failure)
                    if (continuation.isActive) {
                        continuation.resumeWithException(failure)
                    }
                }
            )
        }
    }
}
