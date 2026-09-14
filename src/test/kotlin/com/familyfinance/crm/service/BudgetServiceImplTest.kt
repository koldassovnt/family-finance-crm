package com.familyfinance.crm.service

import com.familyfinance.crm.budget
import com.familyfinance.crm.budgetVersion
import com.familyfinance.crm.category
import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.BudgetVersion
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.dto.CreateBudgetRequest
import com.familyfinance.crm.dto.UpdateBudgetRequest
import com.familyfinance.crm.exception.DuplicateBudgetException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.BudgetRepository
import com.familyfinance.crm.repository.BudgetVersionRepository
import com.familyfinance.crm.repository.CategoryTotal
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetServiceImplTest {
    private val budgetRepository = mockk<BudgetRepository>()
    private val budgetVersionRepository = mockk<BudgetVersionRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryService = mockk<CategoryService>()
    private val service =
        BudgetServiceImpl(
            budgetRepository = budgetRepository,
            budgetVersionRepository = budgetVersionRepository,
            transactionRepository = transactionRepository,
            categoryService = categoryService,
            clock = fixedClock(LocalDate.of(2026, 9, 10)),
        )

    private val owner = user()
    private val groceries = category(owner)
    private val september = YearMonth.of(2026, 9)

    init {
        every { budgetRepository.save(any<Budget>()) } answers { firstArg<Budget>().withId() }
        every {
            budgetVersionRepository.save(any<BudgetVersion>())
        } answers { firstArg<BudgetVersion>().withId() }
        every { budgetRepository.existsActiveForCategory(any(), any()) } returns false
        // Closing the old version must be flushed before the new one is inserted.
        every { budgetVersionRepository.flush() } returns Unit
        every { categoryService.getOwnedBy(groceries.idValue, owner) } returns groceries
        every { categoryService.descendantIndex(owner) } returns
            mapOf(groceries.idValue to setOf(groceries.idValue))
        every {
            transactionRepository.sumByTypeAndCategories(any(), any(), any(), any(), any())
        } returns BigDecimal.ZERO
    }

    @Test
    fun `computes usage against the current month in the app timezone`() {
        every {
            transactionRepository.sumByTypeAndCategories(
                owner,
                TransactionType.EXPENSE,
                setOf(groceries.idValue),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
            )
        } returns BigDecimal("20000")

        val created = service.create(owner, request())

        assertEquals("2026-09", created.month.toString())
        assertEquals(BigDecimal("20000"), created.spent)
        assertEquals(BigDecimal("30000"), created.remaining)
        assertEquals(BigDecimal("40.00"), created.percentUsed)
    }

    @Test
    fun `starts the first version in the current month`() {
        val created = service.create(owner, request())

        assertEquals(september.atDay(1), created.version.effectiveFromMonth)
        assertNull(created.version.effectiveToMonth)
    }

    @Test
    fun `reports overspend as a negative remainder and a percentage past 100`() {
        every {
            transactionRepository.sumByTypeAndCategories(any(), any(), any(), any(), any())
        } returns BigDecimal("60000")

        val created = service.create(owner, request())

        assertEquals(BigDecimal("-10000"), created.remaining)
        assertEquals(BigDecimal("120.00"), created.percentUsed)
    }

    @Test
    fun `rejects a budget on an INCOME category`() {
        val salary = category(owner, kind = CategoryKind.INCOME)
        every { categoryService.getOwnedBy(salary.idValue, owner) } returns salary

        val error =
            assertThrows<ValidationException> {
                service.create(owner, request().copy(categoryId = salary.idValue))
            }

        assertEquals(setOf("categoryId"), error.fieldErrors.keys)
    }

    @Test
    fun `rejects a second budget for the same category`() {
        every { budgetRepository.existsActiveForCategory(owner, groceries.idValue) } returns true

        assertThrows<DuplicateBudgetException> { service.create(owner, request()) }
    }

    @Test
    fun `rejects a zero limit`() {
        assertThrows<ValidationException> { service.create(owner, request(limitAmount = "0")) }
    }

    @Test
    fun `rolls sub-category spending up into the parent budget`() {
        val fruit = category(owner, parent = groceries)
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject)
        every { budgetVersionRepository.findInForce(owner, september.atDay(1)) } returns listOf(version)
        every { categoryService.descendantIndex(owner) } returns
            mapOf(
                groceries.idValue to setOf(groceries.idValue, fruit.idValue),
                fruit.idValue to setOf(fruit.idValue),
            )
        every {
            transactionRepository.sumByCategory(
                owner,
                TransactionType.EXPENSE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
            )
        } returns
            listOf(
                categoryTotal(groceries.idValue, BigDecimal("10000")),
                categoryTotal(fruit.idValue, BigDecimal("2500")),
            )

        val listed = service.list(owner, september)

        assertEquals(BigDecimal("12500"), listed.single().spent)
        assertEquals(BigDecimal("25.00"), listed.single().percentUsed)
    }

    @Test
    fun `editing a limit in the month it started corrects that version in place`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = september)
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        val updated =
            service.update(subject.idValue, owner, UpdateBudgetRequest(limitAmount = BigDecimal("70000")))

        assertEquals(version.idValue, updated.version.idValue)
        assertEquals(BigDecimal("70000"), updated.version.limitAmount)
        assertNull(updated.version.effectiveToMonth)
    }

    @Test
    fun `editing a limit from an earlier month opens a new version from this month`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = YearMonth.of(2026, 7))
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        val updated =
            service.update(subject.idValue, owner, UpdateBudgetRequest(limitAmount = BigDecimal("70000")))

        // The old version now ends in August; the new one starts in September.
        assertEquals(YearMonth.of(2026, 8).atDay(1), version.effectiveToMonth)
        assertEquals(september.atDay(1), updated.version.effectiveFromMonth)
        assertEquals(BigDecimal("70000"), updated.version.limitAmount)
        assertEquals(BigDecimal("50000"), version.limitAmount)
    }

    @Test
    fun `a threshold-only edit does not split the limit history`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = YearMonth.of(2026, 7))
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        val updated =
            service.update(
                subject.idValue,
                owner,
                UpdateBudgetRequest(alertThresholdPercent = Optional.of(90)),
            )

        // The limit never changed, so July's version stays open rather than
        // being closed and reopened with an identical limit.
        assertEquals(version.idValue, updated.version.idValue)
        assertEquals(YearMonth.of(2026, 7).atDay(1), updated.version.effectiveFromMonth)
        assertNull(updated.version.effectiveToMonth)
        assertEquals(90, updated.version.alertThresholdPercent)
    }

    @Test
    fun `an empty edit does not split the limit history`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = YearMonth.of(2026, 7))
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        val updated = service.update(subject.idValue, owner, UpdateBudgetRequest())

        assertEquals(version.idValue, updated.version.idValue)
        assertNull(updated.version.effectiveToMonth)
    }

    @Test
    fun `resending the same limit at a different scale is not a change`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = YearMonth.of(2026, 7))
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        // The database round-trips 50000 as 50000.0000, which is not `equals`.
        val updated =
            service.update(subject.idValue, owner, UpdateBudgetRequest(limitAmount = BigDecimal("50000.0000")))

        assertEquals(version.idValue, updated.version.idValue)
        assertNull(updated.version.effectiveToMonth)
    }

    @Test
    fun `rejects a month in the future, whose usage could only be zero`() {
        val error =
            assertThrows<ValidationException> { service.list(owner, YearMonth.of(2030, 1)) }

        assertEquals(setOf("month"), error.fieldErrors.keys)
    }

    @Test
    fun `a new version carries forward whatever the request left out`() {
        val subject = budget(owner, groceries)
        val version =
            budgetVersion(subject, effectiveFromMonth = YearMonth.of(2026, 7), alertThresholdPercent = 80)
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        val updated =
            service.update(subject.idValue, owner, UpdateBudgetRequest(limitAmount = BigDecimal("70000")))

        assertEquals(80, updated.version.alertThresholdPercent)
    }

    @Test
    fun `clears the alert threshold when it is explicitly null`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = september)
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        val updated =
            service.update(
                subject.idValue,
                owner,
                UpdateBudgetRequest(alertThresholdPercent = Optional.empty()),
            )

        assertNull(updated.version.alertThresholdPercent)
    }

    @Test
    fun `rejects an out-of-range alert threshold`() {
        val subject = budget(owner, groceries)
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns budgetVersion(subject)

        assertThrows<ValidationException> {
            service.update(
                subject.idValue,
                owner,
                UpdateBudgetRequest(alertThresholdPercent = Optional.of(101)),
            )
        }
    }

    @Test
    fun `deleting closes the open version at the end of last month`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = YearMonth.of(2026, 7))
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        service.softDelete(subject.idValue, owner)

        assertEquals(YearMonth.of(2026, 8).atDay(1), version.effectiveToMonth)
        assertTrue(subject.isDeleted)
    }

    @Test
    fun `deleting in the month it started drops the version rather than inverting its range`() {
        val subject = budget(owner, groceries)
        val version = budgetVersion(subject, effectiveFromMonth = september)
        every { budgetRepository.findActiveById(subject.idValue) } returns subject
        every { budgetVersionRepository.findOpenVersion(subject.idValue) } returns version

        service.softDelete(subject.idValue, owner)

        assertTrue(version.isDeleted)
        assertNull(version.effectiveToMonth)
        assertTrue(subject.isDeleted)
    }

    @Test
    fun `a month before the budget existed simply has no row`() {
        every { budgetVersionRepository.findInForce(owner, YearMonth.of(2026, 5).atDay(1)) } returns emptyList()

        assertTrue(service.list(owner, YearMonth.of(2026, 5)).isEmpty())
    }

    @Test
    fun `returns 404 for a budget owned by someone else`() {
        val theirs = budget(user(email = "other@example.com"), groceries)
        every { budgetRepository.findActiveById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, owner, UpdateBudgetRequest(limitAmount = BigDecimal("1")))
        }
    }

    private fun request(limitAmount: String = "50000") =
        CreateBudgetRequest(
            categoryId = groceries.idValue,
            limitAmount = BigDecimal(limitAmount),
            alertThresholdPercent = 80,
        )

    private fun categoryTotal(
        id: UUID,
        amount: BigDecimal,
    ) = object : CategoryTotal {
        override val categoryId = id
        override val categoryName = "Category"
        override val total = amount
    }
}
