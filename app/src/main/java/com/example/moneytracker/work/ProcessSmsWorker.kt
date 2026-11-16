package com.example.moneytracker.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.moneytracker.MoneyTrackerApp
import com.example.moneytracker.data.sms.SmsReceiver

/**
 * WorkManager worker that processes a single new incoming SMS.
 *
 * Triggered by [SmsReceiver] when a new SMS arrives. Runs off the main thread
 * in a coroutine, so it can call `suspend` repository methods safely.
 *
 * Input data keys: [SmsReceiver.KEY_SENDER], [SmsReceiver.KEY_BODY],
 *                  [SmsReceiver.KEY_TIMESTAMP]
 *
 * SECURITY: The SMS body arrives as WorkManager input data and is used only
 * to call [repository.handleSms] — it is never logged or persisted
 * (PROJECT_SPEC rules 3 and 4).
 */
class ProcessSmsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ProcessSmsWorker"
    }

    override suspend fun doWork(): Result {
        val sender = inputData.getString(SmsReceiver.KEY_SENDER) ?: return Result.failure()
        val body = inputData.getString(SmsReceiver.KEY_BODY) ?: return Result.failure()
        val timestamp = inputData.getLong(SmsReceiver.KEY_TIMESTAMP, -1L)
        if (timestamp < 0) return Result.failure()

        val app = applicationContext as MoneyTrackerApp
        val inserted = app.container.repository.handleSms(sender, body, timestamp)

        Log.d(TAG, "ProcessSmsWorker done. inserted=$inserted")
        return Result.success()
    }
}
