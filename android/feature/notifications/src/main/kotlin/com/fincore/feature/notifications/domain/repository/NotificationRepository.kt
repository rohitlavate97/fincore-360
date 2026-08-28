package com.fincore.feature.notifications.domain.repository

import com.fincore.feature.notifications.domain.model.NotificationItem
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeNotifications(): Flow<List<NotificationItem>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun refreshNotifications(): Result<Unit>
    suspend fun markAsRead(id: String): Result<Unit>
}
