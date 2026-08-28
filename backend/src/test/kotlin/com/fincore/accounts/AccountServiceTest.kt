package com.fincore.accounts

import com.fincore.accounts.application.AccountService
import com.fincore.accounts.application.CreateAccountCommand
import com.fincore.accounts.domain.Account
import com.fincore.accounts.domain.AccountStatus
import com.fincore.accounts.domain.AccountType
import com.fincore.accounts.infrastructure.AccountRepository
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.error.ResourceNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Optional
import java.util.UUID

class AccountServiceTest {

    private val accountRepository = mockk<AccountRepository>()
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val accountService = AccountService(accountRepository, auditLogRepository)

    @Test
    fun `createAccount generates unique account number, saves account, and returns AccountView`() {
        val customerId = UUID.randomUUID()
        val command = CreateAccountCommand(
            customerId = customerId,
            accountType = AccountType.CHECKING,
            currency = "GBP",
            initialDeposit = BigDecimal("100.0000")
        )

        every { accountRepository.existsByAccountNumber(any()) } returns false
        val accountSlot = slot<Account>()
        every { accountRepository.save(capture(accountSlot)) } answers { accountSlot.captured }

        val view = accountService.createAccount(command)

        assertNotNull(view.id)
        assertEquals(customerId, view.customerId)
        assertTrue(view.accountNumber.startsWith("GB29FINC"))
        assertEquals("CHECKING", view.accountType)
        assertEquals("ACTIVE", view.status)
        assertEquals("GBP", view.currency)
        assertEquals("100.0000", view.ledgerBalance)
        assertEquals("100.0000", view.availableBalance)

        verify(exactly = 1) {
            auditLogRepository.append(
                eventType = "ACCOUNT_CREATED",
                actorId = customerId,
                actorRole = "ROLE_CUSTOMER",
                resourceType = "ACCOUNT",
                resourceId = view.id,
                outcome = "SUCCESS",
                reason = null,
                ipAddress = null,
                userAgent = null,
                correlationId = null
            )
        }
    }

    @Test
    fun `getAccountsByCustomer returns paged accounts with scale 4 balance format`() {
        val customerId = UUID.randomUUID()
        val account = Account(
            id = UUID.randomUUID(),
            customerId = customerId,
            accountNumber = "GB29FINC12345678901234",
            accountType = AccountType.SAVINGS,
            status = AccountStatus.ACTIVE,
            currency = "GBP",
            ledgerBalance = BigDecimal("250.7500"),
            availableBalance = BigDecimal("250.7500")
        )

        every { accountRepository.findByCustomerId(customerId, any<Pageable>()) } returns
            PageImpl(listOf(account))

        val result = accountService.getAccountsByCustomer(customerId, 0, 10)

        assertEquals(1, result.items.size)
        assertEquals("250.7500", result.items[0].availableBalance)
        assertEquals("250.7500", result.items[0].ledgerBalance)
        assertEquals("SAVINGS", result.items[0].accountType)
    }

    @Test
    fun `getAccountById throws ResourceNotFoundException when account not found or customer mismatched`() {
        val accountId = UUID.randomUUID()
        val customerId = UUID.randomUUID()

        every { accountRepository.findByIdAndCustomerId(accountId, customerId) } returns Optional.empty()

        assertThrows(ResourceNotFoundException::class.java) {
            accountService.getAccountById(accountId, customerId)
        }
    }
}
