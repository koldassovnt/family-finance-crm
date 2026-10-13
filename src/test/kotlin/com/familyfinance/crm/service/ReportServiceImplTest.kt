package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.category
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.TradeSide
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.topic
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportServiceImplTest {
    private val transactionService = mockk<TransactionService>()
    private val service = ReportServiceImpl(transactionService)

    private val owner = user()
    private val account = account(owner)
    private val from = LocalDate.of(2026, 9, 1)
    private val to = LocalDate.of(2026, 9, 30)

    @Test
    fun `the first row names every column`() {
        val sheet = export()

        assertEquals(
            listOf(
                "Date",
                "Type",
                "Account",
                "To account",
                "Category",
                "Topic",
                "Amount",
                "Currency",
                "Rate to KZT",
                "Amount KZT",
                "To amount",
                "To currency",
                "Trade side",
                "Ticker",
                "Quantity",
                "Unit price",
                "Note",
            ),
            sheet.getRow(0).map { it.stringCellValue },
        )
    }

    @Test
    fun `an export with no transactions is a sheet with only the header`() {
        val sheet = export()

        assertEquals(0, sheet.lastRowNum)
    }

    @Test
    fun `transactions are written one per row in the order the list returns them`() {
        val sheet = export(transaction(note = "newest"), transaction(note = "older"))

        assertEquals(listOf("newest", "older"), listOf(sheet.text(1, "Note"), sheet.text(2, "Note")))
    }

    @Test
    fun `an expense is written with its account, category, topic and note by name`() {
        val sheet =
            export(
                transaction(
                    category = category(owner),
                    topic = topic(owner, name = "Malaysia trip"),
                    note = "Lunch",
                ),
            )

        assertEquals(
            listOf("EXPENSE", "Main", "Groceries", "Malaysia trip", "Lunch"),
            listOf("Type", "Account", "Category", "Topic", "Note").map { sheet.text(1, it) },
        )
    }

    @Test
    fun `the date is a real date cell rather than text`() {
        val sheet = export(transaction(occurredOn = LocalDate.of(2026, 9, 5)))

        assertEquals(LocalDate.of(2026, 9, 5), sheet.cell(1, "Date")?.localDateTimeCellValue?.toLocalDate())
    }

    @Test
    fun `amounts are numeric cells, in the account's currency and in KZT`() {
        val usd = account(owner, currency = "USD")
        val sheet = export(transaction(account = usd, amount = "12.50", exchangeRate = "480.25"))

        assertEquals(
            listOf(12.5, 480.25, 6003.13),
            listOf("Amount", "Rate to KZT", "Amount KZT").map { sheet.cell(1, it)?.numericCellValue },
        )
    }

    @Test
    fun `a cross-currency transfer shows the destination account, amount and currency`() {
        val usd = account(owner, currency = "USD")
        val sheet =
            export(
                transaction(type = TransactionType.TRANSFER, amount = "480000", toAccount = usd, toAmount = "1000"),
            )

        assertEquals("USD", sheet.text(1, "To currency"))
        assertEquals(1000.0, sheet.cell(1, "To amount")?.numericCellValue)
    }

    @Test
    fun `a same-currency transfer leaves the destination amount and currency empty`() {
        val sheet = export(transaction(type = TransactionType.TRANSFER, toAccount = account(owner)))

        assertNull(sheet.cell(1, "To amount"))
        assertNull(sheet.cell(1, "To currency"))
    }

    @Test
    fun `a trade is written with its side, ticker, quantity and unit price`() {
        val broker = account(owner, currency = "USD", type = AccountType.BROKER)
        val sheet =
            export(
                transaction(
                    type = TransactionType.TRADE,
                    account = broker,
                    tradeSide = TradeSide.BUY,
                    ticker = "BTC/USD",
                    quantity = "0.0001234567",
                    unitPrice = "65000.5",
                ),
            )

        assertEquals(listOf("BUY", "BTC/USD"), listOf(sheet.text(1, "Trade side"), sheet.text(1, "Ticker")))
        assertEquals(
            listOf(0.0001234567, 65000.5),
            listOf("Quantity", "Unit price").map { sheet.cell(1, it)?.numericCellValue },
        )
    }

    @Test
    fun `a value the transaction does not have is an empty cell, not a zero`() {
        val sheet = export(transaction())

        assertEquals(
            emptyList(),
            listOf("To account", "Category", "Topic", "To amount", "Trade side", "Ticker", "Quantity", "Unit price", "Note")
                .mapNotNull { sheet.cell(1, it) },
        )
    }

    @Test
    fun `a note that looks like a formula is written as text`() {
        val sheet = export(transaction(note = "=1+1"))

        assertEquals(CellType.STRING, sheet.cell(1, "Note")?.cellType)
    }

    @Test
    fun `the filters are passed to the transaction list unchanged`() {
        val category = category(owner)
        val topic = topic(owner)
        every { transactionService.list(any(), any(), any(), any(), any(), any()) } returns emptyList()

        service.transactionsWorkbook(
            owner = owner,
            from = from,
            to = to,
            accountId = account.idValue,
            categoryId = category.idValue,
            topicId = topic.idValue,
        )

        verify {
            transactionService.list(
                owner = owner,
                from = from,
                to = to,
                accountId = account.idValue,
                categoryId = category.idValue,
                topicId = topic.idValue,
            )
        }
    }

    @Test
    fun `a filter that is not the caller's fails the export as it fails the list`() {
        every { transactionService.list(any(), any(), any(), any(), any(), any()) } throws
            NotFoundException("Account was not found")

        assertThrows<NotFoundException> {
            service.transactionsWorkbook(owner = owner, from = from, to = to, accountId = account.idValue)
        }
    }

    private fun export(vararg transactions: Transaction): Sheet {
        every { transactionService.list(owner, from, to, null, null, null) } returns transactions.toList()
        val bytes = service.transactionsWorkbook(owner = owner, from = from, to = to)
        return XSSFWorkbook(bytes.inputStream()).use { it.getSheet("Transactions") }
    }

    private fun transaction(
        type: TransactionType = TransactionType.EXPENSE,
        account: Account = this.account,
        amount: String = "1500",
        exchangeRate: String = "1",
        toAccount: Account? = null,
        toAmount: String? = null,
        occurredOn: LocalDate = LocalDate.of(2026, 9, 10),
        category: Category? = null,
        topic: Topic? = null,
        note: String? = null,
        tradeSide: TradeSide? = null,
        ticker: String? = null,
        quantity: String? = null,
        unitPrice: String? = null,
    ) = Transaction(
        type = type,
        amount = BigDecimal(amount),
        currency = account.currency,
        exchangeRate = BigDecimal(exchangeRate),
        amountKzt = BigDecimal(amount).multiply(BigDecimal(exchangeRate)).setScale(2, RoundingMode.HALF_UP),
        toAmount = toAmount?.let(::BigDecimal),
        occurredOn = occurredOn,
        account = account,
        toAccount = toAccount,
        category = category,
        note = note,
        topic = topic,
        tradeSide = tradeSide,
        ticker = ticker,
        quantity = quantity?.let(::BigDecimal),
        unitPrice = unitPrice?.let(::BigDecimal),
    ).withId()
}

private fun Sheet.column(header: String): Int = getRow(0).first { it.stringCellValue == header }.columnIndex

private fun Sheet.cell(
    row: Int,
    header: String,
) = getRow(row).getCell(column(header), Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)

private fun Sheet.text(
    row: Int,
    header: String,
): String? = cell(row, header)?.stringCellValue
