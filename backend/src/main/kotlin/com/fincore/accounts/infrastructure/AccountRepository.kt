package com.fincore.accounts.infrastructure

import com.fincore.accounts.domain.Account
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByCustomerId(customerId: UUID, pageable: Pageable): Page<Account>
    fun findAllByCustomerId(customerId: UUID): List<Account>
    fun findByIdAndCustomerId(id: UUID, customerId: UUID): Optional<Account>
    fun existsByAccountNumber(accountNumber: String): Boolean
}
