package com.familyfinance.crm.service

import com.familyfinance.crm.domain.PlannedPurchase
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreatePlannedPurchaseRequest
import com.familyfinance.crm.dto.UpdatePlannedPurchaseRequest
import java.util.UUID

/**
 * One rule decides every call: a family item is everyone's, a personal item is
 * its owner's alone. Somebody else's personal item is a 404 everywhere.
 */
interface PlannedPurchaseService {
    /** The user's personal items and every family item; still to buy first. */
    fun list(user: User): List<PlannedPurchase>

    fun create(
        user: User,
        request: CreatePlannedPurchaseRequest,
    ): PlannedPurchase

    /** Rename it or mark it bought (or not bought again). */
    fun update(
        id: UUID,
        user: User,
        request: UpdatePlannedPurchaseRequest,
    ): PlannedPurchase

    fun softDelete(
        id: UUID,
        user: User,
    )
}
