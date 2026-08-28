package com.fincore.transactions.domain

import com.fincore.shared.error.InvalidStateTransitionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TransactionStateMachineTest {

    private fun createTx(status: TransactionStatus = TransactionStatus.PENDING): Transaction {
        return Transaction(
            amount = BigDecimal("100.0000"),
            currency = "GBP",
            status = status
        )
    }

    @Test
    fun `valid transition lifecycle PENDING to PROCESSING to COMPLETED to REVERSED`() {
        val tx = createTx()
        assertEquals(TransactionStatus.PENDING, tx.status)

        tx.transitionTo(TransactionStatus.PROCESSING)
        assertEquals(TransactionStatus.PROCESSING, tx.status)

        tx.transitionTo(TransactionStatus.COMPLETED)
        assertEquals(TransactionStatus.COMPLETED, tx.status)

        tx.transitionTo(TransactionStatus.REVERSED)
        assertEquals(TransactionStatus.REVERSED, tx.status)
    }

    @Test
    fun `valid transition PENDING to CANCELLED`() {
        val tx = createTx()
        tx.transitionTo(TransactionStatus.CANCELLED)
        assertEquals(TransactionStatus.CANCELLED, tx.status)
    }

    @Test
    fun `valid transition PROCESSING to FAILED`() {
        val tx = createTx(TransactionStatus.PROCESSING)
        tx.transitionTo(TransactionStatus.FAILED)
        assertEquals(TransactionStatus.FAILED, tx.status)
    }

    @Test
    fun `invalid transition PENDING to COMPLETED throws InvalidStateTransitionException`() {
        val tx = createTx(TransactionStatus.PENDING)
        assertThrows(InvalidStateTransitionException::class.java) {
            tx.transitionTo(TransactionStatus.COMPLETED)
        }
    }

    @Test
    fun `terminal status CANCELLED cannot transition throws InvalidStateTransitionException`() {
        val tx = createTx(TransactionStatus.CANCELLED)
        assertThrows(InvalidStateTransitionException::class.java) {
            tx.transitionTo(TransactionStatus.COMPLETED)
        }
    }

    @Test
    fun `terminal status FAILED cannot transition throws InvalidStateTransitionException`() {
        val tx = createTx(TransactionStatus.FAILED)
        assertThrows(InvalidStateTransitionException::class.java) {
            tx.transitionTo(TransactionStatus.COMPLETED)
        }
    }
}
