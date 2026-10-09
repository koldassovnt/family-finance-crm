package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.Bank
import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.domain.Share
import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.repository.CategoryTotal
import com.familyfinance.crm.service.BillWithStatus
import com.familyfinance.crm.service.BudgetWithUsage
import com.familyfinance.crm.service.GoalWithProgress
import com.familyfinance.crm.service.Readable
import com.familyfinance.crm.service.TopicDetail
import com.familyfinance.crm.service.TopicWithTotals
import com.familyfinance.crm.service.ValuedHolding
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

fun User.toResponse() =
    UserResponse(
        id = requiredId(),
        email = email,
        displayName = displayName,
        role = role,
        createdAt = createdAt,
    )

fun User.toRef() = UserRef(id = requiredId(), displayName = displayName)

fun Bank.toResponse() = BankResponse(id = requiredId(), name = name)

fun Account.toResponse() =
    AccountResponse(
        id = requiredId(),
        name = name,
        type = type,
        balance = balance,
        currency = currency,
        bank = bank?.toResponse(),
    )

/**
 * Both badge fields come from the same [Readable] that decided them, so a
 * response can never say it is shared without saying whose it is, or the
 * reverse. `owner` stays null for the caller's own things — they know.
 */
fun Readable<Account>.toResponse() = resource.toResponse().copy(access = accessLevel, owner = sharedBy?.toRef())

fun Readable<TopicWithTotals>.toTopicResponse() = resource.toResponse().copy(access = accessLevel, owner = sharedBy?.toRef())

fun Readable<TopicDetail>.toTopicDetailResponse() =
    TopicDetailResponse(
        topic = resource.totals.toResponse().copy(access = accessLevel, owner = sharedBy?.toRef()),
        expenseByCategory = resource.expenseByCategory.map { it.toSummary() },
        incomeByCategory = resource.incomeByCategory.map { it.toSummary() },
    )

fun Readable<GoalWithProgress>.toGoalResponse() = resource.toResponse().copy(access = accessLevel, owner = sharedBy?.toRef())

fun Readable<BudgetWithUsage>.toBudgetResponse() = resource.toResponse().copy(access = accessLevel, owner = sharedBy?.toRef())

fun Readable<BillWithStatus>.toBillResponse() = resource.toResponse().copy(access = accessLevel, owner = sharedBy?.toRef())

fun Share.toResponse(resourceName: String? = null) =
    ShareResponse(
        id = requiredId(),
        resourceType = resourceType,
        resourceId = resourceId,
        resourceName = resourceName,
        owner = owner.toRef(),
        grantee = grantee.toRef(),
        access = access,
        sharedAt = createdAt,
    )

/** The embedded form: everything a goal needs, and no claim about access. */
fun Account.toLinkedResponse() =
    LinkedAccountResponse(
        id = requiredId(),
        name = name,
        type = type,
        balance = balance,
        currency = currency,
        bank = bank?.toResponse(),
    )

fun Category.toResponse() =
    CategoryResponse(
        id = requiredId(),
        name = name,
        kind = kind,
        parentId = parent?.id,
    )

fun Transaction.toResponse() =
    TransactionResponse(
        id = requiredId(),
        type = type,
        amount = amount,
        currency = currency,
        toAmount = toAmount,
        exchangeRate = exchangeRate,
        amountKzt = amountKzt,
        occurredOn = occurredOn,
        accountId = account.requiredId(),
        toAccountId = toAccount?.id,
        category = category?.toResponse(),
        topic = topic?.toRef(),
        note = note,
        tradeSide = tradeSide,
        ticker = ticker,
        quantity = quantity,
        unitPrice = unitPrice,
    )

fun ValuedHolding.toResponse() =
    HoldingResponse(
        ticker = holding.ticker,
        accountId = holding.account.requiredId(),
        accountName = holding.account.name,
        accountType = holding.account.type,
        currency = holding.currency,
        quantity = holding.quantity,
        averagePrice = holding.averagePrice,
        averagePriceKzt = holding.averagePriceKzt,
        cost = holding.cost,
        costKzt = holding.costKzt,
        price = price,
        priceAsOf = priceAsOf,
        exchange = exchange,
        value = value,
        valueKzt = valueKzt,
        gain = gain,
        gainKzt = gainKzt,
    )

fun List<ValuedHolding>.toInvestmentsResponse() =
    InvestmentsResponse(
        holdings = map { it.toResponse() },
        totalsByCurrency =
            groupBy { it.holding.currency }
                .map { (currency, holdings) ->
                    CurrencyTotal(
                        currency = currency,
                        cost = holdings.sumOf { it.holding.cost },
                        costKzt = holdings.sumOf { it.holding.costKzt },
                        value = holdings.sumOfKnown { it.value },
                        valueKzt = holdings.sumOfKnown { it.valueKzt },
                        gain = holdings.sumOfKnown { it.gain },
                        gainKzt = holdings.sumOfKnown { it.gainKzt },
                        unpriced = holdings.count { it.value == null },
                    )
                }.sortedBy { it.currency },
        totalCostKzt = sumOf { it.holding.costKzt },
        totalValueKzt = sumOfKnown { it.valueKzt },
        totalGainKzt = sumOfKnown { it.gainKzt },
        unpriced = count { it.valueKzt == null },
    )

/** The figure summed over the holdings that have it, or null when none does. Callers report how many were left out. */
private fun List<ValuedHolding>.sumOfKnown(figure: (ValuedHolding) -> BigDecimal?): BigDecimal? =
    mapNotNull(figure).takeIf { it.isNotEmpty() }?.sumOf { it }

fun MarketQuote.toRateResponse() = ExchangeRateResponse(currency = symbol, rateKzt = price, fetchedAt = fetchedAt)

fun Topic.toRef() = TopicRef(id = requiredId(), name = name, status = status)

fun TopicWithTotals.toResponse() =
    TopicResponse(
        id = topic.requiredId(),
        name = topic.name,
        description = topic.description,
        startDate = topic.startDate,
        endDate = topic.endDate,
        plannedAmount = topic.plannedAmount,
        status = topic.status,
        spent = spent,
        received = received,
        net = net,
        remaining = remaining,
        transactionCount = transactionCount,
        firstTransactionOn = firstTransactionOn,
        lastTransactionOn = lastTransactionOn,
    )

fun BudgetWithUsage.toResponse() =
    BudgetResponse(
        id = budget.requiredId(),
        category = budget.category.toResponse(),
        limitAmount = version.limitAmount,
        period = budget.period,
        alertThresholdPercent = version.alertThresholdPercent,
        month = month.toString(),
        effectiveFrom = YearMonth.from(version.effectiveFromMonth).toString(),
        effectiveTo = version.effectiveToMonth?.let { YearMonth.from(it).toString() },
        spent = spent,
        remaining = remaining,
        percentUsed = percentUsed,
    )

fun GoalWithProgress.toResponse() =
    GoalResponse(
        id = goal.requiredId(),
        name = goal.name,
        type = goal.type,
        targetAmount = goal.targetAmount,
        targetDate = goal.targetDate,
        linkedAccount = goal.linkedAccount.toLinkedResponse(),
        status = goal.status,
        progressPercent = progressPercent,
        achieved = achieved,
    )

fun BillWithStatus.toResponse() =
    BillResponse(
        id = bill.requiredId(),
        name = bill.name,
        amount = bill.amount,
        currency = bill.currency,
        dueDate = bill.dueDate,
        isPaid = bill.isPaid,
        overdue = overdue,
        batchId = bill.batchId,
    )

fun CategoryTotal.toSummary() =
    CategorySummary(
        categoryId = categoryId,
        categoryName = categoryName,
        total = total,
    )

/** Any entity reaching the API boundary has been persisted, so its id is set. */
fun BaseEntity.requiredId(): UUID = checkNotNull(id) { "Entity has not been persisted yet" }
