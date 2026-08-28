package com.fincore.transactions.infrastructure

import com.fincore.transactions.domain.Transaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, UUID> {
    fun findByIdempotencyKey(idempotencyKey: UUID): Optional<Transaction>

    @Query(
        """
        SELECT t FROM Transaction t
        WHERE t.sourceAccountId = :accountId OR t.destAccountId = :accountId
        ORDER BY t.createdAt DESC, t.id DESC
        """
    )
    fun findByAccountId(
        @Param("accountId") accountId: UUID,
        pageable: Pageable
    ): Page<Transaction>
}
