package com.fincore.transactions.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class LedgerDirection {
    DEBIT,
    CREDIT
}

/**
 * Immutable double-entry bookkeeping ledger entry (M-7).
 * Every completed financial transfer writes two paired entries:
 * 1. DEBIT on the source account with the post-transfer source running balance
 * 2. CREDIT on the destination account with the post-transfer destination running balance
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "transaction_id", nullable = false)
    val transactionId: UUID,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6)
    val direction: LedgerDirection,

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 4)
    val runningBalance: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
