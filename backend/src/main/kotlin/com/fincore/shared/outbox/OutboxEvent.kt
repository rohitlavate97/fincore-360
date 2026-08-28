package com.fincore.shared.outbox

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

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}

@Entity
@Table(name = "outbox_events")
class OutboxEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "aggregate_type", nullable = false, length = 50)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: UUID,

    @Column(name = "actor_id")
    val actorId: UUID?,

    @Column(name = "correlation_id", nullable = false)
    val correlationId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null
) {
    fun markPublished() {
        this.status = OutboxStatus.PUBLISHED
        this.publishedAt = Instant.now()
    }

    fun markFailed() {
        this.retryCount += 1
        if (this.retryCount >= 3) {
            this.status = OutboxStatus.FAILED
        }
    }
}
