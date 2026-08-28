package com.fincore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val syncKey: String,
    val lastSyncedAt: Long,
    val status: String
)
