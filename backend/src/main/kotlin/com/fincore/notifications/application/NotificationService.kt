package com.fincore.notifications.application

import com.fincore.notifications.domain.Notification
import com.fincore.notifications.domain.NotificationStatus
import com.fincore.notifications.domain.NotificationType
import com.fincore.notifications.infrastructure.NotificationRepository
import com.fincore.shared.error.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository
) {

    @Transactional
    fun createNotification(
        customerId: UUID,
        title: String,
        body: String,
        type: NotificationType,
        deepLinkUri: String? = null
    ): Notification {
        val notification = Notification(
            id = UUID.randomUUID(),
            customerId = customerId,
            title = title,
            body = body,
            type = type,
            deepLinkUri = deepLinkUri,
            status = NotificationStatus.UNREAD
        )
        return notificationRepository.save(notification)
    }

    @Transactional(readOnly = true)
    fun getNotifications(customerId: UUID, page: Int = 0, size: Int = 20): Page<Notification> {
        val safePage = if (page < 0) 0 else page
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
    }

    @Transactional
    fun markAsRead(notificationId: UUID, customerId: UUID): Notification {
        val notification = notificationRepository.findByIdAndCustomerId(notificationId, customerId)
            .orElseThrow { ResourceNotFoundException("Notification not found") }
        notification.markRead()
        return notificationRepository.save(notification)
    }

    @Transactional(readOnly = true)
    fun getUnreadCount(customerId: UUID): Long {
        return notificationRepository.countByCustomerIdAndStatus(customerId, NotificationStatus.UNREAD)
    }
}
