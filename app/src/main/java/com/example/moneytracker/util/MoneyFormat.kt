package com.example.moneytracker.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Formats paise amounts into Indian-locale currency strings.
 *
 * Uses the "en-IN" locale which produces groupings like ₹1,25,000.50
 * rather than the Western ₹125,000.50.
 *
 * NEVER use Double or Float for monetary arithmetic — only convert to
 * decimal at the final formatting step (PROJECT_SPEC rule 8).
 */
object MoneyFormat {

    private val indiaLocale = Locale("en", "IN")

    /**
     * Formats [paise] as a rupee string with ₹ prefix and Indian grouping.
     * Example: 125050 → "₹1,250.50"
     */
    fun format(paise: Long): String {
        val rupees = paise.toBigDecimal().movePointLeft(2)
        val formatter = NumberFormat.getCurrencyInstance(indiaLocale)
        // NumberFormat with en-IN already uses ₹ symbol
        return formatter.format(rupees)
    }

    /**
     * Formats with an explicit sign prefix: "−" for debits, "+" for credits.
     * The sign is ALWAYS shown so the UI never relies on colour alone
     * (PROJECT_SPEC section 9: "Never rely on colour alone").
     */
    fun formatSigned(paise: Long, isDebit: Boolean): String {
        val sign = if (isDebit) "−" else "+"
        return "$sign ${format(paise)}"
    }
}
