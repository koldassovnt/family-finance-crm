package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.FamilyPurchasesResponse
import java.time.LocalDate

interface FamilyService {
    /**
     * Every member's family purchases in the window, newest first, with their
     * totals. Readable by any member: an expense marked as a family purchase
     * was published to the household by whoever recorded it. Nothing personal
     * is ever returned — see `phase-9-family-purchases.md`.
     */
    fun purchases(
        reader: User,
        from: LocalDate,
        to: LocalDate,
    ): FamilyPurchasesResponse
}
