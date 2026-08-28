package com.fincore.transactions

import com.fincore.accounts.domain.Account
import com.fincore.accounts.domain.AccountStatus
import com.fincore.accounts.domain.AccountType
import com.fincore.accounts.infrastructure.AccountRepository
import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.identity.domain.Role
import com.fincore.identity.domain.User
import com.fincore.identity.domain.UserStatus
import com.fincore.identity.infrastructure.UserRepository
import com.fincore.support.EmbeddedPostgresSupport
import com.fincore.transactions.application.TransferCommand
import com.fincore.transactions.application.TransferService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

@SpringBootTest
class ConcurrentTransferIntegrationTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transferService: TransferService

    @Test
    @DisplayName("Exit Criterion: concurrent transfers preserve balance integrity without deadlocks")
    fun concurrentTransfersPreserveBalanceIntegrity() {
        val customer = customerRepository.save(
            Customer(email = "conc_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Concurrent Cust", status = CustomerStatus.ACTIVE)
        )
        val user = userRepository.save(
            User(username = "conc_${UUID.randomUUID().toString().take(8)}", email = customer.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customer.id)
        )

        val accountA = accountRepository.save(
            Account(
                customerId = customer.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.CHECKING,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("5000.0000"),
                availableBalance = BigDecimal("5000.0000")
            )
        )

        val accountB = accountRepository.save(
            Account(
                customerId = customer.id,
                accountNumber = "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14),
                accountType = AccountType.SAVINGS,
                status = AccountStatus.ACTIVE,
                currency = "GBP",
                ledgerBalance = BigDecimal("5000.0000"),
                availableBalance = BigDecimal("5000.0000")
            )
        )

        val totalInitialBalance = accountA.availableBalance + accountB.availableBalance // 10,000.0000

        val threadCount = 20
        val executor = Executors.newFixedThreadPool(10)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)

        val transferAmount = BigDecimal("10.0000")
        val successCount = AtomicInteger(0)

        val tasks = (0 until threadCount).map { i ->
            Callable {
                readyLatch.countDown()
                startLatch.await()

                val (src, dest) = if (i % 2 == 0) Pair(accountA.id, accountB.id) else Pair(accountB.id, accountA.id)
                val cmd = TransferCommand(
                    idempotencyKey = UUID.randomUUID(),
                    sourceAccountId = src,
                    destinationAccountId = dest,
                    amount = transferAmount,
                    currency = "GBP",
                    callerUserId = user.id,
                    callerCustomerId = customer.id
                )

                try {
                    transferService.executeTransfer(cmd)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    // Ignored or logged
                }
            }
        }

        val futures = tasks.map { executor.submit(it) }
        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()

        futures.forEach { it.get(15, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(threadCount, successCount.get(), "All 20 concurrent transfers should succeed without deadlock")

        val finalA = accountRepository.findById(accountA.id).get()
        val finalB = accountRepository.findById(accountB.id).get()

        val totalFinalBalance = finalA.availableBalance + finalB.availableBalance

        // Critical Financial Invariant: total money in system must never change!
        assertEquals(0, totalInitialBalance.compareTo(totalFinalBalance))
        assertEquals(0, BigDecimal("5000.0000").compareTo(finalA.availableBalance))
        assertEquals(0, BigDecimal("5000.0000").compareTo(finalB.availableBalance))
    }

    @Test
    @DisplayName("Exit Criterion: concurrent idempotency race executes exactly once and prevents double debit")
    fun concurrentIdempotencyRaceExecutesOnce() {
        val customer = customerRepository.save(
            Customer(email = "race_${UUID.randomUUID().toString().take(8)}@bank.test", fullName = "Race Cust", status = CustomerStatus.ACTIVE)
        )
        val user = userRepository.save(
            User(username = "race_${UUID.randomUUID().toString().take(8)}", email = customer.email, passwordHash = "hash", roles = Role.CUSTOMER.authority, status = UserStatus.ACTIVE, customerId = customer.id)
        )

        val accountA = accountRepository.save(
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

        val accountB = accountRepository.save(
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

        val sharedKey = UUID.randomUUID()
        val threadCount = 2
        val executor = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val startLatch = CountDownLatch(1)

        val completedCount = AtomicInteger(0)
        val replayedOrConflictCount = AtomicInteger(0)

        val tasks = (0 until threadCount).map {
            Callable {
                readyLatch.countDown()
                startLatch.await()

                val cmd = TransferCommand(
                    idempotencyKey = sharedKey,
                    sourceAccountId = accountA.id,
                    destinationAccountId = accountB.id,
                    amount = BigDecimal("400.0000"),
                    currency = "GBP",
                    callerUserId = user.id,
                    callerCustomerId = customer.id
                )

                try {
                    val result = transferService.executeTransfer(cmd)
                    if (result.replayed) {
                        replayedOrConflictCount.incrementAndGet()
                    } else {
                        completedCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    // Conflict case (IN_PROGRESS)
                    replayedOrConflictCount.incrementAndGet()
                }
            }
        }

        val futures = tasks.map { executor.submit(it) }
        readyLatch.await(5, TimeUnit.SECONDS)
        startLatch.countDown()

        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        // Exactly one thread performed the initial transfer
        assertEquals(1, completedCount.get(), "Exactly one transfer must execute initially")
        assertEquals(1, replayedOrConflictCount.get(), "The concurrent duplicate must be replayed or rejected with conflict")

        val finalA = accountRepository.findById(accountA.id).get()
        val finalB = accountRepository.findById(accountB.id).get()

        // Source must be debited ONLY ONCE: £1000 - £400 = £600
        assertEquals(0, BigDecimal("600.0000").compareTo(finalA.availableBalance))
        // Destination must be credited ONLY ONCE: £500 + £400 = £900
        assertEquals(0, BigDecimal("900.0000").compareTo(finalB.availableBalance))
        // Total invariant preserved
        assertEquals(0, BigDecimal("1500.0000").compareTo(finalA.availableBalance + finalB.availableBalance))
    }
}
