package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.QuoteKind
import com.familyfinance.crm.domain.TradeSide
import com.familyfinance.crm.domain.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What one account holds of one ticker, derived from its trades and never
 * stored — a stored quantity could drift from the history that explains it.
 * [cost] is what the units still held were bought for, in the account's
 * currency; [costKzt] is the same at each purchase's own rate.
 */
data class Holding(
    val account: Account,
    val ticker: String,
    val quantity: BigDecimal,
    val cost: BigDecimal,
    val costKzt: BigDecimal,
) {
    val currency: String get() = account.currency

    /** Which price API knows this ticker: decided by the account, since a ticker alone does not say. */
    val quoteKind: QuoteKind get() = if (account.type == AccountType.CRYPTO) QuoteKind.CRYPTO else QuoteKind.STOCK
    val averagePrice: BigDecimal get() = cost.divide(quantity, PRICE_SCALE, RoundingMode.HALF_UP)
    val averagePriceKzt: BigDecimal get() = costKzt.divide(quantity, PRICE_SCALE, RoundingMode.HALF_UP)
}

/**
 * Replays [trades] into holdings, one per account and ticker. Cost is a
 * weighted average: a sale takes its share of the cost with it and leaves the
 * average price of what remains unchanged. A position sold down to nothing is
 * dropped.
 *
 * [trades] must be oldest first.
 */
fun holdingsOf(trades: List<Transaction>): List<Holding> =
    positionsOf(trades)
        .filter { it.quantity.signum() > 0 }
        .sortedWith(compareBy({ it.ticker }, { it.account.name }))

/**
 * Every position [trades] ever opened, including the ones sold out and — when
 * a purchase was deleted from under a sale — the ones sold below zero, which
 * is what the write paths look for before letting such a change through.
 */
fun positionsOf(trades: List<Transaction>): List<Holding> =
    trades
        .groupBy { it.account to it.tradeTicker() }
        .map { (key, rows) ->
            val (account, ticker) = key
            rows.fold(Holding(account, ticker, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)) { held, trade ->
                held.after(trade)
            }
        }

private fun Holding.after(trade: Transaction): Holding {
    val traded = checkNotNull(trade.quantity) { "A TRADE always has a quantity" }
    return when (checkNotNull(trade.tradeSide) { "A TRADE always has a side" }) {
        TradeSide.BUY, TradeSide.OPENING -> {
            copy(
                quantity = quantity + traded,
                cost = cost + trade.amount,
                costKzt = costKzt + trade.amountKzt,
            )
        }

        TradeSide.SELL -> {
            val remaining = quantity - traded
            if (remaining.signum() <= 0) {
                copy(quantity = remaining, cost = BigDecimal.ZERO, costKzt = BigDecimal.ZERO)
            } else {
                copy(
                    quantity = remaining,
                    cost = cost.share(remaining, quantity),
                    costKzt = costKzt.share(remaining, quantity),
                )
            }
        }
    }
}

private fun Transaction.tradeTicker(): String = checkNotNull(ticker) { "A TRADE always has a ticker" }

private fun BigDecimal.share(
    part: BigDecimal,
    whole: BigDecimal,
): BigDecimal = multiply(part).divide(whole, MONEY_SCALE, RoundingMode.HALF_UP)

const val MONEY_SCALE = 4

/** Matches the `quantity` and `unit_price` columns. */
const val PRICE_SCALE = 10
