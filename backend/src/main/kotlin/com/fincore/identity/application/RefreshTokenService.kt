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

        // Check if presented token matches an already rotated previous token (REUSE DETECTION)
        val previousMatch = refreshTokenRepository.findByPreviousTokenHash(tokenHash)
        if (previousMatch.isPresent) {
            val token = previousMatch.get()
            log.warn("Reuse of already rotated refresh token detected for user: {}, device: {}. Revoking entire device token family.", token.userId, token.deviceId)
            token.revokedAt = Instant.now()
            refreshTokenRepository.save(token)
            refreshTokenRepository.revokeAllByUserIdAndDeviceId(token.userId, token.deviceId)
            return RefreshResult.ReuseDetected
        }

        val tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash)
        if (tokenOpt.isEmpty) {
            log.warn("Unknown refresh token presented")
            return RefreshResult.Invalid
        }

        val token = tokenOpt.get()

        // If current token is already revoked, revoke device family
        if (token.isRevoked) {
            log.warn("Revoked refresh token presented for user: {}, device: {}. Revoking entire device token family.", token.userId, token.deviceId)
            refreshTokenRepository.revokeAllByUserIdAndDeviceId(token.userId, token.deviceId)
            return RefreshResult.ReuseDetected
        }

        if (token.isExpired) {
            log.info("Expired refresh token presented for user: {}", token.userId)
            token.revokedAt = Instant.now()
            refreshTokenRepository.save(token)
            return RefreshResult.Expired
        }

        val user = userRepository.findById(token.userId).orElse(null) ?: return RefreshResult.Invalid
        if (!user.isAccountNonLocked()) {
            return RefreshResult.UserLocked
        }

        // Rotate: shift tokenHash to previousTokenHash, issue new tokenHash
        val newRawToken = generateSecureToken()
        token.previousTokenHash = token.tokenHash
        token.tokenHash = TokenHasher.hash(newRawToken)
        token.expiresAt = Instant.now().plus(REFRESH_TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS)
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

    @Transactional
    fun revokeAllForUserAndDevice(userId: UUID, deviceId: String) {
        refreshTokenRepository.revokeAllByUserIdAndDeviceId(userId, deviceId)
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
