package com.fincore.feature.auth.domain.usecase

import com.fincore.feature.auth.data.remote.dto.AuthResponseDto
import com.fincore.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        username: String,
        password: String,
        deviceId: String = "android-device-primary"
    ): Result<AuthResponseDto> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username or email cannot be empty"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password cannot be empty"))
        }
        return authRepository.login(username.trim(), password, deviceId)
    }
}
