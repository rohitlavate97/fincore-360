package com.fincore.shared.money

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A monetary amount and its currency, inseparably.
 *
 * ADR-012 in full:
 *   BigDecimal (here) -> NUMERIC(19,4) (PostgreSQL) -> JSON **string** (wire)
 *
 * `double`/`float` are banned for money. `0.1 + 0.2 == 0.30000000000000004` in
 * binary floating point, and in a ledger that error compounds until balances
 * stop reconciling.
 *
 * Scale 4, not 2, so intermediate results (interest, fee apportionment, FX) have
 * room below the minor unit before a final rounding step.
 *
 * The amount serialises as a JSON **string**. A JSON number would be parsed into
 * an IEEE-754 double by any JavaScript client, destroying in transit exactly the
 * precision protected everywhere else.
 */
class Money private constructor(
    val amount: BigDecimal,
    val currency: String,
) : Comparable<Money> {

    @JsonProperty("amount")
    fun amountAsString(): String = amount.toPlainString()

    @JsonProperty("currency")
    fun currencyCode(): String = currency

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.subtract(other.amount), currency)
    }

    fun isNegative(): Boolean = amount.signum() < 0

    fun isZero(): Boolean = amount.signum() == 0

    private fun requireSameCurrency(other: Money) =
        require(currency == other.currency) {
            "Cannot combine $currency with ${other.currency}"
        }

    /**
     * Ordering by value. Note this deliberately does not compare scale — see
     * [equals].
     */
    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    /**
     * Value equality by *amount*, not by scale.
     *
     * This is the BigDecimal trap: `BigDecimal("1.0").equals(BigDecimal("1.00"))`
     * is false, because BigDecimal.equals compares scale as well as value. Since
     * every Money is normalised to scale 4 at construction the distinction is
     * moot here, but compareTo is used regardless so the class stays correct if
     * that ever changes.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currency == other.currency && amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int = 31 * currency.hashCode() + amount.stripTrailingZeros().hashCode()

    override fun toString(): String = "${amount.toPlainString()} $currency"

    companion object {
        /** Matches NUMERIC(19,4). See DATABASE-DESIGN.md §1. */
        const val SCALE: Int = 4

        /**
         * Rounding must always be explicit. An unspecified non-terminating
         * division throws ArithmeticException rather than silently rounding.
         */
        val ROUNDING: RoundingMode = RoundingMode.HALF_EVEN

        fun of(amount: BigDecimal, currency: String): Money =
            Money(amount.setScale(SCALE, ROUNDING), validateCurrency(currency))

        /**
         * The safe construction path from text.
         *
         * Note there is deliberately no `of(Double)` overload. `BigDecimal(0.1)`
         * inherits the binary representation error you were trying to avoid, so
         * the type system is used to make that mistake unavailable.
         */
        @JvmStatic
        @JsonCreator
        fun of(
            @JsonProperty("amount") amount: String,
            @JsonProperty("currency") currency: String,
        ): Money = of(BigDecimal(amount), currency)

        fun zero(currency: String): Money = of(BigDecimal.ZERO, currency)

        private fun validateCurrency(currency: String): String {
            require(currency.length == 3 && currency.all { it.isLetter() }) {
                "Currency must be a 3-letter ISO 4217 code, got '$currency'"
            }
            return currency.uppercase()
        }
    }
}
