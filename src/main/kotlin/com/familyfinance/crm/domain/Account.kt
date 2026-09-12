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

/**
 * Deliberately has **no** `@SQLRestriction`, for the same reason as
 * [Category]: transactions keep referencing a soft-deleted account, and the
 * restriction applies to relationship loading too — `transaction.account`
 * would resolve to null and blow up every history and summary response.
 * Deleted accounts are filtered explicitly in the account queries instead.
 */
@Entity
@Table(name = "accounts")
class Account(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    var bank: Bank?,
    @Column(nullable = false, length = 255)
    var name: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var type: AccountType,
    @Column(nullable = false, precision = 19, scale = 4)
    var balance: BigDecimal,
    @Column(nullable = false, length = 3)
    var currency: String,
) : BaseEntity()
