package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.exception.invalidField

/**
 * How a ticker is written depends on the account it is traded in — see
 * "Ticker format" in `phase-5-investments.md`. One spelling per kind of
 * account is what lets a holding be matched to a price without an
 * instrument table:
 *
 * - a `CRYPTO` account: `COIN/CUR`, quoted in the account's currency — `TON/USD`
 * - a `BROKER` account in any currency but KZT: `SYMBOL.EXCHANGE` — `VEA.US`
 * - a `BROKER` account in KZT: the plain local ticker — `HSBK`
 */
fun normalizeTicker(
    ticker: String,
    account: Account,
): String {
    val normalized = requireNonBlankName(ticker, "ticker").uppercase()
    when {
        account.type == AccountType.CRYPTO -> {
            if (!CRYPTO_TICKER.matches(normalized)) {
                throw invalidField("ticker", "must be written as COIN/${account.currency}, e.g. TON/${account.currency}")
            }
            if (normalized.substringAfter(PAIR_SEPARATOR) != account.currency) {
                throw invalidField("ticker", "must be quoted in the account's currency, ${account.currency}")
            }
        }

        isBaseCurrency(account.currency) -> {
            if (!LOCAL_TICKER.matches(normalized)) {
                throw invalidField("ticker", "must be the plain ticker for a KZT account, e.g. HSBK")
            }
        }

        else -> {
            if (!EXCHANGE_TICKER.matches(normalized)) {
                throw invalidField("ticker", "must include the exchange for a ${account.currency} account, e.g. VEA.US")
            }
        }
    }
    return normalized
}

/**
 * The price API's spelling of a coin pair: joined, and against the dollar
 * stablecoin rather than the dollar — `TON/USD` is asked for as `TONUSDT`.
 */
fun cryptoApiSymbol(ticker: String): String {
    val quote = ticker.substringAfter(PAIR_SEPARATOR)
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

private val CRYPTO_TICKER = Regex("^[A-Z0-9]{1,20}/[A-Z]{3}$")
private val EXCHANGE_TICKER = Regex("^[A-Z0-9^-]{1,20}\\.[A-Z]{1,6}$")
private val LOCAL_TICKER = Regex("^[A-Z0-9]{1,20}$")
