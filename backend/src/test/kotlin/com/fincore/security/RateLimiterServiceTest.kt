package com.fincore.security

import com.fincore.shared.security.ratelimit.RateLimiterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RateLimiterServiceTest {

    private lateinit var rateLimiterService: RateLimiterService

    @BeforeEach
    fun setup() {
        rateLimiterService = RateLimiterService()
        rateLimiterService.reset()
    }

    @Test
    @DisplayName("Allows requests up to limit, blocks and calculates retry-after when exceeded")
    fun enforcesSlidingWindowLimit() {
        val key = "test-client-ip"
        val limit = 3
        val windowSeconds = 60L

        // First 3 requests allowed
        for (i in 1..limit) {
            val result = rateLimiterService.tryAcquire(key, limit, windowSeconds)
            assertTrue(result.allowed, "Request $i should be permitted")
            assertEquals(0L, result.retryAfterSeconds)
        }

        // 4th request must be blocked
        val blockedResult = rateLimiterService.tryAcquire(key, limit, windowSeconds)
        assertFalse(blockedResult.allowed, "Request exceeding limit must be blocked")
        assertTrue(blockedResult.retryAfterSeconds > 0, "Retry-After should be > 0 seconds")
    }

    @Test
    @DisplayName("Different client keys have independent rate limits")
    fun rateLimitsAreIndependentPerKey() {
        val keyA = "client-A"
        val keyB = "client-B"
        val limit = 2
        val windowSeconds = 60L

        rateLimiterService.tryAcquire(keyA, limit, windowSeconds)
        rateLimiterService.tryAcquire(keyA, limit, windowSeconds)
        assertFalse(rateLimiterService.tryAcquire(keyA, limit, windowSeconds).allowed)

        // Key B is fresh and unaffected
        assertTrue(rateLimiterService.tryAcquire(keyB, limit, windowSeconds).allowed)
    }
}
