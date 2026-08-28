package com.fincore.feature.notifications.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val deepLinkUri: String? = null,
    val status: String,
    val createdAt: String,
    val readAt: String? = null
)

@Serializable
data class PagedNotificationDto(
    val items: List<NotificationDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

@Serializable
data class UnreadCountDto(
    val unreadCount: Long
)
