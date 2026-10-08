package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.TransactionRepository
import com.familyfinance.crm.user
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class InvestmentServiceImplTest {
    private val transactionRepository = mockk<TransactionRepository>()
    private val accountService = mockk<AccountService>()
    private val service = InvestmentServiceImpl(transactionRepository, accountService)

    private val owner = user()
    private val viewer = user(email = "viewer@example.com")

    @Test
    fun `the portfolio is read from the caller's own trades only`() {
        every { transactionRepository.findTradesByOwner(viewer) } returns emptyList()

        service.portfolio(viewer)

        verify(exactly = 1) { transactionRepository.findTradesByOwner(viewer) }
    }

    @Test
    fun `a viewer of a shared broker account can read its holdings`() {
        val broker = account(owner, type = AccountType.BROKER)
        every { accountService.getReadableBy(broker.idValue, viewer) } returns
            Readable.Shared(resource = broker, owner = owner, access = ShareAccess.VIEWER)
        every { transactionRepository.findTradesByAccount(broker) } returns emptyList()

        val holdings = service.accountHoldings(broker.idValue, viewer)

        assertTrue(holdings.isEmpty())
    }

    @Test
    fun `holdings of an account that is not shared are a 404`() {
        val broker = account(owner, type = AccountType.BROKER)
        every { accountService.getReadableBy(broker.idValue, viewer) } throws NotFoundException("Account was not found")

        assertThrows<NotFoundException> { service.accountHoldings(broker.idValue, viewer) }
    }
}
