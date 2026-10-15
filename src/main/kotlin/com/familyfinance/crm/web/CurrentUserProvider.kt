package com.familyfinance.crm.web

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.exception.UnauthenticatedException
import com.familyfinance.crm.security.TelegramUserAuthentication
import com.familyfinance.crm.service.UserService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The JWT carries only an id (its subject, checked to be a UUID when the token
 * was validated); services work with the entity. This is
 * the one place that bridges the two, so controllers don't each repeat it.
 */
@Component
class CurrentUserProvider(
    private val userService: UserService,
) {
    fun require(): User {
        val userId =
            when (val authentication = SecurityContextHolder.getContext().authentication) {
                is JwtAuthenticationToken -> UUID.fromString(authentication.token.subject)

                // The Telegram bot, speaking for the user it resolved by Telegram id.
                is TelegramUserAuthentication -> authentication.userId

                else -> throw UnauthenticatedException("A valid bearer token is required")
            }
        return userService.getById(userId)
    }
}
