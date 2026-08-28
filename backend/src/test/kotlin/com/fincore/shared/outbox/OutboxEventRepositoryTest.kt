package com.fincore.shared.outbox

import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@Transactional
class OutboxEventRepositoryTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @Test
    fun `can persist outbox event with JSONB payload and query pending`() {
        val event = OutboxEvent(
            eventType = "TRANSFER_COMPLETED",
            aggregateType = "TRANSACTION",
            aggregateId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            payload = """{"amount":"100.0000","currency":"GBP"}"""
        )

        val saved = outboxEventRepository.save(event)
        assertNotNull(saved.id)
        assertEquals(OutboxStatus.PENDING, saved.status)

        val pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, 10))
        assertNotNull(pending.find { it.id == saved.id })

        saved.markPublished()
        outboxEventRepository.save(saved)

        val updated = outboxEventRepository.findById(saved.id).get()
        assertEquals(OutboxStatus.PUBLISHED, updated.status)
        assertNotNull(updated.publishedAt)
    }
}
