package com.fincore.feature.accounts

import com.fincore.core.database.dao.AccountDao
import com.fincore.core.database.entity.AccountEntity
import com.fincore.feature.accounts.data.remote.AccountApi
import com.fincore.feature.accounts.data.remote.dto.AccountDto
import com.fincore.feature.accounts.data.remote.dto.PagedAccountDto
import com.fincore.feature.accounts.data.repository.AccountRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountRepositoryTest {

    private val accountApi = mockk<AccountApi>()
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val repository = AccountRepositoryImpl(accountApi, accountDao)

    @Test
    fun `getAccounts streams data directly from AccountDao as single source of truth`() = runTest {
        val entity = AccountEntity(
            id = "acc-1",
            customerId = "cust-1",
            accountNumber = "GB29FINC123",
            accountType = "CHECKING",
            status = "ACTIVE",
            currency = "GBP",
            ledgerBalance = "1000.0000",
            availableBalance = "1000.0000",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        every { accountDao.getAllAccounts() } returns flowOf(listOf(entity))

        val accounts = repository.getAccounts().first()

        assertEquals(1, accounts.size)
        assertEquals("acc-1", accounts[0].id)
        assertEquals("1000.0000", accounts[0].availableBalance)
        assertEquals("CHECKING", accounts[0].accountType)
    }

    @Test
    fun `refreshAccounts fetches from remote and updates AccountDao`() = runTest {
        val dto = AccountDto(
            id = "acc-remote-1",
            customerId = "cust-1",
            accountNumber = "GB29FINC999",
            accountType = "SAVINGS",
            status = "ACTIVE",
            currency = "GBP",
            ledgerBalance = "2500.0000",
            availableBalance = "2500.0000",
            createdAt = "2026-08-28T12:00:00Z"
        )

        coEvery { accountApi.getAccounts(any(), any()) } returns PagedAccountDto(
            items = listOf(dto),
            page = 0,
            size = 20,
            totalElements = 1,
            totalPages = 1,
            hasNext = false
        )

        val result = repository.refreshAccounts()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { accountDao.upsertAll(any()) }
    }
}
