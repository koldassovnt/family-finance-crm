package com.familyfinance.crm.service

import com.familyfinance.crm.bill
import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.dto.CreateBillBatchRequest
import com.familyfinance.crm.dto.CreateBillRequest
import com.familyfinance.crm.dto.UpdateBillRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.BillRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillServiceImplTest {
    private val billRepository = mockk<BillRepository>()
    private val today = LocalDate.of(2026, 9, 10)
    private val service = BillServiceImpl(billRepository, fixedClock(today))
    private val owner = user()

    init {
        every { billRepository.save(any<Bill>()) } answers { firstArg<Bill>().withId() }
        every { billRepository.saveAll(any<List<Bill>>()) } answers {
            firstArg<List<Bill>>().map { it.withId() }
        }
    }

    @Test
    fun `an unpaid bill past its due date is overdue`() {
        val subject = bill(owner, dueDate = today.minusDays(1))
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns listOf(subject)

        assertTrue(service.list(owner, month = null).single().overdue)
    }

    @Test
    fun `a paid bill past its due date is not overdue`() {
        val subject = bill(owner, dueDate = today.minusDays(1), isPaid = true)
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns listOf(subject)

        assertFalse(service.list(owner, month = null).single().overdue)
    }

    @Test
    fun `a bill due today is not yet overdue`() {
        val subject = bill(owner, dueDate = today)
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns listOf(subject)

        assertFalse(service.list(owner, month = null).single().overdue)
    }

    @Test
    fun `accepts a due date in the past, which simply reads as overdue`() {
        val created =
            service.create(
                owner,
                CreateBillRequest(name = "Electricity", amount = BigDecimal("12000"), dueDate = today.minusMonths(1)),
            )

        assertTrue(created.overdue)
        assertNull(created.bill.batchId)
    }

    @Test
    fun `rejects a zero amount`() {
        assertThrows<ValidationException> {
            service.create(owner, CreateBillRequest(name = "Free", amount = BigDecimal.ZERO, dueDate = today))
        }
    }

    @Test
    fun `expands a batch into one bill per month, inclusive of both ends`() {
        val created = service.createBatch(owner, batchRequest(dayOfMonth = 15, start = "2026-09", end = "2026-12"))

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 12, 15),
            ),
            created.map { it.bill.dueDate },
        )
    }

    @Test
    fun `clamps a day past the end of a short month to its last day`() {
        val created = service.createBatch(owner, batchRequest(dayOfMonth = 31, start = "2026-01", end = "2026-04"))

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
            ),
            created.map { it.bill.dueDate },
        )
    }

    @Test
    fun `every row in a batch shares one batch id`() {
        val created = service.createBatch(owner, batchRequest(start = "2026-09", end = "2026-11"))

        assertEquals(1, created.mapNotNull { it.bill.batchId }.distinct().size)
    }

    @Test
    fun `a single-month batch creates exactly one bill`() {
        val created = service.createBatch(owner, batchRequest(start = "2026-09", end = "2026-09"))

        assertEquals(1, created.size)
    }

    @Test
    fun `rejects an inverted month range`() {
        assertThrows<ValidationException> {
            service.createBatch(owner, batchRequest(start = "2026-12", end = "2026-09"))
        }
    }

    @Test
    fun `rejects a batch larger than the cap, so a typo cannot generate thousands`() {
        val error =
            assertThrows<ValidationException> {
                service.createBatch(owner, batchRequest(start = "2026-01", end = "2040-01"))
            }

        assertEquals(setOf("endMonth"), error.fieldErrors.keys)
    }

    @Test
    fun `rejects a month that is not yyyy-MM`() {
        assertThrows<ValidationException> {
            service.createBatch(owner, batchRequest(start = "September 2026", end = "2026-12"))
        }
    }

    @Test
    fun `editing one row keeps its batch id and leaves siblings alone`() {
        val batchId = UUID.randomUUID()
        val first = bill(owner, dueDate = LocalDate.of(2026, 9, 15), batchId = batchId)
        val sibling = bill(owner, dueDate = LocalDate.of(2026, 10, 15), batchId = batchId)
        every { billRepository.findById(first.idValue) } returns Optional.of(first)

        val updated =
            service.update(first.idValue, owner, UpdateBillRequest(amount = BigDecimal("99000"), isPaid = true))

        assertEquals(BigDecimal("99000"), updated.bill.amount)
        assertEquals(batchId, updated.bill.batchId)
        assertTrue(updated.bill.isPaid)
        assertEquals(BigDecimal("12000"), sibling.amount)
        assertFalse(sibling.isPaid)
    }

    @Test
    fun `deleting a batch soft-deletes every row in it`() {
        val batchId = UUID.randomUUID()
        val rows = listOf(bill(owner, batchId = batchId), bill(owner, batchId = batchId))
        every { billRepository.findAllByOwnerAndBatchId(owner, batchId) } returns rows

        service.softDeleteBatch(batchId, owner)

        assertTrue(rows.all { it.isDeleted })
    }

    @Test
    fun `returns 404 for a batch id with no rows`() {
        val batchId = UUID.randomUUID()
        every { billRepository.findAllByOwnerAndBatchId(owner, batchId) } returns emptyList()

        assertThrows<NotFoundException> { service.softDeleteBatch(batchId, owner) }
    }

    @Test
    fun `returns 404 for a bill owned by someone else`() {
        val theirs = bill(user(email = "other@example.com"))
        every { billRepository.findById(theirs.idValue) } returns Optional.of(theirs)

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, owner, UpdateBillRequest(isPaid = true))
        }
    }

    @Test
    fun `soft deletes rather than removing the row`() {
        val subject = bill(owner)
        every { billRepository.findById(subject.idValue) } returns Optional.of(subject)

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }

    private fun batchRequest(
        dayOfMonth: Int = 15,
        start: String = "2026-09",
        end: String = "2026-12",
    ) = CreateBillBatchRequest(
        name = "Loan payment",
        amount = BigDecimal("250000"),
        dayOfMonth = dayOfMonth,
        startMonth = start,
        endMonth = end,
    )
}
