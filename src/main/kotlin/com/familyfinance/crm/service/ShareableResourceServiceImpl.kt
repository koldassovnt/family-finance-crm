package com.familyfinance.crm.service

import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.repository.AccountRepository
import com.familyfinance.crm.repository.BillRepository
import com.familyfinance.crm.repository.BudgetRepository
import com.familyfinance.crm.repository.GoalRepository
import com.familyfinance.crm.repository.TopicRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Ownership goes through each resource's own service, so the `getOwnedBy` check
 * stays in the one place it is written and tested. Names come from the
 * repositories directly, because a viewer's list needs the name of something
 * they do not own — an ownership check there would be wrong, not just unhelpful.
 */
@Service
class ShareableResourceServiceImpl(
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
) : ShareableResourceService {
    @Transactional(readOnly = true)
    override fun requireOwned(
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

    @Transactional(readOnly = true)
    override fun namesOf(
        resourceType: ShareResourceType,
        ids: Collection<UUID>,
    ): Map<UUID, String> {
        // `IN ()` is not valid JPQL, so an empty set never reaches a query. A
        // budget has no name of its own below: it *is* its category.
        if (ids.isEmpty()) return emptyMap()
        return when (resourceType) {
            ShareResourceType.ACCOUNT -> accountRepository.findAllActiveByIds(ids).namedBy { it.name }
            ShareResourceType.GOAL -> goalRepository.findAllDetailedByIds(ids).namedBy { it.name }
            ShareResourceType.BUDGET -> budgetRepository.findAllActiveByIds(ids).namedBy { it.category.name }
            ShareResourceType.BILL -> billRepository.findAllDetailedByIds(ids).namedBy { it.name }
            ShareResourceType.TOPIC -> topicRepository.findAllActiveByIds(ids).namedBy { it.name }
        }
    }
}

private fun <T : BaseEntity> List<T>.namedBy(name: (T) -> String): Map<UUID, String> =
    mapNotNull { entity -> entity.id?.let { it to name(entity) } }.toMap()
