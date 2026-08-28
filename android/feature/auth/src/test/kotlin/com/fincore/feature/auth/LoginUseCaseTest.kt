package com.fincore.feature.auth

import com.fincore.feature.auth.data.remote.dto.AuthResponseDto
import com.fincore.feature.auth.domain.repository.AuthRepository
import com.fincore.feature.auth.domain.usecase.LoginUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoginUseCaseTest {

    private val authRepository = mockk<AuthRepository>()
    private val loginUseCase = LoginUseCase(authRepository)

    @Test
    fun `blank username returns failure`() = runTest {
        val result = loginUseCase("", "password123")
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.login(any(), any(), any()) }
    }

    @Test
    fun `blank password returns failure`() = runTest {
        val result = loginUseCase("alice", "   ")
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { authRepository.login(any(), any(), any()) }
    }

    @Test
    fun `valid credentials calls repository`() = runTest {
        val expected = AuthResponseDto("access", "refresh", "Bearer", 900, "uid1", "alice", listOf("ROLE_CUSTOMER"))
        coEvery { authRepository.login("alice", "password123", any()) } returns Result.success(expected)

        val result = loginUseCase("alice", "password123")
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }
}
