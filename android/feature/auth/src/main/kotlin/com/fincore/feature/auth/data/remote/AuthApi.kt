package com.fincore.feature.auth.data.remote

import com.fincore.feature.auth.data.remote.dto.AuthResponseDto
import com.fincore.feature.auth.data.remote.dto.LoginRequestDto
import com.fincore.feature.auth.data.remote.dto.LogoutRequestDto
import com.fincore.feature.auth.data.remote.dto.RegisterRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): AuthResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: LogoutRequestDto)
}
