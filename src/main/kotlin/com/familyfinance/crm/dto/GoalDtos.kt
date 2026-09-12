package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.GoalStatus
import com.familyfinance.crm.domain.GoalType
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

data class CreateGoalRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
    @field:NotNull(message = "is required")
    val type: GoalType?,
    @field:NotNull(message = "is required")
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val targetAmount: BigDecimal?,
    val targetDate: LocalDate? = null,
    @field:NotNull(message = "is required")
    val linkedAccountId: UUID?,
)

/**
 * The linked account is fixed once set — progress is measured against it, so
 * swapping it would silently rewrite the goal's history of progress.
 * `targetDate` is an [Optional] so an explicit `null` clears the deadline.
 */
data class UpdateGoalRequest(
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String? = null,
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val targetAmount: BigDecimal? = null,
    val targetDate: Optional<LocalDate>? = null,
    val status: GoalStatus? = null,
)

data class GoalResponse(
    val id: UUID,
    val name: String,
    val type: GoalType,
    val targetAmount: BigDecimal,
    val targetDate: LocalDate?,
    val linkedAccount: AccountResponse,
    val status: GoalStatus,
    /** Clamped to 0–100. */
    val progressPercent: BigDecimal,
    /** Derived from progress, never a stored state transition. */
    val achieved: Boolean,
)
