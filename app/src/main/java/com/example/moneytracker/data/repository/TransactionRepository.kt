package com.example.moneytracker.data.repository

import com.example.moneytracker.data.local.TransactionDao
import com.example.moneytracker.data.local.TransactionEntity
import com.example.moneytracker.data.sms.SmsParser
import com.example.moneytracker.domain.DaySummary
import com.example.moneytracker.domain.Transaction
import com.example.moneytracker.util.BankUtils
import com.example.moneytracker.util.DateRanges
import com.example.moneytracker.util.Hashing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Single source of truth for all transaction data.
 *
 * The repository sits between the UI/ViewModels and the data sources (Room DB
 * and SMS parsing). ViewModels never touch [TransactionDao] or [SmsParser] directly.
 *
 * All returned [Flow]s are live: Room emits a new value whenever the underlying
 * table changes, so the UI updates automatically when a new SMS is processed.
 */
class TransactionRepository(private val dao: TransactionDao) {

    // ── Live data flows ───────────────────────────────────────────────────────

    /**
     * Returns a [Flow] of [DaySummary] for the current calendar day.
     * Recomputes time range on each collection so midnight rollover works
     * without restarting the app (PROJECT_SPEC section 13).
     */
    fun todaySummary(): Flow<DaySummary> {
        val range = DateRanges.today()
        return summaryForRange(range.first, range.last)
    }

    /**
     * Returns a [Flow] of [DaySummary] for the current calendar month.
     */
    fun thisMonthSummary(): Flow<DaySummary> {
        val range = DateRanges.thisMonth()
        return summaryForRange(range.first, range.last)
    }

    /**
     * Returns a [Flow] of [DaySummary] for any specific calendar day.
     */
    fun specificDateSummary(dateEpochMs: Long): Flow<DaySummary> {
        val range = DateRanges.dayFor(dateEpochMs)
        return summaryForRange(range.first, range.last)
    }

    // ── SMS ingestion ─────────────────────────────────────────────────────────

    /**
     * Processes one SMS: parses it, and if valid, inserts it into the database.
     * Includes smart deduplication (3-minute sliding window) to prevent duplicate
     * entries caused by dual bank alerts (e.g. account debit + UPI confirmation) or
     * concurrent receiver + inbox reads.
     *
     * @param senderId   The originating address / sender ID.
     * @param body       The raw SMS text. Discarded after parsing; never stored.
     * @param timestamp  Epoch milliseconds from the SMS date field.
     * @return  true if a new transaction was inserted; false if rejected or duplicate.
     */
    suspend fun handleSms(senderId: String, body: String, timestamp: Long): Boolean {
        val parsed = SmsParser.parse(senderId, body, timestamp) ?: return false

        // Smart deduplication window: 3 minutes (180,000 ms)
        val windowMs = 180_000L
        val candidates = dao.findPotentialDuplicates(
            amountPaise = parsed.amountPaise,
            isDebit = parsed.isDebit,
            minTimestamp = timestamp - windowMs,
            maxTimestamp = timestamp + windowMs,
        )

        val incomingBank = BankUtils.getBankName(senderId)
        val existingMatch = candidates.firstOrNull { candidate ->
            isDuplicateMatch(candidate, parsed, incomingBank)
        }

        if (existingMatch != null) {
            // Transaction already recorded. Enrich existing transaction if new parsed SMS
            // contains missing party or account information:
            var updated = existingMatch
            var needsUpdate = false

            if (existingMatch.party == null && parsed.party != null) {
                updated = updated.copy(party = parsed.party)
                needsUpdate = true
            }
            if (existingMatch.accountLast4 == null && parsed.accountLast4 != null) {
                updated = updated.copy(accountLast4 = parsed.accountLast4)
                needsUpdate = true
            }

            if (needsUpdate) {
                dao.update(updated)
            }
            return false
        }

        val dedupeKey = Hashing.dedupeKey(senderId, timestamp, body)

        val entity = TransactionEntity(
            amountPaise = parsed.amountPaise,
            isDebit = parsed.isDebit,
            party = parsed.party,
            accountLast4 = parsed.accountLast4,
            senderId = senderId,
            timestamp = timestamp,
            dedupeKey = dedupeKey,
        )

        // Returns -1 if the dedupeKey already exists (IGNORE strategy)
        val rowId = dao.insert(entity)
        return rowId != -1L
    }

    /**
     * Cleans up any duplicate transactions currently in the database (e.g. historical
     * duplicates caused by SMS receiver + inbox import or dual bank SMS).
     * Preserves the row with the most complete details (e.g. party/account info).
     *
     * @return Number of duplicate rows removed.
     */
    suspend fun cleanupExistingDuplicates(): Int {
        val all = dao.getAllTransactions()
        if (all.size < 2) return 0

        val toDelete = mutableListOf<TransactionEntity>()
        val toUpdate = mutableMapOf<Long, TransactionEntity>()

        var i = 0
        while (i < all.size) {
            var current = toUpdate[all[i].id] ?: all[i]
            if (toDelete.any { it.id == current.id }) {
                i++
                continue
            }

            var j = i + 1
            while (j < all.size) {
                val next = all[j]
                if (toDelete.any { it.id == next.id }) {
                    j++
                    continue
                }

                // If timestamp difference exceeds 3 minutes, break inner loop (ordered by timestamp)
                if (Math.abs(next.timestamp - current.timestamp) > 180_000L) {
                    break
                }

                if (areHistoricalDuplicates(current, next)) {
                    val mergedParty = current.party ?: next.party
                    val mergedAccount = current.accountLast4 ?: next.accountLast4

                    if (mergedParty != current.party || mergedAccount != current.accountLast4) {
                        current = current.copy(party = mergedParty, accountLast4 = mergedAccount)
                        toUpdate[current.id] = current
                    }
                    toDelete.add(next)
                }
                j++
            }
            i++
        }

        if (toUpdate.isNotEmpty()) {
            dao.updateTransactions(toUpdate.values.toList())
        }
        if (toDelete.isNotEmpty()) {
            dao.deleteTransactions(toDelete)
        }

        return toDelete.size
    }

    private fun isDuplicateMatch(
        existing: TransactionEntity,
        parsed: com.example.moneytracker.data.sms.ParsedTxn,
        incomingBank: String
    ): Boolean {
        // Different party handles explicitly specified -> not a duplicate
        if (existing.party != null && parsed.party != null &&
            !existing.party.equals(parsed.party, ignoreCase = true)
        ) {
            return false
        }

        // Different bank account numbers -> not a duplicate
        if (existing.accountLast4 != null && parsed.accountLast4 != null &&
            existing.accountLast4 != parsed.accountLast4
        ) {
            return false
        }

        val existingBank = BankUtils.getBankName(existing.senderId)
        val bankMatches = existingBank.equals(incomingBank, ignoreCase = true)
        val accountsMatch = existing.accountLast4 != null && parsed.accountLast4 != null &&
                existing.accountLast4 == parsed.accountLast4
        val partyMatches = existing.party != null && parsed.party != null &&
                existing.party.equals(parsed.party, ignoreCase = true)

        return accountsMatch || bankMatches || partyMatches
    }

    private fun areHistoricalDuplicates(a: TransactionEntity, b: TransactionEntity): Boolean {
        if (a.amountPaise != b.amountPaise) return false
        if (a.isDebit != b.isDebit) return false

        if (a.party != null && b.party != null && !a.party.equals(b.party, ignoreCase = true)) {
            return false
        }
        if (a.accountLast4 != null && b.accountLast4 != null && a.accountLast4 != b.accountLast4) {
            return false
        }

        val bankA = BankUtils.getBankName(a.senderId)
        val bankB = BankUtils.getBankName(b.senderId)
        val bankMatches = bankA.equals(bankB, ignoreCase = true)
        val accountsMatch = a.accountLast4 != null && b.accountLast4 != null && a.accountLast4 == b.accountLast4
        val partyMatches = a.party != null && b.party != null && a.party.equals(b.party, ignoreCase = true)

        return accountsMatch || bankMatches || partyMatches
    }

    /**
     * Inserts a manually entered transaction (e.g. Cash payment or receipt).
     *
     * @param amountPaise The transaction amount in paise (must be > 0).
     * @param isDebit     true if expense/money out; false if income/money in.
     * @param party       Description, note, or payee (e.g. "Customer payment", "Tea / Snacks").
     * @param paymentMode Origin or mode (defaults to "Cash").
     * @param timestamp   Epoch milliseconds of the transaction.
     * @return true if inserted successfully.
     */
    suspend fun addManualTransaction(
        amountPaise: Long,
        isDebit: Boolean,
        party: String?,
        paymentMode: String = "Cash",
        timestamp: Long = System.currentTimeMillis(),
    ): Boolean {
        require(amountPaise > 0) { "Amount must be strictly positive" }
        val dedupeKey = "manual_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}"
        val entity = TransactionEntity(
            amountPaise = amountPaise,
            isDebit = isDebit,
            party = party?.trim()?.ifEmpty { null } ?: if (isDebit) "Cash Payment" else "Cash Received",
            accountLast4 = null,
            senderId = paymentMode.trim().ifEmpty { "Cash" },
            timestamp = timestamp,
            dedupeKey = dedupeKey,
        )
        val rowId = dao.insert(entity)
        return rowId != -1L
    }

    // ── Administrative ────────────────────────────────────────────────────────

    /**
     * Deletes all transactions. Called from "Delete all my data" in Settings.
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun summaryForRange(startMs: Long, endMs: Long): Flow<DaySummary> {
        val txFlow = dao.getTransactionsInRange(startMs, endMs)
        val debitFlow = dao.getTotalDebitInRange(startMs, endMs)
        val creditFlow = dao.getTotalCreditInRange(startMs, endMs)

        // Combine three flows into one DaySummary emission
        return combine(txFlow, debitFlow, creditFlow) { entities, totalDebit, totalCredit ->
            DaySummary(
                totalDebitPaise = totalDebit,
                totalCreditPaise = totalCredit,
                transactions = entities.map { it.toDomain() },
            )
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun TransactionEntity.toDomain() = Transaction(
        id = id,
        amountPaise = amountPaise,
        isDebit = isDebit,
        party = party,
        accountLast4 = accountLast4,
        senderId = senderId,
        timestamp = timestamp,
    )
}
