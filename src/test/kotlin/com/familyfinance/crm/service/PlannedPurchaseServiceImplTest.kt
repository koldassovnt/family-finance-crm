package com.familyfinance.crm.service

import com.familyfinance.crm.domain.PlannedPurchase
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreatePlannedPurchaseRequest
import com.familyfinance.crm.dto.UpdatePlannedPurchaseRequest
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.PlannedPurchaseRepository
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlannedPurchaseServiceImplTest {
    private val repository = mockk<PlannedPurchaseRepository>()
    private val service = PlannedPurchaseServiceImpl(repository)

    private val husband = user(email = "husband@example.com")
    private val wife = user(email = "wife@example.com")

    init {
        every { repository.save(any<PlannedPurchase>()) } answers { firstArg<PlannedPurchase>().withId() }
    }

    @Test
    fun `a new item is personal, not bought, and has its name trimmed`() {
        val created = service.create(wife, CreatePlannedPurchaseRequest(name = "  Пылесос "))

        assertEquals(listOf<Any>("Пылесос", false, false), listOf(created.name, created.isFamily, created.isBought))
    }

    @Test
    fun `an item can be added to the family list`() {
        val created = service.create(wife, CreatePlannedPurchaseRequest(name = "Диван", isFamily = true))

        assertTrue(created.isFamily)
    }

    @Test
    fun `an item records who added it`() {
        val created = service.create(wife, CreatePlannedPurchaseRequest(name = "Диван", isFamily = true))

        assertSame(wife, created.owner)
    }

    @Test
    fun `rejects a blank name`() {
        assertThrows<ValidationException> {
            service.create(wife, CreatePlannedPurchaseRequest(name = "   "))
        }
    }

    @Test
    fun `the list is whatever is visible to the caller`() {
        val visible = listOf(purchase(wife), purchase(husband, isFamily = true))
        every { repository.findVisibleTo(wife) } returns visible

        assertEquals(visible, service.list(wife))
    }

    @Test
    fun `the owner can mark a personal item bought`() {
        val purchase = stored(purchase(wife))

        val updated = service.update(purchase.idValue, wife, UpdatePlannedPurchaseRequest(isBought = true))

        assertTrue(updated.isBought)
    }

    @Test
    fun `a bought item can be marked not bought again`() {
        val purchase = stored(purchase(wife, isBought = true))

        val updated = service.update(purchase.idValue, wife, UpdatePlannedPurchaseRequest(isBought = false))

        assertFalse(updated.isBought)
    }

    @Test
    fun `renaming leaves the bought flag alone`() {
        val purchase = stored(purchase(wife, isBought = true))

        val updated = service.update(purchase.idValue, wife, UpdatePlannedPurchaseRequest(name = "Робот-пылесос"))

        assertEquals("Робот-пылесос" to true, updated.name to updated.isBought)
    }

    @Test
    fun `another member can mark a family item bought`() {
        val purchase = stored(purchase(husband, isFamily = true))

        val updated = service.update(purchase.idValue, wife, UpdatePlannedPurchaseRequest(isBought = true))

        assertTrue(updated.isBought)
    }

    @Test
    fun `another member can delete a family item`() {
        val purchase = stored(purchase(husband, isFamily = true))

        service.softDelete(purchase.idValue, wife)

        assertTrue(purchase.isDeleted)
    }

    @Test
    fun `another member cannot change someone's personal item`() {
        val purchase = stored(purchase(husband))

        assertThrows<NotFoundException> {
            service.update(purchase.idValue, wife, UpdatePlannedPurchaseRequest(isBought = true))
        }
    }

    @Test
    fun `another member cannot delete someone's personal item`() {
        val purchase = stored(purchase(husband))

        assertThrows<NotFoundException> { service.softDelete(purchase.idValue, wife) }
    }

    @Test
    fun `the owner can delete a personal item`() {
        val purchase = stored(purchase(wife))

        service.softDelete(purchase.idValue, wife)

        assertTrue(purchase.isDeleted)
    }

    @Test
    fun `an unknown id is not found`() {
        val id = UUID.randomUUID()
        every { repository.findDetailedById(id) } returns null

        assertThrows<NotFoundException> { service.softDelete(id, wife) }
    }

    private fun stored(purchase: PlannedPurchase): PlannedPurchase {
        every { repository.findDetailedById(purchase.idValue) } returns purchase
        return purchase
    }

    private fun purchase(
        owner: User,
        isFamily: Boolean = false,
        isBought: Boolean = false,
    ) = PlannedPurchase(owner = owner, name = "Пылесос", isFamily = isFamily, isBought = isBought).withId()
}
