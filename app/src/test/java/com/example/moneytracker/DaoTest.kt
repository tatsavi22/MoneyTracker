package com.example.moneytracker

import android.content.Context
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.example.moneytracker.data.local.AppDatabase
import com.example.moneytracker.data.local.TransactionDao
import com.example.moneytracker.data.local.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [TransactionDao].
 *
 * Uses plain in-memory Room without SQLCipher for JVM tests (PROJECT_SPEC section 4).
 * Runs on Robolectric to provide an Android [Context].
 */
@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao

    @Before
    fun createDb() {
        val context = RuntimeEnvironment.getApplication()
        // Plain Room without SQLCipher for fast in-memory JVM testing
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `insert and deduplicate - same dedupeKey is ignored`() = runBlocking {
        val entity1 = TransactionEntity(
            amountPaise = 25000,
            isDebit = true,
            party = "shop@upi",
            accountLast4 = "1234",
            senderId = "AX-HDFCBK",
            timestamp = 1000L,
            dedupeKey = "hash-1"
        )
        val rowId1 = dao.insert(entity1)
        assertEquals(1L, rowId1)

        // Try inserting the exact same dedupeKey
        val entityDuplicate = entity1.copy(id = 0, amountPaise = 99999)
        val rowId2 = dao.insert(entityDuplicate)
        assertEquals(-1L, rowId2) // OnConflictStrategy.IGNORE returns -1

        // Verify count remains 1
        assertEquals(1, dao.count())
        val items = dao.getTransactionsInRange(0, 2000).first()
        assertEquals(1, items.size)
        assertEquals(25000L, items[0].amountPaise)
    }

    @Test
    fun `range query returns only matching transactions ordered newest first`() = runBlocking {
        dao.insert(
            TransactionEntity(
                amountPaise = 10000,
                isDebit = true,
                party = "t1@upi",
                accountLast4 = "1111",
                senderId = "AX-HDFCBK",
                timestamp = 100L,
                dedupeKey = "h1"
            )
        )
        dao.insert(
            TransactionEntity(
                amountPaise = 20000,
                isDebit = true,
                party = "t2@upi",
                accountLast4 = "2222",
                senderId = "AX-HDFCBK",
                timestamp = 500L,
                dedupeKey = "h2"
            )
        )
        dao.insert(
            TransactionEntity(
                amountPaise = 30000,
                isDebit = false,
                party = "t3@upi",
                accountLast4 = "3333",
                senderId = "AX-HDFCBK",
                timestamp = 1000L,
                dedupeKey = "h3"
            )
        )

        // Query between 200 and 800
        val inRange = dao.getTransactionsInRange(200L, 800L).first()
        assertEquals(1, inRange.size)
        assertEquals(20000L, inRange[0].amountPaise)

        // Query all between 0 and 2000 (should be ordered newest first)
        val all = dao.getTransactionsInRange(0L, 2000L).first()
        assertEquals(3, all.size)
        assertEquals(1000L, all[0].timestamp) // newest first
        assertEquals(500L, all[1].timestamp)
        assertEquals(100L, all[2].timestamp)
    }

    @Test
    fun `totals calculation in SQL groups debits and credits accurately`() = runBlocking {
        // Debit: 250.00 (25000 paise)
        dao.insert(
            TransactionEntity(
                amountPaise = 25000,
                isDebit = true,
                party = "shop1@upi",
                accountLast4 = "1111",
                senderId = "AX-HDFCBK",
                timestamp = 1000L,
                dedupeKey = "h1"
            )
        )
        // Debit: 150.50 (15050 paise)
        dao.insert(
            TransactionEntity(
                amountPaise = 15050,
                isDebit = true,
                party = "shop2@upi",
                accountLast4 = "1111",
                senderId = "AX-HDFCBK",
                timestamp = 2000L,
                dedupeKey = "h2"
            )
        )
        // Credit: 500.00 (50000 paise)
        dao.insert(
            TransactionEntity(
                amountPaise = 50000,
                isDebit = false,
                party = "payer@upi",
                accountLast4 = "1111",
                senderId = "AX-HDFCBK",
                timestamp = 3000L,
                dedupeKey = "h3"
            )
        )

        val totalDebit = dao.getTotalDebitInRange(0L, 5000L).first()
        val totalCredit = dao.getTotalCreditInRange(0L, 5000L).first()

        assertEquals(40050L, totalDebit) // 25000 + 15050
        assertEquals(50000L, totalCredit) // 50000
    }

    @Test
    fun `deleteAll removes all rows`() = runBlocking {
        dao.insert(
            TransactionEntity(
                amountPaise = 1000,
                isDebit = true,
                party = null,
                accountLast4 = null,
                senderId = "AX-HDFCBK",
                timestamp = 100L,
                dedupeKey = "h1"
            )
        )
        assertEquals(1, dao.count())

        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun `findPotentialDuplicates locates transactions within time window`() = runBlocking {
        dao.insert(
            TransactionEntity(
                amountPaise = 50000,
                isDebit = true,
                party = null,
                accountLast4 = "1234",
                senderId = "AX-HDFCBK",
                timestamp = 100_000L,
                dedupeKey = "k1"
            )
        )
        dao.insert(
            TransactionEntity(
                amountPaise = 50000,
                isDebit = true,
                party = "swiggy@axisbank",
                accountLast4 = "1234",
                senderId = "AX-HDFCBK",
                timestamp = 105_000L,
                dedupeKey = "k2"
            )
        )
        // Different amount
        dao.insert(
            TransactionEntity(
                amountPaise = 20000,
                isDebit = true,
                party = null,
                accountLast4 = "1234",
                senderId = "AX-HDFCBK",
                timestamp = 102_000L,
                dedupeKey = "k3"
            )
        )

        val matches = dao.findPotentialDuplicates(
            amountPaise = 50000,
            isDebit = true,
            minTimestamp = 90_000L,
            maxTimestamp = 110_000L
        )

        assertEquals(2, matches.size)
    }

    @Test
    fun `deleteTransactions removes duplicates cleanly`() = runBlocking {
        val id1 = dao.insert(
            TransactionEntity(
                amountPaise = 15000,
                isDebit = true,
                party = "store@upi",
                accountLast4 = "9999",
                senderId = "AX-SBIN",
                timestamp = 1000L,
                dedupeKey = "d1"
            )
        )
        val id2 = dao.insert(
            TransactionEntity(
                amountPaise = 15000,
                isDebit = true,
                party = null,
                accountLast4 = "9999",
                senderId = "AX-SBIN",
                timestamp = 1010L,
                dedupeKey = "d2"
            )
        )

        val all = dao.getAllTransactions()
        assertEquals(2, all.size)

        // Delete second
        val toDelete = all.filter { it.id == id2 }
        dao.deleteTransactions(toDelete)

        val remaining = dao.getAllTransactions()
        assertEquals(1, remaining.size)
        assertEquals(id1, remaining[0].id)
    }
}
