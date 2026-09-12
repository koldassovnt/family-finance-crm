package com.familyfinance.crm.exception

import org.springframework.http.HttpStatus

/**
 * Expected business failures. Every subclass maps to exactly one HTTP status
 * and one [ErrorCode]; the translation to a response body lives solely in
 * [GlobalExceptionHandler].
 */
sealed class ApiException(
    val code: ErrorCode,
    val status: HttpStatus,
    override val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
) : RuntimeException(message)

class NotFoundException(
    message: String,
) : ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message)

class ForbiddenException(
    message: String,
) : ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message)

class UnauthenticatedException(
    message: String,
) : ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, message)

class ConflictException(
    message: String,
) : ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message)

class CurrencyMismatchException(
    message: String,
) : ApiException(ErrorCode.CURRENCY_MISMATCH, HttpStatus.BAD_REQUEST, message)

/** One active budget per category per person — see `phase-2-budgets-goals.md`. */
class DuplicateBudgetException(
    message: String,
) : ApiException(ErrorCode.DUPLICATE_BUDGET, HttpStatus.CONFLICT, message)

class ValidationException(
    message: String,
    fieldErrors: Map<String, String> = emptyMap(),
) : ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message, fieldErrors)

/** Shorthand for the "wrong field, one message" case, which is most of them. */
fun invalidField(
    field: String,
    message: String,
): ValidationException = ValidationException(message, mapOf(field to message))
