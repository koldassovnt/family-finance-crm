package com.familyfinance.crm.dto

import com.familyfinance.crm.domain.CategoryKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.Optional
import java.util.UUID

data class CreateCategoryRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String,
    @field:NotNull(message = "is required")
    val kind: CategoryKind?,
    val parentId: UUID? = null,
)

/** `parentId` is an [Optional] so an explicit `null` detaches the parent. */
data class UpdateCategoryRequest(
    @field:Size(max = 255, message = "must be at most 255 characters")
    val name: String? = null,
    val parentId: Optional<UUID>? = null,
)

data class CategoryResponse(
    val id: UUID,
    val name: String,
    val kind: CategoryKind,
    val parentId: UUID?,
)
