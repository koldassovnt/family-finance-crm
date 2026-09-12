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
 * Deliberately has **no** `@SQLRestriction` — historical transactions keep
 * pointing at soft-deleted categories, and the restriction would apply to
 * relationship loading too, silently nulling them out. Deleted categories are
 * filtered explicitly in the list query instead. See `00-`.
 */
@Entity
@Table(name = "categories")
class Category(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @Column(nullable = false, length = 255)
    var name: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Category?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var kind: CategoryKind,
) : BaseEntity()
