package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateUserRequest
import java.util.UUID

interface UserService {
    /** The authenticated caller as an entity; the principal only carries an id. */
    fun getById(id: UUID): User

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
