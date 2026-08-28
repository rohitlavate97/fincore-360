package com.fincore.app.sync

import com.fincore.core.common.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncWorkerTest {

    private val syncManager = mockk<SyncManager>()

    @Test
    fun `sync execution handles success result`() = runTest {
        coEvery { syncManager.sync(force = true) } returns kotlin.Result.success(Unit)

        val result = syncManager.sync(force = true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { syncManager.sync(force = true) }
    }
}
