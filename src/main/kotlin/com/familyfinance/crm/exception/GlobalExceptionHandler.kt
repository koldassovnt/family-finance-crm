package com.familyfinance.crm.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/** The only place an exception becomes an HTTP response body. */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(ex.status)
            .body(ErrorResponse(code = ex.code, message = ex.message, fieldErrors = ex.fieldErrors))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBeanValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors =
            ex.bindingResult.fieldErrors.associate { error ->
                error.field to (error.defaultMessage ?: "is invalid")
            }
        return badRequest(
            ErrorCode.VALIDATION_FAILED,
            fieldErrors.entries.firstOrNull()?.let { "${it.key} ${it.value}" } ?: "Request is invalid",
            fieldErrors,
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val fieldErrors =
            ex.constraintViolations.associate { violation ->
                violation.propertyPath.last().name to violation.message
            }
        return badRequest(ErrorCode.VALIDATION_FAILED, "Request is invalid", fieldErrors)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
        badRequest(
            ErrorCode.VALIDATION_FAILED,
            "Required parameter '${ex.parameterName}' is missing",
            mapOf(ex.parameterName to "is required"),
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        badRequest(
            ErrorCode.VALIDATION_FAILED,
            "Parameter '${ex.name}' has an invalid value",
            mapOf(ex.name to "is invalid"),
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.debug("Unreadable request body", ex)
        return badRequest(ErrorCode.VALIDATION_FAILED, "Request body is malformed or missing", emptyMap())
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ErrorCode.UNAUTHENTICATED, "Authentication is required"))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ErrorCode.FORBIDDEN, "You are not allowed to perform this action"))

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ErrorCode.NOT_FOUND, "No endpoint at ${ex.resourcePath}"))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ErrorCode.INTERNAL_ERROR, "Something went wrong"))
    }

    private fun badRequest(
        code: ErrorCode,
        message: String,
        fieldErrors: Map<String, String>,
    ): ResponseEntity<ErrorResponse> = ResponseEntity.badRequest().body(ErrorResponse(code, message, fieldErrors))
}
