package com.fincore.accounts.api.dto

import com.fincore.accounts.domain.AccountType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateAccountRequest(
    val accountType: AccountType = AccountType.CHECKING,

    @field:NotBlank(message = "Currency is required")
    @field:Size(min = 3, max = 3, message = "Currency must be 3 characters ISO-4217")
    val currency: String = "GBP",

    @field:DecimalMin(value = "0.0000", message = "Initial deposit cannot be negative")
    val initialDeposit: BigDecimal = BigDecimal.ZERO
)

data class AccountResponse(
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

data class PagedAccountResponse(
    val items: List<AccountResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class DepositRequest(
    @field:DecimalMin(value = "0.0100", message = "Deposit amount must be at least 0.0100")
    val amount: BigDecimal,
    val reference: String? = null
)

