package com.example.meritrankerstudent.data.billing

object BillingConstants {

    // Subscription Product IDs (Configured in Google Play Console)
    const val PRODUCT_PRO_MONTHLY = "meritranker_pro_monthly"
    const val PRODUCT_PRO_YEARLY = "meritranker_pro_yearly"

    // In-App One-Time Booster Product IDs
    const val PRODUCT_CREDITS_100 = "meritranker_credits_100"
    const val PRODUCT_MOCK_PACK_10 = "meritranker_mock_pack_10"

    val ALL_SUBSCRIPTION_IDS = listOf(
        PRODUCT_PRO_MONTHLY,
        PRODUCT_PRO_YEARLY
    )

    val ALL_IN_APP_IDS = listOf(
        PRODUCT_CREDITS_100,
        PRODUCT_MOCK_PACK_10
    )

    // Feature bullet points for plan cards
    val PRO_FEATURES = listOf(
        "Unlimited Smart Tutor AI Doubt Solving",
        "Step-by-step Mathematical & Reasoning Solutions",
        "Adaptive AI Mock Tests with Instant Scoring",
        "Deep Sectional Weakness & Accuracy Analytics",
        "Ad-free & Priority AI Response Speed"
    )

    val CREDITS_FEATURES = listOf(
        "100 AI Doubt Solving Prompt Credits",
        "Instant Step-by-Step Question Breakdown",
        "No Expiry on Unused Credits"
    )

    val MOCKS_FEATURES = listOf(
        "10 Full-Length Exam Simulation Tests",
        "Detailed Performance & Rank Analytics",
        "Explanations for all 1,000+ Questions"
    )

    // Fallback product catalog when BillingClient returns empty (e.g. debug builds or offline)
    val DEFAULT_PRODUCTS = listOf(
        PlayProductDetails(
            productId = PRODUCT_PRO_MONTHLY,
            productType = ProductType.SUBSCRIPTION,
            title = "MeritRanker Pro (Monthly)",
            description = "Unlimited AI Doubt Solving, Mock Tests & Full Analytics",
            formattedPrice = "₹499 / mo",
            priceAmountMicros = 499000000L,
            priceCurrencyCode = "INR",
            billingPeriod = "P1M",
            features = PRO_FEATURES,
            badge = "Flexible"
        ),
        PlayProductDetails(
            productId = PRODUCT_PRO_YEARLY,
            productType = ProductType.SUBSCRIPTION,
            title = "MeritRanker Pro (Annual)",
            description = "Full Exam Preparation Suite for 12 Months",
            formattedPrice = "₹3,999 / yr",
            priceAmountMicros = 3999000000L,
            priceCurrencyCode = "INR",
            billingPeriod = "P1Y",
            features = PRO_FEATURES,
            badge = "Save 33% • Best Value"
        ),
        PlayProductDetails(
            productId = PRODUCT_CREDITS_100,
            productType = ProductType.IN_APP,
            title = "100 AI Tutor Credits",
            description = "Ask 100 questions with instant AI step-by-step explanations",
            formattedPrice = "₹99",
            priceAmountMicros = 99000000L,
            priceCurrencyCode = "INR",
            features = CREDITS_FEATURES,
            badge = "Booster"
        ),
        PlayProductDetails(
            productId = PRODUCT_MOCK_PACK_10,
            productType = ProductType.IN_APP,
            title = "10 Full Mock Test Pack",
            description = "10 Full-length exam practice tests with in-depth solutions",
            formattedPrice = "₹199",
            priceAmountMicros = 199000000L,
            priceCurrencyCode = "INR",
            features = MOCKS_FEATURES,
            badge = "Exam Ready"
        )
    )
}
