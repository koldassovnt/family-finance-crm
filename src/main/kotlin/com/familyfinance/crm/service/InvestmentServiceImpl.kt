package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.RenameTickerRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.MarketQuoteRepository
import com.familyfinance.crm.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class InvestmentServiceImpl(
    private val transactionRepository: TransactionRepository,
    private val quoteRepository: MarketQuoteRepository,
    private val accountService: AccountService,
) : InvestmentService {
    @Transactional(readOnly = true)
    override fun portfolio(owner: User): List<ValuedHolding> = valued(transactionRepository.findTradesByOwner(owner))

    @Transactional(readOnly = true)
    override fun accountHoldings(
        accountId: UUID,
        reader: User,
    ): List<ValuedHolding> {
        // A read path, so a viewer of this account is a legitimate caller.
        val account = accountService.getReadableBy(accountId, reader).resource
        return valued(transactionRepository.findTradesByAccount(account))
    }

    @Transactional
    override fun renameTicker(
        accountId: UUID,
        owner: User,
        request: RenameTickerRequest,
    ): List<ValuedHolding> {
        // A write path: the owner only, never a viewer.
        val account = accountService.getOwnedBy(accountId, owner)
        val from = request.from.trim().uppercase()
        val to = normalizeTicker(request.to, account)
        val trades = transactionRepository.findTradesByAccount(account)
        val renamed = trades.filter { it.ticker == from }
        if (renamed.isEmpty()) throw invalidField("from", "is not a ticker traded in this account")
        if (from != to && trades.any { it.ticker == to }) {
            // Merging two histories cannot be undone by renaming back.
            throw ConflictException("$to is already traded in this account; a rename cannot merge two tickers")
        }
        renamed.forEach { it.ticker = to }
        return valued(trades)
    }

    /** Reads only what is stored: viewing a portfolio never spends an API call. */
    private fun valued(trades: List<Transaction>): List<ValuedHolding> {
        val quotes = QuoteBook(quoteRepository.findAll())
        return holdingsOf(trades).map(quotes::value)
    }
}
