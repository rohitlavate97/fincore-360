package com.fincore.shared.error

import java.time.Instant

/**
 * The single error response shape for the entire API. See API-DESIGN.md §3.
 *
 * Never carries a stack trace, an internal class name, SQL, or framework error
 * text. `traceId` is the debugging channel; `message` is a product surface.
 */
data class ErrorResponse(
    val errorCode: ErrorCode,
    val message: String,
    val details: List<ErrorDetail>? = null,
    val traceId: String?,
    val timestamp: Instant,
)

/** Populated only for validation errors (400, 422). */
data class ErrorDetail(
    val field: String,
    val issue: String,
)
