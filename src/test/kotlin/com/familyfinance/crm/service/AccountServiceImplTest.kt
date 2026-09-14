package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.Bank
import com.familyfinance.crm.dto.CreateAccountRequest
import com.familyfinance.crm.dto.UpdateAccountRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.AccountRepository
import com.familyfinance.crm.repository.GoalRepository
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
    private val service = AccountServiceImpl(accountRepository, bankService, goalRepository)
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
    fun `soft deletes rather than removing the row`() {
        val subject = account(owner)
        every { accountRepository.findActiveById(subject.idValue) } returns subject

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }
}
