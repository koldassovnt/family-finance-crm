package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.PlannedPurchase
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** The owner is fetched everywhere because every response says who added the item. */
interface PlannedPurchaseRepository : JpaRepository<PlannedPurchase, UUID> {
    /**
     * The user's own personal items and every family item, whoever added it.
     * Still to buy first, then newest first.
     */
    @Query(
        """
        SELECT p FROM PlannedPurchase p
        JOIN FETCH p.owner
        WHERE p.isFamily = true OR p.owner = :user
        ORDER BY p.isBought ASC, p.createdAt DESC
        """,
    )
    fun findVisibleTo(user: User): List<PlannedPurchase>

    @Query("SELECT p FROM PlannedPurchase p JOIN FETCH p.owner WHERE p.id = :id")
    fun findDetailedById(id: UUID): PlannedPurchase?
}
