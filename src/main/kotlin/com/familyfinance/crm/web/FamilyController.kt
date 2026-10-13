package com.familyfinance.crm.web

import com.familyfinance.crm.dto.FamilyPurchasesResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.FamilyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/family")
@Tag(name = "Family", description = "What the household spends together")
class FamilyController(
    private val familyService: FamilyService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping("/purchases")
    @Operation(
        summary = "Family purchases of every member",
        description =
            "Every EXPENSE marked `isFamily`, whoever recorded it, newest first, with the total and its " +
                "breakdown by member and by category name, in KZT. `from` and `to` are required and the " +
                "range is capped at one year. Shows the amount, date, category name, note and who paid — " +
                "never the account. Personal transactions are never returned. To mark, edit or delete a " +
                "purchase its owner uses the transaction endpoints.",
    )
    @ApiResponse(responseCode = "200", description = "The purchases in the range and their totals")
    @ApiResponse(
        responseCode = "400",
        description = "Range is inverted or longer than a year",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun purchases(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): FamilyPurchasesResponse = familyService.purchases(reader = currentUser.require(), from = from, to = to)
}
