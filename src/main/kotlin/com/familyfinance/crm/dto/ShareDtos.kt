package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.ShareResourceType
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateShareRequest(
    @field:NotNull(message = "is required")
    val resourceType: ShareResourceType?,
    @field:NotNull(message = "is required")
    val resourceId: UUID?,
    @field:NotNull(message = "is required")
    val granteeUserId: UUID?,
)

/** Just enough of a person to render and sort a row. */
data class UserRef(
    val id: UUID,
    val displayName: String,
)

data class ShareResponse(
    val id: UUID,
    val resourceType: ShareResourceType,
    val resourceId: UUID,
    /**
     * The shared thing's own name — a budget's is its category's. Present on the
     * incoming and outgoing lists, which need it to render a row; null when the
     * caller asked who one resource is shared with, since they are looking at it.
     */
    val resourceName: String?,
    val owner: UserRef,
    val grantee: UserRef,
    val access: ShareAccess,
    val sharedAt: Instant?,
)
