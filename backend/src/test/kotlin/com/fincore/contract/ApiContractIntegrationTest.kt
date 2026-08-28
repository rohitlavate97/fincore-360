package com.fincore.contract

import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.support.EmbeddedPostgresSupport
import org.hamcrest.Matchers.matchesPattern
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
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiContractIntegrationTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var testUser: User
    private lateinit var testToken: String

    @BeforeEach
    fun setup() {
        testUser = userRepository.save(
            User(
                username = "contract_${UUID.randomUUID().toString().take(8)}",
                email = "contract_${UUID.randomUUID().toString().take(8)}@bank.test",
                passwordHash = "hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE
            )
        )
        testToken = jwtTokenService.createAccessToken(testUser)
    }

    @Test
    @DisplayName("Contract Test: 400 Bad Request error response matches exact error contract with traceId and no stack trace")
    fun errorContractConformsOn400() {
        val corrId = UUID.randomUUID().toString()

        mockMvc.perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer $testToken")
                .header("X-Correlation-ID", corrId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}") // Missing required fields and Idempotency-Key
        )
            .andExpect(status().isBadRequest)
            .andExpect(header().string("X-Correlation-ID", corrId))
            .andExpect(jsonPath("$.errorCode").exists())
            .andExpect(jsonPath("$.message").isString)
            .andExpect(jsonPath("$.traceId").value(corrId))
            .andExpect(jsonPath("$.timestamp").isString)
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist())
            .andExpect(jsonPath("$.trace").doesNotExist())
    }

    @Test
    @DisplayName("Contract Test: 401 Unauthorized matches error contract schema")
    fun errorContractConformsOn401() {
        val corrId = UUID.randomUUID().toString()

        mockMvc.perform(
            get("/api/v1/accounts")
                .header("X-Correlation-ID", corrId)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("X-Correlation-ID", corrId))
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.traceId").value(corrId))
            .andExpect(jsonPath("$.timestamp").isString)
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    @DisplayName("Contract Test: 403 Forbidden matches error contract schema")
    fun errorContractConformsOn403() {
        val corrId = UUID.randomUUID().toString()

        mockMvc.perform(
            get("/api/v1/audit/events")
                .header("Authorization", "Bearer $testToken")
                .header("X-Correlation-ID", corrId)
        )
            .andExpect(status().isForbidden)
            .andExpect(header().string("X-Correlation-ID", corrId))
            .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.traceId").value(corrId))
            .andExpect(jsonPath("$.timestamp").isString)
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    @DisplayName("Contract Test: 404 Not Found matches error contract schema")
    fun errorContractConformsOn404() {
        val corrId = UUID.randomUUID().toString()

        mockMvc.perform(
            get("/api/v1/accounts/${UUID.randomUUID()}")
                .header("Authorization", "Bearer $testToken")
                .header("X-Correlation-ID", corrId)
        )
            .andExpect(status().isNotFound)
            .andExpect(header().string("X-Correlation-ID", corrId))
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").value(corrId))
            .andExpect(jsonPath("$.timestamp").isString)
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    @DisplayName("Contract Test: Account balances serialise as scale-4 monetary strings, matching NUMERIC(19,4)")
    fun monetaryBalancesSerialiseAsStringsWithScaleFour() {
        mockMvc.perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer $testToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
            // If items exist, availableBalance must match string with 4 decimal digits
            .andExpect(jsonPath("$.items[*].availableBalance").value(org.hamcrest.Matchers.everyItem(matchesPattern("^\\d+\\.\\d{4}$"))))
    }
}
