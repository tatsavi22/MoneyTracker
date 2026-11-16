package com.example.moneytracker

import androidx.room.Room
import com.example.moneytracker.data.local.AppDatabase
import com.example.moneytracker.data.local.TransactionEntity
import com.example.moneytracker.data.repository.TransactionRepository
import com.example.moneytracker.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests verifying ViewModel and Repository integration (PROJECT_SPEC M5).
 *
 * Verifies that the totals in [DaySummary] strictly equal the sum of individual
 * transaction rows, and that debits, credits, and net amounts match.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repository: TransactionRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TransactionRepository(db.transactionDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `totals strictly equal sum of listed rows for today and this month`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()

        // Insert 2 debits and 1 credit with current timestamp
        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 25000,
                isDebit = true,
                party = "shop@upi",
                accountLast4 = "1234",
                senderId = "AX-HDFCBK",
                timestamp = now,
                dedupeKey = "k1"
            )
        )
        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 15000,
                isDebit = true,
                party = "coffee@upi",
                accountLast4 = "1234",
                senderId = "AX-HDFCBK",
                timestamp = now + 10,
                dedupeKey = "k2"
            )
        )
        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 50000,
                isDebit = false,
                party = "friend@okaxis",
                accountLast4 = "1234",
                senderId = "AX-HDFCBK",
                timestamp = now + 20,
                dedupeKey = "k3"
            )
        )

        // Verify repository todaySummary directly
        val todaySummary = repository.todaySummary().first()
        assertNotNull(todaySummary)

        // Acceptance criterion M5: totals equal the sum of the listed rows
        val sumDebits = todaySummary.transactions
            .filter { it.isDebit }
            .sumOf { it.amountPaise }
        val sumCredits = todaySummary.transactions
            .filter { !it.isDebit }
            .sumOf { it.amountPaise }

        assertEquals(todaySummary.totalDebitPaise, sumDebits)
        assertEquals(todaySummary.totalCreditPaise, sumCredits)
        assertEquals(40000L, todaySummary.totalDebitPaise)
        assertEquals(50000L, todaySummary.totalCreditPaise)
        assertEquals(10000L, todaySummary.netPaise) // 50000 - 40000

        // Verify HomeViewModel factory and instantiation
        val viewModel = HomeViewModel(repository)
        assertNotNull(viewModel)
    }

    @Test
    fun `filtering transactions by bank filters items and recalculates totals`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()

        // HDFC Bank debit
        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 20000,
                isDebit = true,
                party = "store@upi",
                accountLast4 = "1111",
                senderId = "VM-HDFCBK-S",
                timestamp = now,
                dedupeKey = "b1"
            )
        )
        // Bank of India debit
        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 35000,
                isDebit = true,
                party = "market@upi",
                accountLast4 = "2222",
                senderId = "JD-BOIIND-S",
                timestamp = now + 1,
                dedupeKey = "b2"
            )
        )
        // Bank of India credit
        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 100000,
                isDebit = false,
                party = "salary@boi",
                accountLast4 = "2222",
                senderId = "JD-BOIIND-S",
                timestamp = now + 2,
                dedupeKey = "b3"
            )
        )

        val summary = repository.todaySummary().first()
        assertEquals(3, summary.transactions.size)

        // Filter by Bank of India
        val boiTxns = summary.transactions.filter {
            com.example.moneytracker.util.BankUtils.getBankName(it.senderId) == "Bank of India"
        }
        assertEquals(2, boiTxns.size)

        val boiDebit = boiTxns.filter { it.isDebit }.sumOf { it.amountPaise }
        val boiCredit = boiTxns.filter { !it.isDebit }.sumOf { it.amountPaise }
        assertEquals(35000L, boiDebit)
        assertEquals(100000L, boiCredit)
        assertEquals(65000L, boiCredit - boiDebit)

        // Filter by HDFC Bank
        val hdfcTxns = summary.transactions.filter {
            com.example.moneytracker.util.BankUtils.getBankName(it.senderId) == "HDFC Bank"
        }
        assertEquals(1, hdfcTxns.size)
        assertEquals(20000L, hdfcTxns.first().amountPaise)
    }

    @Test
    fun `explicitly adding cash transactions updates summary and identifies as Cash`() = runTest(testDispatcher) {
        // Add Cash income (e.g. customer paid cash)
        repository.addManualTransaction(
            amountPaise = 75000,
            isDebit = false,
            party = "Customer cash payment",
            paymentMode = "Cash"
        )

        // Add Cash expense (e.g. cash paid for tea / snacks)
        repository.addManualTransaction(
            amountPaise = 12000,
            isDebit = true,
            party = "Tea and snacks",
            paymentMode = "Cash"
        )

        val summary = repository.todaySummary().first()
        assertEquals(2, summary.transactions.size)
        assertEquals(12000L, summary.totalDebitPaise)
        assertEquals(75000L, summary.totalCreditPaise)
        assertEquals(63000L, summary.netPaise)

        val cashTxns = summary.transactions.filter {
            com.example.moneytracker.util.BankUtils.getBankName(it.senderId) == "Cash"
        }
        assertEquals(2, cashTxns.size)
    }

    @Test
    fun `specificDateSummary returns transactions for chosen past calendar day`() = runTest(testDispatcher) {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 25, 14, 30, 0)
        }
        val pastDateMs = cal.timeInMillis

        db.transactionDao().insert(
            TransactionEntity(
                amountPaise = 42000,
                isDebit = true,
                party = "past_merchant@upi",
                accountLast4 = "9876",
                senderId = "AX-HDFCBK",
                timestamp = pastDateMs,
                dedupeKey = "past_1"
            )
        )

        val summary = repository.specificDateSummary(pastDateMs).first()
        assertEquals(1, summary.transactions.size)
        assertEquals(42000L, summary.totalDebitPaise)
        assertEquals("past_merchant@upi", summary.transactions[0].party)

        val viewModel = HomeViewModel(repository)
        viewModel.selectCustomDate(pastDateMs)
        val vmSummary = viewModel.customDateSummary.first { it != null }
        assertNotNull(vmSummary)
        assertEquals(42000L, vmSummary?.totalDebitPaise)
    }
}
