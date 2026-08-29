package com.fincore.security

import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.shared.security.JwtConfig
import com.fincore.shared.security.RsaKeyReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.BadJwtException
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

/**
 * Verifies C-1: Multi-instance token verification.
 * Proves that access tokens minted on replica A can be decoded and verified
 * on replica B when configured with shared external PEM keys.
 */
class JwtCrossInstanceVerificationTest {

    @Test
    @DisplayName("C-1: Token minted by Replica A is verified by Replica B using shared external PEM keys")
    fun tokenMintedByReplicaAIsVerifiedByReplicaB() {
        // 1. Generate a production-grade 2048-bit RSA keypair and export to PEM
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()
        val privateKeyPem = RsaKeyReader.toPem(keyPair.private as RSAPrivateKey)
        val publicKeyPem = RsaKeyReader.toPem(keyPair.public as RSAPublicKey)

        // 2. Initialize Replica A and mint access token
        val replicaAConfig = JwtConfig(
            privateKeyPem = privateKeyPem,
            publicKeyPem = publicKeyPem,
            keyId = "prod-key-1"
        )
        val tokenServiceA = JwtTokenService(replicaAConfig.jwtEncoder())

        val testUser = User(
            id = UUID.randomUUID(),
            username = "alice_multireplica",
            email = "alice@fincore.bank",
            passwordHash = "hash",
            roles = Role.CUSTOMER.authority,
            status = UserStatus.ACTIVE,
            customerId = UUID.randomUUID()
        )

        val token = tokenServiceA.createAccessToken(testUser)
        assertNotNull(token)

        // 3. Initialize Replica B (completely separate JVM bean instance) with same PEM keys
        val replicaBConfig = JwtConfig(
            privateKeyPem = privateKeyPem,
            publicKeyPem = publicKeyPem,
            keyId = "prod-key-1"
        )
        val decoderB = replicaBConfig.jwtDecoder()

        // 4. Verify Replica B successfully validates token minted by Replica A
        val decodedJwt = decoderB.decode(token)
        assertEquals(testUser.id.toString(), decodedJwt.subject)
        assertEquals(testUser.username, decodedJwt.getClaimAsString("username"))
        assertEquals(listOf(JwtTokenService.AUDIENCE), decodedJwt.audience)
        assertEquals(JwtTokenService.ISSUER, decodedJwt.issuer.toString())

        // 5. Verify that Replica C with a DIFFERENT keypair rejects the token
        val otherKp = kpg.generateKeyPair()
        val replicaCConfig = JwtConfig(
            privateKeyPem = RsaKeyReader.toPem(otherKp.private as RSAPrivateKey),
            publicKeyPem = RsaKeyReader.toPem(otherKp.public as RSAPublicKey),
            keyId = "other-key-2"
        )
        val decoderC = replicaCConfig.jwtDecoder()

        assertThrows(BadJwtException::class.java) {
            decoderC.decode(token)
        }
    }
}
