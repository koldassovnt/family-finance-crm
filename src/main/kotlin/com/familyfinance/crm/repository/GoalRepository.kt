package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Goal
import com.familyfinance.crm.domain.GoalStatus
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

    /** The owner is fetched because a viewer's response names it — see [findAllDetailedByIds]. */
    @Query(
        """
        SELECT g FROM Goal g
        JOIN FETCH g.linkedAccount a
        LEFT JOIN FETCH a.bank
        JOIN FETCH g.owner
        WHERE g.id = :id
        """,
    )
    fun findDetailedById(id: UUID): Goal?

    /**
     * The goals shared with a viewer. Sharing a goal discloses its linked
     * account's balance, which is why the account is fetched here as it is for
     * the owner's own list — the disclosure is the feature, decided in
     * `phase-8-sharing.md`, and the share dialog names the account.
     */
    @Query(
        """
        SELECT g FROM Goal g
        JOIN FETCH g.linkedAccount a
        LEFT JOIN FETCH a.bank
        JOIN FETCH g.owner
        WHERE g.id IN :ids
        ORDER BY g.name ASC
        """,
    )
    fun findAllDetailedByIds(ids: Collection<UUID>): List<Goal>

    /**
     * Whether an **active** goal still points at this account — the check that
     * blocks soft-deleting it. Abandoned and archived goals are kept for the
     * record and deliberately do not block.
     */
    @Query(
        """
        SELECT count(g) > 0 FROM Goal g
        WHERE g.linkedAccount.id = :accountId AND g.status = :status
        """,
    )
    fun existsActiveForAccountId(
        accountId: UUID,
        status: GoalStatus = GoalStatus.ACTIVE,
    ): Boolean
}
