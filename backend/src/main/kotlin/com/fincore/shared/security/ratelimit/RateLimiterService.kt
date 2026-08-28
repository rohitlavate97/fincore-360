package com.fincore.shared.security.ratelimit

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.max

data class RateLimitResult(
    val allowed: Boolean,
    val retryAfterSeconds: Long
)

@Service
class RateLimiterService {

    private val requestWindows = ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>()

    fun tryAcquire(key: String, limit: Int, windowSeconds: Long): RateLimitResult {
        val now = Instant.now().toEpochMilli()
        val windowMillis = windowSeconds * 1000L
        val cutoff = now - windowMillis

        val timestamps = requestWindows.computeIfAbsent(key) { ConcurrentLinkedDeque() }

        synchronized(timestamps) {
            // Evict expired timestamps outside the sliding window
            while (timestamps.isNotEmpty() && timestamps.peekFirst()!! < cutoff) {
                timestamps.pollFirst()
            }

            if (timestamps.size < limit) {
                timestamps.addLast(now)
                return RateLimitResult(allowed = true, retryAfterSeconds = 0)
            } else {
                val oldest = timestamps.peekFirst() ?: now
                val elapsedSinceOldest = now - oldest
                val retryAfter = max(1L, windowSeconds - (elapsedSinceOldest / 1000L))
                return RateLimitResult(allowed = false, retryAfterSeconds = retryAfter)
            }
        }
    }

    fun reset() {
        requestWindows.clear()
    }
}
