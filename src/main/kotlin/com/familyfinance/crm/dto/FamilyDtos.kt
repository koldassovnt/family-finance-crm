package com.familyfinance.crm.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * A family purchase as the whole household sees it. Deliberately not a
 * [TransactionResponse]: which account paid, its rate and its topic are the
 * payer's own, so they are absent here rather than left for a mapper to omit.
 */
data class FamilyPurchaseResponse(
    val id: UUID,
    val amount: BigDecimal,
    val currency: String,
    val amountKzt: BigDecimal,
    val occurredOn: LocalDate,
    /** By name only: categories are per member, so an id would mean nothing to the reader. */
    val categoryName: String?,
    val note: String?,
    val paidBy: UserRef,
    /** Only the payer can edit or delete it, through the transaction endpoints. */
    val isMine: Boolean,
)

data class FamilyMemberTotal(
    val member: UserRef,
    val total: BigDecimal,
)

/** Members' categories of the same name are one line; `null` is uncategorised. */
data class FamilyCategoryTotal(
    val categoryName: String?,
    val total: BigDecimal,
)

/** The one response in the system whose totals sum across members. */
data class FamilyPurchasesResponse(
    val from: LocalDate,
    val to: LocalDate,
    /** Always KZT — foreign amounts are converted at each purchase's own rate. */
    val currency: String,
    val total: BigDecimal,
    val byMember: List<FamilyMemberTotal>,
    val byCategory: List<FamilyCategoryTotal>,
    val purchases: List<FamilyPurchaseResponse>,
)
