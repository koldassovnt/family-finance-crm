package com.familyfinance.crm.service

import com.familyfinance.crm.category
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.dto.CreateCategoryRequest
import com.familyfinance.crm.dto.UpdateCategoryRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.CategoryRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryServiceImplTest {
    private val categoryRepository = mockk<CategoryRepository>()
    private val service = CategoryServiceImpl(categoryRepository)
    private val owner = user()

    init {
        every { categoryRepository.save(any<Category>()) } answers { firstArg<Category>().withId() }
        every { categoryRepository.hasActiveChildren(any()) } returns false
    }

    @Test
    fun `returns 404 for a category owned by someone else`() {
        val other = user(email = "other@example.com")
        val theirs = category(other)
        every { categoryRepository.findActiveById(theirs.idValue) } returns theirs

        assertThrows<NotFoundException> { service.getOwnedBy(theirs.idValue, owner) }
    }

    @Test
    fun `returns 404 for a category that does not exist`() {
        val id = UUID.randomUUID()
        every { categoryRepository.findActiveById(id) } returns null

        assertThrows<NotFoundException> { service.getOwnedBy(id, owner) }
    }

    @Test
    fun `creates a category with a parent of the same owner`() {
        val parent = category(owner)
        every { categoryRepository.findActiveById(parent.idValue) } returns parent

        val created =
            service.create(
                owner,
                CreateCategoryRequest(name = "  Fruit  ", kind = CategoryKind.EXPENSE, parentId = parent.idValue),
            )

        assertEquals("Fruit", created.name)
        assertEquals(parent, created.parent)
    }

    @Test
    fun `rejects making a category its own parent`() {
        val subject = category(owner)
        every { categoryRepository.findActiveById(subject.idValue) } returns subject

        assertThrows<ValidationException> {
            service.update(
                subject.idValue,
                owner,
                UpdateCategoryRequest(parentId = java.util.Optional.of(subject.idValue)),
            )
        }
    }

    @Test
    fun `rejects a parent that is one of the category's own descendants`() {
        val root = category(owner)
        val child = category(owner, parent = root)
        every { categoryRepository.findActiveById(root.idValue) } returns root
        every { categoryRepository.findActiveById(child.idValue) } returns child

        assertThrows<ValidationException> {
            service.update(root.idValue, owner, UpdateCategoryRequest(parentId = java.util.Optional.of(child.idValue)))
        }
    }

    @Test
    fun `detaches the parent when parentId is explicitly null`() {
        val root = category(owner)
        val child = category(owner, parent = root)
        every { categoryRepository.findActiveById(child.idValue) } returns child

        val updated = service.update(child.idValue, owner, UpdateCategoryRequest(parentId = java.util.Optional.empty()))

        assertEquals(null, updated.parent)
    }

    @Test
    fun `leaves the parent alone when parentId is absent`() {
        val root = category(owner)
        val child = category(owner, parent = root)
        every { categoryRepository.findActiveById(child.idValue) } returns child

        val updated = service.update(child.idValue, owner, UpdateCategoryRequest(name = "Renamed"))

        assertEquals(root, updated.parent)
        assertEquals("Renamed", updated.name)
    }

    @Test
    fun `soft deletes rather than removing the row`() {
        val subject = category(owner)
        every { categoryRepository.findActiveById(subject.idValue) } returns subject

        service.softDelete(subject.idValue, owner)

        assertTrue(subject.isDeleted)
    }

    @Test
    fun `blocks deleting a category that still has sub-categories`() {
        val subject = category(owner)
        every { categoryRepository.findActiveById(subject.idValue) } returns subject
        every { categoryRepository.hasActiveChildren(subject.idValue) } returns true

        assertThrows<ConflictException> { service.softDelete(subject.idValue, owner) }
    }
}
