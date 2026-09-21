package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Share
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateShareRequest
import java.util.UUID

/**
 * Granting and revoking. The read side lives in [ShareAccessService], which the
 * five resource services depend on; this one depends on *them*, to check that a
 * resource is the caller's to give.
 */
interface ShareService {
    /** Who one resource is shared with — **owner only**, and 404 for anyone else's. */
    fun listForResource(
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ): List<Share>

    fun grant(
        owner: User,
        request: CreateShareRequest,
    ): Share

    /** Soft-deletes the grant. The partial unique index lets it be re-shared later. */
    fun revoke(
        id: UUID,
        owner: User,
    )

    /**
     * Everything shared with me, across all five types, each paired with the
     * shared thing's name so a list row needs no second call.
     */
    fun incoming(grantee: User): List<Pair<Share, String>>

    /** Everything I have shared, so revoking does not mean visiting five pages. */
    fun outgoing(owner: User): List<Pair<Share, String>>
}
