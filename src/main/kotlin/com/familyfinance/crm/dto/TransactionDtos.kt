package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.TradeSide
import com.familyfinance.crm.domain.TransactionType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

data class CreateTransactionRequest(
    @field:NotNull(message = "is required")
    val type: TransactionType?,
    /**
     * Required for everything except a `TRADE`, where it must be absent: a
     * trade's amount is `quantity × unitPrice`.
     */
    val amount: BigDecimal? = null,
    @field:NotNull(message = "is required")
    val accountId: UUID?,
    /** Required for `TRANSFER`, rejected otherwise. */
    val toAccountId: UUID? = null,
    /** Required for a cross-currency `TRANSFER`, rejected otherwise. */
    val toAmount: BigDecimal? = null,
    /**
     * KZT per 1 unit of the account's currency. Required when the account is
     * not in KZT, and must be absent or 1 when it is.
     */
    val exchangeRate: BigDecimal? = null,
    val categoryId: UUID? = null,
    /** The undertaking this belongs to; `INCOME`/`EXPENSE` only. */
    val topicId: UUID? = null,
    /** Defaults to today in the app timezone; may not be in the future. */
    val occurredOn: LocalDate? = null,
    @field:Size(max = 1000, message = "must be at most 1000 characters")
    val note: String? = null,
    /** `TRADE` only, and required there: `BUY`, `SELL`, or `OPENING` for an asset already held. */
    val tradeSide: TradeSide? = null,
    /** `TRADE` only, and required there. Stored uppercase. */
    @field:Size(max = 32, message = "must be at most 32 characters")
    val ticker: String? = null,
    /** `TRADE` only, and required there. May be fractional, to ten decimals. */
    val quantity: BigDecimal? = null,
    /** `TRADE` only, and required there: the price of one unit in the account's currency. */
    val unitPrice: BigDecimal? = null,
)

/**
 * Only amount/toAmount/date/category/note are editable, plus a trade's ticker,
 * quantity and price. Changing type, trade side, account, or destination
 * account means delete and recreate.
 */
data class UpdateTransactionRequest(
    /** Not valid for a `TRADE`: correct its `quantity` or `unitPrice` instead. */
    val amount: BigDecimal? = null,
    /**
     * The destination figure of a cross-currency `TRANSFER`. Required
     * alongside a changed `amount` on one, since the two sides are credited
     * independently and correcting only the source would leave the
     * destination holding the old figure.
     */
    val toAmount: BigDecimal? = null,
    /** Correcting a mistyped rate re-derives the KZT figure. */
    val exchangeRate: BigDecimal? = null,
    val occurredOn: LocalDate? = null,
    val categoryId: Optional<UUID>? = null,
    /** An explicit `null` detaches the transaction from its topic. */
    val topicId: Optional<UUID>? = null,
    val note: Optional<String>? = null,
    /** `TRADE` only. */
    @field:Size(max = 32, message = "must be at most 32 characters")
    val ticker: String? = null,
    /** `TRADE` only; re-derives the amount and re-applies the balance difference. */
    val quantity: BigDecimal? = null,
    /** `TRADE` only; re-derives the amount and re-applies the balance difference. */
    val unitPrice: BigDecimal? = null,
)

data class TransactionResponse(
    val id: UUID,
    val type: TransactionType,
    val amount: BigDecimal,
    val currency: String,
    val toAmount: BigDecimal?,
    val exchangeRate: BigDecimal,
    /** What every total is computed from. */
    val amountKzt: BigDecimal,
    val occurredOn: LocalDate,
    val accountId: UUID,
    val toAccountId: UUID?,
    val category: CategoryResponse?,
    /** Embedded like the category, so a list needs no second lookup. */
    val topic: TopicRef?,
    val note: String?,
    /** The four below are set on a `TRADE` and null on everything else. */
    val tradeSide: TradeSide?,
    val ticker: String?,
    val quantity: BigDecimal?,
    val unitPrice: BigDecimal?,
)

/**
 * Monthly totals for the caller. `ADJUSTMENT` is excluded — a correction
 * isn't spending — and so are `TRANSFER`, which only moves money between the
 * caller's own accounts, and `TRADE`, which turns cash into an asset.
 */
data class MonthlySummaryResponse(
    val month: String,
    /** Always KZT — foreign amounts are converted at each transaction's own rate. */
    val currency: String,
    val from: LocalDate,
    val to: LocalDate,
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val net: BigDecimal,
    val expenseByCategory: List<CategorySummary>,
    val incomeByCategory: List<CategorySummary>,
)

data class CategorySummary(
    val categoryId: UUID?,
    val categoryName: String?,
    val total: BigDecimal,
)
