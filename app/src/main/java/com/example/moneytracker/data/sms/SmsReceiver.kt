package com.example.moneytracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.moneytracker.work.ProcessSmsWorker

/**
 * Listens for new incoming SMS messages.
 *
 * This receiver does the absolute minimum — Android kills a receiver that
 * takes more than ~10 seconds. We validate the sender and immediately
 * hand off to [ProcessSmsWorker] via WorkManager.
 *
 * The receiver is declared in AndroidManifest with:
 *   android:permission="android.permission.BROADCAST_SMS"
 * so only the system (not other apps) can trigger it.
 *
 * SECURITY: The SMS body is passed to WorkManager only as opaque input data
 * and is processed (and discarded) in the Worker. It is never logged here
 * (PROJECT_SPEC rule 4).
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"

        // Keys matching what ProcessSmsWorker.kt expects
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_TIMESTAMP = "timestamp"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Retrieve all parts of the message (handles multi-part / dual-SIM SMS)
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group parts by sender (all parts of one message share the same originating address)
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        grouped.forEach { (sender, parts) ->
            if (sender.isBlank()) return@forEach

            // Join all parts in order to reconstruct the full SMS body
            val body = parts.joinToString("") { it.messageBody ?: "" }
            if (body.isBlank()) return@forEach

            // Use the timestamp of the first part
            val timestamp = parts.first().timestampMillis

            Log.d(TAG, "Received bank SMS — enqueueing worker.")

            // Enqueue the work — the actual parsing and insertion happens there
            val inputData = Data.Builder()
                .putString(KEY_SENDER, sender)
                .putString(KEY_BODY, body)   // body is passed to the worker, never logged
                .putLong(KEY_TIMESTAMP, timestamp)
                .build()

            val request = OneTimeWorkRequestBuilder<ProcessSmsWorker>()
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
