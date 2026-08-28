package com.fincore.feature.accounts.domain.model

data class Account(
    val id: String,
    val customerId: String,
    val accountNumber: String,
    val accountType: String,
    val status: String,
    val currency: String,
    val ledgerBalance: String,
    val availableBalance: String,
    val createdAt: Long
)
