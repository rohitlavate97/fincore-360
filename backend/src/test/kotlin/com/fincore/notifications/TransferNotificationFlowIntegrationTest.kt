package com.fincore.notifications

import com.fincore.accounts.domain.Account
import com.fincore.accounts.domain.AccountStatus
import com.fincore.accounts.domain.AccountType
import com.fincore.accounts.infrastructure.AccountRepository
import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.notifications.domain.NotificationStatus
import com.fincore.notifications.infrastructure.NotificationRepository
import com.fincore.shared.outbox.OutboxService
import com.fincore.support.EmbeddedPostgresSupport
import com.fincore.transactions.api.dto.CreateTransferRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferNotificationFlowIntegrationTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var outboxService: OutboxService

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var customerSender: Customer
    private lateinit var customerRecipient: Customer
    private lateinit var userSender: User
    private lateinit var tokenSender: String
    private lateinit var accountSender: Account
    private lateinit var accountRecipient: Account

    @BeforeEach
    fun setup() {
        customerSender = customerRepository.save(
            Customer(email = "sender_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Sender User", status = CustomerStatus.ACTIVE)
        )
        userSender = userRepository.save(
            User(username = "sender_${UUID.randomUUID().toString().take(8)}", email = customerSender.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customerSender.id)
        )
        tokenSender = jwtTokenService.createAccessToken(userSender)

        customerRecipient = customerRepository.save(
            Customer(email = "recipient_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Recipient User", status = CustomerStatus.ACTIVE)
        )

        accountSender = accountRepository.save(
            Account(
                customerId = customerSender.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.CHECKING,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("1000.0000"),
                availableBalance = BigDecimal("1000.0000")
            )
        )

        accountRecipient = accountRepository.save(
            Account(
                customerId = customerRecipient.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.SAVINGS,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("500.0000"),
                availableBalance = BigDecimal("500.0000")
            )
        )
    }

    @Test
    @DisplayName("Exit Criterion: Transfer produces notification with deep link fincore://transactions/{id}")
    fun transferGeneratesNotificationWithDeepLink() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = CreateTransferRequest(
            sourceAccountId = accountSender.id,
            destinationAccountId = accountRecipient.id,
            amount = BigDecimal("350.0000"),
            currency = "GBP",
            description = "Notification Deep Link Test"
        )

        val resultMvc = mockMvc.perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenSender")
                .header("Idempotency-Key", idempotencyKey)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated).andReturn()

        val transferResponse = objectMapper.readTree(resultMvc.response.contentAsString)
        val transactionId = transferResponse.get("id").asText()

        // Relay pending outbox events which triggers TransactionEventListener
        val relayed = outboxService.relayPendingEvents(50)
        assertTrue(relayed >= 1, "Must relay at least 1 event")

        // 1. Verify Recipient Notification with Deep Link
        val recipientNotifs = notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerRecipient.id, org.springframework.data.domain.PageRequest.of(0, 10))
        assertEquals(1, recipientNotifs.content.size)
        val recNotif = recipientNotifs.content[0]
        assertEquals("Money Received", recNotif.title)
        assertTrue(recNotif.body.contains("350.0000"))
        assertEquals("fincore://transactions/$transactionId", recNotif.deepLinkUri)
        assertEquals(NotificationStatus.UNREAD, recNotif.status)

        // 2. Verify Sender Notification with Deep Link
        val senderNotifs = notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerSender.id, org.springframework.data.domain.PageRequest.of(0, 10))
        assertEquals(1, senderNotifs.content.size)
        val sendNotif = senderNotifs.content[0]
        assertEquals("Transfer Sent", sendNotif.title)
        assertTrue(sendNotif.body.contains("350.0000"))
        assertEquals("fincore://transactions/$transactionId", sendNotif.deepLinkUri)
        assertEquals(NotificationStatus.UNREAD, sendNotif.status)
    }
}
