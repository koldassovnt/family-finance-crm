package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface BillRepository : JpaRepository<Bill, UUID> {
    fun findAllByOwnerOrderByDueDateAscNameAsc(owner: User): List<Bill>

    fun findAllByOwnerAndDueDateBetweenOrderByDueDateAscNameAsc(
        owner: User,
        from: LocalDate,
        to: LocalDate,
    ): List<Bill>

    fun findAllByOwnerAndBatchId(
        owner: User,
        batchId: UUID,
    ): List<Bill>
}
