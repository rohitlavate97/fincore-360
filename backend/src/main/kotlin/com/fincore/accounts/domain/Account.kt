package com.fincore.accounts.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import java.sql.Types
import java.util.UUID

@Entity
@Table(name = "accounts")
class Account(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    val accountNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    var accountType: AccountType = AccountType.CHECKING,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: AccountStatus = AccountStatus.ACTIVE,

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "GBP",

    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 4)
    var ledgerBalance: BigDecimal = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY),

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    var availableBalance: BigDecimal = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY),

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun isActive(): Boolean = status == AccountStatus.ACTIVE

    fun isFrozen(): Boolean = status == AccountStatus.FROZEN

    fun isClosed(): Boolean = status == AccountStatus.CLOSED
}
