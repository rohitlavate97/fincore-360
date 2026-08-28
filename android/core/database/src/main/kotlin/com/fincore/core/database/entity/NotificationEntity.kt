package com.fincore.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val title: String,
    val body: String,
    val type: String,
    val deepLinkUri: String?,
    val status: String,
    val createdAt: Long,
    val readAt: Long?
)
