package com.fincore.app.sync

import com.fincore.core.common.result.ErrorType
import com.fincore.core.common.result.ScreenState
import com.fincore.core.common.sync.SyncStatus
import com.fincore.core.database.dao.AccountDao
import com.fincore.core.database.dao.PendingMutationDao
import com.fincore.core.database.dao.SyncMetadataDao
import com.fincore.core.database.dao.TransactionDao
import com.fincore.core.database.entity.AccountEntity
import com.fincore.core.database.entity.SyncMetadataEntity
import com.fincore.core.database.entity.TransactionEntity
import com.fincore.core.network.monitor.TestNetworkMonitor
import com.fincore.core.testing.MainDispatcherRule
import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.repository.AccountRepository
import com.fincore.feature.accounts.domain.usecase.CreateAccountUseCase
import com.fincore.feature.accounts.domain.usecase.GetAccountsUseCase
import com.fincore.feature.accounts.domain.usecase.RefreshAccountsUseCase
import com.fincore.feature.accounts.presentation.AccountsViewModel
import com.fincore.feature.transactions.domain.repository.TransactionRepository
import com.fincore.feature.transfer.domain.usecase.ExecuteTransferUseCase
import com.fincore.feature.transfer.presentation.TransferViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OfflineSyncIntegrationTest {

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

    private val getAccountsUseCase = mockk<GetAccountsUseCase>()
    private val refreshAccountsUseCase = mockk<RefreshAccountsUseCase>()
    private val createAccountUseCase = mockk<CreateAccountUseCase>()
    private val executeTransferUseCase = mockk<ExecuteTransferUseCase>()

    @Test
    @DisplayName("Exit Criterion 1: Cached data rendered offline with staleness timestamp")
    fun cachedDataRenderedOffline() = runTest {
        // Device goes offline (Airplane mode)
        networkMonitor.setOnline(false)

        val cachedAccounts = listOf(
            Account("acc-offline-1", "cust-1", "GB29FINC999", "CHECKING", "ACTIVE", "GBP", "1250.0000", "1250.0000", 1000L)
        )
        every { getAccountsUseCase() } returns flowOf(cachedAccounts)
        coEvery { refreshAccountsUseCase() } returns Result.failure(RuntimeException("Airplane mode"))
        every { syncMetadataDao.observeSyncMetadata("FINANCIAL_DATA") } returns flowOf(
            SyncMetadataEntity("FINANCIAL_DATA", 1700000000000L, "SUCCESS")
        )

        val accountsViewModel = AccountsViewModel(
            getAccountsUseCase,
            refreshAccountsUseCase,
            createAccountUseCase,
            networkMonitor,
            syncMetadataDao
        )
        advanceUntilIdle()

        // 1. Verifies offline state is detected
        assertFalse(accountsViewModel.isOnline.value)

        // 2. Verifies cached accounts still render successfully from Room SSOT
        assertTrue(accountsViewModel.screenState.value is ScreenState.Success)
        val success = accountsViewModel.screenState.value as ScreenState.Success
        assertEquals(1, success.data.size)
        assertEquals("acc-offline-1", success.data[0].id)
        assertEquals("1250.0000", success.data[0].availableBalance)

        // 3. Verifies last-synced timestamp is exposed for UI staleness label
        assertEquals(1700000000000L, accountsViewModel.lastSyncedAt.value)
    }

    @Test
    @DisplayName("Exit Criterion 1b: Offline transfer attempt is rejected with connection required and NEVER queued")
    fun offlineTransferRejectedAndNeverQueued() = runTest {
        // Device offline
        networkMonitor.setOnline(false)
        every { getAccountsUseCase() } returns flowOf(emptyList())

        val transferViewModel = TransferViewModel(
            getAccountsUseCase,
            executeTransferUseCase,
            networkMonitor
        )

        transferViewModel.onSourceAccountSelected("acc-1")
        transferViewModel.onDestinationAccountChanged("acc-2")
        transferViewModel.onAmountChanged("100.0000")

        transferViewModel.submitTransfer()
        advanceUntilIdle()

        // Verifies immediate error state
        val transferState = transferViewModel.uiState.value.transferState
        assertTrue(transferState is ScreenState.Error)
        assertEquals(ErrorType.NETWORK, (transferState as ScreenState.Error).type)
        assertTrue(transferState.message.contains("Connection required", ignoreCase = true))

        // Verifies no transfer use case or mutation queueing occurred
        coVerify(exactly = 0) { executeTransferUseCase(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { pendingMutationDao.upsert(any()) }
    }

    @Test
    @DisplayName("Exit Criterion 2: Sync restores correct state when connectivity restored")
    fun syncRestoresCorrectStateWhenOnline() = runTest {
        // Connectivity restored
        networkMonitor.setOnline(true)

        val freshServerAccounts = listOf(
            Account("acc-1", "cust-1", "GB29FINC111", "CHECKING", "ACTIVE", "GBP", "2500.0000", "2500.0000", 2000L)
        )

        coEvery { accountRepository.refreshAccounts() } returns Result.success(Unit)
        coEvery { accountRepository.getAccounts() } returns flowOf(freshServerAccounts)
        coEvery { transactionRepository.refreshTransactions("acc-1") } returns Result.success(Unit)
        coEvery { pendingMutationDao.getPendingMutations() } returns emptyList()

        val syncResult = syncManager.sync(force = true)

        assertTrue(syncResult.isSuccess)
        assertEquals(SyncStatus.SUCCESS, syncManager.syncStatus.value)

        // Verifies server data was written to Room and last-synced timestamp advanced
        coVerify(exactly = 1) {
            accountRepository.refreshAccounts()
            transactionRepository.refreshTransactions("acc-1")
            syncMetadataDao.insertSyncMetadata(match {
                it.syncKey == "FINANCIAL_DATA" && it.status == "SUCCESS"
            })
        }
    }
}
