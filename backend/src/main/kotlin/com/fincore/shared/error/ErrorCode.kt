package com.fincore.shared.error

/**
 * Application-defined error codes.
 *
 * These are a stable, searchable contract — they appear in API responses and in
 * runbooks, so renaming one is a breaking change. Never expose a raw exception
 * class name in their place. See API-DESIGN.md §3.
 */
enum class ErrorCode {
    // Validation
    VALIDATION_FAILED,
    MALFORMED_REQUEST,

    // Authentication / authorization  (Phase 3)
    AUTHENTICATION_REQUIRED,
    AUTHENTICATION_FAILED,
    ACCESS_DENIED,

    // Resources
    RESOURCE_NOT_FOUND,

    // Idempotency  (Phase 5)
    IDEMPOTENCY_KEY_REQUIRED,
    IDEMPOTENCY_REQUEST_IN_PROGRESS,

    // Transactions  (Phase 5)
    TRANSFER_INSUFFICIENT_FUNDS,
    TRANSFER_INVALID_STATE_TRANSITION,
    ACCOUNT_NOT_ACTIVE,

    // Infrastructure
    DEPENDENCY_UNAVAILABLE,
    INTERNAL_ERROR,
}
