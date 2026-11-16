package com.example.moneytracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing one financial transaction in the encrypted database.
 *
 * SECURITY NOTES (PROJECT_SPEC section 2):
 *  - The raw SMS body is NEVER stored here.
 *  - Only the last 4 digits of an account number are kept ([accountLast4]).
 *  - Deduplication is done via [dedupeKey] (SHA-256 of "sender|timestamp|body").
 *  - Money is stored as [Long] in paise, never Double or Float (rule 8).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dedupeKey"], unique = true), // prevents duplicate inserts
        Index(value = ["timestamp"]),                 // fast range queries for today/month
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Amount in paise. E.g. Rs 250.50 → 25050. Always positive. */
    val amountPaise: Long,

    /** true = money out (debit), false = money in (credit). */
    val isDebit: Boolean,

    /**
     * UPI handle extracted from the SMS, e.g. "shop@upi".
     * Null when the SMS did not contain a UPI ID.
     */
    val party: String?,

    /**
     * Last 4 digits of the bank account mentioned in the SMS.
     * Never the full account number (PROJECT_SPEC rule 5).
     * Null when not found in the SMS.
     */
    val accountLast4: String?,

    /** SMS sender ID, e.g. "AX-HDFCBK". */
    val senderId: String,

    /** Epoch milliseconds from the SMS date field. */
    val timestamp: Long,

    /**
     * SHA-256 of "sender|timestampMs|smsBody".
     * The body itself is NOT stored — only this hash (rule 3).
     * The UNIQUE index on this column prevents the same SMS being saved twice.
     */
    val dedupeKey: String,
)
