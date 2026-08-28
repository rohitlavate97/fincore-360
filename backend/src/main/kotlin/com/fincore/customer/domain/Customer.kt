package com.fincore.customer.domain

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
@Table(name = "customers")
class Customer(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 320)
    var email: String,

    @Column(name = "full_name", nullable = false, length = 200)
    var fullName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CustomerStatus = CustomerStatus.ACTIVE,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

enum class CustomerStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
