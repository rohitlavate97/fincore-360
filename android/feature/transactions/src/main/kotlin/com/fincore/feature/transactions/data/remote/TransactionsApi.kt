package com.fincore.feature.transactions.data.remote

import com.fincore.feature.transactions.data.remote.dto.PagedTransactionDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TransactionsApi {

    @GET("api/v1/accounts/{accountId}/transactions")
    suspend fun getAccountTransactions(
        @Path("accountId") accountId: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedTransactionDto
}
