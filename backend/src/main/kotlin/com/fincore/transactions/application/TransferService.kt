package com.fincore.transactions.application

import com.fincore.accounts.application.AccountService
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.idempotency.IdempotencyResolution
import com.fincore.shared.idempotency.IdempotencyService
import com.fincore.shared.outbox.OutboxService
import com.fincore.transactions.domain.LedgerDirection
import com.fincore.transactions.domain.LedgerEntry
import com.fincore.transactions.domain.Transaction
import com.fincore.transactions.domain.TransactionStatus
import com.fincore.transactions.domain.TransactionType
import com.fincore.transactions.infrastructure.LedgerEntryRepository
import com.fincore.transactions.infrastructure.TransactionRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Service
class TransferService(
    private val transactionRepository: TransactionRepository,
    private val accountService: AccountService,
    private val idempotencyService: IdempotencyService,
    private val auditLogRepository: AuditLogRepository,
    private val outboxService: OutboxService,
    private val objectMapper: ObjectMapper,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val bankingMetricsService: com.fincore.shared.observability.BankingMetricsService? = null
) {

    @Transactional
    fun executeTransfer(command: TransferCommand, httpRequest: HttpServletRequest? = null): TransferResult {
        val endpoint = "/api/v1/transfers"

        // 1. Idempotency resolution (ADR-010)
        val resolution = idempotencyService.startOrResolve(
            key = command.idempotencyKey,
            userId = command.callerUserId,
            endpoint = endpoint
        )

        if (resolution is IdempotencyResolution.Replay) {
            bankingMetricsService?.recordIdempotencyReplay(endpoint)
            val replayed = objectMapper.readValue(resolution.body, TransferResult::class.java)
            return replayed.copy(replayed = true)
        }

        val idempotencyRecord = (resolution as IdempotencyResolution.Proceed).record

        val correlationId = CorrelationIdFilter.current()?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: UUID.randomUUID()

        bankingMetricsService?.recordTransferInitiated(command.currency)
        val startTime = System.currentTimeMillis()

        // 2. Persist initial transaction as PROCESSING
        var transaction = transactionRepository.saveAndFlush(
            Transaction(
                id = UUID.randomUUID(),
                idempotencyKey = command.idempotencyKey,
                sourceAccountId = command.sourceAccountId,
                destAccountId = command.destinationAccountId,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.PROCESSING,
                amount = command.amount.setScale(4, RoundingMode.UNNECESSARY),
                currency = command.currency.uppercase().trim(),
                createdBy = command.callerUserId,
                correlationId = correlationId,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        // 3. Complete Audit Trail: TRANSFER_INITIATED
        auditLogRepository.append(
            eventType = "TRANSFER_INITIATED",
            actorId = command.callerUserId,
            actorRole = "ROLE_CUSTOMER",
            resourceType = "TRANSACTION",
            resourceId = transaction.id,
            outcome = "SUCCESS",
            reason = "Transfer initiated",
            ipAddress = httpRequest?.remoteAddr,
            userAgent = httpRequest?.getHeader("User-Agent"),
            correlationId = correlationId
        )

        outboxService.recordEvent(
            eventType = "TRANSFER_INITIATED",
            aggregateType = "TRANSACTION",
            aggregateId = transaction.id,
            actorId = command.callerUserId,
            correlationId = correlationId,
            payload = mapOf(
                "transactionId" to transaction.id.toString(),
                "sourceAccountId" to command.sourceAccountId.toString(),
                "destinationAccountId" to command.destinationAccountId.toString(),
                "amount" to command.amount.toPlainString(),
                "currency" to command.currency
            )
        )

        try {
            // 4. Execute balance transfer under deterministic pessimistic lock
            val balanceSummary = accountService.executeTransferBalances(
                sourceAccountId = command.sourceAccountId,
                destinationAccountId = command.destinationAccountId,
                amount = command.amount,
                currency = command.currency,
                callerCustomerId = command.callerCustomerId
            )

            // Double-entry ledger recording (M-7): Write paired DEBIT & CREDIT entries with running balances
            val debitEntry = LedgerEntry(
                transactionId = transaction.id,
                accountId = command.sourceAccountId,
                direction = LedgerDirection.DEBIT,
                amount = transaction.amount,
                runningBalance = java.math.BigDecimal(balanceSummary.sourceRemainingBalance)
            )
            val creditEntry = LedgerEntry(
                transactionId = transaction.id,
                accountId = command.destinationAccountId,
                direction = LedgerDirection.CREDIT,
                amount = transaction.amount,
                runningBalance = java.math.BigDecimal(balanceSummary.destinationRemainingBalance)
            )
            ledgerEntryRepository.saveAll(listOf(debitEntry, creditEntry))

            // 5. Transition to COMPLETED
            transaction.transitionTo(TransactionStatus.COMPLETED)
            transaction = transactionRepository.saveAndFlush(transaction)

            // 6. Complete Audit Trail: TRANSFER_COMPLETED
            auditLogRepository.append(
                eventType = "TRANSFER_COMPLETED",
                actorId = command.callerUserId,
                actorRole = "ROLE_CUSTOMER",
                resourceType = "TRANSACTION",
                resourceId = transaction.id,
                outcome = "SUCCESS",
                reason = null,
                ipAddress = httpRequest?.remoteAddr,
                userAgent = httpRequest?.getHeader("User-Agent"),
                correlationId = correlationId
            )

            outboxService.recordEvent(
                eventType = "TRANSFER_COMPLETED",
                aggregateType = "TRANSACTION",
                aggregateId = transaction.id,
                actorId = command.callerUserId,
                correlationId = correlationId,
                payload = mapOf(
                    "transactionId" to transaction.id.toString(),
                    "sourceAccountId" to command.sourceAccountId.toString(),
                    "destinationAccountId" to command.destinationAccountId.toString(),
                    "amount" to transaction.amount.toPlainString(),
                    "currency" to transaction.currency,
                    "status" to "COMPLETED"
                )
            )

            val result = TransferResult(
                transactionId = transaction.id,
                idempotencyKey = command.idempotencyKey,
                sourceAccountId = command.sourceAccountId,
                destinationAccountId = command.destinationAccountId,
                type = transaction.type.name,
                status = transaction.status.name,
                amount = transaction.amount.toPlainString(),
                currency = transaction.currency,
                createdAt = transaction.createdAt.toString(),
                replayed = false
            )

            // 7. Complete idempotency record
            val bodyJson = objectMapper.writeValueAsString(result)
            idempotencyService.complete(idempotencyRecord.id, 201, bodyJson)

            val durationMillis = System.currentTimeMillis() - startTime
            bankingMetricsService?.recordTransferCompleted(command.currency, durationMillis)

            return result
        } catch (e: Exception) {
            // Failure audit log survives transaction rollback via REQUIRES_NEW
            auditLogRepository.appendIndependently(
                eventType = "TRANSFER_FAILED",
                actorId = command.callerUserId,
                actorRole = "ROLE_CUSTOMER",
                resourceType = "TRANSACTION",
                resourceId = transaction.id,
                outcome = "FAILURE",
                reason = e.message ?: "Transfer failed",
                ipAddress = httpRequest?.remoteAddr,
                userAgent = httpRequest?.getHeader("User-Agent"),
                correlationId = correlationId
            )

            val reason = when (e) {
                is com.fincore.shared.error.InsufficientFundsException -> "INSUFFICIENT_FUNDS"
                is com.fincore.shared.error.AccountNotActiveException -> "ACCOUNT_NOT_ACTIVE"
                is com.fincore.shared.error.ResourceNotFoundException -> "RESOURCE_NOT_FOUND"
                else -> "INTERNAL_ERROR"
            }
            bankingMetricsService?.recordTransferFailed(command.currency, reason)

            runCatching {
                transaction.transitionTo(TransactionStatus.FAILED)
                transactionRepository.saveAndFlush(transaction)
            }
            throw e
        }
    }
}
