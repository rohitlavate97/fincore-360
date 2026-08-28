package com.fincore.identity.application

import com.fincore.identity.domain.User
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class JwtTokenService(
    private val jwtEncoder: JwtEncoder
) {
    companion object {
        const val ISSUER = "https://api.fincore.com"
        const val ACCESS_TOKEN_EXPIRY_MINUTES = 15L
    }

    fun createAccessToken(user: User): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES))
            .subject(user.id.toString())
            .id(UUID.randomUUID().toString())
            .claim("username", user.username)
            .claim("roles", user.getRoleList())
            .claim("customerId", user.customerId?.toString() ?: user.id.toString())
            .build()

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }
}
