package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.Goal
import com.familyfinance.crm.domain.GoalStatus
import com.familyfinance.crm.domain.GoalType
import com.familyfinance.crm.dto.CreateGoalRequest
import com.familyfinance.crm.dto.UpdateGoalRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.goal
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.GoalRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalServiceImplTest {
    private val goalRepository = mockk<GoalRepository>()
    private val accountService = mockk<AccountService>()
    private val service = GoalServiceImpl(goalRepository, accountService)

    private val owner = user()

    init {
        every { goalRepository.save(any<Goal>()) } answers { firstArg<Goal>().withId() }
    }

    @Test
    fun `computes progress from the linked account balance`() {
        val savings = account(owner, balance = "250000")
        every { accountService.getOwnedBy(savings.idValue, owner) } returns savings

        val created = service.create(owner, request(savings, targetAmount = "1000000"))

        assertEquals(BigDecimal("25.00"), created.progressPercent)
        assertFalse(created.achieved)
        assertEquals(GoalStatus.ACTIVE, created.goal.status)
    }

    @Test
    fun `marks a goal achieved once the balance reaches the target`() {
        val savings = account(owner, balance = "1000000")
        every { accountService.getOwnedBy(savings.idValue, owner) } returns savings

        val created = service.create(owner, request(savings, targetAmount = "1000000"))

        assertTrue(created.achieved)
        assertEquals(BigDecimal("100.00"), created.progressPercent)
    }

    @Test
    fun `clamps progress at 100 when the balance overshoots the target`() {
        val savings = account(owner, balance = "3000000")
        every { accountService.getOwnedBy(savings.idValue, owner) } returns savings

        val created = service.create(owner, request(savings, targetAmount = "1000000"))

        assertEquals(BigDecimal("100.00"), created.progressPercent)
        assertTrue(created.achieved)
    }

    @Test
    fun `clamps progress at zero for a negative balance`() {
        val savings = account(owner, balance = "-5000")
        every { accountService.getOwnedBy(savings.idValue, owner) } returns savings

        val created = service.create(owner, request(savings))

        assertEquals(BigDecimal("0.00"), created.progressPercent)
        assertFalse(created.achieved)
    }

    @Test
    fun `an achieved goal can still be abandoned`() {
        val savings = account(owner, balance = "1000000")
        val subject = goal(owner, savings, targetAmount = "1000000")
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateGoalRequest(status = GoalStatus.ABANDONED))

        assertEquals(GoalStatus.ABANDONED, updated.goal.status)
        assertTrue(updated.achieved)
    }

    @Test
    fun `rejects a linked account owned by someone else`() {
        val theirs = account(user(email = "other@example.com"))
        every { accountService.getOwnedBy(theirs.idValue, owner) } throws NotFoundException("nope")

        assertThrows<NotFoundException> { service.create(owner, request(theirs)) }
    }

    @Test
    fun `rejects a zero target amount`() {
        val savings = account(owner)
        every { accountService.getOwnedBy(savings.idValue, owner) } returns savings

        assertThrows<ValidationException> { service.create(owner, request(savings, targetAmount = "0")) }
    }

    @Test
    fun `clears the target date when it is explicitly null`() {
        val savings = account(owner)
        val subject = goal(owner, savings)
        subject.targetDate = LocalDate.of(2027, 1, 1)
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateGoalRequest(targetDate = Optional.empty()))

        assertNull(updated.goal.targetDate)
    }

    @Test
    fun `leaves the target date alone when it is absent`() {
        val savings = account(owner)
        val subject = goal(owner, savings)
        subject.targetDate = LocalDate.of(2027, 1, 1)
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateGoalRequest(name = "Renamed"))

        assertEquals(LocalDate.of(2027, 1, 1), updated.goal.targetDate)
        assertEquals("Renamed", updated.goal.name)
    }

    @Test
    fun `returns 404 for a goal owned by someone else`() {
        val theirs = goal(user(email = "other@example.com"), account(owner))
        every { goalRepository.findDetailedById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, owner, UpdateGoalRequest(name = "Mine now"))
        }
    }

    @Test
    fun `soft deletes rather than removing the row`() {
        val subject = goal(owner, account(owner))
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }

    private fun request(
        linkedAccount: Account,
        targetAmount: String = "1000000",
    ) = CreateGoalRequest(
        name = "Emergency fund",
        type = GoalType.EMERGENCY_FUND,
        targetAmount = BigDecimal(targetAmount),
        linkedAccountId = linkedAccount.idValue,
    )
}
