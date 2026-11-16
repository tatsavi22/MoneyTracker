package com.example.moneytracker.domain

/**
 * Aggregated totals for a single day or for the current month.
 * Returned by the repository and displayed on the Home screen.
 *
 * All amounts are in paise.
 */
data class DaySummary(
    /** Total paise debited (money out) in the period. */
    val totalDebitPaise: Long,
    /** Total paise credited (money in) in the period. */
    val totalCreditPaise: Long,
    /** All individual transactions in the period, newest first. */
    val transactions: List<Transaction>,
) {
    /** Net paise = credits − debits. Negative means overall spending exceeded income. */
    val netPaise: Long get() = totalCreditPaise - totalDebitPaise
}
