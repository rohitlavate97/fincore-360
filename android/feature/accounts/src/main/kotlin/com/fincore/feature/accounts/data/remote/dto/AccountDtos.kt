package com.fincore.feature.accounts.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: String,
    val customerId: String,
    val accountNumber: String,
    val accountType: String,
    val status: String,
    val currency: String,
    val ledgerBalance: String,
    val availableBalance: String,
    val createdAt: String
)

@Serializable
data class PagedAccountDto(
    val items: List<AccountDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

@Serializable
data class CreateAccountRequestDto(
    val accountType: String = "CHECKING",
    val currency: String = "GBP",
    val initialDeposit: String = "0.0000"
)
