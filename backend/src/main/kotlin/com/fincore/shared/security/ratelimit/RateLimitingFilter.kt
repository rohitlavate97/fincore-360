package com.fincore.shared.security.ratelimit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.error.ErrorResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RateLimitingFilter(
    private val rateLimiterService: RateLimiterService
) : OncePerRequestFilter() {

    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

    companion object {
        const val LOGIN_LIMIT = 5
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
                rateLimiterService.tryAcquire("login:$clientIp", LOGIN_LIMIT, LOGIN_WINDOW_SECONDS)
            }
            path == "/api/v1/transfers" && request.method.equals("POST", ignoreCase = true) -> {
                rateLimiterService.tryAcquire("transfer:$clientIp", TRANSFER_LIMIT, TRANSFER_WINDOW_SECONDS)
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
                errorCode = com.fincore.shared.error.ErrorCode.RATE_LIMIT_EXCEEDED,
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
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",")[0].trim()
        }
        return request.remoteAddr ?: "unknown"
    }
}
