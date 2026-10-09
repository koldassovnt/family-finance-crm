package com.familyfinance.crm.service

import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.dto.MarketRefreshResponse

interface MarketDataService {
    /**
     * Fetches the price of every asset held and the KZT rate of every currency
     * an account is in, stalest first, stopping each API at its daily cap. Safe
     * to call at any time: what the cap does not allow today is left for
     * tomorrow, and a failed call keeps the previous quote.
     */
    fun refresh(): MarketRefreshResponse

    /** The latest KZT rate of each currency, as last fetched. */
    fun rates(): List<MarketQuote>
}
