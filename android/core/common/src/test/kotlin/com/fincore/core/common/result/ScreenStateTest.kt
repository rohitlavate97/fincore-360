package com.fincore.core.common.result

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenStateTest {
    @Test
    fun `ScreenState Loading is correct`() {
        val state = ScreenState.Loading
        assertTrue(state is ScreenState.Loading)
    }
    
    @Test
    fun `ScreenState Success holds data`() {
        val state = ScreenState.Success("Data")
        assertEquals("Data", state.data)
    }
}
