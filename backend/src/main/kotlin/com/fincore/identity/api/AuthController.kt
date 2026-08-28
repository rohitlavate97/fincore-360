package com.fincore.identity.api

import com.fincore.identity.api.dto.AuthResponse
import com.fincore.identity.api.dto.LoginRequest
import com.fincore.identity.api.dto.LogoutRequest
import com.fincore.identity.api.dto.RefreshTokenRequest
import com.fincore.identity.api.dto.RegisterRequest
import com.fincore.identity.application.AuthResult
import com.fincore.identity.application.AuthService
import com.fincore.identity.application.LoginCommand
import com.fincore.identity.application.RefreshTokenCommand
import com.fincore.identity.application.RegisterCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration, login, token refresh, and logout")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register new customer account")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val command = RegisterCommand(
            username = request.username,
            email = request.email,
            password = request.password,
            fullName = request.fullName,
            deviceId = request.deviceId
        )
        val result = authService.register(command, httpRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(result.toResponse())
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue tokens")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val command = LoginCommand(
            username = request.username,
            password = request.password,
            deviceId = request.deviceId
        )
        val result = authService.login(command, httpRequest)
        return ResponseEntity.ok(result.toResponse())
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue new access token")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AuthResponse> {
        val command = RefreshTokenCommand(
            refreshToken = request.refreshToken,
            deviceId = request.deviceId
        )
        val result = authService.refresh(command, httpRequest)
        return ResponseEntity.ok(result.toResponse())
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke refresh token and terminate session")
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<Unit> {
        authService.logout(request.refreshToken, httpRequest)
        return ResponseEntity.noContent().build()
    }

    private fun AuthResult.toResponse(): AuthResponse = AuthResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        expiresIn = expiresIn,
        userId = userId,
        username = username,
        roles = roles
    )
}
