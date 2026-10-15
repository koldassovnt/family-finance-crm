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
    DUPLICATE_BUDGET,

    /** The bot spoke for a Telegram account no user has linked — reply "send /start". */
    TELEGRAM_NOT_LINKED,
    INTERNAL_ERROR,
}
