package com.familyfinance.crm.service

import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.TopicStatus
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateTopicRequest
import com.familyfinance.crm.dto.UpdateTopicRequest
import com.familyfinance.crm.repository.CategoryTotal
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** A topic plus the figures summed from whatever is attached to it. */
data class TopicWithTotals(
    val topic: Topic,
    val spent: BigDecimal,
    val received: BigDecimal,
    val transactionCount: Long,
    val firstTransactionOn: LocalDate?,
    val lastTransactionOn: LocalDate?,
) {
    /** What the undertaking cost once refunds are taken off. */
    val net: BigDecimal get() = spent - received

    /** Negative once past what was planned; null when nothing was planned. */
    val remaining: BigDecimal? get() = topic.plannedAmount?.minus(net)
}

/** The detail view: totals plus the per-category breakdowns a chart needs. */
data class TopicDetail(
    val totals: TopicWithTotals,
    val expenseByCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
)

interface TopicService {
    /** [scope] defaults to `OWN`, so this returns what it always did unless asked otherwise. */
    fun list(
        reader: User,
        status: TopicStatus? = null,
        scope: ShareScope = ShareScope.OWN,
    ): List<Readable<TopicWithTotals>>

    fun get(
        id: UUID,
        reader: User,
    ): Readable<TopicDetail>

    /**
     * Resolves a topic the caller owns, rejecting a deleted or foreign one with
     * the same 404 as a missing id. Used by the transaction service when a
     * transaction names a topic.
     *
     * **Every write path uses this one** — attach and detach included, since
     * both move money into or out of a view. Its read-side twin
     * [getReadableBy] also admits viewers.
     */
    fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Topic

    /**
     * Resolves a topic the caller may **read** — their own, or one shared with
     * them. The widest of the five shares: a topic is a lens over transactions,
     * so a viewer reaching it can also read [transactions] on accounts that were
     * never shared with them. Read paths only.
     */
    fun getReadableBy(
        id: UUID,
        reader: User,
    ): Readable<Topic>

    fun create(
        owner: User,
        request: CreateTopicRequest,
    ): TopicWithTotals

    fun update(
        id: UUID,
        owner: User,
        request: UpdateTopicRequest,
    ): TopicWithTotals

    /** Soft delete. Attached transactions keep their link — a view, not money. */
    fun softDelete(
        id: UUID,
        owner: User,
    )

    /**
     * Everything attached, newest first; no date range, membership is the bound.
     * Readable by a viewer of the topic — sharing the lens shares what it frames.
     */
    fun transactions(
        id: UUID,
        reader: User,
    ): List<Transaction>

    /**
     * Unattached income/expense inside the topic's declared window. **Owner
     * only**, even though it reads: it suggests the caller's own transactions to
     * attach, which is a writing tool rather than a view.
     */
    fun candidates(
        id: UUID,
        owner: User,
    ): List<Transaction>

    /** All-or-nothing: one bad id rejects the call rather than half-attaching. */
    fun attach(
        id: UUID,
        owner: User,
        transactionIds: List<UUID>,
    ): List<Transaction>

    fun detach(
        id: UUID,
        owner: User,
        transactionId: UUID,
    )
}
