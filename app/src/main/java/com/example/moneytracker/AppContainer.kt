package com.example.moneytracker

import android.content.Context
import com.example.moneytracker.data.local.AppDatabase
import com.example.moneytracker.data.local.DbKeyProvider
import com.example.moneytracker.data.local.UserPreferences
import com.example.moneytracker.data.repository.TransactionRepository
import com.example.moneytracker.data.sms.SmsInboxReader

/**
 * Manual dependency injection container (PROJECT_SPEC section 4: no Hilt/Dagger).
 *
 * Created once in [MoneyTrackerApp.onCreate] and accessed from Workers and ViewModels.
 * All dependencies are lazy so we don't block the main thread at app start.
 *
 * Initialization of the database is intentionally deferred — it must run on a
 * background thread since [DbKeyProvider.getPassphrase] does cryptographic work.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    // The DB key provider — talks to the Android Keystore
    private val dbKeyProvider = DbKeyProvider(appContext)

    /**
     * The encrypted Room database.
     * Initialized lazily; first access must be from a background thread.
     */
    val database: AppDatabase by lazy {
        val passphrase = dbKeyProvider.getPassphrase()
        val db = AppDatabase.create(appContext, passphrase)
        // Clear the passphrase from memory now that Room / SQLCipher has it
        passphrase.fill(0)
        db
    }

    /** The DAO — depends on [database], so also lazy and background-safe. */
    val transactionDao by lazy { database.transactionDao() }

    /** Central repository — the single point of access for all transaction data. */
    val repository: TransactionRepository by lazy {
        TransactionRepository(transactionDao)
    }

    /** Reads SMS from the system inbox for the initial / re-import flow. */
    val smsInboxReader: SmsInboxReader by lazy {
        SmsInboxReader(appContext.contentResolver, repository)
    }

    /** User preferences (display name, lock toggle). */
    val userPreferences: UserPreferences by lazy {
        UserPreferences(appContext)
    }

    /**
     * Clears the stored DB passphrase and closes the database.
     * Called from "Delete all my data". After this, [database] must NOT be accessed.
     */
    fun wipeData() {
        userPreferences.clear()
        dbKeyProvider.clearStoredPassphrase()
        if (database.isOpen) database.close()
        // Delete the physical DB files so SQLCipher can't open the old encrypted blob
        appContext.deleteDatabase("moneytracker.db")
    }
}
