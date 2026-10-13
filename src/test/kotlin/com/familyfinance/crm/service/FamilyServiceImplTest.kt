package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.category
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.FamilyCategoryTotal
import com.familyfinance.crm.dto.FamilyPurchaseResponse
import com.familyfinance.crm.dto.UserRef
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.reflect.full.memberProperties
import kotlin.test.assertEquals

class FamilyServiceImplTest {
    private val transactionRepository = mockk<TransactionRepository>()
    private val service = FamilyServiceImpl(transactionRepository)

    private val husband = user(email = "husband@example.com")
    private val wife = user(email = "wife@example.com")
    private val from = LocalDate.of(2026, 9, 1)
    private val to = LocalDate.of(2026, 9, 30)

    @Test
    fun `returns the family purchases of every member, whoever asks`() {
        val his = purchase(husband, note = "his")
        val hers = purchase(wife, note = "hers")
        every { transactionRepository.findFamilyPurchases(from, to) } returns listOf(his, hers)

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(listOf("his", "hers"), response.purchases.map { it.note })
    }

    @Test
    fun `reads only what the family query returns, so nothing personal can appear`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns emptyList()

        service.purchases(reader = wife, from = from, to = to)

        // The mock is strict: any other repository read would have failed above.
        verify(exactly = 1) { transactionRepository.findFamilyPurchases(from, to) }
    }

    @Test
    fun `a purchase says who paid and whether it is the reader's own`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns listOf(purchase(husband), purchase(wife))

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(
            listOf(husband.idValue to false, wife.idValue to true),
            response.purchases.map { it.paidBy.id to it.isMine },
        )
    }

    @Test
    fun `a purchase shows its amount, date, category name and note`() {
        val purchase =
            purchase(
                husband,
                amount = "12.50",
                amountKzt = "6000",
                currency = "USD",
                category = category(husband),
                note = "Lunch",
                occurredOn = LocalDate.of(2026, 9, 5),
            )
        every { transactionRepository.findFamilyPurchases(from, to) } returns listOf(purchase)

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(
            FamilyPurchaseResponse(
                id = purchase.idValue,
                amount = BigDecimal("12.50"),
                currency = "USD",
                amountKzt = BigDecimal("6000"),
                occurredOn = LocalDate.of(2026, 9, 5),
                categoryName = "Groceries",
                note = "Lunch",
                paidBy = UserRef(id = husband.idValue, displayName = "Owner"),
                isMine = false,
            ),
            response.purchases.single(),
        )
    }

    @Test
    fun `the response has no field that could name the account that paid`() {
        // Guards the type itself: adding an account, rate or topic field to it
        // would publish something the payer did not agree to show.
        assertEquals(
            setOf("id", "amount", "currency", "amountKzt", "occurredOn", "categoryName", "note", "paidBy", "isMine"),
            FamilyPurchaseResponse::class.memberProperties.map { it.name }.toSet(),
        )
    }

    @Test
    fun `the total sums every member's purchases in KZT`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns
            listOf(purchase(husband, amountKzt = "1000"), purchase(wife, amountKzt = "2500.50"))

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(BigDecimal("3500.50"), response.total)
    }

    @Test
    fun `the total is in KZT even when a purchase was paid in another currency`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns
            listOf(purchase(husband, amount = "10", currency = "USD", amountKzt = "4800"))

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals("KZT" to BigDecimal("4800"), response.currency to response.total)
    }

    @Test
    fun `totals by member are listed largest first`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns
            listOf(
                purchase(husband, amountKzt = "1000"),
                purchase(wife, amountKzt = "2000"),
                purchase(husband, amountKzt = "500"),
            )

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(
            listOf(wife.idValue to BigDecimal("2000"), husband.idValue to BigDecimal("1500")),
            response.byMember.map { it.member.id to it.total },
        )
    }

    @Test
    fun `two members' categories of the same name are one line`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns
            listOf(
                purchase(husband, amountKzt = "1000", category = category(husband)),
                purchase(wife, amountKzt = "2000", category = category(wife)),
            )

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(listOf(FamilyCategoryTotal(categoryName = "Groceries", total = BigDecimal("3000"))), response.byCategory)
    }

    @Test
    fun `purchases without a category are totalled under no name`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns
            listOf(purchase(husband, amountKzt = "700"), purchase(wife, amountKzt = "300"))

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(listOf(FamilyCategoryTotal(categoryName = null, total = BigDecimal("1000"))), response.byCategory)
    }

    @Test
    fun `a window with no family purchases is an empty list and a zero total`() {
        every { transactionRepository.findFamilyPurchases(from, to) } returns emptyList()

        val response = service.purchases(reader = wife, from = from, to = to)

        assertEquals(emptyList<FamilyPurchaseResponse>() to BigDecimal.ZERO, response.purchases to response.total)
    }

    @Test
    fun `rejects a window longer than a year`() {
        assertThrows<ValidationException> {
            service.purchases(reader = wife, from = LocalDate.of(2025, 1, 1), to = LocalDate.of(2026, 9, 30))
        }
    }

    private fun purchase(
        payer: User,
        amount: String = "1500",
        amountKzt: String = amount,
        currency: String = "KZT",
        category: Category? = null,
        note: String? = null,
        occurredOn: LocalDate = LocalDate.of(2026, 9, 10),
        account: Account = account(payer, currency = currency),
    ) = Transaction(
        type = TransactionType.EXPENSE,
        amount = BigDecimal(amount),
        currency = currency,
        exchangeRate = BigDecimal.ONE,
        amountKzt = BigDecimal(amountKzt),
        toAmount = null,
        occurredOn = occurredOn,
        account = account,
        toAccount = null,
        category = category,
        note = note,
        isFamily = true,
    ).withId()
}
