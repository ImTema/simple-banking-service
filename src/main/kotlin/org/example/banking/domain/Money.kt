package org.example.banking.domain

import java.math.BigDecimal
import java.util.Currency

data class Money(val amount: BigDecimal, val currency: Currency) : Comparable<Money> {

    init {
        require(amount >= BigDecimal.ZERO) { "Money amount must not be negative: $amount" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount - other.amount, currency)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    override fun equals(other: Any?): Boolean =
        other is Money && currency == other.currency && amount.compareTo(other.amount) == 0

    override fun hashCode(): Int = currency.hashCode() * 31 + amount.stripTrailingZeros().hashCode()

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
    }
}
