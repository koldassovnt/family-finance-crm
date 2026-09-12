package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Progress is measured off the linked account's balance rather than a separate
 * ledger, so there is no "contribute to goal" operation — contributions are
 * ordinary transactions into the account.
 */
@Entity
@Table(name = "goals")
@SQLRestriction("is_deleted = false")
class Goal(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @Column(nullable = false, length = 255)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: GoalType,
    @Column(nullable = false, precision = 19, scale = 4)
    var targetAmount: BigDecimal,
    @Column
    var targetDate: LocalDate?,
    /** Several goals may point at the same account — separate milestones on one pot. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linked_account_id", nullable = false)
    var linkedAccount: Account,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: GoalStatus,
) : BaseEntity()
