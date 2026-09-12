package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateAccountRequest
import com.familyfinance.crm.dto.UpdateAccountRequest
import java.util.UUID

interface AccountService {
    fun list(owner: User): List<Account>

    /**
     * Resolves an account the caller owns. Someone else's account 404s exactly
     * like a missing one, so ids can't be probed.
     */
    fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Account

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
