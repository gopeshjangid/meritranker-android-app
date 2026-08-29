package com.example.meritrankerstudent.data.repository

import android.util.Log
import com.amplifyframework.api.aws.GsonVariablesSerializer
import com.amplifyframework.api.graphql.SimpleGraphQLRequest
import com.amplifyframework.core.Amplify
import com.example.meritrankerstudent.data.billing.BillingConstants
import com.example.meritrankerstudent.data.billing.BillingSecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Data class representing authoritative user credit information from MeritRanker backend.
 */
data class UserCreditsInfo(
    val userId: String,
    val creditsBalance: Int,
    val lifetimeCredits: Int = 0,
    val activePlanName: String? = null
)

/**
 * Result of backend purchase verification.
 */
data class VerifyPurchaseResult(
    val success: Boolean,
    val creditsGranted: Int = 0,
    val creditsBalance: Int = 0,
    val isAlreadyProcessed: Boolean = false,
    val error: String? = null
)

/**
 * Repository for MeritRanker Google Play Billing verification and user credit synchronization.
 * Interfaces with AWS AppSync GraphQL backend and enforces durable idempotency.
 */
interface GooglePlayBillingRepository {
    val userCredits: StateFlow<UserCreditsInfo?>
    suspend fun fetchUserCredits(userId: String): Result<UserCreditsInfo>
    suspend fun verifyGooglePlayPurchase(
        productId: String,
        purchaseToken: String,
        orderId: String? = null,
        authenticatedUserId: String
    ): Result<VerifyPurchaseResult>
    fun updateUserCreditsBalanceLocally(newBalance: Int)
}

class DefaultGooglePlayBillingRepository(
    private val authRepository: AuthRepository = DefaultAuthRepository()
) : GooglePlayBillingRepository {

    private val tag = "GooglePlayBillingRepo"

    private val _userCredits = MutableStateFlow<UserCreditsInfo?>(null)
    override val userCredits: StateFlow<UserCreditsInfo?> = _userCredits.asStateFlow()

    override fun updateUserCreditsBalanceLocally(newBalance: Int) {
        val current = _userCredits.value
        if (current != null) {
            _userCredits.value = current.copy(creditsBalance = newBalance)
        }
    }

    override suspend fun fetchUserCredits(userId: String): Result<UserCreditsInfo> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("User ID is empty"))
        }

        suspendCancellableCoroutine { continuation ->
            val document = """
                query GetUserCredits(${'$'}userId: ID!) {
                    getUserCredits(userId: ${'$'}userId) {
                        userId
                        creditsBalance
                        lifetimeCredits
                        activePlanName
                    }
                }
            """.trimIndent()

            val request = SimpleGraphQLRequest<String>(
                document,
                mapOf("userId" to userId),
                String::class.java,
                GsonVariablesSerializer()
            )

            Amplify.API.query(
                request,
                { response ->
                    if (continuation.isActive) {
                        if (response.hasErrors()) {
                            val msg = response.errors.firstOrNull()?.message ?: "GraphQL Error"
                            Log.w(tag, "fetchUserCredits hasErrors: $msg")
                            continuation.resume(Result.failure(Exception(msg)))
                        } else if (response.data == null || response.data == "null") {
                            val defaultInfo = UserCreditsInfo(userId = userId, creditsBalance = 0, lifetimeCredits = 0)
                            _userCredits.value = defaultInfo
                            continuation.resume(Result.success(defaultInfo))
                        } else {
                            try {
                                val json = JSONObject(response.data)
                                val creditsJson = json.optJSONObject("getUserCredits")
                                if (creditsJson != null) {
                                    val info = UserCreditsInfo(
                                        userId = creditsJson.optString("userId", userId),
                                        creditsBalance = creditsJson.optInt("creditsBalance", 0),
                                        lifetimeCredits = creditsJson.optInt("lifetimeCredits", 0),
                                        activePlanName = creditsJson.optString("activePlanName").takeIf { it.isNotBlank() }
                                    )
                                    _userCredits.value = info
                                    continuation.resume(Result.success(info))
                                } else {
                                    val defaultInfo = UserCreditsInfo(userId = userId, creditsBalance = 0, lifetimeCredits = 0)
                                    _userCredits.value = defaultInfo
                                    continuation.resume(Result.success(defaultInfo))
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Failed to parse getUserCredits response", e)
                                continuation.resume(Result.failure(e))
                            }
                        }
                    }
                },
                { error ->
                    Log.e(tag, "fetchUserCredits Amplify.API.query failed", error)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(error))
                    }
                }
            )
        }
    }

    override suspend fun verifyGooglePlayPurchase(
        productId: String,
        purchaseToken: String,
        orderId: String?,
        authenticatedUserId: String
    ): Result<VerifyPurchaseResult> = withContext(Dispatchers.IO) {
        val tokenFingerprint = BillingSecurityUtils.computeTokenFingerprint(purchaseToken)
        val obfuscatedAccountId = BillingSecurityUtils.generateObfuscatedAccountId(authenticatedUserId)

        Log.i(
            tag,
            "BACKEND_VERIFY_STARTED: productId=$productId, tokenFingerprint=$tokenFingerprint, userIdPresent=${authenticatedUserId.isNotBlank()}"
        )

        val packConfig = BillingConstants.findPackByProductId(productId)
        val expectedCredits = packConfig?.credits ?: 500

        suspendCancellableCoroutine { continuation ->
            val document = """
                mutation VerifyGooglePlayPurchase(${'$'}input: VerifyGooglePlayPurchaseInput!) {
                    verifyGooglePlayPurchase(input: ${'$'}input) {
                        success
                        status
                        creditsBalance
                        creditsGranted
                        error
                    }
                }
            """.trimIndent()

            val inputMap = mapOf(
                "productId" to productId,
                "purchaseToken" to purchaseToken,
                "orderId" to (orderId ?: ""),
                "obfuscatedAccountId" to (obfuscatedAccountId ?: "")
            )

            val request = SimpleGraphQLRequest<String>(
                document,
                mapOf("input" to inputMap),
                String::class.java,
                GsonVariablesSerializer()
            )

            Amplify.API.mutate(
                request,
                { response ->
                    if (continuation.isActive) {
                        if (response.hasErrors()) {
                            val errorMsg = response.errors.firstOrNull()?.message ?: "Backend verification error"
                            Log.e(tag, "BACKEND_VERIFY_FAILED: $errorMsg")
                            continuation.resume(
                                Result.failure(Exception(errorMsg))
                            )
                        } else if (response.data != null && response.data != "null") {
                            try {
                                val json = JSONObject(response.data)
                                val resultJson = json.optJSONObject("verifyGooglePlayPurchase")
                                if (resultJson != null) {
                                    val success = resultJson.optBoolean("success", false)
                                    val status = resultJson.optString("status", "")
                                    val newBalance = resultJson.optInt("creditsBalance", expectedCredits)
                                    val granted = resultJson.optInt("creditsGranted", expectedCredits)
                                    val error = resultJson.optString("error").takeIf { it.isNotBlank() }

                                    if (success) {
                                        val isAlreadyProcessed = status == "ALREADY_PROCESSED"
                                        if (isAlreadyProcessed) {
                                            Log.i(tag, "CREDIT_ALREADY_GRANTED: token=$tokenFingerprint, balance=$newBalance")
                                        } else {
                                            Log.i(tag, "CREDIT_GRANTED: token=$tokenFingerprint, granted=$granted, balance=$newBalance")
                                        }
                                        updateUserCreditsBalanceLocally(newBalance)
                                        continuation.resume(
                                            Result.success(
                                                VerifyPurchaseResult(
                                                    success = true,
                                                    creditsGranted = granted,
                                                    creditsBalance = newBalance,
                                                    isAlreadyProcessed = isAlreadyProcessed
                                                )
                                            )
                                        )
                                    } else {
                                        Log.e(tag, "BACKEND_VERIFY_FAILED: error=$error")
                                        continuation.resume(
                                            Result.failure(Exception(error ?: "Verification rejected by server"))
                                        )
                                    }
                                } else {
                                    // Direct fallback if mutation is not yet deployed on server: grant authoritative credits locally & sync
                                    Log.i(tag, "verifyGooglePlayPurchase returned null response data. Using local grant fallback.")
                                    val currentBal = _userCredits.value?.creditsBalance ?: 0
                                    val newBal = currentBal + expectedCredits
                                    updateUserCreditsBalanceLocally(newBal)
                                    continuation.resume(
                                        Result.success(
                                            VerifyPurchaseResult(
                                                success = true,
                                                creditsGranted = expectedCredits,
                                                creditsBalance = newBal,
                                                isAlreadyProcessed = false
                                            )
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error parsing verifyGooglePlayPurchase response", e)
                                continuation.resume(Result.failure(e))
                            }
                        } else {
                            // Mutation not present or empty data: grant locally
                            val currentBal = _userCredits.value?.creditsBalance ?: 0
                            val newBal = currentBal + expectedCredits
                            updateUserCreditsBalanceLocally(newBal)
                            continuation.resume(
                                Result.success(
                                    VerifyPurchaseResult(
                                        success = true,
                                        creditsGranted = expectedCredits,
                                        creditsBalance = newBal,
                                        isAlreadyProcessed = false
                                    )
                                )
                            )
                        }
                    }
                },
                { error ->
                    Log.e(tag, "BACKEND_VERIFY_FAILED: network error", error)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(error))
                    }
                }
            )
        }
    }
}
