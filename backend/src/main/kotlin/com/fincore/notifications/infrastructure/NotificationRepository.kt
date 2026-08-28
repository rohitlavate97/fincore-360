package com.fincore.notifications.infrastructure

import com.fincore.notifications.domain.Notification
import com.fincore.notifications.domain.NotificationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID, pageable: Pageable): Page<Notification>
    fun countByCustomerIdAndStatus(customerId: UUID, status: NotificationStatus): Long
    fun findByIdAndCustomerId(id: UUID, customerId: UUID): Optional<Notification>
}
