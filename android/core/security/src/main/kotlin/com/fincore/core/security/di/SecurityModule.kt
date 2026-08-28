package com.fincore.core.security.di

import com.fincore.core.security.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

class InMemoryTokenManager : TokenManager {
    private var accessToken: String? = null
    private var refreshToken: String? = null

    override suspend fun saveAccessToken(token: String) { accessToken = token }
    override suspend fun getAccessToken(): String? = accessToken
    override suspend fun saveRefreshToken(token: String) { refreshToken = token }
    override suspend fun getRefreshToken(): String? = refreshToken
    override suspend fun clearTokens() {
        accessToken = null
        refreshToken = null
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideTokenManager(): TokenManager {
        return InMemoryTokenManager()
    }
}
