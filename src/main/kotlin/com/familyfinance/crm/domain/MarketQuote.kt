package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.Instant

/**
 * The latest known price of one thing, overwritten on each refresh. Shared
 * across users like [Bank]: a price belongs to the market, not to whoever
 * holds the asset.
 */
@Entity
@Table(name = "market_quotes")
@SQLRestriction("is_deleted = false")
class MarketQuote(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var kind: QuoteKind,
    /** A ticker for `STOCK` and `CRYPTO`, a currency code for `CURRENCY`. */
    @Column(nullable = false, length = 32)
    var symbol: String,
    /** One unit's price in [currency]. For `CURRENCY`: KZT per one unit of [symbol]. */
    @Column(nullable = false, precision = 28, scale = 10)
    var price: BigDecimal,
    @Column(nullable = false, length = 3)
    var currency: String,
    @Column(nullable = false)
    var fetchedAt: Instant,
    /** Where a `STOCK` trades, as the price API names it; null for the other kinds. */
    @Column(length = 32)
    var exchange: String? = null,
) : BaseEntity()
