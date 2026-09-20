package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.AccessLevel
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.Bank
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.dto.CreateAccountRequest
import com.familyfinance.crm.dto.UpdateAccountRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.AccountRepository
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
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountServiceImplTest {
    private val accountRepository = mockk<AccountRepository>()
    private val bankService = mockk<BankService>()
    private val goalRepository = mockk<GoalRepository>()
    private val shareRepository = mockk<ShareRepository>()
    private val shareAccess = ShareAccessServiceImpl(shareRepository)
    private val service = AccountServiceImpl(accountRepository, bankService, goalRepository, shareAccess)
    private val owner = user()

    init {
        every { accountRepository.save(any<Account>()) } answers { firstArg<Account>().withId() }
        every { goalRepository.existsActiveForAccountId(any(), any()) } returns false
    }

    @Test
    fun `returns 404 for an account owned by someone else`() {
        val theirs = account(user(email = "other@example.com"))
        every { accountRepository.findActiveById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> { service.getOwnedBy(theirs.idValue, owner) }
    }

    @Test
    fun `stores the currency uppercased`() {
        val created =
            service.create(
                owner,
                CreateAccountRequest(name = "Wallet", type = AccountType.CASH, currency = "kzt"),
            )

        assertEquals("KZT", created.currency)
    }

    @Test
    fun `accepts a negative opening balance`() {
        val created =
            service.create(
                owner,
                CreateAccountRequest(name = "Wallet", type = AccountType.CASH, balance = BigDecimal("-500")),
            )

        assertEquals(BigDecimal("-500"), created.balance)
    }

    @Test
    fun `rejects a bank on a cash account`() {
        assertThrows<ValidationException> {
            service.create(
                owner,
                CreateAccountRequest(name = "Wallet", type = AccountType.CASH, bankId = UUID.randomUUID()),
            )
        }
    }

    @Test
    fun `clears the bank when bankId is explicitly null`() {
        val subject = account(owner)
        subject.bank = Bank(name = "Halyk").withId()
        every { accountRepository.findActiveById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateAccountRequest(bankId = Optional.empty()))

        assertNull(updated.bank)
    }

    @Test
    fun `leaves the bank alone when bankId is absent`() {
        val bank = Bank(name = "Halyk").withId()
        val subject = account(owner)
        subject.bank = bank
        every { accountRepository.findActiveById(subject.idValue) } returns subject

        val updated = service.update(subject.idValue, owner, UpdateAccountRequest(name = "Renamed"))

        assertEquals(bank, updated.bank)
        assertEquals("Renamed", updated.name)
    }

    @Test
    fun `blocks deleting an account that an ACTIVE goal points at`() {
        val subject = account(owner)
        every { accountRepository.findActiveById(subject.idValue) } returns subject
        every { goalRepository.existsActiveForAccountId(subject.idValue, any()) } returns true

        assertThrows<ConflictException> { service.softDelete(subject.idValue, owner) }
    }

    @Test
    fun `rejects a blank name on update`() {
        val subject = account(owner)
        every { accountRepository.findActiveById(subject.idValue) } returns subject

        assertThrows<ValidationException> {
            service.update(subject.idValue, owner, UpdateAccountRequest(name = "   "))
        }
        assertEquals("Main", subject.name)
    }

    @Test
    fun `soft deletes rather than removing the row`() {
        val subject = account(owner)
        every { accountRepository.findActiveById(subject.idValue) } returns subject

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }

    // Phase 8 — sharing. A bug in any of these is a disclosure, not a wrong number.

    private val viewer = user(email = "viewer@example.com")

    @Test
    fun `a viewer may read the one account shared with them`() {
        val theirs = account(owner)
        every { accountRepository.findActiveById(theirs.idValue) } returns theirs
        every { shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, theirs.idValue) } returns
            share(owner, viewer, ShareResourceType.ACCOUNT, theirs.idValue)

        val readable = service.getReadableBy(theirs.idValue, viewer)

        assertEquals(theirs, readable.resource)
        assertEquals(AccessLevel.VIEWER, readable.accessLevel)
        assertEquals(owner, readable.sharedBy)
    }

    @Test
    fun `a viewer of one account cannot read a second account of the same owner`() {
        val alsoTheirs = account(owner)
        every { accountRepository.findActiveById(alsoTheirs.idValue) } returns alsoTheirs
        every { shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, alsoTheirs.idValue) } returns null

        assertThrows<NotFoundException> { service.getReadableBy(alsoTheirs.idValue, viewer) }
    }

    @Test
    fun `a revoked share reads as not found straight afterwards`() {
        val theirs = account(owner)
        every { accountRepository.findActiveById(theirs.idValue) } returns theirs
        // Revoking soft-deletes the row, and Share is restricted, so the grant is
        // gone from this lookup rather than merely flagged.
        every { shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, theirs.idValue) } returns null

        assertThrows<NotFoundException> { service.getReadableBy(theirs.idValue, viewer) }
    }

    @Test
    fun `a viewer cannot rename an account shared with them`() {
        val theirs = account(owner)
        every { accountRepository.findActiveById(theirs.idValue) } returns theirs
        every { shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, theirs.idValue) } returns
            share(owner, viewer, ShareResourceType.ACCOUNT, theirs.idValue)

        // The write path checks ownership and never asks about shares at all.
        assertThrows<NotFoundException> {
            service.update(theirs.idValue, viewer, UpdateAccountRequest(name = "Mine now"))
        }
        assertEquals("Main", theirs.name)
    }

    @Test
    fun `a viewer cannot delete an account shared with them`() {
        val theirs = account(owner)
        every { accountRepository.findActiveById(theirs.idValue) } returns theirs
        every { shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, theirs.idValue) } returns
            share(owner, viewer, ShareResourceType.ACCOUNT, theirs.idValue)

        assertThrows<NotFoundException> { service.softDelete(theirs.idValue, viewer) }
        assertTrue(!theirs.isDeleted)
    }

    @Test
    fun `scope OWN lists only your own, without consulting the share table`() {
        val mine = account(owner)
        every { accountRepository.findAllActiveByOwner(owner) } returns listOf(mine)

        val listed = service.list(owner, ShareScope.OWN)

        assertEquals(listOf(mine), listed.map { it.resource })
        assertEquals(listOf(AccessLevel.OWNER), listed.map { it.accessLevel })
        // Unstubbed calls on a strict mock throw, so reaching the share table here
        // would fail this test — which is the point: OWN is the pre-Phase-8 answer.
    }

    @Test
    fun `scope SHARED lists only what others shared, badged with its owner`() {
        val theirs = account(owner)
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.ACCOUNT) } returns
            listOf(share(owner, viewer, ShareResourceType.ACCOUNT, theirs.idValue))
        every { accountRepository.findAllActiveByIds(setOf(theirs.idValue)) } returns listOf(theirs)

        val listed = service.list(viewer, ShareScope.SHARED)

        assertEquals(listOf(theirs), listed.map { it.resource })
        assertEquals(listOf(owner), listed.map { it.sharedBy })
    }

    @Test
    fun `scope ALL badges each row by how it was reached`() {
        val mine = account(viewer)
        val theirs = account(owner)
        every { accountRepository.findAllActiveByOwner(viewer) } returns listOf(mine)
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.ACCOUNT) } returns
            listOf(share(owner, viewer, ShareResourceType.ACCOUNT, theirs.idValue))
        every { accountRepository.findAllActiveByIds(setOf(theirs.idValue)) } returns listOf(theirs)

        val listed = service.list(viewer, ShareScope.ALL)

        assertEquals(listOf(mine, theirs), listed.map { it.resource })
        assertEquals(listOf(AccessLevel.OWNER, AccessLevel.VIEWER), listed.map { it.accessLevel })
        assertEquals(listOf(null, owner), listed.map { it.sharedBy })
    }
}
