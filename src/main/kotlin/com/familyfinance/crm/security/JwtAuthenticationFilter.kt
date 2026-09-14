package com.familyfinance.crm.security

import com.familyfinance.crm.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.temporal.ChronoUnit

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            bearerToken(request)
                ?.let(jwtService::parse)
                ?.takeIf(::stillValid)
                ?.let { principal -> authenticate(principal, request) }
        }
        filterChain.doFilter(request, response)
    }

    /**
     * There is no token store, so a token cannot be revoked individually — but
     * changing a password moves `passwordChangedAt` forward, which invalidates
     * every token issued before it.
     */
    private fun stillValid(principal: AuthenticatedUser): Boolean {
        val changedAt = userRepository.findPasswordChangedAt(principal.id) ?: return false
        return !principal.issuedAt.isBefore(changedAt.truncatedTo(ChronoUnit.SECONDS))
    }

    private fun authenticate(
        principal: AuthenticatedUser,
        request: HttpServletRequest,
    ) {
        val authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun bearerToken(request: HttpServletRequest): String? =
        request
            .getHeader("Authorization")
            ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

private const val BEARER_PREFIX = "Bearer "
