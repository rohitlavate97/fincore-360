package com.fincore.feature.accounts

import com.fincore.core.common.result.ScreenState
import com.fincore.core.testing.MainDispatcherRule
import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.usecase.CreateAccountUseCase
import com.fincore.feature.accounts.domain.usecase.GetAccountsUseCase
import com.fincore.feature.accounts.domain.usecase.RefreshAccountsUseCase
import com.fincore.feature.accounts.presentation.AccountsViewModel
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
class AccountsViewModelTest {

    private val getAccountsUseCase = mockk<GetAccountsUseCase>()
    private val refreshAccountsUseCase = mockk<RefreshAccountsUseCase>()
    private val createAccountUseCase = mockk<CreateAccountUseCase>()

    @Test
    fun `when accounts exist transitions to Success state`() = runTest {
        val accounts = listOf(
            Account("a1", "c1", "GB29FINC123", "CHECKING", "ACTIVE", "GBP", "100.0000", "100.0000", 1L)
        )
        every { getAccountsUseCase() } returns flowOf(accounts)
        coEvery { refreshAccountsUseCase() } returns Result.success(Unit)

        val viewModel = AccountsViewModel(getAccountsUseCase, refreshAccountsUseCase, createAccountUseCase)
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is ScreenState.Success)
        val success = viewModel.screenState.value as ScreenState.Success
        assertEquals(1, success.data.size)
        assertEquals("a1", success.data[0].id)
    }

    @Test
    fun `when accounts list is empty transitions to Empty state`() = runTest {
        every { getAccountsUseCase() } returns flowOf(emptyList())
        coEvery { refreshAccountsUseCase() } returns Result.success(Unit)

        val viewModel = AccountsViewModel(getAccountsUseCase, refreshAccountsUseCase, createAccountUseCase)
        advanceUntilIdle()

        assertEquals(ScreenState.Empty, viewModel.screenState.value)
    }

    @Test
    fun `when refresh fails and no cached accounts exist transitions to Error state`() = runTest {
        every { getAccountsUseCase() } returns flowOf(emptyList())
        coEvery { refreshAccountsUseCase() } returns Result.failure(Exception("Network unreachable"))

        val viewModel = AccountsViewModel(getAccountsUseCase, refreshAccountsUseCase, createAccountUseCase)
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is ScreenState.Error)
        val error = viewModel.screenState.value as ScreenState.Error
        assertEquals("Network unreachable", error.message)
    }

    @Test
    fun `createAccount delegates to useCase`() = runTest {
        every { getAccountsUseCase() } returns flowOf(emptyList())
        coEvery { refreshAccountsUseCase() } returns Result.success(Unit)
        val created = Account("a2", "c1", "GB2", "CHECKING", "ACTIVE", "GBP", "0.0000", "0.0000", 2L)
        coEvery { createAccountUseCase("CHECKING", "GBP", "0.0000") } returns Result.success(created)

        val viewModel = AccountsViewModel(getAccountsUseCase, refreshAccountsUseCase, createAccountUseCase)
        viewModel.createAccount("CHECKING", "GBP", "0.0000")
        advanceUntilIdle()

        coVerify { createAccountUseCase("CHECKING", "GBP", "0.0000") }
    }
}
