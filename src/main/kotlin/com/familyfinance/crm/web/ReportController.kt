package com.familyfinance.crm.web

import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.ReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

const val XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Files to download")
class ReportController(
    private val reportService: ReportService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping("/transactions.xlsx")
    @Operation(
        summary = "Export transactions to Excel",
        description =
            "The same transactions as GET /api/v1/transactions for the same parameters — your own, " +
                "`from` and `to` required, at most one year, optionally narrowed by `accountId`, " +
                "`categoryId` and `topicId` — as an .xlsx file, one row each, newest first. " +
                "Amounts are positive as in the API; the Type column says which way the money moved.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "The workbook, as an attachment",
        content = [Content(mediaType = XLSX_MEDIA_TYPE, schema = Schema(type = "string", format = "binary"))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "Range is inverted or longer than a year",
        content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "The account, category or topic filter is not one of yours",
        content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
    )
    fun transactions(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) accountId: UUID?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(required = false) topicId: UUID?,
    ): ResponseEntity<ByteArray> {
        val workbook =
            reportService.transactionsWorkbook(
                owner = currentUser.require(),
                from = from,
                to = to,
                accountId = accountId,
                categoryId = categoryId,
                topicId = topicId,
            )
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename("transactions_${from}_$to.xlsx")
                    .build()
                    .toString(),
            ).body(workbook)
    }
}
