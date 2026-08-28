package com.fincore.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val timestamp: String,
    val status: Int,
    val errorCode: String,
    val message: String,
    val path: String,
    val correlationId: String
)
