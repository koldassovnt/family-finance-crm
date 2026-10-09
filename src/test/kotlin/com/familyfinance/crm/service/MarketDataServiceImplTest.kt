package com.familyfinance.crm.service

import com.familyfinance.crm.ALMATY
import com.familyfinance.crm.account
import com.familyfinance.crm.client.ApiNinjasClient
import com.familyfinance.crm.client.CryptoPriceQuote
import com.familyfinance.crm.client.ExchangeRateQuote
import com.familyfinance.crm.client.StockPriceQuote
import com.familyfinance.crm.config.AppProperties
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.MarketDataUsage
import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.domain.QuoteKind
import com.familyfinance.crm.domain.TradeSide
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.repository.AccountRepository
import com.familyfinance.crm.repository.MarketDataUsageRepository
import com.familyfinance.crm.repository.MarketQuoteRepository
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.user
import feign.FeignException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MarketDataServiceImplTest {
    private val client = mockk<ApiNinjasClient>()
    private val quoteRepository = mockk<MarketQuoteRepository>()
    private val usageRepository = mockk<MarketDataUsageRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val today = LocalDate.of(2026, 9, 10)

    init {
        every { quoteRepository.findAllByKindOrderBySymbol(any()) } returns emptyList()
        every { quoteRepository.save(any<MarketQuote>()) } answers { firstArg() }
        every { usageRepository.findByKindAndDay(any(), any()) } returns null
        every { usageRepository.save(any<MarketDataUsage>()) } answers { firstArg() }
        every { transactionRepository.findAllTrades() } returns emptyList()
        every { accountRepository.findActiveCurrencies() } returns listOf("KZT")
    }

    @Test
    fun `does nothing without an API key`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD")

        val report = service(apiKey = "").refresh()

        assertFalse(report.configured)
        verify(exactly = 0) { client.exchangeRate(any()) }
    }

    @Test
    fun `fetches the KZT rate of every foreign account currency and none for KZT itself`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("KZT", "USD")
        every { client.exchangeRate("USD_KZT") } returns ExchangeRateQuote(BigDecimal("512.34"))
        val saved = slot<MarketQuote>()
        every { quoteRepository.save(capture(saved)) } answers { firstArg() }

        service().refresh()

        assertEquals(BigDecimal("512.34"), saved.captured.price)
        verify(exactly = 1) { client.exchangeRate(any()) }
    }

    @Test
    fun `stops calling an API once its daily cap is reached`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD", "EUR", "GBP")
        every { client.exchangeRate(any()) } returns ExchangeRateQuote(BigDecimal("500"))

        val report = service(dailyLimit = 2).refresh()

        assertEquals(1, report.overBudget)
        verify(exactly = 2) { client.exchangeRate(any()) }
    }

    @Test
    fun `calls already made today count toward the cap`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD")
        every { usageRepository.findByKindAndDay(QuoteKind.CURRENCY, today) } returns
            MarketDataUsage(kind = QuoteKind.CURRENCY, day = today, calls = 2)

        val report = service(dailyLimit = 2).refresh()

        assertEquals(1, report.overBudget)
        verify(exactly = 0) { client.exchangeRate(any()) }
    }

    @Test
    fun `when the cap cannot cover everything, the stalest quote is refreshed first`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD", "EUR")
        every { quoteRepository.findAllByKindOrderBySymbol(QuoteKind.CURRENCY) } returns
            listOf(rate("EUR", fetchedAt = "2026-09-08T03:00:00Z"), rate("USD", fetchedAt = "2026-09-09T03:00:00Z"))
        every { client.exchangeRate(any()) } returns ExchangeRateQuote(BigDecimal("600"))

        service(dailyLimit = 1).refresh()

        verify(exactly = 1) { client.exchangeRate("EUR_KZT") }
    }

    @Test
    fun `a quote already fetched today is not asked for again`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD")
        every { quoteRepository.findAllByKindOrderBySymbol(QuoteKind.CURRENCY) } returns
            listOf(rate("USD", fetchedAt = "2026-09-10T03:00:00Z"))

        val report = service().refresh()

        assertEquals(1, report.upToDate)
        verify(exactly = 0) { client.exchangeRate(any()) }
    }

    @Test
    fun `holdings in a KZT account are never sent to the price API`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))

        service().refresh()

        verify(exactly = 0) { client.stockPrice(any()) }
    }

    @Test
    fun `a failed call keeps the previous quote and still counts against the cap`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD")
        every { client.exchangeRate(any()) } throws mockk<FeignException>(relaxed = true)
        val usage = slot<MarketDataUsage>()
        every { usageRepository.save(capture(usage)) } answers { firstArg() }

        val report = service().refresh()

        assertEquals(1, report.failed)
        assertEquals(1, usage.captured.calls)
        verify(exactly = 0) { quoteRepository.save(any<MarketQuote>()) }
    }

    @Test
    fun `an answer with no price is a failure, not a quote`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD")
        every { client.exchangeRate(any()) } returns ExchangeRateQuote(null)

        val report = service().refresh()

        assertEquals(1, report.failed)
    }

    @Test
    fun `a coin held in a crypto account is priced against the dollar stablecoin and stored as dollars`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.CRYPTO, "BTC/USD"))
        every { client.cryptoPrice("BTCUSDT") } returns CryptoPriceQuote(BigDecimal("61000.5"))
        val saved = slot<MarketQuote>()
        every { quoteRepository.save(capture(saved)) } answers { firstArg() }

        service().refresh()

        assertEquals(QuoteKind.CRYPTO to "USD", saved.captured.kind to saved.captured.currency)
    }

    @Test
    fun `a ticker held in a broker account is priced by the stock API in the currency it reports`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "VOO.US"))
        every { client.stockPrice("VOO") } returns StockPriceQuote(price = BigDecimal("600"), currency = "usd", exchange = "AMEX")
        val saved = slot<MarketQuote>()
        every { quoteRepository.save(capture(saved)) } answers { firstArg() }

        service().refresh()

        assertEquals("USD" to "AMEX", saved.captured.currency to saved.captured.exchange)
    }

    private fun opening(
        accountType: AccountType,
        ticker: String,
        currency: String = "USD",
    ) = Transaction(
        type = TransactionType.TRADE,
        amount = BigDecimal("100.0000"),
        currency = currency,
        exchangeRate = BigDecimal("500"),
        amountKzt = BigDecimal("50000.0000"),
        toAmount = null,
        occurredOn = today,
        account = account(user(), currency = currency, type = accountType),
        toAccount = null,
        category = null,
        note = null,
        tradeSide = TradeSide.OPENING,
        ticker = ticker,
        quantity = BigDecimal("1"),
        unitPrice = BigDecimal("100"),
    )

    private fun service(
        apiKey: String = "key",
        dailyLimit: Int = 30,
    ) = MarketDataServiceImpl(
        client = client,
        quoteRepository = quoteRepository,
        usageRepository = usageRepository,
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        properties =
            AppProperties(
                timezone = ALMATY,
                jwt = AppProperties.JwtProperties(secret = "0123456789abcdef0123456789abcdef", expiryDays = 30),
                marketData = AppProperties.MarketDataProperties(apiKey = apiKey, dailyLimit = dailyLimit),
            ),
        clock = fixedClock(today),
    )

    private fun rate(
        currency: String,
        fetchedAt: String,
    ) = MarketQuote(
        kind = QuoteKind.CURRENCY,
        symbol = currency,
        price = BigDecimal("500"),
        currency = "KZT",
        fetchedAt = Instant.parse(fetchedAt),
    )
}
