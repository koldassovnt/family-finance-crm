package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.TopicStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

data class CreateTopicRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
    @field:Size(max = 1000, message = "must be at most 1000 characters")
    val description: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    @field:DecimalMin(value = "0", inclusive = false, message = "must be greater than zero")
    val plannedAmount: BigDecimal? = null,
)

/**
 * Every nullable field is an [Optional] so an explicit `null` clears it — a
 * plain nullable could not distinguish "leave the end date alone" from "this
 * trip has no end date after all".
 */
data class UpdateTopicRequest(
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String? = null,
    /** Length and sign are checked in the service — a wrapped value can't be annotated. */
    val description: Optional<String>? = null,
    val startDate: Optional<LocalDate>? = null,
    val endDate: Optional<LocalDate>? = null,
    val plannedAmount: Optional<BigDecimal>? = null,
    val status: TopicStatus? = null,
)

/** Bulk attach: a trip is tagged after the fact, not one row at a time. */
data class AttachTransactionsRequest(
    @field:NotEmpty(message = "must not be empty")
    @field:Size(max = 500, message = "must be at most 500 ids")
    val transactionIds: List<UUID> = emptyList(),
)

data class TopicResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val plannedAmount: BigDecimal?,
    val status: TopicStatus,
    /** Sum of attached `EXPENSE` transactions in KZT. */
    val spent: BigDecimal,
    /** Sum of attached `INCOME` — refunds and money paid back. */
    val received: BigDecimal,
    /** `spent - received`: what the undertaking actually cost. */
    val net: BigDecimal,
    /** `plannedAmount - net`, or null when nothing was planned. Negative once overspent. */
    val remaining: BigDecimal?,
    val transactionCount: Long,
    /** The real span of attached spending, often wider than the declared dates. */
    val firstTransactionOn: LocalDate?,
    val lastTransactionOn: LocalDate?,
)

/** The detail view: the list shape plus the breakdowns a chart needs. */
data class TopicDetailResponse(
    val topic: TopicResponse,
    val expenseByCategory: List<CategorySummary>,
    val incomeByCategory: List<CategorySummary>,
)

/** The embedded reference on a transaction — name included, so no join is needed. */
data class TopicRef(
    val id: UUID,
    val name: String,
    val status: TopicStatus,
)
