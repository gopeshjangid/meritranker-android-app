package com.example.meritrankerstudent.data.billing

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

/**
 * Clean data model for MeritRanker One-Time Credit Pack Configuration.
 * NO Subscriptions. NO Recurring billing.
 */
data class CreditPackConfig(
    val localPlanId: String,
    val googlePlayProductId: String,
    val purchaseOptionId: String? = null,
    val credits: Int,
    val displayLabel: String,
    val sortOrder: Int = 0,
    val isPopular: Boolean = false,
    val badgeText: String? = null
) {
    val isConfigured: Boolean
        get() = googlePlayProductId.isNotBlank()
}

/**
 * Authoritative Google Play localized product pricing & metadata for one-time INAPP products.
 */
data class PlayProductDetails(
    val productId: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val title: String,
    val description: String,
    val rawProductDetails: ProductDetails? = null
)

/**
 * UI presentation state representing a single credit pack card.
 */
data class CreditPackItemState(
    val config: CreditPackConfig,
    val googleProductDetails: PlayProductDetails?,
    val isAvailable: Boolean,
    val isConfigured: Boolean
)

/**
 * Secure purchase payload retained for subsequent backend verification and credit grant phase.
 * Never logs raw purchaseToken or exposes PII.
 */
data class DetectedPurchasePayload(
    val localPlanId: String?,
    val productId: String,
    val purchaseToken: String,
    val orderId: String?,
    val purchaseTime: Long,
    val purchaseState: Int,
    val tokenFingerprint: String,
    val rawJson: String? = null
) {
    companion object {
        fun computeFingerprint(token: String): String {
            return BillingSecurityUtils.computeTokenFingerprint(token)
        }

        fun fromGooglePurchase(
            purchase: Purchase,
            localPlanId: String? = null
        ): DetectedPurchasePayload {
            val productId = purchase.products.firstOrNull() ?: "unknown_product"
            return DetectedPurchasePayload(
                localPlanId = localPlanId,
                productId = productId,
                purchaseToken = purchase.purchaseToken,
                orderId = purchase.orderId,
                purchaseTime = purchase.purchaseTime,
                purchaseState = purchase.purchaseState,
                tokenFingerprint = computeFingerprint(purchase.purchaseToken),
                rawJson = purchase.originalJson
            )
        }
    }
}

/**
 * Explicit state machine for Google Play one-time credit purchase flow.
 */
sealed interface BillingState {
    data object Idle : BillingState
    data object Connecting : BillingState
    data object ProductLoading : BillingState
    data class Ready(
        val packs: List<CreditPackItemState>,
        val isConnected: Boolean = true
    ) : BillingState
    data class Launching(val localPlanId: String) : BillingState
    data class Pending(val payload: DetectedPurchasePayload) : BillingState
    data class Verifying(val payload: DetectedPurchasePayload) : BillingState
    data class Success(
        val creditsGranted: Int,
        val updatedBalance: Int,
        val payload: DetectedPurchasePayload
    ) : BillingState
    data class PurchasedUnverified(val payload: DetectedPurchasePayload) : BillingState
    data object UserCanceled : BillingState
    data class Error(
        val userMessage: String,
        val responseCode: Int = 0
    ) : BillingState
}
