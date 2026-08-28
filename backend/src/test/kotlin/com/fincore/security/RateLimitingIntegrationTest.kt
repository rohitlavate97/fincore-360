package com.fincore.security

import com.fincore.shared.security.ratelimit.RateLimiterService
import com.fincore.shared.security.ratelimit.RateLimitingFilter
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitingIntegrationTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var rateLimiterService: RateLimiterService

    @BeforeEach
    fun setup() {
        rateLimiterService.reset()
    }

    @Test
    @DisplayName("Exit Criterion: Rate limit on /api/v1/auth/login triggers 429 TOO_MANY_REQUESTS with Retry-After")
    fun loginEndpointRateLimitingTriggers429() {
        val clientIp = "198.51.100.25"
        val payload = """{"username":"user_attacker","password":"bad_password"}"""

        // Fire 5 requests (up to limit)
        for (i in 1..RateLimitingFilter.LOGIN_LIMIT) {
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
            // Either 401 (invalid credentials) or another auth status, but not 429
        }

        // 6th request must be rejected with 429 TOO_MANY_REQUESTS
        mockMvc.perform(
            post("/api/v1/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
    }
}
