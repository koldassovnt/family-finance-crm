package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.Bank
import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.repository.CategoryTotal
import com.familyfinance.crm.service.BudgetWithUsage
import com.familyfinance.crm.service.GoalWithProgress
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
        note = note,
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

fun CategoryTotal.toSummary() =
    CategorySummary(
        categoryId = categoryId,
        categoryName = categoryName,
        total = total,
    )

/** Any entity reaching the API boundary has been persisted, so its id is set. */
fun BaseEntity.requiredId(): UUID = checkNotNull(id) { "Entity has not been persisted yet" }
