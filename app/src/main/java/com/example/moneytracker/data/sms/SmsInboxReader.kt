package com.example.moneytracker.data.sms

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.example.moneytracker.data.repository.TransactionRepository

/**
 * Reads existing SMS from the device inbox using [ContentResolver].
 *
 * This is used by [ImportInboxWorker] to perform the initial (or manual
 * re-import) scan of the last 90 days of SMS.
 *
 * IMPORTANT: Only parses SMS from sender IDs that look like bank short codes.
 * Personal phone numbers are ignored at the [SmsParser] level.
 *
 * SECURITY: This class reads SMS bodies temporarily to pass them to [SmsParser].
 * The bodies are never stored, logged, or held in any long-lived reference
 * (PROJECT_SPEC rules 3 and 4).
 */
class SmsInboxReader(
    private val contentResolver: ContentResolver,
    private val repository: TransactionRepository,
) {
    companion object {
        private const val TAG = "SmsInboxReader"
        private const val SMS_URI = "content://sms/inbox"
        private const val NINETY_DAYS_MS = 90L * 24 * 60 * 60 * 1000
    }

    /**
     * Reads all SMS from the last 90 days, filters by bank sender pattern,
     * parses each one, and inserts the parsed transactions into the repository.
     *
     * @return  The number of transactions successfully inserted (duplicates not counted).
     */
    suspend fun importLast90Days(): Int {
        val cutoffMs = System.currentTimeMillis() - NINETY_DAYS_MS
        var insertedCount = 0

        val cursor: Cursor? = try {
            contentResolver.query(
                Uri.parse(SMS_URI),
                arrayOf("address", "date", "date_sent", "body"),
                "date >= ?",
                arrayOf(cutoffMs.toString()),
                "date DESC"
            )
        } catch (_: Exception) {
            contentResolver.query(
                Uri.parse(SMS_URI),
                arrayOf("address", "date", "body"),
                "date >= ?",
                arrayOf(cutoffMs.toString()),
                "date DESC"
            )
        }

        cursor?.use { c ->
            val addrIdx = c.getColumnIndexOrThrow("address")
            val dateIdx = c.getColumnIndexOrThrow("date")
            val dateSentIdx = c.getColumnIndex("date_sent")
            val bodyIdx = c.getColumnIndexOrThrow("body")

            while (c.moveToNext()) {
                val senderId = c.getString(addrIdx) ?: continue
                val dateSent = if (dateSentIdx >= 0 && !c.isNull(dateSentIdx)) c.getLong(dateSentIdx) else 0L
                val date = c.getLong(dateIdx)
                val timestamp = if (dateSent > 0) dateSent else date
                // The body is a local variable — it goes out of scope and is GC-eligible
                // as soon as we're done with this iteration. Never stored or logged.
                val body = c.getString(bodyIdx) ?: continue

                val inserted = repository.handleSms(senderId, body, timestamp)
                if (inserted) insertedCount++
            }
        }

        Log.d(TAG, "Inbox import complete. Inserted $insertedCount new transactions.")
        return insertedCount
    }
}
