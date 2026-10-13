package com.familyfinance.crm.service

import com.familyfinance.crm.ALMATY
import com.familyfinance.crm.account
import com.familyfinance.crm.client.ApiNinjasClient
import com.familyfinance.crm.client.CryptoPriceQuote
import com.familyfinance.crm.client.ExchangeRateQuote
import com.familyfinance.crm.client.KaseClient
import com.familyfinance.crm.client.KaseSecurity
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MarketDataServiceImplTest {
    private val client = mockk<ApiNinjasClient>()
    private val kaseClient = mockk<KaseClient>()
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
        every { kaseClient.security(any()) } returns KaseSecurity(closePrice = BigDecimal("100"), currencyType = "KZT")
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
    fun `a refresh started while another is running waits for it, so together they stay within the cap`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD", "EUR")
        storeUsageLikeTheDatabase()
        val firstCallStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { client.exchangeRate(any()) } answers {
            firstCallStarted.countDown()
            release.await(5, TimeUnit.SECONDS)
            ExchangeRateQuote(BigDecimal("500"))
        }
        val service = service(dailyLimit = 2)

        val first = thread { service.refresh() }
        firstCallStarted.await(5, TimeUnit.SECONDS)
        val second = thread { service.refresh() }
        second.join(SECOND_REFRESH_HEAD_START_MS)
        release.countDown()
        first.join()
        second.join()

        verify(exactly = 2) { client.exchangeRate(any()) }
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
    fun `a stock held in a KZT account is priced by KASE at its closing price`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))
        every { kaseClient.security("HSBK") } returns
            KaseSecurity(closePrice = BigDecimal("374.0"), price = BigDecimal("375.5"), currencyType = "KZT")
        val saved = slot<MarketQuote>()
        every { quoteRepository.save(capture(saved)) } answers { firstArg() }

        service().refreshLocalExchange()

        assertEquals(
            listOf(QuoteKind.STOCK, "HSBK", BigDecimal("374.0"), "KZT", "KASE"),
            saved.captured.let { listOf(it.kind, it.symbol, it.price, it.currency, it.exchange) },
        )
    }

    @Test
    fun `the last deal is used when KASE gives no closing price`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))
        every { kaseClient.security("HSBK") } returns KaseSecurity(price = BigDecimal("375.5"), currencyType = "KZT")
        val saved = slot<MarketQuote>()
        every { quoteRepository.save(capture(saved)) } answers { firstArg() }

        service().refreshLocalExchange()

        assertEquals(BigDecimal("375.5"), saved.captured.price)
    }

    @Test
    fun `KASE is asked without an API Ninjas key`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))

        val report = service(apiKey = "").refresh()

        assertEquals(false to 1, report.configured to report.updated)
    }

    @Test
    fun `a coin in a KZT account is not sent to KASE`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.CRYPTO, "BTC/KZT", currency = "KZT"))

        service().refreshLocalExchange()

        verify(exactly = 0) { kaseClient.security(any()) }
    }

    @Test
    fun `a KASE outage is a failed ticker, not an exception`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))
        every { kaseClient.security(any()) } throws mockk<FeignException>(relaxed = true)

        val report = service().refreshLocalExchange()

        assertEquals(1, report.failed)
    }

    @Test
    fun `a KASE outage leaves the previous price in place`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))
        every { quoteRepository.findAllByKindOrderBySymbol(QuoteKind.STOCK) } returns
            listOf(kaseQuote("HSBK", fetchedAt = "2026-09-09T13:30:00Z"))
        every { kaseClient.security(any()) } throws mockk<FeignException>(relaxed = true)

        service().refreshLocalExchange()

        verify(exactly = 0) { quoteRepository.save(any<MarketQuote>()) }
    }

    @Test
    fun `one ticker KASE does not know does not stop the others`() {
        every { transactionRepository.findAllTrades() } returns
            listOf(
                opening(AccountType.BROKER, "GONE", currency = "KZT"),
                opening(AccountType.BROKER, "HSBK", currency = "KZT"),
            )
        every { kaseClient.security("GONE") } throws mockk<FeignException>(relaxed = true)

        val report = service().refreshLocalExchange()

        assertEquals(1 to 1, report.failed to report.updated)
    }

    @Test
    fun `a KASE answer without a price is a failure, not a quote`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))
        every { kaseClient.security("HSBK") } returns KaseSecurity()

        val report = service().refreshLocalExchange()

        assertEquals(1, report.failed)
    }

    @Test
    fun `an ordinary refresh does not ask KASE again for a ticker fetched today`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))
        every { quoteRepository.findAllByKindOrderBySymbol(QuoteKind.STOCK) } returns
            listOf(kaseQuote("HSBK", fetchedAt = "2026-09-10T04:00:00Z"))

        val report = service().refresh()

        assertEquals(1, report.upToDate)
        verify(exactly = 0) { kaseClient.security(any()) }
    }

    @Test
    fun `the after-close run replaces a price fetched earlier the same day`() {
        every { transactionRepository.findAllTrades() } returns listOf(opening(AccountType.BROKER, "HSBK", currency = "KZT"))
        every { quoteRepository.findAllByKindOrderBySymbol(QuoteKind.STOCK) } returns
            listOf(kaseQuote("HSBK", fetchedAt = "2026-09-10T04:00:00Z"))

        val report = service().refreshLocalExchange()

        assertEquals(1, report.updated)
    }

    @Test
    fun `the after-close run asks only KASE`() {
        every { accountRepository.findActiveCurrencies() } returns listOf("USD")

        service().refreshLocalExchange()

        verify(exactly = 0) { client.exchangeRate(any()) }
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

    /** Each read returns a fresh copy of the last saved count, as a database read would. */
    private fun storeUsageLikeTheDatabase() {
        val saved = AtomicReference<Int?>(null)
        every { usageRepository.findByKindAndDay(QuoteKind.CURRENCY, today) } answers {
            saved.get()?.let { MarketDataUsage(kind = QuoteKind.CURRENCY, day = today, calls = it) }
        }
        every { usageRepository.save(any<MarketDataUsage>()) } answers {
            firstArg<MarketDataUsage>().also { saved.set(it.calls) }
        }
    }

    private fun service(
        apiKey: String = "key",
        dailyLimit: Int = 30,
    ) = MarketDataServiceImpl(
        client = client,
        kaseClient = kaseClient,
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

    private fun kaseQuote(
        ticker: String,
        fetchedAt: String,
    ) = MarketQuote(
        kind = QuoteKind.STOCK,
        symbol = ticker,
        price = BigDecimal("370"),
        currency = "KZT",
        fetchedAt = Instant.parse(fetchedAt),
        exchange = "KASE",
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

/**
 * Long enough for an unguarded second refresh to read the count and reach the
 * API while the first is still mid-call; a guarded one just waits it out.
 */
private const val SECOND_REFRESH_HEAD_START_MS = 300L
