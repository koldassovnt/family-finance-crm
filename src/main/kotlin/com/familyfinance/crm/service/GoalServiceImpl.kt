package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Goal
import com.familyfinance.crm.domain.GoalStatus
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateGoalRequest
import com.familyfinance.crm.dto.UpdateGoalRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.GoalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class GoalServiceImpl(
    private val goalRepository: GoalRepository,
    private val accountService: AccountService,
) : GoalService {
    @Transactional(readOnly = true)
    override fun list(owner: User): List<GoalWithProgress> = goalRepository.findAllByOwner(owner).map(::withProgress)

    @Transactional
    override fun create(
        owner: User,
        request: CreateGoalRequest,
    ): GoalWithProgress {
        val type = request.type ?: throw invalidField("type", "is required")
        val linkedAccountId =
            request.linkedAccountId ?: throw invalidField("linkedAccountId", "is required")
        val targetAmount = requirePositiveTarget(request.targetAmount)
        val linkedAccount = accountService.getOwnedBy(linkedAccountId, owner)
        val saved =
            goalRepository.save(
                Goal(
                    owner = owner,
                    name = request.name.trim(),
                    type = type,
                    targetAmount = targetAmount,
                    targetDate = request.targetDate,
                    linkedAccount = linkedAccount,
                    status = GoalStatus.ACTIVE,
                ),
            )
        return withProgress(saved)
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateGoalRequest,
    ): GoalWithProgress {
        val goal = getOwnedBy(id, owner)
        request.name?.let { goal.name = it.trim() }
        request.targetAmount?.let { goal.targetAmount = requirePositiveTarget(it) }
        request.targetDate?.let { goal.targetDate = it.orElse(null) }
        request.status?.let { goal.status = it }
        return withProgress(goal)
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        owner: User,
    ) {
        getOwnedBy(id, owner).isDeleted = true
    }

    private fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Goal {
        val goal =
            goalRepository.findDetailedById(id)
                ?: throw NotFoundException("Goal $id was not found")
        if (goal.owner != owner) throw NotFoundException("Goal $id was not found")
        return goal
    }
}

private const val PERCENT_SCALE = 2
private val HUNDRED = BigDecimal(100)

private fun requirePositiveTarget(targetAmount: BigDecimal?): BigDecimal {
    val value = targetAmount ?: throw invalidField("targetAmount", "is required")
    if (value.signum() <= 0) throw invalidField("targetAmount", "must be greater than zero")
    return value
}

/**
 * Progress is the linked account's balance against the target, clamped to
 * 0–100 — a negative balance reads as 0%, and there is no "150% achieved".
 * `achieved` is derived here and never persisted, so an achieved goal can
 * still be abandoned by hand.
 */
private fun withProgress(goal: Goal): GoalWithProgress {
    val raw =
        goal.linkedAccount.balance
            .multiply(HUNDRED)
            .divide(goal.targetAmount, PERCENT_SCALE, RoundingMode.HALF_UP)
    val clamped = raw.coerceIn(BigDecimal.ZERO.setScale(PERCENT_SCALE), HUNDRED.setScale(PERCENT_SCALE))
    return GoalWithProgress(
        goal = goal,
        progressPercent = clamped,
        achieved = raw >= HUNDRED,
    )
}
