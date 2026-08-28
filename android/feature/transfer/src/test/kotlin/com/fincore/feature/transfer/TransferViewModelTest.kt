package com.fincore.feature.transfer

import com.fincore.core.common.result.ErrorType
import com.fincore.core.common.result.ScreenState
import com.fincore.core.network.monitor.TestNetworkMonitor
import com.fincore.core.testing.MainDispatcherRule
import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.usecase.GetAccountsUseCase
import com.fincore.feature.transfer.domain.model.TransferRecord
import com.fincore.feature.transfer.domain.usecase.ExecuteTransferUseCase
import com.fincore.feature.transfer.presentation.TransferViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class TransferViewModelTest {

    private val getAccountsUseCase = mockk<GetAccountsUseCase>()
    private val executeTransferUseCase = mockk<ExecuteTransferUseCase>()
    private val networkMonitor = TestNetworkMonitor(initialOnline = true)

    @Test
    fun `validation error on invalid amount`() = runTest {
        every { getAccountsUseCase() } returns flowOf(emptyList())

        val viewModel = TransferViewModel(getAccountsUseCase, executeTransferUseCase, networkMonitor)
        viewModel.onSourceAccountSelected("acc-1")
        viewModel.onDestinationAccountChanged("acc-2")
        viewModel.onAmountChanged("-50.00")

        viewModel.submitTransfer()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.transferState is ScreenState.Error)
    }

    @Test
    fun `when offline transfer is blocked with network error and never executed`() = runTest {
        networkMonitor.setOnline(false)
        every { getAccountsUseCase() } returns flowOf(emptyList())

        val viewModel = TransferViewModel(getAccountsUseCase, executeTransferUseCase, networkMonitor)
        viewModel.onSourceAccountSelected("acc-1")
        viewModel.onDestinationAccountChanged("acc-2")
        viewModel.onAmountChanged("50.00")

        viewModel.submitTransfer()
        advanceUntilIdle()

        val state = viewModel.uiState.value.transferState
        assertTrue(state is ScreenState.Error)
        assertEquals(ErrorType.NETWORK, (state as ScreenState.Error).type)
        coVerify(exactly = 0) { executeTransferUseCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `successful transfer transitions to Success state`() = runTest {
        val account = Account("acc-1", "cust-1", "GB1", "CHECKING", "ACTIVE", "GBP", "500.0000", "500.0000", 1L)
        every { getAccountsUseCase() } returns flowOf(listOf(account))

        val record = TransferRecord("tx-1", "k1", "acc-1", "acc-2", "TRANSFER", "COMPLETED", "100.0000", "GBP", 1L)
        coEvery { executeTransferUseCase("acc-1", "acc-2", "100.0000", "GBP", any()) } returns Result.success(record)

        val viewModel = TransferViewModel(getAccountsUseCase, executeTransferUseCase, networkMonitor)
        advanceUntilIdle()

        viewModel.onDestinationAccountChanged("acc-2")
        viewModel.onAmountChanged("100.0000")
        viewModel.submitTransfer()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.transferState is ScreenState.Success)
        val success = viewModel.uiState.value.transferState as ScreenState.Success
        assertEquals("tx-1", success.data.transactionId)
    }
}
