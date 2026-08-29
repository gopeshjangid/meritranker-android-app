package com.example.meritrankerstudent.data.billing

import java.security.MessageDigest

/**
 * Security utilities for Google Play Billing (PBL 9.1.0).
 * Handles safe, deterministic, non-PII user attribution (obfuscatedAccountId)
 * and secure purchase token hashing for transaction idempotency.
 */
object BillingSecurityUtils {

    /**
     * Computes a deterministic SHA-256 hex string from the input (e.g. Cognito userId/sub).
     * Guaranteed to be exactly 64 hex characters (256 bits), non-reversible, and non-PII.
     * Complies with Google Play maximum 64-character limit for obfuscatedAccountId.
     */
    fun sha256Hex(input: String): String {
        if (input.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a safe, non-PII obfuscated account identifier for Google Play BillingFlowParams.
     * Derived deterministically from the authenticated MeritRanker user ID.
     */
    fun generateObfuscatedAccountId(userId: String?): String? {
        if (userId.isNullOrBlank()) return null
        return sha256Hex(userId.trim())
    }

    /**
     * Computes a short safe diagnostic fingerprint of a purchase token (16 hex chars).
     * Used for safe client-side logging without exposing the raw token.
     */
    fun computeTokenFingerprint(purchaseToken: String?): String {
        if (purchaseToken.isNullOrBlank()) return "empty_token"
        val hash = sha256Hex(purchaseToken)
        return hash.take(16)
    }
}
