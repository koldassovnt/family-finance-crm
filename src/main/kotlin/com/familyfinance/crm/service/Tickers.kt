package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.exception.invalidField

/**
 * How a ticker is written — see "Ticker format" in `phase-5-investments.md`.
 * The spelling is what lets a holding be matched to a price without an
 * instrument table, and it is also what says whether it is a coin or a stock:
 *
 * - a coin, in any account that can trade: `COIN/CUR`, quoted in the
 *   account's currency — `GRAM/USD`
 * - a stock in a `BROKER` account in any currency but KZT:
 *   `SYMBOL.EXCHANGE` — `VEA.US`
 * - a stock in a `BROKER` account in KZT: the plain local ticker — `HSBK`
 *
 * A `CRYPTO` account takes coins only.
 */
fun normalizeTicker(
    ticker: String,
    account: Account,
): String {
    val normalized = requireNonBlankName(ticker, "ticker").uppercase()
    when {
        isCoinPair(normalized) -> {
            if (!COIN_PAIR.matches(normalized)) {
                throw invalidField("ticker", "must be written as COIN/${account.currency}, e.g. GRAM/${account.currency}")
            }
            if (cryptoQuoteCurrency(normalized) != account.currency) {
                throw invalidField("ticker", "must be quoted in the account's currency, ${account.currency}")
            }
        }

        account.type == AccountType.CRYPTO -> {
            throw invalidField("ticker", "must be written as COIN/${account.currency}, e.g. GRAM/${account.currency}")
        }

        isBaseCurrency(account.currency) -> {
            if (!LOCAL_TICKER.matches(normalized)) {
                throw invalidField("ticker", "must be the plain ticker for a KZT account, e.g. HSBK")
            }
        }

        else -> {
            if (!EXCHANGE_TICKER.matches(normalized)) {
                throw invalidField(
                    "ticker",
                    "must include the exchange, e.g. VEA.US, or be a coin pair, e.g. GRAM/${account.currency}",
                )
            }
        }
    }
    return normalized
}

/** A coin is recognised by the separator between it and what it is quoted in. */
fun isCoinPair(ticker: String): Boolean = PAIR_SEPARATOR in ticker

/**
 * The price API's spelling of a coin pair: joined, and against the dollar
 * stablecoin rather than the dollar — `GRAM/USD` is asked for as `GRAMUSDT`.
 */
fun cryptoApiSymbol(ticker: String): String {
    val quote = cryptoQuoteCurrency(ticker)
    return ticker.substringBefore(PAIR_SEPARATOR) + if (quote == DOLLAR) DOLLAR_STABLECOIN else quote
}

/** The price API lists US tickers bare and every other exchange with its suffix. */
fun stockApiSymbol(ticker: String): String = ticker.removeSuffix(US_SUFFIX)

/** The quote half of a coin pair, which is the currency its price is in. */
fun cryptoQuoteCurrency(ticker: String): String = ticker.substringAfter(PAIR_SEPARATOR)

private const val PAIR_SEPARATOR = "/"
private const val DOLLAR = "USD"
private const val DOLLAR_STABLECOIN = "USDT"
private const val US_SUFFIX = ".US"

private val COIN_PAIR = Regex("^[A-Z0-9]{1,20}/[A-Z]{3}$")
private val EXCHANGE_TICKER = Regex("^[A-Z0-9^-]{1,20}\\.[A-Z]{1,6}$")
private val LOCAL_TICKER = Regex("^[A-Z0-9]{1,20}$")
