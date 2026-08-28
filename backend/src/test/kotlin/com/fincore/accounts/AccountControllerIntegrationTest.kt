package com.fincore.accounts

import com.fincore.accounts.api.dto.CreateAccountRequest
import com.fincore.accounts.domain.AccountType
import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.support.EmbeddedPostgresSupport
import org.hamcrest.Matchers.hasSize
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
class AccountControllerIntegrationTest {

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
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var customer: Customer
    private lateinit var user: User
    private lateinit var token: String

    @BeforeEach
    fun setupCustomerAndUser() {
        customer = customerRepository.save(
            Customer(
                email = "cust_${UUID.randomUUID().toString().take(8)}@bank.test",
                fullName = "Alice In Chains",
                status = CustomerStatus.ACTIVE
            )
        )

        user = userRepository.save(
            User(
                username = "user_${UUID.randomUUID().toString().take(8)}",
                email = customer.email,
                passwordHash = "passwordHash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE,
                customerId = customer.id
            )
        )

        token = jwtTokenService.createAccessToken(user)
    }

    @Test
    @DisplayName("GET /api/v1/accounts returns 401 when unauthenticated")
    fun unauthenticatedAccountsReturns401() {
        mockMvc.perform(get("/api/v1/accounts"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
    }

    @Test
    @DisplayName("POST /api/v1/accounts creates account, and GET /api/v1/accounts returns paginated list")
    fun createAndListAccounts() {
        val request = CreateAccountRequest(
            accountType = AccountType.CHECKING,
            currency = "GBP",
            initialDeposit = BigDecimal("500.0000")
        )

        val createResult = mockMvc.perform(
            post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $token")
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.accountType").value("CHECKING"))
            .andExpect(jsonPath("$.currency").value("GBP"))
            .andExpect(jsonPath("$.availableBalance").value("500.0000"))
            .andExpect(jsonPath("$.ledgerBalance").value("500.0000"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn()

        val responseBody = createResult.response.contentAsString
        val accountId = objectMapper.readTree(responseBody).get("id").asText()

        // List accounts
        mockMvc.perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items", hasSize<Any>(1)))
            .andExpect(jsonPath("$.items[0].id").value(accountId))
            .andExpect(jsonPath("$.items[0].availableBalance").value("500.0000"))

        // Get single account
        mockMvc.perform(
            get("/api/v1/accounts/$accountId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(accountId))
            .andExpect(jsonPath("$.availableBalance").value("500.0000"))
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} returns 404 for account owned by another customer")
    fun nonOwnedAccountReturns404() {
        val otherCustomer = customerRepository.save(
            Customer(
                email = "other_${UUID.randomUUID().toString().take(8)}@bank.test",
                fullName = "Other Customer",
                status = CustomerStatus.ACTIVE
            )
        )
        val otherUser = userRepository.save(
            User(
                username = "other_${UUID.randomUUID().toString().take(8)}",
                email = otherCustomer.email,
                passwordHash = "hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE,
                customerId = otherCustomer.id
            )
        )
        val otherToken = jwtTokenService.createAccessToken(otherUser)

        val createRequest = CreateAccountRequest(
            accountType = AccountType.SAVINGS,
            currency = "GBP",
            initialDeposit = BigDecimal.ZERO
        )

        val otherCreate = mockMvc.perform(
            post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $otherToken")
                .content(objectMapper.writeValueAsString(createRequest))
        ).andReturn()

        val otherAccountId = objectMapper.readTree(otherCreate.response.contentAsString).get("id").asText()

        // First user tries to access other user's account -> 404 Not Found (enumeration prevention)
        mockMvc.perform(
            get("/api/v1/accounts/$otherAccountId")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
    }
}
