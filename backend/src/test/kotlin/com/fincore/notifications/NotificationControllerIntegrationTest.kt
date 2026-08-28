package com.fincore.notifications

import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.notifications.domain.Notification
import com.fincore.notifications.domain.NotificationType
import com.fincore.notifications.infrastructure.NotificationRepository
import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerIntegrationTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var customerA: Customer
    private lateinit var userA: User
    private lateinit var tokenA: String

    private lateinit var customerB: Customer
    private lateinit var userB: User
    private lateinit var tokenB: String

    @BeforeEach
    fun setup() {
        customerA = customerRepository.save(Customer(email = "notif_a_${UUID.randomUUID().toString().take(8)}@test.com", fullName = "Alice A", status = CustomerStatus.ACTIVE))
        userA = userRepository.save(User(username = "alice_${UUID.randomUUID().toString().take(8)}", email = customerA.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customerA.id))
        tokenA = jwtTokenService.createAccessToken(userA)

        customerB = customerRepository.save(Customer(email = "notif_b_${UUID.randomUUID().toString().take(8)}@test.com", fullName = "Bob B", status = CustomerStatus.ACTIVE))
        userB = userRepository.save(User(username = "bob_${UUID.randomUUID().toString().take(8)}", email = customerB.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customerB.id))
        tokenB = jwtTokenService.createAccessToken(userB)
    }

    @Test
    @DisplayName("Customer can retrieve their notifications and unread count")
    fun customerCanRetrieveNotifications() {
        notificationRepository.save(
            Notification(
                customerId = customerA.id,
                title = "Alert 1",
                body = "Body 1",
                type = NotificationType.TRANSACTION_ALERT,
                deepLinkUri = "fincore://transactions/tx-123"
            )
        )

        mockMvc.perform(
            get("/api/v1/notifications/unread-count")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unreadCount").value(1))

        mockMvc.perform(
            get("/api/v1/notifications")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Alert 1"))
            .andExpect(jsonPath("$.items[0].deepLinkUri").value("fincore://transactions/tx-123"))
    }

    @Test
    @DisplayName("Customer cannot access or mark as read notifications of another customer (404)")
    fun customerIsolationEnforced() {
        val notifA = notificationRepository.save(
            Notification(
                customerId = customerA.id,
                title = "Alice Secret",
                body = "Confidential",
                type = NotificationType.SECURITY_ALERT,
                deepLinkUri = null
            )
        )

        mockMvc.perform(
            patch("/api/v1/notifications/${notifA.id}/read")
                .header("Authorization", "Bearer $tokenB")
        )
            .andExpect(status().isNotFound)
    }
}
