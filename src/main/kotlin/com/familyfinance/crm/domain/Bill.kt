package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Something due: a utility bill, a subscription, a loan payment. Deliberately
 * knows nothing about accounts or categories — marking one paid does **not**
 * create a `Transaction`; you record the spend separately, as you would
 * without this feature.
 *
 * "Overdue" is derived (`!isPaid && dueDate < today`), never stored, so it
 * cannot go stale.
 */
@Entity
@Table(name = "bills")
@SQLRestriction("is_deleted = false")
class Bill(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @Column(nullable = false, length = 255)
    var name: String,
    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal,
    @Column(nullable = false, length = 3)
    var currency: String,
    @Column(nullable = false)
    var dueDate: LocalDate,
    @Column(nullable = false)
    var isPaid: Boolean,
    /**
     * Shared by every row from one batch call, null for an individually created
     * bill. A convenience handle for deleting a series, not a grouping that
     * constrains the rows — editing one never touches its siblings.
     */
    @Column
    var batchId: UUID?,
) : BaseEntity()
