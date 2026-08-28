package com.fincore.identity.infrastructure

import com.fincore.identity.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): Optional<RefreshToken>
    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId AND r.deviceId = :deviceId")
    fun revokeAllByUserIdAndDeviceId(userId: UUID, deviceId: String, now: Instant = Instant.now())

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId")
    fun revokeAllByUserId(userId: UUID, now: Instant = Instant.now())
}
