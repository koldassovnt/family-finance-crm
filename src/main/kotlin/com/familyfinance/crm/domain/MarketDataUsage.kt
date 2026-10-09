package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

/**
 * How many calls one API has been sent on one day. Stored rather than counted
 * in memory, so the daily cap survives a restart and covers manual refreshes.
 */
@Entity
@Table(name = "market_data_usage")
@SQLRestriction("is_deleted = false")
class MarketDataUsage(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var kind: QuoteKind,
    @Column(nullable = false)
    var day: LocalDate,
    @Column(nullable = false)
    var calls: Int,
) : BaseEntity()
