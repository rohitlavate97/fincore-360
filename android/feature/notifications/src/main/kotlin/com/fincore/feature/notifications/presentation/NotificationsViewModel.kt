package com.fincore.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fincore.core.common.result.ErrorType
import com.fincore.core.common.result.ScreenState
import com.fincore.feature.notifications.domain.model.NotificationItem
import com.fincore.feature.notifications.domain.usecase.GetNotificationsUseCase
import com.fincore.feature.notifications.domain.usecase.MarkNotificationReadUseCase
import com.fincore.feature.notifications.domain.usecase.ObserveUnreadCountUseCase
import com.fincore.feature.notifications.domain.usecase.RefreshNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val getNotificationsUseCase: GetNotificationsUseCase,
    private val observeUnreadCountUseCase: ObserveUnreadCountUseCase,
    private val refreshNotificationsUseCase: RefreshNotificationsUseCase,
    private val markNotificationReadUseCase: MarkNotificationReadUseCase
) : ViewModel() {

    private val _screenState = MutableStateFlow<ScreenState<List<NotificationItem>>>(ScreenState.Loading)
    val screenState: StateFlow<ScreenState<List<NotificationItem>>> = _screenState.asStateFlow()

    val unreadCount: StateFlow<Int> = observeUnreadCountUseCase()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        observeNotifications()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshNotificationsUseCase().onFailure { error ->
                if (_screenState.value is ScreenState.Loading) {
                    _screenState.value = ScreenState.Error(
                        message = error.message ?: "Failed to load notifications",
                        type = ErrorType.NETWORK
                    )
                }
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            markNotificationReadUseCase(id)
        }
    }

    fun onNotificationTapped(
        item: NotificationItem,
        onNavigateToTransaction: (String) -> Unit
    ) {
        if (!item.isRead) {
            markAsRead(item.id)
        }

        val deepLink = item.deepLinkUri
        if (!deepLink.isNullOrBlank() && deepLink.startsWith("fincore://transactions/")) {
            val transactionId = deepLink.removePrefix("fincore://transactions/")
            if (transactionId.isNotBlank()) {
                onNavigateToTransaction(transactionId)
            }
        }
    }

    private fun observeNotifications() {
        getNotificationsUseCase()
            .onEach { items ->
                _screenState.value = if (items.isEmpty()) {
                    ScreenState.Empty
                } else {
                    ScreenState.Success(items)
                }
            }
            .launchIn(viewModelScope)
    }
}
