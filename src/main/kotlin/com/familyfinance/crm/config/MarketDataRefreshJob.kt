package com.familyfinance.crm.config

import com.familyfinance.crm.service.MarketDataService
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * The daily price and rate refresh. Once a day is all the free plan is worth:
 * it serves a closing stock price and a daily exchange rate, so asking more
 * often would spend the quota on the same answer.
 */
@Component
class MarketDataRefreshJob(
    private val marketDataService: MarketDataService,
    private val taskScheduler: TaskScheduler,
    private val clock: Clock,
) {
    @Scheduled(cron = "\${app.market-data.refresh-cron}", zone = "\${app.timezone}")
    fun refresh() {
        marketDataService.refresh()
    }

    /**
     * Also once shortly after every start. A deploy lands after the morning run
     * as often as before it, and the first one left the live instance with no
     * rates at all until the next day. Free on a restart later the same day:
     * what was already fetched today is not asked for again.
     *
     * Handed to the scheduler rather than run here, so a failure is a logged
     * error on a worker thread and never a failed startup.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun refreshAfterStart() {
        taskScheduler.schedule({ marketDataService.refresh() }, clock.instant().plusSeconds(STARTUP_DELAY_SECONDS))
    }
}

private const val STARTUP_DELAY_SECONDS = 20L
