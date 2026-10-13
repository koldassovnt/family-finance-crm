package com.familyfinance.crm.service

import com.familyfinance.crm.domain.PlannedPurchase
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreatePlannedPurchaseRequest
import com.familyfinance.crm.dto.UpdatePlannedPurchaseRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.repository.PlannedPurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PlannedPurchaseServiceImpl(
    private val plannedPurchaseRepository: PlannedPurchaseRepository,
) : PlannedPurchaseService {
    @Transactional(readOnly = true)
    override fun list(user: User): List<PlannedPurchase> = plannedPurchaseRepository.findVisibleTo(user)

    @Transactional
    override fun create(
        user: User,
        request: CreatePlannedPurchaseRequest,
    ): PlannedPurchase =
        plannedPurchaseRepository.save(
            PlannedPurchase(owner = user, name = requireNonBlankName(request.name), isFamily = request.isFamily),
        )

    @Transactional
    override fun update(
        id: UUID,
        user: User,
        request: UpdatePlannedPurchaseRequest,
    ): PlannedPurchase {
        val purchase = getAccessible(id, user)
        request.name?.let { purchase.name = requireNonBlankName(it) }
        request.isBought?.let { purchase.isBought = it }
        return purchase
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        user: User,
    ) {
        getAccessible(id, user).isDeleted = true
    }

    /** A family item is anyone's to change; a personal one only its owner's. */
    private fun getAccessible(
        id: UUID,
        user: User,
    ): PlannedPurchase {
        val purchase = plannedPurchaseRepository.findDetailedById(id)
        if (purchase == null || (!purchase.isFamily && purchase.owner != user)) {
            throw NotFoundException("Planned purchase $id was not found")
        }
        return purchase
    }
}
