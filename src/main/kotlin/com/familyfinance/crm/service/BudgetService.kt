package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.BudgetVersion
import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateBudgetRequest
import com.familyfinance.crm.dto.UpdateBudgetRequest
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

/**
 * A budget, the version of it that applied in [month], and the usage computed
 * for that month. None of the three numbers is persisted.
 */
data class BudgetWithUsage(
    val budget: Budget,
    val version: BudgetVersion,
    val month: YearMonth,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentUsed: BigDecimal,
)

interface BudgetService {
    /**
     * The budgets that were in force in [month], each with that month's limit
     * and usage. Budgets that did not exist yet are simply absent.
     *
     * [scope] defaults to `OWN`; a budget has no detail endpoint, so this list
     * is also the read path a viewer uses. A shared budget's usage is its
     * **owner's** spending — a viewer's own figures are never touched by it.
     */
    fun list(
        reader: User,
        month: YearMonth,
        scope: ShareScope = ShareScope.OWN,
    ): List<Readable<BudgetWithUsage>>

    /**
     * Resolves a budget the caller owns; someone else's 404s like a missing one.
     * Every write path uses this, and a budget is only ever read through [list].
     */
    fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Budget

    fun create(
        owner: User,
        request: CreateBudgetRequest,
    ): BudgetWithUsage

    /**
     * Changing a limit opens a new version from the current month, leaving past
     * months measured against the limit that actually applied then.
     */
    fun update(
        id: UUID,
        owner: User,
        request: UpdateBudgetRequest,
    ): BudgetWithUsage

    /** Stops the budget from this month onward; past months keep reporting it. */
    fun softDelete(
        id: UUID,
        owner: User,
    )
}
