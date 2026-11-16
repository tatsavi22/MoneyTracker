package com.example.moneytracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The Room database for MoneyTracker.
 *
 * Encrypted with SQLCipher using a passphrase from [DbKeyProvider].
 * The database file is never readable by a plain SQLite tool (PROJECT_SPEC
 * security check 5).
 *
 * Version history:
 *   1 — initial schema (TransactionEntity)
 */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false // no schema export directory needed for a single-user app
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val DB_NAME = "moneytracker.db"

        /**
         * Builds the encrypted [AppDatabase].
         *
         * [passphrase] is consumed here and should be cleared from memory by
         * the caller after this returns (the [SupportFactory] copies it internally).
         *
         * For tests, use [Room.inMemoryDatabaseBuilder] with a plain (unencrypted)
         * setup — SQLCipher is not needed in a JVM test environment.
         */
        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            try {
                System.loadLibrary("sqlcipher")
            } catch (_: UnsatisfiedLinkError) {
                // Handled for test environments
            }
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
