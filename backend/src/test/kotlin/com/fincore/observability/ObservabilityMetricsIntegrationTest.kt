package com.fincore.observability

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest(properties = [
    "management.endpoints.web.exposure.include=health,info,prometheus,metrics",
    "management.prometheus.metrics.export.enabled=true"
])
@AutoConfigureMockMvc
class ObservabilityMetricsIntegrationTest {

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

    private lateinit var testCustomer: Customer
    private lateinit var destCustomer: Customer
    private lateinit var testUser: User
    private lateinit var testToken: String
    private lateinit var sourceAccount: Account
    private lateinit var destinationAccount: Account

    @BeforeEach
    fun setup() {
        val unique = UUID.randomUUID().toString().take(8)

        testCustomer = customerRepository.save(
            Customer(
                email = "obs_cust_$unique@bank.test",
                fullName = "Observability Customer",
                status = CustomerStatus.ACTIVE
            )
        )

        destCustomer = customerRepository.save(
            Customer(
                email = "obs_dest_$unique@bank.test",
                fullName = "Destination Customer",
                status = CustomerStatus.ACTIVE
            )
        )

        testUser = userRepository.save(
            User(
                username = "obs_$unique",
                email = testCustomer.email,
                passwordHash = "hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE,
                customerId = testCustomer.id
            )
        )
        testToken = jwtTokenService.createAccessToken(testUser)

        sourceAccount = accountRepository.save(
            Account(
                accountNumber = "GB29FINC${unique.take(8).uppercase()}",
                customerId = testCustomer.id,
                accountType = AccountType.CHECKING,
                currency = "GBP",
                ledgerBalance = BigDecimal("10.0000"),
                availableBalance = BigDecimal("10.0000"),
                status = AccountStatus.ACTIVE
            )
        )

        destinationAccount = accountRepository.save(
            Account(
                accountNumber = "GB29FINC${UUID.randomUUID().toString().take(8).uppercase()}",
                customerId = destCustomer.id,
                accountType = AccountType.CHECKING,
                currency = "GBP",
                ledgerBalance = BigDecimal("50.0000"),
                availableBalance = BigDecimal("50.0000"),
                status = AccountStatus.ACTIVE
            )
        )
    }

    @Test
    @DisplayName("Exit Criterion: Actuator metrics endpoint exports and answers 'How many transfers failed and why?'")
    fun metricsEndpointTracksFailedTransfersWithReason() {
        // 1. Verify /actuator/health is UP
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))

        // 2. Trigger an insufficient funds failure (attempting to transfer 100 GBP with only 10 GBP available)
        val transferPayload = """
            {
                "sourceAccountId": "${sourceAccount.id}",
                "destinationAccountId": "${destinationAccount.id}",
                "amount": "100.00",
                "currency": "GBP"
            }
        """.trimIndent()

        val idemKey = UUID.randomUUID().toString()
        mockMvc.perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer $testToken")
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferPayload)
        )
            .andExpect(status().isUnprocessableEntity)

        // 3. Inspect actuator metrics endpoint for fincore.transfers.failed
        // Directly answers the exit criterion: "How many transfers failed in the last hour, and why?"
        mockMvc.perform(get("/actuator/metrics/fincore.transfers.failed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("fincore.transfers.failed"))
            .andExpect(jsonPath("$.measurements[0].value").value(1.0))
            .andExpect(jsonPath("$.availableTags[0].tag").value("reason"))
            .andExpect(jsonPath("$.availableTags[0].values[0]").value("INSUFFICIENT_FUNDS"))

        // 4. Query specifically filtered by tag reason:INSUFFICIENT_FUNDS
        mockMvc.perform(get("/actuator/metrics/fincore.transfers.failed?tag=reason:INSUFFICIENT_FUNDS"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("fincore.transfers.failed"))
            .andExpect(jsonPath("$.measurements[0].value").value(1.0))
    }
}
