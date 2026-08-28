package com.fincore.notifications.application

import com.fincore.accounts.application.AccountService
import com.fincore.notifications.domain.NotificationType
import com.fincore.shared.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class TransactionEventListener(
    private val notificationService: NotificationService,
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onDomainEvent(event: DomainEvent) {
        if (event.eventType != "TRANSFER_COMPLETED") return

        try {
            val rootNode = objectMapper.readTree(event.payload)
            val transactionId = rootNode.get("transactionId")?.asText() ?: event.aggregateId.toString()
            val sourceAccountIdStr = rootNode.get("sourceAccountId")?.asText()
            val destAccountIdStr = rootNode.get("destinationAccountId")?.asText()
            val amount = rootNode.get("amount")?.asText() ?: "0.0000"
            val currency = rootNode.get("currency")?.asText() ?: "GBP"

            val deepLinkUri = "fincore://transactions/$transactionId"

            // 1. Notify Destination Customer (Money Received)
            if (!destAccountIdStr.isNullOrBlank()) {
                val destAccountId = UUID.fromString(destAccountIdStr)
                val destAccount = accountService.findAccountById(destAccountId)
                if (destAccount != null) {
                    notificationService.createNotification(
                        customerId = destAccount.customerId,
                        title = "Money Received",
                        body = "You received $currency $amount in account ${destAccount.accountNumber}",
                        type = NotificationType.TRANSACTION_ALERT,
                        deepLinkUri = deepLinkUri
                    )
                }
            }

            // 2. Notify Source Customer (Money Sent)
            if (!sourceAccountIdStr.isNullOrBlank()) {
                val sourceAccountId = UUID.fromString(sourceAccountIdStr)
                val sourceAccount = accountService.findAccountById(sourceAccountId)
                if (sourceAccount != null) {
                    notificationService.createNotification(
                        customerId = sourceAccount.customerId,
                        title = "Transfer Sent",
                        body = "You sent $currency $amount from account ${sourceAccount.accountNumber}",
                        type = NotificationType.TRANSACTION_ALERT,
                        deepLinkUri = deepLinkUri
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Failed to process notification for event ${event.eventId}: ${e.message}", e)
        }
    }
}
