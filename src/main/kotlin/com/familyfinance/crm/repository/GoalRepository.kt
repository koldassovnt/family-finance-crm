package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Goal
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface GoalRepository : JpaRepository<Goal, UUID> {
    @Query(
        """
        SELECT g FROM Goal g
        JOIN FETCH g.linkedAccount a
        LEFT JOIN FETCH a.bank
        WHERE g.owner = :owner
        ORDER BY g.name ASC
        """,
    )
    fun findAllByOwner(owner: User): List<Goal>

    @Query(
        """
        SELECT g FROM Goal g
        JOIN FETCH g.linkedAccount a
        LEFT JOIN FETCH a.bank
        WHERE g.id = :id
        """,
    )
    fun findDetailedById(id: UUID): Goal?

    /**
     * Whether a live goal still points at this account — the check that blocks
     * soft-deleting it.
     */
    @Query("SELECT count(g) > 0 FROM Goal g WHERE g.linkedAccount.id = :accountId")
    fun existsForAccountId(accountId: UUID): Boolean
}
