package com.familyfinance.crm.web

import com.familyfinance.crm.dto.ChangePasswordRequest
import com.familyfinance.crm.dto.CreateUserRequest
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Owner-only user provisioning")
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
