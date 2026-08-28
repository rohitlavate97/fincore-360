package com.fincore.identity.application

data class RegisterCommand(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val deviceId: String
)

data class LoginCommand(
    val username: String,
    val password: String,
    val deviceId: String
)

data class RefreshTokenCommand(
    val refreshToken: String,
    val deviceId: String
)

data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 900,
    val userId: String,
    val username: String,
    val roles: List<String>
)
