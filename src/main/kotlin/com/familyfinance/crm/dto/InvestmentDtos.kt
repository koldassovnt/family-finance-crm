package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.AccountType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RenameTickerRequest(
    /** The ticker as it is written on the trades today. */
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 32, message = "must be at most 32 characters")
    val from: String,
    /** The new ticker, in the format the account requires. */
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 32, message = "must be at most 32 characters")
    val to: String,
)

/**
 * One ticker in one account. The cost figures are always present. The market
 * figures — [price] onward — are null until a price has been fetched for the
 * ticker in this account's currency.
 */
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
    /** The latest market price of one unit, in [currency]. */
    val price: BigDecimal?,
    /** When [price] was fetched. A daily closing price, so it can be a day or more old. */
    val priceAsOf: Instant?,
    /** The exchange the price came from, e.g. `AMEX`. Stocks only, and only once priced. */
    val exchange: String?,
    /** `quantity × price`, in [currency]. */
    val value: BigDecimal?,
    /** [value] at the latest KZT rate; null when that rate has not been fetched either. */
    val valueKzt: BigDecimal?,
    /** `value − cost`. Negative for a loss. */
    val gain: BigDecimal?,
    /** `valueKzt − costKzt`, so it reflects the exchange rate moving as well as the price. */
    val gainKzt: BigDecimal?,
)

/**
 * `cost` covers every holding in the group. The market figures cover **only
 * the holdings that have a price**, and [unpriced] says how many do not —
 * some assets are never priced, and they must not blank the total for the
 * rest. So `value − cost` is not the gain when [unpriced] is above zero;
 * [gain] is, because it is summed over the priced holdings alone.
 */
data class CurrencyTotal(
    val currency: String,
    val cost: BigDecimal,
    val costKzt: BigDecimal,
    /** Null when no holding in the group has a price. */
    val value: BigDecimal?,
    val valueKzt: BigDecimal?,
    val gain: BigDecimal?,
    val gainKzt: BigDecimal?,
    /** Holdings left out of the market figures above. */
    val unpriced: Int,
)

data class InvestmentsResponse(
    val holdings: List<HoldingResponse>,
    /** Summed per currency; currencies are never added to each other except in KZT. */
    val totalsByCurrency: List<CurrencyTotal>,
    val totalCostKzt: BigDecimal,
    /** Over the holdings that have a KZT value only; see [unpriced]. */
    val totalValueKzt: BigDecimal?,
    val totalGainKzt: BigDecimal?,
    /** Holdings with no KZT value, left out of the two totals above. */
    val unpriced: Int,
)
