package com.fincore.identity.application

import com.fincore.identity.domain.RefreshToken
import com.fincore.identity.infrastructure.RefreshTokenRepository
import com.fincore.identity.infrastructure.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        const val REFRESH_TOKEN_EXPIRY_DAYS = 7L
        const val ABSOLUTE_SESSION_LIFETIME_DAYS = 30L
    }

    @Transactional
    fun createRefreshToken(userId: UUID, deviceId: String): String {
        val rawToken = generateSecureToken()
        val tokenHash = TokenHasher.hash(rawToken)
        val expiresAt = Instant.now().plus(REFRESH_TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS)

        val existing = refreshTokenRepository.findByUserIdAndDeviceId(userId, deviceId)
        if (existing.isPresent) {
            val token = existing.get()
            token.tokenHash = tokenHash
            token.previousTokenHash = null
            token.expiresAt = expiresAt
            token.revokedAt = null
            refreshTokenRepository.save(token)
        } else {
            val token = RefreshToken(
                userId = userId,
                deviceId = deviceId,
                tokenHash = tokenHash,
                expiresAt = expiresAt
            )
            refreshTokenRepository.save(token)
        }

        return rawToken
    }

    @Transactional
    fun rotateRefreshToken(rawToken: String, deviceId: String): RefreshResult {
        val tokenHash = TokenHasher.hash(rawToken)

        // Check if presented token matches an already rotated previous token (REUSE DETECTION - M-2)
        val previousMatch = refreshTokenRepository.findByPreviousTokenHash(tokenHash)
        if (previousMatch.isPresent) {
            val token = previousMatch.get()
            log.warn("Reuse of already rotated refresh token detected for user: {}, device: {}. Revoking ALL active sessions across all devices for account (M-2).", token.userId, token.deviceId)
            token.revokedAt = Instant.now()
            refreshTokenRepository.save(token)
            refreshTokenRepository.revokeAllByUserId(token.userId)
            return RefreshResult.ReuseDetected
        }

        val tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash)
        if (tokenOpt.isEmpty) {
            log.warn("Unknown refresh token presented")
            return RefreshResult.Invalid
        }

        val token = tokenOpt.get()

        // If current token is already revoked, revoke all user sessions (M-2)
        if (token.isRevoked) {
            log.warn("Revoked refresh token presented for user: {}, device: {}. Revoking ALL active sessions across all devices for account (M-2).", token.userId, token.deviceId)
            refreshTokenRepository.revokeAllByUserId(token.userId)
            return RefreshResult.ReuseDetected
        }

        // M-1: Absolute lifetime check
        val maxAbsoluteExpiry = token.createdAt.plus(ABSOLUTE_SESSION_LIFETIME_DAYS, ChronoUnit.DAYS)
        if (Instant.now().isAfter(maxAbsoluteExpiry) || token.isExpired) {
            log.info("Refresh token expired or exceeded absolute session lifetime for user: {}", token.userId)
            token.revokedAt = Instant.now()
            refreshTokenRepository.save(token)
            return RefreshResult.Expired
        }

        val user = userRepository.findById(token.userId).orElse(null) ?: return RefreshResult.Invalid
        if (!user.isAccountNonLocked()) {
            return RefreshResult.UserLocked
        }

        // Rotate: shift tokenHash to previousTokenHash, issue new tokenHash with bounded sliding window (M-1)
        val newRawToken = generateSecureToken()
        token.previousTokenHash = token.tokenHash
        token.tokenHash = TokenHasher.hash(newRawToken)
        val nextSlidingExpiry = Instant.now().plus(REFRESH_TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS)
        token.expiresAt = if (nextSlidingExpiry.isBefore(maxAbsoluteExpiry)) nextSlidingExpiry else maxAbsoluteExpiry
        refreshTokenRepository.save(token)

        return RefreshResult.Success(newRawToken, user)
    }

    @Transactional
    fun revokeToken(rawToken: String) {
        val tokenHash = TokenHasher.hash(rawToken)
        val token = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null) ?: return
        token.revokedAt = Instant.now()
        refreshTokenRepository.save(token)
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
