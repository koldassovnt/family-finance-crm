package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

interface BillRepository : JpaRepository<Bill, UUID> {
    /** The owner is fetched because a viewer's response names it. */
    @Query("SELECT b FROM Bill b JOIN FETCH b.owner WHERE b.id = :id")
    fun findDetailedById(id: UUID): Bill?

    /** The bills shared with a viewer; `month` and `unpaid` are applied in the service. */
    @Query(
        """
        SELECT b FROM Bill b
        JOIN FETCH b.owner
        WHERE b.id IN :ids
        ORDER BY b.dueDate ASC, b.name ASC
        """,
    )
    fun findAllDetailedByIds(ids: Collection<UUID>): List<Bill>

    /**
     * The bot's morning reminder: what each Telegram-linked owner still owes on
     * [date]. Only their own bills — a shared one is its owner's to pay.
     */
    @Query(
        """
        SELECT b FROM Bill b
        JOIN FETCH b.owner o
        WHERE b.dueDate = :date AND b.isPaid = false
          AND o.isDeleted = false AND o.telegramChatId IS NOT NULL
        ORDER BY o.displayName ASC, b.name ASC
        """,
    )
    fun findUnpaidDueOnForTelegramLinkedOwners(date: LocalDate): List<Bill>

    fun findAllByOwnerOrderByDueDateAscNameAsc(owner: User): List<Bill>

    fun findAllByOwnerAndBatchId(
        owner: User,
        batchId: UUID,
    ): List<Bill>
}
