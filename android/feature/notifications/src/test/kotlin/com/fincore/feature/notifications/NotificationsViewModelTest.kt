package com.fincore.feature.notifications

import com.fincore.core.common.result.ScreenState
import com.fincore.core.testing.MainDispatcherRule
import com.fincore.feature.notifications.domain.model.NotificationItem
import com.fincore.feature.notifications.domain.usecase.GetNotificationsUseCase
import com.fincore.feature.notifications.domain.usecase.MarkNotificationReadUseCase
import com.fincore.feature.notifications.domain.usecase.ObserveUnreadCountUseCase
import com.fincore.feature.notifications.domain.usecase.RefreshNotificationsUseCase
import com.fincore.feature.notifications.presentation.NotificationsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class NotificationsViewModelTest {

    private val getNotificationsUseCase = mockk<GetNotificationsUseCase>()
    private val observeUnreadCountUseCase = mockk<ObserveUnreadCountUseCase>()
    private val refreshNotificationsUseCase = mockk<RefreshNotificationsUseCase>()
    private val markNotificationReadUseCase = mockk<MarkNotificationReadUseCase>(relaxed = true)

    @Test
    fun `when notifications exist transitions to Success state`() = runTest {
        val items = listOf(
            NotificationItem("n1", "Transfer Received", "You received £100", "TRANSACTION_ALERT", "fincore://transactions/tx-1", false, 1000L, null)
        )
        every { getNotificationsUseCase() } returns flowOf(items)
        every { observeUnreadCountUseCase() } returns flowOf(1)
        coEvery { refreshNotificationsUseCase() } returns Result.success(Unit)

        val viewModel = NotificationsViewModel(
            getNotificationsUseCase,
            observeUnreadCountUseCase,
            refreshNotificationsUseCase,
            markNotificationReadUseCase
        )
        advanceUntilIdle()

        assertTrue(viewModel.screenState.value is ScreenState.Success)
        val success = viewModel.screenState.value as ScreenState.Success
        assertEquals(1, success.data.size)
        assertEquals("n1", success.data[0].id)
        assertEquals(1, viewModel.unreadCount.value)
    }

    @Test
    fun `when notifications empty transitions to Empty state`() = runTest {
        every { getNotificationsUseCase() } returns flowOf(emptyList())
        every { observeUnreadCountUseCase() } returns flowOf(0)
        coEvery { refreshNotificationsUseCase() } returns Result.success(Unit)

        val viewModel = NotificationsViewModel(
            getNotificationsUseCase,
            observeUnreadCountUseCase,
            refreshNotificationsUseCase,
            markNotificationReadUseCase
        )
        advanceUntilIdle()

        assertEquals(ScreenState.Empty, viewModel.screenState.value)
    }

    @Test
    @DisplayName("Exit Criterion: Tapping notification marks read and deep-links to transaction")
    fun onNotificationTappedTriggersDeepLink() = runTest {
        val item = NotificationItem(
            id = "n-100",
            title = "Money Received",
            body = "You received £250.00",
            type = "TRANSACTION_ALERT",
            deepLinkUri = "fincore://transactions/tx-999-uuid",
            isRead = false,
            createdAt = 2000L,
            readAt = null
        )
        every { getNotificationsUseCase() } returns flowOf(listOf(item))
        every { observeUnreadCountUseCase() } returns flowOf(1)
        coEvery { refreshNotificationsUseCase() } returns Result.success(Unit)

        val viewModel = NotificationsViewModel(
            getNotificationsUseCase,
            observeUnreadCountUseCase,
            refreshNotificationsUseCase,
            markNotificationReadUseCase
        )
        advanceUntilIdle()

        var navigatedTxId: String? = null
        viewModel.onNotificationTapped(item) { txId ->
            navigatedTxId = txId
        }
        advanceUntilIdle()

        // 1. Verifies notification was marked as read
        coVerify(exactly = 1) { markNotificationReadUseCase("n-100") }

        // 2. Verifies deep link resolved to correct transactionId
        assertEquals("tx-999-uuid", navigatedTxId)
    }
}
