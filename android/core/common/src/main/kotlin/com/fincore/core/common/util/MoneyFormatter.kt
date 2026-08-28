package com.fincore.core.common.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

object MoneyFormatter {
    fun format(amount: BigDecimal): String {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        return format.format(amount.setScale(4, java.math.RoundingMode.HALF_UP))
    }
}
