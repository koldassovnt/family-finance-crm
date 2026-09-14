package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LoginRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Email(message = "must be a valid email address")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val email: String,
    @field:NotBlank(message = "must not be blank")
    val password: String,
)

data class LoginResponse(
    val token: String,
    val expiresAt: Instant,
    val user: UserResponse,
)

data class CreateUserRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Email(message = "must be a valid email address")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val email: String,
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val displayName: String,
    @field:NotBlank(message = "must not be blank")
    @field:Size(min = 8, max = 128, message = "must be between 8 and 128 characters")
    val password: String,
    /** Optional; only `MEMBER` is accepted — there is exactly one `OWNER`. */
    val role: UserRole? = null,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val createdAt: Instant?,
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "must not be blank")
    val currentPassword: String,
    @field:NotBlank(message = "must not be blank")
    @field:Size(min = 8, max = 128, message = "must be between 8 and 128 characters")
    val newPassword: String,
)
