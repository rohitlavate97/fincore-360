package com.fincore.feature.accounts.domain.repository

import com.fincore.feature.accounts.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun getAccounts(): Flow<List<Account>>
    fun getAccountById(id: String): Flow<Account?>
    suspend fun refreshAccounts(): Result<Unit>
    suspend fun createAccount(accountType: String, currency: String, initialDeposit: String): Result<Account>
}
