package com.fincore.accounts.application

import com.fincore.accounts.domain.AccountType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateAccountCommand(
    val customerId: UUID,
    val accountType: AccountType = AccountType.CHECKING,
    val currency: String = "GBP",
    val initialDeposit: BigDecimal = BigDecimal.ZERO
)

data class AccountView(
    val id: UUID,
    val customerId: UUID,
    val accountNumber: String,
    val accountType: String,
    val status: String,
    val currency: String,
    val ledgerBalance: String,
    val availableBalance: String,
    val createdAt: Instant
)

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class TransferAccountsSummary(
    val sourceAccountId: UUID,
    val sourceCustomerId: UUID,
    val destinationAccountId: UUID,
    val destinationCustomerId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val sourceRemainingBalance: String
)