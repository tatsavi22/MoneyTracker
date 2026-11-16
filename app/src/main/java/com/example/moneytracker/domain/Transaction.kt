package com.example.moneytracker.domain

/**
 * Immutable domain model for a single financial transaction.
 * This is the object ViewModels and UI deal with; it is mapped from [TransactionEntity].
 *
 * Money is always stored as Long in paise (1 rupee = 100 paise) so arithmetic
 * is exact with no floating-point rounding (PROJECT_SPEC rule 8).
 */
data class Transaction(
    val id: Long,
    /** Amount in paise. Always positive; use [isDebit] for direction. */
    val amountPaise: Long,
    /** true = money out (debit), false = money in (credit). */
    val isDebit: Boolean,
    /** UPI handle like "shop@upi", or null if the SMS didn't have one. */
    val party: String?,
    /** Last 4 digits of the bank account, or null. Never the full number. */
    val accountLast4: String?,
    /** The SMS sender ID, e.g. "AX-HDFCBK". */
    val senderId: String,
    /** When the transaction happened, as epoch milliseconds. */
    val timestamp: Long,
)
