package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.ShareScope
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
    /**
     * The caller's bills, narrowed by either filter or both. [month] matches the
     * due date; [unpaid] is `true` for outstanding bills and `false` for settled
     * ones. Both null returns everything.
     *
     * The two are independent: `month` alone is the calendar grid, and `unpaid`
     * alone is what's still owed — including bills that fell due in an earlier
     * month, which a month view by definition cannot show.
     *
     * [scope] defaults to `OWN`. A bill has no detail endpoint of its own, so
     * this list is also the read path a viewer uses; both filters apply to
     * shared bills exactly as they do to the caller's own.
     */
    fun list(
        reader: User,
        month: YearMonth?,
        unpaid: Boolean?,
        scope: ShareScope = ShareScope.OWN,
    ): List<Readable<BillWithStatus>>

    /**
     * Resolves a bill the caller owns; someone else's 404s like a missing one.
     * Every write path uses this, and a bill is only ever read through [list].
     */
    fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Bill

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
