package com.familyfinance.crm.domain

/**
 * The single accounting currency. Accounts may hold any currency, but every
 * number the reporting endpoints add up is in this one — see `Transaction.amountKzt`.
 */
const val BASE_CURRENCY = "KZT"

const val CURRENCY_CODE_LENGTH = 3
