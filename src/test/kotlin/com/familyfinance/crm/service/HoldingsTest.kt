package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.TradeSide
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HoldingsTest {
    private val owner = user()
    private val broker = account(owner, currency = "USD", type = AccountType.BROKER)

    @Test
    fun `two purchases average their price by quantity`() {
        val holding =
            holdingsOf(
                listOf(
                    trade(TradeSide.BUY, quantity = "1", unitPrice = "100", rate = "500"),
                    trade(TradeSide.BUY, quantity = "3", unitPrice = "200", rate = "520"),
                ),
            ).single()

        assertEquals(BigDecimal("4"), holding.quantity)
        assertEquals(BigDecimal("700.0000"), holding.cost)
        assertEquals(0, BigDecimal("175").compareTo(holding.averagePrice))
    }

    @Test
    fun `cost in KZT uses each purchase's own rate`() {
        val holding =
            holdingsOf(
                listOf(
                    trade(TradeSide.BUY, quantity = "1", unitPrice = "100", rate = "500"),
                    trade(TradeSide.BUY, quantity = "3", unitPrice = "200", rate = "520"),
                ),
            ).single()

        assertEquals(BigDecimal("362000.0000"), holding.costKzt)
    }

    @Test
    fun `a sale takes its share of the cost and leaves the average price unchanged`() {
        val holding =
            holdingsOf(
                listOf(
                    trade(TradeSide.BUY, quantity = "4", unitPrice = "100", rate = "500"),
                    trade(TradeSide.SELL, quantity = "1", unitPrice = "900", rate = "500"),
                ),
            ).single()

        assertEquals(BigDecimal("3"), holding.quantity)
        assertEquals(BigDecimal("300.0000"), holding.cost)
        assertEquals(0, BigDecimal("100").compareTo(holding.averagePrice))
    }

    @Test
    fun `an opening position counts toward the holding`() {
        val holding = holdingsOf(listOf(trade(TradeSide.OPENING, quantity = "0.05", unitPrice = "60000", rate = "500"))).single()

        assertEquals(BigDecimal("0.05"), holding.quantity)
        assertEquals(BigDecimal("3000.0000"), holding.cost)
    }

    @Test
    fun `a position sold down to nothing is dropped`() {
        val holdings =
            holdingsOf(
                listOf(
                    trade(TradeSide.BUY, quantity = "2", unitPrice = "100", rate = "500"),
                    trade(TradeSide.SELL, quantity = "2", unitPrice = "120", rate = "500"),
                ),
            )

        assertTrue(holdings.isEmpty())
    }

    @Test
    fun `the same ticker in two accounts is two holdings`() {
        val second = account(owner, currency = "USD", type = AccountType.BROKER)

        val holdings =
            holdingsOf(
                listOf(
                    trade(TradeSide.BUY, quantity = "1", unitPrice = "100", rate = "500"),
                    trade(TradeSide.BUY, quantity = "2", unitPrice = "100", rate = "500", account = second),
                ),
            )

        assertEquals(setOf(broker, second), holdings.map { it.account }.toSet())
    }

    private fun trade(
        side: TradeSide,
        quantity: String,
        unitPrice: String,
        rate: String,
        account: Account = broker,
    ): Transaction {
        val amount = BigDecimal(quantity).multiply(BigDecimal(unitPrice)).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        return Transaction(
            type = TransactionType.TRADE,
            amount = amount,
            currency = account.currency,
            exchangeRate = BigDecimal(rate),
            amountKzt = amount.multiply(BigDecimal(rate)).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            toAmount = null,
            occurredOn = LocalDate.of(2026, 9, 1),
            account = account,
            toAccount = null,
            category = null,
            note = null,
            tradeSide = side,
            ticker = "VOO",
            quantity = BigDecimal(quantity),
            unitPrice = BigDecimal(unitPrice),
        ).withId()
    }
}
