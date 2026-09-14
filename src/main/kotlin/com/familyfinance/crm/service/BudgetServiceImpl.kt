package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.BudgetVersion
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateBudgetRequest
import com.familyfinance.crm.dto.UpdateBudgetRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.DuplicateBudgetException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.BudgetRepository
import com.familyfinance.crm.repository.BudgetVersionRepository
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
    private val budgetVersionRepository: BudgetVersionRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryService: CategoryService,
    private val clock: Clock,
) : BudgetService {
    @Transactional(readOnly = true)
    override fun list(
        owner: User,
        month: YearMonth,
    ): List<BudgetWithUsage> {
        val versions = budgetVersionRepository.findInForce(owner, month.atDay(1))
        if (versions.isEmpty()) return emptyList()
        val range = monthRange(month)
        // One aggregate for the whole month, then rolled up per budget in memory,
        // rather than a query per budget.
        val spentByCategory =
            transactionRepository
                .sumByCategory(owner, TransactionType.EXPENSE, range.from, range.to)
                .associate { it.categoryId to it.total }
        val descendants = categoryService.descendantIndex(owner)
        return versions.map { version ->
            val categoryIds = descendantsOf(version.budget.category, descendants)
            val spent =
                categoryIds.fold(BigDecimal.ZERO) { total, categoryId ->
                    total + (spentByCategory[categoryId] ?: BigDecimal.ZERO)
                }
            withUsage(version, month, spent)
        }
    }

    @Transactional
    override fun create(
        owner: User,
        request: CreateBudgetRequest,
    ): BudgetWithUsage {
        val categoryId = request.categoryId ?: throw invalidField("categoryId", "is required")
        val limitAmount = requirePositiveLimit(request.limitAmount)
        val category = categoryService.getOwnedBy(categoryId, owner)
        if (category.kind != CategoryKind.EXPENSE) {
            // Usage only ever counts EXPENSE transactions, so an income budget
            // could never read anything but zero.
            throw invalidField("categoryId", "must be an EXPENSE category")
        }
        if (budgetRepository.existsActiveForCategory(owner, categoryId)) {
            throw DuplicateBudgetException("A budget for category ${category.name} already exists")
        }
        val budget =
            budgetRepository.save(
                Budget(owner = owner, category = category, period = request.period),
            )
        val version =
            budgetVersionRepository.save(
                BudgetVersion(
                    budget = budget,
                    limitAmount = limitAmount,
                    alertThresholdPercent = request.alertThresholdPercent?.also(::validateThreshold),
                    effectiveFromMonth = currentMonth().atDay(1),
                    effectiveToMonth = null,
                ),
            )
        return usageFor(version, owner)
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateBudgetRequest,
    ): BudgetWithUsage {
        val budget = getOwnedBy(id, owner)
        val open = openVersionOf(budget)
        val month = currentMonth()
        val limitAmount = request.limitAmount?.let(::requirePositiveLimit) ?: open.limitAmount
        // An explicit null clears the cue, so this cannot collapse into an elvis
        // chain: `Optional.empty()` and an absent field both yield null there.
        val threshold =
            if (request.alertThresholdPercent == null) {
                open.alertThresholdPercent
            } else {
                request.alertThresholdPercent.orElse(null)?.also(::validateThreshold)
            }

        val effective =
            if (open.effectiveFromMonth == month.atDay(1)) {
                // Still the month this version started in — correct it in place
                // rather than leaving two versions for one month.
                open.limitAmount = limitAmount
                open.alertThresholdPercent = threshold
                open
            } else {
                open.effectiveToMonth = month.minusMonths(1).atDay(1)
                // Hibernate orders inserts before updates within a flush, so the
                // close has to reach the database first — otherwise two open
                // versions exist momentarily and the partial unique index trips.
                budgetVersionRepository.flush()
                budgetVersionRepository.save(
                    BudgetVersion(
                        budget = budget,
                        limitAmount = limitAmount,
                        alertThresholdPercent = threshold,
                        effectiveFromMonth = month.atDay(1),
                        effectiveToMonth = null,
                    ),
                )
            }
        return usageFor(effective, owner)
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        owner: User,
    ) {
        val budget = getOwnedBy(id, owner)
        val open = budgetVersionRepository.findOpenVersion(checkNotNull(budget.id))
        val month = currentMonth()
        when {
            open == null -> Unit

            // Created and deleted in the same month: it never applied anywhere,
            // and closing it at last month would invert its range.
            open.effectiveFromMonth == month.atDay(1) -> open.isDeleted = true

            else -> open.effectiveToMonth = month.minusMonths(1).atDay(1)
        }
        budget.isDeleted = true
    }

    private fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Budget {
        val budget =
            budgetRepository.findActiveById(id)
                ?: throw NotFoundException("Budget $id was not found")
        if (budget.owner != owner) throw NotFoundException("Budget $id was not found")
        return budget
    }

    private fun openVersionOf(budget: Budget): BudgetVersion =
        budgetVersionRepository.findOpenVersion(checkNotNull(budget.id))
            ?: throw ConflictException("Budget ${budget.id} has no version in force")

    private fun usageFor(
        version: BudgetVersion,
        owner: User,
    ): BudgetWithUsage {
        val month = currentMonth()
        val range = monthRange(month)
        val categoryIds =
            descendantsOf(version.budget.category, categoryService.descendantIndex(owner))
        val spent =
            transactionRepository.sumByTypeAndCategories(
                owner = owner,
                type = TransactionType.EXPENSE,
                categoryIds = categoryIds,
                from = range.from,
                to = range.to,
            )
        return withUsage(version, month, spent)
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

private fun requirePositiveLimit(limitAmount: BigDecimal?): BigDecimal {
    val value = limitAmount ?: throw invalidField("limitAmount", "is required")
    if (value.signum() <= 0) throw invalidField("limitAmount", "must be greater than zero")
    return value
}

/** The category itself plus every descendant — a parent budget caps the group. */
private fun descendantsOf(
    category: Category,
    index: Map<UUID, Set<UUID>>,
): Set<UUID> {
    val id = checkNotNull(category.id)
    return index[id] ?: setOf(id)
}

private fun withUsage(
    version: BudgetVersion,
    month: YearMonth,
    spent: BigDecimal,
): BudgetWithUsage =
    BudgetWithUsage(
        budget = version.budget,
        version = version,
        month = month,
        spent = spent,
        // Goes negative once the limit is passed; overspend is information.
        remaining = version.limitAmount - spent,
        percentUsed =
            spent
                .multiply(BigDecimal(100))
                .divide(version.limitAmount, PERCENT_SCALE, RoundingMode.HALF_UP),
    )
