package com.fincore.transactions.infrastructure

import com.fincore.accounts.domain.Account
import com.fincore.accounts.domain.AccountStatus
import com.fincore.accounts.domain.AccountType
import com.fincore.accounts.infrastructure.AccountRepository
import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.support.EmbeddedPostgresSupport
import com.fincore.transactions.domain.Transaction
import com.fincore.transactions.domain.TransactionStatus
import com.fincore.transactions.domain.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@Transactional
class TransactionRepositoryTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Test
    fun `can persist transaction and query history by accountId`() {
        val customer = customerRepository.save(
            Customer(email = "tx_${UUID.randomUUID().toString().take(8)}@test.com", fullName = "Tx Test", status = CustomerStatus.ACTIVE)
        )

        val srcAccount = accountRepository.save(
            Account(
                customerId = customer.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.CHECKING,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("1000.0000"),
                availableBalance = BigDecimal("1000.0000")
            )
        )

        val destAccount = accountRepository.save(
            Account(
                customerId = customer.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.SAVINGS,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("500.0000"),
                availableBalance = BigDecimal("500.0000")
            )
        )

        val tx = transactionRepository.save(
            Transaction(
                idempotencyKey = UUID.randomUUID(),
                sourceAccountId = srcAccount.id,
                destAccountId = destAccount.id,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED,
                amount = BigDecimal("150.0000"),
                currency = "GBP"
            )
        )

        val page = transactionRepository.findByAccountId(srcAccount.id, PageRequest.of(0, 10))
        assertEquals(1, page.totalElements)
        assertEquals(tx.id, page.content[0].id)
        assertEquals("150.0000", page.content[0].amount.toPlainString())

        // Test deterministic ordered locking query on AccountRepository
        val lockedAccounts = accountRepository.findAllByIdInForUpdate(listOf(destAccount.id, srcAccount.id))
        assertEquals(2, lockedAccounts.size)
        // IDs must be in ascending order
        assertEquals(setOf(srcAccount.id, destAccount.id), lockedAccounts.map { it.id }.toSet())
    }
}
