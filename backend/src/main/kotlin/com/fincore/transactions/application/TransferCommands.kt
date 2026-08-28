package com.fincore.transactions.application

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TransferCommand(
    val idempotencyKey: UUID,
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amount: BigDecimal,
    val currency: String = "GBP",
    val description: String? = null,
    val callerUserId: UUID,
    val callerCustomerId: UUID?
)

data class TransferResult(
    val transactionId: UUID = UUID.randomUUID(),
    val idempotencyKey: UUID = UUID.randomUUID(),
    val sourceAccountId: UUID = UUID.randomUUID(),
    val destinationAccountId: UUID = UUID.randomUUID(),
    val type: String = "",
    val status: String = "",
    val amount: String = "",
    val currency: String = "",
    val createdAt: String = Instant.now().toString(),
    val replayed: Boolean = false
)
