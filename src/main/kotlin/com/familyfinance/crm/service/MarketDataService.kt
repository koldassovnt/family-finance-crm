package com.familyfinance.crm.service

import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.dto.MarketRefreshResponse

interface MarketDataService {
    /**
     * Fetches the price of every asset held and the KZT rate of every currency
     * an account is in, stalest first, stopping each API at its daily cap; and
     * from KASE, the price of any KZT-account stock not yet fetched today. Safe
     * to call at any time: what the cap does not allow today is left for
     * tomorrow, and a failed call keeps the previous quote.
     */
    fun refresh(): MarketRefreshResponse

    /**
     * Fetches the closing price of every stock held in a KZT account from
     * KASE, replacing a price fetched earlier the same day. Run after the
     * exchange closes. Never throws for a KASE failure: that endpoint is
     * undocumented, and a holding it cannot price keeps what it had.
     */
    fun refreshLocalExchange(): MarketRefreshResponse

    /** The latest KZT rate of each currency, as last fetched. */
    fun rates(): List<MarketQuote>
}
