package com.fincore.accounts.infrastructure

import com.fincore.accounts.domain.Account
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByCustomerId(customerId: UUID, pageable: Pageable): Page<Account>
    fun findAllByCustomerId(customerId: UUID): List<Account>
    fun findByIdAndCustomerId(id: UUID, customerId: UUID): Optional<Account>
    fun existsByAccountNumber(accountNumber: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Optional<Account>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id ASC")
    fun findAllByIdInForUpdate(@Param("ids") ids: Collection<UUID>): List<Account>
}
