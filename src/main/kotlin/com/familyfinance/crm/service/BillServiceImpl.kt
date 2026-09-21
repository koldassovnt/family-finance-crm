package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateBillBatchRequest
import com.familyfinance.crm.dto.CreateBillRequest
import com.familyfinance.crm.dto.UpdateBillRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.BillRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BillServiceImpl(
    private val billRepository: BillRepository,
    private val shareAccess: ShareAccessService,
    private val clock: Clock,
) : BillService {
    @Transactional(readOnly = true)
    override fun list(
        reader: User,
        month: YearMonth?,
        unpaid: Boolean?,
        scope: ShareScope,
    ): List<Readable<BillWithStatus>> =
        scope.collect(
            own = { listOwn(reader, month, unpaid).map { Readable.Own(it) } },
            shared = { listShared(reader, month, unpaid) },
        )

    private fun listOwn(
        owner: User,
        month: YearMonth?,
        unpaid: Boolean?,
    ): List<BillWithStatus> {
        // ponytail: filtered in memory; one query with nullable parameters (like
        // `findForOwner`) if an owner's bills ever run into the thousands.
        val matches = billFilter(month, unpaid)
        return billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner).filter(matches).map(::withStatus)
    }

    /** A viewer holds a handful of shared bills; one id lookup, then the same filter as their own. */
    private fun listShared(
        reader: User,
        month: YearMonth?,
        unpaid: Boolean?,
    ): List<Readable<BillWithStatus>> {
        val matches = billFilter(month, unpaid)
        return shareAccess
            .sharedWith(
                reader = reader,
                resourceType = ShareResourceType.BILL,
                load = billRepository::findAllDetailedByIds,
                ownerOf = Bill::owner,
            ).filter { matches(it.resource) }
            .map { readable -> readable.map(::withStatus) }
    }

    @Transactional
    override fun create(
        owner: User,
        request: CreateBillRequest,
    ): BillWithStatus {
        val amount = requirePositive(request.amount, "amount")
        val dueDate = request.dueDate ?: throw invalidField("dueDate", "is required")
        // A bill records something planned, so unlike a transaction it may sit in
        // the future — and a past date simply means it is overdue.
        val bill =
            billRepository.save(
                Bill(
                    owner = owner,
                    name = requireNonBlankName(request.name),
                    amount = amount,
                    currency = normalizeCurrency(request.currency),
                    dueDate = dueDate,
                    isPaid = false,
                    batchId = null,
                ),
            )
        return withStatus(bill)
    }

    @Transactional
    override fun createBatch(
        owner: User,
        request: CreateBillBatchRequest,
    ): List<BillWithStatus> {
        val amount = requirePositive(request.amount, "amount")
        val dayOfMonth = request.dayOfMonth ?: throw invalidField("dayOfMonth", "is required")
        if (dayOfMonth !in 1..MAX_DAY_OF_MONTH) {
            throw invalidField("dayOfMonth", "must be between 1 and $MAX_DAY_OF_MONTH")
        }
        val startMonth = parseMonth(request.startMonth, field = "startMonth")
        val endMonth = parseMonth(request.endMonth, field = "endMonth")
        if (endMonth.isBefore(startMonth)) {
            throw invalidField("endMonth", "must not be before 'startMonth'")
        }
        val months = ChronoUnit.MONTHS.between(startMonth, endMonth) + 1
        if (months > MAX_BATCH_ROWS) {
            // A typo in endMonth must not be able to generate thousands of rows.
            throw ValidationException(
                "A batch may create at most $MAX_BATCH_ROWS bills; this would create $months",
                mapOf("endMonth" to "would create $months bills, more than $MAX_BATCH_ROWS"),
            )
        }

        val batchId = UUID.randomUUID()
        val currency = normalizeCurrency(request.currency)
        val name = requireNonBlankName(request.name)
        val bills =
            generateSequence(startMonth) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(endMonth) }
                .map { month ->
                    Bill(
                        owner = owner,
                        name = name,
                        amount = amount,
                        currency = currency,
                        dueDate = month.atDay(dayWithin(month, dayOfMonth)),
                        isPaid = false,
                        batchId = batchId,
                    )
                }.toList()
        return billRepository.saveAll(bills).map(::withStatus)
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateBillRequest,
    ): BillWithStatus {
        val bill = getOwnedBy(id, owner)
        request.name?.let { bill.name = requireNonBlankName(it) }
        request.currency?.let { currency ->
            // Changing only the currency would silently reinterpret the amount —
            // 12000 KZT becoming 12000 USD is a ~480x rewrite with no conversion.
            val normalized = normalizeCurrency(currency)
            if (normalized != bill.currency && request.amount == null) {
                throw invalidField("amount", "is required when changing the currency")
            }
            bill.currency = normalized
        }
        request.amount?.let { bill.amount = requirePositive(it, "amount") }
        request.dueDate?.let { bill.dueDate = it }
        // Editing one row never touches its siblings, and it keeps its batchId.
        request.isPaid?.let { bill.isPaid = it }
        return withStatus(bill)
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        owner: User,
    ) {
        getOwnedBy(id, owner).isDeleted = true
    }

    @Transactional
    override fun softDeleteBatch(
        batchId: UUID,
        owner: User,
    ) {
        val bills = billRepository.findAllByOwnerAndBatchId(owner, batchId)
        if (bills.isEmpty()) throw NotFoundException("Bill batch $batchId was not found")
        bills.forEach { it.isDeleted = true }
    }

    @Transactional(readOnly = true)
    override fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Bill {
        val bill =
            billRepository.findDetailedById(id) ?: throw NotFoundException("Bill $id was not found")
        if (bill.owner != owner) throw NotFoundException("Bill $id was not found")
        return bill
    }

    /** Overdue is derived against today in the app timezone, so it can't go stale. */
    private fun withStatus(bill: Bill): BillWithStatus =
        BillWithStatus(
            bill = bill,
            overdue = !bill.isPaid && bill.dueDate.isBefore(LocalDate.now(clock)),
        )
}

private const val MAX_DAY_OF_MONTH = 31
private const val MAX_BATCH_ROWS = 120

/** The 31st of a 30-day month clamps to the 30th rather than skipping or rolling over. */
private fun dayWithin(
    month: YearMonth,
    dayOfMonth: Int,
): Int = minOf(dayOfMonth, month.lengthOfMonth())

/** `unpaid = true` means isPaid = false, so the flag inverts; null skips that filter. */
private fun billFilter(
    month: YearMonth?,
    unpaid: Boolean?,
): (Bill) -> Boolean {
    val range = month?.let { monthRange(it) }
    return { bill ->
        (range == null || bill.dueDate in range.from..range.to) && (unpaid == null || bill.isPaid != unpaid)
    }
}
