package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Bill
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
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BillServiceImpl(
    private val billRepository: BillRepository,
    private val clock: Clock,
) : BillService {
    @Transactional(readOnly = true)
    override fun list(
        owner: User,
        month: YearMonth?,
        unpaid: Boolean?,
    ): List<BillWithStatus> {
        val range = month?.let { monthRange(it) }
        // `unpaid = true` means isPaid = false, so the flag inverts.
        val isPaid = unpaid?.not()
        // Nested rather than a flat `when`, so both nullables narrow by branch.
        val bills =
            if (isPaid == null) {
                if (range == null) {
                    billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner)
                } else {
                    billRepository.findAllByOwnerAndDueDateBetweenOrderByDueDateAscNameAsc(
                        owner = owner,
                        from = range.from,
                        to = range.to,
                    )
                }
            } else {
                if (range == null) {
                    billRepository.findAllByOwnerAndIsPaidOrderByDueDateAscNameAsc(
                        owner = owner,
                        isPaid = isPaid,
                    )
                } else {
                    billRepository.findAllByOwnerAndIsPaidAndDueDateBetweenOrderByDueDateAscNameAsc(
                        owner = owner,
                        isPaid = isPaid,
                        from = range.from,
                        to = range.to,
                    )
                }
            }
        return bills.map(::withStatus)
    }

    @Transactional
    override fun create(
        owner: User,
        request: CreateBillRequest,
    ): BillWithStatus {
        val amount = requirePositiveAmount(request.amount)
        val dueDate = request.dueDate ?: throw invalidField("dueDate", "is required")
        // A bill records something planned, so unlike a transaction it may sit in
        // the future — and a past date simply means it is overdue.
        val bill =
            billRepository.save(
                Bill(
                    owner = owner,
                    name = request.name.trim(),
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
        val amount = requirePositiveAmount(request.amount)
        val dayOfMonth = request.dayOfMonth ?: throw invalidField("dayOfMonth", "is required")
        if (dayOfMonth !in 1..MAX_DAY_OF_MONTH) {
            throw invalidField("dayOfMonth", "must be between 1 and $MAX_DAY_OF_MONTH")
        }
        val startMonth = parseMonthField("startMonth", request.startMonth)
        val endMonth = parseMonthField("endMonth", request.endMonth)
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
        val name = request.name.trim()
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
        request.name?.let { bill.name = it.trim() }
        request.currency?.let { currency ->
            // Changing only the currency would silently reinterpret the amount —
            // 12000 KZT becoming 12000 USD is a ~480x rewrite with no conversion.
            val normalized = normalizeCurrency(currency)
            if (normalized != bill.currency && request.amount == null) {
                throw invalidField("amount", "is required when changing the currency")
            }
            bill.currency = normalized
        }
        request.amount?.let { bill.amount = requirePositiveAmount(it) }
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

    private fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Bill {
        val bill =
            billRepository.findById(id).orElseThrow { NotFoundException("Bill $id was not found") }
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

private fun parseMonthField(
    field: String,
    value: String,
): YearMonth =
    try {
        YearMonth.parse(value)
    } catch (ex: java.time.format.DateTimeParseException) {
        throw invalidField(field, "must be in yyyy-MM format, e.g. 2026-09")
    }

private fun requirePositiveAmount(amount: BigDecimal?): BigDecimal {
    val value = amount ?: throw invalidField("amount", "is required")
    if (value.signum() <= 0) throw invalidField("amount", "must be greater than zero")
    return value
}
