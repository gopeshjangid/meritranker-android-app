package com.example.meritrankerstudent.data.repository

import com.example.meritrankerstudent.data.billing.ProductType
import com.example.meritrankerstudent.data.billing.PurchaseTransactionRecord
import com.example.meritrankerstudent.data.billing.PurchaseTransactionStatus
import com.example.meritrankerstudent.data.local.PurchaseTransactionDao
import com.example.meritrankerstudent.data.local.PurchaseTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PurchaseTransactionRepositoryTest {

    private lateinit var fakeDao: FakePurchaseTransactionDao
    private lateinit var repository: PurchaseTransactionRepository

    @Before
    fun setUp() {
        fakeDao = FakePurchaseTransactionDao()
        repository = DefaultPurchaseTransactionRepository(fakeDao)
    }

    @Test
    fun recordTransaction_insertsRecordAndRetrievesByUserId() = runTest {
        val record = PurchaseTransactionRecord(
            transactionId = "tx-101",
            userId = "user_abc",
            orderId = "GPA.1234-5678-9012",
            purchaseToken = "token_xyz",
            productId = "meritranker_pro_monthly",
            productTitle = "MeritRanker Pro (Monthly)",
            productType = ProductType.SUBSCRIPTION,
            purchaseTime = 1700000000000L,
            status = PurchaseTransactionStatus.PURCHASED,
            isAcknowledged = true,
            isSyncedWithBackend = false
        )

        repository.recordTransaction(record)

        val userTransactions = repository.getTransactions("user_abc").first()
        assertEquals(1, userTransactions.size)
        assertEquals("tx-101", userTransactions[0].transactionId)
        assertEquals("user_abc", userTransactions[0].userId)
        assertEquals("meritranker_pro_monthly", userTransactions[0].productId)
        assertEquals(PurchaseTransactionStatus.PURCHASED, userTransactions[0].status)
        assertTrue(userTransactions[0].isAcknowledged)
        assertFalse(userTransactions[0].isSyncedWithBackend)
    }

    @Test
    fun getActiveSubscription_returnsLatestPurchasedSubscription() = runTest {
        val oldSub = PurchaseTransactionRecord(
            transactionId = "tx-old",
            userId = "user_abc",
            orderId = "GPA.OLD",
            purchaseToken = "token_old",
            productId = "meritranker_pro_monthly",
            productTitle = "MeritRanker Pro (Monthly)",
            productType = ProductType.SUBSCRIPTION,
            purchaseTime = 1600000000000L,
            status = PurchaseTransactionStatus.PURCHASED,
            isAcknowledged = true,
            isSyncedWithBackend = true
        )

        val latestSub = PurchaseTransactionRecord(
            transactionId = "tx-new",
            userId = "user_abc",
            orderId = "GPA.NEW",
            purchaseToken = "token_new",
            productId = "meritranker_pro_yearly",
            productTitle = "MeritRanker Pro (Annual)",
            productType = ProductType.SUBSCRIPTION,
            purchaseTime = 1700000000000L,
            status = PurchaseTransactionStatus.PURCHASED,
            isAcknowledged = true,
            isSyncedWithBackend = false
        )

        val booster = PurchaseTransactionRecord(
            transactionId = "tx-booster",
            userId = "user_abc",
            orderId = "GPA.BOOSTER",
            purchaseToken = "token_booster",
            productId = "meritranker_credits_100",
            productTitle = "100 AI Credits",
            productType = ProductType.IN_APP,
            purchaseTime = 1750000000000L,
            status = PurchaseTransactionStatus.PURCHASED,
            isAcknowledged = true,
            isSyncedWithBackend = false
        )

        repository.recordTransaction(oldSub)
        repository.recordTransaction(latestSub)
        repository.recordTransaction(booster)

        val activeSub = repository.getActiveSubscription("user_abc").first()
        assertNotNull(activeSub)
        assertEquals("tx-new", activeSub?.transactionId)
        assertEquals("meritranker_pro_yearly", activeSub?.productId)
    }

    @Test
    fun getUnsyncedTransactions_returnsOnlyPendingBackendSyncRecords() = runTest {
        val syncedTx = PurchaseTransactionRecord(
            transactionId = "tx-synced",
            userId = "user_abc",
            orderId = "GPA.1",
            purchaseToken = "token_1",
            productId = "meritranker_pro_monthly",
            productTitle = "MeritRanker Pro (Monthly)",
            productType = ProductType.SUBSCRIPTION,
            status = PurchaseTransactionStatus.PURCHASED,
            isSyncedWithBackend = true
        )

        val unsyncedTx = PurchaseTransactionRecord(
            transactionId = "tx-unsynced",
            userId = "user_abc",
            orderId = "GPA.2",
            purchaseToken = "token_2",
            productId = "meritranker_mock_pack_10",
            productTitle = "10 Mock Tests",
            productType = ProductType.IN_APP,
            status = PurchaseTransactionStatus.PURCHASED,
            isSyncedWithBackend = false
        )

        repository.recordTransaction(syncedTx)
        repository.recordTransaction(unsyncedTx)

        val unsynced = repository.getUnsyncedTransactions("user_abc")
        assertEquals(1, unsynced.size)
        assertEquals("tx-unsynced", unsynced[0].transactionId)

        // Mark as synced
        repository.markAsSynced("tx-unsynced")
        val updatedUnsynced = repository.getUnsyncedTransactions("user_abc")
        assertEquals(0, updatedUnsynced.size)
    }

    @Test
    fun recordTransactions_persistsPendingAndCanceledTransactions() = runTest {
        val pendingTx = PurchaseTransactionRecord(
            transactionId = "tx-pending",
            userId = "user_abc",
            orderId = "GPA.PENDING",
            purchaseToken = "token_pending",
            productId = "meritranker_pro_yearly",
            productTitle = "MeritRanker Pro (Annual)",
            productType = ProductType.SUBSCRIPTION,
            status = PurchaseTransactionStatus.PENDING,
            isAcknowledged = false,
            isSyncedWithBackend = false
        )

        val canceledTx = PurchaseTransactionRecord(
            transactionId = "tx-canceled",
            userId = "user_abc",
            orderId = null,
            purchaseToken = "CANCELED_123",
            productId = "meritranker_credits_100",
            productTitle = "100 AI Credits",
            productType = ProductType.IN_APP,
            status = PurchaseTransactionStatus.USER_CANCELED,
            isAcknowledged = false,
            isSyncedWithBackend = false
        )

        val errorTx = PurchaseTransactionRecord(
            transactionId = "tx-error",
            userId = "user_abc",
            orderId = null,
            purchaseToken = "ERROR_123",
            productId = "meritranker_credits_100",
            productTitle = "100 AI Credits",
            productType = ProductType.IN_APP,
            status = PurchaseTransactionStatus.ERROR,
            responseCode = 3,
            errorMessage = "Billing service unavailable",
            isAcknowledged = false,
            isSyncedWithBackend = false
        )

        repository.recordTransactions(listOf(pendingTx, canceledTx, errorTx))

        val transactions = repository.getTransactionsSync("user_abc")
        assertEquals(3, transactions.size)
        assertTrue(transactions.any { it.status == PurchaseTransactionStatus.PENDING })
        assertTrue(transactions.any { it.status == PurchaseTransactionStatus.USER_CANCELED })
        assertTrue(transactions.any { it.status == PurchaseTransactionStatus.ERROR })
    }
}

class FakePurchaseTransactionDao : PurchaseTransactionDao {
    private val storage = mutableMapOf<String, PurchaseTransactionEntity>()

    override fun getTransaction(transactionId: String): Flow<PurchaseTransactionEntity?> {
        return flowOf(storage[transactionId])
    }

    override suspend fun getTransactionSync(transactionId: String): PurchaseTransactionEntity? {
        return storage[transactionId]
    }

    override fun getTransactionsByUser(userId: String): Flow<List<PurchaseTransactionEntity>> {
        return flowOf(
            storage.values
                .filter { it.userId == userId }
                .sortedByDescending { it.purchaseTime }
        )
    }

    override suspend fun getTransactionsByUserSync(userId: String): List<PurchaseTransactionEntity> {
        return storage.values
            .filter { it.userId == userId }
            .sortedByDescending { it.purchaseTime }
    }

    override fun getActiveSubscription(userId: String): Flow<PurchaseTransactionEntity?> {
        return flowOf(
            storage.values
                .filter { it.userId == userId && it.productType == "SUBSCRIPTION" && it.status == "PURCHASED" }
                .maxByOrNull { it.purchaseTime }
        )
    }

    override suspend fun getActiveSubscriptionSync(userId: String): PurchaseTransactionEntity? {
        return storage.values
            .filter { it.userId == userId && it.productType == "SUBSCRIPTION" && it.status == "PURCHASED" }
            .maxByOrNull { it.purchaseTime }
    }

    override suspend fun getUnsyncedTransactions(userId: String): List<PurchaseTransactionEntity> {
        return storage.values
            .filter { it.userId == userId && !it.isSyncedWithBackend }
            .sortedBy { it.purchaseTime }
    }

    override suspend fun upsertTransaction(transaction: PurchaseTransactionEntity) {
        storage[transaction.transactionId] = transaction
    }

    override suspend fun upsertTransactions(transactions: List<PurchaseTransactionEntity>) {
        for (tx in transactions) {
            storage[tx.transactionId] = tx
        }
    }

    override suspend fun markAsSynced(transactionId: String, updatedAt: Long) {
        storage[transactionId]?.let {
            storage[transactionId] = it.copy(isSyncedWithBackend = true, updatedAt = updatedAt)
        }
    }

    override suspend fun deleteTransactionsByUser(userId: String) {
        storage.entries.removeIf { it.value.userId == userId }
    }

    override suspend fun clearAllTransactions() {
        storage.clear()
    }
}
