package com.familyfinance.crm.service

import com.familyfinance.crm.domain.User
import com.familyfinance.crm.domain.UserRole
import com.familyfinance.crm.dto.CreateUserRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.UnauthenticatedException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.UserRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.test.assertEquals

class UserServiceImplTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val service = UserServiceImpl(userRepository, passwordEncoder, fixedClock())

    private val request =
        CreateUserRequest(
            email = "member@example.com",
            displayName = "Member",
            password = "correct horse",
        )

    init {
        every { userRepository.save(any<User>()) } answers { firstArg<User>().withId() }
        every { passwordEncoder.encode(any()) } returns "encoded"
    }

    @Test
    fun `creates a member`() {
        every { userRepository.existsByEmailIgnoreCase(request.email) } returns false

        val created = service.createMember(request)

        assertEquals(UserRole.MEMBER, created.role)
        assertEquals("encoded", created.passwordHash)
    }

    @Test
    fun `refuses to create a second owner`() {
        val error =
            assertThrows<ValidationException> {
                service.createMember(request.copy(role = UserRole.OWNER))
            }

        assertEquals(setOf("role"), error.fieldErrors.keys)
    }

    @Test
    fun `rejects an email that is already taken`() {
        every { userRepository.existsByEmailIgnoreCase(request.email) } returns true

        assertThrows<ConflictException> { service.createMember(request) }
    }

    @Test
    fun `changing a password moves passwordChangedAt forward, invalidating old tokens`() {
        val existing = user()
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)
        every { passwordEncoder.matches("old-password", existing.passwordHash) } returns true
        every { passwordEncoder.matches("new-password", existing.passwordHash) } returns false

        service.changePassword(existing.idValue, "old-password", "new-password")

        assertEquals("encoded", existing.passwordHash)
        assertEquals(fixedClock().instant(), existing.passwordChangedAt)
    }

    @Test
    fun `rejects a password change with the wrong current password`() {
        val existing = user()
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)
        every { passwordEncoder.matches("wrong", existing.passwordHash) } returns false

        assertThrows<ValidationException> {
            service.changePassword(existing.idValue, "wrong", "new-password")
        }
    }

    @Test
    fun `rejects a new password identical to the current one`() {
        val existing = user()
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)
        every { passwordEncoder.matches("same-password", existing.passwordHash) } returns true

        assertThrows<ValidationException> {
            service.changePassword(existing.idValue, "same-password", "same-password")
        }
    }

    @Test
    fun `rejects a wrong password`() {
        val existing = user(email = "owner@example.com")
        every { userRepository.findByEmailIgnoreCase(existing.email) } returns existing
        every { passwordEncoder.matches("wrong", existing.passwordHash) } returns false

        assertThrows<UnauthenticatedException> { service.authenticate(existing.email, "wrong") }
    }

    @Test
    fun `rejects an unknown email with the same error as a wrong password`() {
        every { userRepository.findByEmailIgnoreCase("nobody@example.com") } returns null

        val error =
            assertThrows<UnauthenticatedException> { service.authenticate("nobody@example.com", "whatever") }

        assertEquals("Email or password is incorrect", error.message)
    }
}
