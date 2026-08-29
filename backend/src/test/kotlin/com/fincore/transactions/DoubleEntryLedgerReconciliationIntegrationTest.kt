package com.fincore.transactions

import com.fincore.accounts.application.AccountService
import com.fincore.accounts.application.CreateAccountCommand
import com.fincore.accounts.domain.AccountType
import com.fincore.customer.domain.Customer
import com.fincore.customer.domain.CustomerStatus
import com.fincore.customer.infrastructure.CustomerRepository
import com.fincore.support.EmbeddedPostgresSupport
import com.fincore.transactions.application.TransferCommand
import com.fincore.transactions.application.TransferService
import com.fincore.transactions.domain.LedgerDirection
import com.fincore.transactions.infrastructure.LedgerEntryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@Transactional
class DoubleEntryLedgerReconciliationIntegrationTest {

    @TestConfiguration
    class EmbeddedDatabaseConfig {
        @Bean
        @Primary
        fun dataSource(): DataSource = EmbeddedPostgresSupport.dataSource
    }

    @Autowired
    private lateinit var transferService: TransferService

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    @Autowired
    private lateinit var ledgerEntryRepository: LedgerEntryRepository

    private lateinit var customerA: Customer
    private lateinit var customerB: Customer

    @BeforeEach
    fun setup() {
        customerA = customerRepository.save(
            Customer(
                email = "ledger_a_${UUID.randomUUID().toString().take(8)}@bank.test",
                fullName = "Alice Ledger",
                status = CustomerStatus.ACTIVE
            )
        )
        customerB = customerRepository.save(
            Customer(
                email = "ledger_b_${UUID.randomUUID().toString().take(8)}@bank.test",
                fullName = "Bob Ledger",
                status = CustomerStatus.ACTIVE
            )
        )
    }

    @Test
    @DisplayName("M-7: Double-entry ledger paired DEBIT/CREDIT records, running balance accuracy and zero-sum invariant")
    fun doubleEntryLedgerMaintainsReconciliationInvariants() {
        val accountA = accountService.createAccount(
            CreateAccountCommand(
                customerId = customerA.id,
                accountType = AccountType.CHECKING,
                currency = "GBP",
                initialDeposit = BigDecimal.ZERO
            )
        )
        // Admin funds accounts via teller cash deposit
        accountService.depositCash(accountA.id, BigDecimal("1000.0000"), UUID.randomUUID())

        val accountB = accountService.createAccount(
            CreateAccountCommand(
                customerId = customerB.id,
                accountType = AccountType.CHECKING,
                currency = "GBP",
                initialDeposit = BigDecimal.ZERO
            )
        )
        accountService.depositCash(accountB.id, BigDecimal("500.0000"), UUID.randomUUID())

        // 1. Transfer £250.0000 from A -> B
        val transfer1 = transferService.executeTransfer(
            TransferCommand(
                idempotencyKey = UUID.randomUUID(),
                sourceAccountId = accountA.id,
                destinationAccountId = accountB.id,
                amount = BigDecimal("250.0000"),
                currency = "GBP",
                callerUserId = UUID.randomUUID(),
                callerCustomerId = customerA.id
            )
        )

        // 2. Transfer £100.0000 from B -> A
        val transfer2 = transferService.executeTransfer(
            TransferCommand(
                idempotencyKey = UUID.randomUUID(),
                sourceAccountId = accountB.id,
                destinationAccountId = accountA.id,
                amount = BigDecimal("100.0000"),
                currency = "GBP",
                callerUserId = UUID.randomUUID(),
                callerCustomerId = customerB.id
            )
        )

        // Invariant 1: Exactly 2 ledger entries per transaction (DEBIT and CREDIT)
        val entries1 = ledgerEntryRepository.findAllByTransactionId(transfer1.transactionId)
        assertEquals(2, entries1.size)
        val debit1 = entries1.first { it.direction == LedgerDirection.DEBIT }
        val credit1 = entries1.first { it.direction == LedgerDirection.CREDIT }
        assertEquals(BigDecimal("250.0000"), debit1.amount)
        assertEquals(BigDecimal("250.0000"), credit1.amount)
        assertEquals(accountA.id, debit1.accountId)
        assertEquals(accountB.id, credit1.accountId)
        assertEquals(BigDecimal("750.0000"), debit1.runningBalance)
        assertEquals(BigDecimal("750.0000"), credit1.runningBalance)

        val entries2 = ledgerEntryRepository.findAllByTransactionId(transfer2.transactionId)
        assertEquals(2, entries2.size)
        val debit2 = entries2.first { it.direction == LedgerDirection.DEBIT }
        val credit2 = entries2.first { it.direction == LedgerDirection.CREDIT }
        assertEquals(BigDecimal("100.0000"), debit2.amount)
        assertEquals(BigDecimal("100.0000"), credit2.amount)
        assertEquals(accountB.id, debit2.accountId)
        assertEquals(accountA.id, credit2.accountId)
        assertEquals(BigDecimal("650.0000"), debit2.runningBalance)
        assertEquals(BigDecimal("850.0000"), credit2.runningBalance)

        // Invariant 2: Invariant reconciliation: SUM(DEBIT) == SUM(CREDIT)
        val tx1Debits = ledgerEntryRepository.sumAmountByTransactionIdAndDirection(transfer1.transactionId, LedgerDirection.DEBIT)
        val tx1Credits = ledgerEntryRepository.sumAmountByTransactionIdAndDirection(transfer1.transactionId, LedgerDirection.CREDIT)
        assertEquals(BigDecimal("250.0000"), tx1Debits)
        assertEquals(BigDecimal("250.0000"), tx1Credits)

        val tx2Debits = ledgerEntryRepository.sumAmountByTransactionIdAndDirection(transfer2.transactionId, LedgerDirection.DEBIT)
        val tx2Credits = ledgerEntryRepository.sumAmountByTransactionIdAndDirection(transfer2.transactionId, LedgerDirection.CREDIT)
        assertEquals(BigDecimal("100.0000"), tx2Debits)
        assertEquals(BigDecimal("100.0000"), tx2Credits)

        val totalDebits = ledgerEntryRepository.sumAmountByDirection(LedgerDirection.DEBIT)
        val totalCredits = ledgerEntryRepository.sumAmountByDirection(LedgerDirection.CREDIT)
        assertEquals(totalDebits, totalCredits)
        assertTrue(totalDebits >= BigDecimal("350.0000"))

        // Invariant 3: Latest ledger entry running balance equals live account available balance
        val accountAEntries = ledgerEntryRepository.findAllByAccountIdOrderByCreatedAtDesc(accountA.id)
        val latestEntryA = accountAEntries.first()
        val currentAccountA = accountService.getAccountById(accountA.id, customerA.id)
        assertEquals(BigDecimal(currentAccountA.availableBalance), latestEntryA.runningBalance)

        val accountBEntries = ledgerEntryRepository.findAllByAccountIdOrderByCreatedAtDesc(accountB.id)
        val latestEntryB = accountBEntries.first()
        val currentAccountB = accountService.getAccountById(accountB.id, customerB.id)
        assertEquals(BigDecimal(currentAccountB.availableBalance), latestEntryB.runningBalance)
    }
}
