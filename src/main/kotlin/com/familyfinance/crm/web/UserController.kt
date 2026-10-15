package com.familyfinance.crm.web

import com.familyfinance.crm.dto.ChangePasswordRequest
import com.familyfinance.crm.dto.CreateUserRequest
import com.familyfinance.crm.dto.UpdateMeRequest
import com.familyfinance.crm.dto.UserResponse
import com.familyfinance.crm.dto.requiredId
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "The household: owner-only provisioning, plus the member list sharing needs")
class UserController(
    private val userService: UserService,
    private val currentUser: CurrentUserProvider,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    @Operation(
        summary = "Create a member",
        description = "Owner-only. Can only create a MEMBER — the single OWNER is created by the bootstrap step.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(
        responseCode = "409",
        description = "Email already in use",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun create(
        @Valid @RequestBody request: CreateUserRequest,
    ): UserResponse = userService.createMember(request).toResponse()

    @GetMapping
    @Operation(
        summary = "The household",
        description =
            "Every member, for any authenticated caller — sharing needs somebody to share with, and in " +
                "a household of a few people who already know each other's names, hiding the list would " +
                "protect nothing. Includes you; sharing with yourself is rejected anyway.",
    )
    @ApiResponse(responseCode = "200", description = "The members")
    fun list(): List<UserResponse> = userService.listMembers().map { it.toResponse() }

    @GetMapping("/me")
    @Operation(
        summary = "Who you are",
        description =
            "The caller's own id, email, display name and role. There is no refresh flow, so a client " +
                "holding a stored token uses this to re-establish identity after a reload.",
    )
    @ApiResponse(responseCode = "200", description = "The authenticated user")
    fun me(): UserResponse = currentUser.require().toResponse()

    @PatchMapping("/me")
    @Operation(
        summary = "Update your own profile",
        description =
            "Any role, for your own account only. `telegramUsername` is accepted with or without its `@` " +
                "and stored with it; an explicit null clears it, leaving it out changes nothing.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(
        responseCode = "400",
        description = "Not a valid Telegram username",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "Telegram username already belongs to another user",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun updateMe(
        @RequestBody request: UpdateMeRequest,
    ): UserResponse = userService.updateMe(id = currentUser.require().requiredId(), request = request).toResponse()

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Change your own password",
        description =
            "Any role, for your own account only. Also invalidates every token issued before now, " +
                "including the one making this call — log in again afterwards.",
    )
    @ApiResponse(responseCode = "204", description = "Changed; existing tokens are now invalid")
    @ApiResponse(
        responseCode = "400",
        description = "Current password is wrong, or the new one is unchanged",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
    ) = userService.changePassword(
        id = currentUser.require().requiredId(),
        currentPassword = request.currentPassword,
        newPassword = request.newPassword,
    )
}
