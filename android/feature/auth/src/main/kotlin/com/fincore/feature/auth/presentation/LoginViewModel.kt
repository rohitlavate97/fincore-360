package com.fincore.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fincore.core.common.result.ErrorType
import com.fincore.core.common.result.ScreenState
import com.fincore.feature.auth.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _screenState = MutableStateFlow<ScreenState<LoginUiState>>(ScreenState.Empty)
    val screenState: StateFlow<ScreenState<LoginUiState>> = _screenState.asStateFlow()

    fun login(username: String, password: String) {
        _screenState.value = ScreenState.Loading

        viewModelScope.launch {
            loginUseCase(username, password)
                .onSuccess { response ->
                    _screenState.value = ScreenState.Success(
                        LoginUiState(
                            username = response.username,
                            roles = response.roles,
                            isAuthenticated = true
                        )
                    )
                }
                .onFailure { error ->
                    _screenState.value = ScreenState.Error(
                        type = ErrorType.UNAUTHORIZED,
                        message = error.message ?: "Authentication failed. Please check credentials."
                    )
                }
        }
    }

    fun resetState() {
        _screenState.value = ScreenState.Empty
    }
}
