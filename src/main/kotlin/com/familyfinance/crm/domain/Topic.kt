package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A named undertaking — a trip, a renovation, a wedding — that a set of
 * transactions belongs to. It adds no money of its own: every figure it
 * reports is summed on read from the transactions pointing at it.
 *
 * Orthogonal to [Category], which says *what* the money was for and carries
 * budgets. A topic says *which occasion* it belonged to, is finite in time,
 * and cuts across categories.
 *
 * Deliberately has **no** `@SQLRestriction`, for the same reason as
 * [Category] — transactions keep pointing at a deleted topic, and the
 * restriction would apply to relationship loading and silently null them out.
 * Deleted topics are filtered explicitly in the list queries instead.
 */
@Entity
@Table(name = "topics")
class Topic(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @Column(nullable = false, length = 255)
    var name: String,
    @Column(length = 1000)
    var description: String?,
    /** Describes when it happened; does not constrain what may be attached. */
    @Column
    var startDate: LocalDate?,
    @Column
    var endDate: LocalDate?,
    /** What you expected to spend, in KZT. Display only — nothing alerts. */
    @Column(precision = 19, scale = 4)
    var plannedAmount: BigDecimal?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: TopicStatus,
) : BaseEntity()
