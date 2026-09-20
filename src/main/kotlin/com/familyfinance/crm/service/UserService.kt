package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateUserRequest
import java.util.UUID

interface UserService {
    /** The authenticated caller as an entity; the principal only carries an id. */
    fun getById(id: UUID): User

    /**
     * Every member of the household, for **any** authenticated caller — sharing
     * needs somebody to share with. A widening of Phase 0/1, where users were
     * invisible to each other; see `phase-8-sharing.md`.
     */
    fun listMembers(): List<User>

    fun authenticate(
        email: String,
        password: String,
    ): User

    /** `OWNER`-only. Can only ever create a `MEMBER` — see `00-`. */
    fun createMember(request: CreateUserRequest): User

    /**
     * Self-service, any role. Also invalidates every token issued before now,
     * which is the only revocation this system has.
     */
    fun changePassword(
        id: UUID,
        currentPassword: String,
        newPassword: String,
    )
}
