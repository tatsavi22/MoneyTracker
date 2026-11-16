package com.example.moneytracker.util

/**
 * Resolves user-friendly bank and institution names from SMS sender IDs.
 *
 * Indian bank SMS sender IDs follow TRAI alphanumeric headers such as:
 *  - "JD-BOIIND-S" -> "Bank of India"
 *  - "AX-AXISBK-S" -> "Axis Bank"
 *  - "JM-HSBCIN-S" -> "HSBC"
 *  - "VM-HDFCBK-S" -> "HDFC Bank"
 *  - "JX-SBIBNK-S" -> "State Bank of India"
 *  - "JM-SBICRD-S" -> "SBI Card"
 *  - "JM-ICICIT-S" -> "ICICI Bank"
 */
object BankUtils {

    /**
     * Extracts a user-friendly bank name from [senderId].
     */
    fun getBankName(senderId: String): String {
        // Strip 2-letter circle/carrier prefix (e.g. "AX-", "JD-") and DLT suffix (e.g. "-S", "-P")
        val stripped = senderId
            .replace(Regex("""^[A-Za-z]{2}-"""), "")
            .replace(Regex("""-[A-Za-z0-9]{1,2}$"""), "")
            .uppercase()

        return when {
            stripped.contains("CASH") -> "Cash"
            stripped.contains("BOI") -> "Bank of India"
            stripped.contains("AXIS") -> "Axis Bank"
            stripped.contains("HSBC") -> "HSBC"
            stripped.contains("HDFC") -> "HDFC Bank"
            stripped.contains("ICICI") -> "ICICI Bank"
            stripped.contains("SBICRD") -> "SBI Card"
            stripped.contains("SBI") -> "State Bank of India"
            stripped.contains("KOTAK") -> "Kotak Bank"
            stripped.contains("PNB") -> "Punjab National Bank"
            stripped.contains("BOB") || stripped.contains("BARB") -> "Bank of Baroda"
            stripped.contains("CANBNK") || stripped.contains("CANARA") -> "Canara Bank"
            stripped.contains("UNIONB") || stripped.contains("UBI") -> "Union Bank"
            stripped.contains("IDFC") -> "IDFC FIRST Bank"
            stripped.contains("INDUS") -> "IndusInd Bank"
            stripped.contains("YES") -> "Yes Bank"
            stripped.contains("FED") || stripped.contains("FEDERAL") -> "Federal Bank"
            stripped.contains("CITI") -> "Citi Bank"
            stripped.contains("RBL") -> "RBL Bank"
            stripped.contains("AUFINA") || stripped.contains("AUBANK") -> "AU Small Finance Bank"
            stripped.contains("PAYTM") || stripped.contains("PYTM") -> "Paytm Bank"
            stripped.contains("AIRTEL") -> "Airtel Payments Bank"
            stripped.contains("JIO") -> "Jio Payments Bank"
            stripped.contains("CREDIN") -> "CRED"
            stripped.contains("AMEX") -> "American Express"
            stripped.contains("SCBLTD") || stripped.contains("SCB") -> "Standard Chartered"
            stripped.contains("DBS") -> "DBS Bank"
            stripped.isNotBlank() -> stripped.capitalizeWords()
            else -> "Bank"
        }
    }

    /**
     * Extracts a single initial letter for avatar display.
     */
    fun getBankInitial(senderId: String): String {
        val name = getBankName(senderId)
        return name.firstOrNull()?.uppercase() ?: "B"
    }

    /**
     * Formats the bank and account display string.
     * Examples:
     *  - "Bank of India ••2403"
     *  - "Axis Bank ••1234"
     *  - "HSBC" (when last4 is null)
     */
    fun formatBankAndAccount(senderId: String, accountLast4: String?): String {
        val bankName = getBankName(senderId)
        return if (!accountLast4.isNullOrBlank()) {
            "$bankName ••$accountLast4"
        } else {
            bankName
        }
    }

    /**
     * Converts a raw UPI ID or merchant string into a human-friendly display title.
     * Examples:
     *  - "swiggy@axisbank" -> "Swiggy"
     *  - "zomato.order@icici" -> "Zomato Order"
     *  - "rahul.sharma@okaxis" -> "Rahul Sharma"
     *  - "paytm-12345@paytm" -> "Paytm"
     *  - null / blank -> "Payment Sent" (if debit) or "Money Received" (if credit)
     */
    fun formatPartyTitle(party: String?, isDebit: Boolean): String {
        if (party.isNullOrBlank()) {
            return if (isDebit) "Payment Sent" else "Money Received"
        }
        val trimmed = party.trim()
        if (trimmed.contains("@")) {
            val handle = trimmed.substringBefore("@")
            val stripped = handle
                .replace(Regex("""[-_.]\d+$"""), "")
                .replace(Regex("""[-_.]+"""), " ")
                .trim()
            if (stripped.isNotBlank()) {
                return stripped.capitalizeWords()
            }
        }
        return trimmed.capitalizeWords()
    }

    private fun String.capitalizeWords(): String =
        lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
