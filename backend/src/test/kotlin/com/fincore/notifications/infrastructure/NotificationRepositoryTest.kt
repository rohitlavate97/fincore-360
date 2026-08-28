package com.fincore.notifications.infrastructure

import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.notifications.domain.Notification
import com.fincore.notifications.domain.NotificationStatus
import com.fincore.notifications.domain.NotificationType
import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@Transactional
class NotificationRepositoryTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    private lateinit var customer: Customer

    @BeforeEach
    fun setup() {
        customer = customerRepository.save(
            Customer(
                email = "notif_${UUID.randomUUID().toString().take(8)}@bank.test",
                fullName = "Notif User",
                status = CustomerStatus.ACTIVE
            )
        )
    }

    @Test
    fun `can save notification, query unread count, and mark as read`() {
        val notif = Notification(
            customerId = customer.id,
            title = "Transfer Received",
            body = "You received £100.00 from Alice",
            type = NotificationType.TRANSACTION_ALERT,
            deepLinkUri = "fincore://transactions/12345"
        )

        val saved = notificationRepository.save(notif)
        assertNotNull(saved.id)
        assertEquals(NotificationStatus.UNREAD, saved.status)

        val unreadCount = notificationRepository.countByCustomerIdAndStatus(customer.id, NotificationStatus.UNREAD)
        assertEquals(1, unreadCount)

        val page = notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customer.id, PageRequest.of(0, 10))
        assertEquals(1, page.content.size)
        assertEquals("fincore://transactions/12345", page.content[0].deepLinkUri)

        saved.markRead()
        notificationRepository.save(saved)

        val updated = notificationRepository.findById(saved.id).get()
        assertEquals(NotificationStatus.READ, updated.status)
        assertNotNull(updated.readAt)

        val newUnreadCount = notificationRepository.countByCustomerIdAndStatus(customer.id, NotificationStatus.UNREAD)
        assertEquals(0, newUnreadCount)
    }
}
