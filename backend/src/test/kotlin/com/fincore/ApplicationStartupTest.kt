package com.fincore

import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
// Boot 4 relocated this from org.springframework.boot.test.autoconfigure.web.servlet
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

/**
 * Phase 1's headline verification criterion: the application actually starts,
 * against a real PostgreSQL, with migrations applied, and reports healthy.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationStartupTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        /** Replaces the configured PostgreSQL with the embedded real server. */
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtTokenService: com.fincore.identity.application.JwtTokenService

    private fun generateAdminToken(): String {
        val adminUser = com.fincore.identity.domain.User(
            id = java.util.UUID.randomUUID(),
            username = "admin_startup_test",
            email = "admin@bank.test",
            passwordHash = "hash",
            roles = "ROLE_ADMIN",
            status = com.fincore.identity.domain.UserStatus.ACTIVE
        )
        return jwtTokenService.createAccessToken(adminUser)
    }

    @Test
    @DisplayName("the Spring context loads — migrations run and JPA validates against the schema")
    fun contextLoads() {
        // Reaching this point proves Flyway migrated successfully AND that
        // Hibernate's ddl-auto=validate found the schema consistent.
    }

    @Test
    @DisplayName("GET /actuator/health returns 200 UP")
    fun healthEndpointReturnsUp() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    @DisplayName("liveness and readiness probes are distinct and available")
    fun probesAreAvailable() {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))

        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    @DisplayName("every response carries X-Correlation-ID, generated when absent")
    fun correlationIdIsGenerated() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(header().exists("X-Correlation-ID"))
    }

    @Test
    @DisplayName("a supplied X-Correlation-ID is echoed unchanged")
    fun correlationIdIsEchoed() {
        val supplied = "11111111-2222-3333-4444-555555555555"
        mockMvc.perform(get("/actuator/health").header("X-Correlation-ID", supplied))
            .andExpect(header().string("X-Correlation-ID", supplied))
    }

    @Test
    @DisplayName("an unknown path returns the error contract, not a framework page")
    fun unknownPathUsesErrorContract() {
        val token = generateAdminToken()
        mockMvc.perform(
            get("/api/v1/does-not-exist")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    @DisplayName("error responses never leak a stack trace or internal class name")
    fun errorsLeakNothing() {
        val token = generateAdminToken()
        val body = mockMvc.perform(
            get("/api/v1/does-not-exist")
                .header("Authorization", "Bearer $token")
        )
            .andReturn().response.contentAsString

        listOf("java.", "org.springframework", "Exception", "at com.fincore").forEach {
            assert(!body.contains(it)) { "error response leaked '$it': $body" }
        }
    }

    @Test
    @DisplayName("OpenAPI spec is generated and served")
    fun openApiIsServed() {
        val token = generateAdminToken()
        mockMvc.perform(
            get("/v3/api-docs")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("application/json"))
    }
}
