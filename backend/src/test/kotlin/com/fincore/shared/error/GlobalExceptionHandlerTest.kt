package com.fincore.shared.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleIllegalArgument returns 400 with VALIDATION_FAILED`() {
        val ex = IllegalArgumentException("Source and destination accounts must be distinct")
        val response = handler.handleIllegalArgument(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.VALIDATION_FAILED, response.body?.errorCode)
        assertEquals("The request was not valid", response.body?.message)
    }

    @Test
    fun `handleIntegrity returns 409 with CONFLICT`() {
        val ex = DataIntegrityViolationException("check constraint failure")
        val response = handler.handleIntegrity(ex)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.CONFLICT, response.body?.errorCode)
        assertEquals("The request conflicts with the current state", response.body?.message)
    }

    @Test
    fun `handleContention returns 503 with SYSTEM_DEGRADED`() {
        val ex = CannotAcquireLockException("could not obtain lock on row in table accounts")
        val response = handler.handleContention(ex)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(ErrorCode.SYSTEM_DEGRADED, response.body?.errorCode)
        assertEquals("Database is under contention, please retry", response.body?.message)
    }

    @Test
    fun `handleAccessDenied returns 403 with ACCESS_DENIED`() {
        val ex = AccessDeniedException("Forbidden")
        val response = handler.handleAccessDenied(ex)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.ACCESS_DENIED, response.body?.errorCode)
    }

    @Test
    fun `handleUnexpected returns 500 with INTERNAL_ERROR and generic message`() {
        val ex = RuntimeException("Sensitive internal database connection string")
        val response = handler.handleUnexpected(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(ErrorCode.INTERNAL_ERROR, response.body?.errorCode)
        assertEquals("An unexpected error occurred", response.body?.message)
    }
}
