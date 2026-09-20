package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.AccessLevel
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.domain.ShareScope
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
import com.familyfinance.crm.repository.ShareRepository
import com.familyfinance.crm.repository.TopicRepository
import com.familyfinance.crm.repository.TopicTotals
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.share
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
import kotlin.test.assertTrue

class TopicServiceImplTest {
    private val topicRepository = mockk<TopicRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val shareRepository = mockk<ShareRepository>()
    private val shareAccess = ShareAccessServiceImpl(shareRepository)
    private val service =
        TopicServiceImpl(
            topicRepository = topicRepository,
            transactionRepository = transactionRepository,
            shareAccess = shareAccess,
        )

    private val owner = user()
    private val account = account(owner)

    init {
        every { topicRepository.save(any<Topic>()) } answers { firstArg<Topic>().withId() }
        every { topicRepository.existsByName(any(), any(), any()) } returns false
        every { transactionRepository.sumForTopics(any(), any(), any()) } returns emptyList()
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
        every { topicRepository.findActiveById(topic.idValue) } returns topic

        service.update(topic.idValue, owner, UpdateTopicRequest(endDate = Optional.empty()))

        assertNull(topic.endDate)
    }

    @Test
    fun `remaining goes negative once past the planned amount`() {
        val topic = topic(owner, plannedAmount = "500000")
        every { topicRepository.findActiveById(topic.idValue) } returns topic
        every { transactionRepository.sumForTopics(any(), any(), any()) } returns
            listOf(totals(topic.idValue, spent = "620000", received = "20000"))
        every { transactionRepository.sumByTopicAndCategory(topic, any()) } returns emptyList()

        val detail = service.get(topic.idValue, owner).resource

        assertEquals(BigDecimal("600000"), detail.totals.net)
        assertEquals(BigDecimal("-100000"), detail.totals.remaining)
    }

    @Test
    fun `remaining is null when nothing was planned`() {
        val topic = topic(owner)
        every { topicRepository.findActiveById(topic.idValue) } returns topic
        every { transactionRepository.sumByTopicAndCategory(topic, any()) } returns emptyList()

        assertNull(
            service
                .get(topic.idValue, owner)
                .resource.totals.remaining,
        )
    }

    @Test
    fun `candidates require the topic to have both dates`() {
        val topic = topic(owner, endDate = null)
        every { topicRepository.findActiveById(topic.idValue) } returns topic

        assertThrows<ValidationException> { service.candidates(topic.idValue, owner) }
    }

    @Test
    fun `attaching sets the topic on every requested transaction`() {
        val topic = topic(owner)
        val first = transaction(TransactionType.EXPENSE)
        val second = transaction(TransactionType.INCOME)
        every { topicRepository.findActiveById(topic.idValue) } returns topic
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
        every { topicRepository.findActiveById(topic.idValue) } returns topic
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
        every { topicRepository.findActiveById(topic.idValue) } returns topic
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
        every { topicRepository.findActiveById(topic.idValue) } returns topic
        every { transactionRepository.findDetailedById(transaction.idValue) } returns transaction

        assertThrows<NotFoundException> {
            service.detach(topic.idValue, owner, transaction.idValue)
        }
    }

    @Test
    fun `a deleted topic reads as not found`() {
        val topic = topic(owner).apply { isDeleted = true }
        // The query filters isDeleted, so a soft-deleted topic is simply absent.
        every { topicRepository.findActiveById(topic.idValue) } returns null

        assertThrows<NotFoundException> { service.getOwnedBy(topic.idValue, owner) }
    }

    // Phase 8 — sharing. The widest of the five: sharing a lens shares what it frames.

    private val viewer = user(email = "viewer@example.com")

    @Test
    fun `a viewer may read the detail of a topic shared with them`() {
        val theirs = topic(owner, plannedAmount = "500000")
        every { topicRepository.findActiveById(theirs.idValue) } returns theirs
        every { shareRepository.findGrant(viewer, ShareResourceType.TOPIC, theirs.idValue) } returns
            share(owner, viewer, ShareResourceType.TOPIC, theirs.idValue)
        every { transactionRepository.sumForTopics(any(), any(), any()) } returns
            listOf(totals(theirs.idValue, spent = "620000", received = "20000"))
        every { transactionRepository.sumByTopicAndCategory(theirs, any()) } returns emptyList()

        val readable = service.get(theirs.idValue, viewer)

        assertEquals(BigDecimal("600000"), readable.resource.totals.net)
        assertEquals(AccessLevel.VIEWER, readable.accessLevel)
        assertEquals(owner, readable.sharedBy)
    }

    @Test
    fun `a viewer may read the transactions a shared topic frames, whatever account they sit on`() {
        val theirs = topic(owner)
        val attached = transaction(TransactionType.EXPENSE)
        every { topicRepository.findActiveById(theirs.idValue) } returns theirs
        every { shareRepository.findGrant(viewer, ShareResourceType.TOPIC, theirs.idValue) } returns
            share(owner, viewer, ShareResourceType.TOPIC, theirs.idValue)
        every { transactionRepository.findAllByTopic(theirs) } returns listOf(attached)

        // Deliberate: the rows belong to an account never shared with them.
        assertEquals(listOf(attached), service.transactions(theirs.idValue, viewer))
    }

    @Test
    fun `a viewer of one topic cannot read a second topic of the same owner`() {
        val alsoTheirs = topic(owner)
        every { topicRepository.findActiveById(alsoTheirs.idValue) } returns alsoTheirs
        every { shareRepository.findGrant(viewer, ShareResourceType.TOPIC, alsoTheirs.idValue) } returns null

        assertThrows<NotFoundException> { service.get(alsoTheirs.idValue, viewer) }
    }

    @Test
    fun `candidates stay owner-only, because suggesting what to attach is a writing tool`() {
        val theirs = topic(owner)
        every { topicRepository.findActiveById(theirs.idValue) } returns theirs
        every { shareRepository.findGrant(viewer, ShareResourceType.TOPIC, theirs.idValue) } returns
            share(owner, viewer, ShareResourceType.TOPIC, theirs.idValue)

        assertThrows<NotFoundException> { service.candidates(theirs.idValue, viewer) }
    }

    @Test
    fun `a viewer cannot attach a transaction to a topic shared with them`() {
        val theirs = topic(owner)
        every { topicRepository.findActiveById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.attach(theirs.idValue, viewer, listOf(UUID.randomUUID()))
        }
    }

    @Test
    fun `a viewer cannot detach a transaction from a topic shared with them`() {
        val theirs = topic(owner)
        every { topicRepository.findActiveById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.detach(theirs.idValue, viewer, UUID.randomUUID())
        }
    }

    @Test
    fun `a viewer cannot rename or delete a topic shared with them`() {
        val theirs = topic(owner)
        every { topicRepository.findActiveById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> {
            service.update(theirs.idValue, viewer, UpdateTopicRequest(name = "Mine now"))
        }
        assertThrows<NotFoundException> { service.softDelete(theirs.idValue, viewer) }
        assertEquals("Malaysia trip", theirs.name)
        assertTrue(!theirs.isDeleted)
    }

    @Test
    fun `scope SHARED lists shared topics with their own totals, badged with the owner`() {
        val theirs = topic(owner, plannedAmount = "500000")
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.TOPIC) } returns
            listOf(share(owner, viewer, ShareResourceType.TOPIC, theirs.idValue))
        every { topicRepository.findAllActiveByIds(setOf(theirs.idValue)) } returns listOf(theirs)
        every { transactionRepository.sumForTopics(any(), any(), any()) } returns
            listOf(totals(theirs.idValue, spent = "100000", received = "0"))

        val listed = service.list(viewer, status = null, scope = ShareScope.SHARED)

        assertEquals(BigDecimal("100000"), listed.single().resource.net)
        assertEquals(owner, listed.single().sharedBy)
    }

    @Test
    fun `the status filter applies to shared topics too`() {
        val active = topic(owner, name = "Active trip")
        val closed = topic(owner, name = "Closed trip", status = TopicStatus.CLOSED)
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.TOPIC) } returns
            listOf(
                share(owner, viewer, ShareResourceType.TOPIC, active.idValue),
                share(owner, viewer, ShareResourceType.TOPIC, closed.idValue),
            )
        every { topicRepository.findAllActiveByIds(any()) } returns listOf(active, closed)

        val listed = service.list(viewer, status = TopicStatus.CLOSED, scope = ShareScope.SHARED)

        assertEquals(listOf("Closed trip"), listed.map { it.resource.topic.name })
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
