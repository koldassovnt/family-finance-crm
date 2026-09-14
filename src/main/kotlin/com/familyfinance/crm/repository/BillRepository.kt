package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

/**
 * `month` and `isPaid` are independent filters, so there is one derived query
 * per combination. Verbose, but each is type-safe and none of them needs a
 * nullable parameter smuggled into JPQL.
 */
interface BillRepository : JpaRepository<Bill, UUID> {
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
