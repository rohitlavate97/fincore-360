package com.fincore.identity.api

import com.fincore.shared.security.JwtConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JwksControllerTest {

    @Test
    fun `getJwks returns RFC 7517 compliant JSON Web Key Set with RS256 key`() {
        val jwtConfig = JwtConfig(privateKeyPem = "", publicKeyPem = "", keyId = "test-key-id")
        val controller = JwksController(jwtConfig.jwkSet())

        val jwks = controller.getJwks()

        assertNotNull(jwks["keys"])
        @Suppress("UNCHECKED_CAST")
        val keys = jwks["keys"] as List<Map<String, Any>>
        assertTrue(keys.isNotEmpty())

        val key = keys[0]
        assertEquals("RSA", key["kty"])
        assertEquals("test-key-id", key["kid"])
        assertNotNull(key["n"])
        assertNotNull(key["e"])
    }
}
