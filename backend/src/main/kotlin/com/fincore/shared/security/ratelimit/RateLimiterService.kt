package com.fincore.shared.security.ratelimit

import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_TRACKED_KEYS = 50_000
    }

    private val requestWindows = ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>>()

    fun tryAcquire(key: String, limit: Int, windowSeconds: Long): RateLimitResult {
        val now = Instant.now().toEpochMilli()
        val windowMillis = windowSeconds * 1000L
        val cutoff = now - windowMillis

        // Bounded capacity check: prevent unbounded memory exhaustion under spoofing storms (C-6)
        if (requestWindows.size > MAX_TRACKED_KEYS && !requestWindows.containsKey(key)) {
            evictStaleKeys(cutoff)
            if (requestWindows.size > MAX_TRACKED_KEYS) {
                log.warn("Rate limiter capacity reached max limit ({}), rejecting untracked keys fail-closed", MAX_TRACKED_KEYS)
                return RateLimitResult(allowed = false, retryAfterSeconds = windowSeconds)
            }
        }

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

    private fun evictStaleKeys(cutoff: Long) {
        val iterator = requestWindows.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val deque = entry.value
            synchronized(deque) {
                while (deque.isNotEmpty() && deque.peekFirst()!! < cutoff) {
                    deque.pollFirst()
                }
                if (deque.isEmpty()) {
                    iterator.remove()
                }
            }
        }
    }

    fun reset() {
        requestWindows.clear()
    }
}
