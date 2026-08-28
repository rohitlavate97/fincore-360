package com.fincore.identity

import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `user can be created and retrieved by username and email`() {
        val user = User(
            id = UUID.randomUUID(),
            username = "test_user_${UUID.randomUUID()}",
            email = "user_${UUID.randomUUID()}@example.com",
            passwordHash = "\$2a\$10\$dummyBcryptHashedPasswordExample",
            roles = Role.CUSTOMER.authority,
            status = UserStatus.ACTIVE
        )

        val saved = userRepository.save(user)
        val retrieved = userRepository.findByUsername(saved.username)

        assertTrue(retrieved.isPresent)
        assertEquals(saved.email, retrieved.get().email)
        assertEquals(listOf(Role.CUSTOMER.authority), retrieved.get().getRoleList())
        assertTrue(retrieved.get().isAccountNonLocked())
    }

    @Test
    fun `locked user accounts report locked state properly`() {
        val lockedUser = User(
            id = UUID.randomUUID(),
            username = "locked_user_${UUID.randomUUID()}",
            email = "locked_${UUID.randomUUID()}@example.com",
            passwordHash = "hash",
            status = UserStatus.LOCKED,
            lockedUntil = Instant.now().plus(1, ChronoUnit.HOURS)
        )

        assertFalse(lockedUser.isAccountNonLocked())

        val expiredLockUser = User(
            id = UUID.randomUUID(),
            username = "expired_lock_${UUID.randomUUID()}",
            email = "expired_${UUID.randomUUID()}@example.com",
            passwordHash = "hash",
            status = UserStatus.LOCKED,
            lockedUntil = Instant.now().minus(1, ChronoUnit.MINUTES)
        )

        assertTrue(expiredLockUser.isAccountNonLocked())
    }
}
