package com.familyfinance.crm.config

import com.familyfinance.crm.service.MarketDataService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * The daily price and rate refresh. Once a day is all the free plan is worth:
 * it serves a closing stock price and a daily exchange rate, so asking more
 * often would spend the quota on the same answer.
 */
@Component
class MarketDataRefreshJob(
    private val marketDataService: MarketDataService,
) {
    @Scheduled(cron = "\${app.market-data.refresh-cron}", zone = "\${app.timezone}")
    fun refresh() {
        marketDataService.refresh()
    }
}
