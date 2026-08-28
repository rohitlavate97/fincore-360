package com.fincore.feature.accounts.domain.usecase

import com.fincore.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

class RefreshAccountsUseCase @Inject constructor(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(): Result<Unit> = accountRepository.refreshAccounts()
}
