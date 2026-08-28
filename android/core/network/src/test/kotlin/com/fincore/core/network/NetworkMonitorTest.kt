package com.fincore.core.network

import com.fincore.core.network.monitor.TestNetworkMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkMonitorTest {

    @Test
    fun `test network monitor emits connectivity changes`() = runTest {
        val monitor = TestNetworkMonitor(initialOnline = true)
        assertTrue(monitor.isOnline.first())

        monitor.setOnline(false)
        assertFalse(monitor.isOnline.first())

        monitor.setOnline(true)
        assertTrue(monitor.isOnline.first())
    }
}
