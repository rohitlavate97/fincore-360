package com.fincore.shared.outbox

import com.fincore.shared.event.DomainEvent
import com.fincore.shared.event.DomainEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class OutboxService(
    private val outboxEventRepository: OutboxEventRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.MANDATORY)
    fun recordEvent(
        eventType: String,
        aggregateType: String,
        aggregateId: UUID,
        actorId: UUID?,
        correlationId: UUID,
        payload: Any
    ): OutboxEvent {
        val payloadJson = if (payload is String) payload else objectMapper.writeValueAsString(payload)

        val event = OutboxEvent(
            eventType = eventType,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            actorId = actorId,
            correlationId = correlationId,
            payload = payloadJson
        )

        return outboxEventRepository.save(event)
    }

    @Transactional
    fun relayPendingEvents(batchSize: Int = 50): Int {
        val pending = outboxEventRepository.claimPendingBatch(batchSize)

        if (pending.isEmpty()) return 0

        var publishedCount = 0
        for (event in pending) {
            try {
                val domainEvent = DomainEvent(
                    eventId = event.id,
                    eventType = event.eventType,
                    aggregateType = event.aggregateType,
                    aggregateId = event.aggregateId,
                    actorId = event.actorId,
                    correlationId = event.correlationId,
                    timestamp = event.createdAt,
                    payload = event.payload
                )

                domainEventPublisher.publish(domainEvent)
                event.markPublished()
                outboxEventRepository.save(event)
                publishedCount++
            } catch (e: Exception) {
                log.error("Failed to relay outbox event id={}: {}", event.id, e.message, e)
                event.markFailed()
                outboxEventRepository.save(event)
            }
        }

        return publishedCount
    }
}
