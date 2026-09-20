package com.familyfinance.crm.service

import com.familyfinance.crm.domain.AccessLevel
import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.domain.asAccessLevel
import java.util.UUID

/**
 * A resource the caller is allowed to **read**, and how they reached it. The
 * two travel together on purpose: a response can never badge something as
 * shared without being able to say whose it is.
 */
sealed interface Readable<T> {
    val resource: T

    /** The caller's own — the only case any write path may ever accept. */
    data class Own<T>(
        override val resource: T,
    ) : Readable<T>

    data class Shared<T>(
        override val resource: T,
        val owner: User,
        val access: ShareAccess,
    ) : Readable<T>

    val accessLevel: AccessLevel
        get() =
            when (this) {
                is Own -> AccessLevel.OWNER
                is Shared -> access.asAccessLevel()
            }

    /** Whose it is, or null when it is the caller's — they already know who they are. */
    val sharedBy: User?
        get() =
            when (this) {
                is Own -> null
                is Shared -> owner
            }

    /** Keeps the access verdict while the resource is turned into something richer. */
    fun <R> map(transform: (T) -> R): Readable<R> =
        when (this) {
            is Own -> Own(transform(resource))
            is Shared -> Shared(resource = transform(resource), owner = owner, access = access)
        }
}

/**
 * The read side of sharing, and deliberately the *only* part of it the five
 * resource services depend on: they ask whether something is readable, never
 * how sharing works. Kept apart from [ShareService] — which resolves resources
 * through those same services in order to check ownership — so the dependency
 * runs one way and cannot cycle.
 */
interface ShareAccessService {
    /**
     * Wraps [resource] as the caller's own, or as shared with them, or returns
     * **null** when neither. Null is not "forbidden": every caller turns it into
     * the same 404 a missing id gives, so a foreign id cannot be probed.
     */
    fun <T> readableBy(
        reader: User,
        resource: T,
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ): Readable<T>?

    /**
     * Everything of one type shared with [reader], loaded by [load] and paired
     * with the grant that made it readable. A resource whose grant has gone, or
     * which [load] no longer returns because it was soft-deleted, is simply
     * absent — a deleted resource stops appearing for a viewer exactly as it
     * does for its owner.
     */
    fun <T : BaseEntity> sharedWith(
        reader: User,
        resourceType: ShareResourceType,
        load: (Collection<UUID>) -> List<T>,
        ownerOf: (T) -> User,
    ): List<Readable<T>>

    /**
     * The raw grants behind [sharedWith], for the one list that cannot load its
     * resources by id — a budget's usage is read through the version in force.
     */
    fun readableGrants(
        reader: User,
        resourceType: ShareResourceType,
    ): Map<UUID, ShareAccess>
}
