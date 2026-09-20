package com.familyfinance.crm.service

import com.familyfinance.crm.account
import com.familyfinance.crm.domain.AccessLevel
import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.ShareAccess
import com.familyfinance.crm.domain.ShareResourceType
import com.familyfinance.crm.idValue
import com.familyfinance.crm.repository.ShareRepository
import com.familyfinance.crm.share
import com.familyfinance.crm.user
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareAccessServiceImplTest {
    private val shareRepository = mockk<ShareRepository>()
    private val service = ShareAccessServiceImpl(shareRepository)

    private val owner = user()
    private val viewer = user(email = "viewer@example.com")
    private val subject = account(owner)

    @Test
    fun `an owner reads their own without a share being looked up at all`() {
        val readable = service.readableBy(owner, subject, owner, ShareResourceType.ACCOUNT, subject.idValue)

        assertIs<Readable.Own<Account>>(readable)
        // Ownership must never depend on the share table: a member with no grants
        // anywhere still reads everything of their own.
        verify(exactly = 0) { shareRepository.findGrant(any(), any(), any()) }
    }

    @Test
    fun `a grant makes one resource readable, carrying what it confers`() {
        every {
            shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, subject.idValue)
        } returns share(owner, viewer, ShareResourceType.ACCOUNT, subject.idValue)

        val readable = service.readableBy(viewer, subject, owner, ShareResourceType.ACCOUNT, subject.idValue)

        val shared = assertIs<Readable.Shared<Account>>(readable)
        assertEquals(owner, shared.owner)
        assertEquals(ShareAccess.VIEWER, shared.access)
    }

    @Test
    fun `no grant is null, which every caller turns into the same 404 as a missing id`() {
        every { shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, subject.idValue) } returns null

        assertNull(service.readableBy(viewer, subject, owner, ShareResourceType.ACCOUNT, subject.idValue))
    }

    @Test
    fun `an owner badge reports OWNER and names nobody`() {
        val readable = service.readableBy(owner, subject, owner, ShareResourceType.ACCOUNT, subject.idValue)

        assertEquals(AccessLevel.OWNER, readable?.accessLevel)
        assertNull(readable?.sharedBy)
    }

    @Test
    fun `a viewer badge reports VIEWER and names the owner`() {
        every {
            shareRepository.findGrant(viewer, ShareResourceType.ACCOUNT, subject.idValue)
        } returns share(owner, viewer, ShareResourceType.ACCOUNT, subject.idValue)

        val readable = service.readableBy(viewer, subject, owner, ShareResourceType.ACCOUNT, subject.idValue)

        assertEquals(AccessLevel.VIEWER, readable?.accessLevel)
        assertEquals(owner, readable?.sharedBy)
    }

    @Test
    fun `sharedWith pairs each resource with the grant that made it readable`() {
        val second = account(owner)
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.ACCOUNT) } returns
            listOf(
                share(owner, viewer, ShareResourceType.ACCOUNT, subject.idValue),
                share(owner, viewer, ShareResourceType.ACCOUNT, second.idValue),
            )

        val readable =
            service.sharedWith(
                reader = viewer,
                resourceType = ShareResourceType.ACCOUNT,
                load = { listOf(subject, second) },
                ownerOf = Account::owner,
            )

        assertEquals(listOf(subject, second), readable.map { it.resource })
    }

    @Test
    fun `sharedWith drops a resource its owner has since deleted`() {
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.ACCOUNT) } returns
            listOf(share(owner, viewer, ShareResourceType.ACCOUNT, subject.idValue))

        // The loader filters isDeleted, so a deleted resource simply is not returned —
        // it stops appearing for its viewer exactly as it does for its owner.
        val readable =
            service.sharedWith(
                reader = viewer,
                resourceType = ShareResourceType.ACCOUNT,
                load = { emptyList() },
                ownerOf = Account::owner,
            )

        assertTrue(readable.isEmpty())
    }

    @Test
    fun `sharedWith holds a resource whose grant is not among the ones asked for`() {
        val unshared = account(owner)
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.ACCOUNT) } returns
            listOf(share(owner, viewer, ShareResourceType.ACCOUNT, subject.idValue))

        // A loader that over-returns must not widen access: only the granted id survives.
        val readable =
            service.sharedWith(
                reader = viewer,
                resourceType = ShareResourceType.ACCOUNT,
                load = { listOf(subject, unshared) },
                ownerOf = Account::owner,
            )

        assertEquals(listOf(subject), readable.map { it.resource })
    }

    @Test
    fun `no grants at all never reaches a query, since IN () is not valid JPQL`() {
        every { shareRepository.findAllGrantsTo(viewer, ShareResourceType.ACCOUNT) } returns emptyList()
        var loaded: Collection<UUID>? = null

        val readable =
            service.sharedWith(
                reader = viewer,
                resourceType = ShareResourceType.ACCOUNT,
                load = { ids ->
                    loaded = ids
                    listOf(subject)
                },
                ownerOf = Account::owner,
            )

        assertTrue(readable.isEmpty())
        assertNull(loaded)
    }
}
