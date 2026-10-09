package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.TradeSide
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.dto.RenameTickerRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.MarketQuoteRepository
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InvestmentServiceImplTest {
    private val transactionRepository = mockk<TransactionRepository>()
    private val quoteRepository = mockk<MarketQuoteRepository>()
    private val accountService = mockk<AccountService>()
    private val service = InvestmentServiceImpl(transactionRepository, quoteRepository, accountService)

    private val owner = user()
    private val viewer = user(email = "viewer@example.com")

    init {
        every { quoteRepository.findAll() } returns emptyList()
    }

    @Test
    fun `the portfolio is read from the caller's own trades only`() {
        every { transactionRepository.findTradesByOwner(viewer) } returns emptyList()

        service.portfolio(viewer)

        verify(exactly = 1) { transactionRepository.findTradesByOwner(viewer) }
    }

    @Test
    fun `a viewer of a shared broker account can read its holdings`() {
        val broker = account(owner, type = AccountType.BROKER)
        every { accountService.getReadableBy(broker.idValue, viewer) } returns
            Readable.Shared(resource = broker, owner = owner, access = ShareAccess.VIEWER)
        every { transactionRepository.findTradesByAccount(broker) } returns emptyList()

        val holdings = service.accountHoldings(broker.idValue, viewer)

        assertTrue(holdings.isEmpty())
    }

    @Test
    fun `holdings of an account that is not shared are a 404`() {
        val broker = account(owner, type = AccountType.BROKER)
        every { accountService.getReadableBy(broker.idValue, viewer) } throws NotFoundException("Account was not found")

        assertThrows<NotFoundException> { service.accountHoldings(broker.idValue, viewer) }
    }

    @Test
    fun `a rename rewrites every trade of the ticker in the account`() {
        val exchange = account(owner, currency = "USD", type = AccountType.CRYPTO)
        val trades = listOf(opening(exchange, "TON/USD"), opening(exchange, "TON/USD"), opening(exchange, "BTC/USD"))
        every { accountService.getOwnedBy(exchange.idValue, owner) } returns exchange
        every { transactionRepository.findTradesByAccount(exchange) } returns trades

        service.renameTicker(exchange.idValue, owner, RenameTickerRequest(from = "ton/usd", to = "gram/usd"))

        assertEquals(listOf("GRAM/USD", "GRAM/USD", "BTC/USD"), trades.map { it.ticker })
    }

    @Test
    fun `a rename onto a ticker already traded in the account is refused`() {
        val exchange = account(owner, currency = "USD", type = AccountType.CRYPTO)
        every { accountService.getOwnedBy(exchange.idValue, owner) } returns exchange
        every { transactionRepository.findTradesByAccount(exchange) } returns
            listOf(opening(exchange, "TON/USD"), opening(exchange, "BTC/USD"))

        assertThrows<ConflictException> {
            service.renameTicker(exchange.idValue, owner, RenameTickerRequest(from = "TON/USD", to = "BTC/USD"))
        }
    }

    @Test
    fun `a rename of a ticker the account never traded is rejected`() {
        val exchange = account(owner, currency = "USD", type = AccountType.CRYPTO)
        every { accountService.getOwnedBy(exchange.idValue, owner) } returns exchange
        every { transactionRepository.findTradesByAccount(exchange) } returns listOf(opening(exchange, "BTC/USD"))

        assertThrows<ValidationException> {
            service.renameTicker(exchange.idValue, owner, RenameTickerRequest(from = "TON/USD", to = "GRAM/USD"))
        }
    }

    @Test
    fun `a rename must use the format the account requires`() {
        val exchange = account(owner, currency = "USD", type = AccountType.CRYPTO)
        every { accountService.getOwnedBy(exchange.idValue, owner) } returns exchange

        assertThrows<ValidationException> {
            service.renameTicker(exchange.idValue, owner, RenameTickerRequest(from = "TON/USD", to = "GRAM"))
        }
    }

    @Test
    fun `a viewer of a shared account cannot rename its tickers`() {
        val exchange = account(owner, currency = "USD", type = AccountType.CRYPTO)
        every { accountService.getOwnedBy(exchange.idValue, viewer) } throws NotFoundException("Account was not found")

        assertThrows<NotFoundException> {
            service.renameTicker(exchange.idValue, viewer, RenameTickerRequest(from = "TON/USD", to = "GRAM/USD"))
        }
    }

    private fun opening(
        account: Account,
        ticker: String,
    ) = Transaction(
        type = TransactionType.TRADE,
        amount = BigDecimal("100.0000"),
        currency = account.currency,
        exchangeRate = BigDecimal("500"),
        amountKzt = BigDecimal("50000.0000"),
        toAmount = null,
        occurredOn = LocalDate.of(2026, 9, 1),
        account = account,
        toAccount = null,
        category = null,
        note = null,
        tradeSide = TradeSide.OPENING,
        ticker = ticker,
        quantity = BigDecimal("1"),
        unitPrice = BigDecimal("100"),
    ).withId()
}
