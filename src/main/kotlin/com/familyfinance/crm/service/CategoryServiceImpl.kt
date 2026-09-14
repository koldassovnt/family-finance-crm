package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateCategoryRequest
import com.familyfinance.crm.dto.UpdateCategoryRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.BudgetVersionRepository
import com.familyfinance.crm.repository.CategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CategoryServiceImpl(
    private val categoryRepository: CategoryRepository,
    // A repository rather than BudgetService: BudgetService already depends on
    // this service, and a cycle between the two would not start.
    private val budgetVersionRepository: BudgetVersionRepository,
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
                name = requireNonBlankName(request.name),
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
        request.name?.let { category.name = requireNonBlankName(it) }
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
        // A budget whose version is still open is current config, not history,
        // so it blocks the delete; a closed version does not.
        if (budgetVersionRepository.existsOpenForCategory(id)) {
            throw ConflictException("Category $id still has an active budget; delete the budget first")
        }
        if (categoryRepository.hasActiveChildren(id)) {
            throw ConflictException("Category $id still has sub-categories; delete or re-parent them first")
        }
        // Transactions referencing this category deliberately do NOT block the
        // delete — they keep rendering with its name in history. See `00-`.
        category.isDeleted = true
    }

    @Transactional(readOnly = true)
    override fun descendantIndex(owner: User): Map<UUID, Set<UUID>> {
        val categories = categoryRepository.findAllByOwnerIncludingDeleted(owner)
        val childrenByParent =
            categories
                .filter { it.parent != null }
                .groupBy({ checkNotNull(it.parent).id }, { checkNotNull(it.id) })
        return categories.associate { category ->
            val root = checkNotNull(category.id)
            root to collectDescendants(root, childrenByParent)
        }
    }

    /**
     * Breadth-first rather than recursive, and tracking what it has seen, so a
     * cycle that somehow reached the database can't spin forever here.
     */
    private fun collectDescendants(
        root: UUID,
        childrenByParent: Map<UUID?, List<UUID>>,
    ): Set<UUID> {
        val collected = mutableSetOf(root)
        val queue = ArrayDeque(childrenByParent[root].orEmpty())
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (collected.add(next)) {
                queue.addAll(childrenByParent[next].orEmpty())
            }
        }
        return collected
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
