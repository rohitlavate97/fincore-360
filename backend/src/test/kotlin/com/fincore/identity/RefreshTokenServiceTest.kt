package com.fincore.identity

import com.fincore.identity.application.RefreshResult
import com.fincore.identity.application.RefreshTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.RefreshTokenRepository
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@Transactional
class RefreshTokenServiceTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var refreshTokenService: RefreshTokenService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Test
    fun `refresh token lifecycle rotate and reuse detection`() {
        val user = userRepository.save(
            User(
                id = UUID.randomUUID(),
                username = "refresh_user_${UUID.randomUUID()}",
                email = "refresh_${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE
            )
        )

        val deviceId = "device-pixel-8"

        // 1. Initial generation
        val token1 = refreshTokenService.createRefreshToken(user.id, deviceId)
        assertNotNull(token1)

        // 2. Successful rotation
        val result1 = refreshTokenService.rotateRefreshToken(token1, deviceId)
        assertTrue(result1 is RefreshResult.Success)
        val success1 = result1 as RefreshResult.Success
        val token2 = success1.newRefreshToken
        assertNotNull(token2)
        assertEquals(user.id, success1.user.id)

        // 3. REUSE DETECTION: presenting token1 again must trigger reuse detection (ADR-013)
        val resultReuse = refreshTokenService.rotateRefreshToken(token1, deviceId)
        assertEquals(RefreshResult.ReuseDetected, resultReuse)

        // 4. Token family revoked: token2 should now also be revoked
        val result2 = refreshTokenService.rotateRefreshToken(token2, deviceId)
        assertEquals(RefreshResult.ReuseDetected, result2)
    }

    @Test
    fun `revocation explicitly invalidates token`() {
        val user = userRepository.save(
            User(
                id = UUID.randomUUID(),
                username = "revoke_user_${UUID.randomUUID()}",
                email = "revoke_${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE
            )
        )

        val deviceId = "device-ios"
        val token = refreshTokenService.createRefreshToken(user.id, deviceId)

        refreshTokenService.revokeToken(token)

        val result = refreshTokenService.rotateRefreshToken(token, deviceId)
        assertEquals(RefreshResult.ReuseDetected, result)
    }

    @Test
    @org.junit.jupiter.api.DisplayName("M-2: Token reuse on device A revokes all sessions across all devices for the account")
    fun tokenReuseOnDeviceARevokesAllSessionsAcrossAllDevices() {
        val user = userRepository.save(
            User(
                id = UUID.randomUUID(),
                username = "multi_device_user_${UUID.randomUUID()}",
                email = "multidev_${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE
            )
        )

        // Session on Laptop
        val tokenLaptop1 = refreshTokenService.createRefreshToken(user.id, "device-laptop")
        val rotateLaptopResult = refreshTokenService.rotateRefreshToken(tokenLaptop1, "device-laptop")
        assertTrue(rotateLaptopResult is RefreshResult.Success)

        // Session on Phone
        val tokenPhone = refreshTokenService.createRefreshToken(user.id, "device-phone")
        assertNotNull(tokenPhone)

        // Replaying compromised tokenLaptop1 triggers token theft reuse detection
        val reuseResult = refreshTokenService.rotateRefreshToken(tokenLaptop1, "device-laptop")
        assertEquals(RefreshResult.ReuseDetected, reuseResult)

        // Phone session must also be revoked as account-level kill switch (M-2)
        val phoneRotateResult = refreshTokenService.rotateRefreshToken(tokenPhone, "device-phone")
        assertEquals(RefreshResult.ReuseDetected, phoneRotateResult)
    }

    @Test
    @org.junit.jupiter.api.DisplayName("M-1: Absolute session lifetime expires and cannot be refreshed indefinitely")
    fun absoluteSessionLifetimeExpiresAndCannotBeRefreshedIndefinitely() {
        val user = userRepository.save(
            User(
                id = UUID.randomUUID(),
                username = "absolute_expiry_user_${UUID.randomUUID()}",
                email = "abs_exp_${UUID.randomUUID()}@example.com",
                passwordHash = "hash",
                roles = Role.CUSTOMER.authority,
                status = UserStatus.ACTIVE
            )
        )

        val deviceId = "device-tablet"
        val token = refreshTokenService.createRefreshToken(user.id, deviceId)

        // Fast-forward token creation to 31 days ago (past 30-day absolute lifetime)
        val stored = refreshTokenRepository.findByUserIdAndDeviceId(user.id, deviceId).get()
        stored.expiresAt = java.time.Instant.now().minus(java.time.Duration.ofDays(1))
        refreshTokenRepository.save(stored)

        val result = refreshTokenService.rotateRefreshToken(token, deviceId)
        assertEquals(RefreshResult.Expired, result)
    }
}
