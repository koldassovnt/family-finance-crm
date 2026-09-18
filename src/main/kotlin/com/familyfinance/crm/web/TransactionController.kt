package com.familyfinance.crm.web

import com.familyfinance.crm.dto.CreateTransactionRequest
import com.familyfinance.crm.dto.MonthlySummaryResponse
import com.familyfinance.crm.dto.TransactionResponse
import com.familyfinance.crm.dto.UpdateTransactionRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.TransactionService
import com.familyfinance.crm.service.parseMonth
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
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
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "The ledger: one row per movement of money")
class TransactionController(
    private val transactionService: TransactionService,
    private val currentUser: CurrentUserProvider,
    private val clock: Clock,
) {
    @GetMapping
    @Operation(
        summary = "List transactions across every account",
        description =
            "`from` and `to` are required and the range is capped at one year — there is no pagination. " +
                "Optionally narrowed by `accountId` (matching either side of a transfer) and `categoryId`. " +
                "Newest first. Per-account history with the same window lives at " +
                "GET /api/v1/accounts/{id}/transactions.",
    )
    @ApiResponse(responseCode = "200", description = "Transactions in the range, newest first")
    @ApiResponse(
        responseCode = "400",
        description = "Range is inverted or longer than a year",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "The account or category filter is not one of yours",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) accountId: UUID?,
        @RequestParam(required = false) categoryId: UUID?,
    ): List<TransactionResponse> =
        transactionService
            .list(
                owner = currentUser.require(),
                from = from,
                to = to,
                accountId = accountId,
                categoryId = categoryId,
            ).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Record a transaction",
        description =
            "INCOME, EXPENSE or TRANSFER. A cross-currency TRANSFER requires `toAmount`. " +
                "ADJUSTMENT is not accepted here — use POST /api/v1/accounts/{id}/reconcile.",
    )
    @ApiResponse(responseCode = "201", description = "Recorded; balances updated")
    @ApiResponse(
        responseCode = "400",
        description = "Invalid amount, future date, or a toAmount that doesn't match the currencies",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun create(
        @Valid @RequestBody request: CreateTransactionRequest,
    ): TransactionResponse = transactionService.create(currentUser.require(), request).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        summary = "Edit a transaction",
        description =
            "Amount, destination amount, date, category and note only — changing the type or the accounts " +
                "means delete and recreate. A changed amount re-applies the balance difference. " +
                "On a cross-currency TRANSFER the two sides are credited independently, so a changed " +
                "amount must be accompanied by `toAmount`.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTransactionRequest,
    ): TransactionResponse = transactionService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Soft delete a transaction",
        description = "Also reverses its effect on the balances it touched.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted; balances reversed")
    fun delete(
        @PathVariable id: UUID,
    ) = transactionService.softDelete(id, currentUser.require())

    @GetMapping("/summary")
    @Operation(
        summary = "Monthly summary",
        description =
            "Totals and per-category breakdowns for one month, e.g. `2026-09`; defaults to the current month " +
                "in Asia/Almaty. TRANSFER and ADJUSTMENT are excluded — neither is spending.",
    )
    @ApiResponse(responseCode = "200", description = "The summary")
    fun summary(
        @RequestParam(required = false) month: String?,
    ): MonthlySummaryResponse =
        transactionService.monthlySummary(
            owner = currentUser.require(),
            month = month?.let(::parseMonth) ?: YearMonth.now(clock),
        )
}
