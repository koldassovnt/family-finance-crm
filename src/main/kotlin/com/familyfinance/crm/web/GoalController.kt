package com.familyfinance.crm.web

import com.familyfinance.crm.dto.CreateGoalRequest
import com.familyfinance.crm.dto.GoalResponse
import com.familyfinance.crm.dto.UpdateGoalRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.GoalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Goals", description = "Savings targets tracked against a linked account's balance")
class GoalController(
    private val goalService: GoalService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "List your goals with computed progress",
        description =
            "Progress is the linked account's balance against the target, clamped to 0–100. " +
                "`achieved` is derived, never a stored state — an achieved goal can still be abandoned.",
    )
    @ApiResponse(responseCode = "200", description = "Your goals")
    fun list(): List<GoalResponse> = goalService.list(currentUser.require()).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a goal",
        description =
            "The linked account must be one of yours; several goals may share one account. " +
                "There is no contribute endpoint — pay into the account with an ordinary transaction.",
    )
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(
        responseCode = "404",
        description = "No such account",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun create(
        @Valid @RequestBody request: CreateGoalRequest,
    ): GoalResponse = goalService.create(currentUser.require(), request).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update a goal",
        description =
            "Name, target amount, target date, or status. The linked account is fixed. " +
                "Send `targetDate: null` to clear the deadline.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateGoalRequest,
    ): GoalResponse = goalService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft delete a goal", description = "Frees the linked account to be deleted.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    fun delete(
        @PathVariable id: UUID,
    ) = goalService.softDelete(id, currentUser.require())
}
