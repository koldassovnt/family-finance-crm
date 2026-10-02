package com.familyfinance.crm.service

import com.familyfinance.crm.config.AppProperties.BootstrapOwnerProperties
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateUserRequest
import java.util.UUID

sealed interface OwnerBootstrap {
    data class Created(
        val email: String,
    ) : OwnerBootstrap

    data object AlreadyExists : OwnerBootstrap

    /** No `OWNER` yet and nothing to create one from — nobody can log in. */
    data object NotConfigured : OwnerBootstrap
}

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
     * Startup only, never reachable over HTTP: creates the `OWNER` from
     * deploy-time config when none exists. Invalid config throws, so a
     * misconfigured deploy fails to start instead of running owner-less.
     */
    fun bootstrapOwner(config: BootstrapOwnerProperties): OwnerBootstrap

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
