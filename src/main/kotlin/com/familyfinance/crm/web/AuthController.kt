package com.familyfinance.crm.web

import com.familyfinance.crm.dto.LoginRequest
import com.familyfinance.crm.dto.LoginResponse
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.security.JwtService
import com.familyfinance.crm.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Log in and receive a bearer token")
class AuthController(
    private val userService: UserService,
    private val jwtService: JwtService,
) {
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
        summary = "Log in",
        description = "Exchanges email and password for a 30-day JWT. There is no refresh token; log in again on expiry.",
    )
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(
        responseCode = "401",
        description = "Email or password is incorrect",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): LoginResponse {
        val user = userService.authenticate(email = request.email, password = request.password)
        val issued = jwtService.issue(user)
        return LoginResponse(token = issued.token, expiresAt = issued.expiresAt, user = user.toResponse())
    }
}
