package com.fincore.shared.security

import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.error.ErrorCode
import com.fincore.shared.error.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
class CustomAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val errorResponse = ErrorResponse(
            errorCode = ErrorCode.AUTHENTICATION_REQUIRED,
            message = authException.message ?: "Full authentication is required to access this resource",
            details = null,
            traceId = CorrelationIdFilter.current() ?: request.getHeader(CorrelationIdFilter.HEADER),
            timestamp = Instant.now()
        )
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
