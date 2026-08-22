package com.example.meritrankerstudent.data.billing

import java.util.UUID

enum class PurchaseTransactionStatus {
    PURCHASED,
    PENDING,
    USER_CANCELED,
    ERROR,
    REFUNDED
}

enum class ProductType {
    SUBSCRIPTION,
    IN_APP
}

data class PlayProductDetails(
    val productId: String,
    val productType: ProductType,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val billingPeriod: String? = null,
    val offerToken: String? = null,
    val features: List<String> = emptyList(),
    val badge: String? = null,
    val isPurchased: Boolean = false
)

data class PurchaseTransactionRecord(
    val transactionId: String = UUID.randomUUID().toString(),
    val userId: String,
    val orderId: String?,
    val purchaseToken: String,
    val productId: String,
    val productTitle: String,
    val productType: ProductType,
    val purchaseTime: Long = System.currentTimeMillis(),
    val status: PurchaseTransactionStatus,
    val isAcknowledged: Boolean = false,
    val isSyncedWithBackend: Boolean = false,
    val responseCode: Int = 0,
    val errorMessage: String? = null,
    val rawJsonPayload: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

sealed interface BillingUiState {
    data object Idle : BillingUiState
    data object Connecting : BillingUiState
    data class Ready(
        val products: List<PlayProductDetails>,
        val activeSubscription: PurchaseTransactionRecord?,
        val recentTransactions: List<PurchaseTransactionRecord>
    ) : BillingUiState
    data class Purchasing(val productId: String) : BillingUiState
    data class PurchaseSuccess(
        val transaction: PurchaseTransactionRecord,
        val message: String = "Subscription activated! You have full access to MeritRanker Pro."
    ) : BillingUiState
    data class PurchasePending(
        val transaction: PurchaseTransactionRecord,
        val message: String = "Payment pending confirmation by Google Play. Benefits will unlock once payment clears."
    ) : BillingUiState
    data class PurchaseCanceled(
        val productId: String,
        val message: String = "Purchase was canceled. No charges were made."
    ) : BillingUiState
    data class PurchaseError(
        val errorCode: Int,
        val message: String
    ) : BillingUiState
}
