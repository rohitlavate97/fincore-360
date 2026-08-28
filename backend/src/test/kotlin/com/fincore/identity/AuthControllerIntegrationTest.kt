package com.fincore.identity

import com.fincore.identity.api.dto.LoginRequest
import com.fincore.identity.api.dto.LogoutRequest
import com.fincore.identity.api.dto.RefreshTokenRequest
import com.fincore.identity.api.dto.RegisterRequest
import com.fincore.support.EmbeddedPostgresSupport
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

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

    @Test
    @DisplayName("full registration, login, token refresh, reuse detection, and logout lifecycle")
    fun fullAuthLifecycle() {
        val unique = UUID.randomUUID().toString().take(8)
        val username = "user_$unique"
        val email = "user_$unique@bank.test"
        val password = "SecurePassword123!"
        val deviceId = "device-test-1"

        // 1. Register new customer
        val regRequest = RegisterRequest(
            username = username,
            email = email,
            password = password,
            fullName = "Test Customer $unique",
            deviceId = deviceId
        )

        val regResponse = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.username").value(username))
            .andReturn().response.contentAsString

        val rootNode = objectMapper.readTree(regResponse)
        val initialRefreshToken = rootNode.get("refreshToken").asText()

        // 2. Login with correct credentials
        val loginRequest = LoginRequest(
            username = username,
            password = password,
            deviceId = deviceId
        )

        val loginResponse = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andReturn().response.contentAsString

        val loginNode = objectMapper.readTree(loginResponse)
        val currentRefreshToken = loginNode.get("refreshToken").asText()

        // 3. Rotate Refresh Token
        val refreshRequest = RefreshTokenRequest(
            refreshToken = currentRefreshToken,
            deviceId = deviceId
        )

        val refreshResponse = mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andReturn().response.contentAsString

        val refreshNode = objectMapper.readTree(refreshResponse)
        val rotatedRefreshToken = refreshNode.get("refreshToken").asText()

        // 4. REUSE DETECTION: presenting consumed currentRefreshToken again must fail with 401
        val reuseRequest = RefreshTokenRequest(
            refreshToken = currentRefreshToken,
            deviceId = deviceId
        )

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reuseRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))

        // 5. Presenting rotatedRefreshToken after reuse must also fail (family was revoked)
        val revokedFamilyRequest = RefreshTokenRequest(
            refreshToken = rotatedRefreshToken,
            deviceId = deviceId
        )

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(revokedFamilyRequest))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
    }

    @Test
    @DisplayName("wrong credentials increments failed attempts and locks account on 5th attempt")
    fun accountLockoutAfterFiveFailedAttempts() {
        val unique = UUID.randomUUID().toString().take(8)
        val username = "lockout_$unique"
        val email = "lockout_$unique@bank.test"
        val password = "RealPassword123!"
        val deviceId = "device-lockout"

        // Register
        mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    RegisterRequest(username, email, password, "Lockout User", deviceId)
                ))
        ).andExpect(status().isCreated)

        // Attempt 1-4: Wrong password -> 401
        for (i in 1..4) {
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        LoginRequest(username, "WrongPass$i", deviceId)
                    ))
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
        }

        // Attempt 5: Locks account -> 401
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    LoginRequest(username, "WrongPass5", deviceId)
                ))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))

        // Attempt 6 with CORRECT password: Must still be rejected because account is locked!
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    LoginRequest(username, password, deviceId)
                ))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
            .andExpect(jsonPath("$.message").value("Account is locked due to multiple failed login attempts. Try again later."))
    }

    @Test
    @DisplayName("logout revokes refresh token")
    fun logoutRevokesToken() {
        val unique = UUID.randomUUID().toString().take(8)
        val username = "logout_$unique"
        val email = "logout_$unique@bank.test"
        val password = "LogoutPassword123!"
        val deviceId = "device-logout"

        val regResponse = mockMvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    RegisterRequest(username, email, password, "Logout User", deviceId)
                ))
        ).andExpect(status().isCreated).andReturn().response.contentAsString

        val token = objectMapper.readTree(regResponse).get("refreshToken").asText()

        // Logout
        mockMvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LogoutRequest(refreshToken = token)))
        ).andExpect(status().isNoContent)

        // Attempt refresh with revoked token -> 401
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshTokenRequest(refreshToken = token, deviceId = deviceId)))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
    }
}
