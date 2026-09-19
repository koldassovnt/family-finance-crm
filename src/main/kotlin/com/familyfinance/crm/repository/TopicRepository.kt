package com.familyfinance.crm.repository

import com.familyfinance.crm.domain.Topic
import com.familyfinance.crm.domain.TopicStatus
import com.familyfinance.crm.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * [Topic] carries no `@SQLRestriction`, so every query here filters
 * `isDeleted` explicitly. Newest undertaking first, with topics that never got
 * a start date last rather than first.
 */
interface TopicRepository : JpaRepository<Topic, UUID> {
    @Query(
        """
        SELECT t FROM Topic t
        WHERE t.owner = :owner AND t.isDeleted = false
        ORDER BY t.startDate DESC NULLS LAST, t.name ASC
        """,
    )
    fun findAllActiveByOwner(owner: User): List<Topic>

    @Query(
        """
        SELECT t FROM Topic t
        WHERE t.owner = :owner AND t.isDeleted = false AND t.status = :status
        ORDER BY t.startDate DESC NULLS LAST, t.name ASC
        """,
    )
    fun findAllByOwnerAndStatus(
        owner: User,
        status: TopicStatus,
    ): List<Topic>

    /** Backs the partial unique index, so the 409 arrives before the database does. */
    @Query(
        """
        SELECT count(t) > 0 FROM Topic t
        WHERE t.owner = :owner AND t.isDeleted = false
          AND lower(t.name) = lower(:name) AND (:excludedId IS NULL OR t.id <> :excludedId)
        """,
    )
    fun existsByName(
        owner: User,
        name: String,
        excludedId: UUID?,
    ): Boolean
}
