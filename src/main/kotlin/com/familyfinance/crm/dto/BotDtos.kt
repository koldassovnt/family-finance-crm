package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.UserRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Sent by the bot on `/start`, with what Telegram told it about the sender. */
data class TelegramLinkRequest(
    /** As Telegram reports it, with or without the `@`; matched ignoring case. */
    @field:NotBlank(message = "must not be blank")
    val telegramUsername: String,
    @field:NotNull(message = "is required")
    val telegramUserId: Long?,
    @field:NotNull(message = "is required")
    val chatId: Long?,
)

data class TelegramLinkResponse(
    val userId: UUID,
    val displayName: String,
    val role: UserRole,
)

/** One linked user's unpaid bills for the day, with where to send them. */
data class BillsDueResponse(
    val userId: UUID,
    val telegramUserId: Long,
    val chatId: Long,
    val displayName: String,
    val bills: List<BillDue>,
) {
    data class BillDue(
        val id: UUID,
        val name: String,
        val amount: BigDecimal,
        val currency: String,
        val dueDate: LocalDate,
    )
}
