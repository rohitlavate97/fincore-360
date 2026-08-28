package com.fincore.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 100)
    var username: String,

    @Column(nullable = false, unique = true, length = 320)
    var email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Column(nullable = false, length = 255)
    var roles: String = "ROLE_CUSTOMER",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "customer_id")
    var customerId: UUID? = null,

    @Column(name = "failed_attempts", nullable = false)
    var failedAttempts: Int = 0,

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun getRoleList(): List<String> = roles.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun isAccountNonLocked(): Boolean {
        if (status == UserStatus.LOCKED) {
            val until = lockedUntil ?: return false
            if (Instant.now().isAfter(until)) {
                return true
            }
            return false
        }
        return status == UserStatus.ACTIVE
    }
}
