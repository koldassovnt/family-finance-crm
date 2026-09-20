package com.familyfinance.crm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.util.UUID

/**
 * One grant: [grantee] may read the one resource [resourceType]/[resourceId]
 * that [owner] owns, and nothing else. One table with a discriminator rather
 * than five near-identical join tables — see `phase-8-sharing.md` for why the
 * missing FK on [resourceId] is the right trade here.
 *
 * Carries `@SQLRestriction` deliberately: a revoked share is soft-deleted, and
 * a revoked grant that still resolved anywhere would be a disclosure. Nothing
 * holds an association *to* a `Share`, so the restriction has none of the
 * relationship-loading problems that keep it off `Account` and `Category`.
 */
@Entity
@Table(name = "shares")
@SQLRestriction("is_deleted = false")
class Share(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var resourceType: ShareResourceType,
    /** No FK: it points at one of five tables. Nothing is ever hard-deleted, so it cannot dangle. */
    @Column(nullable = false)
    var resourceId: UUID,
    /**
     * Who granted it, denormalized from the resource so "everything I have
     * shared" is one query rather than five.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grantee_id", nullable = false)
    var grantee: User,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var access: ShareAccess,
) : BaseEntity()
