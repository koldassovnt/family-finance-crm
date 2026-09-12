package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateBudgetRequest
import com.familyfinance.crm.dto.UpdateBudgetRequest
import com.familyfinance.crm.exception.DuplicateBudgetException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.BudgetRepository
import com.familyfinance.crm.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.YearMonth
import java.util.UUID

@Service
class BudgetServiceImpl(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryService: CategoryService,
    private val clock: Clock,
) : BudgetService {
    @Transactional(readOnly = true)
    override fun list(owner: User): List<BudgetWithUsage> {
        val budgets = budgetRepository.findAllByOwner(owner)
        if (budgets.isEmpty()) return emptyList()
        val month = currentMonth()
        val range = monthRange(month)
        // One aggregate for the whole month rather than a query per budget.
        val spentByCategory =
            transactionRepository
                .sumByCategory(owner, TransactionType.EXPENSE, range.from, range.to)
                .associate { it.categoryId to it.total }
        return budgets.map { budget ->
            withUsage(budget, month, spentByCategory[budget.category.id] ?: BigDecimal.ZERO)
        }
    }

    @Transactional
    override fun create(
        owner: User,
        request: CreateBudgetRequest,
    ): BudgetWithUsage {
        val categoryId = request.categoryId ?: throw invalidField("categoryId", "is required")
        val limitAmount = request.limitAmount ?: throw invalidField("limitAmount", "is required")
        if (limitAmount.signum() <= 0) throw invalidField("limitAmount", "must be greater than zero")
        val category = categoryService.getOwnedBy(categoryId, owner)
        if (budgetRepository.existsForCategory(owner, categoryId)) {
            throw DuplicateBudgetException("A budget for category ${category.name} already exists")
        }
        val saved =
            budgetRepository.save(
                Budget(
                    owner = owner,
                    category = category,
                    limitAmount = limitAmount,
                    period = request.period,
                    alertThresholdPercent = request.alertThresholdPercent,
                ),
            )
        return usageFor(saved, owner)
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateBudgetRequest,
    ): BudgetWithUsage {
        val budget = getOwnedBy(id, owner)
        request.limitAmount?.let { limitAmount ->
            if (limitAmount.signum() <= 0) throw invalidField("limitAmount", "must be greater than zero")
            budget.limitAmount = limitAmount
        }
        request.alertThresholdPercent?.let { threshold ->
            budget.alertThresholdPercent = threshold.orElse(null)?.also(::validateThreshold)
        }
        return usageFor(budget, owner)
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
    ): Budget {
        val budget =
            budgetRepository.findDetailedById(id)
                ?: throw NotFoundException("Budget $id was not found")
        if (budget.owner != owner) throw NotFoundException("Budget $id was not found")
        return budget
    }

    private fun usageFor(
        budget: Budget,
        owner: User,
    ): BudgetWithUsage {
        val month = currentMonth()
        val range = monthRange(month)
        val categoryId = checkNotNull(budget.category.id) { "A persisted budget has a persisted category" }
        val spent =
            transactionRepository.sumByTypeAndCategory(
                owner = owner,
                type = TransactionType.EXPENSE,
                categoryId = categoryId,
                from = range.from,
                to = range.to,
            )
        return withUsage(budget, month, spent)
    }

    /** "Current month" is the calendar month in the app timezone. */
    private fun currentMonth(): YearMonth = YearMonth.now(clock)

    private fun validateThreshold(threshold: Int) {
        if (threshold < MIN_THRESHOLD_PERCENT || threshold > MAX_THRESHOLD_PERCENT) {
            throw invalidField(
                "alertThresholdPercent",
                "must be between $MIN_THRESHOLD_PERCENT and $MAX_THRESHOLD_PERCENT",
            )
        }
    }
}

private const val MIN_THRESHOLD_PERCENT = 1
private const val MAX_THRESHOLD_PERCENT = 100
private const val PERCENT_SCALE = 2

private fun withUsage(
    budget: Budget,
    month: YearMonth,
    spent: BigDecimal,
): BudgetWithUsage =
    BudgetWithUsage(
        budget = budget,
        month = month,
        spent = spent,
        // Goes negative once the limit is passed; overspend is information.
        remaining = budget.limitAmount - spent,
        percentUsed =
            spent
                .multiply(BigDecimal(100))
                .divide(budget.limitAmount, PERCENT_SCALE, RoundingMode.HALF_UP),
    )
