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

class AuthenticationFailedException(
    message: String = "Authentication failed"
) : DomainException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED, message)

class ConflictException(
    message: String
) : DomainException(ErrorCode.VALIDATION_FAILED, HttpStatus.CONFLICT, message)

class InsufficientFundsException(
    message: String = "Insufficient available balance to complete transfer"
) : DomainException(ErrorCode.TRANSFER_INSUFFICIENT_FUNDS, HttpStatus.UNPROCESSABLE_ENTITY, message)

class AccountNotActiveException(
    message: String = "Account is not active"
) : DomainException(ErrorCode.ACCOUNT_NOT_ACTIVE, HttpStatus.UNPROCESSABLE_ENTITY, message)

class IdempotencyInProgressException(
    message: String = "A request with this idempotency key is currently being processed"
) : DomainException(ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS, HttpStatus.CONFLICT, message)

class IdempotencyKeyRequiredException(
    message: String = "Idempotency-Key header is required"
) : DomainException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, HttpStatus.BAD_REQUEST, message)
