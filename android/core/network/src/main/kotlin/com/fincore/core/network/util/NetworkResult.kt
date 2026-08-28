package com.fincore.core.network.util

sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String) : NetworkResult<Nothing>()
    data class NetworkError(val exception: Exception) : NetworkResult<Nothing>()
}
