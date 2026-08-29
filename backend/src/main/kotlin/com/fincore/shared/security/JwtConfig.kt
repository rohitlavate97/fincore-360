package com.fincore.shared.security

import com.fincore.identity.application.JwtTokenService
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Configuration
class JwtConfig(
    @Value("\${fincore.security.jwt.private-key:}") private val privateKeyPem: String,
    @Value("\${fincore.security.jwt.public-key:}") private val publicKeyPem: String,
    @Value("\${fincore.security.jwt.key-id:fincore-signing-key-1}") private val keyId: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val rsaKeyPair: KeyPair by lazy {
        if (privateKeyPem.isNotBlank() && publicKeyPem.isNotBlank()) {
            log.info("Loading external RSA keypair from configured PEM properties (key-id={})", keyId)
            val pub = RsaKeyReader.readPublic(publicKeyPem)
            val priv = RsaKeyReader.readPrivate(privateKeyPem)
            KeyPair(pub, priv)
        } else {
            log.warn("No external JWT RSA keys configured! Generating ephemeral in-memory keypair. Multiple replicas will NOT share tokens.")
            generateRsaKey()
        }
    }

    @Bean
    fun rsaPublicKey(): RSAPublicKey = rsaKeyPair.public as RSAPublicKey

    @Bean
    fun rsaPrivateKey(): RSAPrivateKey = rsaKeyPair.private as RSAPrivateKey

    @Bean
    fun jwtEncoder(): JwtEncoder {
        val rsaKey = RSAKey.Builder(rsaPublicKey())
            .privateKey(rsaPrivateKey())
            .keyID(keyId)
            .build()
        val jwkSource = ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext>(JWKSet(rsaKey))
        return NimbusJwtEncoder(jwkSource)
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withPublicKey(rsaPublicKey()).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefault(),
                    JwtIssuerValidator(JwtTokenService.ISSUER),
                    JwtClaimValidator<List<String>>("aud") { it?.contains(JwtTokenService.AUDIENCE) == true }
                )
            )
        }
    }

    private fun generateRsaKey(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }
}
