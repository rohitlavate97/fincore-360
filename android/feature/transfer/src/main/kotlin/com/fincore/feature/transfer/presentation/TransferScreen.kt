package com.fincore.feature.transfer.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fincore.core.common.result.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    viewModel: TransferViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transfer Funds") }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isOnline) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Connection required. Transfers cannot be executed or queued offline.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            OutlinedTextField(
                value = state.sourceAccountId,
                onValueChange = { viewModel.onSourceAccountSelected(it) },
                label = { Text("Source Account ID") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.destinationAccountId,
                onValueChange = { viewModel.onDestinationAccountChanged(it) },
                label = { Text("Destination Account ID") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.amount,
                onValueChange = { viewModel.onAmountChanged(it) },
                label = { Text("Amount (GBP)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.onDescriptionChanged(it) },
                label = { Text("Reference / Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.submitTransfer() },
                modifier = Modifier.fillMaxWidth(),
                enabled = isOnline && state.transferState !is ScreenState.Loading
            ) {
                Text("Confirm Transfer")
            }

            when (val ts = state.transferState) {
                is ScreenState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing transfer with atomic idempotency...")
                    }
                }
                is ScreenState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Transfer Successful!", style = MaterialTheme.typography.titleMedium)
                            Text("Amount: £${ts.data.amount}")
                            Text("Transaction: ${ts.data.transactionId}")
                        }
                    }
                }
                is ScreenState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Transfer Failed", style = MaterialTheme.typography.titleMedium)
                            Text(ts.message)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
