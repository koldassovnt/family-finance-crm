package com.familyfinance.crm.web

import com.familyfinance.crm.dto.BankResponse
import com.familyfinance.crm.dto.CreateBankRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.service.BankService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/banks")
@Tag(name = "Banks", description = "Shared bank lookup list, self-service")
class BankController(
    private val bankService: BankService,
) {
    @GetMapping
    @Operation(summary = "List banks", description = "Banks are global, not per-user. No pagination — the list is small.")
    @ApiResponse(responseCode = "200", description = "The bank list")
    fun list(): List<BankResponse> = bankService.listAll().map { it.toResponse() }

    @PostMapping
    @Operation(
        summary = "Find or create a bank",
        description = "Returns the existing bank when the name already exists (case-insensitive), otherwise creates it.",
    )
    @ApiResponse(responseCode = "200", description = "The existing or newly created bank")
    fun findOrCreate(
        @Valid @RequestBody request: CreateBankRequest,
    ): BankResponse = bankService.findOrCreate(request.name).toResponse()
}
