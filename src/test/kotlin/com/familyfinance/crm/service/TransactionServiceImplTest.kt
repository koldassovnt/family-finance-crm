package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.category
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.dto.CreateTransactionRequest
import com.familyfinance.crm.dto.ReconcileRequest
import com.familyfinance.crm.dto.UpdateTransactionRequest
import com.familyfinance.crm.exception.CurrencyMismatchException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionServiceImplTest {
    private val transactionRepository = mockk<TransactionRepository>()
    private val accountService = mockk<AccountService>()
    private val categoryService = mockk<CategoryService>()
    private val today = LocalDate.of(2026, 9, 10)
    private val service =
        TransactionServiceImpl(
            transactionRepository = transactionRepository,
            accountService = accountService,
            categoryService = categoryService,
            clock = fixedClock(today),
        )

    private val owner = user()

    init {
        every { transactionRepository.save(any<Transaction>()) } answers { firstArg<Transaction>().withId() }
    }

    @Test
    fun `income increases the account balance`() {
        val account = account(owner, balance = "100")
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        service.create(owner, request(TransactionType.INCOME, "50", account.idValue))

        assertEquals(BigDecimal("150"), account.balance)
    }

    @Test
    fun `expense decreases the account balance below zero when needed`() {
        val account = account(owner, balance = "20")
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        service.create(owner, request(TransactionType.EXPENSE, "50", account.idValue))

        assertEquals(BigDecimal("-30"), account.balance)
    }

    @Test
    fun `same-currency transfer moves the amount between both accounts`() {
        val source = account(owner, balance = "100")
        val destination = account(owner, balance = "10")
        every { accountService.getOwnedBy(source.idValue, owner) } returns source
        every { accountService.getOwnedBy(destination.idValue, owner) } returns destination

        val transaction =
            service.create(
                owner,
                request(TransactionType.TRANSFER, "40", source.idValue, toAccountId = destination.id),
            )

        assertEquals(BigDecimal("60"), source.balance)
        assertEquals(BigDecimal("50"), destination.balance)
        assertNull(transaction.toAmount)
    }

    @Test
    fun `cross-currency transfer credits the destination its own amount`() {
        val source = account(owner, balance = "500", currency = "USD")
        val destination = account(owner, balance = "0", currency = "KZT")
        every { accountService.getOwnedBy(source.idValue, owner) } returns source
        every { accountService.getOwnedBy(destination.idValue, owner) } returns destination

        service.create(
            owner,
            request(
                TransactionType.TRANSFER,
                "100",
                source.idValue,
                toAccountId = destination.id,
                toAmount = BigDecimal("48000"),
            ),
        )

        assertEquals(BigDecimal("400"), source.balance)
        assertEquals(BigDecimal("48000"), destination.balance)
    }

    @Test
    fun `rejects a cross-currency transfer without toAmount`() {
        val source = account(owner, currency = "USD")
        val destination = account(owner, currency = "KZT")
        every { accountService.getOwnedBy(source.idValue, owner) } returns source
        every { accountService.getOwnedBy(destination.idValue, owner) } returns destination

        assertThrows<CurrencyMismatchException> {
            service.create(owner, request(TransactionType.TRANSFER, "100", source.idValue, toAccountId = destination.id))
        }
    }

    @Test
    fun `rejects a redundant toAmount on a same-currency transfer`() {
        val source = account(owner)
        val destination = account(owner)
        every { accountService.getOwnedBy(source.idValue, owner) } returns source
        every { accountService.getOwnedBy(destination.idValue, owner) } returns destination

        assertThrows<CurrencyMismatchException> {
            service.create(
                owner,
                request(
                    TransactionType.TRANSFER,
                    "100",
                    source.idValue,
                    toAccountId = destination.id,
                    toAmount = BigDecimal("100"),
                ),
            )
        }
    }

    @Test
    fun `rejects a future occurredOn`() {
        val account = account(owner)
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        val error =
            assertThrows<ValidationException> {
                service.create(
                    owner,
                    request(TransactionType.EXPENSE, "10", account.idValue, occurredOn = today.plusDays(1)),
                )
            }

        assertEquals(mapOf("occurredOn" to "must not be in the future"), error.fieldErrors)
    }

    @Test
    fun `rejects a zero amount`() {
        val account = account(owner)
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        assertThrows<ValidationException> {
            service.create(owner, request(TransactionType.EXPENSE, "0", account.idValue))
        }
    }

    @Test
    fun `rejects creating an ADJUSTMENT directly`() {
        val error =
            assertThrows<ValidationException> {
                service.create(owner, request(TransactionType.ADJUSTMENT, "10", UUID.randomUUID()))
            }

        assertTrue(error.message.contains("reconcile"))
    }

    @Test
    fun `rejects an income transaction categorised as an expense`() {
        val account = account(owner)
        val expenseCategory = category(owner, kind = CategoryKind.EXPENSE)
        every { accountService.getOwnedBy(account.idValue, owner) } returns account
        every { categoryService.getOwnedBy(expenseCategory.idValue, owner) } returns expenseCategory

        assertThrows<ValidationException> {
            service.create(
                owner,
                request(TransactionType.INCOME, "10", account.idValue, categoryId = expenseCategory.id),
            )
        }
    }

    @Test
    fun `deleting an expense restores the balance it removed`() {
        val account = account(owner, balance = "100")
        every { accountService.getOwnedBy(account.idValue, owner) } returns account
        val transaction = service.create(owner, request(TransactionType.EXPENSE, "30", account.idValue))
        every { transactionRepository.findDetailedById(transaction.idValue) } returns transaction

        service.softDelete(transaction.idValue, owner)

        assertEquals(BigDecimal("100"), account.balance)
        assertTrue(transaction.isDeleted)
    }

    @Test
    fun `deleting a cross-currency transfer reverses both sides`() {
        val source = account(owner, balance = "500", currency = "USD")
        val destination = account(owner, balance = "0", currency = "KZT")
        every { accountService.getOwnedBy(source.idValue, owner) } returns source
        every { accountService.getOwnedBy(destination.idValue, owner) } returns destination
        val transaction =
            service.create(
                owner,
                request(
                    TransactionType.TRANSFER,
                    "100",
                    source.idValue,
                    toAccountId = destination.id,
                    toAmount = BigDecimal("48000"),
                ),
            )
        every { transactionRepository.findDetailedById(transaction.idValue) } returns transaction

        service.softDelete(transaction.idValue, owner)

        assertEquals(BigDecimal("500"), source.balance)
        assertEquals(BigDecimal("0"), destination.balance)
    }

    @Test
    fun `editing the amount re-applies only the difference`() {
        val account = account(owner, balance = "100")
        every { accountService.getOwnedBy(account.idValue, owner) } returns account
        val transaction = service.create(owner, request(TransactionType.EXPENSE, "30", account.idValue))
        every { transactionRepository.findDetailedById(transaction.idValue) } returns transaction

        service.update(transaction.idValue, owner, UpdateTransactionRequest(amount = BigDecimal("50")))

        assertEquals(BigDecimal("50"), account.balance)
    }

    @Test
    fun `reconcile writes an ADJUSTMENT for the delta and lands on the actual balance`() {
        val account = account(owner, balance = "100")
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        val adjustment =
            service.reconcile(
                account.idValue,
                owner,
                ReconcileRequest(actualBalance = BigDecimal("80"), note = "bank fee"),
            )

        assertEquals(TransactionType.ADJUSTMENT, adjustment.type)
        assertEquals(BigDecimal("-20"), adjustment.amount)
        assertNull(adjustment.category)
        assertEquals(BigDecimal("80"), account.balance)
    }

    @Test
    fun `reconcile rejects a balance that already matches`() {
        val account = account(owner, balance = "100")
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        assertThrows<ValidationException> {
            service.reconcile(account.idValue, owner, ReconcileRequest(actualBalance = BigDecimal("100.00")))
        }
    }

    private fun request(
        type: TransactionType,
        amount: String,
        accountId: UUID,
        toAccountId: UUID? = null,
        toAmount: BigDecimal? = null,
        categoryId: UUID? = null,
        occurredOn: LocalDate? = null,
    ) = CreateTransactionRequest(
        type = type,
        amount = BigDecimal(amount),
        accountId = accountId,
        toAccountId = toAccountId,
        toAmount = toAmount,
        categoryId = categoryId,
        occurredOn = occurredOn,
    )
}
