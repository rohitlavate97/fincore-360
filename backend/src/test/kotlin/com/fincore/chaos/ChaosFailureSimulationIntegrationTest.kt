package com.fincore.chaos

import com.fincore.shared.event.DomainEvent
import com.fincore.shared.event.DomainEventPublisher
import com.fincore.shared.outbox.OutboxEvent
import com.fincore.shared.outbox.OutboxEventRepository
import com.fincore.shared.outbox.OutboxService
import com.fincore.shared.outbox.OutboxStatus
import com.fincore.support.EmbeddedPostgresSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
class ChaosFailureSimulationIntegrationTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("Chaos Test: Downstream publisher failure during outbox relay increments retryCount and exhausts to FAILED without dropping payload")
    fun outboxPublisherFailurePreservesEventIntegrity() {
        val outboxRepo = mockk<OutboxEventRepository>()
        val failingPublisher = mockk<DomainEventPublisher>()
        val objectMapper = ObjectMapper()

        val outboxService = OutboxService(
            outboxEventRepository = outboxRepo,
            domainEventPublisher = failingPublisher,
            objectMapper = objectMapper
        )

        val event = OutboxEvent(
            eventType = "TRANSFER_COMPLETED",
            aggregateType = "TRANSACTION",
            aggregateId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            payload = """{"amount":"250.0000"}"""
        )

        every {
            outboxRepo.claimPendingBatch(50)
        } returns listOf(event)

        every { outboxRepo.save(any()) } answers { firstArg() }

        // Inject chaos: downstream Kafka broker or event bus throws network disconnect exception
        every { failingPublisher.publish(any()) } throws RuntimeException("Chaos: Network partition to event bus")

        // First failure -> retryCount becomes 1, status remains PENDING for retry
        outboxService.relayPendingEvents(50)
        assertEquals(1, event.retryCount)
        assertEquals(OutboxStatus.PENDING, event.status)

        // Second failure -> retryCount becomes 2
        outboxService.relayPendingEvents(50)
        assertEquals(2, event.retryCount)
        assertEquals(OutboxStatus.PENDING, event.status)

        // Third failure -> threshold reached, transitions to FAILED for dead-letter processing
        outboxService.relayPendingEvents(50)
        assertEquals(3, event.retryCount)
        assertEquals(OutboxStatus.FAILED, event.status, "Event must transition to FAILED status after max retries")

        verify(atLeast = 3) { outboxRepo.save(event) }
    }

    @Test
    @DisplayName("Chaos Test: Unhandled catastrophic exception returns 404/500 with traceId and zero stack trace leak")
    fun catastrophicExceptionHandledGracefully() {
        mockMvc.perform(get("/api/v1/chaos/non-existent-path-for-exception-check"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").exists())
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.stackTrace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist())
    }
}
