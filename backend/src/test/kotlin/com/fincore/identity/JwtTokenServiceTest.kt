package com.fincore.identity

import com.fincore.identity.application.JwtTokenService
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
class JwtTokenServiceTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `mints valid RS256 JWT with correct claims`() {
        val user = User(
            id = UUID.randomUUID(),
            username = "alice",
            email = "alice@example.com",
            passwordHash = "hash",
            roles = "ROLE_CUSTOMER,ROLE_ADMIN",
            status = UserStatus.ACTIVE
        )

        val token = jwtTokenService.createAccessToken(user)
        assertNotNull(token)

        val decoded = jwtDecoder.decode(token)
        assertEquals(user.id.toString(), decoded.subject)
        assertEquals("https://api.fincore.com", decoded.getClaimAsString("iss"))
        assertEquals("alice", decoded.getClaim<String>("username"))
        val roles = decoded.getClaimAsStringList("roles")
        assertEquals(listOf("ROLE_CUSTOMER", "ROLE_ADMIN"), roles)
        assertNotNull(decoded.id)
        assertNotNull(decoded.expiresAt)
    }

    @Test
    fun `tampered token fails verification`() {
        val user = User(
            id = UUID.randomUUID(),
            username = "bob",
            email = "bob@example.com",
            passwordHash = "hash",
            roles = Role.CUSTOMER.authority,
            status = UserStatus.ACTIVE
        )

        val token = jwtTokenService.createAccessToken(user)
        val tampered = token.dropLast(5) + "abcde"

        assertThrows(BadJwtException::class.java) {
            jwtDecoder.decode(tampered)
        }
    }
}
