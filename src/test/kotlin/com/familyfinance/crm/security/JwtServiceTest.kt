package com.familyfinance.crm.security

import com.familyfinance.crm.ALMATY
import com.familyfinance.crm.config.AppProperties
import com.familyfinance.crm.domain.UserRole
import com.familyfinance.crm.idValue
import com.familyfinance.crm.user
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtServiceTest {
    private val secret = "a-test-secret-that-is-long-enough-for-hs256"
    private val issuedAt =
        LocalDate
            .of(2026, 9, 10)
            .atTime(12, 0)
            .atZone(ALMATY)
            .toInstant()
    private val service = JwtService(properties(), Clock.fixed(issuedAt, ALMATY))

    @Test
    fun `round-trips the principal`() {
        val subject = user(role = UserRole.MEMBER)

        val principal = service.parse(service.issue(subject).token)

        assertEquals(subject.idValue, principal?.id)
        assertEquals(subject.email, principal?.email)
        assertEquals(UserRole.MEMBER, principal?.role)
    }

    @Test
    fun `expires the token after the configured window`() {
        val token = service.issue(user()).token
        val later = JwtService(properties(), Clock.fixed(issuedAt.plus(Duration.ofDays(31)), ALMATY))

        assertNull(later.parse(token))
    }

    @Test
    fun `rejects a token signed with a different secret`() {
        val token =
            JwtService(properties(secret = "a-completely-different-secret-key-32b"), Clock.fixed(issuedAt, ALMATY))
                .issue(user())
                .token

        assertNull(service.parse(token))
    }

    @Test
    fun `rejects a garbage token`() {
        assertNull(service.parse("not-a-jwt"))
    }

    @Test
    fun `refuses to start with a secret that is too short for HS256`() {
        assertThrows<IllegalStateException> {
            JwtService(properties(secret = "too-short"), Clock.fixed(issuedAt, ALMATY))
        }
    }

    private fun properties(secret: String = this.secret) =
        AppProperties(
            timezone = ALMATY,
            jwt = AppProperties.JwtProperties(secret = secret, expiryDays = 30),
        )
}
