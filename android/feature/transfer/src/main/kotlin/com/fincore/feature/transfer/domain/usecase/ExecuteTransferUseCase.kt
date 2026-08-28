package com.fincore.feature.transfer.domain.usecase

import com.fincore.feature.transfer.domain.model.TransferRecord
import com.fincore.feature.transfer.domain.repository.TransferRepository
import javax.inject.Inject

class ExecuteTransferUseCase @Inject constructor(
    private val transferRepository: TransferRepository
) {
    suspend operator fun invoke(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: String,
        currency: String = "GBP",
        description: String? = null
    ): Result<TransferRecord> {
        return transferRepository.executeTransfer(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            currency = currency,
            description = description
        )
    }
}
