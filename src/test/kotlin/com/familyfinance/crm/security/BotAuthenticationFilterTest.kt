package com.familyfinance.crm.security

import com.familyfinance.crm.config.AppProperties.BotProperties
import com.familyfinance.crm.domain.UserRole
import com.familyfinance.crm.idValue
import com.familyfinance.crm.service.UserService
import com.familyfinance.crm.user
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BotAuthenticationFilterTest {
    private val userService = mockk<UserService>()
    private val filter =
        BotAuthenticationFilter(
            properties = BotProperties(token = "bot-secret"),
            userService = userService,
            objectMapper = ObjectMapper(),
        )

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun run(
        path: String,
        botToken: String? = "bot-secret",
        telegramUserId: String? = null,
        method: String = "GET",
    ): Pair<MockHttpServletResponse, MockFilterChain> {
        val request = MockHttpServletRequest(method, path).apply { requestURI = path }
        botToken?.let { request.addHeader(BOT_TOKEN_HEADER, it) }
        telegramUserId?.let { request.addHeader(TELEGRAM_USER_ID_HEADER, it) }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        return response to chain
    }

    @Test
    fun `leaves a request without a bot token to the bearer-token check`() {
        val (_, chain) = run("/api/v1/bills", botToken = null)

        assertNotNull(chain.request)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `refuses a wrong bot token with 401`() {
        val (response, chain) = run("/api/v1/bot/bills-due", botToken = "guess")

        assertEquals(401, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `refuses every bot request when no token is configured`() {
        val unconfigured = BotAuthenticationFilter(BotProperties(token = ""), userService, ObjectMapper())
        val request =
            MockHttpServletRequest("GET", "/api/v1/bot/bills-due").apply {
                requestURI = "/api/v1/bot/bills-due"
                addHeader(BOT_TOKEN_HEADER, "")
            }
        val response = MockHttpServletResponse()

        unconfigured.doFilter(request, response, MockFilterChain())

        assertEquals(401, response.status)
    }

    @Test
    fun `authenticates the bot itself on its own endpoints`() {
        val (_, chain) = run("/api/v1/bot/bills-due")

        assertNotNull(chain.request)
        assertIs<BotAuthentication>(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `acts for the linked user on an allowed endpoint`() {
        val member = user(role = UserRole.MEMBER)
        every { userService.findByTelegramUserId(123) } returns member

        run("/api/v1/transactions", telegramUserId = "123")

        val authentication = assertIs<TelegramUserAuthentication>(SecurityContextHolder.getContext().authentication)
        assertEquals(member.idValue, authentication.userId)
        assertEquals(listOf("ROLE_MEMBER"), authentication.authorities.map { it.authority })
    }

    @Test
    fun `answers TELEGRAM_NOT_LINKED for a Telegram account nobody linked`() {
        every { userService.findByTelegramUserId(999) } returns null

        val (response, chain) = run("/api/v1/bills", telegramUserId = "999")

        assertEquals(403, response.status)
        assertTrue(response.contentAsString.contains("TELEGRAM_NOT_LINKED"))
        assertNull(chain.request)
    }

    @Test
    fun `answers TELEGRAM_NOT_LINKED when the user header is missing`() {
        val (response, _) = run("/api/v1/bills")

        assertTrue(response.contentAsString.contains("TELEGRAM_NOT_LINKED"))
    }

    @Test
    fun `lets the bot list a linked user's topics`() {
        every { userService.findByTelegramUserId(123) } returns user()

        val (_, chain) = run("/api/v1/topics", telegramUserId = "123")

        assertNotNull(chain.request)
    }

    @Test
    fun `keeps the bot away from a topic's own endpoints`() {
        val (response, chain) = run("/api/v1/topics/${UUID.randomUUID()}/transactions", telegramUserId = "123")

        assertEquals(403, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `lets the bot read the stored exchange rates`() {
        every { userService.findByTelegramUserId(123) } returns user()

        val (_, chain) = run("/api/v1/market-data/rates", telegramUserId = "123")

        assertNotNull(chain.request)
    }

    @Test
    fun `does not let the bot start a market data refresh`() {
        val (response, chain) = run("/api/v1/market-data/refresh", telegramUserId = "123", method = "POST")

        assertEquals(403, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `keeps the bot away from endpoints it has no use for`() {
        val (response, chain) = run("/api/v1/shares/outgoing", telegramUserId = "123")

        assertEquals(403, response.status)
        assertNull(chain.request)
    }
}
