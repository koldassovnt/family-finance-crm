package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Account
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
        WHERE t.id = :id
        """,
    )
    fun findDetailedById(id: UUID): Transaction?

    /**
     * Month totals per type for one owner. `ADJUSTMENT` is a balance
     * correction, not spending, so callers must not include it — see `00-`.
     */
    @Query(
        """
        SELECT coalesce(sum(t.amount), 0) FROM Transaction t
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
     * The same aggregation as [sumByCategory], narrowed to one category —
     * this is what a budget's usage is computed from.
     */
    @Query(
        """
        SELECT coalesce(sum(t.amount), 0) FROM Transaction t
        WHERE t.account.owner = :owner
          AND t.type = :type
          AND t.category.id = :categoryId
          AND t.occurredOn BETWEEN :from AND :to
        """,
    )
    fun sumByTypeAndCategory(
        owner: User,
        type: TransactionType,
        categoryId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): BigDecimal

    @Query(
        """
        SELECT c.id AS categoryId, c.name AS categoryName, sum(t.amount) AS total
        FROM Transaction t
        LEFT JOIN t.category c
        WHERE t.account.owner = :owner
          AND t.type = :type
          AND t.occurredOn BETWEEN :from AND :to
        GROUP BY c.id, c.name
        ORDER BY sum(t.amount) DESC
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
