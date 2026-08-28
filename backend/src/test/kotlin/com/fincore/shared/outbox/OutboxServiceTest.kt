package com.fincore.shared.outbox

import com.fincore.shared.event.DomainEventPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class OutboxServiceTest {

    private val outboxEventRepository = mockk<OutboxEventRepository>()
    private val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    private val objectMapper = ObjectMapper()

    private val outboxService = OutboxService(
        outboxEventRepository = outboxEventRepository,
        domainEventPublisher = domainEventPublisher,
        objectMapper = objectMapper
    )

    @Test
    fun `recordEvent creates and saves OutboxEvent in current transaction`() {
        val corrId = UUID.randomUUID()
        val aggId = UUID.randomUUID()
        val actorId = UUID.randomUUID()

        every { outboxEventRepository.save(any()) } answers { firstArg() }

        val saved = outboxService.recordEvent(
            eventType = "TRANSFER_COMPLETED",
            aggregateType = "TRANSACTION",
            aggregateId = aggId,
            actorId = actorId,
            correlationId = corrId,
            payload = mapOf("amount" to "100.0000")
        )

        assertNotNull(saved.id)
        assertEquals("TRANSFER_COMPLETED", saved.eventType)
        assertEquals(OutboxStatus.PENDING, saved.status)
        verify(exactly = 1) { outboxEventRepository.save(any()) }
    }

    @Test
    fun `relayPendingEvents publishes events and marks them PUBLISHED`() {
        val event = OutboxEvent(
            eventType = "TRANSFER_COMPLETED",
            aggregateType = "TRANSACTION",
            aggregateId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            payload = """{"amount":"50.0000"}"""
        )

        every {
            outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 50))
        } returns listOf(event)

        every { outboxEventRepository.save(any()) } answers { firstArg() }

        val count = outboxService.relayPendingEvents(50)

        assertEquals(1, count)
        assertEquals(OutboxStatus.PUBLISHED, event.status)
        assertNotNull(event.publishedAt)
        verify(exactly = 1) { domainEventPublisher.publish(match { it.eventId == event.id }) }
        verify(exactly = 1) { outboxEventRepository.save(event) }
    }
}
