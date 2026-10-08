package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import java.util.UUID

interface InvestmentService {
    /**
     * Everything the caller holds across their own accounts. Strictly own-only:
     * a broker account shared with them never adds to these figures.
     */
    fun portfolio(owner: User): List<Holding>

    /**
     * One account's holdings. Readable by a viewer of the account, like its
     * history: sharing a broker account shares what is in it.
     */
    fun accountHoldings(
        accountId: UUID,
        reader: User,
    ): List<Holding>
}
