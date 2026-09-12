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

/**
 * One active budget per category per person. Usage is never stored — it is
 * summed from the current month's `EXPENSE` transactions on every read, so
 * there is no per-month row to roll over.
 */
@Entity
@Table(name = "budgets")
@SQLRestriction("is_deleted = false")
class Budget(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category,
    @Column(nullable = false, precision = 19, scale = 4)
    var limitAmount: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var period: BudgetPeriod,
    /**
     * Display cue only — nothing server-side reacts to it. The API returns it
     * next to computed usage so the UI can colour the progress bar.
     */
    @Column
    var alertThresholdPercent: Int?,
) : BaseEntity()
