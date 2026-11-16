package com.example.moneytracker

import com.example.moneytracker.util.BankUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class BankUtilsTest {

    @Test
    fun `resolves Bank of India sender IDs`() {
        assertEquals("Bank of India", BankUtils.getBankName("JD-BOIIND-S"))
        assertEquals("Bank of India", BankUtils.getBankName("AX-BOIIND-S"))
        assertEquals("Bank of India", BankUtils.getBankName("VM-BOICMP-P"))
        assertEquals("Bank of India ••2403", BankUtils.formatBankAndAccount("JD-BOIIND-S", "2403"))
    }

    @Test
    fun `resolves Axis Bank sender IDs`() {
        assertEquals("Axis Bank", BankUtils.getBankName("AX-AXISBK-S"))
        assertEquals("Axis Bank", BankUtils.getBankName("JD-AXISBK-S"))
        assertEquals("Axis Bank", BankUtils.getBankName("BT-AXISMR-P"))
        assertEquals("Axis Bank ••1234", BankUtils.formatBankAndAccount("AX-AXISBK-S", "1234"))
    }

    @Test
    fun `resolves HSBC sender IDs`() {
        assertEquals("HSBC", BankUtils.getBankName("JM-HSBCIN-S"))
        assertEquals("HSBC", BankUtils.getBankName("VA-HSBCIM-S"))
        assertEquals("HSBC", BankUtils.formatBankAndAccount("JM-HSBCIN-S", null))
    }

    @Test
    fun `resolves HDFC Bank sender IDs`() {
        assertEquals("HDFC Bank", BankUtils.getBankName("VM-HDFCBK-S"))
        assertEquals("HDFC Bank", BankUtils.getBankName("AX-HDFCBK"))
    }

    @Test
    fun `resolves SBI and SBI Card sender IDs`() {
        assertEquals("State Bank of India", BankUtils.getBankName("JX-SBIBNK-S"))
        assertEquals("State Bank of India", BankUtils.getBankName("VM-SBIUPI"))
        assertEquals("SBI Card", BankUtils.getBankName("JM-SBICRD-S"))
        assertEquals("SBI Card ••5678", BankUtils.formatBankAndAccount("JM-SBICRD-S", "5678"))
    }

    @Test
    fun `resolves ICICI Bank sender IDs`() {
        assertEquals("ICICI Bank", BankUtils.getBankName("JM-ICICIT-S"))
    }

    @Test
    fun `initial extraction`() {
        assertEquals("B", BankUtils.getBankInitial("JD-BOIIND-S"))
        assertEquals("A", BankUtils.getBankInitial("AX-AXISBK-S"))
        assertEquals("H", BankUtils.getBankInitial("JM-HSBCIN-S"))
        assertEquals("S", BankUtils.getBankInitial("JM-SBICRD-S"))
        assertEquals("C", BankUtils.getBankInitial("Cash"))
    }

    @Test
    fun `resolves Cash payment mode`() {
        assertEquals("Cash", BankUtils.getBankName("Cash"))
        assertEquals("Cash", BankUtils.getBankName("CASH"))
        assertEquals("Cash", BankUtils.formatBankAndAccount("Cash", null))
    }

    @Test
    fun `formatPartyTitle formats UPI handles and merchant names`() {
        assertEquals("Swiggy", BankUtils.formatPartyTitle("swiggy@axisbank", isDebit = true))
        assertEquals("Zomato Order", BankUtils.formatPartyTitle("zomato.order@icici", isDebit = true))
        assertEquals("Paytm", BankUtils.formatPartyTitle("paytm-12345@paytm", isDebit = true))
        assertEquals("Rahul Sharma", BankUtils.formatPartyTitle("Rahul Sharma", isDebit = false))
        assertEquals("Payment Sent", BankUtils.formatPartyTitle(null, isDebit = true))
        assertEquals("Money Received", BankUtils.formatPartyTitle("", isDebit = false))
    }
}
