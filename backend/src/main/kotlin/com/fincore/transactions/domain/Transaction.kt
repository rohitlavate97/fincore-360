package com.fincore.transactions.domain

import com.fincore.shared.error.InvalidStateTransitionException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import java.math.BigDecimal
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "transactions")
class Transaction(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "idempotency_key")
    val idempotencyKey: UUID? = null,

    @Column(name = "source_account_id")
    val sourceAccountId: UUID? = null,

    @Column(name = "dest_account_id")
    val destAccountId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: TransactionType = TransactionType.TRANSFER,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TransactionStatus = TransactionStatus.PENDING,

    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @JdbcTypeCode(Types.CHAR)
    @Column(nullable = false, length = 3)
    val currency: String = "GBP",

    @Column(name = "created_by")
    val createdBy: UUID? = null,

    @Column(name = "correlation_id")
    val correlationId: UUID? = null,

    @Version
    @Column(nullable = false)
    val version: Long = 0L,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    init {
        require(amount > BigDecimal.ZERO) { "Transaction amount must be strictly positive" }
        require(currency.length == 3) { "Currency must be a 3-character ISO-4217 code" }
    }

    fun transitionTo(newStatus: TransactionStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw InvalidStateTransitionException(status.name, newStatus.name)
        }
        status = newStatus
        updatedAt = Instant.now()
    }
}
