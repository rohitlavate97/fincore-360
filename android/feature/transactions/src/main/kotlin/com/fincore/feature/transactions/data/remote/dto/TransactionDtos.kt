package com.fincore.feature.transactions.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val id: String,
    val idempotencyKey: String? = null,
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val type: String,
    val status: String,
    val amount: String,
    val currency: String,
    val createdAt: String
)

@Serializable
data class PagedTransactionDto(
    val items: List<TransactionDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)
