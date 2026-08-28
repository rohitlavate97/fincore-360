package com.fincore.feature.auth.domain.repository

import com.fincore.feature.auth.data.remote.dto.AuthResponseDto

interface AuthRepository {
    suspend fun login(username: String, password: String, deviceId: String): Result<AuthResponseDto>
    suspend fun register(username: String, email: String, password: String, fullName: String, deviceId: String): Result<AuthResponseDto>
    suspend fun logout(): Result<Unit>
    suspend fun isAuthenticated(): Boolean
}
