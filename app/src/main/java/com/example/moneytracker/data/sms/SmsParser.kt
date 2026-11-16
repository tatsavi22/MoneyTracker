package com.example.moneytracker.data.sms

/**
 * The result of a successful SMS parse.
 *
 * This is an intermediate object used only during parsing and insertion.
 * It is NEVER persisted directly — only the fields listed in [TransactionEntity]
 * are saved to the database (PROJECT_SPEC rule 3: never store the SMS body).
 */
data class ParsedTxn(
    /** Amount in paise. Always positive and non-zero. */
    val amountPaise: Long,
    /** true = money out (debit), false = money in (credit). */
    val isDebit: Boolean,
    /** UPI handle or merchant/payee found in the SMS, or null. */
    val party: String?,
    /** Last 4 digits of the account number, or null. */
    val accountLast4: String?,
)

/**
 * Pure Kotlin SMS parser — zero Android imports, so it runs in plain JVM unit tests.
 *
 * Entry point: [SmsParser.parse].
 *
 * Design: each rule is a small, independently testable function. Adding support
 * for a new bank format means adding a new matcher and a corresponding test —
 * not rewriting this file (PROJECT_SPEC section 7, design for extension).
 */
object SmsParser {

    // ── Reject patterns ──────────────────────────────────────────────────────

    /** Lowercase keywords that mean this SMS is NOT a completed money movement. */
    private val REJECT_KEYWORDS = listOf(
        "otp",
        "failed",
        "declined",
        "will be debited",
        "collect request",
        "requested",
        "mandate",
        "expires",
        "offer",
        "emi due",
    )

    // ── Direction keywords ────────────────────────────────────────────────────

    private val DEBIT_WORDS = listOf("debited", "spent", "paid", "sent", "withdrawn")
    private val CREDIT_WORDS = listOf("credited", "received", "deposited")

    // ── Regex patterns ────────────────────────────────────────────────────────

    /**
     * Primary amount match: prefixed by Rs., Rs, INR, or ₹.
     * Captures digits with optional commas and up to 2 decimal places.
     * Example matches: "Rs.250.00", "INR 1,500.50", "₹99"
     */
    private val AMOUNT_REGEX = Regex(
        """(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Fallback amount match for Indian bank statements where the currency token is omitted
     * after action prepositions (e.g. "debited towards ... for 1500.00 on ...").
     */
    private val FALLBACK_AMOUNT_REGEX = Regex(
        """(?:debited|credited|paid|spent)\s+(?:towards\s+[\w\s]+?\s+)?(?:for|by|of)\s+([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Matches a UPI handle: lowercase-ish alphanumeric with dots/hyphens/underscores,
     * then @, then alphanumeric (the VPA bank/PSP suffix).
     */
    private val UPI_REGEX = Regex("""[a-z0-9._-]+@[a-z0-9]+""", RegexOption.IGNORE_CASE)

    /**
     * Matches payee name from Indian bank UPI alerts (e.g. "credited to <payee> via upi").
     */
    private val PAYEE_REGEX = Regex(
        """credited\s+to\s+([A-Za-z0-9\s._-]{2,35}?)(?:\s+via\s+upi|\s+on\s+|\s+ref\s+|\.|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Matches merchant from "debited towards <merchant>" or "paid to <merchant>".
     */
    private val TOWARDS_REGEX = Regex(
        """(?:debited\s+towards|paid\s+to|sent\s+to|towards)\s+([A-Za-z0-9\s._-]{2,35}?)(?:\s+for\s+|\s+on\s+|\s+via|\s+ref|\.|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Matches account references like "A/c XX1234", "a/c *1234", "a/cxx1234",
     * "Account ending 1234", "Card ending in 1234", etc.
     * Capture group 1 = the last-4 digits.
     */
    private val ACCOUNT_REGEX = Regex(
        """(?:[Aa]/[Cc]|acct|account|card)\s*(?:no\.?|ending(?:\s+with|\s+in)?|[xX*.-]+)?\s*[*xX.-]*(\d{4})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Sender ID filter:
     * Matches standard TRAI alphanumeric headers (with or without 2-letter circle prefix)
     * and handles modern DLT service/transactional suffixes (e.g. "-S", "-P", "-T", "-G").
     * E.g. "AX-HDFCBK", "JD-BOIIND-S", "AX-AXISBK-S", "HDFCBK".
     * Personal phone numbers (e.g. "+91...", "9876543210") and numeric shortcodes are rejected.
     */
    private val SENDER_FILTER = Regex("""^(?:[A-Za-z]{2}-)?[A-Za-z][A-Za-z0-9]{2,7}(?:-[A-Za-z0-9]{1,2})?$""")

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Parses a single SMS into a [ParsedTxn], or returns null if this SMS
     * is not a completed money movement.
     *
     * @param senderId   The originating address / sender ID from the SMS.
     * @param body       The full SMS text. NEVER store or log this after parsing.
     * @param timestamp  Epoch milliseconds from the SMS date field (unused here
     *                   but passed through so callers don't need to track it separately).
     */
    fun parse(senderId: String, body: String, @Suppress("UNUSED_PARAMETER") timestamp: Long): ParsedTxn? {
        // 1. Sender must look like a bank short code, not a personal number
        if (!SENDER_FILTER.matches(senderId)) return null

        val lower = body.lowercase()

        // 2. Reject non-transaction SMS
        if (REJECT_KEYWORDS.any { lower.contains(it) }) return null

        // 3. Determine direction
        val hasDebit = DEBIT_WORDS.any { lower.contains(it) }
        val hasCredit = CREDIT_WORDS.any { lower.contains(it) }

        val isDebit: Boolean = when {
            hasDebit && !hasCredit -> true
            !hasDebit && hasCredit -> false
            hasDebit && hasCredit -> {
                // If it explicitly says "debited and credited to your account", ambiguous/contradictory
                if (lower.contains("credited to your account") || lower.contains("credited to your a/c")) {
                    return null
                }
                // When both appear (e.g. "debited from A/c XX... and credited to shop@upi"):
                // The primary action on the user's account appears first.
                val debitIdx = DEBIT_WORDS.map { lower.indexOf(it) }.filter { it != -1 }.minOrNull() ?: -1
                val creditIdx = CREDIT_WORDS.map { lower.indexOf(it) }.filter { it != -1 }.minOrNull() ?: -1
                if (debitIdx != -1 && creditIdx != -1 && debitIdx < creditIdx) {
                    true
                } else {
                    return null
                }
            }
            else -> return null
        }

        // 4. Extract amount
        val amountPaise = extractAmountPaise(body) ?: return null
        if (amountPaise <= 0) return null

        // 5. Extract optional party (UPI handle or merchant/payee name)
        val party = extractParty(body)

        // 6. Extract optional account last-4
        val accountLast4 = ACCOUNT_REGEX.find(body)?.groupValues?.getOrNull(1)

        return ParsedTxn(
            amountPaise = amountPaise,
            isDebit = isDebit,
            party = party,
            accountLast4 = accountLast4,
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Finds currency amount in [body] and converts it to paise.
     * Uses [java.math.BigDecimal] for exact decimal → integer conversion.
     * Returns null if no valid amount is found.
     */
    private fun extractAmountPaise(body: String): Long? {
        val raw = AMOUNT_REGEX.find(body)?.groupValues?.getOrNull(1)
            ?: FALLBACK_AMOUNT_REGEX.find(body)?.groupValues?.getOrNull(1)
            ?: return null

        // Strip grouping commas (Indian format: "1,25,000.50")
        val cleaned = raw.replace(",", "")
        return try {
            java.math.BigDecimal(cleaned)
                .movePointRight(2)
                .toLong()
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun extractParty(body: String): String? {
        // Priority 1: Exact UPI handle (e.g. user@okhdfcbank)
        val upiMatch = UPI_REGEX.find(body)?.value?.lowercase()
        if (upiMatch != null) return upiMatch

        // Priority 2: Payee extracted from "credited to <name> via upi"
        val payeeMatch = PAYEE_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()
        if (!payeeMatch.isNullOrBlank() && !payeeMatch.contains("account", ignoreCase = true)) {
            return payeeMatch
        }

        // Priority 3: Merchant from "debited towards <merchant>"
        val towardsMatch = TOWARDS_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()
        if (!towardsMatch.isNullOrBlank()) {
            return towardsMatch
        }

        return null
    }
}
