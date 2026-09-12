package com.familyfinance.crm.security

import com.familyfinance.crm.config.AppProperties
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.domain.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Single long-lived token, no refresh and no server-side store — logout is
 * client-side and a token can't be revoked before it expires. See `00-`.
 */
@Component
class JwtService(
    properties: AppProperties,
    private val clock: Clock,
) {
    private val key: SecretKey = signingKey(properties.jwt.secret)
    private val expiry: Duration = Duration.ofDays(properties.jwt.expiryDays)

    fun issue(user: User): IssuedToken {
        val userId = requireNotNull(user.id) { "Cannot issue a token for an unsaved user" }
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(expiry)
        val token =
            Jwts
                .builder()
                .subject(userId.toString())
                .claim(EMAIL_CLAIM, user.email)
                .claim(ROLE_CLAIM, user.role.name)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact()
        return IssuedToken(token = token, expiresAt = expiresAt)
    }

    /** Returns null for any token that is malformed, unsigned, or expired. */
    fun parse(token: String): AuthenticatedUser? =
        try {
            toPrincipal(
                Jwts
                    .parser()
                    .verifyWith(key)
                    .clock { Date.from(clock.instant()) }
                    .build()
                    .parseSignedClaims(token)
                    .payload,
            )
        } catch (ex: JwtException) {
            null
        } catch (ex: IllegalArgumentException) {
            null
        }

    private fun toPrincipal(claims: Claims): AuthenticatedUser? {
        val id = claims.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val email = claims[EMAIL_CLAIM] as? String ?: return null
        val role = (claims[ROLE_CLAIM] as? String)?.let { name -> UserRole.entries.find { it.name == name } }
        return role?.let { AuthenticatedUser(id = id, email = email, role = it) }
    }

    data class IssuedToken(
        val token: String,
        val expiresAt: Instant,
    )
}

private const val EMAIL_CLAIM = "email"
private const val ROLE_CLAIM = "role"
private const val MIN_SECRET_BYTES = 32

private fun signingKey(secret: String): SecretKey {
    val bytes = secret.toByteArray()
    check(bytes.size >= MIN_SECRET_BYTES) {
        "app.jwt.secret must be at least $MIN_SECRET_BYTES bytes for HS256 (env JWT_SECRET)"
    }
    return Keys.hmacShaKeyFor(bytes)
}
