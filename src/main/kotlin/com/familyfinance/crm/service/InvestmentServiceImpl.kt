package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class InvestmentServiceImpl(
    private val transactionRepository: TransactionRepository,
    private val accountService: AccountService,
) : InvestmentService {
    @Transactional(readOnly = true)
    override fun portfolio(owner: User): List<Holding> = holdingsOf(transactionRepository.findTradesByOwner(owner))

    @Transactional(readOnly = true)
    override fun accountHoldings(
        accountId: UUID,
        reader: User,
    ): List<Holding> {
        // A read path, so a viewer of this account is a legitimate caller.
        val account = accountService.getReadableBy(accountId, reader).resource
        return holdingsOf(transactionRepository.findTradesByAccount(account))
    }
}
