package com.fincore.feature.auth.data.repository

import com.fincore.core.security.TokenManager
import com.fincore.feature.auth.data.remote.AuthApi
import com.fincore.feature.auth.data.remote.dto.AuthResponseDto
import com.fincore.feature.auth.data.remote.dto.LoginRequestDto
import com.fincore.feature.auth.data.remote.dto.LogoutRequestDto
import com.fincore.feature.auth.data.remote.dto.RegisterRequestDto
import com.fincore.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
) : AuthRepository {

    override suspend fun login(
        username: String,
        password: String,
        deviceId: String
    ): Result<AuthResponseDto> = runCatching {
        val response = authApi.login(LoginRequestDto(username, password, deviceId))
        tokenManager.saveAccessToken(response.accessToken)
        tokenManager.saveRefreshToken(response.refreshToken)
        response
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String,
        fullName: String,
        deviceId: String
    ): Result<AuthResponseDto> = runCatching {
        val response = authApi.register(RegisterRequestDto(username, email, password, fullName, deviceId))
        tokenManager.saveAccessToken(response.accessToken)
        tokenManager.saveRefreshToken(response.refreshToken)
        response
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        val refreshToken = tokenManager.getRefreshToken()
        if (!refreshToken.isNullOrBlank()) {
            runCatching { authApi.logout(LogoutRequestDto(refreshToken)) }
        }
        tokenManager.clearTokens()
    }

    override suspend fun isAuthenticated(): Boolean {
        return !tokenManager.getAccessToken().isNullOrBlank()
    }
}
