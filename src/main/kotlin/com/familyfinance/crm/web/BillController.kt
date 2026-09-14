package com.familyfinance.crm.web

import com.familyfinance.crm.dto.BillResponse
import com.familyfinance.crm.dto.CreateBillBatchRequest
import com.familyfinance.crm.dto.CreateBillRequest
import com.familyfinance.crm.dto.UpdateBillRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.BillService
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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/bills")
@Tag(name = "Bills", description = "What's due and when — no recurrence engine, no reconciliation")
class BillController(
    private val billService: BillService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "List your bills",
        description =
            "Two independent filters, combinable. `month=2026-09` matches the due date — the calendar " +
                "grid. `unpaid=true` returns everything still owed, including bills that fell due in an " +
                "earlier month, which a month view by definition cannot show; `unpaid=false` returns " +
                "settled ones. Omit both for every bill. `overdue` is derived from today in Asia/Almaty, " +
                "so it is never stale.",
    )
    @ApiResponse(responseCode = "200", description = "Your bills, earliest due date first")
    fun list(
        @RequestParam(required = false) month: String?,
        @RequestParam(required = false) unpaid: Boolean?,
    ): List<BillResponse> =
        billService
            .list(
                owner = currentUser.require(),
                month = month?.let(::parseMonth),
                unpaid = unpaid,
            ).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a bill",
        description = "A due date may be in the past — that just means it is already overdue.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    fun create(
        @Valid @RequestBody request: CreateBillRequest,
    ): BillResponse = billService.create(currentUser.require(), request).toResponse()

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a series of bills from a pattern",
        description =
            "Expands `dayOfMonth` across `startMonth`–`endMonth` inclusive into ordinary bills " +
                "sharing one `batchId`. A day past the end of a short month clamps to its last day. " +
                "Capped at 120 rows per call. Nothing regenerates them later, and editing one row " +
                "never affects its siblings.",
    )
    @ApiResponse(responseCode = "201", description = "The created bills")
    @ApiResponse(
        responseCode = "400",
        description = "Inverted month range, or more than 120 rows",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun createBatch(
        @Valid @RequestBody request: CreateBillBatchRequest,
    ): List<BillResponse> = billService.createBatch(currentUser.require(), request).map { it.toResponse() }

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update a bill",
        description =
            "Name, amount, currency, due date, or `isPaid`. Marking one paid does **not** create a " +
                "transaction — record the spend separately.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBillRequest,
    ): BillResponse = billService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft delete one bill", description = "Siblings in the same batch are untouched.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    fun delete(
        @PathVariable id: UUID,
    ) = billService.softDelete(id, currentUser.require())

    @DeleteMapping("/batch/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Soft delete a whole batch",
        description = "Every bill created in that one batch call, however many remain.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(
        responseCode = "404",
        description = "No bills with that batch id",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun deleteBatch(
        @PathVariable batchId: UUID,
    ) = billService.softDeleteBatch(batchId, currentUser.require())
}
