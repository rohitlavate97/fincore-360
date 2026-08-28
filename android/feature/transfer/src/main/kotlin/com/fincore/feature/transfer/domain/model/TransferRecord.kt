package com.fincore.feature.transfer.domain.model

data class TransferRecord(
    val transactionId: String,
    val idempotencyKey: String?,
    val sourceAccountId: String?,
    val destinationAccountId: String?,
    val type: String,
    val status: String,
    val amount: String,
    val currency: String,
    val createdAt: Long
)
