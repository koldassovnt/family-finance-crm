package com.familyfinance.crm.security

import com.familyfinance.crm.domain.UserRole
import java.time.Instant
import java.util.UUID

/** The authenticated principal, carrying only what a request needs to know. */
data class AuthenticatedUser(
    val id: UUID,
    val email: String,
    val role: UserRole,
    /** Checked against the user's `passwordChangedAt` on every request. */
    val issuedAt: Instant,
)
