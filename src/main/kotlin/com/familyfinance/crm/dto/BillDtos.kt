package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.BASE_CURRENCY
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class CreateBillRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
    @field:NotNull(message = "is required")
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val amount: BigDecimal?,
    @field:NotNull(message = "is required")
    val dueDate: LocalDate?,
    @field:Size(min = 3, max = 3, message = "must be exactly 3 characters")
    val currency: String = BASE_CURRENCY,
)

/**
 * Expands a pattern into ordinary bills in one call — there is no recurrence
 * engine and nothing regenerates rows later. Once created they are just normal
 * bills that happen to share a `batchId`.
 */
data class CreateBillBatchRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
    @field:NotNull(message = "is required")
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val amount: BigDecimal?,
    /** Clamped to the last day of any month too short for it. */
    @field:NotNull(message = "is required")
    @field:Min(value = 1, message = "must be between 1 and 31")
    @field:Max(value = 31, message = "must be between 1 and 31")
    val dayOfMonth: Int?,
    /** Inclusive, `yyyy-MM`. */
    @field:NotBlank(message = "is required")
    val startMonth: String,
    @field:NotBlank(message = "is required")
    val endMonth: String,
    @field:Size(min = 3, max = 3, message = "must be exactly 3 characters")
    val currency: String = BASE_CURRENCY,
)

data class UpdateBillRequest(
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String? = null,
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val amount: BigDecimal? = null,
    @field:Size(min = 3, max = 3, message = "must be exactly 3 characters")
    val currency: String? = null,
    val dueDate: LocalDate? = null,
    /** Marking paid does not create a transaction — record that separately. */
    val isPaid: Boolean? = null,
)

data class BillResponse(
    val id: UUID,
    val name: String,
    val amount: BigDecimal,
    val currency: String,
    val dueDate: LocalDate,
    val isPaid: Boolean,
    /** Derived, never stored: unpaid and past its due date in the app timezone. */
    val overdue: Boolean,
    val batchId: UUID?,
)
