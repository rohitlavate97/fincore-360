package com.fincore.feature.transactions

import com.fincore.core.common.result.ScreenState
import com.fincore.core.database.dao.TransactionDao
import com.fincore.core.database.entity.TransactionEntity
import com.fincore.core.testing.MainDispatcherRule
import com.fincore.feature.transactions.data.remote.TransactionsApi
import com.fincore.feature.transactions.data.remote.dto.PagedTransactionDto
import com.fincore.feature.transactions.data.remote.dto.TransactionDto
import com.fincore.feature.transactions.data.repository.TransactionRepositoryImpl
import com.fincore.feature.transactions.domain.model.TransactionItem
import com.fincore.feature.transactions.domain.usecase.GetAccountTransactionsUseCase
import com.fincore.feature.transactions.presentation.TransactionsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

class TransactionRepositoryTest {

    private val api = mockk<TransactionsApi>()
    private val dao = mockk<TransactionDao>(relaxed = true)
    private val repository = TransactionRepositoryImpl(api, dao)

    @Test
    fun `getTransactionsForAccount streams from Room DAO as single source of truth`() = runTest {
        val entity = TransactionEntity("tx-1", "k-1", "acc-1", "acc-2", "TRANSFER", "COMPLETED", "100.0000", "GBP", 1000L)
        every { dao.getTransactionsByAccount("acc-1") } returns flowOf(listOf(entity))

        val items = repository.getTransactionsForAccount("acc-1").first()

        assertEquals(1, items.size)
        assertEquals("tx-1", items[0].id)
        assertEquals("100.0000", items[0].amount)
    }

    @Test
    fun `refreshTransactions fetches from API and updates Room DAO`() = runTest {
        val dto = TransactionDto("tx-remote", "k-1", "acc-1", "acc-2", "TRANSFER", "COMPLETED", "200.0000", "GBP", "2026-08-28T12:00:00Z")
        coEvery { api.getAccountTransactions("acc-1", any(), any()) } returns PagedTransactionDto(
            items = listOf(dto),
            page = 0,
            size = 20,
            totalElements = 1,
            totalPages = 1,
            hasNext = false
        )

        val result = repository.refreshTransactions("acc-1")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { dao.upsertAll(any()) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class TransactionsViewModelTest {

    private val useCase = mockk<GetAccountTransactionsUseCase>()

    @Test
    fun `loadTransactions transitions to Success when transactions exist`() = runTest {
        val items = listOf(
            TransactionItem("t1", "k1", "a1", "a2", "TRANSFER", "COMPLETED", "50.0000", "GBP", 100L)
        )
        every { useCase("a1") } returns flowOf(items)
        coEvery { useCase.refresh("a1") } returns Result.success(Unit)

        val viewModel = TransactionsViewModel(useCase)
        viewModel.loadTransactions("a1")
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is ScreenState.Success)
        val success = viewModel.screenState.value as ScreenState.Success
        assertEquals(1, success.data.size)
        assertEquals("t1", success.data[0].id)
    }
}
