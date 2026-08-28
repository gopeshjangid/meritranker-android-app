package com.example.meritrankerstudent.data.billing

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive unit tests for MeritRanker One-Time Credit Pack Billing Architecture (PBL 9.1.0).
 * Tests real Google Play product mappings (500, 1000, 1500), state machine, token fingerprinting,
 * partial product availability, and strict Phase 1 security boundaries.
 */
class CreditBillingTest {

    @Test
    fun defaultCreditPacks_configuresRealActiveProducts_neutralPresentation() {
        val packs = BillingConstants.DEFAULT_CREDIT_PACKS
        assertEquals(3, packs.size)

        val pack500 = packs[0]
        assertEquals("pack_500", pack500.localPlanId)
        assertEquals("meritranker_credits_500", pack500.googlePlayProductId)
        assertEquals(500, pack500.credits)
        assertEquals("500 Credits", pack500.displayLabel)
        assertEquals(1, pack500.sortOrder)
        assertFalse(pack500.isPopular)
        assertNull(pack500.badgeText)
        assertTrue(pack500.isConfigured)

        val pack1000 = packs[1]
        assertEquals("pack_1000", pack1000.localPlanId)
        assertEquals("meritranker_credits_1000", pack1000.googlePlayProductId)
        assertEquals(1000, pack1000.credits)
        assertEquals("1000 Credits", pack1000.displayLabel)
        assertEquals(2, pack1000.sortOrder)
        assertFalse(pack1000.isPopular)
        assertNull(pack1000.badgeText)
        assertTrue(pack1000.isConfigured)

        val pack1500 = packs[2]
        assertEquals("pack_1500", pack1500.localPlanId)
        assertEquals("meritranker_credits_1500", pack1500.googlePlayProductId)
        assertEquals(1500, pack1500.credits)
        assertEquals("1500 Credits", pack1500.displayLabel)
        assertEquals(3, pack1500.sortOrder)
        assertFalse(pack1500.isPopular)
        assertNull(pack1500.badgeText)
        assertTrue(pack1500.isConfigured)
    }

    @Test
    fun billingConstants_helpersResolveAllThreeActiveProducts() {
        val configuredIds = BillingConstants.getConfiguredProductIds()
        assertEquals(
            listOf("meritranker_credits_500", "meritranker_credits_1000", "meritranker_credits_1500"),
            configuredIds
        )

        val resolved500 = BillingConstants.findPackByProductId("meritranker_credits_500")
        assertNotNull(resolved500)
        assertEquals("pack_500", resolved500?.localPlanId)
        assertEquals(500, resolved500?.credits)

        val resolved1000 = BillingConstants.findPackByProductId("meritranker_credits_1000")
        assertNotNull(resolved1000)
        assertEquals("pack_1000", resolved1000?.localPlanId)
        assertEquals(1000, resolved1000?.credits)

        val resolved1500 = BillingConstants.findPackByProductId("meritranker_credits_1500")
        assertNotNull(resolved1500)
        assertEquals("pack_1500", resolved1500?.localPlanId)
        assertEquals(1500, resolved1500?.credits)

        val resolvedByPlan = BillingConstants.findPackByPlanId("pack_1000")
        assertNotNull(resolvedByPlan)
        assertEquals("meritranker_credits_1000", resolvedByPlan?.googlePlayProductId)

        assertNull(BillingConstants.findPackByProductId("non_existent_sku"))
        assertNull(BillingConstants.findPackByProductId(""))
    }

    @Test
    fun detectedPurchasePayload_computesConsistentSha256Fingerprint_andNeverExposesRawToken() {
        val rawToken = "sample_google_play_purchase_token_inapp_999888777"
        val fp1 = DetectedPurchasePayload.computeFingerprint(rawToken)
        val fp2 = DetectedPurchasePayload.computeFingerprint(rawToken)

        assertEquals(fp1, fp2)
        assertEquals(16, fp1.length) // 8 bytes represented as 16 hex chars
        assertFalse(fp1.contains("sample_google_play"))
        assertFalse(fp1.contains("999888777"))

        val emptyFp = DetectedPurchasePayload.computeFingerprint("")
        assertEquals("empty_token", emptyFp)
    }

    @Test
    fun creditPackItemState_handlesPartialAvailabilityCleanly() {
        val pack500Config = BillingConstants.DEFAULT_CREDIT_PACKS[0]
        val pack1000Config = BillingConstants.DEFAULT_CREDIT_PACKS[1]
        val pack1500Config = BillingConstants.DEFAULT_CREDIT_PACKS[2]

        val playDetails500 = PlayProductDetails(
            productId = "meritranker_credits_500",
            formattedPrice = "₹99.00",
            priceAmountMicros = 99000000L,
            priceCurrencyCode = "INR",
            title = "500 AI Credits",
            description = "Pack of 500 AI credits"
        )

        val playDetails1000 = PlayProductDetails(
            productId = "meritranker_credits_1000",
            formattedPrice = "₹179.00",
            priceAmountMicros = 179000000L,
            priceCurrencyCode = "INR",
            title = "1000 AI Credits",
            description = "Pack of 1000 AI credits"
        )

        // Scenario: 500 & 1000 loaded successfully from Google Play, 1500 failed/unfetched
        val state500 = CreditPackItemState(
            config = pack500Config,
            googleProductDetails = playDetails500,
            isAvailable = true,
            isConfigured = true
        )

        val state1000 = CreditPackItemState(
            config = pack1000Config,
            googleProductDetails = playDetails1000,
            isAvailable = true,
            isConfigured = true
        )

        val state1500Failed = CreditPackItemState(
            config = pack1500Config,
            googleProductDetails = null,
            isAvailable = false,
            isConfigured = true
        )

        assertTrue(state500.isAvailable)
        assertEquals("₹99.00", state500.googleProductDetails?.formattedPrice)

        assertTrue(state1000.isAvailable)
        assertEquals("₹179.00", state1000.googleProductDetails?.formattedPrice)

        assertFalse(state1500Failed.isAvailable)
        assertTrue(state1500Failed.isConfigured)
        assertNull(state1500Failed.googleProductDetails)
    }

    @Test
    fun billingState_supportsAllRequiredLifecyclePhases() {
        val idle: BillingState = BillingState.Idle
        val connecting: BillingState = BillingState.Connecting
        val productLoading: BillingState = BillingState.ProductLoading
        val ready: BillingState = BillingState.Ready(emptyList(), isConnected = true)
        val launching: BillingState = BillingState.Launching("pack_500")
        val userCanceled: BillingState = BillingState.UserCanceled
        val error: BillingState = BillingState.Error("Service unavailable", 3)

        val samplePayload = DetectedPurchasePayload(
            localPlanId = "pack_500",
            productId = "meritranker_credits_500",
            purchaseToken = "tok_google_play_500",
            orderId = "GPA.9876-5432",
            purchaseTime = 1750000000000L,
            purchaseState = 1, // PURCHASED
            tokenFingerprint = "500abcd1234efgh"
        )

        val purchasedUnverified: BillingState = BillingState.PurchasedUnverified(samplePayload)
        val pending: BillingState = BillingState.Pending(samplePayload)

        assertTrue(idle is BillingState.Idle)
        assertTrue(connecting is BillingState.Connecting)
        assertTrue(productLoading is BillingState.ProductLoading)
        assertTrue(ready is BillingState.Ready)
        assertTrue(launching is BillingState.Launching)
        assertTrue(userCanceled is BillingState.UserCanceled)
        assertTrue(error is BillingState.Error)
        assertTrue(purchasedUnverified is BillingState.PurchasedUnverified)
        assertTrue(pending is BillingState.Pending)

        assertEquals("pack_500", (purchasedUnverified as BillingState.PurchasedUnverified).payload.localPlanId)
        assertEquals("meritranker_credits_500", purchasedUnverified.payload.productId)
        assertEquals("GPA.9876-5432", purchasedUnverified.payload.orderId)
    }

    @Test
    fun multiplePurchases_createsIndependentPayloads_withoutStateCollision() {
        val payload1000 = DetectedPurchasePayload(
            localPlanId = "pack_1000",
            productId = "meritranker_credits_1000",
            purchaseToken = "tok_1000",
            orderId = "GPA.1111-2222",
            purchaseTime = 1750000001000L,
            purchaseState = 1,
            tokenFingerprint = "fp1000"
        )

        val payload1500 = DetectedPurchasePayload(
            localPlanId = "pack_1500",
            productId = "meritranker_credits_1500",
            purchaseToken = "tok_1500",
            orderId = "GPA.3333-4444",
            purchaseTime = 1750000002000L,
            purchaseState = 1,
            tokenFingerprint = "fp1500"
        )

        val state1 = BillingState.PurchasedUnverified(payload1000)
        val state2 = BillingState.PurchasedUnverified(payload1500)

        assertNotEquals(state1.payload.productId, state2.payload.productId)
        assertNotEquals(state1.payload.localPlanId, state2.payload.localPlanId)
        assertNotEquals(state1.payload.orderId, state2.payload.orderId)
    }
}
