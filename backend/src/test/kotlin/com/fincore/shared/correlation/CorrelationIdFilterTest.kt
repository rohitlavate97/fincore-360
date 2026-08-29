package com.fincore.shared.correlation

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID

class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter()

    @Test
    fun `valid UUID in header is preserved and echoed`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val validUuid = UUID.randomUUID().toString()
        request.addHeader(CorrelationIdFilter.HEADER, validUuid)

        var capturedMdc: String? = null
        val filterChain = FilterChain { _, _ ->
            capturedMdc = CorrelationIdFilter.current()
        }

        filter.doFilter(request, response, filterChain)

        assertEquals(validUuid, capturedMdc)
        assertEquals(validUuid, response.getHeader(CorrelationIdFilter.HEADER))
        assertNull(CorrelationIdFilter.current(), "MDC should be cleared after request")
    }

    @Test
    fun `malformed or forged header generates fresh UUID and prevents log injection`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val forgedHeader = "MALFORMED_HEADER_WITH\nNEWLINE_AND_INJECTION"
        request.addHeader(CorrelationIdFilter.HEADER, forgedHeader)

        var capturedMdc: String? = null
        val filterChain = FilterChain { _, _ ->
            capturedMdc = CorrelationIdFilter.current()
        }

        filter.doFilter(request, response, filterChain)

        assertNotNull(capturedMdc)
        assertNotEquals(forgedHeader, capturedMdc)
        // Must be a valid UUID
        assertDoesNotThrow { UUID.fromString(capturedMdc) }
        assertEquals(capturedMdc, response.getHeader(CorrelationIdFilter.HEADER))
    }

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            org.junit.jupiter.api.fail("Expected block not to throw exception, but threw $e")
        }
    }
}
