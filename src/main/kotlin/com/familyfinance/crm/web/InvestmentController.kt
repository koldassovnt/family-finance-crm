package com.familyfinance.crm.web

import com.familyfinance.crm.dto.InvestmentsResponse
import com.familyfinance.crm.dto.toInvestmentsResponse
import com.familyfinance.crm.service.InvestmentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/investments")
@Tag(name = "Investments", description = "What the broker and crypto accounts hold, derived from their trades")
class InvestmentController(
    private val investmentService: InvestmentService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "Everything you hold",
        description =
            "One row per ticker per account, with the quantity and what it cost in the account's currency " +
                "and in KZT, plus cost totals per currency. Derived on read from TRADE transactions; " +
                "record one with POST /api/v1/transactions. Figures are purchase cost, not current value — " +
                "no price source exists yet. Your own accounts only: one shared with you never adds to this.",
    )
    @ApiResponse(responseCode = "200", description = "The holdings and their totals")
    fun portfolio(): InvestmentsResponse = investmentService.portfolio(currentUser.require()).toInvestmentsResponse()
}
