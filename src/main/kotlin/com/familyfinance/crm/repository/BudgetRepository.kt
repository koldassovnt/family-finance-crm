package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * `Budget` deliberately carries no `@SQLRestriction` (see [Budget]), so every
 * query here filters `isDeleted` explicitly.
 */
interface BudgetRepository : JpaRepository<Budget, UUID> {
    /** The owner is fetched because a viewer's response names it. */
    @Query(
        """
        SELECT b FROM Budget b
        JOIN FETCH b.category c
        LEFT JOIN FETCH c.parent
        JOIN FETCH b.owner
        WHERE b.id = :id AND b.isDeleted = false
        """,
    )
    fun findActiveById(id: UUID): Budget?

    /** Names a set of shared budgets for a "shared with me" row: a budget is its category. */
    @Query(
        """
        SELECT b FROM Budget b
        JOIN FETCH b.category
        WHERE b.id IN :ids AND b.isDeleted = false
        """,
    )
    fun findAllActiveByIds(ids: Collection<UUID>): List<Budget>

    /** Enforces one live budget per category per person before the index does. */
    @Query(
        """
        SELECT count(b) > 0 FROM Budget b
        WHERE b.owner = :owner AND b.category.id = :categoryId AND b.isDeleted = false
        """,
    )
    fun existsActiveForCategory(
        owner: User,
        categoryId: UUID,
    ): Boolean
}
