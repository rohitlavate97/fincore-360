package com.fincore.audit

import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.shared.audit.AuditLogRepository
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditControllerIntegrationTest {

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
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    private lateinit var adminUser: User
    private lateinit var customerUser: User
    private lateinit var adminToken: String
    private lateinit var customerToken: String

    @BeforeEach
    fun setup() {
        adminUser = userRepository.save(
            User(username = "admin_${UUID.randomUUID().toString().take(8)}", email = "admin_${UUID.randomUUID().toString().take(8)}@bank.test", passwordHash = "hash", roles = "${Role.ADMIN.authority},${Role.CUSTOMER.authority}", status = UserStatus.ACTIVE)
        )
        adminToken = jwtTokenService.createAccessToken(adminUser)

        customerUser = userRepository.save(
            User(username = "cust_${UUID.randomUUID().toString().take(8)}", email = "cust_${UUID.randomUUID().toString().take(8)}@bank.test", passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE)
        )
        customerToken = jwtTokenService.createAccessToken(customerUser)
    }

    @Test
    @DisplayName("Admin can query audit events by correlation ID")
    fun adminCanQueryAuditEvents() {
        val corrId = UUID.randomUUID()
        auditLogRepository.append(
            eventType = "TRANSFER_INITIATED",
            actorId = adminUser.id,
            actorRole = "ROLE_ADMIN",
            resourceType = "TRANSACTION",
            resourceId = UUID.randomUUID(),
            outcome = "SUCCESS",
            reason = null,
            ipAddress = "127.0.0.1",
            userAgent = "JUnit",
            correlationId = corrId
        )

        mockMvc.perform(
            get("/api/v1/audit/events")
                .header("Authorization", "Bearer $adminToken")
                .param("correlationId", corrId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].eventType").value("TRANSFER_INITIATED"))
            .andExpect(jsonPath("$.items[0].actorRole").value("ROLE_ADMIN"))
            .andExpect(jsonPath("$.items[0].ipAddress").value("127.0.0.1"))
            .andExpect(jsonPath("$.items[0].userAgent").value("JUnit"))
    }

    @Test
    @DisplayName("Exit Criterion: Non-admin roles return 403 ACCESS_DENIED on audit endpoint")
    fun nonAdminRolesDeniedOnAuditEndpoint() {
        val nonAdminRoles = listOf(
            Role.CUSTOMER.authority,
            "ROLE_SUPPORT_AGENT",
            "ROLE_OPERATIONS"
        )

        for (authority in nonAdminRoles) {
            val user = userRepository.save(
                User(
                    username = "user_${UUID.randomUUID().toString().take(8)}",
                    email = "user_${UUID.randomUUID().toString().take(8)}@bank.test",
                    passwordHash = "hash",
                    roles = authority,
                    status = UserStatus.ACTIVE
                )
            )
            val token = jwtTokenService.createAccessToken(user)

            mockMvc.perform(
                get("/api/v1/audit/events")
                    .header("Authorization", "Bearer $token")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
        }
    }

    @Test
    @DisplayName("Unauthenticated request returns 401 AUTHENTICATION_REQUIRED")
    fun unauthenticatedDeniedOnAuditEndpoint() {
        mockMvc.perform(
            get("/api/v1/audit/events")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
    }
}
