package com.fincore.shared.money

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * ADR-012 verification. These are the tests that prove the monetary
 * representation rule holds — not a document asserting that it does.
 */
class MoneyTest {

    @Test
    @DisplayName("repeated addition of 0.1 is exact — the defect this design exists to prevent")
    fun repeatedAdditionIsExact() {
        // In IEEE-754 double arithmetic, summing 0.1 ten times yields
        // 0.9999999999999999, and the error compounds across a ledger.
        var total = Money.zero("GBP")
        repeat(10) { total += Money.of("0.10", "GBP") }

        assertEquals(0, total.amount.compareTo(BigDecimal("1.00")))
        assertEquals("1.0000", total.amountAsString())
    }

    @Test
    @DisplayName("0.1 + 0.2 == 0.3 exactly")
    fun classicFloatingPointCase() {
        val sum = Money.of("0.1", "GBP") + Money.of("0.2", "GBP")
        assertEquals(0, sum.amount.compareTo(BigDecimal("0.3")))
    }

    @Test
    @DisplayName("a debit then credit returns the balance to its start value")
    fun debitCreditRoundTrip() {
        val start = Money.of("1000.0000", "GBP")
        val amount = Money.of("333.3333", "GBP")
        assertEquals(start, start - amount + amount)
    }

    @Test
    @DisplayName("amounts are normalised to scale 4, matching NUMERIC(19,4)")
    fun scaleIsAlwaysFour() {
        assertEquals("5.0000", Money.of("5", "GBP").amountAsString())
        assertEquals("5.5000", Money.of("5.5", "GBP").amountAsString())
        assertEquals(Money.SCALE, Money.of("1", "GBP").amount.scale())
    }

    @Test
    @DisplayName("equality compares value, not scale — the BigDecimal.equals trap")
    fun equalityIgnoresScale() {
        // BigDecimal("1.0").equals(BigDecimal("1.00")) is FALSE because equals
        // compares scale. Money must not inherit that behaviour.
        assertNotEquals(BigDecimal("1.0"), BigDecimal("1.00"))
        assertEquals(Money.of("1.0", "GBP"), Money.of("1.00", "GBP"))
    }

    @Test
    @DisplayName("currency is validated and normalised")
    fun currencyValidation() {
        assertEquals("GBP", Money.of("1", "gbp").currencyCode())
        assertThrows(IllegalArgumentException::class.java) { Money.of("1", "GB") }
        assertThrows(IllegalArgumentException::class.java) { Money.of("1", "GBPP") }
        assertThrows(IllegalArgumentException::class.java) { Money.of("1", "12") }
    }

    @Test
    @DisplayName("mixing currencies is rejected rather than silently coerced")
    fun cannotMixCurrencies() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of("1", "GBP") + Money.of("1", "USD")
        }
    }

    @Test
    fun negativeAndZeroDetection() {
        assertTrue(Money.of("-0.0001", "GBP").isNegative())
        assertTrue(Money.zero("GBP").isZero())
        assertTrue(!Money.of("0.0001", "GBP").isNegative())
    }

    @Test
    @DisplayName("large values within NUMERIC(19,4) range survive intact")
    fun largeValuePrecision() {
        val large = "999999999999999.9999"
        assertEquals(large, Money.of(large, "GBP").amountAsString())
    }
}
