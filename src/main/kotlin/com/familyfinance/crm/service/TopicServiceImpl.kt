package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.TopicStatus
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateTopicRequest
import com.familyfinance.crm.dto.UpdateTopicRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.TopicRepository
import com.familyfinance.crm.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class TopicServiceImpl(
    private val topicRepository: TopicRepository,
    private val transactionRepository: TransactionRepository,
) : TopicService {
    @Transactional(readOnly = true)
    override fun list(
        owner: User,
        status: TopicStatus?,
    ): List<TopicWithTotals> {
        val topics =
            if (status == null) {
                topicRepository.findAllActiveByOwner(owner)
            } else {
                topicRepository.findAllByOwnerAndStatus(owner, status)
            }
        // One aggregate query for every topic this owner has, rather than a
        // sum per row.
        val totals =
            transactionRepository
                .sumByTopic(
                    owner = owner,
                    expense = TransactionType.EXPENSE,
                    income = TransactionType.INCOME,
                ).associateBy { it.topicId }
        return topics.map { topic ->
            val row = totals[topic.id]
            TopicWithTotals(
                topic = topic,
                spent = row?.spent ?: BigDecimal.ZERO,
                received = row?.received ?: BigDecimal.ZERO,
                transactionCount = row?.transactionCount ?: 0,
                firstTransactionOn = row?.firstTransactionOn,
                lastTransactionOn = row?.lastTransactionOn,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun get(
        id: UUID,
        owner: User,
    ): TopicDetail {
        val topic = getOwnedBy(id, owner)
        return TopicDetail(
            totals = totalsFor(topic, owner),
            expenseByCategory =
                transactionRepository.sumByTopicAndCategory(topic, TransactionType.EXPENSE),
            incomeByCategory =
                transactionRepository.sumByTopicAndCategory(topic, TransactionType.INCOME),
        )
    }

    @Transactional(readOnly = true)
    override fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Topic {
        val topic =
            topicRepository.findById(id).orElseThrow { NotFoundException("Topic $id was not found") }
        // Deleted and foreign topics are both simply "not found", so ids can't be probed.
        if (topic.isDeleted || topic.owner != owner) throw NotFoundException("Topic $id was not found")
        return topic
    }

    @Transactional
    override fun create(
        owner: User,
        request: CreateTopicRequest,
    ): TopicWithTotals {
        val name = requireAvailableName(owner, request.name, excludedId = null)
        requireOrderedDates(request.startDate, request.endDate)
        val topic =
            topicRepository.save(
                Topic(
                    owner = owner,
                    name = name,
                    description = request.description?.let(::requireShortDescription),
                    startDate = request.startDate,
                    endDate = request.endDate,
                    plannedAmount = request.plannedAmount?.let(::requirePositivePlannedAmount),
                    status = TopicStatus.ACTIVE,
                ),
            )
        return TopicWithTotals(
            topic = topic,
            spent = BigDecimal.ZERO,
            received = BigDecimal.ZERO,
            transactionCount = 0,
            firstTransactionOn = null,
            lastTransactionOn = null,
        )
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateTopicRequest,
    ): TopicWithTotals {
        val topic = getOwnedBy(id, owner)
        request.name?.let { topic.name = requireAvailableName(owner, it, excludedId = topic.id) }
        request.description?.let { topic.description = it.orElse(null)?.let(::requireShortDescription) }
        request.startDate?.let { topic.startDate = it.orElse(null) }
        request.endDate?.let { topic.endDate = it.orElse(null) }
        // Checked after both are applied: either one alone can invert the range.
        requireOrderedDates(topic.startDate, topic.endDate)
        request.plannedAmount?.let {
            topic.plannedAmount = it.orElse(null)?.let(::requirePositivePlannedAmount)
        }
        request.status?.let { topic.status = it }
        return totalsFor(topic, owner)
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        owner: User,
    ) {
        // Transactions keep their topic_id: a topic is a view, and deleting a
        // view must never touch the money it was showing.
        getOwnedBy(id, owner).isDeleted = true
    }

    @Transactional(readOnly = true)
    override fun transactions(
        id: UUID,
        owner: User,
    ): List<Transaction> = transactionRepository.findAllByTopic(getOwnedBy(id, owner))

    @Transactional(readOnly = true)
    override fun candidates(
        id: UUID,
        owner: User,
    ): List<Transaction> {
        val topic = getOwnedBy(id, owner)
        val from = topic.startDate
        val to = topic.endDate
        if (from == null || to == null) {
            // Without a window the "candidates" are everything ever recorded,
            // which is a transaction list, not a suggestion.
            throw ValidationException(
                "Topic $id needs both a start date and an end date before candidates can be suggested",
                mapOf("startDate" to "is required to suggest candidates"),
            )
        }
        return transactionRepository.findTopicCandidates(
            owner = owner,
            types = ATTACHABLE_TYPES,
            from = from,
            to = to,
        )
    }

    @Transactional
    override fun attach(
        id: UUID,
        owner: User,
        transactionIds: List<UUID>,
    ): List<Transaction> {
        val topic = getOwnedBy(id, owner)
        val requested = transactionIds.distinct()
        val found = transactionRepository.findAllDetailedByIds(requested)

        val byId = found.associateBy { it.requiredIdOf() }
        val missing = requested.filter { byId[it]?.account?.owner != owner }
        if (missing.isNotEmpty()) {
            // Foreign ids read as missing, exactly as a single-id lookup would.
            throw NotFoundException("Transactions not found: ${missing.joinToString()}")
        }
        val wrongType = found.filterNot { it.type in ATTACHABLE_TYPES }
        if (wrongType.isNotEmpty()) {
            throw ValidationException(
                "Only INCOME and EXPENSE transactions can belong to a topic; " +
                    "rejected: ${wrongType.joinToString { "${it.requiredIdOf()} (${it.type})" }}",
                mapOf("transactionIds" to "must all be INCOME or EXPENSE"),
            )
        }

        found.forEach { it.topic = topic }
        return found
    }

    @Transactional
    override fun detach(
        id: UUID,
        owner: User,
        transactionId: UUID,
    ) {
        val topic = getOwnedBy(id, owner)
        val transaction =
            transactionRepository.findDetailedById(transactionId)
                ?: throw NotFoundException("Transaction $transactionId was not found")
        if (transaction.account.owner != owner) {
            throw NotFoundException("Transaction $transactionId was not found")
        }
        if (transaction.topic != topic) {
            throw NotFoundException("Transaction $transactionId is not attached to topic $id")
        }
        transaction.topic = null
    }

    private fun totalsFor(
        topic: Topic,
        owner: User,
    ): TopicWithTotals {
        val row =
            transactionRepository
                .sumByTopic(
                    owner = owner,
                    expense = TransactionType.EXPENSE,
                    income = TransactionType.INCOME,
                ).firstOrNull { it.topicId == topic.id }
        return TopicWithTotals(
            topic = topic,
            spent = row?.spent ?: BigDecimal.ZERO,
            received = row?.received ?: BigDecimal.ZERO,
            transactionCount = row?.transactionCount ?: 0,
            firstTransactionOn = row?.firstTransactionOn,
            lastTransactionOn = row?.lastTransactionOn,
        )
    }

    private fun requireAvailableName(
        owner: User,
        name: String,
        excludedId: UUID?,
    ): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw invalidField("name", "must not be blank")
        if (topicRepository.existsByName(owner = owner, name = trimmed, excludedId = excludedId)) {
            throw ConflictException("A topic named '$trimmed' already exists")
        }
        return trimmed
    }
}

/** A transfer would count both the withdrawal and whatever it paid for. */
private val ATTACHABLE_TYPES = setOf(TransactionType.INCOME, TransactionType.EXPENSE)

private const val MAX_DESCRIPTION_LENGTH = 1000

private fun Transaction.requiredIdOf(): UUID = checkNotNull(id) { "Transaction has not been persisted yet" }

private fun requireShortDescription(description: String): String {
    if (description.length > MAX_DESCRIPTION_LENGTH) {
        throw invalidField("description", "must be at most $MAX_DESCRIPTION_LENGTH characters")
    }
    return description
}

private fun requirePositivePlannedAmount(amount: BigDecimal): BigDecimal {
    if (amount.signum() <= 0) throw invalidField("plannedAmount", "must be greater than zero")
    return amount
}

private fun requireOrderedDates(
    startDate: LocalDate?,
    endDate: LocalDate?,
) {
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
        throw invalidField("endDate", "must not be before 'startDate'")
    }
}
