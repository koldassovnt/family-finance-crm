package com.familyfinance.crm.domain

enum class UserRole { OWNER, MEMBER }

enum class AccountType { CASH, BANK, DEPOSIT, BROKER }

enum class CategoryKind { EXPENSE, INCOME }

enum class TransactionType { INCOME, EXPENSE, TRANSFER, ADJUSTMENT }

/** `MONTHLY` only — `YEARLY` waits for an actual need. */
enum class BudgetPeriod { MONTHLY, }

enum class GoalType { SAVINGS, EMERGENCY_FUND }

/**
 * `CLOSED` keeps the record but drops the topic from the transaction form's
 * picker. Two states are enough: an undertaking that never happened has no
 * transactions and can simply be deleted.
 */
enum class TopicStatus { ACTIVE, CLOSED }

/**
 * User-set only. There is no `ACHIEVED` state: "achieved" is derived from the
 * linked account's balance on read, so an achieved goal can still be abandoned
 * or archived. Only `ACTIVE` goals block deleting their linked account.
 */
enum class GoalStatus { ACTIVE, ABANDONED, ARCHIVED }
