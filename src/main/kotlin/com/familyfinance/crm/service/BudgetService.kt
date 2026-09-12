package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateBudgetRequest
import com.familyfinance.crm.dto.UpdateBudgetRequest
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

/**
 * A budget plus the usage computed for it. Usage is never persisted, so it
 * travels alongside the entity rather than on it.
 */
data class BudgetWithUsage(
    val budget: Budget,
    val month: YearMonth,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    val percentUsed: BigDecimal,
)

interface BudgetService {
    fun list(owner: User): List<BudgetWithUsage>

    fun create(
        owner: User,
        request: CreateBudgetRequest,
    ): BudgetWithUsage

    fun update(
        id: UUID,
        owner: User,
        request: UpdateBudgetRequest,
    ): BudgetWithUsage

    fun softDelete(
        id: UUID,
        owner: User,
    )
}
