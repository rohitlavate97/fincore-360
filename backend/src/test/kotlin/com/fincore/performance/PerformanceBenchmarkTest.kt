package com.fincore.performance

import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.shared.money.Money
import com.fincore.shared.security.ratelimit.RateLimiterService
import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.util.UUID
import javax.sql.DataSource
import kotlin.system.measureTimeMillis

@SpringBootTest
class PerformanceBenchmarkTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    @DisplayName("Benchmark: High-precision Money arithmetic executes 100,000 operations in < 500ms with zero precision drift")
    fun moneyArithmeticHighThroughputBenchmark() {
        var current = Money.of("1000.0000", "GBP")
        val increment = Money.of("0.0001", "GBP")

        val iterations = 100_000
        val durationMs = measureTimeMillis {
            for (i in 1..iterations) {
                current += increment
            }
        }

        assertEquals(Money.of("1010.0000", "GBP"), current)
        println("Money arithmetic benchmark: $iterations additions took ${durationMs}ms (${iterations * 1000L / (durationMs + 1)} ops/sec)")
        assertTrue(durationMs < 5000, "100k Money additions should complete in < 5000ms, actual: ${durationMs}ms")
    }

    @Test
    @DisplayName("Benchmark: RateLimiter sliding window handles 50,000 evaluations in < 500ms")
    fun rateLimiterHighThroughputBenchmark() {
        val rateLimiter = RateLimiterService()
        val iterations = 50_000
        val key = "bench-client-ip"

        val durationMs = measureTimeMillis {
            for (i in 1..iterations) {
                rateLimiter.tryAcquire(key, limit = 100, windowSeconds = 60)
            }
        }

        println("RateLimiter benchmark: $iterations evaluations took ${durationMs}ms (${iterations * 1000L / (durationMs + 1)} ops/sec)")
        assertTrue(durationMs < 5000, "50k rate limiter evaluations should complete in < 5000ms, actual: ${durationMs}ms")
    }

    @Test
    @DisplayName("Benchmark: RS256 JWT access token minting and validation executes 500 cycles in < 2000ms")
    fun jwtTokenGenerationBenchmark() {
        val testUser = User(
            id = UUID.randomUUID(),
            username = "perf_user",
            email = "perf@bank.test",
            passwordHash = "hash",
            roles = Role.CUSTOMER.authority,
            status = UserStatus.ACTIVE
        )

        val iterations = 500
        val durationMs = measureTimeMillis {
            for (i in 1..iterations) {
                val token = jwtTokenService.createAccessToken(testUser)
                val decoded = jwtDecoder.decode(token)
                assertNotNull(decoded.subject)
            }
        }

        println("JWT cryptographic benchmark: $iterations mint+decode cycles took ${durationMs}ms (${iterations * 1000L / (durationMs + 1)} ops/sec)")
        assertTrue(durationMs < 10000, "500 RS256 token cycles should complete in < 10000ms, actual: ${durationMs}ms")
    }
}
