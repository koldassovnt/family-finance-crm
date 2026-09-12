package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateCategoryRequest
import com.familyfinance.crm.dto.UpdateCategoryRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.CategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CategoryServiceImpl(
    private val categoryRepository: CategoryRepository,
) : CategoryService {
    @Transactional(readOnly = true)
    override fun list(owner: User): List<Category> = categoryRepository.findAllActiveByOwner(owner)

    @Transactional(readOnly = true)
    override fun getOwnedBy(
        id: UUID,
        owner: User,
    ): Category {
        val category =
            categoryRepository.findActiveById(id)
                ?: throw NotFoundException("Category $id was not found")
        if (category.owner != owner) throw NotFoundException("Category $id was not found")
        return category
    }

    @Transactional
    override fun create(
        owner: User,
        request: CreateCategoryRequest,
    ): Category {
        val kind = request.kind ?: throw invalidField("kind", "is required")
        val parent = request.parentId?.let { getOwnedBy(it, owner) }
        return categoryRepository.save(
            Category(
                owner = owner,
                name = request.name.trim(),
                parent = parent,
                kind = kind,
            ),
        )
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateCategoryRequest,
    ): Category {
        val category = getOwnedBy(id, owner)
        request.name?.let { category.name = it.trim() }
        request.parentId?.let { parentId ->
            category.parent = parentId.orElse(null)?.let { resolveParent(it, category, owner) }
        }
        return category
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        owner: User,
    ) {
        val category = getOwnedBy(id, owner)
        // TODO: block if referenced by an active Budget (Phase 2 — no table yet).
        if (categoryRepository.hasActiveChildren(id)) {
            throw ConflictException("Category $id still has sub-categories; delete or re-parent them first")
        }
        // Transactions referencing this category deliberately do NOT block the
        // delete — they keep rendering with its name in history. See `00-`.
        category.isDeleted = true
    }

    private fun resolveParent(
        parentId: UUID,
        category: Category,
        owner: User,
    ): Category {
        if (parentId == category.id) throw invalidField("parentId", "cannot be the category itself")
        val parent = getOwnedBy(parentId, owner)
        if (isDescendantOf(parent, category)) {
            throw invalidField("parentId", "cannot be one of the category's own descendants")
        }
        return parent
    }

    private fun isDescendantOf(
        candidate: Category,
        ancestor: Category,
    ): Boolean {
        var current: Category? = candidate.parent
        while (current != null) {
            if (current == ancestor) return true
            current = current.parent
        }
        return false
    }
}
