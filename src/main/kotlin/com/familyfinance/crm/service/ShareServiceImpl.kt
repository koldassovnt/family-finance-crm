package com.familyfinance.crm.service

import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.Share
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateShareRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.AccountRepository
import com.familyfinance.crm.repository.BillRepository
import com.familyfinance.crm.repository.BudgetRepository
import com.familyfinance.crm.repository.GoalRepository
import com.familyfinance.crm.repository.ShareRepository
import com.familyfinance.crm.repository.TopicRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The one place that knows all five shareable types by name: [requireOwned] and
 * [namesOf] are the two `when`s a sixth type must fail to compile in.
 * Ownership goes through each resource's own service, so the `getOwnedBy` check
 * stays in the one place it is written and tested. Names come from the
 * repositories directly, because a viewer's list needs the name of something
 * they do not own — an ownership check there would be wrong, not just unhelpful.
 */
@Service
class ShareServiceImpl(
    private val shareRepository: ShareRepository,
    private val userService: UserService,
    private val accountService: AccountService,
    private val goalService: GoalService,
    private val budgetService: BudgetService,
    private val billService: BillService,
    private val topicService: TopicService,
    private val accountRepository: AccountRepository,
    private val goalRepository: GoalRepository,
    private val budgetRepository: BudgetRepository,
    private val billRepository: BillRepository,
    private val topicRepository: TopicRepository,
) : ShareService {
    @Transactional(readOnly = true)
    override fun listForResource(
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ): List<Share> {
        // Who something is shared with is the owner's business alone, so this
        // 404s for a viewer of that very resource.
        requireOwned(owner, resourceType, resourceId)
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
        requireOwned(owner, resourceType, resourceId)
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
    override fun incoming(grantee: User): List<Pair<Share, String>> = summarize(shareRepository.findAllIncoming(grantee))

    @Transactional(readOnly = true)
    override fun outgoing(owner: User): List<Pair<Share, String>> = summarize(shareRepository.findAllOutgoing(owner))

    /**
     * Names the rows with one query per type present, rather than one per row. A
     * share whose resource has been soft-deleted has no name and drops out: the
     * grant stays on the books but the thing has stopped appearing, for its
     * owner and its viewer alike.
     */
    private fun summarize(shares: List<Share>): List<Pair<Share, String>> {
        val namesByType =
            shares
                .groupBy { it.resourceType }
                .mapValues { (type, rows) -> namesOf(type, rows.map { it.resourceId }) }
        return shares.mapNotNull { share ->
            namesByType[share.resourceType]
                ?.get(share.resourceId)
                ?.let { share to it }
        }
    }

    /**
     * Resolves a resource the caller owns, 404ing exactly as that resource's own
     * service would. **Only an owner may share**, so this is deliberately the
     * owning check and never the readable one — a viewer must not be able to
     * re-share what was shared with them.
     */
    private fun requireOwned(
        owner: User,
        resourceType: ShareResourceType,
        resourceId: UUID,
    ) {
        when (resourceType) {
            ShareResourceType.ACCOUNT -> accountService.getOwnedBy(resourceId, owner)
            ShareResourceType.GOAL -> goalService.getOwnedBy(resourceId, owner)
            ShareResourceType.BUDGET -> budgetService.getOwnedBy(resourceId, owner)
            ShareResourceType.BILL -> billService.getOwnedBy(resourceId, owner)
            ShareResourceType.TOPIC -> topicService.getOwnedBy(resourceId, owner)
        }
    }

    /**
     * Display names for resources of one type, whether or not the caller owns
     * them — a share is the authority here, and it was already checked.
     * Soft-deleted resources are simply absent from the result. A budget has no
     * name of its own: it *is* its category.
     */
    private fun namesOf(
        resourceType: ShareResourceType,
        ids: Collection<UUID>,
    ): Map<UUID, String> =
        when (resourceType) {
            ShareResourceType.ACCOUNT -> accountRepository.findAllActiveByIds(ids).namedBy { it.name }
            ShareResourceType.GOAL -> goalRepository.findAllDetailedByIds(ids).namedBy { it.name }
            ShareResourceType.BUDGET -> budgetRepository.findAllActiveByIds(ids).namedBy { it.category.name }
            ShareResourceType.BILL -> billRepository.findAllDetailedByIds(ids).namedBy { it.name }
            ShareResourceType.TOPIC -> topicRepository.findAllActiveByIds(ids).namedBy { it.name }
        }
}

private fun <T : BaseEntity> List<T>.namedBy(name: (T) -> String): Map<UUID, String> =
    mapNotNull { entity -> entity.id?.let { it to name(entity) } }.toMap()
