package com.familyfinance.crm.service

import com.familyfinance.crm.bill
import com.familyfinance.crm.domain.Bill
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.dto.CreateBillBatchRequest
import com.familyfinance.crm.dto.CreateBillRequest
import com.familyfinance.crm.dto.UpdateBillRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.BillRepository
import com.familyfinance.crm.repository.ShareRepository
import com.familyfinance.crm.resources
import com.familyfinance.crm.share
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillServiceImplTest {
    private val billRepository = mockk<BillRepository>()
    private val today = LocalDate.of(2026, 9, 10)
    private val shareRepository = mockk<ShareRepository>()
    private val shareAccess = ShareAccessServiceImpl(shareRepository)
    private val service = BillServiceImpl(billRepository, shareAccess, fixedClock(today))
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

        assertTrue(
            service
                .list(owner, month = null, unpaid = null)
                .resources
                .single()
                .overdue,
        )
    }

    @Test
    fun `a paid bill past its due date is not overdue`() {
        val subject = bill(owner, dueDate = today.minusDays(1), isPaid = true)
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns listOf(subject)

        assertFalse(
            service
                .list(owner, month = null, unpaid = null)
                .resources
                .single()
                .overdue,
        )
    }

    @Test
    fun `a bill due today is not yet overdue`() {
        val subject = bill(owner, dueDate = today)
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns listOf(subject)

        assertFalse(
            service
                .list(owner, month = null, unpaid = null)
                .resources
                .single()
                .overdue,
        )
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
    fun `unpaid true returns outstanding bills regardless of month`() {
        val overdue = bill(owner, name = "Electricity", dueDate = LocalDate.of(2026, 8, 25))
        val upcoming = bill(owner, name = "Netflix", dueDate = LocalDate.of(2026, 9, 28))
        val settled = bill(owner, name = "Water", dueDate = LocalDate.of(2026, 9, 5), isPaid = true)
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns
            listOf(overdue, settled, upcoming)

        val listed = service.list(owner, month = null, unpaid = true).resources

        // The August bill is exactly what a September month view cannot show.
        assertEquals(listOf("Electricity", "Netflix"), listed.map { it.bill.name })
        assertTrue(listed.first().overdue)
    }

    @Test
    fun `unpaid false returns settled bills`() {
        val paid = bill(owner, isPaid = true)
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns listOf(paid, bill(owner))

        assertEquals(1, service.list(owner, month = null, unpaid = false).size)
    }

    @Test
    fun `month and unpaid combine into one narrower filter`() {
        val subject = bill(owner, dueDate = LocalDate.of(2026, 9, 15))
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns
            listOf(
                subject,
                bill(owner, dueDate = LocalDate.of(2026, 9, 16), isPaid = true),
                bill(owner, dueDate = LocalDate.of(2026, 10, 1)),
            )

        val listed = service.list(owner, month = YearMonth.of(2026, 9), unpaid = true)

        assertEquals(1, listed.size)
    }

    @Test
    fun `month alone does not filter on paid status`() {
        val paid = bill(owner, dueDate = LocalDate.of(2026, 9, 15), isPaid = true)
        val unpaid = bill(owner, dueDate = LocalDate.of(2026, 9, 20))
        val october = bill(owner, dueDate = LocalDate.of(2026, 10, 1))
        every { billRepository.findAllByOwnerOrderByDueDateAscNameAsc(owner) } returns listOf(paid, unpaid, october)

        assertEquals(2, service.list(owner, month = YearMonth.of(2026, 9), unpaid = null).size)
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
        every { billRepository.findDetailedById(first.idValue) } returns first

        val updated =
            service.update(first.idValue, owner, UpdateBillRequest(amount = BigDecimal("99000"), isPaid = true))

        assertEquals(BigDecimal("99000"), updated.bill.amount)
        assertEquals(batchId, updated.bill.batchId)
        assertTrue(updated.bill.isPaid)
        assertEquals(BigDecimal("12000"), sibling.amount)
        assertFalse(sibling.isPaid)
    }

    @Test
    fun `refuses to change a bill's currency without restating the amount`() {
        val subject = bill(owner, amount = "12000")
        every { billRepository.findDetailedById(subject.idValue) } returns subject

        // 12000 KZT silently becoming 12000 USD is a ~480x rewrite.
        val error =
            assertThrows<ValidationException> {
                service.update(subject.idValue, owner, UpdateBillRequest(currency = "USD"))
            }

        assertEquals(setOf("amount"), error.fieldErrors.keys)
        assertEquals("KZT", subject.currency)
    }

    @Test
    fun `allows a currency change when the amount is restated with it`() {
        val subject = bill(owner, amount = "12000")
        every { billRepository.findDetailedById(subject.idValue) } returns subject

        val updated =
            service.update(
                subject.idValue,
                owner,
                UpdateBillRequest(currency = "USD", amount = BigDecimal("25")),
            )

        assertEquals("USD", updated.bill.currency)
        assertEquals(BigDecimal("25"), updated.bill.amount)
    }

    @Test
    fun `restating the same currency alone is not a change`() {
        val subject = bill(owner, amount = "12000")
        every { billRepository.findDetailedById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateBillRequest(currency = "kzt"))

        assertEquals("KZT", updated.bill.currency)
    }

    @Test
    fun `rejects a blank name on update`() {
        val subject = bill(owner)
        every { billRepository.findDetailedById(subject.idValue) } returns subject

        // @NotBlank cannot guard an optional PATCH field, so the service must.
        assertThrows<ValidationException> {
            service.update(subject.idValue, owner, UpdateBillRequest(name = "   "))
        }
        assertEquals("Electricity", subject.name)
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
        every { billRepository.findDetailedById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, owner, UpdateBillRequest(isPaid = true))
        }
    }

    @Test
    fun `soft deletes rather than removing the row`() {
        val subject = bill(owner)
        every { billRepository.findDetailedById(subject.idValue) } returns subject

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }

    // Phase 8 — sharing. A bug in any of these is a disclosure, not a wrong number.

    private val viewer = user(email = "viewer@example.com")

    @Test
    fun `a viewer sees a bill shared with them, badged with its owner`() {
        val theirs = bill(owner, name = "Water", dueDate = today)
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.BILL) } returns
            listOf(share(owner, viewer, ShareResourceType.BILL, theirs.idValue))
        every { billRepository.findAllDetailedByIds(setOf(theirs.idValue)) } returns listOf(theirs)

        val listed = service.list(viewer, month = null, unpaid = null, scope = ShareScope.SHARED)

        assertEquals(listOf("Water"), listed.map { it.resource.bill.name })
        assertEquals(listOf(owner), listed.map { it.sharedBy })
    }

    @Test
    fun `the month filter applies to shared bills exactly as to your own`() {
        val september = bill(owner, name = "Water", dueDate = LocalDate.of(2026, 9, 15))
        val october = bill(owner, name = "Gas", dueDate = LocalDate.of(2026, 10, 15))
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.BILL) } returns
            listOf(
                share(owner, viewer, ShareResourceType.BILL, september.idValue),
                share(owner, viewer, ShareResourceType.BILL, october.idValue),
            )
        every { billRepository.findAllDetailedByIds(any()) } returns listOf(september, october)

        val listed =
            service.list(viewer, month = YearMonth.of(2026, 9), unpaid = null, scope = ShareScope.SHARED)

        assertEquals(listOf("Water"), listed.map { it.resource.bill.name })
    }

    @Test
    fun `the unpaid filter applies to shared bills exactly as to your own`() {
        val paid = bill(owner, name = "Water", isPaid = true)
        val unpaid = bill(owner, name = "Gas")
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.BILL) } returns
            listOf(
                share(owner, viewer, ShareResourceType.BILL, paid.idValue),
                share(owner, viewer, ShareResourceType.BILL, unpaid.idValue),
            )
        every { billRepository.findAllDetailedByIds(any()) } returns listOf(paid, unpaid)

        val listed = service.list(viewer, month = null, unpaid = true, scope = ShareScope.SHARED)

        assertEquals(listOf("Gas"), listed.map { it.resource.bill.name })
    }

    @Test
    fun `a viewer cannot mark a bill shared with them as paid`() {
        val theirs = bill(owner)
        every { billRepository.findDetailedById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, viewer, UpdateBillRequest(isPaid = true))
        }
        assertFalse(theirs.isPaid)
    }

    @Test
    fun `a viewer cannot delete a bill shared with them`() {
        val theirs = bill(owner)
        every { billRepository.findDetailedById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> { service.softDelete(theirs.idValue, viewer) }
        assertFalse(theirs.isDeleted)
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
