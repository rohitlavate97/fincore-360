package com.fincore.transactions

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
import com.fincore.support.EmbeddedPostgresSupport
import com.fincore.transactions.api.dto.CreateTransferRequest
import org.hamcrest.Matchers.equalTo
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferControllerIntegrationTest {

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
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var customerA: Customer
    private lateinit var customerB: Customer
    private lateinit var userA: User
    private lateinit var userB: User
    private lateinit var tokenA: String
    private lateinit var accountA: Account
    private lateinit var accountB: Account

    @BeforeEach
    fun setup() {
        customerA = customerRepository.save(
            Customer(email = "alice_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Alice A", status = CustomerStatus.ACTIVE)
        )
        userA = userRepository.save(
            User(username = "alice_${UUID.randomUUID().toString().take(8)}", email = customerA.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customerA.id)
        )
        tokenA = jwtTokenService.createAccessToken(userA)

        customerB = customerRepository.save(
            Customer(email = "bob_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Bob B", status = CustomerStatus.ACTIVE)
        )
        userB = userRepository.save(
            User(username = "bob_${UUID.randomUUID().toString().take(8)}", email = customerB.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customerB.id)
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
    @DisplayName("missing Idempotency-Key header returns 400 with IDEMPOTENCY_KEY_REQUIRED")
    fun missingIdempotencyKeyReturns400() {
        val request = CreateTransferRequest(
            sourceAccountId = accountA.id,
            destinationAccountId = accountB.id,
            amount = BigDecimal("50.0000"),
            currency = "GBP"
        )

        mockMvc.perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenA")
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"))
    }

    @Test
    @DisplayName("valid transfer debits source and credits destination, sequential retry replayed")
    fun validTransferAndIdempotentReplay() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = CreateTransferRequest(
            sourceAccountId = accountA.id,
            destinationAccountId = accountB.id,
            amount = BigDecimal("150.0000"),
            currency = "GBP",
            description = "Dinner split"
        )

        // 1. Initial transfer
        val result = mockMvc.perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenA")
                .header("Idempotency-Key", idempotencyKey)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.amount").value("150.0000"))
            .andExpect(jsonPath("$.sourceAccountId").value(accountA.id.toString()))
            .andExpect(jsonPath("$.destinationAccountId").value(accountB.id.toString()))
            .andReturn()

        val initialTxId = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        // Verify balances updated in DB
        val updatedA = accountRepository.findById(accountA.id).get()
        val updatedB = accountRepository.findById(accountB.id).get()
        assert(updatedA.availableBalance.compareTo(BigDecimal("850.0000")) == 0)
        assert(updatedB.availableBalance.compareTo(BigDecimal("350.0000")) == 0)

        // 2. Sequential retry with identical Idempotency-Key -> Replay!
        mockMvc.perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenA")
                .header("Idempotency-Key", idempotencyKey)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(initialTxId))
            .andExpect(jsonPath("$.amount").value("150.0000"))

        // Balances must remain EXACTLY 850 and 350 (no double debit!)
        val recheckA = accountRepository.findById(accountA.id).get()
        val recheckB = accountRepository.findById(accountB.id).get()
        assert(recheckA.availableBalance.compareTo(BigDecimal("850.0000")) == 0)
        assert(recheckB.availableBalance.compareTo(BigDecimal("350.0000")) == 0)

        // 3. Query transaction details by ID
        mockMvc.perform(
            get("/api/v1/transactions/$initialTxId")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(initialTxId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        // 4. Query account transaction history
        mockMvc.perform(
            get("/api/v1/accounts/${accountA.id}/transactions")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].id").value(initialTxId))
    }

    @Test
    @DisplayName("transfer with insufficient funds returns 422 with TRANSFER_INSUFFICIENT_FUNDS")
    fun insufficientFundsReturns422() {
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
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errorCode").value("TRANSFER_INSUFFICIENT_FUNDS"))
    }

    @Test
    @DisplayName("transfer from non-owned account returns 404 RESOURCE_NOT_FOUND")
    fun nonOwnedAccountReturns404() {
        val idempotencyKey = UUID.randomUUID().toString()
        // Alice attempts to debit Bob's account
        val request = CreateTransferRequest(
            sourceAccountId = accountB.id,
            destinationAccountId = accountA.id,
            amount = BigDecimal("50.0000"),
            currency = "GBP"
        )

        mockMvc.perform(
            post("/api/v1/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenA")
                .header("Idempotency-Key", idempotencyKey)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
    }
}
