package com.familyfinance.crm.domain

enum class UserRole { OWNER, MEMBER }

enum class AccountType { CASH, BANK, DEPOSIT, BROKER }

enum class CategoryKind { EXPENSE, INCOME }

enum class TransactionType { INCOME, EXPENSE, TRANSFER, ADJUSTMENT }

/** `MONTHLY` only — `YEARLY` waits for an actual need. */
enum class BudgetPeriod { MONTHLY, }

enum class GoalType { SAVINGS, EMERGENCY_FUND }

/**
 * User-set only. There is no `ACHIEVED` state: "achieved" is derived from the
 * linked account's balance on read, so an achieved goal can still be abandoned
 * or archived. Only `ACTIVE` goals block deleting their linked account.
 */
enum class GoalStatus { ACTIVE, ABANDONED, ARCHIVED }
