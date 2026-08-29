package com.fincore.shared.security.ratelimit

import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.error.ErrorCode
import com.fincore.shared.error.ErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RateLimitingFilter(
    private val rateLimiterService: RateLimiterService,
    private val objectMapper: ObjectMapper,
    @Value("\${fincore.ratelimit.login.limit:10}") private val loginLimit: Int = 10,
    @Value("\${fincore.ratelimit.login.window-seconds:60}") private val loginWindowSeconds: Long = 60L,
    @Value("\${fincore.ratelimit.transfer.limit:10}") private val transferLimit: Int = 10,
    @Value("\${fincore.ratelimit.transfer.window-seconds:60}") private val transferWindowSeconds: Long = 60L
) : OncePerRequestFilter() {

    companion object {
        const val LOGIN_LIMIT = 10
        const val LOGIN_WINDOW_SECONDS = 60L
        const val TRANSFER_LIMIT = 10
        const val TRANSFER_WINDOW_SECONDS = 60L
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val clientIp = extractClientIp(request)

        val rateLimitResult = when {
            path == "/api/v1/auth/login" && request.method.equals("POST", ignoreCase = true) -> {
                rateLimiterService.tryAcquire("login:$clientIp", loginLimit, loginWindowSeconds)
            }
            path == "/api/v1/transfers" && request.method.equals("POST", ignoreCase = true) -> {
                rateLimiterService.tryAcquire("transfer:$clientIp", transferLimit, transferWindowSeconds)
            }
            else -> RateLimitResult(allowed = true, retryAfterSeconds = 0)
        }

        if (!rateLimitResult.allowed) {
            val traceId = request.getHeader(CorrelationIdFilter.HEADER)
                ?: UUID.randomUUID().toString()

            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.setHeader("Retry-After", rateLimitResult.retryAfterSeconds.toString())

            val errorResponse = ErrorResponse(
                errorCode = ErrorCode.RATE_LIMIT_EXCEEDED,
                message = "Too many requests. Rate limit exceeded. Please retry in ${rateLimitResult.retryAfterSeconds} seconds.",
                details = emptyList(),
                traceId = traceId,
                timestamp = Instant.now()
            )

            response.writer.write(objectMapper.writeValueAsString(errorResponse))
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun extractClientIp(request: HttpServletRequest): String {
        // With server.forward-headers-strategy=NATIVE, remoteAddr is resolved by
        // trusted reverse proxies/ingress. Never trust raw unvalidated client headers (C-6).
        return request.remoteAddr ?: "unknown"
    }
}
