package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BudgetRepository : JpaRepository<Budget, UUID> {
    @Query(
        """
        SELECT b FROM Budget b
        JOIN FETCH b.category c
        LEFT JOIN FETCH c.parent
        WHERE b.owner = :owner
        ORDER BY c.name ASC
        """,
    )
    fun findAllByOwner(owner: User): List<Budget>

    @Query(
        """
        SELECT b FROM Budget b
        JOIN FETCH b.category c
        LEFT JOIN FETCH c.parent
        WHERE b.id = :id
        """,
    )
    fun findDetailedById(id: UUID): Budget?

    /** Enforces one active budget per category per person before the index does. */
    @Query("SELECT count(b) > 0 FROM Budget b WHERE b.owner = :owner AND b.category.id = :categoryId")
    fun existsForCategory(
        owner: User,
        categoryId: UUID,
    ): Boolean

    /**
     * Whether a live budget still configures this category — the check that
     * blocks soft-deleting it. An FK can't see `is_deleted`, hence a query.
     */
    @Query("SELECT count(b) > 0 FROM Budget b WHERE b.category.id = :categoryId")
    fun existsForCategoryId(categoryId: UUID): Boolean
}
