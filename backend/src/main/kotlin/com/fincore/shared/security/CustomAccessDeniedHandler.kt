package com.fincore.shared.security

import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.error.ErrorCode
import com.fincore.shared.error.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
class CustomAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val errorResponse = ErrorResponse(
            errorCode = ErrorCode.ACCESS_DENIED,
            message = "Access is denied",
            details = null,
            traceId = CorrelationIdFilter.current() ?: request.getHeader(CorrelationIdFilter.HEADER),
            timestamp = Instant.now()
        )
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }
}
