package com.familyfinance.crm.client

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.math.BigDecimal

/**
 * The Kazakhstan Stock Exchange, which API Ninjas does not cover. This is the
 * JSON endpoint kase.kz's own pages load, found by the owner on 2026-10-13: no
 * key, no documentation, and no promise it stays — so nothing may depend on it
 * answering. See "KASE" in `phase-5-investments.md`.
 */
@FeignClient(name = "kase", url = "\${app.market-data.kase-base-url}")
interface KaseClient {
    /** [ticker] as traded on KASE, e.g. `HSBK`. An unknown one is a 404. */
    @GetMapping("/api/instruments/securities/{ticker}/")
    fun security(
        @PathVariable("ticker") ticker: String,
    ): KaseSecurity
}

/** Nullable throughout, so a changed or partial answer reads as "no price" instead of failing to parse. */
data class KaseSecurity(
    /** The day's closing price; the one stored. */
    @JsonProperty("close_price")
    val closePrice: BigDecimal? = null,
    /** The last deal, used when no closing price is given. */
    val price: BigDecimal? = null,
    @JsonProperty("currency_type")
    val currencyType: String? = null,
)
