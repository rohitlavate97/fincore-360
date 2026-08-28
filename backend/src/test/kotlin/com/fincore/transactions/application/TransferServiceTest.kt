package com.fincore.transactions.application

import com.fincore.accounts.application.AccountService
import com.fincore.accounts.application.TransferAccountsSummary
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.error.InsufficientFundsException
import com.fincore.shared.idempotency.IdempotencyKeyRecord
import com.fincore.shared.idempotency.IdempotencyResolution
import com.fincore.shared.idempotency.IdempotencyService
import com.fincore.shared.idempotency.IdempotencyState
import com.fincore.transactions.domain.Transaction
import com.fincore.transactions.domain.TransactionStatus
import com.fincore.transactions.infrastructure.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TransferServiceTest {

    private val accountService = mockk<AccountService>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val idempotencyService = mockk<IdempotencyService>()
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val objectMapper = ObjectMapper()

    private val transferService = TransferService(
        accountService,
        transactionRepository,
        idempotencyService,
        auditLogRepository,
        objectMapper
    )

    @Test
    fun `successful transfer executes balances, transitions to COMPLETED, and completes idempotency`() {
        val key = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val srcId = UUID.randomUUID()
        val destId = UUID.randomUUID()

        val command = TransferCommand(
            idempotencyKey = key,
            sourceAccountId = srcId,
            destinationAccountId = destId,
            amount = BigDecimal("100.0000"),
            currency = "GBP",
            callerUserId = userId,
            callerCustomerId = customerId
        )

        val idempRecord = IdempotencyKeyRecord(
            key = key,
            userId = userId,
            endpoint = "/api/v1/transfers",
            state = IdempotencyState.IN_PROGRESS,
            expiresAt = Instant.now().plusSeconds(3600)
        )

        every { idempotencyService.startOrResolve(key, userId, "/api/v1/transfers") } returns
            IdempotencyResolution.Proceed(idempRecord)

        val txSlot = slot<Transaction>()
        every { transactionRepository.saveAndFlush(capture(txSlot)) } answers { txSlot.captured }

        every {
            accountService.executeTransferBalances(srcId, destId, BigDecimal("100.0000"), "GBP", customerId)
        } returns TransferAccountsSummary(
            sourceAccountId = srcId,
            sourceCustomerId = customerId,
            destinationAccountId = destId,
            destinationCustomerId = UUID.randomUUID(),
            amount = BigDecimal("100.0000"),
            currency = "GBP",
            sourceRemainingBalance = "400.0000"
        )

        every { idempotencyService.complete(idempRecord.id, 201, any()) } returns Unit

        val result = transferService.executeTransfer(command)

        assertEquals("COMPLETED", result.status)
        assertEquals("100.0000", result.amount)
        assertFalse(result.replayed)

        verify(exactly = 1) {
            idempotencyService.complete(idempRecord.id, 201, any())
            auditLogRepository.append(
                eventType = "TRANSFER_COMPLETED",
                actorId = userId,
                actorRole = "ROLE_CUSTOMER",
                resourceType = "TRANSACTION",
                resourceId = result.transactionId,
                outcome = "SUCCESS",
                reason = null,
                ipAddress = null,
                userAgent = null,
                correlationId = null
            )
        }
    }

    @Test
    fun `replayed transfer returns cached response without debiting accounts`() {
        val key = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val cachedTxId = UUID.randomUUID()

        val cachedResult = TransferResult(
            transactionId = cachedTxId,
            idempotencyKey = key,
            sourceAccountId = UUID.randomUUID(),
            destinationAccountId = UUID.randomUUID(),
            type = "TRANSFER",
            status = "COMPLETED",
            amount = "250.0000",
            currency = "GBP",
            createdAt = Instant.now().toString(),
            replayed = false
        )

        every { idempotencyService.startOrResolve(key, userId, "/api/v1/transfers") } returns
            IdempotencyResolution.Replay(201, objectMapper.writeValueAsString(cachedResult))

        val command = TransferCommand(
            idempotencyKey = key,
            sourceAccountId = cachedResult.sourceAccountId,
            destinationAccountId = cachedResult.destinationAccountId,
            amount = BigDecimal("250.0000"),
            currency = "GBP",
            callerUserId = userId,
            callerCustomerId = UUID.randomUUID()
        )

        val result = transferService.executeTransfer(command)

        assertTrue(result.replayed)
        assertEquals(cachedTxId, result.transactionId)
        assertEquals("250.0000", result.amount)

        verify(exactly = 0) {
            accountService.executeTransferBalances(any(), any(), any(), any(), any())
            transactionRepository.saveAndFlush(any())
        }
    }

    @Test
    fun `insufficient funds throws exception, marks transaction FAILED and logs failure`() {
        val key = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val customerId = UUID.randomUUID()
        val srcId = UUID.randomUUID()
        val destId = UUID.randomUUID()

        val command = TransferCommand(
            idempotencyKey = key,
            sourceAccountId = srcId,
            destinationAccountId = destId,
            amount = BigDecimal("1000.0000"),
            currency = "GBP",
            callerUserId = userId,
            callerCustomerId = customerId
        )

        val idempRecord = IdempotencyKeyRecord(
            key = key,
            userId = userId,
            endpoint = "/api/v1/transfers",
            state = IdempotencyState.IN_PROGRESS,
            expiresAt = Instant.now().plusSeconds(3600)
        )

        every { idempotencyService.startOrResolve(key, userId, "/api/v1/transfers") } returns
            IdempotencyResolution.Proceed(idempRecord)

        val txSlot = slot<Transaction>()
        every { transactionRepository.saveAndFlush(capture(txSlot)) } answers { txSlot.captured }

        every {
            accountService.executeTransferBalances(srcId, destId, BigDecimal("1000.0000"), "GBP", customerId)
        } throws InsufficientFundsException("Insufficient funds")

        assertThrows(InsufficientFundsException::class.java) {
            transferService.executeTransfer(command)
        }

        verify(exactly = 1) {
            auditLogRepository.append(
                eventType = "TRANSFER_FAILED",
                actorId = userId,
                actorRole = "ROLE_CUSTOMER",
                resourceType = "TRANSACTION",
                resourceId = any(),
                outcome = "FAILURE",
                reason = "Insufficient funds",
                ipAddress = null,
                userAgent = null,
                correlationId = null
            )
        }
    }
}
