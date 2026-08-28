package com.fincore.feature.accounts.data.remote

import com.fincore.feature.accounts.data.remote.dto.AccountDto
import com.fincore.feature.accounts.data.remote.dto.CreateAccountRequestDto
import com.fincore.feature.accounts.data.remote.dto.PagedAccountDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AccountApi {

    @GET("api/v1/accounts")
    suspend fun getAccounts(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): PagedAccountDto

    @GET("api/v1/accounts/{id}")
    suspend fun getAccountById(@Path("id") id: String): AccountDto

    @POST("api/v1/accounts")
    suspend fun createAccount(@Body request: CreateAccountRequestDto): AccountDto
}
