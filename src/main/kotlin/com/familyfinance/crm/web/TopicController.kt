package com.familyfinance.crm.web

import com.familyfinance.crm.domain.TopicStatus
import com.familyfinance.crm.dto.AttachTransactionsRequest
import com.familyfinance.crm.dto.CreateTopicRequest
import com.familyfinance.crm.dto.TopicDetailResponse
import com.familyfinance.crm.dto.TopicResponse
import com.familyfinance.crm.dto.TransactionResponse
import com.familyfinance.crm.dto.UpdateTopicRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.TopicService
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
@RequestMapping("/api/v1/topics")
@Tag(
    name = "Topics",
    description = "A trip, a renovation, a wedding: one undertaking's transactions, viewed and totalled together",
)
class TopicController(
    private val topicService: TopicService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "List your topics with their totals",
        description =
            "Newest start date first, undated last. `status=ACTIVE` or `CLOSED` filters; omit for all. " +
                "Every total is summed on read from the attached transactions, in KZT.",
    )
    @ApiResponse(responseCode = "200", description = "Your topics")
    fun list(
        @RequestParam(required = false) status: TopicStatus?,
    ): List<TopicResponse> = topicService.list(currentUser.require(), status).map { it.toResponse() }

    @GetMapping("/{id}")
    @Operation(
        summary = "One topic with its breakdowns",
        description = "The list figures plus per-category expense and income breakdowns for a chart.",
    )
    @ApiResponse(responseCode = "200", description = "The topic")
    fun get(
        @PathVariable id: UUID,
    ): TopicDetailResponse = topicService.get(id, currentUser.require()).toResponse()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a topic",
        description = "Starts ACTIVE. `plannedAmount` is a display figure in KZT — nothing alerts on it.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(
        responseCode = "409",
        description = "You already have a topic with this name",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun create(
        @Valid @RequestBody request: CreateTopicRequest,
    ): TopicResponse = topicService.create(currentUser.require(), request).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update a topic",
        description =
            "Name, description, dates, planned amount and status. Send an explicit `null` to clear a " +
                "nullable field. CLOSED only hides it from the transaction form's picker — it still " +
                "accepts attachments, since a late invoice is normal.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTopicRequest,
    ): TopicResponse = topicService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Soft delete a topic",
        description =
            "Its transactions are untouched and keep pointing at it — a topic is a view, and deleting " +
                "a view must not delete money.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted")
    fun delete(
        @PathVariable id: UUID,
    ) = topicService.softDelete(id, currentUser.require())

    @GetMapping("/{id}/transactions")
    @Operation(
        summary = "Everything attached to this topic",
        description =
            "Newest first, with no date range required: membership is itself the bound, unlike " +
                "GET /api/v1/transactions where a range is mandatory.",
    )
    @ApiResponse(responseCode = "200", description = "The topic's transactions")
    fun transactions(
        @PathVariable id: UUID,
    ): List<TransactionResponse> = topicService.transactions(id, currentUser.require()).map { it.toResponse() }

    @GetMapping("/{id}/candidates")
    @Operation(
        summary = "Suggest transactions to attach",
        description =
            "Your unattached INCOME/EXPENSE transactions inside the topic's start..end window, newest " +
                "first. A suggestion only — nothing is attached automatically, because dates are a weak " +
                "signal: a flight booked in March belongs to a June trip.",
    )
    @ApiResponse(responseCode = "200", description = "Candidates")
    @ApiResponse(
        responseCode = "400",
        description = "The topic has no start and end date to search between",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun candidates(
        @PathVariable id: UUID,
    ): List<TransactionResponse> = topicService.candidates(id, currentUser.require()).map { it.toResponse() }

    @PostMapping("/{id}/transactions")
    @Operation(
        summary = "Attach transactions in bulk",
        description =
            "All-or-nothing: an unknown id, one that isn't yours, or a TRANSFER/ADJUSTMENT rejects the " +
                "whole call and names the offenders. A partial success would leave you guessing which " +
                "of 23 rows landed.",
    )
    @ApiResponse(responseCode = "200", description = "The attached transactions")
    @ApiResponse(
        responseCode = "400",
        description = "One or more ids are not an INCOME or EXPENSE",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun attach(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AttachTransactionsRequest,
    ): List<TransactionResponse> =
        topicService
            .attach(id, currentUser.require(), request.transactionIds)
            .map { it.toResponse() }

    @DeleteMapping("/{id}/transactions/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Detach one transaction",
        description = "Equivalent to PATCH /api/v1/transactions/{id} with `topicId: null`. The transaction stays.",
    )
    @ApiResponse(responseCode = "204", description = "Detached")
    fun detach(
        @PathVariable id: UUID,
        @PathVariable transactionId: UUID,
    ) = topicService.detach(id, currentUser.require(), transactionId)
}
