package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateTransactionRequest
import com.familyfinance.crm.dto.MonthlySummaryResponse
import com.familyfinance.crm.dto.ReconcileRequest
import com.familyfinance.crm.dto.UpdateTransactionRequest
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

interface TransactionService {
    fun create(
        owner: User,
        request: CreateTransactionRequest,
    ): Transaction

    /** Amount, date, category and note only — type and accounts are immutable. */
    fun update(
        id: UUID,
        owner: User,
        request: UpdateTransactionRequest,
    ): Transaction

    /** Soft delete that also reverses the transaction's effect on balances. */
    fun softDelete(
        id: UUID,
        owner: User,
    )

    /** Matches either side of a transfer, so both accounts see it. */
    fun history(
        accountId: UUID,
        owner: User,
        from: LocalDate,
        to: LocalDate,
    ): List<Transaction>

    fun monthlySummary(
        owner: User,
        month: YearMonth,
    ): MonthlySummaryResponse

    /**
     * Corrects a drifted balance by writing an `ADJUSTMENT` for the delta —
     * the ledger must always explain the balance, so there is no direct edit.
     */
    fun reconcile(
        accountId: UUID,
        owner: User,
        request: ReconcileRequest,
    ): Transaction
}
