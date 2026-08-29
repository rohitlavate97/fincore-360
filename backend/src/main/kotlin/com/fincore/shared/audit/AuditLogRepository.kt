package com.fincore.shared.audit

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AuditEventRecord(
    val eventId: UUID,
    val eventType: String,
    val actorId: UUID?,
    val actorRole: String?,
    val resourceType: String?,
    val resourceId: UUID?,
    val outcome: String,
    val reason: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val correlationId: UUID?,
    val timestamp: Instant
)

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
                resource_id, outcome, reason, ip_address, user_agent,
                correlation_id, timestamp
            ) VALUES (
                :eventId, :eventType, :actorId, :actorRole, :resourceType,
                :resourceId, :outcome, :reason, CAST(:ipAddress AS INET), :userAgent,
                :correlationId, now()
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
            .param("ipAddress", ipAddress)
            .param("userAgent", userAgent?.take(1000))
            .param("correlationId", correlationId)
            .update()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun appendIndependently(
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
        append(
            eventType = eventType,
            actorId = actorId,
            actorRole = actorRole,
            resourceType = resourceType,
            resourceId = resourceId,
            outcome = outcome,
            reason = reason,
            ipAddress = ipAddress,
            userAgent = userAgent,
            correlationId = correlationId
        )
    }

    fun findEvents(
        correlationId: UUID? = null,
        resourceId: UUID? = null,
        eventType: String? = null,
        actorId: UUID? = null,
        page: Int = 0,
        size: Int = 20
    ): List<AuditEventRecord> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        if (correlationId != null) {
            conditions.add("correlation_id = :correlationId")
            params["correlationId"] = correlationId
        }
        if (resourceId != null) {
            conditions.add("resource_id = :resourceId")
            params["resourceId"] = resourceId
        }
        if (eventType != null) {
            conditions.add("event_type = :eventType")
            params["eventType"] = eventType
        }
        if (actorId != null) {
            conditions.add("actor_id = :actorId")
            params["actorId"] = actorId
        }

        val whereClause = if (conditions.isNotEmpty()) "WHERE " + conditions.joinToString(" AND ") else ""
        val offset = (if (page < 0) 0 else page) * size
        val sql = """
            SELECT event_id, event_type, actor_id, actor_role, resource_type,
                   resource_id, outcome, reason, ip_address, user_agent, correlation_id, timestamp
            FROM audit_events
            $whereClause
            ORDER BY timestamp ASC
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        params["limit"] = size.coerceIn(1, 100)
        params["offset"] = offset

        val query = jdbcClient.sql(sql)
        params.forEach { (k, v) -> query.param(k, v) }

        return query.query { rs, _ ->
            AuditEventRecord(
                eventId = rs.getObject("event_id", UUID::class.java),
                eventType = rs.getString("event_type"),
                actorId = rs.getObject("actor_id", UUID::class.java),
                actorRole = rs.getString("actor_role"),
                resourceType = rs.getString("resource_type"),
                resourceId = rs.getObject("resource_id", UUID::class.java),
                outcome = rs.getString("outcome"),
                reason = rs.getString("reason"),
                ipAddress = rs.getString("ip_address"),
                userAgent = rs.getString("user_agent"),
                correlationId = rs.getObject("correlation_id", UUID::class.java),
                timestamp = rs.getTimestamp("timestamp").toInstant()
            )
        }.list()
    }

    fun countEvents(
        correlationId: UUID? = null,
        resourceId: UUID? = null,
        eventType: String? = null,
        actorId: UUID? = null
    ): Long {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        if (correlationId != null) {
            conditions.add("correlation_id = :correlationId")
            params["correlationId"] = correlationId
        }
        if (resourceId != null) {
            conditions.add("resource_id = :resourceId")
            params["resourceId"] = resourceId
        }
        if (eventType != null) {
            conditions.add("event_type = :eventType")
            params["eventType"] = eventType
        }
        if (actorId != null) {
            conditions.add("actor_id = :actorId")
            params["actorId"] = actorId
        }

        val whereClause = if (conditions.isNotEmpty()) "WHERE " + conditions.joinToString(" AND ") else ""
        val sql = "SELECT COUNT(*) FROM audit_events $whereClause"

        val query = jdbcClient.sql(sql)
        params.forEach { (k, v) -> query.param(k, v) }

        return query.query(Long::class.java).single()
    }
}
