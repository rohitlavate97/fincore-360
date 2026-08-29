package com.fincore.transactions.infrastructure

import com.fincore.transactions.domain.LedgerDirection
import com.fincore.transactions.domain.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.UUID

@Repository
interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {
    fun findAllByTransactionId(transactionId: UUID): List<LedgerEntry>
    fun findAllByAccountIdOrderByCreatedAtDesc(accountId: UUID): List<LedgerEntry>

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.direction = :direction")
    fun sumAmountByDirection(@Param("direction") direction: LedgerDirection): BigDecimal

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.transactionId = :transactionId AND l.direction = :direction")
    fun sumAmountByTransactionIdAndDirection(
        @Param("transactionId") transactionId: UUID,
        @Param("direction") direction: LedgerDirection
    ): BigDecimal
}
