package com.familyfinance.crm.dto

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
    @field:NotNull(message = "is required")
    val amount: BigDecimal?,
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
    /** Defaults to today in the app timezone; may not be in the future. */
    val occurredOn: LocalDate? = null,
    @field:Size(max = 1000, message = "must be at most 1000 characters")
    val note: String? = null,
)

/**
 * Only amount/date/category/note are editable. Changing type, account, or
 * destination account means delete and recreate.
 */
data class UpdateTransactionRequest(
    val amount: BigDecimal? = null,
    /** Correcting a mistyped rate re-derives the KZT figure. */
    val exchangeRate: BigDecimal? = null,
    val occurredOn: LocalDate? = null,
    val categoryId: Optional<UUID>? = null,
    val note: Optional<String>? = null,
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
    val note: String?,
)

/**
 * Monthly totals for the caller. `ADJUSTMENT` is excluded — a correction
 * isn't spending — and so is `TRANSFER`, which only moves money between the
 * caller's own accounts.
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
