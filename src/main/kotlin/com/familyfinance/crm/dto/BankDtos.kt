package com.familyfinance.crm.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateBankRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
)

data class BankResponse(
    val id: UUID,
    val name: String,
)
