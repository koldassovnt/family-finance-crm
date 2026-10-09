package com.familyfinance.crm.client

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigDecimal

/**
 * The one outbound dependency this service has — see "Market data" in
 * `phase-5-investments.md`. Every call costs one of the day's requests, so
 * nothing calls this except `MarketDataService`, which counts them.
 */
@FeignClient(
    name = "api-ninjas",
    url = "\${app.market-data.base-url}",
    configuration = [ApiNinjasClientConfig::class],
)
interface ApiNinjasClient {
    /** [pair] is `FROM_TO`, e.g. `USD_KZT`: how many `TO` one `FROM` buys. */
    @GetMapping("/v1/exchangerate")
    fun exchangeRate(
        @RequestParam("pair") pair: String,
    ): ExchangeRateQuote

    @GetMapping("/v1/stockprice")
    fun stockPrice(
        @RequestParam("ticker") ticker: String,
    ): StockPriceQuote

    /** [symbol] is the coin and what it is quoted in, joined: `BTCUSDT`. */
    @GetMapping("/v1/cryptoprice")
    fun cryptoPrice(
        @RequestParam("symbol") symbol: String,
    ): CryptoPriceQuote
}

/**
 * Every field is nullable on purpose: an unknown symbol comes back as a 200
 * with an empty or partial body, and that has to read as "no price" rather
 * than fail to parse.
 */
data class ExchangeRateQuote(
    @JsonProperty("exchange_rate")
    val exchangeRate: BigDecimal? = null,
)

data class StockPriceQuote(
    val price: BigDecimal? = null,
    /** The currency the exchange quotes it in — not necessarily the account's. */
    val currency: String? = null,
    val exchange: String? = null,
)

data class CryptoPriceQuote(
    val price: BigDecimal? = null,
)
