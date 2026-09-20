package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.ShareScope
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateAccountRequest
import com.familyfinance.crm.dto.UpdateAccountRequest
import java.util.UUID

interface AccountService {
    /** [scope] defaults to `OWN`, so this returns what it always did unless asked otherwise. */
    fun list(
        reader: User,
        scope: ShareScope = ShareScope.OWN,
    ): List<Readable<Account>>

    /**
     * Resolves an account the caller owns. Someone else's account 404s exactly
     * like a missing one, so ids can't be probed.
     *
     * **Every write path uses this one.** Its read-side twin
     * [getReadableBy] also admits viewers, so the two must never be swapped.
     */
    fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Account

    /**
     * Resolves an account the caller may **read** — their own, or one shared
     * with them. Read paths only: a viewer reaching a write path is the one
     * failure this feature cannot have.
     */
    fun getReadableBy(
        id: UUID,
        reader: User,
    ): Readable<Account>

    fun create(
        owner: User,
        request: CreateAccountRequest,
    ): Account

    /** Name and bank only — never the balance, which the ledger owns. */
    fun update(
        id: UUID,
        owner: User,
        request: UpdateAccountRequest,
    ): Account

    fun softDelete(
        id: UUID,
        owner: User,
    )
}
