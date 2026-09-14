package com.familyfinance.crm.web

import com.familyfinance.crm.dto.BudgetResponse
import com.familyfinance.crm.dto.CreateBudgetRequest
import com.familyfinance.crm.dto.UpdateBudgetRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.BudgetService
import com.familyfinance.crm.service.parseMonth
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets", description = "Per-category monthly spending limits with usage computed on read")
class BudgetController(
    private val budgetService: BudgetService,
    private val currentUser: CurrentUserProvider,
    private val clock: Clock,
) {
    @GetMapping
    @Operation(
        summary = "List your budgets with a month's usage",
        description =
            "Defaults to the current month in Asia/Almaty; pass `month=2026-09` for any past month — " +
                "a future one is rejected, since its usage could only ever be zero. " +
                "Each budget reports the limit that actually applied that month, and usage summed " +
                "from that month's EXPENSE transactions in KZT, rolled up from any sub-categories. " +
                "TRANSFER and ADJUSTMENT are excluded, and `percentUsed` is not capped at 100. " +
                "A budget that did not exist yet is simply absent from that month.",
    )
    @ApiResponse(responseCode = "200", description = "Your budgets for that month")
    fun list(
        @RequestParam(required = false) month: String?,
    ): List<BudgetResponse> =
        budgetService
            .list(
                owner = currentUser.require(),
                month = month?.let(::parseMonth) ?: YearMonth.now(clock),
            ).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a budget",
        description = "One active budget per category. `alertThresholdPercent` is a display cue only — nothing alerts.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(
        responseCode = "409",
        description = "This category already has an active budget",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun create(
        @Valid @RequestBody request: CreateBudgetRequest,
    ): BudgetResponse = budgetService.create(currentUser.require(), request).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update a budget",
        description =
            "Limit and alert threshold only — the category is fixed. Takes effect from the current " +
                "month: past months keep the limit that applied then. " +
                "Send `alertThresholdPercent: null` to clear the cue.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBudgetRequest,
    ): BudgetResponse = budgetService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Soft delete a budget",
        description =
            "Stops it from this month onward and frees the category to be budgeted again. " +
                "Past months keep reporting it against the limit that applied then.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted")
    fun delete(
        @PathVariable id: UUID,
    ) = budgetService.softDelete(id, currentUser.require())
}
