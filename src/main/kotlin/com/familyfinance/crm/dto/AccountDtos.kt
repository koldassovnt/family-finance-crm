package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.AccountType
import com.familyfinance.crm.domain.BASE_CURRENCY
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

data class CreateAccountRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
    @field:NotNull(message = "is required")
    val type: AccountType?,
    /** Null for a `CASH` account, which has no bank. */
    val bankId: UUID? = null,
    /** Opening balance; defaults to zero. May be negative. */
    val balance: BigDecimal = BigDecimal.ZERO,
    @field:Size(min = 3, max = 3, message = "must be exactly 3 characters")
    val currency: String = BASE_CURRENCY,
)

/**
 * PATCH semantics: a null property means "absent, leave unchanged". `bankId`
 * is an [Optional] so an explicit `null` in the body can clear the bank,
 * which a plain nullable field could not express.
 */
data class UpdateAccountRequest(
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String? = null,
    val bankId: Optional<UUID>? = null,
)

data class ReconcileRequest(
    /** The balance the bank actually reports, in the account's currency. */
    @field:NotNull(message = "is required")
    val actualBalance: BigDecimal?,
    /** KZT per 1 unit of the account's currency; required for a non-KZT account. */
    val exchangeRate: BigDecimal? = null,
    @field:Size(max = 1000, message = "must be at most 1000 characters")
    val note: String? = null,
    /** Defaults to today in the app timezone. */
    val occurredOn: LocalDate? = null,
)

data class AccountResponse(
    val id: UUID,
    val name: String,
    val type: AccountType,
    val balance: BigDecimal,
    val currency: String,
    val bank: BankResponse?,
)
