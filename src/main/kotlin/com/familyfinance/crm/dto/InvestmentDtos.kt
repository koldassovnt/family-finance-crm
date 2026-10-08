package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.AccountType
import java.math.BigDecimal
import java.util.UUID

/** One ticker in one account. Every figure is what was paid, not what it is worth today. */
data class HoldingResponse(
    val ticker: String,
    val accountId: UUID,
    val accountName: String,
    val accountType: AccountType,
    /** The account's currency, which is the currency the asset was bought in. */
    val currency: String,
    val quantity: BigDecimal,
    /** Weighted average price paid per unit, in [currency]. */
    val averagePrice: BigDecimal,
    /** The same in KZT, at the rate of each purchase. */
    val averagePriceKzt: BigDecimal,
    /** What the units still held cost, in [currency]. */
    val cost: BigDecimal,
    val costKzt: BigDecimal,
)

data class CurrencyTotal(
    val currency: String,
    val cost: BigDecimal,
    val costKzt: BigDecimal,
)

data class InvestmentsResponse(
    val holdings: List<HoldingResponse>,
    /** Cost summed per currency; currencies are never added to each other except in KZT. */
    val totalsByCurrency: List<CurrencyTotal>,
    val totalCostKzt: BigDecimal,
)
