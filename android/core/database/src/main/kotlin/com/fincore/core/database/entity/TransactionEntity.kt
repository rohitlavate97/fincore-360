package com.fincore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String?,

    @ColumnInfo(name = "source_account_id")
    val sourceAccountId: String?,

    @ColumnInfo(name = "dest_account_id")
    val destAccountId: String?,

    val type: String,

    val status: String,

    val amount: String,

    val currency: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
