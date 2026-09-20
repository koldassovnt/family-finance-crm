package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * `Account` deliberately carries no `@SQLRestriction` (see [Account]), so
 * every query here filters `isDeleted` explicitly.
 */
interface AccountRepository : JpaRepository<Account, UUID> {
    @Query(
        """
        SELECT a FROM Account a
        LEFT JOIN FETCH a.bank
        WHERE a.owner = :owner AND a.isDeleted = false
        ORDER BY a.name ASC
        """,
    )
    fun findAllActiveByOwner(owner: User): List<Account>

    /**
     * Fetches the owner because a viewer's response names it. The join is an
     * inner one onto a restricted `User`, so an account whose owner was
     * soft-deleted reads as absent rather than failing mid-serialization —
     * a backstop for the deletion rules in `phase-8-sharing.md`, not the fix.
     */
    @Query(
        """
        SELECT a FROM Account a
        LEFT JOIN FETCH a.bank
        JOIN FETCH a.owner
        WHERE a.id = :id AND a.isDeleted = false
        """,
    )
    fun findActiveById(id: UUID): Account?

    /** The accounts shared with a viewer, in the same order as their own list. */
    @Query(
        """
        SELECT a FROM Account a
        LEFT JOIN FETCH a.bank
        JOIN FETCH a.owner
        WHERE a.id IN :ids AND a.isDeleted = false
        ORDER BY a.name ASC
        """,
    )
    fun findAllActiveByIds(ids: Collection<UUID>): List<Account>
}
