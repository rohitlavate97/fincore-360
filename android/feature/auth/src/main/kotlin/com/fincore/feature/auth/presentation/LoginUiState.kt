package com.fincore.feature.auth.presentation

data class LoginUiState(
    val username: String = "",
    val roles: List<String> = emptyList(),
    val isAuthenticated: Boolean = false
)
