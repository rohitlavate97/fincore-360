package com.fincore.feature.transactions.domain.model

data class TransactionItem(
    val id: String,
    val idempotencyKey: String?,
    val sourceAccountId: String?,
    val destinationAccountId: String?,
    val type: String,
    val status: String,
    val amount: String,
    val currency: String,
    val createdAt: Long
)
