package com.familyfinance.crm.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreatePlannedPurchaseRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
    /** `true` puts it on the family list, which everyone sees and can tick off. Personal by default. */
    val isFamily: Boolean = false,
)

/** A null property means "leave unchanged". Which list an item is on cannot be changed. */
data class UpdatePlannedPurchaseRequest(
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String? = null,
    val isBought: Boolean? = null,
)

data class PlannedPurchaseResponse(
    val id: UUID,
    val name: String,
    val isFamily: Boolean,
    val isBought: Boolean,
    val addedBy: UserRef,
    val createdAt: Instant?,
)
