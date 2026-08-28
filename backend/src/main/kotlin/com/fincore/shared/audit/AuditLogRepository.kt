package com.fincore.shared.audit

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AuditLogRepository(
    private val jdbcClient: JdbcClient
) {
    fun append(
        eventType: String,
        actorId: UUID?,
        actorRole: String?,
        resourceType: String?,
        resourceId: UUID?,
        outcome: String,
        reason: String?,
        ipAddress: String?,
        userAgent: String?,
        correlationId: UUID?
    ) {
        val sql = """
            INSERT INTO audit_events (
                event_id, event_type, actor_id, actor_role, resource_type,
                resource_id, outcome, reason, correlation_id, timestamp
            ) VALUES (
                :eventId, :eventType, :actorId, :actorRole, :resourceType,
                :resourceId, :outcome, :reason, :correlationId, now()
            )
        """.trimIndent()

        jdbcClient.sql(sql)
            .param("eventId", UUID.randomUUID())
            .param("eventType", eventType)
            .param("actorId", actorId)
            .param("actorRole", actorRole)
            .param("resourceType", resourceType)
            .param("resourceId", resourceId)
            .param("outcome", outcome)
            .param("reason", reason)
            .param("correlationId", correlationId)
            .update()
    }
}
