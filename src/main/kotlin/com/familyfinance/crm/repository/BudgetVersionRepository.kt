package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.BudgetVersion
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface BudgetVersionRepository : JpaRepository<BudgetVersion, UUID> {
    /**
     * The version of every one of this owner's budgets that applied in the
     * given month. A budget that did not exist yet simply has no row, which is
     * what makes a past month report only the budgets that were really set.
     */
    @Query(
        """
        SELECT v FROM BudgetVersion v
        JOIN FETCH v.budget b
        JOIN FETCH b.category c
        LEFT JOIN FETCH c.parent
        WHERE b.owner = :owner
          AND v.effectiveFromMonth <= :month
          AND (v.effectiveToMonth IS NULL OR v.effectiveToMonth >= :month)
        ORDER BY c.name ASC
        """,
    )
    fun findInForce(
        owner: User,
        month: LocalDate,
    ): List<BudgetVersion>

    @Query(
        """
        SELECT v FROM BudgetVersion v
        WHERE v.budget.id = :budgetId AND v.effectiveToMonth IS NULL
        """,
    )
    fun findOpenVersion(budgetId: UUID): BudgetVersion?

    /**
     * Whether a live budget is still configuring this category — the check that
     * blocks soft-deleting it. A closed version is history and does not block.
     */
    @Query(
        """
        SELECT count(v) > 0 FROM BudgetVersion v
        WHERE v.budget.category.id = :categoryId
          AND v.effectiveToMonth IS NULL
          AND v.budget.isDeleted = false
        """,
    )
    fun existsOpenForCategory(categoryId: UUID): Boolean
}
