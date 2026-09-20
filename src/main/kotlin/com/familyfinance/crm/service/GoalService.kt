package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Goal
import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateGoalRequest
import com.familyfinance.crm.dto.UpdateGoalRequest
import java.math.BigDecimal
import java.util.UUID

/**
 * A goal plus its progress, computed from the linked account's balance on
 * every read rather than stored.
 */
data class GoalWithProgress(
    val goal: Goal,
    val progressPercent: BigDecimal,
    val achieved: Boolean,
)

interface GoalService {
    /**
     * [scope] defaults to `OWN`. A goal has no detail endpoint of its own, so
     * this list is also the read path a viewer uses — and reading one discloses
     * its linked account's balance, deliberately: see `phase-8-sharing.md`.
     */
    fun list(
        reader: User,
        scope: ShareScope = ShareScope.OWN,
    ): List<Readable<GoalWithProgress>>

    /**
     * Resolves a goal the caller owns; someone else's 404s like a missing one.
     * Every write path uses this, and sharing gives no read-only twin — a goal
     * is only ever read through [list].
     */
    fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Goal

    fun create(
        owner: User,
        request: CreateGoalRequest,
    ): GoalWithProgress

    fun update(
        id: UUID,
        owner: User,
        request: UpdateGoalRequest,
    ): GoalWithProgress

    fun softDelete(
        id: UUID,
        owner: User,
    )
}
