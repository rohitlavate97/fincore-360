package com.fincore.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_mutations")
data class PendingMutationEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "mutation_type")
    val mutationType: String,

    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    val payload: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    val status: String = "PENDING"
)
