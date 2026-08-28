package com.fincore.feature.transactions.domain.repository

import com.fincore.feature.transactions.domain.model.TransactionItem
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getTransactionsForAccount(accountId: String): Flow<List<TransactionItem>>
    suspend fun refreshTransactions(accountId: String): Result<Unit>
}
