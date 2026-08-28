package com.fincore.feature.transfer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fincore.core.common.result.ErrorType
import com.fincore.core.common.result.ScreenState
import com.fincore.core.network.monitor.NetworkMonitor
import com.fincore.feature.accounts.domain.model.Account
import com.fincore.feature.accounts.domain.usecase.GetAccountsUseCase
import com.fincore.feature.transfer.domain.model.TransferRecord
import com.fincore.feature.transfer.domain.usecase.ExecuteTransferUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class TransferFormState(
    val sourceAccountId: String = "",
    val destinationAccountId: String = "",
    val amount: String = "",
    val description: String = "",
    val availableAccounts: List<Account> = emptyList(),
    val transferState: ScreenState<TransferRecord> = ScreenState.Empty
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val executeTransferUseCase: ExecuteTransferUseCase,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferFormState())
    val uiState: StateFlow<TransferFormState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            getAccountsUseCase().collect { accounts ->
                _uiState.value = _uiState.value.copy(
                    availableAccounts = accounts,
                    sourceAccountId = if (_uiState.value.sourceAccountId.isBlank() && accounts.isNotEmpty()) accounts.first().id else _uiState.value.sourceAccountId
                )
            }
        }
    }

    fun onSourceAccountSelected(accountId: String) {
        _uiState.value = _uiState.value.copy(sourceAccountId = accountId)
    }

    fun onDestinationAccountChanged(accountId: String) {
        _uiState.value = _uiState.value.copy(destinationAccountId = accountId)
    }

    fun onAmountChanged(amount: String) {
        _uiState.value = _uiState.value.copy(amount = amount)
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun submitTransfer() {
        val current = _uiState.value

        viewModelScope.launch {
            val online = networkMonitor.isOnline.first()
            if (!online) {
                _uiState.value = current.copy(
                    transferState = ScreenState.Error(
                        ErrorType.NETWORK,
                        "Connection required. Transfers cannot be executed while offline."
                    )
                )
                return@launch
            }

            val amountDec = runCatching { BigDecimal(current.amount.trim()) }.getOrNull()

            if (amountDec == null || amountDec <= BigDecimal.ZERO) {
                _uiState.value = current.copy(
                    transferState = ScreenState.Error(ErrorType.VALIDATION, "Amount must be a positive number")
                )
                return@launch
            }

            if (current.sourceAccountId.isBlank() || current.destinationAccountId.isBlank()) {
                _uiState.value = current.copy(
                    transferState = ScreenState.Error(ErrorType.VALIDATION, "Source and destination accounts are required")
                )
                return@launch
            }

            if (current.sourceAccountId == current.destinationAccountId) {
                _uiState.value = current.copy(
                    transferState = ScreenState.Error(ErrorType.VALIDATION, "Source and destination cannot be the same account")
                )
                return@launch
            }

            _uiState.value = current.copy(transferState = ScreenState.Loading)

            executeTransferUseCase(
                sourceAccountId = current.sourceAccountId,
                destinationAccountId = current.destinationAccountId,
                amount = current.amount.trim(),
                currency = "GBP",
                description = current.description.ifBlank { null }
            ).fold(
                onSuccess = { record ->
                    _uiState.value = _uiState.value.copy(
                        transferState = ScreenState.Success(record),
                        amount = "",
                        destinationAccountId = ""
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        transferState = ScreenState.Error(
                            ErrorType.SERVER,
                            error.message ?: "Transfer failed. Please check balance and try again."
                        )
                    )
                }
            )
        }
    }
}
