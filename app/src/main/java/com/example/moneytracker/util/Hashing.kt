package com.example.moneytracker.util

import java.security.MessageDigest

/**
 * Utility for computing a one-way SHA-256 hash used as the deduplication key
 * for transactions (PROJECT_SPEC rule 3 and section 6).
 *
 * The hash input is "sender|timestampMillis|smsBody". The body is used only
 * during hash computation — it is NEVER stored anywhere in the app.
 */
object Hashing {

    /**
     * Computes the deduplication key for a transaction.
     *
     * In Indian banking SMS, every completed transaction message contains unique
     * metadata (timestamp/date, UTR/reference number, updated balance).
     * Normalizing sender and body ensures the same SMS produces an identical key
     * whether ingested via real-time [SmsReceiver] or [SmsInboxReader], even when
     * their millisecond arrival timestamps slightly differ.
     *
     * @param senderId  The SMS sender ID (e.g. "AX-HDFCBK").
     * @param timestampMs  The SMS timestamp in epoch milliseconds.
     * @param body  The raw SMS body. Used only for hashing; never persisted.
     * @return  A lowercase hex-encoded SHA-256 string.
     */
    fun dedupeKey(senderId: String, timestampMs: Long, body: String): String {
        val cleanSender = senderId.trim().uppercase()
        val cleanBody = body.trim()
        val input = "$cleanSender|$cleanBody"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
