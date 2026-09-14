package com.familyfinance.crm.service

import com.familyfinance.crm.domain.BASE_CURRENCY
import com.familyfinance.crm.domain.CURRENCY_CODE_LENGTH
import com.familyfinance.crm.exception.invalidField

/**
 * Shared field rules for the service layer. Bean validation covers the create
 * DTOs, but a PATCH field is optional — `@NotBlank` would reject an absent
 * value, so "present but blank" has to be caught here instead.
 */
fun requireNonBlankName(
    value: String,
    field: String = "name",
): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) throw invalidField(field, "must not be blank")
    return trimmed
}

/** Currency is a plain 3-letter code, stored uppercase. */
fun normalizeCurrency(currency: String): String {
    val normalized = currency.trim().uppercase()
    if (normalized.length != CURRENCY_CODE_LENGTH) {
        throw invalidField("currency", "must be exactly $CURRENCY_CODE_LENGTH characters")
    }
    return normalized
}

/** The single accounting currency; anything else needs an explicit rate. */
fun isBaseCurrency(currency: String): Boolean = currency == BASE_CURRENCY
