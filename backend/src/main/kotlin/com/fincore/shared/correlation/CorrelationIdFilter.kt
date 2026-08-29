package com.fincore.shared.correlation

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Reads `X-Correlation-ID` from the request, or generates one, and places it in
 * the SLF4J MDC so every log line emitted while handling the request carries it.
 *
 * A request must never be untraceable, so an absent header is generated rather
 * than rejected.
 *
 * The same value is echoed on the response and surfaces as `traceId` in the
 * error contract — that is what turns a user-reported error into a log lookup.
 * See OBSERVABILITY.md §1 and API-DESIGN.md §3.
 *
 * Ordered highest so the ID exists before any other filter can log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = request.getHeader(HEADER)
            ?.trim()
            ?.takeIf { it.length == 36 && UUID_RE.matches(it) }
            ?: UUID.randomUUID().toString()

        val previous = MDC.get(MDC_KEY)
        MDC.put(MDC_KEY, correlationId)
        response.setHeader(HEADER, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            if (previous != null) {
                MDC.put(MDC_KEY, previous)
            } else {
                MDC.remove(MDC_KEY)
            }
        }
    }

    companion object {
        const val HEADER = "X-Correlation-ID"
        const val MDC_KEY = "correlationId"
        private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** The correlation ID for the request being handled, if any. */
        fun current(): String? = MDC.get(MDC_KEY)
    }
}
