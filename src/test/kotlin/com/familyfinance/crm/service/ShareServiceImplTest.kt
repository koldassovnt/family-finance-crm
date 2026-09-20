package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.Share
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.dto.CreateShareRequest
import com.familyfinance.crm.exception.ConflictException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.ValidationException
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.ShareRepository
import com.familyfinance.crm.share
import com.familyfinance.crm.user
import com.familyfinance.crm.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareServiceImplTest {
    private val shareRepository = mockk<ShareRepository>()
    private val userService = mockk<UserService>()
    private val shareableResources = mockk<ShareableResourceService>()
    private val service = ShareServiceImpl(shareRepository, userService, shareableResources)

    private val owner = user()
    private val grantee = user(email = "member@example.com")
    private val subject = account(owner)

    init {
        every { shareRepository.save(any<Share>()) } answers { firstArg<Share>().withId() }
        every { shareableResources.requireOwned(any(), any(), any()) } returns Unit
        every { userService.getById(grantee.idValue) } returns grantee
        every { shareRepository.findGrant(any(), any(), any()) } returns null
    }

    private fun request(
        resourceId: UUID = subject.idValue,
        granteeUserId: UUID = grantee.idValue,
    ) = CreateShareRequest(
        resourceType = ShareResourceType.ACCOUNT,
        resourceId = resourceId,
        granteeUserId = granteeUserId,
    )

    @Test
    fun `granting stores a VIEWER grant with the resource's owner on it`() {
        val granted = service.grant(owner, request())

        assertEquals(ShareAccess.VIEWER, granted.access)
        assertEquals(owner, granted.owner)
        assertEquals(grantee, granted.grantee)
        assertEquals(ShareResourceType.ACCOUNT, granted.resourceType)
    }

    @Test
    fun `sharing something you do not own is rejected before the grantee is even resolved`() {
        every {
            shareableResources.requireOwned(owner, ShareResourceType.ACCOUNT, subject.idValue)
        } throws NotFoundException("Account was not found")

        assertThrows<NotFoundException> { service.grant(owner, request()) }
        // Nothing about the resource may leak, so the caller never gets as far as
        // learning whether the grantee exists.
        verify(exactly = 0) { userService.getById(any()) }
    }

    @Test
    fun `sharing with yourself is rejected`() {
        every { userService.getById(owner.idValue) } returns owner

        assertThrows<ValidationException> { service.grant(owner, request(granteeUserId = owner.idValue)) }
    }

    @Test
    fun `sharing with an unknown member is not found`() {
        val stranger = UUID.randomUUID()
        every { userService.getById(stranger) } throws NotFoundException("User was not found")

        assertThrows<NotFoundException> { service.grant(owner, request(granteeUserId = stranger)) }
    }

    @Test
    fun `sharing the same thing twice with the same person is a conflict`() {
        every {
            shareRepository.findGrant(grantee, ShareResourceType.ACCOUNT, subject.idValue)
        } returns share(owner, grantee, ShareResourceType.ACCOUNT, subject.idValue)

        assertThrows<ConflictException> { service.grant(owner, request()) }
    }

    @Test
    fun `revoking soft deletes the grant rather than removing the row`() {
        val existing = share(owner, grantee, ShareResourceType.ACCOUNT, subject.idValue)
        every { shareRepository.findById(existing.idValue) } returns Optional.of(existing)

        service.revoke(existing.idValue, owner)

        assertTrue(existing.isDeleted)
    }

    @Test
    fun `revoking someone else's grant reads as not found`() {
        val theirs = share(grantee, user(email = "third@example.com"), ShareResourceType.ACCOUNT, subject.idValue)
        every { shareRepository.findById(theirs.idValue) } returns Optional.of(theirs)

        assertThrows<NotFoundException> { service.revoke(theirs.idValue, owner) }
        assertTrue(!theirs.isDeleted)
    }

    @Test
    fun `who a resource is shared with requires owning it`() {
        every {
            shareableResources.requireOwned(grantee, ShareResourceType.ACCOUNT, subject.idValue)
        } throws NotFoundException("Account was not found")

        // A viewer of this very account gets the same 404 as a stranger: who else
        // can see it is the owner's business.
        assertThrows<NotFoundException> {
            service.listForResource(grantee, ShareResourceType.ACCOUNT, subject.idValue)
        }
    }

    @Test
    fun `an incoming row carries the resource's name, resolved once per type`() {
        val topicId = UUID.randomUUID()
        every { shareRepository.findAllIncoming(grantee) } returns
            listOf(
                share(owner, grantee, ShareResourceType.ACCOUNT, subject.idValue),
                share(owner, grantee, ShareResourceType.TOPIC, topicId),
            )
        every { shareableResources.namesOf(ShareResourceType.ACCOUNT, listOf(subject.idValue)) } returns
            mapOf(subject.idValue to "Main")
        every { shareableResources.namesOf(ShareResourceType.TOPIC, listOf(topicId)) } returns
            mapOf(topicId to "Малайзия 2026")

        val incoming = service.incoming(grantee)

        assertEquals(listOf("Main", "Малайзия 2026"), incoming.map { it.resourceName })
    }

    @Test
    fun `a share whose resource has been deleted drops out of the list`() {
        every { shareRepository.findAllOutgoing(owner) } returns
            listOf(share(owner, grantee, ShareResourceType.ACCOUNT, subject.idValue))
        // Deleted resources have no name to resolve: the grant stays on the books,
        // but the thing has stopped appearing for everyone.
        every { shareableResources.namesOf(ShareResourceType.ACCOUNT, listOf(subject.idValue)) } returns emptyMap()

        assertTrue(service.outgoing(owner).isEmpty())
    }
}
