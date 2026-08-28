package com.fincore.feature.transfer

import com.fincore.core.database.dao.TransactionDao
import com.fincore.feature.transfer.data.remote.TransferApi
import com.fincore.feature.transfer.data.remote.dto.TransferResponseDto
import com.fincore.feature.transfer.data.repository.TransferRepositoryImpl
import com.fincore.feature.transfer.domain.model.TransferRecord
import com.fincore.feature.transfer.domain.repository.TransferRepository
import com.fincore.feature.transfer.domain.usecase.ExecuteTransferUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TransferRepositoryTest {

    private val transferApi = mockk<TransferApi>()
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val repository = TransferRepositoryImpl(transferApi, transactionDao)

    @Test
    fun `executeTransfer calls API with Idempotency-Key and persists transaction to Room`() = runTest {
        val response = TransferResponseDto(
            id = "tx-123",
            idempotencyKey = "key-456",
            sourceAccountId = "acc-src",
            destinationAccountId = "acc-dest",
            type = "TRANSFER",
            status = "COMPLETED",
            amount = "75.0000",
            currency = "GBP",
            createdAt = "2026-08-28T12:00:00Z"
        )

        coEvery { transferApi.executeTransfer(any(), any()) } returns response

        val result = repository.executeTransfer(
            sourceAccountId = "acc-src",
            destinationAccountId = "acc-dest",
            amount = "75.0000",
            currency = "GBP"
        )

        assertTrue(result.isSuccess)
        val record = result.getOrThrow()
        assertEquals("tx-123", record.transactionId)
        assertEquals("COMPLETED", record.status)
        assertEquals("75.0000", record.amount)

        coVerify(exactly = 1) {
            transferApi.executeTransfer(any(), any())
            transactionDao.upsert(match { it.id == "tx-123" && it.status == "COMPLETED" })
        }
    }
}

class ExecuteTransferUseCaseTest {

    private val repository = mockk<TransferRepository>()
    private val useCase = ExecuteTransferUseCase(repository)

    @Test
    fun `invoke delegates to repository executeTransfer`() = runTest {
        val record = TransferRecord(
            transactionId = "tx-99",
            idempotencyKey = "key-99",
            sourceAccountId = "src",
            destinationAccountId = "dest",
            type = "TRANSFER",
            status = "COMPLETED",
            amount = "50.0000",
            currency = "GBP",
            createdAt = 1000L
        )

        coEvery { repository.executeTransfer("src", "dest", "50.0000", "GBP", null) } returns
            Result.success(record)

        val result = useCase("src", "dest", "50.0000", "GBP")

        assertTrue(result.isSuccess)
        assertEquals("tx-99", result.getOrThrow().transactionId)
    }
}
