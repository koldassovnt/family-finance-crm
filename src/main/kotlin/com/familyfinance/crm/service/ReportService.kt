package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import java.time.LocalDate
import java.util.UUID

interface ReportService {
    /**
     * The caller's own transactions as an `.xlsx` workbook, one row each,
     * newest first. Takes the same window and filters as
     * [TransactionService.list] and fails the same way, so the file always
     * holds exactly what that list would return.
     */
    fun transactionsWorkbook(
        owner: User,
        from: LocalDate,
        to: LocalDate,
        accountId: UUID? = null,
        categoryId: UUID? = null,
        topicId: UUID? = null,
    ): ByteArray
}
