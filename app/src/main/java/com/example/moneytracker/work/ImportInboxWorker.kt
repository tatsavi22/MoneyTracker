package com.example.moneytracker.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.moneytracker.MoneyTrackerApp

/**
 * WorkManager worker for the initial inbox import and manual re-import.
 *
 * Reads the last 90 days of SMS from the system inbox via [SmsInboxReader].
 * This runs as a one-time job, enqueued:
 *  - On first launch, after the user grants READ_SMS permission.
 *  - When the user taps "Import again" in Settings.
 *
 * WorkManager retries automatically on failure (e.g. permission revoked
 * mid-run will just cause this to return [Result.failure]).
 */
class ImportInboxWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ImportInboxWorker"
        /** Unique work name so we don't enqueue duplicate import jobs. */
        const val WORK_NAME = "import_inbox"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyTrackerApp
        return try {
            val count = app.container.smsInboxReader.importLast90Days()
            val cleaned = app.container.repository.cleanupExistingDuplicates()
            Log.d(TAG, "Inbox import finished. $count transactions inserted, $cleaned duplicates cleaned.")
            Result.success()
        } catch (e: SecurityException) {
            // READ_SMS permission was not granted or was revoked
            Log.d(TAG, "Import failed — READ_SMS permission not available.")
            Result.failure()
        }
    }
}
