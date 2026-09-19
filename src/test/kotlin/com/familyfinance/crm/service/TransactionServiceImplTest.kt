package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.category
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.dto.CreateTransactionRequest
import com.familyfinance.crm.dto.ReconcileRequest
import com.familyfinance.crm.dto.UpdateTransactionRequest
import com.familyfinance.crm.exception.CurrencyMismatchException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.topic
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionServiceImplTest {
    private val transactionRepository = mockk<TransactionRepository>()
    private val accountService = mockk<AccountService>()
    private val categoryService = mockk<CategoryService>()
    private val topicService = mockk<TopicService>()
    private val today = LocalDate.of(2026, 9, 10)
    private val service =
        TransactionServiceImpl(
            transactionRepository = transactionRepository,
            accountService = accountService,
            categoryService = categoryService,
            topicService = topicService,
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
                exchangeRate = BigDecimal("480"),
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
            service.create(
                owner,
                request(
                    TransactionType.TRANSFER,
                    "100",
                    source.idValue,
                    toAccountId = destination.id,
                    exchangeRate = BigDecimal("480"),
                ),
            )
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
    fun `converts a foreign expense to KZT at the supplied rate`() {
        val usd = account(owner, balance = "1000", currency = "USD")
        every { accountService.getOwnedBy(usd.idValue, owner) } returns usd

        val transaction =
            service.create(
                owner,
                request(TransactionType.EXPENSE, "15", usd.idValue, exchangeRate = BigDecimal("480")),
            )

        assertEquals(BigDecimal("15"), transaction.amount)
        assertEquals("USD", transaction.currency)
        assertEquals(BigDecimal("7200.0000"), transaction.amountKzt)
        assertEquals(BigDecimal("985"), usd.balance)
    }

    @Test
    fun `requires a rate for a foreign-currency account`() {
        val usd = account(owner, currency = "USD")
        every { accountService.getOwnedBy(usd.idValue, owner) } returns usd

        val error =
            assertThrows<ValidationException> {
                service.create(owner, request(TransactionType.EXPENSE, "15", usd.idValue))
            }

        assertEquals(setOf("exchangeRate"), error.fieldErrors.keys)
    }

    @Test
    fun `defaults the rate to 1 for a KZT account`() {
        val account = account(owner, balance = "100")
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        val transaction = service.create(owner, request(TransactionType.EXPENSE, "30", account.idValue))

        assertEquals(BigDecimal.ONE, transaction.exchangeRate)
        assertEquals(BigDecimal("30.0000"), transaction.amountKzt)
    }

    @Test
    fun `rejects a rate other than 1 on a KZT account`() {
        val account = account(owner)
        every { accountService.getOwnedBy(account.idValue, owner) } returns account

        assertThrows<ValidationException> {
            service.create(
                owner,
                request(TransactionType.EXPENSE, "30", account.idValue, exchangeRate = BigDecimal("480")),
            )
        }
    }

    @Test
    fun `correcting a mistyped rate re-derives the KZT figure`() {
        val usd = account(owner, balance = "1000", currency = "USD")
        every { accountService.getOwnedBy(usd.idValue, owner) } returns usd
        val transaction =
            service.create(
                owner,
                request(TransactionType.EXPENSE, "15", usd.idValue, exchangeRate = BigDecimal("48")),
            )
        every { transactionRepository.findDetailedById(transaction.idValue) } returns transaction

        val updated =
            service.update(
                transaction.idValue,
                owner,
                UpdateTransactionRequest(exchangeRate = BigDecimal("480")),
            )

        assertEquals(BigDecimal("7200.0000"), updated.amountKzt)
        // The account is in USD, so its balance never moved.
        assertEquals(BigDecimal("985"), usd.balance)
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
                    exchangeRate = BigDecimal("480"),
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

    @Test
    fun `editing both amounts of a cross-currency transfer moves each side by its own figure`() {
        val source = account(owner, balance = "1000", currency = "KZT")
        val destination = account(owner, balance = "0", currency = "USD")
        val transfer = crossCurrencyTransfer(source, destination, amount = "478", toAmount = "1")

        service.update(
            transfer.idValue,
            owner,
            UpdateTransactionRequest(amount = BigDecimal("956"), toAmount = BigDecimal("2")),
        )

        assertEquals(BigDecimal("44"), source.balance)
        assertEquals(BigDecimal("2"), destination.balance)
    }

    @Test
    fun `editing only the destination amount leaves the source balance alone`() {
        val source = account(owner, balance = "1000", currency = "KZT")
        val destination = account(owner, balance = "0", currency = "USD")
        val transfer = crossCurrencyTransfer(source, destination, amount = "478", toAmount = "1")

        service.update(transfer.idValue, owner, UpdateTransactionRequest(toAmount = BigDecimal("3")))

        assertEquals(BigDecimal("522"), source.balance)
        assertEquals(BigDecimal("3"), destination.balance)
    }

    @Test
    fun `rejects changing a cross-currency transfer's amount without a destination amount`() {
        val source = account(owner, balance = "1000", currency = "KZT")
        val destination = account(owner, balance = "0", currency = "USD")
        val transfer = crossCurrencyTransfer(source, destination, amount = "478", toAmount = "1")

        assertThrows<ValidationException> {
            service.update(transfer.idValue, owner, UpdateTransactionRequest(amount = BigDecimal("956")))
        }
    }

    @Test
    fun `rejects a destination amount on a transaction that is not a cross-currency transfer`() {
        val account = account(owner, balance = "100")
        val expense = transaction(TransactionType.EXPENSE, account, amount = "50")

        assertThrows<ValidationException> {
            service.update(expense.idValue, owner, UpdateTransactionRequest(toAmount = BigDecimal("10")))
        }
    }

    @Test
    fun `an expense may be attached to a topic on creation`() {
        val account = account(owner, balance = "100")
        val topic = topic(owner)
        every { accountService.getOwnedBy(account.idValue, owner) } returns account
        every { topicService.getOwnedBy(topic.idValue, owner) } returns topic

        val created =
            service.create(
                owner,
                request(TransactionType.EXPENSE, "50", account.idValue, topicId = topic.idValue),
            )

        assertEquals(topic, created.topic)
    }

    @Test
    fun `rejects attaching a transfer to a topic`() {
        val source = account(owner, balance = "100")
        val destination = account(owner)
        val topic = topic(owner)
        every { accountService.getOwnedBy(source.idValue, owner) } returns source
        every { accountService.getOwnedBy(destination.idValue, owner) } returns destination

        assertThrows<ValidationException> {
            service.create(
                owner,
                request(
                    type = TransactionType.TRANSFER,
                    amount = "50",
                    accountId = source.idValue,
                    toAccountId = destination.idValue,
                    topicId = topic.idValue,
                ),
            )
        }
    }

    @Test
    fun `editing a transaction's topic to null detaches it`() {
        val account = account(owner, balance = "100")
        val topic = topic(owner)
        val transaction = transaction(TransactionType.EXPENSE, account, amount = "30")
        transaction.topic = topic

        service.update(
            transaction.idValue,
            owner,
            UpdateTransactionRequest(topicId = Optional.empty()),
        )

        assertNull(transaction.topic)
    }

    @Test
    fun `list without filters queries the whole range with no account or category`() {
        every {
            transactionRepository.findForOwner(owner, today.minusMonths(1), today, null, null, null)
        } returns emptyList()

        service.list(owner = owner, from = today.minusMonths(1), to = today)

        verify { transactionRepository.findForOwner(owner, today.minusMonths(1), today, null, null, null) }
    }

    @Test
    fun `list passes an account filter through once it is confirmed to be the caller's`() {
        val account = account(owner)
        every { accountService.getOwnedBy(account.idValue, owner) } returns account
        every {
            transactionRepository.findForOwner(owner, today, today, account.idValue, null, null)
        } returns emptyList()

        service.list(owner = owner, from = today, to = today, accountId = account.idValue)

        verify { accountService.getOwnedBy(account.idValue, owner) }
    }

    @Test
    fun `list rejects an account that belongs to someone else`() {
        val strangersAccount = UUID.randomUUID()
        every { accountService.getOwnedBy(strangersAccount, owner) } throws NotFoundException("Account was not found")

        assertThrows<NotFoundException> {
            service.list(owner = owner, from = today, to = today, accountId = strangersAccount)
        }
    }

    @Test
    fun `list rejects a range longer than a year`() {
        assertThrows<ValidationException> {
            service.list(owner = owner, from = today.minusYears(1).minusDays(1), to = today)
        }
    }

    private fun crossCurrencyTransfer(
        source: Account,
        destination: Account,
        amount: String,
        toAmount: String,
    ): Transaction {
        every { accountService.getOwnedBy(source.idValue, owner) } returns source
        every { accountService.getOwnedBy(destination.idValue, owner) } returns destination
        return service
            .create(
                owner,
                request(
                    type = TransactionType.TRANSFER,
                    amount = amount,
                    accountId = source.idValue,
                    toAccountId = destination.idValue,
                    toAmount = BigDecimal(toAmount),
                ),
            ).also { every { transactionRepository.findDetailedById(it.idValue) } returns it }
    }

    private fun transaction(
        type: TransactionType,
        account: Account,
        amount: String,
    ): Transaction {
        every { accountService.getOwnedBy(account.idValue, owner) } returns account
        return service
            .create(owner, request(type, amount, account.idValue))
            .also { every { transactionRepository.findDetailedById(it.idValue) } returns it }
    }

    private fun request(
        type: TransactionType,
        amount: String,
        accountId: UUID,
        toAccountId: UUID? = null,
        toAmount: BigDecimal? = null,
        categoryId: UUID? = null,
        topicId: UUID? = null,
        occurredOn: LocalDate? = null,
        exchangeRate: BigDecimal? = null,
    ) = CreateTransactionRequest(
        type = type,
        amount = BigDecimal(amount),
        accountId = accountId,
        toAccountId = toAccountId,
        toAmount = toAmount,
        categoryId = categoryId,
        topicId = topicId,
        occurredOn = occurredOn,
        exchangeRate = exchangeRate,
    )
}
