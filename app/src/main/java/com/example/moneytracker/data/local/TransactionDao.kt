package com.example.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [TransactionEntity] table.
 *
 * All queries that return live data use [Flow] so Room emits updates
 * whenever the underlying data changes (e.g. a new SMS arrives while the
 * app is open).
 *
 * Totals are computed in SQL with SUM() — never in Kotlin — so the DB does
 * the heavy lifting and only the result is transferred to the app process.
 */
@Dao
interface TransactionDao {

    /**
     * Inserts a transaction. Uses IGNORE strategy so a duplicate [dedupeKey]
     * silently does nothing — the same SMS from both the inbox import and the
     * live receiver will produce exactly one row (PROJECT_SPEC section 6).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    /**
     * Updates an existing transaction entity (e.g. to attach party or account details
     * when a complementary SMS arrives for an already recorded transaction).
     */
    @Update
    suspend fun update(entity: TransactionEntity)

    /**
     * Updates multiple transactions.
     */
    @Update
    suspend fun updateTransactions(entities: List<TransactionEntity>)

    /**
     * Deletes multiple transactions. Used during deduplication cleanup.
     */
    @Delete
    suspend fun deleteTransactions(entities: List<TransactionEntity>)

    /**
     * Queries for potential duplicate transactions within a timestamp window.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amountPaise = :amountPaise
          AND isDebit = :isDebit
          AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
        ORDER BY timestamp DESC
        """
    )
    suspend fun findPotentialDuplicates(
        amountPaise: Long,
        isDebit: Boolean,
        minTimestamp: Long,
        maxTimestamp: Long
    ): List<TransactionEntity>

    /**
     * Returns all transactions ordered by timestamp ascending.
     * Used for full-history duplicate cleanup.
     */
    @Query("SELECT * FROM transactions ORDER BY timestamp ASC, id ASC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    /**
     * Returns all transactions whose [TransactionEntity.timestamp] falls
     * within [[startMs], [endMs]], ordered newest first, as a live [Flow].
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :startMs AND :endMs
        ORDER BY timestamp DESC
        """
    )
    fun getTransactionsInRange(startMs: Long, endMs: Long): Flow<List<TransactionEntity>>

    /**
     * Returns the total paise debited (money out) in the given range.
     * Returns 0 if there are no debit transactions.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE timestamp BETWEEN :startMs AND :endMs
        AND isDebit = 1
        """
    )
    fun getTotalDebitInRange(startMs: Long, endMs: Long): Flow<Long>

    /**
     * Returns the total paise credited (money in) in the given range.
     * Returns 0 if there are no credit transactions.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE timestamp BETWEEN :startMs AND :endMs
        AND isDebit = 0
        """
    )
    fun getTotalCreditInRange(startMs: Long, endMs: Long): Flow<Long>

    /**
     * Deletes every row in the transactions table.
     * Used by "Delete all my data" in Settings.
     */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /** Returns the total number of rows — handy for tests. */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
