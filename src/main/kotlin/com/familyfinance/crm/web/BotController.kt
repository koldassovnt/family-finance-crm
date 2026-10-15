package com.familyfinance.crm.web

import com.familyfinance.crm.dto.BillsDueResponse
import com.familyfinance.crm.dto.TelegramLinkRequest
import com.familyfinance.crm.dto.TelegramLinkResponse
import com.familyfinance.crm.dto.toBillsDueResponse
import com.familyfinance.crm.dto.toTelegramLinkResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.BillService
import com.familyfinance.crm.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/bot")
@Tag(
    name = "Telegram bot",
    description =
        "Only for the bot, authenticated by X-Bot-Token. Elsewhere it acts for a linked user by adding " +
            "X-Telegram-User-Id, on transactions, accounts, categories, bills and planned purchases only.",
)
class BotController(
    private val userService: UserService,
    private val billService: BillService,
) {
    @PostMapping("/link")
    @Operation(
        summary = "Link a Telegram account on /start",
        description =
            "Finds the user whose Telegram username matches (with or without @, ignoring case) and records " +
                "the Telegram user id and chat id. Repeating it refreshes the chat id.",
    )
    @ApiResponse(responseCode = "200", description = "Linked")
    @ApiResponse(
        responseCode = "404",
        description = "No user has this Telegram username",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "This Telegram account is already linked to another user",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun link(
        @Valid @RequestBody request: TelegramLinkRequest,
    ): TelegramLinkResponse =
        userService
            .linkTelegram(
                telegramUsername = request.telegramUsername,
                telegramUserId = checkNotNull(request.telegramUserId) { "validated as required" },
                chatId = checkNotNull(request.chatId) { "validated as required" },
            ).toTelegramLinkResponse()

    @GetMapping("/bills-due")
    @Operation(
        summary = "Bills to remind people about",
        description =
            "Unpaid bills due on `date` (today in the app timezone when omitted), grouped by linked owner. " +
                "Only each person's own bills; users with nothing due are left out.",
    )
    @ApiResponse(responseCode = "200", description = "One entry per linked user with something due")
    fun billsDue(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    ): List<BillsDueResponse> = billService.dueForTelegramReminders(date).map { (owner, bills) -> owner.toBillsDueResponse(bills) }
}
