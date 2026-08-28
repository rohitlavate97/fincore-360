package com.fincore.shared.event

import java.time.Instant
import java.util.UUID

data class DomainEvent(
    val eventId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: UUID,
    val actorId: UUID?,
    val correlationId: UUID,
    val timestamp: Instant = Instant.now(),
    val payload: String
)

interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
