package com.familyfinance.crm.service

import com.familyfinance.crm.client.ApiNinjasClient
import com.familyfinance.crm.config.AppProperties
import com.familyfinance.crm.domain.BASE_CURRENCY
import com.familyfinance.crm.domain.CURRENCY_CODE_LENGTH
import com.familyfinance.crm.domain.MarketDataUsage
import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.domain.QuoteKind
import com.familyfinance.crm.dto.MarketRefreshResponse
import com.familyfinance.crm.repository.AccountRepository
import com.familyfinance.crm.repository.MarketDataUsageRepository
import com.familyfinance.crm.repository.MarketQuoteRepository
import com.familyfinance.crm.repository.TransactionRepository
import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Deliberately **not** `@Transactional`: a refresh makes up to ninety HTTP
 * calls, and holding a database connection open across them would tie the
 * pool to a third party's response time. Each save commits on its own, which
 * is also what makes the call count survive a refresh that dies halfway.
 *
 * Refreshes run one at a time. Each reads the day's call count once and counts
 * on from it in memory, so two at once (the startup run and a manual one, or
 * two people pressing refresh) would each spend up to the cap and fetch the
 * same symbols. A JVM lock is enough because only one instance ever runs.
 */
@Service
class MarketDataServiceImpl(
    private val client: ApiNinjasClient,
    private val quoteRepository: MarketQuoteRepository,
    private val usageRepository: MarketDataUsageRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val properties: AppProperties,
    private val clock: Clock,
) : MarketDataService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val refreshLock = ReentrantLock()

    // A caller that waits here finds the first run's quotes and count already
    // saved, so its own run asks only for what is still missing.
    override fun refresh(): MarketRefreshResponse = refreshLock.withLock { refreshNow() }

    private fun refreshNow(): MarketRefreshResponse {
        if (!properties.marketData.isConfigured) {
            return MarketRefreshResponse(configured = false, updated = 0, upToDate = 0, failed = 0, overBudget = 0)
        }
        val outcomes = wantedSymbols().flatMap { (kind, symbols) -> refreshKind(kind, symbols) }
        return MarketRefreshResponse(
            configured = true,
            updated = outcomes.count { it == Outcome.UPDATED },
            upToDate = outcomes.count { it == Outcome.UP_TO_DATE },
            failed = outcomes.count { it == Outcome.FAILED },
            overBudget = outcomes.count { it == Outcome.OVER_BUDGET },
        ).also { log.info("Market data refresh: {}", it) }
    }

    override fun rates(): List<MarketQuote> = quoteRepository.findAllByKindOrderBySymbol(QuoteKind.CURRENCY)

    /** What is worth a call: assets someone still holds, and currencies some account is in. */
    private fun wantedSymbols(): Map<QuoteKind, Set<String>> {
        val holdings = holdingsOf(transactionRepository.findAllTrades())
        return mapOf(
            QuoteKind.CURRENCY to accountRepository.findActiveCurrencies().filterNot(::isBaseCurrency).toSet(),
            QuoteKind.STOCK to holdings.tickersOf(QuoteKind.STOCK),
            QuoteKind.CRYPTO to holdings.tickersOf(QuoteKind.CRYPTO),
        )
    }

    private fun refreshKind(
        kind: QuoteKind,
        symbols: Set<String>,
    ): List<Outcome> {
        val known = quoteRepository.findAllByKindOrderBySymbol(kind).associateBy { it.symbol }
        val today = LocalDate.now(clock)
        val usage = usageRepository.findByKindAndDay(kind, today) ?: MarketDataUsage(kind = kind, day = today, calls = 0)
        // Never fetched first, then stalest: when there are more symbols than
        // the cap allows, tomorrow's refresh picks up where today's stopped.
        return symbols
            .sortedBy { known[it]?.fetchedAt ?: Instant.MIN }
            .map { symbol ->
                if (known[symbol].isFrom(today)) {
                    // The source moves once a day, so a second ask today buys the same answer.
                    Outcome.UP_TO_DATE
                } else if (usage.calls >= properties.marketData.dailyLimit) {
                    Outcome.OVER_BUDGET
                } else {
                    // Counted before the call: a request that fails still spent quota.
                    usage.calls += 1
                    usageRepository.save(usage)
                    fetchAndStore(kind, symbol, known[symbol])
                }
            }
    }

    private fun MarketQuote?.isFrom(day: LocalDate): Boolean = this != null && LocalDate.ofInstant(fetchedAt, clock.zone) == day

    private fun fetchAndStore(
        kind: QuoteKind,
        symbol: String,
        existing: MarketQuote?,
    ): Outcome {
        val fetched =
            try {
                fetch(kind, symbol)
            } catch (e: FeignException) {
                log.warn("No {} quote for {}: HTTP {} {}", kind, symbol, e.status(), e.message)
                null
            }
        if (fetched == null || fetched.price.signum() <= 0 || fetched.currency.length != CURRENCY_CODE_LENGTH) {
            // The previous quote, if any, stays: stale and dated beats absent.
            return Outcome.FAILED
        }
        val quote =
            existing?.apply {
                price = fetched.price
                currency = fetched.currency
                exchange = fetched.exchange
                fetchedAt = clock.instant()
            } ?: MarketQuote(
                kind = kind,
                symbol = symbol,
                price = fetched.price,
                currency = fetched.currency,
                exchange = fetched.exchange,
                fetchedAt = clock.instant(),
            )
        quoteRepository.save(quote)
        return Outcome.UPDATED
    }

    private fun fetch(
        kind: QuoteKind,
        symbol: String,
    ): Fetched? =
        when (kind) {
            QuoteKind.CURRENCY -> {
                client.exchangeRate("${symbol}_$BASE_CURRENCY").exchangeRate?.let { Fetched(it, BASE_CURRENCY) }
            }

            QuoteKind.STOCK -> {
                val quote = client.stockPrice(stockApiSymbol(symbol))
                quote.price?.let { price ->
                    quote.currency?.let { Fetched(price, it.uppercase(), quote.exchange?.take(MAX_EXCHANGE_LENGTH)) }
                }
            }

            // Quoted in a dollar stablecoin and recorded as dollars, the same
            // way a crypto account's own currency is — see `phase-5-investments.md`.
            QuoteKind.CRYPTO -> {
                client.cryptoPrice(cryptoApiSymbol(symbol)).price?.let { Fetched(it, cryptoQuoteCurrency(symbol)) }
            }
        }

    private data class Fetched(
        val price: BigDecimal,
        val currency: String,
        val exchange: String? = null,
    )

    private enum class Outcome { UPDATED, UP_TO_DATE, FAILED, OVER_BUDGET }
}

/**
 * Holdings in a KZT account are left out: the price API covers neither the
 * local exchange nor KZT-quoted coins, so asking would spend a call every day
 * on an answer that is always "unknown". Checked with a real key on 2026-10-09.
 */
private fun List<Holding>.tickersOf(kind: QuoteKind): Set<String> =
    filter { it.quoteKind == kind && !isBaseCurrency(it.currency) }.map { it.ticker }.toSet()

private const val MAX_EXCHANGE_LENGTH = 32
