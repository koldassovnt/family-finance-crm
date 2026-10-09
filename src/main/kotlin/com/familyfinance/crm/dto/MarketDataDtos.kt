package com.familyfinance.crm.dto

import java.math.BigDecimal
import java.time.Instant

/** What one refresh did, counted in symbols. `updated` and `failed` each cost one call; the other two cost none. */
data class MarketRefreshResponse(
    /** False when no API key is set; nothing was attempted. */
    val configured: Boolean,
    val updated: Int,
    /** Already fetched today, so not asked for again. */
    val upToDate: Int,
    /** Asked for and not obtained: unknown symbol, API error or timeout. The previous quote, if any, was kept. */
    val failed: Int,
    /** Not asked for, because that API's daily cap was already reached. */
    val overBudget: Int,
)

data class ExchangeRateResponse(
    val currency: String,
    /** KZT per one unit of [currency]. */
    val rateKzt: BigDecimal,
    val fetchedAt: Instant,
)
