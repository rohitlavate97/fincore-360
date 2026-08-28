package com.fincore.shared.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

@Configuration
class JwtConfig {

    private val keyPair: KeyPair = generateRsaKey()

    @Bean
    fun rsaPublicKey(): RSAPublicKey = keyPair.public as RSAPublicKey

    @Bean
    fun rsaPrivateKey(): RSAPrivateKey = keyPair.private as RSAPrivateKey

    @Bean
    fun jwtEncoder(): JwtEncoder {
        val rsaKey = RSAKey.Builder(rsaPublicKey())
            .privateKey(rsaPrivateKey())
            .keyID(UUID.randomUUID().toString())
            .build()
        val jwkSource = ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext>(JWKSet(rsaKey))
        return NimbusJwtEncoder(jwkSource)
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withPublicKey(rsaPublicKey()).build()
    }

    private fun generateRsaKey(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }
}
