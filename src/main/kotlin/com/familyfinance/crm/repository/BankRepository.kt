package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Bank
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BankRepository : JpaRepository<Bank, UUID> {
    @Query("SELECT b FROM Bank b WHERE lower(b.name) = lower(:name)")
    fun findByNameIgnoreCase(name: String): Bank?

    fun findAllByOrderByNameAsc(): List<Bank>
}
