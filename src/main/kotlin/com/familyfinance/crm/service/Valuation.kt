package com.familyfinance.crm.service

import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.domain.QuoteKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * A holding next to what the market says it is worth. Everything beyond the
 * holding itself is nullable, and stays null rather than guessed: no quote
 * yet, a quote in another currency, or no rate for the account's currency.
 */
data class ValuedHolding(
    val holding: Holding,
    /** The asset's latest price, only when it is quoted in the holding's own currency. */
    val quote: MarketQuote?,
    /** KZT per one unit of the holding's currency; 1 for a KZT account. */
    val rateKzt: BigDecimal?,
) {
    val price: BigDecimal? get() = quote?.price
    val priceAsOf: Instant? get() = quote?.fetchedAt

    /** Where the asset trades, as the price API names it; stocks only. */
    val exchange: String? get() = quote?.exchange

    val value: BigDecimal?
        get() = quote?.let { holding.quantity.multiply(it.price).setScale(MONEY_SCALE, RoundingMode.HALF_UP) }

    val valueKzt: BigDecimal?
        get() = value?.let { value -> rateKzt?.let { value.multiply(it).setScale(MONEY_SCALE, RoundingMode.HALF_UP) } }

    val gain: BigDecimal? get() = value?.let { it - holding.cost }

    /** Moves with the exchange rate as well as the price, since the cost was fixed at each purchase's rate. */
    val gainKzt: BigDecimal? get() = valueKzt?.let { it - holding.costKzt }
}

/** The stored quotes, indexed for valuing holdings. */
class QuoteBook(
    quotes: List<MarketQuote>,
) {
    private val bySymbol = quotes.associateBy { it.kind to it.symbol }

    fun value(holding: Holding): ValuedHolding =
        ValuedHolding(
            holding = holding,
            // A price in another currency cannot be multiplied into this
            // holding's cost currency, so it is treated as no price at all.
            quote = bySymbol[holding.quoteKind to holding.ticker]?.takeIf { it.currency == holding.currency },
            rateKzt =
                if (isBaseCurrency(holding.currency)) {
                    BigDecimal.ONE
                } else {
                    bySymbol[QuoteKind.CURRENCY to holding.currency]?.price
                },
        )
}
