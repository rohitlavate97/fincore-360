package com.fincore.audit.api.dto

import java.util.UUID

data class AuditEventResponse(
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
    val timestamp: String
)

data class PagedAuditEventResponse(
    val items: List<AuditEventResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean
)
