package com.example.meritrankerstudent.data.billing

/**
 * MeritRanker One-Time Credit Pack Catalog & Product Configuration.
 * 
 * Strict Commercial Model:
 * - One-time purchases only (BillingClient.ProductType.INAPP)
 * - NO subscriptions
 * - NO recurring billing
 * - Real Google Play product IDs will be supplied/finalized by the developer in Play Console.
 */
object BillingConstants {

    // Real Google Play One-Time INAPP Products
    val DEFAULT_CREDIT_PACKS = listOf(
        CreditPackConfig(
            localPlanId = "pack_500",
            googlePlayProductId = "meritranker_credits_500",
            purchaseOptionId = "buy-500",
            credits = 500,
            displayLabel = "500 Credits",
            sortOrder = 1,
            isPopular = false,
            badgeText = null
        ),
        CreditPackConfig(
            localPlanId = "pack_1000",
            googlePlayProductId = "meritranker_credits_1000",
            purchaseOptionId = "buy-1000",
            credits = 1000,
            displayLabel = "1000 Credits",
            sortOrder = 2,
            isPopular = false,
            badgeText = null
        ),
        CreditPackConfig(
            localPlanId = "pack_1500",
            googlePlayProductId = "meritranker_credits_1500",
            purchaseOptionId = "buy-1500",
            credits = 1500,
            displayLabel = "1500 Credits",
            sortOrder = 3,
            isPopular = false,
            badgeText = null
        )
    )

    fun findPackByProductId(productId: String, customPacks: List<CreditPackConfig> = DEFAULT_CREDIT_PACKS): CreditPackConfig? {
        if (productId.isBlank()) return null
        return customPacks.firstOrNull { it.googlePlayProductId == productId }
    }

    fun findPackByPlanId(planId: String, customPacks: List<CreditPackConfig> = DEFAULT_CREDIT_PACKS): CreditPackConfig? {
        return customPacks.firstOrNull { it.localPlanId == planId }
    }

    fun getConfiguredProductIds(customPacks: List<CreditPackConfig> = DEFAULT_CREDIT_PACKS): List<String> {
        return customPacks.map { it.googlePlayProductId }.filter { it.isNotBlank() }.distinct()
    }
}
