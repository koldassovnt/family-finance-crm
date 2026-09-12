package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateAccountRequest
import com.familyfinance.crm.dto.UpdateAccountRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.AccountRepository
import com.familyfinance.crm.repository.GoalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccountServiceImpl(
    private val accountRepository: AccountRepository,
    private val bankService: BankService,
    // A repository rather than GoalService, which already depends on this one.
    private val goalRepository: GoalRepository,
) : AccountService {
    @Transactional(readOnly = true)
    override fun list(owner: User): List<Account> = accountRepository.findAllActiveByOwner(owner)

    @Transactional(readOnly = true)
    override fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Account {
        val account =
            accountRepository.findActiveById(id)
                ?: throw NotFoundException("Account $id was not found")
        if (account.owner != owner) throw NotFoundException("Account $id was not found")
        return account
    }

    @Transactional
    override fun create(
        owner: User,
        request: CreateAccountRequest,
    ): Account {
        val type = request.type ?: throw invalidField("type", "is required")
        if (type == AccountType.CASH && request.bankId != null) {
            throw invalidField("bankId", "must be absent for a CASH account")
        }
        return accountRepository.save(
            Account(
                owner = owner,
                bank = request.bankId?.let(bankService::getById),
                name = request.name.trim(),
                type = type,
                balance = request.balance,
                currency = normalizeCurrency(request.currency),
            ),
        )
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateAccountRequest,
    ): Account {
        val account = getOwnedBy(id, owner)
        request.name?.let { account.name = it.trim() }
        request.bankId?.let { bankId ->
            val bank = bankId.orElse(null)?.let(bankService::getById)
            if (bank != null && account.type == AccountType.CASH) {
                throw invalidField("bankId", "must be absent for a CASH account")
            }
            account.bank = bank
        }
        return account
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        owner: User,
    ) {
        val account = getOwnedBy(id, owner)
        // A goal measures its progress off this balance, so it blocks the delete.
        if (goalRepository.existsForAccountId(id)) {
            throw ConflictException("Account $id still has an active goal; delete the goal first")
        }
        account.isDeleted = true
    }
}

/** Currency is a plain 3-letter code, stored uppercase. */
internal fun normalizeCurrency(currency: String): String {
    val normalized = currency.trim().uppercase()
    if (normalized.length != CURRENCY_CODE_LENGTH) {
        throw invalidField("currency", "must be exactly $CURRENCY_CODE_LENGTH characters")
    }
    return normalized
}

private const val CURRENCY_CODE_LENGTH = 3
