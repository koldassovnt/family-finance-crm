package com.familyfinance.crm.service

import com.familyfinance.crm.budget
import com.familyfinance.crm.category
import com.familyfinance.crm.domain.Budget
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.dto.CreateBudgetRequest
import com.familyfinance.crm.dto.UpdateBudgetRequest
import com.familyfinance.crm.exception.DuplicateBudgetException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.BudgetRepository
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
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetServiceImplTest {
    private val budgetRepository = mockk<BudgetRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryService = mockk<CategoryService>()
    private val service =
        BudgetServiceImpl(
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            categoryService = categoryService,
            clock = fixedClock(LocalDate.of(2026, 9, 10)),
        )

    private val owner = user()
    private val groceries = category(owner)

    init {
        every { budgetRepository.save(any<Budget>()) } answers { firstArg<Budget>().withId() }
        every { budgetRepository.existsForCategory(any(), any()) } returns false
        every { categoryService.getOwnedBy(groceries.idValue, owner) } returns groceries
        every {
            transactionRepository.sumByTypeAndCategory(any(), any(), any(), any(), any())
        } returns BigDecimal.ZERO
    }

    @Test
    fun `computes usage against the current month in the app timezone`() {
        every {
            transactionRepository.sumByTypeAndCategory(
                owner,
                TransactionType.EXPENSE,
                groceries.idValue,
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
    fun `reports overspend as a negative remainder and a percentage past 100`() {
        every {
            transactionRepository.sumByTypeAndCategory(any(), any(), any(), any(), any())
        } returns BigDecimal("60000")

        val created = service.create(owner, request())

        assertEquals(BigDecimal("-10000"), created.remaining)
        assertEquals(BigDecimal("120.00"), created.percentUsed)
    }

    @Test
    fun `rejects a second budget for the same category`() {
        every { budgetRepository.existsForCategory(owner, groceries.idValue) } returns true

        assertThrows<DuplicateBudgetException> { service.create(owner, request()) }
    }

    @Test
    fun `rejects a zero limit`() {
        assertThrows<ValidationException> { service.create(owner, request(limitAmount = "0")) }
    }

    @Test
    fun `rejects an out-of-range alert threshold on update`() {
        val subject = budget(owner, groceries)
        every { budgetRepository.findDetailedById(subject.idValue) } returns subject

        assertThrows<ValidationException> {
            service.update(subject.idValue, owner, UpdateBudgetRequest(alertThresholdPercent = Optional.of(101)))
        }
    }

    @Test
    fun `clears the alert threshold when it is explicitly null`() {
        val subject = budget(owner, groceries)
        every { budgetRepository.findDetailedById(subject.idValue) } returns subject

        val updated =
            service.update(subject.idValue, owner, UpdateBudgetRequest(alertThresholdPercent = Optional.empty()))

        assertNull(updated.budget.alertThresholdPercent)
    }

    @Test
    fun `leaves the alert threshold alone when it is absent`() {
        val subject = budget(owner, groceries, alertThresholdPercent = 80)
        every { budgetRepository.findDetailedById(subject.idValue) } returns subject

        val updated =
            service.update(subject.idValue, owner, UpdateBudgetRequest(limitAmount = BigDecimal("70000")))

        assertEquals(80, updated.budget.alertThresholdPercent)
        assertEquals(BigDecimal("70000"), updated.budget.limitAmount)
    }

    @Test
    fun `returns 404 for a budget owned by someone else`() {
        val theirs = budget(user(email = "other@example.com"), groceries)
        every { budgetRepository.findDetailedById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, owner, UpdateBudgetRequest(limitAmount = BigDecimal("1")))
        }
    }

    @Test
    fun `soft deletes rather than removing the row`() {
        val subject = budget(owner, groceries)
        every { budgetRepository.findDetailedById(subject.idValue) } returns subject

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }

    @Test
    fun `lists budgets from a single month-wide aggregation`() {
        val subject = budget(owner, groceries)
        every { budgetRepository.findAllByOwner(owner) } returns listOf(subject)
        every {
            transactionRepository.sumByCategory(
                owner,
                TransactionType.EXPENSE,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
            )
        } returns listOf(categoryTotal(groceries.idValue, BigDecimal("12500")))

        val listed = service.list(owner)

        assertEquals(BigDecimal("12500"), listed.single().spent)
        assertEquals(BigDecimal("25.00"), listed.single().percentUsed)
    }

    @Test
    fun `reports zero usage for a category with no spending this month`() {
        val subject = budget(owner, groceries)
        every { budgetRepository.findAllByOwner(owner) } returns listOf(subject)
        every { transactionRepository.sumByCategory(any(), any(), any(), any()) } returns emptyList()

        val listed = service.list(owner)

        assertEquals(BigDecimal.ZERO, listed.single().spent)
        assertEquals(BigDecimal("0.00"), listed.single().percentUsed)
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
        override val categoryName = "Groceries"
        override val total = amount
    }
}
