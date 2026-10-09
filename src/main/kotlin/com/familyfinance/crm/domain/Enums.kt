package com.familyfinance.crm.domain

enum class UserRole { OWNER, MEMBER }

enum class AccountType {
    CASH,
    BANK,
    DEPOSIT,
    BROKER,
    CRYPTO,
    ;

    /** Only these may record a `TRADE` — see `phase-5-investments.md`. */
    val holdsAssets: Boolean get() = this == BROKER || this == CRYPTO
}

enum class CategoryKind { EXPENSE, INCOME }

enum class TransactionType { INCOME, EXPENSE, TRANSFER, ADJUSTMENT, TRADE }

/**
 * `BUY` debits the account and `SELL` credits it. `OPENING` records an asset
 * already held before tracking began: it counts toward the holding at the price
 * paid, and moves no cash, because that cash left before the ledger started.
 */
enum class TradeSide { BUY, SELL, OPENING }

/**
 * What a [MarketQuote] prices, which is also which API it came from. A
 * holding's kind follows its account: a `CRYPTO` account holds coins, a
 * `BROKER` account holds everything the stock API knows.
 */
enum class QuoteKind { STOCK, CRYPTO, CURRENCY }

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

/** The five things a share can point at — see `phase-8-sharing.md`. */
enum class ShareResourceType { ACCOUNT, GOAL, BUDGET, BILL, TOPIC }

/**
 * What a grant confers. `VIEWER` only, like [BudgetPeriod], so an `EDITOR`
 * would later be a new value rather than a new concept.
 */
enum class ShareAccess { VIEWER, }

/**
 * How the caller reached a resource: as its owner, or through a share. Every
 * [ShareAccess] value needs a counterpart here, which [asAccessLevel] keeps
 * honest — adding `EDITOR` above stops compiling until it is handled.
 */
enum class AccessLevel { OWNER, VIEWER }

fun ShareAccess.asAccessLevel(): AccessLevel =
    when (this) {
        ShareAccess.VIEWER -> AccessLevel.VIEWER
    }

/**
 * Which resources a list endpoint returns. `OWN` is the default everywhere,
 * because it is the scope where nothing can sum across owners by accident.
 */
enum class ShareScope {
    OWN,
    SHARED,
    ALL,
    ;

    val includesOwn: Boolean get() = this != SHARED
    val includesShared: Boolean get() = this != OWN

    /** Own rows first, then shared — each side only loaded when in scope. */
    fun <T> collect(
        own: () -> List<T>,
        shared: () -> List<T>,
    ): List<T> = (if (includesOwn) own() else emptyList()) + (if (includesShared) shared() else emptyList())
}
