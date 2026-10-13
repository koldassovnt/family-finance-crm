package com.familyfinance.crm.exception

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
        respond(ex.status, ErrorResponse(code = ex.code, message = ex.message, fieldErrors = ex.fieldErrors))

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
        respond(HttpStatus.UNAUTHORIZED, ErrorResponse(ErrorCode.UNAUTHENTICATED, "Authentication is required"))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.FORBIDDEN, ErrorResponse(ErrorCode.FORBIDDEN, "You are not allowed to perform this action"))

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ErrorResponse(ErrorCode.NOT_FOUND, "No endpoint at ${ex.resourcePath}"))

    /**
     * A unique index losing a race — two concurrent requests both inserting the
     * one budget version allowed to be open, say. The outcome is a conflict, not
     * a server fault, so it must not read as one.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("Constraint violation", ex)
        return respond(
            HttpStatus.CONFLICT,
            ErrorResponse(ErrorCode.CONFLICT, "That change conflicts with the current state; try again"),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", ex)
        return respond(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorResponse(ErrorCode.INTERNAL_ERROR, "Something went wrong"),
        )
    }

    private fun badRequest(
        code: ErrorCode,
        message: String,
        fieldErrors: Map<String, String>,
    ): ResponseEntity<ErrorResponse> = respond(HttpStatus.BAD_REQUEST, ErrorResponse(code, message, fieldErrors))

    /**
     * An error is JSON whatever the request's `Accept` asked for. Left to
     * negotiation, a failed file download — whose client accepts only the
     * file's type — could not be answered at all and surfaced as a bare 500.
     */
    private fun respond(
        status: HttpStatus,
        body: ErrorResponse,
    ): ResponseEntity<ErrorResponse> = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)
}
