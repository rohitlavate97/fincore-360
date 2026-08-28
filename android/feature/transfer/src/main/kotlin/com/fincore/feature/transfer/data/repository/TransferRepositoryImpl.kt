package com.fincore.feature.transfer.data.repository

import com.fincore.core.database.dao.TransactionDao
import com.fincore.core.database.entity.TransactionEntity
import com.fincore.feature.transfer.data.remote.TransferApi
import com.fincore.feature.transfer.data.remote.dto.CreateTransferRequestDto
import com.fincore.feature.transfer.domain.model.TransferRecord
import com.fincore.feature.transfer.domain.repository.TransferRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class TransferRepositoryImpl @Inject constructor(
    private val transferApi: TransferApi,
    private val transactionDao: TransactionDao
) : TransferRepository {

    override suspend fun executeTransfer(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: String,
        currency: String,
        description: String?
    ): Result<TransferRecord> = runCatching {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = CreateTransferRequestDto(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            currency = currency,
            description = description
        )

        val response = transferApi.executeTransfer(idempotencyKey, request)
        val epochMillis = runCatching { Instant.parse(response.createdAt).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())

        val entity = TransactionEntity(
            id = response.id,
            idempotencyKey = response.idempotencyKey,
            sourceAccountId = response.sourceAccountId,
            destAccountId = response.destinationAccountId,
            type = response.type,
            status = response.status,
            amount = response.amount,
            currency = response.currency,
            createdAt = epochMillis
        )

        transactionDao.upsert(entity)

        TransferRecord(
            transactionId = entity.id,
            idempotencyKey = entity.idempotencyKey,
            sourceAccountId = entity.sourceAccountId,
            destinationAccountId = entity.destAccountId,
            type = entity.type,
            status = entity.status,
            amount = entity.amount,
            currency = entity.currency,
            createdAt = entity.createdAt
        )
    }
}
