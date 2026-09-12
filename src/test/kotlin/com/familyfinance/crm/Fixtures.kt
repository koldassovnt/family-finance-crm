package com.familyfinance.crm

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.BaseEntity
import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.BudgetPeriod
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.domain.Goal
import com.familyfinance.crm.domain.GoalStatus
import com.familyfinance.crm.domain.GoalType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.domain.UserRole
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

val ALMATY: ZoneId = ZoneId.of("Asia/Almaty")

/** Fixes "today" at 2026-09-10 in the app timezone. */
fun fixedClock(today: LocalDate = LocalDate.of(2026, 9, 10)): Clock = Clock.fixed(today.atTime(12, 0).atZone(ALMATY).toInstant(), ALMATY)

fun <T : BaseEntity> T.withId(id: UUID = UUID.randomUUID()): T = apply { this.id = id }

/** Fixture entities are always given an id, so unwrapping it is safe here. */
val BaseEntity.idValue: UUID get() = checkNotNull(id)

fun user(
    id: UUID = UUID.randomUUID(),
    email: String = "owner@example.com",
    role: UserRole = UserRole.OWNER,
): User =
    User(
        email = email,
        displayName = "Owner",
        passwordHash = "hashed",
        role = role,
    ).withId(id)

fun account(
    owner: User,
    balance: String = "0",
    currency: String = "KZT",
    type: AccountType = AccountType.BANK,
    id: UUID = UUID.randomUUID(),
): Account =
    Account(
        owner = owner,
        bank = null,
        name = "Main",
        type = type,
        balance = BigDecimal(balance),
        currency = currency,
    ).withId(id)

fun category(
    owner: User,
    kind: CategoryKind = CategoryKind.EXPENSE,
    parent: Category? = null,
    id: UUID = UUID.randomUUID(),
): Category =
    Category(
        owner = owner,
        name = "Groceries",
        parent = parent,
        kind = kind,
    ).withId(id)

fun budget(
    owner: User,
    category: Category,
    limitAmount: String = "50000",
    alertThresholdPercent: Int? = 80,
    id: UUID = UUID.randomUUID(),
): Budget =
    Budget(
        owner = owner,
        category = category,
        limitAmount = BigDecimal(limitAmount),
        period = BudgetPeriod.MONTHLY,
        alertThresholdPercent = alertThresholdPercent,
    ).withId(id)

fun goal(
    owner: User,
    linkedAccount: Account,
    targetAmount: String = "1000000",
    status: GoalStatus = GoalStatus.ACTIVE,
    id: UUID = UUID.randomUUID(),
): Goal =
    Goal(
        owner = owner,
        name = "Emergency fund",
        type = GoalType.EMERGENCY_FUND,
        targetAmount = BigDecimal(targetAmount),
        targetDate = null,
        linkedAccount = linkedAccount,
        status = status,
    ).withId(id)
