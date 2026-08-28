package com.fincore.accounts.api

import com.fincore.accounts.api.dto.AccountResponse
import com.fincore.accounts.api.dto.CreateAccountRequest
import com.fincore.accounts.api.dto.PagedAccountResponse
import com.fincore.accounts.application.AccountService
import com.fincore.accounts.application.AccountView
import com.fincore.accounts.application.CreateAccountCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Customer bank account management and lookups")
class AccountController(
    private val accountService: AccountService
) {

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "List accounts for authenticated customer with pagination")
    fun getAccounts(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) customerId: UUID?
    ): ResponseEntity<PagedAccountResponse> {
        val targetCustomerId = resolveCustomerId(jwt, customerId)
        val pagedResult = accountService.getAccountsByCustomer(targetCustomerId, page, size)

        val response = PagedAccountResponse(
            items = pagedResult.items.map { it.toResponse() },
            page = pagedResult.page,
            size = pagedResult.size,
            totalElements = pagedResult.totalElements,
            totalPages = pagedResult.totalPages,
            hasNext = pagedResult.hasNext
        )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Get account details by ID with ownership verification")
    fun getAccountById(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) customerId: UUID?
    ): ResponseEntity<AccountResponse> {
        val targetCustomerId = resolveCustomerId(jwt, customerId)
        val account = accountService.getAccountById(id, targetCustomerId)
        return ResponseEntity.ok(account.toResponse())
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @Operation(summary = "Create a new bank account")
    fun createAccount(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateAccountRequest,
        @RequestParam(required = false) customerId: UUID?,
        httpRequest: HttpServletRequest
    ): ResponseEntity<AccountResponse> {
        val targetCustomerId = resolveCustomerId(jwt, customerId)
        val command = CreateAccountCommand(
            customerId = targetCustomerId,
            accountType = request.accountType,
            currency = request.currency,
            initialDeposit = request.initialDeposit
        )
        val account = accountService.createAccount(command, httpRequest)
        val response = account.toResponse()
        return ResponseEntity.created(URI.create("/api/v1/accounts/${account.id}")).body(response)
    }

    private fun resolveCustomerId(jwt: Jwt, requestedCustomerId: UUID?): UUID {
        val roles = jwt.getClaimAsStringList("roles") ?: emptyList()
        val isAdmin = roles.contains("ROLE_ADMIN")

        if (isAdmin && requestedCustomerId != null) {
            return requestedCustomerId
        }

        val claimStr = jwt.getClaimAsString("customerId") ?: jwt.subject
        return UUID.fromString(claimStr)
    }

    private fun AccountView.toResponse(): AccountResponse = AccountResponse(
        id = id,
        customerId = customerId,
        accountNumber = accountNumber,
        accountType = accountType,
        status = status,
        currency = currency,
        ledgerBalance = ledgerBalance,
        availableBalance = availableBalance,
        createdAt = createdAt
    )
}
