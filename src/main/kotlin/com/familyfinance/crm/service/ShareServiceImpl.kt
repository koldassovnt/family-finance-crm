package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Share
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateShareRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.ShareRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ShareServiceImpl(
    private val shareRepository: ShareRepository,
    private val userService: UserService,
    private val shareableResources: ShareableResourceService,
) : ShareService {
    @Transactional(readOnly = true)
    override fun listForResource(
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ): List<Share> {
        // Who something is shared with is the owner's business alone, so this
        // 404s for a viewer of that very resource.
        shareableResources.requireOwned(owner, resourceType, resourceId)
        return shareRepository.findAllForResource(
            owner = owner,
            resourceType = resourceType,
            resourceId = resourceId,
        )
    }

    @Transactional
    override fun grant(
        owner: User,
        request: CreateShareRequest,
    ): Share {
        val resourceType = request.resourceType ?: throw invalidField("resourceType", "is required")
        val resourceId = request.resourceId ?: throw invalidField("resourceId", "is required")
        val granteeId = request.granteeUserId ?: throw invalidField("granteeUserId", "is required")
        // Ownership first, and as a 404: nothing about a resource id is revealed
        // before the caller has proved the thing is theirs to give.
        shareableResources.requireOwned(owner, resourceType, resourceId)
        // An unknown or deleted member is a 404 — `User` is restricted.
        val grantee = userService.getById(granteeId)
        if (grantee == owner) {
            throw invalidField("granteeUserId", "cannot be yourself")
        }
        if (shareRepository.findGrant(grantee, resourceType, resourceId) != null) {
            throw ConflictException("This is already shared with ${grantee.displayName}")
        }
        return shareRepository.save(
            Share(
                resourceType = resourceType,
                resourceId = resourceId,
                owner = owner,
                grantee = grantee,
                access = ShareAccess.VIEWER,
            ),
        )
    }

    @Transactional
    override fun revoke(
        id: UUID,
        owner: User,
    ) {
        val share =
            shareRepository.findById(id).orElseThrow { NotFoundException("Share $id was not found") }
        // Someone else's grant reads as missing, and an already-revoked one is
        // invisible to the lookup at all — `Share` is restricted.
        if (share.owner != owner) throw NotFoundException("Share $id was not found")
        share.isDeleted = true
    }

    @Transactional(readOnly = true)
    override fun incoming(grantee: User): List<ShareSummary> = summarize(shareRepository.findAllIncoming(grantee))

    @Transactional(readOnly = true)
    override fun outgoing(owner: User): List<ShareSummary> = summarize(shareRepository.findAllOutgoing(owner))

    /**
     * Names the rows with one query per type present, rather than one per row. A
     * share whose resource has been soft-deleted has no name and drops out: the
     * grant stays on the books but the thing has stopped appearing, for its
     * owner and its viewer alike.
     */
    private fun summarize(shares: List<Share>): List<ShareSummary> {
        val namesByType =
            shares
                .groupBy { it.resourceType }
                .mapValues { (type, rows) -> shareableResources.namesOf(type, rows.map { it.resourceId }) }
        return shares.mapNotNull { share ->
            namesByType[share.resourceType]
                ?.get(share.resourceId)
                ?.let { ShareSummary(share = share, resourceName = it) }
        }
    }
}
