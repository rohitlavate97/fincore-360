package com.fincore.transactions.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

data class CreateTransferRequest(
    @field:NotNull(message = "sourceAccountId is required")
    val sourceAccountId: UUID,

    @field:NotNull(message = "destinationAccountId is required")
    val destinationAccountId: UUID,

    @field:NotNull(message = "amount is required")
    @field:DecimalMin(value = "0.0001", message = "Amount must be strictly positive")
    val amount: BigDecimal,

    @field:NotBlank(message = "currency is required")
    @field:Size(min = 3, max = 3, message = "currency must be 3 characters ISO-4217")
    val currency: String = "GBP",

    val description: String? = null
)

data class TransactionResponse(
    val id: UUID,
    val idempotencyKey: UUID?,
    val sourceAccountId: UUID?,
    val destinationAccountId: UUID?,
    val type: String,
    val status: String,
    val amount: String,
    val currency: String,
    val createdAt: String
)

data class PagedTransactionResponse(
    val items: List<TransactionResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)
