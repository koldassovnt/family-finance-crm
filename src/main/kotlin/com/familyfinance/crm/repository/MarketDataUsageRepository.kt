package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.MarketDataUsage
import com.familyfinance.crm.domain.QuoteKind
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface MarketDataUsageRepository : JpaRepository<MarketDataUsage, UUID> {
    fun findByKindAndDay(
        kind: QuoteKind,
        day: LocalDate,
    ): MarketDataUsage?
}
