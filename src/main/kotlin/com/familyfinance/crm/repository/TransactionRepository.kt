package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** One row of the monthly per-category breakdown. */
interface CategoryTotal {
    val categoryId: UUID?
    val categoryName: String?
    val total: BigDecimal
}

/** One topic's aggregates, so the list view costs one query rather than N. */
interface TopicTotals {
    val topicId: UUID
    val spent: BigDecimal
    val received: BigDecimal
    val transactionCount: Long
    val firstTransactionOn: LocalDate?
    val lastTransactionOn: LocalDate?
}

interface TransactionRepository : JpaRepository<Transaction, UUID> {
    /**
     * Account history matches **either** side of a transfer, so a transfer
     * appears for the destination account too — not only the source.
     */
    @Query(
        """
        SELECT t FROM Transaction t
        JOIN FETCH t.account
        LEFT JOIN FETCH t.toAccount
        LEFT JOIN FETCH t.category
        LEFT JOIN FETCH t.topic
        WHERE (t.account = :account OR t.toAccount = :account)
          AND t.occurredOn BETWEEN :from AND :to
        ORDER BY t.occurredOn DESC, t.createdAt DESC
        """,
    )
    fun findHistory(
        account: Account,
        from: LocalDate,
        to: LocalDate,
    ): List<Transaction>

    /**
     * The same window across every account the owner has, with both filters
     * optional. Transfers between the owner's own accounts are the only ones
     * that exist, so matching on the source account's owner already covers
     * both sides — but an `accountId` filter still has to match either side,
     * exactly as [findHistory] does.
     */
    @Query(
        """
        SELECT t FROM Transaction t
        JOIN FETCH t.account a
        LEFT JOIN FETCH t.toAccount ta
        LEFT JOIN FETCH t.category c
        LEFT JOIN FETCH t.topic tp
        WHERE a.owner = :owner
          AND t.occurredOn BETWEEN :from AND :to
          AND (:accountId IS NULL OR a.id = :accountId OR ta.id = :accountId)
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:topicId IS NULL OR tp.id = :topicId)
        ORDER BY t.occurredOn DESC, t.createdAt DESC
        """,
    )
    fun findForOwner(
        owner: User,
        from: LocalDate,
        to: LocalDate,
        accountId: UUID?,
        categoryId: UUID?,
        topicId: UUID?,
    ): List<Transaction>

    /**
     * Fetches the associations the response needs up front: with
     * `open-in-view: false` the session is gone by the time the controller maps
     * the entity, so a lazy proxy there is a 500.
     */
    @Query(
        """
        SELECT t FROM Transaction t
        JOIN FETCH t.account
        LEFT JOIN FETCH t.toAccount
        LEFT JOIN FETCH t.category
        LEFT JOIN FETCH t.topic
        WHERE t.id = :id
        """,
    )
    fun findDetailedById(id: UUID): Transaction?

    /** The same fetch shape as [findDetailedById], for a bulk attach. */
    @Query(
        """
        SELECT t FROM Transaction t
        JOIN FETCH t.account
        LEFT JOIN FETCH t.toAccount
        LEFT JOIN FETCH t.category
        LEFT JOIN FETCH t.topic
        WHERE t.id IN :ids
        """,
    )
    fun findAllDetailedByIds(ids: Collection<UUID>): List<Transaction>

    /**
     * Everything attached to one topic, unbounded by date: membership is
     * itself the bound, so a trip's own view needs no range — see the Phase 7
     * doc.
     */
    @Query(
        """
        SELECT t FROM Transaction t
        JOIN FETCH t.account
        LEFT JOIN FETCH t.toAccount
        LEFT JOIN FETCH t.category
        LEFT JOIN FETCH t.topic
        WHERE t.topic = :topic
        ORDER BY t.occurredOn DESC, t.createdAt DESC
        """,
    )
    fun findAllByTopic(topic: Topic): List<Transaction>

    /**
     * Unattached spending inside a topic's window — the suggestion list behind
     * bulk attach. Already-attached rows are excluded so the list shrinks as
     * the user works through it.
     */
    @Query(
        """
        SELECT t FROM Transaction t
        JOIN FETCH t.account
        LEFT JOIN FETCH t.toAccount
        LEFT JOIN FETCH t.category
        WHERE t.account.owner = :owner
          AND t.topic IS NULL
          AND t.type IN :types
          AND t.occurredOn BETWEEN :from AND :to
        ORDER BY t.occurredOn DESC, t.createdAt DESC
        """,
    )
    fun findTopicCandidates(
        owner: User,
        types: Collection<TransactionType>,
        from: LocalDate,
        to: LocalDate,
    ): List<Transaction>

    /**
     * Per-topic totals for one owner in a single query — the list view needs
     * them for every topic at once, and a sum per topic would be N+1.
     */
    @Query(
        """
        SELECT t.topic.id AS topicId,
               coalesce(sum(CASE WHEN t.type = :expense THEN t.amountKzt ELSE 0 END), 0) AS spent,
               coalesce(sum(CASE WHEN t.type = :income THEN t.amountKzt ELSE 0 END), 0) AS received,
               count(t) AS transactionCount,
               min(t.occurredOn) AS firstTransactionOn,
               max(t.occurredOn) AS lastTransactionOn
        FROM Transaction t
        WHERE t.account.owner = :owner AND t.topic IS NOT NULL
        GROUP BY t.topic.id
        """,
    )
    fun sumByTopic(
        owner: User,
        expense: TransactionType,
        income: TransactionType,
    ): List<TopicTotals>

    /** The monthly summary's breakdown, narrowed to one topic. */
    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName, sum(t.amountKzt) AS total
        FROM Transaction t
        LEFT JOIN t.category c
        WHERE t.topic = :topic AND t.type = :type
        GROUP BY c.id, c.name
        ORDER BY sum(t.amountKzt) DESC
        """,
    )
    fun sumByTopicAndCategory(
        topic: Topic,
        type: TransactionType,
    ): List<CategoryTotal>

    /**
     * Month totals per type for one owner, in KZT. `ADJUSTMENT` is a balance
     * correction, not spending, so callers must not include it — see `00-`.
     */
    @Query(
        """
        SELECT coalesce(sum(t.amountKzt), 0) FROM Transaction t
        WHERE t.account.owner = :owner
          AND t.type = :type
          AND t.occurredOn BETWEEN :from AND :to
        """,
    )
    fun sumByType(
        owner: User,
        type: TransactionType,
        from: LocalDate,
        to: LocalDate,
    ): BigDecimal

    /**
     * The same aggregation as [sumByCategory], narrowed to a set of categories.
     * A budget passes its own category plus every descendant, so spending filed
     * under a sub-category counts toward the parent's budget.
     */
    @Query(
        """
        SELECT coalesce(sum(t.amountKzt), 0) FROM Transaction t
        WHERE t.account.owner = :owner
          AND t.type = :type
          AND t.category.id IN :categoryIds
          AND t.occurredOn BETWEEN :from AND :to
        """,
    )
    fun sumByTypeAndCategories(
        owner: User,
        type: TransactionType,
        categoryIds: Collection<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): BigDecimal

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName, sum(t.amountKzt) AS total
        FROM Transaction t
        LEFT JOIN t.category c
        WHERE t.account.owner = :owner
          AND t.type = :type
          AND t.occurredOn BETWEEN :from AND :to
        GROUP BY c.id, c.name
        ORDER BY sum(t.amountKzt) DESC
        """,
    )
    fun sumByCategory(
        owner: User,
        type: TransactionType,
        from: LocalDate,
        to: LocalDate,
    ): List<CategoryTotal>

    @Query(
        """
        SELECT count(t) > 0 FROM Transaction t
        WHERE t.account = :account OR t.toAccount = :account
        """,
    )
    fun existsForAccount(account: Account): Boolean
}
