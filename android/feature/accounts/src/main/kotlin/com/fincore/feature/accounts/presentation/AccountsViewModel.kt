package com.fincore.feature.accounts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fincore.core.common.result.ErrorType
import com.fincore.core.common.result.ScreenState
import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.usecase.CreateAccountUseCase
import com.fincore.feature.accounts.domain.usecase.GetAccountsUseCase
import com.fincore.feature.accounts.domain.usecase.RefreshAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val refreshAccountsUseCase: RefreshAccountsUseCase,
    private val createAccountUseCase: CreateAccountUseCase
) : ViewModel() {

    private val _screenState = MutableStateFlow<ScreenState<List<Account>>>(ScreenState.Loading)
    val screenState: StateFlow<ScreenState<List<Account>>> = _screenState.asStateFlow()

    init {
        observeAccounts()
        refresh()
    }

    fun observeAccounts() {
        viewModelScope.launch {
            getAccountsUseCase()
                .catch { e ->
                    if (_screenState.value !is ScreenState.Success) {
                        _screenState.value = ScreenState.Error(
                            type = ErrorType.UNKNOWN,
                            message = e.message ?: "Failed to load accounts"
                        )
                    }
                }
                .collect { accounts ->
                    if (accounts.isEmpty()) {
                        if (_screenState.value !is ScreenState.Error) {
                            _screenState.value = ScreenState.Empty
                        }
                    } else {
                        _screenState.value = ScreenState.Success(accounts)
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshAccountsUseCase()
                .onFailure { error ->
                    if (_screenState.value is ScreenState.Empty || _screenState.value is ScreenState.Loading) {
                        _screenState.value = ScreenState.Error(
                            type = ErrorType.NETWORK,
                            message = error.message ?: "Failed to connect to FinCore servers. Please retry."
                        )
                    }
                }
        }
    }

    fun createAccount(accountType: String = "CHECKING", currency: String = "GBP", initialDeposit: String = "0.0000") {
        viewModelScope.launch {
            createAccountUseCase(accountType, currency, initialDeposit)
                .onFailure { error ->
                    _screenState.value = ScreenState.Error(
                        type = ErrorType.SERVER,
                        message = error.message ?: "Failed to create account"
                    )
                }
        }
    }
}
