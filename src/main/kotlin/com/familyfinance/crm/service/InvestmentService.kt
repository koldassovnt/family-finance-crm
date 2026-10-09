package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.RenameTickerRequest
import java.util.UUID

interface InvestmentService {
    /**
     * Everything the caller holds across their own accounts, valued at the
     * latest stored prices. Strictly own-only: a broker account shared with
     * them never adds to these figures.
     */
    fun portfolio(owner: User): List<ValuedHolding>

    /**
     * One account's holdings. Readable by a viewer of the account, like its
     * history: sharing a broker account shares what is in it.
     */
    fun accountHoldings(
        accountId: UUID,
        reader: User,
    ): List<ValuedHolding>

    /**
     * Rewrites a ticker on every trade of it in one account, for an asset that
     * was renamed (TON became GRAM). All of them at once, because one at a
     * time would split the holding in two halfway through. Owner only.
     */
    fun renameTicker(
        accountId: UUID,
        owner: User,
        request: RenameTickerRequest,
    ): List<ValuedHolding>
}
