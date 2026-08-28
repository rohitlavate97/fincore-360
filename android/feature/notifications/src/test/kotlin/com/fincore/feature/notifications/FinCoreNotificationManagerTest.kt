package com.fincore.feature.notifications

import com.fincore.feature.notifications.manager.FinCoreNotificationManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FinCoreNotificationManagerTest {

    @Test
    @DisplayName("Exit Criterion: Deep link URI conforms to fincore://transactions/{transactionId}")
    fun deepLinkUriPatternConformsToContract() {
        val transactionId = "tx-12345-uuid"
        val uriString = FinCoreNotificationManager.createTransactionDeepLinkUri(transactionId)

        assertEquals("fincore://transactions/tx-12345-uuid", uriString)
    }
}
