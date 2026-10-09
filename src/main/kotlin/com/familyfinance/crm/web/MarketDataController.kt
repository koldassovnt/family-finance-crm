package com.familyfinance.crm.web

import com.familyfinance.crm.dto.ExchangeRateResponse
import com.familyfinance.crm.dto.MarketRefreshResponse
import com.familyfinance.crm.dto.toRateResponse
import com.familyfinance.crm.service.MarketDataService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/market-data")
@Tag(name = "Market data", description = "Prices and exchange rates fetched from API Ninjas")
class MarketDataController(
    private val marketDataService: MarketDataService,
) {
    @GetMapping("/rates")
    @Operation(
        summary = "Latest exchange rates",
        description =
            "KZT per one unit of each currency an account is held in, as last fetched — a daily rate, so " +
                "`fetchedAt` can be a day old. Empty until the first refresh. Read from storage: " +
                "calling this never spends an API request.",
    )
    @ApiResponse(responseCode = "200", description = "The stored rates, by currency")
    fun rates(): List<ExchangeRateResponse> = marketDataService.rates().map { it.toRateResponse() }

    @PostMapping("/refresh")
    @Operation(
        summary = "Fetch prices and rates now",
        description =
            "Runs the same refresh as the daily job: every asset anyone holds and every non-KZT account " +
                "currency, stalest first. Each of the three APIs is capped per day, so asking again once " +
                "the cap is reached fetches nothing and reports the rest as `overBudget`. " +
                "`configured: false` means no API key is set.",
    )
    @ApiResponse(responseCode = "200", description = "What the refresh did")
    fun refresh(): MarketRefreshResponse = marketDataService.refresh()
}
