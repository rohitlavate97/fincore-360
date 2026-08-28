package com.fincore.transactions.application

import com.fincore.accounts.application.AccountService
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.idempotency.IdempotencyResolution
import com.fincore.shared.idempotency.IdempotencyService
import com.fincore.transactions.domain.Transaction
import com.fincore.transactions.domain.TransactionStatus
import com.fincore.transactions.domain.TransactionType
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
    private val accountService: AccountService,
    private val transactionRepository: TransactionRepository,
    private val idempotencyService: IdempotencyService,
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
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
            val replayed = objectMapper.readValue(resolution.body, TransferResult::class.java)
            return replayed.copy(replayed = true)
        }

        val idempotencyRecord = (resolution as IdempotencyResolution.Proceed).record

        val correlationId = CorrelationIdFilter.current()?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

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

        try {
            // 3. Execute balance transfer under deterministic pessimistic lock
            accountService.executeTransferBalances(
                sourceAccountId = command.sourceAccountId,
                destinationAccountId = command.destinationAccountId,
                amount = command.amount,
                currency = command.currency,
                callerCustomerId = command.callerCustomerId
            )

            // 4. Transition to COMPLETED
            transaction.transitionTo(TransactionStatus.COMPLETED)
            transaction = transactionRepository.saveAndFlush(transaction)

            // 5. Audit log
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

            // 6. Complete idempotency record
            idempotencyService.complete(
                recordId = idempotencyRecord.id,
                status = 201,
                responseBody = objectMapper.writeValueAsString(result)
            )

            return result
        } catch (e: Exception) {
            transaction.transitionTo(TransactionStatus.FAILED)
            transactionRepository.saveAndFlush(transaction)

            auditLogRepository.append(
                eventType = "TRANSFER_FAILED",
                actorId = command.callerUserId,
                actorRole = "ROLE_CUSTOMER",
                resourceType = "TRANSACTION",
                resourceId = transaction.id,
                outcome = "FAILURE",
                reason = e.message,
                ipAddress = httpRequest?.remoteAddr,
                userAgent = httpRequest?.getHeader("User-Agent"),
                correlationId = correlationId
            )
            throw e
        }
    }
}
