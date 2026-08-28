package com.fincore.identity.application

import com.fincore.customer.application.CustomerService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.error.AuthenticationFailedException
import com.fincore.shared.error.ConflictException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val customerService: CustomerService,
    private val jwtTokenService: JwtTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val passwordEncoder: PasswordEncoder,
    private val auditLogRepository: AuditLogRepository
) {
    companion object {
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCK_DURATION_MINUTES = 15L
    }

    @Transactional
    fun register(command: RegisterCommand, httpRequest: HttpServletRequest): AuthResult {
        if (userRepository.existsByUsername(command.username)) {
            throw ConflictException("Username already taken")
        }
        if (userRepository.existsByEmail(command.email)) {
            throw ConflictException("Email already registered")
        }

        val customer = customerService.createCustomer(
            email = command.email,
            fullName = command.fullName
        )

        val user = userRepository.save(
            User(
                username = command.username,
                email = command.email,
                passwordHash = passwordEncoder.encode(command.password) ?: "",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE,
                customerId = customer.id
            )
        )

        val accessToken = jwtTokenService.createAccessToken(user)
        val refreshToken = refreshTokenService.createRefreshToken(user.id, command.deviceId)

        auditLog(
            eventType = "USER_REGISTER",
            actorId = user.id,
            actorRole = Role.CUSTOMER.authority,
            resourceType = "USER",
            resourceId = user.id,
            outcome = "SUCCESS",
            reason = null,
            httpRequest = httpRequest
        )

        return AuthResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id.toString(),
            username = user.username,
            roles = user.getRoleList()
        )
    }

    @Transactional(noRollbackFor = [AuthenticationFailedException::class])
    fun login(command: LoginCommand, httpRequest: HttpServletRequest): AuthResult {
        val user = userRepository.findByUsername(command.username).orElseGet {
            userRepository.findByEmail(command.username).orElse(null)
        }

        if (user == null) {
            auditLog(
                eventType = "USER_LOGIN",
                actorId = null,
                actorRole = null,
                resourceType = "USER",
                resourceId = null,
                outcome = "FAILURE",
                reason = "USER_NOT_FOUND",
                httpRequest = httpRequest
            )
            throw AuthenticationFailedException("Invalid username or password")
        }

        if (!user.isAccountNonLocked()) {
            auditLog(
                eventType = "USER_LOGIN",
                actorId = user.id,
                actorRole = user.roles,
                resourceType = "USER",
                resourceId = user.id,
                outcome = "FAILURE",
                reason = "ACCOUNT_LOCKED",
                httpRequest = httpRequest
            )
            throw AuthenticationFailedException("Account is locked due to multiple failed login attempts. Try again later.")
        }

        if (!passwordEncoder.matches(command.password, user.passwordHash)) {
            user.failedAttempts += 1
            if (user.failedAttempts >= MAX_FAILED_ATTEMPTS) {
                user.status = UserStatus.LOCKED
                user.lockedUntil = Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES)
            }
            userRepository.save(user)

            auditLog(
                eventType = "USER_LOGIN",
                actorId = user.id,
                actorRole = user.roles,
                resourceType = "USER",
                resourceId = user.id,
                outcome = "FAILURE",
                reason = "INVALID_CREDENTIALS",
                httpRequest = httpRequest
            )

            throw AuthenticationFailedException("Invalid username or password")
        }

        user.failedAttempts = 0
        user.status = UserStatus.ACTIVE
        user.lockedUntil = null
        userRepository.save(user)

        val accessToken = jwtTokenService.createAccessToken(user)
        val refreshToken = refreshTokenService.createRefreshToken(user.id, command.deviceId)

        auditLog(
            eventType = "USER_LOGIN",
            actorId = user.id,
            actorRole = user.roles,
            resourceType = "USER",
            resourceId = user.id,
            outcome = "SUCCESS",
            reason = null,
            httpRequest = httpRequest
        )

        return AuthResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id.toString(),
            username = user.username,
            roles = user.getRoleList()
        )
    }

    @Transactional(noRollbackFor = [AuthenticationFailedException::class])
    fun refresh(command: RefreshTokenCommand, httpRequest: HttpServletRequest): AuthResult {
        when (val result = refreshTokenService.rotateRefreshToken(command.refreshToken, command.deviceId)) {
            is RefreshResult.Success -> {
                val accessToken = jwtTokenService.createAccessToken(result.user)
                auditLog(
                    eventType = "TOKEN_REFRESH",
                    actorId = result.user.id,
                    actorRole = result.user.roles,
                    resourceType = "REFRESH_TOKEN",
                    resourceId = result.user.id,
                    outcome = "SUCCESS",
                    reason = null,
                    httpRequest = httpRequest
                )
                return AuthResult(
                    accessToken = accessToken,
                    refreshToken = result.newRefreshToken,
                    userId = result.user.id.toString(),
                    username = result.user.username,
                    roles = result.user.getRoleList()
                )
            }
            is RefreshResult.ReuseDetected -> {
                auditLog(
                    eventType = "TOKEN_REFRESH",
                    actorId = null,
                    actorRole = null,
                    resourceType = "REFRESH_TOKEN",
                    resourceId = null,
                    outcome = "FAILURE",
                    reason = "REUSE_DETECTED",
                    httpRequest = httpRequest
                )
                throw AuthenticationFailedException("Refresh token reuse detected. All sessions on this device have been revoked.")
            }
            is RefreshResult.Expired -> {
                throw AuthenticationFailedException("Refresh token expired. Please log in again.")
            }
            is RefreshResult.UserLocked -> {
                throw AuthenticationFailedException("User account is locked.")
            }
            is RefreshResult.Invalid -> {
                throw AuthenticationFailedException("Invalid refresh token.")
            }
        }
    }

    @Transactional
    fun logout(refreshToken: String, httpRequest: HttpServletRequest) {
        refreshTokenService.revokeToken(refreshToken)
        auditLog(
            eventType = "USER_LOGOUT",
            actorId = null,
            actorRole = null,
            resourceType = "REFRESH_TOKEN",
            resourceId = null,
            outcome = "SUCCESS",
            reason = null,
            httpRequest = httpRequest
        )
    }

    private fun auditLog(
        eventType: String,
        actorId: UUID?,
        actorRole: String?,
        resourceType: String?,
        resourceId: UUID?,
        outcome: String,
        reason: String?,
        httpRequest: HttpServletRequest
    ) {
        val corrIdStr = CorrelationIdFilter.current() ?: httpRequest.getHeader(CorrelationIdFilter.HEADER)
        val correlationId = runCatching { UUID.fromString(corrIdStr) }.getOrNull()

        auditLogRepository.append(
            eventType = eventType,
            actorId = actorId,
            actorRole = actorRole,
            resourceType = resourceType,
            resourceId = resourceId,
            outcome = outcome,
            reason = reason,
            ipAddress = httpRequest.remoteAddr,
            userAgent = httpRequest.getHeader("User-Agent"),
            correlationId = correlationId
        )
    }
}
