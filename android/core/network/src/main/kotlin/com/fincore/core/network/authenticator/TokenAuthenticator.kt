package com.fincore.core.network.authenticator

import com.fincore.core.security.TokenManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
    val deviceId: String
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String
)

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val json: Json,
    private val baseUrl: String = "https://api.fincore.com/",
    private val deviceId: String = "android-device-primary"
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/auth/refresh")) {
            runBlocking { tokenManager.clearTokens() }
            return null
        }

        val failedAccessToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        return runBlocking {
            refreshMutex.withLock {
                val currentAccessToken = tokenManager.getAccessToken()

                // Single-flight check: if already refreshed by another concurrent request, retry with new token
                if (currentAccessToken != null && currentAccessToken != failedAccessToken) {
                    return@withLock response.request.newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                }

                val currentRefreshToken = tokenManager.getRefreshToken()
                if (currentRefreshToken.isNullOrBlank()) {
                    tokenManager.clearTokens()
                    return@withLock null
                }

                val refreshSuccess = performTokenRefresh(currentRefreshToken)
                if (refreshSuccess != null) {
                    tokenManager.saveAccessToken(refreshSuccess.accessToken)
                    tokenManager.saveRefreshToken(refreshSuccess.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshSuccess.accessToken}")
                        .build()
                } else {
                    tokenManager.clearTokens()
                    null
                }
            }
        }
    }

    private fun performTokenRefresh(refreshToken: String): AuthResponseDto? {
        return runCatching {
            val client = OkHttpClient()
            val requestBody = json.encodeToString(
                RefreshTokenRequestDto.serializer(),
                RefreshTokenRequestDto(refreshToken, deviceId)
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/v1/auth/refresh")
                .post(requestBody)
                .build()

            val refreshResponse = client.newCall(request).execute()
            if (refreshResponse.isSuccessful) {
                val bodyString = refreshResponse.body?.string() ?: return null
                json.decodeFromString(AuthResponseDto.serializer(), bodyString)
            } else {
                null
            }
        }.getOrNull()
    }
}
