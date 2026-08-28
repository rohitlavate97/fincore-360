package com.fincore.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "device_id", nullable = false, length = 200)
    val deviceId: String,

    @Column(name = "token_hash", nullable = false, length = 255)
    var tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt)
    val isRevoked: Boolean get() = revokedAt != null
    val isValid: Boolean get() = !isExpired && !isRevoked
}
