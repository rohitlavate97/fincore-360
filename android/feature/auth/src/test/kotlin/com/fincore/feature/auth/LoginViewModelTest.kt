package com.fincore.feature.auth

import com.fincore.core.common.result.ScreenState
import com.fincore.core.testing.MainDispatcherRule
import com.fincore.feature.auth.data.remote.dto.AuthResponseDto
import com.fincore.feature.auth.domain.usecase.LoginUseCase
import com.fincore.feature.auth.presentation.LoginViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class LoginViewModelTest {

    private val loginUseCase = mockk<LoginUseCase>()
    private val viewModel by lazy { LoginViewModel(loginUseCase) }

    @Test
    fun `initial state is Empty`() {
        assertEquals(ScreenState.Empty, viewModel.screenState.value)
    }

    @Test
    fun `login success transitions to Success state`() = runTest {
        val authResponse = AuthResponseDto("access", "refresh", "Bearer", 900, "uid1", "alice", listOf("ROLE_CUSTOMER"))
        coEvery { loginUseCase("alice", "password123", any()) } returns Result.success(authResponse)

        viewModel.login("alice", "password123")
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is ScreenState.Success)
        val success = viewModel.screenState.value as ScreenState.Success
        assertEquals("alice", success.data.username)
        assertTrue(success.data.isAuthenticated)
    }

    @Test
    fun `login failure transitions to Error state`() = runTest {
        coEvery { loginUseCase("alice", "wrong", any()) } returns Result.failure(Exception("Bad credentials"))

        viewModel.login("alice", "wrong")
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is ScreenState.Error)
        val error = viewModel.screenState.value as ScreenState.Error
        assertEquals("Bad credentials", error.message)
    }
}
