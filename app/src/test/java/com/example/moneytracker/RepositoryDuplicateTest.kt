package com.example.moneytracker

import androidx.room.Room
import com.example.moneytracker.data.local.AppDatabase
import com.example.moneytracker.data.local.TransactionDao
import com.example.moneytracker.data.local.TransactionEntity
import com.example.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RepositoryDuplicateTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.transactionDao()
        repository = TransactionRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `handleSms rejects duplicate transaction within 3 minutes and merges richer party info`() = runBlocking {
        val timestamp = 1731770000000L

        // SMS 1: Generic bank debit without merchant/party
        val sms1 = "Dear Customer, INR 250.00 debited from A/C XX1234 on 16-Nov-24 by UPI ref 432109876543."
        val inserted1 = repository.handleSms("AX-HDFCBK", sms1, timestamp)
        assertTrue(inserted1)
        assertEquals(1, dao.count())

        val txnsBefore = dao.getAllTransactions()
        assertEquals(null, txnsBefore[0].party)
        assertEquals("1234", txnsBefore[0].accountLast4)

        // SMS 2: 15 seconds later, UPI payment notification with merchant handle
        val sms2 = "Sent Rs. 250.00 from HDFC Bank A/C 1234 to SWIGGY UPI: swiggy@axisbank on 16-Nov-24."
        val inserted2 = repository.handleSms("AX-HDFCBK", sms2, timestamp + 15_000L)

        // Must be identified as duplicate and rejected
        assertFalse(inserted2)

        // Total count must still be 1 (NO duplicate row!)
        assertEquals(1, dao.count())

        // And the existing transaction must be updated with the richer party "swiggy@axisbank"
        val txnsAfter = dao.getAllTransactions()
        assertEquals(1, txnsAfter.size)
        assertEquals("swiggy@axisbank", txnsAfter[0].party)
        assertEquals(25000L, txnsAfter[0].amountPaise)
    }

    @Test
    fun `cleanupExistingDuplicates removes historical duplicates and corrects total sum`() = runBlocking {
        val timestamp = 1731770000000L

        // Simulate two duplicate rows already inserted previously in the database
        // Row 1: inserted by SmsReceiver
        dao.insert(
            TransactionEntity(
                amountPaise = 50000,
                isDebit = true,
                party = "zomato@icici",
                accountLast4 = "5555",
                senderId = "AX-HDFCBK",
                timestamp = timestamp,
                dedupeKey = "receiver_hash"
            )
        )

        // Row 2: inserted by ContentResolver inbox reader (slightly different timestamp and dedupeKey)
        dao.insert(
            TransactionEntity(
                amountPaise = 50000,
                isDebit = true,
                party = null,
                accountLast4 = "5555",
                senderId = "AX-HDFCBK",
                timestamp = timestamp + 350L,
                dedupeKey = "inbox_hash"
            )
        )

        assertEquals(2, dao.count())
        val debitBefore = dao.getTotalDebitInRange(timestamp - 1000, timestamp + 5000).first()
        assertEquals(100000L, debitBefore) // 500 + 500 = 1000 (wrong duplicate sum)

        // Run cleanup
        val cleanedCount = repository.cleanupExistingDuplicates()
        assertEquals(1, cleanedCount)

        // Verify only 1 remains, with correct data and sum
        assertEquals(1, dao.count())
        val all = dao.getAllTransactions()
        assertEquals(1, all.size)
        assertEquals("zomato@icici", all[0].party)
        assertEquals("5555", all[0].accountLast4)

        val debitAfter = dao.getTotalDebitInRange(timestamp - 1000, timestamp + 5000).first()
        assertEquals(50000L, debitAfter) // 500 (corrected sum)
    }

    @Test
    fun `two distinct transactions with different amounts or outside 3 minutes are preserved`() = runBlocking {
        val timestamp = 1731770000000L

        // Txn 1: Rs 100
        val sms1 = "Rs 100 debited from A/C XX1234 on 16-Nov-24 to shop1@upi"
        assertTrue(repository.handleSms("AX-HDFCBK", sms1, timestamp))

        // Txn 2: Rs 100, but 10 minutes later (e.g. bought second item)
        val sms2 = "Rs 100 debited from A/C XX1234 on 16-Nov-24 to shop1@upi. Avl Bal 4800"
        assertTrue(repository.handleSms("AX-HDFCBK", sms2, timestamp + 600_000L))

        assertEquals(2, dao.count())
        val cleaned = repository.cleanupExistingDuplicates()
        assertEquals(0, cleaned)
        assertEquals(2, dao.count())
    }
}
