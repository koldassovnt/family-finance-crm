package com.familyfinance.crm.web

import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.dto.CreateShareRequest
import com.familyfinance.crm.dto.ShareResponse
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.ShareService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/shares")
@Tag(
    name = "Shares",
    description = "Letting one household member view one specific thing you own, and nothing more",
)
class ShareController(
    private val shareService: ShareService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "Who this resource is shared with",
        description =
            "Owner only. A viewer of the resource gets the same 404 as a stranger: who else can see " +
                "something is the owner's business.",
    )
    @ApiResponse(responseCode = "200", description = "The grants on this resource")
    @ApiResponse(
        responseCode = "404",
        description = "No such resource, or not yours",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun listForResource(
        @RequestParam resourceType: ShareResourceType,
        @RequestParam resourceId: UUID,
    ): List<ShareResponse> =
        shareService
            .listForResource(
                owner = currentUser.require(),
                resourceType = resourceType,
                resourceId = resourceId,
            ).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Share one thing with one person",
        description =
            "Read-only access to that single resource. What a share exposes is wider than it sounds — " +
                "an account brings its whole transaction history, a goal discloses the linked account's " +
                "balance, a topic brings transactions from accounts that were never shared. The UI should " +
                "say so before this call, not after.",
    )
    @ApiResponse(responseCode = "201", description = "Shared")
    @ApiResponse(
        responseCode = "400",
        description = "Sharing with yourself",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "No such resource, not yours, or no such member",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    @ApiResponse(
        responseCode = "409",
        description = "Already shared with this person",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun grant(
        @Valid @RequestBody request: CreateShareRequest,
    ): ShareResponse = shareService.grant(currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Revoke a share",
        description =
            "Takes effect immediately: the next read by that viewer is a 404. The same thing can be " +
                "shared with them again afterwards.",
    )
    @ApiResponse(responseCode = "204", description = "Revoked")
    fun revoke(
        @PathVariable id: UUID,
    ) = shareService.revoke(id, currentUser.require())

    @GetMapping("/incoming")
    @Operation(
        summary = "Everything shared with me",
        description =
            "All five types in one list, each with its name and whose it is — enough to render a " +
                "\"Shared with me\" screen without a call per type. Something its owner has since " +
                "deleted stops appearing here, as it does for them.",
    )
    @ApiResponse(responseCode = "200", description = "Grants held by you")
    fun incoming(): List<ShareResponse> = shareService.incoming(currentUser.require()).map { it.toResponse() }

    @GetMapping("/outgoing")
    @Operation(
        summary = "Everything I have shared",
        description = "One screen to review and revoke from, rather than visiting five resource pages.",
    )
    @ApiResponse(responseCode = "200", description = "Grants made by you")
    fun outgoing(): List<ShareResponse> = shareService.outgoing(currentUser.require()).map { it.toResponse() }
}
