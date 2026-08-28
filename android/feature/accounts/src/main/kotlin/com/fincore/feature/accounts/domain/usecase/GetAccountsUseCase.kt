package com.fincore.feature.accounts.domain.usecase

import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAccountsUseCase @Inject constructor(
    private val accountRepository: AccountRepository
) {
    operator fun invoke(): Flow<List<Account>> = accountRepository.getAccounts()
}
