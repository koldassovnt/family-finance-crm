package com.familyfinance.crm.web

import com.familyfinance.crm.dto.CreateUserRequest
import com.familyfinance.crm.dto.UserResponse
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
}
