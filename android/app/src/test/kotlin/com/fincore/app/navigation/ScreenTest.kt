package com.fincore.app.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenTest {

    @Test
    fun `verify screen routes`() {
        assertEquals("login", Screen.Login.route)
        assertEquals("dashboard", Screen.Dashboard.route)
        assertEquals("accounts", Screen.Accounts.route)
        assertEquals("profile", Screen.Profile.route)
    }
}
