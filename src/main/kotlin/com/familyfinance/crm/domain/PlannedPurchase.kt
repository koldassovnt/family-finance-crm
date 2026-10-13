package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

/**
 * Something someone means to buy. A personal one belongs to whoever added it;
 * a family one is the whole household's, to see, tick off and remove. It knows
 * nothing about accounts or money: marking it bought does **not** create a
 * `Transaction` — see `phase-9-planned-purchases.md`.
 */
@Entity
@Table(name = "planned_purchases")
@SQLRestriction("is_deleted = false")
class PlannedPurchase(
    /** Who added it; for a family item that is a label, not a permission. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @Column(nullable = false, length = 255)
    var name: String,
    /** Fixed when it is added: moving an item between the two lists is delete and add again. */
    @Column(nullable = false)
    var isFamily: Boolean,
    @Column(nullable = false)
    var isBought: Boolean = false,
) : BaseEntity()
