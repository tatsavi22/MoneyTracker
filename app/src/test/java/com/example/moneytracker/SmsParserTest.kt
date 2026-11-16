package com.example.moneytracker

import com.example.moneytracker.data.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SmsParser].
 *
 * All test SMS text uses the synthetic samples from PROJECT_SPEC section 7
 * plus additional cases covering: commas in amounts, no decimals, ₹ symbol,
 * uppercase text, missing party, and edge cases.
 *
 * SmsParser has zero Android dependencies, so these run as plain JVM tests.
 */
class SmsParserTest {

    // Helper: a constant timestamp (unused by parser but required by signature)
    private val t = 1_700_000_000_000L
    private val bankSender = "AX-HDFCBK"

    // ── PROJECT_SPEC synthetic samples (section 7) ────────────────────────────

    @Test
    fun `spec sample 1 - Rs dot amount debited with UPI and account`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs.250.00 debited from A/c XX1234 on 14-11-26 to VPA shop@upi (UPI Ref 123456789012)",
            t
        )
        assertNotNull(result)
        assertEquals(25000L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
        assertEquals("shop@upi", result.party)
        assertEquals("1234", result.accountLast4)
    }

    @Test
    fun `spec sample 2 - credited with comma amount and UPI`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 1,500.50 credited to A/c XX1234 on 14-11-26 from rahul@okaxis",
            t
        )
        assertNotNull(result)
        assertEquals(150050L, result!!.amountPaise)
        assertEquals(false, result.isDebit)
        assertEquals("rahul@okaxis", result.party)
    }

    @Test
    fun `spec sample 3 - INR paid via UPI`() {
        val result = SmsParser.parse(
            bankSender,
            "INR 99.00 paid to coffee@ybl via UPI",
            t
        )
        assertNotNull(result)
        assertEquals(9900L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
        assertEquals("coffee@ybl", result.party)
    }

    @Test
    fun `spec sample 4 - OTP SMS is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "123456 is your OTP for payment of Rs 500",
            t
        )
        assertNull(result)
    }

    @Test
    fun `spec sample 5 - failed debit is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 100 debit failed. Amount not deducted",
            t
        )
        assertNull(result)
    }

    @Test
    fun `spec sample 6 - collect request is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "You have a collect request of Rs 300 from amit@upi",
            t
        )
        assertNull(result)
    }

    @Test
    fun `spec sample 7 - promo SMS is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Get 10% off! Pay Rs 200 and win",
            t
        )
        // No debit or credit word → ambiguous → null
        assertNull(result)
    }

    @Test
    fun `spec sample 8 - zero amount is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 0.00 credited",
            t
        )
        assertNull(result)
    }

    // ── Additional cases ──────────────────────────────────────────────────────

    @Test
    fun `rupee symbol with comma grouping (Indian lakh format)`() {
        val result = SmsParser.parse(
            bankSender,
            "₹1,25,000.00 debited from your account",
            t
        )
        assertNotNull(result)
        assertEquals(12_500_000L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
    }

    @Test
    fun `rupee symbol without decimals`() {
        val result = SmsParser.parse(
            bankSender,
            "₹500 credited to your account",
            t
        )
        assertNotNull(result)
        assertEquals(50000L, result!!.amountPaise)
        assertEquals(false, result.isDebit)
    }

    @Test
    fun `uppercase SMS text is handled`() {
        val result = SmsParser.parse(
            bankSender,
            "RS 750.00 DEBITED FROM A/C XX5678",
            t
        )
        assertNotNull(result)
        assertEquals(75000L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
        assertEquals("5678", result.accountLast4)
    }

    @Test
    fun `sent keyword counts as debit`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 200 sent to mobile@paytm successfully",
            t
        )
        assertNotNull(result)
        assertEquals(20000L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
    }

    @Test
    fun `withdrawn keyword counts as debit`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 3000.00 withdrawn from ATM",
            t
        )
        assertNotNull(result)
        assertEquals(300000L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
        assertNull(result.party) // ATM withdrawal, no UPI handle
    }

    @Test
    fun `deposited keyword counts as credit`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 5000 deposited to your savings account",
            t
        )
        assertNotNull(result)
        assertEquals(500000L, result!!.amountPaise)
        assertEquals(false, result.isDebit)
    }

    @Test
    fun `received keyword counts as credit`() {
        val result = SmsParser.parse(
            bankSender,
            "INR 1000 received from friend@okicici",
            t
        )
        assertNotNull(result)
        assertEquals(100000L, result!!.amountPaise)
        assertEquals(false, result.isDebit)
        assertEquals("friend@okicici", result.party)
    }

    @Test
    fun `missing party results in null party field`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 150.00 debited from your account",
            t
        )
        assertNotNull(result)
        assertNull(result!!.party)
    }

    @Test
    fun `account with star prefix is parsed correctly`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 999.99 debited from a/c *9876",
            t
        )
        assertNotNull(result)
        assertEquals("9876", result!!.accountLast4)
    }

    @Test
    fun `declined SMS is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 500 declined. Transaction unsuccessful.",
            t
        )
        assertNull(result)
    }

    @Test
    fun `mandate SMS is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Your mandate of Rs 2000 has been registered",
            t
        )
        assertNull(result)
    }

    @Test
    fun `expires SMS is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Your Rs 100 voucher expires tomorrow",
            t
        )
        assertNull(result)
    }

    @Test
    fun `emi due SMS is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "EMI due of Rs 3500 on 15-11-26",
            t
        )
        assertNull(result)
    }

    @Test
    fun `personal phone number sender is rejected`() {
        // Sender looks like a phone number, not a bank short code
        val result = SmsParser.parse(
            "+919876543210",
            "Rs 500 debited from your account",
            t
        )
        assertNull(result)
    }

    @Test
    fun `personal phone number without plus is rejected`() {
        val result = SmsParser.parse(
            "9876543210",
            "Rs 500 debited from your account",
            t
        )
        assertNull(result)
    }

    @Test
    fun `both debit and credit words present is ambiguous - rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 500 debited and credited to your account",
            t
        )
        assertNull(result)
    }

    @Test
    fun `amount with single decimal digit`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs.75.5 debited",
            t
        )
        assertNotNull(result)
        assertEquals(7550L, result!!.amountPaise)
    }

    @Test
    fun `SBI-style sender ID passes filter`() {
        val result = SmsParser.parse(
            "VM-SBIUPI",
            "Rs 1200 credited to your SBI account from upi@sbi",
            t
        )
        assertNotNull(result)
        assertEquals(false, result!!.isDebit)
    }

    @Test
    fun `will be debited phrase is rejected`() {
        val result = SmsParser.parse(
            bankSender,
            "Rs 800 will be debited from your account for EMI",
            t
        )
        assertNull(result)
    }

    @Test
    fun `modern TRAI DLT sender headers with service suffixes`() {
        val r1 = SmsParser.parse(
            "AX-AXISBK-S",
            "Rs 250.00 debited from A/c XX1234",
            t
        )
        assertNotNull(r1)
        assertEquals(25000L, r1!!.amountPaise)
        assertEquals("1234", r1.accountLast4)

        val r2 = SmsParser.parse(
            "JD-BOIIND-S",
            "Rs 500.00 debited from A/c XX5678",
            t
        )
        assertNotNull(r2)

        val r3 = SmsParser.parse(
            "JM-HSBCIN-S",
            "INR 1000.00 credited to A/c XX9999",
            t
        )
        assertNotNull(r3)
        assertEquals(false, r3!!.isDebit)
    }

    @Test
    fun `UPI transfer with both debited and credited to merchant`() {
        val result = SmsParser.parse(
            "JD-BOIIND-S",
            "debited a/cxx2403 by Rs 150.00 and credited to Avenue Supermarts Ltd via upi ref no 123456789012",
            t
        )
        assertNotNull(result)
        assertEquals(15000L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
        assertEquals("2403", result.accountLast4)
        assertEquals("Avenue Supermarts Ltd", result.party)
    }

    @Test
    fun `fallback amount when currency symbol is omitted`() {
        val result = SmsParser.parse(
            "JD-BOIIND-S",
            "BOI UPI - Your account has been debited towards Indian Clearing Corporation for 1500.00 on 01/11/2026",
            t
        )
        assertNotNull(result)
        assertEquals(150000L, result!!.amountPaise)
        assertEquals(true, result.isDebit)
        assertEquals("Indian Clearing Corporation", result.party)
    }

    @Test
    fun `account ending variations`() {
        val r1 = SmsParser.parse(
            "VM-HDFCBK-S",
            "Rs 200 debited from Account ending 4321",
            t
        )
        assertNotNull(r1)
        assertEquals("4321", r1!!.accountLast4)

        val r2 = SmsParser.parse(
            "JM-SBICRD-S",
            "Rs 1500 spent on Card ending in 8765",
            t
        )
        assertNotNull(r2)
        assertEquals("8765", r2!!.accountLast4)
    }
}
