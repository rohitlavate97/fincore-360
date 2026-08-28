package com.fincore.audit.api

import com.fincore.audit.api.dto.AuditEventResponse
import com.fincore.audit.api.dto.PagedAuditEventResponse
import com.fincore.shared.audit.AuditEventRecord
import com.fincore.shared.audit.AuditLogRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Query immutable audit trails (Admin only)")
class AuditController(
    private val auditLogRepository: AuditLogRepository
) {

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Query immutable audit events with pagination and filters")
    fun getAuditEvents(
        @RequestParam(required = false) correlationId: UUID?,
        @RequestParam(required = false) resourceId: UUID?,
        @RequestParam(required = false) eventType: String?,
        @RequestParam(required = false) actorId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedAuditEventResponse> {
        val safePage = if (page < 0) 0 else page
        val safeSize = size.coerceIn(1, 100)

        val events = auditLogRepository.findEvents(
            correlationId = correlationId,
            resourceId = resourceId,
            eventType = eventType,
            actorId = actorId,
            page = safePage,
            size = safeSize
        )

        val totalElements = auditLogRepository.countEvents(
            correlationId = correlationId,
            resourceId = resourceId,
            eventType = eventType,
            actorId = actorId
        )

        val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / safeSize + 1).toInt()
        val hasNext = (safePage + 1) * safeSize < totalElements

        val response = PagedAuditEventResponse(
            items = events.map { it.toResponse() },
            page = safePage,
            size = safeSize,
            totalElements = totalElements,
            totalPages = totalPages,
            hasNext = hasNext
        )

        return ResponseEntity.ok(response)
    }

    private fun AuditEventRecord.toResponse(): AuditEventResponse = AuditEventResponse(
        eventId = eventId,
        eventType = eventType,
        actorId = actorId,
        actorRole = actorRole,
        resourceType = resourceType,
        resourceId = resourceId,
        outcome = outcome,
        reason = reason,
        ipAddress = ipAddress,
        userAgent = userAgent,
        correlationId = correlationId,
        timestamp = timestamp.toString()
    )
}
