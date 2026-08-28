package com.fincore.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fincore.core.common.result.ScreenState

@Composable
fun <T> ScreenStateContainer(
    state: ScreenState<T>,
    onRetry: () -> Unit = {},
    successContent: @Composable (T) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            is ScreenState.Loading -> CircularProgressIndicator()
            is ScreenState.Empty -> Text("No data available.")
            is ScreenState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message)
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
            is ScreenState.Success -> successContent(state.data)
        }
    }
}
