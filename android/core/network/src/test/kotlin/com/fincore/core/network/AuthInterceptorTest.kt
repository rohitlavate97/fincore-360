package com.fincore.core.network

import com.fincore.core.network.interceptor.AuthInterceptor
import com.fincore.core.security.TokenManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthInterceptorTest {

    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val interceptor = AuthInterceptor(tokenManager)

    @Test
    fun `attaches bearer token when present`() {
        coEvery { tokenManager.getAccessToken() } returns "test-jwt-token"

        val originalRequest = Request.Builder()
            .url("https://api.fincore.com/api/v1/accounts")
            .build()

        val capturedRequest = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(capturedRequest)) } returns Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .build()

        interceptor.intercept(chain)

        assertEquals("Bearer test-jwt-token", capturedRequest.captured.header("Authorization"))
    }

    @Test
    fun `does not attach bearer token on public auth paths`() {
        coEvery { tokenManager.getAccessToken() } returns "test-jwt-token"

        val originalRequest = Request.Builder()
            .url("https://api.fincore.com/api/v1/auth/login")
            .build()

        val capturedRequest = slot<Request>()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(capturedRequest)) } returns Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .build()

        interceptor.intercept(chain)

        assertNull(capturedRequest.captured.header("Authorization"))
    }
}
