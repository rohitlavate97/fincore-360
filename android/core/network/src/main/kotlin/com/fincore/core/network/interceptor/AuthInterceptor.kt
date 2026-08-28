package com.fincore.core.network.interceptor

import com.fincore.core.security.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val path = request.url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")) {
            return chain.proceed(request)
        }

        val token = runBlocking { tokenManager.getAccessToken() }
        return if (!token.isNullOrBlank() && request.header("Authorization") == null) {
            val authenticatedRequest = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(request)
        }
    }
}
