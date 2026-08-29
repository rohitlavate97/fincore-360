package com.fincore.shared.idempotency

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdempotencyKeyPurgeJobTest {

    private val repository = mockk<IdempotencyKeyRepository>()
    private val purgeJob = IdempotencyKeyPurgeJob(repository)

    @Test
    fun `purgeExpiredKeys delegates to deleteAllExpired and returns deleted count`() {
        every { repository.deleteAllExpired(any()) } returns 42

        val deleted = purgeJob.purgeExpiredKeys()

        assertEquals(42, deleted)
        verify(exactly = 1) { repository.deleteAllExpired(any()) }
    }
}
