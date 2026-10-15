package com.familyfinance.crm.service

import com.familyfinance.crm.config.AppProperties.BootstrapOwnerProperties
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.domain.UserRole
import com.familyfinance.crm.dto.CreateUserRequest
import com.familyfinance.crm.dto.UpdateMeRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.UnauthenticatedException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.fixedClock
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.UserRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.validation.Validation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServiceImplTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val service =
        UserServiceImpl(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            clock = fixedClock(),
            validator = Validation.buildDefaultValidatorFactory().validator,
        )

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
    fun `creates a member with a telegram username, adding the missing @`() {
        every { userRepository.existsByEmailIgnoreCase(request.email) } returns false
        every { userRepository.findByTelegramUsernameIgnoreCase("@member_tg") } returns null

        val created = service.createMember(request.copy(telegramUsername = "member_tg"))

        assertEquals("@member_tg", created.telegramUsername)
    }

    @Test
    fun `rejects a member whose telegram username another user has`() {
        every { userRepository.existsByEmailIgnoreCase(request.email) } returns false
        every { userRepository.findByTelegramUsernameIgnoreCase("@Member_TG") } returns user()

        assertThrows<ConflictException> { service.createMember(request.copy(telegramUsername = "@Member_TG")) }
    }

    @Test
    fun `sets your own telegram username, keeping the @`() {
        val existing = user()
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)
        every { userRepository.findByTelegramUsernameIgnoreCase("@owner_tg") } returns null

        service.updateMe(existing.idValue, UpdateMeRequest(telegramUsername = Optional.of(" @owner_tg ")))

        assertEquals("@owner_tg", existing.telegramUsername)
    }

    @Test
    fun `re-saving your own telegram username in another case is not a clash`() {
        val existing = user().apply { telegramUsername = "@owner_tg" }
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)
        every { userRepository.findByTelegramUsernameIgnoreCase("@Owner_TG") } returns existing

        service.updateMe(existing.idValue, UpdateMeRequest(telegramUsername = Optional.of("Owner_TG")))

        assertEquals("@Owner_TG", existing.telegramUsername)
    }

    @Test
    fun `an explicit null clears your telegram username`() {
        val existing = user().apply { telegramUsername = "@owner_tg" }
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)

        service.updateMe(existing.idValue, UpdateMeRequest(telegramUsername = Optional.empty()))

        assertNull(existing.telegramUsername)
    }

    @Test
    fun `leaving the telegram username out changes nothing`() {
        val existing = user().apply { telegramUsername = "@owner_tg" }
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)

        service.updateMe(existing.idValue, UpdateMeRequest())

        assertEquals("@owner_tg", existing.telegramUsername)
    }

    @Test
    fun `changing your telegram username unlinks the old Telegram account`() {
        val existing = user().apply { linkTelegram("@owner_tg") }
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)
        every { userRepository.findByTelegramUsernameIgnoreCase("@someone_else") } returns null

        service.updateMe(existing.idValue, UpdateMeRequest(telegramUsername = Optional.of("someone_else")))

        assertNull(existing.telegramUserId)
        assertNull(existing.telegramChatId)
    }

    @Test
    fun `clearing your telegram username unlinks the Telegram account`() {
        val existing = user().apply { linkTelegram("@owner_tg") }
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)

        service.updateMe(existing.idValue, UpdateMeRequest(telegramUsername = Optional.empty()))

        assertNull(existing.telegramUserId)
    }

    @Test
    fun `re-saving the same telegram username in another case keeps the link`() {
        val existing = user().apply { linkTelegram("@owner_tg") }
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)
        every { userRepository.findByTelegramUsernameIgnoreCase("@Owner_TG") } returns existing

        service.updateMe(existing.idValue, UpdateMeRequest(telegramUsername = Optional.of("Owner_TG")))

        assertEquals(123L, existing.telegramUserId)
    }

    @Test
    fun `links a Telegram account by username, adding the missing @`() {
        val existing = user().apply { telegramUsername = "@owner_tg" }
        every { userRepository.findByTelegramUsernameIgnoreCase("@owner_tg") } returns existing
        every { userRepository.findByTelegramUserId(123) } returns null

        service.linkTelegram(telegramUsername = "owner_tg", telegramUserId = 123, chatId = 456)

        assertEquals(123L, existing.telegramUserId)
        assertEquals(456L, existing.telegramChatId)
    }

    @Test
    fun `linking again refreshes the chat id`() {
        val existing = user().apply { linkTelegram("@owner_tg") }
        every { userRepository.findByTelegramUsernameIgnoreCase("@owner_tg") } returns existing
        every { userRepository.findByTelegramUserId(123) } returns existing

        service.linkTelegram(telegramUsername = "owner_tg", telegramUserId = 123, chatId = 789)

        assertEquals(789L, existing.telegramChatId)
    }

    @Test
    fun `refuses to link a username nobody has`() {
        every { userRepository.findByTelegramUsernameIgnoreCase("@stranger") } returns null

        assertThrows<NotFoundException> {
            service.linkTelegram(telegramUsername = "stranger", telegramUserId = 123, chatId = 456)
        }
    }

    @Test
    fun `refuses to link a Telegram account another user already has`() {
        val existing = user().apply { telegramUsername = "@owner_tg" }
        every { userRepository.findByTelegramUsernameIgnoreCase("@owner_tg") } returns existing
        every { userRepository.findByTelegramUserId(123) } returns user(email = "member@example.com")

        assertThrows<ConflictException> {
            service.linkTelegram(telegramUsername = "owner_tg", telegramUserId = 123, chatId = 456)
        }
    }

    private fun User.linkTelegram(username: String) {
        telegramUsername = username
        telegramUserId = 123
        telegramChatId = 456
    }

    @Test
    fun `rejects a telegram username Telegram itself would not allow`() {
        val existing = user()
        every { userRepository.findById(existing.idValue) } returns Optional.of(existing)

        val error =
            assertThrows<ValidationException> {
                service.updateMe(existing.idValue, UpdateMeRequest(telegramUsername = Optional.of("@abc")))
            }

        assertEquals(setOf("telegramUsername"), error.fieldErrors.keys)
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

    private val ownerConfig =
        BootstrapOwnerProperties(
            email = "owner@example.com",
            displayName = "Owner",
            password = "correct horse",
        )

    @Test
    fun `bootstraps the owner from config when none exists`() {
        every { userRepository.countByRole(UserRole.OWNER) } returns 0
        every { userRepository.existsByEmailIgnoreCase(ownerConfig.email) } returns false
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured.withId() }

        val result = service.bootstrapOwner(ownerConfig)

        assertEquals(OwnerBootstrap.Created("owner@example.com"), result)
        assertEquals(UserRole.OWNER, saved.captured.role)
        assertEquals("encoded", saved.captured.passwordHash)
    }

    @Test
    fun `leaves an existing owner alone even when config is set`() {
        every { userRepository.countByRole(UserRole.OWNER) } returns 1

        val result = service.bootstrapOwner(ownerConfig)

        assertEquals(OwnerBootstrap.AlreadyExists, result)
        verify(exactly = 0) { userRepository.save(any<User>()) }
    }

    @Test
    fun `reports that nobody can log in when there is no owner and no config`() {
        every { userRepository.countByRole(UserRole.OWNER) } returns 0

        val result = service.bootstrapOwner(BootstrapOwnerProperties())

        assertEquals(OwnerBootstrap.NotConfigured, result)
    }

    @Test
    fun `refuses to start with a bootstrap password the API would reject`() {
        every { userRepository.countByRole(UserRole.OWNER) } returns 0

        val error = assertThrows<IllegalStateException> { service.bootstrapOwner(ownerConfig.copy(password = "short")) }

        assertTrue(error.message.orEmpty().contains("password must be between 8 and 128 characters"))
    }

    @Test
    fun `refuses to start with a partial bootstrap config`() {
        every { userRepository.countByRole(UserRole.OWNER) } returns 0

        assertThrows<IllegalStateException> { service.bootstrapOwner(BootstrapOwnerProperties(email = "owner@example.com")) }
    }

    @Test
    fun `refuses to bootstrap an owner whose email a member already has`() {
        every { userRepository.countByRole(UserRole.OWNER) } returns 0
        every { userRepository.existsByEmailIgnoreCase(ownerConfig.email) } returns true

        assertThrows<IllegalStateException> { service.bootstrapOwner(ownerConfig) }
    }

    @Test
    fun `never prints the bootstrap password`() {
        assertFalse(ownerConfig.toString().contains(ownerConfig.password))
    }
}
