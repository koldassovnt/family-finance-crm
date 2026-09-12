package com.familyfinance.crm.web

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.exception.UnauthenticatedException
import com.familyfinance.crm.security.AuthenticatedUser
import com.familyfinance.crm.service.UserService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * The JWT principal carries only an id; services work with the entity. This is
 * the one place that bridges the two, so controllers don't each repeat it.
 */
@Component
class CurrentUserProvider(
    private val userService: UserService,
) {
    fun require(): User {
        val principal =
            SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
                ?: throw UnauthenticatedException("A valid bearer token is required")
        return userService.getById(principal.id)
    }
}
