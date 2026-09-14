package com.familyfinance.crm.security

import com.familyfinance.crm.exception.ErrorCode
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.NestedRuntimeException
import org.springframework.http.HttpStatus
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
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            val principal = bearerToken(request)?.let(jwtService::parse)
            if (principal != null) {
                val valid =
                    try {
                        stillValid(principal)
                    } catch (ex: NestedRuntimeException) {
                        // This filter runs outside DispatcherServlet, so an
                        // escaping exception would become a container error page
                        // rather than the JSON shape every client expects.
                        // NestedRuntimeException, not DataAccessException: a
                        // repository call that cannot even begin its transaction
                        // (pool exhausted, database unreachable) throws
                        // CannotCreateTransactionException, which is a sibling
                        // rather than a subclass.
                        log.error("Could not check token validity", ex)
                        response.writeError(
                            objectMapper,
                            HttpStatus.SERVICE_UNAVAILABLE,
                            ErrorResponse(ErrorCode.INTERNAL_ERROR, "Service temporarily unavailable"),
                        )
                        return
                    }
                if (valid) authenticate(principal, request)
            }
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
