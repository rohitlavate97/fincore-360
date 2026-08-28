package com.fincore.shared.error

import org.springframework.http.HttpStatus

/**
 * Base type for errors the domain raises deliberately.
 *
 * A domain rule violation is not a generic failure — it carries an ErrorCode and
 * the HTTP status it maps to, so the global handler never has to guess.
 */
abstract class DomainException(
    val errorCode: ErrorCode,
    val status: HttpStatus,
    override val message: String,
    val details: List<ErrorDetail>? = null,
) : RuntimeException(message)

class ResourceNotFoundException(
    message: String = "The requested resource was not found",
) : DomainException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message)

/**
 * Thrown when a transaction is asked to make a transition its lifecycle
 * forbids. See ARCHITECTURE.md §5 — invalid transitions must raise a domain
 * exception, never a generic error.
 */
class InvalidStateTransitionException(
    from: String,
    to: String,
) : DomainException(
    ErrorCode.TRANSFER_INVALID_STATE_TRANSITION,
    HttpStatus.CONFLICT,
    "Transition from $from to $to is not permitted",
)
