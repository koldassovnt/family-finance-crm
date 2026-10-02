package com.familyfinance.crm.service

import com.familyfinance.crm.config.AppProperties.BootstrapOwnerProperties
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.domain.UserRole
import com.familyfinance.crm.dto.CreateUserRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.UnauthenticatedException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.UserRepository
import jakarta.validation.Validator
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
    private val validator: Validator,
) : UserService {
    @Transactional(readOnly = true)
    override fun getById(id: UUID): User = userRepository.findById(id).orElseThrow { NotFoundException("User $id was not found") }

    @Transactional(readOnly = true)
    override fun listMembers(): List<User> = userRepository.findAllOrdered()

    @Transactional(readOnly = true)
    override fun authenticate(
        email: String,
        password: String,
    ): User {
        val user = userRepository.findByEmailIgnoreCase(email)
        // Same message either way — never reveal which half was wrong.
        if (user == null || !passwordEncoder.matches(password, user.passwordHash)) {
            throw UnauthenticatedException("Email or password is incorrect")
        }
        return user
    }

    @Transactional
    override fun createMember(request: CreateUserRequest): User {
        if (request.role != null && request.role != UserRole.MEMBER) {
            throw invalidField("role", "must be MEMBER — there is exactly one OWNER")
        }
        if (userRepository.existsByEmailIgnoreCase(request.email)) {
            throw ConflictException("A user with email ${request.email} already exists")
        }
        return userRepository.save(
            User(
                email = request.email,
                displayName = request.displayName,
                passwordHash = passwordEncoder.encode(request.password),
                role = UserRole.MEMBER,
                passwordChangedAt = clock.instant(),
            ),
        )
    }

    @Transactional
    override fun bootstrapOwner(config: BootstrapOwnerProperties): OwnerBootstrap {
        if (userRepository.countByRole(UserRole.OWNER) > 0) return OwnerBootstrap.AlreadyExists
        if (!config.isConfigured) return OwnerBootstrap.NotConfigured

        // Same rules as any other user, so the OWNER cannot get a password the
        // API would have refused.
        val request =
            CreateUserRequest(
                email = config.email.trim(),
                displayName = config.displayName.trim(),
                password = config.password,
            )
        val problems = validator.validate(request).map { "${it.propertyPath} ${it.message}" }.sorted()
        check(problems.isEmpty()) {
            "Bootstrap owner (OWNER_EMAIL, OWNER_DISPLAY_NAME, OWNER_PASSWORD) is invalid: ${problems.joinToString("; ")}"
        }
        check(!userRepository.existsByEmailIgnoreCase(request.email)) {
            "Bootstrap owner email ${request.email} already belongs to another user"
        }

        userRepository.save(
            User(
                email = request.email,
                displayName = request.displayName,
                passwordHash = passwordEncoder.encode(request.password),
                role = UserRole.OWNER,
                passwordChangedAt = clock.instant(),
            ),
        )
        return OwnerBootstrap.Created(request.email)
    }

    @Transactional
    override fun changePassword(
        id: UUID,
        currentPassword: String,
        newPassword: String,
    ) {
        val user = getById(id)
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw invalidField("currentPassword", "is incorrect")
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash)) {
            throw invalidField("newPassword", "must differ from the current password")
        }
        user.passwordHash = passwordEncoder.encode(newPassword)
        // Logs out every other session: tokens issued before now stop working.
        user.passwordChangedAt = clock.instant()
    }
}
