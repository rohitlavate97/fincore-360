package com.fincore.notifications.api.dto

import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val title: String,
    val body: String,
    val type: String,
    val deepLinkUri: String?,
    val status: String,
    val createdAt: String,
    val readAt: String?
)

data class PagedNotificationResponse(
    val items: List<NotificationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

data class UnreadCountResponse(
    val unreadCount: Long
)
