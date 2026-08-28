package com.fincore.feature.transactions.data.repository

import com.fincore.core.database.dao.TransactionDao
import com.fincore.core.database.entity.TransactionEntity
import com.fincore.feature.transactions.data.remote.TransactionsApi
import com.fincore.feature.transactions.domain.model.TransactionItem
import com.fincore.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class TransactionRepositoryImpl @Inject constructor(
    private val api: TransactionsApi,
    private val dao: TransactionDao
) : TransactionRepository {

    override fun getTransactionsForAccount(accountId: String): Flow<List<TransactionItem>> {
        return dao.getTransactionsByAccount(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun refreshTransactions(accountId: String): Result<Unit> = runCatching {
        val paged = api.getAccountTransactions(accountId)
        val entities = paged.items.map { dto ->
            val epoch = runCatching { Instant.parse(dto.createdAt).toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
            TransactionEntity(
                id = dto.id,
                idempotencyKey = dto.idempotencyKey,
                sourceAccountId = dto.sourceAccountId,
                destAccountId = dto.destinationAccountId,
                type = dto.type,
                status = dto.status,
                amount = dto.amount,
                currency = dto.currency,
                createdAt = epoch
            )
        }
        dao.upsertAll(entities)
    }

    private fun TransactionEntity.toDomain() = TransactionItem(
        id = id,
        idempotencyKey = idempotencyKey,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destAccountId,
        type = type,
        status = status,
        amount = amount,
        currency = currency,
        createdAt = createdAt
    )
}
