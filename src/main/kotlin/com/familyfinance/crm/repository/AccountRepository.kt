package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * `Account` deliberately carries no `@SQLRestriction` (see [Account]), so
 * every query here filters `isDeleted` explicitly.
 */
interface AccountRepository : JpaRepository<Account, UUID> {
    @Query(
        """
        SELECT a FROM Account a
        LEFT JOIN FETCH a.bank
        WHERE a.owner = :owner AND a.isDeleted = false
        ORDER BY a.name ASC
        """,
    )
    fun findAllActiveByOwner(owner: User): List<Account>

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.bank WHERE a.id = :id AND a.isDeleted = false")
    fun findActiveById(id: UUID): Account?
}
