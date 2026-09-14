package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * The stable identity of a budget — one per (owner, category). The limit lives
 * in [BudgetVersion]s, so it can change without rewriting what past months were
 * measured against. Usage is never stored; it is summed on read.
 *
 * Deliberately has **no** `@SQLRestriction`: deleting a budget closes its open
 * version but leaves past months intact, and the restriction would hide those
 * months too. Active budgets are filtered explicitly, as with [Category] and
 * [Account].
 */
@Entity
@Table(name = "budgets")
class Budget(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    var category: Category,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var period: BudgetPeriod,
) : BaseEntity()
