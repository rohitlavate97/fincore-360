package com.fincore.app.security

import android.view.WindowManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AndroidSecurityPolicyTest {

    @Test
    @DisplayName("Exit Criterion: FLAG_SECURE mask is 0x00002000 per Android WindowManager specifications")
    fun flagSecureConstantMatchesAndroidSpec() {
        assertEquals(0x00002000, WindowManager.LayoutParams.FLAG_SECURE)
    }
}
