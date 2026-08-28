package com.fincore.shared.idempotency

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "idempotency_keys")
class IdempotencyKeyRecord(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "key", nullable = false)
    val key: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "endpoint", nullable = false, length = 200)
    val endpoint: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    var state: IdempotencyState = IdempotencyState.IN_PROGRESS,

    @Column(name = "response_status")
    var responseStatus: Int? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    var responseBody: String? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
