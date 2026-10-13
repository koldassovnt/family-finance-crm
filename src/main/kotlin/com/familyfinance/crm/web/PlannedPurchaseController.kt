package com.familyfinance.crm.web

import com.familyfinance.crm.dto.CreatePlannedPurchaseRequest
import com.familyfinance.crm.dto.PlannedPurchaseResponse
import com.familyfinance.crm.dto.UpdatePlannedPurchaseRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.service.PlannedPurchaseService
import io.swagger.v3.oas.annotations.Operation
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
@RequestMapping("/api/v1/planned-purchases")
@Tag(name = "Planned purchases", description = "Things to buy: your own, and the family's")
class PlannedPurchaseController(
    private val plannedPurchaseService: PlannedPurchaseService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "List planned purchases",
        description =
            "Your personal items and every family item, whoever added it. Still to buy first, then " +
                "newest first. `isFamily` tells the two lists apart.",
    )
    @ApiResponse(responseCode = "200", description = "The items")
    fun list(): List<PlannedPurchaseResponse> = plannedPurchaseService.list(currentUser.require()).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Add a planned purchase",
        description = "Personal unless `isFamily` is true. A family item is seen, ticked off and removed by anyone.",
    )
    @ApiResponse(responseCode = "201", description = "Added, not bought yet")
    fun create(
        @Valid @RequestBody request: CreatePlannedPurchaseRequest,
    ): PlannedPurchaseResponse = plannedPurchaseService.create(currentUser.require(), request).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        summary = "Mark bought, or rename",
        description =
            "`isBought` true or false, and `name`. Any member may change a family item; a personal one " +
                "only whoever added it. Marking bought does not create a transaction.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdatePlannedPurchaseRequest,
    ): PlannedPurchaseResponse = plannedPurchaseService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft delete a planned purchase", description = "Same rule as editing.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    fun delete(
        @PathVariable id: UUID,
    ) = plannedPurchaseService.softDelete(id, currentUser.require())
}
