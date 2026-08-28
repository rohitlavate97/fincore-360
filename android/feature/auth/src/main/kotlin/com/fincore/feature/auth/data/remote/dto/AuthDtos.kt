package com.fincore.feature.auth.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    val deviceId: String
)

@Serializable
data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val deviceId: String
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900,
    val userId: String,
    val username: String,
    val roles: List<String>
)
