package com.familyfinance.crm.service

import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import java.util.UUID

/**
 * The one place that knows all five shareable types by name. Sharing needs two
 * things of a resource — is it the caller's to give, and what is it called — and
 * both are a `when` over [ShareResourceType]. Keeping them here means a sixth
 * type fails to compile in exactly two places instead of being missed in one.
 */
interface ShareableResourceService {
    /**
     * Resolves a resource the caller owns, 404ing exactly as that resource's own
     * service would. **Only an owner may share**, so this is deliberately the
     * owning check and never the readable one — a viewer must not be able to
     * re-share what was shared with them.
     */
    fun requireOwned(
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    )

    /**
     * Display names for resources of one type, whether or not the caller owns
     * them — a share is the authority here, and it was already checked.
     * Soft-deleted resources are simply absent from the result, which is what
     * drops them from a "shared with me" list: a deleted resource stops
     * appearing for a viewer exactly as it does for its owner.
     */
    fun namesOf(
        resourceType: ShareResourceType,
        ids: Collection<UUID>,
    ): Map<UUID, String>
}
