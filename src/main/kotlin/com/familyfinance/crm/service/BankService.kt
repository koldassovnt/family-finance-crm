package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Bank
import java.util.UUID

interface BankService {
    fun listAll(): List<Bank>

    fun getById(id: UUID): Bank

    /** Self-service: any member can add a bank that isn't in the list yet. */
    fun findOrCreate(name: String): Bank
}
