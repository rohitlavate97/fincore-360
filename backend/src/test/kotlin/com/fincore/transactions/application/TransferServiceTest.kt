package com.fincore.transactions.application

import com.fincore.accounts.application.AccountService
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.error.InsufficientFundsException
import com.fincore.shared.idempotency.IdempotencyKeyRecord
import com.fincore.shared.idempotency.IdempotencyResolution
import com.fincore.shared.idempotency.IdempotencyService
import com.fincore.shared.idempotency.IdempotencyState
import com.fincore.shared.outbox.OutboxService
import com.fincore.transactions.domain.Transaction
import com.fincore.transactions.domain.TransactionStatus
import com.fincore.transactions.infrastructure.TransactionRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TransferServiceTest {

    private val transactionRepository = mockk<TransactionRepository>()
    private val accountService = mockk<AccountService>()
    private val idempotencyService = mockk<IdempotencyService>()
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val outboxService = mockk<OutboxService>(relaxed = true)
    private val objectMapper = ObjectMapper()

    private val transferService = TransferService(
        transactionRepository = transactionRepository,
        accountService = accountService,
        idempotencyService = idempotencyService,
        auditLogRepository = auditLogRepository,
        outboxService = outboxService,
        objectMapper = objectMapper
    )

    @Test
    fun `successful transfer executes balances, transitions to COMPLETED, records audit trail and outbox events`() {
        val command = TransferCommand(
            idempotencyKey = UUID.randomUUID(),
            sourceAccountId = UUID.randomUUID(),
            destinationAccountId = UUID.randomUUID(),
            amount = BigDecimal("100.0000"),
            currency = "GBP",
            callerUserId = UUID.randomUUID(),
            callerCustomerId = UUID.randomUUID()
        )

        val record = IdempotencyKeyRecord(
            key = command.idempotencyKey,
            userId = command.callerUserId,
            endpoint = "/api/v1/transfers",
            state = IdempotencyState.IN_PROGRESS,
            expiresAt = Instant.now().plusSeconds(86400)
        )

        every { idempotencyService.startOrResolve(command.idempotencyKey, command.callerUserId, "/api/v1/transfers") } returns
            IdempotencyResolution.Proceed(record)

        every { transactionRepository.saveAndFlush(any()) } answers { firstArg() }
        every { accountService.executeTransferBalances(any(), any(), any(), any(), any()) } returns mockk()
        every { idempotencyService.complete(any(), 201, any()) } returns mockk()

        val result = transferService.executeTransfer(command)

        assertEquals("COMPLETED", result.status)
        assertEquals("100.0000", result.amount)
        assertFalse(result.replayed)

        // Verify initiation -> completion audit trail
        verify(exactly = 1) {
            auditLogRepository.append(eventType = "TRANSFER_INITIATED", any(), any(), any(), any(), any(), any(), any(), any(), any())
            auditLogRepository.append(eventType = "TRANSFER_COMPLETED", any(), any(), any(), any(), any(), any(), any(), any(), any())
        }

        // Verify outbox events recorded inside transaction
        verify(exactly = 1) {
            outboxService.recordEvent(eventType = "TRANSFER_INITIATED", any(), any(), any(), any(), any())
            outboxService.recordEvent(eventType = "TRANSFER_COMPLETED", any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `insufficient funds throws exception, marks transaction FAILED and logs failure audit trail`() {
        val command = TransferCommand(
            idempotencyKey = UUID.randomUUID(),
            sourceAccountId = UUID.randomUUID(),
            destinationAccountId = UUID.randomUUID(),
            amount = BigDecimal("9999.0000"),
            currency = "GBP",
            callerUserId = UUID.randomUUID(),
            callerCustomerId = UUID.randomUUID()
        )

        val record = IdempotencyKeyRecord(
            key = command.idempotencyKey,
            userId = command.callerUserId,
            endpoint = "/api/v1/transfers",
            state = IdempotencyState.IN_PROGRESS,
            expiresAt = Instant.now().plusSeconds(86400)
        )

        every { idempotencyService.startOrResolve(any(), any(), any()) } returns IdempotencyResolution.Proceed(record)
        every { transactionRepository.saveAndFlush(any()) } answers { firstArg() }
        every { accountService.executeTransferBalances(any(), any(), any(), any(), any()) } throws
            InsufficientFundsException("Insufficient funds")

        assertThrows<InsufficientFundsException> {
            transferService.executeTransfer(command)
        }

        verify(exactly = 1) {
            auditLogRepository.append(eventType = "TRANSFER_INITIATED", any(), any(), any(), any(), any(), any(), any(), any(), any())
            auditLogRepository.appendIndependently(eventType = "TRANSFER_FAILED", any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `replayed transfer returns cached response without debiting accounts`() {
        val command = TransferCommand(
            idempotencyKey = UUID.randomUUID(),
            sourceAccountId = UUID.randomUUID(),
            destinationAccountId = UUID.randomUUID(),
            amount = BigDecimal("50.0000"),
            currency = "GBP",
            callerUserId = UUID.randomUUID(),
            callerCustomerId = UUID.randomUUID()
        )

        val cachedResult = TransferResult(
            transactionId = UUID.randomUUID(),
            idempotencyKey = command.idempotencyKey,
            sourceAccountId = command.sourceAccountId,
            destinationAccountId = command.destinationAccountId,
            type = "TRANSFER",
            status = "COMPLETED",
            amount = "50.0000",
            currency = "GBP",
            createdAt = Instant.now().toString(),
            replayed = false
        )

        val cachedJson = objectMapper.writeValueAsString(cachedResult)

        every { idempotencyService.startOrResolve(any(), any(), any()) } returns
            IdempotencyResolution.Replay(201, cachedJson)

        val result = transferService.executeTransfer(command)

        assertTrue(result.replayed)
        assertEquals("50.0000", result.amount)
        verify(exactly = 0) {
            accountService.executeTransferBalances(any(), any(), any(), any(), any())
        }
    }
}
