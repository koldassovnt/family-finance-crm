package com.familyfinance.crm.security

import com.familyfinance.crm.config.AppProperties
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.repository.UserRepository
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.slf4j.LoggerFactory
import org.springframework.core.NestedRuntimeException
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Single long-lived token, no refresh and no server-side store — logout is
 * client-side and a token can't be revoked before it expires, except by a
 * password change. See `00-`. Verification is Spring's resource server; this
 * supplies the key, the clock, and that one revocation rule.
 */
@Component
class JwtService(
    properties: AppProperties,
    private val clock: Clock,
    private val userRepository: UserRepository,
) {
    private val key: SecretKey = signingKey(properties.jwt.secret)
    private val expiry: Duration = Duration.ofDays(properties.jwt.expiryDays)
    private val encoder = NimbusJwtEncoder(ImmutableSecret(key))
    private val log = LoggerFactory.getLogger(javaClass)

    /** Rejects a malformed, foreign-signed, expired, or password-revoked token. */
    val decoder: JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
            // Zero skew, as before: a token is dead the second it expires.
            val timestamps = JwtTimestampValidator(Duration.ZERO).apply { setClock(clock) }
            setJwtValidator(DelegatingOAuth2TokenValidator(timestamps, notRevoked()))
        }

    fun issue(user: User): IssuedToken {
        val userId = requireNotNull(user.id) { "Cannot issue a token for an unsaved user" }
        val issuedAt = clock.instant()
        val expiresAt = issuedAt.plus(expiry)
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .claim(EMAIL_CLAIM, user.email)
                .claim(ROLE_CLAIM, user.role.name)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedToken(token = token, expiresAt = expiresAt)
    }

    /**
     * Changing a password moves `passwordChangedAt` forward, which invalidates
     * every token issued before it. A database failure here is an
     * [AuthenticationServiceException] — a 503, not a rejected token.
     */
    private fun notRevoked() =
        OAuth2TokenValidator<Jwt> { jwt ->
            val id = jwt.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val issuedAt = jwt.issuedAt
            val changedAt =
                id?.let {
                    try {
                        userRepository.findPasswordChangedAt(it)
                    } catch (ex: NestedRuntimeException) {
                        // Not DataAccessException: a pool that cannot even begin a
                        // transaction throws CannotCreateTransactionException, a sibling.
                        log.error("Could not check token validity", ex)
                        throw AuthenticationServiceException("Could not check token validity", ex)
                    }
                }
            if (issuedAt != null && changedAt != null && !issuedAt.isBefore(changedAt.truncatedTo(ChronoUnit.SECONDS))) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Token is no longer valid", null))
            }
        }

    data class IssuedToken(
        val token: String,
        val expiresAt: Instant,
    )
}

/** The claim `SecurityConfig` turns into `ROLE_OWNER` / `ROLE_MEMBER`. */
const val ROLE_CLAIM = "role"
private const val EMAIL_CLAIM = "email"
private const val MIN_SECRET_BYTES = 32

private fun signingKey(secret: String): SecretKey {
    val bytes = secret.toByteArray()
    check(bytes.size >= MIN_SECRET_BYTES) {
        "app.jwt.secret must be at least $MIN_SECRET_BYTES bytes for HS256 (env JWT_SECRET)"
    }
    return SecretKeySpec(bytes, "HmacSHA256")
}
