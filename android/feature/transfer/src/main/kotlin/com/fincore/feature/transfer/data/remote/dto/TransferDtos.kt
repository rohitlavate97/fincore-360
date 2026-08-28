package com.fincore.feature.transfer.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateTransferRequestDto(
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amount: String,
    val currency: String = "GBP",
    val description: String? = null
)

@Serializable
data class TransferResponseDto(
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
