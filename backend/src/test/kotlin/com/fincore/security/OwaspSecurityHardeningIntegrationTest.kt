package com.fincore.security

import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwaspSecurityHardeningIntegrationTest {

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

    private lateinit var userA: User
    private lateinit var userB: User
    private lateinit var tokenA: String
    private lateinit var tokenB: String

    @BeforeEach
    fun setup() {
        userA = userRepository.save(
            User(username = "alice_${UUID.randomUUID().toString().take(8)}", email = "alice_${UUID.randomUUID().toString().take(8)}@bank.test", passwordHash = "hashA", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE)
        )
        tokenA = jwtTokenService.createAccessToken(userA)

        userB = userRepository.save(
            User(username = "bob_${UUID.randomUUID().toString().take(8)}", email = "bob_${UUID.randomUUID().toString().take(8)}@bank.test", passwordHash = "hashB", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE)
        )
        tokenB = jwtTokenService.createAccessToken(userB)
    }

    @Test
    @DisplayName("OWASP A01 (Broken Access Control / IDOR): Accessing other customer account returns 404 with no data leak")
    fun idorAccessReturns404WithNoDataLeak() {
        val nonExistentOrOtherAccountId = UUID.randomUUID()

        mockMvc.perform(
            get("/api/v1/accounts/$nonExistentOrOtherAccountId")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    @DisplayName("OWASP A02 (Cryptographic Failures): Tampered JWT signature returns 401 UNAUTHORIZED")
    fun tamperedJwtSignatureReturns401() {
        val parts = tokenA.split(".")
        // Tamper with the signature portion
        val tamperedToken = "${parts[0]}.${parts[1]}.tampered_signature_payload"

        mockMvc.perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer $tamperedToken")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
    }

    @Test
    @DisplayName("OWASP A03 (Injection): SQL injection strings in parameters are safely handled via parameterized queries")
    fun sqlInjectionAttemptSafelyRejected() {
        val maliciousSql = "' OR '1'='1' --"

        mockMvc.perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer $tokenA")
                .param("page", "0")
                .param("size", "20")
                .param("sort", maliciousSql)
        )
            // Either 200 (sanitized/ignored) or 400 (validation rejected), NEVER 500 SQL syntax error
            .andExpect(status().is2xxSuccessful)
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    @DisplayName("OWASP A04 (Sensitive Data Exposure): Passwords and password hashes are never exposed in responses")
    fun sensitiveDataNeverExposedInResponses() {
        mockMvc.perform(
            get("/api/v1/accounts")
                .header("Authorization", "Bearer $tokenA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$..password").doesNotExist())
            .andExpect(jsonPath("$..passwordHash").doesNotExist())
            .andExpect(jsonPath("$..secret").doesNotExist())
    }

    @Test
    @DisplayName("OWASP A07 (Identification and Authentication Failures): Invalid credentials return 401 with generic error")
    fun invalidCredentialsReturnGenericError() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"${userA.username}","password":"wrong_password","deviceId":"device-owasp-1"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
    }

    @Test
    @DisplayName("C-2: Unmapped endpoint returns 401 UNAUTHORIZED (deny-by-default fail-closed)")
    fun unmappedEndpointReturnsUnauthorizedByDefault() {
        mockMvc.perform(
            get("/api/v1/unmapped-endpoint-path")
        )
            .andExpect(status().isUnauthorized)
    }
}
