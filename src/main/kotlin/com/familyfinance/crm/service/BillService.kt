package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateBillBatchRequest
import com.familyfinance.crm.dto.CreateBillRequest
import com.familyfinance.crm.dto.UpdateBillRequest
import java.time.YearMonth
import java.util.UUID

/** A bill plus whether it is overdue, which is derived on read rather than stored. */
data class BillWithStatus(
    val bill: Bill,
    val overdue: Boolean,
)

interface BillService {
    /** All of the caller's bills, or only those due in [month] when given. */
    fun list(
        owner: User,
        month: YearMonth?,
    ): List<BillWithStatus>

    fun create(
        owner: User,
        request: CreateBillRequest,
    ): BillWithStatus

    /** Expands the pattern into ordinary rows sharing one `batchId`. */
    fun createBatch(
        owner: User,
        request: CreateBillBatchRequest,
    ): List<BillWithStatus>

    fun update(
        id: UUID,
        owner: User,
        request: UpdateBillRequest,
    ): BillWithStatus

    fun softDelete(
        id: UUID,
        owner: User,
    )

    /** Soft-deletes every row created in one batch call. */
    fun softDeleteBatch(
        batchId: UUID,
        owner: User,
    )
}
