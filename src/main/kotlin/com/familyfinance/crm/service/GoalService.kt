package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Goal
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
    fun list(owner: User): List<GoalWithProgress>

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
