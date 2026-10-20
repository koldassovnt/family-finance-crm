package com.familyfinance.crm.security

import com.familyfinance.crm.config.AppProperties.BotProperties
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.requiredId
import com.familyfinance.crm.exception.ErrorCode
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.util.UUID

const val BOT_TOKEN_HEADER = "X-Bot-Token"
const val TELEGRAM_USER_ID_HEADER = "X-Telegram-User-Id"

/** The bot's own endpoints: it speaks for nobody there. */
val BOT_PATHS: RequestMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/v1/bot/**")

/**
 * Where the bot may act for a linked user — what it actually does, and no
 * more. Sharing, users, budgets and the rest stay out of reach of a leaked
 * bot token.
 */
private val ON_BEHALF_PATHS: RequestMatcher =
    PathPatternRequestMatcher.withDefaults().let { paths ->
        OrRequestMatcher(
            listOf(
                "/api/v1/transactions",
                "/api/v1/transactions/**",
                "/api/v1/accounts",
                "/api/v1/accounts/**",
                "/api/v1/categories",
                "/api/v1/categories/**",
                "/api/v1/bills",
                "/api/v1/bills/**",
                "/api/v1/planned-purchases",
                "/api/v1/planned-purchases/**",
                // The topics to file a transaction under, and the stored rate
                // to offer — not a topic's own endpoints, nor a refresh.
                "/api/v1/topics",
                "/api/v1/market-data/rates",
            ).map { paths.matcher(it) },
        )
    }

/** The bot itself, on its own endpoints. */
class BotAuthentication : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_BOT"))) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials() = null

    override fun getPrincipal() = "telegram-bot"
}

/** The bot acting for one linked user, with exactly that user's rights. */
class TelegramUserAuthentication(
    user: User,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))) {
    val userId: UUID = user.requiredId()

    init {
        isAuthenticated = true
    }

    override fun getCredentials() = null

    override fun getPrincipal() = userId
}

/**
 * Authenticates the Telegram bot by its shared secret. A request without
 * [BOT_TOKEN_HEADER] passes through untouched to the bearer-token check;
 * one with it is decided here and never falls back to a JWT.
 */
class BotAuthenticationFilter(
    private val properties: BotProperties,
    private val userService: UserService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val token = request.getHeader(BOT_TOKEN_HEADER)
        if (token == null) {
            chain.doFilter(request, response)
            return
        }
        val authentication =
            when {
                !matchesConfiguredToken(token) -> {
                    return response.writeError(
                        objectMapper,
                        HttpStatus.UNAUTHORIZED,
                        ErrorResponse(ErrorCode.UNAUTHENTICATED, "A valid bot token is required"),
                    )
                }

                BOT_PATHS.matches(request) -> {
                    BotAuthentication()
                }

                !ON_BEHALF_PATHS.matches(request) -> {
                    return response.writeError(
                        objectMapper,
                        HttpStatus.FORBIDDEN,
                        ErrorResponse(ErrorCode.FORBIDDEN, "The bot may not call this endpoint"),
                    )
                }

                else -> {
                    onBehalfOf(request) ?: return response.notLinked()
                }
            }
        SecurityContextHolder.getContext().authentication = authentication
        chain.doFilter(request, response)
    }

    private fun onBehalfOf(request: HttpServletRequest): TelegramUserAuthentication? {
        val telegramUserId = request.getHeader(TELEGRAM_USER_ID_HEADER)?.trim()?.toLongOrNull() ?: return null
        val user = userService.findByTelegramUserId(telegramUserId) ?: return null
        return TelegramUserAuthentication(user)
    }

    /** Constant-time, so response timing reveals nothing about the secret. */
    private fun matchesConfiguredToken(token: String) =
        properties.isConfigured &&
            MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), properties.token.toByteArray(Charsets.UTF_8))

    private fun HttpServletResponse.notLinked() =
        writeError(
            objectMapper,
            HttpStatus.FORBIDDEN,
            ErrorResponse(
                ErrorCode.TELEGRAM_NOT_LINKED,
                "$TELEGRAM_USER_ID_HEADER is missing or belongs to no linked user — send /start to the bot",
            ),
        )
}
