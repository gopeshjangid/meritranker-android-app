package com.example.meritrankerstudent.data.billing

import com.example.meritrankerstudent.data.local.EntityMappers
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class BillingModelsTest {

    @Test
    fun billingConstants_definesRequiredProductIdsAndFeatures() {
        assertTrue(BillingConstants.ALL_SUBSCRIPTION_IDS.contains(BillingConstants.PRODUCT_PRO_MONTHLY))
        assertTrue(BillingConstants.ALL_SUBSCRIPTION_IDS.contains(BillingConstants.PRODUCT_PRO_YEARLY))
        assertTrue(BillingConstants.ALL_IN_APP_IDS.contains(BillingConstants.PRODUCT_CREDITS_100))
        assertTrue(BillingConstants.ALL_IN_APP_IDS.contains(BillingConstants.PRODUCT_MOCK_PACK_10))

        assertEquals(4, BillingConstants.DEFAULT_PRODUCTS.size)
        assertTrue(BillingConstants.PRO_FEATURES.isNotEmpty())
        assertTrue(BillingConstants.CREDITS_FEATURES.isNotEmpty())
        assertTrue(BillingConstants.MOCKS_FEATURES.isNotEmpty())
    }

    @Test
    fun entityMappers_convertsPurchaseTransactionBothWaysAccurately() {
        val originalRecord = PurchaseTransactionRecord(
            transactionId = "tx-mapper-test",
            userId = "student_999",
            orderId = "GPA.9999-8888",
            purchaseToken = "token_mapper_test",
            productId = BillingConstants.PRODUCT_PRO_YEARLY,
            productTitle = "MeritRanker Pro (Annual)",
            productType = ProductType.SUBSCRIPTION,
            purchaseTime = 1710000000000L,
            status = PurchaseTransactionStatus.PURCHASED,
            isAcknowledged = true,
            isSyncedWithBackend = false,
            responseCode = 0,
            errorMessage = null,
            rawJsonPayload = "{\"productId\":\"meritranker_pro_yearly\"}",
            createdAt = 1710000000000L,
            updatedAt = 1710000000000L
        )

        val entity = EntityMappers.purchaseTransactionToEntity(originalRecord)
        assertEquals("tx-mapper-test", entity.transactionId)
        assertEquals("student_999", entity.userId)
        assertEquals("GPA.9999-8888", entity.orderId)
        assertEquals("SUBSCRIPTION", entity.productType)
        assertEquals("PURCHASED", entity.status)
        assertTrue(entity.isAcknowledged)
        assertFalse(entity.isSyncedWithBackend)

        val convertedRecord = EntityMappers.entityToPurchaseTransaction(entity)
        assertEquals(originalRecord.transactionId, convertedRecord.transactionId)
        assertEquals(originalRecord.userId, convertedRecord.userId)
        assertEquals(originalRecord.orderId, convertedRecord.orderId)
        assertEquals(originalRecord.productId, convertedRecord.productId)
        assertEquals(originalRecord.productType, convertedRecord.productType)
        assertEquals(originalRecord.status, convertedRecord.status)
        assertEquals(originalRecord.isAcknowledged, convertedRecord.isAcknowledged)
        assertEquals(originalRecord.isSyncedWithBackend, convertedRecord.isSyncedWithBackend)
        assertEquals(originalRecord.rawJsonPayload, convertedRecord.rawJsonPayload)
    }

    @Test
    fun entityMappers_handlesAllPurchaseTransactionStatuses() {
        PurchaseTransactionStatus.values().forEach { status ->
            val record = PurchaseTransactionRecord(
                userId = "user_1",
                orderId = null,
                purchaseToken = "tok",
                productId = "prod",
                productTitle = "title",
                productType = ProductType.IN_APP,
                status = status
            )
            val entity = EntityMappers.purchaseTransactionToEntity(record)
            assertEquals(status.name, entity.status)
            val back = EntityMappers.entityToPurchaseTransaction(entity)
            assertEquals(status, back.status)
        }
    }
}
