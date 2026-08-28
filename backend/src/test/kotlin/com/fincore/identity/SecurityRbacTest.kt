package com.fincore.identity

import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.support.EmbeddedPostgresSupport
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRbacTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Test
    @DisplayName("unauthenticated request to protected endpoint returns 401 with standard error contract")
    fun unauthenticatedReturns401() {
        mockMvc.perform(get("/api/v1/customer/profile"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    @DisplayName("customer access token can access customer endpoint")
    fun customerCanAccessCustomerEndpoint() {
        val customer = User(
            id = UUID.randomUUID(),
            username = "cust1",
            email = "cust1@test.com",
            passwordHash = "hash",
            roles = Role.CUSTOMER.authority,
            status = UserStatus.ACTIVE
        )
        val token = jwtTokenService.createAccessToken(customer)

        mockMvc.perform(
            get("/api/v1/customer/profile")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Customer profile"))
    }

    @Test
    @DisplayName("customer access token cannot access admin endpoint — returns 403 with ACCESS_DENIED")
    fun customerCannotAccessAdminEndpoint() {
        val customer = User(
            id = UUID.randomUUID(),
            username = "cust2",
            email = "cust2@test.com",
            passwordHash = "hash",
            roles = Role.CUSTOMER.authority,
            status = UserStatus.ACTIVE
        )
        val token = jwtTokenService.createAccessToken(customer)

        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    @DisplayName("admin access token can access admin endpoint")
    fun adminCanAccessAdminEndpoint() {
        val admin = User(
            id = UUID.randomUUID(),
            username = "admin1",
            email = "admin1@test.com",
            passwordHash = "hash",
            roles = Role.ADMIN.authority,
            status = UserStatus.ACTIVE
        )
        val token = jwtTokenService.createAccessToken(admin)

        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Admin users"))
    }
}
