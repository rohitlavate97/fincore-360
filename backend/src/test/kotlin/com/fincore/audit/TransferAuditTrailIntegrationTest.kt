package com.fincore.audit

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
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.outbox.OutboxEventRepository
import com.fincore.shared.outbox.OutboxService
import com.fincore.shared.outbox.OutboxStatus
import com.fincore.support.EmbeddedPostgresSupport
import com.fincore.transactions.api.dto.CreateTransferRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
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
import org.springframework.dao.DataAccessException
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
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
class TransferAuditTrailIntegrationTest {

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
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Autowired
    private lateinit var outboxService: OutboxService

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private lateinit var customerA: Customer
    private lateinit var customerB: Customer
    private lateinit var userA: User
    private lateinit var tokenA: String
    private lateinit var accountA: Account
    private lateinit var accountB: Account

    @BeforeEach
    fun setup() {
        customerA = customerRepository.save(
            Customer(email = "audit_alice_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Alice Audit", status = CustomerStatus.ACTIVE)
        )
        userA = userRepository.save(
            User(username = "alice_${UUID.randomUUID().toString().take(8)}", email = customerA.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customerA.id)
        )
        tokenA = jwtTokenService.createAccessToken(userA)

        customerB = customerRepository.save(
            Customer(email = "audit_bob_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Bob Audit", status = CustomerStatus.ACTIVE)
        )

        accountA = accountRepository.save(
            Account(
                customerId = customerA.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.CHECKING,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("1000.0000"),
                availableBalance = BigDecimal("1000.0000")
            )
        )

        accountB = accountRepository.save(
            Account(
                customerId = customerB.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.SAVINGS,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("200.0000"),
                availableBalance = BigDecimal("200.0000")
            )
        )
    }

    @Test
    @DisplayName("Exit Criterion: Transfer audit trail complete initiation -> completion with outbox events")
    fun transferAuditTrailCompleteInitiationToCompletion() {
        val idempotencyKey = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID()

        val request = CreateTransferRequest(
            sourceAccountId = accountA.id,
            destinationAccountId = accountB.id,
            amount = BigDecimal("250.0000"),
            currency = "GBP",
            description = "Audit Trail Verification"
        )

        mockMvc.perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenA")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Correlation-ID", correlationId.toString())
                .content(objectMapper.writeValueAsString(request))
        ).andExpect(status().isCreated)

        // 1. Verify Audit Events initiation -> completion
        val auditEvents = auditLogRepository.findEvents(correlationId = correlationId)
        assertEquals(2, auditEvents.size, "Must have exactly 2 audit events: initiation and completion")

        val initiated = auditEvents[0]
        assertEquals("TRANSFER_INITIATED", initiated.eventType)
        assertEquals("SUCCESS", initiated.outcome)
        assertEquals("ROLE_CUSTOMER", initiated.actorRole)
        assertEquals("TRANSACTION", initiated.resourceType)
        assertEquals(userA.id, initiated.actorId)
        assertEquals(correlationId, initiated.correlationId)

        val completed = auditEvents[1]
        assertEquals("TRANSFER_COMPLETED", completed.eventType)
        assertEquals("SUCCESS", completed.outcome)
        assertEquals("ROLE_CUSTOMER", completed.actorRole)
        assertEquals("TRANSACTION", completed.resourceType)
        assertEquals(userA.id, completed.actorId)
        assertEquals(correlationId, completed.correlationId)

        assertTrue(initiated.timestamp <= completed.timestamp)

        // 2. Verify Transactional Outbox Events (ADR-009)
        val outboxEvents = outboxEventRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId)
        assertEquals(2, outboxEvents.size, "Must have exactly 2 outbox events: initiation and completion")
        assertEquals("TRANSFER_INITIATED", outboxEvents[0].eventType)
        assertEquals("TRANSFER_COMPLETED", outboxEvents[1].eventType)
        assertEquals(OutboxStatus.PENDING, outboxEvents[0].status)
        assertEquals(OutboxStatus.PENDING, outboxEvents[1].status)

        // 3. Verify Outbox Relay moves pending events to PUBLISHED
        val relayedCount = outboxService.relayPendingEvents(50)
        assertTrue(relayedCount >= 2)

        val reloadedOutbox = outboxEventRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId)
        assertEquals(OutboxStatus.PUBLISHED, reloadedOutbox[0].status)
        assertEquals(OutboxStatus.PUBLISHED, reloadedOutbox[1].status)
        assertNotNull(reloadedOutbox[0].publishedAt)
        assertNotNull(reloadedOutbox[1].publishedAt)

        // 4. Verify Append-Only Database Trigger rejects UPDATE and DELETE
        assertThrows(DataAccessException::class.java) {
            jdbcClient.sql("UPDATE audit_events SET reason = 'tampered' WHERE correlation_id = :corrId")
                .param("corrId", correlationId)
                .update()
        }

        assertThrows(DataAccessException::class.java) {
            jdbcClient.sql("DELETE FROM audit_events WHERE correlation_id = :corrId")
                .param("corrId", correlationId)
                .update()
        }
    }

    @Test
    @DisplayName("H-1: Failed transfer still records TRANSFER_FAILED in the audit trail surviving transaction rollback")
    fun failedTransferRecordsTransferFailedInAuditTrail() {
        val correlationId = UUID.randomUUID()
        val idempotencyKey = UUID.randomUUID().toString()
        val request = CreateTransferRequest(
            sourceAccountId = accountA.id,
            destinationAccountId = accountB.id,
            amount = BigDecimal("50000.0000"),
            currency = "GBP"
        )

        mockMvc.perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenA")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Correlation-ID", correlationId.toString())
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnprocessableEntity)

        val events = auditLogRepository.findEvents(correlationId = correlationId)
        assertTrue(
            events.any { it.eventType == "TRANSFER_FAILED" && it.outcome == "FAILURE" },
            "Failure audit event must survive transaction rollback and be durably persisted"
        )
    }
}
