package com.familyfinance.crm.web

import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.dto.AccountResponse
import com.familyfinance.crm.dto.CreateAccountRequest
import com.familyfinance.crm.dto.InvestmentsResponse
import com.familyfinance.crm.dto.ReconcileRequest
import com.familyfinance.crm.dto.RenameTickerRequest
import com.familyfinance.crm.dto.TransactionResponse
import com.familyfinance.crm.dto.UpdateAccountRequest
import com.familyfinance.crm.dto.toInvestmentsResponse
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.AccountService
import com.familyfinance.crm.service.InvestmentService
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
    private val investmentService: InvestmentService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "List accounts",
        description =
            "No pagination — the list is small by design. `scope` selects whose: `OWN` (the default, " +
                "and exactly what this returned before sharing existed), `SHARED` for accounts other " +
                "members have shared with you, or `ALL` for both, each row badged with `access`. " +
                "Balances are never totalled across owners.",
    )
    @ApiResponse(responseCode = "200", description = "The accounts in scope")
    fun list(
        @RequestParam(defaultValue = "OWN") scope: ShareScope,
    ): List<AccountResponse> = accountService.list(currentUser.require(), scope).map { it.toResponse() }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get one account",
        description = "Yours, or one shared with you. Anything else is a 404, indistinguishable from a missing id.",
    )
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(
        responseCode = "404",
        description = "No such account, and not shared with you",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun get(
        @PathVariable id: UUID,
    ): AccountResponse = accountService.getReadableBy(id, currentUser.require()).toResponse()

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
                "Transfers appear for both the source and the destination account. Readable by a viewer " +
                "of this account: a balance without its history explains nothing. A transfer to an " +
                "account that was not shared still carries its `toAccountId`, which the viewer simply " +
                "cannot resolve — that account is healthy, just not theirs to see.",
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
            .history(accountId = id, reader = currentUser.require(), from = from, to = to)
            .map { it.toResponse() }

    @GetMapping("/{id}/holdings")
    @Operation(
        summary = "What one account holds",
        description =
            "The same shape as GET /api/v1/investments, narrowed to this account. Readable by a viewer " +
                "of the account, like its history. Empty for an account that has never recorded a TRADE.",
    )
    @ApiResponse(responseCode = "200", description = "The holdings and their totals")
    @ApiResponse(
        responseCode = "404",
        description = "No such account, and not shared with you",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun holdings(
        @PathVariable id: UUID,
    ): InvestmentsResponse =
        investmentService
            .accountHoldings(accountId = id, reader = currentUser.require())
            .toInvestmentsResponse()

    @PostMapping("/{id}/holdings/rename")
    @Operation(
        summary = "Rename a ticker",
        description =
            "For an asset that changed its name: rewrites `from` to `to` on every trade of it in this " +
                "account, in one step. `to` must be in the format the account requires. Owner only. " +
                "Returns the account's holdings afterwards. Its price appears after the next refresh.",
    )
    @ApiResponse(responseCode = "200", description = "The account's holdings after the rename")
    @ApiResponse(
        responseCode = "400",
        description = "`from` is not traded in this account, or `to` is in the wrong format",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "`to` is already traded in this account",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun renameTicker(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RenameTickerRequest,
    ): InvestmentsResponse =
        investmentService
            .renameTicker(accountId = id, owner = currentUser.require(), request = request)
            .toInvestmentsResponse()
}
