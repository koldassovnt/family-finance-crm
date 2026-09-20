package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

/**
 * `month` and `isPaid` are independent filters, so there is one derived query
 * per combination. Verbose, but each is type-safe and none of them needs a
 * nullable parameter smuggled into JPQL.
 */
interface BillRepository : JpaRepository<Bill, UUID> {
    /** The owner is fetched because a viewer's response names it. */
    @Query("SELECT b FROM Bill b JOIN FETCH b.owner WHERE b.id = :id")
    fun findDetailedById(id: UUID): Bill?

    /**
     * The bills shared with a viewer. `month` and `unpaid` are applied in the
     * service rather than here: a viewer has a handful of shared bills, and one
     * id lookup beats four more derived queries for the same filters.
     */
    @Query(
        """
        SELECT b FROM Bill b
        JOIN FETCH b.owner
        WHERE b.id IN :ids
        ORDER BY b.dueDate ASC, b.name ASC
        """,
    )
    fun findAllDetailedByIds(ids: Collection<UUID>): List<Bill>

    fun findAllByOwnerOrderByDueDateAscNameAsc(owner: User): List<Bill>

    fun findAllByOwnerAndIsPaidOrderByDueDateAscNameAsc(
        owner: User,
        isPaid: Boolean,
    ): List<Bill>

    fun findAllByOwnerAndDueDateBetweenOrderByDueDateAscNameAsc(
        owner: User,
        from: LocalDate,
        to: LocalDate,
    ): List<Bill>

    fun findAllByOwnerAndIsPaidAndDueDateBetweenOrderByDueDateAscNameAsc(
        owner: User,
        isPaid: Boolean,
        from: LocalDate,
        to: LocalDate,
    ): List<Bill>

    fun findAllByOwnerAndBatchId(
        owner: User,
        batchId: UUID,
    ): List<Bill>
}
