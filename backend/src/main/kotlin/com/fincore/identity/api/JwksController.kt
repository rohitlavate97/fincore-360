package com.fincore.identity.api

import com.nimbusds.jose.jwk.JWKSet
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public JWKS (JSON Web Key Set) endpoint (RFC 7517).
 * Enables multi-replica and external clients/gateways to retrieve public keys
 * for dynamic JWT signature verification without redeploying.
 */
@RestController
@Tag(name = "JWKS", description = "Public JSON Web Key Set discovery")
class JwksController(
    private val jwkSet: JWKSet
) {
    @GetMapping("/.well-known/jwks.json", produces = ["application/json"])
    @Operation(summary = "Get JSON Web Key Set for JWT verification")
    fun getJwks(): Map<String, Any> {
        return jwkSet.toJSONObject()
    }
}
