package com.fincore.shared.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {

    @Query(
        value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun claimPendingBatch(@Param("batchSize") batchSize: Int): List<OutboxEvent>

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC")
    fun findByStatusOrderByCreatedAtAsc(status: OutboxStatus, pageable: Pageable): List<OutboxEvent>

    fun findByCorrelationIdOrderByCreatedAtAsc(correlationId: UUID): List<OutboxEvent>

    fun countByStatus(status: OutboxStatus): Long
}
