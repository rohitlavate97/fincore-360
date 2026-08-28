package com.fincore.feature.notifications.domain.usecase

import com.fincore.feature.notifications.domain.model.NotificationItem
import com.fincore.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    operator fun invoke(): Flow<List<NotificationItem>> = repository.observeNotifications()
}

class ObserveUnreadCountUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    operator fun invoke(): Flow<Int> = repository.observeUnreadCount()
}

class RefreshNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshNotifications()
}

class MarkNotificationReadUseCase @Inject constructor(
    private val repository: NotificationRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.markAsRead(id)
}
