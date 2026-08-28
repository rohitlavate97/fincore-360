package com.fincore.core.common.result

sealed class ScreenState<out T> {
    data object Loading : ScreenState<Nothing>()
    data class Success<T>(val data: T) : ScreenState<T>()
    data object Empty : ScreenState<Nothing>()
    data class Error(val type: ErrorType, val message: String) : ScreenState<Nothing>()
}
