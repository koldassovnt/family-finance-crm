package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateCategoryRequest
import com.familyfinance.crm.dto.UpdateCategoryRequest
import java.util.UUID

interface CategoryService {
    fun list(owner: User): List<Category>

    /**
     * Resolves a category the caller owns. Rejects one owned by someone else
     * with the same 404 as a missing id, so ids can't be probed.
     */
    fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Category

    fun create(
        owner: User,
        request: CreateCategoryRequest,
    ): Category

    fun update(
        id: UUID,
        owner: User,
        request: UpdateCategoryRequest,
    ): Category

    fun softDelete(
        id: UUID,
        owner: User,
    )

    /**
     * Maps each of this owner's categories to itself plus every descendant, to
     * arbitrary depth. Budgets use it to roll sub-category spending up into the
     * parent's usage. Built once per request rather than walked per budget.
     */
    fun descendantIndex(owner: User): Map<UUID, Set<UUID>>
}
