package com.fincore.transactions.api

import com.fincore.accounts.application.AccountService
import com.fincore.shared.error.IdempotencyKeyRequiredException
import com.fincore.shared.error.ResourceNotFoundException
import com.fincore.transactions.api.dto.CreateTransferRequest
import com.fincore.transactions.api.dto.PagedTransactionResponse
import com.fincore.transactions.api.dto.TransactionResponse
import com.fincore.transactions.application.TransferCommand
import com.fincore.transactions.application.TransferService
import com.fincore.transactions.domain.Transaction
import com.fincore.transactions.infrastructure.TransactionRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transfers & Transactions", description = "Money transfers and transaction history")
class TransferController(
    private val transferService: TransferService,
    private val transactionRepository: TransactionRepository,
    private val accountService: AccountService
) {

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Execute a funds transfer with mandatory Idempotency-Key")
    fun transfer(
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKeyHeader: String?,
        @Valid @RequestBody request: CreateTransferRequest,
        @AuthenticationPrincipal jwt: Jwt,
        httpRequest: HttpServletRequest
    ): ResponseEntity<TransactionResponse> {
        if (idempotencyKeyHeader.isNullOrBlank()) {
            throw IdempotencyKeyRequiredException()
        }

        val idempotencyKey = runCatching { UUID.fromString(idempotencyKeyHeader.trim()) }
            .getOrElse { throw IdempotencyKeyRequiredException("Idempotency-Key must be a valid UUID") }

        val userId = UUID.fromString(jwt.subject)
        val customerIdStr = jwt.getClaimAsString("customerId")
        val customerId = customerIdStr?.let { UUID.fromString(it) }

        val command = TransferCommand(
            idempotencyKey = idempotencyKey,
            sourceAccountId = request.sourceAccountId,
            destinationAccountId = request.destinationAccountId,
            amount = request.amount,
            currency = request.currency,
            description = request.description,
            callerUserId = userId,
            callerCustomerId = customerId
        )

        val result = transferService.executeTransfer(command, httpRequest)

        val response = TransactionResponse(
            id = result.transactionId,
            idempotencyKey = result.idempotencyKey,
            sourceAccountId = result.sourceAccountId,
            destinationAccountId = result.destinationAccountId,
            type = result.type,
            status = result.status,
            amount = result.amount,
            currency = result.currency,
            createdAt = result.createdAt
        )

        return ResponseEntity
            .created(URI.create("/api/v1/transactions/${result.transactionId}"))
            .body(response)
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Get transaction details by ID with ownership verification")
    fun getTransactionById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<TransactionResponse> {
        val transaction = transactionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Transaction not found") }

        val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
        val isAdmin = roles.contains("ROLE_ADMIN")

        if (!isAdmin) {
            val customerIdStr = jwt.getClaimAsString("customerId") ?: jwt.subject
            val callerCustomerId = UUID.fromString(customerIdStr)

            val callerOwnsSource = transaction.sourceAccountId?.let {
                runCatching { accountService.getAccountById(it, callerCustomerId) }.isSuccess
            } ?: false

            val callerOwnsDest = transaction.destAccountId?.let {
                runCatching { accountService.getAccountById(it, callerCustomerId) }.isSuccess
            } ?: false

            if (!callerOwnsSource && !callerOwnsDest) {
                throw ResourceNotFoundException("Transaction not found")
            }
        }

        return ResponseEntity.ok(transaction.toResponse())
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Get transaction history for account with pagination")
    fun getAccountTransactions(
        @PathVariable accountId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<PagedTransactionResponse> {
        val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
        val isAdmin = roles.contains("ROLE_ADMIN")

        if (!isAdmin) {
            val customerIdStr = jwt.getClaimAsString("customerId") ?: jwt.subject
            val callerCustomerId = UUID.fromString(customerIdStr)
            accountService.getAccountById(accountId, callerCustomerId)
        }

        val safePage = if (page < 0) 0 else page
        val safeSize = size.coerceIn(1, 100)
        val pagedResult = transactionRepository.findByAccountId(accountId, PageRequest.of(safePage, safeSize))

        val response = PagedTransactionResponse(
            items = pagedResult.content.map { it.toResponse() },
            page = pagedResult.number,
            size = pagedResult.size,
            totalElements = pagedResult.totalElements,
            totalPages = pagedResult.totalPages,
            hasNext = pagedResult.hasNext()
        )
        return ResponseEntity.ok(response)
    }

    private fun Transaction.toResponse(): TransactionResponse = TransactionResponse(
        id = id,
        idempotencyKey = idempotencyKey,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destAccountId,
        type = type.name,
        status = status.name,
        amount = amount.toPlainString(),
        currency = currency.trim(),
        createdAt = createdAt.toString()
    )
}
