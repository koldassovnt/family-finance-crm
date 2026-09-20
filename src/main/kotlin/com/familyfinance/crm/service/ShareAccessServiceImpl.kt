package com.familyfinance.crm.service

import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.repository.ShareRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ShareAccessServiceImpl(
    private val shareRepository: ShareRepository,
) : ShareAccessService {
    @Transactional(readOnly = true)
    override fun <T> readableBy(
        reader: User,
        resource: T,
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ): Readable<T>? {
        if (owner == reader) return Readable.Own(resource)
        // A live grant is the only other way in. Revoking soft-deletes the row and
        // `Share` is restricted, so a revoked share reads as no grant at all.
        val grant =
            shareRepository.findGrant(
                grantee = reader,
                resourceType = resourceType,
                resourceId = resourceId,
            ) ?: return null
        return Readable.Shared(resource = resource, owner = owner, access = grant.access)
    }

    @Transactional(readOnly = true)
    override fun <T : BaseEntity> sharedWith(
        reader: User,
        resourceType: ShareResourceType,
        load: (Collection<UUID>) -> List<T>,
        ownerOf: (T) -> User,
    ): List<Readable<T>> {
        val grants = readableGrants(reader, resourceType)
        // `IN ()` is not valid JPQL, so an empty grant set never reaches a query.
        if (grants.isEmpty()) return emptyList()
        return load(grants.keys).mapNotNull { resource ->
            val id = resource.id ?: return@mapNotNull null
            val access = grants[id] ?: return@mapNotNull null
            Readable.Shared(resource = resource, owner = ownerOf(resource), access = access)
        }
    }

    @Transactional(readOnly = true)
    override fun readableGrants(
        reader: User,
        resourceType: ShareResourceType,
    ): Map<UUID, ShareAccess> =
        shareRepository
            .findAllGrantsTo(reader, resourceType)
            .associate { it.resourceId to it.access }
}
