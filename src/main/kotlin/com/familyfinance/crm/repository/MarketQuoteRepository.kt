package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.MarketQuote
import com.familyfinance.crm.domain.QuoteKind
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MarketQuoteRepository : JpaRepository<MarketQuote, UUID> {
    fun findAllByKindOrderBySymbol(kind: QuoteKind): List<MarketQuote>
}
