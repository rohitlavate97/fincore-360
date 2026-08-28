package com.fincore.notifications.api

import com.fincore.notifications.api.dto.NotificationResponse
import com.fincore.notifications.api.dto.PagedNotificationResponse
import com.fincore.notifications.api.dto.UnreadCountResponse
import com.fincore.notifications.application.NotificationService
import com.fincore.notifications.domain.Notification
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Customer notification endpoints")
class NotificationController(
    private val notificationService: NotificationService
) {

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get paginated notifications for current customer")
    fun getNotifications(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedNotificationResponse> {
        val customerId = extractCustomerId(jwt)
        val paged = notificationService.getNotifications(customerId, page, size)

        val response = PagedNotificationResponse(
            items = paged.content.map { it.toResponse() },
            page = paged.number,
            size = paged.size,
            totalElements = paged.totalElements,
            totalPages = paged.totalPages,
            hasNext = paged.hasNext()
        )
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Mark a notification as read")
    fun markAsRead(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID
    ): ResponseEntity<NotificationResponse> {
        val customerId = extractCustomerId(jwt)
        val updated = notificationService.markAsRead(id, customerId)
        return ResponseEntity.ok(updated.toResponse())
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get unread notification count for current customer")
    fun getUnreadCount(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<UnreadCountResponse> {
        val customerId = extractCustomerId(jwt)
        val count = notificationService.getUnreadCount(customerId)
        return ResponseEntity.ok(UnreadCountResponse(unreadCount = count))
    }

    private fun extractCustomerId(jwt: Jwt): UUID {
        val customerIdStr = jwt.getClaimAsString("customerId") ?: jwt.subject
        return UUID.fromString(customerIdStr)
    }

    private fun Notification.toResponse(): NotificationResponse = NotificationResponse(
        id = id,
        title = title,
        body = body,
        type = type.name,
        deepLinkUri = deepLinkUri,
        status = status.name,
        createdAt = createdAt.toString(),
        readAt = readAt?.toString()
    )
}
