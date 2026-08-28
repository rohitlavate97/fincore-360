package com.fincore.notifications.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class NotificationType {
    TRANSACTION_ALERT,
    SECURITY_ALERT,
    SYSTEM
}

enum class NotificationStatus {
    UNREAD,
    READ
}

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

    @Column(name = "title", nullable = false, length = 200)
    val title: String,

    @Column(name = "body", nullable = false)
    val body: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    val type: NotificationType,

    @Column(name = "deep_link_uri", length = 500)
    val deepLinkUri: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: NotificationStatus = NotificationStatus.UNREAD,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "read_at")
    var readAt: Instant? = null
) {
    fun markRead() {
        this.status = NotificationStatus.READ
        this.readAt = Instant.now()
    }
}
