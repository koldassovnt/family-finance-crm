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

@Entity
@Table(name = "transactions")
@SQLRestriction("is_deleted = false")
class Transaction(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var type: TransactionType,
    @Column(nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal,
    /** The account's currency; the original currency the money moved in. */
    @Column(nullable = false, length = 3)
    var currency: String,
    /**
     * KZT per 1 unit of [currency], supplied by hand — no rate source is
     * stored or fetched. Always 1 for a KZT account.
     */
    @Column(nullable = false, precision = 19, scale = 6)
    var exchangeRate: BigDecimal,
    /**
     * [amount] converted at [exchangeRate]. Every aggregation sums this rather
     * than [amount], so totals are never a mix of currencies added together.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    var amountKzt: BigDecimal,
    /** Destination amount for a cross-currency `TRANSFER`; null otherwise. */
    @Column(precision = 19, scale = 4)
    var toAmount: BigDecimal?,
    @Column(nullable = false)
    var occurredOn: LocalDate,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    var account: Account,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    var toAccount: Account?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category?,
    @Column(length = 1000)
    var note: String?,
) : BaseEntity()
