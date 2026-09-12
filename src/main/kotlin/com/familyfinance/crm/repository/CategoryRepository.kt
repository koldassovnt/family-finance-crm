package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * `Category` deliberately carries no `@SQLRestriction` (see `00-`), so every
 * query here filters `isDeleted` explicitly.
 */
interface CategoryRepository : JpaRepository<Category, UUID> {
    @Query(
        """
        SELECT c FROM Category c
        LEFT JOIN FETCH c.parent
        WHERE c.owner = :owner AND c.isDeleted = false
        ORDER BY c.name ASC
        """,
    )
    fun findAllActiveByOwner(owner: User): List<Category>

    @Query(
        """
        SELECT c FROM Category c
        LEFT JOIN FETCH c.parent
        WHERE c.id = :id AND c.isDeleted = false
        """,
    )
    fun findActiveById(id: UUID): Category?

    @Query(
        """
        SELECT count(c) > 0 FROM Category c
        WHERE c.parent.id = :parentId AND c.isDeleted = false
        """,
    )
    fun hasActiveChildren(parentId: UUID): Boolean
}
