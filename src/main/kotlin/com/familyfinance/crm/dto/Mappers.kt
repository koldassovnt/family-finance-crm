package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.Bank
import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.repository.CategoryTotal
import com.familyfinance.crm.service.BillWithStatus
import com.familyfinance.crm.service.BudgetWithUsage
import com.familyfinance.crm.service.GoalWithProgress
import com.familyfinance.crm.service.TopicDetail
import com.familyfinance.crm.service.TopicWithTotals
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
    )

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

fun TopicDetail.toResponse() =
    TopicDetailResponse(
        topic = totals.toResponse(),
        expenseByCategory = expenseByCategory.map { it.toSummary() },
        incomeByCategory = incomeByCategory.map { it.toSummary() },
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
        linkedAccount = goal.linkedAccount.toResponse(),
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
