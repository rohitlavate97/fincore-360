package com.fincore.shared.idempotency

import com.fincore.shared.error.ConflictException
import com.fincore.shared.error.IdempotencyInProgressException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class IdempotencyServiceTest {

    private val repository = mockk<IdempotencyKeyRepository>()
    private val service = IdempotencyService(repository)

    @Test
    fun `startOrResolve returns Proceed for unseen key`() {
        val key = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val endpoint = "/api/v1/transfers"

        every { repository.findByKey(key) } returns Optional.empty()
        every { repository.saveAndFlush(any()) } answers { firstArg() }

        val resolution = service.startOrResolve(key, userId, endpoint)

        assertTrue(resolution is IdempotencyResolution.Proceed)
        val proceed = resolution as IdempotencyResolution.Proceed
        assertEquals(key, proceed.record.key)
        assertEquals(IdempotencyState.IN_PROGRESS, proceed.record.state)
    }

    @Test
    fun `startOrResolve returns Replay when key state is COMPLETE`() {
        val key = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val endpoint = "/api/v1/transfers"

        val completeRecord = IdempotencyKeyRecord(
            key = key,
            userId = userId,
            endpoint = endpoint,
            state = IdempotencyState.COMPLETE,
            responseStatus = 201,
            responseBody = """{"status":"COMPLETED"}""",
            expiresAt = Instant.now().plusSeconds(3600)
        )

        every { repository.findByKey(key) } returns Optional.of(completeRecord)

        val resolution = service.startOrResolve(key, userId, endpoint)

        assertTrue(resolution is IdempotencyResolution.Replay)
        val replay = resolution as IdempotencyResolution.Replay
        assertEquals(201, replay.status)
        assertEquals("""{"status":"COMPLETED"}""", replay.body)
    }

    @Test
    fun `startOrResolve throws IdempotencyInProgressException when key state is IN_PROGRESS`() {
        val key = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val endpoint = "/api/v1/transfers"

        val inProgressRecord = IdempotencyKeyRecord(
            key = key,
            userId = userId,
            endpoint = endpoint,
            state = IdempotencyState.IN_PROGRESS,
            expiresAt = Instant.now().plusSeconds(3600)
        )

        every { repository.findByKey(key) } returns Optional.of(inProgressRecord)

        assertThrows(IdempotencyInProgressException::class.java) {
            service.startOrResolve(key, userId, endpoint)
        }
    }

    @Test
    fun `startOrResolve throws ConflictException when key was presented by different user`() {
        val key = UUID.randomUUID()
        val userA = UUID.randomUUID()
        val userB = UUID.randomUUID()
        val endpoint = "/api/v1/transfers"

        val existingRecord = IdempotencyKeyRecord(
            key = key,
            userId = userA,
            endpoint = endpoint,
            state = IdempotencyState.COMPLETE,
            responseStatus = 201,
            responseBody = "{}",
            expiresAt = Instant.now().plusSeconds(3600)
        )

        every { repository.findByKey(key) } returns Optional.of(existingRecord)

        assertThrows(ConflictException::class.java) {
            service.startOrResolve(key, userB, endpoint)
        }
    }

    @Test
    fun `complete transitions state to COMPLETE and records status and body`() {
        val recordId = UUID.randomUUID()
        val record = IdempotencyKeyRecord(
            id = recordId,
            key = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            endpoint = "/api/v1/transfers",
            state = IdempotencyState.IN_PROGRESS,
            expiresAt = Instant.now().plusSeconds(3600)
        )

        every { repository.findById(recordId) } returns Optional.of(record)
        every { repository.saveAndFlush(record) } returns record

        service.complete(recordId, 201, """{"id":"tx-1"}""")

        assertEquals(IdempotencyState.COMPLETE, record.state)
        assertEquals(201, record.responseStatus)
        assertEquals("""{"id":"tx-1"}""", record.responseBody)
        verify(exactly = 1) { repository.saveAndFlush(record) }
    }
}
