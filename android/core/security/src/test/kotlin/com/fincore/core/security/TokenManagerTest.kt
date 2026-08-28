package com.fincore.core.security

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val tokenManager = KeystoreTokenManager(context)

    @Test
    fun `save and retrieve access token`() = runTest {
        assertNull(tokenManager.getAccessToken())

        tokenManager.saveAccessToken("jwt.access.token.123")
        assertEquals("jwt.access.token.123", tokenManager.getAccessToken())
    }

    @Test
    fun `save and retrieve refresh token`() = runTest {
        assertNull(tokenManager.getRefreshToken())

        tokenManager.saveRefreshToken("opaque.refresh.token.456")
        assertEquals("opaque.refresh.token.456", tokenManager.getRefreshToken())
    }

    @Test
    fun `clearTokens purges both access and refresh tokens`() = runTest {
        tokenManager.saveAccessToken("access")
        tokenManager.saveRefreshToken("refresh")

        tokenManager.clearTokens()

        assertNull(tokenManager.getAccessToken())
        assertNull(tokenManager.getRefreshToken())
    }
}
