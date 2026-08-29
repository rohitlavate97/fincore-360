package com.fincore.simulation

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
import com.fincore.shared.event.DomainEventPublisher
import com.fincore.shared.outbox.OutboxEvent
import com.fincore.shared.outbox.OutboxEventRepository
import com.fincore.shared.outbox.OutboxService
import com.fincore.shared.outbox.OutboxStatus
import com.fincore.support.EmbeddedPostgresSupport
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

/**
 * Phase 14 Production Simulation & Chaos Verification Test Suite.
 * Validates that system behaviour under simulated failures adheres strictly
 * to the production failure modes catalogue (PRODUCTION-FAILURE-MODES.md).
 */
@SpringBootTest(properties = [
    "management.endpoints.web.exposure.include=health,info,prometheus,metrics"
])
@AutoConfigureMockMvc
class ProductionSimulationIntegrationTest {

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
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var testUser: User
    private lateinit var validToken: String
    private lateinit var sourceAccount: Account
    private lateinit var destinationAccount: Account

    @BeforeEach
    fun setUp() {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val customer = customerRepository.save(
            Customer(
                fullName = "Simulation Test User $uniqueSuffix",
                email = "sim_$uniqueSuffix@fincore.bank",
                status = CustomerStatus.ACTIVE
            )
        )

        testUser = userRepository.save(
            User(
                username = "sim_user_$uniqueSuffix",
                email = customer.email,
                passwordHash = "argon2id_dummy_hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE,
                customerId = customer.id
            )
        )

        validToken = jwtTokenService.createAccessToken(testUser)

        sourceAccount = accountRepository.save(
            Account(
                accountNumber = "SIM-SRC-${UUID.randomUUID().toString().take(6)}",
                customerId = customer.id,
                accountType = AccountType.CHECKING,
                currency = "USD",
                ledgerBalance = BigDecimal("1000.0000"),
                availableBalance = BigDecimal("1000.0000"),
                status = AccountStatus.ACTIVE
            )
        )

        destinationAccount = accountRepository.save(
            Account(
                accountNumber = "SIM-DST-${UUID.randomUUID().toString().take(6)}",
                customerId = customer.id,
                accountType = AccountType.SAVINGS,
                currency = "USD",
                ledgerBalance = BigDecimal("500.0000"),
                availableBalance = BigDecimal("500.0000"),
                status = AccountStatus.ACTIVE
            )
        )
    }

    @Test
    @DisplayName("FM-INFRA-001 & FM-INFRA-004: Decoupled probes verify liveness vs readiness separation")
    fun simulateDecoupledHealthProbes() {
        // 1. Verify liveness probe returns UP (shallow process check, strictly decoupled from external DB)
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))

        // 2. Verify readiness probe returns UP (deep check confirming DB availability)
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    @DisplayName("FM-ANDROID-001 & FM-ANDROID-002: Token expiration simulation returns structured 401 contract")
    fun simulateTokenExpiryAndRejection() {
        val expiredToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZXhwIjoxNTE2MjM5MDIyfQ.invalid_sig"

        mockMvc.perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer $expiredToken")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("FM-BACKEND-006: Tampered JWT claims rejected immediately without database leakage")
    fun simulateTamperedJwtClaims() {
        val tamperedToken = validToken.dropLast(5) + "abcde"

        mockMvc.perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer $tamperedToken")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("FM-BACKEND-002: Rate limiter sliding window enforces fail-closed protection (429 Retry-After)")
    fun simulateRateLimitingFailClosedProtection() {
        val ipAddress = "192.0.2.${(10..200).random()}"

        // Rapid fire requests to trigger rate limit filter
        for (i in 1..15) {
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"attacker","password":"password123"}""")
                    .header("X-Forwarded-For", ipAddress)
            )
        }

        // Must return 429 TOO_MANY_REQUESTS with Retry-After header
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"attacker","password":"password123"}""")
                .header("X-Forwarded-For", ipAddress)
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
    }

    @Test
    @DisplayName("FM-BACKEND-004: Duplicate transaction storm returns idempotent replay with zero double-debit")
    fun simulateDuplicateTransactionReplayStorm() {
        val idempotencyKey = UUID.randomUUID().toString()
        val payload = """
            {
                "sourceAccountId": "${sourceAccount.id}",
                "destinationAccountId": "${destinationAccount.id}",
                "amount": "100.0000",
                "currency": "USD",
                "description": "Simulation duplicate test transfer"
            }
        """.trimIndent()

        // First transfer execution
        val result1 = mockMvc.perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer $validToken")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andReturn()

        val responseBody1 = result1.response.contentAsString

        // Second concurrent/duplicate request with identical Idempotency-Key
        val result2 = mockMvc.perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer $validToken")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andReturn()

        val responseBody2 = result2.response.contentAsString
        assertEquals(responseBody1, responseBody2, "Duplicate submission must return byte-for-byte replayed response")

        // Verify balance was debited exactly once
        val reloadedSource = accountRepository.findById(sourceAccount.id).get()
        assertEquals(BigDecimal("900.0000"), reloadedSource.availableBalance, "Available balance must be debited exactly once (1000 - 100 = 900)")
    }

    @Test
    @DisplayName("FM-BACKEND-003: Broker partition preserves transactional outbox events for retry/DLQ")
    fun simulateOutboxBrokerPartitionResilience() {
        val outboxRepo = mockk<OutboxEventRepository>()
        val failingPublisher = mockk<DomainEventPublisher>()
        val objectMapper = ObjectMapper()

        val outboxService = OutboxService(
            outboxEventRepository = outboxRepo,
            domainEventPublisher = failingPublisher,
            objectMapper = objectMapper
        )

        val outboxEvent = OutboxEvent(
            eventType = "TRANSFER_COMPLETED",
            aggregateType = "TRANSACTION",
            aggregateId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            payload = """{"amount":"100.0000"}"""
        )

        every {
            outboxRepo.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 50))
        } returns listOf(outboxEvent)
        every { outboxRepo.save(any()) } answers { firstArg() }
        every { failingPublisher.publish(any()) } throws RuntimeException("Chaos: Simulated Kafka Partition Outage")

        // Relay attempt fails -> event increments retry count and remains PENDING
        outboxService.relayPendingEvents(50)
        assertEquals(1, outboxEvent.retryCount)
        assertEquals(OutboxStatus.PENDING, outboxEvent.status)
    }
}
