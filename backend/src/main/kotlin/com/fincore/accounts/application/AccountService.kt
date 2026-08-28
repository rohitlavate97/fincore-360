package com.fincore.accounts.application

import com.fincore.accounts.domain.Account
import com.fincore.accounts.domain.AccountStatus
import com.fincore.accounts.infrastructure.AccountRepository
import com.fincore.shared.audit.AuditLogRepository
import com.fincore.shared.correlation.CorrelationIdFilter
import com.fincore.shared.error.AccountNotActiveException
import com.fincore.shared.error.ConflictException
import com.fincore.shared.error.InsufficientFundsException
import com.fincore.shared.error.ResourceNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val auditLogRepository: AuditLogRepository
) {
    private val random = SecureRandom()

    @Transactional
    fun createAccount(command: CreateAccountCommand, httpRequest: HttpServletRequest? = null): AccountView {
        val accountNumber = generateUniqueAccountNumber()
        val balance = command.initialDeposit.setScale(4, RoundingMode.UNNECESSARY)

        val account = accountRepository.save(
            Account(
                id = UUID.randomUUID(),
                customerId = command.customerId,
                accountNumber = accountNumber,
                accountType = command.accountType,
                status = AccountStatus.ACTIVE,
                currency = command.currency.uppercase().trim(),
                ledgerBalance = balance,
                availableBalance = balance,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        auditLog(
            eventType = "ACCOUNT_CREATED",
            actorId = command.customerId,
            actorRole = "ROLE_CUSTOMER",
            resourceId = account.id,
            outcome = "SUCCESS",
            reason = null,
            httpRequest = httpRequest
        )

        return account.toView()
    }

    @Transactional(readOnly = true)
    fun getAccountsByCustomer(customerId: UUID, page: Int, size: Int): PagedResult<AccountView> {
        val safePage = if (page < 0) 0 else page
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val pagedAccounts = accountRepository.findByCustomerId(customerId, pageable)
        val items = pagedAccounts.content.map { it.toView() }

        return PagedResult(
            items = items,
            page = pagedAccounts.number,
            size = pagedAccounts.size,
            totalElements = pagedAccounts.totalElements,
            totalPages = pagedAccounts.totalPages,
            hasNext = pagedAccounts.hasNext()
        )
    }

    @Transactional(readOnly = true)
    fun getAccountById(accountId: UUID, customerId: UUID): AccountView {
        val account = accountRepository.findByIdAndCustomerId(accountId, customerId)
            .orElseThrow { ResourceNotFoundException("Account not found") }
        return account.toView()
    }

    @Transactional(readOnly = true)
    fun findAccountById(accountId: UUID): AccountView? {
        return accountRepository.findById(accountId).map { it.toView() }.orElse(null)
    }

    @Transactional
    fun executeTransferBalances(
        sourceAccountId: UUID,
        destinationAccountId: UUID,
        amount: BigDecimal,
        currency: String,
        callerCustomerId: UUID?
    ): TransferAccountsSummary {
        require(sourceAccountId != destinationAccountId) { "Source and destination accounts must be distinct" }
        require(amount > BigDecimal.ZERO) { "Transfer amount must be strictly positive" }

        // 1. Lock accounts in deterministic order (by account ID ASC) — deadlock defence (DATABASE-DESIGN.md §3)
        val lockedAccounts = accountRepository.findAllByIdInForUpdate(listOf(sourceAccountId, destinationAccountId))
        if (lockedAccounts.size < 2) {
            throw ResourceNotFoundException("One or both accounts not found")
        }

        val sourceAccount = lockedAccounts.first { it.id == sourceAccountId }
        val destAccount = lockedAccounts.first { it.id == destinationAccountId }

        // 2. Ownership check on source account (API-DESIGN.md §4: 404 on unowned to prevent enumeration)
        if (callerCustomerId != null && sourceAccount.customerId != callerCustomerId) {
            throw ResourceNotFoundException("Account not found")
        }

        // 3. Status checks
        if (sourceAccount.status != AccountStatus.ACTIVE) {
            throw AccountNotActiveException("Source account is not active (${sourceAccount.status})")
        }
        if (destAccount.status != AccountStatus.ACTIVE) {
            throw AccountNotActiveException("Destination account is not active (${destAccount.status})")
        }

        // 4. Currency checks
        if (!sourceAccount.currency.trim().equals(currency.trim(), ignoreCase = true) ||
            !destAccount.currency.trim().equals(currency.trim(), ignoreCase = true)) {
            throw ConflictException("Cross-currency transfers are not supported. Source: ${sourceAccount.currency}, Dest: ${destAccount.currency}, Request: $currency")
        }

        // 5. Balance check on AVAILABLE balance (DATABASE-DESIGN.md §2)
        val normalizedAmount = amount.setScale(4, RoundingMode.UNNECESSARY)
        if (sourceAccount.availableBalance < normalizedAmount) {
            throw InsufficientFundsException(
                "Insufficient funds: available balance ${sourceAccount.availableBalance} is less than requested amount $normalizedAmount"
            )
        }

        // 6. Debit source and credit destination
        sourceAccount.availableBalance = (sourceAccount.availableBalance - normalizedAmount).setScale(4, RoundingMode.UNNECESSARY)
        sourceAccount.ledgerBalance = (sourceAccount.ledgerBalance - normalizedAmount).setScale(4, RoundingMode.UNNECESSARY)
        sourceAccount.updatedAt = Instant.now()

        destAccount.availableBalance = (destAccount.availableBalance + normalizedAmount).setScale(4, RoundingMode.UNNECESSARY)
        destAccount.ledgerBalance = (destAccount.ledgerBalance + normalizedAmount).setScale(4, RoundingMode.UNNECESSARY)
        destAccount.updatedAt = Instant.now()

        accountRepository.save(sourceAccount)
        accountRepository.save(destAccount)

        return TransferAccountsSummary(
            sourceAccountId = sourceAccount.id,
            sourceCustomerId = sourceAccount.customerId,
            destinationAccountId = destAccount.id,
            destinationCustomerId = destAccount.customerId,
            amount = normalizedAmount,
            currency = currency.uppercase().trim(),
            sourceRemainingBalance = sourceAccount.availableBalance.toPlainString()
        )
    }
    private fun generateUniqueAccountNumber(): String {
        var attempts = 0
        while (attempts < 10) {
            val digits = StringBuilder()
            repeat(14) { digits.append(random.nextInt(10)) }
            val candidate = "GB29FINC$digits"
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate
            }
            attempts++
        }
        return "GB29FINC" + UUID.randomUUID().toString().replace("-", "").take(14).uppercase()
    }

    private fun Account.toView(): AccountView = AccountView(
        id = id,
        customerId = customerId,
        accountNumber = accountNumber,
        accountType = accountType.name,
        status = status.name,
        currency = currency.trim(),
        ledgerBalance = ledgerBalance.setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
        availableBalance = availableBalance.setScale(4, RoundingMode.UNNECESSARY).toPlainString(),
        createdAt = createdAt
    )

    private fun auditLog(
        eventType: String,
        actorId: UUID?,
        actorRole: String?,
        resourceId: UUID?,
        outcome: String,
        reason: String?,
        httpRequest: HttpServletRequest?
    ) {
        val corrIdStr = CorrelationIdFilter.current() ?: httpRequest?.getHeader(CorrelationIdFilter.HEADER)
        val correlationId = runCatching { UUID.fromString(corrIdStr) }.getOrNull()

        auditLogRepository.append(
            eventType = eventType,
            actorId = actorId,
            actorRole = actorRole,
            resourceType = "ACCOUNT",
            resourceId = resourceId,
            outcome = outcome,
            reason = reason,
            ipAddress = httpRequest?.remoteAddr,
            userAgent = httpRequest?.getHeader("User-Agent"),
            correlationId = correlationId
        )
    }
}
