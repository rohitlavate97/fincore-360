package com.fincore.feature.auth

import com.fincore.core.security.TokenManager
import com.fincore.feature.auth.data.remote.AuthApi
import com.fincore.feature.auth.data.remote.dto.AuthResponseDto
import com.fincore.feature.auth.data.remote.dto.LoginRequestDto
import com.fincore.feature.auth.data.repository.AuthRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthRepositoryTest {

    private val authApi = mockk<AuthApi>()
    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val repository = AuthRepositoryImpl(authApi, tokenManager)

    @Test
    fun `login saves tokens into TokenManager on success`() = runTest {
        val response = AuthResponseDto("access-token-123", "refresh-token-456", "Bearer", 900, "user-1", "alice", listOf("ROLE_CUSTOMER"))
        coEvery { authApi.login(any()) } returns response

        val result = repository.login("alice", "secret", "device-1")

        assertTrue(result.isSuccess)
        assertEquals(response, result.getOrNull())
        coVerify { tokenManager.saveAccessToken("access-token-123") }
        coVerify { tokenManager.saveRefreshToken("refresh-token-456") }
    }

    @Test
    fun `logout clears tokens from TokenManager`() = runTest {
        coEvery { tokenManager.getRefreshToken() } returns "token-to-revoke"
        coEvery { authApi.logout(any()) } returns Unit

        val result = repository.logout()

        assertTrue(result.isSuccess)
        coVerify { tokenManager.clearTokens() }
    }
}
