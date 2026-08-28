package com.fincore.feature.transfer.data.remote

import com.fincore.feature.transfer.data.remote.dto.CreateTransferRequestDto
import com.fincore.feature.transfer.data.remote.dto.TransferResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TransferApi {

    @POST("api/v1/transfers")
    suspend fun executeTransfer(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateTransferRequestDto
    ): TransferResponseDto
}
