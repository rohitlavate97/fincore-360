package com.fincore.core.security

interface TokenManager {
    // Implementation will use Android Keystore in Phase 3.
    suspend fun saveAccessToken(token: String)
    suspend fun getAccessToken(): String?
    suspend fun saveRefreshToken(token: String)
    suspend fun getRefreshToken(): String?
    suspend fun clearTokens()
}
