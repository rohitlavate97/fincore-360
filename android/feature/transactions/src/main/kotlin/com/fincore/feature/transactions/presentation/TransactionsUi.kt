package com.fincore.feature.transactions.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fincore.core.common.result.ErrorType
import com.fincore.core.common.result.ScreenState
import com.fincore.core.ui.component.ScreenStateContainer
import com.fincore.feature.transactions.domain.model.TransactionItem
import com.fincore.feature.transactions.domain.usecase.GetAccountTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val getAccountTransactionsUseCase: GetAccountTransactionsUseCase
) : ViewModel() {

    private val _screenState = MutableStateFlow<ScreenState<List<TransactionItem>>>(ScreenState.Loading)
    val screenState: StateFlow<ScreenState<List<TransactionItem>>> = _screenState.asStateFlow()

    fun loadTransactions(accountId: String) {
        viewModelScope.launch {
            _screenState.value = ScreenState.Loading
            getAccountTransactionsUseCase(accountId).collect { items ->
                if (items.isEmpty()) {
                    _screenState.value = ScreenState.Empty
                } else {
                    _screenState.value = ScreenState.Success(items)
                }
            }
        }
        viewModelScope.launch {
            getAccountTransactionsUseCase.refresh(accountId).onFailure { error ->
                if (_screenState.value is ScreenState.Empty || _screenState.value is ScreenState.Loading) {
                    _screenState.value = ScreenState.Error(
                        ErrorType.NETWORK,
                        error.message ?: "Failed to refresh transaction history"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    viewModel: TransactionsViewModel,
    accountId: String,
    modifier: Modifier = Modifier
) {
    val state by viewModel.screenState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Transaction History") })
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScreenStateContainer(
                state = state,
                onRetry = { viewModel.loadTransactions(accountId) }
            ) { transactions ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(transactions, key = { it.id }) { item ->
                        TransactionRowCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionRowCard(item: TransactionItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = item.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = item.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "${item.currency} ${item.amount}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Transaction Reference", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Text(transactionId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.padding(top = 12.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Verified Transaction") }
                    )
                }
            }
        }
    }
}