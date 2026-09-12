package com.familyfinance.crm.web

import com.familyfinance.crm.dto.CategoryResponse
import com.familyfinance.crm.dto.CreateCategoryRequest
import com.familyfinance.crm.dto.UpdateCategoryRequest
import com.familyfinance.crm.dto.toResponse
import com.familyfinance.crm.exception.ErrorResponse
import com.familyfinance.crm.service.CategoryService
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Per-user expense and income categories")
class CategoryController(
    private val categoryService: CategoryService,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    @Operation(
        summary = "List your categories",
        description = "Excludes soft-deleted categories, which stay attached to historical transactions.",
    )
    @ApiResponse(responseCode = "200", description = "Your categories")
    fun list(): List<CategoryResponse> = categoryService.list(currentUser.require()).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a category", description = "A parent category, if given, must be one of your own.")
    @ApiResponse(responseCode = "201", description = "Created")
    fun create(
        @Valid @RequestBody request: CreateCategoryRequest,
    ): CategoryResponse = categoryService.create(currentUser.require(), request).toResponse()

    @PatchMapping("/{id}")
    @Operation(
        summary = "Update a category",
        description = "Name and parent only. Send `parentId: null` to detach the parent; omit it to leave it alone.",
    )
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(
        responseCode = "404",
        description = "No such category",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCategoryRequest,
    ): CategoryResponse = categoryService.update(id, currentUser.require(), request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Soft delete a category",
        description = "Transactions keep pointing at it; it just stops appearing in this list.",
    )
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(
        responseCode = "409",
        description = "Still referenced by sub-categories",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun delete(
        @PathVariable id: UUID,
    ) = categoryService.softDelete(id, currentUser.require())
}
