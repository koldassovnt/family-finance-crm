package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.TopicStatus
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.dto.CreateTopicRequest
import com.familyfinance.crm.dto.UpdateTopicRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.TopicRepository
import com.familyfinance.crm.repository.TopicTotals
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.topic
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
import kotlin.test.assertSame

class TopicServiceImplTest {
    private val topicRepository = mockk<TopicRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val service =
        TopicServiceImpl(
            topicRepository = topicRepository,
            transactionRepository = transactionRepository,
        )

    private val owner = user()
    private val account = account(owner)

    init {
        every { topicRepository.save(any<Topic>()) } answers { firstArg<Topic>().withId() }
        every { topicRepository.existsByName(any(), any(), any()) } returns false
        every { transactionRepository.sumByTopic(any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `a new topic starts ACTIVE with its name trimmed`() {
        val created = service.create(owner, CreateTopicRequest(name = "  Малайзия 2026 "))

        assertEquals("Малайзия 2026", created.topic.name)
        assertEquals(TopicStatus.ACTIVE, created.topic.status)
    }

    @Test
    fun `rejects a second topic with the same name`() {
        every { topicRepository.existsByName(owner, "Малайзия", null) } returns true

        assertThrows<ConflictException> {
            service.create(owner, CreateTopicRequest(name = "Малайзия"))
        }
    }

    @Test
    fun `rejects an end date before the start date`() {
        assertThrows<ValidationException> {
            service.create(
                owner,
                CreateTopicRequest(
                    name = "Trip",
                    startDate = LocalDate.of(2026, 9, 14),
                    endDate = LocalDate.of(2026, 9, 1),
                ),
            )
        }
    }

    @Test
    fun `an explicit null clears the end date`() {
        val topic = topic(owner)
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)

        service.update(topic.idValue, owner, UpdateTopicRequest(endDate = Optional.empty()))

        assertNull(topic.endDate)
    }

    @Test
    fun `remaining goes negative once past the planned amount`() {
        val topic = topic(owner, plannedAmount = "500000")
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)
        every { transactionRepository.sumByTopic(owner, any(), any()) } returns
            listOf(totals(topic.idValue, spent = "620000", received = "20000"))
        every { transactionRepository.sumByTopicAndCategory(topic, any()) } returns emptyList()

        val detail = service.get(topic.idValue, owner)

        assertEquals(BigDecimal("600000"), detail.totals.net)
        assertEquals(BigDecimal("-100000"), detail.totals.remaining)
    }

    @Test
    fun `remaining is null when nothing was planned`() {
        val topic = topic(owner)
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)
        every { transactionRepository.sumByTopicAndCategory(topic, any()) } returns emptyList()

        assertNull(service.get(topic.idValue, owner).totals.remaining)
    }

    @Test
    fun `candidates require the topic to have both dates`() {
        val topic = topic(owner, endDate = null)
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)

        assertThrows<ValidationException> { service.candidates(topic.idValue, owner) }
    }

    @Test
    fun `attaching sets the topic on every requested transaction`() {
        val topic = topic(owner)
        val first = transaction(TransactionType.EXPENSE)
        val second = transaction(TransactionType.INCOME)
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)
        every { transactionRepository.findAllDetailedByIds(any()) } returns listOf(first, second)

        service.attach(topic.idValue, owner, listOf(first.idValue, second.idValue))

        assertSame(topic, first.topic)
        assertSame(topic, second.topic)
    }

    @Test
    fun `attaching rejects the whole call when one transaction is a transfer`() {
        val topic = topic(owner)
        val expense = transaction(TransactionType.EXPENSE)
        val transfer = transaction(TransactionType.TRANSFER)
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)
        every { transactionRepository.findAllDetailedByIds(any()) } returns listOf(expense, transfer)

        assertThrows<ValidationException> {
            service.attach(topic.idValue, owner, listOf(expense.idValue, transfer.idValue))
        }

        assertNull(expense.topic)
    }

    @Test
    fun `attaching a transaction belonging to someone else is not found`() {
        val topic = topic(owner)
        val strangers = transaction(TransactionType.EXPENSE, owner = user(email = "other@example.com"))
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)
        every { transactionRepository.findAllDetailedByIds(any()) } returns listOf(strangers)

        assertThrows<NotFoundException> {
            service.attach(topic.idValue, owner, listOf(strangers.idValue))
        }
    }

    @Test
    fun `detaching a transaction attached to a different topic is not found`() {
        val topic = topic(owner)
        val other = topic(owner, name = "Renovation")
        val transaction = transaction(TransactionType.EXPENSE).apply { this.topic = other }
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)
        every { transactionRepository.findDetailedById(transaction.idValue) } returns transaction

        assertThrows<NotFoundException> {
            service.detach(topic.idValue, owner, transaction.idValue)
        }
    }

    @Test
    fun `a deleted topic reads as not found`() {
        val topic = topic(owner).apply { isDeleted = true }
        every { topicRepository.findById(topic.idValue) } returns Optional.of(topic)

        assertThrows<NotFoundException> { service.getOwnedBy(topic.idValue, owner) }
    }

    private fun transaction(
        type: TransactionType,
        owner: com.familyfinance.crm.domain.User = this.owner,
    ): Transaction =
        Transaction(
            type = type,
            amount = BigDecimal("100"),
            currency = "KZT",
            exchangeRate = BigDecimal.ONE,
            amountKzt = BigDecimal("100"),
            toAmount = null,
            occurredOn = LocalDate.of(2026, 9, 5),
            account = if (owner == this.owner) account else account(owner),
            toAccount = null,
            category = null,
            note = null,
        ).withId()

    private fun totals(
        topicId: UUID,
        spent: String,
        received: String,
    ): TopicTotals =
        object : TopicTotals {
            override val topicId = topicId
            override val spent = BigDecimal(spent)
            override val received = BigDecimal(received)
            override val transactionCount = 2L
            override val firstTransactionOn = LocalDate.of(2026, 9, 2)
            override val lastTransactionOn = LocalDate.of(2026, 9, 12)
        }
}
