package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.domain.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    fun findByEmailIgnoreCase(email: String): User?

    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    fun existsByEmailIgnoreCase(email: String): Boolean

    fun countByRole(role: UserRole): Long

    /** Only the instant, so the per-request revocation check stays cheap. */
    @Query("SELECT u.passwordChangedAt FROM User u WHERE u.id = :id")
    fun findPasswordChangedAt(id: UUID): Instant?
}
