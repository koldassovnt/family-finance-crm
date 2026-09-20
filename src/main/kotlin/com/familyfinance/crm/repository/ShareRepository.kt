package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Share
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * [Share] carries `@SQLRestriction`, so a revoked grant is already invisible to
 * every query here — none of them repeats the `isDeleted` filter.
 *
 * The listing queries `JOIN FETCH` both users, which are needed for the
 * response and would otherwise be lazy proxies past the open session. That join
 * is also what happens if a member is ever soft-deleted: `User` is restricted
 * too, so their grants drop out of these lists rather than failing to resolve
 * mid-serialization. It is a safety net, not the fix — see the deletion rules
 * in `phase-8-sharing.md`.
 */
interface ShareRepository : JpaRepository<Share, UUID> {
    /** The access check itself: one grant, or nothing. */
    @Query(
        """
        SELECT s FROM Share s
        WHERE s.grantee = :grantee AND s.resourceType = :resourceType AND s.resourceId = :resourceId
        """,
    )
    fun findGrant(
        grantee: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ): Share?

    /**
     * Every grant of one type held by this reader — what `scope=SHARED` lists.
     * The grants themselves rather than bare ids, so what each one confers
     * travels with it instead of being assumed at five call sites.
     */
    @Query(
        """
        SELECT s FROM Share s
        WHERE s.grantee = :grantee AND s.resourceType = :resourceType
        """,
    )
    fun findAllGrantsTo(
        grantee: User,
        resourceType: ShareResourceType,
    ): List<Share>

    /** Who one resource is shared with — the owner's view of a single thing. */
    @Query(
        """
        SELECT s FROM Share s
        JOIN FETCH s.owner
        JOIN FETCH s.grantee g
        WHERE s.owner = :owner AND s.resourceType = :resourceType AND s.resourceId = :resourceId
        ORDER BY g.displayName ASC
        """,
    )
    fun findAllForResource(
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ): List<Share>

    /** Everything shared with me, across all five types. */
    @Query(
        """
        SELECT s FROM Share s
        JOIN FETCH s.owner o
        JOIN FETCH s.grantee
        WHERE s.grantee = :grantee
        ORDER BY s.resourceType ASC, o.displayName ASC
        """,
    )
    fun findAllIncoming(grantee: User): List<Share>

    /** Everything I have shared, so revoking does not mean visiting five pages. */
    @Query(
        """
        SELECT s FROM Share s
        JOIN FETCH s.owner
        JOIN FETCH s.grantee g
        WHERE s.owner = :owner
        ORDER BY s.resourceType ASC, g.displayName ASC
        """,
    )
    fun findAllOutgoing(owner: User): List<Share>
}
