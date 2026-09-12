package com.familyfinance.crm.web

import com.familyfinance.crm.dto.AccountResponse
import com.familyfinance.crm.dto.CreateAccountRequest
import com.familyfinance.crm.dto.ReconcileRequest
import com.familyfinance.crm.dto.TransactionResponse
import com.familyfinance.crm.dto.UpdateAccountRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.AccountService
import com.familyfinance.crm.service.TransactionService
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
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Where money sits, and the history of each")
class AccountController(
    private val accountService: AccountService,
    // Account history and reconciliation are ledger operations that happen to
    // hang off an account path, so they delegate to the transaction service.
    private val transactionService: TransactionService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "List your accounts", description = "No pagination — the list is small by design.")
    @ApiResponse(responseCode = "200", description = "Your accounts")
    fun list(): List<AccountResponse> = accountService.list(currentUser.require()).map { it.toResponse() }

    @GetMapping("/{id}")
    @Operation(summary = "Get one account")
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(
        responseCode = "404",
        description = "No such account",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun get(
        @PathVariable id: UUID,
    ): AccountResponse = accountService.getOwnedBy(id, currentUser.require()).toResponse()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create an account",
        description = "`balance` is the opening balance. A CASH account has no bank.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    fun create(
        @Valid @RequestBody request: CreateAccountRequest,
    ): AccountResponse = accountService.create(currentUser.require(), request).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update an account",
        description = "Name and bank only. The balance is owned by the ledger — correct it with /reconcile.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateAccountRequest,
    ): AccountResponse = accountService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft delete an account", description = "Its transactions stay in history.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    fun delete(
        @PathVariable id: UUID,
    ) = accountService.softDelete(id, currentUser.require())

    @PostMapping("/{id}/reconcile")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Correct a drifted balance",
        description =
            "Takes the balance the bank actually reports and writes an ADJUSTMENT for the difference, " +
                "so the ledger still explains the balance.",
    )
    @ApiResponse(responseCode = "201", description = "The ADJUSTMENT transaction that was written")
    @ApiResponse(
        responseCode = "400",
        description = "The balance already matches",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun reconcile(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReconcileRequest,
    ): TransactionResponse = transactionService.reconcile(id, currentUser.require(), request).toResponse()

    @GetMapping("/{id}/transactions")
    @Operation(
        summary = "Account history",
        description =
            "`from` and `to` are required and the range is capped at one year — there is no pagination. " +
                "Transfers appear for both the source and the destination account.",
    )
    @ApiResponse(responseCode = "200", description = "Transactions in the range, newest first")
    @ApiResponse(
        responseCode = "400",
        description = "Range is inverted or longer than a year",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun history(
        @PathVariable id: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<TransactionResponse> =
        transactionService
            .history(accountId = id, owner = currentUser.require(), from = from, to = to)
            .map { it.toResponse() }
}
