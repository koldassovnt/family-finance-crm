package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.domain.QuoteKind
import com.familyfinance.crm.dto.toInvestmentsResponse
import com.familyfinance.crm.user
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValuationTest {
    private val owner = user()
    private val usdBroker = account(owner, currency = "USD", type = AccountType.BROKER)
    private val fetchedAt = Instant.parse("2026-10-09T03:00:00Z")

    @Test
    fun `value is quantity times the latest price`() {
        val valued = QuoteBook(listOf(stock("VOO", "600"), rate("USD", "500"))).value(holding(usdBroker, "VOO"))

        assertEquals(BigDecimal("1200.0000"), valued.value)
        assertEquals(BigDecimal("200.0000"), valued.gain)
    }

    @Test
    fun `value in KZT uses the latest rate, not the purchase rate`() {
        val valued = QuoteBook(listOf(stock("VOO", "600"), rate("USD", "500"))).value(holding(usdBroker, "VOO"))

        assertEquals(BigDecimal("600000.0000"), valued.valueKzt)
        assertEquals(BigDecimal("120000.0000"), valued.gainKzt)
    }

    @Test
    fun `a holding with no quote has no value`() {
        val valued = QuoteBook(listOf(rate("USD", "500"))).value(holding(usdBroker, "VOO"))

        assertNull(valued.value)
    }

    @Test
    fun `a price quoted in another currency is not applied`() {
        val valued = QuoteBook(listOf(stock("VOO", "600", currency = "EUR"))).value(holding(usdBroker, "VOO"))

        assertNull(valued.value)
    }

    @Test
    fun `a missing exchange rate leaves the KZT value empty but keeps the value`() {
        val valued = QuoteBook(listOf(stock("VOO", "600"))).value(holding(usdBroker, "VOO"))

        assertEquals(BigDecimal("1200.0000"), valued.value)
        assertNull(valued.valueKzt)
    }

    @Test
    fun `a KZT account needs no rate`() {
        val kztBroker = account(owner, currency = "KZT", type = AccountType.BROKER)

        val valued = QuoteBook(listOf(stock("KSPI", "600", currency = "KZT"))).value(holding(kztBroker, "KSPI"))

        assertEquals(BigDecimal("1200.0000"), valued.valueKzt)
    }

    @Test
    fun `a coin pair is priced from the crypto quote even in an account typed as a broker`() {
        val quotes = QuoteBook(listOf(quote(QuoteKind.CRYPTO, "BTC/USD", "60000", "USD")))

        val valued = quotes.value(holding(usdBroker, "BTC/USD"))

        assertEquals(BigDecimal("120000.0000"), valued.value)
    }

    @Test
    fun `a total covers the priced holdings and counts the ones left out`() {
        val quotes = QuoteBook(listOf(stock("VOO", "600"), rate("USD", "500")))

        val response = listOf(holding(usdBroker, "VOO"), holding(usdBroker, "VTI")).map(quotes::value).toInvestmentsResponse()

        assertEquals(BigDecimal("600000.0000") to 1, response.totalValueKzt to response.unpriced)
    }

    @Test
    fun `a total's gain is taken over the priced holdings only`() {
        val quotes = QuoteBook(listOf(stock("VOO", "600"), rate("USD", "500")))

        val response = listOf(holding(usdBroker, "VOO"), holding(usdBroker, "VTI")).map(quotes::value).toInvestmentsResponse()

        assertEquals(BigDecimal("120000.0000"), response.totalGainKzt)
    }

    @Test
    fun `a total is empty when nothing in it has a price`() {
        val response = listOf(holding(usdBroker, "VOO")).map(QuoteBook(emptyList())::value).toInvestmentsResponse()

        assertNull(response.totalValueKzt)
    }

    @Test
    fun `a total is the sum when every holding has a value`() {
        val quotes = QuoteBook(listOf(stock("VOO", "600"), stock("VTI", "100"), rate("USD", "500")))

        val response = listOf(holding(usdBroker, "VOO"), holding(usdBroker, "VTI")).map(quotes::value).toInvestmentsResponse()

        assertEquals(BigDecimal("700000.0000"), response.totalValueKzt)
    }

    /** Two units bought for 1000 in the account's currency, 480 000 in KZT. */
    private fun holding(
        account: Account,
        ticker: String,
    ) = Holding(
        account = account,
        ticker = ticker,
        quantity = BigDecimal("2"),
        cost = BigDecimal("1000.0000"),
        costKzt = BigDecimal("480000.0000"),
    )

    private fun stock(
        ticker: String,
        price: String,
        currency: String = "USD",
    ) = quote(QuoteKind.STOCK, ticker, price, currency)

    private fun rate(
        currency: String,
        rateKzt: String,
    ) = quote(QuoteKind.CURRENCY, currency, rateKzt, "KZT")

    private fun quote(
        kind: QuoteKind,
        symbol: String,
        price: String,
        currency: String,
    ) = MarketQuote(kind = kind, symbol = symbol, price = BigDecimal(price), currency = currency, fetchedAt = fetchedAt)
}
