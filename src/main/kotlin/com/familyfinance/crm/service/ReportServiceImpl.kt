package com.familyfinance.crm.service

import com.familyfinance.crm.domain.BASE_CURRENCY
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class ReportServiceImpl(
    private val transactionService: TransactionService,
) : ReportService {
    override fun transactionsWorkbook(
        owner: User,
        from: LocalDate,
        to: LocalDate,
        accountId: UUID?,
        categoryId: UUID?,
        topicId: UUID?,
    ): ByteArray {
        // Ownership, the one-year cap and the filters are all the list's own,
        // and it returns every association a row reads already fetched.
        val transactions =
            transactionService.list(
                owner = owner,
                from = from,
                to = to,
                accountId = accountId,
                categoryId = categoryId,
                topicId = topicId,
            )
        // Built in memory: a year of one household's ledger is a few thousand
        // rows, and POI's streaming workbook would spool to temp files on disk.
        return XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(SHEET_NAME)
            val styles = cellStyles(workbook)

            val header = sheet.createRow(0)
            COLUMNS.forEachIndexed { index, column ->
                header.createCell(index).apply {
                    setCellValue(column.header)
                    cellStyle = styles.header
                }
                // Fixed widths: auto-sizing measures text with AWT fonts, which
                // the JRE-only Alpine image the app runs in does not have.
                sheet.setColumnWidth(index, column.widthInChars * COLUMN_WIDTH_UNIT)
            }
            transactions.forEachIndexed { index, transaction ->
                val row = sheet.createRow(index + 1)
                COLUMNS.forEachIndexed { columnIndex, column ->
                    row.write(columnIndex, column.value(transaction), styles.of(column.format))
                }
            }
            sheet.createFreezePane(0, 1)
            sheet.setAutoFilter(CellRangeAddress(0, transactions.size, 0, COLUMNS.lastIndex))

            ByteArrayOutputStream().also(workbook::write).toByteArray()
        }
    }
}

private const val SHEET_NAME = "Transactions"

/** POI measures a column's width in 1/256ths of a character. */
private const val COLUMN_WIDTH_UNIT = 256

private enum class CellFormat(
    val pattern: String?,
) {
    TEXT(null),
    DATE("yyyy-mm-dd"),
    MONEY("#,##0.00"),

    /** A rate or a crypto quantity keeps the decimals it has, and no more. */
    PRECISE("#,##0.00########"),
}

private class Column(
    val header: String,
    val widthInChars: Int,
    val format: CellFormat = CellFormat.TEXT,
    val value: (Transaction) -> Any?,
)

/**
 * Amounts are written as the ledger stores them — positive, with `Type` (and
 * `Trade side`) saying which way the money moved; only an `ADJUSTMENT` is
 * signed. `Amount` is in the account's `Currency`, `Amount KZT` is the figure
 * every total in the app is summed from.
 */
private val COLUMNS =
    listOf(
        Column("Date", widthInChars = 12, format = CellFormat.DATE) { it.occurredOn },
        Column("Type", widthInChars = 12) { it.type.name },
        Column("Account", widthInChars = 24) { it.account.name },
        Column("To account", widthInChars = 24) { it.toAccount?.name },
        Column("Category", widthInChars = 24) { it.category?.name },
        Column("Topic", widthInChars = 24) { it.topic?.name },
        // A family purchase, visible to the whole household; empty for a personal one.
        Column("Family", widthInChars = 10) { transaction -> "yes".takeIf { transaction.isFamily } },
        Column("Amount", widthInChars = 16, format = CellFormat.MONEY) { it.amount },
        Column("Currency", widthInChars = 10) { it.currency },
        Column("Rate to $BASE_CURRENCY", widthInChars = 14, format = CellFormat.PRECISE) { it.exchangeRate },
        Column("Amount $BASE_CURRENCY", widthInChars = 16, format = CellFormat.MONEY) { it.amountKzt },
        // Set on a cross-currency transfer only, in the destination's currency.
        Column("To amount", widthInChars = 16, format = CellFormat.MONEY) { it.toAmount },
        Column("To currency", widthInChars = 12) { transaction -> transaction.toAmount?.let { transaction.toAccount?.currency } },
        Column("Trade side", widthInChars = 12) { it.tradeSide?.name },
        Column("Ticker", widthInChars = 12) { it.ticker },
        Column("Quantity", widthInChars = 16, format = CellFormat.PRECISE) { it.quantity },
        Column("Unit price", widthInChars = 16, format = CellFormat.PRECISE) { it.unitPrice },
        Column("Note", widthInChars = 40) { it.note },
    )

private class CellStyles(
    val header: XSSFCellStyle,
    private val byFormat: Map<CellFormat, XSSFCellStyle>,
) {
    fun of(format: CellFormat): XSSFCellStyle? = byFormat[format]
}

/** One style per format for the whole sheet; a workbook caps how many it can hold. */
private fun cellStyles(workbook: XSSFWorkbook): CellStyles {
    val formats = workbook.createDataFormat()
    return CellStyles(
        header = workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true }) },
        byFormat =
            CellFormat.entries
                .mapNotNull { format -> format.pattern?.let { format to it } }
                .associate { (format, pattern) ->
                    format to workbook.createCellStyle().apply { dataFormat = formats.getFormat(pattern) }
                },
    )
}

/**
 * A missing value leaves the cell empty rather than writing a zero. Numbers
 * and dates go in as real ones, so Excel can sum and sort them; text goes in
 * as text, so a note that starts with `=` is never evaluated as a formula.
 */
private fun Row.write(
    index: Int,
    value: Any?,
    style: CellStyle?,
) {
    if (value == null) return
    val cell = createCell(index)
    when (value) {
        is LocalDate -> cell.setCellValue(value)
        is BigDecimal -> cell.setCellValue(value.toDouble())
        else -> cell.setCellValue(value.toString())
    }
    style?.let { cell.cellStyle = it }
}
