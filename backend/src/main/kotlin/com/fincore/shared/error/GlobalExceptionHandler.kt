package com.fincore.shared.error

import com.fincore.shared.correlation.CorrelationIdFilter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

/**
 * Maps every exception to the single error contract in API-DESIGN.md §3.
 *
 * The rule this class exists to enforce: no stack trace, internal class name,
 * SQL, or framework error text ever reaches a client. Diagnostics go to the log,
 * correlated by traceId.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Deliberate domain rule violations — these carry their own code and status. */
    @ExceptionHandler(DomainException::class)
    fun handleDomain(ex: DomainException): ResponseEntity<ErrorResponse> {
        log.warn("Domain rule violation: {} - {}", ex.errorCode, ex.message)
        return respond(ex.status, ex.errorCode, ex.message, ex.details)
    }

    /** Bean Validation failures — the one case that populates `details`. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors.map {
            ErrorDetail(field = it.field, issue = it.defaultMessage ?: "is invalid")
        }
        return respond(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_FAILED,
            "The request failed validation",
            details,
        )
    }

    /** Unparseable body. The parser's message is deliberately NOT forwarded. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Malformed request body: {}", ex.message)
        return respond(
            HttpStatus.BAD_REQUEST,
            ErrorCode.MALFORMED_REQUEST,
            "The request body could not be parsed",
        )
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        respond(
            HttpStatus.NOT_FOUND,
            ErrorCode.RESOURCE_NOT_FOUND,
            "The requested resource was not found",
        )    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDenied(ex: org.springframework.security.access.AccessDeniedException): ResponseEntity<ErrorResponse> =
        respond(
            HttpStatus.FORBIDDEN,
            ErrorCode.ACCESS_DENIED,
            "Access is denied",
        )

    @ExceptionHandler(org.springframework.security.core.AuthenticationException::class)
    fun handleAuthException(ex: org.springframework.security.core.AuthenticationException): ResponseEntity<ErrorResponse> =
        respond(
            HttpStatus.UNAUTHORIZED,
            ErrorCode.AUTHENTICATION_REQUIRED,
            ex.message ?: "Authentication required",
        )

    /**
     * Anything unanticipated. The exception is logged in full; the client gets a
     * generic message and a traceId to quote to support.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return respond(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_ERROR,
            "An unexpected error occurred",
        )
    }

    private fun respond(
        status: HttpStatus,
        code: ErrorCode,
        message: String,
        details: List<ErrorDetail>? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                errorCode = code,
                message = message,
                details = details,
                traceId = CorrelationIdFilter.current(),
                timestamp = Instant.now(),
            ),
        )
}
