package com.fincore.transactions.domain

enum class TransactionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REVERSED;

    fun canTransitionTo(next: TransactionStatus): Boolean {
        return when (this) {
            PENDING -> next == PROCESSING || next == CANCELLED
            PROCESSING -> next == COMPLETED || next == FAILED
            COMPLETED -> next == REVERSED
            CANCELLED, FAILED, REVERSED -> false
        }
    }
}
