package com.fincore.feature.transactions.domain.usecase

import com.fincore.feature.transactions.domain.model.TransactionItem
import com.fincore.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAccountTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    operator fun invoke(accountId: String): Flow<List<TransactionItem>> =
        repository.getTransactionsForAccount(accountId)

    suspend fun refresh(accountId: String): Result<Unit> =
        repository.refreshTransactions(accountId)
}
