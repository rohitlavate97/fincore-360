package com.fincore.notifications.application

import com.fincore.accounts.application.AccountService
import com.fincore.accounts.application.AccountView
import com.fincore.notifications.domain.Notification
import com.fincore.notifications.domain.NotificationStatus
import com.fincore.notifications.domain.NotificationType
import com.fincore.notifications.infrastructure.NotificationRepository
import com.fincore.shared.event.DomainEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Optional
import java.util.UUID

class NotificationServiceTest {

    private val notificationRepository = mockk<NotificationRepository>()
    private val notificationService = NotificationService(notificationRepository)

    @Test
    fun `createNotification saves notification with UNREAD status`() {
        val custId = UUID.randomUUID()
        every { notificationRepository.save(any()) } answers { firstArg() }

        val created = notificationService.createNotification(
            customerId = custId,
            title = "Test Title",
            body = "Test Body",
            type = NotificationType.TRANSACTION_ALERT,
            deepLinkUri = "fincore://transactions/abc"
        )

        assertNotNull(created.id)
        assertEquals(NotificationStatus.UNREAD, created.status)
        assertEquals("fincore://transactions/abc", created.deepLinkUri)
        verify(exactly = 1) { notificationRepository.save(any()) }
    }

    @Test
    fun `markAsRead transitions status and sets readAt`() {
        val custId = UUID.randomUUID()
        val notif = Notification(
            customerId = custId,
            title = "Title",
            body = "Body",
            type = NotificationType.SYSTEM,
            deepLinkUri = null
        )

        every { notificationRepository.findByIdAndCustomerId(notif.id, custId) } returns Optional.of(notif)
        every { notificationRepository.save(any()) } answers { firstArg() }

        val updated = notificationService.markAsRead(notif.id, custId)
        assertEquals(NotificationStatus.READ, updated.status)
        assertNotNull(updated.readAt)
    }
}

class TransactionEventListenerTest {

    private val notificationService = mockk<NotificationService>(relaxed = true)
    private val accountService = mockk<AccountService>()
    private val objectMapper = ObjectMapper()

    private val listener = TransactionEventListener(
        notificationService = notificationService,
        accountService = accountService,
        objectMapper = objectMapper
    )

    @Test
    fun `TRANSFER_COMPLETED event generates notification with deepLinkUri for both sender and recipient`() {
        val txId = UUID.randomUUID()
        val srcAccId = UUID.randomUUID()
        val dstAccId = UUID.randomUUID()
        val srcCustId = UUID.randomUUID()
        val dstCustId = UUID.randomUUID()

        val srcAccount = AccountView(
            id = srcAccId,
            customerId = srcCustId,
            accountNumber = "GB29FINC111",
            accountType = "CHECKING",
            status = "ACTIVE",
            currency = "GBP",
            ledgerBalance = "500.0000",
            availableBalance = "500.0000",
            createdAt = Instant.now()
        )

        val dstAccount = AccountView(
            id = dstAccId,
            customerId = dstCustId,
            accountNumber = "GB29FINC222",
            accountType = "SAVINGS",
            status = "ACTIVE",
            currency = "GBP",
            ledgerBalance = "1000.0000",
            availableBalance = "1000.0000",
            createdAt = Instant.now()
        )

        every { accountService.findAccountById(srcAccId) } returns srcAccount
        every { accountService.findAccountById(dstAccId) } returns dstAccount

        val payload = """
            {
                "transactionId": "$txId",
                "sourceAccountId": "$srcAccId",
                "destinationAccountId": "$dstAccId",
                "amount": "150.0000",
                "currency": "GBP"
            }
        """.trimIndent()

        val event = DomainEvent(
            eventId = UUID.randomUUID(),
            eventType = "TRANSFER_COMPLETED",
            aggregateType = "TRANSACTION",
            aggregateId = txId,
            actorId = UUID.randomUUID(),
            correlationId = UUID.randomUUID(),
            payload = payload
        )

        listener.onDomainEvent(event)

        // Verify recipient notification
        verify(exactly = 1) {
            notificationService.createNotification(
                customerId = dstCustId,
                title = "Money Received",
                body = match { it.contains("150.0000") },
                type = NotificationType.TRANSACTION_ALERT,
                deepLinkUri = "fincore://transactions/$txId"
            )
        }

        // Verify sender notification
        verify(exactly = 1) {
            notificationService.createNotification(
                customerId = srcCustId,
                title = "Transfer Sent",
                body = match { it.contains("150.0000") },
                type = NotificationType.TRANSACTION_ALERT,
                deepLinkUri = "fincore://transactions/$txId"
            )
        }
    }
}
