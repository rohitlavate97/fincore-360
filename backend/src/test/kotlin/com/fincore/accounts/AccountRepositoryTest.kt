package com.fincore.accounts

import com.fincore.accounts.domain.Account
import com.fincore.accounts.domain.AccountStatus
import com.fincore.accounts.domain.AccountType
import com.fincore.accounts.infrastructure.AccountRepository
import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.support.EmbeddedPostgresSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@Transactional
class AccountRepositoryTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    private lateinit var customerId: UUID

    @BeforeEach
    fun setupCustomer() {
        val customer = customerRepository.save(
            Customer(
                email = "account_test_${UUID.randomUUID().toString().take(8)}@bank.test",
                fullName = "Account Tester",
                status = CustomerStatus.ACTIVE
            )
        )
        customerId = customer.id
    }

    @Test
    @DisplayName("can persist account and query with pagination by customerId")
    fun persistAndQueryAccounts() {
        val account1 = accountRepository.save(
            Account(
                customerId = customerId,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14).uppercase(),
                accountType = AccountType.CHECKING,
                currency = "GBP",
                ledgerBalance = BigDecimal("1500.5000"),
                availableBalance = BigDecimal("1500.5000")
            )
        )

        val account2 = accountRepository.save(
            Account(
                customerId = customerId,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14).uppercase(),
                accountType = AccountType.SAVINGS,
                currency = "GBP",
                ledgerBalance = BigDecimal("5000.0000"),
                availableBalance = BigDecimal("5000.0000")
            )
        )

        val page = accountRepository.findByCustomerId(customerId, PageRequest.of(0, 10))
        assertEquals(2, page.totalElements)
        assertEquals(1, page.totalPages)
        assertTrue(page.content.any { it.accountType == AccountType.CHECKING })
        assertTrue(page.content.any { it.accountType == AccountType.SAVINGS })

        val found = accountRepository.findByIdAndCustomerId(account1.id, customerId)
        assertTrue(found.isPresent)
        assertEquals(account1.accountNumber, found.get().accountNumber)
    }

    @Test
    @DisplayName("findByIdAndCustomerId returns empty when customerId does not match")
    fun findByIdAndCustomerIdMismatchedCustomer() {
        val account = accountRepository.save(
            Account(
                customerId = customerId,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14).uppercase(),
                accountType = AccountType.CHECKING,
                currency = "GBP",
                ledgerBalance = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY),
                availableBalance = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY)
            )
        )

        val otherCustomerId = UUID.randomUUID()
        val found = accountRepository.findByIdAndCustomerId(account.id, otherCustomerId)
        assertFalse(found.isPresent)
    }
}
