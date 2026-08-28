package com.fincore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "account_number")
    val accountNumber: String,

    @ColumnInfo(name = "account_type")
    val accountType: String,

    val status: String,

    val currency: String,

    @ColumnInfo(name = "ledger_balance")
    val ledgerBalance: String,

    @ColumnInfo(name = "available_balance")
    val availableBalance: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
