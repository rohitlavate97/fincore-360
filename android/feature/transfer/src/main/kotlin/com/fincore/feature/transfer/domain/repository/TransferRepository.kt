package com.fincore.feature.transfer.domain.repository

import com.fincore.feature.transfer.domain.model.TransferRecord

interface TransferRepository {
    suspend fun executeTransfer(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: String,
        currency: String = "GBP",
        description: String? = null
    ): Result<TransferRecord>
}
