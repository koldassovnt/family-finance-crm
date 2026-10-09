package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.user
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TickersTest {
    private val owner = user()
    private val crypto = account(owner, currency = "USD", type = AccountType.CRYPTO)
    private val usdBroker = account(owner, currency = "USD", type = AccountType.BROKER)
    private val kztBroker = account(owner, currency = "KZT", type = AccountType.BROKER)

    @Test
    fun `a coin is written as a pair quoted in the account's currency`() {
        assertEquals("TON/USD", normalizeTicker(" ton/usd ", crypto))
    }

    @Test
    fun `rejects a coin written without what it is quoted in`() {
        assertThrows<ValidationException> { normalizeTicker("TON", crypto) }
    }

    @Test
    fun `rejects a coin quoted in a currency other than the account's`() {
        assertThrows<ValidationException> { normalizeTicker("TON/EUR", crypto) }
    }

    @Test
    fun `a stock in a foreign-currency broker account carries its exchange`() {
        assertEquals("VEA.US", normalizeTicker("vea.us", usdBroker))
    }

    @Test
    fun `rejects a stock without an exchange in a foreign-currency broker account`() {
        assertThrows<ValidationException> { normalizeTicker("VEA", usdBroker) }
    }

    @Test
    fun `a coin pair is accepted in a foreign-currency broker account`() {
        assertEquals("GRAM/USD", normalizeTicker("gram/usd", usdBroker))
    }

    @Test
    fun `a crypto account takes coins only`() {
        assertThrows<ValidationException> { normalizeTicker("VEA.US", crypto) }
    }

    @Test
    fun `a stock in a KZT broker account is the plain ticker`() {
        assertEquals("HSBK", normalizeTicker("hsbk", kztBroker))
    }

    @Test
    fun `rejects an exchange suffix in a KZT broker account`() {
        assertThrows<ValidationException> { normalizeTicker("HSBK.US", kztBroker) }
    }

    @Test
    fun `a dollar coin pair is asked for against the dollar stablecoin`() {
        assertEquals("TONUSDT", cryptoApiSymbol("TON/USD"))
    }

    @Test
    fun `a US stock is asked for without its suffix`() {
        assertEquals("VEA", stockApiSymbol("VEA.US"))
    }

    @Test
    fun `a stock on any other exchange keeps its suffix`() {
        assertEquals("HSBK.IL", stockApiSymbol("HSBK.IL"))
    }
}
