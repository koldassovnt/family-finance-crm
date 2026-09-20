package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.AccessLevel
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.Goal
import com.familyfinance.crm.domain.GoalStatus
import com.familyfinance.crm.domain.GoalType
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.dto.CreateGoalRequest
import com.familyfinance.crm.dto.UpdateGoalRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.goal
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.GoalRepository
import com.familyfinance.crm.repository.ShareRepository
import com.familyfinance.crm.share
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
    private val shareRepository = mockk<ShareRepository>()
    private val shareAccess = ShareAccessServiceImpl(shareRepository)
    private val service = GoalServiceImpl(goalRepository, accountService, shareAccess)

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
    fun `an achieved goal can be archived and keeps reporting progress`() {
        val savings = account(owner, balance = "1000000")
        val subject = goal(owner, savings, targetAmount = "1000000")
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateGoalRequest(status = GoalStatus.ARCHIVED))

        assertEquals(GoalStatus.ARCHIVED, updated.goal.status)
        assertTrue(updated.achieved)
        assertEquals(BigDecimal("100.00"), updated.progressPercent)
    }

    @Test
    fun `refuses to reactivate a goal whose linked account has been deleted`() {
        val closed = account(owner)
        closed.isDeleted = true
        val subject = goal(owner, closed, status = GoalStatus.ARCHIVED)
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        // Archiving frees the account for deletion, so reactivating afterwards
        // would bind an ACTIVE goal to a balance nothing can move.
        val error =
            assertThrows<ValidationException> {
                service.update(subject.idValue, owner, UpdateGoalRequest(status = GoalStatus.ACTIVE))
            }

        assertEquals(setOf("status"), error.fieldErrors.keys)
        assertEquals(GoalStatus.ARCHIVED, subject.status)
    }

    @Test
    fun `still allows archiving a goal whose linked account has been deleted`() {
        val closed = account(owner)
        closed.isDeleted = true
        val subject = goal(owner, closed, status = GoalStatus.ABANDONED)
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateGoalRequest(status = GoalStatus.ARCHIVED))

        assertEquals(GoalStatus.ARCHIVED, updated.goal.status)
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
    fun `rejects a blank name on update`() {
        val subject = goal(owner, account(owner))
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        assertThrows<ValidationException> {
            service.update(subject.idValue, owner, UpdateGoalRequest(name = "   "))
        }
        assertEquals("Emergency fund", subject.name)
    }

    @Test
    fun `soft deletes rather than removing the row`() {
        val subject = goal(owner, account(owner))
        every { goalRepository.findDetailedById(subject.idValue) } returns subject

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }

    // Phase 8 — sharing. A bug in any of these is a disclosure, not a wrong number.

    private val viewer = user(email = "viewer@example.com")

    @Test
    fun `a shared goal discloses the linked account's balance, which is the whole point`() {
        val savings = account(owner, balance = "250000")
        val theirs = goal(owner, savings, targetAmount = "1000000")
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.GOAL) } returns
            listOf(share(owner, viewer, ShareResourceType.GOAL, theirs.idValue))
        every { goalRepository.findAllDetailedByIds(setOf(theirs.idValue)) } returns listOf(theirs)

        val listed = service.list(viewer, ShareScope.SHARED)

        // Progress *is* balance over target, so there is no version of this that
        // shows progress and withholds the balance — see phase-8-sharing.md.
        assertEquals(BigDecimal("25.00"), listed.single().resource.progressPercent)
        assertEquals(
            BigDecimal("250000"),
            listed
                .single()
                .resource.goal.linkedAccount.balance,
        )
        assertEquals(owner, listed.single().sharedBy)
    }

    @Test
    fun `scope OWN lists only your own goals, without consulting the share table`() {
        val mine = goal(viewer, account(viewer, balance = "100"))
        every { goalRepository.findAllByOwner(viewer) } returns listOf(mine)

        val listed = service.list(viewer, ShareScope.OWN)

        assertEquals(listOf(mine), listed.map { it.resource.goal })
        assertEquals(listOf(AccessLevel.OWNER), listed.map { it.accessLevel })
    }

    @Test
    fun `a viewer cannot retarget a goal shared with them`() {
        val theirs = goal(owner, account(owner))
        every { goalRepository.findDetailedById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, viewer, UpdateGoalRequest(targetAmount = BigDecimal("1")))
        }
    }

    @Test
    fun `a viewer cannot delete a goal shared with them`() {
        val theirs = goal(owner, account(owner))
        every { goalRepository.findDetailedById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> { service.softDelete(theirs.idValue, viewer) }
        assertTrue(!theirs.isDeleted)
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
