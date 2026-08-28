package com.fincore.feature.accounts

import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.repository.AccountRepository
import com.fincore.feature.accounts.domain.usecase.GetAccountsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetAccountsUseCaseTest {

    private val repository = mockk<AccountRepository>()
    private val useCase = GetAccountsUseCase(repository)

    @Test
    fun `invoke delegates to repository getAccounts`() = runTest {
        val accounts = listOf(
            Account("a1", "c1", "GB1", "CHECKING", "ACTIVE", "GBP", "10.0000", "10.0000", 1L)
        )
        every { repository.getAccounts() } returns flowOf(accounts)

        val result = useCase().first()
        assertEquals(1, result.size)
        assertEquals("a1", result[0].id)
    }
}
