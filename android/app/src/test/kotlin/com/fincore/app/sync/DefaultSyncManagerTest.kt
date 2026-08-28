package com.fincore.app.sync

import com.fincore.core.common.sync.SyncStatus
import com.fincore.core.database.dao.PendingMutationDao
import com.fincore.core.database.dao.SyncMetadataDao
import com.fincore.core.database.entity.SyncMetadataEntity
import com.fincore.core.network.monitor.TestNetworkMonitor
import com.fincore.core.testing.MainDispatcherRule
import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.repository.AccountRepository
import com.fincore.feature.transactions.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class DefaultSyncManagerTest {

    private val accountRepository = mockk<AccountRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val syncMetadataDao = mockk<SyncMetadataDao>(relaxed = true)
    private val pendingMutationDao = mockk<PendingMutationDao>(relaxed = true)
    private val networkMonitor = TestNetworkMonitor(initialOnline = true)

    private val syncManager = DefaultSyncManager(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        syncMetadataDao = syncMetadataDao,
        pendingMutationDao = pendingMutationDao,
        networkMonitor = networkMonitor
    )

    @Test
    fun `sync executes accounts and transactions refresh and updates lastSyncedAt`() = runTest {
        val account = Account("acc-1", "cust-1", "GB1", "CHECKING", "ACTIVE", "GBP", "100.0000", "100.0000", 1L)
        coEvery { accountRepository.refreshAccounts() } returns Result.success(Unit)
        coEvery { accountRepository.getAccounts() } returns flowOf(listOf(account))
        coEvery { transactionRepository.refreshTransactions("acc-1") } returns Result.success(Unit)
        coEvery { pendingMutationDao.getPendingMutations() } returns emptyList()

        val result = syncManager.sync(force = true)

        assertTrue(result.isSuccess)
        assertEquals(SyncStatus.SUCCESS, syncManager.syncStatus.value)

        coVerify(exactly = 1) {
            accountRepository.refreshAccounts()
            transactionRepository.refreshTransactions("acc-1")
            syncMetadataDao.insertSyncMetadata(match { it.syncKey == "FINANCIAL_DATA" && it.status == "SUCCESS" })
        }
    }

    @Test
    fun `sync fails immediately when offline without refreshing`() = runTest {
        networkMonitor.setOnline(false)

        val result = syncManager.sync(force = true)

        assertFalse(result.isSuccess)
        assertEquals(SyncStatus.ERROR, syncManager.syncStatus.value)

        coVerify(exactly = 0) {
            accountRepository.refreshAccounts()
            transactionRepository.refreshTransactions(any())
        }
    }

    @Test
    fun `sync throttles repeated calls within throttle window`() = runTest {
        val account = Account("acc-1", "cust-1", "GB1", "CHECKING", "ACTIVE", "GBP", "100.0000", "100.0000", 1L)
        coEvery { accountRepository.refreshAccounts() } returns Result.success(Unit)
        coEvery { accountRepository.getAccounts() } returns flowOf(listOf(account))
        coEvery { transactionRepository.refreshTransactions("acc-1") } returns Result.success(Unit)
        coEvery { pendingMutationDao.getPendingMutations() } returns emptyList()

        // First call - succeeds
        val result1 = syncManager.sync(force = true)
        assertTrue(result1.isSuccess)

        // Immediate subsequent call without force - throttled
        val result2 = syncManager.sync(force = false)
        assertTrue(result2.isSuccess)

        // Verify account repository was called only ONCE
        coVerify(exactly = 1) {
            accountRepository.refreshAccounts()
        }
    }
}
