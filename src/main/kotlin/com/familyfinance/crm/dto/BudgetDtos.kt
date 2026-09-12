package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.BudgetPeriod
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

data class CreateBudgetRequest(
    @field:NotNull(message = "is required")
    val categoryId: UUID?,
    @field:NotNull(message = "is required")
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val limitAmount: BigDecimal?,
    /** `MONTHLY` is the only period; defaults to it. */
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    @field:Min(value = 1, message = "must be between 1 and 100")
    @field:Max(value = 100, message = "must be between 1 and 100")
    val alertThresholdPercent: Int? = null,
)

/**
 * Limit and alert threshold only — a budget's category is fixed, since usage
 * for a different category is a different budget. `alertThresholdPercent` is
 * an [Optional] so an explicit `null` clears the cue.
 */
data class UpdateBudgetRequest(
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val limitAmount: BigDecimal? = null,
    val alertThresholdPercent: Optional<Int>? = null,
)

data class BudgetResponse(
    val id: UUID,
    val category: CategoryResponse,
    val limitAmount: BigDecimal,
    val period: BudgetPeriod,
    val alertThresholdPercent: Int?,
    /** The month usage was computed for, in the app timezone. */
    val month: String,
    val spent: BigDecimal,
    /** Negative once the limit is exceeded — overspend is a thing to show, not to hide. */
    val remaining: BigDecimal,
    /** Deliberately not capped at 100: the UI needs to see how far past the limit this is. */
    val percentUsed: BigDecimal,
)
