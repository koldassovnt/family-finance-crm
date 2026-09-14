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

/**
 * One snapshot of a budget's limit, in force over a range of months. Changing a
 * limit closes the open version at the end of last month and opens a new one
 * from this month, so a past month always reports the limit that actually
 * applied then.
 *
 * Both month columns hold the **first day** of the month, and
 * [effectiveToMonth] is **inclusive** — the last month this version applied to.
 * `null` means still in force.
 */
@Entity
@Table(name = "budget_versions")
@SQLRestriction("is_deleted = false")
class BudgetVersion(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false)
    var budget: Budget,
    @Column(nullable = false, precision = 19, scale = 4)
    var limitAmount: BigDecimal,
    @Column
    var alertThresholdPercent: Int?,
    @Column(nullable = false)
    var effectiveFromMonth: LocalDate,
    @Column
    var effectiveToMonth: LocalDate?,
) : BaseEntity()
