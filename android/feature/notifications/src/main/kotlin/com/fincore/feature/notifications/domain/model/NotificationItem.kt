package com.fincore.feature.notifications.domain.model

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val deepLinkUri: String?,
    val isRead: Boolean,
    val createdAt: Long,
    val readAt: Long?
)
