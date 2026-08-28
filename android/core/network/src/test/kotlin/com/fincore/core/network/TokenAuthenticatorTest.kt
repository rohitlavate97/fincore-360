package com.fincore.core.network

import com.fincore.core.network.authenticator.TokenAuthenticator
import com.fincore.core.security.TokenManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenAuthenticatorTest {

    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val authenticator = TokenAuthenticator(tokenManager, json)

    @Test
    fun `when token was already refreshed by concurrent request returns new token immediately`() {
        coEvery { tokenManager.getAccessToken() } returns "new-already-refreshed-token"

        val failedRequest = Request.Builder()
            .url("https://api.fincore.com/api/v1/accounts")
            .header("Authorization", "Bearer old-stale-token")
            .build()

        val response = Response.Builder()
            .request(failedRequest)
            .protocol(Protocol.HTTP_2)
            .code(401)
            .message("Unauthorized")
            .build()

        val retryRequest = authenticator.authenticate(null, response)

        assertEquals("Bearer new-already-refreshed-token", retryRequest?.header("Authorization"))
    }

    @Test
    fun `when refresh path returns 401 clears tokens and aborts`() {
        val refreshRequest = Request.Builder()
            .url("https://api.fincore.com/api/v1/auth/refresh")
            .build()

        val response = Response.Builder()
            .request(refreshRequest)
            .protocol(Protocol.HTTP_2)
            .code(401)
            .message("Unauthorized")
            .build()

        val retryRequest = authenticator.authenticate(null, response)

        assertNull(retryRequest)
        coVerify { tokenManager.clearTokens() }
    }
}
