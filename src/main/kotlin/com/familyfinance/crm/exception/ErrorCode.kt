package com.familyfinance.crm.exception

/**
 * Stable, machine-readable codes the frontend branches on. Never localized,
 * never renamed once a client depends on one.
 */
enum class ErrorCode {
    VALIDATION_FAILED,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    CURRENCY_MISMATCH,
    INTERNAL_ERROR,
}
