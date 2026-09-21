package com.familyfinance.crm.security

import com.familyfinance.crm.ALMATY
import com.familyfinance.crm.config.AppProperties
import com.familyfinance.crm.domain.UserRole
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.UserRepository
import com.familyfinance.crm.user
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals

class JwtServiceTest {
    private val secret = "a-test-secret-that-is-long-enough-for-hs256"
    private val issuedAt =
        LocalDate
            .of(2026, 9, 10)
            .atTime(12, 0)
            .atZone(ALMATY)
            .toInstant()
    private val userRepository =
        mockk<UserRepository> {
            every { findPasswordChangedAt(any()) } returns issuedAt.minus(Duration.ofDays(1))
        }
    private val service = service()

    @Test
    fun `round-trips the subject and role`() {
        val subject = user(role = UserRole.MEMBER)

        val jwt = service.decoder.decode(service.issue(subject).token)

        assertEquals(subject.idValue.toString(), jwt.subject)
        assertEquals("MEMBER", jwt.getClaimAsString(ROLE_CLAIM))
    }

    @Test
    fun `expires the token after the configured window`() {
        val token = service.issue(user()).token
        val later = service(clock = Clock.fixed(issuedAt.plus(Duration.ofDays(31)), ALMATY))

        assertThrows<JwtException> { later.decoder.decode(token) }
    }

    @Test
    fun `rejects a token issued before the password was last changed`() {
        val token = service.issue(user()).token
        every { userRepository.findPasswordChangedAt(any()) } returns issuedAt.plusSeconds(1)

        assertThrows<JwtException> { service.decoder.decode(token) }
    }

    @Test
    fun `reports a database failure as a service error rather than a bad token`() {
        val token = service.issue(user()).token
        every { userRepository.findPasswordChangedAt(any()) } throws DataAccessResourceFailureException("down")

        assertThrows<AuthenticationServiceException> { service.decoder.decode(token) }
    }

    @Test
    fun `rejects a token signed with a different secret`() {
        val token = service(secret = "a-completely-different-secret-key-32b").issue(user()).token

        assertThrows<JwtException> { service.decoder.decode(token) }
    }

    @Test
    fun `rejects a garbage token`() {
        assertThrows<JwtException> { service.decoder.decode("not-a-jwt") }
    }

    @Test
    fun `refuses to start with a secret that is too short for HS256`() {
        assertThrows<IllegalStateException> { service(secret = "too-short") }
    }

    private fun service(
        secret: String = this.secret,
        clock: Clock = Clock.fixed(issuedAt, ALMATY),
    ) = JwtService(
        properties =
            AppProperties(
                timezone = ALMATY,
                jwt = AppProperties.JwtProperties(secret = secret, expiryDays = 30),
            ),
        clock = clock,
        userRepository = userRepository,
    )
}
