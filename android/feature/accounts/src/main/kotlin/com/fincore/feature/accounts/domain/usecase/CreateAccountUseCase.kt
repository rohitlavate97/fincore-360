package com.fincore.feature.accounts.domain.usecase

import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

class CreateAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(
        accountType: String = "CHECKING",
        currency: String = "GBP",
        initialDeposit: String = "0.0000"
    ): Result<Account> {
        return accountRepository.createAccount(accountType, currency, initialDeposit)
    }
}
