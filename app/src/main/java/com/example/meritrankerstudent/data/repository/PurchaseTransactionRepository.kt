package com.example.meritrankerstudent.data.repository

import android.content.Context
import com.example.meritrankerstudent.data.billing.PurchaseTransactionRecord
import com.example.meritrankerstudent.data.local.AppDatabase
import com.example.meritrankerstudent.data.local.EntityMappers
import com.example.meritrankerstudent.data.local.PurchaseTransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PurchaseTransactionRepository {
    fun getTransactions(userId: String): Flow<List<PurchaseTransactionRecord>>
    suspend fun getTransactionsSync(userId: String): List<PurchaseTransactionRecord>
    fun getActiveSubscription(userId: String): Flow<PurchaseTransactionRecord?>
    suspend fun getActiveSubscriptionSync(userId: String): PurchaseTransactionRecord?
    suspend fun recordTransaction(record: PurchaseTransactionRecord)
    suspend fun recordTransactions(records: List<PurchaseTransactionRecord>)
    suspend fun getUnsyncedTransactions(userId: String): List<PurchaseTransactionRecord>
    suspend fun markAsSynced(transactionId: String)
}

class DefaultPurchaseTransactionRepository(
    private val dao: PurchaseTransactionDao
) : PurchaseTransactionRepository {

    constructor(context: Context) : this(AppDatabase.getInstance(context).purchaseTransactionDao())

    override fun getTransactions(userId: String): Flow<List<PurchaseTransactionRecord>> {
        return dao.getTransactionsByUser(userId).map { entities ->
            entities.map { EntityMappers.entityToPurchaseTransaction(it) }
        }
    }

    override suspend fun getTransactionsSync(userId: String): List<PurchaseTransactionRecord> {
        return dao.getTransactionsByUserSync(userId).map {
            EntityMappers.entityToPurchaseTransaction(it)
        }
    }

    override fun getActiveSubscription(userId: String): Flow<PurchaseTransactionRecord?> {
        return dao.getActiveSubscription(userId).map { entity ->
            entity?.let { EntityMappers.entityToPurchaseTransaction(it) }
        }
    }

    override suspend fun getActiveSubscriptionSync(userId: String): PurchaseTransactionRecord? {
        return dao.getActiveSubscriptionSync(userId)?.let {
            EntityMappers.entityToPurchaseTransaction(it)
        }
    }

    override suspend fun recordTransaction(record: PurchaseTransactionRecord) {
        val entity = EntityMappers.purchaseTransactionToEntity(record)
        dao.upsertTransaction(entity)
    }

    override suspend fun recordTransactions(records: List<PurchaseTransactionRecord>) {
        val entities = records.map { EntityMappers.purchaseTransactionToEntity(it) }
        dao.upsertTransactions(entities)
    }

    override suspend fun getUnsyncedTransactions(userId: String): List<PurchaseTransactionRecord> {
        return dao.getUnsyncedTransactions(userId).map {
            EntityMappers.entityToPurchaseTransaction(it)
        }
    }

    override suspend fun markAsSynced(transactionId: String) {
        dao.markAsSynced(transactionId)
    }
}
