package com.familyfinance.crm.exception

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class ErrorResponse(
    val code: ErrorCode,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)
