package com.familyfinance.crm.security

import com.familyfinance.crm.exception.ErrorCode
import com.familyfinance.crm.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

/**
 * Security rejects a request before it reaches a controller, so
 * `@RestControllerAdvice` never sees it. These two keep those responses in the
 * same shape as every other error.
 */
@Component
class JsonAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = response.writeError(
        objectMapper,
        HttpStatus.UNAUTHORIZED,
        ErrorResponse(ErrorCode.UNAUTHENTICATED, "A valid bearer token is required"),
    )
}

@Component
class JsonAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = response.writeError(
        objectMapper,
        HttpStatus.FORBIDDEN,
        ErrorResponse(ErrorCode.FORBIDDEN, "You are not allowed to perform this action"),
    )
}

private fun HttpServletResponse.writeError(
    objectMapper: ObjectMapper,
    httpStatus: HttpStatus,
    body: ErrorResponse,
) {
    status = httpStatus.value()
    contentType = MediaType.APPLICATION_JSON_VALUE
    characterEncoding = Charsets.UTF_8.name()
    objectMapper.writeValue(outputStream, body)
}
